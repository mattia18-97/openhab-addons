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
import org.openhab.binding.picnet.internal.PicNetModuleConfiguration;
import org.openhab.binding.picnet.internal.util.PicNetDataUtils;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.paolodenti.jsapp.core.command.Sapp74Command;
import com.github.paolodenti.jsapp.core.command.base.SappConnection;

/**
 * The {@link PicNetInputHandler} handles Input modules in PicNet system.
 * Input modules are read-only.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetInputHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PicNetInputHandler.class);

    private @Nullable PicNetModuleConfiguration config;
    private @Nullable PicNetBridgeHandler bridgeHandler;

    public PicNetInputHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(PicNetModuleConfiguration.class);

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

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Input modules are read-only, no commands to handle
        logger.debug("Input module {} is read-only, ignoring command", config != null ? config.address : "unknown");
    }

    /**
     * Read the word value from this input module
     *
     * @return the word value or -1 on error
     */
    public int readInputWord() {
        PicNetModuleConfiguration localConfig = config;
        PicNetBridgeHandler localBridgeHandler = bridgeHandler;

        if (localConfig == null || localBridgeHandler == null) {
            logger.trace("Cannot read input - configuration or bridge not available");
            return -1;
        }

        SappConnection connection = localBridgeHandler.getConnection();
        if (connection == null || !connection.isConnected()) {
            logger.trace("Cannot read input {} - connection not available", localConfig.address);
            return -1;
        }

        try {
            Sapp74Command command = new Sapp74Command((byte) localConfig.address);
            command.run(connection);

            if (command.isResponseOk()) {
                int value = command.getResponse().getDataAsWord();
                logger.trace("Read input module {}: {}", localConfig.address, value);
                return value;
            } else {
                logger.debug("Failed to read input module {}", localConfig.address);
                return -1;
            }
        } catch (Exception e) {
            logger.debug("Error reading input module {}: {}", localConfig.address, e.getMessage());
            return -1;
        }
    }

    /**
     * Update all channels from the word value read during polling
     *
     * @param wordValue the word value read from the device
     */
    public void updateAllChannels(int wordValue) {
        // Update all channels based on their ID
        for (Channel channel : getThing().getChannels()) {
            String channelId = channel.getUID().getId();

            // Handle bit channels (bit1-bit16) as contacts with inverted logic (NC)
            if (channelId.startsWith("bit")) {
                try {
                    int bit = Integer.parseInt(channelId.substring(3));
                    boolean bitValue = PicNetDataUtils.getBit(wordValue, bit);
                    // Inverted: bit 0 = OPEN, bit 1 = CLOSED (NC contact logic)
                    updateState(channel.getUID(), bitValue ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                } catch (NumberFormatException e) {
                    logger.debug("Invalid bit channel ID: {}", channelId);
                }
                continue;
            }

            // Handle other channels by ID
            switch (channelId) {
                case "word":
                    updateState(channel.getUID(), new DecimalType(wordValue));
                    break;

                case "signedWord":
                    updateState(channel.getUID(), new DecimalType(PicNetDataUtils.toSignedWord(wordValue)));
                    break;

                case "highByte":
                    updateState(channel.getUID(), new DecimalType(PicNetDataUtils.getHighByte(wordValue)));
                    break;

                case "lowByte":
                    updateState(channel.getUID(), new DecimalType(PicNetDataUtils.getLowByte(wordValue)));
                    break;

                case "highByteSigned":
                    updateState(channel.getUID(),
                            new DecimalType(PicNetDataUtils.toSignedByte(PicNetDataUtils.getHighByte(wordValue))));
                    break;

                case "lowByteSigned":
                    updateState(channel.getUID(),
                            new DecimalType(PicNetDataUtils.toSignedByte(PicNetDataUtils.getLowByte(wordValue))));
                    break;

                default:
                    logger.trace("Unknown channel ID: {}", channelId);
                    break;
            }
        }
    }

    /**
     * Update the channel state from polling (legacy, kept for compatibility)
     *
     * @param wordValue the word value read from the device
     */
    public void updateWordChannel(int wordValue) {
        updateAllChannels(wordValue);
    }

    /**
     * Get the module address
     *
     * @return the module address or 0 if not configured
     */
    public int getAddress() {
        PicNetModuleConfiguration localConfig = config;
        return localConfig != null ? localConfig.address : 0;
    }
}
