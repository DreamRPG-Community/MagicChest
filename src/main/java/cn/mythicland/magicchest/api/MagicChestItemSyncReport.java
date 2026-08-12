package cn.mythicland.magicchest.api;

/**
 * Immutable statistics from one MagicChest synchronization pass.
 *
 * @param chests                 number of scanned chests
 * @param updatedTemplates       number of refreshed template slots
 * @param updatedDrafts          number of refreshed unmodified draft slots
 * @param updatedLiveItems       number of refreshed live item slots
 * @param updatedOpenInventories number of open inventories rewritten
 * @param skippedUnmanaged       number of unmarked item slots left unchanged
 * @param staleItems             number of marked items whose source was removed
 */
public record MagicChestItemSyncReport(
        int chests,
        int updatedTemplates,
        int updatedDrafts,
        int updatedLiveItems,
        int updatedOpenInventories,
        int skippedUnmanaged,
        int staleItems
) {

    public MagicChestItemSyncReport {
        if (chests < 0 || updatedTemplates < 0 || updatedDrafts < 0 || updatedLiveItems < 0
                || updatedOpenInventories < 0 || skippedUnmanaged < 0 || staleItems < 0) {
            throw new IllegalArgumentException("MagicChest sync counters cannot be negative");
        }
    }
}
