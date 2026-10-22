package dev.senob.commands;

import dev.senob.GravenCore;
import dev.senob.core.GravenTheme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /invsee &lt;player&gt; — a live, EDITABLE view of another player's
 * inventory (permission: gravencore.admin; anyone with the permission
 * can take items out and put items in). Layout per Aaron's diagram
 * (2026-10-22), 54 slots:
 *
 * <pre>
 * [pane][Helmet][Chest][Legs][Boots][pane][Main][Off][pane]
 * [pane] x9  (full divider row)
 * 27 main inventory slots
 * 9 hotbar slots
 * </pre>
 *
 * The special slots show a labeled colored pane when empty (purple
 * armor, lime main hand, orange off hand; the panes carry a PDC
 * marker so they are never written into the real inventory).
 * Dividers are gray panes and reject all interaction. A reconcile
 * pass runs twice a second per open view: slots the admin changed are
 * pushed into the target's real inventory, slots the target changed
 * are pulled into the view. Shift-clicking from the admin's own
 * inventory into the view is blocked so items never land in random
 * slots; taking items out by shift-click is allowed.
 */
public class InvSeeCommand implements CommandExecutor, TabCompleter, Listener {

    /** Marker holder so the GUIManager leaves this inventory alone. */
    private static final class InvSeeHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final class View {
        final UUID targetId;
        final Inventory inventory;
        /** GUI content per slot as of the last reconcile (null = empty). */
        final ItemStack[] synced = new ItemStack[54];

        View(UUID targetId, Inventory inventory) {
            this.targetId = targetId;
            this.inventory = inventory;
        }
    }

    private final GravenCore plugin;
    private final NamespacedKey placeholderKey;
    private final Map<UUID, View> openViews = new HashMap<>();

    public InvSeeCommand(GravenCore plugin) {
        this.plugin = plugin;
        this.placeholderKey = new NamespacedKey(plugin, "invsee_placeholder");
        // Bidirectional reconcile for every open view
        new BukkitRunnable() {
            @Override
            public void run() {
                reconcileAll();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("gravencore.admin")) {
            sender.sendMessage(GravenTheme.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(GravenTheme.parse("<red>Only players can use this command.</red>"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(GravenTheme.parse("<red>Usage: /invsee <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(GravenTheme.parse("<red>Player '" + args[0] + "' is not online.</red>"));
            return true;
        }

        InvSeeHolder holder = new InvSeeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, GravenTheme.parse("InvSee (" + target.getName() + ")"));
        holder.inventory = inventory;

        View view = new View(target.getUniqueId(), inventory);
        // Initial fill: dividers, then pull everything from the target
        for (int slot = 0; slot < 54; slot++) {
            if (isDivider(slot)) inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, null));
        }
        pullAll(view, target);
        viewer.openInventory(inventory);
        // Registered after open(): switching views fires the old view's
        // close event first, which must not remove this new view
        openViews.put(viewer.getUniqueId(), view);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        return List.of();
    }

    // --- Interaction guards ---

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvSeeHolder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        Inventory top = event.getView().getTopInventory();

        if (event.getClickedInventory() == top) {
            int slot = event.getSlot();
            if (isDivider(slot)) {
                event.setCancelled(true);
                return;
            }
            if (isPlaceholder(event.getCurrentItem())) {
                event.setCancelled(true);
                // Placing onto an empty special slot by hand: swap the
                // cursor stack in for the placeholder pane
                ItemStack cursor = event.getCursor();
                if (!isEmpty(cursor) && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                    top.setItem(slot, cursor.clone());
                    viewer.setItemOnCursor(null);
                    syncSoon(viewer);
                }
                return;
            }
            // A real item slot: vanilla behavior, then push the result
            syncSoon(viewer);
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Shift-clicking from the admin's own inventory would scatter
            // items into the first free GUI slots — blocked; dragging or
            // clicking items in by hand still works
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvSeeHolder)) return;
        Inventory top = event.getView().getTopInventory();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                // Raw slots 0-53 are the top inventory's own slots here
                if (isDivider(rawSlot) || isPlaceholder(top.getItem(rawSlot))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (event.getWhoClicked() instanceof Player viewer) syncSoon(viewer);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        View view = openViews.get(event.getPlayer().getUniqueId());
        if (view != null && event.getInventory() == view.inventory) {
            // Final push so no edit is lost
            if (event.getPlayer() instanceof Player && Bukkit.getPlayer(view.targetId) instanceof Player target) {
                pushEdits(view, target);
            }
            openViews.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openViews.remove(event.getPlayer().getUniqueId());
    }

    // --- Reconcile engine ---

    private void syncSoon(Player viewer) {
        new BukkitRunnable() {
            @Override
            public void run() {
                View view = openViews.get(viewer.getUniqueId());
                if (view == null) return;
                Player target = Bukkit.getPlayer(view.targetId);
                if (target != null) pushEdits(view, target);
            }
        }.runTask(plugin);
    }

    private void reconcileAll() {
        if (openViews.isEmpty()) return;
        for (Map.Entry<UUID, View> entry : new ArrayList<>(openViews.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue().targetId);
            if (viewer == null) {
                openViews.remove(entry.getKey());
                continue;
            }
            if (target == null) {
                // Target logged out — nothing left to watch or edit
                openViews.remove(entry.getKey());
                viewer.closeInventory();
                continue;
            }
            if (viewer.getOpenInventory().getTopInventory() != entry.getValue().inventory) {
                openViews.remove(entry.getKey());
                continue;
            }
            pushEdits(entry.getValue(), target);
            pullChanges(entry.getValue(), target);
        }
    }

    /** Writes slots the admin changed (GUI != last synced) into the target. */
    private void pushEdits(View view, Player target) {
        PlayerInventory targetInv = target.getInventory();
        for (int slot : mappedSlots()) {
            ItemStack guiItem = view.inventory.getItem(slot);
            if (sameItem(guiItem, view.synced[slot])) continue;
            // Placeholders stand for "empty" in the real inventory
            setTargetSlot(targetInv, slot, isPlaceholder(guiItem) ? null : cloneOrNull(guiItem));
            view.synced[slot] = cloneOrNull(guiItem);
        }
    }

    /** Pulls slots the target changed into the GUI; the admin's edits win ties. */
    private void pullChanges(View view, Player target) {
        PlayerInventory targetInv = target.getInventory();
        for (int slot : mappedSlots()) {
            ItemStack guiItem = view.inventory.getItem(slot);
            if (!sameItem(guiItem, view.synced[slot])) continue; // admin edit pending push
            ItemStack targetItem = getTargetSlot(targetInv, slot);
            ItemStack guiWanted = isEmpty(targetItem) ? placeholderFor(slot) : targetItem.clone();
            if (sameItem(guiItem, guiWanted)) continue;
            view.inventory.setItem(slot, guiWanted);
            view.synced[slot] = cloneOrNull(guiWanted);
        }
    }

    private void pullAll(View view, Player target) {
        PlayerInventory targetInv = target.getInventory();
        for (int slot : mappedSlots()) {
            ItemStack targetItem = getTargetSlot(targetInv, slot);
            ItemStack guiItem = isEmpty(targetItem) ? placeholderFor(slot) : targetItem.clone();
            view.inventory.setItem(slot, guiItem);
            view.synced[slot] = cloneOrNull(guiItem);
        }
    }

    // --- Slot mapping (GUI slot -> target inventory) ---

    private static List<Integer> mappedSlots() {
        List<Integer> slots = new ArrayList<>();
        slots.add(1); slots.add(2); slots.add(3); slots.add(4); // armor
        slots.add(6); slots.add(7);                            // hands
        for (int slot = 18; slot <= 53; slot++) slots.add(slot); // storage + hotbar
        return slots;
    }

    private static boolean isDivider(int slot) {
        return slot == 0 || slot == 5 || slot == 8 || (slot >= 9 && slot <= 17);
    }

    private ItemStack placeholderFor(int guiSlot) {
        return switch (guiSlot) {
            case 1 -> pane(Material.PURPLE_STAINED_GLASS_PANE, "Helmet");
            case 2 -> pane(Material.PURPLE_STAINED_GLASS_PANE, "Chestplate");
            case 3 -> pane(Material.PURPLE_STAINED_GLASS_PANE, "Leggings");
            case 4 -> pane(Material.PURPLE_STAINED_GLASS_PANE, "Boots");
            case 6 -> pane(Material.LIME_STAINED_GLASS_PANE, "Main Hand");
            case 7 -> pane(Material.ORANGE_STAINED_GLASS_PANE, "Off Hand");
            default -> null;
        };
    }

    private static ItemStack getTargetSlot(PlayerInventory inv, int guiSlot) {
        return switch (guiSlot) {
            case 1 -> inv.getHelmet();
            case 2 -> inv.getChestplate();
            case 3 -> inv.getLeggings();
            case 4 -> inv.getBoots();
            case 6 -> inv.getItemInMainHand();
            case 7 -> inv.getItemInOffHand();
            default -> inv.getStorageContents()[guiSlot <= 44 ? guiSlot - 18 + 9 : guiSlot - 45];
        };
    }

    private static void setTargetSlot(PlayerInventory inv, int guiSlot, ItemStack item) {
        switch (guiSlot) {
            case 1 -> inv.setHelmet(item);
            case 2 -> inv.setChestplate(item);
            case 3 -> inv.setLeggings(item);
            case 4 -> inv.setBoots(item);
            case 6 -> inv.setItemInMainHand(item);
            case 7 -> inv.setItemInOffHand(item);
            default -> inv.setItem(guiSlot <= 44 ? guiSlot - 18 + 9 : guiSlot - 45, item);
        }
    }

    // --- Item helpers ---

    private boolean isPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(placeholderKey, PersistentDataType.STRING);
    }

    private ItemStack pane(Material material, String label) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(label == null ? Component.text(" ") : GravenTheme.parse("<gray>" + label + "</gray>"));
            if (label != null) {
                meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.STRING, label);
            }
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    /** Content equality for sync purposes (air and null both mean empty). */
    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (isEmpty(a) && isEmpty(b)) return true;
        if (isEmpty(a) || isEmpty(b)) return false;
        return a.getAmount() == b.getAmount() && a.isSimilar(b);
    }
}
