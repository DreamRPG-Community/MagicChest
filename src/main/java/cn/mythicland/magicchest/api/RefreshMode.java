package cn.mythicland.magicchest.api;

/**
 * Scheduling mode for one MagicChest.
 */
public enum RefreshMode {
    /** Refreshes after a configured interval. */
    INTERVAL,
    /** Refreshes at a configured local time every day. */
    DAILY,
    /** Keeps refreshing from the template whenever the one-second service tick is due. */
    ALWAYS,
    /** Never refreshes automatically. */
    NEVER
}
