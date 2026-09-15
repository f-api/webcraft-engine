package com.gameexpert.sign.service;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.sign.dto.SignBlockData;
import com.gameexpert.sign.entity.WorldSignBlock;
import com.gameexpert.sign.repository.WorldSignBlockRepository;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.api.persistence.WorldStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Atomic placed-sign text and colocated block-diff persistence. */
@Service
public class SignBlockPersistenceService {
    public enum GeneratedInstallOutcome { COMMITTED, IDEMPOTENT }
    private final WorldSignBlockRepository signs;
    private final WorldBlockDiffRepository blocks;
    private final WorldStore worlds;

    public SignBlockPersistenceService(WorldSignBlockRepository signs,
            WorldBlockDiffRepository blocks, WorldStore worlds) {
        this.signs = signs; this.blocks = blocks; this.worlds = worlds;
    }

    @Transactional(readOnly = true)
    public List<SignBlockData> loadWorld(Long worldId) {
        return signs.findAllByWorldId(worldId).stream().map(WorldSignBlock::snapshot).toList();
    }

    @Transactional
    public void place(Long worldId, int x, int y, int z, short blockType, short state,
            List<String> lines) {
        int id = Short.toUnsignedInt(blockType);
        if (id != Blocks.POPLAR_SIGN && id != Blocks.POPLAR_HANGING_SIGN) {
            throw new IllegalArgumentException("poplar sign required");
        }
        SignBlockData data = new SignBlockData(x, y, z, lines);
        var world = worlds.getReferenceById(worldId);
        WorldBlockDiff diff = blocks.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseGet(() -> new WorldBlockDiff(world, x, y, z, blockType, state));
        diff.replace(blockType, state, null);
        blocks.save(diff);
        WorldSignBlock row = signs.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseGet(() -> new WorldSignBlock(world, data));
        row.replace(data.lines());
        signs.save(row);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GeneratedInstallOutcome installGeneratedJoiningTransaction(Long worldId,
            int x, int y, int z, List<String> lines, String installationId,
            String installationFingerprint) {
        SignBlockData data = new SignBlockData(x, y, z, lines);
        WorldSignBlock row = signs.findByWorldIdAndXAndYAndZ(worldId, x, y, z).orElse(null);
        if (row != null) {
            row.requireSameGeneratedInstallation(installationId, installationFingerprint);
            return GeneratedInstallOutcome.IDEMPOTENT;
        }
        row = new WorldSignBlock(worlds.getReferenceById(worldId), data);
        row.claimGeneratedInstallation(installationId, installationFingerprint);
        signs.save(row);
        return GeneratedInstallOutcome.COMMITTED;
    }

    @Transactional
    public void remove(Long worldId, int x, int y, int z, short replacementType,
            short replacementState) {
        var world = worlds.getReferenceById(worldId);
        WorldBlockDiff diff = blocks.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseGet(() -> new WorldBlockDiff(world, x, y, z,
                        replacementType, replacementState));
        diff.replace(replacementType, replacementState, null);
        blocks.save(diff);
        signs.deleteAt(worldId, x, y, z);
    }

    @Transactional public void deleteWorld(Long worldId) { signs.deleteAllByWorldId(worldId); }
}
