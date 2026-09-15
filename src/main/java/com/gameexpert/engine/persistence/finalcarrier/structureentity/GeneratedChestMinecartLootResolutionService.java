package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootMapMaterializationService;
import com.gameexpert.engine.persistence.finalcarrier.loot.GeneratedEntityLootAssignmentFactory;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierLaneMutation;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierLaneMutationRepository;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignment;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignmentRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.LootStatus;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot;
import java.util.Arrays;
import java.util.Objects;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic first-open bridge from one authenticated canonical LOOT authority to one ENTS minecart. */
@Service
public class GeneratedChestMinecartLootResolutionService {
    public enum Outcome { RESOLVED, IDEMPOTENT, STALE }

    /** Exact locked durable result; callers never reconstruct post-commit state from an outcome. */
    public record ResolutionResult(Outcome outcome, MinecartRuntimeSnapshot current,
            List<WorldMapData> materializedMaps) {
        public ResolutionResult(Outcome outcome, MinecartRuntimeSnapshot current) {
            this(outcome, current, List.of());
        }

        public ResolutionResult {
            Objects.requireNonNull(outcome, "minecart loot resolution outcome");
            Objects.requireNonNull(current, "minecart loot resolution current state");
            materializedMaps = List.copyOf(materializedMaps);
        }
    }

    private final WorldGeneratedStructureEntityStateRepository states;
    private final WorldStructureEntityRepository sources;
    private final WorldCanonicalLootAssignmentRepository lootAssignments;
    private final CanonicalLootMapMaterializationService mapMaterialization;
    private final FinalCarrierLaneMutationRepository lanes;
    private final WorldStore worlds;
    private final CanonicalWorldgenStore canonical;

    public GeneratedChestMinecartLootResolutionService(
            WorldGeneratedStructureEntityStateRepository states,
            WorldStructureEntityRepository sources,
            WorldCanonicalLootAssignmentRepository lootAssignments,
            CanonicalLootMapMaterializationService mapMaterialization,
            FinalCarrierLaneMutationRepository lanes, WorldStore worlds, CanonicalWorldgenStore canonical) {
        this.states = Objects.requireNonNull(states, "generated entity state repository");
        this.sources = Objects.requireNonNull(sources, "generated entity source repository");
        this.lootAssignments = Objects.requireNonNull(lootAssignments,
                "canonical LOOT assignment repository");
        this.mapMaterialization = Objects.requireNonNull(mapMaterialization,
                "canonical LOOT map materialization");
        this.lanes = Objects.requireNonNull(lanes, "installed ENTS lane repository");
        this.worlds = Objects.requireNonNull(worlds, "generated loot world repository");
        this.canonical = Objects.requireNonNull(canonical, "canonical producer store");
    }

    /**
     * Locks the nested LOOT/state aggregates, binds the exact ENTS source revision, then commits
     * both terminal receipts in one transaction. The caller cannot supply cargo values.
     */
    @Transactional
    public ResolutionResult resolveFirstOpen(long worldId, long entityId,
            long expectedSourceRevision,
            StructureEntityAggregate aggregate) {
        if (worldId <= 0L || entityId <= 0L || entityId == Long.MAX_VALUE
                || expectedSourceRevision < 0L || expectedSourceRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("invalid generated minecart first-open identity");
        }
        Objects.requireNonNull(aggregate, "generated minecart source aggregate");
        WorldGeneratedStructureEntityState state = states
                .findLockedByWorldIdAndAuthoritativeEntityId(worldId, entityId)
                .orElse(null);
        if (state == null) {
            throw new IllegalStateException("generated minecart durable state is unavailable");
        }
        MinecartRuntimeSnapshot current = requireMinecart(state.runtimeSnapshot());
        WorldStructureEntity source = sources.findByWorldIdAndAuthoritativeEntityId(
                worldId, entityId).orElseThrow(() -> new IllegalStateException(
                        "generated minecart source row is unavailable"));
        state.requireSameBinding(worldId, current.binding().chunkX(), current.binding().chunkZ(),
                current.binding().installationIdentity(), source, aggregate);
        if (current.lootStatus() == LootStatus.UNOPENED
                && current.revision() != expectedSourceRevision) {
            return new ResolutionResult(Outcome.STALE, current);
        }
        int x = floor(current.transform().x());
        int y = floor(current.transform().y());
        int z = floor(current.transform().z());
        WorldCanonicalLootAssignment lootAuthority = lootAssignments
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, x, y, z)
                .orElse(null);
        if (lootAuthority == null) {
            if (current.lootStatus() != LootStatus.UNOPENED) {
                throw new IllegalStateException("resolved generated minecart nested LOOT authority is unavailable");
            }
            lootAuthority = materializeInstalledEntityAssignment(source, aggregate);
            requireNestedLootAuthority(current, lootAuthority);
            lootAssignments.save(lootAuthority);
        }
        requireNestedLootAuthority(current, lootAuthority);
        boolean assignmentAlreadyResolved = lootAuthority.getStatus()
                == WorldCanonicalLootAssignment.Status.RESOLVED;
        CanonicalLootStoredResolution candidate = lootAuthority.resolveCandidate(verifiedEntitySource(source));

        if (current.lootStatus() == LootStatus.RESOLVED) {
            CanonicalLootMapMaterializationService.Materialization maps =
                    replayMaterializedMaps(lootAuthority, candidate);
            requireExactReplay(current, lootAuthority, candidate);
            lootAuthority.commitResolution(candidate, maps.payload(), maps.receipt());
            return new ResolutionResult(Outcome.IDEMPOTENT,
                    requireMinecart(state.runtimeSnapshot()), maps.maps());
        }
        if (current.revision() != expectedSourceRevision) {
            return new ResolutionResult(Outcome.STALE, current);
        }
        if (assignmentAlreadyResolved) {
            throw new IllegalStateException(
                    "resolved nested LOOT has no matching minecart terminal state");
        }

        CanonicalLootMapMaterializationService.Materialization maps =
                materializeMaps(lootAuthority, candidate);
        candidate = maps.resolution();

        ResolutionAuthority authority = new ResolutionAuthority(state, source,
                aggregate.installationIdentity(), aggregate.sourceFingerprint(),
                expectedSourceRevision, lootAuthority.getDefinitionFingerprint(), candidate);
        state.applyResolvedMinecartLoot(authority);
        // Commit nested LOOT last; any exception rolls the state mutation back with this row.
        lootAuthority.commitResolution(candidate, maps.payload(), maps.receipt());
        states.save(state);
        return new ResolutionResult(Outcome.RESOLVED,
                requireMinecart(state.runtimeSnapshot()), maps.maps());
    }

    /**
     * ENTS owns its own authenticated LDEC; block LOOT admission cannot create this row. Read the
     * already-installed exact payload so retained unopened carts use their original source bytes.
     * Never invent a declaration, replace an occupied assignment, or recover a resolved orphan.
     */
    private WorldCanonicalLootAssignment materializeInstalledEntityAssignment(
            WorldStructureEntity source, StructureEntityAggregate aggregate) {
        String identity = source.getLaneInstallationIdentity();
        String prefix = source.getWorldId() + ":" + source.getChunkX() + ":"
                + source.getChunkZ() + ":ENTITIES:";
        if (!identity.startsWith(prefix)
                || !identity.substring(prefix.length()).matches("[0-9a-f]{64}:[0-9a-f]{64}")
                || !aggregate.installed()) {
            throw new IllegalStateException("generated minecart installed ENTS identity is invalid");
        }
        String sourceFingerprint = identity.substring(prefix.length(), prefix.length() + 64);
        FinalCarrierLaneMutation mutation = lanes
                .findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(source.getWorldId(),
                        source.getChunkX(), source.getChunkZ(), "ENTITIES", sourceFingerprint)
                .orElseThrow(() -> new IllegalStateException("generated minecart installed ENTS source is unavailable"));
        byte[] typed = mutation.getTypedPayload();
        if (mutation.getActivationStatus() != FinalCarrierLaneMutation.ActivationStatus.INSTALLED
                || mutation.getWorldId() != source.getWorldId()
                || mutation.getChunkX() != source.getChunkX() || mutation.getChunkZ() != source.getChunkZ()
                || !"ENTITIES".equals(mutation.getLane())
                || !identity.equals(mutation.getInstallationIdentity())
                || !sourceFingerprint.equals(mutation.getSourceFingerprint())
                || !mutation.matches(sha256(typed), typed)) {
            throw new IllegalStateException("generated minecart installed ENTS payload identity differs");
        }
        NeutralFinalChunk carrier = canonicalSource(source).verifyCarrier(typed);
        var exact = carrier.sidecars();
        if (carrier.chunkX() != source.getChunkX() || carrier.chunkZ() != source.getChunkZ()
                || !exact.blockTicks().isEmpty() || !exact.fluidTicks().isEmpty()
                || !exact.loot().isEmpty() || !exact.spawners().isEmpty() || !exact.owners().isEmpty()
                || !exact.archaeology().isEmpty() || !exact.bees().isEmpty() || !exact.blockEntities().isEmpty()) {
            throw new IllegalStateException("generated minecart installed payload is not exact ENTS");
        }
        var base = carrier.withSidecars(NeutralFinalChunk.Sidecars.EMPTY);
        if (!sourceFingerprint.equals(sha256(base.encodedCarrier()))) {
            throw new IllegalStateException("generated minecart installed terrain source differs");
        }
        var installation = new StructureEntityAggregate.Installation(identity, exact.entities(),
                aggregate.plannedEntities().stream().map(StructureEntityAggregate.PlannedEntity::authoritativeEntityId).toList());
        if (!installation.sourceFingerprint().equals(aggregate.sourceFingerprint())
                || !installation.expectedReceipts().equals(aggregate.expectedReceipts())) {
            throw new IllegalStateException("generated minecart installed ENTS aggregate differs");
        }
        int ordinal = source.getEncounterOrdinal();
        if (ordinal < 0 || ordinal >= exact.entities().size()
                || !source.matches(source.getWorldId(), source.getChunkX(), source.getChunkZ(), identity,
                        installation.sourceFingerprint(), installation.plannedEntities().get(ordinal))) {
            throw new IllegalStateException("generated minecart installed ENTS row differs");
        }
        var declarations = exact.containerLootDeclarations().stream().filter(value ->
                value.sourceSection() == NeutralFinalChunk.ContainerLootSourceSection.ENTS
                        && value.sourceSectionOrdinal() == ordinal).toList();
        if (declarations.size() != 1) {
            throw new IllegalStateException("generated minecart authenticated ENTS loot declaration is unavailable");
        }
        long worldSeed = worlds.findById(source.getWorldId()).orElseThrow(() ->
                new IllegalStateException("generated minecart world source is unavailable")).getSeed();
        return GeneratedEntityLootAssignmentFactory.create(source.getWorldId(), worldSeed,
                source.getChunkX(), source.getChunkZ(), identity, aggregate.sourceFingerprint(),
                ordinal, exact.entities().get(ordinal), declarations.getFirst(), carrier);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private CanonicalLootMapMaterializationService.Materialization materializeMaps(
            WorldCanonicalLootAssignment assignment, CanonicalLootStoredResolution candidate) {
        if (candidate.pendingMapReferences().isEmpty()) {
            return new CanonicalLootMapMaterializationService.Materialization(
                    candidate, List.of(), null, null);
        }
        if (mapMaterialization == null) {
            throw new IllegalStateException("canonical LOOT map materialization is unavailable");
        }
        return mapMaterialization.materializeJoiningTransaction(assignment, candidate);
    }

    private CanonicalLootMapMaterializationService.Materialization replayMaterializedMaps(
            WorldCanonicalLootAssignment assignment, CanonicalLootStoredResolution stored) {
        if (assignment.getMapMaterializationReceipt() == null
                && assignment.getMapMaterializationPayload() == null) {
            return new CanonicalLootMapMaterializationService.Materialization(
                    stored, List.of(), null, null);
        }
        if (mapMaterialization == null) {
            throw new IllegalStateException("canonical LOOT map replay authority is unavailable");
        }
        return mapMaterialization.replayJoiningTransaction(assignment, stored,
                assignment.getMapMaterializationPayload(),
                assignment.getMapMaterializationReceipt());
    }

    private static MinecartRuntimeSnapshot requireMinecart(
            WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot) {
        if (!(snapshot instanceof MinecartRuntimeSnapshot minecart)
                || minecart.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("live generated chest minecart is required");
        }
        return minecart;
    }

    private static void requireNestedLootAuthority(MinecartRuntimeSnapshot minecart,
            WorldCanonicalLootAssignment authority) {
        int x = floor(minecart.transform().x());
        int y = floor(minecart.transform().y());
        int z = floor(minecart.transform().z());
        if (authority.getWorldId() != minecart.binding().worldId()
                || authority.getContainerKind() != CanonicalLootContainerKind.CHEST
                || !authority.getTableKey().equals(minecart.lootTable())
                || authority.getRawSeed() != minecart.lootSeed()
                || authority.getPosX() != x || authority.getPosY() != y
                || authority.getPosZ() != z
                || !authority.getLaneInstallationIdentity()
                        .equals(minecart.binding().installationIdentity())
                || !authority.getInstallationFingerprint()
                        .equals(minecart.binding().installationSourceFingerprint())) {
            throw new IllegalStateException(
                    "generated minecart nested LOOT authority differs from ENTS source");
        }
    }

    private static void requireExactReplay(MinecartRuntimeSnapshot current,
            WorldCanonicalLootAssignment authority, CanonicalLootStoredResolution candidate) {
        String result = candidate.fingerprint(authority.getDefinitionFingerprint());
        if (!Objects.equals(current.lootDefinitionFingerprint(),
                    authority.getDefinitionFingerprint())
                || !Objects.equals(current.lootResultFingerprint(), result)
                || !Arrays.equals(current.lootResolution(), candidate.encode())) {
            throw new IllegalStateException(
                    "generated minecart LOOT resolution identity collision");
        }
    }

    private NeutralFinalChunk canonicalSource(WorldStructureEntity source) {
        var snapshot=canonical.find(source.getWorldId(),source.getChunkX(),source.getChunkZ());
        if(snapshot==null || snapshot.commit().semanticFinalChunk()==null) throw new IllegalStateException("generated entity producer source unavailable");
        return snapshot.commit().semanticFinalChunk();
    }
    private NeutralFinalChunk verifiedEntitySource(WorldStructureEntity source) {
        // The persisted ENTS lane has its own dense declaration ordinals; verify those exact bytes.
        String identity=source.getLaneInstallationIdentity();
        String prefix=source.getWorldId()+":"+source.getChunkX()+":"+source.getChunkZ()+":ENTITIES:";
        if(!identity.startsWith(prefix)||!identity.substring(prefix.length()).matches("[0-9a-f]{64}:[0-9a-f]{64}")) throw new IllegalStateException("invalid ENTS installation identity");
        String fingerprint=identity.substring(prefix.length(),prefix.length()+64);
        var mutation=lanes.findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(source.getWorldId(), source.getChunkX(), source.getChunkZ(), "ENTITIES",fingerprint).orElseThrow(()->new IllegalStateException("installed ENTS lane unavailable"));
        byte[] typed=mutation.getTypedPayload();
        if(mutation.getActivationStatus()!=FinalCarrierLaneMutation.ActivationStatus.INSTALLED || !identity.equals(mutation.getInstallationIdentity()) || !mutation.matches(sha256(typed),typed)) throw new IllegalStateException("installed ENTS lane binding mismatch");
        NeutralFinalChunk decoded = canonicalSource(source).verifyCarrier(typed);
        if (!fingerprint.equals(sha256(decoded.withSidecars(NeutralFinalChunk.Sidecars.EMPTY).encodedCarrier()))) {
            throw new IllegalStateException("installed ENTS source fingerprint mismatch");
        }
        return decoded;
    }

    private static int floor(double value) {
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalStateException("generated minecart coordinate is outside int bounds");
        }
        return (int) floor;
    }

    /** Opaque one-shot proof; only this transaction owner can construct it. */
    static final class ResolutionAuthority {
        final WorldGeneratedStructureEntityState state;
        final WorldStructureEntity source;
        final String installationIdentity;
        final String installationSourceFingerprint;
        final long expectedSourceRevision;
        final String definitionFingerprint;
        final CanonicalLootStoredResolution resolution;

        private ResolutionAuthority(WorldGeneratedStructureEntityState state,
                WorldStructureEntity source, String installationIdentity,
                String installationSourceFingerprint, long expectedSourceRevision,
                String definitionFingerprint, CanonicalLootStoredResolution resolution) {
            this.state = state;
            this.source = source;
            this.installationIdentity = installationIdentity;
            this.installationSourceFingerprint = installationSourceFingerprint;
            this.expectedSourceRevision = expectedSourceRevision;
            this.definitionFingerprint = definitionFingerprint;
            this.resolution = resolution;
        }
    }
}
