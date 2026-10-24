package dev.senob.home_system;

import dev.senob.core.GravenTheme;
import dev.senob.guis.CustomGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"SpellCheckingInspection"})
public class HomeSystem implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final HomeManager homeManager;

    /** Home slots: 2 rows of 5, 10 homes per page (rows 2-3, cols 2-6). */
    private static final int[] HOME_SLOTS = {
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33
    };
    private static final int PAGE_SIZE = HOME_SLOTS.length;
    /** Bottom-row navigation: Back, help, Next. */
    private static final int SLOT_BACK = 48;
    private static final int SLOT_HELP = 49;
    private static final int SLOT_NEXT = 50;

    public HomeSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.homeManager = new HomeManager(plugin);

        for (String cmd : List.of("homes", "home", "sethome", "delhome", "checkhomes")) {
            if (plugin.getCommand(cmd) != null) {
                Objects.requireNonNull(plugin.getCommand(cmd)).setExecutor(this);
                Objects.requireNonNull(plugin.getCommand(cmd)).setTabCompleter(this);
            }
        }
        plugin.getLogger().info("HomeSystem module initialized!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("checkhomes")) {
            if (!sender.hasPermission("gravehomes.admin")) {
                sender.sendMessage(GravenTheme.parse("<red>You do not have permission to inspect homes.</red>"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(GravenTheme.parse("<red>Usage: /checkhomes <player></red>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(GravenTheme.parse("<red>Player not found.</red>"));
                return true;
            }
            if (sender instanceof Player viewer) {
                openHomesGui(viewer, target);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(GravenTheme.parse("<red>Only players can use home commands.</red>"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        switch (cmd) {
            case "homes" -> openHomesGui(player, player);

            case "sethome" -> {
                if (args.length < 1) {
                    if (!sender.hasPermission("gravehomes.member")) {
                        player.sendMessage(GravenTheme.parse("<red>Usage: /sethome <name></red>"));
                        return true;
                    }
                }
                String name = args[0].toLowerCase();
                Map<String, Location> homes = homeManager.getHomes(uuid);
                int maxHomes = homeManager.getMaxHomes(player);

                if (!homes.containsKey(name) && homes.size() >= maxHomes) {
                    player.sendMessage(GravenTheme.parse("<red>You have reached your maximum limit of " + maxHomes + " homes!</red>"));
                    return true;
                }

                homeManager.setHome(uuid, name, player.getLocation());
                player.sendMessage(GravenTheme.parse("<green>Successfully set home '<yellow>" + name + "</yellow>'!</green>"));

                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.25f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            }

            case "home" -> {
                if (args.length < 1) {
                    if (!sender.hasPermission("gravehomes.member")) {
                        player.sendMessage(GravenTheme.parse("<red>Usage: /home <name></red>"));
                        return true;
                    }
                }
                String name = args[0].toLowerCase();
                Location loc = homeManager.getHomes(uuid).get(name);
                if (loc == null) {
                    player.sendMessage(GravenTheme.parse("<red>Home '" + name + "' does not exist.</red>"));
                    return true;
                }
                player.teleport(loc);
                player.sendMessage(GravenTheme.parse("<green>Teleported to home '<yellow>" + name + "</yellow>'!</green>"));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }

            case "delhome" -> {
                if (args.length < 1) {
                    if (!sender.hasPermission("gravehomes.member")) {
                        player.sendMessage(GravenTheme.parse("<red>Usage: /delhome <name></red>"));
                        return true;
                    }
                }
                String name = args[0].toLowerCase();
                if (!homeManager.getHomes(uuid).containsKey(name)) {
                    player.sendMessage(GravenTheme.parse("<red>Home '" + name + "' does not exist.</red>"));
                    return true;
                }
                homeManager.deleteHome(uuid, name);
                player.sendMessage(GravenTheme.parse("<yellow>Deleted home '" + name + "'.</yellow>"));

                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }
        return true;
    }

    private void openHomesGui(@NotNull Player viewer, @NotNull Player target) {
        openHomesGui(viewer, target, 0);
    }

    private void openHomesGui(@NotNull Player viewer, @NotNull Player target, int page) {
        boolean isSelf = viewer.equals(target);
        String titleStr = isSelf ? "[HOMES]" : "[HOMES: " + target.getName() + "]";

        Map<String, Location> homes = homeManager.getHomes(target.getUniqueId());
        int maxSlots = homeManager.getMaxHomes(target);
        int totalPages = Math.max(1, (maxSlots + PAGE_SIZE - 1) / PAGE_SIZE);
        // Final for the click lambdas below
        final int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        CustomGUI gui = new CustomGUI(54, GravenTheme.parse("<dark_gray>" + titleStr + "</dark_gray>"));

        // Top row: the target's own head carries the old banner's info
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(target);
            skullMeta.displayName(GravenTheme.parse("<gold><bold>" + target.getName() + "'s Homes</bold></gold>"));
            skullMeta.lore(List.of(
                    GravenTheme.parse("<gray>Max Capacity: <yellow>" + maxSlots + "</yellow></gray>"),
                    GravenTheme.parse("<gray>Total Set: <yellow>" + homes.size() + "</yellow></gray>"),
                    GravenTheme.parse("<gray>Page: <yellow>" + (currentPage + 1) + " / " + totalPages + "</yellow></gray>")
            ));
            head.setItemMeta(skullMeta);
        }
        gui.setItem(4, head, null);

        // Framing: a full outer border — the top row around the head,
        // the whole bottom row around the navigation, and both side
        // columns top to bottom. The interior of rows 1 and 4 stays
        // empty, so the grid still floats mid-GUI.
        ItemStack pane = decorativePane();
        for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) gui.setItem(slot, pane, null);
        for (int slot : new int[]{9, 18, 27, 36}) gui.setItem(slot, pane, null);
        for (int slot : new int[]{17, 26, 35, 44}) gui.setItem(slot, pane, null);
        for (int slot : new int[]{45, 46, 47, 51, 52, 53}) gui.setItem(slot, pane, null);

        int firstIndex = currentPage * PAGE_SIZE;
        for (int i = 0; i < HOME_SLOTS.length; i++) {
            int globalIndex = firstIndex + i;
            int slot = HOME_SLOTS[i];
            if (globalIndex < maxSlots) {
                String homeNameIndex = getHomeNameByIndex(homes, globalIndex);
                if (homeNameIndex != null) {
                    ItemStack bed = new ItemStack(Material.BLUE_BED);
                    ItemMeta meta = bed.getItemMeta();
                    if (meta != null) {
                        meta.displayName(GravenTheme.parse("<aqua><bold>" + homeNameIndex + "</bold></aqua>"));
                        meta.lore(List.of(
                                GravenTheme.parse("<gray>Click to teleport to your home</gray>"),
                                GravenTheme.parse("<dark_red>Right-click to delete</dark_red>")
                        ));
                        bed.setItemMeta(meta);
                    }

                    final int returnPage = currentPage;
                    gui.setItem(slot, bed, event -> {
                        if (isSelf) {
                            if (event.isRightClick()) {
                                openConfirmDeleteGui(viewer, homeNameIndex, returnPage);
                            } else {
                                Location loc = homes.get(homeNameIndex);
                                if (loc != null) {
                                    viewer.closeInventory();
                                    viewer.teleport(loc);
                                    viewer.sendMessage(GravenTheme.parse("<green>Teleported to home '<yellow>" + homeNameIndex + "</yellow>'!</green>"));
                                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                                }
                            }
                        }
                    });
                } else {
                    ItemStack emptyBed = new ItemStack(Material.LIGHT_GRAY_BED);
                    ItemMeta meta = emptyBed.getItemMeta();
                    if (meta != null) {
                        meta.displayName(GravenTheme.parse("<white>SET HOME</white>"));
                        meta.lore(List.of(GravenTheme.parse("<gray>Click to create a home</gray>")));
                        emptyBed.setItemMeta(meta);
                    }

                    gui.setItem(slot, emptyBed, event -> {
                        if (isSelf) {
                            viewer.closeInventory();
                            String defaultName = "home";
                            int counter = 2;
                            while (homes.containsKey(defaultName)) {
                                defaultName = "home" + counter;
                                counter++;
                            }
                            homeManager.setHome(viewer.getUniqueId(), defaultName, viewer.getLocation());
                            viewer.sendMessage(GravenTheme.parse("<green>Created home '<yellow>" + defaultName + "</yellow>' at your current location!</green>"));

                            viewer.playSound(viewer.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.25f);
                            viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                        }
                    });
                }
            } else {
                ItemStack locked = new ItemStack(Material.BEDROCK);
                ItemMeta meta = locked.getItemMeta();
                if (meta != null) {
                    meta.displayName(GravenTheme.parse("<red>LOCKED SLOT</red>"));
                    meta.lore(List.of(GravenTheme.parse("<gray>Upgrade your rank for more homes.</gray>")));
                    locked.setItemMeta(meta);
                }
                gui.setItem(slot, locked, null);
            }
        }

        // Bottom-row navigation: pale oak hanging signs for Back/Next
        // (greyed name when that way is closed), a dark oak hanging
        // sign for help? — matching the reference look.
        if (currentPage > 0) {
            ItemStack back = namedItem(Material.PALE_OAK_HANGING_SIGN, "<green><bold>Back</bold></green>",
                    List.of(GravenTheme.parse("<gray>Go to page " + currentPage + "</gray>")));
            gui.setItem(SLOT_BACK, back, event ->
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(viewer, target, currentPage - 1), 1L));
        } else {
            ItemStack back = namedItem(Material.PALE_OAK_HANGING_SIGN, "<gray><bold>Back</bold></gray>",
                    List.of(GravenTheme.parse("<gray>You are on the first page.</gray>")));
            gui.setItem(SLOT_BACK, back, null);
        }

        ItemStack help = namedItem(Material.DARK_OAK_HANGING_SIGN, "<yellow><bold>help?</bold></yellow>",
                List.of(GravenTheme.parse("<gray>Click to learn how the homes system works.</gray>")));
        gui.setItem(SLOT_HELP, help, event ->
                Bukkit.getScheduler().runTaskLater(plugin, () -> openHelpGui(viewer, target, currentPage), 1L));

        if (currentPage < totalPages - 1) {
            ItemStack next = namedItem(Material.PALE_OAK_HANGING_SIGN, "<green><bold>Next</bold></green>",
                    List.of(GravenTheme.parse("<gray>Go to page " + (currentPage + 2) + "</gray>")));
            gui.setItem(SLOT_NEXT, next, event ->
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(viewer, target, currentPage + 1), 1L));
        } else {
            ItemStack next = namedItem(Material.PALE_OAK_HANGING_SIGN, "<gray><bold>Next</bold></gray>",
                    List.of(GravenTheme.parse("<gray>No more pages.</gray>")));
            gui.setItem(SLOT_NEXT, next, null);
        }

        gui.open(viewer);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    /** The help screen behind the "help?" button. */
    private void openHelpGui(@NotNull Player viewer, @NotNull Player target, int returnPage) {
        CustomGUI gui = new CustomGUI(27, GravenTheme.parse("<dark_gray>Homes Help</dark_gray>"));

        ItemStack setting = namedItem(Material.BOOK, "<yellow><bold>Setting a Home</bold></yellow>", List.of(
                GravenTheme.parse("<gray>Click an empty bed slot to create a home</gray>"),
                GravenTheme.parse("<gray>where you are standing. It gets named</gray>"),
                GravenTheme.parse("<gray>home, home2, home3... automatically.</gray>"),
                GravenTheme.parse("<gray>Or pick the name: /sethome <name></gray>")
        ));
        gui.setItem(10, setting, null);

        ItemStack teleporting = namedItem(Material.ENDER_PEARL, "<aqua><bold>Teleporting</bold></aqua>", List.of(
                GravenTheme.parse("<gray>Click a blue bed to teleport to that home.</gray>"),
                GravenTheme.parse("<gray>Or use /home <name>.</gray>")
        ));
        gui.setItem(12, teleporting, null);

        ItemStack deleting = namedItem(Material.RED_BED, "<red><bold>Deleting a Home</bold></red>", List.of(
                GravenTheme.parse("<gray>Right-click a blue bed, then confirm.</gray>"),
                GravenTheme.parse("<gray>Or use /delhome <name>.</gray>")
        ));
        gui.setItem(14, deleting, null);

        int maxSlots = homeManager.getMaxHomes(target);
        ItemStack limits = namedItem(Material.PAPER, "<green><bold>Limits & Pages</bold></green>", List.of(
                GravenTheme.parse("<gray>Your rank allows up to <yellow>" + maxSlots + "</yellow> homes.</gray>"),
                GravenTheme.parse("<gray>Bedrock slots are locked — upgrade</gray>"),
                GravenTheme.parse("<gray>your rank for more homes.</gray>"),
                GravenTheme.parse("<gray>Use Next and Back to flip pages.</gray>")
        ));
        gui.setItem(16, limits, null);

        ItemStack back = namedItem(Material.ARROW, "<green><bold>Back to Homes</bold></green>",
                List.of(GravenTheme.parse("<gray>Return to the homes menu.</gray>")));
        gui.setItem(22, back, event ->
                Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(viewer, target, returnPage), 1L));

        gui.fillBackground(decorativePane());
        gui.open(viewer);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    private ItemStack decorativePane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(GravenTheme.parse(""));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack namedItem(Material material, String displayName, List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(GravenTheme.parse(displayName));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openConfirmDeleteGui(@NotNull Player player, @NotNull String homeName, int returnPage) {
        CustomGUI gui = new CustomGUI(27, GravenTheme.parse("<dark_red>CONFIRM DELETE</dark_red>"));

        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(GravenTheme.parse("<red><bold>CANCEL</bold></red>"));
            cancel.setItemMeta(cancelMeta);
        }

        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cMeta = confirm.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(GravenTheme.parse("<green><bold>CONFIRM DELETE</bold></green>"));
            confirm.setItemMeta(cMeta);
        }

        ItemStack homeDisplay = new ItemStack(Material.BLUE_BED);
        ItemMeta hMeta = homeDisplay.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(GravenTheme.parse("<yellow>Home: " + homeName + "</yellow>"));
            hMeta.lore(List.of(GravenTheme.parse("<gray>Clicking confirm will delete this home permanently.</gray>")));
            homeDisplay.setItemMeta(hMeta);
        }

        gui.setItem(11, cancel, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(player, player, returnPage), 1L);
        });

        gui.setItem(13, homeDisplay, null);

        gui.setItem(15, confirm, event -> {
            homeManager.deleteHome(player.getUniqueId(), homeName);
            player.sendMessage(GravenTheme.parse("<yellow>Deleted home '" + homeName + "'.</yellow>"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(player, player, returnPage), 1L);
        });

        gui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 0.9f);
    }

    private @Nullable String getHomeNameByIndex(Map<String, Location> homes, int index) {
        int current = 0;
        for (String name : homes.keySet()) {
            if (current == index) return name;
            current++;
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        String cmd = command.getName().toLowerCase();
        if (sender instanceof Player player) {
            if (cmd.equals("home") || cmd.equals("delhome")) {
                if (args.length == 1) {
                    return new ArrayList<>(homeManager.getHomes(player.getUniqueId()).keySet());
                }
            }
        }
        if (cmd.equals("checkhomes") && sender.hasPermission("gravehomes.admin")) {
            if (args.length == 1) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return names;
            }
        }
        return Collections.emptyList();
    }
}