package dev.senob.core;

import dev.senob.GravenCore;
import dev.senob.commands.VanishCommand;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
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
 *
 * <p>{@code %gravencore_invis_name%} — the player's name for the TAB
 * plugin's player-list name format: empty while invisible and
 * {@code name-obfuscation.show-in-tab} is off, the name scrambled
 * while invisible and {@code obfuscated} is on, the plain name
 * otherwise. Swap it in for {@code %player%} and the list follows
 * the invisibility state.</p>
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
        if (params.equalsIgnoreCase("invis_name")) {
            if (player == null) return null;
            if (player instanceof Player online
                    && online.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                if (!GravenConfig.NAME_OBFUSCATION_SHOW_IN_TAB) return "";
                if (GravenConfig.NAME_OBFUSCATION_ENABLED) return "&k" + online.getName();
            }
            return player.getName();
        }
        return null;
    }
}
