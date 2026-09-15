package com.gameexpert.engine.mob;

/**
 * 팬텀(바닐라 실존 종, stableId 90). 수치 근거는 {@link MobType#PHANTOM} 의 인라인 인용이다
 * (MC Java 1.21.4 {@code EntityType.PHANTOM sized(0.9F, 0.5F)} · {@code createAttributes()}
 * ATTACK_DAMAGE 6 · MAX_HEALTH 는 {@code Mob} 기본 20).
 *
 * <p><b>불면 스폰 계약</b>: 바닐라 {@code PhantomSpawner} 는 플레이어의
 * {@code ServerStatsCounter} 통계 {@code TIME_SINCE_REST} 를 읽어
 * {@value #SLEEPLESS_TICKS_THRESHOLD} MC 틱(3일) 이상이면 밤·열린 하늘 아래에서 굴린다.
 * WebCraft 는 그 통계 축을 {@code PlayerTickState#timeSinceRestMcTicks} 로 직접 세우고
 * (자고 있지 않은 매 틱 +2 MC 틱, 침대 진입·사망에 0) 굴림은
 * {@link MobSpawner#tryPhantomInsomnia} 가 원문 그대로 옮긴다.
 * 원문과 등급은 {@code docs/research/mc-phantom-insomnia.md} 에 고정돼 있다(등급 A).
 *
 * <p><b>야간 급강하</b>: 표적 위 {@value #SWOOP_ALTITUDE} 블록에서 선회하다 내리꽂는다.
 *
 * <p><b>고양이 — 스폰 억제가 아니라 공격 중단이다.</b> 원문
 * {@code PhantomSpawner.tick} 에는 {@code Cat} 이 한 번도 나오지 않는다. 고양이는 오직
 * {@code Phantom.PhantomSweepAttackGoal.canContinueToUse} 안에서만 작동해, 팬텀 AABB 를
 * {@value #CAT_AVOID_RANGE} 만큼 팽창시킨 <b>상자</b> 안에 살아 있는 {@link Cat} 이 있으면
 * 그 고양이들이 hiss 하고 팬텀은 {@code stop()} 으로 <b>표적을 버린다</b>. 재탐색 주기는
 * 20 game tick = 서버 {@value #CAT_SEARCH_INTERVAL_TICKS} 틱이다. 도입 당시 이 javadoc 이
 * "바닐라도 고양이 곁에서는 스폰하지 않는다"고 적었으나 원문에 근거가 없어 폐기했다.
 *
 * <p>이 웨이브는 <b>코어 등록</b>이라 급강하 궤적은 세우지 않고 근접 골격을 쓴다.
 * <b>팬텀 막</b>은 아이템만 등록하고 수선 재료 배선은 다음 단계다.
 */
public final class Phantom extends MeleeMob {

    /** 불면 임계 72000 MC 틱(3일). 바닐라 {@code PhantomSpawner} 와 같은 값이다. */
    public static final int SLEEPLESS_TICKS_THRESHOLD = 72_000;
    /** 선회 고도(블록). */
    public static final double SWOOP_ALTITUDE = 20.0;
    /** 고양이 회피 반경(블록). 바닐라 {@code getBoundingBox().inflate(16.0)} 의 팽창량이다. */
    public static final double CAT_AVOID_RANGE = 16.0;
    /** 고양이 재탐색 주기. 바닐라 {@code CAT_SEARCH_TICK_DELAY} 20 game tick 을 10 TPS 로 옮긴 값. */
    public static final int CAT_SEARCH_INTERVAL_TICKS = 10;

    /** 마지막 고양이 탐색 결과. 바닐라 {@code isScaredOfCat} 과 같은 자리다. */
    private boolean scaredOfCat;
    /** 다음 탐색까지 남은 틱. 바닐라 {@code catSearchTick} 과 같은 상환 계약이다. */
    private int catSearchCooldown;

    public Phantom(long id, double x, double y, double z) {
        super(id, MobType.PHANTOM, x, y, z);
    }

    /**
     * 바닐라 {@code PhantomSweepAttackGoal.canContinueToUse} 의 고양이 분기. 표적을 쫓는 동안
     * {@value #CAT_SEARCH_INTERVAL_TICKS} 틱마다 한 번만 상자를 훑고, 고양이가 있으면 그 goal 이
     * {@code stop()} 되며 {@code setTarget(null)} 하는 것과 같게 <b>표적을 버린다</b>.
     * 상자는 구가 아니고 시야 차폐도 보지 않는다(mc-phantom-insomnia.md §2).
     */
    @Override
    protected boolean abandonTrackedTarget(MobWorldView world, MobRandom rng) {
        if (--catSearchCooldown > 0) return scaredOfCat;
        catSearchCooldown = CAT_SEARCH_INTERVAL_TICKS;
        scaredOfCat = catWithinAvoidBox(world);
        return scaredOfCat;
    }

    private boolean catWithinAvoidBox(MobWorldView world) {
        double halfWidth = width() / 2.0;
        return world.catWithinBox(
                x - halfWidth - CAT_AVOID_RANGE,
                y - CAT_AVOID_RANGE,
                z - halfWidth - CAT_AVOID_RANGE,
                x + halfWidth + CAT_AVOID_RANGE,
                y + height() + CAT_AVOID_RANGE,
                z + halfWidth + CAT_AVOID_RANGE);
    }

    /**
     * 고양이가 끊은 sweep 표적을 같은 틱에 공통 시각 탐색이 다시 잡지 못하게 한다. 고양이가
     * 떠나면 다음 표적 탐색에서 즉시 풀리므로 캐시가 팬텀을 영구 무해 상태로 가두지도 않는다.
     */
    @Override
    protected boolean mayAcquireVisualTarget(MobWorldView world) {
        if (!scaredOfCat) return true;
        scaredOfCat = catWithinAvoidBox(world);
        return !scaredOfCat;
    }

    @Override public String movementMedium() { return "fly"; }
    @Override protected double detectRange() { return 64.0; }
    @Override protected double attackRange() { return 1.5; }
    /** 바닐라 {@code Attributes.ATTACK_DAMAGE} 6.0. */
    @Override protected int attackDamage() { return 6; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return false; }

    /**
     * 비행 추격은 플레이어 몸 중앙을 향한다. 이전 구현은 수평 성분만 주어 불면 스폰 높이
     * (플레이어 위 20~34블록)에 태어난 팬텀이 영원히 내려오지 못했다.
     */
    @Override
    protected double chaseVerticalMovement(MobWorldView world, PlayerSnapshot target,
                                            MobRandom rng) {
        double delta = target.y() + 0.9 - (y + height() * 0.5);
        if (Math.abs(delta) < 1e-9) return 0.0;
        return Math.copySign(preparedMovementSpeed(moveSpeed()), delta);
    }
}
