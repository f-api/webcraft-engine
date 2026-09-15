package com.gameexpert.engine.persistence.finalcarrier.blockentity;

import com.gameexpert.terrain.Blocks;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersProductionTransaction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Pure, completely preflighted schema-4 BENT installation definition. */
public final class FinalCarrierBlockEntityPlan {
    private static final byte[] FINGERPRINT_DOMAIN =
            "MCF263LC/BENT/PLAN/v1".getBytes(StandardCharsets.US_ASCII);
    private static final String JUNGLE_DISPENSER_TABLE =
            "minecraft:chests/jungle_temple_dispenser";

    private final String installationIdentity;
    private final String sourceFingerprint;
    private final List<PlannedBlockEntity> entries;

    private FinalCarrierBlockEntityPlan(String installationIdentity, String sourceFingerprint,
            List<PlannedBlockEntity> entries) {
        this.installationIdentity = installationIdentity;
        this.sourceFingerprint = sourceFingerprint;
        this.entries = entries;
    }

    /** Validates all positions, carrier blocks, NBT schemas, and standard loot references. */
    public static FinalCarrierBlockEntityPlan prepare(String installationIdentity,
            NeutralFinalChunk carrier) {
        if (installationIdentity == null || installationIdentity.isBlank()) {
            throw new IllegalArgumentException("BENT installation identity required");
        }
        Objects.requireNonNull(carrier, "typed BENT carrier");
        short[] blockIds = carrier.blockIds();
        Map<Integer, NeutralFinalChunk.Loot> loot = new HashMap<>();
        for (NeutralFinalChunk.Loot row : carrier.sidecars().loot()) {
            if (loot.put(row.packed(), row) != null) {
                throw new IllegalArgumentException("duplicate colocated LOOT position");
            }
        }
        Set<Integer> positions = new HashSet<>();
        ArrayList<PlannedBlockEntity> result = new ArrayList<>();
        for (NeutralFinalChunk.BlockEntity row : carrier.sidecars().blockEntities()) {
            if (!positions.add(row.packed())) {
                throw new IllegalArgumentException("duplicate BENT carrier position");
            }
            TypeContract contract = TypeContract.require(row.blockIdentity(), row.entityType());
            int actualBlockId = Short.toUnsignedInt(blockIds[row.packed()]);
            if (actualBlockId != contract.blockId) {
                throw new IllegalArgumentException(
                        "BENT type does not match exact carrier block evidence");
            }
            int localX = row.packed() % Blocks.CHUNK_X;
            int yz = row.packed() / Blocks.CHUNK_X;
            int localZ = yz % Blocks.CHUNK_Z;
            int x = carrier.chunkX() * Blocks.CHUNK_X + localX;
            int y = Blocks.MIN_Y + yz / Blocks.CHUNK_Z;
            int z = carrier.chunkZ() * Blocks.CHUNK_Z + localZ;
            CanonicalBlockEntityNbt.Document nbt = CanonicalBlockEntityNbt.parse(
                    row.canonicalNbt());
            Semantic semantic = validateSchema(contract, nbt, x, y, z);
            NeutralFinalChunk.Loot colocated = loot.get(row.packed());
            if (semantic.lootTable.isPresent()) {
                if (colocated == null || !colocated.table().equals(semantic.lootTable.get())
                        || (semantic.lootSeed.isPresent()
                                && colocated.seed() != semantic.lootSeed.getAsLong())
                        || (contract == TypeContract.DISPENSER
                                && semantic.lootSeed.isEmpty() && colocated.seed() != 0L)) {
                    throw new IllegalArgumentException(
                            "BENT standard loot fields conflict with colocated LOOT");
                }
            }
            result.add(new PlannedBlockEntity(row.packed(),
                    x, y, z, actualBlockId,
                    row.blockIdentity(), row.entityType(), nbt.raw(), semantic.semantic,
                    semantic.randomSeed, semantic.lootTable, semantic.lootSeed,
                    semantic.decoratedPot));
        }
        List<PlannedBlockEntity> immutable = List.copyOf(result);
        return new FinalCarrierBlockEntityPlan(installationIdentity,
                fingerprint(installationIdentity, carrier.chunkX(), carrier.chunkZ(), immutable),
                immutable);
    }

    public String installationIdentity() {
        return installationIdentity;
    }

    public String sourceFingerprint() {
        return sourceFingerprint;
    }

    public List<PlannedBlockEntity> entries() {
        return entries;
    }

    public boolean sameDefinition(FinalCarrierBlockEntityPlan other) {
        return other != null && installationIdentity.equals(other.installationIdentity)
                && sourceFingerprint.equals(other.sourceFingerprint)
                && entries.equals(other.entries);
    }

    private static Semantic validateSchema(TypeContract contract,
            CanonicalBlockEntityNbt.Document nbt, int x, int y, int z) {
        if (contract == TypeContract.DISPENSER) {
            String table = nbt.string("LootTable").orElseThrow(() ->
                    new IllegalArgumentException("dispenser BENT requires LootTable"));
            if (table.equals(JUNGLE_DISPENSER_TABLE)) {
                nbt.requireOnly("LootTable", "LootTableSeed");
                OptionalLong seed = nbt.longValue("LootTableSeed");
                if (seed.isPresent() && seed.getAsLong() == 0L) {
                    throw new IllegalArgumentException(
                            "canonical dispenser omits zero LootTableSeed");
                }
                return new Semantic(Optional.empty(), OptionalLong.empty(), Optional.of(table), seed,
                        Optional.empty());
            }
            requireTrialEnvelope(nbt, "minecraft:dispenser", false, x, y, z);
            if (!Mc263TrialChambersProductionTransaction.isAuthenticatedNonChestBentLdec(
                    "minecraft:dispenser", table)) {
                throw new IllegalArgumentException("unsupported Trial dispenser loot table");
            }
            long seed = nbt.longValue("LootTableSeed").orElseThrow(() ->
                    new IllegalArgumentException("Trial dispenser BENT requires LootTableSeed"));
            return new Semantic(Optional.empty(), OptionalLong.empty(), Optional.of(table),
                    OptionalLong.of(seed), Optional.empty());
        }

        if (contract == TypeContract.TRIAL_DECORATED_POT) {
            requireTrialEnvelope(nbt, "minecraft:decorated_pot", true, x, y, z);
            // Components are source authority. Keep their exact raw representation in the
            // projection rather than creating a second interpreted storage authority.
            nbt.requireCompound("components");
            Map<String, String> sherds = nbt.requireCompoundStrings(
                    "sherds", "back", "left", "right", "front");
            for (String face : sherds.values()) {
                if (!isMinecraftItemKey(face)) {
                    throw new IllegalArgumentException("Trial decorated-pot sherd is not a Minecraft item key");
                }
            }
            String table = nbt.string("LootTable").orElseThrow(() ->
                    new IllegalArgumentException("Trial decorated-pot BENT requires LootTable"));
            if (!Mc263TrialChambersProductionTransaction.isAuthenticatedNonChestBentLdec(
                    "minecraft:decorated_pot", table)) {
                throw new IllegalArgumentException("unsupported Trial decorated-pot loot table");
            }
            if (nbt.contains("LootTableSeed")) {
                throw new IllegalArgumentException(
                        "Trial decorated-pot BENT omits LootTableSeed");
            }
            return new Semantic(Optional.empty(), OptionalLong.empty(), Optional.of(table),
                    OptionalLong.empty(), Optional.of(new DecoratedPotProjection(nbt.raw(),
                            sherds.get("back"), sherds.get("left"), sherds.get("right"),
                            sherds.get("front"))));
        }

        nbt.requireOnly("id", "webcraft:semantic", "RandomSeed");
        String id = nbt.string("id").orElseThrow(
                () -> new IllegalArgumentException("igloo BENT requires id"));
        if (!id.equals(contract.entityType)) {
            throw new IllegalArgumentException("igloo BENT id/type mismatch");
        }
        String semantic = nbt.string("webcraft:semantic").orElseThrow(
                () -> new IllegalArgumentException("igloo BENT requires semantic"));
        if (!semantic.equals(contract.semantic)) {
            throw new IllegalArgumentException("igloo BENT semantic/type mismatch");
        }
        OptionalLong randomSeed = nbt.longValue("RandomSeed");
        if (contract != TypeContract.CHEST && randomSeed.isPresent()) {
            throw new IllegalArgumentException("only igloo chest may carry RandomSeed");
        }
        return new Semantic(Optional.of(semantic), randomSeed, Optional.empty(),
                OptionalLong.empty(), Optional.empty());
    }

    private static void requireTrialEnvelope(CanonicalBlockEntityNbt.Document nbt,
            String entityType, boolean decoratedPot, int x, int y, int z) {
        nbt.requireOnly(decoratedPot
                ? new String[] {"LootTable", "components", "sherds", "x", "y", "z", "id"}
                : new String[] {"LootTable", "components", "x", "y", "z", "id",
                        "LootTableSeed"});
        if (!nbt.string("id").orElse("").equals(entityType)) {
            throw new IllegalArgumentException("Trial BENT id/type mismatch");
        }
        Map<String, CanonicalBlockEntityNbt.TagType> types = nbt.rootTypes();
        if (types.get("components") != CanonicalBlockEntityNbt.TagType.COMPOUND
                || types.get("x") != CanonicalBlockEntityNbt.TagType.INT
                || types.get("y") != CanonicalBlockEntityNbt.TagType.INT
                || types.get("z") != CanonicalBlockEntityNbt.TagType.INT
                || (decoratedPot && types.get("sherds") != CanonicalBlockEntityNbt.TagType.COMPOUND)) {
            throw new IllegalArgumentException("Trial BENT canonical envelope mismatch");
        }
        if (nbt.intValue("x").orElseThrow() != x
                || nbt.intValue("y").orElseThrow() != y
                || nbt.intValue("z").orElseThrow() != z) {
            throw new IllegalArgumentException("Trial BENT coordinates conflict with carrier position");
        }
    }

    private enum TypeContract {
        CHEST("minecraft:chest", "minecraft:chest", Blocks.CHEST,
                Mc263IglooStructureExecutor.EMPTY_CHEST),
        FURNACE("minecraft:furnace", "minecraft:furnace", Blocks.FURNACE,
                Mc263IglooStructureExecutor.EMPTY_FURNACE),
        SIGN("minecraft:oak_wall_sign", "minecraft:sign", Blocks.OAK_WALL_SIGN,
                Mc263IglooStructureExecutor.DIRECTION_SIGN),
        BREWING("minecraft:brewing_stand", "minecraft:brewing_stand", Blocks.BREWING_STAND,
                Mc263IglooStructureExecutor.WEAKNESS_BREWING_STAND),
        DISPENSER("minecraft:dispenser", "minecraft:dispenser", Blocks.DISPENSER, null),
        TRIAL_DECORATED_POT("minecraft:decorated_pot", "minecraft:decorated_pot",
                Blocks.DECORATED_POT, null);

        private final String blockIdentity;
        private final String entityType;
        private final int blockId;
        private final String semantic;

        TypeContract(String blockIdentity, String entityType, int blockId, String semantic) {
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
            this.blockId = blockId;
            this.semantic = semantic;
        }

        private static TypeContract require(String blockIdentity, String entityType) {
            for (TypeContract value : values()) {
                if (value.blockIdentity.equals(blockIdentity)
                        && value.entityType.equals(entityType)) return value;
            }
            throw new IllegalArgumentException("unsupported BENT block/entity pairing");
        }
    }

    private static boolean isMinecraftItemKey(String value) {
        return value.matches("minecraft:[a-z0-9_./-]+");
    }

    private record Semantic(Optional<String> semantic, OptionalLong randomSeed,
            Optional<String> lootTable, OptionalLong lootSeed,
            Optional<DecoratedPotProjection> decoratedPot) {}

    /** Immutable server projection for a Trial decorated pot; raw NBT remains canonical authority. */
    public static final class DecoratedPotProjection {
        private final byte[] canonicalNbt;
        private final String back;
        private final String left;
        private final String right;
        private final String front;

        private DecoratedPotProjection(byte[] canonicalNbt, String back, String left,
                String right, String front) {
            this.canonicalNbt = canonicalNbt.clone();
            this.back = back;
            this.left = left;
            this.right = right;
            this.front = front;
        }

        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public String back() { return back; }
        public String left() { return left; }
        public String right() { return right; }
        public String front() { return front; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof DecoratedPotProjection value)) return false;
            return Arrays.equals(canonicalNbt, value.canonicalNbt) && back.equals(value.back)
                    && left.equals(value.left) && right.equals(value.right)
                    && front.equals(value.front);
        }

        @Override public int hashCode() {
            return 31 * Objects.hash(back, left, right, front) + Arrays.hashCode(canonicalNbt);
        }
    }

    /** Exact generated aggregate input for one carrier cell. */
    public static final class PlannedBlockEntity {
        private final int packed;
        private final int x;
        private final int y;
        private final int z;
        private final int carrierBlockId;
        private final String blockIdentity;
        private final String entityType;
        private final byte[] canonicalNbt;
        private final Optional<String> semantic;
        private final OptionalLong randomSeed;
        private final Optional<String> lootTable;
        private final OptionalLong lootSeed;
        private final Optional<DecoratedPotProjection> decoratedPot;

        private PlannedBlockEntity(int packed, int x, int y, int z, int carrierBlockId,
                String blockIdentity, String entityType, byte[] canonicalNbt,
                Optional<String> semantic, OptionalLong randomSeed, Optional<String> lootTable,
                OptionalLong lootSeed, Optional<DecoratedPotProjection> decoratedPot) {
            this.packed = packed;
            this.x = x;
            this.y = y;
            this.z = z;
            this.carrierBlockId = carrierBlockId;
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
            this.canonicalNbt = canonicalNbt.clone();
            this.semantic = semantic;
            this.randomSeed = randomSeed;
            this.lootTable = lootTable;
            this.lootSeed = lootSeed;
            this.decoratedPot = decoratedPot;
        }

        public int packed() { return packed; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int carrierBlockId() { return carrierBlockId; }
        public String blockIdentity() { return blockIdentity; }
        public String entityType() { return entityType; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public Optional<String> semantic() { return semantic; }
        public OptionalLong randomSeed() { return randomSeed; }
        public Optional<String> lootTable() { return lootTable; }
        public OptionalLong lootSeed() { return lootSeed; }
        public Optional<DecoratedPotProjection> decoratedPot() { return decoratedPot; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PlannedBlockEntity value)) return false;
            return packed == value.packed && x == value.x && y == value.y && z == value.z
                    && carrierBlockId == value.carrierBlockId
                    && blockIdentity.equals(value.blockIdentity)
                    && entityType.equals(value.entityType)
                    && Arrays.equals(canonicalNbt, value.canonicalNbt)
                    && semantic.equals(value.semantic) && randomSeed.equals(value.randomSeed)
                    && lootTable.equals(value.lootTable) && lootSeed.equals(value.lootSeed)
                    && decoratedPot.equals(value.decoratedPot);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(packed, x, y, z, carrierBlockId, blockIdentity, entityType,
                    semantic, randomSeed, lootTable, lootSeed);
            result = 31 * result + decoratedPot.hashCode();
            return 31 * result + Arrays.hashCode(canonicalNbt);
        }
    }

    private static String fingerprint(String identity, int chunkX, int chunkZ,
            List<PlannedBlockEntity> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_DOMAIN);
            update(digest, identity);
            update(digest, chunkX);
            update(digest, chunkZ);
            update(digest, entries.size());
            for (PlannedBlockEntity entry : entries) {
                update(digest, entry.packed);
                update(digest, entry.x);
                update(digest, entry.y);
                update(digest, entry.z);
                update(digest, entry.carrierBlockId);
                update(digest, entry.blockIdentity);
                update(digest, entry.entityType);
                update(digest, entry.canonicalNbt.length);
                digest.update(entry.canonicalNbt);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
