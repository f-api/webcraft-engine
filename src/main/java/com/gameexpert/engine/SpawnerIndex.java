package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gameexpert.engine.mob.MobSpawner;
import com.gameexpert.engine.mob.FrogPoisonConversionRules;
import com.gameexpert.engine.mob.PlayerSnapshot;
import com.gameexpert.terrain.Blocks;

/**
 * 월드별 스포너 블록(60~62) 인덱스. 몹 스포너의 ±16 완전탐색을 대체한다(틱 스레드 전용).
 *
 * 플레이어 주변의 불변 resident snapshot을 제한된 수만 스캔하고, snapshot 정체성이 바뀐 청크만
 * 다시 색인한다. 따라서 런타임 구조물 적용과 스포너 파괴도 반영하며, 실제 소환 직전에 현재 블록을
 * 다시 확인한다.
 */
final class SpawnerIndex implements MobSpawner.SpawnerScan {

    /** 불변 청크 스냅샷 공급자. 객체 정체성이 바뀌면 해당 청크만 다시 색인한다. */
    interface ChunkSource {
        Object snapshot(int cx, int cz);
        int blockTypeAt(Object snapshot, int blockIndex);

        default boolean copyCellsTo(Object snapshot, short[] types, byte[] states) {
            return false;
        }
    }

    /** 이미 색인된 청크가 몹 틱에서 안전하게 블록을 읽을 수 있는지 확인하는 비생성 조회입니다. */
    interface ChunkAvailability {
        boolean available(int cx, int cz);
    }

    private static final int SPAWNER_MIN = 60;
    private static final int SPAWNER_MAX = 62;
    private static final int CHUNK_RADIUS = 1; // ±16블록은 ±1청크 안에 들어옵니다.
    static final int MAX_SCANS_PER_TICK = 2;

    private final ChunkSource source;
    private final ChunkAvailability availability;
    private final Map<Long, Object> scannedSnapshots = new HashMap<>();
    private final Map<Long, List<int[]>> byChunk = new HashMap<>();
    /**
     * [TRIAL] 같은 스캔 패스가 함께 담는 트라이얼 챔버 설비 좌표. 스포너 인덱스와 청크 집합·
     * 상환 예산을 공유하므로 시련 배선이 두 번째 전체 탐색을 만들지 않는다.
     */
    private final Map<Long, List<int[]>> trialSpawnersByChunk = new HashMap<>();
    private final Map<Long, List<int[]>> vaultsByChunk = new HashMap<>();
    private final Map<Long, List<int[]>> beeHivesByChunk = new HashMap<>();
    private final Map<Long, List<int[]>> rafflesiaByChunk = new HashMap<>();
    private final Map<Long, List<int[]>> fireflyBushesByChunk = new HashMap<>();
    private List<Long> lastDemandedChunks = List.of();
    /** Spawner-containing chunks only; retained across range exit to recognize later destruction. */
    private final Map<Long, List<int[]>> lastKnownSpawners = new HashMap<>();
    private final List<int[]> removedSpawners = new ArrayList<>();
    // 보류·신규 후보를 하나의 순환 대기열로 유지해 cold 앞부분이 뒤쪽의 이미 활성화된 청크를 굶기지 않는다.
    private final LinkedHashSet<Long> pendingChunks = new LinkedHashSet<>();
    private final short[] scanTypes = new short[Blocks.CHUNK_BLOCKS];
    private final byte[] scanStates = new byte[Blocks.CHUNK_BLOCKS];

    SpawnerIndex(ChunkSource source) {
        this(source, (cx, cz) -> true);
    }

    SpawnerIndex(ChunkSource source, ChunkAvailability availability) {
        this.source = source;
        this.availability = availability;
    }

    void invalidateChunk(int chunkX, int chunkZ) {
        pendingChunks.add(chunkKey(chunkX, chunkZ));
    }

    void recordBlockChanged(int x, int y, int z, int blockType) {
        long key = chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        replaceIndexedPosition(rafflesiaByChunk, key, x, y, z, blockType == Blocks.RAFFLESIA);
        replaceIndexedPosition(fireflyBushesByChunk, key, x, y, z,
                blockType == Blocks.FIREFLY_BUSH);
        pendingChunks.add(key);
    }

    private static void replaceIndexedPosition(Map<Long, List<int[]>> index, long key,
            int x, int y, int z, boolean present) {
        List<int[]> positions = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        positions.removeIf(position -> position[0] == x && position[1] == y && position[2] == z);
        if (present) positions.add(new int[] {x, y, z});
        if (positions.isEmpty()) index.remove(key);
    }

    @Override
    public List<int[]> spawnerBlocks(List<PlayerSnapshot> players) {
        // 플레이어/좌표 순서대로 유지해 여러 틱에 나눠도 같은 입력에는 같은 청크 순서로 상환한다.
        Set<Long> chunks = new TreeSet<>(SpawnerIndex::compareChunkKeys);
        for (PlayerSnapshot p : players) {
            int pcx = Math.floorDiv((int) Math.floor(p.x()), 16);
            int pcz = Math.floorDiv((int) Math.floor(p.z()), 16);
            for (int dcx = -CHUNK_RADIUS; dcx <= CHUNK_RADIUS; dcx++) {
                for (int dcz = -CHUNK_RADIUS; dcz <= CHUNK_RADIUS; dcz++) {
                    chunks.add(chunkKey(pcx + dcx, pcz + dcz));
                }
            }
        }
        // 현재 수요 청크가 resident로 바뀌었으면 최신 snapshot을 색인 대기열에 넣는다.
        for (long key : chunks) {
            Object snapshot = cachedChunk(key);
            if (snapshot != null && scannedSnapshots.get(key) != snapshot) {
                pendingChunks.add(key);
            }
        }
        scannedSnapshots.keySet().removeIf(key -> !chunks.contains(key));
        byChunk.keySet().removeIf(key -> !chunks.contains(key));
        trialSpawnersByChunk.keySet().removeIf(key -> !chunks.contains(key));
        vaultsByChunk.keySet().removeIf(key -> !chunks.contains(key));
        beeHivesByChunk.keySet().removeIf(key -> !chunks.contains(key));
        rafflesiaByChunk.keySet().removeIf(key -> !chunks.contains(key));
        fireflyBushesByChunk.keySet().removeIf(key -> !chunks.contains(key));
        lastDemandedChunks = List.copyOf(chunks);
        pendingChunks.retainAll(chunks);
        int attempts = 0;
        while (!pendingChunks.isEmpty() && attempts < MAX_SCANS_PER_TICK) {
            long key = pendingChunks.iterator().next();
            pendingChunks.remove(key);
            attempts++;
            Object snapshot = cachedChunk(key);
            if (snapshot == null) {
                continue;
            }
            scannedSnapshots.put(key, snapshot);
            List<int[]> found = scanChunk(key, snapshot);
            recordConfirmedRemovals(key, found);
            byChunk.put(key, found);
        }
        List<int[]> out = new ArrayList<>();
        for (long key : chunks) {
            List<int[]> indexed = byChunk.get(key);
            // 축출된 색인 청크는 아래 MobSpawner의 getBlock이 재생성하지 않도록 정상 접근까지 보류한다.
            if (indexed != null && availability.available((int) (key >> 32), (int) key)) out.addAll(indexed);
        }
        out.sort(SpawnerIndex::comparePositions);
        return out;
    }

    @Override
    public List<int[]> removedSpawnerBlocks() {
        if (removedSpawners.isEmpty()) return List.of();
        List<int[]> result = List.copyOf(removedSpawners);
        removedSpawners.clear();
        return result;
    }

    private void recordConfirmedRemovals(long key, List<int[]> found) {
        List<int[]> previous = lastKnownSpawners.get(key);
        if (previous != null) {
            for (int[] old : previous) {
                if (!containsPosition(found, old)) removedSpawners.add(old);
            }
        }
        if (found.isEmpty()) lastKnownSpawners.remove(key);
        else lastKnownSpawners.put(key, found);
    }

    private static boolean containsPosition(List<int[]> positions, int[] expected) {
        for (int[] position : positions) {
            if (position[0] == expected[0] && position[1] == expected[1]
                    && position[2] == expected[2]) return true;
        }
        return false;
    }

    private Object cachedChunk(long key) {
        return source.snapshot((int) (key >> 32), (int) key);
    }

    private List<int[]> scanChunk(long key, Object snapshot) {
        int cx = (int) (key >> 32);
        int cz = (int) key;
        List<int[]> found = new ArrayList<>();
        List<int[]> trialSpawners = new ArrayList<>();
        List<int[]> vaults = new ArrayList<>();
        List<int[]> beeHives = new ArrayList<>();
        List<int[]> rafflesia = new ArrayList<>();
        List<int[]> fireflyBushes = new ArrayList<>();
        int baseX = cx * 16;
        int baseZ = cz * 16;
        boolean copied = source.copyCellsTo(snapshot, scanTypes, scanStates);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = Blocks.MIN_Y; y <= Blocks.MAX_Y; y++) {
                    int blockIndex = Blocks.blockIndex(lx, y, lz);
                    int id = copied ? Short.toUnsignedInt(scanTypes[blockIndex])
                            : source.blockTypeAt(snapshot, blockIndex);
                    if (id >= SPAWNER_MIN && id <= SPAWNER_MAX) {
                        found.add(new int[]{baseX + lx, y, baseZ + lz});
                    } else if (id == Blocks.TRIAL_SPAWNER) {
                        trialSpawners.add(new int[]{baseX + lx, y, baseZ + lz});
                    } else if (id == Blocks.VAULT) {
                        vaults.add(new int[]{baseX + lx, y, baseZ + lz});
                    } else if (id == Blocks.BEE_NEST || id == Blocks.BEEHIVE) {
                        beeHives.add(new int[]{baseX + lx, y, baseZ + lz});
                    } else if (id == Blocks.RAFFLESIA) {
                        rafflesia.add(new int[]{baseX + lx, y, baseZ + lz});
                    } else if (id == Blocks.FIREFLY_BUSH) {
                        fireflyBushes.add(new int[]{baseX + lx, y, baseZ + lz});
                    }
                }
            }
        }
        trialSpawnersByChunk.put(key, trialSpawners);
        vaultsByChunk.put(key, vaults);
        beeHivesByChunk.put(key, beeHives);
        rafflesiaByChunk.put(key, rafflesia);
        fireflyBushesByChunk.put(key, fireflyBushes);
        return found;
    }

    interface PositionEligibility { boolean test(int x, int y, int z); }

    interface PoisonFrogColonyEligibility {
        boolean test(int rafflesiaX, int rafflesiaY, int rafflesiaZ,
                int bushX, int bushY, int bushZ);
    }

    /**
     * Allocation-free nearest eligible Rafflesia/Firefly-Bush pair. The caller supplies the
     * six-int output buffer: Rafflesia xyz followed by bush xyz.
     */
    boolean nearestPoisonFrogColony(double x, double y, double z, int[] out,
            PoisonFrogColonyEligibility eligible) {
        if (out == null || out.length < 6) {
            throw new IllegalArgumentException("poison frog colony output needs six integers");
        }
        boolean found = false;
        for (Map.Entry<Long, List<int[]>> flowerEntry : rafflesiaByChunk.entrySet()) {
            long flowerChunk = flowerEntry.getKey();
            if (!availability.available((int) (flowerChunk >> 32), (int) flowerChunk)) continue;
            for (int[] flower : flowerEntry.getValue()) {
                for (Map.Entry<Long, List<int[]>> bushEntry : fireflyBushesByChunk.entrySet()) {
                    long bushChunk = bushEntry.getKey();
                    if (!availability.available((int) (bushChunk >> 32), (int) bushChunk)) continue;
                    for (int[] bush : bushEntry.getValue()) {
                        if (Math.abs(flower[0] - bush[0])
                                    > FrogPoisonConversionRules.RAFFLESIA_HORIZONTAL_RADIUS
                                || Math.abs(flower[2] - bush[2])
                                    > FrogPoisonConversionRules.RAFFLESIA_HORIZONTAL_RADIUS
                                || Math.abs(flower[1] - bush[1])
                                    > FrogPoisonConversionRules.RAFFLESIA_VERTICAL_RADIUS) continue;
                        double dx = bush[0] + 0.5 - x;
                        double dy = bush[1] - y;
                        double dz = bush[2] + 0.5 - z;
                        if (Math.abs(dx) > FrogPoisonConversionRules.FIREFLY_HORIZONTAL_RADIUS
                                || Math.abs(dy) > FrogPoisonConversionRules.FIREFLY_VERTICAL_RADIUS
                                || Math.abs(dz) > FrogPoisonConversionRules.FIREFLY_HORIZONTAL_RADIUS
                                || !eligible.test(flower[0], flower[1], flower[2],
                                        bush[0], bush[1], bush[2])) continue;
                        if (!found || FrogPoisonConversionRules.compare(x, y, z,
                                flower[0], flower[1], flower[2], bush[0], bush[1], bush[2],
                                out[0], out[1], out[2], out[3], out[4], out[5]) < 0) {
                            out[0] = flower[0]; out[1] = flower[1]; out[2] = flower[2];
                            out[3] = bush[0]; out[4] = bush[1]; out[5] = bush[2];
                            found = true;
                        }
                    }
                }
            }
        }
        return found;
    }

    long nearestFireflyBush(double x, double y, double z, int horizontalRadius,
            int verticalRadius, PositionEligibility eligible) {
        int[] best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (List<int[]> bushes : fireflyBushesByChunk.values()) for (int[] bush : bushes) {
            double dx = bush[0] + 0.5 - x, dy = bush[1] - y, dz = bush[2] + 0.5 - z;
            if (Math.abs(dx) > horizontalRadius || Math.abs(dy) > verticalRadius
                    || Math.abs(dz) > horizontalRadius || !eligible.test(bush[0], bush[1], bush[2])) {
                continue;
            }
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance || distance == bestDistance
                    && (best == null || comparePositions(bush, best) < 0)) {
                bestDistance = distance;
                best = bush;
            }
        }
        return best == null ? 0L : RafflesiaRules.packPosition(best[0], best[1], best[2]);
    }

    /** Allocation-free nearest lookup over the placement/removal-maintained chunk index. */
    long nearestRafflesia(int x, int y, int z, int horizontalRadius, int verticalRadius) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (List<int[]> flowers : rafflesiaByChunk.values()) for (int[] flower : flowers) {
            int dx = flower[0] - x, dy = flower[1] - y, dz = flower[2] - z;
            if (Math.abs(dx) > horizontalRadius || Math.abs(dz) > horizontalRadius
                    || Math.abs(dy) > verticalRadius) continue;
            long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            if (distance < bestDistance || distance == bestDistance
                    && (best == null || comparePositions(flower, best) < 0)) {
                bestDistance = distance;
                best = flower;
            }
        }
        return best == null ? RafflesiaRules.NO_POSITION
                : RafflesiaRules.packPosition(best[0], best[1], best[2]);
    }

    int[] nearestBeeHive(int x, int y, int z, int horizontalRadius, int verticalRadius) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (List<int[]> hives : beeHivesByChunk.values()) for (int[] hive : hives) {
            int dx = hive[0] - x, dy = hive[1] - y, dz = hive[2] - z;
            if (Math.abs(dx) > horizontalRadius || Math.abs(dz) > horizontalRadius
                    || Math.abs(dy) > verticalRadius) continue;
            long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            if (distance < bestDistance || distance == bestDistance
                    && (best == null || comparePositions(hive, best) < 0)) {
                bestDistance = distance; best = hive;
            }
        }
        return best == null ? null : best.clone();
    }

    /**
     * [TRIAL] 직전 {@link #spawnerBlocks} 가 확정한 수요 청크의 트라이얼 스포너 좌표.
     * 스캔 자체를 새로 돌리지 않으므로 호출 순서는 "스포너 먼저, 시련 뒤" 다.
     */
    List<int[]> trialSpawnerBlocks() {
        return collectIndexed(trialSpawnersByChunk);
    }

    /** [TRIAL] 같은 수요 청크의 금고 좌표. */
    List<int[]> vaultBlocks() {
        return collectIndexed(vaultsByChunk);
    }

    private List<int[]> collectIndexed(Map<Long, List<int[]>> index) {
        List<int[]> out = new ArrayList<>();
        for (long key : lastDemandedChunks) {
            List<int[]> indexed = index.get(key);
            if (indexed == null || indexed.isEmpty()) continue;
            if (!availability.available((int) (key >> 32), (int) key)) continue;
            out.addAll(indexed);
        }
        out.sort(SpawnerIndex::comparePositions);
        return out;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int compareChunkKeys(long left, long right) {
        int x = Integer.compare((int) (left >> 32), (int) (right >> 32));
        return x != 0 ? x : Integer.compare((int) left, (int) right);
    }

    private static int comparePositions(int[] left, int[] right) {
        int x = Integer.compare(left[0], right[0]);
        if (x != 0) return x;
        int y = Integer.compare(left[1], right[1]);
        return y != 0 ? y : Integer.compare(left[2], right[2]);
    }
}
