package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.TurtleEggRules;
import com.gameexpert.terrain.Blocks;

/**
 * [TURTLE] 좀비류의 거북 알 사냥 goal.
 *
 * <p>[A] {@code Zombie.registerGoals} 가 우선순위 4 에 거는
 * {@code ZombieAttackTurtleEggGoal extends RemoveBlockGoal extends MoveToBlockGoal} 의 이식이다.
 * 하위 종은 {@code addBehaviourGoals} 만 재정의하고 {@code registerGoals} 를 물려받으므로
 * {@link #hunts(MobType)} 의 여섯 종이 곧 [A] 의 대상 집합이다.
 *
 * <p>수치는 전부 {@link TurtleEggRules} 의 {@code HUNT_*} 절이 소유하고, 정적판
 * {@code StandaloneMobRuntime.tickTurtleEggHunt} 가 같은 표를 읽어 같은 판단을 낸다.
 *
 * <p><b>이 저장소의 갈래([C])</b> — 경로 탐색이 없다:
 * <ul>
 *   <li>[A] 는 {@code getMoveToTarget() = blockPos.above()} 중심까지의 3차원 거리로 도달을
 *       판정한다(알 <b>위에</b> 올라선 좀비의 발 높이가 그 중심에서 0.5). 이 저장소의 거북 알은
 *       충돌 블록이라 몹이 옆 칸에 서므로, 도달은 [A] {@code RemoveBlockGoal.getPosWithBlock}
 *       이 이미 갖고 있는 "내 자리·아래·동서남북·두 칸 아래" 이웃 판정과
 *       수평 {@code acceptedDistance} 로 읽는다.</li>
 *   <li>[A] 가 도달 뒤 매 틱 넣는 {@code setDeltaMovement(x, 0.3, z)} 도약은 싣지 않는다 —
 *       두 권위의 수직 속도 규약이 서로 달라(정적판 물리는 이동 전 0.28 을 뺀다) 같은 상수를
 *       그대로 넣으면 오히려 갈라진다. 결과(알 제거)는 도약에 의존하지 않는다.</li>
 *   <li>실패한 탐색의 재시도 대기는 난수 대신 고정값이다({@code HUNT_SCAN_INTERVAL_TICKS} 절).</li>
 * </ul>
 */
public final class TurtleEggHunt {

    /** 이번 틱 goal 이 몹을 소유했는가. 거짓이면 호출부는 평소의 배회로 떨어진다. */
    private boolean active;
    /** 소유한 틱이 파괴 단계인가(참이면 이동 0, 거짓이면 알 칸으로 걸어간다). */
    private boolean breaking;

    private boolean tracking;
    private int eggX;
    private int eggY;
    private int eggZ;
    private int breakTicks;
    private int tryTicks;
    private int scanCooldown;

    /**
     * [A] 이 goal 을 갖는 종. {@code Zombie.registerGoals} 를 물려받는 좀비 하위 종 전부이며,
     * 이 저장소가 좀비의 새끼를 별도 종으로 쪼갠 {@code BABY_ZOMBIE} 도 같은 좀비다.
     *
     * <p>{@code ZOMBIE_PIGMAN}·{@code PIGMAN}·좀비 동물 계열은 이 저장소가 더한 [B] 종이라
     * 바닐라 goal 집합이 없다 — 알 사냥도 하지 않는다.
     */
    public static boolean hunts(MobType type) {
        return switch (type) {
            case ZOMBIE, BABY_ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, ZOMBIFIED_PIGLIN -> true;
            default -> false;
        };
    }

    /** 이번 틱 goal 이 이동·행동을 소유했는가. {@link #tick} 뒤에만 의미가 있다. */
    public boolean active() { return active; }

    /** 소유한 틱이 파괴 단계인가. 참이면 호출부는 수평 이동을 0 으로 둔다. */
    public boolean breaking() { return breaking; }

    /** 향할 알 칸 중심의 x(수평). {@link #active()} 이며 파괴 단계가 아닐 때만 의미가 있다. */
    public double targetX() { return eggX + 0.5; }

    /** 향할 알 칸 중심의 z(수평). */
    public double targetZ() { return eggZ + 0.5; }

    /** 진행 중인 파괴 누적(권위 틱). 테스트가 진행을 들여다보는 유일한 창이다. */
    public int breakTicks() { return breakTicks; }

    /**
     * 한 틱을 굴린다. 표적이 없는 좀비류만 부른다([A] 우선순위 4 는 공격 goal(2) 아래다).
     * 돌려주는 목록은 이번 틱에 실을 사건이며 대개 비어 있다.
     */
    public List<MobEvent> tick(Mob mob, MobWorldView world) {
        active = false;
        breaking = false;
        // [A] canDestroyEgg — 좀비는 면제 종이 아니므로 mobGriefing 하나만 본다.
        if (!TurtleEggRules.canDestroy(false, false, TurtleEggRules.MOB_GRIEFING)) {
            return abandon();
        }
        int mobX = (int) Math.floor(mob.x);
        int mobY = (int) Math.floor(mob.y);
        int mobZ = (int) Math.floor(mob.z);
        if (!tracking) {
            if (scanCooldown > 0) {
                scanCooldown--;
                return List.of();
            }
            int[] found = findNearestEgg(world, mobX, mobY, mobZ);
            if (found == null) {
                scanCooldown = TurtleEggRules.HUNT_SCAN_INTERVAL_TICKS;
                return List.of();
            }
            eggX = found[0];
            eggY = found[1];
            eggZ = found[2];
            tracking = true;
            breakTicks = 0;
            tryTicks = 0;
        }
        // [A] canContinueToUse 의 isValidTarget — 알이 사라졌으면 goal 도 끝난다.
        if (!isEgg(world, eggX, eggY, eggZ)) return abandon();
        int[] reached = posWithBlock(world, mobX, mobY, mobZ);
        double dx = targetX() - mob.x;
        double dz = targetZ() - mob.z;
        boolean withinAccepted = dx * dx + dz * dz
                < TurtleEggRules.HUNT_ACCEPTED_DISTANCE * TurtleEggRules.HUNT_ACCEPTED_DISTANCE;
        if (reached == null || !withinAccepted) {
            // [A] tryTicks 는 도달하지 못한 틱마다 오르고 1200 MC 틱에서 goal 을 놓는다.
            tryTicks++;
            if (tryTicks > TurtleEggRules.HUNT_GIVE_UP_TICKS) return abandon();
            breakTicks = 0;
            active = true;
            return List.of();
        }
        active = true;
        breaking = true;
        List<MobEvent> events = List.of();
        if (breakTicks % TurtleEggRules.HUNT_PROGRESS_SOUND_INTERVAL_TICKS == 0) {
            events = List.of(new MobEvent.Sound("attack"));
        }
        breakTicks++;
        if (breakTicks >= TurtleEggRules.HUNT_BREAK_TICKS) {
            // [A] level.removeBlock(pos, false) — 알 수와 무관하게 칸째 사라진다.
            MobEvent removal = new MobEvent.ChangeBlock(reached[0], reached[1], reached[2],
                    Blocks.TURTLE_EGG, Blocks.AIR);
            events = events.isEmpty() ? List.of(removal) : List.of(events.get(0), removal);
            abandon();
            active = true;
            breaking = true;
        }
        return events;
    }

    /** goal 을 놓는다. 다음 탐색은 고정 대기 뒤에 굴린다. */
    private List<MobEvent> abandon() {
        tracking = false;
        breakTicks = 0;
        tryTicks = 0;
        scanCooldown = TurtleEggRules.HUNT_SCAN_INTERVAL_TICKS;
        return List.of();
    }

    /**
     * [A] {@code RemoveBlockGoal.getPosWithBlock} — 실제로 부술 칸은 goal 이 잡아 둔 좌표가
     * 아니라 <b>몹이 지금 서 있는 자리</b> 기준의 일곱 후보 중 첫 알이다.
     */
    static int[] posWithBlock(MobWorldView world, int x, int y, int z) {
        if (isEgg(world, x, y, z)) return new int[] { x, y, z };
        int[][] candidates = {
            { x, y - 1, z }, { x - 1, y, z }, { x + 1, y, z },
            { x, y, z - 1 }, { x, y, z + 1 }, { x, y - 2, z },
        };
        for (int[] candidate : candidates) {
            if (isEgg(world, candidate[0], candidate[1], candidate[2])) return candidate;
        }
        return null;
    }

    /**
     * [A] {@code MoveToBlockGoal.findNearestBlock} 의 탐색 순서를 그대로 옮긴다 —
     * y 는 {@code k - 1} 이고 k 수열은 0,1,-1,2,-2,3,-3 이라(마지막 -3 도 {@code k <= 3} 을
     * 통과한다) 실제 창은 <b>아래 4칸·위 2칸</b>이다. 수평은 반경 0..23 의 정사각 테두리다.
     * 첫 알에서 멈추므로 "바닐라가 고르는 그 칸"이 나온다.
     */
    static int[] findNearestEgg(MobWorldView world, int originX, int originY, int originZ) {
        int vertical = TurtleEggRules.HUNT_VERTICAL_RANGE;
        int range = TurtleEggRules.HUNT_SEARCH_RANGE;
        for (int k = 0; k <= vertical; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < range; l++) {
                for (int i = 0; i <= l; i = i > 0 ? -i : 1 - i) {
                    for (int j = i < l && i > -l ? l : 0; j <= l; j = j > 0 ? -j : 1 - j) {
                        int x = originX + i;
                        int y = originY + k - 1;
                        int z = originZ + j;
                        if (isEgg(world, x, y, z)) return new int[] { x, y, z };
                    }
                }
            }
        }
        return null;
    }

    private static boolean isEgg(MobWorldView world, int x, int y, int z) {
        return (world.getBlock(x, y, z) & 0xffff) == Blocks.TURTLE_EGG;
    }
}
