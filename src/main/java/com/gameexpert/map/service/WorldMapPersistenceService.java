package com.gameexpert.map.service;

import com.gameexpert.common.NotFoundException;
import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.map.entity.WorldMap;
import com.gameexpert.map.entity.WorldPlayerMapSettlement;
import com.gameexpert.map.repository.WorldMapRepository;
import com.gameexpert.map.repository.WorldPlayerMapSettlementRepository;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 채워진 지도 행과 월드별 내구성 ID 예약을 소유하는 저장 경계입니다. */
@Service
@RequiredArgsConstructor
public class WorldMapPersistenceService {

    /** 기존 receipt 스키마에서 terminal과 구분되는, 외부 ground ID로는 불가능한 값입니다. */
    public static final long PENDING_GROUND_ENTITY_ID = -1L;

    public enum AllocationKind {
        EMPTY_MAP(true),
        CARTOGRAPHY_EXPAND(true),
        CARTOGRAPHY_LOCK(true),
        CARTOGRAPHY_CLONE(false);

        private final boolean allocatesNewMap;

        AllocationKind(boolean allocatesNewMap) {
            this.allocatesNewMap = allocatesNewMap;
        }

        public boolean allocatesNewMap() {
            return allocatesNewMap;
        }
    }

    /** One declaration-ordered map row requested by authenticated canonical loot materialization. */
    public record NewMapSpec(int centerX, int centerZ, int scale, byte[] colors,
            WorldMapData.TargetMarker targetMarker) {
        public NewMapSpec(int centerX, int centerZ, int scale, byte[] colors) {
            this(centerX, centerZ, scale, colors, null);
        }
        public NewMapSpec {
            if (scale < 0 || scale > 4) {
                throw new IllegalArgumentException("map scale must be 0..4");
            }
            if (colors == null || colors.length != WorldMapData.COLOR_COUNT) {
                throw new IllegalArgumentException(
                        "map colors must contain exactly " + WorldMapData.COLOR_COUNT + " bytes");
            }
            colors = Arrays.copyOf(colors, colors.length);
        }

        @Override
        public byte[] colors() {
            return Arrays.copyOf(colors, colors.length);
        }
    }

    /**
     * ID 예약 이전에 이미 고정할 수 있는 전체 권위 주장입니다. actionFingerprint는 각 명령이
     * 실제 손/격자 입력, 지도 generation과 출력 방식을 다시 계산해 대조합니다.
     */
    public record AllocationRequest(
            long settlementId,
            Long worldId,
            Long playerId,
            long expectedInventoryRevision,
            long inventoryLeaseNonce,
            String sourceInventoryDigest,
            AllocationKind kind,
            int retainedMapId,
            String actionFingerprint) {

        public AllocationRequest {
            if (settlementId <= 0 || settlementId == Long.MAX_VALUE
                    || worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE
                    || playerId == null || playerId <= 0 || playerId == Long.MAX_VALUE
                    || expectedInventoryRevision < 0
                    || expectedInventoryRevision >= Long.MAX_VALUE - 1
                    || inventoryLeaseNonce <= 0 || inventoryLeaseNonce == Long.MAX_VALUE
                    || !isSha256(sourceInventoryDigest) || kind == null
                    || !isSha256(actionFingerprint)
                    || (kind.allocatesNewMap() && retainedMapId != 0)
                    || (!kind.allocatesNewMap() && retainedMapId <= 0)) {
                throw new IllegalArgumentException("complete durable map allocation identity is required");
            }
        }

        public String fingerprint() {
            return sha256("world-map-allocation-request-v1|" + settlementId + "|" + worldId
                    + "|" + playerId + "|" + expectedInventoryRevision + "|"
                    + inventoryLeaseNonce + "|" + sourceInventoryDigest + "|" + kind + "|"
                    + retainedMapId + "|" + actionFingerprint);
        }
    }

    /** 영구 pending receipt가 인증하는 재생 가능한 월드별 ID 예약입니다. */
    public record AllocationReservation(
            AllocationRequest request,
            int previousHighWater,
            int mapId,
            String fingerprint) {

        public AllocationReservation(AllocationRequest request, int previousHighWater, int mapId) {
            this(request, previousHighWater, mapId,
                    reservationFingerprint(request, previousHighWater, mapId));
        }

        public AllocationReservation {
            if (request == null || previousHighWater < 0 || mapId <= 0
                    || (request.kind().allocatesNewMap()
                            && (previousHighWater == Integer.MAX_VALUE
                                    || mapId != previousHighWater + 1))
                    || (!request.kind().allocatesNewMap()
                            && (previousHighWater != 0 || mapId != request.retainedMapId()))
                    || !isSha256(fingerprint)
                    || !fingerprint.equals(
                            reservationFingerprint(request, previousHighWater, mapId))) {
                throw new IllegalArgumentException("invalid durable map allocation reservation");
            }
        }

        private static String reservationFingerprint(
                AllocationRequest request, int previousHighWater, int mapId) {
            if (request == null) {
                throw new IllegalArgumentException("map allocation request is required");
            }
            return sha256("world-map-allocation-reservation-v1|" + request.fingerprint()
                    + "|" + previousHighWater + "|" + mapId);
        }
    }

    private final WorldMapRepository mapRepository;
    private final WorldStore worldRepository;
    private final WorldPlayerMapSettlementRepository settlements;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<WorldMapData> loadWorld(Long worldId) {
        return mapRepository.findByWorld_IdOrderByMapIdAsc(worldId).stream()
                .map(WorldMap::toData)
                .toList();
    }

    /**
     * Locks the durable world allocator, appends one pending receipt and returns the same reservation
     * on every identical retry. A rolled-back terminal transaction cannot make this ID reusable.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AllocationReservation reserveMapId(AllocationRequest request) {
        if (request == null) throw new IllegalArgumentException("map allocation request is required");
        lockWorld(request.worldId());
        var locked = settlements.findForUpdate(request.worldId(), request.settlementId());
        if (locked.isPresent()) {
            return replayReservation(request, requiredState(request));
        }

        int previousHighWater;
        int mapId;
        if (request.kind().allocatesNewMap()) {
            previousHighWater = maximumAllocatedMapId(request.worldId());
            if (previousHighWater == Integer.MAX_VALUE) {
                throw new IllegalStateException("world map ID space is exhausted");
            }
            mapId = previousHighWater + 1;
        } else {
            previousHighWater = 0;
            mapId = request.retainedMapId();
            if (mapRepository.findByWorld_IdAndMapId(request.worldId(), mapId).isEmpty()) {
                throw new IllegalStateException("retained cartography source map is missing");
            }
        }
        AllocationReservation reservation = new AllocationReservation(
                request, previousHighWater, mapId);
        settlements.saveAndFlush(new WorldPlayerMapSettlement(
                request.worldId(), request.settlementId(), request.playerId(),
                request.expectedInventoryRevision(), request.expectedInventoryRevision(),
                mapId, PENDING_GROUND_ENTITY_ID, reservation.fingerprint()));
        return reservation;
    }

    /** Reconstructs and authenticates a reservation after restart without advancing the allocator. */
    private AllocationReservation replayReservation(AllocationRequest request,
            WorldPlayerMapSettlementRepository.SettlementState state) {
        int previousHighWater = request.kind().allocatesNewMap() ? state.getMapId() - 1 : 0;
        AllocationReservation reservation = new AllocationReservation(
                request, previousHighWater, state.getMapId());
        long terminalRevision = Math.addExact(request.expectedInventoryRevision(), 1);
        boolean pending = state.getCommittedInventoryRevision()
                == request.expectedInventoryRevision()
                && state.getGroundEntityId() == PENDING_GROUND_ENTITY_ID;
        boolean terminal = state.getCommittedInventoryRevision() == terminalRevision
                && state.getGroundEntityId() >= 0;
        if (!state.getPlayerId().equals(request.playerId())
                || state.getSourceInventoryRevision() != request.expectedInventoryRevision()
                || (pending && !state.getCommandFingerprint().equals(reservation.fingerprint()))
                || (terminal && !isSha256(state.getCommandFingerprint()))
                || (!pending && !terminal)) {
            throw new IllegalStateException("map allocation settlement identity collision");
        }
        return reservation;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockWorldJoiningTransaction(Long worldId) {
        lockWorld(worldId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorldMapData lockMapJoiningTransaction(Long worldId, int mapId) {
        return entityManager.createQuery("""
                        select map from WorldMap map
                        where map.world.id = :worldId and map.mapId = :mapId
                        """, WorldMap.class)
                .setParameter("worldId", worldId)
                .setParameter("mapId", mapId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .map(WorldMap::toData)
                .orElse(null);
    }

    /**
     * Allocates and inserts an authenticated declaration-ordered group in the caller's transaction.
     * The world row serializes this allocator with player reservations; rollback removes both the
     * rows and the apparent high-water advance, so a failed first-open never burns an ID.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<WorldMapData> createNextBatchJoiningTransaction(
            Long worldId, List<NewMapSpec> requested) {
        if (worldId == null || worldId <= 0) {
            throw new IllegalArgumentException("worldId must be positive");
        }
        if (requested == null) {
            throw new IllegalArgumentException("map batch is required");
        }
        List<NewMapSpec> specs = List.copyOf(requested);
        if (specs.isEmpty()) {
            return List.of();
        }

        WorldAccess world = worldRepository.findByIdForShare(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
        int highWater = maximumAllocatedMapId(worldId);
        if ((long) highWater + specs.size() > Integer.MAX_VALUE) {
            throw new IllegalStateException("world map ID space is exhausted");
        }

        List<WorldMapData> created = new ArrayList<>(specs.size());
        for (NewMapSpec spec : specs) {
            int mapId = ++highWater;
            WorldMapData data = new WorldMapData(worldId, mapId,
                    spec.centerX(), spec.centerZ(), spec.scale(), false, spec.colors(), 0, spec.targetMarker());
            mapRepository.save(new WorldMap(world, data));
            created.add(data);
        }
        mapRepository.flush();
        return List.copyOf(created);
    }

    /** 새 지도 행을 즉시 flush해 성공 반환 전에 (world,mapId) 고유성과 내구성을 확정합니다. */
    @Transactional
    public WorldMapData create(WorldMapData data) {
        if (data.getRevision() != 0) {
            throw new IllegalArgumentException("a new map must start at revision zero");
        }
        WorldAccess world = worldRepository.findById(data.getWorldId())
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
        return mapRepository.saveAndFlush(new WorldMap(world, data)).toData();
    }

    /** 이후 권위 래스터 갱신이 쓰는 전체 색 스냅샷 저장 경계입니다. */
    @Transactional
    public WorldMapData replaceColors(Long worldId, int mapId, byte[] colors, long revision) {
        WorldMap map = mapRepository.findByWorld_IdAndMapId(worldId, mapId)
                .orElseThrow(() -> new NotFoundException("MAP_NOT_FOUND"));
        map.replaceColors(colors, revision);
        return map.toData();
    }

    private void lockWorld(Long worldId) {
        worldRepository.findByIdForShare(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
    }

    private WorldPlayerMapSettlementRepository.SettlementState requiredState(
            AllocationRequest request) {
        return settlements.findState(request.worldId(), request.settlementId())
                .orElseThrow(() -> new IllegalStateException("locked map reservation disappeared"));
    }

    private int maximumAllocatedMapId(Long worldId) {
        Integer mapHighWater = entityManager.createQuery("""
                        select max(map.mapId) from WorldMap map where map.world.id = :worldId
                        """, Integer.class)
                .setParameter("worldId", worldId)
                .getSingleResult();
        Integer receiptHighWater = settlements.findMaximumReservedMapId(worldId);
        return Math.max(mapHighWater == null ? 0 : mapHighWater,
                receiptHighWater == null ? 0 : receiptHighWater);
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
