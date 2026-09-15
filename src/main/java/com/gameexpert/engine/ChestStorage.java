package com.gameexpert.engine;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.terrain.Blocks;

/**
 * 한 월드의 상자 내용물 보관소. 좌표 → {@link ChestInventory} 로만 이루어진 단순한 맵입니다.
 *
 * <p><b>스레딩</b>: 월드 틱 스레드가 변이를 소유하고, 저장·welcome 읽기는 동기화된 맵 접근과
 * {@link ChestInventory.PersistenceSnapshot}을 사용합니다. WS 스레드는 {@code ActionQueue} 로만
 * 요청하므로 gameplay 변이는 여전히 월드 틱 스레드에서 수행합니다.
 *
 * <p><b>영속</b>: 내용물은 {@code world_chests}/{@code world_chest_items} 에 저장되어 서버를 껐다 켜도
 * 남습니다. 이 클래스는 런타임 캐시이며, 월드 입장 시 {@link #loadAll} 로 채우고 변경이 생기면
 * {@link #drainDirty} 가 돌려주는 좌표만 저장합니다(블록 diff 와 같은 dirty 기반 배치 저장).
 * 블록 자체(상자가 거기 있다는 사실)는 {@code world_block_diffs} 가 담당합니다.
 */
public final class ChestStorage {

    private static final int HORIZONTAL_BITS = 26;
    private static final long HORIZONTAL_MASK = (1L << HORIZONTAL_BITS) - 1L;
    private static final int HORIZONTAL_SIGN_BIT = 1 << (HORIZONTAL_BITS - 1);

    private final Map<Coordinate, ChestInventory> chests = new HashMap<>();
    /** HopperBlockEntity.TransferCooldown; tickedGameTime is deliberately not persisted. */
    private final Map<Coordinate, Integer> hopperCooldowns = new HashMap<>();
    // 저장이 필요한 상자 좌표. 변경마다 DB 를 때리지 않고, 주기 플러시가 이 목록만 가져간다.
    private final Set<Coordinate> dirty = new java.util.HashSet<>();
    /** 같은 가변 ChestInventory 인스턴스를 두 좌표가 공동 소유하지 못하게 하는 역색인입니다. */
    private final Map<ChestInventory, Coordinate> owners = new IdentityHashMap<>();

    private record Coordinate(int x, int y, int z) { }

    /** 월드 경계 안의 x/z와 유효한 블록 y를 충돌 없이 26+26+9비트로 packing 합니다. */
    static long key(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        return ((long) position.x() & HORIZONTAL_MASK) << 35
                | (((long) position.z() & HORIZONTAL_MASK) << 9)
                | (position.y() - Blocks.MIN_Y);
    }

    private static Coordinate coordinate(int x, int y, int z) {
        if (x < -MovementLimits.MAX_HORIZONTAL_COORDINATE
                || x > MovementLimits.MAX_HORIZONTAL_COORDINATE
                || z < -MovementLimits.MAX_HORIZONTAL_COORDINATE
                || z > MovementLimits.MAX_HORIZONTAL_COORDINATE) {
            throw new IllegalArgumentException("chest coordinate exceeds the world boundary");
        }
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalArgumentException("chest y coordinate is outside the world");
        }
        return new Coordinate(x, y, z);
    }

    /** 해당 좌표의 상자를 얻습니다. 없으면 새로 만듭니다(상자를 처음 열 때). */
    public synchronized ChestInventory openAt(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        ChestInventory existing = chests.get(position);
        if (existing != null) return existing;
        ChestInventory created = new ChestInventory();
        bind(position, created);
        return created;
    }

    public synchronized ChestInventory openAt(int x, int y, int z, int slots) {
        Coordinate position = coordinate(x, y, z);
        ChestInventory existing = chests.get(position);
        if (existing != null) {
            if (existing.slots() != slots) throw new IllegalStateException("container size mismatch");
            return existing;
        }
        ChestInventory created = new ChestInventory(slots);
        bind(position, created);
        return created;
    }

    private void bind(Coordinate position, ChestInventory chest) {
        if (chest == null) throw new IllegalArgumentException("chest is required");
        Coordinate previous = owners.get(chest);
        if (previous != null && !previous.equals(position)) {
            throw new IllegalArgumentException("one chest inventory cannot be aliased across coordinates");
        }
        ChestInventory replaced = chests.get(position);
        if (replaced != null && replaced != chest) owners.remove(replaced);
        chests.put(position, chest);
        owners.put(chest, position);
    }

    /** 내용물을 조회만 합니다. 아직 한 번도 열지 않은 상자면 null. */
    public synchronized ChestInventory peekAt(int x, int y, int z) {
        return chests.get(coordinate(x, y, z));
    }

    /**
     * 상자가 부서질 때 호출합니다. 보관 항목을 비우고 (종류, 개수) 목록을 돌려주므로
    * 호출부가 드랍을 스폰할 수 있습니다. 비어 있거나 없던 상자면 빈 목록.
     *
     * <p>드레인이 terminal revision으로 거부되면 먼저 맵에서 떼지 않습니다. 그래야 저장소와
     * 내용물이 같은 원자적 상태로 남습니다.
     */
    public synchronized List<ChestInventory.StoredStack> removeAt(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        ChestInventory chest = chests.get(position);
        if (chest == null) {
            hopperCooldowns.remove(position);
            dirty.add(position);
            return List.of();
        }
        List<ChestInventory.StoredStack> drained = chest.drainAll();
        hopperCooldowns.remove(position);
        chests.remove(position);
        owners.remove(chest);
        dirty.add(position);
        return drained;
    }

    /**
     * [SHULKER-CONTENTS] 좌표에서 컨테이너를 <b>비우지 않고</b> 떼어 냅니다. 셜커 상자를 캘 때
     * 27칸은 쏟아지는 것이 아니라 아이템이 물고 가므로, {@link #removeAt} 처럼 drain 하면
     * 안 됩니다. 좌표 행 삭제는 똑같이 dirty 로 표시하므로 다시 켰을 때 되살아나지 않습니다.
     */
    public synchronized ChestInventory detachAt(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        ChestInventory chest = chests.remove(position);
        hopperCooldowns.remove(position);
        if (chest != null) owners.remove(chest);
        // 부순 상자도 저장 대상이다 — DB 행을 지워야 다시 켰을 때 되살아나지 않는다.
        dirty.add(position);
        return chest;
    }

    /** 열려 있는 상자 수(디버그·테스트용). */
    public synchronized int size() {
        return chests.size();
    }

    /** Detached coordinate/content/revision snapshots for welcome-time block-entity presentation. */
    public synchronized java.util.List<PositionedInventory> snapshots() {
        java.util.List<PositionedInventory> out = new java.util.ArrayList<>(chests.size());
        for (Map.Entry<Coordinate, ChestInventory> entry : chests.entrySet()) {
            Coordinate position = entry.getKey();
            ChestInventory.PersistenceSnapshot captured = entry.getValue().persistenceSnapshot();
            out.add(new PositionedInventory(position.x(), position.y(), position.z(), captured));
        }
        return List.copyOf(out);
    }

    /** Chunk presentation needs only pot metadata, never a copy of every container's contents. */
    public synchronized java.util.List<PositionedPotComponents> potSnapshots(int chunkX, int chunkZ) {
        var out = new java.util.ArrayList<PositionedPotComponents>();
        for (Map.Entry<Coordinate, ChestInventory> entry : chests.entrySet()) {
            Coordinate pos = entry.getKey();
            if (Math.floorDiv(pos.x(), 16) != chunkX || Math.floorDiv(pos.z(), 16) != chunkZ) continue;
            String components = entry.getValue().potItemComponents();
            if (components != null) out.add(new PositionedPotComponents(pos.x(), pos.y(), pos.z(), components));
        }
        return java.util.List.copyOf(out);
    }

    public record PositionedPotComponents(int x, int y, int z, String components) { }

    public record PositionedInventory(int x, int y, int z, ChestInventory inventory, long revision) {
        public PositionedInventory(int x, int y, int z, ChestInventory inventory) {
            this(x, y, z, inventory.persistenceSnapshot());
        }

        public PositionedInventory(int x, int y, int z,
                ChestInventory.PersistenceSnapshot snapshot) {
            this(x, y, z, snapshot.detachedInventory(), snapshot.revision());
        }
    }

    // ── 영속 ─────────────────────────────────────────────
    // 상자 내용이 바뀌면 좌표만 표시해 두고, 주기 플러시가 그 좌표들만 저장한다.
    // (블록 diff 와 같은 방식 — 변경마다 DB 를 때리면 틱이 느려진다.)

    /** 내용이 바뀐 상자를 표시합니다. 넣기·꺼내기 후 반드시 호출해야 저장됩니다. */
    public synchronized void markDirty(int x, int y, int z) {
        dirty.add(coordinate(x, y, z));
    }

    /** 저장 대상 좌표를 가져가고 비웁니다(원자 스왑). 반환 배열은 {x, y, z}. */
    public synchronized java.util.List<int[]> drainDirty() {
        if (dirty.isEmpty()) return List.of();
        java.util.List<int[]> out = new java.util.ArrayList<>(dirty.size());
        for (Coordinate position : dirty) {
            out.add(new int[] { position.x(), position.y(), position.z() });
        }
        dirty.clear();
        return out;
    }

    /** 비동기 DB 쓰기가 실패했을 때 최신 캐시 상태를 다음 주기에 다시 스냅샷하도록 좌표를 복원합니다. */
    public synchronized void restoreDirty(java.util.List<int[]> positions) {
        if (positions == null) throw new IllegalArgumentException("dirty positions are required");
        for (int[] pos : positions) {
            if (pos == null || pos.length < 3) throw new IllegalArgumentException("invalid dirty position");
            dirty.add(coordinate(pos[0], pos[1], pos[2]));
        }
    }

    public synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** 월드 입장 시 DB 에서 읽은 내용으로 캐시를 채웁니다. dirty 로 표시하지 않습니다. */
    public synchronized void load(int x, int y, int z, ChestInventory chest) {
        // Container settlement installs a new inventory object for the same block entity.
        // Keep its cooldown; actual removal/replacement already clears the coordinate metadata.
        load(x, y, z, chest, chest.slots() == 5
                ? hopperCooldowns.get(coordinate(x, y, z)) : null);
    }

    /** Existing rows have null metadata, the vanilla missing-TransferCooldown default (-1). */
    public synchronized void load(int x, int y, int z, ChestInventory chest, Integer cooldown) {
        if (cooldown != null && chest.slots() != 5) {
            throw new IllegalArgumentException("hopper cooldown requires five slots");
        }
        Coordinate position = coordinate(x, y, z);
        bind(position, chest);
        if (cooldown == null) hopperCooldowns.remove(position);
        else hopperCooldowns.put(position, cooldown);
    }

    public synchronized Integer hopperCooldownAt(int x, int y, int z) {
        return hopperCooldowns.get(coordinate(x, y, z));
    }

    /** Called after the transfer phase, including cooldown-only ticks and receiving neighbours. */
    public synchronized void saveHopperCooldown(int x, int y, int z, int cooldown) {
        Coordinate position = coordinate(x, y, z);
        ChestInventory chest = chests.get(position);
        if (chest == null || chest.slots() != 5) return;
        if (hopperCooldowns.getOrDefault(position, -1) == cooldown) return;
        chest.markBlockEntityMetadataChanged();
        hopperCooldowns.put(position, cooldown);
        dirty.add(position);
    }

    /** Replacement invalidates this incarnation even before the orphan inventory is drained. */
    synchronized void clearHopperCooldown(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        if (!hopperCooldowns.containsKey(position)) return;
        ChestInventory chest = chests.get(position);
        if (chest != null) chest.markBlockEntityMetadataChanged();
        hopperCooldowns.remove(position);
        dirty.add(position);
    }

    static int[] unkey(long packed) {
        int x = (int) ((packed >>> 35) & HORIZONTAL_MASK);
        int z = (int) ((packed >>> 9) & HORIZONTAL_MASK);
        int y = (int) (packed & 0x1FFL) + Blocks.MIN_Y;
        if ((x & HORIZONTAL_SIGN_BIT) != 0) x -= 1 << HORIZONTAL_BITS;
        if ((z & HORIZONTAL_SIGN_BIT) != 0) z -= 1 << HORIZONTAL_BITS;
        coordinate(x, y, z);
        return new int[] { x, y, z };
    }
}
