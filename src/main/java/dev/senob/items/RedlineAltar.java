package dev.senob.items;

import dev.senob.GravenCore;
import dev.senob.core.GravenConfig;
import dev.senob.core.GravenTheme;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redline Altar — upgrades a base Redline spear to the upgraded version.
 *
 * A dropper ringed by 8 chiseled-stone-bricks pedestals is an altar. The
 * player loads the upgrade recipe into the dropper (layout below) and closes
 * it: the ingredients appear on the pedestals, the spear in the center, and
 * after the ritual the upgraded spear drops at the center.
 *
 * Recipe layout (dropper slots):
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
    private static final Material PEDESTAL_BLOCK = Material.CHISELED_STONE_BRICKS;

    private final GravenCore plugin;
    private final RedlineSpear redlineSpear;
    private final Set<Location> activeAltars = new HashSet<>();

    public RedlineAltar(GravenCore plugin, RedlineSpear redlineSpear) {
        this.plugin = plugin;
        this.redlineSpear = redlineSpear;
        plugin.getLogger().info("Redline Altar module initialized!");
    }

    @EventHandler
    public void onDropperClose(InventoryCloseEvent event) {
        if (!GravenConfig.REDLINE_ALTAR_ENABLED) return;
        Inventory inv = event.getInventory();
        if (inv.getType() != InventoryType.DROPPER) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Location loc = inv.getLocation();
        if (loc == null) return;
        Location center = loc.getBlock().getLocation();
        if (center.getBlock().getType() != Material.DROPPER) return;
        if (activeAltars.contains(center)) return;

        ItemStack centerItem = inv.getItem(4);
        int tier = (centerItem != null) ? redlineSpear.getRedlineTier(centerItem) : 0;
        if (tier == 0 || !ingredientsMatch(inv)) return;

        if (tier >= 2) {
            player.sendMessage(GravenTheme.prefix().append(
                    GravenTheme.parse("<red>That Redline is already upgraded.</red>")));
            return;
        }

        List<Location> pedestals = findPedestals(center);
        if (pedestals.isEmpty()) {
            player.sendMessage(GravenTheme.prefix().append(
                    GravenTheme.parse("<red>The altar is incomplete — the dropper needs exactly 8 chiseled stone bricks pedestals around it.</red>")));
            return;
        }

        // Snapshot the ring items in compass order so each ingredient appears
        // on the pedestal matching its recipe position
        List<ItemStack> ringItems = new ArrayList<>();
        for (int slot : SLOT_BY_COMPASS) ringItems.add(inv.getItem(slot).clone());
        ItemStack spearItem = centerItem.clone();

        // Consume exactly one of each ingredient (extras stay in the dropper)
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            if (it.getAmount() <= 1) inv.setItem(i, null);
            else it.setAmount(it.getAmount() - 1);
        }

        startRitual(player, center, pedestals, ringItems, spearItem);
    }

    private boolean ingredientsMatch(Inventory inv) {
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != SLOT_MATERIALS[i]) return false;
        }
        return true;
    }

    /**
     * Finds the 8 pedestals around the center: chiseled stone bricks at the
     * same height, 2–4 blocks out, exactly one per compass sector.
     * Returns them sorted by compass sector, or empty if the ring is invalid.
     */
    private List<Location> findPedestals(Location center) {
        World world = center.getWorld();
        if (world == null) return List.of();
        Location[] byCompass = new Location[8];
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
                if (chebyshev < 2 || chebyshev > 4) continue;
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
                             List<ItemStack> ringItems, ItemStack spearItem) {
        World world = center.getWorld();
        if (world == null) return;
        activeAltars.add(center);

        world.playSound(center, GravenConfig.REDLINE_ALTAR_START_SOUND,
                GravenConfig.REDLINE_ALTAR_START_VOLUME, GravenConfig.REDLINE_ALTAR_START_PITCH);
        if (player.isOnline()) {
            player.sendMessage(GravenTheme.prefix().append(
                    GravenTheme.parse("<aqua>The altar awakens — the ritual begins!</aqua>")));
        }

        List<ItemDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Location at = pedestals.get(i).clone().add(0.5, 1.7, 0.5);
            ItemStack shown = ringItems.get(i).clone();
            shown.setAmount(1);
            displays.add(world.spawn(at, ItemDisplay.class, disp -> {
                disp.setItemStack(shown);
                disp.setPersistent(false);
                disp.setBillboard(Display.Billboard.FIXED);
            }));
        }
        Location spearAt = center.clone().add(0.5, 2.0, 0.5);
        ItemDisplay spearDisplay = world.spawn(spearAt, ItemDisplay.class, disp -> {
            disp.setItemStack(spearItem.clone());
            disp.setPersistent(false);
            disp.setBillboard(Display.Billboard.FIXED);
        });

        long totalTicks = Math.max(20L, (long) (GravenConfig.REDLINE_ALTAR_RITUAL_DURATION * 20.0));
        new BukkitRunnable() {
            long elapsed = 0;
            float yaw = 0f;

            @Override
            public void run() {
                if (elapsed >= totalTicks) {
                    finishRitual(player, center, displays, spearDisplay);
                    cancel();
                    return;
                }
                yaw += 9f;
                Location spearLoc = spearDisplay.isValid() ? spearDisplay.getLocation() : center.clone().add(0.5, 2.0, 0.5);
                for (ItemDisplay display : displays) {
                    if (!display.isValid()) continue;
                    Location l = display.getLocation();
                    l.setYaw(yaw);
                    display.teleport(l);
                    world.spawnParticle(Particle.ENCHANT, display.getLocation(), 2, 0.2, 0.2, 0.2, 0.5);
                    drawBeam(world, display.getLocation(), spearLoc, elapsed);
                }
                if (spearDisplay.isValid()) {
                    Location l = spearDisplay.getLocation();
                    l.setYaw(yaw * 1.5f);
                    spearDisplay.teleport(l);
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
