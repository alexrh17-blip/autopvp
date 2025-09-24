package net.runelite.client.plugins.autopvp.core;

import java.util.Arrays;
import java.util.Set;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.kit.KitType;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Varbits;
import net.runelite.api.InventoryID;
import net.runelite.client.plugins.autopvp.adapters.TimerManagerAdapter;
import net.runelite.client.plugins.autopvp.adapters.DamageTrackerAdapter;
import com.elvarg.util.timers.TimerKey;

/**
 * Encodes RuneLite client state into NH environment observation vector.
 * Maps available data to indices defined in NhContract.OBS_IDS.
 */
public final class NhObservationEncoder
{
    private NhObservationEncoder() {}

    /**
     * Encode client state into observation vector.
     * Returns float[176] with available fields populated, others 0.
     */
    public static float[] encode(Client client, TimerManagerAdapter timers, DamageTrackerAdapter damage)
    {
        float[] obs = new float[NhContract.OBS_SIZE];

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return obs;
        }

        // Get target if we're interacting with another player
        Actor target = localPlayer.getInteracting();
        Player targetPlayer = (target instanceof Player) ? (Player) target : null;

        // === Player equipment & combat style (0-4) ===
        encodeEquipmentStyle(localPlayer, obs);

        // Special percentage (4) — varp 300 is 0..1000 → normalize to 0..1
        obs[4] = getSpecialPercent01(client);

        // === Player prayers (5-9) ===
        encodeSelfPrayers(client, obs);

        // === Health (10-11) ===
        obs[10] = getLocalPlayerHealthPercent(client);
        obs[11] = getTargetHealthPercent(targetPlayer);

        // === Target equipment (12-15) ===
        if (targetPlayer != null) {
            encodeTargetEquipment(targetPlayer, obs);

            // === Target prayers (16-20) ===
            encodeTargetPrayers(targetPlayer, obs);
        }

        // === Resources (21-27) ===
        encodeInventoryResources(client, obs);

        // === Prayer points (28) ===
        obs[28] = getPrayerPointScale(client);

        // === Frozen status (29-32) ===
        encodeFreezeStatus(timers, obs);

        // === Range check (33) ===
        obs[33] = isInMeleeRange(localPlayer, targetPlayer) ? 1f : 0f;

        // === Skill levels (34-38) — relative to target ===
        if (targetPlayer != null) {
            encodeRelativeLevels(obs);
        }

        // === Timing (39-45) ===
        encodeTimingInfo(timers, obs);

        // === Combat state (46-53) ===
        encodeCombatState(damage, obs);

        // === Movement state (54-57) ===
        obs[54] = (targetPlayer != null && localPlayer.getInteracting() == targetPlayer) ? 1f : 0f;
        obs[55] = isMoving(localPlayer) ? 1f : 0f;
        obs[56] = (targetPlayer != null && isMoving(targetPlayer)) ? 1f : 0f;
        // PID (57) unknown on live client

        // === Spell availability (58-59) ===
        obs[58] = canCastIceBarrage(client) ? 1f : 0f;
        obs[59] = canCastBloodBarrage(client) ? 1f : 0f;

        // === Distance metrics (60-62) ===
        if (targetPlayer != null) {
            float dist = getDistance(localPlayer, targetPlayer);
            obs[62] = Math.min(dist, 10f) / 10f; // clamp and normalize
        }

        // === Absolute levels (96-102) ===
        encodeAbsoluteLevels(client, obs);

        // === Game modes (160-161) ===
        encodeGameModes(client, obs);

        // === Vengeance (162-167) ===
        encodeVengeanceStatus(client, localPlayer, targetPlayer, timers, obs);

        // === Attack availability (168-175) ===
        // Light heuristics based on equipment presence
        obs[171] = hasRangedWeapon(localPlayer) ? 1f : 0f;
        obs[173] = hasMeleeWeapon(localPlayer) ? 1f : 0f;

        return obs;
    }

    private static void encodeEquipmentStyle(Player player, float[] obs)
    {
        PlayerComposition comp = player.getPlayerComposition();
        if (comp == null) return;

        int weaponId = comp.getEquipmentId(KitType.WEAPON);

        // Attack style flags (mutually exclusive here)
        if (isMeleeWeapon(weaponId)) {
            obs[0] = 1f; // isMeleeEquipped
        } else if (isRangedWeapon(weaponId)) {
            obs[1] = 1f; // isRangedEquipped
        } else if (isMageWeapon(weaponId)) {
            obs[2] = 1f; // isMageEquipped
        }

        // Special melee weapon flag
        if (hasSpecialAttack(weaponId)) {
            obs[3] = 1f; // isMeleeSpecialWeaponEquipped
        }
    }

    private static void encodeSelfPrayers(Client client, float[] obs)
    {
        // Player prayers (5-9)
        obs[5]  = client.isPrayerActive(Prayer.PROTECT_FROM_MELEE) ? 1f : 0f;
        obs[6]  = client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES) ? 1f : 0f;
        obs[7]  = client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC) ? 1f : 0f;
        obs[8]  = client.isPrayerActive(Prayer.SMITE) ? 1f : 0f;
        obs[9]  = client.isPrayerActive(Prayer.REDEMPTION) ? 1f : 0f;
    }

    private static void encodeTargetEquipment(Player target, float[] obs)
    {
        PlayerComposition comp = target.getPlayerComposition();
        if (comp == null) return;

        int weaponId = comp.getEquipmentId(KitType.WEAPON);

        if (isMeleeWeapon(weaponId)) {
            obs[12] = 1f; // isTargetMeleeEquipped
        } else if (isRangedWeapon(weaponId)) {
            obs[13] = 1f; // isTargetRangedEquipped
        } else if (isMageWeapon(weaponId)) {
            obs[14] = 1f; // isTargetMageEquipped
        }

        if (hasSpecialAttack(weaponId)) {
            obs[15] = 1f; // isTargetMeleeSpecialWeaponEquipped
        }
    }

    private static void encodeTargetPrayers(Player target, float[] obs)
    {
        // Overhead icon exposure
        HeadIcon overhead = target.getOverheadIcon();
        if (overhead == null) return;

        switch (overhead)
        {
            case MELEE:
                obs[16] = 1f; // isTargetProtectMeleeActive
                break;
            case RANGED:
                obs[17] = 1f; // isTargetProtectRangedActive
                break;
            case MAGIC:
                obs[18] = 1f; // isTargetProtectMagicActive
                break;
            case SMITE:
                obs[19] = 1f; // isTargetSmiteActive
                break;
            case REDEMPTION:
                obs[20] = 1f; // isTargetRedemptionActive
                break;
            default:
                break;
        }
    }

    private static void encodeRelativeLevels(float[] obs)
    {
        // Target levels unknown in RuneLite - assume equal
        obs[34] = 0f; // relativeLevelStrength
        obs[35] = 0f; // relativeLevelAttack
        obs[36] = 0f; // relativeLevelDefence
        obs[37] = 0f; // relativeLevelRanged
        obs[38] = 0f; // relativeLevelMagic
    }

    private static void encodeAbsoluteLevels(Client client, float[] obs)
    {
        obs[96]  = client.getRealSkillLevel(Skill.ATTACK)     / 99f;
        obs[97]  = client.getRealSkillLevel(Skill.STRENGTH)   / 99f;
        obs[98]  = client.getRealSkillLevel(Skill.DEFENCE)    / 99f;
        obs[99]  = client.getRealSkillLevel(Skill.RANGED)     / 99f;
        obs[100] = client.getRealSkillLevel(Skill.MAGIC)      / 99f;
        obs[101] = client.getRealSkillLevel(Skill.PRAYER)     / 99f;
        obs[102] = client.getRealSkillLevel(Skill.HITPOINTS)  / 99f;
    }

    private static final Set<Integer> TRACKED_WEAPON_IDS = Set.of(
        ItemID.XBOWS_CROSSBOW_RUNITE,
        ItemID.XBOWS_CROSSBOW_DRAGON,
        ItemID.ACB,
        ItemID.ZARYTE_XBOW,
        ItemID.HEAVY_BALLISTA,
        ItemID.LIGHT_BALLISTA,
        ItemID.MORRIGANS_JAVELIN,
        ItemID.DRAGON_THROWNAXE,
        ItemID.DRAGON_KNIFE,
        ItemID.DRAGON_KNIFE_P,
        ItemID.DRAGON_KNIFE_P_,
        ItemID.DRAGON_KNIFE_P__,
        ItemID.DRAGON_DAGGER,
        ItemID.DRAGON_DAGGER_P,
        ItemID.DRAGON_DAGGER_P_,
        ItemID.DRAGON_DAGGER_P__,
        ItemID.DRAGON_CLAWS,
        ItemID.ANCIENT_GODSWORD,
        ItemID.AGS,
        ItemID.SGS,
        ItemID.BGS,
        ItemID.ZGS,
        ItemID.VESTAS_LONGSWORD,
        ItemID.STATIUS_WARHAMMER,
        ItemID.GRANITE_MAUL,
        ItemID.NIGHTMARE_STAFF,
        ItemID.NIGHTMARE_STAFF_HARMONISED,
        ItemID.NIGHTMARE_STAFF_VOLATILE,
        ItemID.NIGHTMARE_STAFF_ELDRITCH,
        ItemID.ZURIELS_STAFF,
        ItemID.TOXIC_SOTD_CHARGED,
        ItemID.SOTD,
        ItemID.STAFF_OF_LIGHT,
        ItemID.GHRAZI_RAPIER,
        ItemID.BLOOD_AMULET,
        ItemID.BARROWS_DHAROK_WEAPON,
        ItemID.BARROWS_DHAROK_HEAD,
        ItemID.BARROWS_DHAROK_BODY,
        ItemID.BARROWS_DHAROK_LEGS,
        ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED,
        ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND,
        ItemID.DRAGON_BOLTS_ENCHANTED_DRAGONSTONE,
        ItemID.DRAGON_BOLTS_ENCHANTED_OPAL
    );

    public static Set<Integer> getTrackedWeaponIds()
    {
        return TRACKED_WEAPON_IDS;
    }

    private static final java.util.concurrent.ConcurrentMap<Integer, Float> TARGET_HEALTH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile float lastLocalHealthPercent = 1f;

    private static float getLocalPlayerHealthPercent(Client client)
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return 0f;
        }

        Float fromHealthBar = computeHealthPercentFromBar(localPlayer);
        if (fromHealthBar != null)
        {
            lastLocalHealthPercent = fromHealthBar;
            return fromHealthBar;
        }

        int boosted = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int real = client.getRealSkillLevel(Skill.HITPOINTS);
        if (boosted > 0 && real > 0)
        {
            float percent = Math.min(1f, Math.max(0f, boosted / (float) Math.max(real, 1)));
            lastLocalHealthPercent = percent;
            return percent;
        }

        return lastLocalHealthPercent;
    }

    private static float getTargetHealthPercent(Player targetPlayer)
    {
        if (targetPlayer == null)
        {
            return 0f;
        }

        int identity = System.identityHashCode(targetPlayer);
        Float fromHealthBar = computeHealthPercentFromBar(targetPlayer);
        if (fromHealthBar != null)
        {
            TARGET_HEALTH_CACHE.put(identity, fromHealthBar);
            return fromHealthBar;
        }

        if (targetPlayer.getHealthRatio() == 0)
        {
            TARGET_HEALTH_CACHE.put(identity, 0f);
            return 0f;
        }

        return TARGET_HEALTH_CACHE.getOrDefault(identity, 1f);
    }

    private static Float computeHealthPercentFromBar(Player player)
    {
        int ratio = player.getHealthRatio();
        int scale = player.getHealthScale();
        if (scale > 0 && ratio >= 0)
        {
            if (ratio == 0)
            {
                return 0f;
            }
            return ratio / (float) scale;
        }
        return null;
    }

private static float getSpecialPercent01(Client client)
    {
        // VarPlayer.SPECIAL_ATTACK_PERCENT is 0..1000 on RL
        int raw = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
        if (raw < 0) raw = 0;
        if (raw > 1000) raw = 1000;
        return raw / 1000f;
    }

    private static float getPrayerPointScale(Client client)
    {
        int current = client.getBoostedSkillLevel(Skill.PRAYER);
        int max = client.getRealSkillLevel(Skill.PRAYER);
        return max > 0 ? (float) current / (float) max : 0f;
    }

    private static boolean isInMeleeRange(Player player, Player target)
    {
        if (player == null || target == null) return false;
        return getDistance(player, target) <= 1.0f;
    }

    private static float getDistance(Player from, Player to)
    {
        if (from == null || to == null) return Float.MAX_VALUE;
        WorldPoint p1 = from.getWorldLocation();
        WorldPoint p2 = to.getWorldLocation();
        if (p1 == null || p2 == null) return Float.MAX_VALUE;
        int dx = p1.getX() - p2.getX();
        int dy = p1.getY() - p2.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean isMoving(Player player)
    {
        return player.getPoseAnimation() != player.getIdlePoseAnimation();
    }

    private static boolean canCastIceBarrage(Client client)
    {
        // Req 94 magic (runes check omitted)
        return client.getRealSkillLevel(Skill.MAGIC) >= 94;
    }

    private static boolean canCastBloodBarrage(Client client)
    {
        // Req 92 magic (runes check omitted)
        return client.getRealSkillLevel(Skill.MAGIC) >= 92;
    }

    // === Weapon type checks ===

    private static boolean isMeleeWeapon(int itemId)
    {
        // Common melee weapons
        return itemId == ItemID.DRAGON_SCIMITAR ||
               itemId == ItemID.ABYSSAL_WHIP ||
               itemId == ItemID.DRAGON_DAGGER ||
               itemId == ItemID.DRAGON_DAGGER_P_ ||
               itemId == ItemID.DRAGON_DAGGER_P__ ||
               itemId == ItemID.DRAGON_CLAWS ||
               itemId == ItemID.AGS ||
               itemId == ItemID.GRANITE_MAUL ||
               itemId == ItemID.DRAGON_MACE ||
               itemId == ItemID.ANCIENT_GODSWORD;
    }

    private static boolean isRangedWeapon(int itemId)
    {
        // Common ranged weapons
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

    private static boolean isMageWeapon(int itemId)
    {
        // Common mage weapons
        return itemId == ItemID.STAFF_OF_ZAROS ||
               itemId == ItemID.KODAI_WAND ||
               itemId == ItemID.NIGHTMARE_STAFF ||
               itemId == ItemID.TOXIC_SOTD_CHARGED ||
               itemId == ItemID.SOTD ||
               itemId == ItemID.STAFF_OF_LIGHT;
    }

    private static boolean hasSpecialAttack(int itemId)
    {
        // Weapons with special attacks (melee + some ranged)
        return itemId == ItemID.DRAGON_DAGGER ||
               itemId == ItemID.DRAGON_DAGGER_P_ ||
               itemId == ItemID.DRAGON_DAGGER_P__ ||
               itemId == ItemID.DRAGON_CLAWS ||
               itemId == ItemID.AGS ||
               itemId == ItemID.GRANITE_MAUL ||
               itemId == ItemID.ANCIENT_GODSWORD ||
               itemId == ItemID.DARKBOW ||
               itemId == ItemID.HEAVY_BALLISTA ||
               itemId == ItemID.LIGHT_BALLISTA ||
               itemId == ItemID.DRAGON_KNIFE ||
               itemId == ItemID.DRAGON_KNIFE_P_ ||
               itemId == ItemID.DRAGON_KNIFE_P__;
    }

    private static boolean hasRangedWeapon(Player player)
    {
        PlayerComposition comp = player.getPlayerComposition();
        if (comp == null) return false;
        return isRangedWeapon(comp.getEquipmentId(KitType.WEAPON));
    }

    private static boolean hasMeleeWeapon(Player player)
    {
        PlayerComposition comp = player.getPlayerComposition();
        if (comp == null) return false;
        int weaponId = comp.getEquipmentId(KitType.WEAPON);
        // Unarmed counts as melee
        return weaponId == -1 || isMeleeWeapon(weaponId);
    }

    private static void encodeInventoryResources(Client client, float[] obs)
    {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return;

        Item[] items = inv.getItems();

        // Count potions
        int rangingDoses = countPotionDoses(items, ItemID._4DOSERANGERSPOTION, ItemID._3DOSERANGERSPOTION,  // Ranging 4 and 3
                                           ItemID._2DOSERANGERSPOTION, ItemID._1DOSERANGERSPOTION,    // Ranging 2 and 1
                                           ItemID._4DOSEBASTION, ItemID._3DOSEBASTION,  // Bastion 4 and 3
                                           ItemID._2DOSEBASTION, ItemID._1DOSEBASTION); // Bastion 2 and 1

        int superCombatDoses = countPotionDoses(items, ItemID._4DOSE2COMBAT, ItemID._3DOSE2COMBAT,  // Super combat 4 and 3
                                                ItemID._2DOSE2COMBAT, ItemID._1DOSE2COMBAT);  // Super combat 2 and 1

        int magicDoses = countPotionDoses(items, ItemID._4DOSE1MAGIC, ItemID._3DOSE1MAGIC,  // Magic 4 and 3
                                         ItemID._2DOSE1MAGIC, ItemID._1DOSE1MAGIC,    // Magic 2 and 1
                                         ItemID._4DOSEBATTLEMAGE, ItemID._3DOSEBATTLEMAGE,  // Battlemage 4 and 3
                                         ItemID._2DOSEBATTLEMAGE, ItemID._1DOSEBATTLEMAGE); // Battlemage 2 and 1

        int sarasBrews = countPotionDoses(items, ItemID._4DOSEPOTIONOFSARADOMIN, ItemID._3DOSEPOTIONOFSARADOMIN,  // Sara brew 4 and 3
                                          ItemID._2DOSEPOTIONOFSARADOMIN, ItemID._1DOSEPOTIONOFSARADOMIN);  // Sara brew 2 and 1

        int superRestores = countPotionDoses(items, ItemID._4DOSE2RESTORE, ItemID._3DOSE2RESTORE,  // Super restore 4 and 3
                                            ItemID._2DOSE2RESTORE, ItemID._1DOSE2RESTORE,   // Super restore 2 and 1
                                            ItemID.SANFEW_SALVE_4_DOSE, ItemID.SANFEW_SALVE_3_DOSE, // Sanfew serum 4 and 3
                                            ItemID.SANFEW_SALVE_2_DOSE, ItemID.SANFEW_SALVE_1_DOSE); // Sanfew serum 2 and 1

        // Count food
        int foodCount = countFood(items);

        // Store in observation vector (normalizing to 0-1)
        obs[21] = Math.min(rangingDoses, 10f) / 10f;
        obs[22] = Math.min(superCombatDoses, 10f) / 10f;
        obs[23] = Math.min(magicDoses, 10f) / 10f;
        obs[24] = Math.min(sarasBrews, 10f) / 10f;
        obs[25] = Math.min(superRestores, 10f) / 10f;
        obs[26] = Math.min(foodCount, 10f) / 10f;
        // obs[27] is prayer potions - included in super restores
    }

    private static int countPotionDoses(Item[] items, int... potionIds)
    {
        int totalDoses = 0;
        for (Item item : items) {
            if (item == null) continue;
            int id = item.getId();
            for (int i = 0; i < potionIds.length; i++) {
                if (id == potionIds[i]) {
                    // Dose count is (4,3,2,1) based on position in pairs
                    int doses = 4 - (i % 4);
                    totalDoses += doses * item.getQuantity();
                    break;
                }
            }
        }
        return totalDoses;
    }

    private static int countFood(Item[] items)
    {
        int count = 0;
        for (Item item : items) {
            if (item == null) continue;
            int id = item.getId();
            // Common PvP food - using actual item IDs from Elvarg source
            if (id == ItemID.TBWT_COOKED_KARAMBWAN || id == ItemID.ANGLERFISH ||
                id == ItemID.DARK_CRAB || id == ItemID.MANTARAY ||
                id == ItemID.SEATURTLE || id == ItemID.SHARK ||
                id == ItemID.PINEAPPLE_PIZZA || id == ItemID.POTATO_MUSHROOM_ONION) {
                count += item.getQuantity();
            }
        }
        return count;
    }

    private static void encodeFreezeStatus(TimerManagerAdapter timers, float[] obs)
    {
        // Player freeze status
        int playerFreezeTicks = timers.getRemainingTicks(true, TimerKey.FREEZE);
        obs[29] = playerFreezeTicks > 0 ? 1f : 0f; // isPlayerFrozen
        obs[30] = Math.min(playerFreezeTicks, 20f) / 20f; // playerFrozenTicksRemaining (normalized)

        // Target freeze status (assuming we are tracking it separately)
        int targetFreezeTicks = timers.getRemainingTicks(false, TimerKey.FREEZE);
        obs[31] = targetFreezeTicks > 0 ? 1f : 0f; // isTargetFrozen
        obs[32] = Math.min(targetFreezeTicks, 20f) / 20f; // targetFrozenTicksRemaining (normalized)
    }

    private static void encodeTimingInfo(TimerManagerAdapter timers, float[] obs)
    {
        // Combat timer
        int combatTicks = timers.getRemainingTicks(true, TimerKey.COMBAT_ATTACK);
        obs[39] = Math.min(combatTicks, 10f) / 10f; // ticksToNextAttack

        // Food timer
        int foodTicks = timers.getRemainingTicks(true, TimerKey.FOOD);
        obs[40] = Math.min(foodTicks, 3f) / 3f; // ticksToNextEat

        // Potion timer
        int potionTicks = timers.getRemainingTicks(true, TimerKey.POTION);
        obs[41] = Math.min(potionTicks, 3f) / 3f; // ticksToNextPot

        // Karambwan timer
        int karambwanTicks = timers.getRemainingTicks(true, TimerKey.KARAMBWAN);
        obs[42] = Math.min(karambwanTicks, 3f) / 3f; // ticksToNextKarambwan

        // Target combat timer (if tracked)
        int targetCombatTicks = timers.getRemainingTicks(false, TimerKey.COMBAT_ATTACK);
        obs[43] = Math.min(targetCombatTicks, 10f) / 10f; // targetTicksToNextAttack

        // Other timers default to 0
    }

    private static void encodeCombatState(DamageTrackerAdapter damage, float[] obs)
    {
        if (damage == null) return;

        // Last hit damage and confidence
        obs[46] = damage.getLastHitDamage() / 50f; // lastHitDamage (normalized to max 50)
        obs[47] = (float) damage.getLastHitConfidence(); // lastHitSuccessChance

        // Other combat state fields would need additional tracking
        // Setting basic values based on available data
        obs[51] = damage.wasLastHitSuccessful() ? 1f : 0f; // lastHitSuccess
    }

    private static void encodeGameModes(Client client, float[] obs)
    {
        // LMS detection using IN_LMS varbit
        int inLms = client.getVarbitValue(Varbits.IN_LMS);
        obs[160] = (inLms > 0) ? 1f : 0f; // isInLms

        // PvP Arena detection - not directly available in RuneLite API
        // Would need to check for PvP Arena-specific conditions
        obs[161] = 0f; // isInPvpArena - requires specific detection logic
    }

    private static void encodeVengeanceStatus(Client client, Player localPlayer, Player targetPlayer,
                                             TimerManagerAdapter timers, float[] obs)
    {
        // Check if we have vengeance active (graphic 726)
        boolean hasVeng = localPlayer != null && localPlayer.getGraphic() == 726;
        obs[162] = hasVeng ? 1f : 0f; // hasVengeance

        // Check if target has vengeance
        boolean targetHasVeng = targetPlayer != null && targetPlayer.getGraphic() == 726;
        obs[163] = targetHasVeng ? 1f : 0f; // targetHasVengeance

        // Vengeance cooldown
        int vengCooldown = timers.getRemainingTicks(true, TimerKey.VENGEANCE_COOLDOWN);
        obs[164] = Math.min(vengCooldown, 30f) / 30f; // vengeanceCooldown (normalized)

        // Target vengeance cooldown (if tracked)
        int targetVengCooldown = timers.getRemainingTicks(false, TimerKey.VENGEANCE_COOLDOWN);
        obs[165] = Math.min(targetVengCooldown, 30f) / 30f; // targetVengeanceCooldown

        // Can cast vengeance (based on cooldown and magic level)
        boolean canCastVeng = vengCooldown == 0 && client.getRealSkillLevel(Skill.MAGIC) >= 94;
        obs[166] = canCastVeng ? 1f : 0f; // canCastVengeance

        // Target can cast (estimated)
        obs[167] = targetVengCooldown == 0 ? 1f : 0f; // targetCanCastVengeance
    }
}



