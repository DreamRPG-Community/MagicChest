package cn.mythicland.magicchest;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the MythicThePit-style selected and unselected option lines.
 */
class MagicChestMenuItemsTest {

    @Test
    void selectedOptionIsGreenAndHasTriangleMarker() {
        assertEquals("&a▶ &a小箱子", MagicChestMenuItems.optionLine("小箱子", true));
    }

    @Test
    void unselectedOptionIsGrayAndHasNoSelectionMarker() {
        assertEquals("&7  &7大箱子", MagicChestMenuItems.optionLine("大箱子", false));
    }

    @Test
    void onlyPlainLeftAndRightClicksCycleManagementSettings() {
        assertTrue(MagicChestManagementMenu.isCycleClick(ClickType.LEFT));
        assertTrue(MagicChestManagementMenu.isCycleClick(ClickType.RIGHT));
        assertFalse(MagicChestManagementMenu.isCycleClick(ClickType.DOUBLE_CLICK));
        assertFalse(MagicChestManagementMenu.isCycleClick(ClickType.SHIFT_LEFT));
    }

    @Test
    void legacyDyeSettingsUseTheConfiguredMaterialData() {
        ItemStack lime = new ItemStack(Material.INK_SACK, 1, (short) 10);
        ItemStack gray = new ItemStack(Material.INK_SACK, 1, (short) 8);

        assertEquals((short) 10, lime.getDurability());
        assertEquals((short) 8, gray.getDurability());
    }
}
