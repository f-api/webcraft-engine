package com.gameexpert.tnt.service.explosion;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import com.gameexpert.api.persistence.WorldAccess;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Reads absent-overlay cells only from the immutable current canonical final carrier. */
@Component
public final class CanonicalWorldgenBaseBlockAuthority
        implements PrimedTntPersistenceService.CanonicalBaseBlockAuthority {
    public static final long CANONICAL_REVISION =
            ((long) CanonicalWorldgenStore.ABI_VERSION << Integer.SIZE)
                    | Integer.toUnsignedLong(CanonicalWorldgenStore.FINAL_CARRIER_SCHEMA);

    private final CanonicalWorldgenStore worldgen;

    public CanonicalWorldgenBaseBlockAuthority(CanonicalWorldgenStore worldgen) {
        this.worldgen = Objects.requireNonNull(worldgen, "canonical worldgen store");
    }

    @Override
    public String canonicalProductIdentity(WorldAccess world) {
        return Objects.requireNonNull(world, "canonical world").generationProfile().getBaselineId();
    }

    @Override
    public PrimedTntPersistenceService.BaseBlockWitness preflightExact(WorldAccess world,
            ExplosionSettlementCommand.BlockTransition transition) {
        if (world == null || world.getId() == null
                || transition == null
                || transition.y() < Blocks.MIN_Y || transition.y() > Blocks.MAX_Y
                || transition.provenance().overlayState()
                        != ExplosionSettlementCommand.OverlayState.CANONICAL_BASE) {
            throw new IllegalStateException("current canonical base authority is required");
        }
        int chunkX = Math.floorDiv(transition.x(), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(transition.z(), Blocks.CHUNK_Z);
        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot =
                worldgen.find(world.getId(), chunkX, chunkZ);
        if (snapshot == null || snapshot.commit() == null) {
            throw new IllegalStateException("canonical explosion chunk is absent");
        }
        CanonicalWorldgenStore.ChunkCommit commit = snapshot.commit();
        if (commit.worldId() != world.getId()
                || !canonicalProductIdentity(world).equals(commit.worldIdentity())
                || commit.chunkX() != chunkX || commit.chunkZ() != chunkZ) {
            throw new IllegalStateException("canonical explosion chunk identity mismatch");
        }
        var carrier = commit.semanticFinalChunk();
        if (carrier.chunkX() != chunkX || carrier.chunkZ() != chunkZ
                || carrier.blockIds().length != Blocks.CHUNK_BLOCKS) {
            throw new IllegalStateException("canonical explosion carrier is malformed");
        }
        int packed = Blocks.blockIndex(Math.floorMod(transition.x(), Blocks.CHUNK_X),
                transition.y(), Math.floorMod(transition.z(), Blocks.CHUNK_Z));
        short blockType = carrier.blockIds()[packed];
        var exactState = carrier.stateOverrides().get(packed);
        int stateCode = exactState == null ? 0 : exactState.stateCode();
        if (stateCode < 0 || stateCode > Short.MAX_VALUE) {
            throw new IllegalStateException("canonical explosion state code is invalid");
        }
        return new PrimedTntPersistenceService.BaseBlockWitness(world.getId(),
                transition.x(), transition.y(), transition.z(), blockType, (short) stateCode,
                canonicalProductIdentity(world), HexFormat.of().formatHex(commit.fingerprint()),
                CANONICAL_REVISION);
    }
}
