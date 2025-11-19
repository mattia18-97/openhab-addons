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
package org.openhab.binding.picnet.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.picnet.internal.PicNetBridgeHandler;
import org.openhab.binding.picnet.internal.PicNetLightConfiguration;
import org.openhab.binding.picnet.internal.util.PicNetDataUtils;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.paolodenti.jsapp.core.command.Sapp74Command;
import com.github.paolodenti.jsapp.core.command.Sapp75Command;
import com.github.paolodenti.jsapp.core.command.Sapp7DCommand;
import com.github.paolodenti.jsapp.core.command.Sapp7ECommand;
import com.github.paolodenti.jsapp.core.command.base.SappCommand;
import com.github.paolodenti.jsapp.core.command.base.SappConnection;

/**
 * The {@link PicNetLightHandler} handles a single light in the PicNet system.
 * Reads status from Input/Output/Virtual address and writes commands to Virtual address.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetLightHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PicNetLightHandler.class);

    private @Nullable PicNetLightConfiguration config;
    private @Nullable PicNetBridgeHandler bridgeHandler;

    public PicNetLightHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(PicNetLightConfiguration.class);

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No bridge configured");
            return;
        }

        bridgeHandler = (PicNetBridgeHandler) bridge.getHandler();
        if (bridgeHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED, "Bridge handler not available");
            return;
        }

        // Check bridge status before going online
        if (bridge.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            // Bridge came online - reinitialize to reload configuration
            initialize();
        } else {
            // Bridge went offline
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        PicNetLightConfiguration localConfig = config;
        PicNetBridgeHandler localBridgeHandler = bridgeHandler;

        if (localConfig == null || localBridgeHandler == null) {
            logger.warn("Cannot handle command - configuration or bridge not available");
            return;
        }

        SappConnection connection = localBridgeHandler.getConnection();
        if (connection == null || !connection.isConnected()) {
            logger.warn("Cannot handle command - connection not available");
            return;
        }

        // Only handle "switch" channel with ON/OFF commands
        if ("switch".equals(channelUID.getId()) && command instanceof OnOffType onOffCommand) {
            writeLightCommand(connection, localConfig, onOffCommand);
        }
    }

    /**
     * Read the light status from configured address (Input/Output/Virtual)
     *
     * @return the word value or -1 on error
     */
    public int readWord() {
        PicNetLightConfiguration localConfig = config;
        PicNetBridgeHandler localBridgeHandler = bridgeHandler;

        if (localConfig == null || localBridgeHandler == null) {
            logger.trace("Cannot read - configuration or bridge not available");
            return -1;
        }

        SappConnection connection = localBridgeHandler.getConnection();
        if (connection == null || !connection.isConnected()) {
            logger.trace("Cannot read {} - connection not available", localConfig.readAddress);
            return -1;
        }

        try {
            switch (localConfig.readType.toLowerCase()) {
                case "input":
                    return readInputWord(connection, localConfig.readAddress);
                case "output":
                    return readOutputWord(connection, localConfig.readAddress);
                case "virtual":
                    return readVirtualWord(connection, localConfig.readAddress);
                default:
                    logger.warn("Invalid readType: {}", localConfig.readType);
                    return -1;
            }
        } catch (Exception e) {
            logger.debug("Error reading {}: {}", localConfig.readType, e.getMessage());
            return -1;
        }
    }

    /**
     * Read word from Input module
     */
    private int readInputWord(SappConnection connection, int address) {
        try {
            Sapp74Command command = new Sapp74Command((byte) address);
            command.run(connection);
            if (command.isResponseOk()) {
                int value = command.getResponse().getDataAsWord();
                logger.trace("Read input module {}: {}", address, value);
                return value;
            }
        } catch (Exception e) {
            logger.debug("Error reading input module {}: {}", address, e.getMessage());
        }
        return -1;
    }

    /**
     * Read word from Output module
     */
    private int readOutputWord(SappConnection connection, int address) {
        try {
            Sapp75Command command = new Sapp75Command((byte) address);
            command.run(connection);
            if (command.isResponseOk()) {
                int value = command.getResponse().getDataAsWord();
                logger.trace("Read output module {}: {}", address, value);
                return value;
            }
        } catch (Exception e) {
            logger.debug("Error reading output module {}: {}", address, e.getMessage());
        }
        return -1;
    }

    /**
     * Read word from Virtual address
     */
    private int readVirtualWord(SappConnection connection, int address) {
        try {
            Sapp7ECommand command = new Sapp7ECommand(address, (byte) 1);
            command.run(connection);
            if (command.isResponseOk()) {
                int[] values = command.getResponse().getDataAsWordArray();
                if (values != null && values.length > 0) {
                    logger.trace("Read virtual {}: {}", address, values[0]);
                    return values[0];
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading virtual {}: {}", address, e.getMessage());
        }
        return -1;
    }

    /**
     * Write light command to Virtual address
     */
    private void writeLightCommand(SappConnection connection, PicNetLightConfiguration config, OnOffType command) {
        try {
            if (config.pulseMode) {
                // Pulse mode: send a pulse (1) for any command
                writePulse(connection, config.virtualAddress, config.writeBit);
            } else {
                // Normal mode: set bit to 1 for ON, 0 for OFF
                writeNormal(connection, config.virtualAddress, config.writeBit, command);
            }
        } catch (Exception e) {
            logger.warn("Error writing light command to virtual {} bit {}: {}", config.virtualAddress, config.writeBit,
                    e.getMessage());
        }
    }

    /**
     * Normal mode: write ON (1) or OFF (0) to the bit
     */
    private void writeNormal(SappConnection connection, int virtualAddress, int bit, OnOffType command) {
        try {
            // Read current value from Virtual to preserve other bits
            int currentValue = readVirtualWordSync(connection, virtualAddress);
            if (currentValue < 0) {
                logger.warn("Cannot read current value to modify bit {} on virtual {}", bit, virtualAddress);
                return;
            }

            // Set bit based on command (ON=1, OFF=0)
            boolean newBitValue = (command == OnOffType.ON);
            int newValue = PicNetDataUtils.setBit(currentValue, bit, newBitValue);

            // Write back
            SappCommand writeCommand = new Sapp7DCommand(virtualAddress, newValue);
            writeCommand.run(connection);

            if (writeCommand.isResponseOk()) {
                logger.debug("Successfully set light bit {} to {} on virtual {}", bit, newBitValue ? "ON" : "OFF",
                        virtualAddress);
            } else {
                logger.warn("Failed to write light command to virtual {}", virtualAddress);
            }
        } catch (Exception e) {
            logger.warn("Error in normal write mode: {}", e.getMessage());
        }
    }

    /**
     * Pulse mode: send a pulse (set bit to 1)
     */
    private void writePulse(SappConnection connection, int virtualAddress, int bit) {
        try {
            // Read current value
            int currentValue = readVirtualWordSync(connection, virtualAddress);
            if (currentValue < 0) {
                logger.warn("Cannot read current value to send pulse on bit {} of virtual {}", bit, virtualAddress);
                return;
            }

            // Set bit to 1 (pulse)
            int pulseValue = PicNetDataUtils.setBit(currentValue, bit, true);
            SappCommand pulseCommand = new Sapp7DCommand(virtualAddress, pulseValue);
            pulseCommand.run(connection);

            if (pulseCommand.isResponseOk()) {
                logger.debug("Pulse sent to virtual {} bit {}", virtualAddress, bit);
            } else {
                logger.warn("Failed to send pulse to virtual {}", virtualAddress);
            }
        } catch (Exception e) {
            logger.warn("Error in pulse mode: {}", e.getMessage());
        }
    }

    /**
     * Read virtual word synchronously (blocking)
     */
    private int readVirtualWordSync(SappConnection connection, int address) {
        try {
            Sapp7ECommand command = new Sapp7ECommand(address, (byte) 1);
            command.run(connection);

            if (command.isResponseOk()) {
                int[] values = command.getResponse().getDataAsWordArray();
                if (values != null && values.length > 0) {
                    return values[0];
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading virtual {} for write-back: {}", address, e.getMessage());
        }
        return -1;
    }

    /**
     * Update the switch channel from the word value read during polling
     *
     * @param wordValue the word value read from the configured address
     */
    public void updateSwitchChannel(int wordValue) {
        PicNetLightConfiguration localConfig = config;
        if (localConfig == null) {
            return;
        }

        // Extract the bit value for this light
        boolean bitValue = PicNetDataUtils.getBit(wordValue, localConfig.readBit);
        updateState("switch", bitValue ? OnOffType.ON : OnOffType.OFF);
    }

    /**
     * Get the read address for polling
     *
     * @return the read address or 0 if not configured
     */
    public int getReadAddress() {
        PicNetLightConfiguration localConfig = config;
        return localConfig != null ? localConfig.readAddress : 0;
    }

    /**
     * Get the read type
     *
     * @return the read type or empty string if not configured
     */
    public String getReadType() {
        PicNetLightConfiguration localConfig = config;
        return localConfig != null ? localConfig.readType : "";
    }
}
