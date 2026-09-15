package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.RuinLootProfile;
import com.gameexpert.engine.structure.StructureAabb;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * Deterministic project-specific camping site assembled around a shared fire.
 * Every write remains an AIR-only runtime overlay; uneven or obstructed shelter footprints are
 * rejected instead of producing warped or tree-intersecting tents.
 */
public final class CampGenerator implements StructureOverlayGenerator {
    private static final int LAYOUT = 0x43414d50;
    private static final int SHELTER = 0x54454e54;
    private static final int DETAIL = 0x50524f50;
    private static final int AGE = 0x41474544;
    private static final int SHELTER_VARIANTS = 6;
    private static final int FIRE_VARIANT_SHIFT = 11;
    private static final int FIRE_VARIANT_MASK = 0x0f;
    private static final int COLD_FIRE_VARIANTS = 4;

    private static final int[][] CAMPING_SLOTS = {
            {0, -5}, {5, 0}, {0, 5}, {-5, 0}, {4, 4}, {-4, 4}, {4, -4}, {-4, -4}
    };
    private static final int[][] WORKSITE_SLOTS = {
            {0, -5}, {4, -4}, {5, 0}, {4, 4}, {0, 5}, {-4, 4}, {-5, 0}, {-4, -4},
            {0, -3}, {3, -3}, {3, 0}, {3, 3}, {0, 3}, {-3, 3}, {-3, 0}, {-3, -3}
    };
    private static final SurfaceDecorator.BlockView PRE_RASTER_PLANE =
            (x, y, z) -> y == 0 ? Blocks.GRASS : y < 0 ? Blocks.DIRT : Blocks.AIR;

    /**
     * Terrain-free site box selected by the original procedural generator. The abstract level plane
     * gives the planner only stable support and AIR headroom, so its own piece collision, retry,
     * direction, size and detail decisions run unchanged without manufacturing a terrain fact.
     */
    public static List<StructureAabb> preRasterHorizontalPieceBounds(
            StructureSiteDescriptor site) {
        if (site.kind() != StructureSiteDescriptor.Kind.CAMPING_SITE) {
            throw new IllegalArgumentException("not a surface camp: " + site.kind());
        }
        List<RuinGenerator.Voxel> plan = new CampGenerator().plan(site, PRE_RASTER_PLANE);
        if (plan.isEmpty()) throw new IllegalStateException("pre-raster camp plan is empty");
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RuinGenerator.Voxel voxel : plan) {
            minX = Math.min(minX, voxel.x());
            minZ = Math.min(minZ, voxel.z());
            maxX = Math.max(maxX, voxel.x());
            maxZ = Math.max(maxZ, voxel.z());
        }
        return List.of(new StructureAabb(minX, Blocks.MIN_Y, minZ,
                maxX, Blocks.MAX_Y, maxZ));
    }

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        if (site.kind() != StructureSiteDescriptor.Kind.CAMPING_SITE) {
            throw new IllegalArgumentException("surface camp generator cannot plan " + site.kind());
        }
        Planner planner = new Planner(site, world);
        Palette palette = planner.palette();
        int age = ageFor(site);
        boolean hasLoot = RuinLootProfile.hasLoot(
                site.seed(), site.anchorX(), site.anchorZ(), site.kind());
        if (!fireCircle(planner, initialCampfireState(site, hasLoot), palette, age)) {
            return List.of();
        }

        int shelters = 1 + site.sizeClass();
        int[][] slots = CAMPING_SLOTS;
        int slotOffset = Math.floorMod(site.partLane0(0, LAYOUT), slots.length);
        int built = 0;
        for (int attempt = 0; attempt < slots.length && built < shelters; attempt++) {
            int[] slot = slots[(slotOffset + attempt * 3) % slots.length];
            if (shelter(planner, built, slot[0], slot[1], palette, age)) {
                built++;
            }
        }

        travelerDetails(planner, palette, age);
        perimeterDetails(planner, palette, age);
        return planner.voxels();
    }

    private int initialCampfireState(StructureSiteDescriptor site, boolean hasLoot) {
        int variant = (site.shapeLane() >>> FIRE_VARIANT_SHIFT) & FIRE_VARIANT_MASK;
        return hasLoot || variant >= COLD_FIRE_VARIANTS
                ? BuildingBlockRules.CAMPFIRE_LIT : 0;
    }

    private boolean fireCircle(Planner p, int campfireState, Palette palette, int age) {
        int base = p.levelBase(0, 0, 2, 2, 1);
        if (base < Blocks.MIN_Y) return false;
        p.put(0, base + 1, 0, Blocks.CAMPFIRE, campfireState);
        int[][] ring = {
                {-1, -1}, {0, -1}, {1, -1}, {-1, 0},
                {1, 0}, {-1, 1}, {0, 1}, {1, 1}
        };
        for (int index = 0; index < ring.length; index++) {
            int[] cell = ring[index];
            int block = (index & 1) == 0
                    ? (age >= 2 ? Blocks.MOSSY_COBBLE_SLAB : Blocks.COBBLE_SLAB)
                    : Blocks.MOSSY_COBBLE_WALL;
            p.foundation(cell[0], cell[1], base, block);
        }
        int hubLane = p.site.partLane0(31, DETAIL);
        if ((hubLane & 1) == 0) {
            p.foundation(-2, 0, base, palette.logZ());
            p.foundation(2, 0, base, palette.logZ());
            p.foundation(0, -2, base, palette.slab());
            p.foundation(0, 2, base, palette.slab());
        } else {
            p.foundation(-2, 0, base, palette.slab());
            p.foundation(2, 0, base, palette.slab());
            p.foundation(0, -2, base, palette.logX());
            p.foundation(0, 2, base, palette.logX());
        }
        return true;
    }

    private boolean shelter(Planner p, int index, int centerX, int centerZ,
            Palette palette, int age) {
        int lane = p.site.partLane0(index, SHELTER);
        int variant = shelterVariant(lane);
        boolean ridgeAlongX = Math.abs(centerX) > Math.abs(centerZ)
                || (Math.abs(centerX) == Math.abs(centerZ) && (lane & 1) == 0);
        int halfLong = 2;
        int halfShort = 2;
        int halfX = ridgeAlongX ? halfLong : halfShort;
        int halfZ = ridgeAlongX ? halfShort : halfLong;
        int base = p.levelBase(centerX, centerZ, halfX, halfZ, 6);
        if (base < Blocks.MIN_Y) return false;

        int floor = ((lane >>> 4) & 1) == 0 ? palette.plank() : Blocks.BROWN_TERRACOTTA;
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                p.foundation(centerX + x, centerZ + z, base, floor);
            }
        }

        int canvas = tentCanvas(p.site, lane, palette);
        int accent = canvas == palette.canvasA() ? palette.canvasB() : palette.canvasA();
        int length = ridgeAlongX ? halfX : halfZ;
        int front = entranceEnd(centerX, centerZ, ridgeAlongX, lane) * length;
        switch (variant) {
            case 0 -> aFrame(p, centerX, centerZ, base, ridgeAlongX, length,
                    front, lane, age, canvas, accent, palette);
            case 1 -> lowRidge(p, centerX, centerZ, base, ridgeAlongX, length,
                    front, lane, age, canvas, accent, palette);
            case 2 -> leanTo(p, centerX, centerZ, base, ridgeAlongX, length,
                    front, lane, age, canvas, accent, palette);
            case 3 -> canopy(p, centerX, centerZ, base, ridgeAlongX, length,
                    front, lane, age, canvas, accent, palette);
            case 4 -> openBedroll(p, centerX, centerZ, base, ridgeAlongX,
                    front, lane, age, canvas, accent, palette);
            default -> collapsedFrame(p, centerX, centerZ, base, ridgeAlongX, length,
                    front, lane, canvas, accent, palette);
        }

        p.putBed(centerX, base + 2, centerZ, ridgeAlongX ? 1 : 2);
        int propX = localX(centerX, ridgeAlongX, -1, 1);
        int propZ = localZ(centerZ, ridgeAlongX, -1, 1);
        int prop = ((lane >>> 10) & 1) == 0 ? Blocks.HAY_BLOCK : palette.slab();
        p.put(propX, base + 2, propZ, prop);
        if ((lane & 0x2000) != 0) {
            p.put(localX(centerX, ridgeAlongX, -front + Integer.signum(front), -1),
                    base + 2,
                    localZ(centerZ, ridgeAlongX, -front + Integer.signum(front), -1),
                    Blocks.TORCH);
        }
        pathToHub(p, centerX, centerZ, palette, lane);
        return true;
    }

    static int shelterVariant(int lane) {
        return Math.floorMod(lane >>> 1, SHELTER_VARIANTS);
    }

    static int ageFor(StructureSiteDescriptor site) {
        return Math.floorMod(site.partLane1(0, AGE) >>> 3, 4);
    }

    private int entranceEnd(int centerX, int centerZ, boolean alongX, int lane) {
        int radial = alongX ? centerX : centerZ;
        return radial > 0 ? -1 : radial < 0 ? 1 : (lane & 0x4000) == 0 ? -1 : 1;
    }

    private int tentCanvas(StructureSiteDescriptor site, int lane, Palette palette) {
        return switch ((lane >>> 5) & 3) {
            case 0 -> palette.canvasA();
            case 1 -> palette.canvasB();
            case 2 -> Blocks.YELLOW_TERRACOTTA;
            default -> Blocks.BROWN_TERRACOTTA;
        };
    }

    private void aFrame(Planner p, int cx, int cz, int base, boolean alongX, int length,
            int front, int lane, int age, int canvas, int accent, Palette palette) {
        ridgePosts(p, cx, cz, base, alongX, length, front, palette.fence());
        for (int along = -length; along <= length; along++) {
            roof(p, cx, cz, base, alongX, along, -2, 3, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, 2, 3, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, -1, 4, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, 1, 4, lane, age, canvas, accent);
            putLocal(p, cx, cz, alongX, along, 0, base + 5,
                    ((lane >>> 8) & 1) == 0 ? palette.slab() : canvas);
        }
        backFlap(p, cx, cz, base, alongX, -front, lane, canvas, accent);
    }

    private void lowRidge(Planner p, int cx, int cz, int base, boolean alongX, int length,
            int front, int lane, int age, int canvas, int accent, Palette palette) {
        ridgePosts(p, cx, cz, base, alongX, length, front, palette.fence());
        for (int along = -length; along <= length; along++) {
            roof(p, cx, cz, base, alongX, along, -2, 4, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, 2, 4, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, -1, 5, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, 0, 5, lane, age, canvas, accent);
            roof(p, cx, cz, base, alongX, along, 1, 5, lane, age, canvas, accent);
        }
        backFlap(p, cx, cz, base, alongX, -front, lane, canvas, accent);
    }

    private void leanTo(Planner p, int cx, int cz, int base, boolean alongX, int length,
            int front, int lane, int age, int canvas, int accent, Palette palette) {
        int high = (lane & 0x10000) == 0 ? -2 : 2;
        for (int end : new int[] {-length, length}) {
            for (int y = base + 2; y <= base + 5; y++) {
                putLocal(p, cx, cz, alongX, end, high, y, palette.fence());
            }
            for (int y = base + 2; y <= base + 4; y++) {
                putLocal(p, cx, cz, alongX, end, -high, y, palette.fence());
            }
        }
        for (int along = -length; along <= length; along++) {
            for (int cross = -2; cross <= 2; cross++) {
                int height = base + 5 + (cross == high ? 1 : 0);
                roof(p, cx, cz, base, alongX, along, cross, height - base,
                        lane, age, canvas, accent);
            }
        }
        backFlap(p, cx, cz, base, alongX, -front, lane, canvas, accent);
    }

    private void canopy(Planner p, int cx, int cz, int base, boolean alongX, int length,
            int front, int lane, int age, int canvas, int accent, Palette palette) {
        for (int along : new int[] {-length, length}) {
            for (int cross : new int[] {-2, 2}) {
                for (int y = base + 2; y <= base + 4; y++) {
                    putLocal(p, cx, cz, alongX, along, cross, y, palette.fence());
                }
            }
        }
        for (int along = -length; along <= length; along++) {
            for (int cross = -2; cross <= 2; cross++) {
                roof(p, cx, cz, base, alongX, along, cross,
                        5 + ((along + length) & 1), lane, age, canvas, accent);
            }
        }
        backFlap(p, cx, cz, base, alongX, -front, lane, canvas, accent);
    }

    private void openBedroll(Planner p, int cx, int cz, int base, boolean alongX,
            int front, int lane, int age, int canvas, int accent, Palette palette) {
        int back = -front;
        for (int cross : new int[] {-2, 2}) {
            for (int y = base + 2; y <= base + 4; y++) {
                putLocal(p, cx, cz, alongX, back, cross, y, palette.fence());
            }
        }
        for (int along = Math.min(0, back); along <= Math.max(0, back); along++) {
            for (int cross = -2; cross <= 2; cross++) {
                roof(p, cx, cz, base, alongX, along, cross, 5,
                        lane, Math.max(1, age), canvas, accent);
            }
        }
        backFlap(p, cx, cz, base, alongX, back, lane, canvas, accent);
    }

    private void collapsedFrame(Planner p, int cx, int cz, int base, boolean alongX, int length,
            int front, int lane, int canvas, int accent, Palette palette) {
        ridgePosts(p, cx, cz, base, alongX, length, front, palette.fence());
        for (int along = -length; along <= length; along++) {
            roof(p, cx, cz, base, alongX, along, -2, 3, lane, 3, canvas, accent);
            roof(p, cx, cz, base, alongX, along, -1, 4, lane, 3, canvas, accent);
            if ((along & 1) == 0 || along == 0) {
                putLocal(p, cx, cz, alongX, along, 0, base + 5, palette.slab());
            }
            roof(p, cx, cz, base, alongX, along, 1, 4, lane, 3, canvas, accent);
            if ((along & 1) == 0) {
                roof(p, cx, cz, base, alongX, along, 2, 3, lane, 3, canvas, accent);
            }
        }
        putLocal(p, cx, cz, alongX, -front, -1, base + 3, Blocks.COBWEB);
        p.foundation(localX(cx, alongX, front, 3), localZ(cz, alongX, front, 3),
                base, palette.log());
    }

    private void ridgePosts(Planner p, int cx, int cz, int base, boolean alongX,
            int length, int front, int fence) {
        for (int end : new int[] {-length, length}) {
            if (end == front) {
                putLocal(p, cx, cz, alongX, end, -2, base + 2, fence);
                putLocal(p, cx, cz, alongX, end, 2, base + 2, fence);
                continue;
            }
            for (int y = base + 2; y <= base + 4; y++) {
                putLocal(p, cx, cz, alongX, end, 0, y, fence);
            }
        }
    }

    private void backFlap(Planner p, int cx, int cz, int base, boolean alongX,
            int back, int lane, int canvas, int accent) {
        for (int cross = -1; cross <= 1; cross++) {
            putLocal(p, cx, cz, alongX, back, cross, base + 2,
                    ((lane >>> (cross + 3)) & 1) == 0 ? canvas : accent);
            if (cross != 0) {
                putLocal(p, cx, cz, alongX, back, cross, base + 3, canvas);
            }
        }
    }

    private void roof(Planner p, int cx, int cz, int base, boolean alongX, int along,
            int cross, int height, int lane, int age, int canvas, int accent) {
        if (missingPanel(lane, along, cross, age)) return;
        int bit = Math.floorMod(along * 3 + cross * 5, 24);
        int block = age >= 1 && ((lane >>> bit) & 1) != 0 ? accent : canvas;
        putLocal(p, cx, cz, alongX, along, cross, base + height, block);
    }

    private boolean missingPanel(int lane, int along, int cross, int age) {
        if (age < 2 || (along == 0 && Math.abs(cross) <= 1)) return false;
        int bit = Math.floorMod(along * 7 + cross * 11, 24);
        int mask = age == 2 ? 3 : 1;
        return ((lane >>> bit) & mask) == mask;
    }

    private void pathToHub(Planner p, int centerX, int centerZ, Palette palette, int lane) {
        int x = centerX;
        int z = centerZ;
        int step = 0;
        while (Math.abs(x) > 2 || Math.abs(z) > 2) {
            if ((step & 1) == 0 && x != 0) x -= Integer.signum(x);
            else if (z != 0) z -= Integer.signum(z);
            else if (x != 0) x -= Integer.signum(x);
            if ((step++ + (lane & 3)) % 4 != 0) p.addSurface(x, z, palette.path());
        }
    }

    private static int localX(int centerX, boolean alongX, int along, int cross) {
        return centerX + (alongX ? along : cross);
    }

    private static int localZ(int centerZ, boolean alongX, int along, int cross) {
        return centerZ + (alongX ? cross : along);
    }

    private void putLocal(Planner p, int cx, int cz, boolean alongX, int along, int cross,
            int y, int block) {
        p.put(localX(cx, alongX, along, cross), y,
                localZ(cz, alongX, along, cross), block);
    }

    private void travelerDetails(Planner p, Palette palette, int age) {
        int lane = p.site.partLane1(0, DETAIL);
        int side = (lane & 1) == 0 ? -1 : 1;
        boolean alongX = (lane & 8) == 0;
        int start = Math.floorMod(lane >>> 6, WORKSITE_SLOTS.length);
        for (int attempt = 0; attempt < WORKSITE_SLOTS.length; attempt++) {
            int[] candidate = WORKSITE_SLOTS[(start + attempt * 5) % WORKSITE_SLOTS.length];
            int base = p.levelBase(candidate[0], candidate[1], alongX ? 1 : 0,
                    alongX ? 0 : 1, 1);
            if (base < Blocks.MIN_Y) continue;
            int firstX = candidate[0] - (alongX ? 1 : 0);
            int firstZ = candidate[1] - (alongX ? 0 : 1);
            p.foundation(firstX, firstZ, base, Blocks.CRAFTING_TABLE);
            p.foundation(candidate[0], candidate[1], base,
                    (lane & 2) == 0 ? palette.log() : palette.logX());
            break;
        }
        if ((lane & 4) != 0) p.addSurface(-side * 3, -3, Blocks.HAY_BLOCK);
        if (age >= 2) p.addSurface(-side * 4, 2, Blocks.COBWEB);
    }

    private void perimeterDetails(Planner p, Palette palette, int age) {
        int radius = 6;
        int count = 2 + p.site.sizeClass() * 2
                + Math.floorMod(p.site.partLane0(7, DETAIL), 3);
        for (int index = 0; index < count; index++) {
            int lane = p.site.partLane1(20 + index, DETAIL);
            int side = lane & 3;
            int cross = Math.floorMod(lane >>> 5, radius * 2 - 3) - radius + 2;
            int x = side == 1 ? radius : side == 3 ? -radius : cross;
            int z = side == 0 ? -radius : side == 2 ? radius : cross;
            int choice = (lane >>> 12) & 3;
            int block = switch (choice) {
                case 0 -> Blocks.HAY_BLOCK;
                case 1 -> side < 2 ? palette.logX() : palette.logZ();
                case 2 -> palette.fence();
                default -> age >= 2 ? Blocks.MOSS_BLOCK : Blocks.COBBLE;
            };
            p.addSurface(x, z, block);
            if (choice == 2) {
                // 외곽 울타리 기둥 위 조명 -> 구리 랜턴(야영지 경계등).
                int y = p.surfaceY(x, z);
                if (y >= Blocks.MIN_Y) p.put(x, y + 2, z, Blocks.COPPER_LANTERN);
            }
        }
    }

    private record Palette(int plank, int slab, int fence, int log, int logX, int logZ,
            int canvasA, int canvasB, int path) {}

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int maxReach;
        private final int cacheWidth;
        private final int[] surfaceCache;
        private final Map<BlockPos, RuinGenerator.Voxel> plan = new LinkedHashMap<>();

        private Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
            this.site = site;
            this.world = world;
            this.maxReach = site.kind().maxReach();
            this.cacheWidth = maxReach * 2 + 1;
            this.surfaceCache = new int[cacheWidth * cacheWidth];
            Arrays.fill(surfaceCache, Integer.MIN_VALUE);
        }

        /**
         * The shared generator contract has no biome oracle. The exposed top material is the
         * canonical fact both authorities can observe, so it selects only palettes whose biome
         * identity is unambiguous from that material.
         */
        private Palette palette() {
            int groundY = surfaceY(0, 0);
            int ground = groundY >= Blocks.MIN_Y ? block(0, groundY, 0) : Blocks.GRASS;
            return switch (ground) {
                case Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.PODZOL -> new Palette(
                        Blocks.SPRUCE_PLANK, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE,
                        Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG_X,
                        Blocks.STRIPPED_SPRUCE_LOG_Z, Blocks.WHITE_TERRACOTTA,
                        Blocks.BROWN_TERRACOTTA, Blocks.SPRUCE_SLAB);
                case Blocks.SAND, Blocks.SANDSTONE -> new Palette(
                        Blocks.ACACIA_PLANK, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE,
                        Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG_X,
                        Blocks.STRIPPED_ACACIA_LOG_Z, Blocks.YELLOW_TERRACOTTA,
                        Blocks.ORANGE_TERRACOTTA, Blocks.SANDSTONE_SLAB);
                case Blocks.RED_SAND, Blocks.RED_SANDSTONE, Blocks.TERRACOTTA -> new Palette(
                        Blocks.ACACIA_PLANK, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE,
                        Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG_X,
                        Blocks.STRIPPED_ACACIA_LOG_Z, Blocks.ORANGE_TERRACOTTA,
                        Blocks.RED_TERRACOTTA, Blocks.RED_SANDSTONE_SLAB);
                case Blocks.MUD, Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS -> new Palette(
                        Blocks.MANGROVE_PLANK, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_FENCE,
                        Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG_X,
                        Blocks.STRIPPED_MANGROVE_LOG_Z, Blocks.BROWN_TERRACOTTA,
                        Blocks.GREEN_TERRACOTTA, Blocks.MUD_BRICK_SLAB);
                case Blocks.MOSS_BLOCK -> new Palette(
                        Blocks.DARK_OAK_PLANK, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE,
                        Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG_X,
                        Blocks.STRIPPED_DARK_OAK_LOG_Z, Blocks.GREEN_TERRACOTTA,
                        Blocks.BROWN_TERRACOTTA, Blocks.MOSS_CARPET);
                default -> new Palette(Blocks.PLANK, Blocks.PLANK_SLAB, Blocks.WOOD_FENCE,
                        Blocks.LOG, Blocks.LOG_X, Blocks.LOG_Z, Blocks.WHITE_TERRACOTTA,
                        Blocks.ORANGE_TERRACOTTA, Blocks.COBBLE_SLAB);
            };
        }

        private int levelBase(int centerX, int centerZ, int halfX, int halfZ, int headroom) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int z = -halfZ; z <= halfZ; z++) {
                for (int x = -halfX; x <= halfX; x++) {
                    int localX = centerX + x;
                    int localZ = centerZ + z;
                    if (Math.abs(localX) > maxReach || Math.abs(localZ) > maxReach) {
                        return Blocks.MIN_Y - 1;
                    }
                    int ground = surfaceY(localX, localZ);
                    if (ground < Blocks.MIN_Y || !support(block(localX, ground, localZ))) {
                        return Blocks.MIN_Y - 1;
                    }
                    min = Math.min(min, ground);
                    max = Math.max(max, ground);
                }
            }
            if (max - min > 1 || max + headroom >= Blocks.MAX_Y) return Blocks.MIN_Y - 1;
            for (int z = -halfZ; z <= halfZ; z++) {
                for (int x = -halfX; x <= halfX; x++) {
                    int ground = surfaceY(centerX + x, centerZ + z);
                    for (int y = ground + 1; y <= max + headroom; y++) {
                        if (!open(centerX + x, y, centerZ + z)) {
                            return Blocks.MIN_Y - 1;
                        }
                    }
                }
            }
            return max;
        }

        private void foundation(int localX, int localZ, int baseY, int topBlock) {
            int ground = surfaceY(localX, localZ);
            if (ground < Blocks.MIN_Y || ground > baseY) return;
            for (int y = ground + 1; y <= baseY; y++) {
                put(localX, y, localZ, Blocks.COBBLE);
            }
            put(localX, baseY + 1, localZ, topBlock);
        }

        private void addSurface(int localX, int localZ, int block) {
            int ground = surfaceY(localX, localZ);
            if (ground >= Blocks.MIN_Y && support(this.block(localX, ground, localZ))) {
                put(localX, ground + 1, localZ, block);
            }
        }

        private int surfaceY(int localX, int localZ) {
            if (Math.abs(localX) > maxReach || Math.abs(localZ) > maxReach) {
                return Blocks.MIN_Y - 1;
            }
            int index = (localZ + maxReach) * cacheWidth + localX + maxReach;
            int cached = surfaceCache[index];
            if (cached != Integer.MIN_VALUE) return cached;
            int worldX = site.anchorX() + rotatedX(localX, localZ, site.direction());
            int worldZ = site.anchorZ() + rotatedZ(localX, localZ, site.direction());
            for (int y = Blocks.MAX_Y - 1; y >= Blocks.MIN_Y; y--) {
                int block = world.getBlock(worldX, y, worldZ);
                if (block == Blocks.AIR || vegetation(block)) continue;
                surfaceCache[index] = y;
                return y;
            }
            surfaceCache[index] = Blocks.MIN_Y - 1;
            return surfaceCache[index];
        }

        private int block(int localX, int worldY, int localZ) {
            return world.getBlock(
                    site.anchorX() + rotatedX(localX, localZ, site.direction()),
                    worldY,
                    site.anchorZ() + rotatedZ(localX, localZ, site.direction()));
        }

        private boolean open(int localX, int worldY, int localZ) {
            int worldX = site.anchorX() + rotatedX(localX, localZ, site.direction());
            int worldZ = site.anchorZ() + rotatedZ(localX, localZ, site.direction());
            return !plan.containsKey(new BlockPos(worldX, worldY, worldZ))
                    && StructureTerrainRules.isReplaceableByStructure(
                            world.getBlock(worldX, worldY, worldZ));
        }

        private void put(int localX, int y, int localZ, int block) {
            put(localX, y, localZ, block, 0);
        }

        private void put(int localX, int y, int localZ, int block, int state) {
            if (block == Blocks.AIR || y < Blocks.MIN_Y || y > Blocks.MAX_Y
                    || Math.abs(localX) > maxReach || Math.abs(localZ) > maxReach) {
                return;
            }
            BlockPos pos = new BlockPos(
                    site.anchorX() + rotatedX(localX, localZ, site.direction()),
                    y,
                    site.anchorZ() + rotatedZ(localX, localZ, site.direction()));
            if (plan.containsKey(pos)
                    || !StructureTerrainRules.isReplaceableByStructure(
                            world.getBlock(pos.x(), pos.y(), pos.z()))) {
                return;
            }
            plan.put(pos, RuinGenerator.Voxel.at(pos.x(), pos.y(), pos.z(), block, state));
        }

        private void putBed(int footX, int y, int footZ, int facing) {
            int headX = footX + (facing == 1 ? 1 : facing == 3 ? -1 : 0);
            int headZ = footZ + (facing == 2 ? 1 : facing == 0 ? -1 : 0);
            int worldFacing = (facing + site.direction()) & BuildingBlockRules.FACING_MASK;
            put(footX, y, footZ, Blocks.BED, worldFacing);
            put(headX, y, headZ, Blocks.BED,
                    worldFacing | BuildingBlockRules.BED_HEAD);
        }

        private List<RuinGenerator.Voxel> voxels() {
            List<RuinGenerator.Voxel> out = new ArrayList<>(plan.values());
            out.sort(Comparator.comparingInt(RuinGenerator.Voxel::x)
                    .thenComparingInt(RuinGenerator.Voxel::y)
                    .thenComparingInt(RuinGenerator.Voxel::z)
                    .thenComparingInt(RuinGenerator.Voxel::blockType));
            return List.copyOf(out);
        }
    }

    private static boolean support(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private static boolean vegetation(int block) {
        return StructureTerrainRules.isVegetation(block);
    }

    private static int rotatedX(int x, int z, int direction) {
        return switch (direction) {
            case 1 -> -z;
            case 2 -> -x;
            case 3 -> z;
            default -> x;
        };
    }

    private static int rotatedZ(int x, int z, int direction) {
        return switch (direction) {
            case 1 -> x;
            case 2 -> -z;
            case 3 -> -x;
            default -> z;
        };
    }
}
