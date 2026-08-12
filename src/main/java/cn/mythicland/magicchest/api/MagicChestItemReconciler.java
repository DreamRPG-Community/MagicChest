package cn.mythicland.magicchest.api;

import org.bukkit.inventory.ItemStack;

/**
 * External item refresh callback used by MagicChest without coupling it to a specific item
 * provider.
 */
@FunctionalInterface
public interface MagicChestItemReconciler {

    /**
     * Inspects one existing stored stack. The implementation must not mutate the input stack.
     *
     * @param item item stored in MagicChest
     * @param mode amount policy
     * @return detached synchronization decision
     * @throws RuntimeException if the external item source cannot determine a decision
     */
    MagicChestItemSyncDecision reconcile(ItemStack item, MagicChestItemSyncMode mode);
}
