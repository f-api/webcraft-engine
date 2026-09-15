package com.gameexpert.engine.mob;

/**
 * 낙타 husk(바닐라 1.21.11 실존 종, stableId 85). 수치·근거·divergence 는
 * {@link CamelHuskRules} 가 소유하고 리서치 발췌는
 * {@code docs/research/mc-camel-husk-1-21-11.md} 에 있다.
 *
 * <p>기수가 없는 이 트랙에서는 <b>선공하지 않는</b>다 — 바닐라에서도 적대성은 기수에게서
 * 오기 때문이다. 그래서 좀비 말과 같은 자리에서 {@link AnimalMob} 의 배회·도주 골격을
 * 그대로 쓰고, 언데드라는 사실은 드랍({@code RottenLeatherDropRules})과 무선공 판정
 * ({@link UndeadNeutralityRules#isUndead})으로만 표현한다.
 *
 * <p><b>먹지 않는다</b>(언데드라 선인장 번식 표를 통과시키지 않는다) —
 * {@code MobType#breedingEnabled} 명단 밖이라 번식 경로 자체가 서지 않는다.
 * 탑승·대시는 이 트랙 밖이다(모델 트랙과 함께 다음 단계).
 */
public final class CamelHusk extends AnimalMob {

    public CamelHusk(long id, double x, double y, double z) {
        super(MobType.CAMEL_HUSK, id, x, y, z);
    }
}
