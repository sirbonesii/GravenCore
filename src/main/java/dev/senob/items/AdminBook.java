package dev.senob.items;

import dev.senob.GravenCore;
import dev.senob.core.GravenTheme;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Admin Book: a plain BOOK item that, on right-click, opens the item
 * menu as a real book UI (Adventure Book), not an inventory grid.
 *
 * Menu layout (6 pages, computed below so the contents links stay right):
 *   page 1: contents — Armor / Weapons / Special (change_page jumps)
 *   pages 2-4: armor, two tiers per page (tier header + 4 pieces)
 *   page 5: weapons (redline base + upgraded, dracula, celerity)
 *   page 6: special (Alter of Despair)
 * Every piece line is clickable: it runs /gravencore bookdialog <id>,
 * which opens the give dialogue (give to me / give to player + name bar).
 *
 * Icons: book pages are text-only, so each line starts with a glyph from
 * the gravenmoor:icons bitmap font (U+E100-U+E107, pack file
 * assets/gravenmoor/font/icons.json). The glyph renders tinted by the
 * line's text color, so the icon matches the entry color.
 *
 * Colors: the tier text-effect colors (#F8F8xx) are near-white and
 * unreadable on parchment, so book lines use darker material colors
 * instead (netherite/diamond/iron/chainmail/gold/leather). Chat messages
 * still use the canonical tier/weapon colors (see GravenCoreCommand).
 */
public class AdminBook implements CustomItem {

    // --- Icon glyphs (gravenmoor:icons) ---
    public static final char GLYPH_HELMET = '\uE100';
    public static final char GLYPH_CHESTPLATE = '\uE101';
    public static final char GLYPH_LEGGINGS = '\uE102';
    public static final char GLYPH_BOOTS = '\uE103';
    public static final char GLYPH_SPEAR = '\uE104';
    public static final char GLYPH_BAT = '\uE105'; // Dracula's Plate
    public static final char GLYPH_CELERITY_BOOT = '\uE106';
    public static final char GLYPH_DROPPER = '\uE107'; // Alter of Despair

    /** One clickable menu entry. tierId/pieceId set for armor, weaponId for weapons, else null. */
    public static final class Entry {
        public final String id;
        public final String category; // "armor", "weapon", "special"
        public final String label;
        public final String bookColor; // parchment-readable hex, with leading '#'
        public final char glyph;
        public final String tierId;
        public final String pieceId;
        public final String weaponId;

        public Entry(String id, String category, String label, String bookColor, char glyph,
                     String tierId, String pieceId, String weaponId) {
            this.id = id;
            this.category = category;
            this.label = label;
            this.bookColor = bookColor;
            this.glyph = glyph;
            this.tierId = tierId;
            this.pieceId = pieceId;
            this.weaponId = weaponId;
        }
    }

    // Parchment-readable material colors, one per tier (see class javadoc).
    private static final List<String> TIER_ORDER =
            List.of("mythical", "legendary", "epic", "rare", "uncommon", "common");
    private static final Map<String, String> TIER_BOOK_COLORS = Map.of(
            "mythical", "#3A3A42",
            "legendary", "#2A8A7A",
            "epic", "#6A6A6A",
            "rare", "#4A4A4A",
            "uncommon", "#8A6A00",
            "common", "#7A4A1A");
    private static final Map<String, Character> PIECE_GLYPHS = Map.of(
            "helmet", GLYPH_HELMET,
            "chestplate", GLYPH_CHESTPLATE,
            "leggings", GLYPH_LEGGINGS,
            "boots", GLYPH_BOOTS);

    public static final List<Entry> ENTRIES = buildEntries();
    public static final Map<String, Entry> ENTRY_BY_ID = buildIndex();

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        for (String tierId : TIER_ORDER) {
            String tierName = ItemTier.fromId(tierId).getDisplayName();
            String color = TIER_BOOK_COLORS.get(tierId);
            for (String pieceId : TieredArmorSet.PIECE_IDS) {
                String pieceName = pieceId.substring(0, 1).toUpperCase() + pieceId.substring(1);
                entries.add(new Entry(tierId + "_" + pieceId, "armor",
                        tierName + " " + pieceName, color, PIECE_GLYPHS.get(pieceId),
                        tierId, pieceId, null));
            }
        }
        entries.add(new Entry("redline_base", "weapon", "Base Redline Spear",
                "#C23B22", GLYPH_SPEAR, null, null, "redline_base"));
        entries.add(new Entry("redline_upgraded", "weapon", "Upgraded Redline Spear",
                "#C23B22", GLYPH_SPEAR, null, null, "redline_upgraded"));
        entries.add(new Entry("dracula", "weapon", "Dracula's Plate",
                "#AA0000", GLYPH_BAT, null, null, "dracula"));
        entries.add(new Entry("celerity", "weapon", "Celerity Boots",
                "#5A5A3A", GLYPH_CELERITY_BOOT, null, null, "celerity"));
        entries.add(new Entry("alter", "special", "Alter of Despair",
                "#7A6A00", GLYPH_DROPPER, null, null, null));
        return List.copyOf(entries);
    }

    private static Map<String, Entry> buildIndex() {
        Map<String, Entry> index = new HashMap<>();
        for (Entry entry : ENTRIES) index.put(entry.id, entry);
        return Map.copyOf(index);
    }

    private final NamespacedKey adminBookKey;

    public AdminBook(GravenCore plugin) {
        this.adminBookKey = new NamespacedKey(plugin, "admin_book");
    }

    /** The trigger item: a plain book, PDC-tagged so ItemManager routes its right-click here. */
    public ItemStack createAdminBookItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(GravenTheme.parse("<gold><bold>Admin Book</bold></gold>"));
        meta.lore(List.of(GravenTheme.parse("<gray>Right-click to open the item menu.</gray>")));
        meta.getPersistentDataContainer().set(adminBookKey, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean isItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(adminBookKey, PersistentDataType.INTEGER);
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("gravencore.admin")) {
            player.sendMessage(GravenTheme.parse("<red>You do not have permission to use the Admin Book.</red>"));
            return;
        }
        // Cancel so right-clicking a block (chest, door, ...) with the book
        // only opens the menu instead of also triggering that block.
        event.setCancelled(true);
        player.openBook(buildMenuBook());
    }

    /** Builds the 6-page menu book: contents + armor (2 tiers/page) + weapons + special. */
    public static Book buildMenuBook() {
        int tiersPerPage = 2;
        int armorPageCount = (TIER_ORDER.size() + tiersPerPage - 1) / tiersPerPage;
        int weaponsPage = 1 + armorPageCount + 1;
        int specialPage = weaponsPage + 1;

        List<Component> pages = new ArrayList<>();
        pages.add(page(List.of(
                "<bold>GravenCore Items</bold>",
                "",
                "<blue><click:change_page:2>Armor</click></blue>",
                "<blue><click:change_page:" + weaponsPage + ">Weapons</click></blue>",
                "<blue><click:change_page:" + specialPage + ">Special</click></blue>")));

        for (int start = 0; start < TIER_ORDER.size(); start += tiersPerPage) {
            List<String> lines = new ArrayList<>();
            for (int t = start; t < Math.min(start + tiersPerPage, TIER_ORDER.size()); t++) {
                String tierId = TIER_ORDER.get(t);
                lines.add("<bold><color:" + TIER_BOOK_COLORS.get(tierId) + ">"
                        + ItemTier.fromId(tierId).getDisplayName() + "</color></bold>");
                for (Entry entry : ENTRIES) {
                    if ("armor".equals(entry.category) && tierId.equals(entry.tierId)) {
                        lines.add(pieceLine(entry));
                    }
                }
            }
            lines.add("");
            lines.add("<blue><click:change_page:1>Back to contents</click></blue>");
            pages.add(page(lines));
        }

        List<String> weaponLines = new ArrayList<>();
        weaponLines.add("<bold>Weapons</bold>");
        for (Entry entry : ENTRIES) {
            if ("weapon".equals(entry.category)) weaponLines.add(pieceLine(entry));
        }
        weaponLines.add("");
        weaponLines.add("<blue><click:change_page:1>Back to contents</click></blue>");
        pages.add(page(weaponLines));

        List<String> specialLines = new ArrayList<>();
        specialLines.add("<bold>Special</bold>");
        for (Entry entry : ENTRIES) {
            if ("special".equals(entry.category)) specialLines.add(pieceLine(entry));
        }
        specialLines.add("");
        specialLines.add("<blue><click:change_page:1>Back to contents</click></blue>");
        pages.add(page(specialLines));

        return Book.book(Component.text("GravenCore Items"), Component.text("Senob"), pages);
    }

    private static Component page(List<String> miniMessageLines) {
        List<Component> lines = new ArrayList<>();
        for (String line : miniMessageLines) lines.add(GravenTheme.parse(line));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** One clickable piece line: [icon glyph] Name — click opens the give dialogue for it. */
    private static String pieceLine(Entry entry) {
        return "<hover:show_text:'Click to choose a recipient'>"
                + "<click:run_command:'/gravencore bookdialog " + entry.id + "'>"
                + "<font:gravenmoor:icons>" + entry.glyph + "</font>"
                + " <color:" + entry.bookColor + ">" + entry.label + "</color>"
                + "</click></hover>";
    }
}
