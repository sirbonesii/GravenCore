package dev.senob.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GravenConfig {

    private static JavaPlugin plugin;

    // --- GLOBAL SETTINGS ---
    public static String CREATOR;

    // --- REDLINE SPEAR ---
    public static double REDLINE_MELEE_BASE, REDLINE_MELEE_UPGRADED;
    public static String REDLINE_TIER;
    public static double REDLINE_ABILITY_BASE, REDLINE_ABILITY_UPGRADED;
    public static double REDLINE_RANGE_BASE, REDLINE_RANGE_UPGRADED;
    public static long REDLINE_COOLDOWN_BASE, REDLINE_COOLDOWN_UPGRADED;
    public static double REDLINE_ATTACK_SPEED;
    public static double REDLINE_LAUNCH_VELOCITY;
    public static boolean REDLINE_PIERCE_BLOCKS;
    public static String REDLINE_CHARGE_SOUND;
    public static float REDLINE_CHARGE_VOLUME;
    public static float REDLINE_CHARGE_PITCH;
    public static String REDLINE_PREFIRE_SOUND;
    public static float REDLINE_PREFIRE_VOLUME;
    public static float REDLINE_PREFIRE_PITCH;
    public static String REDLINE_FIRE_SOUND;
    public static float REDLINE_FIRE_VOLUME;
    public static float REDLINE_FIRE_PITCH;

    // --- REDLINE ALTAR (upgrading) ---
    public static boolean REDLINE_ALTAR_ENABLED;
    public static double REDLINE_ALTAR_RITUAL_DURATION;
    public static double REDLINE_ALTAR_SPEAR_OFFSET_X;
    public static double REDLINE_ALTAR_SPEAR_OFFSET_Y;
    public static double REDLINE_ALTAR_SPEAR_OFFSET_Z;
    public static String REDLINE_ALTAR_START_SOUND;
    public static float REDLINE_ALTAR_START_VOLUME;
    public static float REDLINE_ALTAR_START_PITCH;
    public static String REDLINE_ALTAR_COMPLETE_SOUND;
    public static float REDLINE_ALTAR_COMPLETE_VOLUME;
    public static float REDLINE_ALTAR_COMPLETE_PITCH;
    public static String REDLINE_ALTAR_ANVIL_SOUND;
    public static float REDLINE_ALTAR_ANVIL_VOLUME;
    public static float REDLINE_ALTAR_ANVIL_PITCH;
    public static String REDLINE_ALTAR_LOCK_SOUND;
    public static float REDLINE_ALTAR_LOCK_VOLUME;
    public static float REDLINE_ALTAR_LOCK_PITCH;
    public static boolean REDLINE_ALTAR_SPAWN_SOUND;
    /** When true, smithing a Legendary piece into netherite does NOT convert it to the Mythical piece. True by default: Mythical stays altar-only. */
    public static boolean SMITHING_MYTHICAL_DISABLED;
    /** When true, the altar never upgrades armor: a Legendary piece in the center just displays. False by default: the armor ritual runs. */
    public static boolean REDLINE_ALTAR_ARMOR_DISABLED;

    // Resource pack (/gravencore packreload)
    public static String RESOURCE_PACK_URL;
    public static String RESOURCE_PACK_SHA1;
    public static boolean RESOURCE_PACK_REQUIRED;
    public static String RESOURCE_PACK_PROMPT;

    // Alter of Despair (placeable altar dropper)
    // Hardcoded by design (Aaron): the alter display's scale and forced
    // brightness are fixed tuning, not config. 1.01 gives the display a
    // hair of overhang so it never z-fights the dropper; the display
    // sits inside a solid block (light 0), so brightness is forced
    // sky-only to light it like an outdoor object.
    public static final double ALTER_DISPLAY_SCALE = 1.01;
    public static final int ALTER_BRIGHTNESS_BLOCK = 0;
    public static final int ALTER_BRIGHTNESS_SKY = 15;
    public static boolean ALTER_GLINT;
    public static String ALTER_BLOCK_ID;
    public static String ALTER_DISPLAY_NAME;
    public static String ALTER_HEX_CODE;

    // --- DRACULA PLATE ---
    public static long DRACULA_COOLDOWN;
    public static long DRACULA_DURATION;
    public static double DRACULA_ARMOR;
    public static double DRACULA_TOUGHNESS;
    public static double DRACULA_ATTACK_DAMAGE;
    public static double DRACULA_LIFESTEAL_PERCENT;
    public static double DRACULA_LIFESTEAL_CHANCE;
    public static double DRACULA_BLOCK_RATE;
    public static double DRACULA_BLOCK_POWER;
    /** Block proc sound: namespaced id, volume, pitch (from sound-effects.block). */
    public static String DRACULA_BLOCK_SOUND;
    public static float DRACULA_BLOCK_VOLUME;
    public static float DRACULA_BLOCK_PITCH;
    public static String DRACULA_TIER;

    // --- ARMOR SETS (Mythical / Legendary / Epic / Rare / Uncommon / Common) ---
    /** Stats for one tiered armor set, loaded from armor_tiers.yml <tierId>.*. */
    public static class ArmorTierStats {
        public String tier;
        public int durability;
        public double blockRate;
        public double blockPower;
        public String blockSound;
        public float blockVolume;
        public float blockPitch;
        /** Piece id -> [defense, toughness, knockback-resistance]. */
        public final Map<String, double[]> pieces = new LinkedHashMap<>();
    }

    /** Tier id -> stats, for the five TieredArmorSet tiers. */
    public static final Map<String, ArmorTierStats> ARMOR_TIERS = new LinkedHashMap<>();

    // --- MYTHICAL BOOTS (standalone item, not part of the set) ---
    public static String MYTHICAL_BOOTS_TIER;
    public static int MYTHICAL_BOOTS_DURABILITY;
    /** Time Slow: shift + left-click freezes the world; the wearer is exempt plus speed boost. */
    public static boolean MYTHICAL_BOOTS_TIMESLOW_ENABLED;
    public static double MYTHICAL_BOOTS_TIMESLOW_DURATION;
    public static long MYTHICAL_BOOTS_TIMESLOW_COOLDOWN;
    public static int MYTHICAL_BOOTS_TIMESLOW_SPEEDBOOST;
    public static boolean MYTHICAL_BOOTS_TIMESLOW_FREEZEPLAYERS;
    public static int MYTHICAL_BOOTS_TIMESLOW_WEAKNESS;
    public static String MYTHICAL_BOOTS_TIMESLOW_SOUND;
    public static float MYTHICAL_BOOTS_TIMESLOW_VOLUME;
    public static float MYTHICAL_BOOTS_TIMESLOW_PITCH;
    /** Block: chance to shave flat damage off incoming attacks. */
    public static double MYTHICAL_BOOTS_BLOCK_RATE;
    public static double MYTHICAL_BOOTS_BLOCK_POWER;
    public static String MYTHICAL_BOOTS_BLOCK_SOUND;
    public static float MYTHICAL_BOOTS_BLOCK_VOLUME;
    public static float MYTHICAL_BOOTS_BLOCK_PITCH;

    // --- NAME OBFUSCATION (invisible / vanished players) ---
    /** Scramble hidden players' names in kill-feed messages and TAB. */
    public static boolean NAME_OBFUSCATION_ENABLED;
    /** False (default): invisible players are hidden from TAB entirely. */
    public static boolean NAME_OBFUSCATION_SHOW_IN_TAB;

    // --- VANISHED NAME (boss-bar title) ---
    /** Boss-bar title shown to a vanished player (config: vanished-name.display-name). */
    public static String VANISHED_NAME = "You are [👻]";

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

        // 2. WEAPONS — the Redline Spear lives in its own file
        // (special/weapons.yml, top-level weapon ids).
        FileConfiguration weapons = loadWeaponsConfig(config);
        // Dracula's Plate and the Celerity Boots are armor, so they
        // live in special/armor.yml instead.
        FileConfiguration armorCfg = loadArmorConfig(config);

        // 2. REDLINE SPEAR
        REDLINE_TIER = weapons.getString("redline.tier", "unknown");
        REDLINE_MELEE_BASE = weapons.getDouble("redline.melee-damage.base", 5.0);
        REDLINE_MELEE_UPGRADED = weapons.getDouble("redline.melee-damage.upgraded", 5.0);
        REDLINE_ABILITY_BASE = weapons.getDouble("redline.ability-damage.base", 16.0);
        REDLINE_ABILITY_UPGRADED = weapons.getDouble("redline.ability-damage.upgraded", 22.0);
        REDLINE_RANGE_BASE = weapons.getDouble("redline.ability-range.base", 28.0);
        REDLINE_RANGE_UPGRADED = weapons.getDouble("redline.ability-range.upgraded", 28.0);
        REDLINE_COOLDOWN_BASE = weapons.getLong("redline.cooldown.base", 20L);
        REDLINE_COOLDOWN_UPGRADED = weapons.getLong("redline.cooldown.upgraded", 16L);
        REDLINE_ATTACK_SPEED = weapons.getDouble("redline.attack-speed", 0.87);
        REDLINE_LAUNCH_VELOCITY = weapons.getDouble("redline.launch-velocity", 2.85);
        REDLINE_PIERCE_BLOCKS = weapons.getBoolean("redline.pierce-blocks", true);
        String[] charge = readSoundList(weapons, "redline.sound-effects.charge",
                "minecraft:block.end_portal_frame.fill", "100.0", "0.9");
        REDLINE_CHARGE_SOUND = charge[0];
        REDLINE_CHARGE_VOLUME = Float.parseFloat(charge[1]);
        REDLINE_CHARGE_PITCH = Float.parseFloat(charge[2]);
        String[] preFire = readSoundList(weapons, "redline.sound-effects.pre-fire",
                "minecraft:entity.warden.sonic_charge", "100.0", "0.6");
        REDLINE_PREFIRE_SOUND = preFire[0];
        REDLINE_PREFIRE_VOLUME = Float.parseFloat(preFire[1]);
        REDLINE_PREFIRE_PITCH = Float.parseFloat(preFire[2]);
        String[] fire = readSoundList(weapons, "redline.sound-effects.fire",
                "minecraft:entity.warden.sonic_boom", "100.0", "0.6");
        REDLINE_FIRE_SOUND = fire[0];
        REDLINE_FIRE_VOLUME = Float.parseFloat(fire[1]);
        REDLINE_FIRE_PITCH = Float.parseFloat(fire[2]);
        // The Alter of Despair and the Redline Altar ritual both live
        // in crafting_alter/alter.yml — load it once, share it.
        FileConfiguration alterCfg = loadAlterConfig(config);
        loadRedlineAltar(alterCfg);
        loadResourcePack(config);
        loadNameObfuscation(config);
        loadVanishedName(config);
        loadAlter(alterCfg);

        // 3. DRACULA PLATE
        DRACULA_COOLDOWN = armorCfg.getLong("dracula.cooldown", 30L);
        DRACULA_DURATION = armorCfg.getLong("dracula.duration", 10L);
        DRACULA_ARMOR = armorCfg.getDouble("dracula.armor", 8.0);
        DRACULA_TOUGHNESS = armorCfg.getDouble("dracula.toughness", 3.0);

        DRACULA_ATTACK_DAMAGE = armorCfg.getDouble("dracula.attack-damage", 2.0);
        DRACULA_LIFESTEAL_PERCENT = armorCfg.getDouble("dracula.lifesteal-percent", 20.0);
        DRACULA_LIFESTEAL_CHANCE = armorCfg.getDouble("dracula.lifesteal-chance", 85.0);
        DRACULA_BLOCK_RATE = armorCfg.getDouble("dracula.block-rate", 25.0);
        DRACULA_BLOCK_POWER = armorCfg.getDouble("dracula.block-power", 4.0);
        loadDraculaBlockSound(armorCfg);
        DRACULA_TIER = armorCfg.getString("dracula.tier", "unknown");

        // 4. ARMOR SETS — all six tiers, best to lowest.
        // Piece defaults are [defense (per piece), toughness, kb-resist].
        // The tiers live in their own file (tiers/armor_tiers.yml, top-level
        // tier ids); the Celerity Boots live in special/armor.yml.
        loadMythicalBoots(armorCfg);
        FileConfiguration tiers = loadTiersConfig(config);
        loadArmorTier(tiers, "mythical", 2096, 45.0, 4.0,
                new double[]{3.0, 3.0, 0.1}, new double[]{8.0, 3.0, 0.1},
                new double[]{6.0, 3.0, 0.1}, new double[]{3.0, 3.0, 0.1});
        loadArmorTier(tiers, "legendary", 1024, 35.0, 3.0,
                new double[]{3.0, 2.0, 0.0}, new double[]{8.0, 2.0, 0.0},
                new double[]{6.0, 2.0, 0.0}, new double[]{3.0, 2.0, 0.0});
        loadArmorTier(tiers, "epic", 512, 25.0, 2.0,
                new double[]{2.0, 1.0, 0.0}, new double[]{6.0, 1.0, 0.0},
                new double[]{5.0, 1.0, 0.0}, new double[]{2.0, 1.0, 0.0});
        loadArmorTier(tiers, "rare", 256, 15.0, 1.5,
                new double[]{2.0, 0.0, 0.0}, new double[]{5.0, 0.0, 0.0},
                new double[]{4.0, 0.0, 0.0}, new double[]{1.0, 0.0, 0.0});
        loadArmorTier(tiers, "uncommon", 128, 10.0, 1.0,
                new double[]{2.0, 0.0, 0.0}, new double[]{5.0, 0.0, 0.0},
                new double[]{3.0, 0.0, 0.0}, new double[]{1.0, 0.0, 0.0});
        loadArmorTier(tiers, "common", 64, 5.0, 0.5,
                new double[]{1.0, 0.0, 0.0}, new double[]{3.0, 0.0, 0.0},
                new double[]{2.0, 0.0, 0.0}, new double[]{1.0, 0.0, 0.0});
    }

    /**
     * Loads the weapons from plugins/GravenCore/special/weapons.yml
     * (copied from the jar on first run). If that file is missing,
     * the weapons.* sections of the main config are lifted into the
     * same shape, so an old live config still works — and if those
     * are gone too, the code fallbacks carry it.
     */
    private static FileConfiguration loadWeaponsConfig(FileConfiguration config) {
        File weaponsDir = new File(plugin.getDataFolder(), "special");
        if (!weaponsDir.exists()) weaponsDir.mkdirs();
        try {
            if (plugin.getResource("special/weapons.yml") != null) {
                plugin.saveResource("special/weapons.yml", false);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("weapons.yml not found in the jar; reading weapons from config.yml.");
        }
        File weaponsFile = new File(weaponsDir, "weapons.yml");
        if (weaponsFile.exists()) {
            return YamlConfiguration.loadConfiguration(weaponsFile);
        }
        YamlConfiguration lifted = new YamlConfiguration();
        ConfigurationSection weapons = config.getConfigurationSection("weapons");
        if (weapons != null) {
            for (Map.Entry<String, Object> entry : weapons.getValues(false).entrySet()) {
                if (entry.getValue() instanceof ConfigurationSection section) {
                    for (Map.Entry<String, Object> deep : section.getValues(true).entrySet()) {
                        lifted.set(entry.getKey() + "." + deep.getKey(), deep.getValue());
                    }
                }
            }
        }
        return lifted;
    }

    /**
     * Loads Dracula's Plate and the Celerity Boots from
     * plugins/GravenCore/special/armor.yml (copied from the jar on
     * first run). If that file is missing, the old locations are
     * lifted into the same shape — weapons.dracula and
     * armor.mythical-boots from the main config — so an old live
     * config still works, and if those are gone too the code
     * fallbacks carry it.
     */
    private static FileConfiguration loadArmorConfig(FileConfiguration config) {
        File armorDir = new File(plugin.getDataFolder(), "special");
        if (!armorDir.exists()) armorDir.mkdirs();
        try {
            if (plugin.getResource("special/armor.yml") != null) {
                plugin.saveResource("special/armor.yml", false);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("armor.yml not found in the jar; reading armor pieces from config.yml.");
        }
        File armorFile = new File(armorDir, "armor.yml");
        if (armorFile.exists()) {
            return YamlConfiguration.loadConfiguration(armorFile);
        }
        YamlConfiguration lifted = new YamlConfiguration();
        liftSection(config, "weapons.dracula", lifted, "dracula");
        liftSection(config, "armor.mythical-boots", lifted, "mythical-boots");
        return lifted;
    }

    /** Copies one dotted section of the main config into a top-level section. */
    private static void liftSection(FileConfiguration from, String fromPath,
                                    FileConfiguration to, String toId) {
        ConfigurationSection section = from.getConfigurationSection(fromPath);
        if (section != null) {
            for (Map.Entry<String, Object> deep : section.getValues(true).entrySet()) {
                to.set(toId + "." + deep.getKey(), deep.getValue());
            }
        }
    }

    /**
     * Loads the armor tiers from plugins/GravenCore/tiers/armor_tiers.yml
     * (copied from the jar on first run). If that file is missing,
     * the armor.* sections of the main config are lifted into the
     * same shape, so an old live config still works — and if those
     * are gone too, the code fallbacks in loadArmorTier carry it.
     */
    private static FileConfiguration loadTiersConfig(FileConfiguration config) {
        File tiersDir = new File(plugin.getDataFolder(), "tiers");
        if (!tiersDir.exists()) tiersDir.mkdirs();
        try {
            if (plugin.getResource("tiers/armor_tiers.yml") != null) {
                plugin.saveResource("tiers/armor_tiers.yml", false);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("armor_tiers.yml not found in the jar; reading armor tiers from config.yml.");
        }
        File tiersFile = new File(tiersDir, "armor_tiers.yml");
        if (tiersFile.exists()) {
            return YamlConfiguration.loadConfiguration(tiersFile);
        }
        YamlConfiguration lifted = new YamlConfiguration();
        ConfigurationSection armor = config.getConfigurationSection("armor");
        if (armor != null) {
            for (Map.Entry<String, Object> entry : armor.getValues(false).entrySet()) {
                if (entry.getValue() instanceof ConfigurationSection section) {
                    for (Map.Entry<String, Object> deep : section.getValues(true).entrySet()) {
                        lifted.set(entry.getKey() + "." + deep.getKey(), deep.getValue());
                    }
                }
            }
        }
        return lifted;
    }

    private static void loadArmorTier(FileConfiguration config, String id,
                                      int durability, double blockRate, double blockPower,
                                      double[] helmet, double[] chestplate,
                                      double[] leggings, double[] boots) {
        // armor_tiers.yml uses top-level tier ids (the lifted fallback too)
        String base = id;
        ArmorTierStats stats = new ArmorTierStats();
        stats.tier = config.getString(base + ".tier", id);
        stats.durability = config.getInt(base + ".durability", durability);
        stats.blockRate = config.getDouble(base + ".block-rate", blockRate);
        stats.blockPower = config.getDouble(base + ".block-power", blockPower);
        String[] s = readSoundList(config, base + ".sound-effects.block");
        stats.blockSound = s[0];
        stats.blockVolume = Float.parseFloat(s[1]);
        stats.blockPitch = Float.parseFloat(s[2]);
        String[] pieceIds = {"helmet", "chestplate", "leggings", "boots"};
        double[][] defaults = {helmet, chestplate, leggings, boots};
        for (int i = 0; i < pieceIds.length; i++) {
            String pieceBase = base + ".pieces." + pieceIds[i];
            stats.pieces.put(pieceIds[i], new double[]{
                    config.getDouble(pieceBase + ".defense", defaults[i][0]),
                    config.getDouble(pieceBase + ".toughness", defaults[i][1]),
                    config.getDouble(pieceBase + ".knockback-resistance", defaults[i][2])
            });
        }
        ARMOR_TIERS.put(id, stats);
    }

    private static void loadRedlineAltar(FileConfiguration config) {
        String base = "upgrading.redline-altar";
        REDLINE_ALTAR_ENABLED = config.getBoolean(base + ".enabled", true);
        REDLINE_ALTAR_RITUAL_DURATION = config.getDouble(base + ".ritual-duration", 10.0);
        REDLINE_ALTAR_SPEAR_OFFSET_X = config.getDouble(base + ".spear-offset.x", 0.0);
        REDLINE_ALTAR_SPEAR_OFFSET_Y = config.getDouble(base + ".spear-offset.y", 0.0);
        REDLINE_ALTAR_SPEAR_OFFSET_Z = config.getDouble(base + ".spear-offset.z", 0.120);
        REDLINE_ALTAR_SPAWN_SOUND = config.getBoolean(base + ".spawn-sound", true);
        SMITHING_MYTHICAL_DISABLED = config.getBoolean(base + ".disable-smithing-mythical", true);
        REDLINE_ALTAR_ARMOR_DISABLED = config.getBoolean(base + ".disable-armor", false);
        String[] s = readSoundList(config, base + ".sound-effects.start");
        REDLINE_ALTAR_START_SOUND = s[0];
        REDLINE_ALTAR_START_VOLUME = Float.parseFloat(s[1]);
        REDLINE_ALTAR_START_PITCH = Float.parseFloat(s[2]);
        String[] c = readSoundList(config, base + ".sound-effects.complete");
        REDLINE_ALTAR_COMPLETE_SOUND = c[0];
        REDLINE_ALTAR_COMPLETE_VOLUME = Float.parseFloat(c[1]);
        REDLINE_ALTAR_COMPLETE_PITCH = Float.parseFloat(c[2]);
        String[] a = readSoundList(config, base + ".sound-effects.anvil",
                "minecraft:block.anvil.land", "100.0", "0.8");
        REDLINE_ALTAR_ANVIL_SOUND = a[0];
        REDLINE_ALTAR_ANVIL_VOLUME = Float.parseFloat(a[1]);
        REDLINE_ALTAR_ANVIL_PITCH = Float.parseFloat(a[2]);
        String[] l = readSoundList(config, base + ".sound-effects.lock-in",
                "minecraft:entity.creaking.spawn", "100.0", "0.1");
        REDLINE_ALTAR_LOCK_SOUND = l[0];
        REDLINE_ALTAR_LOCK_VOLUME = Float.parseFloat(l[1]);
        REDLINE_ALTAR_LOCK_PITCH = Float.parseFloat(l[2]);
    }

    private static void loadNameObfuscation(FileConfiguration config) {
        NAME_OBFUSCATION_ENABLED = config.getBoolean("name-obfuscation.obfuscated", true);
        NAME_OBFUSCATION_SHOW_IN_TAB = config.getBoolean("name-obfuscation.show-in-tab", false);
    }

    private static void loadVanishedName(FileConfiguration config) {
        // Accepts both vanished-name.display-name and the list form
        // vanished-name: [{display-name: ...}] — anything else falls back.
        String name = config.getString("vanished-name.display-name");
        if (name == null) {
            List<?> entries = config.getList("vanished-name");
            if (entries != null && !entries.isEmpty() && entries.get(0) instanceof Map<?, ?> first) {
                Object value = first.get("display-name");
                if (value instanceof String s) name = s;
            }
        }
        VANISHED_NAME = (name == null || name.isBlank()) ? "You are [👻]" : name;
    }

    /**
     * Loads the Alter of Despair from
     * plugins/GravenCore/crafting_alter/alter.yml (copied from the
     * jar on first run). If that file is missing, the alter section
     * and the upgrading.redline-altar section of the main config
     * are lifted into the same shape, so an old live config still
     * works — and if those are gone too, the code fallbacks in
     * loadAlter/loadRedlineAltar carry it.
     */
    private static FileConfiguration loadAlterConfig(FileConfiguration config) {
        File alterDir = new File(plugin.getDataFolder(), "crafting_alter");
        if (!alterDir.exists()) alterDir.mkdirs();
        try {
            if (plugin.getResource("crafting_alter/alter.yml") != null) {
                plugin.saveResource("crafting_alter/alter.yml", false);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("alter.yml not found in the jar; reading the alter from config.yml.");
        }
        File alterFile = new File(alterDir, "alter.yml");
        if (alterFile.exists()) {
            return YamlConfiguration.loadConfiguration(alterFile);
        }
        YamlConfiguration lifted = new YamlConfiguration();
        ConfigurationSection alter = config.getConfigurationSection("alter");
        if (alter != null) {
            for (Map.Entry<String, Object> deep : alter.getValues(true).entrySet()) {
                lifted.set(deep.getKey(), deep.getValue());
            }
        }
        liftSection(config, "upgrading.redline-altar", lifted, "upgrading.redline-altar");
        return lifted;
    }

    private static void loadAlter(FileConfiguration alterCfg) {
        ALTER_GLINT = alterCfg.getBoolean("glint", true);
        ALTER_BLOCK_ID = alterCfg.getString("block", "minecraft:respawn_anchor");
        ALTER_DISPLAY_NAME = alterCfg.getString("display_name", "Alter of Despair");
        ALTER_HEX_CODE = alterCfg.getString("hex_code", "<#F8F854>");
    }

    /** The block the Alter of Despair shows, resolved from crafting_alter/alter.yml. */
    public static org.bukkit.Material alterBlockMaterial() {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(
                ALTER_BLOCK_ID != null ? ALTER_BLOCK_ID : "minecraft:respawn_anchor");
        return mat != null ? mat : org.bukkit.Material.RESPAWN_ANCHOR;
    }

    /** The item_model key for the alter item, from the same alter.block id. */
    public static org.bukkit.NamespacedKey alterItemModelKey() {
        org.bukkit.NamespacedKey key = ALTER_BLOCK_ID != null
                ? org.bukkit.NamespacedKey.fromString(ALTER_BLOCK_ID) : null;
        return key != null ? key
                : new org.bukkit.NamespacedKey(org.bukkit.NamespacedKey.MINECRAFT_NAMESPACE, "respawn_anchor");
    }

    private static void loadResourcePack(FileConfiguration config) {
        RESOURCE_PACK_URL = config.getString("resource-pack.url", "");
        RESOURCE_PACK_SHA1 = config.getString("resource-pack.sha1", "");
        RESOURCE_PACK_REQUIRED = config.getBoolean("resource-pack.required", false);
        RESOURCE_PACK_PROMPT = config.getString("resource-pack.prompt", "");
    }

    private static void loadDraculaBlockSound(FileConfiguration config) {
        String[] s = readSoundList(config, "dracula.sound-effects.block");
        DRACULA_BLOCK_SOUND = s[0];
        DRACULA_BLOCK_VOLUME = Float.parseFloat(s[1]);
        DRACULA_BLOCK_PITCH = Float.parseFloat(s[2]);
    }

    private static void loadMythicalBoots(FileConfiguration config) {
        String base = "mythical-boots";
        MYTHICAL_BOOTS_TIER = config.getString(base + ".tier", "unknown");
        MYTHICAL_BOOTS_DURABILITY = config.getInt(base + ".durability", 2096);
        MYTHICAL_BOOTS_TIMESLOW_ENABLED = config.getBoolean(base + ".time-slow.enabled", true);
        MYTHICAL_BOOTS_TIMESLOW_DURATION = config.getDouble(base + ".time-slow.duration", 6.0);
        MYTHICAL_BOOTS_TIMESLOW_COOLDOWN = config.getLong(base + ".time-slow.cooldown", 45L);
        MYTHICAL_BOOTS_TIMESLOW_SPEEDBOOST = config.getInt(base + ".time-slow.speed-boost", 1);
        MYTHICAL_BOOTS_TIMESLOW_FREEZEPLAYERS = config.getBoolean(base + ".time-slow.freeze-players", true);
        MYTHICAL_BOOTS_TIMESLOW_WEAKNESS = config.getInt(base + ".time-slow.weakness-level", 5);
        String[] s = readSoundList(config, base + ".sound-effects.time-slow");
        MYTHICAL_BOOTS_TIMESLOW_SOUND = s[0];
        MYTHICAL_BOOTS_TIMESLOW_VOLUME = Float.parseFloat(s[1]);
        MYTHICAL_BOOTS_TIMESLOW_PITCH = Float.parseFloat(s[2]);
        MYTHICAL_BOOTS_BLOCK_RATE = config.getDouble(base + ".block-rate", 25.0);
        MYTHICAL_BOOTS_BLOCK_POWER = config.getDouble(base + ".block-power", 4.0);
        String[] b = readSoundList(config, base + ".sound-effects.block");
        MYTHICAL_BOOTS_BLOCK_SOUND = b[0];
        MYTHICAL_BOOTS_BLOCK_VOLUME = Float.parseFloat(b[1]);
        MYTHICAL_BOOTS_BLOCK_PITCH = Float.parseFloat(b[2]);
    }

    /** Reads a [sound-name, volume, pitch] list; falls back to anvil defaults on any problem. */
    private static String[] readSoundList(FileConfiguration config, String path) {
        return readSoundList(config, path, "minecraft:block.anvil.land", "100.0", "1.9");
    }

    private static String[] readSoundList(FileConfiguration config, String path,
                                          String defSound, String defVolume, String defPitch) {
        // Preferred form: a map of sound: / volume: / pitch:
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            String sound = section.getString("sound", defSound);
            double volume = section.getDouble("volume", Double.parseDouble(defVolume));
            double pitch = section.getDouble("pitch", Double.parseDouble(defPitch));
            return new String[]{sound, String.valueOf((float) volume), String.valueOf((float) pitch)};
        }
        // Legacy form: [sound-name, volume, pitch]
        List<String> list = config.getStringList(path);
        if (list.size() >= 3) {
            try {
                return new String[]{
                        list.get(0),
                        String.valueOf(Float.parseFloat(list.get(1))),
                        String.valueOf(Float.parseFloat(list.get(2)))
                };
            } catch (NumberFormatException ignored) {
                // fall through to defaults
            }
        }
        return new String[]{defSound, defVolume, defPitch};
    }

}