package cn.mythicland.magicchest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the vanilla Paper 1.12.2 chest-to-player shift-click order.
 */
class MagicChestInventoryTransferTest {

    @Test
    void shiftClickUsesTheSameReversePlayerContainerOrderAsVanillaChest() {
        ItemStack[] storage = new ItemStack[41];

        ItemStack leftover = MagicChestInventoryTransfer.moveToPlayer(
                storage,
                new ItemStack(Material.DIAMOND, 3),
                64,
                (first, second) -> first.getType() == second.getType()
        );

        assertNull(leftover);
        assertEquals(Material.DIAMOND, storage[8].getType());
        assertEquals(3, storage[8].getAmount());
        assertNull(storage[0]);
        assertNull(storage[35]);
    }

    @Test
    void shiftClickFillsExistingStacksBeforeTheNextVanillaSlot() {
        ItemStack[] storage = new ItemStack[41];
        storage[8] = new ItemStack(Material.DIAMOND, 63);

        ItemStack leftover = MagicChestInventoryTransfer.moveToPlayer(
                storage,
                new ItemStack(Material.DIAMOND, 3),
                64,
                (first, second) -> first.getType() == second.getType()
        );

        assertNull(leftover);
        assertEquals(64, storage[8].getAmount());
        assertEquals(2, storage[7].getAmount());
    }
}
