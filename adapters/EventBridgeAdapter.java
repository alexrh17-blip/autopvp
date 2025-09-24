package net.runelite.client.plugins.autopvp.adapters;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adapter that bridges RuneLite events to the Elvarg/Naton environment system.
 * Coordinates event flow between RuneLite's event system and the RSPS environment.
 */
@Slf4j
public class EventBridgeAdapter {

    private final Client client;
    private final EventBus eventBus;

    // Event listeners for different phases of game tick processing
    private final List<Runnable> tickStartListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> tickProcessedListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> tickEndListeners = new CopyOnWriteArrayList<>();

    // Combat event listeners
    private final List<Runnable> combatStartListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> combatEndListeners = new CopyOnWriteArrayList<>();

    // Animation and graphics listeners
    private final List<AnimationListener> animationListeners = new CopyOnWriteArrayList<>();
    private final List<GraphicListener> graphicListeners = new CopyOnWriteArrayList<>();

    // State tracking
    private int currentTick = 0;
    private boolean inCombat = false;
    private int lastAnimation = -1;
    private int lastGraphic = -1;

    public interface AnimationListener {
        void onAnimation(int animationId);
    }

    public interface GraphicListener {
        void onGraphic(int graphicId);
    }

    public EventBridgeAdapter(Client client, EventBus eventBus) {
        this.client = client;
        this.eventBus = eventBus;
        eventBus.register(this);
        log.debug("[EVENT] EventBridgeAdapter initialized");
    }

    /**
     * Main game tick handler - coordinates all tick phases.
     */
    @Subscribe
    public void onGameTick(GameTick event) {
        currentTick++;

        // Phase 1: Tick start
        fireTickStart();

        // Phase 2: Tick processed (main processing)
        fireTickProcessed();

        // Phase 3: Tick end
        fireTickEnd();

        log.debug("[EVENT] Game tick {} processed", currentTick);
    }

    /**
     * Handle animation changes.
     */
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        int animationId = event.getActor().getAnimation();
        if (animationId != lastAnimation) {
            lastAnimation = animationId;
            fireAnimationEvent(animationId);
            log.debug("[EVENT] Animation changed to: {}", animationId);
        }
    }

    /**
     * Handle graphic changes (spell animations, etc).
     */
    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        int graphicId = event.getActor().getGraphic();
        if (graphicId != lastGraphic) {
            lastGraphic = graphicId;
            fireGraphicEvent(graphicId);
            log.debug("[EVENT] Graphic changed to: {}", graphicId);
        }
    }

    /**
     * Handle interacting changes (combat start/end).
     */
    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        if (event.getSource() != client.getLocalPlayer()) {
            return;
        }

        boolean wasInCombat = inCombat;
        inCombat = (event.getTarget() != null);

        if (!wasInCombat && inCombat) {
            fireCombatStart();
            log.debug("[EVENT] Combat started with: {}", event.getTarget().getName());
        } else if (wasInCombat && !inCombat) {
            fireCombatEnd();
            log.debug("[EVENT] Combat ended");
        }
    }

    /**
     * Handle hitsplat events.
     */
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        // Hitsplat events are handled by DamageTrackerAdapter
        // This is here for completeness and potential future use
        log.debug("[EVENT] Hitsplat applied: {} damage to {}",
                 event.getHitsplat().getAmount(),
                 event.getActor().getName());
    }

    /**
     * Handle stat changes.
     */
    @Subscribe
    public void onStatChanged(StatChanged event) {
        // Stat changes are handled by individual adapters
        // This is here for completeness
        log.debug("[EVENT] Stat changed: {} to {}",
                 event.getSkill().getName(),
                 event.getLevel());
    }

    /**
     * Handle item container changes.
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Item changes are handled by InventoryAdapter and EquipmentAdapter
        // This is here for completeness
        log.debug("[EVENT] Item container {} changed", event.getContainerId());
    }

    /**
     * Handle varbit changes (prayers, special attack, etc).
     */
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        // Varbit changes are handled by specific adapters
        // This is here for completeness
        int varbitId = event.getVarbitId();
        int value = event.getValue();
        log.debug("[EVENT] Varbit {} changed to {}", varbitId, value);
    }

    // Listener registration methods

    public void addTickStartListener(Runnable listener) {
        tickStartListeners.add(listener);
    }

    public void addTickProcessedListener(Runnable listener) {
        tickProcessedListeners.add(listener);
    }

    public void addTickEndListener(Runnable listener) {
        tickEndListeners.add(listener);
    }

    public void addCombatStartListener(Runnable listener) {
        combatStartListeners.add(listener);
    }

    public void addCombatEndListener(Runnable listener) {
        combatEndListeners.add(listener);
    }

    public void addAnimationListener(AnimationListener listener) {
        animationListeners.add(listener);
    }

    public void addGraphicListener(GraphicListener listener) {
        graphicListeners.add(listener);
    }

    // Event firing methods

    private void fireTickStart() {
        for (Runnable listener : tickStartListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("[EVENT] Error in tick start listener", e);
            }
        }
    }

    private void fireTickProcessed() {
        for (Runnable listener : tickProcessedListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("[EVENT] Error in tick processed listener", e);
            }
        }
    }

    private void fireTickEnd() {
        for (Runnable listener : tickEndListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("[EVENT] Error in tick end listener", e);
            }
        }
    }

    private void fireCombatStart() {
        for (Runnable listener : combatStartListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("[EVENT] Error in combat start listener", e);
            }
        }
    }

    private void fireCombatEnd() {
        for (Runnable listener : combatEndListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("[EVENT] Error in combat end listener", e);
            }
        }
    }

    private void fireAnimationEvent(int animationId) {
        for (AnimationListener listener : animationListeners) {
            try {
                listener.onAnimation(animationId);
            } catch (Exception e) {
                log.error("[EVENT] Error in animation listener", e);
            }
        }
    }

    private void fireGraphicEvent(int graphicId) {
        for (GraphicListener listener : graphicListeners) {
            try {
                listener.onGraphic(graphicId);
            } catch (Exception e) {
                log.error("[EVENT] Error in graphic listener", e);
            }
        }
    }

    /**
     * Get the current game tick count.
     */
    public int getCurrentTick() {
        return currentTick;
    }

    /**
     * Check if player is in combat.
     */
    public boolean isInCombat() {
        return inCombat;
    }

    /**
     * Get the last animation ID.
     */
    public int getLastAnimation() {
        return lastAnimation;
    }

    /**
     * Get the last graphic ID.
     */
    public int getLastGraphic() {
        return lastGraphic;
    }

    /**
     * Reset the event bridge state.
     */
    public void reset() {
        currentTick = 0;
        inCombat = false;
        lastAnimation = -1;
        lastGraphic = -1;
        log.debug("[EVENT] EventBridge reset");
    }

    /**
     * Clean up when adapter is no longer needed.
     */
    public void shutdown() {
        eventBus.unregister(this);
        tickStartListeners.clear();
        tickProcessedListeners.clear();
        tickEndListeners.clear();
        combatStartListeners.clear();
        combatEndListeners.clear();
        animationListeners.clear();
        graphicListeners.clear();
        reset();
        log.debug("[EVENT] EventBridge shutdown");
    }
}