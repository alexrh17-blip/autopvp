package net.runelite.client.plugins.autopvp;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.VarPlayer;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
// Adapter imports
import net.runelite.client.plugins.autopvp.adapters.*;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.entity.impl.player.Player;
import net.runelite.client.plugins.autopvp.core.NhObservationEncoder;
import net.runelite.client.plugins.autopvp.core.NhActionMaskBuilder;
import net.runelite.client.plugins.autopvp.core.NhContract;
import net.runelite.client.plugins.autopvp.core.NhEnvironmentBridge;
import net.runelite.client.plugins.autopvp.core.ActionExecutor;
import net.runelite.client.plugins.autopvp.core.AIClient;
import net.runelite.client.plugins.autopvp.core.ItemDefinitionInitializer;
import net.runelite.client.plugins.autopvp.test.AutoPvPTester;
import net.runelite.client.plugins.autopvp.util.PacketUtilsInitializer;
import net.runelite.client.plugins.autopvp.util.ObservationLogger;
import javax.inject.Inject;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
/**
 * AutoPvP Plugin - RL agent for PvP automation.
 * Bridges Elvarg RSPS logic to RuneLite through adapters.
 */
@Slf4j
@PluginDescriptor(
    name = "AutoPvP",
    description = "Automated PvP using reinforcement learning",
    tags = {"pvp", "ml", "automation"}
)
public class AutoPvPPlugin extends Plugin
{
    private static final String[] ACTION_HEAD_NAMES = {
        "Attack",
        "Melee",
        "Ranged",
        "Mage",
        "Potion",
        "Food",
        "Karamb",
        "Venge",
        "Gear",
        "Move",
        "Distance",
        "Prayer"
    };
    @Inject
    private Client client;
    @Inject
    private EventBus eventBus;
    @Inject
    private AutoPvPConfig config;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ConfigManager configManager;
    @Inject
    private net.runelite.client.game.ItemManager itemManager;
    // Initialization state
    private boolean adaptersInitialized = false;
    // Core adapters (12-adapter system as per requirements)
    private PlayerAdapter playerAdapter;
    private LocationAdapter locationAdapter;
    private EquipmentAdapter equipmentAdapter;
    private InventoryAdapter inventoryAdapter;
    private CombatFactoryAdapter combatFactoryAdapter;
    private TimerManagerAdapter timerManagerAdapter;
    private DamageTrackerAdapter damageTrackerAdapter;
    private GearLoadoutTracker gearLoadoutTracker;
    private CombatHistoryTracker combatHistoryTracker;
    private PrayerHandlerAdapter prayerHandlerAdapter;
    private PotionConsumableAdapter potionConsumableAdapter;
    private FoodAdapter foodAdapter;
    private EventBridgeAdapter eventBridgeAdapter;
    private PathFinderAdapter pathFinderAdapter;
    // Support adapters
    private MovementQueueAdapter movementQueueAdapter;
    private SkillManagerAdapter skillManagerAdapter;
    private CombatAdapter combatAdapter;
    // Core integration components
    private NhEnvironmentBridge environmentBridge;
    private ActionExecutor actionExecutor;
    private AIClient aiClient;
    private ExecutorService executor;
    private long lastTickTime = 0;
    private long lastActionTime = 0;
    private boolean pluginEnabled = false;
    @Override
    protected void startUp()
    {
        log.info("[AUTOPVP] Starting AutoPvP plugin");
        // Initialize executor for async operations
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("AutoPvP-Worker");
            t.setDaemon(true);
            return t;
        });
        // Check if client is logged in before initializing adapters
        if (client.getGameState() == GameState.LOGGED_IN) {
            // Must run initialization on client thread to avoid thread safety issues
            clientThread.invokeLater(() -> {
                initializePlugin();
            });
        } else {
            // Defer initialization until logged in
            log.info("[AUTOPVP] Deferring initialization until client is logged in (current state: {})",
                     client.getGameState());
        }
        // Set enabled state from config
        pluginEnabled = config.enabled();
    }
    private void initializePlugin()
    {
        if (adaptersInitialized) {
            log.debug("[AUTOPVP] Adapters already initialized, skipping");
            return;
        }
        log.info("[AUTOPVP] Initializing adapters and environment");
        // Show initialization message to user
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] Initializing plugin components...", null);
        if (!verifyNatonClasspath()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] WARNING: Missing naton environment classes. Check shaded dependency.", null);
        }
        // Initialize ItemDefinition data before creating adapters
        // This is required for NhEnvironment to function properly
        // Initialize PacketUtils FIRST - this is CRITICAL for actions to work
        // Check if already initialized to skip expensive operation
        if (!PacketUtilsInitializer.isAlreadyInitialized()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Setting up packet injection (first time only, may take a few seconds)...", null);
        }
        PacketUtilsInitializer.initialize(client);
        ItemDefinitionInitializer.initialize(itemManager);
        // Initialize all adapters with proper dependencies
        initializeAdapters();
        // Initialize the environment bridge and action executor
        initializeEnvironmentBridge();
        // Initialize AI client
        initializeAIClient();
        adaptersInitialized = true;
        log.info("[AUTOPVP] All adapters and environment bridge initialized successfully");
        // Notify user that initialization is complete
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] Plugin ready! No freezes will occur during combat.", null);
    }
    @Override
    protected void shutDown()
    {
        log.info("[AUTOPVP] Shutting down AutoPvP plugin");
        // Shutdown adapters that need cleanup
        if (timerManagerAdapter != null) {
            timerManagerAdapter.shutdown();
        }
        if (damageTrackerAdapter != null) {
            damageTrackerAdapter.shutdown();
        }
        if (gearLoadoutTracker != null) {
            gearLoadoutTracker.shutdown();
        }
        if (combatHistoryTracker != null) {
            combatHistoryTracker.shutdown();
        }
        // CombatFactoryAdapter has no shutdown method (static utility class)
        if (potionConsumableAdapter != null) {
            potionConsumableAdapter.shutdown();
        }
        if (foodAdapter != null) {
            foodAdapter.shutdown();
        }
        if (eventBridgeAdapter != null) {
            eventBridgeAdapter.shutdown();
        }
        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
        // Clear adapter references
        clearAdapters();
        // Shutdown AI client
        if (aiClient != null) {
            aiClient.shutdown();
            aiClient = null;
        }
        // Clear bridge references
        environmentBridge = null;
        actionExecutor = null;
        // Reset initialization flag
        adaptersInitialized = false;
        log.info("[AUTOPVP] AutoPvP plugin stopped");
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN && !adaptersInitialized) {
            log.info("[AUTOPVP] Client logged in, initializing plugin");
            // onGameStateChanged runs on client thread already, so we can call directly
            initializePlugin();
        }
    }
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // Log all manual clicks for debugging
        MenuEntry entry = event.getMenuEntry();
        if (entry != null) {
            // Log widget interactions
            if (entry.getWidget() != null) {
                net.runelite.api.widgets.Widget widget = entry.getWidget();
                log.info("[MANUAL-ACTION] Widget click: {} on widget ID {} (group:{}, child:{}) at index {}",
                    entry.getOption(),
                    widget.getId(),
                    widget.getId() >>> 16,
                    widget.getId() & 0xFFFF,
                    entry.getParam0());
                // Special attention to spec orb clicks
                if (widget.getId() == net.runelite.api.widgets.WidgetInfo.MINIMAP_SPEC_ORB.getId()) {
                    log.info("[MANUAL-ACTION] *** SPECIAL ATTACK ORB CLICKED ***");
                }
                // Log combat tab interactions
                if ((widget.getId() >>> 16) == 593) { // Combat tab group ID
                    log.info("[MANUAL-ACTION] Combat tab interaction: widget child {}", widget.getId() & 0xFFFF);
                }
            }
            // Log inventory interactions
            if (entry.getItemId() > 0) {
                log.info("[MANUAL-ACTION] Item interaction: {} on item {} (slot {}) - ID: {}",
                    entry.getOption(),
                    entry.getTarget(),
                    entry.getParam0(),
                    entry.getItemId());
            }
            // Log NPC interactions
            if (entry.getNpc() != null) {
                log.info("[MANUAL-ACTION] NPC interaction: {} on {} (ID: {})",
                    entry.getOption(),
                    entry.getNpc().getName(),
                    entry.getNpc().getId());
            }
            // Log ground item interactions
            if (entry.getType().getId() >= 20 && entry.getType().getId() <= 25) {
                log.info("[MANUAL-ACTION] Ground item: {} on {} at tile ({}, {})",
                    entry.getOption(),
                    entry.getTarget(),
                    entry.getParam0(),
                    entry.getParam1());
            }
            // Log prayer interactions
            if (entry.getOption() != null && entry.getOption().startsWith("Activate") &&
                entry.getTarget() != null && entry.getTarget().contains("prayer")) {
                log.info("[MANUAL-ACTION] Prayer activation: {}", entry.getTarget());
            }
        }
    }
    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        // Track special attack energy changes
        int varbitId = event.getVarbitId();
        if (varbitId == 300) { // VarPlayer.SPECIAL_ATTACK_PERCENT
            int specPercent = event.getValue();
            log.info("[MANUAL-ACTION] Special attack energy changed to {}%", specPercent / 10);
        }
        // Track special attack enabled state
        if (varbitId == 301) { // VarPlayer.SPECIAL_ATTACK_ENABLED
            boolean enabled = event.getValue() == 1;
            log.info("[MANUAL-ACTION] Special attack {} (varbit {})",
                enabled ? "ENABLED" : "DISABLED", varbitId);
        }
        // Log other potentially interesting varbits
        if (varbitId == 357) { // Autocast spell
            log.info("[MANUAL-ACTION] Autocast spell changed to: {}", event.getValue());
        }
        // Log all varbit changes in debug mode for discovery
        if (config.debugMode()) {
            log.debug("[MANUAL-ACTION-DEBUG] Varbit {} changed to {}",
                varbitId, event.getValue());
        }
    }
    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        String command = event.getCommand().toLowerCase();
        if (command.equals("pvptest"))
        {
            String[] args = event.getArguments();
            String fullCommand = "::pvptest";
            if (args.length > 0)
            {
                fullCommand = "::pvptest " + String.join(" ", args).toLowerCase();
            }
            runTestCommand(fullCommand);
        }
        else if (command.equals("autopvpdebug"))
        {
            String[] args = event.getArguments();
            if (args.length > 0 && args[0].equalsIgnoreCase("target"))
            {
                dumpTargetDebug();
            }
        }
    }
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Only respond to player's own messages
        if (event.getType() != ChatMessageType.PUBLICCHAT &&
            event.getType() != ChatMessageType.PRIVATECHAT) {
            return;
        }
        String message = event.getMessage().toLowerCase();
        // Test commands for AutoPvP - kept for backwards compatibility
        // In case the CommandExecuted event doesn't fire
        if (message.startsWith("::pvptest")) {
            runTestCommand(message);
        } else if (message.startsWith("::autopvpdebug target")) {
            dumpTargetDebug();
        } else if (message.startsWith("::autopvpobs")) {
            dumpCurrentObservations();
        }
    }
    private void runTestCommand(String command)
    {
        if (!adaptersInitialized) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Plugin not initialized. Please log in first.", null);
            return;
        }
        try {
            if (command.equals("::pvptest all")) {
                // Run comprehensive test
                String results = AutoPvPTester.runAllTests(playerAdapter, environmentBridge);
                // Split into multiple messages due to chat length limits
                for (String line : results.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
                        log.info("[GAMECHAT] {}", line); // Also log to console
                    }
                }
            } else if (command.equals("::pvptest obs")) {
                // Test observations
                java.util.List<Number> observations = environmentBridge.getObservations();
                String summary = String.format("[AutoPvP] Observations: %d values, %d non-zero",
                    observations.size(),
                    observations.stream().filter(n -> n.doubleValue() != 0.0).count());
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", summary, null);
                log.info("[OBSERVATION TEST] {}", summary);
                // Show observation sanity check
                String sanity = AutoPvPTester.testObservationSanity(observations);
                log.info("[OBSERVATION TEST] Full validation output:\n{}", sanity);
                for (String line : sanity.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
                    }
                }
            } else if (command.equals("::pvptest masks")) {
                // Test action masks
                java.util.List<java.util.List<Boolean>> masks = environmentBridge.getActionMasks();
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    String.format("[AutoPvP] Action masks: %d heads", masks.size()), null);
                for (int i = 0; i < masks.size(); i++) {
                    long valid = masks.get(i).stream().filter(b -> b).count();
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        String.format("  Head %d: %d/%d valid", i, valid, masks.get(i).size()), null);
                }
            } else if (command.equals("::pvptest action")) {
                // Test complete action loop
                testActionLoop();
            } else if (command.equals("::pvptest fulltest") ||
                      command.equals("::pvptest full")) {
                // Test all actions comprehensively with 5-tick delays
                testAllActions();
            } else if (command.equals("::pvptest inv")) {
                // Debug inventory detection
                testInventory();
            } else if (command.equals("::pvptest status")) {
                // Show plugin status
                String statusMsg1 = String.format("[AutoPvP] Status: %s, AI: %s",
                    pluginEnabled ? "ENABLED" : "DISABLED",
                    aiClient != null && aiClient.isConnected() ? "CONNECTED" : "DISCONNECTED");
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", statusMsg1, null);
                log.info("[GAMECHAT] {}", statusMsg1);
                String statusMsg2 = String.format("[AutoPvP] HP: %d, Special: %d%%, Location: %s",
                    playerAdapter.getHitpoints(),
                    playerAdapter.getSpecialPercentage(),
                    playerAdapter.getLocation());
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", statusMsg2, null);
                log.info("[GAMECHAT] {}", statusMsg2);
            } else if (command.equals("::pvptest enable")) {
                // Enable the plugin
                pluginEnabled = true;
                String msg = "[AutoPvP] Plugin ENABLED";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
                log.info("[GAMECHAT] {}", msg);
                // Try to connect to AI if not connected
                if (aiClient != null && !aiClient.isConnected()) {
                    if (aiClient.connect()) {
                        String connMsg = "[AutoPvP] Connected to AI server";
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", connMsg, null);
                        log.info("[GAMECHAT] {}", connMsg);
                    }
                }
            } else if (command.equals("::pvptest disable")) {
                // Disable the plugin
                pluginEnabled = false;
                String msg = "[AutoPvP] Plugin DISABLED";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
                log.info("[GAMECHAT] {}", msg);
            } else if (command.equals("::pvptest toggle")) {
                // Toggle the plugin
                pluginEnabled = !pluginEnabled;
                String msg = String.format("[AutoPvP] Plugin %s",
                    pluginEnabled ? "ENABLED" : "DISABLED");
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
                log.info("[GAMECHAT] {}", msg);
                // Try to connect to AI if enabled and not connected
                if (pluginEnabled && aiClient != null && !aiClient.isConnected()) {
                    if (aiClient.connect()) {
                        String connMsg = "[AutoPvP] Connected to AI server";
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", connMsg, null);
                        log.info("[GAMECHAT] {}", connMsg);
                    }
                }
            } else if (command.equals("::pvptest help")) {
                // Show available commands
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[AutoPvP] Available test commands:", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest all - Run all tests", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest obs - Test observations", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest masks - Test action masks", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest action - Test complete action loop", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest fulltest - Test ALL actions with 5-tick delays", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest inv - Test inventory", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest status - Show plugin status", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest enable - Enable the plugin", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest disable - Disable the plugin", null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "  ::pvptest toggle - Toggle plugin on/off", null);
            } else {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[AutoPvP] Unknown test command. Use ::pvptest help", null);
            }
        } catch (Exception e) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Test error: " + e.getMessage(), null);
            log.error("[AUTOPVP] Test command error", e);
        }
    }
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("autopvp")) {
            return;
        }
        if (event.getKey().equals("enabled")) {
            boolean newEnabled = config.enabled();
            log.info("[AUTOPVP] Config changed: enabled = {}", newEnabled);
            if (newEnabled != pluginEnabled) {
                pluginEnabled = newEnabled;
                if (pluginEnabled) {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "[AutoPvP] Plugin ENABLED via config", null);
                } else {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "[AutoPvP] Plugin DISABLED via config", null);
                }
            }
        } else if (event.getKey().equals("loadoutOverride")) {
            LoadoutOverride override = config.loadoutOverride();
            log.info("[AUTOPVP] Config changed: loadoutOverride = {}", override);
            if (adaptersInitialized && playerAdapter != null) {
                clientThread.invokeLater(this::initializeEnvironmentBridge);
            }
        }
    }
    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Execute test sequence if running
        if (testRunning) {
            executeTestSequence();
            return; // Don't run AI while testing
        }
        // Only process ticks when plugin is enabled and initialized
        if (!config.enabled() || environmentBridge == null || !pluginEnabled) {
            return;
        }
        // Check if we have a real opponent (not just a dummy player)
        Actor interactingActor = client.getLocalPlayer() != null ? client.getLocalPlayer().getInteracting() : null;
        boolean hasRealTarget = interactingActor instanceof net.runelite.api.Player;
        net.runelite.api.Player targetPlayer = hasRealTarget ? (net.runelite.api.Player) interactingActor : null;
        // Only process AI if we have a real target
        if (!hasRealTarget) {
            // Log periodically to avoid spam
            if (client.getTickCount() % 30 == 0) {
                log.debug("[AUTOPVP] No real target, skipping AI processing");
            }
            return;
        }
        try {
            long tickStart = System.currentTimeMillis();
            // Update the environment bridge at tick start
            environmentBridge.onTickStart();
            // Get observations from the bridge (uses original NhEnvironment logic)
            java.util.List<Number> observations = environmentBridge.getObservations();

            if (hasRealTarget)
            {
                int tick = client.getTickCount();
                String targetName = targetPlayer != null ? targetPlayer.getName() : "none";
                String header = String.format(Locale.ROOT, "[AUTOPVP-OBS][tick=%d][target=%s]", tick, targetName);
                ObservationLogger.logInfoSnapshot(observations, header);
            }

            // Get action masks from the bridge
            java.util.List<java.util.List<Boolean>> actionMasks = environmentBridge.getActionMasks();
            // Log observation stats for debugging
            if (log.isDebugEnabled() && !observations.isEmpty()) {
                logBridgeObservationStats(observations, actionMasks, tickStart);
            }
            // Send observations to AI and get action if enabled
            if (pluginEnabled && config.enabled() && aiClient != null && environmentBridge != null) {
                requestAndExecuteAction(observations, actionMasks);
            }
            // Update the environment bridge at tick end
            if (environmentBridge != null) {
                environmentBridge.onTickProcessed();
                environmentBridge.onTickEnd();
            }
            if (gearLoadoutTracker != null) {
                gearLoadoutTracker.onTickEnd();
            }
            if (combatHistoryTracker != null) {
                combatHistoryTracker.onTickEnd();
            }
            // Track tick timing to ensure we meet 600ms deadline
            long tickDuration = System.currentTimeMillis() - tickStart;
            if (tickDuration > 100) {
                log.warn("[AUTOPVP] Tick processing took {}ms", tickDuration);
            }
            lastTickTime = tickStart;
        } catch (Exception e) {
            log.error("[AUTOPVP] Error in game tick handler: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("[AUTOPVP] Cause: {}", e.getCause().getMessage());
            }
        }
    }
    private void initializeAdapters()
    {
        try {
            // Create dummy player for Elvarg compatibility
            DummyElvargPlayer dummyPlayer = new DummyElvargPlayer();
            // Create PrayerHandlerAdapter first (needed by PlayerAdapter)
            // Source: Consolidates prayer state management to avoid desynchronization
            prayerHandlerAdapter = new PrayerHandlerAdapter(client, eventBus);
            // Initialize core adapters
            // PlayerAdapter now receives PrayerHandlerAdapter to delegate prayer state
            playerAdapter = new PlayerAdapter(client, eventBus, prayerHandlerAdapter, itemManager);
            locationAdapter = new LocationAdapter(client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : new net.runelite.api.coords.WorldPoint(0, 0, 0));
            equipmentAdapter = new EquipmentAdapter(client, dummyPlayer);
            inventoryAdapter = new InventoryAdapter(client, dummyPlayer);
            // Combat-related adapters
            combatFactoryAdapter = new CombatFactoryAdapter();
            timerManagerAdapter = new TimerManagerAdapter(client, eventBus);
            gearLoadoutTracker = new GearLoadoutTracker(client, eventBus, itemManager, timerManagerAdapter);
            combatHistoryTracker = new CombatHistoryTracker(client, eventBus, timerManagerAdapter);
            damageTrackerAdapter = new DamageTrackerAdapter(client, eventBus);
            // Action-related adapters (prayerHandlerAdapter already created above)
            potionConsumableAdapter = new PotionConsumableAdapter(client, eventBus, timerManagerAdapter);
            foodAdapter = new FoodAdapter(client, eventBus, timerManagerAdapter);
            // Utility adapters
            eventBridgeAdapter = new EventBridgeAdapter(client, eventBus);
            pathFinderAdapter = new PathFinderAdapter(client);
            movementQueueAdapter = new MovementQueueAdapter(client, dummyPlayer);
            // Skill manager with wrapper
            skillManagerAdapter = new SkillManagerAdapter(client, dummyPlayer);
            SkillManagerWrapperAdapter skillWrapper = new SkillManagerWrapperAdapter(client);
            log.debug("[AUTOPVP] All {} adapters initialized", 16);
        } catch (Exception e) {
            log.error("[AUTOPVP] Failed to initialize adapters", e);
            throw new RuntimeException("Failed to initialize AutoPvP adapters", e);
        }
    }
    private void clearAdapters()
    {
        playerAdapter = null;
        locationAdapter = null;
        equipmentAdapter = null;
        inventoryAdapter = null;
        combatFactoryAdapter = null;
        timerManagerAdapter = null;
        damageTrackerAdapter = null;
        gearLoadoutTracker = null;
        combatHistoryTracker = null;
        prayerHandlerAdapter = null;
        potionConsumableAdapter = null;
        foodAdapter = null;
        eventBridgeAdapter = null;
        pathFinderAdapter = null;
        movementQueueAdapter = null;
        skillManagerAdapter = null;
        combatAdapter = null;
    }
    private void initializeEnvironmentBridge()
    {
        try {
            // Create the environment bridge with our player adapter, combat adapter, and ItemManager for gear tracking
            // Cast to CombatAdapter since PlayerAdapter.getCombat() returns the generic Combat type
            CombatAdapter combatAdapter = (CombatAdapter) playerAdapter.getCombat();
            environmentBridge = new NhEnvironmentBridge(client, playerAdapter, combatAdapter, timerManagerAdapter, damageTrackerAdapter, itemManager, gearLoadoutTracker, combatHistoryTracker, config.loadoutOverride());
            // Wire TimerManagerAdapter to CombatAdapter so it can call processTimers() on targets
            timerManagerAdapter.setCombatAdapter(combatAdapter);
            // Create the action executor
            actionExecutor = new ActionExecutor(client, environmentBridge, clientThread);
            log.info("[AUTOPVP] NhEnvironmentBridge and ActionExecutor initialized");
        } catch (Exception e) {
            log.error("[AUTOPVP] Failed to initialize environment bridge", e);
        }
    }
    private void logBridgeObservationStats(java.util.List<Number> observations,
                                          java.util.List<java.util.List<Boolean>> actionMasks,
                                          long tickStart)
    {
        // Count non-zero observations
        long nonZeroObs = observations.stream()
            .filter(v -> v.doubleValue() != 0.0)
            .count();
        // Count valid actions across all heads
        long validActions = actionMasks.stream()
            .flatMap(java.util.List::stream)
            .filter(Boolean::booleanValue)
            .count();
        log.debug("[AUTOPVP] Tick {} - Obs: {}/{} non-zero, Actions: {} valid, Time: {}ms",
            client.getTickCount(),
            nonZeroObs,
            NhContract.OBS_SIZE,
            validActions,
            System.currentTimeMillis() - tickStart
        );
    }
    private void logObservationStats(float[] observation, boolean[] actionMask, long tickStart)
    {
        // Count non-zero observations
        long nonZeroObs = Arrays.stream(floatArrayToDoubleArray(observation))
            .filter(v -> v != 0.0)
            .count();
        // Count valid actions
        long validActions = 0;
        for (boolean valid : actionMask) {
            if (valid) validActions++;
        }
        log.debug("[AUTOPVP] Tick {} - Obs: {}/{} non-zero, Actions: {}/{} valid, Time: {}ms",
            client.getTickCount(),
            nonZeroObs,
            NhContract.OBS_SIZE,
            validActions,
            NhContract.ACTION_FLAT_SIZE,
            System.currentTimeMillis() - tickStart
        );
    }
    private void initializeAIClient()
    {
        try {
            String host = config.serverHost();
            int port = config.serverPort();
            aiClient = new AIClient(host, port);
            // Try to connect if auto-connect is enabled
            if (config.enabled() && config.autoReconnect()) {
                aiClient.connect();
            }
            log.info("[AUTOPVP] AI client initialized for {}:{}", host, port);
        } catch (Exception e) {
            log.error("[AUTOPVP] Failed to initialize AI client", e);
        }
    }
    private void requestAndExecuteAction(java.util.List<Number> observations,
                                        java.util.List<java.util.List<Boolean>> actionMasks)
    {
        // Check action delay
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < config.actionDelay()) {
            return;
        }
        // Request action from AI asynchronously
        final int tickSnapshot = client.getTickCount();
        final String targetNameSnapshot = client.getLocalPlayer() != null &&
            client.getLocalPlayer().getInteracting() instanceof net.runelite.api.Player
            ? ((net.runelite.api.Player) client.getLocalPlayer().getInteracting()).getName()
            : "none";
        CompletableFuture<int[]> actionFuture = aiClient.requestActionAsync(
            client.getTickCount(),
            0.0f, // No reward signal for now
            observations,
            actionMasks
        );
        // Handle the action when it arrives
        actionFuture.thenAccept(action -> {
            if (action != null && actionExecutor != null) {
                // Check if action is all zeros (PPO server failure)
                // Citation: AIClient.java:321-322 returns getDefaultAction() on IOException
                boolean allZeros = java.util.Arrays.stream(action).allMatch(a -> a == 0);
                if (allZeros) {
                    log.error("[AUTOPVP] AI server returned all zeros - connection failed");
                    return;
                }
                // Double-check we still have a real opponent when action arrives
                if (!hasRealOpponent()) {
                    log.debug("[AUTOPVP] Action received but no real opponent, skipping execution");
                    return;
                }
                // Execute the action in the game on the client thread
                if (!config.safeMode() || isSafeAction(action)) {
                    // Log detailed action info
                    log.info("[AUTOPVP-ACTION] Executing action from AI:");
                    logActionDetails(tickSnapshot, targetNameSnapshot, action);
                    // Execute on client thread to avoid thread safety issues
                    clientThread.invoke(() -> {
                        // Final check on client thread
                        if (!hasRealOpponent()) {
                            log.debug("[AUTOPVP] Action execution cancelled - no real opponent");
                            return;
                        }
                        actionExecutor.executeAction(action);
                        lastActionTime = System.currentTimeMillis();
                        if (config.debugMode()) {
                            log.debug("[AUTOPVP] Executed action: {}", Arrays.toString(action));
                        }
                    });
                }
            }
        }).exceptionally(ex -> {
            if (config.debugMode()) {
                log.error("[AUTOPVP] Failed to get action from AI", ex);
            }
            return null;
        });
    }
    private void logActionDetails(int tick, String targetName, int[] action)
    {
        if (action == null || action.length != ACTION_HEAD_NAMES.length)
        {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("[AUTOPVP-ACTION][tick=").append(tick)
            .append("][target=").append(targetName).append("] Heads: ");
        for (int i = 0; i < action.length; i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append('[').append(i).append(']').append(ACTION_HEAD_NAMES[i]).append('=').append(action[i]);
        }
        log.info(sb.toString());
        if (log.isDebugEnabled())
        {
            log.debug("[AUTOPVP-ACTION] raw={}", Arrays.toString(action));
        }
    }
    private boolean isSafeAction(int[] action)
    {
        // Check if we have a real opponent or just the dummy
        boolean hasRealOpponent = hasRealOpponent();
        // In safe mode, only allow non-attack actions
        // Action[0] is attack type: 0=none, 1=mage, 2=ranged, 3=melee
        boolean noAttack = action[0] == 0;
        // If no real opponent, also block prayer switching to prevent flicking against dummy
        // Action[11] is prayer: 0=none, 1=mage, 2=ranged, 3=melee, 4=smite, 5=redemption
        if (!hasRealOpponent && action[11] != 0) {
            return false; // Block prayer actions when there's no real opponent
        }
        return noAttack;
    }
    private boolean hasRealOpponent()
    {
        // Check if local player is interacting with another player
        if (client.getLocalPlayer() == null) {
            return false;
        }
        net.runelite.api.Actor interacting = client.getLocalPlayer().getInteracting();
        // We have a real opponent if we're interacting with another player
        return interacting instanceof net.runelite.api.Player;
    }
    private void testInventory() {
        try {
            // Test inventory adapter
            var inventory = playerAdapter.getInventory();
            String msg = String.format("[AutoPvP] Inventory: %d free slots, %s",
                inventory.getFreeSlots(),
                inventory.isEmpty() ? "empty" : "has items");
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
            log.info("[GAMECHAT] {}", msg);
            // Check for specific items
            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
            if (inv != null) {
                net.runelite.api.Item[] items = inv.getItems();
                int itemCount = 0;
                for (net.runelite.api.Item item : items) {
                    if (item != null && item.getId() > 0) {
                        itemCount++;
                        // Log first few items
                        if (itemCount <= 5) {
                            String itemMsg = String.format("  Item %d: ID=%d, Qty=%d",
                                itemCount, item.getId(), item.getQuantity());
                            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", itemMsg, null);
                            log.info("[GAMECHAT] {}", itemMsg);
                        }
                    }
                }
                String totalMsg = String.format("[AutoPvP] Total items: %d", itemCount);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", totalMsg, null);
                log.info("[GAMECHAT] {}", totalMsg);
            }
            // Check what NhEnvironment sees
            var masks = environmentBridge.getActionMasks();
            if (masks.size() > 4) {
                // Head 4 is potions, Head 5 is food
                String potionMsg = String.format("[AutoPvP] Potion actions: %d valid",
                    masks.get(4).stream().filter(b -> b).count());
                String foodMsg = String.format("[AutoPvP] Food actions: %d valid",
                    masks.get(5).stream().filter(b -> b).count());
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", potionMsg, null);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", foodMsg, null);
                log.info("[GAMECHAT] {}", potionMsg);
                log.info("[GAMECHAT] {}", foodMsg);
            }
        } catch (Exception e) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Inventory test error: " + e.getMessage(), null);
            log.error("[AUTOPVP] Inventory test failed", e);
        }
    }
    /**
     * Comprehensive test of all actions with proper timing.
     * Each action executes 5 ticks apart to ensure smooth testing.
     */
    private void testAllActions() {
        if (actionExecutor == null || !adaptersInitialized) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Plugin not initialized. Please log in first.", null);
            return;
        }
        // Disable AI temporarily during test
        boolean wasEnabled = pluginEnabled;
        pluginEnabled = false;
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] Starting comprehensive action test - each action 5 ticks apart", null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] AI disabled during test for safety", null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] Required inventory items:", null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "  - Shark, Anglerfish, Manta ray, Cooked karambwan", null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "  - Super restore (any dose), Super combat (any dose)", null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "  - AGS (Armadyl godsword)", null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "  - Tank gear items configured in loadout", null);
        // Initialize test sequence counter
        testSequenceCounter = 0;
        testRunning = true;
        ticksSinceLastAction = 5; // Start immediately
    }
    private int testSequenceCounter = 0;
    private boolean testRunning = false;
    private int ticksSinceLastAction = 0;
    /**
     * Execute test actions in sequence with 5-tick delays
     */
    private void executeTestSequence() {
        if (!testRunning || actionExecutor == null) return;
        ticksSinceLastAction++;
        if (ticksSinceLastAction < 5) return; // Wait 5 ticks between actions
        ticksSinceLastAction = 0;
        // Create action array with all zeros except the one we're testing
        int[] action = new int[12];
        String actionDesc = "";
        switch(testSequenceCounter) {
            // GEAR TESTS - Equip each piece of gear
            case 0:
                action[8] = 1; // Gear switch to tank gear
                actionDesc = "Equipping tank gear (all slots)";
                break;
            // FOOD TESTS
            case 1:
                action[5] = 1; // Eat food (will eat shark/manta/angler in order found)
                actionDesc = "Eating shark";
                break;
            case 2:
                action[5] = 1; // Eat food again
                actionDesc = "Eating anglerfish";
                break;
            case 3:
                action[5] = 1; // Eat food again
                actionDesc = "Eating manta ray";
                break;
            case 4:
                action[6] = 1; // Eat karambwan
                actionDesc = "Eating karambwan";
                break;
            // POTION TESTS
            case 5:
                action[4] = 2; // Drink super restore
                actionDesc = "Drinking super restore";
                break;
            case 6:
                action[4] = 3; // Drink super combat
                actionDesc = "Drinking super combat";
                break;
            // MOVEMENT TESTS
            case 7:
                action[9] = 3; // Farcast movement
                action[10] = 4; // 5 tiles distance
                actionDesc = "Walking 5 tiles north";
                break;
            case 8:
                action[9] = 4; // Diagonal movement
                action[10] = 6; // max distance for diagonal west movement
                actionDesc = "Walking 10 tiles west";
                break;
            // PRAYER TESTS
            case 9:
                action[11] = 3; // Protect from Melee
                actionDesc = "Activating Protect from Melee";
                break;
            case 10:
                action[11] = 2; // Protect from Missiles
                actionDesc = "Switching to Protect from Missiles";
                break;
            case 11:
                action[11] = 1; // Protect from Magic
                actionDesc = "Switching to Protect from Magic";
                break;
            case 12:
                // Turn off all prayers manually
                actionDesc = "Turning off all prayers";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    String.format("[AutoPvP] Test %d: %s", testSequenceCounter + 1, actionDesc), null);
                if (client.getLocalPlayer() != null) {
                    for (net.runelite.api.Prayer prayer : net.runelite.api.Prayer.values()) {
                        if (client.isPrayerActive(prayer)) {
                            com.example.InteractionApi.PrayerInteraction.setPrayerState(prayer, false);
                        }
                    }
                }
                testSequenceCounter++;
                return; // Skip normal execution
            // EQUIP AGS BEFORE SPECIAL TEST
            case 13:
                // Manually equip AGS from inventory
                actionDesc = "Equipping AGS for special attack test";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    String.format("[AutoPvP] Test %d: %s", testSequenceCounter + 1, actionDesc), null);
                // Find and equip AGS using PacketUtils
                try {
                    net.runelite.api.ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
                    if (inventory != null) {
                        net.runelite.api.Item[] items = inventory.getItems();
                        for (int slot = 0; slot < items.length; slot++) {
                            if (items[slot] != null && items[slot].getId() == ItemID.AGS) { // AGS ID
                                final int finalSlot = slot;
                                clientThread.invoke(() -> {
                                    net.runelite.api.widgets.Widget invWidget = client.getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY);
                                    if (invWidget != null && invWidget.getChildren() != null) {
                                        net.runelite.api.widgets.Widget itemWidget = invWidget.getChildren()[finalSlot];
                                        if (itemWidget != null) {
                                            com.example.Packets.MousePackets.queueClickPacket();
                                            com.example.Packets.WidgetPackets.queueWidgetAction(itemWidget, "Wield");
                                            log.info("[AUTOPVP] Equipped AGS from slot {}", finalSlot);
                                        }
                                    }
                                });
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[AUTOPVP] Failed to equip AGS", e);
                }
                testSequenceCounter++;
                ticksSinceLastAction = 0;
                return; // Skip normal action execution
            // SPECIAL ATTACK TEST
            case 14:
                action[0] = 3; // Melee attack
                action[1] = 2; // Melee special
                actionDesc = "Activating AGS special attack (toggles spec orb)";
                break;
            default:
                testRunning = false;
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[AutoPvP] Test sequence completed!", null);
                return;
        }
        // Execute the action
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            String.format("[AutoPvP] Test %d: %s", testSequenceCounter + 1, actionDesc), null);
        actionExecutor.executeAction(action);
        testSequenceCounter++;
    }
    private void testActionLoop()
    {
        try {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] === ACTION LOOP TEST ===", null);
            // Step 1: Collect observations
            long startTime = System.currentTimeMillis();
            java.util.List<Number> observations = environmentBridge.getObservations();
            long obsTime = System.currentTimeMillis() - startTime;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                String.format("[AutoPvP] Observations: %d values collected in %dms",
                    observations.size(), obsTime), null);
            // Step 2: Get action masks
            startTime = System.currentTimeMillis();
            java.util.List<java.util.List<Boolean>> masks = environmentBridge.getActionMasks();
            long maskTime = System.currentTimeMillis() - startTime;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                String.format("[AutoPvP] Action masks: %d heads generated in %dms",
                    masks.size(), maskTime), null);
            // Step 3: Test AI connection
            boolean aiConnected = aiClient != null && aiClient.isConnected();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                String.format("[AutoPvP] AI Server: %s",
                    aiConnected ? "CONNECTED (127.0.0.1:5557)" : "DISCONNECTED"), null);
            // Step 4: Generate a mock action
            int[] mockAction = new int[12];
            for (int i = 0; i < 12; i++) {
                // Find first valid action for this head
                if (i < masks.size()) {
                    java.util.List<Boolean> mask = masks.get(i);
                    for (int j = 0; j < mask.size(); j++) {
                        if (mask.get(j)) {
                            mockAction[i] = j;
                            break;
                        }
                    }
                }
            }
            // Step 5: Log what the action would do
            String mockMsg = "[AutoPvP] Mock action generated:";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", mockMsg, null);
            log.info("[GAMECHAT] {}", mockMsg); // Also log to console
            String[] actionNames = {
                "Attack", "Melee", "Ranged", "Mage",
                "Potion", "Food", "Karam", "Venge",
                "Gear", "Move", "Distance", "Prayer"
            };
            for (int i = 0; i < 12 && i < actionNames.length; i++) {
                if (i < masks.size() && !masks.get(i).isEmpty()) {
                    String actionMsg = String.format("  %s: %d", actionNames[i], mockAction[i]);
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", actionMsg, null);
                    log.info("[GAMECHAT] {}", actionMsg); // Also log to console
                }
            }
            // Step 6: Test execution (dry run)
            startTime = System.currentTimeMillis();
            if (actionExecutor != null) {
                // Use ActionExecutor which handles both NhEnvironment update and RuneLite actions
                actionExecutor.executeAction(mockAction);
            } else {
                log.warn("[AUTOPVP] ActionExecutor unavailable during test run; skipping action dispatch");
            }
            long execTime = System.currentTimeMillis() - startTime;
            String procMsg = String.format("[AutoPvP] Action processed in %dms", execTime);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", procMsg, null);
            log.info("[GAMECHAT] {}", procMsg);
            // Step 7: Total loop time
            long totalTime = obsTime + maskTime + execTime;
            String totalMsg = String.format("[AutoPvP] Total loop: %dms (%s 600ms deadline)",
                totalTime, totalTime < 600 ? "PASS" : "FAIL");
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", totalMsg, null);
            log.info("[GAMECHAT] {}", totalMsg);
            // Log to file for debugging
            log.info("[AUTOPVP-TEST] Action loop test completed: obs={}ms, mask={}ms, exec={}ms, total={}ms",
                obsTime, maskTime, execTime, totalTime);
        } catch (Exception e) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Test failed: " + e.getMessage(), null);
            log.error("[AUTOPVP] Action loop test failed", e);
        }
    }
    private void dumpTargetDebug()
    {
        if (environmentBridge == null) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[AutoPvP] Target debug unavailable (bridge offline)", null);
            return;
        }
        DynamicTargetPlayer dynamicTarget = environmentBridge.getDynamicTargetPlayer();
        if (dynamicTarget == null) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[AutoPvP] Target debug unavailable (no dynamic target)", null);
            return;
        }
        int[] itemIds = dynamicTarget.getTargetEquipmentItemIds();
        double[] bonuses = dynamicTarget.getTargetGearBonuses();
        double confidence = dynamicTarget.getTargetEquipmentConfidence();
        int specEstimate = dynamicTarget.getEstimatedSpecialPercent();
        boolean lightbearer = dynamicTarget.hasLightbearerObserved();
        String spellbook = dynamicTarget.getDetectedSpellbook();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            String.format("[AutoPvP] Target: confidence=%.2f spec=%d%% lightbearer=%s spellbook=%s",
                confidence, specEstimate, lightbearer, spellbook), null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] Target item ids: " + Arrays.toString(itemIds), null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[AutoPvP] Target bonuses: " + Arrays.toString(bonuses), null);
        if (combatAdapter != null && combatAdapter.getTarget() instanceof OpponentElvargPlayer) {
            OpponentElvargPlayer opponent = (OpponentElvargPlayer) combatAdapter.getTarget();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Opponent confidence: " + opponent.getEquipmentConfidence(), null);
        }
    }
    private double[] floatArrayToDoubleArray(float[] input)
    {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    private void logDetailedObservations(java.util.List<Number> observations, Actor target)
    {
        if (observations == null || observations.size() != 176) {
            log.error("[OBS] Invalid observations: {} values", observations != null ? observations.size() : "null");
            return;
        }
        net.runelite.api.Player targetPlayer = (net.runelite.api.Player) target;
        log.info("[OBS] ===== OBSERVATIONS FOR: {} =====", targetPlayer.getName());
        // Basic stats
        double ourHp = observations.get(13).doubleValue();
        double oppHp = observations.get(14).doubleValue();
        log.info("[OBS] HP - Us: {}%, Opp: {}%", String.format("%.1f", ourHp * 100), String.format("%.1f", oppHp * 100));
        log.info("[OBS] Distance: {}, Our Spec: {}%, Opp Spec: {}%",
            observations.get(0), observations.get(1).intValue(), observations.get(2).intValue());
        // Opponent prayers
        StringBuilder oppPrayers = new StringBuilder("[OBS] Opp Prayers: ");
        int prayerCount = 0;
        for (int i = 15; i <= 43; i++) {
            if (observations.get(i).doubleValue() > 0) {
                prayerCount++;
                String prayerName = getPrayerName(i - 15);
                oppPrayers.append(prayerName).append(" ");
            }
        }
        if (prayerCount > 0) {
            log.info(oppPrayers.toString());
        } else {
            log.info("[OBS] Opp Prayers: None");
        }
        // Equipment
        int equipCount = 0;
        StringBuilder equipment = new StringBuilder("[OBS] Opp Equipment IDs: ");
        for (int i = 44; i <= 56; i++) {
            int itemId = observations.get(i).intValue();
            if (itemId > 0) {
                equipCount++;
                equipment.append(itemId).append(" ");
            }
        }
        log.info("{} ({}/13 slots)", equipment.toString(), equipCount);
        // Combat stats
        log.info("[OBS] Opp Combat Bonuses - Att[57-59]: {} {} {}, Def[60-62]: {} {} {}, Str[63-65]: {} {} {}",
            observations.get(57), observations.get(58), observations.get(59),
            observations.get(60), observations.get(61), observations.get(62),
            observations.get(63), observations.get(64), observations.get(65));
        // Summary
        long nonZero = observations.stream().filter(n -> n.doubleValue() != 0.0).count();
        if (oppHp == 0.0 && equipCount == 0) {
            log.warn("[OBS] WARNING: Looks like DUMMY DATA! (0 HP, 0 equipment)");
            log.warn("[OBS] Actual player HP ratio: {}, Level: {}",
                targetPlayer.getHealthRatio(), targetPlayer.getCombatLevel());
        } else {
            log.info("[OBS] Valid observations: {} non-zero/176, HP={}%, Equip={}/13",
                nonZero, String.format("%.0f", oppHp * 100), equipCount);
        }
        // Also log combat adapter state
        if (combatAdapter != null) {
            Mobile combatTarget = combatAdapter.getTarget();
            if (combatTarget != null) {
                String targetName = "Unknown";
                if (combatTarget instanceof com.elvarg.game.entity.impl.player.Player) {
                    targetName = ((com.elvarg.game.entity.impl.player.Player) combatTarget).getUsername();
                }
                log.info("[OBS] CombatAdapter target: {} (type: {})",
                    targetName, combatTarget.getClass().getSimpleName());
                log.info("[OBS] CombatAdapter target HP: {}", combatTarget.getHitpoints());
            } else {
                log.warn("[OBS] CombatAdapter has NULL target!");
            }
        }
    }
    private String getPrayerName(int prayerIndex)
    {
        String[] prayerNames = {
            "ThickSkin", "BurstStr", "Clarity", "SharpEye",
            "MysticWill", "RockSkin", "SuperStr", "ImpReflex",
            "RapidRestore", "RapidHeal", "ProtItem", "HawkEye", "MysticLore",
            "SteelSkin", "UltStr", "IncrReflex", "ProtMage",
            "ProtRange", "ProtMelee", "EagleEye", "MysticMight",
            "Retribution", "Redemption", "Smite", "Preserve", "Chivalry", "Piety",
            "Rigour", "Augury"
        };
        return prayerIndex >= 0 && prayerIndex < prayerNames.length ?
               prayerNames[prayerIndex] : "Unknown[" + prayerIndex + "]";
    }
    private void dumpCurrentObservations()
    {
        if (!adaptersInitialized || environmentBridge == null) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Plugin not initialized", null);
            return;
        }
        try {
            java.util.List<Number> observations = environmentBridge.getObservations();
            if (observations == null || observations.size() != 176) {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "[AutoPvP] Invalid obs: " + (observations != null ? observations.size() : "null") + " values",
                    null);
                return;
            }
            // Summary for chat
            double ourHp = observations.get(13).doubleValue();
            double oppHp = observations.get(14).doubleValue();
            int equipCount = 0;
            for (int i = 44; i <= 56; i++) {
                if (observations.get(i).intValue() > 0) equipCount++;
            }
            int prayerCount = 0;
            for (int i = 15; i <= 43; i++) {
                if (observations.get(i).doubleValue() > 0) prayerCount++;
            }
            long nonZero = observations.stream().filter(n -> n.doubleValue() != 0.0).count();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                String.format("[AutoPvP] HP: Us=%.0f%% Opp=%.0f%%", ourHp * 100, oppHp * 100), null);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                String.format("[AutoPvP] Opp: %d equip, %d prayers", equipCount, prayerCount), null);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                String.format("[AutoPvP] Total: %d/176 non-zero", nonZero), null);
            // Full log to console
            Actor target = client.getLocalPlayer() != null ? client.getLocalPlayer().getInteracting() : null;
            if (target instanceof net.runelite.api.Player) {
                logDetailedObservations(observations, target);
            }
        } catch (Exception e) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[AutoPvP] Error: " + e.getMessage(), null);
            log.error("[AUTOPVP] Error dumping observations", e);
        }
    }
    /**
     * Provide config for GUI.
     */
    @Provides
    AutoPvPConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoPvPConfig.class);
    }
    private boolean verifyNatonClasspath() {
        try {
            Class.forName("com.github.naton1.rl.env.nh.NhEnvironment");
            Class.forName("com.github.naton1.rl.env.nh.NhLoadout");
            return true;
        } catch (ClassNotFoundException e) {
            log.error("[AUTOPVP] Naton1 environment classes not found on classpath", e);
            return false;
        }
    }
}
