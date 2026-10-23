package dev.senob.items;

import net.kyori.adventure.text.format.TextColor;

/**
 * Item rarity tiers shown on the tooltip's tier line, best to lowest:
 * MYTHICAL, LEGENDARY, EPIC, RARE, UNCOMMON, COMMON. The hex colors are
 * Aaron's client text-effect triggers (aurora, lava, iterating jump,
 * blinking, green-yellow gradient, sequential spin), so each tier's
 * line animates differently in game.
 */
public enum ItemTier {

    MYTHICAL("mythical", "Mythical", TextColor.color(0xF8F8A0)),
    LEGENDARY("legendary", "Legendary", TextColor.color(0xF8F894)),
    EPIC("epic", "Epic", TextColor.color(0xF8F878)),
    RARE("rare", "Rare", TextColor.color(0xF8F864)),
    UNCOMMON("uncommon", "Uncommon", TextColor.color(0xF8F888)),
    COMMON("common", "Common", TextColor.color(0xF8F870));

    private final String id;
    private final String displayName;
    private final TextColor color;

    ItemTier(String id, String displayName, TextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }
    @SuppressWarnings("unused")
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TextColor getColor() {
        return color;
    }

    /**
     * Wraps MiniMessage content in this tier's color.
     */
    public String wrap(String miniMessage) {
        return "<color:#" + String.format("%06X", color.value()) + ">" + miniMessage + "</color>";
    }

    /**
     * Resolves a tier id from config, falling back to MYTHICAL.
     */
    public static ItemTier fromId(String id) {
        if (id != null) {
            for (ItemTier tier : values()) {
                if (tier.id.equalsIgnoreCase(id)) {
                    return tier;
                }
            }
        }
        return MYTHICAL;
    }
}
