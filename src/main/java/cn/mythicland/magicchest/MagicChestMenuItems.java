package cn.mythicland.magicchest;

import cn.mythicland.lib.text.LegacyText;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Small item factory shared by MagicChest's virtual menus.
 */
final class MagicChestMenuItems {

    private MagicChestMenuItems() {
    }

    static ItemStack button(Material material, String name, List<String> lore) {
        return button(new ItemStack(material), name, lore);
    }

    private static ItemStack button(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LegacyText.colorize(name));
        if (!lore.isEmpty()) meta.setLore(lore.stream().map(LegacyText::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack setting(
            Material material,
            String name,
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        return setting(new ItemStack(material), name, description, options, selectedIndex, details);
    }

    static ItemStack setting(
            Material material,
            short durability,
            String name,
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        return setting(new ItemStack(material, 1, durability), name, description, options, selectedIndex, details);
    }

    private static ItemStack setting(
            ItemStack icon,
            String name,
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + description);
        lore.add("");
        for (int index = 0; index < options.size(); index++) {
            lore.add(optionLine(options.get(index), index == selectedIndex));
        }
        if (!details.isEmpty()) {
            lore.add("");
            lore.addAll(details);
        }
        lore.add("");
        lore.add("&e点击切换!");
        return button(icon, "&a" + name, lore);
    }

    static String optionLine(String option, boolean selected) {
        return selected ? "&a▶ &a" + option : "&7  &7" + option;
    }
}
