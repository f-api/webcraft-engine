package com.gameexpert.engine.dragon;

import java.util.List;

/**
 * [DRAGON] 드래곤 두뇌({@link DragonBrain})가 세계를 읽고 바꾸는 유일한 포트. 권위(Spring
 * {@code DragonFight})가 구현하고, 순수 테스트는 가짜 세계를
 * 넣는다. 모든 좌표는 월드 좌표이고 모든 호출은 바닐라 20 TPS 한 틱 안에서 일어난다.
 */
public interface DragonWorld {
    /**
     * {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} 의 {@code getHeightmapPos(x, 0, z).getY()} — 가장 높은
     * 이동 차단(잎 제외) 블록 바로 위 칸의 y. 비어 있는 열은 월드 최저 y.
     */
    int heightNoLeaves(int x, int z);

    /** {@code Heightmap.Types.MOTION_BLOCKING} 의 같은 값. */
    int heightMotionBlocking(int x, int z);

    /** 블록 ID(공기 0). 비상주 칸은 음수다 — 두뇌는 비상주 칸을 공기로 보지 않고 건너뛴다. */
    int block(int x, int y, int z);

    /** {@code ServerLevel.removeBlock(pos, false)} — 바뀌었으면 true(드래곤 몸통의 블록 파괴). */
    boolean removeBlock(int x, int y, int z);

    /** 엔드 차원 모든 플레이어(순서는 권위의 연결 순서로 고정). */
    List<DragonPlayer> players();

    /** {@code Entity.hasLineOfSight}: 두 눈높이 사이 COLLIDER 광선이 블록에 막히지 않는가(128 블록 상한 포함). */
    boolean lineOfSight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);

    /**
     * 상자 {@code (minX..maxX, minY..maxY, minZ..maxZ)} 와 겹치는 살아 있는 개체({@code LivingEntity},
     * {@code EntitySelector.NO_CREATIVE_OR_SPECTATOR}). 드래곤 자신과 엔드 수정은 넣지 않는다.
     */
    List<DragonVictim> livingEntitiesIn(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ);

    /** {@code Entity.push(dx, dy, dz)} — 틱당 속도 가산(블록/틱). */
    void push(DragonVictim victim, double dx, double dy, double dz);

    /** 드래곤의 {@code mobAttack} 피해. 난이도 보정·피격 무적·방어구는 권위의 피해 경로가 적용한다. */
    void hurtByDragon(DragonVictim victim, float amount);

    /** 반경 32 로 부푼 드래곤 상자 안의 엔드 수정({@code checkCrystals}). */
    List<DragonCrystal> crystalsNear(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ);

    /** 이 id 의 엔드 수정이 아직 원장에 있는가({@code nearestCrystal.isRemoved()} 의 반대). */
    boolean crystalAlive(long crystalMobId);

    /** {@code new DragonFireball(level, dragon, dir.normalize())} 를 (x, y, z) 에 둔다. */
    void spawnFireball(double x, double y, double z, double dirX, double dirY, double dirZ);

    /**
     * {@code DragonSittingFlamingPhase} 의 화염 구름: 반지름 5 · 지속 200 · 즉시 피해 I · 드래곤 숨결 입자.
     * @return 구름 id({@link #discardCloud} 로 지운다)
     */
    long spawnSittingFlame(double x, double y, double z);

    void discardCloud(long cloudId);

    /** {@code ServerLevel.levelEvent(event, pos, data)}. {@code global} 이면 {@code globalLevelEvent}. */
    void levelEvent(int event, int x, int y, int z, int data, boolean global);

    /** {@code ExperienceOrb.award(level, pos, amount)}. */
    void awardExperience(double x, double y, double z, int amount);

    /** {@code GameRules.MOB_GRIEFING}. */
    boolean mobGriefing();

    /** 엔드 차원 드래곤전의 살아 있는 수정 수({@code EnderDragonFight.aliveCrystals}); 싸움이 없으면 -1. */
    int aliveCrystals();

    /** {@code EnderDragonFight.hasPreviouslyKilledDragon}; 싸움이 없으면 true 로 본다(보상 500). */
    boolean previouslyKilledDragon();

    /** 드래곤이 사망 연출을 마쳤다({@code EnderDragonFight.setDragonKilled} 후 제거). */
    void dragonKilled();

    /**
     * 플레이어 한 명의 두뇌용 사실. {@code alive} 는 {@code ENTITY_STILL_ALIVE}(보스바), {@code attackable} 은
     * {@code TargetingConditions.forCombat}(살아 있고 창작·관전이 아니며 평화 난이도가 아님).
     */
    record DragonPlayer(String nickname, double x, double y, double z, double eyeY,
            boolean alive, boolean attackable, boolean crouching) {
    }

    /** 밀치기·피해 대상(플레이어면 nickname, 몹이면 mobId). */
    record DragonVictim(String nickname, long mobId, double x, double y, double z) {
        public String key() {
            return nickname != null ? "p:" + nickname : "m:" + mobId;
        }
    }

    /** 엔드 수정 한 개(몹 id 와 위치). */
    record DragonCrystal(long mobId, double x, double y, double z) {
    }
}
