package dev.senob.core;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Admin mode ({@code /admin toggle}): an admin can drop to playing as a
 * regular player, and toggle back. The mode is stored in the player's
 * own PDC, so it survives relogs, and it defaults to ON — anyone who
 * never toggles keeps exactly the behavior they had before.
 *
 * <p>Everything that requires an admin permission in GravenCore goes
 * through {@link #isAdmin} / {@link #isHomesAdmin} instead of a raw
 * permission check, so a toggled-off admin is treated as a regular
 * player: GravenCore admin commands refuse, vanished players hide
 * from them, and the server-wide command blocklist
 * (AdminModeListener) applies. The toggle command itself checks the
 * raw permission, never this gate — otherwise a toggled-off admin
 * could never toggle back on. Console and command blocks are always
 * admin (they have no mode to toggle).</p>
 */
public final class AdminMode {

    private static NamespacedKey modeKey;

    private AdminMode() {
    }

    /** Called once from onEnable, before any command or listener runs. */
    public static void init(Plugin plugin) {
        modeKey = new NamespacedKey(plugin, "admin_mode");
    }

    /** True when the player's admin mode is ON (default ON when never toggled). */
    public static boolean isAdminMode(Player player) {
        if (modeKey == null) return true;
        Byte value = player.getPersistentDataContainer().get(modeKey, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    public static void setAdminMode(Player player, boolean on) {
        if (modeKey == null) return;
        player.getPersistentDataContainer().set(modeKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
    }

    /** gravencore.admin, respected only while admin mode is ON. */
    public static boolean isAdmin(CommandSender sender) {
        if (!sender.hasPermission("gravencore.admin")) return false;
        if (sender instanceof Player player) return isAdminMode(player);
        return true;
    }

    /** gravehomes.admin, respected only while admin mode is ON. */
    public static boolean isHomesAdmin(CommandSender sender) {
        if (!sender.hasPermission("gravehomes.admin")) return false;
        if (sender instanceof Player player) return isAdminMode(player);
        return true;
    }
}
