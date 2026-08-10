package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.MagicChestKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one player-specific native MagicChest inventory.
 *
 * <p>A new holder and a new Bukkit inventory are created for every viewer. The holder therefore
 * also prevents an inventory instance from being accidentally reused by another player.</p>
 */
final class MagicChestInventoryHolder implements InventoryHolder {

    private final MagicChestKey key;
    private final UUID viewerUniqueId;
    private final boolean editor;
    private Inventory inventory;

    MagicChestInventoryHolder(MagicChestKey key, UUID viewerUniqueId, boolean editor) {
        this.key = Objects.requireNonNull(key, "key");
        this.viewerUniqueId = Objects.requireNonNull(viewerUniqueId, "viewerUniqueId");
        this.editor = editor;
    }

    MagicChestKey key() {
        return key;
    }

    UUID viewerUniqueId() {
        return viewerUniqueId;
    }

    boolean editor() {
        return editor;
    }

    void attach(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        if (this.inventory != null) throw new IllegalStateException("MagicChest inventory already attached");
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
