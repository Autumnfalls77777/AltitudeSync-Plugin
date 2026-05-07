package com.altitude.sync;

import com.altitude.sync.listeners.PlayerListener;
import com.altitude.sync.sync.ServerSync;
import com.altitude.voteplugin.VoteListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class AltitudeSync extends JavaPlugin {

    private static AltitudeSync instance;
    private Economy economy;
    private String backendUrl;
    private String apiKey;
    private ServerSync serverSync;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        // Read from the unified config
        backendUrl   = getConfig().getString("backend.url",      "https://altitude-vmzb.onrender.com");
        apiKey       = getConfig().getString("backend.api-key",  "CHANGE_ME");
        int interval = getConfig().getInt("server-sync-interval", 20);

        // ── Vault Economy ──────────────────────────────────────────────────
        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault Economy not found! Disabling AltitudeSync.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault Economy hooked successfully.");

        // ── Listeners ──────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        getLogger().info("PlayerListener registered.");

        // Register VoteListener (NuVotifier vote events)
        Bukkit.getPluginManager().registerEvents(new VoteListener(this), this);
        getLogger().info("VoteListener registered.");

        // ── Server Sync Task ───────────────────────────────────────────────
        long intervalTicks = (long) interval * 20L;
        serverSync = new ServerSync(this);
        serverSync.start(intervalTicks);
        getLogger().info("ServerSync started (every " + interval + "s).");

        getLogger().info("AltitudeSync v" + getDescription().getVersion() + " enabled.");
        getLogger().info("Backend URL: " + backendUrl);
    }

    @Override
    public void onDisable() {
        if (serverSync != null) serverSync.stop();
        getLogger().info("AltitudeSync disabled.");
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static AltitudeSync getInstance() { return instance;   }
    public Economy getEconomy()              { return economy;     }
    public String  getBackendUrl()           { return backendUrl;  }
    public String  getApiKey()               { return apiKey;      }
}
