package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.MagicChestKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the world-coordinate storage key format.
 */
class MagicChestKeyTest {

    @Test
    void encodedKeyRoundTripsNegativeCoordinates() {
        MagicChestKey key = new MagicChestKey(UUID.fromString("00000000-0000-0000-0000-000000000001"), -4, 70, -8);

        assertEquals(key, MagicChestKey.parse(key.encoded()));
    }

    @Test
    void malformedKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MagicChestKey.parse("not-a-key"));
    }
}
