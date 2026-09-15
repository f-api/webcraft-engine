package com.gameexpert.engine.validation;

import java.util.HashMap;
import java.util.Map;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 서버가 받아들이는 블록 <b>파괴 속도</b>의 상한. 채굴 진행률 자체는 클라 권위지만, 조작 클라가
 * 채굴 시간을 건너뛰고 즉시·연속으로 파괴하는 것은 서버가 받아들이지 않는다.
 *
 * <p>두 겹으로 막는다.
 * <ol>
 *   <li><b>최소 채굴 시간</b>: 마지막으로 승인한 파괴로부터
 *       {@link MiningRules#minimumBreakSeconds} 의 {@link #ALLOWED_FRACTION} 만큼도 지나지 않았으면
 *       이 파괴를 무시한다. 한 번의 채굴은 한 좌표에서 끝나므로(클라 Interaction 계약) 연속 파괴
 *       사이의 간격이 곧 그 블록에 들인 채굴 시간이다.</li>
 *   <li><b>파괴 레이트 예산</b>: hardness 0(즉시 파괴) 블록에는 최소 시간이 없으므로,
 *       초당 {@link #MAX_BREAKS_PER_SECOND} 회·버스트 {@link #BURST_BREAKS} 회의 공통 예산을 둔다.
 *       클라는 블록마다 좌클릭을 새로 눌러야 하므로 사람 손으로는 닿을 수 없는 값이다.</li>
 * </ol>
 *
 * <p>오탐이 없는 이유: 최소 시간은 <b>인벤토리에 든 최선의 도구</b>로 계산하고 다시 절반으로
 * 깎은 값이다. 실제 클라 진행률은 그 도구보다 빠를 수 없으므로, 네트워크 지연·틱 경계로 파괴
 * 요청이 한두 틱 당겨 도착해도 정상 채굴은 이 상한 아래로 내려가지 않는다.
 *
 * <p>상한을 넘은 파괴는 호출자가 <b>롤백</b>한다(킥·밴 없음). 클라의 낙관 반영은 되돌아가고
 * 플레이어는 그 블록을 다시 캐면 된다.
 */
public final class MiningLimits {

    /** 이론 최소 채굴 시간 중 실제로 강제하는 비율(나머지는 지연·틱 경계 여유). */
    public static final double ALLOWED_FRACTION = 0.5;

    /** 최소 시간이 없는 블록까지 포함한 공통 파괴 상한(회/초). */
    public static final double MAX_BREAKS_PER_SECOND = 20.0;

    /** 지연 뒤 몰아 도착한 파괴를 위해 쌓아 둘 수 있는 최대 예산(회). */
    public static final double BURST_BREAKS = 40.0;

    private static final double BREAKS_PER_TICK = MAX_BREAKS_PER_SECOND / 10.0;

    /** 10 TPS 기준 초 → 틱. */
    private static final double TICKS_PER_SECOND = 10.0;

    private final Map<String, Budget> budgets = new HashMap<>();

    /**
     * 이 파괴를 서버 권위 월드에 반영해도 되는가. 거짓이면 호출자는 파괴를 무시하고 현재 블록을
     * 다시 방송해 클라 낙관 반영을 되돌려야 한다.
     */
    public boolean accept(String nickname, long tickNo, int blockType, PlayerInventory inventory) {
        return accept(nickname, tickNo, blockType, inventory, 1.0);
    }

    /**
     * [BEACON] 상태이상 채굴 가속(성급함 · 콘딧 파워, {@code StatusEffects.digSpeedMultiplier})을 반영한
     * 판정. 가속 배율만큼 최소 시간이 줄어든다 — 그러지 않으면 성급함 II 의 정상 채굴이 여유 비율을
     * 먼저 깎아 먹는다. 채굴 피로처럼 느려지는 효과는 상한만 보는 이 검증과 무관하다(항상 통과).
     */
    public boolean accept(String nickname, long tickNo, int blockType, PlayerInventory inventory,
            double effectSpeedMultiplier) {
        Budget budget = budgets.computeIfAbsent(nickname, ignored -> new Budget(tickNo));
        long elapsedTicks = Math.max(0L, tickNo - budget.lastTickNo);
        budget.lastTickNo = tickNo;
        budget.breaks = Math.min(BURST_BREAKS, budget.breaks + elapsedTicks * BREAKS_PER_TICK);
        if (budget.breaks < 1.0) return false;

        long requiredTicks = requiredTicks(blockType, inventory, effectSpeedMultiplier);
        if (tickNo - budget.lastBreakTickNo < requiredTicks) return false;

        budget.breaks -= 1.0;
        budget.lastBreakTickNo = tickNo;
        return true;
    }

    /** 직전 파괴로부터 이만큼의 틱이 지나야 이 블록을 부술 수 있다. */
    public static long requiredTicks(int blockType, PlayerInventory inventory) {
        return requiredTicks(blockType, inventory, 1.0);
    }

    /** [BEACON] 상태이상 채굴 가속 배율(1 이상)로 나눈 최소 틱. 1 미만 값은 1 로 본다. */
    public static long requiredTicks(int blockType, PlayerInventory inventory,
            double effectSpeedMultiplier) {
        double speed = Double.isFinite(effectSpeedMultiplier) ? Math.max(1.0, effectSpeedMultiplier) : 1.0;
        double seconds = MiningRules.minimumBreakSeconds(blockType, inventory) * ALLOWED_FRACTION / speed;
        return (long) Math.floor(seconds * TICKS_PER_SECOND);
    }

    /** 서버가 위치를 강제한 뒤(입장·부활) 파괴 추적을 초기화한다. */
    public void reset(String nickname, long tickNo) {
        budgets.put(nickname, new Budget(tickNo));
    }

    /** 퇴장한 플레이어의 추적 상태를 지운다. */
    public void forget(String nickname) {
        budgets.remove(nickname);
    }

    int trackedPlayersForTest() {
        return budgets.size();
    }

    private static final class Budget {
        private long lastTickNo;
        private long lastBreakTickNo;
        private double breaks = BURST_BREAKS;

        private Budget(long tickNo) {
            this.lastTickNo = tickNo;
            // 입장 직후 첫 파괴가 최소 시간에 걸리지 않도록, 직전 파괴 시각은 충분히 과거로 둔다.
            this.lastBreakTickNo = tickNo - Long.MAX_VALUE / 4;
        }
    }
}
