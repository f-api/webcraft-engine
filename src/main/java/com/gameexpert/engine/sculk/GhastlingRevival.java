package com.gameexpert.engine.sculk;

import com.gameexpert.engine.mob.HappyGhast;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.engine.mob.MobType;

/**
 * [DEEP-DARK] 다 수화된 말린 가스트가 실제 가스틀링을 세우는 <b>한 지점</b>.
 *
 * <p>{@link WardenSummon} 과 같은 분업이다: 사슬({@link DriedGhastHydrationSystem})은 "네 구간을
 * 다 채웠다" 까지만 책임지고, <b>어느 좌표에 · 어떤 나이로 · 영속을 걸고</b> 세우는지는 전부
 * 여기에 있다. 몹 원장 하나만 요구하는 정적 메서드라 두 권위의 서로 다른 코어(스프링
 * {@code MobSystem} · 정적판 {@code StandaloneWorldRuntime})가 각자 자기 자리에서 부른다.
 *
 * <p><b>가스틀링은 별도 종이 아니다.</b> [B] minecraft.wiki «Ghastling» — 가스틀링과 해피
 * 가스트는 <b>같은 엔티티 id {@code happy_ghast}</b> 이고 가스틀링은 그 새끼 형태다(베드락
 * 숫자 id 도 하나뿐이다). 그래서 새 {@code MobType} 을 열지 않는다 — 새 종을 열면 프로토콜
 * stableId·스폰 파리티 표·직렬화가 전부 늘어나는데 바닐라에는 그 종이 없다. 피글린 새끼가
 * 이미 쓰고 있는 {@code babyForm}/{@code ageTicksRemaining} 인프라를 그대로 쓴다.
 *
 * <p><b>영속을 강제한다.</b> 해피 가스트는 이 저장소에 자연 스폰이 없다(획득 경로는 딥다크
 * 도시 상자의 말린 가스트뿐이다). 청크가 내려갔다 올라오는 것으로 사라지면 20분을 들인 수화가
 * 통째로 없어지므로 워든과 같은 이유로 {@code persistenceRequired} 를 건다.
 */
public final class GhastlingRevival {

    private GhastlingRevival() {}

    /**
     * 말린 가스트가 있던 칸에서 가스틀링 하나. 블록 제거는 호출자
     * ({@link DriedGhastHydrationSystem})가 이미 했다 — 이 메서드는 몹만 세운다.
     *
     * <p>좌표는 칸의 <b>중심</b>이다. 성체 해피 가스트의 AABB 는 4×4 지만 가스틀링은
     * 0.95×0.95 라([B] «Ghastling» 히트박스) 한 칸 자리에서 그대로 태어난다 — 성체 크기로
     * 세웠다면 벽에 끼는 좌표를 따로 찾아야 했을 것이다.
     *
     * @return 세운 가스틀링. 종 생성이 막힌 경우에만 null.
     */
    public static Mob revive(MobRuntime runtime, int x, int y, int z) {
        Mob spawned = runtime.addMob(MobType.HAPPY_GHAST, x + 0.5, y, z + 0.5, true);
        if (spawned == null) return null;
        // 피글린 새끼와 같은 두 줄이다: 영속 필드 babyForm 이 "이 개체는 새끼로 태어났다" 를
        // 들고, 성장 타이머가 성체 전환 경계를 정확히 한 번 지나게 한다.
        spawned.restoreBabyForm(true);
        spawned.setAgeTicksRemaining(HappyGhast.GROWTH_AUTHORITY_TICKS);
        return spawned;
    }
}
