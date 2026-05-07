package com.altitude.sync.sync;

import com.altitude.sync.AltitudeSync;
import com.altitude.sync.util.LuckPermsUtil;
import com.google.gson.JsonObject;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds and POSTs a player data payload to the backend.
 */
public final class PlayerSync {

    private final AltitudeSync plugin;

    public PlayerSync(AltitudeSync plugin) {
        this.plugin = plugin;
    }

    /**
     * Collects player data on the main thread, then dispatches the HTTP POST
     * asynchronously so the main thread is never blocked.
     */
    public void syncAsync(Player player) {
        final String uuid     = player.getUniqueId().toString();
        final String username = player.getName();
        final double balance  = getBalance(player);
        final long   playtime = getPlaytime(player);
        final int    kills    = getKills(player);
        final int    deaths   = getStat(player, Statistic.DEATHS);
        final String rank     = LuckPermsUtil.getPrimaryGroup(player);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> postPlayerData(uuid, username, balance, playtime, kills, deaths, rank));
    }

    // -----------------------------------------------------------------------
    // Stat helpers
    // -----------------------------------------------------------------------

    private double getBalance(Player player) {
        Economy eco = plugin.getEconomy();
        if (eco == null) return 0.0;
        try {
            return eco.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AltitudeSync] Failed to fetch balance for " + player.getName(), e);
            return 0.0;
        }
    }

    /**
     * Returns total play-time in seconds.
     * PLAY_ONE_MINUTE counts ticks despite its historic name; divide by 20 to get seconds.
     */
    private long getPlaytime(Player player) {
        try {
            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return ticks / 20L;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AltitudeSync] Failed to fetch playtime for " + player.getName(), e);
            return 0L;
        }
    }

    private int getKills(Player player) {
        try {
            return player.getStatistic(Statistic.PLAYER_KILLS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AltitudeSync] Failed to fetch kills for " + player.getName(), e);
            return 0;
        }
    }

    /** Safely reads any simple (no-argument) Statistic. */
    private int getStat(Player player, Statistic stat) {
        try {
            return player.getStatistic(stat);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AltitudeSync] Failed to read stat " + stat + " for " + player.getName(), e);
            return 0;
        }
    }

    // -----------------------------------------------------------------------
    // HTTP POST
    // -----------------------------------------------------------------------

    private void postPlayerData(String uuid, String username, double balance,
                                long playtimeSeconds, int kills, int deaths, String rank) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid",             uuid);
        json.addProperty("username",         username);
        json.addProperty("balance",          balance);
        json.addProperty("playtime_seconds", playtimeSeconds);
        json.addProperty("kills",            kills);
        json.addProperty("deaths",           deaths);
        json.addProperty("rank",             rank);

        sendPost(plugin.getBackendUrl() + "/api/update-player", json.toString(), plugin.getApiKey());
    }

    // -----------------------------------------------------------------------
    // Shared HTTP utility (package-private so ServerSync can reuse it)
    // -----------------------------------------------------------------------

    /**
     * POSTs {@code jsonBody} to {@code urlString}, authenticating via the
     * {@code x-api-key} request header.
     */
    static void sendPost(String urlString, String jsonBody, String apiKey) {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(urlString);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",   "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept",         "application/json");
            conn.setRequestProperty("x-api-key",      apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                Logger.getLogger("AltitudeSync").info(
                        "[AltitudeSync] POST to " + urlString + " succeeded — HTTP " + code);
            } else {
                Logger.getLogger("AltitudeSync").warning(
                        "[AltitudeSync] POST to " + urlString + " failed — HTTP " + code);
            }
        } catch (URISyntaxException e) {
            Logger.getLogger("AltitudeSync").log(Level.WARNING,
                    "[AltitudeSync] Invalid endpoint URL: " + urlString, e);
        } catch (IOException e) {
            Logger.getLogger("AltitudeSync").log(Level.WARNING,
                    "[AltitudeSync] HTTP POST failed for " + urlString, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
