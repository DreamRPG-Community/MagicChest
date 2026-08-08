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
 * Full virtual inventory used to edit a chest draft.
 */
final class MagicChestEditorMenu implements StatefulMenuView {

    private final MagicChestService service;
    private final MagicChestKey key;
    private ContainerAnimationHandle animation;
    private boolean closed;

    MagicChestEditorMenu(
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
        return LegacyText.colorize("&8箱子(编辑状态)");
    }

    @Override
    public int size(Player player) {
        return service.recordForMenu(key).size().slots();
    }

    @Override
    public void render(Player player, Inventory inventory) {
        MagicChestRecord record = service.recordForMenu(key);
        ItemStack[] draft = record.draftCopy();
        ItemStack[] visible = new ItemStack[record.size().slots()];
        for (int index = 0; index < visible.length; index++) {
            visible[index] = draft[index] == null ? null : draft[index].clone();
        }
        inventory.setContents(visible);
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
        event.setCancelled(false);
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuService menuService) {
        event.setCancelled(false);
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        finish(player, inventory);
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        finish(player, inventory);
    }

    private void finish(Player player, Inventory inventory) {
        if (closed) return;
        closed = true;
        if (animation != null) animation.close();
        service.onEditorClosed(player, key, inventory);
    }
}
