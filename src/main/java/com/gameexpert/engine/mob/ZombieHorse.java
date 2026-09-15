package com.gameexpert.engine.mob;

/**
 * 좀비 말. pinned 26.3-snapshot-7의 {@code ZombieHorse extends AbstractHorse} 자리와
 * 자연 MONSTER 스폰·능력치 롤·좀비 기수 finalize 계약을 따른다. 수치·근거는
 * {@link ZombieHorseRules} 가 소유한다.
 *
 * <p>말 계열 상태 기계({@code AbstractHorseMob})를 <b>그대로</b> 재사용한다 — 좌석·안장·영속·
 * 좌표 업링크가 말과 한 글자도 다르지 않다. WebCraft가 유지하는 생애주기
 * divergence는 다음 두 가지다:
 * <ol>
 *   <li>태어날 때부터 길들여져 있다 — temper·낙마 판정이 아예 서지 않는다.</li>
 *   <li>먹지 않는다 — 언데드라 먹이 표를 통과시키지 않는다.</li>
 * </ol>
 * 안장 게이트는 말과 같다: 안장이 없으면 {@link MobMountRules#steerable} 이 거짓이라 탈 수는
 * 있어도 조종되지 않는다.
 */
final class ZombieHorse extends AbstractHorseMob {

    ZombieHorse(long id, double x, double y, double z) {
        super(MobType.ZOMBIE_HORSE, id, x, y, z, null);
        // 상위 생성자가 26.3의 점프 3회 → 속도 3회 롤을 적용했다.
        // 남은 것은 "언데드 말은 길들이는 과정이 없다"는 자체 계약뿐이다.
        tameFromBirth();
    }

    /** 언데드 말은 길들이는 과정이 없다 — temper·낙마 판정이 서지 않는다. */
    @Override boolean bornTamed() {
        return true;
    }

    /** 언데드라 먹이 표를 통과시키지 않는다. 아이템은 소비되지 않는다. */
    @Override boolean feed(short itemType) {
        return false;
    }

    /** 상자를 달지 않는다(바닐라 {@code AbstractChestedHorse} 하위형이 아니다). */
    @Override boolean chestable() {
        return false;
    }

    /** 카펫 장식은 라마 전용이다. */
    @Override boolean carpetable() {
        return false;
    }
}
