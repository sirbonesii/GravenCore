package dev.senob;

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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"SpellCheckingInspection"})
public class HomeSystem implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final HomeManager homeManager;

    private static final int[] HOME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32
    };

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
        boolean isSelf = viewer.equals(target);
        String titleStr = isSelf ? "HOMES" : "HOMES: " + target.getName();

        CustomGUI gui = new CustomGUI(54, GravenTheme.parse("<dark_gray>" + titleStr + "</dark_gray>"));

        ItemStack infoBanner = new ItemStack(Material.RED_BANNER);
        ItemMeta bannerMeta = infoBanner.getItemMeta();
        if (bannerMeta != null) {
            bannerMeta.displayName(GravenTheme.parse("<gold><bold>" + target.getName() + "'s Homes</bold></gold>"));
            bannerMeta.lore(List.of(
                    GravenTheme.parse("<gray>Max Capacity: <yellow>" + homeManager.getMaxHomes(target) + "</yellow></gray>"),
                    GravenTheme.parse("<gray>Total Set: <yellow>" + homeManager.getHomes(target.getUniqueId()).size() + "</yellow></gray>")
            ));
            infoBanner.setItemMeta(bannerMeta);
        }
        gui.setItem(4, infoBanner, null);

        Map<String, Location> homes = homeManager.getHomes(target.getUniqueId());
        int maxSlots = homeManager.getMaxHomes(target);

        for (int i = 0; i < HOME_SLOTS.length; i++) {
            int slot = HOME_SLOTS[i];
            if (i < maxSlots) {
                String homeNameIndex = getHomeNameByIndex(homes, i);
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

                    gui.setItem(slot, bed, event -> {
                        if (isSelf) {
                            if (event.isRightClick()) {
                                openConfirmDeleteGui(viewer, homeNameIndex);
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
                        meta.displayName(GravenTheme.parse("<yellow><bold>NO HOME SET</bold></yellow>"));
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
                ItemStack locked = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = locked.getItemMeta();
                if (meta != null) {
                    meta.displayName(GravenTheme.parse("<red>LOCKED SLOT</red>"));
                    meta.lore(List.of(GravenTheme.parse("<gray>Upgrade your rank for more homes.</gray>")));
                    locked.setItemMeta(meta);
                }
                gui.setItem(slot, locked, null);
            }
        }

        gui.open(viewer);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    private void openConfirmDeleteGui(@NotNull Player player, @NotNull String homeName) {
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
            Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(player, player), 1L);
        });

        gui.setItem(13, homeDisplay, null);

        gui.setItem(15, confirm, event -> {
            homeManager.deleteHome(player.getUniqueId(), homeName);
            player.sendMessage(GravenTheme.parse("<yellow>Deleted home '" + homeName + "'.</yellow>"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> openHomesGui(player, player), 1L);
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