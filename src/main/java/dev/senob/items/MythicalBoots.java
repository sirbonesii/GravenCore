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

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone Mythical boots (not part of the Mythical armor set), in the same
 * style as Dracula's Plate: its own item, config section and ability.
 *
 * Ability — Time Slow: shift + left-click drops the server tick rate for a few
 * seconds. The tick rate is server-global, so the wearer is compensated with
 * Speed/Haste to feel unaffected while the world crawls.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class MythicalBoots implements Listener, CustomItem {

    private final GravenCore plugin;
    private final CooldownManager cooldownManager;

    public static final CooldownManager.CooldownType COOLDOWN_TYPE =
            new CooldownManager.CooldownType("mythical-boots-timeslow", "⏳", TextColor.color(0x5EEAD4));

    private final NamespacedKey bootsKey;

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
            lore.add(GravenTheme.parse("<gray>Slows the world to <aqua>"
                    + GravenConfig.MYTHICAL_BOOTS_TIMESLOW_TICKRATE + " tps</aqua> for <yellow>"
                    + GravenConfig.MYTHICAL_BOOTS_TIMESLOW_DURATION + "s</yellow> — you feel nothing</gray>"));
        }
        lore.add(TooltipBuilder.divider());

        TooltipBuilder.fitDividers(lore);
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Time Slow: shift + left-click drops the server tick rate for the
     * configured duration. Game ticks run slower than real time while active,
     * so all delays are measured in slowed ticks (duration * tick-rate).
     */
    @EventHandler
    public void onTimeSlowTrigger(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !GravenConfig.MYTHICAL_BOOTS_TIMESLOW_ENABLED) return;
        if (!isWearingMythicalBoots(player)) return;
        if (cooldownManager.isOnCooldown(player, COOLDOWN_TYPE)) return;

        double tickRate = Math.clamp(GravenConfig.MYTHICAL_BOOTS_TIMESLOW_TICKRATE, 1.0, 20.0);
        double durationSec = Math.max(1.0, GravenConfig.MYTHICAL_BOOTS_TIMESLOW_DURATION);
        // Slowed ticks covering the real-time duration: duration * tick-rate
        long slowTicks = Math.max(1L, (long) (durationSec * tickRate));

        var tickManager = Bukkit.getServerTickManager();
        float previousRate = tickManager.getTickRate();
        tickManager.setTickRate((float) tickRate);

        // Compensate the wearer so they feel unaffected while the world crawls
        int speedAmp = Math.max(0, GravenConfig.MYTHICAL_BOOTS_TIMESLOW_SPEEDAMP);
        int hasteAmp = Math.max(0, GravenConfig.MYTHICAL_BOOTS_TIMESLOW_HASTEAMP);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) slowTicks, speedAmp, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (int) slowTicks, hasteAmp, false, false, true));

        player.getWorld().playSound(player.getLocation(),
                GravenConfig.MYTHICAL_BOOTS_TIMESLOW_SOUND,
                GravenConfig.MYTHICAL_BOOTS_TIMESLOW_VOLUME,
                GravenConfig.MYTHICAL_BOOTS_TIMESLOW_PITCH);
        spawnTimeSlowAura(player, slowTicks);

        cooldownManager.setCooldown(player, COOLDOWN_TYPE, GravenConfig.MYTHICAL_BOOTS_TIMESLOW_COOLDOWN);

        // Restore the previous tick rate even if the wearer logs out mid-slow
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getServerTickManager().setTickRate(previousRate);
            }
        }.runTaskLater(plugin, slowTicks);

        if (action == Action.LEFT_CLICK_BLOCK) event.setCancelled(true);
    }

    /** Witch-particle ring that follows the wearer for the whole slow. */
    private void spawnTimeSlowAura(Player player, long slowTicks) {
        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= slowTicks || !player.isOnline()) {
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
