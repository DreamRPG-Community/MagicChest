package cn.mythicland.magicchest;

import cn.mythicland.lib.container.ContainerAnimationHandle;
import cn.mythicland.lib.menu.*;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.magicchest.api.MagicChestKey;
import cn.mythicland.magicchest.api.RefreshMode;
import cn.mythicland.magicchest.api.RefreshPolicy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Administrator configuration menu for one MagicChest.
 *
 * <p>The layout follows MythicThePit's settings convention: each setting is one item, and its
 * lore contains the complete option list. The selected option is green with a triangle marker;
 * other options remain gray. Empty slots intentionally stay empty.</p>
 */
final class MagicChestManagementMenu extends AnnotatedMenuView {

    private static final int REFRESH_ENABLED_SLOT = 10;
    private static final int SIZE_SLOT = 11;
    private static final int MODE_SLOT = 12;
    private static final int TIME_SLOT = 13;
    private static final int PARTICLE_SLOT = 14;
    private static final int HOLOGRAM_SLOT = 15;
    private static final int EDIT_SLOT = 16;
    private static final short GRAY_DYE_DATA = 8;
    private static final short LIME_DYE_DATA = 10;

    private final MagicChestService service;
    private final MagicChestKey key;
    private ContainerAnimationHandle animation;
    private boolean closed;

    MagicChestManagementMenu(
            MagicChestService service,
            MagicChestKey key
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.key = Objects.requireNonNull(key, "key");
    }

    private static int selectedIndex(List<String> options, String selected) {
        int index = options.indexOf(selected);
        return Math.max(index, 0);
    }

    private static List<String> displayOptions(List<String> options) {
        return options.stream().map(MagicChestManagementMenu::displayOption).toList();
    }

    private static String displayOption(String option) {
        if ("custom".equalsIgnoreCase(option)) return "自定义";
        if ("NONE".equalsIgnoreCase(option)) return "无";
        return option;
    }

    void attachAnimation(ContainerAnimationHandle animation) {
        if (this.animation != null) throw new IllegalStateException("MagicChest animation already attached");
        this.animation = Objects.requireNonNull(animation, "animation");
    }

    @Override
    public String title(Player player) {
        return LegacyText.colorize("&8MagicChest 管理");
    }

    @Override
    public int size(Player player) {
        return 27;
    }

    @MenuButton(slot = REFRESH_ENABLED_SLOT)
    private ItemStack refreshEnabledButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        return MenuSelection.item(
                Material.INK_SACK,
                record.refreshEnabled() ? LIME_DYE_DATA : GRAY_DYE_DATA,
                "&a箱子刷新",
                "选择是否启用该箱子的刷新功能。",
                List.of("启用", "关闭"),
                record.refreshEnabled() ? 0 : 1,
                List.of()
        );
    }

    @MenuButton(slot = SIZE_SLOT)
    private ItemStack sizeButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        return MenuSelection.item(
                Material.CHEST,
                "&a箱子大小",
                "选择虚拟箱子的容量。",
                List.of("小箱子", "大箱子"),
                record.size().slots() == 27 ? 0 : 1,
                List.of()
        );
    }

    @MenuButton(slot = MODE_SLOT)
    private ItemStack refreshModeButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        return MenuSelection.item(
                Material.WATCH,
                "&a刷新模式",
                "选择箱子的刷新方式。",
                List.of("定时刷新", "固定时间刷新", "始终刷新", "永不刷新"),
                record.refreshMode().ordinal(),
                List.of()
        );
    }

    @MenuButton(slot = TIME_SLOT)
    private ItemStack refreshTimeButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        MagicChestSettings.Snapshot settings = service.settingsForMenu();
        if (record.refreshMode() == RefreshMode.INTERVAL) {
            return MenuSelection.item(
                    Material.WATCH,
                    "&a间隔刷新时间",
                    "选择定时刷新的间隔。",
                    displayOptions(settings.intervalOptions()),
                    selectedIndex(settings.intervalOptions(), record.intervalOption()),
                    List.of("&7自定义值: &f" + RefreshPolicy.formatInterval(settings.customInterval()))
            );
        }
        if (record.refreshMode() == RefreshMode.DAILY) {
            return MenuSelection.item(
                    Material.REDSTONE,
                    "&a每日刷新时间",
                    "选择每日固定刷新的时间。",
                    displayOptions(settings.dailyOptions()),
                    selectedIndex(settings.dailyOptions(), record.dailyOption()),
                    List.of("&7自定义值: &f" + settings.customDailyTime())
            );
        }
        return null;
    }

    @MenuButton(slot = PARTICLE_SLOT)
    private ItemStack particleButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        MagicChestSettings.Snapshot settings = service.settingsForMenu();
        return MenuSelection.item(
                Material.BLAZE_POWDER,
                "&a可领取粒子",
                "选择箱子可领取时的粒子效果。",
                displayOptions(settings.particleOptions()),
                selectedIndex(settings.particleOptions(), record.particle()),
                List.of()
        );
    }

    @MenuButton(slot = HOLOGRAM_SLOT)
    private ItemStack hologramButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        return MenuSelection.item(
                Material.NAME_TAG,
                "&a悬浮字倒计时",
                "选择是否显示箱子上方的倒计时。",
                List.of("开启", "关闭"),
                record.hologramEnabled() ? 0 : 1,
                List.of()
        );
    }

    @MenuButton(slot = EDIT_SLOT)
    private ItemStack editButton(Player player) {
        MagicChestRecord record = service.recordForMenu(key);
        return MenuItems.button(
                record.editing() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                record.editing() ? "&a退出编辑模式" : "&a进入编辑模式",
                record.editing()
                        ? List.of("&7保存当前草稿并立即刷新箱子。")
                        : List.of("&7打开虚拟编辑库存。")
        );
    }

    @MenuAction(
            slot = REFRESH_ENABLED_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void toggleRefreshEnabled(Player player) {
        service.toggleRefreshEnabled(player, key);
    }

    @MenuAction(
            slot = SIZE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private boolean toggleSize(Player player) {
        return service.toggleSize(player, key);
    }

    @MenuAction(
            slot = MODE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void cycleRefreshMode(Player player, ClickType click) {
        service.cycleRefreshMode(player, key, MenuSelection.direction(click) > 0);
    }

    @MenuAction(
            slot = TIME_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private boolean cycleRefreshTime(Player player, ClickType click) {
        boolean next = MenuSelection.direction(click) > 0;
        RefreshMode mode = service.recordForMenu(key).refreshMode();
        if (mode == RefreshMode.INTERVAL) {
            service.cycleInterval(player, key, next);
            return true;
        }
        if (mode == RefreshMode.DAILY) {
            service.cycleDaily(player, key, next);
            return true;
        }
        return false;
    }

    @MenuAction(
            slot = PARTICLE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void cycleParticle(Player player, ClickType click) {
        service.cycleParticle(player, key, MenuSelection.direction(click) > 0);
    }

    @MenuAction(
            slot = HOLOGRAM_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void toggleHologram(Player player) {
        service.toggleHologram(player, key);
    }

    @MenuAction(
            slot = EDIT_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private boolean toggleEditing(Player player) {
        return service.recordForMenu(key).editing()
                ? service.exitEditing(player, key)
                : service.enterEditing(player, key);
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        finish(player);
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        finish(player);
    }

    private void finish(Player player) {
        if (closed) return;
        closed = true;
        if (animation != null) animation.close();
        service.onManagementClosed(player, key);
    }
}
