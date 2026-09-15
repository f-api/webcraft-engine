package com.gameexpert.world.dimension;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.ChunkProductSource;
import com.gameexpert.terrain.TerrainAccessor;

/** 명시적으로 선택된 사용자 차원 전용. 일반 월드의 canonical 공급자와 섞지 않는다. */
public final class DimensionChunkProductSource implements ChunkProductSource {
    private static final int PREPARED_LIMIT = 64;
    private final String dimension;
    private final int seed;
    private final com.gameexpert.world.WorldGenerationProfile generationProfile;
    @Override public com.gameexpert.world.WorldGenerationProfile generationProfile() {
        return generationProfile;
    }
    private final DimensionChunkProvider provider;
    private final Map<Long, DimensionChunk> prepared = new LinkedHashMap<>(16, .75f, true);

    public DimensionChunkProductSource(DimensionProviders providers, String dimension, int seed) {
        this(providers, dimension, seed, com.gameexpert.world.WorldGenerationProfiles.newWorldProfile());
    }

    public DimensionChunkProductSource(DimensionProviders providers, String dimension, int seed,
            com.gameexpert.world.WorldGenerationProfile profile) {
        generationProfile = com.gameexpert.world.WorldGenerationProfiles.requireSupported(profile);
        this.provider = providers.require(dimension);
        this.dimension = dimension;
        this.seed = seed;
    }

    public String dimension() { return dimension; }

    /** [DIMENSION-EXT] 콘텐츠 도착 공간 재구성 칸. */
    public java.util.List<DimensionChunkProvider.Cell> arrivalCells() {
        return java.util.List.copyOf(provider.arrivalCells(seed));
    }

    /** [DIMENSION-EXT] 이 칸의 콘텐츠 소유 초기 컨테이너, 없으면 null. */
    public DimensionChunkProvider.Container initialContainerAt(int x, int y, int z) {
        for (DimensionChunkProvider.Container container : provider.initialContainers(seed,
                Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z))) {
            if (container.x() == x && container.y() == y && container.z() == z) return container;
        }
        return null;
    }

    /** [END-CITY] 이 청크의 콘텐츠 소유 초기 개체(첫 채움 한 번). */
    public java.util.List<DimensionChunkProvider.InitialMob> initialMobs(int chunkX, int chunkZ) {
        return java.util.List.copyOf(provider.initialMobs(seed, chunkX, chunkZ));
    }

    /** 공급자는 결정론적이어야 한다. 유한 준비 캐시를 잃어도 같은 block/state를 재생성한다. */
    public synchronized DimensionChunk dimensionChunk(int worldSeed, int chunkX, int chunkZ) {
        if (worldSeed != seed) throw new IllegalArgumentException("dimension source seed mismatch");
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        DimensionChunk chunk = prepared.get(key);
        if (chunk == null) {
            chunk = java.util.Objects.requireNonNull(provider.generate(seed, chunkX, chunkZ));
            prepared.put(key, chunk);
            if (prepared.size() > PREPARED_LIMIT) prepared.remove(prepared.keySet().iterator().next());
        }
        return chunk;
    }

    @Override
    public ChunkGenerator.GeneratedChunk generate(int worldSeed, int chunkX, int chunkZ) {
        DimensionChunk chunk = dimensionChunk(worldSeed, chunkX, chunkZ);
        short[] heights = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        java.util.Arrays.fill(heights, (short) (Blocks.MIN_Y - 1));
        for (int z = 0; z < Blocks.CHUNK_Z; z++) for (int x = 0; x < Blocks.CHUNK_X; x++) {
            for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
                if (chunk.blockAt(Blocks.blockIndex(x, y, z)) != Blocks.AIR) {
                    heights[x + z * Blocks.CHUNK_X] = (short) y;
                    break;
                }
            }
        }
        return ChunkGenerator.GeneratedChunk.customDimension(
                chunkX, chunkZ, chunk.blocks(), heights);
    }

    /** 활성화 worker에서 생성 state를 기존 replayable overlay에 설치한다. 저장 diff가 우선한다. */
    public TerrainAccessor.ReplayableChunkPatch initialStates(int worldSeed, int chunkX, int chunkZ) {
        DimensionChunk chunk = dimensionChunk(worldSeed, chunkX, chunkZ);
        try (var builder = TerrainAccessor.ReplayableChunkPatch.builder(chunkX, chunkZ)) {
            for (int index = 0; index < Blocks.CHUNK_BLOCKS; index++) {
                int state = chunk.stateAt(index);
                if (state == 0) continue;
                int x = Math.addExact(Math.multiplyExact(chunkX, Blocks.CHUNK_X), index % Blocks.CHUNK_X);
                int z = Math.addExact(Math.multiplyExact(chunkZ, Blocks.CHUNK_Z),
                        index / Blocks.CHUNK_X % Blocks.CHUNK_Z);
                int y = Blocks.MIN_Y + index / (Blocks.CHUNK_X * Blocks.CHUNK_Z);
                builder.put(x, y, z, chunk.blockAt(index), state);
            }
            return builder.build();
        }
    }
}
