package dev.senob;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GravenConfig {

    private static JavaPlugin plugin;

    // --- GLOBAL SETTINGS ---
    public static String CREATOR;

    // --- REDLINE SPEAR ---
    public static double REDLINE_MELEE_BASE, REDLINE_MELEE_UPGRADED;
    public static double REDLINE_ABILITY_BASE, REDLINE_ABILITY_UPGRADED;
    public static double REDLINE_RANGE_BASE, REDLINE_RANGE_UPGRADED;
    public static long REDLINE_COOLDOWN_BASE, REDLINE_COOLDOWN_UPGRADED;
    public static double REDLINE_ATTACK_SPEED;
    public static double REDLINE_LAUNCH_VELOCITY;
    public static boolean REDLINE_PIERCE_BLOCKS;
    public static List<String> REDLINE_LORE;

    // --- DRACULA PLATE ---
    public static long DRACULA_COOLDOWN;
    public static long DRACULA_DURATION;
    public static double DRACULA_ARMOR;
    public static double DRACULA_TOUGHNESS;
    public static double DRACULA_ATTACK_DAMAGE;
    public static double DRACULA_LIFESTEAL_PERCENT;
    public static double DRACULA_LIFESTEAL_CHANCE;
    public static String DRACULA_TIER;
    public static List<String> DRACULA_LORE;
    public static List<String> DRACULA_EFFECTS;

    // --- MYTHICAL ARMOR SET ---
    public static String MYTHICAL_TIER;
    public static int MYTHICAL_DURABILITY;
    /** Chance (percent, 0-100) for a worn piece to block an incoming attack. */
    public static double MYTHICAL_BLOCK_RATE;
    /** Flat damage subtracted when the block triggers. */
    public static double MYTHICAL_BLOCK_POWER;
    /** Piece id -> [defense, toughness, knockback-resistance]. */
    public static Map<String, double[]> MYTHICAL_PIECES;

    public static void init(JavaPlugin instance) {
        plugin = instance;
        plugin.saveDefaultConfig();
        reload();
    }

    public static void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // 1. GLOBAL
        CREATOR = config.getString("settings.creator", "Senob");

        // 2. REDLINE SPEAR
        REDLINE_MELEE_BASE = config.getDouble("weapons.redline.melee-damage.base", 5.0);
        REDLINE_MELEE_UPGRADED = config.getDouble("weapons.redline.melee-damage.upgraded", 5.0);
        REDLINE_ABILITY_BASE = config.getDouble("weapons.redline.ability-damage.base", 16.0);
        REDLINE_ABILITY_UPGRADED = config.getDouble("weapons.redline.ability-damage.upgraded", 22.0);
        REDLINE_RANGE_BASE = config.getDouble("weapons.redline.ability-range.base", 28.0);
        REDLINE_RANGE_UPGRADED = config.getDouble("weapons.redline.ability-range.upgraded", 28.0);
        REDLINE_COOLDOWN_BASE = config.getLong("weapons.redline.cooldown.base", 20L);
        REDLINE_COOLDOWN_UPGRADED = config.getLong("weapons.redline.cooldown.upgraded", 16L);
        REDLINE_ATTACK_SPEED = config.getDouble("weapons.redline.attack-speed", 0.87);
        REDLINE_LAUNCH_VELOCITY = config.getDouble("weapons.redline.launch-velocity", 2.85);
        REDLINE_PIERCE_BLOCKS = config.getBoolean("weapons.redline.pierce-blocks", true);

        REDLINE_LORE = config.getStringList("weapons.redline.lore");
        if (REDLINE_LORE.isEmpty()) {
            REDLINE_LORE = List.of(
                    "<gray>Lunge IV</gray>",
                    "<gray>—————————————————</gray>",
                    "<gold>ABILITY</gold><gray>:</gray> <gold><bold>CRIMSON PIERCER</bold></gold>",
                    "<gray>—————————————————</gray>",
                    "<dark_gray>• <green>Ability Damage</green><gray>: <red><ability_damage> ❤</red>",
                    "<dark_gray>• <blue>Ability Range</blue><gray>:</gray> <yellow><range> Blocks</yellow>",
                    "<dark_gray>• <yellow>Cooldown</yellow><gray>:</gray> <gold><cooldown>s 🔥</gold>",
                    "<gray>—————————————————</gray>",
                    "<dark_red>✦</dark_red> <gray>Created by</gray> <gradient:#FF3B1F:#FF9E2C><italic><creator></italic></gradient>"
            );
        }

        // 3. DRACULA PLATE
        DRACULA_COOLDOWN = config.getLong("weapons.dracula.cooldown", 30L);
        DRACULA_DURATION = config.getLong("weapons.dracula.duration", 10L);
        DRACULA_ARMOR = config.getDouble("weapons.dracula.armor", 8.0);
        DRACULA_TOUGHNESS = config.getDouble("weapons.dracula.toughness", 3.0);

        DRACULA_ATTACK_DAMAGE = config.getDouble("weapons.dracula.attack-damage", 2.0);
        DRACULA_LIFESTEAL_PERCENT = config.getDouble("weapons.dracula.lifesteal-percent", 20.0);
        DRACULA_LIFESTEAL_CHANCE = config.getDouble("weapons.dracula.lifesteal-chance", 85.0);
        DRACULA_TIER = config.getString("weapons.dracula.tier", "mythical");

        // Ability body lines. Section chrome (dividers, headers, footer)
        // is added by TooltipBuilder; keep this to content only.
        DRACULA_LORE = config.getStringList("weapons.dracula.lore");
        if (DRACULA_LORE.isEmpty()) {
            DRACULA_LORE = List.of(
                    "<gold>ABILITY</gold><gray>:</gray> <dark_red><bold>VAMPIRE'S FLIGHT</bold></dark_red>",
                    "<dark_gray>• <blue>Duration</blue><gray>:</gray> <yellow><duration>s 🦇</yellow>",
                    "<dark_gray>• <yellow>Cooldown</yellow><gray>:</gray> <gold><cooldown>s ⏳</gold>"
            );
        }

        DRACULA_EFFECTS = config.getStringList("weapons.dracula.effects");
        if (DRACULA_EFFECTS.isEmpty()) {
            DRACULA_EFFECTS = List.of(
                    "<dark_gray>• <green>Passive</green><gray>:</gray> <color:#F8F858>Regeneration I</color>",
                    "<dark_gray>• <dark_red>Lifesteal</dark_red><gray>:</gray> <red><lifesteal>%</red> <gray>of damage dealt</gray>"
            );
        }

        // 4. MYTHICAL ARMOR SET
        MYTHICAL_TIER = config.getString("armor.mythical.tier", "mythical");
        MYTHICAL_DURABILITY = config.getInt("armor.mythical.durability", 2096);
        MYTHICAL_BLOCK_RATE = config.getDouble("armor.mythical.block-rate", 25.0);
        MYTHICAL_BLOCK_POWER = config.getDouble("armor.mythical.block-power", 4.0);
        MYTHICAL_PIECES = new LinkedHashMap<>();
        loadMythicalPiece(config, "helmet", 3.0);
        loadMythicalPiece(config, "chestplate", 8.0);
        loadMythicalPiece(config, "leggings", 6.0);
        loadMythicalPiece(config, "boots", 3.0);
    }

    private static void loadMythicalPiece(FileConfiguration config, String id, double defaultDefense) {
        String base = "armor.mythical.pieces." + id;
        MYTHICAL_PIECES.put(id, new double[]{
                config.getDouble(base + ".defense", defaultDefense),
                config.getDouble(base + ".toughness", 3.0),
                config.getDouble(base + ".knockback-resistance", 0.1)
        });
    }
}