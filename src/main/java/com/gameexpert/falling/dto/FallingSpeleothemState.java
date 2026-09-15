package com.gameexpert.falling.dto;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import lombok.Value;
import lombok.experimental.Accessors;

/** Durable falling entity state; velocity and age use unscaled 20 Hz Minecraft ticks. */
@Value
@Accessors(fluent = true)
public class FallingSpeleothemState {
    String id;
    int blockId;
    int blockState;
    double x;
    double y;
    double z;
    double velocityY;
    double fallDistance;
    int age;
    int damagePerDistance;
    boolean finished;

    public FallingSpeleothemState(String id, int blockId, int blockState,
            double x, double y, double z, double velocityY, double fallDistance,
            int age, int damagePerDistance, boolean finished) {
        if (id == null || id.isEmpty() || id.length() > 160
                || blockId != Blocks.POINTED_DRIPSTONE && blockId != Blocks.SULFUR_SPIKE
                || blockState < 0 || blockState >= 20
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(velocityY) || !Double.isFinite(fallDistance)
                || fallDistance < 0 || age < 0 || age > 601 || age > 600 && !finished
                || damagePerDistance < 0) {
            throw new IllegalArgumentException("invalid falling speleothem state");
        }
        String exactState;
        try {
            exactState = Mc263ExactStateCodec.decode(blockId, blockState).exactState();
        } catch (IllegalArgumentException invalidState) {
            throw new IllegalArgumentException("invalid falling speleothem state", invalidState);
        }
        // FallingBlockEntity.fall copies waterlogged speleothems as dry entities, and this path is
        // only spawned by SpeleothemBlock's downward stalactite branch. Reject recovery rows that
        // could never have been emitted by that authority instead of letting Java accept states the
        // standalone validator already rejects.
        if (!exactState.contains("vertical_direction=down")
                || !exactState.contains("waterlogged=false")) {
            throw new IllegalArgumentException("invalid falling speleothem state");
        }
        boolean damagingTip = exactState.contains("thickness=tip,")
                || exactState.contains("thickness=tip_merge,");
        // spawnFallingStalactite calls setHurtsEntities only for TIP/TIP_MERGE and the amount is
        // always max(6, 1 + topY - segmentY). Bodies therefore carry exactly zero, while tips
        // always carry at least six.
        if (damagingTip ? damagePerDistance < 6 : damagePerDistance != 0) {
            throw new IllegalArgumentException("invalid falling speleothem damage state");
        }
        this.id = id;
        this.blockId = blockId;
        this.blockState = blockState;
        this.x = x;
        this.y = y;
        this.z = z;
        this.velocityY = velocityY;
        this.fallDistance = fallDistance;
        this.age = age;
        this.damagePerDistance = damagePerDistance;
        this.finished = finished;
    }
}
