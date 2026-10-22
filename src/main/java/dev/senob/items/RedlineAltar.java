package dev.senob.items;

import dev.senob.GravenCore;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import dev.senob.guis.CustomGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redline Altar — upgrades a base Redline spear to the upgraded version.
 *
 * A dropper ringed by 8 lodestone pedestals is an altar. Right-clicking the
 * dropper opens the REDLINE bench GUI: placing the correct ingredient in a
 * slot locks it in (lime pane, and the item appears on the matching
 * pedestal); a wrong item flashes the slot red and is not taken. Locked
 * items are stored in the dropper itself and can be clicked back out. When
 * all 9 slots are locked, the ritual starts: red beams stream from the
 * pedestal items into the center spear, a beam shoots skyward, and the
 * upgraded spear drops at the center.
 *
 * Recipe layout:
 *   [dragon breath] [heavy core]   [dragon breath]
 *   [netherite]     [base spear]   [netherite]
 *   [dragon breath] [ghast tear]   [dragon breath]
 */
public class RedlineAltar implements Listener {

    private static final Material[] SLOT_MATERIALS = {
            Material.DRAGON_BREATH, Material.HEAVY_CORE, Material.DRAGON_BREATH,
            Material.NETHERITE_INGOT, null, Material.NETHERITE_INGOT,
            Material.DRAGON_BREATH, Material.GHAST_TEAR, Material.DRAGON_BREATH
    };
    /** Dropper slot for each compass sector (0=N, 1=NE, 2=E, ... 7=NW). */
    private static final int[] SLOT_BY_COMPASS = {1, 2, 5, 8, 7, 6, 3, 0};
    /** GUI slot for each recipe slot (3x3 centered in a 27-slot inventory). */
    private static final int[] GUI_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final Material PEDESTAL_BLOCK = Material.LODESTONE;
    /** Preview/ritual display key for the center spear (pedestals use compass 0-7). */
    private static final int CENTER_KEY = 8;

    private final GravenCore plugin;
    private final RedlineSpear redlineSpear;
    private final Set<Location> activeAltars = new HashSet<>();
    /** Live pedestal displays per altar center, keyed by compass sector / CENTER_KEY. */
    private final Map<Location, Map<Integer, ItemDisplay>> previews = new HashMap<>();
    private final Map<Location, List<Location>> pedestalCache = new HashMap<>();

    public RedlineAltar(GravenCore plugin, RedlineSpear redlineSpear) {
        this.plugin = plugin;
        this.redlineSpear = redlineSpear;
        plugin.getLogger().info("Redline Altar module initialized!");
    }

    // --- Bench GUI ---------------------------------------------------------------

    @EventHandler
    public void onBenchInteract(PlayerInteractEvent event) {
        if (!GravenConfig.REDLINE_ALTAR_ENABLED) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.DROPPER) return;
        Location center = block.getLocation();
        if (activeAltars.contains(center)) {
            event.setCancelled(true);
            return;
        }
        List<Location> pedestals = findPedestals(center);
        if (pedestals.isEmpty()) return; // not an altar: vanilla dropper behavior
        pedestalCache.put(center, pedestals);
        event.setCancelled(true);

        Player player = event.getPlayer();
        syncPreview(center);
        if (isRecipeComplete(center)) {
            startRitualFromBench(player, center);
            return;
        }
        openBench(player, center);
    }

    private void openBench(Player player, Location center) {
        CustomGUI gui = new CustomGUI(27, GravenTheme.parse("<dark_red><bold>REDLINE</bold></dark_red>"));
        gui.fillBackground(makePane(Material.BLACK_STAINED_GLASS_PANE, " "));
        for (int r = 0; r < 9; r++) {
            renderSlot(gui, center, r);
        }
        gui.open(player);
    }

    /** Renders a recipe slot as an empty (green), locked (lime), or stored-junk slot. */
    private void renderSlot(CustomGUI gui, Location center, int recipeSlot) {
        Inventory dropper = dropperInventory(center);
        ItemStack stored = (dropper != null) ? dropper.getItem(recipeSlot) : null;
        ItemStack display;
        if (stored == null) {
            display = makePane(Material.GREEN_STAINED_GLASS_PANE,
                    "<gray>Place: </gray><white>" + ingredientName(recipeSlot) + "</white>");
        } else if (isCorrectForSlot(recipeSlot, stored)) {
            display = makePane(Material.LIME_STAINED_GLASS_PANE,
                    "<green>✔ " + ingredientName(recipeSlot) + "</green>");
        } else {
            display = stored.clone(); // junk stored before it was an altar: show it, click to reclaim
        }
        final int slot = recipeSlot;
        gui.setItem(GUI_SLOTS[recipeSlot], display, event -> handleRecipeClick(event, center, gui, slot));
    }

    private void handleRecipeClick(InventoryClickEvent event, Location center, CustomGUI gui, int recipeSlot) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory dropper = dropperInventory(center);
        if (dropper == null) return;
        ItemStack stored = dropper.getItem(recipeSlot);
        ItemStack cursor = player.getItemOnCursor();

        if (stored != null) {
            // Occupied slot: empty hand takes the item back out
            if (cursor == null || cursor.getType() == Material.AIR) {
                dropper.setItem(recipeSlot, null);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stored);
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                renderSlot(gui, center, recipeSlot);
                syncPreview(center);
            }
            return;
        }

        if (cursor == null || cursor.getType() == Material.AIR) return;

        if (isCorrectForSlot(recipeSlot, cursor)) {
            // Lock one of the item into the bench (the rest stay on the cursor)
            ItemStack locked = cursor.clone();
            locked.setAmount(1);
            dropper.setItem(recipeSlot, locked);
            if (cursor.getAmount() <= 1) player.setItemOnCursor(null);
            else cursor.setAmount(cursor.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            renderSlot(gui, center, recipeSlot);
            syncPreview(center);
            if (isRecipeComplete(center)) {
                player.closeInventory();
                startRitualFromBench(player, center);
            }
        } else {
            // Wrong item: red flash, and the item is never taken
            if (recipeSlot == 4 && redlineSpear.getRedlineTier(cursor) >= 2) {
                player.sendMessage(GravenTheme.prefix().append(
                        GravenTheme.parse("<red>That Redline is already upgraded.</red>")));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            gui.setItem(GUI_SLOTS[recipeSlot],
                    makePane(Material.RED_STAINED_GLASS_PANE, "<red>✗ Wrong item</red>"),
                    click -> handleRecipeClick(click, center, gui, recipeSlot));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Inventory d = dropperInventory(center);
                if (d != null && d.getItem(recipeSlot) == null) {
                    renderSlot(gui, center, recipeSlot);
                }
            }, 20L);
        }
    }

    private static String ingredientName(int recipeSlot) {
        return switch (recipeSlot) {
            case 0, 2, 6, 8 -> "Dragon's Breath";
            case 1 -> "Heavy Core";
            case 3, 5 -> "Netherite Ingot";
            case 4 -> "Base Redline Spear";
            case 7 -> "Ghast Tear";
            default -> "?";
        };
    }

    private static ItemStack makePane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(GravenTheme.parse(name));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private boolean isCorrectForSlot(int recipeSlot, ItemStack item) {
        if (item == null) return false;
        if (recipeSlot == 4) return redlineSpear.getRedlineTier(item) == 1;
        return item.getType() == SLOT_MATERIALS[recipeSlot];
    }

    private boolean isRecipeComplete(Location center) {
        Inventory dropper = dropperInventory(center);
        if (dropper == null) return false;
        for (int r = 0; r < 9; r++) {
            if (!isCorrectForSlot(r, dropper.getItem(r))) return false;
        }
        return true;
    }

    // --- Pedestal preview ----------------------------------------------------------

    /** Mirrors the bench's stored items onto the pedestals around it. */
    private void syncPreview(Location center) {
        if (activeAltars.contains(center)) return;
        if (center.getBlock().getType() != Material.DROPPER) {
            clearPreviews(center);
            return;
        }
        List<Location> pedestals = pedestalCache.computeIfAbsent(center, this::findPedestals);
        if (pedestals.isEmpty()) return;

        Inventory inv = dropperInventory(center);
        if (inv == null) return;

        Map<Integer, ItemDisplay> shown = previews.computeIfAbsent(center, k -> new HashMap<>());
        World world = center.getWorld();
        if (world == null) return;

        for (int compass = 0; compass < 8; compass++) {
            ItemStack item = inv.getItem(SLOT_BY_COMPASS[compass]);
            Location at = pedestals.get(compass).clone().add(0.5, 1.7, 0.5);
            reconcile(shown, compass, at, item);
        }
        ItemStack spear = inv.getItem(4);
        ItemStack spearShown = (spear != null && redlineSpear.getRedlineTier(spear) >= 1) ? spear : null;
        reconcile(shown, CENTER_KEY, center.clone().add(0.5, 2.0, 0.5), spearShown);
    }

    /** Shows the item on a display, replacing or removing the display as needed. */
    private void reconcile(Map<Integer, ItemDisplay> shown, int key, Location at, ItemStack item) {
        ItemDisplay existing = shown.get(key);
        if (item == null || item.getType() == Material.AIR) {
            if (existing != null) {
                existing.remove();
                shown.remove(key);
            }
            return;
        }
        if (existing != null && existing.isValid() && item.isSimilar(existing.getItemStack())) return;
        if (existing != null) existing.remove();
        World world = at.getWorld();
        if (world == null) return;
        shown.put(key, spawnDisplay(world, at, item));
    }

    /** Spawns an item display at normal dropped-item size (0.5 scale). */
    private static ItemDisplay spawnDisplay(World world, Location at, ItemStack item) {
        ItemStack shownItem = item.clone();
        shownItem.setAmount(1);
        return world.spawn(at, ItemDisplay.class, disp -> {
            disp.setItemStack(shownItem);
            disp.setPersistent(false);
            disp.setBillboard(Display.Billboard.FIXED);
            disp.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
        });
    }

    private void clearPreviews(Location center) {
        Map<Integer, ItemDisplay> shown = previews.remove(center);
        if (shown == null) return;
        for (ItemDisplay display : shown.values()) {
            if (display.isValid()) display.remove();
        }
    }

    private Inventory dropperInventory(Location center) {
        if (center.getBlock().getState() instanceof InventoryHolder holder) {
            return holder.getInventory();
        }
        return null;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.DROPPER) {
            Location center = block.getLocation();
            clearPreviews(center);
            pedestalCache.remove(center);
        } else if (block.getType() == PEDESTAL_BLOCK) {
            Location broken = block.getLocation();
            for (Map.Entry<Location, List<Location>> entry : pedestalCache.entrySet()) {
                if (entry.getValue().contains(broken)) {
                    clearPreviews(entry.getKey());
                }
            }
        }
    }

    // --- Ritual ----------------------------------------------------------------------

    /** Consumes the completed recipe from the bench and starts the ritual. */
    private void startRitualFromBench(Player player, Location center) {
        Inventory dropper = dropperInventory(center);
        if (dropper == null) return;
        List<Location> pedestals = pedestalCache.getOrDefault(center, List.of());
        if (pedestals.isEmpty()) pedestals = findPedestals(center);
        if (pedestals.isEmpty()) {
            player.sendMessage(GravenTheme.prefix().append(
                    GravenTheme.parse("<red>The altar is incomplete — the dropper needs exactly 8 lodestone pedestals around it.</red>")));
            return;
        }

        // Snapshot the ring items in compass order so each ingredient appears
        // on the pedestal matching its recipe position
        List<ItemStack> ringItems = new ArrayList<>();
        for (int slot : SLOT_BY_COMPASS) ringItems.add(dropper.getItem(slot).clone());
        ItemStack spearItem = dropper.getItem(4).clone();
        dropper.clear();

        // The ritual takes over the live preview displays
        Map<Integer, ItemDisplay> adopted = previews.remove(center);
        pedestalCache.remove(center);
        startRitual(player, center, pedestals, ringItems, spearItem, adopted);
    }

    /**
     * Finds the 8 pedestals around the center: lodestones at the
     * same height, 2–6 blocks out, exactly one per compass sector.
     * Returns them sorted by compass sector, or empty if the ring is invalid.
     */
    private List<Location> findPedestals(Location center) {
        World world = center.getWorld();
        if (world == null) return List.of();
        Location[] byCompass = new Location[8];
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
                if (chebyshev < 2 || chebyshev > 6) continue;
                Block b = world.getBlockAt(center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                if (b.getType() != PEDESTAL_BLOCK) continue;
                int sector = compassIndex(dx, dz);
                if (byCompass[sector] != null) return List.of(); // two in one sector: ambiguous
                byCompass[sector] = b.getLocation();
            }
        }
        List<Location> pedestals = new ArrayList<>();
        for (Location pedestal : byCompass) {
            if (pedestal == null) return List.of();
            pedestals.add(pedestal);
        }
        return pedestals;
    }

    /** Compass sector for an offset: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW. */
    private static int compassIndex(int dx, int dz) {
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        return (int) Math.round(((deg + 90.0 + 360.0) % 360.0) / 45.0) % 8;
    }

    private void startRitual(Player player, Location center, List<Location> pedestals,
                             List<ItemStack> ringItems, ItemStack spearItem,
                             Map<Integer, ItemDisplay> adopted) {
        World world = center.getWorld();
        if (world == null) return;
        activeAltars.add(center);

        world.playSound(center, GravenConfig.REDLINE_ALTAR_START_SOUND,
                GravenConfig.REDLINE_ALTAR_START_VOLUME, GravenConfig.REDLINE_ALTAR_START_PITCH);
        if (player.isOnline()) {
            player.sendMessage(GravenTheme.prefix().append(
                    GravenTheme.parse("<aqua>The altar awakens — the ritual begins!</aqua>")));
        }

        // Adopt the live preview displays where they exist; spawn any missing ones
        List<ItemDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ItemDisplay display = (adopted != null) ? adopted.remove(i) : null;
            if (display == null || !display.isValid()) {
                Location at = pedestals.get(i).clone().add(0.5, 1.7, 0.5);
                display = spawnDisplay(world, at, ringItems.get(i));
            }
            displays.add(display);
        }
        ItemDisplay spearDisplay = (adopted != null) ? adopted.remove(CENTER_KEY) : null;
        if (spearDisplay == null || !spearDisplay.isValid()) {
            Location spearAt = center.clone().add(0.5, 2.0, 0.5);
            spearDisplay = spawnDisplay(world, spearAt, spearItem);
        }
        if (adopted != null) {
            for (ItemDisplay leftover : adopted.values()) leftover.remove();
        }
        final ItemDisplay centerDisplay = spearDisplay;

        long totalTicks = Math.max(20L, (long) (GravenConfig.REDLINE_ALTAR_RITUAL_DURATION * 20.0));
        new BukkitRunnable() {
            long elapsed = 0;
            float yaw = 0f;

            @Override
            public void run() {
                if (elapsed >= totalTicks) {
                    finishRitual(player, center, displays, centerDisplay);
                    cancel();
                    return;
                }
                yaw += 9f;
                Location spearLoc = centerDisplay.isValid() ? centerDisplay.getLocation() : center.clone().add(0.5, 2.0, 0.5);
                for (ItemDisplay display : displays) {
                    if (!display.isValid()) continue;
                    Location l = display.getLocation();
                    l.setYaw(yaw);
                    display.teleport(l);
                    world.spawnParticle(Particle.ENCHANT, display.getLocation(), 2, 0.2, 0.2, 0.2, 0.5);
                    drawBeam(world, display.getLocation(), spearLoc, elapsed);
                }
                if (centerDisplay.isValid()) {
                    Location l = centerDisplay.getLocation();
                    l.setYaw(yaw * 1.5f);
                    centerDisplay.teleport(l);
                }
                // Portal particles spiral inward as the ritual progresses
                double progress = (double) elapsed / totalTicks;
                double radius = 3.5 * (1.0 - progress) + 0.5;
                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2 * i / 8.0) + (elapsed * 0.08);
                    Location p = center.clone().add(0.5 + Math.cos(angle) * radius,
                            1.2 + progress * 1.2, 0.5 + Math.sin(angle) * radius);
                    world.spawnParticle(Particle.PORTAL, p, 2, 0.05, 0.05, 0.05, 0.0);
                }
                // Vertical energy beam shooting up from the bench through the spear
                for (double y = 1.0; y <= 8.0; y += 0.5) {
                    Location p = center.clone().add(0.5, y, 0.5);
                    world.spawnParticle(Particle.DUST, p, 1, 0.06, 0.06, 0.06, 0.0, BEAM_DUST);
                }
                world.spawnParticle(Particle.END_ROD, center.clone().add(0.5, 8.2, 0.5), 2, 0.2, 0.3, 0.2, 0.02);
                elapsed += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Dust color of the pedestal beams (Redline orange-red). */
    private static final Particle.DustOptions BEAM_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 64, 32), 1.2f);

    /** Draws a flowing particle beam from a pedestal item to the center spear. */
    private static void drawBeam(World world, Location from, Location to, long elapsed) {
        double length = from.distance(to);
        if (length < 0.1) return;
        Vector dir = to.clone().subtract(from).toVector().normalize();
        // Phase advances each run so the beam streams toward the center
        double phase = (elapsed % 10) / 10.0 * 0.5;
        for (double d = phase; d <= length; d += 0.5) {
            Location p = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, BEAM_DUST);
        }
        world.spawnParticle(Particle.END_ROD, to, 1, 0.1, 0.1, 0.1, 0.01);
    }

    private void finishRitual(Player player, Location center, List<ItemDisplay> displays, ItemDisplay spearDisplay) {
        World world = center.getWorld();
        if (world == null) return;

        for (ItemDisplay display : displays) {
            if (!display.isValid()) continue;
            world.spawnParticle(Particle.POOF, display.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
            display.remove();
        }
        if (spearDisplay.isValid()) spearDisplay.remove();

        Location dropAt = center.clone().add(0.5, 1.5, 0.5);
        Item dropped = world.dropItem(dropAt, redlineSpear.createRedlineSpear(2));
        dropped.setVelocity(new Vector(0, 0, 0));
        dropped.setGlowing(true);
        dropped.setOwner(player.getUniqueId());
        dropped.setPickupDelay(10);

        world.playSound(center, GravenConfig.REDLINE_ALTAR_COMPLETE_SOUND,
                GravenConfig.REDLINE_ALTAR_COMPLETE_VOLUME, GravenConfig.REDLINE_ALTAR_COMPLETE_PITCH);
        world.spawnParticle(Particle.FIREWORK, dropAt, 30, 0.4, 0.4, 0.4, 0.15);

        if (player.isOnline()) {
            player.sendMessage(GravenTheme.prefix().append(
                    GravenTheme.parse("<green>Your Redline has ascended to the <bold>Upgraded</bold> version!</green>")));
        }
        activeAltars.remove(center);
    }
}
