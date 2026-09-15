package com.gameexpert.banner.service;

import com.gameexpert.banner.entity.WorldBannerBlock;
import com.gameexpert.banner.repository.WorldBannerBlockRepository;
import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.api.persistence.WorldStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomic persistence boundary for the block diff and its colocated patterned-banner entity. */
@Service
public class BannerBlockPersistenceService {
    private final WorldBannerBlockRepository banners;
    private final WorldBlockDiffRepository blockDiffs;
    private final WorldStore worlds;

    public BannerBlockPersistenceService(WorldBannerBlockRepository banners,
            WorldBlockDiffRepository blockDiffs, WorldStore worlds) {
        this.banners = banners;
        this.blockDiffs = blockDiffs;
        this.worlds = worlds;
    }

    @Transactional(readOnly = true)
    public List<BannerBlockData> loadWorld(Long worldId) {
        return banners.findAllByWorldId(worldId).stream()
                .map(row -> new BannerBlockData(row.getX(), row.getY(), row.getZ(),
                        BannerPatternCodec.decode(row.getPatternData())))
                .toList();
    }

    @Transactional
    public void place(Long worldId, int x, int y, int z, short blockType, short blockState,
            List<ItemComponentData.BannerLayer> patterns) {
        placeJoiningTransaction(worldId, x, y, z, blockType, blockState, patterns);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void placeJoiningTransaction(Long worldId, int x, int y, int z,
            short blockType, short blockState, List<ItemComponentData.BannerLayer> patterns) {
        if (!Blocks.isBanner(Short.toUnsignedInt(blockType))) {
            throw new IllegalArgumentException("placed banner requires banner block ID");
        }
        String encoded = BannerPatternCodec.encode(patterns);
        var world = worlds.getReferenceById(worldId);
        WorldBlockDiff diff = blockDiffs.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseGet(() -> new WorldBlockDiff(world, x, y, z, blockType, blockState));
        diff.replace(blockType, blockState, null);
        blockDiffs.save(diff);
        WorldBannerBlock banner = banners.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseGet(() -> new WorldBannerBlock(world, x, y, z, encoded));
        banner.replacePatternData(encoded);
        banners.save(banner);
    }

    @Transactional
    public void remove(Long worldId, int x, int y, int z, short replacementType,
            short replacementState) {
        removeJoiningTransaction(worldId, x, y, z, replacementType, replacementState);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeJoiningTransaction(Long worldId, int x, int y, int z,
            short replacementType, short replacementState) {
        if (Blocks.isBanner(Short.toUnsignedInt(replacementType))) {
            throw new IllegalArgumentException("banner replacement requires place operation");
        }
        var world = worlds.getReferenceById(worldId);
        WorldBlockDiff diff = blockDiffs.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseGet(() -> new WorldBlockDiff(world, x, y, z, replacementType, replacementState));
        diff.replace(replacementType, replacementState, null);
        blockDiffs.save(diff);
        banners.deleteAt(worldId, x, y, z);
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        banners.deleteAllByWorldId(worldId);
    }

    /** Follows placement's block-then-banner lock order and removes only the captured source. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeExactJoiningTransaction(Long worldId, BannerBlockData expected,
            short expectedType, short expectedState) {
        WorldBlockDiff diff = blockDiffs.findLockedAt(
                worldId, expected.x(), expected.y(), expected.z())
                .orElseThrow(() -> new IllegalStateException("banner mining block is absent"));
        WorldBannerBlock banner = banners.findLockedAt(
                worldId, expected.x(), expected.y(), expected.z())
                .orElseThrow(() -> new IllegalStateException("banner mining source is absent"));
        if (diff.getBlockType() != expectedType || diff.getBlockState() != expectedState
                || !BannerPatternCodec.encode(BannerPatternCodec.decode(banner.getPatternData()))
                        .equals(BannerPatternCodec.encode(expected.patterns()))) {
            throw new IllegalStateException("banner mining source changed");
        }
        diff.replace((short) Blocks.AIR, (short) 0, null);
        blockDiffs.saveAndFlush(diff);
        banners.delete(banner);
        banners.flush();
    }
}
