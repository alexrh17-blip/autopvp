package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.PotionConsumable;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.Skill;
import com.elvarg.util.timers.TimerKey;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that bridges RuneLite's potion system to Elvarg's PotionConsumable.
 * Tracks potion effects, doses, and consumption timing.
 */
@Slf4j
public class PotionConsumableAdapter {

    private final Client client;
    private final EventBus eventBus;
    private final TimerManagerAdapter timerManager;

    // Animation for drinking potions
    private static final int DRINK_ANIMATION = 829;

    // Track active potion effects and their levels
    private final Map<Skill, Integer> boostedSkills = new HashMap<>();

    // Track last potion consumed
    private PotionConsumable lastPotionConsumed = null;
    private int lastPotionItemId = -1;
    private long lastPotionTime = 0;

    public PotionConsumableAdapter(Client client, EventBus eventBus, TimerManagerAdapter timerManager) {
        this.client = client;
        this.eventBus = eventBus;
        this.timerManager = timerManager;
        eventBus.register(this);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        if (event.getActor().getAnimation() == DRINK_ANIMATION) {
            // Player is drinking a potion
            handlePotionDrink();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }

        // Check for potion consumption by inventory changes
        // This is a backup detection method
    }

    private void handlePotionDrink() {
        // Register potion timer
        if (timerManager != null) {
            timerManager.register(TimerKey.POTION, 3); // 3 tick delay
        }

        lastPotionTime = System.currentTimeMillis();
        log.debug("[POTION] Potion consumed, 3 tick delay registered");
    }

    /**
     * Check if a specific potion type can be consumed.
     */
    public boolean canDrink(Player player, int itemId) {
        // Check if on potion timer
        if (timerManager != null && timerManager.has(TimerKey.POTION)) {
            return false;
        }

        // Check if stunned
        if (timerManager != null && timerManager.has(TimerKey.STUN)) {
            return false;
        }

        return true;
    }

    /**
     * Get the PotionConsumable type for an item ID.
     */
    public Optional<PotionConsumable> getPotionType(int itemId) {
        // Delegate to Elvarg's PotionConsumable enum
        // Citation: PotionConsumable.getIds() from C:/dev/elvarg-rsps-master/ElvargServer/game/src/main/java/com/elvarg/game/content/PotionConsumable.java:L393
        for (PotionConsumable potion : PotionConsumable.values()) {
            for (int id : potion.getIds()) {
                if (id == itemId) {
                    return Optional.of(potion);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Check if an item is a potion.
     */
    public boolean isPotion(int itemId) {
        return getPotionType(itemId).isPresent();
    }

    /**
     * Get the number of doses for a potion item.
     */
    public int getPotionDoses(int itemId) {
        // Determine doses based on item ID pattern
        // Most potions follow pattern: (4) = 4 doses, (3) = 3 doses, etc.
        String name = client.getItemDefinition(itemId).getName();
        if (name.contains("(4)")) return 4;
        if (name.contains("(3)")) return 3;
        if (name.contains("(2)")) return 2;
        if (name.contains("(1)")) return 1;
        return 0;
    }

    /**
     * Count total potion doses in inventory.
     */
    public int countPotionDoses(PotionConsumable type) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        int totalDoses = 0;
        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null) {
                Optional<PotionConsumable> potionType = getPotionType(item.getId());
                if (potionType.isPresent() && potionType.get() == type) {
                    totalDoses += getPotionDoses(item.getId());
                }
            }
        }

        return totalDoses;
    }

    /**
     * Count total doses of a specific potion category.
     */
    public int countCombatPotionDoses() {
        return countPotionDoses(PotionConsumable.COMBAT_POTIONS) +
               countPotionDoses(PotionConsumable.SUPER_COMBAT_POTIONS);
    }

    public int countRangingPotionDoses() {
        return countPotionDoses(PotionConsumable.RANGE_POTIONS) +
               countPotionDoses(PotionConsumable.SUPER_RANGE_POTIONS);
    }

    public int countBrewDoses() {
        return countPotionDoses(PotionConsumable.SARADOMIN_BREW);
    }

    public int countRestoreDoses() {
        return countPotionDoses(PotionConsumable.SUPER_RESTORE_POTIONS);
    }

    /**
     * Check if player has any boost active for a skill.
     */
    public boolean hasBoost(Skill skill) {
        return boostedSkills.containsKey(skill) && boostedSkills.get(skill) > 0;
    }

    /**
     * Track skill boost from potion.
     */
    public void trackBoost(Skill skill, int boostAmount) {
        boostedSkills.put(skill, boostAmount);
        log.debug("[POTION] {} boosted by {}", skill.name(), boostAmount);
    }

    /**
     * Get the last potion consumed.
     */
    public PotionConsumable getLastPotionConsumed() {
        return lastPotionConsumed;
    }

    /**
     * Get time since last potion (in milliseconds).
     */
    public long getTimeSinceLastPotion() {
        return System.currentTimeMillis() - lastPotionTime;
    }

    /**
     * Check if player can use potions (not stunned or on timer).
     */
    public boolean canUsePotion() {
        if (timerManager == null) {
            return true;
        }

        return !timerManager.has(TimerKey.POTION) && !timerManager.has(TimerKey.STUN);
    }

    /**
     * Clean up when adapter is no longer needed.
     */
    public void shutdown() {
        eventBus.unregister(this);
        boostedSkills.clear();
    }
}