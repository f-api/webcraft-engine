package com.gameexpert.mob.service;

import com.gameexpert.mob.dto.VillagerBedClaimSnapshot;
import com.gameexpert.mob.dto.VillagerSocietySnapshot;
import com.gameexpert.mob.entity.WorldVillagerBedClaim;
import com.gameexpert.mob.entity.WorldVillagerSocietyState;
import com.gameexpert.mob.repository.WorldVillagerBedClaimRepository;
import com.gameexpert.mob.repository.WorldVillagerSocietyStateRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주민 사회 상태(번식 재고·구애·수면·골렘 기억)와 HOME 침대 claim 레인의 영속 경계다.
 * 구조물 occupant claim 이 "정확히 한 번 append" 인 것과 달리 두 표는 갱신·해제가 있으므로
 * flush 마다 현재 권위 상태로 upsert 하고 사라진 행을 지운다.
 */
@Service
public class VillagerSocietyPersistenceService {

    private final WorldVillagerSocietyStateRepository stateRepository;
    private final WorldVillagerBedClaimRepository bedClaimRepository;

    public VillagerSocietyPersistenceService(
            WorldVillagerSocietyStateRepository stateRepository,
            WorldVillagerBedClaimRepository bedClaimRepository) {
        this.stateRepository = stateRepository;
        this.bedClaimRepository = bedClaimRepository;
    }

    /** reconnect·서버 재기동 복구용 로드. villagerId 오름차순으로 안정 정렬한다. */
    @Transactional(readOnly = true)
    public List<VillagerSocietySnapshot> loadStates(Long worldId) {
        List<VillagerSocietySnapshot> out = new ArrayList<>();
        for (WorldVillagerSocietyState state : stateRepository.findAllByWorldId(worldId)) {
            out.add(state.toSnapshot());
        }
        out.sort(Comparator.comparingLong(VillagerSocietySnapshot::villagerId));
        return out;
    }

    /** claim identity 오름차순으로 안정 정렬한다. */
    @Transactional(readOnly = true)
    public List<VillagerBedClaimSnapshot> loadBedClaims(Long worldId) {
        List<VillagerBedClaimSnapshot> out = new ArrayList<>();
        for (WorldVillagerBedClaim claim : bedClaimRepository.findAllByWorldId(worldId)) {
            out.add(claim.toSnapshot());
        }
        out.sort(Comparator.comparing(VillagerBedClaimSnapshot::identityKey));
        return out;
    }

    /** 상태와 침대 claim 을 한 트랜잭션으로 저장한다. */
    @Transactional
    public void flush(Long worldId, Collection<VillagerSocietySnapshot> states,
            Collection<VillagerBedClaimSnapshot> bedClaims) {
        persistStates(worldId, states);
        persistBedClaims(worldId, bedClaims);
    }

    @Transactional
    public void deleteAllByWorldId(Long worldId) {
        stateRepository.deleteAllByWorldId(worldId);
        bedClaimRepository.deleteAllByWorldId(worldId);
    }

    private void persistStates(Long worldId, Collection<VillagerSocietySnapshot> states) {
        if (states == null) return;
        List<WorldVillagerSocietyState> storedRows = stateRepository.findAllByWorldId(worldId);
        Map<Long, WorldVillagerSocietyState> storedByVillager = new HashMap<>();
        for (WorldVillagerSocietyState stored : storedRows) {
            storedByVillager.put(stored.getVillagerId(), stored);
        }
        Set<Long> live = new HashSet<>();
        List<WorldVillagerSocietyState> changed = new ArrayList<>();
        for (VillagerSocietySnapshot snapshot : states) {
            live.add(snapshot.villagerId());
            WorldVillagerSocietyState existing = storedByVillager.get(snapshot.villagerId());
            if (existing != null) {
                if (societyStateChanged(existing, snapshot)) {
                    existing.apply(snapshot);
                    changed.add(existing);
                }
            } else {
                changed.add(new WorldVillagerSocietyState(worldId, snapshot));
            }
        }
        if (!changed.isEmpty()) stateRepository.saveAll(changed);
        for (WorldVillagerSocietyState stored : storedRows) {
            if (!live.contains(stored.getVillagerId())) stateRepository.delete(stored);
        }
    }

    private void persistBedClaims(Long worldId, Collection<VillagerBedClaimSnapshot> bedClaims) {
        if (bedClaims == null) return;
        List<WorldVillagerBedClaim> storedRows = bedClaimRepository.findAllByWorldId(worldId);
        Map<String, WorldVillagerBedClaim> storedByIdentity = new HashMap<>();
        for (WorldVillagerBedClaim stored : storedRows) {
            storedByIdentity.put(stored.identityKey(), stored);
        }
        Set<String> live = new HashSet<>();
        List<WorldVillagerBedClaim> changed = new ArrayList<>();
        for (VillagerBedClaimSnapshot snapshot : bedClaims) {
            live.add(snapshot.identityKey());
            WorldVillagerBedClaim existing = storedByIdentity.get(snapshot.identityKey());
            if (existing != null) {
                if (bedClaimChanged(existing, snapshot)) {
                    existing.reassign(snapshot);
                    changed.add(existing);
                }
            } else {
                changed.add(new WorldVillagerBedClaim(worldId, snapshot));
            }
        }
        if (!changed.isEmpty()) bedClaimRepository.saveAll(changed);
        for (WorldVillagerBedClaim stored : storedRows) {
            if (!live.contains(stored.identityKey())) bedClaimRepository.delete(stored);
        }
    }

    private static boolean societyStateChanged(WorldVillagerSocietyState stored,
            VillagerSocietySnapshot snapshot) {
        return !java.util.Objects.equals(stored.getFoodInventory(), snapshot.foodInventory())
                || stored.getFoodPoints() != snapshot.foodPoints()
                || stored.getLastSleptTick() != snapshot.lastSleptTick()
                || stored.getGolemMemoryTick() != snapshot.golemMemoryTick()
                || stored.getMateId() != snapshot.mateId()
                || stored.getBirthTick() != snapshot.birthTick()
                || stored.getCourtshipEndTick() != snapshot.courtshipEndTick();
    }

    private static boolean bedClaimChanged(WorldVillagerBedClaim stored,
            VillagerBedClaimSnapshot snapshot) {
        return stored.getOwnerVillagerId() != snapshot.ownerVillagerId()
                || stored.getPolicyVersion() != snapshot.policyVersion();
    }
}
