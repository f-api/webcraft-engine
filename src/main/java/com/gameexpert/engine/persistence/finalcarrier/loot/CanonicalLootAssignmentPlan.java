package com.gameexpert.engine.persistence.finalcarrier.loot;

import com.gameexpert.authority.versioned.NeutralFinalChunk;

import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierLaneMutation;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.Binding;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.Found;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.LocatedMapTarget;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.NotFound;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Completely preflighted immutable definitions for one admitted canonical LOOT lane. */
public final class CanonicalLootAssignmentPlan {
    private static final byte[] FINGERPRINT_DOMAIN =
            "MCF263/LOOT/DEFINITION/v3".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LOCATED_PRODUCTION_CONTEXT_MAGIC =
            "MC263-LOOT-TARGET-CONTEXT-V3\0".getBytes(StandardCharsets.US_ASCII);
    /** These limits are the authenticated LDEC/context limits used by the 26.3 carrier. */
    private static final int MAX_CONTEXT_MAPS = 64;
    private static final int MAX_CONTEXT_TEXT_BYTES = 192;
    private static final int MAX_CONTEXT_PAYLOAD_BYTES = 16 * 1024;
    private static final int CONTEXT_RECEIPT_BYTES = 32;
    private static final Comparator<String> BYTEWISE_CONTEXT_TEXT_ORDER =
            (left, right) -> Arrays.compareUnsigned(
                    left.getBytes(StandardCharsets.US_ASCII),
                    right.getBytes(StandardCharsets.US_ASCII));
    private static final byte[] FIXTURE_SOURCE_DOMAIN =
            "MCF263/LOOT/FIXTURE-SOURCE/v1".getBytes(StandardCharsets.US_ASCII);
    private static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;
    private static final long GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15L;
    private final List<WorldCanonicalLootAssignment> assignments;

    private CanonicalLootAssignmentPlan(List<WorldCanonicalLootAssignment> assignments) {
        this.assignments = List.copyOf(assignments);
    }

    public static CanonicalLootAssignmentPlan prepare(long worldSeed,
            FinalCarrierLaneMutation mutation, NeutralFinalChunk carrier,
            NeutralFinalChunk.Sidecars exactPayload) {
        Objects.requireNonNull(mutation, "LOOT lane mutation");
        Objects.requireNonNull(carrier, "LOOT carrier");
        Objects.requireNonNull(exactPayload, "LOOT projection");
        if (carrier.chunkX() != mutation.getChunkX() || carrier.chunkZ() != mutation.getChunkZ()) {
            throw new IllegalArgumentException("LOOT carrier coordinates differ from lane mutation");
        }

        List<NeutralFinalChunk.Loot> canonicalLoot = carrier.sidecars().loot().stream()
                .sorted(Comparator.comparingInt(NeutralFinalChunk.Loot::packed)).toList();
        List<NeutralFinalChunk.ContainerLootDeclaration> declarations =
                validateExactDeclarations(carrier, exactPayload, canonicalLoot);
        Map<NeutralFinalChunk.Loot, Integer> sourceOrdinals = new HashMap<>();
        for (int ordinal = 0; ordinal < canonicalLoot.size(); ordinal++) {
            if (sourceOrdinals.put(canonicalLoot.get(ordinal), ordinal) != null) {
                throw new IllegalArgumentException("duplicate canonical LOOT source row");
            }
        }
        if (!canonicalLoot.equals(exactPayload.loot())) {
            throw new IllegalArgumentException("LOOT projection differs from canonical carrier order");
        }

        Set<Integer> positions = new HashSet<>();
        short[] blockIds = carrier.blockIds();
        ArrayList<ValidatedLoot> validated = new ArrayList<>(exactPayload.loot().size());
        for (NeutralFinalChunk.Loot loot : exactPayload.loot()) {
            if (!positions.add(loot.packed())) {
                throw new IllegalArgumentException("duplicate canonical LOOT position");
            }
            Integer sourceOrdinal = sourceOrdinals.get(loot);
            if (sourceOrdinal == null) {
                throw new IllegalArgumentException("LOOT row is not referenced by canonical carrier");
            }
            NeutralFinalChunk.ContainerLootDeclaration declaration =
                    declarations.get(sourceOrdinal);
            if (declaration.ordinal() != sourceOrdinal
                    || declaration.sourceSection()
                            != NeutralFinalChunk.ContainerLootSourceSection.LOOT
                    || declaration.sourceSectionOrdinal() != sourceOrdinal) {
                throw new IllegalArgumentException(
                        "LOOT declarations have an ordinal that does not match the row");
            }
            int carrierBlock = Short.toUnsignedInt(blockIds[loot.packed()]);
            CanonicalLootContainerKind kind = switch (carrierBlock) {
                case Blocks.CHEST -> CanonicalLootContainerKind.CHEST;
                case Blocks.BARREL -> CanonicalLootContainerKind.BARREL;
                case Blocks.DISPENSER -> CanonicalLootContainerKind.DISPENSER;
                case Blocks.DECORATED_POT -> CanonicalLootContainerKind.DECORATED_POT;
                default -> throw new IllegalArgumentException(
                        "LOOT container conflicts with carrier block");
            };
            int localX = loot.packed() % Blocks.CHUNK_X;
            int yz = loot.packed() / Blocks.CHUNK_X;
            int localZ = yz % Blocks.CHUNK_Z;
            int y = Blocks.MIN_Y + yz / Blocks.CHUNK_Z;
            int x = carrier.chunkX() * Blocks.CHUNK_X + localX;
            int z = carrier.chunkZ() * Blocks.CHUNK_Z + localZ;
            if (declaration.containerSize() != kind.slots()) {
                throw new IllegalArgumentException("LOOT declaration container size does not match row");
            }
            long[] initial = loot.seed() == 0L
                    ? namedInitialState(worldSeed, loot.table()) : null;
            validated.add(new ValidatedLoot(loot, x, y, z, kind, declaration, initial));
        }

        ArrayList<WorldCanonicalLootAssignment> result = new ArrayList<>(validated.size());
        for (ValidatedLoot value : validated) {
            byte[] contextPayload = encodeLocatedProductionContextPayload(value.declaration());
            String definition = fingerprint(worldSeed, mutation, value.loot(), value.x(), value.y(),
                    value.z(), value.kind(), contextPayload, value.initial());
            WorldCanonicalLootAssignment assignment = new WorldCanonicalLootAssignment(mutation.getWorldId(), worldSeed,
                    carrier.chunkX(), carrier.chunkZ(), value.loot().packed(), value.x(), value.y(),
                    value.z(), value.loot().facing(), value.loot().table(), value.loot().seed(),
                    value.kind(), mutation.getInstallationIdentity(),
                    mutation.getPayloadFingerprint(), definition,
                    value.initial() == null ? null : value.initial()[0],
                    value.initial() == null ? null : value.initial()[1], contextPayload);
            assignment.verifyProducer(carrier);
            result.add(assignment);
        }
        return new CanonicalLootAssignmentPlan(result);
    }

    public List<WorldCanonicalLootAssignment> assignments() {
        return assignments;
    }

    static long[] namedInitialState(long worldSeed, String tableKey) {
        try {
            long low = worldSeed ^ SILVER_RATIO_64;
            long high = low + GOLDEN_RATIO_64;
            byte[] hash = MessageDigest.getInstance("MD5")
                    .digest(tableKey.getBytes(StandardCharsets.UTF_8));
            ByteBuffer words = ByteBuffer.wrap(hash).order(ByteOrder.BIG_ENDIAN);
            return new long[] {McRandom.mixStafford13(low ^ words.getLong()),
                    McRandom.mixStafford13(high ^ words.getLong())};
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static List<NeutralFinalChunk.ContainerLootDeclaration> validateExactDeclarations(
            NeutralFinalChunk carrier, NeutralFinalChunk.Sidecars exactPayload,
            List<NeutralFinalChunk.Loot> canonicalLoot) {
        NeutralFinalChunk projected = carrier.withSidecars(exactPayload);
        if (!projected.sidecars().equals(exactPayload)) throw new IllegalArgumentException("exact LOOT projection differs from selected producer result");
        List<NeutralFinalChunk.ContainerLootDeclaration> declarations =
                exactPayload.containerLootDeclarations();
        if (declarations.size() != canonicalLoot.size()) {
            throw new IllegalArgumentException(
                    "canonical LOOT declarations contain missing or unreferenced rows");
        }
        List<NeutralFinalChunk.ContainerLootDeclaration> sourceDeclarations =
                carrier.sidecars().containerLootDeclarations();
        for (int ordinal = 0; ordinal < canonicalLoot.size(); ordinal++) {
            if (sourceDeclarations.size() <= ordinal
                    || !declarations.get(ordinal).equals(sourceDeclarations.get(ordinal))) {
                throw new IllegalArgumentException(
                        "canonical LOOT declarations do not exactly match their source row");
            }
        }
        Set<Integer> referencedOrdinals = new HashSet<>();
        for (NeutralFinalChunk.ContainerLootDeclaration declaration : declarations) {
            if (!referencedOrdinals.add(declaration.ordinal())) {
                throw new IllegalArgumentException(
                        "canonical LOOT declarations contain duplicate declaration ordinal");
            }
        }
        if (referencedOrdinals.size() != declarations.size()) {
            throw new IllegalArgumentException("canonical LOOT declarations contain an unreferenced row");
        }
        return declarations;
    }

    static String fingerprint(long worldSeed, FinalCarrierLaneMutation mutation,
            NeutralFinalChunk.Loot loot, int x, int y, int z,
            CanonicalLootContainerKind kind,
            byte[] contextPayload, long[] initial) {
        return fingerprint(worldSeed, mutation.getInstallationIdentity(),
                mutation.getPayloadFingerprint(), mutation.getWorldId(), mutation.getChunkX(),
                mutation.getChunkZ(), loot.packed(), x, y, z, loot.facing(), loot.table(),
                loot.seed(), kind, contextPayload, initial);
    }

    static String fingerprint(long worldSeed, String laneInstallationIdentity,
            String installationFingerprint, long worldId, int chunkX, int chunkZ, int packed,
            int x, int y, int z, String facing, String tableKey, long rawSeed,
            CanonicalLootContainerKind kind, byte[] contextPayload, long[] initial) {
        return fingerprint(worldSeed, laneInstallationIdentity, installationFingerprint,
                worldId, chunkX, chunkZ, packed, x, y, z, facing, tableKey, rawSeed, kind,
                contextPayload, initial, NeutralFinalChunk.ContainerLootSourceSection.LOOT);
    }

    static String fingerprint(long worldSeed, String laneInstallationIdentity,
            String installationFingerprint, long worldId, int chunkX, int chunkZ, int packed,
            int x, int y, int z, String facing, String tableKey, long rawSeed,
            CanonicalLootContainerKind kind, byte[] contextPayload, long[] initial,
            NeutralFinalChunk.ContainerLootSourceSection expectedSourceSection) {
        decodeLocatedProductionContextPayload(contextPayload, expectedSourceSection);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_DOMAIN);
            update(digest, laneInstallationIdentity);
            update(digest, installationFingerprint);
            update(digest, worldId);
            update(digest, worldSeed);
            update(digest, chunkX);
            update(digest, chunkZ);
            update(digest, packed);
            update(digest, x);
            update(digest, y);
            update(digest, z);
            update(digest, facing);
            update(digest, tableKey);
            update(digest, rawSeed);
            update(digest, kind.name());
            update(digest, contextPayload);
            if (initial != null) {
                update(digest, initial[0]);
                update(digest, initial[1]);
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

    private static void update(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte) (value >>> shift));
    }

    private static void update(MessageDigest digest, byte[] value) {
        Objects.requireNonNull(value, "production context payload");
        update(digest, value.length);
        digest.update(value);
    }

    /** Descriptor-free durable v3 payload carrying exact Found/NotFound target authority. */
    public record LocatedProductionContextPayload(int ordinal,
            NeutralFinalChunk.ContainerLootSourceSection sourceSection,
            int sourceSectionOrdinal, int containerSize,
            NeutralFinalChunk.ProductionContext productionContext,
            String producerSourceSha256, String sourceDeclarationSha256) {
        public LocatedProductionContextPayload {
            boolean blockLoot = sourceSection
                    == NeutralFinalChunk.ContainerLootSourceSection.LOOT
                    && sourceSectionOrdinal == ordinal;
            // Installed ENTITIES projection has no block LOOT declarations. Its dense LDEC
            // ordinal counts only loot-bearing rows, while the ENTS ordinal counts every row.
            // Exact dense/source-row matching is authenticated by the installed carrier codec.
            boolean entityLoot = sourceSection
                    == NeutralFinalChunk.ContainerLootSourceSection.ENTS
                    && sourceSectionOrdinal >= ordinal && containerSize == 27;
            if (ordinal < 0 || (!blockLoot && !entityLoot)
                    || containerSize < 1 || containerSize > 256) {
                throw new IllegalArgumentException("located production context envelope drift");
            }
            Objects.requireNonNull(productionContext, "located production context");
            requireReceipt(productionContext.catalogReceipt(),
                    "located production context receipt");
            requireReceipt(producerSourceSha256, "producer source identity");
            requireReceipt(sourceDeclarationSha256, "source declaration identity");
        }
    }

    public static byte[] encodeLocatedProductionContextPayload(
            NeutralFinalChunk.ProductionContext context, String producerSourceSha256,
            String sourceDeclarationSha256) {
        return encodeLocatedProductionContextPayload(new LocatedProductionContextPayload(0,
                NeutralFinalChunk.ContainerLootSourceSection.LOOT, 0, 27, context,
                producerSourceSha256, sourceDeclarationSha256));
    }

    public static byte[] encodeLocatedProductionContextPayload(
            LocatedProductionContextPayload value) {
        Objects.requireNonNull(value, "located production context payload");
        NeutralFinalChunk.ProductionContext context = value.productionContext();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(LOCATED_PRODUCTION_CONTEXT_MAGIC);
            output.writeInt(value.ordinal());
            output.writeByte(value.sourceSection().ordinal());
            output.writeInt(value.sourceSectionOrdinal());
            output.writeShort(value.containerSize());
            writeContextString(output, context.biomeKey(), "production loot-origin biome");
            writeContextString(output, context.worldIdentity(), "production loot world identity");
            writeContextString(output, context.sourceIdentity(), "production loot source identity");
            writeContextString(output, context.tableIdentity(), "production loot table identity");
            output.writeInt(context.originX()); output.writeInt(context.originY());
            output.writeInt(context.originZ());
            output.write(HexFormat.of().parseHex(context.catalogReceipt()));
            List<Map.Entry<String, NeutralFinalChunk.TargetMap>> maps = context.maps().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(BYTEWISE_CONTEXT_TEXT_ORDER)).toList();
            if (maps.size() > MAX_CONTEXT_MAPS) {
                throw new IllegalArgumentException("located production context exceeds 64 maps");
            }
            output.writeShort(maps.size());
            for (Map.Entry<String, NeutralFinalChunk.TargetMap> entry : maps) {
                writeContextString(output, entry.getKey(), "map destination");
                NeutralFinalChunk.TargetMap target = entry.getValue();
                NeutralFinalChunk.MapBinding binding = target.binding();
                output.writeByte(target.found() != null ? 1 : 0);
                writeContextString(output, binding.destinationTag(), "map destination tag");
                writeContextString(output, binding.structureSet(), "map structure set");
                output.writeShort(binding.acceptedMembers().size());
                for (String member : binding.acceptedMembers()) {
                    writeContextString(output, member, "map accepted structure member");
                }
                output.writeInt(binding.scale());
                output.writeInt(binding.searchRadius());
                output.writeBoolean(binding.skipExistingChunks());
                output.write(HexFormat.of().parseHex(binding.locatorSourceReceipt()));
                requireReceipt(binding.referenceSnapshotReceipt(), "reference snapshot receipt");
                output.write(HexFormat.of().parseHex(binding.referenceSnapshotReceipt()));
                if (target.found() != null) {
                    var found = target.found();
                    output.writeInt(found.targetX()); output.writeInt(found.targetZ());
                    output.writeInt(found.savedCenterX()); output.writeInt(found.savedCenterZ());
                    output.write(HexFormat.of().parseHex(found.previewSha256()));
                }
                output.write(HexFormat.of().parseHex(target.targetReceipt()));
            }
            output.write(HexFormat.of().parseHex(value.producerSourceSha256()));
            output.write(HexFormat.of().parseHex(value.sourceDeclarationSha256()));
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_CONTEXT_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("located production context exceeds 16 KiB");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Decodes the public block-LOOT contract; a source-section flip must never select ENTS. */
    public static LocatedProductionContextPayload decodeLocatedProductionContextPayload(
            byte[] payload) {
        return decodeLocatedProductionContextPayload(payload,
                NeutralFinalChunk.ContainerLootSourceSection.LOOT);
    }

    /** Internal callers select the already-authenticated owning lane, never the payload byte. */
    static LocatedProductionContextPayload decodeLocatedProductionContextPayload(
            byte[] payload, NeutralFinalChunk.ContainerLootSourceSection expectedSourceSection) {
        Objects.requireNonNull(payload, "located production context payload");
        Objects.requireNonNull(expectedSourceSection, "located production context source section");
        if (payload.length == 0 || payload.length > MAX_CONTEXT_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "located production context payload is outside bounds");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            byte[] magic = input.readNBytes(LOCATED_PRODUCTION_CONTEXT_MAGIC.length);
            if (!Arrays.equals(magic, LOCATED_PRODUCTION_CONTEXT_MAGIC)) {
                throw new IllegalArgumentException(
                        "invalid located production context payload magic");
            }
            int ordinal = input.readInt();
            int sectionCode = input.readUnsignedByte();
            if (sectionCode >= NeutralFinalChunk.ContainerLootSourceSection.values().length) {
                throw new IllegalArgumentException("located context source section drift");
            }
            if (sectionCode != expectedSourceSection.ordinal()) {
                throw new IllegalArgumentException("located context source section differs from owning lane");
            }
            int sectionOrdinal = input.readInt();
            int containerSize = input.readUnsignedShort();
            String biome = readContextString(input, "production loot-origin biome");
            String worldIdentity = readContextString(input, "production loot world identity");
            String sourceIdentity = readContextString(input, "production loot source identity");
            String tableIdentity = readContextString(input, "production loot table identity");
            int originX = input.readInt();
            int originY = input.readInt();
            int originZ = input.readInt();
            String contextReceipt = readReceipt(input, "located production context receipt");
            int mapCount = input.readUnsignedShort();
            if (mapCount > MAX_CONTEXT_MAPS) {
                throw new IllegalArgumentException("located production context exceeds 64 maps");
            }
            LinkedHashMap<String, NeutralFinalChunk.TargetMap> maps = new LinkedHashMap<>();
            String previous = null;
            for (int index = 0; index < mapCount; index++) {
                String destination = readContextString(input, "map destination");
                if (!destination.matches("[a-z0-9_]+") || previous != null
                        && BYTEWISE_CONTEXT_TEXT_ORDER.compare(previous, destination) >= 0) {
                    throw new IllegalArgumentException(
                            "located production maps are invalid, duplicate, or unsorted");
                }
                int variant = input.readUnsignedByte();
                if (variant != 0 && variant != 1) {
                    throw new IllegalArgumentException("unknown located-map target variant");
                }
                String destinationTag = readContextString(input, "map destination tag");
                String structureSet = readContextString(input, "map structure set");
                int memberCount = input.readUnsignedShort();
                if (memberCount == 0 || memberCount > MAX_CONTEXT_MAPS) {
                    throw new IllegalArgumentException("located-map member count outside bounds");
                }
                ArrayList<String> members = new ArrayList<>(memberCount);
                for (int member = 0; member < memberCount; member++) {
                    members.add(readContextString(input, "map accepted structure member"));
                }
                int scale = input.readInt();
                int searchRadius = input.readInt();
                boolean skipExistingChunks = input.readBoolean();
                String locatorReceipt = readReceipt(input, "locator source receipt");
                String referenceSnapshotReceipt = readReceipt(input,
                        "reference snapshot receipt");
                NeutralFinalChunk.MapBinding binding = new NeutralFinalChunk.MapBinding(destination, destinationTag, structureSet, members,
                        worldIdentity, sourceIdentity, tableIdentity, originX, originY, originZ,
                        scale, searchRadius, skipExistingChunks, locatorReceipt,
                        referenceSnapshotReceipt);
                NeutralFinalChunk.TargetMap target;
                if (variant == 1) {
                    int targetX = input.readInt();
                    int targetZ = input.readInt();
                    int savedCenterX = input.readInt();
                    int savedCenterZ = input.readInt();
                    String previewReceipt = readReceipt(input, "map preview receipt");
                    String targetReceipt = readReceipt(input, "map target receipt");
                    target = new NeutralFinalChunk.TargetMap(destination, binding, targetReceipt, new NeutralFinalChunk.FoundTarget(targetX, targetZ, savedCenterX, savedCenterZ, previewReceipt));
                } else {
                    target = new NeutralFinalChunk.TargetMap(destination, binding, readReceipt(input, "map target receipt"), null);
                }
                if (maps.put(destination, target) != null) {
                    throw new IllegalArgumentException("duplicate located production map");
                }
                previous = destination;
            }
            String producer = readReceipt(input, "producer source identity");
            String source = readReceipt(input, "source declaration identity");
            if (input.read() != -1) {
                throw new IllegalArgumentException("trailing located production context payload");
            }
            NeutralFinalChunk.ProductionContext context = new NeutralFinalChunk.ProductionContext(biome, worldIdentity, sourceIdentity, tableIdentity, originX, originY, originZ, contextReceipt, List.copyOf(maps.values()), List.of(), true);
            LocatedProductionContextPayload decoded = new LocatedProductionContextPayload(
                    ordinal, NeutralFinalChunk.ContainerLootSourceSection.values()[sectionCode],
                    sectionOrdinal, containerSize, context, producer, source);
            if (!Arrays.equals(payload, encodeLocatedProductionContextPayload(decoded))) {
                throw new IllegalArgumentException(
                        "noncanonical located production context payload");
            }
            return decoded;
        } catch (IOException | RuntimeException invalid) {
            if (invalid instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException(
                    "malformed located production context payload", invalid);
        }
    }

    public static boolean isLocatedProductionContextPayload(byte[] payload) {
        if (payload == null || payload.length < LOCATED_PRODUCTION_CONTEXT_MAGIC.length) {
            return false;
        }
        for (int index = 0; index < LOCATED_PRODUCTION_CONTEXT_MAGIC.length; index++) {
            if (payload[index] != LOCATED_PRODUCTION_CONTEXT_MAGIC[index]) return false;
        }
        return true;
    }

    /** Validates the sole durable v3 located-target context representation. */
    public static void validateProductionContextPayload(byte[] payload) {
        decodeLocatedProductionContextPayload(payload);
    }

    static byte[] encodeLocatedProductionContextPayload(
            NeutralFinalChunk.ContainerLootDeclaration declaration) {
        Objects.requireNonNull(declaration, "loot declaration");
        if (declaration.productionContext().located()) {
            var located = declaration.productionContext();
            return encodeLocatedProductionContextPayload(new LocatedProductionContextPayload(
                    declaration.ordinal(), declaration.sourceSection(),
                    declaration.sourceSectionOrdinal(), declaration.containerSize(), located,
                    declaration.producerSourceSha256(),
                    declaration.sourceDeclarationSha256()));
        }
        throw new IllegalArgumentException(
                "production LOOT declaration rejects fixture-only context variant 0");
    }

    private static void writeContextString(DataOutputStream output, String value, String label)
            throws IOException {
        byte[] bytes = contextTextBytes(value, label);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static byte[] contextTextBytes(String value, String label) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException(label + " required");
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_CONTEXT_TEXT_BYTES || !isAscii(bytes)
                || !value.equals(new String(bytes, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(label + " exceeds canonical ASCII bound");
        }
        return bytes;
    }

    private static String readContextString(DataInputStream input, String label) throws IOException {
        int length = input.readUnsignedShort();
        if (length == 0 || length > MAX_CONTEXT_TEXT_BYTES) {
            throw new IllegalArgumentException(label + " exceeds canonical bound");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        String value = new String(bytes, StandardCharsets.US_ASCII);
        if (!isAscii(bytes) || !value.equals(new String(bytes, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(label + " is not canonical ASCII");
        }
        return value;
    }

    private static String readReceipt(DataInputStream input, String label) throws IOException {
        byte[] receipt = input.readNBytes(CONTEXT_RECEIPT_BYTES);
        if (receipt.length != CONTEXT_RECEIPT_BYTES) throw new EOFException();
        String value = HexFormat.of().formatHex(receipt);
        requireReceipt(value, label);
        return value;
    }

    private static boolean isAscii(byte[] bytes) {
        for (byte value : bytes) {
            if (value < 0) return false;
        }
        return true;
    }

    private static void requireReceipt(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")
                || value.equals("0".repeat(64))) {
            throw new IllegalArgumentException(label + " must be canonical nonzero SHA-256");
        }
    }

    public static boolean isCampTable(String tableKey) {
        return "minecraft:barrels/abandoned_camp_barrel".equals(tableKey)
                || "minecraft:chests/abandoned_camp_common_chest".equals(tableKey)
                || "minecraft:chests/abandoned_camp_secret_chest".equals(tableKey);
    }

    private static NeutralFinalChunk.ProductionContext projectLegacyContext(LocatedProductionContext c) {
        List<NeutralFinalChunk.TargetMap> targets = new ArrayList<>();
        for (var entry:c.maps().entrySet()) {
            var t=entry.getValue(); var b=t.binding();
            var binding=new NeutralFinalChunk.MapBinding(b.destination(), b.destinationTag(), b.structureSet(), b.acceptedMembers(), b.worldIdentity(), b.sourceIdentity(), b.tableIdentity(), b.originX(), b.originY(), b.originZ(), b.scale(), b.searchRadius(), b.skipExistingChunks(), b.locatorSourceReceipt(), b.referenceSnapshotReceipt());
            var found=t instanceof Found f ? new NeutralFinalChunk.FoundTarget(f.targetX(),f.targetZ(),f.savedCenterX(),f.savedCenterZ(),f.previewSha256()) : null;
            targets.add(new NeutralFinalChunk.TargetMap(entry.getKey(),binding,t.targetReceipt(),found));
        }
        return new NeutralFinalChunk.ProductionContext(c.biomeKey(),c.worldIdentity(),c.sourceIdentity(),c.tableIdentity(),c.originX(),c.originY(),c.originZ(),c.catalogReceipt(),targets,List.of(),true);
    }
    /** Legacy fixture adapter; production callers carry selected-worker projections. */
    public static byte[] encodeLocatedProductionContextPayload(LocatedProductionContext c, String producer, String declaration) {
        return encodeLocatedProductionContextPayload(projectLegacyContext(c),producer,declaration);
    }
    /** Builds the exact empty-target v3 fixture context for non-located fixture rows. */
    static LocatedProductionContext fixtureDefaultContext(long worldSeed,
            String tableKey, long rawSeed, int worldX, int worldY, int worldZ, int containerSize) {
        if (isCampTable(tableKey)) {
            throw new IllegalArgumentException("camp LOOT requires authenticated production context");
        }
        if (!Mc263ContainerLootResolver.supportedTableKeys().contains(tableKey)) {
            throw new IllegalArgumentException("unsupported pinned loot table: " + tableKey);
        }
        if (containerSize != Mc263ContainerLootResolver.supportedContainerSize(tableKey)) {
            throw new IllegalArgumentException("container size does not match pinned loot table");
        }
        if ("minecraft:chests/abandoned_mineshaft".equals(tableKey)) {
            if (worldSeed == 0L && rawSeed == 0L
                    && worldX == -256 && worldY == -20 && worldZ == -384) {
                return fixtureAuthenticatedContext("minecraft:sulfur_caves", Map.of(),
                        worldSeed, tableKey, worldX, worldY, worldZ);
            }
            if (worldSeed == 0L && rawSeed == 0L
                    && worldX == 0 && worldY == 64 && worldZ == 0) {
                return fixtureAuthenticatedContext("minecraft:river", Map.of(),
                        worldSeed, tableKey, worldX, worldY, worldZ);
            }
            if (worldSeed == 0L && rawSeed == Long.MAX_VALUE
                    && worldX == 192 && worldY == -32 && worldZ == -224) {
                return fixtureAuthenticatedContext("minecraft:forest", Map.of(),
                        worldSeed, tableKey, worldX, worldY, worldZ);
            }
            if (worldSeed == 0L && rawSeed == Long.MAX_VALUE
                    && worldX == -256 && worldY == -20 && worldZ == -384) {
                return fixtureAuthenticatedContext("minecraft:sulfur_caves", Map.of(),
                        worldSeed, tableKey, worldX, worldY, worldZ);
            }
        } else if (worldSeed == 0L && (rawSeed == 0L || rawSeed == Long.MAX_VALUE)
                && worldX == 0 && worldY == 64 && worldZ == 0) {
            return fixtureAuthenticatedContext("minecraft:river", Map.of(),
                    worldSeed, tableKey, worldX, worldY, worldZ);
        }
        throw new IllegalArgumentException("fixture has no exact authenticated production context");
    }

    private static LocatedProductionContext fixtureAuthenticatedContext(
            String biomeKey, Map<String, LocatedMapTarget> witnesses,
            long worldSeed, String tableKey, int originX, int originY, int originZ) {
        String worldIdentity = Long.toString(worldSeed);
        String sourceIdentity = Mc263FinalChunkSidecars.CONTAINER_LOOT_PRODUCER_SOURCE_SHA256;
        Map<String, LocatedMapTarget> authenticated = Map.copyOf(witnesses);
        String receipt = Mc263ContainerLootResolver.targetProductionContextReceipt(
                biomeKey, worldIdentity, sourceIdentity, tableKey,
                originX, originY, originZ, authenticated);
        return LocatedProductionContext.authenticated(
                biomeKey, authenticated, worldIdentity, sourceIdentity, tableKey,
                originX, originY, originZ, receipt);
    }

    static byte[] fixtureLocatedProductionContextPayload(long worldId, long worldSeed, int worldX,
            int worldY, int worldZ, String tableKey, long rawSeed,
            CanonicalLootContainerKind containerKind, String definitionFingerprint) {
        LocatedProductionContext context = fixtureDefaultContext(worldSeed,
                tableKey, rawSeed, worldX, worldY, worldZ, containerKind.slots());
        String source = fixtureSourceDeclarationSha256(worldId, worldSeed, worldX, worldY,
                worldZ, tableKey, rawSeed, containerKind, context, definitionFingerprint);
        Map<String, LocatedMapTarget> targets = Map.of();
        String receipt = Mc263ContainerLootResolver.targetProductionContextReceipt(
                context.biomeKey(), context.worldIdentity(), context.sourceIdentity(),
                context.tableIdentity(), context.originX(), context.originY(), context.originZ(),
                targets);
        LocatedProductionContext located = LocatedProductionContext.authenticated(
                context.biomeKey(), targets, context.worldIdentity(), context.sourceIdentity(),
                context.tableIdentity(), context.originX(), context.originY(), context.originZ(),
                receipt);
        return encodeLocatedProductionContextPayload(new LocatedProductionContextPayload(0,
                NeutralFinalChunk.ContainerLootSourceSection.LOOT, 0,
                containerKind.slots(), projectLegacyContext(located),
                Mc263FinalChunkSidecars.CONTAINER_LOOT_PRODUCER_SOURCE_SHA256, source));
    }

    private static String fixtureSourceDeclarationSha256(long worldId, long worldSeed, int worldX,
            int worldY, int worldZ, String tableKey, long rawSeed,
            CanonicalLootContainerKind containerKind,
            LocatedProductionContext context, String definitionFingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FIXTURE_SOURCE_DOMAIN);
            update(digest, worldId);
            update(digest, worldSeed);
            update(digest, worldX);
            update(digest, worldY);
            update(digest, worldZ);
            update(digest, tableKey);
            update(digest, rawSeed);
            update(digest, containerKind.name());
            update(digest, context.biomeKey());
            for (Map.Entry<String, LocatedMapTarget> entry
                    : context.maps().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue().targetReceipt());
            }
            update(digest, definitionFingerprint);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record ValidatedLoot(NeutralFinalChunk.Loot loot, int x, int y, int z,
            CanonicalLootContainerKind kind,
            NeutralFinalChunk.ContainerLootDeclaration declaration, long[] initial) {
    }
}
