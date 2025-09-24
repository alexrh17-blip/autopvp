package net.runelite.client.plugins.autopvp.core;

import java.util.Arrays;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.kit.KitType;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Builds action masks for NH environment based on game state.
 * Masks indicate which actions are currently valid/available.
 */
public final class NhActionMaskBuilder
{
    private NhActionMaskBuilder() {}

    /**
     * Build action mask based on current game state.
     * Returns boolean[45] with true for available actions.
     */
    public static boolean[] build(Client client)
    {
        boolean[] masks = new boolean[NhContract.ACTION_FLAT_SIZE];
        Arrays.fill(masks, false);
        
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            // If no player, only allow no-op
            masks[0] = true; // Attack head: no-op
            return masks;
        }

        // Get target if we're interacting
        Actor target = localPlayer.getInteracting();
        Player targetPlayer = (target instanceof Player) ? (Player) target : null;
        
        // Get equipment info
        int weaponId = getLocalCanonicalWeaponId(client, localPlayer);
        
        // === Head 0: Attack type [no-op, mage, ranged, melee] ===
        int h0 = NhContract.headOffset(0);
        masks[h0] = true; // no-op always available
        
        if (targetPlayer != null) {
            // Check which attack styles are available
            if (canCastSpells(client)) {
                masks[h0 + 1] = true; // mage
            }
            if (hasRangedWeapon(weaponId)) {
                masks[h0 + 2] = true; // ranged
            }
            masks[h0 + 3] = true; // melee (always available, even unarmed)
        }

        // === Head 1: Melee attack type [none, basic, spec] ===
        int h1 = NhContract.headOffset(1);
        masks[h1] = true; // none
        if (targetPlayer != null) {
            masks[h1 + 1] = true; // basic melee
            if (hasSpecialAttack(weaponId) && getSpecialPercentage(client) >= 25) {
                masks[h1 + 2] = true; // spec
            }
        }

        // === Head 2: Ranged attack type [none, basic, spec] ===
        int h2 = NhContract.headOffset(2);
        masks[h2] = true; // none
        if (targetPlayer != null && hasRangedWeapon(weaponId)) {
            masks[h2 + 1] = true; // basic ranged
            if (hasRangedSpec(weaponId) && getSpecialPercentage(client) >= 25) {
                masks[h2 + 2] = true; // spec
            }
        }

        // === Head 3: Mage attack type [none, ice, blood, spec] ===
        int h3 = NhContract.headOffset(3);
        masks[h3] = true; // none
        if (targetPlayer != null && canCastSpells(client)) {
            if (client.getRealSkillLevel(Skill.MAGIC) >= 94) {
                masks[h3 + 1] = true; // ice barrage
            }
            if (client.getRealSkillLevel(Skill.MAGIC) >= 92) {
                masks[h3 + 2] = true; // blood barrage
            }
            if (hasMageSpec(weaponId) && getSpecialPercentage(client) >= 50) {
                masks[h3 + 3] = true; // mage spec
            }
        }

        // === Head 4: Potion [none, brew, restore, combat, ranging] ===
        int h4 = NhContract.headOffset(4);
        masks[h4] = true; // none always available
        // Would need inventory scanning to check potion availability
        // For now, assume all potions could be available
        masks[h4 + 1] = true; // brew
        masks[h4 + 2] = true; // restore
        masks[h4 + 3] = true; // combat
        masks[h4 + 4] = true; // ranging

        // === Head 5: Primary food [none, eat] ===
        int h5 = NhContract.headOffset(5);
        masks[h5] = true; // none
        masks[h5 + 1] = true; // eat (assume food available)

        // === Head 6: Karambwan [none, eat] ===
        int h6 = NhContract.headOffset(6);
        masks[h6] = true; // none
        masks[h6 + 1] = true; // eat karam (assume available)

        // === Head 7: Vengeance [none, cast] ===
        int h7 = NhContract.headOffset(7);
        masks[h7] = true; // none
        // Would need to check lunar spellbook and cooldown
        if (client.getRealSkillLevel(Skill.MAGIC) >= 94) {
            masks[h7 + 1] = true; // cast veng (assume available)
        }

        // === Head 8: Gear switch [none, tank] ===
        int h8 = NhContract.headOffset(8);
        masks[h8] = true; // none
        masks[h8 + 1] = true; // tank switch (assume available)

        // === Head 9: Movement [none, adjacent, under, farcast, diagonal] ===
        int h9 = NhContract.headOffset(9);
        masks[h9] = true; // none
        if (targetPlayer != null) {
            masks[h9 + 1] = true; // move adjacent
            masks[h9 + 2] = true; // move under
            masks[h9 + 3] = true; // farcast position
            masks[h9 + 4] = true; // diagonal
        }

        // === Head 10: Farcast distance [none, 2, 3, 4, 5, 6, 7] ===
        int h10 = NhContract.headOffset(10);
        masks[h10] = true; // none
        if (targetPlayer != null) {
            // All distances potentially available
            for (int i = 1; i <= 6; i++) {
                masks[h10 + i] = true;
            }
        }

        // === Head 11: Overhead prayer [none, mage, range, melee, smite, redemption] ===
        int h11 = NhContract.headOffset(11);
        masks[h11] = true; // none
        if (client.getRealSkillLevel(Skill.PRAYER) >= 43) {
            // Protection prayers
            masks[h11 + 1] = true; // protect mage
            masks[h11 + 2] = true; // protect range
            masks[h11 + 3] = true; // protect melee
        }
        if (client.getRealSkillLevel(Skill.PRAYER) >= 52) {
            masks[h11 + 4] = true; // smite
        }
        if (client.getRealSkillLevel(Skill.PRAYER) >= 49) {
            masks[h11 + 5] = true; // redemption
        }

        return masks;
    }

    private static float getSpecialPercentage(Client client)
    {
        return client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10f;
    }

    private static boolean canCastSpells(Client client)
    {
        // Basic check - would need to verify spellbook and runes
        return client.getRealSkillLevel(Skill.MAGIC) >= 70;
    }

    private static int getLocalCanonicalWeaponId(Client client, Player localPlayer)
    {
        if (client != null && localPlayer != null)
        {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment != null)
            {
                Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
                if (weapon != null)
                {
                    int canonical = canonicalizeItemId(weapon.getId());
                    if (canonical > 0)
                    {
                        return canonical;
                    }
                }
            }
        }

        PlayerComposition comp = localPlayer != null ? localPlayer.getPlayerComposition() : null;
        if (comp == null)
        {
            return -1;
        }

        int encodedId = comp.getEquipmentId(KitType.WEAPON);
        int decodedId = decodeEquipmentId(encodedId);
        return canonicalizeItemId(decodedId);
    }

    private static int decodeEquipmentId(int encodedId)
    {
        if (encodedId >= PlayerComposition.ITEM_OFFSET)
        {
            int id = encodedId - PlayerComposition.ITEM_OFFSET;
            return id > 0 ? id : -1;
        }
        return encodedId > 0 ? encodedId : -1;
    }

    private static int canonicalizeItemId(int itemId)
    {
        if (itemId <= 0)
        {
            return -1;
        }
        int mapped = ItemVariationMapping.map(itemId);
        return mapped > 0 ? mapped : -1;
    }

    private static boolean hasRangedWeapon(int itemId)
    {
        return itemId == ItemID.MAGIC_SHORTBOW ||
               itemId == ItemID.MAGIC_SHORTBOW_I ||
               itemId == ItemID.DARKBOW ||
               itemId == ItemID.HEAVY_BALLISTA ||
               itemId == ItemID.LIGHT_BALLISTA ||
               itemId == ItemID.XBOWS_CROSSBOW_RUNITE ||
               itemId == ItemID.XBOWS_CROSSBOW_DRAGON ||
               itemId == ItemID.ACB ||
               itemId == ItemID.DRAGON_KNIFE ||
               itemId == ItemID.DRAGON_KNIFE_P_ ||
               itemId == ItemID.DRAGON_KNIFE_P__;
    }

    private static boolean hasSpecialAttack(int itemId)
    {
        return itemId == ItemID.DRAGON_DAGGER ||
               itemId == ItemID.DRAGON_DAGGER_P_ ||
               itemId == ItemID.DRAGON_DAGGER_P__ ||
               itemId == ItemID.DRAGON_CLAWS ||
               itemId == ItemID.AGS ||
               itemId == ItemID.GRANITE_MAUL ||
               itemId == ItemID.ANCIENT_GODSWORD;
    }

    private static boolean hasRangedSpec(int itemId)
    {
        return itemId == ItemID.DARKBOW ||
               itemId == ItemID.HEAVY_BALLISTA ||
               itemId == ItemID.LIGHT_BALLISTA ||
               itemId == ItemID.DRAGON_KNIFE ||
               itemId == ItemID.DRAGON_KNIFE_P_ ||
               itemId == ItemID.DRAGON_KNIFE_P__;
    }

    private static boolean hasMageSpec(int itemId)
    {
        return itemId == ItemID.NIGHTMARE_STAFF;
    }
}