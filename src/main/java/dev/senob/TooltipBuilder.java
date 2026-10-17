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
     * The standard divider line placed between tooltip sections.
     */
    public static Component divider() {
        return GravenTheme.parse("<gray>—————————————————</gray>");
    }

    /**
     * A section header, e.g. ATTRIBUTES or EFFECTS.
     */
    public static Component section(String title) {
        return GravenTheme.parse("<gold><bold>" + title + "</bold></gold>");
    }

    /**
     * The "Tier: Mythical" line in the tier's color.
     */
    public static Component tierLine(ItemTier tier) {
        return GravenTheme.parse("<gray>Tier:</gray> " + tier.wrap("<bold>" + tier.getDisplayName() + "</bold>"));
    }

    /**
     * A single attribute row, e.g. "• ⚔ Attack Damage: +2.0".
     * The "+" prefix is added automatically; pass the plain value.
     */
    public static Component attribute(String icon, String label, String value) {
        return GravenTheme.parse("<dark_gray>• " + icon + " " + label + ":</gray> <green>+" + value + "</green>");
    }

    /**
     * The standard "Created by" footer.
     */
    public static Component creatorFooter() {
        return GravenTheme.parse("<dark_red>✦</dark_red> <gray>Created by</gray> <gradient:#AA0000:#FF5555><italic>" + GravenConfig.CREATOR + "</italic></gradient>");
    }
}
