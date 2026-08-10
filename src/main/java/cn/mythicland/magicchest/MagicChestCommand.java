package cn.mythicland.magicchest;

import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.text.LegacyText;

import java.util.Objects;

/**
 * Handles MagicChest administrative commands.
 */
@CommandComponent("magicchest")
final class MagicChestCommand {

    private final MagicChestPlugin plugin;

    MagicChestCommand(MagicChestPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @CommandHandler(value = "reload", permission = "magicchest.reload")
    void reload(CommandContext context) {
        context.requireArguments(0);
        plugin.reloadMagicChest();
        context.sender().sendMessage(LegacyText.colorize("&aMagicChest 配置已重新加载。"));
    }
}
