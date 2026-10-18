package dev.senob.listeners;

import dev.senob.items.TooltipBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the "Durability: &lt;actual&gt;/&lt;max&gt;" lore line live on custom
 * items as they take durability damage.
 *
 * Item sets register the PersistentDataContainer key that marks their items
 * (see {@link #track(NamespacedKey)}). The max durability is read from the
 * item's own Damageable meta, so future armor sets need no changes here —
 * they just track their PDC id key at startup.
 */
public class DurabilityLoreListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<NamespacedKey> trackedKeys = new HashSet<>();

    public DurabilityLoreListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a set's PDC id key for live durability lore updates.
     */
    public void track(NamespacedKey idKey) {
        trackedKeys.add(idKey);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        if (!isTracked(meta.getPersistentDataContainer())) return;

        // The event fires before damage is applied, so account for it now.
        int max = damageable.getMaxDamage();
        int remaining = Math.max(0, max - damageable.getDamage() - event.getDamage());

        List<Component> lore = meta.lore();
        if (lore == null) return;
        for (int i = 0; i < lore.size(); i++) {
            if (TooltipBuilder.isDurabilityLine(lore.get(i))) {
                lore.set(i, TooltipBuilder.durabilityLine("<green>" + remaining + "/" + max + "</green>"));
                meta.lore(lore);
                item.setItemMeta(meta);
                return;
            }
        }
    }

    private boolean isTracked(PersistentDataContainer pdc) {
        for (NamespacedKey key : trackedKeys) {
            if (pdc.has(key, PersistentDataType.STRING)) return true;
        }
        return false;
    }
}
