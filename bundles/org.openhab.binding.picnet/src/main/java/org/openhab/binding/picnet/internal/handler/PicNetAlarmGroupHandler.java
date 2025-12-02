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
import org.openhab.binding.picnet.internal.PicNetAlarmGroupConfiguration;
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
 * The {@link PicNetAlarmGroupHandler} handles a PicNet alarm group.
 * Monitors a range of alarms and reports if ANY alarm in the range is triggered.
 * Alarm status is read by the bridge using Sapp72Command for batch efficiency.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetAlarmGroupHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PicNetAlarmGroupHandler.class);

    private @Nullable PicNetAlarmGroupConfiguration config;
    private @Nullable PicNetBridgeHandler bridgeHandler;

    public PicNetAlarmGroupHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(PicNetAlarmGroupConfiguration.class);

        // Validate configuration
        PicNetAlarmGroupConfiguration localConfig = config;
        if (localConfig != null) {
            if (localConfig.startAlarm < 1 || localConfig.startAlarm > 255) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "startAlarm must be between 1 and 255");
                return;
            }
            if (localConfig.endAlarm < 1 || localConfig.endAlarm > 255) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "endAlarm must be between 1 and 255");
                return;
            }
            if (localConfig.startAlarm > localConfig.endAlarm) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "startAlarm must be less than or equal to endAlarm");
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
        // Alarm group is read-only, no commands to handle
        logger.debug("Alarm group is read-only, ignoring command for channel: {}", channelUID.getId());
    }

    /**
     * Update the master alarm channel based on all alarm statuses in the range
     *
     * @param alarmStatuses array of alarm statuses (index 0 = alarm 1, etc.)
     */
    public void updateMasterAlarmChannel(byte[] alarmStatuses) {
        PicNetAlarmGroupConfiguration localConfig = config;
        if (localConfig == null) {
            return;
        }

        // Check if any alarm in the configured range is triggered
        boolean anyAlarmTriggered = false;
        int checkedAlarms = 0;

        for (int alarmNumber = localConfig.startAlarm; alarmNumber <= localConfig.endAlarm; alarmNumber++) {
            // Array is 0-indexed, alarm numbers are 1-indexed
            int arrayIndex = alarmNumber - 1;
            if (arrayIndex >= 0 && arrayIndex < alarmStatuses.length) {
                int status = alarmStatuses[arrayIndex] & 0xFF;
                if (status != 0) {
                    anyAlarmTriggered = true;
                    logger.trace("Alarm {} is triggered (status={})", alarmNumber, status);
                }
                checkedAlarms++;
            }
        }

        // Update channel: CLOSED = all OK, OPEN = at least one triggered
        OpenClosedType state = anyAlarmTriggered ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
        updateState("master-alarm", state);

        logger.trace("Updated master alarm channel: {} (checked {} alarms in range {}-{})", state, checkedAlarms,
                localConfig.startAlarm, localConfig.endAlarm);
    }

    /**
     * Get the start alarm number
     *
     * @return the start alarm number or 1 if not configured
     */
    public int getStartAlarm() {
        PicNetAlarmGroupConfiguration localConfig = config;
        return localConfig != null ? localConfig.startAlarm : 1;
    }

    /**
     * Get the end alarm number
     *
     * @return the end alarm number or 255 if not configured
     */
    public int getEndAlarm() {
        PicNetAlarmGroupConfiguration localConfig = config;
        return localConfig != null ? localConfig.endAlarm : 255;
    }
}
