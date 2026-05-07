package com.altitude.sync.sync;

import com.altitude.sync.AltitudeSync;
import com.google.gson.JsonObject;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Periodically collects server statistics and POSTs them to the backend.
 * Runs fully asynchronously — the main thread is never touched inside sync().
 */
public final class ServerSync {

    private final AltitudeSync plugin;
    private BukkitTask task;

    public ServerSync(AltitudeSync plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the repeating async task.
     *
     * @param intervalTicks period between syncs in ticks (20 ticks = 1 second)
     */
    public void start(long intervalTicks) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::sync, intervalTicks, intervalTicks);
    }

    /** Cancels the repeating task on plugin disable. */
    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    // -----------------------------------------------------------------------
    // Sync logic (runs off main thread)
    // -----------------------------------------------------------------------

    private void sync() {
        try {
            // getTPS() returns double[]{1-min avg, 5-min avg, 15-min avg}
            double[] tpsArray  = plugin.getServer().getTPS();
            double   tps       = Math.min(20.0, tpsArray[0]);
            double   tpsRounded = Math.round(tps * 100.0) / 100.0;

            int onlinePlayers = plugin.getServer().getOnlinePlayers().size();

            JsonObject json = new JsonObject();
            json.addProperty("tps",            tpsRounded);
            json.addProperty("online_players", onlinePlayers);

            PlayerSync.sendPost(
                    plugin.getBackendUrl() + "/api/update-server",
                    json.toString(),
                    plugin.getApiKey()
            );

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AltitudeSync] ServerSync error during periodic sync.", e);
        }
    }
}
