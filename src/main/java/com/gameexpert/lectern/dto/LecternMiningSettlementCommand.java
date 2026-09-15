package com.gameexpert.lectern.dto;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.terrain.Blocks;
import java.util.List;

/** Complete durable command for mining a lectern that contains a book. */
public final class LecternMiningSettlementCommand {
    private final GroundMutationCommand groundMutation;
    private final LecternBlockData expectedLectern;

    public LecternMiningSettlementCommand(GroundMutationCommand groundMutation,
            LecternBlockData expectedLectern) {
        if (groundMutation == null || expectedLectern == null
                || groundMutation.kind() != GroundMutationCommand.Kind.BLOCK_DROP
                || !groundMutation.removedItemIds().isEmpty()
                || !groundMutation.insertedXpOrbs().isEmpty()
                || !groundMutation.removedXpOrbIds().isEmpty()) {
            throw new IllegalArgumentException("lectern mining requires one block-drop mutation");
        }
        requireExactDrops(groundMutation.insertedItems(), expectedLectern);
        this.groundMutation = groundMutation;
        this.expectedLectern = expectedLectern;
    }

    public GroundMutationCommand groundMutation() { return groundMutation; }
    public LecternBlockData expectedLectern() { return expectedLectern; }

    private static void requireExactDrops(
            List<GroundItemSnapshot> drops, LecternBlockData lectern) {
        if (drops.size() != 2) {
            throw new IllegalArgumentException("lectern mining requires lectern and book drops");
        }
        int lecternDrops = 0;
        int bookDrops = 0;
        for (GroundItemSnapshot drop : drops) {
            if (Math.floor(drop.x()) != lectern.x() || Math.floor(drop.y()) != lectern.y()
                    || Math.floor(drop.z()) != lectern.z()) {
                throw new IllegalArgumentException("lectern drop coordinates do not match");
            }
            if (drop.itemType() == (short) Blocks.LECTERN && drop.count() == 1
                    && drop.durability() == 0 && drop.enchantments() == 0
                    && drop.mapId() == 0 && drop.shulkerId() == 0
                    && drop.bucketMobData() == null && drop.itemComponentData() == null) {
                lecternDrops++;
            }
            if (matches(drop, lectern.book())) bookDrops++;
        }
        if (lecternDrops != 1 || bookDrops != 1) {
            throw new IllegalArgumentException("lectern mining drops do not preserve exact book");
        }
    }

    private static boolean matches(
            GroundItemSnapshot drop, PlayerInventory.StackSnapshot stack) {
        return drop.itemType() == stack.itemType() && drop.count() == stack.count()
                && drop.durability() == stack.durability()
                && drop.enchantments() == stack.enchantments()
                && drop.mapId() == stack.mapId() && drop.shulkerId() == stack.shulkerId()
                && java.util.Objects.equals(drop.bucketMobData(), stack.bucketMobData())
                && java.util.Objects.equals(drop.itemComponentData(), stack.itemComponentData());
    }
}
