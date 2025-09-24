package net.runelite.client.plugins.autopvp.core;

import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Client for communicating with the Python PPO server.
 * Protocol (JSON over TCP):
 *   Request: {"model": "FineTunedNh", "actionMasks": [[...]], "obs": [[...]], ...}\n
 *   Response: {"action": [1,0,2,...], "logProb": null, ...}\n
 *
 * The response contains the 12-head action array directly.
 */
@Slf4j
public class AIClient {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5557;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 500; // Must be < 600ms tick deadline

    private final String host;
    private final int port;
    private final ExecutorService executor;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Gson gson;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private long lastRequestTime = 0;
    private long totalRequests = 0;
    private long totalLatency = 0;

    public AIClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public AIClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("AIClient-Worker");
            t.setDaemon(true);
            return t;
        });
        // Configure Gson to handle NaN and Infinity values
        this.gson = new GsonBuilder()
            .serializeSpecialFloatingPointValues()
            .create();
    }

    /**
     * Connect to the Python PPO server.
     * @return true if connected successfully
     */
    public boolean connect() {
        try {
            disconnect(); // Clean up any existing connection

            socket = new Socket(host, port);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm for lower latency

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            connected.set(true);
            log.info("[AI] Connected to PPO server at {}:{}", host, port);
            return true;

        } catch (Exception e) {
            log.error("[AI] Failed to connect to PPO server at {}:{}", host, port, e);
            connected.set(false);
            return false;
        }
    }

    /**
     * Disconnect from the Python PPO server.
     */
    public void disconnect() {
        connected.set(false);

        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Request an action from the AI server.
     * @param tick Current game tick
     * @param reward Reward signal (0.0 for normal operation)
     * @param observations List of observation values (176 elements)
     * @param actionMasks List of action masks for each head (12 heads)
     * @return Action array with 12 elements, or null if failed
     */
    public CompletableFuture<int[]> requestActionAsync(int tick, float reward,
                                                       List<Number> observations,
                                                       List<List<Boolean>> actionMasks) {
        return CompletableFuture.supplyAsync(() ->
            requestAction(tick, reward, observations, actionMasks), executor);
    }

    /**
     * Request an action from the AI server (blocking).
     * @param tick Current game tick
     * @param reward Reward signal (0.0 for normal operation)
     * @param observations List of observation values (176 elements)
     * @param actionMasks List of action masks for each head (12 heads)
     * @return Action array with 12 elements, or null if failed
     */
    public int[] requestAction(int tick, float reward,
                               List<Number> observations,
                               List<List<Boolean>> actionMasks) {
        if (!connected.get()) {
            if (!connect()) {
                return getDefaultAction(); // Return safe default if can't connect
            }
        }

        try {
            long startTime = System.currentTimeMillis();

            // Build request string
            String request = buildRequest(tick, reward, observations, actionMasks);

            // Send request
            writer.write(request);
            writer.flush();

            // Read response
            String response = reader.readLine();
            if (response == null) {
                throw new IOException("Server closed connection");
            }

            // Parse JSON response
            Map<String, Object> responseMap = gson.fromJson(response, Map.class);
            List<Double> actionListDouble = (List<Double>) responseMap.get("action");

            if (actionListDouble == null || actionListDouble.size() != 12) {
                log.error("[AI] Invalid response - expected 12 actions, got: {}", actionListDouble);
                return getDefaultAction();
            }

            // Convert from Double to Integer
            List<Integer> actionList = actionListDouble.stream()
                .map(Double::intValue)
                .collect(Collectors.toList());


            // Convert to int array
            int[] action = actionList.stream().mapToInt(Integer::intValue).toArray();

            // Track metrics
            long latency = System.currentTimeMillis() - startTime;
            totalRequests++;
            totalLatency += latency;
            lastRequestTime = System.currentTimeMillis();

            if (latency > 100) {
                log.warn("[AI] High latency: {}ms", latency);
            }

            return action;

        } catch (SocketTimeoutException e) {
            log.warn("[AI] Request timeout");
            connected.set(false);
            return getDefaultAction();

        } catch (Exception e) {
            log.error("[AI] Request failed", e);
            connected.set(false);
            return getDefaultAction();
        }
    }

    /**
     * Build the request string for the server.
     */
    private String buildRequest(int tick, float reward,
                                List<Number> observations,
                                List<List<Boolean>> actionMasks) {
        // Build JSON request for Naton1 API format
        Map<String, Object> request = new HashMap<>();

        // Model name - use default model
        request.put("model", "FineTunedNh");

        // Action masks - list of lists of booleans
        request.put("actionMasks", actionMasks);

        // Observations - wrap in outer list for potential frame stacking
        List<List<Number>> obsWrapper = new ArrayList<>();
        obsWrapper.add(observations);
        request.put("obs", obsWrapper);

        // Optional flags
        request.put("deterministic", false);
        request.put("returnLogProb", false);
        request.put("returnEntropy", false);
        request.put("returnValue", false);
        request.put("returnProbs", false);
        request.put("extensions", new ArrayList<>());

        // Convert to JSON
        try {
            return gson.toJson(request) + "\n";
        } catch (Exception e) {
            log.error("[AI] Failed to serialize request", e);
            return null;
        }
    }

    /**
     * Convert action masks to hex string for compact transmission.
     */
    private String convertActionMasksToHex(List<List<Boolean>> actionMasks) {
        // Flatten all masks into a bit string
        StringBuilder bits = new StringBuilder();
        for (List<Boolean> headMask : actionMasks) {
            for (Boolean valid : headMask) {
                bits.append(valid ? "1" : "0");
            }
        }

        // Convert to hex (pad to multiple of 4)
        while (bits.length() % 4 != 0) {
            bits.append("0");
        }

        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < bits.length(); i += 4) {
            String nibble = bits.substring(i, i + 4);
            hex.append(Integer.toHexString(Integer.parseInt(nibble, 2)));
        }

        return hex.toString();
    }

    /**
     * Convert flat action index to 12-head action array.
     */
    private int[] convertFlatIndexToAction(int flatIndex, List<List<Boolean>> actionMasks) {
        int[] action = new int[12];
        int currentIndex = 0;

        // Convert flat index to multi-head actions
        for (int head = 0; head < 12 && head < actionMasks.size(); head++) {
            List<Boolean> headMask = actionMasks.get(head);
            int headSize = headMask.size();

            // Find the valid action for this head
            int validCount = 0;
            for (int i = 0; i < headSize; i++) {
                if (headMask.get(i)) {
                    if (currentIndex == flatIndex) {
                        action[head] = i;
                        return action;
                    }
                    currentIndex++;
                    validCount++;
                }
            }

            // If no valid actions, default to 0
            if (validCount == 0) {
                action[head] = 0;
            }
        }

        // If we couldn't map the index properly, return default
        log.warn("[AI] Could not map flat index {} to action heads", flatIndex);
        return getDefaultAction();
    }

    /**
     * Get a safe default action (all zeros = no action).
     */
    private int[] getDefaultAction() {
        return new int[12]; // All zeros = no action
    }

    /**
     * Check if the client is connected.
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Get average latency in milliseconds.
     */
    public double getAverageLatency() {
        if (totalRequests == 0) {
            return 0;
        }
        return (double) totalLatency / totalRequests;
    }

    /**
     * Get total number of requests made.
     */
    public long getTotalRequests() {
        return totalRequests;
    }

    /**
     * Shutdown the client and cleanup resources.
     */
    public void shutdown() {
        disconnect();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}