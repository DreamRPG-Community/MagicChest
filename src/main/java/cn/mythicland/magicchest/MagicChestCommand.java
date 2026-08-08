package cn.mythicland.magicchest;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.BukkitCommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.command.Subcommand;
import cn.mythicland.lib.text.LegacyText;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

/**
 * Registers MagicChest's administrative command tree.
 */
@InjectComponent
@CommandComponent
final class MagicChestCommand implements BukkitCommandComponent {

    private final CommandRouter router;

    MagicChestCommand(JavaPlugin plugin, LibApi lib, MagicChestPlugin magicChestPlugin) {
        this.router = Objects.requireNonNull(lib, "lib").createCommandRouter(plugin, "magicchest");
        router.register(new ReloadCommand(magicChestPlugin));
    }

    @Override
    public String commandName() {
        return "magicchest";
    }

    @Override
    public CommandRouter executor() {
        return router;
    }

    @Override
    public CommandRouter tabCompleter() {
        return router;
    }

    private record ReloadCommand(MagicChestPlugin plugin) implements Subcommand {

        private ReloadCommand {
            plugin = Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public String name() {
            return "reload";
        }

        @Override
        public String usage() {
            return "/magicchest reload";
        }

        @Override
        public String permission() {
            return MagicChestService.reloadPermission();
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (!arguments.isEmpty()) throw new CommandUsageException(usage());
            plugin.reloadMagicChest();
            sender.sendMessage(LegacyText.colorize("&aMagicChest 配置已重新加载。"));
        }
    }
}
