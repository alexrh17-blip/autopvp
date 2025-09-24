package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.hit.HitDamage;
import com.elvarg.game.content.combat.hit.HitDamageCache;
import com.elvarg.game.content.combat.hit.HitMask;
import com.elvarg.game.content.combat.hit.PendingHit;
import com.elvarg.util.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that tracks damage dealt and received for Elvarg's combat system.
 * Correlates RuneLite hitsplats with Elvarg's damage tracking.
 */
@Slf4j
public class DamageTrackerAdapter {

    private final Client client;
    private final EventBus eventBus;

    // Track damage dealt by the local player
    private final Map<Actor, HitDamageCache> damageDealt = new ConcurrentHashMap<>();

    // Track damage received by the local player
    private final Map<Actor, HitDamageCache> damageReceived = new ConcurrentHashMap<>();

    // Track recent hitsplats for correlation
    private final Map<Actor, Integer> lastHitsplatDamage = new ConcurrentHashMap<>();

    // Track recent hit confidence (for observations) - volatile for thread safety (RuneLite EventBus thread vs game thread)
    private volatile double lastHitConfidence = 0.0;
    private volatile int lastHitDamage = 0;
    private volatile boolean lastHitSuccessful = false;

    // Cache timeout in milliseconds (matches Elvarg's 60 second timeout)
    private static final long CACHE_TIMEOUT_MS = 60000;

    public DamageTrackerAdapter(Client client, EventBus eventBus) {
        this.client = client;
        this.eventBus = eventBus;
        eventBus.register(this);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor actor = event.getActor();
        Hitsplat hitsplat = event.getHitsplat();

        // Track damage value
        int damage = hitsplat.getAmount();

        // Determine if this is damage dealt or received
        if (actor == client.getLocalPlayer()) {
            // Damage received by us
            Actor source = client.getLocalPlayer().getInteracting();
            if (source != null) {
                trackDamageReceived(source, damage);
            }
        } else if (client.getLocalPlayer().getInteracting() == actor) {
            // Damage dealt by us to our target
            trackDamageDealt(actor, damage);

            // Update hit tracking for observations
            lastHitDamage = damage;
            lastHitSuccessful = damage > 0;

            // Calculate hit confidence based on damage
            if (damage > 0) {
                // Successful hit - high confidence
                lastHitConfidence = 1.0;
            } else {
                // Miss or block - low confidence
                lastHitConfidence = 0.0;
            }

            log.debug("[DAMAGE] Dealt {} damage to {}, confidence: {}",
                     damage, actor.getName(), lastHitConfidence);
        }

        // Store last hitsplat for this actor
        lastHitsplatDamage.put(actor, damage);

        // Clean up old cache entries
        cleanupOldEntries();
    }

    /**
     * Track damage dealt to a target.
     */
    private void trackDamageDealt(Actor target, int damage) {
        if (damage <= 0) {
            return; // Don't track misses
        }

        HitDamageCache cache = damageDealt.computeIfAbsent(target,
            k -> new HitDamageCache(0));
        cache.incrementDamage(damage);

        log.debug("[DAMAGE] Total damage dealt to {}: {}",
                 target.getName(), cache.getDamage());
    }

    /**
     * Track damage received from a source.
     */
    private void trackDamageReceived(Actor source, int damage) {
        if (damage <= 0) {
            return; // Don't track misses
        }

        HitDamageCache cache = damageReceived.computeIfAbsent(source,
            k -> new HitDamageCache(0));
        cache.incrementDamage(damage);

        log.debug("[DAMAGE] Total damage received from {}: {}",
                 source.getName(), cache.getDamage());
    }

    /**
     * Get total damage dealt to a target.
     */
    public int getDamageDealt(Actor target) {
        HitDamageCache cache = damageDealt.get(target);
        return cache != null ? cache.getDamage() : 0;
    }

    /**
     * Get total damage received from a source.
     */
    public int getDamageReceived(Actor source) {
        HitDamageCache cache = damageReceived.get(source);
        return cache != null ? cache.getDamage() : 0;
    }

    /**
     * Get the last hit confidence for observations.
     */
    public double getLastHitConfidence() {
        return lastHitConfidence;
    }

    /**
     * Get the last hit damage for observations.
     */
    public int getLastHitDamage() {
        return lastHitDamage;
    }

    /**
     * Check if the last hit was successful.
     */
    public boolean wasLastHitSuccessful() {
        return lastHitSuccessful;
    }

    /**
     * Create a HitDamage object for Elvarg compatibility.
     */
    public HitDamage createHitDamage(int damage, HitMask mask) {
        return new HitDamage(damage, mask);
    }

    /**
     * Clean up old cache entries that have timed out.
     */
    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();

        // Clean up damage dealt cache
        damageDealt.entrySet().removeIf(entry -> {
            HitDamageCache cache = entry.getValue();
            return cache.getStopwatch().elapsed() > CACHE_TIMEOUT_MS;
        });

        // Clean up damage received cache
        damageReceived.entrySet().removeIf(entry -> {
            HitDamageCache cache = entry.getValue();
            return cache.getStopwatch().elapsed() > CACHE_TIMEOUT_MS;
        });

        // Clean up old hitsplat tracking
        if (lastHitsplatDamage.size() > 10) {
            lastHitsplatDamage.clear();
        }
    }

    /**
     * Reset all damage tracking.
     */
    public void reset() {
        damageDealt.clear();
        damageReceived.clear();
        lastHitsplatDamage.clear();
        lastHitConfidence = 0.0;
        lastHitDamage = 0;
        lastHitSuccessful = false;
    }

    /**
     * Clean up when adapter is no longer needed.
     */
    public void shutdown() {
        eventBus.unregister(this);
        reset();
    }
}