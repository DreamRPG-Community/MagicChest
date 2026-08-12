package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread-only mutable state for one MagicChest.
 */
final class MagicChestRecord {

    static final int STORAGE_SIZE = 54;

    private final MagicChestKey key;
    private boolean refreshEnabled;
    private MagicChestSize size;
    private RefreshMode refreshMode;
    private String intervalOption;
    private String dailyOption;
    private String particle;
    private boolean hologramEnabled;
    private boolean editing;
    private UUID editor;
    private long nextRefreshEpochSecond;
    private ItemStack[] template;
    private ItemStack[] draft;
    private ItemStack[] liveContents;

    @SuppressWarnings("SameParameterValue")
    MagicChestRecord(
            MagicChestKey key,
            boolean refreshEnabled,
            MagicChestSize size,
            RefreshMode refreshMode,
            String intervalOption,
            String dailyOption,
            String particle,
            boolean hologramEnabled,
            boolean editing,
            long nextRefreshEpochSecond,
            ItemStack[] template,
            ItemStack[] draft,
            ItemStack[] liveContents
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.refreshEnabled = refreshEnabled;
        this.size = Objects.requireNonNull(size, "size");
        this.refreshMode = Objects.requireNonNull(refreshMode, "refreshMode");
        this.intervalOption = requireOption(intervalOption, "intervalOption");
        this.dailyOption = requireOption(dailyOption, "dailyOption");
        this.particle = Objects.requireNonNull(particle, "particle");
        this.hologramEnabled = hologramEnabled;
        this.editing = editing;
        if (nextRefreshEpochSecond < 0L)
            throw new IllegalArgumentException("nextRefreshEpochSecond cannot be negative");
        this.nextRefreshEpochSecond = nextRefreshEpochSecond;
        this.template = fixedCopy(template, "template");
        this.draft = fixedCopy(draft, "draft");
        this.liveContents = fixedCopy(liveContents, "liveContents");
    }

    @SuppressWarnings("SameParameterValue")
    MagicChestRecord(
            MagicChestKey key,
            MagicChestSize size,
            RefreshMode refreshMode,
            String intervalOption,
            String dailyOption,
            String particle,
            boolean hologramEnabled,
            boolean editing,
            long nextRefreshEpochSecond,
            ItemStack[] template,
            ItemStack[] draft,
            ItemStack[] liveContents
    ) {
        this(
                key,
                true,
                size,
                refreshMode,
                intervalOption,
                dailyOption,
                particle,
                hologramEnabled,
                editing,
                nextRefreshEpochSecond,
                template,
                draft,
                liveContents
        );
    }

    static MagicChestRecord createDefault(
            MagicChestKey key,
            MagicChestSettings.Snapshot settings,
            Instant now
    ) {
        MagicChestRecord record = new MagicChestRecord(
                key,
                false,
                MagicChestSize.SMALL,
                RefreshMode.INTERVAL,
                settings.defaultIntervalOption(),
                settings.defaultDailyOption(),
                settings.defaultParticle(),
                false,
                false,
                0L,
                emptyContents(),
                emptyContents(),
                emptyContents()
        );
        record.recalculateNextRefresh(settings, now);
        return record;
    }

    static ItemStack[] emptyContents() {
        return new ItemStack[STORAGE_SIZE];
    }

    static ItemStack[] fixedCopy(ItemStack[] source, String fieldName) {
        Objects.requireNonNull(source, fieldName);
        if (source.length != STORAGE_SIZE) {
            throw new IllegalArgumentException(fieldName + " must contain exactly " + STORAGE_SIZE + " slots");
        }
        return copyContents(source);
    }

    static ItemStack[] copyContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) copy[index] = cloneItem(source[index]);
        return copy;
    }

    static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static String requireOption(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }

    MagicChestKey key() {
        return key;
    }

    boolean refreshEnabled() {
        return refreshEnabled;
    }

    void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    MagicChestSize size() {
        return size;
    }

    void setSize(MagicChestSize size) {
        this.size = Objects.requireNonNull(size, "size");
    }

    RefreshMode refreshMode() {
        return refreshMode;
    }

    void setRefreshMode(RefreshMode refreshMode) {
        this.refreshMode = Objects.requireNonNull(refreshMode, "refreshMode");
    }

    String intervalOption() {
        return intervalOption;
    }

    void setIntervalOption(String intervalOption) {
        this.intervalOption = requireOption(intervalOption, "intervalOption");
    }

    String dailyOption() {
        return dailyOption;
    }

    void setDailyOption(String dailyOption) {
        this.dailyOption = requireOption(dailyOption, "dailyOption");
    }

    String particle() {
        return particle;
    }

    void setParticle(String particle) {
        this.particle = Objects.requireNonNull(particle, "particle");
    }

    boolean hologramEnabled() {
        return hologramEnabled;
    }

    void setHologramEnabled(boolean hologramEnabled) {
        this.hologramEnabled = hologramEnabled;
    }

    boolean editing() {
        return editing;
    }

    void setEditing(boolean editing) {
        this.editing = editing;
    }

    UUID editor() {
        return editor;
    }

    void setEditor(UUID editor) {
        this.editor = editor;
    }

    long nextRefreshEpochSecond() {
        return nextRefreshEpochSecond;
    }

    @SuppressWarnings("SameParameterValue")
    void setNextRefreshEpochSecond(long nextRefreshEpochSecond) {
        if (nextRefreshEpochSecond < 0L) {
            throw new IllegalArgumentException("nextRefreshEpochSecond cannot be negative");
        }
        this.nextRefreshEpochSecond = nextRefreshEpochSecond;
    }

    RefreshPolicy refreshPolicy(MagicChestSettings.Snapshot settings) {
        Objects.requireNonNull(settings, "settings");
        return switch (refreshMode) {
            case INTERVAL -> {
                Duration duration = intervalOption.equalsIgnoreCase("custom")
                        ? settings.customInterval()
                        : RefreshPolicy.parseInterval(intervalOption);
                yield RefreshPolicy.interval(duration);
            }
            case DAILY -> RefreshPolicy.daily(
                    dailyOption.equalsIgnoreCase("custom")
                            ? settings.customDailyTime()
                            : RefreshPolicy.parseDailyTime(dailyOption)
            );
            case ALWAYS -> RefreshPolicy.always();
            case NEVER -> RefreshPolicy.never();
        };
    }

    void recalculateNextRefresh(MagicChestSettings.Snapshot settings, Instant now) {
        if (refreshMode == RefreshMode.NEVER || !refreshEnabled) {
            nextRefreshEpochSecond = 0L;
            return;
        }
        nextRefreshEpochSecond = refreshPolicy(settings).nextRefresh(now, settings.timeZone()).getEpochSecond();
    }

    boolean isDue(Instant now) {
        return refreshEnabled && nextRefreshEpochSecond > 0L && now.getEpochSecond() >= nextRefreshEpochSecond;
    }

    void refreshNow(MagicChestSettings.Snapshot settings, Instant now) {
        ItemStack[] refreshedContents = copyContents(liveContents);
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            if (!isEmpty(template[slot])) refreshedContents[slot] = cloneItem(template[slot]);
        }
        liveContents = refreshedContents;
        recalculateNextRefresh(settings, now);
    }

    ItemStack[] templateCopy() {
        return copyContents(template);
    }

    void setTemplate(ItemStack[] contents) {
        template = fixedCopy(contents, "template");
    }

    ItemStack[] draftCopy() {
        return copyContents(draft);
    }

    void setDraft(ItemStack[] contents) {
        draft = fixedCopy(contents, "draft");
    }

    void setLiveContents(ItemStack[] contents) {
        liveContents = fixedCopy(contents, "liveContents");
    }

    ItemStack[] liveCopy() {
        return copyContents(liveContents);
    }

    ItemStack liveItem(int slot) {
        checkSlot(slot);
        return cloneItem(liveContents[slot]);
    }

    void setLiveItem(int slot, ItemStack item) {
        checkSlot(slot);
        liveContents[slot] = cloneItem(item);
    }

    boolean hasClaimableItems() {
        for (int slot = 0; slot < size.slots(); slot++) {
            if (!isEmpty(liveContents[slot])) return true;
        }
        return false;
    }

    boolean hasItemsOutside(MagicChestSize candidateSize) {
        for (int slot = candidateSize.slots(); slot < STORAGE_SIZE; slot++) {
            if (!isEmpty(template[slot]) || !isEmpty(draft[slot]) || !isEmpty(liveContents[slot])) return true;
        }
        return false;
    }

    MagicChestSnapshot snapshot(MagicChestSettings.Snapshot settings, int claimViewers) {
        return new MagicChestSnapshot(
                key,
                refreshEnabled,
                size,
                refreshPolicy(settings),
                particle,
                hologramEnabled,
                editing,
                hasClaimableItems(),
                claimViewers,
                nextRefreshEpochSecond
        );
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= STORAGE_SIZE) throw new IndexOutOfBoundsException("slot: " + slot);
    }
}
