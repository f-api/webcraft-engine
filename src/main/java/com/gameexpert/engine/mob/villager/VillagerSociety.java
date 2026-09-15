package com.gameexpert.engine.mob.villager;

import com.gameexpert.mob.dto.VillagerBedClaimSnapshot;
import com.gameexpert.mob.dto.VillagerSocietySnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 주민 번식·철 골렘 소환의 상태·영속 계층이다. 판정 상수는 {@link VillagerBrainRules},
 * 월드 탐색은 {@link VillagerSocietyRules}, 침대 소유는 {@link VillagerBedClaims}가 갖는다.
 *
 * <p>바닐라 근거(Java 1.21.4):
 * <ul>
 *   <li>번식은 {@code VillagerMakeLove}: BREED_TARGET 이 서로 willing 이고 5블록 안이어야 하며,
 *       시작 시각에 {@code 275+nextInt(50)} 뒤를 출산 시각으로 잡고 350틱 뒤 만료한다. 출산 시각에
 *       양쪽이 음식을 소비하고, 도달 가능한 빈 HOME 침대가 있어야 새끼가 생긴다. 성공하면 부모
 *       age {@code +6000}, 새끼 {@code -24000}이며 새끼가 그 침대를 갖는다.</li>
 *   <li>willing 은 {@code Villager#canBreed}: age 0 이고 음식 점수 합이 12 이상.</li>
 *   <li>골렘은 {@code Villager#spawnGolemIfNeeded}: 최근 24000틱 안에 잤고 GOLEM_DETECTED_RECENTLY
 *       가 없는 주민이, 100틱 주기 panic 경로에서 10블록 안 같은 조건 주민 3명(최대 5명 표본) 이상
 *       모였을 때 {@code SpawnUtil.trySpawnMob(...10,8,6,LEGACY_IRON_GOLEM)}으로 소환하고, 성공하면
 *       10블록 안 모든 주민에게 TTL 599 기억을 남긴다.</li>
 * </ul>
 *
 * <p>Food stock is an eight-slot item inventory shared by nearby awake villagers.
 * Farmers share surplus wheat and craft bread while working. Transfers commit in this society lane;
 * flying item entities and autonomous crop harvesting are separate behaviours.
 */
public final class VillagerSociety {
    /** 음식 점수 상한. 정수 넘침 방지용 방어값이며 바닐라 판정에 영향을 주지 않는다. */
    public static final int MAX_FOOD_POINTS = 2_048;

    /** 표현이 없는 주민의 시각 행동. */
    public static final String NO_VISUAL_ACTION = "none";

    /** 침대에서 자는 동안 매 틱 다시 채우는 시각 행동. */
    public static final String SLEEP_VISUAL_ACTION = "sleep";

    /** MEET 일정에서 이웃과 붙어 있는 동안 매 틱 다시 채우는 시각 행동. */
    public static final String SOCIALIZE_VISUAL_ACTION = "socialize";

    /**
     * 시각 행동을 다시 채워 넣는 길이. 매 틱 갱신되므로 상태가 유지되는 한 만료하지 않고,
     * 권위가 조용해져도 이 길이 안에 클라이언트가 스스로 IDLE 로 돌아간다.
     */
    public static final int SOCIAL_VISUAL_ACTION_TICKS = 40;

    /** 이번 틱 시뮬레이션 대상 주민 하나. */
    public interface SocietyVillager {
        long id();

        double x();

        double y();

        double z();

        boolean baby();

        /** 최근 번식으로 age>0 인 상태(바닐라 6000틱). */
        boolean breedingCooldown();

        /** 이번 틱 panic 반경 안 적대 몹을 감지했는가. */
        boolean panicking();

        boolean farmer();

        /**
         * 이 주민의 현재 활동. 정본은 {@link VillagerActivityLedger} 이며 권위 배선이 그 값을 넘긴다.
         * 원장이 없는 순수 테스트 월드는 막 만들어진 뇌의 일정 값이다.
         *
         * @param dayTime MC 일정 축(0..23999)의 시각
         */
        default VillagerBrainRules.Activity activity(long dayTime) {
            return VillagerActivityLedger.fallbackActivity(dayTime, baby());
        }
    }

    /** 사회 계층이 보는 월드. 블록 분류는 골렘 배치 탐색과 공유한다. */
    public interface SocietyWorld extends VillagerSocietyRules.GolemSpawnProbe {
        long dayCount();

        /** 0..11999 월드 시계. */
        long worldTime();

        boolean bedBlockAt(int x, int y, int z);

        /**
         * Resident POI-index hook. Pure tests and unknown resident coverage retain the exact
         * block-by-block scan through this default implementation.
         */
        default int[] nearestVacantBed(int originX, int originY, int originZ,
                VillagerSocietyRules.VacantBedProbe probe) {
            return VillagerSocietyRules.nearestVacantBed(originX, originY, originZ, probe);
        }

        int nextInt(int bound);
    }

    /** 태어난 새끼 하나. 침대는 이미 새끼 소유로 claim 되어 있다. */
    public record Birth(long firstParentId, long secondParentId, long childId,
            double x, double y, double z, int bedX, int bedY, int bedZ) {}

    /** 소환된 철 골렘 하나. */
    public record GolemSummon(long summonerId, int x, int y, int z, int wantingVillagers) {}

    /** 침대가 없어 새끼가 생기지 않은 출산 시도(음식은 이미 소비됐다). */
    public record FailedBirth(long firstParentId, long secondParentId) {}

    public record Outcome(List<Birth> births, List<GolemSummon> golems,
            List<FailedBirth> failedBirths) {}

    /** 새끼를 실제로 만들어 주는 권위 훅. 0을 돌려주면 출산을 포기한다. */
    @FunctionalInterface
    public interface ChildSpawner {
        long spawnChild(double x, double y, double z);
    }

    /** 골렘을 실제로 만들어 주는 권위 훅. */
    @FunctionalInterface
    public interface GolemSpawner {
        boolean spawnGolem(int x, int y, int z);
    }

    private static final Outcome EMPTY_OUTCOME = new Outcome(List.of(), List.of(), List.of());

    private final VillagerBedClaims bedClaims;
    private final Map<Long, State> states = new HashMap<>();
    /** 이번 틱에서 유도된 시각 행동. 영속되지 않고 매 틱 다시 만들어진다. */
    private final Map<Long, String> visualActions = new HashMap<>();
    private int bedScanCursor;
    private long persistenceRevision;
    private long persistedRevision;

    public record PersistenceSnapshot(long revision,
            List<VillagerSocietySnapshot> states,
            List<VillagerBedClaimSnapshot> bedClaims) {}

    public VillagerSociety() {
        this(new VillagerBedClaims());
    }

    public VillagerSociety(VillagerBedClaims bedClaims) {
        this.bedClaims = bedClaims;
    }

    public VillagerBedClaims bedClaims() {
        return bedClaims;
    }

    /** Store actual items; wheat does not itself count as breeding food. */
    public boolean offerItem(long villagerId, int itemType) {
        if (villagerId <= 0) return false;
        State state = state(villagerId);
        if (VillagerFoodInventory.add(state.foodInventory, VillagerFoodInventory.index(itemType), 1) == 0) return false;
        state.foodPoints = VillagerFoodInventory.points(state.foodInventory);
        markPersistenceDirty();
        return true;
    }

    public int foodPoints(long villagerId) {
        State state = states.get(villagerId);
        return state == null ? 0 : state.foodPoints;
    }

    public long mateOf(long villagerId) {
        State state = states.get(villagerId);
        return state == null ? 0L : state.mateId;
    }

    public boolean hasSlept(long villagerId) {
        State state = states.get(villagerId);
        return state != null && state.lastSleptTick != Long.MIN_VALUE;
    }

    /**
     * 마지막 {@link #tick} 이 유도한 시각 행동("sleep"/"socialize"), 아무것도 아니면 "none".
     * 영속 상태가 아니라 매 틱 위치·일정에서 다시 계산되는 표현 신호다.
     */
    public String visualActionOf(long villagerId) {
        return visualActions.getOrDefault(villagerId, NO_VISUAL_ACTION);
    }

    /** 주민이 죽거나 월드에서 사라질 때 상태·침대 claim 을 함께 정리한다. */
    public void forget(long villagerId) {
        states.remove(villagerId);
        visualActions.remove(villagerId);
        bedClaims.releaseVillager(villagerId);
        for (State state : states.values()) {
            if (state.mateId == villagerId) state.clearCourtship();
        }
        markPersistenceDirty();
    }

    /** 영속 whitelist 로 나가는 정렬된 상태 스냅샷. */
    public List<VillagerSocietySnapshot> snapshot() {
        List<VillagerSocietySnapshot> out = new ArrayList<>(states.size());
        for (Map.Entry<Long, State> entry : states.entrySet()) {
            State state = entry.getValue();
            out.add(new VillagerSocietySnapshot(entry.getKey(), state.foodPoints,
                    state.lastSleptTick, state.golemMemoryTick, state.mateId,
                    state.birthTick, state.courtshipEndTick,
                    VillagerFoodInventory.encode(state.foodInventory)));
        }
        out.sort(Comparator.comparingLong(VillagerSocietySnapshot::villagerId));
        return out;
    }

    public List<VillagerBedClaimSnapshot> bedClaimSnapshot() {
        return bedClaims.snapshot();
    }

    /** Returns no allocation at all for an unchanged stationary checkpoint. */
    public PersistenceSnapshot persistenceSnapshot() {
        if (persistenceRevision == persistedRevision) return null;
        return new PersistenceSnapshot(persistenceRevision, snapshot(), bedClaimSnapshot());
    }

    public void acknowledgePersistence(long revision) {
        if (revision > persistedRevision) persistedRevision = revision;
    }

    public boolean hasUnpersistedPersistence() {
        return persistenceRevision != persistedRevision;
    }

    public void markPersistenceDirty() {
        persistenceRevision++;
    }

    /** reconnect/월드 재적재 복구. 짝이 사라진 구애는 그대로 두고 다음 틱 검증에서 정리된다. */
    public void restore(Collection<VillagerSocietySnapshot> restoredStates,
            Collection<VillagerBedClaimSnapshot> restoredClaims) {
        if (restoredStates != null) {
            for (VillagerSocietySnapshot snapshot : restoredStates) {
                State state = state(snapshot.villagerId());
                state.foodInventory = VillagerFoodInventory.parse(snapshot.foodInventory());
                state.foodPoints = VillagerFoodInventory.points(state.foodInventory);
                state.lastSleptTick = snapshot.lastSleptTick();
                state.golemMemoryTick = snapshot.golemMemoryTick();
                state.mateId = snapshot.mateId();
                state.birthTick = snapshot.birthTick();
                state.courtshipEndTick = snapshot.courtshipEndTick();
            }
        }
        bedClaims.restore(restoredClaims);
        persistedRevision = persistenceRevision;
    }

    /**
     * 활성 청크의 살아 있는 주민만 넘긴다. hibernate 된 주민의 상태는 이 호출로 사라지지 않고
     * {@link #forget(long)} 로만 정리된다.
     */
    public Outcome tick(SocietyWorld world, List<? extends SocietyVillager> villagers,
            ChildSpawner childSpawner, GolemSpawner golemSpawner) {
        visualActions.clear();
        if (villagers == null || villagers.isEmpty()) return EMPTY_OUTCOME;
        List<SocietyVillager> ordered = new ArrayList<>(villagers);
        ordered.sort(Comparator.comparingLong(SocietyVillager::id));
        long gameTime = VillagerSocietyRules.mcGameTime(world.dayCount(), world.worldTime());
        long dayTime = VillagerSocietyRules.mcDayTime(world.worldTime());

        Map<Long, SocietyVillager> byId = new HashMap<>(ordered.size() * 2);
        for (SocietyVillager villager : ordered) byId.put(villager.id(), villager);

        Set<Long> sleeping = maintainBeds(world, ordered, gameTime, dayTime);
        socializePass(ordered, dayTime);
        shareFoodPass(world, ordered, gameTime, dayTime);
        List<Birth> births = new ArrayList<>();
        List<FailedBirth> failedBirths = new ArrayList<>();
        breedingPass(world, ordered, byId, sleeping, gameTime, childSpawner, births, failedBirths);
        List<GolemSummon> golems = golemPass(world, ordered, gameTime, golemSpawner);
        if (births.isEmpty() && golems.isEmpty() && failedBirths.isEmpty()) return EMPTY_OUTCOME;
        return new Outcome(births, golems, failedBirths);
    }

    /**
     * 침대 레인 유지: 부서진 침대 해제, 틱당 한 명의 탐색, REST 일정에서의 수면 기록.
     *
     * @return 이번 틱 자고 있는 주민 id 집합(willing 판정의 awake 를 뒤집는다)
     */
    private Set<Long> maintainBeds(SocietyWorld world, List<SocietyVillager> ordered,
            long gameTime, long dayTime) {
        Set<Long> sleeping = new HashSet<>();
        SocietyVillager scanCandidate = null;
        int scanIndex = -1;
        for (int offset = 0; offset < ordered.size(); offset++) {
            int index = Math.floorMod(bedScanCursor + offset, ordered.size());
            SocietyVillager villager = ordered.get(index);
            int[] bed = bedClaims.bedOf(villager.id());
            if (bed != null && !world.bedBlockAt(bed[0], bed[1], bed[2])) {
                bedClaims.releaseBed(bed[0], bed[1], bed[2]);
                markPersistenceDirty();
                bed = null;
            }
            if (bed == null && scanCandidate == null) {
                scanCandidate = villager;
                scanIndex = index;
            }
        }
        if (scanCandidate != null) {
            bedScanCursor = scanIndex + 1;
            int[] bed = world.nearestVacantBed(
                    (int) Math.floor(scanCandidate.x()),
                    (int) Math.floor(scanCandidate.y()),
                    (int) Math.floor(scanCandidate.z()),
                    (x, y, z) -> world.bedBlockAt(x, y, z) && !bedClaims.claimed(x, y, z));
            if (bed != null && bedClaims.claim(
                    bed[0], bed[1], bed[2], scanCandidate.id())) {
                markPersistenceDirty();
            }
        }
        for (SocietyVillager villager : ordered) {
            int[] bed = bedClaims.bedOf(villager.id());
            if (bed == null) continue;
            if (villager.activity(dayTime) != VillagerBrainRules.Activity.REST) continue;
            if (!VillagerSocietyRules.withinHomeUseRange(villager.x(), villager.y(), villager.z(),
                    bed[0], bed[1], bed[2])) {
                continue;
            }
            State state = state(villager.id());
            if (state.lastSleptTick != gameTime) {
                state.lastSleptTick = gameTime;
                markPersistenceDirty();
            }
            sleeping.add(villager.id());
            visualActions.put(villager.id(), SLEEP_VISUAL_ACTION);
        }
        return sleeping;
    }

    /**
     * MEET 일정에서 이웃과 이야기하고 있는 주민의 표현을 유도한다. 판정 거리는 gossip 상호작용
     * 사거리와 같고, gossip 쿨다운과는 무관하다(대화가 실제로 성사되지 않아도 모여 있는 동안은
     * 계속 어울리는 모습이다). 자고 있는 주민은 REST 일정이라 여기서 걸리지 않는다.
     */
    private void socializePass(List<SocietyVillager> ordered, long dayTime) {
        if (ordered.size() < 2) return;
        for (SocietyVillager villager : ordered) {
            if (villager.activity(dayTime) != VillagerBrainRules.Activity.MEET) continue;
            for (SocietyVillager other : ordered) {
                if (other.id() == villager.id()) continue;
                if (other.activity(dayTime) != VillagerBrainRules.Activity.MEET) continue;
                if (!VillagerGossipRules.withinGossipRange(distanceSquared(villager, other))) {
                    continue;
                }
                visualActions.put(villager.id(), SOCIALIZE_VISUAL_ACTION);
                break;
            }
        }
    }

    private void breedingPass(SocietyWorld world, List<SocietyVillager> ordered,
            Map<Long, SocietyVillager> byId, Set<Long> sleeping, long gameTime,
            ChildSpawner childSpawner, List<Birth> births, List<FailedBirth> failedBirths) {
        Set<Long> paired = new HashSet<>();
        for (SocietyVillager first : ordered) {
            if (paired.contains(first.id())) continue;
            State state = states.get(first.id());
            if (state != null && state.mateId != 0L) {
                SocietyVillager mate = byId.get(state.mateId);
                if (mate == null || paired.contains(mate.id())
                        || !willing(first, sleeping) || !willing(mate, sleeping)
                        || !VillagerBrainRules.mateWithinRange(distanceSquared(first, mate))) {
                    clearCourtship(first.id(), state.mateId);
                    continue;
                }
                paired.add(first.id());
                paired.add(mate.id());
                if (VillagerBrainRules.birthAttemptDue(gameTime, state.birthTick, true)) {
                    attemptBirth(world, first, mate, childSpawner, births, failedBirths);
                    clearCourtship(first.id(), mate.id());
                } else if (gameTime > state.courtshipEndTick) {
                    clearCourtship(first.id(), mate.id());
                }
                continue;
            }
            if (!willing(first, sleeping)) continue;
            SocietyVillager mate = findMate(ordered, byId, paired, sleeping, first);
            if (mate == null) continue;
            long birthTick = gameTime
                    + VillagerBrainRules.birthDelayTicks(world.nextInt(
                            VillagerBrainRules.BIRTH_DELAY_JITTER_SPAN_TICKS));
            long courtshipEnd = gameTime + VillagerBrainRules.matingDurationTicks();
            beginCourtship(first.id(), mate.id(), birthTick, courtshipEnd);
            paired.add(first.id());
            paired.add(mate.id());
        }
    }

    private SocietyVillager findMate(List<SocietyVillager> ordered,
            Map<Long, SocietyVillager> byId, Set<Long> paired, Set<Long> sleeping,
            SocietyVillager first) {
        SocietyVillager best = null;
        double nearest = Double.MAX_VALUE;
        for (SocietyVillager candidate : ordered) {
            if (candidate.id() == first.id() || paired.contains(candidate.id())) continue;
            State candidateState = states.get(candidate.id());
            if (candidateState != null && candidateState.mateId != 0L) continue;
            if (!willing(candidate, sleeping)) continue;
            double distance = distanceSquared(first, candidate);
            if (!VillagerBrainRules.mateWithinRange(distance)) continue;
            if (distance < nearest) {
                nearest = distance;
                best = candidate;
            }
        }
        return best == null ? null : byId.get(best.id());
    }

    private void attemptBirth(SocietyWorld world, SocietyVillager first, SocietyVillager mate,
            ChildSpawner childSpawner, List<Birth> births, List<FailedBirth> failedBirths) {
        // 바닐라와 같이 침대 유무와 무관하게 출산 시각에 음식을 소비한다.
        consumeBreedingFood(first.id());
        consumeBreedingFood(mate.id());
        int[] bed = world.nearestVacantBed(
                (int) Math.floor(first.x()), (int) Math.floor(first.y()),
                (int) Math.floor(first.z()),
                (x, y, z) -> world.bedBlockAt(x, y, z) && !bedClaims.claimed(x, y, z));
        if (bed == null) {
            failedBirths.add(new FailedBirth(first.id(), mate.id()));
            return;
        }
        long childId = childSpawner.spawnChild(first.x(), first.y(), first.z());
        if (childId <= 0) {
            failedBirths.add(new FailedBirth(first.id(), mate.id()));
            return;
        }
        bedClaims.claim(bed[0], bed[1], bed[2], childId);
        state(childId);
        markPersistenceDirty();
        births.add(new Birth(first.id(), mate.id(), childId,
                first.x(), first.y(), first.z(), bed[0], bed[1], bed[2]));
    }

    private List<GolemSummon> golemPass(SocietyWorld world, List<SocietyVillager> ordered,
            long gameTime, GolemSpawner golemSpawner) {
        if (!VillagerBrainRules.golemPanicCheckDue(gameTime)) return List.of();
        List<GolemSummon> summons = null;
        for (SocietyVillager villager : ordered) {
            if (!villager.panicking() || !wantsGolem(villager.id(), gameTime)) continue;
            List<SocietyVillager> nearby = nearbyVillagers(ordered, villager);
            int wanting = 0;
            for (SocietyVillager candidate : nearby) {
                if (!wantsGolem(candidate.id(), gameTime)) continue;
                if (++wanting == VillagerBrainRules.golemNearbyVillagerLimit()) break;
            }
            if (!VillagerBrainRules.golemPanicEligible(wanting)) continue;
            int[] offsets = new int[VillagerSocietyRules.golemSpawnRandomLaneLength()];
            for (int index = 0; index < offsets.length; index++) {
                offsets[index] = world.nextInt(VillagerSocietyRules.GOLEM_SPAWN_XZ_SPREAD * 2 + 1);
            }
            int[] position = VillagerSocietyRules.golemSpawnPosition(world,
                    (int) Math.floor(villager.x()), (int) Math.floor(villager.y()),
                    (int) Math.floor(villager.z()), offsets);
            if (position == null) continue;
            if (!golemSpawner.spawnGolem(position[0], position[1], position[2])) continue;
            for (SocietyVillager candidate : nearby) {
                State state = state(candidate.id());
                if (state.golemMemoryTick != gameTime) {
                    state.golemMemoryTick = gameTime;
                    markPersistenceDirty();
                }
            }
            if (summons == null) summons = new ArrayList<>(1);
            summons.add(new GolemSummon(villager.id(), position[0], position[1], position[2],
                    wanting));
        }
        return summons == null ? List.of() : summons;
    }

    /** {@code getBoundingBox().inflate(10,10,10)} 과 같은 축별 상자다. 자기 자신을 포함한다. */
    private static List<SocietyVillager> nearbyVillagers(List<SocietyVillager> ordered,
            SocietyVillager center) {
        int radius = VillagerBrainRules.golemNearbyVillagerRadiusBlocks();
        List<SocietyVillager> nearby = new ArrayList<>();
        for (SocietyVillager candidate : ordered) {
            if (Math.abs(candidate.x() - center.x()) <= radius
                    && Math.abs(candidate.y() - center.y()) <= radius
                    && Math.abs(candidate.z() - center.z()) <= radius) {
                nearby.add(candidate);
            }
        }
        return nearby;
    }

    private boolean wantsGolem(long villagerId, long gameTime) {
        State state = states.get(villagerId);
        if (state == null) return false;
        boolean memoryPresent = state.golemMemoryTick != Long.MIN_VALUE
                && VillagerBrainRules.golemRecentMemoryActive(gameTime, state.golemMemoryTick);
        return VillagerBrainRules.golemEligible(gameTime, state.lastSleptTick,
                state.lastSleptTick != Long.MIN_VALUE, memoryPresent);
    }

    private boolean willing(SocietyVillager villager, Set<Long> sleeping) {
        if (villager.baby() || villager.breedingCooldown()) return false;
        State state = states.get(villager.id());
        int food = state == null ? 0 : state.foodPoints;
        return VillagerBrainRules.willingToBreed(0, !sleeping.contains(villager.id()), food);
    }

    private static boolean sharingAwake(SocietyVillager v, long dayTime) {
        var activity=v.activity(dayTime);
        return !v.baby() && !v.panicking()
                && (activity == VillagerBrainRules.Activity.IDLE
                    || activity == VillagerBrainRules.Activity.WORK
                    || activity == VillagerBrainRules.Activity.MEET);
    }

    /** Same atomic transfer and inventory accounting as StandaloneVillagerSociety. */
    private void shareFoodPass(SocietyWorld world, List<SocietyVillager> ordered, long gameTime, long dayTime) {
        if (gameTime % 20 != 0) return;
        Set<Long> exchanged = new HashSet<>();
        for (SocietyVillager giver : ordered) {
            if (!sharingAwake(giver, dayTime) || exchanged.contains(giver.id())) continue;
            State source=state(giver.id());
            for (SocietyVillager receiver : ordered) {
                if (giver.id()==receiver.id() || exchanged.contains(receiver.id()) || !sharingAwake(receiver,dayTime)
                        || distanceSquared(giver,receiver)>9 || !foodTransferClear(world,giver,receiver)) continue;
                State target=state(receiver.id());
                boolean shared=false;
                for (int i=0;i<5;i++) {
                    int threshold=i==0?32:i==1?6:24;
                    if(source.foodInventory[i]<=threshold) continue;
                    if(i==0 ? !giver.farmer() || target.foodInventory[0]>=32
                            : !giver.farmer() && target.foodPoints>=12) continue;
                    int accepted=VillagerFoodInventory.add(target.foodInventory,i,source.foodInventory[i]/2);
                    if(accepted==0) continue;
                    source.foodInventory[i]-=accepted;
                    source.foodPoints=VillagerFoodInventory.points(source.foodInventory);
                    target.foodPoints=VillagerFoodInventory.points(target.foodInventory);
                    markPersistenceDirty();
                    shared=true; break;
                }
                if(shared) {
                    exchanged.add(giver.id());
                    exchanged.add(receiver.id());
                    visualActions.put(giver.id(),SOCIALIZE_VISUAL_ACTION);
                    visualActions.put(receiver.id(),SOCIALIZE_VISUAL_ACTION);
                    break;
                }
            }
            if(giver.farmer() && giver.activity(dayTime)==VillagerBrainRules.Activity.WORK && source.foodPoints<12) {
                int bread=Math.min(3,source.foodInventory[0]/3);
                if(bread>0) {
                    int[] candidate=source.foodInventory.clone();
                    candidate[0]-=bread*3;
                    if(VillagerFoodInventory.add(candidate,1,bread)==bread) {
                        source.foodInventory=candidate;
                        source.foodPoints=VillagerFoodInventory.points(candidate);
                        markPersistenceDirty();
                    }
                }
            }
        }
    }

    private static boolean foodTransferClear(SocietyWorld world, SocietyVillager giver, SocietyVillager receiver) {
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(distanceSquared(giver, receiver)) * 4));
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            int block = world.blockAt((int) Math.floor(giver.x() + (receiver.x() - giver.x()) * t),
                    (int) Math.floor(giver.y() + 1 + (receiver.y() - giver.y()) * t),
                    (int) Math.floor(giver.z() + (receiver.z() - giver.z()) * t));
            if (block < 0 || world.solid(block)) return false;
        }
        return true;
    }

    private void consumeBreedingFood(long villagerId) {
        State state = state(villagerId);
        VillagerFoodInventory.consumeBreedingFood(state.foodInventory);
        state.foodPoints = VillagerFoodInventory.points(state.foodInventory);
        markPersistenceDirty();
    }

    private void beginCourtship(long firstId, long secondId, long birthTick,
            long courtshipEndTick) {
        State first = state(firstId);
        State second = state(secondId);
        first.mateId = secondId;
        second.mateId = firstId;
        first.birthTick = birthTick;
        second.birthTick = birthTick;
        first.courtshipEndTick = courtshipEndTick;
        second.courtshipEndTick = courtshipEndTick;
        markPersistenceDirty();
    }

    private void clearCourtship(long firstId, long secondId) {
        State first = states.get(firstId);
        if (first != null) first.clearCourtship();
        State second = states.get(secondId);
        if (second != null) second.clearCourtship();
        if (first != null || second != null) markPersistenceDirty();
    }

    private static double distanceSquared(SocietyVillager left, SocietyVillager right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private State state(long villagerId) {
        State existing = states.get(villagerId);
        if (existing != null) return existing;
        State created = new State();
        states.put(villagerId, created);
        markPersistenceDirty();
        return created;
    }

    private static final class State {
        private int foodPoints;
        private int[] foodInventory = new int[5];
        private long lastSleptTick = Long.MIN_VALUE;
        private long golemMemoryTick = Long.MIN_VALUE;
        private long mateId;
        private long birthTick;
        private long courtshipEndTick;

        private void clearCourtship() {
            mateId = 0L;
            birthTick = 0L;
            courtshipEndTick = 0L;
        }
    }
}
