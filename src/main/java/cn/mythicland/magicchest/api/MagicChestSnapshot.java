package cn.mythicland.magicchest.api;

import java.util.Objects;

/**
 * Read-only runtime snapshot of one managed chest.
 *
 * @param key                    chest identity
 * @param refreshEnabled         whether MagicChest intercepts the physical chest and refreshes it
 * @param size                   visible virtual inventory size
 * @param refreshPolicy          current refresh policy
 * @param particle               claimable particle option, or {@code NONE}
 * @param hologramEnabled        whether floating text is enabled
 * @param editing                whether the chest is in administrator edit mode
 * @param claimable              whether the authoritative live inventory contains an item
 * @param activeClaimViewers     number of players currently viewing the claim menu
 * @param nextRefreshEpochSecond next scheduled refresh in epoch seconds
 */
public record MagicChestSnapshot(
        MagicChestKey key,
        boolean refreshEnabled,
        MagicChestSize size,
        RefreshPolicy refreshPolicy,
        String particle,
        boolean hologramEnabled,
        boolean editing,
        boolean claimable,
        int activeClaimViewers,
        long nextRefreshEpochSecond
) {

    /**
     * Validates the immutable snapshot.
     */
    public MagicChestSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(refreshPolicy, "refreshPolicy");
        Objects.requireNonNull(particle, "particle");
        if (activeClaimViewers < 0) throw new IllegalArgumentException("activeClaimViewers cannot be negative");
    }
}
