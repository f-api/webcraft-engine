package com.gameexpert.block.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable exactly-once ordinary block-state mutation without a durable player aggregate. */
public final class BlockStateSettlementCommand {
    private final long settlementId;
    private final Long worldId;
    private final int x, y, z;
    private final short blockType, blockState;
    private final String fingerprint;

    public BlockStateSettlementCommand(long settlementId, Long worldId,
            int x, int y, int z, short blockType, short blockState) {
        if (settlementId <= 0 || settlementId == Long.MAX_VALUE || worldId == null || worldId <= 0) {
            throw new IllegalArgumentException("complete block settlement identity is required");
        }
        this.settlementId = settlementId;
        this.worldId = worldId;
        this.x = x; this.y = y; this.z = z;
        this.blockType = blockType; this.blockState = blockState;
        String canonical = settlementId + ":" + worldId + ":" + x + ":" + y + ":" + z
                + ":" + Short.toUnsignedInt(blockType) + ":" + Short.toUnsignedInt(blockState);
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
    public long settlementId() { return settlementId; }
    public Long worldId() { return worldId; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public short blockType() { return blockType; }
    public short blockState() { return blockState; }
    public String fingerprint() { return fingerprint; }
}
