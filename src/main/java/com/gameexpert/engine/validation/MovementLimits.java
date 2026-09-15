package com.gameexpert.engine.validation;

import java.util.HashMap;
import java.util.Map;

import com.gameexpert.terrain.Blocks;

/**
 * 서버가 기록하는 플레이어 pose 의 상한. 이동 자체는 클라 권위이지만, 조작된 클라가 임의 좌표로
 * 순간이동하거나 월드 밖으로 나가는 것은 서버가 받아들이지 않습니다.
 *
 * <p>판정은 <b>거리 예산</b>입니다. 플레이어마다 틱당 {@code MAX_SPEED_BLOCKS_PER_SECOND/10} 블록의
 * 예산이 {@code BURST_ALLOWANCE_BLOCKS} 까지 쌓이고, 승인한 이동 거리만큼 차감합니다. 지연 뒤 여러
 * pose 가 한 틱에 몰려 도착해도 그동안 쌓인 예산으로 통과하므로 배치 도착이 오탐이 되지 않습니다.
 *
 * <p>상한을 넘은 pose 는 <b>무시</b>합니다(킥·밴 없음). 예산은 차감하지 않으므로 잠시 뒤 다시 쌓인
 * 예산으로 자연히 재동기화되고, 그래도 {@code FORCED_RESYNC_TICKS} 동안 한 번도 승인되지 않으면
 * 다음 pose 를 승인해 정상 플레이어가 영구히 굳지 않게 합니다. 이 <b>강제 재동기화에도 거리
 * 상한이 있습니다</b>: 거절이 시작된 시점부터 최대 속도로 갈 수 있는 거리에 버스트를 더한
 * 값까지만 승인합니다. 거절이 이어지는 동안 클라가 실제로 갈 수 있는 최대 거리가 그 값이므로
 * 정상 플레이(겉날개 활공 78.4, 청크 지연 뒤 배치 도착)는 걸리지 않고, 임의 좌표 순간이동만
 * 남습니다. 거절이 아예 없던 AFK 시간은 이 창을 늘리지 않습니다.
 *
 * <p>상한 근거(클라 물리 정본 {@code client/src/physics/constants.ts}):
 * 스프린트 5.6, 보트 8, 디버그 비행 24, 낙하 종단속도 60 블록/초. 낙하하며 스프린트한 최악의 정상
 * 조합이 √(60²+5.6²)=60.3 블록/초입니다.
 *
 * <p><b>겉날개 활공 예산 계약</b>(근거: {@code docs/MC-REFERENCE.md} 겉날개/폭죽 절,
 * {@code client/src/player/ElytraGlide.ts}). 활공은 바닐라 20 TPS 식을 그대로 쓰므로 일반 낙하의
 * 종단속도 60 블록/초 클램프를 지나갑니다. 최악값은 두 가지입니다.
 * <ul>
 *   <li>수직 급강하: 시선이 수직이면 양력항이 0이라 매 틱 {@code (vy − 0.08) × 0.98} 이고
 *       종단속도는 {@code 0.08 × 0.98 / 0.02 = 3.92} 블록/틱 = <b>78.4 블록/초</b>입니다.
 *       항력이 세 성분에 같이 걸려 대각 강하의 총속력도 이 값을 넘지 못합니다.</li>
 *   <li>폭죽 순항: 부스트는 {@code v' = 0.5v + look × 0.85} 로 수축하므로 활공 중력·항력과 합친
 *       고정점이 약 {@code 0.93 / (1 − 0.5 × 0.99) ≈ 1.84} 블록/틱 = <b>약 37 블록/초</b>입니다.</li>
 * </ul>
 * 따라서 활공 최악값 78.4 블록/초에도 100 블록/초는 <b>1.28배</b> 여유가 남아 상한을 올릴 필요가
 * 없습니다. 버스트 256 블록은 활공 최악 속도로도 3.2초 분량이라 {@code FORCED_RESYNC_TICKS}(3초)와
 * 같은 크기의 지연 배치를 그대로 흡수합니다. 월드 높이 384 블록보다 크게 잡을 이유는 없습니다.
 */
public final class MovementLimits {

    /** 승인 속도 상한(블록/초). 정상 최대 조합 60.3 블록/초의 1.66배. */
    public static final double MAX_SPEED_BLOCKS_PER_SECOND = 100.0;

    /** 지연 뒤 몰아 도착한 pose 를 위해 쌓아 둘 수 있는 최대 예산(블록). */
    public static final double BURST_ALLOWANCE_BLOCKS = 256.0;

    /** 이 틱 수 동안 한 번도 승인하지 못하면 다음 pose 를 무조건 승인해 재동기화합니다(10 TPS 기준 3초). */
    public static final long FORCED_RESYNC_TICKS = 30;

    /** 바닐라 월드 경계와 같은 수평 좌표 상한. */
    public static final double MAX_HORIZONTAL_COORDINATE = 30_000_000.0;

    /** 기반암 아래 여유 64칸. 정상 플레이는 기반암을 못 뚫어 이 아래로 갈 수 없습니다. */
    public static final double MIN_ACCEPTED_Y = Blocks.MIN_Y - 64.0;

    /** 건축 한계 위 여유 64칸. 최고 블록(319) 위에서 점프해도 321 정도입니다. */
    public static final double MAX_ACCEPTED_Y = Blocks.MAX_Y + 64.0;

    private static final double BLOCKS_PER_TICK = MAX_SPEED_BLOCKS_PER_SECOND / 10.0;

    private final Map<String, Budget> budgets = new HashMap<>();

    /** 좌표 자체가 월드 안인가(무한대·NaN 은 호출 전 WS 필드 검증에서 걸러집니다). */
    public static boolean withinWorldBounds(double x, double y, double z) {
        return Math.abs(x) <= MAX_HORIZONTAL_COORDINATE
                && Math.abs(z) <= MAX_HORIZONTAL_COORDINATE
                && y >= MIN_ACCEPTED_Y && y <= MAX_ACCEPTED_Y;
    }

    /**
     * 서버 권위 pose 를 {@code from} 에서 {@code to} 로 옮겨도 되는지 판정합니다.
     * 거짓이면 호출자는 이 이동을 통째로 무시하고 기존 pose 를 유지해야 합니다.
     */
    public boolean accept(String nickname, long tickNo,
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ) {
        Budget budget = budgets.computeIfAbsent(nickname, ignored -> new Budget(tickNo));
        long elapsedTicks = Math.max(0L, tickNo - budget.lastTickNo);
        budget.lastTickNo = tickNo;
        budget.blocks = Math.min(BURST_ALLOWANCE_BLOCKS,
                budget.blocks + elapsedTicks * BLOCKS_PER_TICK);

        // 월드 밖 좌표는 예산과 무관하게 거부하되, 강제 재동기화 대상도 아닙니다.
        if (!withinWorldBounds(toX, toY, toZ)) return false;

        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > budget.blocks) {
            if (budget.rejectingSinceTickNo == NOT_REJECTING) budget.rejectingSinceTickNo = tickNo;
            // 오래 거절만 이어지면 서버 pose 가 클라와 영구히 어긋난 상태이므로 한 번 맞춰 줍니다.
            if (tickNo - budget.lastAcceptedTickNo < FORCED_RESYNC_TICKS) return false;
            // 다만 재동기화도 "그동안 갈 수 있었던 거리"까지입니다. 그 밖은 순간이동이므로
            // 승인하지 않고, 거절 창은 계속 자라 정상 클라는 곧 이 상한 안으로 들어옵니다.
            if (distance > forcedResyncAllowance(budget, tickNo)) return false;
            budget.blocks = 0.0;
            budget.lastAcceptedTickNo = tickNo;
            budget.rejectingSinceTickNo = NOT_REJECTING;
            return true;
        }
        budget.blocks -= distance;
        budget.lastAcceptedTickNo = tickNo;
        budget.rejectingSinceTickNo = NOT_REJECTING;
        return true;
    }

    /** 거절이 시작된 뒤 최대 속도로 갈 수 있는 거리 + 버스트. 재동기화가 승인할 수 있는 최대 이동. */
    private static double forcedResyncAllowance(Budget budget, long tickNo) {
        long rejectingTicks = Math.max(0L, tickNo - budget.rejectingSinceTickNo);
        return rejectingTicks * BLOCKS_PER_TICK + BURST_ALLOWANCE_BLOCKS;
    }

    /** 서버가 pose 를 강제한 뒤(입장·부활) 예산과 기준 시각을 초기화합니다. */
    public void reset(String nickname, long tickNo) {
        budgets.put(nickname, new Budget(tickNo));
    }

    /** 퇴장한 플레이어의 추적 상태를 지웁니다. */
    public void forget(String nickname) {
        budgets.remove(nickname);
    }

    int trackedPlayersForTest() {
        return budgets.size();
    }

    /** 지금은 거절 상태가 아님을 나타내는 표식. */
    private static final long NOT_REJECTING = Long.MIN_VALUE;

    private static final class Budget {
        private long lastTickNo;
        private long lastAcceptedTickNo;
        private long rejectingSinceTickNo = NOT_REJECTING;
        private double blocks = BURST_ALLOWANCE_BLOCKS;

        private Budget(long tickNo) {
            this.lastTickNo = tickNo;
            this.lastAcceptedTickNo = tickNo;
        }
    }
}
