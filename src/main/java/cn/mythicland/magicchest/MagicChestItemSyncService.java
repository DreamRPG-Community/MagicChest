package cn.mythicland.magicchest;

import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.magicchest.api.MagicChestItemReconciler;
import cn.mythicland.magicchest.api.MagicChestItemSyncApi;
import cn.mythicland.magicchest.api.MagicChestItemSyncReport;

import java.util.Objects;

/**
 * Service facade that keeps MagicChest state mutation inside MagicChestService.
 */
@ServiceComponent(MagicChestItemSyncApi.class)
public final class MagicChestItemSyncService implements MagicChestItemSyncApi {

    private final MagicChestService service;

    MagicChestItemSyncService(MagicChestService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Delegates synchronization to MagicChest's main-thread state owner.
     *
     * @param reconciler external item reconciler
     * @return synchronization statistics
     */
    @Override
    public MagicChestItemSyncReport synchronize(MagicChestItemReconciler reconciler) {
        return service.synchronizeItems(reconciler);
    }
}
