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
     * The standard divider line placed between tooltip sections.
     */
    public static Component divider() {
        return divider(DIVIDER_WIDTH);
    }

    /**
     * A divider line with an explicit width, in dashes.
     */
    public static Component divider(int width) {
        return GravenTheme.parse("<gray>" + "-".repeat(Math.max(1, width)) + "</gray>");
    }

    /**
     * A section header embedded in the middle of the divider,
     * e.g. -------------ATTRIBUTES-------------. Sized to the
     * standard divider width; see {@link #section(String, int)}.
     */
    public static Component section(String title) {
        return section(title, DIVIDER_WIDTH);
    }

    /**
     * A section header embedded in the middle of the divider,
     * e.g. -------------ATTRIBUTES-------------. The title is centered with
     * equal dashes per side so all headers align consistently, Teldaria-style.
     */
    public static Component section(String title, int width) {
        int side = Math.max(4, (width - title.length()) / 2);
        String dashes = "-".repeat(side);
        return GravenTheme.parse("<gray>" + dashes + "</gray><gold><bold>" + title + "</bold></gold><gray>" + dashes + "</gray>");
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
        // Full-bleed: dividers run past the content. Minimum 34 chars so even
        // short tooltips get the wide Teldaria look; grows if content is wider.
        int dividerWidth = Math.max(34, width + 8);
        ListIterator<Component> it = lore.listIterator();
        while (it.hasNext()) {
            Component line = it.next();
            String text = plain.serialize(line);
            if (text.matches("-+")) {
                it.set(divider(dividerWidth));
            } else if (text.matches("-+[^-]+-+")) {
                it.set(section(text.replaceAll("^-+|-+$", ""), dividerWidth));
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
     * The standard "Created by" footer.
     */
    public static Component creatorFooter() {
        return GravenTheme.parse("<dark_red>✦</dark_red> <gray>Created by</gray> <gradient:#AA0000:#FF5555><italic>" + GravenConfig.CREATOR + "</italic></gradient>");
    }
}
