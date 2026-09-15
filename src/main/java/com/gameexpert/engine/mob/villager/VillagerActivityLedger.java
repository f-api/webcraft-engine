package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.mob.villager.VillagerBrainRules.Activity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주민 한 마리 한 마리의 <b>현재 활동</b>과 그 활동이 쓰는 휘발 기억의 정본이다. 일정표
 * ({@link VillagerBrainRules#scheduledActivity})는 이 원장의 바탕일 뿐이고, 직업·사회·gossip·이동
 * lane 은 모두 {@link #activityOf} 가 돌려주는 값을 쓴다.
 *
 * <p>바닐라 근거(26.3-snapshot-7 client jar javap):
 * <ul>
 *   <li>{@code VillagerGoalPackages.getCorePackage}: {@code ReactToBell} — {@code HEARD_BELL_TIME}
 *       이 있고 발밑에 습격({@code ServerLevel.getRaidAt})이 없으면
 *       {@code setActiveActivityIfPossible(HIDE)}. {@code SetRaidStatus} — {@code nextInt(20)==0}
 *       일 때 습격이 있으면 첫 wave 전이거나 wave 사이면 PRE_RAID, 아니면 RAID 를 기본·현재 활동으로.</li>
 *   <li>{@code getHidePackage}: {@code SetHiddenState.create(15, 3)} — 들은 지 300틱
 *       ({@code HIDE_TIMEOUT}) 이 지났거나 숨은 틱이 {@code 15*20} 을 넘으면 두 기억을 지우고
 *       {@code Brain.updateActivityFromSchedule}; 아니면 은신처 3블록({@code BlockPos.closerThan})
 *       안에서 숨은 틱을 센다. {@code LocateHidingPlace.create(32, speed*1.25F, 2)}.</li>
 *   <li>{@code getRaidPackage}: 습격이 진행 중이면 {@code LocateHidingPlace.create(24, speed*1.4F, 1)},
 *       승리했으면 하늘 보이는 곳/마을 배회(speed*1.1F). {@code getPreRaidPackage}: 종 치기와 집합점
 *       걷기(speed*1.5F) 또는 배회. 둘 다 99순위 {@code ResetRaidStatus}: {@code nextInt(20)==0} 일 때
 *       습격이 없거나 중단·패배면 기본 활동을 IDLE 로 되돌리고 일정으로 갱신한다.</li>
 *   <li>{@code LocateHidingPlace}: WALK_TARGET 이 없을 때만. {@code closeEnough+1} 반경의 HOME 중
 *       중심이 {@code closeEnough} 안인 것 → 반경 {@code radius} 의 무작위 HOME → 자기 HOME 기억 순으로
 *       고르고, 은신처로 기억한 뒤 {@code closeEnough} 밖이면 그 칸을 걷기 목표로 둔다.</li>
 *   <li>{@code Brain.updateActivityFromSchedule}: 직전 갱신에서 20틱을 <b>넘겨야</b> 다시 보고,
 *       이미 그 활동이면 그대로 둔다. {@code MoveToTargetSink}: 목표 블록까지 맨해튼 거리가
 *       closeEnough 이하면 WALK_TARGET 을 지운다.</li>
 *   <li>{@code HEARD_BELL_TIME}·{@code HIDING_PLACE} 는 codec 없이 등록된 기억이라
 *       ({@code MemoryModuleType.<clinit>}) 저장되지 않는다. 활동도 저장되지 않고 뇌가 만들어질 때
 *       일정으로 다시 고른다. 그래서 이 원장도 영속하지 않는다.</li>
 * </ul>
 *
 * <p>WebCraft divergence(계약, 두 권위 동일):
 * <ul>
 *   <li>권위 틱 하나는 MC 틱 둘이다. 숨은 틱은 틱마다 2씩 세고, {@code nextInt(20)} 동전은 그 두
 *       MC 틱 각각에 대해 {@link #raidStatusCoin}(mob id·MC 틱의 결정적 해시)으로 던진다.</li>
 *   <li>{@code PoiManager.getRandom} 의 무작위 HOME 은 같은 해시로 후보 서수({@link
 *       VillagerPoiIndex} 순서)에서 뽑는다. HOME 은 WebCraft 의 침대 칸이다.</li>
 *   <li>일정 활동의 기억 요구(WORK 는 JOB_SITE, MEET 는 MEETING_POINT)는 적용하지 않는다 — 집합점
 *       기억이 없어 MEET 가 영영 사라지기 때문이며, 기존 일정 lane 과 같은 판정이다.</li>
 *   <li>{@code RingBell}(PRE_RAID 에서 주민이 집합점 종을 친다)은 MEETING_POINT 기억이 있어야 하므로
 *       WebCraft 에서는 일어나지 않는다. 같은 이유로 PRE_RAID 는 집합점 걷기 없이 배회만 한다.</li>
 * </ul>
 *
 * <p>정적판 사본은 {@code StandaloneVillagerActivityLedger.ts} 이며 같은 순서·같은 수치를 쓴다.
 */
public final class VillagerActivityLedger {

    /** "아직 없음"을 뜻하는 시각. */
    public static final long NEVER = Long.MIN_VALUE;

    /** {@code SetHiddenState.HIDE_TIMEOUT}: 종을 들은 뒤 숨는 최대 MC 틱. */
    public static final int HEARD_BELL_TIMEOUT_TICKS = 300;
    /** {@code SetHiddenState.create(15, 3)} 의 15초 × 20. */
    public static final int HIDE_MAX_HIDDEN_TICKS = 15 * 20;
    /** {@code SetHiddenState.create(15, 3)} 의 3블록. */
    public static final int HIDE_CLOSE_ENOUGH_BLOCKS = 3;
    /** 숨기 묶음 {@code LocateHidingPlace.create(32, speed*1.25F, 2)}. */
    public static final int HIDE_LOCATE_RADIUS_BLOCKS = 32;
    public static final double HIDE_WALK_SPEED_FACTOR = 1.25;
    public static final int HIDE_LOCATE_CLOSE_ENOUGH_BLOCKS = 2;
    /** 습격 묶음 {@code LocateHidingPlace.create(24, speed*1.4F, 1)}. */
    public static final int RAID_LOCATE_RADIUS_BLOCKS = 24;
    public static final double RAID_WALK_SPEED_FACTOR = 1.4;
    public static final int RAID_LOCATE_CLOSE_ENOUGH_BLOCKS = 1;
    /** 습격 전 묶음 {@code VillageBoundRandomStroll.create(speed*1.5F)}. */
    public static final double PRE_RAID_STROLL_FACTOR = 1.5;
    /** 습격 승리 묶음 {@code VillageBoundRandomStroll.create(speed*1.1F)}. */
    public static final double RAID_VICTORY_STROLL_FACTOR = 1.1;
    /** 놀이 일정의 배회 배속(기존 {@code Villager#idleMoveSpeed} 계약). */
    public static final double PLAY_STROLL_FACTOR = 1.15;
    /** {@code SetRaidStatus}/{@code ResetRaidStatus} 의 {@code nextInt(20)}. */
    public static final int RAID_STATUS_COIN_BOUND = 20;
    /** {@code Brain.updateActivityFromSchedule} 의 {@code gameTime - last > 20}. */
    public static final long SCHEDULE_UPDATE_INTERVAL_TICKS = 20L;

    /** 동전 용도 salt. 정적판과 같은 값이다. */
    public static final int COIN_SET_RAID_STATUS = 0x5e7a1d;
    public static final int COIN_RESET_RAID_STATUS = 0x7e5e7d;
    public static final int COIN_RANDOM_HOME = 0x401fe5;

    /** 이 lane 이 보는 주민 하나. */
    public interface ActivityVillager {
        long id();

        double x();

        double y();

        double z();

        boolean baby();

        boolean alive();

        /** 이 주민의 HOME 기억(침대 claim) {@code {x,y,z}} 또는 {@code null}. */
        default int[] homeBed() {
            return null;
        }
    }

    /**
     * 발밑 습격 한 건의 바닐라 판정 표면({@code Raid#hasFirstWaveSpawned}·{@code isBetweenWaves}·
     * {@code isVictory}·{@code isLoss}·{@code isStopped}). {@code null} 은 {@code getRaidAt} 이 없음을
     * 뜻한다.
     */
    public record RaidView(boolean firstWaveSpawned, boolean betweenWaves, boolean victory,
            boolean loss, boolean stopped) {
        /** 바닐라 {@code VillagerGoalPackages.raidExistsAndActive}. */
        public boolean activeFight() {
            return !victory && !loss;
        }
    }

    /** 이 lane 이 보는 월드. */
    public interface ActivityWorld {
        /** 0..11999 월드 시계. 일정은 {@link VillagerSocietyRules#mcDayTime}로 환산한다. */
        long worldTime();

        default long dayCount() {
            return 0L;
        }

        /** {@code ServerLevel.getRaidAt(blockPosition)} 또는 {@code null}. */
        default RaidView raidAt(double x, double y, double z) {
            return null;
        }

        /**
         * 원점 블록에서 유클리드 {@code radius} 안(블록 좌표 {@code distSqr <= radius²})의 HOME 칸을
         * 후보 서수 오름차순으로. 상주 범위를 모르면 {@code null}(그 단계를 건너뛴다).
         */
        default List<int[]> homesWithin(int originX, int originY, int originZ, int radius) {
            return List.of();
        }
    }

    /** 이동이 읽는 한 주민의 결정. */
    public record Snapshot(Activity activity, int[] walkTarget, double walkSpeedFactor,
            double strollFactor) {}

    private static final class State {
        private Activity activity = Activity.IDLE;
        private long lastScheduleUpdate = NEVER;
        private long heardBellTime = NEVER;
        private int[] hidingPlace;
        private int[] walkTarget;
        private int walkCloseEnough;
        private double walkSpeedFactor;
        private int ticksHidden;
        private boolean raidVictory;
        private boolean initialized;
    }

    private final Map<Long, State> states = new HashMap<>();

    /**
     * 주민의 현재 활동. 아직 이 lane 이 한 번도 보지 못한 주민은 뇌가 막 만들어진 바닐라 주민처럼
     * 일정표 값이다.
     *
     * @param dayTime MC 일정 축(0..23999)의 시각
     */
    public Activity activityOf(long mobId, long dayTime, boolean baby) {
        State state = states.get(mobId);
        return state == null || !state.initialized ? fallbackActivity(dayTime, baby)
                : state.activity;
    }

    /** lane 밖(순수 테스트·아직 틱이 돌지 않은 주민)의 활동: 막 만들어진 뇌의 일정 값이다. */
    public static Activity fallbackActivity(long dayTime, boolean baby) {
        return VillagerBrainRules.scheduledActivity(dayTime, baby ? -1 : 0);
    }

    /** 이 주민의 HEARD_BELL_TIME 기억(MC 틱) 또는 {@link #NEVER}. */
    public long heardBellTime(long mobId) {
        State state = states.get(mobId);
        return state == null ? NEVER : state.heardBellTime;
    }

    /** HIDING_PLACE 기억 {@code {x,y,z}} 또는 {@code null}. */
    public int[] hidingPlace(long mobId) {
        State state = states.get(mobId);
        return state == null || state.hidingPlace == null ? null : state.hidingPlace.clone();
    }

    /** 이동 lane 이 읽는 결정. 아직 모르는 주민은 {@code null}. */
    public Snapshot snapshot(long mobId) {
        State state = states.get(mobId);
        if (state == null || !state.initialized) return null;
        return new Snapshot(state.activity,
                state.walkTarget == null ? null : state.walkTarget.clone(),
                state.walkSpeedFactor, strollFactor(state));
    }

    private static double strollFactor(State state) {
        return strollFactor(state.activity, state.raidVictory);
    }

    /**
     * 활동이 허락하는 배회 배속(주민 기본 배속에 곱한다). 0 이면 배회하지 않는다 — REST·HIDE 는
     * 배회 행동이 없고, 진행 중인 RAID 는 은신처 찾기뿐이다. 승리한 RAID 는 마을 배회(1.1배),
     * PRE_RAID 는 1.5배, PLAY 는 기존 1.15배다.
     */
    public static double strollFactor(Activity activity, boolean raidVictory) {
        return switch (activity) {
            case REST, HIDE -> 0.0;
            case RAID -> raidVictory ? RAID_VICTORY_STROLL_FACTOR : 0.0;
            case PRE_RAID -> PRE_RAID_STROLL_FACTOR;
            case PLAY -> PLAY_STROLL_FACTOR;
            case IDLE, WORK, MEET -> 1.0;
        };
    }

    /** 주민이 영구히 사라졌을 때. */
    public void forget(long mobId) {
        states.remove(mobId);
    }

    /**
     * {@code BellBlockEntity#updateEntities} 가 이 주민에게 {@code HEARD_BELL_TIME = gameTime} 을
     * 쓴다. 누가 듣는가(48블록 AABB 목록의 60틱 캐시와 32블록 판정)는 {@link VillagerBellRules}
     * 가 정한다.
     */
    public void hearBell(long mobId, long gameTime) {
        states.computeIfAbsent(mobId, ignored -> new State()).heardBellTime = gameTime;
    }

    /**
     * 한 권위 틱. 활성 청크의 주민만 넘긴다. mob id 오름차순으로 판정해 두 권위가 같은 결론을
     * 낸다.
     */
    public void tick(ActivityWorld world, List<? extends ActivityVillager> villagers) {
        if (villagers == null || villagers.isEmpty()) return;
        List<ActivityVillager> ordered = new ArrayList<>(villagers);
        ordered.sort(Comparator.comparingLong(ActivityVillager::id));
        long gameTime = VillagerSocietyRules.mcGameTime(world.dayCount(), world.worldTime());
        long dayTime = VillagerSocietyRules.mcDayTime(world.worldTime());
        for (ActivityVillager villager : ordered) {
            if (!villager.alive()) {
                forget(villager.id());
                continue;
            }
            step(world, villager, gameTime, dayTime);
        }
    }

    private void step(ActivityWorld world, ActivityVillager villager, long gameTime,
            long dayTime) {
        State state = states.computeIfAbsent(villager.id(), ignored -> new State());
        boolean baby = villager.baby();
        if (!state.initialized) {
            // Villager#registerBrainGoals: 뇌를 만들자마자 일정으로 활동을 고른다.
            state.initialized = true;
            updateActivityFromSchedule(state, gameTime, dayTime, baby);
        }
        int bx = (int) Math.floor(villager.x());
        int by = (int) Math.floor(villager.y());
        int bz = (int) Math.floor(villager.z());
        // MoveToTargetSink(core): 목표 블록까지 맨해튼 거리 ≤ closeEnough 면 도착이다.
        if (state.walkTarget != null
                && Math.abs(state.walkTarget[0] - bx) + Math.abs(state.walkTarget[1] - by)
                        + Math.abs(state.walkTarget[2] - bz) <= state.walkCloseEnough) {
            state.walkTarget = null;
        }
        RaidView raid = world.raidAt(villager.x(), villager.y(), villager.z());
        state.raidVictory = raid != null && raid.victory();
        // core: ReactToBell.
        if (state.heardBellTime != NEVER && raid == null) state.activity = Activity.HIDE;
        // core: SetRaidStatus.
        if (raid != null && raidStatusCoin(villager.id(), gameTime, COIN_SET_RAID_STATUS)) {
            state.activity = !raid.firstWaveSpawned() || raid.betweenWaves()
                    ? Activity.PRE_RAID : Activity.RAID;
        }
        switch (state.activity) {
            case HIDE -> {
                setHiddenState(state, bx, by, bz, gameTime, dayTime, baby);
                if (state.activity == Activity.HIDE) {
                    locateHidingPlace(world, state, villager, bx, by, bz, gameTime,
                            HIDE_LOCATE_RADIUS_BLOCKS, HIDE_WALK_SPEED_FACTOR,
                            HIDE_LOCATE_CLOSE_ENOUGH_BLOCKS);
                }
            }
            case RAID -> {
                if (raid != null && raid.activeFight()) {
                    locateHidingPlace(world, state, villager, bx, by, bz, gameTime,
                            RAID_LOCATE_RADIUS_BLOCKS, RAID_WALK_SPEED_FACTOR,
                            RAID_LOCATE_CLOSE_ENOUGH_BLOCKS);
                }
                resetRaidStatus(state, villager.id(), raid, gameTime, dayTime, baby);
            }
            case PRE_RAID -> resetRaidStatus(state, villager.id(), raid, gameTime, dayTime, baby);
            case IDLE, WORK, MEET, REST, PLAY ->
                    updateActivityFromSchedule(state, gameTime, dayTime, baby);
        }
    }

    /** {@code SetHiddenState}: HIDING_PLACE 와 HEARD_BELL_TIME 이 둘 다 있어야 돈다. */
    private static void setHiddenState(State state, int bx, int by, int bz, long gameTime,
            long dayTime, boolean baby) {
        if (state.hidingPlace == null || state.heardBellTime == NEVER) return;
        boolean expired = state.heardBellTime + HEARD_BELL_TIMEOUT_TICKS <= gameTime;
        if (state.ticksHidden <= HIDE_MAX_HIDDEN_TICKS && !expired) {
            if (blockDistanceSquared(state.hidingPlace, bx, by, bz)
                    < (long) HIDE_CLOSE_ENOUGH_BLOCKS * HIDE_CLOSE_ENOUGH_BLOCKS) {
                state.ticksHidden += VillagerSocietyRules.MC_TICKS_PER_AUTHORITY_TICK;
            }
            return;
        }
        state.heardBellTime = NEVER;
        state.hidingPlace = null;
        updateActivityFromSchedule(state, gameTime, dayTime, baby);
        state.ticksHidden = 0;
    }

    /**
     * {@code ResetRaidStatus}: 습격이 없거나 중단·패배면 기본 활동을 IDLE 로 되돌리고 일정으로
     * 갱신한다. 기본 활동은 WebCraft 에서 관측되지 않는다(일정 활동은 기억 요구가 없어 기본
     * 활동으로 떨어지는 길이 없다) — 그래서 따로 들고 있지 않는다.
     */
    private static void resetRaidStatus(State state, long mobId, RaidView raid, long gameTime,
            long dayTime, boolean baby) {
        if (!raidStatusCoin(mobId, gameTime, COIN_RESET_RAID_STATUS)) return;
        if (raid != null && !raid.stopped() && !raid.loss()) return;
        updateActivityFromSchedule(state, gameTime, dayTime, baby);
    }

    /** {@code LocateHidingPlace}: WALK_TARGET 이 없을 때만 돈다. */
    private static void locateHidingPlace(ActivityWorld world, State state,
            ActivityVillager villager, int bx, int by, int bz, long gameTime, int radius,
            double speedFactor, int closeEnough) {
        if (state.walkTarget != null) return;
        int[] chosen = null;
        List<int[]> near = world.homesWithin(bx, by, bz, closeEnough + 1);
        if (near != null) {
            for (int[] home : near) {
                if (closerToCenterThan(home, villager, closeEnough)) {
                    chosen = home;
                    break;
                }
            }
        }
        if (chosen == null) {
            List<int[]> homes = world.homesWithin(bx, by, bz, radius);
            if (homes != null && !homes.isEmpty()) {
                chosen = homes.get(randomIndex(villager.id(), gameTime, homes.size()));
            }
        }
        if (chosen == null) chosen = villager.homeBed();
        if (chosen == null) return;
        state.hidingPlace = new int[] { chosen[0], chosen[1], chosen[2] };
        if (!closerToCenterThan(chosen, villager, closeEnough)) {
            state.walkTarget = state.hidingPlace.clone();
            state.walkCloseEnough = closeEnough;
            state.walkSpeedFactor = speedFactor;
        }
    }

    /** {@code Brain.updateActivityFromSchedule}. */
    private static void updateActivityFromSchedule(State state, long gameTime, long dayTime,
            boolean baby) {
        if (state.lastScheduleUpdate != NEVER
                && gameTime - state.lastScheduleUpdate <= SCHEDULE_UPDATE_INTERVAL_TICKS) {
            return;
        }
        state.lastScheduleUpdate = gameTime;
        // setActiveActivityIfPossible: 일정 활동은 기억 요구를 두지 않는다(클래스 주석).
        state.activity = VillagerBrainRules.scheduledActivity(dayTime, baby ? -1 : 0);
    }

    private static long blockDistanceSquared(int[] cell, int bx, int by, int bz) {
        long dx = cell[0] - (long) bx;
        long dy = cell[1] - (long) by;
        long dz = cell[2] - (long) bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** {@code BlockPos.closerToCenterThan(position, d)}: 칸 중심과 위치의 거리² < d². */
    private static boolean closerToCenterThan(int[] cell, ActivityVillager villager, int d) {
        double dx = cell[0] + 0.5 - villager.x();
        double dy = cell[1] + 0.5 - villager.y();
        double dz = cell[2] + 0.5 - villager.z();
        return dx * dx + dy * dy + dz * dz < (double) d * d;
    }

    /**
     * 이 권위 틱이 덮는 두 MC 틱({@code gameTime-1}, {@code gameTime}) 중 한 번이라도
     * {@code nextInt(20)==0} 이 나오는가. 동전은 (mob id, MC 틱, 용도)의 결정적 해시다.
     */
    public static boolean raidStatusCoin(long mobId, long gameTime, int salt) {
        for (long mcTick = gameTime - VillagerSocietyRules.MC_TICKS_PER_AUTHORITY_TICK + 1;
                mcTick <= gameTime; mcTick++) {
            if (Integer.remainderUnsigned(hash(mobId, mcTick, salt), RAID_STATUS_COIN_BOUND)
                    == 0) {
                return true;
            }
        }
        return false;
    }

    /** {@code PoiManager.getRandom} 자리의 결정적 후보 서수. */
    public static int randomIndex(long mobId, long gameTime, int size) {
        return Integer.remainderUnsigned(hash(mobId, gameTime, COIN_RANDOM_HOME), size);
    }

    /** 두 권위가 32비트 정수 연산만으로 같은 값을 내는 해시(정적판 {@code activityHash}). */
    public static int hash(long mobId, long tick, int salt) {
        int h = mix32((int) mobId ^ salt);
        h = mix32(h ^ (int) (mobId >>> 32));
        h = mix32(h ^ (int) tick);
        return mix32(h ^ (int) (tick >>> 32));
    }

    private static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }
}
