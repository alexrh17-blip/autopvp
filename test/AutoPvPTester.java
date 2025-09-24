package net.runelite.client.plugins.autopvp.test;

import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.autopvp.adapters.CombatAdapter;
import net.runelite.client.plugins.autopvp.adapters.DynamicTargetPlayer;
import net.runelite.client.plugins.autopvp.adapters.PlayerAdapter;
import net.runelite.client.plugins.autopvp.core.NhEnvironmentBridge;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.container.impl.Equipment;
import com.elvarg.game.model.container.impl.Inventory;

/**
 * Test class to verify AutoPvP wiring is correct.
 * Provides methods to test each adapter and the environment bridge.
 */
@Slf4j
public final class AutoPvPTester
{
    private AutoPvPTester()
    {
    }

    /**
     * Run comprehensive tests on all adapters and environment bridge.
     */
    public static String runAllTests(PlayerAdapter playerAdapter, NhEnvironmentBridge bridge)
    {
        StringBuilder results = new StringBuilder();
        results.append("=== AutoPvP Wiring Tests ===\n");

        // Test 1: PlayerAdapter basic connectivity
        results.append("\n[TEST 1] PlayerAdapter Connectivity:\n");
        try {
            results.append("  HP: ").append(playerAdapter.getHitpoints()).append("/99\n");
            results.append("  Location: ").append(playerAdapter.getLocation()).append("\n");
            results.append("  Special: ").append(playerAdapter.getSpecialPercentage()).append("%\n");
            results.append("  Vengeance: ").append(playerAdapter.hasVengeance()).append("\n");
            results.append("  OK. PlayerAdapter connected\n");
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 2: Equipment adapter
        results.append("\n[TEST 2] Equipment Adapter:\n");
        try {
            Equipment equipment = playerAdapter.getEquipment();
            if (equipment != null) {
                Item weapon = equipment.get(Equipment.WEAPON_SLOT);
                Item shield = equipment.get(Equipment.SHIELD_SLOT);
                results.append("  Weapon: ").append(weapon != null ? weapon.getId() : "none").append("\n");
                results.append("  Shield: ").append(shield != null ? shield.getId() : "none").append("\n");
                results.append("  OK. Equipment adapter working\n");
            } else {
                results.append("  WARN: Equipment is null\n");
            }
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 3: Inventory adapter
        results.append("\n[TEST 3] Inventory Adapter:\n");
        try {
            Inventory inventory = playerAdapter.getInventory();
            if (inventory != null) {
                int freeSlots = inventory.getFreeSlots();
                int capacity = inventory.capacity();
                results.append("  Free slots: ").append(freeSlots).append("/").append(capacity).append("\n");
                results.append("  OK. Inventory adapter working\n");
            } else {
                results.append("  WARN: Inventory is null\n");
            }
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 4: Prayer states
        results.append("\n[TEST 4] Prayer States:\n");
        try {
            boolean[] prayers = playerAdapter.getPrayerActive();
            int activePrayers = 0;
            for (boolean active : prayers) {
                if (active) {
                    activePrayers++;
                }
            }
            results.append("  Active prayers: ").append(activePrayers).append("/").append(prayers.length).append("\n");
            results.append("  OK. Prayer tracking working\n");
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 5: Environment bridge observations
        results.append("\n[TEST 5] Environment Bridge Observations:\n");
        try {
            List<Number> observations = bridge.getObservations();
            if (observations != null) {
                results.append("  Observation count: ").append(observations.size()).append(" (expected 176)\n");
                long nonZero = observations.stream().filter(n -> n.doubleValue() != 0.0).count();
                results.append("  Non-zero values: ").append(nonZero).append("\n");
                results.append("  First 5 obs: ");
                for (int i = 0; i < Math.min(5, observations.size()); i++) {
                    if (i > 0) {
                        results.append(", ");
                    }
                    results.append(String.format("%.2f", observations.get(i).doubleValue()));
                }
                results.append("\n");
                if (observations.size() == 176) {
                    results.append("  OK. Observation vector size is correct\n");
                } else {
                    results.append("  WARN: Unexpected observation count\n");
                }
            } else {
                results.append("  WARN: Observations are null\n");
            }
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 6: Target gear translation
        results.append("\n[TEST 6] Target Gear Translation:\n");
        try {
            DynamicTargetPlayer dynamicTarget = bridge.getDynamicTargetPlayer();
            if (dynamicTarget != null) {
                results.append(String.format("  Confidence: %.2f\n", dynamicTarget.getTargetEquipmentConfidence()));
                results.append("  Item IDs: ").append(Arrays.toString(dynamicTarget.getTargetEquipmentItemIds())).append("\n");
                results.append("  Bonuses: ").append(Arrays.toString(dynamicTarget.getTargetGearBonuses())).append("\n");
                results.append(String.format("  Lightbearer observed: %s\n", dynamicTarget.hasLightbearerObserved()));
                results.append("  OK. Gear translation recorded\n");
            } else {
                results.append("  WARN: DynamicTargetPlayer unavailable\n");
            }
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 7: Environment bridge action masks
        results.append("\n[TEST 7] Environment Bridge Action Masks:\n");
        try {
            List<List<Boolean>> actionMasks = bridge.getActionMasks();
            if (actionMasks != null) {
                results.append("  Action heads: ").append(actionMasks.size()).append(" (expected 12)\n");
                for (int i = 0; i < actionMasks.size(); i++) {
                    long valid = actionMasks.get(i).stream().filter(Boolean::booleanValue).count();
                    results.append(String.format("    Head %d: %d/%d valid\n", i, valid, actionMasks.get(i).size()));
                }
                if (actionMasks.size() == 12) {
                    results.append("  OK. Action masks present\n");
                } else {
                    results.append("  WARN: Unexpected action head count\n");
                }
            } else {
                results.append("  WARN: Action masks are null\n");
            }
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 8: Gear loadouts from bridge
        results.append("\n[TEST 8] Gear Loadouts from Bridge:\n");
        try {
            int[] meleeGear = bridge.getMeleeGear();
            int[] rangedGear = bridge.getRangedGear();
            int[] mageGear = bridge.getMageGear();
            results.append("  Melee weapon: ").append(meleeGear != null && meleeGear.length > 0 ? meleeGear[0] : -1).append("\n");
            results.append("  Ranged weapon: ").append(rangedGear != null && rangedGear.length > 0 ? rangedGear[0] : -1).append("\n");
            results.append("  Mage weapon: ").append(mageGear != null && mageGear.length > 0 ? mageGear[0] : -1).append("\n");
            results.append("  OK. Loadouts accessible\n");
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 9: Combat adapter
        results.append("\n[TEST 9] Combat Adapter:\n");
        try {
            var combat = playerAdapter.getCombat();
            if (combat != null) {
                results.append("  Has target: ").append(combat.getTarget() != null).append("\n");
                if (combat instanceof CombatAdapter) {
                    CombatAdapter adapter = (CombatAdapter) combat;
                    results.append("  Is attacking: ").append(adapter.isAttacking()).append("\n");
                    results.append("  Being attacked: ").append(adapter.isBeingAttacked()).append("\n");
                }
                results.append("  OK. Combat adapter active\n");
            } else {
                results.append("  WARN: Combat adapter is null\n");
            }
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        // Test 10: Delegating player connectivity
        results.append("\n[TEST 10] DelegatingElvargPlayer Bridge:\n");
        try {
            bridge.onTickStart();
            bridge.onTickProcessed();
            bridge.onTickEnd();
            results.append("  OK. Tick methods executed without error\n");
        } catch (Exception e) {
            results.append("  ERR: ").append(e.getMessage()).append("\n");
        }

        results.append("\n=== Test Complete ===\n");
        return results.toString();
    }

    /**
     * Test observation values for sanity and completeness.
     * Validates all 176 observations according to NhContract specification.
     */
    public static String testObservationSanity(List<Number> observations)
    {
        StringBuilder results = new StringBuilder();
        results.append("=== Comprehensive Observation Validation (176 values) ===\n");

        if (observations == null || observations.isEmpty()) {
            results.append("❌ FAIL: No observations available\n");
            return results.toString();
        }

        if (observations.size() != 176) {
            results.append(String.format("❌ FAIL: Expected 176 observations, got %d\n", observations.size()));
            return results.toString();
        }

        // Track validation results (use array to pass by reference)
        int[] validCount = {0};
        int[] warningCount = {0};
        int[] errorCount = {0};

        results.append("\n=== Combat Style & Equipment (0-4) ===\n");
        validateObservation(results, observations, 0, "isMeleeEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 1, "isRangedEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 2, "isMageEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 3, "isMeleeSpecialWeaponEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 4, "specialPercentage", 0, 100, validCount, warningCount, errorCount);

        results.append("\n=== Player Prayers (5-9) ===\n");
        validateObservation(results, observations, 5, "isProtectMeleeActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 6, "isProtectRangedActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 7, "isProtectMagicActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 8, "isSmiteActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 9, "isRedemptionActive", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Health (10-11) ===\n");
        validateObservation(results, observations, 10, "healthPercent", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 11, "targetHealthPercent", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Target Equipment (12-15) ===\n");
        validateObservation(results, observations, 12, "isTargetMeleeEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 13, "isTargetRangedEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 14, "isTargetMageEquipped", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 15, "isTargetMeleeSpecialWeaponEquipped", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Target Prayers (16-20) ===\n");
        validateObservation(results, observations, 16, "isTargetProtectMeleeActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 17, "isTargetProtectRangedActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 18, "isTargetProtectMagicActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 19, "isTargetSmiteActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 20, "isTargetRedemptionActive", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Resources (21-27) ===\n");
        validateObservation(results, observations, 21, "targetSpecialPercentage", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 22, "remainingRangingPotionDoses", 0, 28, validCount, warningCount, errorCount);
        validateObservation(results, observations, 23, "remainingSuperCombatDoses", 0, 28, validCount, warningCount, errorCount);
        validateObservation(results, observations, 24, "remainingSuperRestoreDoses", 0, 28, validCount, warningCount, errorCount);
        validateObservation(results, observations, 25, "remainingSaradominBrewDoses", 0, 28, validCount, warningCount, errorCount);
        validateObservation(results, observations, 26, "foodCount", 0, 28, validCount, warningCount, errorCount);
        validateObservation(results, observations, 27, "karamCount", 0, 28, validCount, warningCount, errorCount);

        results.append("\n=== Prayer and Status (28-33) ===\n");
        validateObservation(results, observations, 28, "prayerPointScale", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 29, "playerFrozenTicks", 0, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 30, "targetFrozenTicks", 0, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 31, "playerFrozenImmunityTicks", 0, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 32, "targetFrozenImmunityTicks", 0, 200, validCount, warningCount, errorCount);

        results.append("\n=== Range Check (33) ===\n");
        validateObservation(results, observations, 33, "isInMeleeRange", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Skill Levels (34-38) ===\n");
        validateObservation(results, observations, 34, "relativeLevelStrength", -99, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 35, "relativeLevelAttack", -99, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 36, "relativeLevelDefence", -99, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 37, "relativeLevelRanged", -99, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 38, "relativeLevelMagic", -99, 99, validCount, warningCount, errorCount);

        results.append("\n=== Timing (39-45) ===\n");
        validateObservation(results, observations, 39, "ticksUntilNextAttack", -10, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 40, "ticksUntilNextFood", -10, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 41, "ticksUntilNextPotionCycle", -10, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 42, "ticksUntilNextKaramCycle", -10, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 43, "foodAttackDelay", -10, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 44, "ticksUntilNextTargetAttack", -10, 200, validCount, warningCount, errorCount);
        validateObservation(results, observations, 45, "ticksUntilNextTargetPotion", -10, 200, validCount, warningCount, errorCount);

        results.append("\n=== Combat State (46-57) ===\n");
        validateObservation(results, observations, 46, "pendingDamageOnTargetScale", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 47, "ticksUntilHitOnTarget", 0, 10, validCount, warningCount, errorCount);
        validateObservation(results, observations, 48, "ticksUntilHitOnPlayer", 0, 10, validCount, warningCount, errorCount);
        validateObservation(results, observations, 49, "didPlayerJustAttack", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 50, "didTargetJustAttack", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 51, "attackCalculatedDamageScale", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 52, "hitsplatsLandedOnAgentScale", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 53, "hitsplatsLandedOnTargetScale", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 54, "isAttackingTarget", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 55, "isMoving", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 56, "isTargetMoving", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 57, "isHavePidOverTarget", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Spell Availability (58-59) ===\n");
        validateObservation(results, observations, 58, "canCastIceBarrage", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 59, "canCastBloodBarrage", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Distance Metrics (60-62) ===\n");
        validateObservation(results, observations, 60, "destinationDistanceToTarget", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 61, "distanceToDestination", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 62, "distanceToTarget", 0, 100, validCount, warningCount, errorCount);

        results.append("\n=== Prayer Tracking (63-64) ===\n");
        validateObservation(results, observations, 63, "didPlayerPrayCorrectly", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 64, "didTargetPrayCorrectly", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Damage and Confidence (65-66) ===\n");
        validateObservation(results, observations, 65, "damageDealtScale", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 66, "targetHitConfidence", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Hit Counts (67-81) ===\n");
        validateObservation(results, observations, 67, "targetHitMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 68, "targetHitMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 69, "targetHitRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 70, "playerHitMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 71, "playerHitMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 72, "playerHitRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 73, "targetHitCorrectCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 74, "targetPrayConfidence", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 75, "targetPrayMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 76, "targetPrayRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 77, "targetPrayMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 78, "playerPrayMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 79, "playerPrayRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 80, "playerPrayMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 81, "targetPrayCorrectCount", 0, 100, validCount, warningCount, errorCount);

        results.append("\n=== Recent Hit Counts (82-95) ===\n");
        validateObservation(results, observations, 82, "recentTargetHitMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 83, "recentTargetHitMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 84, "recentTargetHitRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 85, "recentPlayerHitMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 86, "recentPlayerHitMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 87, "recentPlayerHitRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 88, "recentTargetHitCorrectCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 89, "recentTargetPrayMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 90, "recentTargetPrayRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 91, "recentTargetPrayMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 92, "recentPlayerPrayMageCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 93, "recentPlayerPrayRangeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 94, "recentPlayerPrayMeleeCount", 0, 100, validCount, warningCount, errorCount);
        validateObservation(results, observations, 95, "recentTargetPrayCorrectCount", 0, 100, validCount, warningCount, errorCount);

        results.append("\n=== Absolute Levels (96-102) ===\n");
        validateObservation(results, observations, 96, "absoluteLevelAttack", 1, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 97, "absoluteLevelStrength", 1, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 98, "absoluteLevelDefence", 1, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 99, "absoluteLevelRanged", 1, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 100, "absoluteLevelMagic", 1, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 101, "absoluteLevelPrayer", 1, 99, validCount, warningCount, errorCount);
        validateObservation(results, observations, 102, "absoluteLevelHitpoints", 1, 99, validCount, warningCount, errorCount);

        results.append("\n=== Bolt Types (103-105) ===\n");
        validateObservation(results, observations, 103, "isEnchantedDragonBolts", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 104, "isEnchantedOpalBolts", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 105, "isEnchantedDiamondBolts", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Spec Weapon Types (106-120) ===\n");
        validateObservation(results, observations, 106, "isMageSpecWeaponInLoadout", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 107, "isRangeSpecWeaponInLoadout", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 108, "isNightmareStaff", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 109, "isZaryteCrossbow", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 110, "isBallista", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 111, "isMorrigansJavelins", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 112, "isDragonKnives", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 113, "isDarkBow", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 114, "isMeleeSpecDclaws", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 115, "isMeleeSpecDds", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 116, "isMeleeSpecAgs", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 117, "isMeleeSpecVls", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 118, "isMeleeSpecStatHammer", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 119, "isMeleeSpecAncientGodsword", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 120, "isMeleeSpecGraniteMaul", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Special Equipment (121-123) ===\n");
        validateObservation(results, observations, 121, "isBloodFury", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 122, "isDharoksSet", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 123, "isZurielStaff", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Gear Stats (124-141) ===\n");
        validateObservation(results, observations, 124, "magicGearAccuracy", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 125, "magicGearStrength", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 126, "rangedGearAccuracy", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 127, "rangedGearStrength", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 128, "rangedGearAttackSpeed", 0, 10, validCount, warningCount, errorCount);
        validateObservation(results, observations, 129, "rangedGearAttackRange", 0, 10, validCount, warningCount, errorCount);
        validateObservation(results, observations, 130, "meleeGearAccuracy", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 131, "meleeGearStrength", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 132, "meleeGearAttackSpeed", 0, 10, validCount, warningCount, errorCount);
        validateObservation(results, observations, 133, "magicGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 134, "magicGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 135, "magicGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 136, "rangedGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 137, "rangedGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 138, "rangedGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 139, "meleeGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 140, "meleeGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 141, "meleeGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);

        results.append("\n=== Target Gear Stats (142-159) ===\n");
        validateObservation(results, observations, 142, "targetCurrentGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 143, "targetCurrentGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 144, "targetCurrentGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 145, "targetLastMagicGearAccuracy", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 146, "targetLastMagicGearStrength", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 147, "targetLastRangedGearAccuracy", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 148, "targetLastRangedGearStrength", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 149, "targetLastMeleeGearAccuracy", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 150, "targetLastMeleeGearStrength", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 151, "targetLastMagicGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 152, "targetLastMagicGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 153, "targetLastMagicGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 154, "targetLastRangedGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 155, "targetLastRangedGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 156, "targetLastRangedGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 157, "targetLastMeleeGearRangedDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 158, "targetLastMeleeGearMageDefence", -500, 500, validCount, warningCount, errorCount);
        validateObservation(results, observations, 159, "targetLastMeleeGearMeleeDefence", -500, 500, validCount, warningCount, errorCount);

        results.append("\n=== Game Modes (160-161) ===\n");
        validateObservation(results, observations, 160, "isLms", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 161, "isPvpArena", 0, 1, validCount, warningCount, errorCount);

        results.append("\n=== Vengeance (162-167) ===\n");
        validateObservation(results, observations, 162, "isVengActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 163, "isTargetVengActive", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 164, "isPlayerLunarSpellbook", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 165, "isTargetLunarSpellbook", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 166, "playerVengCooldownTicks", 0, 50, validCount, warningCount, errorCount);
        validateObservation(results, observations, 167, "targetVengCooldownTicks", 0, 50, validCount, warningCount, errorCount);

        results.append("\n=== Attack Availability (168-175) ===\n");
        validateObservation(results, observations, 168, "isBloodAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 169, "isIceAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 170, "isMageSpecAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 171, "isRangedAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 172, "isRangedSpecAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 173, "isMeleeAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 174, "isMeleeSpecAttackAvailable", 0, 1, validCount, warningCount, errorCount);
        validateObservation(results, observations, 175, "isAnglerfish", 0, 1, validCount, warningCount, errorCount);

        // Summary
        results.append("\n=== VALIDATION SUMMARY ===\n");
        results.append(String.format("✅ Valid: %d/176 (%.1f%%)\n", validCount[0], validCount[0] * 100.0 / 176));
        results.append(String.format("⚠️  Warnings: %d\n", warningCount[0]));
        results.append(String.format("❌ Errors: %d\n", errorCount[0]));

        if (errorCount[0] == 0) {
            results.append("\n🎉 All observations are within valid ranges!\n");
        } else {
            results.append("\n⚠️  Some observations have invalid values - check errors above\n");
        }

        return results.toString();
    }

    private static void validateObservation(StringBuilder results, List<Number> observations, int index,
                                           String name, double min, double max,
                                           int[] validCount, int[] warningCount, int[] errorCount) {
        if (index >= observations.size()) {
            results.append(String.format("  [%3d] %-30s: ❌ MISSING\n", index, name));
            errorCount[0]++;
            return;
        }

        Number value = observations.get(index);
        double val = value.doubleValue();

        if (Double.isNaN(val)) {
            results.append(String.format("  [%3d] %-30s: ❌ NaN\n", index, name));
            errorCount[0]++;
        } else if (Double.isInfinite(val)) {
            results.append(String.format("  [%3d] %-30s: ❌ Infinity\n", index, name));
            errorCount[0]++;
        } else if (val < min || val > max) {
            results.append(String.format("  [%3d] %-30s: ⚠️  %.2f (expected %.0f-%.0f)\n",
                index, name, val, min, max));
            warningCount[0]++;
        } else {
            // Only show non-zero values for conciseness
            if (val != 0) {
                results.append(String.format("  [%3d] %-30s: ✅ %.2f\n", index, name, val));
            }
            validCount[0]++;
        }
    }
}
