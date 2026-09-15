package com.gameexpert.banner.dto;

import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.terrain.Blocks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Complete command for one consumed banner stack and its placed block/entity aggregate. */
public final class BannerPlacementSettlementCommand {
    private final long settlementId;
    private final long expectedPlayerRevision;
    private final PlayerInventoryMutationSnapshot player;
    private final int x;
    private final int y;
    private final int z;
    private final short blockType;
    private final short blockState;
    private final List<ItemComponentData.BannerLayer> patterns;
    private final String fingerprint;

    public BannerPlacementSettlementCommand(long settlementId, long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot player, int x, int y, int z,
            short blockType, short blockState, List<ItemComponentData.BannerLayer> patterns) {
        if (settlementId <= 0 || settlementId == Long.MAX_VALUE || expectedPlayerRevision < 0
                || expectedPlayerRevision == Long.MAX_VALUE || player == null
                || player.revision() != Math.addExact(expectedPlayerRevision, 1)
                || !Blocks.isBanner(Short.toUnsignedInt(blockType))) {
            throw new IllegalArgumentException("complete banner placement settlement is required");
        }
        this.settlementId = settlementId;
        this.expectedPlayerRevision = expectedPlayerRevision;
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
        this.blockState = blockState;
        this.patterns = new com.gameexpert.banner.service.BannerBlockData(x, y, z, patterns).patterns();
        StringBuilder layers = new StringBuilder();
        for (ItemComponentData.BannerLayer layer : this.patterns) {
            layers.append(layer.pattern().wireName()).append(':').append(layer.color()).append(',');
        }
        String canonical = settlementId + ":" + expectedPlayerRevision + ":"
                + player.fingerprint() + ":" + x + ":" + y + ":" + z + ":"
                + Short.toUnsignedInt(blockType) + ":" + Short.toUnsignedInt(blockState)
                + ":" + layers;
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public long settlementId() { return settlementId; }
    public long expectedPlayerRevision() { return expectedPlayerRevision; }
    public PlayerInventoryMutationSnapshot player() { return player; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public short blockType() { return blockType; }
    public short blockState() { return blockState; }
    public List<ItemComponentData.BannerLayer> patterns() { return patterns; }
    public String fingerprint() { return fingerprint; }
}
