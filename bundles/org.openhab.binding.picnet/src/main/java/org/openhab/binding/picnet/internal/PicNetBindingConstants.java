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
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link PicNetBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
public class PicNetBindingConstants {

    private static final String BINDING_ID = "picnet";

    // List of all Bridge Type UIDs
    public static final ThingTypeUID BRIDGE_TYPE_PICNET = new ThingTypeUID(BINDING_ID, "bridge");

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_VIRTUAL = new ThingTypeUID(BINDING_ID, "virtual");
    public static final ThingTypeUID THING_TYPE_INPUT = new ThingTypeUID(BINDING_ID, "input");
    public static final ThingTypeUID THING_TYPE_OUTPUT = new ThingTypeUID(BINDING_ID, "output");
    public static final ThingTypeUID THING_TYPE_LIGHT = new ThingTypeUID(BINDING_ID, "light");

    // List of all Channel IDs
    public static final String CHANNEL_WORD = "word";
    public static final String CHANNEL_SWITCH_BIT = "switch-bit";
    public static final String CHANNEL_CONTACT_BIT = "contact-bit";
    public static final String CHANNEL_NUMBER_BYTE = "number-byte";
    public static final String CHANNEL_NUMBER_SIGNED = "number-signed";
}
