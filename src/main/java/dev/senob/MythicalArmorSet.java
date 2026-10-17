package dev.senob;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The default Mythical netherite armor set (prototype).
 * Four pieces sharing the Mythical tier tooltip chrome: tier line,
 * durability line, ATTRIBUTES (Defense / Armor Toughness / Knockback
 * Resistance), and the creator footer. Stats are real attribute
 * modifiers plus per-item PDC overrides, synced from config.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class MythicalArmorSet implements CustomItem {

    private final GravenCore plugin;

    private final NamespacedKey pieceKey;
    private final NamespacedKey defenseKey;
    private final NamespacedKey toughnessKey;
    private final NamespacedKey kbResistKey;

    /** The four set pieces. */
    public enum Piece {
        HELMET("helmet", "Mythical Helmet", Material.NETHERITE_HELMET, EquipmentSlotGroup.HEAD),
        CHESTPLATE("chestplate", "Mythical Chestplate", Material.NETHERITE_CHESTPLATE, EquipmentSlotGroup.CHEST),
        LEGGINGS("leggings", "Mythical Leggings", Material.NETHERITE_LEGGINGS, EquipmentSlotGroup.LEGS),
        BOOTS("boots", "Mythical Boots", Material.NETHERITE_BOOTS, EquipmentSlotGroup.FEET);

        private final String id;
        private final String displayName;
        private final Material material;
        private final EquipmentSlotGroup slotGroup;

        Piece(String id, String displayName, Material material, EquipmentSlotGroup slotGroup) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.slotGroup = slotGroup;
        }

        public String id() {
            return id;
        }

        public static Piece fromId(String id) {
            if (id != null) {
                for (Piece piece : values()) {
                    if (piece.id.equalsIgnoreCase(id)) return piece;
                }
            }
            return null;
        }
    }

    public MythicalArmorSet(GravenCore plugin) {
        this.plugin = plugin;
        this.pieceKey = new NamespacedKey(plugin, "mythical_armor");
        this.defenseKey = new NamespacedKey(plugin, "mythical_defense");
        this.toughnessKey = new NamespacedKey(plugin, "mythical_toughness");
        this.kbResistKey = new NamespacedKey(plugin, "mythical_kbresist");
        plugin.getLogger().info("Mythical Armor Set module initialized!");
    }

    @Override
    public boolean isItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(pieceKey, PersistentDataType.STRING);
    }

    public String getPieceId(ItemStack item) {
        if (!isItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(pieceKey, PersistentDataType.STRING);
    }

    public ItemStack createPiece(Piece piece) {
        ItemStack item = new ItemStack(piece.material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece.id);
            item.setItemMeta(meta);
        }
        syncPieceStatsAndLore(item, piece.id);
        return item;
    }

    public void syncPieceStatsAndLore(ItemStack item, String pieceId) {
        Piece piece = Piece.fromId(pieceId);
        if (piece == null || item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        meta.displayName(GravenTheme.parse("<color:#F8F8A0><italic>" + piece.displayName + "</italic></color>"));
        meta.itemName(GravenTheme.parse("<color:#F8F8A0>" + piece.displayName + "</color>"));
        meta.setUnbreakable(true);

        double[] defaults = GravenConfig.MYTHICAL_PIECES.getOrDefault(piece.id,
                new double[]{3.0, 3.0, 0.1});
        if (!pdc.has(defenseKey, PersistentDataType.DOUBLE)) pdc.set(defenseKey, PersistentDataType.DOUBLE, defaults[0]);
        if (!pdc.has(toughnessKey, PersistentDataType.DOUBLE)) pdc.set(toughnessKey, PersistentDataType.DOUBLE, defaults[1]);
        if (!pdc.has(kbResistKey, PersistentDataType.DOUBLE)) pdc.set(kbResistKey, PersistentDataType.DOUBLE, defaults[2]);

        double defense = pdc.getOrDefault(defenseKey, PersistentDataType.DOUBLE, defaults[0]);
        double toughness = pdc.getOrDefault(toughnessKey, PersistentDataType.DOUBLE, defaults[1]);
        double kbResist = pdc.getOrDefault(kbResistKey, PersistentDataType.DOUBLE, defaults[2]);

        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE);
        meta.addAttributeModifier(Attribute.ARMOR,
                new AttributeModifier(new NamespacedKey(plugin, "mythical_armor_" + piece.id),
                        defense, AttributeModifier.Operation.ADD_NUMBER, piece.slotGroup));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(new NamespacedKey(plugin, "mythical_toughness_" + piece.id),
                        toughness, AttributeModifier.Operation.ADD_NUMBER, piece.slotGroup));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                new AttributeModifier(new NamespacedKey(plugin, "mythical_kbresist_" + piece.id),
                        kbResist, AttributeModifier.Operation.ADD_NUMBER, piece.slotGroup));

        ItemTier tier = ItemTier.fromId(GravenConfig.MYTHICAL_TIER);

        List<Component> lore = new ArrayList<>();
        lore.add(TooltipBuilder.tierLine(tier));
        lore.add(TooltipBuilder.durabilityLine("<blue>Unbreakable</blue>"));
        lore.add(TooltipBuilder.divider());
        lore.add(TooltipBuilder.section("ATTRIBUTES"));
        lore.add(TooltipBuilder.attribute("🛡", "Defense", "#ADD8E6", formatStat(defense)));
        lore.add(TooltipBuilder.attribute("💎", "Armor Toughness", "#7DD3FC", formatStat(toughness)));
        lore.add(TooltipBuilder.attribute("⚓", "Knockback Resistance", "#93C5FD", formatStat(kbResist)));
        lore.add(TooltipBuilder.divider());

        meta.lore(lore);
        item.setItemMeta(meta);

        AttributeHider.hideAllFlags(item);
    }

    private static String formatStat(double value) {
        return String.format("%.1f", value);
    }
}
