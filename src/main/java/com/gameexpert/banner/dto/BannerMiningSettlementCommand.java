package com.gameexpert.banner.dto;

import com.gameexpert.banner.service.BannerBlockData;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.terrain.Blocks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Exact placed-banner source and its single component-preserving durable drop. */
public final class BannerMiningSettlementCommand {
    private final GroundMutationCommand groundMutation;
    private final BannerBlockData expectedBanner;
    private final short blockType;
    private final short blockState;
    private final String fingerprint;

    public BannerMiningSettlementCommand(GroundMutationCommand groundMutation,
            BannerBlockData expectedBanner, short blockType, short blockState) {
        if (groundMutation == null || expectedBanner == null
                || !Blocks.isBanner(Short.toUnsignedInt(blockType))
                || groundMutation.kind() != GroundMutationCommand.Kind.BLOCK_DROP
                || groundMutation.committedPlayer() == null
                || groundMutation.insertedItems().size() != 1
                || !groundMutation.removedItemIds().isEmpty()
                || !groundMutation.insertedXpOrbs().isEmpty()
                || !groundMutation.removedXpOrbIds().isEmpty()) {
            throw new IllegalArgumentException("banner mining requires one exact block drop");
        }
        var drop = groundMutation.insertedItems().getFirst();
        var stack = bannerStack(blockType, expectedBanner);
        if (Math.floor(drop.x()) != expectedBanner.x()
                || Math.floor(drop.y()) != expectedBanner.y()
                || Math.floor(drop.z()) != expectedBanner.z()
                || drop.itemType() != stack.itemType() || drop.count() != 1
                || drop.durability() != 0 || drop.enchantments() != 0
                || drop.mapId() != 0 || drop.shulkerId() != 0 || drop.bucketMobData() != null
                || !Objects.equals(drop.itemComponentData(), stack.itemComponentData())) {
            throw new IllegalArgumentException("banner mining drop does not preserve its source");
        }
        this.groundMutation = groundMutation;
        this.expectedBanner = expectedBanner;
        this.blockType = blockType;
        this.blockState = blockState;
        String canonical = "banner-mining|" + groundMutation.fingerprint() + "|"
                + Short.toUnsignedInt(blockType) + "|" + Short.toUnsignedInt(blockState);
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static PlayerInventory.StackSnapshot bannerStack(short blockType, BannerBlockData banner) {
        return new PlayerInventory.StackSnapshot(blockType, 1, 0, 0, 0, 0, null,
                ItemComponentCodec.encode(blockType,
                        ItemComponentData.EMPTY.withBannerPatterns(banner.patterns())));
    }

    public GroundMutationCommand groundMutation() { return groundMutation; }
    public BannerBlockData expectedBanner() { return expectedBanner; }
    public short blockType() { return blockType; }
    public short blockState() { return blockState; }
    public long settlementId() { return groundMutation.insertedItems().getFirst().entityId(); }
    public String fingerprint() { return fingerprint; }
}
