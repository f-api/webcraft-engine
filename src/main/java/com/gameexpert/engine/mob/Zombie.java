package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.terrain.Blocks;

/**
 * 좀비: 35블록 내 추적(0.23/틱), 1.5블록 내 공격 피해 3, 쿨다운 10틱. HP 20·방어도 2.
 *
 * <p>hard 난이도에서는 바닐라 {@code BreakDoorGoal} 근사로 앞을 막은 나무 문을 두드려 부순다.
 */
public final class Zombie extends MeleeMob {

    /**
     * 문 파괴까지 걸리는 서버 틱. 바닐라 {@code BreakDoorGoal} 의 240 MC 틱(12초)을 10 TPS 로 환산한 값.
     */
    public static final int DOOR_BREAK_TICKS = 120;
    /** 두드림 사운드 주기(서버 틱). 바닐라의 20 MC 틱(1초)마다 나는 문 두드리는 소리에 대응한다. */
    public static final int DOOR_KNOCK_INTERVAL_TICKS = 10;
    /** 진행 방향으로 문을 찾는 탐침 거리(블록). 좀비 앞면과 한 칸 앞을 본다. */
    private static final double[] DOOR_PROBE_DISTANCES = { 0.6, 1.2 };
    /**
     * 문 너머 플레이어를 감지해 두드리기를 시작하는 반경(블록). 바닐라 {@code DoorInteractGoal} 은 표적이
     * 아니라 경로가 문을 지나는지를 보지만, 이 엔진에는 경로 탐색이 없으므로 문 반대편의 가까운
     * 플레이어를 시작 조건으로 쓴다. 시야(LOS)는 문이 막고 있으므로 요구하지 않는다.
     */
    private static final double DOOR_TRIGGER_RANGE = 8.0;

    /** 현재 두드리는 문의 아랫칸. doorBreakTicks 가 0이면 의미 없다. */
    private int doorX;
    private int doorY;
    private int doorZ;
    private int doorBreakTicks;

    public Zombie(long id, double x, double y, double z) {
        super(id, MobType.ZOMBIE, x, y, z);
        // [SPEAR-MOB] Zombie.populateDefaultEquipmentSlots 는 난이도를 보므로 첫 틱에 굴린다(Mob.resolvePendingSpawnWeapon).
        markSpawnWeaponPending();
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 3; }
    /** [SPEAR-MOB] {@code Zombie.addBehaviourGoals} 의 {@code SpearUseGoal}(창을 쥐면 돌진한다). */
    @Override public boolean spearUseEligible() { return true; }
    /** [SPEAR-MOB] {@code ATTACK_DAMAGE} 속성 기본값(3). */
    @Override public double attackDamageAttributeBase() { return 3.0; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return false; }

    /** 현재까지 누적한 문 파괴 진행(0.0~1.0). 두드리는 중이 아니면 0. */
    public double doorBreakProgress() {
        return Math.min(1.0, doorBreakTicks / (double) DOOR_BREAK_TICKS);
    }

    /**
     * 앞을 막은 닫힌 나무 문을 hard 난이도에서만 두드린다. 두드리는 동안은 제자리에 서고
     * (이동·공격 없음), 주기마다 두드림 사운드를 낸다. 완료 시 위·아래 두 칸을 함께 없앤다.
     *
     * <p>한 번 시작한 두드리기는 바닐라 {@code BreakDoorGoal} 처럼 문이 닫혀 있는 한 이어간다.
     * 문이 시야를 막아 표적 기억(30틱)이 파괴 시간(120틱)보다 짧기 때문이다.
     */
    @Override
    protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        if (!world.difficulty().zombiesBreakDoors()) {
            doorBreakTicks = 0;
            return TargetInterception.NONE;
        }
        // 사거리 안의 플레이어가 있으면 문보다 그쪽이 우선이다(진행은 유지한다).
        if (nearestPlayerWithin(world, attackRange()) != null) return TargetInterception.NONE;
        List<MobEvent> events = List.of();
        if (doorBreakTicks > 0 && isClosedDoor(world, doorX, doorY, doorZ)) {
            // 진행 중인 문은 표적을 다시 보지 않아도 계속 두드린다.
        } else {
            PlayerSnapshot nearby = nearestPlayerWithin(world, DOOR_TRIGGER_RANGE);
            int[] door = nearby == null ? null : closedDoorToward(world, nearby);
            if (door == null) {
                doorBreakTicks = 0;
                return TargetInterception.NONE;
            }
            doorX = door[0];
            doorY = door[1];
            doorZ = door[2];
            doorBreakTicks = 0;
        }
        faceToward(doorX + 0.5, doorZ + 0.5);
        state = MobState.ATTACK;
        doorBreakTicks++;
        if ((doorBreakTicks - 1) % DOOR_KNOCK_INTERVAL_TICKS == 0) {
            events = appendEvent(events, new MobEvent.Sound("attack"));
        }
        if (doorBreakTicks >= DOOR_BREAK_TICKS) {
            events = appendEvent(events, new MobEvent.ChangeBlock(doorX, doorY, doorZ,
                    Blocks.DOOR_CLOSED, Blocks.AIR));
            events = appendEvent(events, new MobEvent.ChangeBlock(doorX, doorY + 1, doorZ,
                    Blocks.DOOR_CLOSED, Blocks.AIR));
            doorBreakTicks = 0;
        }
        return TargetInterception.handled(events);
    }

    /** 반경 안에서 가장 가까운 생존 플레이어. 시야는 보지 않는다(문이 막고 있어도 감지). */
    private PlayerSnapshot nearestPlayerWithin(MobWorldView world, double range) {
        PlayerSnapshot best = null;
        double bestSq = range * range;
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive()) continue;
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq <= bestSq) {
                bestSq = distanceSq;
                best = player;
            }
        }
        return best;
    }

    /** 좌표가 여전히 닫힌 나무 문인가. 열리거나 부서지면 두드리기를 멈춘다. */
    private boolean isClosedDoor(MobWorldView world, int px, int py, int pz) {
        int block = world.getBlock(px, py, pz) & 0xffff;
        if (block != Blocks.DOOR_CLOSED) return false;
        return (world.blockState(px, py, pz, block) & BuildingBlockRules.DOOR_OPEN) == 0;
    }

    /**
     * 표적 방향 바로 앞의 닫힌 나무 문 아랫칸 좌표. 없으면 null.
     * 열린 문은 그대로 지나갈 수 있으므로 대상이 아니다.
     */
    private int[] closedDoorToward(MobWorldView world, PlayerSnapshot target) {
        double dx = target.x() - x;
        double dz = target.z() - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-9) return null;
        double ux = dx / length;
        double uz = dz / length;
        int feetY = (int) Math.floor(y + 0.01);
        for (double probe : DOOR_PROBE_DISTANCES) {
            int px = (int) Math.floor(x + ux * probe);
            int pz = (int) Math.floor(z + uz * probe);
            for (int offsetY = 0; offsetY <= 1; offsetY++) {
                int py = feetY + offsetY;
                if (!isClosedDoor(world, px, py, pz)) continue;
                int block = world.getBlock(px, py, pz) & 0xffff;
                int doorState = world.blockState(px, py, pz, block);
                int lowerY = (doorState & BuildingBlockRules.DOOR_UPPER) != 0 ? py - 1 : py;
                return new int[] { px, lowerY, pz };
            }
        }
        return null;
    }

}
