package com.gameexpert.engine.crop;

import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.terrain.Blocks;

/**
 * [CROP-BERRY] 달콤한 열매 덤불의 작은 정적 정본. 근거는 [A] {@code SweetBerryBushBlock} 이고
 * 수치는 {@code docs/research/mc-crops-berries.md} §3 에 이미 고정되어 있던 것을 그대로 옮겼다.
 *
 * <p>이 블록은 {@link CropRules} 에 넣지 않았다 — {@code CropRules} 는 <b>경작지 위에 씨앗을
 * 심는</b> 작물의 표이고(지지 종류 {@code FARMLAND_BELOW} · {@code forSeed} 심기 분기), 달콤한
 * 열매 덤불은 바닐라에서도 {@code CropBlock} 이 아니라 {@code BushBlock} 이다. 성장 조건(광량
 * 9)·수확 형태(우클릭으로 열매만 따고 블록은 남는다)·접촉 피해가 전부 다르므로 표를 억지로
 * 넓히면 기존 다섯 작물의 분기가 오염된다.
 *
 * <p>난수는 전부 {@code IntUnaryOperator} 로 <b>주입</b>한다({@code CropRules.matureDropCount}
 * 선례). 정적판 {@code StandaloneSweetBerryBushRules.ts} 와 굴림당 결과가 같아야 한다.
 */
public final class SweetBerryBushRules {
    private SweetBerryBushRules() {
    }

    /** [A] {@code MAX_AGE = 3}. 0 = 어린 싹 · 1 = 열매 없음 · 2 = 열매 조금 · 3 = 열매 가득. */
    public static final int MAX_AGE = 3;

    /** age 를 담는 상태 비트. 0..3 이라 두 비트면 충분하다. */
    public static final int AGE_MASK = 0b11;

    /**
     * [A] {@code randomTick}: 위 칸 광량이 이 값 이상일 때만 자란다.
     * ({@code level.getRawBrightness(pos.above(), 0) >= 9})
     */
    public static final int GROWTH_LIGHT = 9;

    /** [A] {@code random.nextInt(5) == 0} — 랜덤틱마다 20%. */
    public static final int GROWTH_ROLL_DENOMINATOR = 5;

    /**
     * [A] {@code use}: 수확 뒤 age 는 <b>1</b> 로 돌아간다. 0 (어린 싹)이 아니다 — 0 으로
     * 되돌리면 다시 열매가 달리기까지 성장 단계가 하나 더 필요해져 회전이 느려진다.
     */
    public static final int AGE_AFTER_HARVEST = 1;

    /** [A] {@code use}: 열매를 딸 수 있는 최소 age. age 0·1 은 열매가 없다. */
    public static final int HARVESTABLE_AGE = 2;

    /**
     * [A] {@code entityInside}: {@code entity.hurt(damageSources().sweetBerryBush(), 1.0F)}.
     * 이 저장소의 선인장({@code EnvironmentSystem.CACTUS_DAMAGE})과 <b>같은 1 피해(0.5하트)</b>
     * 이고, 같은 5틱 피격 보호가 바닐라의 0.5초 간격을 만든다.
     */
    public static final int CONTACT_DAMAGE = 1;

    /**
     * [A] {@code entityInside}: 히트박스 안에 있어도 <b>움직일 때만</b> 아프다 —
     * {@code Math.abs(entity.getX() - entity.xOld) >= 0.003D
     * || Math.abs(entity.getZ() - entity.zOld) >= 0.003D}. 가만히 서 있으면 피해가 없다.
     */
    public static final double MOVE_EPSILON = 0.003;

    /**
     * [A] {@code makeStuckInBlock(state, new Vec3(0.8F, 0.75D, 0.8F))} 의 수평 항.
     * 매 틱 수평 속도에 곱해지므로 정상 속도의 약 34% 로 수렴한다. 거미줄(0.25) 선례와 같은
     * 훅({@code PackPhysicsRules.packMovementMultiplier})을 쓴다.
     */
    public static final double MOVEMENT_MULTIPLIER = 0.8;

    /** 상태에서 age 를 읽는다. 범위 밖 값은 잘라낸다. */
    public static int age(int state) {
        return Math.min(MAX_AGE, state & AGE_MASK);
    }

    /** age 를 상태로 담는다. */
    public static int state(int age) {
        return Math.min(MAX_AGE, Math.max(0, age)) & AGE_MASK;
    }

    /**
     * [A] {@code BushBlock.mayPlaceOn}: {@code #minecraft:dirt} 태그 또는 경작지.
     *
     * <p>흙 태그 쪽은 이미 {@link P6Rules#isSaplingSoil} 이 같은 목록을 들고 있어(잔디·흙·거친
     * 흙·podzol·균사체·뿌리 흙·이끼·진흙…) 사본을 새로 만들지 않고 재사용한다. 경작지만 더한다.
     */
    public static boolean isSoil(int below) {
        return P6Rules.isSaplingSoil(below) || below == Blocks.FARMLAND;
    }

    /**
     * [A] {@code randomTick}: age &lt; MAX_AGE 이고 위 칸 광량 ≥ 9 이고 {@code nextInt(5) == 0}
     * 일 때 한 단계 자란다.
     *
     * @param nextInt {@code [0, bound)} 균등 정수 난수
     */
    public static boolean shouldGrow(int age, int lightAbove,
            java.util.function.IntUnaryOperator nextInt) {
        if (age >= MAX_AGE || lightAbove < GROWTH_LIGHT) {
            return false;
        }
        return nextInt.applyAsInt(GROWTH_ROLL_DENOMINATOR) == 0;
    }

    /**
     * [A] {@code use}: {@code int i = 1 + random.nextInt(2);} 뒤
     * {@code popResource(i + (age == MAX_AGE ? 1 : 0))} — <b>age 2 → 1~2개, age 3 → 2~3개</b>.
     * 열매가 없는 age 0·1 은 0 을 돌려주며 이때 호출자는 블록을 건드리지 않는다.
     */
    public static int harvestDropCount(int age, java.util.function.IntUnaryOperator nextInt) {
        if (age < HARVESTABLE_AGE) {
            return 0;
        }
        return 1 + nextInt.applyAsInt(2) + (age >= MAX_AGE ? 1 : 0);
    }

    /**
     * [A] 파괴 전리품표 {@code blocks/sweet_berry_bush}: 수확과 <b>같은 분포</b>이되 열매가 없는
     * age 는 1개를 떨군다(덤불을 심을 열매 하나는 돌려받는다). 행운은 레벨당 +1 이다.
     */
    public static int breakDropCount(int age, int fortuneLevel,
            java.util.function.IntUnaryOperator nextInt) {
        int base = age >= HARVESTABLE_AGE ? harvestDropCount(age, nextInt) : 1;
        return base + Math.max(0, fortuneLevel);
    }

    /**
     * [A] {@code BonemealableBlock}: 광량·공간과 무관하게 한 단계 자란다. 이미 다 자랐으면
     * 뼛가루가 들지 않는다({@code isValidBonemealTarget} 이 age &lt; MAX_AGE 를 요구한다).
     */
    public static boolean isBoneMealTarget(int age) {
        return age < MAX_AGE;
    }

    /** [A] {@code entityInside}: age 0(어린 싹)은 가시가 없어 아프지 않다. */
    public static boolean damagesOnContact(int age) {
        return age > 0;
    }

    /** [A] 이동 판정: 수평 변위가 {@link #MOVE_EPSILON} 이상이라야 피해가 성립한다. */
    public static boolean movedEnoughToBeHurt(double dx, double dz) {
        return Math.abs(dx) >= MOVE_EPSILON || Math.abs(dz) >= MOVE_EPSILON;
    }
}
