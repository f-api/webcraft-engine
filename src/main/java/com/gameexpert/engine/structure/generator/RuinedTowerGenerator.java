package com.gameexpert.engine.structure.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/** A stateless, coordinate-hash composed ancient tower ruin. */
public final class RuinedTowerGenerator implements StructureOverlayGenerator {
    private static final int MATERIAL_PURPOSE = 0x72;
    private static final int PROFILE_PURPOSE = 0x73;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        int shape = site.shapeLane();
        int topology = site.topologyLane();
        int halfX = 2 + ((shape >>> 3) & 3); // 2..5 before reach-safe clamping
        int halfZ = 2 + ((shape >>> 6) & 3);
        halfX = Math.min(4, halfX);
        halfZ = Math.min(4, halfZ);
        int height = 6 + Integer.remainderUnsigned(shape ^ (shape >>> 13), 11);
        int floorStride = 3 + ((topology >>> 12) & 1);
        int doorSide = (topology >>> 2) & 3;
        int severeSide = (topology >>> 7) & 3;
        int mossDensity = 8 + Integer.remainderUnsigned(site.agingLane(), 25);

        int[][] ground = new int[halfZ * 2 + 1][halfX * 2 + 1];
        int minGround = Integer.MAX_VALUE;
        int maxGround = Integer.MIN_VALUE;
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                int[] rotated = rotate(x, z, site.direction());
                int wx = site.anchorX() + rotated[0], wz = site.anchorZ() + rotated[1];
                int y = groundY(wx, wz, world);
                if (y < Blocks.MIN_Y || !isSupport(world.getBlock(wx, y, wz))) return List.of();
                ground[z + halfZ][x + halfX] = y;
                minGround = Math.min(minGround, y);
                maxGround = Math.max(maxGround, y);
            }
        }
        if (maxGround - minGround > 2 || maxGround + height + 3 >= Blocks.MAX_Y) return List.of();

        Planner p = new Planner(site, world);
        int deckY = maxGround + 1;
        // A complete buried footing guarantees a recognizable, nonempty ruin on valid terrain.
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                for (int y = ground[z + halfZ][x + halfX] + 1; y <= deckY; y++) {
                    p.add(x, y, z, material(site, x, y, z, mossDensity));
                }
            }
        }

        int floorCount = (height - 1) / floorStride;
        int[] leanX = new int[floorCount + 2];
        int[] leanZ = new int[floorCount + 2];
        for (int floor = 1; floor < leanX.length; floor++) {
            int lane = site.partLane0(floor, 0x741);
            leanX[floor] = leanX[floor - 1];
            leanZ[floor] = leanZ[floor - 1];
            if ((lane & 7) == 0) leanX[floor] = clampLean(leanX[floor] + (((lane >>> 4) & 1) * 2 - 1));
            if (((lane >>> 5) & 7) == 0) leanZ[floor] = clampLean(leanZ[floor] + (((lane >>> 9) & 1) * 2 - 1));
        }

        // Every face segment owns a survival profile. The per-floor face masks create broad
        // collapses, while per-segment caps make the surviving top silhouette jagged.
        for (int dy = 1; dy <= height; dy++) {
            int floor = Math.min(dy / floorStride, leanX.length - 1);
            int ox = leanX[floor], oz = leanZ[floor];
            for (int side = 0; side < 4; side++) {
                int extent = side % 2 == 0 ? halfX : halfZ;
                int faceLane = site.partLane1(floor * 4 + side, 0x742);
                int collapse = 1 + ((faceLane >>> 4) & 3) + (side == severeSide ? 2 : 0);
                boolean floorBreach = floor > 0 && Integer.remainderUnsigned(faceLane, 9) < collapse;
                int breachCenter = signedRange(faceLane >>> 10, extent);
                int breachWidth = 1 + ((faceLane >>> 17) & 1);
                for (int along = -extent; along <= extent; along++) {
                    int x = side == 1 ? halfX : side == 3 ? -halfX : along;
                    int z = side == 0 ? -halfZ : side == 2 ? halfZ : along;
                    int profile = site.voxelLane(site.anchorX() + x, dy,
                            site.anchorZ() + z, PROFILE_PURPOSE + side);
                    int topLoss = Integer.remainderUnsigned(profile ^ faceLane, 5 + collapse);
                    int cap = Math.max(2, height - topLoss);
                    boolean doorway = side == doorSide && Math.abs(along) <= 1 && dy <= 2;
                    boolean broadGap = floorBreach && Math.abs(along - breachCenter) <= breachWidth
                            && dy > floor * floorStride;
                    boolean arrowGap = dy > 2 && dy < cap - 1 && (profile & 63) == 0;
                    if (dy > cap || doorway || broadGap || arrowGap) continue;
                    p.add(x + ox, deckY + dy, z + oz,
                            material(site, x + ox, deckY + dy, z + oz, mossDensity));

                    // Hanging growth hugs surviving wall pieces and inherits their irregularity.
                    if (dy > 2 && (profile >>> 8 & 255) < mossDensity * 2) {
                        int vx = x + ox + sideDx(side), vz = z + oz + sideDz(side);
                        int length = 1 + ((profile >>> 19) & 3);
                        for (int drop = 0; drop < length && dy - drop > 1; drop++) {
                            p.add(vx, deckY + dy - drop, vz, Blocks.VINE);
                        }
                    }
                }
            }
        }

        // Rot/impact removes a different wedge from every intermediate floor.
        for (int level = floorStride, floor = 1; level < height; level += floorStride, floor++) {
            int lane = site.partLane0(floor, 0x743);
            if ((lane & 7) == 0) continue;
            int ox = leanX[Math.min(floor, leanX.length - 1)];
            int oz = leanZ[Math.min(floor, leanZ.length - 1)];
            int missingSide = (lane >>> 4) & 3;
            int missingDepth = 1 + ((lane >>> 7) & 3);
            for (int z = -halfZ + 1; z < halfZ; z++) {
                for (int x = -halfX + 1; x < halfX; x++) {
                    if (towardSide(x, z, missingSide, halfX, halfZ) < missingDepth) continue;
                    int cell = site.voxelLane(site.anchorX() + x, deckY + level,
                            site.anchorZ() + z, 0x744 + floor);
                    if ((cell & 31) < 4 + ((lane >>> 14) & 7)) continue;
                    p.add(x + ox, deckY + level, z + oz, Blocks.STONE_BRICK_SLAB);
                    if ((cell >>> 8 & 255) < mossDensity) {
                        p.add(x + ox, deckY + level + 1, z + oz, Blocks.MOSS_CARPET);
                    }
                }
            }
            railFragments(p, site, deckY + level + 1, halfX, halfZ, ox, oz, floor, missingSide);
            stairFragments(p, site, deckY, level, halfX, halfZ, ox, oz, floor);
        }

        pillars(p, site, deckY, halfX, halfZ, height, mossDensity);
        cobwebs(p, site, deckY, halfX, halfZ, height);
        rubble(p, site, halfX, halfZ, severeSide, mossDensity);
        groundGrowthAndLoot(p, site, deckY, halfX, halfZ, mossDensity);
        return p.valid() && !p.voxels().isEmpty() ? p.voxels() : List.of();
    }

    private void railFragments(Planner p, StructureSiteDescriptor site, int y, int halfX,
            int halfZ, int ox, int oz, int floor, int missingSide) {
        for (int side = 0; side < 4; side++) {
            if (side == missingSide) continue;
            int extent = side % 2 == 0 ? halfX - 1 : halfZ - 1;
            int lane = site.partLane1(floor * 4 + side, 0x745);
            for (int along = -extent; along <= extent; along++) {
                if (((lane >>> ((along + extent) & 15)) & 3) != 0) continue;
                int x = side == 1 ? halfX - 1 : side == 3 ? -halfX + 1 : along;
                int z = side == 0 ? -halfZ + 1 : side == 2 ? halfZ - 1 : along;
                p.add(x + ox, y, z + oz, Blocks.WOOD_FENCE);
            }
        }
    }

    private void stairFragments(Planner p, StructureSiteDescriptor site, int baseY, int level,
            int halfX, int halfZ, int ox, int oz, int floor) {
        int lane = site.partLane0(floor, 0x746);
        int side = (lane >>> 2) & 3;
        int steps = 2 + ((lane >>> 5) & 3);
        for (int i = 0; i < steps; i++) {
            if (((lane >>> (10 + i)) & 1) != 0 && i != 0) continue;
            int run = Math.min(level - 1, i + 1);
            int x = side == 1 ? halfX - 1 - i : side == 3 ? -halfX + 1 + i : signedRange(lane >>> 16, Math.max(1, halfX - 2));
            int z = side == 0 ? -halfZ + 1 + i : side == 2 ? halfZ - 1 - i : signedRange(lane >>> 16, Math.max(1, halfZ - 2));
            p.add(x + ox, baseY + level - run, z + oz,
                    (lane & 1) == 0 ? Blocks.STONE_BRICK_STAIRS : Blocks.COBBLE_STAIRS);
        }
    }

    private void pillars(Planner p, StructureSiteDescriptor site, int y, int halfX, int halfZ,
            int height, int mossDensity) {
        int lane = site.partLane1(0, 0x747);
        int count = 2 + Integer.remainderUnsigned(lane >>> 2, 3);
        int[][] corners = {{-halfX + 1, -halfZ + 1}, {halfX - 1, -halfZ + 1},
                {halfX - 1, halfZ - 1}, {-halfX + 1, halfZ - 1}};
        for (int i = 0; i < count; i++) {
            int index = (i + (lane >>> 7)) & 3;
            int pillarLane = site.partLane0(i, 0x748);
            int rise = Math.max(3, height - Integer.remainderUnsigned(pillarLane, 5));
            for (int dy = 1; dy <= rise; dy++) {
                p.add(corners[index][0], y + dy, corners[index][1],
                        material(site, corners[index][0], y + dy, corners[index][1], mossDensity));
            }
            p.add(corners[index][0], y + rise + 1, corners[index][1], Blocks.STONE_BRICK_SLAB);
        }
    }

    private void cobwebs(Planner p, StructureSiteDescriptor site, int y, int halfX, int halfZ,
            int height) {
        int lane = site.partLane0(0, 0x749);
        int count = (lane & 3); // deliberately sparse: zero to three
        for (int i = 0; i < count; i++) {
            int part = site.partLane1(i, 0x74a);
            int x = ((part & 1) == 0 ? -halfX + 1 : halfX - 1);
            int z = ((part & 2) == 0 ? -halfZ + 1 : halfZ - 1);
            int dy = 2 + Integer.remainderUnsigned(part >>> 4, Math.max(1, height - 2));
            p.add(x, y + dy, z, Blocks.COBWEB);
        }
    }

    private void rubble(Planner p, StructureSiteDescriptor site, int halfX, int halfZ,
            int collapseSide, int mossDensity) {
        int lane = site.partLane1(0, 0x74b);
        int count = 9 + Integer.remainderUnsigned(lane, 13);
        int reachX = Math.min(5, halfX + 2), reachZ = Math.min(5, halfZ + 2);
        for (int i = 0; i < count; i++) {
            int part = site.partLane0(i, 0x74c);
            int x = signedRange(part, reachX);
            int z = signedRange(part >>> 11, reachZ);
            if (Math.abs(x) < halfX && Math.abs(z) < halfZ) {
                if ((part & 3) != 0) continue;
            }
            // Bias half the debris toward the most damaged facade.
            if ((part & 1) == 0) {
                if (collapseSide == 0) z = -Math.max(halfZ, Math.abs(z));
                if (collapseSide == 1) x = Math.max(halfX, Math.abs(x));
                if (collapseSide == 2) z = Math.max(halfZ, Math.abs(z));
                if (collapseSide == 3) x = -Math.max(halfX, Math.abs(x));
            }
            int ground = localGroundY(p, x, z);
            if (ground < Blocks.MIN_Y) continue;
            int block = switch ((part >>> 22) & 7) {
                case 0 -> Blocks.SAND;
                case 1, 2 -> Blocks.GRAVEL;
                case 3 -> Blocks.STONE;
                default -> material(site, x, ground + 1, z, mossDensity);
            };
            p.add(x, ground + 1, z, block);
            if (((part >>> 26) & 3) == 0 && ground + 2 < Blocks.MAX_Y) {
                p.add(x, ground + 2, z, (part & 4) == 0 ? Blocks.COBBLE : Blocks.STONE_BRICK);
            }
        }
    }

    private void groundGrowthAndLoot(Planner p, StructureSiteDescriptor site, int y, int halfX,
            int halfZ, int mossDensity) {
        int lane = site.partLane1(2, 0x74d);
        int growth = 2 + Integer.remainderUnsigned(lane >>> 3, 7);
        for (int i = 0; i < growth; i++) {
            int part = site.partLane0(i, 0x74e);
            int x = signedRange(part, halfX);
            int z = signedRange(part >>> 9, halfZ);
            int block = (part & 3) == 0 ? Blocks.FERN
                    : (part & 3) == 1 ? Blocks.TALL_GRASS : Blocks.MOSS_CARPET;
            p.add(x, y + 1, z, block);
        }
        // A rare cache is ringed by debris so it reads as half-buried without replacing terrain.
        if ((lane & 7) == 0) {
            int x = (lane & 8) == 0 ? halfX - 1 : -halfX + 1;
            int z = (lane & 16) == 0 ? halfZ - 1 : -halfZ + 1;
            p.add(x, y + 1, z, Blocks.CHEST);
            p.add(x + Integer.signum(-x), y + 1, z, Blocks.GRAVEL);
            p.add(x, y + 1, z + Integer.signum(-z), Blocks.COBBLE_SLAB);
        }
        if (((lane >>> 5) & 3) == 0) {
            int x = (lane & 128) == 0 ? halfX - 1 : -halfX + 1;
            int z = (lane & 256) == 0 ? 0 : (lane & 512) == 0 ? halfZ - 1 : -halfZ + 1;
            p.add(x, y + 1, z, Blocks.TORCH);
        }
    }

    private int material(StructureSiteDescriptor site, int x, int y, int z, int mossDensity) {
        int lane = site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, MATERIAL_PURPOSE);
        int roll = Integer.remainderUnsigned(lane, 100);
        int weathered = 8 + ((site.agingLane() >>> 25) & 15);
        int cobble = 18 + ((site.shapeLane() >>> 20) & 15);
        if (roll < mossDensity / 2) return Blocks.MOSSY_STONE_BRICK;
        if (roll < mossDensity) return Blocks.MOSSY_COBBLE;
        if (roll < mossDensity + weathered) return Blocks.STONE; // cracked-brick substitute
        if (roll < mossDensity + weathered + cobble) return Blocks.COBBLE;
        return Blocks.STONE_BRICK;
    }

    private int localGroundY(Planner p, int x, int z) {
        int[] r = rotate(x, z, p.site.direction());
        int wx = p.site.anchorX() + r[0], wz = p.site.anchorZ() + r[1];
        int ground = groundY(wx, wz, p.world);
        return ground >= Blocks.MIN_Y && isSupport(p.world.getBlock(wx, ground, wz)) ? ground : Blocks.MIN_Y - 1;
    }

    private int towardSide(int x, int z, int side, int halfX, int halfZ) {
        return switch (side) {
            case 0 -> z + halfZ;
            case 1 -> halfX - x;
            case 2 -> halfZ - z;
            default -> x + halfX;
        };
    }

    private int signedRange(int lane, int radius) {
        return Integer.remainderUnsigned(lane, radius * 2 + 1) - radius;
    }

    private int clampLean(int value) { return Math.max(-1, Math.min(1, value)); }
    private int sideDx(int side) { return side == 1 ? 1 : side == 3 ? -1 : 0; }
    private int sideDz(int side) { return side == 0 ? -1 : side == 2 ? 1 : 0; }

    private int groundY(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.MAX_Y - 1; y >= Blocks.MIN_Y; y--) {
            if (StructureTerrainRules.isStableGround(world.getBlock(x, y, z))) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean isSupport(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private static int[] rotate(int x, int z, int direction) {
        return switch (direction) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        private boolean valid = true;

        private Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
            this.site = site;
            this.world = world;
        }

        private void add(int x, int y, int z, int block) {
            int[] r = rotate(x, z, site.direction());
            BlockPos pos = new BlockPos(site.anchorX() + r[0], y, site.anchorZ() + r[1]);
            if (voxels.containsKey(pos)) return;
            if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y
                    || !StructureTerrainRules.isReplaceableByStructure(
                            world.getBlock(pos.x(), pos.y(), pos.z()))) {
                valid = false;
                return;
            }
            voxels.put(pos, RuinGenerator.Voxel.at(pos.x(), pos.y(), pos.z(), block));
        }

        private boolean valid() { return valid; }
        private List<RuinGenerator.Voxel> voxels() { return List.copyOf(voxels.values()); }
    }
}
