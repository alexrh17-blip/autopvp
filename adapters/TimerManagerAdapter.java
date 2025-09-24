package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.util.timers.TimerKey;
import com.elvarg.game.content.combat.CombatType;
import com.elvarg.util.timers.TimerRepository;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Prayer;
import net.runelite.api.Hitsplat;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.autopvp.adapters.OpponentElvargPlayer;

/**
 * Adapter that tracks timers for Elvarg's timer system.
 * Uses RuneLite events to track freeze, vengeance, and combat timers for both
 * the local player and the current target.
 */
@Slf4j
public class TimerManagerAdapter extends TimerRepository
{
    private static final class TargetState
    {
        int freezeTicks;
        int immunityTicks;
        int attackCooldown;
        int potionCooldown;
        int pendingHitTicks;
        int vengeanceCooldown;
        int lastAttackTick;
        int lastHitsplatTick;
        CombatType lastAttackType = CombatType.MELEE;
        boolean targetPrayerCorrect;
        boolean playerPrayerCorrect;
    }

    private static final int VENGEANCE_GRAPHIC = 726;
    private static final int VENGEANCE_OTHER_GRAPHIC = 725;

    private static final int ICE_RUSH_GRAPHIC = 361;
    private static final int ICE_BURST_GRAPHIC = 363;
    private static final int ICE_BLITZ_GRAPHIC = 367;
    private static final int ICE_BARRAGE_GRAPHIC = 369;

    private static final int EAT_ANIMATION = 829;
    private static final int DRINK_ANIMATION = 829;

    private static final int DEFAULT_ATTACK_SPEED = 4;
    private static final int POTION_GUARD_TICKS = 3; // Elvarg potion delay
    private static final int FREEZE_IMMUNITY_PAD = 5; // Freeze immunity buffer (Elvarg)
    private static final int TARGET_VENGEANCE_COOLDOWN = 50; // EffectSpells.java: 50 ticks

    private final Map<Integer, Integer> weaponSpeeds = new ConcurrentHashMap<>();
    private final Map<Integer, CombatType> animationCombatTypes = new ConcurrentHashMap<>();

    private final Client client;
    private final EventBus eventBus;
    private final Map<TimerKey, Integer> timers = new ConcurrentHashMap<>();
    private final Map<TimerKey, Object> attachments = new ConcurrentHashMap<>();
    private final Map<Actor, TargetState> targetStates = new ConcurrentHashMap<>();

    // Reference to CombatAdapter to access current opponent for timer processing
    private CombatAdapter combatAdapter;

    private int vengeanceCooldown = 0;
    private CombatType playerCombatType = CombatType.MELEE;
    private boolean playerPrayerCorrect;
    private float destinationDistanceToTarget;
    private float distanceToDestination;
    private int lastAttackTick = 0;
    private int lastEatTick = 0;
    private int playerPendingHitTicks = 0;

    public TimerManagerAdapter(Client client, EventBus eventBus)
    {
        this.client = client;
        this.eventBus = eventBus;
        populateWeaponSpeeds();
        eventBus.register(this);
    }

    private void populateWeaponSpeeds()
    {
        weaponSpeeds.put(ItemID.ABYSSAL_WHIP, 4);
        animationCombatTypes.put(390, CombatType.MELEE);
        animationCombatTypes.put(422, CombatType.MELEE);
        animationCombatTypes.put(386, CombatType.MELEE);
        animationCombatTypes.put(1658, CombatType.MELEE);
        animationCombatTypes.put(7514, CombatType.MELEE);
        animationCombatTypes.put(7515, CombatType.MELEE);
        animationCombatTypes.put(7644, CombatType.MELEE);
        animationCombatTypes.put(7645, CombatType.MELEE);
        animationCombatTypes.put(7640, CombatType.MELEE);
        animationCombatTypes.put(7642, CombatType.MELEE);
        animationCombatTypes.put(426, CombatType.RANGED);
        animationCombatTypes.put(4230, CombatType.RANGED);
        animationCombatTypes.put(5061, CombatType.RANGED);
        animationCombatTypes.put(1167, CombatType.MAGIC);
        animationCombatTypes.put(7855, CombatType.MAGIC);
        animationCombatTypes.put(1979, CombatType.MAGIC);
        weaponSpeeds.put(ItemID.TOXIC_BLOWPIPE_LOADED, 4);
        weaponSpeeds.put(ItemID.AGS, 6);
        weaponSpeeds.put(ItemID.BGS, 6);
        weaponSpeeds.put(ItemID.SGS, 6);
        weaponSpeeds.put(ItemID.ZGS, 6);
        weaponSpeeds.put(ItemID.ABYSSAL_TENTACLE, 5);
        weaponSpeeds.put(ItemID.TWISTED_BOW, 5);
        weaponSpeeds.put(ItemID.ACB, 4);
        weaponSpeeds.put(ItemID.XBOWS_CROSSBOW_DRAGON, 4);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        decrementPlayerTimers();
        tickTargetStates();
        updateDestinationDistances();
    }

    private void decrementPlayerTimers()
    {
        Iterator<Map.Entry<TimerKey, Integer>> iterator = timers.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<TimerKey, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0)
            {
                iterator.remove();
            }
            else
            {
                timers.put(entry.getKey(), remaining);
            }
        }

        if (vengeanceCooldown > 0)
        {
            vengeanceCooldown--;
        }

        if (playerPendingHitTicks > 0)
        {
            playerPendingHitTicks--;
        }
    }

    private void tickTargetStates()
    {
        Actor currentTarget = getCurrentTarget();

        targetStates.entrySet().removeIf(entry -> entry.getKey() != currentTarget);

        if (currentTarget == null)
        {
            playerPrayerCorrect = false;
            return;
        }

        TargetState state = targetStates.get(currentTarget);
        if (state == null)
        {
            return;
        }

        int pendingBefore = state.pendingHitTicks;

        state.freezeTicks = decrement(state.freezeTicks);
        state.immunityTicks = decrement(state.immunityTicks);
        state.attackCooldown = decrement(state.attackCooldown);
        state.potionCooldown = decrement(state.potionCooldown);
        state.pendingHitTicks = decrement(state.pendingHitTicks);
        state.vengeanceCooldown = decrement(state.vengeanceCooldown);

        if (pendingBefore > 0 && state.pendingHitTicks == 0)
        {
            state.playerPrayerCorrect = isPlayerPrayerCorrect(state.lastAttackType);
            playerPrayerCorrect = state.playerPrayerCorrect;
        }

        // Call processTimers() on the current opponent if we have a CombatAdapter reference
        if (combatAdapter != null) {
            // Get the current opponent from CombatAdapter (OpponentElvargPlayer instance)
            com.elvarg.game.entity.impl.Mobile currentOpponent = combatAdapter.getTarget();
            if (currentOpponent instanceof OpponentElvargPlayer) {
                ((OpponentElvargPlayer) currentOpponent).processTimers();
            }
        }
    }

    private static int decrement(int value)
    {
        return value > 0 ? value - 1 : 0;
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event)
    {
        Actor actor = event.getActor();
        if (actor == null)
        {
            return;
        }

        if (actor == client.getLocalPlayer())
        {
            handleLocalGraphic(actor.getGraphic());
            return;
        }

        Actor currentTarget = getCurrentTarget();
        if (currentTarget != null && actor == currentTarget)
        {
            handleTargetGraphic(actor, actor.getGraphic());
        }
    }

    private void handleLocalGraphic(int graphic)
    {
        if (graphic == VENGEANCE_GRAPHIC || graphic == VENGEANCE_OTHER_GRAPHIC)
        {
            vengeanceCooldown = TARGET_VENGEANCE_COOLDOWN;
            return;
        }

        registerPlayerFreeze(graphic);
    }

    private void registerPlayerFreeze(int graphic)
    {
        int freezeTicks = getFreezeDuration(graphic);
        if (freezeTicks <= 0)
        {
            return;
        }

        register(TimerKey.FREEZE, freezeTicks);
        register(TimerKey.FREEZE_IMMUNITY, freezeTicks + FREEZE_IMMUNITY_PAD);
    }

    private void handleTargetGraphic(Actor actor, int graphic)
    {
        TargetState state = getOrCreateTargetState(actor);

        if (graphic == VENGEANCE_GRAPHIC || graphic == VENGEANCE_OTHER_GRAPHIC)
        {
            state.vengeanceCooldown = TARGET_VENGEANCE_COOLDOWN;
            return;
        }

        int freezeTicks = getFreezeDuration(graphic);
        if (freezeTicks <= 0)
        {
            return;
        }

        state.freezeTicks = freezeTicks;
        state.immunityTicks = freezeTicks + FREEZE_IMMUNITY_PAD;
    }

    private static int getFreezeDuration(int graphic)
    {
        switch (graphic)
        {
            case ICE_RUSH_GRAPHIC:
                return 8;
            case ICE_BURST_GRAPHIC:
                return 16;
            case ICE_BLITZ_GRAPHIC:
                return 24;
            case ICE_BARRAGE_GRAPHIC:
                return 32;
            default:
                return 0;
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        Actor actor = event.getActor();
        if (actor == null)
        {
            return;
        }

        int animation = actor.getAnimation();
        int currentTick = client.getTickCount();

        if (actor == client.getLocalPlayer())
        {
            handleLocalAnimation(animation, currentTick);
            return;
        }

        Actor currentTarget = getCurrentTarget();
        if (currentTarget != null && actor == currentTarget)
        {
            handleTargetAnimation(actor, animation, currentTick);
        }
    }

    private void handleLocalAnimation(int animation, int currentTick)
    {
        if (animation == EAT_ANIMATION && currentTick - lastEatTick > POTION_GUARD_TICKS)
        {
            register(TimerKey.FOOD, POTION_GUARD_TICKS);
            lastEatTick = currentTick;
        }

        if (isAttackAnimation(animation))
        {
            playerCombatType = mapAnimationToCombatType(animation);
            int weaponSpeed = getWeaponSpeed(client.getLocalPlayer());
            register(TimerKey.COMBAT_ATTACK, weaponSpeed);
            lastAttackTick = currentTick;
            playerPendingHitTicks = getHitDelay(client.getLocalPlayer(), animation);
            updateTargetPrayerCorrectness(getCurrentTarget());
        }
    }

    private void handleTargetAnimation(Actor actor, int animation, int currentTick)
    {
        TargetState state = getOrCreateTargetState(actor);

        if (animation == DRINK_ANIMATION)
        {
            state.potionCooldown = POTION_GUARD_TICKS;
        }

        if (isAttackAnimation(animation))
        {
            state.lastAttackType = mapAnimationToCombatType(animation);
            int weaponSpeed = getWeaponSpeed(actor);
            state.attackCooldown = weaponSpeed;
            state.lastAttackTick = currentTick;
            state.pendingHitTicks = getHitDelay(actor, animation);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        if (event.getActor() != client.getLocalPlayer())
        {
            return;
        }

        Actor source = client.getLocalPlayer().getInteracting();
        if (source == null)
        {
            return;
        }

        TargetState state = targetStates.get(source);
        if (state == null)
        {
            return;
        }

        Hitsplat hitsplat = event.getHitsplat();
        if (hitsplat == null || hitsplat.getAmount() <= 0)
        {
            return;
        }

        state.pendingHitTicks = 0;
        state.lastHitsplatTick = client.getTickCount();
        state.playerPrayerCorrect = isPlayerPrayerCorrect(state.lastAttackType);
        playerPrayerCorrect = state.playerPrayerCorrect;
    }

    private boolean isAttackAnimation(int animation)
    {
        return animation == 390 || // Unarmed punch
            animation == 422 || // Unarmed kick
            animation == 386 || // Staff bash
            animation == 1658 || // Whip
            animation == 7514 || // Claws spec
            animation == 7515 || // Claws spec
            animation == 7644 || // AGS spec
            animation == 7645 || // BGS spec
            animation == 7640 || // SGS spec
            animation == 7642 || // ZGS spec
            animation == 426 || // Bow
            animation == 4230 || // Crossbow
            animation == 5061 || // Blowpipe
            animation == 1167 || // Trident
            animation == 7855 || // Sanguinesti staff
            animation == 1979;   // Ice barrage cast
    }

    private int getWeaponSpeed(Actor actor)
    {
        if (actor == client.getLocalPlayer())
        {
            net.runelite.api.ItemContainer equipment = client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT);
            if (equipment != null)
            {
                net.runelite.api.Item weapon = equipment.getItem(3);
                if (weapon != null)
                {
                    return weaponSpeeds.getOrDefault(weapon.getId(), DEFAULT_ATTACK_SPEED);
                }
            }
            return DEFAULT_ATTACK_SPEED;
        }

        if (actor instanceof Player)
        {
            Player player = (Player) actor;
            PlayerComposition composition = player.getPlayerComposition();
            if (composition != null)
            {
                int[] equipmentIds = composition.getEquipmentIds();
                int weaponSlot = KitType.WEAPON.getIndex();
                if (equipmentIds != null && weaponSlot < equipmentIds.length)
                {
                    int weaponId = decodeItemId(equipmentIds[weaponSlot]);
                    if (weaponId > 0)
                    {
                        return weaponSpeeds.getOrDefault(weaponId, DEFAULT_ATTACK_SPEED);
                    }
                }
            }
        }

        return DEFAULT_ATTACK_SPEED;
    }

    private static int decodeItemId(int encodedId)
    {
        if (encodedId < 0)
        {
            return -1;
        }

        if (encodedId >= PlayerComposition.ITEM_OFFSET)
        {
            return encodedId - PlayerComposition.ITEM_OFFSET;
        }

        return encodedId;
    }

    private int getHitDelay(Actor actor, int animation)
    {
        if (animation == 1979)
        {
            return 4; // Barrage splash delay per CombatFactory#getHitDelay
        }

        if (animation == 5061 || animation == 4230 || animation == 426)
        {
            return 2; // Ranged projectiles typically land after 2 ticks
        }

        if (animation == 1167 || animation == 7855)
        {
            return 2; // Trident / Sang staff per Elvarg magic timings
        }

        return 1; // Standard melee hit delay
    }

    private CombatType mapAnimationToCombatType(int animation)
    {
        return animationCombatTypes.getOrDefault(animation, CombatType.MELEE);
    }

    private boolean isPlayerPrayerCorrect(CombatType attackType)
    {
        switch (attackType)
        {
            case MELEE:
                return client.isPrayerActive(Prayer.PROTECT_FROM_MELEE);
            case RANGED:
                return client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
            case MAGIC:
                return client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);
            default:
                return false;
        }
    }

    private boolean isTargetPrayerCorrect(Actor target, CombatType attackType)
    {
        if (!(target instanceof Player))
        {
            return false;
        }

        HeadIcon overhead = ((Player) target).getOverheadIcon();
        if (overhead == null)
        {
            return false;
        }

        switch (attackType)
        {
            case MELEE:
                return overhead == HeadIcon.MELEE;
            case RANGED:
                return overhead == HeadIcon.RANGED;
            case MAGIC:
                return overhead == HeadIcon.MAGIC;
            default:
                return false;
        }
    }

    private void updateTargetPrayerCorrectness(Actor target)
    {
        if (target == null)
        {
            return;
        }

        TargetState state = getOrCreateTargetState(target);
        state.targetPrayerCorrect = isTargetPrayerCorrect(target, playerCombatType);
    }

    private void updateDestinationDistances()
    {
        destinationDistanceToTarget = 0f;
        distanceToDestination = 0f;

        Player local = client.getLocalPlayer();
        if (local == null)
        {
            return;
        }

        LocalPoint localDestination = client.getLocalDestinationLocation();
        if (localDestination == null)
        {
            return;
        }

        WorldPoint destination = WorldPoint.fromLocalInstance(client, localDestination);
        if (destination == null)
        {
            return;
        }

        WorldPoint playerPoint = local.getWorldLocation();
        distanceToDestination = normaliseDistance(playerPoint, destination);

        Actor target = getCurrentTarget();
        if (target instanceof Player)
        {
            WorldPoint targetPoint = ((Player) target).getWorldLocation();
            destinationDistanceToTarget = normaliseDistance(destination, targetPoint);
        }
    }

    private static float normaliseDistance(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null)
        {
            return 0f;
        }

        int dx = from.getX() - to.getX();
        int dy = from.getY() - to.getY();
        double dist = Math.sqrt((double) dx * dx + (double) dy * dy);
        return (float) Math.min(dist, 10.0) / 10.0f;
    }

    private Actor getCurrentTarget()
    {
        return client.getLocalPlayer() != null ? client.getLocalPlayer().getInteracting() : null;
    }

    private TargetState getOrCreateTargetState(Actor actor)
    {
        return targetStates.computeIfAbsent(actor, key -> new TargetState());
    }

    private TargetState getTargetState(Actor target)
    {
        if (target == null)
        {
            return null;
        }
        return targetStates.get(target);
    }

    @Override
    public void register(TimerKey key, int ticks)
    {
        if (ticks > 0)
        {
            timers.put(key, ticks);
        }
    }

    public void register(TimerKey key, int ticks, Object attachment)
    {
        register(key, ticks);
    }

    @Override
    public void cancel(TimerKey key)
    {
        timers.remove(key);
        attachments.remove(key);
    }

    @Override
    public int left(TimerKey key)
    {
        return timers.getOrDefault(key, 0);
    }

    @Override
    public boolean has(TimerKey key)
    {
        return timers.getOrDefault(key, 0) > 0;
    }

    @Override
    public boolean willEndIn(TimerKey key, int ticks)
    {
        int remaining = left(key);
        return remaining > 0 && remaining <= ticks;
    }

    public float getDestinationDistanceToTarget()
    {
        return destinationDistanceToTarget;
    }

    public float getDistanceToDestination()
    {
        return distanceToDestination;
    }

    public boolean isPlayerPrayerCorrectAgainstTarget(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null && state.playerPrayerCorrect;
    }

    public boolean isTargetPrayerCorrectAgainstPlayer(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null && state.targetPrayerCorrect;
    }

    public Object getAttachment(TimerKey key)
    {
        return attachments.get(key);
    }

    public void setAttachment(TimerKey key, Object attachment)
    {
        if (attachment != null)
        {
            attachments.put(key, attachment);
        }
        else
        {
            attachments.remove(key);
        }
    }

        public int getRemainingTicks(boolean isPlayer, TimerKey key)
    {
        if (isPlayer || key == null)
        {
            if (key != null && key.name().equals("VENGEANCE_COOLDOWN"))
            {
                return vengeanceCooldown;
            }
            return timers.getOrDefault(key, 0);
        }

        TargetState state = getTargetState(getCurrentTarget());
        if (state == null)
        {
            return 0;
        }

        switch (key)
        {
            case FREEZE:
                return state.freezeTicks;
            case FREEZE_IMMUNITY:
                return state.immunityTicks;
            case COMBAT_ATTACK:
                return state.attackCooldown;
            case POTION:
                return state.potionCooldown;
            default:
                return 0;
        }
    }

public int getTargetFreezeTicks(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.freezeTicks : 0;
    }

    public int getTargetFreezeImmunityTicks(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.immunityTicks : 0;
    }

    public int getTargetAttackCooldown(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.attackCooldown : 0;
    }

    public int getTargetPotionCooldown(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.potionCooldown : 0;
    }

    public int getTargetPendingHitTicks(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.pendingHitTicks : 0;
    }

    public boolean didTargetJustAttack(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null && state.lastAttackTick == client.getTickCount();
    }

    public boolean playerHasPidOverTarget(Actor target)
    {
        TargetState state = getTargetState(target);
        if (state == null)
        {
            return false;
        }

        int currentTick = client.getTickCount();
        boolean playerThisTick = lastAttackTick == currentTick;
        boolean targetThisTick = state.lastAttackTick == currentTick;

        if (playerThisTick && !targetThisTick)
        {
            return true;
        }

        if (!playerThisTick && targetThisTick)
        {
            return false;
        }

        if (playerThisTick && targetThisTick)
        {
            return playerPendingHitTicks <= state.pendingHitTicks || state.pendingHitTicks == 0;
        }

        return lastAttackTick >= state.lastAttackTick;
    }

    public int getTargetVengeanceCooldownTicks(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.vengeanceCooldown : 0;
    }

    public int getVengeanceCooldown()
    {
        return vengeanceCooldown;
    }

    public boolean isVengeanceReady()
    {
        return vengeanceCooldown == 0;
    }


    public CombatType getPlayerCombatType()
    {
        return playerCombatType;
    }

    public int getPlayerLastAttackTick()
    {
        return lastAttackTick;
    }

    public CombatType getTargetLastAttackType(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.lastAttackType : null;
    }

    public int getTargetLastAttackTick(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.lastAttackTick : -1;
    }

    public int getTargetLastHitsplatTick(Actor target)
    {
        TargetState state = getTargetState(target);
        return state != null ? state.lastHitsplatTick : -1;
    }

    /**
     * Set reference to CombatAdapter to enable timer processing on opponent targets.
     * This allows TimerManagerAdapter to call processTimers() on OpponentElvargPlayer instances.
     */
    public void setCombatAdapter(CombatAdapter combatAdapter) {
        this.combatAdapter = combatAdapter;
    }

    @Override
    public int getTicks(TimerKey key) {
        // Return the timer value for the given TimerKey
        // This is used by NhEnvironment for FREEZE, FREEZE_IMMUNITY, etc.
        // Vengeance is tracked separately via vengeanceCooldown field
        return timers.getOrDefault(key, 0);
    }

    public void shutdown()
    {
        eventBus.unregister(this);
        timers.clear();
        attachments.clear();
        targetStates.clear();
    }
}
