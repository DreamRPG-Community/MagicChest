package cn.mythicland.magicchest;

import cn.mythicland.lib.container.ContainerAnimationHandle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Tracks one private native inventory and its delayed post-event snapshot.
 */
final class MagicChestInventorySession {

    private final MagicChestInventoryHolder holder;
    private ItemStack[] pendingBaseline;
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

    ItemStack[] pendingBaseline() {
        return pendingBaseline;
    }

    void beginMutation(ItemStack[] baseline) {
        Objects.requireNonNull(baseline, "baseline");
        if (pendingBaseline == null) pendingBaseline = MagicChestRecord.copyContents(baseline);
    }

    void clearMutation() {
        pendingBaseline = null;
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
