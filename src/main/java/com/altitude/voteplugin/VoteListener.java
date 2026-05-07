package com.altitude.voteplugin;

import com.altitude.sync.AltitudeSync;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class VoteListener implements Listener {

    private final AltitudeSync plugin;
    private final Logger log;

    public VoteListener(AltitudeSync plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        // --- Extract all vote data up-front, on the event thread ---
        String username  = event.getVote().getUsername();
        String site      = event.getVote().getServiceName();
        long   timestamp = System.currentTimeMillis();

        FileConfiguration config = plugin.getConfig();
        boolean debug = config.getBoolean("settings.debug", true);

        // Always log receipt — this is the only proof a vote arrived
        log.info("[Vote] Received vote → player='" + username + "', site='" + site + "'");

        // Run HTTP call async — NEVER block the main/vote thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                sendVote(username, site, timestamp, config, debug)
        );
    }

    // -------------------------------------------------------------------------
    // Core send logic — always runs off the main thread
    // -------------------------------------------------------------------------
    private void sendVote(String username, String site, long timestamp,
                          FileConfiguration config, boolean debug) {

        String baseUrl  = config.getString("backend.url",           "https://altitude-vmzb.onrender.com");
        String endpoint = config.getString("backend.vote-endpoint", "/api/vote");
        String apiKey   = config.getString("backend.api-key",       "");
        String fullUrl  = baseUrl + endpoint;

        if (debug) {
            log.info("[Vote] Sending POST → " + fullUrl
                    + " | username=" + username
                    + ", site=" + site
                    + ", timestamp=" + timestamp);
        }

        // Build JSON body: { "username": "...", "site": "...", "timestamp": 000 }
        String body = "{"
                + "\"username\":\"" + escapeJson(username) + "\","
                + "\"site\":\""     + escapeJson(site)     + "\","
                + "\"timestamp\":"  + timestamp
                + "}";

        // Attempt with 1 automatic retry on failure
        boolean success = attemptSend(fullUrl, apiKey, body, username, site, debug);
        if (!success) {
            log.warning("[Vote] First attempt failed — retrying once for '" + username + "' ...");
            boolean retrySuccess = attemptSend(fullUrl, apiKey, body, username, site, debug);
            if (!retrySuccess) {
                log.severe("[Vote] RETRY ALSO FAILED — vote for '" + username
                        + "' from '" + site + "' was NOT recorded. "
                        + "Check backend connectivity or API key.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Single HTTP attempt — returns true on HTTP 200, false on anything else
    // -------------------------------------------------------------------------
    private boolean attemptSend(String fullUrl, String apiKey, String body,
                                String username, String site, boolean debug) {

        HttpURLConnection connection = null;
        try {
            URL url = new URL(fullUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept",       "application/json");
            connection.setRequestProperty("x-api-key",   apiKey);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(bodyBytes);
            }

            int responseCode = connection.getResponseCode();

            if (debug) {
                log.info("[Vote] Response code: " + responseCode
                        + " for player='" + username + "', site='" + site + "'");
            }

            if (responseCode == 200) {
                log.info("[Vote] SUCCESS — vote for '" + username
                        + "' from '" + site + "' accepted (HTTP 200).");
                return true;
            } else {
                log.warning("[Vote] FAILED — backend returned HTTP " + responseCode
                        + " for player='" + username + "', site='" + site + "'. "
                        + "Verify the endpoint and API key in config.yml.");
                return false;
            }

        } catch (java.net.SocketTimeoutException e) {
            log.severe("[Vote] TIMEOUT connecting to backend for '" + username + "': " + e.getMessage());
            return false;
        } catch (java.net.UnknownHostException e) {
            log.severe("[Vote] UNKNOWN HOST — check 'backend.url' in config.yml: " + e.getMessage());
            return false;
        } catch (Exception e) {
            log.severe("[Vote] ERROR sending vote for '" + username + "': " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Safely escape user-supplied strings before embedding in JSON
    // -------------------------------------------------------------------------
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
