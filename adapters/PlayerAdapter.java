package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.Combat;
import com.elvarg.game.content.combat.CombatType;
import com.elvarg.game.content.combat.hit.PendingHit;
import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.content.sound.Sound;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.model.*;
import com.elvarg.game.model.container.impl.Equipment;
import com.elvarg.game.model.container.impl.Inventory;
import com.elvarg.game.model.movement.MovementQueue;
import com.elvarg.util.timers.TimerRepository;
import com.elvarg.game.definition.ItemDefinition;
import com.elvarg.game.content.combat.WeaponInterfaces.WeaponInterface;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Main adapter that bridges RuneLite's Player to Elvarg's Mobile system.
 * This is the central adapter that NhEnvironment will interact with.
 * Aggregates all the sub-adapters (Equipment, Inventory, Skills, etc).
 */
@Slf4j
public class PlayerAdapter extends Mobile {

    private final Client client;
    private final EventBus eventBus;

    // Sub-adapters - these wrap instances rather than extend
    private final EquipmentAdapter equipmentAdapter;
    private final InventoryAdapter inventoryAdapter;
    private final SkillManagerWrapperAdapter skillManagerAdapter;
    private final TimerManagerAdapter timerManagerAdapter;
    private final MovementQueueAdapter movementQueueAdapter;
    private final CombatAdapter combatAdapter;
    private final ItemManager itemManager;

    // Combat state - volatile for thread safety (RuneLite EventBus thread vs game thread)
    private volatile boolean specialActivated = false;
    private volatile int specialPercentage = 100;
    private volatile boolean hasVengeance = false;
    private volatile Mobile interactingMobile = null;
    private volatile Mobile combatFollowing = null;
    private volatile WeaponInterface currentWeapon = WeaponInterface.UNARMED;

    // Prayer tracking - delegated to PrayerHandlerAdapter
    private final PrayerHandlerAdapter prayerHandlerAdapter;

    // Position tracking
    private LocationAdapter currentLocation;

    public PlayerAdapter(Client client, EventBus eventBus, PrayerHandlerAdapter prayerHandlerAdapter, ItemManager itemManager) {
        super(getInitialLocation(client));
        this.client = client;
        this.eventBus = eventBus;
        this.prayerHandlerAdapter = prayerHandlerAdapter;
        this.itemManager = itemManager;

        // Don't update location in constructor - wait for game to be loaded

        // Create sub-adapters - use a dummy Player to satisfy constructors
        DummyElvargPlayer dummyPlayer = new DummyElvargPlayer();
        this.equipmentAdapter = new EquipmentAdapter(client, dummyPlayer);
        this.inventoryAdapter = new InventoryAdapter(client, dummyPlayer);
        this.skillManagerAdapter = new SkillManagerWrapperAdapter(client);
        this.timerManagerAdapter = new TimerManagerAdapter(client, eventBus);
        this.movementQueueAdapter = new MovementQueueAdapter(client, this);
        this.combatAdapter = new CombatAdapter(client, this, eventBus, itemManager);

        // Register for game ticks
        eventBus.register(this);
    }

    private static Location getInitialLocation(Client client) {
        if (client.getLocalPlayer() != null) {
            WorldPoint wp = client.getLocalPlayer().getWorldLocation();
            return new Location(wp.getX(), wp.getY(), wp.getPlane());
        }
        return new Location(3200, 3200, 0); // Default location
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Update location
        updateLocation();

        // Update special attack
        int spec = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
        if (spec != specialPercentage) {
            specialPercentage = spec;
            log.debug("[ADAPTER] Special attack updated: {}%", specialPercentage);
        }

        // Update special activated
        boolean specActive = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 1;
        if (specActive != specialActivated) {
            specialActivated = specActive;
            log.debug("[ADAPTER] Special attack {}", specialActivated ? "activated" : "deactivated");
        }

        // Prayer updates now handled by PrayerHandlerAdapter via its own @Subscribe

        // Update weapon field for CombatStyles.getCombatType() to work
        // Citation: CombatStyles.java:27-34 requires player.getWeapon()
        // Citation: NhEnvironment.java:883-893 uses CombatStyles.getCombatType(getAgent())
        // Citation: OpponentElvargPlayer.java:76-85 shows correct pattern
        Item weaponItem = equipmentAdapter.get(Equipment.WEAPON_SLOT);
        if (weaponItem != null && weaponItem.getId() > 0) {
            ItemDefinition def = ItemDefinition.forId(weaponItem.getId());
            if (def != null && def.getWeaponInterface() != null) {
                currentWeapon = def.getWeaponInterface();
            } else {
                currentWeapon = WeaponInterface.UNARMED;
            }
        } else {
            currentWeapon = WeaponInterface.UNARMED;
        }

    }
    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        // Check for vengeance graphic (726)
        net.runelite.api.Actor actor = event.getActor();
        if (actor == client.getLocalPlayer()) {
            int graphicId = actor.getGraphic();
            if (graphicId == 726) {
                hasVengeance = true;
                log.debug("[ADAPTER] Vengeance detected on local player");
            } else if (hasVengeance && graphicId != 726) {
                hasVengeance = false;
                log.debug("[ADAPTER] Vengeance expired on local player");
            }
        }
    }

    private void updateLocation() {
        if (client.getLocalPlayer() != null) {
            WorldPoint wp = client.getLocalPlayer().getWorldLocation();
            currentLocation = new LocationAdapter(wp);
            setLocation(currentLocation);
        }
    }


    // Getters for sub-adapters
    public Equipment getEquipment() {
        return equipmentAdapter;
    }

    public Inventory getInventory() {
        return inventoryAdapter;
    }

    public SkillManager getSkillManager() {
        return skillManagerAdapter;
    }

    public WeaponInterface getWeapon() {
        return currentWeapon;
    }

    @Override
    public TimerRepository getTimers() {
        return timerManagerAdapter;
    }

    @Override
    public MovementQueue getMovementQueue() {
        return movementQueueAdapter.getMovementQueue();
    }

    @Override
    public Combat getCombat() {
        return combatAdapter;
    }

    // Required abstract method implementations from Mobile
    @Override
    public void onAdd() {
        log.debug("[ADAPTER] Player added to world");
    }

    @Override
    public void onRemove() {
        log.debug("[ADAPTER] Player removed from world");
        eventBus.unregister(this);
        if (timerManagerAdapter != null) {
            timerManagerAdapter.shutdown();
        }
    }

    @Override
    public PendingHit manipulateHit(PendingHit hit) {
        // This would handle damage reduction from prayers, etc.
        // For now, pass through unchanged
        return hit;
    }

    @Override
    public void appendDeath() {
        log.debug("[ADAPTER] Player death");
    }

    @Override
    public void heal(int amount) {
        log.debug("[ADAPTER] Healing {} HP (read-only in RuneLite)", amount);
    }

    @Override
    public int getHitpoints() {
        return skillManagerAdapter.getCurrentLevel(Skill.HITPOINTS);
    }

    @Override
    public Mobile setHitpoints(int hitpoints) {
        log.debug("[ADAPTER] Cannot set hitpoints in RuneLite (read-only): {}", hitpoints);
        return this;
    }

    @Override
    public int getBaseAttack(CombatType type) {
        switch (type) {
            case RANGED:
                return skillManagerAdapter.getCurrentLevel(Skill.RANGED);
            case MAGIC:
                return skillManagerAdapter.getCurrentLevel(Skill.MAGIC);
            default:
                return skillManagerAdapter.getCurrentLevel(Skill.ATTACK);
        }
    }

    @Override
    public int getBaseDefence(CombatType type) {
        if (type == CombatType.MAGIC) {
            return skillManagerAdapter.getCurrentLevel(Skill.MAGIC);
        }
        return skillManagerAdapter.getCurrentLevel(Skill.DEFENCE);
    }

    @Override
    public int getBaseAttackSpeed() {
        // Get weapon attack speed
        net.runelite.api.ItemContainer equipment = client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT);
        if (equipment != null) {
            net.runelite.api.Item weapon = equipment.getItem(3); // Weapon slot
            if (weapon != null) {
                // Return weapon-specific attack speed
                return getWeaponSpeed(weapon.getId());
            }
        }
        return 4; // Default attack speed
    }

    private int getWeaponSpeed(int weaponId) {
        // Common weapon speeds in ticks
        switch (weaponId) {
            case ItemID.ABYSSAL_WHIP:
            case ItemID.ABYSSAL_TENTACLE:
                return 4;
            case ItemID.AGS:
            case ItemID.AGSG:
            case ItemID.BGS:
            case ItemID.BGSG:
            case ItemID.SGS:
            case ItemID.SGSG:
            case ItemID.ZGS:
            case ItemID.ZGSG:
                return 6;
            case ItemID.TOXIC_BLOWPIPE:
            case ItemID.TOXIC_BLOWPIPE_LOADED:
                return 3;
            case ItemID.ACB:
            case ItemID.XBOWS_CROSSBOW_DRAGON:
                return 5;
            case ItemID.TWISTED_BOW:
                return 5;
            case ItemID.DRAGON_CLAWS:
                return 4;
            default:
                return 4; // Default speed
        }
    }

    @Override
    public int getAttackAnim() {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null) {
            return localPlayer.getAnimation();
        }
        return -1;
    }

    @Override
    public Sound getAttackSound() {
        // Return null as sounds aren't relevant for this adapter
        return null;
    }

    @Override
    public int getBlockAnim() {
        // Block animation - typically weapon-specific
        return 424; // Default block animation
    }

    // Required implementations from Entity
    @Override
    public void performAnimation(Animation animation) {
        log.debug("[ADAPTER] Cannot perform animation in RuneLite (read-only): {}", animation);
    }

    @Override
    public void performGraphic(Graphic graphic) {
        log.debug("[ADAPTER] Cannot perform graphic in RuneLite (read-only): {}", graphic);
    }

    @Override
    public int size() {
        return 1; // Players are always size 1
    }

    // Mobile-specific getters/setters
    @Override
    public boolean[] getPrayerActive() {
        // Delegate to PrayerHandlerAdapter for centralized prayer state management
        // Source: runelite-client/src/main/java/net/runelite/client/plugins/autopvp/adapters/PrayerHandlerAdapter.java:138
        return prayerHandlerAdapter.getPrayerActive();
    }

    @Override
    public boolean isSpecialActivated() {
        return specialActivated;
    }

    @Override
    public void setSpecialActivated(boolean specialActivated) {
        log.debug("[ADAPTER] Cannot set special attack in RuneLite (read-only): {}", specialActivated);
    }

    @Override
    public int getSpecialPercentage() {
        return specialPercentage;
    }

    @Override
    public void setSpecialPercentage(int specialPercentage) {
        log.debug("[ADAPTER] Cannot set special percentage in RuneLite (read-only): {}", specialPercentage);
    }

    @Override
    public boolean hasVengeance() {
        return hasVengeance;
    }

    @Override
    public void setHasVengeance(boolean hasVengeance) {
        this.hasVengeance = hasVengeance;
    }

    @Override
    public Mobile getInteractingMobile() {
        return interactingMobile;
    }

    public void setInteractingMobile(Mobile interactingMobile) {
        this.interactingMobile = interactingMobile;
    }

    @Override
    public Mobile getCombatFollowing() {
        return combatFollowing;
    }

    @Override
    public void setCombatFollowing(Mobile combatFollowing) {
        this.combatFollowing = combatFollowing;
    }

    /**
     * Clean up resources when adapter is no longer needed.
     */
    public void shutdown() {
        onRemove();
    }

    // Helper method to check if player is the local player
    public boolean isLocalPlayer() {
        return true; // This adapter always represents the local player
    }

    // Helper to get RuneLite player
    public net.runelite.api.Player getRuneLitePlayer() {
        return client.getLocalPlayer();
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    // Additional methods needed by DelegatingElvargPlayer
    private int lastDamage = 0;

    public int getLastDamage() {
        return lastDamage;
    }

    public void setLastDamage(int damage) {
        this.lastDamage = damage;
    }

    @Override
    public Mobile setPositionToFace(Location position) {
        log.debug("[ADAPTER] Cannot set position to face in RuneLite (read-only): {}", position);
        return this;
    }
}
