package com.gameexpert.engine;

import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Kind;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Transform;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate.PlannedEntity;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.CushionRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.Lifecycle;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.RuntimeBinding;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntity;
import com.gameexpert.terrain.Blocks;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Pure fail-closed planner for the mutable lifecycle of an authenticated generated Cushion. */
public final class GeneratedCushionActionPolicy {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final byte[] TERMINAL_SETTLEMENT_DOMAIN =
            "GAMEEXPERT/GENERATED-CUSHION/TERMINAL-SETTLEMENT/v1"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DROP_DELIVERY_DOMAIN =
            "GAMEEXPERT/GENERATED-CUSHION/DROP-DELIVERY/v1"
                    .getBytes(StandardCharsets.US_ASCII);

    private GeneratedCushionActionPolicy() {}

    public enum Action { SIT, LEAVE, BREAK, RIDER_LOGOUT, RIDER_DEATH, SUPPORT_LOSS }

    public enum Rejection {
        SOURCE_NOT_LIVE, AGGREGATE_NOT_INSTALLED, KIND_MISMATCH, ENTITY_MISMATCH,
        SOURCE_MISMATCH, REVISION_MISMATCH, REVISION_EXHAUSTED, ACTOR_INVALID,
        ACTOR_NOT_PRESENT, ACTOR_DEAD, ACTOR_NOT_CROUCHING_ALLOWED, ACTOR_ALREADY_RIDING,
        OUT_OF_REACH, OCCUPIED, NOT_RIDER, LOGOUT_STATE_MISMATCH, DEATH_STATE_MISMATCH,
        SUPPORT_LOST, SUPPORT_STILL_PRESENT, FORBIDDEN_COLLISION
    }

    public enum DurableMutation { UPDATE_CUSHION, TOMBSTONE_SOURCE_AND_STATE }
    public enum PublicationExpectation { UPDATE_AFTER_COMMIT, REMOVE_AFTER_COMMIT }

    /** Required durable outbox behavior; a duplicate delivery key cannot create another drop. */
    public enum OutboxDedupeContract {
        INSERT_IF_ABSENT_BY_DELIVERY_KEY_IN_TERMINAL_TRANSACTION
    }

    /** Exact source/aggregate/entity/revision inputs; no legacy numeric alias is accepted. */
    public record Authority(WorldStructureEntity source, StructureEntityAggregate aggregate,
            CushionRuntimeSnapshot current, long authoritativeEntityId, long expectedRevision) {
        public Authority {
            Objects.requireNonNull(source, "generated Cushion source");
            Objects.requireNonNull(aggregate, "generated Cushion aggregate");
            Objects.requireNonNull(current, "generated Cushion runtime snapshot");
        }
    }

    /** Complete observed context; unused facts remain explicit rather than inferred. */
    public record Request(Action action, String actor, boolean actorPresent, boolean actorAlive,
            boolean actorCrouching, boolean actorRidingAnotherCushion, boolean withinReach,
            boolean supportPresent, boolean forbiddenCushionOrChainCollision) {
        public Request { Objects.requireNonNull(action, "generated Cushion action"); }
    }

    /** Detached immutable source proof copied from mutable authority objects. */
    public record SourceProof(long worldId, int chunkX, int chunkZ,
            long authoritativeEntityId, String installationIdentity,
            String installationSourceFingerprint, int encounterOrdinal,
            String sourceRowFingerprint) {
        public SourceProof {
            Objects.requireNonNull(installationIdentity, "installation identity");
            Objects.requireNonNull(installationSourceFingerprint,
                    "installation source fingerprint");
            Objects.requireNonNull(sourceRowFingerprint, "source row fingerprint");
        }
    }

    /**
     * Durable exactly-once settlement input, never an executable drop command. The transaction
     * owner atomically inserts its delivery key with the tombstone and later mints an unforgeable
     * committed receipt for the actual outbox consumer.
     */
    public static final class TerminalSettlement {
        private final String settlementKey;
        private final String deliveryKey;
        private final int registeredItemType;
        private final int count;
        private final double x, y, z;
        private final OutboxDedupeContract dedupeContract;

        private TerminalSettlement(String settlementKey, String deliveryKey,
                int registeredItemType, int count, double x, double y, double z,
                OutboxDedupeContract dedupeContract) {
            this.settlementKey = fingerprint(settlementKey, "terminal settlement key");
            this.deliveryKey = fingerprint(deliveryKey, "terminal delivery key");
            if (registeredItemType != Blocks.LIME_CUSHION || count != 1
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("invalid generated Cushion terminal settlement");
            }
            this.registeredItemType = registeredItemType;
            this.count = count;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dedupeContract = Objects.requireNonNull(dedupeContract, "outbox dedupe contract");
        }

        public String settlementKey() { return settlementKey; }
        public String deliveryKey() { return deliveryKey; }
        public int registeredItemType() { return registeredItemType; }
        public int count() { return count; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public OutboxDedupeContract dedupeContract() { return dedupeContract; }
    }

    /** Exact durable operation; private construction prevents arbitrary action/revision plans. */
    public static final class DurablePlan {
        private final Action cause;
        private final SourceProof source;
        private final DurableMutation mutation;
        private final long expectedRevision, nextRevision;
        private final String rider, customName;
        private final TerminalSettlement terminalSettlement;

        private DurablePlan(Action cause, SourceProof source, DurableMutation mutation,
                long expectedRevision, long nextRevision, String rider, String customName,
                TerminalSettlement terminalSettlement) {
            this.cause = Objects.requireNonNull(cause, "generated Cushion action");
            this.source = Objects.requireNonNull(source, "generated Cushion source proof");
            this.mutation = Objects.requireNonNull(mutation, "generated Cushion mutation");
            if (expectedRevision < 0L || expectedRevision == Long.MAX_VALUE
                    || nextRevision != expectedRevision + 1L) {
                throw new IllegalArgumentException("invalid generated Cushion revision transition");
            }
            boolean terminalCause = cause == Action.BREAK || cause == Action.SUPPORT_LOSS;
            if (terminalCause != (mutation == DurableMutation.TOMBSTONE_SOURCE_AND_STATE)
                    || terminalCause != (terminalSettlement != null)
                    || (cause == Action.SIT) != (rider != null)) {
                throw new IllegalArgumentException("invalid generated Cushion action plan");
            }
            this.expectedRevision = expectedRevision;
            this.nextRevision = nextRevision;
            this.rider = rider;
            this.customName = customName;
            this.terminalSettlement = terminalSettlement;
        }

        public Action cause() { return cause; }
        public SourceProof source() { return source; }
        public DurableMutation mutation() { return mutation; }
        public long expectedRevision() { return expectedRevision; }
        public long nextRevision() { return nextRevision; }
        public String rider() { return rider; }
        public String customName() { return customName; }
        public TerminalSettlement terminalSettlement() { return terminalSettlement; }
        public boolean terminal() { return terminalSettlement != null; }
    }

    /** Non-authoritative expectation. It cannot substitute for a committed receipt. */
    public static final class ExpectedEffects {
        private final PublicationExpectation publication;
        private final String rider, terminalDeliveryKey;

        private ExpectedEffects(PublicationExpectation publication, String rider,
                String terminalDeliveryKey) {
            this.publication = Objects.requireNonNull(publication, "publication expectation");
            this.rider = rider;
            this.terminalDeliveryKey = terminalDeliveryKey;
        }

        public PublicationExpectation publication() { return publication; }
        public String rider() { return rider; }
        public String terminalDeliveryKey() { return terminalDeliveryKey; }

        /** Runtime fail-close proof for precommit or forged-effect use. */
        public void requireCommittedAuthority() {
            throw new IllegalStateException(
                    "expected effects are non-authoritative; committed coordinator receipt required");
        }
    }

    /** Immutable persistence-first result; only the policy can construct one. */
    public static final class Plan {
        private final DurablePlan durable;
        private final ExpectedEffects expectedEffects;

        private Plan(DurablePlan durable, ExpectedEffects expectedEffects) {
            this.durable = Objects.requireNonNull(durable, "durable plan");
            this.expectedEffects = Objects.requireNonNull(expectedEffects, "expected effects");
            boolean terminal = durable.terminal();
            if (terminal != (expectedEffects.publication()
                    == PublicationExpectation.REMOVE_AFTER_COMMIT)
                    || terminal != (expectedEffects.terminalDeliveryKey() != null)
                    || (terminal && !durable.terminalSettlement().deliveryKey()
                            .equals(expectedEffects.terminalDeliveryKey()))) {
                throw new IllegalArgumentException("generated Cushion effect description drift");
            }
        }

        public DurablePlan durable() { return durable; }
        public ExpectedEffects expectedEffects() { return expectedEffects; }
        public boolean terminal() { return durable.terminal(); }
    }

    public static final class Decision {
        private final List<Rejection> rejections;
        private final Plan plan;

        private Decision(List<Rejection> rejections, Plan plan) {
            this.rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
            if ((this.rejections.isEmpty()) == (plan == null)) {
                throw new IllegalArgumentException("decision must contain plan or rejections");
            }
            this.plan = plan;
        }

        public List<Rejection> rejections() { return rejections; }
        public Plan plan() { return plan; }
        public boolean accepted() { return plan != null; }
    }

    public static Decision plan(Authority authority, Request request) {
        Objects.requireNonNull(authority, "generated Cushion authority");
        Objects.requireNonNull(request, "generated Cushion request");
        ArrayList<Rejection> rejected = new ArrayList<>();
        validateAuthority(authority, rejected);
        validateAction(authority.current(), request, rejected);
        if (!rejected.isEmpty()) return new Decision(rejected, null);

        CushionRuntimeSnapshot current = authority.current();
        boolean terminal = request.action() == Action.BREAK
                || request.action() == Action.SUPPORT_LOSS;
        String rider = request.action() == Action.SIT ? request.actor() : null;
        SourceProof source = sourceProof(current.binding());
        long nextRevision = authority.expectedRevision() + 1L;
        TerminalSettlement settlement = terminal
                ? terminalSettlement(source, authority.expectedRevision(), nextRevision,
                        request.action(), current.transform())
                : null;
        DurablePlan durable = new DurablePlan(request.action(), source,
                terminal ? DurableMutation.TOMBSTONE_SOURCE_AND_STATE
                        : DurableMutation.UPDATE_CUSHION,
                authority.expectedRevision(), nextRevision, rider, current.customName(), settlement);
        ExpectedEffects expected = new ExpectedEffects(
                terminal ? PublicationExpectation.REMOVE_AFTER_COMMIT
                        : PublicationExpectation.UPDATE_AFTER_COMMIT,
                rider, terminal ? settlement.deliveryKey() : null);
        return new Decision(List.of(), new Plan(durable, expected));
    }

    private static void validateAuthority(Authority authority, List<Rejection> rejected) {
        WorldStructureEntity source = authority.source();
        StructureEntityAggregate aggregate = authority.aggregate();
        CushionRuntimeSnapshot current = authority.current();
        RuntimeBinding binding = current.binding();
        if (source.bindingStatus() != WorldStructureEntity.BindingStatus.LIVE
                || current.lifecycle() != Lifecycle.LIVE) add(rejected, Rejection.SOURCE_NOT_LIVE);
        if (!aggregate.installed()) add(rejected, Rejection.AGGREGATE_NOT_INSTALLED);
        if (binding.kind() != Kind.CUSHION
                || !"minecraft:cushion".equals(source.getEntityKey())) {
            add(rejected, Rejection.KIND_MISMATCH);
        }
        if (authority.authoritativeEntityId() <= 0L
                || binding.entityId() != authority.authoritativeEntityId()
                || source.getAuthoritativeEntityId() != authority.authoritativeEntityId()) {
            add(rejected, Rejection.ENTITY_MISMATCH);
        }
        if (authority.expectedRevision() < 0L
                || current.revision() != authority.expectedRevision()) {
            add(rejected, Rejection.REVISION_MISMATCH);
        }
        if (authority.expectedRevision() == Long.MAX_VALUE) {
            add(rejected, Rejection.REVISION_EXHAUSTED);
        }
        if (!matchesExactSource(source, aggregate, current)) add(rejected, Rejection.SOURCE_MISMATCH);
    }

    private static boolean matchesExactSource(WorldStructureEntity source,
            StructureEntityAggregate aggregate, CushionRuntimeSnapshot current) {
        RuntimeBinding binding = current.binding();
        if (!aggregate.installed() || binding.encounterOrdinal() < 0
                || binding.encounterOrdinal() >= aggregate.plannedEntities().size()
                || source.getWorldId() != binding.worldId()
                || source.getChunkX() != binding.chunkX() || source.getChunkZ() != binding.chunkZ()
                || !source.getLaneInstallationIdentity().equals(binding.installationIdentity())
                || !source.getInstallationSourceFingerprint()
                        .equals(binding.installationSourceFingerprint())
                || source.getEncounterOrdinal() != binding.encounterOrdinal()
                || !source.getRowFingerprint().equals(binding.sourceRowFingerprint())
                || !aggregate.installationIdentity().equals(binding.installationIdentity())
                || !aggregate.sourceFingerprint().equals(binding.installationSourceFingerprint())) {
            return false;
        }
        PlannedEntity planned = aggregate.plannedEntities().get(binding.encounterOrdinal());
        return planned.authoritativeEntityId() == binding.entityId()
                && planned.rowFingerprint().equals(binding.sourceRowFingerprint())
                && sameTransform(current.transform(), planned) && "lime".equals(current.color())
                && current.blockX() == Math.floor(current.transform().x())
                && current.blockY() == Math.floor(current.transform().y())
                && current.blockZ() == Math.floor(current.transform().z())
                && !current.invulnerable()
                && source.matches(binding.worldId(), binding.chunkX(), binding.chunkZ(),
                        binding.installationIdentity(), binding.installationSourceFingerprint(),
                        planned);
    }

    private static boolean sameTransform(Transform transform, PlannedEntity planned) {
        return Double.doubleToRawLongBits(transform.x()) == Double.doubleToRawLongBits(planned.x())
                && Double.doubleToRawLongBits(transform.y()) == Double.doubleToRawLongBits(planned.y())
                && Double.doubleToRawLongBits(transform.z()) == Double.doubleToRawLongBits(planned.z())
                && Float.floatToRawIntBits(transform.yaw()) == Float.floatToRawIntBits(planned.yaw())
                && Float.floatToRawIntBits(transform.pitch()) == Float.floatToRawIntBits(planned.pitch())
                && Double.doubleToRawLongBits(transform.velocityX())
                        == Double.doubleToRawLongBits(planned.velocityX())
                && Double.doubleToRawLongBits(transform.velocityY())
                        == Double.doubleToRawLongBits(planned.velocityY())
                && Double.doubleToRawLongBits(transform.velocityZ())
                        == Double.doubleToRawLongBits(planned.velocityZ());
    }

    private static void validateAction(CushionRuntimeSnapshot current, Request request,
            List<Rejection> rejected) {
        if (request.action() != Action.SUPPORT_LOSS && !validActor(request.actor())) {
            add(rejected, Rejection.ACTOR_INVALID);
        }
        switch (request.action()) {
            case SIT -> {
                if (!request.actorPresent()) add(rejected, Rejection.ACTOR_NOT_PRESENT);
                if (!request.actorAlive()) add(rejected, Rejection.ACTOR_DEAD);
                if (request.actorCrouching()) add(rejected, Rejection.ACTOR_NOT_CROUCHING_ALLOWED);
                if (request.actorRidingAnotherCushion()) add(rejected, Rejection.ACTOR_ALREADY_RIDING);
                if (!request.withinReach()) add(rejected, Rejection.OUT_OF_REACH);
                if (current.rider() != null) add(rejected, Rejection.OCCUPIED);
                if (!request.supportPresent()) add(rejected, Rejection.SUPPORT_LOST);
                if (request.forbiddenCushionOrChainCollision()) {
                    add(rejected, Rejection.FORBIDDEN_COLLISION);
                }
            }
            case LEAVE -> {
                if (!Objects.equals(current.rider(), request.actor())) add(rejected, Rejection.NOT_RIDER);
            }
            case BREAK -> {
                if (!request.actorPresent()) add(rejected, Rejection.ACTOR_NOT_PRESENT);
                if (!request.actorAlive()) add(rejected, Rejection.ACTOR_DEAD);
                if (!request.withinReach()) add(rejected, Rejection.OUT_OF_REACH);
            }
            case RIDER_LOGOUT -> {
                if (!Objects.equals(current.rider(), request.actor())) add(rejected, Rejection.NOT_RIDER);
                if (request.actorPresent()) add(rejected, Rejection.LOGOUT_STATE_MISMATCH);
            }
            case RIDER_DEATH -> {
                if (!Objects.equals(current.rider(), request.actor())) add(rejected, Rejection.NOT_RIDER);
                if (request.actorAlive()) add(rejected, Rejection.DEATH_STATE_MISMATCH);
            }
            case SUPPORT_LOSS -> {
                if (request.supportPresent()) add(rejected, Rejection.SUPPORT_STILL_PRESENT);
            }
        }
    }

    private static TerminalSettlement terminalSettlement(SourceProof source,
            long expectedRevision, long nextRevision, Action cause, Transform transform) {
        String settlementKey = digest(TERMINAL_SETTLEMENT_DOMAIN, source, expectedRevision,
                nextRevision, cause.name(), 0, 0);
        String deliveryKey = digest(DROP_DELIVERY_DOMAIN, source, expectedRevision,
                nextRevision, cause.name(), Blocks.LIME_CUSHION, 1);
        return new TerminalSettlement(settlementKey, deliveryKey, Blocks.LIME_CUSHION, 1,
                transform.x(), transform.y() + CushionSystem.HEIGHT, transform.z(),
                OutboxDedupeContract.INSERT_IF_ABSENT_BY_DELIVERY_KEY_IN_TERMINAL_TRANSACTION);
    }

    private static String digest(byte[] domain, SourceProof source, long expectedRevision,
            long nextRevision, String cause, int itemType, int count) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            updateLong(digest, source.worldId()); updateInt(digest, source.chunkX());
            updateInt(digest, source.chunkZ()); updateLong(digest, source.authoritativeEntityId());
            updateText(digest, source.installationIdentity());
            updateText(digest, source.installationSourceFingerprint());
            updateInt(digest, source.encounterOrdinal()); updateText(digest, source.sourceRowFingerprint());
            updateLong(digest, expectedRevision); updateLong(digest, nextRevision);
            updateText(digest, cause); updateInt(digest, itemType); updateInt(digest, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void updateText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length); digest.update(bytes);
    }

    private static SourceProof sourceProof(RuntimeBinding binding) {
        return new SourceProof(binding.worldId(), binding.chunkX(), binding.chunkZ(),
                binding.entityId(), binding.installationIdentity(),
                binding.installationSourceFingerprint(), binding.encounterOrdinal(),
                binding.sourceRowFingerprint());
    }

    private static String fingerprint(String value, String label) {
        if (value == null || value.length() != 64) throw new IllegalArgumentException(label);
        try { HexFormat.of().parseHex(value); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(label, invalid); }
        return value;
    }

    private static boolean validActor(String actor) {
        return actor != null && PLAYER_NAME.matcher(actor).matches();
    }

    private static void add(List<Rejection> rejected, Rejection value) {
        if (!rejected.contains(value)) rejected.add(value);
    }
}
