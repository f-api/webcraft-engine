package com.gameexpert.block.snapshot;

import java.util.Arrays;
import java.util.List;

import com.gameexpert.terrain.Blocks;

/**
 * 한 순간에 고정한 서버 권위 청크 상태입니다.
 *
 * <p>블록 타입과 상태를 분리해 보관하지만, 전송 시에는 section palette 항목 하나로 함께 인코딩합니다.
 * 이 객체에는 생성기 입력이나 diff 목록이 아니라 이미 합성된 최종 셀 값만 들어갑니다.</p>
 */
public final class ChunkSnapshot {

    private final long worldEpoch;
    private final int cx;
    private final int cz;
    private final long fromVersion;
    private final long toVersion;
    private final byte[] surfaceBiomes;
    private final short[] surfaceHeights;
    private final short[] blockTypes;
    private final byte[] blockStates;
    private final DecoratedPotMotif decoratedPotMotif;

    public ChunkSnapshot(long worldEpoch, int cx, int cz, long fromVersion, long toVersion,
            byte[] surfaceBiomes, short[] surfaceHeights, short[] blockTypes, byte[] blockStates) {
        this(worldEpoch, cx, cz, fromVersion, toVersion,
                surfaceBiomes, surfaceHeights, blockTypes, blockStates, DecoratedPotMotif.empty(), true);
    }

    public ChunkSnapshot(long worldEpoch, int cx, int cz, long fromVersion, long toVersion,
            byte[] surfaceBiomes, short[] surfaceHeights, short[] blockTypes, byte[] blockStates,
            DecoratedPotMotif decoratedPotMotif) {
        this(worldEpoch, cx, cz, fromVersion, toVersion, surfaceBiomes, surfaceHeights, blockTypes,
                blockStates, decoratedPotMotif, true);
    }

    /**
     * 방금 생성해 다른 곳과 공유하지 않는 배열의 소유권을 스냅샷에 이전합니다.
     * 호출 후에는 배열을 읽거나 변경하면 안 됩니다.
     */
    public static ChunkSnapshot takeOwnership(long worldEpoch, int cx, int cz,
            long fromVersion, long toVersion, byte[] surfaceBiomes, short[] surfaceHeights,
            short[] blockTypes, byte[] blockStates) {
        return new ChunkSnapshot(worldEpoch, cx, cz, fromVersion, toVersion,
                surfaceBiomes, surfaceHeights, blockTypes, blockStates, DecoratedPotMotif.empty(), false);
    }

    public static ChunkSnapshot takeOwnership(long worldEpoch, int cx, int cz,
            long fromVersion, long toVersion, byte[] surfaceBiomes, short[] surfaceHeights,
            short[] blockTypes, byte[] blockStates, DecoratedPotMotif decoratedPotMotif) {
        return new ChunkSnapshot(worldEpoch, cx, cz, fromVersion, toVersion, surfaceBiomes, surfaceHeights,
                blockTypes, blockStates, decoratedPotMotif, false);
    }

    private ChunkSnapshot(long worldEpoch, int cx, int cz, long fromVersion, long toVersion,
            byte[] surfaceBiomes, short[] surfaceHeights, short[] blockTypes, byte[] blockStates,
            DecoratedPotMotif decoratedPotMotif, boolean copyArrays) {
        if (fromVersion < 0 || toVersion < fromVersion) {
            throw new IllegalArgumentException("잘못된 청크 버전 범위입니다.");
        }
        if (surfaceBiomes == null || surfaceBiomes.length != Blocks.CHUNK_X * Blocks.CHUNK_Z
                || surfaceHeights == null || surfaceHeights.length != Blocks.CHUNK_X * Blocks.CHUNK_Z
                || blockTypes == null || blockTypes.length != Blocks.CHUNK_BLOCKS
                || blockStates == null || blockStates.length != Blocks.CHUNK_BLOCKS) {
            throw new IllegalArgumentException("청크 스냅샷 배열 크기가 올바르지 않습니다.");
        }
        this.worldEpoch = worldEpoch;
        this.cx = cx;
        this.cz = cz;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.surfaceBiomes = copyArrays ? surfaceBiomes.clone() : surfaceBiomes;
        this.surfaceHeights = copyArrays ? surfaceHeights.clone() : surfaceHeights;
        this.blockTypes = copyArrays ? blockTypes.clone() : blockTypes;
        this.blockStates = copyArrays ? blockStates.clone() : blockStates;
        this.decoratedPotMotif = decoratedPotMotif == null ? DecoratedPotMotif.empty()
                : decoratedPotMotif.validatedFor(this.blockTypes);
    }

    /** 방어 복사 없이 코덱 내부에서만 사용합니다. 호출자는 값을 변경하면 안 됩니다. */
    byte[] surfaceBiomesInternal() {
        return surfaceBiomes;
    }

    /** 방어 복사 없이 코덱 내부에서만 사용합니다. 호출자는 값을 변경하면 안 됩니다. */
    short[] surfaceHeightsInternal() {
        return surfaceHeights;
    }

    /** 방어 복사 없이 코덱 내부에서만 사용합니다. 호출자는 값을 변경하면 안 됩니다. */
    short[] blockTypesInternal() {
        return blockTypes;
    }

    /** 방어 복사 없이 코덱 내부에서만 사용합니다. 호출자는 값을 변경하면 안 됩니다. */
    byte[] blockStatesInternal() {
        return blockStates;
    }

    public long getWorldEpoch() {
        return worldEpoch;
    }

    public int getCx() {
        return cx;
    }

    public int getCz() {
        return cz;
    }

    public long getFromVersion() {
        return fromVersion;
    }

    public long getToVersion() {
        return toVersion;
    }

    /** Returns a defensive copy; codec-only callers retain the package-private raw path above. */
    public byte[] getSurfaceBiomes() {
        return surfaceBiomes.clone();
    }

    /** Returns a defensive copy; codec-only callers retain the package-private raw path above. */
    public short[] getSurfaceHeights() {
        return surfaceHeights.clone();
    }

    /** Returns a defensive copy; codec-only callers retain the package-private raw path above. */
    public short[] getBlockTypes() {
        return blockTypes.clone();
    }

    /** Returns a defensive copy; codec-only callers retain the package-private raw path above. */
    public byte[] getBlockStates() {
        return blockStates.clone();
    }

    public DecoratedPotMotif getDecoratedPotMotif() {
        return decoratedPotMotif;
    }

    public int blockTypeAt(int localX, int y, int localZ) {
        return Short.toUnsignedInt(blockTypes[Blocks.blockIndex(localX, y, localZ)]);
    }

    public int blockStateAt(int localX, int y, int localZ) {
        return Byte.toUnsignedInt(blockStates[Blocks.blockIndex(localX, y, localZ)]);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChunkSnapshot that)) return false;
        return worldEpoch == that.worldEpoch && cx == that.cx && cz == that.cz
                && fromVersion == that.fromVersion && toVersion == that.toVersion
                && Arrays.equals(surfaceBiomes, that.surfaceBiomes)
                && Arrays.equals(surfaceHeights, that.surfaceHeights)
                && Arrays.equals(blockTypes, that.blockTypes)
                && Arrays.equals(blockStates, that.blockStates)
                && decoratedPotMotif.equals(that.decoratedPotMotif);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(worldEpoch);
        result = 31 * result + cx;
        result = 31 * result + cz;
        result = 31 * result + Long.hashCode(fromVersion);
        result = 31 * result + Long.hashCode(toVersion);
        result = 31 * result + Arrays.hashCode(surfaceBiomes);
        result = 31 * result + Arrays.hashCode(surfaceHeights);
        result = 31 * result + Arrays.hashCode(blockTypes);
        result = 31 * result + Arrays.hashCode(blockStates);
        return 31 * result + decoratedPotMotif.hashCode();
    }

    /** Immutable v3 sidecar projection for plain Trial decorated pots. */
    public static final class DecoratedPotMotif {
        private static final List<String> DECLARATION = List.of(
                "minecraft:brick", "minecraft:flow_pottery_sherd",
                "minecraft:guster_pottery_sherd", "minecraft:scrape_pottery_sherd");
        private static final DecoratedPotMotif EMPTY = new DecoratedPotMotif(List.of(), new int[0], new byte[0]);

        private final List<String> keys;
        private final int[] packedIndexes;
        private final byte[] faces;

        public DecoratedPotMotif(List<String> keys, int[] packedIndexes, byte[] faces) {
            if (keys == null || packedIndexes == null || faces == null) {
                throw new IllegalArgumentException("장식 항아리 스냅샷 sidecar가 비어 있습니다.");
            }
            if (keys.isEmpty()) {
                if (packedIndexes.length != 0 || faces.length != 0) {
                    throw new IllegalArgumentException("빈 장식 항아리 sidecar에 항목이 있습니다.");
                }
            } else if (keys.size() > DECLARATION.size() || packedIndexes.length < 1
                    || packedIndexes.length > Blocks.CHUNK_BLOCKS || faces.length != packedIndexes.length * 4) {
                throw new IllegalArgumentException("장식 항아리 sidecar 수가 올바르지 않습니다.");
            }
            int previousDeclaration = -1;
            for (String key : keys) {
                if (key == null || key.length() < 1 || key.length() > 64) {
                    throw new IllegalArgumentException("장식 항아리 키 길이가 올바르지 않습니다.");
                }
                for (int index = 0; index < key.length(); index++) {
                    if (key.charAt(index) < 0x20 || key.charAt(index) > 0x7e) {
                        throw new IllegalArgumentException("장식 항아리 키가 ASCII가 아닙니다.");
                    }
                }
                int declaration = DECLARATION.indexOf(key);
                if (declaration < 0 || declaration <= previousDeclaration) {
                    throw new IllegalArgumentException("장식 항아리 키가 고정 선언 순서 부분집합이 아닙니다.");
                }
                previousDeclaration = declaration;
            }
            this.keys = List.copyOf(keys);
            this.packedIndexes = packedIndexes.clone();
            this.faces = faces.clone();
            for (byte face : this.faces) {
                if (Byte.toUnsignedInt(face) >= this.keys.size()) {
                    throw new IllegalArgumentException("장식 항아리 면 키 index가 범위를 벗어났습니다.");
                }
            }
        }

        public static DecoratedPotMotif empty() {
            return EMPTY;
        }

        public static List<String> declaration() {
            return DECLARATION;
        }

        public List<String> keys() {
            return keys;
        }

        public int[] packedIndexes() {
            return packedIndexes.clone();
        }

        public byte[] faces() {
            return faces.clone();
        }

        boolean isEmpty() {
            return keys.isEmpty();
        }

        DecoratedPotMotif validatedFor(short[] blockTypes) {
            int previous = -1;
            for (int packed : packedIndexes) {
                if (packed < 0 || packed >= Blocks.CHUNK_BLOCKS || packed <= previous
                        || Short.toUnsignedInt(blockTypes[packed]) != Blocks.DECORATED_POT) {
                    throw new IllegalArgumentException("장식 항아리 sidecar 칸이 올바르지 않습니다.");
                }
                previous = packed;
            }
            return this;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof DecoratedPotMotif that)) return false;
            return keys.equals(that.keys) && Arrays.equals(packedIndexes, that.packedIndexes)
                    && Arrays.equals(faces, that.faces);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * keys.hashCode() + Arrays.hashCode(packedIndexes)) + Arrays.hashCode(faces);
        }
    }
}
