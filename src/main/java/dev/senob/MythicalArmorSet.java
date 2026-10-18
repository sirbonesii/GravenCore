package dev.senob;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The default Mythical netherite armor set (prototype).
 * Four pieces sharing the Mythical tier tooltip chrome: tier line,
 * durability line and ATTRIBUTES (Defense / ArT / KbR). Stats are real
 * attribute modifiers plus per-item PDC overrides, synced from config.
 * Also owns the set's block system: while wearing a piece, incoming attacks
 * have a block-rate% chance to be reduced by block-power (flat), with a
 * shield-clang sound and impact particles on trigger.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class MythicalArmorSet implements CustomItem, Listener {

    private final GravenCore plugin;

    private final NamespacedKey pieceKey;
    private final NamespacedKey defenseKey;
    private final NamespacedKey toughnessKey;
    private final NamespacedKey kbResistKey;
    private final NamespacedKey blockRateKey;
    private final NamespacedKey blockPowerKey;

    /** The four set pieces. */
    public enum Piece {
        HELMET("helmet", Material.NETHERITE_HELMET, EquipmentSlotGroup.HEAD),
        CHESTPLATE("chestplate", Material.NETHERITE_CHESTPLATE, EquipmentSlotGroup.CHEST),
        LEGGINGS("leggings", Material.NETHERITE_LEGGINGS, EquipmentSlotGroup.LEGS),
        BOOTS("boots", Material.NETHERITE_BOOTS, EquipmentSlotGroup.FEET);

        private final String id;
        private final Material material;
        private final EquipmentSlotGroup slotGroup;

        Piece(String id, Material material, EquipmentSlotGroup slotGroup) {
            this.id = id;
            this.material = material;
            this.slotGroup = slotGroup;
        }

        public String id() {
            return id;
        }

        /** "NETHERITE_CHESTPLATE" -> "Netherite Chestplate". */
        public String vanillaName() {
            String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return sb.toString();
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
        this.blockRateKey = new NamespacedKey(plugin, "mythical_blockrate");
        this.blockPowerKey = new NamespacedKey(plugin, "mythical_blockpower");
        plugin.getLogger().info("Mythical Armor Set module initialized!");
    }

    /**
     * The PDC key marking set pieces. Registered with DurabilityLoreListener
     * so the durability lore line stays live as pieces take damage.
     */
    public NamespacedKey idKey() {
        return pieceKey;
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

        String vanillaName = piece.vanillaName();
        meta.displayName(GravenTheme.parse("<gray>" + vanillaName + "</gray>"));
        meta.itemName(GravenTheme.parse("<gray>" + vanillaName + "</gray>"));
        meta.setUnbreakable(false);

        int maxDurability = GravenConfig.MYTHICAL_DURABILITY;
        int remainingDurability = maxDurability;
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(maxDurability);
            remainingDurability = Math.max(0, maxDurability - damageable.getDamage());
        }

        double[] defaults = GravenConfig.MYTHICAL_PIECES.getOrDefault(piece.id,
                new double[]{3.0, 3.0, 0.1});
        if (!pdc.has(defenseKey, PersistentDataType.DOUBLE)) pdc.set(defenseKey, PersistentDataType.DOUBLE, defaults[0]);
        if (!pdc.has(toughnessKey, PersistentDataType.DOUBLE)) pdc.set(toughnessKey, PersistentDataType.DOUBLE, defaults[1]);
        if (!pdc.has(kbResistKey, PersistentDataType.DOUBLE)) pdc.set(kbResistKey, PersistentDataType.DOUBLE, defaults[2]);

        double defense = pdc.getOrDefault(defenseKey, PersistentDataType.DOUBLE, defaults[0]);
        double toughness = pdc.getOrDefault(toughnessKey, PersistentDataType.DOUBLE, defaults[1]);
        double kbResist = pdc.getOrDefault(kbResistKey, PersistentDataType.DOUBLE, defaults[2]);

        if (!pdc.has(blockRateKey, PersistentDataType.DOUBLE)) pdc.set(blockRateKey, PersistentDataType.DOUBLE, GravenConfig.MYTHICAL_BLOCK_RATE);
        if (!pdc.has(blockPowerKey, PersistentDataType.DOUBLE)) pdc.set(blockPowerKey, PersistentDataType.DOUBLE, GravenConfig.MYTHICAL_BLOCK_POWER);
        double blockRate = pdc.getOrDefault(blockRateKey, PersistentDataType.DOUBLE, GravenConfig.MYTHICAL_BLOCK_RATE);
        double blockPower = pdc.getOrDefault(blockPowerKey, PersistentDataType.DOUBLE, GravenConfig.MYTHICAL_BLOCK_POWER);

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
        lore.add(TooltipBuilder.durabilityLine("<green>" + remainingDurability + "/" + maxDurability + "</green>"));
        lore.add(TooltipBuilder.section("ATTRIBUTES"));
        lore.add(TooltipBuilder.attribute("🛡", "Defense", "#ADD8E6", formatStat(defense)));
        lore.add(TooltipBuilder.attribute("💎", "ArT", "#7DD3FC", formatStat(toughness)));
        lore.add(TooltipBuilder.attribute("⚓", "KbR", "#93C5FD", formatStat(kbResist)));
        lore.add(TooltipBuilder.attribute("✦", "Block Rate", "#5EEAD4", formatStat(blockRate) + "%", false));
        lore.add(TooltipBuilder.attribute("🔥", "Block Power", "#FCD34D", formatStat(blockPower)));
        lore.add(TooltipBuilder.divider());

        meta.lore(lore);
        item.setItemMeta(meta);

        AttributeHider.hideAllFlags(item);
    }

    private static String formatStat(double value) {
        return String.format("%.1f", value);
    }

    /**
     * Block system: while wearing set pieces, incoming attacks have a chance to be
     * partially blocked. Each worn piece carries its own block rate/power (tunable
     * per piece via /gravencore config); the piece with the highest block rate
     * does the blocking. A shield-clang sound and impact particles play on trigger.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAttackBlocked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double bestRate = 0.0;
        double bestPower = 0.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (!isItem(armor) || !armor.hasItemMeta()) continue;
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();
            double rate = pdc.getOrDefault(blockRateKey, PersistentDataType.DOUBLE, GravenConfig.MYTHICAL_BLOCK_RATE);
            if (rate > bestRate) {
                bestRate = rate;
                bestPower = pdc.getOrDefault(blockPowerKey, PersistentDataType.DOUBLE, GravenConfig.MYTHICAL_BLOCK_POWER);
            }
        }
        if (bestRate <= 0.0) return;

        double rate = Math.min(100.0, bestRate);
        if (ThreadLocalRandom.current().nextDouble(100.0) >= rate) return;

        double power = Math.max(0.0, bestPower);
        event.setDamage(Math.max(0.0, event.getDamage() - power));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0.0, 1.2, 0.0), 10, 0.3, 0.3, 0.3, 0.1);
    }
}
