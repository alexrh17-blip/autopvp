package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.model.Item;
import com.elvarg.game.model.container.impl.Equipment;
import com.elvarg.game.entity.impl.player.Player;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that bridges RuneLite's equipment container to Elvarg's Equipment system.
 * Provides access to equipped items through Elvarg's Equipment interface.
 */
@Slf4j
public class EquipmentAdapter extends Equipment {

    private final Client client;

    public EquipmentAdapter(Client client, Player elvargPlayer) {
        super(elvargPlayer);
        this.client = client;
    }

    @Override
    public Item[] getItems() {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return new Item[14];
        }

        Item[] items = new Item[14];
        net.runelite.api.Item[] rlItems = equipment.getItems();

        for (int i = 0; i < Math.min(rlItems.length, 14); i++) {
            if (rlItems[i] != null && rlItems[i].getId() > 0) {
                items[i] = new Item(rlItems[i].getId(), rlItems[i].getQuantity());
            }
        }

        return items;
    }

    @Override
    public int capacity() {
        return 14;
    }

    @Override
    public Item get(int slot) {
        if (slot < 0 || slot >= 14) {
            return null;
        }

        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return null;
        }

        net.runelite.api.Item[] items = equipment.getItems();
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
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return false;
        }

        for (net.runelite.api.Item item : equipment.getItems()) {
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
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return 0;
        }

        int amount = 0;
        for (net.runelite.api.Item item : equipment.getItems()) {
            if (item != null && item.getId() == itemId) {
                amount += item.getQuantity();
            }
        }

        return amount;
    }

    @Override
    public boolean isEmpty() {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return true;
        }

        for (net.runelite.api.Item item : equipment.getItems()) {
            if (item != null && item.getId() > 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isFull() {
        // Equipment can't be "full" in traditional sense - each slot has specific item types
        return false;
    }

    @Override
    public int getFreeSlots() {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return 14;
        }

        int free = 0;
        net.runelite.api.Item[] items = equipment.getItems();
        for (int i = 0; i < Math.min(items.length, 14); i++) {
            if (items[i] == null || items[i].getId() <= 0) {
                free++;
            }
        }

        return free;
    }

    /**
     * Gets the weapon item. Returns an Item with id -1 if no weapon equipped.
     * This prevents NullPointerException when NhEnvironment calls getWeapon().getId()
     */
    @Override
    public Item getWeapon() {
        Item weapon = get(Equipment.WEAPON_SLOT);
        // Return empty item with ID -1 if no weapon equipped to prevent NPE
        return weapon != null ? weapon : new Item(-1, 0);
    }

    /**
     * Gets equipment as an int array of item IDs (for NhEnvironment compatibility).
     */
    public int[] getItemIdsArray() {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) {
            return new int[14];
        }

        int[] ids = new int[14];
        net.runelite.api.Item[] items = equipment.getItems();

        for (int i = 0; i < Math.min(items.length, 14); i++) {
            if (items[i] != null) {
                ids[i] = items[i].getId();
            }
        }

        return ids;
    }

    // Methods that modify equipment - not supported in RuneLite context
    @Override
    public void set(int slot, Item item) {
        log.debug("[ADAPTER] Cannot modify equipment in RuneLite (read-only)");
    }

    @Override
    public Equipment add(Item item) {
        log.debug("[ADAPTER] Cannot add to equipment in RuneLite (read-only)");
        return this;
    }

    @Override
    public Equipment delete(Item item) {
        log.debug("[ADAPTER] Cannot delete from equipment in RuneLite (read-only)");
        return this;
    }

    @Override
    public Equipment delete(int itemId, int amount) {
        log.debug("[ADAPTER] Cannot delete from equipment in RuneLite (read-only)");
        return this;
    }

    @Override
    public Equipment refreshItems() {
        // No-op in RuneLite context
        return this;
    }

    @Override
    public Equipment setItems(Item[] items) {
        log.debug("[ADAPTER] Cannot set equipment items in RuneLite (read-only)");
        return this;
    }

    @Override
    public Equipment full() {
        log.debug("[ADAPTER] Equipment full message (read-only)");
        return this;
    }
}