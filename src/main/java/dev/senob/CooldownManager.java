package dev.senob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    public record CooldownType(String id, String icon, TextColor color) {}

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
        if (playerCooldowns == null || playerCooldowns.isEmpty()) return;

        Component actionBarMsg = Component.empty();
        boolean first = true;

        Iterator<Map.Entry<String, Long>> iterator = playerCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now >= entry.getValue()) {
                iterator.remove();
            } else {
                CooldownType type = registeredTypes.get(entry.getKey());
                if (type != null) {
                    int secondsLeft = (int) Math.ceil((entry.getValue() - now) / 1000.0);
                    if (!first) {
                        actionBarMsg = actionBarMsg.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
                    }
                    actionBarMsg = actionBarMsg.append(Component.text(type.icon() + " " + secondsLeft + "s", type.color()));
                    first = false;
                }
            }
        }

        if (playerCooldowns.isEmpty()) {
            activeCooldowns.remove(player.getUniqueId());
        } else {
            player.sendActionBar(actionBarMsg);
        }
    }
}