package dev.senob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class EnderChestEx implements Listener {

    private static NamespacedKey NO_EC_KEY;
    private static NamespacedKey DATA_KEY;

    public EnderChestEx(JavaPlugin plugin) {
        NO_EC_KEY = new NamespacedKey(plugin, "no_ender_chest");
        DATA_KEY = new NamespacedKey(plugin, "ender_chest_data");
    }

    @SuppressWarnings("unused")
    public static void restrictFromEnderChest(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(NO_EC_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    private boolean isRestricted(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(NO_EC_KEY, PersistentDataType.BYTE);
    }

    // ==========================================
    // 1. OPENING THE 54-SLOT CHEST
    // ==========================================

    @EventHandler
    public void onEnderChestOpen(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.ENDER_CHEST) {

            event.setCancelled(true);
            Player player = event.getPlayer();

            EnderChestHolder holder = new EnderChestHolder(player);
            Inventory customEnderChest = Bukkit.createInventory(holder, 54, Component.text("Ender Chest", NamedTextColor.DARK_GRAY));

            byte[] bytesData = player.getPersistentDataContainer().get(DATA_KEY, PersistentDataType.BYTE_ARRAY);
            if (bytesData != null) {
                fromBytes(bytesData, customEnderChest);
            }

            player.openInventory(customEnderChest);
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    // ==========================================
    // 2. SAVING THE DATA ON CLOSE
    // ==========================================

    @EventHandler
    public void onEnderChestClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EnderChestHolder(Player player)) {

            byte[] bytesData = toBytes(event.getInventory());
            player.getPersistentDataContainer().set(DATA_KEY, PersistentDataType.BYTE_ARRAY, bytesData);

            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
        }
    }

    // ==========================================
    // 3. BLOCKING RESTRICTED ITEMS
    // ==========================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder)) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && clicked.getType() == InventoryType.PLAYER) {
            if (isRestricted(event.getCurrentItem())) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(Component.text("You cannot store this item in your Ender Chest!", NamedTextColor.RED));
            }
            return;
        }

        if (clicked.getHolder() instanceof EnderChestHolder) {
            if (event.getAction() == InventoryAction.HOTBAR_SWAP) {
                if (event.getHotbarButton() >= 0) {
                    ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                    if (isRestricted(hotbarItem)) {
                        event.setCancelled(true);
                        event.getWhoClicked().sendMessage(Component.text("You cannot store this item in your Ender Chest!", NamedTextColor.RED));
                        return;
                    }
                }
            }

            if (isRestricted(event.getCursor())) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(Component.text("You cannot store this item in your Ender Chest!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder)) return;

        if (isRestricted(event.getOldCursor()) || isRestricted(event.getCursor())) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    event.getWhoClicked().sendMessage(Component.text("You cannot store this item in your Ender Chest!", NamedTextColor.RED));
                    return;
                }
            }
        }
    }

    // ==========================================
    // MODERN PAPER BYTE SERIALIZATION
    // ==========================================

    private byte[] toBytes(Inventory inventory) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            ItemStack[] contents = inventory.getContents();
            dos.writeInt(contents.length);

            for (ItemStack item : contents) {
                if (item == null || item.isEmpty()) {
                    dos.writeInt(0);
                } else {
                    byte[] itemBytes = item.serializeAsBytes();
                    dos.writeInt(itemBytes.length);
                    dos.write(itemBytes);
                }
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    private void fromBytes(byte[] data, Inventory inventory) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bis)) {

            int size = dis.readInt();
            for (int i = 0; i < size; i++) {
                int length = dis.readInt();
                if (length == 0) {
                    inventory.setItem(i, null);
                } else {
                    byte[] itemBytes = new byte[length];
                    dis.readFully(itemBytes);
                    inventory.setItem(i, ItemStack.deserializeBytes(itemBytes));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load item stacks.", e);
        }
    }

    // Modern Java Record for the holder
    private record EnderChestHolder(Player owner) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 54);
        }
    }
}