package cn.mythicland.magicchest.api;

/**
 * Result category returned by a MagicChest item reconciler.
 */
public enum MagicChestItemSyncStatus {
    UNMANAGED,
    CURRENT,
    UPDATED,
    STALE
}
