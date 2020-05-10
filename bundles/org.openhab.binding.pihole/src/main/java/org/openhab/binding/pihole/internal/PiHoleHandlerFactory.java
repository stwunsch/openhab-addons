/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.pihole.internal;

import static org.openhab.binding.pihole.internal.PiHoleBindingConstants.*;

import java.util.Dictionary;
import java.util.Collections;
import java.util.Set;
import java.lang.String;
import java.lang.Integer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.ComponentContext;

/**
 * The {@link PiHoleHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Stefan Wunsch - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.pihole", service = ThingHandlerFactory.class)
public class PiHoleHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.singleton(THING_TYPE_SERVER);
    private String url = "";
    private String token = "";
    private int refreshInterval = -1;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_SERVER.equals(thingTypeUID)) {
            return new PiHoleHandler(thing, url, token, refreshInterval);
        }

        return null;
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);
        Dictionary<String, Object> properties = componentContext.getProperties();
        this.token = (String) properties.get("token");
        this.url = (String) properties.get("url");
        this.refreshInterval = Integer.parseInt((String) properties.get("refreshInterval"));
    }
}
