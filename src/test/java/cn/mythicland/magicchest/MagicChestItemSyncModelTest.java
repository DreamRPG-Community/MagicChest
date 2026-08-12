package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.MagicChestItemSyncDecision;
import cn.mythicland.magicchest.api.MagicChestItemSyncStatus;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicChestItemSyncModelTest {

    @Test
    void syncDecisionDetachesReplacement() {
        ItemStack replacement = new ItemStack(Material.DIAMOND, 4);
        MagicChestItemSyncDecision decision = new MagicChestItemSyncDecision(
                MagicChestItemSyncStatus.UPDATED,
                replacement
        );

        assertTrue(decision.changed());
        assertNotSame(replacement, decision.replacement());

        decision.replacement().setAmount(1);
        assertTrue(decision.replacement().getAmount() == 4);
    }

    @Test
    void updatedDecisionCannotOmitReplacement() {
        assertThrows(IllegalArgumentException.class, () -> new MagicChestItemSyncDecision(
                MagicChestItemSyncStatus.UPDATED,
                null
        ));
    }

    @Test
    void unchangedDecisionCannotCarryReplacement() {
        assertThrows(IllegalArgumentException.class, () -> new MagicChestItemSyncDecision(
                MagicChestItemSyncStatus.CURRENT,
                new ItemStack(Material.STONE)
        ));
    }
}
