package cn.mythicland.magicchest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Small, server-version-neutral copy of the player-side chest shift-click merge order.
 */
final class MagicChestInventoryTransfer {

    static final int PLAYER_STORAGE_SIZE = 36;

    /**
     * Paper 1.12.2's ContainerChest adds the player inventory in main-inventory/hotbar order and
     * merges with reverseDirection=true, which produces this exact slot order.
     */
    private static final int[] CHEST_TO_PLAYER_ORDER = {
            8, 7, 6, 5, 4, 3, 2, 1, 0,
            35, 34, 33, 32, 31, 30, 29, 28, 27,
            26, 25, 24, 23, 22, 21, 20, 19, 18,
            17, 16, 15, 14, 13, 12, 11, 10, 9
    };

    private MagicChestInventoryTransfer() {
    }

    /**
     * Moves as much as possible into a 36-slot Bukkit player-storage array.
     *
     * @param storageContents player storage slots 0-35
     * @param incoming        item being shift-clicked from the chest
     * @param inventoryMax    player inventory maximum stack size
     * @return the untransferred remainder, or {@code null}
     */
    static ItemStack moveToPlayer(ItemStack[] storageContents, ItemStack incoming, int inventoryMax) {
        return moveToPlayer(storageContents, incoming, inventoryMax, ItemStack::isSimilar);
    }

    static ItemStack moveToPlayer(
            ItemStack[] storageContents,
            ItemStack incoming,
            int inventoryMax,
            BiPredicate<ItemStack, ItemStack> similar
    ) {
        Objects.requireNonNull(storageContents, "storageContents");
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(similar, "similar");
        if (storageContents.length < PLAYER_STORAGE_SIZE) {
            throw new IllegalArgumentException("storageContents must contain at least 36 slots");
        }
        if (inventoryMax <= 0) throw new IllegalArgumentException("inventoryMax must be positive");

        ItemStack remaining = incoming.clone();
        mergeIntoExisting(storageContents, remaining, inventoryMax, similar);
        mergeIntoEmpty(storageContents, remaining, inventoryMax);
        return isEmpty(remaining) ? null : remaining;
    }

    private static void mergeIntoExisting(
            ItemStack[] storageContents,
            ItemStack remaining,
            int inventoryMax,
            BiPredicate<ItemStack, ItemStack> similar
    ) {
        for (int slot : CHEST_TO_PLAYER_ORDER) {
            if (isEmpty(remaining)) return;
            ItemStack target = storageContents[slot];
            if (isEmpty(target) || !similar.test(target, remaining)) continue;
            int maxAmount = Math.min(inventoryMax, Math.min(target.getMaxStackSize(), remaining.getMaxStackSize()));
            int moved = Math.min(remaining.getAmount(), maxAmount - target.getAmount());
            if (moved <= 0) continue;
            target.setAmount(target.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }
    }

    private static void mergeIntoEmpty(ItemStack[] storageContents, ItemStack remaining, int inventoryMax) {
        for (int slot : CHEST_TO_PLAYER_ORDER) {
            if (isEmpty(remaining)) return;
            if (!isEmpty(storageContents[slot])) continue;
            int maxAmount = Math.min(inventoryMax, remaining.getMaxStackSize());
            int moved = Math.min(remaining.getAmount(), maxAmount);
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            storageContents[slot] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
