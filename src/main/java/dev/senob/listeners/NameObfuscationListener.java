package dev.senob.listeners;

import dev.senob.GravenCore;
import dev.senob.commands.VanishCommand;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name obfuscation for hidden players (config: {@code name-obfuscation}).
 *
 * <p>While a player has the Invisibility effect and {@code obfuscated} is
 * on, their name renders scrambled in kill-feed death messages (killer
 * and victim), in regular chat messages, and in the TAB list; when
 * {@code show-in-tab} is off (default) they are hidden from TAB
 * entirely instead. Vanished players' names are always scrambled in
 * kill-feed and chat messages, and the vanish {@code [👻]} tag is
 * stripped from kill-feed messages so it cannot leak through the team
 * suffix. Vanish and invisibility compose: a player who is both keeps
 * the tag on their (possibly scrambled) TAB name, and stays hidden
 * from non-admins.</p>
 */
public class NameObfuscationListener implements Listener {

    private static GravenCore plugin;

    /**
     * Invisibility state mirrored from the sync potion events, so the
     * async chat handler never has to touch Bukkit entity state off
     * the main thread.
     */
    private static final Set<UUID> invisible = ConcurrentHashMap.newKeySet();

    public NameObfuscationListener(GravenCore plugin) {
        NameObfuscationListener.plugin = plugin;
    }

    /** Recompute one player's TAB name and TAB visibility from the current rules. */
    public static void refresh(Player target) {
        boolean isInvis = target.hasPotionEffect(PotionEffectType.INVISIBILITY);
        if (isInvis) invisible.add(target.getUniqueId());
        else invisible.remove(target.getUniqueId());
        boolean vanished = VanishCommand.isVanishedId(target.getUniqueId());

        // TAB list name
        if (GravenConfig.NAME_OBFUSCATION_ENABLED && isInvis) {
            target.playerListName(GravenTheme.parse("<obfuscated>" + target.getName() + "</obfuscated>"
                    + (vanished ? " <green>[👻]</green>" : "")));
        } else if (vanished) {
            target.playerListName(GravenTheme.parse(target.getName() + " <green>[👻]</green>"));
        } else {
            target.playerListName(null);
        }

        // TAB visibility: hidden from a viewer when vanished (non-admins
        // only) or when invisible and show-in-tab is off.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == target) continue;
            boolean hide = (vanished && !viewer.hasPermission("gravencore.admin"))
                    || (isInvis && !GravenConfig.NAME_OBFUSCATION_SHOW_IN_TAB);
            if (hide) viewer.hidePlayer(plugin, target);
            else viewer.showPlayer(plugin, target);
        }
    }

    /** Recompute every online player's TAB name and visibility (reload, join). */
    public static void refreshAll() {
        for (Player online : Bukkit.getOnlinePlayers()) refresh(online);
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        boolean wasInvis = event.getOldEffect() != null && event.getOldEffect().getType() == PotionEffectType.INVISIBILITY;
        boolean isInvis = event.getNewEffect() != null && event.getNewEffect().getType() == PotionEffectType.INVISIBILITY;
        if (wasInvis || isInvis) refresh(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // The joiner may be invisible/vanished, and existing hidden players
        // must be hidden from the joiner too — recompute everyone.
        refreshAll();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Death clears potion effects, so a respawn always restores the name.
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invisible.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        // Async event: only thread-safe reads here (tracked sets, never
        // Bukkit entity state). The renderer wrap swaps just the name,
        // so whatever chat format the server uses is otherwise untouched.
        Player player = event.getPlayer();
        boolean scramble = VanishCommand.isVanishedId(player.getUniqueId())
                || (GravenConfig.NAME_OBFUSCATION_ENABLED && invisible.contains(player.getUniqueId()));
        if (!scramble) return;
        ChatRenderer original = event.renderer();
        Component scrambled = Component.text(player.getName()).decorate(TextDecoration.OBFUSCATED);
        event.renderer((source, sourceDisplayName, message, viewer) ->
                original.render(source, scrambled, message, viewer));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message == null) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        Component updated = message;
        boolean touched = false;
        if (killer != null && scrambleInChat(killer)) {
            updated = scramble(updated, killer);
            touched = true;
        }
        if (scrambleInChat(victim)) {
            updated = scramble(updated, victim);
            touched = true;
        }
        if (touched) event.deathMessage(updated);
    }

    /**
     * Whether this player's name is scrambled in kill-feed messages:
     * always when vanished, otherwise only while invisible and the
     * toggle is on.
     */
    private static boolean scrambleInChat(Player player) {
        if (VanishCommand.isVanishedId(player.getUniqueId())) return true;
        return GravenConfig.NAME_OBFUSCATION_ENABLED
                && player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private static Component scramble(Component message, Player player) {
        Component out = message.replaceText(TextReplacementConfig.builder()
                .matchLiteral(player.getName())
                .replacement(Component.text(player.getName()).decorate(TextDecoration.OBFUSCATED))
                .build());
        // A vanished player's name carries the vanish team suffix in
        // broadcasts — strip it, or the tag gives them away anyway.
        if (VanishCommand.isVanishedId(player.getUniqueId())) {
            out = out.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(" [👻] Vanished")
                    .replacement(Component.empty())
                    .build());
        }
        return out;
    }
}
