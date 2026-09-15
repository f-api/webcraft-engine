package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.CanonicalOriginChunkProductSource;
import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.ChunkProductSource;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.biome.McClimateSampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 월드 최초 스폰 지점을 정합니다.
 *
 * <p>예전에는 원점 (0,0) 의 지표 높이만 썼습니다. 그런데 지형 v7 이후 심해가 전체의 33.5% 로 늘어
 * 시드에 따라 원점이 바다 한가운데가 됩니다(예: seed 12345 는 H=18 로 해수면보다 23블록 아래).
 * 그러면 플레이어가 접속하자마자 물속에서 시작해 익사합니다.
 *
 * <p>그래서 원점에서 바깥으로 나선형 탐색해 <b>물 위 마른 땅</b>을 찾습니다. 시드에 대해 결정론적이므로
 * 같은 월드는 항상 같은 곳에서 시작합니다. 지형 생성 결과를 읽기만 하므로 골든에는 영향이 없습니다.
 */
public final class WorldSpawn {

    private static final Logger log = LoggerFactory.getLogger(WorldSpawn.class);

    /** 최대 탐색 반경(블록). 이 안에 안전한 canonical 후보가 없으면 실패합니다. */
    private static final int MAX_RADIUS = 2048;

    private static final int MAX_CHUNK_RADIUS = MAX_RADIUS / Blocks.CHUNK_X;

    private static final int NO_SPAWN = Integer.MIN_VALUE;

    private WorldSpawn() {
    }

    /** 청크 생산 없이 후보를 고른 뒤 확정 청크에서만 실제 충돌·유체를 확인합니다. */
    static int[] findSampled(int seed, ChunkProductSource products) {
        McClimateSampler climate = new McClimateSampler(seed);
        return findSampled(seed, products, (x, z) -> {
            int height = ChunkGenerator.surfaceHeight(seed, x, z);
            if (height <= Blocks.SEA_LEVEL + 2 || height + 2 > Blocks.MAX_Y) return false;
            String biome = McBiomeRegistry.get(climate.biomeAtBlock(x, height, z)).name();
            return !biome.contains("ocean") && !biome.contains("river");
        });
    }

    @FunctionalInterface
    interface DryColumnSampler {
        boolean isDry(int worldX, int worldZ);
    }

    static int[] findSampled(int seed, ChunkProductSource products, DryColumnSampler sampler) {
        Objects.requireNonNull(products, "canonical chunk product source");
        Objects.requireNonNull(sampler, "dry column sampler");
        for (int index = 0; index < candidateCount(); index++) {
            long candidate = candidateChunk(index);
            int chunkX = (int) (candidate >> 32);
            int chunkZ = (int) candidate;
            if (!sampler.isDry(chunkX * Blocks.CHUNK_X + 8,
                    chunkZ * Blocks.CHUNK_Z + 8)) continue;
            int[] spawn = firstDryLand(seed, products, chunkX, chunkZ);
            if (spawn != null) return spawn;
            // 노이즈와 최종 지형이 다르면 안전하지 않은 좌표를 저장하지 않습니다.
            throw new IllegalStateException("sampled spawn chunk has no canonical dry ground at "
                    + chunkX + "," + chunkZ);
        }
        throw new IllegalStateException("sampled world spawn is absent within " + MAX_RADIUS + " blocks");
    }

    /** 탐색 생산 코스도 아래 find와 같은 후보 순서를 사용하되, 다음 묶음만 준비합니다. */
    static long candidateChunk(int index) {
        if (index < 0 || index >= candidateCount()) {
            throw new IllegalArgumentException("spawn candidate index outside search");
        }
        if (index == 0) return 0L;
        int radius = ((int) Math.sqrt(index) + 1) / 2;
        int side = radius * 2;
        int offset = index - (side - 1) * (side - 1);
        int x;
        int z;
        if (offset <= side) {
            x = -radius + offset;
            z = -radius;
        } else if (offset <= side * 2) {
            x = radius;
            z = -radius + offset - side;
        } else if (offset <= side * 3) {
            x = radius - (offset - side * 2);
            z = radius;
        } else {
            x = -radius;
            z = radius - (offset - side * 3);
        }
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int candidateCount() {
        int span = MAX_CHUNK_RADIUS * 2 + 1;
        return span * span;
    }

    /** 후보를 소비하기 직전에만 코스를 전진시키며 실제 탐색 순서와 판정은 find에 맡깁니다. */
    static int[] find(int seed, ChunkProductSource products, IntConsumer beforeCandidate) {
        Objects.requireNonNull(products, "canonical chunk product source");
        Objects.requireNonNull(beforeCandidate, "spawn candidate listener");
        return find(seed, products, beforeCandidate, enabledTiming());
    }

    /**
     * Scheduler-only trace entry point. The supplier is deliberately unreachable unless tracing
     * is enabled, so the ordinary spawn path neither snapshots telemetry nor allocates timing.
     */
    static int[] findWithProductionTelemetry(int seed, ChunkProductSource products,
            IntConsumer beforeCandidate,
            Supplier<CanonicalOriginChunkProductSource.ProductionTelemetry> telemetry) {
        Objects.requireNonNull(telemetry, "production telemetry supplier");
        SpawnTiming timing = enabledTiming();
        return find(seed, products, beforeCandidate, timing == null ? null : telemetry, timing);
    }

    private static SpawnTiming enabledTiming() {
        return Boolean.getBoolean("webcraft.spawnStageTrace")
                ? new SpawnTiming(System::nanoTime, message -> log.info("{}", message)) : null;
    }

    /** Focused timing seam: the normal API supplies an opt-in monotonic clock and one log sink. */
    static int[] find(int seed, ChunkProductSource products, IntConsumer beforeCandidate,
            SpawnTiming timing) {
        return find(seed, products, beforeCandidate, null, timing);
    }

    /** Focused trace seam; production reaches it only through the scheduler trace entry point. */
    static int[] find(int seed, ChunkProductSource products, IntConsumer beforeCandidate,
            Supplier<CanonicalOriginChunkProductSource.ProductionTelemetry> telemetry,
            SpawnTiming timing) {
        Objects.requireNonNull(products, "canonical chunk product source");
        ChunkProductSource searchProducts = beforeCandidate == null && timing == null ? products
                : new SearchProducts(products, beforeCandidate, telemetry, timing);
        int[] spawn = null;
        try {
            spawn = findCandidates(seed, searchProducts);
            return spawn;
        } finally {
            if (timing != null) timing.finish(seed, spawn);
        }
    }

    private static final class SearchProducts implements ChunkProductSource {
        private final ChunkProductSource products;
        private final IntConsumer beforeCandidate;
        private final Supplier<CanonicalOriginChunkProductSource.ProductionTelemetry> telemetry;
        private final SpawnTiming timing;
        private int candidateIndex;

        private SearchProducts(ChunkProductSource products, IntConsumer beforeCandidate,
                Supplier<CanonicalOriginChunkProductSource.ProductionTelemetry> telemetry,
                SpawnTiming timing) {
            this.products = products;
            this.beforeCandidate = beforeCandidate;
            this.telemetry = telemetry;
            this.timing = timing;
        }

        @Override
        public ChunkGenerator.GeneratedChunk generate(int seed, int chunkX, int chunkZ) {
            int index = candidateIndex++;
            if (timing != null) timing.begin(index, chunkX, chunkZ);
            if (beforeCandidate != null) beforeCandidate.accept(index);
            if (timing != null) timing.nextPhase();
            CanonicalOriginChunkProductSource.ProductionTelemetry before = timing == null
                    || telemetry == null ? null : telemetry.get();
            ChunkGenerator.GeneratedChunk chunk = products.generate(seed, chunkX, chunkZ);
            if (before != null) timing.telemetry(before, telemetry.get());
            if (timing != null) timing.nextPhase();
            return chunk;
        }
    }

    /** 마른 땅으로 판정할 최소 높이입니다. 해수면과 같으면 발이 물에 잠기므로 한 칸 위부터입니다. */
    /**
     * 마른 땅 판정. 높이만 보면 안 된다 — 내륙 호수·강은 지형 위에 물이 얹혀 있어
     * {@code surfaceHeight > SEA_LEVEL} 이어도 발밑이 물일 수 있다(seed 7 이 그 사례였다).
     * 실제 canonical 생성물을 조회해 <b>발 위치와 그 아래가 물이 아닌지</b>까지 확인한다.
     */
    private static int drySpawnY(ChunkGenerator.GeneratedChunk chunk, int localX, int localZ) {
        int spawnY = chunk.surfaceHeightAt(localX, localZ) + 1;
        if (spawnY - 1 <= Blocks.SEA_LEVEL || spawnY + 1 > Blocks.MAX_Y) {
            return NO_SPAWN;
        }
        short[] blocks = chunk.blocks();
        if (spawnY + 1 > Blocks.MAX_Y) {
            return NO_SPAWN;
        }
        int support = Short.toUnsignedInt(blocks[Blocks.blockIndex(localX, spawnY - 1, localZ)]);
        int feet = Short.toUnsignedInt(blocks[Blocks.blockIndex(localX, spawnY, localZ)]);
        int head = Short.toUnsignedInt(blocks[Blocks.blockIndex(localX, spawnY + 1, localZ)]);
        return Fluids.isSolid(support)
                && !Fluids.isFluid(support)
                && !Fluids.isSolid(feet)
                && !Fluids.isFluid(feet)
                && !Fluids.isSolid(head)
                && !Fluids.isFluid(head)
                ? spawnY : NO_SPAWN;
    }

    /**
     * 물에 잠기지 않는 스폰 지점을 찾습니다. 원점 청크부터 bounded square spiral로
     * 각 생성물을 한 번만 소비합니다.
     *
     * @return {@code {x, y, z}} — y 는 지표 바로 위(발이 놓이는 높이)
     */
    public static int[] find(int seed, ChunkProductSource products) {
        return find(seed, products, null, enabledTiming());
    }

    private static int[] findCandidates(int seed, ChunkProductSource products) {
        Objects.requireNonNull(products, "canonical chunk product source");
        int[] origin = firstDryLand(seed, products, 0, 0);
        if (origin != null) return origin;

        // 정사각 링의 네 변을 겹치지 않게 순회합니다. 각 좌표는 정확히 한 번 생산됩니다.
        for (int radius = 1; radius <= MAX_CHUNK_RADIUS; radius++) {
            for (int chunkX = -radius; chunkX <= radius; chunkX++) {
                int[] candidate = firstDryLand(seed, products, chunkX, -radius);
                if (candidate != null) return candidate;
            }
            for (int chunkZ = -radius + 1; chunkZ <= radius; chunkZ++) {
                int[] candidate = firstDryLand(seed, products, radius, chunkZ);
                if (candidate != null) return candidate;
            }
            for (int chunkX = radius - 1; chunkX >= -radius; chunkX--) {
                int[] candidate = firstDryLand(seed, products, chunkX, radius);
                if (candidate != null) return candidate;
            }
            for (int chunkZ = radius - 1; chunkZ > -radius; chunkZ--) {
                int[] candidate = firstDryLand(seed, products, -radius, chunkZ);
                if (candidate != null) return candidate;
            }
        }
        throw new IllegalStateException("canonical world spawn is absent within "
                + MAX_RADIUS + " blocks");
    }

    private static int[] firstDryLand(int seed, ChunkProductSource products,
            int chunkX, int chunkZ) {
        ChunkGenerator.GeneratedChunk chunk = Objects.requireNonNull(
                products.generate(seed, chunkX, chunkZ),
                "canonical chunk product source returned null");
        if (chunk.finalLiveCarrier() == null) {
            throw new IllegalStateException("world spawn requires a canonical final carrier");
        }
        for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                int y = drySpawnY(chunk, localX, localZ);
                if (y == NO_SPAWN) continue;
                return decided(products, new int[] {
                        chunkX * Blocks.CHUNK_X + localX,
                        y,
                        chunkZ * Blocks.CHUNK_Z + localZ
                });
            }
        }
        return decided(products, null);
    }

    private static int[] decided(ChunkProductSource products, int[] spawn) {
        if (products instanceof SearchProducts search && search.timing != null) {
            search.timing.decided(spawn != null ? "accepted" : "rejected");
        }
        return spawn;
    }

    /** Opt-in, bounded diagnostics; it observes the existing source and never asks it for extra data. */
    static final class SpawnTiming {
        static final int SLOWEST_LIMIT = 8;
        private static final String[] PHASES = {"callback", "generate", "scan"};
        private final LongSupplier clock;
        private final Consumer<String> report;
        private final long started;
        private final long[] totals = new long[PHASES.length];
        private final List<CandidateTiming> slowest = new ArrayList<>(SLOWEST_LIMIT);
        private CandidateTiming current;
        private CandidateTiming last;
        private int phase;
        private long phaseStarted;
        private int candidates;

        SpawnTiming(LongSupplier clock, Consumer<String> report) {
            this.clock = Objects.requireNonNull(clock, "spawn trace clock");
            this.report = Objects.requireNonNull(report, "spawn trace sink");
            this.started = clock.getAsLong();
        }

        private void begin(int index, int chunkX, int chunkZ) {
            long now = clock.getAsLong();
            current = new CandidateTiming(index, chunkX, chunkZ, now - started);
            candidates++;
            phase = 0;
            phaseStarted = now;
        }

        private void nextPhase() {
            long now = clock.getAsLong();
            long elapsed = now - phaseStarted;
            current.nanos[phase] += elapsed;
            totals[phase] += elapsed;
            phaseStarted = now;
            phase++;
        }

        private void decided(String decision) {
            nextPhase();
            current.decision = decision;
            last = current;
            int insert = 0;
            while (insert < slowest.size() && slowest.get(insert).waitNanos() >= current.waitNanos()) {
                insert++;
            }
            if (insert < SLOWEST_LIMIT) {
                slowest.add(insert, current);
                if (slowest.size() > SLOWEST_LIMIT) slowest.remove(SLOWEST_LIMIT);
            }
            current = null;
        }

        private void telemetry(CanonicalOriginChunkProductSource.ProductionTelemetry before,
                CanonicalOriginChunkProductSource.ProductionTelemetry after) {
            current.telemetry = TelemetryDelta.between(before, after);
        }

        private void finish(int seed, int[] spawn) {
            if (current != null) decided("failed-" + PHASES[phase]);
            String outcome = spawn != null ? "accepted"
                    : last != null && candidates == candidateCount() && "rejected".equals(last.decision)
                            ? "absent" : "failed";
            report.accept("spawn stage: seed=" + seed + " outcome=" + outcome
                    + " candidates=" + candidates + " totalMs=" + millis(clock.getAsLong() - started)
                    + " callbackMs=" + millis(totals[0]) + " generateMs=" + millis(totals[1])
                    + " scanMs=" + millis(totals[2])
                    + " spawn=" + java.util.Arrays.toString(spawn)
                    + " last=" + last + " slowestWaits=" + slowest);
        }

        private static String millis(long nanos) {
            return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
        }

        private static final class CandidateTiming {
            private final int index;
            private final int chunkX;
            private final int chunkZ;
            private final long startNanos;
            private final long[] nanos = new long[PHASES.length];
            private String decision;
            private TelemetryDelta telemetry;

            private CandidateTiming(int index, int chunkX, int chunkZ, long startNanos) {
                this.index = index;
                this.chunkX = chunkX;
                this.chunkZ = chunkZ;
                this.startNanos = startNanos;
            }

            private long waitNanos() {
                return nanos[0] + nanos[1];
            }

            @Override public String toString() {
                return "{index0=" + index + " chunk=" + chunkX + "," + chunkZ
                        + " startMs=" + millis(startNanos)
                        + " callbackMs=" + millis(nanos[0])
                        + " generateMs=" + millis(nanos[1])
                        + " scanMs=" + millis(nanos[2]) + " decision=" + decision
                        + (telemetry == null ? "" : " " + telemetry) + "}";
            }
        }

        private static final class TelemetryDelta {
            private final long replayed;
            private final long courseCompleted;
            private final long courseAbandoned;
            private final long produced;
            private final long prefetched;

            private TelemetryDelta(long replayed, long courseCompleted, long courseAbandoned,
                    long produced, long prefetched) {
                this.replayed = replayed;
                this.courseCompleted = courseCompleted;
                this.courseAbandoned = courseAbandoned;
                this.produced = produced;
                this.prefetched = prefetched;
            }

            private static TelemetryDelta between(
                    CanonicalOriginChunkProductSource.ProductionTelemetry before,
                    CanonicalOriginChunkProductSource.ProductionTelemetry after) {
                return new TelemetryDelta(after.replayedWithoutLock() - before.replayedWithoutLock(),
                        after.courseAwaitedChunks() - before.courseAwaitedChunks(),
                        after.courseAwaitAbandoned() - before.courseAwaitAbandoned(),
                        after.producedChunks() - before.producedChunks(),
                        after.prefetchedChunks() - before.prefetchedChunks());
            }

            @Override public String toString() {
                return "telemetry={replayed=" + replayed + " courseCompleted=" + courseCompleted
                        + " courseAbandoned=" + courseAbandoned + " produced=" + produced
                        + " prefetched=" + prefetched + "}";
            }
        }
    }
}
