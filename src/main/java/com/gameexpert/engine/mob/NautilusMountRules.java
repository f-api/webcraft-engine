package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * [NAUTILUS-MOUNT] 노틸러스 안장·갑옷·수중 탑승의 <b>상태 없는</b> 규칙. 개체 상태는
 * {@code Nautilus} 가 들고 여기에는 수치와 판정만 둔다({@link HappyGhastRules}·
 * {@link CamelRules} 와 같은 분업).
 *
 * <p>근거 등급 <b>[B]</b>: {@code docs/research/mc-nautilus-1-21-11.md} §8-2 —
 * "길들인 성체는 안장 슬롯과 노틸러스 갑옷 슬롯 둘을 갖고, 태우면 산소 막대가 멈추는
 * <b>Breath of the Nautilus</b> 상태이상을 준다."
 *
 * <h2>이 계약이 여는 축 셋</h2>
 * <ol>
 *   <li><b>수중 탈것.</b> 좌석 표({@link MobMountRules#seatCount})의 기존 종은 지상(돼지·말
 *       계열·낙타)과 공중(해피 가스트)뿐이었다. 노틸러스가 <b>물</b> 축의 첫 종이고, 그
 *       구분은 {@link MobMountRules#aquaticMount(MobType)} 한 술어가 소유한다 — 해피 가스트가
 *       {@code seatLateralOffset} 로 <b>두 번째 좌석 축</b>을 연 것과 같은 꼴이다.</li>
 *   <li><b>안장 게이트.</b> 조종은 안장을 요구한다({@link MobMountRules#steerable}) — 말·낙타·
 *       돼지와 <b>글자 그대로 같은</b> 게이트라 새 판정 축이 아니다. 탑승 자체는 길들임을
 *       요구한다([B]: "tamed adult" — 해피 가스트가 하네스 없이는 못 태우는 것과 같은 자리).</li>
 *   <li><b>Breath of the Nautilus.</b> 물약 수중 호흡과 구별되는 전용 상태이상이다. 첫 플레이어
 *       승객에게 60 MC 틱을 부여하고 절대 게임 시간 40 MC 틱 경계에서 갱신하며, 익사를 막고
 *       MC 틱마다 공기를 4 회복한다. 하차해도 즉시 제거하지 않고 남은 시간이 자연히 흐른다.</li>
 * </ol>
 *
 * <p>등록된 구리·철·금·다이아몬드·네더라이트 다섯 티어는 각 재질의 방어 속성을 적용한다.
 * 가위는 승객이 없을 때 정확한 장착 아이템을 반환하며, 갑옷 자체에는 내구도가 없다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneNautilusMountRules.ts} 이고,
 * 두 사본의 상수·판정 동일성은 {@code StandaloneNautilusMountRules.test.ts} 가 이 파일 원문을
 * 읽어 강제한다.
 */
public final class NautilusMountRules {

    private NautilusMountRules() {
    }

    /** 노틸러스가 여는 좌석 수. [B] 는 다인승을 적지 않아 1인승이다(말·돼지와 같다). */
    public static final int SEAT_COUNT = 1;

    /** Breath of the Nautilus duration in Minecraft ticks. */
    public static final int BREATH_MC_TICKS = 60;

    /** Active riders refresh the 60-tick effect every two seconds. */
    public static final int BREATH_REFRESH_INTERVAL_MC_TICKS = 40;

    /** Breath restores four air points on every Minecraft tick. */
    public static final int BREATH_AIR_REFILL_PER_MC_TICK = 4;

    /** Refresh boundary on vanilla absolute game time, independent of daylight and runtime uptime. */
    public static boolean breathRefreshDue(long gameTimeMcTicks) {
        return Math.floorMod(gameTimeMcTicks, BREATH_REFRESH_INTERVAL_MC_TICKS) == 0;
    }


    /** Breath of the Nautilus 의 증폭(레벨-1). 수중 호흡은 레벨이 판정을 바꾸지 않아 0 이다. */
    public static final int BREATH_AMPLIFIER = 0;

    /**
     * 탑승 중인 노틸러스의 이동 속도(블록/초). 종 속성({@code MobType.NAUTILUS.baseSpeed()})을
     * 말·낙타·돼지·해피 가스트와 <b>같은 환산 계수</b>
     * ({@link FarmAnimalRules#MOVEMENT_SPEED_BLOCKS_PER_SECOND})로 옮긴 값이다.
     *
     * <p>[C] — [B] 가 적는 헤엄 속도 6.5 m/s 는 <b>자유 유영</b> 값이고 탑승 배율은 확인하지
     * 못했으므로 배율을 지어내지 않고 속성 그대로 쓴다(해피 가스트와 같은 판단). 이 속도가
     * {@link MobMountRules#RIDER_POS_RANGE} 16 블록 leash 가 견뎌야 할 상한이며 말 14.23
     * 블록/초보다 느려 여유가 그대로 남는다.
     */
    public static double riddenSpeedBlocksPerSecond() {
        return MobType.NAUTILUS.baseSpeed() * FarmAnimalRules.MOVEMENT_SPEED_BLOCKS_PER_SECOND;
    }

    /**
     * 탑승 방송에 실어 보내는 점프 강도. 노틸러스는 <b>수중</b> 탈것이라 도약도 대시도 없다 —
     * 상승/하강은 조종석 클라의 유영 입력이지 임펄스가 아니다(해피 가스트와 같은 이유로 0).
     */
    public static final double JUMP_STRENGTH = 0.0;

    /**
     * 이 아이템이 노틸러스 갑옷인가. ID 정본은 {@link Blocks#isNautilusArmorTier} 이고 이
     * 위임이 몹 쪽 호출부의 단일 통로다(하네스가 {@code HappyGhastRules.isHarnessItem} 로
     * 위임하는 것과 같은 자리).
     */
    public static boolean isArmorItem(int itemId) {
        return Blocks.isNautilusArmorTier(itemId);
    }

    /** 갑옷 아이템 → 티어 서열(0=구리 … 4=네더라이트). 갑옷이 아니면 -1. */
    public static int armorTier(int itemId) {
        return Blocks.nautilusArmorTier(itemId);
    }

    /** 티어 서열 → 갑옷 아이템 ID. 서열 밖이면 0(빈 아이템). */
    public static int armorItemForTier(int tier) {
        return Blocks.nautilusArmorItemForTier(tier);
    }

    /** 저장·복원에서 받아들일 수 있는 갑옷 티어인가. -1(없음)도 유효한 값이다. */
    public static boolean validArmorTier(int tier) {
        return tier == NO_ARMOR || armorItemForTier(tier) != 0;
    }

    /** 갑옷을 입지 않은 상태의 티어 값. 영속 컬럼도 이 값을 쓴다. */
    public static final int NO_ARMOR = -1;

    /** Registered tier defense points: copper 4, iron 5, gold 7, diamond 11, netherite 19. */
    public static int armorPoints(int tier) {
        return switch (tier) {
            case 0 -> 4;
            case 1 -> 5;
            case 2 -> 7;
            case 3 -> 11;
            case 4 -> 19;
            default -> 0;
        };
    }

    /** Vanilla armor toughness: diamond 2, netherite 3, all earlier materials 0. */
    public static int armorToughness(int tier) {
        return tier == 3 ? 2 : tier == 4 ? 3 : 0;
    }

    /** Netherite body armor contributes the vanilla 0.1 knockback-resistance attribute. */
    public static double armorKnockbackResistance(int tier) {
        return tier == 4 ? 0.1 : 0.0;
    }
}
