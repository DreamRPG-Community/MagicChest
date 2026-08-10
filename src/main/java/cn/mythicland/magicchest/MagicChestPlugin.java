package cn.mythicland.magicchest;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for the Lib-managed MagicChest component graph.
 */
public final class MagicChestPlugin extends JavaPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.magicchest";

    private PluginBootstrap bootstrap;

    @Override
    @SuppressWarnings("resource")
    public void onEnable() {
        try {
            LibApi lib = LibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "MagicChest failed to enable: " + LibApi.rootCauseMessage(exception),
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads MagicChest configuration through the injected lifecycle graph.
     */
    public void reloadMagicChest() {
        if (bootstrap == null) throw new IllegalStateException("MagicChest bootstrap is unavailable");
        bootstrap.reload();
    }
}
