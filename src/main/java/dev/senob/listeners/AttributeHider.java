package dev.senob.listeners;

import com.google.common.collect.Multimap;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class AttributeHider implements Listener {

    public AttributeHider() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(AttributeHider.class);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    hideAllFlags(item);
                }
            }
        }, 10L, 10L);
    }

    /**
     * Preserves weapon damage/speed, then hides ALL unwanted tooltip categories.
     */
    public static void hideAllFlags(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // If it already has the flags we care about, skip this step.
        if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES) && meta.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE) && meta.hasAttributeModifiers()) {
            return;
        }

        // 1. Preserve the item's actual damage, attack speed, and armor values
        if (!meta.hasAttributeModifiers()) {
            boolean copiedAny = false;

            // Copy modern Paper DataComponents (for Weapons, Tools, etc.)
            ItemAttributeModifiers componentAttrs = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (componentAttrs != null && !componentAttrs.modifiers().isEmpty()) {
                for (ItemAttributeModifiers.Entry entry : componentAttrs.modifiers()) {
                    meta.addAttributeModifier(entry.attribute(), entry.modifier());
                    copiedAny = true;
                }
            }

            // Fallback for Legacy Bukkit items
            if (!copiedAny) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    Multimap<Attribute, AttributeModifier> defaults = item.getType().getDefaultAttributeModifiers(slot);
                    if (!defaults.isEmpty()) {
                        defaults.forEach(meta::addAttributeModifier);
                    }
                }
            }
        }

        // 2. Hide ALL extra attribute lines globally
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,     // Hides "When in Main Hand / On Head"
                //ItemFlag.HIDE_UNBREAKABLE,    // Hides "Unbreakable"
                ItemFlag.HIDE_ARMOR_TRIM,     // Hides Trim patterns
                ItemFlag.HIDE_DYE,            // Hides "Dyed" text
                ItemFlag.HIDE_DESTROYS,       // Hides "Can destroy X"
                ItemFlag.HIDE_PLACED_ON      // Hides "Can be placed on X"
                //ItemFlag.HIDE_POTION_EFFECTS  // Hides potion duration text
        );

        // meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    // ==========================================
    // EVENTS GRAB ITEMS AUTOMATICALLY
    // ==========================================

    @EventHandler
    public void onItemSpawn(@NotNull ItemSpawnEvent event) {
        // Cleans items the millisecond they drop onto the ground
        ItemStack item = event.getEntity().getItemStack();
        hideAllFlags(item);
        event.getEntity().setItemStack(item);
    }

    @EventHandler
    public void onItemPickup(@NotNull EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            hideAllFlags(event.getItem().getItemStack());
        }
    }

    @EventHandler
    public void onPlayerDrop(@NotNull PlayerDropItemEvent event) {
        hideAllFlags(event.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            hideAllFlags(item);
        }
    }

    @EventHandler
    public void onItemHeld(@NotNull PlayerItemHeldEvent event) {
        hideAllFlags(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler
    public void onSwapHand(@NotNull PlayerSwapHandItemsEvent event) {
        hideAllFlags(event.getMainHandItem());
        hideAllFlags(event.getOffHandItem());
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        hideAllFlags(event.getCurrentItem());
        hideAllFlags(event.getCursor());
    }

    @EventHandler
    public void onPrepareCraft(@NotNull PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null) {
            hideAllFlags(result);
            event.getInventory().setResult(result);
        }
    }

    @EventHandler
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result != null) {
            hideAllFlags(result);
            event.setResult(result);
        }
    }

    @EventHandler
    public void onEnchantItem(@NotNull EnchantItemEvent event) {
        hideAllFlags(event.getItem());
    }
}