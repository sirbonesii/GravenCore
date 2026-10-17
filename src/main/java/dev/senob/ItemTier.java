package dev.senob;

import net.kyori.adventure.text.format.TextColor;

/**
 * Item rarity tiers shown on the tooltip's tier line.
 * Only MYTHICAL exists for now; COMMON, RARE, EPIC, LEGENDARY, etc.
 * will be added with the full tier system later.
 */
public enum ItemTier {

    MYTHICAL("mythical", "Mythical", TextColor.color(0xF8F8A0));

    private final String id;
    private final String displayName;
    private final TextColor color;

    ItemTier(String id, String displayName, TextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

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
