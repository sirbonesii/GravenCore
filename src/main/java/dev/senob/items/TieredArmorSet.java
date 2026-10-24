package dev.senob.items;

import dev.senob.listeners.AttributeHider;
import dev.senob.GravenCore;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
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
 * All six armor sets, best to lowest: Mythical (netherite) plus the five
 * tiers under it — Legendary (diamond), Epic (iron), Rare (chainmail),
 * Uncommon (gold) and Common (leather). Every set shares the same
 * mechanics exactly — same tooltip chrome (tier line, durability,
 * Enchantments, ATTRIBUTES), real attribute modifiers with per-item PDC
 * overrides, and the block system (best worn piece procs) — and differs
 * only in its tier data: materials, stats and block tuning, all loaded
 * from armor_tiers.yml &lt;tier&gt;.* (see GravenConfig). The block
 * ladder: Mythical 45%, Legendary 35%, Epic 25%, Rare 15%, Uncommon 10%,
 * Common 5%.
 *
 * Mythical pieces made by the old standalone MythicalArmorSet keep
 * working unchanged: this module uses the same PDC keys
 * ("mythical_armor" plus "mythical_&lt;stat&gt;") and the same
 * mythical stats (now in armor_tiers.yml), so no migration is needed.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class TieredArmorSet implements CustomItem, Listener {

    /** The six tiers this module owns, best to lowest. */
    public enum Tier {
        MYTHICAL("mythical", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS),
        LEGENDARY("legendary", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
        EPIC("epic", Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
        RARE("rare", Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS),
        UNCOMMON("uncommon", Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
        COMMON("common", Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);

        private final String id;
        private final Material[] materials; // helmet, chestplate, leggings, boots

        Tier(String id, Material helmet, Material chestplate, Material leggings, Material boots) {
            this.id = id;
            this.materials = new Material[]{helmet, chestplate, leggings, boots};
        }

        public String id() {
            return id;
        }

        public Material material(String pieceId) {
            return materials[PIECE_IDS.indexOf(pieceId)];
        }

        public static Tier fromId(String id) {
            if (id != null) {
                for (Tier tier : values()) {
                    if (tier.id.equalsIgnoreCase(id)) return tier;
                }
            }
            return null;
        }
    }

    /** The four set pieces, in give order. */
    public static final List<String> PIECE_IDS = List.of("helmet", "chestplate", "leggings", "boots");

    private static final EquipmentSlotGroup[] SLOT_GROUPS = {
            EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};

    private final GravenCore plugin;

    public TieredArmorSet(GravenCore plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("Tiered Armor Sets module initialized!");
    }

    private NamespacedKey pieceKey(String tierId) {
        return new NamespacedKey(plugin, tierId + "_armor");
    }

    private NamespacedKey statKey(String tierId, String stat) {
        return new NamespacedKey(plugin, tierId + "_" + stat);
    }

    /**
     * The PDC key marking a tier's pieces. Registered with
     * DurabilityLoreListener so the durability lore stays live.
     */
    public NamespacedKey idKey(String tierId) {
        return pieceKey(tierId);
    }

    @Override
    public boolean isItem(ItemStack item) {
        return getTierId(item) != null;
    }

    /** The tier id of a piece ("legendary", "epic", ...), or null. */
    public String getTierId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (Tier tier : Tier.values()) {
            if (pdc.has(pieceKey(tier.id), PersistentDataType.STRING)) return tier.id;
        }
        return null;
    }

    /** The piece id of an item ("helmet", ...), or null if not ours. */
    public String getPieceId(ItemStack item) {
        String tierId = getTierId(item);
        if (tierId == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(pieceKey(tierId), PersistentDataType.STRING);
    }

    public ItemStack createPiece(String tierId, String pieceId) {
        Tier tier = Tier.fromId(tierId);
        if (tier == null || !PIECE_IDS.contains(pieceId)) return null;
        ItemStack item = new ItemStack(tier.material(pieceId));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(pieceKey(tier.id), PersistentDataType.STRING, pieceId);
            item.setItemMeta(meta);
        }
        syncPieceStatsAndLore(item, tier.id, pieceId);
        return item;
    }

    /**
     * Converts a Legendary tiered piece into the Mythical piece for the
     * same slot, carrying enchantments, damage (clamped to the
     * Mythical max) and a custom name from {@code transferFrom}.
     * Returns null if the input isn't a Legendary piece. Shared by
     * the smithing conversion and the altar ritual, so both upgrade
     * paths behave the same.
     *
     * <p>The two arguments are usually the same item. They differ
     * for smithing: the tier identity is read from the base piece
     * (whose PDC is guaranteed), while enchantments, damage and the
     * name are read from the vanilla smithing result (which already
     * merged them from the base) — so the conversion works whether
     * or not the result kept the base's PDC.
     */
    public ItemStack upgradeLegendaryToMythical(ItemStack legendary) {
        return upgradeLegendaryToMythical(legendary, legendary);
    }

    public ItemStack upgradeLegendaryToMythical(ItemStack legendary, ItemStack transferFrom) {
        if (!"legendary".equals(getTierId(legendary))) return null;
        String pieceId = getPieceId(legendary);
        if (pieceId == null) return null;
        ItemStack converted = createPiece("mythical", pieceId);
        if (converted == null) return null;

        ItemMeta sourceMeta = (transferFrom != null) ? transferFrom.getItemMeta() : null;
        boolean changed = false;

        // Enchantments on the source transfer to the new piece
        if (sourceMeta != null && sourceMeta.hasEnchants()) {
            ItemMeta convertedMeta = converted.getItemMeta();
            if (convertedMeta != null) {
                sourceMeta.getEnchants().forEach((enchantment, level) ->
                        convertedMeta.addEnchant(enchantment, level, true));
                converted.setItemMeta(convertedMeta);
                changed = true;
            }
        }

        // Damage on the source carries over, clamped to the converted
        // piece's own max durability
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
        if (changed) syncPieceStatsAndLore(converted);

        // A renamed source keeps its custom name through the conversion;
        // the re-sync resets the name to the vanilla one, so restore it
        if (sourceMeta != null && sourceMeta.hasDisplayName()) {
            Component customName = sourceMeta.displayName();
            ItemMeta convertedMeta = converted.getItemMeta();
            if (convertedMeta != null && customName != null) {
                convertedMeta.displayName(customName);
                converted.setItemMeta(convertedMeta);
            }
        }

        return converted;
    }

    /** Re-syncs a piece, deriving its tier and piece id from its PDC. */
    public void syncPieceStatsAndLore(ItemStack item) {
        String tierId = getTierId(item);
        String pieceId = getPieceId(item);
        if (tierId != null && pieceId != null) syncPieceStatsAndLore(item, tierId, pieceId);
    }

    public void syncPieceStatsAndLore(ItemStack item, String tierId, String pieceId) {
        Tier tier = Tier.fromId(tierId);
        GravenConfig.ArmorTierStats stats = GravenConfig.ARMOR_TIERS.get(tierId);
        if (tier == null || stats == null || !PIECE_IDS.contains(pieceId)) return;
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String vanillaName = vanillaName(tier.material(pieceId));
        meta.displayName(GravenTheme.parse("<gray>" + vanillaName + "</gray>"));
        meta.itemName(GravenTheme.parse("<gray>" + vanillaName + "</gray>"));
        meta.setUnbreakable(false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        int maxDurability = stats.durability;
        int remainingDurability = maxDurability;
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(maxDurability);
            remainingDurability = Math.max(0, maxDurability - damageable.getDamage());
        }

        NamespacedKey defenseKey = statKey(tierId, "defense");
        NamespacedKey toughnessKey = statKey(tierId, "toughness");
        NamespacedKey kbResistKey = statKey(tierId, "kbresist");
        NamespacedKey blockRateKey = statKey(tierId, "blockrate");
        NamespacedKey blockPowerKey = statKey(tierId, "blockpower");

        double[] defaults = stats.pieces.getOrDefault(pieceId, new double[]{1.0, 0.0, 0.0});
        if (!pdc.has(defenseKey, PersistentDataType.DOUBLE)) pdc.set(defenseKey, PersistentDataType.DOUBLE, defaults[0]);
        if (!pdc.has(toughnessKey, PersistentDataType.DOUBLE)) pdc.set(toughnessKey, PersistentDataType.DOUBLE, defaults[1]);
        if (!pdc.has(kbResistKey, PersistentDataType.DOUBLE)) pdc.set(kbResistKey, PersistentDataType.DOUBLE, defaults[2]);

        double defense = pdc.getOrDefault(defenseKey, PersistentDataType.DOUBLE, defaults[0]);
        double toughness = pdc.getOrDefault(toughnessKey, PersistentDataType.DOUBLE, defaults[1]);
        double kbResist = pdc.getOrDefault(kbResistKey, PersistentDataType.DOUBLE, defaults[2]);

        if (!pdc.has(blockRateKey, PersistentDataType.DOUBLE)) pdc.set(blockRateKey, PersistentDataType.DOUBLE, stats.blockRate);
        if (!pdc.has(blockPowerKey, PersistentDataType.DOUBLE)) pdc.set(blockPowerKey, PersistentDataType.DOUBLE, stats.blockPower);
        double blockRate = pdc.getOrDefault(blockRateKey, PersistentDataType.DOUBLE, stats.blockRate);
        double blockPower = pdc.getOrDefault(blockPowerKey, PersistentDataType.DOUBLE, stats.blockPower);

        EquipmentSlotGroup slotGroup = SLOT_GROUPS[PIECE_IDS.indexOf(pieceId)];
        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE);
        meta.addAttributeModifier(Attribute.ARMOR,
                new AttributeModifier(new NamespacedKey(plugin, tierId + "_armor_" + pieceId),
                        defense, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(new NamespacedKey(plugin, tierId + "_toughness_" + pieceId),
                        toughness, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                new AttributeModifier(new NamespacedKey(plugin, tierId + "_kbresist_" + pieceId),
                        kbResist, AttributeModifier.Operation.ADD_NUMBER, slotGroup));

        ItemTier itemTier = ItemTier.fromId(stats.tier);

        List<Component> lore = new ArrayList<>();
        lore.add(TooltipBuilder.tierLine(itemTier));
        lore.add(TooltipBuilder.durabilityLine("<green>" + remainingDurability + "/" + maxDurability + "</green>"));
        List<Component> enchants = TooltipBuilder.enchantLines(meta);
        if (!enchants.isEmpty()) {
            lore.add(TooltipBuilder.section("Enchantments"));
            lore.addAll(enchants);
        }
        lore.add(TooltipBuilder.section("ATTRIBUTES"));
        lore.add(TooltipBuilder.attribute("🛡", "Defense", "#ADD8E6", formatStat(defense)));
        lore.add(TooltipBuilder.attribute("💎", "Armor Tough", "#7DD3FC", formatStat(toughness)));
        lore.add(TooltipBuilder.attribute("⚓", "Knockback Res", "#93C5FD", formatStat(kbResist)));
        lore.add(TooltipBuilder.attribute("✦", "Block Rate", "#5EEAD4", formatStat(blockRate) + "%", false));
        lore.add(TooltipBuilder.attribute("🔥", "Block Power", "#FCD34D", formatStat(blockPower)));
        lore.add(TooltipBuilder.divider());

        TooltipBuilder.fitDividers(lore);
        meta.lore(lore);
        item.setItemMeta(meta);

        AttributeHider.hideAllFlags(item);
    }

    /** "DIAMOND_CHESTPLATE" -> "Diamond Chestplate". */
    private static String vanillaName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static String formatStat(double value) {
        return String.format("%.1f", value);
    }

    /**
     * Block system: while wearing pieces of these sets, incoming attacks
     * have a chance to be partially blocked. Each worn piece carries its
     * own block rate/power (tunable per piece via /gravencore config);
     * the piece with the highest block rate does the blocking, with its
     * tier's block sound and impact particles on trigger. Pieces from
     * different tiers can be mixed — the best piece wins.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAttackBlocked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double bestRate = 0.0;
        double bestPower = 0.0;
        GravenConfig.ArmorTierStats bestStats = null;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            String tierId = getTierId(armor);
            if (tierId == null) continue;
            GravenConfig.ArmorTierStats stats = GravenConfig.ARMOR_TIERS.get(tierId);
            if (stats == null) continue;
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();
            double rate = pdc.getOrDefault(statKey(tierId, "blockrate"), PersistentDataType.DOUBLE, stats.blockRate);
            if (rate > bestRate) {
                bestRate = rate;
                bestPower = pdc.getOrDefault(statKey(tierId, "blockpower"), PersistentDataType.DOUBLE, stats.blockPower);
                bestStats = stats;
            }
        }
        if (bestRate <= 0.0 || bestStats == null) return;

        double rate = Math.min(100.0, bestRate);
        if (ThreadLocalRandom.current().nextDouble(100.0) >= rate) return;

        double power = Math.max(0.0, bestPower);
        event.setDamage(Math.max(0.0, event.getDamage() - power));

        player.getWorld().playSound(player.getLocation(),
                bestStats.blockSound, bestStats.blockVolume, bestStats.blockPitch);
        player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0.0, 1.2, 0.0), 10, 0.3, 0.3, 0.3, 0.1);
    }
}
