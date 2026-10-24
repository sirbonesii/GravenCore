package dev.senob.home_system;

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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    /** Header written at the top of homes_db.yml so the file explains itself. */
    private static final String HEADER =
            "# ==================================================================\n" +
            "#  GravenCore — Player Homes Database\n" +
            "#  Managed by the plugin. You may edit this file by hand, but\n" +
            "#  changes only take effect after a server restart.\n" +
            "#  Layout: players.<uuid>.name, then homes.<name> with world,\n" +
            "#  x, y, z, yaw and pitch. Players are sorted by name and\n" +
            "#  homes alphabetically; coordinates are rounded for\n" +
            "#  readability (positions to 2 decimals, angles to 1).\n" +
            "# ==================================================================\n";

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;
    private final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    /** Last known player name per UUID, for readable sorting and the name field. */
    private final Map<UUID, String> playerNames = new HashMap<>();
    /** Fallbacks matching the old hardcoded limits, used if homes_config.yml is missing. */
    private static final int FALLBACK_DEFAULT_LIMIT = 5;
    private static final Map<String, Integer> FALLBACK_LIMITS = Map.of(
            "gravehomes.limit.10", 10,
            "gravehomes.limit.19", 19);
    /** Homes for players with no limit permission (from homes_config.yml). */
    private int defaultLimit = FALLBACK_DEFAULT_LIMIT;
    /** Permission node -> home limit (from homes_config.yml). */
    private final Map<String, Integer> limitPermissions = new LinkedHashMap<>(FALLBACK_LIMITS);
    /**
     * Homes whose world was not loaded (unknown or missing world key).
     * They are unusable in-game but are kept verbatim on save so a
     * temporarily unloaded world never eats a player's homes.
     * uuid -> home name -> raw key/value pairs from the file.
     */
    private final Map<UUID, Map<String, Map<String, Object>>> orphanHomes = new HashMap<>();

    public HomeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadHomesConfig();
        reloadHomes();
    }

    /**
     * Loads homes_config.yml (copied from the jar on first run).
     * Permission nodes contain dots, so they are read as literal keys
     * via getValues(false) — a dotted path lookup would misread them
     * as nested sections.
     */
    public void reloadHomesConfig() {
        File homesDir = new File(plugin.getDataFolder(), "homes");
        if (!homesDir.exists()) homesDir.mkdirs();
        try {
            if (plugin.getResource("homes/homes_config.yml") != null) {
                plugin.saveResource("homes/homes_config.yml", false);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("homes_config.yml not found in the jar; using built-in home limits.");
            return;
        }
        File configFile = new File(homesDir, "homes_config.yml");
        if (!configFile.exists()) {
            plugin.getLogger().warning("homes_config.yml not found; using built-in home limits.");
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        defaultLimit = cfg.getInt("limits.default", FALLBACK_DEFAULT_LIMIT);
        ConfigurationSection perms = cfg.getConfigurationSection("limits.permissions");
        if (perms != null) {
            limitPermissions.clear();
            for (Map.Entry<String, Object> entry : perms.getValues(false).entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    limitPermissions.put(entry.getKey(), number.intValue());
                }
            }
        }
        if (limitPermissions.isEmpty()) limitPermissions.putAll(FALLBACK_LIMITS);
    }

    /**
     * Picks the data file. Homes live in their own folder inside the
     * plugin folder: plugins/GravenCore/homes/homes_db.yml. Existing
     * servers still have homes.yml at the plugin root — on first boot
     * it is moved into the folder as homes_db.yml so no homes are
     * lost. If the move (and a copy fallback) both fail, the old file
     * is loaded instead so nothing is ever lost to a rename.
     */
    private void resolveFile() {
        File homesDir = new File(plugin.getDataFolder(), "homes");
        if (!homesDir.exists()) homesDir.mkdirs();
        File newFile = new File(homesDir, "homes_db.yml");
        File oldFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!newFile.exists() && oldFile.exists()) {
            try {
                Files.move(oldFile.toPath(), newFile.toPath());
                plugin.getLogger().info("Migrated homes.yml to homes_db.yml.");
            } catch (IOException e) {
                try {
                    Files.copy(oldFile.toPath(), newFile.toPath());
                    plugin.getLogger().info("Copied homes.yml to homes_db.yml (old file kept).");
                } catch (IOException e2) {
                    plugin.getLogger().warning("Could not migrate homes.yml to homes_db.yml; loading the old file instead.");
                    file = oldFile;
                    return;
                }
            }
        }
        file = newFile;
    }

    public void reloadHomes() {
        if (file == null) {
            resolveFile();
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create parent directories for " + file.getName());
            }
            try {
                if (file.createNewFile()) {
                    plugin.getLogger().info("Created new " + file.getName() + " file.");
                    Files.writeString(file.toPath(), HEADER);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create " + file.getName() + ": " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        playerHomes.clear();
        playerNames.clear();
        orphanHomes.clear();

        ConfigurationSection playersSec = config.getConfigurationSection("players");
        if (playersSec != null) {
            for (String uuidStr : playersSec.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                String storedName = config.getString("players." + uuidStr + ".name");
                if (storedName != null) playerNames.put(uuid, storedName);

                Map<String, Location> homes = new HashMap<>();
                Map<String, Map<String, Object>> orphans = new LinkedHashMap<>();
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
                        } else {
                            ConfigurationSection homeSec = homesSec.getConfigurationSection(homeName);
                            if (homeSec != null) {
                                orphans.put(homeName.toLowerCase(), new LinkedHashMap<>(homeSec.getValues(false)));
                            }
                        }
                    }
                }
                playerHomes.put(uuid, homes);
                if (!orphans.isEmpty()) orphanHomes.put(uuid, orphans);
            }
        }
    }

    /**
     * Rewrites homes_db.yml from scratch in its professional form: players
     * sorted by name (with a readable name field beside each UUID),
     * homes sorted alphabetically, coordinates rounded, and the header
     * comment on top. Players with no homes left get no section.
     * Orphaned homes (unknown world) are written back verbatim.
     */
    public void saveAll() {
        YamlConfiguration fresh = new YamlConfiguration();

        List<UUID> players = new ArrayList<>(playerHomes.keySet());
        players.addAll(orphanHomes.keySet().stream()
                .filter(uuid -> !playerHomes.containsKey(uuid)).toList());
        players.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));

        for (UUID uuid : players) {
            String base = "players." + uuid + ".";
            Map<String, Location> homes = playerHomes.getOrDefault(uuid, Collections.emptyMap());
            Map<String, Map<String, Object>> orphans = orphanHomes.getOrDefault(uuid, Collections.emptyMap());
            List<String> names = new ArrayList<>(homes.keySet());
            names.addAll(orphans.keySet().stream().filter(n -> !homes.containsKey(n)).toList());
            names.sort(String::compareToIgnoreCase);
            // Nobody home: no section at all, not even a bare name
            if (names.isEmpty()) continue;

            String name = playerNames.get(uuid);
            if (name == null) {
                String lookedUp = Bukkit.getOfflinePlayer(uuid).getName();
                if (lookedUp != null) {
                    name = lookedUp;
                    playerNames.put(uuid, name);
                }
            }
            if (name != null) fresh.set(base + "name", name);

            for (String homeName : names) {
                String path = base + "homes." + homeName + ".";
                Location loc = homes.get(homeName);
                if (loc != null && loc.getWorld() != null) {
                    fresh.set(path + "world", loc.getWorld().getName());
                    fresh.set(path + "x", round(loc.getX(), 2));
                    fresh.set(path + "y", round(loc.getY(), 2));
                    fresh.set(path + "z", round(loc.getZ(), 2));
                    fresh.set(path + "yaw", round(loc.getYaw(), 1));
                    fresh.set(path + "pitch", round(loc.getPitch(), 1));
                } else {
                    // Orphan: keep every stored key, in the standard field order
                    Map<String, Object> raw = orphans.get(homeName);
                    if (raw != null) {
                        for (String key : List.of("world", "x", "y", "z", "yaw", "pitch")) {
                            if (raw.containsKey(key)) fresh.set(path + key, raw.get(key));
                        }
                        for (Map.Entry<String, Object> entry : raw.entrySet()) {
                            if (!List.of("world", "x", "y", "z", "yaw", "pitch").contains(entry.getKey())) {
                                fresh.set(path + entry.getKey(), entry.getValue());
                            }
                        }
                    }
                }
            }
        }

        config = fresh;
        try {
            config.save(file);
            ensureHeader();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + file.getName() + "!");
        }
    }

    /** SnakeYAML drops comments on save, so re-add the header if it is missing. */
    private void ensureHeader() throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        boolean hasHeader = lines.stream().limit(2).anyMatch(l -> l.startsWith("#  GravenCore"));
        if (!hasHeader) {
            List<String> withHeader = new ArrayList<>(List.of(HEADER.split("\n")));
            withHeader.addAll(lines);
            Files.write(file.toPath(), withHeader);
        }
    }

    private String displayName(UUID uuid) {
        String name = playerNames.get(uuid);
        if (name != null) return name;
        String lookedUp = Bukkit.getOfflinePlayer(uuid).getName();
        return lookedUp != null ? lookedUp : uuid.toString();
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    public @NotNull Map<String, Location> getHomes(@NotNull UUID uuid) {
        return playerHomes.getOrDefault(uuid, Collections.emptyMap());
    }

    public void setHome(@NotNull UUID uuid, @NotNull String name, @NotNull Location location) {
        String lowerName = name.toLowerCase();
        if (location.getWorld() == null) return;
        playerHomes.computeIfAbsent(uuid, k -> new HashMap<>()).put(lowerName, location);
        // A fresh, loadable home replaces any orphan of the same name
        Map<String, Map<String, Object>> orphans = orphanHomes.get(uuid);
        if (orphans != null) orphans.remove(lowerName);
        String lookedUp = Bukkit.getOfflinePlayer(uuid).getName();
        if (lookedUp != null) playerNames.put(uuid, lookedUp);
        saveAll();
    }

    public void deleteHome(@NotNull UUID uuid, @NotNull String name) {
        String lowerName = name.toLowerCase();
        Map<String, Location> homes = playerHomes.get(uuid);
        if (homes != null) homes.remove(lowerName);
        Map<String, Map<String, Object>> orphans = orphanHomes.get(uuid);
        if (orphans != null) orphans.remove(lowerName);
        saveAll();
    }

    /**
     * Highest limit among the limit permissions the player holds
     * (LuckPerms inheritance can stack them), or the default.
     */
    public int getMaxHomes(@NotNull Player player) {
        int max = defaultLimit;
        for (Map.Entry<String, Integer> entry : limitPermissions.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                max = Math.max(max, entry.getValue());
            }
        }
        return max;
    }
}