package cn.mythicland.magicchest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Calculates the random particle points around a managed chest.
 *
 * <p>The horizontal distribution follows ThePitGay's sewer chest effect.
 * The vertical range is kept close to the chest so the hologram area remains
 * clear.</p>
 */
final class MagicChestParticleGeometry {

    private static final double HORIZONTAL_OFFSET = 1.5D;
    private static final double MIN_HEIGHT_OFFSET = 0.3D;
    private static final double MAX_HEIGHT_OFFSET = 1.3D;

    private MagicChestParticleGeometry() {
    }

    static List<ParticlePoint> randomAroundChest(
            int blockX,
            int blockY,
            int blockZ,
            int particleCount,
            RandomGenerator random
    ) {
        Objects.requireNonNull(random, "random");
        if (particleCount <= 0) throw new IllegalArgumentException("particleCount must be positive");
        List<ParticlePoint> points = new ArrayList<>(particleCount);
        double centerX = blockX + 0.5D;
        double centerZ = blockZ + 0.5D;
        for (int index = 0; index < particleCount; index++) {
            points.add(new ParticlePoint(
                    centerX + random.nextDouble(-HORIZONTAL_OFFSET, HORIZONTAL_OFFSET),
                    blockY + random.nextDouble(MIN_HEIGHT_OFFSET, MAX_HEIGHT_OFFSET),
                    centerZ + random.nextDouble(-HORIZONTAL_OFFSET, HORIZONTAL_OFFSET)
            ));
        }
        return List.copyOf(points);
    }

    record ParticlePoint(double x, double y, double z) {
    }
}
