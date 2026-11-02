package dev.senob.commands;

import dev.senob.core.AdminMode;
import dev.senob.core.GravenTheme;
import dev.senob.listeners.NameObfuscationListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /admin toggle}: flip the sender's admin mode. Deliberately
 * checks the raw permission, not {@link AdminMode#isAdmin} — a
 * toggled-off admin must still be able to toggle back on.
 *
 * <p>Bare {@code /admin} reports the current state. Toggling
 * recomputes TAB visibility at once, so vanished players hide from
 * (or reveal to) the toggler immediately instead of on next relog.</p>
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(GravenTheme.parse("<red>Only players can use /admin toggle.</red>"));
            return true;
        }
        if (!sender.hasPermission("gravencore.admin")) {
            sender.sendMessage(GravenTheme.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        if (args.length == 0) {
            String state = AdminMode.isAdminMode(player) ? "<green>ON</green>" : "<red>OFF</red>";
            sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<gray>Admin mode is currently " + state + ". Usage: /admin toggle</gray>")));
            return true;
        }
        if (!args[0].equalsIgnoreCase("toggle")) {
            sender.sendMessage(GravenTheme.parse("<red>Usage: /admin toggle</red>"));
            return true;
        }
        boolean on = !AdminMode.isAdminMode(player);
        AdminMode.setAdminMode(player, on);
        NameObfuscationListener.refreshAll();
        if (on) {
            player.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>Admin mode enabled — you have full admin access.</green>")));
        } else {
            player.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<yellow>Admin mode disabled — you are playing as a regular player.</yellow>")));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) return List.of("toggle");
        return List.of();
    }
}
