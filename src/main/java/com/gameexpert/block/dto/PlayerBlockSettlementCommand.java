package com.gameexpert.block.dto;

import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Complete player inventory/state plus one persisted block-state transition. */
public final class PlayerBlockSettlementCommand {
    private final long settlementId;
    private final long expectedPlayerRevision;
    private final PlayerInventoryMutationSnapshot player;
    private final int x;
    private final int y;
    private final int z;
    private final short blockType;
    private final short blockState;
    private final String fingerprint;

    public PlayerBlockSettlementCommand(long settlementId, long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot player, int x, int y, int z,
            short blockType, short blockState) {
        if (settlementId <= 0 || settlementId == Long.MAX_VALUE || expectedPlayerRevision < 0
                || expectedPlayerRevision == Long.MAX_VALUE || player == null
                || player.revision() != Math.addExact(expectedPlayerRevision, 1)) {
            throw new IllegalArgumentException("complete player block settlement is required");
        }
        this.settlementId = settlementId;
        this.expectedPlayerRevision = expectedPlayerRevision;
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
        this.blockState = blockState;
        String canonical = settlementId + ":" + expectedPlayerRevision + ":"
                + player.fingerprint() + ":" + x + ":" + y + ":" + z + ":"
                + Short.toUnsignedInt(blockType) + ":" + Short.toUnsignedInt(blockState);
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
    public String fingerprint() { return fingerprint; }
}
