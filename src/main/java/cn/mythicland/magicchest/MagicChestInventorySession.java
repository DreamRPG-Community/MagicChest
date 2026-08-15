package cn.mythicland.magicchest;

import cn.mythicland.lib.container.ContainerAnimationHandle;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

/**
 * Tracks one private native inventory session.
 */
final class MagicChestInventorySession {

    private final MagicChestInventoryHolder holder;
    private ContainerAnimationHandle animation;

    MagicChestInventorySession(MagicChestInventoryHolder holder) {
        this.holder = Objects.requireNonNull(holder, "holder");
        if (holder.getInventory() == null) throw new IllegalArgumentException("holder has no inventory");
    }

    MagicChestInventoryHolder holder() {
        return holder;
    }

    Inventory inventory() {
        return holder.getInventory();
    }

    boolean editor() {
        return holder.editor();
    }

    void attachAnimation(ContainerAnimationHandle animation) {
        if (this.animation != null) throw new IllegalStateException("MagicChest animation already attached");
        this.animation = Objects.requireNonNull(animation, "animation");
    }

    void closeAnimation() {
        if (animation != null) animation.close();
        animation = null;
    }
}
