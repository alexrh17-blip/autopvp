package net.runelite.client.plugins.autopvp.util;

import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

/**
 * Weapon detection matching Naton1's EXACT logic
 * NO fallbacks, NO extras - only what Naton1 checks
 */
public class NhWeaponDetector {

    /**
     * Combat type detection matching CombatStyles.java logic
     * Citation: C:/dev/naton/osrs-pvp-reinforcement-learning/simulation-rsps/ElvargServer/src/main/java/com/github/naton1/rl/util/CombatStyles.java:27-34
     */
    public static boolean isRangedWeapon(int itemId, ItemManager itemManager) {
        if (itemId <= 0) return false;

        ItemComposition comp = itemManager.getItemComposition(itemId);
        if (comp == null) return false;

        String name = comp.getName().toLowerCase();

        // Match Naton1's rangedWeapons set (CombatStyles.java:11-22)
        return name.contains("crossbow") || name.contains("bow") ||
               name.contains("ballista") || name.contains("javelin") ||
               name.contains("knife") || name.contains("dart") ||
               name.contains("thrownaxe") || name.contains("throwing axe") ||
               name.contains("blowpipe") || name.contains("chinchompa");
    }

    /**
     * Citation: CombatStyles.java:24-25
     */
    public static boolean isMageWeapon(int itemId, ItemManager itemManager) {
        if (itemId <= 0) return false;

        ItemComposition comp = itemManager.getItemComposition(itemId);
        if (comp == null) return false;

        String name = comp.getName().toLowerCase();

        // Match Naton1's mageWeapons set
        return name.contains("staff") || name.contains("wand") ||
               name.contains("sceptre") || name.contains("trident");
    }

    /**
     * Citation: CombatStyles.java:33 - Everything else is melee
     */
    public static boolean isMeleeWeapon(int itemId, ItemManager itemManager) {
        return !isRangedWeapon(itemId, itemManager) && !isMageWeapon(itemId, itemManager);
    }

    // =============== SPECIFIC WEAPON CHECKS ===============
    // All citations from NhEnvironment.java

    // Citation: line 789-790
    public static boolean isEnchantedOpalBolts(int itemId) {
        return itemId == ItemID.OPAL_DRAGON_BOLTS_E ||
               itemId == ItemID.OPAL_BOLTS_E;
    }

    // Citation: line 793-795
    public static boolean isEnchantedDragonBolts(int itemId) {
        return itemId == ItemID.DRAGONSTONE_DRAGON_BOLTS_E ||
               itemId == ItemID.DRAGONSTONE_BOLTS_E;
    }

    // Citation: line 798-800
    public static boolean isEnchantedDiamondBolts(int itemId) {
        return itemId == ItemID.DIAMOND_DRAGON_BOLTS_E ||
               itemId == ItemID.DIAMOND_BOLTS_E;
    }

    // Citation: line 1675-1676
    public static boolean isNightmareStaff(int itemId) {
        return itemId == ItemID.VOLATILE_NIGHTMARE_STAFF;
    }

    // Citation: line 1667-1668
    public static boolean isZaryteCrossbow(int itemId) {
        return itemId == ItemID.ZARYTE_CROSSBOW;
    }

    // Citation: line 1671-1672
    public static boolean isBallista(int itemId) {
        return itemId == ItemID.LIGHT_BALLISTA ||
               itemId == ItemID.HEAVY_BALLISTA;
    }

    // Citation: line 1718-1719
    public static boolean isDragonKnives(int itemId) {
        return itemId == ItemID.DRAGON_KNIFE ||
               itemId == ItemID.DRAGON_KNIFE_P ||
               itemId == ItemID.DRAGON_KNIFE_P_ ||
               itemId == ItemID.DRAGON_KNIFE_P__;
    }

    // Citation: line 1722-1723
    public static boolean isDarkBow(int itemId) {
        return itemId == ItemID.DARK_BOW;
    }

    // Citation: line 1726-1727
    public static boolean isMorrigansJavelins(int itemId) {
        return itemId == ItemID.MORRIGANS_JAVELIN;
    }

    // Citation: line 807-808
    public static boolean isBloodFury(int itemId) {
        return itemId == ItemID.AMULET_OF_BLOOD_FURY;
    }

    // Citation: line 811-813
    public static boolean isDharoksSet(int itemId) {
        return itemId == ItemID.DHAROKS_GREATAXE ||
               itemId == ItemID.DHAROKS_GREATAXE_100 ||
               itemId == ItemID.DHAROKS_GREATAXE_75 ||
               itemId == ItemID.DHAROKS_GREATAXE_50 ||
               itemId == ItemID.DHAROKS_GREATAXE_25 ||
               itemId == ItemID.DHAROKS_GREATAXE_0;
    }

    // Citation: line 1698-1699
    public static boolean isZurielStaff(int itemId) {
        return itemId == ItemID.ZURIELS_STAFF;
    }

    // Citation: line 781-782
    public static boolean isMeleeSpecDds(int itemId) {
        return itemId == ItemID.DRAGON_DAGGER ||
               itemId == ItemID.DRAGON_DAGGER_P ||
               itemId == ItemID.DRAGON_DAGGER_P_ ||
               itemId == ItemID.DRAGON_DAGGER_P__;
    }

    // Citation: line 785-786
    public static boolean isMeleeSpecDclaws(int itemId) {
        return itemId == ItemID.DRAGON_CLAWS;
    }

    // Citation: line 803-804
    public static boolean isMeleeSpecAgs(int itemId) {
        return itemId == ItemID.ARMADYL_GODSWORD;
    }

    // Citation: line 1714-1715
    public static boolean isMeleeSpecVls(int itemId) {
        return itemId == ItemID.VESTAS_LONGSWORD;
    }

    // Citation: line 1710-1711
    public static boolean isMeleeSpecStatHammer(int itemId) {
        return itemId == ItemID.STATIUSS_WARHAMMER;
    }

    // Citation: line 1702-1703
    public static boolean isMeleeSpecAncientGodsword(int itemId) {
        return itemId == ItemID.ANCIENT_GODSWORD;
    }

    // Citation: line 1706-1707
    public static boolean isMeleeSpecGraniteMaul(int itemId) {
        return itemId == ItemID.GRANITE_MAUL ||
               itemId == ItemID.GRANITE_MAUL_PRETTY;
    }

    // Citation: line 1838-1843 - checks if has special attack
    public static boolean isMeleeSpecialWeapon(int itemId) {
        return isMeleeSpecDds(itemId) ||
               isMeleeSpecDclaws(itemId) ||
               isMeleeSpecAgs(itemId) ||
               isMeleeSpecVls(itemId) ||
               isMeleeSpecStatHammer(itemId) ||
               isMeleeSpecAncientGodsword(itemId) ||
               isMeleeSpecGraniteMaul(itemId);
    }

    // Citation: line 2133-2134 - mage spec weapon check
    public static boolean isMageSpecWeapon(int itemId) {
        return isNightmareStaff(itemId);
    }

    // Citation: line 2129-2130 - range spec weapon check
    public static boolean isRangeSpecWeapon(int itemId) {
        return isZaryteCrossbow(itemId) ||
               isBallista(itemId) ||
               isMorrigansJavelins(itemId) ||
               isDarkBow(itemId) ||
               isDragonKnives(itemId);
    }
}