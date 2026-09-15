package com.gameexpert.engine.mob.origin.persistence;

import com.gameexpert.engine.mob.BrimstoneLurkerRules;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrimstoneSitePersistenceService {
    private final WorldBrimstoneSiteRepository sites;

    public static final class SpawnPlan {
        private final long siteKey; private final long mobId;
        private final int x; private final int y; private final int z;
        private final long nextEligibleAuthorityTick;
        private SpawnPlan(WorldBrimstoneSite site) {
            site.validateCurrentSchema();
            this.siteKey = site.getSiteKey(); this.mobId = site.getActiveMobId();
            this.x = site.getSpawnX(); this.y = site.getSpawnY(); this.z = site.getSpawnZ();
            this.nextEligibleAuthorityTick = site.getNextEligibleAuthorityTick();
        }
        public long siteKey() { return siteKey; }
        public long mobId() { return mobId; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public long nextEligibleAuthorityTick() { return nextEligibleAuthorityTick; }
    }

    @Transactional
    public SpawnPlan registerInitial(Long worldId, long siteKey, int cellX, int cellZ,
            int x, int y, int z, long proposedMobId, long nowAuthorityTick) {
        WorldBrimstoneSite site = sites.findLocked(worldId, siteKey)
                .orElseGet(() -> sites.save(new WorldBrimstoneSite(worldId, siteKey,
                        cellX, cellZ, x, y, z, proposedMobId)));
        if (!site.hasOriginFacts(cellX, cellZ, x, y, z)) {
            throw new IllegalStateException("Brimstone site identity collision");
        }
        if (!site.claim(proposedMobId, nowAuthorityTick)) return null;
        sites.save(site);
        return new SpawnPlan(site);
    }

    @Transactional
    public SpawnPlan markKilled(Long worldId, long mobId, long nowAuthorityTick) {
        WorldBrimstoneSite site = sites.findLockedByMob(worldId, mobId).orElse(null);
        if (site == null || !site.killed(mobId, nowAuthorityTick,
                BrimstoneLurkerRules.RESPAWN_EPOCH_TICKS)) return null;
        sites.save(site);
        return new SpawnPlan(site);
    }

    /**
     * Loads the complete durable ledger once when a world runtime starts. Ordinary authority ticks
     * must reconcile this in-memory snapshot and only return to the database for a real
     * register/kill/claim transition; polling two read transactions every 100 ms stalls the world
     * owner even when the table is empty.
     */
    @Transactional(readOnly = true)
    public List<SpawnPlan> all(Long worldId) {
        return sites.findAllByWorldIdOrderBySiteKeyAsc(worldId).stream()
                .map(SpawnPlan::new).toList();
    }

    @Transactional
    public SpawnPlan claimDue(Long worldId, long siteKey, long proposedMobId, long nowAuthorityTick) {
        WorldBrimstoneSite site = sites.findLocked(worldId, siteKey).orElse(null);
        if (site == null || !site.claim(proposedMobId, nowAuthorityTick)) return null;
        sites.save(site);
        return new SpawnPlan(site);
    }

    @Transactional public void deleteWorld(Long worldId) { sites.deleteAllByWorldId(worldId); }
}
