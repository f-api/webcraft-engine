package com.gameexpert.authority.versioned;

import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.terrain.persistence.InMemoryCanonicalWorldgenStore;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationDeclarationCompatibilityTest {
    private static final long WORLD_ID = 731L;
    private static final int SEED = 1464029505;
    private static final WorldGenerationProfile PROFILE = WorldGenerationProfiles.CURRENT;

    @Test
    void realProducerPreservesGeometryAndReplaysPreviouslyCommittedCarrier() throws IOException {
        CanonicalStructureSnapshot empty = new CanonicalStructureSnapshot(WORLD_ID, PROFILE, List.of());
        CanonicalWorldgenStore.ChunkCommit neighbor = produce(empty, -2, -2);
        CanonicalStructureSnapshot priorWorld = new CanonicalStructureSnapshot(WORLD_ID, PROFILE,
                List.of(new CanonicalStructureSnapshot.Row(-2, -2, neighbor.structureCarrier())));
        assertThat(priorWorld.receipt()).isNotEqualTo(empty.receipt());

        CanonicalWorldgenStore.ChunkCommit previousGeneration = produce(priorWorld, -1, -2);
        CanonicalWorldgenStore.ChunkCommit declarationGeneration = produce(empty, -1, -2);
        NeutralFinalChunk previous = previousGeneration.semanticFinalChunk();
        NeutralFinalChunk current = declarationGeneration.semanticFinalChunk();
        assertThat(current.blockIds()).containsExactly(previous.blockIds());
        assertThat(current.stateOverrides()).isEqualTo(previous.stateOverrides());
        assertThat(current.worldSurfaceWg()).containsExactly(previous.worldSurfaceWg());
        assertThat(current.oceanFloorWg()).containsExactly(previous.oceanFloorWg());
        assertThat(current.motionBlocking()).containsExactly(previous.motionBlocking());
        assertThat(declarationGeneration.structureCarrier()).containsExactly(previousGeneration.structureCarrier());

        InMemoryCanonicalWorldgenStore store = new InMemoryCanonicalWorldgenStore();
        store.commit(neighbor);
        store.commit(previousGeneration);
        IsolatedChunkProductSource source = new IsolatedChunkProductSource(store, WORLD_ID, SEED, PROFILE, 0L);
        ChunkGenerator.GeneratedChunk replay = source.generate(SEED, -1, -2);
        assertThat(replay.blocks()).containsExactly(previous.blockIds());
        assertThat(store.find(WORLD_ID, -1, -2).commit().finalCarrier())
                .containsExactly(previousGeneration.finalCarrier());
        assertThat(store.find(WORLD_ID, -1, -2).commit().fingerprint())
                .containsExactly(previousGeneration.fingerprint());
    }

    private static CanonicalWorldgenStore.ChunkCommit produce(
            CanonicalStructureSnapshot snapshot, int chunkX, int chunkZ
    ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = ProducerStoreWire.header(bytes, PROFILE, 13);
        output.writeLong(WORLD_ID);
        output.writeInt(SEED);
        output.writeLong(0L);
        output.writeInt(chunkX);
        output.writeInt(chunkZ);
        output.writeUTF(snapshot.receipt());
        output.writeInt(snapshot.rows().size());
        for (CanonicalStructureSnapshot.Row row : snapshot.rows()) {
            output.writeInt(row.chunkX());
            output.writeInt(row.chunkZ());
            ProducerStoreWire.write(output, row.carrier());
        }
        output.flush();
        try (DataInputStream input = ProducerStoreWire.response(
                ProducerAuthorities.forGeneration(PROFILE).exchange(bytes.toByteArray()))) {
            assertThat(input.readLong()).isEqualTo(WORLD_ID);
            assertThat(input.readInt()).isEqualTo(SEED);
            assertThat(input.readInt()).isEqualTo(chunkX);
            assertThat(input.readInt()).isEqualTo(chunkZ);
            assertThat(input.readUTF()).isEqualTo(snapshot.receipt());
            CanonicalWorldgenStore.ChunkCommit commit = new CanonicalWorldgenStore.ChunkCommit(
                    WORLD_ID, PROFILE.getBaselineId(), chunkX, chunkZ,
                    ProducerStoreWire.read(input, false), ProducerStoreWire.read(input, false),
                    ProducerStoreWire.read(input, true), ProducerStoreWire.read(input, false));
            assertThat(input.available()).isZero();
            return commit;
        }
    }
}
