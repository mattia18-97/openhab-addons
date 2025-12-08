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
import org.openhab.binding.picnet.internal.PicNetGateConfiguration;
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

import com.github.paolodenti.jsapp.core.command.base.SappConnection;

/**
 * The {@link PicNetGateHandler} handles a gate control in the PicNet system.
 *
 * Can optionally read status from Input/Output/Virtual address and writes trigger commands to Virtual address.
 * If status reading is not configured, operates in command-only mode.
 *
 * Typically uses pulse mode (momentary trigger) for gate activation.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetGateHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PicNetGateHandler.class);

    private @Nullable PicNetGateConfiguration config;
    private @Nullable PicNetBridgeHandler bridgeHandler;

    public PicNetGateHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(PicNetGateConfiguration.class);
        PicNetGateConfiguration localConfig = config;

        if (localConfig == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Configuration is null");
            return;
        }

        // Validate configuration
        if (localConfig.virtualAddress < 1 || localConfig.virtualAddress > 2500) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "virtualAddress must be between 1 and 2500");
            return;
        }

        if (localConfig.writeBit < 1 || localConfig.writeBit > 16) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "writeBit must be between 1 and 16");
            return;
        }

        // Validate status reading configuration if present
        if (localConfig.hasStatusReading()) {
            if (!localConfig.readType.equals("input") && !localConfig.readType.equals("output")
                    && !localConfig.readType.equals("virtual")) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "readType must be 'input', 'output', or 'virtual'");
                return;
            }

            int maxAddress = localConfig.readType.equals("virtual") ? 2500 : 250;
            if (localConfig.readAddress < 1 || localConfig.readAddress > maxAddress) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "readAddress must be between 1 and " + maxAddress);
                return;
            }

            if (localConfig.readBit < 1 || localConfig.readBit > 16) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "readBit must be between 1 and 16");
                return;
            }
        }

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
    public void dispose() {
        super.dispose();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            initialize();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        PicNetGateConfiguration localConfig = config;
        PicNetBridgeHandler localBridgeHandler = bridgeHandler;

        if (localConfig == null || localBridgeHandler == null) {
            logger.warn("Cannot handle command - configuration or bridge not available");
            return;
        }

        SappConnection connection = localBridgeHandler.getConnection();
        if (connection == null || !connection.isConnected()) {
            logger.warn("Cannot handle command - no connection to PicNet");
            return;
        }

        if ("switch".equals(channelUID.getId()) && command instanceof OnOffType) {
            OnOffType onOffCommand = (OnOffType) command;

            if (localConfig.pulseMode) {
                // Pulse mode: trigger on any command (ON or OFF)
                writePulse(connection, localConfig.virtualAddress, localConfig.writeBit);
            } else {
                // Normal mode: write ON (1) or OFF (0) to the bit
                writeNormal(connection, localConfig.virtualAddress, localConfig.writeBit, onOffCommand);
            }
        }
    }

    /**
     * Pulse mode: send a momentary pulse (1 then 0) - typical for gate triggers
     */
    private void writePulse(SappConnection connection, int virtualAddress, int bit) {
        try {
            // Use atomic bit operations from bridge
            PicNetBridgeHandler localBridgeHandler = bridgeHandler;
            if (localBridgeHandler != null) {
                // Set bit to 1
                localBridgeHandler.setBitInVirtual(virtualAddress, bit);

                // Small delay
                Thread.sleep(200);

                // Clear bit back to 0
                localBridgeHandler.clearBitInVirtual(virtualAddress, bit);

                logger.debug("Gate pulse sent to V{}:{}", virtualAddress, bit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Gate pulse interrupted on V{}:{}", virtualAddress, bit);
        } catch (Exception e) {
            logger.warn("Failed to send gate pulse to V{}:{}: {}", virtualAddress, bit, e.getMessage());
        }
    }

    /**
     * Normal mode: write ON (1) or OFF (0) to the bit using atomic operations
     */
    private void writeNormal(SappConnection connection, int virtualAddress, int bit, OnOffType command) {
        try {
            PicNetBridgeHandler localBridgeHandler = bridgeHandler;
            if (localBridgeHandler != null) {
                if (command == OnOffType.ON) {
                    localBridgeHandler.setBitInVirtual(virtualAddress, bit);
                } else {
                    localBridgeHandler.clearBitInVirtual(virtualAddress, bit);
                }
                logger.debug("Gate {} written to V{}:{}", command, virtualAddress, bit);
            }
        } catch (Exception e) {
            logger.warn("Failed to write gate command to V{}:{}: {}", virtualAddress, bit, e.getMessage());
        }
    }

    /**
     * Update gate status channel from read address
     * Called by bridge during polling if status reading is configured
     */
    public void updateGateStatus(int wordValue) {
        PicNetGateConfiguration localConfig = config;
        if (localConfig == null || !localConfig.hasStatusReading()) {
            return;
        }

        // Extract bit value
        boolean bitValue = PicNetDataUtils.getBit(wordValue, localConfig.readBit);

        // Invert the bit (standard for contacts: bit ON = gate CLOSED = OFF, bit OFF = gate OPEN = ON)
        OnOffType state = bitValue ? OnOffType.OFF : OnOffType.ON;

        updateState("switch", state);
    }

    /**
     * Get the read address type for polling
     */
    public String getReadType() {
        PicNetGateConfiguration localConfig = config;
        return localConfig != null ? localConfig.readType : "";
    }

    /**
     * Get the read address for polling
     */
    public int getReadAddress() {
        PicNetGateConfiguration localConfig = config;
        return localConfig != null ? localConfig.readAddress : 0;
    }

    /**
     * Get the read bit for polling
     */
    public int getReadBit() {
        PicNetGateConfiguration localConfig = config;
        return localConfig != null ? localConfig.readBit : 1;
    }

    /**
     * Check if this gate has status reading configured
     */
    public boolean hasStatusReading() {
        PicNetGateConfiguration localConfig = config;
        return localConfig != null && localConfig.hasStatusReading();
    }
}
