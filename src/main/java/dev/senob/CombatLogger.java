package dev.senob;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class CombatLogger implements Listener {

    private final CooldownManager cooldownManager;

    // Define the central CooldownType for Combat
    public static final CooldownManager.CooldownType COOLDOWN_TYPE =
            new CooldownManager.CooldownType("combat", "⚔", TextColor.color(0xFF5555));

    // We only need a Set now just to track state changes (entering/leaving combat)
    private final Set<UUID> activeCombat = new HashSet<>();
    private static final int COMBAT_TIME = 15;

    public CombatLogger(JavaPlugin plugin, CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
        this.cooldownManager.registerType(COOLDOWN_TYPE);

        // A lightweight tracker that ONLY checks for expiration to send the safe message
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<UUID> iterator = activeCombat.iterator();

            while (iterator.hasNext()) {
                UUID uuid = iterator.next();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    continue;
                }

                // If the central CooldownManager says their timer expired:
                if (!this.cooldownManager.isOnCooldown(player, COOLDOWN_TYPE)) {
                    iterator.remove();
                    player.sendActionBar(GravenTheme.parse("<dark_red>⚓</dark_red> <white>You are no longer in</white> <gold>combat!</gold>"));
                    player.sendMessage(GravenTheme.parse("<dark_gray>»</dark_gray> <green>You are out of combat. It is safe to log off.</green>"));
                }
            }
        }, 20L, 20L); // Check once a second
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        Player victim = null;
        Player attacker = null;

        if (event.getEntity() instanceof Player p) {
            victim = p;
        }

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (victim != null && attacker != null && victim != attacker) {
            tagPlayer(victim);
            tagPlayer(attacker);
        }
    }

    private void tagPlayer(Player player) {
        boolean wasInCombat = activeCombat.contains(player.getUniqueId());

        // Tell the central manager to start/reset the timer, and log the state locally
        cooldownManager.setCooldown(player, COOLDOWN_TYPE, COMBAT_TIME);
        activeCombat.add(player.getUniqueId());

        if (!wasInCombat) {
            player.sendMessage(GravenTheme.parse("<dark_gray>»</dark_gray> <red>You are now in combat! Logging out will result in <yellow>Death</yellow>.</red>"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (activeCombat.contains(player.getUniqueId()) && cooldownManager.isOnCooldown(player, COOLDOWN_TYPE)) {
            player.setHealth(0.0);

            // Instantly clear the cooldown and active state
            cooldownManager.setCooldown(player, COOLDOWN_TYPE, 0);
            activeCombat.remove(player.getUniqueId());

            Bukkit.broadcast(GravenTheme.parse("<dark_gray>»</dark_gray> <red>" + player.getName() + " logged out during combat and was <yellow>Killed</yellow>!</red>"));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Clear their tag so they respawn safely
        cooldownManager.setCooldown(player, COOLDOWN_TYPE, 0);
        activeCombat.remove(player.getUniqueId());
    }
}