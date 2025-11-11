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
 * Configuration for a Light (read from Input/Output/Virtual, write to Virtual)
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetLightConfiguration {

    /**
     * Type of address to read from: input, output, or virtual
     */
    public String readType = "output";

    /**
     * Address to read the light status from (1-250 for I/O, 1-2500 for V)
     */
    public int readAddress;

    /**
     * Bit number to read from (1-16)
     */
    public int readBit = 1;

    /**
     * Virtual address to write commands to (1-2500)
     */
    public int virtualAddress;

    /**
     * Bit number to write to (1-16)
     */
    public int writeBit = 1;

    /**
     * Pulse mode: send pulse (1) for both ON and OFF commands
     */
    public boolean pulseMode = false;
}
