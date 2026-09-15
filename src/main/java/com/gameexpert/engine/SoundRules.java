package com.gameexpert.engine;

import java.util.Set;

/**
 * 소리 사실의 전송 반경 규칙. Minecraft Java 1.21.4 근거:
 * <ul>
 *   <li>{@code SoundEvent.getRange(volume)} = {@code fixedRange.orElse(volume > 1.0F ? 16.0F * volume : 16.0F)}
 *       — volume 이 1 이하이면 반경은 볼륨과 무관하게 16블록이다.</li>
 *   <li>{@code ServerLevel.playSeededSound} 는 그 반경을 {@code PlayerList.broadcast} 에 넘기고,
 *       거리 제곱이 반경 제곱보다 작은 플레이어에게만 {@code ClientboundSoundPacket} 을 보낸다.
 *       즉 반경 밖에는 패킷 자체가 가지 않는다.</li>
 *   <li>{@code ServerLevel.sendParticles} 는 강제 전송이 아니면 32블록 안에만 파티클을 보낸다.</li>
 * </ul>
 * WebCraft 의 몹·월드 사운드는 모두 volume ≤ 1 이므로 기본 반경은 16이다. 다만 일부
 * {@code worldSound} kind 는 클라이언트가 파티클까지 파생시키는 겸용 사실이라, 시각 사실이
 * 잘리지 않도록 바닐라 파티클 반경 32를 쓴다.
 */
public final class SoundRules {
    /** volume ≤ 1 인 모든 소리의 바닐라 전송 반경(블록). */
    public static final double RANGE = 16.0;
    /** 파티클을 함께 파생시키는 사실의 전송 반경(블록). */
    public static final double PARTICLE_RANGE = 32.0;
    /** 클라이언트가 소리와 함께 파티클도 만드는 worldSound kind. */
    public static final Set<String> PARTICLE_WORLD_SOUNDS =
            Set.of("fishing_cast", "fishing_bite", "petal_pouch", "end_portal_frame_fill");

    /**
     * volume &gt; 1 로 나는 worldSound kind. 바닐라 {@code SculkShriekerBlockEntity#shriek} 는
     * {@code SoundEvents.SCULK_SHRIEKER_SHRIEK} 을 volume 2.0F 로 내므로
     * {@code getRange(2.0F) = 16 × 2 = 32} 다. 감지체 클릭은 volume 1.0F 라 기본 16 그대로다.
     */
    public static final Set<String> LOUD_WORLD_SOUNDS = Set.of("sculk_shriek", "bell_use");

    /**
     * [TRIAL-GAP] 레벨 이벤트 전송 반경. 26.3-snapshot-7 javap {@code ServerLevel.levelEvent}:
     * {@code PlayerList.broadcast(player, pos.getX(), pos.getY(), pos.getZ(), 64.0, ...)} — 블록
     * 좌표(정수, 중심 보정 없음)에서 64블록 미만의 플레이어에게만 보낸다.
     */
    public static final double LEVEL_EVENT_RANGE = 64.0;

    /**
     * [TRIAL-GAP] 폭발로 나는 worldSound kind. 바닐라 {@code ServerLevel.explode} 는
     * {@code ClientboundExplodePacket} 을 64블록 미만의 플레이어에게 보내고 클라가 volume 4 로 재생한다.
     */
    // [MACE] 돌풍(wind_burst) 인챈트의 ServerLevel.explode(… entity.wind_charge.wind_burst) 도 폭발 패킷이다.
    public static final Set<String> EXPLOSION_WORLD_SOUNDS = Set.of("wind_charged_burst", "wind_burst");
    /** [TRIAL-GAP] 폭발 패킷 전송 반경(블록). */
    public static final double EXPLOSION_RANGE = 64.0;

    private SoundRules() {}

    public static double worldSoundRange(String kind) {
        if (EXPLOSION_WORLD_SOUNDS.contains(kind)) return EXPLOSION_RANGE;
        if (PARTICLE_WORLD_SOUNDS.contains(kind) || LOUD_WORLD_SOUNDS.contains(kind)) {
            return PARTICLE_RANGE;
        }
        return RANGE;
    }

    /** 바닐라 PlayerList.broadcast 와 같은 제곱거리 비교(경계는 제외). */
    public static boolean audible(double soundX, double soundY, double soundZ,
            double listenerX, double listenerY, double listenerZ, double range) {
        double dx = soundX - listenerX;
        double dy = soundY - listenerY;
        double dz = soundZ - listenerZ;
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
