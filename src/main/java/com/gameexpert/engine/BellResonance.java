package com.gameexpert.engine;

/**
 * [GLOWING] 종 블록 엔티티 한 개의 공명 상태 기계. 26.3-snapshot-7 {@code BellBlockEntity} 의 필드
 * ({@code ticks · shaking · resonating · resonationTicks · lastRingTimestamp · nearbyEntities})와
 * {@code tick} · {@code triggerEvent} · {@code updateEntities} 를 MC 틱 단위로 그대로 옮긴다(javap).
 *
 * <ul>
 *   <li>울림({@link #ring}) = {@code triggerEvent(1, face)}: 마지막 탐색에서 {@value #MIN_TICKS_BETWEEN_SEARCHES}
 *       MC 틱이 지났거나 목록이 없을 때만 종 칸을 {@value #SEARCH_RADIUS} 블록 부풀린 상자 안의 엔티티를 다시
 *       모으고, 공명 틱과 흔들림 틱을 0 으로 되돌린다.</li>
 *   <li>틱({@link #tickOnce}): 흔들리는 동안 틱을 세고 {@value #DURATION} 에서 멈춘다. 틱이
 *       {@value #TICKS_BEFORE_RESONATION} 이상이고 아직 공명 틱이 0 이며 <b>울림 때 모은 목록</b>에 종 중심
 *       {@value #HEAR_BELL_RADIUS} 블록 안의 살아 있는 습격자가 있으면 공명을 시작하고 BELL_RESONATE 를 낸다.
 *       공명 중에는 공명 틱을 {@value #MAX_RESONATION_TICKS} 까지 세고, 그다음 틱에 종료 동작을 한다 —
 *       서버는 {@code makeRaidersGlow}: 같은 목록에서 종 중심 {@value #HIGHLIGHT_RAIDERS_RADIUS} 블록 안의
 *       습격자에게 발광 {@value #GLOW_DURATION} MC 틱.</li>
 * </ul>
 *
 * <p>권위 틱(10 TPS)은 MC 2 틱이므로 호출부가 권위 틱마다 {@link #tickOnce} 를 두 번 부른다. 울림은 그 권위
 * 틱의 행동 단계에서 먼저 처리되므로 바닐라처럼 같은 틱의 블록 엔티티 틱보다 앞선다. 정적판 짝은
 * {@code StandaloneBellResonance.ts} 다.
 */
final class BellResonance {
    static final int DURATION = 50;
    static final int GLOW_DURATION = 60;
    static final int MIN_TICKS_BETWEEN_SEARCHES = 60;
    static final int MAX_RESONATION_TICKS = 40;
    static final int TICKS_BEFORE_RESONATION = 5;
    static final int SEARCH_RADIUS = 48;
    static final int HEAR_BELL_RADIUS = 32;
    static final int HIGHLIGHT_RAIDERS_RADIUS = 48;

    /** {@link #tickOnce} 결과: 이번 MC 틱에 공명을 시작했다(BELL_RESONATE 를 낸다). */
    static final int RESONATED = 1;
    /** {@link #tickOnce} 결과: 이번 MC 틱에 공명이 끝나 습격자를 빛나게 했다. */
    static final int GLOWED = 2;

    /** 울림 때 모은 엔티티(습격자) 목록을 읽고 효과를 거는 권위 쪽 창구. */
    interface Raiders {
        /** 종 칸을 반경만큼 부풀린 상자와 겹치는 습격자 몹 id(바닐라 getEntitiesOfClass 순서는 무관하다). */
        long[] capture(int x, int y, int z, int radius);

        /** 이 id 의 몹이 살아 있고 종 중심에서 radius 블록보다 가까운 습격자인가(closerToCenterThan). */
        boolean inRange(long mobId, int x, int y, int z, int radius);

        /** 발광 부여. */
        void glow(long mobId, int mcTicks);
    }

    final int x;
    final int y;
    final int z;
    private int ticks;
    private boolean shaking;
    private boolean resonating;
    private int resonationTicks;
    private long lastRingTimestamp;
    private long[] nearby;

    BellResonance(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** BellBlockEntity#triggerEvent(1, face) → updateEntities. {@code gameTime} 은 MC 틱 게임 시간이다. */
    void ring(long gameTime, Raiders raiders) {
        if (nearby == null || gameTime > lastRingTimestamp + MIN_TICKS_BETWEEN_SEARCHES) {
            lastRingTimestamp = gameTime;
            nearby = raiders.capture(x, y, z, SEARCH_RADIUS);
        }
        resonationTicks = 0;
        ticks = 0;
        shaking = true;
    }

    /** BellBlockEntity#tick 한 번(MC 1 틱). 결과는 {@link #RESONATED} · {@link #GLOWED} 비트다. */
    int tickOnce(Raiders raiders) {
        int result = 0;
        if (shaking) ticks++;
        if (ticks >= DURATION) {
            shaking = false;
            ticks = 0;
        }
        if (ticks >= TICKS_BEFORE_RESONATION && resonationTicks == 0 && anyInRange(raiders, HEAR_BELL_RADIUS)) {
            resonating = true;
            result |= RESONATED;
        }
        if (resonating) {
            if (resonationTicks < MAX_RESONATION_TICKS) {
                resonationTicks++;
            } else {
                if (nearby != null) {
                    for (long id : nearby) {
                        if (raiders.inRange(id, x, y, z, HIGHLIGHT_RAIDERS_RADIUS)) raiders.glow(id, GLOW_DURATION);
                    }
                }
                resonating = false;
                result |= GLOWED;
            }
        }
        return result;
    }

    /**
     * 더 들고 있어도 바닐라와 다를 게 없는가: 흔들림·공명이 끝났고 탐색 간격도 지나 다음 울림이 어차피
     * 목록을 새로 모은다. 이때 원장에서 버려도 된다.
     */
    boolean idle(long gameTime) {
        return !shaking && !resonating && gameTime > lastRingTimestamp + MIN_TICKS_BETWEEN_SEARCHES;
    }

    boolean shaking() {
        return shaking;
    }

    boolean resonating() {
        return resonating;
    }

    private boolean anyInRange(Raiders raiders, int radius) {
        if (nearby == null) return false;
        for (long id : nearby) {
            if (raiders.inRange(id, x, y, z, radius)) return true;
        }
        return false;
    }

    /** BlockPos#closerToCenterThan: 종 칸 중심과의 제곱거리가 radius² 보다 작다(경계 제외). */
    static boolean closerToCenterThan(int x, int y, int z, double px, double py, double pz, double radius) {
        double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz < radius * radius;
    }
}
