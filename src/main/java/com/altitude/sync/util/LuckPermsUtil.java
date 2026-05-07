package com.altitude.sync.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for reading LuckPerms data.
 */
public final class LuckPermsUtil {

    private static final Logger LOGGER = Logger.getLogger("AltitudeSync");

    private LuckPermsUtil() {}

    /**
     * Returns the primary group for the given online player.
     * Falls back to "default" on any error.
     */
    public static String getPrimaryGroup(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                LOGGER.warning("[AltitudeSync] LuckPerms user not cached for: " + player.getName());
                return "default";
            }
            String group = user.getPrimaryGroup();
            return (group == null || group.isBlank()) ? "default" : group;
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "[AltitudeSync] LuckPerms provider unavailable.", e);
            return "default";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[AltitudeSync] Error reading LuckPerms group for " + player.getName(), e);
            return "default";
        }
    }
}
