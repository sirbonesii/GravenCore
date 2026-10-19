package dev.senob.items;

import dev.senob.GravenCore;
import dev.senob.core.CooldownManager;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Standalone Mythical boots (not part of the Mythical armor set), in the same
 * style as Dracula's Plate: its own item, config section and ability.
 *
 * Ability — Time Slow: shift + left-click freezes the world for a few
 * seconds. Tick freeze natively exempts players, so the wearer is truly
 * unaffected (plus a small speed boost) while everything else stops.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class MythicalBoots implements Listener, CustomItem {

    private final GravenCore plugin;
    private final CooldownManager cooldownManager;

    public static final CooldownManager.CooldownType COOLDOWN_TYPE =
            new CooldownManager.CooldownType("mythical-boots-timeslow", "⏳", TextColor.color(0x5EEAD4));

    private final NamespacedKey bootsKey;

    /** Guard for overlapping freezes: the first trigger's pre-freeze state wins. */
    private static BukkitTask activeRestoreTask = null;
    private static boolean preSlowFrozen = false;
    /** Players exempt from the movement lock (the activators). */
    private static final Set<UUID> timeSlowExempt = new HashSet<>();
    /** Whether the active freeze pins other players (from the triggering boots). */
    private static boolean activeFreezePlayers = true;

    public MythicalBoots(GravenCore plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.cooldownManager.registerType(COOLDOWN_TYPE);
        this.bootsKey = new NamespacedKey(plugin, "mythical_boots");
        plugin.getLogger().info("Mythical Boots module initialized!");
    }

    /**
     * The PDC key marking the boots. Registered with DurabilityLoreListener
     * so the durability lore line stays live as the boots take damage.
     */
    public NamespacedKey idKey() {
        return bootsKey;
    }

    @Override
    public boolean isItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(bootsKey, PersistentDataType.INTEGER);
    }

    public boolean isWearingMythicalBoots(Player player) {
        return isItem(player.getInventory().getBoots());
    }

    // --- Per-item time-slow tuning: PDC override, else config default ---

    private double slowDuration(ItemStack boots) {
        return pdcDouble(boots, "mythical_boots_duration", GravenConfig.MYTHICAL_BOOTS_TIMESLOW_DURATION);
    }

    private long slowCooldown(ItemStack boots) {
        return pdcLong(boots, "mythical_boots_cooldown", GravenConfig.MYTHICAL_BOOTS_TIMESLOW_COOLDOWN);
    }

    private int slowSpeedBoost(ItemStack boots) {
        return pdcInt(boots, "mythical_boots_speedboost", GravenConfig.MYTHICAL_BOOTS_TIMESLOW_SPEEDBOOST);
    }

    private boolean slowFreezePlayers(ItemStack boots) {
        return pdcBoolean(boots, "mythical_boots_freezeplayers", GravenConfig.MYTHICAL_BOOTS_TIMESLOW_FREEZEPLAYERS);
    }

    private int slowWeaknessLevel(ItemStack boots) {
        return pdcInt(boots, "mythical_boots_weakness", GravenConfig.MYTHICAL_BOOTS_TIMESLOW_WEAKNESS);
    }

    private double pdcDouble(ItemStack item, String key, double def) {
        if (item == null || !item.hasItemMeta()) return def;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, key), PersistentDataType.DOUBLE, def);
    }

    private long pdcLong(ItemStack item, String key, long def) {
        if (item == null || !item.hasItemMeta()) return def;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, key), PersistentDataType.LONG, def);
    }

    private int pdcInt(ItemStack item, String key, int def) {
        if (item == null || !item.hasItemMeta()) return def;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, key), PersistentDataType.INTEGER, def);
    }

    private boolean pdcBoolean(ItemStack item, String key, boolean def) {
        if (item == null || !item.hasItemMeta()) return def;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, key), PersistentDataType.BOOLEAN, def);
    }

    public ItemStack createMythicalBoots() {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(bootsKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        syncBootsStatsAndLore(item);
        return item;
    }

    public void syncBootsStatsAndLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(GravenTheme.parse("<color:#F8F8A0><italic>Mythical Boots</italic></color>"));
        meta.itemName(GravenTheme.parse("<color:#F8F8A0>Mythical Boots</color>"));
        meta.setUnbreakable(false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        int maxDurability = GravenConfig.MYTHICAL_BOOTS_DURABILITY;
        int remainingDurability = maxDurability;
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(maxDurability);
            remainingDurability = Math.max(0, maxDurability - damageable.getDamage());
        }

        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE);
        meta.addAttributeModifier(Attribute.ARMOR,
                new AttributeModifier(new NamespacedKey(plugin, "mythical_boots_armor"),
                        3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(new NamespacedKey(plugin, "mythical_boots_toughness"),
                        3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                new AttributeModifier(new NamespacedKey(plugin, "mythical_boots_kbresist"),
                        0.1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));

        ItemTier tier = ItemTier.fromId(GravenConfig.MYTHICAL_BOOTS_TIER);

        List<Component> lore = new ArrayList<>();
        lore.add(TooltipBuilder.tierLine(tier));
        lore.add(TooltipBuilder.durabilityLine("<green>" + remainingDurability + "/" + maxDurability + "</green>"));
        List<Component> enchants = TooltipBuilder.enchantLines(meta);
        if (!enchants.isEmpty()) {
            lore.add(TooltipBuilder.section("Enchantments"));
            lore.addAll(enchants);
        }
        lore.add(TooltipBuilder.section("ATTRIBUTES"));
        lore.add(TooltipBuilder.attribute("🛡", "Defense", "#ADD8E6", "3.0"));
        lore.add(TooltipBuilder.attribute("💎", "Armor Tough", "#7DD3FC", "3.0"));
        lore.add(TooltipBuilder.attribute("⚓", "Knockback Res", "#93C5FD", "0.1"));
        if (GravenConfig.MYTHICAL_BOOTS_TIMESLOW_ENABLED) {
            lore.add(TooltipBuilder.section("ABILITY"));
            lore.add(GravenTheme.parse("<aqua>⏳ TIME SLOW</aqua><gray>:</gray> <white>Shift + Left Click</white>"));
            lore.add(GravenTheme.parse("<gray>Freezes everything for <yellow>"
                    + slowDuration(item) + "s</yellow> — only you keep moving</gray>"));
        }
        lore.add(TooltipBuilder.divider());

        TooltipBuilder.fitDividers(lore);
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Time Slow: shift + left-click freezes the world for the configured
     * duration. Tick freeze natively exempts players, so the wearer is truly
     * unaffected (plus a small speed boost); everything else stops.
     * The server loop keeps running while frozen, so Bukkit tasks still fire
     * in real time — delays are measured in normal 20tps ticks.
     */
    @EventHandler
    public void onTimeSlowTrigger(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !GravenConfig.MYTHICAL_BOOTS_TIMESLOW_ENABLED) return;
        if (!isWearingMythicalBoots(player)) return;
        if (cooldownManager.isOnCooldown(player, COOLDOWN_TYPE)) return;
        ItemStack wornBoots = player.getInventory().getBoots();

        double durationSec = Math.max(1.0, slowDuration(wornBoots));
        long freezeTicks = (long) (durationSec * 20.0);

        var tickManager = Bukkit.getServerTickManager();
        if (activeRestoreTask == null) {
            preSlowFrozen = tickManager.isFrozen();
        } else {
            activeRestoreTask.cancel();
        }
        tickManager.setFrozen(true);
        timeSlowExempt.add(player.getUniqueId());
        activeFreezePlayers = slowFreezePlayers(wornBoots);

        // Small speed boost for the wearer while the world is frozen.
        // Config uses 1-indexed levels (1 = Speed I); 0 disables the effect.
        int speedLevel = Math.max(0, slowSpeedBoost(wornBoots));
        if (speedLevel > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) freezeTicks, speedLevel - 1, false, false, true));
        }

        // Weaken everyone else for the freeze so their attacks don't matter
        // (config uses 1-indexed levels; 0 disables the effect)
        if (activeFreezePlayers) {
            int weaknessLevel = Math.max(0, slowWeaknessLevel(wornBoots));
            if (weaknessLevel > 0) {
                PotionEffect weakness = new PotionEffect(
                        PotionEffectType.WEAKNESS, (int) freezeTicks, weaknessLevel - 1, false, false, true);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!timeSlowExempt.contains(other.getUniqueId())) {
                        other.addPotionEffect(weakness);
                    }
                }
            }
        }

        player.getWorld().playSound(player.getLocation(),
                GravenConfig.MYTHICAL_BOOTS_TIMESLOW_SOUND,
                GravenConfig.MYTHICAL_BOOTS_TIMESLOW_VOLUME,
                GravenConfig.MYTHICAL_BOOTS_TIMESLOW_PITCH);
        spawnTimeSlowAura(player, freezeTicks);

        cooldownManager.setCooldown(player, COOLDOWN_TYPE, slowCooldown(wornBoots));

        // Unfreeze even if the wearer logs out mid-freeze
        activeRestoreTask = new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getServerTickManager().setFrozen(preSlowFrozen);
                activeRestoreTask = null;
                timeSlowExempt.clear();
            }
        }.runTaskLater(plugin, freezeTicks);

        if (action == Action.LEFT_CLICK_BLOCK) event.setCancelled(true);
    }

    /**
     * While the world is frozen, pin every non-exempt player fully in place —
     * no moving, no looking around.
     */
    @EventHandler
    public void onFrozenPlayerMove(PlayerMoveEvent event) {
        if (activeRestoreTask == null || !activeFreezePlayers) return;
        Player player = event.getPlayer();
        if (timeSlowExempt.contains(player.getUniqueId())) return;
        event.setTo(event.getFrom());
    }

    /**
     * Cancels an in-progress freeze and restores the pre-freeze state.
     * Called on plugin disable so a reload/stop mid-freeze can't leave the
     * world frozen (the restore task dies with the scheduler).
     */
    public static void cancelActiveSlow() {
        if (activeRestoreTask != null) {
            activeRestoreTask.cancel();
            activeRestoreTask = null;
            timeSlowExempt.clear();
            Bukkit.getServerTickManager().setFrozen(preSlowFrozen);
        }
    }

    /** Witch-particle ring that follows the wearer for the whole freeze. */
    private void spawnTimeSlowAura(Player player, long freezeTicks) {
        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= freezeTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                for (int i = 0; i < 20; i++) {
                    double angle = (Math.PI * 2 * i) / 20;
                    Location at = center.clone().add(Math.cos(angle) * 1.5, 0.1, Math.sin(angle) * 1.5);
                    player.getWorld().spawnParticle(Particle.WITCH, at, 2, 0.05, 0.3, 0.05, 0.0);
                }
                elapsed += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }
}
