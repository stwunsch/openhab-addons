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
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.types.UnDefType;
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
    final private PiHoleConnector connection;
    private @Nullable ScheduledFuture<?> refreshJob;

    final private int refreshInterval;

    public PiHoleHandler(Thing thing, String url, String token, int refreshInterval) {
        super(thing);
        this.connection = new PiHoleConnector(url, token, refreshInterval);
        this.refreshInterval = refreshInterval;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_ACTION_SWITCH.equals(channelUID.getId())) {
            if (command instanceof OnOffType) {
                if (command == OnOffType.ON) {
                    enable();
                } else if (command == OnOffType.OFF) {
                    disable(0);
                }
            }
        } else if (CHANNEL_ACTION_DISABLE.equals(channelUID.getId())) {
            if (command instanceof DecimalType) {
                int seconds = ((DecimalType)command).intValue();
                if (seconds >= 0) {
                    disable(seconds);
                } else {
                    logger.warn("Cannot disable Pi-Hole with seconds smaller than zero (got {})", seconds);
                }
            }
        }
    }

    private void enable() {
        logger.info("Enable PiHole");
        String status;
        try {
            status = connection.enable();
        } catch (Exception e) {
            logger.warn("Failed to enable PiHole ({})", e.getMessage());
            return;
        }
        setStatusChannels(status);
    }

    private void disable(int seconds) {
        logger.info("Disable PiHole for {} seconds (0=permanent)", seconds);
        String status;
        try {
            status = connection.disable(seconds);
        } catch (Exception e) {
            logger.warn("Failed to disable PiHole for {} seconds ({})", seconds, e.getMessage());
            return;
        }
        setStatusChannels(status);
    }

    public void updateChannels() {
        updateSummary();
    }

    private void setStatusChannels(String status) {
        updateState(CHANNEL_SUMMARY_STATUS, new StringType(status));
        if (status.equals("enabled")) {
            updateState(CHANNEL_ACTION_SWITCH, OnOffType.ON);
        } else if (status.equals("disabled")) {
            updateState(CHANNEL_ACTION_SWITCH, OnOffType.OFF);
        } else {
            updateState(CHANNEL_ACTION_SWITCH, UnDefType.UNDEF);
        }
    }

    private void updateSummary() {
        Map<String, String> summary;
        try {
            summary = connection.getSummary();
        } catch (Exception e) {
            logger.warn("Failed to get summary ({})", e.getMessage());
            return;
        }
        setStatusChannels(summary.get("status"));
        updateState(CHANNEL_SUMMARY_DNSQUERIESTODAY, new DecimalType(summary.get("dns_queries_today")));
        updateState(CHANNEL_SUMMARY_DOMAINSBEINGBLOCKED, new DecimalType(summary.get("domains_being_blocked")));
        updateState(CHANNEL_SUMMARY_ADSBLOCKEDTODAY, new DecimalType(summary.get("ads_blocked_today")));
        updateState(CHANNEL_SUMMARY_ADSPERCENTAGETODAY, new DecimalType(summary.get("ads_percentage_today")));
        updateState(CHANNEL_SUMMARY_UNIQUEDOMAINS, new DecimalType(summary.get("unique_domains")));
        updateState(CHANNEL_SUMMARY_QUERIESFORWARDED, new DecimalType(summary.get("queries_forwarded")));
        updateState(CHANNEL_SUMMARY_QUERIESCACHED, new DecimalType(summary.get("queries_cached")));
        updateState(CHANNEL_SUMMARY_CLIENTSEVERSEEN, new DecimalType(summary.get("clients_ever_seen")));
        updateState(CHANNEL_SUMMARY_UNIQUECLIENTS, new DecimalType(summary.get("unique_clients")));
        updateState(CHANNEL_SUMMARY_PRIVACYLEVEL, new DecimalType(summary.get("privacy_level")));
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
