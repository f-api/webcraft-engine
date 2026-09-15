package com.gameexpert.world.dimension.flesh;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.dimension.*;

/** 원본 살점 생성 규칙의 차원 플러그인. 저장된 AIR/편집 우선순위는 기존 source가 소유한다. */
public final class FleshNetherProvider implements DimensionChunkProvider {
    public static DimensionDefinition definition() {
        return new DimensionDefinition(DimensionRegistry.FLESH_NETHER, true,
                Blocks.NETHER_PORTAL, Blocks.NETHER_PORTAL,
                new DimensionDefinition.Arrival(8.5, 64, 11.5, 0, 0),
                DimensionEnvironment.custom(true, false, false,
                        new DimensionEnvironment.Profile(0x39202a, 0x241b27, 160, .30, .65),
                        new DimensionEnvironment.Profile(0x000000, 0x000000, 22, 0, .40)));
    }

    @Override public DimensionChunk generate(int seed, int chunkX, int chunkZ) {
        return FleshColonyTerrain.generate(seed, chunkX, chunkZ);
    }
    @Override public java.util.List<InitialMob> initialMobs(int seed,int chunkX,int chunkZ) {
        return FleshMawPlacements.inChunk(seed,chunkX,chunkZ);
    }
}
