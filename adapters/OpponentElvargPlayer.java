package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.PrayerHandler;
import com.elvarg.game.content.combat.WeaponInterfaces.WeaponInterface;
import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.definition.ItemDefinition;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.Skill;
import com.elvarg.game.model.container.impl.Equipment;
import com.elvarg.game.model.SecondsTimer;
import com.elvarg.util.timers.TimerRepository;
import com.elvarg.util.timers.TimerKey;
import net.runelite.api.HeadIcon;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.kit.KitType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.autopvp.core.ItemDefinitionInitializer;
import net.runelite.client.plugins.autopvp.util.TargetEquipmentTranslator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Arrays;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that wraps a RuneLite Player as an Elvarg Player for opponent tracking.
 * Provides read-only access to opponent's visible state.
 */
@Slf4j
public class OpponentElvargPlayer extends DummyElvargPlayer {

    private static final Map<KitType, Integer> KIT_TO_SLOT = new EnumMap<>(KitType.class);
    static {
        KIT_TO_SLOT.put(KitType.HEAD, Equipment.HEAD_SLOT);
        KIT_TO_SLOT.put(KitType.CAPE, Equipment.CAPE_SLOT);
        KIT_TO_SLOT.put(KitType.AMULET, Equipment.AMULET_SLOT);
        KIT_TO_SLOT.put(KitType.WEAPON, Equipment.WEAPON_SLOT);
        KIT_TO_SLOT.put(KitType.TORSO, Equipment.BODY_SLOT);
        KIT_TO_SLOT.put(KitType.SHIELD, Equipment.SHIELD_SLOT);
        KIT_TO_SLOT.put(KitType.LEGS, Equipment.LEG_SLOT);
        KIT_TO_SLOT.put(KitType.HANDS, Equipment.HANDS_SLOT);
        KIT_TO_SLOT.put(KitType.BOOTS, Equipment.FEET_SLOT);
    }

    private final EventBus eventBus;
    private final ItemManager itemManager;
    private final net.runelite.api.Player runelitePlayer;
    private final Equipment equipmentSnapshot;
    private final double[] baselineBonuses;
    private final Supplier<DynamicTargetPlayer> dynamicTargetSupplier;
    private boolean registeredWithBus = false;
    private volatile TargetEquipmentTranslator.Result lastTranslation = TargetEquipmentTranslator.Result.empty(-1);
    private volatile double[] blendedBonuses = new double[TargetEquipmentTranslator.BONUS_COUNT];
    private volatile double equipmentConfidence = 0.0;
    // Timer tracking fields matching Elvarg structure
    // Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/entity/impl/player/Player.java:192
    private final SecondsTimer vengeanceTimer = new SecondsTimer();
    // Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/content/combat/Combat.java:30-31
    private final SecondsTimer teleblockTimer = new SecondsTimer();
    private final SecondsTimer prayerBlockTimer = new SecondsTimer();
    private int freezeDelay = 0; // Tracked in ticks
    private final SkillManager readOnlySkillManager;
    private final TimerRepository timerRepository;

    public OpponentElvargPlayer(EventBus eventBus, ItemManager itemManager, net.runelite.api.Player runelitePlayer, double[] loadoutBaseline, Supplier<DynamicTargetPlayer> dynamicTargetSupplier) {
        super();
        this.eventBus = eventBus;
        this.itemManager = itemManager;
        this.runelitePlayer = runelitePlayer;
        this.baselineBonuses = loadoutBaseline != null ? loadoutBaseline.clone() : new double[TargetEquipmentTranslator.BONUS_COUNT];
        this.dynamicTargetSupplier = dynamicTargetSupplier;

        // Create read-only equipment wrapper
        this.equipmentSnapshot = new Equipment(this);

        // Create mock skill manager that doesn't use Netty
        this.readOnlySkillManager = new MockSkillManager(this);
        initializeSkillManager();

        // Create timer repository for tracking freeze and other timers
        this.timerRepository = new TimerRepository();

        if (this.eventBus != null) {
            this.eventBus.register(this);
            registeredWithBus = true;
        }

        refreshEquipmentSnapshot();
    }

    private void initializeSkillManager() {
        // Set combat levels from visible player data
        // RuneLite only provides combat level, not individual skills for other players
        // Use reasonable defaults based on combat level
        int combatLevel = runelitePlayer.getCombatLevel();

        // Estimate skills based on combat level (rough approximation)
        // For a pure NH account around 90 combat: 75 att, 99 str, 1 def, 99 range, 99 mage, 52 pray, 99 hp
        if (combatLevel >= 85 && combatLevel <= 95) {
            // Likely pure build
            readOnlySkillManager.setLevel(Skill.ATTACK, 75);
            readOnlySkillManager.setLevel(Skill.STRENGTH, 99);
            readOnlySkillManager.setLevel(Skill.DEFENCE, 1);
            readOnlySkillManager.setLevel(Skill.RANGED, 99);
            readOnlySkillManager.setLevel(Skill.MAGIC, 99);
            readOnlySkillManager.setLevel(Skill.PRAYER, 52);
            readOnlySkillManager.setLevel(Skill.HITPOINTS, 99);
        } else if (combatLevel >= 120) {
            // Likely main/zerker build
            readOnlySkillManager.setLevel(Skill.ATTACK, 99);
            readOnlySkillManager.setLevel(Skill.STRENGTH, 99);
            readOnlySkillManager.setLevel(Skill.DEFENCE, 75);
            readOnlySkillManager.setLevel(Skill.RANGED, 99);
            readOnlySkillManager.setLevel(Skill.MAGIC, 99);
            readOnlySkillManager.setLevel(Skill.PRAYER, 99);
            readOnlySkillManager.setLevel(Skill.HITPOINTS, 99);
        } else {
            // Default mid-level stats
            int avgLevel = Math.max(50, Math.min(99, combatLevel));
            readOnlySkillManager.setLevel(Skill.ATTACK, avgLevel);
            readOnlySkillManager.setLevel(Skill.STRENGTH, avgLevel);
            readOnlySkillManager.setLevel(Skill.DEFENCE, avgLevel);
            readOnlySkillManager.setLevel(Skill.RANGED, avgLevel);
            readOnlySkillManager.setLevel(Skill.MAGIC, avgLevel);
            readOnlySkillManager.setLevel(Skill.PRAYER, avgLevel);
            readOnlySkillManager.setLevel(Skill.HITPOINTS, avgLevel);
        }
    }

        private void refreshEquipmentSnapshot()
    {
        equipmentSnapshot.resetItems();

        if (runelitePlayer == null)
        {
            return;
        }

        PlayerComposition composition = runelitePlayer.getPlayerComposition();
        if (composition == null)
        {
            return;
        }

        int[] equipmentIds = composition.getEquipmentIds();
        if (equipmentIds == null)
        {
            return;
        }

        for (Map.Entry<KitType, Integer> entry : KIT_TO_SLOT.entrySet())
        {
            int kitIndex = entry.getKey().getIndex();
            if (kitIndex >= equipmentIds.length)
            {
                continue;
            }

            int decodedId = decodeItemId(equipmentIds[kitIndex]);
            if (decodedId == -1)
            {
                continue;
            }

            int canonicalId = itemManager != null ? itemManager.canonicalize(decodedId) : decodedId;
            if (canonicalId <= 0)
            {
                continue;
            }

            equipmentSnapshot.setItem(entry.getValue(), new Item(canonicalId, 1));
        }

        // Set weapon field so CombatStyles.getCombatType() works
        // Citation: WeaponInterfaces.java:27-29 pattern
        // Citation: Player.java:1677 setWeapon method exists
        Item weaponItem = equipmentSnapshot.getItems()[Equipment.WEAPON_SLOT];
        if (weaponItem != null && weaponItem.getId() > 0) {
            ItemDefinition def = ItemDefinition.forId(weaponItem.getId());
            if (def != null && def.getWeaponInterface() != null) {
                setWeapon(def.getWeaponInterface());
            } else {
                setWeapon(WeaponInterface.UNARMED);
            }
        } else {
            setWeapon(WeaponInterface.UNARMED);
        }

        // Initialize special attack to 100% for opponents
        updateSpecialAttack();

        // Ensure all equipment items have definitions with bonuses
        // Citation: NhEnvironment.java:635 calls getBonuses()[9]
        for (Item item : equipmentSnapshot.getItems()) {
            if (item != null && item.getId() > 0) {
                ItemDefinitionInitializer.ensureItemDefinition(item.getId(), itemManager);
            }
        }

        TargetEquipmentTranslator.Result translation = TargetEquipmentTranslator.translate(composition, itemManager, -1);
        lastTranslation = translation;
        blendedBonuses = TargetEquipmentTranslator.blendBonuses(translation, baselineBonuses);
        equipmentConfidence = translation.getAverageSlotConfidence();

        // Forward translation to DynamicTargetPlayer
        DynamicTargetPlayer dynamicTargetPlayer = dynamicTargetSupplier != null ? dynamicTargetSupplier.get() : null;
        if (dynamicTargetPlayer != null)
        {
            dynamicTargetPlayer.onOpponentEquipmentUpdate(translation, blendedBonuses);
        }
    }

    public double[] getBlendedBonuses() {
        return blendedBonuses != null ? Arrays.copyOf(blendedBonuses, blendedBonuses.length) : new double[TargetEquipmentTranslator.BONUS_COUNT];
    }

    public TargetEquipmentTranslator.Result getLastTranslation() {
        return lastTranslation;
    }

    public double getEquipmentConfidence() {
        return equipmentConfidence;
    }

    private int decodeItemId(int encodedId) {
        if (encodedId < 0) {
            return -1;
        }
        if (encodedId >= PlayerComposition.ITEM_OFFSET) {
            return encodedId - PlayerComposition.ITEM_OFFSET;
        }
        return -1;
    }

    @Override
    public int getHitpoints() {
        // First check if we have tracked HP from hitsplats (most accurate)
        if (readOnlySkillManager instanceof MockSkillManager) {
            MockSkillManager mockSkillManager = (MockSkillManager) readOnlySkillManager;
            int trackedHp = mockSkillManager.getCurrentLevel(Skill.HITPOINTS);
            int maxHp = mockSkillManager.getMaxLevel(Skill.HITPOINTS);

            // If we have tracked damage, use that
            if (trackedHp < maxHp) {
                return trackedHp;
            }
        }

        // Try to get health from HealthBar if visible
        // Source: runelite-api/src/main/java/net/runelite/api/Actor.java getHealthRatio/getHealthScale
        int healthRatio = runelitePlayer.getHealthRatio();
        int healthScale = runelitePlayer.getHealthScale();

        if (healthRatio >= 0 && healthScale > 0) {
            // Calculate actual HP from health bar
            int maxHp = readOnlySkillManager.getMaxLevel(Skill.HITPOINTS);
            // Improved health calculation with proper rounding
            int currentHp = Math.max(1, (healthRatio * maxHp + healthScale / 2) / healthScale);

            // If health ratio is 0, player is at 0 HP (dead)
            if (healthRatio == 0) {
                currentHp = 0;
            }

            // UPDATE SkillManager with real HP for NhEnvironment observations
            if (readOnlySkillManager instanceof MockSkillManager) {
                ((MockSkillManager) readOnlySkillManager).updateCurrentHitpoints(currentHp);
            }

            return currentHp;
        }

        // If no health bar visible, assume full HP (player hasn't taken damage in combat)
        return readOnlySkillManager.getMaxLevel(Skill.HITPOINTS);
    }

    @Override
    public Location getLocation() {
        // Convert RuneLite WorldPoint to Elvarg Location
        // Source: runelite-api/src/main/java/net/runelite/api/coords/WorldPoint.java
        WorldPoint wp = runelitePlayer.getWorldLocation();
        return new Location(wp.getX(), wp.getY(), wp.getPlane());
    }

    public boolean getPrayerActive(int prayer) {
        // Decode from overhead icon
        // Source: runelite-api/src/main/java/net/runelite/api/HeadIcon.java
        HeadIcon overhead = runelitePlayer.getOverheadIcon();

        if (overhead == null) {
            return false;
        }

        // Map HeadIcon to Elvarg prayer indices
        // Based on com.elvarg.game.content.PrayerHandler prayer indices
        switch (overhead) {
            case MELEE:
                return prayer == PrayerHandler.PROTECT_FROM_MELEE;
            case RANGED:
                return prayer == PrayerHandler.PROTECT_FROM_MISSILES;
            case MAGIC:
                return prayer == PrayerHandler.PROTECT_FROM_MAGIC;
            case RETRIBUTION:
                return prayer == PrayerHandler.RETRIBUTION;
            case SMITE:
                return prayer == PrayerHandler.SMITE;
            case REDEMPTION:
                return prayer == PrayerHandler.REDEMPTION;
            default:
                return false;
        }
    }

    @Override
    public Equipment getEquipment() {
        refreshEquipmentSnapshot();
        return equipmentSnapshot;
    }

    @Subscribe
    public void onPlayerChanged(PlayerChanged event) {
        if (event.getPlayer() == runelitePlayer) {
            refreshEquipmentSnapshot();
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() != runelitePlayer) {
            return;
        }
        DynamicTargetPlayer dynamicTargetPlayer = dynamicTargetSupplier != null ? dynamicTargetSupplier.get() : null;
        if (dynamicTargetPlayer != null) {
            dynamicTargetPlayer.onTargetAnimation(runelitePlayer.getAnimation());
        }
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        if (event.getActor() != runelitePlayer) {
            return;
        }
        DynamicTargetPlayer dynamicTargetPlayer = dynamicTargetSupplier != null ? dynamicTargetSupplier.get() : null;
        if (dynamicTargetPlayer != null) {
            dynamicTargetPlayer.onTargetGraphic(runelitePlayer.getGraphic());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() != runelitePlayer) {
            return;
        }

        // Track damage for vengeance expiration
        DynamicTargetPlayer dynamicTargetPlayer = dynamicTargetSupplier != null ? dynamicTargetSupplier.get() : null;
        if (dynamicTargetPlayer != null && event.getHitsplat() != null) {
            int damage = event.getHitsplat().getAmount();
            if (damage > 0) {
                dynamicTargetPlayer.onTargetDamaged(damage);

                // Update our HP tracking based on damage taken
                updateHPFromDamage(damage);
            }
        }
    }

    /**
     * Update HP tracking when damage is observed via hitsplats.
     * This provides more accurate HP tracking than health bar ratios alone.
     */
    private void updateHPFromDamage(int damage) {
        if (readOnlySkillManager instanceof MockSkillManager) {
            MockSkillManager mockSkillManager = (MockSkillManager) readOnlySkillManager;
            int currentHp = mockSkillManager.getCurrentLevel(Skill.HITPOINTS);
            int newHp = Math.max(0, currentHp - damage);
            mockSkillManager.updateCurrentHitpoints(newHp);
            log.debug("[OPPONENT] {} took {} damage, HP: {} -> {}",
                runelitePlayer.getName(), damage, currentHp, newHp);
        }
    }

    public void shutdown() {
        if (registeredWithBus && eventBus != null) {
            eventBus.unregister(this);
            registeredWithBus = false;
        }
    }

    @Override
    public SkillManager getSkillManager() {
        return readOnlySkillManager;
    }

    /**
     * Get the TimerRepository for tracking freeze and other timers.
     * Citation: NhEnvironment.java:224 calls target.getTimers().getTicks(TimerKey.FREEZE)
     */
    @Override
    public TimerRepository getTimers() {
        return timerRepository;
    }

    public String getUsername() {
        return runelitePlayer.getName();
    }

    public int getCombatLevel() {
        return runelitePlayer.getCombatLevel();
    }

    @Override
    public boolean isRegistered() {
        // Opponent is registered if they're in the game world
        return runelitePlayer != null;
    }

    /**
     * Get the underlying RuneLite player.
     */
    public net.runelite.api.Player getRunelitePlayer() {
        return runelitePlayer;
    }
    /**
     * Get vengeance timer - matches Elvarg API.
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/entity/impl/player/Player.java:710
     */
    public SecondsTimer getVengeanceTimer() {
        return vengeanceTimer;
    }

    /**
     * Get teleblock timer from Combat - matches Elvarg API.
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/content/combat/Combat.java:278
     */
    public SecondsTimer getTeleblockTimer() {
        return teleblockTimer;
    }

    /**
     * Get prayer block timer from Combat - matches Elvarg API.
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/content/combat/Combat.java:282
     */
    public SecondsTimer getPrayerBlockTimer() {
        return prayerBlockTimer;
    }

    /**
     * Get freeze delay in ticks.
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/entity/impl/Mobile.java:712
     */
    public int getFreezeDelay() {
        return freezeDelay;
    }

    /**
     * Update vengeance timer when vengeance is detected (30 seconds).
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/model/timers/GameTimer.java VENGEANCE
     */
    public void updateVengeanceTimer() {
        vengeanceTimer.start(30);
        log.debug("[OPPONENT] {} vengeance timer set to 30 seconds", runelitePlayer.getName());
    }

    /**
     * Update freeze timer when freeze spell is detected.
     * @param durationTicks Freeze duration in game ticks
     */
    public void updateFreezeTimer(int durationTicks) {
        freezeDelay = durationTicks;
        // Register freeze timer in TimerRepository for naton1 compatibility
        timerRepository.register(TimerKey.FREEZE, durationTicks);
        log.debug("[OPPONENT] {} frozen for {} ticks", runelitePlayer.getName(), durationTicks);
    }

    /**
     * Update teleblock timer when teleblock is detected.
     * @param protectFromMagic Whether target has Protect from Magic active
     */
    public void updateTeleblockTimer(boolean protectFromMagic) {
        int duration = protectFromMagic ? 150 : 300; // 2.5 or 5 minutes
        teleblockTimer.start(duration);
        log.debug("[OPPONENT] {} teleblocked for {} seconds", runelitePlayer.getName(), duration);
    }

    /**
     * Process timers each tick (called by game loop).
     */
    public void processTimers() {
        // Decrement freeze delay and sync with TimerRepository
        if (freezeDelay > 0) {
            freezeDelay--;
            // Update TimerRepository for naton1 compatibility
            timerRepository.register(TimerKey.FREEZE, freezeDelay);
        } else {
            // Cancel freeze timer when it expires
            timerRepository.cancel(TimerKey.FREEZE);
        }

        // Process TimerRepository timers (decrements all active timers)
        timerRepository.process();

        // SecondsTimers process themselves via their finish() checks
        // They auto-decrement when checked
    }

    /**
     * Track special attack percentage for opponents.
     * Citation: Mobile.java:78 has specialPercentage field
     * Citation: Mobile.java:565 has setSpecialPercentage method
     * Note: For opponents, we start at 100% and estimate based on special attacks observed
     */
    public void updateSpecialAttack() {
        // For opponents, we can't directly read their special attack %
        // This will be enhanced to track based on special attacks used
        // For now, just ensure it's initialized to 100 if not set
        if (getSpecialPercentage() == 0) {
            setSpecialPercentage(100);
        }
    }

    public double getPid() {
        // Citation: NhEnvironment.java:1760 calls getTarget().getPid()
        // Citation: Mobile.java has getIndex() method that serves as PID
        return getIndex();
    }
}










