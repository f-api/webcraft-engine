package com.gameexpert.engine.mob.villager;

/**
 * 주민 한 명의 직업을 돌려주는 조회기다.
 *
 * <p><b>가정한 인터페이스</b>: 직업 배정의 정본은 ① villager-poi 트랙(작업장 POI claim)이고,
 * 이 거래 트랙은 그 결과만 읽는다. ① 트랙이 자기 API 를 확정하면 통합자가 그 구현을 여기에
 * 배선하면 되고, 거래 쪽 코드는 바뀌지 않는다. 배선 전까지는 항상
 * {@link VillagerTradeRules.Profession#NONE} 을 돌려주는 {@link #NONE} 을 쓴다.
 *
 * <p>{@code professionOf} 는 순수 조회여야 한다. 직업 배정/해제는 ① 트랙이 소유하며,
 * 거래 쪽은 직업이 바뀐 사실을 관측한 시점에
 * {@link VillagerTradeState#assignProfession} 으로 오퍼를 다시 만든다.
 */
@FunctionalInterface
public interface VillagerProfessionSource {

    /** 배선 전 기본값. 어떤 주민도 거래하지 않는다. */
    VillagerProfessionSource NONE = villagerId -> VillagerTradeRules.Profession.NONE;

    VillagerTradeRules.Profession professionOf(long villagerId);

    /**
     * 정본 배선: 직업 배정의 소유자인 POI 점유 원장을 거래 트랙이 읽는 조회기로 감싼다.
     * 순수 조회이며 원장을 바꾸지 않는다.
     */
    static VillagerProfessionSource ofJobClaims(VillagerJobClaimLedger ledger) {
        if (ledger == null) return NONE;
        return villagerId -> tradeProfession(ledger.professionOf(villagerId));
    }

    /** POI lane 직업 → 거래 lane 직업. 두 열거형은 이름이 같은 값만 대응한다. */
    static VillagerTradeRules.Profession tradeProfession(
            VillagerJobSitePolicy.Profession profession) {
        return switch (profession) {
            case NONE -> VillagerTradeRules.Profession.NONE;
            case NITWIT -> VillagerTradeRules.Profession.NITWIT;
            case ARMORER -> VillagerTradeRules.Profession.ARMORER;
            case BUTCHER -> VillagerTradeRules.Profession.BUTCHER;
            case LIBRARIAN -> VillagerTradeRules.Profession.LIBRARIAN;
            case TOOLSMITH -> VillagerTradeRules.Profession.TOOLSMITH;
            case CARTOGRAPHER -> VillagerTradeRules.Profession.CARTOGRAPHER;
            case CLERIC -> VillagerTradeRules.Profession.CLERIC;
            case FARMER -> VillagerTradeRules.Profession.FARMER;
            case FISHERMAN -> VillagerTradeRules.Profession.FISHERMAN;
            case FLETCHER -> VillagerTradeRules.Profession.FLETCHER;
            case LEATHERWORKER -> VillagerTradeRules.Profession.LEATHERWORKER;
            case MASON -> VillagerTradeRules.Profession.MASON;
            case SHEPHERD -> VillagerTradeRules.Profession.SHEPHERD;
            case WEAPONSMITH -> VillagerTradeRules.Profession.WEAPONSMITH;
        };
    }
}
