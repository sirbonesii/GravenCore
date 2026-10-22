package dev.senob.core;

import dev.senob.GravenCore;
import dev.senob.commands.VanishCommand;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion (identifier: {@code gravencore}). Only
 * registered when PlaceholderAPI is installed (softdepend).
 *
 * <p>{@code %gravencore_vanish%} — a green {@code [👻]} while the
 * player is vanished, empty otherwise. Built for the TAB plugin's
 * player-list name format: append it after the name and the tag
 * follows the vanish state.</p>
 */
public class GravenPlaceholders extends PlaceholderExpansion {

    private final GravenCore plugin;

    public GravenPlaceholders(GravenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gravencore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Senob";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("vanish")) {
            return (player != null && VanishCommand.isVanishedId(player.getUniqueId())) ? "&a[👻]" : "";
        }
        return null;
    }
}
