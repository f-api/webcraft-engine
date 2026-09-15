package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.Fluids;
import com.gameexpert.terrain.Blocks;

/**
 * [GUARDIAN] 가디언 2종의 구조물 스폰 규칙(순수·상태 없음).
 *
 * <p>바닐라에서 가디언은 <b>자연 스폰 표에 없고</b> 해저 신전({@code ocean_monument}) 의
 * {@code spawn_overrides} 로만 생긴다 — 신전 경계 상자 안이면서 물속인 칸에서만이다.
 * 엘더는 신전이 놓일 때 정확히 3마리가 고정 좌표(꼭대기 방 1 + 양 날개 2)에 배치된다.
 *
 * <p>이 클래스는 두 lane 을 함께 든다.
 * <ul>
 *   <li><b>배치 명단</b>({@link #monumentRoster}/{@link #spawnable}): 신전이 채택될 때 한 번.
 *       엘더 3기 + 초기 가디언 무리를 신전 상자 안 물칸에 놓는다.</li>
 *   <li><b>주기 재스폰</b>({@link #naturalSpawnRule}): 자연 스폰 루프가 신전 상자 안에서
 *       바닐라 {@code spawn_overrides} 표({@link MonumentSpawnOverride})를 골랐을 때의
 *       종별 판정이다. 신전을 비우면 가디언은 이 lane 으로 다시 차오른다.</li>
 * </ul>
 *
 * <p>같은 규칙의 정적판 사본은 {@code client/src/backend/standalone/StandaloneMobRules.ts}
 * 의 {@code standaloneGuardianSpawnable} / {@code standaloneGuardianMonumentRoster} 다.
 */
public final class GuardianSpawnRules {

    /** 바닐라 {@code ocean_monument} 의 {@code spawn_overrides} 가 정확히 이 한 종만 담는다. */
    public static final int MONUMENT_ELDER_COUNT = 3;

    private GuardianSpawnRules() {}

    /**
     * 신전 경계 상자 조건. 좌표는 몹 발밑 블록 좌표이며 상자 경계는 <b>양끝 포함</b>이다
     * (구조물 AABB 계약과 같다).
     */
    public static boolean withinBounds(int minX, int minY, int minZ,
                                       int maxX, int maxY, int maxZ,
                                       int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * 물속 조건. 발밑 칸과 그 위 칸이 모두 물 매질이어야 한다 — 바닐라
     * {@code Guardian.checkGuardianSpawnRules} 의 {@code level.getFluidState(pos).is(FluidTags.WATER)}
     * 를 몸높이 두 칸으로 넓힌 것으로, 수면 한 칸에 끼는 배치를 막는다.
     */
    public static boolean inWater(MobWorldView world, int x, int y, int z) {
        return Fluids.isWaterMedium(world.getBlock(x, y, z) & 0xffff)
                && Fluids.isWaterMedium(world.getBlock(x, y + 1, z) & 0xffff);
    }

    /** 두 조건을 모두 만족하는 칸만 가디언 스폰 후보다. */
    public static boolean spawnable(MobWorldView world,
                                    int minX, int minY, int minZ,
                                    int maxX, int maxY, int maxZ,
                                    int x, int y, int z) {
        return withinBounds(minX, minY, minZ, maxX, maxY, maxZ, x, y, z)
                && inWater(world, x, y, z);
    }

    /**
     * 바닐라 {@code Guardian.checkGuardianSpawnRules} 그대로의 <b>자연 스폰</b> 판정.
     *
     * <pre>
     * return (random.nextInt(20) == 0 || !level.canSeeSkyFromBelowWater(pos))
     *     &amp;&amp; level.getDifficulty() != Difficulty.PEACEFUL
     *     &amp;&amp; (EntitySpawnReason.isSpawner(reason) || level.getFluidState(pos).is(WATER))
     *     &amp;&amp; level.getFluidState(pos.below()).is(WATER);
     * </pre>
     *
     * <p>{@code nextInt(20)} 은 단축 평가 앞자리라 <b>항상</b> 소비된다 — 두 권위가 같은 자리에서
     * 같은 수를 뽑아야 뒤의 모든 결정이 갈리지 않는다. PEACEFUL 은 이 저장소에 없다
     * ({@code Difficulty} 주석). 스포너 경로가 아니므로 물 조건 두 개는 그대로 남는다.
     *
     * <p>이것은 {@link #inWater} 와 <b>다른 규칙</b>이다: 구조물 배치 명단(엘더·초기 가디언)은
     * 몸높이 두 칸(y, y+1)을 요구하고, 자연 스폰은 바닐라와 같이 발밑(y, y-1)을 요구한다.
     */
    public static boolean naturalSpawnRule(MobWorldView world, int x, int y, int z,
                                           MobRandom rng) {
        boolean darkSide = rng.nextInt(20) == 0 || !canSeeSkyFromBelowWater(world, x, y, z);
        return darkSide
                && Fluids.isWaterMedium(world.getBlock(x, y, z) & 0xffff)
                && Fluids.isWaterMedium(world.getBlock(x, y - 1, z) & 0xffff);
    }

    /**
     * 바닐라 {@code LevelReader.canSeeSkyFromBelowWater} — 해수면 위는 그냥 하늘이 보이는가이고,
     * 아래는 해수면 칸에서 하늘이 보이면서 그 아래로 대상 칸까지 <b>빛을 막는 비유체 블록이
     * 없어야</b> 한다.
     *
     * <p>divergence: 이 저장소에는 블록별 {@code getLightBlock} 표가 없어 "빛을 막는" 을
     * {@link MobWorldView#isSolid}(= 공기·유체 제외) 로 근사한다. {@code openToSky} 가 쓰는
     * 술어와 같은 것이라 두 권위가 같은 판정을 낸다.
     */
    public static boolean canSeeSkyFromBelowWater(MobWorldView world, int x, int y, int z) {
        if (y >= Blocks.SEA_LEVEL) return world.openToSky(x, y, z);
        if (!world.openToSky(x, Blocks.SEA_LEVEL, z)) return false;
        for (int scan = Blocks.SEA_LEVEL - 1; scan > y; scan--) {
            short block = world.getBlock(x, scan, z);
            if (block < 0 || world.isSolid(block)) return false;
        }
        return true;
    }

    /**
     * 신전 하나가 채택될 때의 고정 명단: 엘더 가디언 정확히 3마리.
     * 일반 가디언은 배치 명단이 아니라 신전 경계 상자 안의 주기 스폰이라 여기 없다.
     */
    public static List<MobType> monumentRoster() {
        List<MobType> roster = new ArrayList<>(MONUMENT_ELDER_COUNT);
        for (int index = 0; index < MONUMENT_ELDER_COUNT; index++) {
            roster.add(MobType.ELDER_GUARDIAN);
        }
        return List.copyOf(roster);
    }
}
