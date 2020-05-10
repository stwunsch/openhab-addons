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

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.client.api.ContentResponse;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PiHoleConnector} class connects to the Pi-Hole server.
 *
 * @author Stefan Wunsch - Initial contribution
 */
@NonNullByDefault
public class PiHoleConnector {

    private final Logger logger = LoggerFactory.getLogger(PiHoleConnector.class);

    private HttpClient httpClient;
    private @Nullable String url;
    private @Nullable String token;
    private int timeout;
    JsonParser parser;

    public PiHoleConnector(String url, String token, int refreshInterval) {
        url = url;
        if (url.charAt(url.length() - 1) == '/') {
            this.url = url.substring(0, url.length() - 1);
        } else {
            this.url = url;
        }
        token = token;
        timeout = refreshInterval;

        SslContextFactory sslContextFactory = new SslContextFactory(true);
        httpClient = new HttpClient(sslContextFactory);
        try {
            httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start http client: " + e.getMessage());
        }

        parser = new JsonParser();
    }

    public Map<String, String> getSummary() {
        String content = sendRequest(url + "/admin/api.php?summary");

        JsonElement element = parser.parse(content);
        JsonObject obj = element.getAsJsonObject();

        Map<String, String> summary = new HashMap<String, String>();
        summary.put("status", obj.get("status").getAsString());
        summary.put("dns_queries_today", obj.get("dns_queries_today").getAsString().replace(",", ""));
        return summary;
    }

    public boolean isConnected() {
        try {
            sendRequest("");
            return true;
        } catch (Exception e) {
            logger.warn("Failed to send test request to {} ({})", url, e.getMessage());
            return false;
        }
    }

    private String sendRequest(String request) {
        ContentResponse contentResponse;
        try {
            contentResponse = httpClient.newRequest(request).method(GET).timeout(timeout, TimeUnit.SECONDS).send();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        int httpStatus = contentResponse.getStatus();
        if (httpStatus != OK_200) {
            throw new RuntimeException("Request returned http status " + httpStatus);
        }
        return contentResponse.getContentAsString();
    }
}
