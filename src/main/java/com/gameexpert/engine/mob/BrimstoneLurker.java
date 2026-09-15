package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.sulfur.PotentSulfurRules;
import com.gameexpert.terrain.Blocks;

/**
 * 황린 잠복자(stableId 98). 유황 동굴 지대 고유 <b>엘리트</b> 몹이다. 수치·근거·divergence 는
 * {@link BrimstoneLurkerRules} 가 소유하고 서술 정본은 {@code docs/MC-REFERENCE.md}
 * 「황린 잠복자」 절이다.
 *
 * <p><b>상태 기계</b>(기획 확정본 그대로)
 * <ol>
 *   <li>{@code LURK} — 유황 지면에 반매몰. 이동하지 않고 연기 파티클만 낸다. 반경
 *       {@link BrimstoneLurkerRules#LURK_ACTIVATION_RADIUS} 안에 플레이어가 들어오면 다음
 *       단계로 넘어간다.</li>
 *   <li>{@code EMERGE} — <b>간헐천 연동</b>. 반경 {@link BrimstoneLurkerRules#GEYSER_LINK_RADIUS}
 *       안에 분출구가 있으면 그 분출구의 <b>다음 분출 틱</b>에 맞춰 상방 발사로 솟는다. 위상은
 *       {@link PotentSulfurRules} 의 순수 함수라 양 권위가 반드시 같은 틱을 고른다. 분출구가
 *       멀면 {@link BrimstoneLurkerRules#EMERGE_WARNING_TICKS} 동안 연기로 예고한 뒤 제자리에서
 *       솟는다.</li>
 *   <li>{@code COMBAT} — 갑각 돌진(텔레그래프 → 돌진 → 벽 충돌 그로기)과 유황 침을 번갈아
 *       쓴다. 둘 다 <b>난수를 소비하지 않는</b> 결정적 판정이라 결정 트레이스의 난수 수열이
 *       흔들리지 않는다(좀비 동물 돌진이 낸 선례).</li>
 *   <li>과열 — {@link BrimstoneLurkerRules#overheated(long)} 가 개체 생존 틱만 보고 판정하는
 *       순수 함수다. 과열 중에는 방어도가 0 이고 접촉한 대상이 점화된다.</li>
 * </ol>
 *
 * <p><b>물 접촉</b>은 이 종의 유일한 환경 약점이다: 이동 −50% 이고 갑각 경화가 풀려 방어도가
 * 0 이 된다({@link BrimstoneLurkerRules#effectiveArmor}). 반대로 <b>화염·용암은 완전 면역</b>
 * 이라 자기 장판과 간헐천 위에서 자유롭다.
 */
public final class BrimstoneLurker extends MeleeMob {

    /** 이 종의 상태. 프로토콜에는 실리지 않고 표현은 서버가 내는 의미 사운드로만 나간다. */
    enum Phase { LURK, EMERGE, COMBAT }

    private Phase phase = Phase.LURK;
    /** 개체가 살아 있는 동안 흐른 권위 틱. 과열 주기의 유일한 입력이다. */
    private long aliveTicks;
    /** 남은 등장 예고 틱(간헐천이 멀 때만 쓴다). */
    private int emergeWarningTicks;
    /** 남은 돌진 텔레그래프 틱. */
    private int telegraphTicks;
    /** 남은 돌진 틱. */
    private int chargeTicks;
    /** 남은 돌진 쿨다운 틱. */
    private int chargeCooldown;
    /** 남은 벽 충돌 그로기 틱. */
    private int groggyTicks;
    /** 남은 유황 침 쿨다운 틱. */
    private int spitCooldown;
    /** 직전 틱의 수평 좌표. 돌진 중 벽 충돌 판정에 쓴다(이동량 0 = 막혔다). */
    private double previousX;
    private double previousZ;

    public BrimstoneLurker(long id, double x, double y, double z) {
        super(id, MobType.BRIMSTONE_LURKER, x, y, z);
        this.previousX = x;
        this.previousZ = z;
    }

    // ── MeleeMob 계약 ───────────────────────────────────────────────────────────────
    @Override protected double detectRange() { return BrimstoneLurkerRules.LURK_ACTIVATION_RADIUS; }
    @Override protected double attackRange() { return BrimstoneLurkerRules.ATTACK_RANGE; }
    @Override protected int attackDamage() { return BrimstoneLurkerRules.MELEE_DAMAGE; }
    @Override protected int attackCooldownTicks() {
        return BrimstoneLurkerRules.ATTACK_COOLDOWN_TICKS;
    }
    @Override protected boolean climbWalls() { return false; }

    /** 화염 접촉 면역. 자기 장판·자기 점화·간헐천에 스스로 죽지 않는다. */
    @Override public boolean fireImmune() { return BrimstoneLurkerRules.FIRE_IMMUNE; }

    /** 용암 면역. 간헐천 하부 마그마 위가 이 종의 기본 자세다. */
    public boolean lavaImmune() { return BrimstoneLurkerRules.LAVA_IMMUNE; }

    /**
     * 지금 유효한 방어도. 과열 중이거나 물에 닿아 있으면 갑각 경화가 풀려 0 이다 —
     * 이 종의 유일한 공략 창이라 판정 정본을 규칙 클래스 한 곳에 둔다.
     */
    @Override
    public double armor() {
        return BrimstoneLurkerRules.effectiveArmor(aliveTicks, touchingWater);
    }

    /** 물에 닿아 있는가. 매 틱 갱신하며 방어도·이동 배율이 함께 읽는다. */
    private boolean touchingWater;

    /** 과열 중인가. 발광 텍스처 스왑과 접촉 점화가 함께 읽는다. */
    public boolean overheated() {
        return BrimstoneLurkerRules.overheated(aliveTicks);
    }

    /** 돌진 중인가. 시각 상태·피해 보정·정적판 대조가 읽는다. */
    public boolean charging() {
        return chargeTicks > 0;
    }

    /** 잠복 중인가. 반매몰 표현과 연기 파티클이 읽는다. */
    public boolean lurking() {
        return phase == Phase.LURK;
    }

    /** 벽에 박아 그로기인가. 반격 창 표현이 읽는다. */
    public boolean groggy() {
        return groggyTicks > 0;
    }

    /** 돌진 중에 닿은 타격만 돌진 피해를 쓴다. 그 외에는 평범한 근접이다. */
    @Override
    protected int attackDamage(MobRandom rng) {
        return charging() ? BrimstoneLurkerRules.CHARGE_HIT_DAMAGE : BrimstoneLurkerRules.MELEE_DAMAGE;
    }

    /** 배회·추격 속도. 돌진 중에는 돌진 속도이며 물에 닿아 있으면 −50% 다. */
    @Override
    protected double moveSpeed() {
        double base = charging()
                ? BrimstoneLurkerRules.CHARGE_BLOCKS_PER_TICK
                : BrimstoneLurkerRules.IDLE_BLOCKS_PER_TICK;
        return BrimstoneLurkerRules.effectiveSpeed(base, touchingWater);
    }

    /**
     * 잠복·등장·그로기·텔레그래프 동안에는 이동과 공격을 모두 멈춘다. 그 판정을 여기서
     * 가로채면 {@link MeleeMob} 의 추격/공격 본체를 한 글자도 고치지 않아도 된다.
     */
    @Override
    protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        aliveTicks++;
        touchingWater = inWater(world);
        if (spitCooldown > 0) spitCooldown--;
        if (groggyTicks > 0) {
            groggyTicks--;
            return TargetInterception.HANDLED;
        }
        return switch (phase) {
            case LURK -> tickLurk(world, target);
            case EMERGE -> tickEmerge(world);
            case COMBAT -> tickCombat(world, target);
        };
    }

    // ── ① 잠복 ──────────────────────────────────────────────────────────────────────
    /**
     * 반매몰 대기. 활성 반경 안에 플레이어가 들어오면 등장 절차를 <b>그 자리에서</b> 결정한다
     * — 가까운 분출구가 있으면 그 위상에 올라타고, 없으면 연기 예고를 켠다.
     */
    private TargetInterception tickLurk(MobWorldView world, PlayerSnapshot target) {
        if (target == null || dist3d(target) > BrimstoneLurkerRules.LURK_ACTIVATION_RADIUS) {
            return TargetInterception.HANDLED;
        }
        phase = Phase.EMERGE;
        emergeWarningTicks = nearestGeyser(world) == null
                ? BrimstoneLurkerRules.EMERGE_WARNING_TICKS
                : 0;
        return TargetInterception.handled(List.of(new MobEvent.Sound("brimstone_lurker_wake")));
    }

    // ── ② 등장 ──────────────────────────────────────────────────────────────────────
    /**
     * 간헐천 연동 등장. 분출구가 사거리 안이면 <b>그 분출구가 분출하는 틱</b>에 상방 발사로
     * 솟는다. 분출구가 없으면 예고 틱을 소진한 뒤
     * 제자리에서 솟는다. 어느 쪽도 난수를 쓰지 않는다.
     */
    private TargetInterception tickEmerge(MobWorldView world) {
        int[] vent = nearestGeyser(world);
        if (vent != null) {
            if (!potentSulfurErupting(world, vent)) {
                return TargetInterception.HANDLED;
            }
            x = vent[0] + 0.5;
            z = vent[2] + 0.5;
            y = vent[1] + 1;
        } else if (emergeWarningTicks > 0) {
            emergeWarningTicks--;
            return TargetInterception.HANDLED;
        }
        vy = BrimstoneLurkerRules.ERUPTION_LAUNCH_SPEED;
        phase = Phase.COMBAT;
        return TargetInterception.handled(List.of(new MobEvent.Sound("brimstone_lurker_emerge")));
    }

    // ── ③ 교전 ──────────────────────────────────────────────────────────────────────
    /**
     * 갑각 돌진과 유황 침. 돌진은 텔레그래프 3초 → 돌진 2초 → (벽이면 그로기 2초) → 쿨다운
     * 6초의 결정적 사슬이고, 사슬이 도는 동안 사거리 안이면 유황 침이 별도 쿨다운으로 나간다.
     */
    private TargetInterception tickCombat(MobWorldView world, PlayerSnapshot target) {
        if (target == null) {
            resetCombat();
            return TargetInterception.NONE;
        }
        faceToward(target.x(), target.z());
        if (telegraphTicks > 0) {
            if (--telegraphTicks == 0) chargeTicks = BrimstoneLurkerRules.CHARGE_TICKS;
            return TargetInterception.HANDLED;
        }
        if (chargeTicks > 0) {
            if (hitWall()) {
                chargeTicks = 0;
                chargeCooldown = BrimstoneLurkerRules.CHARGE_COOLDOWN_TICKS;
                groggyTicks = BrimstoneLurkerRules.CHARGE_WALL_GROGGY_TICKS;
                markPosition();
                return TargetInterception.handled(
                        List.of(new MobEvent.Sound("brimstone_lurker_stagger")));
            }
            if (--chargeTicks == 0) chargeCooldown = BrimstoneLurkerRules.CHARGE_COOLDOWN_TICKS;
            markPosition();
            return TargetInterception.NONE;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        } else if (BrimstoneLurkerRules.chargeDistanceOk(dist3d(target))) {
            telegraphTicks = BrimstoneLurkerRules.CHARGE_TELEGRAPH_TICKS;
            markPosition();
            return TargetInterception.handled(
                    List.of(new MobEvent.Sound("brimstone_lurker_telegraph")));
        }
        markPosition();
        MobEvent.ShootArrow spit = trySpit(target);
        return spit == null ? TargetInterception.NONE : TargetInterception.handled(List.of(spit));
    }

    /**
     * 유황 침 한 발. 발사 물리는 <b>라마 침 그대로</b>({@link ProjectileSim.Kind#LLAMA_SPIT})
     * 이므로 궤적 계산이 사본으로 갈라지지 않는다 — 갈리는 것은 피해와 착탄 뒤의 점화 장판뿐이고,
     * 그 장판은 간헐천이 이미 쓰는 접촉 피해 계약을 그대로 받는다.
     */
    private MobEvent.ShootArrow trySpit(PlayerSnapshot target) {
        if (spitCooldown > 0 || dist3d(target) > BrimstoneLurkerRules.SPIT_RANGE) return null;
        spitCooldown = BrimstoneLurkerRules.SPIT_COOLDOWN_TICKS;
        double sx = x;
        double sy = y + eyeHeight();
        double sz = z;
        double[] aim = LlamaRules.spitAim(sx, sy, sz,
                target.x(), target.y(), ProjectileSim.PLAYER_HEIGHT, target.z());
        // 부정확도 0 — 엘리트의 침은 빗나가지 않는다(divergence 2). 라마와 달리 오차 표본을
        // 뽑지 않으므로 이 종을 추가해도 결정 트레이스의 난수 소비 수열이 흔들리지 않는다.
        double[] velocity = LlamaRules.spitVelocity(aim, 0.0, 0.0, 0.0);
        if (velocity == null) return null;
        return new MobEvent.ShootArrow(ProjectileSim.Kind.LLAMA_SPIT, sx, sy, sz,
                velocity[0], velocity[1], velocity[2], BrimstoneLurkerRules.SPIT_DAMAGE);
    }

    // ── 보조 ────────────────────────────────────────────────────────────────────────
    /** 표적을 잃으면 교전 상태를 되돌린다(다음 조우가 깨끗한 상태에서 시작한다). */
    private void resetCombat() {
        telegraphTicks = 0;
        chargeTicks = 0;
        chargeCooldown = 0;
        groggyTicks = 0;
    }

    private void markPosition() {
        previousX = x;
        previousZ = z;
    }

    /**
     * 이번 틱에 벽에 박았는가. 돌진 중인데 수평으로 사실상 움직이지 못했으면 막힌 것이다 —
     * 별도 충돌 질의를 만들지 않고 {@code MobPhysics} 가 이미 낸 결과를 읽는다.
     */
    private boolean hitWall() {
        double moved = Math.abs(x - previousX) + Math.abs(z - previousZ);
        return moved < BrimstoneLurkerRules.CHARGE_BLOCKS_PER_TICK * 0.25;
    }

    /** 발밑·머리 칸 어느 쪽이든 물이면 물 접촉이다. */
    private boolean inWater(MobWorldView world) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int feet = (int) Math.floor(y);
        int head = (int) Math.floor(y + height());
        return isWater(world, bx, feet, bz) || isWater(world, bx, head, bz);
    }

    private static boolean isWater(MobWorldView world, int x, int y, int z) {
        short block = world.getBlock(x, y, z);
        return block >= 0 && Fluids.isWaterMedium(block & 0xffff);
    }

    /**
     * 반경 {@link BrimstoneLurkerRules#GEYSER_LINK_RADIUS} 안에서 가장 가까운 <b>성립한</b>
     * 간헐천 {@code {x, y, z, waterColumn}} 또는 {@code null}. "성립" 판정은 분출구 위 물기둥
     * 1~4칸이며 그 셈의 정본은 {@link PotentSulfurRules} 다 — 환경 틱이
     * 플레이어를 밀어올릴 때 쓰는 것과 같은 함수라 둘이 같은 분출구를 같은 높이로 본다.
     *
     * <p>블록만 읽는 결정적 탐색이라 두 권위가 같은 칸을 고른다(같은 거리는 주사 순서로
     * 갈린다 — dx → dz → dy 오름차순).
     */
    private int[] nearestGeyser(MobWorldView world) {
        int reach = (int) BrimstoneLurkerRules.GEYSER_LINK_RADIUS;
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        int cz = (int) Math.floor(z);
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    short block = world.getBlock(cx + dx, cy + dy, cz + dz);
                    if (block < 0 || (block & 0xffff) != Blocks.POTENT_SULFUR) continue;
                    long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (distance > (long) reach * reach || distance >= bestDistance) continue;
                    int column = potentWaterColumn(world, cx + dx, cy + dy, cz + dz);
                    if (column <= 0) continue;
                    bestDistance = distance;
                    best = new int[] { cx + dx, cy + dy, cz + dz, column };
                }
            }
        }
        return best;
    }

    private static int potentWaterColumn(MobWorldView world, int x, int y, int z) {
        int water = 0;
        while (water < PotentSulfurRules.MAX_WATER_BLOCKS_ABOVE
                && isWaterSource(world, x, y + 1 + water, z)) water++;
        return water;
    }

    private static boolean potentSulfurErupting(MobWorldView world, int[] sulfur) {
        short below = world.getBlock(sulfur[0], sulfur[1] - 1, sulfur[2]);
        if (below < 0) return false;
        int id = below & 0xffff;
        return id == Blocks.LAVA_SOURCE || id == Blocks.MAGMA
                && PotentSulfurRules.periodicErupting(world.worldTick(), sulfur[3],
                        sulfur[0], sulfur[1], sulfur[2]);
    }

    private static boolean isWaterSource(MobWorldView world, int x, int y, int z) {
        short block = world.getBlock(x, y, z);
        return block >= 0 && (block & 0xffff) == Blocks.WATER_SOURCE;
    }
}
