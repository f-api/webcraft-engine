package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * Stateless, additive monster dungeon overlay.  It models the small, ruined spawner rooms
 * found in vanilla caves without carving the cave: every proposed voxel must already be AIR.
 */
public final class MonsterSpawnZoneGenerator implements StructureOverlayGenerator {
    private static final int PURPOSE = 0x4d53;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        // The site key is the stable roll for the visible size spectrum: 0-3 small,
        // 4-7 medium, and 8-9 large.  All detail lanes remain independent below.
        int sizeRoll = Math.floorMod(site.siteKey(), 10);
        int sizeScale = sizeRoll < 4 ? 1 : sizeRoll < 8 ? 2 : 3;
        int halfX = sizeScale + Math.floorMod(site.shapeLane(), 2);
        int halfZ = sizeScale + Math.floorMod(site.shapeLane() >>> 4, 2);
        int wallHeight = 1 + sizeScale + Math.floorMod(site.shapeLane() >>> 8, 2);
        int floorY = caveFloorAt(site.anchorX(), site.anchorZ(), wallHeight, world);
        if (floorY < Blocks.MIN_Y || floorY >= Blocks.MAX_Y - wallHeight - 1) return List.of();
        int shape = Math.floorMod(site.topologyLane(), 5);
        int wear = Math.floorMod(site.agingLane() >>> 3, 8);
        List<RuinGenerator.Voxel> out = new ArrayList<>(64 + halfX * halfZ * wallHeight);

        // Uneven, breached perimeter walls create the room shell and its hash-selected entrance.
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                if (!edge(x, z, halfX, halfZ, shape)) continue;
                int lane = lane(site, x, 0, z, 1);
                if (Math.floorMod(lane, 13) < 2 && !corner(x, z, halfX, halfZ)) continue;
                for (int dy = 1; dy <= wallHeight; dy++) {
                    if (dy == wallHeight && Math.floorMod(lane >>> 5, 7) < 2) continue;
                    add(site, world, out, floorY, x, z, dy, wallMaterial(site, x, dy, z, wear));
                }
            }
        }

        // Partitions and narrow rubble passages vary independently from the outer shell.
        int partitionCount = sizeScale + Math.floorMod(site.topologyLane() >>> 7, 2);
        for (int p = 0; p < partitionCount; p++) {
            int pLane = site.partLane0(p, PURPOSE + 2);
            boolean acrossX = ((pLane >>> 2) & 1) == 0;
            int fixed = acrossX ? signedOffset(pLane >>> 8, halfZ) : signedOffset(pLane >>> 8, halfX);
            int span = acrossX ? halfX : halfZ;
            for (int n = -span + 1; n < span; n++) {
                int doorway = Math.floorMod(pLane >>> (n + span + 3), span * 2 - 1) - span + 1;
                if (n == doorway || (wear > 3 && Math.abs(n - doorway) == 1)) continue;
                int x = acrossX ? n : fixed;
                int z = acrossX ? fixed : n;
                add(site, world, out, floorY, x, z, 1,
                        wallMaterial(site, x, 1, z, wear + p));
                if (wallHeight > 2 && Math.floorMod(lane(site, x, 1, z, 3), 5) != 0) {
                    add(site, world, out, floorY, x, z, 2,
                            wallMaterial(site, x, 2, z, wear + p + 1));
                }
            }
        }

        // The focal spawner is always central; its mob variant is hash-selected.
        add(site, world, out, floorY, 0, 0, 1,
                Blocks.SPAWNER_BASE + Math.floorMod(site.difficultyLane(), 3));

        // Size-scaled side chests, never in the central passage, with independent placement.
        int chestCount = 1 + sizeScale
                + Math.floorMod(site.partLane1(4, PURPOSE + 4), 3);
        for (int i = 0; i < chestCount; i++) {
            int cLane = site.partLane1(i, PURPOSE + 5);
            int x = signedOffset(cLane, halfX);
            int z = signedOffset(cLane >>> 9, halfZ);
            if (Math.abs(x) + Math.abs(z) < 2) x = x == 0 ? (halfX == 1 ? 1 : halfX - 1) : x;
            add(site, world, out, floorY, x, z, 1, Blocks.CHEST);
        }

        // Torches, webs, vines and moss are sparse, hash-driven details rather than fixed props.
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                int v = lane(site, x, 2, z, 7);
                if (Math.floorMod(v, 17) < 3) add(site, world, out, floorY, x, z, 1, Blocks.COBWEB);
                if (Math.floorMod(v >>> 5, 19) < 3) add(site, world, out, floorY, x, z, 1, Blocks.MOSS_CARPET);
                if (Math.floorMod(v >>> 10, 23) < 4 && edge(x, z, halfX, halfZ, shape)) {
                    add(site, world, out, floorY, x, z, 1, Blocks.VINE);
                }
                if (Math.floorMod(v >>> 15, 29) < 4) {
                    add(site, world, out, floorY, x, z, 1,
                            Math.floorMod(v >>> 20, 5) == 0 ? Blocks.TORCH : Blocks.GRAVEL);
                }
            }
        }
        return out.size() < 8 ? List.of() : List.copyOf(out);
    }

    private boolean edge(int x, int z, int hx, int hz, int shape) {
        boolean outer = Math.abs(x) == hx || Math.abs(z) == hz;
        if (!outer) return shape == 3 && (x == 0 || z == 0) && Math.abs(x) + Math.abs(z) > 1;
        if (shape == 1 && Math.abs(x) == hx && Math.abs(z) == hz) return false;
        if (shape == 2 && Math.abs(x) + Math.abs(z) > Math.max(hx, hz) + 1) return false;
        return true;
    }

    private boolean corner(int x, int z, int hx, int hz) {
        return Math.abs(x) == hx && Math.abs(z) == hz;
    }

    private int wallMaterial(StructureSiteDescriptor site, int x, int y, int z, int wear) {
        int lane = lane(site, x, y, z, 11 + wear);
        if (Math.floorMod(lane, 11) < Math.min(5, wear)) return Blocks.GRAVEL; // cracked rubble
        return switch (Math.floorMod(lane >>> 4, 6)) {
            case 0, 1 -> Blocks.MOSSY_COBBLE;
            case 2 -> Blocks.MOSSY_STONE_BRICK;
            case 3 -> Blocks.STONE_BRICK;
            default -> Blocks.COBBLE;
        };
    }

    private int signedOffset(int lane, int half) {
        return Math.floorMod(lane, half * 2 + 1) - half;
    }

    private int lane(StructureSiteDescriptor site, int x, int y, int z, int role) {
        return site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, PURPOSE + role);
    }

    private void add(StructureSiteDescriptor site, SurfaceDecorator.BlockView world,
            List<RuinGenerator.Voxel> out, int floorY, int localX, int localZ, int dy, int block) {
        int[] rotated = rotate(localX, localZ, site.direction());
        int x = site.anchorX() + rotated[0];
        int z = site.anchorZ() + rotated[1];
        int y = floorY + dy;
        if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y || world.getBlock(x, y, z) != Blocks.AIR
                || !caveColumnAt(x, z, floorY, world)
                || contains(out, x, y, z)) return;
        out.add(RuinGenerator.Voxel.at(x, y, z, block));
    }

    private boolean contains(List<RuinGenerator.Voxel> out, int x, int y, int z) {
        for (RuinGenerator.Voxel v : out) {
            if (v.x() == x && v.y() == y && v.z() == z) return true;
        }
        return false;
    }

    private int caveFloorAt(int x, int z, int wallHeight,
            SurfaceDecorator.BlockView world) {
        for (int y = Math.min(Blocks.SEA_LEVEL - 8, Blocks.MAX_Y - wallHeight - 2);
                y >= Blocks.MIN_Y; y--) {
            if (!caveColumnAt(x, z, y, world)) continue;
            boolean enoughHeadroom = true;
            for (int dy = 1; dy <= wallHeight; dy++) {
                if (world.getBlock(x, y + dy, z) != Blocks.AIR) {
                    enoughHeadroom = false;
                    break;
                }
            }
            if (enoughHeadroom) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean caveColumnAt(int x, int z, int floorY,
            SurfaceDecorator.BlockView world) {
        return solid(world.getBlock(x, floorY, z))
                && world.getBlock(x, floorY + 1, z) == Blocks.AIR
                && world.getBlock(x, floorY + 2, z) == Blocks.AIR;
    }

    private boolean solid(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private int[] rotate(int x, int z, int direction) {
        return switch (direction) {
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            case 3 -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }
}
