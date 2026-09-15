package com.gameexpert.engine.sculk;

import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.Warden;

/**
 * [DEEP-DARK] 스컬크 비명체 4단계 경고가 실제 워든을 세우는 <b>한 지점</b>.
 *
 * <p>사슬({@link SculkVibrationSystem})은 "상한에서 매 비명마다 싱크를 부른다" 까지만 책임지고,
 * <b>중복 방지·출현 연출·어그로 초기화</b>는 전부 여기에 있다 —
 * {@code WardenSummonIntegrationTest#chainKeepsCallingTheSinkAtTheCapSoDuplicateGuardBelongsToTheSink}
 * 가 그 분업을 계약으로 못 박아 두었다.
 *
 * <p>몹 원장 하나만 요구하는 정적 메서드라 두 권위의 서로 다른 코어(스프링
 * {@code MobSystem} · 정적판 {@code StandaloneWorldRuntime})가 각자 자기 자리에서 부른다.
 * 정적판 사본은 {@code StandaloneWorldRuntime#summonWarden} 이고 <b>같은 순서·같은 분기</b>다.
 *
 * <p>근거: [B] minecraft.wiki «Sculk Shrieker» — 4번째 활성화에서 워든 소환.
 * [B] MC Java 1.21.4 {@code WardenSpawnTracker#hasNearbyWarden}(48 블록 안에 워든이 있으면
 * 경고가 소환으로 가지 않는다) · {@code SculkShriekerBlockEntity#tryShriek}(소환한 플레이어에게
 * 분노를 건다) · {@code WardenAi} {@code Emerging}(솟는 동안 무적·부동).
 */
public final class WardenSummon {

    private WardenSummon() {}

    /**
     * 비명체 좌표에서 워든 하나. 이미 {@link Warden#NEARBY_WARDEN_RADIUS} 안에 워든이 있으면
     * 새로 세우지 않고 <b>그 개체의 분노만</b> 올린다.
     *
     * <p>워든은 자연 스폰이 없어 청크 재적재로 다시 나올 길이 없다 — 그래서 강제 영속을 건다.
     *
     * @param nickname 네 번째 비명을 실제로 깨운 플레이어. null 이면 어그로 초기화를 건너뛴다.
     * @return 새로 세운 워든. 근처 워든이 있어 분노만 올렸으면 null.
     */
    public static Mob summon(MobRuntime runtime, int x, int y, int z, String nickname) {
        double spawnX = x + 0.5;
        double spawnY = y + 1;
        double spawnZ = z + 0.5;
        Warden nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Mob mob : runtime.mobs()) {
            if (!(mob instanceof Warden warden) || mob.isDead() || mob.removed) continue;
            double dx = mob.x - spawnX;
            double dy = mob.y - spawnY;
            double dz = mob.z - spawnZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > Warden.NEARBY_WARDEN_RADIUS * Warden.NEARBY_WARDEN_RADIUS) continue;
            if (distance >= nearestDistance) continue;
            nearest = warden;
            nearestDistance = distance;
        }
        if (nearest != null) {
            nearest.increaseAngerAt(nickname, Warden.ANGER_VIBRATION_INCREMENT);
            return null;
        }
        Mob spawned = runtime.addMob(MobType.WARDEN, spawnX, spawnY, spawnZ, true);
        if (spawned instanceof Warden warden) {
            warden.beginEmerge();
            // 어그로 초기화: 소환한 플레이어를 곧바로 추격 임계 위로 올린다.
            warden.increaseAngerAt(nickname, Warden.ANGRY_THRESHOLD);
        }
        return spawned;
    }
}
