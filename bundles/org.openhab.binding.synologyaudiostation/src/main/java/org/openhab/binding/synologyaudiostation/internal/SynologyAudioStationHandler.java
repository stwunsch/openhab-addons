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
package org.openhab.binding.synologyaudiostation.internal;

import static org.openhab.binding.synologyaudiostation.internal.SynologyAudioStationBindingConstants.*;

import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SynologyAudioStationHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Stefan Wunsch - Initial contribution
 */
@NonNullByDefault
public class SynologyAudioStationHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SynologyAudioStationHandler.class);

    private @Nullable SynologyAudioStationConfiguration config;

    private static final long INITIAL_DELAY_IN_SECONDS = 30;

    private SynologyAudioStationConnection connection;
    private int refreshInterval;
    private final List<String> allowedCommands = Arrays.asList("play", "pause", "stop", "next", "prev");
    private @Nullable ScheduledFuture<?> refreshJob;

    public SynologyAudioStationHandler(Thing thing, String username, String password, String url, int refreshInterval) {
        super(thing);
        this.connection = new SynologyAudioStationConnection(username, password, url);
        this.refreshInterval = refreshInterval;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_ACTION_PLAYER.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                updateStatus();
                return;
            }
            if (command instanceof PlayPauseType) {
                if (command == PlayPauseType.PLAY) {
                    connection.send_command("play");
                } else if (command == PlayPauseType.PAUSE) {
                    connection.send_command("pause");
                }
                return;
            }
            if (command instanceof NextPreviousType) {
                if (command == NextPreviousType.NEXT) {
                    connection.send_command("next");
                } else if (command == NextPreviousType.PREVIOUS) {
                    connection.send_command("prev");
                }
                return;
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to handle player command " + command.toFullString());
            return;
        }
        if (CHANNEL_ACTION_CONTROL.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                updateStatus();
                return;
            }
            if (command instanceof StringType) {
                String commandstr = command.toString();
                if (allowedCommands.contains(commandstr)) {
                    connection.send_command(commandstr);
                    return;
                } else if (commandstr == null) {
                    return;
                } else {
                    logger.info("Received unknown command {}", commandstr);
                }
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to handle control command " + command.toFullString());
            return;
        }
        if (CHANNEL_ACTION_VOLUME.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                updateStatus();
                return;
            }
            if (command instanceof DecimalType) {
                int volume = (int) Float.parseFloat(command.toString());
                if (volume >= 0 && volume <= 100) {
                    connection.set_volume(volume);
                }
                return;
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to set volume from value " + command.toFullString());
            return;
        }
        if (CHANNEL_GROUP_STATUS.equals(channelUID.getGroupId())) {
            if (command instanceof RefreshType) {
                updateStatus();
                return;
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to handle status command " + command.toFullString());
        }
    }

    private void updateStatus() {
        Map<String,String> status = null;
        try {
            status = connection.get_status();
        } catch (Exception e) {
            logger.info("Failed to get new status ({})", e.getMessage());
            return;
        }
        try {
            status = connection.get_status();
            String state = status.get("state");
            if (state.equals("playing")) {
                updateState(CHANNEL_ACTION_PLAYER, PlayPauseType.PLAY);
            } else if (state.equals("pause") || state.equals("stopped")) {
                updateState(CHANNEL_ACTION_PLAYER, PlayPauseType.PAUSE);
            }
            updateState(CHANNEL_ACTION_VOLUME, new PercentType(status.get("volume")));
            updateState(CHANNEL_STATUS_STATE , new StringType(state));
            updateState(CHANNEL_STATUS_ALBUM, new StringType(status.get("album")));
            updateState(CHANNEL_STATUS_ALBUM_ARTIST, new StringType(status.get("album_artist")));
            updateState(CHANNEL_STATUS_ARTIST, new StringType(status.get("artist")));
            updateState(CHANNEL_STATUS_TITLE, new StringType(status.get("title")));
        } catch (Exception e) {
            logger.info("Failed to set new status ({})", e.getMessage());
            return;
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(SynologyAudioStationConfiguration.class);
        logger.info("Start initializing remote player with name {}", config.name);

        // Initialize the handler.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean is_connected = connection.is_connected();
            boolean is_logged_in = connection.login();
            boolean has_player = connection.set_name(config.name);
            if (is_logged_in && is_connected && has_player) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
                logger.info("Failed to login remote player with name {} (is_logged_in: {}, has_player: {}, is_connected: {})",
                        config.name, is_logged_in, has_player, is_connected);
            }

            if (refreshJob == null || refreshJob.isCancelled()) {
                logger.debug("Start refresh job with interval of {} seconds", refreshInterval);
                refreshJob = scheduler.scheduleWithFixedDelay(this::updateStatus, INITIAL_DELAY_IN_SECONDS,
                        refreshInterval, TimeUnit.SECONDS);
            }
        });

        // logger.debug("Finished initializing!");
        logger.info("Finish initialization of remote player with name {}", config.name);

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void dispose() {
        logger.info("Dispose remote player with name {}", config.name);
        if (refreshJob != null && !refreshJob.isCancelled()) {
            if (refreshJob.cancel(true)) {
                refreshJob = null;
            }
        }
        connection.logout();
    }
}
