package dev.senob.listeners;

import dev.senob.core.GravenConfig;
import dev.senob.items.TieredArmorSet;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.Map;

/**
 * Smithing a Legendary (diamond) tiered piece into netherite produces
 * the Mythical set's piece instead. The vanilla smithing result only
 * swaps the material and keeps the old tier's lore and stats — a
 * netherite helmet still showing the Legendary tooltip at 1024
 * durability — so the result is rebuilt as a proper Mythical piece.
 *
 * The conversion is gated by
 * {@code upgrading.redline-altar.disable-smithing-mythical} (default
 * true: Mythical stays altar-only). While the toggle is on this
 * listener does nothing and smithing gives the vanilla result.
 *
 * Whatever the smithing result already carries transfers to the
 * converted piece: its enchantments (vanilla smithing copies them
 * from the base) and its damage, clamped to the new piece's max
 * durability. A custom name on the result survives the re-sync.
 *
 * Only pieces already in the tier system convert: vanilla (non-tiered)
 * diamond armor smithed to netherite stays vanilla. Other smithing
 * uses (e.g. armor trims, which keep the same material) pass through
 * untouched.
 */
public class ArmorSmithingListener implements Listener {

    private static final Map<Material, String> NETHERITE_PIECES = Map.of(
            Material.NETHERITE_HELMET, "helmet",
            Material.NETHERITE_CHESTPLATE, "chestplate",
            Material.NETHERITE_LEGGINGS, "leggings",
            Material.NETHERITE_BOOTS, "boots");

    private final TieredArmorSet tieredArmorSet;

    public ArmorSmithingListener(TieredArmorSet tieredArmorSet) {
        this.tieredArmorSet = tieredArmorSet;
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (GravenConfig.SMITHING_MYTHICAL_DISABLED) return;
        SmithingInventory inventory = event.getInventory();
        ItemStack base = inventory.getInputEquipment();
        ItemStack result = event.getResult();
        if (base == null || result == null) return;
        if (!"legendary".equals(tieredArmorSet.getTierId(base))) return;
        String pieceId = NETHERITE_PIECES.get(result.getType());
        if (pieceId == null) return;
        if (!pieceId.equals(tieredArmorSet.getPieceId(base))) return; // same slot

        // Identity from the base (its PDC is guaranteed); enchantments,
        // damage and name from the vanilla result, which already merged
        // them from the base. The shared helper is also used by the
        // altar ritual, so both upgrade paths behave the same
        ItemStack converted = tieredArmorSet.upgradeLegendaryToMythical(base, result);
        if (converted == null) return;

        event.setResult(converted);
    }
}
