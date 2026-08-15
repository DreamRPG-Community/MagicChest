package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.MagicChestKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers private claim views and per-view inventory identity.
 */
class MagicChestNativeInventoryTest {

    @Test
    void eachClaimViewerReceivesAnIndependentContentsCopy() {
        ItemStack[] source = MagicChestRecord.emptyContents();
        source[0] = new ItemStack(Material.DIAMOND, 10);

        ItemStack[] firstViewer = MagicChestService.viewContents(source, 27);
        ItemStack[] secondViewer = MagicChestService.viewContents(source, 27);
        firstViewer[0].setAmount(1);
        firstViewer[1] = new ItemStack(Material.GOLD_INGOT, 1);

        assertEquals(10, source[0].getAmount());
        assertEquals(10, secondViewer[0].getAmount());
        assertEquals(Material.GOLD_INGOT, firstViewer[1].getType());
        assertNull(secondViewer[1]);
    }

    @Test
    void eachViewerGetsASeparateInventoryHolder() {
        MagicChestKey key = new MagicChestKey(UUID.randomUUID(), 1, 64, 2);
        MagicChestInventoryHolder first = new MagicChestInventoryHolder(key, UUID.randomUUID(), false);
        MagicChestInventoryHolder second = new MagicChestInventoryHolder(key, UUID.randomUUID(), false);

        assertNotSame(first, second);
        assertNotSame(first.viewerUniqueId(), second.viewerUniqueId());
    }
}
