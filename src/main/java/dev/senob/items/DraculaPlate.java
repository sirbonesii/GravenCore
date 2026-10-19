package dev.senob.items;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.senob.listeners.AttributeHider;
import dev.senob.listeners.EnderChestEx;
import dev.senob.GravenCore;
import dev.senob.core.CooldownManager;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({"UnstableApiUsage", "SpellCheckingInspection", "BooleanMethodIsAlwaysInverted", "unused"})
public class DraculaPlate implements Listener, CustomItem {

    private final GravenCore plugin;
    private final CooldownManager cooldownManager;

    public static final CooldownManager.CooldownType COOLDOWN_TYPE =
            new CooldownManager.CooldownType("dracula", "🦇", TextColor.color(0xAA0000));

    private final NamespacedKey draculaKey;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey attackKey;
    private final NamespacedKey blockRateKey;
    private final NamespacedKey blockPowerKey;

    private static final Set<UUID> activeVampires = new HashSet<>();

    /**
     * Hardcoded lore templates — lore text lives here, not in the config, by design.
     * Placeholders (&lt;duration&gt;, &lt;cooldown&gt;, &lt;creator&gt;, &lt;lifesteal&gt;)
     * are replaced with live values at sync time.
     */
    private static final List<String> LORE = List.of(
            "<gold>ABILITY</gold><gray>:</gray> <dark_red><bold>VAMPIRE'S FLIGHT</bold></dark_red>",
            "<dark_gray>• <blue>Duration</blue><gray>:</gray> <yellow><duration>s 🦇</yellow>",
            "<dark_gray>• <yellow>Cooldown</yellow><gray>:</gray> <gold><cooldown>s ⏳</gold>"
    );

    private static final List<String> EFFECTS = List.of(
            "<dark_gray>• <green>Passive</green><gray>:</gray> <color:#F8F858>Regeneration I</color>",
            "<dark_gray>• <dark_red>Lifesteal</dark_red><gray>:</gray> <red><lifesteal>%</red> <gray>of damage dealt</gray>"
    );
    private static final Map<UUID, ItemStack[]> hiddenArmor = new HashMap<>();
    private static boolean isRegistered = false;

    public DraculaPlate(GravenCore plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.cooldownManager.registerType(COOLDOWN_TYPE);

        this.draculaKey = new NamespacedKey(plugin, "dracula_plate");
        this.cooldownKey = new NamespacedKey(plugin, "dracula_cooldown");
        this.durationKey = new NamespacedKey(plugin, "dracula_duration");
        this.attackKey = new NamespacedKey(plugin, "dracula_attack");
        this.blockRateKey = new NamespacedKey(plugin, "dracula_blockrate");
        this.blockPowerKey = new NamespacedKey(plugin, "dracula_blockpower");

        if (!isRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("Dracula Plate module initialized!");
            isRegistered = true;
        }
    }

    @Override
    public boolean isItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(draculaKey, PersistentDataType.INTEGER);
    }

    public boolean isWearingDraculaPlate(Player player) {
        return isItem(player.getInventory().getChestplate());
    }

    public ItemStack createDraculaPlate() {
        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(draculaKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        syncPlateStatsAndLore(item);
        return item;
    }

    public void syncPlateStatsAndLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        meta.displayName(GravenTheme.parse("<color:#F8F854><italic>Dracula's Plate</italic></color>"));
        meta.itemName(GravenTheme.parse("<color:#AA0000>Dracula's Plate</color>"));
        meta.setUnbreakable(true);

        if (!pdc.has(cooldownKey, PersistentDataType.LONG)) pdc.set(cooldownKey, PersistentDataType.LONG, GravenConfig.DRACULA_COOLDOWN);
        if (!pdc.has(durationKey, PersistentDataType.LONG)) pdc.set(durationKey, PersistentDataType.LONG, GravenConfig.DRACULA_DURATION);
        if (!pdc.has(attackKey, PersistentDataType.DOUBLE)) pdc.set(attackKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_ATTACK_DAMAGE);

        long cooldownSec = pdc.getOrDefault(cooldownKey, PersistentDataType.LONG, GravenConfig.DRACULA_COOLDOWN);
        long durationSec = pdc.getOrDefault(durationKey, PersistentDataType.LONG, GravenConfig.DRACULA_DURATION);
        double attackDamage = pdc.getOrDefault(attackKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_ATTACK_DAMAGE);

        if (!pdc.has(blockRateKey, PersistentDataType.DOUBLE)) pdc.set(blockRateKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_BLOCK_RATE);
        if (!pdc.has(blockPowerKey, PersistentDataType.DOUBLE)) pdc.set(blockPowerKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_BLOCK_POWER);
        double blockRate = pdc.getOrDefault(blockRateKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_BLOCK_RATE);
        double blockPower = pdc.getOrDefault(blockPowerKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_BLOCK_POWER);

        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(plugin, "dracula_armor"), GravenConfig.DRACULA_ARMOR, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(plugin, "dracula_toughness"), GravenConfig.DRACULA_TOUGHNESS, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey(plugin, "dracula_attack"), attackDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));

        ItemTier tier = ItemTier.fromId(GravenConfig.DRACULA_TIER);

        List<Component> lore = new ArrayList<>();
        lore.add(TooltipBuilder.tierLine(tier));
        lore.add(TooltipBuilder.durabilityLine("<blue>Unbreakable</blue>"));
        lore.add(TooltipBuilder.section("ATTRIBUTES"));
        lore.add(TooltipBuilder.attribute("⚔", "Atk Damage", "green", formatStat(attackDamage)));
        lore.add(TooltipBuilder.attribute("🛡", "Defense", "#ADD8E6", formatStat(GravenConfig.DRACULA_ARMOR)));
        lore.add(TooltipBuilder.attribute("✦", "Block Rate", "#5EEAD4", formatStat(blockRate) + "%", false));
        lore.add(TooltipBuilder.attribute("🔥", "Block Power", "#FCD34D", formatStat(blockPower)));
        lore.add(TooltipBuilder.divider());
        for (String line : LORE) {
            String parsed = line
                    .replace("<duration>", String.valueOf(durationSec))
                    .replace("<cooldown>", String.valueOf(cooldownSec))
                    .replace("<creator>", GravenConfig.CREATOR);
            lore.add(GravenTheme.parse(parsed));
        }
        lore.add(TooltipBuilder.section("EFFECTS"));
        for (String line : EFFECTS) {
            String parsed = line
                    .replace("<lifesteal>", formatStat(GravenConfig.DRACULA_LIFESTEAL_PERCENT))
                    .replace("<lifesteal_chance>", formatStat(GravenConfig.DRACULA_LIFESTEAL_CHANCE));
            lore.add(GravenTheme.parse(parsed));
        }
        lore.add(TooltipBuilder.divider());
        lore.add(TooltipBuilder.creatorFooter());

        meta.lore(lore);
        item.setItemMeta(meta);

        AttributeHider.hideAllFlags(item);
        EnderChestEx.restrictFromEnderChest(item);
    }

    private static String formatStat(double value) {
        return String.format("%.1f", value);
    }

    /**
     * Lifesteal: heals a percentage of damage dealt while wearing the plate.
     */
    @EventHandler
    public void onLifesteal(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.isDead() || !isWearingDraculaPlate(player)) return;

        double percent = GravenConfig.DRACULA_LIFESTEAL_PERCENT;
        if (percent <= 0) return;

        if (Math.random() * 100.0 > GravenConfig.DRACULA_LIFESTEAL_CHANCE) return;

        double heal = event.getFinalDamage() * (percent / 100.0);
        if (heal <= 0) return;

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, player.getHealth() + heal);
        if (newHealth > player.getHealth()) {
            player.setHealth(newHealth);
            player.getWorld().spawnParticle(Particle.DUST,
                    event.getEntity().getLocation().add(0, 1.0, 0),
                    6, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(170, 0, 0), 1.0f));
        }
    }

    /**
     * Block system: while wearing the plate, incoming attacks have a block-rate%
     * chance to be partially blocked, reducing the damage by block-power (flat).
     * A shield-clang sound and impact particles play when the block triggers.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAttackBlocked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isDead() || !isWearingDraculaPlate(player)) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || !isItem(chestplate)) return;
        PersistentDataContainer pdc = chestplate.getItemMeta().getPersistentDataContainer();

        double rate = Math.min(100.0, Math.max(0.0,
                pdc.getOrDefault(blockRateKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_BLOCK_RATE)));
        if (rate <= 0.0) return;
        if (ThreadLocalRandom.current().nextDouble(100.0) >= rate) return;

        double power = Math.max(0.0,
                pdc.getOrDefault(blockPowerKey, PersistentDataType.DOUBLE, GravenConfig.DRACULA_BLOCK_POWER));
        event.setDamage(Math.max(0.0, event.getDamage() - power));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0.0, 1.2, 0.0), 10, 0.3, 0.3, 0.3, 0.1);
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !isWearingDraculaPlate(player)) return;

        UUID uuid = player.getUniqueId();
        if (activeVampires.contains(uuid)) return;

        if (cooldownManager.isOnCooldown(player, COOLDOWN_TYPE)) return;

        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !chest.hasItemMeta()) return;
        ItemMeta meta = chest.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long cdSec = pdc.getOrDefault(cooldownKey, PersistentDataType.LONG, GravenConfig.DRACULA_COOLDOWN);
        long durSec = pdc.getOrDefault(durationKey, PersistentDataType.LONG, GravenConfig.DRACULA_DURATION);

        activeVampires.add(uuid);

        hiddenArmor.put(uuid, player.getInventory().getArmorContents().clone());
        player.getInventory().setArmorContents(new ItemStack[4]);

        boolean wasAllowFlight = player.getAllowFlight();
        boolean wasFlying = player.isFlying();

        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int) (durSec * 20), 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, (int) (durSec * 20), 0, false, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.8f);

        for (Entity entity : player.getNearbyEntities(48, 48, 48)) {
            if (entity instanceof Mob mob) {
                if (player.equals(mob.getTarget())) {
                    mob.setTarget(null);
                }
            }
        }

        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Bat bat = player.getWorld().spawn(player.getLocation(), Bat.class, b -> {
                b.setAware(false);
                b.setAwake(true);
                b.setInvulnerable(true);
                b.setSilent(true);
            });
            bats.add(bat);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (durSec * 20);

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ticks >= maxTicks || !hiddenArmor.containsKey(uuid)) {
                    endAbility();
                    return;
                }

                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 2, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.2f));

                Location pLoc = player.getLocation().add(0, 1, 0);
                float pYaw = player.getLocation().getYaw();
                float pPitch = player.getLocation().getPitch();

                for (Bat bat : bats) {
                    if (bat.isValid()) {
                        bat.setAwake(true);
                        bat.setRotation(pYaw, pPitch);

                        if (bat.getLocation().distanceSquared(pLoc) > 100) {
                            bat.teleport(pLoc);
                        } else {
                            Vector randomOffset = new Vector((Math.random() - 0.5) * 8.0, (Math.random() - 0.5) * 5.0, (Math.random() - 0.5) * 8.0);
                            Location target = pLoc.clone().add(randomOffset);
                            Vector dir = target.toVector().subtract(bat.getLocation().toVector());
                            bat.setVelocity(dir.normalize().multiply(0.75));
                        }
                    }
                }
                ticks++;
            }

            private void endAbility() {
                restoreArmor(player);

                if (player.isOnline() && !player.isDead()) {
                    if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                        player.setAllowFlight(wasAllowFlight);
                        player.setFlying(wasFlying);
                        player.setFallDistance(0);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 1200, 0, false, false, false));
                        monitorLanding(player, cdSec);
                    } else {
                        activeVampires.remove(uuid);
                        cooldownManager.setCooldown(player, COOLDOWN_TYPE, cdSec);
                    }
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.removePotionEffect(PotionEffectType.REGENERATION);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_AMBIENT, 1.0f, 0.7f);
                } else {
                    activeVampires.remove(uuid);
                    cooldownManager.setCooldown(player, COOLDOWN_TYPE, cdSec);
                }
                for (Bat bat : bats) {
                    if (bat.isValid()) bat.remove();
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void monitorLanding(Player player, long cdSec) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                    activeVampires.remove(player.getUniqueId());
                    cooldownManager.setCooldown(player, COOLDOWN_TYPE, cdSec);
                    cancel();
                    return;
                }

                if (((Entity) player).isOnGround()) {
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    activeVampires.remove(player.getUniqueId());
                    cooldownManager.setCooldown(player, COOLDOWN_TYPE, cdSec);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    private void restoreArmor(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] stored = hiddenArmor.remove(uuid);
        if (stored != null) {
            ItemStack[] current = player.getInventory().getArmorContents();
            for (int i = 0; i < stored.length; i++) {
                if (stored[i] != null && stored[i].getType() != Material.AIR) {
                    if (current[i] == null || current[i].getType() == Material.AIR) {
                        current[i] = stored[i];
                    } else {
                        player.getWorld().dropItem(player.getLocation(), stored[i]);
                    }
                }
            }
            player.getInventory().setArmorContents(current);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (hiddenArmor.containsKey(uuid)) {
            ItemStack[] stored = hiddenArmor.remove(uuid);
            for (ItemStack item : stored) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getDrops().add(item);
                }
            }
            activeVampires.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restoreArmor(player);
        activeVampires.remove(player.getUniqueId());
    }
}