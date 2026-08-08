package cn.mythicland.magicchest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicChestParticleGeometryTest {

    @Test
    void keepsParticlesNearTheChestHeight() {
        List<MagicChestParticleGeometry.ParticlePoint> points = MagicChestParticleGeometry.randomAroundChest(
                10,
                64,
                20,
                12,
                new SplittableRandom(0L)
        );

        assertEquals(12, points.size());
        assertTrue(points.stream().allMatch(point ->
                point.x() >= 9.0D && point.x() < 12.0D
                        && point.y() >= 64.3D && point.y() < 65.3D
                        && point.z() >= 19.0D && point.z() < 22.0D
        ));
    }

    @Test
    void usesTheConfiguredParticleCount() {
        List<MagicChestParticleGeometry.ParticlePoint> points = MagicChestParticleGeometry.randomAroundChest(
                10,
                64,
                20,
                3,
                new SplittableRandom(0L)
        );

        assertEquals(3, points.size());
    }
}
