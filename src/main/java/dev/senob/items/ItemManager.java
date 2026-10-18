package dev.senob.items;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemManager implements Listener {

    private final List<CustomItem> registeredItems = new ArrayList<>();

    public void registerItem(CustomItem item) {
        registeredItems.add(item);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        for (CustomItem customItem : registeredItems) {
            if (customItem.isItem(item)) {
                customItem.onInteract(event);
                return;
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return;

        for (CustomItem customItem : registeredItems) {
            if (customItem.isItem(item)) {
                customItem.onProjectileLaunch(event);
                return;
            }
        }
    }
}