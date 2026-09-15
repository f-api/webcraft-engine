package com.gameexpert.engine.mob;

/**
 * [WAVE-86-97] <b>체류 시간이 정해진</b> 개체. 바닐라에서 거리와 무관하게 체류 시간이 끝나면
 * 사라지는 몹({@code WanderingTrader#despawnDelay} · {@code TraderLlama#despawnDelay})의
 * 계약이며, 정적판 사본은 {@code StandaloneMobRules.timedDespawnMcTicks} 다.
 *
 * <p>바닐라 {@code WanderingTrader#tick} 은 로드된 동안 매 MC 틱 {@code despawnDelay} 를 1씩
 * 줄이고 0 이 되면 {@code discard()} 한다. WebCraft 서버 틱은 MC 2틱이므로 소멸 패스가
 * 한 번 돌 때마다 2를 뺀다(자연 소멸 패스의 {@code farDespawnTicks} 가 한 번에 2 논리 틱을
 * 도는 것과 같은 환산이다).
 *
 * <p>이 계약은 <b>거리 기반 소멸 면제</b>({@code MobSpawner#exemptFromNaturalDespawn})보다
 * 앞선다 — 행상인·트레이더 라마는 CREATURE 라 거리 소멸에서는 영원히 면제이고, 그래서
 * 만료가 없으면 CREATURE cap 을 영구히 잠식한다. 그 잠식이 이 인터페이스가 있는 이유다.
 */
public interface TimedDespawn {

    /** 소멸 패스 한 번이 도는 MC 틱 수. 서버 10 TPS · MC 20 TPS 라 2 다. */
    int MC_TICKS_PER_DESPAWN_PASS = 2;

    /** 남은 체류 시간(MC 틱). 0 이하면 이번 패스에서 소멸한다. */
    int remainingDespawnMcTicks();

    /**
     * MC 틱만큼 체류 시간을 깎고 만료되었는지 답한다. 이름표/길들임 등으로 영속이 걸린
     * 개체는 바닐라와 같이({@code Mob#isPersistenceRequired}) 시간이 흐르지 않는다.
     */
    boolean expireDespawnDelay(int mcTicks);
}
