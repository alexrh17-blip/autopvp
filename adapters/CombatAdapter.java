package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.Combat;
import com.elvarg.game.content.combat.hit.HitQueue;
import com.elvarg.game.content.combat.magic.CombatSpell;
import com.elvarg.game.content.combat.method.CombatMethod;
import com.elvarg.game.content.combat.ranged.RangedData.Ammunition;
import com.elvarg.game.content.combat.ranged.RangedData.RangedWeapon;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.SecondsTimer;
import com.elvarg.util.Stopwatch;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Adapter that bridges RuneLite's combat state to Elvarg's Combat system.
 * Tracks combat state, targets, and combat-related timers.
 */
@Slf4j
public class CombatAdapter extends Combat {

    private final Client client;
    private final Mobile owner;
    private final EventBus eventBus;
    private final ItemManager itemManager;
    private net.runelite.client.plugins.autopvp.core.NhEnvironmentBridge environmentBridge;

    private volatile double[] targetGearBaseline;
    private volatile Supplier<DynamicTargetPlayer> dynamicTargetSupplier;
    private OpponentElvargPlayer opponentElvargPlayer;

    // Combat state - volatile for thread safety (RuneLite EventBus thread vs game thread)
    private volatile Mobile target;
    private volatile Mobile attacker;
    private final Stopwatch lastAttack = new Stopwatch();
    private final HitQueue hitQueue = new HitQueue();

    // Timers
    private final SecondsTimer poisonImmunityTimer = new SecondsTimer();
    private final SecondsTimer fireImmunityTimer = new SecondsTimer();
    private final SecondsTimer teleblockTimer = new SecondsTimer();
    private final SecondsTimer prayerBlockTimer = new SecondsTimer();

    // Ranged tracking - volatile for thread safety (RuneLite EventBus thread vs game thread)
    private volatile RangedWeapon rangedWeapon;
    private volatile Ammunition rangeAmmoData;

    // Magic tracking - volatile for thread safety (RuneLite EventBus thread vs game thread)
    private volatile CombatSpell castSpell;
    private volatile CombatSpell autoCastSpell;
    private volatile CombatSpell previousCast;

    // Combat method tracking - cached and updated each tick
    private volatile CombatMethod currentMethod = CombatFactoryAdapter.MELEE_COMBAT;

    public CombatAdapter(Client client, Mobile owner, EventBus eventBus, ItemManager itemManager) {
        super(owner);
        this.client = client;
        this.owner = owner;
        this.eventBus = eventBus;
        this.itemManager = itemManager;

        // Register for events
        if (eventBus != null) {
            eventBus.register(this);
        }

        // Don't update state in constructor - wait for game to be loaded
    }

    /**
     * Set the environment bridge for target updates.
     */
    public void setEnvironmentBridge(net.runelite.client.plugins.autopvp.core.NhEnvironmentBridge bridge) {
        this.environmentBridge = bridge;
    }

    /**
     * Provide baseline bonuses derived from NhLoadout for hidden slots.
     */
    public void setTargetGearBaseline(double[] baseline) {
        this.targetGearBaseline = baseline != null ? baseline.clone() : null;
    }

    /**
     * Supplier used to forward opponent events to DynamicTargetPlayer.
     */
    public void setOpponentTargetSupplier(Supplier<DynamicTargetPlayer> supplier) {
        this.dynamicTargetSupplier = supplier;
    }



    /**
     * Updates combat state from RuneLite client.
     */
    public void updateCombatState() {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        // Update target from interacting actor
        Actor interacting = localPlayer.getInteracting();
        boolean targetChanged = false;
        if (interacting != null) {
            // Target is set but we can't create Mobile adapters for other entities
            // This would need a more complex adapter system for other players/NPCs
            log.debug("[COMBAT] Player interacting with: {}", interacting.getName());
            targetChanged = (target == null);
        } else {
            targetChanged = (target != null);
            disposeOpponentTarget();
            target = null;
        }

        // Notify environment bridge if target changed
        if (targetChanged && environmentBridge != null) {
            environmentBridge.updateTarget();
        }

        // Update weapon/ammo state
        updateRangedState();

        // Update combat method based on current state
        updateCombatMethod();
    }

    private void updateRangedState() {
        net.runelite.api.ItemContainer equipment = client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT);
        if (equipment == null) {
            return;
        }

        // Check weapon slot
        net.runelite.api.Item weapon = equipment.getItem(3);
        if (weapon != null) {
            rangedWeapon = getRangedWeapon(weapon.getId());
        }

        // Check ammo slot
        net.runelite.api.Item ammo = equipment.getItem(9);
        if (ammo != null) {
            rangeAmmoData = getAmmunition(ammo.getId());
        }
    }

    private RangedWeapon getRangedWeapon(int itemId) {
        // Map common ranged weapons to Elvarg's RangedWeapon enum
        switch (itemId) {
            case ItemID.TOXIC_BLOWPIPE:
            case ItemID.TOXIC_BLOWPIPE_LOADED:
                return RangedWeapon.TOXIC_BLOWPIPE;
            case ItemID.ACB:
                return RangedWeapon.ARMADYL_CROSSBOW;
            case ItemID.ZARYTE_XBOW:
                return RangedWeapon.ZARYTE_CROSSBOW;
            case ItemID.XBOWS_CROSSBOW_RUNITE:
                return RangedWeapon.RUNE_CROSSBOW;
            case ItemID.MAGIC_SHORTBOW:
            case ItemID.MAGIC_SHORTBOW_I:
                return RangedWeapon.MAGIC_SHORTBOW;
            case ItemID.ZARYTE_BOW:
                return RangedWeapon.ZARYTE_BOW;
            case ItemID.DARKBOW:
                return RangedWeapon.DARK_BOW;
            case ItemID.HEAVY_BALLISTA:
            case ItemID.LIGHT_BALLISTA:
                return RangedWeapon.BALLISTA;
            default:
                return null;
        }
    }

    private Ammunition getAmmunition(int itemId) {
        // Map common ammunition
        switch (itemId) {
            case ItemID.DRAGON_ARROW:
                return Ammunition.DRAGON_ARROW;
            case ItemID.RUNE_ARROW:
                return Ammunition.RUNE_ARROW;
            case ItemID.ADAMANT_ARROW:
                return Ammunition.ADAMANT_ARROW;
            case ItemID.MITHRIL_ARROW:
                return Ammunition.MITHRIL_ARROW;
            case ItemID.STEEL_ARROW:
                return Ammunition.STEEL_ARROW;
            case ItemID.IRON_ARROW:
                return Ammunition.IRON_ARROW;
            case ItemID.BRONZE_ARROW:
                return Ammunition.BRONZE_ARROW;
            case ItemID.DRAGON_BOLTS:
                return Ammunition.DRAGON_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE:
                return Ammunition.RUNITE_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE:
                return Ammunition.ADAMANT_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL:
                return Ammunition.MITHRIL_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_STEEL:
                return Ammunition.STEEL_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_IRON:
                return Ammunition.IRON_BOLT;
            case ItemID.BOLT:
                return Ammunition.BRONZE_BOLT;
            case ItemID.DRAGON_BOLTS_ENCHANTED_DRAGONSTONE:
                return Ammunition.ENCHANTED_DRAGON_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED:
                return Ammunition.ENCHANTED_RUBY_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED:
                return Ammunition.ENCHANTED_DIAMOND_BOLT;
            case ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED:
                return Ammunition.ENCHANTED_ONYX_BOLT;
            default:
                return null;
        }
    }

    @Override
    public void attack(Mobile target) {
        // Can't initiate attacks in RuneLite (read-only)
        log.debug("[COMBAT] Cannot initiate attack in RuneLite (read-only)");
    }

    @Override
    public void process() {
        // Update combat state from client
        updateCombatState();

        // Process hit queue (though we can't apply damage in RuneLite)
        hitQueue.process(owner);

        // Reset attacker if we haven't been attacked in 6 seconds
        if (lastAttack.elapsed(6000)) {
            setUnderAttack(null);
        }
    }

    @Override
    public Mobile getTarget() {
        // Return the actual opponent created in onInteractingChanged (line 480-482)
        // This is either OpponentElvargPlayer (when attacking) or null (when not)
        return opponentElvargPlayer;
    }

    @Override
    public void setTarget(Mobile target) {
        // We don't use Mobile wrappers anymore - just clear the target
        this.target = null;
    }

    @Override
    public Mobile getAttacker() {
        return attacker;
    }

    @Override
    public void setUnderAttack(Mobile attacker) {
        this.attacker = attacker;
        if (attacker != null) {
            lastAttack.reset();
        }
    }

    public boolean isAttacking() {
        return target != null;
    }

    public boolean isBeingAttacked() {
        return attacker != null && !lastAttack.elapsed(6000);
    }

    @Override
    public HitQueue getHitQueue() {
        return hitQueue;
    }

    @Override
    public Stopwatch getLastAttack() {
        return lastAttack;
    }

    @Override
    public RangedWeapon getRangedWeapon() {
        return rangedWeapon;
    }

    public void setRangedWeapon(RangedWeapon rangedWeapon) {
        this.rangedWeapon = rangedWeapon;
    }

    public Ammunition getRangedAmmo() {
        return rangeAmmoData;
    }

    public void setRangedAmmo(Ammunition rangeAmmoData) {
        this.rangeAmmoData = rangeAmmoData;
    }

    @Override
    public CombatSpell getCastSpell() {
        return castSpell;
    }

    public void setCastSpell(CombatSpell castSpell) {
        this.castSpell = castSpell;
    }

    public CombatSpell getAutoCastSpell() {
        return autoCastSpell;
    }

    public void setAutoCastSpell(CombatSpell autoCastSpell) {
        this.autoCastSpell = autoCastSpell;
    }

    @Override
    public CombatSpell getPreviousCast() {
        return previousCast;
    }

    public void setPreviousCast(CombatSpell previousCast) {
        this.previousCast = previousCast;
    }

    public SecondsTimer getPoisonImmunityTimer() {
        return poisonImmunityTimer;
    }

    public SecondsTimer getFireImmunityTimer() {
        return fireImmunityTimer;
    }

    public SecondsTimer getTeleblockTimer() {
        return teleblockTimer;
    }

    public SecondsTimer getPrayerBlockTimer() {
        return prayerBlockTimer;
    }

    public CombatMethod getMethod() {
        // Return the cached combat method that's updated each tick
        return currentMethod;
    }

    public void setMethod(CombatMethod method) {
        log.debug("[COMBAT] Cannot set combat method in RuneLite (read-only)");
    }

    /**
     * Determines the combat method based on current equipment and state.
     * Follows Elvarg's logic from CombatFactory.getMethod() exactly.
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/content/combat/CombatFactory.java:107-141
     */
    private void updateCombatMethod() {
        // Check magic - matches Elvarg logic at CombatFactory.java:117-120
        if (castSpell != null || (autoCastSpell != null && hasStaffEquipped())) {
            currentMethod = CombatFactoryAdapter.MAGIC_COMBAT;
            log.debug("[COMBAT] Method set to MAGIC (cast={}, autocast={})", castSpell != null, autoCastSpell != null);
            return;
        }

        // Check special attacks - matches CombatFactory.java:123-128
        // Note: CombatSpecial tracking not yet implemented in PlayerAdapter
        // For now, skip special attack method detection

        // Check ranged - matches CombatFactory.java:131-133
        if (rangedWeapon != null) {
            currentMethod = CombatFactoryAdapter.RANGED_COMBAT;
            log.debug("[COMBAT] Method set to RANGED (weapon={})", rangedWeapon);
            return;
        }

        // Default to melee - matches CombatFactory.java:140
        currentMethod = CombatFactoryAdapter.MELEE_COMBAT;
        log.debug("[COMBAT] Method set to MELEE (default)");
    }

    /**
     * Checks if the player has a staff equipped (for autocast validation).
     * Based on Elvarg's Equipment.hasStaffEquipped() logic.
     * Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/model/container/impl/Equipment.java:155-159
     */
    private boolean hasStaffEquipped() {
        net.runelite.api.ItemContainer equipment = client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT);
        if (equipment == null) {
            return false;
        }

        net.runelite.api.Item weapon = equipment.getItem(3); // Weapon slot
        if (weapon == null) {
            return false;
        }

        int weaponId = weapon.getId();
        // Check for staff weapon IDs - common magic staves
        // Citation: Item IDs from C:/dev/naton/osrs-pvp-reinforcement-learning/simulation-rsps/ElvargServer/src/main/java/com/elvarg/util/ItemIdentifiers.java
        return weaponId == ItemID.STAFF_OF_AIR ||
               weaponId == ItemID.STAFF_OF_WATER ||
               weaponId == ItemID.STAFF_OF_EARTH ||
               weaponId == ItemID.STAFF_OF_FIRE ||
               weaponId == ItemID.MYSTIC_AIR_STAFF ||
               weaponId == ItemID.MYSTIC_WATER_STAFF ||
               weaponId == ItemID.MYSTIC_EARTH_STAFF ||
               weaponId == ItemID.MYSTIC_FIRE_STAFF ||
               weaponId == ItemID.STAFF_OF_ZAROS ||
               weaponId == ItemID.KODAI_WAND ||
               weaponId == ItemID.TOXIC_SOTD_CHARGED ||
               weaponId == ItemID.SOTD ||
               weaponId == ItemID.STAFF_OF_LIGHT ||
               weaponId == ItemID.NIGHTMARE_STAFF ||
               weaponId == ItemID.NIGHTMARE_STAFF_VOLATILE ||
               weaponId == ItemID.NIGHTMARE_STAFF_HARMONISED ||
               weaponId == ItemID.NIGHTMARE_STAFF_ELDRITCH;
    }

    /**
     * Check if player is in combat based on animation and interaction.
     */
    public boolean isInCombat() {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return false;
        }

        // Check if we have a target
        if (localPlayer.getInteracting() != null) {
            return true;
        }

        // Check if we're being attacked (recent damage)
        return !lastAttack.elapsed(6000);
    }

    /**
     * Get combat level of local player.
     */
    public int getCombatLevel() {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null) {
            return localPlayer.getCombatLevel();
        }
        return 0;
    }

    /**
     * Handle target changes from game events.
     * Dynamically creates OpponentElvargPlayer wrappers for PvP targets.
     */
    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        // Only track local player's target changes
        if (event.getSource() != client.getLocalPlayer()) {
            return;
        }

        Actor newTarget = event.getTarget();
        log.info("[COMBAT] onInteractingChanged fired - newTarget: {}", newTarget != null ? newTarget.getName() : "null");

        if (newTarget == null) {
            // No longer targeting anything
            disposeOpponentTarget();
            target = null;
            log.info("[COMBAT] Target cleared - calling environmentBridge.updateTarget()");
        } else if (newTarget instanceof net.runelite.api.Player) {
            // Create OpponentElvargPlayer wrapper for real player
            disposeOpponentTarget();
            opponentElvargPlayer = new OpponentElvargPlayer(
                eventBus, itemManager, (net.runelite.api.Player) newTarget,
                targetGearBaseline, dynamicTargetSupplier);
            target = opponentElvargPlayer;
            log.info("[COMBAT] New PvP target: {} (wrapped in OpponentElvargPlayer) - calling environmentBridge.updateTarget()", newTarget.getName());
        } else if (newTarget instanceof NPC) {
            // NPCs not supported yet in this adapter
            target = null;
            log.info("[COMBAT] NPC target not supported: {} - calling environmentBridge.updateTarget()", newTarget.getName());
        } else {
            // Unknown target type
            disposeOpponentTarget();
            target = null;
            log.info("[COMBAT] Unknown target type: {} - calling environmentBridge.updateTarget()", newTarget.getClass().getSimpleName());
        }

        // Critical fix: Call environmentBridge.updateTarget() to trigger DynamicTargetPlayer.updateDelegate()
        // This switches the delegation from dummy to real opponent data
        if (environmentBridge != null) {
            environmentBridge.updateTarget();
            log.info("[COMBAT] Called environmentBridge.updateTarget() from onInteractingChanged");
        } else {
            log.warn("[COMBAT] environmentBridge is null in onInteractingChanged!");
        }
    }

    /**
     * Clean up when adapter is no longer needed.
     */
    public void shutdown() {
        if (eventBus != null) {
            eventBus.unregister(this);
        }
        disposeOpponentTarget();
    }

    private void disposeOpponentTarget() {
        if (opponentElvargPlayer != null) {
            opponentElvargPlayer.shutdown();
            opponentElvargPlayer = null;
        }
        target = null;
    }
}




