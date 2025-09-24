package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.content.combat.hit.PendingHit;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.container.impl.Equipment;
import com.elvarg.game.model.areas.Area;
import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.autopvp.util.TargetEquipmentTranslator;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * Dynamic Elvarg Player implementation that can switch between dummy and real opponent.
 * This allows NhEnvironment to maintain its immutable target reference while
 * enabling the adapter to delegate to the actual opponent when available.
 *
 * Enhanced to track actual RuneLite target player for real HP, equipment, and prayer data.
 */
@Slf4j
public class DynamicTargetPlayer extends Player {

    private static final int RING_SLOT = 12;
    private static final int MAX_SPEC_ENERGY = 100;

    private final Client client;
    private final CombatAdapter combatAdapter;
    private final ItemManager itemManager;
    private volatile Player currentDelegate;
    private final DummyElvargPlayer dummyPlayer;
    private volatile net.runelite.api.Player runeliteTarget;

    // Equipment tracking using RuneLite PlayerComposition
    private volatile double[] targetGearBonuses = new double[14]; // 14 equipment bonuses
    private volatile int[] targetEquipmentItemIds = new int[14]; // Equipment slots
    private volatile double targetEquipmentConfidence = 0.0;

    // Special attack tracking
    private volatile int estimatedSpecBudget = MAX_SPEC_ENERGY;

    // Vengeance tracking
    private volatile boolean hasVengeance = false;

    // Spellbook detection for magic accuracy calculations
    private volatile String currentSpellbook = "Standard";

    public DynamicTargetPlayer(Client client, ItemManager itemManager, CombatAdapter combatAdapter) {
        super(null);
        this.client = client;
        this.itemManager = itemManager;
        this.combatAdapter = combatAdapter;
        this.dummyPlayer = new DummyElvargPlayer();
        this.currentDelegate = dummyPlayer;
    }

    /**
     * Updates the delegate based on current combat state.
     * Called from NhEnvironmentBridge.onTickStart() each game tick.
     */
    public void updateDelegate() {
        // Update Elvarg delegate from CombatAdapter's target
        Mobile target = combatAdapter.getTarget();
        if (target instanceof OpponentElvargPlayer) {
            // We have a real opponent wrapped in OpponentElvargPlayer
            this.currentDelegate = (Player) target;
            // CRITICAL: Set runeliteTarget for real data tracking
            net.runelite.api.Player rlPlayer = ((OpponentElvargPlayer) target).getRunelitePlayer();
            this.setRuneliteTarget(rlPlayer); // This also updates equipment
            log.info("[DYNAMIC_TARGET] Switched to real opponent: {} (HP: {})",
                     rlPlayer.getName(), target.getHitpoints());
        } else if (target instanceof Player) {
            // Some other Player type
            this.currentDelegate = (Player) target;
            this.setRuneliteTarget(null); // Clear when not OpponentElvargPlayer
            log.info("[DYNAMIC_TARGET] Switched to player target: {}", target);
        } else {
            // No target or non-player target
            this.currentDelegate = dummyPlayer;
            this.setRuneliteTarget(null); // Clear when no target
            log.info("[DYNAMIC_TARGET] No real opponent, using dummy");
        }
    }


    // Remove methods that are no longer needed since we delegate to currentDelegate

    /**
     * Override getSpecialPercentage to return our tracked estimate.
     * This is what NhEnvironment.getObs() calls for observation 21.
     */
    @Override
    public int getSpecialPercentage() {
        // Return our tracked special attack budget
        // Citation: NhEnvironment.java:880 calls getTarget().getSpecialPercentage()
        return estimatedSpecBudget;
    }

    /**
     * Override hasVengeance to return our tracked state.
     * This is what NhEnvironment.getObs() calls for observation 3 (targetIsVengActive).
     * Citation: NhEnvironment.java:2141-2143 isTargetVengActive() calls getTarget().hasVengeance()
     */
    @Override
    public boolean hasVengeance() {
        // Return our tracked vengeance state
        // Citation: NhEnvironment.java:2142 calls getTarget().hasVengeance()
        return hasVengeance;
    }

    /**
     * Override setHasVengeance to update our tracked state.
     */
    @Override
    public void setHasVengeance(boolean hasVengeance) {
        this.hasVengeance = hasVengeance;
    }

// Delegate all Player methods to currentDelegate

    @Override
    public void onAdd() {
        currentDelegate.onAdd();
    }

    @Override
    public void onRemove() {
        currentDelegate.onRemove();
    }

    @Override
    public PendingHit manipulateHit(PendingHit hit) {
        return currentDelegate.manipulateHit(hit);
    }

    @Override
    public void appendDeath() {
        currentDelegate.appendDeath();
    }

    @Override
    public void heal(int damage) {
        currentDelegate.heal(damage);
    }

    @Override
    public int getHitpoints() {
        // Use real HP from RuneLite target if available
        if (runeliteTarget != null) {
            int healthRatio = runeliteTarget.getHealthRatio();
            int healthScale = runeliteTarget.getHealthScale();

            // Handle cases where health info is not available
            if (healthScale <= 0) {
                log.debug("[DYNAMIC_TARGET] Health scale not available for target");
                return 100; // Default to full health if unknown
            }

            // Calculate HP percentage (0-100 scale to match Elvarg expectations)
            // Elvarg uses absolute HP values, but for NhEnvironment observations,
            // it gets normalized to percentage anyway
            int hpPercentage = (100 * healthRatio) / healthScale;

            // Convert percentage to absolute HP (assuming max HP of 99)
            // This matches how NhEnvironment normalizes it later
            int estimatedHp = (99 * hpPercentage) / 100;

            log.debug("[DYNAMIC_TARGET] Target HP: {}% (ratio: {}/{}, estimated: {})",
                     hpPercentage, healthRatio, healthScale, estimatedHp);

            return estimatedHp;
        }

        // Fall back to delegate if no RuneLite target
        return currentDelegate.getHitpoints();
    }

    @Override
    public Player setHitpoints(int hitpoints) {
        currentDelegate.setHitpoints(hitpoints);
        return this;
    }

    @Override
    public Location getLocation() {
        return currentDelegate.getLocation();
    }

    @Override
    public Equipment getEquipment() {
        // Return virtual Equipment with our tracked item IDs
        // Citation: NhEnvironment.java:629-711 calls target.getEquipment().getItemIdsArray()
        Equipment virtualEquipment = new Equipment(null);

        // Set equipment items from our tracked array
        for (int i = 0; i < targetEquipmentItemIds.length && i < 14; i++) {
            if (targetEquipmentItemIds[i] > 0) {
                virtualEquipment.set(i, new com.elvarg.game.model.Item(targetEquipmentItemIds[i], 1));
            }
        }

        return virtualEquipment;
    }

    /**
     * Update RuneLite target reference.
     * Called from NhEnvironmentBridge when target changes.
     */
    public void setRuneliteTarget(net.runelite.api.Player player) {
        this.runeliteTarget = player;
        if (player != null) {
            updateEquipmentFromComposition(player.getPlayerComposition());
        }
    }

    /**
     * Get current RuneLite target for external access.
     */
    public net.runelite.api.Player getRuneliteTarget() {
        return runeliteTarget;
    }

    /**
     * Updates equipment tracking from RuneLite PlayerComposition.
     * Citation: PlayerComposition.getEquipmentIds() provides visible equipment
     */
    private void updateEquipmentFromComposition(PlayerComposition composition) {
        if (composition == null) {
            Arrays.fill(targetEquipmentItemIds, 0);
            targetEquipmentConfidence = 0.0;
            return;
        }

        int[] equipmentIds = composition.getEquipmentIds();
        if (equipmentIds == null) {
            Arrays.fill(targetEquipmentItemIds, 0);
            targetEquipmentConfidence = 0.0;
            return;
        }

        // Map RuneLite equipment slots to Elvarg slots using TargetEquipmentTranslator
        TargetEquipmentTranslator.Result translation = TargetEquipmentTranslator.translate(composition, itemManager, 0);
        targetEquipmentItemIds = translation.getItemIds();
        targetEquipmentConfidence = translation.getAverageSlotConfidence();

        log.debug("[DYNAMIC_TARGET] Updated equipment: {} items, confidence: {}",
                 Arrays.stream(targetEquipmentItemIds).map(id -> id > 0 ? 1 : 0).sum(),
                 targetEquipmentConfidence);
    }

    /**
     * Called when target performs an animation that might affect special attack.
     * Citation: Various weapon special attacks consume energy
     */
    public void onTargetAnimation(int animationId) {
        // Track special attack usage based on animation
        if (isSpecialAttackAnimation(animationId)) {
            int specCost = getSpecialAttackCost(animationId);
            estimatedSpecBudget = Math.max(0, estimatedSpecBudget - specCost);
            log.debug("[DYNAMIC_TARGET] Special attack used (anim: {}), estimated spec: {}%",
                     animationId, estimatedSpecBudget);
        }
    }

    /**
     * Called when target displays a graphic that might indicate vengeance.
     * Citation: Vengeance graphic ID from OSRS
     */
    public void onTargetGraphic(int graphicId) {
        if (graphicId == 726) { // Vengeance graphic
            hasVengeance = true;
            log.debug("[DYNAMIC_TARGET] Vengeance detected on target");
        }
    }

    private boolean isSpecialAttackAnimation(int animationId) {
        // Common special attack animations
        switch (animationId) {
            case 1658: // Dragon dagger spec
            case 1062: // Dragon longsword spec
            case 1378: // Dragon scimitar spec
            case 7514: // Dragon claws spec
            case 1667: // Abyssal whip spec
            case 5061: // Barrows weapons spec
                return true;
            default:
                return false;
        }
    }

    private int getSpecialAttackCost(int animationId) {
        // Return special attack cost based on animation
        switch (animationId) {
            case 1658: // Dragon dagger
                return 25;
            case 1062: // Dragon longsword
                return 25;
            case 1378: // Dragon scimitar
                return 55;
            case 7514: // Dragon claws
                return 50;
            case 1667: // Abyssal whip
                return 50;
            case 5061: // Barrows weapons
                return 100;
            default:
                return 50; // Default cost
        }
    }

    /**
     * Gets equipment confidence for external validation.
     */
    public double getEquipmentConfidence() {
        return targetEquipmentConfidence;
    }

    /**
     * Gets current spellbook for magic calculations.
     */
    public String getCurrentSpellbook() {
        return currentSpellbook;
    }

    /**
     * Updates special attack regeneration.
     * Citation: Special attack regenerates 10% every 50 ticks (30 seconds)
     */
    public void updateSpecialAttackRegen() {
        if (estimatedSpecBudget < MAX_SPEC_ENERGY) {
            estimatedSpecBudget = Math.min(MAX_SPEC_ENERGY, estimatedSpecBudget + 10);
            log.debug("[DYNAMIC_TARGET] Special attack regenerated to {}%", estimatedSpecBudget);
        }
    }

    /**
     * Resets special attack budget (when target switches or combat ends).
     */
    public void resetSpecialAttack() {
        estimatedSpecBudget = MAX_SPEC_ENERGY;
        log.debug("[DYNAMIC_TARGET] Special attack reset to 100%");
    }

    /**
     * Gets equipment item IDs for external access.
     */
    public int[] getTargetEquipmentItemIds() {
        return targetEquipmentItemIds.clone();
    }

    /**
     * Gets gear bonuses for external access.
     */
    public double[] getTargetGearBonuses() {
        return targetGearBonuses.clone();
    }

    /**
     * Gets target equipment confidence for external access.
     */
    public double getTargetEquipmentConfidence() {
        return targetEquipmentConfidence;
    }

    /**
     * Gets estimated special attack percentage for external access.
     */
    public int getEstimatedSpecialPercent() {
        return estimatedSpecBudget;
    }

    /**
     * Called when opponent equipment is updated.
     */
    public void onOpponentEquipmentUpdate(Object result, double[] bonuses) {
        // Update our tracking from equipment translation
        if (bonuses != null && bonuses.length >= targetGearBonuses.length) {
            System.arraycopy(bonuses, 0, targetGearBonuses, 0, targetGearBonuses.length);
        }
        log.debug("[DYNAMIC_TARGET] Equipment bonuses updated");
    }

    /**
     * Called when target takes damage.
     */
    public void onTargetDamaged(int damage) {
        log.debug("[DYNAMIC_TARGET] Target took {} damage", damage);
        // Could update HP estimation here if needed
    }

    /**
     * Gets lightbearer observation status.
     */
    public boolean hasLightbearerObserved() {
        // Check if target has lightbearer ring equipped
        if (targetEquipmentItemIds.length > RING_SLOT) {
            int ringId = targetEquipmentItemIds[RING_SLOT];
            return ringId == ItemID.LIGHTBEARER || ringId == ItemID.BR_LIGHTBEARER;
        }
        return false;
    }

    /**
     * Gets detected spellbook for external access.
     */
    public String getDetectedSpellbook() {
        return currentSpellbook;
    }

    /**
     * Get combat level from RuneLite target.
     * Citation: Player.getCombatLevel() provides visible combat level
     */
    public int getCombatLevel() {
        if (runeliteTarget != null) {
            return runeliteTarget.getCombatLevel();
        }
        return 3; // Default minimum combat level
    }

    /**
     * Get player name from RuneLite target.
     * Citation: Player.getName() provides display name
     */
    public String getName() {
        if (runeliteTarget != null) {
            return runeliteTarget.getName();
        }
        return "Unknown";
    }

    @Override
    public int getIndex() {
        return currentDelegate.getIndex();
    }

    @Override
    public boolean isRegistered() {
        return currentDelegate.isRegistered();
    }

    @Override
    public Area getArea() {
        return currentDelegate.getArea();
    }

    /**
     * Get entity size for collision detection.
     * Citation: Mobile.getSize() determines collision bounds
     */
    public int getSize() {
        return 1; // Players are 1x1 tiles
    }

    @Override
    public boolean isNeedsPlacement() {
        return currentDelegate.isNeedsPlacement();
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isNpc() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DynamicTargetPlayer) {
            return this == other;
        }
        return currentDelegate.equals(other);
    }

    @Override
    public int hashCode() {
        return currentDelegate.hashCode();
    }

    @Override
    public String toString() {
        return currentDelegate.toString();
    }

    // Methods that might be missing from our Elvarg JAR version

    public void setSwapPid(boolean swapPid) {
        if (currentDelegate instanceof DummyElvargPlayer) {
            ((DummyElvargPlayer) currentDelegate).setSwapPid(swapPid);
        } else {
            try {
                // Use reflection if method exists on real Player
                currentDelegate.getClass().getMethod("setSwapPid", boolean.class)
                    .invoke(currentDelegate, swapPid);
            } catch (Exception e) {
                // Method doesn't exist, ignore
            }
        }
    }

    public boolean isSwapPid() {
        if (currentDelegate instanceof DummyElvargPlayer) {
            return ((DummyElvargPlayer) currentDelegate).isSwapPid();
        } else {
            try {
                // Use reflection if method exists on real Player
                return (boolean) currentDelegate.getClass().getMethod("isSwapPid")
                    .invoke(currentDelegate);
            } catch (Exception e) {
                return false;
            }
        }
    }

    public Mobile setPositionToFace(Location position) {
        if (currentDelegate instanceof DummyElvargPlayer) {
            return ((DummyElvargPlayer) currentDelegate).setPositionToFace(position);
        } else {
            try {
                // Use reflection if method exists on real Player
                return (Mobile) currentDelegate.getClass()
                    .getMethod("setPositionToFace", Location.class)
                    .invoke(currentDelegate, position);
            } catch (Exception e) {
                return this;
            }
        }
    }

    public int getLastDamage() {
        if (currentDelegate instanceof DummyElvargPlayer) {
            return ((DummyElvargPlayer) currentDelegate).getLastDamage();
        } else {
            try {
                // Use reflection if method exists on real Player
                return (int) currentDelegate.getClass().getMethod("getLastDamage")
                    .invoke(currentDelegate);
            } catch (Exception e) {
                return 0;
            }
        }
    }

    public void setLastDamage(int damage) {
        if (currentDelegate instanceof DummyElvargPlayer) {
            ((DummyElvargPlayer) currentDelegate).setLastDamage(damage);
        } else {
            try {
                // Use reflection if method exists on real Player
                currentDelegate.getClass().getMethod("setLastDamage", int.class)
                    .invoke(currentDelegate, damage);
            } catch (Exception e) {
                // Method doesn't exist, ignore
            }
        }
    }

    /**
     * Get the TimerRepository from the current delegate.
     * Citation: NhEnvironment.java:224 calls target.getTimers().getTicks(TimerKey.FREEZE)
     */
    public com.elvarg.util.timers.TimerRepository getTimers() {
        return currentDelegate.getTimers();
    }

    /**
     * Get the SkillManager from the current delegate.
     * Citation: GameDataUtil.java:30 calls player.getSkillManager().getMaxLevel(Skill.HITPOINTS)
     */
    public com.elvarg.game.content.skill.SkillManager getSkillManager() {
        return currentDelegate.getSkillManager();
    }

    /**
     * Get the prayer active array from the current delegate.
     * Citation: GameDataUtil.java:18 calls player.getPrayerActive()[PrayerHandler.getProtectingPrayer(c)]
     */
    public boolean[] getPrayerActive() {
        return currentDelegate.getPrayerActive();
    }
}












































