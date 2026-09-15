package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.inventory.PlayerInventory;

/** 주민의 일정·POI·번식·직업·golem 판정만 모은 상태 없는 규칙이다. */
public final class VillagerBrainRules {
    public static final long DAY_LENGTH_TICKS = 24_000L;
    public static final long SCHEDULE_REFRESH_INTERVAL_TICKS = 20L;
    public static final int BREEDING_FOOD_THRESHOLD = 12;

    public static final int POI_SEARCH_RADIUS_BLOCKS = 48;
    public static final int POI_SEARCH_MAX_RESULTS = 5;
    public static final int POI_INITIAL_PHASE_SPAN_TICKS = 20;
    public static final int POI_RETRY_BASE_TICKS = 20;
    public static final int POI_RETRY_JITTER_SPAN_TICKS = 20;
    public static final int POI_FAILURE_BACKOFF_BASE_TICKS = 40;
    public static final int POI_FAILURE_BACKOFF_JITTER_SPAN_TICKS = 40;
    public static final int POI_FAILURE_BACKOFF_MAX_TICKS = 400;

    public static final int PRIMARY_SENSOR_INTERVAL_TICKS = 20;
    public static final int SECONDARY_SENSOR_INTERVAL_TICKS = 40;
    public static final int GOLEM_SENSOR_INTERVAL_TICKS = 200;

    public static final int MATING_DURATION_TICKS = 350;
    public static final int BIRTH_DELAY_BASE_TICKS = 275;
    public static final int BIRTH_DELAY_JITTER_SPAN_TICKS = 50;
    public static final int MATE_RADIUS_BLOCKS = 5;
    public static final int SUCCESSFUL_PARENT_AGE_TICKS = 6_000;
    public static final int CHILD_AGE_TICKS = -24_000;

    public static final long GOLEM_RECENT_SLEEP_WINDOW_TICKS = 24_000L;
    public static final int GOLEM_PANIC_CHECK_INTERVAL_TICKS = 100;
    public static final int GOLEM_PANIC_MIN_VILLAGERS = 3;
    public static final int GOLEM_GOSSIP_MIN_VILLAGERS = 5;
    public static final int GOLEM_RECENT_MEMORY_TTL_TICKS = 599;
    public static final int GOLEM_NEARBY_VILLAGER_RADIUS_BLOCKS = 10;
    public static final int GOLEM_NEARBY_VILLAGER_LIMIT = 5;

    /**
     * 바닐라 {@code WorkAtPoi.CHECK_COOLDOWN}: 한 번 작업대를 쓴 주민은 300틱이 지나야 다시
     * 근무 판정을 받는다({@code LAST_WORKED_AT_POI} 기억).
     */
    public static final int WORK_AT_POI_INTERVAL_TICKS = 300;

    /**
     * 바닐라 {@code WorkAtPoi.DISTANCE}: 직업지 셀 중심에서 이 거리 안이어야 근무로 친다
     * ({@code globalPos.pos().closerToCenterThan(villager.position(), 1.73)}).
     */
    public static final double WORK_AT_POI_RANGE_BLOCKS = 1.73;

    /**
     * 주민의 비핵심 활동. 앞의 다섯은 일정({@link #scheduledActivity})이 고르는 값이고, 뒤의 셋은
     * 바닐라 {@code Villager#registerBrainGoals} 가 함께 등록하는 사건 활동이다
     * ({@code Activity.HIDE}·{@code PRE_RAID}·{@code RAID}; 26.3-snapshot-7
     * {@code Villager.lambda$static$0} javap). 사건 활동은 일정이 덮지 않는다 — 바닐라에서
     * {@code UpdateActivityFromSchedule} 은 WORK·PLAY·REST·MEET·IDLE 묶음에만 있다
     * ({@code VillagerGoalPackages} javap). 현재 활동의 정본은 {@link VillagerActivityLedger}다.
     */
    public enum Activity {
        IDLE, WORK, MEET, REST, PLAY, HIDE, PRE_RAID, RAID;

        /** 일정이 고르는 다섯 활동인가(사건 활동이 아닌가). */
        public boolean scheduled() {
            return this == IDLE || this == WORK || this == MEET || this == REST || this == PLAY;
        }
    }

    /**
     * 일정이 실제로 주민을 보내는 곳. 바닐라 {@code VillagerGoalPackages} 의
     * {@code SetWalkTargetFromBlockMemory} 대상 메모리와 1:1 이다. IDLE·PLAY 는 특정 POI 로
     * 향하지 않고 각각 배회·놀이 지점을 쓴다.
     */
    public enum ActivityDestination { NONE, JOB_SITE, MEETING_POINT, HOME_BED }

    /** WORK/MEET 는 0.4, REST(귀가)는 1.0 배속으로 이동한다. */
    public static final double WORK_WALK_SPEED_MODIFIER = 0.4;
    public static final double MEET_WALK_SPEED_MODIFIER = 0.4;
    public static final double REST_WALK_SPEED_MODIFIER = 1.0;

    /** 목적지에 이만큼 가까우면 도착으로 본다(블록). */
    public static final int JOB_SITE_CLOSE_ENOUGH_BLOCKS = 9;
    public static final int MEETING_POINT_CLOSE_ENOUGH_BLOCKS = 6;
    public static final int HOME_BED_CLOSE_ENOUGH_BLOCKS = 1;

    /** 이 거리를 넘어가면 그 POI 기억을 버린다(블록). */
    public static final int JOB_SITE_MAX_DISTANCE_BLOCKS = 100;
    public static final int MEETING_POINT_MAX_DISTANCE_BLOCKS = 100;
    public static final int HOME_BED_MAX_DISTANCE_BLOCKS = 150;

    /** 걷기 목표를 다시 세우는 간격(틱). */
    public static final int JOB_SITE_RETRY_INTERVAL_TICKS = 1_200;
    public static final int MEETING_POINT_RETRY_INTERVAL_TICKS = 200;
    public static final int HOME_BED_RETRY_INTERVAL_TICKS = 1_200;

    /** 센서 계층은 20틱 기본, secondary POI 40틱, golem 200틱이다. */
    public enum SensorKind { PRIMARY, SECONDARY_POI, GOLEM }

    /** VillagerHostilesSensor가 사용하는 종별 panic 반경이다. */
    public enum HostileKind {
        DROWNED, EVOKER, HUSK, ILLUSIONER, PILLAGER, RAVAGER,
        VEX, VINDICATOR, ZOGLIN, ZOMBIE, ZOMBIE_VILLAGER
    }

    public enum ChildTypeLane { BIOME, FIRST_PARENT, MATE }

    /** 실제 직업 목록이 아니라 reset/claim 판정에 필요한 최소 상태다. */
    public enum ProfessionState { NONE, NITWIT, ASSIGNED }

    private VillagerBrainRules() {}

    public static boolean isBaby(int age) {
        return age < 0;
    }

    /**
     * 하루 일정표({@code EnvironmentAttributes.VILLAGER_ACTIVITY}/{@code BABY_VILLAGER_ACTIVITY})가
     * 이 시각에 고르는 활동. 주민의 <b>현재</b> 활동이 아니다 — 종·습격이 만든 HIDE/PRE_RAID/RAID 를
     * 모르기 때문이다. 이 함수를 부르는 곳은 {@link VillagerActivityLedger} 하나뿐이어야 하며,
     * 판정 lane 은 원장이 돌려주는 활동을 쓴다.
     */
    public static Activity scheduledActivity(long dayTime, int age) {
        long time = Math.floorMod(dayTime, DAY_LENGTH_TICKS);
        if (isBaby(age)) {
            if (time < 3_000L) return Activity.IDLE;
            if (time < 6_000L) return Activity.PLAY;
            if (time < 10_000L) return Activity.IDLE;
            if (time < 12_000L) return Activity.PLAY;
            return Activity.REST;
        }
        if (time < 2_000L) return Activity.IDLE;
        if (time < 9_000L) return Activity.WORK;
        if (time < 11_000L) return Activity.MEET;
        if (time < 12_000L) return Activity.IDLE;
        return Activity.REST;
    }

    /** 이 활동이 향하는 POI. */
    public static ActivityDestination activityDestination(Activity activity) {
        return switch (activity) {
            case WORK -> ActivityDestination.JOB_SITE;
            case MEET -> ActivityDestination.MEETING_POINT;
            case REST -> ActivityDestination.HOME_BED;
            // 사건 활동은 기억 목적지가 없다. HIDE/RAID 의 귀가 걷기는 원장의 WALK_TARGET
            // (LocateHidingPlace)이 직접 들고, PRE_RAID 의 MEETING_POINT 걷기는 WebCraft 에 집합점
            // 기억이 없어 배회(1.5배)만 남는다.
            case IDLE, PLAY, HIDE, PRE_RAID, RAID -> ActivityDestination.NONE;
        };
    }

    /**
     * 바닐라 {@code GoToPotentialJobSite#checkExtraStartConditions}: 후보 직업지로 걷는 것은 비핵심
     * 활동이 IDLE·WORK·PLAY 일 때뿐이다(26.3-snapshot-7 javap). MEET·REST 와 사건 활동은 걷지 않는다.
     */
    public static boolean potentialJobSiteWalkAllowed(Activity activity) {
        return activity == Activity.IDLE || activity == Activity.WORK || activity == Activity.PLAY;
    }

    public static double destinationSpeedModifier(ActivityDestination destination) {
        return switch (destination) {
            case JOB_SITE -> WORK_WALK_SPEED_MODIFIER;
            case MEETING_POINT -> MEET_WALK_SPEED_MODIFIER;
            case HOME_BED -> REST_WALK_SPEED_MODIFIER;
            case NONE -> 0.0;
        };
    }

    public static int destinationCloseEnoughBlocks(ActivityDestination destination) {
        return switch (destination) {
            case JOB_SITE -> JOB_SITE_CLOSE_ENOUGH_BLOCKS;
            case MEETING_POINT -> MEETING_POINT_CLOSE_ENOUGH_BLOCKS;
            case HOME_BED -> HOME_BED_CLOSE_ENOUGH_BLOCKS;
            case NONE -> 0;
        };
    }

    public static int destinationMaxDistanceBlocks(ActivityDestination destination) {
        return switch (destination) {
            case JOB_SITE -> JOB_SITE_MAX_DISTANCE_BLOCKS;
            case MEETING_POINT -> MEETING_POINT_MAX_DISTANCE_BLOCKS;
            case HOME_BED -> HOME_BED_MAX_DISTANCE_BLOCKS;
            case NONE -> 0;
        };
    }

    public static int destinationRetryIntervalTicks(ActivityDestination destination) {
        return switch (destination) {
            case JOB_SITE -> JOB_SITE_RETRY_INTERVAL_TICKS;
            case MEETING_POINT -> MEETING_POINT_RETRY_INTERVAL_TICKS;
            case HOME_BED -> HOME_BED_RETRY_INTERVAL_TICKS;
            case NONE -> 0;
        };
    }

    /** 목적지에 도착했는가. 수평/수직을 나눠 재는 바닐라 근접 판정과 같이 상자로 본다. */
    public static boolean destinationReached(
            ActivityDestination destination, double dx, double dy, double dz) {
        if (destination == ActivityDestination.NONE) return true;
        int closeEnough = destinationCloseEnoughBlocks(destination);
        return Math.abs(dx) <= closeEnough && Math.abs(dy) <= closeEnough
                && Math.abs(dz) <= closeEnough;
    }

    /** POI 기억을 버려야 할 만큼 멀어졌는가. */
    public static boolean destinationOutOfRange(
            ActivityDestination destination, double dx, double dy, double dz) {
        if (destination == ActivityDestination.NONE) return false;
        int max = destinationMaxDistanceBlocks(destination);
        return Math.abs(dx) > max || Math.abs(dy) > max || Math.abs(dz) > max;
    }

    /**
     * 배회가 이 POI 기억의 울타리를 벗어났는가 — 바닐라 core package
     * {@code GoToClosestVillage(speedModifier, 5)} 자리다. 바닐라 주민은 다른 걷기 목표가 없을 때
     * {@code PoiManager} 마을 인덱스로 "마을 밖"을 판정하고 가장 가까운 마을 구역으로 돌아가며,
     * 그 덕분에 일정이 POI 로 보내지 않는 시간대(IDLE·MEET·PLAY)에도 마을을 떠나지 않는다.
     *
     * <p>WebCraft divergence: PoiManager 마을 인덱스가 없으므로 "마을"은 주민 자신의 POI
     * 기억이고, 안/밖 경계는 같은 기억을 향한 바닐라 walk target 의 close-enough 거리
     * ({@code SetWalkTargetFromBlockMemory(JOB_SITE, 0.4f, 9, 100, 1200)} 의 9)를 그대로 쓴다.
     * 새 수치를 만들지 않으며, 판정은 {@link #destinationReached} 의 여집합이라 울타리 반경이
     * 도착 반경과 갈릴 수 없다.
     */
    public static boolean wanderedOutOfPoiMemory(
            ActivityDestination destination, double dx, double dy, double dz) {
        if (destination == ActivityDestination.NONE) return false;
        return !destinationReached(destination, dx, dy, dz);
    }

    /**
     * 아무 POI 기억도 없는 주민의 울타리 — 같은 바닐라 {@code GoToClosestVillage} 자리의 나머지
     * 절반이다. {@link #wanderedOutOfPoiMemory} 는 자기 POI 기억을 가진 주민만 잡으므로,
     * 미취업·POI 무기억 주민에게는 판정할 기억 자체가 없다. 바닐라는 그 주민도
     * {@code PoiManager} 마을 인덱스로 되돌린다.
     *
     * <p>바닐라 근거(Java 1.21.4): {@code PoiManager} 는 16블록 정육면체 구역
     * ({@code SectionPos}) 격자 위에서 마을 POI 가 점유된 구역을 0 으로 두고 이웃 구역마다 1씩
     * 번지는 거리장({@code PoiManager.DistanceTracker}, {@code SectionTracker} 의 26방향 이웃)을
     * 유지한다. {@code ServerLevel#isVillage(BlockPos)} 는 그 거리가 {@code 1} 이하인 곳을
     * "마을 안"으로 보고, {@code GoToClosestVillage} 는 그 밖에 선 주민만 마을로 돌려보낸다.
     * 원천이 하나일 때 그 거리장은 구역 좌표의 Chebyshev 거리와 같으므로, 여기서는 그대로
     * 구역 좌표 차의 최댓값을 쓴다. 새 수치를 만들지 않는다.
     *
     * <p>WebCraft divergence: 마을 인덱스가 없으므로 거리장의 원천은 이 주민의 마을 앵커
     * 하나다({@code VillagerSocialState#homeVillageAnchor}, 주민이 놓인 마을 site 의 자리).
     * 그 칸은 마을 구조물 안이라 바닐라라면 점유된 마을 POI 가 있는 구역이다.
     */
    public static final int VILLAGE_SECTION_SIZE_BLOCKS = 16;
    public static final int VILLAGE_SECTION_RADIUS = 1;

    /** 블록 좌표가 속한 바닐라 {@code SectionPos} 축 좌표. */
    public static int villageSectionOf(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), VILLAGE_SECTION_SIZE_BLOCKS);
    }

    /**
     * 마을 앵커에서 {@link #VILLAGE_SECTION_RADIUS} 구역을 넘어갔는가 =
     * {@code !ServerLevel#isVillage(pos)}.
     */
    public static boolean wanderedOutOfHomeVillage(
            int anchorX, int anchorY, int anchorZ, double x, double y, double z) {
        int dx = Math.abs(villageSectionOf(x) - villageSectionOf(anchorX));
        int dy = Math.abs(villageSectionOf(y) - villageSectionOf(anchorY));
        int dz = Math.abs(villageSectionOf(z) - villageSectionOf(anchorZ));
        return Math.max(dx, Math.max(dy, dz)) > VILLAGE_SECTION_RADIUS;
    }

    /** 정확히 gameTime-lastUpdate>20인 경우. 뺄셈과 임계값 덧셈 overflow를 모두 피한다. */
    public static boolean scheduleRefreshDue(long gameTime, long lastUpdate) {
        return strictlyAfter(gameTime, lastUpdate, SCHEDULE_REFRESH_INTERVAL_TICKS);
    }

    /**
     * 주민이 지금 자기 직업지에서 실제로 "일하는" 위치인가. 바닐라
     * {@code closerToCenterThan} 과 같이 경계값(정확히 1.73블록)은 포함하지 않는다.
     */
    public static boolean withinWorkstationRange(double distanceSquared) {
        return distanceSquared < WORK_AT_POI_RANGE_BLOCKS * WORK_AT_POI_RANGE_BLOCKS;
    }

    /**
     * 마지막 근무에서 {@link #WORK_AT_POI_INTERVAL_TICKS} 이 지났는가. 아직 한 번도 일한 적이
     * 없는 주민({@code Long.MIN_VALUE})은 항상 자격이 있다.
     *
     * <p>WebCraft divergence: 바닐라 {@code WorkAtPoi} 는 쿨다운이 끝난 뒤에도
     * {@code random.nextInt(2) != 0} 동전을 한 번 더 던지지만, 두 권위가 같은 틱에 같은 결론을
     * 내야 하므로 그 동전은 두지 않는다. 실제 재입고는
     * {@link VillagerTradeRules#allowedToRestock} 이 하루 2회·2400틱 간격으로 막고 있어
     * 동전 유무가 재입고 총량을 바꾸지 않는다.
     */
    public static boolean workAtPoiDue(long gameTime, long lastWorkedAtPoi) {
        if (lastWorkedAtPoi == Long.MIN_VALUE) return true;
        return atLeastElapsed(gameTime, lastWorkedAtPoi, WORK_AT_POI_INTERVAL_TICKS);
    }

    public static int sensorIntervalTicks(SensorKind sensor) {
        return switch (sensor) {
            case PRIMARY -> PRIMARY_SENSOR_INTERVAL_TICKS;
            case SECONDARY_POI -> SECONDARY_SENSOR_INTERVAL_TICKS;
            case GOLEM -> GOLEM_SENSOR_INTERVAL_TICKS;
        };
    }

    /** 센서의 마지막 실행 시각에서 interval 이상 지난 경우다. */
    public static boolean sensorDue(long gameTime, long lastUpdate, SensorKind sensor) {
        return atLeastElapsed(gameTime, lastUpdate, sensorIntervalTicks(sensor));
    }

    public static int poiSearchRadiusBlocks() {
        return POI_SEARCH_RADIUS_BLOCKS;
    }

    public static int poiSearchMaxResults() {
        return POI_SEARCH_MAX_RESULTS;
    }

    /** nextInt 결과는 호출자가 소비한다. 이 함수는 그 phase만 순수하게 전달한다. */
    public static int poiInitialPhaseTicks(int nextInt20) {
        requireRandomLane(nextInt20, POI_INITIAL_PHASE_SPAN_TICKS, "initial POI phase");
        return nextInt20;
    }

    public static int poiRetryDelayTicks(int nextInt20) {
        requireRandomLane(nextInt20, POI_RETRY_JITTER_SPAN_TICKS, "POI retry jitter");
        return POI_RETRY_BASE_TICKS + nextInt20;
    }

    /** 실패마다 이전 지연에 40+nextInt(40)을 더하고 400에서 멈춘다. */
    public static int poiFailureBackoffTicks(int previousDelayTicks, int nextInt40) {
        requireRandomLane(nextInt40, POI_FAILURE_BACKOFF_JITTER_SPAN_TICKS,
                "POI failure backoff jitter");
        long candidate = (long) previousDelayTicks
                + POI_FAILURE_BACKOFF_BASE_TICKS + nextInt40;
        return candidate >= POI_FAILURE_BACKOFF_MAX_TICKS
                ? POI_FAILURE_BACKOFF_MAX_TICKS : (int) candidate;
    }

    public static boolean poiRetryDue(long gameTime, long nextScheduledAttempt) {
        return gameTime >= nextScheduledAttempt;
    }

    /** JitteredLinearRetry의 400틱 유효 창이며, 시계가 뒤로 가면 재시도하지 않는다. */
    public static boolean poiRetryStillValid(long gameTime, long previousAttempt) {
        return withinElapsedWindow(gameTime, previousAttempt, POI_FAILURE_BACKOFF_MAX_TICKS);
    }

    public static int panicRadiusBlocks(HostileKind hostile) {
        return switch (hostile) {
            case DROWNED, HUSK, VEX, ZOMBIE, ZOMBIE_VILLAGER -> 8;
            case EVOKER, ILLUSIONER, RAVAGER -> 12;
            case PILLAGER -> 15;
            case VINDICATOR, ZOGLIN -> 10;
        };
    }

    public static boolean hostileWithinPanicRadius(HostileKind hostile, double distanceSquared) {
        int radius = panicRadiusBlocks(hostile);
        return distanceSquared >= 0.0 && distanceSquared <= (double) radius * radius;
    }

    public static boolean mateEligible(
            int age, boolean awake, int effectiveFoodTotal, boolean breedTargetPresent) {
        return breedTargetPresent && willingToBreed(age, awake, effectiveFoodTotal);
    }

    public static int matingDurationTicks() {
        return MATING_DURATION_TICKS;
    }

    public static boolean mateWithinRange(double distanceSquared) {
        return distanceSquared >= 0.0
                && distanceSquared <= (double) MATE_RADIUS_BLOCKS * MATE_RADIUS_BLOCKS;
    }

    public static int successfulParentAgeTicks() {
        return SUCCESSFUL_PARENT_AGE_TICKS;
    }

    public static int childAgeTicks() {
        return CHILD_AGE_TICKS;
    }

    public static int birthDelayTicks(int nextInt50) {
        requireRandomLane(nextInt50, BIRTH_DELAY_JITTER_SPAN_TICKS, "birth delay jitter");
        return BIRTH_DELAY_BASE_TICKS + nextInt50;
    }

    /** 출산 시각에 mate가 가까우면 음식 소비/출산 시도를 수행하는 경계다. */
    public static boolean birthAttemptDue(
            long gameTime, long birthTimestamp, boolean mateWithinFiveBlocks) {
        return gameTime >= birthTimestamp && mateWithinFiveBlocks;
    }

    /** 실제 새끼 생성에는 도달 가능한 빈 HOME bed도 필요하다. */
    public static boolean birthEligible(
            long gameTime, long birthTimestamp, boolean mateWithinFiveBlocks,
            boolean reachableVacantHomeBed) {
        return birthAttemptDue(gameTime, birthTimestamp, mateWithinFiveBlocks)
                && reachableVacantHomeBed;
    }

    public static ChildTypeLane childTypeLane(double draw) {
        if (draw < 0.5) return ChildTypeLane.BIOME;
        if (draw < 0.75) return ChildTypeLane.FIRST_PARENT;
        return ChildTypeLane.MATE;
    }

    public static ProfessionState childProfession() {
        return ProfessionState.NONE;
    }

    public static boolean professionClaimEligible(
            ProfessionState currentProfession, boolean potentialJobSiteWithinTwoBlocks) {
        return currentProfession == ProfessionState.NONE && potentialJobSiteWithinTwoBlocks;
    }

    public static boolean professionResetEligible(
            boolean jobSitePresent, int level, int xp, ProfessionState profession) {
        return !jobSitePresent && level <= 1 && xp == 0
                && profession != ProfessionState.NONE && profession != ProfessionState.NITWIT;
    }

    /** VillagerPanicTrigger의 100틱 경계다. RNG는 호출자가 소유한다. */
    public static boolean golemPanicCheckDue(long gameTime) {
        return gameTime % GOLEM_PANIC_CHECK_INTERVAL_TICKS == 0;
    }

    public static boolean golemPanicEligible(int nearbyVillagerCount) {
        return nearbyVillagerCount >= GOLEM_PANIC_MIN_VILLAGERS;
    }

    public static boolean golemGossipEligible(int nearbyVillagerCount) {
        return nearbyVillagerCount >= GOLEM_GOSSIP_MIN_VILLAGERS;
    }

    public static int golemNearbyVillagerRadiusBlocks() {
        return GOLEM_NEARBY_VILLAGER_RADIUS_BLOCKS;
    }

    public static int golemNearbyVillagerLimit() {
        return GOLEM_NEARBY_VILLAGER_LIMIT;
    }

    public static boolean recentSleepWithinGolemWindow(long gameTime, long lastSleepTime) {
        return withinElapsedWindow(gameTime, lastSleepTime, GOLEM_RECENT_SLEEP_WINDOW_TICKS);
    }

    public static boolean golemRecentMemoryActive(long gameTime, long memorySetTime) {
        return withinElapsedWindow(gameTime, memorySetTime, GOLEM_RECENT_MEMORY_TTL_TICKS);
    }

    public static boolean golemEligible(
            long gameTime, long lastSleepTime, boolean lastSleepPresent,
            boolean recentGolemMemoryPresent) {
        return lastSleepPresent
                && recentSleepWithinGolemWindow(gameTime, lastSleepTime)
                && !recentGolemMemoryPresent;
    }

    /** 호출 한 번은 Minecraft 논리 tick 하나이며 부호에 따라 0으로 한 칸 접근한다. */
    public static int ageAfterLogicalTick(int age) {
        if (age < 0) return age + 1;
        if (age > 0) return age - 1;
        return 0;
    }

    public static int foodPoints(short itemType) {
        if (itemType == PlayerInventory.BREAD) return 4;
        if (itemType == PlayerInventory.POTATO
                || itemType == PlayerInventory.CARROT
                || itemType == PlayerInventory.BEETROOT) return 1;
        return 0;
    }

    /** 기존 인벤토리의 평행 배열을 읽어 별도 스택 객체를 만들지 않는다. */
    public static int effectiveFoodTotal(short[] itemTypes, int[] counts) {
        int total = 0;
        for (int slot = 0; slot < itemTypes.length; slot++) {
            total += foodPoints(itemTypes[slot]) * counts[slot];
        }
        return total;
    }

    public static boolean willingToBreed(int age, boolean awake, int effectiveFoodTotal) {
        return age == 0 && awake && effectiveFoodTotal >= BREEDING_FOOD_THRESHOLD;
    }

    private static void requireRandomLane(int value, int bound, String label) {
        if (value < 0 || value >= bound) {
            throw new IllegalArgumentException(label + " must be in [0," + bound + ")");
        }
    }

    private static boolean strictlyAfter(long gameTime, long lastUpdate, long interval) {
        if (gameTime <= lastUpdate || lastUpdate > Long.MAX_VALUE - interval) return false;
        return gameTime > lastUpdate + interval;
    }

    private static boolean atLeastElapsed(long gameTime, long lastUpdate, long interval) {
        if (gameTime < lastUpdate || lastUpdate > Long.MAX_VALUE - interval) return false;
        return gameTime >= lastUpdate + interval;
    }

    private static boolean withinElapsedWindow(long gameTime, long startTime, long window) {
        if (gameTime < startTime) return false;
        if (startTime > Long.MAX_VALUE - window) return true;
        return gameTime < startTime + window;
    }
}
