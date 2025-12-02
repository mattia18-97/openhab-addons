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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PicNetAlarmGroupConfiguration} class contains configuration for PicNet alarm group thing.
 * An alarm group monitors a range of alarms and reports if any alarm in the range is triggered.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetAlarmGroupConfiguration {

    /**
     * First alarm number to monitor (1-255)
     */
    public int startAlarm = 1;

    /**
     * Last alarm number to monitor (1-255)
     */
    public int endAlarm = 255;
}
