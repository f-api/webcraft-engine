package com.gameexpert.engine.trial;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.raid.RaidLedger;

/**
 * [TRIAL] 트라이얼 스포너 계약. 월드·JPA·프로토콜 타입을 하나도 참조하지 않아 정적판 Worker 가
 * 한 줄씩 사본을 유지할 수 있다({@code StandaloneTrialSpawner.ts}) — {@link RaidLedger} 와 같은
 * 이유의 같은 설계다.
 *
 * <p><b>상태 기계</b>는 바닐라 {@code TrialSpawnerState} 여섯 상태 그대로다(26.3-snapshot-7
 * 클라이언트 jar javap): INACTIVE · WAITING_FOR_PLAYERS · ACTIVE · WAITING_FOR_REWARD_EJECTION ·
 * EJECTING_REWARD · COOLDOWN. 전이 조건·시각은 {@link TrialSpawnerRuntime} 이 소유하고 여기서는
 * 상태의 값(광량·렌더 비트)과 소환 설정·보상 추첨만 정한다.
 *
 * <p><b>무엇을 재사용하는가</b>: 소환 회계는 {@link RaidLedger} 를 그대로 쓴다. 한 마리가 곧
 * 원장의 "웨이브" 하나이고(웨이브 수 = 바닐라 {@code calculateTargetTotalMobs}), 명단이 비고
 * 모든 웨이브가 나오면 VICTORY 다 — 바닐라 {@code hasFinishedSpawningAllMobs &&
 * haveAllCurrentMobsDied} 와 같은 조건이다.
 */
public final class TrialSpawnerContract {

    private TrialSpawnerContract() {}

    /** 블록 상태의 상태 코드 비트(0..2). */
    public static final int STATE_MASK = 0x07;
    /** 블록 상태의 바닐라 {@code ominous} 비트. */
    public static final int OMINOUS = 0x08;

    /**
     * 바닐라 {@code TrialSpawnerState}. 선언 순서는 바닐라 서수이고, 블록 상태에 싣는
     * {@link #stateBits()} 는 옛 저장(0 idle · 1 active · 2 보상 대기 · 3 배출)과 호환되도록
     * 따로 정한 코드다 — 구조물 carrier 의 {@code waiting_for_players} 도 0 으로 투영된다.
     * 광량은 바닐라 생성자 인자({@code static {}}: 0 · 4 · 8 · 8 · 8 · 0)다.
     */
    public enum State {
        INACTIVE("inactive", 4, 0, false),
        WAITING_FOR_PLAYERS("waiting_for_players", 0, 4, true),
        ACTIVE("active", 1, 8, true),
        WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 2, 8, false),
        EJECTING_REWARD("ejecting_reward", 3, 8, false),
        COOLDOWN("cooldown", 5, 0, false);

        private final String serializedName;
        private final int stateBits;
        private final int lightLevel;
        private final boolean capableOfSpawning;

        State(String serializedName, int stateBits, int lightLevel, boolean capableOfSpawning) {
            this.serializedName = serializedName;
            this.stateBits = stateBits;
            this.lightLevel = lightLevel;
            this.capableOfSpawning = capableOfSpawning;
        }

        /** 블록 상태 하위 3비트 코드. {@code p20-trial.ts} 가 이 코드로 모델을 고른다. */
        public int stateBits() { return stateBits; }
        /** 바닐라 {@code TrialSpawnerState.lightLevel()}. */
        public int lightLevel() { return lightLevel; }
        /** 바닐라 blockstate 속성 값({@code trial_spawner_state}). */
        public String serializedName() { return serializedName; }
        /** 바닐라 {@code isCapableOfSpawning} — 주변 소리(ambient)가 이 상태에서만 난다. */
        public boolean capableOfSpawning() { return capableOfSpawning; }

        /** 블록 상태 코드 → 상태. 범위 밖(6·7)은 null 이다. */
        public static State fromBits(int blockState) {
            int bits = blockState & STATE_MASK;
            for (State state : values()) if (state.stateBits == bits) return state;
            return null;
        }

        /** 바닐라 속성 값 → 상태. 모르면 null. */
        public static State fromSerializedName(String name) {
            for (State state : values()) if (state.serializedName.equals(name)) return state;
            return null;
        }
    }

    /** 블록 상태 하나의 광량. 범위 밖 코드는 0 이다(정규화가 그 코드를 0 으로 되돌린다). */
    public static int lightLevel(int blockState) {
        State state = State.fromBits(blockState);
        return state == null ? 0 : state.lightLevel;
    }

    /** 영속·조작 입력을 유효한 상태 어휘로 접는다. 범위 밖 코드는 대기(0)다. */
    public static int normalizeState(int blockState) {
        int bits = blockState & STATE_MASK;
        if (State.fromBits(bits) == null) bits = State.WAITING_FOR_PLAYERS.stateBits();
        return bits | (blockState & OMINOUS);
    }

    /**
     * 활성 감지 반경(블록). 바닐라 {@code TrialSpawner.DEFAULT_PLAYER_SCAN_RANGE} 14.
     * 제곱 비교는 호출자가 한다.
     */
    public static final double ACTIVATION_RANGE = 14.0;
    /** 바닐라 {@code TrialSpawnerConfig} 기본 {@code spawn_range} 4. */
    public static final int SPAWN_RANGE = 4;
    /** 바닐라 {@code target_cooldown_length} 기본 36000 MC 틱 = 권위 18000 틱. */
    public static final int COOLDOWN_TICKS = 18_000;
    /** 첫 감지 뒤 첫 소환까지: 바닐라 {@code DETECT_PLAYER_SPAWN_BUFFER} 40 MC 틱. */
    public static final int DETECT_PLAYER_SPAWN_BUFFER_TICKS = 20;
    /** 승리 뒤 셔터가 열리기까지: {@code isReadyToOpenShutter(level, 40.0F, ...)} 40 MC 틱. */
    public static final int OPEN_SHUTTER_DELAY_TICKS = 20;
    /** 배출 간격: {@code TIME_BETWEEN_EACH_EJECTION = Mth.floor(30.0F)} 30 MC 틱. */
    public static final int TICKS_BETWEEN_EJECTIONS = 15;
    /** 불길해진 스포너의 아이템 소환기 간격: {@code ticksBetweenItemSpawners()} 160 MC 틱. */
    public static final int TICKS_BETWEEN_ITEM_SPAWNERS = 80;
    /** 플레이어 감지 주기: {@code DELAY_BETWEEN_PLAYER_SCANS} 20 MC 틱. */
    public static final int PLAYER_SCAN_PERIOD_MC_TICKS = 20;
    /** 불길한 징조 → 시련의 징조 환산: {@code TRIAL_OMEN_PER_BAD_OMEN_LEVEL} 18000 MC 틱. */
    public static final int TRIAL_OMEN_TICKS_PER_BAD_OMEN_LEVEL = 9_000;

    /** Bad Omen 앰프 → Trial Omen 지속(권위 틱). {@code 18000 * (amplifier + 1)} MC 틱이다. */
    public static int trialOmenDurationTicks(int badOmenAmplifier) {
        return TRIAL_OMEN_TICKS_PER_BAD_OMEN_LEVEL * (Math.max(0, badOmenAmplifier) + 1);
    }

    /**
     * 바닐라 {@code TrialSpawnerStateData.tryDetectPlayers} 의 주기 문턱:
     * {@code (pos.asLong() + gameTime) % 20 == 0}. 권위 틱 하나가 MC 틱 {@code 2t}·{@code 2t+1}
     * 을 담으므로 둘 중 하나가 문턱이면 이 틱에 감지한다(정확히 권위 10 틱에 한 번).
     */
    public static boolean isPlayerScanTick(int x, int y, int z, long worldTick) {
        long packed = packedBlockPos(x, y, z);
        long first = packed + worldTick * 2;
        long rem = Math.floorMod(first, (long) PLAYER_SCAN_PERIOD_MC_TICKS);
        return rem == 0 || rem == PLAYER_SCAN_PERIOD_MC_TICKS - 1;
    }

    /** 바닐라 {@code BlockPos.asLong}: x 26비트 &lt;&lt; 38 · z 26비트 &lt;&lt; 12 · y 12비트. */
    public static long packedBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FF_FFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FF_FFFFL) << 12;
    }

    /**
     * 소환 설정 한 벌. 바닐라 {@code TrialSpawnerConfig} 의 다섯 수치이며, 소환 간격은 MC 틱을
     * 권위 틱(10 TPS)으로 옮긴 값이다.
     */
    public record MobConfig(float totalMobs, float simultaneousMobs, float totalMobsAddedPerPlayer,
            float simultaneousMobsAddedPerPlayer, int ticksBetweenSpawn) {

        /** 바닐라 {@code calculateTargetTotalMobs(additionalPlayers)}. */
        public int targetTotalMobs(int additionalPlayers) {
            return (int) Math.floor(totalMobs + totalMobsAddedPerPlayer * additionalPlayers);
        }

        /** 바닐라 {@code calculateTargetSimultaneousMobs(additionalPlayers)}. */
        public int targetSimultaneousMobs(int additionalPlayers) {
            return (int) Math.floor(simultaneousMobs
                    + simultaneousMobsAddedPerPlayer * additionalPlayers);
        }
    }

    /** 바닐라 {@code countAdditionalPlayers}: 감지 플레이어 수 − 1(최소 0). */
    public static int additionalPlayers(int detectedPlayers) {
        return Math.max(0, detectedPlayers - 1);
    }

    /**
     * 역할 → 바닐라 {@code data/minecraft/trial_spawner/trial_chamber/**} 설정(normal · ominous).
     * 빈 필드는 {@code TrialSpawnerConfig$Builder} 기본값(total 6 · simultaneous 2 · +2 · +1 ·
     * 40 MC 틱)이다. 저장소의 역할 풀은 구조물 설정 키 대신 좌표 시드로 종을 고르므로 각 역할이
     * 그 종의 바닐라 설정을 대표한다(좀비·허스크·거미 → melee, 해골·스트레이 → ranged, 동굴거미·
     * 슬라임 → small_melee, 브리즈 → breeze).
     */
    public static MobConfig mobConfig(TrialSpawnerPool.Role role, boolean ominous) {
        return switch (role) {
            // melee/{zombie,husk}/{normal,ominous}.json · ranged/{skeleton,stray}: 둘 다 같은 수치.
            case ZOMBIE, HUSK, SKELETON, STRAY -> new MobConfig(6f, 3f, 2f, 0.5f, 10);
            // melee/spider · small_melee/{cave_spider,slime}: ominous 는 simultaneous 4 · total 12.
            case SPIDER, CAVE_SPIDER, SLIME -> ominous
                    ? new MobConfig(12f, 4f, 2f, 0.5f, 10)
                    : new MobConfig(6f, 3f, 2f, 0.5f, 10);
            // breeze/normal: simultaneous 1 · +0.5 · total 2 · +1. ominous: simultaneous 기본 2 · total 4.
            case BREEZE -> ominous
                    ? new MobConfig(4f, 2f, 1f, 0.5f, 10)
                    : new MobConfig(2f, 1f, 1f, 0.5f, 10);
        };
    }

    /** 스포너 좌표에서 유도한 안정 시드. 같은 좌표는 언제나 같은 종·같은 배치를 낸다. */
    public static long spawnerSeed(long worldSeed, int x, int y, int z) {
        long hash = worldSeed * 0x9E3779B97F4A7C15L;
        hash ^= (long) x * 0x165667B19E3779F9L;
        hash ^= (long) y * 0x27D4EB2F165667C5L;
        hash ^= (long) z * 0x2545F4914F6CDD1DL;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        return hash ^ hash >>> 33;
    }

    /** 이 스포너의 시련 identity. {@link RaidLedger} 가 같은 값을 raidId 로 받는다. */
    public static long trialId(long worldSeed, int x, int y, int z) {
        return spawnerSeed(worldSeed, x, y, z);
    }

    /** 한 마리의 소환 요청. 종은 역할 서수로만 말한다. {@code wave} 는 1-기반 소환 번호다. */
    public record Summon(TrialSpawnerPool.Role role, int x, int y, int z, int wave) {}

    /**
     * {@code spawnIndex} 번째 소환의 후보 좌표. 바닐라 {@code spawnMob} 은 시도마다 무작위 좌표를
     * 새로 뽑으므로 {@code attemptTick} 을 lane 에 섞는다 — 지형이 거부한 후보가 다음 틱에 같은
     * 자리로 되풀이되지 않는다. 호출자는 좌표가 몹을 받을 수 있는지 자기 권위로 확인한다.
     */
    public static Summon summon(long spawnerSeed, int spawnerX, int spawnerY, int spawnerZ,
            int spawnIndex, long attemptTick) {
        TrialSpawnerPool.Role role = TrialSpawnerPool.select(spawnerSeed);
        long lane = spawnerSeed ^ (long) spawnIndex * 0x632BE5AB ^ attemptTick * 0x85157AF5;
        lane ^= lane >>> 29;
        lane *= 0xBF58476D1CE4E5B9L;
        lane ^= lane >>> 32;
        int span = SPAWN_RANGE * 2 + 1;
        int dx = (int) Long.remainderUnsigned(lane, span) - SPAWN_RANGE;
        int dz = (int) Long.remainderUnsigned(lane >>> 21, span) - SPAWN_RANGE;
        return new Summon(role, spawnerX + dx, spawnerY, spawnerZ + dz, spawnIndex);
    }

    // ── [TRIAL-GAP] 소환 데이터(spawn_potentials · equipment) ────────────────────────────
    // 바닐라는 소환마다 spawn_potentials 를 레벨 난수로 다시 뽑고(getOrCreateNextSpawnData →
    // 성공한 소환 뒤 lambda$tickAndGetNext$0), Mob.equip 도 레벨 난수로 장비 표를 굴린다. 이
    // 저장소는 두 권위가 같은 몹을 내도록 (시련, 무장 시각, 소환 번호)에서 유도한 결정론 시드를 쓴다.

    /** 바닐라 {@code TrialSpawner$FlameParticle} 서수(level event 3011·3012 의 data). */
    public static final int FLAME_NORMAL = 0;
    public static final int FLAME_OMINOUS = 1;

    /**
     * 슬라임 설정(small_melee/slime/{normal,ominous}.json)의 spawn_potentials: {@code Size:1}(가중치 3) ·
     * {@code Size:2}(가중치 1). 바닐라 {@code AbstractCubeMob.readAdditionalSaveData} 가
     * {@code setSize(Size + 1)} 이므로 실제 크기는 2 · 3 이다. 엔티티 NBT 가 id 만이 아니어서
     * {@code finalizeSpawn} 이 불리지 않고 이 크기가 그대로 남는다.
     */
    public static int slimeSize(long trialId, long armedTick, int spawnIndex) {
        return draw(spawnDataSeed(trialId, armedTick, spawnIndex), 0, 4) < 3 ? 2 : 3;
    }

    /**
     * 장비 표 시드. 불길한 근접 설정(zombie · husk)은 equipment/trial_chamber_melee, 원거리 설정
     * (skeleton · stray)은 trial_chamber_ranged 를 {@code slot_drop_chances 0.0} 으로 입힌다.
     */
    public static long equipmentSeed(long trialId, long armedTick, int spawnIndex) {
        return spawnDataSeed(trialId, armedTick, spawnIndex) ^ 0x2F1C_7A93_5E0B_D461L;
    }

    /** 이 역할의 불길한 설정이 장비를 입히는가(바닐라 trial_chamber/&#42;/ominous.json). */
    public static boolean equipsWhenOminous(TrialSpawnerPool.Role role) {
        return role == TrialSpawnerPool.Role.ZOMBIE || role == TrialSpawnerPool.Role.HUSK
                || role == TrialSpawnerPool.Role.SKELETON || role == TrialSpawnerPool.Role.STRAY;
    }

    /** 원거리 장비 표(trial_chamber_ranged)를 쓰는 역할인가. */
    public static boolean rangedEquipment(TrialSpawnerPool.Role role) {
        return role == TrialSpawnerPool.Role.SKELETON || role == TrialSpawnerPool.Role.STRAY;
    }

    static long spawnDataSeed(long trialId, long armedTick, int spawnIndex) {
        return rewardSeed(trialId ^ 0x51A4_E0C7_33D9_1B85L, armedTick * 31 + spawnIndex);
    }

    // ── 보상 배출 ──────────────────────────────────────────────────────────────
    // 바닐라: EJECTING_REWARD 가 처음 배출할 때 {@code lootTablesToEject().getRandom} 으로 표를
    // 하나 고르고(한 시련 동안 고정), 감지 플레이어마다 그 표를 한 번씩 굴린다. 추첨 RNG 는
    // 바닐라 random sequence 가 아니라 이 저장소의 시드 draw 다(양 권위가 같은 값을 낸다).

    /** 배출 한 번의 결과. {@code itemType == 0} 은 저장소에 없는 항목이 뽑힌 것이다. */
    public record Reward(short itemType, int count) {
        public boolean isNothing() { return itemType == PlayerInventory.EMPTY || count <= 0; }
    }

    public static final Reward NO_REWARD = new Reward((short) PlayerInventory.EMPTY, 0);

    /** 표 선택 결과. 바닐라 키: spawners/trial_chamber/{key,consumables} · ominous/… */
    public enum LootTable { CONSUMABLES, KEY, OMINOUS_KEY, OMINOUS_CONSUMABLES }

    /** 한 항목: 아이템(없으면 0) · 가중치 · 개수 범위. */
    private record Entry(short itemType, int weight, int minCount, int maxCount) {}

    /**
     * spawners/trial_chamber/consumables.json 순서 그대로. [TRIAL-GAP] 재생의 물약
     * (minecraft:potion[regeneration])이 {@link PlayerInventory#POTION_REGENERATION} 로 생겨 그 칸이
     * 실제 아이템을 배출한다.
     */
    private static final Entry[] CONSUMABLES = {
        new Entry(PlayerInventory.CHICKEN_COOKED, 3, 1, 1),
        new Entry(PlayerInventory.BREAD, 3, 1, 3),
        new Entry(PlayerInventory.BAKED_POTATO, 2, 1, 3),
        new Entry(PlayerInventory.POTION_REGENERATION, 1, 1, 1),
        new Entry(PlayerInventory.POTION_SWIFTNESS, 1, 1, 1),
    };
    /** spawners/ominous/trial_chamber/consumables.json 순서 그대로(재생의 물약 포함). */
    private static final Entry[] OMINOUS_CONSUMABLES = {
        new Entry(PlayerInventory.BEEF_COOKED, 3, 1, 2),
        new Entry(PlayerInventory.BAKED_POTATO, 3, 2, 4),
        new Entry(PlayerInventory.GOLDEN_CARROT, 2, 1, 2),
        new Entry(PlayerInventory.POTION_REGENERATION, 1, 1, 1),
        new Entry(PlayerInventory.POTION_STRENGTH, 1, 1, 1),
    };

    /**
     * 표 선택. 일반은 {@code TrialSpawnerConfig$Builder} 기본 {@code [consumables 1, key 1]},
     * 불길은 trial_chamber/&#42;/ominous.json 의 {@code [ominous/key 3, ominous/consumables 7]} 이다.
     */
    public static LootTable ejectedTable(long trialId, long armedTick, boolean ominous) {
        long seed = rewardSeed(trialId, armedTick);
        if (ominous) {
            return draw(seed, 0, 10) < 3 ? LootTable.OMINOUS_KEY : LootTable.OMINOUS_CONSUMABLES;
        }
        return draw(seed, 0, 2) < 1 ? LootTable.CONSUMABLES : LootTable.KEY;
    }

    /** 배출 {@code ordinal}(0-기반, 감지 플레이어 순번) 하나가 주는 것. */
    public static Reward ejectedReward(long trialId, long armedTick, boolean ominous, int ordinal) {
        LootTable table = ejectedTable(trialId, armedTick, ominous);
        return switch (table) {
            case KEY -> new Reward(PlayerInventory.TRIAL_KEY, 1);
            case OMINOUS_KEY -> new Reward(PlayerInventory.OMINOUS_TRIAL_KEY, 1);
            case CONSUMABLES -> rollEntries(CONSUMABLES, rewardSeed(trialId, armedTick), ordinal);
            case OMINOUS_CONSUMABLES ->
                    rollEntries(OMINOUS_CONSUMABLES, rewardSeed(trialId, armedTick), ordinal);
        };
    }

    private static Reward rollEntries(Entry[] entries, long seed, int ordinal) {
        int totalWeight = 0;
        for (Entry entry : entries) totalWeight += entry.weight;
        int pick = draw(seed, 1 + ordinal * 2, totalWeight);
        for (Entry entry : entries) {
            pick -= entry.weight;
            if (pick >= 0) continue;
            if (entry.itemType == PlayerInventory.EMPTY) return NO_REWARD;
            int spread = entry.maxCount - entry.minCount + 1;
            int count = entry.minCount + draw(seed, 2 + ordinal * 2, spread);
            return new Reward(entry.itemType, count);
        }
        return NO_REWARD;
    }

    static long rewardSeed(long trialId, long armedTick) {
        long hash = trialId ^ armedTick * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        return hash ^ hash >>> 33;
    }

    static int draw(long seed, int drawIndex, int bound) {
        long z = seed + 0x9E3779B97F4A7C15L * (drawIndex + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (int) Long.remainderUnsigned(z, bound);
    }
}
