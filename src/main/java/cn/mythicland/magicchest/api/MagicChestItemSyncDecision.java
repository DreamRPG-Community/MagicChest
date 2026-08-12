package cn.mythicland.magicchest.api;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Detached decision for one stored item stack.
 *
 * @param status      synchronization status
 * @param replacement replacement stack required only for {@link MagicChestItemSyncStatus#UPDATED}
 */
public record MagicChestItemSyncDecision(
        MagicChestItemSyncStatus status,
        ItemStack replacement
) {

    public MagicChestItemSyncDecision {
        status = Objects.requireNonNull(status, "status");
        replacement = replacement == null ? null : replacement.clone();
        if (status == MagicChestItemSyncStatus.UPDATED && replacement == null) {
            throw new IllegalArgumentException("UPDATED sync decision requires a replacement item");
        }
        if (status != MagicChestItemSyncStatus.UPDATED && replacement != null) {
            throw new IllegalArgumentException("Only UPDATED sync decisions may contain a replacement item");
        }
    }

    /**
     * Indicates whether this decision replaces the stored item.
     *
     * @return true only for an updated item with a replacement stack
     */
    public boolean changed() {
        return status == MagicChestItemSyncStatus.UPDATED && replacement != null;
    }

    @Override
    public ItemStack replacement() {
        return replacement == null ? null : replacement.clone();
    }
}
