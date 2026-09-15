package com.gameexpert.engine.persistence.tick;

import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierDurableStateException;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical, bounded binary identity for one durable final-carrier tick publication. */
public final class FinalCarrierTickPublicationCodec {
    /** MySQL MEDIUMBLOB stores exactly 2^24 - 1 bytes. */
    public static final int SQL_MEDIUMBLOB_MAX_BYTES = 16_777_215;
    private static final int SQL_TYPE_KEY_MAX_BYTES = 160;
    private static final int GROUND_ITEM_MAX_AGE = 6_000;
    private static final int GROUND_ITEM_MAX_PICKUP_DELAY = 20;
    private static final int MAGIC = 0x46435450; // FCTP
    private static final int FORMAT_VERSION = 1;
    private static final int SHA256_BYTES = 32;
    private static final int BLOCK_CELL_WIRE_BYTES = Integer.BYTES * 7;
    private static final int GROUND_ITEM_MIN_WIRE_BYTES = Long.BYTES * 3 + Short.BYTES
            + Integer.BYTES * 8 + Double.BYTES * 6 + Byte.BYTES;
    private static final byte[] KEY_DOMAIN =
            "webcraft/final-carrier/tick-publication-key/v1\0"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BODY_DOMAIN =
            "webcraft/final-carrier/tick-publication-body/v1\0"
                    .getBytes(StandardCharsets.US_ASCII);

    private FinalCarrierTickPublicationCodec() { }

    /** Current-hash service bounds shared by the scheduler and durable outbox. */
    public static final Limits DEFAULT = Limits.DEFAULT;

    /**
     * Caller-owned bounds for one publication body. The database blob capacity and the
     * authenticated carrier structure determine the values supplied here; this codec has no
     * implicit product-wide count or payload limit.
     */
    public static final class Limits {
        /** Current-hash bounds used by the production scheduler and persistence service. */
        public static final Limits DEFAULT = new Limits(
                SQL_MEDIUMBLOB_MAX_BYTES, FinalCarrierTickScheduler.MAX_MUTATIONS_PER_TICK,
                FinalCarrierTickScheduler.MAX_MUTATIONS_PER_TICK,
                FinalCarrierTickScheduler.MAX_MUTATIONS_PER_TICK, 160, 1 << 20);

        private final int maxBodyBytes;
        private final int maxBlockCells;
        private final int maxGroundItems;
        private final int maxCombinedMutations;
        private final int maxTypeKeyBytes;
        private final int maxTextBytes;

        public Limits(int maxBodyBytes, int maxBlockCells, int maxGroundItems,
                int maxTypeKeyBytes, int maxTextBytes) {
            this(maxBodyBytes, maxBlockCells, maxGroundItems,
                    Math.min(maxBlockCells, maxGroundItems), maxTypeKeyBytes, maxTextBytes);
        }

        /** Full current-hash bounds, including the one authenticated block-plus-drop limit. */
        public Limits(int maxBodyBytes, int maxBlockCells, int maxGroundItems,
                int maxCombinedMutations, int maxTypeKeyBytes, int maxTextBytes) {
            if (maxBodyBytes <= 0 || maxBlockCells < 0 || maxGroundItems < 0
                    || maxCombinedMutations < 0 || maxTypeKeyBytes <= 0 || maxTextBytes < 0
                    || (long) maxCombinedMutations > (long) maxBlockCells + maxGroundItems) {
                throw new IllegalArgumentException("publication codec limits are invalid");
            }
            this.maxBodyBytes = Math.min(maxBodyBytes, SQL_MEDIUMBLOB_MAX_BYTES);
            this.maxBlockCells = maxBlockCells;
            this.maxGroundItems = maxGroundItems;
            this.maxCombinedMutations = maxCombinedMutations;
            this.maxTypeKeyBytes = Math.min(maxTypeKeyBytes, SQL_TYPE_KEY_MAX_BYTES);
            this.maxTextBytes = Math.min(maxTextBytes, this.maxBodyBytes);
        }

        /** Compatibility shape for callers that use one bound for both text fields. */
        public Limits(int maxBlocks, int maxGroundItems, int maxStringBytes, int maxBodyBytes) {
            this(maxBodyBytes, maxBlocks, maxGroundItems, maxStringBytes, maxStringBytes);
        }

        public static Limits of(int maxBodyBytes, int maxBlockCells, int maxGroundItems,
                int maxTypeKeyBytes, int maxTextBytes) {
            return new Limits(maxBodyBytes, maxBlockCells, maxGroundItems, maxTypeKeyBytes,
                    maxTextBytes);
        }

        public static Limits of(int maxBodyBytes, int maxBlockCells, int maxGroundItems,
                int maxCombinedMutations, int maxTypeKeyBytes, int maxTextBytes) {
            return new Limits(maxBodyBytes, maxBlockCells, maxGroundItems, maxCombinedMutations,
                    maxTypeKeyBytes, maxTextBytes);
        }

        public int maxBodyBytes() { return maxBodyBytes; }
        public int maxBlockCells() { return maxBlockCells; }
        public int maxGroundItems() { return maxGroundItems; }
        public int maxCombinedMutations() { return maxCombinedMutations; }
        public int maxTypeKeyBytes() { return maxTypeKeyBytes; }
        public int maxTextBytes() { return maxTextBytes; }

        public int maxBlocks() { return maxBlockCells; }
        public int maxStringBytes() { return maxTextBytes; }
    }

    /** Encodes one semantic publication; publication state is stored separately from its body. */
    public static byte[] encode(FinalCarrierTickPublication publication, Limits limits) {
        Objects.requireNonNull(publication, "publication");
        Objects.requireNonNull(limits, "publication limits");
        int blockCount = publication.blockCells().size();
        int groundCount = publication.groundItems().size();
        requireCount(blockCount, limits.maxBlockCells(), "block cells");
        requireCount(groundCount, limits.maxGroundItems(), "ground items");
        requireCombinedCount(blockCount, groundCount, limits);
        for (GroundItemSnapshot item : publication.groundItems()) {
            validateGroundItemSnapshot(item);
        }

        BoundedWriter writer = new BoundedWriter(limits.maxBodyBytes());
        writer.putInt(MAGIC);
        writer.putInt(FORMAT_VERSION);
        writeTick(writer, publication.scheduledTick(), limits);
        writer.putByte(dispositionTag(publication.disposition()));
        writer.putInt(blockCount);
        for (FinalCarrierTickScheduler.BlockMutation block : publication.blockCells()) {
            writeBlock(writer, block);
        }
        writer.putInt(groundCount);
        for (GroundItemSnapshot item : publication.groundItems()) {
            writeGroundItem(writer, item, limits);
        }
        return writer.toByteArray();
    }

    public static byte[] encodePublication(FinalCarrierTickPublication publication,
            Limits limits) {
        return encode(publication, limits);
    }

    /** Decodes a legacy-body-compatible state as UNACKNOWLEDGED. */
    public static FinalCarrierTickPublication decode(byte[] body, Limits limits) {
        return decode(body, limits, PublicationState.UNACKNOWLEDGED);
    }

    public static FinalCarrierTickPublication decode(byte[] body, Limits limits,
            PublicationState state) {
        Objects.requireNonNull(body, "publication body");
        Objects.requireNonNull(limits, "publication limits");
        Objects.requireNonNull(state, "publication state");
        if (body.length == 0 || body.length > limits.maxBodyBytes()) {
            throw durable("publication body length is outside bounds", null);
        }

        Cursor cursor = new Cursor(body);
        try {
            if (cursor.getInt() != MAGIC || cursor.getInt() != FORMAT_VERSION) {
                throw durable("publication body header is invalid", null);
            }
            FinalCarrierTickScheduler.ScheduledTick tick = readTick(cursor, limits);
            FinalCarrierTickScheduler.DueDisposition disposition =
                    readDisposition(cursor.getUnsignedByte());

            int blockCount = readCount(cursor,
                    Math.min(limits.maxBlockCells(), limits.maxCombinedMutations()), "block cells",
                    BLOCK_CELL_WIRE_BYTES);
            List<FinalCarrierTickScheduler.BlockMutation> blocks =
                    new java.util.ArrayList<>(blockCount);
            for (int index = 0; index < blockCount; index++) {
                blocks.add(readBlock(cursor));
            }

            int remainingCombined = limits.maxCombinedMutations() - blockCount;
            int groundCount = readCount(cursor,
                    Math.min(limits.maxGroundItems(), remainingCombined), "ground items",
                    GROUND_ITEM_MIN_WIRE_BYTES);
            List<GroundItemSnapshot> groundItems =
                    new java.util.ArrayList<>(groundCount);
            for (int index = 0; index < groundCount; index++) {
                groundItems.add(readGroundItem(cursor, limits));
            }
            if (cursor.remaining() != 0) {
                throw durable("publication body has trailing bytes", null);
            }

            FinalCarrierTickPublication publication = new FinalCarrierTickPublication(tick,
                    disposition, blocks, groundItems, state);
            byte[] canonical = encode(publication, limits);
            if (!MessageDigest.isEqual(canonical, body)) {
                throw durable("publication body is not canonical", null);
            }
            return publication;
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw durable("publication body is malformed", invalid);
        }
    }

    public static FinalCarrierTickPublication decodePublication(byte[] body, Limits limits,
            PublicationState state) {
        return decode(body, limits, state);
    }

    public static FinalCarrierTickPublication decodePublication(byte[] body, Limits limits) {
        return decode(body, limits);
    }

    /** Decodes a body and binds it to the caller's exact row identity. */
    public static FinalCarrierTickPublication decode(byte[] body,
            FinalCarrierTickScheduler.ScheduledTick expectedTick,
            FinalCarrierTickScheduler.DueDisposition expectedDisposition, Limits limits,
            PublicationState state) {
        FinalCarrierTickPublication publication = decode(body, limits, state);
        if (!publication.scheduledTick().equals(Objects.requireNonNull(expectedTick,
                        "expected scheduled tick"))
                || publication.disposition() != Objects.requireNonNull(expectedDisposition,
                        "expected disposition")) {
            throw new FinalCarrierDurableStateException(
                    "publication body differs from expected tick identity");
        }
        return publication;
    }

    public static FinalCarrierTickPublication decode(byte[] body,
            FinalCarrierTickScheduler.ScheduledTick expectedTick,
            FinalCarrierTickScheduler.DueDisposition expectedDisposition, Limits limits) {
        return decode(body, expectedTick, expectedDisposition, limits,
                PublicationState.UNACKNOWLEDGED);
    }

    /** Returns the domain-separated identity key for one exact tick and disposition. */
    public static String publicationKey(FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition) {
        return publicationKey(tick, disposition, null);
    }

    public static String publicationKey(FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition, Limits limits) {
        Objects.requireNonNull(tick, "scheduled tick");
        Objects.requireNonNull(disposition, "disposition");
        if (limits != null) requireTypeKeyBytes(tick.typeKey(), limits);

        MessageDigest digest = sha256();
        digest.update(KEY_DOMAIN);
        writeTickToDigest(digest, tick, limits);
        digest.update(dispositionTag(disposition));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String publicationKey(FinalCarrierTickPublication publication, Limits limits) {
        Objects.requireNonNull(publication, "publication");
        return publicationKey(publication.scheduledTick(), publication.disposition(), limits);
    }

    /** Returns the domain-separated digest of the exact encoded body bytes. */
    public static String publicationDigest(byte[] body) {
        Objects.requireNonNull(body, "publication body");
        if (body.length == 0 || body.length > SQL_MEDIUMBLOB_MAX_BYTES) {
            throw new IllegalArgumentException("publication body length is outside SQL blob bounds");
        }
        MessageDigest digest = sha256();
        digest.update(BODY_DOMAIN);
        digest.update(body);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String publicationDigest(byte[] body, Limits limits) {
        Objects.requireNonNull(limits, "publication limits");
        Objects.requireNonNull(body, "publication body");
        if (body.length == 0 || body.length > limits.maxBodyBytes()) {
            throw new IllegalArgumentException("publication body length is outside bounds");
        }
        return publicationDigest(body);
    }

    public static String bodyDigest(byte[] body) { return publicationDigest(body); }
    public static String mutationDigest(byte[] body) { return publicationDigest(body); }

    public static String publicationDigest(FinalCarrierTickPublication publication, Limits limits) {
        return publicationDigest(encode(publication, limits));
    }

    /** Verifies key, digest, body bounds, and byte-for-byte canonical encoding. */
    public static void validate(FinalCarrierTickPublication publication, String key,
            String digest, byte[] body, Limits limits) {
        Objects.requireNonNull(publication, "publication");
        Objects.requireNonNull(key, "publication key");
        Objects.requireNonNull(digest, "publication digest");
        Objects.requireNonNull(body, "publication body");
        Objects.requireNonNull(limits, "publication limits");
        requireSha256Hex(key, "publication key");
        requireSha256Hex(digest, "publication digest");
        if (body.length == 0 || body.length > limits.maxBodyBytes()) {
            throw new FinalCarrierDurableStateException(
                    "publication body length is outside bounds");
        }
        String expectedKey = publicationKey(publication, limits);
        byte[] expectedBody = encode(publication, limits);
        String expectedDigest = publicationDigest(body, limits);
        if (!MessageDigest.isEqual(expectedKey.getBytes(StandardCharsets.US_ASCII),
                        key.getBytes(StandardCharsets.US_ASCII))
                || !MessageDigest.isEqual(expectedDigest.getBytes(StandardCharsets.US_ASCII),
                        digest.getBytes(StandardCharsets.US_ASCII))
                || !MessageDigest.isEqual(expectedBody, body)) {
            throw new FinalCarrierDurableStateException(
                    "durable publication key, digest, or body mismatch");
        }
    }

    public static void validatePublication(FinalCarrierTickPublication publication, String key,
            String digest, byte[] body, Limits limits) {
        validate(publication, key, digest, body, limits);
    }

    public static FinalCarrierTickPublication decodeAndValidate(byte[] body, String key,
            String digest, Limits limits) {
        return decodeAndValidate(body, key, digest, limits, PublicationState.UNACKNOWLEDGED);
    }

    public static FinalCarrierTickPublication decodeAndValidate(byte[] body, String key,
            String digest, Limits limits, PublicationState state) {
        FinalCarrierTickPublication publication = decode(body, limits, state);
        validate(publication, key, digest, body, limits);
        return publication;
    }

    private static void writeTick(BoundedWriter writer,
            FinalCarrierTickScheduler.ScheduledTick tick, Limits limits) {
        writer.putLong(tick.receipt().worldId());
        writer.putInt(tick.receipt().chunkX());
        writer.putInt(tick.receipt().chunkZ());
        writer.putByte(laneTag(tick.receipt().lane()));
        writer.putByte(laneTag(tick.key().lane()));
        writer.putHash(tick.receipt().sourceFingerprint());
        writer.putHash(tick.receipt().lanePayloadFingerprint());
        writer.putInt(tick.x());
        writer.putInt(tick.y());
        writer.putInt(tick.z());
        writeTypeKey(writer, tick.typeKey(), limits);
        writer.putInt(tick.expectedBlockId());
        writer.putLong(tick.dueTick());
        writer.putInt(tick.priority().value());
        writer.putLong(tick.subTickOrder());
        writer.putLong(tick.durableOrder());
    }

    private static FinalCarrierTickScheduler.ScheduledTick readTick(Cursor cursor,
            Limits limits) {
        long worldId = cursor.getLong();
        int chunkX = cursor.getInt();
        int chunkZ = cursor.getInt();
        FinalCarrierTickScheduler.Lane receiptLane = readLane(cursor.getUnsignedByte());
        FinalCarrierTickScheduler.Lane keyLane = readLane(cursor.getUnsignedByte());
        String sourceFingerprint = cursor.getHash();
        String payloadFingerprint = cursor.getHash();
        int x = cursor.getInt();
        int y = cursor.getInt();
        int z = cursor.getInt();
        String typeKey = readTypeKey(cursor, limits);
        int expectedBlockId = cursor.getInt();
        long dueTick = cursor.getLong();
        int priority = cursor.getInt();
        long subTickOrder = cursor.getLong();
        long durableOrder = cursor.getLong();
        if (receiptLane != keyLane) {
            throw durable("publication tick lane identity differs", null);
        }
        return new FinalCarrierTickScheduler.ScheduledTick(
                new FinalCarrierTickScheduler.TickKey(keyLane, x, y, z, typeKey),
                new FinalCarrierTickScheduler.CarrierReceipt(worldId, chunkX, chunkZ,
                        sourceFingerprint, payloadFingerprint, receiptLane),
                expectedBlockId, dueTick,
                com.gameexpert.authority.versioned.NeutralFinalChunk.TickPriority
                        .fromValue(priority),
                subTickOrder, durableOrder);
    }

    private static void writeTypeKey(BoundedWriter writer, String typeKey, Limits limits) {
        byte[] bytes = typeKeyBytes(typeKey, limits);
        writer.putInt(bytes.length);
        writer.putBytes(bytes);
    }

    private static String readTypeKey(Cursor cursor, Limits limits) {
        int length = cursor.getInt();
        if (length <= 0 || length > limits.maxTypeKeyBytes()) {
            throw durable("type key length is outside bounds", null);
        }
        byte[] bytes = cursor.getBytes(length);
        for (byte value : bytes) {
            if ((value & 0x80) != 0) throw durable("type key is not ASCII", null);
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void writeBlock(BoundedWriter writer,
            FinalCarrierTickScheduler.BlockMutation block) {
        writer.putInt(block.x());
        writer.putInt(block.y());
        writer.putInt(block.z());
        writer.putInt(block.beforeBlockId());
        writer.putInt(block.beforeBlockState());
        writer.putInt(block.blockId());
        writer.putInt(block.blockState());
    }

    private static FinalCarrierTickScheduler.BlockMutation readBlock(Cursor cursor) {
        return new FinalCarrierTickScheduler.BlockMutation(cursor.getInt(), cursor.getInt(),
                cursor.getInt(), cursor.getInt(), cursor.getInt(), cursor.getInt(), cursor.getInt());
    }

    private static void writeGroundItem(BoundedWriter writer, GroundItemSnapshot item,
            Limits limits) {
        validateGroundItemSnapshot(item);
        writer.putLong(item.entityId());
        writer.putShort(item.itemType());
        writer.putInt(item.count());
        writer.putInt(item.durability());
        writer.putLong(item.enchantments());
        writer.putInt(item.mapId());
        writer.putInt(item.shulkerId());
        writer.putNullableString(item.bucketMobData(), limits.maxTextBytes());
        writer.putNullableString(item.itemComponentData(), limits.maxTextBytes());
        writer.putLong(Double.doubleToRawLongBits(item.x()));
        writer.putLong(Double.doubleToRawLongBits(item.y()));
        writer.putLong(Double.doubleToRawLongBits(item.z()));
        writer.putLong(Double.doubleToRawLongBits(item.velocityX()));
        writer.putLong(Double.doubleToRawLongBits(item.velocityY()));
        writer.putLong(Double.doubleToRawLongBits(item.velocityZ()));
        writer.putByte(item.playerThrown() ? 1 : 0);
        writer.putInt(item.age());
        writer.putInt(item.pickupDelay());
        writer.putLong(item.excludedAllayId());
    }

    private static GroundItemSnapshot readGroundItem(Cursor cursor, Limits limits) {
        GroundItemSnapshot item = new GroundItemSnapshot(cursor.getLong(), cursor.getShort(),
                cursor.getInt(), cursor.getInt(), cursor.getLong(), cursor.getInt(), cursor.getInt(),
                cursor.getNullableString(limits.maxTextBytes(), "bucket mob data"),
                cursor.getNullableString(limits.maxTextBytes(), "item component data"),
                Double.longBitsToDouble(cursor.getLong()),
                Double.longBitsToDouble(cursor.getLong()),
                Double.longBitsToDouble(cursor.getLong()),
                Double.longBitsToDouble(cursor.getLong()),
                Double.longBitsToDouble(cursor.getLong()),
                Double.longBitsToDouble(cursor.getLong()),
                cursor.getBoolean(), cursor.getInt(), cursor.getInt(), cursor.getLong());
        validateGroundItemSnapshot(item);
        return item;
    }

    private static void writeTickToDigest(MessageDigest digest,
            FinalCarrierTickScheduler.ScheduledTick tick, Limits limits) {
        putLong(digest, tick.receipt().worldId());
        putInt(digest, tick.receipt().chunkX());
        putInt(digest, tick.receipt().chunkZ());
        digest.update(laneTag(tick.receipt().lane()));
        digest.update(laneTag(tick.key().lane()));
        putHash(digest, tick.receipt().sourceFingerprint());
        putHash(digest, tick.receipt().lanePayloadFingerprint());
        putInt(digest, tick.x());
        putInt(digest, tick.y());
        putInt(digest, tick.z());
        byte[] typeKey = typeKeyBytes(tick.typeKey(), limits);
        putInt(digest, typeKey.length);
        digest.update(typeKey);
        putInt(digest, tick.expectedBlockId());
        putLong(digest, tick.dueTick());
        putInt(digest, tick.priority().value());
        putLong(digest, tick.subTickOrder());
        putLong(digest, tick.durableOrder());
    }

    private static byte dispositionTag(FinalCarrierTickScheduler.DueDisposition disposition) {
        return switch (Objects.requireNonNull(disposition, "disposition")) {
            case EXECUTE -> 0;
            case LIVE_TYPE_NO_OP -> 1;
        };
    }

    private static byte laneTag(FinalCarrierTickScheduler.Lane lane) {
        return switch (Objects.requireNonNull(lane, "lane")) {
            case BLOCK -> 0;
            case FLUID -> 1;
        };
    }

    private static FinalCarrierTickScheduler.DueDisposition readDisposition(int tag) {
        return switch (tag) {
            case 0 -> FinalCarrierTickScheduler.DueDisposition.EXECUTE;
            case 1 -> FinalCarrierTickScheduler.DueDisposition.LIVE_TYPE_NO_OP;
            default -> throw durable("publication disposition is invalid", null);
        };
    }

    private static FinalCarrierTickScheduler.Lane readLane(int tag) {
        return switch (tag) {
            case 0 -> FinalCarrierTickScheduler.Lane.BLOCK;
            case 1 -> FinalCarrierTickScheduler.Lane.FLUID;
            default -> throw durable("publication lane is invalid", null);
        };
    }

    private static int readCount(Cursor cursor, int limit, String description, int minimumBytes) {
        int count = cursor.getInt();
        if (count < 0 || count > limit) {
            throw durable(description + " count is outside bounds", null);
        }
        if (count > cursor.remaining() / minimumBytes) {
            throw durable(description + " count cannot fit body", null);
        }
        return count;
    }

    private static void requireCount(int count, int limit, String description) {
        if (count < 0 || count > limit) {
            throw new IllegalArgumentException(description + " count is outside bounds");
        }
    }

    private static void requireCombinedCount(int blockCount, int groundCount, Limits limits) {
        if ((long) blockCount + groundCount > limits.maxCombinedMutations()) {
            throw new IllegalArgumentException("combined block and ground mutation count exceeds "
                    + "the authenticated bound");
        }
    }

    private static byte[] typeKeyBytes(String typeKey, Limits limits) {
        int maxBytes = limits == null ? SQL_TYPE_KEY_MAX_BYTES : limits.maxTypeKeyBytes();
        if (typeKey == null || typeKey.length() > maxBytes) {
            throw new IllegalArgumentException("type key exceeds bounded size");
        }
        byte[] bytes = canonicalTypeKeyBytes(typeKey);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("type key exceeds bounded size");
        }
        return bytes;
    }

    private static void requireTypeKeyBytes(String typeKey, Limits limits) {
        requireTypeKeyBytes(canonicalTypeKeyBytes(typeKey), limits);
    }

    private static void requireTypeKeyBytes(byte[] bytes, Limits limits) {
        if (bytes.length > limits.maxTypeKeyBytes()) {
            throw new IllegalArgumentException("type key exceeds bounded size");
        }
    }

    private static byte[] canonicalTypeKeyBytes(String typeKey) {
        Objects.requireNonNull(typeKey, "type key");
        byte[] bytes = typeKey.getBytes(StandardCharsets.US_ASCII);
        if (new String(bytes, StandardCharsets.US_ASCII).length() != typeKey.length()
                || !typeKey.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("canonical ASCII type key required");
        }
        return bytes;
    }

    private static void putHash(MessageDigest digest, String value) {
        digest.update(decodeHash(value));
    }

    private static byte[] decodeHash(String value) {
        requireSha256Hex(value, "SHA-256 fingerprint");
        return HexFormat.of().parseHex(value);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void putLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void requireSha256Hex(String value, String description) {
        Objects.requireNonNull(value, description);
        if (value.length() != SHA256_BYTES * 2 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(description + " must be lowercase SHA-256");
        }
    }

    /** Validates the complete current ground-item entity contract before body allocation. */
    public static void validateGroundItemSnapshot(GroundItemSnapshot item) {
        Objects.requireNonNull(item, "ground item snapshot");
        if (item.entityId() <= 0L || item.entityId() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("ground item entity ID is outside bounds");
        }
        int maxCount = PlayerInventory.stackMax(item.itemType());
        if (item.count() <= 0 || item.count() > maxCount) {
            throw new IllegalArgumentException("ground item count is outside item stack bounds");
        }
        if (item.age() < 0 || item.age() > GROUND_ITEM_MAX_AGE) {
            throw new IllegalArgumentException("ground item age is outside 0..6000");
        }
        if (item.pickupDelay() < 0 || item.pickupDelay() > GROUND_ITEM_MAX_PICKUP_DELAY) {
            throw new IllegalArgumentException("ground item pickup delay is outside 0..20");
        }
        if (!Double.isFinite(item.x()) || !Double.isFinite(item.y())
                || !Double.isFinite(item.z()) || !Double.isFinite(item.velocityX())
                || !Double.isFinite(item.velocityY()) || !Double.isFinite(item.velocityZ())) {
            throw new IllegalArgumentException("ground item snapshot contains a non-finite value");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static FinalCarrierDurableStateException durable(String message, Throwable cause) {
        return cause == null ? new FinalCarrierDurableStateException(message)
                : new FinalCarrierDurableStateException(message, cause);
    }

    private static final class BoundedWriter {
        private final int limit;
        private byte[] bytes;
        private int position;

        private BoundedWriter(int limit) {
            if (limit <= 0 || limit > SQL_MEDIUMBLOB_MAX_BYTES) {
                throw new IllegalArgumentException("publication body limit exceeds SQL blob bounds");
            }
            this.limit = limit;
            bytes = new byte[Math.min(limit, 256)];
        }

        private void require(int length) {
            if (length < 0 || position > limit - length) {
                throw new IllegalArgumentException("publication body exceeds bounded size");
            }
            int required = position + length;
            if (required <= bytes.length) return;
            int capacity = bytes.length;
            while (capacity < required) {
                capacity = capacity <= limit / 2 ? capacity * 2 : limit;
            }
            bytes = Arrays.copyOf(bytes, capacity);
        }

        private void putByte(int value) {
            require(1);
            bytes[position++] = (byte) value;
        }

        private void putShort(short value) {
            require(Short.BYTES);
            bytes[position++] = (byte) (value >>> 8);
            bytes[position++] = (byte) value;
        }

        private void putInt(int value) {
            require(Integer.BYTES);
            bytes[position++] = (byte) (value >>> 24);
            bytes[position++] = (byte) (value >>> 16);
            bytes[position++] = (byte) (value >>> 8);
            bytes[position++] = (byte) value;
        }

        private void putLong(long value) {
            require(Long.BYTES);
            for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
                bytes[position++] = (byte) (value >>> shift);
            }
        }

        private void putHash(String value) {
            putBytes(decodeHash(value));
        }

        private void putBytes(byte[] value) {
            Objects.requireNonNull(value, "publication bytes");
            require(value.length);
            System.arraycopy(value, 0, bytes, position, value.length);
            position += value.length;
        }

        private void putNullableString(String value, int maxBytes) {
            if (value == null) {
                putInt(-1);
                return;
            }
            if (value.length() > maxBytes) {
                throw new IllegalArgumentException("publication text exceeds bounded size");
            }
            byte[] encoded = strictUtf8(value);
            if (encoded.length > maxBytes) {
                throw new IllegalArgumentException("publication text exceeds bounded size");
            }
            putInt(encoded.length);
            putBytes(encoded);
        }

        private byte[] toByteArray() {
            byte[] result = new byte[position];
            System.arraycopy(bytes, 0, result, 0, position);
            return result;
        }
    }

    private static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("publication text is not UTF-8", invalid);
        }
    }

    private static final class Cursor {
        private final ByteBuffer input;

        private Cursor(byte[] body) {
            input = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        }

        private int remaining() { return input.remaining(); }

        private byte getByte() {
            require(Byte.BYTES);
            return input.get();
        }

        private int getUnsignedByte() { return Byte.toUnsignedInt(getByte()); }

        private short getShort() {
            require(Short.BYTES);
            return input.getShort();
        }

        private int getInt() {
            require(Integer.BYTES);
            return input.getInt();
        }

        private long getLong() {
            require(Long.BYTES);
            return input.getLong();
        }

        private boolean getBoolean() {
            return switch (getUnsignedByte()) {
                case 0 -> false;
                case 1 -> true;
                default -> throw durable("publication boolean is invalid", null);
            };
        }

        private byte[] getBytes(int length) {
            if (length < 0) throw durable("publication byte length is invalid", null);
            require(length);
            byte[] result = new byte[length];
            input.get(result);
            return result;
        }

        private String getHash() {
            return HexFormat.of().formatHex(getBytes(SHA256_BYTES));
        }

        private String getNullableString(int maxBytes, String description) {
            int length = getInt();
            if (length == -1) return null;
            if (length < 0 || length > maxBytes) {
                throw durable(description + " length is outside bounds", null);
            }
            return decodeUtf8(getBytes(length), description);
        }

        private void require(int length) {
            if (length < 0 || length > input.remaining()) {
                throw durable("publication body is truncated", null);
            }
        }
    }

    private static String decodeUtf8(byte[] bytes, String description) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException invalid) {
            throw durable(description + " is not valid UTF-8", invalid);
        }
    }
}
