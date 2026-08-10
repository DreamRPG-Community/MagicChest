package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.MagicChestKey;
import cn.mythicland.magicchest.api.MagicChestSize;
import cn.mythicland.magicchest.api.RefreshMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers native inventory snapshot reconciliation and private per-view identity.
 */
class MagicChestNativeInventoryTest {

    @Test
    void nativeSnapshotAppliesOnlyChangedTopSlots() {
        MagicChestRecord record = newRecord();
        record.setLiveItem(0, new ItemStack(Material.DIAMOND, 10));

        ItemStack[] baseline = record.liveCopy();
        ItemStack[] current = MagicChestRecord.copyContents(baseline);
        current[0] = null;
        current[27] = new ItemStack(Material.GOLD_INGOT, 1);

        boolean changed = MagicChestService.applyNativeChanges(record, baseline, current);

        assertTrue(changed);
        assertNull(record.liveItem(0));
        assertNull(record.liveItem(1));
        assertEquals(Material.GOLD_INGOT, current[27].getType());
        assertNull(record.liveItem(27));
    }

    @Test
    void eachViewerGetsASeparateInventoryHolder() {
        MagicChestKey key = new MagicChestKey(UUID.randomUUID(), 1, 64, 2);
        MagicChestInventoryHolder first = new MagicChestInventoryHolder(key, UUID.randomUUID(), false);
        MagicChestInventoryHolder second = new MagicChestInventoryHolder(key, UUID.randomUUID(), false);

        assertNotSame(first, second);
        assertNotSame(first.viewerUniqueId(), second.viewerUniqueId());
    }

    private static MagicChestRecord newRecord() {
        return new MagicChestRecord(
                new MagicChestKey(UUID.randomUUID(), 1, 64, 2),
                MagicChestSize.SMALL,
                RefreshMode.INTERVAL,
                "1m",
                "00:00",
                "NONE",
                false,
                false,
                1L,
                MagicChestRecord.emptyContents(),
                MagicChestRecord.emptyContents(),
                MagicChestRecord.emptyContents()
        );
    }
}
