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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.ContentResponse;

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

    public SynologyAudioStationConnection(String username, String password, String url) {
        logger.info("Create Audio Station connection for user {} with URL {}", username, url);
        this.httpClient = new HttpClient();
        this.username = username;
        this.password = password;
        this.url = url;
    }

    public boolean is_connected() {
        try {
            String data = send_request("");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String send_request(String command) {
        String request = url + "/" + command;
        final long timeout = 5;
        logger.info("Send request {}", request);
        try {
            ContentResponse contentResponse = httpClient.newRequest(url).method(GET).timeout(timeout, TimeUnit.SECONDS).send();
            int httpStatus = contentResponse.getStatus();
            String content = contentResponse.getContentAsString();
            logger.info("Got response {} for request {}", httpStatus, request);
            return content;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.info("Failed to send request {}", request);
            throw new RuntimeException();
        }
    }
}
