package com.gameexpert.tnt.dto;

import com.gameexpert.engine.BrewingInventory;
import com.gameexpert.engine.CampfireInventory;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.EnchantingInventory;
import com.gameexpert.engine.FurnaceInventory;
import com.gameexpert.engine.FurnaceRules;
import com.gameexpert.engine.FurnaceVariant;
import com.gameexpert.engine.inventory.BrewingRules;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.LecternRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.shelf.ShelfRules;
import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, canonically ordered durable plan for one complete explosion settlement. */
public final class ExplosionSettlementCommand {
    private static final int SHA256_BYTES = 32;

    /** Append-only source protocol registry; OTHER is the retired protocol-2 tombstone. */
    public enum SourceKind {
        PRIMED_TNT(0, null),
        CREEPER(1, MobType.CREEPER),
        OTHER(2, null),
        SULFUR_CUBE(3, MobType.SULFUR_CUBE);

        private final int protocolId;
        private final MobType mobType;

        SourceKind(int protocolId, MobType mobType) {
            this.protocolId = protocolId;
            this.mobType = mobType;
        }

        public int protocolId() { return protocolId; }
        public MobType mobType() { return mobType; }

        public static SourceKind fromProtocolId(int protocolId) {
            for (SourceKind kind : values()) {
                if (kind.protocolId == protocolId) return kind;
            }
            throw new IllegalArgumentException("unknown explosion source protocol ID");
        }
    }

    public enum ContainerKind {
        CHEST(0, CanonicalLootContainerKind.CHEST),
        SHELF(1, null),
        SHULKER(2, null),
        FURNACE(3, null),
        BREWING_STAND(4, null),
        CAMPFIRE(5, null),
        LECTERN(6, null),
        ENCHANTING_TABLE(7, null),
        BARREL(8, CanonicalLootContainerKind.BARREL),
        DISPENSER(9, CanonicalLootContainerKind.DISPENSER);

        private final int protocolId;
        private final CanonicalLootContainerKind canonicalLootKind;

        ContainerKind(int protocolId, CanonicalLootContainerKind canonicalLootKind) {
            this.protocolId = protocolId;
            this.canonicalLootKind = canonicalLootKind;
        }

        public int protocolId() { return protocolId; }

        public static ContainerKind fromProtocolId(int protocolId) {
            for (ContainerKind kind : values()) {
                if (kind.protocolId == protocolId) return kind;
            }
            throw new IllegalArgumentException("unknown explosion container protocol ID");
        }

        private int canonicalLootSlots() {
            if (canonicalLootKind == null) {
                throw new IllegalStateException("container has no canonical loot registry entry");
            }
            return canonicalLootKind.slots();
        }
    }

    /** Append-only provenance state registry; no caller-provided overlay boolean exists. */
    public enum OverlayState {
        CANONICAL_BASE(0), DURABLE_OVERLAY(1);

        private final int protocolId;

        OverlayState(int protocolId) { this.protocolId = protocolId; }
        public int protocolId() { return protocolId; }

        public static OverlayState fromProtocolId(int protocolId) {
            for (OverlayState state : values()) {
                if (state.protocolId == protocolId) return state;
            }
            throw new IllegalArgumentException("unknown block provenance overlay-state ID");
        }
    }

    /**
     * Immutable authenticated provenance of the exact authority cell a transition consumes.
     * Canonical bytes end with their SHA-256, so malformed, non-canonical, and tampered carriers
     * fail before a transition can enter a settlement command.
     */
    /** Parses only registered identities; settlement also checks the world's complete tuple. */
    public static boolean isSupportedProductIdentity(String identity) {
        try {
            WorldGenerationProfiles.requireSupportedBaselineId(identity);
            return true;
        } catch (IllegalStateException invalid) {
            return false;
        }
    }

    public static final class BlockProvenance {
        private static final int MAGIC = 0x47455850; // GEXP
        private static final int SCHEMA_VERSION = 1;

        private final long worldId;
        private final int x;
        private final int y;
        private final int z;
        private final String canonicalProductIdentity;
        private final String canonicalReceiptIdentity;
        private final long canonicalRevision;
        private final OverlayState overlayState;
        private final byte[] canonicalBytes;
        private final String canonicalDigest;

        public BlockProvenance(long worldId, int x, int y, int z,
                String canonicalProductIdentity, String canonicalReceiptIdentity,
                long canonicalRevision, OverlayState overlayState) {
            if (worldId <= 0 || worldId == Long.MAX_VALUE
                    || x < -MovementLimits.MAX_HORIZONTAL_COORDINATE
                    || x > MovementLimits.MAX_HORIZONTAL_COORDINATE
                    || z < -MovementLimits.MAX_HORIZONTAL_COORDINATE
                    || z > MovementLimits.MAX_HORIZONTAL_COORDINATE
                    || y < Blocks.MIN_Y || y > Blocks.MAX_Y
                    || !isSupportedProductIdentity(canonicalProductIdentity)
                    || !isSha256(canonicalReceiptIdentity)
                    || canonicalRevision < 0 || canonicalRevision == Long.MAX_VALUE
                    || overlayState == null) {
                throw new IllegalArgumentException("exact canonical block provenance is required");
            }
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.canonicalProductIdentity = canonicalProductIdentity;
            this.canonicalReceiptIdentity = canonicalReceiptIdentity;
            this.canonicalRevision = canonicalRevision;
            this.overlayState = overlayState;
            byte[] payload = encodePayload();
            byte[] digest = sha256(payload);
            this.canonicalBytes = Arrays.copyOf(payload, payload.length + digest.length);
            System.arraycopy(digest, 0, canonicalBytes, payload.length, digest.length);
            this.canonicalDigest = HexFormat.of().formatHex(digest);
        }

        public long worldId() { return worldId; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public String canonicalProductIdentity() { return canonicalProductIdentity; }
        public String canonicalReceiptIdentity() { return canonicalReceiptIdentity; }
        public long canonicalRevision() { return canonicalRevision; }
        public OverlayState overlayState() { return overlayState; }
        public byte[] canonicalBytes() { return canonicalBytes.clone(); }
        public String canonicalDigest() { return canonicalDigest; }

        public static BlockProvenance parseCanonical(byte[] encoded) {
            if (encoded == null || encoded.length <= SHA256_BYTES) {
                throw new IllegalArgumentException("canonical block provenance bytes are required");
            }
            byte[] detached = encoded.clone();
            byte[] payload = Arrays.copyOf(detached, detached.length - SHA256_BYTES);
            byte[] suppliedDigest = Arrays.copyOfRange(
                    detached, detached.length - SHA256_BYTES, detached.length);
            if (!MessageDigest.isEqual(sha256(payload), suppliedDigest)) {
                throw new IllegalArgumentException("canonical block provenance digest mismatch");
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
                if (input.readInt() != MAGIC || input.readInt() != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported block provenance schema");
                }
                long worldId = input.readLong();
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                int productLength = input.readInt();
                if (productLength <= 0 || productLength > 160) {
                    throw new IllegalArgumentException("canonical product identity is invalid");
                }
                byte[] product = input.readNBytes(productLength);
                byte[] receipt = input.readNBytes(SHA256_BYTES);
                if (product.length != productLength || receipt.length != SHA256_BYTES) {
                    throw new IllegalArgumentException("canonical product/receipt is truncated");
                }
                long revision = input.readLong();
                OverlayState state = OverlayState.fromProtocolId(input.readInt());
                if (input.available() != 0) {
                    throw new IllegalArgumentException("block provenance has trailing bytes");
                }
                BlockProvenance parsed = new BlockProvenance(worldId, x, y, z,
                        new String(product, StandardCharsets.UTF_8),
                        HexFormat.of().formatHex(receipt), revision, state);
                if (!MessageDigest.isEqual(parsed.canonicalBytes, detached)) {
                    throw new IllegalArgumentException("block provenance is not canonical");
                }
                return parsed;
            } catch (IOException malformed) {
                throw new IllegalArgumentException("malformed block provenance", malformed);
            }
        }

        private byte[] encodePayload() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream output = new DataOutputStream(bytes)) {
                    output.writeInt(MAGIC);
                    output.writeInt(SCHEMA_VERSION);
                    output.writeLong(worldId);
                    output.writeInt(x);
                    output.writeInt(y);
                    output.writeInt(z);
                    byte[] product = canonicalProductIdentity.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(product.length);
                    output.write(product);
                    output.write(HexFormat.of().parseHex(canonicalReceiptIdentity));
                    output.writeLong(canonicalRevision);
                    output.writeInt(overlayState.protocolId());
                }
                return bytes.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException("in-memory provenance encoding failed", impossible);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BlockProvenance value
                    && worldId == value.worldId && x == value.x && y == value.y && z == value.z
                    && canonicalRevision == value.canonicalRevision
                    && canonicalProductIdentity.equals(value.canonicalProductIdentity)
                    && canonicalReceiptIdentity.equals(value.canonicalReceiptIdentity)
                    && overlayState == value.overlayState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, x, y, z, canonicalProductIdentity,
                    canonicalReceiptIdentity, canonicalRevision, overlayState);
        }
    }

    /** Immutable authenticated bytes of the existing durable mob snapshot authority. */
    public static final class MobSourceSnapshot {
        private final long mobId;
        private final MobType mobType;
        private final byte[] canonicalState;
        private final String canonicalStateHash;

        public MobSourceSnapshot(long mobId, MobType mobType,
                byte[] canonicalState, String canonicalStateHash) {
            if (mobId <= 0 || mobId == Long.MAX_VALUE || mobType == null
                    || canonicalState == null || canonicalState.length == 0
                    || !isSha256(canonicalStateHash)) {
                throw new IllegalArgumentException("authenticated durable mob snapshot is required");
            }
            byte[] detachedState = canonicalState.clone();
            if (!MessageDigest.isEqual(
                    sha256(detachedState), HexFormat.of().parseHex(canonicalStateHash))) {
                throw new IllegalArgumentException("authenticated durable mob snapshot is required");
            }
            this.mobId = mobId;
            this.mobType = mobType;
            this.canonicalState = detachedState;
            this.canonicalStateHash = canonicalStateHash;
        }

        public long mobId() { return mobId; }
        public MobType mobType() { return mobType; }
        public byte[] canonicalState() { return canonicalState.clone(); }
        public String canonicalStateHash() { return canonicalStateHash; }

        @Override
        public boolean equals(Object other) {
            return other instanceof MobSourceSnapshot value
                    && mobId == value.mobId && mobType == value.mobType
                    && canonicalStateHash.equals(value.canonicalStateHash)
                    && Arrays.equals(canonicalState, value.canonicalState);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(mobId, mobType, canonicalStateHash)
                    + Arrays.hashCode(canonicalState);
        }
    }

    /** Exact expected source snapshot whose successful settlement always retires that source. */
    public static final class SourceRetirement {
        private static final int MAGIC = 0x47455853; // GEXS
        private static final int SCHEMA_VERSION = 1;

        private final long worldId;
        private final SourceKind kind;
        private final long sourceEntityId;
        private final PrimedTntSnapshot primedTntSnapshot;
        private final MobSourceSnapshot mobSnapshot;
        private final byte[] canonicalBytes;
        private final String fingerprint;

        private SourceRetirement(long worldId, SourceKind kind,
                PrimedTntSnapshot primedTntSnapshot, MobSourceSnapshot mobSnapshot) {
            if (worldId <= 0 || worldId == Long.MAX_VALUE || kind == null
                    || kind == SourceKind.OTHER) {
                throw new IllegalArgumentException("exact explosion source retirement is required");
            }
            if (kind == SourceKind.PRIMED_TNT) {
                if (primedTntSnapshot == null || mobSnapshot != null) {
                    throw new IllegalArgumentException("primed TNT source needs its exact snapshot");
                }
                this.sourceEntityId = primedTntSnapshot.tntId();
            } else {
                if (primedTntSnapshot != null || mobSnapshot == null
                        || mobSnapshot.mobType() != kind.mobType()) {
                    throw new IllegalArgumentException("explosive mob source kind is mismatched");
                }
                this.sourceEntityId = mobSnapshot.mobId();
            }
            this.worldId = worldId;
            this.kind = kind;
            this.primedTntSnapshot = primedTntSnapshot;
            this.mobSnapshot = mobSnapshot;
            if (sourceEntityId == Long.MAX_VALUE) {
                throw new IllegalArgumentException("explosion source identity is exhausted");
            }
            byte[] payload = encodePayload();
            byte[] digest = sha256(payload);
            this.canonicalBytes = Arrays.copyOf(payload, payload.length + digest.length);
            System.arraycopy(digest, 0, canonicalBytes, payload.length, digest.length);
            this.fingerprint = HexFormat.of().formatHex(digest);
        }

        public static SourceRetirement primedTnt(long worldId, PrimedTntSnapshot expected) {
            return new SourceRetirement(worldId, SourceKind.PRIMED_TNT, expected, null);
        }

        public static SourceRetirement explosiveMob(
                long worldId, SourceKind kind, MobSourceSnapshot expected) {
            return new SourceRetirement(worldId, kind, null, expected);
        }

        public long worldId() { return worldId; }
        public SourceKind kind() { return kind; }
        public long sourceEntityId() { return sourceEntityId; }
        public PrimedTntSnapshot primedTntSnapshot() { return primedTntSnapshot; }
        public MobSourceSnapshot mobSnapshot() { return mobSnapshot; }
        public byte[] canonicalBytes() { return canonicalBytes.clone(); }
        public String fingerprint() { return fingerprint; }

        public static SourceRetirement parseCanonical(byte[] encoded) {
            if (encoded == null || encoded.length <= SHA256_BYTES) {
                throw new IllegalArgumentException("canonical explosion source bytes are required");
            }
            byte[] detached = encoded.clone();
            byte[] payload = Arrays.copyOf(detached, detached.length - SHA256_BYTES);
            byte[] suppliedDigest = Arrays.copyOfRange(
                    detached, detached.length - SHA256_BYTES, detached.length);
            if (!MessageDigest.isEqual(sha256(payload), suppliedDigest)) {
                throw new IllegalArgumentException("canonical explosion source digest mismatch");
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
                if (input.readInt() != MAGIC || input.readInt() != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported explosion source schema");
                }
                SourceKind kind = SourceKind.fromProtocolId(input.readInt());
                long worldId = input.readLong();
                long sourceEntityId = input.readLong();
                SourceRetirement parsed;
                if (kind == SourceKind.PRIMED_TNT) {
                    PrimedTntSnapshot snapshot = new PrimedTntSnapshot(sourceEntityId,
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()), input.readInt());
                    parsed = primedTnt(worldId, snapshot);
                } else if (kind == SourceKind.CREEPER || kind == SourceKind.SULFUR_CUBE) {
                    MobType mobType = MobType.fromStableId(input.readInt());
                    int stateLength = input.readInt();
                    if (stateLength <= 0 || stateLength > input.available() - SHA256_BYTES) {
                        throw new IllegalArgumentException("durable mob source state is truncated");
                    }
                    byte[] state = input.readNBytes(stateLength);
                    byte[] stateHash = input.readNBytes(SHA256_BYTES);
                    if (state.length != stateLength || stateHash.length != SHA256_BYTES) {
                        throw new IllegalArgumentException("durable mob source state is truncated");
                    }
                    parsed = explosiveMob(worldId, kind, new MobSourceSnapshot(
                            sourceEntityId, mobType, state, HexFormat.of().formatHex(stateHash)));
                } else {
                    throw new IllegalArgumentException("retired explosion source kind is forbidden");
                }
                if (input.available() != 0
                        || !MessageDigest.isEqual(parsed.canonicalBytes, detached)) {
                    throw new IllegalArgumentException("explosion source is not canonical");
                }
                return parsed;
            } catch (IOException malformed) {
                throw new IllegalArgumentException("malformed explosion source", malformed);
            }
        }

        private byte[] encodePayload() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream output = new DataOutputStream(bytes)) {
                    output.writeInt(MAGIC);
                    output.writeInt(SCHEMA_VERSION);
                    output.writeInt(kind.protocolId());
                    output.writeLong(worldId);
                    output.writeLong(sourceEntityId);
                    if (primedTntSnapshot != null) {
                        output.writeLong(Double.doubleToLongBits(primedTntSnapshot.x()));
                        output.writeLong(Double.doubleToLongBits(primedTntSnapshot.y()));
                        output.writeLong(Double.doubleToLongBits(primedTntSnapshot.z()));
                        output.writeLong(Double.doubleToLongBits(primedTntSnapshot.velocityX()));
                        output.writeLong(Double.doubleToLongBits(primedTntSnapshot.velocityY()));
                        output.writeLong(Double.doubleToLongBits(primedTntSnapshot.velocityZ()));
                        output.writeInt(primedTntSnapshot.fuse());
                    } else {
                        output.writeInt(mobSnapshot.mobType().stableId());
                        byte[] state = mobSnapshot.canonicalState;
                        output.writeInt(state.length);
                        output.write(state);
                        output.write(HexFormat.of().parseHex(mobSnapshot.canonicalStateHash()));
                    }
                }
                return bytes.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException("in-memory source encoding failed", impossible);
            }
        }

        private void updateDigest(MessageDigest digest) {
            putBytes(digest, canonicalBytes);
            putString(digest, fingerprint);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SourceRetirement value
                    && worldId == value.worldId && kind == value.kind
                    && sourceEntityId == value.sourceEntityId
                    && Objects.equals(primedTntSnapshot, value.primedTntSnapshot)
                    && Objects.equals(mobSnapshot, value.mobSnapshot)
                    && fingerprint.equals(value.fingerprint)
                    && Arrays.equals(canonicalBytes, value.canonicalBytes);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(worldId, kind, sourceEntityId, primedTntSnapshot,
                    mobSnapshot, fingerprint) + Arrays.hashCode(canonicalBytes);
        }
    }

    /** Exhaustive current-schema state carried by an exact container retirement. */
    public sealed interface ContainerState permits ChestState, FurnaceState, BrewingState,
            CampfireState, EnchantingTableState, LecternState { }

    /** Coordinate-owned chest-schema state; the retirement kind fixes its exact capacity. */
    public record ChestState(int containerSize) implements ContainerState {
        public ChestState {
            if (containerSize != ShelfRules.SLOTS
                    && containerSize != CanonicalLootContainerKind.CHEST.slots()
                    && containerSize != CanonicalLootContainerKind.DISPENSER.slots()) {
                throw new IllegalArgumentException("unsupported chest-schema capacity");
            }
        }
    }

    /** Complete scalar state persisted for one furnace row. */
    public record FurnaceState(int variantCode, int burnTicks, int burnTotalTicks,
            int cookTicks, int xpMilli) implements ContainerState {
        public FurnaceState {
            FurnaceVariant variant = furnaceVariant(variantCode);
            int maximumBurnTicks = FurnaceRules.fuelTicks((short) Blocks.COAL_BLOCK);
            if (burnTicks < 0 || burnTotalTicks < 0 || burnTicks > burnTotalTicks
                    || burnTicks == 0 && burnTotalTicks != 0
                    || burnTotalTicks > maximumBurnTicks
                    || cookTicks < 0 || cookTicks >= variant.cookTotalTicks()
                    || xpMilli < 0 || xpMilli > FurnaceInventory.MAX_XP_MILLI) {
                throw new IllegalArgumentException("invalid furnace state");
            }
        }
    }

    /** Complete scalar state persisted for one brewing-stand row. */
    public record BrewingState(int fuel, int brewTicks, short brewingIngredient)
            implements ContainerState {
        public BrewingState {
            if (fuel < 0 || fuel > BrewingRules.BREWS_PER_BLAZE_POWDER
                    || brewTicks < 0 || brewTicks > BrewingRules.BREW_TIME_TICKS
                    || brewTicks == 0 && brewingIngredient != PlayerInventory.EMPTY
                    || brewTicks > 0 && brewingIngredient == PlayerInventory.EMPTY) {
                throw new IllegalArgumentException("invalid brewing state");
            }
        }
    }

    /** Campfire scalars are carried per slot as {@link ContainerStack#progress()}. */
    public record CampfireState() implements ContainerState { }

    /** Enchanting-table persistence has no aggregate scalar beyond its two stacks. */
    public record EnchantingTableState() implements ContainerState { }

    /** Complete scalar state persisted with a lectern's single book stack. */
    public record LecternState(int page) implements ContainerState {
        public LecternState {
            if (page < 0 || page >= ItemComponentData.BookData.MAX_PAGES) {
                throw new IllegalArgumentException("invalid lectern page");
            }
        }
    }

    private static final Comparator<BlockTransition> BLOCK_ORDER = Comparator
            .comparingInt(BlockTransition::x)
            .thenComparingInt(BlockTransition::y)
            .thenComparingInt(BlockTransition::z);
    private static final Comparator<TntMutation> TNT_ORDER =
            Comparator.comparingLong(TntMutation::tntId);
    private static final Comparator<ContainerRetirement> CONTAINER_ORDER = Comparator
            .comparingInt((ContainerRetirement source) -> source.kind().protocolId())
            .thenComparingInt(ContainerRetirement::x)
            .thenComparingInt(ContainerRetirement::y)
            .thenComparingInt(ContainerRetirement::z)
            .thenComparing(ContainerRetirement::aggregateId);

    private final long explosionId;
    private final Long worldId;
    private final SourceRetirement sourceRetirement;
    private final List<BlockTransition> blockTransitions;
    private final List<TntMutation> tntMutations;
    private final List<ContainerRetirement> containerRetirements;
    private final GroundMutationCommand groundMutation;
    private final long highestReservedGroundEntityId;
    private final long highestTntId;
    private final String commandHash;

    public ExplosionSettlementCommand(long explosionId, Long worldId,
            SourceRetirement sourceRetirement, List<BlockTransition> blockTransitions,
            List<TntMutation> tntMutations,
            List<ContainerRetirement> containerRetirements,
            GroundMutationCommand groundMutation) {
        if (explosionId <= 0 || explosionId == Long.MAX_VALUE
                || worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE
                || sourceRetirement == null
                || sourceRetirement.worldId() != worldId.longValue()) {
            throw new IllegalArgumentException("complete explosion identity is required");
        }
        this.explosionId = explosionId;
        this.worldId = worldId;
        this.sourceRetirement = sourceRetirement;
        this.blockTransitions = immutableSorted(blockTransitions, BLOCK_ORDER, "block transitions");
        this.tntMutations = immutableSorted(tntMutations, TNT_ORDER, "TNT mutations");
        this.containerRetirements = immutableSorted(
                containerRetirements, CONTAINER_ORDER, "container retirements");
        this.groundMutation = groundMutation;
        validateSourceAuthority();
        validateBlocksAndContainers();
        validateTntMutations();
        validateGroundMutationAndConservation();
        this.highestReservedGroundEntityId = calculateHighestReservedGroundEntityId();
        this.highestTntId = this.tntMutations.stream()
                .mapToLong(TntMutation::tntId).max().orElse(0L);
        this.commandHash = calculateHash();
    }

    public long explosionId() { return explosionId; }
    public Long worldId() { return worldId; }
    public SourceRetirement sourceRetirement() { return sourceRetirement; }
    public SourceKind sourceKind() { return sourceRetirement.kind(); }
    public String sourceFingerprint() { return sourceRetirement.fingerprint(); }
    public List<BlockTransition> blockTransitions() { return blockTransitions; }
    public List<TntMutation> tntMutations() { return tntMutations; }
    public List<ContainerRetirement> containerRetirements() { return containerRetirements; }
    public GroundMutationCommand groundMutation() { return groundMutation; }
    public long highestReservedGroundEntityId() { return highestReservedGroundEntityId; }
    public long highestTntId() { return highestTntId; }
    public String commandHash() { return commandHash; }

    private void validateSourceAuthority() {
        if (sourceKind() == SourceKind.PRIMED_TNT) {
            TntMutation source = tntMutations.stream()
                    .filter(mutation -> mutation.tntId()
                            == sourceRetirement.sourceEntityId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "primed TNT explosion must retire its exact source"));
            if (source.expected() == null || source.committed() != null
                    || !sourceRetirement.primedTntSnapshot().equals(source.expected())) {
                throw new IllegalArgumentException(
                        "primed TNT source identity and retirement must be exact");
            }
            return;
        }
        if (sourceKind() == SourceKind.CREEPER
                || sourceKind() == SourceKind.SULFUR_CUBE) {
            if (sourceRetirement.mobSnapshot() == null
                    || sourceRetirement.mobSnapshot().mobType() != sourceKind().mobType()) {
                throw new IllegalArgumentException("explosive mob source identity is mismatched");
            }
            return;
        }
        throw new IllegalArgumentException("unsupported explosion source authority");
    }

    private void validateBlocksAndContainers() {
        Set<Position> blockPositions = new HashSet<>();
        for (BlockTransition transition : blockTransitions) {
            Position position = transition.position();
            if (!blockPositions.add(position)) {
                throw new IllegalArgumentException("duplicate explosion block coordinate");
            }
            if (transition.provenance().worldId() != worldId.longValue()
                    || transition.y() < Blocks.MIN_Y || transition.y() > Blocks.MAX_Y
                    || !Blocks.isWorldBlockId(
                            Short.toUnsignedInt(transition.expectedBlockType()))
                    || transition.expectedBlockType() == (short) Blocks.AIR
                    || transition.committedBlockType() != (short) Blocks.AIR
                    || transition.committedBlockState() != 0) {
                throw new IllegalArgumentException("explosion block transitions must destroy non-air");
            }
        }

        Set<Position> containerPositions = new HashSet<>();
        Map<String, List<ContainerRetirement>> aggregates = new HashMap<>();
        for (ContainerRetirement retirement : containerRetirements) {
            Position position = retirement.position();
            if (!containerPositions.add(position)) {
                throw new IllegalArgumentException("duplicate explosion container coordinate");
            }
            if (retirement.y() < Blocks.MIN_Y || retirement.y() > Blocks.MAX_Y
                    || !blockPositions.contains(position)) {
                throw new IllegalArgumentException("container retirement needs a destroyed block");
            }
            BlockTransition destroyed = blockTransitions.stream()
                    .filter(transition -> transition.position().equals(position))
                    .findFirst().orElseThrow();
            if (!containerKindMatchesBlock(retirement, destroyed.expectedBlockType())) {
                throw new IllegalArgumentException(
                        "container retirement kind does not match destroyed block");
            }
            String key = retirement.kind().protocolId() + ":" + retirement.aggregateId();
            aggregates.computeIfAbsent(key, ignored -> new ArrayList<>()).add(retirement);
        }
        for (List<ContainerRetirement> aggregate : aggregates.values()) {
            ContainerRetirement first = aggregate.getFirst();
            if (first.kind() == ContainerKind.CHEST) {
                int required = first.pairedChest() ? 2 : 1;
                if (aggregate.size() != required
                        || aggregate.stream().anyMatch(value ->
                                value.pairedChest() != first.pairedChest())
                        || first.pairedChest() && !areAdjacent(
                                aggregate.get(0).position(), aggregate.get(1).position())) {
                    throw new IllegalArgumentException("chest aggregate is incomplete");
                }
            } else if (aggregate.size() != 1 || first.pairedChest()) {
                throw new IllegalArgumentException("non-chest container aggregate is invalid");
            }
        }
    }

    private static boolean areAdjacent(Position first, Position second) {
        return first.y == second.y
                && Math.abs(first.x - second.x) + Math.abs(first.z - second.z) == 1;
    }

    private static boolean containerKindMatchesBlock(
            ContainerRetirement retirement, short blockType) {
        ContainerKind kind = retirement.kind();
        int id = Short.toUnsignedInt(blockType);
        return switch (kind) {
            case CHEST -> Blocks.isChestShaped(id) && id != Blocks.ENDER_CHEST;
            case SHELF -> Blocks.isShelf(id);
            case SHULKER -> Blocks.isShulkerBox(id);
            case FURNACE -> retirement.state() instanceof FurnaceState state
                    && FurnaceVariant.of(id) != null
                    && FurnaceVariant.of(id).code() == state.variantCode();
            case BREWING_STAND -> id == Blocks.BREWING_STAND;
            case CAMPFIRE -> id == Blocks.CAMPFIRE;
            case LECTERN -> id == Blocks.LECTERN;
            case ENCHANTING_TABLE -> id == Blocks.ENCHANTING_TABLE;
            case BARREL -> id == Blocks.BARREL;
            case DISPENSER -> id == Blocks.DISPENSER;
        };
    }

    private void validateTntMutations() {
        Set<Long> identities = new HashSet<>();
        for (TntMutation mutation : tntMutations) {
            if (!identities.add(mutation.tntId())) {
                throw new IllegalArgumentException("duplicate or overlapping TNT mutation");
            }
        }
    }

    private void validateGroundMutationAndConservation() {
        Map<Long, GroundItemSnapshot> groundItems = new HashMap<>();
        Map<Long, GroundXpOrbSnapshot> groundXp = new HashMap<>();
        if (groundMutation != null) {
            if (groundMutation.kind() != GroundMutationCommand.Kind.BLOCK_DROP
                    || !worldId.equals(groundMutation.worldId())
                    || groundMutation.committedPlayer() != null
                    || groundMutation.expectedPlayerRevision() != null
                    || !groundMutation.removedItemIds().isEmpty()
                    || !groundMutation.removedXpOrbIds().isEmpty()) {
                throw new IllegalArgumentException("explosion ground mutation must be an insertion-only block drop");
            }
            for (GroundItemSnapshot item : groundMutation.insertedItems()) {
                if (groundItems.put(item.entityId(), item) != null) {
                    throw new IllegalArgumentException("duplicate ground item identity");
                }
            }
            for (GroundXpOrbSnapshot orb : groundMutation.insertedXpOrbs()) {
                if (groundXp.put(orb.entityId(), orb) != null
                        || groundItems.containsKey(orb.entityId())) {
                    throw new IllegalArgumentException("duplicate ground entity identity");
                }
            }
        }

        Set<Long> claimedItems = new HashSet<>();
        Set<Long> claimedXp = new HashSet<>();
        for (ContainerRetirement retirement : containerRetirements) {
            for (ContainerDrop drop : retirement.drops()) {
                if (drop.source().isEmpty()) continue;
                if (!claimedItems.add(drop.outputGroundEntityId())) {
                    throw new IllegalArgumentException("one ground item cannot settle two sources");
                }
                GroundItemSnapshot output = groundItems.get(drop.outputGroundEntityId());
                if (output == null || !drop.source().matches(output)) {
                    throw new IllegalArgumentException("container item is not conserved into ground output");
                }
            }
            long xpTotal = 0;
            for (Long entityId : retirement.outputGroundXpEntityIds()) {
                if (!claimedXp.add(entityId) || claimedItems.contains(entityId)) {
                    throw new IllegalArgumentException("one ground XP output cannot settle two sources");
                }
                GroundXpOrbSnapshot output = groundXp.get(entityId);
                if (output == null) {
                    throw new IllegalArgumentException("container XP output is absent");
                }
                xpTotal = Math.addExact(xpTotal, output.amount());
            }
            if (xpTotal != retirement.expectedExperience()) {
                throw new IllegalArgumentException("container experience is not conserved");
            }
        }
        if (groundMutation == null && (!claimedItems.isEmpty() || !claimedXp.isEmpty())) {
            throw new IllegalArgumentException("container outputs require a ground mutation");
        }
    }

    private long calculateHighestReservedGroundEntityId() {
        long highest = 0;
        if (groundMutation == null) return highest;
        for (GroundItemSnapshot item : groundMutation.insertedItems()) {
            highest = Math.max(highest, item.entityId());
        }
        for (GroundXpOrbSnapshot orb : groundMutation.insertedXpOrbs()) {
            highest = Math.max(highest, orb.entityId());
        }
        return highest;
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putString(digest, "game-expert/explosion-settlement/v3");
            putLong(digest, explosionId);
            putLong(digest, worldId);
            sourceRetirement.updateDigest(digest);
            putInt(digest, blockTransitions.size());
            for (BlockTransition transition : blockTransitions) transition.updateDigest(digest);
            putInt(digest, tntMutations.size());
            for (TntMutation mutation : tntMutations) mutation.updateDigest(digest);
            putInt(digest, containerRetirements.size());
            for (ContainerRetirement retirement : containerRetirements) {
                retirement.updateDigest(digest);
            }
            updateGroundDigest(digest);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private void updateGroundDigest(MessageDigest digest) {
        digest.update((byte) (groundMutation == null ? 0 : 1));
        if (groundMutation == null) return;
        putLong(digest, groundMutation.mutationId());
        putString(digest, groundMutation.kind().name());
        putLong(digest, groundMutation.worldId());
        putLong(digest, groundMutation.expectedGroundRevision());
        putLong(digest, groundMutation.committedGroundRevision());
        List<GroundItemSnapshot> items = groundMutation.insertedItems().stream()
                .sorted(Comparator.comparingLong(GroundItemSnapshot::entityId)).toList();
        putInt(digest, items.size());
        for (GroundItemSnapshot item : items) {
            putLong(digest, item.entityId());
            putShort(digest, item.itemType());
            putInt(digest, item.count());
            putInt(digest, item.durability());
            putLong(digest, item.enchantments());
            putInt(digest, item.mapId());
            putInt(digest, item.shulkerId());
            putNullableString(digest, item.bucketMobData());
            putNullableString(digest, item.itemComponentData());
            putDouble(digest, item.x());
            putDouble(digest, item.y());
            putDouble(digest, item.z());
            putDouble(digest, item.velocityX());
            putDouble(digest, item.velocityY());
            putDouble(digest, item.velocityZ());
            digest.update((byte) (item.playerThrown() ? 1 : 0));
            putInt(digest, item.age());
            putInt(digest, item.pickupDelay());
            putLong(digest, item.excludedAllayId());
        }
        List<GroundXpOrbSnapshot> xp = groundMutation.insertedXpOrbs().stream()
                .sorted(Comparator.comparingLong(GroundXpOrbSnapshot::entityId)).toList();
        putInt(digest, xp.size());
        for (GroundXpOrbSnapshot orb : xp) {
            putLong(digest, orb.entityId());
            putInt(digest, orb.amount());
            putDouble(digest, orb.x());
            putDouble(digest, orb.y());
            putDouble(digest, orb.z());
            putDouble(digest, orb.velocityX());
            putDouble(digest, orb.velocityY());
            putDouble(digest, orb.velocityZ());
            putInt(digest, orb.age());
        }
    }

    private static <T> List<T> immutableSorted(
            List<T> values, Comparator<? super T> order, String label) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " are required");
        }
        ArrayList<T> copy = new ArrayList<>(values);
        copy.sort(order);
        return List.copyOf(copy);
    }

    private static boolean isSha256(String value) {
        return value != null && value.length() == SHA256_BYTES * 2
                && value.matches("[0-9a-f]+")
                && !value.equals("0".repeat(SHA256_BYTES * 2));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void putShort(MessageDigest digest, short value) {
        digest.update(ByteBuffer.allocate(Short.BYTES).putShort(value).array());
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void putDouble(MessageDigest digest, double value) {
        putLong(digest, Double.doubleToLongBits(value));
    }

    private static void putString(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        putInt(digest, encoded.length);
        digest.update(encoded);
    }

    private static void putBytes(MessageDigest digest, byte[] value) {
        putInt(digest, value.length);
        digest.update(value);
    }

    private static void putNullableString(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
        } else {
            digest.update((byte) 1);
            putString(digest, value);
        }
    }

    private static void putSnapshot(MessageDigest digest, PrimedTntSnapshot snapshot) {
        digest.update((byte) (snapshot == null ? 0 : 1));
        if (snapshot == null) return;
        putLong(digest, snapshot.tntId());
        putDouble(digest, snapshot.x());
        putDouble(digest, snapshot.y());
        putDouble(digest, snapshot.z());
        putDouble(digest, snapshot.velocityX());
        putDouble(digest, snapshot.velocityY());
        putDouble(digest, snapshot.velocityZ());
        putInt(digest, snapshot.fuse());
    }

    private static FurnaceVariant furnaceVariant(int variantCode) {
        for (FurnaceVariant variant : FurnaceVariant.values()) {
            if (variant.code() == variantCode) return variant;
        }
        throw new IllegalArgumentException("unsupported furnace variant code");
    }

    private static void updateContainerStateDigest(
            MessageDigest digest, ContainerState state) {
        putString(digest, "game-expert/explosion-container-state/v3");
        if (state instanceof ChestState chest) {
            putString(digest, "ChestState");
            putString(digest, "containerSize");
            putInt(digest, chest.containerSize());
        } else if (state instanceof FurnaceState furnace) {
            putString(digest, "FurnaceState");
            putString(digest, "variantCode");
            putInt(digest, furnace.variantCode());
            putString(digest, "burnTicks");
            putInt(digest, furnace.burnTicks());
            putString(digest, "burnTotalTicks");
            putInt(digest, furnace.burnTotalTicks());
            putString(digest, "cookTicks");
            putInt(digest, furnace.cookTicks());
            putString(digest, "xpMilli");
            putInt(digest, furnace.xpMilli());
        } else if (state instanceof BrewingState brewing) {
            putString(digest, "BrewingState");
            putString(digest, "fuel");
            putInt(digest, brewing.fuel());
            putString(digest, "brewTicks");
            putInt(digest, brewing.brewTicks());
            putString(digest, "brewingIngredient");
            putShort(digest, brewing.brewingIngredient());
        } else if (state instanceof CampfireState) {
            putString(digest, "CampfireState");
        } else if (state instanceof EnchantingTableState) {
            putString(digest, "EnchantingTableState");
        } else if (state instanceof LecternState lectern) {
            putString(digest, "LecternState");
            putString(digest, "page");
            putInt(digest, lectern.page());
        } else {
            throw new IllegalStateException("unsupported exhaustive container state");
        }
    }

    private static final class Position {
        private final int x;
        private final int y;
        private final int z;

        private Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Position position
                    && x == position.x && y == position.y && z == position.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    public static final class BlockTransition {
        private final BlockProvenance provenance;
        private final short expectedBlockType;
        private final short expectedBlockState;
        private final short committedBlockType;
        private final short committedBlockState;

        public BlockTransition(BlockProvenance provenance,
                short expectedBlockType, short expectedBlockState,
                short committedBlockType, short committedBlockState) {
            if (provenance == null) {
                throw new IllegalArgumentException("authenticated block provenance is required");
            }
            this.provenance = provenance;
            this.expectedBlockType = expectedBlockType;
            this.expectedBlockState = expectedBlockState;
            this.committedBlockType = committedBlockType;
            this.committedBlockState = committedBlockState;
        }

        public BlockProvenance provenance() { return provenance; }
        public int x() { return provenance.x(); }
        public int y() { return provenance.y(); }
        public int z() { return provenance.z(); }
        public short expectedBlockType() { return expectedBlockType; }
        public short expectedBlockState() { return expectedBlockState; }
        public short committedBlockType() { return committedBlockType; }
        public short committedBlockState() { return committedBlockState; }

        private Position position() { return new Position(x(), y(), z()); }

        private void updateDigest(MessageDigest digest) {
            putBytes(digest, provenance.canonicalBytes);
            putString(digest, provenance.canonicalDigest());
            putShort(digest, expectedBlockType); putShort(digest, expectedBlockState);
            putShort(digest, committedBlockType); putShort(digest, committedBlockState);
        }
    }

    public static final class TntMutation {
        private final long tntId;
        private final PrimedTntSnapshot expected;
        private final PrimedTntSnapshot committed;

        public TntMutation(long tntId, PrimedTntSnapshot expected,
                PrimedTntSnapshot committed) {
            if (tntId <= 0 || tntId == Long.MAX_VALUE
                    || expected == null && committed == null
                    || expected != null && expected.tntId() != tntId
                    || committed != null && committed.tntId() != tntId) {
                throw new IllegalArgumentException("exact TNT mutation is required");
            }
            this.tntId = tntId;
            this.expected = expected;
            this.committed = committed;
        }

        public long tntId() { return tntId; }
        public PrimedTntSnapshot expected() { return expected; }
        public PrimedTntSnapshot committed() { return committed; }

        private void updateDigest(MessageDigest digest) {
            putLong(digest, tntId);
            putSnapshot(digest, expected);
            putSnapshot(digest, committed);
        }
    }

    public static final class ContainerStack {
        private final int slot;
        private final short itemType;
        private final int count;
        private final int durability;
        private final long enchantments;
        private final int mapId;
        private final int shulkerId;
        private final String bucketMobData;
        private final String itemComponentData;
        private final int progress;

        public ContainerStack(int slot, short itemType, int count, int durability,
                long enchantments, int mapId, int shulkerId, String bucketMobData,
                String itemComponentData, int progress) {
            if (slot < 0 || progress < 0) {
                throw new IllegalArgumentException("container slot and progress are invalid");
            }
            PlayerInventory.StackSnapshot validated = new PlayerInventory.StackSnapshot(
                    itemType, count, durability, enchantments,
                    mapId, shulkerId, bucketMobData, itemComponentData);
            this.slot = slot;
            this.itemType = validated.itemType();
            this.count = validated.count();
            this.durability = validated.durability();
            this.enchantments = validated.enchantments();
            this.mapId = validated.mapId();
            this.shulkerId = validated.shulkerId();
            this.bucketMobData = validated.bucketMobData();
            this.itemComponentData = validated.itemComponentData();
            this.progress = progress;
        }

        public int slot() { return slot; }
        public short itemType() { return itemType; }
        public int count() { return count; }
        public int durability() { return durability; }
        public long enchantments() { return enchantments; }
        public int mapId() { return mapId; }
        public int shulkerId() { return shulkerId; }
        public String bucketMobData() { return bucketMobData; }
        public String itemComponentData() { return itemComponentData; }
        public int progress() { return progress; }
        public boolean isEmpty() { return itemType == PlayerInventory.EMPTY; }

        private PlayerInventory.StackSnapshot snapshot() {
            return new PlayerInventory.StackSnapshot(itemType, count, durability, enchantments,
                    mapId, shulkerId, bucketMobData, itemComponentData);
        }

        private boolean isComponentless() {
            return durability == 0 && enchantments == 0L && mapId == 0 && shulkerId == 0
                    && bucketMobData == null && itemComponentData == null;
        }

        private boolean matches(GroundItemSnapshot output) {
            return itemType == output.itemType() && count == output.count()
                    && durability == output.durability()
                    && enchantments == output.enchantments()
                    && mapId == output.mapId() && shulkerId == output.shulkerId()
                    && Objects.equals(bucketMobData, output.bucketMobData())
                    && Objects.equals(itemComponentData, output.itemComponentData());
        }

        private void updateDigest(MessageDigest digest) {
            putInt(digest, slot); putShort(digest, itemType); putInt(digest, count);
            putInt(digest, durability); putLong(digest, enchantments);
            putInt(digest, mapId); putInt(digest, shulkerId);
            putNullableString(digest, bucketMobData);
            putNullableString(digest, itemComponentData);
            putInt(digest, progress);
        }
    }

    public static final class ContainerDrop {
        private final ContainerStack source;
        private final long outputGroundEntityId;

        public ContainerDrop(ContainerStack source, long outputGroundEntityId) {
            if (source == null
                    || source.isEmpty() && outputGroundEntityId != 0
                    || !source.isEmpty() && (outputGroundEntityId <= 0
                            || outputGroundEntityId
                                    > GroundMutationCommand.MAX_GROUND_ENTITY_ID)) {
                throw new IllegalArgumentException("exact container drop is required");
            }
            this.source = source;
            this.outputGroundEntityId = outputGroundEntityId;
        }

        public ContainerStack source() { return source; }
        public long outputGroundEntityId() { return outputGroundEntityId; }

        private void updateDigest(MessageDigest digest) {
            source.updateDigest(digest);
            putLong(digest, outputGroundEntityId);
        }
    }

    public static final class ContainerRetirement {
        private static final Comparator<ContainerDrop> DROP_ORDER = Comparator
                .comparingInt((ContainerDrop drop) -> drop.source().slot())
                .thenComparingLong(ContainerDrop::outputGroundEntityId);

        private final ContainerKind kind;
        private final int x;
        private final int y;
        private final int z;
        private final long rowId;
        private final long expectedRevision;
        private final String aggregateId;
        private final boolean pairedChest;
        private final ContainerState state;
        private final List<ContainerDrop> drops;
        private final int expectedExperience;
        private final List<Long> outputGroundXpEntityIds;

        public ContainerRetirement(ContainerKind kind, int x, int y, int z,
                long rowId, long expectedRevision, String aggregateId,
                boolean pairedChest, ContainerState state,
                List<ContainerDrop> drops, int expectedExperience,
                List<Long> outputGroundXpEntityIds) {
            if (kind == null || rowId <= 0 || rowId == Long.MAX_VALUE
                    || expectedRevision < 0 || expectedRevision == Long.MAX_VALUE
                    || aggregateId == null || aggregateId.isBlank()
                    || drops == null || drops.stream().anyMatch(Objects::isNull)
                    || expectedExperience < 0 || outputGroundXpEntityIds == null
                    || outputGroundXpEntityIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("exact container retirement is required");
            }
            int capacity = requireStateCapacity(kind, state);
            if (pairedChest && kind != ContainerKind.CHEST) {
                throw new IllegalArgumentException("only a chest may carry paired topology");
            }
            ArrayList<ContainerDrop> orderedDrops = new ArrayList<>(drops);
            orderedDrops.sort(DROP_ORDER);
            Set<Integer> slots = new HashSet<>();
            for (ContainerDrop drop : orderedDrops) {
                int slot = drop.source().slot();
                if (slot < 0 || slot >= capacity || !slots.add(slot)) {
                    throw new IllegalArgumentException("invalid or duplicate container source slot");
                }
            }
            if (orderedDrops.size() != capacity) {
                throw new IllegalArgumentException("container state must cover every exact slot");
            }
            validatePersistedSlots(kind, state, orderedDrops, expectedRevision);
            ArrayList<Long> orderedXp = new ArrayList<>(outputGroundXpEntityIds);
            orderedXp.sort(Long::compareTo);
            if (orderedXp.stream().anyMatch(id -> id <= 0
                    || id > GroundMutationCommand.MAX_GROUND_ENTITY_ID)
                    || new HashSet<>(orderedXp).size() != orderedXp.size()) {
                throw new IllegalArgumentException("container XP output identity is invalid");
            }
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rowId = rowId;
            this.expectedRevision = expectedRevision;
            this.aggregateId = aggregateId;
            this.pairedChest = pairedChest;
            this.state = state;
            this.drops = List.copyOf(orderedDrops);
            this.expectedExperience = expectedExperience;
            this.outputGroundXpEntityIds = List.copyOf(orderedXp);
        }

        public ContainerKind kind() { return kind; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public long rowId() { return rowId; }
        public long expectedRevision() { return expectedRevision; }
        public String aggregateId() { return aggregateId; }
        public boolean pairedChest() { return pairedChest; }
        public ContainerState state() { return state; }
        public List<ContainerDrop> drops() { return drops; }
        public int expectedExperience() { return expectedExperience; }
        public List<Long> outputGroundXpEntityIds() { return outputGroundXpEntityIds; }

        private Position position() { return new Position(x, y, z); }

        private void updateDigest(MessageDigest digest) {
            putInt(digest, kind.protocolId());
            putInt(digest, x); putInt(digest, y); putInt(digest, z);
            putLong(digest, rowId); putLong(digest, expectedRevision);
            putString(digest, aggregateId);
            digest.update((byte) (pairedChest ? 1 : 0));
            updateContainerStateDigest(digest, state);
            putInt(digest, drops.size());
            for (ContainerDrop drop : drops) drop.updateDigest(digest);
            putInt(digest, expectedExperience);
            putInt(digest, outputGroundXpEntityIds.size());
            for (Long entityId : outputGroundXpEntityIds) putLong(digest, entityId);
        }

        private static int requireStateCapacity(ContainerKind kind, ContainerState state) {
            if (state == null) {
                throw new IllegalArgumentException("typed container state is required");
            }
            return switch (kind) {
                case CHEST -> requireChestCapacity(
                        state, kind.canonicalLootSlots(), "chest");
                case SHELF -> requireChestCapacity(state, ShelfRules.SLOTS, "shelf");
                case SHULKER -> requireChestCapacity(
                        state, ChestInventory.SLOTS, "placed shulker");
                case FURNACE -> requireVariant(
                        state, FurnaceState.class, FurnaceInventory.SLOTS, "furnace");
                case BREWING_STAND -> requireVariant(
                        state, BrewingState.class, BrewingInventory.SLOTS, "brewing stand");
                case CAMPFIRE -> requireVariant(
                        state, CampfireState.class, CampfireInventory.SLOTS, "campfire");
                case ENCHANTING_TABLE -> requireVariant(
                        state, EnchantingTableState.class,
                        EnchantingInventory.SLOTS, "enchanting table");
                case LECTERN -> requireVariant(state, LecternState.class, 1, "lectern");
                case BARREL -> requireChestCapacity(
                        state, kind.canonicalLootSlots(), "barrel");
                case DISPENSER -> requireChestCapacity(
                        state, kind.canonicalLootSlots(), "dispenser");
            };
        }

        private static int requireChestCapacity(
                ContainerState state, int expected, String label) {
            if (!(state instanceof ChestState chest) || chest.containerSize() != expected) {
                throw new IllegalArgumentException(label + " requires its exact ChestState");
            }
            return expected;
        }

        private static int requireVariant(ContainerState state,
                Class<? extends ContainerState> expectedType, int capacity, String label) {
            if (!expectedType.isInstance(state)) {
                throw new IllegalArgumentException(label + " requires its exact state variant");
            }
            return capacity;
        }

        private static void validatePersistedSlots(ContainerKind kind, ContainerState state,
                List<ContainerDrop> drops, long expectedRevision) {
            PlayerInventory.StackSnapshot[] stacks = new PlayerInventory.StackSnapshot[drops.size()];
            for (int slot = 0; slot < drops.size(); slot++) {
                ContainerStack source = drops.get(slot).source();
                if (source.slot() != slot) {
                    throw new IllegalArgumentException("container slots must have exact coverage");
                }
                if (kind != ContainerKind.CAMPFIRE && source.progress() != 0) {
                    throw new IllegalArgumentException(
                            "only campfire slots may carry per-slot progress");
                }
                stacks[slot] = source.snapshot();
            }

            try {
                switch (kind) {
                    case CHEST, SHELF, SHULKER, BARREL, DISPENSER -> { }
                    case FURNACE -> validateFurnaceSlots(
                            (FurnaceState) state, drops, expectedRevision);
                    case BREWING_STAND -> validateBrewingSlots((BrewingState) state, drops);
                    case CAMPFIRE -> validateCampfireSlots(drops);
                    case ENCHANTING_TABLE -> new EnchantingInventory()
                            .restorePersistenceSnapshot(stacks, expectedRevision);
                    case LECTERN -> validateLecternSlot((LecternState) state, stacks[0]);
                }
            } catch (IllegalArgumentException invalid) {
                throw invalid;
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("invalid persisted container state", invalid);
            }
        }

        private static void validateFurnaceSlots(FurnaceState state,
                List<ContainerDrop> drops, long expectedRevision) {
            short[] itemTypes = new short[drops.size()];
            int[] counts = new int[drops.size()];
            PlayerInventory.StackSnapshot[] stacks = new PlayerInventory.StackSnapshot[drops.size()];
            for (int slot = 0; slot < drops.size(); slot++) {
                ContainerStack stack = drops.get(slot).source();
                stacks[slot] = new PlayerInventory.StackSnapshot(stack.itemType(), stack.count(),
                        stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                        stack.bucketMobData(), stack.itemComponentData());
                itemTypes[slot] = stack.itemType();
                counts[slot] = stack.count();
            }
            FurnaceVariant variant = furnaceVariant(state.variantCode());
            new FurnaceInventory(variant).restore(new FurnaceInventory.Snapshot(itemTypes, counts, state.burnTicks(),
                    state.burnTotalTicks(), state.cookTicks(), variant,
                    expectedRevision, state.xpMilli(), stacks));
        }

        private static void validateBrewingSlots(
                BrewingState state, List<ContainerDrop> drops) {
            short[] itemTypes = new short[drops.size()];
            int[] counts = new int[drops.size()];
            for (int slot = 0; slot < drops.size(); slot++) {
                ContainerStack stack = drops.get(slot).source();
                requireComponentless(stack, "brewing stand");
                itemTypes[slot] = stack.itemType();
                counts[slot] = stack.count();
            }
            new BrewingInventory().restore(itemTypes, counts, state.fuel(), state.brewTicks(),
                    state.brewingIngredient());
        }

        private static void validateCampfireSlots(List<ContainerDrop> drops) {
            short[] itemTypes = new short[drops.size()];
            int[] cookTicks = new int[drops.size()];
            for (int slot = 0; slot < drops.size(); slot++) {
                ContainerStack stack = drops.get(slot).source();
                requireComponentless(stack, "campfire");
                if (!stack.isEmpty() && stack.count() != 1) {
                    throw new IllegalArgumentException("campfire occupied slots require count one");
                }
                itemTypes[slot] = stack.itemType();
                cookTicks[slot] = stack.progress();
            }
            new CampfireInventory().restore(itemTypes, cookTicks);
        }

        private static void validateLecternSlot(
                LecternState state, PlayerInventory.StackSnapshot book) {
            if (book.isEmpty() || !LecternRules.canPlace(book, book.itemComponents())
                    || state.page() >= book.itemComponents().book().pages().size()) {
                throw new IllegalArgumentException("lectern requires its exact book and page");
            }
        }

        private static void requireComponentless(ContainerStack stack, String label) {
            if (!stack.isComponentless()) {
                throw new IllegalArgumentException(
                        label + " current schema does not support stack components");
            }
        }
    }
}
