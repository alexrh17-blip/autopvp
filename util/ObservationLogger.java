package net.runelite.client.plugins.autopvp.util;

import java.util.List;
import java.util.Locale;
import net.runelite.client.plugins.autopvp.core.NhContract;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive observation logger that labels all 176 observation values
 * to provide visibility into what the AI perceives.
 */
@Slf4j
public class ObservationLogger {

    private static final String[] OBSERVATION_LABELS = {
        // Combat equipment (0-4)
        "isMeleeEquipped",
        "isRangedEquipped",
        "isMageEquipped",
        "isMeleeSpecialWeaponEquipped",
        "specialPercentage",

        // Player prayers (5-9)
        "isProtectMeleeActive",
        "isProtectRangedActive",
        "isProtectMagicActive",
        "isSmiteActive",
        "isRedemptionActive",

        // Health (10-11)
        "playerHealthPercent",
        "targetHealthPercent",

        // Target equipment (12-15)
        "isTargetMeleeEquipped",
        "isTargetRangedEquipped",
        "isTargetMageEquipped",
        "isTargetMeleeSpecialWeaponEquipped",

        // Target prayers (16-21)
        "isTargetProtectMeleeActive",
        "isTargetProtectRangedActive",
        "isTargetProtectMagicActive",
        "isTargetSmiteActive",
        "isTargetRedemptionActive",
        "targetSpecialPercentage",

        // Consumables (22-27)
        "remainingRangingPotionDoses",
        "remainingSuperCombatDoses",
        "remainingSuperRestoreDoses",
        "remainingSaradominBrewDoses",
        "foodCount",
        "karamCount",

        // Prayer and status (28-32)
        "prayerPointScale",
        "playerFrozenTicks",
        "targetFrozenTicks",
        "playerFrozenImmunityTicks",
        "targetFrozenImmunityTicks",

        // Combat state (33-44)
        "isInMeleeRange",
        "relativeStrength",
        "relativeAttack",
        "relativeDefence",
        "relativeRanged",
        "relativeMagic",
        "ticksUntilNextAttack",
        "ticksUntilNextFood",
        "ticksUntilNextPotionCycle",
        "ticksUntilNextKaramCycle",
        "foodAttackDelay",
        "ticksUntilNextTargetAttack",

        // Attack timing (45-48)
        "ticksUntilNextTargetPotion",
        "pendingDamageOnTargetScale",
        "ticksUntilHitOnTarget",
        "ticksUntilHitOnPlayer",

        // Recent actions (49-54)
        "didPlayerJustAttack",
        "didTargetJustAttack",
        "attackCalculatedDamageScale",
        "hitsplatsLandedOnAgentScale",
        "hitsplatsLandedOnTargetScale",
        "isAttackingTarget",

        // Movement (55-58)
        "isMoving",
        "isTargetMoving",
        "havePidOverTarget",
        "canCastIceBarrage",

        // Spell availability (59-62)
        "canCastBloodBarrage",
        "destinationDistanceToTarget",
        "distanceToDestination",
        "distanceToTarget",

        // Prayer effectiveness (63-64)
        "didPlayerPrayCorrectly",
        "didTargetPrayCorrectly",

        // Damage tracking (65-78)
        "damageDealtScale",
        "targetHitConfidence",
        "targetHitMeleeCount",
        "targetHitMageCount",
        "targetHitRangeCount",
        "playerHitMeleeCount",
        "playerHitMageCount",
        "playerHitRangeCount",
        "targetHitCorrectCount",
        "targetPrayConfidence",
        "targetPrayMageCount",
        "targetPrayRangeCount",
        "targetPrayMeleeCount",
        "playerPrayMageCount",

        // Prayer tracking (79-92)
        "playerPrayRangeCount",
        "playerPrayMeleeCount",
        "targetPrayCorrectCount",
        "recentTargetHitMeleeCount",
        "recentTargetHitMageCount",
        "recentTargetHitRangeCount",
        "recentPlayerHitMeleeCount",
        "recentPlayerHitMageCount",
        "recentPlayerHitRangeCount",
        "recentTargetHitCorrectCount",
        "recentTargetPrayMageCount",
        "recentTargetPrayRangeCount",
        "recentTargetPrayMeleeCount",
        "recentPlayerPrayMageCount",

        // Recent prayer tracking (93-100)
        "recentPlayerPrayRangeCount",
        "recentPlayerPrayMeleeCount",
        "recentTargetPrayCorrectCount",
        "absoluteAttack",
        "absoluteStrength",
        "absoluteDefence",
        "absoluteRanged",
        "absoluteMagic",

        // Skills (101-106)
        "absolutePrayer",
        "absoluteHitpoints",
        "isEnchantedDragonBolts",
        "isEnchantedOpalBolts",
        "isEnchantedDiamondBolts",
        "isMageSpecWeaponInLoadout",

        // Special weapons (107-118)
        "isRangeSpecWeaponInLoadout",
        "isNightmareStaff",
        "isZaryteCrossbow",
        "isBallista",
        "isMorrigansJavelins",
        "isDragonKnives",
        "isDarkBow",
        "isMeleeSpecDclaws",
        "isMeleeSpecDds",
        "isMeleeSpecAgs",
        "isMeleeSpecVls",
        "isMeleeSpecStatHammer",

        // More weapons (119-125)
        "isMeleeSpecAncientGodsword",
        "isMeleeSpecGraniteMaul",
        "isBloodFury",
        "isDharoksSet",
        "isZurielStaff",
        "magicGearAccuracy",
        "magicGearStrength",

        // Ranged gear stats (126-131)
        "rangedGearAccuracy",
        "rangedGearStrength",
        "rangedGearAttackSpeed",
        "rangedGearAttackRange",
        "meleeGearAccuracy",
        "meleeGearStrength",

        // Melee gear stats (132-140)
        "meleeGearAttackSpeed",
        "magicGearRangedDefence",
        "magicGearMageDefence",
        "magicGearMeleeDefence",
        "rangedGearRangedDefence",
        "rangedGearMageDefence",
        "rangedGearMeleeDefence",
        "meleeGearRangedDefence",
        "meleeGearMageDefence",

        // Defense stats (141-158)
        "meleeGearMeleeDefence",
        "targetCurrentGearRangedDefence",
        "targetCurrentGearMageDefence",
        "targetCurrentGearMeleeDefence",
        "targetLastMagicGearAccuracy",
        "targetLastMagicGearStrength",
        "targetLastRangedGearAccuracy",
        "targetLastRangedGearStrength",
        "targetLastMeleeGearAccuracy",
        "targetLastMeleeGearStrength",
        "targetLastMagicGearRangedDefence",
        "targetLastMagicGearMageDefence",
        "targetLastMagicGearMeleeDefence",
        "targetLastRangedGearRangedDefence",
        "targetLastRangedGearMageDefence",
        "targetLastRangedGearMeleeDefence",
        "targetLastMeleeGearRangedDefence",
        "targetLastMeleeGearMageDefence",

        // Target defense and vengeance (159-167)
        "targetLastMeleeGearMeleeDefence",
        "isLms",
        "isPvpArena",
        "isVengActive",
        "isTargetVengActive",
        "isPlayerLunarSpellbook",
        "isTargetLunarSpellbook",
        "playerVengCooldownTicks",
        "targetVengCooldownTicks",

        // Attack availability (168-175)
        "isBloodAttackAvailable",
        "isIceAttackAvailable",
        "isMageSpecAttackAvailable",
        "isRangedAttackAvailable",
        "isRangedSpecAttackAvailable",
        "isMeleeAttackAvailable",
        "isMeleeSpecAttackAvailable",
        "isAnglerfish"
    };

    private static final ObservationSection[] OBSERVATION_SECTIONS = {
        new ObservationSection("Player Equipment & Special", 0, 4),
        new ObservationSection("Player Prayers", 5, 9),
        new ObservationSection("Health (Player/Target)", 10, 11),
        new ObservationSection("Target Equipment & Special", 12, 15),
        new ObservationSection("Target Prayers & Special", 16, 21),
        new ObservationSection("Consumables & Resources", 22, 27),
        new ObservationSection("Prayer & Freeze Status", 28, 32),
        new ObservationSection("Relative Skill Levels", 33, 38),
        new ObservationSection("Player/Target Timers", 39, 48),
        new ObservationSection("Recent Actions & Movement", 49, 58),
        new ObservationSection("Spell & Positioning", 59, 64),
        new ObservationSection("Damage & Prayer Tracking", 65, 100),
        new ObservationSection("Absolute Levels & Gear Flags", 101, 118),
        new ObservationSection("Gear Offensive Stats", 119, 140),
        new ObservationSection("Target Gear & Defence", 141, 167),
        new ObservationSection("Attack Availability", 168, 175)
    };

    private static final class ObservationSection
    {
        private final String title;
        private final int start;
        private final int end;

        private ObservationSection(String title, int start, int end)
        {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    public static void logInfoSnapshot(List<Number> observations, String header)
    {
        if (observations == null || observations.size() != NhContract.OBS_SIZE)
        {
            return;
        }

        String title = header != null && !header.isEmpty()
            ? header
            : "[OBSERVATIONS] Full Snapshot";

        StringBuilder sb = new StringBuilder(2048);
        sb.append(title).append(System.lineSeparator());

        for (ObservationSection section : OBSERVATION_SECTIONS)
        {
            sb.append("  ").append(section.title).append(": ");
            appendObservationRange(sb, observations, section.start, section.end);
            sb.append(System.lineSeparator());
        }

        log.info(sb.toString());
    }

    private static void appendObservationRange(StringBuilder sb, List<Number> observations, int start, int end)
    {
        if (observations == null || observations.isEmpty())
        {
            return;
        }

        int cappedEnd = Math.min(end, observations.size() - 1);
        for (int i = start; i <= cappedEnd; i++)
        {
            if (i > start)
            {
                sb.append(", ");
            }

            sb.append('[').append(i).append("] ").append(labelFor(i)).append('=');
            sb.append(formatObservationValue(observations.get(i)));
        }
    }

    private static String formatObservationValue(Number value)
    {
        if (value == null)
        {
            return "0";
        }

        double dbl = value.doubleValue();
        if (Math.abs(dbl - Math.round(dbl)) < 1e-6d)
        {
            return Long.toString(Math.round(dbl));
        }

        return String.format(Locale.ROOT, "%.3f", dbl);
    }

    public static String labelFor(int index) {
        if (index < 0 || index >= OBSERVATION_LABELS.length) {
            return "obs[" + index + "]";
        }
        return OBSERVATION_LABELS[index];
    }

    public static void logObservations(List<Number> observations) {
        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug("[OBSERVATIONS] ========== Full Observation State ==========");

        // Log key observations first
        logKeyObservations(observations);

        // Log all observations with labels if trace enabled
        if (log.isTraceEnabled()) {
            logAllObservations(observations);
        }

        log.debug("[OBSERVATIONS] ==========================================");
    }

    private static void logKeyObservations(List<Number> observations) {
        log.debug("[OBSERVATIONS] Key Values:");

        // Player state
        log.debug("[OBSERVATIONS]   Player Health: {}%", getObs(observations, 10));
        log.debug("[OBSERVATIONS]   Target Health: {}%", getObs(observations, 11));
        log.debug("[OBSERVATIONS]   Special Attack: {}%", getObs(observations, 4));
        log.debug("[OBSERVATIONS]   Prayer Points: {}", getObs(observations, 28));

        // Equipment
        log.debug("[OBSERVATIONS]   Equipment: Melee={}, Ranged={}, Mage={}",
            getObs(observations, 0), getObs(observations, 1), getObs(observations, 2));

        // Prayers
        log.debug("[OBSERVATIONS]   Player Prayers: ProtMelee={}, ProtRange={}, ProtMage={}, Smite={}, Redemption={}",
            getObs(observations, 5), getObs(observations, 6), getObs(observations, 7),
            getObs(observations, 8), getObs(observations, 9));

        // Target info
        if (getObs(observations, 11).doubleValue() > 0) {
            log.debug("[OBSERVATIONS]   Target Equipment: Melee={}, Ranged={}, Mage={}",
                getObs(observations, 12), getObs(observations, 13), getObs(observations, 14));
            log.debug("[OBSERVATIONS]   Target Prayers: ProtMelee={}, ProtRange={}, ProtMage={}",
                getObs(observations, 16), getObs(observations, 17), getObs(observations, 18));
        } else {
            log.debug("[OBSERVATIONS]   No target detected (health=0)");
        }

        // Consumables
        log.debug("[OBSERVATIONS]   Food: {}, Karambwan: {}",
            getObs(observations, 26), getObs(observations, 27));
        log.debug("[OBSERVATIONS]   Potions: Range={}, Combat={}, Restore={}, Brew={}",
            getObs(observations, 22), getObs(observations, 23),
            getObs(observations, 24), getObs(observations, 25));

        // Combat state
        log.debug("[OBSERVATIONS]   In Melee Range: {}", getObs(observations, 33));
        log.debug("[OBSERVATIONS]   Distance to Target: {}", getObs(observations, 62));
        log.debug("[OBSERVATIONS]   Is Attacking: {}", getObs(observations, 54));
    }

    private static void logAllObservations(List<Number> observations) {
        log.trace("[OBSERVATIONS] All 176 Values:");
        for (int i = 0; i < Math.min(observations.size(), OBSERVATION_LABELS.length); i++) {
            Number value = observations.get(i);
            log.trace("[OBSERVATIONS]   [{}] {} = {}", i, OBSERVATION_LABELS[i], value);
        }
    }

    private static Number getObs(List<Number> observations, int index) {
        if (index < observations.size()) {
            return observations.get(index);
        }
        return 0;
    }

    public static void logDecision(List<Integer> action, List<List<Boolean>> actionMasks) {
        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug("[DECISION] AI Action Decision:");
        log.debug("[DECISION]   Attack: {} (0=none, 1=mage, 2=ranged, 3=melee)", action.get(0));
        log.debug("[DECISION]   Melee: {} (0=none, 1=normal, 2=spec)", action.get(1));
        log.debug("[DECISION]   Ranged: {} (0=none, 1=normal, 2=spec)", action.get(2));
        log.debug("[DECISION]   Mage: {} (0=none, 1=ice, 2=blood, 3=spec)", action.get(3));
        log.debug("[DECISION]   Potion: {} (0=none, 1=brew, 2=restore, 3=combat, 4=ranging)", action.get(4));
        log.debug("[DECISION]   Food: {} (0=no, 1=yes)", action.get(5));
        log.debug("[DECISION]   Karambwan: {} (0=no, 1=yes)", action.get(6));
        log.debug("[DECISION]   Vengeance: {} (0=no, 1=yes)", action.get(7));
        log.debug("[DECISION]   Gear: {} (0=no, 1=tank)", action.get(8));
        log.debug("[DECISION]   Movement: {} (0=none, 1=adjacent, 2=under, 3=farcast, 4=diagonal)", action.get(9));
        log.debug("[DECISION]   Distance: {} (farcast distance)", action.get(10));
        log.debug("[DECISION]   Prayer: {} (0=none, 1=mage, 2=ranged, 3=melee, 4=smite, 5=redemption)", action.get(11));
    }

    public static void logActionExecution(String actionType, boolean success, String details) {
        if (success) {
            log.info("[ACTION-EXEC] {} executed successfully: {}", actionType, details);
        } else {
            log.warn("[ACTION-EXEC] {} failed: {}", actionType, details);
        }
    }
}
