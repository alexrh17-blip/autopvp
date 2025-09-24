package net.runelite.client.plugins.autopvp.util;

import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.plugins.autopvp.util.ItemIdMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;

/**
 * Translates RuneLite PlayerComposition equipment to Elvarg-compatible format.
 * Provides confidence-based blending for non-visible equipment slots.
 */
@Slf4j
public class TargetEquipmentTranslator {

    /**
     * PlayerComposition item offset for decoding equipment IDs
     * Citation: C:\Users\alexr\runelite\runelite-api\src\main\java\net\runelite\api\PlayerComposition.java:37
     * Source: int ITEM_OFFSET = 2048;
     * Verified: 2025-01-19
     */
    private static final int ITEM_OFFSET = 2048;

    /**
     * PlayerComposition kit offset for decoding kit IDs
     * Citation: C:\Users\alexr\runelite\runelite-api\src\main\java\net\runelite\api\PlayerComposition.java:36
     * Source: int KIT_OFFSET = 256;
     * Verified: 2025-01-19
     */
    private static final int KIT_OFFSET = 256;

    /**
     * Elvarg Equipment slot constants
     * Citation: C:\dev\naton\osrs-pvp-reinforcement-learning\simulation-rsps\ElvargServer\src\main\java\com\elvarg\game\model\container\impl\Equipment.java:29-69
     * Verified: 2025-01-19
     */
    private static final int HEAD_SLOT = 0;        // Line 29: public static final int HEAD_SLOT = 0;
    private static final int CAPE_SLOT = 1;        // Line 33: public static final int CAPE_SLOT = 1;
    private static final int AMULET_SLOT = 2;      // Line 37: public static final int AMULET_SLOT = 2;
    private static final int WEAPON_SLOT = 3;      // Line 41: public static final int WEAPON_SLOT = 3;
    private static final int BODY_SLOT = 4;        // Line 45: public static final int BODY_SLOT = 4;
    private static final int SHIELD_SLOT = 5;      // Line 49: public static final int SHIELD_SLOT = 5;
    // Note: Slot 6 does not exist in Elvarg
    private static final int LEG_SLOT = 7;         // Line 53: public static final int LEG_SLOT = 7;
    // Note: Slot 8 does not exist in Elvarg
    private static final int HANDS_SLOT = 9;       // Line 57: public static final int HANDS_SLOT = 9;
    private static final int FEET_SLOT = 10;       // Line 61: public static final int FEET_SLOT = 10;
    // Note: Slot 11 does not exist in Elvarg
    private static final int RING_SLOT = 12;       // Line 65: public static final int RING_SLOT = 12;
    private static final int AMMUNITION_SLOT = 13; // Line 69: public static final int AMMUNITION_SLOT = 13;

    /**
     * Total equipment slots including empty slots
     * Citation: Equipment.java uses 14-element arrays
     * Verified: 2025-01-19
     */
    private static final int EQUIPMENT_SIZE = 14;

    /**
     * Total number of bonuses in Elvarg system
     * Citation: C:\dev\naton\osrs-pvp-reinforcement-learning\simulation-rsps\ElvargServer\src\main\java\com\elvarg\game\model\equipment\BonusManager.java:73-82
     * Source: 14 total bonuses (5 attack + 5 defence + 4 other)
     * Verified: 2025-01-19
     */
    public static final int BONUS_COUNT = 14;

    /**
     * Result of equipment translation including confidence levels
     */
    public static class Result {
        private final int[] itemIds;
        private final double[] bonuses;
        private final double[] slotConfidences;

        public Result() {
            this.itemIds = new int[EQUIPMENT_SIZE];
            Arrays.fill(this.itemIds, -1); // Initialize with -1 (empty)
            this.bonuses = new double[BONUS_COUNT];
            this.slotConfidences = new double[EQUIPMENT_SIZE];
        }

        /**
         * Get equipment item IDs array
         * Critical: NhEnvironment calls target.getEquipment().getItemIdsArray()
         * Citation: NhEnvironment.java:629-711 - all getTargetCurrentGear methods
         * Verified: 2025-01-19
         */
        public int[] getItemIds() {
            return itemIds.clone();
        }

        public double[] getBonuses() {
            return bonuses.clone();
        }

        public double getAverageSlotConfidence() {
            double sum = 0;
            int count = 0;
            for (int i = 0; i < EQUIPMENT_SIZE; i++) {
                // Skip non-existent slots (6, 8, 11)
                if (i != 6 && i != 8 && i != 11) {
                    sum += slotConfidences[i];
                    count++;
                }
            }
            return count > 0 ? sum / count : 0.0;
        }

        public static Result empty(int playerId) {
            Result r = new Result();
            log.debug("[TRANSLATOR] Created empty result for player {}", playerId);
            return r;
        }
    }

    /**
     * KitType to Equipment slot mapping
     * Citation: KitType.java:39-50 ordinals mapped to Equipment slots
     * Verified: 2025-01-19
     */
    private static int getEquipmentSlot(KitType kit) {
        switch (kit) {
            case HEAD: return HEAD_SLOT;      // ordinal 0 -> slot 0
            case CAPE: return CAPE_SLOT;      // ordinal 1 -> slot 1
            case AMULET: return AMULET_SLOT;  // ordinal 2 -> slot 2
            case WEAPON: return WEAPON_SLOT;  // ordinal 3 -> slot 3
            case TORSO: return BODY_SLOT;     // ordinal 4 -> slot 4
            case SHIELD: return SHIELD_SLOT;  // ordinal 5 -> slot 5
            case LEGS: return LEG_SLOT;       // ordinal 7 -> slot 7
            case HANDS: return HANDS_SLOT;    // ordinal 9 -> slot 9
            case BOOTS: return FEET_SLOT;     // ordinal 10 -> slot 10
            case ARMS:  // No equipment slot for arms
            case HAIR:  // No equipment slot for hair
            case JAW:   // No equipment slot for jaw
            default: return -1;
        }
    }

    /**
     * Translate PlayerComposition to equipment data with confidence levels
     */
    public static Result translate(PlayerComposition comp, ItemManager itemManager, int playerId) {
        if (comp == null || itemManager == null) {
            return Result.empty(playerId);
        }

        Result result = new Result();
        int[] equipmentIds = comp.getEquipmentIds();

        // Process each KitType
        for (KitType kit : KitType.values()) {
            if (kit.getIndex() >= equipmentIds.length) continue;

            int encodedId = equipmentIds[kit.getIndex()];
            int slot = getEquipmentSlot(kit);

            if (slot == -1) continue; // Skip non-equipment kits

            /**
             * Decode item ID from PlayerComposition
             * Citation: PlayerComposition.java:64-67
             * Items >= 2048, Kits 256-2047
             * Verified: 2025-01-19
             */
            int itemId = -1;
            if (encodedId >= ITEM_OFFSET) {
                itemId = encodedId - ITEM_OFFSET;
            } else if (encodedId >= KIT_OFFSET) {
                // Kit, not an item - skip
                continue;
            }

            if (itemId > 0) {
                /**
                 * Canonicalize for noted/placeholder items
                 * Citation: ItemManager.java:433
                 * Verified: 2025-01-19
                 */
                itemId = itemManager.canonicalize(itemId);
                int elvargItemId = ItemIdMapper.toElvarg(itemId);
                result.itemIds[slot] = elvargItemId;

                /**
                 * Get item stats and add to bonuses
                 * Citation: ItemManager.java:362 - getItemStats(int itemId)
                 * Verified: 2025-01-19
                 */
                ItemStats stats = itemManager.getItemStats(itemId);
                if (stats != null && stats.getEquipment() != null) {
                    addBonuses(result.bonuses, stats.getEquipment());
                    result.slotConfidences[slot] = 0.95; // High confidence for visible
                } else {
                    result.slotConfidences[slot] = 0.1;  // Low confidence if no stats
                }

                log.debug("[TRANSLATOR] Slot {} ({}): Item {} with confidence {}",
                    slot, kit.name(), itemId, result.slotConfidences[slot]);
            }
        }

        // Ring and ammo are never visible in PlayerComposition
        result.slotConfidences[RING_SLOT] = 0.1;
        result.slotConfidences[AMMUNITION_SLOT] = 0.3;

        return result;
    }

    /**
     * Add equipment bonuses to result array
     * Must match exact indices used by NhEnvironment
     * Citation: NhEnvironment.java:629-711 bonus access patterns
     * Verified: 2025-01-19
     */
    private static void addBonuses(double[] bonuses, ItemEquipmentStats equip) {
        /**
         * Bonus mapping verified from NhEnvironment usage:
         * [1] = Melee accuracy (slash) - NhEnvironment.java:700
         * [3] = Magic accuracy - NhEnvironment.java:664
         * [4] = Ranged accuracy - NhEnvironment.java:682
         * [6] = Melee defense (slash) - NhEnvironment.java:645
         * [8] = Magic defense - NhEnvironment.java:655
         * [9] = Ranged defense - NhEnvironment.java:635
         * [10] = Melee strength - NhEnvironment.java:709
         * [11] = Ranged strength - NhEnvironment.java:691
         * [12] = Magic damage - NhEnvironment.java:673
         */
        bonuses[0] += equip.getAstab();   // Not used by NhEnvironment observations
        bonuses[1] += equip.getAslash();  // Melee accuracy
        bonuses[2] += equip.getAcrush();  // Not used by NhEnvironment observations
        bonuses[3] += equip.getAmagic();  // Magic accuracy
        bonuses[4] += equip.getArange();  // Ranged accuracy
        bonuses[5] += equip.getDstab();   // Not used by NhEnvironment observations
        bonuses[6] += equip.getDslash();  // Melee defense
        bonuses[7] += equip.getDcrush();  // Not used by NhEnvironment observations
        bonuses[8] += equip.getDmagic();  // Magic defense
        bonuses[9] += equip.getDrange();  // Ranged defense
        bonuses[10] += equip.getStr();    // Melee strength
        bonuses[11] += equip.getRstr();   // Ranged strength
        bonuses[12] += equip.getMdmg();   // Magic damage %
        bonuses[13] += equip.getPrayer(); // Prayer (not in observations)
    }

    /**
     * Blend translated bonuses with baseline using confidence weights
     */
    public static double[] blendBonuses(Result translation, double[] loadoutBaseline) {
        if (translation == null || loadoutBaseline == null) {
            return new double[BONUS_COUNT];
        }

        double[] blended = new double[BONUS_COUNT];
        double avgConfidence = translation.getAverageSlotConfidence();

        for (int i = 0; i < BONUS_COUNT; i++) {
            blended[i] = translation.bonuses[i] * avgConfidence +
                         loadoutBaseline[i] * (1.0 - avgConfidence);
        }

        log.debug("[TRANSLATOR] Blended bonuses with {}% confidence",
            Math.round(avgConfidence * 100));

        return blended;
    }

    /**
     * Compute baseline bonuses from loadout gear arrays
     * This matches how NhEnvironment calculates loadout bonuses
     * Citation: NhEnvironment.java:520-562 gear bonus calculations
     * Verified: 2025-01-19
     */
    public static double[] computeBaseline(ItemManager itemManager,
                                          int[] meleeGear, int[] rangedGear,
                                          int[] mageGear, int[] tankGear,
                                          int[] meleeSpecGear) {
        double[] baseline = new double[BONUS_COUNT];
        int gearSetCount = 0;

        int[][] gearSets = {meleeGear, rangedGear, mageGear, tankGear, meleeSpecGear};

        for (int[] gearSet : gearSets) {
            if (gearSet != null && gearSet.length > 0) {
                double[] setBonuses = computeGearSetBonuses(itemManager, gearSet);
                for (int i = 0; i < BONUS_COUNT; i++) {
                    baseline[i] += setBonuses[i];
                }
                gearSetCount++;
            }
        }

        // Average the bonuses across all gear sets
        if (gearSetCount > 0) {
            for (int i = 0; i < BONUS_COUNT; i++) {
                baseline[i] /= gearSetCount;
            }
        }

        return baseline;
    }

    /**
     * Compute bonuses for a single gear set
     */
    private static double[] computeGearSetBonuses(ItemManager itemManager, int[] gearSet) {
        double[] bonuses = new double[BONUS_COUNT];

        for (int itemId : gearSet) {
            if (itemId <= 0) continue;

            ItemStats stats = itemManager.getItemStats(itemId);
            if (stats != null && stats.getEquipment() != null) {
                addBonuses(bonuses, stats.getEquipment());
            }
        }

        return bonuses;
    }
}