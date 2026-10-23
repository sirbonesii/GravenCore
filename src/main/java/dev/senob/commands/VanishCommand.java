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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /vanish and /unvanish (permission: gravencore.admin), each with an
 * optional player argument: {@code /vanish [player]}.
 *
 * <p>A vanished player is hidden from every online player who lacks
 * the admin permission (entity and player-list entry), and new joiners
 * get the same treatment. Admins keep seeing the vanished player,
 * whose nameplate gains a green {@code [👻] Vanished} tag and whose
 * player-list name gains a short green {@code [👻]} after the name.
 * The vanished player gets a repeating action-bar reminder so they
 * know they're hidden. The state is stored in the player's own PDC,
 * so it survives relogs.</p>
 */
public class VanishCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String TAG = "[👻] Vanished";
    private static final String TEAM_NAME = "gravencore_vanish";

    private final GravenCore plugin;
    private final NamespacedKey vanishedKey;
    private static final Set<UUID> vanished = new HashSet<>();

    /** Shared lookup for the PlaceholderAPI expansion. */
    public static boolean isVanishedId(UUID id) {
        return vanished.contains(id);
    }

    public VanishCommand(GravenCore plugin) {
        this.plugin = plugin;
        this.vanishedKey = new NamespacedKey(plugin, "vanished");

        // Persistent self-reminder for vanished players
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : vanished) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        player.sendActionBar(GravenTheme.parse("<green>You are " + TAG + "</green>"));
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
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
        // broadcast would give them away, and it renders their name
        // with the vanish team's suffix — the [👻] Vanished tag —
        // included. Setting the message to null suppresses it.
        if (isVanished(event.getPlayer())) event.message(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
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
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != target) viewer.showPlayer(plugin, target);
        }
        vanishTeam().removeEntry(target.getName());
        target.playerListName(null);
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
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != target && !viewer.hasPermission("gravencore.admin")) {
                viewer.hidePlayer(plugin, target);
            }
        }
        vanishTeam().addEntry(target.getName());
        // TAB list gets the short tag only: green [👻] after the name
        target.playerListName(GravenTheme.parse(target.getName() + " <green>[👻]</green>"));
    }

    private Team vanishTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.suffix(GravenTheme.parse(" <green>" + TAG + "</green>"));
        }
        return team;
    }
}
