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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PiHoleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Stefan Wunsch - Initial contribution
 */
@NonNullByDefault
public class PiHoleHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PiHoleHandler.class);

    private static final long INITIAL_DELAY_IN_SECONDS = 15;

    //private @Nullable PiHoleConfiguration config;
    final private @Nullable PiHoleConnector connection;
    private @Nullable ScheduledFuture<?> refreshJob;

    final private int refreshInterval;

    public PiHoleHandler(Thing thing, String url, String token, int refreshInterval) {
        super(thing);
        this.connection = new PiHoleConnector(url, token, refreshInterval);
        this.refreshInterval = refreshInterval;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_SUMMARY_STATUS.equals(channelUID.getId())) {
            //if (command instanceof RefreshType) {} // no explicit data refresh possible

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    public void updateChannels() {
        updateSummary();
    }

    private void updateSummary() {
        Map<String, String> summary;
        try {
            summary = connection.getSummary();
        } catch (Exception e) {
            logger.warn("Failed to get summary ({})", e.getMessage());
            return;
        }
        updateState(CHANNEL_SUMMARY_STATUS, new StringType(summary.get("status")));
        updateState(CHANNEL_SUMMARY_DNSQUERIESTODAY, new DecimalType(summary.get("dns_queries_today")));
    }

    @Override
    public void initialize() {
        logger.info("Begin initialization");
        //config = getConfigAs(PiHoleConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            if (connection.isConnected()) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Cannot connect to server");
            }

            if (refreshJob == null || refreshJob.isCancelled()) {
                logger.info("Start job updating channels every {} seconds", refreshInterval);
                refreshJob = scheduler.scheduleWithFixedDelay(this::updateChannels, INITIAL_DELAY_IN_SECONDS, refreshInterval, TimeUnit.SECONDS);
            }

            logger.info("Finished initialization");
        });
    }

    @Override
    public void dispose() {
        logger.info("Begin disposing handler");
        if (refreshJob != null && !refreshJob.isCancelled()) {
            if (refreshJob.cancel(true)) {
                refreshJob = null;
            }
        }
        logger.info("Finished disposing handler");
    }
}
