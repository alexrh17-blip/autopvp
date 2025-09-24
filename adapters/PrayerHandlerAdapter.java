package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.PrayerHandler;
import com.elvarg.game.content.combat.CombatType;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.entity.impl.player.Player;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.EnumMap;
import java.util.Map;

/**
 * Adapter that bridges RuneLite's prayer system to Elvarg's PrayerHandler.
 * Tracks prayer states and provides compatibility with Elvarg's prayer constants.
 */
@Slf4j
public class PrayerHandlerAdapter {

    private final Client client;
    private final EventBus eventBus;

    // Map RuneLite prayers to Elvarg prayer IDs
    private static final Map<Prayer, Integer> PRAYER_MAPPING = new EnumMap<>(Prayer.class);
    // Canonical prayer drain rates from Elvarg PrayerData enum
    // Citation: C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/content/PrayerHandler.java:530-558
    private static final double[] PRAYER_DRAIN_RATES = {
        5.0,   // 0: THICK_SKIN
        5.0,   // 1: BURST_OF_STRENGTH
        5.0,   // 2: CLARITY_OF_THOUGHT
        5.0,   // 3: SHARP_EYE
        5.0,   // 4: MYSTIC_WILL
        10.0,  // 5: ROCK_SKIN
        10.0,  // 6: SUPERHUMAN_STRENGTH
        10.0,  // 7: IMPROVED_REFLEXES
        2.3,   // 8: RAPID_RESTORE
        3.33,  // 9: RAPID_HEAL
        3.0,   // 10: PROTECT_ITEM
        10.0,  // 11: HAWK_EYE
        10.0,  // 12: MYSTIC_LORE
        20.0,  // 13: STEEL_SKIN
        20.0,  // 14: ULTIMATE_STRENGTH
        20.0,  // 15: INCREDIBLE_REFLEXES
        20.0,  // 16: PROTECT_FROM_MAGIC
        20.0,  // 17: PROTECT_FROM_MISSILES
        20.0,  // 18: PROTECT_FROM_MELEE
        20.0,  // 19: EAGLE_EYE
        20.0,  // 20: MYSTIC_MIGHT
        5.0,   // 21: RETRIBUTION
        10.0,  // 22: REDEMPTION
        30.0,  // 23: SMITE
        3.0,   // 24: PRESERVE
        40.0,  // 25: CHIVALRY
        40.0,  // 26: PIETY
        40.0,  // 27: RIGOUR
        40.0   // 28: AUGURY
    };
    static {
        // Initialize prayer mappings based on PrayerHandler constants
        PRAYER_MAPPING.put(Prayer.THICK_SKIN, PrayerHandler.THICK_SKIN);
        PRAYER_MAPPING.put(Prayer.BURST_OF_STRENGTH, PrayerHandler.BURST_OF_STRENGTH);
        PRAYER_MAPPING.put(Prayer.CLARITY_OF_THOUGHT, PrayerHandler.CLARITY_OF_THOUGHT);
        PRAYER_MAPPING.put(Prayer.SHARP_EYE, PrayerHandler.SHARP_EYE);
        PRAYER_MAPPING.put(Prayer.MYSTIC_WILL, PrayerHandler.MYSTIC_WILL);
        PRAYER_MAPPING.put(Prayer.ROCK_SKIN, PrayerHandler.ROCK_SKIN);
        PRAYER_MAPPING.put(Prayer.SUPERHUMAN_STRENGTH, PrayerHandler.SUPERHUMAN_STRENGTH);
        PRAYER_MAPPING.put(Prayer.IMPROVED_REFLEXES, PrayerHandler.IMPROVED_REFLEXES);
        PRAYER_MAPPING.put(Prayer.RAPID_RESTORE, PrayerHandler.RAPID_RESTORE);
        PRAYER_MAPPING.put(Prayer.RAPID_HEAL, PrayerHandler.RAPID_HEAL);
        PRAYER_MAPPING.put(Prayer.PROTECT_ITEM, PrayerHandler.PROTECT_ITEM);
        PRAYER_MAPPING.put(Prayer.HAWK_EYE, PrayerHandler.HAWK_EYE);
        PRAYER_MAPPING.put(Prayer.MYSTIC_LORE, PrayerHandler.MYSTIC_LORE);
        PRAYER_MAPPING.put(Prayer.STEEL_SKIN, PrayerHandler.STEEL_SKIN);
        PRAYER_MAPPING.put(Prayer.ULTIMATE_STRENGTH, PrayerHandler.ULTIMATE_STRENGTH);
        PRAYER_MAPPING.put(Prayer.INCREDIBLE_REFLEXES, PrayerHandler.INCREDIBLE_REFLEXES);
        PRAYER_MAPPING.put(Prayer.PROTECT_FROM_MAGIC, PrayerHandler.PROTECT_FROM_MAGIC);
        PRAYER_MAPPING.put(Prayer.PROTECT_FROM_MISSILES, PrayerHandler.PROTECT_FROM_MISSILES);
        PRAYER_MAPPING.put(Prayer.PROTECT_FROM_MELEE, PrayerHandler.PROTECT_FROM_MELEE);
        PRAYER_MAPPING.put(Prayer.EAGLE_EYE, PrayerHandler.EAGLE_EYE);
        PRAYER_MAPPING.put(Prayer.MYSTIC_MIGHT, PrayerHandler.MYSTIC_MIGHT);
        PRAYER_MAPPING.put(Prayer.RETRIBUTION, PrayerHandler.RETRIBUTION);
        PRAYER_MAPPING.put(Prayer.REDEMPTION, PrayerHandler.REDEMPTION);
        PRAYER_MAPPING.put(Prayer.SMITE, PrayerHandler.SMITE);
        PRAYER_MAPPING.put(Prayer.PRESERVE, PrayerHandler.PRESERVE);
        PRAYER_MAPPING.put(Prayer.CHIVALRY, PrayerHandler.CHIVALRY);
        PRAYER_MAPPING.put(Prayer.PIETY, PrayerHandler.PIETY);
        PRAYER_MAPPING.put(Prayer.RIGOUR, PrayerHandler.RIGOUR);
        PRAYER_MAPPING.put(Prayer.AUGURY, PrayerHandler.AUGURY);
    }

    // Track prayer states (matches Elvarg's array size)
    private final boolean[] prayerActive = new boolean[29];

    // Cache active overhead prayer - volatile for thread safety (RuneLite EventBus thread vs game thread)
    private volatile int activeOverheadPrayer = -1;

    public PrayerHandlerAdapter(Client client, EventBus eventBus) {
        this.client = client;
        this.eventBus = eventBus;
        eventBus.register(this);
        // Don't update prayers in constructor - wait for game to be loaded
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        // Update prayer states when varbits change
        updateAllPrayers();
    }

    /**
     * Updates all prayer states from client varbits.
     */
    private void updateAllPrayers() {
        for (Prayer prayer : Prayer.values()) {
            boolean active = client.isPrayerActive(prayer);
            Integer elvargId = PRAYER_MAPPING.get(prayer);

            if (elvargId != null) {
                boolean wasActive = prayerActive[elvargId];
                prayerActive[elvargId] = active;

                if (active != wasActive) {
                    log.debug("[PRAYER] {} {}: ElvargID={}",
                             prayer.name(), active ? "activated" : "deactivated", elvargId);

                    // Track overhead prayer changes
                    if (isOverheadPrayer(elvargId)) {
                        if (active) {
                            activeOverheadPrayer = elvargId;
                        } else if (activeOverheadPrayer == elvargId) {
                            activeOverheadPrayer = -1;
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if a prayer is activated.
     * Mirrors PrayerHandler.isActivated() behavior.
     */
    public boolean isActivated(int prayer) {
        if (prayer < 0 || prayer >= prayerActive.length) {
            return false;
        }
        return prayerActive[prayer];
    }

    /**
     * Check if a prayer is activated for a Mobile.
     * Mirrors PrayerHandler.isActivated(Mobile, int) behavior.
     */
    public boolean isActivated(Mobile mobile, int prayer) {
        // For local player, use our tracked state
        if (mobile instanceof Player) {
            return isActivated(prayer);
        }
        // For other entities, delegate to their prayer state
        return PrayerHandler.isActivated(mobile, prayer);
    }

    /**
     * Get the prayer array for Elvarg compatibility.
     * Returns a defensive copy to prevent external modification (thread safety).
     */
    public boolean[] getPrayerActive() {
        // Return defensive copy for thread safety
        return prayerActive.clone();
    }

    /**
     * Check if a prayer is an overhead prayer.
     */
    private boolean isOverheadPrayer(int prayer) {
        for (int overheadPrayer : PrayerHandler.OVERHEAD_PRAYERS) {
            if (overheadPrayer == prayer) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a prayer is a protection prayer.
     */
    public boolean isProtectionPrayer(int prayer) {
        for (int protectionPrayer : PrayerHandler.PROTECTION_PRAYERS) {
            if (protectionPrayer == prayer) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the active overhead prayer ID.
     */
    public int getActiveOverheadPrayer() {
        return activeOverheadPrayer;
    }

    /**
     * Check if player is using protection from a specific combat type.
     */
    public boolean isProtectingFrom(CombatType combatType) {
        int protectionPrayer = PrayerHandler.getProtectingPrayer(combatType);
        return isActivated(protectionPrayer);
    }

    /**
     * Check if Protect from Melee is active.
     */
    public boolean isProtectFromMeleeActive() {
        return isActivated(PrayerHandler.PROTECT_FROM_MELEE);
    }

    /**
     * Check if Protect from Magic is active.
     */
    public boolean isProtectFromMagicActive() {
        return isActivated(PrayerHandler.PROTECT_FROM_MAGIC);
    }

    /**
     * Check if Protect from Missiles is active.
     */
    public boolean isProtectFromMissilesActive() {
        return isActivated(PrayerHandler.PROTECT_FROM_MISSILES);
    }

    /**
     * Check if any offensive prayer is active (for observations).
     */
    public boolean hasOffensivePrayerActive() {
        // Check attack prayers
        for (int prayer : PrayerHandler.ATTACK_PRAYERS) {
            if (isActivated(prayer)) return true;
        }
        // Check strength prayers
        for (int prayer : PrayerHandler.STRENGTH_PRAYERS) {
            if (isActivated(prayer)) return true;
        }
        // Check ranged prayers
        for (int prayer : PrayerHandler.RANGED_PRAYERS) {
            if (isActivated(prayer)) return true;
        }
        // Check magic prayers
        for (int prayer : PrayerHandler.MAGIC_PRAYERS) {
            if (isActivated(prayer)) return true;
        }
        return false;
    }

    /**
     * Get the prayer drain modifier based on active prayers.
     * Used for calculating prayer point consumption.
     */
    public double getPrayerDrainModifier() {
        double totalDrain = 0.0;

        // Calculate drain using canonical PrayerData drain rates
        for (int i = 0; i < prayerActive.length && i < PRAYER_DRAIN_RATES.length; i++) {
            if (prayerActive[i]) {
                totalDrain += PRAYER_DRAIN_RATES[i];
            }
        }

        return totalDrain;
    }
    /**
     * Clean up when adapter is no longer needed.
     */
    public void shutdown() {
        eventBus.unregister(this);
    }
}