package dev.senob.commands;

import dev.senob.GravenCore;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import dev.senob.items.AlterOfDespair;
import dev.senob.items.DraculaPlate;
import dev.senob.items.ItemTier;
import dev.senob.items.TieredArmorSet;
import dev.senob.items.CelerityBoots;
import dev.senob.items.RedlineSpear;
import net.kyori.adventure.text.Component;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GravenCoreCommand implements CommandExecutor, TabCompleter {

    private final GravenCore plugin;
    private final RedlineSpear redlineSpear;
    private final DraculaPlate draculaPlate;
    private final CelerityBoots celerityBoots;
    private final AlterOfDespair alterOfDespair;
    private final TieredArmorSet tieredArmorSet;

    public GravenCoreCommand(GravenCore plugin, RedlineSpear redlineSpear, DraculaPlate draculaPlate, CelerityBoots celerityBoots, AlterOfDespair alterOfDespair, TieredArmorSet tieredArmorSet) {
        this.plugin = plugin;
        this.redlineSpear = redlineSpear;
        this.draculaPlate = draculaPlate;
        this.celerityBoots = celerityBoots;
        this.alterOfDespair = alterOfDespair;
        this.tieredArmorSet = tieredArmorSet;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("gravencore.admin")) {
            sender.sendMessage(GravenTheme.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore <armor|weapon|config|reload|packreload|alter></red>"));
            return true;
        }

        // --- ALTER LOGIC ---
        // /gravencore alter <username> [count]
        if (args[0].equalsIgnoreCase("alter")) {
            if (args.length < 2) {
                sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore alter <username> [count]</red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(GravenTheme.parse("<red>Player '" + args[1] + "' is not online.</red>"));
                return true;
            }
            int count = 1;
            if (args.length >= 3) {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore alter <username> [count]</red>"));
                    return true;
                }
            }
            count = Math.max(1, Math.min(64, count));
            Map<Integer, ItemStack> leftover =
                    target.getInventory().addItem(alterOfDespair.createAlterItem(count));
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            sender.sendMessage(GravenTheme.parse("<green>You have given <white>" + target.getName()
                    + "</white> a <color:#F8F854>Alter of Despair</color> x" + count + ".</green>"));
            target.sendMessage(GravenTheme.parse("<green>You have received a <color:#F8F854>Alter of Despair</color> x" + count + ".</green>"));
            return true;
        }

        // --- ARMOR LOGIC ---
        if (args[0].equalsIgnoreCase("armor")) {
            if (args.length < 3) {
                sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore armor <username> <tier></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(GravenTheme.parse("<red>Player '" + args[1] + "' is not online.</red>"));
                return true;
            }
            String tierArg = args[2].toLowerCase(Locale.ROOT);
            if (TieredArmorSet.Tier.fromId(tierArg) == null) {
                sender.sendMessage(GravenTheme.parse("<red>Unknown armor tier. Available: mythical, legendary, epic, rare, uncommon, common</red>"));
                return true;
            }

            List<ItemStack> pieces = new ArrayList<>();
            for (String pieceId : TieredArmorSet.PIECE_IDS) {
                pieces.add(tieredArmorSet.createPiece(tierArg, pieceId));
            }
            Map<Integer, ItemStack> leftover =
                    target.getInventory().addItem(pieces.toArray(new ItemStack[0]));
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }

            ItemTier tier = ItemTier.fromId(tierArg);
            String tierLabel = tier.getDisplayName();
            String tierColor = "#" + String.format("%06X", tier.getColor().value());
            sender.sendMessage(GravenTheme.parse("<green>You have given <white>" + target.getName()
                    + "</white> a <color:" + tierColor + ">" + tierLabel + "</color> armor set.</green>"));
            target.sendMessage(GravenTheme.parse("<green>You have received <color:" + tierColor + ">" + tierLabel
                    + "</color> armor set.</green>"));
            return true;
        }

        // --- WEAPON LOGIC ---
        // New weapons plug in here: add a case + label/color below, and extend
        // the tab completion + "Unknown weapon" list.
        if (args[0].equalsIgnoreCase("weapon")) {
            if (args.length < 3) {
                sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore weapon <username> <weapon_name></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(GravenTheme.parse("<red>Player '" + args[1] + "' is not online.</red>"));
                return true;
            }
            String weaponArg = args[2].toLowerCase(Locale.ROOT);
            ItemStack weaponItem;
            String weaponLabel;
            String weaponColor;
            switch (weaponArg) {
                case "redline" -> {
                    int tier = (args.length > 3 && args[3].equalsIgnoreCase("base")) ? 1 : 2;
                    weaponItem = redlineSpear.createRedlineSpear(tier);
                    weaponLabel = (tier == 2) ? "Upgraded Redline Spear" : "Base Redline Spear";
                    weaponColor = "#FF4B2B";
                }
                case "dracula" -> {
                    weaponItem = draculaPlate.createDraculaPlate();
                    weaponLabel = "Dracula's Plate";
                    weaponColor = "#AA0000";
                }
                case "celerity" -> {
                    weaponItem = celerityBoots.createMythicalBoots();
                    weaponLabel = "Celerity Boots";
                    weaponColor = "#F8F8BC";
                }
                default -> {
                    sender.sendMessage(GravenTheme.parse("<red>Unknown weapon. Available: redline, dracula, celerity</red>"));
                    return true;
                }
            }

            Map<Integer, ItemStack> leftover = target.getInventory().addItem(weaponItem);
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }

            sender.sendMessage(GravenTheme.parse("<green>You have given <white>" + target.getName()
                    + "</white> a <color:" + weaponColor + "><italic>" + weaponLabel + "</italic></color>.</green>"));
            target.sendMessage(GravenTheme.parse("<green>You have received a <color:" + weaponColor + "><italic>"
                    + weaponLabel + "</italic></color>.</green>"));
            return true;
        }

        // --- PACK RELOAD LOGIC ---
        if (args[0].equalsIgnoreCase("packreload")) {
            String url = GravenConfig.RESOURCE_PACK_URL;
            String sha1 = GravenConfig.RESOURCE_PACK_SHA1;
            if (url == null || url.isBlank() || sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) {
                sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse(
                        "<red>Resource pack isn't configured — set resource-pack.url and a valid 40-character resource-pack.sha1 in the config, then /gravencore reload.</red>")));
                return true;
            }
            Component prompt = (GravenConfig.RESOURCE_PACK_PROMPT == null || GravenConfig.RESOURCE_PACK_PROMPT.isBlank())
                    ? null : GravenTheme.parse(GravenConfig.RESOURCE_PACK_PROMPT);
            byte[] hash = HexFormat.of().parseHex(sha1);
            int sent = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.setResourcePack(url, hash, prompt, GravenConfig.RESOURCE_PACK_REQUIRED);
                sent++;
            }
            sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse(
                    "<green>Resource pack sent to " + sent + " player(s) — they'll download and apply it now.</green>")));
            return true;
        }

        // --- RELOAD LOGIC ---
        if (args[0].equalsIgnoreCase("reload")) {
            // Update the RAM cache
            GravenConfig.reload();
            // TAB names + visibility follow the reloaded toggles at once
            dev.senob.listeners.NameObfuscationListener.refreshAll();
            // Vanish boss bars pick up a reloaded vanished-name at once
            VanishCommand.refreshBossBarTitles();

            NamespacedKey redlineKey = new NamespacedKey(plugin, "redline_tier");
            NamespacedKey draculaKey = new NamespacedKey(plugin, "dracula_plate");
            NamespacedKey bootsKey = new NamespacedKey(plugin, "mythical_boots");

            // Loop through all online players to instantly update their held weapons
            // (inventory + worn armor — getContents() skips armor slots)
            for (Player online : Bukkit.getOnlinePlayers()) {
                List<ItemStack> allItems = new ArrayList<>();
                for (ItemStack item : online.getInventory().getContents()) allItems.add(item);
                for (ItemStack item : online.getInventory().getArmorContents()) allItems.add(item);
                for (ItemStack item : allItems) {
                    if (item == null || !item.hasItemMeta()) continue;
                    PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

                    if (pdc.has(redlineKey, PersistentDataType.INTEGER)) {
                        redlineSpear.syncSpearStatsAndLore(item, pdc.getOrDefault(redlineKey, PersistentDataType.INTEGER, 1));
                    } else if (pdc.has(draculaKey, PersistentDataType.INTEGER)) {
                        draculaPlate.syncPlateStatsAndLore(item);
                    } else if (tieredArmorSet.isItem(item)) {
                        tieredArmorSet.syncPieceStatsAndLore(item);
                    } else if (pdc.has(bootsKey, PersistentDataType.INTEGER)) {
                        celerityBoots.syncBootsStatsAndLore(item);
                    }
                }
            }
            sender.sendMessage(GravenTheme.prefix().append(GravenTheme.parse("<green>Config reloaded and all active items updated!</green>")));
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
                            case "blockrate" -> pdc.set(new NamespacedKey(plugin, "dracula_blockrate"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "blockpower" -> pdc.set(new NamespacedKey(plugin, "dracula_blockpower"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            default -> {
                                player.sendMessage(GravenTheme.parse("<red>Valid Dracula settings: cooldown, duration, blockrate, blockpower</red>"));
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
                else if (TieredArmorSet.Tier.fromId(args[1].toLowerCase(Locale.ROOT)) != null) {
                    String tierId = args[1].toLowerCase(Locale.ROOT);
                    String tierLabel = ItemTier.fromId(tierId).getDisplayName();
                    if (!tierId.equals(tieredArmorSet.getTierId(heldItem))) {
                        player.sendMessage(GravenTheme.parse("<red>You must be holding a " + tierLabel + " armor piece!</red>"));
                        return true;
                    }

                    try {
                        switch (setting) {
                            case "defense" -> pdc.set(new NamespacedKey(plugin, tierId + "_defense"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "toughness" -> pdc.set(new NamespacedKey(plugin, tierId + "_toughness"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "kbresist" -> pdc.set(new NamespacedKey(plugin, tierId + "_kbresist"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "blockrate" -> pdc.set(new NamespacedKey(plugin, tierId + "_blockrate"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "blockpower" -> pdc.set(new NamespacedKey(plugin, tierId + "_blockpower"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            default -> {
                                player.sendMessage(GravenTheme.parse("<red>Valid " + tierLabel + " settings: defense, toughness, kbresist, blockrate, blockpower</red>"));
                                return true;
                            }
                        }
                        heldItem.setItemMeta(meta);
                        tieredArmorSet.syncPieceStatsAndLore(heldItem);
                        player.sendMessage(GravenTheme.parse("<green>Updated " + tierLabel + " " + setting + " to " + valueStr + "!</green>"));
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(GravenTheme.parse("<red>Invalid number format.</red>"));
                        return true;
                    }
                }
                else if (args[1].equalsIgnoreCase("time_boots")) {
                    NamespacedKey bootsKey = new NamespacedKey(plugin, "mythical_boots");
                    if (!pdc.has(bootsKey, PersistentDataType.INTEGER)) {
                        player.sendMessage(GravenTheme.parse("<red>You must be holding the Celerity Boots!</red>"));
                        return true;
                    }

                    try {
                        switch (setting) {
                            case "duration" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_duration"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "cooldown" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_cooldown"), PersistentDataType.LONG, Long.parseLong(valueStr));
                            case "speedboost" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_speedboost"), PersistentDataType.INTEGER, Integer.parseInt(valueStr));
                            case "freezeplayers" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_freezeplayers"), PersistentDataType.BOOLEAN, Boolean.parseBoolean(valueStr));
                            case "weaknesslevel" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_weakness"), PersistentDataType.INTEGER, Integer.parseInt(valueStr));
                            case "blockrate" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_blockrate"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            case "blockpower" -> pdc.set(new NamespacedKey(plugin, "mythical_boots_blockpower"), PersistentDataType.DOUBLE, Double.parseDouble(valueStr));
                            default -> {
                                player.sendMessage(GravenTheme.parse("<red>Valid Time Boots settings: duration, cooldown, speedboost, freezeplayers, weaknesslevel, blockrate, blockpower</red>"));
                                return true;
                            }
                        }
                        heldItem.setItemMeta(meta);
                        celerityBoots.syncBootsStatsAndLore(heldItem);
                        player.sendMessage(GravenTheme.parse("<green>Updated Time Boots " + setting + " to " + valueStr + "!</green>"));
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(GravenTheme.parse("<red>Invalid number format.</red>"));
                        return true;
                    }
                }
            }
            sender.sendMessage(GravenTheme.parse("<red>Usage: /gravencore config <dracula|mythical|legendary|epic|rare|uncommon|common|redline|time_boots> <setting> <value></red>"));
            return true;
        }

        sender.sendMessage(GravenTheme.parse("<red>Unknown GravenCore command.</red>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) return List.of("armor", "weapon", "config", "reload", "packreload", "alter");

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("armor") || args[0].equalsIgnoreCase("weapon") || args[0].equalsIgnoreCase("alter")) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                return names;
            }
            if (args[0].equalsIgnoreCase("config")) {
                return List.of("redline", "dracula", "mythical", "legendary", "epic", "rare", "uncommon", "common", "time_boots");
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("alter")) {
                return List.of("1", "2", "4", "8", "16", "32", "64");
            }
            if (args[0].equalsIgnoreCase("armor")) {
                return List.of("mythical", "legendary", "epic", "rare", "uncommon", "common");
            }
            if (args[0].equalsIgnoreCase("weapon")) {
                return List.of("redline", "dracula", "celerity");
            }
            if (args[0].equalsIgnoreCase("config")) {
                if (args[1].equalsIgnoreCase("redline")) return List.of("cooldown", "range", "basedamage", "abilitydamage");
                if (args[1].equalsIgnoreCase("dracula")) return List.of("cooldown", "duration", "blockrate", "blockpower");
                if (TieredArmorSet.Tier.fromId(args[1].toLowerCase(Locale.ROOT)) != null) return List.of("defense", "toughness", "kbresist", "blockrate", "blockpower");
                if (args[1].equalsIgnoreCase("time_boots")) return List.of("duration", "cooldown", "speedboost", "freezeplayers", "weaknesslevel", "blockrate", "blockpower");
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("weapon") && args[2].equalsIgnoreCase("redline")) {
                return List.of("base", "upgraded");
            }
        }

        return Collections.emptyList();
    }
}