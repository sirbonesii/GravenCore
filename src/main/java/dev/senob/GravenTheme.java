package dev.senob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class GravenTheme {

    // 1. Centralized MiniMessage Parser
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // 2. Global Server Colors
    public static final TextColor ERROR = TextColor.color(0xFF5555);
    public static final TextColor SUCCESS = TextColor.color(0x55FF55);
    public static final TextColor MUTED = TextColor.color(0xAAAAAA);

    // 3. Weapon Specific Colors
    public static final TextColor REDLINE = TextColor.color(0xFF3B1F);
    public static final TextColor DRACULA = TextColor.color(0xAA0000);

    /**
     * Instantly parses MiniMessage tags (like <red> or <gradient>) into Components,
     * while automatically disabling the default italics applied to item lore.
     */
    public static Component parse(String text) {
        return MM.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * A standard prefix for your plugin's chat messages.
     */
    public static Component prefix() {
        return parse("<dark_gray>[</dark_gray><gradient:#FF3B1F:#FF9E2C><bold>GravenCore</bold></gradient><dark_gray>]</dark_gray> ");
    }
}