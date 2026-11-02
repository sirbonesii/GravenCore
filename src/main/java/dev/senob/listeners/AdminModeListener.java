package dev.senob.listeners;

import dev.senob.core.AdminMode;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Server-wide half of admin mode: while an admin has mode OFF, the
 * commands in {@code admin-mode.blocked-commands} are refused, so
 * they can only run basic commands like a regular player (or a
 * Senior-Mod) would.
 *
 * <p>Only admins in OFF mode are restricted here — everyone else
 * keeps exactly the permissions their groups give them, so a
 * Senior-Mod's own commands are never touched by this list. Labels
 * are matched without namespace, so "give" also blocks
 * "/minecraft:give". "/admin" itself is never blocked: it is the
 * way back on.</p>
 */
public class AdminModeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("gravencore.admin")) return;
        if (AdminMode.isAdminMode(player)) return;
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        String label = message.substring(1).split(" ")[0];
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        label = label.toLowerCase();
        if (label.equals("admin")) return;
        if (GravenConfig.ADMIN_MODE_BLOCKED_COMMANDS.contains(label)) {
            event.setCancelled(true);
            player.sendMessage(GravenTheme.prefix().append(GravenTheme.parse(
                    "<red>Admin mode is off — that command is blocked. Use /admin toggle to turn admin mode back on.</red>")));
        }
    }
}
