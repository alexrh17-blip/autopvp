package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.model.Item;
import com.elvarg.game.model.container.impl.Inventory;
import com.elvarg.game.entity.impl.player.Player;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that bridges RuneLite's inventory container to Elvarg's Inventory system.
 * Provides access to inventory items through Elvarg's Inventory interface.
 */
@Slf4j
public class InventoryAdapter extends Inventory {

    private final Client client;

    public InventoryAdapter(Client client, Player elvargPlayer) {
        super(elvargPlayer);
        this.client = client;
    }

    @Override
    public int capacity() {
        return 28; // Standard inventory size
    }

    @Override
    public Item[] getItems() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return new Item[28];
        }

        Item[] items = new Item[28];
        net.runelite.api.Item[] rlItems = inventory.getItems();

        for (int i = 0; i < Math.min(rlItems.length, 28); i++) {
            if (rlItems[i] != null && rlItems[i].getId() > 0) {
                items[i] = new Item(rlItems[i].getId(), rlItems[i].getQuantity());
            }
        }

        return items;
    }

    @Override
    public Item get(int slot) {
        if (slot < 0 || slot >= 28) {
            return null;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return null;
        }

        net.runelite.api.Item[] items = inventory.getItems();
        if (slot >= items.length || items[slot] == null) {
            return null;
        }

        if (items[slot].getId() <= 0) {
            return null;
        }

        return new Item(items[slot].getId(), items[slot].getQuantity());
    }

    @Override
    public boolean contains(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return false;
        }

        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null && item.getId() == itemId) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean contains(Item item) {
        return item != null && contains(item.getId());
    }

    @Override
    public int getAmount(int itemId) {
        // Special handling for anglerfish to count ALL food
        // Citation: NhEnvironment.java:964-968 hardcoded to only count anglerfish
        // Citation: Food.java:153-188 lists all food items
        if (itemId == ItemID.ANGLERFISH) { // Anglerfish ID
            // Count ALL food items when asked for anglerfish
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory == null) {
                return 0;
            }

            int foodCount = 0;
            for (net.runelite.api.Item item : inventory.getItems()) {
                if (item != null && item.getId() > 0) {
                    // Check against all known food IDs
                    int id = item.getId();
                    if (id == ItemID.SHARK || // Shark - Citation: Food.java:166
                        id == ItemID.ANGLERFISH || // Anglerfish - Citation: Food.java:169
                        id == ItemID.MANTARAY || // Manta ray - Citation: Food.java:167
                        id == ItemID.DARK_CRAB || // Dark crab - Citation: Food.java:167
                        id == ItemID.MONKFISH || // Monkfish - Citation: Food.java:165
                        id == ItemID.LOBSTER || // Lobster - Citation: Food.java:163
                        id == ItemID.SWORDFISH || // Swordfish - Citation: Food.java:163-164
                        id == ItemID.TUNA || // Tuna - Citation: Food.java:162-163
                        id == ItemID.SEATURTLE || // Sea turtle - Citation: Food.java:166
                        id == ItemID.TBWT_COOKED_KARAMBWAN) { // Karambwan - Citation: Food.java:167-168
                        foodCount += item.getQuantity();
                    }
                }
            }
            log.debug("[INVENTORY] Food workaround active: {} total food items", foodCount);
            return foodCount;
        }

        // Normal item counting for non-anglerfish
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        int amount = 0;
        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null && item.getId() == itemId) {
                amount += item.getQuantity();
            }
        }

        return amount;
    }

    @Override
    public boolean isEmpty() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return true;
        }

        for (net.runelite.api.Item item : inventory.getItems()) {
            if (item != null && item.getId() > 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isFull() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return false;
        }

        net.runelite.api.Item[] items = inventory.getItems();
        for (int i = 0; i < Math.min(items.length, 28); i++) {
            if (items[i] == null || items[i].getId() <= 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int getFreeSlots() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            log.debug("[INVENTORY] Container is null, returning 28 free slots");
            return 28;
        }

        int free = 0;
        net.runelite.api.Item[] items = inventory.getItems();

        // Debug logging for first call
        if (log.isDebugEnabled()) {
            log.debug("[INVENTORY] Items array length: {}", items.length);
        }

        for (int i = 0; i < Math.min(items.length, 28); i++) {
            // RuneLite uses -1 for empty slots, but sometimes returns Item with id <= 0
            if (items[i] == null) {
                free++;
                if (log.isTraceEnabled()) {
                    log.trace("[INVENTORY] Slot {} is null (free)", i);
                }
            } else if (items[i].getId() == -1) {
                free++;
                if (log.isTraceEnabled()) {
                    log.trace("[INVENTORY] Slot {} has ID -1 (free)", i);
                }
            } else if (items[i].getId() == 0) {
                // ID 0 might be used for empty slots in some cases
                free++;
                if (log.isTraceEnabled()) {
                    log.trace("[INVENTORY] Slot {} has ID 0 (free)", i);
                }
            } else {
                // This slot has an actual item
                if (log.isTraceEnabled()) {
                    log.trace("[INVENTORY] Slot {} has item ID {} qty {}", i, items[i].getId(), items[i].getQuantity());
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("[INVENTORY] Total free slots: {}/28", free);
        }

        return free;
    }

    @Override
    public int getSlot(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return -1;
        }

        net.runelite.api.Item[] items = inventory.getItems();
        for (int i = 0; i < Math.min(items.length, 28); i++) {
            if (items[i] != null && items[i].getId() == itemId) {
                return i;
            }
        }

        return -1;
    }

    public int getSlot(Item item) {
        return item != null ? getSlot(item.getId()) : -1;
    }

    /**
     * Get all item IDs as an array.
     * Required by ItemInSlot.getFromInventory() for food detection.
     * @return Array of item IDs, -1 for empty slots
     */
    public int[] getItemIdsArray() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            int[] empty = new int[28];
            for (int i = 0; i < 28; i++) {
                empty[i] = -1;  // RuneLite uses -1 for empty slots
            }
            return empty;
        }

        net.runelite.api.Item[] items = inventory.getItems();
        int[] ids = new int[28];
        for (int i = 0; i < Math.min(items.length, 28); i++) {
            // Be consistent with getFreeSlots logic
            if (items[i] == null || items[i].getId() <= 0) {
                ids[i] = -1;  // Empty slot
            } else {
                ids[i] = items[i].getId();
            }
        }

        // Log a summary if debug enabled
        if (log.isDebugEnabled()) {
            int itemCount = 0;
            for (int id : ids) {
                if (id > 0) itemCount++;
            }
            log.debug("[INVENTORY] Item array: {} items, {} empty slots", itemCount, 28 - itemCount);
        }

        return ids;
    }

    // Methods that modify inventory - not supported in RuneLite context
    @Override
    public void set(int slot, Item item) {
        log.debug("[ADAPTER] Cannot modify inventory in RuneLite (read-only)");
    }

    @Override
    public Inventory add(Item item) {
        log.debug("[ADAPTER] Cannot add to inventory in RuneLite (read-only)");
        return this;
    }

    @Override
    public Inventory add(int itemId, int amount) {
        log.debug("[ADAPTER] Cannot add to inventory in RuneLite (read-only)");
        return this;
    }

    @Override
    public Inventory delete(Item item) {
        log.debug("[ADAPTER] Cannot delete from inventory in RuneLite (read-only)");
        return this;
    }

    @Override
    public Inventory delete(int itemId, int amount) {
        log.debug("[ADAPTER] Cannot delete from inventory in RuneLite (read-only)");
        return this;
    }

    @Override
    public Inventory refreshItems() {
        // No-op in RuneLite context
        return this;
    }

    @Override
    public Inventory setItems(Item[] items) {
        log.debug("[ADAPTER] Cannot set inventory items in RuneLite (read-only)");
        return this;
    }

    @Override
    public Inventory swap(int slot, int otherSlot) {
        log.debug("[ADAPTER] Cannot swap inventory items in RuneLite (read-only)");
        return this;
    }

    public void shift() {
        log.debug("[ADAPTER] Cannot shift inventory items in RuneLite (read-only)");
    }

    @Override
    public Inventory full() {
        log.debug("[ADAPTER] Inventory full message (read-only)");
        return this;
    }
}