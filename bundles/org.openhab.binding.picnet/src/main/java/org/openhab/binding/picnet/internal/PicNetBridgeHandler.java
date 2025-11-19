/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.picnet.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.picnet.internal.handler.PicNetInputHandler;
import org.openhab.binding.picnet.internal.handler.PicNetLightHandler;
import org.openhab.binding.picnet.internal.handler.PicNetOutputHandler;
import org.openhab.binding.picnet.internal.handler.PicNetVirtualHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.paolodenti.jsapp.core.command.Sapp74Command;
import com.github.paolodenti.jsapp.core.command.Sapp75Command;
import com.github.paolodenti.jsapp.core.command.Sapp7ECommand;
import com.github.paolodenti.jsapp.core.command.base.SappConnection;

/**
 * The {@link PicNetBridgeHandler} is responsible for handling commands and status
 * updates for the PicNet bridge.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(PicNetBridgeHandler.class);
    private final Object connectionLock = new Object();

    private @Nullable PicNetBridgeConfiguration config;
    private @Nullable SappConnection connection;
    private @Nullable ScheduledFuture<?> pollingJob;

    public PicNetBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge doesn't handle commands directly
    }

    @Override
    public void initialize() {
        PicNetBridgeConfiguration localConfig = getConfigAs(PicNetBridgeConfiguration.class);
        config = localConfig;

        if (localConfig.hostname.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Hostname must be configured");
            return;
        }

        logger.debug("Initializing PicNet Bridge with hostname: {} and port: {}", localConfig.hostname,
                localConfig.port);

        // Create connection
        connection = new SappConnection(localConfig.hostname, localConfig.port);

        // Try to connect
        scheduler.execute(this::connect);
    }

    @Override
    public void dispose() {
        stopPolling();
        disconnect();
        super.dispose();
    }

    private void connect() {
        SappConnection localConnection = connection;
        PicNetBridgeConfiguration localConfig = config;
        if (localConnection == null || localConfig == null) {
            return;
        }

        try {
            logger.debug("Connecting to PicNet device at {}:{}", localConfig.hostname, localConfig.port);
            localConnection.openConnection();

            if (localConnection.isConnected()) {
                updateStatus(ThingStatus.ONLINE);
                updateChildThingsStatus(ThingStatus.ONLINE);
                logger.info("Successfully connected to PicNet device at {}:{}", localConfig.hostname, localConfig.port);
                startPolling();
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Failed to establish connection");
                updateChildThingsStatus(ThingStatus.OFFLINE);
            }
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Connection error: " + e.getMessage());
            updateChildThingsStatus(ThingStatus.OFFLINE);
            logger.debug("Connection error", e);
        }
    }

    private void disconnect() {
        SappConnection localConnection = connection;
        if (localConnection != null) {
            localConnection.closeConnection();
            logger.debug("Disconnected from PicNet device");
        }
    }

    private void startPolling() {
        stopPolling();

        PicNetBridgeConfiguration localConfig = config;
        if (localConfig != null && localConfig.refreshInterval > 0) {
            pollingJob = scheduler.scheduleWithFixedDelay(this::poll, localConfig.refreshInterval,
                    localConfig.refreshInterval, TimeUnit.MILLISECONDS);
            logger.debug("Started polling with interval {} ms", localConfig.refreshInterval);
        }
    }

    private void stopPolling() {
        ScheduledFuture<?> localPollingJob = pollingJob;
        if (localPollingJob != null && !localPollingJob.isCancelled()) {
            localPollingJob.cancel(true);
            pollingJob = null;
            logger.debug("Stopped polling");
        }
    }

    private void poll() {
        // Synchronize access to connection to prevent concurrent requests
        synchronized (connectionLock) {
            SappConnection localConnection = connection;
            if (localConnection == null) {
                return;
            }

            // Check if connection is still alive
            if (!localConnection.isConnected()) {
                logger.debug("Connection lost, attempting to reconnect...");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection lost");
                updateChildThingsStatus(ThingStatus.OFFLINE);
                connect();
                return;
            }

            try {
                // Separate handlers by type for batch processing
                List<PicNetVirtualHandler> virtualHandlers = new ArrayList<>();
                List<PicNetInputHandler> inputHandlers = new ArrayList<>();
                List<PicNetOutputHandler> outputHandlers = new ArrayList<>();
                List<PicNetLightHandler> lightHandlers = new ArrayList<>();

                for (Thing thing : getThing().getThings()) {
                    ThingHandler handler = thing.getHandler();
                    if (handler != null && thing.getStatus() == ThingStatus.ONLINE) {
                        if (handler instanceof PicNetVirtualHandler virtualHandler) {
                            virtualHandlers.add(virtualHandler);
                        } else if (handler instanceof PicNetInputHandler inputHandler) {
                            inputHandlers.add(inputHandler);
                        } else if (handler instanceof PicNetOutputHandler outputHandler) {
                            outputHandlers.add(outputHandler);
                        } else if (handler instanceof PicNetLightHandler lightHandler) {
                            lightHandlers.add(lightHandler);
                        }
                    }
                }

                // Batch read Virtual addresses
                if (!virtualHandlers.isEmpty()) {
                    pollVirtualBatch(localConnection, virtualHandlers);
                    Thread.sleep(50);
                }

                // Poll Input modules (single read for now)
                for (PicNetInputHandler inputHandler : inputHandlers) {
                    int value = inputHandler.readInputWord();
                    if (value >= 0) {
                        inputHandler.updateWordChannel(value);
                    }
                    Thread.sleep(50);
                }

                // Poll Output modules (single read for now)
                for (PicNetOutputHandler outputHandler : outputHandlers) {
                    int value = outputHandler.readOutputWord();
                    if (value >= 0) {
                        outputHandler.updateWordChannel(value);
                    }
                    Thread.sleep(50);
                }

                // Poll Light handlers (grouped by readType and address)
                if (!lightHandlers.isEmpty()) {
                    pollLights(localConnection, lightHandlers);
                    Thread.sleep(50);
                }

                logger.trace("Polling cycle completed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Polling interrupted");
            } catch (RuntimeException e) {
                // Check if this is a connection error
                if (e.getMessage() != null && e.getMessage().startsWith("Connection error")) {
                    // Connection error (broken pipe, connection reset, etc.) - attempt reconnection
                    logger.warn("Connection error during polling: {} - attempting reconnection",
                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Connection error - reconnecting");
                    updateChildThingsStatus(ThingStatus.OFFLINE);
                    disconnect();
                    scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
                } else {
                    // Other runtime errors
                    logger.debug("Runtime error during polling: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                // Other unexpected errors - log but don't reconnect
                logger.debug("Error during polling: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Poll Virtual addresses using batch reading for consecutive addresses
     */
    private void pollVirtualBatch(SappConnection connection, List<PicNetVirtualHandler> handlers) {
        if (handlers.isEmpty()) {
            return;
        }

        // Sort handlers by address
        handlers.sort(Comparator.comparingInt(h -> h.getAddress()));

        int i = 0;
        while (i < handlers.size()) {
            int startAddress = handlers.get(i).getAddress();
            int count = 1;

            // Find consecutive addresses
            while (i + count < handlers.size() && handlers.get(i + count).getAddress() == startAddress + count) {
                count++;
                if (count >= 250) { // Protocol limit
                    break;
                }
            }

            // Read batch
            try {
                Sapp7ECommand command = new Sapp7ECommand(startAddress, (byte) count);
                command.run(connection);

                if (command.isResponseOk()) {
                    int[] values = command.getResponse().getDataAsWordArray();
                    if (values != null && values.length == count) {
                        // Distribute values to handlers
                        for (int j = 0; j < count; j++) {
                            handlers.get(i + j).updateAllChannels(values[j]);
                        }
                        logger.trace("Batch read {} virtual addresses starting from {}", count, startAddress);
                    } else {
                        logger.debug("Invalid response for batch read at virtual {}", startAddress);
                    }
                } else {
                    logger.debug("Failed batch read at virtual {}", startAddress);
                }
            } catch (Exception e) {
                // Check if this is a connection error
                if (isConnectionError(e)) {
                    // Propagate connection errors to trigger reconnection
                    throw new RuntimeException("Connection error: " + e.getMessage(), e);
                }
                // Log other errors but continue
                logger.debug("Error batch reading virtual {}: {}", startAddress, e.getMessage());
            }

            i += count;
        }
    }

    /**
     * Poll lights (optimized by reading each address once and distributing to all lights)
     */
    private void pollLights(SappConnection connection, List<PicNetLightHandler> handlers) {
        if (handlers.isEmpty()) {
            return;
        }

        // Group lights by readType and readAddress
        Map<String, List<PicNetLightHandler>> lightsByAddress = new HashMap<>();
        for (PicNetLightHandler handler : handlers) {
            String key = handler.getReadType() + ":" + handler.getReadAddress();
            lightsByAddress.computeIfAbsent(key, k -> new ArrayList<>()).add(handler);
        }

        // Read each address once and update all lights using it
        for (Map.Entry<String, List<PicNetLightHandler>> entry : lightsByAddress.entrySet()) {
            List<PicNetLightHandler> lightsOnThisAddress = entry.getValue();

            // Get first handler to know readType and address
            PicNetLightHandler firstHandler = lightsOnThisAddress.get(0);
            int readAddress = firstHandler.getReadAddress();
            String readType = firstHandler.getReadType();

            try {
                int wordValue = -1;

                // Read based on type
                switch (readType.toLowerCase()) {
                    case "input":
                        Sapp74Command inputCmd = new Sapp74Command((byte) readAddress);
                        inputCmd.run(connection);
                        if (inputCmd.isResponseOk()) {
                            wordValue = inputCmd.getResponse().getDataAsWord();
                        }
                        break;
                    case "output":
                        Sapp75Command outputCmd = new Sapp75Command((byte) readAddress);
                        outputCmd.run(connection);
                        if (outputCmd.isResponseOk()) {
                            wordValue = outputCmd.getResponse().getDataAsWord();
                        }
                        break;
                    case "virtual":
                        Sapp7ECommand virtualCmd = new Sapp7ECommand(readAddress, (byte) 1);
                        virtualCmd.run(connection);
                        if (virtualCmd.isResponseOk()) {
                            int[] values = virtualCmd.getResponse().getDataAsWordArray();
                            if (values != null && values.length > 0) {
                                wordValue = values[0];
                            }
                        }
                        break;
                }

                if (wordValue >= 0) {
                    logger.trace("Read {} {} for {} lights: {}", readType, readAddress, lightsOnThisAddress.size(),
                            wordValue);

                    // Update all lights on this address
                    for (PicNetLightHandler lightHandler : lightsOnThisAddress) {
                        lightHandler.updateSwitchChannel(wordValue);
                    }
                } else {
                    logger.debug("Failed to read {} {} for lights", readType, readAddress);
                }
            } catch (Exception e) {
                // Check if this is a connection error
                if (isConnectionError(e)) {
                    // Propagate connection errors to trigger reconnection
                    throw new RuntimeException("Connection error: " + e.getMessage(), e);
                }
                // Log other errors but continue
                logger.debug("Error reading {} {} for lights: {}", readType, readAddress, e.getMessage());
            }
        }
    }

    /**
     * Check if an exception is a connection error (broken pipe, connection reset, etc.)
     *
     * @param e the exception to check
     * @return true if this is a connection error
     */
    private boolean isConnectionError(Exception e) {
        if (e instanceof IOException) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        // Check for common connection error messages
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("broken pipe") || lowerMessage.contains("connection reset")
                || lowerMessage.contains("connection closed") || lowerMessage.contains("socket closed")
                || lowerMessage.contains("connection refused") || lowerMessage.contains("connection timed out");
    }

    /**
     * Update status of all child things when bridge status changes
     *
     * @param status the status to set (ONLINE or OFFLINE)
     */
    private void updateChildThingsStatus(ThingStatus status) {
        for (Thing thing : getThing().getThings()) {
            if (status == ThingStatus.ONLINE) {
                // When bridge comes online, set children to ONLINE
                thing.setStatusInfo(new ThingStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE, null));
            } else {
                // When bridge goes offline, set children to OFFLINE with BRIDGE_OFFLINE detail
                thing.setStatusInfo(new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null));
            }
        }
        logger.debug("Updated {} child things to status {}", getThing().getThings().size(), status);
    }

    /**
     * Get the SappConnection for child things to use
     *
     * @return the SappConnection or null if not connected
     */
    public @Nullable SappConnection getConnection() {
        return connection;
    }
}
