package cn.mythicland.magicchest;

import cn.mythicland.lib.container.ContainerAnimationHandle;
import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.lib.menu.StatefulMenuView;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.magicchest.api.MagicChestKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Shared editable view. Each player receives a detached client inventory, while every transfer is
 * applied to the one main-thread live inventory owned by the record.
 */
final class MagicChestClaimMenu implements StatefulMenuView {

    private final MagicChestService service;
    private final MagicChestKey key;
    private ContainerAnimationHandle animation;
    private boolean closed;

    MagicChestClaimMenu(
            MagicChestService service,
            MagicChestKey key
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.key = Objects.requireNonNull(key, "key");
    }

    void attachAnimation(ContainerAnimationHandle animation) {
        if (this.animation != null) throw new IllegalStateException("MagicChest animation already attached");
        this.animation = Objects.requireNonNull(animation, "animation");
    }

    @Override
    public String title(Player player) {
        return LegacyText.colorize("&8箱子");
    }

    @Override
    public int size(Player player) {
        return service.recordForMenu(key).size().slots();
    }

    @Override
    public void render(Player player, Inventory inventory) {
        MagicChestRecord record = service.recordForMenu(key);
        ItemStack[] contents = record.liveCopy();
        ItemStack[] visible = new ItemStack[record.size().slots()];
        for (int index = 0; index < visible.length; index++) {
            visible[index] = contents[index] == null ? null : contents[index].clone();
        }
        inventory.setContents(visible);
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
        service.handleClaimClick(player, key, event);
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuService menuService) {
        service.handleClaimDrag(player, key, event);
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
        service.onClaimClosed(player, key);
    }
}
