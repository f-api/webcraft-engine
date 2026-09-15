package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * [SPEAR] 창(spear) 여섯 티어의 순수 규칙. 상태 없음.
 *
 * <p>발췌 핀은 {@code docs/research/mc-spear-1-21-11.md} 다 — Minecraft Java Edition 1.21.11
 * "Mounts of Mayhem" 의 위키 서술이며 <b>등급 B</b> 다. 이 저장소가 고정한 1.21.4 데이터
 * 스냅샷보다 한참 뒤 버전이라 이 트랙에는 [A] 원문 JSON 이 하나도 없다. 아래 표 값은 전부
 * 핀 §1 표를 그대로 옮긴 것이고, 파생하거나 반올림한 값은 하나도 없다. 잽 피해 · 공속 · 돌진({@code KineticWeapon})
 * 표와 조건은 핀 26.3-snapshot-7 jar({@code Items.<clinit>} · {@code Item$Properties.spear} ·
 * {@code KineticWeapon} · {@code KineticWeapon$Condition} javap)가 확정한 [A] 값이다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneSpearRules.ts} 이며 두
 * 권위가 같은 표·같은 판정을 공유해야 한다({@code SpearRulesParityTest} 가 두 파일의 표
 * 원문을 대조한다).
 *
 * <h2>이 모듈이 소유하지 <b>않는</b> 것</h2>
 * <ul>
 *   <li>ID — {@link com.gameexpert.terrain.Blocks} 가 정본이다.</li>
 *   <li>내구도 — {@link PlayerInventory#SPEAR_DURABILITY} 가 정본이다.</li>
 *   <li>권위 리치 상한 — {@link PlayerInteractionRules#ENTITY_REACH} + 패딩 = <b>6.0</b> 이며
 *       이 트랙은 그 값을 <b>건드리지 않는다</b>(아래 §리치 참조).</li>
 * </ul>
 */
public final class SpearRules {

    private SpearRules() {
    }

    // ── §1 티어표 (등급 B, 핀 §1) ────────────────────────────────────────────
    //
    // 배열 색인은 PlayerInventory.spearTier(type) 이며 ID 순서
    // (0 나무 · 1 돌 · 2 구리 · 3 철 · 4 금 · 5 다이아)다. 핀 표의 행 순서(나무·금·돌·
    // 구리·철·다이아)가 아니다 — 핀은 DPS 오름차순으로 정렬해 두었을 뿐이다.
    //
    // **이 축은 티어 서열에 대해 단조가 아니다.** 금(색인 4)은 나무(색인 0)와 같은 잽 피해에
    // 더 낮은 공속이라 DPS 가 전 티어 최저다(핀 §1 원문). 그래서 회귀는 "티어가 오르면 값도
    // 오른다" 를 단언하지 않고 표 값 자체를 못박는다.

    /**
     * [SPEAR][A] 잽 피해. 이 저장소의 {@code CombatRules.meleeDamage} 와 같은 20포인트 스케일의
     * <b>최종 피해</b>다(나무 검이 4 인 그 축). 핀 26.3 jar {@code Item.Properties.spear} 는
     * {@code ATTACK_DAMAGE} 에 {@code 0 + ToolMaterial.attackDamageBonus()} 를 ADD_VALUE 로 걸고
     * 플레이어 기본값이 1 이므로 {@code 1 + 보너스}다(WOOD 0 · STONE 1 · COPPER 1 · IRON 2 · GOLD 0 ·
     * DIAMOND 3 · NETHERITE 4 — {@code ToolMaterial.<clinit>}). 옛 위키 핀 §1 의 철 3.5 · 네더라이트
     * 5.5 는 핀 자신의 DPS 열과도 어긋나던 값이며 jar 가 3 · 5 로 확정한다.
     */
    public static final double[] JAB_DAMAGE = { 1.0, 2.0, 2.0, 3.0, 1.0, 4.0, 5.0 };

    /**
     * [SPEAR][A] 창의 공격 지속(초) — {@code Items.<clinit>} 가 {@code Item.Properties.spear} 의 첫
     * float 인수로 넘기는 값(나무 0.65f · 돌 0.75f · 구리 0.85f · 철 0.95f · 금 0.95f · 다이아 1.05f ·
     * 네더라이트 1.15f). 같은 값이 {@code SwingAnimation(STAB, (int)(d·20))} 도 정한다.
     */
    public static final float[] ATTACK_DURATION_SECONDS = {
        0.65f, 0.75f, 0.85f, 0.95f, 0.95f, 1.05f, 1.15f };

    /**
     * [SPEAR][A] 공속(초당 공격 횟수). {@code Item.Properties.spear} 가 {@code ATTACK_SPEED} 에
     * {@code (double)(1f / d) − 4.0} 를 ADD_VALUE 로 걸고 플레이어 기본값이 4.0 이므로
     * {@code 4.0 + ((double)(1f/d) − 4.0)} 이다(≈ 1.538 · 1.333 · 1.176 · 1.053 · 1.053 · 0.952 ·
     * 0.870 — 옛 핀 §1 표는 이를 소수 둘째 자리로 반올림한 값이었다). {@code CombatRules.attackSpeed}
     * 와 같은 축이라 10 TPS 풀 충전 틱은 {@code 10 / attackSpeed} 다.
     */
    public static final double[] ATTACK_SPEED = attackSpeedTable();

    private static double[] attackSpeedTable() {
        double[] speeds = new double[ATTACK_DURATION_SECONDS.length];
        for (int i = 0; i < speeds.length; i++) {
            speeds[i] = 4.0 + ((double) (1.0f / ATTACK_DURATION_SECONDS[i]) - 4.0);
        }
        return speeds;
    }

    /**
     * [SPEAR][A] 창의 {@code KineticWeapon}(돌진) 한 티어. 핀 26.3 jar {@code Items.<clinit>} 가
     * {@code Item.Properties.spear(material, attackDuration, damageMultiplier, delay, dismountTime,
     * dismountThreshold, knockbackTime, knockbackThreshold, damageTime, damageThreshold)} 에 넘기는 float 이며,
     * {@code spear} 가 {@code new KineticWeapon(10, (int)(delay·20f), Condition.ofAttackerSpeed((int)(dismountTime·20f),
     * dismountThreshold), Condition.ofAttackerSpeed((int)(knockbackTime·20f), knockbackThreshold),
     * Condition.ofRelativeSpeed((int)(damageTime·20f), damageThreshold), 0.38f, damageMultiplier, …)} 로 굽는다.
     * 틱은 MC 틱(20 TPS), 속도는 블록/초다.
     */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Kinetic {
        int delayTicks;
        int dismountMaxTicks;
        float dismountMinSpeed;
        int knockbackMaxTicks;
        float knockbackMinSpeed;
        int damageMaxTicks;
        float damageMinRelativeSpeed;
        float damageMultiplier;

    }

    /**
     * [SPEAR][A] {@code Items.<clinit>} 의 {@code spear(...)} float 인수 여덟(공격 지속 뒤): damageMultiplier,
     * delay, dismountTime, dismountThreshold, knockbackTime, knockbackThreshold, damageTime, damageThreshold.
     * 행 순서는 티어 색인(나무 · 돌 · 구리 · 철 · 금 · 다이아 · 네더라이트)이다.
     */
    private static final float[][] KINETIC_ARGUMENTS = {
        {0.7f, 0.75f, 5.0f, 14.0f, 10.0f, 5.1f, 15.0f, 4.6f},
        {0.82f, 0.7f, 4.5f, 13.0f, 9.0f, 5.1f, 13.75f, 4.6f},
        {0.82f, 0.65f, 4.0f, 12.0f, 8.25f, 5.1f, 12.5f, 4.6f},
        {0.95f, 0.6f, 2.5f, 11.0f, 6.75f, 5.1f, 11.25f, 4.6f},
        {0.7f, 0.7f, 3.5f, 13.0f, 8.5f, 5.1f, 13.75f, 4.6f},
        {1.075f, 0.5f, 3.0f, 10.0f, 6.5f, 5.1f, 10.0f, 4.6f},
        {1.2f, 0.4f, 2.5f, 9.0f, 5.5f, 5.1f, 8.75f, 4.6f},
    };

    /** [SPEAR][A] 티어별 {@link Kinetic}(초 → MC 틱은 float 곱 뒤 {@code (int)} 절삭 — jar 와 같은 산술). */
    public static final Kinetic[] KINETIC = kineticTable();

    private static Kinetic[] kineticTable() {
        Kinetic[] table = new Kinetic[KINETIC_ARGUMENTS.length];
        for (int i = 0; i < table.length; i++) {
            float[] a = KINETIC_ARGUMENTS[i];
            table[i] = new Kinetic((int) (a[1] * 20.0f), (int) (a[2] * 20.0f), a[3],
                    (int) (a[4] * 20.0f), a[5], (int) (a[6] * 20.0f), a[7], a[0]);
        }
        return table;
    }

    /** [SPEAR][A] {@code KineticWeapon.contactCooldownTicks}: 같은 대상을 다시 찌르기까지 MC 틱. */
    public static final int KINETIC_CONTACT_COOLDOWN_TICKS = 10;
    /**
     * [SPEAR][A] {@code damageEntities} 의 속도 배율은 플레이어 1, 그 밖의 쓰는 개체 0.2f 다(몹은
     * {@code MobKinetic.MOB_SPEED_FACTOR}). 명중 반동(HIT_FEEDBACK_TICKS 10)·전진량(forwardMovement 0.38)은
     * 클라 애니메이션({@code SpearAnimations.ts})이 소유한다.
     */
    public static final double KINETIC_PLAYER_SPEED_FACTOR = 1.0;

    // ── §2 리치 (등급 B, 핀 §3) ─────────────────────────────────────────────

    /**
     * [SPEAR][B] 창 잽의 최대 사거리 4.5 블록. 검을 비롯한 다른 모든 무기는 3 블록이라
     * <b>+1.5</b> 다(핀 §3).
     *
     * <p><b>이 값은 게임플레이 분기 전용이다.</b> 서버 권위 검증 상한은
     * {@link PlayerInteractionRules#ENTITY_REACH}(3.0) + {@link
     * PlayerInteractionRules#ENTITY_AUTHORITY_PADDING}(3.0) = <b>6.0</b> 이고 창의 4.5 는 그
     * 안쪽이라 <b>기존 검증을 그대로 통과한다</b>. 따라서 이 트랙은 안티치트 경계를 한 칸도
     * 넓히지 않는다 — {@code ENTITY_REACH} 를 4.5 로 올리면 그 상수는 모든 무기의 부정행위
     * 상한이라 검·도끼까지 함께 느슨해진다(핀 §3 "서버는 고칠 것이 없다").
     */
    public static final double SPEAR_REACH = 4.5;

    /**
     * [SPEAR][B] 창 잽의 <b>최소</b> 사거리 2 블록. 그보다 가까운 대상은 잽으로 맞출 수 없다
     * (핀 §3). 이 저장소의 어떤 무기에도 없던 <b>새 개념</b>이라 하한 분기를 여기서 신설한다.
     */
    public static final double SPEAR_MIN_REACH = 2.0;

    /** [SPEAR][B] 창 히트박스 판정에 주는 여유(inflation) 0.125 블록(핀 §3). */
    public static final double SPEAR_HITBOX_INFLATION = 0.125;

    // ── §3 돌진 조건 (등급 A, 핀 jar KineticWeapon) ─────────────────────────────

    /**
     * [SPEAR][A] {@code KineticWeapon$Condition.test(t, speed, relativeSpeed, factor)}: 쓰기 시작 뒤 지연을 뺀
     * MC 틱 {@code t} 가 창 끝 {@code maxTicks} 이하이고, 공격자 시선 방향 속도가 {@code minSpeed·factor} 이상,
     * 상대 속도가 {@code minRelativeSpeed·factor} 이상이면 성립한다(ofAttackerSpeed 는 상대 속도 하한 0,
     * ofRelativeSpeed 는 공격자 속도 하한 0).
     */
    public static boolean kineticCondition(int t, int maxTicks, double minSpeed, double minRelativeSpeed,
            double speed, double relativeSpeed, double factor) {
        return t <= maxTicks && speed >= minSpeed * factor && relativeSpeed >= minRelativeSpeed * factor;
    }

    /** [SPEAR][A] 하마 조건(공격자 속도 · dismount 창). */
    public static boolean kineticDismounts(Kinetic k, int t, double speed, double factor) {
        return kineticCondition(t, k.dismountMaxTicks(), k.dismountMinSpeed(), 0.0, speed, 0.0, factor);
    }

    /** [SPEAR][A] 넉백 조건(공격자 속도 · knockback 창). */
    public static boolean kineticKnocksBack(Kinetic k, int t, double speed, double factor) {
        return kineticCondition(t, k.knockbackMaxTicks(), k.knockbackMinSpeed(), 0.0, speed, 0.0, factor);
    }

    /** [SPEAR][A] 피해 조건(상대 속도 · damage 창). */
    public static boolean kineticDamages(Kinetic k, int t, double relativeSpeed, double factor) {
        return kineticCondition(t, k.damageMaxTicks(), 0.0, k.damageMinRelativeSpeed(), 0.0, relativeSpeed, factor);
    }

    /**
     * [SPEAR][A] {@code damageEntities} 의 돌진 피해: {@code (float) baseAttackDamage + Mth.floor(relativeSpeed ·
     * (double) damageMultiplier)}. 기본 공격력은 속성 <b>기본값</b>(플레이어 1)이라 창의 공격력 수정치를 더하지
     * 않는다. 이어 {@code Player.stabAttack} 이 인챈트 보너스를 더하고(쓰는 손이면 충전 배율 없음) 피해를 준다.
     */
    public static float kineticDamage(double baseAttackDamage, double relativeSpeed, float damageMultiplier) {
        return (float) baseAttackDamage + (float) Math.floor(relativeSpeed * (double) damageMultiplier);
    }

    /**
     * [SPEAR][A] 창 소리 worldSound kind. jar {@code Item.Properties.spear} 가 나무 창에만 {@code item.spear_wood.*}
     * ({@code SPEAR_WOOD_USE · SPEAR_WOOD_ATTACK · SPEAR_WOOD_HIT}), 나머지 여섯 티어에 {@code item.spear.*} 를 준다.
     * {@code action} 은 use(돌진 쓰기 시작 · KineticWeapon.sound) · attack(찌르기 · PiercingWeapon.sound) ·
     * hit(찌르기 명중 · hitSound) 셋이다.
     */
    public static String soundKind(short itemType, String action) {
        return (itemType == PlayerInventory.WOODEN_SPEAR ? "spear_wood_" : "spear_") + action;
    }

    /** [SPEAR][A] 찌르기 스윙({@code SwingAnimation(STAB, (int)(attackDuration · 20f))})의 MC 틱. 창이 아니면 0. */
    public static int stabSwingTicks(short itemType) {
        if (!PlayerInventory.isSpear(itemType)) return 0;
        return (int) (ATTACK_DURATION_SECONDS[PlayerInventory.spearTier(itemType)] * 20f);
    }

    /** [SPEAR][A] 돌진 인챈트 {@code lunge.json} 의 play_sound 목록(item.spear.lunge_1..3)에서 레벨로 고른 kind. */
    public static String lungeSoundKind(int level) {
        return "spear_lunge_" + Math.max(1, Math.min(3, level));
    }

    /** [SPEAR][A] 창 티어의 {@link Kinetic}. 창이 아니면 null. */
    public static Kinetic kinetic(short itemType) {
        if (!PlayerInventory.isSpear(itemType)) return null;
        return KINETIC[PlayerInventory.spearTier(itemType)];
    }

    // ── §4 표 조회 ──────────────────────────────────────────────────────────

    /** 창의 잽 피해. 창이 아니면 0(호출부가 기존 무기표로 떨어진다). */
    public static double jabDamage(short itemType) {
        int tier = PlayerInventory.spearTier(itemType);
        return tier < 0 ? 0.0 : JAB_DAMAGE[tier];
    }

    /** 창의 공속. 창이 아니면 0. */
    public static double attackSpeed(short itemType) {
        int tier = PlayerInventory.spearTier(itemType);
        return tier < 0 ? 0.0 : ATTACK_SPEED[tier];
    }

    // ── §5 게임플레이 리치 분기 ─────────────────────────────────────────────

    /**
     * 이 무기의 <b>게임플레이</b> 최대 리치. 창이면 {@link #SPEAR_REACH}, 그 외는 기존
     * {@link PlayerInteractionRules#ENTITY_REACH} 다. 권위 검증은 이 값을 읽지 않는다 —
     * 검증은 언제나 6.0 짜리 {@link PlayerInteractionRules#canInteractWithEntity} 다.
     */
    public static double meleeReach(short itemType) {
        return PlayerInventory.isSpear(itemType)
                ? SPEAR_REACH : PlayerInteractionRules.ENTITY_REACH;
    }

    /** 이 무기의 게임플레이 <b>최소</b> 리치. 창만 2.0 이고 나머지는 하한이 없다(0). */
    public static double meleeMinReach(short itemType) {
        return PlayerInventory.isSpear(itemType) ? SPEAR_MIN_REACH : 0.0;
    }

    /**
     * 조준 판정에서 대상 AABB 를 부풀릴 여유(블록). 창만 {@link #SPEAR_HITBOX_INFLATION} 이고
     * 나머지는 0 이다 — 호출부가 이 값을 AABB 반폭·높이에 더한다(정적판 {@code
     * MobManager.pickTarget} 의 {@code inflation} 인자와 같은 의미다).
     */
    public static double hitboxInflation(short itemType) {
        return PlayerInventory.isSpear(itemType) ? SPEAR_HITBOX_INFLATION : 0.0;
    }

    /**
     * 이 거리에서 잽이 맞는가. 창은 <b>하한 미달이면 불발</b>한다 — 너무 가까이 붙은 대상은
     * 창끝이 지나쳐 맞지 않는다는 핀 §3 원문 그대로다.
     *
     * @param distance 눈에서 대상 AABB 까지의 거리(블록). 히트박스 여유
     *                 {@link #SPEAR_HITBOX_INFLATION} 는 호출부가 AABB 를 부풀려 반영한다.
     */
    public static boolean jabConnects(short itemType, double distance) {
        if (distance < 0.0) return false;
        return distance <= meleeReach(itemType) && distance >= meleeMinReach(itemType);
    }

    // ── §6 크리티컬·스프린트 넉백 억제 (등급 B, 핀 §5) ──────────────────────

    /**
     * 이 무기는 크리티컬이 <b>아예 나지 않는가</b>. 창이면 참이다.
     *
     * <p>핀 §5 원문은 명확하다 — 잽은 "cannot do critical hits or sprint-knockback attacks"
     * 이고 돌진도 크리티컬이 아니다(베드락은 크리티컬 <b>파티클만</b> 나오고 실제 크리티컬이
     * 아니다). 따라서 이 저장소가 물었던 "공중 낙하 ×1.5 크리티컬과의 중첩" 규약의 답은
     * <b>곱하지 않는다</b> 이며, 새로 파생할 [C] 규약이 아니라 원문이 이미 답한 항목이다.
     */
    public static boolean suppressesCritical(short itemType) {
        return PlayerInventory.isSpear(itemType);
    }

    /** 이 무기는 스프린트 넉백(달리며 때릴 때의 추가 넉백)이 나지 않는가. 창이면 참이다. */
    public static boolean suppressesSprintKnockback(short itemType) {
        return PlayerInventory.isSpear(itemType);
    }

    /**
     * 이 무기는 힘(Strength)·나약함(Weakness) 보정을 받지 않는가. 창의 <b>돌진</b>은 두 효과의
     * 영향을 받지 않는다(핀 §5). 이 저장소의 효과 축이 근접 피해에 보정을 얹는 자리가 이
     * 술어를 지난다.
     */
    public static boolean ignoresStrengthAndWeakness(short itemType) {
        return PlayerInventory.isSpear(itemType);
    }

    // ── §8 자연 무장 (등급 B, 핀 §5) ────────────────────────────────────────

    /**
     * [SPEAR][B] 이 몹 종이 자연 스폰 때 드는 창. 창을 들지 않는 종이면
     * {@link PlayerInventory#EMPTY} 다.
     *
     * <p>핀 §5 원문 — <b>철 창</b>: 좀비 · 허스크 · 좀비 주민 · 좀비 기수 · 낙타 husk 기수,
     * <b>금 창</b>: 피글린 · 좀비화 피글린. 드랍은 사망 시 8.5%, 약탈 III 로 최대 11.5% 이며
     * 그 확률은 이미 {@code EquipmentDropRules} 가 소유한 공용 규칙과 <b>같은 값</b>이라 새
     * 상수를 만들지 않는다.
     *
     * <p><b>파치드는 창을 들지 않는다.</b> 핀 §5 가 {@code mc-parched-1-21-11.md} 의 무장
     * 서술을 정정했다 — 파치드는 스켈레톤 변종이고 <b>활</b>을 들며 "Parched can no longer
     * pick up spears" 다. 파치드 계열 정정은 별도 트랙의 몫이라 이 트랙은 손대지 않는다.
     */
    /**
     * <p>[SPEAR-MOB] 생성자에서 쥐는 창은 좀비화 피글린 · 좀비 피그맨의 금 창뿐이다({@code ZombifiedPiglin
     * .populateDefaultEquipmentSlots} 의 {@code nextInt(20) == 0} = 이 해시의 5%). 좀비 계열은 난이도를 보는
     * {@link #zombieSpawnWeapon} 을 첫 틱에, 성체 피글린은 {@code Piglin.createSpawnWeapon} 의 금 창 굴림을 첫 틱에 돈다.
     */
    public static short naturalSpearFor(com.gameexpert.engine.mob.MobType type) {
        return switch (type) {
            case ZOMBIFIED_PIGLIN, ZOMBIE_PIGMAN -> PlayerInventory.GOLD_SPEAR;
            default -> PlayerInventory.EMPTY;
        };
    }

    /** [SPEAR-MOB][A] 창의 {@code ATTACK_RANGE}(최소 2 · 최대 4.5 · 창작 2 · 6.5 · 여유 0.125 · 몹 배율 0.5)의 몹 몫. */
    public static final float SPEAR_MOB_ATTACK_RANGE_FACTOR = 0.5f;

    /**
     * [SPEAR-MOB][A] {@code Mob.isWithinMeleeAttackRange}(창): 몹 상자를 수평으로 {@code 4.5·0.5} 부풀린 상자가 대상 상자와
     * 겹치고, {@code 2·0.5} 부풀린 상자와는 겹치지 않는다({@code AABB.intersects} 는 엄격 부등호). 탈것 합집합은 두지 않는다.
     */
    public static boolean mobSpearMeleeReach(double x, double y, double z, double width, double height,
            double targetX, double targetY, double targetZ, double targetWidth, double targetHeight) {
        double max = (double) ((float) SPEAR_REACH * SPEAR_MOB_ATTACK_RANGE_FACTOR);
        double min = (double) ((float) SPEAR_MIN_REACH * SPEAR_MOB_ATTACK_RANGE_FACTOR);
        return boxesIntersect(x, y, z, width, height, max, targetX, targetY, targetZ, targetWidth, targetHeight)
                && !boxesIntersect(x, y, z, width, height, min, targetX, targetY, targetZ, targetWidth, targetHeight);
    }

    private static boolean boxesIntersect(double x, double y, double z, double width, double height, double inflate,
            double tx, double ty, double tz, double tw, double th) {
        double half = width / 2.0 + inflate;
        double targetHalf = tw / 2.0;
        return x - half < tx + targetHalf && x + half > tx - targetHalf
                && y < ty + th && y + height > ty
                && z - half < tz + targetHalf && z + half > tz - targetHalf;
    }

    /** [SPEAR-MOB][A] {@code Zombie.populateDefaultEquipmentSlots}: 어려움이면 {@code nextFloat() < 0.05f}, 아니면 0.01f. */
    public static final float ZOMBIE_WEAPON_CHANCE_HARD = 0.05f;
    public static final float ZOMBIE_WEAPON_CHANCE = 0.01f;

    /**
     * [SPEAR-MOB] 좀비 계열(좀비 · 새끼 좀비 · 허스크 · 좀비 주민)의 자연 주손: 핀 26.3 jar
     * {@code Zombie.populateDefaultEquipmentSlots} 가 {@code nextFloat() < (어려움 ? 0.05f : 0.01f)} 이면
     * {@code nextInt(6)} 으로 0 철 검 · 1 철 창 · 나머지 철 삽을 준다. 두 굴림은 {@link #spearArmed} 와 같은 이유로 난수 대신
     * 몹 id 해시(서로 다른 소금)로 굴린다 — 확률은 jar 와 같고 draw 원천만 다르다(등급 B). 정적판
     * {@code standaloneZombieSpawnWeapon} 과 비트 단위로 같다.
     */
    public static short zombieSpawnWeapon(long mobId, Difficulty difficulty) {
        float chance = difficulty == Difficulty.HARD ? ZOMBIE_WEAPON_CHANCE_HARD : ZOMBIE_WEAPON_CHANCE;
        if (zombieWeaponRollFloat(mobId) >= chance) return PlayerInventory.EMPTY;
        int pick = zombieWeaponPick(mobId);
        return pick == 0 ? PlayerInventory.IRON_SWORD
                : pick == 1 ? PlayerInventory.IRON_SPEAR : PlayerInventory.IRON_SHOVEL;
    }

    /** {@code nextFloat()} 자리: 몹 id 해시의 위 24 비트 × 2⁻²⁴(0 이상 1 미만 float). */
    public static float zombieWeaponRollFloat(long mobId) {
        return (mix32((int) mobId ^ (int) (mobId >>> 32) ^ 0x5bd1e995) >>> 8) * 5.9604645E-8f;
    }

    /** {@code nextInt(6)} 자리: 다른 소금의 몹 id 해시를 6 으로 나눈 나머지. */
    public static int zombieWeaponPick(long mobId) {
        return Math.floorMod(mix32((int) mobId ^ (int) (mobId >>> 32) ^ 0x27d4eb2f), 6);
    }

    private static int mix32(int bits) {
        bits ^= bits >>> 16;
        bits *= 0x7feb352d;
        bits ^= bits >>> 15;
        bits *= 0x846ca68b;
        bits ^= bits >>> 16;
        return bits;
    }

    /**
     * [SPEAR][C] 자연 스폰 개체가 창을 드는 확률.
     *
     * <p><b>핀이 스폰 확률을 주지 않는다</b> — §5 는 "어떤 종이 드는가" 와 "죽을 때 8.5% 로
     * 떨어진다" 만 적었고 "몇 %가 들고 나오는가" 는 없다. 없는 수치를 지어내는 대신 이
     * 저장소가 이미 가진 가장 가까운 바닐라 근거를 빌린다: [A] 1.21.4 {@code Zombie
     * .populateDefaultEquipmentSlots} 는 hard 난이도에서 {@code random.nextFloat() < 0.05F}
     * 로 주손 장비를 굴린다. 창은 그 굴림이 내주던 무기 자리를 대신하므로 같은 0.05 를 쓴다.
     *
     * <p>등급 <b>C</b>(자체 계약)이며, 원문 수치가 확보되면 이 상수만 갈아 끼우면 된다.
     * 정수 비교로만 쓰이므로 아래 {@link #SPEAR_SPAWN_ROLL_LIMIT} 가 실제 판정값이다.
     */
    public static final float SPEAR_SPAWN_CHANCE = 0.05f;

    /** {@link #SPEAR_SPAWN_CHANCE} 를 0..99 굴림으로 옮긴 상한. roll &lt; 5 면 창을 든다. */
    public static final int SPEAR_SPAWN_ROLL_LIMIT = (int) (SPEAR_SPAWN_CHANCE * 100.0f);

    /**
     * [SPEAR] 이 개체가 창을 들고 스폰하는가 — <b>난수를 한 칸도 소비하지 않는</b> 결정론적
     * 판정이다.
     *
     * <p>기존 스폰 굴림 수열에 draw 를 하나라도 끼워 넣으면 그 뒤의 모든 판정(변형·장비·
     * 무리 크기)이 같은 시드에서 다른 답을 내 자연 스폰 파리티가 통째로 깨진다. 그래서 이
     * 트랙은 {@code MobRandom} 을 쓰지 않고 <b>몹 id 해시</b>로만 굴린다 — 이미 같은 이유로
     * 같은 방식을 쓰는 정적판 {@code standaloneDemolisherPickaxe} 의 선례를 그대로 따른다.
     * 월드 시드를 섞지 않는 이유는 이 판정이 두 권위에서 <b>같은 몹 id 에 같은 답</b>을
     * 내야 하는데, Java {@code Mob} 에는 월드 시드가 없기 때문이다.
     *
     * <p>정적판 {@code standaloneSpearArmed} 와 <b>비트 단위로 같은 함수</b>여야 한다.
     */
    public static boolean spearArmed(long mobId) {
        return spearSpawnRoll(mobId) < SPEAR_SPAWN_ROLL_LIMIT;
    }

    /** 몹 id → 0..99 굴림. 정적판 사본과 같은 mix32 다. */
    public static int spearSpawnRoll(long mobId) {
        int bits = (int) mobId ^ (int) (mobId >>> 32);
        bits ^= bits >>> 16;
        bits *= 0x7feb352d;
        bits ^= bits >>> 15;
        bits *= 0x846ca68b;
        bits ^= bits >>> 16;
        return Math.floorMod(bits, 100);
    }

    /**
     * [SPEAR] 이 개체가 자연 스폰 때 실제로 손에 쥐는 창. 창을 들지 않으면
     * {@link PlayerInventory#EMPTY} 다 — 호출부는 그때 기존 무장(금 검 등)을 그대로 쓴다.
     */
    public static short spawnHeldSpear(com.gameexpert.engine.mob.MobType type, long mobId) {
        short spear = naturalSpearFor(type);
        return spear != PlayerInventory.EMPTY && spearArmed(mobId)
                ? spear : PlayerInventory.EMPTY;
    }
}
