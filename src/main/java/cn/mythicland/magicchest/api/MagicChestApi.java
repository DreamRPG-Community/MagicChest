package cn.mythicland.magicchest.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Read-only Bukkit service for querying MagicChest runtime state.
 *
 * <p>All methods are intended for the Bukkit primary thread. Returned snapshots are detached and
 * cannot mutate the plugin's inventory, configuration, or storage.</p>
 */
public interface MagicChestApi {

    /**
     * Finds a managed chest by its world-coordinate key.
     *
     * @param key chest identity
     * @return snapshot when managed
     */
    Optional<MagicChestSnapshot> find(MagicChestKey key);

    /**
     * Returns all currently managed chests.
     *
     * @return immutable snapshots
     */
    Collection<MagicChestSnapshot> snapshots();
}
