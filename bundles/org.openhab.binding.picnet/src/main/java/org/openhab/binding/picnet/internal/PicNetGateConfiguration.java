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
 * Configuration for PicNet Gate thing.
 *
 * Gates can optionally read status from Input/Output/Virtual addresses and write commands to Virtual addresses.
 * If no status reading is configured, the gate operates in command-only mode.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetGateConfiguration {

    /**
     * Type of address to read gate status from: "input", "output", or "virtual"
     * Leave empty for command-only mode (no status reading)
     */
    public String readType = "";

    /**
     * Address to read the gate status from (1-250 for Input/Output, 1-2500 for Virtual)
     * Only used if readType is not empty
     */
    public int readAddress = 0;

    /**
     * Bit number to read gate status from (1-16)
     * Only used if readType is not empty
     */
    public int readBit = 1;

    /**
     * Virtual address to write gate commands to (1-2500)
     */
    public int virtualAddress = 0;

    /**
     * Bit number to write gate commands to (1-16)
     */
    public int writeBit = 1;

    /**
     * Pulse mode: send a pulse (1 then 0) for gate trigger command
     * Default: true (gates typically use pulse/momentary trigger)
     */
    public boolean pulseMode = true;

    /**
     * Check if status reading is configured
     */
    public boolean hasStatusReading() {
        return !readType.isEmpty() && readAddress > 0;
    }
}
