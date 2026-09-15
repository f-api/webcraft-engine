package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * [CONDUIT] 콘딧 활성 · 범위 · 사냥 판정. <b>전부 순수 함수</b>다 — 월드 틱도 좌표도 받지
 * 않고 프레임 블록 개수 하나(또는 블록 표본 함수)만 본다. client
 * {@code world/ConduitRules.ts} 와 같은 규칙이며, 두 권위가 같은 함수를 복제하는 이유는
 * 산호 전이 · 구리 산화와 같다: 판정을 두 벌 적으면 한쪽만 갱신됐을 때 "서버는 켜졌는데
 * 클라는 꺼진 콘딧"이 된다.
 *
 * <h2>왜 순수 함수인가</h2>
 * 활성 여부는 <b>파생값</b>이다. 프레임을 한 칸 부수면 그 순간 꺼지고 되쌓으면 켜지므로
 * 저장할 상태가 없다(파생이면 저장하지 않는다는 규약). 그래서 이 클래스에는 가변 상태도,
 * 난수도, 시간도 없다 — 호출부가 언제 검사하든 같은 배치에는 같은 답이 나온다.
 *
 * <p>검사 <b>빈도</b>만 호출부의 몫이고, 그 자리에서 랜덤틱 선례를 따른다: 바닐라
 * {@code ConduitBlockEntity.serverTick} 도 {@code gameTime % 40 == 0} 일 때만 모양을 다시
 * 읽는다(매 틱 5×5×5 를 훑지 않는다). 10 TPS 환산으로 <b>서버 틱 20회마다 1회</b>다.
 *
 * <h2>[A] 1.21.4 {@code ConduitBlockEntity.updateShape} 원문 기하</h2>
 * <ol>
 *   <li>콘딧을 중심으로 한 <b>3×3×3 스물일곱 칸이 전부 물</b>이어야 한다. 한 칸이라도 물이
 *       아니면 프레임을 세든 말든 즉시 비활성이다.</li>
 *   <li>프레임 후보는 반지름 2 의 <b>축 정렬 링 세 개</b>다. 평면 x=0 · y=0 · z=0 각각에서
 *       5×5 정사각의 테두리(가운데 3×3 을 뺀 열여섯 칸)이고, 세 링은 축 위에서 두 칸씩
 *       겹치므로 후보 총합은 16×3 − 2×3 = <b>42</b> 다.</li>
 *   <li>후보 칸의 블록이 {@code #minecraft:conduit_frame} 태그(프리즈머린 · 프리즈머린
 *       벽돌 · 어두운 프리즈머린 · 바다 랜턴 네 종)면 프레임으로 센다.</li>
 *   <li>센 개수가 <b>16 이상</b>이면 활성이다.</li>
 * </ol>
 */
public final class ConduitRules {

    private ConduitRules() {
    }

    /** [A] 콘딧을 감싸야 하는 물 코어의 반지름(3×3×3 = 27칸). */
    public static final int WATER_CORE_RADIUS = 1;
    /** [A] 프레임 링의 반지름. 세 링 모두 콘딧에서 두 칸 떨어져 있다. */
    public static final int FRAME_RADIUS = 2;
    /** [A] 프레임 후보 칸의 총 개수(16×3 − 2×3). 활성 최댓값이자 사냥 문턱이다. */
    public static final int FRAME_SLOTS = 42;
    /** [A] 활성에 필요한 최소 프레임 블록 수({@code positions.size() >= 16}). */
    public static final int ACTIVATION_FRAME_BLOCKS = 16;
    /**
     * [A] 범위 사다리의 계단 폭. 바닐라 {@code applyEffects} 의 {@code i / 7 * 16} 이며
     * <b>정수 나눗셈</b>이 계단을 만든다 — 프레임 7개마다 범위가 16블록씩 늘어난다.
     */
    public static final int FRAME_BLOCKS_PER_STEP = 7;
    /** [A] 계단 한 칸이 늘리는 범위(블록). */
    public static final int RANGE_PER_STEP = 16;

    /**
     * [A] 콘딧 파워 부여 주기. 바닐라는 {@code gameTime % 40 == 0} 이라 MC 40틱 = 2초마다다.
     * 10 TPS 환산으로 서버 틱 20회다(서버 틱 1회 = MC 2틱).
     */
    public static final int PULSE_SERVER_TICKS = 20;
    /**
     * [A] 한 번에 부여하는 콘딧 파워 지속(MC 틱). 바닐라
     * {@code new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true)} 의 260 이다 —
     * 주기(40)보다 훨씬 길어서 범위를 벗어나도 13초 동안 여운이 남는 것이 바닐라 체감이다.
     */
    public static final int POWER_DURATION_MC_TICKS = 260;
    /** [A] 콘딧 파워의 앰프. 바닐라는 언제나 0(레벨 I)이며 프레임 수가 세기를 바꾸지 않는다. */
    public static final int POWER_AMPLIFIER = 0;

    /**
     * [A] 적대 수중 몹 사냥 반경(블록). 바닐라 {@code getDestroyRangeAABB} 는 콘딧 칸을 8
     * 만큼 부풀리고 {@code pos.closerThan(target.blockPosition(), 8.0)} 로 다시 조인다 —
     * 범위 사다리와 <b>무관한 고정값</b>이다.
     */
    public static final int HUNT_RADIUS = 8;
    /** [A] 사냥 1회 피해. {@code livingentity.hurt(damageSources().magic(), 4.0F)} 다. */
    public static final int HUNT_DAMAGE = 4;

    /**
     * [CONDUIT] {@code #minecraft:conduit_frame} 태그의 네 블록인가. [A] 1.21.4 태그 원문은
     * prismarine · prismarine_bricks · dark_prismarine · sea_lantern 넷뿐이다 —
     * 계단 · 반 블록 · 담장 변형은 <b>들어가지 않는다</b>(태그가 풀 큐브 넷만 나열한다).
     *
     * <p>넷 다 [PRISMARINE] 트랙이 이미 등록한 ID 라 이 트랙은 프레임 재질을 신설하지 않았다.
     * client {@code ConduitRules.isConduitFrameBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isFrameBlock(int id) {
        return id == Blocks.PRISMARINE
                || id == Blocks.PRISMARINE_BRICKS
                || id == Blocks.DARK_PRISMARINE
                || id == Blocks.SEA_LANTERN;
    }

    /**
     * 콘딧 물 코어 판정에서 "물"로 치는 블록인가. [A] {@code level.isWaterAt} 은 유체 태그를
     * 보므로 원천 · 흐르는 물(40~47)뿐 아니라 <b>물 칸을 대신하는 단일-ID 수중 식생</b>
     * (켈프 · 해초 · 산호 식물형 · 부채 · 불우렁쉥이)도 물이다 — 바닐라에서 그 칸들은
     * waterlogged 라 {@code isWaterAt} 이 참이다. {@link Fluids#isWaterMedium} 이 정확히
     * 같은 집합이다.
     *
     * <p>산호 <b>블록</b> 10종은 여기 들어가지 않는다(풀 큐브 고체). 산호초 한가운데 콘딧을
     * 놓으면 코어가 깨져 켜지지 않는 것이 바닐라와 같다.
     */
    public static boolean isCoreWater(int id) {
        return Fluids.isWaterMedium(id);
    }

    /** 콘딧을 원점으로 한 상대 좌표 표본. 청크 밖은 {@code Blocks.AIR} 로 답하면 된다. */
    @FunctionalInterface
    public interface BlockSampler {
        int blockAt(int dx, int dy, int dz);
    }

    /**
     * [A] 프레임 후보 칸 42개의 상대 좌표를 (dx, dy, dz) 3개씩 담은 평평한 배열.
     * 클래스 로드 시 한 번만 만들고 그 뒤로는 읽지만 한다 — 랜덤틱 주기라도 5×5×5 는
     * 125칸이라 매 검사마다 삼중 루프를 다시 도는 것은 낭비다.
     */
    private static final int[] FRAME_OFFSETS = buildFrameOffsets();

    private static int[] buildFrameOffsets() {
        int[] out = new int[FRAME_SLOTS * 3];
        int at = 0;
        int r = FRAME_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int ax = Math.abs(dx);
                    int ay = Math.abs(dy);
                    int az = Math.abs(dz);
                    // [A] 원문 조건 그대로다. 앞 절은 "가운데 3×3×3 은 후보가 아니다",
                    // 뒤 절은 "세 축 정렬 평면 중 하나 위에 있고 그 평면 안에서 테두리다".
                    boolean outsideCore = ax > 1 || ay > 1 || az > 1;
                    boolean onRing = (dx == 0 && (ay == r || az == r))
                            || (dy == 0 && (ax == r || az == r))
                            || (dz == 0 && (ax == r || ay == r));
                    if (outsideCore && onRing) {
                        out[at++] = dx;
                        out[at++] = dy;
                        out[at++] = dz;
                    }
                }
            }
        }
        if (at != out.length) {
            // 후보 칸 수가 42 가 아니면 위 조건을 잘못 옮긴 것이다 — 조용히 다른 범위
            // 사다리를 내는 대신 클래스 로드 시점에 터뜨린다(구리 4연속 검사와 같은 자리).
            throw new IllegalStateException("conduit frame slot count is not " + FRAME_SLOTS);
        }
        return out;
    }

    /** [A] 프레임 후보 칸 42개의 상대 좌표 사본(진단 · 테스트용). */
    public static int[] frameOffsets() {
        return FRAME_OFFSETS.clone();
    }

    /**
     * [A] 프레임 블록 개수. 물 코어가 하나라도 깨져 있으면 <b>0</b>이다 — 바닐라
     * {@code updateShape} 가 그 자리에서 {@code return false} 하고 목록을 비운 채 끝내는
     * 것과 같다.
     *
     * <p>순수 함수다: 같은 표본을 주면 언제나 같은 수를 돌려준다.
     */
    public static int frameCount(BlockSampler sample) {
        int r = WATER_CORE_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!isCoreWater(sample.blockAt(dx, dy, dz))) return 0;
                }
            }
        }
        int count = 0;
        for (int at = 0; at < FRAME_OFFSETS.length; at += 3) {
            if (isFrameBlock(sample.blockAt(
                    FRAME_OFFSETS[at], FRAME_OFFSETS[at + 1], FRAME_OFFSETS[at + 2]))) {
                count++;
            }
        }
        return count;
    }

    /** [A] 프레임 블록 수가 활성 문턱(16) 이상인가. */
    public static boolean active(int frameCount) {
        return frameCount >= ACTIVATION_FRAME_BLOCKS;
    }

    /**
     * [A] 콘딧 파워가 닿는 반경(블록). 비활성이면 0 이다.
     *
     * <p>바닐라 {@code i / 7 * 16} 의 <b>정수 나눗셈 계단</b>이라 프레임 수가 조금 늘어도
     * 범위는 그대로 있다가 7개째마다 16씩 뛴다. 배정 문서가 말한 32 / 48 / 64 는 이 사다리의
     * 첫 세 칸이다 — 프레임 16–20 → 32, 21–27 → 48, 28–34 → 64, 35–41 → 80, 42 → 96.
     * (16 미만은 비활성이라 칸이 없고, 42 가 후보 전부라 96 이 상한이다.)
     */
    public static int effectRange(int frameCount) {
        if (!active(frameCount)) return 0;
        return frameCount / FRAME_BLOCKS_PER_STEP * RANGE_PER_STEP;
    }

    /**
     * [A] 적대 수중 몹을 사냥하는가. 바닐라 {@code updateDestroyTarget} 은 프레임이
     * <b>42 전부</b> 차 있을 때만 표적을 잡는다({@code if (i < 42) destroyTarget = null}) —
     * 범위 사다리와 달리 여기는 계단이 없고 완전 프레임 하나뿐이다.
     */
    public static boolean huntsHostiles(int frameCount) {
        return frameCount >= FRAME_SLOTS;
    }

    /**
     * [A] 콘딧 파워를 받을 거리인가. 바닐라
     * {@code pos.closerThan(player.blockPosition(), j)} 와 같은 <b>제곱 유클리드</b> 비교다
     * (축 최댓값이 아니다). 제곱으로 비교해 부동소수 제곱근을 타지 않는다.
     *
     * <p>"물에 잠겼거나 비를 맞는 중"({@code isInWaterOrRain}) 조건은 이 함수 밖이다 —
     * 그 상태의 소유자는 바이탈이지 콘딧이 아니다.
     */
    public static boolean reaches(int frameCount, int dx, int dy, int dz) {
        int range = effectRange(frameCount);
        if (range <= 0) return false;
        return dx * dx + dy * dy + dz * dz < range * range;
    }

    /** [A] 사냥 표적이 될 거리인가(고정 반경 8, 같은 제곱 유클리드 비교). */
    public static boolean huntReaches(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz < HUNT_RADIUS * HUNT_RADIUS;
    }

    /**
     * [A] 이번 서버 틱이 콘딧 맥박 틱인가. 바닐라 {@code gameTime % 40 == 0} 을 10 TPS 로
     * 옮긴 {@code tick % 20 == 0} 이다 — 모양 검사(5×5×5 = 125칸)를 매 틱 돌리지 않는
     * 자리이고, 랜덤틱이 표본 주기로 하는 일과 같은 역할이다.
     */
    public static boolean pulseDue(long serverTick) {
        return Math.floorMod(serverTick, PULSE_SERVER_TICKS) == 0;
    }

    /** 한 번의 맥박이 부여하는 상태이상 한 건. 비활성이면 {@code null} 이다. */
    public record PowerPulse(int amplifier, int durationMcTicks, int range) {
    }

    /**
     * [A] 활성 콘딧 한 대가 이번 맥박에 부여할 효과. 비활성이면 {@code null} 이라 호출부가
     * "켜졌는지"를 따로 묻지 않는다. 순수 함수라 대상 목록 · 좌표는 호출부의 몫이다.
     */
    public static PowerPulse powerPulse(int frameCount) {
        int range = effectRange(frameCount);
        if (range <= 0) return null;
        return new PowerPulse(POWER_AMPLIFIER, POWER_DURATION_MC_TICKS, range);
    }

    /**
     * [A] 이번 맥박에 적대 수중 몹에게 줄 피해. 프레임이 42 미만이면 0 이다 — 표적 선택
     * (범위 안 적대 몹 중 무작위 하나)은 난수를 쓰므로 이 순수 클래스 밖이고, 여기서는
     * "때리는가 · 얼마나"만 답한다.
     */
    public static int huntDamage(int frameCount) {
        return huntsHostiles(frameCount) ? HUNT_DAMAGE : 0;
    }
}
