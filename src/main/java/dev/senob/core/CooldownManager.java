package dev.senob.core;

import dev.senob.commands.VanishCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    /**
     * A timer shown on the action bar. {@code isDuration} marks an active
     * effect counting down (the boots' freeze time) as opposed to a
     * cooldown waiting to recharge — durations render left of the vanish
     * indicator, cooldowns right of it.
     */
    public record CooldownType(String id, String icon, TextColor color, boolean isDuration) {
        public CooldownType(String id, String icon, TextColor color) {
            this(id, icon, color, false);
        }
    }

    private final Map<String, CooldownType> registeredTypes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> activeCooldowns = new HashMap<>();

    public CooldownManager(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateActionBar(player, now);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        plugin.getLogger().info("Universal Cooldown Manager initialized!");
    }

    public void registerType(CooldownType type) {
        registeredTypes.put(type.id(), type);
    }

    public void setCooldown(Player player, CooldownType type, long seconds) {
        activeCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type.id(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean isOnCooldown(Player player, CooldownType type) {
        Map<String, Long> playerCooldowns = activeCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;

        Long expiration = playerCooldowns.get(type.id());
        return expiration != null && System.currentTimeMillis() < expiration;
    }

    public int getRemainingSeconds(Player player, CooldownType type) {
        Map<String, Long> playerCooldowns = activeCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        Long expiration = playerCooldowns.get(type.id());
        if (expiration == null) return 0;

        long now = System.currentTimeMillis();
        if (now >= expiration) return 0;

        return (int) Math.ceil((expiration - now) / 1000.0);
    }

    private void updateActionBar(Player player, long now) {
        Map<String, Long> playerCooldowns = activeCooldowns.get(player.getUniqueId());
        boolean vanished = VanishCommand.isVanishedId(player.getUniqueId());
        if ((playerCooldowns == null || playerCooldowns.isEmpty()) && !vanished) return;

        // Collect live segments, split into durations and cooldowns.
        List<Component> durations = new ArrayList<>();
        List<Component> cooldowns = new ArrayList<>();
        if (playerCooldowns != null) {
            Iterator<Map.Entry<String, Long>> iterator = playerCooldowns.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (now >= entry.getValue()) {
                    iterator.remove();
                    continue;
                }
                CooldownType type = registeredTypes.get(entry.getKey());
                if (type == null) continue;
                int secondsLeft = (int) Math.ceil((entry.getValue() - now) / 1000.0);
                Component segment = Component.text(type.icon() + " " + secondsLeft + "s", type.color());
                if (type.isDuration()) durations.add(segment);
                else cooldowns.add(segment);
            }

            if (playerCooldowns.isEmpty()) {
                activeCooldowns.remove(player.getUniqueId());
            }
        }

        // Cooldowns only show once no duration is running — while an
        // ability's duration is up, only the duration (and [👻]) is shown.
        // The cooldown itself keeps counting underneath; it just appears
        // once the duration ends (so a 45s cooldown surfacing after a 6s
        // freeze reads 39s, not 45s).
        if (!durations.isEmpty()) {
            cooldowns.clear();
        }

        // Order: durations | [👻] | cooldowns — the " | " pieces are the
        // small dividers between sections. Empty sections collapse, and
        // since cooldowns are suppressed while a duration runs, the two
        // timer sections never show together: during a freeze it reads
        // "❄ 6s | [👻]", after it "[👻] | ⏳ 39s". (The old vanish boss
        // bar is gone — this is the reminder now. The task re-sends
        // every 5 ticks, so it stays up while vanished and fades on its
        // own shortly after unvanish.)
        Component actionBarMsg = Component.empty();
        boolean first = true;
        for (Component segment : durations) {
            if (!first) actionBarMsg = actionBarMsg.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            actionBarMsg = actionBarMsg.append(segment);
            first = false;
        }
        if (vanished) {
            if (!first) actionBarMsg = actionBarMsg.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            actionBarMsg = actionBarMsg.append(Component.text("[👻]", NamedTextColor.GREEN));
            first = false;
        }
        for (Component segment : cooldowns) {
            if (!first) actionBarMsg = actionBarMsg.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            actionBarMsg = actionBarMsg.append(segment);
            first = false;
        }

        if (!first) {
            player.sendActionBar(actionBarMsg);
        }
    }
}