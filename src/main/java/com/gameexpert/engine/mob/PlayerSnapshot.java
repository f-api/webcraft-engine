package com.gameexpert.engine.mob;

import com.gameexpert.engine.effect.StatusEffect;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 몹 AI 가 참조하는 플레이어의 읽기 전용 스냅샷.
 * 좌표는 §11.1 규약대로 발밑 중심(x,z=중심, y=AABB 바닥).
 * alive=false 인 플레이어는 추적/스폰/사격 대상에서 제외한다.
 */
@Getter
@Accessors(fluent = true)
public class PlayerSnapshot {

    private final String nickname;
    private final double x;
    private final double y;
    private final double z;
    private final boolean alive;
    /** 서버 pose 각도(라디안): yaw 0=-Z, pitch 양수=위. */
    private final float yaw;
    private final float pitch;
    private final boolean crouching;
    private final boolean carvedPumpkinHelmet;
    /** Piglin-neutrality fact derived from equipped armor by the authority adapter. */
    private final boolean wearingGoldArmor;
    /**
     * [ROTTEN-LEATHER] 언데드 무적대 판정용 사실 — 썩은 가죽 4부위를 전부 착용 중인가.
     * 금 방어구와 같은 자리에서 권위 어댑터가 파생시킨다({@link UndeadNeutralityRules}).
     */
    private final boolean wearingRottenLeatherSet;
    /** 현재 체력. 마녀 물약 선택(HP≥8 → 독)에 쓴다. */
    private final int health;
    /** 보유 중인 상태이상 비트마스크({@link StatusEffect#ordinal()}). */
    private final int effectMask;
    /**
     * [PHANTOM] 마지막 휴식 이후 경과 MC 틱(바닐라 {@code TIME_SINCE_REST}).
     * {@link MobSpawner#tryPhantomInsomnia} 만 읽는다. 이 사실을 실어 주지 않는 기존 호출부는
     * 0 을 본다 — 0 이면 불면 관문이 절대 열리지 않으므로 안전한 기본값이다.
     */
    private final long timeSinceRestMcTicks;
    /**
     * [NAUTILUS-TEMPT] 지금 손에 든(선택 슬롯) 아이템 타입. 유혹 계약({@link TemptationRules})
     * 만 읽는다 — 이 사실을 실어 주지 않는 기존 호출부는 0(빈 손)을 보고, 0 이면 어떤 종의
     * 유혹 관문도 열리지 않으므로 안전한 기본값이다(불면 시간 0 과 같은 규약).
     *
     * <p><b>와이어 필드가 아니다.</b> 이 값은 권위 내부에서만 흐르고 WS 스냅샷·프로토콜에는
     * 실리지 않는다 — 몹 AI 가 읽는 서버 내부 사실이라 클라가 알 필요가 없고, 남의 손 아이템을
     * 방송하면 신뢰 경계가 넓어진다. 인벤토리 전체가 아니라 <b>타입 하나</b>만 싣는 것도 같은
     * 이유다(금 방어구·썩은 가죽 세트 사실이 불리언 하나인 것과 같은 자리).
     */
    private final int heldItemType;
    /** Offhand counterpart of {@link #heldItemType}; temptation checks either hand. */
    private final int offhandItemType;

    /** 기존 몹 테스트/호출부용: 시선 정보가 없으면 응시 판정을 하지 않는다. */
    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive) {
        this(nickname, x, y, z, alive, Float.NaN, Float.NaN, false, false);
    }

    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet) {
        this(nickname, x, y, z, alive, yaw, pitch, crouching, carvedPumpkinHelmet, 20, 0);
    }

    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet, int health, int effectMask) {
        this(nickname, x, y, z, alive, yaw, pitch, crouching,
                carvedPumpkinHelmet, false, health, effectMask);
    }

    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet, boolean wearingGoldArmor,
                          int health, int effectMask) {
        this(nickname, x, y, z, alive, yaw, pitch, crouching,
                carvedPumpkinHelmet, wearingGoldArmor, false, health, effectMask);
    }

    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet, boolean wearingGoldArmor,
                          boolean wearingRottenLeatherSet, int health, int effectMask) {
        this(nickname, x, y, z, alive, yaw, pitch, crouching, carvedPumpkinHelmet,
                wearingGoldArmor, wearingRottenLeatherSet, health, effectMask, 0L);
    }

    /** [PHANTOM] 불면 시간까지 실은 생성자. 손 아이템 사실은 0(빈 손)으로 둔다. */
    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet, boolean wearingGoldArmor,
                          boolean wearingRottenLeatherSet, int health, int effectMask,
                          long timeSinceRestMcTicks) {
        this(nickname, x, y, z, alive, yaw, pitch, crouching, carvedPumpkinHelmet,
                wearingGoldArmor, wearingRottenLeatherSet, health, effectMask,
                timeSinceRestMcTicks, 0, 0);
    }

    /**
     * [NAUTILUS-TEMPT] 손 아이템까지 실은 최종 생성자. 권위 어댑터만 이 자리를 채운다.
     * 이 축은 <b>append-only</b> 다 — 위의 생성자들이 전부 이 자리로 위임하며 0 을 채우므로
     * 기존 호출부는 한 글자도 바뀌지 않고 유혹 관문만 닫힌 채로 남는다.
     */
    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet, boolean wearingGoldArmor,
                          boolean wearingRottenLeatherSet, int health, int effectMask,
                          long timeSinceRestMcTicks, int heldItemType) {
        this(nickname, x, y, z, alive, yaw, pitch, crouching, carvedPumpkinHelmet,
                wearingGoldArmor, wearingRottenLeatherSet, health, effectMask,
                timeSinceRestMcTicks, heldItemType, 0);
    }

    public PlayerSnapshot(String nickname, double x, double y, double z, boolean alive,
                          float yaw, float pitch, boolean crouching,
                          boolean carvedPumpkinHelmet, boolean wearingGoldArmor,
                          boolean wearingRottenLeatherSet, int health, int effectMask,
                          long timeSinceRestMcTicks, int heldItemType, int offhandItemType) {
        this.heldItemType = Math.max(0, heldItemType);
        this.offhandItemType = Math.max(0, offhandItemType);
        this.timeSinceRestMcTicks = Math.max(0L, timeSinceRestMcTicks);
        this.wearingRottenLeatherSet = wearingRottenLeatherSet;
        this.nickname = nickname;
        this.x = x;
        this.y = y;
        this.z = z;
        this.alive = alive;
        this.yaw = yaw;
        this.pitch = pitch;
        this.crouching = crouching;
        this.carvedPumpkinHelmet = carvedPumpkinHelmet;
        this.wearingGoldArmor = wearingGoldArmor;
        this.health = health;
        this.effectMask = effectMask;
    }

    /** 상태이상 목록을 비트마스크로 압축한다. 스냅샷은 불변이므로 생성 시점에 한 번만 계산한다. */
    public static int maskOf(com.gameexpert.engine.effect.StatusEffects effects) {
        int mask = 0;
        for (StatusEffect effect : StatusEffect.values()) {
            if (effects.has(effect)) mask |= 1 << effect.ordinal();
        }
        return mask;
    }

    public boolean hasEffect(StatusEffect effect) {
        return (effectMask & (1 << effect.ordinal())) != 0;
    }
}
