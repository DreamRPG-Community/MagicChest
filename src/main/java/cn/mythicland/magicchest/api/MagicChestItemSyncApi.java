package cn.mythicland.magicchest.api;

/**
 * Main-thread service for reconciling stored MagicChest items with an external item source.
 */
public interface MagicChestItemSyncApi {

    /**
     * Reconciles templates, drafts, live contents, and open native interfaces atomically from
     * MagicChest's point of view.
     *
     * @param reconciler external item identity and refresh logic
     * @return synchronization statistics
     * @throws NullPointerException  if reconciler is null
     * @throws IllegalStateException if called off the Bukkit primary thread or the MagicChest state
     *                               cannot be updated atomically
     */
    MagicChestItemSyncReport synchronize(MagicChestItemReconciler reconciler);
}
