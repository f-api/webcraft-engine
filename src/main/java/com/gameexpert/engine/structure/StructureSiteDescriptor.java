package com.gameexpert.engine.structure;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stateless site descriptor implementing the shared E/S/T/A/G/D bit contract. */
public final class StructureSiteDescriptor {
    /** 바닐라 청크 한 변. random_spread 의 spacing/separation 은 청크 단위다. */
    public static final int CHUNK_SIZE = 16;

    public enum PlacementStage {
        /** Reserved append-only identity with no runtime planning or generation path. */
        REMOVED,
        /** Emitted only after a terrain or structure host has been verified. */
        HOST_BOUND,
        /** Small independent overlays required in the first authoritative snapshot. */
        ACTIVATION,
        /** Larger independent overlays planned by the deferred structure workers. */
        DEFERRED
    }

    private enum AdmissionScale {
        NONE(0, 1),
        GENERAL(2, 5),
        UNDERWATER(1, 5);

        private final int numerator;
        private final int denominator;

        AdmissionScale(int numerator, int denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
        }

        private int apply(int baseline) {
            return baseline * numerator / denominator;
        }
    }

    public enum Kind {
        CAMPING_SITE(false, true, 29, 7, 0x42a1d16b, 0, 1024,
                PlacementStage.ACTIVATION, AdmissionScale.GENERAL),
        /** Removed: the canonical 26.3 abandoned-camp feature owns this append-only identity. */
        CAMP(false, false, PlacementStage.REMOVED),
        MINER_CAMP(true, true, 31, 8, 0x13579bdf, 2, 0,
                PlacementStage.HOST_BOUND, AdmissionScale.NONE),
        SMALL_RUIN(true, true, 43, 10, 0x3d1ac4b7, 3, 512,
                PlacementStage.ACTIVATION, AdmissionScale.GENERAL),
        MEDIUM_RUIN(true, true, 61, 28, 0x74e2b913, 4, 512,
                PlacementStage.ACTIVATION, AdmissionScale.GENERAL),
        FLOODED_RUIN(true, true, 67, 18, 0x21c78d5f, 5, 512,
                PlacementStage.ACTIVATION, AdmissionScale.UNDERWATER),
        TOMB_RUIN(true, true, 71, 11, 0x59af206d, 6, 512,
                PlacementStage.ACTIVATION, AdmissionScale.GENERAL),
        GENERAL_RUIN(true, true, 59, 15, 0x0e6b93c1, 7, 512,
                PlacementStage.ACTIVATION, AdmissionScale.GENERAL),

        // Terrain-hosted entries have no independent descriptor admission.
        DUNGEON(true, true, PlacementStage.HOST_BOUND),
        /** Removed: canonical 26.3 mineshafts are emitted by the terrain product. */
        MINESHAFT(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 villages are emitted by the terrain product. */
        VILLAGE(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 pillager outposts are emitted by the terrain product. */
        PILLAGER_OUTPOST(false, false, PlacementStage.REMOVED),
        DESERT_TOMB(true, true, 83, 20, 0x68d4a2f1, 13, 768,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        METEOR_CRATER(true, true, 53, 8, 0x2f6a91c3, 9, 512,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        RUINED_TOWER(true, true, 73, 6, 0x7d31e4a9, 11, 768,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        ALTAR(true, true, 47, 4, 0x4a7c92d1, 8, 1024,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        /** Removed: this project ocean ruin duplicated the canonical 26.3 ocean-ruin family. */
        OCEAN_RUIN(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 shipwrecks are emitted by the terrain product. */
        SHIPWRECK(false, false, PlacementStage.REMOVED),
        UNDERWATER_RUIN(true, true, 71, 147, 0x45c2f18b, 16, 640,
                PlacementStage.DEFERRED, AdmissionScale.UNDERWATER),
        UNDERGROUND_RUIN(true, true, 67, 18, 0x27d8a65f, 19, 640,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        UNDERGROUND_DUNGEON(true, true, 83, 111, 0x5ca931e7, 20, 512,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        BURIED_RUIN(true, true, 61, 13, 0x39a5d7c1, 18, 768,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        SEALED_CHAMBER(true, true, 97, 7, 0x73e4b219, 21, 384,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        UNDERGROUND_CITY(true, true, 113, 101, 0x16c9e47b, 22, 256,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        UNDERGROUND_PRISON(true, true, 101, 35, 0x62ad38f1, 23, 320,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        GLITCH_DUNGEON(true, true, 127, 35, 0x0bd75ca3, 24, 192,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        MONSTER_SPAWN_ZONE(true, true, 67, 4, 0x70c4b83d, 17, 5,
                PlacementStage.DEFERRED, AdmissionScale.GENERAL),
        /** Removed: canonical 26.3 ocean monuments are emitted by the terrain product. */
        OCEAN_MONUMENT(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 woodland mansions are emitted by the terrain product. */
        WOODLAND_MANSION(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 trial chambers are emitted by the terrain product. */
        TRIAL_CHAMBER(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 ancient cities are emitted by the terrain product. */
        DEEP_DARK_CITY(false, false, PlacementStage.REMOVED),
        /** Removed project overlay; Sulfur Caves is raw biome 188 and code 31 stays reserved. */
        SULFUR_CAVERN(false, false, PlacementStage.REMOVED),
        /**
         * Removed project-specific birch-grove overlay. Code 32 remains reserved by the append-only
         * accepted-site identity contract, but it has no descriptor, admission, generator or work.
         * Ordinary birch woodland comes only from the pinned vanilla biome feature pass.
         */
        BIRCH_GROVE(false, false, PlacementStage.REMOVED),
        /** Removed fake site for the real biome. Code 33 remains append-only reserved. */
        DAPPLED_FOREST(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 ocean ruins are emitted by the terrain product. */
        OCEAN_RUINS(false, false, PlacementStage.REMOVED),
        /** Removed: canonical 26.3 buried treasure is emitted by the terrain product. */
        BURIED_TREASURE(false, false, PlacementStage.REMOVED),
        /**
         * [CORAL-REEF] 온수 바다 산호초. 새 kind 는 항상 끝에 붙여 기존 ordinal 을 흔들지
         * 않는다.
         *
         * <p>바닐라 1.21.4 의 산호초는 구조물이 아니라 warm ocean 바이옴의 <b>vegetal
         * feature</b>({@code warm_ocean_vegetation})라 옮겨올 {@code structure_set} JSON 이
         * 없고, 지형 생성 단계에 feature 를 더하면 {@code terrain-golden.json} 이 깨진다.
         * 그래서 유황 동굴·딥다크 도시·얼룩덜룩한 숲과 같은 판단으로 런타임 구조물 사이트 lane 을
         * 쓴다. 값과 근거는 {@link CoralReefPlacement} 클래스 주석이 소유한다
         * (칸 12 청크 · UNDERWATER 1/5 = 칸당 20 % · 온수 바다 관문).
         */
        CORAL_REEF(true, true, CoralReefPlacement.CELL_SIZE, CoralReefPlacement.MAX_REACH,
                CoralReefPlacement.SITE_SALT, 34, 65_536,
                PlacementStage.DEFERRED, AdmissionScale.UNDERWATER),
        /** Removed: canonical 26.3 ruined portals are emitted by the terrain product. */
        RUINED_PORTAL(false, false, PlacementStage.REMOVED);

        private static final List<Kind> ALL = List.of(values());
        private static final Map<Integer, Kind> BY_CODE = ALL.stream().collect(Collectors.toUnmodifiableMap(
                Kind::code, Function.identity()));

        private final boolean hasGoldenWork;
        private final boolean hasOverlayWork;
        private final boolean descriptorConfigured;
        private final int cellSize;
        private final int maxReach;
        private final int siteSalt;
        private final int priorityClass;
        private final int baselineExistenceThreshold;
        private final PlacementStage placementStage;
        private final AdmissionScale admissionScale;

        Kind(boolean hasGoldenWork, boolean hasOverlayWork, int cellSize, int maxReach,
                int siteSalt, int priorityClass, int existenceThreshold,
                PlacementStage placementStage, AdmissionScale admissionScale) {
            this(hasGoldenWork, hasOverlayWork, cellSize, maxReach, siteSalt, priorityClass,
                    existenceThreshold, placementStage, admissionScale, (byte) 0);
        }

        Kind(boolean hasGoldenWork, boolean hasOverlayWork, int cellSize, int maxReach,
                int siteSalt, int priorityClass, int existenceThreshold,
                PlacementStage placementStage, AdmissionScale admissionScale, byte ignored) {
            this.hasGoldenWork = hasGoldenWork;
            this.hasOverlayWork = hasOverlayWork;
            this.descriptorConfigured = true;
            this.cellSize = cellSize;
            this.maxReach = maxReach;
            this.siteSalt = siteSalt;
            this.priorityClass = priorityClass;
            this.baselineExistenceThreshold = existenceThreshold;
            this.placementStage = placementStage;
            this.admissionScale = admissionScale;
        }

        Kind(boolean hasGoldenWork, boolean hasOverlayWork, PlacementStage placementStage) {
            this.hasGoldenWork = hasGoldenWork;
            this.hasOverlayWork = hasOverlayWork;
            this.descriptorConfigured = false;
            this.cellSize = 0;
            this.maxReach = 0;
            this.siteSalt = 0;
            this.priorityClass = 0;
            this.baselineExistenceThreshold = 0;
            this.placementStage = placementStage;
            this.admissionScale = AdmissionScale.NONE;
        }

        public boolean hasGoldenWork() { return hasGoldenWork; }
        /**
         * Append-only accepted-site identity shared with Rust planner {@code placement_ordinal}.
         * Deliberately explicit: declaration order remains free to serve placement iteration and must
         * never silently rewrite saved accepted-site identities.
         */
        public int code() {
            return switch (this) {
                case DUNGEON -> 0;
                case MINESHAFT -> 1;
                case CAMPING_SITE -> 2;
                case CAMP -> 3;
                case MINER_CAMP -> 4;
                case SMALL_RUIN -> 5;
                case MEDIUM_RUIN -> 6;
                case FLOODED_RUIN -> 7;
                case TOMB_RUIN -> 8;
                case GENERAL_RUIN -> 9;
                case VILLAGE -> 10;
                case PILLAGER_OUTPOST -> 11;
                case DESERT_TOMB -> 12;
                case METEOR_CRATER -> 13;
                case RUINED_TOWER -> 14;
                case ALTAR -> 15;
                case OCEAN_RUIN -> 16;
                case SHIPWRECK -> 17;
                case UNDERWATER_RUIN -> 18;
                case UNDERGROUND_RUIN -> 19;
                case UNDERGROUND_DUNGEON -> 20;
                case BURIED_RUIN -> 21;
                case SEALED_CHAMBER -> 22;
                case UNDERGROUND_CITY -> 23;
                case UNDERGROUND_PRISON -> 24;
                case GLITCH_DUNGEON -> 25;
                case MONSTER_SPAWN_ZONE -> 26;
                case OCEAN_MONUMENT -> 27;
                case WOODLAND_MANSION -> 28;
                case TRIAL_CHAMBER -> 29;
                case DEEP_DARK_CITY -> 30;
                case SULFUR_CAVERN -> 31;
                case BIRCH_GROVE -> 32;
                case DAPPLED_FOREST -> 33;
                case OCEAN_RUINS -> 34;
                case BURIED_TREASURE -> 35;
                case CORAL_REEF -> 36;
                case RUINED_PORTAL -> 37;
            };
        }

        public static Kind fromCode(int code) {
            Kind kind = BY_CODE.get(code);
            if (kind == null) throw new IllegalArgumentException("unknown structure kind code: " + code);
            return kind;
        }

        public static int count() { return BY_CODE.size(); }

        public static int maxCode() { return ALL.stream().mapToInt(Kind::code).max().orElseThrow(); }
        public boolean hasOverlayWork() { return hasOverlayWork; }
        public boolean descriptorConfigured() { return descriptorConfigured; }
        public PlacementStage placementStage() { return placementStage; }
        public boolean independentlyPlaced() {
            return placementStage == PlacementStage.ACTIVATION
                    || placementStage == PlacementStage.DEFERRED;
        }
        public int cellSize() { return configured(cellSize); }
        public int maxReach() { return configured(maxReach); }
        public int siteSalt() { return configured(siteSalt); }
        public int priorityClass() { return configured(priorityClass); }
        public int existenceThreshold() {
            return admissionScale.apply(configured(baselineExistenceThreshold));
        }

        public static List<Kind> placedIn(PlacementStage stage) {
            return ALL.stream().filter(kind -> kind.placementStage == stage).toList();
        }

        double expectedExistenceProbability() {
            if (!independentlyPlaced()) return 0.0;
            return existenceThreshold() / 65_536.0;
        }

        private int configured(int value) {
            if (!descriptorConfigured) {
                throw new IllegalStateException(name() + " descriptor is not configured");
            }
            return value;
        }

        static {
            if (BY_CODE.size() != ALL.size() || maxCode() != ALL.size() - 1) {
                throw new IllegalStateException("structure kind codes must be unique contiguous append-only ids");
            }
            for (Kind kind : ALL) {
                if (kind.placementStage == PlacementStage.REMOVED) {
                    if (kind.descriptorConfigured || kind.hasGoldenWork || kind.hasOverlayWork
                            || kind.admissionScale != AdmissionScale.NONE
                            || kind.baselineExistenceThreshold != 0) {
                        throw new IllegalStateException(kind + " removed-kind contract mismatch");
                    }
                } else if (kind.placementStage == PlacementStage.HOST_BOUND) {
                    if (kind.admissionScale != AdmissionScale.NONE
                            || kind.baselineExistenceThreshold != 0) {
                        throw new IllegalStateException(kind + " host-bound admission mismatch");
                    }
                } else if (!kind.descriptorConfigured
                        || kind.admissionScale == AdmissionScale.NONE
                        || kind.baselineExistenceThreshold <= 0) {
                    throw new IllegalStateException(kind + " independent admission mismatch");
                }
            }
        }
    }

    private static final int EXIST_SALT = 0x19d2c4e7;
    private static final int SHAPE_SALT = 0x63a9f10b;
    private static final int TOPOLOGY_SALT = 0x34c8a51d;
    private static final int ADAPT_SALT = 0x7b125e93;
    private static final int AGING_SALT = 0x2e6d40af;
    private static final int DIFFICULTY_SALT = 0x51f39c67;
    private static final int PART_SALT = 0x0c47ad31;
    private static final int PART0_SALT = 0x6f2b903d;
    private static final int PART1_SALT = 0x15ca87e9;
    private static final int VOXEL_SALT = 0x72e14b59;

    private final Kind kind;
    private final int seed;
    private final int cellX;
    private final int cellZ;
    private final int siteKey;
    private final int anchorX;
    private final int anchorZ;
    private final int e;
    private final int s;
    private final int t;
    private final int a;
    private final int g;
    private final int d;

    private StructureSiteDescriptor(Kind kind, int seed, int cellX, int cellZ, int siteKey,
            int anchorX, int anchorZ, int e, int s, int t, int a, int g, int d) {
        this.kind = kind;
        this.seed = seed;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.siteKey = siteKey;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        this.e = e;
        this.s = s;
        this.t = t;
        this.a = a;
        this.g = g;
        this.d = d;
    }

    public static StructureSiteDescriptor atCell(int seed, Kind kind, int cellX, int cellZ) {
        if (!kind.descriptorConfigured()) {
            throw new IllegalStateException(kind + " descriptor is not configured");
        }
        int siteKey = StructureHash.hash3(
                StructureHash.seedSalt(seed, kind.siteSalt()), cellX, 0, cellZ);
        int e = StructureHash.mix32(siteKey ^ EXIST_SALT);
        int cellSize = kind.cellSize();
        // E[16..23] and E[24..31] cover the complete custom-structure cell.
        int jitterX = (((e >>> 16) & 255) * cellSize) >>> 8;
        int jitterZ = (((e >>> 24) & 255) * cellSize) >>> 8;
        return new StructureSiteDescriptor(kind, seed, cellX, cellZ, siteKey,
                cellX * cellSize + jitterX, cellZ * cellSize + jitterZ, e,
                StructureHash.mix32(siteKey ^ SHAPE_SALT),
                StructureHash.mix32(siteKey ^ TOPOLOGY_SALT),
                StructureHash.mix32(siteKey ^ ADAPT_SALT),
                StructureHash.mix32(siteKey ^ AGING_SALT),
                StructureHash.mix32(siteKey ^ DIFFICULTY_SALT));
    }

    public Kind kind() { return kind; }
    public int seed() { return seed; }
    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
    public int siteKey() { return siteKey; }
    public int anchorX() { return anchorX; }
    public int anchorZ() { return anchorZ; }
    public int existenceLane() { return e; }
    public int shapeLane() { return s; }
    public int topologyLane() { return t; }
    public int adaptationLane() { return a; }
    public int agingLane() { return g; }
    public int difficultyLane() { return d; }
    public boolean provisionallyExists() {
        if (!kind.independentlyPlaced()) return false;
        return (e & 0xffff) < kind.existenceThreshold();
    }
    public int sizeClass() { int value = s & 3; return value == 3 ? ((s >>> 4) % 3) : value; }
    public int direction() { return s >>> 30; }

    public int partLane0(int stablePartIndex, int role) {
        int key = StructureHash.hash3(StructureHash.seedSalt(PART_SALT, role),
                siteKey, stablePartIndex, role);
        return StructureHash.mix32(key ^ PART0_SALT);
    }

    public int partLane1(int stablePartIndex, int role) {
        int key = StructureHash.hash3(StructureHash.seedSalt(PART_SALT, role),
                siteKey, stablePartIndex, role);
        return StructureHash.mix32(key ^ PART1_SALT);
    }

    public int voxelLane(int worldX, int worldY, int worldZ, int purpose) {
        return StructureHash.hash3(StructureHash.seedSalt(VOXEL_SALT, purpose),
                worldX, worldY, worldZ) ^ siteKey;
    }

    public long collisionKey(int worldX, int worldY, int worldZ) {
        int hash = StructureHash.hash3(siteKey, worldX, worldY, worldZ);
        return ((long) kind.priorityClass() << 32) | StructureHash.unsigned(hash);
    }
}
