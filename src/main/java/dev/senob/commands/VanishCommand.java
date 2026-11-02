package dev.senob.commands;

import dev.senob.GravenCore;
import dev.senob.core.GravenTheme;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /vanish and /unvanish (permission: gravencore.admin), each with an
 * optional player argument: {@code /vanish [player]}.
 *
 * <p>Ghost mode: a vanished player is hidden from every online player
 * who lacks the admin permission (entity and player-list entry), and
 * new joiners get the same treatment. Other admins — and the vanished
 * player themself in third person — see the player as a translucent
 * ghost. That is a custom effect, not Minecraft's Invisibility potion:
 * the vanished player carries the invisible entity flag directly, and
 * every admin shares a team with see-friendly-invisibles on, so the
 * ghost renders for anyone who can see them while regular players see
 * nothing. The vanished player's player-list name gains a short green
 * {@code [👻]} after the name, and they get a green {@code [👻]} in
 * their action bar, alongside the cooldown timers, so they know
 * they're hidden. The state is stored in the player's own PDC, so it
 * survives relogs.</p>
 */
public class VanishCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String TEAM_NAME = "gravencore_admin";

    private final GravenCore plugin;
    private final NamespacedKey vanishedKey;
    // Concurrent: the async chat handler in NameObfuscationListener reads this too.
    private static final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    /** Shared lookup for the PlaceholderAPI expansion (and the CooldownManager action bar). */
    public static boolean isVanishedId(UUID id) {
        return vanished.contains(id);
    }

    public VanishCommand(GravenCore plugin) {
        this.plugin = plugin;
        this.vanishedKey = new NamespacedKey(plugin, "vanished");
    }

    public boolean isVanished(Player player) {
        return isVanishedId(player.getUniqueId());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("gravencore.admin")) {
            sender.sendMessage(GravenTheme.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        boolean vanish = command.getName().equalsIgnoreCase("vanish");

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(GravenTheme.parse("<red>Usage: /" + label + " <player></red>"));
                return true;
            }
            target = self;
        } else if (args.length == 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(GravenTheme.parse("<red>Player '" + args[0] + "' is not online.</red>"));
                return true;
            }
        } else {
            sender.sendMessage(GravenTheme.parse("<red>Usage: /" + label + " [player]</red>"));
            return true;
        }

        if (vanish) {
            vanish(target);
            if (target != sender) {
                sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>" + target.getName() + " is now vanished.</green>")));
                target.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>You are now vanished.</green>")));
            } else {
                sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>You are now vanished.</green>")));
            }
        } else {
            unvanish(target);
            if (target != sender) {
                sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>" + target.getName() + " is no longer vanished.</green>")));
                target.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>You are no longer vanished.</green>")));
            } else {
                sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>You are no longer vanished.</green>")));
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        return List.of();
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        // Mobs never acquire a vanished player as their target — for
        // any reason, including retaliation if the player hits them
        if (event.getTarget() instanceof Player target && isVanished(target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        // A vanished player's advancements are not announced: the
        // broadcast would give them away.
        if (isVanished(event.getPlayer())) event.message(null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Respawn normalizes the ghost flag to the vanish state: a
        // vanished player comes back a ghost, a non-vanished player
        // never comes back stuck invisible.
        event.getPlayer().setInvisible(isVanished(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        // Keep the admin team true: admins see vanished ghosts, and
        // anyone who lost the permission stops seeing them.
        syncAdminTeam();
        // Re-apply the joiner's own vanish if it persisted from last session
        if (joiner.getPersistentDataContainer().has(vanishedKey, PersistentDataType.BYTE)) {
            applyVanish(joiner);
        }
        // Hide everyone else's vanish from the joiner (unless they're staff)
        if (!joiner.hasPermission("gravencore.admin")) {
            for (UUID id : vanished) {
                Player hidden = Bukkit.getPlayer(id);
                if (hidden != null && hidden != joiner) {
                    joiner.hidePlayer(plugin, hidden);
                }
            }
        }
    }

    public void vanish(Player target) {
        target.getPersistentDataContainer().set(vanishedKey, PersistentDataType.BYTE, (byte) 1);
        applyVanish(target);
    }

    public void unvanish(Player target) {
        target.getPersistentDataContainer().remove(vanishedKey);
        vanished.remove(target.getUniqueId());
        // Clear the ghost flag — unless bat form is active (bat form
        // owns the invisibility then; when it ends, its potion removal
        // clears the flag anyway, so nobody gets stuck invisible).
        if (!dev.senob.items.DraculaPlate.isVampireActive(target.getUniqueId())) {
            target.setInvisible(false);
        }
        // List name + visibility recompute.
        dev.senob.listeners.NameObfuscationListener.refresh(target);
    }

    private void applyVanish(Player target) {
        vanished.add(target.getUniqueId());
        // Anything already chasing the player drops them immediately
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof org.bukkit.entity.Mob mob && target.equals(mob.getTarget())) {
                    mob.setTarget(null);
                }
            }
        }
        syncAdminTeam();
        applyGhost(target);
        // List name + visibility recompute (an invisible target keeps
        // its scrambled name, with the tag appended for admins)
        // (The self-reminder is the green [👻] in the CooldownManager
        // action bar now — no boss bar anymore.)
        dev.senob.listeners.NameObfuscationListener.refresh(target);
    }

    /** The ghost itself: the invisible entity flag, not a potion effect — no icon, no particles, nothing to milk off or expire. */
    private void applyGhost(Player target) {
        target.setInvisible(true);
    }

    /**
     * Re-applies the ghost flag if the player is vanished. For other
     * features that clear invisibility (Dracula bat form ending drops
     * its invisibility potion, which can take the flag with it): call
     * this after clearing, so bat form never breaks the ghost.
     */
    public static void restoreGhostIfVanished(Player player) {
        if (isVanishedId(player.getUniqueId())) {
            player.setInvisible(true);
        }
    }

    /**
     * Every admin shares one team with see-friendly-invisibles on, so
     * vanished admins render as translucent ghosts to other admins
     * (and to themselves in third person). The team carries no prefix,
     * suffix, or color, so non-vanished admins' nametags look normal.
     * Non-admins are pruned from it whenever it syncs.
     */
    private void syncAdminTeam() {
        Team team = adminTeam();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("gravencore.admin")) {
                team.addEntry(online.getName());
            } else {
                team.removeEntry(online.getName());
            }
        }
    }

    private Team adminTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.setCanSeeFriendlyInvisibles(true);
        }
        return team;
    }
}
