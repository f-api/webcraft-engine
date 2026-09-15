package com.gameexpert.engine.raid;

import com.gameexpert.engine.structure.StructureHash;

/**
 * Pure deterministic grammar generator for one village raid.
 *
 * <p>A plan contains only static facts. Runtime state such as the currently executing
 * phase is deliberately not part of this value. Each phase is a node in a bounded
 * forward-only graph with two typed outcomes, and each squad carries its own route,
 * formation, objective, and release offset.</p>
 */
public final class RaidPlanGenerator {
    public static final int MAX_CANDIDATES = 32;
    public static final int MIN_PHASES = 2;
    public static final int MAX_PHASES = 6;
    public static final int MIN_SQUADS_PER_PHASE = 1;
    public static final int MAX_SQUADS_PER_PHASE = 3;

    public static final int APPROACH_NORTH = 1;
    public static final int APPROACH_EAST = 2;
    public static final int APPROACH_SOUTH = 4;
    public static final int APPROACH_WEST = 8;
    public static final int APPROACH_MASK = APPROACH_NORTH | APPROACH_EAST
            | APPROACH_SOUTH | APPROACH_WEST;

    public static final int ROLE_PILLAGER = 0;
    public static final int ROLE_VINDICATOR = 1;
    public static final int ROLE_EVOKER = 2;
    public static final int ROLE_WITCH = 3;
    public static final int ROLE_VEX = 4;
    public static final int ROLE_RAVAGER = 5;
    public static final int ROLE_ILLUSIONER = 6;
    public static final int ROLE_STANDARD_BEARER = 7;
    public static final int ROLE_WEB_TRAPPER = 8;
    public static final int ROLE_BREACHER = 9;
    public static final int ROLE_DEMOLISHER = 10;
    public static final int ROLE_BUILDER = 11;
    public static final int ROLE_COUNT = 12;

    public static final int TACTIC_SCREEN = 0;
    public static final int TACTIC_PINCER = 1;
    public static final int TACTIC_BREACH = 2;
    public static final int TACTIC_SIEGE = 3;
    public static final int TACTIC_REINFORCE = 4;
    public static final int TACTIC_WITHDRAW = 5;
    public static final int TACTIC_FINISH = 6;
    public static final int TACTIC_COUNT = 7;

    public static final int TOPOLOGY_DIRECT = 0;
    public static final int TOPOLOGY_PINCER = 1;
    public static final int TOPOLOGY_BREACH = 2;
    public static final int TOPOLOGY_SIEGE = 3;
    public static final int TOPOLOGY_FEINT = 4;
    public static final int TOPOLOGY_RECOVERY = 5;
    public static final int TOPOLOGY_COUNT = 6;

    public static final int FORMATION_LINE = 0;
    public static final int FORMATION_COLUMN = 1;
    public static final int FORMATION_WEDGE = 2;
    public static final int FORMATION_SPLIT = 3;
    public static final int FORMATION_SCREEN = 4;
    public static final int FORMATION_RING = 5;
    public static final int FORMATION_COUNT = 6;

    public static final int OBJECTIVE_PRESS = 0;
    public static final int OBJECTIVE_DIVERT = 1;
    public static final int OBJECTIVE_BREACH = 2;
    public static final int OBJECTIVE_ISOLATE = 3;
    public static final int OBJECTIVE_RALLY = 4;
    public static final int OBJECTIVE_FINISH = 5;
    public static final int OBJECTIVE_KIND_COUNT = 6;

    public static final int COUNTERPLAY_FOCUS_ASSAULT = 1;
    public static final int COUNTERPLAY_SPLIT_DEFENSE = 2;
    public static final int COUNTERPLAY_BLOCK_ROUTE = 4;
    public static final int COUNTERPLAY_PROTECT_TARGET = 8;
    public static final int COUNTERPLAY_CLEAR_SUPPORT = 16;
    public static final int COUNTERPLAY_HOLD_GROUND = 32;
    public static final int COUNTERPLAY_MASK = COUNTERPLAY_FOCUS_ASSAULT
            | COUNTERPLAY_SPLIT_DEFENSE | COUNTERPLAY_BLOCK_ROUTE
            | COUNTERPLAY_PROTECT_TARGET | COUNTERPLAY_CLEAR_SUPPORT
            | COUNTERPLAY_HOLD_GROUND;

    public static final int OUTCOME_PROGRESS = 0;
    public static final int OUTCOME_REINFORCE = 1;
    public static final int OUTCOME_RETREAT = 2;
    public static final int OUTCOME_COMPLETE = 3;
    public static final int OUTCOME_ABORT = 4;
    public static final int OUTCOME_KIND_COUNT = 5;
    public static final int OUTCOME_EDGE_COUNT = 2;

    public static final int RESULT_ACCEPTED = 0;
    public static final int RESULT_UNPLANNABLE_NO_APPROACH = 1;
    public static final int RESULT_UNPLANNABLE_NO_OBJECTIVE = 2;
    public static final int RESULT_UNPLANNABLE_NO_FEASIBLE_CANDIDATE = 3;

    public static final int REJECTION_NONE = 0;
    public static final int REJECTION_APPROACH = 1;
    public static final int REJECTION_TOTAL_BUDGET = 2;
    public static final int REJECTION_PHASE_BUDGET = 3;
    public static final int REJECTION_PHASE_GRAPH = 4;
    public static final int REJECTION_TIMING = 5;
    public static final int REJECTION_CAP = 6;
    public static final int REJECTION_VEX_PREREQUISITE = 7;
    public static final int REJECTION_MUTATION_TARGET = 8;
    public static final int REJECTION_ALTERNATE_ROUTE = 9;
    public static final int REJECTION_SUPPORT_SIEGE = 10;
    public static final int REJECTION_REACHABILITY = 11;
    public static final int REJECTION_COUNTERPLAY = 12;
    public static final int REJECTION_LIVENESS = 13;
    public static final int REJECTION_REWARD = 14;

    public static final int REWARD_NONE = 0;
    public static final int REWARD_CANDIDATE = 1;

    public static final int EXIT_COMPLETE = -1;
    public static final int EXIT_ABORT = -2;

    private static final int BASE_THREAT_BUDGET = 24;
    private static final int THREAT_BUDGET_PER_DEFENDER = 10;
    private static final int THREAT_BUDGET_PER_POWER = 4;
    private static final int MAX_TOTAL_THREAT_BUDGET = 4096;
    private static final int MAX_GLOBAL_ROLE_COUNT = 32;
    private static final int MAX_PHASE_ROLE_COUNT = 8;
    private static final int MAX_TOTAL_UNITS = 96;
    private static final int MAX_PHASE_UNITS = 24;
    private static final int MAX_PHASE_THREAT = 96;
    private static final int MAX_SUPPORT_PER_PHASE = 4;
    private static final int MAX_SIEGE_PER_PHASE = 3;
    private static final int MIN_RESPONSE_TICKS = 10;
    private static final int MIN_PHASE_GAP_TICKS = 8;
    private static final int REWARD_CHANCE_PERCENT = 4;
    private static final int PILLAGER_THREAT = 5;

    /* Each semantic family starts from its own salt and never consumes another lane. */
    private static final int TAG_ROOT = 0x17a4c2d1;
    private static final int TAG_WORLD_SEED_HIGH = 0x4e8b31a7;
    private static final int TAG_WORLD_SEED_LOW = 0x6c21d5b9;
    private static final int TAG_RAID_ID_HIGH = 0x2f93a46d;
    private static final int TAG_RAID_ID_LOW = 0x93d4e7b1;
    private static final int TAG_SITE_KEY_HIGH = 0xa16b2f43;
    private static final int TAG_SITE_KEY_LOW = 0xc8f05927;
    private static final int TAG_PHASE_COUNT = 0x1c7935ab;
    private static final int TAG_TACTIC = 0x71c8e205;
    private static final int TAG_OUTCOME = 0x35af7d91;
    private static final int TAG_APPROACH = 0x5bd214e3;
    private static final int TAG_TOPOLOGY = 0x84e6a137;
    private static final int TAG_FORMATION = 0x29d4f06b;
    private static final int TAG_OBJECTIVE = 0x7e21b493;
    private static final int TAG_COUNTERPLAY = 0x43c7d159;
    private static final int TAG_TIMING_START = 0x18f2ab67;
    private static final int TAG_TIMING_TELEGRAPH = 0x56d93ce1;
    private static final int TAG_TIMING_RELEASE = 0x6f04b8ad;
    private static final int TAG_TIMING_DEADLINE = 0x2b71e4c9;
    private static final int TAG_SQUAD_COUNT = 0x79a5c213;
    private static final int TAG_SQUAD_APPROACH = 0x3e86d17b;
    private static final int TAG_SQUAD_FORMATION = 0x61b2f49d;
    private static final int TAG_SQUAD_OBJECTIVE = 0x4c9e2075;
    private static final int TAG_RELEASE_OFFSET = 0x73d518ab;
    private static final int TAG_ROLE = 0x26e4a791;
    private static final int TAG_REWARD_DECISION = 0x58c1d36f;
    private static final int TAG_REWARD_INDEX = 0x3a7f92c5;
    private static final int TAG_REWARD_TOKEN = 0x69b4e81d;
    private static final int TAG_TRACE = 0x14d7c3a9;
    private static final int TAG_TRACE_ATTEMPT = 0x52e6b17f;
    private static final int TAG_PLAN_HASH = 0x7c09d245;
    private static final int TAG_UNPLANNABLE = 0x45a8d3f1;

    private static final int STRUCTURAL_SEED_HIGH = 0x3b1f5a7d;
    private static final int STRUCTURAL_SEED_LOW = 0x6e2c9413;
    private static final int STRUCTURAL_PLAN = 0x15a7c3e9;
    private static final int STRUCTURAL_TOTAL_THREAT = 0x4a1c97e3;
    private static final int STRUCTURAL_TOTAL_UNITS = 0x73e5b219;
    private static final int STRUCTURAL_ROLE = 0x1f4d86a7;
    private static final int STRUCTURAL_PHASE = 0x5b92e7c1;
    private static final int STRUCTURAL_OUTCOME = 0x68c13f5d;
    private static final int STRUCTURAL_SQUAD = 0x27a4d98b;
    private static final int STRUCTURAL_REWARD = 0x46e2b17d;

    private RaidPlanGenerator() {}

    /** Immutable admission facts used by the grammar generator. */
    public static final class Input {
        private final int generatorVersion;
        private final long worldSeed;
        private final long raidId;
        private final long villageSiteKey;
        private final int defenderCount;
        private final int defenderPower;
        private final int reachableApproachMask;
        private final int objectiveCount;
        private final int safeMutationTargetCount;
        private final boolean alternateRouteAvailable;
        private final int registeredRewardCandidateCount;

        public Input(int generatorVersion, long worldSeed, long raidId, long villageSiteKey,
                int defenderCount, int defenderPower, int reachableApproachMask,
                int objectiveCount, int safeMutationTargetCount,
                boolean alternateRouteAvailable, int registeredRewardCandidateCount) {
            this.generatorVersion = generatorVersion;
            this.worldSeed = worldSeed;
            this.raidId = raidId;
            this.villageSiteKey = villageSiteKey;
            this.defenderCount = defenderCount;
            this.defenderPower = defenderPower;
            this.reachableApproachMask = reachableApproachMask & APPROACH_MASK;
            this.objectiveCount = objectiveCount;
            this.safeMutationTargetCount = safeMutationTargetCount;
            this.alternateRouteAvailable = alternateRouteAvailable;
            this.registeredRewardCandidateCount = registeredRewardCandidateCount;
        }

        public int generatorVersion() { return generatorVersion; }
        public long worldSeed() { return worldSeed; }
        public long raidId() { return raidId; }
        public long villageSiteKey() { return villageSiteKey; }
        public int defenderCount() { return defenderCount; }
        public int defenderPower() { return defenderPower; }
        public int reachableApproachMask() { return reachableApproachMask; }
        public int objectiveCount() { return objectiveCount; }
        public int safeMutationTargetCount() { return safeMutationTargetCount; }
        public boolean alternateRouteAvailable() { return alternateRouteAvailable; }
        public int registeredRewardCandidateCount() { return registeredRewardCandidateCount; }
    }

    /** Generate one accepted plan or a fact-specific unplannable result. */
    public static Plan generate(Input input) {
        if (input == null) throw new IllegalArgumentException("input");

        int root = rootSeed(input);
        if (input.reachableApproachMask() == 0) {
            return unplannable(input, root, RESULT_UNPLANNABLE_NO_APPROACH);
        }
        if (input.objectiveCount() <= 0) {
            return unplannable(input, root, RESULT_UNPLANNABLE_NO_OBJECTIVE);
        }

        int budget = threatBudget(input);
        CandidateScratch candidate = new CandidateScratch();
        int trace = lane(root, TAG_TRACE, 0, 0);
        for (int attempt = 0; attempt < MAX_CANDIDATES; attempt++) {
            boolean fallback = attempt == MAX_CANDIDATES - 1;
            buildCandidate(input, root, budget, attempt, fallback, candidate);
            int rejection = validateCandidate(input, candidate, budget);
            trace = StructureHash.mix32(trace ^ lane(root, TAG_TRACE_ATTEMPT, attempt, rejection)
                    ^ candidate.phaseCount ^ candidate.totalThreat);
            if (rejection == REJECTION_NONE) {
                return materialize(input, root, trace, attempt, fallback, candidate, budget);
            }
        }
        return unplannable(input, root, RESULT_UNPLANNABLE_NO_FEASIBLE_CANDIDATE,
                trace, MAX_CANDIDATES);
    }

    /** Return a stateless semantic lane for tests and deterministic callers. */
    public static int semanticLane(Input input, int semanticTag) {
        if (input == null) throw new IllegalArgumentException("input");
        return lane(rootSeed(input), semanticTag, 0, 0);
    }

    /** Indexed form of {@link #semanticLane(Input, int)}. */
    public static int semanticLane(Input input, int semanticTag, int index0, int index1) {
        if (input == null) throw new IllegalArgumentException("input");
        return lane(rootSeed(input), semanticTag, index0, index1);
    }

    /**
     * Return an allocation-free fingerprint of only the materialized raid structure.
     *
     * <p>Identity, generator version, trace and attempt metadata are intentionally absent.
     * The reward token is also absent: it identifies a grant instance, not raid structure.
     * Reward decision and candidate index are part of the structure because they select the
     * reward outcome exposed by this plan.</p>
     */
    public static long structuralFingerprint(Plan plan) {
        if (plan == null) throw new IllegalArgumentException("plan");

        long fingerprint = (Integer.toUnsignedLong(STRUCTURAL_SEED_HIGH) << 32)
                | Integer.toUnsignedLong(STRUCTURAL_SEED_LOW);
        fingerprint = structuralMix(fingerprint, STRUCTURAL_PLAN, plan.phases.length);
        fingerprint = structuralMix(fingerprint, STRUCTURAL_TOTAL_THREAT, plan.totalThreat);
        fingerprint = structuralMix(fingerprint, STRUCTURAL_TOTAL_UNITS, plan.totalUnitCount);
        fingerprint = structuralMix(fingerprint, STRUCTURAL_ROLE, plan.roleCounts.length);
        for (int role = 0; role < plan.roleCounts.length; role++) {
            fingerprint = structuralMix(fingerprint, STRUCTURAL_ROLE, plan.roleCounts[role]);
        }
        fingerprint = structuralMix(fingerprint, STRUCTURAL_REWARD, plan.rewardDecision);
        fingerprint = structuralMix(fingerprint, STRUCTURAL_REWARD, plan.rewardIndex);

        for (int phaseIndex = 0; phaseIndex < plan.phases.length; phaseIndex++) {
            Phase phase = plan.phases[phaseIndex];
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.phaseIndex);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.tactic);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.approachMask);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.topology);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.formation);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.objective);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.objectiveTarget);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.counterplay);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.startTick);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.telegraphTick);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.releaseTick);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.deadlineTick);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.threat);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_PHASE, phase.unitCount);
            fingerprint = structuralMix(fingerprint, STRUCTURAL_OUTCOME, phase.outcomes.length);
            for (int edge = 0; edge < phase.outcomes.length; edge++) {
                Outcome outcome = phase.outcomes[edge];
                fingerprint = structuralMix(fingerprint, STRUCTURAL_OUTCOME, outcome.kind);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_OUTCOME, outcome.nextPhase);
            }
            fingerprint = structuralMix(fingerprint, STRUCTURAL_ROLE, phase.roleCounts.length);
            for (int role = 0; role < phase.roleCounts.length; role++) {
                fingerprint = structuralMix(fingerprint, STRUCTURAL_ROLE, phase.roleCounts[role]);
            }
            fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, phase.squads.length);
            for (int squadIndex = 0; squadIndex < phase.squads.length; squadIndex++) {
                Squad squad = phase.squads[squadIndex];
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.phaseIndex);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.squadIndex);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.approach);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.formation);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.objective);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD,
                        squad.objectiveTarget);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD,
                        squad.releaseOffset);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.threat);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_SQUAD, squad.unitCount);
                fingerprint = structuralMix(fingerprint, STRUCTURAL_ROLE, squad.roleCounts.length);
                for (int role = 0; role < squad.roleCounts.length; role++) {
                    fingerprint = structuralMix(fingerprint, STRUCTURAL_ROLE,
                            squad.roleCounts[role]);
                }
            }
        }
        return fingerprint;
    }

    private static long structuralMix(long fingerprint, int fieldTag, int value) {
        int high = StructureHash.mix32((int) (fingerprint >>> 32)
                ^ fieldTag ^ value * 0x9e3779b9);
        int low = StructureHash.mix32((int) fingerprint
                ^ fieldTag * 0x85ebca6b ^ Integer.rotateLeft(value, 13));
        return (Integer.toUnsignedLong(high) << 32) | Integer.toUnsignedLong(low);
    }

    /** Validate a materialized plan at an API boundary. */
    public static boolean isValid(Input input, Plan plan) {
        if (input == null || plan == null) return false;
        if (!plan.plannable) {
            return plan.resultCode != RESULT_ACCEPTED
                    && plan.acceptedAttempt == -1
                    && plan.phases.length == 0
                    && allZero(plan.roleCounts)
                    && plan.totalThreat == 0
                    && plan.totalUnitCount == 0;
        }
        if (plan.resultCode != RESULT_ACCEPTED
                || input.reachableApproachMask() == 0
                || input.objectiveCount() <= 0
                || plan.phases.length < MIN_PHASES
                || plan.phases.length > MAX_PHASES
                || plan.acceptedAttempt < 0
                || plan.acceptedAttempt >= MAX_CANDIDATES
                || plan.attemptsEvaluated < plan.acceptedAttempt + 1
                || plan.attemptsEvaluated > MAX_CANDIDATES
                || plan.totalBudget != threatBudget(input)
                || plan.roleCounts.length != ROLE_COUNT) {
            return false;
        }

        int totalThreat = 0;
        int totalUnits = 0;
        int mutationTargetsUsed = 0;
        int seenEvoker = 0;
        int reachablePhases = 1;
        for (int role = 0; role < ROLE_COUNT; role++) {
            if (plan.roleCounts[role] < 0 || plan.roleCounts[role] > MAX_GLOBAL_ROLE_COUNT) {
                return false;
            }
        }

        for (int phaseIndex = 0; phaseIndex < plan.phases.length; phaseIndex++) {
            Phase phase = plan.phases[phaseIndex];
            if (!validPhaseFacts(input, phase, phaseIndex, plan.phases.length)) return false;
            if (phaseIndex > 0
                    && phase.startTick < plan.phases[phaseIndex - 1].deadlineTick
                    + MIN_PHASE_GAP_TICKS) return false;
            if ((reachablePhases & (1 << phaseIndex)) == 0) return false;
            for (int edgeIndex = 0; edgeIndex < OUTCOME_EDGE_COUNT; edgeIndex++) {
                Outcome outcome = phase.outcomes[edgeIndex];
                if (outcome.nextPhase >= 0) reachablePhases |= 1 << outcome.nextPhase;
            }

            int phaseThreat = 0;
            int phaseUnits = 0;
            int support = 0;
            int siege = 0;
            int assault = 0;
            for (int squadIndex = 0; squadIndex < phase.squads.length; squadIndex++) {
                Squad squad = phase.squads[squadIndex];
                if (!validSquadFacts(input, phase, squad, phaseIndex, squadIndex)) return false;
                int squadThreat = 0;
                int squadUnits = 0;
                for (int role = 0; role < ROLE_COUNT; role++) {
                    int count = squad.roleCounts[role];
                    if (count < 0 || count > MAX_PHASE_ROLE_COUNT) return false;
                    squadUnits += count;
                    squadThreat += count * threatOf(role);
                }
                if (squadUnits <= 0 || squadUnits > MAX_PHASE_UNITS
                        || squadUnits != squad.unitCount || squadThreat != squad.threat) {
                    return false;
                }
                phaseUnits += squadUnits;
                phaseThreat += squadThreat;
            }
            for (int role = 0; role < ROLE_COUNT; role++) {
                int summed = 0;
                for (int squadIndex = 0; squadIndex < phase.squads.length; squadIndex++) {
                    summed += phase.squads[squadIndex].roleCounts[role];
                }
                if (phase.roleCounts[role] != summed || summed > MAX_PHASE_ROLE_COUNT) {
                    return false;
                }
                if (isSupportRole(role)) support += summed;
                else if (isSiegeRole(role)) siege += summed;
                else assault += summed;
            }
            if (!boundedSupportSiege(support, siege, assault)) return false;
            if (phaseThreat != phase.threat || phaseUnits != phase.unitCount
                    || phaseThreat > MAX_PHASE_THREAT || phaseUnits > MAX_PHASE_UNITS) {
                return false;
            }
            if (input.safeMutationTargetCount() <= 0
                    && (phase.roleCounts[ROLE_BUILDER] > 0
                    || phase.roleCounts[ROLE_DEMOLISHER] > 0)) return false;
            mutationTargetsUsed += phase.roleCounts[ROLE_BUILDER]
                    + phase.roleCounts[ROLE_DEMOLISHER];
            if (mutationTargetsUsed > safeMutationTargets(input)) return false;
            if (phase.roleCounts[ROLE_EVOKER] > 0) seenEvoker += phase.roleCounts[ROLE_EVOKER];
            if (phase.roleCounts[ROLE_VEX] > 0 && seenEvoker <= 0) return false;

            totalThreat += phaseThreat;
            totalUnits += phaseUnits;
            if (totalThreat > plan.totalBudget || totalUnits > MAX_TOTAL_UNITS) return false;
        }
        if (reachablePhases != (1 << plan.phases.length) - 1) return false;
        for (int role = 0; role < ROLE_COUNT; role++) {
            int summed = 0;
            for (int phaseIndex = 0; phaseIndex < plan.phases.length; phaseIndex++) {
                summed += plan.phases[phaseIndex].roleCounts[role];
            }
            if (summed != plan.roleCounts[role]) return false;
        }
        if (totalThreat != plan.totalThreat || totalUnits != plan.totalUnitCount
                || !validReward(input, plan.rewardDecision, plan.rewardIndex, plan.rewardToken)) {
            return false;
        }
        return true;
    }

    public static int roleIdCount() { return ROLE_COUNT; }

    /** Stable role ID lookup without exposing an allocating name table. */
    public static String roleId(int role) {
        return switch (role) {
            case ROLE_PILLAGER -> "Pillager";
            case ROLE_VINDICATOR -> "Vindicator";
            case ROLE_EVOKER -> "Evoker";
            case ROLE_WITCH -> "Witch";
            case ROLE_VEX -> "Vex";
            case ROLE_RAVAGER -> "Ravager";
            case ROLE_ILLUSIONER -> "Illusioner";
            case ROLE_STANDARD_BEARER -> "Standard Bearer";
            case ROLE_WEB_TRAPPER -> "Web Trapper";
            case ROLE_BREACHER -> "Breacher";
            case ROLE_DEMOLISHER -> "Demolisher";
            case ROLE_BUILDER -> "Builder";
            default -> throw new IllegalArgumentException("role=" + role);
        };
    }

    private static void buildCandidate(Input input, int root, int budget, int attempt,
            boolean fallback, CandidateScratch candidate) {
        candidate.clear();
        int maxPhases = budget / PILLAGER_THREAT;
        if (maxPhases < MIN_PHASES) maxPhases = MIN_PHASES;
        if (maxPhases > MAX_PHASES) maxPhases = MAX_PHASES;
        candidate.phaseCount = fallback
                ? MIN_PHASES
                : MIN_PHASES + unsignedMod(lane(root, TAG_PHASE_COUNT, attempt, 0),
                maxPhases - MIN_PHASES + 1);

        buildGraph(root, attempt, candidate);
        buildPhaseGrammar(input, root, attempt, fallback, candidate);
        buildSquadsAndRoles(input, root, budget, attempt, fallback, candidate);
        chooseReward(input, root, candidate);
    }

    private static void buildGraph(int root, int attempt, CandidateScratch candidate) {
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            int outcomeOffset = phase * OUTCOME_EDGE_COUNT;
            if (phase == candidate.phaseCount - 1) {
                candidate.outcomeKind[outcomeOffset] = OUTCOME_COMPLETE;
                candidate.outcomeTarget[outcomeOffset] = EXIT_COMPLETE;
                candidate.outcomeKind[outcomeOffset + 1] = OUTCOME_ABORT;
                candidate.outcomeTarget[outcomeOffset + 1] = EXIT_ABORT;
            } else {
                candidate.outcomeKind[outcomeOffset] = OUTCOME_PROGRESS;
                candidate.outcomeTarget[outcomeOffset] = phase + 1;
                if (phase + 2 < candidate.phaseCount) {
                    int branch = unsignedMod(lane(root, TAG_OUTCOME, attempt + phase, 0), 2);
                    candidate.outcomeKind[outcomeOffset + 1] = branch == 0
                            ? OUTCOME_REINFORCE : OUTCOME_RETREAT;
                    candidate.outcomeTarget[outcomeOffset + 1] = phase + 2;
                } else {
                    int branch = unsignedMod(lane(root, TAG_OUTCOME, attempt + phase, 1), 2);
                    candidate.outcomeKind[outcomeOffset + 1] = branch == 0
                            ? OUTCOME_REINFORCE : OUTCOME_RETREAT;
                    candidate.outcomeTarget[outcomeOffset + 1] = phase + 1;
                }
            }
        }
    }

    private static void buildPhaseGrammar(Input input, int root, int attempt,
            boolean fallback, CandidateScratch candidate) {
        int previousDeadline = 0;
        int cursor = fallback ? 0 : unsignedMod(lane(root, TAG_TIMING_START, attempt, 0), 72);
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            int tactic = tacticFor(root, attempt, candidate, phase);
            candidate.phaseTactic[phase] = tactic;
            candidate.phaseTopology[phase] = topologyFor(tactic);
            candidate.phaseFormation[phase] = phaseFormation(tactic,
                    lane(root, TAG_FORMATION, attempt, phase));
            candidate.phaseObjective[phase] = objectiveFor(tactic,
                    lane(root, TAG_OBJECTIVE, attempt, phase));
            candidate.phaseObjectiveTarget[phase] = unsignedMod(
                    lane(root, TAG_OBJECTIVE, attempt + phase, 1), input.objectiveCount());
            candidate.phaseCounterplay[phase] = counterplayFor(tactic,
                    lane(root, TAG_COUNTERPLAY, attempt, phase));

            int reachable = input.reachableApproachMask();
            int reachableCount = Integer.bitCount(reachable);
            int desiredAxes = 1;
            if (!fallback && reachableCount > 1
                    && (tactic == TACTIC_PINCER
                    || unsignedMod(lane(root, TAG_APPROACH, attempt + phase, 1), 100) < 58)) {
                desiredAxes = reachableCount > 2
                        && unsignedMod(lane(root, TAG_APPROACH, attempt + phase, 2), 4) == 0
                        ? 3 : 2;
            }
            candidate.phaseApproachMask[phase] = chooseAxes(reachable,
                    lane(root, TAG_APPROACH, attempt + phase, 0), desiredAxes);

            int startGap = phase == 0 ? cursor : MIN_PHASE_GAP_TICKS
                    + unsignedMod(lane(root, TAG_TIMING_START, attempt + phase, 1), 72);
            int start = phase == 0 ? startGap : previousDeadline + startGap;
            int telegraph = start + (fallback ? 12 : 8
                    + unsignedMod(lane(root, TAG_TIMING_TELEGRAPH, attempt + phase, 0), 28));
            int release = telegraph + MIN_RESPONSE_TICKS
                    + (fallback ? 0 : unsignedMod(lane(root, TAG_TIMING_RELEASE,
                    attempt + phase, 0), 36));
            int deadline = release + (fallback ? 72 : 78
                    + unsignedMod(lane(root, TAG_TIMING_DEADLINE, attempt + phase, 0), 188));
            candidate.phaseStartTick[phase] = start;
            candidate.telegraphTick[phase] = telegraph;
            candidate.releaseTick[phase] = release;
            candidate.deadlineTick[phase] = deadline;
            previousDeadline = deadline;
            cursor = deadline + MIN_PHASE_GAP_TICKS;
        }
    }

    private static void buildSquadsAndRoles(Input input, int root, int budget, int attempt,
            boolean fallback, CandidateScratch candidate) {
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            int remainingPhases = candidate.phaseCount - phase - 1;
            int reservedFuture = remainingPhases * PILLAGER_THREAT;
            int maxSquads = (budget - candidate.totalThreat - reservedFuture) / PILLAGER_THREAT;
            if (maxSquads < 1) maxSquads = 1;
            if (maxSquads > MAX_SQUADS_PER_PHASE) maxSquads = MAX_SQUADS_PER_PHASE;

            int desired = fallback ? 1 : MIN_SQUADS_PER_PHASE + unsignedMod(
                    lane(root, TAG_SQUAD_COUNT, attempt + phase, 0), MAX_SQUADS_PER_PHASE);
            if (Integer.bitCount(candidate.phaseApproachMask[phase]) > 1 && desired < 2
                    && maxSquads >= 2) desired = 2;
            int squadCount = desired < maxSquads ? desired : maxSquads;
            if (squadCount < 1) squadCount = 1;
            candidate.squadCount[phase] = squadCount;
            if (Integer.bitCount(candidate.phaseApproachMask[phase]) > squadCount) {
                candidate.phaseApproachMask[phase] = chooseAxes(
                        candidate.phaseApproachMask[phase],
                        lane(root, TAG_APPROACH, attempt + phase, 3), squadCount);
            }

            for (int squad = 0; squad < squadCount; squad++) {
                int squadIndex = phase * MAX_SQUADS_PER_PHASE + squad;
                int squadOffset = squadOffset(phase, squad);
                int approach;
                if (squad > 0 && Integer.bitCount(candidate.phaseApproachMask[phase]) > 1) {
                    approach = nthAxis(candidate.phaseApproachMask[phase], squad
                            % Integer.bitCount(candidate.phaseApproachMask[phase]));
                    if (approach == 0) approach = chooseAxis(candidate.phaseApproachMask[phase],
                            lane(root, TAG_SQUAD_APPROACH, phase, squad));
                } else if (Integer.bitCount(candidate.phaseApproachMask[phase]) > 1) {
                    approach = nthAxis(candidate.phaseApproachMask[phase], 0);
                } else {
                    approach = chooseAxis(candidate.phaseApproachMask[phase],
                            lane(root, TAG_SQUAD_APPROACH, attempt + phase, squad));
                }
                candidate.squadApproach[squadIndex] = approach;
                candidate.squadFormation[squadIndex] = squadFormation(
                        candidate.phaseTactic[phase], squad,
                        lane(root, TAG_SQUAD_FORMATION, attempt + phase, squad));
                candidate.squadObjective[squadIndex] = squadObjective(
                        candidate.phaseTactic[phase], squad);
                candidate.squadObjectiveTarget[squadIndex] = unsignedMod(
                        lane(root, TAG_SQUAD_OBJECTIVE, attempt + phase, squad),
                        input.objectiveCount());
                candidate.squadReleaseOffset[squadIndex] = squad == 0 ? 0
                        : (fallback ? 8 * squad : 6 + unsignedMod(
                        lane(root, TAG_RELEASE_OFFSET, attempt + phase, squad), 44));

                addRole(candidate, phase, squad, ROLE_PILLAGER, 1);
            }

            for (int squad = 0; squad < squadCount; squad++) {
                int slot0 = unsignedMod(lane(root, TAG_ROLE, attempt + phase * 7, squad * 3), 6);
                int role0 = roleFor(candidate.phaseTactic[phase], slot0);
                tryAddCompatibleRole(input, candidate, phase, squad, role0,
                        budget, remainingPhases);
                int slot1 = unsignedMod(lane(root, TAG_ROLE, attempt + phase * 11,
                        squad * 3 + 1), 6);
                int role1 = roleFor(candidate.phaseTactic[phase], slot1 + 2);
                if (role1 != role0) {
                    tryAddCompatibleRole(input, candidate, phase, squad, role1,
                            budget, remainingPhases);
                }
            }
        }
    }

    private static int tacticFor(int root, int attempt, CandidateScratch candidate, int phase) {
        if (phase == candidate.phaseCount - 1) return TACTIC_FINISH;
        boolean retreatIncoming = false;
        boolean reinforceIncoming = false;
        for (int previous = 0; previous < phase; previous++) {
            int offset = previous * OUTCOME_EDGE_COUNT;
            if (candidate.outcomeTarget[offset] == phase) {
                retreatIncoming |= candidate.outcomeKind[offset] == OUTCOME_RETREAT;
                reinforceIncoming |= candidate.outcomeKind[offset] == OUTCOME_REINFORCE;
            }
            if (candidate.outcomeTarget[offset + 1] == phase) {
                retreatIncoming |= candidate.outcomeKind[offset + 1] == OUTCOME_RETREAT;
                reinforceIncoming |= candidate.outcomeKind[offset + 1] == OUTCOME_REINFORCE;
            }
        }
        if (retreatIncoming) return TACTIC_WITHDRAW;
        if (reinforceIncoming) return TACTIC_REINFORCE;
        if (phase == 0) {
            return unsignedMod(lane(root, TAG_TACTIC, attempt, phase), 3);
        }
        return unsignedMod(lane(root, TAG_TACTIC, attempt, phase), 2) == 0
                ? TACTIC_BREACH : TACTIC_SIEGE;
    }

    private static int topologyFor(int tactic) {
        return switch (tactic) {
            case TACTIC_SCREEN -> TOPOLOGY_DIRECT;
            case TACTIC_PINCER -> TOPOLOGY_PINCER;
            case TACTIC_BREACH -> TOPOLOGY_BREACH;
            case TACTIC_SIEGE -> TOPOLOGY_SIEGE;
            case TACTIC_REINFORCE -> TOPOLOGY_FEINT;
            case TACTIC_WITHDRAW -> TOPOLOGY_RECOVERY;
            case TACTIC_FINISH -> TOPOLOGY_BREACH;
            default -> -1;
        };
    }

    private static int objectiveFor(int tactic, int lane) {
        return switch (tactic) {
            case TACTIC_SCREEN -> unsignedMod(lane, 2) == 0
                    ? OBJECTIVE_PRESS : OBJECTIVE_DIVERT;
            case TACTIC_PINCER -> unsignedMod(lane, 2) == 0
                    ? OBJECTIVE_ISOLATE : OBJECTIVE_DIVERT;
            case TACTIC_BREACH, TACTIC_SIEGE -> OBJECTIVE_BREACH;
            case TACTIC_REINFORCE -> unsignedMod(lane, 2) == 0
                    ? OBJECTIVE_PRESS : OBJECTIVE_ISOLATE;
            case TACTIC_WITHDRAW -> OBJECTIVE_RALLY;
            case TACTIC_FINISH -> OBJECTIVE_FINISH;
            default -> -1;
        };
    }

    private static int counterplayFor(int tactic, int lane) {
        int required = switch (tactic) {
            case TACTIC_SCREEN -> COUNTERPLAY_FOCUS_ASSAULT;
            case TACTIC_PINCER -> COUNTERPLAY_SPLIT_DEFENSE;
            case TACTIC_BREACH, TACTIC_SIEGE -> COUNTERPLAY_CLEAR_SUPPORT;
            case TACTIC_REINFORCE -> COUNTERPLAY_PROTECT_TARGET;
            case TACTIC_WITHDRAW -> COUNTERPLAY_HOLD_GROUND;
            case TACTIC_FINISH -> COUNTERPLAY_FOCUS_ASSAULT;
            default -> 0;
        };
        int optional = 1 << unsignedMod(lane, 6);
        return required | optional;
    }

    private static int squadFormation(int tactic, int squad, int lane) {
        if (tactic == TACTIC_PINCER) return squad == 0 ? FORMATION_WEDGE : FORMATION_SPLIT;
        if (tactic == TACTIC_WITHDRAW) return squad == 0 ? FORMATION_SCREEN : FORMATION_COLUMN;
        if (tactic == TACTIC_BREACH || tactic == TACTIC_SIEGE) {
            return squad == 0 ? FORMATION_COLUMN : FORMATION_WEDGE;
        }
        return unsignedMod(lane, FORMATION_COUNT);
    }

    private static int phaseFormation(int tactic, int lane) {
        return switch (tactic) {
            case TACTIC_SCREEN -> FORMATION_SCREEN;
            case TACTIC_PINCER -> FORMATION_SPLIT;
            case TACTIC_BREACH, TACTIC_SIEGE -> FORMATION_COLUMN;
            case TACTIC_REINFORCE -> FORMATION_WEDGE;
            case TACTIC_WITHDRAW -> FORMATION_RING;
            case TACTIC_FINISH -> unsignedMod(lane, 2) == 0
                    ? FORMATION_LINE : FORMATION_WEDGE;
            default -> -1;
        };
    }

    private static int squadObjective(int tactic, int squad) {
        return switch (tactic) {
            case TACTIC_PINCER -> squad == 0 ? OBJECTIVE_ISOLATE : OBJECTIVE_DIVERT;
            case TACTIC_BREACH, TACTIC_SIEGE -> squad == 0
                    ? OBJECTIVE_BREACH : OBJECTIVE_PRESS;
            case TACTIC_WITHDRAW -> OBJECTIVE_RALLY;
            case TACTIC_FINISH -> OBJECTIVE_FINISH;
            default -> squad == 0 ? OBJECTIVE_PRESS : OBJECTIVE_DIVERT;
        };
    }

    private static int roleFor(int tactic, int slot) {
        int normalized = slot % 6;
        return switch (tactic) {
            case TACTIC_SCREEN -> switch (normalized) {
                case 0 -> ROLE_STANDARD_BEARER;
                case 1 -> ROLE_WITCH;
                case 2 -> ROLE_WEB_TRAPPER;
                case 3 -> ROLE_VINDICATOR;
                case 4 -> ROLE_ILLUSIONER;
                default -> ROLE_RAVAGER;
            };
            case TACTIC_PINCER -> switch (normalized) {
                case 0 -> ROLE_VINDICATOR;
                case 1 -> ROLE_WEB_TRAPPER;
                case 2 -> ROLE_ILLUSIONER;
                case 3 -> ROLE_STANDARD_BEARER;
                case 4 -> ROLE_WITCH;
                default -> ROLE_RAVAGER;
            };
            case TACTIC_BREACH -> switch (normalized) {
                case 0 -> ROLE_BREACHER;
                case 1 -> ROLE_DEMOLISHER;
                case 2 -> ROLE_BUILDER;
                case 3 -> ROLE_RAVAGER;
                case 4 -> ROLE_VINDICATOR;
                default -> ROLE_PILLAGER;
            };
            case TACTIC_SIEGE -> switch (normalized) {
                case 0 -> ROLE_DEMOLISHER;
                case 1 -> ROLE_BUILDER;
                case 2 -> ROLE_BREACHER;
                case 3 -> ROLE_EVOKER;
                case 4 -> ROLE_RAVAGER;
                default -> ROLE_VINDICATOR;
            };
            case TACTIC_REINFORCE -> switch (normalized) {
                case 0 -> ROLE_EVOKER;
                case 1 -> ROLE_VEX;
                case 2 -> ROLE_WITCH;
                case 3 -> ROLE_STANDARD_BEARER;
                case 4 -> ROLE_RAVAGER;
                default -> ROLE_VINDICATOR;
            };
            case TACTIC_WITHDRAW -> switch (normalized) {
                case 0 -> ROLE_VINDICATOR;
                case 1 -> ROLE_ILLUSIONER;
                case 2 -> ROLE_WITCH;
                case 3 -> ROLE_EVOKER;
                case 4 -> ROLE_STANDARD_BEARER;
                default -> ROLE_RAVAGER;
            };
            case TACTIC_FINISH -> switch (normalized) {
                case 0 -> ROLE_RAVAGER;
                case 1 -> ROLE_BREACHER;
                case 2 -> ROLE_EVOKER;
                case 3 -> ROLE_VINDICATOR;
                case 4 -> ROLE_ILLUSIONER;
                default -> ROLE_WITCH;
            };
            default -> ROLE_PILLAGER;
        };
    }

    private static void tryAddCompatibleRole(Input input, CandidateScratch candidate, int phase,
            int squad, int role, int budget, int remainingPhases) {
        if (role == ROLE_PILLAGER) return;
        if (role == ROLE_VEX && squadRoleCount(candidate, phase, squad, ROLE_EVOKER) == 0) {
            if (roleAllowed(input, candidate, phase, squad, ROLE_EVOKER)
                    && canAddRole(candidate, phase, squad, ROLE_EVOKER, budget, remainingPhases)) {
                addRole(candidate, phase, squad, ROLE_EVOKER, 1);
            } else {
                return;
            }
        }
        if (!roleAllowed(input, candidate, phase, squad, role)
                || !canAddRole(candidate, phase, squad, role, budget, remainingPhases)) return;
        addRole(candidate, phase, squad, role, 1);
    }

    private static boolean roleAllowed(Input input, CandidateScratch candidate, int phase,
            int squad, int role) {
        int tactic = candidate.phaseTactic[phase];
        int objective = candidate.phaseObjective[phase];
        if (role == ROLE_VEX && squadRoleCount(candidate, phase, squad, ROLE_EVOKER) == 0) {
            return false;
        }
        if (role == ROLE_WEB_TRAPPER) {
            return input.alternateRouteAvailable()
                    && (candidate.phaseTopology[phase] == TOPOLOGY_PINCER
                    || candidate.phaseTopology[phase] == TOPOLOGY_FEINT)
                    && (objective == OBJECTIVE_DIVERT || objective == OBJECTIVE_ISOLATE);
        }
        if (role == ROLE_BUILDER || role == ROLE_DEMOLISHER) {
            return safeMutationTargets(input) > mutationRoleCount(candidate)
                    && (tactic == TACTIC_BREACH || tactic == TACTIC_SIEGE)
                    && objective == OBJECTIVE_BREACH;
        }
        if (role == ROLE_BREACHER) {
            return tactic == TACTIC_BREACH || tactic == TACTIC_SIEGE
                    || tactic == TACTIC_FINISH;
        }
        if (isSiegeRole(role)) {
            return tactic == TACTIC_BREACH || tactic == TACTIC_SIEGE;
        }
        return true;
    }

    private static boolean canAddRole(CandidateScratch candidate, int phase, int squad, int role,
            int budget, int remainingPhases) {
        int phaseOffset = phase * ROLE_COUNT;
        int squadOffset = squadOffset(phase, squad);
        if (candidate.phaseRoleCounts[phaseOffset + role] >= MAX_PHASE_ROLE_COUNT
                || candidate.squadRoleCounts[squadOffset + role] >= MAX_PHASE_ROLE_COUNT
                || candidate.phaseUnitCount[phase] >= MAX_PHASE_UNITS
                || candidate.totalUnitCount >= MAX_TOTAL_UNITS
                || candidate.phaseThreat[phase] + threatOf(role) > MAX_PHASE_THREAT
                || candidate.totalThreat + threatOf(role)
                > budget - remainingPhases * PILLAGER_THREAT) return false;
        int globalRoleCount = 0;
        for (int current = 0; current < candidate.phaseCount; current++) {
            globalRoleCount += candidate.phaseRoleCounts[current * ROLE_COUNT + role];
        }
        if (globalRoleCount >= MAX_GLOBAL_ROLE_COUNT) return false;

        int support = 0;
        int siege = 0;
        int assault = 0;
        for (int currentRole = 0; currentRole < ROLE_COUNT; currentRole++) {
            int count = candidate.phaseRoleCounts[phaseOffset + currentRole]
                    + (currentRole == role ? 1 : 0);
            if (isSupportRole(currentRole)) support += count;
            else if (isSiegeRole(currentRole)) siege += count;
            else assault += count;
        }
        return boundedSupportSiege(support, siege, assault);
    }

    private static int validateCandidate(Input input, CandidateScratch candidate, int budget) {
        if (candidate.phaseCount < MIN_PHASES || candidate.phaseCount > MAX_PHASES) {
            return REJECTION_PHASE_GRAPH;
        }
        int reachablePhases = 1;
        int totalThreat = 0;
        int totalUnits = 0;
        int mutationTargetsUsed = 0;
        int seenEvoker = 0;
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            if ((reachablePhases & (1 << phase)) == 0) return REJECTION_REACHABILITY;
            int approach = candidate.phaseApproachMask[phase];
            if (approach == 0 || (approach & input.reachableApproachMask()) != approach) {
                return REJECTION_APPROACH;
            }
            if (candidate.phaseTactic[phase] < 0 || candidate.phaseTactic[phase] >= TACTIC_COUNT
                    || candidate.phaseTopology[phase] != topologyFor(candidate.phaseTactic[phase])
                    || !phaseFormationAllowed(candidate.phaseTactic[phase],
                    candidate.phaseFormation[phase])
                    || !objectiveAllowed(candidate.phaseTactic[phase],
                    candidate.phaseObjective[phase])) return REJECTION_PHASE_GRAPH;
            if (!counterplayCompatible(candidate.phaseTactic[phase],
                    candidate.phaseCounterplay[phase])) return REJECTION_COUNTERPLAY;
            int offset = phase * OUTCOME_EDGE_COUNT;
            if (!validOutcomePair(candidate.outcomeKind[offset], candidate.outcomeTarget[offset],
                    candidate.outcomeKind[offset + 1], candidate.outcomeTarget[offset + 1],
                    phase, candidate.phaseCount)) {
                return REJECTION_PHASE_GRAPH;
            }
            if (candidate.telegraphTick[phase] <= candidate.phaseStartTick[phase]
                    || candidate.releaseTick[phase] - candidate.telegraphTick[phase]
                    < MIN_RESPONSE_TICKS
                    || candidate.deadlineTick[phase] <= candidate.releaseTick[phase]
                    || (phase > 0 && candidate.phaseStartTick[phase]
                    < candidate.deadlineTick[phase - 1] + MIN_PHASE_GAP_TICKS)) {
                return REJECTION_TIMING;
            }
            for (int edge = 0; edge < OUTCOME_EDGE_COUNT; edge++) {
                if (candidate.outcomeTarget[offset + edge] >= 0) {
                    reachablePhases |= 1 << candidate.outcomeTarget[offset + edge];
                }
            }
            int phaseThreat = 0;
            int phaseUnits = 0;
            int support = 0;
            int siege = 0;
            int assault = 0;
            for (int squad = 0; squad < candidate.squadCount[phase]; squad++) {
                int squadIndex = phase * MAX_SQUADS_PER_PHASE + squad;
                int squadOffset = squadOffset(phase, squad);
                int squadUnits = 0;
                for (int role = 0; role < ROLE_COUNT; role++) {
                    int count = candidate.squadRoleCounts[squadOffset + role];
                    if (count < 0 || count > MAX_PHASE_ROLE_COUNT) return REJECTION_CAP;
                    squadUnits += count;
                    phaseThreat += count * threatOf(role);
                    if (isSupportRole(role)) support += count;
                    else if (isSiegeRole(role)) siege += count;
                    else assault += count;
                }
                if (squadUnits <= 0 || squadUnits > MAX_PHASE_UNITS) return REJECTION_LIVENESS;
                phaseUnits += squadUnits;
                if (!singleAxis(candidate.squadApproach[squadIndex])
                        || (candidate.squadApproach[squadIndex] & approach) == 0
                        || (candidate.squadApproach[squadIndex]
                        & input.reachableApproachMask()) == 0) return REJECTION_APPROACH;
                if (candidate.squadReleaseOffset[squadIndex] < 0
                        || candidate.releaseTick[phase] + candidate.squadReleaseOffset[squadIndex]
                        >= candidate.deadlineTick[phase]) return REJECTION_TIMING;
                if (!squadFormationAllowed(candidate.phaseTactic[phase],
                        candidate.squadFormation[squadIndex])
                        || !squadObjectiveAllowed(candidate.phaseTactic[phase],
                        candidate.squadObjective[squadIndex])) return REJECTION_COUNTERPLAY;
            }
            if (candidate.squadCount[phase] < MIN_SQUADS_PER_PHASE
                    || candidate.squadCount[phase] > MAX_SQUADS_PER_PHASE
                    || phaseUnits > MAX_PHASE_UNITS) return REJECTION_CAP;
            for (int role = 0; role < ROLE_COUNT; role++) {
                int phaseCount = candidate.phaseRoleCounts[phase * ROLE_COUNT + role];
                int summed = 0;
                for (int squad = 0; squad < candidate.squadCount[phase]; squad++) {
                    summed += candidate.squadRoleCounts[squadOffset(phase, squad) + role];
                }
                if (phaseCount != summed || phaseCount < 0
                        || phaseCount > MAX_PHASE_ROLE_COUNT) return REJECTION_CAP;
            }
            for (int squad = 0; squad < candidate.squadCount[phase]; squad++) {
                int squadOffset = squadOffset(phase, squad);
                for (int role = 0; role < ROLE_COUNT; role++) {
                    int count = candidate.squadRoleCounts[squadOffset + role];
                    if (role == ROLE_VEX && count > 0
                            && candidate.squadRoleCounts[squadOffset + ROLE_EVOKER] == 0) {
                        return REJECTION_VEX_PREREQUISITE;
                    }
                    if ((role == ROLE_BUILDER || role == ROLE_DEMOLISHER) && count > 0
                            && candidate.phaseObjective[phase] != OBJECTIVE_BREACH) {
                        return REJECTION_MUTATION_TARGET;
                    }
                    if (role == ROLE_WEB_TRAPPER && count > 0
                            && !input.alternateRouteAvailable()) return REJECTION_ALTERNATE_ROUTE;
                }
            }
            if (!boundedSupportSiege(support, siege, assault)) {
                return REJECTION_SUPPORT_SIEGE;
            }
            if (!counterplayCompatible(candidate.phaseTactic[phase],
                    candidate.phaseCounterplay[phase])) return REJECTION_COUNTERPLAY;
            if (candidate.phaseThreat[phase] != phaseThreat
                    || candidate.phaseUnitCount[phase] != phaseUnits
                    || phaseThreat > MAX_PHASE_THREAT) return REJECTION_PHASE_BUDGET;
            mutationTargetsUsed += candidate.phaseRoleCounts[phase * ROLE_COUNT + ROLE_BUILDER]
                    + candidate.phaseRoleCounts[phase * ROLE_COUNT + ROLE_DEMOLISHER];
            if (mutationTargetsUsed > safeMutationTargets(input)) return REJECTION_MUTATION_TARGET;
            if (candidate.phaseRoleCounts[phase * ROLE_COUNT + ROLE_EVOKER] > 0) {
                seenEvoker += candidate.phaseRoleCounts[phase * ROLE_COUNT + ROLE_EVOKER];
            }
            if (candidate.phaseRoleCounts[phase * ROLE_COUNT + ROLE_VEX] > 0 && seenEvoker <= 0) {
                return REJECTION_VEX_PREREQUISITE;
            }
            totalThreat += phaseThreat;
            totalUnits += phaseUnits;
            if (totalThreat > budget || totalUnits > MAX_TOTAL_UNITS) {
                return REJECTION_TOTAL_BUDGET;
            }
        }
        if (reachablePhases != (1 << candidate.phaseCount) - 1) return REJECTION_REACHABILITY;
        if (totalThreat != candidate.totalThreat || totalUnits != candidate.totalUnitCount) {
            return REJECTION_CAP;
        }
        return rewardFieldsValid(input, candidate) ? REJECTION_NONE : REJECTION_REWARD;
    }

    private static Plan materialize(Input input, int root, int trace, int attempt,
            boolean fallback, CandidateScratch candidate, int budget) {
        int[] totalRoles = new int[ROLE_COUNT];
        Phase[] phases = new Phase[candidate.phaseCount];
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            int phaseRoleOffset = phase * ROLE_COUNT;
            int[] phaseRoles = new int[ROLE_COUNT];
            for (int role = 0; role < ROLE_COUNT; role++) {
                phaseRoles[role] = candidate.phaseRoleCounts[phaseRoleOffset + role];
                totalRoles[role] += phaseRoles[role];
            }
            Squad[] squads = new Squad[candidate.squadCount[phase]];
            for (int squad = 0; squad < candidate.squadCount[phase]; squad++) {
                int squadIndex = phase * MAX_SQUADS_PER_PHASE + squad;
                int offset = squadOffset(phase, squad);
                int[] squadRoles = new int[ROLE_COUNT];
                int squadThreat = 0;
                int squadUnits = 0;
                for (int role = 0; role < ROLE_COUNT; role++) {
                    squadRoles[role] = candidate.squadRoleCounts[offset + role];
                    squadThreat += squadRoles[role] * threatOf(role);
                    squadUnits += squadRoles[role];
                }
                squads[squad] = new Squad(phase, squad, candidate.squadApproach[squadIndex],
                        candidate.squadFormation[squadIndex], candidate.squadObjective[squadIndex],
                        candidate.squadObjectiveTarget[squadIndex],
                        candidate.squadReleaseOffset[squadIndex], squadRoles, squadThreat,
                        squadUnits);
            }
            Outcome[] outcomes = new Outcome[OUTCOME_EDGE_COUNT];
            int outcomeOffset = phase * OUTCOME_EDGE_COUNT;
            for (int edge = 0; edge < OUTCOME_EDGE_COUNT; edge++) {
                outcomes[edge] = new Outcome(candidate.outcomeKind[outcomeOffset + edge],
                        candidate.outcomeTarget[outcomeOffset + edge]);
            }
            phases[phase] = new Phase(phase, candidate.phaseTactic[phase],
                    candidate.phaseApproachMask[phase], candidate.phaseTopology[phase],
                    candidate.phaseFormation[phase], candidate.phaseObjective[phase],
                    candidate.phaseObjectiveTarget[phase], candidate.phaseCounterplay[phase],
                    candidate.phaseStartTick[phase], candidate.telegraphTick[phase],
                    candidate.releaseTick[phase], candidate.deadlineTick[phase], outcomes,
                    phaseRoles, candidate.phaseThreat[phase], candidate.phaseUnitCount[phase],
                    squads);
        }
        int hash = planHash(input, root, candidate, attempt, totalRoles, budget);
        return new Plan(true, RESULT_ACCEPTED, hash, trace, attempt, attempt + 1,
                budget, candidate.totalThreat, candidate.totalUnitCount, totalRoles, phases,
                candidate.rewardDecision, candidate.rewardIndex, candidate.rewardToken, fallback);
    }

    private static Plan unplannable(Input input, int root, int resultCode) {
        return unplannable(input, root, resultCode,
                lane(root, TAG_UNPLANNABLE, resultCode, 0), 0);
    }

    private static Plan unplannable(Input input, int root, int resultCode, int trace,
            int attemptsEvaluated) {
        int hash = StructureHash.mix32(lane(root, TAG_UNPLANNABLE, resultCode,
                attemptsEvaluated) ^ resultCode);
        return new Plan(false, resultCode, hash, trace, -1, attemptsEvaluated,
                threatBudget(input), 0, 0, new int[ROLE_COUNT], new Phase[0],
                REWARD_NONE, -1, 0, false);
    }

    private static int planHash(Input input, int root, CandidateScratch candidate, int attempt,
            int[] totalRoles, int budget) {
        int hash = lane(root, TAG_PLAN_HASH, attempt, candidate.phaseCount);
        hash = StructureHash.mix32(hash ^ lane(root, TAG_PLAN_HASH, budget,
                input.defenderPower()));
        hash = StructureHash.mix32(hash ^ lane(root, TAG_PLAN_HASH,
                input.safeMutationTargetCount(), input.alternateRouteAvailable() ? 1 : 0));
        hash = StructureHash.mix32(hash ^ lane(root, TAG_PLAN_HASH,
                input.registeredRewardCandidateCount(), candidate.totalUnitCount));
        for (int role = 0; role < ROLE_COUNT; role++) {
            hash = StructureHash.mix32(hash ^ lane(root, TAG_ROLE, role, totalRoles[role]));
        }
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            hash = StructureHash.mix32(hash ^ lane(root, TAG_TACTIC, phase,
                    candidate.phaseTactic[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_APPROACH, phase,
                    candidate.phaseApproachMask[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_TOPOLOGY, phase,
                    candidate.phaseTopology[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_FORMATION, phase,
                    candidate.phaseFormation[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_OBJECTIVE, phase,
                    candidate.phaseObjective[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_OBJECTIVE, phase,
                    candidate.phaseObjectiveTarget[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_COUNTERPLAY, phase,
                    candidate.phaseCounterplay[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_TIMING_START, phase,
                    candidate.phaseStartTick[phase]));
            hash = StructureHash.mix32(hash ^ lane(root, TAG_TIMING_DEADLINE, phase,
                    candidate.deadlineTick[phase]));
            int outcomeOffset = phase * OUTCOME_EDGE_COUNT;
            for (int edge = 0; edge < OUTCOME_EDGE_COUNT; edge++) {
                hash = StructureHash.mix32(hash ^ lane(root, TAG_OUTCOME,
                        outcomeOffset + edge, candidate.outcomeKind[outcomeOffset + edge]));
                hash = StructureHash.mix32(hash ^ candidate.outcomeTarget[outcomeOffset + edge]);
            }
            for (int squad = 0; squad < candidate.squadCount[phase]; squad++) {
                int squadIndex = phase * MAX_SQUADS_PER_PHASE + squad;
                int offset = squadOffset(phase, squad);
                hash = StructureHash.mix32(hash ^ lane(root, TAG_SQUAD_COUNT, phase, squad));
                hash = StructureHash.mix32(hash ^ lane(root, TAG_SQUAD_APPROACH,
                        offset, candidate.squadApproach[squadIndex]));
                hash = StructureHash.mix32(hash ^ lane(root, TAG_SQUAD_FORMATION,
                        offset, candidate.squadFormation[squadIndex]));
                hash = StructureHash.mix32(hash ^ lane(root, TAG_SQUAD_OBJECTIVE,
                        offset, candidate.squadObjective[squadIndex]));
                hash = StructureHash.mix32(hash ^ lane(root, TAG_SQUAD_OBJECTIVE,
                        offset + 1, candidate.squadObjectiveTarget[squadIndex]));
                hash = StructureHash.mix32(hash ^ lane(root, TAG_RELEASE_OFFSET,
                        offset, candidate.squadReleaseOffset[squadIndex]));
                for (int role = 0; role < ROLE_COUNT; role++) {
                    hash = StructureHash.mix32(hash ^ lane(root, TAG_ROLE,
                            offset + role, candidate.squadRoleCounts[offset + role]));
                }
            }
        }
        hash = StructureHash.mix32(hash ^ lane(root, TAG_REWARD_DECISION,
                candidate.rewardDecision, candidate.rewardIndex));
        hash = StructureHash.mix32(hash ^ (int) candidate.rewardToken
                ^ (int) (candidate.rewardToken >>> 32));
        return hash;
    }

    private static void chooseReward(Input input, int root, CandidateScratch candidate) {
        candidate.rewardDecision = REWARD_NONE;
        candidate.rewardIndex = -1;
        candidate.rewardToken = 0;
        if (input.registeredRewardCandidateCount() <= 0) return;
        if (unsignedMod(lane(root, TAG_REWARD_DECISION, 0, 0), 100)
                >= REWARD_CHANCE_PERCENT) return;
        candidate.rewardDecision = REWARD_CANDIDATE;
        candidate.rewardIndex = unsignedMod(lane(root, TAG_REWARD_INDEX, 0, 0),
                input.registeredRewardCandidateCount());
        int high = lane(root, TAG_REWARD_TOKEN, candidate.rewardIndex, 0);
        int low = lane(root, TAG_REWARD_TOKEN, candidate.rewardIndex, 1);
        candidate.rewardToken = (Integer.toUnsignedLong(high) << 32)
                | Integer.toUnsignedLong(low);
        if (candidate.rewardToken == 0) candidate.rewardToken = 1;
    }

    private static boolean rewardFieldsValid(Input input, CandidateScratch candidate) {
        return validReward(input, candidate.rewardDecision, candidate.rewardIndex,
                candidate.rewardToken);
    }

    private static boolean validReward(Input input, int decision, int index, long token) {
        if (input.registeredRewardCandidateCount() <= 0) {
            return decision == REWARD_NONE && index == -1 && token == 0;
        }
        if (decision == REWARD_NONE) return index == -1 && token == 0;
        return decision == REWARD_CANDIDATE && index >= 0
                && index < input.registeredRewardCandidateCount() && token != 0;
    }

    private static boolean validPhaseFacts(Input input, Phase phase, int phaseIndex,
            int phaseCount) {
        if (phase == null || phase.phaseIndex != phaseIndex
                || phase.tactic < 0 || phase.tactic >= TACTIC_COUNT
                || phase.approachMask == 0
                || (phase.approachMask & input.reachableApproachMask()) != phase.approachMask
                || phase.topology < 0 || phase.topology >= TOPOLOGY_COUNT
                || phase.topology != topologyFor(phase.tactic)
                || phase.formation < 0 || phase.formation >= FORMATION_COUNT
                || !phaseFormationAllowed(phase.tactic, phase.formation)
                || phase.objective < 0 || phase.objective >= OBJECTIVE_KIND_COUNT
                || !objectiveAllowed(phase.tactic, phase.objective)
                || phase.objectiveTarget < 0 || phase.objectiveTarget >= input.objectiveCount()
                || phase.counterplay == 0
                || (phase.counterplay & ~COUNTERPLAY_MASK) != 0
                || !counterplayCompatible(phase.tactic, phase.counterplay)
                || phase.squads.length < MIN_SQUADS_PER_PHASE
                || phase.squads.length > MAX_SQUADS_PER_PHASE
                || phase.roleCounts.length != ROLE_COUNT
                || phase.startTick < 0
                || phase.telegraphTick <= phase.startTick
                || phase.releaseTick - phase.telegraphTick < MIN_RESPONSE_TICKS
                || phase.deadlineTick <= phase.releaseTick
                || phase.outcomes.length != OUTCOME_EDGE_COUNT) return false;
        if (phaseIndex > 0 && phase.startTick <= 0) return false;
        for (int edge = 0; edge < OUTCOME_EDGE_COUNT; edge++) {
            Outcome outcome = phase.outcomes[edge];
            if (outcome == null || !validOutcome(outcome.kind, outcome.nextPhase,
                    phaseIndex, phaseCount)) return false;
        }
        return validOutcomePair(phase.outcomes[0].kind, phase.outcomes[0].nextPhase,
                phase.outcomes[1].kind, phase.outcomes[1].nextPhase,
                phaseIndex, phaseCount);
    }

    private static boolean validSquadFacts(Input input, Phase phase, Squad squad,
            int phaseIndex, int squadIndex) {
        if (squad == null || squad.phaseIndex != phaseIndex || squad.squadIndex != squadIndex
                || !singleAxis(squad.approach) || (squad.approach & phase.approachMask) == 0
                || (squad.approach & input.reachableApproachMask()) == 0
                || squad.formation < 0 || squad.formation >= FORMATION_COUNT
                || !squadFormationAllowed(phase.tactic, squad.formation)
                || squad.objective < 0 || squad.objective >= OBJECTIVE_KIND_COUNT
                || !squadObjectiveAllowed(phase.tactic, squad.objective)
                || squad.objectiveTarget < 0 || squad.objectiveTarget >= input.objectiveCount()
                || squad.releaseOffset < 0
                || phase.releaseTick + squad.releaseOffset >= phase.deadlineTick
                || squad.roleCounts.length != ROLE_COUNT) return false;
        if (squad.roleCounts[ROLE_VEX] > 0
                && squad.roleCounts[ROLE_EVOKER] == 0) return false;
        if ((squad.roleCounts[ROLE_BUILDER] > 0 || squad.roleCounts[ROLE_DEMOLISHER] > 0)
                && (phase.tactic != TACTIC_BREACH && phase.tactic != TACTIC_SIEGE
                || phase.objective != OBJECTIVE_BREACH)
                || squad.roleCounts[ROLE_WEB_TRAPPER] > 0
                && (!input.alternateRouteAvailable()
                || (phase.topology != TOPOLOGY_PINCER && phase.topology != TOPOLOGY_FEINT))) {
            return false;
        }
        if (squad.roleCounts[ROLE_WEB_TRAPPER] > 0
                && squad.objective != OBJECTIVE_DIVERT
                && squad.objective != OBJECTIVE_ISOLATE) return false;
        if ((phase.approachMask & (phase.approachMask - 1)) != 0
                && phase.squads.length > 1 && phase.squad(0).approach == squad.approach
                && squadIndex > 0) {
            /* Distinct routes are required for a true multi-axis phase. */
            boolean distinct = false;
            for (int previous = 0; previous < squadIndex; previous++) {
                if (phase.squad(previous).approach != squad.approach) distinct = true;
            }
            if (!distinct) return false;
        }
        return true;
    }

    private static boolean validOutcome(int kind, int target, int phase, int phaseCount) {
        if (kind < 0 || kind >= OUTCOME_KIND_COUNT) return false;
        if (kind == OUTCOME_COMPLETE) return phase == phaseCount - 1 && target == EXIT_COMPLETE;
        if (kind == OUTCOME_ABORT) return phase == phaseCount - 1 && target == EXIT_ABORT;
        if (target <= phase || target >= phaseCount) return false;
        return kind == OUTCOME_PROGRESS ? target == phase + 1 : true;
    }

    private static boolean validOutcomePair(int firstKind, int firstTarget, int secondKind,
            int secondTarget, int phase, int phaseCount) {
        if (!validOutcome(firstKind, firstTarget, phase, phaseCount)
                || !validOutcome(secondKind, secondTarget, phase, phaseCount)
                || firstKind == secondKind) return false;
        if (phase == phaseCount - 1) return true;
        return phase + 2 >= phaseCount || secondTarget > phase + 1;
    }

    private static boolean counterplayCompatible(int tactic, int counterplay) {
        int required = switch (tactic) {
            case TACTIC_SCREEN -> COUNTERPLAY_FOCUS_ASSAULT;
            case TACTIC_PINCER -> COUNTERPLAY_SPLIT_DEFENSE;
            case TACTIC_BREACH, TACTIC_SIEGE -> COUNTERPLAY_CLEAR_SUPPORT;
            case TACTIC_REINFORCE -> COUNTERPLAY_PROTECT_TARGET;
            case TACTIC_WITHDRAW -> COUNTERPLAY_HOLD_GROUND;
            case TACTIC_FINISH -> COUNTERPLAY_FOCUS_ASSAULT;
            default -> 0;
        };
        return required != 0 && (counterplay & required) != 0;
    }

    private static boolean objectiveAllowed(int tactic, int objective) {
        return switch (tactic) {
            case TACTIC_SCREEN -> objective == OBJECTIVE_PRESS || objective == OBJECTIVE_DIVERT;
            case TACTIC_PINCER -> objective == OBJECTIVE_ISOLATE || objective == OBJECTIVE_DIVERT;
            case TACTIC_BREACH, TACTIC_SIEGE -> objective == OBJECTIVE_BREACH;
            case TACTIC_REINFORCE -> objective == OBJECTIVE_PRESS || objective == OBJECTIVE_ISOLATE;
            case TACTIC_WITHDRAW -> objective == OBJECTIVE_RALLY;
            case TACTIC_FINISH -> objective == OBJECTIVE_FINISH;
            default -> false;
        };
    }

    private static boolean phaseFormationAllowed(int tactic, int formation) {
        return switch (tactic) {
            case TACTIC_SCREEN -> formation == FORMATION_SCREEN;
            case TACTIC_PINCER -> formation == FORMATION_SPLIT;
            case TACTIC_BREACH, TACTIC_SIEGE -> formation == FORMATION_COLUMN;
            case TACTIC_REINFORCE -> formation == FORMATION_WEDGE;
            case TACTIC_WITHDRAW -> formation == FORMATION_RING;
            case TACTIC_FINISH -> formation == FORMATION_LINE || formation == FORMATION_WEDGE;
            default -> false;
        };
    }

    private static boolean squadFormationAllowed(int tactic, int formation) {
        return switch (tactic) {
            case TACTIC_PINCER -> formation == FORMATION_WEDGE || formation == FORMATION_SPLIT;
            case TACTIC_BREACH, TACTIC_SIEGE -> formation == FORMATION_COLUMN
                    || formation == FORMATION_WEDGE;
            case TACTIC_WITHDRAW -> formation == FORMATION_SCREEN || formation == FORMATION_COLUMN;
            default -> formation >= 0 && formation < FORMATION_COUNT;
        };
    }

    private static boolean squadObjectiveAllowed(int tactic, int objective) {
        return switch (tactic) {
            case TACTIC_PINCER -> objective == OBJECTIVE_ISOLATE || objective == OBJECTIVE_DIVERT;
            case TACTIC_BREACH, TACTIC_SIEGE -> objective == OBJECTIVE_BREACH
                    || objective == OBJECTIVE_PRESS;
            case TACTIC_WITHDRAW -> objective == OBJECTIVE_RALLY;
            case TACTIC_FINISH -> objective == OBJECTIVE_FINISH;
            default -> objective == OBJECTIVE_PRESS || objective == OBJECTIVE_DIVERT
                    || objective == OBJECTIVE_ISOLATE;
        };
    }

    private static int chooseAxes(int mask, int lane, int desired) {
        int available = mask & APPROACH_MASK;
        int count = Integer.bitCount(available);
        int take = desired < count ? desired : count;
        if (take < 1) return 0;
        int selected = 0;
        int start = unsignedMod(lane, 4);
        for (int offset = 0; offset < 4 && Integer.bitCount(selected) < take; offset++) {
            int axis = 1 << ((start + offset) & 3);
            if ((available & axis) != 0) selected |= axis;
        }
        return selected;
    }

    private static int chooseAxis(int mask, int lane) {
        int selected = unsignedMod(lane, Integer.bitCount(mask));
        for (int bit = 0; bit < 4; bit++) {
            int axis = 1 << bit;
            if ((mask & axis) == 0) continue;
            if (selected == 0) return axis;
            selected--;
        }
        return 0;
    }

    private static int nthAxis(int mask, int index) {
        int remaining = index;
        for (int bit = 0; bit < 4; bit++) {
            int axis = 1 << bit;
            if ((mask & axis) == 0) continue;
            if (remaining == 0) return axis;
            remaining--;
        }
        return 0;
    }

    private static void addRole(CandidateScratch candidate, int phase, int squad,
            int role, int count) {
        if (count <= 0) return;
        int phaseOffset = phase * ROLE_COUNT;
        int squadOffset = squadOffset(phase, squad);
        candidate.phaseRoleCounts[phaseOffset + role] += count;
        candidate.squadRoleCounts[squadOffset + role] += count;
        candidate.phaseThreat[phase] += count * threatOf(role);
        candidate.totalThreat += count * threatOf(role);
        candidate.phaseUnitCount[phase] += count;
        candidate.totalUnitCount += count;
    }

    private static int squadRoleCount(CandidateScratch candidate, int phase, int squad, int role) {
        return candidate.squadRoleCounts[squadOffset(phase, squad) + role];
    }

    private static int mutationRoleCount(CandidateScratch candidate) {
        int count = 0;
        for (int phase = 0; phase < candidate.phaseCount; phase++) {
            int offset = phase * ROLE_COUNT;
            count += candidate.phaseRoleCounts[offset + ROLE_BUILDER]
                    + candidate.phaseRoleCounts[offset + ROLE_DEMOLISHER];
        }
        return count;
    }

    private static int safeMutationTargets(Input input) {
        return Math.max(0, input.safeMutationTargetCount());
    }

    private static int threatBudget(Input input) {
        long defenders = Math.max(0L, input.defenderCount());
        long power = Math.max(0L, input.defenderPower());
        long value = BASE_THREAT_BUDGET + defenders * THREAT_BUDGET_PER_DEFENDER
                + power * THREAT_BUDGET_PER_POWER;
        return value > MAX_TOTAL_THREAT_BUDGET ? MAX_TOTAL_THREAT_BUDGET : (int) value;
    }

    private static int threatOf(int role) {
        return switch (role) {
            case ROLE_PILLAGER -> 5;
            case ROLE_VINDICATOR -> 7;
            case ROLE_EVOKER -> 12;
            case ROLE_WITCH -> 9;
            case ROLE_VEX -> 3;
            case ROLE_RAVAGER -> 18;
            case ROLE_ILLUSIONER -> 11;
            case ROLE_STANDARD_BEARER -> 4;
            case ROLE_WEB_TRAPPER -> 6;
            case ROLE_BREACHER -> 8;
            case ROLE_DEMOLISHER -> 10;
            case ROLE_BUILDER -> 5;
            default -> throw new IllegalArgumentException("role=" + role);
        };
    }

    private static boolean isSupportRole(int role) {
        return role == ROLE_EVOKER || role == ROLE_WITCH || role == ROLE_VEX
                || role == ROLE_STANDARD_BEARER || role == ROLE_WEB_TRAPPER;
    }

    private static boolean isSiegeRole(int role) {
        return role == ROLE_BREACHER || role == ROLE_DEMOLISHER || role == ROLE_BUILDER;
    }

    private static boolean boundedSupportSiege(int support, int siege, int assault) {
        return support <= MAX_SUPPORT_PER_PHASE
                && siege <= MAX_SIEGE_PER_PHASE
                && support <= assault + 2
                && siege <= assault + 1
                && support + siege <= 6;
    }

    private static boolean singleAxis(int approach) {
        return approach == APPROACH_NORTH || approach == APPROACH_EAST
                || approach == APPROACH_SOUTH || approach == APPROACH_WEST;
    }

    private static int squadOffset(int phase, int squad) {
        return (phase * MAX_SQUADS_PER_PHASE + squad) * ROLE_COUNT;
    }

    private static int rootSeed(Input input) {
        int hash = StructureHash.mix32(input.generatorVersion() ^ TAG_ROOT);
        hash = mixLongIdentity(hash, input.worldSeed(), TAG_WORLD_SEED_HIGH,
                TAG_WORLD_SEED_LOW);
        hash = mixLongIdentity(hash, input.raidId(), TAG_RAID_ID_HIGH, TAG_RAID_ID_LOW);
        return mixLongIdentity(hash, input.villageSiteKey(), TAG_SITE_KEY_HIGH,
                TAG_SITE_KEY_LOW);
    }

    private static int mixLongIdentity(int hash, long value, int highTag, int lowTag) {
        hash = StructureHash.mix32(hash ^ highTag ^ (int) (value >>> 32));
        return StructureHash.mix32(hash ^ lowTag ^ (int) value);
    }

    private static int lane(int root, int semanticTag, int index0, int index1) {
        int hash = StructureHash.mix32(root ^ semanticTag);
        hash = StructureHash.mix32(hash ^ index0 * 0x9e3779b9);
        return StructureHash.mix32(hash ^ index1 * 0x85ebca6b);
    }

    private static int unsignedMod(int value, int bound) {
        return bound > 0 ? Integer.remainderUnsigned(value, bound) : 0;
    }

    private static boolean allZero(int[] values) {
        if (values == null || values.length != ROLE_COUNT) return false;
        for (int value : values) if (value != 0) return false;
        return true;
    }

    private static final class CandidateScratch {
        private int phaseCount;
        private int totalThreat;
        private int totalUnitCount;
        private int rewardDecision;
        private int rewardIndex;
        private long rewardToken;
        private final int[] phaseTactic = new int[MAX_PHASES];
        private final int[] phaseApproachMask = new int[MAX_PHASES];
        private final int[] phaseTopology = new int[MAX_PHASES];
        private final int[] phaseFormation = new int[MAX_PHASES];
        private final int[] phaseObjective = new int[MAX_PHASES];
        private final int[] phaseObjectiveTarget = new int[MAX_PHASES];
        private final int[] phaseCounterplay = new int[MAX_PHASES];
        private final int[] phaseStartTick = new int[MAX_PHASES];
        private final int[] telegraphTick = new int[MAX_PHASES];
        private final int[] releaseTick = new int[MAX_PHASES];
        private final int[] deadlineTick = new int[MAX_PHASES];
        private final int[] outcomeKind = new int[MAX_PHASES * OUTCOME_EDGE_COUNT];
        private final int[] outcomeTarget = new int[MAX_PHASES * OUTCOME_EDGE_COUNT];
        private final int[] squadCount = new int[MAX_PHASES];
        private final int[] phaseThreat = new int[MAX_PHASES];
        private final int[] phaseUnitCount = new int[MAX_PHASES];
        private final int[] squadApproach = new int[MAX_PHASES * MAX_SQUADS_PER_PHASE];
        private final int[] squadFormation = new int[MAX_PHASES * MAX_SQUADS_PER_PHASE];
        private final int[] squadObjective = new int[MAX_PHASES * MAX_SQUADS_PER_PHASE];
        private final int[] squadObjectiveTarget = new int[MAX_PHASES * MAX_SQUADS_PER_PHASE];
        private final int[] squadReleaseOffset = new int[MAX_PHASES * MAX_SQUADS_PER_PHASE];
        private final int[] phaseRoleCounts = new int[MAX_PHASES * ROLE_COUNT];
        private final int[] squadRoleCounts = new int[
                MAX_PHASES * MAX_SQUADS_PER_PHASE * ROLE_COUNT];

        private void clear() {
            phaseCount = 0;
            totalThreat = 0;
            totalUnitCount = 0;
            rewardDecision = REWARD_NONE;
            rewardIndex = -1;
            rewardToken = 0;
            clear(phaseTactic);
            clear(phaseApproachMask);
            clear(phaseTopology);
            clear(phaseFormation);
            clear(phaseObjective);
            clear(phaseObjectiveTarget);
            clear(phaseCounterplay);
            clear(phaseStartTick);
            clear(telegraphTick);
            clear(releaseTick);
            clear(deadlineTick);
            clear(outcomeKind);
            clear(outcomeTarget);
            clear(squadCount);
            clear(phaseThreat);
            clear(phaseUnitCount);
            clear(squadApproach);
            clear(squadFormation);
            clear(squadObjective);
            clear(squadObjectiveTarget);
            clear(squadReleaseOffset);
            clear(phaseRoleCounts);
            clear(squadRoleCounts);
        }

        private static void clear(int[] values) {
            for (int index = 0; index < values.length; index++) values[index] = 0;
        }
    }

    /** Immutable accepted or unplannable result. */
    public static final class Plan {
        private final boolean plannable;
        private final int resultCode;
        private final int planHash;
        private final int traceHash;
        private final int acceptedAttempt;
        private final int attemptsEvaluated;
        private final int totalBudget;
        private final int totalThreat;
        private final int totalUnitCount;
        private final int[] roleCounts;
        private final Phase[] phases;
        private final int rewardDecision;
        private final int rewardIndex;
        private final long rewardToken;
        private final boolean fallbackUsed;

        private Plan(boolean plannable, int resultCode, int planHash, int traceHash,
                int acceptedAttempt, int attemptsEvaluated, int totalBudget, int totalThreat,
                int totalUnitCount, int[] roleCounts, Phase[] phases, int rewardDecision,
                int rewardIndex, long rewardToken, boolean fallbackUsed) {
            this.plannable = plannable;
            this.resultCode = resultCode;
            this.planHash = planHash;
            this.traceHash = traceHash;
            this.acceptedAttempt = acceptedAttempt;
            this.attemptsEvaluated = attemptsEvaluated;
            this.totalBudget = totalBudget;
            this.totalThreat = totalThreat;
            this.totalUnitCount = totalUnitCount;
            this.roleCounts = roleCounts.clone();
            this.phases = phases.clone();
            this.rewardDecision = rewardDecision;
            this.rewardIndex = rewardIndex;
            this.rewardToken = rewardToken;
            this.fallbackUsed = fallbackUsed;
        }

        public boolean isPlannable() { return plannable; }
        public boolean isUnplannable() { return !plannable; }
        public int resultCode() { return resultCode; }
        public int planHash() { return planHash; }
        public int traceHash() { return traceHash; }
        public int acceptedAttempt() { return acceptedAttempt; }
        public int attemptsEvaluated() { return attemptsEvaluated; }
        public int totalBudget() { return totalBudget; }
        public int totalThreat() { return totalThreat; }
        public int totalUnitCount() { return totalUnitCount; }
        public int roleCount(int role) { return roleCounts[role]; }
        public int[] roleCounts() { return roleCounts.clone(); }
        public int phaseCount() { return phases.length; }
        public Phase phase(int index) { return phases[index]; }
        public Phase[] phases() { return phases.clone(); }
        public int rewardDecision() { return rewardDecision; }
        public boolean hasReward() { return rewardDecision == REWARD_CANDIDATE; }
        public int rewardIndex() { return rewardIndex; }
        public long rewardToken() { return rewardToken; }
        public boolean fallbackUsed() { return fallbackUsed; }
        public long structuralFingerprint() {
            return RaidPlanGenerator.structuralFingerprint(this);
        }
    }

    /** One phase-local tactical grammar node. */
    public static final class Phase {
        private final int phaseIndex;
        private final int tactic;
        private final int approachMask;
        private final int topology;
        private final int formation;
        private final int objective;
        private final int objectiveTarget;
        private final int counterplay;
        private final int startTick;
        private final int telegraphTick;
        private final int releaseTick;
        private final int deadlineTick;
        private final Outcome[] outcomes;
        private final int[] roleCounts;
        private final int threat;
        private final int unitCount;
        private final Squad[] squads;

        private Phase(int phaseIndex, int tactic, int approachMask, int topology, int formation,
                int objective, int objectiveTarget, int counterplay, int startTick,
                int telegraphTick, int releaseTick, int deadlineTick, Outcome[] outcomes,
                int[] roleCounts, int threat, int unitCount, Squad[] squads) {
            this.phaseIndex = phaseIndex;
            this.tactic = tactic;
            this.approachMask = approachMask;
            this.topology = topology;
            this.formation = formation;
            this.objective = objective;
            this.objectiveTarget = objectiveTarget;
            this.counterplay = counterplay;
            this.startTick = startTick;
            this.telegraphTick = telegraphTick;
            this.releaseTick = releaseTick;
            this.deadlineTick = deadlineTick;
            this.outcomes = outcomes.clone();
            this.roleCounts = roleCounts.clone();
            this.threat = threat;
            this.unitCount = unitCount;
            this.squads = squads.clone();
        }

        public int phaseIndex() { return phaseIndex; }
        public int tactic() { return tactic; }
        public int approachMask() { return approachMask; }
        public int topology() { return topology; }
        public int formation() { return formation; }
        public int objective() { return objective; }
        public int objectiveTarget() { return objectiveTarget; }
        public int counterplay() { return counterplay; }
        public int startTick() { return startTick; }
        public int telegraphTick() { return telegraphTick; }
        public int releaseTick() { return releaseTick; }
        public int deadlineTick() { return deadlineTick; }
        public int outcomeCount() { return outcomes.length; }
        public Outcome outcome(int index) { return outcomes[index]; }
        public Outcome[] outcomes() { return outcomes.clone(); }
        public int threat() { return threat; }
        public int unitCount() { return unitCount; }
        public int roleCount(int role) { return roleCounts[role]; }
        public int[] roleCounts() { return roleCounts.clone(); }
        public int squadCount() { return squads.length; }
        public Squad squad(int index) { return squads[index]; }
        public Squad[] squads() { return squads.clone(); }
    }

    /** One finite forward edge from a phase node. */
    public static final class Outcome {
        private final int kind;
        private final int nextPhase;

        private Outcome(int kind, int nextPhase) {
            this.kind = kind;
            this.nextPhase = nextPhase;
        }

        public int kind() { return kind; }
        public int nextPhase() { return nextPhase; }
        public boolean terminal() { return nextPhase < 0; }
    }

    /** One bounded role allocation with its own tactical route and arrival offset. */
    public static final class Squad {
        private final int phaseIndex;
        private final int squadIndex;
        private final int approach;
        private final int formation;
        private final int objective;
        private final int objectiveTarget;
        private final int releaseOffset;
        private final int[] roleCounts;
        private final int threat;
        private final int unitCount;

        private Squad(int phaseIndex, int squadIndex, int approach, int formation,
                int objective, int objectiveTarget, int releaseOffset, int[] roleCounts,
                int threat, int unitCount) {
            this.phaseIndex = phaseIndex;
            this.squadIndex = squadIndex;
            this.approach = approach;
            this.formation = formation;
            this.objective = objective;
            this.objectiveTarget = objectiveTarget;
            this.releaseOffset = releaseOffset;
            this.roleCounts = roleCounts.clone();
            this.threat = threat;
            this.unitCount = unitCount;
        }

        public int phaseIndex() { return phaseIndex; }
        public int squadIndex() { return squadIndex; }
        public int approach() { return approach; }
        public int formation() { return formation; }
        public int objective() { return objective; }
        public int objectiveTarget() { return objectiveTarget; }
        public int releaseOffset() { return releaseOffset; }
        public int roleCount(int role) { return roleCounts[role]; }
        public int[] roleCounts() { return roleCounts.clone(); }
        public int threat() { return threat; }
        public int unitCount() { return unitCount; }
    }
}
