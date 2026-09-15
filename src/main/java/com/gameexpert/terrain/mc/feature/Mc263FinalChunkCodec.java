package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.McTerrainDataPin;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedMap;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.ProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.Binding;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.Found;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.LocatedMapTarget;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.NotFound;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Immutable final-live chunk wire carrier. Schema 6 is strictly big-endian:
 * {@code MCF263LC | u16 schema | 32-byte input fingerprint | i32 chunk X/Z | u8 stage(1) |
 * u32 total length | u32 cell count | u16[cell count] IDs | u32 sparse count |
 * (u32 packed,u8 nondefault-state-code)* | 3*i16[256] heightmaps | eleven tagged sections}.
 * Sections are exactly {@code KEYS,BTIK,FTIK,LOOT,SPWN,OWNR,ARCH,BEES,BENT,ENTS,LDEC}; each is a
 * four-byte tag, u32 payload length, then payload. BENT stores block identity, block-entity type,
 * and canonical binary NBT. ENTS stores encounter-ordered type-tagged entity state, motion,
 * optional loot identity, and canonical persisted payload. LDEC carries one authenticated,
 * combined LOOT/ENTS declaration ordinal plus the container size, complete production context,
 * source-row binding and producer source identity. BCAP, SCHD and POST never enter this format.
 */
public final class Mc263FinalChunkCodec {
    public static final int SCHEMA = 6;
    public static final int FULL_POST_RESOLVED = 1;
    /** LOOT has one row per packed cell; loot-bearing ENTS is bounded by the ENTS lane. */
    static final int MAX_CONTAINER_LOOT_DECLARATIONS =
            Math.addExact(Blocks.CHUNK_BLOCKS, Blocks.CHUNK_BLOCKS);
    private static final byte[] MAGIC = "MCF263LC".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINGERPRINT =
            HexFormat.of().parseHex(McTerrainDataPin.INPUT_FINGERPRINT_SHA256);
    private static final HexFormat HEX = HexFormat.of();
    private static final int MAX_CONTEXT_MAPS = 64;
    private static final List<String> TAGS =
            List.of("KEYS", "BTIK", "FTIK", "LOOT", "SPWN", "OWNR", "ARCH", "BEES", "BENT", "ENTS", "LDEC");

    private Mc263FinalChunkCodec() {}

    public record FinalChunk(int chunkX, int chunkZ, short[] blockIds,
                             Map<Integer, Mc263FeatureBlockState> stateOverrides,
                             int[] worldSurfaceWg, int[] oceanFloorWg, int[] motionBlocking,
                             Mc263FinalChunkSidecars sidecars) {
        public FinalChunk {
            blockIds = blockIds.clone();
            stateOverrides = Map.copyOf(stateOverrides);
            worldSurfaceWg = worldSurfaceWg.clone(); oceanFloorWg = oceanFloorWg.clone();
            motionBlocking = motionBlocking.clone();
            if (blockIds.length != Blocks.CHUNK_BLOCKS) throw new IllegalArgumentException("final chunk block count must be " + Blocks.CHUNK_BLOCKS);
            validateHeightmap(worldSurfaceWg); validateHeightmap(oceanFloorWg); validateHeightmap(motionBlocking);
            for (Map.Entry<Integer, Mc263FeatureBlockState> entry : stateOverrides.entrySet()) {
                int packed = entry.getKey();
                if (packed < 0 || packed >= blockIds.length) throw new IllegalArgumentException("state override outside chunk: " + packed);
                Mc263FeatureBlockState state = entry.getValue();
                int id = Short.toUnsignedInt(blockIds[packed]);
                if (state.blockId() != id) throw new IllegalArgumentException("state override ID mismatch at " + packed);
                if (Mc263ExactStateCodec.stateCode(state) == 0) throw new IllegalArgumentException("default state must not be sparse at " + packed);
            }
            sidecars = java.util.Objects.requireNonNull(sidecars);
        }
        @Override public short[] blockIds() { return blockIds.clone(); }
        @Override public Map<Integer, Mc263FeatureBlockState> stateOverrides() { return stateOverrides; }
        @Override public int[] worldSurfaceWg() { return worldSurfaceWg.clone(); }
        @Override public int[] oceanFloorWg() { return oceanFloorWg.clone(); }
        @Override public int[] motionBlocking() { return motionBlocking.clone(); }
        Mc263FeatureBlockState stateAt(int packed) {
            Mc263FeatureBlockState state = stateOverrides.get(packed);
            return state != null ? state : Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(blockIds[packed]));
        }
    }

    public static byte[] encode(FinalChunk chunk) {
        return encode(chunk, true, false, null);
    }

    /** Encodes against an already-authenticated integrated build receipt. */
    public static byte[] encode(FinalChunk chunk,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        return encode(chunk, true, false, integratedBuildReceipt);
    }

    /** Explicit legacy variant-0 fixture encoder; production must use {@link #encode}. */
    public static byte[] encodeFixture(FinalChunk chunk) {
        return encode(chunk, true, true, null);
    }

    /**
     * Runs every fail-closed check {@link #encode} runs, without materialising its bytes.
     *
     * <p>AGENTS 10l: a caller that encodes only to validate paid a 220 KB buffer, a 196 KB
     * big-endian block buffer and their copies for a result it discarded — eighteen times per
     * canonical chunk on the mineshaft settlement path alone. This entry point is the same code
     * with the same checks in the same order, writing to a null sink, so it cannot drift from the
     * encoder it stands in for.</p>
     */
    public static void validate(FinalChunk chunk) {
        encode(chunk, false, false, null);
    }

    /** Validates against an already-authenticated integrated build receipt. */
    public static void validate(FinalChunk chunk,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        encode(chunk, false, false, integratedBuildReceipt);
    }

    public static void validateFixture(FinalChunk chunk) {
        encode(chunk, false, true, null);
    }

    private static byte[] encode(FinalChunk chunk, boolean emit, boolean allowLegacyFixture,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        validateSidecars(chunk, integratedBuildReceipt);
        try {
            ByteArrayOutputStream bytes = emit ? new ByteArrayOutputStream(220_000) : null;
            DataOutputStream out = new DataOutputStream(
                    emit ? bytes : OutputStream.nullOutputStream());
            out.write(MAGIC); out.writeShort(SCHEMA); out.write(FINGERPRINT);
            out.writeInt(chunk.chunkX()); out.writeInt(chunk.chunkZ()); out.writeByte(FULL_POST_RESOLVED);
            out.writeInt(0);
            out.writeInt(Blocks.CHUNK_BLOCKS);
            short[] ids = chunk.blockIds;
            // AGENTS rule 10l: 98,304 single writeShort calls per encoded chunk went through
            // DataOutputStream's counter and ByteArrayOutputStream's grow check one word at a
            // time. writeShort emits the high byte then the low byte, so the buffer below is the
            // identical big-endian stream written in one bulk copy. The fail-closed defaultForId
            // probe is unchanged in set and in order; a chunk is dominated by long runs of one
            // ID, and re-probing an ID already accepted in this loop cannot decide differently.
            byte[] packedIds = emit ? new byte[Math.multiplyExact(ids.length, 2)] : null;
            int previousId = -1;
            for (int index = 0; index < ids.length; index++) {
                int id = Short.toUnsignedInt(ids[index]);
                if (id != previousId) {
                    Mc263FeatureBlockState.defaultForId(id);
                    previousId = id;
                }
                if (packedIds != null) {
                    packedIds[index * 2] = (byte) (id >>> 8);
                    packedIds[index * 2 + 1] = (byte) id;
                }
            }
            if (packedIds != null) out.write(packedIds);
            List<Map.Entry<Integer, Mc263FeatureBlockState>> sparse = chunk.stateOverrides.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
            out.writeInt(sparse.size());
            for (Map.Entry<Integer, Mc263FeatureBlockState> entry : sparse) { out.writeInt(entry.getKey()); out.writeByte(Mc263ExactStateCodec.stateCode(entry.getValue())); }
            writeHeightmap(out, chunk.worldSurfaceWg); writeHeightmap(out, chunk.oceanFloorWg); writeHeightmap(out, chunk.motionBlocking);
            KeyTable keys = KeyTable.of(chunk.sidecars);
            writeSection(out, "KEYS", keys.payload());
            writeSection(out, "BTIK", blockTicks(chunk.sidecars, keys));
            writeSection(out, "FTIK", fluidTicks(chunk.sidecars, keys));
            writeSection(out, "LOOT", loot(chunk.sidecars, keys));
            writeSection(out, "SPWN", spawners(chunk.sidecars, keys));
            writeSection(out, "OWNR", owners(chunk.sidecars));
            writeSection(out, "ARCH", archaeology(chunk.sidecars, keys));
            writeSection(out, "BEES", bees(chunk.sidecars));
            writeSection(out, "BENT", blockEntities(chunk.sidecars, keys));
            writeSection(out, "ENTS", entities(chunk.sidecars, keys));
            writeSection(out, "LDEC", lootDeclarations(chunk.sidecars, allowLegacyFixture));
            out.flush();
            if (!emit) return null;
            byte[] result = bytes.toByteArray();
            putInt(result, 51, result.length);
            return result;
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static FinalChunk decode(byte[] bytes) {
        return decode(bytes, false, null);
    }

    /** Decodes against an already-authenticated integrated build receipt. */
    public static FinalChunk decode(byte[] bytes,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        return decode(bytes, false, integratedBuildReceipt);
    }

    /** Explicit legacy variant-0 fixture decoder; production must use {@link #decode}. */
    public static FinalChunk decodeFixture(byte[] bytes) {
        return decode(bytes, true, null);
    }

    private static FinalChunk decode(byte[] bytes, boolean allowLegacyFixture,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (!Arrays.equals(in.readNBytes(8), MAGIC)) throw invalid("bad magic");
            if (in.readUnsignedShort() != SCHEMA) throw invalid("unsupported schema");
            if (!Arrays.equals(in.readNBytes(32), FINGERPRINT)) throw invalid("input fingerprint mismatch");
            int chunkX = in.readInt(), chunkZ = in.readInt();
            if (in.readUnsignedByte() != FULL_POST_RESOLVED) throw invalid("invalid stage");
            if (Integer.toUnsignedLong(in.readInt()) != bytes.length) throw invalid("total length mismatch");
            if (in.readInt() != Blocks.CHUNK_BLOCKS) throw invalid("cell count mismatch");
            short[] ids = new short[Blocks.CHUNK_BLOCKS];
            for (int i = 0; i < ids.length; i++) { ids[i] = (short) in.readUnsignedShort(); Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(ids[i])); }
            long sparseCount = Integer.toUnsignedLong(in.readInt());
            if (sparseCount > Blocks.CHUNK_BLOCKS) throw invalid("sparse count exceeds chunk");
            Map<Integer, Mc263FeatureBlockState> sparse = new LinkedHashMap<>();
            int previous = -1;
            for (long i = 0; i < sparseCount; i++) {
                int packed = checkedPacked(in.readInt()); int code = in.readUnsignedByte();
                if (packed <= previous || code == 0) throw invalid("noncanonical sparse state order/code");
                Mc263FeatureBlockState state = Mc263ExactStateCodec.decode(Short.toUnsignedInt(ids[packed]), code);
                sparse.put(packed, state); previous = packed;
            }
            int[] ws = readHeightmap(in), of = readHeightmap(in), mb = readHeightmap(in);
            Map<String, byte[]> sections = new LinkedHashMap<>();
            for (String expected : TAGS) {
                byte[] tagBytes = in.readNBytes(4);
                if (tagBytes.length != 4) throw new EOFException();
                String tag = decodeAscii(tagBytes, "section tag");
                if (!tag.equals(expected)) throw invalid("section order mismatch: " + tag);
                long length = Integer.toUnsignedLong(in.readInt());
                if (length > in.available()) throw invalid("section length exceeds carrier");
                sections.put(tag, in.readNBytes((int) length));
            }
            if (in.available() != 0) throw invalid("trailing bytes");
            List<String> keys = readKeys(sections.get("KEYS"));
            Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(
                    readBlockTicks(sections.get("BTIK"), keys), readFluidTicks(sections.get("FTIK"), keys),
                    readLoot(sections.get("LOOT"), keys), readSpawners(sections.get("SPWN"), keys),
                    readOwners(sections.get("OWNR")), readArchaeology(sections.get("ARCH"), keys),
                    readBees(sections.get("BEES")), readBlockEntities(sections.get("BENT"), keys),
                    readEntities(sections.get("ENTS"), keys),
                    readLootDeclarations(sections.get("LDEC"), allowLegacyFixture));
            FinalChunk chunk = new FinalChunk(chunkX, chunkZ, ids, sparse, ws, of, mb, sidecars);
            validateSidecars(chunk, integratedBuildReceipt);
            if (!Arrays.equals(bytes, encode(chunk, true, allowLegacyFixture,
                    integratedBuildReceipt))) {
                throw invalid("noncanonical carrier encoding");
            }
            return chunk;
        } catch (EOFException truncated) { throw invalid("truncated carrier", truncated);
        } catch (IOException impossible) { throw invalid("malformed carrier", impossible); }
    }

    private static void validateSidecars(FinalChunk chunk,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        if (chunk.sidecars.loot().size() > Blocks.CHUNK_BLOCKS) {
            throw invalid("LOOT count exceeds packed-cell capacity");
        }
        if (chunk.sidecars.entities().size() > Blocks.CHUNK_BLOCKS) {
            throw invalid("ENTS count exceeds chunk capacity");
        }
        requireLootDeclarationCount(chunk.sidecars.containerLootDeclarations().size());
        requireUnique(chunk.sidecars.blockTicks().stream()
                .map(value -> value.packed() + "\0" + value.key()).toList(), "block tick");
        requireUnique(chunk.sidecars.fluidTicks().stream()
                .map(value -> List.of(value.packed(), value.key(), value.subTickOrder())).toList(),
                "fluid tick");
        requireUnique(chunk.sidecars.loot().stream().map(Mc263FinalChunkSidecars.Loot::packed)
                .toList(), "loot");
        requireUnique(chunk.sidecars.spawners().stream()
                .map(Mc263FinalChunkSidecars.Spawner::packed).toList(), "spawner");
        requireUnique(chunk.sidecars.owners().stream().map(Mc263FinalChunkSidecars.Owner::packed)
                .toList(), "owner");
        requireUnique(chunk.sidecars.archaeology().stream()
                .map(Mc263FinalChunkSidecars.Archaeology::packed).toList(), "archaeology");
        requireUnique(chunk.sidecars.bees().stream().map(Mc263FinalChunkSidecars.BeeNest::packed)
                .toList(), "bee nest");
        requireUnique(chunk.sidecars.blockEntities().stream()
                .map(Mc263FinalChunkSidecars.BlockEntity::packed).toList(), "block entity");
        requireUnique(chunk.sidecars.entities(), "structure entity");
        validateLootDeclarations(chunk, integratedBuildReceipt);
        for (Mc263FinalChunkSidecars.BlockTick tick : chunk.sidecars.blockTicks()) {
            if (tick.key().startsWith("webcraft:")) {
                if (!Mc263FeatureBlockState.legacyTickKeyMatches(tick.blockId(), tick.key())) throw invalid("block tick ID/key mismatch");
            } else if (Mc263FeatureBlockState.forSemanticBlockKey(tick.key()).blockId() != tick.blockId()) throw invalid("block tick ID/key mismatch");
        }
        for (Mc263FinalChunkSidecars.FluidTick tick : chunk.sidecars.fluidTicks()) Mc263FeatureBlockState.requireFluidTickKey(tick.key());
        for (Mc263FinalChunkSidecars.Loot value : chunk.sidecars.loot()) {
            // 장식용 항아리의 전리품은 블록 엔티티 속성이라 RANDOMIZABLE_CONTAINER 능력을 갖지 않는다.
            if (isBlockEntityLootContainer(chunk.stateAt(value.packed()))) continue;
            capability(chunk, value.packed(), Mc263FeatureBlockState.Capability.RANDOMIZABLE_CONTAINER, "loot");
        }
        for (Mc263FinalChunkSidecars.Spawner value : chunk.sidecars.spawners()) capability(chunk, value.packed(), Mc263FeatureBlockState.Capability.SPAWNER, "spawner");
        for (Mc263FinalChunkSidecars.Archaeology value : chunk.sidecars.archaeology()) capability(chunk, value.packed(), Mc263FeatureBlockState.Capability.BRUSHABLE, "archaeology");
        for (Mc263FinalChunkSidecars.BeeNest value : chunk.sidecars.bees()) capability(chunk, value.packed(), Mc263FeatureBlockState.Capability.BEEHIVE, "bees");
        for (Mc263FinalChunkSidecars.BlockEntity value : chunk.sidecars.blockEntities()) {
            if (!chunk.stateAt(value.packed()).blockKey().equals(value.blockIdentity())) {
                throw invalid("block entity block identity mismatch at " + value.packed());
            }
        }
        for (Mc263FinalChunkSidecars.StructureEntity value : chunk.sidecars.entities()) {
            long blockX = (long) Math.floor(value.x()), blockZ = (long) Math.floor(value.z());
            if (Math.floorDiv(blockX, 16L) != chunk.chunkX()
                    || Math.floorDiv(blockZ, 16L) != chunk.chunkZ()) {
                throw invalid("structure entity position outside carrier chunk");
            }
            if (Math.floor(value.y()) < Blocks.MIN_Y || Math.floor(value.y()) > Blocks.MAX_Y) {
                throw invalid("structure entity Y outside generation range");
            }
        }
    }
    /**
     * 장식용 항아리는 1칸 전리품 컨테이너지만, 전리품이 블록 엔티티 NBT 속성이라
     * chest facing 도 RANDOMIZABLE_CONTAINER 능력도 갖지 않는다.
     */
    private static boolean isBlockEntityLootContainer(Mc263FeatureBlockState state) {
        return state.blockKey().equals("minecraft:decorated_pot");
    }

    private static void requireUnique(List<?> values, String name) {
        if (new java.util.HashSet<>(values).size() != values.size()) {
            throw invalid("duplicate " + name + " sidecar");
        }
    }
    private static void capability(FinalChunk chunk, int packed, Mc263FeatureBlockState.Capability expected, String sidecar) {
        if (chunk.stateAt(packed).capability() != expected) throw invalid(sidecar + " sidecar capability was overwritten at " + packed);
    }

    private static void validateLootDeclarations(FinalChunk chunk,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        List<Mc263FinalChunkSidecars.Loot> loot = chunk.sidecars.loot().stream()
                .sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Loot::packed)).toList();
        List<Mc263FinalChunkSidecars.StructureEntity> entities = chunk.sidecars.entities();
        List<Mc263FinalChunkSidecars.ContainerLootDeclaration> declarations =
                chunk.sidecars.containerLootDeclarations();
        int expectedCount = Math.addExact(loot.size(), Math.toIntExact(entities.stream()
                .filter(value -> !value.lootTable().isEmpty()).count()));
        if (declarations.size() != expectedCount) {
            throw invalid("final LOOT/ENTS declaration count mismatch");
        }
        String producerSourceSha256 = expectedCount == 0 ? null
                : authenticatedProducerSourceSha256(integratedBuildReceipt);
        int declarationIndex = 0;
        for (int sectionOrdinal = 0; sectionOrdinal < loot.size(); sectionOrdinal++) {
            var declaration = declarations.get(declarationIndex);
            var row = loot.get(sectionOrdinal);
            requireDeclarationIdentity(declaration, producerSourceSha256, declarationIndex,
                    Mc263FinalChunkSidecars.ContainerLootSourceSection.LOOT, sectionOrdinal);
            Mc263FeatureBlockState finalState = chunk.stateAt(row.packed());
            // 항아리는 chest facing 이 없으므로 방향을 대조하지 않는다.
            if (!isBlockEntityLootContainer(finalState)
                    && !row.facing().equals(finalState.chestFacing())) {
                throw invalid("LOOT declaration facing/final-state mismatch");
            }
            if (declaration.containerSize() != Mc263FinalChunkAssembler.containerSize(
                    finalState.blockKey())) {
                throw invalid("LOOT declaration container size mismatch");
            }
            String expected = Mc263FinalChunkSidecars.sourceDeclarationBinding(
                    declarationIndex, sectionOrdinal, chunk.chunkX(), chunk.chunkZ(), row,
                    declaration.containerSize(), declaration.productionContext(),
                    declaration.producerSourceSha256());
            if (!expected.equals(declaration.sourceDeclarationSha256())) {
                throw invalid("LOOT source declaration binding mismatch");
            }
            declarationIndex++;
        }
        for (int sectionOrdinal = 0; sectionOrdinal < entities.size(); sectionOrdinal++) {
            var row = entities.get(sectionOrdinal);
            if (row.lootTable().isEmpty()) continue;
            var declaration = declarations.get(declarationIndex);
            requireDeclarationIdentity(declaration, producerSourceSha256, declarationIndex,
                    Mc263FinalChunkSidecars.ContainerLootSourceSection.ENTS, sectionOrdinal);
            if (declaration.containerSize() != Mc263FinalChunkAssembler.entityContainerSize(
                    row.entityKey())) {
                throw invalid("ENTS declaration container size mismatch");
            }
            String expected = Mc263FinalChunkSidecars.sourceDeclarationBinding(
                    declarationIndex, sectionOrdinal, chunk.chunkX(), chunk.chunkZ(), row,
                    declaration.containerSize(), declaration.productionContext(),
                    declaration.producerSourceSha256());
            if (!expected.equals(declaration.sourceDeclarationSha256())) {
                throw invalid("ENTS source declaration binding mismatch");
            }
            declarationIndex++;
        }
    }

    private static void requireDeclarationIdentity(
            Mc263FinalChunkSidecars.ContainerLootDeclaration declaration,
            String producerSourceSha256, int ordinal,
            Mc263FinalChunkSidecars.ContainerLootSourceSection section, int sectionOrdinal) {
        if (declaration.ordinal() != ordinal || declaration.sourceSection() != section
                || declaration.sourceSectionOrdinal() != sectionOrdinal) {
            throw invalid("LOOT/ENTS declaration ordinal drift");
        }
        if (!declaration.producerSourceSha256().equals(producerSourceSha256)) {
            throw invalid("container loot producer source identity mismatch");
        }
    }

    private static String authenticatedProducerSourceSha256(
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        if (integratedBuildReceipt == null) {
            integratedBuildReceipt = Mc263FinalChunkSidecars.currentIntegratedBuildReceipt();
        }
        integratedBuildReceipt.verifyAgainstCurrentBuild();
        return integratedBuildReceipt.producerSourceSha256();
    }

    private static byte[] blockTicks(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.blockTicks(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeShort(v.blockId());out.writeShort(k.index(v.key()));out.writeInt(v.delay());out.writeByte(v.priority().value());out.writeLong(v.subTickOrder());}}); }
    private static byte[] fluidTicks(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.fluidTicks(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeShort(k.index(v.key()));out.writeInt(v.delay());out.writeByte(v.priority().value());out.writeLong(v.subTickOrder());}}); }
    private static byte[] loot(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.loot().stream().sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Loot::packed)).toList(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeByte(facing(v.facing()));out.writeShort(k.index(v.table()));out.writeLong(v.seed());}}); }
    private static byte[] spawners(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.spawners().stream().sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Spawner::packed)).toList(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeShort(k.index(v.entityType()));}}); }
    private static byte[] owners(Mc263FinalChunkSidecars s) throws IOException { return payload(out -> { var values=s.owners().stream().sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Owner::packed)).toList(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeLong(v.owner());}}); }
    private static byte[] archaeology(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.archaeology().stream().sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Archaeology::packed)).toList(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeShort(k.index(v.table()));out.writeLong(v.seed());}}); }
    private static byte[] bees(Mc263FinalChunkSidecars s) throws IOException { return payload(out -> { var values=s.bees().stream().sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.BeeNest::packed)).toList(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());int occupants=v.ticksInHive().size();if(occupants>0xffff)throw invalid("bee occupant count exceeds unsigned-16: "+occupants);out.writeShort(occupants);for(int tick:v.ticksInHive())out.writeShort(tick);}}); }
    private static byte[] blockEntities(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.blockEntities().stream().sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.BlockEntity::packed)).toList(); out.writeInt(values.size()); for(var v:values){out.writeInt(v.packed());out.writeShort(k.index(v.blockIdentity()));out.writeShort(k.index(v.entityType()));byte[] nbt=v.canonicalNbt();out.writeInt(nbt.length);out.write(nbt);}}); }
    private static byte[] entities(Mc263FinalChunkSidecars s, KeyTable k) throws IOException { return payload(out -> { var values=s.entities();out.writeInt(values.size());for(var v:values){out.writeShort(k.index(v.entityKey()));out.writeShort(k.index(v.spawnReason()));out.writeDouble(v.x());out.writeDouble(v.y());out.writeDouble(v.z());out.writeFloat(v.yaw());out.writeFloat(v.pitch());out.writeDouble(v.velocityX());out.writeDouble(v.velocityY());out.writeDouble(v.velocityZ());out.writeShort(v.lootTable().isEmpty()?0xffff:k.index(v.lootTable()));out.writeLong(v.lootSeed());byte[] entityPayload=v.canonicalPayload();out.writeInt(entityPayload.length);out.write(entityPayload);}}); }

    private static byte[] lootDeclarations(Mc263FinalChunkSidecars s,
            boolean allowLegacyFixture) throws IOException {
        return payload(out -> {
            var values = s.containerLootDeclarations();
            requireLootDeclarationCount(values.size());
            out.writeInt(values.size());
            for (var value : values) {
                out.writeInt(value.ordinal()); out.writeByte(value.sourceSection().ordinal());
                out.writeInt(value.sourceSectionOrdinal()); out.writeShort(value.containerSize());
                writeProductionContext(out, value.productionContext(), allowLegacyFixture);
                writeSha256(out, value.producerSourceSha256(), "producer source identity");
                writeSha256(out, value.sourceDeclarationSha256(),
                        "loot source declaration identity");
            }
        });
    }

    private static List<String> readKeys(byte[] p)throws IOException{return readPayload(p,in->{int n=in.readUnsignedShort();List<String> r=new ArrayList<>();String prev=null;for(int i=0;i<n;i++){String v=readString(in);if(prev!=null&&prev.compareTo(v)>=0)throw invalid("duplicate/unsorted key");r.add(v);prev=v;}return List.copyOf(r);});}
    private static List<Mc263FinalChunkSidecars.BlockTick> readBlockTicks(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"block ticks");var r=new ArrayList<Mc263FinalChunkSidecars.BlockTick>();for(int i=0;i<n;i++)r.add(new Mc263FinalChunkSidecars.BlockTick(checkedPacked(in.readInt()),in.readUnsignedShort(),key(k,in.readUnsignedShort()),in.readInt(),Mc263FinalChunkSidecars.TickPriority.fromValue(in.readByte()),in.readLong()));return r;});}
    private static List<Mc263FinalChunkSidecars.FluidTick> readFluidTicks(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"fluid ticks");var r=new ArrayList<Mc263FinalChunkSidecars.FluidTick>();for(int i=0;i<n;i++)r.add(new Mc263FinalChunkSidecars.FluidTick(checkedPacked(in.readInt()),key(k,in.readUnsignedShort()),in.readInt(),Mc263FinalChunkSidecars.TickPriority.fromValue(in.readByte()),in.readLong()));return r;});}
    private static List<Mc263FinalChunkSidecars.Loot> readLoot(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"loot");var r=new ArrayList<Mc263FinalChunkSidecars.Loot>();for(int i=0;i<n;i++)r.add(new Mc263FinalChunkSidecars.Loot(checkedPacked(in.readInt()),facing(in.readUnsignedByte()),key(k,in.readUnsignedShort()),in.readLong()));return r;});}
    private static List<Mc263FinalChunkSidecars.Spawner> readSpawners(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"spawners");var r=new ArrayList<Mc263FinalChunkSidecars.Spawner>();for(int i=0;i<n;i++)r.add(new Mc263FinalChunkSidecars.Spawner(checkedPacked(in.readInt()),key(k,in.readUnsignedShort())));return r;});}
    private static List<Mc263FinalChunkSidecars.Owner> readOwners(byte[]p)throws IOException{return readPayload(p,in->{int n=count(in,"owners");var r=new ArrayList<Mc263FinalChunkSidecars.Owner>();for(int i=0;i<n;i++)r.add(new Mc263FinalChunkSidecars.Owner(checkedPacked(in.readInt()),in.readLong()));return r;});}
    private static List<Mc263FinalChunkSidecars.Archaeology> readArchaeology(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"archaeology");var r=new ArrayList<Mc263FinalChunkSidecars.Archaeology>();for(int i=0;i<n;i++)r.add(new Mc263FinalChunkSidecars.Archaeology(checkedPacked(in.readInt()),key(k,in.readUnsignedShort()),in.readLong()));return r;});}
    private static List<Mc263FinalChunkSidecars.BeeNest> readBees(byte[]p)throws IOException{return readPayload(p,in->{int n=count(in,"bees");var r=new ArrayList<Mc263FinalChunkSidecars.BeeNest>();for(int i=0;i<n;i++){int packed=checkedPacked(in.readInt()), c=in.readUnsignedShort();List<Integer> ticks=new ArrayList<>();for(int j=0;j<c;j++)ticks.add(in.readUnsignedShort());r.add(new Mc263FinalChunkSidecars.BeeNest(packed,ticks));}return r;});}
    private static List<Mc263FinalChunkSidecars.BlockEntity> readBlockEntities(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"block entities");var r=new ArrayList<Mc263FinalChunkSidecars.BlockEntity>();int previous=-1;for(int i=0;i<n;i++){int packed=checkedPacked(in.readInt());if(packed<=previous)throw invalid("noncanonical block entity order");String block=key(k,in.readUnsignedShort()),type=key(k,in.readUnsignedShort());int length=boundedPayloadLength(in.readInt());byte[] nbt=in.readNBytes(length);if(nbt.length!=length)throw new EOFException();r.add(new Mc263FinalChunkSidecars.BlockEntity(packed,block,type,nbt));previous=packed;}return r;});}
    private static List<Mc263FinalChunkSidecars.StructureEntity> readEntities(byte[]p,List<String>k)throws IOException{return readPayload(p,in->{int n=count(in,"entities");var r=new ArrayList<Mc263FinalChunkSidecars.StructureEntity>();for(int i=0;i<n;i++){String entity=key(k,in.readUnsignedShort()),reason=key(k,in.readUnsignedShort());double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();float yaw=in.readFloat(),pitch=in.readFloat();double vx=in.readDouble(),vy=in.readDouble(),vz=in.readDouble();int lootIndex=in.readUnsignedShort();String loot=lootIndex==0xffff?"":key(k,lootIndex);long seed=in.readLong();int length=boundedPayloadLength(in.readInt());byte[] entityPayload=in.readNBytes(length);if(entityPayload.length!=length)throw new EOFException();r.add(new Mc263FinalChunkSidecars.StructureEntity(entity,reason,x,y,z,yaw,pitch,vx,vy,vz,loot,seed,entityPayload));}return r;});}

    private static List<Mc263FinalChunkSidecars.ContainerLootDeclaration>
            readLootDeclarations(byte[] payload, boolean allowLegacyFixture) throws IOException {
        return readPayload(payload, in -> {
            int count = requireLootDeclarationCount(Integer.toUnsignedLong(in.readInt()));
            var result = new ArrayList<Mc263FinalChunkSidecars.ContainerLootDeclaration>(count);
            for (int index = 0; index < count; index++) {
                int ordinal = in.readInt();
                int sectionCode = in.readUnsignedByte();
                if (sectionCode >= Mc263FinalChunkSidecars.ContainerLootSourceSection.values().length) {
                    throw invalid("loot declaration source section code drift");
                }
                var section = Mc263FinalChunkSidecars.ContainerLootSourceSection.values()[sectionCode];
                int sectionOrdinal = in.readInt(); int containerSize = in.readUnsignedShort();
                LootProductionContext context = readProductionContext(in, allowLegacyFixture);
                String producer = readSha256(in); String row = readSha256(in);
                result.add(new Mc263FinalChunkSidecars.ContainerLootDeclaration(ordinal, section,
                        sectionOrdinal, containerSize, context, producer, row));
            }
            return List.copyOf(result);
        });
    }

    /**
     * AGENTS rule 10l: a KEYS table is rebuilt for every encoded chunk on the warm seam. Its
     * {@code values} already come out of a {@link TreeSet} in the codec's canonical order, so the
     * index of a key is its position in that sorted list — the former {@code HashMap} plus
     * {@code Map.copyOf} rebuilt a second, boxed index of the same ordering on every encode.
     * A binary search answers the identical question with no map, no boxing and no copy, and the
     * emitted indices are unchanged because the ordering is unchanged.
     */
    private record KeyTable(List<String> values) {
        static KeyTable of(Mc263FinalChunkSidecars s){TreeSet<String> set=new TreeSet<>();s.blockTicks().forEach(v->set.add(v.key()));s.fluidTicks().forEach(v->set.add(v.key()));s.loot().forEach(v->set.add(v.table()));s.spawners().forEach(v->set.add(v.entityType()));s.archaeology().forEach(v->set.add(v.table()));s.blockEntities().forEach(v->{set.add(v.blockIdentity());set.add(v.entityType());});s.entities().forEach(v->{set.add(v.entityKey());set.add(v.spawnReason());if(!v.lootTable().isEmpty())set.add(v.lootTable());});List<String> values=List.copyOf(set);if(values.size()>0xffff)throw invalid("too many keys");return new KeyTable(values);}
        int index(String key){int found=Collections.binarySearch(values,key);if(found<0)throw invalid("key outside KEYS: "+key);return found;}
        byte[] payload()throws IOException{return Mc263FinalChunkCodec.payload(out->{out.writeShort(values.size());for(String value:values)writeString(out,value);});}
    }
    static void writeProductionContext(DataOutputStream out, LootProductionContext context)
            throws IOException {
        writeProductionContext(out, context, false);
    }

    static void writeFixtureProductionContext(DataOutputStream out, LootProductionContext context)
            throws IOException {
        writeProductionContext(out, context, true);
    }

    private static void writeProductionContext(DataOutputStream out, LootProductionContext context,
            boolean allowLegacyFixture) throws IOException {
        Objects.requireNonNull(context, "loot declaration production context");
        if (context instanceof LocatedProductionContext located) {
            out.writeByte(1);
            writeLocatedProductionContext(out, located);
        } else {
            if (!allowLegacyFixture) {
                throw invalid("schema-6 production LDEC rejects fixture-only context variant 0");
            }
            out.writeByte(0);
            transferProductionContext(ContextIo.writing(out), (ProductionContext) context);
        }
    }

    private static LootProductionContext readProductionContext(DataInputStream in)
            throws IOException {
        return readProductionContext(in, false);
    }

    private static LootProductionContext readProductionContext(DataInputStream in,
            boolean allowLegacyFixture) throws IOException {
        int variant = in.readUnsignedByte();
        return switch (variant) {
            case 0 -> {
                if (!allowLegacyFixture) {
                    throw invalid(
                            "schema-6 production LDEC rejects fixture-only context variant 0");
                }
                yield transferProductionContext(ContextIo.reading(in), null);
            }
            case 1 -> readLocatedProductionContext(in);
            default -> throw invalid("unknown loot production context variant");
        };
    }

    /** One bidirectional declaration of the fixture-v1 context/map field grammar. */
    private static ProductionContext transferProductionContext(ContextIo wire,
            ProductionContext context) throws IOException {
        boolean reading = wire.isReading();
        List<Map.Entry<String, LocatedMap>> ordered = reading ? List.of()
                : validatedContextMaps(context);
        String biome = wire.text(reading ? null : context.biomeKey(), TextKind.RESOURCE_KEY);
        String worldIdentity = wire.text(reading ? null : context.worldIdentity(), TextKind.ASCII);
        String sourceIdentity = wire.text(reading ? null : context.sourceIdentity(), TextKind.ASCII);
        String tableIdentity = wire.text(reading ? null : context.tableIdentity(),
                TextKind.RESOURCE_KEY);
        int originX = wire.integer(reading ? 0 : context.originX());
        int originY = wire.integer(reading ? 0 : context.originY());
        int originZ = wire.integer(reading ? 0 : context.originZ());
        String catalogReceipt = wire.receipt(reading ? null : context.catalogReceipt(),
                "production context catalog receipt");
        int mapCount = wire.mapCount(ordered.size());
        Map<String, LocatedMap> maps = new LinkedHashMap<>();
        String previous = null;
        for (int index = 0; index < mapCount; index++) {
            Map.Entry<String, LocatedMap> entry = reading ? null : ordered.get(index);
            LocatedMap source = reading ? null : entry.getValue();
            String key = wire.text(reading ? null : entry.getKey(), TextKind.TOKEN);
            String destination = wire.text(reading ? null : source.destination(), TextKind.TOKEN);
            String mapWorldIdentity = wire.text(reading ? null : source.worldIdentity(),
                    TextKind.ASCII);
            String mapSourceIdentity = wire.text(reading ? null : source.sourceIdentity(),
                    TextKind.ASCII);
            String mapTableIdentity = wire.text(reading ? null : source.tableIdentity(),
                    TextKind.RESOURCE_KEY);
            int mapId = wire.integer(reading ? 0 : source.mapId());
            int centerX = wire.integer(reading ? 0 : source.centerX());
            int centerZ = wire.integer(reading ? 0 : source.centerZ());
            int mapOriginX = wire.integer(reading ? 0 : source.originX());
            int mapOriginY = wire.integer(reading ? 0 : source.originY());
            int mapOriginZ = wire.integer(reading ? 0 : source.originZ());
            int scale = wire.integer(reading ? 0 : source.scale());
            String mapReceipt = wire.receipt(reading ? null : source.resolverCatalogReceipt(),
                    "map destination receipt");
            if (previous != null && previous.compareTo(key) >= 0) {
                throw invalid("loot declaration maps are duplicate or unsorted");
            }
            LocatedMap map = reading ? new LocatedMap(destination, mapWorldIdentity,
                    mapSourceIdentity, mapTableIdentity, mapId, centerX, centerZ, mapOriginX,
                    mapOriginY, mapOriginZ, scale, mapReceipt) : source;
            maps.put(key, map);
            previous = key;
        }
        if (!reading) return context;
        return ProductionContext.authenticated(biome, maps, worldIdentity, sourceIdentity,
                tableIdentity, originX, originY, originZ, catalogReceipt);
    }

    private static List<Map.Entry<String, LocatedMap>> validatedContextMaps(
            ProductionContext context) {
        Objects.requireNonNull(context, "loot declaration production context");
        List<Map.Entry<String, LocatedMap>> maps = context.maps().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        for (var entry : maps) {
            entry.getValue().requireAuthenticated();
        }
        if (!context.isAuthenticated()) {
            throw invalid("loot declaration production context is unauthenticated");
        }
        return maps;
    }

    private static void writeLocatedProductionContext(DataOutputStream out,
            LocatedProductionContext context) throws IOException {
        context.requireAuthenticatedContext();
        writeString(out, context.biomeKey());
        writeContextString(out, context.worldIdentity());
        writeContextString(out, context.sourceIdentity());
        writeString(out, context.tableIdentity());
        out.writeInt(context.originX()); out.writeInt(context.originY());
        out.writeInt(context.originZ());
        writeSha256(out, context.catalogReceipt(), "located production context receipt");
        List<Map.Entry<String, LocatedMapTarget>> targets = context.maps().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        if (targets.size() > MAX_CONTEXT_MAPS) {
            throw invalid("loot declaration target count exceeds " + MAX_CONTEXT_MAPS);
        }
        out.writeShort(targets.size());
        String previous = null;
        for (var entry : targets) {
            if (previous != null && previous.compareTo(entry.getKey()) >= 0) {
                throw invalid("loot declaration targets are duplicate or unsorted");
            }
            LocatedMapTarget target = entry.getValue();
            target.requireAuthenticated();
            Binding binding = target.binding();
            writeToken(out, entry.getKey());
            out.writeByte(target instanceof Found ? 1 : 0);
            writeContextString(out, binding.destinationTag());
            writeString(out, binding.structureSet());
            if (binding.acceptedMembers().isEmpty()
                    || binding.acceptedMembers().size() > MAX_CONTEXT_MAPS) {
                throw invalid("located-map member count outside bounds");
            }
            out.writeShort(binding.acceptedMembers().size());
            for (String member : binding.acceptedMembers()) writeString(out, member);
            out.writeInt(binding.scale()); out.writeInt(binding.searchRadius());
            out.writeBoolean(binding.skipExistingChunks());
            writeSha256(out, binding.locatorSourceReceipt(), "locator source receipt");
            writeNonzeroSha256(out, binding.referenceSnapshotReceipt(),
                    "reference snapshot receipt");
            if (target instanceof Found found) {
                out.writeInt(found.targetX()); out.writeInt(found.targetZ());
                out.writeInt(found.savedCenterX()); out.writeInt(found.savedCenterZ());
                writeSha256(out, found.previewSha256(), "map preview receipt");
            }
            writeSha256(out, target.targetReceipt(), "map target receipt");
            previous = entry.getKey();
        }
    }

    private static LocatedProductionContext readLocatedProductionContext(DataInputStream in)
            throws IOException {
        String biome = readString(in);
        String worldIdentity = readContextString(in);
        String sourceIdentity = readContextString(in);
        String tableIdentity = readString(in);
        int originX = in.readInt(); int originY = in.readInt(); int originZ = in.readInt();
        String contextReceipt = readSha256(in);
        int count = in.readUnsignedShort();
        if (count > MAX_CONTEXT_MAPS) {
            throw invalid("loot declaration target count exceeds " + MAX_CONTEXT_MAPS);
        }
        LinkedHashMap<String, LocatedMapTarget> targets = new LinkedHashMap<>();
        String previous = null;
        for (int index = 0; index < count; index++) {
            String destination = readToken(in);
            if (previous != null && previous.compareTo(destination) >= 0) {
                throw invalid("loot declaration targets are duplicate or unsorted");
            }
            int variant = in.readUnsignedByte();
            if (variant != 0 && variant != 1) throw invalid("unknown located-map target variant");
            String destinationTag = readContextString(in);
            String structureSet = readString(in);
            int memberCount = in.readUnsignedShort();
            if (memberCount == 0 || memberCount > MAX_CONTEXT_MAPS) {
                throw invalid("located-map member count outside bounds");
            }
            ArrayList<String> members = new ArrayList<>(memberCount);
            for (int member = 0; member < memberCount; member++) members.add(readString(in));
            int scale = in.readInt(); int radius = in.readInt();
            boolean skipExisting = in.readBoolean();
            String locatorReceipt = readSha256(in);
            String referenceSnapshotReceipt = readNonzeroSha256(in,
                    "reference snapshot receipt");
            Binding binding = new Binding(destination, destinationTag, structureSet, members,
                    worldIdentity, sourceIdentity, tableIdentity, originX, originY, originZ,
                    scale, radius, skipExisting, locatorReceipt, referenceSnapshotReceipt);
            LocatedMapTarget target;
            if (variant == 1) {
                target = new Found(binding, in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        readSha256(in), readSha256(in));
            } else {
                target = new NotFound(binding, readSha256(in));
            }
            targets.put(destination, target);
            previous = destination;
        }
        return LocatedProductionContext.authenticated(biome, targets, worldIdentity,
                sourceIdentity, tableIdentity, originX, originY, originZ, contextReceipt);
    }
    private static void writeHeightmap(DataOutputStream out,int[] h)throws IOException{for(int y:h)out.writeShort(y);}
    private static int[] readHeightmap(DataInputStream in)throws IOException{int[]h=new int[256];for(int i=0;i<h.length;i++)h[i]=in.readShort();validateHeightmap(h);return h;}
    private static void validateHeightmap(int[] h){if(h.length!=256)throw invalid("heightmap length must be 256");for(int y:h)if(y<Blocks.MIN_Y||y>Blocks.MAX_Y+1)throw invalid("heightmap value outside generation range: "+y);}
    private static void writeSection(DataOutputStream out,String tag,byte[]p)throws IOException{byte[]b=canonicalAsciiBytes(tag,"section tag");if(b.length!=4)throw invalid("section tag must be four ASCII bytes");out.write(b);out.writeInt(p.length);out.write(p);}
    private static void writeString(DataOutputStream out,String s)throws IOException{writeAsciiU16(out,Mc263FeatureBlockState.requireCanonicalResourceKey(s,"carrier key"),"carrier key");}
    private static void writeContextString(DataOutputStream out,String s)throws IOException{writeAsciiU16(out,s,"production context string");}
    private static void writeToken(DataOutputStream out,String s)throws IOException{writeAsciiU16(out,requireToken(s),"declaration map destination");}
    private static String readString(DataInputStream in)throws IOException{String s=decodeAscii(readU16Bytes(in),"carrier key");return Mc263FeatureBlockState.requireCanonicalResourceKey(s,"carrier key");}
    private static String readContextString(DataInputStream in)throws IOException{String s=decodeUtf8(readU16Bytes(in),"production context string");return requireCanonicalAscii(s,"production context string");}
    private static String readToken(DataInputStream in)throws IOException{return requireToken(decodeAscii(readU16Bytes(in),"declaration map destination"));}
    private static void writeAsciiU16(DataOutputStream out,String value,String name)throws IOException{value=requireCanonicalAscii(value,name);if(value.length()>0xffff)throw invalid(name+" exceeds unsigned-16 bytes");byte[]b=value.getBytes(StandardCharsets.US_ASCII);out.writeShort(b.length);out.write(b);}
    private static byte[] canonicalAsciiBytes(String value,String name){return requireCanonicalAscii(value,name).getBytes(StandardCharsets.US_ASCII);}
    private static String requireCanonicalAscii(String value,String name){Objects.requireNonNull(value,name);for(int i=0;i<value.length();i++)if(value.charAt(i)>0x7f)throw invalid(name+" contains a non-ASCII code point");return value;}
    private static String requireToken(String value){requireCanonicalAscii(value,"declaration map destination");if(value.isEmpty())throw invalid("invalid declaration map destination");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'))throw invalid("invalid declaration map destination");}return value;}
    private static byte[] readU16Bytes(DataInputStream in)throws IOException{int n=in.readUnsignedShort();byte[]b=in.readNBytes(n);if(b.length!=n)throw new EOFException();return b;}
    private static String decodeAscii(byte[] bytes,String name){try{return StandardCharsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException malformed){throw invalid(name+" contains non-ASCII bytes",malformed);}}
    private static String decodeUtf8(byte[] bytes,String name){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException malformed){throw invalid("malformed UTF-8 "+name,malformed);}}
    private static void writeSha256(DataOutputStream out,String value,String name)throws IOException{if(value==null||value.length()!=64)throw invalid(name+" must be canonical SHA-256");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')))throw invalid(name+" must be canonical SHA-256");}out.write(HEX.parseHex(value));}
    private static String readSha256(DataInputStream in)throws IOException{byte[]b=in.readNBytes(32);if(b.length!=32)throw new EOFException();return HEX.formatHex(b);}
    private static void writeNonzeroSha256(DataOutputStream out,String value,String name)throws IOException{writeSha256(out,value,name);if(value.equals("0".repeat(64)))throw invalid(name+" must be nonzero SHA-256");}
    private static String readNonzeroSha256(DataInputStream in,String name)throws IOException{String value=readSha256(in);if(value.equals("0".repeat(64)))throw invalid(name+" must be nonzero SHA-256");return value;}

    private enum TextKind { RESOURCE_KEY, ASCII, TOKEN }
    private static final class ContextIo {
        private final DataInputStream in;
        private final DataOutputStream out;
        private ContextIo(DataInputStream in,DataOutputStream out){this.in=in;this.out=out;}
        static ContextIo reading(DataInputStream in){return new ContextIo(Objects.requireNonNull(in),null);}
        static ContextIo writing(DataOutputStream out){return new ContextIo(null,Objects.requireNonNull(out));}
        boolean isReading(){return in!=null;}
        String text(String value,TextKind kind)throws IOException{
            if(isReading())return switch(kind){case RESOURCE_KEY->readString(in);case ASCII->readContextString(in);case TOKEN->readToken(in);};
            switch(kind){case RESOURCE_KEY->writeString(out,value);case ASCII->writeContextString(out,value);case TOKEN->writeToken(out,value);}
            return value;
        }
        int integer(int value)throws IOException{if(isReading())return in.readInt();out.writeInt(value);return value;}
        String receipt(String value,String name)throws IOException{if(isReading())return readSha256(in);writeSha256(out,value,name);return value;}
        int mapCount(int value)throws IOException{int count=isReading()?in.readUnsignedShort():value;if(count<0||count>MAX_CONTEXT_MAPS)throw invalid("loot declaration map count exceeds "+MAX_CONTEXT_MAPS);if(!isReading())out.writeShort(count);return count;}
    }
    private static String key(List<String> keys,int i){if(i>=keys.size())throw invalid("key index outside KEYS");return keys.get(i);}
    private static int count(DataInputStream in,String what)throws IOException{long n=Integer.toUnsignedLong(in.readInt());if(n>Blocks.CHUNK_BLOCKS)throw invalid(what+" count exceeds chunk");return(int)n;}
    static int requireLootDeclarationCount(long count) {
        if (count < 0 || count > MAX_CONTAINER_LOOT_DECLARATIONS) {
            throw invalid("loot declaration count exceeds LOOT+loot-ENTS capacities");
        }
        return (int) count;
    }
    private static int boundedPayloadLength(int length){if(length<0||length>1_048_576)throw invalid("sidecar payload length outside 0..1048576");return length;}
    private static int checkedPacked(int p){if(p<0||p>=Blocks.CHUNK_BLOCKS)throw invalid("packed position outside chunk: "+p);return p;}
    /**
     * LOOT facing code table (one unsigned byte, append-only). Codes {@code 0..3} are the original
     * horizontal encoding and are byte-identical to every chunk written before the vertical
     * facings existed; {@code 4} and {@code 5} were appended for vanilla's vertical container
     * facings (barrel, dispenser, hopper). Codes {@code 6..255} are unused spare space.
     *
     * <pre>0=north 1=east 2=south 3=west 4=up 5=down</pre>
     */
    static final List<String> FACING_CODES = List.of("north","east","south","west","up","down");
    private static int facing(String f){int code=FACING_CODES.indexOf(f);if(code<0)throw invalid("invalid facing: "+f);return code;}
    private static String facing(int f){if(f>=FACING_CODES.size())throw invalid("invalid facing code");return FACING_CODES.get(f);}
    private static void putInt(byte[]b,int p,int v){b[p]=(byte)(v>>>24);b[p+1]=(byte)(v>>>16);b[p+2]=(byte)(v>>>8);b[p+3]=(byte)v;}
    private static byte[] payload(Writer w)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);w.write(o);o.flush();return b.toByteArray();}
    private static <T>T readPayload(byte[]p,Reader<T>r)throws IOException{DataInputStream in=new DataInputStream(new ByteArrayInputStream(p));T v=r.read(in);if(in.available()!=0)throw invalid("trailing section payload");return v;}
    private static IllegalArgumentException invalid(String s){return new IllegalArgumentException(s);}
    private static IllegalArgumentException invalid(String s,Throwable t){return new IllegalArgumentException(s,t);}
    @FunctionalInterface private interface Writer{void write(DataOutputStream out)throws IOException;}
    @FunctionalInterface private interface Reader<T>{T read(DataInputStream in)throws IOException;}
}
