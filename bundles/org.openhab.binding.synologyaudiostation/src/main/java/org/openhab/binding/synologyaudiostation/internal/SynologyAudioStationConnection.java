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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

/**
 * The {@link SynologyAudioStationConnection} is responsible for handling the connection to the Audio Station
 *
 * @author Stefan Wunsch - Initial contribution
 */
@NonNullByDefault
public class SynologyAudioStationConnection {

    private final Logger logger = LoggerFactory.getLogger(SynologyAudioStationConnection.class);

    final private HttpClient httpClient;
    final private String username;
    final private String password;
    final private String url;
    private String sessionId= "";
    private String name = "";
    private String playerId = "";

    public SynologyAudioStationConnection(String username, String password, String url) {
        logger.info("Create Audio Station connection for user {} with URL {}", username, url);
        this.username = username;
        this.password = password;
        this.url = url;

        SslContextFactory sslContextFactory = new SslContextFactory(true);
        this.httpClient = new HttpClient(sslContextFactory);
        try {
            this.httpClient.start();
        } catch (Exception e) {
            logger.info("Failed to start http client ({})", e.getMessage());
            throw new RuntimeException();
        }
    }

    public boolean is_connected() {
        try {
            send_request("");
            return true;
        } catch (Exception e) {
            logger.info("Failed to connect ({})", e.getMessage());
            return false;
        }
    }

    public boolean set_name(String name) {
        this.name = name;
        String command = String.format("/webapi/AudioStation/remote_player.cgi?api=SYNO.AudioStation.RemotePlayer&version=2&method=list&_sid=%s", this.sessionId);
        try {
            String content = send_request(command);
            this.playerId = get_player_id(content);
            return true;
        } catch (Exception e) {
            logger.info("Failed to get player id for remote player {} ({})", name, e.getMessage());
            return false;
        }
    }

    public void send_command(String action) {
        String command = String.format("/webapi/AudioStation/remote_player.cgi?api=SYNO.AudioStation.RemotePlayer&version=2&method=control&_sid=%s&id=%s&action=%s", this.sessionId, this.playerId, action);
        try {
            send_request(command);
        } catch (Exception e) {
            logger.info("Failed to send command {} ({})", action, e.getMessage());
        }
    }

    public void set_volume(int volume) {
        String command = String.format("/webapi/AudioStation/remote_player.cgi?api=SYNO.AudioStation.RemotePlayer&version=2&method=control&_sid=%s&id=%s&action=set_volume&value=%s", this.sessionId, this.playerId, volume);
        try {
            send_request(command);
        } catch (Exception e) {
            logger.info("Failed to set volume to {} ({})", volume, e.getMessage());
        }
    }

    public Map<String,String> get_status() {
        Map<String,String> status = new HashMap<String,String>();
        String command = String.format("/webapi/AudioStation/remote_player.cgi?api=SYNO.AudioStation.RemotePlayer&version=2&method=getstatus&_sid=%s&id=%s&additional=song_tag", this.sessionId, this.playerId);
        try {
            String content = send_request(command);
            JsonElement data = get_data(content);
            status.put("volume", data.getAsJsonObject().get("volume").getAsString());
        } catch (Exception e) {
            logger.info("Failed to update status with request {} ({})", command, e.getMessage());
        }
        return status;
    }

    private String get_player_id(String json) {
        try {
            JsonElement data = get_data(json);
            JsonArray players = data.getAsJsonObject().get("players").getAsJsonArray();
            for (JsonElement player : players) {
                JsonObject obj = player.getAsJsonObject();
                String name = obj.get("name").getAsString();
                if (name.equals(this.name)) {
                    String id = obj.get("id").getAsString();
                    return id;
                }
            }
            logger.info("Failed remote player {} in list of players", this.name);
            throw new RuntimeException();
        } catch (Exception e) {
            logger.info("Failed parse json with player id for remote player {} ({})", this.name, e.getMessage());
            throw new RuntimeException();
        }
    }

    private JsonElement get_data(String json) {
        boolean success = false;
        JsonParser parser = new JsonParser();
        JsonElement data;
        try {
            JsonElement element = parser.parse(json);
            JsonObject obj = element.getAsJsonObject();
            success = obj.get("success").getAsBoolean();
            data = obj.get("data");
        } catch (Exception e) {
            logger.info("Failed to parse json {}", json);
            throw new RuntimeException();
        }
        if (!success) {
            logger.info("Failed to get valid data from json {}", json);
            throw new RuntimeException();
        }
        return data;
    }

    private String get_session_id(String json) {
        try {
            JsonElement data = get_data(json);
            JsonObject obj = data.getAsJsonObject();
            String sid = obj.get("sid").getAsString();
            return sid;
        } catch (Exception e) {
            logger.info("Failed to get session id ({})", e.getMessage());
            throw new RuntimeException();
        }
    }

    public boolean login() {
        String command = String.format("/webapi/auth.cgi?api=SYNO.API.Auth&method=login&version=3&account=%s&passwd=%s&session=AudioStation&format=sid", this.username, this.password);
        try {
            String content = send_request(command);
            this.sessionId = get_session_id(content);
            logger.info("Logged in user {} with session id {}", this.username, this.sessionId);
            return true;
        } catch (Exception e) {
            logger.info("Failed to login user {} ({})", this.username, e.getMessage());
            return false;
        }
    }

    public boolean logout() {
        // TODO
        return true;
    }

    private String send_request(String command) {
        String request = url + command;
        final long timeout = 5;
        logger.debug("Send request {}", request);
        try {
            ContentResponse contentResponse = this.httpClient.newRequest(request).method(GET).timeout(timeout, TimeUnit.SECONDS).send();
            int httpStatus = contentResponse.getStatus();
            if (httpStatus != OK_200) {
                logger.info("Failed to send request (status {})", httpStatus);
                throw new RuntimeException();
            }
            String content = contentResponse.getContentAsString();
            return content;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.info("Failed to send request {} ({})", request, e.getMessage());
            throw new RuntimeException();
        }
    }
}
