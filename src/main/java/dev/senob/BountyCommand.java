package dev.senob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private static final int BOOK_PAGE_WIDTH = 114;
    private static final int MAX_REASON_WORDS = 1000;

    private static final int[] SAMPLE_X = {0, 2, 3, 4, 5, 7};
    private static final int[] SAMPLE_Y = {0, 2, 3, 4, 6, 7};

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(GravenTheme.parse("<red>Only players can issue bounties.</red>"));
            return true;
        }

        if (!player.hasPermission("gravehomes.member")) {
            player.sendMessage(GravenTheme.parse("<red>You do not have permission to issue bounties.</red>"));
            return true;
        }

        // Syntax: /bounty add <username> <reason>
        if (args.length < 3 || !args[0].equalsIgnoreCase("add")) {
            player.sendMessage(GravenTheme.parse("<red>Usage: /bounty add <username> <reason></red>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(GravenTheme.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }

        // Parse required <reason> starting from args[2] and enforce 1,000-word limit
        String[] reasonWords = Arrays.copyOfRange(args, 2, args.length);
        if (reasonWords.length > MAX_REASON_WORDS) {
            player.sendMessage(GravenTheme.parse("<red>Bounty reason cannot exceed " + MAX_REASON_WORDS + " words! (Current: " + reasonWords.length + ")</red>"));
            return true;
        }
        String reason = String.join(" ", reasonWords);

        String issuerName = player.getName();
        String targetName = target.getName();
        Location targetLoc = target.getLocation();
        URL skinUrl = target.getPlayerProfile().getTextures().getSkin();
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(BountyCommand.class);

        CompletableFuture.supplyAsync(() -> buildPixelHead(skinUrl, targetName))
                .thenAccept(headComponent -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    ItemStack bountyBook = createBountyBook(issuerName, targetName, targetLoc, reason, headComponent);

                    if (!player.getInventory().addItem(bountyBook).isEmpty()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), bountyBook);
                    }

                    player.sendMessage(GravenTheme.parse("<green>Created a bounty book for <yellow>" + targetName + "</yellow>!</green>"));
                }));

        return true;
    }

    private ItemStack createBountyBook(String issuerName, String targetName, Location targetLoc, String reason, Component headComponent) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta != null) {
            meta.setTitle("Bounty " + targetName);
            meta.setAuthor(issuerName);

            String wantedLine = "WANTED";
            String deadOrAliveLine = "DEAD OR ALIVE";
            String locationHeader = "Last Location";
            String coordsLine = targetLoc.getBlockX() + "  " + targetLoc.getBlockZ();

            Component pageOne = Component.text()
                    .append(Component.text(getCenterPadding(wantedLine, true)))
                    .append(Component.text(wantedLine + "\n", NamedTextColor.BLACK, TextDecoration.BOLD))
                    .append(Component.text(getCenterPadding(deadOrAliveLine, true)))
                    .append(Component.text(deadOrAliveLine + "\n", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text(getCenterPadding(targetName, false)))
                    .append(Component.text(targetName + "\n\n", NamedTextColor.BLACK, TextDecoration.ITALIC))
                    .append(headComponent)
                    .append(Component.text("\n\n")) // Extra line break to replace the removed reward text
                    .append(Component.text(getCenterPadding(locationHeader, false)))
                    .append(Component.text(locationHeader + "\n", NamedTextColor.BLACK))
                    .append(Component.text(getCenterPadding(coordsLine, true)))
                    .append(Component.text(coordsLine, NamedTextColor.BLACK, TextDecoration.BOLD))
                    .build();

            meta.addPages(pageOne);

            List<Component> reasonPages = buildReasonPages(reason);
            for (Component reasonPage : reasonPages) {
                meta.addPages(reasonPage);
            }

            book.setItemMeta(meta);
        }

        return book;
    }

    private List<Component> buildReasonPages(String reason) {
        List<Component> pages = new ArrayList<>();
        String header = "BOUNTY REASON";
        String headerPadding = getCenterPadding(header, true);

        String[] words = reason.split("\\s+");
        StringBuilder currentChunk = new StringBuilder();
        boolean isFirstPage = true;
        int maxCharsPerPage = 190;

        for (String word : words) {
            if (currentChunk.length() + word.length() + 1 > maxCharsPerPage && !currentChunk.isEmpty()) {
                pages.add(formatReasonPage(currentChunk.toString().trim(), isFirstPage, header, headerPadding));
                currentChunk.setLength(0);
                isFirstPage = false;
                maxCharsPerPage = 230;
            }
            if (!currentChunk.isEmpty()) {
                currentChunk.append(" ");
            }
            currentChunk.append(word);
        }

        if (!currentChunk.isEmpty() || pages.isEmpty()) {
            pages.add(formatReasonPage(currentChunk.toString().trim(), isFirstPage, header, headerPadding));
        }

        return pages;
    }

    private Component formatReasonPage(String bodyText, boolean includeHeader, String header, String headerPadding) {
        TextComponent.Builder builder = Component.text();
        if (includeHeader) {
            builder.append(Component.text(headerPadding))
                    .append(Component.text(header + "\n\n", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
        builder.append(Component.text(bodyText, NamedTextColor.BLACK));
        return builder.build();
    }

    private Component buildPixelHead(URL skinUrl, String targetName) {
        try {
            BufferedImage image = null;
            boolean isFullSkin = false;

            if (skinUrl != null) {
                image = ImageIO.read(skinUrl);
                isFullSkin = true;
            }

            if (image == null) {
                URL fallbackUrl = URI.create("https://minotar.net/helm/" + targetName + "/8.png").toURL();
                HttpURLConnection conn = (HttpURLConnection) fallbackUrl.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                try (InputStream in = conn.getInputStream()) {
                    image = ImageIO.read(in);
                }
            }

            if (image != null) {
                // 6 blocks of '█' (9px each = 54px) centered on a 114px page -> 7 spaces (28px)
                String headPadding = "       ";
                TextComponent.Builder grid = Component.text();
                for (int sy : SAMPLE_Y) {
                    grid.append(Component.text(headPadding));
                    for (int sx : SAMPLE_X) {
                        int rgb = isFullSkin ? getBlendedFacePixel(image, sx, sy) : image.getRGB(sx, sy);
                        TextColor pixelColor = TextColor.color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                        grid.append(Component.text("█", pixelColor));
                    }
                    grid.append(Component.text("\n"));
                }
                return grid.build();
            }
        } catch (Exception ignored) {
        }

        String noImg = "[No Image]";
        return Component.text("\n\n" + getCenterPadding(noImg, false) + noImg + "\n\n\n\n", NamedTextColor.GRAY);
    }

    private int getBlendedFacePixel(BufferedImage skin, int x, int y) {
        int base = skin.getRGB(8 + x, 8 + y);
        int hat = skin.getRGB(40 + x, 8 + y);
        int hatAlpha = (hat >> 24) & 0xFF;

        if (hatAlpha == 0) return base;
        if (hatAlpha == 255) return hat;

        float alpha = hatAlpha / 255.0f;
        int r = Math.round(((hat >> 16) & 0xFF) * alpha + ((base >> 16) & 0xFF) * (1 - alpha));
        int g = Math.round(((hat >> 8) & 0xFF) * alpha + ((base >> 8) & 0xFF) * (1 - alpha));
        int b = Math.round((hat & 0xFF) * alpha + (base & 0xFF) * (1 - alpha));

        return (r << 16) | (g << 8) | b;
    }

    private String getCenterPadding(String text, boolean bold) {
        return getPaddingFromPixels(getPixelWidth(text, bold));
    }

    private String getPaddingFromPixels(int textPixels) {
        int leftPixels = Math.max(0, (BOOK_PAGE_WIDTH - textPixels) / 2);
        int spaces = Math.round(leftPixels / 4.0f);
        return " ".repeat(spaces);
    }

    private int getPixelWidth(String text, boolean bold) {
        int width = 0;
        for (char c : text.toCharArray()) {
            int charWidth = switch (c) {
                case 'i', '!', ':', ';', '|', '.', ',', '\'' -> 2;
                case 'l', '`' -> 3;
                case 'I', 't', ' ', '[', ']', '"', '*' -> 4;
                case 'f', 'k', '<', '>', '(', ')', '{', '}' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
            if (bold && c != ' ') {
                charWidth += 1;
            }
            width += charWidth;
        }
        return width;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return Collections.singletonList("add");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return Collections.singletonList("<reason>");
        }
        return Collections.emptyList();
    }
}