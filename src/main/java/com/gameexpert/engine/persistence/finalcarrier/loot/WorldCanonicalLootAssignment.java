package com.gameexpert.engine.persistence.finalcarrier.loot;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.security.MessageDigest;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Current-only durable lazy container-loot assignment. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_canonical_loot_assignments",
        options = "DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
        @UniqueConstraint(name = "uk_canonical_loot_installation_cell",
                columnNames = {"lane_installation_identity", "packed"}),
        @UniqueConstraint(name = "uk_canonical_loot_world_position",
                columnNames = {"world_id", "pos_x", "pos_y", "pos_z"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldCanonicalLootAssignment {
    public enum Status { UNOPENED, RESOLVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private long worldSeed;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false) private int packed;
    @Column(nullable = false) private int posX;
    @Column(nullable = false) private int posY;
    @Column(nullable = false) private int posZ;
    @Column(nullable = false, length = 16) private String facing;
    @Column(nullable = false, length = 255) private String tableKey;
    @Column(nullable = false) private long rawSeed;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private CanonicalLootContainerKind containerKind;
    @Column(nullable = false, length = 255) private String laneInstallationIdentity;
    @Column(nullable = false, length = 64) private String installationFingerprint;
    @Column(nullable = false, length = 64) private String definitionFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status;
    @Column(length = 255) private String namedSequenceKey;
    private Long initialSeedLo;
    private Long initialSeedHi;
    @Enumerated(EnumType.STRING)
    @Column(length = 32) private Mc263ContainerLootResolver.Continuation.Kind continuationKind;
    private Long continuationFirst;
    private Long continuationSecond;
    @Column(length = 64) private String resultFingerprint;
    @Column(length = 64) private String mapMaterializationReceipt;
    /** Exact descriptor-free LDEC production context and source receipts. */
    @Lob @Column(name = "production_context_payload", nullable = false,
            columnDefinition = "longblob")
    private byte[] productionContextPayload;
    @Lob @Column(columnDefinition = "longblob") private byte[] resolvedPayload;
    @Lob @Column(columnDefinition = "longblob") private byte[] mapMaterializationPayload;
    /** New-profile outcome and immutable replay input membership; original declaration is unchanged. */
    @Lob @Column(columnDefinition = "longblob") private byte[] lateOutcomePayload;
    @Column(length = 64) private String lateSnapshotIdentity;
    @jakarta.persistence.Transient
    private com.gameexpert.authority.versioned.LateLootOutcome verifiedLateOutcome;


    /** Creates a production assignment with its authenticated context payload. */
    public WorldCanonicalLootAssignment(long worldId, long worldSeed,
            int chunkX, int chunkZ, int packed,
            int posX, int posY, int posZ, String facing, String tableKey, long rawSeed,
            CanonicalLootContainerKind containerKind, String laneInstallationIdentity,
            String installationFingerprint, String definitionFingerprint,
            Long initialSeedLo, Long initialSeedHi, byte[] productionContextPayload) {
        this(worldId, worldSeed, chunkX, chunkZ, packed, posX, posY, posZ, facing, tableKey,
                rawSeed, containerKind, laneInstallationIdentity, installationFingerprint,
                definitionFingerprint, initialSeedLo, initialSeedHi, productionContextPayload,
                false);
    }

    /** Alternate argument order for persistence/import callers. */
    public WorldCanonicalLootAssignment(long worldId, long worldSeed,
            int chunkX, int chunkZ, int packed,
            int posX, int posY, int posZ, String facing, String tableKey, long rawSeed,
            CanonicalLootContainerKind containerKind, String laneInstallationIdentity,
            String installationFingerprint, String definitionFingerprint,
            byte[] productionContextPayload, Long initialSeedLo, Long initialSeedHi) {
        this(worldId, worldSeed, chunkX, chunkZ, packed, posX, posY, posZ, facing, tableKey,
                rawSeed, containerKind, laneInstallationIdentity, installationFingerprint,
                definitionFingerprint, initialSeedLo, initialSeedHi, productionContextPayload,
                false);
    }

    private WorldCanonicalLootAssignment(long worldId, long worldSeed,
            int chunkX, int chunkZ, int packed,
            int posX, int posY, int posZ, String facing, String tableKey, long rawSeed,
            CanonicalLootContainerKind containerKind, String laneInstallationIdentity,
            String installationFingerprint, String definitionFingerprint,
            Long initialSeedLo, Long initialSeedHi, byte[] productionContextPayload,
            boolean ignored) {
        if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
        if (packed < 0) throw new IllegalArgumentException("packed position required");
        this.worldId = worldId;
        this.worldSeed = worldSeed;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.packed = packed;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.facing = requireText(facing, "facing", 16);
        this.tableKey = requireText(tableKey, "loot table", 255);
        this.rawSeed = rawSeed;
        this.containerKind = Objects.requireNonNull(containerKind, "container kind");
        this.laneInstallationIdentity = requireText(
                laneInstallationIdentity, "lane installation identity", 255);
        this.installationFingerprint = requireFingerprint(
                installationFingerprint, "installation fingerprint");
        this.definitionFingerprint = requireFingerprint(
                definitionFingerprint, "definition fingerprint");
        this.productionContextPayload = productionContextPayload == null
                ? null : productionContextPayload.clone();
        if (rawSeed == 0L) {
            if (initialSeedLo == null || initialSeedHi == null) {
                throw new IllegalArgumentException("zero-seed LOOT requires named initial state");
            }
            namedSequenceKey = tableKey;
            this.initialSeedLo = initialSeedLo;
            this.initialSeedHi = initialSeedHi;
        } else if (initialSeedLo != null || initialSeedHi != null) {
            throw new IllegalArgumentException("nonzero LOOT cannot own named initial state");
        }
        validatePersistedContext();
        validateDefinitionFingerprint();
        status = Status.UNOPENED;
    }

    public boolean sameDefinition(WorldCanonicalLootAssignment other) {
        if (other == null || verifiedProducer == null && other.verifiedProducer == null) return false;
        try {
            validatePersistedContext();
            validateDefinitionFingerprint();
            other.validatePersistedContext();
            other.validateDefinitionFingerprint();
        } catch (RuntimeException invalid) {
            return false;
        }
        return worldId == other.worldId && worldSeed == other.worldSeed
                && chunkX == other.chunkX
                && chunkZ == other.chunkZ && packed == other.packed && posX == other.posX
                && posY == other.posY && posZ == other.posZ && facing.equals(other.facing)
                && tableKey.equals(other.tableKey) && rawSeed == other.rawSeed
                && containerKind == other.containerKind
                && laneInstallationIdentity.equals(other.laneInstallationIdentity)
                && installationFingerprint.equals(other.installationFingerprint)
                && definitionFingerprint.equals(other.definitionFingerprint)
                && sameBytes(productionContextPayload, other.productionContextPayload)
                && Objects.equals(namedSequenceKey, other.namedSequenceKey)
                && Objects.equals(initialSeedLo, other.initialSeedLo)
                && Objects.equals(initialSeedHi, other.initialSeedHi);
    }

    @jakarta.persistence.Transient
    private NeutralFinalChunk verifiedProducer;

    /** Binds persisted context and row facts to the exact selected producer carrier. */
    public void verifyProducer(NeutralFinalChunk source) {
        Objects.requireNonNull(source, "selected producer source");
        if(source.chunkX()!=chunkX || source.chunkZ()!=chunkZ) throw new IllegalArgumentException("loot source coordinates mismatch");
        source.verifyLoot(productionContextPayload, worldSeed, tableKey, rawSeed,
                posX, posY, posZ, containerKind.slots(), initialSeedLo, initialSeedHi);
        verifiedProducer=source;
    }
    public java.util.Optional<NeutralFinalChunk> verifiedProducerSource() { return java.util.Optional.ofNullable(verifiedProducer); }
    private NeutralFinalChunk requireVerifiedProducer() {
        if(verifiedProducer==null) throw new IllegalStateException("loot assignment requires selected producer verification");
        return verifiedProducer;
    }
    public CanonicalLootStoredResolution resolveCandidate(NeutralFinalChunk source) {
        verifyProducer(source);
        return resolveCandidate();
    }

    public CanonicalLootStoredResolution resolve() {
        return commitResolution(resolveCandidate());
    }

    /** Calculates the exact fixed slots without making the assignment terminal. */
    public CanonicalLootStoredResolution resolveCandidate() {
        validatePersistedContext();
        validateDefinitionFingerprint();
        if (status == Status.REJECTED) {
            throw new IllegalStateException("canonical LOOT assignment is terminally rejected");
        }
        if (status == Status.RESOLVED) return requireStoredResolution();
        NeutralFinalChunk producer = requireVerifiedProducer();
        CanonicalLootStoredResolution candidate = CanonicalLootStoredResolution.decode(
                producer.resolveLoot(productionContextPayload, worldSeed, tableKey, rawSeed,
                        posX, posY, posZ, containerKind.slots(), initialSeedLo, initialSeedHi));
        if (CanonicalLootAssignmentPlan.isCampTable(tableKey)
                && candidate.hasNumericMapId()) {
            throw new IllegalStateException(
                    "production Camp LOOT cannot carry a numeric map placeholder");
        }
        return candidate;
    }

    /** Makes a precomputed result terminal after its container aggregate has accepted it. */
    public CanonicalLootStoredResolution commitResolution(
            CanonicalLootStoredResolution stored) {
        return commitResolution(stored, null, null);
    }

    /** Commits the fixed slots and their exact durable-map allocation receipt together. */
    public CanonicalLootStoredResolution commitResolution(
            CanonicalLootStoredResolution stored, byte[] materializationPayload,
            String materializationReceipt) {
        requireVerifiedProducer();
        Objects.requireNonNull(stored, "canonical LOOT result");
        if (!stored.pendingMapReferences().isEmpty()) {
            throw new IllegalArgumentException("canonical LOOT result still has pending map IDs");
        }
        boolean noMaps = materializationPayload == null && materializationReceipt == null;
        if (!noMaps && (materializationPayload == null || materializationPayload.length == 0
                || materializationReceipt == null
                || !materializationReceipt.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("map materialization identity is incomplete");
        }
        validatePersistedContext();
        validateDefinitionFingerprint();
        requireMapMaterializationInvariant(stored, materializationPayload,
                materializationReceipt);
        if (status == Status.REJECTED) {
            throw new IllegalStateException("canonical LOOT assignment is terminally rejected");
        }
        if (status == Status.RESOLVED) {
            CanonicalLootStoredResolution persisted = requireStoredResolution();
            if (!MessageDigest.isEqual(persisted.encode(), stored.encode())
                    || !sameBytes(mapMaterializationPayload, materializationPayload)
                    || !Objects.equals(mapMaterializationReceipt, materializationReceipt)) {
                throw new IllegalStateException("canonical LOOT replay result mismatch");
            }
            return persisted;
        }
        if (stored.slots().size() != containerKind.slots()) {
            throw new IllegalArgumentException("canonical LOOT result has wrong container shape");
        }
        resolvedPayload = stored.encode();
        continuationKind = stored.continuationKind();
        continuationFirst = stored.continuationFirst();
        continuationSecond = stored.continuationSecond();
        resultFingerprint = stored.fingerprint(definitionFingerprint);
        mapMaterializationPayload = materializationPayload == null
                ? null : materializationPayload.clone();
        mapMaterializationReceipt = materializationReceipt;
        status = Status.RESOLVED;
        return stored;
    }

    public boolean reject() {
        if (status == Status.RESOLVED) return false;
        if (status == Status.REJECTED) return true;
        status = Status.REJECTED;
        return true;
    }

    public CanonicalLootStoredResolution requireStoredResolution() {
        requireVerifiedProducer();
        validatePersistedContext();
        validateDefinitionFingerprint();
        if (status != Status.RESOLVED || resolvedPayload == null || continuationKind == null
                || continuationFirst == null || continuationSecond == null
                || resultFingerprint == null) {
            throw new IllegalStateException("canonical LOOT assignment has no complete result");
        }
        CanonicalLootStoredResolution stored = CanonicalLootStoredResolution.decode(resolvedPayload);
        if (stored.slots().size() != containerKind.slots()
                || stored.continuationKind() != continuationKind
                || stored.continuationFirst() != continuationFirst
                || stored.continuationSecond() != continuationSecond
                || !MessageDigest.isEqual(resultFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        stored.fingerprint(definitionFingerprint).getBytes(
                                java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("persisted canonical LOOT result fingerprint mismatch");
        }
        if (!stored.pendingMapReferences().isEmpty()
                || (mapMaterializationPayload == null) != (mapMaterializationReceipt == null)
                || mapMaterializationReceipt != null
                        && !mapMaterializationReceipt.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("persisted canonical LOOT map materialization is incomplete");
        }
        requireMapMaterializationInvariant(stored, mapMaterializationPayload,
                mapMaterializationReceipt);
        return stored;
    }

    private void requireMapMaterializationInvariant(CanonicalLootStoredResolution stored,
            byte[] materializationPayload, String materializationReceipt) {
        boolean hasIdentity = materializationPayload != null && materializationReceipt != null;
        boolean requiresIdentity = CanonicalLootAssignmentPlan.isCampTable(tableKey)
                && stored.hasNumericMapId();
        if (requiresIdentity != hasIdentity) {
            throw new IllegalStateException(requiresIdentity
                    ? "production Camp map ID lacks durable materialization identity"
                    : "map materialization identity has no production Camp map ID");
        }
    }

    public byte[] getResolvedPayload() {
        return resolvedPayload == null ? null : resolvedPayload.clone();
    }

    public byte[] getMapMaterializationPayload() {
        return mapMaterializationPayload == null ? null : mapMaterializationPayload.clone();
    }

    /** Returns the exact persisted bytes, never a re-encoded descriptor. */
    public byte[] getProductionContextPayload() {
        validatePersistedContext();
        return productionContextPayload == null ? null : productionContextPayload.clone();
    }

    private String lateContextSha256() {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(productionContextPayload));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public byte[] getLateOutcomePayload() {
        return lateOutcomePayload==null?null:lateOutcomePayload.clone();
    }
    public void bindVerifiedLateOutcome(com.gameexpert.authority.versioned.LateLootOutcome outcome) {
        requireVerifiedProducer();Objects.requireNonNull(outcome,"verified late outcome");
        if(outcome.needsStructure()||!lateContextSha256().equals(outcome.originalContextSha256()))
            throw new IllegalArgumentException("late outcome differs from original declaration");
        if(lateOutcomePayload!=null&&!java.util.Arrays.equals(lateOutcomePayload,outcome.encoded()))
            throw new IllegalStateException("persisted late outcome did not replay exactly");
        verifiedLateOutcome=outcome;
    }
    public void stageLateOutcome(String snapshotIdentity,
            com.gameexpert.authority.versioned.LateLootOutcome outcome) {
        if(status!=Status.UNOPENED)throw new IllegalStateException("late outcome requires pending assignment");
        bindVerifiedLateOutcome(outcome);
        if(!outcome.referenceEpoch().equals(requireFingerprint(snapshotIdentity,"late snapshot identity")))
            throw new IllegalArgumentException("late outcome snapshot differs");
        lateSnapshotIdentity=snapshotIdentity;lateOutcomePayload=outcome.encoded();
    }
    public java.util.Optional<com.gameexpert.authority.versioned.LateLootOutcome> verifiedLateOutcome() {
        if(lateOutcomePayload!=null&&verifiedLateOutcome==null)
            throw new IllegalStateException("persisted late outcome requires exact producer replay");
        return java.util.Optional.ofNullable(verifiedLateOutcome);
    }
    public NeutralFinalChunk.ProductionContext getEffectiveLocatedProductionContext() {
        return verifiedLateOutcome().map(com.gameexpert.authority.versioned.LateLootOutcome::resolvedContext)
                .orElseGet(this::getLocatedProductionContext);
    }
    public NeutralFinalChunk.ProductionContext getLocatedProductionContext() {
        requireVerifiedProducer();
        return persistedLocatedProductionContext().productionContext();
    }

    private CanonicalLootAssignmentPlan.LocatedProductionContextPayload
            persistedLocatedProductionContext() {
        if (productionContextPayload == null
                || !CanonicalLootAssignmentPlan.isLocatedProductionContextPayload(
                        productionContextPayload)) {
            throw new IllegalStateException(
                    "canonical LOOT assignment has no located production context");
        }
        CanonicalLootAssignmentPlan.LocatedProductionContextPayload payload =
                CanonicalLootAssignmentPlan.decodeLocatedProductionContextPayload(
                        productionContextPayload, expectedContextSourceSection());
        validateLocatedEnvelope(payload);
        return payload;
    }

    private NeutralFinalChunk.ContainerLootSourceSection expectedContextSourceSection() {
        String prefix = worldId + ":" + chunkX + ":" + chunkZ + ":ENTITIES:";
        return laneInstallationIdentity.startsWith(prefix)
                && laneInstallationIdentity.substring(prefix.length())
                        .matches("[0-9a-f]{64}:[0-9a-f]{64}")
                ? NeutralFinalChunk.ContainerLootSourceSection.ENTS
                : NeutralFinalChunk.ContainerLootSourceSection.LOOT;
    }

    private void validateLocatedEnvelope(
            CanonicalLootAssignmentPlan.LocatedProductionContextPayload payload) {
        String entityPrefix = worldId + ":" + chunkX + ":" + chunkZ + ":ENTITIES:";
        boolean entityLane = laneInstallationIdentity.startsWith(entityPrefix);
        boolean blockLoot = payload.sourceSection()
                == NeutralFinalChunk.ContainerLootSourceSection.LOOT
                && payload.sourceSectionOrdinal() == payload.ordinal() && !entityLane;
        boolean entityLoot = payload.sourceSection()
                == NeutralFinalChunk.ContainerLootSourceSection.ENTS
                && entityLane
                && laneInstallationIdentity.substring(entityPrefix.length())
                        .matches("[0-9a-f]{64}:[0-9a-f]{64}")
                && containerKind == CanonicalLootContainerKind.CHEST
                && "entity".equals(facing)
                && payload.ordinal() >= 0
                && payload.sourceSectionOrdinal() >= payload.ordinal();
        if ((!blockLoot && !entityLoot)
                || payload.containerSize() != containerKind.slots()) {
            throw new IllegalStateException(
                    "located canonical LOOT production context does not match assignment");
        }
        try {
            payload.productionContext().requireMatches(worldSeed, tableKey, posX, posY, posZ);
        } catch (IllegalArgumentException mismatch) {
            throw new IllegalStateException(
                    "located canonical LOOT context identity does not match assignment", mismatch);
        }
        requireDeclaredPosition();
    }

    private void requireDeclaredPosition() {
        if (packed < 0 || packed >= Blocks.CHUNK_BLOCKS) {
            throw new IllegalStateException("canonical LOOT declaration packed cell is invalid");
        }
        int localX = packed % Blocks.CHUNK_X;
        int yz = packed / Blocks.CHUNK_X;
        int localZ = yz % Blocks.CHUNK_Z;
        int localY = Blocks.MIN_Y + yz / Blocks.CHUNK_Z;
        long declaredX = (long) chunkX * Blocks.CHUNK_X + localX;
        long declaredZ = (long) chunkZ * Blocks.CHUNK_Z + localZ;
        if (declaredX != posX || localY != posY || declaredZ != posZ) {
            throw new IllegalStateException(
                    "canonical LOOT production context differs from final-carrier declaration");
        }
    }

    private void validatePersistedContext() {
        persistedLocatedProductionContext();
    }

    private void validateDefinitionFingerprint() {
        var context = persistedLocatedProductionContext();
        int ordinal = context.ordinal();
        if (rawSeed == 0L && (initialSeedLo == null || initialSeedHi == null)) {
            throw new IllegalStateException("zero-seed LOOT has incomplete named initial state");
        }
        if (rawSeed != 0L && (initialSeedLo != null || initialSeedHi != null)) {
            throw new IllegalStateException("nonzero LOOT has named initial state");
        }
        String expected = CanonicalLootAssignmentPlan.fingerprint(worldSeed,
                laneInstallationIdentity, installationFingerprint, worldId, chunkX, chunkZ, packed,
                posX, posY, posZ, facing, tableKey, rawSeed, containerKind,
                productionContextPayload, rawSeed == 0L
                        ? new long[] {initialSeedLo, initialSeedHi} : null, context.sourceSection());
        if (!definitionFingerprint.equals(expected)) {
            throw new IllegalStateException("canonical LOOT definition fingerprint mismatch");
        }
        if (ordinal < 0) {
            throw new IllegalStateException("canonical LOOT declaration ordinal is invalid");
        }
    }

    private static boolean sameBytes(byte[] first, byte[] second) {
        return first == null ? second == null : second != null
                && MessageDigest.isEqual(first, second);
    }

    private static String requireText(String value, String label, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(label + " required");
        }
        return value;
    }

    private static String requireFingerprint(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be SHA-256");
        }
        return value;
    }
}
