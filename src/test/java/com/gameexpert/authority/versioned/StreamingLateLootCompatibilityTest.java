package com.gameexpert.authority.versioned;

import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAssignmentPlan;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import java.util.stream.IntStream;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingLateLootCompatibilityTest {
    private static final long WORLD = 743L;
    private static final int SEED = 0;
    private static final WorldGenerationProfile PROFILE = WorldGenerationProfiles.CURRENT;

    @Test
    void authenticatedLootMatchesShippedWorkerAndStreamsBeyond64MiB() throws Exception {
        CanonicalWorldgenStore.ChunkCommit generated = produce(0, 0);
        NeutralFinalChunk source = fixtureLootCarrier();
        NeutralFinalChunk.ContainerLootDeclaration declaration = source.sidecars().containerLootDeclarations()
                .stream().filter(value -> value.sourceSection() == NeutralFinalChunk.ContainerLootSourceSection.LOOT)
                .filter(value -> value.productionContext().tableIdentity().equals("minecraft:chests/simple_dungeon"))
                .findFirst().orElseThrow();
        NeutralFinalChunk.ProductionContext context = declaration.productionContext();
        byte[] payload = CanonicalLootAssignmentPlan.encodeLocatedProductionContextPayload(
                new CanonicalLootAssignmentPlan.LocatedProductionContextPayload(declaration.ordinal(),
                        declaration.sourceSection(), declaration.sourceSectionOrdinal(), declaration.containerSize(),
                        context, declaration.producerSourceSha256(), declaration.sourceDeclarationSha256()));
        long rawSeed = source.sidecars().loot().get(declaration.sourceSectionOrdinal()).seed();
        assertThat(rawSeed).isNotZero();
        List<CanonicalStructureSnapshot.Row> rows = new ArrayList<>();
        rows.add(new CanonicalStructureSnapshot.Row(generated.chunkX(), generated.chunkZ(), generated.structureCarrier()));
        List<LateLootOutcome.Claim> claims = List.of();
        boolean completed = false;
        for (int attempt = 0; attempt < 12; attempt++) {
            CanonicalStructureSnapshot snapshot = new CanonicalStructureSnapshot(WORLD, PROFILE, rows);
            LateLootOutcome streamed = source.prepareLateLoot(payload, SEED, context.tableIdentity(), rawSeed,
                    context.originX(), context.originY(), context.originZ(), declaration.containerSize(),
                    null, null, snapshot, claims);
            byte[] legacy = legacy(source, declaration, payload, rawSeed, snapshot, claims);
            assertThat(streamed.encoded()).as("attempt %s", attempt).containsExactly(legacy);
            if (streamed.needsStructure()) {
                CanonicalWorldgenStore.ChunkCommit needed = produce(streamed.neededChunkX(), streamed.neededChunkZ());
                rows.add(new CanonicalStructureSnapshot.Row(needed.chunkX(), needed.chunkZ(), needed.structureCarrier()));
                continue;
            }
            assertThat(streamed.resolution()).isNotEmpty();
            byte[] carrier = generated.structureCarrier();
            int count = (64 * 1024 * 1024) / carrier.length + 2;
            List<CanonicalStructureSnapshot.Row> largeRows = IntStream.range(0, count)
                    .mapToObj(index -> CanonicalStructureSnapshot.Row.lazy(index + 1000000, 0,
                            carrier.length, () -> carrier)).toList();
            assertThat((long) count * carrier.length).isGreaterThan(64L * 1024 * 1024);
            CanonicalStructureSnapshot large = new CanonicalStructureSnapshot(WORLD, PROFILE, largeRows);
            LateLootOutcome largeOutcome = source.prepareLateLoot(payload, SEED, context.tableIdentity(), rawSeed,
                    context.originX(), context.originY(), context.originZ(), declaration.containerSize(),
                    null, null, large, claims);
            assertThat(largeOutcome.needsStructure()).isFalse();
            assertThat(largeOutcome.resolvedContext()).isEqualTo(streamed.resolvedContext());
            assertThat(largeOutcome.resolution()).containsExactly(streamed.resolution());
            completed = true;
            break;
        }
        assertThat(completed).as("bounded fixture preparation completed").isTrue();
    }

    private static InputStream getClassResource(String name) {
        return StreamingLateLootCompatibilityTest.class.getResourceAsStream("/generation-producers/" + name);
    }

    // Authenticated protocol fixture, not evidence of a naturally spawned container.
    private static NeutralFinalChunk fixtureLootCarrier() throws Exception {
        String table = "minecraft:chests/simple_dungeon";
        String producer = Mc263FinalChunkSidecars.CONTAINER_LOOT_PRODUCER_SOURCE_SHA256;
        Mc263LocatedMapAuthority locator = Mc263LocatedMapAuthority.pinnedUnreferencedFixture(
                Mc263LocatedMapAuthority.biomePreviewRenderer());
        Map<String, Mc263LocatedMapAuthority.LocatedMapTarget> targets = new LinkedHashMap<>();
        Mc263ContainerLootResolver.requiredMapDestinations(table).forEach(destination ->
                targets.put(destination, locator.locate(0L, producer, table, 0, 64, 0, destination)));
        String receipt = Mc263ContainerLootResolver.targetProductionContextReceipt(
                "minecraft:ocean", "0", producer, table, 0, 64, 0, targets);
        Mc263ContainerLootResolver.LocatedProductionContext context =
                Mc263ContainerLootResolver.LocatedProductionContext.authenticated(
                        "minecraft:ocean", targets, "0", producer, table, 0, 64, 0, receipt);
        Mc263FinalChunkSidecars.Loot loot = new Mc263FinalChunkSidecars.Loot(
                Blocks.blockIndex(0, 64, 0), "north", table, 7L);
        Method binding = Mc263FinalChunkSidecars.class.getDeclaredMethod("sourceDeclarationBinding",
                int.class, int.class, int.class, int.class, Mc263FinalChunkSidecars.Loot.class,
                int.class, Mc263ContainerLootResolver.LootProductionContext.class, String.class);
        binding.setAccessible(true);
        String declarationReceipt = (String) binding.invoke(null, 0, 0, 0, 0, loot, 27, context, producer);
        Mc263FinalChunkSidecars.ContainerLootDeclaration declaration =
                new Mc263FinalChunkSidecars.ContainerLootDeclaration(0,
                        Mc263FinalChunkSidecars.ContainerLootSourceSection.LOOT, 0, 27,
                        context, producer, declarationReceipt);
        Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(List.of(), List.of(), List.of(loot),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(declaration));
        short[] blocks = new short[Blocks.CHUNK_BLOCKS];
        Arrays.fill(blocks, (short) Blocks.AIR);
        blocks[loot.packed()] = (short) Blocks.CHEST;
        int[] heights = new int[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        Arrays.fill(heights, Blocks.MIN_Y);
        byte[] encoded = Mc263FinalChunkCodec.encode(new Mc263FinalChunkCodec.FinalChunk(0, 0,
                blocks, Map.of(), heights, heights, heights, sidecars));
        return ProducerAuthorities.verify(PROFILE, 0, 0, encoded);
    }

    private static byte[] legacy(NeutralFinalChunk source, NeutralFinalChunk.ContainerLootDeclaration declaration,
            byte[] payload, long rawSeed, CanonicalStructureSnapshot snapshot,
            List<LateLootOutcome.Claim> claims) throws IOException {
        NeutralFinalChunk.ProductionContext context = declaration.productionContext();
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        DataOutputStream request = new DataOutputStream(requestBytes);
        request.writeInt(source.chunkX());
        request.writeInt(source.chunkZ());
        ProducerStoreWire.write(request, source.encodedCarrier());
        ProducerStoreWire.write(request, payload);
        request.writeLong(SEED);
        request.writeUTF(context.tableIdentity());
        request.writeLong(rawSeed);
        request.writeInt(context.originX());
        request.writeInt(context.originY());
        request.writeInt(context.originZ());
        request.writeInt(declaration.containerSize());
        request.writeBoolean(false);
        request.flush();
        ByteArrayOutputStream snapshotBytes = new ByteArrayOutputStream();
        DataOutputStream snapshotOut = new DataOutputStream(snapshotBytes);
        snapshotOut.writeLong(WORLD);
        snapshotOut.writeInt(snapshot.rows().size());
        for (CanonicalStructureSnapshot.Row row : snapshot.rows()) {
            snapshotOut.writeInt(row.chunkX());
            snapshotOut.writeInt(row.chunkZ());
            ProducerStoreWire.write(snapshotOut, row.carrier());
        }
        snapshotOut.flush();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = ProducerStoreWire.header(bytes, PROFILE, 14);
        ProducerStoreWire.write(out, requestBytes.toByteArray());
        ProducerStoreWire.write(out, snapshotBytes.toByteArray());
        out.writeInt(claims.size());
        for (LateLootOutcome.Claim claim : claims) {
            out.writeUTF(claim.structureId());
            out.writeInt(claim.originChunkX());
            out.writeInt(claim.originChunkZ());
            out.writeUTF(claim.structureRowSha256());
        }
        out.flush();
        try (InputStream manifest = getClassResource("current-worker.properties");
                InputStream graph = getClassResource("current-source-graph.json")) {
            List<IsolatedProducerSession.PinnedJar> jars = ProducerRuntimeManifest.read(
                    manifest, ProducerBundle.directory(), PROFILE);
            try (IsolatedProducerSession shipped = new IsolatedProducerSession(jars, PROFILE,
                    jars.get(1).sha256(), graph.readAllBytes())) {
                return shipped.exchange(bytes.toByteArray());
            }
        }
    }

    private static CanonicalWorldgenStore.ChunkCommit produce(int x, int z) throws IOException {
        CanonicalStructureSnapshot empty = new CanonicalStructureSnapshot(WORLD, PROFILE, List.of());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = ProducerStoreWire.header(bytes, PROFILE, 13);
        output.writeLong(WORLD);
        output.writeInt(SEED);
        output.writeLong(0L);
        output.writeInt(x);
        output.writeInt(z);
        output.writeUTF(empty.receipt());
        output.writeInt(0);
        output.flush();
        try (DataInputStream input = ProducerStoreWire.response(
                ProducerAuthorities.forGeneration(PROFILE).exchange(bytes.toByteArray()))) {
            assertThat(input.readLong()).isEqualTo(WORLD);
            assertThat(input.readInt()).isEqualTo(SEED);
            assertThat(input.readInt()).isEqualTo(x);
            assertThat(input.readInt()).isEqualTo(z);
            assertThat(input.readUTF()).isEqualTo(empty.receipt());
            CanonicalWorldgenStore.ChunkCommit commit = new CanonicalWorldgenStore.ChunkCommit(WORLD,
                    PROFILE.getBaselineId(), x, z, ProducerStoreWire.read(input, false),
                    ProducerStoreWire.read(input, false), ProducerStoreWire.read(input, true),
                    ProducerStoreWire.read(input, false));
            assertThat(input.available()).isZero();
            return commit;
        }
    }
}
