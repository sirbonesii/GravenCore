package dev.senob.items;

import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Builds the sectioned item tooltips (Tier / ATTRIBUTES / ABILITY / EFFECTS)
 * shared by GravenCore custom items.
 * Layout chrome (dividers, headers, footers) lives here so item classes
 * only declare their content.
 */
public final class TooltipBuilder {

    private TooltipBuilder() {
    }

    /**
     * Dash budget for divider lines; section headers size their dashes
     * to match so every line aligns.
     */
    private static final int DIVIDER_WIDTH = 17;

    /**
     * The server pack's tooltip font (assets/minecraft/font/dividers.json).
     * Its glyphs are textured section chrome: they render at their texture
     * width, so glyph lines are never resized by fitDividers.
     */
    private static final String TOOLTIP_FONT = "minecraft:dividers";
    private static final String DIVIDER_GLYPH = "\uE000";
    private static final String ENCHANTMENTS_GLYPH = "\uE001";
    private static final String ATTRIBUTES_GLYPH = "\uE002";
    private static final String ABILITY_GLYPH = "\uE003";
    private static final String EFFECTS_GLYPH = "\uE004";
    // \uE005 (description) has no section in the current tooltips; unwired.

    /**
     * A single glyph line in the tooltip font.
     */
    private static Component glyphLine(String glyph) {
        return GravenTheme.parse("<font:" + TOOLTIP_FONT + ">" + glyph + "</font>");
    }

    /**
     * The standard divider line placed between tooltip sections.
     */
    public static Component divider() {
        return glyphLine(DIVIDER_GLYPH);
    }

    /**
     * A divider line with an explicit width, in dashes. Kept for
     * fitDividers' resize pass over legacy dash lines; direct callers
     * use divider(), which returns the divider glyph.
     */
    public static Component divider(int width) {
        return GravenTheme.parse("<gray>" + "-".repeat(Math.max(1, width)) + "</gray>");
    }

    /**
     * A section header. Known titles render as their textured glyph
     * (glyphs-only, per Aaron's tooltip design); any other title falls
     * back to the dash header below.
     */
    public static Component section(String title) {
        String glyph = switch (title.toUpperCase()) {
            case "ENCHANTMENTS" -> ENCHANTMENTS_GLYPH;
            case "ATTRIBUTES" -> ATTRIBUTES_GLYPH;
            case "ABILITY" -> ABILITY_GLYPH;
            case "EFFECTS" -> EFFECTS_GLYPH;
            default -> null;
        };
        if (glyph != null) return glyphLine(glyph);
        return section(title, sectionColor(title), DIVIDER_WIDTH);
    }

    /**
     * A section header with an explicit title color and width, embedded
     * in the middle of the divider, e.g. ──────────ATTRIBUTES──────────.
     * The title is centered with equal line segments per side so all headers
     * align consistently, Teldaria-style. Each section has its own title
     * color (white ATTRIBUTES, aqua EFFECTS, gold ENCHANTMENTS).
     * This is the legacy dash renderer, kept for fitDividers' resize pass;
     * direct callers go through section(String), which maps known titles
     * to glyphs. The total character count exactly matches width so headers
     * align with plain dividers.
     */
    public static Component section(String title, String color, int width) {
        int left = Math.max(4, (width - title.length()) / 2);
        int right = Math.max(4, width - title.length() - left);
        String leftLine = "-".repeat(left);
        String rightLine = "-".repeat(right);
        return GravenTheme.parse("<gray>" + leftLine + "</gray><" + color + ">" + title + "</" + color + "><gray>" + rightLine + "</gray>");
    }

    /**
     * The Teldaria-style title color for a section.
     */
    private static String sectionColor(String title) {
        return switch (title.toUpperCase()) {
            case "ATTRIBUTES" -> "white";
            case "EFFECTS" -> "aqua";
            default -> "gold";
        };
    }

    /**
     * Resizes every divider and section-header line in the lore to span wider
     * than the widest content line, Teldaria-style: the dividers bleed to the
     * tooltip edges and define the box width instead of hugging the text.
     * Call after building the lore list, before meta.lore(lore).
     */
    public static void fitDividers(List<Component> lore) {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        int max = 0;
        for (Component line : lore) {
            String text = plain.serialize(line);
            if (isDividerText(text)) continue;
            max = Math.max(max, text.length());
        }
        int width = Math.max(DIVIDER_WIDTH, max);
        // Size dividers to the content width, not full-bleed.
        int dividerWidth = width;
        ListIterator<Component> it = lore.listIterator();
        while (it.hasNext()) {
            Component line = it.next();
            String text = plain.serialize(line);
            if (text.matches("-+")) {
                it.set(divider(dividerWidth));
            } else if (text.matches("-+[^-]+-+")) {
                String title = text.replaceAll("^-+|-+$", "");
                it.set(section(title, sectionColor(title), dividerWidth));
            }
        }
    }

    private static boolean isDividerText(String text) {
        return text.matches("-+") || text.matches("-+[^-]+-+");
    }

    /**
     * The "Tier: Mythical" line in the tier's color.
     */
    public static Component tierLine(ItemTier tier) {
        return GravenTheme.parse("<gray>Tier:</gray> " + tier.wrap("<bold>" + tier.getDisplayName() + "</bold>"));
    }

    /**
     * A single attribute row, e.g. "• ⚔ Atk Damage: +2.0".
     * The "+" prefix is added automatically; pass the plain value.
     * labelColor is a MiniMessage color (e.g. "green" or "#ADD8E6").
     * The ":" separator is always gray.
     */
    public static Component attribute(String icon, String label, String labelColor, String value) {
        return attribute(icon, label, labelColor, value, true);
    }

    /**
     * Same as {@link #attribute(String, String, String, String)}, but with control
     * over the "+" prefix (e.g. pass false for percent stats like "25.0%").
     */
    public static Component attribute(String icon, String label, String labelColor, String value, boolean plusPrefix) {
        String prefix = plusPrefix ? "+" : "";
        return GravenTheme.parse("<dark_gray>•</dark_gray> <" + labelColor + ">" + icon + " " + label + "</" + labelColor + "><gray>:</gray> <green>" + prefix + value + "</green>");
    }

    /**
     * A "Durability: ..." line, placed under the tier line (slightly indented).
     * Pass the value pre-colored, e.g. "<blue>Unbreakable</blue>".
     */
    public static Component durabilityLine(String value) {
        return GravenTheme.parse("  <gray>Durability:</gray> " + value);
    }

    /**
     * True if this lore line is a durability line (e.g. "Durability: 2096/2096").
     * Used by DurabilityLoreListener to find the line and refresh it as the
     * item takes damage.
     */
    public static boolean isDurabilityLine(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line).trim();
        return plain.startsWith("Durability:");
    }

    /**
     * The item's enchantments as lore lines, using vanilla's own display names.
     * Pair with ItemFlag.HIDE_ENCHANTS (which suppresses the client's automatic
     * enchantment block) so enchantments live under their own section instead
     * of floating at the top of the tooltip. Empty when the item has none.
     */
    public static List<Component> enchantLines(ItemMeta meta) {
        List<Component> lines = new ArrayList<>();
        meta.getEnchants().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getKey().toString()))
                .forEach(e -> lines.add(enchantLine(e.getKey(), e.getValue())));
        return lines;
    }

    private static Component enchantLine(Enchantment enchantment, int level) {
        return enchantment.displayName(level)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * True if the lore's Enchantments section no longer matches the item's
     * actual enchantments. The section is baked into the lore when the item
     * is synced, so enchanting (or disenchanting) the item afterwards leaves
     * it stale — the tooltip keeps showing the old enchantments, or none,
     * until something re-syncs the item. EnchantSyncListener uses this to
     * refresh only the pieces that actually changed.
     */
    public static boolean enchantSectionStale(ItemMeta meta) {
        List<Component> lore = meta.lore();
        if (lore == null) lore = List.of();

        // The enchant names the lore currently shows: the lines under the
        // Enchantments header, up to the next divider or section header
        // (enchantment names never contain dashes, headers/dividers do)
        List<String> shown = new ArrayList<>();
        boolean inSection = false;
        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!inSection) {
                if (plain.contains("Enchantments") || plain.contains(ENCHANTMENTS_GLYPH)) inSection = true;
                continue;
            }
            if (plain.contains("-") || plain.contains("—") || isChromeGlyphLine(plain)) break;
            shown.add(plain);
        }

        List<String> actual = new ArrayList<>();
        for (Component line : enchantLines(meta)) {
            actual.add(PlainTextComponentSerializer.plainText().serialize(line).trim());
        }
        return !shown.equals(actual);
    }

    /**
     * True if a lore line's plain text is one of the tooltip font's chrome
     * glyphs (divider or section header). Used by enchantSectionStale so a
     * following glyph header ends the enchantments section, the way a dash
     * line used to.
     */
    private static boolean isChromeGlyphLine(String plain) {
        return plain.contains(DIVIDER_GLYPH) || plain.contains(ENCHANTMENTS_GLYPH)
                || plain.contains(ATTRIBUTES_GLYPH) || plain.contains(ABILITY_GLYPH)
                || plain.contains(EFFECTS_GLYPH);
    }

    /**
     * The standard "Created by" footer.
     */
    public static Component creatorFooter() {
        return GravenTheme.parse("<dark_red>✦</dark_red> <gray>Created by</gray> <gradient:#AA0000:#FF5555><italic>" + GravenConfig.CREATOR + "</italic></gradient>");
    }
}
