package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.TreeMap;

/** Byte-only adapter loaded alongside immutable producer classes, never in the app namespace. */
public final class LegacyProducerWorker {
    private static final int MAGIC = 0x57504731;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private LegacyProducerWorker() { }

    /** One reviewed outer-profile binding, checked against the real private producer jar. */
    public static void configure(byte[] configuration) throws Exception {
        WorkerProfileBinding.configure(configuration);
    }

    public static void close() { WorkerSnapshotUpload.close(); LegacyStructureGenerationRequest.close(); }

    /** V1 operation 1 verifies a final carrier and returns its basic gameplay projection. */
    public static byte[] dispatch(byte[] request) throws Exception {
        if (request == null || request.length > MAX_BYTES) {
            throw new IllegalArgumentException("producer frame exceeds bounds");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(request))) {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != 1) {
                throw new IllegalArgumentException("unsupported producer wire version");
            }
            WorkerProfileBinding.requireRequest(input);
            int operation = input.readUnsignedByte();
            if (operation >= 15 && operation <= 20) return WorkerSnapshotUpload.execute(input, operation);
            if (operation == 2) return LegacyGenerationRequest.execute(input);
            if (operation == 8 || operation == 9) return WorkerLootOperations.execute(input, operation == 8);
            if (operation == 3) return LegacyStoreOperations.validateCommit(input);
            if (operation == 6) return LegacyStoreOperations.references(input);
            if (operation == 7) return LegacyMobOperations.matches(input);
            if (operation == 11) return LegacyStateOperations.decode(input);
            if (operation == 12) return LegacyStateOperations.exact(input);
            if (operation == 10) return LegacyMapPreviewOperations.render(input);
            if (operation == 13) return LegacyStructureGenerationRequest.execute(input);
            if (operation == 14) return WorkerLateLootOperations.execute(input);
            if (operation == 4) return LegacyCarrierOperations.subset(input);
            if (operation == 5) return LegacyCarrierOperations.defaults(input);
            if (operation != 1) throw new IllegalArgumentException("unsupported producer operation");
            int x = input.readInt();
            int z = input.readInt();
            int length = input.readInt();
            if (length <= 0 || length > MAX_BYTES || length != input.available()) {
                throw new IllegalArgumentException("invalid final carrier frame length");
            }
            var carrier = Mc263FinalChunkCodec.decode(input.readNBytes(length));
            if (carrier.chunkX() != x || carrier.chunkZ() != z) {
                throw new IllegalArgumentException("producer target coordinate mismatch");
            }
            return project(carrier);
        }
    }

    static byte[] project(Mc263FinalChunkCodec.FinalChunk carrier) throws IOException {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeByte(1);
                output.writeInt(carrier.chunkX());
                output.writeInt(carrier.chunkZ());
                short[] blocks = carrier.blockIds();
                output.writeInt(blocks.length);
                for (short block : blocks) output.writeShort(block);
                output.writeInt(carrier.stateOverrides().size());
                for (var entry : new TreeMap<>(carrier.stateOverrides()).entrySet()) {
                    output.writeInt(entry.getKey());
                    output.writeInt(Mc263ExactStateCodec.stateCode(entry.getValue()));
                    output.writeUTF(entry.getValue().exactState());
                    output.writeBoolean(entry.getValue().isLeavesTag());
                    output.writeUTF(entry.getValue().fluidTypeKey());
                }
                writeInts(output, carrier.worldSurfaceWg());
                writeInts(output, carrier.oceanFloorWg());
                writeInts(output, carrier.motionBlocking());
                LegacySidecarProjectionWriter.write(output, carrier.sidecars());
            }
            return bytes.toByteArray();
    }

    private static void writeInts(DataOutputStream output, int[] values) throws IOException {
        output.writeInt(values.length);
        for (int value : values) output.writeInt(value);
    }
}
