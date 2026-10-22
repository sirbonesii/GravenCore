package dev.senob.items;

import dev.senob.GravenCore;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Alter of Despair: a named dropper given out by
 * {@code /gravencore alter <username> [count]}, rendered as the
 * configured block (alter.block, default crafting table) via the
 * item_model component (the placed block stays a
 * dropper). Placing it spawns the
 * glinting ItemDisplay of that block inside the dropper's own block —
 * the same setup as summoning an item_display with the
 * enchantment_glint_override component, a full transformation and a
 * forced brightness — and breaking the dropper removes the display and
 * drops a fresh alter item, so the marker survives the place/break cycle.
 */
public class AlterOfDespair implements Listener {

    private final GravenCore plugin;
    private final NamespacedKey alterItemKey;
    private final NamespacedKey displayBlockKey;

    public AlterOfDespair(GravenCore plugin) {
        this.plugin = plugin;
        this.alterItemKey = new NamespacedKey(plugin, "alter_item");
        this.displayBlockKey = new NamespacedKey(plugin, "alter_display_block");
    }

    public ItemStack createAlterItem(int count) {
        ItemStack item = new ItemStack(Material.DROPPER, Math.max(1, Math.min(64, count)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(GravenTheme.parse("<color:#F8F854>Alter of Despair</color>"));
            meta.itemName(GravenTheme.parse("<color:#F8F854>Alter of Despair</color>"));
            // Render the item as the configured block (alter.block); the
            // base item stays a dropper so it still places the altar block
            meta.setItemModel(GravenConfig.alterItemModelKey());
            meta.getPersistentDataContainer().set(alterItemKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAlterItem(ItemStack item) {
        if (item == null || item.getType() != Material.DROPPER || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(alterItemKey, PersistentDataType.INTEGER);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isAlterItem(event.getItemInHand())) return;
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.DROPPER) return;
        // One tick later, so the block (and its tile state) is fully placed
        Bukkit.getScheduler().runTask(plugin, () -> spawnDisplay(block));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.DROPPER) return;
        ItemDisplay display = findDisplay(block);
        if (display == null) return; // not an alter dropper
        display.remove();
        // The placed item's PDC marker doesn't survive into the block, so
        // swap the plain dropper drop for a fresh alter item.
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createAlterItem(1));
    }

    private void spawnDisplay(Block block) {
        if (block.getType() != Material.DROPPER) return;
        ItemDisplay existing = findDisplay(block);
        if (existing != null) existing.remove(); // never stack two
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawn(center, ItemDisplay.class, display -> {
            ItemStack table = new ItemStack(GravenConfig.alterBlockMaterial());
            ItemMeta tableMeta = table.getItemMeta();
            if (tableMeta != null) {
                if (GravenConfig.ALTER_GLINT) tableMeta.setEnchantmentGlintOverride(true);
                table.setItemMeta(tableMeta);
            }
            display.setItemStack(table);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            float scale = (float) GravenConfig.ALTER_DISPLAY_SCALE;
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            int brightnessBlock = Math.max(0, Math.min(15, GravenConfig.ALTER_BRIGHTNESS_BLOCK));
            int brightnessSky = Math.max(0, Math.min(15, GravenConfig.ALTER_BRIGHTNESS_SKY));
            display.setBrightness(new Display.Brightness(brightnessBlock, brightnessSky));
            display.setPersistent(true);
            display.getPersistentDataContainer().set(displayBlockKey, PersistentDataType.STRING,
                    block.getX() + "," + block.getY() + "," + block.getZ());
        });
    }

    private ItemDisplay findDisplay(Block block) {
        String wanted = block.getX() + "," + block.getY() + "," + block.getZ();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (entity instanceof ItemDisplay display) {
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                if (wanted.equals(pdc.get(displayBlockKey, PersistentDataType.STRING))) return display;
            }
        }
        return null;
    }
}
