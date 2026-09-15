package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.WorldClock;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** 드라운드: 물속 3차원 추적, 야간 상륙 추적, 일부 개체의 투사체 공격을 담당한다. */
public final class Drowned extends MeleeMob {
    /**
     * [TRIDENT][B] 던진 삼지창의 피해. 플레이어 투척과 <b>같은 값</b>이라 상수는
     * {@link com.gameexpert.engine.CombatRules#TRIDENT_PROJECTILE_DAMAGE} 하나만 둔다.
     */
    private static final int TRIDENT_DAMAGE =
            com.gameexpert.engine.CombatRules.TRIDENT_PROJECTILE_DAMAGE;
    private static final int WATER_SEARCH_RADIUS = 8;
    private static final double DAYLIGHT_LAND_SPEED_FACTOR = 0.5;
    private static final long NO_WATER_POSITION = Long.MIN_VALUE;

    /** [TRIDENT][B] 보조손 앵무조개 껍데기 스폰 확률(자바판 3%). */
    static final float NAUTILUS_SHELL_SPAWN_CHANCE = 0.03f;

    /**
     * [TRIDENT][B] 삼지창 투척 사거리 <b>20 블록</b>. 예전에는 {@code Skeleton.SHOOT_RANGE}(15)
     * 를 빌려 썼는데, 바닐라 드라운드는 "sending it up to 20 blocks away" 라 스켈레톤과
     * 사거리가 다르다([B] minecraft.wiki «Drowned» §Combat, 조회 2026-08-10).
     * 스켈레톤 상수를 계속 빌리면 스켈레톤 밸런스를 건드릴 때 드라운드가 딸려 움직인다.
     */
    static final double TRIDENT_THROW_RANGE = 20.0;
    /**
     * [TRIDENT][B] 삼지창 투척 간격. 바닐라는 <b>1.5초마다</b> 한 번이다. 이 저장소의 월드
     * 틱은 10 TPS 이므로 1.5 × 10 = <b>15 틱</b>이다({@code Skeleton.SHOOT_INTERVAL} 20 틱
     * = 2.0 초와 다르다 — 빌려 쓰던 값이 드라운드에는 너무 느렸다).
     */
    static final int TRIDENT_THROW_INTERVAL = 15;

    private boolean swimming;
    private boolean carrierRolled;
    private boolean tridentCarrier;
    private boolean shellCarrier;
    /** Natural-spawn-only one-shot Zombie Nautilus jockey decision. Legacy/other origins stay settled. */
    private boolean jockeyDecisionArmed;
    private boolean jockeyDecisionSettled = true;
    private boolean jockeyDecisionWinner;
    private int shootTimer;

    enum SpawnEquipment { NONE, TRIDENT, FISHING_ROD }

    public Drowned(long id, double x, double y, double z) {
        super(id, MobType.DROWNED, x, y, z);
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 3; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return false; }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (!carrierRolled) {
            SpawnEquipment equipment = rollSpawnEquipment(rng);
            tridentCarrier = equipment == SpawnEquipment.TRIDENT;
            if (equipment == SpawnEquipment.FISHING_ROD) {
                installGeneratedHeldItem(PlayerInventory.FISHING_ROD);
            } else if (equipment == SpawnEquipment.TRIDENT) {
                // [TRIDENT] 삼지창도 **진짜 손 아이템**이어야 한다. 예전에는 tridentCarrier
                // 불리언만 세우고 heldItem 을 비워 둬서, 낚싯대는 8.5% 로 떨어지는데 삼지창은
                // 어떤 확률로도 떨어지지 않았다(입수 경로 자체가 없었다). 이제 손에 들리므로
                // Mob.naturalEquipmentDrops 의 공용 8.5% + 약탈 레벨당 1% 규칙을 그대로 탄다.
                // 시각은 이미 VISUAL_DROWNED_TRIDENT 가 전용 프롭으로 그리고 공용 손 슬롯을
                // 억제하므로 이중 렌더가 되지 않는다.
                installGeneratedHeldItem(PlayerInventory.TRIDENT);
            }
            // [TRIDENT] 앵무조개 껍데기 보조손 굴림은 **기존 두 굴림 뒤에만** 붙인다 —
            // 위 rollSpawnEquipment 의 draw 순서를 한 칸도 건드리지 않아 기존 시드의
            // 삼지창/낚싯대 판정이 그대로 재현된다.
            shellCarrier = rollNautilusShell(rng);
            carrierRolled = true;
        }
        swimming = bodyTouchesWater(world);
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        if (attackCd > 0) attackCd--;
        if (shootTimer > 0) shootTimer--;

        double dx = 0.0, dy = 0.0, dz = 0.0;
        PlayerSnapshot target = trackedTarget(world, detectRange(), true);
        if (target != null) {
            faceToward(target.x(), target.z());
            if (tridentCarrier && dist3d(target) <= TRIDENT_THROW_RANGE
                    && shootTimer == 0 && canSeeTargetNow(world, target)) {
                state = MobState.ATTACK;
                MobEvent.ShootArrow shot = aimTridentAt(target);
                if (shot != null) events = appendEvent(events, shot);
                shootTimer = TRIDENT_THROW_INTERVAL;
            } else if (dist3d(target) <= attackRange() && canSeeTargetNow(world, target)) {
                state = MobState.ATTACK;
                if (attackCd == 0) {
                    events = appendEvent(events, new MobEvent.AttackPlayer(
                            target.nickname(), contactDamage(world, attackDamage(rng)), x, z));
                }
            } else {
                state = MobState.CHASE;
                if (swimming) {
                    double tx = target.x() - x;
                    double ty = target.y() - y;
                    double tz = target.z() - z;
                    double distance = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    if (distance > 1e-9) {
                        double scale = preparedMovementSpeed(moveSpeed()) / distance;
                        dx = tx * scale; dy = ty * scale; dz = tz * scale;
                    }
                } else {
                    boolean night = WorldClock.isNight(world.worldTime());
                    boolean targetNearWater = targetNearWater(world, target);
                    long water = night || targetNearWater
                            ? NO_WATER_POSITION : nearestWater(world);
                    double tx = water == NO_WATER_POSITION
                            ? target.x() : unpackWaterX(water) + 0.5;
                    double tz = water == NO_WATER_POSITION
                            ? target.z() : unpackWaterZ(water) + 0.5;
                    double speed = night || targetNearWater
                            ? moveSpeed() : moveSpeed() * DAYLIGHT_LAND_SPEED_FACTOR;
                    double[] movement = towardHoriz(tx, tz, speed);
                    dx = movement[0]; dz = movement[1];
                }
            }
        } else {
            boolean seekWater = !swimming && !WorldClock.isNight(world.worldTime());
            long water = seekWater ? nearestWater(world) : NO_WATER_POSITION;
            // [TURTLE] 드라운드도 좀비의 registerGoals 를 물려받아 알 사냥 goal 을 갖는다.
            // 다만 물 찾기(DrownedGoToWaterGoal, 우선순위 1)가 알 사냥(4)보다 위이므로 물을
            // 찾은 틱에는 goal 을 굴리지 않는다 — 배회(RandomStrollGoal, 7)만 밀어낸다.
            boolean hunting = false;
            if (water == NO_WATER_POSITION) {
                events = tickTurtleEggHunt(world, events);
                hunting = turtleEggHuntActive();
            }
            if (hunting) {
                TurtleEggHunt hunt = turtleEggHunt();
                if (hunt.breaking()) {
                    faceToward(hunt.targetX(), hunt.targetZ());
                    state = MobState.ATTACK;
                } else {
                    double[] movement = towardHoriz(hunt.targetX(), hunt.targetZ(), moveSpeed());
                    dx = movement[0];
                    dz = movement[1];
                    state = MobState.CHASE;
                }
            } else {
                double[] movement = water == NO_WATER_POSITION
                        ? wander(rng, seekWater ? moveSpeed() * DAYLIGHT_LAND_SPEED_FACTOR : moveSpeed())
                        : towardHoriz(unpackWaterX(water) + 0.5, unpackWaterZ(water) + 0.5,
                                moveSpeed() * DAYLIGHT_LAND_SPEED_FACTOR);
                dx = movement[0];
                dz = movement[1];
                state = water != NO_WATER_POSITION ? MobState.CHASE
                        : (dx != 0.0 || dz != 0.0) ? MobState.WANDER : MobState.IDLE;
            }
        }

        MobPhysics.tickMove(this, world, dx, dy, dz, swimming ? MoveMode.FLY : MoveMode.WALK);
        return events;
    }

    static SpawnEquipment rollSpawnEquipment(MobRandom rng) {
        if (rng.nextFloat() <= 0.9f) return SpawnEquipment.NONE;
        return rng.nextInt(16) < 10 ? SpawnEquipment.TRIDENT : SpawnEquipment.FISHING_ROD;
    }

    /**
     * 보조손 앵무조개 껍데기 굴림. 바닐라 Java 확률은 <b>3%</b> 다([B] minecraft.wiki
     * «Drowned» — "Each drowned has a 3%{{only|java}}/8%{{only|bedrock}} chance to spawn with a
     * nautilus shell", 조회 2026-08-10). 자바판을 따르므로 8% 가 아니라 3% 다.
     *
     * <p>삼지창/낚싯대 굴림과 <b>독립</b>이다 — 바닐라도 주손과 보조손을 따로 굴린다.
     */
    static boolean rollNautilusShell(MobRandom rng) {
        return rng.nextFloat() < NAUTILUS_SHELL_SPAWN_CHANCE;
    }

    private MobEvent.ShootArrow aimTridentAt(PlayerSnapshot target) {
        double sx = x, sy = y + eyeHeight(), sz = z;
        double[] velocity = Ballistics.solve(sx, sy, sz,
                target.x(), target.y() + 0.9, target.z(),
                Skeleton.ARROW_SPEED, ProjectileSim.GRAVITY);
        if (velocity == null) return null;
        return new MobEvent.ShootArrow(ProjectileSim.Kind.TRIDENT, sx, sy, sz,
                velocity[0], velocity[1], velocity[2], TRIDENT_DAMAGE);
    }

    private long nearestWater(MobWorldView world) {
        int ox = (int) Math.floor(x), oy = (int) Math.floor(y), oz = (int) Math.floor(z);
        for (int radius = 1; radius <= WATER_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                long north = waterAt(world, ox + dx, oy, oz - radius);
                if (north != NO_WATER_POSITION) return north;
                long south = waterAt(world, ox + dx, oy, oz + radius);
                if (south != NO_WATER_POSITION) return south;
            }
            for (int dz = -radius + 1; dz < radius; dz++) {
                long west = waterAt(world, ox - radius, oy, oz + dz);
                if (west != NO_WATER_POSITION) return west;
                long east = waterAt(world, ox + radius, oy, oz + dz);
                if (east != NO_WATER_POSITION) return east;
            }
        }
        return NO_WATER_POSITION;
    }

    private boolean targetNearWater(MobWorldView world, PlayerSnapshot target) {
        int tx = (int) Math.floor(target.x());
        int ty = (int) Math.floor(target.y());
        int tz = (int) Math.floor(target.z());
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if ((dx != 0 || dz != 0)
                        && isWater(world.getBlock(tx + dx, ty, tz + dz))) return true;
        return false;
    }

    @Override
    protected boolean canTargetPlayer(MobWorldView world, PlayerSnapshot target) {
        return WorldClock.isNight(world.worldTime()) || targetTouchesWater(world, target);
    }

    private boolean targetTouchesWater(MobWorldView world, PlayerSnapshot target) {
        int tx = (int) Math.floor(target.x());
        int ty = (int) Math.floor(target.y() + 0.01);
        int tz = (int) Math.floor(target.z());
        return isWater(world.getBlock(tx, ty, tz))
                || isWater(world.getBlock(tx, ty + 1, tz));
    }

    private static long waterAt(MobWorldView world, int x, int y, int z) {
        return isWater(world.getBlock(x, y, z))
                ? ((long) x << 32) | (z & 0xffff_ffffL)
                : NO_WATER_POSITION;
    }

    private static int unpackWaterX(long position) { return (int) (position >> 32); }
    private static int unpackWaterZ(long position) { return (int) position; }

    private static boolean isWater(short block) {
        return block >= Blocks.WATER_SOURCE && block < Blocks.LAVA_SOURCE;
    }

    @Override public String movementMedium() { return swimming ? "swim" : "land"; }

    @Override public int visualFlags() {
        return tridentCarrier ? Mob.VISUAL_DROWNED_TRIDENT : 0;
    }

    boolean carrierRolled() {
        return carrierRolled;
    }

    boolean tridentCarrier() {
        return tridentCarrier;
    }

    void armJockeyDecision() {
        if (jockeyDecisionArmed || !jockeyDecisionSettled || isBaby()) return;
        jockeyDecisionArmed = true;
        jockeyDecisionSettled = false;
        jockeyDecisionWinner = false;
    }

    boolean jockeyDecisionArmed() { return jockeyDecisionArmed; }
    boolean jockeyDecisionSettled() { return jockeyDecisionSettled; }
    boolean jockeyDecisionWinner() { return jockeyDecisionWinner; }

    void settleJockeyDecision(boolean winner) {
        if (!jockeyDecisionArmed || jockeyDecisionSettled || !carrierRolled) {
            throw new IllegalStateException("Drowned jockey decision is not pending");
        }
        if (winner && !tridentCarrier) {
            throw new IllegalArgumentException("only a trident Drowned can win the jockey roll");
        }
        jockeyDecisionSettled = true;
        jockeyDecisionWinner = winner;
    }

    void restoreJockeyDecision(boolean armed, boolean settled, boolean winner) {
        if ((!armed && (!settled || winner)) || (!settled && winner)
                || (winner && !tridentCarrier)) {
            throw new IllegalArgumentException("invalid persisted Drowned jockey decision");
        }
        jockeyDecisionArmed = armed;
        jockeyDecisionSettled = settled;
        jockeyDecisionWinner = winner;
    }

    /**
     * 보조손에 앵무조개 껍데기를 들었는가. 바닐라에서 이 개체는 처치 시 껍데기를
     * <b>100%</b> 떨어뜨린다([B] «Drowned» — "A drowned holding a nautilus shell always
     * drops it"). 삼지창·낚싯대의 8.5% 장비 드랍과 달리 확률 굴림이 없다.
     */
    public boolean shellCarrier() {
        return shellCarrier;
    }

    void restoreCarrierState(boolean rolled, boolean trident, boolean shell) {
        if (!rolled && (trident || shell)) {
            throw new IllegalStateException("unrolled Drowned cannot carry equipment");
        }
        carrierRolled = rolled;
        tridentCarrier = trident;
        shellCarrier = shell;
    }
}
