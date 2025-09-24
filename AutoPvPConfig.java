package net.runelite.client.plugins.autopvp;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Configuration for the AutoPvP plugin.
 */
@ConfigGroup("autopvp")
public interface AutoPvPConfig extends Config {

    @ConfigItem(
        keyName = "enabled",
        name = "Enable AutoPvP",
        description = "Enable or disable the AutoPvP plugin"
    )
    default boolean enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "serverHost",
        name = "AI Server Host",
        description = "Host address of the Python PPO server"
    )
    default String serverHost() {
        return "127.0.0.1";
    }

    @ConfigItem(
        keyName = "serverPort",
        name = "AI Server Port",
        description = "Port of the Python PPO server"
    )
    default int serverPort() {
        return 5557;
    }

    @ConfigItem(
        keyName = "loadoutOverride",
        name = "Loadout Override",
        description = "Choose which naton loadout to use. Auto detects from equipped gear."
    )
    default LoadoutOverride loadoutOverride() {
        return LoadoutOverride.AUTO;
    }

    @ConfigItem(
        keyName = "debugMode",
        name = "Debug Mode",
        description = "Enable detailed debug logging"
    )
    default boolean debugMode() {
        return false;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Show AutoPvP status overlay"
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "actionDelay",
        name = "Action Delay (ms)",
        description = "Minimum delay between actions in milliseconds"
    )
    default int actionDelay() {
        return 50;
    }

    @ConfigItem(
        keyName = "autoReconnect",
        name = "Auto Reconnect",
        description = "Automatically reconnect to AI server if disconnected"
    )
    default boolean autoReconnect() {
        return true;
    }

    @ConfigItem(
        keyName = "safeMode",
        name = "Safe Mode",
        description = "Prevent potentially dangerous actions (for testing)"
    )
    default boolean safeMode() {
        return false;
    }
}
