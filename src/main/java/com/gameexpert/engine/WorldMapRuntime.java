package com.gameexpert.engine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.inventory.CartographyRules;
import com.gameexpert.map.dto.WorldMapData;

/** 월드 틱 소유자가 관리하는 채워진 지도 ID·메모리 스냅샷입니다. */
public final class WorldMapRuntime {

    /**
     * 지도 제작 결과를 계획할 때만 전달하는 불변 권위 토큰입니다. 생성자는 지도 래스터
     * 소유자 안에만 있으므로 호출자가 scale/lock/mapId를 조립해 권위를 위조할 수 없습니다.
     */
    public static final class CartographyAuthority {
        private final CartographyRules.Operation operation;
        private final int sourceMapId;
        private final long sourceRevision;
        private final int currentScale;
        private final boolean locked;
        private final int resultMapId;
        private final boolean settlementAuthorized;

        private CartographyAuthority(CartographyRules.Operation operation,
                int sourceMapId, long sourceRevision, int currentScale,
                boolean locked, int resultMapId, boolean settlementAuthorized) {
            this.operation = operation;
            this.sourceMapId = sourceMapId;
            this.sourceRevision = sourceRevision;
            this.currentScale = currentScale;
            this.locked = locked;
            this.resultMapId = resultMapId;
            this.settlementAuthorized = settlementAuthorized;
        }

        public CartographyRules.Operation operation() { return operation; }
        public int sourceMapId() { return sourceMapId; }
        public long sourceRevision() { return sourceRevision; }
        public int currentScale() { return currentScale; }
        public boolean locked() { return locked; }
        public int resultMapId() { return resultMapId; }
        public boolean settlementAuthorized() { return settlementAuthorized; }
    }

    /**
     * 한 제작 시도의 지도 행 후보와 권위를 함께 고정합니다. source metadata는 오직
     * MapRaster의 durable 스냅샷에서 읽고 EXPAND/LOCK만 새 ID를 예약합니다.
     */
    static final class CartographyReservation {
        private final CartographyRules.Operation operation;
        private final int sourceMapId;
        private final long sourceRevision;
        private final WorldMapData source;
        private final WorldMapData candidate;
        private final CartographyAuthority authority;

        private CartographyReservation(WorldMapData durable, CartographyRules.Operation operation,
                int resultMapId, WorldMapData candidate) {
            this.operation = operation;
            this.sourceMapId = durable.getMapId();
            this.sourceRevision = durable.getRevision();
            this.source = durable;
            this.candidate = candidate;
            this.authority = new CartographyAuthority(operation, sourceMapId, sourceRevision,
                    durable.getScale(), durable.isLocked(), resultMapId, true);
        }

        CartographyRules.Operation operation() { return operation; }
        int sourceMapId() { return sourceMapId; }
        long sourceRevision() { return sourceRevision; }
        WorldMapData source() { return source; }
        WorldMapData candidate() { return candidate; }
        CartographyAuthority authority() { return authority; }
    }

    static final class PendingPatch {
        private final WorldMapData candidate;
        private final int x;
        private final int z;
        private final int width;
        private final int height;
        private final byte[] colors;

        private PendingPatch(WorldMapData candidate, int x, int z,
                int width, int height, byte[] colors) {
            this.candidate = candidate;
            this.x = x;
            this.z = z;
            this.width = width;
            this.height = height;
            this.colors = colors;
        }

        WorldMapData candidate() {
            return candidate;
        }

        int x() {
            return x;
        }

        int z() {
            return z;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        byte[] colors() {
            return colors.clone();
        }
    }

    private static final class MapRaster {
        private WorldMapData durable;
        private final byte[] workingColors;
        private boolean persistencePending;
        private int dirtyMinX = WorldMapData.SIZE;
        private int dirtyMinZ = WorldMapData.SIZE;
        private int dirtyMaxX = -1;
        private int dirtyMaxZ = -1;

        private MapRaster(WorldMapData durable) {
            this.durable = durable;
            this.workingColors = durable.getColors();
        }

        private boolean dirty() {
            return dirtyMaxX >= dirtyMinX && dirtyMaxZ >= dirtyMinZ;
        }

        private void includeDirty(int x, int z) {
            dirtyMinX = Math.min(dirtyMinX, x);
            dirtyMinZ = Math.min(dirtyMinZ, z);
            dirtyMaxX = Math.max(dirtyMaxX, x);
            dirtyMaxZ = Math.max(dirtyMaxZ, z);
        }

        private void includeDirty(int x, int z, int width, int height) {
            includeDirty(x, z);
            includeDirty(x + width - 1, z + height - 1);
        }

        private void clearDirty() {
            dirtyMinX = WorldMapData.SIZE;
            dirtyMinZ = WorldMapData.SIZE;
            dirtyMaxX = -1;
            dirtyMaxZ = -1;
        }
    }

    private final Long worldId;
    private final Map<Integer, MapRaster> maps = new HashMap<>();

    WorldMapRuntime(Long worldId, List<WorldMapData> persisted) {
        if (worldId == null || worldId <= 0) {
            throw new IllegalArgumentException("worldId must be positive");
        }
        this.worldId = worldId;
        for (WorldMapData map : persisted) {
            acceptPersisted(map);
        }
    }

    synchronized WorldMapData prepare(double playerX, double playerZ) {
        throw new IllegalStateException("durable map ID reservation is required");
    }

    /** Builds an empty-map candidate only around an ID already reserved by durable storage. */
    synchronized WorldMapData prepare(double playerX, double playerZ, int durableMapId) {
        if (durableMapId <= 0) {
            throw new IllegalArgumentException("durable map ID must be positive");
        }
        if (maps.containsKey(durableMapId)) {
            throw new IllegalStateException("world map ID collision " + durableMapId);
        }
        return new WorldMapData(worldId, durableMapId, center(playerX), center(playerZ),
                0, false, new byte[WorldMapData.COLOR_COUNT], 0);
    }

    synchronized CartographyReservation prepareCartography(int sourceMapId,
            CartographyRules.Operation operation) {
        // Every result, including CLONE's retained map ID, needs a durable reservation receipt.
        return null;
    }

    /**
     * Builds a cartography reservation around an ID which was already reserved by the durable
     * allocator. Unlike the compatibility overload above, this method never advances a
     * process-local counter. The expected source revision closes the preview-to-click race.
     */
    synchronized CartographyReservation prepareCartography(int sourceMapId, long sourceRevision,
            CartographyRules.Operation operation, int durableResultMapId) {
        MapRaster source = maps.get(sourceMapId);
        if (source == null || source.durable.getRevision() != sourceRevision) return null;
        if (operation != CartographyRules.Operation.CLONE
                && maps.containsKey(durableResultMapId)) return null;
        return reservationForDurableSource(source.durable, operation, durableResultMapId);
    }

    /**
     * Issues a non-settleable preview witness from the current durable map generation. New-map
     * operations deliberately reuse the source ID in the preview only: viewing a result must not
     * consume a durable allocation. Result pickup always replaces this witness with one issued
     * from a durable allocator reservation.
     */
    synchronized CartographyAuthority previewCartography(int sourceMapId,
            CartographyRules.Operation operation) {
        MapRaster source = maps.get(sourceMapId);
        return source == null ? null : previewForDurableSource(source.durable, operation);
    }

    static CartographyAuthority previewForDurableSource(WorldMapData durable,
            CartographyRules.Operation operation) {
        if (durable == null || operation == null) return null;
        if (durable.isLocked() && operation != CartographyRules.Operation.CLONE) return null;
        if (operation == CartographyRules.Operation.EXPAND
                && durable.getScale() >= CartographyRules.MAX_SCALE) return null;
        return new CartographyAuthority(operation, durable.getMapId(), durable.getRevision(),
                durable.getScale(), durable.isLocked(), durable.getMapId(), false);
    }

    /** Exact derivation shared by the runtime state machine and focused tests. */
    static CartographyReservation reservationForDurableSource(WorldMapData source,
            CartographyRules.Operation operation, int durableResultMapId) {
        if (source == null || operation == null || durableResultMapId <= 0) return null;
        if (operation == CartographyRules.Operation.CLONE) {
            return durableResultMapId == source.getMapId()
                    ? new CartographyReservation(source, operation, source.getMapId(), null)
                    : null;
        }
        if (durableResultMapId == source.getMapId() || source.isLocked()
                || operation == CartographyRules.Operation.EXPAND && source.getTargetMarker() != null) return null;
        int scale = operation == CartographyRules.Operation.EXPAND
                ? source.getScale() + 1 : source.getScale();
        if (scale < 0 || scale > CartographyRules.MAX_SCALE) return null;
        WorldMapData candidate = new WorldMapData(source.getWorldId(), durableResultMapId,
                source.getCenterX(), source.getCenterZ(), scale,
                operation == CartographyRules.Operation.LOCK,
                operation == CartographyRules.Operation.LOCK
                        ? source.getColors() : new byte[WorldMapData.COLOR_COUNT],
                0, source.getTargetMarker());
        return new CartographyReservation(source, operation, durableResultMapId, candidate);
    }

    synchronized WorldMapData prepareDerived(int sourceMapId,
            CartographyRules.Operation operation) {
        CartographyReservation reservation = prepareCartography(sourceMapId, operation);
        return reservation == null ? null : reservation.candidate();
    }

    synchronized void acceptPersisted(WorldMapData map) {
        if (!worldId.equals(map.getWorldId())) {
            throw new IllegalArgumentException("persisted map belongs to another world");
        }
        MapRaster previous = maps.get(map.getMapId());
        if (previous != null) {
            if (sameDurableMap(previous.durable, map)) return;
            throw new IllegalStateException("world map ID collision " + map.getMapId());
        }
        maps.put(map.getMapId(), new MapRaster(map));
    }

    synchronized WorldMapData get(int mapId) {
        MapRaster raster = maps.get(mapId);
        return raster == null ? null : raster.durable;
    }

    synchronized boolean isCurrent(CartographyReservation reservation) {
        if (reservation == null) return false;
        MapRaster source = maps.get(reservation.sourceMapId());
        return source != null && sameDurableMap(source.durable, reservation.source());
    }

    static boolean sameDurableMap(WorldMapData left, WorldMapData right) {
        return left != null && right != null
                && left.getWorldId().equals(right.getWorldId())
                && left.getMapId() == right.getMapId()
                && left.getCenterX() == right.getCenterX()
                && left.getCenterZ() == right.getCenterZ()
                && left.getScale() == right.getScale()
                && left.isLocked() == right.isLocked()
                && left.getRevision() == right.getRevision()
                && Arrays.equals(left.getColors(), right.getColors());
    }

    /** 다음 지형 샘플은 커밋 대기 중 변경까지 포함한 작업 버퍼에서 시작합니다. */
    synchronized byte[] workingColors(int mapId) {
        MapRaster raster = maps.get(mapId);
        return raster == null ? null : raster.workingColors.clone();
    }

    /** 행 우선 직사각형 샘플을 작업 버퍼에 병합하고 실제로 달라진 픽셀만 dirty로 표시합니다. */
    synchronized boolean applySample(int mapId, int x, int z,
            int width, int height, byte[] colors) {
        validateRectangle(x, z, width, height, colors);
        MapRaster raster = maps.get(mapId);
        if (raster == null || raster.durable.isLocked()) return false;
        boolean changed = false;
        for (int row = 0; row < height; row++) {
            int source = row * width;
            int destination = (z + row) * WorldMapData.SIZE + x;
            for (int column = 0; column < width; column++) {
                int target = destination + column;
                byte color = colors[source + column];
                if (raster.workingColors[target] == color) continue;
                raster.workingColors[target] = color;
                raster.includeDirty(x + column, z + row);
                changed = true;
            }
        }
        return changed;
    }

    /** 현재 dirty 사각형을 revision 하나의 불변 저장 후보로 고정합니다. */
    synchronized PendingPatch prepareDirtyPatch(int mapId) {
        MapRaster raster = maps.get(mapId);
        if (raster == null || raster.persistencePending || !raster.dirty()) return null;
        int x = raster.dirtyMinX;
        int z = raster.dirtyMinZ;
        int width = raster.dirtyMaxX - x + 1;
        int height = raster.dirtyMaxZ - z + 1;
        byte[] patchColors = rectangle(raster.workingColors, x, z, width, height);
        WorldMapData durable = raster.durable;
        WorldMapData candidate = new WorldMapData(worldId, mapId,
                durable.getCenterX(), durable.getCenterZ(), durable.getScale(), durable.isLocked(),
                raster.workingColors,
                Math.addExact(durable.getRevision(), 1), durable.getTargetMarker());
        raster.clearDirty();
        raster.persistencePending = true;
        return new PendingPatch(candidate, x, z, width, height, patchColors);
    }

    synchronized boolean needsPersistence(int mapId) {
        MapRaster raster = maps.get(mapId);
        return raster != null && (raster.persistencePending || raster.dirty());
    }

    /** 폐기 장벽이 flush 주기 전 dirty 지도와 이미 제출된 지도를 모두 추적할 때 사용합니다. */
    synchronized List<Integer> mapIdsNeedingPersistence() {
        return maps.entrySet().stream()
                .filter(entry -> entry.getValue().persistencePending || entry.getValue().dirty())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    synchronized boolean hasPendingPersistence() {
        return maps.values().stream()
                .anyMatch(raster -> raster.persistencePending || raster.dirty());
    }

    /** DB 커밋된 후보만 클라이언트가 읽는 durable 상태로 승격합니다. */
    synchronized void acceptPatch(PendingPatch patch, WorldMapData persisted) {
        MapRaster raster = requirePending(patch);
        if (!worldId.equals(persisted.getWorldId())
                || persisted.getMapId() != patch.candidate.getMapId()
                || persisted.getRevision() != patch.candidate.getRevision()) {
            throw new IllegalArgumentException("persisted map patch does not match its candidate");
        }
        raster.durable = persisted;
        raster.persistencePending = false;
    }

    /** 거부·실패한 저장 후보 영역을 다시 dirty로 합쳐 다음 틱에서 손실 없이 재시도합니다. */
    synchronized void rejectPatch(PendingPatch patch) {
        MapRaster raster = requirePending(patch);
        raster.persistencePending = false;
        raster.includeDirty(patch.x, patch.z, patch.width, patch.height);
    }

    private MapRaster requirePending(PendingPatch patch) {
        MapRaster raster = maps.get(patch.candidate.getMapId());
        if (raster == null || !raster.persistencePending
                || raster.durable.getRevision() + 1 != patch.candidate.getRevision()) {
            throw new IllegalStateException("map patch is not the active persistence candidate");
        }
        return raster;
    }

    private static void validateRectangle(int x, int z, int width, int height, byte[] colors) {
        if (x < 0 || z < 0 || width <= 0 || height <= 0
                || x + width > WorldMapData.SIZE || z + height > WorldMapData.SIZE
                || colors == null || colors.length != width * height) {
            throw new IllegalArgumentException("map patch rectangle is invalid");
        }
    }

    private static byte[] rectangle(byte[] source, int x, int z, int width, int height) {
        byte[] result = new byte[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(source, (z + row) * WorldMapData.SIZE + x,
                    result, row * width, width);
        }
        return result;
    }

    /** Minecraft 1.21.4 scale-0 중심: floor((coord+64)/128)*128. */
    static int center(double coordinate) {
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException("map coordinate must be finite");
        }
        long block = (long) Math.floor(coordinate);
        long centered = Math.floorDiv(block + 64L, (long) WorldMapData.SIZE)
                * WorldMapData.SIZE;
        if (centered < Integer.MIN_VALUE || centered > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("map center is outside the integer coordinate range");
        }
        return (int) centered;
    }
}
