package dev.senob.items;

import dev.senob.listeners.AttributeHider;
import dev.senob.listeners.EnderChestEx;
import dev.senob.GravenCore;
import dev.senob.particle_engine.ParticleEngineUtil;
import dev.senob.commands.TrustCommand;
import dev.senob.core.CooldownManager;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

@SuppressWarnings({"UnstableApiUsage", "SpellCheckingInspection"})
public class RedlineSpear implements Listener, CustomItem {

    private final GravenCore plugin;
    private final CooldownManager cooldownManager;

    public static final CooldownManager.CooldownType COOLDOWN_TYPE =
            new CooldownManager.CooldownType("redline", "🔥", TextColor.color(0xFF8C00));

    private final NamespacedKey redlineTierKey;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey abilityDamageKey;
    private final NamespacedKey meleeDamageKey;
    private final NamespacedKey rangeKey;

    /**
     * Hardcoded lore template — lore text lives here, not in the config, by design.
     * Placeholders (&lt;ability_damage&gt;, &lt;range&gt;, &lt;cooldown&gt;, &lt;creator&gt;)
     * are replaced with live values at sync time.
     */
    private static final List<String> LORE = List.of(
            "<gray>Lunge IV</gray>",
            // No divider above the ABILITY section — the banner glyph is
            // its own separator (the top divider was dropped 2026-10-28).
            "<gold><bold>ABILITY</gold><gray>:</gray> <gold><bold>CRIMSON PIERCER</bold></gold>",
            "<gray>—————————————————</gray>",
            "<dark_gray>• <green>Ability Damage</green><gray>:</gray> <red><ability_damage> ❤</red>",
            "<dark_gray>• <blue>Ability Range</blue><gray>:</gray> <yellow><range> Blocks</yellow>",
            "<dark_gray>• <yellow>Cooldown</yellow><gray>:</gray> <gold><cooldown>s 🔥</gold>",
            "<gray>—————————————————</gray>",
            "<dark_red>✦</dark_red> <gray>Created by</gray> <gradient:#FF3B1F:#FF9E2C><italic><creator></italic></gradient>"
    );

    private final Set<UUID> chargingPlayers = new HashSet<>();
    // Players whose charge applied Slow Falling (only tracked when the
    // plugin applied it — a pre-existing Slow Falling is left alone).
    private final Set<UUID> chargeSlowFalling = new HashSet<>();
    private final Map<UUID, Long> lastRightClickTime = new HashMap<>();
    private final Map<UUID, Long> fallImmunity = new HashMap<>();

    // New charge sequence (Aaron's video, re-measured 2026-10-25, tail shortened
    // 2026-10-26, trimmed again 2026-10-26): hold right-click for 6 pulse beats
    // — three slow (18 ticks apart), then three rapid (4 apart) — then the
    // warden pre-fire sound, then the beam ~30 ticks later, right as the
    // charge sound ends — ~80 ticks total.
    private static final int PRE_FIRE_TICK = 50;
    private static final int FIRE_TICK = 80;
    private static final int RING_POINTS = 96;
    private static final int RING_LAYERS = 2;
    private static final int SHIELD_DURABILITY_DAMAGE = 180;

    private record PulseBeat(int tick, double startRadius, double expansionSpeed) {}

    private static final List<PulseBeat> PULSE_BEATS = List.of(
            new PulseBeat(0,  0.22, 0.135),
            new PulseBeat(18, 0.21, 0.123),
            new PulseBeat(36, 0.19, 0.111),
            new PulseBeat(41, 0.18, 0.099),
            new PulseBeat(45, 0.16, 0.087),
            new PulseBeat(49, 0.15, 0.075)
    );

    public RedlineSpear(GravenCore plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.cooldownManager.registerType(COOLDOWN_TYPE);

        this.redlineTierKey = new NamespacedKey(plugin, "redline_tier");
        this.cooldownKey = new NamespacedKey(plugin, "redline_cooldown");
        this.abilityDamageKey = new NamespacedKey(plugin, "redline_ability_damage");
        this.meleeDamageKey = new NamespacedKey(plugin, "redline_melee_damage");
        this.rangeKey = new NamespacedKey(plugin, "redline_range");

        plugin.getLogger().info("Redline module initialized!");
    }

    @Override
    public boolean isItem(ItemStack item) {
        return getRedlineTier(item) > 0;
    }

    public ItemStack createRedlineSpear(int tier) {
        Material baseMaterial = resolveSpearMaterial();
        ItemStack item = new ItemStack(baseMaterial);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(redlineTierKey, PersistentDataType.INTEGER, tier);
            item.setItemMeta(meta);
        }

        syncSpearStatsAndLore(item, tier);
        return item;
    }

    public void syncSpearStatsAndLore(ItemStack item, int tier) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        meta.displayName(GravenTheme.parse("<color:#F8F858><italic>Redline</italic></color>"));
        meta.itemName(GravenTheme.parse("<color:#FF3B1F>Redline</color>"));

        Enchantment lungeEnchant = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft("lunge"));
        if (lungeEnchant != null && !meta.hasEnchant(lungeEnchant)) {
            meta.addEnchant(lungeEnchant, 4, true);
        }
        if (!meta.hasEnchant(Enchantment.SHARPNESS)) meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        if (!meta.hasEnchant(Enchantment.FIRE_ASPECT)) meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.setUnbreakable(true);
        // Resource-pack models: base spear = 2000, upgraded = 2001
        meta.setCustomModelData(tier >= 2 ? 2001 : 2000);
        pdc.set(redlineTierKey, PersistentDataType.INTEGER, tier);

        double baseMelee = tier >= 2 ? GravenConfig.REDLINE_MELEE_UPGRADED : GravenConfig.REDLINE_MELEE_BASE;
        double baseAbility = tier >= 2 ? GravenConfig.REDLINE_ABILITY_UPGRADED : GravenConfig.REDLINE_ABILITY_BASE;
        double baseRange = tier >= 2 ? GravenConfig.REDLINE_RANGE_UPGRADED : GravenConfig.REDLINE_RANGE_BASE;
        long baseCooldown = tier >= 2 ? GravenConfig.REDLINE_COOLDOWN_UPGRADED : GravenConfig.REDLINE_COOLDOWN_BASE;

        if (!pdc.has(meleeDamageKey, PersistentDataType.DOUBLE)) pdc.set(meleeDamageKey, PersistentDataType.DOUBLE, baseMelee);
        if (!pdc.has(abilityDamageKey, PersistentDataType.DOUBLE)) pdc.set(abilityDamageKey, PersistentDataType.DOUBLE, baseAbility);
        if (!pdc.has(rangeKey, PersistentDataType.DOUBLE)) pdc.set(rangeKey, PersistentDataType.DOUBLE, Math.max(1.0, baseRange));
        if (!pdc.has(cooldownKey, PersistentDataType.LONG)) pdc.set(cooldownKey, PersistentDataType.LONG, baseCooldown);

        double configuredMelee = pdc.getOrDefault(meleeDamageKey, PersistentDataType.DOUBLE, baseMelee);
        double abilityDamage = pdc.getOrDefault(abilityDamageKey, PersistentDataType.DOUBLE, baseAbility);
        double abilityRange = pdc.getOrDefault(rangeKey, PersistentDataType.DOUBLE, baseRange);
        long cooldownSec = pdc.getOrDefault(cooldownKey, PersistentDataType.LONG, baseCooldown);

        AttributeModifier damageModifier = new AttributeModifier(
                NamespacedKey.minecraft("base_attack_damage"),
                configuredMelee - 1.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        );
        AttributeModifier speedModifier = new AttributeModifier(
                NamespacedKey.minecraft("base_attack_speed"),
                GravenConfig.REDLINE_ATTACK_SPEED - 4.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        );

        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

        List<Component> finalLore = new ArrayList<>();
        // Tier line, first — the Redline spear is an Unknown-tier item
        finalLore.add(TooltipBuilder.tierLine(ItemTier.fromId(GravenConfig.REDLINE_TIER)));
        for (String line : LORE) {
            if (lungeEnchant != null && line.contains("Lunge IV")) continue;

            // Glyph chrome, like the other tooltips: dash dividers become
            // the divider glyph, and the ABILITY header becomes the
            // ABILITIES banner with the ability name kept as its own line
            // (the old dash-embedded header line goes away with this,
            // including its unclosed-bold quirk).
            if (line.equals("<gray>—————————————————</gray>")) {
                finalLore.add(TooltipBuilder.divider());
                continue;
            }
            if (line.contains("CRIMSON PIERCER")) {
                finalLore.add(TooltipBuilder.section("ABILITY"));
                finalLore.add(GravenTheme.parse("<gold><bold>CRIMSON PIERCER</bold></gold>"));
                continue;
            }

            String parsed = line
                    .replace("<ability_damage>", String.valueOf(abilityDamage))
                    .replace("<range>", String.valueOf(abilityRange))
                    .replace("<cooldown>", String.valueOf(cooldownSec))
                    .replace("<creator>", GravenConfig.CREATOR);

            finalLore.add(GravenTheme.parse(parsed));
        }

        TooltipBuilder.fitDividers(finalLore);
        meta.lore(finalLore);
        item.setItemMeta(meta);

        AttributeHider.hideAllFlags(item);
        EnderChestEx.restrictFromEnderChest(item);
    }

    private Material resolveSpearMaterial() {
        for (String candidate : List.of("NETHERITE_SPEAR", "DIAMOND_SPEAR", "SPEAR", "TRIDENT", "NETHERITE_SWORD")) {
            Material mat = Material.matchMaterial(candidate);
            if (mat != null) return mat;
        }
        return Material.NETHERITE_SWORD;
    }

    public int getRedlineTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer()
                .getOrDefault(redlineTierKey, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        int tier = getRedlineTier(item);
        if (tier == 0) return;

        if (!player.isSneaking()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldownManager.isOnCooldown(player, COOLDOWN_TYPE)) return;

        lastRightClickTime.put(uuid, now);
        if (chargingPlayers.contains(uuid)) return;

        chargingPlayers.add(uuid);

        // Slow Falling for the charge window, until the ability is cast:
        // no particles (the flame rings are the visual), HUD icon kept.
        // Duration 120 ticks covers the 80-tick charge with margin; the
        // effect is removed explicitly at fire or on cancel either way.
        // Only applied if the player does not already have it, so a
        // pre-existing Slow Falling is never stripped by the cleanup.
        if (!player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, true));
            chargeSlowFalling.add(uuid);
        }

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                boolean holdingSpear = getRedlineTier(player.getInventory().getItemInMainHand()) > 0;

                boolean stillHoldingRightClick = (tick < 4)
                        ? (currentTime - lastRightClickTime.getOrDefault(uuid, 0L) <= 450L)
                        : (player.isHandRaised() || (currentTime - lastRightClickTime.getOrDefault(uuid, 0L) <= 350L));

                if (!player.isOnline() || player.isDead() || !holdingSpear || !stillHoldingRightClick) {
                    chargingPlayers.remove(uuid);
                    if (chargeSlowFalling.remove(uuid) && player.isOnline()) {
                        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    }
                    lastRightClickTime.remove(uuid);
                    cancel();
                    return;
                }

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                World world = eye.getWorld();

                for (PulseBeat beat : PULSE_BEATS) {
                    if (tick == beat.tick()) {
                        // Charge-up sound (config: weapons.yml redline.sound-effects.charge),
                        // heard only by players within 30 blocks — the plugin
                        // equivalent of /playsound ... player @a[distance=..30]
                        for (Player listener : world.getPlayers()) {
                            if (listener.getLocation().distanceSquared(eye) <= 900.0) {
                                listener.playSound(eye, GravenConfig.REDLINE_CHARGE_SOUND,
                                        SoundCategory.PLAYERS, GravenConfig.REDLINE_CHARGE_VOLUME,
                                        GravenConfig.REDLINE_CHARGE_PITCH);
                            }
                        }

                        Location ringCenter = eye.clone().add(dir.clone().multiply(2.8));
                        ParticleEngineUtil.spawnExpandingRing(ringCenter, dir, beat.startRadius(), beat.expansionSpeed(), Particle.FLAME, RING_POINTS, RING_LAYERS);
                    }
                }

                // Pre-fire sound (config: weapons.yml redline.sound-effects.pre-fire):
                // Aaron's video sequence (2026-10-25) — the warden sonic charge
                // plays once, after the last pulse, to everyone (master @a style)
                if (tick == PRE_FIRE_TICK) {
                    world.playSound(eye, GravenConfig.REDLINE_PREFIRE_SOUND,
                            SoundCategory.MASTER, GravenConfig.REDLINE_PREFIRE_VOLUME,
                            GravenConfig.REDLINE_PREFIRE_PITCH);
                }

                tick++;

                if (tick >= FIRE_TICK) {
                    chargingPlayers.remove(uuid);
                    // Charge over, beam cast: the Slow Falling window ends
                    // here (the tier ≥2 launch keeps its own 3s fall
                    // immunity for the landing).
                    if (chargeSlowFalling.remove(uuid)) {
                        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    }
                    lastRightClickTime.remove(uuid);
                    player.clearActiveItem();

                    ItemMeta meta = item != null ? item.getItemMeta() : null;
                    Long cdVal = meta != null ? meta.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG) : null;
                    long cdSeconds = cdVal != null ? cdVal : GravenConfig.REDLINE_COOLDOWN_BASE;

                    cooldownManager.setCooldown(player, COOLDOWN_TYPE, cdSeconds);

                    // The beam fires ~30 ticks after the pre-fire sound, right as
                    // the charge sound ends, and the fire sound (sonic_boom)
                    // plays inside fireRedlineBlast
                    fireRedlineBlast(player, item, tier);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        event.setCancelled(true);
    }

    private void fireRedlineBlast(Player player, ItemStack spear, int tier) {
        Location origin = player.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        World world = origin.getWorld();

        // Fire sound (config: weapons.yml redline.sound-effects.fire), played
        // to everyone like /playsound ... master @a
        world.playSound(origin, GravenConfig.REDLINE_FIRE_SOUND,
                SoundCategory.MASTER, GravenConfig.REDLINE_FIRE_VOLUME,
                GravenConfig.REDLINE_FIRE_PITCH);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.85f);
        world.playSound(origin, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.1f);
        world.playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.9f);

        if (tier >= 2) {
            Vector launchVel = dir.clone().multiply(GravenConfig.REDLINE_LAUNCH_VELOCITY);
            launchVel.setY(Math.max(0.45, launchVel.getY() * 1.15));
            player.setVelocity(launchVel);
            fallImmunity.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
        }

        ItemMeta meta = spear != null ? spear.getItemMeta() : null;
        PersistentDataContainer pdc = meta != null ? meta.getPersistentDataContainer() : null;

        double abilityDamage = pdc != null ? pdc.getOrDefault(abilityDamageKey, PersistentDataType.DOUBLE, GravenConfig.REDLINE_ABILITY_BASE) : GravenConfig.REDLINE_ABILITY_BASE;
        double maxRange = pdc != null ? pdc.getOrDefault(rangeKey, PersistentDataType.DOUBLE, GravenConfig.REDLINE_RANGE_BASE) : GravenConfig.REDLINE_RANGE_BASE;

        Particle.DustOptions crimsonCore = new Particle.DustOptions(Color.fromRGB(255, 25, 0), 1.7f);
        Particle.DustOptions orangeGlow = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.3f);
        Set<UUID> hitTargets = new HashSet<>();

        // The beam runs until it hits a solid block or reaches max range;
        // beamDist remembers how far it got, for the beam circles below
        double beamDist = 0.0;

        for (double d = 1.0; d <= maxRange; d += 0.6) {
            Location point = origin.clone().add(dir.clone().multiply(d));
            beamDist = d;

            if (!GravenConfig.REDLINE_PIERCE_BLOCKS && point.getBlock().getType().isSolid()) {
                world.spawnParticle(Particle.EXPLOSION_EMITTER, point, 1);
                break;
            }

            // A tight, clean beam: crimson core, orange glow, a lick of flame
            world.spawnParticle(Particle.DUST, point, 7, 0.10, 0.10, 0.10, 0, crimsonCore);
            world.spawnParticle(Particle.DUST, point, 4, 0.16, 0.16, 0.16, 0, orangeGlow);
            world.spawnParticle(Particle.FLAME, point, 3, 0.07, 0.07, 0.07, 0.02);

            for (Entity entity : world.getNearbyEntities(point, 2.2, 2.2, 2.2)) {
                if (entity instanceof LivingEntity target && !target.equals(player) && hitTargets.add(target.getUniqueId())) {
                    // Trusted players are immune to the beam entirely — no
                    // damage, fire, knockback or shield-break. (The damage
                    // alone would already be cancelled by the trust guard,
                    // but fire and knockback bypass the damage event.)
                    if (target instanceof Player victimPlayer && TrustCommand.isTrusted(player, victimPlayer)) continue;
                    if (target instanceof Player victim && victim.isBlocking()) {
                        victim.setCooldown(Material.SHIELD, 120);
                        damageShield(victim);
                        world.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.8f);
                    }

                    target.damage(abilityDamage, player);
                    target.setFireTicks(120);
                    target.setVelocity(dir.clone().multiply(1.35).setY(0.55));

                    Location hitLoc = target.getLocation().add(0, 1.0, 0);
                    world.spawnParticle(Particle.EXPLOSION, hitLoc, 3, 0.3, 0.3, 0.3, 0);
                    world.spawnParticle(Particle.LAVA, hitLoc, 15, 0.4, 0.4, 0.4, 0);
                    world.spawnParticle(Particle.DUST, hitLoc, 30, 0.6, 0.6, 0.6, 0, crimsonCore);
                }
            }
        }

        // Beam circles at the end of the beam — the charge-up's expanding
        // flame rings, replayed at the far end (Aaron's diagram, 2026-10-21):
        // three of them along the beam's final stretch, crowding together
        // toward the tip and rippling outward in sequence, each one exactly
        // the same ring as the first charge-up pulse.
        double[] circlePositions = {0.74, 0.88, 0.99};
        PulseBeat startRing = PULSE_BEATS.get(0);
        for (int i = 0; i < circlePositions.length; i++) {
            double at = Math.max(1.2, beamDist * circlePositions[i]);
            Location circleCenter = origin.clone().add(dir.clone().multiply(at));
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    ParticleEngineUtil.spawnExpandingRing(circleCenter, dir, startRing.startRadius(), startRing.expansionSpeed(),
                            Particle.FLAME, RING_POINTS, RING_LAYERS), i * 2L);
        }
    }

    private void damageShield(Player victim) {
        for (ItemStack handItem : List.of(victim.getInventory().getItemInMainHand(), victim.getInventory().getItemInOffHand())) {
            if (handItem.getType() == Material.SHIELD && handItem.getItemMeta() instanceof Damageable damageable) {
                damageable.setDamage(damageable.getDamage() + SHIELD_DURABILITY_DAMAGE);
                handItem.setItemMeta(damageable);
            }
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Long immunityUntil = fallImmunity.get(player.getUniqueId());
        if (immunityUntil != null && System.currentTimeMillis() < immunityUntil) {
            event.setCancelled(true);
            fallImmunity.remove(player.getUniqueId());
        }
    }
}