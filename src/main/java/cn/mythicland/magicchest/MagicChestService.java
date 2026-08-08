package cn.mythicland.magicchest;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.lib.container.ContainerAnimationService;
import cn.mythicland.lib.container.ContainerAnimationSpec;
import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.lib.text.FloatingTextHandle;
import cn.mythicland.lib.text.FloatingTextService;
import cn.mythicland.lib.text.FloatingTextSpec;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.magicchest.api.MagicChestApi;
import cn.mythicland.magicchest.api.MagicChestKey;
import cn.mythicland.magicchest.api.MagicChestSize;
import cn.mythicland.magicchest.api.MagicChestSnapshot;
import cn.mythicland.magicchest.api.RefreshMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main-thread MagicChest domain service.
 */
@InjectComponent
@ListenerComponent
@ServiceComponent(MagicChestApi.class)
public final class MagicChestService implements MagicChestApi, Listener, LibPluginLifecycle {

    private static final String ADMIN_PERMISSION = "magicchest.admin";
    private static final String RELOAD_PERMISSION = "magicchest.reload";

    private final LibApi lib;
    private final MenuService menus;
    private final ContainerAnimationService animations;
    private final FloatingTextService floatingText;
    private final MagicChestSettings settings;
    private final MagicChestStore store;
    private final Logger logger;
    private final Map<UUID, MagicChestKey> openSessions = new HashMap<>();
    private final Map<MagicChestKey, Set<UUID>> managementViewers = new HashMap<>();
    private final Map<MagicChestKey, Set<UUID>> claimViewers = new HashMap<>();
    private final Map<MagicChestKey, FloatingTextHandle> holograms = new HashMap<>();
    private final Map<MagicChestKey, FloatingTextSpec> hologramSpecifications = new HashMap<>();
    private final Map<MagicChestKey, Location> hologramLocations = new HashMap<>();
    private BukkitTask tickTask;
    private BukkitTask particleTask;

    /**
     * Creates the injected MagicChest service.
     */
    MagicChestService(
            LibApi lib,
            MenuService menus,
            ContainerAnimationService animations,
            FloatingTextService floatingText,
            MagicChestSettings settings,
            MagicChestStore store,
            Logger logger
    ) {
        this.lib = Objects.requireNonNull(lib, "lib");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.floatingText = Objects.requireNonNull(floatingText, "floatingText");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static int halfAmount(ItemStack item) {
        return MagicChestRecord.isEmpty(item) ? 1 : Math.max(1, (item.getAmount() + 1) / 2);
    }

    private static ItemStack addToPlayer(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] before = MagicChestRecord.copyContents(contents);
        ItemStack leftover = MagicChestInventoryTransfer.moveToPlayer(
                contents,
                item,
                inventory.getMaxStackSize()
        );
        for (int slot = 0; slot < MagicChestInventoryTransfer.PLAYER_STORAGE_SIZE; slot++) {
            if (!sameStack(before[slot], contents[slot])) inventory.setItem(slot, contents[slot]);
        }
        return leftover;
    }

    private static ItemStack insertIntoChest(MagicChestRecord record, ItemStack incoming) {
        ItemStack remaining = incoming.clone();
        for (int slot = 0; slot < record.size().slots() && !MagicChestRecord.isEmpty(remaining); slot++) {
            ItemStack target = record.liveItem(slot);
            if (MagicChestRecord.isEmpty(target) || !target.isSimilar(remaining)) continue;
            int capacity = Math.max(0, target.getMaxStackSize() - target.getAmount());
            int moved = Math.min(capacity, remaining.getAmount());
            if (moved <= 0) continue;
            target.setAmount(target.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            record.setLiveItem(slot, target);
        }
        for (int slot = 0; slot < record.size().slots() && !MagicChestRecord.isEmpty(remaining); slot++) {
            if (!MagicChestRecord.isEmpty(record.liveItem(slot))) continue;
            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            remaining.setAmount(remaining.getAmount() - moved);
            record.setLiveItem(slot, placed);
        }
        return cloneOrNull(remaining);
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        if (MagicChestRecord.isEmpty(first) || MagicChestRecord.isEmpty(second)) {
            return MagicChestRecord.isEmpty(first) && MagicChestRecord.isEmpty(second);
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return MagicChestRecord.isEmpty(item) ? null : item.clone();
    }

    private static int clampRequestedAmount(int requested, int available) {
        if (available <= 0) return 0;
        return Math.clamp(requested, 1, available);
    }

    private static boolean sameAnchor(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().getUID().equals(second.getWorld().getUID())
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }

    private static String formatRemaining(MagicChestRecord record, Instant now) {
        if (record.refreshMode() == RefreshMode.ALWAYS) return "始终刷新";
        if (record.refreshMode() == RefreshMode.NEVER) return "永不刷新";
        long seconds = Math.max(0L, record.nextRefreshEpochSecond() - now.getEpochSecond());
        Duration duration = Duration.ofSeconds(seconds);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long remainingSeconds = duration.toSecondsPart();
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    private static String cycle(List<String> options, String current, boolean next) {
        int index = options.indexOf(current);
        if (index < 0) index = 0;
        int direction = next ? 1 : options.size() - 1;
        return options.get((index + direction) % options.size());
    }

    private static ItemStack[] capture(org.bukkit.inventory.Inventory inventory) {
        ItemStack[] result = MagicChestRecord.emptyContents();
        ItemStack[] contents = inventory.getContents();
        if (contents.length > MagicChestRecord.STORAGE_SIZE) {
            throw new IllegalStateException("MagicChest editor inventory exceeds 54 slots");
        }
        for (int index = 0; index < contents.length; index++)
            result[index] = contents[index] == null ? null : contents[index].clone();
        return result;
    }

    static boolean isChest(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    /**
     * Returns the reload permission used by the command component.
     */
    static String reloadPermission() {
        return RELOAD_PERMISSION;
    }

    @Override
    public void enable() {
        animations.verifyCompatibility();
        floatingText.verifyCompatibility();
        for (MagicChestRecord record : store.all()) {
            record.refreshPolicy(settings.snapshot());
            if (record.nextRefreshEpochSecond() == 0L) {
                record.recalculateNextRefresh(settings.snapshot(), Instant.now());
            }
        }
        tickTask = lib.runTimer(1L, 20L, this::tick);
        particleTask = lib.runTimer(1L, settings.snapshot().particleIntervalTicks(), this::renderParticles);
        tick();
        renderParticles();
    }

    @Override
    public void reload() {
        settings.reload();
        for (MagicChestRecord record : store.all()) record.refreshPolicy(settings.snapshot());
        if (particleTask != null) particleTask.cancel();
        particleTask = lib.runTimer(1L, settings.snapshot().particleIntervalTicks(), this::renderParticles);
        tick();
        renderParticles();
    }

    @Override
    public void disable() {
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        if (particleTask != null) particleTask.cancel();
        particleTask = null;
        for (UUID playerUniqueId : List.copyOf(openSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null) menus.close(player);
        }
        openSessions.clear();
        managementViewers.clear();
        claimViewers.clear();
        for (FloatingTextHandle handle : List.copyOf(holograms.values())) handle.close();
        holograms.clear();
        hologramSpecifications.clear();
        hologramLocations.clear();
        store.close();
    }

    @Override
    public Optional<MagicChestSnapshot> find(MagicChestKey key) {
        Objects.requireNonNull(key, "key");
        MagicChestRecord record = store.find(key);
        return record == null
                ? Optional.empty()
                : Optional.of(record.snapshot(settings.snapshot(), claimViewerCount(key)));
    }

    @Override
    public Collection<MagicChestSnapshot> snapshots() {
        return store.all().stream()
                .sorted(Comparator.comparing(record -> record.key().encoded()))
                .map(record -> record.snapshot(settings.snapshot(), claimViewerCount(record.key())))
                .toList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (!isChest(block)) return;
        Player player = event.getPlayer();
        boolean admin = player.hasPermission(ADMIN_PERMISSION);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (admin && player.isSneaking()) {
            event.setCancelled(true);
            openManagement(player, block);
            return;
        }
        MagicChestRecord record = store.find(MagicChestKey.from(block));
        if (record == null || !record.refreshEnabled()) return;
        event.setCancelled(true);
        if (record.editing()) {
            if (!admin) {
                player.sendMessage(LegacyText.colorize("&c该箱子正在编辑, 暂时无法打开。"));
                return;
            }
            openEditor(player, record.key(), block);
            return;
        }
        openClaim(player, block, record);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (openSessions.containsKey(event.getPlayer().getUniqueId())) menus.close(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        removeIfManaged(MagicChestKey.from(event.getBlock()), "箱子被破坏, MagicChest 管理记录已删除。", event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : List.copyOf(event.blockList())) {
            removeIfManaged(MagicChestKey.from(block), "箱子被爆炸破坏, MagicChest 管理记录已删除。", null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            removeIfManaged(MagicChestKey.from(block), "箱子被活塞移动, MagicChest 管理记录已删除。", null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            removeIfManaged(MagicChestKey.from(block), "箱子被活塞移动, MagicChest 管理记录已删除。", null);
        }
    }

    void openManagement(Player player, Block sourceBlock) {
        requireAdmin(player);
        MagicChestKey key = MagicChestKey.from(sourceBlock);
        MagicChestRecord record = store.find(key);
        if (record == null) {
            record = MagicChestRecord.createDefault(key, settings.snapshot(), Instant.now());
            store.put(record);
            persist();
        }
        MagicChestManagementMenu menu = new MagicChestManagementMenu(this, key);
        try {
            menus.open(player, menu);
            openSessions.put(player.getUniqueId(), key);
            managementViewers.computeIfAbsent(key, ignored -> new HashSet<>()).add(player.getUniqueId());
            menu.attachAnimation(animations.open(sourceBlock, player, ContainerAnimationSpec.enderChest()));
        } catch (RuntimeException exception) {
            menus.close(player);
            logger.log(Level.SEVERE, "Failed to open MagicChest management menu", exception);
            player.sendMessage(LegacyText.colorize("&c箱子管理面板打开失败, 请查看服务端日志。"));
        }
    }

    private void openClaim(Player player, Block sourceBlock, MagicChestRecord record) {
        MagicChestKey key = record.key();
        MagicChestClaimMenu menu = new MagicChestClaimMenu(this, key);
        try {
            menus.open(player, menu);
            openSessions.put(player.getUniqueId(), key);
            claimViewers.computeIfAbsent(key, ignored -> new HashSet<>()).add(player.getUniqueId());
            menu.attachAnimation(animations.open(sourceBlock, player, ContainerAnimationSpec.enderChest()));
        } catch (RuntimeException exception) {
            menus.close(player);
            logger.log(Level.SEVERE, "Failed to open MagicChest claim menu", exception);
            player.sendMessage(LegacyText.colorize("&c箱子打开失败, 请查看服务端日志。"));
        }
    }

    void openEditor(Player player, MagicChestKey key, Block sourceBlock) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        if (!record.refreshEnabled()) {
            player.sendMessage(LegacyText.colorize("&c请先启用箱子刷新, 再编辑虚拟箱子内容。"));
            return;
        }
        if (!record.editing()) {
            player.sendMessage(LegacyText.colorize("&c请先在箱子管理面板中进入编辑模式。"));
            return;
        }
        if (!activeClaimViewers(key).isEmpty()) {
            player.sendMessage(LegacyText.colorize("&c还有玩家正在领取, 请等待他们关闭箱子后再编辑。"));
            return;
        }
        if (record.editor() != null && !record.editor().equals(player.getUniqueId())) {
            player.sendMessage(LegacyText.colorize("&c另一名管理员正在编辑这个箱子。"));
            return;
        }
        record.setEditor(player.getUniqueId());
        persist();
        MagicChestEditorMenu menu = new MagicChestEditorMenu(this, key);
        try {
            menus.open(player, menu);
            openSessions.put(player.getUniqueId(), key);
            menu.attachAnimation(animations.open(sourceBlock, player, ContainerAnimationSpec.enderChest()));
        } catch (RuntimeException exception) {
            menus.close(player);
            record.setEditor(null);
            persist();
            logger.log(Level.SEVERE, "Failed to open MagicChest editor", exception);
            player.sendMessage(LegacyText.colorize("&c箱子编辑器打开失败, 请查看服务端日志。"));
        }
    }

    void enterEditing(Player player, MagicChestKey key) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        if (!record.refreshEnabled()) {
            player.sendMessage(LegacyText.colorize("&c请先启用箱子刷新, 再编辑虚拟箱子内容。"));
            return;
        }
        if (record.editing()) return;
        if (!activeClaimViewers(key).isEmpty()) {
            player.sendMessage(LegacyText.colorize("&c还有玩家正在领取, 请等待他们关闭箱子后再编辑。"));
            return;
        }
        record.setDraft(record.templateCopy());
        record.setEditing(true);
        persist();
        refreshManagementMenus(key);
        player.sendMessage(LegacyText.colorize("&a已进入编辑模式。"));
    }

    void exitEditing(Player player, MagicChestKey key) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        if (!record.editing()) return;
        if (record.editor() != null) {
            player.sendMessage(LegacyText.colorize("&c请先关闭编辑库存, 再退出编辑模式。"));
            return;
        }
        if (!activeClaimViewers(key).isEmpty()) {
            player.sendMessage(LegacyText.colorize("&c还有玩家正在领取, 无法完成编辑。"));
            return;
        }
        record.setTemplate(record.draftCopy());
        record.setEditing(false);
        record.refreshNow(settings.snapshot(), Instant.now());
        persist();
        updateDisplay(record, Instant.now());
        refreshManagementMenus(key);
        player.sendMessage(LegacyText.colorize("&a已退出编辑模式。"));
    }

    void toggleSize(Player player, MagicChestKey key) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        MagicChestSize candidate = record.size() == MagicChestSize.SMALL
                ? MagicChestSize.LARGE
                : MagicChestSize.SMALL;
        if (candidate == MagicChestSize.SMALL && record.hasItemsOutside(candidate)) {
            player.sendMessage(LegacyText.colorize("&c大型箱子内第 28-54 格仍有物品, 无法切换。"));
            return;
        }
        record.setSize(candidate);
        persist();
        menus.refresh(player);
    }

    void cycleRefreshMode(Player player, MagicChestKey key, boolean next) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        RefreshMode[] modes = RefreshMode.values();
        int index = (record.refreshMode().ordinal() + (next ? 1 : modes.length - 1)) % modes.length;
        record.setRefreshMode(modes[index]);
        record.recalculateNextRefresh(settings.snapshot(), Instant.now());
        persist();
        refreshManagementMenus(key);
    }

    void toggleRefreshEnabled(Player player, MagicChestKey key) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        boolean enabled = !record.refreshEnabled();
        Instant now = Instant.now();
        record.setRefreshEnabled(enabled);
        if (!enabled) {
            for (UUID playerUniqueId : List.copyOf(activeClaimViewers(key))) {
                Player viewer = Bukkit.getPlayer(playerUniqueId);
                if (viewer != null) menus.close(viewer);
            }
            claimViewers.remove(key);
            UUID editor = record.editor();
            if (editor != null) {
                Player editorPlayer = Bukkit.getPlayer(editor);
                if (editorPlayer != null) menus.close(editorPlayer);
            }
            record.setEditing(false);
            record.setEditor(null);
            record.setNextRefreshEpochSecond(0L);
            closeHologram(key);
        } else {
            record.recalculateNextRefresh(settings.snapshot(), now);
            if (record.refreshMode() == RefreshMode.ALWAYS) record.refreshNow(settings.snapshot(), now);
        }
        persist();
        updateDisplay(record, now);
        refreshManagementMenus(key);
    }

    void cycleInterval(Player player, MagicChestKey key, boolean next) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        MagicChestSettings.Snapshot configuration = settings.snapshot();
        String option = cycle(configuration.intervalOptions(), record.intervalOption(), next);
        record.setIntervalOption(option);
        record.recalculateNextRefresh(configuration, Instant.now());
        persist();
        refreshManagementMenus(key);
    }

    void cycleDaily(Player player, MagicChestKey key, boolean next) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        MagicChestSettings.Snapshot configuration = settings.snapshot();
        String option = cycle(configuration.dailyOptions(), record.dailyOption(), next);
        record.setDailyOption(option);
        record.recalculateNextRefresh(configuration, Instant.now());
        persist();
        refreshManagementMenus(key);
    }

    void cycleParticle(Player player, MagicChestKey key, boolean next) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        List<String> options = settings.snapshot().particleOptions();
        int current = Math.max(0, options.indexOf(record.particle()));
        int index = (current + (next ? 1 : options.size() - 1)) % options.size();
        record.setParticle(options.get(index));
        persist();
        refreshManagementMenus(key);
    }

    void toggleHologram(Player player, MagicChestKey key) {
        requireAdmin(player);
        MagicChestRecord record = requireRecord(key);
        record.setHologramEnabled(!record.hologramEnabled());
        persist();
        updateDisplay(record, Instant.now());
        refreshManagementMenus(key);
    }

    ItemStack[] liveContents(MagicChestKey key) {
        return requireRecord(key).liveCopy();
    }

    MagicChestRecord recordForMenu(MagicChestKey key) {
        return requireRecord(key);
    }

    MagicChestSettings.Snapshot settingsForMenu() {
        return settings.snapshot();
    }

    int claimViewerCountForMenu(MagicChestKey key) {
        return claimViewerCount(key);
    }

    /**
     * Applies one Bukkit click to the authoritative shared virtual inventory.
     *
     * <p>Lib cancels menu clicks before dispatching them here. Top-inventory actions are therefore
     * applied explicitly, while ordinary clicks in the player's own inventory are released back
     * to Bukkit. This keeps every viewer connected to one main-thread inventory without copying a
     * stale view back over another viewer's changes.</p>
     */
    void handleClaimClick(Player player, MagicChestKey key, InventoryClickEvent event) {
        MagicChestRecord record = requireRecord(key);
        InventoryAction action = event.getAction();
        if (action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR) {
            if (dropCursor(player, action == InventoryAction.DROP_ONE_CURSOR)) commitClaimMutation(record, key);
            return;
        }
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            if (collectToCursor(player, record)) commitClaimMutation(record, key);
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= 0 && rawSlot < topSize) {
            if (applyTopClick(player, record, event)) commitClaimMutation(record, key);
            return;
        }
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (moveBottomToChest(record, event)) commitClaimMutation(record, key);
            return;
        }

        // The click only concerns the player's inventory, so restore normal Bukkit handling.
        event.setCancelled(false);
    }

    /**
     * Applies a drag which touches the virtual chest. A drag confined to the player's inventory is
     * released to Bukkit unchanged.
     */
    void handleClaimDrag(Player player, MagicChestKey key, InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = false;
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize) {
                touchesTop = true;
                break;
            }
        }
        if (!touchesTop) {
            event.setCancelled(false);
            return;
        }

        MagicChestRecord record = requireRecord(key);
        boolean changed = false;
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            ItemStack item = cloneOrNull(entry.getValue());
            if (rawSlot >= 0 && rawSlot < topSize && rawSlot < record.size().slots()) {
                record.setLiveItem(rawSlot, item);
                changed = true;
            } else if (rawSlot >= topSize) {
                event.getView().setItem(rawSlot, item);
            }
        }
        player.setItemOnCursor(cloneOrNull(event.getCursor()));
        if (changed) commitClaimMutation(record, key);
        else player.updateInventory();
    }

    private boolean applyTopClick(Player player, MagicChestRecord record, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot < 0 || slot >= record.size().slots()) return false;
        return switch (event.getAction()) {
            case PICKUP_ALL -> pickupFromChest(player, record, slot, Integer.MAX_VALUE);
            case PICKUP_SOME, PICKUP_HALF -> pickupFromChest(player, record, slot, halfAmount(record.liveItem(slot)));
            case PICKUP_ONE -> pickupFromChest(player, record, slot, 1);
            case PLACE_ALL, PLACE_SOME -> placeInChest(player, record, slot, Integer.MAX_VALUE);
            case PLACE_ONE -> placeInChest(player, record, slot, 1);
            case SWAP_WITH_CURSOR -> swapWithCursor(player, record, slot);
            case DROP_ALL_SLOT -> dropFromChest(player, record, slot, Integer.MAX_VALUE);
            case DROP_ONE_SLOT -> dropFromChest(player, record, slot, 1);
            case MOVE_TO_OTHER_INVENTORY -> moveTopToPlayer(player, record, slot);
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> swapWithHotbar(player, record, slot, event.getHotbarButton());
            case CLONE_STACK -> cloneChestStack(player, record, slot);
            default -> false;
        };
    }

    private boolean pickupFromChest(Player player, MagicChestRecord record, int slot, int requested) {
        if (!MagicChestRecord.isEmpty(player.getItemOnCursor())) return false;
        ItemStack item = record.liveItem(slot);
        if (MagicChestRecord.isEmpty(item)) return false;
        int amount = clampRequestedAmount(requested, item.getAmount());
        ItemStack taken = item.clone();
        taken.setAmount(amount);
        item.setAmount(item.getAmount() - amount);
        player.setItemOnCursor(taken);
        record.setLiveItem(slot, item);
        return true;
    }

    private boolean placeInChest(Player player, MagicChestRecord record, int slot, int requested) {
        ItemStack cursor = player.getItemOnCursor();
        if (MagicChestRecord.isEmpty(cursor)) return false;
        ItemStack target = record.liveItem(slot);
        int amount;
        if (MagicChestRecord.isEmpty(target)) {
            amount = clampRequestedAmount(requested, cursor.getAmount());
            ItemStack placed = cursor.clone();
            placed.setAmount(amount);
            record.setLiveItem(slot, placed);
        } else {
            if (!target.isSimilar(cursor)) return false;
            int capacity = Math.max(0, target.getMaxStackSize() - target.getAmount());
            amount = clampRequestedAmount(requested, capacity);
            if (amount <= 0) return false;
            target.setAmount(target.getAmount() + amount);
            record.setLiveItem(slot, target);
        }
        cursor.setAmount(cursor.getAmount() - amount);
        player.setItemOnCursor(cursor);
        return true;
    }

    private boolean swapWithCursor(Player player, MagicChestRecord record, int slot) {
        ItemStack cursor = player.getItemOnCursor();
        ItemStack target = record.liveItem(slot);
        if (MagicChestRecord.isEmpty(cursor) && MagicChestRecord.isEmpty(target)) return false;
        record.setLiveItem(slot, cursor);
        player.setItemOnCursor(target);
        return true;
    }

    private boolean moveTopToPlayer(Player player, MagicChestRecord record, int slot) {
        ItemStack item = record.liveItem(slot);
        if (MagicChestRecord.isEmpty(item)) return false;
        ItemStack leftover = addToPlayer(player, item);
        if (sameStack(item, leftover)) return false;
        record.setLiveItem(slot, leftover);
        return true;
    }

    private boolean moveBottomToChest(MagicChestRecord record, InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != event.getView().getBottomInventory()) return false;
        int slot = event.getSlot();
        if (slot < 0 || slot >= clicked.getSize()) return false;
        ItemStack item = clicked.getItem(slot);
        if (MagicChestRecord.isEmpty(item)) return false;
        ItemStack leftover = insertIntoChest(record, item);
        if (sameStack(item, leftover)) return false;
        clicked.setItem(slot, leftover);
        return true;
    }

    private boolean swapWithHotbar(Player player, MagicChestRecord record, int slot, int hotbarButton) {
        if (hotbarButton < 0 || hotbarButton > 8) return false;
        PlayerInventory inventory = player.getInventory();
        ItemStack chest = record.liveItem(slot);
        ItemStack hotbar = inventory.getItem(hotbarButton);
        record.setLiveItem(slot, hotbar);
        inventory.setItem(hotbarButton, chest);
        return !sameStack(chest, hotbar);
    }

    private boolean cloneChestStack(Player player, MagicChestRecord record, int slot) {
        ItemStack item = record.liveItem(slot);
        if (MagicChestRecord.isEmpty(item)) return false;
        ItemStack copy = item.clone();
        copy.setAmount(copy.getMaxStackSize());
        player.setItemOnCursor(copy);
        return true;
    }

    private boolean dropFromChest(Player player, MagicChestRecord record, int slot, int requested) {
        ItemStack item = record.liveItem(slot);
        if (MagicChestRecord.isEmpty(item)) return false;
        int amount = clampRequestedAmount(requested, item.getAmount());
        ItemStack dropped = item.clone();
        dropped.setAmount(amount);
        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        item.setAmount(item.getAmount() - amount);
        record.setLiveItem(slot, item);
        return true;
    }

    private boolean dropCursor(Player player, boolean one) {
        ItemStack cursor = player.getItemOnCursor();
        if (MagicChestRecord.isEmpty(cursor)) return false;
        int amount = one ? 1 : cursor.getAmount();
        ItemStack dropped = cursor.clone();
        dropped.setAmount(amount);
        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        cursor.setAmount(cursor.getAmount() - amount);
        player.setItemOnCursor(cursor);
        return true;
    }

    private boolean collectToCursor(Player player, MagicChestRecord record) {
        ItemStack cursor = player.getItemOnCursor();
        if (MagicChestRecord.isEmpty(cursor)) return false;
        int remaining = cursor.getMaxStackSize() - cursor.getAmount();
        if (remaining <= 0) return false;
        ItemStack target = cursor.clone();
        boolean changed = false;
        for (int slot = 0; slot < record.size().slots() && remaining > 0; slot++) {
            ItemStack item = record.liveItem(slot);
            if (MagicChestRecord.isEmpty(item) || !item.isSimilar(target)) continue;
            int taken = Math.min(remaining, item.getAmount());
            target.setAmount(target.getAmount() + taken);
            item.setAmount(item.getAmount() - taken);
            record.setLiveItem(slot, item);
            remaining -= taken;
            changed = true;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (MagicChestRecord.isEmpty(item) || !item.isSimilar(target)) continue;
            int taken = Math.min(remaining, item.getAmount());
            target.setAmount(target.getAmount() + taken);
            item.setAmount(item.getAmount() - taken);
            inventory.setItem(slot, item);
            remaining -= taken;
            changed = true;
        }
        if (changed) player.setItemOnCursor(target);
        return changed;
    }

    private void commitClaimMutation(MagicChestRecord record, MagicChestKey key) {
        persist();
        refreshClaimMenus(key);
        updateDisplay(record, Instant.now());
    }

    void onManagementClosed(Player player, MagicChestKey key) {
        closeSession(player, key);
        Set<UUID> viewers = managementViewers.get(key);
        if (viewers != null) {
            viewers.remove(player.getUniqueId());
            if (viewers.isEmpty()) managementViewers.remove(key);
        }
    }

    void onClaimClosed(Player player, MagicChestKey key) {
        closeSession(player, key);
        Set<UUID> viewers = claimViewers.get(key);
        if (viewers != null) {
            viewers.remove(player.getUniqueId());
            if (viewers.isEmpty()) claimViewers.remove(key);
        }
        MagicChestRecord record = store.find(key);
        if (record != null && viewersAreEmpty(key) && !record.editing() && record.isDue(Instant.now())) {
            record.refreshNow(settings.snapshot(), Instant.now());
            persist();
            refreshClaimMenus(key);
            updateDisplay(record, Instant.now());
        }
    }

    void onEditorClosed(Player player, MagicChestKey key, org.bukkit.inventory.Inventory inventory) {
        closeSession(player, key);
        MagicChestRecord record = store.find(key);
        if (record == null) return;
        if (player.getUniqueId().equals(record.editor())) {
            record.setDraft(capture(inventory));
            record.setEditor(null);
            persist();
            refreshManagementMenus(key);
        }
    }

    private void closeSession(Player player, MagicChestKey key) {
        openSessions.remove(player.getUniqueId(), key);
    }

    private void refreshManagementMenus(MagicChestKey key) {
        for (UUID playerUniqueId : List.copyOf(managementViewers.getOrDefault(key, Set.of()))) {
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null) menus.refresh(player);
        }
    }

    private void refreshClaimMenus(MagicChestKey key) {
        for (UUID playerUniqueId : List.copyOf(activeClaimViewers(key))) {
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null) menus.refresh(player);
        }
    }

    private Set<UUID> activeClaimViewers(MagicChestKey key) {
        return claimViewers.getOrDefault(key, Set.of());
    }

    private int claimViewerCount(MagicChestKey key) {
        return activeClaimViewers(key).size();
    }

    private boolean viewersAreEmpty(MagicChestKey key) {
        return activeClaimViewers(key).isEmpty();
    }

    private void tick() {
        Instant now = Instant.now();
        boolean changed = false;
        for (MagicChestRecord record : store.all()) {
            if (record.refreshEnabled() && record.isDue(now) && !record.editing() && viewersAreEmpty(record.key())) {
                record.refreshNow(settings.snapshot(), now);
                changed = true;
            }
            updateDisplay(record, now);
        }
        if (changed) persist();
    }

    private void renderParticles() {
        MagicChestSettings.Snapshot configuration = settings.snapshot();
        for (MagicChestRecord record : store.all()) {
            if (!record.refreshEnabled() || !record.hasClaimableItems() || record.particle().equalsIgnoreCase("NONE"))
                continue;
            Particle particle;
            try {
                particle = Particle.valueOf(record.particle());
            } catch (IllegalArgumentException exception) {
                logger.log(Level.WARNING, "Ignoring invalid MagicChest particle: " + record.particle(), exception);
                continue;
            }
            World world = Bukkit.getWorld(record.key().worldId());
            if (world == null || !world.isChunkLoaded(record.key().x() >> 4, record.key().z() >> 4)) continue;
            Location center = new Location(
                    world,
                    record.key().x() + 0.5D,
                    record.key().y() + 0.5D,
                    record.key().z() + 0.5D
            );
            List<MagicChestParticleGeometry.ParticlePoint> particlePoints = MagicChestParticleGeometry.randomAroundChest(
                    record.key().x(),
                    record.key().y(),
                    record.key().z(),
                    configuration.particleCount(),
                    ThreadLocalRandom.current()
            );
            double distanceSquared = (double) configuration.particleRenderDistance()
                    * configuration.particleRenderDistance();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) <= distanceSquared) {
                    for (MagicChestParticleGeometry.ParticlePoint point : particlePoints) {
                        player.spawnParticle(
                                particle,
                                point.x(),
                                point.y(),
                                point.z(),
                                1,
                                0.0D,
                                0.0D,
                                0.0D,
                                0.0D
                        );
                    }
                }
            }
        }
    }

    private void updateDisplay(MagicChestRecord record, Instant now) {
        MagicChestSettings.Snapshot configuration = settings.snapshot();
        MagicChestKey key = record.key();
        if (!record.refreshEnabled() || !record.hologramEnabled()) {
            closeHologram(key);
            return;
        }
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            closeHologram(key);
            return;
        }
        String text = "&7" + formatRemaining(record, now) + "\n"
                + (record.hasClaimableItems() ? "&a可领取!" : "&c暂无物品");
        FloatingTextHandle handle = holograms.get(key);
        Location location = new Location(
                world,
                key.x() + 0.5D,
                key.y() + configuration.hologramHeight(),
                key.z() + 0.5D
        );
        FloatingTextSpec specification = new FloatingTextSpec(
                List.of(
                        LegacyText.colorize(text.substring(0, text.indexOf('\n'))),
                        LegacyText.colorize(text.substring(text.indexOf('\n') + 1))
                ),
                configuration.hologramLineSpacing(),
                configuration.hologramViewDistance()
        );
        if (handle == null) {
            holograms.put(key, floatingText.show(location, specification));
        } else {
            Location previousLocation = hologramLocations.get(key);
            if (!sameAnchor(previousLocation, location)) handle.move(location);
            if (!specification.equals(hologramSpecifications.get(key))) handle.update(specification);
        }
        hologramSpecifications.put(key, specification);
        hologramLocations.put(key, location.clone());
    }

    private void closeHologram(MagicChestKey key) {
        FloatingTextHandle handle = holograms.remove(key);
        if (handle != null) handle.close();
        hologramSpecifications.remove(key);
        hologramLocations.remove(key);
    }

    private MagicChestRecord requireRecord(MagicChestKey key) {
        MagicChestRecord record = store.find(Objects.requireNonNull(key, "key"));
        if (record == null) throw new IllegalStateException("MagicChest record disappeared: " + key.encoded());
        return record;
    }

    private void requireAdmin(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            throw new IllegalStateException("Player lacks " + ADMIN_PERMISSION + ": " + player.getName());
        }
    }

    private void persist() {
        store.save(store.all());
    }

    private void removeIfManaged(MagicChestKey key, String message, Player notify) {
        if (store.find(key) == null) return;
        removeRecord(key, message, notify);
    }

    private void removeRecord(MagicChestKey key, String message, Player notify) {
        for (UUID playerUniqueId : List.copyOf(openSessions.keySet())) {
            if (!key.equals(openSessions.get(playerUniqueId))) continue;
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null) menus.close(player);
        }
        closeHologram(key);
        store.remove(key);
        claimViewers.remove(key);
        persist();
        if (notify != null) notify.sendMessage(LegacyText.colorize("&a" + message));
    }

}
