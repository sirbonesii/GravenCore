package dev.senob;

import net.kyori.adventure.text.Component;

/**
 * Builds the sectioned item tooltips (Tier / ATTRIBUTES / ABILITY / EFFECTS)
 * shared by GravenCore custom items.
 *
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
        return GravenTheme.parse("<gray>" + "—".repeat(DIVIDER_WIDTH) + "</gray>");
    }

    /**
     * A section header embedded in the middle of the divider,
     * e.g. ——————ATTRIBUTES——————. The dashes are sized so the whole
     * line matches divider() width (roughly: one em-dash ≈ two title
     * characters wide).
     */
    public static Component section(String title) {
        int titleDashes = Math.max(1, (int) Math.ceil(title.length() / 2.0));
        int side = Math.max(2, (DIVIDER_WIDTH - titleDashes) / 2);
        String dashes = "—".repeat(side);
        return GravenTheme.parse("<gray>" + dashes + "</gray><gold><bold>" + title + "</bold></gold><gray>" + dashes + "</gray>");
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
        return GravenTheme.parse("<dark_gray>•</dark_gray> <" + labelColor + ">" + icon + " " + label + "</" + labelColor + "><gray>:</gray> <green>+" + value + "</green>");
    }

    /**
     * A "Durability: ..." line, placed under the tier line (slightly indented).
     * Pass the value pre-colored, e.g. "<blue>Unbreakable</blue>".
     */
    public static Component durabilityLine(String value) {
        return GravenTheme.parse("  <gray>Durability:</gray> " + value);
    }

    /**
     * The standard "Created by" footer.
     */
    public static Component creatorFooter() {
        return GravenTheme.parse("<dark_red>✦</dark_red> <gray>Created by</gray> <gradient:#AA0000:#FF5555><italic>" + GravenConfig.CREATOR + "</italic></gradient>");
    }
}
