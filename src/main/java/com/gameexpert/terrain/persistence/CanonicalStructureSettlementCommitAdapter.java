package com.gameexpert.terrain.persistence;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dormant transaction boundary for one exact STR263C1/MCF263LC structure settlement.
 *
 * <p>The adapter deliberately has no Spring component annotation or production caller. Once a
 * structure executor is activated, registering this type makes its single store call join the
 * caller's transaction. The canonical store owns exact replay and collision handling.</p>
 */
public class CanonicalStructureSettlementCommitAdapter {
    private final CanonicalWorldgenStore store;

    public CanonicalStructureSettlementCommitAdapter(CanonicalWorldgenStore store) {
        this.store = Objects.requireNonNull(store, "canonical worldgen store");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CanonicalWorldgenStore.CanonicalChunkSnapshot commitJoiningTransaction(
            Settlement settlement) {
        Objects.requireNonNull(settlement, "structure settlement");
        return store.commit(new CanonicalWorldgenStore.ChunkCommit(
                settlement.worldId(), settlement.worldIdentity(), settlement.chunkX(),
                settlement.chunkZ(), settlement.finalCarrier(), settlement.structureCarrier(),
                settlement.mutablePieceSuccessor(), settlement.fingerprint()));
    }

    /** Exact durable values produced by swamp-hut or desert-pyramid settlement. */
    public static final class Settlement {
        private final long worldId;
        private final String worldIdentity;
        private final int chunkX;
        private final int chunkZ;
        private final byte[] structureCarrier;
        private final byte[] finalCarrier;
        private final byte[] mutablePieceSuccessor;
        private final byte[] fingerprint;

        public Settlement(long worldId, String worldIdentity, int chunkX, int chunkZ,
                byte[] structureCarrier, byte[] finalCarrier, byte[] mutablePieceSuccessor,
                byte[] fingerprint) {
            this.worldId = worldId;
            this.worldIdentity = Objects.requireNonNull(worldIdentity, "world identity");
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.structureCarrier = copy(structureCarrier, "STR263C1 carrier");
            this.finalCarrier = copy(finalCarrier, "MCF263LC carrier");
            this.mutablePieceSuccessor = copy(
                    mutablePieceSuccessor, "mutable piece/start successor");
            this.fingerprint = copy(fingerprint, "settlement fingerprint");
            if (this.fingerprint.length != 32) {
                throw new IllegalArgumentException("settlement fingerprint must be SHA-256");
            }

            Mc263StructureCarrier decodedStructures =
                    Mc263StructureCarrier.decode(this.structureCarrier);
            if (decodedStructures.referenceChunk(chunkX, chunkZ).isEmpty()) {
                throw new IllegalArgumentException(
                        "settlement structure carrier is missing target references");
            }
            Mc263FinalChunkCodec.FinalChunk decoded = Mc263FinalChunkCodec.decode(
                    this.finalCarrier);
            if (decoded.chunkX() != chunkX || decoded.chunkZ() != chunkZ) {
                throw new IllegalArgumentException("settlement final-carrier coordinate mismatch");
            }
        }

        public long worldId() { return worldId; }
        public String worldIdentity() { return worldIdentity; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public byte[] structureCarrier() { return structureCarrier.clone(); }
        public byte[] finalCarrier() { return finalCarrier.clone(); }
        public byte[] mutablePieceSuccessor() { return mutablePieceSuccessor.clone(); }
        public byte[] fingerprint() { return fingerprint.clone(); }

        private static byte[] copy(byte[] value, String name) {
            Objects.requireNonNull(value, name);
            if (value.length == 0) throw new IllegalArgumentException(name + " is empty");
            return Arrays.copyOf(value, value.length);
        }
    }
}
