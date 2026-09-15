package com.gameexpert.engine.mob;

/**
 * [ROTTEN-LEATHER] 썩은 가죽 **풀세트**를 입은 플레이어에 대한 언데드 무적대 판정의 정본.
 *
 * <p>MC-REFERENCE: 바닐라에는 대응물이 없는 WebCraft 자체 계약이다. 구조는 바닐라 피글린의
 * 금 방어구 무적대({@code PiglinAi.isWearingGold})를 그대로 따른다 — "장비로 성립하는 중립"이고
 * "선공하면 그 개체만 적대로 돌아선다". 피글린과 다른 점은 두 가지뿐이다.
 * <ul>
 *   <li>한 부위가 아니라 <b>4부위 전부</b>를 입어야 한다(세트 보너스).</li>
 *   <li>대상이 피글린 계열이 아니라 <b>언데드 계열 전체</b>다({@link #isUndead(MobType)}).</li>
 * </ul>
 *
 * <p><b>원한(grudge)은 개체별</b>이다. 플레이어가 먼저 때린 그 몹만 적대를 유지하고, 옆에 있던
 * 같은 종은 계속 중립이다. 다만 좀비 피그맨처럼 <b>무리 연쇄 계약이 이미 있는 종</b>은 그 계약이
 * 원한을 무리 전체에 퍼뜨린다 — 연쇄로 분노한 개체도 각자 원한을 받으므로 함께 적대가 된다.
 *
 * <p><b>영속</b>: 원한은 저장하지 않는 런타임 상태다(피글린 분노와 달리 지속 시간이 없는 대신
 * 세션 범위다). 재접속하면 원한이 사라져 세트를 입은 플레이어는 다시 중립으로 시작한다.
 * 세트를 한 부위라도 벗으면 원한과 무관하게 전원이 즉시 적대로 돌아온다 — 중립의 근거가
 * 기억이 아니라 <b>지금 입고 있는 장비</b>이기 때문이다.
 */
public final class UndeadNeutralityRules {

    private UndeadNeutralityRules() {}

    /**
     * 언데드 계열인가. 피그맨(PIGMAN)은 죽지 않은 돼지 인간형이라 언데드가 아니다.
     * 좀비 동물 신종은 여기에만 줄을 더하면 된다.
     *
     * <p>[WAVE-86-97] 팬텀은 예전에 "이 게임에 없다"는 이유로 빠져 있었다. 종이 등록됐으므로
     * 이제 바닐라 그대로 언데드다 — 스켈레톤 말·파치드도 같다. 신종 중 워든·브리즈·유황
     * 큐브는 바닐라에서도 언데드가 <b>아니다</b>(스컬크 생물·바람 원소·광물 큐브다).
     */
    public static boolean isUndead(MobType kind) {
        return switch (kind) {
            // 좀비 계열.
            case ZOMBIE, BABY_ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER,
                 ZOMBIE_PIGMAN, ZOMBIFIED_PIGLIN -> true;
            // 스켈레톤 계열.
            case SKELETON, STRAY, BOGGED -> true;
            // WebCraft 창작 좀비 동물.
            case ZOMBIE_BEAR, ZOMBIE_NAUTILUS -> true;
            // 좀비 말(중립이라 원래 선공하지 않는다 — 계약 명시)·좀비 늑대(선공이 억제된다).
            case ZOMBIE_HORSE, ZOMBIE_WOLF -> true;
            // 좀비 동물 10종. 좀비화 6종과 창작 2종(사슴·멧돼지)은 적대 종이라 여기서 실제로
            // 선공이 갈린다. 나머지 둘은 원래 선공하지 않아 세트가 눈에 보이는 차이를 만들지
            // 않는다 — 시체 까마귀(공격 자체가 없는 신호 종)와 낙타 husk(기수가 없으면
            // 바닐라에서도 passive)다. 그래도 언데드라는 사실은 계약으로 남긴다.
            case ZOMBIE_COW, ZOMBIE_PIG, ZOMBIE_SHEEP, ZOMBIE_GOAT, ZOMBIE_FOX,
                 ZOMBIE_CHICKEN -> true;
            case CARRION_STAG, CARRION_BOAR, CARRION_CROW, CAMEL_HUSK -> true;
            // [WAVE-86-97] 바닐라 언데드 신종 셋. 팬텀·스켈레톤 말은 바닐라 원문 그대로이고
            // 파치드는 사막 언데드 인간형이라 허스크 계열과 같은 자리다.
            case PHANTOM, SKELETON_HORSE, PARCHED -> true;
            default -> false;
        };
    }

    /**
     * 이 몹이 지금 이 플레이어에게 중립인가(= 선공하지 않는가).
     *
     * @param kind            몹 종
     * @param grudgeNickname  이 개체가 기억하는 선공자. 없으면 {@code null}
     * @param wearingSet      플레이어가 썩은 가죽 4부위를 전부 착용 중인가
     * @param nickname        판정 대상 플레이어의 닉네임
     */
    public static boolean undeadNeutralTo(MobType kind, String grudgeNickname,
                                          boolean wearingSet, String nickname) {
        return wearingSet && isUndead(kind) && !java.util.Objects.equals(grudgeNickname, nickname);
    }

    /** 같은 판정을 권위 스냅샷으로 받는 형태. 타게팅 경로는 전부 이쪽을 지난다. */
    public static boolean undeadNeutralTo(MobType kind, String grudgeNickname,
                                          PlayerSnapshot player) {
        return player != null && undeadNeutralTo(kind, grudgeNickname,
                player.wearingRottenLeatherSet(), player.nickname());
    }
}
