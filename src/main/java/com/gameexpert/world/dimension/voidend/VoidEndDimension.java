package com.gameexpert.world.dimension.voidend;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.dimension.DimensionChunk;
import com.gameexpert.world.dimension.DimensionChunkProvider;
import com.gameexpert.world.dimension.DimensionDefinition;
import com.gameexpert.world.dimension.DimensionEnvironment;
import com.gameexpert.world.dimension.DimensionProviders;
import com.gameexpert.world.dimension.DimensionRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * [VOID-END] 엔드 차원 콘텐츠 플러그인. 정적판 {@code VoidEndDimension.ts} 와 같은 설명·환경·생성기를
 * 낸다. 스프링 빈이 아니며 스스로 등록하지 않는다 — 실제 조립점 {@code config/DimensionConfiguration}
 * 이 연결 시작 전에 {@link #install} 을 불러 {@code void_end} 를 활성 목적지로 만든다(CONTRACT §11A).
 */
public final class VoidEndDimension {
    /**
     * 핀 26.3 dimension_type/the_end: sky_color #000000, fog_color #181318, ambient_light 0.25,
     * sky_light_factor 0.0, has_fixed_time. fog_*_distance 속성이 없어 대기 안개는 렌더 거리에서
     * 끝나므로 WebCraft 프리셋 렌더 거리 160 블록을 쓴다. 블록광 전파는 일반 월드와 같다.
     */
    public static final DimensionEnvironment.Profile PROFILE =
            new DimensionEnvironment.Profile(0x000000, 0x181318, 160, 0.25, 1);
    /** 낮밤·해달·별 없음(바닐라 End 하늘은 end_sky 텍스처 상자). */
    public static final DimensionEnvironment ENVIRONMENT =
            DimensionEnvironment.custom(false, false, false, PROFILE, PROFILE);

    /** 바닐라 End 도착 (100.5, 49, 0.5), Direction.WEST(90°) = π/2 라디안(WebCraft yaw 규약). */
    public static final DimensionDefinition.Arrival ARRIVAL =
            new DimensionDefinition.Arrival(100.5, 49, 0.5, (float) (Math.PI / 2), 0f);
    public static final DimensionDefinition DEFINITION = new DimensionDefinition(DimensionRegistry.VOID_END,
            true, Blocks.END_PORTAL, Blocks.END_PORTAL, ARRIVAL, ENVIRONMENT);

    /**
     * SkyRenderer.buildEndSky · the_end 속성 중 {@link DimensionEnvironment} 에 필드가 없는 표현 데이터
     * (정적판 VoidEndPresentation 과 같은 값). 하늘 상자·음악은 클라이언트가 정적판 사본을 읽는다.
     */
    public record Presentation(String skybox, String skyTexture, int skyVertexColor, int skyUvRepeat,
            int skyHalfExtent, int ambientLightColor, int skyLightColor, double skyLightFactor,
            boolean fixedTime, String musicKey, int musicMinDelayTicks, int musicMaxDelayTicks) {
    }

    public static final Presentation PRESENTATION = new Presentation("end", "environment/end_sky.png",
            0x282828, 16, 100, 0x3f473f, 0xac60cd, 0.0, true, "minecraft:music.end", 6000, 24000);

    /** 게임 목표 상자 내용: 가운데 슬롯(13)의 겉날개 1. */
    public record TreasureItem(int slot, short itemType, int count) {
    }

    public static List<TreasureItem> treasureContents() {
        return List.of(new TreasureItem(13, PlayerInventory.ELYTRA, 1));
    }

    /** [END-CITY] {@code chests/end_city_treasure} 한 상자의 칸(바닐라 {@code LootTable.fill}). */
    public static List<DimensionChunkProvider.Slot> endCityTreasureSlots(long lootSeed) {
        List<DimensionChunkProvider.Slot> slots = new ArrayList<>();
        for (var entry : com.gameexpert.engine.trial.TrialLootTables.endCityTreasureSlots(lootSeed).entrySet()) {
            var stack = entry.getValue();
            slots.add(new DimensionChunkProvider.Slot(entry.getKey(), stack.itemType(), stack.count(),
                    stack.durability(), stack.enchantments(), stack.itemComponentData()));
        }
        return List.copyOf(slots);
    }

    private VoidEndDimension() {
    }

    /** 결정적 청크 공급자. 도착 발판 재구성과 보물 상자 내용도 이 콘텐츠가 소유한다. */
    public static final class ChunkProvider implements DimensionChunkProvider {
        @Override
        public DimensionChunk generate(int seed, int chunkX, int chunkZ) {
            VoidEndGenerator.Planes planes = VoidEndGenerator.generate(seed, chunkX, chunkZ);
            return new DimensionChunk(planes.blocks(), planes.states());
        }

        /**
         * 바닐라 {@code EndPortalBlock#getPortalDestination} → {@code EndPlatformFeature.createEndPlatform(
         * level, END_SPAWN_POINT.below(), true)}: 5×5 의 y−1 은 흑요석, y0..y2 는 공기(목표와 다른 칸만
         * 드랍과 함께 부수고 바꾼다). 도착은 그 발판 위 (100.5, 49, 0.5).
         */
        @Override
        public List<Cell> arrivalCells(int seed) {
            return platformCells();
        }

        /**
         * 보물 탑 꼭대기 상자(그 청크에서만 한 개)와 [END-CITY] 엔드 도시 상자({@code end_city_treasure}, 상자
         * {@code LootTableSeed} 로 굴린다) · 엔드 배 양조기(강한 치유의 물약 둘, 바닐라 병 칸 0 · 2).
         */
        @Override
        public List<Container> initialContainers(int seed, int chunkX, int chunkZ) {
            List<Container> containers = new ArrayList<>();
            int[] chest = treasureChest(seed);
            if (Math.floorDiv(chest[0], Blocks.CHUNK_X) == chunkX && Math.floorDiv(chest[2], Blocks.CHUNK_Z) == chunkZ) {
                List<Slot> slots = new ArrayList<>();
                for (TreasureItem item : treasureContents()) {
                    slots.add(new Slot(item.slot(), item.itemType(), item.count()));
                }
                containers.add(new Container(chest[0], chest[1], chest[2], Blocks.CHEST, slots));
            }
            for (VoidEndCity.Content content : VoidEndCity.chunkContents(seed, chunkX, chunkZ)) {
                if (content.kind() == VoidEndCity.Kind.TREASURE_CHEST) {
                    containers.add(new Container(content.x(), content.y(), content.z(), Blocks.CHEST,
                            endCityTreasureSlots(content.lootSeed())));
                } else if (content.kind() == VoidEndCity.Kind.BREWING_STAND) {
                    containers.add(new Container(content.x(), content.y(), content.z(), Blocks.BREWING_STAND,
                            List.of(new Slot(com.gameexpert.engine.BrewingInventory.FIRST_BOTTLE_SLOT,
                                            PlayerInventory.POTION_STRONG_HEALING, 1),
                                    new Slot(com.gameexpert.engine.BrewingInventory.LAST_BOTTLE_SLOT,
                                            PlayerInventory.POTION_STRONG_HEALING, 1))));
                }
            }
            return List.copyOf(containers);
        }

        /**
         * [END-CITY] 엔드 도시 셜커(Sentry 표지: 표지 칸 중심 발밑, 부착면 아래)와 엔드 배 겉날개 액자(Elytra 표지:
         * 표지 칸에 걸린 액자, 방향 = 조각 회전으로 돌린 SOUTH). 바닐라 {@code EndCityPiece.handleDataMarker}.
         */
        @Override
        public List<InitialMob> initialMobs(int seed, int chunkX, int chunkZ) {
            List<InitialMob> mobs = new ArrayList<>();
            // [DRAGON] EndSpikeFeature.placeSpike: 가시 기둥 중심 칸의 청크가 꼭대기 기반암 위 (x+0.5, h+1, z+0.5) 에
            // 엔드 수정을 둔다(받침 보임 · 광선 없음 · 무적 아님).
            for (VoidEndGenerator.Spike spike : VoidEndGenerator.spikes(seed)) {
                if (Math.floorDiv(spike.centerX(), 16) != chunkX || Math.floorDiv(spike.centerZ(), 16) != chunkZ) continue;
                mobs.add(new InitialMob("end_crystal", spike.centerX() + 0.5, spike.height() + 1,
                        spike.centerZ() + 0.5, 0, 0));
            }
            for (VoidEndCity.Content content : VoidEndCity.chunkContents(seed, chunkX, chunkZ)) {
                if (content.kind() == VoidEndCity.Kind.SENTRY) {
                    mobs.add(new InitialMob("shulker", content.x() + 0.5, content.y(), content.z() + 0.5,
                            VoidEndCity.DIRECTION_DOWN, 0));
                } else if (content.kind() == VoidEndCity.Kind.ELYTRA_FRAME) {
                    mobs.add(new InitialMob("item_frame", content.x(), content.y(), content.z(),
                            content.direction(), PlayerInventory.ELYTRA));
                }
            }
            return List.copyOf(mobs);
        }
    }

    /** 도착 발판 재구성 칸(흑요석 y48, 공기 y49..51, x 98..102, z −2..2). */
    public static List<DimensionChunkProvider.Cell> platformCells() {
        List<DimensionChunkProvider.Cell> cells = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy < 3; dy++) {
                    cells.add(new DimensionChunkProvider.Cell(VoidEndGenerator.PLATFORM_X + dx,
                            VoidEndGenerator.PLATFORM_Y + 1 + dy, VoidEndGenerator.PLATFORM_Z + dz,
                            dy == -1 ? Blocks.OBSIDIAN : Blocks.AIR));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** 통합자가 부르는 설치 훅. 실패하면 DimensionProviders.install 이 이번 공급자만 되돌린다. */
    public static ChunkProvider install(DimensionProviders providers) {
        ChunkProvider provider = new ChunkProvider();
        providers.install(DEFINITION, provider);
        return provider;
    }

    /** 도착 발판 중심(흑요석 y48, 반지름 2, 위 3칸 공기). */
    public static int[] arrivalPlatform() {
        return new int[] {VoidEndGenerator.PLATFORM_X, VoidEndGenerator.PLATFORM_Y, VoidEndGenerator.PLATFORM_Z, 2};
    }

    /** 귀환 포털 END_PORTAL 칸 [x, y, z] (기둥 칸 제외). */
    public static List<int[]> returnPortalCells(int seed) {
        int y = VoidEndGenerator.podiumY(seed);
        List<int[]> cells = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (x * x + z * z < 6.25 && !(x == 0 && z == 0)) cells.add(new int[] {x, y, z});
            }
        }
        return cells;
    }

    /** 보물 상자 좌표와 state: [x, y, z, state]. */
    public static int[] treasureChest(int seed) {
        VoidEndGenerator.TreasureSite site = VoidEndGenerator.treasureSite(seed);
        return new int[] {site.chestX(), site.chestY(), site.chestZ(), site.chestState()};
    }
}
