package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.MagicChestKey;
import cn.mythicland.magicchest.api.MagicChestSize;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers fixed storage size, draft separation, immediate refresh, and shrink protection.
 */
class MagicChestRecordTest {

    private static final MagicChestSettings.Snapshot SETTINGS = new MagicChestSettings.Snapshot(
            List.of("1m", "5m", "custom"),
            Duration.ofHours(2),
            List.of("00:00", "13:00", "custom"),
            LocalTime.of(13, 0),
            ZoneId.of("Asia/Shanghai"),
            List.of("NONE", "HEART"),
            32,
            10,
            12,
            32.0D,
            0.25D,
            2.25D
    );

    @Test
    void allPersistentArraysAreExactly54Slots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MagicChestRecord.fixedCopy(new ItemStack[27], "test")
        );
    }

    @Test
    void newlyManagedChestStartsWithRefreshDisabled() {
        MagicChestRecord record = MagicChestRecord.createDefault(
                new MagicChestKey(UUID.randomUUID(), 1, 64, 2),
                SETTINGS,
                Instant.parse("2026-08-08T04:00:00Z")
        );

        assertFalse(record.refreshEnabled());
        assertEquals(0L, record.nextRefreshEpochSecond());
    }

    @Test
    void shrinkingRejectsItemsOutsideTheSmallChest() {
        MagicChestRecord record = newRecord();
        ItemStack item = new ItemStack(Material.DIAMOND, 3);
        ItemStack[] template = record.templateCopy();
        template[30] = item;
        record.setTemplate(template);

        assertTrue(record.hasItemsOutside(MagicChestSize.SMALL));
        record.setSize(MagicChestSize.LARGE);
        assertEquals(MagicChestSize.LARGE, record.size());
    }

    @Test
    void editorDraftBecomesLiveOnlyWhenRefreshIsRequested() {
        MagicChestRecord record = newRecord();
        ItemStack[] draft = record.draftCopy();
        draft[0] = new ItemStack(Material.EMERALD, 2);
        record.setDraft(draft);
        assertNull(record.liveItem(0));

        record.setTemplate(record.draftCopy());
        Instant now = Instant.parse("2026-08-08T04:00:00Z");
        record.refreshNow(SETTINGS, now);

        assertNotNull(record.liveItem(0));
        assertEquals(2, record.liveItem(0).getAmount());
        assertTrue(record.nextRefreshEpochSecond() > now.getEpochSecond());
    }

    @Test
    void refreshReplacesRefreshingSlotsAndPreservesOtherContents() {
        MagicChestRecord record = newRecord();
        ItemStack[] template = record.templateCopy();
        template[0] = new ItemStack(Material.DIAMOND, 2);
        record.setTemplate(template);
        record.setLiveItem(0, new ItemStack(Material.DIRT, 64));
        record.setLiveItem(1, new ItemStack(Material.EMERALD, 1));

        record.refreshNow(SETTINGS, Instant.parse("2026-08-08T04:00:00Z"));

        assertEquals(Material.DIAMOND, record.liveItem(0).getType());
        assertEquals(2, record.liveItem(0).getAmount());
        assertEquals(Material.EMERALD, record.liveItem(1).getType());
        assertEquals(1, record.liveItem(1).getAmount());
    }

    @Test
    void liveCopiesAreDetachedSoTwoViewsCannotWriteStaleContentsBack() {
        MagicChestRecord record = newRecord();
        record.setLiveItem(0, new ItemStack(Material.GOLD_INGOT, 10));
        ItemStack firstView = record.liveItem(0);
        ItemStack secondView = record.liveItem(0);

        firstView.setAmount(4);
        record.setLiveItem(0, firstView);
        secondView.setAmount(10);

        assertEquals(4, record.liveItem(0).getAmount());
    }

    @Test
    void refreshReplacesOccupiedSlotsButPreservesPlayerItemsInEmptySlots() {
        MagicChestRecord record = newRecord();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ItemStack[] template = record.templateCopy();
        template[0] = new ItemStack(Material.DIAMOND, 10);
        record.setTemplate(template);
        record.setLiveItem(0, new ItemStack(Material.DIRT, 64));

        ItemStack[] firstContents = record.liveCopy();
        firstContents[0] = new ItemStack(Material.GOLD_INGOT, 2);
        firstContents[5] = new ItemStack(Material.EMERALD, 3);
        record.setPlayerContents(firstPlayer, firstContents);

        assertNull(record.playerContentsCopy(secondPlayer));
        assertEquals(Material.GOLD_INGOT, record.playerContentsCopy(firstPlayer)[0].getType());
        assertEquals(Material.EMERALD, record.playerContentsCopy(firstPlayer)[5].getType());

        record.refreshNow(SETTINGS, Instant.parse("2026-08-08T04:00:00Z"));

        ItemStack[] refreshedPlayerContents = record.playerContentsCopy(firstPlayer);
        assertEquals(Material.DIAMOND, refreshedPlayerContents[0].getType());
        assertEquals(10, refreshedPlayerContents[0].getAmount());
        assertEquals(Material.EMERALD, refreshedPlayerContents[5].getType());
        assertEquals(3, refreshedPlayerContents[5].getAmount());
        assertEquals(10, record.liveItem(0).getAmount());
    }

    @Test
    void twoPlayersKeepSeparateClaimProgress() {
        MagicChestRecord record = newRecord();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        record.setLiveItem(0, new ItemStack(Material.DIAMOND, 10));

        ItemStack[] firstContents = record.liveCopy();
        firstContents[0] = null;
        record.setPlayerContents(firstPlayer, firstContents);

        ItemStack[] secondContents = record.liveCopy();
        secondContents[0].setAmount(4);
        record.setPlayerContents(secondPlayer, secondContents);

        assertNull(record.playerContentsCopy(firstPlayer)[0]);
        assertEquals(4, record.playerContentsCopy(secondPlayer)[0].getAmount());
        assertEquals(10, record.liveItem(0).getAmount());
    }

    @Test
    void neverModeDoesNotScheduleAutomaticRefresh() {
        MagicChestRecord record = new MagicChestRecord(
                new MagicChestKey(UUID.randomUUID(), 1, 64, 2),
                MagicChestSize.SMALL,
                cn.mythicland.magicchest.api.RefreshMode.NEVER,
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
        Instant now = Instant.parse("2026-08-08T04:00:00Z");

        record.recalculateNextRefresh(SETTINGS, now);

        assertEquals(0L, record.nextRefreshEpochSecond());
        assertFalse(record.isDue(now.plus(Duration.ofDays(1))));
    }

    @Test
    void neverModeIgnoresAStalePersistedNextRefreshTime() {
        MagicChestRecord record = new MagicChestRecord(
                new MagicChestKey(UUID.randomUUID(), 1, 64, 2),
                true,
                MagicChestSize.SMALL,
                cn.mythicland.magicchest.api.RefreshMode.NEVER,
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

        assertFalse(record.isDue(Instant.parse("2026-08-08T04:00:00Z")));
    }

    @Test
    void disabledRecordDoesNotScheduleAutomaticRefresh() {
        MagicChestRecord record = newRecord();
        record.setRefreshEnabled(false);

        record.recalculateNextRefresh(SETTINGS, Instant.parse("2026-08-08T04:00:00Z"));

        assertEquals(0L, record.nextRefreshEpochSecond());
        assertFalse(record.isDue(Instant.parse("2026-08-08T05:00:00Z")));
    }

    @Test
    void disablingRefreshKeepsTheExistingChestConfiguration() {
        MagicChestRecord record = newRecord();
        ItemStack[] template = record.templateCopy();
        template[0] = new ItemStack(Material.DIAMOND, 4);
        record.setTemplate(template);
        record.setSize(MagicChestSize.LARGE);
        record.setRefreshMode(cn.mythicland.magicchest.api.RefreshMode.DAILY);
        record.setIntervalOption("custom");
        record.setDailyOption("13:00");
        record.setParticle("HEART");
        record.setHologramEnabled(true);
        record.setRefreshEnabled(false);

        assertEquals(MagicChestSize.LARGE, record.size());
        assertEquals(cn.mythicland.magicchest.api.RefreshMode.DAILY, record.refreshMode());
        assertEquals("custom", record.intervalOption());
        assertEquals("13:00", record.dailyOption());
        assertEquals("HEART", record.particle());
        assertTrue(record.hologramEnabled());
        assertEquals(Material.DIAMOND, record.templateCopy()[0].getType());
        assertEquals(4, record.templateCopy()[0].getAmount());
    }

    private static MagicChestRecord newRecord() {
        return new MagicChestRecord(
                new MagicChestKey(UUID.randomUUID(), 1, 64, 2),
                MagicChestSize.SMALL,
                cn.mythicland.magicchest.api.RefreshMode.INTERVAL,
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
