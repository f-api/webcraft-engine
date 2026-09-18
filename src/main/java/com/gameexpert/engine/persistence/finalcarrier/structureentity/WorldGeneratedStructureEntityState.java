package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.GeneratedCushionActionPolicy;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator.CushionMutationAuthority;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator.GeneratedEntityMutationCapability;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator.GeneratedEntityMutationOperation;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator.SettledCushionTerminal;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.ArmorStand;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.ChestMinecart;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Cushion;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Decoded;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.EntityFacts;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Transform;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.gameexpert.terrain.mc.structure.Mc263VillageTemplateEntityFacts;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mutable runtime state for one authenticated generated non-mob ENTS row.
 *
 * <p>The immutable source binding is projected only from {@link GeneratedStructureEntityFacts};
 * callers cannot supply a kind, installation identity, ordinal, fingerprint, or initial facts.
 * Runtime mutations use an exact revision CAS. A DEAD row is a retained immutable tombstone.</p>
 */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_generated_structure_entity_states", uniqueConstraints = {
        @UniqueConstraint(name = "uk_generated_structure_entity_world_entity",
                columnNames = {"world_id", "authoritative_entity_id"}),
        @UniqueConstraint(name = "uk_generated_structure_entity_install_ordinal",
                columnNames = {"lane_installation_identity", "encounter_ordinal"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldGeneratedStructureEntityState {
    public enum Lifecycle { LIVE, DEAD }
    public enum LootStatus { UNOPENED, RESOLVED }

    public record RuntimeBinding(long worldId, int chunkX, int chunkZ, long entityId,
            GeneratedStructureEntityFacts.Kind kind, String installationIdentity,
            String installationSourceFingerprint, int encounterOrdinal,
            String sourceRowFingerprint) {}

    public sealed interface RuntimeSnapshot
            permits ArmorStandRuntimeSnapshot, CushionRuntimeSnapshot, MinecartRuntimeSnapshot {
        RuntimeBinding binding();
        long revision();
        Lifecycle lifecycle();
        Transform transform();
    }

    public record ArmorStandRuntimeSnapshot(RuntimeBinding binding, long revision,
            Lifecycle lifecycle, Transform transform, float[] poseHead, float[] poseBody,
            String equipmentSlot, String equipmentItem, boolean showArms, boolean small,
            boolean noBasePlate, boolean invisible, boolean invulnerable, int disabledSlots,
            float health) implements RuntimeSnapshot {
        public ArmorStandRuntimeSnapshot {
            poseHead = poseHead.clone();
            poseBody = poseBody.clone();
        }
        @Override public float[] poseHead() { return poseHead.clone(); }
        @Override public float[] poseBody() { return poseBody.clone(); }
    }

    public record CushionRuntimeSnapshot(RuntimeBinding binding, long revision,
            Lifecycle lifecycle, Transform transform, String color, int blockX, int blockY,
            int blockZ, boolean invulnerable, String rider, String customName)
            implements RuntimeSnapshot {}

    public record MinecartRuntimeSnapshot(RuntimeBinding binding, long revision,
            Lifecycle lifecycle, Transform transform, String lootTable, long lootSeed,
            String provenance, LootStatus lootStatus, String lootDefinitionFingerprint,
            String lootResultFingerprint, byte[] lootResolution, List<ChestItem> cargo)
            implements RuntimeSnapshot {
        public MinecartRuntimeSnapshot {
            lootResolution = lootResolution == null ? null : lootResolution.clone();
            cargo = List.copyOf(copyCargo(cargo));
        }
        @Override public byte[] lootResolution() {
            return lootResolution == null ? null : lootResolution.clone();
        }
        @Override public List<ChestItem> cargo() { return List.copyOf(copyCargo(cargo)); }
    }

    private static final int CARGO_SLOTS = CanonicalLootContainerKind.CHEST.slots();
    private static final byte[] BINDING_DOMAIN =
            "MCF263/GENERATED-ENTITY/BINDING/v1".getBytes(StandardCharsets.US_ASCII);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false) private long authoritativeEntityId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24) private GeneratedStructureEntityFacts.Kind kind;
    @Column(nullable = false, length = 255) private String laneInstallationIdentity;
    @Column(nullable = false, length = 64) private String installationSourceFingerprint;
    @Column(nullable = false) private int encounterOrdinal;
    @Column(nullable = false, length = 64) private String sourceRowFingerprint;
    @Column(nullable = false, length = 64) private String durableBindingFingerprint;
    @Column(nullable = false) private long revision;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8) private Lifecycle lifecycle;

    @Column(nullable = false) private long xBits;
    @Column(nullable = false) private long yBits;
    @Column(nullable = false) private long zBits;
    @Column(nullable = false) private int yawBits;
    @Column(nullable = false) private int pitchBits;
    @Column(nullable = false) private long velocityXBits;
    @Column(nullable = false) private long velocityYBits;
    @Column(nullable = false) private long velocityZBits;

    @Getter(AccessLevel.NONE) @Lob private byte[] armorPoseHeadBits;
    @Getter(AccessLevel.NONE) @Lob private byte[] armorPoseBodyBits;
    @Column(length = 8) private String armorEquipmentSlot;
    @Column(length = 128) private String armorEquipmentItem;
    private Boolean armorShowArms;
    private Boolean armorSmall;
    private Boolean armorNoBasePlate;
    private Boolean armorInvisible;
    private Boolean armorInvulnerable;
    private Integer armorDisabledSlots;
    private Integer armorHealthBits;

    @Column(length = 16) private String cushionColor;
    private Integer cushionBlockX;
    private Integer cushionBlockY;
    private Integer cushionBlockZ;
    private Boolean cushionInvulnerable;
    @Column(length = 12) private String cushionRider;
    @Column(length = 64) private String cushionCustomName;

    // Reserved persistence shape only. These columns are not a receipt or mutation capability;
    // a future transaction-owning coordinator must authenticate and bind their complete payload.
    @Getter(AccessLevel.NONE)
    @Column(name = "terminal_ground_mutation_id")
    private Long terminalGroundMutationId;
    @Getter(AccessLevel.NONE)
    @Column(name = "terminal_ground_entity_id")
    private Long terminalGroundEntityId;
    @Getter(AccessLevel.NONE)
    @Column(name = "terminal_ground_command_fingerprint", length = 64)
    private String terminalGroundCommandFingerprint;

    @Column(length = 255) private String minecartLootTable;
    private Long minecartLootSeed;
    @Column(length = 16) private String minecartProvenance;
    @Enumerated(EnumType.STRING)
    @Column(length = 16) private LootStatus minecartLootStatus;
    @Column(length = 64) private String minecartLootDefinitionFingerprint;
    @Column(length = 64) private String minecartLootResultFingerprint;
    @Getter(AccessLevel.NONE) @Lob private byte[] minecartLootResolution;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "world_generated_structure_entity_cargo",
            joinColumns = @JoinColumn(name = "state_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_generated_entity_cargo_slot",
                    columnNames = {"state_id", "slot"}))
    private List<ChestItem> minecartCargo = new ArrayList<>();

    /**
     * Installs revision zero from a terminal aggregate and its exact persisted source row.
     * The expected coordinates and identity come from the owning lane mutation, not the row.
     */
    public static WorldGeneratedStructureEntityState install(long expectedWorldId,
            int expectedChunkX, int expectedChunkZ, String expectedInstallationIdentity,
            WorldStructureEntity source, StructureEntityAggregate aggregate) {
        Objects.requireNonNull(source, "generated structure entity source");
        authenticateSource(expectedWorldId, expectedChunkX, expectedChunkZ,
                expectedInstallationIdentity, source, aggregate);
        var result = GeneratedStructureEntityFacts.decode(source);
        if (!(result instanceof Decoded decoded)) {
            throw new IllegalArgumentException("source row is not an owned generated entity");
        }
        EntityFacts facts = decoded.facts();
        if (facts.lifecycle() != WorldStructureEntity.BindingStatus.LIVE) {
            throw new IllegalStateException("cannot install generated state from a dead binding");
        }
        WorldGeneratedStructureEntityState state = new WorldGeneratedStructureEntityState();
        state.bind(source, facts);
        state.revision = 0L;
        state.lifecycle = Lifecycle.LIVE;
        state.setTransform(facts.transform());
        if (facts instanceof ArmorStand armor) state.installArmor(armor);
        else if (facts instanceof Cushion cushion) state.installCushion(cushion);
        else if (facts instanceof ChestMinecart minecart) state.installMinecart(minecart);
        else throw new IllegalArgumentException("unsupported generated entity facts");
        state.requireValidDurableState();
        return state;
    }

    /**
     * Classifies one aggregate-planned row before durable IDs are reserved. Known malformed
     * generated kinds throw; ordinary mob rows return empty.
     */
    public static Optional<GeneratedStructureEntityFacts.Kind> classifyPlannedEntity(
            StructureEntityAggregate.PlannedEntity planned) {
        Objects.requireNonNull(planned, "planned ENTS entity");
        WorldStructureEntity transientRow = new WorldStructureEntity(1L, 0, 0,
                "preflight", "0".repeat(64), planned);
        var result = GeneratedStructureEntityFacts.decode(transientRow);
        return result instanceof Decoded decoded
                ? Optional.of(decoded.facts().kind()) : Optional.empty();
    }

    private void bind(WorldStructureEntity source, EntityFacts facts) {
        if (source.getWorldId() <= 0L || source.getAuthoritativeEntityId() <= 0L
                || source.getEncounterOrdinal() < 0) {
            throw new IllegalArgumentException("invalid generated entity source binding");
        }
        worldId = source.getWorldId();
        chunkX = source.getChunkX();
        chunkZ = source.getChunkZ();
        authoritativeEntityId = source.getAuthoritativeEntityId();
        kind = facts.kind();
        laneInstallationIdentity = text(source.getLaneInstallationIdentity(), 255,
                "installation identity");
        installationSourceFingerprint = fingerprint(source.getInstallationSourceFingerprint(),
                "installation source fingerprint");
        encounterOrdinal = source.getEncounterOrdinal();
        sourceRowFingerprint = fingerprint(source.getRowFingerprint(), "source row fingerprint");
        durableBindingFingerprint = computeDurableBindingFingerprint();
    }

    private void installArmor(ArmorStand armor) {
        armorPoseHeadBits = encodePose(armor.poseHead());
        armorPoseBodyBits = encodePose(armor.poseBody());
        armorEquipmentSlot = armor.equipmentSlot();
        armorEquipmentItem = armor.equipmentItem();
        armorShowArms = armor.showArms();
        armorSmall = armor.small();
        armorNoBasePlate = armor.noBasePlate();
        armorInvisible = armor.invisible();
        armorInvulnerable = armor.invulnerable();
        armorDisabledSlots = armor.disabledSlots();
        armorHealthBits = Float.floatToRawIntBits(armor.health());
    }

    private void installCushion(Cushion cushion) {
        cushionColor = cushion.color();
        cushionBlockX = cushion.blockX();
        cushionBlockY = cushion.blockY();
        cushionBlockZ = cushion.blockZ();
        cushionInvulnerable = cushion.invulnerable();
    }

    private void installMinecart(ChestMinecart minecart) {
        minecartLootTable = minecart.lootTable();
        minecartLootSeed = minecart.lootSeed();
        minecartProvenance = minecart.provenance();
        minecartLootStatus = LootStatus.UNOPENED;
        minecartCargo = new ArrayList<>(); // Sparse encoding of exactly CARGO_SLOTS empty slots.
    }

    /** Verifies an unknown-commit replay against its lane-owned terminal aggregate. */
    public void requireSameBinding(long expectedWorldId, int expectedChunkX, int expectedChunkZ,
            String expectedInstallationIdentity, WorldStructureEntity source,
            StructureEntityAggregate aggregate) {
        Objects.requireNonNull(source, "generated structure entity source");
        authenticateSource(expectedWorldId, expectedChunkX, expectedChunkZ,
                expectedInstallationIdentity, source, aggregate);
        var result = GeneratedStructureEntityFacts.decode(source);
        if (!(result instanceof Decoded decoded)) {
            throw new IllegalStateException("generated entity binding kind changed");
        }
        EntityFacts facts = decoded.facts();
        if (kind != facts.kind()) {
            throw new IllegalStateException("generated entity binding kind changed");
        }
        if (worldId != source.getWorldId()
                || chunkX != source.getChunkX() || chunkZ != source.getChunkZ()
                || authoritativeEntityId != source.getAuthoritativeEntityId()
                || !laneInstallationIdentity.equals(source.getLaneInstallationIdentity())
                || !installationSourceFingerprint.equals(source.getInstallationSourceFingerprint())
                || encounterOrdinal != source.getEncounterOrdinal()
                || !sourceRowFingerprint.equals(source.getRowFingerprint())) {
            throw new IllegalStateException("generated entity source binding drift");
        }
        Lifecycle sourceLifecycle = source.bindingStatus() == WorldStructureEntity.BindingStatus.LIVE
                ? Lifecycle.LIVE : Lifecycle.DEAD;
        if (sourceLifecycle != lifecycle) {
            throw new IllegalStateException("generated entity source/state lifecycle conflict");
        }
        requireValidDurableState();
    }

    /** Legacy values-only armor mutation entry point; capability-free mutation is rejected. */
    void applyArmorStand(long expectedRevision, long nextRevision,
            Transform transform, float health) {
        rejectUnauthenticatedGenericMutation();
    }

    /** Applies armor state only from a coordinator-issued, state-bound capability. */
    public synchronized void applyArmorStand(GeneratedEntityMutationCapability capability,
            Transform transform, float health) {
        requireValidDurableState();
        requireKind(GeneratedStructureEntityFacts.Kind.ARMOR_STAND);
        long expectedRevision = revision;
        long nextRevision = Math.addExact(expectedRevision, 1L);
        validateTransition(expectedRevision, nextRevision);
        validateTransform(transform);
        if (!Float.isFinite(health) || health <= 0.0f || health > 20.0f
                || Float.floatToRawIntBits(health) == Float.floatToRawIntBits(-0.0f)) {
            throw new IllegalArgumentException("invalid live armor stand health");
        }
        ArmorStandRuntimeSnapshot current = (ArmorStandRuntimeSnapshot) runtimeSnapshot();
        ArmorStandRuntimeSnapshot next = new ArmorStandRuntimeSnapshot(current.binding(),
                nextRevision, Lifecycle.LIVE, transform, current.poseHead(), current.poseBody(),
                current.equipmentSlot(), current.equipmentItem(), current.showArms(),
                current.small(), current.noBasePlate(), current.invisible(), current.invulnerable(),
                current.disabledSlots(), health);
        requireGeneratedMutationCapability(GeneratedStructureEntityFacts.Kind.ARMOR_STAND,
                GeneratedEntityMutationOperation.ARMOR_STAND_MUTATION, capability, next);
        setTransform(transform);
        armorHealthBits = Float.floatToRawIntBits(health);
        revision = nextRevision;
    }

    /** Applies only the equipment half of an authenticated player/entity settlement. */
    public synchronized void applyArmorStandEquipment(
            GeneratedEntityMutationCapability capability, String equipmentItem) {
        requireValidDurableState();
        requireKind(GeneratedStructureEntityFacts.Kind.ARMOR_STAND);
        String nextItem = text(equipmentItem, 128, "armor stand equipment item");
        long nextRevision = Math.addExact(revision, 1L);
        ArmorStandRuntimeSnapshot current = (ArmorStandRuntimeSnapshot) runtimeSnapshot();
        ArmorStandRuntimeSnapshot next = new ArmorStandRuntimeSnapshot(current.binding(),
                nextRevision, Lifecycle.LIVE, current.transform(), current.poseHead(),
                current.poseBody(), current.equipmentSlot(), nextItem, current.showArms(),
                current.small(), current.noBasePlate(), current.invisible(),
                current.invulnerable(), current.disabledSlots(), current.health());
        if (!validArmorTuple(next.poseHead(), next.poseBody(), next.equipmentSlot(), nextItem)) {
            throw new IllegalArgumentException("unsupported armor stand equipment transition");
        }
        requireGeneratedMutationCapability(GeneratedStructureEntityFacts.Kind.ARMOR_STAND,
                GeneratedEntityMutationOperation.ARMOR_STAND_EQUIPMENT_SETTLEMENT,
                capability, next);
        armorEquipmentItem = nextItem;
        revision = nextRevision;
        requireValidDurableState();
    }

    /** Legacy values-only Cushion entry point; it is intentionally non-mutating. */
    public void applyCushion(long expectedRevision, long nextRevision,
            String rider, String customName) {
        rejectUnauthenticatedGenericMutation();
    }

    /** Applies mutable Cushion values only through a coordinator-private opaque authority. */
    public synchronized void applyCushion(CushionMutationAuthority authority,
            String rider, String customName) {
        requireValidDurableState();
        requireKind(GeneratedStructureEntityFacts.Kind.CUSHION);
        long expectedRevision = revision;
        long nextRevision = Math.addExact(expectedRevision, 1L);
        validateTransition(expectedRevision, nextRevision);
        String validatedRider = optionalText(rider, 12, "cushion rider");
        if (validatedRider != null && !validatedRider.matches("[A-Za-z0-9_]{2,12}")) {
            throw new IllegalArgumentException("invalid cushion rider");
        }
        String validatedName = optionalText(customName, 64, "cushion custom name");
        CushionRuntimeSnapshot current = (CushionRuntimeSnapshot) runtimeSnapshot();
        CushionRuntimeSnapshot next = new CushionRuntimeSnapshot(current.binding(), nextRevision,
                Lifecycle.LIVE, current.transform(), current.color(), current.blockX(),
                current.blockY(), current.blockZ(), current.invulnerable(), validatedRider,
                validatedName);
        GeneratedStructureEntityMutationCoordinator.requireCushionMutationAuthority(this,
                authority, next);
        cushionRider = validatedRider;
        cushionCustomName = validatedName;
        revision = nextRevision;
    }

    /**
     * Applies the terminal Cushion transition only from the coordinator's private-constructor
     * proof minted after the exact ground receipt and item have been observed in this transaction.
     */
    public void applySettledCushionTerminal(SettledCushionTerminal authority,
            WorldStructureEntity source, StructureEntityAggregate aggregate) {
        requireKind(GeneratedStructureEntityFacts.Kind.CUSHION);
        Objects.requireNonNull(authority, "settled generated Cushion authority");
        Objects.requireNonNull(source, "generated Cushion source");
        Objects.requireNonNull(aggregate, "generated Cushion aggregate");
        if (authority.sourceIdentity() != source
                || authority.cause() != GeneratedCushionActionPolicy.Action.BREAK
                        && authority.cause() != GeneratedCushionActionPolicy.Action.SUPPORT_LOSS
                || authority.worldId() != worldId
                || authority.expectedRevision() != revision
                || authority.nextRevision() != Math.addExact(revision, 1L)
                || authority.mutationId() <= 0L || authority.mutationId() == Long.MAX_VALUE
                || authority.groundEntityId() <= 0L
                || authority.groundEntityId() == Long.MAX_VALUE
                || authoritativeEntityId > (Long.MAX_VALUE - 7L) / 8L + 1L
                || authority.mutationId() != (authoritativeEntityId - 1L) * 8L + 7L) {
            throw new IllegalStateException("generated Cushion terminal authority differs");
        }
        fingerprint(authority.deliveryKey(), "terminal delivery key");
        String commandFingerprint = fingerprint(authority.commandFingerprint(),
                "terminal ground command fingerprint");
        GeneratedCushionActionPolicy.SourceProof proof = authority.sourceProof();
        if (proof.worldId() != worldId || proof.chunkX() != chunkX || proof.chunkZ() != chunkZ
                || proof.authoritativeEntityId() != authoritativeEntityId
                || !proof.installationIdentity().equals(laneInstallationIdentity)
                || !proof.installationSourceFingerprint().equals(installationSourceFingerprint)
                || proof.encounterOrdinal() != encounterOrdinal
                || !proof.sourceRowFingerprint().equals(sourceRowFingerprint)) {
            throw new IllegalStateException("generated Cushion terminal source proof differs");
        }
        validateTransition(authority.expectedRevision(), authority.nextRevision());
        authenticateSource(worldId, chunkX, chunkZ, laneInstallationIdentity, source, aggregate);
        requireSameBinding(worldId, chunkX, chunkZ, laneInstallationIdentity, source, aggregate);
        if (!source.markDead()) {
            throw new IllegalStateException("generated Cushion source tombstone transition failed");
        }
        terminalGroundMutationId = authority.mutationId();
        terminalGroundEntityId = authority.groundEntityId();
        terminalGroundCommandFingerprint = commandFingerprint;
        cushionRider = null;
        lifecycle = Lifecycle.DEAD;
        revision = authority.nextRevision();
        requireValidDurableState();
    }

    /** Legacy values-only minecart motion entry point; capability-free mutation is rejected. */
    void applyChestMinecartMotion(long expectedRevision, long nextRevision,
            Transform transform) {
        rejectUnauthenticatedGenericMutation();
    }

    /** Applies minecart motion only from a coordinator-issued, state-bound capability. */
    public synchronized void applyChestMinecartMotion(GeneratedEntityMutationCapability capability,
            Transform transform) {
        requireValidDurableState();
        requireKind(GeneratedStructureEntityFacts.Kind.CHEST_MINECART);
        long expectedRevision = revision;
        long nextRevision = Math.addExact(expectedRevision, 1L);
        validateTransition(expectedRevision, nextRevision);
        validateTransform(transform);
        MinecartRuntimeSnapshot current = (MinecartRuntimeSnapshot) runtimeSnapshot();
        MinecartRuntimeSnapshot next = new MinecartRuntimeSnapshot(current.binding(), nextRevision,
                Lifecycle.LIVE, transform, current.lootTable(), current.lootSeed(),
                current.provenance(), current.lootStatus(), current.lootDefinitionFingerprint(),
                current.lootResultFingerprint(), current.lootResolution(), current.cargo());
        requireGeneratedMutationCapability(GeneratedStructureEntityFacts.Kind.CHEST_MINECART,
                GeneratedEntityMutationOperation.CHEST_MINECART_MOTION, capability, next);
        setTransform(transform);
        revision = nextRevision;
    }

    /** Legacy values-only terminal entry point; capability-free mutation is rejected. */
    public void markDead(long expectedRevision, long nextRevision,
            long expectedWorldId, int expectedChunkX, int expectedChunkZ,
            String expectedInstallationIdentity, WorldStructureEntity source,
            StructureEntityAggregate aggregate) {
        if (kind == GeneratedStructureEntityFacts.Kind.CUSHION) {
            throw new IllegalStateException(
                    "cushion terminal settlement requires authenticated coordinator");
        }
        rejectUnauthenticatedGenericMutation();
    }

    /** Terminalizes an Armor Stand only from its coordinator-issued terminal capability. */
    public synchronized void markArmorStandDead(GeneratedEntityMutationCapability capability,
            long expectedWorldId, int expectedChunkX, int expectedChunkZ,
            String expectedInstallationIdentity, WorldStructureEntity source,
            StructureEntityAggregate aggregate) {
        terminalizeGeneratedEntity(GeneratedStructureEntityFacts.Kind.ARMOR_STAND, capability,
                expectedWorldId, expectedChunkX, expectedChunkZ, expectedInstallationIdentity,
                source, aggregate);
    }

    /** Terminalizes a chest minecart only from its coordinator-issued terminal capability. */
    public synchronized void markChestMinecartDead(GeneratedEntityMutationCapability capability,
            long expectedWorldId, int expectedChunkX, int expectedChunkZ,
            String expectedInstallationIdentity, WorldStructureEntity source,
            StructureEntityAggregate aggregate) {
        throw new IllegalStateException(
                "chest minecart terminalization requires a cargo ground-destruction receipt handoff");
    }

    private void terminalizeGeneratedEntity(GeneratedStructureEntityFacts.Kind expectedKind,
            GeneratedEntityMutationCapability capability, long expectedWorldId,
            int expectedChunkX, int expectedChunkZ, String expectedInstallationIdentity,
            WorldStructureEntity source, StructureEntityAggregate aggregate) {
        requireValidDurableState();
        requireKind(expectedKind);
        long expectedRevision = revision;
        long nextRevision = Math.addExact(expectedRevision, 1L);
        validateTransition(expectedRevision, nextRevision);
        authenticateSource(expectedWorldId, expectedChunkX, expectedChunkZ,
                expectedInstallationIdentity, source, aggregate);
        requireSameBinding(expectedWorldId, expectedChunkX, expectedChunkZ,
                expectedInstallationIdentity, source, aggregate);
        ArmorStandRuntimeSnapshot current = (ArmorStandRuntimeSnapshot) runtimeSnapshot();
        ArmorStandRuntimeSnapshot next = new ArmorStandRuntimeSnapshot(current.binding(),
                nextRevision, Lifecycle.DEAD, current.transform(), current.poseHead(),
                current.poseBody(), current.equipmentSlot(), current.equipmentItem(),
                current.showArms(), current.small(), current.noBasePlate(), current.invisible(),
                current.invulnerable(), current.disabledSlots(), 0.0f);
        requireGeneratedMutationCapability(expectedKind, GeneratedEntityMutationOperation.TERMINAL,
                capability, next);
        if (!source.markDead()) {
            throw new IllegalStateException("generated entity source tombstone transition failed");
        }
        if (expectedKind == GeneratedStructureEntityFacts.Kind.ARMOR_STAND) {
            armorHealthBits = Float.floatToRawIntBits(0.0f);
        }
        lifecycle = Lifecycle.DEAD;
        revision = nextRevision;
        requireValidDurableState();
    }

    /** Legacy values-only loot entry point; canonical resolution requires transaction authority. */
    void applyResolvedMinecartLoot(long expectedRevision, long nextRevision,
            String definitionFingerprint, CanonicalLootStoredResolution resolution,
            List<ChestItem> cargo) {
        rejectUnauthenticatedGenericMutation();
    }

    /** Commits the exact nested LOOT result only from its locked transaction owner. */
    synchronized void applyResolvedMinecartLoot(
            GeneratedChestMinecartLootResolutionService.ResolutionAuthority authority) {
        Objects.requireNonNull(authority, "generated minecart LOOT resolution authority");
        requireValidDurableState();
        requireKind(GeneratedStructureEntityFacts.Kind.CHEST_MINECART);
        if (authority.state != this || authority.source.getWorldId() != worldId
                || authority.source.getAuthoritativeEntityId() != authoritativeEntityId
                || authority.source.bindingStatus() != WorldStructureEntity.BindingStatus.LIVE
                || !authority.installationIdentity.equals(laneInstallationIdentity)
                || !authority.installationSourceFingerprint
                        .equals(installationSourceFingerprint)
                || !authority.source.getLaneInstallationIdentity()
                        .equals(laneInstallationIdentity)
                || !authority.source.getInstallationSourceFingerprint()
                        .equals(installationSourceFingerprint)
                || authority.source.getEncounterOrdinal() != encounterOrdinal
                || !authority.source.getRowFingerprint().equals(sourceRowFingerprint)) {
            throw new IllegalStateException(
                    "generated minecart LOOT resolution authority differs from ENTS source");
        }
        long expectedRevision = authority.expectedSourceRevision;
        long nextRevision = Math.addExact(expectedRevision, 1L);
        validateTransition(expectedRevision, nextRevision);
        if (minecartLootStatus != LootStatus.UNOPENED) {
            throw new IllegalStateException("chest minecart loot is already terminal");
        }
        String definition = fingerprint(authority.definitionFingerprint,
                "loot definition fingerprint");
        CanonicalLootStoredResolution resolution = Objects.requireNonNull(
                authority.resolution, "canonical chest minecart loot resolution");
        if (resolution.slots().size() != CARGO_SLOTS) {
            throw new IllegalArgumentException("chest minecart loot must have 27 slots");
        }
        byte[] encoded = resolution.encode();
        ArrayList<ChestItem> copiedCargo = copyCargo(projectCanonicalMinecartCargo(resolution));
        minecartLootDefinitionFingerprint = definition;
        minecartLootResultFingerprint = resolution.fingerprint(definition);
        minecartLootResolution = encoded.clone();
        minecartCargo = copiedCargo;
        minecartLootStatus = LootStatus.RESOLVED;
        revision = nextRevision;
    }

    /** Legacy values-only cargo entry point; capability-free mutation is rejected. */
    void applyMinecartCargo(long expectedRevision, long nextRevision,
            List<ChestItem> cargo) {
        rejectUnauthenticatedGenericMutation();
    }

    /** Replaces minecart cargo only from a coordinator-issued, state-bound capability. */
    public synchronized void applyMinecartCargo(GeneratedEntityMutationCapability capability,
            List<ChestItem> cargo) {
        requireValidDurableState();
        requireKind(GeneratedStructureEntityFacts.Kind.CHEST_MINECART);
        long expectedRevision = revision;
        long nextRevision = Math.addExact(expectedRevision, 1L);
        validateTransition(expectedRevision, nextRevision);
        if (minecartLootStatus != LootStatus.RESOLVED) {
            throw new IllegalStateException("chest minecart loot is not resolved");
        }
        ArrayList<ChestItem> copiedCargo = copyCargo(cargo);
        MinecartRuntimeSnapshot current = (MinecartRuntimeSnapshot) runtimeSnapshot();
        MinecartRuntimeSnapshot next = new MinecartRuntimeSnapshot(current.binding(), nextRevision,
                Lifecycle.LIVE, current.transform(), current.lootTable(), current.lootSeed(),
                current.provenance(), current.lootStatus(), current.lootDefinitionFingerprint(),
                current.lootResultFingerprint(), current.lootResolution(), copiedCargo);
        requireGeneratedMutationCapability(GeneratedStructureEntityFacts.Kind.CHEST_MINECART,
                GeneratedEntityMutationOperation.CHEST_MINECART_CARGO, capability, next);
        minecartCargo = copiedCargo;
        revision = nextRevision;
    }

    private void requireGeneratedMutationCapability(
            GeneratedStructureEntityFacts.Kind expectedKind,
            GeneratedEntityMutationOperation expectedOperation,
            GeneratedEntityMutationCapability capability, RuntimeSnapshot next) {
        GeneratedStructureEntityMutationCoordinator.requireGeneratedEntityMutationCapability(
                this, capability, expectedKind, expectedOperation, next);
    }

    private void rejectUnauthenticatedGenericMutation() {
        throw new IllegalStateException(
                "generic generated entity mutation requires a coordinator capability");
    }

    public Transform transform() {
        return new Transform(Double.longBitsToDouble(xBits), Double.longBitsToDouble(yBits),
                Double.longBitsToDouble(zBits), Float.intBitsToFloat(yawBits),
                Float.intBitsToFloat(pitchBits), Double.longBitsToDouble(velocityXBits),
                Double.longBitsToDouble(velocityYBits), Double.longBitsToDouble(velocityZBits));
    }

    public float[] armorPoseHead() { return decodePose(armorPoseHeadBits); }
    public float[] armorPoseBody() { return decodePose(armorPoseBodyBits); }
    public float armorHealth() {
        requireKind(GeneratedStructureEntityFacts.Kind.ARMOR_STAND);
        return Float.intBitsToFloat(armorHealthBits);
    }

    /** Returns detached sparse filled slots; absent indices are the other exact empty slots. */
    public List<ChestItem> getMinecartCargo() {
        requireKind(GeneratedStructureEntityFacts.Kind.CHEST_MINECART);
        return List.copyOf(copyCargo(minecartCargo));
    }

    public int minecartCargoSlotCount() {
        requireKind(GeneratedStructureEntityFacts.Kind.CHEST_MINECART);
        return CARGO_SLOTS;
    }

    public byte[] getMinecartLootResolution() {
        return minecartLootResolution == null ? null : minecartLootResolution.clone();
    }

    /** Detached immutable projection suitable for publication outside the JPA session. */
    public RuntimeSnapshot runtimeSnapshot() {
        requireValidDurableState();
        RuntimeBinding binding = new RuntimeBinding(worldId, chunkX, chunkZ,
                authoritativeEntityId, kind,
                laneInstallationIdentity, installationSourceFingerprint, encounterOrdinal,
                sourceRowFingerprint);
        return switch (kind) {
            case ARMOR_STAND -> new ArmorStandRuntimeSnapshot(binding, revision, lifecycle,
                    transform(), armorPoseHead(), armorPoseBody(), armorEquipmentSlot,
                    armorEquipmentItem, armorShowArms, armorSmall, armorNoBasePlate,
                    armorInvisible, armorInvulnerable, armorDisabledSlots, armorHealth());
            case CUSHION -> new CushionRuntimeSnapshot(binding, revision, lifecycle, transform(),
                    cushionColor, cushionBlockX, cushionBlockY, cushionBlockZ,
                    cushionInvulnerable, cushionRider, cushionCustomName);
            case CHEST_MINECART -> new MinecartRuntimeSnapshot(binding, revision, lifecycle,
                    transform(), minecartLootTable, minecartLootSeed, minecartProvenance,
                    minecartLootStatus, minecartLootDefinitionFingerprint,
                    minecartLootResultFingerprint, minecartLootResolution, minecartCargo);
        };
    }

    @PostLoad
    private void validateAfterLoad() {
        requireValidDurableState();
    }

    /** Fail-closed validator used by post-load and every locked/recovery repository read. */
    public WorldGeneratedStructureEntityState requireValidDurableState() {
        if (worldId <= 0L || authoritativeEntityId <= 0L || encounterOrdinal < 0
                || revision < 0L || revision == Long.MAX_VALUE || kind == null
                || lifecycle == null || minecartCargo == null
                || (lifecycle == Lifecycle.DEAD && revision == 0L)) {
            throw new IllegalStateException("invalid generated entity durable envelope");
        }
        text(laneInstallationIdentity, 255, "installation identity");
        fingerprint(installationSourceFingerprint, "installation source fingerprint");
        fingerprint(sourceRowFingerprint, "source row fingerprint");
        if (!fingerprint(durableBindingFingerprint, "durable binding fingerprint")
                .equals(computeDurableBindingFingerprint())) {
            throw new IllegalStateException("generated entity durable binding fingerprint drift");
        }
        validateTransform(transform());
        validateTerminalDropState();
        switch (kind) {
            case ARMOR_STAND -> validateArmorState();
            case CUSHION -> validateCushionState();
            case CHEST_MINECART -> validateMinecartState();
        }
        return this;
    }

    private void validateArmorState() {
        float[] head = decodePose(armorPoseHeadBits);
        float[] body = decodePose(armorPoseBodyBits);
        if (armorEquipmentSlot == null || armorEquipmentItem == null
                || armorShowArms == null || armorSmall == null || armorNoBasePlate == null
                || armorInvisible == null || armorInvulnerable == null
                || armorDisabledSlots == null || armorHealthBits == null
                || !validArmorTuple(head, body, armorEquipmentSlot, armorEquipmentItem)
                || armorShowArms || armorSmall || armorNoBasePlate || armorInvisible
                || armorInvulnerable || armorDisabledSlots != 0
                || cushionColor != null || cushionBlockX != null || cushionBlockY != null
                || cushionBlockZ != null || cushionInvulnerable != null || cushionRider != null
                || cushionCustomName != null || hasMinecartColumns() || !minecartCargo.isEmpty()) {
            throw new IllegalStateException("invalid durable armor stand state");
        }
        float health = Float.intBitsToFloat(armorHealthBits);
        if (!Float.isFinite(health) || Float.floatToRawIntBits(health)
                == Float.floatToRawIntBits(-0.0f)
                || (lifecycle == Lifecycle.LIVE ? health <= 0.0f || health > 20.0f
                        : health != 0.0f)) {
            throw new IllegalStateException("armor stand lifecycle/health conflict");
        }
    }

    private void validateCushionState() {
        Transform value = transform();
        if (!"lime".equals(cushionColor) || cushionBlockX == null || cushionBlockY == null
                || cushionBlockZ == null || !Boolean.FALSE.equals(cushionInvulnerable)
                || armorPoseHeadBits != null || armorPoseBodyBits != null
                || armorEquipmentSlot != null || armorEquipmentItem != null
                || armorShowArms != null || armorSmall != null || armorNoBasePlate != null
                || armorInvisible != null || armorInvulnerable != null
                || armorDisabledSlots != null || armorHealthBits != null
                || hasMinecartColumns() || !minecartCargo.isEmpty()
                || value.x() != cushionBlockX + 0.5d
                || value.y() != cushionBlockY + 0.9375d
                || value.z() != cushionBlockZ + 0.5d || value.pitch() != 0.0f
                || value.velocityX() != 0.0d || value.velocityY() != 0.0d
                || value.velocityZ() != 0.0d
                || !(value.yaw() == 0.0f || value.yaw() == 90.0f
                        || value.yaw() == 180.0f || value.yaw() == 270.0f)
                || lifecycle == Lifecycle.DEAD && cushionRider != null) {
            throw new IllegalStateException("invalid durable cushion state");
        }
        String rider = optionalText(cushionRider, 12, "cushion rider");
        if (rider != null && !rider.matches("[A-Za-z0-9_]{2,12}")) {
            throw new IllegalStateException("invalid durable cushion rider");
        }
        optionalText(cushionCustomName, 64, "cushion custom name");
    }

    private void validateTerminalDropState() {
        boolean any = hasTerminalDropColumns();
        boolean all = terminalGroundMutationId != null && terminalGroundEntityId != null
                && terminalGroundCommandFingerprint != null;
        if (any != all || lifecycle == Lifecycle.LIVE && any
                || kind != GeneratedStructureEntityFacts.Kind.CUSHION && any) {
            throw new IllegalStateException("generated entity terminal drop lifecycle conflict");
        }
        if (kind == GeneratedStructureEntityFacts.Kind.CUSHION && lifecycle == Lifecycle.DEAD) {
            if (!all || terminalGroundMutationId <= 0L
                    || terminalGroundMutationId == Long.MAX_VALUE
                    || terminalGroundEntityId <= 0L || terminalGroundEntityId == Long.MAX_VALUE
                    || authoritativeEntityId > (Long.MAX_VALUE - 7L) / 8L + 1L
                    || terminalGroundMutationId != (authoritativeEntityId - 1L) * 8L + 7L) {
                throw new IllegalStateException(
                        "cushion terminal settlement has no authenticated commit binding");
            }
            fingerprint(terminalGroundCommandFingerprint,
                    "terminal ground command fingerprint");
        }
    }

    private boolean hasTerminalDropColumns() {
        return terminalGroundMutationId != null || terminalGroundEntityId != null
                || terminalGroundCommandFingerprint != null;
    }

    private void validateMinecartState() {
        if (lifecycle == Lifecycle.DEAD) {
            throw new IllegalStateException(
                    "chest minecart terminalization requires a cargo ground-destruction receipt handoff");
        }
        if (!"minecraft:chests/abandoned_mineshaft".equals(minecartLootTable)
                || minecartLootSeed == null || !"CME263E1".equals(minecartProvenance)
                || minecartLootStatus == null || armorPoseHeadBits != null
                || armorPoseBodyBits != null || armorEquipmentSlot != null
                || armorEquipmentItem != null || armorShowArms != null || armorSmall != null
                || armorNoBasePlate != null || armorInvisible != null
                || armorInvulnerable != null || armorDisabledSlots != null
                || armorHealthBits != null || cushionColor != null || cushionBlockX != null
                || cushionBlockY != null || cushionBlockZ != null
                || cushionInvulnerable != null || cushionRider != null
                || cushionCustomName != null) {
            throw new IllegalStateException("invalid durable chest minecart state");
        }
        copyCargo(minecartCargo);
        if (minecartLootStatus == LootStatus.UNOPENED) {
            if (minecartLootDefinitionFingerprint != null || minecartLootResultFingerprint != null
                    || minecartLootResolution != null || !minecartCargo.isEmpty()) {
                throw new IllegalStateException("unopened chest minecart has resolved state");
            }
            return;
        }
        String definition = fingerprint(minecartLootDefinitionFingerprint,
                "loot definition fingerprint");
        fingerprint(minecartLootResultFingerprint, "loot result fingerprint");
        if (minecartLootResolution == null) {
            throw new IllegalStateException("resolved chest minecart has no canonical payload");
        }
        CanonicalLootStoredResolution resolution = CanonicalLootStoredResolution.decode(
                minecartLootResolution.clone());
        if (resolution.slots().size() != CARGO_SLOTS
                || !Arrays.equals(resolution.encode(), minecartLootResolution)
                || !resolution.fingerprint(definition).equals(minecartLootResultFingerprint)) {
            throw new IllegalStateException("resolved chest minecart receipt drift");
        }
    }

    /** Exact reusable gameplay projection of the canonical symbolic 27-slot result. */
    public static List<ChestItem> projectCanonicalMinecartCargo(
            CanonicalLootStoredResolution resolution) {
        Objects.requireNonNull(resolution, "canonical chest minecart loot resolution");
        if (resolution.slots().size() != CARGO_SLOTS) {
            throw new IllegalArgumentException("chest minecart loot must have 27 slots");
        }
        return List.copyOf(copyCargo(
                ChestPersistenceService.projectCanonicalLootItems(resolution)));
    }

    private static boolean sameCargo(List<ChestItem> left, List<ChestItem> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            ChestItem a = left.get(index);
            ChestItem b = right.get(index);
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

    private boolean hasMinecartColumns() {
        return minecartLootTable != null || minecartLootSeed != null
                || minecartProvenance != null || minecartLootStatus != null
                || minecartLootDefinitionFingerprint != null
                || minecartLootResultFingerprint != null || minecartLootResolution != null;
    }

    private static boolean validArmorTuple(float[] head, float[] body, String slot, String item) {
        int[] headBits = rawBits(head);
        int[] bodyBits = rawBits(body);
        return Mc263VillageTemplateEntityFacts.pinned().entriesInOfficialOrder().stream()
                .filter(entry -> "minecraft:armor_stand".equals(entry.entityKey()))
                .map(Mc263VillageTemplateEntityFacts.Entry::facts)
                .anyMatch(facts -> Arrays.equals(headBits, facts.poseHeadBits())
                        && Arrays.equals(bodyBits, facts.poseBodyBits())
                        && slot.equals(facts.armorStandEquipmentSlot())
                        && (item.equals(facts.armorStandEquipmentItem())
                                || item.equals("minecraft:air")));
    }

    private static int[] rawBits(float[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = Float.floatToRawIntBits(values[index]);
        }
        return result;
    }

    private static void authenticateSource(long expectedWorldId, int expectedChunkX,
            int expectedChunkZ, String expectedInstallationIdentity,
            WorldStructureEntity source, StructureEntityAggregate aggregate) {
        Objects.requireNonNull(source, "generated structure entity source");
        Objects.requireNonNull(aggregate, "generated structure entity aggregate");
        String identity = text(expectedInstallationIdentity, 255, "expected installation identity");
        if (expectedWorldId <= 0L || !aggregate.installed()
                || !identity.equals(aggregate.installationIdentity())
                || source.getWorldId() != expectedWorldId || source.getChunkX() != expectedChunkX
                || source.getChunkZ() != expectedChunkZ
                || !identity.equals(source.getLaneInstallationIdentity())
                || source.getEncounterOrdinal() < 0
                || source.getEncounterOrdinal() >= aggregate.plannedEntities().size()) {
            throw new IllegalStateException("generated entity source is outside aggregate binding");
        }
        StructureEntityAggregate.PlannedEntity planned = aggregate.plannedEntities()
                .get(source.getEncounterOrdinal());
        if (!source.matches(expectedWorldId, expectedChunkX, expectedChunkZ, identity,
                aggregate.sourceFingerprint(), planned)) {
            throw new IllegalStateException("generated entity source aggregate proof mismatch");
        }
    }

    private void validateTransition(long expectedRevision, long nextRevision) {
        if (lifecycle != Lifecycle.LIVE) {
            throw new IllegalStateException("generated entity tombstone is immutable");
        }
        if (hasTerminalDropColumns()) {
            throw new IllegalStateException("live generated entity has terminal drop receipt");
        }
        if (expectedRevision != revision || expectedRevision == Long.MAX_VALUE
                || nextRevision != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalStateException("generated entity revision CAS failed");
        }
    }

    private void requireKind(GeneratedStructureEntityFacts.Kind expected) {
        if (kind != expected) throw new IllegalStateException("generated entity kind mismatch");
    }

    private void setTransform(Transform value) {
        validateTransform(value);
        xBits = Double.doubleToRawLongBits(value.x());
        yBits = Double.doubleToRawLongBits(value.y());
        zBits = Double.doubleToRawLongBits(value.z());
        yawBits = Float.floatToRawIntBits(value.yaw());
        pitchBits = Float.floatToRawIntBits(value.pitch());
        velocityXBits = Double.doubleToRawLongBits(value.velocityX());
        velocityYBits = Double.doubleToRawLongBits(value.velocityY());
        velocityZBits = Double.doubleToRawLongBits(value.velocityZ());
    }

    private static void validateTransform(Transform value) {
        Objects.requireNonNull(value, "generated entity transform");
        if (!Double.isFinite(value.x()) || !Double.isFinite(value.y())
                || !Double.isFinite(value.z()) || !Float.isFinite(value.yaw())
                || !Float.isFinite(value.pitch()) || !Double.isFinite(value.velocityX())
                || !Double.isFinite(value.velocityY()) || !Double.isFinite(value.velocityZ())
                || !MovementLimits.withinWorldBounds(value.x(), value.y(), value.z())
                || value.pitch() < -90.0f || value.pitch() > 90.0f) {
            throw new IllegalArgumentException("generated entity transform outside authority bounds");
        }
    }

    private static byte[] encodePose(float[] pose) {
        if (pose == null || pose.length != 3) throw new IllegalArgumentException("invalid pose");
        ByteBuffer bytes = ByteBuffer.allocate(12);
        for (float value : pose) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("non-finite pose");
            bytes.putInt(Float.floatToRawIntBits(value));
        }
        return bytes.array();
    }

    private static float[] decodePose(byte[] payload) {
        if (payload == null || payload.length != 12) throw new IllegalStateException("invalid pose");
        ByteBuffer bytes = ByteBuffer.wrap(payload.clone());
        return new float[] {Float.intBitsToFloat(bytes.getInt()),
                Float.intBitsToFloat(bytes.getInt()), Float.intBitsToFloat(bytes.getInt())};
    }

    private static ArrayList<ChestItem> copyCargo(List<ChestItem> source) {
        Objects.requireNonNull(source, "minecart cargo");
        ArrayList<ChestItem> copy = new ArrayList<>(source.size());
        HashSet<Integer> occupied = new HashSet<>();
        for (ChestItem item : source) {
            Objects.requireNonNull(item, "minecart cargo item");
            int slot = item.getSlot();
            short type = item.getItemType();
            int count = item.getItemCount();
            if (slot < 0 || slot >= CARGO_SLOTS || !occupied.add(slot)
                    || type == PlayerInventory.EMPTY || !PlayerInventory.isRegisteredItemType(type)
                    || count <= 0 || count > PlayerInventory.stackMax(type)) {
                throw new IllegalArgumentException("invalid chest minecart cargo slot");
            }
            new PlayerInventory.StackSnapshot(type, count,
                    item.getDurability() == null ? 0 : item.getDurability(),
                    item.enchantmentsOrZero(), item.mapIdOrZero(), item.shulkerIdOrZero(),
                    item.getBucketMobData(), item.getItemComponentData());
            copy.add(new ChestItem(slot, type, count, item.getDurability(),
                    item.getEnchantments(), item.getMapId(), item.getShulkerId(),
                    item.getBucketMobData(), item.getItemComponentData()));
        }
        copy.sort(Comparator.comparingInt(ChestItem::getSlot));
        return copy;
    }

    private static String text(String value, int maximum, String label) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return value;
    }

    private static String optionalText(String value, int maximum, String label) {
        if (value == null) return null;
        if (value.isEmpty() || value.length() > maximum) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return value;
    }

    private static String fingerprint(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return value;
    }

    private String computeDurableBindingFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(BINDING_DOMAIN);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(worldId).array());
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(chunkX).array());
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(chunkZ).array());
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(authoritativeEntityId).array());
            putDigestText(digest, kind == null ? "" : kind.name());
            putDigestText(digest, laneInstallationIdentity);
            putDigestText(digest, installationSourceFingerprint);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encounterOrdinal).array());
            putDigestText(digest, sourceRowFingerprint);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void putDigestText(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "generated entity binding text")
                .getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
