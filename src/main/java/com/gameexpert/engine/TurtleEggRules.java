package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * [TURTLE] 거북 생애 주기의 순수 규칙(상태 어휘 · 부화 사슬 · 밟기 파괴 · 설치 · 드랍 ·
 * 산란 · 등껍질 투구). <b>전부 순수 함수</b>다 — 월드도 난수 객체도 시간도 받지 않고
 * 호출부가 굴림 결과와 밤낮만 넘긴다.
 *
 * <p>client {@code backend/standalone/StandaloneTurtleRules.ts} 와 <b>같은 표</b>이며 한쪽만
 * 고치면 파리티가 깨진다. 시간 단위는 전부 10 TPS 권위 틱이다(바닐라 초 × 10).
 *
 * <h2>상태 어휘</h2>
 * 바닐라 {@code TurtleEggBlock} 의 블록스테이트는 {@code eggs}(1..4) × {@code hatch}(0..2)
 * = 12 가지다. 이 저장소는 ID 를 열둘로 쪼개지 않고 상태 바이트 하나에 둘 다 싣는다:
 * 하위 두 비트가 {@code eggs − 1}, 그 위 두 비트가 {@code hatch} 다. 그래서 <b>상태 0 이
 * 곧 "갓 놓은 알 한 개"</b>이고, 상태를 싣지 않는 옛 경로(희소 상태 배열의 부재)가 유효한
 * 알로 읽힌다.
 */
public final class TurtleEggRules {

    private TurtleEggRules() {
    }

    /** [A] {@code TurtleEggBlock.MIN_EGGS}. */
    public static final int MIN_EGGS = 1;
    /** [A] {@code TurtleEggBlock.MAX_EGGS}. 다섯 번째 알은 같은 칸에 쌓이지 않는다. */
    public static final int MAX_EGGS = 4;
    /** [A] {@code TurtleEggBlock.MAX_HATCH_LEVEL}. 이 단계에서 다음 진행이 곧 부화다. */
    public static final int MAX_HATCH = 2;

    /** 상태 바이트를 만든다. 범위를 벗어난 입력은 [A] 블록스테이트 범위로 자른다. */
    public static int state(int eggs, int hatch) {
        int clampedEggs = Math.max(MIN_EGGS, Math.min(MAX_EGGS, eggs));
        int clampedHatch = Math.max(0, Math.min(MAX_HATCH, hatch));
        return (clampedEggs - 1) | (clampedHatch << 2);
    }

    /** 상태 바이트가 말하는 알의 수(1..4). */
    public static int eggs(int state) {
        return (state & 0b11) + 1;
    }

    /** 상태 바이트가 말하는 균열 단계(0..2). */
    public static int hatch(int state) {
        return Math.min(MAX_HATCH, (state >> 2) & 0b11);
    }

    /**
     * [A] {@code TurtleEggBlock.onSand} 가 보는 {@code #minecraft:sand} 태그.
     * 바닐라 태그는 sand · red_sand · suspicious_sand 셋이고 이 저장소도 셋을 다 가지고 있다.
     */
    public static boolean isSand(int blockType) {
        return blockType == Blocks.SAND || blockType == Blocks.RED_SAND
                || blockType == Blocks.SUSPICIOUS_SAND;
    }

    /**
     * [A] {@code mayPlaceOn} = {@code canSurvive} = {@code onSand(level, pos)} — 아래 칸이
     * 모래여야 한다. 지지 표({@code Blocks.classify})는 "아래가 꽉 찬 면"까지만 보므로 모래
     * 여부는 이 술어가 소유한다.
     */
    public static boolean canPlaceOn(int blockBelow) {
        return isSand(blockBelow);
    }

    /**
     * [A] {@code canBeReplaced}: 손에 든 것이 거북 알이고 지금 알이 4 개 미만이면 새 블록을
     * 놓는 대신 같은 칸의 알이 하나 늘어난다. 웅크린 채 놓으면 바닐라도 쌓지 않지만 이
     * 저장소에는 그 입력 축이 없어 언제나 쌓는다([C]).
     */
    public static boolean stacks(int existingBlock, int heldItem, int state) {
        return existingBlock == Blocks.TURTLE_EGG && heldItem == Blocks.TURTLE_EGG
                && eggs(state) < MAX_EGGS;
    }

    /**
     * 거북 알을 놓았을 때의 상태. 같은 칸에 이미 알이 있으면 개수만 올린다 — [A]
     * {@code getStateForPlacement} 도 {@code EGGS} 만 바꾸고 {@code HATCH} 는 유지한다.
     */
    public static int placedState(int existingBlock, int existingState) {
        if (existingBlock != Blocks.TURTLE_EGG) return state(MIN_EGGS, 0);
        return state(eggs(existingState) + 1, hatch(existingState));
    }

    // ── 부화 사슬 ────────────────────────────────────────────────────────────
    //
    // [A] randomTick 은 shouldUpdateHatchLevel(level) && onSand(level, pos) 일 때만 한 단계
    // 나아간다. hatch < 2 면 균열이 한 단계 늘고, 2 였으면 블록이 사라지며 알 수만큼 새끼가
    // 나온다.
    //
    // [A] shouldUpdateHatchLevel:
    //   float f = level.getTimeOfDay(1.0F);
    //   if (f < 0.69 && f > 0.65) return true;   // 하루의 4% — 자정 지난 좁은 창
    //   return level.random.nextInt(500) == 0;   // 그 밖에는 1/500
    //
    // [C] 이 저장소는 하루 위상을 밤/낮 두 값으로만 안다. 그래서 좁은 창을 밤 전체로 펼치되
    // 기댓값을 보존하도록 확률을 다시 푼다:
    //   · 하루 평균 = 0.04 × 1 + 0.96 × (1/500) ≈ 0.04192
    //   · 창이 통째로 밤(하루의 절반) 안에 있으므로
    //     밤 평균 = (0.04 + 0.46 × 1/500) / 0.5 ≈ 0.08184 ≈ 1/12.2 → nextInt(12)
    //   · 낮은 바닐라 그대로 1/500
    // 창 안에서 난수를 쓰지 않던 바닐라와 달리 이쪽은 언제나 nextInt 하나를 소비한다 —
    // 그것이 두 권위가 공유하는 소비 규약이다.

    /** [C] 밤의 균열 진행 확률 분모. */
    public static final int NIGHT_HATCH_BOUND = 12;
    /** [A] 창 밖(이 저장소에서는 낮)의 균열 진행 확률 분모. */
    public static final int DAY_HATCH_BOUND = 500;

    /** 밤낮에 따른 진행 확률 분모. 호출부는 이 값으로 {@code nextInt} 를 한 번만 굴린다. */
    public static int hatchBound(boolean daytime) {
        return daytime ? DAY_HATCH_BOUND : NIGHT_HATCH_BOUND;
    }

    /** 굴림 결과가 진행을 뜻하는가. 바닐라와 같이 0 일 때만 나아간다. */
    public static boolean shouldAdvance(int roll) {
        return roll == 0;
    }

    /** 이번 진행이 부화인가(균열이 이미 최대치인가). */
    public static boolean hatchesNow(int state) {
        return hatch(state) >= MAX_HATCH;
    }

    /**
     * 부화하지 않는 진행의 다음 상태. 알 수는 그대로 두고 균열만 한 단계 올린다.
     * {@link #hatchesNow} 가 참일 때 부르면 안 된다(그때는 블록이 사라진다).
     */
    public static int advancedState(int state) {
        return state(eggs(state), hatch(state) + 1);
    }

    /** 부화로 나오는 새끼 거북 마릿수 = 그 순간의 알 수([A] {@code for i < EGGS}). */
    public static int hatchSpawnCount(int state) {
        return eggs(state);
    }

    // ── 밟기 파괴 ────────────────────────────────────────────────────────────
    //
    // [A] stepOn : 웅크리지 않은 채 밟으면 destroyEgg(..., 100) → 1/100
    //     fallOn : 좀비가 아니면        destroyEgg(..., 3)   → 1/3
    //     canDestroyEgg: 거북·박쥐는 못 부수고, 그 밖의 LivingEntity 는 플레이어이거나
    //                    mobGriefing 이 켜져 있어야 한다.
    // 좀비가 fallOn 에서 빠지는 것은 "좀비는 낙하로 부수는 대신 일부러 밟으러 온다"는
    // 바닐라 설계 때문이다(Zombie 의 RemoveBlockGoal).

    /** [A] {@code stepOn} 의 확률 분모. */
    public static final int STEP_BOUND = 100;
    /** [A] {@code fallOn} 의 확률 분모. */
    public static final int FALL_BOUND = 3;

    /**
     * [A] {@code canDestroyEgg}. 거북·박쥐({@code exempt})는 언제나 거짓, 플레이어는 언제나
     * 참, 그 밖의 몹은 mobGriefing 을 따른다.
     */
    public static boolean canDestroy(boolean player, boolean exempt, boolean mobGriefing) {
        if (exempt) return false;
        return player || mobGriefing;
    }

    /**
     * 알 하나가 부서진 뒤의 상태. 마지막 하나였으면 블록째 사라지므로
     * {@link Blocks#AIR} 를 뜻하는 {@code -1} 을 돌려준다([A] {@code decreaseEggs}).
     */
    public static int decreasedState(int state) {
        int remaining = eggs(state);
        if (remaining <= MIN_EGGS) return -1;
        return state(remaining - 1, hatch(state));
    }

    // ── 채굴 드랍 ────────────────────────────────────────────────────────────

    /**
     * [A] 전리품표 {@code blocks/turtle_egg} 는 실크 터치 조건 하나뿐이고, 맞으면
     * {@code copy_state} 로 옮긴 {@code eggs} 만큼을 떨군다. 없으면 아무것도 나오지 않는다.
     */
    public static int minedDropCount(int state, boolean silkTouch) {
        return silkTouch ? eggs(state) : 0;
    }

    // ── 산란 ─────────────────────────────────────────────────────────────────

    /** [A] {@code TurtleLayEggGoal} 의 {@code random.nextInt(4) + 1}. */
    public static int laidEggCount(int roll) {
        return Math.max(MIN_EGGS, Math.min(MAX_EGGS, roll + 1));
    }

    /** 산란으로 놓이는 블록 상태. 갓 놓은 알이라 균열은 언제나 0 이다. */
    public static int laidEggState(int roll) {
        return state(laidEggCount(roll), 0);
    }

    // ── 성장 드랍 ────────────────────────────────────────────────────────────

    /**
     * [A] {@code Turtle.ageBoundaryReached}: 새끼가 성체가 되는 그 순간 등딱지 조각 한 개를
     * 떨군다. 성체 거북은 죽어도 아무것도 떨구지 않으므로 이것이 조각의 유일한 경로다.
     */
    public static final int GROWTH_SCUTE_COUNT = 1;

    // ── 거북 등껍질 투구 ─────────────────────────────────────────────────────
    //
    // [A] TurtleHelmetItem.turtleHelmetTick:
    //   if (!player.isEyeInFluid(FluidTags.WATER)) {
    //       player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, ...));
    //   }
    // 200 MC 틱 = 10 초. 물 밖에 있을 때만 다시 걸어 주므로 물에서 나오는 순간부터 10 초가
    // 다시 채워진다.

    /** [A] 200 MC 틱을 10 TPS 권위 틱으로 환산한 값(바닐라 초 × 10). */
    public static final int HELMET_WATER_BREATHING_TICKS = 100;

    /** 이번 틱에 거북 등껍질이 수중 호흡을 다시 걸어야 하는가. */
    public static boolean helmetRefreshesWaterBreathing(boolean wearing, boolean eyeInWater) {
        return wearing && !eyeInWater;
    }

    // ── 번식 ─────────────────────────────────────────────────────────────────

    /**
     * [A] {@code Turtle.isFood} 는 {@code #minecraft:turtle_food} 태그 하나이고 그 태그의
     * 유일한 원소가 해초다. 해초는 이미 블록 58 이라 재료를 신설하지 않는다.
     *
     * <p>짝짓기가 끝나도 새끼가 바로 나오지 않는다 — [A] {@code spawnChildFromBreeding} 이
     * {@code setHasEgg(true)} 만 하고 자식을 만들지 않으며, 새끼는 고향 해변에 낳은 알에서
     * 나온다.
     */
    public static final int BREEDING_FOOD = Blocks.SEAGRASS;

    // ── 좀비류 알 사냥(RemoveBlockGoal) ───────────────────────────────────────
    //
    // [A] {@code Zombie.registerGoals} 는 우선순위 4 에
    // {@code ZombieAttackTurtleEggGoal(this, 1.0, 3)} 를 건다. 그 goal 은
    // {@code RemoveBlockGoal(Blocks.TURTLE_EGG, mob, 1.0, 3)} 이고
    // {@code RemoveBlockGoal extends MoveToBlockGoal(mob, speedModifier, 24, verticalSearchRange)}
    // 라 수평 24 · 수직 3 상자를 훑는다. 하위 종은 {@code addBehaviourGoals} 만 재정의하고
    // {@code registerGoals} 는 그대로 물려받으므로 좀비·좀비주민·허스크·드라운드·좀비피글린이
    // 같은 goal 을 갖는다.
    //
    // 밟기(1/100)·낙하(1/3)와 달리 이 goal 은 블록째 없앤다 — [A] {@code RemoveBlockGoal.tick}
    // 이 {@code level.removeBlock(pos, false)} 를 부르지 {@code decreaseEggs} 를 부르지 않는다.
    //
    // 정적판 {@code StandaloneTurtleRules} 의 같은 절과 짝이며, 두 권위의 배선
    // ({@link com.gameexpert.engine.mob.TurtleEggHunt} · {@code StandaloneMobRuntime})이
    // 이 표만 읽는다.

    /** [A] {@code MoveToBlockGoal} 의 수평 탐색 반경(블록). */
    public static final int HUNT_SEARCH_RANGE = 24;
    /** [A] {@code RemoveBlockGoal(..., verticalSearchRange = 3)}. */
    public static final int HUNT_VERTICAL_RANGE = 3;
    /** [A] {@code ZombieAttackTurtleEggGoal.acceptedDistance()} = 1.14. */
    public static final double HUNT_ACCEPTED_DISTANCE = 1.14;
    /** [A] {@code ticksSinceReachedGoal > 60} 의 60 MC 틱을 10 TPS 권위 틱으로 환산한 값. */
    public static final int HUNT_BREAK_TICKS = 30;
    /** [A] {@code ticksSinceReachedGoal % 6 == 0} 의 진행음 주기(6 MC 틱 = 권위 3틱). */
    public static final int HUNT_PROGRESS_SOUND_INTERVAL_TICKS = 3;
    /** [A] {@code MoveToBlockGoal.canContinueToUse} 의 {@code tryTicks <= 1200}(권위 600틱). */
    public static final int HUNT_GIVE_UP_TICKS = 600;
    /**
     * [C] 실패한 탐색을 다시 굴리기까지의 대기(권위 틱). [A] 는
     * {@code nextStartTick = reducedTickDelay(200 + random.nextInt(200))} 로 100~200 MC 틱을
     * 뽑지만, 이 저장소의 몹 난수는 자연 스폰 파리티가 걸린 공유 스트림이라 한 칸도 소비할 수
     * 없다(창 굴림이 몹 id 해시인 것과 같은 규율). 그래서 난수 없이 그 창의 하한
     * 100 MC 틱 = 권위 50틱으로 고정한다.
     */
    public static final int HUNT_SCAN_INTERVAL_TICKS = 50;
    /**
     * [C] 이 저장소에는 게임 규칙(gamerule) 축이 없다. 바닐라 기본값이 켜짐이고 크리퍼 폭발·
     * 엔더맨 집기 같은 기존 변형도 전부 켜진 것처럼 굴러가므로 {@code mobGriefing} 을 상수
     * 켜짐으로 고정한다 — 규칙 갈래([A] {@code canDestroyEgg})는 그대로 두고 입력만 고정한다.
     */
    public static final boolean MOB_GRIEFING = true;

    /**
     * [C] <b>거북 산란은 고향 해변으로 돌아가지 않는다(선언된 divergence).</b>
     * [A] {@code Turtle.TurtleLayEggGoal} 은 {@code homePos} 로 회귀하지만, 정적판 영속 행
     * ({@code StandaloneMobStateRecord})이 필드 전부 필수인 평면 스키마 + 종별 불변식 검증이라
     * 좌표 세 칸을 여는 비용이 크고 회귀 goal 자체가 아직 두 권위 어디에도 없다. 이 저장소의
     * 계약은 <b>근처 모래 산란</b>이며, 회귀 goal 을 들이는 트랙이 이 상수와
     * {@code docs/MC-REFERENCE.md} 의 같은 줄을 함께 연다.
     */
    public static final boolean LAYS_EGGS_AT_HOME_BEACH = false;
}
