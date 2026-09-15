package com.gameexpert.engine.mob;

/**
 * 파치드(바닐라 1.21.11 실존 종, stableId 91). 수치·근거·divergence 는
 * {@link ParchedRules} 가 소유하고 리서치 발췌는
 * {@code docs/research/mc-parched-1-21-11.md} 에 있다.
 *
 * <p><b>[PARCHED-FAMILY]</b> 이 클래스는 원래 {@code MeleeMob} 을 상속한 허스크 사본이었다.
 * 원문 재확인 결과 파치드는 <b>스켈레톤 변종</b>이라 {@link Stray}·{@link Bogged} 와 <b>글자
 * 그대로 같은 자리</b>로 옮긴다 — {@link Skeleton} 을 상속해 8~14블록 거리 유지 · strafe ·
 * 중력 보정 조준 · 근접 무기를 주웠을 때만 근접이라는 골격을 통째로 물려받고,
 * <b>화살이 싣는 상태이상</b>과 <b>사격 주기</b> 두 축만 재정의한다.
 *
 * <p>재정의하는 두 축:
 * <ol>
 *   <li>{@link #arrowEffect} — 나약함 30초(normal). 스트레이의 감속·보그드의 독과 같은 축이며
 *       난이도 분기도 같은 규약을 쓴다.</li>
 *   <li>{@link #shootIntervalTicks} — easy/normal 3.5초 · hard 2.5초. 스켈레톤의 2초보다
 *       느리다("보그드처럼 느린 연사"라는 원문 서술의 수치 근거).</li>
 * </ol>
 */
public final class Parched extends Skeleton {

    public Parched(long id, double x, double y, double z) {
        super(id, MobType.PARCHED, x, y, z);
    }

    @Override
    protected ProjectileEffect arrowEffect(MobWorldView world) {
        return MobEffectRules.parchedArrow(world.difficulty());
    }

    @Override
    protected int shootIntervalTicks(MobWorldView world) {
        return world.difficulty().parchedShootIntervalTicks();
    }
}
