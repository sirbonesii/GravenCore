package dev.senob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;
    private final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();

    public HomeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadHomes();
    }

    public void reloadHomes() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "homes.yml");
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create parent directories for homes.yml");
            }
            try {
                if (file.createNewFile()) {
                    plugin.getLogger().info("Created new homes.yml file.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create homes.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        playerHomes.clear();

        ConfigurationSection playersSec = config.getConfigurationSection("players");
        if (playersSec != null) {
            for (String uuidStr : playersSec.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Location> homes = new HashMap<>();
                String path = "players." + uuidStr + ".homes.";

                ConfigurationSection homesSec = config.getConfigurationSection(path);
                if (homesSec != null) {
                    for (String homeName : homesSec.getKeys(false)) {
                        String worldName = config.getString(path + homeName + ".world");
                        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                        if (world != null) {
                            double x = config.getDouble(path + homeName + ".x");
                            double y = config.getDouble(path + homeName + ".y");
                            double z = config.getDouble(path + homeName + ".z");
                            float yaw = (float) config.getDouble(path + homeName + ".yaw");
                            float pitch = (float) config.getDouble(path + homeName + ".pitch");
                            homes.put(homeName.toLowerCase(), new Location(world, x, y, z, yaw, pitch));
                        }
                    }
                }
                playerHomes.put(uuid, homes);
            }
        }
    }

    public void saveConfigSafely() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save homes.yml!");
        }
    }

    public @NotNull Map<String, Location> getHomes(@NotNull UUID uuid) {
        return playerHomes.getOrDefault(uuid, Collections.emptyMap());
    }

    public void setHome(@NotNull UUID uuid, @NotNull String name, @NotNull Location location) {
        String lowerName = name.toLowerCase();
        playerHomes.computeIfAbsent(uuid, k -> new HashMap<>()).put(lowerName, location);

        // Removed unnecessary .toString() call on UUID
        String path = "players." + uuid + ".homes." + lowerName + ".";
        if (location.getWorld() != null) {
            config.set(path + "world", location.getWorld().getName());
            config.set(path + "x", location.getX());
            config.set(path + "y", location.getY());
            config.set(path + "z", location.getZ());
            config.set(path + "yaw", location.getYaw());
            config.set(path + "pitch", location.getPitch());
            saveConfigSafely();
        }
    }

    public void deleteHome(@NotNull UUID uuid, @NotNull String name) {
        Map<String, Location> homes = playerHomes.get(uuid);
        if (homes != null) {
            String lowerName = name.toLowerCase();
            homes.remove(lowerName);

            String path = "players." + uuid + ".homes." + lowerName;
            config.set(path, null);
            saveConfigSafely();
        }
    }

    public int getMaxHomes(@NotNull Player player) {
        return player.hasPermission("gravehomes.limit.19") ? 19 :
                player.hasPermission("gravehomes.limit.10") ? 10 : 5;
    }
}