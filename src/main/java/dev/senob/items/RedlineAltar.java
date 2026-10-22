package dev.senob.items;

import dev.senob.GravenCore;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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
 * A dropper ringed by 8 lodestone pedestals is an altar. The bench is the
 * dropper's own vanilla UI — no custom inventory, no custom title — so items
 * are placed, shift-clicked, dragged and taken out exactly like a normal
 * dropper. Whenever an item sitting in a slot is the correct ingredient for
 * that slot, the altar locks it in: the item itself is stored in the dropper
 * block's persistent data (so it survives restarts) and the slot shows a
 * green glass pane in its place, with the ingredient appearing — spinning —
 * on the matching pedestal. The spear itself stands planted point-down
 * into the dropper, also spinning. Wrong items are left completely
 * untouched, like any dropper. Clicking a green pane with an empty hand
 * takes the stored item back out. When all 9 slots are locked, the ritual
 * starts: the pedestal items orbit the center, spiraling in and shrinking
 * into the spear as it spins faster and rises out of the altar, and the
 * ritual ends in an explosion — lethal to anyone standing in it, unless
 * a totem of undying saves them — after which the upgraded spear drops
 * at the center. The center spear uses its big in-hand model, stuck
 * point-down into the dropper. The altar's only particles are the
 * enchant sparkles on the pedestal items, and it never sends chat
 * messages.
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
    private static final Material PEDESTAL_BLOCK = Material.LODESTONE;
    /** Preview/ritual display key for the center spear (pedestals use compass 0-7). */
    private static final int CENTER_KEY = 8;

    private final GravenCore plugin;
    private final RedlineSpear redlineSpear;
    private final Set<Location> activeAltars = new HashSet<>();
    /** Live pedestal displays per altar center, keyed by compass sector / CENTER_KEY. */
    private final Map<Location, Map<Integer, ItemDisplay>> previews = new HashMap<>();
    private final Map<Location, List<Location>> pedestalCache = new HashMap<>();
    private final Set<Location> reconcilePending = new HashSet<>();
    /** Continuously spins every preview display (pedestal items + planted spear). */
    private BukkitTask previewSpinTask;
    private float previewYaw = 0f;

    public RedlineAltar(GravenCore plugin, RedlineSpear redlineSpear) {
        this.plugin = plugin;
        this.redlineSpear = redlineSpear;
        plugin.getLogger().info("Redline Altar module initialized!");
    }

    // --- Bench (the dropper's own vanilla inventory) ------------------------------

    @EventHandler
    public void onAltarInteract(PlayerInteractEvent event) {
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
        if (pedestalsFor(center).isEmpty()) return; // not an altar: vanilla dropper behavior
        syncPreview(center);
        if (readStored(center).size() == 9) {
            // Recipe already complete (e.g. finished before a restart):
            // right-clicking starts the ritual instead of opening the dropper
            event.setCancelled(true);
            startRitualFromDropper(event.getPlayer(), center);
        }
        // Otherwise do nothing: the vanilla dropper opens, with its default title
    }

    @EventHandler
    public void onDropperOpen(InventoryOpenEvent event) {
        if (!GravenConfig.REDLINE_ALTAR_ENABLED) return;
        Location center = altarLocation(event.getInventory());
        if (center == null || pedestalsFor(center).isEmpty()) return;
        // Lock in anything already sitting in the right slots
        if (event.getPlayer() instanceof Player player) scheduleReconcile(center, player);
    }

    @EventHandler
    public void onDropperClick(InventoryClickEvent event) {
        if (!GravenConfig.REDLINE_ALTAR_ENABLED) return;
        Inventory top = event.getView().getTopInventory();
        Location center = altarLocation(top);
        if (center == null || pedestalsFor(center).isEmpty()) return;
        if (activeAltars.contains(center)) {
            event.setCancelled(true);
            return;
        }
        Player player = (event.getWhoClicked() instanceof Player p) ? p : null;

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            // A double-click could collect the green lock panes like real items
            if (!readStored(center).isEmpty()) event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(top)) {
            int slot = event.getSlot();
            Map<Integer, ItemStack> stored = readStored(center);
            if (stored.containsKey(slot)) {
                // Locked slot: the green pane is a marker, not an item.
                // An empty-hand click takes the stored ingredient back out;
                // anything else is blocked so the pane can't be moved.
                event.setCancelled(true);
                ItemStack cursor = (player != null) ? player.getItemOnCursor() : null;
                if (player != null && (cursor == null || cursor.getType() == Material.AIR)) {
                    unlock(center, slot, player, stored);
                }
                return;
            }
            // Any other change to the dropper contents: check for lock-ins after it lands
            scheduleReconcile(center, player);
        } else if (event.isShiftClick() && clicked != null) {
            // Shift-clicking from the player's inventory moves items into the dropper
            scheduleReconcile(center, player);
        }
    }

    @EventHandler
    public void onDropperDrag(InventoryDragEvent event) {
        if (!GravenConfig.REDLINE_ALTAR_ENABLED) return;
        Inventory top = event.getView().getTopInventory();
        Location center = altarLocation(top);
        if (center == null || pedestalsFor(center).isEmpty()) return;
        if (activeAltars.contains(center)) {
            event.setCancelled(true);
            return;
        }
        Map<Integer, ItemStack> stored = readStored(center);
        boolean touchesTop = false;
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                touchesTop = true;
                if (stored.containsKey(raw)) {
                    event.setCancelled(true); // never drag across a locked pane
                    return;
                }
            }
        }
        if (touchesTop) {
            scheduleReconcile(center, (event.getWhoClicked() instanceof Player p) ? p : null);
        }
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (!GravenConfig.REDLINE_ALTAR_ENABLED) return;
        Location dest = altarLocation(event.getDestination());
        if (dest != null && !pedestalsFor(dest).isEmpty() && !activeAltars.contains(dest)) {
            scheduleReconcile(dest, null);
        }
        Location src = altarLocation(event.getSource());
        if (src != null && !pedestalsFor(src).isEmpty()
                && event.getItem().getType() == Material.GREEN_STAINED_GLASS_PANE
                && !readStored(src).isEmpty()) {
            event.setCancelled(true); // hoppers can't pull the lock panes out
        }
    }

    /** Schedules a one-tick-later check of the dropper contents (once per altar). */
    private void scheduleReconcile(Location center, Player player) {
        if (!reconcilePending.add(center)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            reconcilePending.remove(center);
            reconcile(center, player);
        }, 1L);
    }

    /**
     * Locks in every correct ingredient currently sitting in the dropper:
     * one of the item is stored in the altar and its slot becomes a green
     * pane (any excess of a stack is dropped back out at the altar). Wrong
     * items are left exactly as they are. Starts the ritual on the 9th lock.
     */
    private void reconcile(Location center, Player player) {
        if (activeAltars.contains(center)) return;
        Inventory inv = dropperInventory(center);
        if (inv == null) return;
        Map<Integer, ItemStack> stored = readStored(center);
        boolean lockedAny = false;
        for (int r = 0; r < 9; r++) {
            if (stored.containsKey(r)) {
                ItemStack cur = inv.getItem(r);
                if (cur == null || cur.getType() != Material.GREEN_STAINED_GLASS_PANE) {
                    inv.setItem(r, lockPane(r)); // pane went missing: restore the marker
                }
                continue;
            }
            ItemStack item = inv.getItem(r);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isCorrectForSlot(r, item)) continue; // wrong item: vanilla, left alone
            ItemStack one = item.clone();
            one.setAmount(1);
            stored.put(r, one);
            if (item.getAmount() > 1) {
                ItemStack excess = item.clone();
                excess.setAmount(item.getAmount() - 1);
                World world = center.getWorld();
                if (world != null) world.dropItemNaturally(center.clone().add(0.5, 1.2, 0.5), excess);
            }
            inv.setItem(r, lockPane(r));
            lockedAny = true;
        }
        if (lockedAny) {
            writeStored(center, stored);
            World world = center.getWorld();
            if (world != null) world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            syncPreview(center);
        }
        if (stored.size() == 9) {
            Player starter = (player != null) ? player : firstViewer(inv);
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) viewer.closeInventory();
            startRitualFromDropper(starter, center);
        }
    }

    /** Takes a locked ingredient back out of the altar and returns it to the player. */
    private void unlock(Location center, int slot, Player player, Map<Integer, ItemStack> stored) {
        ItemStack item = stored.remove(slot);
        if (item == null) return;
        Inventory inv = dropperInventory(center);
        if (inv != null) inv.setItem(slot, null);
        writeStored(center, stored);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        syncPreview(center);
    }

    private static Player firstViewer(Inventory inv) {
        for (HumanEntity viewer : inv.getViewers()) {
            if (viewer instanceof Player player) return player;
        }
        return null;
    }

    // --- Locked-item storage (in the dropper block's persistent data) ---------------

    private NamespacedKey slotKey(int recipeSlot) {
        return new NamespacedKey(plugin, "altar_slot_" + recipeSlot);
    }

    /** Reads the ingredients locked into the altar, keyed by recipe slot. */
    private Map<Integer, ItemStack> readStored(Location center) {
        Map<Integer, ItemStack> stored = new HashMap<>();
        if (center.getBlock().getState() instanceof TileState tile) {
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            for (int r = 0; r < 9; r++) {
                byte[] bytes = pdc.get(slotKey(r), PersistentDataType.BYTE_ARRAY);
                if (bytes == null) continue;
                try {
                    stored.put(r, ItemStack.deserializeBytes(bytes));
                } catch (Exception ignored) {
                    // Corrupt entry: treat the slot as unlocked
                }
            }
        }
        return stored;
    }

    /** Persists the locked ingredients. Call after the inventory edits they belong to. */
    private void writeStored(Location center, Map<Integer, ItemStack> stored) {
        if (center.getBlock().getState() instanceof TileState tile) {
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            for (int r = 0; r < 9; r++) {
                ItemStack item = stored.get(r);
                if (item == null) pdc.remove(slotKey(r));
                else pdc.set(slotKey(r), PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
            }
            tile.update(true);
        }
    }

    // --- Recipe helpers ---------------------------------------------------------------

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

    /** The green pane that replaces a locked-in ingredient in the dropper. */
    private static ItemStack lockPane(int recipeSlot) {
        ItemStack pane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(GravenTheme.parse("<green>✔ " + ingredientName(recipeSlot) + "</green>"));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private boolean isCorrectForSlot(int recipeSlot, ItemStack item) {
        if (item == null) return false;
        if (recipeSlot == 4) return redlineSpear.getRedlineTier(item) == 1;
        return item.getType() == SLOT_MATERIALS[recipeSlot];
    }

    private List<Location> pedestalsFor(Location center) {
        List<Location> cached = pedestalCache.get(center);
        if (cached != null && !cached.isEmpty()) return cached;
        List<Location> found = findPedestals(center);
        if (!found.isEmpty()) pedestalCache.put(center, found);
        return found;
    }

    /** The altar location behind a block inventory, or null for other inventories. */
    private static Location altarLocation(Inventory inv) {
        if (inv.getHolder() instanceof BlockState state && state.getType() == Material.DROPPER) {
            return state.getLocation();
        }
        return null;
    }

    // --- Pedestal preview ----------------------------------------------------------

    /** Mirrors the altar's locked-in items onto the pedestals around it. */
    private void syncPreview(Location center) {
        if (activeAltars.contains(center)) return;
        if (center.getBlock().getType() != Material.DROPPER) {
            clearPreviews(center);
            return;
        }
        List<Location> pedestals = pedestalCache.computeIfAbsent(center, this::findPedestals);
        if (pedestals.isEmpty()) return;

        Map<Integer, ItemStack> stored = readStored(center);
        Map<Integer, ItemDisplay> shown = previews.computeIfAbsent(center, k -> new HashMap<>());
        World world = center.getWorld();
        if (world == null) return;

        for (int compass = 0; compass < 8; compass++) {
            ItemStack item = stored.get(SLOT_BY_COMPASS[compass]);
            Location at = pedestals.get(compass).clone().add(0.5, 1.7, 0.5);
            reconcile(shown, compass, at, item);
        }
        ItemStack spear = stored.get(4);
        ItemStack spearShown = (spear != null && redlineSpear.getRedlineTier(spear) >= 1) ? spear : null;
        // The spear sticks point-down into the dropper (the in-hand model is
        // long, so the display origin sits well above the block)
        reconcile(shown, CENTER_KEY, center.clone().add(0.5, 2.1, 0.5), spearShown);
        ensureSpinTask();
    }

    /** Starts the shared preview spin loop if it is not already running. */
    private void ensureSpinTask() {
        if (previewSpinTask != null) return;
        previewSpinTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            boolean anyShown = previews.values().stream().anyMatch(m -> !m.isEmpty());
            if (!anyShown) {
                previewSpinTask.cancel();
                previewSpinTask = null;
                return;
            }
            previewYaw += 10f;
            for (Map<Integer, ItemDisplay> shown : previews.values()) {
                for (Map.Entry<Integer, ItemDisplay> entry : shown.entrySet()) {
                    ItemDisplay display = entry.getValue();
                    if (!display.isValid()) continue;
                    Location l = display.getLocation();
                    l.setYaw(previewYaw);
                    display.teleport(l);
                    // The altar's only particles: sparkles on the pedestal items
                    if (entry.getKey() != CENTER_KEY) {
                        display.getWorld().spawnParticle(Particle.ENCHANT, display.getLocation(),
                                1, 0.15, 0.2, 0.15, 0.3);
                    }
                }
            }
        }, 2L, 2L);
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
        shown.put(key, key == CENTER_KEY ? spawnPlantedSpear(world, at, item) : spawnDisplay(world, at, item));
    }

    /** Spawns the center spear planted point-down into the altar, using its in-hand model. */
    private static ItemDisplay spawnPlantedSpear(World world, Location at, ItemStack item) {
        ItemStack shownItem = item.clone();
        shownItem.setAmount(1);
        return world.spawn(at, ItemDisplay.class, disp -> {
            disp.setItemStack(shownItem);
            disp.setPersistent(false);
            disp.setBillboard(Display.Billboard.FIXED);
            // THIRDPERSON_RIGHTHAND renders the spear's big in-hand 3D model
            // (the one an armor stand or player holds) instead of the flat
            // frame/dropped model; the half-turn around X flips that held
            // pose point-down so the spear sticks down into the altar.
            // Scale 1.0 = exactly the size it has in a player's hand.
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);
            disp.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf().rotateX((float) Math.PI),
                    new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
        });
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
            // Drop the real contents — locked ingredients and any loose items —
            // and clear the inventory so the green lock panes never drop as items
            Map<Integer, ItemStack> stored = readStored(center);
            Inventory inv = dropperInventory(center);
            List<ItemStack> drops = new ArrayList<>();
            if (inv != null) {
                for (int r = 0; r < 9; r++) {
                    ItemStack locked = stored.get(r);
                    if (locked != null) drops.add(locked.clone());
                    ItemStack cur = inv.getItem(r);
                    boolean isPane = locked != null && cur != null
                            && cur.getType() == Material.GREEN_STAINED_GLASS_PANE;
                    if (cur != null && !isPane) drops.add(cur);
                }
                inv.clear();
            } else {
                for (ItemStack locked : stored.values()) drops.add(locked.clone());
            }
            World world = center.getWorld();
            if (world != null) {
                for (ItemStack drop : drops) {
                    world.dropItemNaturally(center.clone().add(0.5, 0.5, 0.5), drop);
                }
            }
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

    /** Consumes the completed recipe from the altar and starts the ritual. */
    private void startRitualFromDropper(Player player, Location center) {
        Map<Integer, ItemStack> stored = readStored(center);
        if (stored.size() < 9) return;
        List<Location> pedestals = pedestalsFor(center);
        if (pedestals.isEmpty()) return; // the altar never speaks in chat

        // Snapshot the ring items in compass order so each ingredient appears
        // on the pedestal matching its recipe position
        List<ItemStack> ringItems = new ArrayList<>();
        for (int slot : SLOT_BY_COMPASS) ringItems.add(stored.get(slot).clone());
        ItemStack spearItem = stored.get(4).clone();

        Inventory dropper = dropperInventory(center);
        if (dropper != null) dropper.clear(); // removes the green lock panes
        writeStored(center, Map.of());

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
            Location spearAt = center.clone().add(0.5, 2.1, 0.5);
            spearDisplay = spawnPlantedSpear(world, spearAt, spearItem);
        }
        if (adopted != null) {
            for (ItemDisplay leftover : adopted.values()) leftover.remove();
        }
        final ItemDisplay centerDisplay = spearDisplay;
        final double spearStartY = centerDisplay.getLocation().getY();

        // Orbit paths for the pedestal items: each starts above its pedestal,
        // then circles the center while spiraling in and shrinking toward the spear
        List<Double> orbitAngles = new ArrayList<>();
        List<Double> orbitRadii = new ArrayList<>();
        List<Double> orbitY = new ArrayList<>();
        for (ItemDisplay display : displays) {
            Location l = display.getLocation();
            double dx = l.getX() - (center.getX() + 0.5);
            double dz = l.getZ() - (center.getZ() + 0.5);
            orbitAngles.add(Math.atan2(dz, dx));
            orbitRadii.add(Math.hypot(dx, dz));
            orbitY.add(l.getY());
        }

        long totalTicks = Math.max(20L, (long) (GravenConfig.REDLINE_ALTAR_RITUAL_DURATION * 20.0));
        new BukkitRunnable() {
            long elapsed = 0;
            float yaw = 0f;
            float spearYaw = 0f;

            @Override
            public void run() {
                if (elapsed >= totalTicks) {
                    finishRitual(player, center, displays, centerDisplay);
                    cancel();
                    return;
                }
                double progress = (double) elapsed / totalTicks;
                yaw += 9f + (float) (progress * 9.0);
                // The planted spear spins faster and faster and rises out of the altar
                if (centerDisplay.isValid()) {
                    spearYaw += 9f + (float) (progress * 27.0);
                    Location l = centerDisplay.getLocation();
                    l.setY(spearStartY + progress * 1.3);
                    l.setYaw(spearYaw);
                    centerDisplay.teleport(l);
                }
                for (int i = 0; i < displays.size(); i++) {
                    ItemDisplay display = displays.get(i);
                    if (!display.isValid()) continue;
                    // Circular motion: two full revolutions while the radius
                    // collapses toward the spear and the item shrinks away
                    double angle = orbitAngles.get(i) + progress * Math.PI * 4.0;
                    double radius = orbitRadii.get(i) * (1.0 - progress) + 0.35 * progress;
                    double oy = orbitY.get(i) + ((spearStartY + 1.0) - orbitY.get(i)) * progress;
                    Location l = new Location(world,
                            center.getX() + 0.5 + Math.cos(angle) * radius,
                            oy,
                            center.getZ() + 0.5 + Math.sin(angle) * radius);
                    l.setYaw(yaw);
                    display.teleport(l);
                    float s = (float) (0.5 * (1.0 - progress * 0.92));
                    display.setTransformation(new Transformation(
                            new Vector3f(), new Quaternionf(),
                            new Vector3f(s, s, s), new Quaternionf()));
                    // The only particles in the whole ritual: the sparkles
                    // on the pedestal items themselves
                    world.spawnParticle(Particle.ENCHANT, display.getLocation(), 2, 0.2, 0.2, 0.2, 0.5);
                }
                elapsed += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finishRitual(Player player, Location center, List<ItemDisplay> displays, ItemDisplay spearDisplay) {
        World world = center.getWorld();
        if (world == null) return;

        // The ritual ends in an explosion: sound and lethal damage only —
        // no particles (the pedestal sparkles are the altar's only particles).
        // The blast breaks no blocks, but it kills anyone standing in it —
        // a held totem of undying still pops and saves them, as vanilla intends
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);
        Location blast = center.clone().add(0.5, 1.0, 0.5);
        for (Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distanceSquared(blast) <= 64.0) { // within 8 blocks
                nearby.damage(1000.0);
            }
        }

        for (ItemDisplay display : displays) {
            if (display.isValid()) display.remove();
        }
        if (spearDisplay.isValid()) spearDisplay.remove();

        Location dropAt = center.clone().add(0.5, 1.5, 0.5);
        Item dropped = world.dropItem(dropAt, redlineSpear.createRedlineSpear(2));
        dropped.setVelocity(new Vector(0, 0, 0));
        dropped.setGlowing(true);
        dropped.setOwner(player != null ? player.getUniqueId() : null);
        dropped.setPickupDelay(10);

        world.playSound(center, GravenConfig.REDLINE_ALTAR_COMPLETE_SOUND,
                GravenConfig.REDLINE_ALTAR_COMPLETE_VOLUME, GravenConfig.REDLINE_ALTAR_COMPLETE_PITCH);

        activeAltars.remove(center);
    }
}
