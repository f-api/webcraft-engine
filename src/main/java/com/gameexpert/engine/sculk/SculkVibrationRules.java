package com.gameexpert.engine.sculk;

import com.gameexpert.terrain.Blocks;

/**
 * [DEEP-DARK] 스컬크 감지체·비명체의 <b>축소 진동 계약</b>.
 *
 * <p>이 저장소에는 전면 레드스톤이 없다 — 바닐라의 감지체는 진동을 레드스톤 신호 세기로
 * 내보내고 그 신호가 비명체를 깨우지만, 여기에는 실어 나를 회로가 없다. 그래서 계약을
 * 다음으로 <b>줄인다</b>:
 *
 * <ul>
 *   <li>감지체는 반경 {@link #LISTEN_RADIUS} 안의 {@link Event} 를 듣고 스스로 활성화한다.</li>
 *   <li>활성화된 감지체는 <b>같은 반경 안의 비명체를 직접</b> 깨운다(레드스톤 경유 없음).</li>
 *   <li>비명체는 경고 단계를 1 올리고, {@link #WARNING_LEVEL_MAX} 에서 한 번 더 울리면
 *       {@link WardenSummonSink} 를 부른다.</li>
 * </ul>
 *
 * <p>줄인 것은 <b>전달 매체</b>뿐이고 수치는 전부 바닐라다. 근거 등급:
 * [B] minecraft.wiki «Sculk Sensor»(청취 반경 8 · 활성 40 틱) · «Sculk Shrieker»(플레이어당
 * 10초 쿨다운 · 4번째 활성화에서 워든 · 경고 단계는 12000 틱마다 1 감소 · 비명 후 반경 40 의
 * 플레이어에게 어둠 12초). [C] 감지체→비명체 직결과 {@link Event} 목록은 이 저장소 계약이다.
 *
 * <p>시간 단위는 전부 <b>MC 틱(20 TPS)</b> 이다. 서버 틱 1회가 MC 2틱을 누적하는 기존
 * {@code StatusEffects} 규약과 같다.
 *
 * <p>상태가 없는 순수 규칙 클래스다 — 어느 블록이 어떤 경고 단계인지는 월드 블록 상태
 * 바이트가 소유하고, 이 클래스는 그 바이트를 읽고 쓰는 방법만 정한다.
 */
public final class SculkVibrationRules {

    /** 감지체가 진동을 듣는 구 반경(블록). [B] */
    public static final int LISTEN_RADIUS = 8;
    /** 감지체가 활성 상태로 머무는 시간. [B] 바닐라 sculk sensor 는 40 틱 뒤 식는다. */
    public static final int SENSOR_ACTIVE_MC_TICKS = 40;
    /** 비명체의 플레이어당 재활성화 쿨다운(10초). [B] */
    public static final int SHRIEK_COOLDOWN_MC_TICKS = 200;
    /** 경고 단계 상한. 이 단계에서 한 번 더 울리면 워든이 나온다(= 4번째 활성화). [B] */
    public static final int WARNING_LEVEL_MAX = 3;
    /** 경고 단계가 1 내려가는 데 걸리는 시간(12000 MC 틱 = 10분). [B] */
    public static final int WARNING_DECAY_MC_TICKS = 12_000;
    /** 비명이 끝나고 어둠을 받는 플레이어의 최대 거리. [B] */
    public static final int DARKNESS_RADIUS = 40;
    /** 부여되는 어둠의 길이(12초). [B] */
    public static final int DARKNESS_MC_TICKS = 240;
    /** 어둠의 앰프. 바닐라 어둠은 레벨 I 하나뿐이다. [B] */
    public static final int DARKNESS_AMPLIFIER = 0;

    /** 감지체 상태 바이트에서 "활성" 을 나타내는 비트. 나머지 비트는 쓰지 않는다. */
    public static final int SENSOR_ACTIVE_BIT = 0x01;
    /** 비명체 상태 바이트의 경고 단계 마스크(0~3). */
    public static final int SHRIEKER_WARNING_MASK = 0x03;
    /**
     * [WORLD-GEOMETRY] 비명체 {@code can_summon}(비트 2). 생성(고대 도시 · 스컬크 패치) 비명체만 참이고 설치한
     * 비명체는 거짓이다. 바닐라 {@code SculkShriekerBlockEntity#canRespond} 가 이 값을 봐서, 거짓이면 울기만 하고
     * 경고 단계 · 어둠 · 감시자 소환이 없다(javap: tryShriek → canRespond → tryToWarn, tryRespond → canRespond).
     */
    public static final int SHRIEKER_CAN_SUMMON = 0x04;

    private SculkVibrationRules() {}

    /**
     * 감지체가 듣는 진동 종류. 바닐라 {@code GameEvent} 목록의 <b>축소판</b>이다 — 이 저장소가
     * 실제로 권위 이벤트로 가지고 있는 넷만 담는다. 목록을 늘리는 것은 append-only 다. [C]
     */
    public enum Event {
        /** 플레이어·몹이 블록을 설치했다. */
        BLOCK_PLACE,
        /** 플레이어·몹이 블록을 부쉈다. */
        BLOCK_BREAK,
        /** 걷기·달리기 발소리. 웅크림은 바닐라와 같이 진동을 내지 않는다. */
        STEP,
        /** 발사체가 착탄했다. */
        PROJECTILE_LAND,
        /** [BLOCK-SHAPES] 블록 상태만 바뀐 사건(바닐라 GameEvent.BLOCK_CHANGE) — 종 치기가 낸다. */
        BLOCK_CHANGE,
        /** Hanging entity destroyed (MC-253023). */
        ENTITY_DIE
    }

    /**
     * 워든 소환 훅. 두 권위의 코어가 각자 자기 몹 원장에 종을 세우는 구현을 끼운다
     * ({@code WorldTickLoop} · {@code StandaloneWorldRuntime}). {@link #NO_OP} 는 사슬만
     * 돌리는 테스트·진단용으로 남는다.
     */
    public interface WardenSummonSink {
        /** 비명체 좌표에서 워든 소환을 요청한다. 구현이 없으면 아무 일도 일어나지 않는다. */
        void summonWarden(int x, int y, int z);

        /**
         * 네 번째 비명을 실제로 깨운 플레이어까지 함께 넘기는 소환. 코어는 이 닉네임으로
         * 갓 나온 워든의 <b>어그로를 초기화</b>한다 — 바닐라도 소환한 비명체가
         * {@code Warden#increaseAngerAt} 을 그 플레이어에게 걸고 나온다. 기본 구현은 좌표만
         * 쓰는 옛 계약으로 위임하므로 기존 구현·람다는 그대로 컴파일된다.
         */
        default void summonWarden(int x, int y, int z, String nickname) {
            summonWarden(x, y, z);
        }

        /** 사슬만 돌릴 때의 기본 구현. 소환 조건 판정 자체는 이미 권위에서 돌고 있다. */
        WardenSummonSink NO_OP = (x, y, z) -> { };
    }

    /**
     * 비명이 부여하는 어둠 한 벌. 지속과 앰프를 따로 들고 다니면 한쪽만 갱신되는 자리가
     * 생기므로 <b>한 값</b>으로 묶는다 — 부여 지점은 이 하나만 읽는다.
     */
    public record DarknessGrant(int amplifier, int durationMcTicks) {}

    /** 비명체가 부여하는 어둠. 바닐라는 레벨 I · 12초 하나뿐이다. */
    public static DarknessGrant darknessGrant() {
        return new DarknessGrant(DARKNESS_AMPLIFIER, DARKNESS_MC_TICKS);
    }

    /** 활성화 이후 이만큼 지났으면 감지체가 식었는가. */
    public static boolean sensorCooledDown(long elapsedMcTicks) {
        return elapsedMcTicks >= SENSOR_ACTIVE_MC_TICKS;
    }

    /** 마지막 비명 이후 이만큼 지났으면 이 플레이어가 다시 비명을 깨울 수 있는가. */
    public static boolean shriekCooledDown(long elapsedMcTicks) {
        return elapsedMcTicks >= SHRIEK_COOLDOWN_MC_TICKS;
    }

    /** 감지체가 이 상대 좌표의 진동을 듣는가. 바닐라와 같이 <b>구</b> 반경이다. */
    public static boolean withinListenRange(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz <= LISTEN_RADIUS * LISTEN_RADIUS;
    }

    /** 비명이 끝난 뒤 이 상대 좌표의 플레이어가 어둠을 받는가. */
    public static boolean withinDarknessRange(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz <= DARKNESS_RADIUS * DARKNESS_RADIUS;
    }

    /** 감지체 상태 바이트 → 활성 여부. */
    public static boolean sensorActive(int state) {
        return (state & SENSOR_ACTIVE_BIT) != 0;
    }

    /** 활성 여부 → 감지체 상태 바이트. */
    public static int sensorState(boolean active) {
        return active ? SENSOR_ACTIVE_BIT : 0;
    }

    /** 비명체 상태 바이트 → 경고 단계(0~3). */
    public static int warningLevel(int state) {
        return state & SHRIEKER_WARNING_MASK;
    }

    /** 경고 단계 → 비명체 상태 바이트. 범위를 벗어난 값은 잘라 넣는다. */
    public static int shriekerState(int warningLevel) {
        return clampWarningLevel(warningLevel) & SHRIEKER_WARNING_MASK;
    }

    /** 경고 단계 + can_summon → 비명체 상태 바이트. */
    public static int shriekerState(int warningLevel, boolean canSummon) {
        return shriekerState(warningLevel) | (canSummon ? SHRIEKER_CAN_SUMMON : 0);
    }

    /** 비명체 상태 바이트의 can_summon. */
    public static boolean canSummon(int state) {
        return (state & SHRIEKER_CAN_SUMMON) != 0;
    }

    /**
     * 이번 비명이 워든을 부르는가. 바닐라 문구는 "4번째 활성화" 이고, 그 시점의 <b>직전</b>
     * 경고 단계가 {@link #WARNING_LEVEL_MAX}(=3) 이다. 단계를 올리기 <b>전에</b> 묻는다.
     */
    public static boolean summonsWarden(int currentWarningLevel) {
        return clampWarningLevel(currentWarningLevel) >= WARNING_LEVEL_MAX;
    }

    /** 비명 한 번이 올린 경고 단계. 상한에서 더 올라가지 않는다(그 자리에서 소환된다). */
    public static int nextWarningLevel(int currentWarningLevel) {
        return Math.min(WARNING_LEVEL_MAX, clampWarningLevel(currentWarningLevel) + 1);
    }

    /**
     * 마지막 비명 이후 {@code elapsedMcTicks} 가 지난 뒤 남는 경고 단계.
     * 12000 MC 틱마다 정확히 1 씩 내려간다.
     */
    public static int decayedWarningLevel(int currentWarningLevel, long elapsedMcTicks) {
        if (elapsedMcTicks <= 0) return clampWarningLevel(currentWarningLevel);
        long steps = elapsedMcTicks / WARNING_DECAY_MC_TICKS;
        long remaining = clampWarningLevel(currentWarningLevel) - steps;
        return remaining <= 0 ? 0 : (int) remaining;
    }

    /** 이 블록이 진동을 듣는 감지체인가. */
    public static boolean isSensor(int blockType) {
        return blockType == Blocks.SCULK_SENSOR;
    }

    /** 이 블록이 경고 단계를 쌓는 비명체인가. */
    public static boolean isShrieker(int blockType) {
        return blockType == Blocks.SCULK_SHRIEKER;
    }

    private static int clampWarningLevel(int value) {
        if (value < 0) return 0;
        return Math.min(value, WARNING_LEVEL_MAX);
    }
}
