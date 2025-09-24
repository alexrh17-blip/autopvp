package net.runelite.client.plugins.autopvp.core;

import com.elvarg.game.content.combat.WeaponInterfaces;
import com.elvarg.game.content.combat.WeaponInterfaces.WeaponInterface;
import com.elvarg.game.definition.ItemDefinition;
import com.elvarg.game.model.EquipmentType;
import com.elvarg.game.model.Item;
import com.github.naton1.rl.env.nh.NhLmsMedLoadout;
import com.github.naton1.rl.env.nh.NhLmsPureLoadout;
import com.github.naton1.rl.env.nh.NhLmsZerkLoadout;
import com.github.naton1.rl.env.nh.NhLoadout;
import com.github.naton1.rl.env.nh.NhMaxLoadout;
import com.github.naton1.rl.env.nh.NhMedLoadout;
import com.github.naton1.rl.env.nh.NhPureLoadout;
import com.github.naton1.rl.env.nh.NhZerkLoadout;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.autopvp.core.NhObservationEncoder;

/**
 * Initializes ItemDefinition data for items used in NhEnvironment.
 * Builds the Elvarg definition cache from RuneLite's item statistics so that
 * naton1's RSPS logic can execute without falling back to zeroed data.
 */
@Slf4j
public final class ItemDefinitionInitializer {

    private static final int BONUS_COUNT = 14;

    private ItemDefinitionInitializer() {
    }

    static {
        // Fix ItemDefinition.DEFAULT to have non-null bonuses
        // Citation: ItemDefinition.java:26 DEFAULT created with null bonuses field
        // Citation: ItemDefinition.java:53 bonuses field not initialized
        // Citation: ItemDefinition.java:64 forId returns DEFAULT for unknown items
        try {
            if (ItemDefinition.DEFAULT.getBonuses() == null) {
                Field bonusesField = ItemDefinition.class.getDeclaredField("bonuses");
                bonusesField.setAccessible(true);
                bonusesField.set(ItemDefinition.DEFAULT, new int[BONUS_COUNT]);
                log.info("[AUTOPVP] Fixed ItemDefinition.DEFAULT bonuses array");
            }
        } catch (Exception e) {
            log.error("[AUTOPVP] Failed to fix DEFAULT bonuses", e);
        }
    }

    /**
     * Populate {@link ItemDefinition#definitions} using RuneLite item stats for
     * all gear referenced by the NH loadouts. Idempotent and safe to call multiple times.
     */
    public static void initialize(ItemManager itemManager) {
        if (itemManager == null) {
            log.warn("[AUTOPVP] ItemManager unavailable, cannot seed ItemDefinitions");
            return;
        }

        Set<Integer> itemIds = new LinkedHashSet<>();

        for (NhLoadout loadout : new NhLoadout[] {
            new NhPureLoadout(),
            new NhZerkLoadout(),
            new NhMedLoadout(),
            new NhMaxLoadout(),
            new NhLmsPureLoadout(),
            new NhLmsZerkLoadout(),
            new NhLmsMedLoadout()
        }) {
            collectLoadoutItems(loadout, itemIds);
        }

        itemIds.addAll(NhObservationEncoder.getTrackedWeaponIds());

        int registered = 0;
        for (int id : itemIds) {
            if (ensureItemDefinition(id, itemManager)) {
                registered++;
            }
        }

        log.info("[AUTOPVP] Seeded {} ItemDefinition entries ({} unique ids)", registered, itemIds.size());
    }

    /**
     * Ensure a single item definition exists for naton1's environment.
     *
     * @return true if a new definition was created, false otherwise
     */
    public static boolean ensureItemDefinition(int rawItemId, ItemManager itemManager) {
        if (rawItemId <= 0 || itemManager == null) {
            return false;
        }

        int itemId = itemManager.canonicalize(rawItemId);
        ItemDefinition existing = ItemDefinition.definitions.get(itemId);
        // Always fix items with null bonuses
        if (existing != null && existing.getBonuses() == null) {
            log.debug("[AUTOPVP] Fixing null bonuses for item {}", itemId);
            // Continue to update this definition
        } else if (existing != null && !allZero(existing.getBonuses())) {
            return false; // Already has valid non-zero bonuses
        }

        try {
            ItemDefinition definition = new ItemDefinition();
            setField(definition, "id", itemId);
            setField(definition, "name", itemManager.getItemComposition(itemId).getName());

            ItemStats stats = itemManager.getItemStats(itemId);
            if (stats != null) {
                setField(definition, "weight", stats.getWeight());
                ItemEquipmentStats equipmentStats = stats.getEquipment();
                if (equipmentStats != null) {
                    setField(definition, "doubleHanded", equipmentStats.isTwoHanded());
                    setField(definition, "bonuses", toBonusArray(equipmentStats));
                    setField(definition, "equipmentType", mapEquipmentType(equipmentStats.getSlot()));
                    if (equipmentStats.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()) {
                        setField(definition, "weaponInterface", inferWeaponInterface(itemId));
                    }
                } else {
                    setField(definition, "bonuses", new int[BONUS_COUNT]);
                    setField(definition, "equipmentType", EquipmentType.NONE);
                }
            } else {
                setField(definition, "bonuses", new int[BONUS_COUNT]);
                setField(definition, "equipmentType", EquipmentType.NONE);
            }

            ItemDefinition.definitions.put(itemId, definition);
            return true;
        } catch (Exception e) {
            log.error("[AUTOPVP] Unable to seed ItemDefinition for {}", itemId, e);
            return false;
        }
    }

    public static boolean isInitialized() {
        return !ItemDefinition.definitions.isEmpty();
    }

    private static void collectLoadoutItems(NhLoadout loadout, Set<Integer> sink) {
        Arrays.stream(loadout.getEquipment()).filter(Objects::nonNull).forEach(item -> sink.add(item.getId()));
        Arrays.stream(loadout.getInventory()).filter(Objects::nonNull).forEach(item -> sink.add(item.getId()));
        addAll(loadout.getMageGear(), sink);
        addAll(loadout.getRangedGear(), sink);
        addAll(loadout.getMeleeGear(), sink);
        addAll(loadout.getMeleeSpecGear(), sink);
        addAll(loadout.getTankGear(), sink);
    }

    private static void addAll(int[] ids, Set<Integer> sink) {
        if (ids == null) {
            return;
        }
        for (int id : ids) {
            if (id > 0) {
                sink.add(id);
            }
        }
    }

    private static boolean allZero(int[] bonuses) {
        for (int bonus : bonuses) {
            if (bonus != 0) {
                return false;
            }
        }
        return true;
    }

    private static void setField(ItemDefinition def, String fieldName, Object value) throws Exception {
        Field field = ItemDefinition.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(def, value);
    }

    private static int[] toBonusArray(ItemEquipmentStats stats) {
        int[] bonuses = new int[BONUS_COUNT];
        bonuses[0] = stats.getAstab();
        bonuses[1] = stats.getAslash();
        bonuses[2] = stats.getAcrush();
        bonuses[3] = stats.getAmagic();
        bonuses[4] = stats.getArange();
        bonuses[5] = stats.getDstab();
        bonuses[6] = stats.getDslash();
        bonuses[7] = stats.getDcrush();
        bonuses[8] = stats.getDmagic();
        bonuses[9] = stats.getDrange();
        bonuses[10] = stats.getStr();
        bonuses[11] = stats.getRstr();
        bonuses[12] = Math.round(stats.getMdmg());
        bonuses[13] = stats.getPrayer();
        return bonuses;
    }

    private static EquipmentType mapEquipmentType(int slot) {
        switch (slot) {
            case 0:
                return EquipmentType.FULL_HELMET;
            case 1:
                return EquipmentType.CAPE;
            case 2:
                return EquipmentType.AMULET;
            case 3:
                return EquipmentType.WEAPON;
            case 4:
                return EquipmentType.BODY;
            case 5:
                return EquipmentType.SHIELD;
            case 6:
                return EquipmentType.BODY;
            case 7:
                return EquipmentType.LEGS;
            case 9:
                return EquipmentType.GLOVES;
            case 10:
                return EquipmentType.BOOTS;
            case 12:
                return EquipmentType.RING;
            case 13:
                return EquipmentType.ARROWS;
            default:
                return EquipmentType.NONE;
        }
    }

    private static WeaponInterface inferWeaponInterface(int itemId) {
        if (itemId <= 0) {
            return WeaponInterfaces.WeaponInterface.UNARMED;
        }

        if (itemId == ItemID.XBOWS_CROSSBOW_RUNITE
            || itemId == ItemID.XBOWS_CROSSBOW_DRAGON
            || itemId == ItemID.BARROWS_KARIL_WEAPON
            || itemId == ItemID.ACB
            || itemId == ItemID.ZARYTE_XBOW) {
            return WeaponInterfaces.WeaponInterface.CROSSBOW;
        }

        if (itemId == ItemID.HEAVY_BALLISTA) {
            return WeaponInterfaces.WeaponInterface.BALLISTA;
        }

        if (itemId == ItemID.DARKBOW) {
            return WeaponInterfaces.WeaponInterface.DARK_BOW;
        }

        if (itemId == ItemID.TOXIC_BLOWPIPE || itemId == ItemID.TOXIC_BLOWPIPE_LOADED) {
            return WeaponInterfaces.WeaponInterface.BLOWPIPE;
        }

        if (itemId == ItemID.MAGIC_SHORTBOW || itemId == ItemID.MAGIC_SHORTBOW_I) {
            return WeaponInterfaces.WeaponInterface.SHORTBOW;
        }

        if (itemId == ItemID.TWISTED_BOW) {
            return WeaponInterfaces.WeaponInterface.LONGBOW;
        }

        if (itemId == ItemID.BARROWS_AHRIM_WEAPON
            || itemId == ItemID.BARROWS_AHRIM_WEAPON_100
            || itemId == ItemID.KODAI_WAND
            || itemId == ItemID.BR_KODAI_WAND
            || itemId == ItemID.SANGUINESTI_STAFF
            || itemId == ItemID.TOXIC_SOTD_CHARGED) {
            return WeaponInterfaces.WeaponInterface.STAFF;
        }

        if (itemId == ItemID.ABYSSAL_WHIP || itemId == ItemID.ABYSSAL_TENTACLE) {
            return WeaponInterfaces.WeaponInterface.WHIP;
        }

        if (itemId == ItemID.DRAGON_SCIMITAR) {
            return WeaponInterfaces.WeaponInterface.SCIMITAR;
        }

        if (itemId == ItemID.DRAGON_CLAWS) {
            return WeaponInterfaces.WeaponInterface.CLAWS;
        }

        if (itemId == ItemID.GRANITE_MAUL) {
            return WeaponInterfaces.WeaponInterface.GRANITE_MAUL;
        }

        if (itemId == ItemID.ANCIENT_GODSWORD) {
            return WeaponInterfaces.WeaponInterface.GODSWORD;
        }

        return WeaponInterfaces.WeaponInterface.UNARMED;
    }
}
