package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.CanonicalPostprocessActivationContext;
import com.gameexpert.terrain.Mc263BaseHeightSampler;
import com.gameexpert.terrain.Mc263FeaturesRegionBridge;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrierOrigin;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.terrain.persistence.InMemoryCanonicalWorldgenStore;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/** One proposal against a complete immutable host snapshot; never commits host world state. */
public final class LegacyGenerationRequest {
    private LegacyGenerationRequest() { }

    public static byte[] execute(DataInputStream input) throws Exception {
        long worldId = input.readLong();
        int seed = input.readInt();
        long gameTime = input.readLong();
        int x = input.readInt();
        int z = input.readInt();
        String expectedReferenceReceipt = input.readUTF();
        int rows = input.readInt();
        if (worldId <= 0 || gameTime < 0 || rows < 0 || rows > 1_000_000
                || !expectedReferenceReceipt.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid generation snapshot binding");
        }
        String identity = (String) Class.forName("com.gameexpert.world.WorldBaseline")
                .getField("ID").get(null);
        var store = new InMemoryCanonicalWorldgenStore();
        var snapshots = new ArrayList<CanonicalWorldgenStore.CanonicalChunkSnapshot>();
        for (int index = 0; index < rows; index++) {
            int chunkX = input.readInt();
            int chunkZ = input.readInt();
            var commit = new CanonicalWorldgenStore.ChunkCommit(worldId, identity,
                    chunkX, chunkZ, readBytes(input, false), readBytes(input, false),
                    readBytes(input, true), readBytes(input, false));
            snapshots.add(store.commit(commit));
        }
        if (input.available() != 0) throw new IllegalArgumentException("trailing generation data");
        var references = CanonicalWorldgenStore.freezeStructureReferences(worldId, snapshots);
        if (!references.receipt().equals(expectedReferenceReceipt)) {
            throw new IllegalArgumentException("transferred structure snapshot receipt mismatch");
        }
        CanonicalWorldgenStore.CanonicalChunkSnapshot existing = store.find(worldId, x, z);
        CanonicalWorldgenStore.ChunkCommit proposal;
        if (existing != null) {
            proposal = existing.commit();
        } else {
            var maps = Mc263LocatedMapAuthority.pinnedBiomePreview(requestedSeed -> {
                if (requestedSeed != seed) throw new IllegalArgumentException("foreign snapshot seed");
                return references;
            });
            var contexts = Mc263ProductionContextCatalog.liveProvider(maps);
            var world = Mc263StructureCarrierOrigin.prepare(seed, Mc263BaseHeightSampler.overworld(seed));
            var assembly = Mc263StructureCarrierOrigin.assemble(world, x, z,
                    Mc263StructureCarrierOrigin.RegionMemo.bounded(1));
            try (var builders = Mc263FeaturesRegionBridge.newInputBuilderContext(1)) {
                var pending = Mc263FeaturesRegionBridge.startPostCarversInput(builders,
                        seed, x, z, assembly.carrier(), Mc263FeaturesRegionBridge.RegionMemo.bounded(25));
                var product = Mc263FeaturesRegionBridge.generateCanonicalProductFromInput(
                        seed, x, z, assembly.carrier(), contexts,
                        new CanonicalPostprocessActivationContext(gameTime), pending.finish());
                byte[] successor = product.structureCarrier().receiptBytes();
                proposal = new CanonicalWorldgenStore.ChunkCommit(worldId, identity, x, z,
                        product.finalCarrier(), successor, successor, product.commitFingerprint());
            }
        }
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(0x57504731);
            output.writeByte(1);
            output.writeLong(worldId);
            output.writeInt(seed);
            output.writeInt(x);
            output.writeInt(z);
            output.writeUTF(expectedReferenceReceipt);
            writeBytes(output, proposal.finalCarrier());
            writeBytes(output, proposal.structureCarrier());
            writeBytes(output, proposal.mutablePieceSuccessor());
            writeBytes(output, proposal.fingerprint());
        }
        return bytes.toByteArray();
    }

    private static byte[] readBytes(DataInputStream input, boolean nullable) throws IOException {
        int length = input.readInt();
        if (nullable && length == -1) return null;
        if (length <= 0 || length > 64 * 1024 * 1024 || length > input.available()) {
            throw new IllegalArgumentException("invalid snapshot carrier length");
        }
        return input.readNBytes(length);
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes == null ? -1 : bytes.length);
        if (bytes != null) output.write(bytes);
    }
}
