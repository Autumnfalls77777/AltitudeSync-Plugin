package com.altitude.sync.listeners;

import com.altitude.sync.AltitudeSync;
import com.altitude.sync.sync.PlayerSync;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for join and quit events and triggers an async player data sync.
 * EventPriority.HIGHEST ensures Vault and LuckPerms have already processed
 * the event before we read balance/group data.
 */
public final class PlayerListener implements Listener {

    private final PlayerSync playerSync;

    public PlayerListener(AltitudeSync plugin) {
        this.playerSync = new PlayerSync(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerSync.syncAsync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSync.syncAsync(event.getPlayer());
    }
}
