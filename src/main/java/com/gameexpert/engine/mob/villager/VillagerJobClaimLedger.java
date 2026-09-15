package com.gameexpert.engine.mob.villager;

import com.gameexpert.mob.dto.VillagerJobClaimSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Durable "exactly once" occupancy of villager job sites, in the same shape as the structure
 * occupant lane: the authority owns the ledger, every accepted decision becomes one durable row,
 * and a restored row is never decided twice.
 *
 * <p>Two invariants hold at all times, which is why both directions are indexed:
 * <ul>
 *   <li>one villager per station cell — a second villager cannot claim an occupied cell;</li>
 *   <li>one station cell per villager — an employed villager cannot claim a second cell.</li>
 * </ul>
 *
 * <p>Unlike the structure lane a job claim can be given up (owner death, station block removed,
 * owner permanently out of range), so the lane tracks removals as well as upserts. A profession
 * locked by a completed trade survives the release exactly as it does in vanilla: the row stays,
 * only its cell is freed.
 *
 * <p>This is the read/write surface other villager tracks build on; see the query section and
 * {@link Listener}.
 */
public final class VillagerJobClaimLedger {

    /** Fired on the authority thread, after the ledger state has already changed. */
    public interface Listener {
        default void onProfessionAssigned(VillagerJobClaimSnapshot claim) {
        }

        default void onProfessionLocked(VillagerJobClaimSnapshot claim) {
        }

        default void onJobSiteReleased(VillagerJobClaimSnapshot claim,
                VillagerJobSitePolicy.ReleaseReason reason) {
        }
    }

    private final Map<Long, VillagerJobClaimSnapshot> byMob = new ConcurrentHashMap<>();
    private final Map<String, Long> occupantByCell = new ConcurrentHashMap<>();
    private final Map<Long, VillagerJobClaimSnapshot> unpersistedUpserts =
            new ConcurrentHashMap<>();
    private final Map<Long, Long> unpersistedRemovals = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    /** World activation: adopt stored rows without re-deciding or re-persisting them. */
    public void restore(Collection<VillagerJobClaimSnapshot> rows) {
        for (VillagerJobClaimSnapshot row : rows) {
            if (row == null || byMob.containsKey(row.mobId())) continue;
            if (row.hasSite() && occupantByCell.containsKey(row.cellKey())) {
                // A duplicated cell in storage would break one-villager-per-cell. The first row
                // in iteration order keeps the cell; the loser is dropped, and the drop is made
                // durable so storage converges instead of losing the conflict again next time.
                unpersistedRemovals.put(row.mobId(), row.mobId());
                continue;
            }
            byMob.put(row.mobId(), row);
            if (row.hasSite()) occupantByCell.put(row.cellKey(), row.mobId());
        }
    }

    /**
     * Claim one station cell for an unemployed villager. Returns the granted profession, or
     * {@code null} when the villager already holds a row, the cell is already occupied, or the
     * block is not a registered station.
     */
    public VillagerJobSitePolicy.Profession claimJobSite(long mobId, int blockId,
            int x, int y, int z) {
        int stationCode = VillagerJobSitePolicy.stationCode(blockId);
        if (mobId <= 0 || stationCode == 0) return null;
        String cell = VillagerJobSitePolicy.identityKey(stationCode, x, y, z);
        VillagerJobClaimSnapshot claim =
                VillagerJobClaimSnapshot.siteClaim(mobId, stationCode, x, y, z, false);
        // Reserve the cell first: two villagers racing the same cell in one tick must not both
        // observe it free. The reservation is undone when the villager turns out to be employed,
        // so a loser never leaves a half claim behind.
        if (occupantByCell.putIfAbsent(cell, mobId) != null) return null;
        if (byMob.putIfAbsent(mobId, claim) != null) {
            occupantByCell.remove(cell, mobId);
            return null;
        }
        markDirty(claim);
        for (Listener listener : listeners) listener.onProfessionAssigned(claim);
        return claim.profession();
    }

    /**
     * Record that this villager will never take a job. Idempotent; returns {@code false} when the
     * villager already holds any row.
     */
    public boolean markNitwit(long mobId) {
        if (mobId <= 0) return false;
        VillagerJobClaimSnapshot claim = VillagerJobClaimSnapshot.nitwit(mobId);
        if (byMob.putIfAbsent(mobId, claim) != null) return false;
        markDirty(claim);
        for (Listener listener : listeners) listener.onProfessionAssigned(claim);
        return true;
    }

    /** Vanilla: the first completed trade locks the profession permanently. Idempotent. */
    public boolean lockByTrade(long mobId) {
        VillagerJobClaimSnapshot claim = byMob.get(mobId);
        if (claim == null || claim.profession() == VillagerJobSitePolicy.Profession.NITWIT
                || claim.lockedByTrade()) {
            return false;
        }
        VillagerJobClaimSnapshot locked = claim.withLockedByTrade(true);
        if (!byMob.replace(mobId, claim, locked)) return false;
        markDirty(locked);
        for (Listener listener : listeners) listener.onProfessionLocked(locked);
        return true;
    }

    /** Release by owner: the villager walked out of reach for good, or died. */
    public VillagerJobClaimSnapshot releaseByMob(long mobId,
            VillagerJobSitePolicy.ReleaseReason reason) {
        VillagerJobClaimSnapshot claim = byMob.get(mobId);
        if (claim == null || !claim.hasSite()) return null;
        return release(claim, reason);
    }

    /** Release by cell: the station block at that position stopped being that station. */
    public VillagerJobClaimSnapshot releaseAt(int stationCode, int x, int y, int z) {
        Long occupant = occupantByCell.get(VillagerJobSitePolicy.identityKey(stationCode, x, y, z));
        if (occupant == null) return null;
        VillagerJobClaimSnapshot claim = byMob.get(occupant);
        if (claim == null) return null;
        return release(claim, VillagerJobSitePolicy.ReleaseReason.BLOCK_REMOVED);
    }

    /**
     * Release whatever claim sits at that position, whichever station it was. The block authority
     * calls this after a break or replace without knowing which station stood there.
     */
    public VillagerJobClaimSnapshot releaseAtPosition(int x, int y, int z) {
        for (int stationCode : VillagerJobSitePolicy.stationCodes()) {
            VillagerJobClaimSnapshot released = releaseAt(stationCode, x, y, z);
            if (released != null) return released;
        }
        return null;
    }

    /**
     * The villager is gone for good. Its cell is freed and every row about it is deleted — a dead
     * mob's locked profession has nothing left to belong to.
     */
    public VillagerJobClaimSnapshot forgetMob(long mobId) {
        VillagerJobClaimSnapshot claim = byMob.remove(mobId);
        if (claim == null) return null;
        if (claim.hasSite()) occupantByCell.remove(claim.cellKey(), mobId);
        unpersistedUpserts.remove(mobId);
        unpersistedRemovals.put(mobId, mobId);
        if (claim.hasSite()) {
            for (Listener listener : listeners) {
                listener.onJobSiteReleased(claim, VillagerJobSitePolicy.ReleaseReason.DEATH);
            }
        }
        return claim;
    }

    /**
     * Evaluate one live claim against the world and release it when vanilla would. Returns the
     * reason applied, or {@code null} when the claim stands.
     */
    public VillagerJobSitePolicy.ReleaseReason revalidate(long mobId, boolean ownerAlive,
            int currentBlockId, double distanceSquared) {
        VillagerJobClaimSnapshot claim = byMob.get(mobId);
        if (claim == null || !claim.hasSite()) return null;
        VillagerJobSitePolicy.ReleaseReason reason = VillagerJobSitePolicy.releaseReason(
                ownerAlive, currentBlockId, claim.stationCode(), distanceSquared);
        if (reason == null) return null;
        if (reason == VillagerJobSitePolicy.ReleaseReason.DEATH) {
            return forgetMob(mobId) == null ? null : reason;
        }
        return release(claim, reason) == null ? null : reason;
    }

    private VillagerJobClaimSnapshot release(VillagerJobClaimSnapshot claim,
            VillagerJobSitePolicy.ReleaseReason reason) {
        long mobId = claim.mobId();
        if (claim.lockedByTrade()) {
            // Vanilla keeps a traded profession forever, so only the cell is given up. The row is
            // rewritten without a site instead of deleted, which keeps the lock durable.
            VillagerJobClaimSnapshot kept = claim.withoutSite();
            if (!byMob.replace(mobId, claim, kept)) return null;
            occupantByCell.remove(claim.cellKey(), mobId);
            markDirty(kept);
        } else {
            if (!byMob.remove(mobId, claim)) return null;
            occupantByCell.remove(claim.cellKey(), mobId);
            unpersistedUpserts.remove(mobId);
            unpersistedRemovals.put(mobId, mobId);
        }
        for (Listener listener : listeners) listener.onJobSiteReleased(claim, reason);
        return claim;
    }

    private void markDirty(VillagerJobClaimSnapshot claim) {
        unpersistedUpserts.put(claim.mobId(), claim);
        unpersistedRemovals.remove(claim.mobId());
    }

    // ---- queries: the read surface other villager tracks use -------------------------------

    /** The villager's profession; {@code NONE} when it holds no row. */
    public VillagerJobSitePolicy.Profession professionOf(long mobId) {
        VillagerJobClaimSnapshot claim = byMob.get(mobId);
        return claim == null ? VillagerJobSitePolicy.Profession.NONE : claim.profession();
    }

    public VillagerBrainRules.ProfessionState professionStateOf(long mobId) {
        return VillagerJobSitePolicy.professionState(professionOf(mobId));
    }

    /** {@code {stationCode, x, y, z}} of the villager's job site, or {@code null}. */
    public int[] jobSiteOf(long mobId) {
        VillagerJobClaimSnapshot claim = byMob.get(mobId);
        if (claim == null || !claim.hasSite()) return null;
        return new int[] { claim.stationCode(), claim.x(), claim.y(), claim.z() };
    }

    public VillagerJobClaimSnapshot claimOf(long mobId) {
        return byMob.get(mobId);
    }

    /** Mob id occupying that cell, or {@code 0} when it is free. */
    public long occupantOf(int stationCode, int x, int y, int z) {
        Long occupant = occupantByCell.get(
                VillagerJobSitePolicy.identityKey(stationCode, x, y, z));
        return occupant == null ? 0L : occupant;
    }

    public boolean isClaimed(int stationCode, int x, int y, int z) {
        return occupantByCell.containsKey(
                VillagerJobSitePolicy.identityKey(stationCode, x, y, z));
    }

    /** True when the block at that position is a station nobody has claimed. */
    public boolean isFreeStation(int blockId, int x, int y, int z) {
        int stationCode = VillagerJobSitePolicy.stationCode(blockId);
        return stationCode != 0 && !isClaimed(stationCode, x, y, z);
    }

    public boolean isLockedByTrade(long mobId) {
        VillagerJobClaimSnapshot claim = byMob.get(mobId);
        return claim != null && claim.lockedByTrade();
    }

    public int size() {
        return byMob.size();
    }

    public List<VillagerJobClaimSnapshot> sortedRows() {
        List<VillagerJobClaimSnapshot> rows = new ArrayList<>(byMob.values());
        rows.sort((left, right) -> Long.compare(left.mobId(), right.mobId()));
        return rows;
    }

    // ---- persistence lane ------------------------------------------------------------------

    public boolean hasUnpersistedRows() {
        return !unpersistedUpserts.isEmpty() || !unpersistedRemovals.isEmpty();
    }

    public Map<Long, VillagerJobClaimSnapshot> unpersistedUpserts() {
        return Map.copyOf(unpersistedUpserts);
    }

    /** Mob ids whose rows the store must delete. */
    public Map<Long, Long> unpersistedRemovals() {
        return Map.copyOf(unpersistedRemovals);
    }

    /** Drop exactly the rows the store confirmed, so a concurrent change is not lost. */
    public void acknowledgePersisted(Map<Long, VillagerJobClaimSnapshot> upserts,
            Map<Long, Long> removals) {
        upserts.forEach(unpersistedUpserts::remove);
        removals.forEach(unpersistedRemovals::remove);
    }
}
