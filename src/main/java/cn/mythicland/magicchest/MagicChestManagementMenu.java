package cn.mythicland.magicchest;

import cn.mythicland.lib.container.ContainerAnimationHandle;
import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.lib.menu.StatefulMenuView;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.magicchest.api.MagicChestKey;
import cn.mythicland.magicchest.api.RefreshMode;
import cn.mythicland.magicchest.api.RefreshPolicy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Objects;

/**
 * Administrator configuration menu for one MagicChest.
 *
 * <p>The layout follows MythicThePit's settings convention: each setting is one item, and its
 * lore contains the complete option list. The selected option is green with a triangle marker;
 * other options remain gray. Empty slots intentionally stay empty.</p>
 */
final class MagicChestManagementMenu implements StatefulMenuView {

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

    @Override
    public void render(Player player, Inventory inventory) {
        MagicChestRecord record = service.recordForMenu(key);
        MagicChestSettings.Snapshot settings = service.settingsForMenu();
        inventory.clear();

        inventory.setItem(REFRESH_ENABLED_SLOT, MagicChestMenuItems.setting(
                Material.INK_SACK,
                record.refreshEnabled() ? LIME_DYE_DATA : GRAY_DYE_DATA,
                "箱子刷新",
                "选择是否启用该箱子的刷新功能。",
                List.of("启用", "关闭"),
                record.refreshEnabled() ? 0 : 1,
                List.of()
        ));
        inventory.setItem(SIZE_SLOT, MagicChestMenuItems.setting(
                Material.CHEST,
                "箱子大小",
                "选择虚拟箱子的容量。",
                List.of("小箱子", "大箱子"),
                record.size().slots() == 27 ? 0 : 1,
                List.of()
        ));
        inventory.setItem(MODE_SLOT, MagicChestMenuItems.setting(
                Material.WATCH,
                "刷新模式",
                "选择箱子的刷新方式。",
                List.of("定时刷新", "固定时间刷新", "始终刷新", "永不刷新"),
                record.refreshMode().ordinal(),
                List.of()
        ));
        if (record.refreshMode() == RefreshMode.INTERVAL) {
            inventory.setItem(TIME_SLOT, MagicChestMenuItems.setting(
                    Material.WATCH,
                    "间隔刷新时间",
                    "选择定时刷新的间隔。",
                    displayOptions(settings.intervalOptions()),
                    selectedIndex(settings.intervalOptions(), record.intervalOption()),
                    List.of("&7自定义值: &f" + RefreshPolicy.formatInterval(settings.customInterval()))
            ));
        } else if (record.refreshMode() == RefreshMode.DAILY) {
            inventory.setItem(TIME_SLOT, MagicChestMenuItems.setting(
                    Material.REDSTONE,
                    "每日刷新时间",
                    "选择每日固定刷新的时间。",
                    displayOptions(settings.dailyOptions()),
                    selectedIndex(settings.dailyOptions(), record.dailyOption()),
                    List.of("&7自定义值: &f" + settings.customDailyTime())
            ));
        }
        inventory.setItem(PARTICLE_SLOT, MagicChestMenuItems.setting(
                Material.BLAZE_POWDER,
                "可领取粒子",
                "选择箱子可领取时的粒子效果。",
                displayOptions(settings.particleOptions()),
                selectedIndex(settings.particleOptions(), record.particle()),
                List.of()
        ));
        inventory.setItem(HOLOGRAM_SLOT, MagicChestMenuItems.setting(
                Material.NAME_TAG,
                "悬浮字倒计时",
                "选择是否显示箱子上方的倒计时。",
                List.of("开启", "关闭"),
                record.hologramEnabled() ? 0 : 1,
                List.of()
        ));
        inventory.setItem(EDIT_SLOT, MagicChestMenuItems.button(
                record.editing() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                record.editing() ? "&a退出编辑模式" : "&a进入编辑模式",
                record.editing()
                        ? List.of("&7保存当前草稿并立即刷新箱子。")
                        : List.of("&7打开虚拟编辑库存。")
        ));
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size(player)) return;
        ClickType click = event.getClick();
        if (!isCycleClick(click)) return;
        boolean next = click == ClickType.LEFT;
        switch (slot) {
            case REFRESH_ENABLED_SLOT -> service.toggleRefreshEnabled(player, key);
            case SIZE_SLOT -> service.toggleSize(player, key);
            case MODE_SLOT -> service.cycleRefreshMode(player, key, next);
            case TIME_SLOT -> {
                if (service.recordForMenu(key).refreshMode() == RefreshMode.INTERVAL) {
                    service.cycleInterval(player, key, next);
                } else if (service.recordForMenu(key).refreshMode() == RefreshMode.DAILY) {
                    service.cycleDaily(player, key, next);
                }
            }
            case PARTICLE_SLOT -> service.cycleParticle(player, key, next);
            case HOLOGRAM_SLOT -> service.toggleHologram(player, key);
            case EDIT_SLOT -> {
                if (service.recordForMenu(key).editing()) service.exitEditing(player, key);
                else service.enterEditing(player, key);
            }
            default -> {
            }
        }
    }

    static boolean isCycleClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuService menuService) {
        event.setCancelled(true);
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
