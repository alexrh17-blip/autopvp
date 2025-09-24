package net.runelite.client.plugins.autopvp.core;

/**
 * NH environment contract from naton1's RSPS implementation.
 * Defines the exact observation and action space structure.
 */
public final class NhContract
{
    private NhContract() {}

    // Observation space size
    public static final int OBS_SIZE = 176;

    // Observation IDs in exact order from NhEnvironment.getObs()
    public static final String[] OBS_IDS = {
        // Combat style & equipment (0-4)
        "isMeleeEquipped",                     // 0
        "isRangedEquipped",                    // 1
        "isMageEquipped",                      // 2
        "isMeleeSpecialWeaponEquipped",        // 3
        "specialPercentage",                   // 4

        // Player prayers (5-9)
        "isProtectMeleeActive",                // 5
        "isProtectRangedActive",               // 6
        "isProtectMagicActive",                // 7
        "isSmiteActive",                       // 8
        "isRedemptionActive",                  // 9

        // Health (10-11)
        "healthPercent",                       // 10
        "targetHealthPercent",                 // 11

        // Target equipment (12-15)
        "isTargetMeleeEquipped",               // 12
        "isTargetRangedEquipped",              // 13
        "isTargetMageEquipped",                // 14
        "isTargetMeleeSpecialWeaponEquipped",  // 15

        // Target prayers (16-20)
        "isTargetProtectMeleeActive",          // 16
        "isTargetProtectRangedActive",         // 17
        "isTargetProtectMagicActive",          // 18
        "isTargetSmiteActive",                 // 19
        "isTargetRedemptionActive",            // 20

        // Resources (21-27)
        "targetSpecialPercentage",             // 21
        "remainingRangingPotionDoses",         // 22
        "remainingSuperCombatDoses",           // 23
        "remainingSuperRestoreDoses",          // 24
        "remainingSaradominBrewDoses",         // 25
        "foodCount",                           // 26
        "karamCount",                          // 27

        // Prayer and status (28-32)
        "prayerPointScale",                    // 28
        "playerFrozenTicks",                   // 29
        "targetFrozenTicks",                   // 30
        "playerFrozenImmunityTicks",           // 31
        "targetFrozenImmunityTicks",           // 32

        // Range check (33)
        "isInMeleeRange",                      // 33

        // Skill levels (34-38)
        "relativeLevelStrength",               // 34
        "relativeLevelAttack",                 // 35
        "relativeLevelDefence",                // 36
        "relativeLevelRanged",                 // 37
        "relativeLevelMagic",                  // 38

        // Timing (39-46)
        "ticksUntilNextAttack",                // 39
        "ticksUntilNextFood",                  // 40
        "ticksUntilNextPotionCycle",           // 41
        "ticksUntilNextKaramCycle",            // 42
        "foodAttackDelay",                     // 43
        "ticksUntilNextTargetAttack",          // 44
        "ticksUntilNextTargetPotion",          // 45

        // Combat state (46-52)
        "pendingDamageOnTargetScale",          // 46
        "ticksUntilHitOnTarget",               // 47
        "ticksUntilHitOnPlayer",               // 48
        "didPlayerJustAttack",                 // 49
        "didTargetJustAttack",                 // 50
        "attackCalculatedDamageScale",         // 51
        "hitsplatsLandedOnAgentScale",         // 52
        "hitsplatsLandedOnTargetScale",        // 53

        // Movement state (54-57)
        "isAttackingTarget",                   // 54
        "isMoving",                            // 55
        "isTargetMoving",                      // 56
        "isHavePidOverTarget",                 // 57

        // Spell availability (58-59)
        "canCastIceBarrage",                   // 58
        "canCastBloodBarrage",                 // 59

        // Distance metrics (60-62)
        "destinationDistanceToTarget",         // 60
        "distanceToDestination",               // 61
        "distanceToTarget",                    // 62

        // Prayer tracking (63-64)
        "didPlayerPrayCorrectly",              // 63
        "didTargetPrayCorrectly",              // 64

        // Damage and confidence (65-66)
        "damageDealtScale",                    // 65
        "targetHitConfidence",                 // 66

        // Hit counts (67-72)
        "targetHitMeleeCount",                 // 67
        "targetHitMageCount",                  // 68
        "targetHitRangeCount",                 // 69
        "playerHitMeleeCount",                 // 70
        "playerHitMageCount",                  // 71
        "playerHitRangeCount",                 // 72

        // Prayer counts (73-79)
        "targetHitCorrectCount",               // 73
        "targetPrayConfidence",                // 74
        "targetPrayMageCount",                 // 75
        "targetPrayRangeCount",                // 76
        "targetPrayMeleeCount",                // 77
        "playerPrayMageCount",                 // 78
        "playerPrayRangeCount",                // 79
        "playerPrayMeleeCount",                // 80
        "targetPrayCorrectCount",              // 81

        // Recent hit counts (82-87)
        "recentTargetHitMeleeCount",           // 82
        "recentTargetHitMageCount",            // 83
        "recentTargetHitRangeCount",           // 84
        "recentPlayerHitMeleeCount",           // 85
        "recentPlayerHitMageCount",            // 86
        "recentPlayerHitRangeCount",           // 87

        // Recent prayer counts (88-95)
        "recentTargetHitCorrectCount",         // 88
        "recentTargetPrayMageCount",           // 89
        "recentTargetPrayRangeCount",          // 90
        "recentTargetPrayMeleeCount",          // 91
        "recentPlayerPrayMageCount",           // 92
        "recentPlayerPrayRangeCount",          // 93
        "recentPlayerPrayMeleeCount",          // 94
        "recentTargetPrayCorrectCount",        // 95

        // Absolute levels (96-102)
        "absoluteLevelAttack",                 // 96
        "absoluteLevelStrength",               // 97
        "absoluteLevelDefence",                // 98
        "absoluteLevelRanged",                 // 99
        "absoluteLevelMagic",                  // 100
        "absoluteLevelPrayer",                 // 101
        "absoluteLevelHitpoints",              // 102

        // Bolt types (103-105)
        "isEnchantedDragonBolts",              // 103
        "isEnchantedOpalBolts",                // 104
        "isEnchantedDiamondBolts",             // 105

        // Spec weapon types (106-117)
        "isMageSpecWeaponInLoadout",           // 106
        "isRangeSpecWeaponInLoadout",          // 107
        "isNightmareStaff",                    // 108
        "isZaryteCrossbow",                    // 109
        "isBallista",                          // 110
        "isMorrigansJavelins",                 // 111
        "isDragonKnives",                      // 112
        "isDarkBow",                           // 113
        "isMeleeSpecDclaws",                   // 114
        "isMeleeSpecDds",                      // 115
        "isMeleeSpecAgs",                      // 116
        "isMeleeSpecVls",                      // 117
        "isMeleeSpecStatHammer",               // 118
        "isMeleeSpecAncientGodsword",          // 119
        "isMeleeSpecGraniteMaul",              // 120

        // Special equipment (121-123)
        "isBloodFury",                         // 121
        "isDharoksSet",                        // 122
        "isZurielStaff",                       // 123

        // Magic gear stats (124-129)
        "magicGearAccuracy",                   // 124
        "magicGearStrength",                   // 125
        "rangedGearAccuracy",                  // 126
        "rangedGearStrength",                  // 127
        "rangedGearAttackSpeed",               // 128
        "rangedGearAttackRange",               // 129

        // Melee gear stats (130-132)
        "meleeGearAccuracy",                   // 130
        "meleeGearStrength",                   // 131
        "meleeGearAttackSpeed",                // 132

        // Defence bonuses (133-141)
        "magicGearRangedDefence",              // 133
        "magicGearMageDefence",                // 134
        "magicGearMeleeDefence",               // 135
        "rangedGearRangedDefence",             // 136
        "rangedGearMageDefence",               // 137
        "rangedGearMeleeDefence",              // 138
        "meleeGearRangedDefence",              // 139
        "meleeGearMageDefence",                // 140
        "meleeGearMeleeDefence",               // 141

        // Target gear stats (142-159)
        "targetCurrentGearRangedDefence",      // 142
        "targetCurrentGearMageDefence",        // 143
        "targetCurrentGearMeleeDefence",       // 144
        "targetLastMagicGearAccuracy",         // 145
        "targetLastMagicGearStrength",         // 146
        "targetLastRangedGearAccuracy",        // 147
        "targetLastRangedGearStrength",        // 148
        "targetLastMeleeGearAccuracy",         // 149
        "targetLastMeleeGearStrength",         // 150
        "targetLastMagicGearRangedDefence",    // 151
        "targetLastMagicGearMageDefence",      // 152
        "targetLastMagicGearMeleeDefence",     // 153
        "targetLastRangedGearRangedDefence",   // 154
        "targetLastRangedGearMageDefence",     // 155
        "targetLastRangedGearMeleeDefence",    // 156
        "targetLastMeleeGearRangedDefence",    // 157
        "targetLastMeleeGearMageDefence",      // 158
        "targetLastMeleeGearMeleeDefence",     // 159

        // Game modes (160-161)
        "isLms",                               // 160
        "isPvpArena",                          // 161

        // Vengeance (162-165)
        "isVengActive",                        // 162
        "isTargetVengActive",                  // 163
        "isPlayerLunarSpellbook",              // 164
        "isTargetLunarSpellbook",              // 165
        "playerVengCooldownTicks",             // 166
        "targetVengCooldownTicks",             // 167

        // Attack availability (168-175)
        "isBloodAttackAvailable",              // 168
        "isIceAttackAvailable",                // 169
        "isMageSpecAttackAvailable",           // 170
        "isRangedAttackAvailable",             // 171
        "isRangedSpecAttackAvailable",         // 172
        "isMeleeAttackAvailable",              // 173
        "isMeleeSpecAttackAvailable",          // 174
        "isAnglerfish"                         // 175
    };

    // Action space structure from getActionMasks()
    public static final int[] ACTION_HEAD_SIZES = {
        4,  // Head 0: Attack type [no-op, mage, ranged, melee]
        3,  // Head 1: Melee attack type [none, basic, spec]
        3,  // Head 2: Ranged attack type [none, basic, spec]
        4,  // Head 3: Mage attack type [none, ice, blood, spec]
        5,  // Head 4: Potion [none, brew, restore, combat, ranging]
        2,  // Head 5: Primary food [none, eat]
        2,  // Head 6: Karambwan [none, eat]
        2,  // Head 7: Vengeance [none, cast]
        2,  // Head 8: Gear switch [none, tank]
        5,  // Head 9: Movement [none, adjacent, under, farcast, diagonal]
        7,  // Head 10: Farcast distance [none, 2, 3, 4, 5, 6, 7]
        6   // Head 11: Overhead prayer [none, mage, range, melee, smite, redemption]
    };

    // Cumulative offsets for each action head
    public static final int[] ACTION_HEAD_OFFSETS = {
        0,   // Head 0 starts at 0
        4,   // Head 1 starts at 4
        7,   // Head 2 starts at 7
        10,  // Head 3 starts at 10
        14,  // Head 4 starts at 14
        19,  // Head 5 starts at 19
        21,  // Head 6 starts at 21
        23,  // Head 7 starts at 23
        25,  // Head 8 starts at 25
        27,  // Head 9 starts at 27
        32,  // Head 10 starts at 32
        39   // Head 11 starts at 39
    };

    // Total flattened action size
    public static final int ACTION_FLAT_SIZE = 45;

    /**
     * Get the starting offset for a given action head.
     */
    public static int headOffset(int headIdx)
    {
        if (headIdx < 0 || headIdx >= ACTION_HEAD_OFFSETS.length) {
            throw new IllegalArgumentException("Invalid head index: " + headIdx);
        }
        return ACTION_HEAD_OFFSETS[headIdx];
    }

    /**
     * Get the observation index by name.
     */
    public static int getObsIndex(String name)
    {
        for (int i = 0; i < OBS_IDS.length; i++) {
            if (OBS_IDS[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}