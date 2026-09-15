package com.gameexpert.engine.mob;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 스포너가 낸 소환 요청. 좌표는 발밑 중심(블록 중앙 x+0.5, z+0.5). */
@Getter
@Accessors(fluent = true)
public class SpawnRequest {

    private final MobType type;
    private final double x;
    private final double y;
    private final double z;
    /** 자연 소 무리에서 실제로 성립한 첫 개체인지 여부. */
    private final boolean firstCowInHerd;
    /** 바이옴이 확정한 서버 권위 외형. 변종이 없는 종류는 null. */
    private final String variant;
    /** Null for ordinary natural/spawner/population requests. */
    private final IllagerCompanionPolicy.Context companionContext;
    private final long companionContextIdentity;
    /** 1-based wave that released this raider; 0 outside a raid. */
    private final int raidWave;
    /** True only for an ordinary natural Drowned outside river/frozen-river biomes. */
    private final boolean naturalDrownedJockeyEligible;
    /** Existing winning Drowned passenger for an internally planned Zombie Nautilus vehicle. */
    private final long drownedJockeyPassengerMobId;
    /** 26.3 natural Zombie Horse finalizeSpawn creates a Zombie rider with an iron spear. */
    private final boolean naturalZombieHorseJockey;
    /**
     * [TRIAL-GAP] 트라이얼 스포너의 spawn_potentials 가 정한 슬라임 크기(2 · 3). 0 은 종 기본값이다.
     */
    private final int slimeSize;
    /**
     * [TRIAL-GAP] 불길한 설정의 장비 표(equipment/trial_chamber_*)가 고른 스택들. 바닐라
     * {@code slot_drop_chances 0.0} 이라 이 칸들은 사망해도 떨어지지 않는다. 빈 목록은 장비 없음이다.
     */
    private final java.util.List<com.gameexpert.engine.trial.TrialLootTables.Stack> trialEquipment;
    /** [TRIAL-GAP] 바닐라 {@code TrialSpawner.spawnMob} 의 {@code setPersistenceRequired()}. */
    private final boolean persistenceRequired;

    public SpawnRequest(MobType type, double x, double y, double z) {
        this(type, x, y, z, false, null);
    }

    public SpawnRequest(MobType type, double x, double y, double z,
                        boolean firstCowInHerd, String variant) {
        this(type, x, y, z, firstCowInHerd, variant, null, 0L, 0, false, 0L, false);
    }

    public SpawnRequest(MobType type, double x, double y, double z,
                        IllagerCompanionPolicy.Context companionContext,
                        long companionContextIdentity) {
        this(type, x, y, z, false, null, companionContext, companionContextIdentity, 0, false, 0L,
                false);
    }

    public SpawnRequest(MobType type, double x, double y, double z,
                        IllagerCompanionPolicy.Context companionContext,
                        long companionContextIdentity, int raidWave) {
        this(type, x, y, z, false, null, companionContext, companionContextIdentity, raidWave,
                false, 0L, false);
    }

    static SpawnRequest naturalDrowned(double x, double y, double z, int biome) {
        return new SpawnRequest(MobType.DROWNED, x, y, z, false, null,
                null, 0L, 0, biome != 7 && biome != 11, 0L, false);
    }

    static SpawnRequest zombieNautilusJockey(Drowned passenger) {
        return new SpawnRequest(MobType.ZOMBIE_NAUTILUS,
                passenger.x, passenger.y, passenger.z, false, null,
                null, 0L, 0, false, passenger.id, false);
    }

    static SpawnRequest naturalZombieHorse(double x, double y, double z) {
        return new SpawnRequest(MobType.ZOMBIE_HORSE, x, y, z, false, null,
                null, 0L, 0, false, 0L, true);
    }

    /**
     * Raid membership is carried by the request itself so the runtime can brand the materialized
     * mob inside the same transaction that creates it. Only {@code RAID} requests own a raid id.
     */
    public long raidId() {
        return companionContext == IllagerCompanionPolicy.Context.RAID
                ? companionContextIdentity : 0L;
    }

    private SpawnRequest(MobType type, double x, double y, double z,
                        boolean firstCowInHerd, String variant,
                        IllagerCompanionPolicy.Context companionContext,
                        long companionContextIdentity, int raidWave,
                        boolean naturalDrownedJockeyEligible,
                        long drownedJockeyPassengerMobId,
                        boolean naturalZombieHorseJockey) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.firstCowInHerd = firstCowInHerd;
        this.variant = variant;
        this.companionContext = companionContext;
        this.companionContextIdentity = companionContextIdentity;
        this.raidWave = raidWave;
        this.naturalDrownedJockeyEligible = naturalDrownedJockeyEligible;
        this.drownedJockeyPassengerMobId = drownedJockeyPassengerMobId;
        this.naturalZombieHorseJockey = naturalZombieHorseJockey;
        this.slimeSize = 0;
        this.trialEquipment = java.util.List.of();
        this.persistenceRequired = false;
    }

    /**
     * [TRIAL-GAP] 트라이얼 스포너 소환 한 마리. 슬라임 크기·장비·지속성을 요청 자체가 실어 오므로
     * 실체화가 같은 트랜잭션 안에서 몹을 완성한다.
     */
    public static SpawnRequest trial(MobType type, double x, double y, double z, int slimeSize,
            java.util.List<com.gameexpert.engine.trial.TrialLootTables.Stack> equipment) {
        return new SpawnRequest(type, x, y, z, slimeSize,
                equipment == null ? java.util.List.of() : java.util.List.copyOf(equipment));
    }

    private SpawnRequest(MobType type, double x, double y, double z, int slimeSize,
            java.util.List<com.gameexpert.engine.trial.TrialLootTables.Stack> trialEquipment) {
        if (slimeSize != 0 && (type != MobType.SLIME || slimeSize < 1)) {
            throw new IllegalArgumentException("trial slime size is only valid for slimes");
        }
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.firstCowInHerd = false;
        this.variant = null;
        this.companionContext = null;
        this.companionContextIdentity = 0L;
        this.raidWave = 0;
        this.naturalDrownedJockeyEligible = false;
        this.drownedJockeyPassengerMobId = 0L;
        this.naturalZombieHorseJockey = false;
        this.slimeSize = slimeSize;
        this.trialEquipment = trialEquipment;
        this.persistenceRequired = true;
    }
}
