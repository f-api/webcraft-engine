package com.gameexpert.engine;

import com.gameexpert.engine.GeneratedCushionActionPolicy.Action;
import com.gameexpert.engine.GeneratedCushionActionPolicy.Authority;
import com.gameexpert.engine.GeneratedCushionActionPolicy.DurableMutation;
import com.gameexpert.engine.GeneratedCushionActionPolicy.Plan;
import com.gameexpert.engine.GeneratedCushionActionPolicy.Request;
import com.gameexpert.engine.GeneratedCushionActionPolicy.TerminalSettlement;
import com.gameexpert.engine.WorldRuntime.GeneratedCushionActionClaim;
import com.gameexpert.engine.WorldRuntime.GeneratedCushionActionEvidence;
import com.gameexpert.engine.WorldRuntime.GeneratedCushionPersistencePublication;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Kind;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.CushionRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.RuntimeBinding;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.RuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityStateRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntity;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityRepository;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.entity.WorldGroundItem;
import com.gameexpert.ground.repository.WorldGroundItemRepository;
import com.gameexpert.ground.repository.WorldGroundMutationReceiptRepository;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import jakarta.annotation.PreDestroy;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction boundary for policy-authenticated mutable generated-entity state. */
@Service
public class GeneratedStructureEntityMutationCoordinator {
    private static final int TERMINAL_GROUND_NAMESPACE = 7;

    private final WorldStructureEntityRepository sources;
    private final WorldGeneratedStructureEntityStateRepository states;
    private final GroundMutationSettlementService groundSettlements;
    private final WorldGroundMutationReceiptRepository groundReceipts;
    private final WorldGroundItemRepository groundItems;
    private final Map<WorldRuntime, Map<Long, IssuedCushionAction>> issued = new WeakHashMap<>();
    private final Map<WorldRuntime, ArrayDeque<CommittedCushionMutation>> committed =
            new WeakHashMap<>();
    // Capability instances retain identity semantics (they do not override equals/hashCode),
    // while abandoned pending tokens must not retain their state forever.
    private final Map<GeneratedEntityMutationCapability, GeneratedEntityMutationBinding>
            generatedEntityCapabilities = new WeakHashMap<>();

    public GeneratedStructureEntityMutationCoordinator(WorldStructureEntityRepository sources,
            WorldGeneratedStructureEntityStateRepository states,
            GroundMutationSettlementService groundSettlements,
            WorldGroundMutationReceiptRepository groundReceipts,
            WorldGroundItemRepository groundItems) {
        this.sources = Objects.requireNonNull(sources, "generated entity source repository");
        this.states = Objects.requireNonNull(states, "generated entity state repository");
        this.groundSettlements = Objects.requireNonNull(groundSettlements,
                "ground mutation settlement service");
        this.groundReceipts = Objects.requireNonNull(groundReceipts,
                "ground mutation receipt repository");
        this.groundItems = Objects.requireNonNull(groundItems, "ground item repository");
    }

    /**
     * The old values-only minting shape cannot prove a source aggregate or a next state. Keep the
     * symbol only so an unupdated caller fails closed at its handoff instead of silently receiving
     * a weaker capability.
     */
    public synchronized GeneratedEntityMutationCapability prepareGeneratedEntityMutation(
            WorldGeneratedStructureEntityState state, GeneratedEntityMutationOperation operation) {
        throw new IllegalStateException(
                "generic generated entity mutation capability requires aggregate identity and "
                        + "exact intended next state");
    }

    /**
     * Mints a one-shot capability bound to the complete current snapshot, aggregate identity,
     * source fingerprints, operation, and caller-proven exact next snapshot.
     */
    public synchronized GeneratedEntityMutationCapability prepareGeneratedEntityMutation(
            WorldGeneratedStructureEntityState state, StructureEntityAggregate aggregate,
            RuntimeSnapshot intendedNextState, GeneratedEntityMutationOperation operation) {
        Objects.requireNonNull(state, "generated entity state");
        Objects.requireNonNull(aggregate, "generated entity aggregate");
        Objects.requireNonNull(intendedNextState, "intended generated entity next state");
        Objects.requireNonNull(operation, "generated entity mutation operation");
        state.requireValidDurableState();
        RuntimeSnapshot current = state.runtimeSnapshot();
        Kind kind = current.binding().kind();
        if (current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || !operation.allowedFor(kind)) {
            throw new IllegalStateException(
                    "generic generated entity mutation capability is not valid for this state");
        }
        requireAggregateBinding(aggregate, current.binding());
        validateGenericNextState(current, intendedNextState, operation);

        RuntimeSnapshot detachedCurrent = copySnapshot(current);
        RuntimeSnapshot detachedNext = copySnapshot(intendedNextState);
        GeneratedEntityMutationBinding binding = new GeneratedEntityMutationBinding(state,
                detachedCurrent, detachedNext, aggregate.installationIdentity(),
                aggregate.sourceFingerprint(), operation);
        GeneratedEntityMutationCapability capability = new GeneratedEntityMutationCapability(this,
                state, detachedCurrent, detachedNext, aggregate.installationIdentity(),
                aggregate.sourceFingerprint(), operation);
        generatedEntityCapabilities.put(capability, binding);
        return capability;
    }

    /** Mints the only capability that may change an Armor Stand's equipment item. */
    public synchronized GeneratedEntityMutationCapability
            prepareArmorStandEquipmentSettlement(
                    WorldGeneratedStructureEntityState state,
                    StructureEntityAggregate aggregate, String nextEquipmentItem) {
        Objects.requireNonNull(state, "generated Armor Stand state");
        Objects.requireNonNull(nextEquipmentItem, "next Armor Stand equipment item");
        RuntimeSnapshot raw = state.runtimeSnapshot();
        if (!(raw instanceof ArmorStandRuntimeSnapshot current)
                || current.lifecycle()
                        != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("live Armor Stand equipment authority is required");
        }
        ArmorStandRuntimeSnapshot next = new ArmorStandRuntimeSnapshot(current.binding(),
                Math.addExact(current.revision(), 1L), current.lifecycle(), current.transform(),
                current.poseHead(), current.poseBody(), current.equipmentSlot(),
                nextEquipmentItem, current.showArms(), current.small(), current.noBasePlate(),
                current.invisible(), current.invulnerable(), current.disabledSlots(),
                current.health());
        return prepareGeneratedEntityMutation(state, aggregate, next,
                GeneratedEntityMutationOperation.ARMOR_STAND_EQUIPMENT_SETTLEMENT);
    }

    /**
     * Commits one exact Armor Stand terminal action against the locked durable source and state.
     * The caller supplies the complete live snapshot and installed aggregate it resolved; this
     * transaction re-authenticates both rather than accepting an entity ID as authority.
     */
    @Transactional
    public ArmorStandRuntimeSnapshot commitArmorStandTerminal(
            ArmorStandRuntimeSnapshot expectedCurrent, StructureEntityAggregate aggregate) {
        Objects.requireNonNull(expectedCurrent, "expected Armor Stand state");
        Objects.requireNonNull(aggregate, "generated entity aggregate");
        RuntimeBinding binding = expectedCurrent.binding();
        if (binding.kind() != Kind.ARMOR_STAND
                || expectedCurrent.lifecycle()
                        != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("live Armor Stand authority is required");
        }
        WorldGeneratedStructureEntityState state = states
                .findLockedByWorldIdAndAuthoritativeEntityId(
                        binding.worldId(), binding.entityId())
                .orElseThrow(() -> new IllegalStateException(
                        "durable Armor Stand state is unavailable"));
        RuntimeSnapshot durableCurrent = state.runtimeSnapshot();
        if (!sameSnapshot(expectedCurrent, durableCurrent)) {
            throw new IllegalStateException("Armor Stand runtime and durable state differ");
        }
        requireAggregateBinding(aggregate, binding);
        WorldStructureEntity source = sources.findByWorldIdAndAuthoritativeEntityId(
                binding.worldId(), binding.entityId()).orElseThrow(() ->
                        new IllegalStateException("durable Armor Stand source is unavailable"));
        ArmorStandRuntimeSnapshot next = new ArmorStandRuntimeSnapshot(binding,
                Math.addExact(expectedCurrent.revision(), 1L),
                WorldGeneratedStructureEntityState.Lifecycle.DEAD,
                expectedCurrent.transform(), expectedCurrent.poseHead(),
                expectedCurrent.poseBody(), expectedCurrent.equipmentSlot(),
                expectedCurrent.equipmentItem(), expectedCurrent.showArms(),
                expectedCurrent.small(), expectedCurrent.noBasePlate(),
                expectedCurrent.invisible(), expectedCurrent.invulnerable(),
                expectedCurrent.disabledSlots(), 0.0f);
        GeneratedEntityMutationCapability capability = prepareGeneratedEntityMutation(
                state, aggregate, next, GeneratedEntityMutationOperation.TERMINAL);
        state.markArmorStandDead(capability, binding.worldId(), binding.chunkX(),
                binding.chunkZ(), binding.installationIdentity(), source, aggregate);
        sources.save(source);
        states.save(state);
        return (ArmorStandRuntimeSnapshot) state.runtimeSnapshot();
    }

    /** Validates and consumes a coordinator-issued generic capability before state mutation. */
    public static void requireGeneratedEntityMutationCapability(
            WorldGeneratedStructureEntityState state, GeneratedEntityMutationCapability capability,
            Kind expectedKind, GeneratedEntityMutationOperation expectedOperation,
            RuntimeSnapshot actualNextState) {
        Objects.requireNonNull(state, "generated entity state");
        Objects.requireNonNull(capability, "generated entity mutation capability");
        Objects.requireNonNull(expectedKind, "expected generated entity kind");
        Objects.requireNonNull(expectedOperation, "expected generated entity operation");
        Objects.requireNonNull(actualNextState, "actual generated entity next state");
        GeneratedStructureEntityMutationCoordinator owner = capability.owner;
        if (owner == null) {
            throw new IllegalStateException(
                    "generated entity mutation capability is foreign, forged, or stale");
        }
        owner.claim(capability, state, expectedKind, expectedOperation, actualNextState);
    }

    /** Cancels one pending capability; foreign, consumed, and cleared tokens are no-ops. */
    public synchronized boolean cancelGeneratedEntityMutation(
            GeneratedEntityMutationCapability capability) {
        if (capability == null || capability.owner != this) return false;
        return generatedEntityCapabilities.remove(capability) != null;
    }

    /** Clears all pending generic capabilities at a coordinator lifecycle boundary. */
    @PreDestroy
    public synchronized void clearGeneratedEntityCapabilities() {
        generatedEntityCapabilities.clear();
        issued.clear();
        committed.clear();
    }

    private synchronized void claim(GeneratedEntityMutationCapability capability,
            WorldGeneratedStructureEntityState state, Kind expectedKind,
            GeneratedEntityMutationOperation expectedOperation, RuntimeSnapshot actualNextState) {
        GeneratedEntityMutationBinding binding = generatedEntityCapabilities.get(capability);
        if (capability.owner != this || binding == null
                || capability.state != binding.state
                || !sameSnapshot(capability.currentState, binding.currentState)
                || !sameSnapshot(capability.nextState, binding.nextState)
                || !Objects.equals(capability.aggregateInstallationIdentity,
                        binding.aggregateInstallationIdentity)
                || !Objects.equals(capability.aggregateSourceFingerprint,
                        binding.aggregateSourceFingerprint)
                || capability.operation != binding.operation || binding.state != state
                || binding.operation != expectedOperation
                || binding.currentState.binding().kind() != expectedKind) {
            generatedEntityCapabilities.remove(capability);
            throw new IllegalStateException(
                    "generated entity mutation capability is foreign, forged, stale, replayed, "
                            + "wrong-kind, wrong-revision, or wrong-operation");
        }
        RuntimeSnapshot current;
        try {
            current = state.runtimeSnapshot();
        } catch (RuntimeException | Error invalid) {
            generatedEntityCapabilities.remove(capability);
            throw new IllegalStateException(
                    "generated entity mutation capability is foreign, forged, stale, replayed, "
                            + "wrong-kind, wrong-revision, or wrong-operation", invalid);
        }
        if (!sameSnapshot(binding.currentState, current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || !Objects.equals(binding.aggregateInstallationIdentity,
                        current.binding().installationIdentity())
                || !Objects.equals(binding.aggregateSourceFingerprint,
                        current.binding().installationSourceFingerprint())) {
            generatedEntityCapabilities.remove(capability);
            throw new IllegalStateException(
                    "generated entity mutation capability is foreign, forged, stale, replayed, "
                            + "wrong-kind, wrong-revision, or wrong-operation");
        }
        try {
            validateGenericNextState(current, actualNextState, expectedOperation);
        } catch (RuntimeException | Error invalidNextState) {
            // The current hash is still valid, so the caller may retry the same token with the
            // exact next state it originally proved. A malformed next state never claims it.
            throw new IllegalStateException(
                    "generated entity mutation intended next state is invalid or substituted",
                    invalidNextState);
        }
        if (!sameSnapshot(binding.nextState, actualNextState)) {
            throw new IllegalStateException(
                    "generated entity mutation intended next state is invalid or substituted");
        }
        generatedEntityCapabilities.remove(capability);
    }

    /**
     * Validates the coordinator-only opaque proof used by the state aggregate's Cushion update
     * method. The proof is never returned by the public Cushion action preparation API.
     */
    public static void requireCushionMutationAuthority(
            WorldGeneratedStructureEntityState state, CushionMutationAuthority authority,
            CushionRuntimeSnapshot actualNextState) {
        Objects.requireNonNull(state, "generated Cushion state");
        Objects.requireNonNull(authority, "generated Cushion mutation authority");
        Objects.requireNonNull(actualNextState, "actual generated Cushion next state");
        GeneratedStructureEntityMutationCoordinator owner = authority.owner;
        if (owner == null) {
            throw new IllegalStateException(
                    "generated Cushion mutation authority is foreign, forged, or stale");
        }
        owner.claimCushionMutationAuthority(state, authority, actualNextState);
    }

    private synchronized void claimCushionMutationAuthority(
            WorldGeneratedStructureEntityState state, CushionMutationAuthority authority,
            CushionRuntimeSnapshot actualNextState) {
        if (authority.owner != this) {
            throw new IllegalStateException(
                    "generated Cushion mutation authority is foreign, forged, or stale");
        }
        WorldRuntime runtime = authority.runtime.get();
        IssuedCushionAction mutation = runtime == null
                ? null : lookup(runtime, authority.capability);
        if (mutation == null || mutation.authority != authority || !mutation.inFlight) {
            throw new IllegalStateException(
                    "generated Cushion mutation authority is foreign, forged, stale, or replayed");
        }
        CushionRuntimeSnapshot current;
        try {
            current = requireLiveCushion(state.runtimeSnapshot());
        } catch (RuntimeException | Error invalid) {
            throw new IllegalStateException(
                    "generated Cushion mutation authority is foreign, forged, or stale", invalid);
        }
        if (!mutation.current.equals(current)
                || authority.capability.expectedRevision() != current.revision()
                || authority.capability.nextRevision() != actualNextState.revision()
                || actualNextState.lifecycle()
                        != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("generated Cushion mutation authority is stale");
        }
        String rider = mutation.request.action() == Action.SIT
                ? mutation.request.actor() : null;
        CushionRuntimeSnapshot expectedNext = new CushionRuntimeSnapshot(current.binding(),
                Math.addExact(current.revision(), 1L),
                WorldGeneratedStructureEntityState.Lifecycle.LIVE, current.transform(),
                current.color(), current.blockX(), current.blockY(), current.blockZ(),
                current.invulnerable(), rider, current.customName());
        if (!sameSnapshot(expectedNext, actualNextState)
                || mutation.publication == null
                || !sameSnapshot(mutation.publication.next(), actualNextState)) {
            throw new IllegalStateException(
                    "generated Cushion mutation authority next state is invalid or substituted");
        }
    }

    /** Claims exact runtime-issued evidence; the locked transaction recomputes policy authority. */
    public synchronized CushionActionCapability prepareCushionAction(WorldRuntime runtime,
            StructureEntityAggregate aggregate, long authoritativeEntityId,
            GeneratedCushionActionEvidence evidence,
            GroundMutationCommand terminalGroundCommand) {
        Objects.requireNonNull(runtime, "world runtime");
        Objects.requireNonNull(aggregate, "generated entity aggregate");
        Objects.requireNonNull(evidence, "generated Cushion action evidence");
        GeneratedCushionActionClaim claim = runtime.claimGeneratedCushionActionEvidence(
                evidence, authoritativeEntityId);
        try {
            Request request = claim.request();
            boolean terminal = request.action() == Action.BREAK
                    || request.action() == Action.SUPPORT_LOSS;
            if (terminal != (terminalGroundCommand != null)) {
                throw new IllegalArgumentException(
                        "terminal generated Cushion action requires exactly one ground command");
            }
            CushionRuntimeSnapshot current = claim.current();
            if (!current.equals(liveRuntimeCushion(runtime, authoritativeEntityId))) {
                throw new IllegalStateException("generated Cushion runtime evidence is stale");
            }
            requireAggregateBinding(aggregate, current.binding());
            if (terminal && (!Objects.equals(terminalGroundCommand.worldId(),
                    current.binding().worldId())
                    || terminalGroundCommand.expectedGroundRevision() != runtime.groundRevision())) {
                throw new IllegalStateException("generated Cushion ground baseline is stale");
            }
            Map<Long, IssuedCushionAction> existingByEntity = issued.get(runtime);
            IssuedCushionAction existing = existingByEntity == null
                    ? null : existingByEntity.get(authoritativeEntityId);
            if (existing != null && existing.inFlight) {
                throw new IllegalStateException(
                        "generated Cushion action already has an in-flight transaction");
            }

            GeneratedCushionPersistencePublication publication = null;
            if (!terminal) {
                String rider = request.action() == Action.SIT ? request.actor() : null;
                publication = runtime.prepareGeneratedCushionPersistencePublication(
                        authoritativeEntityId, rider, current.customName());
                if (!publication.current().equals(current)) {
                    runtime.revokeGeneratedCushionPersistencePublication(publication);
                    throw new IllegalStateException("generated Cushion runtime baseline changed");
                }
            }

            CushionActionCapability capability = new CushionActionCapability(this,
                    current.binding().worldId(), authoritativeEntityId, current.revision(),
                    Math.addExact(current.revision(), 1L), request.action());
            CushionMutationAuthority authority = new CushionMutationAuthority(this, runtime,
                    capability);
            Map<Long, IssuedCushionAction> byEntity = existingByEntity == null
                    ? issued.computeIfAbsent(runtime, ignored -> new HashMap<>()) : existingByEntity;
            IssuedCushionAction superseded = byEntity.put(authoritativeEntityId,
                    new IssuedCushionAction(capability, authority, aggregate, current, request,
                            claim, terminalGroundCommand, publication));
            if (superseded != null) revoke(runtime, superseded);
            return capability;
        } catch (RuntimeException | Error rejected) {
            runtime.revokeGeneratedCushionActionEvidence(claim);
            throw rejected;
        }
    }

    /** Mutates locked exact rows and joins terminal ground settlement in this same transaction. */
    @Transactional
    public void mutateCushionAction(WorldRuntime runtime, CushionActionCapability capability) {
        Objects.requireNonNull(runtime, "world runtime");
        Objects.requireNonNull(capability, "generated Cushion action capability");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("generated entity mutation requires a transaction");
        }
        IssuedCushionAction mutation;
        synchronized (this) {
            mutation = lookup(runtime, capability);
            if (mutation == null) {
                throw new IllegalStateException(
                        "generated Cushion action capability is foreign, forged, or replayed");
            }
            if (mutation.inFlight) {
                throw new IllegalStateException("generated Cushion action capability is in flight");
            }
            if (!mutation.current.equals(liveRuntimeCushion(runtime,
                    capability.authoritativeEntityId()))
                    || !runtime.ownsGeneratedCushionActionEvidence(mutation.claim)
                    || mutation.publication != null
                    && !runtime.ownsGeneratedCushionPersistencePublication(mutation.publication)) {
                consume(runtime, mutation);
                revoke(runtime, mutation);
                throw new IllegalStateException("generated Cushion action capability is stale");
            }
            mutation.inFlight = true;
        }
        registerCompletion(runtime, mutation);

        RuntimeBinding binding = mutation.current.binding();
        WorldStructureEntity source = sources.findByWorldIdAndAuthoritativeEntityId(
                binding.worldId(), binding.entityId()).orElseThrow(() ->
                        new IllegalStateException("generated entity source row is missing"));
        WorldGeneratedStructureEntityState state = states
                .findLockedByWorldIdAndAuthoritativeEntityId(
                        binding.worldId(), binding.entityId()).orElseThrow(() ->
                        new IllegalStateException("generated entity durable row is missing"));
        state.requireSameBinding(binding.worldId(), binding.chunkX(), binding.chunkZ(),
                binding.installationIdentity(), source, mutation.aggregate);
        CushionRuntimeSnapshot locked = requireLiveCushion(state.runtimeSnapshot());
        if (!mutation.current.equals(locked)) {
            throw new IllegalStateException("generated Cushion durable state is stale");
        }

        GeneratedCushionActionPolicy.Decision decision = GeneratedCushionActionPolicy.plan(
                new Authority(source, mutation.aggregate, locked, binding.entityId(),
                        capability.expectedRevision()), mutation.request);
        if (!decision.accepted()) {
            throw new IllegalStateException(
                    "generated Cushion action rejected: " + decision.rejections());
        }
        Plan plan = decision.plan();
        requireExactPlan(capability, mutation, plan);

        if (plan.durable().mutation() == DurableMutation.UPDATE_CUSHION) {
            state.applyCushion(mutation.authority, plan.durable().rider(),
                    plan.durable().customName());
            CushionRuntimeSnapshot next = requireLiveCushion(state.runtimeSnapshot());
            if (mutation.publication == null || !mutation.publication.next().equals(next)) {
                throw new IllegalStateException(
                        "generated Cushion durable update differs from owner publication");
            }
            WorldGeneratedStructureEntityState flushed = states.saveAndFlush(state);
            if (!next.equals(flushed.runtimeSnapshot())) {
                throw new IllegalStateException("flushed generated Cushion update differs");
            }
            mutation.preparedCommit = CommittedCushionMutation.update(plan, next);
            return;
        }

        TerminalSettlement terminal = plan.durable().terminalSettlement();
        GroundMutationCommand command = mutation.terminalGroundCommand;
        GroundItemSnapshot item = requireExactTerminalGroundCommand(runtime, plan, terminal, command);
        GroundMutationOutcome outcome = groundSettlements.settle(command);
        if (outcome != GroundMutationOutcome.COMMITTED) {
            throw new IllegalStateException(
                    "generated Cushion terminal ground settlement was not newly committed: "
                            + outcome);
        }
        SettledCushionTerminal authority = authenticateCommittedGround(
                plan, command, item, source);
        state.applySettledCushionTerminal(authority, source, mutation.aggregate);
        WorldGeneratedStructureEntityState flushed = states.saveAndFlush(state);
        authenticateCommittedGround(plan, command, item, source);
        if (!flushed.runtimeSnapshot().equals(state.runtimeSnapshot())
                || flushed.getLifecycle() != WorldGeneratedStructureEntityState.Lifecycle.DEAD
                || source.bindingStatus() != WorldStructureEntity.BindingStatus.DEAD) {
            throw new IllegalStateException("generated Cushion terminal rows did not flush atomically");
        }
        mutation.preparedCommit = CommittedCushionMutation.terminal(plan,
                state.runtimeSnapshot(), command, item, terminal.deliveryKey());
    }

    /** Package-owned drain; no committed result exists before afterCommit. */
    synchronized CommittedCushionMutation pollCommittedCushionMutation(WorldRuntime runtime) {
        ArrayDeque<CommittedCushionMutation> queue = committed.get(runtime);
        if (queue == null) return null;
        CommittedCushionMutation result = queue.pollFirst();
        if (queue.isEmpty()) committed.remove(runtime);
        return result;
    }

    private void registerCompletion(WorldRuntime runtime, IssuedCushionAction mutation) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                CommittedCushionMutation result = mutation.preparedCommit;
                if (result == null) {
                    throw new IllegalStateException(
                            "generated Cushion commit completed without durable result");
                }
                synchronized (GeneratedStructureEntityMutationCoordinator.this) {
                    committed.computeIfAbsent(runtime, ignored -> new ArrayDeque<>()).addLast(result);
                }
                if (mutation.publication != null) {
                    runtime.enqueueCommittedGeneratedCushionPersistencePublication(
                            mutation.publication);
                }
            }

            @Override public void afterCompletion(int status) {
                synchronized (GeneratedStructureEntityMutationCoordinator.this) {
                    consume(runtime, mutation);
                }
                revoke(runtime, mutation);
            }
        });
    }

    private SettledCushionTerminal authenticateCommittedGround(Plan plan,
            GroundMutationCommand command, GroundItemSnapshot item, WorldStructureEntity source) {
        String fingerprint = command.fingerprint();
        var receipt = groundReceipts.findByWorldIdAndMutationId(
                command.worldId(), command.mutationId()).orElseThrow(() ->
                        new IllegalStateException("committed ground receipt is missing"));
        if (receipt.mutationId() != command.mutationId() || !receipt.matches(fingerprint)) {
            throw new IllegalStateException("committed ground receipt identity differs");
        }
        WorldGroundItem persisted = groundItems.findByWorldIdAndEntityId(
                command.worldId(), item.entityId()).orElseThrow(() ->
                        new IllegalStateException("committed generated Cushion drop is missing"));
        if (!persisted.matches(item)) {
            throw new IllegalStateException("committed generated Cushion drop payload differs");
        }
        return new SettledCushionTerminal(plan.durable().source(), source,
                plan.durable().cause(), plan.durable().expectedRevision(),
                plan.durable().nextRevision(), plan.durable().terminalSettlement().deliveryKey(),
                command.worldId(), command.mutationId(), item.entityId(), fingerprint);
    }

    private static GroundItemSnapshot requireExactTerminalGroundCommand(WorldRuntime runtime,
            Plan plan, TerminalSettlement terminal, GroundMutationCommand command) {
        Objects.requireNonNull(command, "generated Cushion terminal ground command");
        long mutationId = WorldRuntime.stableGroundMutationId(
                plan.durable().source().authoritativeEntityId(), TERMINAL_GROUND_NAMESPACE);
        if (command.kind() != GroundMutationCommand.Kind.BLOCK_DROP
                || !Objects.equals(command.worldId(), plan.durable().source().worldId())
                || !Objects.equals(command.worldId(), runtime.worldId())
                || command.mutationId() != mutationId
                || command.expectedGroundRevision() != runtime.groundRevision()
                || command.committedGroundRevision()
                        != Math.addExact(command.expectedGroundRevision(), 1L)
                || command.expectedPlayerRevision() != null || command.committedPlayer() != null
                || !command.removedItemIds().isEmpty()
                || !command.insertedXpOrbs().isEmpty() || !command.removedXpOrbIds().isEmpty()
                || command.insertedItems().size() != 1) {
            throw new IllegalStateException(
                    "generated Cushion terminal ground command differs from policy");
        }
        GroundItemSnapshot item = command.insertedItems().getFirst();
        GroundItemSnapshot expected = runtime.itemSystem().settlementDropSnapshot(item.entityId(),
                (short) terminal.registeredItemType(), terminal.x(), terminal.y(), terminal.z());
        if (terminal.count() != 1 || !expected.equals(item)) {
            throw new IllegalStateException(
                    "generated Cushion terminal ground item differs from policy");
        }
        return item;
    }

    static GroundMutationCommand terminalGroundCommand(WorldRuntime runtime,
            long authoritativeEntityId, long expectedGroundRevision,
            TerminalSettlement terminal) {
        Objects.requireNonNull(runtime, "world runtime");
        Objects.requireNonNull(terminal, "generated Cushion terminal settlement");
        long entityId = runtime.itemSystem().reserveSettlementEntityId();
        if (terminal.count() != 1) {
            throw new IllegalStateException("generated Cushion terminal count differs");
        }
        GroundItemSnapshot item = runtime.itemSystem().settlementDropSnapshot(entityId,
                (short) terminal.registeredItemType(), terminal.x(), terminal.y(), terminal.z());
        return new GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(
                        authoritativeEntityId, TERMINAL_GROUND_NAMESPACE),
                GroundMutationCommand.Kind.BLOCK_DROP, runtime.worldId(), expectedGroundRevision,
                Math.addExact(expectedGroundRevision, 1L), null, null, List.of(item), List.of(),
                List.of(), List.of());
    }

    private static void requireExactPlan(CushionActionCapability capability,
            IssuedCushionAction mutation, Plan plan) {
        if (plan.durable().cause() != capability.action()
                || plan.durable().expectedRevision() != capability.expectedRevision()
                || plan.durable().nextRevision() != capability.nextRevision()
                || plan.terminal() != (mutation.terminalGroundCommand != null)
                || plan.terminal() != (mutation.publication == null)) {
            throw new IllegalStateException("generated Cushion recomputed policy plan drifted");
        }
    }

    private static CushionRuntimeSnapshot liveRuntimeCushion(WorldRuntime runtime, long entityId) {
        return requireLiveCushion(runtime.generatedStructureEntities().snapshotByEntityId(entityId));
    }

    private static CushionRuntimeSnapshot requireLiveCushion(
            WorldGeneratedStructureEntityState.RuntimeSnapshot raw) {
        if (!(raw instanceof CushionRuntimeSnapshot current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("live generated Cushion authority is required");
        }
        return current;
    }

    private IssuedCushionAction lookup(WorldRuntime runtime, CushionActionCapability capability) {
        Map<Long, IssuedCushionAction> byEntity = issued.get(runtime);
        if (byEntity == null) return null;
        IssuedCushionAction mutation = byEntity.get(capability.authoritativeEntityId());
        return mutation != null && mutation.capability == capability ? mutation : null;
    }

    private void consume(WorldRuntime runtime, IssuedCushionAction mutation) {
        Map<Long, IssuedCushionAction> byEntity = issued.get(runtime);
        if (byEntity != null
                && byEntity.get(mutation.capability.authoritativeEntityId()) == mutation) {
            byEntity.remove(mutation.capability.authoritativeEntityId());
            if (byEntity.isEmpty()) issued.remove(runtime);
        }
    }

    private static void revoke(WorldRuntime runtime, IssuedCushionAction mutation) {
        runtime.revokeGeneratedCushionActionEvidence(mutation.claim);
        if (mutation.publication != null) {
            runtime.revokeGeneratedCushionPersistencePublication(mutation.publication);
        }
    }

    private static void validateGenericNextState(RuntimeSnapshot current,
            RuntimeSnapshot next, GeneratedEntityMutationOperation operation) {
        Objects.requireNonNull(current, "current generated entity state");
        Objects.requireNonNull(next, "intended generated entity next state");
        Objects.requireNonNull(operation, "generated entity mutation operation");
        if (!current.binding().equals(next.binding())
                || current.revision() == Long.MAX_VALUE
                || next.revision() != Math.addExact(current.revision(), 1L)) {
            throw new IllegalStateException(
                    "generated entity mutation next state has the wrong current binding or revision");
        }
        switch (operation) {
            case ARMOR_STAND_MUTATION -> {
                if (!(current instanceof ArmorStandRuntimeSnapshot currentArmor)
                        || !(next instanceof ArmorStandRuntimeSnapshot nextArmor)
                        || next.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                        || !sameArmorImmutableState(currentArmor, nextArmor)) {
                    throw new IllegalStateException(
                            "armor stand mutation next state is not an exact live transition");
                }
            }
            case ARMOR_STAND_EQUIPMENT_SETTLEMENT -> {
                if (!(current instanceof ArmorStandRuntimeSnapshot currentArmor)
                        || !(next instanceof ArmorStandRuntimeSnapshot nextArmor)
                        || next.lifecycle()
                                != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                        || !sameArmorExceptEquipment(currentArmor, nextArmor)
                        || Objects.equals(currentArmor.equipmentItem(),
                                nextArmor.equipmentItem())) {
                    throw new IllegalStateException(
                            "armor stand equipment next state is not an exact live transition");
                }
            }
            case CHEST_MINECART_MOTION -> {
                if (!(current instanceof MinecartRuntimeSnapshot currentMinecart)
                        || !(next instanceof MinecartRuntimeSnapshot nextMinecart)
                        || next.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                        || !sameMinecartReceiptAndCargo(currentMinecart, nextMinecart)) {
                    throw new IllegalStateException(
                            "chest minecart motion next state is not an exact live transition");
                }
            }
            case CHEST_MINECART_CARGO -> {
                if (!(current instanceof MinecartRuntimeSnapshot currentMinecart)
                        || !(next instanceof MinecartRuntimeSnapshot nextMinecart)) {
                    throw new IllegalStateException(
                            "chest minecart cargo next state is not an exact resolved transition");
                }
                if (currentMinecart.lootStatus()
                        != WorldGeneratedStructureEntityState.LootStatus.RESOLVED) {
                    throw new IllegalStateException("chest minecart loot is not resolved");
                }
                if (nextMinecart.lifecycle()
                        != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                        || !sameMinecartReceiptAndTransform(currentMinecart, nextMinecart)) {
                    throw new IllegalStateException(
                            "chest minecart cargo next state is not an exact resolved transition");
                }
            }
            case TERMINAL -> {
                if (!(current instanceof ArmorStandRuntimeSnapshot currentArmor)
                        || !(next instanceof ArmorStandRuntimeSnapshot nextArmor)
                        || next.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.DEAD
                        || !sameArmorImmutableState(currentArmor, nextArmor)
                        || !currentArmor.transform().equals(nextArmor.transform())
                        || Float.floatToRawIntBits(nextArmor.health())
                                != Float.floatToRawIntBits(0.0f)) {
                    throw new IllegalStateException(
                            "generic terminal next state is not an exact armor stand tombstone");
                }
            }
        }
    }

    private static boolean sameArmorImmutableState(ArmorStandRuntimeSnapshot left,
            ArmorStandRuntimeSnapshot right) {
        return Arrays.equals(left.poseHead(), right.poseHead())
                && Arrays.equals(left.poseBody(), right.poseBody())
                && Objects.equals(left.equipmentSlot(), right.equipmentSlot())
                && Objects.equals(left.equipmentItem(), right.equipmentItem())
                && left.showArms() == right.showArms() && left.small() == right.small()
                && left.noBasePlate() == right.noBasePlate()
                && left.invisible() == right.invisible()
                && left.invulnerable() == right.invulnerable()
                && left.disabledSlots() == right.disabledSlots();
    }

    private static boolean sameArmorExceptEquipment(ArmorStandRuntimeSnapshot left,
            ArmorStandRuntimeSnapshot right) {
        return Arrays.equals(left.poseHead(), right.poseHead())
                && Arrays.equals(left.poseBody(), right.poseBody())
                && Objects.equals(left.equipmentSlot(), right.equipmentSlot())
                && left.showArms() == right.showArms() && left.small() == right.small()
                && left.noBasePlate() == right.noBasePlate()
                && left.invisible() == right.invisible()
                && left.invulnerable() == right.invulnerable()
                && left.disabledSlots() == right.disabledSlots()
                && left.transform().equals(right.transform())
                && Float.floatToRawIntBits(left.health())
                        == Float.floatToRawIntBits(right.health());
    }

    private static boolean sameMinecartReceipt(MinecartRuntimeSnapshot left,
            MinecartRuntimeSnapshot right) {
        return Objects.equals(left.lootTable(), right.lootTable())
                && left.lootSeed() == right.lootSeed()
                && Objects.equals(left.provenance(), right.provenance())
                && left.lootStatus() == right.lootStatus()
                && Objects.equals(left.lootDefinitionFingerprint(),
                        right.lootDefinitionFingerprint())
                && Objects.equals(left.lootResultFingerprint(), right.lootResultFingerprint())
                && Arrays.equals(left.lootResolution(), right.lootResolution());
    }

    private static boolean sameMinecartReceiptAndCargo(MinecartRuntimeSnapshot left,
            MinecartRuntimeSnapshot right) {
        return sameMinecartReceipt(left, right) && sameCargo(left.cargo(), right.cargo());
    }

    private static boolean sameMinecartReceiptAndTransform(MinecartRuntimeSnapshot left,
            MinecartRuntimeSnapshot right) {
        return sameMinecartReceipt(left, right)
                && left.transform().equals(right.transform());
    }

    private static boolean sameCargo(List<com.gameexpert.chest.entity.ChestItem> left,
            List<com.gameexpert.chest.entity.ChestItem> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            var a = left.get(index);
            var b = right.get(index);
            if (a.getSlot() != b.getSlot() || a.getItemType() != b.getItemType()
                    || a.getItemCount() != b.getItemCount()
                    || !Objects.equals(a.getDurability(), b.getDurability())
                    || !Objects.equals(a.getEnchantments(), b.getEnchantments())
                    || !Objects.equals(a.getMapId(), b.getMapId())
                    || !Objects.equals(a.getShulkerId(), b.getShulkerId())
                    || !Objects.equals(a.getBucketMobData(), b.getBucketMobData())
                    || !Objects.equals(a.getItemComponentData(), b.getItemComponentData())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSnapshot(RuntimeSnapshot left, RuntimeSnapshot right) {
        if (left == null || right == null || !left.binding().equals(right.binding())
                || left.revision() != right.revision() || left.lifecycle() != right.lifecycle()
                || !left.transform().equals(right.transform())
                || left.getClass() != right.getClass()) {
            return false;
        }
        if (left instanceof ArmorStandRuntimeSnapshot a
                && right instanceof ArmorStandRuntimeSnapshot b) {
            return sameArmorImmutableState(a, b)
                    && Float.floatToRawIntBits(a.health()) == Float.floatToRawIntBits(b.health());
        }
        if (left instanceof CushionRuntimeSnapshot a
                && right instanceof CushionRuntimeSnapshot b) {
            return Objects.equals(a.color(), b.color()) && a.blockX() == b.blockX()
                    && a.blockY() == b.blockY() && a.blockZ() == b.blockZ()
                    && a.invulnerable() == b.invulnerable()
                    && Objects.equals(a.rider(), b.rider())
                    && Objects.equals(a.customName(), b.customName());
        }
        if (left instanceof MinecartRuntimeSnapshot a
                && right instanceof MinecartRuntimeSnapshot b) {
            return sameMinecartReceiptAndCargo(a, b);
        }
        return false;
    }

    private static RuntimeSnapshot copySnapshot(RuntimeSnapshot snapshot) {
        if (snapshot instanceof ArmorStandRuntimeSnapshot armor) {
            return new ArmorStandRuntimeSnapshot(armor.binding(), armor.revision(),
                    armor.lifecycle(), armor.transform(), armor.poseHead(), armor.poseBody(),
                    armor.equipmentSlot(), armor.equipmentItem(), armor.showArms(), armor.small(),
                    armor.noBasePlate(), armor.invisible(), armor.invulnerable(),
                    armor.disabledSlots(), armor.health());
        }
        if (snapshot instanceof CushionRuntimeSnapshot cushion) {
            return new CushionRuntimeSnapshot(cushion.binding(), cushion.revision(),
                    cushion.lifecycle(), cushion.transform(), cushion.color(), cushion.blockX(),
                    cushion.blockY(), cushion.blockZ(), cushion.invulnerable(), cushion.rider(),
                    cushion.customName());
        }
        if (snapshot instanceof MinecartRuntimeSnapshot minecart) {
            return new MinecartRuntimeSnapshot(minecart.binding(), minecart.revision(),
                    minecart.lifecycle(), minecart.transform(), minecart.lootTable(),
                    minecart.lootSeed(), minecart.provenance(), minecart.lootStatus(),
                    minecart.lootDefinitionFingerprint(), minecart.lootResultFingerprint(),
                    minecart.lootResolution(), minecart.cargo());
        }
        throw new IllegalStateException("unknown generated entity runtime snapshot");
    }

    private static void requireAggregateBinding(StructureEntityAggregate aggregate,
            RuntimeBinding binding) {
        String label = binding.kind() == Kind.CUSHION ? "generated Cushion" : "generated entity";
        if (!aggregate.installed()
                || !aggregate.installationIdentity().equals(binding.installationIdentity())
                || !aggregate.sourceFingerprint().equals(binding.installationSourceFingerprint())
                || binding.encounterOrdinal() < 0
                || binding.encounterOrdinal() >= aggregate.plannedEntities().size()) {
            throw new IllegalStateException(label + " aggregate binding is invalid");
        }
        StructureEntityAggregate.PlannedEntity planned = aggregate.plannedEntities()
                .get(binding.encounterOrdinal());
        if (planned.authoritativeEntityId() != binding.entityId()
                || !planned.rowFingerprint().equals(binding.sourceRowFingerprint())) {
            throw new IllegalStateException(label + " aggregate row proof differs");
        }
    }

    private static final class GeneratedEntityMutationBinding {
        private final WorldGeneratedStructureEntityState state;
        private final RuntimeSnapshot currentState;
        private final RuntimeSnapshot nextState;
        private final String aggregateInstallationIdentity;
        private final String aggregateSourceFingerprint;
        private final GeneratedEntityMutationOperation operation;

        private GeneratedEntityMutationBinding(WorldGeneratedStructureEntityState state,
                RuntimeSnapshot currentState, RuntimeSnapshot nextState,
                String aggregateInstallationIdentity, String aggregateSourceFingerprint,
                GeneratedEntityMutationOperation operation) {
            this.state = state;
            this.currentState = currentState;
            this.nextState = nextState;
            this.aggregateInstallationIdentity = aggregateInstallationIdentity;
            this.aggregateSourceFingerprint = aggregateSourceFingerprint;
            this.operation = operation;
        }
    }

    /** Opaque identity capability; construction access is unavailable to callers. */
    public static final class CushionActionCapability {
        private final GeneratedStructureEntityMutationCoordinator owner;
        private final long worldId, authoritativeEntityId, expectedRevision, nextRevision;
        private final Action action;
        private CushionActionCapability(GeneratedStructureEntityMutationCoordinator owner,
                long worldId, long authoritativeEntityId,
                long expectedRevision, long nextRevision, Action action) {
            this.owner = owner;
            this.worldId = worldId;
            this.authoritativeEntityId = authoritativeEntityId;
            this.expectedRevision = expectedRevision;
            this.nextRevision = nextRevision;
            this.action = action;
        }
        public long worldId() { return worldId; }
        public long authoritativeEntityId() { return authoritativeEntityId; }
        public long expectedRevision() { return expectedRevision; }
        public long nextRevision() { return nextRevision; }
        public Action action() { return action; }
    }

    /** Opaque state-write proof; only the coordinator can construct or expose it. */
    public static final class CushionMutationAuthority {
        private final GeneratedStructureEntityMutationCoordinator owner;
        private final WeakReference<WorldRuntime> runtime;
        private final CushionActionCapability capability;

        private CushionMutationAuthority(GeneratedStructureEntityMutationCoordinator owner,
                WorldRuntime runtime, CushionActionCapability capability) {
            this.owner = owner;
            this.runtime = new WeakReference<>(runtime);
            this.capability = capability;
        }
    }

    /** Opaque identity capability; construction and reuse are coordinator-controlled. */
    public static final class GeneratedEntityMutationCapability {
        private final GeneratedStructureEntityMutationCoordinator owner;
        private final WorldGeneratedStructureEntityState state;
        private final RuntimeSnapshot currentState;
        private final RuntimeSnapshot nextState;
        private final String aggregateInstallationIdentity;
        private final String aggregateSourceFingerprint;
        private final GeneratedEntityMutationOperation operation;

        private GeneratedEntityMutationCapability(
                GeneratedStructureEntityMutationCoordinator owner,
                WorldGeneratedStructureEntityState state, RuntimeSnapshot currentState,
                RuntimeSnapshot nextState, String aggregateInstallationIdentity,
                String aggregateSourceFingerprint, GeneratedEntityMutationOperation operation) {
            this.owner = owner;
            this.state = state;
            this.currentState = currentState;
            this.nextState = nextState;
            this.aggregateInstallationIdentity = aggregateInstallationIdentity;
            this.aggregateSourceFingerprint = aggregateSourceFingerprint;
            this.operation = operation;
        }
    }

    /** Operation is intentionally narrower than a generic values-only mutation API. */
    public enum GeneratedEntityMutationOperation {
        ARMOR_STAND_MUTATION {
            @Override boolean allowedFor(Kind kind) { return kind == Kind.ARMOR_STAND; }
        },
        ARMOR_STAND_EQUIPMENT_SETTLEMENT {
            @Override boolean allowedFor(Kind kind) { return kind == Kind.ARMOR_STAND; }
        },
        CHEST_MINECART_MOTION {
            @Override boolean allowedFor(Kind kind) { return kind == Kind.CHEST_MINECART; }
        },
        CHEST_MINECART_CARGO {
            @Override boolean allowedFor(Kind kind) { return kind == Kind.CHEST_MINECART; }
        },
        TERMINAL {
            @Override boolean allowedFor(Kind kind) { return kind == Kind.ARMOR_STAND; }
        };

        abstract boolean allowedFor(Kind kind);
    }

    /** Commit-bound proof accepted only by the generated-state aggregate. */
    public static final class SettledCushionTerminal {
        private final GeneratedCushionActionPolicy.SourceProof sourceProof;
        private final WorldStructureEntity sourceIdentity;
        private final Action cause;
        private final long expectedRevision, nextRevision, worldId, mutationId, groundEntityId;
        private final String deliveryKey, commandFingerprint;
        private SettledCushionTerminal(GeneratedCushionActionPolicy.SourceProof sourceProof,
                WorldStructureEntity sourceIdentity, Action cause, long expectedRevision,
                long nextRevision, String deliveryKey, long worldId, long mutationId,
                long groundEntityId, String commandFingerprint) {
            this.sourceProof = sourceProof;
            this.sourceIdentity = sourceIdentity;
            this.cause = cause;
            this.expectedRevision = expectedRevision;
            this.nextRevision = nextRevision;
            this.deliveryKey = deliveryKey;
            this.worldId = worldId;
            this.mutationId = mutationId;
            this.groundEntityId = groundEntityId;
            this.commandFingerprint = commandFingerprint;
        }
        public GeneratedCushionActionPolicy.SourceProof sourceProof() { return sourceProof; }
        public WorldStructureEntity sourceIdentity() { return sourceIdentity; }
        public Action cause() { return cause; }
        public long expectedRevision() { return expectedRevision; }
        public long nextRevision() { return nextRevision; }
        public String deliveryKey() { return deliveryKey; }
        public long worldId() { return worldId; }
        public long mutationId() { return mutationId; }
        public long groundEntityId() { return groundEntityId; }
        public String commandFingerprint() { return commandFingerprint; }
    }

    /** Immutable committed event; its private factories run only after durable checks. */
    public static final class CommittedCushionMutation {
        private final Action cause;
        private final WorldGeneratedStructureEntityState.RuntimeSnapshot durable;
        private final long groundMutationId, groundEntityId, committedGroundRevision;
        private final String groundCommandFingerprint, terminalDeliveryKey;
        private CommittedCushionMutation(Action cause,
                WorldGeneratedStructureEntityState.RuntimeSnapshot durable,
                long groundMutationId, long groundEntityId, long committedGroundRevision,
                String groundCommandFingerprint, String terminalDeliveryKey) {
            this.cause = cause;
            this.durable = durable;
            this.groundMutationId = groundMutationId;
            this.groundEntityId = groundEntityId;
            this.committedGroundRevision = committedGroundRevision;
            this.groundCommandFingerprint = groundCommandFingerprint;
            this.terminalDeliveryKey = terminalDeliveryKey;
        }
        private static CommittedCushionMutation update(Plan plan, CushionRuntimeSnapshot next) {
            return new CommittedCushionMutation(plan.durable().cause(), next,
                    0L, 0L, 0L, null, null);
        }
        private static CommittedCushionMutation terminal(Plan plan,
                WorldGeneratedStructureEntityState.RuntimeSnapshot durable,
                GroundMutationCommand command, GroundItemSnapshot item, String deliveryKey) {
            return new CommittedCushionMutation(plan.durable().cause(), durable,
                    command.mutationId(), item.entityId(), command.committedGroundRevision(),
                    command.fingerprint(), deliveryKey);
        }
        public Action cause() { return cause; }
        public WorldGeneratedStructureEntityState.RuntimeSnapshot durable() { return durable; }
        public boolean terminal() { return terminalDeliveryKey != null; }
        public long groundMutationId() { return groundMutationId; }
        public long groundEntityId() { return groundEntityId; }
        public long committedGroundRevision() { return committedGroundRevision; }
        public String groundCommandFingerprint() { return groundCommandFingerprint; }
        public String terminalDeliveryKey() { return terminalDeliveryKey; }
    }

    private static final class IssuedCushionAction {
        private final CushionActionCapability capability;
        private final CushionMutationAuthority authority;
        private final StructureEntityAggregate aggregate;
        private final CushionRuntimeSnapshot current;
        private final Request request;
        private final GeneratedCushionActionClaim claim;
        private final GroundMutationCommand terminalGroundCommand;
        private final GeneratedCushionPersistencePublication publication;
        private boolean inFlight;
        private CommittedCushionMutation preparedCommit;
        private IssuedCushionAction(CushionActionCapability capability,
                CushionMutationAuthority authority,
                StructureEntityAggregate aggregate, CushionRuntimeSnapshot current,
                Request request, GeneratedCushionActionClaim claim,
                GroundMutationCommand terminalGroundCommand,
                GeneratedCushionPersistencePublication publication) {
            this.capability = capability;
            this.authority = authority;
            this.aggregate = aggregate;
            this.current = current;
            this.request = request;
            this.claim = claim;
            this.terminalGroundCommand = terminalGroundCommand;
            this.publication = publication;
        }
    }
}
