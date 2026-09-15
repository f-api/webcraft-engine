package com.gameexpert.engine.mob;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.WorldClock;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry.SpawnCategory;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry.SpawnEntry;

/**
 * 몹 스폰/디스폰 규칙 엔진. 스포너 쿨다운을 월드별로 유지한다.
 *
 * <p>자연 적대 스폰은 매 10TPS 서버 틱에 raw sky/local 밝기 확률, 유효 바닥, 빈 몸체 공간,
 * 플레이어로부터 24 초과 128 이하의 3D 거리를 검사한다. Java의 per-category mobcap을
 * 활성 17×17 spawnable-chunk 면적에 비례해 적용한다. 서버는 10TPS이므로 틱당 두 번의
 * 20TPS 스폰 패스를 실행하며, 종과 무리 크기는 pinned 26.3-snapshot-7
 * 바이옴 레지스트리에서 선택한다.
 *
 * <p>스포너 블록은 자연 cap과 무관하며 생성 시 확정된 state로 소환 종을 결정한다.
 */
public final class MobSpawner {
    static final double NATURAL_MIN_DIST = 24.0;
    static final double NATURAL_MAX_DIST = 128.0;
    /** Java Edition per-category 자연 스폰 캡(1.21.x). */
    static final int MC_MONSTER_CAP = 70;
    static final int MC_CREATURE_CAP = 10;
    static final int MC_AMBIENT_CAP = 15;
    static final int MC_WATER_CREATURE_CAP = 5;
    static final int MC_WATER_AMBIENT_CAP = 20;
    static final int MC_UNDERGROUND_WATER_CREATURE_CAP = 5;
    public static final int WORLD_MOB_CAP = MC_MONSTER_CAP;
    public static final int WORLD_ANIMAL_CAP = MC_CREATURE_CAP;
    public static final int WORLD_AMBIENT_CAP = MC_AMBIENT_CAP;
    public static final int WORLD_WATER_CREATURE_CAP = MC_WATER_CREATURE_CAP;
    public static final int WORLD_WATER_AMBIENT_CAP = MC_WATER_AMBIENT_CAP;
    public static final int WORLD_UNDERGROUND_WATER_CREATURE_CAP = MC_UNDERGROUND_WATER_CREATURE_CAP;
    public static final int WORLD_AXOLOTL_CAP = 5;
    static final int VANILLA_SPAWNABLE_CHUNK_AREA = 17 * 17;
    public static final int SPAWNER_INITIAL_DELAY = 20;
    public static final int SPAWNER_MIN_DELAY = 200;
    public static final int SPAWNER_MAX_DELAY = 799;
    public static final double IMMEDIATE_DESPAWN_DIST = 128.0;
    static final double WATER_AMBIENT_IMMEDIATE_DESPAWN_DIST = 64.0;
    public static final double RANDOM_DESPAWN_DIST = 32.0;
    public static final int RANDOM_DESPAWN_DELAY = 600;
    public static final int RANDOM_DESPAWN_ROLL = 800;

    static final int NATURAL_PACK_ATTEMPTS = 3;
    static final int VIRTUAL_PASSES_PER_SERVER_TICK = 2;
    static final int CREATURE_SPAWN_INTERVAL = 400;
    /** 매 10TPS 자연 스폰 틱의 지표 순찰대 시도 확률. 기대 간격 600초(10분). */
    static final int PILLAGER_PATROL_ROLL = 6_000;
    static final int PILLAGER_PATROL_MIN = 2;
    static final int PILLAGER_PATROL_MAX = 3;
    /**
     * 좀비곰 배회(WebCraft 창작몹) 시도 확률. 매 10TPS 자연 스폰 틱 중 <b>밤이고 적대 캡에
     * 여유가 있는</b> 틱에서만 굴리며, 기대 간격은 36,000 유효 틱 = 3,600초다.
     *
     * <p>가중치 표를 쓰지 않는 이유가 계약의 핵심이다 — 바이옴 표에 항목을 더하면 분모가
     * 커져 기존 모든 종의 절대 확률이 바뀐다. 순찰대와 같은 <b>독립 굴림</b>이면 표는 글자
     * 그대로 그대로이고, 기존 종의 상대·절대 확률이 전부 불변이다
     * ({@code MobSpawnerZombieBearTest} 가 이를 감사한다).
     *
     * <p><b>기대 조우율(문서화 계약).</b> WebCraft 하루는 {@code WorldClock.DAY_LENGTH}
     * 12,000틱 = 실시간 1,200초이고 밤은 {@code NIGHT_START} 6,500 ~ {@code NIGHT_END}
     * 11,500 = 5,000틱(41.7%)이다. 기대 36,000 유효 틱 = 밤 7.2번 = 실시간
     * <b>8,640초(144분)</b>에 한 번 굴림이 성공한다. 성공해도 (1) 뽑은 방위의 기둥이 활성 청크여야 하고, (2) 그 지점의 바이옴이
     * {@link #brownBearBiome} 목록(숲/타이가 계열 7종)이어야 하며, (3) 어두운 유효 적대
     * 스폰 지점이어야 한다. 숲 한복판에서만 놀아도 (1)(3) 합쳐 대략 절반이 떨어지므로,
     * <b>숲 바이옴 상주 기준 실시간 4~5시간에 1마리</b>가 설계 기대값이다. 숲 밖에서는
     * 바이옴 조건이 0이라 자연 스폰으로는 만나지 못한다.
     */
    static final int ZOMBIE_BEAR_PROWL_ROLL = 36_000;
    /** 좀비곰은 무리를 이루지 않는다. 한 번의 성공에 한 마리다. */
    static final int ZOMBIE_BEAR_PROWL_COUNT = 1;
    /** 배회 후보 좌표의 최소 거리(블록). 자연 스폰 안전 반경 24 밖이다. */
    static final int ZOMBIE_BEAR_PROWL_MIN_DISTANCE = 32;
    /** 배회 후보 좌표의 거리 굴림 폭(배타). 32 + nextInt(97) → 32~128 블록. */
    static final int ZOMBIE_BEAR_PROWL_DISTANCE_SPAN = 97;
    /**
     * 좀비 늑대 무리(WebCraft 창작몹) 시도 확률. 좀비곰과 <b>같은 자릿수</b>인 24,000 유효 틱
     * = 2,400초다. 좀비곰보다 잦은 이유는 개체가 약하기 때문이다 — 체력 12·타격 4 라 한 마리는
     * 사건이 되지 못하고, 무리(2~3)로 나와야 위협이 성립한다.
     *
     * <p>기대 실시간 <b>5,760초(96분)</b>에 한 번 굴림이 성공하며, 이후 조건은 좀비곰과 같다:
     * (1) 활성 청크 기둥, (2) {@link #zombieWolfBiome} 목록(숲/타이가 계열 7종), (3) 어두운
     * 유효 적대 스폰 지점. <b>숲/타이가 상주 기준 실시간 2~3시간에 한 무리</b>가 설계 기대값이다.
     */
    static final int ZOMBIE_WOLF_PACK_ROLL = 24_000;
    /** 무리 최소 마릿수. 순찰대와 같은 형태의 닫힌 구간이다. */
    static final int ZOMBIE_WOLF_PACK_MIN = 2;
    /** 무리 최대 마릿수. */
    static final int ZOMBIE_WOLF_PACK_MAX = 3;
    /** 무리 후보 좌표의 최소 거리(블록). */
    static final int ZOMBIE_WOLF_PACK_MIN_DISTANCE = 32;
    /** 무리 후보 좌표의 거리 굴림 폭(배타). 32 + nextInt(97) → 32~128 블록. */
    static final int ZOMBIE_WOLF_PACK_DISTANCE_SPAN = 97;

    // ── [ZOMBIE-ANIMAL] 좀비 동물 10종의 티어 굴림 ────────────────────────────────
    // 전부 좀비곰 배회와 **같은 형태의 독립 굴림**이다 — 바이옴 가중치 표를 읽지도 쓰지도
    // 않으므로 기존 종의 상대·절대 확률이 한 톨도 바뀌지 않는다(`MobSpawnerZombieAnimalTest`
    // 가 전수 감사한다). 굴림 주기는 임의로 고른 값이 아니라 **썩은 가죽 100시간 경제에서
    // 역산한 값**이며, 유도 과정은 docs/MC-REFERENCE.md 「썩은 가죽 100시간 경제」 절이
    // 소유하고 시드 고정 시뮬레이션 `RottenLeatherEconomy.test.ts` 가 구간을 단언한다.
    //
    // 공통 환산: 밤은 하루 12,000틱 중 5,000틱이고 하루는 실시간 1,200초이므로
    // **유효 틱 1개 = 실시간 0.24초**다. 굴림 상한 R 이면 기대 성공 간격은 0.24R 초,
    // 시간당 기대 성공은 15,000/R 회다.
    /**
     * 흔한 티어 — 좀비 소·돼지·양이 섞인 <b>혼성 무리</b>. 5,000 유효 틱 = 실시간 1,200초라
     * 기대 <b>밤 한 번에 한 무리</b>다(시간당 3회). 좀비 동물 경제의 기둥이며, 나머지 아홉
     * 굴림을 다 합쳐도 이 하나가 내놓는 가죽의 4분의 1이 안 된다.
     *
     * <p>한 번의 성공이 무리 <b>구성까지</b> 추첨한다 — 마릿수를 뽑고, 자리마다 종을 따로
     * 뽑는다. 종을 미리 정해 두면 "소 무리"·"양 무리"가 되어 밤 목장의 뒤섞인 인상이 죽는다.
     */
    static final int ZOMBIE_HERD_PACK_ROLL = 5_000;
    /** 무리 최소 마릿수. */
    static final int ZOMBIE_HERD_PACK_MIN = 2;
    /** 무리 최대 마릿수. 기대 3마리다. */
    static final int ZOMBIE_HERD_PACK_MAX = 4;
    /** 무리 후보 좌표의 최소 거리(블록). 좀비곰·늑대와 같다. */
    static final int ZOMBIE_HERD_PACK_MIN_DISTANCE = 32;
    /** 거리 굴림 폭(배타). 32 + nextInt(97) → 32~128 블록. */
    static final int ZOMBIE_HERD_PACK_DISTANCE_SPAN = 97;
    /** 무리 구성 추첨의 배타 상한. 0=소 · 1=돼지 · 2=양 균등이다. */
    static final int ZOMBIE_HERD_SPECIES_ROLL = 3;
    /**
     * 중간 티어(산악) — 좀비 염소. 30,000 유효 틱 = 실시간 7,200초(2시간)다.
     * 흔한 티어의 6분의 1이며, 산악 바이옴에 있어야만 만난다.
     */
    static final int ZOMBIE_GOAT_PROWL_ROLL = 30_000;
    /**
     * 중간 티어(사막) — 낙타 husk. 좀비 염소와 <b>같은 주기</b>다(30,000 유효 틱). 체급이
     * 대형이라 한 마리가 내놓는 가죽은 염소의 두 배지만, 사막 상주는 산악 상주보다 드물어
     * 실제 수확은 비슷해진다. <b>CREATURE 캡</b>을 본다.
     */
    static final int CAMEL_HUSK_ENCOUNTER_ROLL = 30_000;
    /**
     * 희귀 재미 티어 — 좀비 여우. 40,000 유효 틱 = 실시간 9,600초(2시간 40분)다.
     * 혼자서 끝까지 따라붙는 종이라 "사건"으로 성립할 만큼만 드물게 둔다.
     */
    static final int ZOMBIE_FOX_PROWL_ROLL = 40_000;
    /** 희귀 재미 티어 — 좀비 닭. 좀비 여우와 같은 주기이며 상한 달걀의 유일한 출처다. */
    static final int ZOMBIE_CHICKEN_PROWL_ROLL = 40_000;
    /**
     * 창작 희귀 티어 — 무덤 사슴. 60,000 유효 틱 = 실시간 14,400초(4시간)다.
     * 도망가는 종이라 조우 자체가 사건이어야 한다.
     */
    static final int CARRION_STAG_PROWL_ROLL = 60_000;
    /** 창작 희귀 티어 — 부패 멧돼지. 무덤 사슴과 같은 주기다. */
    static final int CARRION_BOAR_PROWL_ROLL = 60_000;
    /**
     * [WAVE-86-97] 행상인 도착 — 바닐라 {@code WanderingTraderSpawner} 의 주기 굴림.
     * 24,000 MC 틱마다 창이 열리므로 서버 10TPS 로는 12,000 서버 틱이다.
     */
    static final int WANDERING_TRADER_INTERVAL_TICKS =
            WanderingTrader.SPAWN_DELAY_TICKS / VIRTUAL_PASSES_PER_SERVER_TICK;
    /** 아직 세운 적이 없는 행상인 창(영속 값이 없는 월드). */
    public static final long TRADER_WINDOW_UNSET = Long.MIN_VALUE;
    /** 창작 희귀·중간 티어가 공유하는 후보 좌표 최소 거리(블록). */
    static final int ZOMBIE_ANIMAL_MIN_DISTANCE = 32;
    /** 같은 거리 굴림 폭(배타). 32 + nextInt(97) → 32~128 블록. */
    static final int ZOMBIE_ANIMAL_DISTANCE_SPAN = 97;
    // ── [PHANTOM] 불면 스폰(바닐라 PhantomSpawner 원문 · mc-phantom-insomnia.md §1) ──
    /** {@code nextTick += (60 + nextInt(60)) * 20} game tick 의 하한 60초를 10 TPS 로 옮긴 값. */
    static final int PHANTOM_ATTEMPT_MIN_TICKS = 600;
    /** 같은 식의 {@code nextInt(60)}(초) 폭. 10 TPS 라 뽑은 초에 10 을 곱한다. */
    static final int PHANTOM_ATTEMPT_SPAN_SECONDS = 60;
    /** 10 TPS 환산 배율(초 × 10). */
    static final int PHANTOM_ATTEMPT_TICKS_PER_SECOND = 10;
    /** {@code above(20 + nextInt(15))} 의 하한. */
    static final int PHANTOM_SPAWN_ABOVE_MIN = 20;
    /** 같은 식의 {@code nextInt(15)} 폭. */
    static final int PHANTOM_SPAWN_ABOVE_SPAN = 15;
    /** {@code east(-10 + nextInt(21)).south(-10 + nextInt(21))} 의 하한과 폭. */
    static final int PHANTOM_SPAWN_SIDE_MIN = -10;
    static final int PHANTOM_SPAWN_SIDE_SPAN = 21;
    /** {@code isHarderThan(nextFloat() * 3.0F)} 의 배율. */
    static final float PHANTOM_DIFFICULTY_ROLL_SCALE = 3.0f;

    static final int SPAWNER_LOCAL_CAP = 6;
    static final int SPAWNER_SPAWN_COUNT = 4;
    static final int SPAWNER_SPAWN_RANGE = 4;
    /** 던전 좀비 변종: 새끼 5%. */
    static final int DUNGEON_BABY_ZOMBIE_PERCENT = 5;

    static final short GRASS = 3;
    private static final double TWO_PI = Math.PI * 2;

    /** 서버 어댑터가 알려진 스포너 좌표를 제공하는 선택적 인덱스. */
    public interface SpawnerScan {
        /** 생존 플레이어 근처의 후보 스포너 절대 좌표 목록({x,y,z}). */
        List<int[]> spawnerBlocks(List<PlayerSnapshot> players);

        /** A refreshed resident snapshot confirmed these previously indexed coordinates are gone. */
        default List<int[]> removedSpawnerBlocks() { return List.of(); }
    }

    private final SpawnerScan spawnerScan;
    private final Map<SpawnerPosition, Integer> spawnerRemainingTicks = new LinkedHashMap<>();
    private final Map<SpawnerPosition, Long> spawnerDefinitions = new LinkedHashMap<>();
    private long[] spawnableChunkScratch = new long[VANILLA_SPAWNABLE_CHUNK_AREA];
    private final Set<Long> spawnableChunkSeen = new LinkedHashSet<>();
    /** Tick-owner scratch shared by spawn and despawn passes; never escapes either call. */
    private final List<PlayerSnapshot> alivePlayerScratch = new ArrayList<>(4);
    private final LocalCapIndex localCapIndex = new LocalCapIndex();
    /**
     * [WAVE-86-97] 행상인 도착 확률 계단(%). 바닐라 {@code WanderingTraderSpawner.spawnChance}
     * 와 같은 자리의 상태이며, 바닐라와 같이 <b>월드에 영속</b>한다
     * ({@code wanderingTraderSpawnChance}). 어댑터가 {@link #restoreTraderWindow} 로 넣고
     * {@link #traderWindowState()} 로 가져간다.
     */
    private int traderChancePercent = WanderingTrader.SPAWN_CHANCE_MIN_PERCENT;
    /**
     * [WAVE-86-97] 다음 굴림이 열리는 <b>영속 시계</b> 값({@link #persistentSpawnTick}).
     * 바닐라 {@code wanderingTraderSpawnDelay} 와 같은 자리다. {@link #TRADER_WINDOW_UNSET}
     * 은 "이 월드에서 아직 창을 세운 적이 없다"는 뜻이라 첫 틱에 하루 뒤로 세운다.
     */
    private long traderNextAttemptTick = TRADER_WINDOW_UNSET;
    /**
     * [PHANTOM] 다음 불면 시도까지 남은 서버 틱. 바닐라 {@code PhantomSpawner.nextTick} 과 같은
     * 자리이며 <b>영속하지 않는다</b> — 바닐라도 {@code PhantomSpawner} 를 서버 기동마다 새로
     * 만들어 0 에서 시작한다(docs/research/mc-phantom-insomnia.md §1). 0 이하면 이번 틱에 시도한다.
     */
    private int phantomNextTick;
    /** 특수 스폰 전 전역 cap을 한 번의 mob 순회로 계산하는 재사용 버퍼. */
    private final int[] globalCategoryScratch = new int[MobCategory.values().length];

    public MobSpawner() {
        this(players -> List.of());
    }

    /** 스포너 인덱스 주입 생성자. */
    public MobSpawner(SpawnerScan spawnerScan) {
        this.spawnerScan = spawnerScan;
    }

    /** 좀비 던전의 새끼 변종 매핑. */
    static MobType pickZombieDungeonType(MobRandom rng) {
        int roll = rng.nextInt(100);
        if (roll >= 100 - DUNGEON_BABY_ZOMBIE_PERCENT) {
            return MobType.BABY_ZOMBIE;
        }
        return MobType.ZOMBIE;
    }

    /**
     * 청크 생성 시 동물 무리 배치(§39 vanilla 1.21.4 바이옴별 creature spawn probability).
     * 좌표 결정론 RNG로 확률이 이어지는 동안 바이옴 가중 무리를 잔디 지표에 놓는다.
     * 생성 시점 배치라 플레이어 거리·월드 캡은 생략하지만 종별 바닥·밝기 규칙은 적용한다.
     * 종과 무리 크기는 현재 좌표의 pinned biome spawner table에서 그대로 선택한다.
     */
    public List<SpawnRequest> populateChunk(MobWorldView world, int chunkX, int chunkZ,
                                                   MobRandom rng) {
        // 마을 철 골렘은 여기서 놓지 않는다. 청크 위치 해시(StructureSiteDescriptor.E lane)는
        // 마을 후보일 뿐이고 실제 배치 여부는 VillageGenerator 만 안다. 골렘은 생성기가 블록을
        // 실제로 놓아 사이트가 채택됐을 때만 MobSystem 의 구조물 점유 명단으로 나온다.
        List<SpawnRequest> out = new ArrayList<>();
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        McRandom rabbitRegionRandom = new McRandom(world.worldSeed()).forkPositional()
                .fromHashOf("minecraft:worldgen_region_random")
                .forkPositional().at(minX, 0, minZ);
        int biome = fuzzyBiomeAt(world, minX, Blocks.MAX_Y, minZ);
        McBiomeRegistry.Biome biomeSettings = McBiomeRegistry.get(biome);
        float probability = biomeSettings.creatureSpawnProbability();
        while (rng.nextFloat() < probability) {
            SpawnChoice entry = weightedSpawnChoice(biome, SpawnCategory.CREATURE, rng);
            if (entry == null) continue;
            MobType type = entry.type();
            if (type == null || type.category() != MobCategory.CREATURE) continue;
            int groupSize = entry.minCount()
                    + rng.nextInt(entry.maxCount() - entry.minCount() + 1);
            int x = minX + rng.nextInt(16);
            int z = minZ + rng.nextInt(16);
            int anchorX = x;
            int anchorZ = z;
            boolean firstSuccessfulCow = type == MobType.COW;
            String groupRabbitVariant = null;
            for (int member = 0; member < groupSize; member++) {
                boolean spawned = false;
                for (int attempt = 0; !spawned && attempt < 4; attempt++) {
                    int feetY = world.motionBlockingNoLeavesHeight(x, z) + 1;
                    double spawnX = clamp(x, minX + type.width(), minX + 16.0 - type.width());
                    double spawnZ = clamp(z, minZ + type.width(), minZ + 16.0 - type.width());
                    int blockX = (int) Math.floor(spawnX);
                    int blockZ = (int) Math.floor(spawnZ);
                    if (hasActiveColumn(world, blockX, blockZ)
                            && validPopulationSpot(world, blockX, feetY, blockZ,
                                    spawnX, spawnZ, type)) {
                        rng.nextFloat(); // protocol에 없는 yaw도 decoration RNG 순서를 보존한다.
                        String variant = null;
                        if (type == MobType.RABBIT) {
                            int roll = rabbitRegionRandom.nextInt(100);
                            if (groupRabbitVariant == null) {
                                groupRabbitVariant = rabbitVariant(
                                        world.biomeAt(blockX, feetY, blockZ), roll);
                            }
                            variant = groupRabbitVariant;
                        } else if (type == MobType.COW || type == MobType.PIG
                                || type == MobType.CHICKEN) {
                            // [FARM-VARIANT] 자연 스폰과 같은 표. 청크 생성 배치는 좌표별
                            // 바이옴을 그대로 읽는다(토끼와 같은 경계).
                            // Dappled forest is raw biome 187 and therefore uses this same tag table.
                            variant = FarmAnimalVariantRules.climateForBiome(
                                    world.biomeAt(blockX, feetY, blockZ));
                        } else if (type == MobType.FOX) {
                            variant = FarmAnimalVariantRules.foxVariantForBiome(
                                    world.biomeAt(blockX, feetY, blockZ));
                        } else if (type == MobType.FROG) {
                            variant = MobRuntime.frogVariantForBiome(
                                    world.biomeAt(blockX, feetY, blockZ));
                        } else if (type == MobType.HORSE) {
                            variant = type.variantAt(rng.nextInt(type.variantCount()));
                        }
                        out.add(new SpawnRequest(type, spawnX, feetY, spawnZ,
                                type == MobType.COW && firstSuccessfulCow,
                                variant));
                        if (type == MobType.COW) firstSuccessfulCow = false;
                        spawned = true;
                    }
                    x += rng.nextInt(5) - rng.nextInt(5);
                    z += rng.nextInt(5) - rng.nextInt(5);
                    while (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
                        x = anchorX + rng.nextInt(5) - rng.nextInt(5);
                        z = anchorZ + rng.nextInt(5) - rng.nextInt(5);
                    }
                }
            }
        }
        return out;
    }

    /** Vanilla {@code BiomeManager#getBiome}: fuzzy zoom 후 raw quart 바이옴을 한 번만 읽는다. */
    static int fuzzyBiomeAt(MobWorldView world, int blockX, int blockY, int blockZ) {
        int shiftedX = blockX - 2;
        int shiftedY = blockY - 2;
        int shiftedZ = blockZ - 2;
        int quartX = shiftedX >> 2;
        int quartY = shiftedY >> 2;
        int quartZ = shiftedZ >> 2;
        int corner = populationBiomeCorner(world.biomeZoomSeed(), blockX, blockY, blockZ);
        if ((corner & 4) != 0) quartX++;
        if ((corner & 2) != 0) quartY++;
        if ((corner & 1) != 0) quartZ++;
        return world.noiseBiomeAtQuart(quartX, quartY, quartZ);
    }

    static int populationBiomeCorner(long zoomSeed, int blockX, int blockY, int blockZ) {
        int shiftedX = blockX - 2;
        int shiftedY = blockY - 2;
        int shiftedZ = blockZ - 2;
        int quartX = shiftedX >> 2;
        int quartY = shiftedY >> 2;
        int quartZ = shiftedZ >> 2;
        double offsetX = (shiftedX & 3) / 4.0;
        double offsetY = (shiftedY & 3) / 4.0;
        double offsetZ = (shiftedZ & 3) / 4.0;
        int bestCorner = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            boolean lowX = (corner & 4) == 0;
            boolean lowY = (corner & 2) == 0;
            boolean lowZ = (corner & 1) == 0;
            double distance = fiddledDistance(zoomSeed,
                    lowX ? quartX : quartX + 1,
                    lowY ? quartY : quartY + 1,
                    lowZ ? quartZ : quartZ + 1,
                    lowX ? offsetX : offsetX - 1.0,
                    lowY ? offsetY : offsetY - 1.0,
                    lowZ ? offsetZ : offsetZ - 1.0);
            if (bestDistance > distance) {
                bestCorner = corner;
                bestDistance = distance;
            }
        }
        return bestCorner;
    }

    public static long biomeZoomSeed(long worldSeed) {
        byte[] input = new byte[Long.BYTES];
        for (int i = 0; i < input.length; i++) input[i] = (byte) (worldSeed >>> (i * 8));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            long result = 0;
            for (int i = Long.BYTES - 1; i >= 0; i--) result = result << 8 | hash[i] & 0xffL;
            return result;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static long decorationSeed(long worldSeed, int minX, int minZ) {
        Random seedRandom = new Random(worldSeed);
        long xMultiplier = seedRandom.nextLong() | 1L;
        long zMultiplier = seedRandom.nextLong() | 1L;
        return (long) minX * xMultiplier + (long) minZ * zMultiplier ^ worldSeed;
    }

    private static double fiddledDistance(long seed, int x, int y, int z,
                                           double offsetX, double offsetY, double offsetZ) {
        long mixed = lcgNext(seed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        mixed = lcgNext(mixed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        double fiddleX = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double fiddleY = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double fiddleZ = fiddle(mixed);
        return sq(offsetX + fiddleX) + sq(offsetY + fiddleY) + sq(offsetZ + fiddleZ);
    }

    private static long lcgNext(long state, long salt) {
        return state * (state * 6_364_136_223_846_793_005L
                + 1_442_695_040_888_963_407L) + salt;
    }

    private static double fiddle(long state) {
        return (Math.floorMod(state >> 24, 1024L) / 1024.0 - 0.5) * 0.9;
    }

    /**
     * Pinned 1.21.4 내부 pack 순서를 유지한다. 원점에서 provisional 크기를 뽑고
     * 이동한 첫 유효 후보에서만 biome entry·실제 group 크기를 지연 확정한다.
     * 미등록 vanilla 종도 추첨 분모에는 남으므로 지원 종의 빈도가 조용히 부풀지 않는다.
     */
    private void tryNaturalCategory(MobWorldView world, MobRandom rng, Collection<Mob> mobs,
                                    List<PlayerSnapshot> players, List<SpawnRequest> out,
                                    long[] spawnableChunks, int spawnableChunkCount,
                                    MobCategory category,
                                    SpawnCategory biomeCategory, int fullCap) {
        LocalCapIndex localCaps = localCapIndex;
        if (localCaps.playerCount != players.size()) {
            localCaps.reset(mobs, out, players);
        }
        int cap = scaledCap(fullCap, spawnableChunkCount);
        int count = localCaps.globalCount(category);
        if (count >= cap) return;

        for (int chunkIndex = 0; chunkIndex < spawnableChunkCount && count < cap; chunkIndex++) {
            long chunk = spawnableChunks[chunkIndex];
            int originX = (int) (chunk >> 32) * 16 + rng.nextInt(16);
            int originZ = (int) chunk * 16 + rng.nextInt(16);
            int surface = world.worldSurfaceHeight(originX, originZ);
            int minY = world.endDimension() ? 0 : Blocks.MIN_Y;
            int originY = minY + rng.nextInt(Math.max(1, surface - minY + 2));
            short originBlock = world.getBlock(originX, originY, originZ);
            if (originBlock < 0) continue;
            int originId = originBlock & 0xffff;
            if (BuildingBlockRules.isRedstoneConductor(originId,
                    world.blockState(originX, originY, originZ, originId))) continue;

            for (int packAttempt = 0;
                    packAttempt < NATURAL_PACK_ATTEMPTS && count < cap; packAttempt++) {
                int wanted = provisionalPackCount(rng.nextFloat());
                int x = originX, y = originY, z = originZ;
                int biome = 0;
                SpawnChoice entry = null;
                MobType selectedType = null;
                boolean firstSuccessfulCow = false;
                Boolean groupBabyZombie = null;
                String groupRabbitVariant = null;
                String groupTropicalFishVariant = null;
                String[] groupAxolotlVariants = null;
                for (int member = 0; member < wanted && count < cap; member++) {
                    x += rng.nextInt(6) - rng.nextInt(6);
                    z += rng.nextInt(6) - rng.nextInt(6);
                    double spawnX = x + 0.5;
                    double spawnZ = z + 0.5;
                    if (!validNaturalDistance(players, spawnX, y, spawnZ)
                            || !outsideWorldSpawn(world, spawnX, y, spawnZ)) continue;
                    boolean selectedHere = false;
                    if (entry == null) {
                        biome = world.biomeAt(x, y, z);
                        entry = naturalSpawnChoice(world, x, y, z, biome, biomeCategory, rng);
                        if (entry == null) break;
                        wanted = entry.minCount()
                                + rng.nextInt(entry.maxCount() - entry.minCount() + 1);
                        selectedType = entry.type();
                        // NaturalSpawner does not require the selected entity type's category to
                        // equal the biome-list category. 26.3 intentionally lists ocelot under
                        // jungle MONSTER; afterSpawn accounts it to its actual CREATURE category.
                        if (selectedType == null) break;
                        firstSuccessfulCow = selectedType == MobType.COW;
                        selectedHere = true;
                    }
                    if (!selectedHere && !world.endDimension() && !sameNaturalSpawnList(
                            biome, world.biomeAt(x, y, z))) continue;
                    if (!validSpawnPlacement(world, selectedType, x, y, z, rng)) continue;
                    if (!localCaps.allows(category, fullCap, spawnX, spawnZ)) continue;
                    rng.nextFloat(); // protocol에 없는 yaw도 shared spawn RNG 순서를 보존한다.
                    MobType type = selectedType;
                    if (selectedType == MobType.ZOMBIE) {
                        if (groupBabyZombie == null) groupBabyZombie = rng.nextFloat() < 0.05f;
                        if (groupBabyZombie) type = MobType.BABY_ZOMBIE;
                    }
                    String variant;
                    if (type == MobType.RABBIT) {
                        int roll = rng.nextInt(100);
                        if (groupRabbitVariant == null) {
                            groupRabbitVariant = rabbitVariant(biome, roll);
                        }
                        variant = groupRabbitVariant;
                    } else if (type == MobType.TROPICAL_FISH) {
                        if (groupTropicalFishVariant == null) {
                            groupTropicalFishVariant = commonTropicalFishVariant(rng);
                        }
                        variant = groupTropicalFishVariant;
                    } else if (type == MobType.AXOLOTL) {
                        if (groupAxolotlVariants == null) {
                            groupAxolotlVariants = new String[] {
                                commonAxolotlVariant(rng), commonAxolotlVariant(rng),
                            };
                        }
                        variant = groupAxolotlVariants[rng.nextInt(2)];
                    } else if (type == MobType.COW || type == MobType.PIG
                            || type == MobType.CHICKEN) {
                        // [FARM-VARIANT] 1.21.5 기후 변종. 바이옴이 정본이라 RNG 를 쓰지 않고,
                        // 소는 여기서 **기후 낱말만** 실어 보낸다(성별은 MobFactory 의 기존
                        // 결정론 가중표가 그대로 뽑는다). 무리 전원이 같은 스폰 목록을 공유하는
                        // 그룹 바이옴을 쓰는 것은 토끼와 같은 계약이다.
                        // Dappled forest is raw biome 187 in the same authoritative tag table.
                        variant = FarmAnimalVariantRules.climateForBiome(biome);
                    } else if (type == MobType.FOX) {
                        // [FARM-VARIANT] 눈 여우는 grove·snowy_taiga 에서만 난다
                        // (`BiomeTags.SPAWNS_SNOW_FOXES`). 그 전에는 좌표 해시가 50:50 으로
                        // 뽑아 정글 한복판에도 흰여우가 섰다 — 이 분기가 그 결손을 닫는다.
                        variant = FarmAnimalVariantRules.foxVariantForBiome(biome);
                    } else if (type == MobType.FROG) {
                        // [FARM-VARIANT] 개구리 기후 표는 이미 있었지만 **올챙이 변태 경로에만**
                        // 걸려 있었고 자연 스폰은 좌표 해시로 3등분되고 있었다.
                        variant = MobRuntime.frogVariantForBiome(biome);
                    } else if (type == MobType.HORSE) {
                        variant = type.variantAt(rng.nextInt(type.variantCount()));
                    } else {
                        variant = null;
                    }
                    out.add(type == MobType.DROWNED
                            ? SpawnRequest.naturalDrowned(spawnX, y, spawnZ, biome)
                            : type == MobType.ZOMBIE_HORSE
                            ? SpawnRequest.naturalZombieHorse(spawnX, y, spawnZ)
                            : new SpawnRequest(type, spawnX, y, spawnZ,
                                    type == MobType.COW && firstSuccessfulCow, variant));
                    localCaps.add(type.category(), spawnX, spawnZ);
                    if (type == MobType.COW) firstSuccessfulCow = false;
                    if (type.category() == category) count++;
                }
            }
        }
    }

    static SpawnEntry naturalSpawnEntry(int biome, SpawnCategory category, MobRandom rng) {
        if (category == SpawnCategory.WATER_AMBIENT && (biome == 7 || biome == 11)
                && rng.nextFloat() < 0.98f) return null;
        return weightedEntry(McBiomeRegistry.get(biome).spawns(category), rng);
    }

    /**
     * 바닐라 {@code NaturalSpawner#getRandomSpawnMobAt} 의 순서를 그대로 지킨다 —
     * 강 water_ambient 98% 감축이 먼저이고, 그 다음이 {@code ChunkGenerator#getMobsAt} 다.
     * {@code getMobsAt} 은 좌표를 감싸는 구조물의 {@code spawn_overrides} 가 그 카테고리를
     * 들고 있으면 바이옴 표 대신 그 표를 굴린다(해저 신전 = {@link MonumentSpawnOverride}).
     *
     * <p>난수 소비도 바닐라와 같다: 비어 있지 않은 표는 {@code WeightedList#getRandom} 이
     * 항상 {@code nextInt(totalWeight)} 를 한 번 쓰고(가디언 하나짜리 표도 {@code nextInt(1)}),
     * 빈 표({@code axolotls}·{@code underground_water_creature})는 한 번도 쓰지 않는다.
     */
    private static SpawnChoice naturalSpawnChoice(MobWorldView world, int x, int y, int z,
                                                   int biome, SpawnCategory category,
                                                   MobRandom rng) {
        if (world.endDimension()) {
            if (category != SpawnCategory.MONSTER) return null;
            rng.nextInt(10); // 26.3-snapshot-7: all five End biomes, enderman weight 10, group 4.
            return new SpawnChoice(MobType.ENDERMAN, 4, 4);
        }
        if (category == SpawnCategory.WATER_AMBIENT && (biome == 7 || biome == 11)
                && rng.nextFloat() < 0.98f) return null;
        if (MonumentSpawnOverride.overridesCategory(category)
                && MonumentSpawnOverride.within(world, x, y, z)) {
            int total = MonumentSpawnOverride.overrideWeightTotal(category);
            if (total == 0) return null;
            rng.nextInt(total);
            return new SpawnChoice(MonumentSpawnOverride.overrideType(category),
                    MonumentSpawnOverride.overrideMinCount(category),
                    MonumentSpawnOverride.overrideMaxCount(category));
        }
        return weightedSpawnChoice(biome, category, rng);
    }

    /**
     * Adds the project-original brown bear without mutating pinned vanilla biome data or
     * allocating a merged list on each natural-spawn attempt. Weight one and pack one make
     * it an uncommon member of temperate/boreal creature lists.
     */
    static SpawnChoice weightedSpawnChoice(int biome, SpawnCategory category,
                                           MobRandom rng) {
        List<SpawnEntry> entries = McBiomeRegistry.get(biome).spawns(category);
        int customWeight = category == SpawnCategory.CREATURE && brownBearBiome(biome) ? 1 : 0;
        int boarWeight = category == SpawnCategory.CREATURE && boarBiome(biome) ? 3 : 0;
        int grizzlyWeight = category == SpawnCategory.CREATURE && brownBearBiome(biome) ? 1 : 0;
        int total = customWeight + boarWeight + grizzlyWeight;
        for (SpawnEntry entry : entries) total += entry.weight();
        if (total == 0) return null;
        int selected = rng.nextInt(total);
        for (SpawnEntry entry : entries) {
            selected -= entry.weight();
            if (selected < 0) {
                MobType type = supportedType(entry.type());
                return type == null ? null
                        : new SpawnChoice(type, entry.minCount(), entry.maxCount());
            }
        }
        if (selected < customWeight) return new SpawnChoice(MobType.BROWN_BEAR, 1, 1);
        if (selected < customWeight + boarWeight) return new SpawnChoice(MobType.BOAR, 1, 3);
        return grizzlyWeight > 0 && rng.nextInt(8) == 0
                ? new SpawnChoice(MobType.GRIZZLY_BEAR, 1, 1) : null;
    }

    static boolean boarBiome(int biome) {
        return brownBearBiome(biome) || biome == 6;
    }

    static boolean brownBearBiome(int biome) {
        return biome == 4 || biome == 5 || biome == 29 || biome == 32
                || biome == 34 || biome == 160 || biome == 178;
    }

    record SpawnChoice(MobType type, int minCount, int maxCount) { }

    static boolean sameNaturalSpawnList(int selectedBiome, int candidateBiome) {
        return selectedBiome == candidateBiome;
    }

    private static SpawnEntry weightedEntry(List<SpawnEntry> entries, MobRandom rng) {
        int total = 0;
        for (SpawnEntry entry : entries) total += entry.weight();
        if (total == 0) return null;
        int selected = rng.nextInt(total);
        for (SpawnEntry entry : entries) {
            selected -= entry.weight();
            if (selected < 0) return entry;
        }
        return null;
    }

    static MobType supportedType(String namespacedType) {
        String type = namespacedType.substring("minecraft:".length());
        return switch (type) {
            case "spider" -> MobType.SPIDER;
            case "zombie" -> MobType.ZOMBIE;
            case "zombie_villager" -> MobType.ZOMBIE_VILLAGER;
            case "zombie_horse" -> MobType.ZOMBIE_HORSE;
            case "skeleton" -> MobType.SKELETON;
            case "creeper" -> MobType.CREEPER;
            case "slime" -> MobType.SLIME;
            case "enderman" -> MobType.ENDERMAN;
            case "witch" -> MobType.WITCH;
            case "drowned" -> MobType.DROWNED;
            case "husk" -> MobType.HUSK;
            case "stray" -> MobType.STRAY;
            case "bogged" -> MobType.BOGGED;
            case "parched" -> MobType.PARCHED;
            case "sulfur_cube" -> MobType.SULFUR_CUBE;
            case "cave_spider" -> MobType.CAVE_SPIDER;
            case "sheep" -> MobType.SHEEP;
            case "pig" -> MobType.PIG;
            case "chicken" -> MobType.CHICKEN;
            case "cow" -> MobType.COW;
            case "rabbit" -> MobType.RABBIT;
            case "bat" -> MobType.BAT;
            case "squid" -> MobType.SQUID;
            case "cod" -> MobType.COD;
            case "salmon" -> MobType.SALMON;
            case "tropical_fish" -> MobType.TROPICAL_FISH;
            case "glow_squid" -> MobType.GLOW_SQUID;
            case "axolotl" -> MobType.AXOLOTL;
            case "armadillo" -> MobType.ARMADILLO;
            case "dolphin" -> MobType.DOLPHIN;
            case "nautilus" -> MobType.NAUTILUS;
            case "camel" -> MobType.CAMEL;
            case "donkey" -> MobType.DONKEY;
            case "fox" -> MobType.FOX;
            case "frog" -> MobType.FROG;
            case "goat" -> MobType.GOAT;
            case "horse" -> MobType.HORSE;
            case "llama" -> MobType.LLAMA;
            case "mooshroom" -> MobType.MOOSHROOM;
            case "ocelot" -> MobType.OCELOT;
            case "panda" -> MobType.PANDA;
            case "parrot" -> MobType.PARROT;
            case "polar_bear" -> MobType.POLAR_BEAR;
            case "pufferfish" -> MobType.PUFFERFISH;
            case "turtle" -> MobType.TURTLE;
            case "wolf" -> MobType.WOLF;
            default -> null;
        };
    }

    boolean validSpawnSpot(MobWorldView world, List<PlayerSnapshot> players,
                           MobType type, int x, int y, int z, MobRandom rng) {
        if (!validNaturalDistance(players, x + 0.5, y, z + 0.5)
                || !outsideWorldSpawn(world, x + 0.5, y, z + 0.5)) return false;
        return validSpawnPlacement(world, type, x, y, z, rng);
    }

    private boolean validSpawnPlacement(MobWorldView world, MobType type,
                                        int x, int y, int z, MobRandom rng) {
        if (!hasActiveColumn(world, x, z) || y <= Blocks.MIN_Y || y >= Blocks.MAX_Y) return false;
        if (world.endDimension() && (y < 0 || y >= 256)) return false;
        return TerrainAccessor.inspectWithoutChunkActivation(() -> {
            if (type == MobType.BAT) {
                return onGroundPlacement(world, x, y, z)
                        && validBatSpeciesRules(world, x, y, z, rng)
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, true);
            }
            if (type == MobType.DROWNED) {
                return inWaterPlacement(world, x, y, z)
                        && validDrownedSpeciesRules(world, x, y, z, rng)
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, false);
            }
            // [MONUMENT] 가디언은 MONSTER 카테고리지만 바닐라 배치 타입이 IN_WATER 다
            // (`SpawnPlacements.register(GUARDIAN, IN_WATER, MOTION_BLOCKING_NO_LEAVES,
            //  Guardian::checkGuardianSpawnRules)`). 어둠 판정을 타는 지상 몬스터 분기로
            // 내려보내면 안 된다.
            if (type == MobType.GUARDIAN) {
                return inWaterPlacement(world, x, y, z)
                        && GuardianSpawnRules.naturalSpawnRule(world, x, y, z, rng)
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, false);
            }
            // 26.3 sulfur_cube uses a no-restrictions spawn rule: it still needs a valid
            // on-ground position and collision-free AABB, but never consumes darkness RNG.
            if (type == MobType.SULFUR_CUBE) {
                return onGroundPlacement(world, x, y, z)
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, true);
            }
            if (type.category() == MobCategory.WATER_CREATURE
                    || type.category() == MobCategory.WATER_AMBIENT
                    || type.category() == MobCategory.UNDERGROUND_WATER_CREATURE) {
                if (!inWaterPlacement(world, x, y, z)) return false;
                boolean speciesRule;
                if (type == MobType.GLOW_SQUID) {
                    speciesRule = y <= Blocks.SEA_LEVEL - 33
                            && rawBrightness(world, x, y, z) == 0;
                } else if (type == MobType.DOLPHIN) {
                    speciesRule = y >= Blocks.SEA_LEVEL - 13 && y <= Blocks.SEA_LEVEL
                            && isWater(world.getBlock(x, y - 1, z));
                } else if (type == MobType.NAUTILUS) {
                    // [NAUTILUS-SPAWN] 바닐라 수심 조건은 Y 38..58 이라 일반 수생 분기의
                    // SEA_LEVEL-13..SEA_LEVEL(50..63)과 다르다(연구 핀 §2). 위·아래 칸이
                    // 물이어야 하는 것은 일반 분기와 같다. 난수를 쓰지 않는다.
                    speciesRule = NautilusSpawnRules.withinSpawnDepth(y)
                            && isWater(world.getBlock(x, y - 1, z))
                            && isWater(world.getBlock(x, y + 1, z));
                } else {
                    speciesRule = y >= Blocks.SEA_LEVEL - 13 && y <= Blocks.SEA_LEVEL
                            && isWater(world.getBlock(x, y - 1, z))
                            && isWater(world.getBlock(x, y + 1, z));
                }
                return speciesRule
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, false);
            }
            if (type == MobType.AXOLOTL) {
                return inWaterPlacement(world, x, y, z)
                        && world.biomeAt(x, y, z) == 175
                        && world.getBlock(x, y - 1, z) == Blocks.CLAY
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, false);
            }
            if (type.category() == MobCategory.CREATURE) {
                return onGroundPlacement(world, x, y, z)
                        && validAnimalSpeciesRules(world, type, x, y, z)
                        && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, true);
            }
            if (!onGroundPlacement(world, x, y, z)) return false;
            boolean speciesRule = type == MobType.SLIME
                    ? validSlimeSpawn(world, x, y, z, rng)
                    : darkEnoughForMonster(world, x, y, z, rng);
            return speciesRule
                    && collisionFreeAabb(world, type, x + 0.5, y, z + 0.5, true);
        });
    }

    private static boolean onGroundPlacement(MobWorldView world, int x, int y, int z) {
        short supportBlock = world.getBlock(x, y - 1, z);
        if (supportBlock < 0) return false;
        int support = supportBlock & 0xffff;
        int supportState = world.blockState(x, y - 1, z, support);
        if (!BuildingBlockRules.canSpawnOn(support, supportState)) return false;
        return isEmptySpawnCell(world, x, y, z) && isEmptySpawnCell(world, x, y + 1, z);
    }

    private static boolean inWaterPlacement(MobWorldView world, int x, int y, int z) {
        if (!isWater(world.getBlock(x, y, z))) return false;
        short aboveBlock = world.getBlock(x, y + 1, z);
        if (aboveBlock < 0) return false;
        int above = aboveBlock & 0xffff;
        return !BuildingBlockRules.isRedstoneConductor(
                above, world.blockState(x, y + 1, z, above));
    }

    private static boolean validAnimalSpeciesRules(MobWorldView world, MobType type,
                                                   int x, int y, int z) {
        short floor = world.getBlock(x, y - 1, z);
        if (!animalSpawnFloor(type, floor)) return false;
        if (type == MobType.TURTLE
                && (y >= Blocks.SEA_LEVEL + 4 || !world.openToSky(x, y, z))) return false;
        return rawBrightness(world, x, y, z) > 8;
    }

    private static boolean animalSpawnFloor(MobType type, short floor) {
        return switch (type) {
            case RABBIT -> floor == GRASS || floor == Blocks.SAND || floor == Blocks.RED_SAND
                    || floor == Blocks.SNOW_BLOCK;
            case ARMADILLO -> armadilloSpawnFloor(floor);
            case MOOSHROOM -> floor == Blocks.MYCELIUM;
            case TURTLE -> floor == Blocks.SAND;
            case CAMEL -> floor == Blocks.SAND || floor == Blocks.RED_SAND;
            case GOAT -> floor == GRASS || floor == Blocks.STONE || floor == Blocks.GRAVEL
                    || floor == Blocks.SNOW_BLOCK;
            case POLAR_BEAR -> floor == GRASS || floor == Blocks.SNOW_BLOCK
                    || floor == Blocks.ICE || floor == Blocks.PACKED_ICE
                    || floor == Blocks.BLUE_ICE;
            case FOX, WOLF -> floor == GRASS || floor == Blocks.SNOW_BLOCK
                    || floor == Blocks.PODZOL || floor == Blocks.COARSE_DIRT;
            default -> floor == GRASS;
        };
    }

    private static boolean armadilloSpawnFloor(short floor) {
        return floor == GRASS || floor == Blocks.RED_SAND || floor == Blocks.COARSE_DIRT
                || floor == Blocks.TERRACOTTA || floor == Blocks.WHITE_TERRACOTTA
                || floor == Blocks.YELLOW_TERRACOTTA || floor == Blocks.ORANGE_TERRACOTTA
                || floor == Blocks.RED_TERRACOTTA || floor == Blocks.BROWN_TERRACOTTA
                || floor == Blocks.LIGHT_GRAY_TERRACOTTA;
    }

    private static boolean collisionFreeAabb(MobWorldView world, MobType type,
                                             double centerX, int y, double centerZ,
                                             boolean rejectLiquids) {
        double halfWidth = type.width() * 0.5;
        double entityMinX = centerX - halfWidth;
        double entityMaxX = centerX + halfWidth;
        double entityMinY = y;
        double entityMaxY = y + type.height();
        double entityMinZ = centerZ - halfWidth;
        double entityMaxZ = centerZ + halfWidth;
        int minX = (int) Math.floor(entityMinX);
        int maxX = (int) Math.ceil(entityMaxX) - 1;
        int minZ = (int) Math.floor(entityMinZ);
        int maxZ = (int) Math.ceil(entityMaxZ) - 1;
        int minY = (int) Math.floor(y) - 1;
        int maxY = (int) Math.ceil(y + type.height()) - 1;
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                for (int blockY = minY; blockY <= maxY; blockY++) {
                    short block = world.getBlock(blockX, blockY, blockZ);
                    if (block < 0) return false;
                    int id = block & 0xffff;
                    if (rejectLiquids && (Fluids.isWaterMedium(id) || Fluids.isLava(id))) {
                        return false;
                    }
                    int state = world.blockState(blockX, blockY, blockZ, id);
                    double localMinX = Math.max(0.0, entityMinX - blockX);
                    double localMaxX = Math.min(1.0, entityMaxX - blockX);
                    double localMinY = entityMinY - blockY;
                    double localMaxY = entityMaxY - blockY;
                    double localMinZ = Math.max(0.0, entityMinZ - blockZ);
                    double localMaxZ = Math.min(1.0, entityMaxZ - blockZ);
                    if (BuildingBlockRules.isStairs(id)
                            && (state & BuildingBlockRules.STAIR_TOP) != 0) {
                        boolean hitsFullTopHalf = localMaxY > 0.5 + 1.0e-6
                                && localMinY < 1.0 - 1.0e-6;
                        boolean hitsLowerQuarter = localMaxY > 1.0e-6
                                && localMinY < 0.5 - 1.0e-6
                                && BuildingBlockRules.stairQuarterIntersects(state,
                                        localMinX, localMaxX, localMinZ, localMaxZ);
                        if (hitsFullTopHalf || hitsLowerQuarter) return false;
                        continue;
                    }
                    double top = BuildingBlockRules.supportTop(id, state, blockX, blockZ,
                            localMinX, localMaxX, localMinZ, localMaxZ);
                    double bottom = BuildingBlockRules.collisionBottom(id, state);
                    if (top > bottom + 1.0e-6
                            && top > y - blockY + 1.0e-6
                            && bottom < entityMaxY - blockY - 1.0e-6) return false;
                }
            }
        }
        return true;
    }

    static boolean spawnerCollisionFree(MobWorldView world, MobType type,
                                        double centerX, int y, double centerZ) {
        return collisionFreeAabb(world, type, centerX, y, centerZ, false);
    }

    static boolean spawnerObstructionFree(MobWorldView world, MobType type,
                                          double centerX, int y, double centerZ) {
        return collisionFreeAabb(world, type, centerX, y, centerZ, true);
    }

    static boolean validSpawnerSpot(MobWorldView world, MobType type,
                                    int x, int y, int z, MobRandom rng) {
        return validSpawnerSpot(world, type, x + 0.5, y, z + 0.5, rng);
    }

    private static boolean validSpawnerSpot(MobWorldView world, MobType type,
                                            double centerX, int y, double centerZ,
                                            MobRandom rng) {
        int x = (int) Math.floor(centerX);
        int z = (int) Math.floor(centerZ);
        return spawnerCollisionFree(world, type, centerX, y, centerZ)
                && darkEnoughForMonster(world, x, y, z, rng);
    }

    private static boolean isBatSpawnSupport(int support) {
        return support == Blocks.STONE || support == Blocks.DEEPSLATE
                || support == Blocks.GRANITE || support == Blocks.DIORITE
                || support == Blocks.ANDESITE || support == Blocks.TUFF;
    }

    private static boolean darkEnoughForMonster(MobWorldView world, int x, int y, int z,
                                                MobRandom rng) {
        int rawSky = world.rawSkyLight(x, y, z);
        if (rawSky > rng.nextInt(32)) return false;
        int block = world.blockLight(x, y, z);
        if (block > 0) return false;
        int skyDarken = WorldClock.isNight(world.worldTime()) ? 11 : 0;
        int local = Math.max(block, Math.max(0, rawSky - skyDarken));
        return local <= (world.endDimension() ? 15 : rng.nextInt(8));
    }

    private static boolean validSlimeSpawn(MobWorldView world, int x, int y, int z,
                                           MobRandom rng) {
        int biome = world.biomeAt(x, y, z);
        if ((biome == 6 || biome == 184) && y > 50 && y < 70
                && rng.nextFloat() < 0.5f
                && rng.nextFloat() < WorldClock.moonBrightness(world.dayCount())) {
            int localBrightness = world.lightLevel(x, y, z);
            if (localBrightness <= rng.nextInt(8)) return true;
        }
        boolean slimeChunk = isSlimeChunk(
                world.worldSeed(), Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        return rng.nextInt(10) == 0 && slimeChunk && y < 40;
    }

    /** LegacyRandomSource의 첫 nextInt(10)을 allocation 없이 재현한다. */
    static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        long seed = slimeChunkSeed(worldSeed, chunkX, chunkZ);
        long state = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        while (true) {
            state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            int bits = (int) (state >>> 17);
            int value = bits % 10;
            if (bits - value + 9 >= 0) return value == 0;
        }
    }

    static long slimeChunkSeed(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed
                + (long) (chunkX * chunkX * 4_987_142)
                + (long) (chunkX * 5_947_611)
                + (long) (chunkZ * chunkZ) * 4_392_871L
                + (long) (chunkZ * 389_711) ^ 987_234_911L;
    }

    private static int rawBrightness(MobWorldView world, int x, int y, int z) {
        return Math.max(world.rawSkyLight(x, y, z), world.blockLight(x, y, z));
    }

    static int provisionalPackCount(float randomFloat) {
        return (int) Math.ceil(randomFloat * 4.0f);
    }

    /**
     * Axolotl.Variant.getCommonSpawnVariant: rare 가 아닌 lucy/wild/gold/cyan 4종 균등 추첨.
     * 무리 첫 개체가 이 추첨을 두 번 해 쌍을 만들고 각 구성원이 그중 하나를 고르므로
     * blue 는 자연 생성되지 않는다.
     */
    static String commonAxolotlVariant(MobRandom rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> "lucy";
            case 1 -> "wild";
            case 2 -> "gold";
            default -> "cyan";
        };
    }

    /**
     * TropicalFish.finalizeSpawn 의 common 변종 추첨(COMMON_VARIANTS 22종 균등).
     * 무리 첫 개체만 뽑고 나머지는 그룹 값을 그대로 쓴다. 바닐라의 10% 완전 무작위 가지는
     * 닫힌 프로토콜 어휘 밖이라 WebCraft 는 채택하지 않는다(docs/MC-REFERENCE.md).
     */
    static String commonTropicalFishVariant(MobRandom rng) {
        return MobType.TROPICAL_FISH.variantAt(
                rng.nextInt(MobType.TROPICAL_FISH.variantCount()));
    }

    /** 생성 시 배치 전용 지형 판정: placement → 실제 AABB → 종별 바닥/밝기 순서. */
    private boolean validPopulationSpot(MobWorldView world, int bx, int feetY, int bz,
                                        double spawnX, double spawnZ, MobType type) {
        if (feetY <= Blocks.MIN_Y || feetY >= Blocks.MAX_Y) return false;
        return onGroundPlacement(world, bx, feetY, bz)
                && collisionFreeAabb(world, type, spawnX, feetY, spawnZ, true)
                && validAnimalSpeciesRules(world, type, bx, feetY, bz);
    }

    public static boolean isNight(long worldTime) {
        return WorldClock.isNight(worldTime);
    }

    /** 서버 10TPS 틱을 Java 20TPS 자연 스폰 두 패스로 평가한다. */
    public List<SpawnRequest> tick(MobWorldView world, MobRandom rng,
                                   Collection<Mob> mobs, long worldTick) {
        List<SpawnRequest> out = new ArrayList<>();
        List<PlayerSnapshot> alive = alivePlayers(world);
        if (alive.isEmpty()) return out;

        int spawnableChunkCount = collectSpawnableChunks(world, alive);
        if (world.endDimension()) {
            LocalCapIndex localCaps = localCapIndex.reset(mobs, out, alive);
            Set<SpawnerPosition> indexedSpawners = findSpawnerBlocks(alive);
            for (int pass = 0; pass < VIRTUAL_PASSES_PER_SERVER_TICK; pass++) {
                trySpawnerBlocks(world, rng, mobs, alive, out, indexedSpawners, localCaps);
                tryNaturalCategory(world, rng, mobs, alive, out,
                        spawnableChunkScratch, spawnableChunkCount,
                        MobCategory.MONSTER, SpawnCategory.MONSTER, WORLD_MOB_CAP);
            }
            return out;
        }
        countCategories(mobs, globalCategoryScratch);
        int monsterCap = scaledCap(WORLD_MOB_CAP, spawnableChunkCount);
        int monsters = globalCategoryScratch[MobCategory.MONSTER.ordinal()];
        monsters += tryPillagerPatrol(
                world, rng, monsters, monsterCap, alive, out, worldTick);
        monsters += tryZombieBearProwl(world, rng, monsters, monsterCap, alive, out);
        // 좀비곰은 WebCraft 독립 굴림이고, 26.3 좀비 말은 바이옴 MONSTER 표가 소유한다.
        int animalCap = scaledCap(WORLD_ANIMAL_CAP, spawnableChunkCount);
        int animals = globalCategoryScratch[MobCategory.CREATURE.ordinal()];
        monsters += tryZombieWolfPack(world, rng, monsters, monsterCap, alive, out);
        // [ZOMBIE-ANIMAL] 좀비 동물 10종. 굴림 순서(무리 → 염소 → 낙타 → 여우 → 닭 → 사슴 →
        // 멧돼지)가 곧 난수 소비 순서이므로 정적판 `StandaloneMobRuntime` 사본도 이 순서를
        // 글자 그대로 지킨다. 시체 까마귀는 독립 굴림이 없고 무리 굴림에 부수한다.
        int ambientCap = scaledCap(WORLD_AMBIENT_CAP, spawnableChunkCount);
        int ambients = globalCategoryScratch[MobCategory.AMBIENT.ordinal()];
        monsters += tryZombieHerdPack(
                world, rng, monsters, monsterCap, ambients, ambientCap, alive, out);
        monsters += tryZombieAnimalProwl(world, rng, monsters, monsterCap, alive, out,
                MobType.ZOMBIE_GOAT, ZOMBIE_GOAT_PROWL_ROLL);
        animals += tryCamelHuskEncounter(world, rng, animals, animalCap, alive, out);
        monsters += tryZombieAnimalProwl(world, rng, monsters, monsterCap, alive, out,
                MobType.ZOMBIE_FOX, ZOMBIE_FOX_PROWL_ROLL);
        monsters += tryZombieAnimalProwl(world, rng, monsters, monsterCap, alive, out,
                MobType.ZOMBIE_CHICKEN, ZOMBIE_CHICKEN_PROWL_ROLL);
        monsters += tryZombieAnimalProwl(world, rng, monsters, monsterCap, alive, out,
                MobType.CARRION_STAG, CARRION_STAG_PROWL_ROLL);
        monsters += tryZombieAnimalProwl(world, rng, monsters, monsterCap, alive, out,
                MobType.CARRION_BOAR, CARRION_BOAR_PROWL_ROLL);
        animals += tryWanderingTraderArrival(world, rng, animals, animalCap, alive, out);
        // Phantom uses its separate vanilla insomnia spawner, not a biome table.
        monsters += tryPhantomInsomnia(world, rng, monsters, monsterCap, alive, out);
        appendDrownedJockeyRequests(mobs, rng, out);
        LocalCapIndex localCaps = localCapIndex.reset(mobs, out, alive);
        Set<SpawnerPosition> indexedSpawners = findSpawnerBlocks(alive);
        long virtualTick = worldTick * VIRTUAL_PASSES_PER_SERVER_TICK;
        for (int pass = 0; pass < VIRTUAL_PASSES_PER_SERVER_TICK; pass++, virtualTick++) {
            // Spawner blocks are independent of natural-spawn caps.
            trySpawnerBlocks(world, rng, mobs, alive, out, indexedSpawners, localCaps);
            tryNaturalCategory(world, rng, mobs, alive, out,
                    spawnableChunkScratch, spawnableChunkCount,
                    MobCategory.MONSTER, SpawnCategory.MONSTER, WORLD_MOB_CAP);
            if (virtualTick % CREATURE_SPAWN_INTERVAL == 0) {
                tryNaturalCategory(world, rng, mobs, alive, out,
                        spawnableChunkScratch, spawnableChunkCount,
                        MobCategory.CREATURE, SpawnCategory.CREATURE, WORLD_ANIMAL_CAP);
            }
            tryNaturalCategory(world, rng, mobs, alive, out,
                    spawnableChunkScratch, spawnableChunkCount,
                    MobCategory.AMBIENT, SpawnCategory.AMBIENT, WORLD_AMBIENT_CAP);
            tryNaturalCategory(world, rng, mobs, alive, out,
                    spawnableChunkScratch, spawnableChunkCount,
                    MobCategory.WATER_CREATURE, SpawnCategory.WATER_CREATURE,
                    WORLD_WATER_CREATURE_CAP);
            tryNaturalCategory(world, rng, mobs, alive, out,
                    spawnableChunkScratch, spawnableChunkCount,
                    MobCategory.WATER_AMBIENT, SpawnCategory.WATER_AMBIENT,
                    WORLD_WATER_AMBIENT_CAP);
            tryNaturalCategory(world, rng, mobs, alive, out,
                    spawnableChunkScratch, spawnableChunkCount,
                    MobCategory.UNDERGROUND_WATER_CREATURE,
                    SpawnCategory.UNDERGROUND_WATER_CREATURE,
                    WORLD_UNDERGROUND_WATER_CREATURE_CAP);
            tryNaturalCategory(world, rng, mobs, alive, out,
                    spawnableChunkScratch, spawnableChunkCount,
                    MobCategory.AXOLOTLS, SpawnCategory.AXOLOTLS, WORLD_AXOLOTL_CAP);
        }
        return out;
    }

    /**
     * One-shot Drowned jockey tail. This separate non-natural origin remains after the special
     * spawners and before the category passes, so winners enter the following local-cap reset.
     */
    static void appendDrownedJockeyRequests(
            Collection<Mob> mobs, MobRandom rng, List<SpawnRequest> out) {
        List<Drowned> pending = new ArrayList<>();
        for (Mob mob : mobs) {
            if (mob instanceof Drowned drowned && !mob.isDead() && !mob.removed
                    && drowned.jockeyDecisionArmed() && !drowned.jockeyDecisionSettled()
                    && drowned.carrierRolled()) {
                pending.add(drowned);
            }
        }
        pending.sort(Comparator.comparingLong(mob -> mob.id));
        for (Drowned drowned : pending) {
            if (!drowned.tridentCarrier()) {
                drowned.settleJockeyDecision(false);
                continue;
            }
            boolean winner = rng.nextInt(2) == 0;
            drowned.settleJockeyDecision(winner);
            if (winner) out.add(SpawnRequest.zombieNautilusJockey(drowned));
        }
    }

    /**
     * 구조물 전초기지 전까지 사용하는 순찰대 매핑. 밤의 열린 지표에만 낮은 확률로
     * 2~3마리를 붙여 내며, 자연 적대 전역 캡과 모든 플레이어의 안전 반경을 공유한다.
     */
    private int tryPillagerPatrol(MobWorldView world, MobRandom rng, int hostiles, int hostileCap,
                                  List<PlayerSnapshot> alive, List<SpawnRequest> out,
                                  long worldTick) {
        if (hostiles >= hostileCap || !isNight(world.worldTime())
                || rng.nextInt(PILLAGER_PATROL_ROLL) != 0) return 0;
        PlayerSnapshot p = alive.get(rng.nextInt(alive.size()));
        double ang = rng.nextDouble() * TWO_PI;
        int d = 25 + rng.nextInt(104);
        int bx = (int) Math.floor(p.x() + Math.cos(ang) * d);
        int bz = (int) Math.floor(p.z() + Math.sin(ang) * d);
        int wanted = PILLAGER_PATROL_MIN
                + rng.nextInt(PILLAGER_PATROL_MAX - PILLAGER_PATROL_MIN + 1);
        long patrolIdentity = worldTick * 0x9e3779b97f4a7c15L
                ^ ((long) bx << 32) ^ (bz & 0xffff_ffffL);
        int[][] offsets = {{0, 0}, {1, 0}, {0, 1}};
        int added = 0;
        for (int[] offset : offsets) {
            if (added >= wanted || hostiles + added >= hostileCap) break;
            int x = bx + offset[0], z = bz + offset[1];
            if (!hasActiveColumn(world, x, z)) continue;
            int feetY = world.motionBlockingNoLeavesHeight(x, z) + 1;
            if (!confirmedHostileSpot(world, alive, MobType.PILLAGER, x, feetY, z, rng)) continue;
            out.add(new SpawnRequest(MobType.PILLAGER, x + 0.5, feetY, z + 0.5,
                    IllagerCompanionPolicy.Context.PATROL, patrolIdentity));
            added++;
        }
        return added;
    }

    /**
     * 좀비곰 배회(WebCraft 창작몹). 순찰대와 같은 <b>독립 굴림</b> 방식이며 바이옴 가중치 표는
     * 읽지도 쓰지도 않는다 — 기존 종의 스폰 확률을 한 톨도 바꾸지 않기 위한 계약이다
     * ({@link #ZOMBIE_BEAR_PROWL_ROLL} 주석에 기대 조우율 수치를 남겼다).
     *
     * <p>난수 소비 순서는 정적판 사본과 글자 그대로 같아야 한다:
     * {@code nextInt(ROLL)} → {@code nextInt(alive)} → {@code nextDouble()} →
     * {@code nextInt(SPAN)} → (지형 확인) {@code confirmedHostileSpot} 내부 소비.
     * 굴림이 실패하면 그 뒤 난수는 하나도 소비하지 않는다.
     */
    private int tryZombieBearProwl(MobWorldView world, MobRandom rng, int hostiles, int hostileCap,
                                   List<PlayerSnapshot> alive, List<SpawnRequest> out) {
        if (hostiles >= hostileCap || !isNight(world.worldTime())
                || rng.nextInt(ZOMBIE_BEAR_PROWL_ROLL) != 0) return 0;
        PlayerSnapshot player = alive.get(rng.nextInt(alive.size()));
        double angle = rng.nextDouble() * TWO_PI;
        int distance = ZOMBIE_BEAR_PROWL_MIN_DISTANCE
                + rng.nextInt(ZOMBIE_BEAR_PROWL_DISTANCE_SPAN);
        int bx = (int) Math.floor(player.x() + Math.cos(angle) * distance);
        int bz = (int) Math.floor(player.z() + Math.sin(angle) * distance);
        if (!hasActiveColumn(world, bx, bz)) return 0;
        int feetY = world.motionBlockingNoLeavesHeight(bx, bz) + 1;
        // 갈색곰이 사는 숲/타이가 계열에서만 부패한 개체가 나온다(같은 개체군의 부패형).
        if (!brownBearBiome(world.biomeAt(bx, feetY, bz))) return 0;
        if (!confirmedHostileSpot(world, alive, MobType.ZOMBIE_BEAR, bx, feetY, bz, rng)) return 0;
        int added = 0;
        while (added < ZOMBIE_BEAR_PROWL_COUNT && hostiles + added < hostileCap) {
            out.add(new SpawnRequest(MobType.ZOMBIE_BEAR, bx + 0.5, feetY, bz + 0.5));
            added++;
        }
        return added;
    }

    /**
     * 좀비 늑대 무리. 좀비곰 배회와 같은 독립 굴림이되 순찰대처럼 <b>여러 마리</b>를 한 지점
     * 주위에 붙인다 — 마릿수 굴림 {@code nextInt(MAX-MIN+1)} 이 거리 굴림 뒤에 오며, 이 순서가
     * 곧 난수 소비 계약이다.
     */
    private int tryZombieWolfPack(MobWorldView world, MobRandom rng, int hostiles, int hostileCap,
                                  List<PlayerSnapshot> alive, List<SpawnRequest> out) {
        if (hostiles >= hostileCap || !isNight(world.worldTime())
                || rng.nextInt(ZOMBIE_WOLF_PACK_ROLL) != 0) return 0;
        PlayerSnapshot player = alive.get(rng.nextInt(alive.size()));
        double angle = rng.nextDouble() * TWO_PI;
        int distance = ZOMBIE_WOLF_PACK_MIN_DISTANCE
                + rng.nextInt(ZOMBIE_WOLF_PACK_DISTANCE_SPAN);
        int wanted = ZOMBIE_WOLF_PACK_MIN
                + rng.nextInt(ZOMBIE_WOLF_PACK_MAX - ZOMBIE_WOLF_PACK_MIN + 1);
        int bx = (int) Math.floor(player.x() + Math.cos(angle) * distance);
        int bz = (int) Math.floor(player.z() + Math.sin(angle) * distance);
        if (!hasActiveColumn(world, bx, bz)) return 0;
        int feetY = world.motionBlockingNoLeavesHeight(bx, bz) + 1;
        // 늑대가 사는 숲/타이가 계열에서만 부패한 무리가 나온다(갈색곰과 같은 목록).
        if (!zombieWolfBiome(world.biomeAt(bx, feetY, bz))) return 0;
        // 순찰대와 같은 오프셋 배치. 한 지점의 유효성만 확인하고 이웃 칸에 붙인다.
        int[][] offsets = {{0, 0}, {1, 0}, {0, 1}};
        int added = 0;
        for (int[] offset : offsets) {
            if (added >= wanted || hostiles + added >= hostileCap) break;
            int x = bx + offset[0];
            int z = bz + offset[1];
            if (!hasActiveColumn(world, x, z)) continue;
            int spotY = world.motionBlockingNoLeavesHeight(x, z) + 1;
            if (!confirmedHostileSpot(world, alive, MobType.ZOMBIE_WOLF, x, spotY, z, rng)) continue;
            out.add(new SpawnRequest(MobType.ZOMBIE_WOLF, x + 0.5, spotY, z + 0.5));
            added++;
        }
        return added;
    }

    /**
     * [ZOMBIE-ANIMAL] 흔한 티어 — 좀비 소·돼지·양의 <b>혼성 무리</b>. 좀비 늑대 무리와 같은
     * 형태의 독립 굴림이되 자리마다 <b>종을 따로 뽑는다</b>는 것 하나가 다르다.
     *
     * <p>난수 소비 순서(정적판 사본과 글자 그대로 같다):
     * {@code nextInt(ROLL)} → {@code nextInt(alive)} → {@code nextDouble()} →
     * {@code nextInt(SPAN)} → {@code nextInt(MAX-MIN+1)} → 자리마다
     * ({@code confirmedHostileSpot} 내부 소비 → 성공하면 {@code nextInt(3)} 종 추첨) →
     * 한 마리라도 붙었으면 {@code nextInt(CROW_ROLL)} 까마귀 호위. 굴림이 실패하면 그 뒤
     * 난수는 하나도 소비하지 않는다.
     *
     * @return 이번 굴림이 더한 <b>적대</b> 개체 수(까마귀는 AMBIENT 라 세지 않는다)
     */
    private int tryZombieHerdPack(MobWorldView world, MobRandom rng, int hostiles, int hostileCap,
                                  int ambients, int ambientCap,
                                  List<PlayerSnapshot> alive, List<SpawnRequest> out) {
        if (hostiles >= hostileCap || !isNight(world.worldTime())
                || rng.nextInt(ZOMBIE_HERD_PACK_ROLL) != 0) return 0;
        PlayerSnapshot player = alive.get(rng.nextInt(alive.size()));
        double angle = rng.nextDouble() * TWO_PI;
        int distance = ZOMBIE_HERD_PACK_MIN_DISTANCE
                + rng.nextInt(ZOMBIE_HERD_PACK_DISTANCE_SPAN);
        int wanted = ZOMBIE_HERD_PACK_MIN
                + rng.nextInt(ZOMBIE_HERD_PACK_MAX - ZOMBIE_HERD_PACK_MIN + 1);
        int bx = (int) Math.floor(player.x() + Math.cos(angle) * distance);
        int bz = (int) Math.floor(player.z() + Math.sin(angle) * distance);
        if (!hasActiveColumn(world, bx, bz)) return 0;
        int feetY = world.motionBlockingNoLeavesHeight(bx, bz) + 1;
        // 가축이 사는 목초지·숲 지표에서만 부패한 무리가 나온다(같은 개체군의 부패형).
        if (!zombieHerdBiome(world.biomeAt(bx, feetY, bz))) return 0;
        // 순찰대·좀비 늑대와 같은 오프셋 배치. 네 마리까지라 자리도 넷이다.
        int[][] offsets = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        int added = 0;
        int lastY = feetY;
        for (int[] offset : offsets) {
            if (added >= wanted || hostiles + added >= hostileCap) break;
            int x = bx + offset[0];
            int z = bz + offset[1];
            if (!hasActiveColumn(world, x, z)) continue;
            int spotY = world.motionBlockingNoLeavesHeight(x, z) + 1;
            if (!confirmedHostileSpot(world, alive, ZOMBIE_HERD_SPOT_TYPE, x, spotY, z, rng)) continue;
            out.add(new SpawnRequest(herdSpecies(rng.nextInt(ZOMBIE_HERD_SPECIES_ROLL)),
                    x + 0.5, spotY, z + 0.5));
            lastY = spotY;
            added++;
        }
        if (added == 0) return 0;
        // 시체 까마귀는 무리 위를 돈다 — 밤에 멀리서 무리를 알아보는 유일한 신호다.
        if (ambients < ambientCap
                && rng.nextInt(CarrionCrowRules.HERD_ESCORT_ROLL) == 0) {
            out.add(new SpawnRequest(MobType.CARRION_CROW,
                    bx + 0.5 + CarrionCrowRules.ORBIT_RADIUS,
                    lastY + CarrionCrowRules.ORBIT_ALTITUDE, bz + 0.5));
        }
        return added;
    }

    /** 무리 구성 추첨의 인덱스 → 종. 0=소 · 1=돼지 · 2=양 이고 순서가 곧 RNG 정체성이다. */
    static MobType herdSpecies(int rolled) {
        return switch (rolled) {
            case 0 -> MobType.ZOMBIE_COW;
            case 1 -> MobType.ZOMBIE_PIG;
            default -> MobType.ZOMBIE_SHEEP;
        };
    }

    /**
     * [ZOMBIE-ANIMAL] 한 마리짜리 티어 굴림 공통 구현(염소·여우·닭·사슴·멧돼지). 좀비곰 배회와
     * <b>글자 그대로 같은 형태</b>이고 갈리는 것은 종·굴림 상한·바이옴 술어 셋뿐이다.
     *
     * <p>난수 소비 순서: {@code nextInt(roll)} → {@code nextInt(alive)} →
     * {@code nextDouble()} → {@code nextInt(SPAN)} → (지형 확인) {@code confirmedHostileSpot}
     * 내부 소비. 굴림이 실패하면 그 뒤 난수는 하나도 소비하지 않는다.
     */
    private int tryZombieAnimalProwl(MobWorldView world, MobRandom rng, int hostiles,
                                     int hostileCap, List<PlayerSnapshot> alive,
                                     List<SpawnRequest> out, MobType type, int roll) {
        if (hostiles >= hostileCap || !isNight(world.worldTime())
                || rng.nextInt(roll) != 0) return 0;
        PlayerSnapshot player = alive.get(rng.nextInt(alive.size()));
        double angle = rng.nextDouble() * TWO_PI;
        int distance = ZOMBIE_ANIMAL_MIN_DISTANCE + rng.nextInt(ZOMBIE_ANIMAL_DISTANCE_SPAN);
        int bx = (int) Math.floor(player.x() + Math.cos(angle) * distance);
        int bz = (int) Math.floor(player.z() + Math.sin(angle) * distance);
        if (!hasActiveColumn(world, bx, bz)) return 0;
        int feetY = world.motionBlockingNoLeavesHeight(bx, bz) + 1;
        if (!zombieAnimalBiome(type, world.biomeAt(bx, feetY, bz))) return 0;
        if (!confirmedHostileSpot(world, alive, type, bx, feetY, bz, rng)) return 0;
        out.add(new SpawnRequest(type, bx + 0.5, feetY, bz + 0.5));
        return 1;
    }

    /**
     * [ZOMBIE-ANIMAL] 낙타 husk 조우. 좀비 말과 <b>같은 자리</b>의 계약이다 — 선공하지 않는
     * 언데드 동물이라 <b>CREATURE 캡</b>을 보고, 그래도 밤·어둠 조건은 좀비곰과 같이 쓴다.
     * 바닐라가 husk 스폰의 10%에 얹는 것과 갈리는 이유는 {@link CamelHuskRules} 가 소유한다.
     */
    private int tryCamelHuskEncounter(MobWorldView world, MobRandom rng, int animals,
                                      int animalCap, List<PlayerSnapshot> alive,
                                      List<SpawnRequest> out) {
        if (animals >= animalCap || !isNight(world.worldTime())
                || rng.nextInt(CAMEL_HUSK_ENCOUNTER_ROLL) != 0) return 0;
        PlayerSnapshot player = alive.get(rng.nextInt(alive.size()));
        double angle = rng.nextDouble() * TWO_PI;
        int distance = ZOMBIE_ANIMAL_MIN_DISTANCE + rng.nextInt(ZOMBIE_ANIMAL_DISTANCE_SPAN);
        int bx = (int) Math.floor(player.x() + Math.cos(angle) * distance);
        int bz = (int) Math.floor(player.z() + Math.sin(angle) * distance);
        if (!hasActiveColumn(world, bx, bz)) return 0;
        int feetY = world.motionBlockingNoLeavesHeight(bx, bz) + 1;
        if (!camelHuskBiome(world.biomeAt(bx, feetY, bz))) return 0;
        if (!confirmedHostileSpot(world, alive, MobType.CAMEL_HUSK, bx, feetY, bz, rng)) return 0;
        out.add(new SpawnRequest(MobType.CAMEL_HUSK, bx + 0.5, feetY, bz + 0.5));
        return 1;
    }

    /**
     * [WAVE-86-97] 재시작을 건너 이어지는 <b>영속 시계</b>. 절대 일수({@code dayCount}) 는 양
     * 권위 모두 월드 행에 적히므로 하루 경계는 재시작으로 사라지지 않는다. 일중 시각은 세션마다
     * 0 에서 다시 흐르므로 이 값이 하루 안에서 뒤로 갈 수는 있지만, 그래도 <b>하루에 한 번</b>
     * 창이 반드시 열린다는 성질은 남는다(비영속 {@code worldTick} 은 그러지 못한다).
     */
    static long persistentSpawnTick(MobWorldView world) {
        return world.dayCount() * WorldClock.DAY_LENGTH + world.worldTime();
    }

    /**
     * [WAVE-86-97] 영속 대상 행상인 창 상태 {@code {다음 시도 틱, 현재 확률(%)}}. 어댑터가
     * 이 값을 월드 행에 적고 다음 접속에 {@link #restoreTraderWindow} 로 되돌린다.
     */
    public long[] traderWindowState() {
        return new long[] {traderNextAttemptTick, traderChancePercent};
    }

    /**
     * [WAVE-86-97] 저장된 창 상태를 되돌린다. 값이 없던 월드는 {@link #TRADER_WINDOW_UNSET}
     * 을 그대로 넘기면 되고, 확률은 바닐라와 같이 계단 범위로 조인다.
     */
    public void restoreTraderWindow(long nextAttemptTick, int chancePercent) {
        this.traderNextAttemptTick = nextAttemptTick;
        this.traderChancePercent = Math.min(WanderingTrader.SPAWN_CHANCE_MAX_PERCENT,
                Math.max(WanderingTrader.SPAWN_CHANCE_MIN_PERCENT, chancePercent));
    }

    /**
     * [WAVE-86-97] 행상인 도착. 바닐라 {@code WanderingTraderSpawner} 의 계단 확률을 옮긴다 —
     * {@value #WANDERING_TRADER_INTERVAL_TICKS} 서버 틱마다 창이 한 번 열리고, 굴림 확률은
     * {@link WanderingTrader#SPAWN_CHANCE_MIN_PERCENT}% 에서 시작해 실패할 때마다
     * {@link WanderingTrader#SPAWN_CHANCE_STEP_PERCENT}% 씩 올라
     * {@link WanderingTrader#SPAWN_CHANCE_MAX_PERCENT}% 에서 멈춘다. <b>실제로 개체가
     * 나왔을 때만</b> 최소로 되돌아간다 — 바닐라 {@code WanderingTraderSpawner#tick} 의
     * 마지막 두 줄이 글자 그대로 그렇다:
     * <pre>{@code
     *   int chance = this.spawnChance;
     *   this.spawnChance = Mth.clamp(chance + 25, 25, 75);
     *   ...
     *   if (this.random.nextInt(100) > chance) return;
     *   if (this.spawn()) this.spawnChance = 25;
     * }</pre>
     * 즉 계단은 <b>창이 열릴 때마다</b> 오르고, 굴림이 통해도 배치가 실패하면
     * ({@code spawn()} 이 false) 오른 값이 그대로 남는다. 굴림이 통했다는 이유만으로
     * 25% 로 되돌리면 자리가 나빠 계속 실패하는 월드에서 계단이 영영 오르지 못한다.
     *
     * <p><b>창 상태의 영속</b>({@link #traderNextAttemptTick} · {@link #traderChancePercent}):
     * 바닐라도 이 둘을 level.dat({@code wanderingTraderSpawnDelay} ·
     * {@code wanderingTraderSpawnChance})에 적는다. 비영속 {@code worldTick} 의 나머지 연산에
     * 묶으면 서버가 12,000 틱 전에 재시작될 때마다 창이 한 번도 열리지 않는다. 그래서 창은
     * <b>영속 시계</b>({@link #persistentSpawnTick}: 영속 {@code dayCount} × 하루 길이 +
     * 일중 시각)로 재고, 두 값은 {@link #traderWindowState()} 로 어댑터가 월드 행에 적는다.
     *
     * <p><b>트레이더 라마 2기</b>가 함께 나온다(바닐라 리드 동행). 리드가 없는 divergence 는
     * {@link TraderLlama} 가 소유한다.
     *
     * <p>난수 소비 순서: (창이 열린 틱에만) {@code nextInt(100)} → 성공하면
     * {@code nextInt(alive)} → {@code nextDouble()} → {@code nextInt(SPAN)}. 창이 닫힌 틱에는
     * 난수를 하나도 쓰지 않는다.
     */
    private int tryWanderingTraderArrival(MobWorldView world, MobRandom rng, int animals,
                                          int animalCap, List<PlayerSnapshot> alive,
                                          List<SpawnRequest> out) {
        long now = persistentSpawnTick(world);
        if (traderNextAttemptTick == TRADER_WINDOW_UNSET) {
            // 첫 창은 바닐라의 초기 spawnDelay 24,000 MC 틱과 같은 하루 뒤다.
            traderNextAttemptTick = now + WANDERING_TRADER_INTERVAL_TICKS;
            return 0;
        }
        if (now < traderNextAttemptTick) return 0;
        traderNextAttemptTick = now + WANDERING_TRADER_INTERVAL_TICKS;
        int chance = traderChancePercent;
        traderChancePercent = Math.min(WanderingTrader.SPAWN_CHANCE_MAX_PERCENT,
                traderChancePercent + WanderingTrader.SPAWN_CHANCE_STEP_PERCENT);
        if (rng.nextInt(100) >= chance) return 0;
        if (animals >= animalCap) return 0;
        PlayerSnapshot player = alive.get(rng.nextInt(alive.size()));
        double angle = rng.nextDouble() * TWO_PI;
        int distance = ZOMBIE_ANIMAL_MIN_DISTANCE + rng.nextInt(ZOMBIE_ANIMAL_DISTANCE_SPAN);
        int bx = (int) Math.floor(player.x() + Math.cos(angle) * distance);
        int bz = (int) Math.floor(player.z() + Math.sin(angle) * distance);
        if (!hasActiveColumn(world, bx, bz)) return 0;
        int feetY = world.motionBlockingNoLeavesHeight(bx, bz) + 1;
        // 자연 스폰 동물과 같은 관문을 쓴다({@code validSpawnSpot}) — 정적판
        // `validNaturalSpawnSpot` 과 같은 술어라 두 권위의 판정이 글자 그대로 같다.
        // 이 관문은 CREATURE 분기라 난수를 소비하지 않는다.
        if (!validSpawnSpot(world, alive, MobType.WANDERING_TRADER, bx, feetY, bz, rng)) return 0;
        out.add(new SpawnRequest(MobType.WANDERING_TRADER, bx + 0.5, feetY, bz + 0.5));
        // 바닐라 `if (this.spawn()) this.spawnChance = 25;` — 개체가 실제로 나온 이 자리에서만
        // 계단이 최소로 돌아간다.
        traderChancePercent = WanderingTrader.SPAWN_CHANCE_MIN_PERCENT;
        int added = 1;
        // 트레이더 라마 2기. 행상인 옆 칸에 붙이며 자리가 없으면 그만큼 덜 나온다.
        int[][] offsets = {{1, 0}, {0, 1}};
        for (int[] offset : offsets) {
            if (added - 1 >= WanderingTrader.LLAMA_ESCORT_COUNT
                    || animals + added >= animalCap) break;
            int x = bx + offset[0];
            int z = bz + offset[1];
            if (!hasActiveColumn(world, x, z)) continue;
            int spotY = world.motionBlockingNoLeavesHeight(x, z) + 1;
            if (!validSpawnSpot(world, alive, MobType.TRADER_LLAMA, x, spotY, z, rng)) continue;
            out.add(new SpawnRequest(MobType.TRADER_LLAMA, x + 0.5, spotY, z + 0.5));
            added++;
        }
        return added;
    }

    /**
     * 혼성 무리가 나오는 바이옴. 살아 있는 소·돼지·양이 함께 스폰하는 목초지·숲 지표 계열이다 —
     * plains(1) · windswept_hills(3) · forest(4) · taiga(5) · birch_forest(27) ·
     * dark_forest(29) · old_growth_pine_taiga(32) · savanna(35) · savanna_plateau(36) ·
     * sunflower_plains(129) · flower_forest(132) · old_growth_birch_forest(155) ·
     * old_growth_spruce_taiga(160) · meadow(177) · cherry_grove(185).
     */
    /**
     * [PHANTOM] 불면 팬텀. 바닐라 {@code PhantomSpawner.tick} 을 10 TPS 로 옮긴 사본이며,
     * 원문은 {@code docs/research/mc-phantom-insomnia.md} §1 에 고정돼 있다.
     *
     * <p><b>난수 소비 순서는 원문 그대로다</b>(정적판 {@code StandaloneMobRuntime#planPhantomInsomnia}
     * 도 글자 그대로 같다):
     * <ol>
     *   <li>창이 열린 틱에만 {@code nextInt(60)} 하나(다음 창 재장전). 창이 닫혀 있으면
     *       난수를 <b>하나도</b> 쓰지 않는다.</li>
     *   <li>밤이 아니면 재장전만 하고 끝난다(원문의 {@code getSkyDarken() < 5} 조기 반환과 같은 자리).</li>
     *   <li>살아 있는 플레이어마다: {@code nextFloat()} → (난이도 통과 시)
     *       {@code nextInt(불면 MC 틱)} → (72000 이상이면) {@code nextInt(15)}, {@code nextInt(21)},
     *       {@code nextInt(21)} → (자리 유효 시) {@code nextInt(난이도 id + 1)}.</li>
     * </ol>
     * 어느 관문에서 떨어지든 그 뒤 난수는 소비하지 않는다.
     *
     * <p><b>바닐라와 다른 점 둘</b>:
     * <ul>
     *   <li>지역 난이도가 없어 {@link Difficulty#phantomEffectiveDifficulty()} 하한을 쓴다(§1.1).</li>
     *   <li>바닐라 {@code CustomSpawner} 는 몬스터 cap 을 보지 않지만, 이 저장소의 다른 독립 굴림
     *       (순찰대·좀비곰·유황 큐브)과 같이 전역 적대 cap 을 공유한다. 넘치는 밤에 팬텀만
     *       무한히 쌓이지 않게 하는 저장소 계약이다.</li>
     * </ul>
     */
    // 패키지 가시성: 창 카운트다운·난수 소비 순서를 `PhantomInsomniaTest` 가 직접 굴려
    // 검증한다. tick() 전체를 통과시키면 앞선 굴림들의 난수 소비에 가려 순서를 못 박을 수 없다.
    int tryPhantomInsomnia(MobWorldView world, MobRandom rng, int hostiles, int hostileCap,
                                   List<PlayerSnapshot> alive, List<SpawnRequest> out) {
        // 원문의 `--this.nextTick; if (this.nextTick > 0) return 0;` — game tick 당 1 감소이므로
        // 서버 틱 당 1 감소로 옮기고 재장전 폭을 초 × 10 으로 환산한다(같은 벽시계 60~119초).
        if (--phantomNextTick > 0) return 0;
        phantomNextTick += (PHANTOM_ATTEMPT_MIN_TICKS
                + rng.nextInt(PHANTOM_ATTEMPT_SPAN_SECONDS) * PHANTOM_ATTEMPT_TICKS_PER_SECOND);
        if (!isNight(world.worldTime())) return 0;
        Difficulty difficulty = world.difficulty();
        float effectiveDifficulty = difficulty.phantomEffectiveDifficulty();
        int added = 0;
        for (PlayerSnapshot player : alive) {
            int px = (int) Math.floor(player.x());
            int py = (int) Math.floor(player.y());
            int pz = (int) Math.floor(player.z());
            // 원문의 `y >= seaLevel && canSeeSky(pos)`. 두 관문은 난수를 쓰지 않으므로
            // 여기서 걸러도 이후 소비 순서가 흔들리지 않는다 — 원문도 같은 순서다.
            if (py < Blocks.SEA_LEVEL || !world.openToSky(px, py, pz)) continue;
            if (!(effectiveDifficulty > rng.nextFloat() * PHANTOM_DIFFICULTY_ROLL_SCALE)) continue;
            // 원문 `Mth.clamp(TIME_SINCE_REST, 1, Integer.MAX_VALUE)`.
            long sleepless = player.timeSinceRestMcTicks();
            int bound = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, sleepless));
            if (rng.nextInt(bound) < Phantom.SLEEPLESS_TICKS_THRESHOLD) continue;
            int y = py + PHANTOM_SPAWN_ABOVE_MIN + rng.nextInt(PHANTOM_SPAWN_ABOVE_SPAN);
            // east = +x, south = +z (원문 BlockPos.east/south).
            int x = px + PHANTOM_SPAWN_SIDE_MIN + rng.nextInt(PHANTOM_SPAWN_SIDE_SPAN);
            int z = pz + PHANTOM_SPAWN_SIDE_MIN + rng.nextInt(PHANTOM_SPAWN_SIDE_SPAN);
            if (!validPhantomSpawnCell(world, x, y, z)) continue;
            int wanted = 1 + rng.nextInt(difficulty.phantomGroupSizeBound());
            for (int i = 0; i < wanted; i++) {
                if (hostiles + added >= hostileCap) break;
                // 원문은 무리 전원을 같은 칸에 겹쳐 놓는다(`moveTo($$11, 0, 0)` 반복).
                out.add(new SpawnRequest(MobType.PHANTOM, x + 0.5, y, z + 0.5));
                added++;
            }
        }
        return added;
    }

    /**
     * [PHANTOM] 바닐라 {@code NaturalSpawner.isValidEmptySpawnBlock} 자리. 팬텀은 비행 몹이라
     * 발판을 요구하지 않고 <b>그 칸 하나가 비어 있는지</b>만 본다. 유체·고체·경계 밖은 거절한다.
     */
    private static boolean validPhantomSpawnCell(MobWorldView world, int x, int y, int z) {
        if (!hasActiveColumn(world, x, z) || y <= Blocks.MIN_Y || y >= Blocks.MAX_Y) return false;
        return TerrainAccessor.inspectWithoutChunkActivation(
                () -> isEmptySpawnCell(world, x, y, z));
    }

    static boolean zombieHerdBiome(int biome) {
        return biome == 1 || biome == 3 || biome == 4 || biome == 5 || biome == 27
                || biome == 29 || biome == 32 || biome == 35 || biome == 36 || biome == 129
                || biome == 132 || biome == 155 || biome == 160 || biome == 177
                || biome == 185;
    }

    /** 종별 티어 굴림의 바이옴 술어. 살아 있는 원본이 사는 계열을 그대로 물려받는다. */
    static boolean zombieAnimalBiome(MobType type, int biome) {
        return switch (type) {
            // 염소: 바닐라 염소가 스폰하는 산악 계열 — meadow(177) · snowy_slopes(179) ·
            // jagged_peaks(180) · frozen_peaks(181) · stony_peaks(182).
            case ZOMBIE_GOAT -> biome == 177 || biome == 179 || biome == 180
                    || biome == 181 || biome == 182;
            // 여우: 바닐라 여우가 스폰하는 타이가·그로브 계열 — taiga(5) · snowy_taiga(30) ·
            // old_growth_pine_taiga(32) · old_growth_spruce_taiga(160) · grove(178).
            case ZOMBIE_FOX -> biome == 5 || biome == 30 || biome == 32 || biome == 160
                    || biome == 178;
            // 닭: 온대 평지·숲 계열 — plains(1) · forest(4) · birch_forest(27) ·
            // dark_forest(29) · sunflower_plains(129) · flower_forest(132) ·
            // old_growth_birch_forest(155) · cherry_grove(185).
            case ZOMBIE_CHICKEN -> biome == 1 || biome == 4 || biome == 27 || biome == 29
                    || biome == 129 || biome == 132 || biome == 155 || biome == 185;
            // 무덤 사슴: 그늘진 활엽수림 — forest(4) · birch_forest(27) · dark_forest(29) ·
            // flower_forest(132) · old_growth_birch_forest(155) · pale_garden(186).
            case CARRION_STAG -> biome == 4 || biome == 27 || biome == 29 || biome == 132
                    || biome == 155 || biome == 186;
            // 부패 멧돼지: 습하고 우거진 곳 — swamp(6) · jungle(21) · sparse_jungle(23) ·
            // dark_forest(29) · bamboo_jungle(168) · mangrove_swamp(184).
            case CARRION_BOAR -> biome == 6 || biome == 21 || biome == 23 || biome == 29
                    || biome == 168 || biome == 184;
            default -> false;
        };
    }

    /**
     * 낙타 husk 가 나오는 바이옴. 바닐라 원문이 "Desert" 하나라 사막(2)만이다 — 배들랜드나
     * 사바나로 넓히면 등급 A 근거를 넘어서는 창작이 된다.
     */
    static boolean camelHuskBiome(int biome) {
        return biome == 2;
    }

    /** 좀비 늑대가 나오는 바이옴. 늑대·갈색곰이 사는 숲/타이가 계열과 같은 목록이다. */
    static boolean zombieWolfBiome(int biome) {
        return brownBearBiome(biome);
    }

    static String rabbitVariant(int biome, int roll) {
        if (biome == 2) {
            return roll < 70 ? "gold" : roll < 90 ? "cinnamon" : "cream";
        }
        if (biome == 12 || biome == 30 || biome == 140
                || biome == 178 || biome == 179) {
            return roll < 20 ? "white_splotched"
                    : roll < 70 ? "white"
                    : roll < 85 ? "blue_gray"
                    : roll < 95 ? "cream"
                    : "harlequin";
        }
        return roll < 35 ? "brown"
                : roll < 60 ? "salt"
                : roll < 70 ? "black"
                : roll < 82 ? "cinnamon"
                : roll < 92 ? "cream"
                : roll < 97 ? "blue_gray"
                : "harlequin";
    }

    private static boolean validBatSpeciesRules(MobWorldView world,
                                                int x, int y, int z, MobRandom rng) {
        boolean halloween = halloweenBatWindow(LocalDate.now(ZoneOffset.UTC));
        return world.biomeAt(x, y, z) != 183
                && y <= world.surfaceHeight(x, z)
                && (halloween || rng.nextInt(2) == 0)
                && world.localBrightness(x, y, z) <= rng.nextInt(halloween ? 7 : 4)
                && isBatSpawnSupport(world.getBlock(x, y - 1, z) & 0xffff);
    }

    private static boolean validDrownedSpeciesRules(MobWorldView world, int x, int y, int z,
                                                     MobRandom rng) {
        if (!isWater(world.getBlock(x, y - 1, z))) return false;
        boolean dark = darkEnoughForMonster(world, x, y, z, rng);
        int biome = world.biomeAt(x, y, z);
        boolean frequent = biome == 7 || biome == 11;
        if (rng.nextInt(frequent ? 15 : 40) != 0) return false;
        return dark && (frequent || y < Blocks.SEA_LEVEL - 5);
    }

    private static boolean halloweenBatWindow(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        return month == 10 && day >= 20 || month == 11 && day <= 3;
    }

    private void trySpawnerBlocks(MobWorldView world, MobRandom rng, Collection<Mob> mobs,
                                  List<PlayerSnapshot> alive, List<SpawnRequest> out,
                                  Set<SpawnerPosition> indexedSpawners,
                                  LocalCapIndex localCaps) {
        for (SpawnerPosition position : indexedSpawners) {
            int bx = position.x, by = position.y, bz = position.z;
            int blockId = world.getBlock(bx, by, bz);
            if (blockId < Blocks.SPAWNER_BASE || blockId > Blocks.SPAWNER_BASE + 2) {
                spawnerRemainingTicks.remove(position);
                spawnerDefinitions.remove(position);
                continue;
            }
            int state = world.blockState(bx, by, bz, blockId);
            MobType type = SpawnerRules.mobType(state);
            if (type == null) {
                spawnerRemainingTicks.remove(position);
                spawnerDefinitions.remove(position);
                continue;
            }
            long definition = ((long) blockId << 32) | (state & 0xffff_ffffL);
            Long previousDefinition = spawnerDefinitions.put(position, definition);
            if (previousDefinition != null && previousDefinition.longValue() != definition) {
                spawnerRemainingTicks.remove(position);
            }
            if (!anyPlayerWithin(alive, bx + 0.5, by + 0.5, bz + 0.5, 16.0)) continue;
            Integer remaining = spawnerRemainingTicks.get(position);
            if (remaining == null) {
                spawnerRemainingTicks.put(position, SPAWNER_INITIAL_DELAY);
                continue;
            }
            if (remaining > 0) {
                remaining--;
                spawnerRemainingTicks.put(position, remaining);
                continue;
            }
            boolean spawned = false;
            boolean capped = false;
            for (int attempt = 0; attempt < SPAWNER_SPAWN_COUNT; attempt++) {
                double spawnX = bx + (rng.nextDouble() - rng.nextDouble())
                        * SPAWNER_SPAWN_RANGE + 0.5;
                int y = by + rng.nextInt(3) - 1;
                double spawnZ = bz + (rng.nextDouble() - rng.nextDouble())
                        * SPAWNER_SPAWN_RANGE + 0.5;
                int x = (int) Math.floor(spawnX);
                int z = (int) Math.floor(spawnZ);
                if (!hasActiveColumn(world, x, z)
                        || !validSpawnerSpot(world, type, spawnX, y, spawnZ, rng)) continue;
                MobType spawnedType = type == MobType.ZOMBIE ? pickZombieDungeonType(rng) : type;
                if (countSpawnerClassWithin(mobs, out, spawnedType,
                        bx, by, bz, SPAWNER_SPAWN_RANGE) >= SPAWNER_LOCAL_CAP) {
                    resetSpawnerDelay(position, rng);
                    capped = true;
                    break;
                }
                // The protocol has no yaw field, but consuming the vanilla yaw draw preserves RNG order.
                rng.nextFloat();
                if (!spawnerObstructionFree(world, spawnedType, spawnX, y, spawnZ)) continue;
                out.add(new SpawnRequest(spawnedType, spawnX, y, spawnZ));
                localCaps.add(spawnedType.category(), spawnX, spawnZ);
                spawned = true;
            }
            if (!capped && spawned) resetSpawnerDelay(position, rng);
        }
    }

    private void resetSpawnerDelay(SpawnerPosition position, MobRandom rng) {
        int span = SPAWNER_MAX_DELAY - SPAWNER_MIN_DELAY + 1;
        spawnerRemainingTicks.put(position, SPAWNER_MIN_DELAY + rng.nextInt(span));
    }

    /**
     * 동물은 거리와 무관하게 유지한다. 적대 몹은 128블록 밖에서 즉시, 32블록 밖에
     * 연속 600 Minecraft 논리 틱(30초) 머문 뒤 각 논리 틱마다 1/800 확률로 소멸한다.
     */
    public List<MobDespawn> despawns(Collection<Mob> mobs, MobWorldView world, MobRandom rng) {
        List<MobDespawn> out = null;
        List<PlayerSnapshot> players = alivePlayers(world);
        if (players.isEmpty()) return List.of();
        for (Mob m : mobs) {
            if (m.removed || m.isDead()) continue;
            // [WAVE-86-97] 체류 시간 만료는 거리 면제보다 앞선다. 행상인·트레이더 라마는
            // CREATURE 라 거리 소멸에서 영구 면제이므로, 만료가 없으면 한 번 나온 개체가
            // CREATURE cap 을 영원히 잠식한다(바닐라에는 그런 상태가 없다).
            if (m instanceof TimedDespawn timed
                    && timed.expireDespawnDelay(TimedDespawn.MC_TICKS_PER_DESPAWN_PASS)) {
                if (out == null) out = new ArrayList<>();
                // 어휘는 기존 것을 그대로 쓴다 — 거리와 무관한 자연 소멸이라 "random" 이다.
                out.add(new MobDespawn(m, "random"));
                continue;
            }
            if (exemptFromNaturalDespawn(m)) continue;
            double ndSq = nearestPlayerDistSq(m, players);
            double immediateDistance = immediateDespawnDistance(m.type);
            if (ndSq > immediateDistance * immediateDistance) {
                if (out == null) out = new ArrayList<>();
                out.add(new MobDespawn(m, "far"));
                continue;
            }
            if (ndSq <= RANDOM_DESPAWN_DIST * RANDOM_DESPAWN_DIST) {
                m.farDespawnTicks = 0;
                continue;
            }
            for (int logicalTick = 0; logicalTick < 2; logicalTick++) {
                if (m.farDespawnTicks > RANDOM_DESPAWN_DELAY
                        && rng.nextInt(RANDOM_DESPAWN_ROLL) == 0) {
                    if (out == null) out = new ArrayList<>();
                    out.add(new MobDespawn(m, "random"));
                    break;
                }
                m.farDespawnTicks++;
            }
        }
        return out == null ? List.of() : out;
    }

    private static double immediateDespawnDistance(MobType type) {
        return switch (type) {
            case COD, SALMON, TROPICAL_FISH -> WATER_AMBIENT_IMMEDIATE_DESPAWN_DIST;
            default -> IMMEDIATE_DESPAWN_DIST;
        };
    }

    /**
     * 자연 소멸(거리 기반 despawn 및 청크 hibernation cull)에서 제외되는 몹인지 판정한다.
     * 동물(passive)·철 골렘·이름표/길들임(persist)·땅에서 주운 장비 보유·보트 탑승·블록을 든 엔더맨은
     * 바닐라에서도 청크 언로드 시 저장·유지되므로 소멸 대상이 아니다.
     */
    static boolean exemptFromNaturalDespawn(Mob m) {
        return m.shouldPersist() || m.type.naturallyPersistent() || m.type == MobType.IRON_GOLEM
                || m.hasPickedUpEquipment() || m.isRidingBoat()
                || (m instanceof Enderman enderman && enderman.carriedBlock() != 0);
    }

    /**
     * 자리 검사. 마지막 관문의 상자는 <b>실제로 놓을 종</b>({@code type})의 것이다.
     *
     * <p>[HOSTILE-SPOT-AABB] 이 인자는 예전에 없었고 관문이 언제나
     * {@code MobType.PILLAGER}(0.6 × 1.95) 로 쟀다. 그 관문은 순찰자 굴림뿐 아니라 좀비 무리
     * 굴림({@link #tryZombieHerdPack})까지 공유하는데 그 굴림이 뽑는 3종은 치수가 다르다 —
     * {@code ZombieCowRules} 0.9 × 1.4 가 대표적이다. 그래서 폭 0.6 은 지나가지만 0.9 는
     * 지나가지 못하는 좁은 틈에 좀비 소가 박히고, 높이 1.4 면 충분한 낮은 천장을 1.95 로 재서
     * 자리를 통째로 버렸다. 두 방향 모두 자연 스폰 분포를 조용히 비튼다.
     *
     * <p>MC-REFERENCE: 바닐라 {@code NaturalSpawner.isValidSpawnPostitionForType} 은
     * {@code entityType.getSpawnAABB(x, y, z)} 로 <b>그 종의</b> 상자를 만들어
     * {@code level.noCollision(aabb)} 를 본다. 한 종의 치수를 전 종에 돌려쓰는 자리는
     * 바닐라에 없다. 정적판 {@code StandaloneMobRuntime.validHostileSpot} 은 처음부터
     * {@code type} 을 받아 {@code entityAabbCollisionFree(world, type, ...)} 로 쟀으므로,
     * 이 수정은 <b>정적판과의 정합</b>을 회복하는 것이기도 하다.
     *
     * <p><b>난수 정체성</b>: 이 관문은 난수를 소비하지 않는다({@code darkEnoughForMonster} 가
     * 소비를 끝낸 뒤에 온다). 그래서 상자를 종별로 바꿔도 <b>소비 개수·순서는 그대로</b>이고,
     * 갈리는 것은 굴림의 성공/실패뿐이다. 좀비 무리 굴림의 종 추첨({@code nextInt(3)})은
     * 자리 검사 <b>뒤</b>에 오는 계약이므로 추첨을 앞으로 당기지 않는다 — 대신 그 굴림은
     * 뽑힐 수 있는 3종 중 <b>가장 큰 상자</b>({@link #ZOMBIE_HERD_SPOT_TYPE})로 검사한다.
     * 그러면 어느 종이 뽑히든 실제로 들어갈 수 있고, 소비 순서도 지금과 글자 그대로 같다.
     */
    private boolean validHostileSpot(MobWorldView world, List<PlayerSnapshot> players,
                                     MobType type, int bx, int feetY, int bz, MobRandom rng) {
        if (feetY <= Blocks.MIN_Y || feetY >= Blocks.MAX_Y) return false;
        if (!onGroundPlacement(world, bx, feetY, bz)) return false;
        if (!validNaturalDistance(players, bx + 0.5, feetY, bz + 0.5)
                || !outsideWorldSpawn(world, bx + 0.5, feetY, bz + 0.5)) return false;
        if (!darkEnoughForMonster(world, bx, feetY, bz, rng)) return false;
        return collisionFreeAabb(world, type, bx + 0.5, feetY, bz + 0.5, true);
    }

    /**
     * [HOSTILE-SPOT-AABB] 혼성 무리 굴림이 자리 검사에 쓰는 종. 추첨({@link #herdSpecies})이
     * 뽑을 수 있는 소·돼지·양 중 <b>상자가 가장 큰</b> 좀비 소다 — 폭 0.9 × 높이 1.4 로
     * 돼지(0.9 × 0.9)·양(0.9 × 1.3)을 모두 감싼다. 가장 큰 상자로 재야 어느 종이 뽑혀도
     * 실제로 들어가고, 추첨을 자리 검사 앞으로 당기지 않아 난수 소비 순서가 보존된다.
     */
    static final MobType ZOMBIE_HERD_SPOT_TYPE = MobType.ZOMBIE_COW;

    /** 자연 스폰 후보는 이미 실제 플레이가 활성화한 청크에서만 검사한다. */
    private boolean confirmedHostileSpot(MobWorldView world, List<PlayerSnapshot> players,
                                         MobType type, int bx, int feetY, int bz, MobRandom rng) {
        if (!hasActiveColumn(world, bx, bz)) return false;
        return TerrainAccessor.inspectWithoutChunkActivation(
                () -> validHostileSpot(world, players, type, bx, feetY, bz, rng));
    }

    static boolean isWater(short block) {
        return Fluids.isWaterMedium(block & 0xffff);
    }

    private static boolean validNaturalDistance(List<PlayerSnapshot> players,
                                                double x, double y, double z) {
        boolean withinMaximum = false;
        double minSq = NATURAL_MIN_DIST * NATURAL_MIN_DIST;
        double maxSq = NATURAL_MAX_DIST * NATURAL_MAX_DIST;
        for (PlayerSnapshot p : players) {
            double d = sq(p.x() - x) + sq(p.y() - y) + sq(p.z() - z);
            if (d <= minSq) return false;
            if (d <= maxSq) withinMaximum = true;
        }
        return withinMaximum;
    }

    static boolean outsideWorldSpawn(MobWorldView world, double x, double y, double z) {
        if (world.endDimension()) return true; // The default respawn point belongs to the overworld.
        int[] spawn = world.worldSpawn();
        return spawn == null || spawn.length < 3
                || sq(spawn[0] + 0.5 - x) + sq(spawn[1] + 0.5 - y)
                        + sq(spawn[2] + 0.5 - z)
                        >= NATURAL_MIN_DIST * NATURAL_MIN_DIST;
    }

    static boolean withinLocalCaps(Collection<Mob> mobs, List<SpawnRequest> requests,
                                   List<PlayerSnapshot> players, MobCategory category,
                                   int cap, double x, double z) {
        int candidateChunkX = Math.floorDiv((int) Math.floor(x), 16);
        int candidateChunkZ = Math.floorDiv((int) Math.floor(z), 16);
        for (PlayerSnapshot player : players) {
            int playerChunkX = Math.floorDiv((int) Math.floor(player.x()), 16);
            int playerChunkZ = Math.floorDiv((int) Math.floor(player.z()), 16);
            if (Math.abs(candidateChunkX - playerChunkX) > 8
                    || Math.abs(candidateChunkZ - playerChunkZ) > 8) continue;
            int count = 0;
            for (Mob mob : mobs) {
                if (mob.type.category() != category || mob.shouldPersist()
                        || mob.isDead() || mob.removed) continue;
                int mobChunkX = Math.floorDiv((int) Math.floor(mob.x), 16);
                int mobChunkZ = Math.floorDiv((int) Math.floor(mob.z), 16);
                if (Math.abs(mobChunkX - playerChunkX) <= 8
                        && Math.abs(mobChunkZ - playerChunkZ) <= 8) count++;
            }
            for (SpawnRequest request : requests) {
                if (request.type().category() != category) continue;
                int requestChunkX = Math.floorDiv((int) Math.floor(request.x()), 16);
                int requestChunkZ = Math.floorDiv((int) Math.floor(request.z()), 16);
                if (Math.abs(requestChunkX - playerChunkX) <= 8
                        && Math.abs(requestChunkZ - playerChunkZ) <= 8) count++;
            }
            if (count < cap) return true;
        }
        return false;
    }

    /**
     * Per-player category counts for one natural-spawn tick. Candidate admission becomes
     * O(players) instead of rescanning every active mob and prior request for every valid
     * spawn position. Storage is retained by the world-owned spawner and resized only when
     * the connected-player high-water mark grows.
     */
    private static final class LocalCapIndex {
        private int[] playerChunkX = new int[2];
        private int[] playerChunkZ = new int[2];
        private int[][] counts = new int[2][MobCategory.values().length];
        private final int[] globalCounts = new int[MobCategory.values().length];
        private int playerCount;

        private LocalCapIndex reset(Collection<Mob> mobs, List<SpawnRequest> requests,
                                    List<PlayerSnapshot> players) {
            ensureCapacity(players.size());
            playerCount = players.size();
            Arrays.fill(globalCounts, 0);
            for (int index = 0; index < playerCount; index++) {
                PlayerSnapshot player = players.get(index);
                playerChunkX[index] = Math.floorDiv((int) Math.floor(player.x()), 16);
                playerChunkZ[index] = Math.floorDiv((int) Math.floor(player.z()), 16);
                Arrays.fill(counts[index], 0);
            }
            for (Mob mob : mobs) {
                if (mob.shouldPersist() || mob.isDead() || mob.removed) continue;
                add(mob.type.category(), mob.x, mob.z);
            }
            for (SpawnRequest request : requests) {
                add(request.type().category(), request.x(), request.z());
            }
            return this;
        }

        private boolean allows(MobCategory category, int cap, double x, double z) {
            int candidateChunkX = Math.floorDiv((int) Math.floor(x), 16);
            int candidateChunkZ = Math.floorDiv((int) Math.floor(z), 16);
            int categoryIndex = category.ordinal();
            for (int player = 0; player < playerCount; player++) {
                if (Math.abs(candidateChunkX - playerChunkX[player]) <= 8
                        && Math.abs(candidateChunkZ - playerChunkZ[player]) <= 8
                        && counts[player][categoryIndex] < cap) return true;
            }
            return false;
        }

        private void add(MobCategory category, double x, double z) {
            int chunkX = Math.floorDiv((int) Math.floor(x), 16);
            int chunkZ = Math.floorDiv((int) Math.floor(z), 16);
            int categoryIndex = category.ordinal();
            globalCounts[categoryIndex]++;
            for (int player = 0; player < playerCount; player++) {
                if (Math.abs(chunkX - playerChunkX[player]) <= 8
                        && Math.abs(chunkZ - playerChunkZ[player]) <= 8) {
                    counts[player][categoryIndex]++;
                }
            }
        }

        private int globalCount(MobCategory category) {
            return globalCounts[category.ordinal()];
        }

        private void ensureCapacity(int required) {
            if (required <= playerChunkX.length) return;
            int capacity = playerChunkX.length;
            while (capacity < required) capacity *= 2;
            playerChunkX = Arrays.copyOf(playerChunkX, capacity);
            playerChunkZ = Arrays.copyOf(playerChunkZ, capacity);
            counts = Arrays.copyOf(counts, capacity);
            for (int index = 0; index < capacity; index++) {
                if (counts[index] == null) counts[index] = new int[MobCategory.values().length];
            }
        }
    }

    private static boolean hasActiveColumn(MobWorldView world, int x, int z) {
        return world.isChunkActive(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    private static void countCategories(Collection<Mob> mobs, int[] counts) {
        Arrays.fill(counts, 0);
        for (Mob mob : mobs) {
            if (!mob.shouldPersist() && !mob.isDead() && !mob.removed) {
                counts[mob.type.category().ordinal()]++;
            }
        }
    }

    private Set<SpawnerPosition> findSpawnerBlocks(List<PlayerSnapshot> alive) {
        Set<SpawnerPosition> indexed = new LinkedHashSet<>();
        for (int[] c : spawnerScan.spawnerBlocks(alive)) {
            if (c != null && c.length == 3) {
                indexed.add(new SpawnerPosition(c[0], c[1], c[2]));
            }
        }
        for (int[] c : spawnerScan.removedSpawnerBlocks()) {
            if (c != null && c.length == 3) {
                SpawnerPosition position = new SpawnerPosition(c[0], c[1], c[2]);
                spawnerRemainingTicks.remove(position);
                spawnerDefinitions.remove(position);
            }
        }
        return indexed;
    }

    private static boolean isEmptySpawnCell(MobWorldView world, int x, int y, int z) {
        short b = world.getBlock(x, y, z);
        if (b < 0) return false;
        int id = b & 0xffff;
        if (Fluids.isWaterMedium(id) || Fluids.isLava(id)) return false;
        int state = world.blockState(x, y, z, id);
        return BuildingBlockRules.supportTop(id, state, x, z, 0.0, 1.0, 0.0, 1.0)
                <= BuildingBlockRules.collisionBottom(id, state) + 1.0e-6;
    }

    private static boolean anyPlayerWithin(List<PlayerSnapshot> players,
                                           double x, double y, double z, double r) {
        double r2 = r * r;
        for (PlayerSnapshot p : players) {
            double d = sq(p.x() - x) + sq(p.y() - y) + sq(p.z() - z);
            if (d <= r2) return true;
        }
        return false;
    }

    static int countSpawnerClassWithin(Collection<Mob> mobs,
                                       List<SpawnRequest> requests, MobType type,
                                       int blockX, int blockY, int blockZ, int range) {
        double minX = blockX - range;
        double minY = blockY - range;
        double minZ = blockZ - range;
        double maxX = blockX + 1.0 + range;
        double maxY = blockY + 1.0 + range;
        double maxZ = blockZ + 1.0 + range;
        int n = 0;
        for (Mob m : mobs) {
            if (!sameSpawnerClass(m.type, type) || m.isDead() || m.removed) continue;
            if (intersects(m.type, m.x, m.y, m.z,
                    minX, minY, minZ, maxX, maxY, maxZ)) n++;
        }
        for (SpawnRequest request : requests) {
            if (!sameSpawnerClass(request.type(), type)) continue;
            if (intersects(request.type(), request.x(), request.y(), request.z(),
                    minX, minY, minZ, maxX, maxY, maxZ)) n++;
        }
        return n;
    }

    private static boolean sameSpawnerClass(MobType candidate, MobType selectedType) {
        if (selectedType == MobType.ZOMBIE || selectedType == MobType.BABY_ZOMBIE) {
            return candidate == MobType.ZOMBIE || candidate == MobType.BABY_ZOMBIE;
        }
        return candidate == selectedType;
    }

    private static boolean intersects(MobType type, double x, double y, double z,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
        double half = type.width() * 0.5;
        return x + half > minX && x - half < maxX
                && y + type.height() > minY && y < maxY
                && z + half > minZ && z - half < maxZ;
    }

    private static double nearestPlayerDistSq(Mob m, List<PlayerSnapshot> players) {
        double best = Double.POSITIVE_INFINITY;
        for (PlayerSnapshot p : players) {
            double d = sq(p.x() - m.x) + sq(p.y() - m.y) + sq(p.z() - m.z);
            if (d < best) best = d;
        }
        return best;
    }

    private List<PlayerSnapshot> alivePlayers(MobWorldView world) {
        alivePlayerScratch.clear();
        for (PlayerSnapshot player : world.players()) {
            if (player.alive()) alivePlayerScratch.add(player);
        }
        return alivePlayerScratch;
    }

    private int collectSpawnableChunks(MobWorldView world, List<PlayerSnapshot> players) {
        int count = 0;
        if (players.size() == 1) {
            PlayerSnapshot player = players.getFirst();
            int centerX = Math.floorDiv((int) Math.floor(player.x()), 16);
            int centerZ = Math.floorDiv((int) Math.floor(player.z()), 16);
            for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (world.isChunkActive(chunkX, chunkZ)) {
                    count = appendSpawnableChunk(count, chunkKey(chunkX, chunkZ));
                }
            }
            return count;
        }
        spawnableChunkSeen.clear();
        for (PlayerSnapshot player : players) {
            int centerX = Math.floorDiv((int) Math.floor(player.x()), 16);
            int centerZ = Math.floorDiv((int) Math.floor(player.z()), 16);
            for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                long key = chunkKey(chunkX, chunkZ);
                if (world.isChunkActive(chunkX, chunkZ) && spawnableChunkSeen.add(key)) {
                    count = appendSpawnableChunk(count, key);
                }
            }
        }
        return count;
    }

    private int appendSpawnableChunk(int count, long key) {
        if (count == spawnableChunkScratch.length) {
            spawnableChunkScratch = Arrays.copyOf(spawnableChunkScratch, count * 2);
        }
        spawnableChunkScratch[count] = key;
        return count + 1;
    }

    static int scaledCap(int fullCap, int spawnableChunks) {
        return fullCap * spawnableChunks / VANILLA_SPAWNABLE_CHUNK_AREA;
    }

    private static double sq(double v) { return v * v; }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffff_ffffL);
    }

    private static final class SpawnerPosition {
        private final int x;
        private final int y;
        private final int z;

        private SpawnerPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SpawnerPosition position)) return false;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override public int hashCode() {
            int result = Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(y);
            return 31 * result + Integer.hashCode(z);
        }
    }
}
