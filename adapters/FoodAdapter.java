package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.Food;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Item;
import com.elvarg.util.timers.TimerKey;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that bridges RuneLite's food system to Elvarg's Food/Edible system.
 * Tracks food consumption, healing amounts, and eating delays.
 */
@Slf4j
public class FoodAdapter {

    private final Client client;
    private final EventBus eventBus;
    private final TimerManagerAdapter timerManager;

    // Animation for eating food
    private static final int EAT_ANIMATION = 829;

    // Track last food consumed
    private Food.Edible lastFoodConsumed = null;
    private int lastFoodItemId = -1;
    private long lastFoodTime = 0;

    // Map item IDs to Edible types - using numeric IDs from Elvarg source
    private static final Map<Integer, Food.Edible> FOOD_MAPPING = new HashMap<>();
    static {
        // Initialize food mappings from Elvarg Food.java
        // Fish
        registerFood(ItemID.ANCHOVIES, Food.Edible.ANCHOVIES);      // Anchovies
        registerFood(315, Food.Edible.SHRIMPS);        // Shrimps - no ItemID constant (315)
        registerFood(ItemID.SARDINE, Food.Edible.SARDINE);        // Sardine
        registerFood(ItemID.COD, Food.Edible.COD);            // Cod
        registerFood(ItemID.TROUT, Food.Edible.TROUT);          // Trout
        registerFood(ItemID.PIKE, Food.Edible.PIKE);           // Pike
        registerFood(ItemID.SALMON, Food.Edible.SALMON);         // Salmon
        registerFood(ItemID.TUNA, Food.Edible.TUNA);           // Tuna
        registerFood(ItemID.LOBSTER, Food.Edible.LOBSTER);        // Lobster
        registerFood(ItemID.BASS, Food.Edible.BASS);           // Bass
        registerFood(ItemID.SWORDFISH, Food.Edible.SWORDFISH);      // Swordfish
        registerFood(ItemID.MONKFISH, Food.Edible.MONKFISH);      // Monkfish
        registerFood(ItemID.SHARK, Food.Edible.SHARK);          // Shark
        registerFood(ItemID.SEATURTLE, Food.Edible.SEA_TURTLE);     // Sea turtle
        registerFood(ItemID.DARK_CRAB, Food.Edible.DARK_CRAB);    // Dark crab
        registerFood(ItemID.MANTARAY, Food.Edible.MANTA_RAY);      // Manta ray
        registerFood(ItemID.TBWT_COOKED_KARAMBWAN, Food.Edible.KARAMBWAN);     // Karambwan
        registerFood(ItemID.ANGLERFISH, Food.Edible.ANGLERFISH);   // Anglerfish

        // Baked goods
        registerFood(ItemID.POTATO, Food.Edible.POTATO);        // Potato
        registerFood(ItemID.POTATO_BAKED, Food.Edible.BAKED_POTATO);  // Baked potato
        registerFood(ItemID.POTATO_BUTTER, Food.Edible.POTATO_WITH_BUTTER);  // Potato with butter
        registerFood(ItemID.POTATO_CHILLI_CARNE, Food.Edible.CHILLI_POTATO); // Chilli potato
        registerFood(ItemID.POTATO_EGG_TOMATO, Food.Edible.EGG_POTATO);    // Egg potato
        registerFood(ItemID.POTATO_CHEESE, Food.Edible.POTATO_WITH_CHEESE);  // Potato with cheese
        registerFood(ItemID.POTATO_MUSHROOM_ONION, Food.Edible.MUSHROOM_POTATO);     // Mushroom potato
        registerFood(ItemID.POTATO_TUNA_SWEETCORN, Food.Edible.TUNA_POTATO);   // Tuna potato

        // Fruit
        registerFood(ItemID.BANANA, Food.Edible.BANANA);        // Banana
        registerFood(18199, Food.Edible.BANANA_);      // Banana (variant) - no ItemID constant (18199)
        registerFood(ItemID.CABBAGE, Food.Edible.CABBAGE);       // Cabbage
        registerFood(ItemID.ORANGE, Food.Edible.ORANGE);        // Orange
        registerFood(ItemID.PINEAPPLE_CHUNKS, Food.Edible.PINEAPPLE_CHUNKS);    // Pineapple chunks
        registerFood(ItemID.PINEAPPLE_RING, Food.Edible.PINEAPPLE_RINGS);     // Pineapple rings
        registerFood(ItemID.PEACH, Food.Edible.PEACH);         // Peach

        // Other
        registerFood(ItemID.KEBAB, Food.Edible.KEBAB);         // Kebab
        registerFood(ItemID.CHEESE, Food.Edible.CHEESE);        // Cheese
        registerFood(ItemID.CAKE, Food.Edible.CAKE);          // Cake
        registerFood(ItemID.PARTIAL_CAKE, Food.Edible.SECOND_CAKE_SLICE);   // Second cake slice
        registerFood(ItemID.CAKE_SLICE, Food.Edible.THIRD_CAKE_SLICE);    // Third cake slice
        registerFood(14640, Food.Edible.BANDAGES);     // Bandages - ItemID.CASTLEWARS_BANDAGES is different (14640 vs 4049)
        registerFood(ItemID.JANGERBERRIES, Food.Edible.JANGERBERRIES);  // Jangerberries
        registerFood(ItemID.WORM_CRUNCHIES, Food.Edible.WORM_CRUNCHIES);      // Worm crunchies
        registerFood(ItemID.EDIBLE_SEAWEED, Food.Edible.EDIBLE_SEAWEED); // Edible seaweed
        registerFood(ItemID.MEAT_PIZZA, Food.Edible.MEAT_PIZZA);    // Meat pizza
        registerFood(ItemID.SPINACH_ROLL, Food.Edible.SPINACH_ROLL);  // Spinach roll
        registerFood(4561, Food.Edible.PURPLE_SWEETS); // Purple sweets - no exact ItemID constant (4561)
    }

    private static void registerFood(int itemId, Food.Edible type) {
        FOOD_MAPPING.put(itemId, type);
    }

    public FoodAdapter(Client client, EventBus eventBus, TimerManagerAdapter timerManager) {
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

        if (event.getActor().getAnimation() == EAT_ANIMATION) {
            // Player is eating food
            handleFoodConsumption();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }

        // Check for food consumption by inventory changes
        // This is a backup detection method
    }

    private void handleFoodConsumption() {
        // Register food timer - default 3 tick delay
        if (timerManager != null) {
            timerManager.register(TimerKey.FOOD, 3);

            // Check if karambwan was consumed (special case - shorter delay)
            if (lastFoodConsumed == Food.Edible.KARAMBWAN) {
                timerManager.register(TimerKey.KARAMBWAN, 3);
                timerManager.register(TimerKey.POTION, 3); // Karambwan blocks potions
            }
        }

        lastFoodTime = System.currentTimeMillis();
        log.debug("[FOOD] Food consumed, 3 tick delay registered");
    }

    /**
     * Check if a specific food type can be consumed.
     */
    public boolean canEat(Player player, int itemId) {
        // Check if stunned
        if (timerManager != null && timerManager.has(TimerKey.STUN)) {
            return false;
        }

        Food.Edible food = getFoodType(itemId).orElse(null);
        if (food == null) {
            return false;
        }

        // Check food timer
        if (food == Food.Edible.KARAMBWAN) {
            if (timerManager != null && timerManager.has(TimerKey.KARAMBWAN)) {
                return false;
            }
        } else {
            if (timerManager != null && timerManager.has(TimerKey.FOOD)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get the Food.Edible type for an item ID.
     */
    public Optional<Food.Edible> getFoodType(int itemId) {
        return Optional.ofNullable(FOOD_MAPPING.get(itemId));
    }

    /**
     * Check if an item is food.
     */
    public boolean isFood(int itemId) {
        return FOOD_MAPPING.containsKey(itemId);
    }

    /**
     * Get the heal amount for a food item.
     */
    public int getHealAmount(int itemId) {
        Food.Edible food = FOOD_MAPPING.get(itemId);
        if (food == null) {
            return 0;
        }

        // Special case for anglerfish - dynamic healing
        if (food == Food.Edible.ANGLERFISH) {
            int maxHp = client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS);
            int c = 2;
            if (maxHp >= 25) c = 4;
            if (maxHp >= 50) c = 6;
            if (maxHp >= 75) c = 8;
            if (maxHp >= 93) c = 13;
            int healAmount = (int) Math.floor((maxHp / 10.0) + c);
            return Math.min(healAmount, 22);
        }

        return food.getHeal();
    }

    /**
     * Count total food items in inventory.
     */
    public int countFood() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        int count = 0;
        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null && isFood(item.getId())) {
                count++;
            }
        }

        return count;
    }

    /**
     * Count total healing potential in inventory.
     */
    public int getTotalHealingPotential() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        int totalHealing = 0;
        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null && isFood(item.getId())) {
                totalHealing += getHealAmount(item.getId());
            }
        }

        return totalHealing;
    }

    /**
     * Count specific type of food.
     */
    public int countFoodType(Food.Edible type) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        int count = 0;
        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null) {
                Optional<Food.Edible> foodType = getFoodType(item.getId());
                if (foodType.isPresent() && foodType.get() == type) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Get the last food consumed.
     */
    public Food.Edible getLastFoodConsumed() {
        return lastFoodConsumed;
    }

    /**
     * Get time since last food (in milliseconds).
     */
    public long getTimeSinceLastFood() {
        return System.currentTimeMillis() - lastFoodTime;
    }

    /**
     * Check if player can eat (not stunned or on timer).
     */
    public boolean canEat() {
        if (timerManager == null) {
            return true;
        }

        return !timerManager.has(TimerKey.FOOD) && !timerManager.has(TimerKey.STUN);
    }

    /**
     * Check if player can eat karambwan (special combo food).
     */
    public boolean canEatKarambwan() {
        if (timerManager == null) {
            return true;
        }

        return !timerManager.has(TimerKey.KARAMBWAN) && !timerManager.has(TimerKey.STUN);
    }

    /**
     * Count sharks in inventory.
     */
    public int countSharks() {
        return countFoodType(Food.Edible.SHARK);
    }

    /**
     * Count karambwans in inventory.
     */
    public int countKarambwans() {
        return countFoodType(Food.Edible.KARAMBWAN);
    }

    /**
     * Count anglerfish in inventory.
     */
    public int countAnglerfish() {
        return countFoodType(Food.Edible.ANGLERFISH);
    }

    /**
     * Count manta rays in inventory.
     */
    public int countMantaRays() {
        return countFoodType(Food.Edible.MANTA_RAY);
    }

    /**
     * Clean up when adapter is no longer needed.
     */
    public void shutdown() {
        eventBus.unregister(this);
    }
}