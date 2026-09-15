package com.gameexpert.engine.mob.villager;

import com.gameexpert.mob.dto.VillagerBedClaimSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HOME 침대 claim 레인. 구조물 occupant claim({@code WorldStructureOccupantClaim})과 같은
 * "정확히 한 번" 패턴을 쓰되, 침대는 주민이 죽거나 침대가 부서지면 다시 비므로 해제 가능한
 * 레인이다. 레인 이름은 {@link VillagerSocietyRules#BED_CLAIM_LANE}이라 직업 POI claim 레인과
 * 절대 같은 키를 만들지 않는다.
 *
 * <p>바닐라 근거는 {@code PoiTypes.HOME}의 maxTickets 1이다: 침대 한 칸은 주민 한 명만 소유한다.
 * 플레이어의 침대 리스폰/잠자기 계약은 이 레인을 읽지도 쓰지도 않으므로 그대로 유지된다.
 */
public final class VillagerBedClaims {
    private final Map<String, Long> ownerByBed = new HashMap<>();
    private final Map<String, int[]> positionByBed = new HashMap<>();
    private final Map<Long, String> bedByOwner = new HashMap<>();

    public VillagerBedClaims() {}

    public VillagerBedClaims(Collection<VillagerBedClaimSnapshot> restored) {
        restore(restored);
    }

    /** reconnect/월드 재적재 복구. 같은 주민이 두 침대를 가진 저장본은 마지막 것만 남긴다. */
    public void restore(Collection<VillagerBedClaimSnapshot> restored) {
        if (restored == null) return;
        for (VillagerBedClaimSnapshot claim : restored) {
            claim(claim.bedX(), claim.bedY(), claim.bedZ(), claim.ownerVillagerId());
        }
    }

    public boolean claimed(int x, int y, int z) {
        return ownerByBed.containsKey(VillagerSocietyRules.bedClaimKey(x, y, z));
    }

    /** 소유자 주민 id, 없으면 0. */
    public long owner(int x, int y, int z) {
        Long owner = ownerByBed.get(VillagerSocietyRules.bedClaimKey(x, y, z));
        return owner == null ? 0L : owner;
    }

    public boolean hasBed(long villagerId) {
        return bedByOwner.containsKey(villagerId);
    }

    /** 주민이 가진 침대 좌표 {@code {x,y,z}} 또는 {@code null}. */
    public int[] bedOf(long villagerId) {
        String key = bedByOwner.get(villagerId);
        if (key == null) return null;
        int[] position = positionByBed.get(key);
        return position == null ? null : new int[] { position[0], position[1], position[2] };
    }

    /**
     * 침대를 주민에게 넘긴다. 이미 다른 주민이 가진 침대면 실패한다. 주민이 다른 침대를 갖고
     * 있었으면 그 침대는 즉시 해제된다(주민 한 명 = HOME 한 칸).
     */
    public boolean claim(int x, int y, int z, long villagerId) {
        if (villagerId <= 0) return false;
        String key = VillagerSocietyRules.bedClaimKey(x, y, z);
        Long current = ownerByBed.get(key);
        if (current != null) return current == villagerId;
        releaseVillager(villagerId);
        ownerByBed.put(key, villagerId);
        positionByBed.put(key, new int[] { x, y, z });
        bedByOwner.put(villagerId, key);
        return true;
    }

    /** 주민이 죽거나 사라질 때의 해제. 해제된 침대 좌표 또는 {@code null}. */
    public int[] releaseVillager(long villagerId) {
        String key = bedByOwner.remove(villagerId);
        if (key == null) return null;
        ownerByBed.remove(key);
        int[] position = positionByBed.remove(key);
        return position;
    }

    /** 침대 블록이 사라졌을 때의 해제. 해제된 소유자 id 또는 0. */
    public long releaseBed(int x, int y, int z) {
        String key = VillagerSocietyRules.bedClaimKey(x, y, z);
        Long owner = ownerByBed.remove(key);
        positionByBed.remove(key);
        if (owner == null) return 0L;
        bedByOwner.remove(owner);
        return owner;
    }

    public int size() {
        return ownerByBed.size();
    }

    /** 영속 whitelist 로 나가는 정렬된 스냅샷. 정렬 키는 claim identity 다. */
    public List<VillagerBedClaimSnapshot> snapshot() {
        List<VillagerBedClaimSnapshot> out = new ArrayList<>(ownerByBed.size());
        for (Map.Entry<String, Long> entry : ownerByBed.entrySet()) {
            int[] position = positionByBed.get(entry.getKey());
            if (position == null) continue;
            out.add(new VillagerBedClaimSnapshot(position[0], position[1], position[2],
                    entry.getValue(), VillagerSocietyRules.BED_CLAIM_POLICY_VERSION));
        }
        out.sort(Comparator.comparing(VillagerBedClaimSnapshot::identityKey));
        return out;
    }
}
