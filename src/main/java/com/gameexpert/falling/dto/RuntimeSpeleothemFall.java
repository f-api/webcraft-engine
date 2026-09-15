package com.gameexpert.falling.dto;

import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.BlockMutation;
import com.gameexpert.engine.persistence.tick.Mc263FinalCarrierTickSemantics.SemanticWorld;
import com.gameexpert.engine.persistence.tick.SpeleothemFallIntents;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import java.io.*;
import java.util.*;
import lombok.Value;
import lombok.experimental.Accessors;

/** Runtime provenance; never a canonical carrier receipt or publication. */
@Value
@Accessors(fluent = true)
public class RuntimeSpeleothemFall {
    String identity;
    int x;
    int y;
    int z;
    int blockId;
    long dueTick;

    /** Existing resident/diff/wire state is compact; falling entity payloads are exact codes. */
    public static String exactFromCompact(int blockId, int compact) {
        String type = blockId == Blocks.POINTED_DRIPSTONE ? "pointed_dripstone" : "sulfur_spike";
        String thickness = switch (compact & 14) {
            case 0 -> "tip"; case 2 -> "tip_merge"; case 4 -> "frustum";
            case 6 -> "middle"; case 8 -> "base";
            default -> throw new IllegalArgumentException("invalid compact speleothem thickness");
        };
        return "minecraft:" + type + "[thickness=" + thickness + ",vertical_direction="
                + ((compact & 1) != 0 ? "up" : "down") + ",waterlogged=" + ((compact & 128) != 0) + "]";
    }

    public static int compactState(int blockId, int exactCode) {
        String exact = Mc263ExactStateCodec.decode(blockId, exactCode).exactState();
        int thickness = exact.contains("thickness=tip_merge,") ? 2
                : exact.contains("thickness=frustum,") ? 4
                : exact.contains("thickness=middle,") ? 6
                : exact.contains("thickness=base,") ? 8 : 0;
        return thickness | (exact.contains("vertical_direction=up") ? 1 : 0)
                | (exact.contains("waterlogged=true") ? 128 : 0);
    }

    public String positionKey() { return x + ":" + y + ":" + z + ":" + blockId; }

    public static boolean unsupportedDown(SemanticWorld world, int x, int y, int z) {
        var current = Mc263FeatureBlockState.fromExact(world.exactBlockStateAt(x, y, z));
        if (!SpeleothemFallIntents.isSpeleothem(current.blockId())
                || !current.exactState().contains("vertical_direction=down")) return false;
        if (y == Blocks.MAX_Y) return true;
        var above = Mc263FeatureBlockState.fromExact(world.exactBlockStateAt(x, y + 1, z));
        return !above.isFaceSturdyDown() && !(above.blockId() == current.blockId()
                && above.exactState().contains("vertical_direction=down"));
    }

    /** Live type mismatch consumes a no-op; restored support never cancels a downward tick. */
    public List<BlockMutation> plan(SemanticWorld world) {
        var current = Mc263FeatureBlockState.fromExact(world.exactBlockStateAt(x, y, z));
        if (current.blockId() != blockId) return List.of();
        if (current.exactState().contains("vertical_direction=up")) {
            var below = y == Blocks.MIN_Y ? Mc263FeatureBlockState.fromExact("minecraft:air")
                    : Mc263FeatureBlockState.fromExact(world.exactBlockStateAt(x, y - 1, z));
            if (below.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP)
                    || below.blockId() == blockId && below.exactState().contains("vertical_direction=up")) {
                return List.of();
            }
            return List.of(world.encodeExactState(x, y, z,
                    current.exactState().contains("waterlogged=true") ? "minecraft:water" : "minecraft:air"));
        }
        List<BlockMutation> cells = new ArrayList<>();
        for (int sy = y; sy >= Blocks.MIN_Y; sy--) {
            var state = Mc263FeatureBlockState.fromExact(world.exactBlockStateAt(x, sy, z));
            String exact = state.exactState();
            if (!SpeleothemFallIntents.isSpeleothem(state.blockId())
                    || !exact.contains("vertical_direction=down")) break;
            cells.add(world.encodeExactState(x, sy, z,
                    exact.contains("waterlogged=true") ? "minecraft:water" : "minecraft:air"));
            if (exact.contains("thickness=tip,") || exact.contains("thickness=tip_merge,")) break;
        }
        return List.copyOf(cells);
    }

    public List<FallingSpeleothemState> births(List<BlockMutation> cells) {
        List<FallingSpeleothemState> result = new ArrayList<>();
        for (var cell : cells) {
            String exact = Mc263ExactStateCodec.decode(cell.beforeBlockId(), cell.beforeBlockState()).exactState();
            if (!exact.contains("vertical_direction=down")) continue;
            boolean tip = exact.contains("thickness=tip,") || exact.contains("thickness=tip_merge,");
            int dry = Mc263ExactStateCodec.stateCode(Mc263FeatureBlockState.fromExact(
                    exact.replace("waterlogged=true", "waterlogged=false")));
            result.add(new FallingSpeleothemState("runtime-fall:" + identity + ":" + cell.y(),
                    cell.beforeBlockId(), dry, cell.x() + .5, cell.y(), cell.z() + .5,
                    0, 0, 0, tip ? Math.max(6, 1 + y - cell.y()) : 0, false));
        }
        return List.copyOf(result);
    }

    public static byte[] encode(List<BlockMutation> cells) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(0x52534631); // Runtime Speleothem Fall v1, independent of canonical codecs.
            out.writeInt(cells.size());
            for (var c : cells) {
                out.writeInt(c.x()); out.writeInt(c.y()); out.writeInt(c.z());
                out.writeInt(c.beforeBlockId()); out.writeInt(c.beforeBlockState());
                out.writeInt(c.blockId()); out.writeInt(c.blockState());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    public static List<BlockMutation> decode(byte[] bytes) {
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0x52534631) throw new IllegalArgumentException("runtime fall receipt version");
            int size = in.readInt();
            if (size < 0 || size > Blocks.MAX_Y - Blocks.MIN_Y + 1) throw new IllegalArgumentException("runtime fall receipt size");
            List<BlockMutation> result = new ArrayList<>();
            for (int i = 0; i < size; i++) result.add(new BlockMutation(in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt(), in.readInt()));
            if (in.available() != 0) throw new IllegalArgumentException("runtime fall receipt trailing bytes");
            return List.copyOf(result);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
