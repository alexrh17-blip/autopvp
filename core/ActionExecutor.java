package net.runelite.client.plugins.autopvp.core;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.*;
// Using gameval ItemID as per CLAUDE.md instructions
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InterfaceID.MagicSpellbook;
import net.runelite.client.callback.ClientThread;

// PacketUtils imports with citations for audit trail
// Citation: All imports from C:/dev/PacketUtils/src/main/java/com/example/ for direct packet injection
import com.example.InteractionApi.InventoryInteraction; // Citation: PacketUtils inventory API from C:/dev/PacketUtils/src/main/java/com/example/InteractionApi/InventoryInteraction.java
import com.example.Packets.WidgetPackets; // Citation: PacketUtils widget API from C:/dev/PacketUtils/src/main/java/com/example/Packets/WidgetPackets.java
import com.example.InteractionApi.PlayerInteractionHelper; // Citation: PacketUtils player interaction API from C:/dev/PacketUtils/src/main/java/com/example/InteractionApi/PlayerInteractionHelper.java
import com.example.Packets.MovementPackets; // Citation: PacketUtils movement API from C:/dev/PacketUtils/src/main/java/com/example/Packets/MovementPackets.java
import com.example.Packets.MousePackets; // Citation: PacketUtils mouse API from C:/dev/PacketUtils/src/main/java/com/example/Packets/MousePackets.java
import com.example.EthanApiPlugin.Collections.Inventory; // Citation: PacketUtils inventory collection from C:/dev/PacketUtils/src/main/java/com/example/EthanApiPlugin/Collections/Inventory.java
import com.example.PacketUtils.PacketUtilsPlugin; // Citation: Main plugin class for initialization check
import com.example.PacketUtils.PacketReflection; // Citation: For getting client instance

import java.util.Arrays;
import java.util.Optional;

/**
 * ActionExecutor translates AI actions into game mechanics using PacketUtils.
 * This class is responsible for executing actions determined by the AI model
 * directly through packet injection, bypassing the normal menu system.
 */
@Slf4j
public class ActionExecutor {

    private final Client client;
    private final NhEnvironmentBridge environmentBridge;
    private final ClientThread clientThread;

    // Track if PacketUtils is properly initialized
    private boolean packetUtilsReady = false;
    private boolean hasWarnedAboutPacketUtils = false;

    public ActionExecutor(Client client, NhEnvironmentBridge environmentBridge, ClientThread clientThread) {
        this.client = client;
        this.environmentBridge = environmentBridge;
        this.clientThread = clientThread;

        // Check PacketUtils initialization on construction
        checkPacketUtilsInitialization();
    }

    /**
     * Checks if PacketUtils is properly initialized and ready to use.
     * This prevents NullPointerException when trying to send packets.
     */
    private boolean checkPacketUtilsInitialization() {
        try {
            // Check if the plugin's static fields are initialized
            boolean clientAddNode = PacketUtilsPlugin.usingClientAddNode;
            if (!clientAddNode && PacketUtilsPlugin.addNodeMethod == null) {
                if (!hasWarnedAboutPacketUtils) {
                    log.error("[ACTION] PacketUtilsPlugin not initialized - addNodeMethod is null! Packets will fail.");
                    log.error("[ACTION] Make sure PacketUtils plugin is enabled in RuneLite plugin manager.");
                    hasWarnedAboutPacketUtils = true;
                }
                packetUtilsReady = false;
                return false;
            }

            // Verify client is available through PacketReflection
            Client reflectionClient = PacketReflection.getClient();
            if (reflectionClient == null) {
                if (!hasWarnedAboutPacketUtils) {
                    log.error("[ACTION] PacketReflection.getClient() returned null - PacketUtils not ready!");
                    hasWarnedAboutPacketUtils = true;
                }
                packetUtilsReady = false;
                return false;
            }

            packetUtilsReady = true;
            if (hasWarnedAboutPacketUtils) {
                log.info("[ACTION] PacketUtils is now properly initialized and ready!");
                hasWarnedAboutPacketUtils = false;
            }
            return true;
        } catch (Exception e) {
            if (!hasWarnedAboutPacketUtils) {
                log.error("[ACTION] Failed to check PacketUtils initialization", e);
                hasWarnedAboutPacketUtils = true;
            }
            packetUtilsReady = false;
            return false;
        }
    }

    /**
     * Executes actions from the AI model using PacketUtils for direct packet injection.
     * All packet operations MUST run on the client thread to avoid disconnections.
     *
     * @param actions Array of 12 action values from AI model
     */
    public void executeAction(int[] actions) {
        if (actions == null || actions.length != 12) {
            log.warn("[ACTION] Received invalid action array");
            return;
        }

        if (!checkPacketUtilsInitialization()) {
            log.debug("[ACTION] Skipping action execution - PacketUtils not ready");
            return;
        }

        int[] actionCopy = Arrays.copyOf(actions, actions.length);
        clientThread.invoke(() -> executeActionInternal(actionCopy));
    }

    private void executeActionInternal(int[] actions) {
        if (!client.isClientThread()) {
            clientThread.invoke(() -> executeActionInternal(actions));
            return;
        }

        if (!checkPacketUtilsInitialization()) {
            log.debug("[ACTION] PacketUtils not ready during execution");
            return;
        }

        log.debug("[ACTION] Executing actions: attack={}, melee={}, ranged={}, mage={}, potion={}, food={}, karam={}, veng={}, gear={}, move={}, farcast={}, prayer={}",
                  actions[0], actions[1], actions[2], actions[3], actions[4], actions[5],
                  actions[6], actions[7], actions[8], actions[9], actions[10], actions[11]);

        int attackType = actions[0];
        int meleeType = actions[1];
        int rangedType = actions[2];
        int mageType = actions[3];
        int potionAction = actions[4];
        int foodAction = actions[5];
        int karambwanAction = actions[6];
        int vengAction = actions[7];
        int gearAction = actions[8];
        int moveAction = actions[9];
        int farcastDistance = actions[10];
        int prayerAction = actions[11];

        if (foodAction == 1) {
            handleFood();
        }
        if (karambwanAction == 1) {
            handleKarambwan();
        }
        if (potionAction > 0) {
            handlePotion(potionAction);
        }

        if (gearAction == 1) {
            handleTankGear();
        }
        if (vengAction == 1) {
            handleVengeance(vengAction);
        }

        if (prayerAction > 0) {
            handlePrayer(prayerAction);
        }

        if (attackType > 0) {
            handleAttack(attackType, meleeType, rangedType, mageType);
        }

        if (moveAction > 0) {
            handleMovement(moveAction, farcastDistance);
        }
    }

    private void handleFood() {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot eat food - PacketUtils not ready");
            return;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            Item[] items = inventory.getItems();
            for (int slot = 0; slot < items.length; slot++) {
                Item item = items[slot];
                if (item != null && isFood(item.getId())) {
                    final int foodId = item.getId();
                    final int foodSlot = slot;

                    // Execute ENTIRE packet operation on client thread with full validation
                    clientThread.invoke(() -> {
                        try {
                            // Re-validate item still exists at this slot on client thread
                            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
                            if (inv == null) {
                                log.debug("[ACTION] Cannot eat food - inventory null on client thread");
                                return;
                            }

                            Item[] currentItems = inv.getItems();
                            if (foodSlot >= currentItems.length) {
                                log.debug("[ACTION] Cannot eat food - slot {} out of bounds", foodSlot);
                                return;
                            }

                            Item currentItem = currentItems[foodSlot];
                            if (currentItem == null || currentItem.getId() != foodId) {
                                log.debug("[ACTION] Cannot eat food - item changed or moved from slot {}", foodSlot);
                                return;
                            }

                            // Get the widget directly to ensure it's valid
                            Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
                            if (inventoryWidget == null) {
                                log.debug("[ACTION] Cannot eat food - inventory widget null");
                                return;
                            }

                            Widget[] children = inventoryWidget.getDynamicChildren();
                            if (children == null || foodSlot >= children.length) {
                                log.debug("[ACTION] Cannot eat food - no children widgets or slot out of bounds");
                                return;
                            }

                            Widget foodWidget = children[foodSlot];
                            if (foodWidget == null || foodWidget.getItemId() != foodId) {
                                log.debug("[ACTION] Cannot eat food - widget not found or item mismatch at slot {}", foodSlot);
                                return;
                            }

                            // Now send the packets directly with the validated widget
                            MousePackets.queueClickPacket();
                            WidgetPackets.queueWidgetAction(foodWidget, "Eat");
                            log.debug("[ACTION] Consumed food item {} at slot {} via PacketUtils", foodId, foodSlot);

                        } catch (Exception e) {
                            log.error("[ACTION] Failed to consume food", e);
                        }
                    });
                    return; // Eat only one food at a time
                }
            }
        }
        log.debug("[ACTION] No food found in inventory");
    }

    private boolean isFood(int itemId) {
        // Check common PvP foods based on verified IDs from gameval
        return itemId == ItemID.SHARK ||           // 385 - gameval constant
               itemId == ItemID.MANTARAY ||        // 391 - gameval constant (MANTARAY not MANTA_RAY)
               itemId == ItemID.DARK_CRAB ||       // 11936 - gameval constant
               itemId == ItemID.ANGLERFISH ||      // 13441 - gameval constant
               itemId == ItemID.TBWT_COOKED_KARAMBWAN;  // 3144 - gameval constant (also handled separately)
    }

    private void handlePotion(int potionAction) {
        log.debug("[ACTION] handlePotion called with action: {}", potionAction);

        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot drink potion - PacketUtils not ready");
            return;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            Item[] items = inventory.getItems();
            for (int slot = 0; slot < items.length; slot++) {
                Item item = items[slot];
                if (item != null && isPotionType(item.getId(), potionAction)) {
                    final int potionId = item.getId();
                    final int potionSlot = slot;

                    // Execute ENTIRE packet operation on client thread with full validation
                    clientThread.invoke(() -> {
                        try {
                            // Re-validate item still exists at this slot on client thread
                            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
                            if (inv == null) {
                                log.debug("[ACTION] Cannot drink potion - inventory null on client thread");
                                return;
                            }

                            Item[] currentItems = inv.getItems();
                            if (potionSlot >= currentItems.length) {
                                log.debug("[ACTION] Cannot drink potion - slot {} out of bounds", potionSlot);
                                return;
                            }

                            Item currentItem = currentItems[potionSlot];
                            if (currentItem == null || currentItem.getId() != potionId) {
                                log.debug("[ACTION] Cannot drink potion - item changed or moved from slot {}", potionSlot);
                                return;
                            }

                            // Get the widget directly to ensure it's valid (EXACT SAME AS FOOD WHICH WORKS)
                            Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
                            if (inventoryWidget == null) {
                                log.debug("[ACTION] Cannot drink potion - inventory widget null");
                                return;
                            }

                            // Use getDynamicChildren() exactly like food does
                            Widget[] children = inventoryWidget.getDynamicChildren();
                            if (children == null || potionSlot >= children.length) {
                                log.debug("[ACTION] Cannot drink potion - no children widgets or slot out of bounds");
                                return;
                            }

                            Widget potionWidget = children[potionSlot];
                            if (potionWidget == null || potionWidget.getItemId() != potionId) {
                                log.debug("[ACTION] Cannot drink potion - widget not found or item mismatch at slot {}", potionSlot);
                                return;
                            }

                            // Now send the packets directly with the validated widget (EXACT SAME AS FOOD)
                            MousePackets.queueClickPacket();
                            WidgetPackets.queueWidgetAction(potionWidget, "Drink");
                            log.debug("[ACTION] Consumed potion {} at slot {} via PacketUtils", potionId, potionSlot);

                        } catch (Exception e) {
                            log.error("[ACTION] Failed to consume potion", e);
                        }
                    });
                    return; // Drink only one potion at a time
                }
            }
        }
        log.debug("[ACTION] No potion of requested type found");
    }

    private boolean isPotionType(int itemId, int potionAction) {
        switch (potionAction) {
            case 1: // Saradomin brew - IDs from gameval: 6691, 6689, 6687, 6685
                return itemId == ItemID._1DOSEPOTIONOFSARADOMIN || itemId == ItemID._2DOSEPOTIONOFSARADOMIN ||
                       itemId == ItemID._3DOSEPOTIONOFSARADOMIN || itemId == ItemID._4DOSEPOTIONOFSARADOMIN;
            case 2: // Super restore - IDs from gameval: 3030, 3028, 3026, 3024
                return itemId == ItemID._1DOSE2RESTORE || itemId == ItemID._2DOSE2RESTORE ||
                       itemId == ItemID._3DOSE2RESTORE || itemId == ItemID._4DOSE2RESTORE;
            case 3: // Combat potion (super combat) - IDs from gameval: 12701, 12699, 12697, 12695
                return itemId == ItemID._1DOSE2COMBAT || itemId == ItemID._2DOSE2COMBAT ||
                       itemId == ItemID._3DOSE2COMBAT || itemId == ItemID._4DOSE2COMBAT;
            case 4: // Ranging potion - IDs from gameval: 173, 171, 169, 2444
                return itemId == ItemID._1DOSERANGERSPOTION || itemId == ItemID._2DOSERANGERSPOTION ||
                       itemId == ItemID._3DOSERANGERSPOTION || itemId == ItemID._4DOSERANGERSPOTION;
            default:
                return false;
        }
    }

    private void handleKarambwan() {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot eat karambwan - PacketUtils not ready");
            return;
        }

        // Karambwans are special combo food
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            Item[] items = inventory.getItems();
            for (int slot = 0; slot < items.length; slot++) {
                Item item = items[slot];
                if (item != null && item.getId() == ItemID.TBWT_COOKED_KARAMBWAN) {
                    final int karambwanId = item.getId();
                    final int karambwanSlot = slot;

                    // Execute ENTIRE packet operation on client thread with full validation
                    clientThread.invoke(() -> {
                        try {
                            // Re-validate item still exists at this slot on client thread
                            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
                            if (inv == null) {
                                log.debug("[ACTION] Cannot eat karambwan - inventory null on client thread");
                                return;
                            }

                            Item[] currentItems = inv.getItems();
                            if (karambwanSlot >= currentItems.length) {
                                log.debug("[ACTION] Cannot eat karambwan - slot {} out of bounds", karambwanSlot);
                                return;
                            }

                            Item currentItem = currentItems[karambwanSlot];
                            if (currentItem == null || currentItem.getId() != karambwanId) {
                                log.debug("[ACTION] Cannot eat karambwan - item changed or moved");
                                return;
                            }

                            // Get the widget directly to ensure it's valid
                            Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
                            if (inventoryWidget == null) {
                                log.debug("[ACTION] Cannot eat karambwan - inventory widget null");
                                return;
                            }

                            Widget[] children = inventoryWidget.getDynamicChildren();
                            if (children == null || karambwanSlot >= children.length) {
                                log.debug("[ACTION] Cannot eat karambwan - no children widgets");
                                return;
                            }

                            Widget karambwanWidget = children[karambwanSlot];
                            if (karambwanWidget == null || karambwanWidget.getItemId() != karambwanId) {
                                log.debug("[ACTION] Cannot eat karambwan - widget not found");
                                return;
                            }

                            // Now send the packets directly with the validated widget
                            MousePackets.queueClickPacket();
                            WidgetPackets.queueWidgetAction(karambwanWidget, "Eat");
                            log.debug("[ACTION] Consumed karambwan via PacketUtils combo food");

                        } catch (Exception e) {
                            log.error("[ACTION] Failed to consume karambwan", e);
                        }
                    });
                    return;
                }
            }
        }
        log.debug("[ACTION] No karambwan found in inventory");
    }

    private void handleTankGear() {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot equip tank gear - PacketUtils not ready");
            return;
        }

        // Get tank gear IDs from the environment's loadout
        int[] tankGearIds = environmentBridge.getTankGear();

        if (tankGearIds == null || tankGearIds.length == 0) {
            log.debug("[ACTION] No tank gear configured in loadout");
            return;
        }

        // Find tank gear items and equip them
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            Item[] items = inventory.getItems();
            for (int slot = 0; slot < items.length; slot++) {
                Item item = items[slot];
                if (item != null && isTankGear(item.getId(), tankGearIds)) {
                    final int gearId = item.getId();
                    final int gearSlot = slot;

                    // Execute ENTIRE packet operation on client thread with full validation
                    clientThread.invoke(() -> {
                        try {
                            // Re-validate item still exists at this slot on client thread
                            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
                            if (inv == null) {
                                log.debug("[ACTION] Cannot equip tank gear - inventory null on client thread");
                                return;
                            }

                            Item[] currentItems = inv.getItems();
                            if (gearSlot >= currentItems.length) {
                                log.debug("[ACTION] Cannot equip tank gear - slot {} out of bounds", gearSlot);
                                return;
                            }

                            Item currentItem = currentItems[gearSlot];
                            if (currentItem == null || currentItem.getId() != gearId) {
                                log.debug("[ACTION] Cannot equip tank gear - item changed or moved");
                                return;
                            }

                            // Get the widget directly to ensure it's valid
                            Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
                            if (inventoryWidget == null) {
                                log.debug("[ACTION] Cannot equip tank gear - inventory widget null");
                                return;
                            }

                            Widget[] children = inventoryWidget.getDynamicChildren();
                            if (children == null || gearSlot >= children.length) {
                                log.debug("[ACTION] Cannot equip tank gear - no children widgets");
                                return;
                            }

                            Widget gearWidget = children[gearSlot];
                            if (gearWidget == null || gearWidget.getItemId() != gearId) {
                                log.debug("[ACTION] Cannot equip tank gear - widget not found");
                                return;
                            }

                            // Now send the packets directly with the validated widget
                            MousePackets.queueClickPacket();
                            WidgetPackets.queueWidgetAction(gearWidget, "Wear", "Wield");
                            log.debug("[ACTION] Equipped tank gear item {} via PacketUtils", gearId);

                        } catch (Exception e) {
                            log.error("[ACTION] Failed to equip tank gear", e);
                        }
                    });
                }
            }
        }
    }

    private boolean isTankGear(int itemId, int[] tankGearIds) {
        for (int tankId : tankGearIds) {
            if (itemId == tankId) {
                return true;
            }
        }
        return false;
    }

    private void handleVengeance(int vengAction) {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot cast vengeance - PacketUtils not ready");
            return;
        }

        if (vengAction == 0) {
            return; // Don't cast vengeance
        }

        // Cast vengeance spell using PacketUtils WidgetPackets
        // MUST run on client thread to avoid disconnection
        clientThread.invoke(() -> {
            try {
                // Verify spellbook is on Lunar
                int spellbook = client.getVarbitValue(Varbits.SPELLBOOK);
                if (spellbook != 2) { // 2 = Lunar
                    log.debug("[ACTION] Cannot cast vengeance - not on Lunar spellbook");
                    return;
                }

                // Citation: InterfaceID.MagicSpellbook.VENGEANCE from runelite-api
                int widgetId = MagicSpellbook.VENGEANCE; // 0x00da008e
                int childId = widgetId & 0xFFFF; // Extract child (0x008e = 142)
                int groupId = widgetId >> 16; // Extract group (0x00da = 218)

                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetActionPacket(1, groupId << 16 | childId, -1, childId);
                log.debug("[ACTION] Cast Vengeance spell via PacketUtils");

            } catch (Exception e) {
                log.error("[ACTION] Failed to cast vengeance", e);
            }
        });
    }

    private void handleAttack(int attackType, int meleeType, int rangedType, int mageType) {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot attack - PacketUtils not ready");
            return;
        }

        if (attackType == 0) {
            return; // No attack
        }

        String attackDesc = "";
        boolean needsSpecial = false;

        // Prepare attack based on type
        switch (attackType) {
            case 1: // Mage attack
                switch (mageType) {
                    case 1: // Ice barrage
                        clientThread.invoke(() -> {
                            try {
                                int iceBarrageWidget = MagicSpellbook.ICE_BARRAGE; // 0x00da0052
                                int iceChildId = iceBarrageWidget & 0xFFFF; // 0x0052 = 82
                                int iceGroupId = iceBarrageWidget >> 16; // 0x00da = 218
                                MousePackets.queueClickPacket();
                                WidgetPackets.queueWidgetActionPacket(1, iceGroupId << 16 | iceChildId, -1, iceChildId);
                                log.debug("[ACTION] Selected Ice Barrage spell");
                            } catch (Exception e) {
                                log.error("[ACTION] Failed to select ice barrage", e);
                            }
                        });
                        attackDesc = "Ice barrage";
                        break;

                    case 2: // Blood barrage
                        clientThread.invoke(() -> {
                            try {
                                int bloodBarrageWidget = MagicSpellbook.BLOOD_BARRAGE; // 0x00da0056
                                int bloodChildId = bloodBarrageWidget & 0xFFFF; // 0x0056 = 86
                                int bloodGroupId = bloodBarrageWidget >> 16; // 0x00da = 218
                                MousePackets.queueClickPacket();
                                WidgetPackets.queueWidgetActionPacket(1, bloodGroupId << 16 | bloodChildId, -1, bloodChildId);
                                log.debug("[ACTION] Selected Blood Barrage spell");
                            } catch (Exception e) {
                                log.error("[ACTION] Failed to select blood barrage", e);
                            }
                        });
                        attackDesc = "Blood barrage";
                        break;

                    case 3:
                        attackDesc = "Mage special";
                        needsSpecial = true;
                        break;
                }
                break;

            case 2: // Ranged attack
                if (rangedType == 2) {
                    attackDesc = "Ranged special";
                    needsSpecial = true;
                } else {
                    attackDesc = "Ranged normal";
                }
                break;

            case 3: // Melee attack
                if (meleeType == 2) {
                    attackDesc = "Melee special";
                    needsSpecial = true;
                } else {
                    attackDesc = "Melee normal";
                }
                break;
        }

        if (!attackDesc.isEmpty()) {
            // Toggle special attack if needed
            if (needsSpecial) {
                toggleSpecialAttack();
            }

            // Execute the actual attack on the target
            net.runelite.api.Player localPlayer = client.getLocalPlayer();
            if (localPlayer != null) {
                Actor target = localPlayer.getInteracting();
                if (target instanceof Player) {
                    Player playerTarget = (Player) target;
                    final String attack = attackDesc;

                    // Execute attack on client thread
                    clientThread.invoke(() -> {
                        try {
                            PlayerInteractionHelper.interact(playerTarget, "Attack");
                            log.debug("[ACTION] Executed {} on player: {} via PacketUtils", attack, playerTarget.getName());
                        } catch (Exception e) {
                            log.error("[ACTION] Failed to attack player", e);
                        }
                    });
                } else {
                    log.debug("[ACTION] No valid player target for attack: {}", attackDesc);
                }
            } else {
                log.debug("[ACTION] Cannot attack - local player is null");
            }
        }
    }

    /**
     * Toggle special attack using the spec orb.
     * MUST run on client thread to avoid disconnection.
     */
    private void toggleSpecialAttack() {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot toggle special attack - PacketUtils not ready");
            return;
        }

        clientThread.invoke(() -> {
            try {
                // Check if we have special energy first
                int specEnergy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
                if (specEnergy < 250) { // Special is stored as value * 10 (25% = 250)
                    log.debug("[ACTION] Cannot toggle special - insufficient energy: {}%", specEnergy / 10);
                    // Try anyway for testing purposes
                }

                // Try using the combat options special attack button first
                // From manual testing: Widget ID 38862886 (group:593, child:38)
                try {
                    int COMBAT_OPTIONS_GROUP = 593;
                    int SPECIAL_ATTACK_CLICKBOX = 38;  // Changed from 41 to 38 based on manual testing

                    // Check if combat options tab is open
                    Widget combatWidget = client.getWidget(COMBAT_OPTIONS_GROUP, SPECIAL_ATTACK_CLICKBOX);
                    if (combatWidget != null && !combatWidget.isHidden()) {
                        // Use queueWidgetActionPacket directly with action 1 (like InteractionHelper does for toggles)
                        int packedWidgetId = (COMBAT_OPTIONS_GROUP << 16) | SPECIAL_ATTACK_CLICKBOX;
                        MousePackets.queueClickPacket();
                        WidgetPackets.queueWidgetActionPacket(1, packedWidgetId, -1, -1);
                        log.debug("[ACTION] Toggled special attack via combat options widget (593:38)");
                        return;
                    } else {
                        log.debug("[ACTION] Combat widget not available - widget null: {}, hidden: {}",
                            combatWidget == null, combatWidget != null ? combatWidget.isHidden() : "N/A");
                    }
                } catch (Exception combatEx) {
                    log.debug("[ACTION] Combat options special attack failed: {}", combatEx.getMessage());
                }

                // Fallback to minimap spec orb
                // From manual testing: Widget ID 10485795 (group:160, child:35)
                int MINIMAP_GROUP = 160;
                int SPEC_ORB_CHILD = 35;
                int packedSpecOrb = (MINIMAP_GROUP << 16) | SPEC_ORB_CHILD;

                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetActionPacket(1, packedSpecOrb, -1, -1);
                log.debug("[ACTION] Toggled special attack via minimap spec orb (160:35)");

            } catch (Exception e) {
                log.error("[ACTION] Failed to toggle special attack", e);
            }
        });
    }

    private void handleMovement(int moveAction, int farcastDistance) {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot move - PacketUtils not ready");
            return;
        }

        if (moveAction == 0) {
            log.debug("[ACTION] No movement requested (moveAction = 0)");
            return; // No movement
        }

        log.debug("[ACTION] Processing movement action {} with farcast distance {}", moveAction, farcastDistance);

        // Calculate destination based on movement type
        WorldPoint destination = calculateDestination(moveAction, farcastDistance);

        if (destination != null) {
            // Execute movement on client thread
            clientThread.invoke(() -> {
                try {
                    // Convert world coordinates to scene coordinates
                    Scene scene = client.getScene();
                    if (scene == null) {
                        log.debug("[ACTION] Cannot move - scene is null");
                        return;
                    }

                    int sceneX = destination.getX() - scene.getBaseX();
                    int sceneY = destination.getY() - scene.getBaseY();

                    // Validate scene coordinates are in bounds
                    if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) {
                        log.debug("[ACTION] Cannot move - destination out of scene bounds");
                        return;
                    }

                    // Convert back to world coordinates for MovementPackets
                    int worldX = scene.getBaseX() + sceneX;
                    int worldY = scene.getBaseY() + sceneY;

                    MovementPackets.queueMovement(worldX, worldY, false);

                    String moveDesc = "";
                    switch (moveAction) {
                        case 1:
                            moveDesc = "Move adjacent to target";
                            break;
                        case 2:
                            moveDesc = "Move under target";
                            break;
                        case 3:
                            moveDesc = "Move to farcast position (" + (farcastDistance + 1) + " tiles)";
                            break;
                        case 4:
                            moveDesc = "Move diagonal to target";
                            break;
                    }

                    log.debug("[ACTION] {} to ({}, {})", moveDesc, destination.getX(), destination.getY());

                } catch (Exception e) {
                    log.error("[ACTION] Failed to execute movement", e);
                }
            });
        } else {
            log.debug("[ACTION] Cannot move - no valid destination calculated");
        }
    }

    private WorldPoint calculateDestination(int moveAction, int farcastDistance) {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return null;
        }

        WorldPoint playerPos = localPlayer.getWorldLocation();
        Actor target = localPlayer.getInteracting();

        // For test mode or when no target, use absolute movement based on player position
        if (!(target instanceof Player)) {
            // Test mode movement - move relative to player's current position
            switch (moveAction) {
                case 1: // Adjacent - move 1 tile north for testing
                    return new WorldPoint(playerPos.getX(), playerPos.getY() + 1, playerPos.getPlane());

                case 2: // Under - move 2 tiles east for testing
                    return new WorldPoint(playerPos.getX() + 2, playerPos.getY(), playerPos.getPlane());

                case 3: // Farcast - move north by farcastDistance+1 tiles
                    return new WorldPoint(playerPos.getX(), playerPos.getY() + (farcastDistance + 1), playerPos.getPlane());

                case 4: // Diagonal - move west by farcastDistance+1 tiles
                    return new WorldPoint(playerPos.getX() - (farcastDistance + 1), playerPos.getY(), playerPos.getPlane());

                default:
                    return null;
            }
        }

        // Normal PvP mode with target
        WorldPoint targetPos = target.getWorldLocation();

        int dx = targetPos.getX() - playerPos.getX();
        int dy = targetPos.getY() - playerPos.getY();

        switch (moveAction) {
            case 1: // Adjacent
                // Move to adjacent tile
                if (Math.abs(dx) > Math.abs(dy)) {
                    return new WorldPoint(targetPos.getX() - Integer.signum(dx), targetPos.getY(), playerPos.getPlane());
                } else {
                    return new WorldPoint(targetPos.getX(), targetPos.getY() - Integer.signum(dy), playerPos.getPlane());
                }

            case 2: // Under
                // Move to same tile as target
                return targetPos;

            case 3: // Farcast
                // Move to specified distance from target
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > 0) {
                    double scale = (farcastDistance + 1) / distance;
                    int newX = targetPos.getX() - (int)(dx * scale);
                    int newY = targetPos.getY() - (int)(dy * scale);
                    return new WorldPoint(newX, newY, playerPos.getPlane());
                }
                return playerPos;

            case 4: // Diagonal
                // Move diagonally relative to target
                int diagX = Integer.signum(dx);
                int diagY = Integer.signum(dy);
                return new WorldPoint(targetPos.getX() + diagX, targetPos.getY() + diagY, playerPos.getPlane());

            default:
                return null;
        }
    }

    private void handlePrayer(int prayerAction) {
        // Validate PacketUtils is ready
        if (!packetUtilsReady) {
            log.debug("[ACTION] Cannot change prayer - PacketUtils not ready");
            return;
        }

        if (prayerAction == 0) {
            return; // No prayer change
        }

        // Execute prayer change on client thread
        clientThread.invoke(() -> {
            try {
                // Prayer widget IDs from C:/dev/PacketUtils/src/main/java/com/example/PacketUtils/WidgetID.java
                final int PRAYER_GROUP_ID = 541;
                Prayer targetPrayer = null;
                int childId = -1;

                switch (prayerAction) {
                    case 1: // Protect from Magic (AI says "mage")
                        targetPrayer = Prayer.PROTECT_FROM_MAGIC;
                        childId = 21;
                        break;
                    case 2: // Protect from Missiles (AI says "ranged")
                        targetPrayer = Prayer.PROTECT_FROM_MISSILES;
                        childId = 22;
                        break;
                    case 3: // Protect from Melee (AI says "melee")
                        targetPrayer = Prayer.PROTECT_FROM_MELEE;
                        childId = 23;
                        break;
                    case 4: // Smite - Citation: NhEnvironment.java:1244
                        targetPrayer = Prayer.SMITE;
                        childId = 28;
                        break;
                    case 5: // Redemption - Citation: NhEnvironment.java:1246
                        targetPrayer = Prayer.REDEMPTION;
                        childId = 30;
                        break;
                    // Note: case 6 removed - doesn't exist in NhEnvironment (only actions 0-5)
                    default:
                        log.debug("[ACTION] Unknown prayer action: {}", prayerAction);
                        return;
                }

                // Check if we need to toggle the prayer
                boolean isActive = client.isPrayerActive(targetPrayer);

                // Always toggle the prayer since the AI decides when to activate/deactivate
                log.debug("[ACTION] Toggling {} prayer (currently {})", targetPrayer.name(), isActive ? "active" : "inactive");

                // Send the prayer toggle packet
                MousePackets.queueClickPacket();
                int packedId = (PRAYER_GROUP_ID << 16) | childId;
                WidgetPackets.queueWidgetActionPacket(1, packedId, -1, -1);

                log.debug("[ACTION] Sent prayer toggle packet for {} via PacketUtils", targetPrayer.name());

            } catch (Exception e) {
                log.error("[ACTION] Failed to change prayer", e);
            }
        });
    }
}