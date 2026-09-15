package com.gameexpert.engine.creaking;

import com.gameexpert.engine.mob.Creaking;
import com.gameexpert.engine.mob.CreakingRules;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.engine.mob.MobType;

/**
 * 크리킹 하트가 실제 크리킹을 세우고 거두는 <b>한 지점</b>.
 *
 * <p>{@link com.gameexpert.engine.sculk.WardenSummon} 과 같은 분업이다: 사슬
 * ({@link CreakingHeartSystem})은 "깨어난 하트마다 매 틱 싱크를 부른다" 까지만 책임지고
 * <b>중복 방지·결속 좌표·처치</b>는 전부 여기에 있다. 몹 원장 하나만 요구하는 정적 메서드라
 * 두 권위의 서로 다른 코어(스프링 {@code MobSystem} · 정적판
 * {@code StandaloneWorldRuntime})가 각자 자기 자리에서 부른다.
 *
 * <p>근거: [B] minecraft.wiki «Creaking Heart» — 하트당 크리킹 <b>하나</b>, 밤에 소환,
 * 하트를 부수면 그 크리킹이 즉시 죽고 처치가 부순 플레이어에게 귀속된다.
 */
public final class CreakingSummon {

    private CreakingSummon() {
    }

    /**
     * 이 하트가 거느리는 크리킹, 또는 아직 없으면 {@code null}. 하트 칸 좌표가 곧 결속
     * 정체성이므로 별도 ID 표를 저장하지 않는다 — 몹이 자기 하트 좌표를 들고 있다.
     */
    public static Creaking boundCreaking(MobRuntime runtime, int x, int y, int z) {
        for (Mob mob : runtime.mobs()) {
            if (!(mob instanceof Creaking creaking) || mob.isDead() || mob.removed) continue;
            if (creaking.heartX() == x && creaking.heartY() == y && creaking.heartZ() == z) {
                return creaking;
            }
        }
        return null;
    }

    /**
     * 깨어난 하트 하나가 크리킹 하나를 갖게 한다. 이미 있으면 <b>아무 일도 하지 않는다</b>
     * ([B] "One creaking per heart") — 중복 방지가 사슬이 아니라 이 싱크의 책임인 것은
     * 워든 소환이 낸 계약 그대로다.
     *
     * <p>배치는 하트 칸 <b>바로 위</b>다. 바닐라는 33×17×33 상자 안에서 유효한 칸을 고르지만
     * 그 탐색은 난수를 소비하므로({@link CreakingRules#SPAWN_BOX_HORIZONTAL_RADIUS} 은 그
     * 상자의 치수를 [B] 로 남겨 둔 것이다) 이 저장소는 <b>결정적 한 칸</b>을 쓴다
     * ([C] divergence — 새 난수 소비가 생기면 기존 종의 확률·난수 프리픽스 감사가 깨진다.
     * 말린 가스트 수화 사슬이 랜덤 틱을 되끌어오지 않은 것과 같은 판단이다).
     *
     * <p>크리킹은 자연 스폰이 없어 청크 재적재로 다시 나올 길이 없다 — 그런데 <b>영속을
     * 걸지 않는다</b>. 밤이 끝나면 사라지는 것이 이 종의 계약이고, 하트가 다시 깨어나면 이
     * 함수가 다시 세우기 때문이다.
     *
     * @return 새로 세운 크리킹. 이미 있었으면 null.
     */
    public static Creaking summon(MobRuntime runtime, int x, int y, int z) {
        if (boundCreaking(runtime, x, y, z) != null) return null;
        Mob spawned = runtime.addMob(
                MobType.CREAKING, x + 0.5, y + 1, z + 0.5, false);
        if (!(spawned instanceof Creaking creaking)) return null;
        creaking.bindHeart(x, y, z);
        return creaking;
    }

    /**
     * 이 하트가 거느린 크리킹을 <b>즉시</b> 죽인다. 하트가 부서졌거나 정렬이 깨졌거나 낮이
     * 됐을 때 사슬이 부른다.
     *
     * @return 실제로 죽인 개체가 있었으면 true
     */
    public static boolean dismiss(MobRuntime runtime, int x, int y, int z) {
        Creaking creaking = boundCreaking(runtime, x, y, z);
        if (creaking == null) return false;
        creaking.killedByHeartLoss();
        return true;
    }
}
