package dev.senob.listeners;

import dev.senob.items.TieredArmorSet;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Crafting a vanilla armor piece of a tiered material produces the
 * tiered set's piece instead: diamond -> Legendary, iron -> Epic,
 * chainmail -> Rare, gold -> Uncommon, leather -> Common. (Chainmail
 * has no vanilla crafting recipe, so the Rare entries only matter if
 * a recipe is ever added for it.)
 *
 * Whatever the crafted result already carries transfers to the
 * converted piece: its enchantments (which the tiered tooltip then
 * renders in its Enchantments section) and its damage (e.g. when two
 * worn pieces are grid-repaired into one), clamped to the new
 * piece's max durability.
 */
public class ArmorCraftListener implements Listener {

    private record Mapping(String tierId, String pieceId) {
    }

    private static final Map<Material, Mapping> MAPPINGS = Map.ofEntries(
            Map.entry(Material.DIAMOND_HELMET, new Mapping("legendary", "helmet")),
            Map.entry(Material.DIAMOND_CHESTPLATE, new Mapping("legendary", "chestplate")),
            Map.entry(Material.DIAMOND_LEGGINGS, new Mapping("legendary", "leggings")),
            Map.entry(Material.DIAMOND_BOOTS, new Mapping("legendary", "boots")),
            Map.entry(Material.IRON_HELMET, new Mapping("epic", "helmet")),
            Map.entry(Material.IRON_CHESTPLATE, new Mapping("epic", "chestplate")),
            Map.entry(Material.IRON_LEGGINGS, new Mapping("epic", "leggings")),
            Map.entry(Material.IRON_BOOTS, new Mapping("epic", "boots")),
            Map.entry(Material.CHAINMAIL_HELMET, new Mapping("rare", "helmet")),
            Map.entry(Material.CHAINMAIL_CHESTPLATE, new Mapping("rare", "chestplate")),
            Map.entry(Material.CHAINMAIL_LEGGINGS, new Mapping("rare", "leggings")),
            Map.entry(Material.CHAINMAIL_BOOTS, new Mapping("rare", "boots")),
            Map.entry(Material.GOLDEN_HELMET, new Mapping("uncommon", "helmet")),
            Map.entry(Material.GOLDEN_CHESTPLATE, new Mapping("uncommon", "chestplate")),
            Map.entry(Material.GOLDEN_LEGGINGS, new Mapping("uncommon", "leggings")),
            Map.entry(Material.GOLDEN_BOOTS, new Mapping("uncommon", "boots")),
            Map.entry(Material.LEATHER_HELMET, new Mapping("common", "helmet")),
            Map.entry(Material.LEATHER_CHESTPLATE, new Mapping("common", "chestplate")),
            Map.entry(Material.LEATHER_LEGGINGS, new Mapping("common", "leggings")),
            Map.entry(Material.LEATHER_BOOTS, new Mapping("common", "boots")));

    private final TieredArmorSet tieredArmorSet;

    public ArmorCraftListener(TieredArmorSet tieredArmorSet) {
        this.tieredArmorSet = tieredArmorSet;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (result == null) return;
        Mapping mapping = MAPPINGS.get(result.getType());
        if (mapping == null) return;
        if (tieredArmorSet.isItem(result)) return; // already a tiered piece

        ItemStack converted = tieredArmorSet.createPiece(mapping.tierId(), mapping.pieceId());
        if (converted == null) return;

        ItemMeta sourceMeta = result.getItemMeta();
        boolean changed = false;

        // Enchantments on the crafted result transfer to the new piece
        if (sourceMeta != null && sourceMeta.hasEnchants()) {
            ItemMeta convertedMeta = converted.getItemMeta();
            if (convertedMeta != null) {
                sourceMeta.getEnchants().forEach((enchantment, level) ->
                        convertedMeta.addEnchant(enchantment, level, true));
                converted.setItemMeta(convertedMeta);
                changed = true;
            }
        }

        // Damage on the result (grid repair) carries over, clamped to
        // the converted piece's own max durability
        if (sourceMeta instanceof Damageable sourceDamage && sourceDamage.getDamage() > 0) {
            ItemMeta convertedMeta = converted.getItemMeta();
            if (convertedMeta instanceof Damageable targetDamage && targetDamage.hasMaxDamage()) {
                targetDamage.setDamage(Math.min(sourceDamage.getDamage(), targetDamage.getMaxDamage() - 1));
                converted.setItemMeta(convertedMeta);
                changed = true;
            }
        }

        // Re-sync so the tooltip reflects the transferred enchantments
        // and the real remaining durability
        if (changed) tieredArmorSet.syncPieceStatsAndLore(converted);

        inventory.setResult(converted);
    }
}
