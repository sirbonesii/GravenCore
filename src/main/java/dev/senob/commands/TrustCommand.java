package dev.senob.commands;

import dev.senob.core.GravenTheme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trust System: /trust add &lt;player&gt; teams two players, /trust remove
 * &lt;player&gt; dissolves the team. Teamed players cannot hurt each other —
 * melee, projectiles (arrows, tridents, thrown potions) and the Redline
 * beam are all blocked (the beam skips trusted players outright in
 * RedlineSpear, since its fire and knockback bypass the damage event).
 *
 * Storage is plugins/GravenCore/trusts.yml (UUID -&gt; list of trusted
 * UUIDs), loaded once at startup and saved on every add/remove — so
 * trust survives restarts, and removing an offline player works exactly,
 * unlike player-PDC storage which cannot be edited while they are away.
 * An add writes BOTH directions, so one add teams both players, and one
 * remove dissolves the team fully.
 *
 * Known gaps (damage that cannot be attributed to a player): TNT ignited
 * by a player, lingering potion clouds and end crystals. Time Freeze is
 * not exempted either — it deals no damage, so it is outside the damage
 * guard; say the word and trusted players can be skipped there too.
 */
public class TrustCommand implements CommandExecutor, TabCompleter, Listener {

    private static final Map<UUID, Set<UUID>> TRUSTED = new HashMap<>();
    private static File trustFile;
    private static JavaPlugin pluginRef;

    public TrustCommand(JavaPlugin plugin) {
        trustFile = new File(plugin.getDataFolder(), "trusts.yml");
        pluginRef = plugin;
        load();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private static void load() {
        TRUSTED.clear();
        if (trustFile == null || !trustFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(trustFile);
        for (String key : cfg.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Set<UUID> set = new HashSet<>();
            for (String id : cfg.getStringList(key)) {
                try {
                    set.add(UUID.fromString(id));
                } catch (IllegalArgumentException ignored) {
                    // Skip a malformed entry, keep the rest.
                }
            }
            if (!set.isEmpty()) TRUSTED.put(owner, set);
        }
    }

    private static void save() {
        if (trustFile == null) return;
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : TRUSTED.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            List<String> ids = new ArrayList<>();
            for (UUID id : entry.getValue()) ids.add(id.toString());
            cfg.set(entry.getKey().toString(), ids);
        }
        try {
            if (trustFile.getParentFile() != null) trustFile.getParentFile().mkdirs();
            cfg.save(trustFile);
        } catch (IOException ex) {
            if (pluginRef != null) pluginRef.getLogger().warning("Failed to save trusts.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Queries — static so damage handlers and the beam can use them
    // ------------------------------------------------------------------

    /** True when a and b are teamed: a link in either direction teams them. */
    public static boolean isTrusted(Player a, Player b) {
        return isTrusted(a.getUniqueId(), b.getUniqueId());
    }

    /** True when a and b are teamed: a link in either direction teams them. */
    public static boolean isTrusted(UUID a, UUID b) {
        if (a.equals(b)) return false;
        Set<UUID> aSet = TRUSTED.get(a);
        if (aSet != null && aSet.contains(b)) return true;
        Set<UUID> bSet = TRUSTED.get(b);
        return bSet != null && bSet.contains(a);
    }

    /** Every player teamed with the given player, from either direction. */
    public static Set<UUID> teammates(UUID player) {
        Set<UUID> out = new HashSet<>(TRUSTED.getOrDefault(player, Set.of()));
        for (Map.Entry<UUID, Set<UUID>> entry : TRUSTED.entrySet()) {
            if (entry.getValue().contains(player)) out.add(entry.getKey());
        }
        return out;
    }

    private static void link(UUID a, UUID b) {
        TRUSTED.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        TRUSTED.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    private static void unlink(UUID a, UUID b) {
        Set<UUID> aSet = TRUSTED.get(a);
        if (aSet != null) {
            aSet.remove(b);
            if (aSet.isEmpty()) TRUSTED.remove(a);
        }
        Set<UUID> bSet = TRUSTED.get(b);
        if (bSet != null) {
            bSet.remove(a);
            if (bSet.isEmpty()) TRUSTED.remove(b);
        }
    }

    // ------------------------------------------------------------------
    // Damage guard — runs first, so block/lifesteal handlers (which skip
    // cancelled events) never see a trusted hit
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (isTrusted(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    /** Online player with this exact name (case-insensitive), or null. */
    private Player resolveOnline(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }
        return null;
    }

    /** The player behind a direct hit or a projectile, or null if neither. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    // ------------------------------------------------------------------
    // Command
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(GravenTheme.parse("<red>This command can only be used in-game.</red>"));
            return true;
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove"))) {
            player.sendMessage(GravenTheme.parse("<red>Usage: /trust add <player> or /trust remove <player></red>"));
            return true;
        }
        boolean add = args[0].equalsIgnoreCase("add");
        String nameArg = args[1];

        // Resolve the target: add requires them online; remove also works
        // while they are offline, matched by name against the sender's team.
        UUID targetId;
        String targetName;
        Player target = resolveOnline(nameArg);
        if (target != null) {
            targetId = target.getUniqueId();
            targetName = target.getName();
        } else if (!add) {
            UUID found = null;
            String foundName = null;
            for (UUID id : teammates(player.getUniqueId())) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                if (n != null && n.equalsIgnoreCase(nameArg)) {
                    found = id;
                    foundName = n;
                    break;
                }
            }
            if (found == null) {
                player.sendMessage(GravenTheme.parse("<gray>[Gravenmoor]</gray> <red>" + nameArg + " is not trusted.</red>"));
                return true;
            }
            targetId = found;
            targetName = foundName;
        } else {
            player.sendMessage(GravenTheme.parse("<red>Player '" + nameArg + "' is not online.</red>"));
            return true;
        }

        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage(GravenTheme.parse("<red>You can't trust yourself.</red>"));
            return true;
        }

        if (add) {
            if (isTrusted(player.getUniqueId(), targetId)) {
                player.sendMessage(GravenTheme.parse("<gray>[Gravenmoor]</gray> <red>" + targetName + " is already trusted.</red>"));
                return true;
            }
            link(player.getUniqueId(), targetId);
            save();
            Component msg = GravenTheme.parse("<gray>[Gravenmoor]</gray> <green>" + targetName + "</green> is now trusted.");
            player.sendMessage(msg);
            if (target != null) target.sendMessage(msg);
        } else {
            if (!isTrusted(player.getUniqueId(), targetId)) {
                player.sendMessage(GravenTheme.parse("<gray>[Gravenmoor]</gray> <red>" + targetName + " is not trusted.</red>"));
                return true;
            }
            unlink(player.getUniqueId(), targetId);
            save();
            Component msg = GravenTheme.parse("<gray>[Gravenmoor]</gray> <red>" + targetName + "</red> is no longer trusted.");
            player.sendMessage(msg);
            if (target != null) target.sendMessage(msg);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            return List.of("add", "remove");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add")) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) names.add(online.getName());
                }
                return names;
            }
            if (args[0].equalsIgnoreCase("remove")) {
                List<String> names = new ArrayList<>();
                for (UUID id : teammates(player.getUniqueId())) {
                    String n = Bukkit.getOfflinePlayer(id).getName();
                    if (n != null) names.add(n);
                }
                return names;
            }
        }
        return List.of();
    }
}
