package cn.mythicland.magicchest.api;

/**
 * Amount policy requested by an external MagicChest item synchronizer.
 */
public enum MagicChestItemSyncMode {
    /**
     * The item is a template and should use the amount from the current source definition.
     */
    TEMPLATE,
    /**
     * The item is an existing stored instance; preserve its current amount.
     */
    EXISTING_INSTANCE
}
