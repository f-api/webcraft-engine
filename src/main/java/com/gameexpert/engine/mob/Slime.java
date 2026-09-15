package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 바닐라 size 규칙을 유지하는 슬라임. 자연 스폰은 size=2로 시작한다.
 * 이동은 보행이 아니라 도약이다: 지상에서 대기 → 점프 + 수평 성분 → 체공 중 방향 유지 → 착지.
 */
public final class Slime extends MeleeMob {
    /** 도약 대기 최소치(10 TPS 서버 틱). 바닐라 MC 10틱. */
    public static final int MIN_JUMP_DELAY_TICKS = 5;
    /** 대기 난수 폭. 5 + [0,11) → 5~15 서버 틱(= MC 10~30). */
    public static final int JUMP_DELAY_SPAN_TICKS = 11;
    /** 추적 중 대기 단축 계수(바닐라 jumpDelay/3). */
    public static final int CHASE_DELAY_DIVISOR = 3;
    /** 도약 초기 수직 속도. 토끼 hop(0.42) 기준에 크기당 가산해 큰 슬라임이 더 높이 뛴다. */
    public static final double BASE_JUMP_POWER = 0.42;
    public static final double JUMP_POWER_PER_SIZE = 0.03;

    private final int size;
    private int jumpDelay = MIN_JUMP_DELAY_TICKS;
    /** 이번 틱에 수평 이동 성분을 허용하는가(지상 대기 중에는 정지). */
    private boolean leaping;

    public Slime(long id, double x, double y, double z) {
        this(id, x, y, z, 2);
    }

    public Slime(long id, double x, double y, double z, int size) {
        super(id, MobType.SLIME, x, y, z);
        if (size < 1) throw new IllegalArgumentException("slime size must be positive");
        this.size = size;
        setInitialHealth(size * size);
    }

    @Override public double width() { return 0.51 * size; }
    @Override public double height() { return 0.51 * size; }
    @Override public int maxHp() { return size * size; }
    public int size() { return size; }
    @Override public int slimeSize() { return size; }

    @Override protected double detectRange() { return 16.0; }
    @Override protected double attackRange() { return 1.2; }
    @Override protected int attackDamage() { return size == 1 ? 0 : size; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected boolean climbWalls() { return false; }

    /** 지상 대기 중에는 수평 이동을 0으로 눌러 도약 사이에 미끄러지지 않게 한다. */
    @Override
    protected double moveSpeed() {
        return leaping ? super.moveSpeed() : 0.0;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return super.tick(world, rng);
        boolean groundedBefore = onGround;
        if (groundedBefore && jumpDelay > 0) jumpDelay--;
        leaping = !groundedBefore || jumpDelay <= 0;

        List<MobEvent> events = super.tick(world, rng);

        // 대기가 끝났고 여전히 접지 상태면 이번 틱에 뛴다. 다음 틱의 수직 적분이 실제 상승을 만든다.
        if (groundedBefore && jumpDelay <= 0 && onGround) {
            vy = BASE_JUMP_POWER + JUMP_POWER_PER_SIZE * size;
            boolean chasing = state == MobState.CHASE || state == MobState.ATTACK;
            int delay = MIN_JUMP_DELAY_TICKS + rng.nextInt(JUMP_DELAY_SPAN_TICKS);
            jumpDelay = chasing ? Math.max(1, delay / CHASE_DELAY_DIVISOR) : delay;
        } else if (!groundedBefore && onGround) {
            events = appendEvent(events, new MobEvent.Sound("step"));   // 착지
        }
        return events;
    }

    /** 남은 도약 대기(테스트용). */
    int jumpDelayTicks() {
        return jumpDelay;
    }
}
