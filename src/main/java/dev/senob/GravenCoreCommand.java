package dev.senob;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GravenCoreCommand implements CommandExecutor, TabCompleter {

    private final GravenCore plugin;
    private final RedlineSpear redlineSpear;
    private final DraculaPlate draculaPlate;
    private final MythicalArmorSet mythicalArmorSet;

    public GravenCoreCommand(GravenCore plugin, RedlineSpear redlineSpear, DraculaPlate draculaPlate, MythicalArmorSet mythicalArmorSet) {
        this.plugin = plugin;
        this.redlineSpear = redlineSpear;
        this.draculaPlate = draculaPlate;
        this.mythicalArmorSet = mythicalArmorSet;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("gravencore.admin")) {
            sender.sendMessage(GravenTheme.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore <give|config|reload></red>"));
            return true;
        }

        // --- RELOAD LOGIC ---
        if (args[0].equalsIgnoreCase("reload")) {
            // Update the RAM cache
            GravenConfig.reload();

            NamespacedKey redlineKey = new NamespacedKey(plugin, "redline_tier");
            NamespacedKey draculaKey = new NamespacedKey(plugin, "dracula_plate");
            NamespacedKey mythicalKey = new NamespacedKey(plugin, "mythical_armor");

            // Loop through all online players to instantly update their held weapons
            for (Player online : Bukkit.getOnlinePlayers()) {
                for (ItemStack item : online.getInventory().getContents()) {
                    if (item == null || !item.hasItemMeta()) continue;
                    PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

                    if (pdc.has(redlineKey, PersistentDataType.INTEGER)) {
                        redlineSpear.syncSpearStatsAndLore(item, pdc.getOrDefault(redlineKey, PersistentDataType.INTEGER, 1));
                    } else if (pdc.has(draculaKey, PersistentDataType.INTEGER)) {
                        draculaPlate.syncPlateStatsAndLore(item);
                    } else if (pdc.has(mythicalKey, PersistentDataType.STRING)) {
                        mythicalArmorSet.syncPieceStatsAndLore(item, pdc.get(mythicalKey, PersistentDataType.STRING));
                    }
                }
            }
            sender.sendMessage(GravenTheme.parse(GravenTheme.prefix() + "<green>Config reloaded and all active items updated!</green>"));
            return true;
        }

        // --- GIVE LOGIC ---
        if (args[0].equalsIgnoreCase("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(GravenTheme.parse("<red>Only players can receive items.</red>"));
                return true;
            }

            if (args.length >= 2) {
                if (args[1].equalsIgnoreCase("redline")) {
                    int tier = (args.length > 2 && args[2].equalsIgnoreCase("base")) ? 1 : 2;
                    player.getInventory().addItem(redlineSpear.createRedlineSpear(tier));
                    String tierLabel = (tier == 2) ? "Upgraded Redline" : "Base Redline";
                    player.sendMessage(GravenTheme.parse("<gray>Received the </gray><color:#FF4B2B><italic>" + tierLabel + "</italic></color><gray> spear!</gray>"));
                    return true;
                }
                else if (args[1].equalsIgnoreCase("dracula")) {
                    player.getInventory().addItem(draculaPlate.createDraculaPlate());
                    player.sendMessage(GravenTheme.parse("<gray>Received </gray><color:#AA0000><italic>Dracula's Plate</italic></color><gray>!</gray>"));
                    return true;
                }
                else if (args[1].equalsIgnoreCase("armor")) {
                    // Armor tiers: more coming with the full tier system (only "mythical" for now)
                    if (args.length < 3 || !args[2].equalsIgnoreCase("mythical")) {
                        player.sendMessage(GravenTheme.parse("<red>Usage: /gravencore give armor <tier></red>"));
                        return true;
                    }
                    String pieceArg = (args.length > 3) ? args[3].toLowerCase(Locale.ROOT) : "set";
                    if (pieceArg.equals("set")) {
                        for (MythicalArmorSet.Piece piece : MythicalArmorSet.Piece.values()) {
                            player.getInventory().addItem(mythicalArmorSet.createPiece(piece));
                        }
                        player.sendMessage(GravenTheme.parse("<gray>Received the full </gray><color:#F8F8A0><italic>Mythical</italic></color><gray> armor set!</gray>"));
                    } else {
                        MythicalArmorSet.Piece piece = MythicalArmorSet.Piece.fromId(pieceArg);
                        if (piece == null) {
                            player.sendMessage(GravenTheme.parse("<red>Usage: /gravencore give armor mythical <helmet|chestplate|leggings|boots|set></red>"));
                            return true;
                        }
                        player.getInventory().addItem(mythicalArmorSet.createPiece(piece));
                        player.sendMessage(GravenTheme.parse("<gray>Received </gray><color:#F8F8A0><italic>Mythical " + pieceArg + "</italic></color><gray>!</gray>"));
                    }
                    return true;
                }
            }
            sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore give <redline|dracula|armor></red>"));
            return true;
        }

        // --- CONFIG LOGIC ---
        if (args[0].equalsIgnoreCase("config")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(GravenTheme.parse("<red>Only players can modify held items.</red>"));
                return true;
            }

            if (args.length >= 4) {
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem.getType() == Material.AIR || !heldItem.hasItemMeta()) {
                    player.sendMessage(GravenTheme.parse("<red>You must hold the custom item to configure it!</red>"));
                    return true;
                }

                ItemMeta meta = heldItem.getItemMeta();
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String setting = args[2].toLowerCase(Locale.ROOT);
                String valueStr = args[3];

                if (args[1].equalsIgnoreCase("redline")) {
                    NamespacedKey redlineKey = new NamespacedKey(plugin, "redline_tier");
                    if (!pdc.has(redlineKey, PersistentDataType.INTEGER)) {
                        player.sendMessage(GravenTheme.parse("<red>You must be holding a Redline Spear!</red>"));
                        return true;
                    }
                    int tier = pdc.getOrDefault(redlineKey, PersistentDataType.INTEGER, 1);

                    try {
                        switch (setting) {
                            case "cooldown" -> pdc.set(new NamespacedKey(plugin, "redline_cooldown"), PersistentDataType.LONG, Long.parseLong(valueStr));
                            case "range" -> pdc.set(new NamespacedKey(plugin, "redline_range"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "basedamage" -> pdc.set(new NamespacedKey(plugin, "redline_melee_damage"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "abilitydamage" -> pdc.set(new NamespacedKey(plugin, "redline_ability_damage"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            default -> {
                                player.sendMessage(GravenTheme.parse("<red>Valid Redline settings: cooldown, range, basedamage, abilitydamage</red>"));
                                return true;
                            }
                        }
                        heldItem.setItemMeta(meta);
                        redlineSpear.syncSpearStatsAndLore(heldItem, tier);
                        player.sendMessage(GravenTheme.parse("<green>Updated Redline " + setting + " to " + valueStr + "!</green>"));
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(GravenTheme.parse("<red>Invalid number format.</red>"));
                        return true;
                    }
                }
                else if (args[1].equalsIgnoreCase("dracula")) {
                    NamespacedKey draculaKey = new NamespacedKey(plugin, "dracula_plate");
                    if (!pdc.has(draculaKey, PersistentDataType.INTEGER)) {
                        player.sendMessage(GravenTheme.parse("<red>You must be holding Dracula's Plate!</red>"));
                        return true;
                    }

                    try {
                        switch (setting) {
                            case "cooldown" -> pdc.set(new NamespacedKey(plugin, "dracula_cooldown"), PersistentDataType.LONG, Long.parseLong(valueStr));
                            case "duration" -> pdc.set(new NamespacedKey(plugin, "dracula_duration"), PersistentDataType.LONG, Long.parseLong(valueStr));
                            default -> {
                                player.sendMessage(GravenTheme.parse("<red>Valid Dracula settings: cooldown, duration</red>"));
                                return true;
                            }
                        }
                        heldItem.setItemMeta(meta);
                        draculaPlate.syncPlateStatsAndLore(heldItem);
                        player.sendMessage(GravenTheme.parse("<green>Updated Dracula " + setting + " to " + valueStr + "!</green>"));
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(GravenTheme.parse("<red>Invalid number format.</red>"));
                        return true;
                    }
                }
                else if (args[1].equalsIgnoreCase("mythical")) {
                    NamespacedKey mythicalKey = new NamespacedKey(plugin, "mythical_armor");
                    if (!pdc.has(mythicalKey, PersistentDataType.STRING)) {
                        player.sendMessage(GravenTheme.parse("<red>You must be holding a Mythical armor piece!</red>"));
                        return true;
                    }

                    try {
                        switch (setting) {
                            case "defense" -> pdc.set(new NamespacedKey(plugin, "mythical_defense"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "toughness" -> pdc.set(new NamespacedKey(plugin, "mythical_toughness"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "kbresist" -> pdc.set(new NamespacedKey(plugin, "mythical_kbresist"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            default -> {
                                player.sendMessage(GravenTheme.parse("<red>Valid Mythical settings: defense, toughness, kbresist</red>"));
                                return true;
                            }
                        }
                        heldItem.setItemMeta(meta);
                        mythicalArmorSet.syncPieceStatsAndLore(heldItem, pdc.get(mythicalKey, PersistentDataType.STRING));
                        player.sendMessage(GravenTheme.parse("<green>Updated Mythical " + setting + " to " + valueStr + "!</green>"));
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(GravenTheme.parse("<red>Invalid number format.</red>"));
                        return true;
                    }
                }
            }
            sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore config <redline|dracula|mythical> <setting> <value></red>"));
            return true;
        }

        sender.sendMessage(GravenTheme.parse("<red>Unknown GravenCore command.</red>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) return List.of("give", "config", "reload");

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("config")) {
                return List.of("redline", "dracula", "armor");
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("redline")) {
                return List.of("base", "upgraded");
            }
            if (args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("armor")) {
                return List.of("mythical");
            }
            if (args[0].equalsIgnoreCase("config")) {
                if (args[1].equalsIgnoreCase("redline")) return List.of("cooldown", "range", "basedamage", "abilitydamage");
                if (args[1].equalsIgnoreCase("dracula")) return List.of("cooldown", "duration");
                if (args[1].equalsIgnoreCase("mythical")) return List.of("defense", "toughness", "kbresist");
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("armor")
                    && args[2].equalsIgnoreCase("mythical")) {
                return List.of("helmet", "chestplate", "leggings", "boots", "set");
            }
        }
        return Collections.emptyList();
    }
}