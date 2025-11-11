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

import static org.openhab.binding.picnet.internal.PicNetBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.picnet.internal.handler.PicNetInputHandler;
import org.openhab.binding.picnet.internal.handler.PicNetLightHandler;
import org.openhab.binding.picnet.internal.handler.PicNetOutputHandler;
import org.openhab.binding.picnet.internal.handler.PicNetVirtualHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link PicNetHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Mattia Belloni - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.picnet", service = ThingHandlerFactory.class)
public class PicNetHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(BRIDGE_TYPE_PICNET, THING_TYPE_VIRTUAL,
            THING_TYPE_INPUT, THING_TYPE_OUTPUT, THING_TYPE_LIGHT);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (BRIDGE_TYPE_PICNET.equals(thingTypeUID)) {
            return new PicNetBridgeHandler((Bridge) thing);
        } else if (THING_TYPE_VIRTUAL.equals(thingTypeUID)) {
            return new PicNetVirtualHandler(thing);
        } else if (THING_TYPE_INPUT.equals(thingTypeUID)) {
            return new PicNetInputHandler(thing);
        } else if (THING_TYPE_OUTPUT.equals(thingTypeUID)) {
            return new PicNetOutputHandler(thing);
        } else if (THING_TYPE_LIGHT.equals(thingTypeUID)) {
            return new PicNetLightHandler(thing);
        }

        return null;
    }
}
