package cn.mythicland.magicchest.api;

import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable in-memory identity for one managed chest block.
 *
 * <p>The key contains only a world UUID and block coordinates. It does not modify or identify a
 * block through NBT, persistent data, or any other world storage.</p>
 *
 * @param worldId world UUID
 * @param x       block X coordinate
 * @param y       block Y coordinate
 * @param z       block Z coordinate
 */
public record MagicChestKey(UUID worldId, int x, int y, int z) {

    /**
     * Validates the key.
     */
    public MagicChestKey {
        Objects.requireNonNull(worldId, "worldId");
    }

    /**
     * Creates a key from a Bukkit block.
     *
     * @param block source block
     * @return immutable key
     */
    public static MagicChestKey from(Block block) {
        Objects.requireNonNull(block, "block");
        if (block.getWorld() == null) throw new IllegalArgumentException("block.world cannot be null");
        return new MagicChestKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Returns the storage-safe textual key.
     *
     * @return {@code world-uuid:x:y:z}
     */
    public String encoded() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    /**
     * Parses a key produced by {@link #encoded()}.
     *
     * @param value encoded key
     * @return parsed key
     * @throws IllegalArgumentException when the value is malformed
     */
    public static MagicChestKey parse(String value) {
        String text = Objects.requireNonNull(value, "value").trim();
        String[] parts = text.split(":", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid MagicChest key: " + value);
        try {
            return new MagicChestKey(
                    UUID.fromString(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MagicChest key: " + value, exception);
        }
    }
}
