package cn.mythicland.magicchest.api;

/**
 * Virtual inventory sizes supported by MagicChest.
 */
public enum MagicChestSize {
    /**
     * 27-slot small chest.
     */
    SMALL(27),
    /**
     * 54-slot large chest.
     */
    LARGE(54);

    private final int slots;

    MagicChestSize(int slots) {
        this.slots = slots;
    }

    /**
     * Returns the visible virtual inventory size.
     *
     * @return slot count
     */
    public int slots() {
        return slots;
    }
}
