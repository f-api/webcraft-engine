package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.Attempt;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.Candidate;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.PlanningState;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.WorldAccess;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact dormant per-center 26.3 structure-start decision coordinator. */
public final class Mc263StructureStartDecisionCoordinator {
    private static final byte[] RECEIPT_MAGIC = "SDC263D2".getBytes(StandardCharsets.US_ASCII);
    private final Mc263StructureSetStartPlanner planner;

    private Mc263StructureStartDecisionCoordinator(Mc263StructureSetStartPlanner planner) {
        this.planner = planner;
    }

    public static Mc263StructureStartDecisionCoordinator pinned() {
        return PinnedHolder.INSTANCE;
    }

    private static final class PinnedHolder {
        private static final Mc263StructureStartDecisionCoordinator INSTANCE =
                new Mc263StructureStartDecisionCoordinator(Mc263StructureSetStartPlanner.pinned());

        private PinnedHolder() { }
    }

    /**
     * Resolves the current center chunk only. After capability preflight, every existing start in
     * every possible set is read before the first placement RNG or exclusion query.
     */
    public DecisionPlan decide(long worldSeed, int centerChunkX, int centerChunkZ,
            PlanningState state, WorldAccess world, AuthorityAccess authority) {
        if (authority == null || !authority.hasExistingStartFacts()
                || !authority.hasAttemptGenerator() || !authority.hasValidStartPersistence()) {
            throw new IllegalStateException("complete structure authority capabilities are required");
        }
        planner.preflightDecision(world, state, worldSeed);

        Map<String, SetExistingFacts> existingBySet = new HashMap<>();
        for (String setKey : state.possibleSetKeys()) {
            List<ExistingCheck> checks = new ArrayList<>();
            String validKey = "";
            for (String member : planner.memberKeys(setKey)) {
                ExistingStartFact fact = authority.existingStart(member, centerChunkX, centerChunkZ);
                validateExistingFact(fact);
                checks.add(new ExistingCheck(member, fact.status(), fact.references()));
                if (validKey.isEmpty() && fact.status() == ExistingStart.VALID) validKey = member;
            }
            existingBySet.put(setKey, new SetExistingFacts(List.copyOf(checks), validKey));
        }

        List<CandidateDecision> decisions = new ArrayList<>(state.possibleSetKeys().size());
        for (String setKey : state.possibleSetKeys()) {
            SetExistingFacts facts = existingBySet.get(setKey);
            if (!facts.validStructureKey.isEmpty()) {
                decisions.add(CandidateDecision.existing(setKey, centerChunkX, centerChunkZ,
                        facts.validStructureKey, facts.checks));
                continue;
            }
            Candidate candidate = planner.candidateAt(worldSeed, centerChunkX, centerChunkZ,
                    setKey, state, world);
            if (!candidate.placementChunk() || !candidate.frequencyAccepted()
                    || candidate.excluded()) {
                decisions.add(CandidateDecision.rejected(candidate, facts.checks));
                continue;
            }
            Map<String, ExistingCheck> checksByKey = new HashMap<>();
            for (ExistingCheck check : facts.checks) checksByKey.put(check.structureKey(), check);
            List<GenerationAttempt> generated = new ArrayList<>();
            boolean resolved = false;
            for (int index = 0; index < candidate.attempts().size(); index++) {
                Attempt attempt = candidate.attempts().get(index);
                ExistingCheck prior = checksByKey.get(attempt.structureKey());
                if (prior == null) throw new IllegalStateException("attempt not in existing facts");
                AttemptContext context = new AttemptContext(worldSeed, setKey, centerChunkX,
                        centerChunkZ, candidate.locatePos(), index, attempt.structureKey(),
                        attempt.weight(), prior.references());
                GeneratorResult result = authority.generate(context);
                if (result == null) throw new IllegalStateException("generator result is null");
                generated.add(new GenerationAttempt(attempt.structureKey(), result.valid(),
                        result.generatorReceipt(), prior.references()));
                if (!result.valid()) continue;
                authority.persistValid(new ValidSettlement(setKey, attempt.structureKey(),
                        centerChunkX, centerChunkZ, prior.references(), result.generatorReceipt()));
                decisions.add(CandidateDecision.generated(candidate, Outcome.GENERATED_VALID,
                        attempt.structureKey(), facts.checks, generated, true));
                resolved = true;
                break;
            }
            if (!resolved) {
                // Vanilla discards an invalid generated start. No INVALID settlement is written.
                decisions.add(CandidateDecision.generated(candidate, Outcome.GENERATED_INVALID,
                        "", facts.checks, generated, false));
            }
        }
        return new DecisionPlan(worldSeed, centerChunkX, centerChunkZ, List.copyOf(decisions));
    }

    private static void validateExistingFact(ExistingStartFact fact) {
        if (fact == null || fact.status() == null || fact.references() < 0
                || (fact.status() == ExistingStart.ABSENT && fact.references() != 0)) {
            throw new IllegalStateException("invalid existing-start fact");
        }
    }

    public static byte[] receiptBytes(DecisionPlan plan) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(RECEIPT_MAGIC); out.writeLong(plan.worldSeed());
            out.writeInt(plan.centerChunkX()); out.writeInt(plan.centerChunkZ());
            out.writeInt(plan.decisions().size());
            for (CandidateDecision d : plan.decisions()) {
                writeString(out, d.setKey()); out.writeInt(d.chunkX()); out.writeInt(d.chunkZ());
                out.writeBoolean(d.placementEvaluated()); out.writeBoolean(d.placementChunk());
                out.writeBoolean(d.frequencyAccepted()); out.writeBoolean(d.excluded());
                out.writeByte(d.outcome().ordinal()); writeString(out, d.resolvedStructureKey());
                out.writeBoolean(d.persisted()); out.writeInt(d.existingChecks().size());
                for (ExistingCheck c : d.existingChecks()) { writeString(out, c.structureKey());
                    out.writeByte(c.status().ordinal()); out.writeInt(c.references()); }
                out.writeInt(d.generationAttempts().size());
                for (GenerationAttempt a : d.generationAttempts()) { writeString(out, a.structureKey());
                    out.writeBoolean(a.valid()); out.writeLong(a.generatorReceipt());
                    out.writeInt(a.priorReferences()); }
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length); out.write(bytes);
    }

    public interface AuthorityAccess {
        boolean hasExistingStartFacts();
        boolean hasAttemptGenerator();
        boolean hasValidStartPersistence();
        ExistingStartFact existingStart(String structureKey, int chunkX, int chunkZ);
        GeneratorResult generate(AttemptContext context);
        void persistValid(ValidSettlement settlement);
    }

    public enum ExistingStart { ABSENT, INVALID, VALID }
    public enum Outcome { PLACEMENT_REJECTED, EXISTING_VALID, GENERATED_VALID, GENERATED_INVALID }
    public record ExistingStartFact(ExistingStart status, int references) { }
    public record AttemptContext(long worldSeed, String setKey, int chunkX, int chunkZ,
            BlockPos locatePos, int attemptIndex, String structureKey, int weight,
            int priorReferences) { }
    public record GeneratorResult(boolean valid, long generatorReceipt) { }
    public record ValidSettlement(String setKey, String structureKey, int chunkX, int chunkZ,
            int priorReferences, long generatorReceipt) { }
    public record ExistingCheck(String structureKey, ExistingStart status, int references) { }
    public record GenerationAttempt(String structureKey, boolean valid, long generatorReceipt,
            int priorReferences) { }

    public record CandidateDecision(String setKey, int chunkX, int chunkZ,
            boolean placementEvaluated, boolean placementChunk, boolean frequencyAccepted,
            boolean excluded, Outcome outcome, String resolvedStructureKey,
            List<ExistingCheck> existingChecks, List<GenerationAttempt> generationAttempts,
            boolean persisted) {
        public CandidateDecision { existingChecks = List.copyOf(existingChecks);
            generationAttempts = List.copyOf(generationAttempts); }
        static CandidateDecision existing(String key, int x, int z, String resolved,
                List<ExistingCheck> checks) {
            return new CandidateDecision(key, x, z, false, false, false, false,
                    Outcome.EXISTING_VALID, resolved, checks, List.of(), false);
        }
        static CandidateDecision rejected(Candidate c, List<ExistingCheck> checks) {
            return new CandidateDecision(c.setKey(), c.chunkX(), c.chunkZ(), true,
                    c.placementChunk(), c.frequencyAccepted(), c.excluded(),
                    Outcome.PLACEMENT_REJECTED, "", checks, List.of(), false);
        }
        static CandidateDecision generated(Candidate c, Outcome outcome, String resolved,
                List<ExistingCheck> checks, List<GenerationAttempt> attempts, boolean persisted) {
            return new CandidateDecision(c.setKey(), c.chunkX(), c.chunkZ(), true, true, true,
                    false, outcome, resolved, checks, attempts, persisted);
        }
    }
    public record DecisionPlan(long worldSeed, int centerChunkX, int centerChunkZ,
            List<CandidateDecision> decisions) {
        public DecisionPlan { decisions = List.copyOf(decisions); }
    }
    private record SetExistingFacts(List<ExistingCheck> checks, String validStructureKey) { }
}
