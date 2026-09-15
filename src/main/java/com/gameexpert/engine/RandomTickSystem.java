package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.gameexpert.engine.blocks.P1Rules;
import com.gameexpert.engine.blocks.P2Rules;
import com.gameexpert.engine.blocks.P3Rules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.blocks.P26Rules;
import com.gameexpert.engine.blocks.P29Rules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.crop.CropRules;
import com.gameexpert.engine.crop.SweetBerryBushRules;

/**
 * 접속자 주변 청크만 처리하는 서버 권위 랜덤 틱(월드 틱 스레드 전용).
 * 기본 randomTickSpeed 3을 20 TPS에서 10 TPS로 옮겨 한 16^3 섹션당 서버 틱마다 6칸을 뽑습니다.
 */
final class RandomTickSystem {

    @FunctionalInterface
    interface ChunkView {
        int blockAt(int localX, int y, int localZ);
    }

    static final int CHUNK_RADIUS = 1;
    static final int MAX_ACTIVE_CHUNKS = 32;
    static final int SAMPLES_PER_SECTION = 6;
    static final int MAX_RANDOM_SAMPLES = MAX_ACTIVE_CHUNKS
            * (Blocks.CHUNK_Y / 16) * SAMPLES_PER_SECTION;
    static final int MAX_TRANSITIONS = 128;
    private static final int FIRE_UPDATE_TICKS = 15; // MC 30~39 game ticks, authority loop is 10 TPS.
    private static final int FIRE_UPDATE_JITTER = 5;
    private static final int MAX_FIRE_AGE = 15;
    private static final int SAPLING_LIGHT = 9;
    private static final int SAPLING_GROWTH_ROLL = 7;
    private static final float COPPER_RANDOM_TICK_CHANCE = 0.05688889f;
    private static final float DRIPSTONE_MUD_TRANSFER_CHANCE = 0.17578125f;
    private static final float DRIPSTONE_GROWTH_CHANCE = 0.011377778f;
    private static final int RANDOM_FLOAT_BOUND = 1 << 24;
    private static final int[][] BRANCH_DIRECTIONS = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
    };
    private static final int[][] DIRECTIONS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 },
            { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 }
    };
    // P6Rules의 자수정 facing 0..5: +Y,-Y,-Z,+X,+Z,-X.
    /** 싹 계열의 성장 단계 수(작은·중간·큰·군집). 두 계열 모두 네 단계다. */
    private static final int BUD_STAGES = 4;
    /** 싹이 붙을 수 있는 여섯 방향. 인덱스가 곧 싹의 상태 바이트(부착 방향)다. */
    private static final int[][] BUD_DIRECTIONS = {
            { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, -1 },
            { 1, 0, 0 }, { 0, 0, 1 }, { -1, 0, 0 }
    };
    private static final int[][] HORIZONTAL_DIRECTIONS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };

    @FunctionalInterface
    interface RandomSource {
        int nextInt(int bound);
    }

    interface World {
        int getBlock(int x, int y, int z);

        /**
         * Optional immutable resident-chunk view for the thousands of primary random samples.
         * Implementations refresh it after a committed transition; neighbor/rule reads still use
         * {@link #getBlock} so the authoritative overlay ordering remains unchanged.
         */
        default ChunkView randomTickChunkView(int chunkX, int chunkZ) { return null; }

        /** cold 청크는 플레이어/게임플레이의 실제 접근이 활성화할 때까지 랜덤틱하지 않는다. */
        default boolean isChunkActivated(int chunkX, int chunkZ) { return true; }

        /** 이 청크의 조회가 생성/DB 접근 없이 즉시 끝나는지 확인합니다. */
        default boolean isChunkResident(int chunkX, int chunkZ) { return true; }

        void setBlock(int x, int y, int z, int blockId);

        /** 식물 판정용 raw brightness. 밤에도 하늘광을 포함하며 skyDarken은 적용하지 않습니다. */
        int lightLevel(int x, int y, int z);

        /** [ENCHANT-WIDE] 블록 광만(하늘광 제외). 얼음 녹기({@code IceBlock.randomTick})가 읽는다. */
        default int blockLightLevel(int x, int y, int z) { return 0; }

        /**
         * [ENCHANT-WIDE] 랜덤틱 표본이 살얼음을 만났다. 바닐라 살얼음은 랜덤틱하지 않고 청크에 저장된 예약 틱으로
         * 녹지만 이 저장소의 예약 틱은 인메모리라, 예약이 없는(재시작 뒤 남은) 살얼음을 여기서 다시 예약한다.
         */
        default void frostedIceSampled(int x, int y, int z) { }

        default int getState(int x, int y, int z, int blockId) { return 0; }

        default void setState(int x, int y, int z, int blockId, int state) { }

        /** 해당 셀과 수평 인접 셀에 실제 비가 닿는지는 런타임 날씨/sky-open 판정이 제공한다. */
        default boolean isRainingAt(int x, int y, int z) { return false; }

        /** FireBlock의 increased_burnout biome tag. */
        default boolean isHumid(int x, int y, int z) { return false; }

        /** 난이도 id(peaceful=0..hard=3). 자연 확산 식의 지역 난이도 항에 사용한다. */
        default int difficultyId() { return 2; }

        /** Overworld infiniburn 지지 블록. 현재 등록 블록에는 없으므로 기본값은 false다. */
        default boolean isFireSource(int x, int y, int z) { return false; }

        /** 화재가 TNT를 태울 때 권위 TNT 엔티티로 넘긴다. true이면 호출자가 점화를 소비했다. */
        default boolean igniteTnt(int x, int y, int z) { return false; }

        /** 자연 감쇠한 잎의 드랍을 런타임 아이템 시스템에 맡긴다. 화재 소실은 호출하지 않는다. */
        default void dropDecayedLeaf(int leaf, int x, int y, int z) {
        }

        /**
         * [TURTLE] 지금이 낮인가. 거북 알의 부화 사슬만 이 값을 본다 — [A] 는 하루 위상을
         * 실수로 보지만 이 저장소는 밤/낮 두 값이라, 그 접기의 근거는
         * {@link TurtleEggRules} 의 같은 절이 소유한다.
         */
        default boolean daytime() { return true; }

        /** Null preserves the state in dimensions without the Overworld day timeline. */
        default Boolean eyeblossomOpen() { return null; }
        default long gameTimeMcTicks() { return -1; }

        /**
         * [TURTLE] 알이 부화했다 — 이 좌표에서 {@code count} 마리의 새끼 거북이 나와야 한다.
         * 랜덤틱 계층은 몹을 만들지 않으므로(블록만 바꾼다) 소환은 몹 권위를 가진 호출자의
         * 몫이다. 감쇠한 잎의 드랍({@link #dropDecayedLeaf})과 같은 경로다.
         */
        default void hatchTurtleEggs(int x, int y, int z, int count) {
        }

        /** Retryable animal-block spawn handoff. False leaves the source block intact. */
        default boolean requestAnimalBlockSpawn(
                AnimalDependencyBlockRules.SpawnKind kind, int x, int y, int z) {
            return false;
        }
    }

    static final class TickStats {
        private final int activeChunks;
        private final int samples;
        private final int transitions;

        TickStats(int activeChunks, int samples, int transitions) {
            this.activeChunks = activeChunks;
            this.samples = samples;
            this.transitions = transitions;
        }

        int activeChunks() { return activeChunks; }
        int samples() { return samples; }
        int transitions() { return transitions; }
    }

    private static final class BurningBlock {
        private long nextUpdateTick;

        BurningBlock(long nextUpdateTick) {
            this.nextUpdateTick = nextUpdateTick;
        }
    }

    /** 예산과 공간을 먼저 검증한 뒤에만 적용하기 위한 나무 변경 계획. */
    private static final class TreePlan {
        private final Map<BlockPos, Integer> blocks = new LinkedHashMap<>();
        private final Map<BlockPos, Integer> states = new HashMap<>();

        void log(int x, int y, int z, int block) {
            log(x, y, z, block, 0);
        }

        void log(int x, int y, int z, int block, int state) {
            BlockPos pos = new BlockPos(x, y, z);
            blocks.put(pos, block);
            if (state != 0) states.put(pos, state);
            else states.remove(pos);
        }

        void leaf(int x, int y, int z, int block) {
            blocks.putIfAbsent(new BlockPos(x, y, z), block);
        }

        int size() {
            return blocks.size();
        }

        int state(BlockPos pos) {
            return states.getOrDefault(pos, 0);
        }
    }

    private final World world;
    private final RandomSource random;
    private final long bambooCapSeed;
    private final int worldSeed;
    // Java/standalone 모두 삽입 순서로 처리해 동일 RNG trace를 유지한다.
    private final Map<BlockPos, BurningBlock> burning = new LinkedHashMap<>();
    /** Owner-tick scratch: active chunk admission and fire traversal allocate nothing after warmup. */
    private final LinkedHashSet<Long> activeChunkWorklist = new LinkedHashSet<>();
    private final ArrayList<BlockPos> burningWorklist = new ArrayList<>();
    /**
     * 잎 거리는 여섯 변까지만 보는 너비 우선 탐색이다. 큐에 들어갈 수 있는 모든 잎은
     * 맨해튼 반경 5(231칸) 안에 있으므로, 틱 스레드 전용 원시 배열을 재사용해 탐색마다
     * 생기던 큐·집합과 칸별 BlockPos/SearchNode 할당을 없앤다.
     */
    private static final int LEAF_SEARCH_RADIUS = 5;
    private static final int LEAF_SEARCH_DIAMETER = LEAF_SEARCH_RADIUS * 2 + 1;
    private static final int LEAF_SEARCH_CELL_COUNT = LEAF_SEARCH_DIAMETER
            * LEAF_SEARCH_DIAMETER * LEAF_SEARCH_DIAMETER;
    private static final int LEAF_SEARCH_QUEUE_CAPACITY = 231;
    private final byte[] leafSearchX = new byte[LEAF_SEARCH_QUEUE_CAPACITY];
    private final byte[] leafSearchY = new byte[LEAF_SEARCH_QUEUE_CAPACITY];
    private final byte[] leafSearchZ = new byte[LEAF_SEARCH_QUEUE_CAPACITY];
    private final byte[] leafSearchDistance = new byte[LEAF_SEARCH_QUEUE_CAPACITY];
    private final int[] leafSearchVisited = new int[LEAF_SEARCH_CELL_COUNT];
    private int leafSearchGeneration;
    private long fireRevision;
    private boolean fireDirty;
    private volatile FireSnapshot publishedFire = new FireSnapshot(0, List.of());

    static final class FireSnapshot {
        private final long revision;
        private final List<BlockPos> blocks;

        private FireSnapshot(long revision, List<BlockPos> blocks) {
            this.revision = revision;
            this.blocks = blocks;
        }

        long revision() {
            return revision;
        }

        List<BlockPos> blocks() {
            return blocks;
        }
    }

    RandomTickSystem(World world, long seed) {
        this(world, new Random(seed)::nextInt, seed);
    }

    RandomTickSystem(World world, RandomSource random) {
        this(world, random, 0L);
    }

    RandomTickSystem(World world, RandomSource random, long bambooCapSeed) {
        this.world = world;
        this.random = random;
        this.bambooCapSeed = bambooCapSeed;
        this.worldSeed = (int) bambooCapSeed;
    }

    TickStats tick(Set<Long> nearbyChunks, long tickNo) {
        if (nearbyChunks.isEmpty()) {
            publishFireSnapshotIfDirty();
            return new TickStats(0, 0, 0);
        }
        LinkedHashSet<Long> active = activeChunkWorklist;
        active.clear();
        for (long chunk : nearbyChunks) {
            if (active.size() >= MAX_ACTIVE_CHUNKS) break;
            int cx = chunkX(chunk);
            int cz = chunkZ(chunk);
            if (!world.isChunkActivated(cx, cz) || !hasResidentReadHalo(cx, cz)) continue;
            active.add(chunk);
        }

        int samples = 0;
        int transitions = processEyeblossomTicks(active, tickNo);
        for (long chunk : active) {
            int chunkX = chunkX(chunk);
            int chunkZ = chunkZ(chunk);
            int baseX = chunkX * Blocks.CHUNK_X;
            int baseZ = chunkZ * Blocks.CHUNK_Z;
            ChunkView chunkView = world.randomTickChunkView(chunkX, chunkZ);
            for (int sectionY = Blocks.MIN_Y; sectionY <= Blocks.MAX_Y; sectionY += 16) {
                for (int sample = 0; sample < SAMPLES_PER_SECTION; sample++) {
                    if (samples >= MAX_RANDOM_SAMPLES || transitions >= MAX_TRANSITIONS) break;
                    int localX = random.nextInt(16);
                    int y = sectionY + random.nextInt(16);
                    int localZ = random.nextInt(16);
                    int x = baseX + localX;
                    int z = baseZ + localZ;
                    samples++;
                    int block = chunkView == null
                            ? world.getBlock(x, y, z)
                            : chunkView.blockAt(localX, y, localZ);
                    int applied = processRandomTick(block, x, y, z, tickNo,
                            MAX_TRANSITIONS - transitions);
                    transitions += applied;
                    if (applied != 0 && chunkView != null) {
                        // setBlock publishes a fresh immutable source. Refresh only on the rare
                        // mutating sample so later samples in this chunk see the same state as the
                        // former point lookup path.
                        chunkView = world.randomTickChunkView(chunkX, chunkZ);
                    }
                }
            }
        }
        if (transitions < MAX_TRANSITIONS) {
            transitions += processBurning(active, tickNo, MAX_TRANSITIONS - transitions);
        }
        publishFireSnapshotIfDirty();
        return new TickStats(active.size(), samples, transitions);
    }

    /** 랜덤틱 규칙의 최대 수평 탐색(버섯 4칸 등)이 닿는 이웃까지 먼저 고정합니다. */
    private boolean hasResidentReadHalo(int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!world.isChunkResident(chunkX + dx, chunkZ + dz)) return false;
            }
        }
        return true;
    }

    /** [ENCHANT-WIDE] 얼음의 {@code getLightDampening()}(반투명 블록 1). */
    static final int ICE_LIGHT_DAMPENING = 1;

    int processRandomTick(int x, int y, int z, long tickNo) {
        return processRandomTick(x, y, z, tickNo, MAX_TRANSITIONS);
    }

    public record EyeblossomSchedule(int x, int y, int z, int expected, long dueMcTick) { }

    public List<EyeblossomSchedule> eyeblossomSchedules() {
        return eyeblossomTicks.values().stream().map(tick -> new EyeblossomSchedule(
                tick.position.x(), tick.position.y(), tick.position.z(), tick.expected, tick.due)).toList();
    }

    public void restoreEyeblossomSchedules(List<EyeblossomSchedule> schedules) {
        var restored = new LinkedHashMap<BlockPos, EyeblossomTick>();
        for (var row : schedules) {
            if ((row.expected() != Blocks.OPEN_EYEBLOSSOM && row.expected() != Blocks.CLOSED_EYEBLOSSOM)
                    || row.y() < Blocks.MIN_Y || row.y() > Blocks.MAX_Y || row.dueMcTick() < 0) {
                throw new IllegalArgumentException("invalid eyeblossom scheduled tick");
            }
            var pos = new BlockPos(row.x(), row.y(), row.z());
            if (restored.putIfAbsent(pos, new EyeblossomTick(pos, row.expected(), row.dueMcTick())) != null) {
                throw new IllegalArgumentException("duplicate eyeblossom scheduled tick");
            }
        }
        eyeblossomTicks.clear();
        eyeblossomTicks.putAll(restored);
    }

    private long eyeblossomClock(long tickNo) {
        long absolute = world.gameTimeMcTicks();
        return absolute >= 0 ? absolute : tickNo * 2;
    }

    private static final class EyeblossomTick {
        final BlockPos position; final int expected; final long due;
        EyeblossomTick(BlockPos position, int expected, long due) {
            this.position = position; this.expected = expected; this.due = due;
        }
    }
    private final Map<BlockPos, EyeblossomTick> eyeblossomTicks = new LinkedHashMap<>();

    private int processEyeblossomTicks(Set<Long> active, long tickNo) {
        int changed = 0;
        for (EyeblossomTick pending : new ArrayList<>(eyeblossomTicks.values())) {
            BlockPos p = pending.position;
            if (changed >= MAX_TRANSITIONS || pending.due > eyeblossomClock(tickNo)
                    || !active.contains(chunkKey(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16)))) continue;
            eyeblossomTicks.remove(p);
            if (world.getBlock(p.x(),p.y(),p.z()) == pending.expected) {
                changed += tickEyeblossom(pending.expected,p.x(),p.y(),p.z(),tickNo);
            }
        }
        return changed;
    }

    private int tickEyeblossom(int current, int x, int y, int z, long tickNo) {
        Boolean open = world.eyeblossomOpen();
        if (open == null || open == (current == Blocks.OPEN_EYEBLOSSOM)) return 0;
        world.setBlock(x,y,z,open ? Blocks.OPEN_EYEBLOSSOM : Blocks.CLOSED_EYEBLOSSOM);
        for (int dx=-3;dx<=3;dx++) for (int dy=-2;dy<=2;dy++) for (int dz=-3;dz<=3;dz++) {
            int px=x+dx,py=y+dy,pz=z+dz;
            if (py<Blocks.MIN_Y || py>Blocks.MAX_Y || !world.isChunkResident(Math.floorDiv(px,16),Math.floorDiv(pz,16))
                    || world.getBlock(px,py,pz)!=current) continue;
            double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            int min=(int)(distance*5),max=(int)(distance*10);
            long due=eyeblossomClock(tickNo)+((min+random.nextInt(max-min+1)+1)/2)*2;
            BlockPos pos=new BlockPos(px,py,pz);
            eyeblossomTicks.putIfAbsent(pos,new EyeblossomTick(pos,current,due));
        }
        return 1;
    }

    /** Scheduled-tick entry point; callers reschedule with {@link #animalBlockNextDelay}. */
    int processAnimalBlockScheduledTick(int x, int y, int z) {
        int block = world.getBlock(x, y, z);
        AnimalDependencyBlockRules.ScheduledStep step;
        if (block == Blocks.FROGSPAWN) {
            step = AnimalDependencyBlockRules.frogspawnStep();
        } else if (block == Blocks.SNIFFER_EGG) {
            step = AnimalDependencyBlockRules.snifferEggStep(
                    world.getState(x, y, z, block), world.getBlock(x, y - 1, z) == Blocks.MOSS_BLOCK);
        } else {
            return 0;
        }
        if (step.requestsSpawn()) {
            if (!world.requestAnimalBlockSpawn(step.spawn(), x, y, z)) return 0;
            world.setBlock(x, y, z, Blocks.AIR);
            return 1;
        }
        world.setState(x, y, z, block, step.nextState());
        return 1;
    }

    int animalBlockNextDelay(int x, int y, int z, long absoluteGameTime) {
        int block = world.getBlock(x, y, z);
        if (block == Blocks.FROGSPAWN) {
            return AnimalDependencyBlockRules.deterministicFrogspawnHatchDelay(
                    worldSeed, x, y, z, absoluteGameTime);
        }
        if (block == Blocks.SNIFFER_EGG) {
            return AnimalDependencyBlockRules.snifferEggNextDelay(
                    world.getBlock(x, y - 1, z) == Blocks.MOSS_BLOCK);
        }
        return -1;
    }

    int processRandomTick(int x, int y, int z, long tickNo, int transitionBudget) {
        int block = world.getBlock(x, y, z);
        return processRandomTick(block, x, y, z, tickNo, transitionBudget);
    }

    private int processRandomTick(int block, int x, int y, int z,
            long tickNo, int transitionBudget) {
        if (block == Blocks.FIRE) {
            trackLoadedFireDeferred(x, y, z, tickNo);
            return 0;
        }
        // LavaFluid.randomTick owns natural ignition in Java 1.21.4. Keeping it at the old
        // fire-only branch preserves every unrelated random-tick branch and RNG order.
        if (Fluids.isLava(block)) {
            return igniteFromLavaDeferred(x, y, z, tickNo) ? 1 : 0;
        }
        // [COPPER] 산화 대상은 이제 구리 4블록이 아니라 열 계열이다. 판정은 하드코딩 범위가
        // 아니라 정본 표(Blocks.nextOxidationStage)만 읽고, **분기 위치는 그대로**다 —
        // 이 자리를 옮기면 난수 소비 순서가 바뀌어 정적판 미러와 갈린다.
        // 최종 단계(oxidized)는 nextOxidationStage 가 -1 이라 들어오지 않는다. 바닐라도
        // ChangeOverTimeBlock.isRandomlyTicking 이 getNext().isPresent() 라 같다(난수 미소비).
        if (CopperAgeRules.nextOxidationStage(block) >= 0) {
            return weatherCopper(block, x, y, z);
        }
        // [ENCHANT-WIDE] 바닐라 IceBlock.randomTick: 블록 광이 11 - lightDampening(1) 보다 크면 녹아 물 수원이
        // 된다(울트라웜 차원이면 사라지지만 이 저장소엔 그런 차원이 없다). 난수를 쓰지 않아 다른 분기의
        // 난수 순서를 밀지 않는다.
        if (block == Blocks.ICE) {
            if (world.blockLightLevel(x, y, z) > 11 - ICE_LIGHT_DAMPENING) {
                world.setBlock(x, y, z, Fluids.WATER_SOURCE);
                return 1;
            }
            return 0;
        }
        if (block == Blocks.FROSTED_ICE) {
            world.frostedIceSampled(x, y, z);
            return 0;
        }
        if (block == Blocks.BUDDING_AMETHYST) {
            return growBud(x, y, z, Blocks.SMALL_AMETHYST_BUD);
        }
        if (isWoodLeaves(block)) {
            if (!connectedToLog(x, y, z)) {
                world.setBlock(x, y, z, Blocks.AIR);
                world.dropDecayedLeaf(block, x, y, z); // MC처럼 자연 감쇠도 플레이어 파괴와 같은 드랍표를 쓴다.
                return 1;
            }
            return 0;
        }
        if (block == Blocks.GRASS) {
            if (blocksGrassLight(world.getBlock(x, y + 1, z))) {
                world.setBlock(x, y, z, Blocks.DIRT);
                return 1;
            }
            return spreadGrass(x, y, z, transitionBudget);
        }
        if (block == Blocks.DIRT_PATH) {
            if (Fluids.isSolid(world.getBlock(x, y + 1, z))) {
                world.setBlock(x, y, z, Blocks.DIRT);
                return 1;
            }
            return 0;
        }
        if (block == Blocks.SUGARCANE) {
            return growColumn(block, x, y, z);
        }
        if (block == Blocks.CACTUS) return growCactus(x, y, z);
        if (block == Blocks.BAMBOO) {
            return growBamboo(x, y, z, transitionBudget);
        }
        if (block == Blocks.VINE) {
            return growVineDown(x, y, z, transitionBudget);
        }
        if (block == Blocks.CAVE_VINES) {
            return growCaveVines(x, y, z, transitionBudget);
        }
        if (block == Blocks.POINTED_DRIPSTONE) {
            return tickPointedDripstone(x, y, z, transitionBudget);
        }
        if (block == Blocks.MUSHROOM_BROWN || block == Blocks.MUSHROOM_RED) {
            return spreadMushroom(block, x, y, z, transitionBudget);
        }
        if (block == Blocks.OAK_SAPLING || block == Blocks.BIRCH_SAPLING
                || P6Rules.isSapling(block)
                // [POPLAR] 포플러 묘목(1539)은 기존 묘목 구간(387~391) 밖이라
                // P6Rules.isSapling 이 잡지 못한다.
                || block == Blocks.POPLAR_SAPLING) {
            return growSapling(block, x, y, z, transitionBudget, true);
        }
        if (block == Blocks.MANGROVE_PROPAGULE) {
            int state = world.getState(x, y, z, block);
            if ((state & P6Rules.PROPAGULE_HANGING) != 0) {
                int age = (state & P6Rules.PROPAGULE_AGE_MASK) >> 1;
                if (age < 4) {
                    world.setState(x, y, z, block,
                            P6Rules.PROPAGULE_HANGING | ((age + 1) << 1));
                    return 1;
                }
                return 0;
            }
            return growSapling(block, x, y, z, transitionBudget, true);
        }
        if (block == Blocks.FARMLAND) {
            return tickFarmland(x, y, z);
        }
        if (block == Blocks.PITCHER_CROP) {
            return tickPitcherCrop(x, y, z);
        }
        int animalCropMaxAge = AnimalDependencyBlockRules.cropMaxAge(block);
        if (animalCropMaxAge >= 0) {
            return tickAnimalCrop(block, animalCropMaxAge, x, y, z);
        }
        CropRules.Rule crop = CropRules.forCrop(block);
        if (crop != null) {
            return tickCrop(crop, x, y, z);
        }
        // [CROP-BERRY] 달콤한 열매 덤불. 이 분기를 **맨 끝에** 두는 것은 난수 소비 규율이다 —
        // 위 어느 분기 사이에 끼우면 그 뒤 블록들의 난수열 위상이 통째로 밀려 기존 작물·묘목의
        // 성장이 조용히 달라지고 정적판 미러와 갈린다. 덤불은 어느 기존 분기와도 ID 가 겹치지
        // 않으므로 맨 끝이어도 판정 결과는 같다.
        if (block == Blocks.SWEET_BERRY_BUSH) {
            return growSweetBerryBush(x, y, z);
        }
        // [CORAL-REEF] 살아있는 산호 30종 중 열다섯. 이 분기도 **맨 끝**이며 난수를 한 번도
        // 소비하지 않는다 — 바닐라 CoralBlock/BaseCoralPlantTypeBlock.randomTick 이 물 검사
        // 결과만 보고 결정하기 때문이다. 그래서 이 분기를 더해도 위 모든 블록의 난수열 위상이
        // 그대로이고 정적판 미러와 갈리지 않는다.
        if (Blocks.isLiveCoral(block)) {
            return dryOutCoral(block, x, y, z);
        }
        // [TURTLE] 거북 알 부화 사슬. 위 두 분기와 같은 이유로 **맨 끝**이다 — 사이에 끼우면
        // 그 뒤 블록들의 난수열 위상이 밀려 기존 작물·묘목의 성장이 조용히 달라지고 정적판
        // 미러와 갈린다. 정적판 사본은 StandaloneNaturalEnvironment.advanceTurtleEgg 다.
        if (block == Blocks.OPEN_EYEBLOSSOM || block == Blocks.CLOSED_EYEBLOSSOM) {
            return tickEyeblossom(block,x,y,z,tickNo);
        }
        if (block == Blocks.TURTLE_EGG) {
            return advanceTurtleEgg(x, y, z);
        }
        // [VOID-END] 후렴 꽃 성장(ChorusFlowerBlock.randomTick). 위 분기들과 같은 이유로 **맨 끝**이다 —
        // 난수를 쓰는 기존 블록의 위상을 밀지 않는다. 시든 꽃(age 5)은 isRandomlyTicking 이 거짓이라
        // 난수를 소비하지 않는다. 정적판 사본은 StandaloneNaturalEnvironment 의 같은 자리다.
        if (block == Blocks.CHORUS_FLOWER) {
            return com.gameexpert.engine.blocks.VoidEndBlockRules.chorusFlowerRandomTick(x, y, z,
                    world.getState(x, y, z, block), new com.gameexpert.engine.blocks.VoidEndBlockRules.GrowthWorld() {
                        @Override
                        public int getBlock(int qx, int qy, int qz) {
                            return world.getBlock(qx, qy, qz);
                        }

                        @Override
                        public void setBlock(int qx, int qy, int qz, int blockId, int state) {
                            world.setBlock(qx, qy, qz, blockId);
                            world.setState(qx, qy, qz, blockId, state);
                        }
                    }, random::nextInt, Blocks.MAX_Y);
        }
        return 0;
    }

    /**
     * [TURTLE] [A] {@code TurtleEggBlock.randomTick}. 결정·판정은 전부
     * {@link TurtleEggRules} 가 소유하고 여기서는 월드에 적용만 한다.
     *
     * <h2>난수 소비 규약</h2>
     * 바닐라는 {@code shouldUpdateHatchLevel(level) && onSand(level, pos)} 라 <b>모래를 잃은
     * 알도</b> 난수를 먼저 뽑는다. 이쪽은 순서를 뒤집어 지지 검사를 먼저 한다 — 모래를 잃은
     * 알은 어차피 지지 규칙이 곧 지우는데, 그 사이에 난수를 뽑으면 알이 여럿 깔린 해변에서
     * 두 권위의 난수열이 알의 수만큼 어긋난다. 정적판도 <b>같은 순서</b>다.
     */
    private int advanceTurtleEgg(int x, int y, int z) {
        if (!TurtleEggRules.canPlaceOn(world.getBlock(x, y - 1, z))) return 0;
        if (!TurtleEggRules.shouldAdvance(
                random.nextInt(TurtleEggRules.hatchBound(world.daytime())))) {
            return 0;
        }
        int state = world.getState(x, y, z, Blocks.TURTLE_EGG);
        if (!TurtleEggRules.hatchesNow(state)) {
            world.setState(x, y, z, Blocks.TURTLE_EGG, TurtleEggRules.advancedState(state));
            return 1;
        }
        world.setBlock(x, y, z, Blocks.AIR);
        world.hatchTurtleEggs(x, y, z, TurtleEggRules.hatchSpawnCount(state));
        return 1;
    }

    /**
     * [CORAL-REEF] 산호 건조 전이. [A] {@code CoralBlock.randomTick} ·
     * {@code BaseCoralPlantTypeBlock.randomTick}: 물이 닿지 않으면 같은 색·같은 형상의 죽은
     * 변형이 된다. 난수를 소비하지 않는다.
     *
     * <p><b>[C] waterlogged 매핑.</b> 바닐라 식물형·부채는 자기 칸의 {@code waterlogged} 상태를
     * 먼저 보고, 아니면 여섯 이웃을 훑는다. 이 저장소에는 waterlogged 상태 자체가 없고 수중
     * 식생이 물 셀을 대신하는 단일-ID 모델이라({@code Fluids.isSubmergedDecoration}) 자기 칸을
     * 물어보면 <b>언제나</b> 물이라는 답이 나와 산호가 영원히 죽지 않는다. 그래서 서른 종
     * 전부에 <b>여섯 이웃 물 검사</b> 하나만 쓴다 — 바닐라 {@code CoralBlock} 의 규칙과
     * 문자 그대로 같고, 식물형·부채의 비-waterlogged 갈래와도 같다. 결과적으로 물에 잠긴
     * 산호초 내부는 살아 있고, 물이 빠졌거나 뭍에 놓인 산호만 죽는다.
     *
     * <p>정적판 사본은 {@code StandaloneRandomTicks} 의 같은 이름 함수다.
     */
    private int dryOutCoral(int block, int x, int y, int z) {
        if (coralTouchesWater(x, y, z)) return 0;
        world.setBlock(x, y, z, Blocks.deadCoral(block));
        return 1;
    }

    /** 여섯 이웃 중 하나라도 물(수중 식생이 대신한 물 셀 포함)인가. */
    private boolean coralTouchesWater(int x, int y, int z) {
        return Fluids.isWaterMedium(world.getBlock(x, y + 1, z))
                || Fluids.isWaterMedium(world.getBlock(x, y - 1, z))
                || Fluids.isWaterMedium(world.getBlock(x + 1, y, z))
                || Fluids.isWaterMedium(world.getBlock(x - 1, y, z))
                || Fluids.isWaterMedium(world.getBlock(x, y, z + 1))
                || Fluids.isWaterMedium(world.getBlock(x, y, z - 1));
    }

    /**
     * [CROP-BERRY] [A] {@code SweetBerryBushBlock.randomTick}: 위 칸 광량 ≥ 9 이고
     * {@code random.nextInt(5) == 0} 이면 age 한 단계. 다 자랐으면 난수를 소비하지 않는다
     * (바닐라도 {@code isRandomlyTicking} 이 age &lt; MAX_AGE 라 아예 틱하지 않는다).
     */
    private int growSweetBerryBush(int x, int y, int z) {
        int age = SweetBerryBushRules.age(
                world.getState(x, y, z, Blocks.SWEET_BERRY_BUSH));
        if (age >= SweetBerryBushRules.MAX_AGE) return 0;
        if (!SweetBerryBushRules.shouldGrow(age, world.lightLevel(x, y + 1, z),
                random::nextInt)) {
            return 0;
        }
        world.setState(x, y, z, Blocks.SWEET_BERRY_BUSH,
                SweetBerryBushRules.state(age + 1));
        return 1;
    }

    /**
     * 바닐라 {@code BuddingAmethystBlock.randomTick} 의 사본. 1/5 확률로 여섯 방향 중 하나를
     * 골라 그 칸의 싹을 한 단계 키운다. {@code smallBud} 는 <b>성장 순서대로 연속</b>인 싹
     * 4종의 첫 ID 이고(작은·중간·큰·군집), 다음 단계는 +1 산술 하나로 얻는다.
     *
     * <p>자수정(403~406)과 [SULFUR] 유황(1304~1307) 두 계열이 이 함수 하나를 공유한다 —
     * 규칙 사본을 둘로 늘리면 한쪽만 고쳐지는 사고가 난다. 난수 소비 순서(1회 nextInt(5) +
     * 1회 nextInt(6))는 계열과 무관하게 같다.
     */
    private int growBud(int x, int y, int z, int smallBud) {
        if (random.nextInt(5) != 0) return 0;
        int facing = random.nextInt(BUD_DIRECTIONS.length);
        int[] direction = BUD_DIRECTIONS[facing];
        int targetX = x + direction[0];
        int targetY = y + direction[1];
        int targetZ = z + direction[2];
        int current = world.getBlock(targetX, targetY, targetZ);
        int next;
        if (current == Blocks.AIR || current == Blocks.WATER_SOURCE) {
            next = smallBud;
        } else if (current >= smallBud && current < smallBud + BUD_STAGES - 1
                && world.getState(targetX, targetY, targetZ, current) == facing) {
            next = current + 1;
        } else {
            return 0;
        }
        world.setBlock(targetX, targetY, targetZ, next);
        world.setState(targetX, targetY, targetZ, next, facing);
        return 1;
    }

    private int tickFarmland(int x, int y, int z) {
        boolean wet = hasWaterWithinFour(x, y, z) || world.isRainingAt(x, y + 1, z);
        int state = world.getState(x, y, z, Blocks.FARMLAND);
        if (wet) {
            if (state == 0) {
                world.setState(x, y, z, Blocks.FARMLAND, 1);
                return 1;
            }
            return 0;
        }
        if (state != 0) {
            world.setState(x, y, z, Blocks.FARMLAND, 0);
            return 1;
        }
        if (!CropRules.isCrop(world.getBlock(x, y + 1, z))) {
            world.setBlock(x, y, z, Blocks.DIRT);
            return 1;
        }
        return 0;
    }

    private boolean hasWaterWithinFour(int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    int block = world.getBlock(x + dx, y + dy, z + dz);
                    if (block >= Blocks.WATER_SOURCE && block <= 47) return true;
                }
            }
        }
        return false;
    }

    int initialFarmlandState(int x, int y, int z) {
        return hasWaterWithinFour(x, y, z) ? 1 : 0;
    }

    private int tickCrop(CropRules.Rule crop, int x, int y, int z) {
        if (world.getBlock(x, y - 1, z) != Blocks.FARMLAND) {
            world.setBlock(x, y, z, Blocks.AIR);
            return 1;
        }
        int age = world.getState(x, y, z, crop.cropBlock());
        if (age >= crop.maxAge()) return crop.stem() ? placeStemFruit(crop, x, y, z) : 0;
        if (world.lightLevel(x, y, z) < 9) return 0;
        float points = farmlandPoints(crop, x, y, z);
        int denominator = (int) Math.floor(25.0f / points) + 1;
        if (random.nextInt(denominator) != 0) return 0;
        world.setState(x, y, z, crop.cropBlock(), age + 1);
        return 1;
    }

    /**
     * [PITCHER] {@code PitcherCropBlock.randomTick} on the lower half: roll
     * {@code nextInt((int)(25 / growthSpeed) + 1) == 0} first, then {@code grow(+1)} under {@code canGrow}.
     * Before that, a crop saved at age 3..4 before the upper half existed grows it back when the cell above
     * is air (no RNG). A lower half off farmland is removed like the other farmland crops; the upper half
     * never ticks. Standalone twin: {@code StandaloneNaturalEnvironment.tickPitcherCrop}.
     */
    private int tickPitcherCrop(int x, int y, int z) {
        int state = world.getState(x, y, z, Blocks.PITCHER_CROP);
        if (PitcherRules.isUpper(state)) return 0;
        if (world.getBlock(x, y - 1, z) != Blocks.FARMLAND) {
            world.setBlock(x, y, z, Blocks.AIR);
            return 1;
        }
        int age = PitcherRules.age(state);
        boolean upperPresent = y + 1 <= Blocks.MAX_Y
                && world.getBlock(x, y + 1, z) == Blocks.PITCHER_CROP
                && PitcherRules.isUpper(world.getState(x, y + 1, z, Blocks.PITCHER_CROP));
        if (PitcherRules.isDouble(age) && !upperPresent) {
            if (y + 1 > Blocks.MAX_Y || world.getBlock(x, y + 1, z) != Blocks.AIR) return 0;
            world.setBlock(x, y + 1, z, Blocks.PITCHER_CROP);
            world.setState(x, y + 1, z, Blocks.PITCHER_CROP, age | PitcherRules.UPPER);
            return 1;
        }
        if (age >= PitcherRules.MAX_AGE) return 0;
        float points = animalCropFarmlandPoints(Blocks.PITCHER_CROP, x, y, z);
        if (random.nextInt((int) Math.floor(25.0f / points) + 1) != 0) return 0;
        return growPitcherCrop(x, y, z, state) ? 1 : 0;
    }

    /** [PITCHER] {@code PitcherCropBlock.grow(level, lowerState, lowerPos, 1)}. */
    private boolean growPitcherCrop(int x, int y, int z, int lowerState) {
        int next = Math.min(PitcherRules.age(lowerState) + 1, PitcherRules.MAX_AGE);
        int above = y + 1 <= Blocks.MAX_Y ? world.getBlock(x, y + 1, z) : Blocks.AIR;
        if (!PitcherRules.canGrow(lowerState, next, world.lightLevel(x, y, z), y, above)) return false;
        world.setState(x, y, z, Blocks.PITCHER_CROP, next);
        if (PitcherRules.isDouble(next)) {
            if (above != Blocks.PITCHER_CROP) world.setBlock(x, y + 1, z, Blocks.PITCHER_CROP);
            world.setState(x, y + 1, z, Blocks.PITCHER_CROP, next | PitcherRules.UPPER);
        }
        return true;
    }

    /** [PITCHER] The lower half of the pitcher crop at or under {@code (x, y, z)}, or null. */
    private int[] pitcherLowerHalf(int x, int y, int z) {
        if (world.getBlock(x, y, z) != Blocks.PITCHER_CROP) return null;
        int state = world.getState(x, y, z, Blocks.PITCHER_CROP);
        if (!PitcherRules.isUpper(state)) return new int[] {y, state};
        if (world.getBlock(x, y - 1, z) != Blocks.PITCHER_CROP) return null;
        int lower = world.getState(x, y - 1, z, Blocks.PITCHER_CROP);
        return PitcherRules.isUpper(lower) ? null : new int[] {y - 1, lower};
    }

    private int tickAnimalCrop(int block, int maxAge, int x, int y, int z) {
        if (world.getBlock(x, y - 1, z) != Blocks.FARMLAND) {
            world.setBlock(x, y, z, Blocks.AIR);
            return 1;
        }
        int age = AnimalDependencyBlockRules.cropAge(world.getState(x, y, z, block));
        if (age >= maxAge || world.lightLevel(x, y, z) < 9) return 0;
        float points = animalCropFarmlandPoints(block, x, y, z);
        if (random.nextInt((int) Math.floor(25.0f / points) + 1) != 0) return 0;
        world.setState(x, y, z, block, age + 1);
        return 1;
    }

    private float animalCropFarmlandPoints(int cropBlock, int x, int y, int z) {
        float points = 1.0f;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            if (world.getBlock(x + dx, y - 1, z + dz) == Blocks.FARMLAND) {
                points += world.getState(x + dx, y - 1, z + dz, Blocks.FARMLAND) == 0
                        ? 0.25f : 0.75f;
            }
        }
        points += world.getState(x, y - 1, z, Blocks.FARMLAND) == 0 ? 0.0f : 2.0f;
        boolean rowX = world.getBlock(x - 1, y, z) == cropBlock || world.getBlock(x + 1, y, z) == cropBlock;
        boolean rowZ = world.getBlock(x, y, z - 1) == cropBlock || world.getBlock(x, y, z + 1) == cropBlock;
        boolean diagonal = world.getBlock(x - 1, y, z - 1) == cropBlock
                || world.getBlock(x - 1, y, z + 1) == cropBlock
                || world.getBlock(x + 1, y, z - 1) == cropBlock
                || world.getBlock(x + 1, y, z + 1) == cropBlock;
        return diagonal || rowX && rowZ ? points / 2.0f : points;
    }

    private int placeStemFruit(CropRules.Rule crop, int x, int y, int z) {
        for (int[] direction : HORIZONTAL_DIRECTIONS) {
            if (world.getBlock(x + direction[0], y, z + direction[1]) == crop.fruitBlock()) {
                return 0;
            }
        }
        int[] direction = HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)];
        int fruitX = x + direction[0];
        int fruitZ = z + direction[1];
        if (world.getBlock(fruitX, y, fruitZ) != Blocks.AIR
                || !crop.isValidFruitFloor(world.getBlock(fruitX, y - 1, fruitZ))) return 0;
        world.setBlock(fruitX, y, fruitZ, crop.fruitBlock());
        return 1;
    }

    private float farmlandPoints(CropRules.Rule crop, int x, int y, int z) {
        float points = 1.0f;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (world.getBlock(x + dx, y - 1, z + dz) == Blocks.FARMLAND) {
                    points += world.getState(x + dx, y - 1, z + dz, Blocks.FARMLAND) == 0 ? 0.25f : 0.75f;
                }
            }
        }
        points += world.getState(x, y - 1, z, Blocks.FARMLAND) == 0 ? 0.0f : 2.0f;
        boolean rowX = CropRules.isSameCrop(world.getBlock(x - 1, y, z), crop)
                || CropRules.isSameCrop(world.getBlock(x + 1, y, z), crop);
        boolean rowZ = CropRules.isSameCrop(world.getBlock(x, y, z - 1), crop)
                || CropRules.isSameCrop(world.getBlock(x, y, z + 1), crop);
        boolean diagonal = CropRules.isSameCrop(world.getBlock(x - 1, y, z - 1), crop)
                || CropRules.isSameCrop(world.getBlock(x - 1, y, z + 1), crop)
                || CropRules.isSameCrop(world.getBlock(x + 1, y, z - 1), crop)
                || CropRules.isSameCrop(world.getBlock(x + 1, y, z + 1), crop);
        return diagonal || rowX && rowZ ? points / 2.0f : points;
    }

    /**
     * MC 묘목의 빛 9+·토양 지지 조건을 단일 ID 블록에 대응한다.
     * 1/7은 이번 제한 리서치에서 MC 확정값으로 검증하지 못한 WebCraft 진행 확률이다.
     * WebCraft는 sapling stage 블록상태가 없으므로 성공한 진행을 즉시 생성 시도로 매핑한다.
     * 변경 계획 전체가 남은 트랜지션 예산과 빈 공간에 들어갈 때만 한 번에 배치한다.
     */
    private int growSapling(int sapling, int x, int y, int z, int transitionBudget, boolean naturalRoll) {
        if (transitionBudget <= 0 || world.lightLevel(x, y + 1, z) < SAPLING_LIGHT
                || !SupportRules.isSupported(sapling, x, y, z, world::getBlock)
                || (naturalRoll && random.nextInt(SAPLING_GROWTH_ROLL) != 0)) return 0;

        boolean birch = sapling == Blocks.BIRCH_SAPLING;
        boolean oak = sapling == Blocks.OAK_SAPLING;
        boolean largeOak = oak && random.nextInt(10) == 0;
        int rootX = x;
        int rootZ = z;
        if (sapling == Blocks.DARK_OAK_SAPLING) {
            int[] root = darkOakRoot(x, y, z);
            if (root == null) return 0;
            rootX = root[0];
            rootZ = root[1];
        }
        int trunkHeight = largeOak ? 9 + random.nextInt(5)
                : birch ? 5 + random.nextInt(3)
                : sapling == Blocks.SPRUCE_SAPLING ? 6 + random.nextInt(4)
                : sapling == Blocks.JUNGLE_SAPLING ? 7 + random.nextInt(5)
                : sapling == Blocks.ACACIA_SAPLING ? 5 + random.nextInt(3)
                : sapling == Blocks.DARK_OAK_SAPLING ? 6 + random.nextInt(4)
                : sapling == Blocks.CHERRY_SAPLING ? 5 + random.nextInt(3)
                : sapling == Blocks.MANGROVE_PROPAGULE ? 6 + random.nextInt(4)
                // [POPLAR] 위키가 포플러 줄기 높이를 적지 않는다(핀 §5). "줄기 하나 +
                // 유난히 큰 수관" 이라는 서술만 [B] 라, 높이는 이 저장소에서 가장 큰 단일
                // 줄기 수종인 정글(7..11)과 같은 범위를 쓰고 수관만 넓힌다([C]).
                : sapling == Blocks.POPLAR_SAPLING ? 7 + random.nextInt(5)
                : 4 + random.nextInt(4);
        TreePlan plan = planSaplingTree(sapling, rootX, y, rootZ, trunkHeight, largeOak);

        if (plan.size() > transitionBudget
                || (oak || birch) && !hasRequiredClearance(rootX, y, rootZ,
                        trunkHeight, birch, largeOak)
                || !canApplyTree(plan, rootX, y, rootZ, sapling)) return 0;
        applyTree(plan);
        return plan.size();
    }

    /**
     * 뼛가루의 MC 45% 성장 단계 진행을 WebCraft의 단일 묘목 상태에 대응한다.
     * 숨은 성장 단계가 없으므로 성공 판정은 즉시 나무 생성 시도가 되며, 지지·빛·공간 검사는 자연 성장과 같다.
     */
    boolean applyBoneMeal(int x, int y, int z) {
        int sapling = world.getBlock(x, y, z);
        if (RafflesiaRules.canAcquireAt(x, y, z, world.isHumid(x, y, z), world::getBlock)) {
            if (random.nextInt(RafflesiaRules.ACQUISITION_ROLL_BOUND) != 0) return false;
            world.setBlock(x, y + 1, z, Blocks.RAFFLESIA);
            return true;
        }
        if (sapling == Blocks.RAFFLESIA) {
            return spreadRafflesia(x, y, z);
        }
        if (sapling == Blocks.RED_SHRUB) {
            int[] target = RedShrubRules.spreadPosition(world::getBlock, x, y, z, random::nextInt);
            if (target == null) return false;
            world.setBlock(target[0], target[1], target[2], Blocks.RED_SHRUB);
            return true;
        }
        // [CROP-BERRY] [A] 달콤한 열매 덤불의 뼛가루는 광량·공간과 무관하게 한 단계다.
        // 이미 다 자랐으면 들지 않는다(isValidBonemealTarget 이 age < MAX_AGE 를 요구한다).
        if (sapling == Blocks.SWEET_BERRY_BUSH) {
            int age = SweetBerryBushRules.age(world.getState(x, y, z, sapling));
            if (!SweetBerryBushRules.isBoneMealTarget(age)) return false;
            world.setState(x, y, z, sapling, SweetBerryBushRules.state(age + 1));
            return true;
        }
        if (sapling == Blocks.CAVE_VINES || sapling == Blocks.CAVE_VINES_PLANT) {
            int state = world.getState(x, y, z, sapling);
            if ((state & P6Rules.CAVE_VINES_BERRIES) != 0) return false;
            world.setState(x, y, z, sapling, state | P6Rules.CAVE_VINES_BERRIES);
            return true;
        }
        int p1Placement = P1Rules.boneMealPlacement(sapling, world.getBlock(x, y - 1, z));
        if (p1Placement != Blocks.AIR && y > Blocks.MIN_Y
                && world.getBlock(x, y - 1, z) == Blocks.AIR) {
            world.setBlock(x, y - 1, z, p1Placement);
            return true;
        }
        // Shelf mushroom growth is AGE=0→1 on the same block; preserve horizontal facing.
        int p29Result = P29Rules.boneMealStateResult(
                sapling, world.getState(x, y, z, sapling));
        if (p29Result >= 0) {
            world.setState(x, y, z, sapling, p29Result);
            return true;
        }
        int p3Result = P3Rules.boneMealResult(sapling,
                world.getState(x, y, z, sapling), world.getBlock(x, y + 1, z));
        if (p3Result != Blocks.AIR) {
            world.setBlock(x, y, z, p3Result);
            return true;
        }
        // [SPRING-TO-LIFE] 1.21.5 자연 요소. 갈래 셋을 P26Rules 가 순수 판정으로 소유하고
        // 여기서는 월드 쓰기와 난수 소비만 한다.
        if (P26Rules.isBoneMealTarget(sapling, world.getState(x, y, z, sapling))) {
            int p22State = P26Rules.boneMealStateResult(sapling, world.getState(x, y, z, sapling));
            if (p22State >= 0) {
                world.setState(x, y, z, sapling, p22State);
                return true;
            }
            int p22Block = P26Rules.boneMealBlockResult(sapling);
            if (p22Block != Blocks.AIR) {
                world.setBlock(x, y, z, p22Block);
                return true;
            }
            int spread = P26Rules.boneMealSpreadBlock(sapling);
            if (spread != Blocks.AIR) return spreadSpringPlant(spread, x, y, z);
        }
        if (sapling == Blocks.AZALEA || sapling == Blocks.FLOWERING_AZALEA) {
            if (random.nextInt(100) >= 45) return false;
            return growAzaleaTree(sapling, x, y, z) > 0;
        }
        if (sapling == Blocks.BAMBOO) {
            return growBambooWithBoneMeal(x, y, z) > 0;
        }
        // [PITCHER] PitcherCropBlock.performBonemeal: grow(+1) from the lower half of either half.
        if (sapling == Blocks.PITCHER_CROP) {
            int[] lower = pitcherLowerHalf(x, y, z);
            return lower != null && growPitcherCrop(x, lower[0], z, lower[1]);
        }
        CropRules.Rule crop = CropRules.forCrop(sapling);
        if (crop != null) {
            int age = world.getState(x, y, z, crop.cropBlock());
            if (age >= crop.maxAge()) return false;
            world.setState(x, y, z, crop.cropBlock(),
                    CropRules.boneMealAge(crop, age, random::nextInt));
            return true;
        }
        if (sapling != Blocks.OAK_SAPLING && sapling != Blocks.BIRCH_SAPLING
                && !P6Rules.isSapling(sapling) && sapling != Blocks.MANGROVE_PROPAGULE
                // [POPLAR] 포플러 묘목도 뼛가루를 받는다(바닐라와 같다).
                && sapling != Blocks.POPLAR_SAPLING) {
            return false;
        }
        if (sapling == Blocks.MANGROVE_PROPAGULE
                && (world.getState(x, y, z, sapling) & P6Rules.PROPAGULE_HANGING) != 0) {
            int state = world.getState(x, y, z, sapling);
            int age = (state & P6Rules.PROPAGULE_AGE_MASK) >> 1;
            if (age >= 4) return false;
            world.setState(x, y, z, sapling,
                    P6Rules.PROPAGULE_HANGING | (Math.min(4, age + 1) << 1));
            return true;
        }
        if (random.nextInt(100) >= 45) return false;
        return growSapling(sapling, x, y, z, MAX_TRANSITIONS, false) > 0;
    }

    boolean isPackBoneMealTarget(int x, int y, int z) {
        int block = world.getBlock(x, y, z);
        return RafflesiaRules.canAcquireAt(x, y, z, world.isHumid(x, y, z), world::getBlock)
                || block == Blocks.RAFFLESIA
                || block == Blocks.RED_SHRUB && RedShrubRules.spreadPosition(world::getBlock, x, y, z, null) != null
                || P1Rules.boneMealPlacement(block, world.getBlock(x, y - 1, z)) != Blocks.AIR
                || P3Rules.isBoneMealTarget(block, world.getState(x, y, z, block))
                || (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT)
                        && (world.getState(x, y, z, block) & P6Rules.CAVE_VINES_BERRIES) == 0
                || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA
                || P26Rules.isBoneMealTarget(block, world.getState(x, y, z, block))
                || P29Rules.isBoneMealTarget(block, world.getState(x, y, z, block))
                || block == Blocks.POPLAR_SAPLING
                || P6Rules.isSapling(block) || block == Blocks.MANGROVE_PROPAGULE;
    }

    private boolean spreadRafflesia(int x, int y, int z) {
        for (int attempt = 0; attempt < RafflesiaRules.SPREAD_ATTEMPTS; attempt++) {
            int tx = x + random.nextInt(7) - 3;
            int tz = z + random.nextInt(7) - 3;
            int ty = y + random.nextInt(3) - 1;
            if (ty <= Blocks.MIN_Y || ty > Blocks.MAX_Y
                    || world.getBlock(tx, ty, tz) != Blocks.AIR
                    || !world.isHumid(tx, ty - 1, tz)
                    || !RafflesiaRules.isSupported(tx, ty, tz, world::getBlock)) continue;
            world.setBlock(tx, ty, tz, Blocks.RAFFLESIA);
            return true;
        }
        return false;
    }

    /**
     * [SPRING-TO-LIFE] 뼛가루가 이웃 칸에 같은 계열 식물을 하나 틔운다.
     * [B] 수풀·반딧불 수풀은 자기 자신을, 큰 마른 풀은 짧은 마른 풀을 퍼뜨린다.
     *
     * <p>후보 선택은 이미 이 파일에 있는 잔디 확산과 <b>같은 형태</b>다 — ±1 XZ · ±1 Y 를
     * 무작위로 뽑아 최대 {@link P26Rules#SPREAD_ATTEMPTS} 번 시도한다. 바닐라의 실제 후보
     * 분포는 [A]/[B] 어느 등급으로도 확보하지 못해 이 형태 자체가 [C] 자체 계약이고,
     * 그래서 새 분포를 발명하는 대신 저장소에 이미 있는 것을 그대로 재사용한다.
     */
    private boolean spreadSpringPlant(int plant, int x, int y, int z) {
        for (int attempt = 0; attempt < P26Rules.SPREAD_ATTEMPTS; attempt++) {
            int tx = x + random.nextInt(3) - 1;
            int ty = y + random.nextInt(3) - 1;
            int tz = z + random.nextInt(3) - 1;
            if (ty <= Blocks.MIN_Y || ty > Blocks.MAX_Y) continue;
            if (tx == x && ty == y && tz == z) continue;
            if (!P26Rules.isSpreadTarget(world.getBlock(tx, ty, tz))) continue;
            if (!P26Rules.canPlantOn(plant, world.getBlock(tx, ty - 1, tz))) continue;
            world.setBlock(tx, ty, tz, plant);
            return true;
        }
        return false;
    }

    /** 배치된 동굴 덩굴 머리의 바닐라 AGE 초기값 [0,24]. */
    int initialCaveVinesState() {
        return P6Rules.caveVinesState(random.nextInt(P6Rules.CAVE_VINES_MAX_AGE), false);
    }

    private int growCaveVines(int x, int y, int z, int transitionBudget) {
        if (transitionBudget < 2 || y <= Blocks.MIN_Y || random.nextInt(10) != 0
                || world.getBlock(x, y - 1, z) != Blocks.AIR) {
            return 0;
        }
        int state = world.getState(x, y, z, Blocks.CAVE_VINES);
        int age = P6Rules.caveVinesAge(state);
        if (age >= P6Rules.CAVE_VINES_MAX_AGE) return 0;

        world.setBlock(x, y, z, Blocks.CAVE_VINES_PLANT);
        world.setState(x, y, z, Blocks.CAVE_VINES_PLANT,
                state & P6Rules.CAVE_VINES_BERRIES);
        int nextState = P6Rules.caveVinesState(age + 1, random.nextInt(100) < 11);
        world.setBlock(x, y - 1, z, Blocks.CAVE_VINES);
        world.setState(x, y - 1, z, Blocks.CAVE_VINES, nextState);
        return 2;
    }

    private int tickPointedDripstone(int x, int y, int z, int transitionBudget) {
        int state = world.getState(x, y, z, Blocks.POINTED_DRIPSTONE);
        if ((state & P6Rules.DRIPSTONE_UP) != 0
                || world.getBlock(x, y + 1, z) == Blocks.POINTED_DRIPSTONE) {
            return 0;
        }

        int transitions = 0;
        float transferRoll = nextFloat();
        if (transferRoll < DRIPSTONE_MUD_TRANSFER_CHANCE
                && world.getBlock(x, y + 1, z) == Blocks.DRIPSTONE_BLOCK
                && world.getBlock(x, y + 2, z) == Blocks.MUD
                && findDripstoneTipY(x, y, z, false, 11) != Integer.MIN_VALUE) {
            world.setBlock(x, y + 2, z, Blocks.CLAY);
            transitions++;
        }

        if (nextFloat() >= DRIPSTONE_GROWTH_CHANCE
                || world.getBlock(x, y + 1, z) != Blocks.DRIPSTONE_BLOCK
                || world.getBlock(x, y + 2, z) != Blocks.WATER_SOURCE
                || transitionBudget - transitions < 10) {
            return transitions;
        }
        int tipY = findDripstoneTipY(x, y, z, false, 7);
        if (tipY == Integer.MIN_VALUE || !canPointedTipGrow(x, tipY, z, false)) {
            return transitions;
        }
        int grown = random.nextInt(2) == 0
                ? growPointedDripstone(x, tipY, z, false)
                : growStalagmiteBelow(x, tipY, z);
        return transitions + grown;
    }

    private int findDripstoneTipY(
            int x, int startY, int z, boolean upward, int searchLength) {
        int direction = upward ? 1 : -1;
        for (int offset = 0; offset < searchLength; offset++) {
            int y = startY + direction * offset;
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y
                    || world.getBlock(x, y, z) != Blocks.POINTED_DRIPSTONE) {
                return Integer.MIN_VALUE;
            }
            int state = world.getState(x, y, z, Blocks.POINTED_DRIPSTONE);
            if (((state & P6Rules.DRIPSTONE_UP) != 0) != upward) {
                return Integer.MIN_VALUE;
            }
            if ((state & P6Rules.DRIPSTONE_THICKNESS_MASK) == P6Rules.DRIPSTONE_TIP) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean canPointedTipGrow(int x, int y, int z, boolean upward) {
        int targetY = y + (upward ? 1 : -1);
        int target = world.getBlock(x, targetY, z);
        if (Fluids.isFluid(target)) return false;
        if (target == Blocks.AIR) return true;
        if (target != Blocks.POINTED_DRIPSTONE) return false;
        int targetState = world.getState(x, targetY, z, target);
        return ((targetState & P6Rules.DRIPSTONE_UP) != 0) != upward
                && (targetState & P6Rules.DRIPSTONE_THICKNESS_MASK)
                        == P6Rules.DRIPSTONE_TIP;
    }

    private int growStalagmiteBelow(int x, int stalactiteTipY, int z) {
        for (int distance = 1; distance <= 10; distance++) {
            int y = stalactiteTipY - distance;
            if (y <= Blocks.MIN_Y) return 0;
            int block = world.getBlock(x, y, z);
            if (Fluids.isFluid(block)) return 0;
            if (block == Blocks.POINTED_DRIPSTONE) {
                int state = world.getState(x, y, z, block);
                if ((state & P6Rules.DRIPSTONE_UP) != 0
                        && (state & P6Rules.DRIPSTONE_THICKNESS_MASK)
                                == P6Rules.DRIPSTONE_TIP
                        && canPointedTipGrow(x, y, z, true)) {
                    return growPointedDripstone(x, y, z, true);
                }
                return 0;
            }
            if (block != Blocks.AIR) return 0;
            int below = world.getBlock(x, y - 1, z);
            boolean supported = Fluids.isSolid(below)
                    || below == Blocks.POINTED_DRIPSTONE
                            && (world.getState(x, y - 1, z, below) & P6Rules.DRIPSTONE_UP) != 0;
            if (supported && !Fluids.isWater(below)) {
                world.setBlock(x, y, z, Blocks.POINTED_DRIPSTONE);
                world.setState(x, y, z, Blocks.POINTED_DRIPSTONE,
                        P6Rules.DRIPSTONE_UP | P6Rules.DRIPSTONE_TIP);
                return 2 + restatePointedDripstoneColumn(x, y, z, true);
            }
        }
        return 0;
    }

    private int growPointedDripstone(int x, int tipY, int z, boolean upward) {
        int direction = upward ? 1 : -1;
        int targetY = tipY + direction;
        int target = world.getBlock(x, targetY, z);
        int transitions = 0;
        if (target == Blocks.POINTED_DRIPSTONE) {
            int targetState = world.getState(x, targetY, z, target);
            boolean oppositeTip = ((targetState & P6Rules.DRIPSTONE_UP) != 0) != upward
                    && (targetState & P6Rules.DRIPSTONE_THICKNESS_MASK)
                            == P6Rules.DRIPSTONE_TIP;
            if (!oppositeTip) return 0;
            world.setState(x, tipY, z, Blocks.POINTED_DRIPSTONE,
                    (upward ? P6Rules.DRIPSTONE_UP : 0) | P6Rules.DRIPSTONE_TIP_MERGE);
            world.setState(x, targetY, z, Blocks.POINTED_DRIPSTONE,
                    (upward ? 0 : P6Rules.DRIPSTONE_UP) | P6Rules.DRIPSTONE_TIP_MERGE);
            transitions += 2;
        } else if (target == Blocks.AIR) {
            world.setBlock(x, targetY, z, Blocks.POINTED_DRIPSTONE);
            world.setState(x, targetY, z, Blocks.POINTED_DRIPSTONE,
                    (upward ? P6Rules.DRIPSTONE_UP : 0) | P6Rules.DRIPSTONE_TIP);
            transitions += 2;
        } else {
            return 0;
        }
        return transitions + restatePointedDripstoneColumn(x, tipY, z, upward);
    }

    int refreshPointedDripstoneColumnsAround(int x, int y, int z) {
        return refreshSpeleothemColumnsAround(Blocks.POINTED_DRIPSTONE, x, y, z);
    }

    int refreshSpeleothemColumnsAround(int blockType, int x, int y, int z) {
        if (!P6Rules.isSpeleothem(blockType)) return 0;
        int transitions = 0;
        for (int candidateY = y - 1; candidateY <= y + 1; candidateY++) {
            if (candidateY < Blocks.MIN_Y || candidateY > Blocks.MAX_Y
                    || world.getBlock(x, candidateY, z) != blockType) {
                continue;
            }
            boolean upward = (world.getState(
                    x, candidateY, z, blockType)
                    & P6Rules.DRIPSTONE_UP) != 0;
            transitions += restateSpeleothemColumn(
                    blockType, x, candidateY, z, upward);
        }
        return transitions;
    }

    private int restatePointedDripstoneColumn(int x, int anyY, int z, boolean upward) {
        return restateSpeleothemColumn(Blocks.POINTED_DRIPSTONE, x, anyY, z, upward);
    }

    private int restateSpeleothemColumn(
            int blockType, int x, int anyY, int z, boolean upward) {
        int direction = upward ? 1 : -1;
        int rootY = anyY;
        while (rootY - direction >= Blocks.MIN_Y && rootY - direction <= Blocks.MAX_Y
                && isSpeleothemWithDirection(blockType, x, rootY - direction, z, upward)) {
            rootY -= direction;
        }
        int length = 0;
        while (rootY + direction * length >= Blocks.MIN_Y
                && rootY + direction * length <= Blocks.MAX_Y
                && isSpeleothemWithDirection(
                        blockType, x, rootY + direction * length, z, upward)) {
            length++;
        }
        int tipY = rootY + direction * (length - 1);
        int beyondTipY = tipY + direction;
        boolean merged = beyondTipY >= Blocks.MIN_Y && beyondTipY <= Blocks.MAX_Y
                && isSpeleothemWithDirection(blockType, x, beyondTipY, z, !upward);
        int transitions = 0;
        for (int offset = 0; offset < length; offset++) {
            int thickness;
            if (length >= 3 && offset == 0) thickness = P6Rules.DRIPSTONE_BASE;
            else if (length >= 3 && offset < length - 2) thickness = P6Rules.DRIPSTONE_MIDDLE;
            else if (length >= 2 && offset == length - 2) thickness = P6Rules.DRIPSTONE_FRUSTUM;
            else thickness = merged ? P6Rules.DRIPSTONE_TIP_MERGE : P6Rules.DRIPSTONE_TIP;
            int state = (upward ? P6Rules.DRIPSTONE_UP : 0) | thickness;
            int y = rootY + direction * offset;
            if (world.getState(x, y, z, blockType) != state) {
                world.setState(x, y, z, blockType, state);
                transitions++;
            }
        }
        return transitions;
    }

    private boolean isPointedWithDirection(int x, int y, int z, boolean upward) {
        return isSpeleothemWithDirection(Blocks.POINTED_DRIPSTONE, x, y, z, upward);
    }

    private boolean isSpeleothemWithDirection(
            int blockType, int x, int y, int z, boolean upward) {
        return world.getBlock(x, y, z) == blockType
                && ((world.getState(x, y, z, blockType)
                        & P6Rules.DRIPSTONE_UP) != 0) == upward;
    }

    /**
     * 1.21.4 ChangeOverTimeBlock: 5.688889% 사전 게이트 뒤 맨해튼 거리 4의 구리군을 비교한다.
     * 더 덜 산화된 이웃이 하나라도 있으면 멈추고, 더 산화된 비율의 제곱(초기 단계 ×0.75)으로 한 단계 진행한다.
     *
     * <p>[COPPER] 이웃 집계는 <b>계열을 가리지 않는다</b> — 바닐라도 {@code getNextState} 가
     * 같은 {@code WeatherState} 열거를 쓰는 모든 {@code ChangeOverTimeBlock} 을 세지, 같은
     * 블록만 세지 않는다. 밀랍 블록은 {@code ChangeOverTimeBlock} 이 아니라 집계에서 빠지고,
     * 그것이 {@link Blocks#isOxidizableCopper(int)} 가 밀랍 표를 보지 않는 이유다.
     *
     * <p>난수 소비는 예전과 글자 그대로 같다: 사전 게이트 1 회 → (난수 없는) 이웃 스캔 →
     * 확률 판정 1 회. 스캔 순서(dy → dz → dx)도 그대로라 정적판 미러와 draw 순서가 어긋나지 않는다.
     */
    private int weatherCopper(int block, int x, int y, int z) {
        // [COPPER-CHEST-PAIR] 구리 상자 겹 상자는 두 반쪽이 따로 늙으면 ID 가 갈려
        // Blocks.chestPairs 가 깨지고 54칸이 27칸 둘로 쪼개진다. 바닐라
        // WeatheringCopperChestBlock.randomTick 은 <b>RIGHT 반쪽을 난수 소비 없이 즉시
        // 반환</b>하고, LEFT 가 늙으면 updateShape 가 이웃의 블록을 속성을 지킨 채 따라가게
        // 한다. 이 판정은 COPPER_RANDOM_TICK_CHANCE 굴림보다 앞에 있어야 난수 순서가
        // 바닐라·정적판 미러와 같다.
        int state = world.getState(x, y, z, block);
        int chestType = Blocks.isChestShaped(block)
                ? (state & BuildingBlockRules.CHEST_TYPE_MASK)
                : BuildingBlockRules.CHEST_SINGLE;
        if (chestType == BuildingBlockRules.CHEST_RIGHT) return 0;
        if (nextFloat() >= COPPER_RANDOM_TICK_CHANCE) return 0;
        int age = CopperAgeRules.oxidationAge(block);
        int same = 0;
        int later = 0;
        for (int dy = -4; dy <= 4; dy++) {
            int wy = y + dy;
            if (wy < Blocks.MIN_Y || wy > Blocks.MAX_Y) continue;
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance == 0 || distance > 4) continue;
                    int neighbor = world.getBlock(x + dx, wy, z + dz);
                    if (!CopperAgeRules.isOxidizable(neighbor)) continue;
                    int neighborAge = CopperAgeRules.oxidationAge(neighbor);
                    if (neighborAge < age) return 0;
                    if (neighborAge > age) later++;
                    else same++;
                }
            }
        }
        float ratio = (later + 1.0f) / (later + same + 1.0f);
        float chance = ratio * ratio * (age == 0 ? 0.75f : 1.0f);
        if (nextFloat() >= chance) return 0;
        return advanceCopper(block, x, y, z, state, chestType);
    }

    /** QA 소요 시간만 건너뛰고 정상 산화의 두 셀 변경 경로를 그대로 사용한다. */
    int qaAdvanceCopperChestPair(int expectedBlock) {
        if (CopperAgeRules.firstOxidationStage(expectedBlock) != Blocks.COPPER_CHEST
                || CopperAgeRules.nextOxidationStage(expectedBlock) < 0
                || world.getBlock(1, 72, 25) != expectedBlock
                || world.getBlock(2, 72, 25) != expectedBlock) return 0;
        int firstState = world.getState(1, 72, 25, expectedBlock);
        int secondState = world.getState(2, 72, 25, expectedBlock);
        if (!BuildingBlockRules.matchingChestStates(firstState, secondState)
                || BuildingBlockRules.chestPartnerX(1, firstState) != 2
                || BuildingBlockRules.chestPartnerZ(25, firstState) != 25
                || BuildingBlockRules.chestPartnerX(2, secondState) != 1
                || BuildingBlockRules.chestPartnerZ(25, secondState) != 25) return 0;
        int leftX = (firstState & BuildingBlockRules.CHEST_TYPE_MASK)
                == BuildingBlockRules.CHEST_LEFT ? 1 : 2;
        int leftState = leftX == 1 ? firstState : secondState;
        return advanceCopper(expectedBlock, leftX, 72, 25, leftState,
                BuildingBlockRules.CHEST_LEFT);
    }

    private int advanceCopper(int block, int x, int y, int z, int state, int chestType) {
        // 계열은 산화 4단계가 연속 ID 라 결과는 언제나 block + 1 이지만, 그 산술이 계열
        // 경계를 넘지 않는지는 정본 표가 확인한다(호출 전 게이트와 같은 함수).
        // 바닐라 WeatheringCopper.getNext 는 `next.withPropertiesOf(state)` 로 <b>기존
        // blockstate 속성을 그대로 옮긴다</b>. 산화는 형상을 바꾸지 않으므로 반 블록의
        // top/bottom/double, 문·다락문의 half/facing/open 이 보존돼야 한다 — 도끼 긁기
        // (WorldTickLoop 의 scrapeState) 와 같은 계약이다.
        int next = CopperAgeRules.nextOxidationStage(block);
        world.setBlock(x, y, z, next);
        // setBlock 은 단일 편집 깔때기에서 state 를 0 으로 되돌리므로(applyTree 와 같은 순서)
        // 0 이 아닐 때만 되돌려 놓는다.
        if (state != 0) world.setState(x, y, z, next, state);
        // 왼쪽 반쪽이 늙었으면 짝도 같은 틱에 같은 단계로 옮긴다 — 같은 편집 경로를 쓰므로
        // 영속화·클라 브로드캐스트가 두 셀을 모두 본다.
        if (chestType == BuildingBlockRules.CHEST_LEFT) {
            int px = BuildingBlockRules.chestPartnerX(x, state);
            int pz = BuildingBlockRules.chestPartnerZ(z, state);
            int partner = world.getBlock(px, y, pz);
            if (Blocks.chestPairs(block, partner)) {
                int partnerState = world.getState(px, y, pz, partner);
                if (BuildingBlockRules.matchingChestStates(state, partnerState)) {
                    world.setBlock(px, y, pz, next);
                    if (partnerState != 0) world.setState(px, y, pz, next, partnerState);
                    return 2;
                }
            }
        }
        return 1;
    }

    /** java.util.Random.nextFloat와 같은 24-bit 균등 격자를 기존 RandomSource 계약 위에 구성한다. */
    private float nextFloat() {
        return random.nextInt(RANDOM_FLOAT_BOUND) / (float) RANDOM_FLOAT_BOUND;
    }

    /** 진달래 뼛가루는 기존 private 나무 planner/원자 적용 경로를 그대로 사용한다. */
    private int growAzaleaTree(int azalea, int x, int y, int z) {
        if (world.lightLevel(x, y + 1, z) < SAPLING_LIGHT
                || !SupportRules.isSupported(azalea, x, y, z, world::getBlock)) return 0;
        int trunkHeight = 4 + random.nextInt(3);
        TreePlan plan = planOrdinaryTree(x, y, z, trunkHeight, Blocks.LOG, Blocks.LEAVES);
        if (plan.size() > MAX_TRANSITIONS
                || !hasRequiredClearance(x, y, z, trunkHeight, false, false)
                || !canApplyTree(plan, x, y, z, azalea)) return 0;
        applyTree(plan);
        return plan.size();
    }

    private void applyTree(TreePlan plan) {
        for (Map.Entry<BlockPos, Integer> placement : plan.blocks.entrySet()) {
            BlockPos pos = placement.getKey();
            int block = placement.getValue();
            world.setBlock(pos.x(), pos.y(), pos.z(), block);
            int state = plan.state(pos);
            if (state != 0) world.setState(pos.x(), pos.y(), pos.z(), block, state);
        }
    }

    private TreePlan planOrdinaryTree(
            int x, int y, int z, int trunkHeight, int log, int leaves) {
        TreePlan plan = new TreePlan();
        int top = y + trunkHeight - 1;
        for (int ty = y; ty <= top; ty++) plan.log(x, ty, z, log);

        leafPlus(plan, x, top + 1, z, leaves);
        leafPlus(plan, x, top, z, leaves);
        int upperCount = 1 + random.nextInt(3);
        int upperStart = random.nextInt(4);
        for (int i = 0; i < upperCount; i++) {
            addCornerLeaf(plan, x, top, z, 1, (upperStart + i) & 3, leaves);
        }
        for (int layer = 1; layer <= 2; layer++) {
            leafRing5(plan, x, top - layer, z, leaves);
            int cornerCount = random.nextInt(5);
            int cornerStart = random.nextInt(4);
            for (int i = 0; i < cornerCount; i++) {
                addCornerLeaf(plan, x, top - layer, z, 2, (cornerStart + i) & 3, leaves);
            }
        }
        return plan;
    }

    /**
     * [POPLAR] 포플러. 위키가 확인해 주는 것은 <b>"줄기 하나 + 유난히 큰 수관"</b> 하나뿐
     * 이고(핀 §2) 층 수·반지름·잎 곡선은 어디에도 없다(§5). 그래서 형상은 [C] 설계이며,
     * 새 문법을 만들지 않고 이 파일이 이미 가진 두 벽돌만 쓴다 — 곧은 줄기 + 사각 잎 층.
     *
     * <p>다른 수종과 갈리는 지점은 딱 둘이다: 수관이 <b>반지름 3</b> 까지 넓어지고(다른
     * 수종은 2), 줄기 꼭대기 <b>세 칸에만</b> 얹혀 우산처럼 보인다. 그래야 "유난히 큰
     * 수관" 이 실루엣으로 읽힌다.
     *
     * <p>잎 색은 세 변종 중 하나를 <b>나무 단위로</b> 고른다 — 바닐라도 묘목이 자랄 때
     * 하나를 고르는 것이지 칸마다 섞이는 것이 아니다 [B].
     */
    private TreePlan planPoplar(int x, int y, int z, int trunkHeight) {
        int leaves = Blocks.POPLAR_LEAVES_YELLOW + random.nextInt(3);
        TreePlan plan = new TreePlan();
        int top = y + trunkHeight - 1;
        for (int ty = y; ty <= top; ty++) plan.log(x, ty, z, Blocks.POPLAR_LOG);

        // 꼭대기 마감: 한 칸 위 십자 + 꼭대기 십자(다른 수종과 같은 문법).
        leafPlus(plan, x, top + 1, z, leaves);
        leafPlus(plan, x, top, z, leaves);
        int upperCount = 1 + random.nextInt(3);
        int upperStart = random.nextInt(4);
        for (int i = 0; i < upperCount; i++) {
            addCornerLeaf(plan, x, top, z, 1, (upperStart + i) & 3, leaves);
        }
        // 큰 수관: 아래 세 층이 반지름 3 → 3 → 2 로 벌어졌다 좁아진다.
        int[] radii = {3, 3, 2};
        for (int layer = 0; layer < radii.length; layer++) {
            int radius = radii[layer];
            int ly = top - 1 - layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue; // 줄기 칸
                    // 모서리는 잘라 원형에 가깝게 만들고, 가장자리는 성기게 둔다.
                    int reach = Math.abs(dx) + Math.abs(dz);
                    if (reach > radius + 1) continue;
                    if (reach == radius + 1 && random.nextInt(2) == 0) continue;
                    plan.leaf(x + dx, ly, z + dz, leaves);
                }
            }
        }
        return plan;
    }

    private TreePlan planSaplingTree(int sapling, int x, int y, int z,
            int trunkHeight, boolean largeOak) {
        if (largeOak) return planLargeOak(x, y, z, trunkHeight);
        if (sapling == Blocks.BIRCH_SAPLING) {
            return planOrdinaryTree(x, y, z, trunkHeight, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        }
        if (sapling == Blocks.SPRUCE_SAPLING) return planConifer(x, y, z, trunkHeight);
        if (sapling == Blocks.JUNGLE_SAPLING) {
            return planOrdinaryTree(x, y, z, trunkHeight, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES);
        }
        if (sapling == Blocks.ACACIA_SAPLING) return planAcacia(x, y, z, trunkHeight);
        if (sapling == Blocks.DARK_OAK_SAPLING) return planDarkOak(x, y, z, trunkHeight);
        if (sapling == Blocks.CHERRY_SAPLING) return planCherry(x, y, z, trunkHeight);
        if (sapling == Blocks.MANGROVE_PROPAGULE) return planMangrove(x, y, z, trunkHeight);
        if (sapling == Blocks.POPLAR_SAPLING) return planPoplar(x, y, z, trunkHeight);
        return planOrdinaryTree(x, y, z, trunkHeight, Blocks.LOG, Blocks.LEAVES);
    }

    private TreePlan planConifer(int x, int y, int z, int height) {
        TreePlan plan = new TreePlan();
        int top = y + height - 1;
        for (int ty = y; ty <= top; ty++) plan.log(x, ty, z, Blocks.SPRUCE_LOG);
        for (int layer = 0; layer < Math.min(5, height - 1); layer++) {
            int radius = Math.min(2, (layer + 1) / 2);
            int ly = top - layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius + 1) {
                        plan.leaf(x + dx, ly, z + dz, Blocks.SPRUCE_LEAVES);
                    }
                }
            }
        }
        plan.leaf(x, top + 1, z, Blocks.SPRUCE_LEAVES);
        return plan;
    }

    private TreePlan planAcacia(int x, int y, int z, int height) {
        TreePlan plan = new TreePlan();
        int[] direction = BRANCH_DIRECTIONS[random.nextInt(4) * 2];
        int bendStart = Math.max(2, height - 3);
        int tx = x;
        int tz = z;
        for (int dy = 0; dy < height; dy++) {
            if (dy >= bendStart) {
                tx += direction[0];
                tz += direction[1];
            }
            plan.log(tx, y + dy, tz, Blocks.ACACIA_LOG);
        }
        int top = y + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                plan.leaf(tx + dx, top, tz + dz, Blocks.ACACIA_LEAVES);
            }
        }
        leafPlus(plan, tx, top + 1, tz, Blocks.ACACIA_LEAVES);
        return plan;
    }

    private TreePlan planDarkOak(int x, int y, int z, int height) {
        TreePlan plan = new TreePlan();
        for (int dy = 0; dy < height; dy++) {
            plan.log(x, y + dy, z, Blocks.DARK_OAK_LOG);
            plan.log(x + 1, y + dy, z, Blocks.DARK_OAK_LOG);
            plan.log(x, y + dy, z + 1, Blocks.DARK_OAK_LOG);
            plan.log(x + 1, y + dy, z + 1, Blocks.DARK_OAK_LOG);
        }
        int top = y + height;
        for (int dx = -2; dx <= 3; dx++) {
            for (int dz = -2; dz <= 3; dz++) {
                if ((dx == -2 || dx == 3) && (dz == -2 || dz == 3)) continue;
                plan.leaf(x + dx, top, z + dz, Blocks.DARK_OAK_LEAVES);
            }
        }
        return plan;
    }

    private TreePlan planCherry(int x, int y, int z, int height) {
        TreePlan plan = new TreePlan();
        int top = y + height - 1;
        for (int dy = 0; dy < height; dy++) plan.log(x, y + dy, z, Blocks.CHERRY_LOG);
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int axis = direction[0] != 0 ? 1 : 2;
            plan.log(x + direction[0], top, z + direction[1], Blocks.CHERRY_LOG, axis);
        }
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 0 ? 3 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius + 1) {
                        plan.leaf(x + dx, top + 1 + dy, z + dz, Blocks.CHERRY_LEAVES);
                    }
                }
            }
        }
        return plan;
    }

    private TreePlan planMangrove(int x, int y, int z, int height) {
        TreePlan plan = new TreePlan();
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            plan.log(x + direction[0], y, z + direction[1], Blocks.MANGROVE_ROOTS);
            plan.log(x + direction[0] * 2, y - 1, z + direction[1] * 2,
                    Blocks.MUDDY_MANGROVE_ROOTS);
        }
        int top = y + height - 1;
        for (int dy = 0; dy < height; dy++) plan.log(x, y + dy, z, Blocks.MANGROVE_LOG);
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 0 ? 3 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius + 2) {
                        plan.leaf(x + dx, top + dy, z + dz, Blocks.MANGROVE_LEAVES);
                    }
                }
            }
        }
        return plan;
    }

    private int[] darkOakRoot(int x, int y, int z) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int rx = x + dx;
                int rz = z + dz;
                if (world.getBlock(rx, y, rz) == Blocks.DARK_OAK_SAPLING
                        && world.getBlock(rx + 1, y, rz) == Blocks.DARK_OAK_SAPLING
                        && world.getBlock(rx, y, rz + 1) == Blocks.DARK_OAK_SAPLING
                        && world.getBlock(rx + 1, y, rz + 1) == Blocks.DARK_OAK_SAPLING) {
                    return new int[] {rx, rz};
                }
            }
        }
        return null;
    }

    /** 지형생성기의 팬시 오크와 같은 9~13 줄기·수평 가지·잎뭉치 실루엣. */
    private TreePlan planLargeOak(int x, int y, int z, int trunkHeight) {
        TreePlan plan = new TreePlan();
        int top = y + trunkHeight - 1;
        int bare = Math.max(3, (int) Math.floor(trunkHeight * 0.618));
        for (int ty = y; ty <= top; ty++) plan.log(x, ty, z, Blocks.LOG);
        for (int branch = 0; branch < 2; branch++) {
            int[] direction = BRANCH_DIRECTIONS[random.nextInt(BRANCH_DIRECTIONS.length)];
            int length = 4 + random.nextInt(2);
            int branchY = Math.min(y + bare + branch, top - 1);
            int bx = x;
            int bz = z;
            int by = branchY;
            for (int step = 1; step <= length; step++) {
                bx += direction[0];
                bz += direction[1];
                by = Math.min(branchY + Math.floorDiv(step, 4), top);
                plan.log(bx, by, bz, Blocks.LOG);
            }
            leafBlob(plan, bx, by, bz, Blocks.LEAVES);
        }
        leafBlob(plan, x, top, z, Blocks.LEAVES);
        return plan;
    }

    private boolean canApplyTree(TreePlan plan, int sourceX, int sourceY, int sourceZ, int sapling) {
        for (Map.Entry<BlockPos, Integer> placement : plan.blocks.entrySet()) {
            BlockPos pos = placement.getKey();
            if (pos.y() < Blocks.MIN_Y || pos.y() > Blocks.MAX_Y) return false;
            int current = world.getBlock(pos.x(), pos.y(), pos.z());
            if (pos.x() == sourceX && pos.y() == sourceY && pos.z() == sourceZ) {
                if (current != sapling) return false;
            } else if (current != Blocks.AIR && current != Blocks.SHELF_MUSHROOM
                    && !(pos.y() == sourceY && current == sapling)
                    && !(placement.getValue() == Blocks.MUDDY_MANGROVE_ROOTS
                            && P6Rules.isSaplingSoil(current))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 일반 오크/자작은 묘목 위부터 3×3 기둥을 비운다. 자작의 완전한 관을 위한
     * 상단 3층은 5×5를 검사한다. 거대 오크는 계획된 줄기/가지/잎 칸을
     * {@link #canApplyTree(TreePlan, int, int, int, int)}에서 직접 검사한다.
     */
    private boolean hasRequiredClearance(
            int x, int y, int z, int trunkHeight, boolean birch, boolean largeOak) {
        if (largeOak) return true;
        int top = y + trunkHeight - 1;
        for (int ty = y + 1; ty <= top + 1; ty++) {
            int radius = birch && ty >= top - 2 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (ty < Blocks.MIN_Y || ty > Blocks.MAX_Y
                            || (world.getBlock(x + dx, ty, z + dz) != Blocks.AIR
                                    && world.getBlock(x + dx, ty, z + dz) != Blocks.SHELF_MUSHROOM)) return false;
                }
            }
        }
        return true;
    }

    private static void addCornerLeaf(
            TreePlan plan, int x, int y, int z, int radius, int corner, int leaves) {
        int dx = (corner == 0 || corner == 3) ? -radius : radius;
        int dz = (corner == 0 || corner == 1) ? -radius : radius;
        plan.leaf(x + dx, y, z + dz, leaves);
    }

    private static void leafPlus(TreePlan plan, int x, int y, int z, int leaves) {
        plan.leaf(x, y, z, leaves);
        plan.leaf(x + 1, y, z, leaves);
        plan.leaf(x - 1, y, z, leaves);
        plan.leaf(x, y, z + 1, leaves);
        plan.leaf(x, y, z - 1, leaves);
    }

    private static void leafRing5(TreePlan plan, int x, int y, int z, int leaves) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                plan.leaf(x + dx, y, z + dz, leaves);
            }
        }
    }

    private static void leafBlob(TreePlan plan, int x, int y, int z, int leaves) {
        leafPlus(plan, x, y - 1, z, leaves);
        leafRing5(plan, x, y, z, leaves);
        leafPlus(plan, x, y + 1, z, leaves);
    }

    private boolean connectedToLog(int x, int y, int z) {
        int generation = nextLeafSearchGeneration();
        int head = 0;
        int tail = 1;
        leafSearchX[0] = 0;
        leafSearchY[0] = 0;
        leafSearchZ[0] = 0;
        leafSearchDistance[0] = 0;
        leafSearchVisited[leafSearchIndex(0, 0, 0)] = generation;
        while (head < tail) {
            int offsetX = leafSearchX[head];
            int offsetY = leafSearchY[head];
            int offsetZ = leafSearchZ[head];
            int distance = leafSearchDistance[head++];
            if (distance >= 6) continue;
            int nextDistance = distance + 1;
            for (int[] direction : DIRECTIONS) {
                int nextOffsetX = offsetX + direction[0];
                int nextOffsetY = offsetY + direction[1];
                int nextOffsetZ = offsetZ + direction[2];
                int nx = x + nextOffsetX;
                int ny = y + nextOffsetY;
                int nz = z + nextOffsetZ;
                int neighbor = world.getBlock(nx, ny, nz);
                if (isWoodLog(neighbor)) return true;
                if (!isWoodLeaves(neighbor) || nextDistance >= 6) continue;
                int visitedIndex = leafSearchIndex(nextOffsetX, nextOffsetY, nextOffsetZ);
                if (leafSearchVisited[visitedIndex] == generation) continue;
                leafSearchVisited[visitedIndex] = generation;
                leafSearchX[tail] = (byte) nextOffsetX;
                leafSearchY[tail] = (byte) nextOffsetY;
                leafSearchZ[tail] = (byte) nextOffsetZ;
                leafSearchDistance[tail] = (byte) nextDistance;
                tail++;
            }
        }
        return false;
    }

    private int nextLeafSearchGeneration() {
        if (leafSearchGeneration == Integer.MAX_VALUE) {
            for (int i = 0; i < leafSearchVisited.length; i++) leafSearchVisited[i] = 0;
            leafSearchGeneration = 1;
        } else {
            leafSearchGeneration++;
        }
        return leafSearchGeneration;
    }

    private static int leafSearchIndex(int x, int y, int z) {
        return ((x + LEAF_SEARCH_RADIUS) * LEAF_SEARCH_DIAMETER
                + y + LEAF_SEARCH_RADIUS) * LEAF_SEARCH_DIAMETER
                + z + LEAF_SEARCH_RADIUS;
    }

    /** MC Java의 4회 시도 및 3x5x3(x/z -1..1, y -3..1) 대상 기하. */
    private int spreadGrass(int x, int y, int z, int transitionBudget) {
        int transitions = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (transitions >= transitionBudget) break;
            int tx = x + random.nextInt(3) - 1;
            int ty = y + random.nextInt(5) - 3;
            int tz = z + random.nextInt(3) - 1;
            if (ty < Blocks.MIN_Y || ty > Blocks.MAX_Y) continue;
            int target = world.getBlock(tx, ty, tz);
            if (P1Rules.canGrassSpreadTo(target, target == Blocks.DIRT)
                    && !blocksGrassLight(world.getBlock(tx, ty + 1, tz))) {
                world.setBlock(tx, ty, tz, Blocks.GRASS);
                transitions++;
            }
        }
        return transitions;
    }

    /**
     * 서버 조명 API가 없으므로 잔디에 필요한 투명도만 단순 매핑한다. 유체와 일반 불투명
     * 풀블록은 빛을 막지만 유리·잎·하단 반블록은 충돌 고체여도 잔디 빛을 막지 않는다.
     */
    private static boolean blocksGrassLight(int block) {
        if (Fluids.isFluid(block)) return true;
        if (Blocks.isGlassBlock(block) || BlockFamilies.isLeaves(block)
                || block == Blocks.PLANK_SLAB || block == Blocks.COBBLE_SLAB) return false;
        return Fluids.isSolid(block);
    }

    private int growColumn(int block, int x, int y, int z) {
        if (random.nextInt(16) != 0 || y + 1 > Blocks.MAX_Y
                || world.getBlock(x, y + 1, z) != Blocks.AIR) return 0;
        int height = 1;
        while (height < 3 && world.getBlock(x, y - height, z) == block) height++;
        int bottomY = y - height + 1;
        if (height >= 3
                || !SupportRules.isSupported(block, x, bottomY, z, world::getBlock)
                || !SupportRules.isSupported(block, x, y + 1, z, world::getBlock)) return 0;
        world.setBlock(x, y + 1, z, block);
        return 1;
    }

    /** 1.21.5 CactusBlock: 성장 시 높이별 확률로 빈 꼭대기에 선인장 꽃을 먼저 시도한다. */
    private int growCactus(int x, int y, int z) {
        if (random.nextInt(16) != 0 || y >= Blocks.MAX_Y
                || world.getBlock(x, y + 1, z) != Blocks.AIR) return 0;
        int height = 1;
        while (height < 3 && world.getBlock(x, y - height, z) == Blocks.CACTUS) height++;
        int bottomY = y - height + 1;
        if (!SupportRules.isSupported(Blocks.CACTUS, x, bottomY, z, world::getBlock)) return 0;
        boolean flowerClear = world.getBlock(x + 1, y + 1, z) == Blocks.AIR
                && world.getBlock(x - 1, y + 1, z) == Blocks.AIR
                && world.getBlock(x, y + 1, z + 1) == Blocks.AIR
                && world.getBlock(x, y + 1, z - 1) == Blocks.AIR;
        int flowerChance = height >= 3 ? 25 : 10;
        if (flowerClear && random.nextInt(100) < flowerChance) {
            world.setBlock(x, y + 1, z, Blocks.CACTUS_FLOWER);
            return 1;
        }
        if (height >= 3 || !SupportRules.isSupported(
                Blocks.CACTUS, x, y + 1, z, world::getBlock)) return 0;
        world.setBlock(x, y + 1, z, Blocks.CACTUS);
        return 1;
    }

    /** P2의 높이/성장 규칙을 기존 좌표 랜덤틱에 연결한다. 별도 전수 순회는 만들지 않는다. */
    private int growBamboo(int x, int y, int z, int transitionBudget) {
        if (transitionBudget <= 0 || world.getBlock(x, y + 1, z) == Blocks.BAMBOO) return 0;
        int bottomY = bambooBottom(x, y, z);
        int columnHeight = y - bottomY + 1;
        int heightLimit = bambooHeightLimit(x, bottomY, z);
        if (!SupportRules.isSupported(Blocks.BAMBOO, x, bottomY, z, world::getBlock)
                || !P2Rules.canBambooGrow(columnHeight, heightLimit,
                y < Blocks.MAX_Y && world.getBlock(x, y + 1, z) == Blocks.AIR,
                world.lightLevel(x, y + 1, z))) return 0;
        world.setBlock(x, y + 1, z, Blocks.BAMBOO);
        return 1;
    }

    /** 선택한 줄기 어느 칸에 사용해도 꼭대기에서만 1~2칸 자라며 전역 전이 예산을 넘지 않는다. */
    private int growBambooWithBoneMeal(int x, int y, int z) {
        int topY = y;
        while (topY < Blocks.MAX_Y && world.getBlock(x, topY + 1, z) == Blocks.BAMBOO) topY++;
        int bottomY = bambooBottom(x, topY, z);
        if (!SupportRules.isSupported(Blocks.BAMBOO, x, bottomY, z, world::getBlock)) return 0;
        int columnHeight = topY - bottomY + 1;
        int requested = Math.min(MAX_TRANSITIONS,
                P2Rules.bambooBoneMealGrowth(random.nextInt(2)));
        int grown = 0;
        while (grown < requested
                && P2Rules.canBambooGrow(columnHeight, P2Rules.BAMBOO_MAX_HEIGHT,
                        topY < Blocks.MAX_Y && world.getBlock(x, topY + 1, z) == Blocks.AIR,
                        15)) {
            world.setBlock(x, ++topY, z, Blocks.BAMBOO);
            columnHeight++;
            grown++;
        }
        return grown;
    }

    /** 자연 대나무의 12~16칸 상한은 월드/기둥마다 고정되어 틱·재접속 때 다시 뽑히지 않는다. */
    int bambooHeightLimit(int x, int bottomY, int z) {
        long mixed = bambooCapSeed
                ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) bottomY * 0xBF58476D1CE4E5B9L
                ^ (long) z * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return P2Rules.bambooHeightLimit((int) Math.floorMod(mixed, 5L));
    }

    /** 상호작용 전에 지지·공간·최대 높이만 확인한다. 뼛가루 성장은 조도를 요구하지 않는다. */
    boolean canApplyBoneMeal(int x, int y, int z) {
        // [PITCHER] PitcherCropBlock.isValidBonemealTarget: canGrow(age + 1) on the lower half.
        if (world.getBlock(x, y, z) == Blocks.PITCHER_CROP) {
            int[] lower = pitcherLowerHalf(x, y, z);
            if (lower == null) return false;
            int above = lower[0] + 1 <= Blocks.MAX_Y ? world.getBlock(x, lower[0] + 1, z) : Blocks.AIR;
            return PitcherRules.canGrow(lower[1], PitcherRules.age(lower[1]) + 1,
                    world.lightLevel(x, lower[0], z), lower[0], above);
        }
        if (world.getBlock(x, y, z) != Blocks.BAMBOO) return false;
        int topY = y;
        while (topY < Blocks.MAX_Y && world.getBlock(x, topY + 1, z) == Blocks.BAMBOO) topY++;
        int bottomY = bambooBottom(x, topY, z);
        int columnHeight = topY - bottomY + 1;
        return SupportRules.isSupported(Blocks.BAMBOO, x, bottomY, z, world::getBlock)
                && P2Rules.canBambooGrow(columnHeight, P2Rules.BAMBOO_MAX_HEIGHT,
                        topY < Blocks.MAX_Y && world.getBlock(x, topY + 1, z) == Blocks.AIR, 15);
    }

    private int bambooBottom(int x, int y, int z) {
        int bottomY = y;
        while (bottomY > Blocks.MIN_Y && world.getBlock(x, bottomY - 1, z) == Blocks.BAMBOO) bottomY--;
        return bottomY;
    }

    /** P2 덩굴 규칙은 샘플된 덩굴 바로 아래 한 칸만 처리해 랜덤틱 비용을 O(1)로 유지한다. */
    private int growVineDown(int x, int y, int z, int transitionBudget) {
        if (transitionBudget <= 0 || y <= Blocks.MIN_Y) return 0;
        int below = world.getBlock(x, y - 1, z);
        if (!P2Rules.canVineGrowDown(Fluids.isReplaceable(below))) return 0;
        int state = world.getState(x, y, z, Blocks.VINE)
                & (P2Rules.VINE_NORTH | P2Rules.VINE_EAST
                        | P2Rules.VINE_SOUTH | P2Rules.VINE_WEST);
        if (state == 0) return 0;
        if (below == Blocks.VINE) {
            int currentState = world.getState(x, y - 1, z, Blocks.VINE);
            state |= currentState;
            if (state == currentState) return 0;
        } else {
            world.setBlock(x, y - 1, z, Blocks.VINE);
        }
        world.setState(x, y - 1, z, Blocks.VINE, state);
        return 1;
    }

    /**
     * MC Java의 작은 버섯 번식: 랜덤 틱당 1/25, 같은 종류가 9x3x9 안에 5개 미만일 때만
     * 네 번의 후보 보행 뒤 마지막 유효 후보에 하나를 놓는다.
     */
    private int spreadMushroom(int mushroom, int x, int y, int z, int transitionBudget) {
        if (transitionBudget <= 0 || random.nextInt(25) != 0
                || hasMushroomDensityCap(mushroom, x, y, z)) return 0;

        int currentX = x;
        int currentY = y;
        int currentZ = z;
        int candidateX = x + random.nextInt(3) - 1;
        int candidateY = y + random.nextInt(2) - random.nextInt(2);
        int candidateZ = z + random.nextInt(3) - 1;

        for (int attempt = 0; attempt < 4; attempt++) {
            if (canMushroomSurvive(candidateX, candidateY, candidateZ)) {
                currentX = candidateX;
                currentY = candidateY;
                currentZ = candidateZ;
            }
            candidateX = currentX + random.nextInt(3) - 1;
            candidateY = currentY + random.nextInt(2) - random.nextInt(2);
            candidateZ = currentZ + random.nextInt(3) - 1;
        }

        if (!canMushroomSurvive(candidateX, candidateY, candidateZ)) return 0;
        world.setBlock(candidateX, candidateY, candidateZ, mushroom);
        return 1;
    }

    private boolean hasMushroomDensityCap(int mushroom, int x, int y, int z) {
        int remaining = 5;
        for (int tx = x - 4; tx <= x + 4; tx++) {
            for (int ty = y - 1; ty <= y + 1; ty++) {
                for (int tz = z - 4; tz <= z + 4; tz++) {
                    if (world.getBlock(tx, ty, tz) == mushroom && --remaining <= 0) return true;
                }
            }
        }
        return false;
    }

    private boolean canMushroomSurvive(int x, int y, int z) {
        return y > Blocks.MIN_Y && y <= Blocks.MAX_Y
                && world.getBlock(x, y, z) == Blocks.AIR
                && world.lightLevel(x, y, z) < 13
                && isOpaqueFullTop(world.getBlock(x, y - 1, z));
    }

    /** WebCraft 블록 집합 중 MC의 opaque full-top 지지면에 해당하는 풀블록. */
    private static boolean isOpaqueFullTop(int block) {
        return block == Blocks.STONE || block == Blocks.DIRT || block == Blocks.GRASS
                || block == Blocks.MYCELIUM
                || block == Blocks.SAND || BlockFamilies.isWoodLog(block) || block == Blocks.PLANK
                || block == Blocks.COBBLE || block == Blocks.BEDROCK
                || block == Blocks.GRAVEL || block == Blocks.MUDDY_MANGROVE_ROOTS
                || (block >= Blocks.GRANITE && block <= Blocks.DRIPSTONE_BLOCK)
                || (block >= Blocks.COAL_ORE && block <= Blocks.DIAMOND_ORE)
                || (block >= Blocks.EMERALD_ORE && block <= Blocks.REDSTONE_ORE)
                || block == Blocks.STONE_BRICK || block == Blocks.FURNACE
                || block == Blocks.FURNACE_LIT
                || (block >= Blocks.COAL_BLOCK && block <= Blocks.DIAMOND_BLOCK)
                || block == Blocks.OBSIDIAN;
    }

    int processBurning(Set<Long> activeChunks, long tickNo, int budget) {
        int transitions = 0;
        List<BlockPos> snapshot = burningWorklist;
        snapshot.clear();
        snapshot.addAll(burning.keySet());
        for (BlockPos pos : snapshot) {
            if (transitions >= budget || !activeChunks.contains(chunkKey(
                    Math.floorDiv(pos.x(), 16), Math.floorDiv(pos.z(), 16)))) continue;
            BurningBlock scheduled = burning.get(pos);
            if (scheduled == null) continue;
            if (world.getBlock(pos.x(), pos.y(), pos.z()) != Blocks.FIRE) {
                removeTrackedFire(pos);
                continue;
            }
            if (tickNo < scheduled.nextUpdateTick) continue;
            scheduled.nextUpdateTick = tickNo + nextFireTickDelay();
            transitions += tickFire(pos, tickNo, budget - transitions);
        }
        publishFireSnapshotIfDirty();
        return transitions;
    }

    /** Flint-and-steel placement validity: the target is empty and fire has sturdy/flammable support. */
    boolean canPlaceFire(int x, int y, int z) {
        return y >= Blocks.MIN_Y && y <= Blocks.MAX_Y
                && world.getBlock(x, y, z) == Blocks.AIR
                && canFireSurvive(x, y, z);
    }

    /** Places an actual FIRE cell. Successful-only durability remains the caller's responsibility. */
    boolean ignite(int x, int y, int z, long tickNo) {
        boolean ignited = placeFire(x, y, z, 0, tickNo, true);
        publishFireSnapshotIfDirty();
        return ignited;
    }

    /** Rebuilds the bounded scheduler when a resident chunk supplies a persisted FIRE cell. */
    boolean trackLoadedFire(int x, int y, int z, long tickNo) {
        boolean tracked = trackLoadedFireDeferred(x, y, z, tickNo);
        publishFireSnapshotIfDirty();
        return tracked;
    }

    private boolean trackLoadedFireDeferred(int x, int y, int z, long tickNo) {
        if (world.getBlock(x, y, z) != Blocks.FIRE) return false;
        BlockPos pos = new BlockPos(x, y, z);
        if (burning.containsKey(pos)) return false;
        burning.put(pos, new BurningBlock(tickNo + nextFireTickDelay()));
        fireDirty = true;
        return true;
    }

    /** Water/player removal hook. Adjacent water does not extinguish vanilla fire. */
    boolean extinguish(int x, int y, int z) {
        if (world.getBlock(x, y, z) != Blocks.FIRE) return false;
        world.setBlock(x, y, z, Blocks.AIR);
        removeTrackedFire(new BlockPos(x, y, z));
        publishFireSnapshotIfDirty();
        return true;
    }

    /** Scheduled support recheck after any neighbor mutation. */
    boolean onNeighborChanged(int x, int y, int z) {
        if (world.getBlock(x, y, z) != Blocks.FIRE || canFireSurvive(x, y, z)) return false;
        return extinguish(x, y, z);
    }

    /** Drops scheduler-only state for an evicted chunk; the persisted FIRE cells remain authoritative. */
    boolean onChunkReleased(int chunkX, int chunkZ) {
        boolean changed = burning.keySet().removeIf(pos -> Math.floorDiv(pos.x(), 16) == chunkX
                && Math.floorDiv(pos.z(), 16) == chunkZ);
        if (changed) {
            fireDirty = true;
            publishFireSnapshotIfDirty();
        }
        return changed;
    }

    /** Lava-fluid hook for runtimes that schedule lava separately from random block samples. */
    boolean igniteFromLava(int x, int y, int z, long tickNo) {
        boolean ignited = Fluids.isLava(world.getBlock(x, y, z))
                && igniteFromLavaDeferred(x, y, z, tickNo);
        publishFireSnapshotIfDirty();
        return ignited;
    }

    private boolean igniteFromLavaDeferred(int x, int y, int z, long tickNo) {
        int attempts = random.nextInt(3);
        if (attempts > 0) {
            int tx = x;
            int ty = y;
            int tz = z;
            for (int attempt = 0; attempt < attempts; attempt++) {
                tx += random.nextInt(3) - 1;
                ty++;
                tz += random.nextInt(3) - 1;
                if (!world.isChunkResident(Math.floorDiv(tx, 16), Math.floorDiv(tz, 16))) return false;
                int target = world.getBlock(tx, ty, tz);
                if (target == Blocks.AIR && hasLavaIgnitableNeighbor(tx, ty, tz)) {
                    return placeFire(tx, ty, tz, 0, tickNo, false);
                }
                if (Fluids.isSolid(target)) return false;
            }
            return false;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            int tx = x + random.nextInt(3) - 1;
            int tz = z + random.nextInt(3) - 1;
            if (isLavaIgnitable(world.getBlock(tx, y, tz))
                    && world.getBlock(tx, y + 1, tz) == Blocks.AIR) {
                return placeFire(tx, y + 1, tz, 0, tickNo, false);
            }
        }
        return false;
    }

    private int tickFire(BlockPos pos, long tickNo, int budget) {
        if (budget <= 0) return 0;
        int x = pos.x();
        int y = pos.y();
        int z = pos.z();
        if (!canFireSurvive(x, y, z)) {
            extinguishDeferred(pos);
            return 1;
        }
        int age = Math.min(MAX_FIRE_AGE, world.getState(x, y, z, Blocks.FIRE) & 0x0F);
        boolean fireSource = world.isFireSource(x, y - 1, z);
        if (!fireSource && nearRain(x, y, z)
                && random.nextInt(1000) < 200 + age * 30) {
            extinguishDeferred(pos);
            return 1;
        }

        int nextAge = Math.min(MAX_FIRE_AGE, age + random.nextInt(3) / 2);
        int transitions = 0;
        if (nextAge != age) {
            world.setState(x, y, z, Blocks.FIRE, nextAge);
            age = nextAge;
            transitions++;
        }

        if (!fireSource) {
            if (!hasFlammableNeighbor(x, y, z)) {
                if (!hasSturdyTop(x, y - 1, z) || random.nextInt(4) == 0) {
                    extinguishDeferred(pos);
                    return transitions + 1;
                }
                return transitions;
            }
            if (age == MAX_FIRE_AGE && random.nextInt(4) == 0
                    && !isFlammable(world.getBlock(x, y - 1, z))) {
                extinguishDeferred(pos);
                return transitions + 1;
            }
        }

        int humidityPenalty = world.isHumid(x, y, z) ? -50 : 0;
        int[][] burnDirections = {
                { 1, 0, 0, 300 }, { -1, 0, 0, 300 },
                { 0, -1, 0, 250 }, { 0, 1, 0, 250 },
                { 0, 0, -1, 300 }, { 0, 0, 1, 300 }
        };
        for (int[] direction : burnDirections) {
            if (transitions >= budget) return transitions;
            transitions += checkBurnOut(x + direction[0], y + direction[1], z + direction[2],
                    direction[3] + humidityPenalty, age, tickNo);
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    if (transitions >= budget) return transitions;
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int tx = x + dx;
                    int ty = y + dy;
                    int tz = z + dz;
                    if (world.getBlock(tx, ty, tz) != Blocks.AIR) continue;
                    int odds = igniteOddsAround(tx, ty, tz);
                    if (odds <= 0) continue;
                    int denominator = 100 + Math.max(0, dy - 1) * 100;
                    int chance = (odds + 40 + Math.max(0, Math.min(3, world.difficultyId())) * 7)
                            / (age + 30);
                    if (world.isHumid(tx, ty, tz)) chance /= 2;
                    if (chance <= 0 || random.nextInt(denominator) > chance
                            || nearRain(tx, ty, tz)) continue;
                    int spreadAge = Math.min(MAX_FIRE_AGE, age + random.nextInt(5) / 4);
                    if (placeFire(tx, ty, tz, spreadAge, tickNo, false)) transitions++;
                }
            }
        }
        return transitions;
    }

    private int checkBurnOut(int x, int y, int z, int denominator, int age, long tickNo) {
        int target = world.getBlock(x, y, z);
        int burnOdds = burnOdds(target);
        if (burnOdds <= 0 || random.nextInt(denominator) >= burnOdds) return 0;
        if (target == Blocks.TNT && world.igniteTnt(x, y, z)) {
            return 1;
        }
        if (random.nextInt(age + 10) < 5 && !world.isRainingAt(x, y, z)) {
            int nextAge = Math.min(MAX_FIRE_AGE, age + random.nextInt(5) / 4);
            replaceWithFire(x, y, z, nextAge, tickNo);
        } else {
            world.setBlock(x, y, z, Blocks.AIR);
        }
        return 1;
    }

    private boolean placeFire(int x, int y, int z, int age, long tickNo, boolean requireSurvival) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y || world.getBlock(x, y, z) != Blocks.AIR
                || requireSurvival && !canFireSurvive(x, y, z)) return false;
        world.setBlock(x, y, z, Blocks.FIRE);
        world.setState(x, y, z, Blocks.FIRE, age & 0x0F);
        BlockPos pos = new BlockPos(x, y, z);
        burning.put(pos, new BurningBlock(tickNo + nextFireTickDelay()));
        fireDirty = true;
        return true;
    }

    private void replaceWithFire(int x, int y, int z, int age, long tickNo) {
        world.setBlock(x, y, z, Blocks.FIRE);
        world.setState(x, y, z, Blocks.FIRE, age & 0x0F);
        burning.put(new BlockPos(x, y, z), new BurningBlock(tickNo + nextFireTickDelay()));
        fireDirty = true;
    }

    private int nextFireTickDelay() {
        return FIRE_UPDATE_TICKS + random.nextInt(FIRE_UPDATE_JITTER);
    }

    private boolean canFireSurvive(int x, int y, int z) {
        return hasSturdyTop(x, y - 1, z) || hasFlammableNeighbor(x, y, z);
    }

    /**
     * [BLOCK-SHAPES] 바닐라 FireBlock 의 {@code below.isFaceSturdy(level, below, UP)} — 면별 FULL 판정
     * (윗 반 블록·이중 반 블록·닫힌 윗 다락문·위 반 계단·유리 등). 잎은 지지 형상이 비어 sturdy 가
     * 아니지만 스스로 탈 수 있는 이웃이라 불은 여전히 그 위에 선다(hasFlammableNeighbor).
     */
    boolean hasSturdyTop(int x, int y, int z) {
        int below = world.getBlock(x, y, z);
        if (below < 0) return false;
        return BlockFaceSturdiness.isFaceSturdy(below, world.getState(x, y, z, below),
                BlockFaceSturdiness.UP, BlockFaceSturdiness.FULL);
    }

    private boolean hasFlammableNeighbor(int x, int y, int z) {
        for (int[] direction : DIRECTIONS) {
            if (isFlammable(world.getBlock(x + direction[0], y + direction[1], z + direction[2]))) {
                return true;
            }
        }
        return false;
    }

    private int igniteOddsAround(int x, int y, int z) {
        int odds = 0;
        for (int[] direction : DIRECTIONS) {
            odds = Math.max(odds, igniteOdds(
                    world.getBlock(x + direction[0], y + direction[1], z + direction[2])));
        }
        return odds;
    }

    private boolean nearRain(int x, int y, int z) {
        return world.isRainingAt(x, y, z) || world.isRainingAt(x - 1, y, z)
                || world.isRainingAt(x + 1, y, z) || world.isRainingAt(x, y, z - 1)
                || world.isRainingAt(x, y, z + 1);
    }

    private void extinguishDeferred(BlockPos pos) {
        world.setBlock(pos.x(), pos.y(), pos.z(), Blocks.AIR);
        removeTrackedFire(pos);
    }

    private void removeTrackedFire(BlockPos pos) {
        if (burning.remove(pos) != null) fireDirty = true;
    }

    private void publishFireSnapshotIfDirty() {
        if (!fireDirty) return;
        fireDirty = false;
        publishedFire = new FireSnapshot(++fireRevision, List.copyOf(burning.keySet()));
    }

    private static boolean isFlammable(int block) {
        return isWoodLog(block)
                || Blocks.isPlankBlock(block) || Blocks.isWoodSlab(block)
                || Blocks.isWoodStairs(block) || Blocks.isFence(block)
                || Blocks.isFenceGate(block)
                || isWoodLeaves(block)
                || block == Blocks.SUNFLOWER
                || block == Blocks.LILAC
                || block == Blocks.ROSE_BUSH
                || block == Blocks.PEONY
                || block == Blocks.PITCHER_PLANT
                || block == Blocks.TALL_GRASS || block == Blocks.FLOWER_RED
                || block == Blocks.FLOWER_YELLOW || block == Blocks.DEAD_BUSH
                || block == Blocks.CACTUS_FLOWER
                || block == Blocks.VINE || block == Blocks.BAMBOO
                || block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT
                || isAnySapling(block)
                || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA
                || isMossCarpet(block) || block == Blocks.HAY_BLOCK
                || block == Blocks.COMPOSTER || block == Blocks.LECTERN
                || block == Blocks.BOOKSHELF
                || block == Blocks.TNT
                || isWoolBlock(block) || isCarpetBlock(block)
                // [SHELF-FUNGUS-WOOL-SLAB] 양털 반 블록은 양털과, 선반은 목재 건축 블록과
                // 같은 가연 갈래다 [B]. 선반버섯은 위키가 "Flammable: Yes" 라고만 적고 괄호
                // 값을 주지 않아 수치가 [C] 다 — 같은 크기의 초본 장식(덩굴·이끼 바닥)과 같은
                // 60/100 을 쓴다(핀 §5.2).
                || (Blocks.isWoolSlab(block) || Blocks.isWoolStairs(block)) || Blocks.isShelf(block)
                || Blocks.isShelfMushroom(block)
                || Blocks.isPoplarFlammableProduct(block);
    }

    private static boolean isLavaIgnitable(int block) {
        return block != Blocks.CACTUS_FLOWER && isFlammable(block);
    }

    private boolean hasLavaIgnitableNeighbor(int x, int y, int z) {
        for (int[] direction : DIRECTIONS) {
            if (isLavaIgnitable(world.getBlock(
                    x + direction[0], y + direction[1], z + direction[2]))) return true;
        }
        return false;
    }

    private static int igniteOdds(int block) {
        if (isWoodLeaves(block)) return 30;
        if (block == Blocks.SUNFLOWER ||
                block == Blocks.LILAC ||
                block == Blocks.ROSE_BUSH ||
                block == Blocks.PEONY ||
                block == Blocks.PITCHER_PLANT ||
                block == Blocks.TALL_GRASS || block == Blocks.FLOWER_RED
                || block == Blocks.FLOWER_YELLOW || block == Blocks.DEAD_BUSH
                || block == Blocks.CACTUS_FLOWER
                || block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT
                || isAnySapling(block)) return 60;
        if (block == Blocks.VINE) return 15;
        if (block == Blocks.BAMBOO || isMossCarpet(block)
                || block == Blocks.HAY_BLOCK) return 60;
        if (block == Blocks.BOOKSHELF || block == Blocks.LECTERN
                || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA) return 30;
        if (block == Blocks.TNT) return 15;
        if (isWoolBlock(block) || (Blocks.isWoolSlab(block) || Blocks.isWoolStairs(block))) return 30;
        if (isCarpetBlock(block)) return 60;
        if (Blocks.isShelf(block)) return 30;
        if (Blocks.isShelfMushroom(block)) return 60;
        if (isWoodLog(block) || Blocks.isPlankBlock(block) || Blocks.isWoodSlab(block)
                || Blocks.isWoodStairs(block) || Blocks.isFence(block)
                || Blocks.isFenceGate(block) || block == Blocks.COMPOSTER
                || Blocks.isPoplarFlammableProduct(block)) return 5;
        return 0;
    }

    /**
     * 이끼 바닥 계열(기존 199 + [PALE-GARDEN] 창백한 이끼 바닥 1506). 바닐라
     * {@code FireBlock.bootStrap()} 은 {@code pale_moss_carpet} 을 {@code moss_carpet} 과
     * 같은 60/100 표에 넣으므로 가연 판정도 한 술어로 묶는다.
     */
    private static boolean isMossCarpet(int block) {
        return block == Blocks.MOSS_CARPET || block == Blocks.PALE_MOSS_CARPET;
    }

    /** 양털 16색 구간(561~576). 색은 연속 ID라 범위 하나로 판정한다. */
    private static boolean isWoolBlock(int block) {
        return block >= Blocks.WHITE_WOOL && block <= Blocks.BLACK_WOOL;
    }

    /** 카펫 16색 구간(577~592). */
    private static boolean isCarpetBlock(int block) {
        return block >= Blocks.WHITE_CARPET && block <= Blocks.BLACK_CARPET;
    }

    private static int burnOdds(int block) {
        if (isWoodLeaves(block)) return 60;
        if (isWoolBlock(block) || (Blocks.isWoolSlab(block) || Blocks.isWoolStairs(block))) return 60;
        if (isCarpetBlock(block)) return 20;
        if (Blocks.isShelf(block)) return 20;
        if (Blocks.isShelfMushroom(block)) return 100;
        if (block == Blocks.SUNFLOWER ||
                block == Blocks.LILAC ||
                block == Blocks.ROSE_BUSH ||
                block == Blocks.PEONY ||
                block == Blocks.PITCHER_PLANT ||
                block == Blocks.TALL_GRASS || block == Blocks.FLOWER_RED
                || block == Blocks.FLOWER_YELLOW || block == Blocks.DEAD_BUSH
                || block == Blocks.CACTUS_FLOWER
                || block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT
                || isAnySapling(block)
                || block == Blocks.VINE || isMossCarpet(block)) return 100;
        if (block == Blocks.BAMBOO) return 60;
        if (block == Blocks.TNT) return 100;
        if (Blocks.isPlankBlock(block) || Blocks.isWoodSlab(block)
                || Blocks.isWoodStairs(block) || Blocks.isFence(block)
                || Blocks.isFenceGate(block) || block == Blocks.BOOKSHELF
                || block == Blocks.HAY_BLOCK || block == Blocks.COMPOSTER
                || block == Blocks.LECTERN
                || Blocks.isPoplarFlammableProduct(block)) return 20;
        if (block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA) return 60;
        return isWoodLog(block) ? 5 : 0;
    }

    /** Focused Java/standalone parity fixture: high byte ignite odds, low byte burn odds. */
    static int fireOddsForTest(int block) {
        return igniteOdds(block) << 8 | burnOdds(block);
    }

    private static boolean isWoodLog(int block) {
        return BlockFamilies.isWoodLog(block);
    }

    private static boolean isAnySapling(int block) {
        return block == Blocks.OAK_SAPLING || block == Blocks.BIRCH_SAPLING
                || block == Blocks.MANGROVE_PROPAGULE || P6Rules.isSapling(block)
                // [PALE-GARDEN] 창백한 참나무 묘목(1504)은 기존 묘목 구간(387~391) 밖이라
                // P6Rules.isSapling 이 잡지 못한다 — 가연 60/100 을 받으려면 여기 있어야 한다.
                || block == Blocks.PALE_OAK_SAPLING
                // [POPLAR] 포플러 묘목(1539)도 같은 이유로 여기 있어야 한다.
                || block == Blocks.POPLAR_SAPLING;
    }

    private static boolean isWoodLeaves(int block) {
        return BlockFamilies.isLeaves(block);
    }

    boolean isBurning(int x, int y, int z) { return burning.containsKey(new BlockPos(x, y, z)); }
    int burningCount() { return burning.size(); }
    FireSnapshot fireSnapshot() { return publishedFire; }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) { return (int) (key >> 32); }
    private static int chunkZ(long key) { return (int) key; }
}
