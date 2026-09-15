package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.terrain.Blocks;

/**
 * [SURV-X] 바닐라 경험치(XP)의 순수 상수·판정 모음.
 *
 * <p><b>단일 상태 규약</b>: 플레이어는 정수 {@code xpTotal}(마지막 사망 이후 누적 총량) <b>하나만</b> 가진다.
 * 레벨과 경험치 바 진행도는 언제나 이 값에서 파생하므로 두 권위(Java·정적판)가 따로 누적하다 어긋날 수 없다.
 *
 * <p><b>고정소수 규약</b>: SURV-H {@link HungerRules}가 세운 1/1000 단위 정수(milli) 규약을 그대로 쓴다.
 * 바닐라 제련 XP는 float(0.7, 0.15 …)이지만 화로에 누적하는 값이라 부동소수 누적을 피해야 한다.
 * 그래서 {@link #smeltXpMilli(short)}는 ×1000 정수만 돌려주고, 산출물을 꺼낼 때 정수부만 지급한다.
 * 이 파일 전체에 {@code Math.sqrt}나 부동소수 누적은 없다.
 *
 * <p><b>레벨 역산</b>: {@link #levelFromTotal(int)}은 닫힌 식(sqrt) 대신 정수 루프다. ECMAScript의
 * {@code Math.sqrt}는 구현 근사가 허용되어 정적판과 값이 갈릴 수 있는 파리티 위험 요소이기 때문이다.
 *
 * <p><b>난수 규약</b>: 이 클래스는 RNG를 들고 있지 않다. 범위 난수가 필요한 규칙은 호출자가 뽑은
 * 음이 아닌 정수 {@code roll}을 받아 내부에서 나머지 연산으로 구간에 사상한다. 나머지 편향은
 * 구간 폭이 작아(최대 29) 무시할 수 있는 수준이며 의도적이다.
 */
public final class XpRules {

    private XpRules() {
    }

    /** 제련 XP 고정소수 배율(1.0 = 1000). SURV-H {@link HungerRules#MILLI}와 같은 규약이다. */
    public static final int MILLI = 1000;

    /**
     * 레벨 스캔 루프의 상한. 이 레벨까지의 누적 XP 총량(2,147,407,943)이 부호 있는 32비트에 들어가는
     * 마지막 값이라, 여기서 멈추면 {@code int} 오버플로와 무한 루프를 동시에 막는다.
     */
    public static final int MAX_LEVEL_SCAN = 21863;

    // ── 경험치 바 구간(바닐라 Player.getXpNeededForNextLevel) ──
    /** 바 공식이 갈리는 첫 경계 레벨. */
    public static final int XP_BAR_TIER1_LEVEL = 15;
    /** 바 공식이 갈리는 둘째 경계 레벨. */
    public static final int XP_BAR_TIER2_LEVEL = 30;
    /** 0~14 구간: {@code 7 + 2*level}. */
    public static final int XP_BAR_TIER0_BASE = 7;
    /** 0~14 구간의 레벨당 증가폭. */
    public static final int XP_BAR_TIER0_STEP = 2;
    /** 15~29 구간: {@code 37 + 5*(level-15)}. */
    public static final int XP_BAR_TIER1_BASE = 37;
    /** 15~29 구간의 레벨당 증가폭. */
    public static final int XP_BAR_TIER1_STEP = 5;
    /** 30 이상 구간: {@code 112 + 9*(level-30)}. */
    public static final int XP_BAR_TIER2_BASE = 112;
    /** 30 이상 구간의 레벨당 증가폭. */
    public static final int XP_BAR_TIER2_STEP = 9;

    // ── 사망 손실 ──
    /** 사망 시 레벨당 떨어뜨리는 XP. */
    public static final int XP_DROP_PER_LEVEL = 7;
    /** 사망 시 떨어뜨리는 XP 상한(바닐라와 동일). */
    public static final int XP_DROP_CAP = 100;

    // ── 경험치 오브 ──
    /** 오브가 플레이어에게 끌려오기 시작하는 반경(블록). 아이템 드랍과 같은 흡인 속도를 쓴다. */
    public static final double XP_ORB_MAGNET_RADIUS = 1.0;
    /**
     * 두 오브가 하나로 합쳐지는 반경(블록). {@link #XP_ORB_MAGNET_RADIUS}보다 반드시 좁아야 한다 —
     * 같은 값이면 끌리기 시작하는 순간 전부 한 덩어리가 되어 여러 오브가 보이지 않는다.
     */
    public static final double XP_ORB_MERGE_RADIUS = 0.5;
    /** 병합 판정용 제곱 반경(제곱근 회피). 정적판은 같은 값을 리터럴 0.25 로 적는다. */
    public static final double XP_ORB_MERGE_RADIUS_SQUARED =
            XP_ORB_MERGE_RADIUS * XP_ORB_MERGE_RADIUS;
    /** 오브 하나가 담을 수 있는 경험치 상한(분할 사다리의 맨 윗칸). */
    public static final int XP_ORB_MAX_AMOUNT = 2477;
    /** 오브의 수명 상한(10분, 10 TPS 기준). XP 오브 수명 판정과 내구 스냅샷이 함께 쓴다. */
    public static final int XP_ORB_DESPAWN_AGE = 6000;
    /**
     * 쪼갠 오브를 흩뿌리는 폭(블록, 중심 기준 ±절반). 0이면 같은 자리에 겹쳐 나와 그 틱의 병합이
     * 곧바로 다시 한 덩어리로 만들어 버리므로, 분할 사다리와 짝을 이루는 값이다.
     */
    public static final double XP_ORB_SPAWN_JITTER = 0.4;
    /** 스폰 직후 수평 속도 폭(블록/틱, 중심 기준 ±절반). */
    public static final double XP_ORB_SPAWN_SPEED = 0.08;
    /** 스폰 직후 수직 속도(블록/틱). */
    public static final double XP_ORB_SPAWN_LIFT = 0.08;
    /** 자석이 겨냥하는 플레이어 높이(발 기준 오프셋). 발밑이 아니라 몸통 쪽으로 끌린다. */
    /**
     * 오브를 하나 흡수한 뒤 다음 흡수까지의 유예(서버 틱). 바닐라 {@code Player.takeXpDelay} 는
     * 20 TPS 기준 2틱이므로 10 TPS 서버에서는 1틱이다(같은 0.1초). 이게 없으면 오브 더미 위에
     * 서는 순간 한 틱에 전부 빨려 들어가 획득 사운드 케이던스가 바닐라와 달라진다.
     */
    public static final int XP_ORB_TAKE_DELAY_TICKS = 1;

    public static final double XP_ORB_MAGNET_AIM_HEIGHT = 0.5;
    /**
     * 자석 흡인 <b>가속도</b>(블록/틱²). 속도를 덮어쓰지 않고 더하기만 하며, 그 뒤에는 언제나
     * 중력·마찰·블록 충돌 적분을 거친다. 위치를 직접 옮기면 오브가 벽을 통과한다.
     */
    public static final double XP_ORB_MAGNET_PULL = 0.1;
    /**
     * 오브 획득 판정 상자. 오브는 드랍 아이템과 <b>같은 상자</b>로 흡수돼야 하므로
     * {@link ItemEntitySystem} 의 값을 그대로 쓴다(정적판은 같은 수를 리터럴로 적는다).
     * 자석 반경보다 넓어서, 끌리지 않는 거리에서도 스쳐 지나가면 주워진다(바닐라와 동일).
     */
    public static final double XP_ORB_PICKUP_HORIZONTAL = ItemEntitySystem.PLAYER_PICKUP_HORIZONTAL;
    /** 획득 판정 상자의 아래쪽 여유(발 기준). */
    public static final double XP_ORB_PICKUP_BELOW = ItemEntitySystem.PLAYER_PICKUP_BELOW;
    /** 획득 판정 상자의 위쪽 여유(발 기준). */
    public static final double XP_ORB_PICKUP_ABOVE = ItemEntitySystem.PLAYER_PICKUP_ABOVE;

    /**
     * 바닐라 {@code ExperienceOrb.getExperienceValue} 분할 사다리. 큰 칸부터 내려오며 남은 양이
     * 그 칸 이상이면 그 크기의 오브 하나를 떼어낸다. 맨 윗칸이 {@link #XP_ORB_MAX_AMOUNT}다.
     */
    private static final int[] XP_ORB_SPLIT_LADDER = {
        2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1,
    };

    // ── 채굴 XP 구간(바닐라 블록별 XP 드랍) ──
    /** 석탄 광석 0~2. */
    public static final int XP_COAL_MIN = 0;
    public static final int XP_COAL_MAX = 2;
    /** 다이아몬드·에메랄드 광석 3~7. */
    public static final int XP_GEM_MIN = 3;
    public static final int XP_GEM_MAX = 7;
    /** 청금석 광석 2~5. */
    public static final int XP_LAPIS_MIN = 2;
    public static final int XP_LAPIS_MAX = 5;
    /** 레드스톤 광석 1~5. */
    public static final int XP_REDSTONE_MIN = 1;
    public static final int XP_REDSTONE_MAX = 5;
    /** 네더 금 광석 0~1. */
    public static final int XP_NETHER_GOLD_MIN = 0;
    public static final int XP_NETHER_GOLD_MAX = 1;
    /**
     * [DEEP-DARK] 스컬크 본체·정맥 채굴 XP. 바닐라는 정확히 1 고정이라 하한=상한이다.
     * [B] minecraft.wiki «Sculk».
     */
    public static final int XP_SCULK = 1;
    /**
     * [DEEP-DARK] 스컬크 촉매·감지체·비명체 채굴 XP. 바닐라는 정확히 5 고정이다.
     * [B] minecraft.wiki «Sculk Catalyst»·«Sculk Sensor»·«Sculk Shrieker».
     */
    public static final int XP_SCULK_FIXTURE = 5;
    /** 스포너 15~43. */
    public static final int XP_SPAWNER_MIN = 15;
    public static final int XP_SPAWNER_MAX = 43;

    // ── 몹 처치 XP ──
    /**
     * 적대 몹 처치 XP 기본값. 바닐라 {@code Monster} 생성자의 {@code this.xpReward = 5} 이며,
     * 종별 표에서 따로 적히지 않은 적대 종이 전부 이 값을 받는다.
     * [A] minecraft.wiki «Experience» 「Killing mobs」 = {@code 5 + 1–3 (per equipment)}.
     */
    public static final int XP_HOSTILE_KILL = 5;
    /** 동물·주변 몹 처치 XP 하한. 바닐라 {@code Animal#getExperienceReward} = {@code 1 + rand(3)}. */
    public static final int XP_PASSIVE_KILL_MIN = 1;
    /** 동물·주변 몹 처치 XP 상한. */
    public static final int XP_PASSIVE_KILL_MAX = 3;
    /**
     * XP 를 아예 주지 않는 종의 값. 바닐라 {@code Mob.xpReward} 기본값 0 이며 주민·떠돌이 상인·
     * 골렘 3종·알레이·박쥐·올챙이가 여기 든다.
     * [A] minecraft.wiki «Experience»: "Baby animals, bats, golems, and villagers give no
     * experience orbs at all", «Tadpole»: "tadpoles do not drop any items or experience".
     */
    public static final int XP_NO_KILL = 0;
    /**
     * 이보커·가디언·엘더 가디언·브리즈의 고정 처치 XP.
     * [A] minecraft.wiki «Experience» 표의 {@code 10} 행 · «Breeze»
     * "{{xp|10}} experience orbs are dropped if killed by a player or tamed wolf".
     */
    public static final int XP_KILL_TIER_TEN = 10;
    /**
     * 라베저의 고정 처치 XP. 바닐라 {@code Ravager} 생성자의 {@code this.xpReward = 20}.
     * [A] minecraft.wiki «Experience» 표의 {@code 20 + 1–3 (per equipment)} 행.
     */
    public static final int XP_KILL_RAVAGER = 20;
    /**
     * [SULFUR] 유황 큐브(큰 개체) 처치 XP 하한. 바닐라 대응이 없는 26.2 종이라 등급 [B] 이며
     * 값의 근거는 {@code docs/research/mc-sulfur-26.2.md} §5 "큰 개체는 … 경험치 1–2,
     * 작은 개체는 없음" 이다. 분열한 작은 개체는 {@link #xpForMobKill}의 크기 관문에서 0이 된다.
     */
    public static final int XP_KILL_SULFUR_CUBE_MIN = 1;
    /** [SULFUR] 유황 큐브(큰 개체) 처치 XP 상한. */
    public static final int XP_KILL_SULFUR_CUBE_MAX = 2;
    /**
     * 새끼 배율(고정소수 ×1000). 바닐라 {@code Zombie#getExperienceReward} 의
     * {@code this.xpReward = (int)(this.xpReward * 2.5F)} 그대로이며, 적대 몹 5 × 2.5 = 12 다.
     * 방어구 보너스는 이 배율 <b>뒤에</b> 붙는다(바닐라가 {@code super.getExperienceReward()} 를
     * 마지막에 부르기 때문).
     */
    public static final int BABY_KILL_XP_MULTIPLIER_MILLI = 2500;
    /**
     * 방어구 한 칸이 더하는 XP 하한. 바닐라 {@code Mob#getExperienceReward} 의
     * {@code i += 1 + this.random.nextInt(3)} 이며, 비어 있지 않은 방어구 칸마다 한 번씩 돈다.
     * 기본 XP 가 0인 종은 그 루프 자체를 돌지 않는다({@code if (this.xpReward > 0)} 가드).
     */
    public static final int XP_ARMOR_BONUS_MIN = 1;
    /** 방어구 한 칸 보너스의 난수 폭({@code nextInt(3)}). 즉 칸당 1~3 이다. */
    public static final int XP_ARMOR_BONUS_SPAN = 3;
    /** 방어구를 하나도 끼지 않은 몹에게 쓰는 빈 굴림 배열. */
    public static final int[] NO_ARMOR_ROLLS = new int[0];
    /** 번식 성공 XP 하한. */
    public static final int XP_BREEDING_MIN = 1;
    /** 번식 성공 XP 상한(바닐라 {@code 1 + rand(7)}). */
    public static final int XP_BREEDING_MAX = 7;
    /**
     * 주민 거래 성사 XP 하한. 바닐라 {@code AbstractVillager#rewardTradeXp} 의
     * {@code int i = 3 + this.random.nextInt(4)} 이라 3~6 이다.
     */
    public static final int XP_TRADE_MIN = 3;
    /** 주민 거래 성사 XP 상한. */
    public static final int XP_TRADE_MAX = 6;

    // ── 제련 XP(바닐라 값 ×1000) ──
    /** 철·구리 주괴 0.7. */
    public static final int SMELT_XP_INGOT_MILLI = 700;
    /** 금 주괴 1.0. */
    public static final int SMELT_XP_GOLD_INGOT_MILLI = 1 * MILLI;
    /** 숯 0.15. */
    public static final int SMELT_XP_CHARCOAL_MILLI = 150;
    /** 익힌 음식 0.35. */
    public static final int SMELT_XP_COOKED_FOOD_MILLI = 350;
    /** 유리·돌 0.1. */
    public static final int SMELT_XP_BLOCK_MILLI = 100;
    /** 고대 잔해 → 네더라이트 파편 2.0. */
    public static final int SMELT_XP_NETHERITE_SCRAP_MILLI = 2 * MILLI;

    /**
     * 해당 레벨에서 다음 레벨까지 필요한 XP(경험치 바 한 칸 전체).
     * 바닐라 {@code Player.getXpNeededForNextLevel} 그대로다.
     */
    public static int xpBarCap(int level) {
        if (level >= XP_BAR_TIER2_LEVEL) {
            return XP_BAR_TIER2_BASE + (level - XP_BAR_TIER2_LEVEL) * XP_BAR_TIER2_STEP;
        }
        if (level >= XP_BAR_TIER1_LEVEL) {
            return XP_BAR_TIER1_BASE + (level - XP_BAR_TIER1_LEVEL) * XP_BAR_TIER1_STEP;
        }
        return XP_BAR_TIER0_BASE + level * XP_BAR_TIER0_STEP;
    }

    /**
     * 누적 XP에서 레벨을 역산한다. 닫힌 식(sqrt)이 아니라 {@link #xpBarCap(int)}를 차감하는 정수 루프이며,
     * {@link #MAX_LEVEL_SCAN}에서 반드시 멈춘다.
     */
    public static int levelFromTotal(int xpTotal) {
        if (xpTotal <= 0) return 0;
        int remaining = xpTotal;
        int level = 0;
        while (level < MAX_LEVEL_SCAN) {
            int cap = xpBarCap(level);
            if (remaining < cap) break;
            remaining -= cap;
            level++;
        }
        return level;
    }

    /** 그 레벨에 막 도달했을 때의 누적 XP. {@link #levelFromTotal(int)}의 역함수다. */
    public static int totalForLevel(int level) {
        if (level <= 0) return 0;
        int capped = Math.min(level, MAX_LEVEL_SCAN);
        int total = 0;
        for (int i = 0; i < capped; i++) total += xpBarCap(i);
        return total;
    }

    /** 현재 레벨 안에서의 진행 XP(경험치 바에 채워진 양). */
    public static int progressWithinLevel(int xpTotal) {
        if (xpTotal <= 0) return 0;
        return xpTotal - totalForLevel(levelFromTotal(xpTotal));
    }

    /** 경험치 바 충전율(0~1000 permille). HUD가 부동소수 없이 폭을 계산할 수 있게 정수로 준다. */
    public static int xpBarFillPermille(int xpTotal) {
        int level = levelFromTotal(xpTotal);
        int cap = xpBarCap(level);
        if (cap <= 0) return 0;
        int progress = xpTotal - totalForLevel(level);
        if (progress <= 0) return 0;
        if (progress >= cap) return MILLI;
        return progress * MILLI / cap;
    }

    /**
     * 남은 경험치에서 다음 오브 하나가 가져갈 양(바닐라 분할 사다리). 100은 73 → 17 → 7 → 3으로
     * 오브 네 개가 된다. 두 권위가 같은 개수의 오브(=같은 획득 사운드 횟수)를 내도록
     * 이 사다리 하나만 쓴다.
     */
    public static int xpOrbSplit(int remaining) {
        for (int step : XP_ORB_SPLIT_LADDER) {
            if (remaining >= step) return step;
        }
        return 1;
    }

    /** 사망 시 떨어뜨리는 XP. 나머지는 소멸하고 {@code xpTotal}은 0이 된다. */
    public static int xpDroppedOnDeath(int level) {
        if (level <= 0) return 0;
        return Math.min(XP_DROP_CAP, XP_DROP_PER_LEVEL * level);
    }

    /**
     * {@code levels} 레벨만큼 차감한 뒤의 누적 XP. 바닐라 인챈트 비용처럼 "레벨 단위"로 깎을 때 쓴다.
     *
     * <p><b>바닐라는 절대량이 아니라 비율을 유지한다.</b> {@code Player#giveExperienceLevels} 는
     * {@code experienceLevel} 만 줄이고 {@code experienceProgress}(0~1 <b>비율</b>)는 손대지 않으며,
     * 레벨이 음수로 내려갈 때만 {@code experienceProgress}·{@code totalExperience} 를 0 으로 만든다.
     * 남은 레벨의 바 용량이 좁아지므로 <b>절대 진행량은 비율만큼 함께 줄어든다</b> — 30레벨 바 절반
     * (56/112)에서 15레벨을 쓰면 18/37 이지 56 이 잘려 36 이 되는 게 아니다.
     *
     * <p>이 프로젝트는 정수 {@code xpTotal} 하나만 들고 있으므로 비율을 {@code progress × newCap /
     * oldCap} 정수 나눗셈으로 다시 만든다. 부동소수를 끼우지 않아 두 권위가 같은 값을 낸다.
     */
    public static int totalAfterSpendingLevels(int xpTotal, int levels) {
        if (levels <= 0) return Math.max(xpTotal, 0);
        int level = levelFromTotal(xpTotal);
        int target = level - levels;
        // 바닐라는 레벨이 정확히 0이 되는 경우 바 진행도(비율)를 살려 둔다(음수일 때만 전부 버린다).
        if (target < 0) return 0;
        int progress = xpTotal - totalForLevel(level);
        if (progress <= 0) return totalForLevel(target);
        int cap = xpBarCap(level);
        int targetCap = xpBarCap(target);
        // 0 방향 절삭이라 progress < cap 이면 결과도 반드시 targetCap 미만이다(레벨이 오르지 않는다).
        // long 승격은 필수다 — 최고 레벨대의 바 용량(약 196,609)끼리 곱하면 int 를 넘는다.
        int scaled = (int) ((long) progress * targetCap / cap);
        if (scaled > targetCap - 1) scaled = targetCap - 1;
        return totalForLevel(target) + scaled;
    }

    /** 채굴 XP 하한. XP를 떨어뜨리지 않는 블록이면 0. */
    public static int minedBlockXpMin(int block) {
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return XP_COAL_MIN;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return XP_GEM_MIN;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return XP_GEM_MIN;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return XP_LAPIS_MIN;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return XP_REDSTONE_MIN;
        }
        if (block == Blocks.NETHER_GOLD_ORE) return XP_NETHER_GOLD_MIN;
        if (block >= Blocks.SPAWNER_BASE && block <= Blocks.SPAWNER_BASE + 2) return XP_SPAWNER_MIN;
        // [DEEP-DARK] 스컬크 계열은 고정 XP 라 하한/상한이 같다 — 구간 난수를 쓰지 않는다.
        if (block == Blocks.SCULK || block == Blocks.SCULK_VEIN) return XP_SCULK;
        if (Blocks.isSculkBlock(block)) return XP_SCULK_FIXTURE;
        return 0;
    }

    /** 채굴 XP 상한. XP를 떨어뜨리지 않는 블록이면 0. */
    public static int minedBlockXpMax(int block) {
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return XP_COAL_MAX;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return XP_GEM_MAX;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return XP_GEM_MAX;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return XP_LAPIS_MAX;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return XP_REDSTONE_MAX;
        }
        if (block == Blocks.NETHER_GOLD_ORE) return XP_NETHER_GOLD_MAX;
        if (block >= Blocks.SPAWNER_BASE && block <= Blocks.SPAWNER_BASE + 2) return XP_SPAWNER_MAX;
        if (block == Blocks.SCULK || block == Blocks.SCULK_VEIN) return XP_SCULK;
        if (Blocks.isSculkBlock(block)) return XP_SCULK_FIXTURE;
        return 0;
    }

    /**
     * 블록을 캤을 때 나오는 XP. 실크 터치로 캔 경우 호출자가 이 함수를 아예 부르지 않는다
     * (실크 터치는 광석을 원형 그대로 주므로 XP가 0이다).
     *
     * @param roll 호출자가 뽑은 음이 아닌 난수. 구간 폭으로 나눈 나머지가 구간 안 위치가 된다.
     */
    public static int xpForMinedBlock(int block, int roll) {
        int min = minedBlockXpMin(block);
        int max = minedBlockXpMax(block);
        return rangeFromRoll(min, max, roll);
    }

    /**
     * 종별 처치 XP 하한(= 바닐라 {@code Mob.xpReward}. 구간을 갖는 종만 상한과 다르다).
     * 새끼 배율·방어구 보너스가 붙기 <b>전</b> 값이다.
     *
     * <p>표에 적히지 않은 종은 축 두 개로 갈린다: 적대({@link MobType#hostile()})면
     * {@link #XP_HOSTILE_KILL}, 아니면 동물 구간 {@link #XP_PASSIVE_KILL_MIN}~
     * {@link #XP_PASSIVE_KILL_MAX} 다. WebCraft 창작몹은 바닐라 원문이 없어 전부 이 기본값을
     * 쓴다(등급 [C]).
     *
     * @param slimeSize 슬라임의 크기(1·2·4). 슬라임이 아니면 무시된다.
     */
    public static int mobKillXpMin(MobType kind, int slimeSize) {
        return switch (kind) {
            // 바닐라 Slime#setSize 의 this.xpReward = i — 크기가 곧 XP 다(큰 4·중간 2·작은 1).
            case SLIME -> Math.max(slimeSize, 0);
            case VILLAGER, WANDERING_TRADER, IRON_GOLEM, SNOW_GOLEM, COPPER_GOLEM, ALLAY,
                    BAT, TADPOLE,
                    // [C] 시체 까마귀는 박쥐와 같은 AMBIENT 계약이라 같은 0 을 쓴다.
                    CARRION_CROW,
                    // [EC-MOBS][DRAGON] 액자·엔드 수정은 살아 있는 개체가 아니고, 드래곤 XP 는 사망 연출이 준다.
                    ITEM_FRAME, END_CRYSTAL, ENDER_DRAGON -> XP_NO_KILL;
            case EVOKER, GUARDIAN, ELDER_GUARDIAN, BREEZE -> XP_KILL_TIER_TEN;
            case RAVAGER -> XP_KILL_RAVAGER;
            case BABY_GHOUL -> 8;
            case BONE_PROCESSION -> 15;
            case HANGING_MAW -> 6;
            case SULFUR_CUBE -> XP_KILL_SULFUR_CUBE_MIN;
            // [A] 좀비 나우틸러스는 MONSTER cap 을 쓰지만 바닐라 표의 1–3 행에 있다.
            case ZOMBIE_NAUTILUS -> XP_PASSIVE_KILL_MIN;
            default -> kind.hostile() ? XP_HOSTILE_KILL : XP_PASSIVE_KILL_MIN;
        };
    }

    /** 종별 처치 XP 상한. 고정 XP 종은 {@link #mobKillXpMin(MobType, int)} 과 같은 값이다. */
    public static int mobKillXpMax(MobType kind, int slimeSize) {
        return switch (kind) {
            case SLIME -> Math.max(slimeSize, 0);
            case VILLAGER, WANDERING_TRADER, IRON_GOLEM, SNOW_GOLEM, COPPER_GOLEM, ALLAY,
                    BAT, TADPOLE, CARRION_CROW, ITEM_FRAME, END_CRYSTAL, ENDER_DRAGON -> XP_NO_KILL;
            case EVOKER, GUARDIAN, ELDER_GUARDIAN, BREEZE -> XP_KILL_TIER_TEN;
            case RAVAGER -> XP_KILL_RAVAGER;
            case BABY_GHOUL -> 8;
            case BONE_PROCESSION -> 15;
            case HANGING_MAW -> 6;
            case SULFUR_CUBE -> XP_KILL_SULFUR_CUBE_MAX;
            case ZOMBIE_NAUTILUS -> XP_PASSIVE_KILL_MAX;
            default -> kind.hostile() ? XP_HOSTILE_KILL : XP_PASSIVE_KILL_MAX;
        };
    }

    /**
     * 몹 처치 XP. 바닐라 {@code Mob#getExperienceReward} 의 순서를 그대로 따른다:
     * 종별 {@code xpReward} → (새끼면) ×2.5 → 방어구 칸마다 {@code 1 + rand(3)}.
     *
     * <p>{@code baby} 제외는 <b>동물 새끼</b>에만 적용된다. 바닐라가 XP를 주지 않는 대상은 어린 가축이지
     * 적대 몹이 아니므로, 적대 판정을 새끼 판정보다 <b>먼저</b> 본다. 좀비 새끼
     * ({@code BABY_ZOMBIE})는 {@code Mob} 생성자가 {@code babyForm}을 강제로 세워
     * {@code isBaby()}가 참이며, 바닐라 {@code Zombie#getExperienceReward} 대로 5×2.5 = 12 를 받는다.
     *
     * @param slimeSize   슬라임 크기(1·2·4). 슬라임이 아니면 0 을 넘긴다.
     * @param roll        호출자가 뽑은 음이 아닌 난수(구간을 갖는 종에만 쓰인다)
     * @param armorRolls  <b>비어 있지 않은 방어구 칸마다 하나씩</b> 뽑은 음이 아닌 난수.
     *                    방어구가 없으면 {@link #NO_ARMOR_ROLLS}.
     */
    public static int xpForMobKill(MobType kind, int slimeSize, boolean baby,
            boolean killedByPlayer, int roll, int[] armorRolls) {
        if (!killedByPlayer) return 0;
        boolean isBaby = kind != MobType.BABY_GHOUL && (baby || kind == MobType.BABY_ZOMBIE);
        // 26.2 유황 큐브는 적대몹이지만 작은 개체는 XP 자체가 없다. 일반 적대 새끼의 ×2.5
        // 배율보다 이 종별 계약이 먼저다.
        if (kind == MobType.SULFUR_CUBE && isBaby) return 0;
        // 바닐라: 어린 가축은 아무것도 주지 않는다. 적대 몹의 새끼는 오히려 배율을 받는다.
        if (isBaby && !kind.hostile()) return 0;
        int reward = rangeFromRoll(mobKillXpMin(kind, slimeSize), mobKillXpMax(kind, slimeSize),
                roll);
        // 바닐라 Mob#getExperienceReward 의 if (this.xpReward > 0) 가드. 0 인 종은 방어구를
        // 껴도 0 이고, 호출자는 그래서 굴림을 뽑지도 않는다.
        if (reward <= 0) return 0;
        if (isBaby) reward = reward * BABY_KILL_XP_MULTIPLIER_MILLI / MILLI;
        int total = reward;
        if (armorRolls != null) {
            for (int armorRoll : armorRolls) {
                total += rangeFromRoll(XP_ARMOR_BONUS_MIN,
                        XP_ARMOR_BONUS_MIN + XP_ARMOR_BONUS_SPAN - 1, armorRoll);
            }
        }
        return total;
    }

    /**
     * 그 처치가 방어구 보너스 굴림을 뽑아야 하는가. 바닐라 {@code Mob#getExperienceReward} 의
     * {@code xpReward > 0} 가드와 같은 판정이며, 호출자가 <b>난수를 뽑기 전</b>에 물어
     * XP 를 주지 않는 종에서 난수열이 흔들리지 않게 한다.
     */
    public static boolean mobKillAwardsXp(MobType kind, int slimeSize, boolean baby,
            boolean killedByPlayer) {
        if (!killedByPlayer) return false;
        if (kind == MobType.SULFUR_CUBE && baby) return false;
        if ((baby || kind == MobType.BABY_ZOMBIE) && !kind.hostile()) return false;
        return mobKillXpMax(kind, slimeSize) > 0;
    }

    /**
     * 주민 거래 한 번이 플레이어에게 주는 XP 3~6. 바닐라
     * {@code AbstractVillager#rewardTradeXp} 의 {@code 3 + this.random.nextInt(4)} 이며,
     * 구슬은 주민 자리({@code getY() + 0.5})에서 나온다.
     */
    public static int xpForVillagerTrade(int roll) {
        return rangeFromRoll(XP_TRADE_MIN, XP_TRADE_MAX, roll);
    }

    /** 번식 성공 XP 1~7(바닐라 {@code 1 + rand(7)}). */
    public static int xpForBreeding(int roll) {
        return rangeFromRoll(XP_BREEDING_MIN, XP_BREEDING_MAX, roll);
    }

    /**
     * 제련 산출물 1개가 화로에 쌓는 XP(milli). 바닐라 값의 ×1000이며 등록되지 않은 산출물은 0이다.
     *
     * <p>바닐라의 벽돌(0.3)은 이 프로젝트에 벽돌 아이템 ID가 없어 표에 넣지 않았다.
     * 벽돌 아이템이 생기면 300을 추가한다.
     */
    public static int smeltXpMilli(short itemType) {
        if (itemType == PlayerInventory.IRON_INGOT || itemType == PlayerInventory.COPPER_INGOT) {
            return SMELT_XP_INGOT_MILLI;
        }
        if (itemType == PlayerInventory.GOLD_INGOT) return SMELT_XP_GOLD_INGOT_MILLI;
        // 금속 장비 회수 제련은 세 재료 모두 공식 레시피 experience=0.1이다.
        if (itemType == PlayerInventory.IRON_NUGGET
                || itemType == PlayerInventory.GOLD_NUGGET
                || itemType == PlayerInventory.COPPER_NUGGET) {
            return SMELT_XP_BLOCK_MILLI;
        }
        if (itemType == PlayerInventory.CHARCOAL) return SMELT_XP_CHARCOAL_MILLI;
        if (itemType == PlayerInventory.NETHERITE_SCRAP) return SMELT_XP_NETHERITE_SCRAP_MILLI;
        if (itemType == PlayerInventory.BEEF_COOKED || itemType == PlayerInventory.PORK_COOKED
                || itemType == PlayerInventory.MUTTON_COOKED
                || itemType == PlayerInventory.CHICKEN_COOKED
                || itemType == PlayerInventory.RABBIT_COOKED
                || itemType == PlayerInventory.BAKED_POTATO
                || itemType == PlayerInventory.COD_COOKED
                || itemType == PlayerInventory.SALMON_COOKED) {
            return SMELT_XP_COOKED_FOOD_MILLI;
        }
        if (itemType == (short) Blocks.GLASS || itemType == (short) Blocks.STONE) {
            return SMELT_XP_BLOCK_MILLI;
        }
        // [VOID-END] recipe/popped_chorus_fruit.json experience 0.1.
        if (itemType == PlayerInventory.POPPED_CHORUS_FRUIT) return SMELT_XP_BLOCK_MILLI;
        // [ARMOR-TRIM] recipe/resin_brick.json experience 0.1.
        if (itemType == PlayerInventory.RESIN_BRICK) return SMELT_XP_BLOCK_MILLI;
        return 0;
    }

    /** 화로에 쌓인 milli XP 중 실제로 지급할 정수부. */
    public static int collectedSmeltXp(int accumulatedMilli) {
        if (accumulatedMilli <= 0) return 0;
        return accumulatedMilli / MILLI;
    }

    /** 지급 후 화로에 남기는 milli 나머지. */
    public static int remainingSmeltXpMilli(int accumulatedMilli) {
        if (accumulatedMilli <= 0) return 0;
        return accumulatedMilli % MILLI;
    }

    /** 음이 아닌 난수를 닫힌 구간 {@code [min, max]}으로 사상한다. 나머지 편향은 의도적이다. */
    private static int rangeFromRoll(int min, int max, int roll) {
        int span = max - min + 1;
        if (span <= 1) return min;
        int safe = roll < 0 ? -roll : roll;
        return min + safe % span;
    }
}
