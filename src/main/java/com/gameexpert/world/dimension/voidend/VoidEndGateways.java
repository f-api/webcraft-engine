package com.gameexpert.world.dimension.voidend;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * [END-GATEWAY] 바닐라 {@code TheEndGatewayBlockEntity} 의 출구 탐색·섬 생성·관문 배치를 <b>살아 있는 월드</b>
 * 위에서 푸는 순수 규칙. 정적판 {@code client/src/world/dimensions/voidEnd/VoidEndGateways.ts} 와 같은 순서·
 * 같은 연산을 줄 단위로 옮긴 사본이다.
 *
 * <p>근거(핀 26.3-snapshot-7 client jar, javap):
 * <ul>
 * <li>{@code getPortalPosition}: 출구가 없고 End 이면 {@code findOrCreateValidTeleportPos(level, pos).above(10)}
 * 에 {@code EndGatewayFeature.knownExit(원래 관문, false)} 를 놓고 원래 관문의 출구로 기록한다. 출구가 있으면
 * 정확 이동이면 그 칸, 아니면 {@code findExitPosition} = {@code findTallestBlock(exit + (0,2,0), 5, false).above()}
 * 을 <b>매 이동마다</b> 현재 월드에서 다시 푼다. 목적지는 {@code Vec3.atBottomCenterOf}.</li>
 * <li>{@code findExitPortalXZPosTentative}: (x, 0, z) 정규화 × 1024, 비지 않은 청크면 16칸씩 최대 16번
 * 뒤로, 빈 청크면 16칸씩 최대 16번 앞으로({@code LevelChunk.getHighestFilledSectionIndex() == -1}).</li>
 * <li>{@code findValidSpawnInChunk}: (minX, 30, minZ)..(maxX, 최고 구역 바닥 + 15, maxZ) 를
 * {@code BlockPos.betweenClosed}(x 가장 빠름, 그다음 y, z 가장 느림) 로 돌며 END_STONE 이고 위 두 칸이
 * {@code isCollisionShapeFullBlock} 이 아닌 칸 중 원점 {@code distToCenterSqr} 가 가장 작은 첫 칸.</li>
 * <li>없으면 {@code BlockPos.containing(x + 0.5, 75, z + 0.5)} 에 {@code EndFeatures.END_ISLAND} 를
 * {@code RandomSource.create(pos.asLong())} 로 놓는다({@link VoidEndGenerator} 의 EndIslandFeature 사본).</li>
 * <li>그 뒤 {@code findTallestBlock(level, pos, 16, true)}: i(x) 바깥·j(z) 안쪽, 각 열 maxY 에서 내려가며
 * 현재 최고보다 위에서만 {@code isCollisionShapeFullBlock}(기반암 허용 여부) 첫 칸.</li>
 * </ul>
 * 월드 경계는 WebCraft 차원의 실제 높이({@link Blocks#MIN_Y}..{@link Blocks#MAX_Y}) 다. 생성 지형은 0..255 밖에
 * 블록을 두지 않으므로 편집 없는 월드에서는 바닐라 End(0..255) 와 같은 답이다.
 */
public final class VoidEndGateways {
    /** {@code TheEndGatewayBlockEntity}: SPAWN_TIME 200(빔은 클라 endGatewayBeam 이 소유), COOLDOWN_TIME 40, ATTENTION_INTERVAL 2400 (게임 틱). */
    public static final int COOLDOWN_TIME = 40;
    public static final int ATTENTION_INTERVAL = 2400;
    /** GATEWAY_HEIGHT_ABOVE_SURFACE. */
    public static final int GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    /** findOrCreateValidTeleportPos 의 섬 높이(BlockPos.containing(x + .5, 75, z + .5)). */
    public static final int ISLAND_Y = 75;

    private VoidEndGateways() {
    }

    /** 살아 있는 월드 조회. 호출 전에 필요한 청크가 모두 상주해야 한다. */
    public interface Level {
        int getBlock(int x, int y, int z);

        int getState(int x, int y, int z);

        /** {@code BlockState.isCollisionShapeFullBlock}. */
        boolean fullBlock(int block, int state);
    }

    /** 블록 한 칸 쓰기(섬·관문 배치). */
    public interface BlockSink {
        void set(int x, int y, int z, int block);
    }

    private static boolean full(Level level, int x, int y, int z) {
        int block = level.getBlock(x, y, z);
        return block != Blocks.AIR && level.fullBlock(block, level.getState(x, y, z));
    }

    /** {@code getHighestFilledSectionIndex() == -1}: 청크 전 높이에 공기 아닌 칸이 하나도 없다. */
    public static boolean chunkEmpty(Level level, int chunkX, int chunkZ) {
        return highestNonAirY(level, chunkX, chunkZ) == Integer.MIN_VALUE;
    }

    private static int highestNonAirY(Level level, int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int x = minX; x < minX + 16; x++) {
                    if (level.getBlock(x, y, z) != Blocks.AIR) return y;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** {@code LevelChunk.getHighestSectionPosition()}: 비지 않은 가장 높은 16칸 구역의 바닥 Y(없으면 minY). */
    public static int highestSectionBottom(Level level, int chunkX, int chunkZ) {
        int y = highestNonAirY(level, chunkX, chunkZ);
        return y == Integer.MIN_VALUE ? Blocks.MIN_Y : Math.floorDiv(y, 16) * 16;
    }

    /** {@code findTallestBlock}. 찾지 못하면 기준 좌표를 돌려준다. */
    public static int[] findTallestBlock(Level level, int px, int py, int pz, int radius, boolean allowBedrock) {
        int[] best = null;
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (i == 0 && j == 0 && !allowBedrock) continue;
                for (int y = Blocks.MAX_Y; y > (best == null ? Blocks.MIN_Y : best[1]); y--) {
                    int block = level.getBlock(px + i, y, pz + j);
                    if (block != Blocks.AIR && level.fullBlock(block, level.getState(px + i, y, pz + j))
                            && (allowBedrock || block != Blocks.BEDROCK)) {
                        best = new int[] {px + i, y, pz + j};
                        break;
                    }
                }
            }
        }
        return best == null ? new int[] {px, py, pz} : best;
    }

    /** {@code findExitPosition}: 출구 두 칸 위 기준 반경 5(기반암 제외) 최고 칸의 한 칸 위. */
    public static int[] findExitPosition(Level level, int exitX, int exitY, int exitZ) {
        int[] tallest = findTallestBlock(level, exitX, exitY + 2, exitZ, 5, false);
        return new int[] {tallest[0], tallest[1] + 1, tallest[2]};
    }

    /** {@code findValidSpawnInChunk}. 없으면 null. */
    public static int[] findValidSpawnInChunk(Level level, int chunkX, int chunkZ) {
        int top = highestSectionBottom(level, chunkX, chunkZ) + 16 - 1;
        int[] best = null;
        double bestDistance = 0.0;
        for (int z = chunkZ * 16; z <= chunkZ * 16 + 15; z++) {
            for (int y = 30; y <= top; y++) {
                for (int x = chunkX * 16; x <= chunkX * 16 + 15; x++) {
                    if (level.getBlock(x, y, z) != Blocks.END_STONE) continue;
                    if (full(level, x, y + 1, z) || full(level, x, y + 2, z)) continue;
                    double dx = x + 0.5;
                    double dy = y + 0.5;
                    double dz = z + 0.5;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (best == null || distance < bestDistance) {
                        best = new int[] {x, y, z};
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** (x, 0, z) 의 {@code Vec3.normalize}. 길이 1e-5 미만이면 0 벡터. */
    private static double[] direction(int gatewayX, int gatewayZ) {
        double length = Math.sqrt((double) gatewayX * gatewayX + 0.0 * 0.0 + (double) gatewayZ * gatewayZ);
        if (length < 1.0E-5) return new double[] {0.0, 0.0};
        return new double[] {gatewayX / length, gatewayZ / length};
    }

    private static int floor16(double value) {
        return (int) Math.floor(value / 16.0);
    }

    /**
     * {@code findExitPortalXZPosTentative} 가 읽을 수 있는 모든 청크. 뒤로 n(0..16)번 뺀 점과, 그 점에서 앞으로
     * 1..16번 더한 점을 탐색과 같은 double 연산 순서로 모두 만든다(뺀 뒤 더한 점은 반올림 때문에 앞으로만 간
     * 점과 다를 수 있다). 탐색 경로가 데이터에 따라 달라도 이 집합 밖은 읽지 않는다. [chunkX, chunkZ], 중복 없음.
     */
    public static List<int[]> rayChunks(int gatewayX, int gatewayZ) {
        double[] v = direction(gatewayX, gatewayZ);
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> chunks = new ArrayList<>();
        double bx = v[0] * 1024.0;
        double bz = v[1] * 1024.0;
        for (int back = 0; back <= 16; back++) {
            if (back > 0) {
                bx = bx + v[0] * -16.0;
                bz = bz + v[1] * -16.0;
            }
            addChunk(seen, chunks, bx, bz);
            double fx = bx;
            double fz = bz;
            for (int forward = 0; forward < 16; forward++) {
                fx = fx + v[0] * 16.0;
                fz = fz + v[1] * 16.0;
                addChunk(seen, chunks, fx, fz);
            }
        }
        return chunks;
    }

    private static void addChunk(Set<Long> seen, List<int[]> chunks, double x, double z) {
        int cx = floor16(x);
        int cz = floor16(z);
        if (seen.add(((long) cx << 32) ^ (cz & 0xffffffffL))) chunks.add(new int[] {cx, cz});
    }

    /** {@code findExitPortalXZPosTentative}: [x, z] (double). */
    public static double[] findExitPortalXZPosTentative(Level level, int gatewayX, int gatewayZ) {
        double[] v = direction(gatewayX, gatewayZ);
        double px = v[0] * 1024.0;
        double pz = v[1] * 1024.0;
        for (int i = 16; !chunkEmpty(level, floor16(px), floor16(pz)) && i-- > 0; ) {
            px = px + v[0] * -16.0;
            pz = pz + v[1] * -16.0;
        }
        for (int i = 16; chunkEmpty(level, floor16(px), floor16(pz)) && i-- > 0; ) {
            px = px + v[0] * 16.0;
            pz = pz + v[1] * 16.0;
        }
        return new double[] {px, pz};
    }

    /** 첫 탐색 결과: 섬을 새로 세워야 하는지와 findTallestBlock 기준점. */
    public record SpawnSearch(boolean island, int x, int y, int z) {
    }

    /** {@code findOrCreateValidTeleportPos} 의 앞 절반(섬 배치 전). */
    public static SpawnSearch findSpawn(Level level, int gatewayX, int gatewayZ) {
        double[] xz = findExitPortalXZPosTentative(level, gatewayX, gatewayZ);
        int[] spawn = findValidSpawnInChunk(level, floor16(xz[0]), floor16(xz[1]));
        if (spawn != null) return new SpawnSearch(false, spawn[0], spawn[1], spawn[2]);
        return new SpawnSearch(true, (int) Math.floor(xz[0] + 0.5), ISLAND_Y, (int) Math.floor(xz[1] + 0.5));
    }

    /** 섬 배치·출구 관문 배치·목적지 계산에 필요한 청크 상자(기준점 ± 22 블록). [minCX, minCZ, maxCX, maxCZ]. */
    public static int[] exitNeighborhood(int x, int z) {
        int reach = 16 + GATEWAY_HEIGHT_ABOVE_SURFACE + 6;
        return new int[] {Math.floorDiv(x - reach, 16), Math.floorDiv(z - reach, 16),
            Math.floorDiv(x + reach, 16), Math.floorDiv(z + reach, 16)};
    }

    /** {@code findExitPosition} 이 읽는 청크 상자(출구 ± 6). */
    public static int[] exitPositionNeighborhood(int x, int z) {
        return new int[] {Math.floorDiv(x - 6, 16), Math.floorDiv(z - 6, 16),
            Math.floorDiv(x + 6, 16), Math.floorDiv(z + 6, 16)};
    }

    /** {@code BlockPos.asLong} 의 하위 48비트(LegacyRandom 시드는 이 48비트만 쓴다). */
    public static long blockPosLow48(int x, int y, int z) {
        return ((long) (x & 0x3FF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** {@code EndFeatures.END_ISLAND} 를 {@code RandomSource.create(pos.asLong())} 로. */
    public static void placeIsland(BlockSink sink, int x, int y, int z) {
        VoidEndGenerator.endIsland(sink::set, new VoidEndGenerator.Random48(blockPosLow48(x, y, z)), x, y, z);
    }

    /** 관문 한 칸의 배치 결과. */
    public record Cell(int x, int y, int z, int block) {
    }

    /**
     * {@code EndGatewayFeature.place}: (−1,−2,−1)..(1,2,1) 을 {@code betweenClosed} 순서(x, y, z)로 돌며 가운데는
     * 관문, 같은 층은 공기, 위아래 두 칸 가운데와 위아래 한 칸 십자는 기반암, 나머지는 공기.
     */
    public static List<Cell> gatewayCells(int cx, int cy, int cz) {
        List<Cell> cells = new ArrayList<>(45);
        for (int z = cz - 1; z <= cz + 1; z++) {
            for (int y = cy - 2; y <= cy + 2; y++) {
                for (int x = cx - 1; x <= cx + 1; x++) {
                    boolean bx = x == cx;
                    boolean by = y == cy;
                    boolean bz = z == cz;
                    boolean edge = Math.abs(y - cy) == 2;
                    int block;
                    if (bx && by && bz) block = Blocks.END_GATEWAY;
                    else if (by) block = Blocks.AIR;
                    else if (edge && bx && bz) block = Blocks.BEDROCK;
                    else if ((bx || bz) && !edge) block = Blocks.BEDROCK;
                    else block = Blocks.AIR;
                    cells.add(new Cell(x, y, z, block));
                }
            }
        }
        return cells;
    }

    /** {@code Vec3.atBottomCenterOf}. */
    public static double[] bottomCenter(int x, int y, int z) {
        return new double[] {x + 0.5, y, z + 0.5};
    }
}
