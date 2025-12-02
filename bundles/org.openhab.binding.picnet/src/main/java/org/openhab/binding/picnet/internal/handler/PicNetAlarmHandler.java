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
import org.openhab.binding.picnet.internal.PicNetAlarmConfiguration;
import org.openhab.binding.picnet.internal.PicNetBridgeHandler;
import org.openhab.core.library.types.OpenClosedType;
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

/**
 * The {@link PicNetAlarmHandler} handles a PicNet alarm sensor.
 * Alarm status is read by the bridge using Sapp72Command for batch efficiency.
 * Supports alarms 1-255.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetAlarmHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PicNetAlarmHandler.class);

    private @Nullable PicNetAlarmConfiguration config;
    private @Nullable PicNetBridgeHandler bridgeHandler;

    public PicNetAlarmHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(PicNetAlarmConfiguration.class);

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
        // Alarm is read-only, no commands to handle
        logger.debug("Alarm is read-only, ignoring command for channel: {}", channelUID.getId());
    }

    /**
     * Update the alarm channel from the status byte read during polling
     *
     * @param statusByte the alarm status byte (0=OK, 1=TRIGGERED)
     */
    public void updateAlarmChannel(int statusByte) {
        // Alarm status interpretation:
        // 0 = No alarm (CLOSED)
        // 1 = Alarm triggered (OPEN)
        OpenClosedType state = (statusByte == 0) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
        updateState("alarm", state);

        logger.trace("Updated alarm channel: {}", state);
    }

    /**
     * Get the alarm number for polling
     *
     * @return the alarm number or 0 if not configured
     */
    public int getAlarmNumber() {
        PicNetAlarmConfiguration localConfig = config;
        return localConfig != null ? localConfig.alarmNumber : 0;
    }
}
