package com.gameexpert.engine.dragon;

/**
 * [DRAGON] 드래곤전이 내는 바닐라 {@code LevelEvent} id(핀 26.3 {@code LevelEvent} 상수). Java
 * {@code WsMessages.LevelEvent.EVENTS} · 정적판 {@code LEVEL_EVENT_IDS} 와 같은 값이다.
 */
public final class DragonEvents {
    /** {@code SOUND_DRAGON_FIREBALL}: 화염구 발사음(드래곤 위치). */
    public static final int SOUND_DRAGON_FIREBALL = 1017;
    /** {@code SOUND_DRAGON_DEATH}: {@code globalLevelEvent} — 거리와 무관하게 모든 플레이어가 듣는다. */
    public static final int SOUND_DRAGON_DEATH = 1028;
    /** {@code PARTICLES_DRAGON_FIREBALL_SPLASH}: 화염구 착탄(data 1 = 소리 포함). */
    public static final int PARTICLES_DRAGON_FIREBALL_SPLASH = 2006;
    /** {@code PARTICLES_DRAGON_BLOCK_BREAK}: 몸통이 블록을 부순 자리 하나의 폭발 입자. */
    public static final int PARTICLES_DRAGON_BLOCK_BREAK = 2008;
    /** {@code PARTICLES_DRAGON_EGG}: 알 순간이동 궤적(data 는 목적지 상대 좌표). */
    public static final int PARTICLES_DRAGON_EGG = 2015;
    /** {@code ANIMATION_DRAGON_SUMMON_ROAR}: 부활 연출 SUMMONING_DRAGON 의 포효. */
    public static final int ANIMATION_DRAGON_SUMMON_ROAR = 3001;

    private DragonEvents() {
    }
}
