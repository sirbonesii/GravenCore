package dev.senob.particle_engine;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;

@SuppressWarnings("unused")
public class ParticleEngineUtil {

    // --- HELPER 1: Calculates 3D rotation for flat shapes ---
    private static Vector[] getAxes(Vector normal) {
        Vector up = Math.abs(normal.getY()) > 0.98 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = normal.clone().getCrossProduct(up).normalize();
        Vector trueUp = right.clone().getCrossProduct(normal).normalize();
        return new Vector[]{right, trueUp};
    }

    // --- HELPER 2: Handles dust options without duplicating code ---
    private static void spawn(World world, Particle particle, Location loc, int count, double extraSpeed, Object dustOptions) {
        if (dustOptions != null) {
            world.spawnParticle(particle, loc, count, 0, 0, 0, extraSpeed, dustOptions);
        } else {
            world.spawnParticle(particle, loc, count, 0, 0, 0, extraSpeed);
        }
    }

    // --- HELPER 3: Generates the 3D circular points to prevent duplicating math! ---
    private static void processCirclePoints(Location center, Vector normal, int points, BiConsumer<World, Vector> action) {
        World world = center.getWorld();
        if (world == null) return;

        Vector[] axes = getAxes(normal);
        double step = (2 * Math.PI) / points;

        for (int i = 0; i < points; i++) {
            double angle = i * step;
            // Calculates the unscaled outward direction for this point
            Vector radialDir = axes[0].clone().multiply(Math.cos(angle)).add(axes[1].clone().multiply(Math.sin(angle))).normalize();
            action.accept(world, radialDir);
        }
    }

    /**
     * Spawns an expanding flat ring of directional particles oriented along a normal vector.
     */
    public static void spawnExpandingRing(Location center, Vector normal, double startRadius, double expansionSpeed, Particle particle, int points, int layers) {
        for (int layer = 0; layer < layers; layer++) {
            double layerSpeed = expansionSpeed + (layer * 0.008);

            processCirclePoints(center, normal, points, (world, radialDir) -> {
                Location spawnLoc = center.clone().add(radialDir.clone().multiply(startRadius));
                world.spawnParticle(particle, spawnLoc, 0, radialDir.getX(), radialDir.getY(), radialDir.getZ(), layerSpeed);
            });
        }
    }

    /**
     * Spawns a static, flat circle of particles.
     */
    public static void spawnCircle(Location center, Vector normal, double radius, Particle particle, int points, Object dustOptions) {
        processCirclePoints(center, normal, points, (world, radialDir) -> {
            Location loc = center.clone().add(radialDir.multiply(radius));
            spawn(world, particle, loc, 1, 0, dustOptions);
        });
    }

    /**
     * Draws a solid line of particles from a starting location.
     */
    public static void drawLine(Location start, Vector direction, double length, double stepSpacing, Particle particle, int count, double extraSpeed, Object dustOptions) {
        World world = start.getWorld();
        if (world == null) return;

        Vector dirNorm = direction.clone().normalize();
        for (double d = 0; d <= length; d += stepSpacing) {
            Location loc = start.clone().add(dirNorm.clone().multiply(d));
            spawn(world, particle, loc, count, extraSpeed, dustOptions);
        }
    }

    /**
     * Spawns a hollow sphere of particles using randomized surface distribution.
     */
    public static void spawnSphere(Location center, double radius, Particle particle, int density, Object dustOptions) {
        World world = center.getWorld();
        if (world == null) return;

        for (int i = 0; i < density; i++) {
            double phi = Math.acos(1 - 2 * Math.random());
            double theta = 2 * Math.PI * Math.random();

            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);

            Location loc = center.clone().add(x, y, z);
            spawn(world, particle, loc, 1, 0, dustOptions);
        }
    }
}