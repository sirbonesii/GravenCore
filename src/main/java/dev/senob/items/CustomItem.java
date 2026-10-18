package dev.senob.items;

import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public interface CustomItem {

    // The manager will call this to ask "Is this your item?"
    boolean isItem(ItemStack item);

    // Triggered automatically when the player clicks with this item
    default void onInteract(PlayerInteractEvent event) {}

    // Triggered automatically if the player tries to throw/shoot the item
    default void onProjectileLaunch(ProjectileLaunchEvent event) {}
}