package com.gameexpert.engine.mob;

/**
 * [ROTTEN-LEATHER] 썩은 가죽을 떨구는 종의 <b>정본 술어</b>와 그 <b>체급 티어</b>.
 *
 * <p>썩은 가죽은 좀비화된 동물의 남은 가죽이다 — 살점만 남는 인간형 언데드(좀비·주민 좀비·
 * 익사체 …)가 아니라 <b>가죽을 가진 동물이 좀비가 된 종</b>만 떨군다. 새 좀비 동물을 추가하는
 * 트랙은 드랍표를 건드리지 말고 {@link #rottenLeatherTier(MobType)} 에 종만 더하면 두 권위의
 * 드랍이 함께 따라온다(정적판 사본은 client {@code StandaloneMobRules.rottenLeatherTier}).
 *
 * <p>MC-REFERENCE: 바닐라에 대응물이 없는 WebCraft 자체 계약이다. 드랍 pool 은 종별 표
 * <b>뒤에</b> 한 번 붙는다(호출부 {@code MobSystem.mobDrops}). 그래서 이 목록이 늘어도 기존 종의
 * 난수 소비 수열은 앞부분이 그대로 보존된다.
 *
 * <h2>체급 티어(100시간 경제)</h2>
 * 한 마리가 내놓는 가죽은 <b>체급</b>이 정한다. 티어는 셋뿐이고 모두 굴림 하나만 쓴다:
 * <ul>
 *   <li>{@link Tier#LARGE} — {@code nextInt(3)} → 0~2 (기대 1.0). 좀비곰·좀비 말·좀비 늑대·
 *       좀비 앵무조개(기존 종, <b>값 불변</b>)와 좀비 소·낙타 husk·무덤 사슴·부패 멧돼지.</li>
 *   <li>{@link Tier#MEDIUM} — {@code nextInt(2)} → 0~1 (기대 0.5). 좀비 돼지·양·염소·여우.</li>
 *   <li>{@link Tier#BIRD} — {@code nextInt(4) == 0} → 1 (기대 0.25). 좀비 닭·시체 까마귀.</li>
 * </ul>
 * <b>기존 4종은 전부 LARGE 라 굴림 형태({@code nextInt(3)})와 기대값이 트랙 이전과
 * 글자 그대로 같다</b> — golden·decision trace 가 흔들리지 않는 이유다.
 *
 * <p>티어별 기대값이 <b>시간당 총 기대 가죽</b>을 어떻게 만드는지(풀세트 24장 ≈ 100시간)는
 * {@code docs/MC-REFERENCE.md} 「썩은 가죽 100시간 경제」 절이 유도 과정을 소유하고,
 * 시드 고정 시뮬레이션 {@code RottenLeatherEconomy.test.ts} 가 그 구간을 단언한다.
 */
public final class RottenLeatherDropRules {

    private RottenLeatherDropRules() {}

    /** 체급 티어. {@code NONE} 은 "이 종은 썩은 가죽을 떨구지 않는다"는 뜻이다. */
    public enum Tier {
        NONE,
        LARGE,
        MEDIUM,
        BIRD
    }

    /** 대형 개수 굴림의 배타 상한. {@code rng.nextInt(3)} 이라 무약탈 0~2 다. */
    public static final int ROTTEN_LEATHER_ROLL = 3;
    /** 중형 개수 굴림의 배타 상한. {@code rng.nextInt(2)} 이라 무약탈 0~1 다. */
    public static final int ROTTEN_LEATHER_MEDIUM_ROLL = 2;
    /**
     * 조류 확률 굴림의 배타 상한. {@code rng.nextInt(4) == 0} 이면 1 장이다 — 개수 굴림이
     * 아니라 확률 굴림인 이유는 "새 한 마리에서 가죽 두 장"이 성립하지 않기 때문이다.
     */
    public static final int ROTTEN_LEATHER_BIRD_ROLL = 4;

    /**
     * 이 종의 썩은 가죽 체급. 좀비 동물 신종은 여기에만 줄을 더하면 된다.
     */
    public static Tier rottenLeatherTier(MobType kind) {
        return switch (kind) {
            // ── 기존 종(이 트랙 이전부터 LARGE. 값·형태 불변) ──
            // 좀비곰·좀비 앵무조개(WebCraft 창작 좀비 동물).
            case ZOMBIE_BEAR, ZOMBIE_NAUTILUS -> Tier.LARGE;
            // 좀비 말·좀비 늑대. 둘 다 "가죽을 가진 동물이 좀비가 된 종"이라 같은 줄에 든다.
            case ZOMBIE_HORSE, ZOMBIE_WOLF -> Tier.LARGE;
            // ── 좀비 동물 10종 트랙 ──
            // 대형: 소·낙타 husk 는 체구가 크고, 사슴·멧돼지는 창작 희귀 대형이다.
            case ZOMBIE_COW, CAMEL_HUSK, CARRION_STAG, CARRION_BOAR -> Tier.LARGE;
            // 중형: 돼지·양·염소·여우.
            case ZOMBIE_PIG, ZOMBIE_SHEEP, ZOMBIE_GOAT, ZOMBIE_FOX -> Tier.MEDIUM;
            // 조류: 닭·까마귀.
            case ZOMBIE_CHICKEN, CARRION_CROW -> Tier.BIRD;
            default -> Tier.NONE;
        };
    }

    /**
     * 이 종이 썩은 가죽을 떨구는가. 기존 호출부·테스트가 쓰는 술어 형태를 유지한다.
     */
    public static boolean rottenLeatherDrop(MobType kind) {
        return rottenLeatherTier(kind) != Tier.NONE;
    }

    /**
     * 티어 하나를 굴린다. 굴림 <b>횟수</b>는 티어와 무관하게 항상 1 이라, 종이 어느 티어든
     * 이 pool 이 소비하는 난수 개수가 같다(약탈 보너스는 호출부가 따로 싣는다).
     *
     * @return 약탈 전 썩은 가죽 개수
     */
    public static int rollRottenLeather(MobType kind, MobRandom rng) {
        return switch (rottenLeatherTier(kind)) {
            case LARGE -> rng.nextInt(ROTTEN_LEATHER_ROLL);
            case MEDIUM -> rng.nextInt(ROTTEN_LEATHER_MEDIUM_ROLL);
            case BIRD -> rng.nextInt(ROTTEN_LEATHER_BIRD_ROLL) == 0 ? 1 : 0;
            case NONE -> 0;
        };
    }
}
