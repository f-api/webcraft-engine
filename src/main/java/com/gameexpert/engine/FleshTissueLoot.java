package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Natural tissue yields materials, never itself; enchantments deliberately have no input. */
public final class FleshTissueLoot {
    private FleshTissueLoot() { }

    public static List<int[]> drops(int block, int state, DoubleSupplier random) {
        return switch (block) {
            case Blocks.FLESH_HEART -> List.of(new int[] {Blocks.FLESH_FIBER, 1});
            case Blocks.FLESH_ARTERY -> random.getAsDouble() < .1
                    ? List.of(new int[] {Blocks.FLESH_FIBER, 1}, new int[] {Blocks.FLESH_CLOT_SAC, 1})
                    : List.of(new int[] {Blocks.FLESH_FIBER, 1});
            case Blocks.FLESH_FAT_SAC -> List.of(new int[] {Blocks.FLESH_FAT, 1 + (int) (random.getAsDouble() * 2)});
            case Blocks.FLESH_MEMBRANE_BLOCK -> List.of(new int[] {Blocks.FLESH_MEMBRANE, 1});
            case Blocks.FLESH_BONE_SPUR -> List.of(new int[] {PlayerInventory.BONE, 1 + (int) (random.getAsDouble() * 2)});
            case Blocks.FLESH_NECROSIS -> random.getAsDouble() < .25
                    ? List.of(new int[] {Blocks.FLESH_FIBER, 1}) : List.of();
            case Blocks.FLESH_LARGE_COCOON -> List.of(new int[] {Blocks.FLESH_MEMBRANE, 2}, new int[] {PlayerInventory.BONE, 2});
            case Blocks.FLESH_COCOON -> state == 2 && random.getAsDouble() < .25
                    ? List.of(new int[] {Blocks.FLESH_MEMBRANE, 1}, new int[] {Blocks.FLESH_CLOT_SAC, 1})
                    : List.of(new int[] {Blocks.FLESH_MEMBRANE, 1});
            default -> null;
        };
    }
}
