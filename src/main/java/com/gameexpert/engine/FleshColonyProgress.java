package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure flesh-colony progression/population rules; this class performs no persistence or world edits. */
public final class FleshColonyProgress {
    public static final int ALL_ANCHORS_MASK = 7;
    public static final int HATCH_COOLDOWN_TICKS = 100;
    public static final int ALARM_TELEGRAPH_TICKS = 20;
    public static final int MAX_ALARM_SELECTIONS = 2;
    public static final int GROWTH_INTERVAL_TICKS = 200;
    public static final int GROWTH_COLONIES_PER_STEP = 4;
    public static final double MIN_NATURAL_PLAYER_DISTANCE = 24.0;
    public static final int COLONY_TOTAL_CAP = 12, GHOUL_FAMILY_CAP = 8, BABY_CAP = 2,
            GUARDIAN_CAP = 2, RESIDENT_GLOBAL_CAP = 48;
    public static final String GHOUL = "ghoul", BABY_GHOUL = "baby_ghoul",
            FLESH_STALKER = "flesh_stalker", BONE_PROCESSION = "bone_procession";

    private FleshColonyProgress() { }

    public static State initial() { return new State(0, false, 0); }

    /** Caller atomically commits acted-on cell AIR + next state + reward + one-time receipt. */
    public static TransitionResult severAnchor(State current, int index, long expectedRevision) {
        Objects.requireNonNull(current, "current");
        if (index < 0 || index > 2) return TransitionResult.reject(current, TransitionReject.INVALID_ANCHOR);
        if (expectedRevision != current.revision()) return TransitionResult.reject(current, TransitionReject.STALE_REVISION);
        if (current.coreDestroyed()) return TransitionResult.reject(current, TransitionReject.CORE_DESTROYED);
        int bit = 1 << index;
        if ((current.severedMask() & bit) != 0) return TransitionResult.reject(current, TransitionReject.ALREADY_SEVERED);
        State next = new State(current.severedMask() | bit, false, Math.addExact(current.revision(), 1));
        return TransitionResult.accept(current, next, new RewardIntent(RewardKind.CLOT_SAC, 2));
    }

    /** Caller atomically commits acted-on cell AIR + next state + reward + one-time receipt. */
    public static TransitionResult extractCore(State current, long expectedRevision) {
        Objects.requireNonNull(current, "current");
        if (expectedRevision != current.revision()) return TransitionResult.reject(current, TransitionReject.STALE_REVISION);
        if (current.coreDestroyed()) return TransitionResult.reject(current, TransitionReject.CORE_ALREADY_DESTROYED);
        if (current.severedMask() != ALL_ANCHORS_MASK) return TransitionResult.reject(current, TransitionReject.ANCHORS_REMAIN);
        State next = new State(ALL_ANCHORS_MASK, true, Math.addExact(current.revision(), 1));
        return TransitionResult.accept(current, next, new RewardIntent(RewardKind.CORE, 1));
    }

    /** 0..63 adult ghoul, 64..79 baby ghoul, 80..99 folded/flesh-stalker. */
    public static String ordinaryKindForRoll(int roll) {
        checkRoll(roll);
        return roll < 64 ? GHOUL : roll < 80 ? BABY_GHOUL : FLESH_STALKER;
    }

    public static String largeCocoonGuardianKind() { return BONE_PROCESSION; }

    public static HatchDecision naturalHatch(State colony, int stage, long tick, long lastHatch,
            Double nearestPlayerDistance, boolean largeCocoon, int roll, PopulationCounts counts) {
        requireInputs(colony, counts); checkRoll(roll);
        HatchReject gate = commonGate(colony, stage, tick, lastHatch);
        if (gate != HatchReject.NONE) return HatchDecision.reject(gate);
        if (nearestPlayerDistance == null) return HatchDecision.reject(HatchReject.NO_PLAYER);
        if (!Double.isFinite(nearestPlayerDistance) || nearestPlayerDistance < MIN_NATURAL_PLAYER_DISTANCE) {
            return HatchDecision.reject(HatchReject.PLAYER_TOO_CLOSE);
        }
        return admit(largeCocoon ? BONE_PROCESSION : ordinaryKindForRoll(roll), counts);
    }

    /** Alarm bypasses only player-distance; blocked decisions are one-shot and must not be backlogged. */
    public static HatchDecision alarmHatch(State colony, boolean preexisting, int stage,
            long telegraphTicks, int alreadySelected, long tick, long lastHatch,
            boolean largeCocoon, int roll, PopulationCounts counts) {
        requireInputs(colony, counts); checkRoll(roll);
        HatchReject gate = commonGate(colony, stage, tick, lastHatch);
        if (gate != HatchReject.NONE) return HatchDecision.reject(gate);
        if (!preexisting) return HatchDecision.reject(HatchReject.ALARM_NOT_PREEXISTING);
        if (telegraphTicks < ALARM_TELEGRAPH_TICKS) return HatchDecision.reject(HatchReject.ALARM_TELEGRAPH);
        if (alreadySelected < 0 || alreadySelected >= MAX_ALARM_SELECTIONS) return HatchDecision.reject(HatchReject.ALARM_LIMIT);
        return admit(largeCocoon ? BONE_PROCESSION : ordinaryKindForRoll(roll), counts);
    }

    public static boolean growthEligible(State colony, long tick) {
        Objects.requireNonNull(colony, "colony");
        return !colony.coreDestroyed() && tick >= 0 && tick % GROWTH_INTERVAL_TICKS == 0;
    }

    /** One cooldown snapshot, sequential population reservations; caller commits one event atomically.
     * Rejected candidates are discarded, never placed in a delayed retry queue.
     */
    public static List<HatchDecision> resolveAlarm(State colony, long tick, long lastHatch,
            PopulationCounts counts, List<AlarmCandidate> candidates) {
        PopulationCounts reserved = counts;
        List<HatchDecision> decisions = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            AlarmCandidate candidate = candidates.get(index);
            HatchDecision decision = alarmHatch(colony, candidate.preexisting, candidate.stage,
                    candidate.telegraphTicks, index, tick, lastHatch,
                    candidate.largeCocoon, candidate.roll, reserved);
            decisions.add(decision);
            if (!decision.accepted()) continue;
            boolean baby = BABY_GHOUL.equals(decision.mobKind());
            boolean family = baby || GHOUL.equals(decision.mobKind());
            reserved = new PopulationCounts(reserved.colonyTotal() + 1,
                    reserved.ghoulFamily() + (family ? 1 : 0), reserved.babies() + (baby ? 1 : 0),
                    reserved.guardians() + (BONE_PROCESSION.equals(decision.mobKind()) ? 1 : 0),
                    reserved.residentGlobal() + 1);
        }
        return List.copyOf(decisions);
    }

    public static final class AlarmCandidate {
        private final boolean preexisting, largeCocoon;
        private final int stage, roll;
        private final long telegraphTicks;

        public AlarmCandidate(boolean preexisting, int stage, long telegraphTicks, boolean largeCocoon, int roll) {
            this.preexisting = preexisting;
            this.stage = stage;
            this.telegraphTicks = telegraphTicks;
            this.largeCocoon = largeCocoon;
            this.roll = roll;
        }
    }

    /** Stable-key-only resident scheduling: no coordinates, chunks, or world lookup. */
    public static GrowthSchedule scheduleGrowth(long tick, List<ResidentColony> resident, int cursor) {
        Objects.requireNonNull(resident, "resident");
        if (tick < 0 || tick % GROWTH_INTERVAL_TICKS != 0) return new GrowthSchedule(List.of(), cursor);
        List<ResidentColony> active = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (ResidentColony colony : resident) {
            Objects.requireNonNull(colony, "resident colony");
            if (!seen.add(colony.stableKey())) throw new IllegalArgumentException("duplicate stable key: " + colony.stableKey());
            if (!colony.state().coreDestroyed()) active.add(colony);
        }
        active.sort(Comparator.comparing(ResidentColony::stableKey));
        if (active.isEmpty()) return new GrowthSchedule(List.of(), cursor);
        int first = Math.floorMod(cursor, active.size()), count = Math.min(GROWTH_COLONIES_PER_STEP, active.size());
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) keys.add(active.get((first + i) % active.size()).stableKey());
        return new GrowthSchedule(keys, (first + count) % active.size());
    }

    private static HatchReject commonGate(State state, int stage, long tick, long lastHatch) {
        if (state.coreDestroyed()) return HatchReject.CORE_RETIRED;
        if (stage != 2) return HatchReject.IMMATURE;
        if (tick < 0 || lastHatch >= 0 && tick - lastHatch < HATCH_COOLDOWN_TICKS) return HatchReject.COOLDOWN;
        return HatchReject.NONE;
    }

    public static HatchDecision admitOrdinaryHatch(int roll, PopulationCounts counts) {
        Objects.requireNonNull(counts, "counts");
        return admit(ordinaryKindForRoll(roll), counts);
    }

    public static HatchDecision admitLargeHatch(PopulationCounts counts) {
        Objects.requireNonNull(counts, "counts");
        return admit(BONE_PROCESSION, counts);
    }

    private static HatchDecision admit(String kind, PopulationCounts c) {
        if (c.residentGlobal() >= RESIDENT_GLOBAL_CAP) return HatchDecision.reject(HatchReject.GLOBAL_CAP);
        if (c.colonyTotal() >= COLONY_TOTAL_CAP) return HatchDecision.reject(HatchReject.TOTAL_CAP);
        if (GHOUL.equals(kind)) {
            return c.ghoulFamily() >= GHOUL_FAMILY_CAP ? HatchDecision.reject(HatchReject.GHOUL_FAMILY_CAP)
                    : HatchDecision.accept(GHOUL, false);
        }
        if (BABY_GHOUL.equals(kind)) {
            if (c.ghoulFamily() >= GHOUL_FAMILY_CAP) return HatchDecision.reject(HatchReject.GHOUL_FAMILY_CAP);
            return c.babies() >= BABY_CAP ? HatchDecision.accept(GHOUL, true) : HatchDecision.accept(BABY_GHOUL, false);
        }
        if (FLESH_STALKER.equals(kind)) return HatchDecision.accept(FLESH_STALKER, false);
        if (BONE_PROCESSION.equals(kind)) {
            return c.guardians() >= GUARDIAN_CAP ? HatchDecision.reject(HatchReject.GUARDIAN_CAP)
                    : HatchDecision.accept(BONE_PROCESSION, false);
        }
        throw new IllegalArgumentException("unknown flesh kind");
    }

    private static void checkRoll(int roll) {
        if (roll < 0 || roll > 99) throw new IllegalArgumentException("roll outside 0..99");
    }

    private static void requireInputs(State state, PopulationCounts counts) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(counts, "counts");
    }

    public enum RewardKind { NONE, CLOT_SAC, CORE }
    public enum TransitionReject { NONE, INVALID_ANCHOR, STALE_REVISION, ALREADY_SEVERED,
        CORE_DESTROYED, ANCHORS_REMAIN, CORE_ALREADY_DESTROYED }
    public enum HatchReject { NONE, CORE_RETIRED, IMMATURE, COOLDOWN, NO_PLAYER, PLAYER_TOO_CLOSE,
        GLOBAL_CAP, TOTAL_CAP, GHOUL_FAMILY_CAP, GUARDIAN_CAP, ALARM_NOT_PREEXISTING,
        ALARM_TELEGRAPH, ALARM_LIMIT }

    public static final class State {
        private final int severedMask; private final boolean coreDestroyed; private final long revision;
        public State(int severedMask, boolean coreDestroyed, long revision) {
            if (severedMask < 0 || severedMask > 7) throw new IllegalArgumentException("mask outside 0..7");
            if (revision < 0) throw new IllegalArgumentException("negative revision");
            if (coreDestroyed && severedMask != 7) throw new IllegalArgumentException("destroyed core requires mask7");
            this.severedMask = severedMask; this.coreDestroyed = coreDestroyed; this.revision = revision;
        }
        public int severedMask() { return severedMask; }
        public boolean coreDestroyed() { return coreDestroyed; }
        public long revision() { return revision; }
    }

    public static final class RewardIntent {
        public static final RewardIntent NONE = new RewardIntent(RewardKind.NONE, 0);
        private final RewardKind kind; private final int count;
        public RewardIntent(RewardKind kind, int count) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if ((kind == RewardKind.NONE) != (count == 0) || count < 0) throw new IllegalArgumentException("invalid reward");
            this.count = count;
        }
        public RewardKind kind() { return kind; } public int count() { return count; }
    }

    public static final class TransitionResult {
        private final boolean accepted; private final TransitionReject reject;
        private final State current, next; private final RewardIntent reward;
        private TransitionResult(boolean accepted, TransitionReject reject, State current, State next, RewardIntent reward) {
            this.accepted = accepted; this.reject = reject; this.current = current; this.next = next; this.reward = reward;
        }
        private static TransitionResult accept(State current, State next, RewardIntent reward) {
            return new TransitionResult(true, TransitionReject.NONE, current, next, reward);
        }
        private static TransitionResult reject(State current, TransitionReject reject) {
            return new TransitionResult(false, reject, current, current, RewardIntent.NONE);
        }
        public boolean accepted() { return accepted; } public TransitionReject reject() { return reject; }
        public State current() { return current; } public State next() { return next; }
        public RewardIntent reward() { return reward; }
    }

    public static final class PopulationCounts {
        private final int colonyTotal, ghoulFamily, babies, guardians, residentGlobal;
        public PopulationCounts(int colonyTotal, int ghoulFamily, int babies, int guardians, int residentGlobal) {
            if (colonyTotal < 0 || ghoulFamily < 0 || babies < 0 || guardians < 0 || residentGlobal < 0
                    || babies > ghoulFamily || ghoulFamily > colonyTotal || guardians > colonyTotal
                    || residentGlobal < colonyTotal) throw new IllegalArgumentException("inconsistent population counts");
            this.colonyTotal = colonyTotal; this.ghoulFamily = ghoulFamily; this.babies = babies;
            this.guardians = guardians; this.residentGlobal = residentGlobal;
        }
        public int colonyTotal() { return colonyTotal; } public int ghoulFamily() { return ghoulFamily; }
        public int babies() { return babies; } public int guardians() { return guardians; }
        public int residentGlobal() { return residentGlobal; }
    }

    public static final class HatchDecision {
        private final boolean accepted, babyFallback; private final HatchReject reject; private final String mobKind;
        private HatchDecision(boolean accepted, HatchReject reject, String mobKind, boolean babyFallback) {
            this.accepted = accepted; this.reject = reject; this.mobKind = mobKind; this.babyFallback = babyFallback;
        }
        private static HatchDecision accept(String mobKind, boolean fallback) { return new HatchDecision(true, HatchReject.NONE, mobKind, fallback); }
        private static HatchDecision reject(HatchReject reject) { return new HatchDecision(false, reject, null, false); }
        public boolean accepted() { return accepted; } public HatchReject reject() { return reject; }
        public String mobKind() { return mobKind; } public boolean babyFallbackToAdult() { return babyFallback; }
    }

    public static final class ResidentColony {
        private final String stableKey; private final State state;
        public ResidentColony(String stableKey, State state) {
            if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("blank stable key");
            this.stableKey = stableKey; this.state = Objects.requireNonNull(state, "state");
        }
        public String stableKey() { return stableKey; } public State state() { return state; }
    }

    public static final class GrowthSchedule {
        private final List<String> stableKeys; private final int nextCursor;
        private GrowthSchedule(List<String> stableKeys, int nextCursor) { this.stableKeys = List.copyOf(stableKeys); this.nextCursor = nextCursor; }
        public List<String> stableKeys() { return stableKeys; } public int nextCursor() { return nextCursor; }
    }
}
