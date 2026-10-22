package dev.senob.listeners;

import dev.senob.GravenCore;
import dev.senob.items.CelerityBoots;
import dev.senob.items.DraculaPlate;
import dev.senob.items.MythicalArmorSet;
import dev.senob.items.TooltipBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Keeps the Enchantments lore section live on the custom pieces that render
 * one (Mythical armor, Dracula's Plate, Celerity Boots — the Redline spear
 * shows vanilla enchantments, so it needs nothing).
 *
 * Those pieces bake their enchantments into the lore under their own
 * Enchantments header (with HIDE_ENCHANTS suppressing the vanilla block),
 * so when a piece is enchanted at a table, combined on an anvil, or stripped
 * on a grindstone, the tooltip would keep showing the old enchantments
 * until a /gravencore reload re-synced it. This listener refreshes the piece
 * the moment its enchantments change instead: it re-runs the piece's own
 * sync, but only when the section is actually stale, and it preserves a
 * player-given display name (anvil rename) across the re-sync.
 */
public class EnchantSyncListener implements Listener {

    private final GravenCore plugin;
    private final DraculaPlate draculaPlate;
    private final MythicalArmorSet mythicalArmorSet;
    private final CelerityBoots celerityBoots;
    private final NamespacedKey draculaKey;
    private final NamespacedKey mythicalKey;
    private final NamespacedKey bootsKey;

    public EnchantSyncListener(GravenCore plugin, DraculaPlate draculaPlate,
                               MythicalArmorSet mythicalArmorSet, CelerityBoots celerityBoots) {
        this.plugin = plugin;
        this.draculaPlate = draculaPlate;
        this.mythicalArmorSet = mythicalArmorSet;
        this.celerityBoots = celerityBoots;
        this.draculaKey = new NamespacedKey(plugin, "dracula_plate");
        this.mythicalKey = new NamespacedKey(plugin, "mythical_armor");
        this.bootsKey = new NamespacedKey(plugin, "mythical_boots");
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        // Vanilla applies the new enchantments after this event returns,
        // so the refresh has to wait a tick
        ItemStack item = event.getItem();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refresh(item), 1L);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        // The result already carries the merged enchantments; its lore
        // still shows the left input's, so refresh it before it's taken
        ItemStack result = event.getResult();
        if (result != null && refresh(result)) event.setResult(result);
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        // Same as the anvil: the result's enchantments were stripped,
        // its lore has to follow
        ItemStack result = event.getResult();
        if (result != null && refresh(result)) event.setResult(result);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Backstop for the paths with no dedicated event (/enchant command,
        // other plugins): a tick later, refresh whatever was clicked or held
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            refresh(current);
            refresh(cursor);
        }, 1L);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        refresh(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            refresh(event.getItem().getItemStack());
        }
    }

    /**
     * Re-syncs the item if it is one of the section-rendering custom pieces
     * and its Enchantments section is stale. Returns true when it synced.
     */
    private boolean refresh(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean dracula = pdc.has(draculaKey, PersistentDataType.INTEGER);
        boolean mythical = pdc.has(mythicalKey, PersistentDataType.STRING);
        boolean boots = pdc.has(bootsKey, PersistentDataType.INTEGER);
        if (!dracula && !mythical && !boots) return false;
        if (!TooltipBuilder.enchantSectionStale(meta)) return false;

        String pieceId = mythical ? pdc.get(mythicalKey, PersistentDataType.STRING) : null;
        // A re-sync resets the display name to the piece's canonical name;
        // keep whatever name the item currently shows (anvil renames)
        Component shownName = meta.displayName();

        if (dracula) draculaPlate.syncPlateStatsAndLore(item);
        else if (mythical) mythicalArmorSet.syncPieceStatsAndLore(item, pieceId);
        else celerityBoots.syncBootsStatsAndLore(item);

        if (shownName != null) {
            ItemMeta synced = item.getItemMeta();
            if (synced != null) {
                synced.displayName(shownName);
                item.setItemMeta(synced);
            }
        }
        return true;
    }
}
