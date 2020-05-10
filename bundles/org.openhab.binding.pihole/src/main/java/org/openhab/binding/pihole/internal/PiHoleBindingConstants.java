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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link PiHoleBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Stefan Wunsch - Initial contribution
 */
@NonNullByDefault
public class PiHoleBindingConstants {

    private static final String BINDING_ID = "pihole";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SERVER = new ThingTypeUID(BINDING_ID, "server");

    // List of all Channel ids
    public static final String CHANNEL_SUMMARY_STATUS = "summary:status";
    public static final String CHANNEL_SUMMARY_DNSQUERIESTODAY = "summary:dns_queries_today";
}
