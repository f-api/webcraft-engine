package com.gameexpert.engine.structure;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.terrain.Blocks;

/** Removes detached ruin fragments after all walls, floors and supports have been planned.
 * Edge/corner contact is retained for diagonal stairs and tent roofs. This is deliberately
 * conservative: it does not simulate gravity or replace the generator's terrain admission.
 */
public final class SurfaceStructureConnectivity {
    private SurfaceStructureConnectivity() { }

    public static List<RuinGenerator.Voxel> grounded(List<RuinGenerator.Voxel> plan,
            SurfaceDecorator.BlockView world) {
        Map<BlockPos, RuinGenerator.Voxel> fabric = new HashMap<>();
        Set<BlockPos> clearance = new HashSet<>();
        Set<BlockPos> material = new HashSet<>();
        Set<BlockPos> decorations = new HashSet<>();
        for (var voxel : plan) {
            if (voxel.blockType() == Blocks.AIR) clearance.add(voxel.pos());
            else {
                fabric.put(voxel.pos(), voxel);
                // Planned log pillars bear weight; trees in the terrain are still excluded below.
                if (StructureTerrainRules.isStableGround(voxel.blockType())
                        || BlockFamilies.isWoodLog(voxel.blockType())) material.add(voxel.pos());
                else if (!Fluids.isFluid(voxel.blockType())) decorations.add(voxel.pos());
            }
        }
        Set<BlockPos> grounded = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (var voxel : plan) {
            if (!material.contains(voxel.pos())) continue;
            BlockPos position = voxel.pos();
            BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
            if (!fabric.containsKey(below) && !clearance.contains(below)
                    && StructureTerrainRules.isStableGround(world.getBlock(below.x(), below.y(), below.z()))) {
                grounded.add(position);
                queue.add(position);
            }
        }
        connect(material, grounded, queue);
        // Decorations can attach to standing fabric or a real floor, but kelp/cobweb chains
        // cannot make an otherwise detached roof count as supported construction.
        for (var voxel : plan) {
            BlockPos position = voxel.pos();
            if (grounded.contains(position)) {
                queue.addLast(position);
                continue;
            }
            if (!decorations.contains(position)) continue;
            BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
            if (!fabric.containsKey(below) && !clearance.contains(below)
                    && StructureTerrainRules.isStableGround(world.getBlock(below.x(), below.y(), below.z()))
                    && grounded.add(position)) queue.addLast(position);
        }
        connect(decorations, grounded, queue);
        if (grounded.size() == fabric.size()) return plan;
        // Preserve source ordering, block states, clearance and loot positions of the standing ruin.
        return plan.stream().filter(v -> v.blockType() == Blocks.AIR || grounded.contains(v.pos())).toList();
    }

    private static void connect(Set<BlockPos> eligible, Set<BlockPos> grounded,
            ArrayDeque<BlockPos> queue) {
        while (!queue.isEmpty()) {
            BlockPos position = queue.removeFirst();
            for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos next = new BlockPos(position.x() + dx, position.y() + dy, position.z() + dz);
                if (eligible.contains(next) && grounded.add(next)) queue.addLast(next);
            }
        }
    }
}
