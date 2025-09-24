package net.runelite.client.plugins.autopvp.util;

import com.elvarg.util.ItemIdentifiers;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Bridges RuneLite item identifiers to the Elvarg constants Nato's PPO expects.
 * Most OSRS items share numeric IDs across both domains, but we keep an explicit
 * map for items where Nato's decision logic is sensitive (special weapons,
 * niche gear). Any unmapped item ID falls back to the RuneLite value.
 */
public final class ItemIdMapper
{
    private static final Map<Integer, Integer> RL_TO_ELVARG = new HashMap<>();

    static
    {
        // Melee spec weapons
        RL_TO_ELVARG.put(ItemID.ABYSSAL_TENTACLE, ItemIdentifiers.ABYSSAL_TENTACLE);
        RL_TO_ELVARG.put(ItemID.ABYSSAL_WHIP, ItemIdentifiers.ABYSSAL_WHIP);
        RL_TO_ELVARG.put(ItemID.DRAGON_CLAWS, ItemIdentifiers.DRAGON_CLAWS);
        RL_TO_ELVARG.put(ItemID.DRAGON_DAGGERP_PLUS_PLUS, ItemIdentifiers.DRAGON_DAGGER_P_PLUS_PLUS_);
        RL_TO_ELVARG.put(ItemID.VESTAS_LONGSWORD, ItemIdentifiers.VESTAS_LONGSWORD);
        RL_TO_ELVARG.put(ItemID.STATIUSS_WARHAMMER, ItemIdentifiers.STATIUSS_WARHAMMER);
        RL_TO_ELVARG.put(ItemID.ARMADYL_GODSWORD, ItemIdentifiers.ARMADYL_GODSWORD);
        RL_TO_ELVARG.put(ItemID.ANCIENT_GODSWORD, ItemIdentifiers.ANCIENT_GODSWORD);
        RL_TO_ELVARG.put(ItemID.DRAGON_SCIMITAR, ItemIdentifiers.DRAGON_SCIMITAR);

        // Ranged special weapons
        RL_TO_ELVARG.put(ItemID.HEAVY_BALLISTA, ItemIdentifiers.HEAVY_BALLISTA);
        RL_TO_ELVARG.put(ItemID.LIGHT_BALLISTA, ItemIdentifiers.LIGHT_BALLISTA);
        RL_TO_ELVARG.put(ItemID.DARK_BOW, ItemIdentifiers.DARK_BOW);
        RL_TO_ELVARG.put(ItemID.DRAGON_KNIFE, ItemIdentifiers.DRAGON_KNIFE);
        RL_TO_ELVARG.put(ItemID.MORRIGANS_JAVELIN, ItemIdentifiers.MORRIGANS_JAVELIN);
        RL_TO_ELVARG.put(ItemID.TOXIC_BLOWPIPE, ItemIdentifiers.TOXIC_BLOWPIPE);
        RL_TO_ELVARG.put(ItemID.TOXIC_BLOWPIPE_EMPTY, ItemIdentifiers.TOXIC_BLOWPIPE_UNCHARGED);
        RL_TO_ELVARG.put(ItemID.CRAWS_BOW, ItemIdentifiers.CRAWS_BOW);

        // Magic
        RL_TO_ELVARG.put(ItemID.NIGHTMARE_STAFF, ItemIdentifiers.NIGHTMARE_STAFF);
        RL_TO_ELVARG.put(ItemID.VOLATILE_NIGHTMARE_STAFF, ItemIdentifiers.VOLATILE_NIGHTMARE_STAFF);
        RL_TO_ELVARG.put(ItemID.HARMONISED_NIGHTMARE_STAFF, ItemIdentifiers.HARMONISED_NIGHTMARE_STAFF);
        RL_TO_ELVARG.put(ItemID.ELDRITCH_NIGHTMARE_STAFF, ItemIdentifiers.ELDRITCH_NIGHTMARE_STAFF);
        RL_TO_ELVARG.put(ItemID.KODAI_WAND, ItemIdentifiers.KODAI_WAND);
        RL_TO_ELVARG.put(ItemID.STAFF_OF_THE_DEAD, ItemIdentifiers.STAFF_OF_THE_DEAD);
        RL_TO_ELVARG.put(ItemID.TOXIC_STAFF_OF_THE_DEAD, ItemIdentifiers.TOXIC_STAFF_OF_THE_DEAD);

        // Jewellery & defensive staples
        RL_TO_ELVARG.put(ItemID.AMULET_OF_BLOOD_FURY, ItemIdentifiers.AMULET_OF_BLOOD_FURY);
        RL_TO_ELVARG.put(ItemID.FEROCIOUS_GLOVES, ItemIdentifiers.FEROCIOUS_GLOVES);
        RL_TO_ELVARG.put(ItemID.DHAROKS_GREATAXE, ItemIdentifiers.DHAROKS_GREATAXE);
    }

    private ItemIdMapper()
    {
        // Utility class
    }

    public static int toElvarg(int runeLiteItemId)
    {
        return RL_TO_ELVARG.getOrDefault(runeLiteItemId, runeLiteItemId);
    }
}
