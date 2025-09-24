package net.runelite.client.plugins.autopvp.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Initializes PacketUtils plugin for direct packet injection.
 * This is CRITICAL for actions to work properly.
 */
@Slf4j
public class PacketUtilsInitializer {

    private static boolean initialized = false;

    /**
     * Check if PacketUtils can be initialized without actually doing it.
     * Useful for checking if the expensive operation has already been done.
     */
    public static boolean isAlreadyInitialized() {
        if (initialized) {
            return true;
        }

        // Check if PacketUtils has already been initialized by checking static fields
        try {
            Class<?> packetUtilsClass = Class.forName("com.example.PacketUtils.PacketUtilsPlugin");
            java.lang.reflect.Field addNodeField = packetUtilsClass.getField("addNodeMethod");
            Object addNodeMethod = addNodeField.get(null);
            java.lang.reflect.Field usingClientField = packetUtilsClass.getField("usingClientAddNode");
            boolean usingClientAddNode = (boolean) usingClientField.get(null);

            // If either is set, PacketUtils was already initialized
            if (addNodeMethod != null || usingClientAddNode) {
                initialized = true;
                return true;
            }
        } catch (Exception e) {
            // Ignore - not initialized
        }
        return false;
    }

    public static void initialize(Client client) {
        // Check if already initialized to avoid re-running expensive operations
        if (initialized) {
            log.debug("[PACKETUTILS] PacketUtils already initialized, skipping");
            return;
        }

        try {
            log.info("[PACKETUTILS] Initializing PacketUtils plugin for direct packet injection");

            // Fix PacketUtils marker file issue before initialization
            // Citation: PacketUtilsPlugin.java:291 Files.write() throws FileAlreadyExistsException
            java.io.File packetUtilsMarker = new java.io.File(System.getProperty("user.home"), ".runelite/PacketUtils/1.11.16-233.txt");
            if (packetUtilsMarker.exists()) {
                boolean deleted = packetUtilsMarker.delete();
                log.info("[PACKETUTILS] Deleted existing marker file: {}", deleted);
            }

            Class<?> packetUtilsClass = Class.forName("com.example.PacketUtils.PacketUtilsPlugin");

            // Create instance
            Object packetUtils = packetUtilsClass.getDeclaredConstructor().newInstance();

            // Inject client dependency
            java.lang.reflect.Field clientField = packetUtilsClass.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(packetUtils, client);

            // Set static client reference
            java.lang.reflect.Field staticClientField = packetUtilsClass.getDeclaredField("staticClient");
            staticClientField.setAccessible(true);
            staticClientField.set(null, client);

            // Call startUp to initialize
            java.lang.reflect.Method startUpMethod = packetUtilsClass.getDeclaredMethod("startUp");
            startUpMethod.setAccessible(true);
            startUpMethod.invoke(packetUtils);

            // Wait a moment for initialization
            Thread.sleep(100);

            // Verify initialization by checking addNodeMethod
            java.lang.reflect.Field addNodeField = packetUtilsClass.getField("addNodeMethod");
            Object addNodeMethod = addNodeField.get(null);

            java.lang.reflect.Field usingClientField = packetUtilsClass.getField("usingClientAddNode");
            boolean usingClientAddNode = (boolean) usingClientField.get(null);

            if (addNodeMethod != null || usingClientAddNode) {
                log.info("[PACKETUTILS] PacketUtils initialized successfully!");
                log.info("[PACKETUTILS] Using client addNode: {}, addNodeMethod: {}",
                    usingClientAddNode, addNodeMethod != null ? addNodeMethod.toString() : "N/A");
                initialized = true;  // Mark as initialized on success
            } else {
                log.warn("[PACKETUTILS] PacketUtils initialized but addNodeMethod is null and not using client addNode");
                initialized = true;  // Still mark as initialized to avoid re-running
            }

        } catch (ClassNotFoundException e) {
            // PacketUtils plugin not available - this is fine, not all users have it
            log.info("[PACKETUTILS] PacketUtils plugin not found - actions may not work");
            initialized = true;  // Don't retry if class not found
        } catch (Exception e) {
            log.error("[PACKETUTILS] Failed to initialize PacketUtils: {}", e.getMessage(), e);
            // Don't mark as initialized on errors, allow retry
        }
    }
}