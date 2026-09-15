package com.gameexpert.engine;

import com.gameexpert.falling.dto.FallingSpeleothemState;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;

/**
 * Pinned SpeleothemBlock/FallingBlockEntity motion, shared by live and recovery paths.
 * One {@link #step} is one unscaled 20 Hz Minecraft entity tick; the authority layer runs two
 * substeps per authority tick.
 */
public final class FallingSpeleothemPhysics {
    private FallingSpeleothemPhysics() {}

    @FunctionalInterface
    public interface VerticalCollision {
        /** Clip the downward sweep of the 0.98-wide entity; NaN means unavailable terrain. */
        double clip(FallingSpeleothemState state, double movement);
    }

    public static FallingSpeleothemState step(FallingSpeleothemState source,
            VerticalCollision collision) {
        if (source.finished()) return source;
        double velocity = source.velocityY() - 0.04;
        double movement = collision.clip(source, velocity);
        if (Double.isNaN(movement)) return source;
        if (!Double.isFinite(movement) || movement < velocity - 1e-7 || movement > 0) {
            throw new IllegalArgumentException("invalid falling collision displacement");
        }
        boolean landed = movement > velocity + 1e-7;
        int age = source.age() + 1;
        double y = source.y() + movement;
        // FallingBlockEntity checks blockPosition().getY(), so void expiry uses the floored entity
        // position and both pinned build-height bounds rather than the raw double coordinate.
        double blockY = Math.floor(y);
        boolean expired = age > 600
                || age > 100 && (blockY <= Blocks.MIN_Y || blockY > Blocks.MAX_Y);
        return new FallingSpeleothemState(source.id(), source.blockId(), source.blockState(),
                source.x(), y, source.z(), landed ? 0 : velocity * (double) 0.98f,
                source.fallDistance() - movement, age, source.damagePerDistance(), landed || expired);
    }

    public static int landingDamage(FallingSpeleothemState state) {
        if (!state.finished() || state.velocityY() != 0 || state.damagePerDistance() == 0
                || !isDamagingTip(state)) return 0;
        return (int) Math.min(40, Math.max(0, Math.ceil(state.fallDistance() - 1))
                * state.damagePerDistance());
    }

    private static boolean isDamagingTip(FallingSpeleothemState state) {
        String exactState = Mc263ExactStateCodec.decode(state.blockId(), state.blockState()).exactState();
        return exactState.contains("vertical_direction=down")
                && exactState.contains("waterlogged=false")
                && (exactState.contains("thickness=tip,")
                        || exactState.contains("thickness=tip_merge,"));
    }
}
