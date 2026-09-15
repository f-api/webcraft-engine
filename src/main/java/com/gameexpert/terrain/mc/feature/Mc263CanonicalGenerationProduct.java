package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The only output object allowed to leave the canonical Java FEATURES pipeline.
 *
 * <p>It binds the immutable center chunk, STR structure carrier, stage receipts, and one commit
 * fingerprint. Callers cannot obtain a partially assembled snapshot or a nullable carrier from
 * this boundary.</p>
 */
public final class Mc263CanonicalGenerationProduct {
    private static final byte[] PROVENANCE_MAGIC =
            "CPS263P1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] POST_CARVERS_MAGIC =
            "PCR263F1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PRODUCTION_CONTEXT_AUTHORITY_MAGIC =
            "PCA263P1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WORLDGEN_RANDOM_PROVENANCE_MAGIC =
            "WGR263P1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FEATURES_MAGIC =
            "MCF263FR".getBytes(StandardCharsets.US_ASCII);
    private static final int FEATURES_SCHEMA = 5;
    private static final List<String> FEATURES_SECTION_TAGS = List.of(
            "SCHD", "BLKS", "STAT", "HMAP", "POST", "TICK", "FLTK", "BCAP", "LOOT",
            "SPWN", "OWNR", "ARCH", "BEES");
    private static final byte[] ORIGIN_MAGIC = "SCOR2631".getBytes(StandardCharsets.US_ASCII);
    private static final int ORIGIN_SCHEMA = 5;
    private static final byte[] PRODUCT_BINDING_MAGIC =
            "MCP263B1".getBytes(StandardCharsets.US_ASCII);
    private static final int PRODUCT_BINDING_SCHEMA = 2;
    private final Mc263FinalChunkCodec.FinalChunk finalChunk;
    private final Mc263StructureCarrier structureCarrier;
    private final byte[] provenanceReceipt;
    private final byte[] featuresReceipt;
    private final List<String> stageHashes;
    private final byte[] commitFingerprint;
    private final int sourceCount;
    private final int structurePreflightCount;
    private final int scheduleEventCount;
    private final String scheduleTraceSha256;
    private final int postProcessedOccurrences;
    private final long worldSeed;
    /**
     * The append-only schema-4 carrier bytes and proven view decoded from them, produced once.
     *
     * <p>AGENTS rule 10l: the codec is a pure function of this product's immutable final chunk,
     * so encoding it, decoding that encoding, and hashing the three stage payloads are the same
     * answer every time they are asked. The integrity law is unchanged — it is proven on the one
     * encoding this product will ever publish, and every later verification compares coordinates
     * against that already-proven view instead of re-running the codec. Commit, verification and
     * replay therefore share one decode rather than paying three.</p>
     */
    private final byte[] finalCarrierBytes;
    private final Mc263FinalChunkCodec.FinalChunk provenFinalCarrier;
    private final ProvenanceTarget provenance;
    private final byte[] finalCarrierProductBinding;
    private final Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt;

    Mc263CanonicalGenerationProduct(Mc263FinalChunkCodec.FinalChunk finalChunk,
            Mc263StructureCarrier structureCarrier, byte[] provenanceReceipt,
            byte[] featuresReceipt, List<String> stageHashes, byte[] commitFingerprint,
            Mc263CanonicalFeaturesProducerSkeleton.DispatchEvidence evidence,
            Mc263PostprocessResolver.Result postprocess) {
        this(finalChunk, structureCarrier, provenanceReceipt, featuresReceipt, stageHashes,
                commitFingerprint, evidence, postprocess,
                Mc263FinalChunkSidecars.currentIntegratedBuildReceipt());
    }

    Mc263CanonicalGenerationProduct(Mc263FinalChunkCodec.FinalChunk finalChunk,
            Mc263StructureCarrier structureCarrier, byte[] provenanceReceipt,
            byte[] featuresReceipt, List<String> stageHashes, byte[] commitFingerprint,
            Mc263CanonicalFeaturesProducerSkeleton.DispatchEvidence evidence,
            Mc263PostprocessResolver.Result postprocess,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        Mc263FinalChunkCodec.FinalChunk suppliedFinalChunk =
                Objects.requireNonNull(finalChunk, "final chunk");
        this.structureCarrier = Objects.requireNonNull(structureCarrier, "structure carrier");
        this.provenanceReceipt = copy(provenanceReceipt, "provenance receipt");
        this.featuresReceipt = copy(featuresReceipt, "FEATURES receipt");
        this.stageHashes = List.copyOf(stageHashes);
        if (this.stageHashes.size() != 3) {
            throw new IllegalArgumentException("canonical product requires three stage hashes");
        }
        this.commitFingerprint = copy(commitFingerprint, "commit fingerprint");
        if (this.commitFingerprint.length != 32) {
            throw new IllegalArgumentException("canonical commit fingerprint must be SHA-256");
        }
        this.sourceCount = evidence.sourceCount();
        this.structurePreflightCount = evidence.structurePreflightCount();
        this.scheduleEventCount = evidence.scheduleEventCount();
        this.scheduleTraceSha256 = Objects.requireNonNull(evidence.scheduleTraceSha256(),
                "schedule trace hash");
        if (!this.scheduleTraceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("schedule trace hash must be canonical SHA-256");
        }
        this.postProcessedOccurrences = Objects.requireNonNull(postprocess, "POST result")
                .processedOccurrences();
        this.integratedBuildReceipt = Objects.requireNonNull(integratedBuildReceipt,
                "integrated build receipt");
        this.finalCarrierBytes = Mc263FinalChunkCodec.encode(suppliedFinalChunk,
                this.integratedBuildReceipt);
        this.provenFinalCarrier = Mc263FinalChunkCodec.decode(finalCarrierBytes,
                this.integratedBuildReceipt);
        this.finalChunk = provenFinalCarrier;
        this.provenance = readProvenanceTarget();
        this.worldSeed = provenance.worldSeed();
        // Order matters: the original single-pass verification rejected a bad coordinate before it
        // rejected a bad stage hash, and callers assert on that first message.
        verifyIntegrity(provenance.chunkX(), provenance.chunkZ());
        proveStageProvenance();
        this.finalCarrierProductBinding = productBinding(finalCarrierBytes);
    }

    public Mc263FinalChunkCodec.FinalChunk finalChunk() { return finalChunk; }
    public Mc263StructureCarrier structureCarrier() { return structureCarrier; }
    public byte[] provenanceReceipt() { return provenanceReceipt.clone(); }
    public byte[] featuresReceipt() { return featuresReceipt.clone(); }
    public List<String> stageHashes() { return stageHashes; }
    public byte[] commitFingerprint() { return commitFingerprint.clone(); }
    public byte[] finalCarrier() { return finalCarrierBytes.clone(); }
    public byte[] finalCarrierProductBinding() { return finalCarrierProductBinding.clone(); }

    /**
     * Canonical exporter envelope binding the exact carrier, origin, provenance and this product.
     * A source collector verifies this one envelope instead of trusting matching filenames.
     */
    public byte[] exportProductBinding(byte[] originReceipt) {
        throw new IllegalArgumentException(
                "integrated build receipt is required for canonical product export");
    }

    /**
     * Exports the unchanged MCP263B1 schema-2 envelope only after the integrated build has
     * authenticated every producer/exporter class and external dependency receipt.
     */
    public byte[] exportProductBinding(byte[] originReceipt,
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt) {
        Objects.requireNonNull(integratedBuildReceipt, "integrated build receipt")
                .verifyAgainstCurrentBuild();
        if (!integratedBuildReceipt.producerSourceSha256().equals(
                this.integratedBuildReceipt.producerSourceSha256())) {
            throw new IllegalArgumentException("integrated producer receipt is not current");
        }
        if (!integratedBuildReceipt.exporterSourceSha256().equals(
                this.integratedBuildReceipt.exporterSourceSha256())) {
            throw new IllegalArgumentException("integrated exporter receipt is not current");
        }
        OriginReceipt origin = authenticateOriginReceipt(originReceipt);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(352);
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(PRODUCT_BINDING_MAGIC);
            out.writeShort(PRODUCT_BINDING_SCHEMA);
            out.writeLong(worldSeed); out.writeInt(finalChunk.chunkX());
            out.writeInt(finalChunk.chunkZ());
            out.write(sha256Bytes(finalCarrierBytes)); out.write(sha256Bytes(origin.bytes()));
            out.write(sha256Bytes(provenance.predecessor().receiptBytes()));
            out.write(sha256Bytes(provenanceReceipt)); out.write(sha256Bytes(featuresReceipt));
            out.write(sha256Bytes(structureCarrier.receiptBytes()));
            out.write(commitFingerprint); out.write(finalCarrierProductBinding);
            out.write(HexFormat.of().parseHex(integratedBuildReceipt.producerSourceSha256()));
            out.write(HexFormat.of().parseHex(integratedBuildReceipt.exporterSourceSha256()));
            out.writeInt(featuresReceipt.length); out.write(featuresReceipt);
            out.write(HexFormat.of().parseHex(scheduleTraceSha256));
            out.writeInt(postProcessedOccurrences);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * The proven decoded view of {@link #finalCarrier()}, shared with commit and replay so the
     * schema-4 carrier is decoded exactly once per product.
     */
    public Mc263FinalChunkCodec.FinalChunk provenFinalCarrier() { return provenFinalCarrier; }
    /** Commits this complete product as one current-only STR/final-carrier persistence unit. */
    public CanonicalWorldgenStore.CanonicalChunkSnapshot commitTo(
            CanonicalWorldgenStore store, long worldId, String worldIdentity) {
        Objects.requireNonNull(store, "canonical worldgen store");
        byte[] finalCarrier = finalCarrier();
        byte[] successorStr = structureCarrier.receiptBytes();
        // The commit's own decode is served from this product's proven view (ChunkCommit keeps it).
        return store.commit(new CanonicalWorldgenStore.ChunkCommit(
                worldId, worldIdentity, finalChunk.chunkX(), finalChunk.chunkZ(), finalCarrier,
                successorStr, successorStr, commitFingerprint));
    }
    public int sourceCount() { return sourceCount; }
    public int structurePreflightCount() { return structurePreflightCount; }
    public int scheduleEventCount() { return scheduleEventCount; }
    public String scheduleTraceSha256() { return scheduleTraceSha256; }
    public int postProcessedOccurrences() { return postProcessedOccurrences; }

    /**
     * Returns the final schema-4 carrier only after rechecking its immutable product identity
     * against the requested generation coordinates. No nullable or legacy block carrier is
     * accepted at this boundary.
     */
    public Mc263FinalChunkCodec.FinalChunk verifiedFinalChunk(
            long expectedWorldSeed, int expectedChunkX, int expectedChunkZ) {
        if (worldSeed != expectedWorldSeed) {
            throw new IllegalArgumentException("canonical product world seed mismatch");
        }
        verifyIntegrity(expectedChunkX, expectedChunkZ);
        return finalChunk;
    }

    private void verifyIntegrity(int expectedChunkX, int expectedChunkZ) {
        if (finalChunk.chunkX() != expectedChunkX || finalChunk.chunkZ() != expectedChunkZ) {
            throw new IllegalArgumentException("canonical product coordinates mismatch");
        }
        if (provenance.chunkX() != expectedChunkX || provenance.chunkZ() != expectedChunkZ) {
            throw new IllegalArgumentException("canonical provenance coordinates mismatch");
        }
        verifyFeaturesTarget(expectedChunkX, expectedChunkZ);
        if (provenFinalCarrier.chunkX() != expectedChunkX
                || provenFinalCarrier.chunkZ() != expectedChunkZ) {
            throw new IllegalArgumentException("schema-6 final carrier coordinates mismatch");
        }
    }

    /**
     * The coordinate-independent half of the integrity law, proven once on the single encoding
     * this product publishes: identical inputs would produce identical hashes on every repeat.
     */
    private void proveStageProvenance() {
        List<String> expectedStages = List.of(sha256(provenanceReceipt),
                sha256(featuresReceipt), sha256(finalCarrierBytes));
        if (!stageHashes.equals(expectedStages)) {
            throw new IllegalArgumentException("canonical product stage provenance mismatch");
        }
        byte[] expectedFingerprint = commitFingerprint(finalCarrierBytes);
        if (!MessageDigest.isEqual(commitFingerprint, expectedFingerprint)) {
            throw new IllegalArgumentException("canonical product commit fingerprint mismatch");
        }
    }

    private ProvenanceTarget readProvenanceTarget() {
        ByteBuffer envelope = ByteBuffer.wrap(provenanceReceipt);
        requireBytes(envelope, PROVENANCE_MAGIC, "canonical provenance magic");
        byte[] sourceReceipt = readSection(envelope, "source provenance");
        byte[] structures = readSection(envelope, "structure provenance");
        if (envelope.hasRemaining()) {
            throw new IllegalArgumentException("canonical provenance has trailing bytes");
        }
        Mc263StructureCarrier predecessor = Mc263StructureCarrier.decode(structures);
        if (!predecessor.registry().definitions().equals(
                structureCarrier.registry().definitions())) {
            throw new IllegalArgumentException("canonical structure registry provenance mismatch");
        }
        if (!predecessor.referenceChunks().equals(structureCarrier.referenceChunks())) {
            throw new IllegalArgumentException("canonical structure reference provenance mismatch");
        }
        verifyStructureTransition(predecessor, structureCarrier);
        TargetIdentity target = parseSourceReceipt(sourceReceipt);
        return new ProvenanceTarget(target.worldSeed(), target.chunkX(), target.chunkZ(), predecessor);
    }

    private static TargetIdentity parseSourceReceipt(byte[] sourceReceipt) {
        ByteBuffer source = ByteBuffer.wrap(sourceReceipt);
        TargetIdentity target = parsePostCarversReceipt(source);
        requireBytes(source, PRODUCTION_CONTEXT_AUTHORITY_MAGIC,
                "production-context authority provenance magic");
        byte[] authorityReceipt = readSection(source, "production-context authority provenance");
        if (authorityReceipt.length != 0 && authorityReceipt.length != 32) {
            throw new IllegalArgumentException(
                    "production-context authority receipt must be absent or SHA-256 sized");
        }
        if (authorityReceipt.length == 32 && allZero(authorityReceipt)) {
            throw new IllegalArgumentException(
                    "production-context authority receipt must not be all zero");
        }
        requireBytes(source, WORLDGEN_RANDOM_PROVENANCE_MAGIC,
                "WorldGenRegion random provenance magic");
        Mc263WorldGenRegionRandom.decodeReceipt(
                readSection(source, "WorldGenRegion random predecessor provenance"));
        Mc263WorldGenRegionRandom.decodeReceipt(
                readSection(source, "WorldGenRegion random successor provenance"));
        if (source.hasRemaining()) {
            throw new IllegalArgumentException("canonical source provenance has trailing bytes");
        }
        return target;
    }

    private OriginReceipt authenticateOriginReceipt(byte[] originReceipt) {
        byte[] origin = copy(originReceipt, "origin receipt");
        ByteBuffer input = ByteBuffer.wrap(origin);
        requireBytes(input, ORIGIN_MAGIC, "carrier origin magic");
        if (input.remaining() < Integer.BYTES + Short.BYTES * 2 + Long.BYTES
                + Integer.BYTES * 4 + 32) {
            throw new IllegalArgumentException("truncated carrier origin header");
        }
        if (input.getInt() != ORIGIN_SCHEMA) {
            throw new IllegalArgumentException("carrier origin schema mismatch");
        }
        if (Short.toUnsignedInt(input.getShort()) != Mc263FinalChunkCodec.SCHEMA) {
            throw new IllegalArgumentException("carrier origin final-carrier schema mismatch");
        }
        if (Short.toUnsignedInt(input.getShort()) != 0) {
            throw new IllegalArgumentException("carrier origin flags mismatch");
        }
        if (input.getLong() != worldSeed || input.getInt() != finalChunk.chunkX()
                || input.getInt() != finalChunk.chunkZ()) {
            throw new IllegalArgumentException("carrier origin product target mismatch");
        }
        int referenceSetCount = input.getInt();
        if (referenceSetCount < 0) {
            throw new IllegalArgumentException("negative carrier origin reference-set count");
        }
        int carrierLength = input.getInt();
        if (carrierLength < 0 || input.remaining() != 32 + carrierLength) {
            throw new IllegalArgumentException("carrier origin embedded length mismatch");
        }
        byte[] expectedDigest = new byte[32]; input.get(expectedDigest);
        byte[] embedded = new byte[carrierLength]; input.get(embedded);
        if (!MessageDigest.isEqual(expectedDigest, sha256Bytes(embedded))) {
            throw new IllegalArgumentException("carrier origin embedded digest mismatch");
        }
        Mc263StructureCarrier successor = Mc263StructureCarrier.decode(embedded);
        if (!successor.hasSameReceipt(structureCarrier)) {
            throw new IllegalArgumentException("carrier origin/product successor mismatch");
        }
        int expectedReferenceSets = successor.referenceChunk(finalChunk.chunkX(),
                finalChunk.chunkZ()).orElseThrow(() -> new IllegalArgumentException(
                        "carrier origin lacks target reference chunk")).orderedSets().size();
        if (referenceSetCount != expectedReferenceSets) {
            throw new IllegalArgumentException("carrier origin reference-set count mismatch");
        }
        verifyStructureTransition(provenance.predecessor(), successor);
        return new OriginReceipt(origin, successor);
    }

    private static void verifyStructureTransition(Mc263StructureCarrier predecessor,
            Mc263StructureCarrier successor) {
        if (!predecessor.registry().definitions().equals(successor.registry().definitions())
                || !predecessor.referenceChunks().equals(successor.referenceChunks())) {
            throw new IllegalArgumentException("canonical STR predecessor/successor shape mismatch");
        }

        Set<String> permitted = new HashSet<>();
        for (Mc263StructureCarrier.ChunkReferences references : predecessor.referenceChunks()) {
            for (Mc263StructureCarrier.ReferenceSet set : references.orderedSets()) {
                for (long origin : set.orderedOrigins()) {
                    permitted.add(structureOriginIdentity(set.structureId(),
                            Mc263StructureCarrier.unpackChunkX(origin),
                            Mc263StructureCarrier.unpackChunkZ(origin)));
                }
            }
        }

        int predecessorChunkIndex = 0;
        for (Mc263StructureCarrier.ChunkStarts after : successor.startChunks()) {
            Mc263StructureCarrier.ChunkStarts before = predecessorChunkIndex
                    < predecessor.startChunks().size()
                    ? predecessor.startChunks().get(predecessorChunkIndex) : null;
            if (before != null && before.chunkX() == after.chunkX()
                    && before.chunkZ() == after.chunkZ()) {
                predecessorChunkIndex++;
                if (before.orderedStarts().size() != after.orderedStarts().size()) {
                    throw new IllegalArgumentException(
                            "canonical STR successor changed start-entry cardinality");
                }
                for (int index = 0; index < before.orderedStarts().size(); index++) {
                    Mc263StructureCarrier.StartEntry left = before.orderedStarts().get(index);
                    Mc263StructureCarrier.StartEntry right = after.orderedStarts().get(index);
                    if (!left.structureId().equals(right.structureId())) {
                        throw new IllegalArgumentException(
                                "canonical STR successor reordered or replaced a start identity");
                    }
                    if (!left.equals(right) && !permitted.contains(structureOriginIdentity(
                            left.structureId(), before.chunkX(), before.chunkZ()))) {
                        throw new IllegalArgumentException(
                                "canonical STR successor mutated an unrelated predecessor start");
                    }
                }
            } else if (after.orderedStarts().size() != 1 || !permitted.contains(
                    structureOriginIdentity(after.orderedStarts().get(0).structureId(),
                            after.chunkX(), after.chunkZ()))) {
                throw new IllegalArgumentException(
                        "canonical STR successor inserted an unauthenticated start chunk");
            }
        }
        if (predecessorChunkIndex != predecessor.startChunks().size()) {
            throw new IllegalArgumentException("canonical STR successor removed a predecessor start chunk");
        }

        verifyRawStartTransition(predecessor.rawStartPayloads(), successor.rawStartPayloads(),
                permitted);
        verifyProducerGraphTransition(predecessor.producerGraphPayloads(),
                successor.producerGraphPayloads(), permitted);
    }

    private static void verifyRawStartTransition(
            List<Mc263StructureCarrier.RawStartPayload> before,
            List<Mc263StructureCarrier.RawStartPayload> after, Set<String> permitted) {
        for (Mc263StructureCarrier.RawStartPayload left : before) {
            Mc263StructureCarrier.RawStartPayload right = after.stream()
                    .filter(candidate -> sameRawStartIdentity(left, candidate)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "canonical STR successor removed a raw-start payload"));
            if (!sameRawStartPayload(left, right) && !permitted.contains(structureOriginIdentity(
                    left.structureId(), left.originChunkX(), left.originChunkZ()))) {
                throw new IllegalArgumentException(
                        "canonical STR successor mutated an unrelated raw-start payload");
            }
        }
        for (Mc263StructureCarrier.RawStartPayload right : after) {
            boolean existed = before.stream().anyMatch(left -> sameRawStartIdentity(left, right));
            if (!existed && !permitted.contains(structureOriginIdentity(right.structureId(),
                    right.originChunkX(), right.originChunkZ()))) {
                throw new IllegalArgumentException(
                        "canonical STR successor inserted an unauthenticated raw-start payload");
            }
        }
    }

    private static void verifyProducerGraphTransition(
            List<Mc263StructureCarrier.ProducerGraphPayload> before,
            List<Mc263StructureCarrier.ProducerGraphPayload> after, Set<String> permitted) {
        for (Mc263StructureCarrier.ProducerGraphPayload left : before) {
            Mc263StructureCarrier.ProducerGraphPayload right = after.stream()
                    .filter(candidate -> sameProducerGraphIdentity(left, candidate)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "canonical STR successor removed a producer-graph payload"));
            if (!left.equals(right) && !permitted.contains(structureOriginIdentity(
                    left.structureId(), left.originChunkX(), left.originChunkZ()))) {
                throw new IllegalArgumentException(
                        "canonical STR successor mutated an unrelated producer-graph payload");
            }
        }
        for (Mc263StructureCarrier.ProducerGraphPayload right : after) {
            boolean existed = before.stream().anyMatch(left -> sameProducerGraphIdentity(left, right));
            if (!existed && !permitted.contains(structureOriginIdentity(right.structureId(),
                    right.originChunkX(), right.originChunkZ()))) {
                throw new IllegalArgumentException(
                        "canonical STR successor inserted an unauthenticated producer-graph payload");
            }
        }
    }

    private static boolean sameRawStartIdentity(Mc263StructureCarrier.RawStartPayload left,
            Mc263StructureCarrier.RawStartPayload right) {
        return left.structureId().equals(right.structureId())
                && left.startKey().equals(right.startKey())
                && left.originChunkX() == right.originChunkX()
                && left.originChunkZ() == right.originChunkZ();
    }

    private static boolean sameRawStartPayload(Mc263StructureCarrier.RawStartPayload left,
            Mc263StructureCarrier.RawStartPayload right) {
        return sameRawStartIdentity(left, right)
                && MessageDigest.isEqual(left.predecessorBinaryNbtCompound(),
                        right.predecessorBinaryNbtCompound())
                && MessageDigest.isEqual(left.successorBinaryNbtCompound(),
                        right.successorBinaryNbtCompound());
    }

    private static boolean sameProducerGraphIdentity(
            Mc263StructureCarrier.ProducerGraphPayload left,
            Mc263StructureCarrier.ProducerGraphPayload right) {
        return left.structureId().equals(right.structureId())
                && left.startKey().equals(right.startKey())
                && left.originChunkX() == right.originChunkX()
                && left.originChunkZ() == right.originChunkZ();
    }

    private static String structureOriginIdentity(String structureId, int originChunkX,
            int originChunkZ) {
        return structureId + '\u0000' + originChunkX + '\u0000' + originChunkZ;
    }

    private byte[] commitFingerprint(byte[] finalCarrier) {
        MessageDigest digest = sha256Digest();
        digest.update("MC263-CANONICAL-PRODUCT-V1\0".getBytes(StandardCharsets.US_ASCII));
        digest.update(provenanceReceipt);
        digest.update(structureCarrier.receiptBytes());
        digest.update(featuresReceipt);
        digest.update(scheduleTraceSha256.getBytes(StandardCharsets.US_ASCII));
        digest.update(Integer.toString(postProcessedOccurrences).getBytes(StandardCharsets.US_ASCII));
        digest.update(finalCarrier);
        return digest.digest();
    }

    private byte[] productBinding(byte[] finalCarrier) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write("MC263-FINAL-CARRIER-PRODUCT-BINDING-V2\0"
                    .getBytes(StandardCharsets.US_ASCII));
            out.writeLong(worldSeed); out.writeInt(finalChunk.chunkX());
            out.writeInt(finalChunk.chunkZ());
            writeBlob(out, finalCarrier); writeBlob(out, provenanceReceipt);
            writeBlob(out, featuresReceipt); writeBlob(out, structureCarrier.receiptBytes());
            out.write(commitFingerprint);
            out.write(HexFormat.of().parseHex(
                    integratedBuildReceipt.producerSourceSha256()));
            out.write(HexFormat.of().parseHex(
                    integratedBuildReceipt.exporterSourceSha256()));
            out.flush();
            return sha256Bytes(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeBlob(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length); output.write(value);
    }

    private void verifyFeaturesTarget(int expectedChunkX, int expectedChunkZ) {
        parseFeaturesReceipt(featuresReceipt, expectedChunkX, expectedChunkZ);
    }

    /** Focused test seam for nested receipt mutation tests; production uses the constructor path. */
    public static void validateNestedReceiptsForTesting(byte[] provenance, byte[] features,
            int expectedChunkX, int expectedChunkZ) {
        ByteBuffer envelope = ByteBuffer.wrap(provenance);
        requireBytes(envelope, PROVENANCE_MAGIC, "canonical provenance magic");
        byte[] source = readSection(envelope, "source provenance");
        readSection(envelope, "structure provenance");
        if (envelope.hasRemaining()) {
            throw new IllegalArgumentException("canonical provenance has trailing bytes");
        }
        TargetIdentity target = parseSourceReceipt(source);
        if (target.chunkX() != expectedChunkX || target.chunkZ() != expectedChunkZ) {
            throw new IllegalArgumentException("canonical provenance coordinates mismatch");
        }
        parseFeaturesReceipt(features, expectedChunkX, expectedChunkZ);
    }

    private static void parseFeaturesReceipt(byte[] features, int expectedChunkX,
            int expectedChunkZ) {
        ByteBuffer receipt = ByteBuffer.wrap(features);
        requireBytes(receipt, FEATURES_MAGIC, "FEATURES receipt magic");
        if (receipt.remaining() < Short.BYTES + Integer.BYTES * 2) {
            throw new IllegalArgumentException("truncated FEATURES receipt target");
        }
        int schema = Short.toUnsignedInt(receipt.getShort());
        if (schema != FEATURES_SCHEMA) {
            throw new IllegalArgumentException("FEATURES receipt schema mismatch");
        }
        if (receipt.getInt() != expectedChunkX || receipt.getInt() != expectedChunkZ) {
            throw new IllegalArgumentException("FEATURES receipt coordinates mismatch");
        }
        for (String tag : FEATURES_SECTION_TAGS) {
            byte[] tagBytes = tag.getBytes(StandardCharsets.US_ASCII);
            requireBytes(receipt, tagBytes, "FEATURES " + tag + " section tag");
            parseFeaturesSection(tag, readSection(receipt, "FEATURES " + tag + " section"));
        }
        if (receipt.hasRemaining()) {
            throw new IllegalArgumentException("FEATURES receipt has trailing bytes");
        }
    }

    private static TargetIdentity parsePostCarversReceipt(ByteBuffer input) {
        requireBytes(input, POST_CARVERS_MAGIC, "post-CARVERS provenance magic");
        requireRemaining(input, Long.BYTES + Integer.BYTES * 4 + 2,
                "post-CARVERS target provenance");
        long worldSeed = input.getLong();
        int targetX = input.getInt();
        int targetZ = input.getInt();
        int minY = input.getInt();
        int chunkY = input.getInt();
        int radius = Byte.toUnsignedInt(input.get());
        int chunkCount = Byte.toUnsignedInt(input.get());
        if (minY != Blocks.MIN_Y || chunkY != Blocks.CHUNK_Y
                || radius != Mc263FeaturesRegion.INPUT_RADIUS
                || chunkCount != Mc263FeaturesRegion.INPUT_CHUNK_COUNT) {
            throw new IllegalArgumentException("post-CARVERS provenance header mismatch");
        }
        long firstX = (long) targetX - radius;
        long lastX = (long) targetX + radius;
        long firstZ = (long) targetZ - radius;
        long lastZ = (long) targetZ + radius;
        if (firstX < Integer.MIN_VALUE || lastX > Integer.MAX_VALUE
                || firstZ < Integer.MIN_VALUE || lastZ > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("post-CARVERS provenance target overflows chunk range");
        }
        for (long chunkX = firstX; chunkX <= lastX; chunkX++) {
            for (long chunkZ = firstZ; chunkZ <= lastZ; chunkZ++) {
                parsePostCarversChunk(input, Math.toIntExact(chunkX), Math.toIntExact(chunkZ));
            }
        }
        return new TargetIdentity(worldSeed, targetX, targetZ);
    }

    private static void parsePostCarversChunk(ByteBuffer input, int expectedChunkX,
            int expectedChunkZ) {
        requireRemaining(input, Integer.BYTES * 2, "post-CARVERS chunk coordinates");
        if (input.getInt() != expectedChunkX || input.getInt() != expectedChunkZ) {
            throw new IllegalArgumentException("post-CARVERS chunk order mismatch");
        }
        requireExactCount(input, Blocks.CHUNK_BLOCKS, "post-CARVERS blocks");
        skipWords(input, Blocks.CHUNK_BLOCKS, Short.BYTES,
                "post-CARVERS block payload");
        requireExactCount(input, Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK,
                "post-CARVERS biome keys");
        for (int index = 0; index < Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK; index++) {
            readKey(input, "post-CARVERS biome key");
        }
        int overrideCount = readBoundedCount(input, "post-CARVERS state overrides",
                Blocks.CHUNK_BLOCKS, 6);
        int previousOverride = -1;
        for (int index = 0; index < overrideCount; index++) {
            requireRemaining(input, Integer.BYTES + Short.BYTES, "post-CARVERS state override");
            int blockIndex = input.getInt();
            int blockId = Short.toUnsignedInt(input.getShort());
            if (blockIndex < 0 || blockIndex >= Blocks.CHUNK_BLOCKS
                    || blockIndex <= previousOverride
                    || blockId >= Blocks.BLOCK_ID_TABLE_CAPACITY) {
                throw new IllegalArgumentException("post-CARVERS state override drift");
            }
            readAsciiDelimited(input, "post-CARVERS exact state");
            previousOverride = blockIndex;
        }
        requireRemaining(input, 1, "post-CARVERS postprocess section count");
        if (Byte.toUnsignedInt(input.get()) != Mc263FeaturesRegion.SECTION_COUNT) {
            throw new IllegalArgumentException("post-CARVERS postprocess section count mismatch");
        }
        for (int section = 0; section < Mc263FeaturesRegion.SECTION_COUNT; section++) {
            int count = readBoundedCount(input, "post-CARVERS postprocess marks",
                    Blocks.CHUNK_BLOCKS, Short.BYTES);
            int previousMark = -1;
            for (int index = 0; index < count; index++) {
                int mark = readUnsignedShort(input, "post-CARVERS postprocess mark");
                if (mark <= previousMark) {
                    throw new IllegalArgumentException("post-CARVERS postprocess mark order drift");
                }
                previousMark = mark;
            }
        }
        int blockTickCount = readBoundedCount(input, "post-CARVERS block ticks",
                Blocks.CHUNK_BLOCKS, 9);
        for (int index = 0; index < blockTickCount; index++) {
            requireRemaining(input, 1 + Integer.BYTES + 1 + Short.BYTES,
                    "post-CARVERS block tick");
            int localX = Byte.toUnsignedInt(input.get());
            int blockY = input.getInt();
            int localZ = Byte.toUnsignedInt(input.get());
            int blockId = Short.toUnsignedInt(input.getShort());
            requireBlockPosition(localX, blockY, localZ, "post-CARVERS block tick");
            if (blockId >= Blocks.BLOCK_ID_TABLE_CAPACITY) {
                throw new IllegalArgumentException("post-CARVERS block tick ID drift");
            }
            readKey(input, "post-CARVERS block tick key");
            requireRemaining(input, Integer.BYTES, "post-CARVERS block tick delay");
            if (input.getInt() < 0) {
                throw new IllegalArgumentException("post-CARVERS block tick delay drift");
            }
        }
        int fluidTickCount = readBoundedCount(input, "post-CARVERS fluid ticks",
                Blocks.CHUNK_BLOCKS, 7);
        for (int index = 0; index < fluidTickCount; index++) {
            requireRemaining(input, 1 + Integer.BYTES + 1,
                    "post-CARVERS fluid tick");
            int localX = Byte.toUnsignedInt(input.get());
            int blockY = input.getInt();
            int localZ = Byte.toUnsignedInt(input.get());
            requireBlockPosition(localX, blockY, localZ, "post-CARVERS fluid tick");
            readKey(input, "post-CARVERS fluid tick key");
            requireRemaining(input, Integer.BYTES, "post-CARVERS fluid tick delay");
            if (input.getInt() < 0) {
                throw new IllegalArgumentException("post-CARVERS fluid tick delay drift");
            }
        }
        parseLight(input, "post-CARVERS");
    }

    private static void parseLight(ByteBuffer input, String owner) {
        parseLightKind(input, owner + " sky light");
        parseLightKind(input, owner + " block light");
    }

    private static void parseLightKind(ByteBuffer input, String owner) {
        requireRemaining(input, 1, owner + " section count");
        if (Byte.toUnsignedInt(input.get()) != Mc263FeaturesRegion.SECTION_COUNT) {
            throw new IllegalArgumentException(owner + " section count mismatch");
        }
        for (int section = 0; section < Mc263FeaturesRegion.SECTION_COUNT; section++) {
            requireRemaining(input, 1, owner + " presence flag");
            int present = Byte.toUnsignedInt(input.get());
            if (present > 1) throw new IllegalArgumentException(owner + " presence flag mismatch");
            if (present == 1) skip(input, Mc263FeaturesRegion.LIGHT_SECTION_BYTES,
                    owner + " section payload");
        }
    }

    private static void parseFeaturesSection(String tag, byte[] encoded) {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        switch (tag) {
            case "SCHD" -> {
                int count = readBoundedCount(input, "FEATURES schedule", Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, Integer.BYTES * 2 + 1 + 1 + Short.BYTES,
                            "FEATURES schedule event");
                    input.getInt(); input.getInt();
                    int kind = Byte.toUnsignedInt(input.get());
                    int step = Byte.toUnsignedInt(input.get());
                    input.getShort();
                    if (kind > 1 || step > 10) {
                        throw new IllegalArgumentException("FEATURES schedule event drift");
                    }
                    readKey(input, "FEATURES schedule key");
                }
            }
            case "BLKS" -> {
                requireExactCount(input, Blocks.CHUNK_BLOCKS, "FEATURES dense blocks");
                for (int index = 0; index < Blocks.CHUNK_BLOCKS; index++) {
                    if (readUnsignedShort(input, "FEATURES dense block ID")
                            >= Blocks.BLOCK_ID_TABLE_CAPACITY) {
                        throw new IllegalArgumentException("FEATURES dense block ID drift");
                    }
                }
            }
            case "STAT" -> {
                int paletteCount = readUnsignedShort(input, "FEATURES state palette");
                Set<String> palette = new HashSet<>();
                for (int index = 0; index < paletteCount; index++) {
                    String state = readDelimited(input, "FEATURES exact state palette entry");
                    if (!palette.add(state)) {
                        throw new IllegalArgumentException("FEATURES state palette duplicate");
                    }
                }
                if (palette.isEmpty()) {
                    throw new IllegalArgumentException("FEATURES state palette is empty");
                }
                requireExactCount(input, Blocks.CHUNK_BLOCKS, "FEATURES state indices");
                for (int index = 0; index < Blocks.CHUNK_BLOCKS; index++) {
                    if (readUnsignedShort(input, "FEATURES state index") >= paletteCount) {
                        throw new IllegalArgumentException("FEATURES state index drift");
                    }
                }
            }
            case "HMAP" -> {
                int columns = Math.multiplyExact(Blocks.CHUNK_X, Blocks.CHUNK_Z);
                requireExactCount(input, columns, "FEATURES heightmap columns");
                for (int index = 0; index < columns * 3; index++) {
                    int height = readInt(input, "FEATURES heightmap value");
                    if (height < Blocks.MIN_Y || height > Blocks.MAX_Y + 1) {
                        throw new IllegalArgumentException("FEATURES heightmap value drift");
                    }
                }
            }
            case "POST" -> {
                int count = readBoundedCount(input, "FEATURES postprocess marks",
                        Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + 1 + Integer.BYTES + 1,
                            "FEATURES postprocess mark");
                    int section = Byte.toUnsignedInt(input.get());
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    if (section >= Mc263FeaturesRegion.SECTION_COUNT) {
                        throw new IllegalArgumentException("FEATURES postprocess section drift");
                    }
                    requireBlockPosition(localX, blockY, localZ,
                            "FEATURES postprocess mark");
                }
            }
            case "TICK" -> parseFeaturePositionStringDelay(input, "FEATURES block tick", true);
            case "FLTK" -> parseFeaturePositionStringDelay(input, "FEATURES fluid tick", true);
            case "BCAP" -> {
                int count = readBoundedCount(input, "FEATURES block capabilities",
                        Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + Integer.BYTES + 1 + 1,
                            "FEATURES block capability");
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    int capability = Byte.toUnsignedInt(input.get());
                    requireBlockPosition(localX, blockY, localZ,
                            "FEATURES block capability");
                    if (capability < 1 || capability > 8) {
                        throw new IllegalArgumentException("FEATURES block capability drift");
                    }
                    readDelimited(input, "FEATURES block capability state");
                }
            }
            case "LOOT" -> {
                int count = readBoundedCount(input, "FEATURES loot", Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + Integer.BYTES + 1 + 1 + Long.BYTES,
                            "FEATURES loot");
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    int facing = Byte.toUnsignedInt(input.get());
                    requireBlockPosition(localX, blockY, localZ, "FEATURES loot");
                    if (facing >= Mc263FinalChunkCodec.FACING_CODES.size()) {
                        throw new IllegalArgumentException("FEATURES loot facing drift");
                    }
                    readKey(input, "FEATURES loot table"); input.getLong();
                }
            }
            case "SPWN" -> {
                int count = readBoundedCount(input, "FEATURES spawners",
                        Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + Integer.BYTES + 1,
                            "FEATURES spawner");
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    requireBlockPosition(localX, blockY, localZ, "FEATURES spawner");
                    readKey(input, "FEATURES spawner entity");
                }
            }
            case "OWNR" -> {
                int count = readBoundedCount(input, "FEATURES ownership",
                        Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + Integer.BYTES + 1 + Long.BYTES,
                            "FEATURES ownership");
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    requireBlockPosition(localX, blockY, localZ, "FEATURES ownership");
                    input.getLong();
                }
            }
            case "ARCH" -> {
                int count = readBoundedCount(input, "FEATURES archaeology",
                        Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + Integer.BYTES + 1 + Long.BYTES,
                            "FEATURES archaeology");
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    requireBlockPosition(localX, blockY, localZ, "FEATURES archaeology");
                    readKey(input, "FEATURES archaeology table"); input.getLong();
                }
            }
            case "BEES" -> {
                int count = readBoundedCount(input, "FEATURES beehives",
                        Blocks.CHUNK_BLOCKS, 0);
                for (int index = 0; index < count; index++) {
                    requireRemaining(input, 1 + Integer.BYTES + 1 + Integer.BYTES,
                            "FEATURES beehive");
                    int localX = Byte.toUnsignedInt(input.get());
                    int blockY = input.getInt();
                    int localZ = Byte.toUnsignedInt(input.get());
                    requireBlockPosition(localX, blockY, localZ, "FEATURES beehive");
                    int occupants = readBoundedCount(input, "FEATURES beehive occupants",
                            0xffff, Integer.BYTES);
                    for (int occupant = 0; occupant < occupants; occupant++) {
                        int ticks = readInt(input, "FEATURES beehive occupant");
                        if (ticks < 0 || ticks > 598) {
                            throw new IllegalArgumentException(
                                    "FEATURES beehive occupant ticks drift");
                        }
                    }
                }
            }
            default -> throw new IllegalArgumentException("unknown FEATURES section: " + tag);
        }
        if (input.hasRemaining()) {
            throw new IllegalArgumentException("FEATURES " + tag + " section has trailing bytes");
        }
    }

    private static void parseFeaturePositionStringDelay(ByteBuffer input, String owner,
            boolean key) {
        int count = readBoundedCount(input, owner, Blocks.CHUNK_BLOCKS, 0);
        for (int index = 0; index < count; index++) {
            requireRemaining(input, 1 + Integer.BYTES + 1,
                    owner);
            int localX = Byte.toUnsignedInt(input.get());
            int blockY = input.getInt();
            int localZ = Byte.toUnsignedInt(input.get());
            requireBlockPosition(localX, blockY, localZ, owner);
            if (key) readKey(input, owner + " key");
            else readDelimited(input, owner + " key");
            int delay = readInt(input, owner + " delay");
            if (delay < 0) throw new IllegalArgumentException(owner + " delay drift");
        }
    }

    private static int readCount(ByteBuffer input, String owner) {
        return readBoundedCount(input, owner, Integer.MAX_VALUE, 0);
    }

    private static int readBoundedCount(ByteBuffer input, String owner, int maximum,
            int minimumWidth) {
        requireRemaining(input, Integer.BYTES, owner + " count");
        int count = input.getInt();
        if (count < 0 || count > maximum
                || count > input.remaining() / Math.max(1, minimumWidth)) {
            throw new IllegalArgumentException(owner + " count is invalid: " + count);
        }
        return count;
    }

    private static void requireExactCount(ByteBuffer input, int expected, String owner) {
        if (readCount(input, owner) != expected) {
            throw new IllegalArgumentException(owner + " count mismatch");
        }
    }

    private static int readUnsignedShort(ByteBuffer input, String owner) {
        requireRemaining(input, Short.BYTES, owner);
        return Short.toUnsignedInt(input.getShort());
    }

    private static String readDelimited(ByteBuffer input, String owner) {
        int length = readUnsignedShort(input, owner + " length");
        byte[] bytes = new byte[length];
        requireRemaining(input, length, owner);
        input.get(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.isEmpty() || value.indexOf('\0') >= 0
                || !Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException(owner + " string is not canonical UTF-8");
        }
        return value;
    }

    private static String readKey(ByteBuffer input, String owner) {
        String value = readDelimited(input, owner);
        Mc263FeatureBlockState.requireCanonicalResourceKey(value, owner);
        return value;
    }

    private static String readAsciiDelimited(ByteBuffer input, String owner) {
        int length = readUnsignedShort(input, owner + " length");
        requireRemaining(input, length, owner);
        byte[] bytes = new byte[length];
        input.get(bytes);
        String value = new String(bytes, StandardCharsets.US_ASCII);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.US_ASCII))
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(owner + " string is not canonical ASCII");
        }
        return value;
    }

    private static int readInt(ByteBuffer input, String owner) {
        requireRemaining(input, Integer.BYTES, owner);
        return input.getInt();
    }

    private static void requireBlockPosition(int localX, int blockY, int localZ, String owner) {
        if (localX < 0 || localX >= Blocks.CHUNK_X || localZ < 0 || localZ >= Blocks.CHUNK_Z
                || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
            throw new IllegalArgumentException(owner + " position drift");
        }
    }

    private static void skipWords(ByteBuffer input, int count, int width, String owner) {
        if (count < 0 || width <= 0 || count > input.remaining() / width) {
            throw new IllegalArgumentException(owner + " payload is truncated");
        }
        skip(input, count * width, owner);
    }

    private static void skip(ByteBuffer input, int bytes, String owner) {
        if (bytes < 0 || bytes > input.remaining()) {
            throw new IllegalArgumentException(owner + " payload is truncated");
        }
        input.position(input.position() + bytes);
    }

    private static void requireRemaining(ByteBuffer input, int bytes, String owner) {
        if (bytes < 0 || input.remaining() < bytes) {
            throw new IllegalArgumentException("truncated " + owner);
        }
    }

    private static byte[] readSection(ByteBuffer input, String name) {
        if (input.remaining() < Integer.BYTES) {
            throw new IllegalArgumentException("missing " + name + " length");
        }
        int length = input.getInt();
        if (length < 0 || length > input.remaining()) {
            throw new IllegalArgumentException("invalid " + name + " length");
        }
        byte[] value = new byte[length];
        input.get(value);
        return value;
    }

    private static void requireBytes(ByteBuffer input, byte[] expected, String name) {
        if (input.remaining() < expected.length) {
            throw new IllegalArgumentException("truncated " + name);
        }
        byte[] actual = new byte[expected.length];
        input.get(actual);
        if (!Arrays.equals(actual, expected)) {
            throw new IllegalArgumentException(name + " mismatch");
        }
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(byte[] value) { return sha256Digest().digest(value); }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record TargetIdentity(long worldSeed, int chunkX, int chunkZ) {}
    private record ProvenanceTarget(long worldSeed, int chunkX, int chunkZ,
                                    Mc263StructureCarrier predecessor) {}
    private record OriginReceipt(byte[] bytes, Mc263StructureCarrier successor) {
        private OriginReceipt {
            bytes = bytes.clone();
            Objects.requireNonNull(successor, "origin successor");
        }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    private static byte[] copy(byte[] value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length == 0) throw new IllegalArgumentException(name + " is empty");
        return Arrays.copyOf(value, value.length);
    }

    private static boolean allZero(byte[] value) {
        for (byte item : value) if (item != 0) return false;
        return true;
    }
}
