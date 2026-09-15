package com.gameexpert.engine;

import java.util.Arrays;

import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.terrain.Blocks;

/** 상주 중인 권위 컬럼을 Minecraft map-color 바이트로 투영하는 scale-0 샘플러입니다. */
final class WorldMapColorSampler {

    static final int NONE = 0;
    static final int CLEAR = NONE;
    static final int GRASS = 1;
    static final int SAND = 2;
    static final int WOOL = 3;
    static final int FIRE = 4;
    static final int ICE = 5;
    static final int METAL = 6;
    static final int PLANT = 7;
    static final int SNOW = 8;
    static final int CLAY = 9;
    static final int DIRT = 10;
    static final int STONE = 11;
    static final int WATER = 12;
    static final int WOOD = 13;
    static final int QUARTZ = 14;
    static final int ORANGE = 15;
    static final int MAGENTA = 16;
    static final int LIGHT_BLUE = 17;
    static final int YELLOW = 18;
    static final int LIGHT_GREEN = 19;
    static final int PINK = 20;
    static final int GRAY = 21;
    static final int LIGHT_GRAY = 22;
    static final int CYAN = 23;
    static final int PURPLE = 24;
    static final int BLUE = 25;
    static final int BROWN = 26;
    static final int GREEN = 27;
    static final int RED = 28;
    static final int BLACK = 29;
    static final int GOLD = 30;
    static final int DIAMOND = 31;
    /** [TRIAL-GAP] MapColor.EMERALD(33) — 에메랄드 블록. 정적판 StandaloneMaps 의 EMERALD 와 같다. */
    static final int EMERALD = 33;
    static final int PODZOL = 34;
    static final int NETHER = 35;
    static final int TERRACOTTA_WHITE = 36;
    static final int TERRACOTTA_ORANGE = 37;
    static final int TERRACOTTA_MAGENTA = 38;
    static final int TERRACOTTA_LIGHT_BLUE = 39;
    static final int TERRACOTTA_YELLOW = 40;
    static final int TERRACOTTA_LIGHT_GREEN = 41;
    static final int TERRACOTTA_PINK = 42;
    static final int TERRACOTTA_GRAY = 43;
    static final int TERRACOTTA_LIGHT_GRAY = 44;
    static final int TERRACOTTA_CYAN = 45;
    static final int TERRACOTTA_PURPLE = 46;
    static final int TERRACOTTA_BLUE = 47;
    static final int TERRACOTTA_BROWN = 48;
    static final int TERRACOTTA_GREEN = 49;
    static final int TERRACOTTA_RED = 50;
    static final int TERRACOTTA_BLACK = 51;
    static final int CRIMSON_HYPHAE = 54;
    static final int WARPED_NYLIUM = 55;
    static final int WARPED_STEM = 56;
    static final int WARPED_HYPHAE = 57;
    static final int DEEPSLATE = 59;
    static final int RAW_IRON = 60;
    static final int GLOW_LICHEN = 61;

    private static final int[] DYE_MAP_COLORS = {
            SNOW, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIGHT_GREEN, PINK, GRAY,
            LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK
    };
    private static final byte UNASSIGNED = -1;
    private static final byte[] BLOCK_MAP_COLORS =
            new byte[Blocks.BLOCK_ID_TABLE_CAPACITY];

    static {
        Arrays.fill(BLOCK_MAP_COLORS, UNASSIGNED);

        // Pinned official Java 1.21.4 default-state MapColor identities. Project-only blocks use
        // the same explicit identities as StandaloneMaps.ts; biome render tint is never involved.
        assign(CLEAR, Blocks.AIR, Blocks.GLASS, Blocks.TORCH, Blocks.LADDER,
                Blocks.NETHER_PORTAL, Blocks.PRIMED_TNT, Blocks.GLASS_PANE,
                Blocks.IRON_BARS, Blocks.RAIL, Blocks.TATTERED_BANNER, Blocks.CHERRY_BONSAI);
        assignRange(CLEAR, Blocks.WALL_TORCH_N, Blocks.WALL_TORCH_W);
        assign(GRAY, Blocks.TINTED_GLASS);
        assign(GRASS, Blocks.GRASS);
        assign(SAND, Blocks.SAND, Blocks.BIRCH_LOG, Blocks.BIRCH_LOG_X, Blocks.BIRCH_LOG_Z,
                Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB,
                Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.BONE_BLOCK,
                Blocks.GLOWSTONE);
        assign(WOOL, Blocks.COBWEB, Blocks.MUSHROOM_STEM);
        assign(FIRE, Blocks.LAVA_SOURCE, Blocks.TNT, Blocks.FIRE);
        assignRange(FIRE, Blocks.LAVA_SOURCE, Blocks.LAVA_SOURCE + 3);
        assign(ICE, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE);
        assign(METAL, Blocks.IRON_BLOCK, Blocks.BREWING_STAND, Blocks.GRINDSTONE,
                Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL);
        assign(PLANT, Blocks.LEAVES, Blocks.BIRCH_LEAVES, Blocks.TALL_GRASS,
                Blocks.FLOWER_RED, Blocks.FLOWER_YELLOW, Blocks.SUGARCANE, Blocks.CACTUS,
                Blocks.LILY_PAD, Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING, Blocks.BAMBOO,
                Blocks.VINE, Blocks.FERN, Blocks.BUSH, Blocks.SPORE_BLOSSOM,
                Blocks.SMALL_DRIPLEAF, Blocks.BIG_DRIPLEAF, Blocks.AZALEA,
                Blocks.FLOWERING_AZALEA, Blocks.WHEAT_CROP, Blocks.CARROT_CROP,
                Blocks.POTATO_CROP, Blocks.BEETROOT_CROP, Blocks.PUMPKIN_STEM,
                Blocks.SPRUCE_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES,
                Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES,
                Blocks.MANGROVE_PROPAGULE, Blocks.SPRUCE_SAPLING, Blocks.JUNGLE_SAPLING,
                Blocks.ACACIA_SAPLING, Blocks.DARK_OAK_SAPLING, Blocks.CAVE_VINES,
                Blocks.CAVE_VINES_PLANT,
                // [CROP-BERRY] 달콤한 열매 덤불. 바닐라 지도색도 PLANT 다(열매가 붉어도
                // 블록 대부분이 잎·가지다). StandaloneMaps.ts 가 이미 쥔 값의 사본이다.
                Blocks.SWEET_BERRY_BUSH);
        assign(PLANT, Blocks.RAFFLESIA);
        assign(SNOW, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW);
        assign(CLAY, Blocks.CLAY);
        assign(DIRT, Blocks.DIRT, Blocks.FARMLAND, Blocks.ROOTED_DIRT, Blocks.DIRT_PATH,
                Blocks.COARSE_DIRT, Blocks.GRANITE, Blocks.JUNGLE_LOG, Blocks.HANGING_ROOTS,
                Blocks.BROWN_MUSHROOM_BLOCK, Blocks.PACKED_MUD);
        assign(STONE, Blocks.STONE, Blocks.COBBLE, Blocks.BEDROCK, Blocks.GRAVEL,
                Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
                Blocks.STONE_BRICK, Blocks.COBBLE_SLAB, Blocks.FURNACE, Blocks.FURNACE_LIT,
                Blocks.SPAWNER_BASE, Blocks.SPAWNER_BASE + 1, Blocks.SPAWNER_BASE + 2,
                Blocks.MOSSY_COBBLE, Blocks.MOSSY_STONE_BRICK, Blocks.COBBLE_STAIRS,
                Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.COBBLE_WALL,
                Blocks.MOSSY_COBBLE_WALL, Blocks.STONE_BRICK_WALL, Blocks.EMERALD_ORE,
                Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.ANDESITE, Blocks.COPPER_ORE,
                Blocks.BLAST_FURNACE, Blocks.CAULDRON, Blocks.SMOKER, Blocks.STONECUTTER,
                Blocks.SMOOTH_STONE);
        assignRange(WATER, Blocks.WATER_SOURCE, Blocks.WATER_SOURCE + 7);
        assign(WATER, Blocks.KELP, Blocks.SEAGRASS);
        assign(WOOD, Blocks.LOG, Blocks.PLANK, Blocks.PLANK_SLAB, Blocks.DOOR_CLOSED,
                // [CHEST-FAMILY] 덫 상자도 나무 상자라 같은 WOOD 지도색이다
                // (구리 상자는 아래 구리 계열 색을 따로 받는다).
                Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LOG_X, Blocks.LOG_Z,
                Blocks.CRAFTING_TABLE,
                Blocks.WOOD_STAIRS, Blocks.WOOD_FENCE, Blocks.WOOD_FENCE_GATE,
                Blocks.WOOD_TRAPDOOR, Blocks.DEAD_BUSH, Blocks.BOOKSHELF, Blocks.BARREL,
                Blocks.CARTOGRAPHY_TABLE, Blocks.COMPOSTER, Blocks.FLETCHING_TABLE,
                Blocks.LECTERN, Blocks.LOOM, Blocks.SMITHING_TABLE);
        assign(QUARTZ, Blocks.DIORITE, Blocks.SEA_LANTERN);
        assign(ORANGE, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.RED_SANDSTONE,
                Blocks.ACACIA_LOG, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN,
                Blocks.JACK_O_LANTERN, Blocks.RAW_COPPER_BLOCK, Blocks.COPPER_BLOCK,
                Blocks.COPPER_LANTERN, Blocks.LIGHTNING_ROD);
        assign(PINK, Blocks.CHERRY_LEAVES, Blocks.CHERRY_SAPLING);
        // Official defaults: sculk sensor is CYAN; the other sculk blocks are BLACK.
        assign(BLACK, Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST,
                Blocks.SCULK_SHRIEKER);
        assign(CYAN, Blocks.SCULK_SENSOR);
        // Dried ghast is COLOR_GRAY in the official 26.2 server block bootstrap.
        assign(GRAY, Blocks.DRIED_GHAST);
        assign(STONE, Blocks.TRIAL_SPAWNER, Blocks.VAULT);
        // [SULFUR] 유황 계열은 황록–노랑 팔레트라 지도에서도 노랑이다. 분출구·모래까지
        // 같은 색으로 묶어 지대 전체가 한 덩어리로 읽히게 한다.
        assign(YELLOW, Blocks.HAY_BLOCK, Blocks.SULFUR_BLOCK, Blocks.CHISELED_SULFUR,
                Blocks.POLISHED_SULFUR, Blocks.POLISHED_SULFUR_SLAB,
                Blocks.POLISHED_SULFUR_STAIRS, Blocks.POLISHED_SULFUR_WALL,
                Blocks.POTENT_SULFUR, Blocks.SULFUR_BRICKS, Blocks.SULFUR_BRICK_SLAB,
                Blocks.SULFUR_BRICK_STAIRS, Blocks.SULFUR_BRICK_WALL,
                Blocks.SULFUR_SLAB, Blocks.SULFUR_STAIRS, Blocks.SULFUR_WALL,
                Blocks.SULFUR_SPIKE);
        assign(RED, Blocks.CHISELED_CINNABAR, Blocks.CINNABAR, Blocks.CINNABAR_BRICKS,
                Blocks.CINNABAR_BRICK_SLAB, Blocks.CINNABAR_BRICK_STAIRS,
                Blocks.CINNABAR_BRICK_WALL, Blocks.CINNABAR_SLAB, Blocks.CINNABAR_STAIRS,
                Blocks.CINNABAR_WALL, Blocks.POLISHED_CINNABAR,
                Blocks.POLISHED_CINNABAR_SLAB, Blocks.POLISHED_CINNABAR_STAIRS,
                Blocks.POLISHED_CINNABAR_WALL);
        assign(PURPLE, Blocks.MYCELIUM, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST,
                Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER);
        assign(BROWN, Blocks.MUSHROOM_BROWN, Blocks.DARK_OAK_LOG);
        assign(GREEN, Blocks.SEA_PICKLE, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET);
        assign(RED, Blocks.MUSHROOM_RED, Blocks.CORAL, Blocks.RED_MUSHROOM_BLOCK,
                Blocks.MANGROVE_LOG, Blocks.ENCHANTING_TABLE, Blocks.BED);
        assign(BLACK, Blocks.COAL_BLOCK, Blocks.OBSIDIAN, Blocks.SMOOTH_BASALT);
        assign(GOLD, Blocks.GOLD_BLOCK, Blocks.RAW_GOLD_BLOCK);
        assign(DIAMOND, Blocks.DIAMOND_BLOCK);
        assign(PODZOL, Blocks.PODZOL, Blocks.SPRUCE_LOG, Blocks.MANGROVE_ROOTS,
                Blocks.MUDDY_MANGROVE_ROOTS, Blocks.CAMPFIRE);
        assign(NETHER, Blocks.NETHER_GOLD_ORE);
        assign(TERRACOTTA_WHITE, Blocks.WHITE_TERRACOTTA, Blocks.CALCITE, Blocks.CHERRY_LOG);
        assign(TERRACOTTA_ORANGE, Blocks.ORANGE_TERRACOTTA);
        assign(TERRACOTTA_YELLOW, Blocks.YELLOW_TERRACOTTA);
        assign(TERRACOTTA_GRAY, Blocks.TUFF);
        assign(TERRACOTTA_LIGHT_GRAY, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.MUD_BRICKS,
                Blocks.EXPOSED_COPPER);
        assign(TERRACOTTA_CYAN, Blocks.MUD);
        assign(TERRACOTTA_BROWN, Blocks.BROWN_TERRACOTTA, Blocks.DRIPSTONE_BLOCK,
                Blocks.POINTED_DRIPSTONE);
        assign(TERRACOTTA_RED, Blocks.RED_TERRACOTTA);
        assign(WARPED_STEM, Blocks.WEATHERED_COPPER);
        assign(WARPED_NYLIUM, Blocks.OXIDIZED_COPPER);
        assign(DEEPSLATE, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        assignRange(DEEPSLATE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        assign(RAW_IRON, Blocks.RAW_IRON_BLOCK);
        assign(GLOW_LICHEN, Blocks.GLOW_LICHEN);

        // Spring-to-Life default-state identities extracted from the official 26.2 server.
        assign(PLANT, Blocks.WILDFLOWERS, Blocks.FIREFLY_BUSH);
        assign(BROWN, Blocks.LEAF_LITTER);
        assign(PINK, Blocks.CACTUS_FLOWER);
        assign(YELLOW, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS);
        // Magma is NETHER in Java 1.21.4. The project-only sulfur carapace follows the
        // explicit YELLOW identity of the rest of its sulfur-crystal material family.
        assign(NETHER, Blocks.MAGMA);
        assign(YELLOW, Blocks.BRIMSTONE_CARAPACE_TROPHY);
        // [PALE-GARDEN] 창백한 참나무 목재는 바닐라 MapColor 가 QUARTZ(거의 흰빛)다 —
        // 지도에서 다른 수종과 갈리는 유일한 특징이라 그대로 따른다. 잎·묘목·늘어진 이끼는
        // 식생 그룹(PLANT)에, 창백한 이끼 블록·바닥은 기존 이끼(66·199)와 같은 GREEN 에 둔다.
        assign(QUARTZ, Blocks.PALE_OAK_LOG, Blocks.PALE_OAK_LOG_X, Blocks.PALE_OAK_LOG_Z,
                Blocks.STRIPPED_PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG_X,
                Blocks.STRIPPED_PALE_OAK_LOG_Z);
        for (int id = Blocks.PALE_OAK_PLANK; id <= Blocks.PALE_OAK_DOOR; id++) {
            BLOCK_MAP_COLORS[id] = (byte) QUARTZ;
        }
        assign(PLANT, Blocks.PALE_OAK_LEAVES, Blocks.PALE_OAK_SAPLING,
                Blocks.PALE_HANGING_MOSS);
        assign(GREEN, Blocks.PALE_MOSS_BLOCK, Blocks.PALE_MOSS_CARPET);
        // [CREAKING] 크리킹 하트 쌍둥이(1508·1509)와 수지 블록(1510)은 크리킹 웨이브가 지도
        // 색 없이 착지해 이 표에 구멍으로 남아 있었다. 바닐라 블록 물성 스냅샷에 두 블록이
        // 없어 map color 원문을 확보하지 못했으므로 재질에서 파생한다([C]). 하트는 창백한
        // 참나무 원목을 두른 블록이라 그 QUARTZ 를 물려받고(활성 쌍둥이는 겉모습만 다르다),
        // 수지 블록은 텍스처 기준색이 주황 그 자체라 ORANGE 다. StandaloneMaps.ts 사본.
        assign(QUARTZ, Blocks.CREAKING_HEART, Blocks.CREAKING_HEART_ACTIVE);
        assign(ORANGE, Blocks.RESIN_BLOCK);
        // [COOKING]·[GOLD-FOOD] 같은 구멍. 케이크는 바닐라 map color 가 WOOL 이고 [B],
        // 황금 민들레는 저장소 전용 꽃이라 원본 민들레와 같은 PLANT 다.
        assign(WOOL, Blocks.CAKE);
        assign(PLANT, Blocks.GOLDEN_DANDELION);

        // [POPLAR] 포플러 목재는 담황이라 기존 목재 그룹(WOOD)에 둔다. 잎은 바닐라 map color
        // 가 변종마다 갈린다 — 노랑 18 COLOR_YELLOW · 주황 15 COLOR_ORANGE · 빨강
        // 28 COLOR_RED [B]. 이 저장소의 지도 팔레트에 그대로 대응하는 세 그룹을 쓴다.
        assign(WOOD, Blocks.POPLAR_LOG, Blocks.POPLAR_LOG_X, Blocks.POPLAR_LOG_Z,
                Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG_X,
                Blocks.STRIPPED_POPLAR_LOG_Z, Blocks.POPLAR_BUTTON,
                Blocks.POPLAR_PRESSURE_PLATE, Blocks.POPLAR_SIGN,
                Blocks.POPLAR_HANGING_SIGN, Blocks.POPLAR_WOOD,
                Blocks.STRIPPED_POPLAR_WOOD, Blocks.POPLAR_SHELF);
        for (int id = Blocks.POPLAR_PLANK; id <= Blocks.POPLAR_DOOR; id++) {
            BLOCK_MAP_COLORS[id] = (byte) WOOD;
        }
        assign(YELLOW, Blocks.POPLAR_LEAVES_YELLOW);
        assign(ORANGE, Blocks.POPLAR_LEAVES_ORANGE);
        assign(RED, Blocks.POPLAR_LEAVES_RED);
        assign(PLANT, Blocks.POPLAR_SAPLING);
        assign(PLANT, Blocks.POTTED_POPLAR_SAPLING);
        assign(RED, Blocks.RED_SHRUB);

        // [CORAL-REEF] 산호 30종의 지도색은 [A] 1.21.4 블록 bootstrap 그대로다:
        // 관 BLUE · 뇌 PINK · 거품 PURPLE · 불 RED · 뿔 YELLOW 이고, 죽은 변형 열다섯은
        // 색과 무관하게 전부 GRAY 다(석회화한 골격). 세 형상 모두 같은 색 순서라 한 표를
        // 세 번 돈다 — 색 순서가 어긋나면 지도에서 산호초가 엉뚱한 색 얼룩이 된다.
        int[] coralColors = {BLUE, PINK, PURPLE, RED, YELLOW};
        int[] coralLiveBases = {Blocks.TUBE_CORAL_BLOCK, Blocks.TUBE_CORAL, Blocks.TUBE_CORAL_FAN,
                // 벽 부착 부채 10종(1910~1919)도 같은 색 순서다 — 공식 26.3 bootstrap 에서
                // tube_coral_wall_fan 25 · brain 20 · bubble 24 · fire 28 · horn 18 이고
                // 죽은 다섯은 전부 21(GRAY) 이다.
                Blocks.TUBE_CORAL_WALL_FAN};
        for (int base : coralLiveBases) {
            for (int species = 0; species < Blocks.CORAL_SPECIES_COUNT; species++) {
                BLOCK_MAP_COLORS[base + species] = (byte) coralColors[species];
                BLOCK_MAP_COLORS[base + species + Blocks.CORAL_DEAD_OFFSET] = (byte) GRAY;
            }
        }

        // [TURTLE] 거북 알의 지도색은 [A] 1.21.4 {@code Blocks.TURTLE_EGG} 의
        // {@code mapColor(MapColor.SAND)} 그대로다 — 모래 위에 놓이는 옅은 껍질이라
        // 바닐라도 모래와 같은 색으로 찍는다. client StandaloneMaps 의 같은 줄과 같아야 한다.
        assign(SAND, Blocks.TURTLE_EGG);

        // [CONDUIT] 콘딧의 지도색은 [A] 1.21.4 {@code ConduitBlock} 의
        // {@code Properties.of().mapColor(MapColor.DIAMOND)} 그대로다 — 프리즈머린 벽돌 ·
        // 다크 프리즈머린과 같은 DIAMOND 계열이라 새 색을 만들지 않는다.
        // client StandaloneMaps 의 같은 줄과 문자 그대로 같아야 한다.
        assign(DIAMOND, Blocks.CONDUIT);

        // [ARCHAEOLOGY] 의심 블록 둘은 [A] 자기 원본 블록의 map color 를 그대로 쓴다 —
        // {@code SUSPICIOUS_SAND} 는 {@code mapColor(MapColor.SAND)}, {@code SUSPICIOUS_GRAVEL}
        // 은 자갈과 같은 {@code MapColor.STONE} 이다. 붓질로 원본이 되는 블록이라 지도에서
        // 원본과 구분되지 않는 것이 바닐라 그대로다.
        assign(SAND, Blocks.SUSPICIOUS_SAND);
        assign(STONE, Blocks.SUSPICIOUS_GRAVEL);
        // [ARCHAEOLOGY] 장식 항아리 8 종은 무늬만 다르고 재질이 같은 점토 벽돌판이라 [A]
        // {@code DecoratedPotBlock} 의 {@code mapColor(MapColor.TERRACOTTA_RED)} 하나를
        // 구간 전체가 나눠 쓴다 — 무늬별 색을 만들면 두 권위의 표가 갈린다.
        assignRange(TERRACOTTA_RED, Blocks.DECORATED_POT, Blocks.SNORT_DECORATED_POT);

        assignRange(DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILE_WALL);
        int[] woodSpecies = {SAND, PODZOL, DIRT, ORANGE, BROWN, TERRACOTTA_WHITE, RED};
        for (int id = Blocks.BIRCH_PLANK; id <= Blocks.MANGROVE_DOOR; id++) {
            BLOCK_MAP_COLORS[id] = (byte) woodSpecies[(id - Blocks.BIRCH_PLANK) % woodSpecies.length];
        }
        int[] strippedSpecies = {WOOD, SAND, PODZOL, DIRT, ORANGE, BROWN,
                TERRACOTTA_WHITE, RED};
        for (int id = Blocks.STRIPPED_OAK_LOG; id <= Blocks.STRIPPED_MANGROVE_LOG_Z; id++) {
            BLOCK_MAP_COLORS[id] = (byte) strippedSpecies[
                    Math.floorDiv(id - Blocks.STRIPPED_OAK_LOG, 3)];
        }

        assign(DIRT, Blocks.GRANITE_STAIRS, Blocks.GRANITE_SLAB, Blocks.GRANITE_WALL,
                Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE_STAIRS,
                Blocks.POLISHED_GRANITE_SLAB);
        assign(QUARTZ, Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB, Blocks.DIORITE_WALL,
                Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE_STAIRS,
                Blocks.POLISHED_DIORITE_SLAB);
        assign(STONE, Blocks.ANDESITE_STAIRS, Blocks.STONE_STAIRS,
                Blocks.MOSSY_COBBLE_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
                Blocks.ANDESITE_SLAB, Blocks.STONE_SLAB, Blocks.MOSSY_COBBLE_SLAB,
                Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.ANDESITE_WALL,
                Blocks.MOSSY_STONE_BRICK_WALL, Blocks.POLISHED_ANDESITE,
                Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
                Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB,
                Blocks.SMOOTH_STONE_SLAB);
        assign(SAND, Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL,
                Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS,
                Blocks.SMOOTH_SANDSTONE_SLAB);
        assign(ORANGE, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB,
                Blocks.RED_SANDSTONE_WALL, Blocks.CHISELED_RED_SANDSTONE,
                Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
                Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.CUT_RED_SANDSTONE_SLAB,
                Blocks.SMOOTH_RED_SANDSTONE_SLAB);
        assign(RED, Blocks.BRICKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICK_WALL);
        assign(DIRT, Blocks.PACKED_MUD);
        assign(TERRACOTTA_LIGHT_GRAY, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS,
                Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL);
        assign(TERRACOTTA_GRAY, Blocks.CHISELED_TUFF, Blocks.POLISHED_TUFF,
                Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF_BRICKS, Blocks.TUFF_STAIRS,
                Blocks.POLISHED_TUFF_STAIRS, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_SLAB,
                Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_WALL,
                Blocks.POLISHED_TUFF_WALL, Blocks.TUFF_BRICK_WALL);
        assign(STONE, Blocks.BLAST_FURNACE_LIT, Blocks.SMOKER_LIT);

        assignDyes(Blocks.WHITE_WOOL);
        assignDyes(Blocks.WHITE_CARPET);
        // [WOOL-SLAB] 양털 반 블록 16색도 같은 염료 색 표를 따른다(재질이 양털 그대로다).
        assignDyes(Blocks.WHITE_WOOL_SLAB);
        // [SHELF-FUNGUS-WOOL-SLAB] 선반 여덟 수종은 벗긴 원목과 같은 WOOD 하나로 받고
        // (바닐라는 수종마다 다르지만 이 저장소의 지도 표는 재질 단위다 — [C] 축소),
        // 선반버섯은 바닐라 map color 그대로 PLANT 다 [B].
        for (int id = Blocks.OAK_SHELF; id <= Blocks.MANGROVE_SHELF; id++) assign(WOOD, id);
        assign(PLANT, Blocks.SHELF_MUSHROOM);
        assign(WOOD, Blocks.POPLAR_SHELF, Blocks.PALE_OAK_SHELF);
        assignDyes(Blocks.WHITE_BED);
        assignDyes(Blocks.WHITE_BANNER);
        // [FURNITURE-26.3] 쿠션 16색(1282~1297)은 저장소 전용 블록이라 가장 가까운 바닐라
        // 재질인 양털을 따른다 — 공식 26.3 bootstrap 의 white_wool 8 … black_wool 29 가
        // 이 염료 색 표와 그대로 같다. 이 줄이 빠져 있어 지도 표에 16칸 구멍이 남아 있었다.
        assignDyes(Blocks.WHITE_CUSHION);
        // 건초 침대는 색이 아니라 재질이 건초라 건초 더미와 같은 YELLOW 다.
        assign(YELLOW, Blocks.STRAW_BED);
        assign(YELLOW, Blocks.WET_SPONGE, Blocks.SPONGE);
        assign(CYAN, Blocks.PRISMARINE, Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB,
                Blocks.PRISMARINE_WALL);
        assign(DIAMOND, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                Blocks.PRISMARINE_BRICK_STAIRS, Blocks.DARK_PRISMARINE_STAIRS,
                Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB);
        assignRange(QUARTZ, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICK_WALL);

        int[] copperStages = {ORANGE, TERRACOTTA_LIGHT_GRAY, WARPED_STEM, WARPED_NYLIUM};
        for (int id = Blocks.CUT_COPPER; id <= Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR; id++) {
            BLOCK_MAP_COLORS[id] = (byte) copperStages[(id - Blocks.CUT_COPPER) % 4];
        }
        // [OPENABLE-METAL] 구리 창살 8종도 같은 단계별 색을 쓴다. ID 구간이 위와 떨어져
        // 있을 뿐 산화 단계 = 오프셋 % 4 라는 규약은 그대로다.
        for (int id = Blocks.COPPER_BARS; id <= Blocks.WAXED_OXIDIZED_COPPER_BARS; id++) {
            BLOCK_MAP_COLORS[id] = (byte) copperStages[(id - Blocks.COPPER_BARS) % 4];
        }
        // [COPPER-CHAIN] 구리 사슬 24종도 같은 단계별 색이다. 배치가 축 3 × 산화 4 라
        // 축이 바뀌어도 오프셋 % 4 가 그대로 산화 단계를 가리킨다(각 축이 4연속이기 때문이다).
        for (int id = Blocks.COPPER_CHAIN; id <= Blocks.WAXED_OXIDIZED_COPPER_CHAIN_Z; id++) {
            BLOCK_MAP_COLORS[id] = (byte) copperStages[(id - Blocks.COPPER_CHAIN) % 4];
        }
        // [CHEST-FAMILY] 구리 상자 8종(1811~1818)도 같은 단계별 색이다 — 산화 4 뒤에 밀랍 4 가
        // 붙는 배치라 오프셋 % 4 가 그대로 산화 단계를 가리킨다(위 세 계열과 같은 규약).
        for (int id = Blocks.COPPER_CHEST; id <= Blocks.WAXED_OXIDIZED_COPPER_CHEST; id++) {
            BLOCK_MAP_COLORS[id] = (byte) copperStages[(id - Blocks.COPPER_CHEST) % 4];
        }
        assignRange(CLEAR, Blocks.COPPER_TORCH, Blocks.COPPER_WALL_TORCH_W);
        assignCopperAgeFamily(Blocks.EXPOSED_COPPER_LANTERN, false, copperStages);
        assignCopperAgeFamily(Blocks.EXPOSED_LIGHTNING_ROD, false, copperStages);
        assignCopperAgeFamily(Blocks.COPPER_GOLEM_STATUE, true, copperStages);
        assign(NETHER, Blocks.NETHERRACK);
        assign(BLACK, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK);
        assign(WATER, Blocks.FROGSPAWN);
        assign(RED, Blocks.SNIFFER_EGG);
        assign(PLANT, Blocks.TORCHFLOWER_CROP, Blocks.TORCHFLOWER, Blocks.PITCHER_CROP);
        // [PITCHER] 핀 26.3 Blocks.PITCHER_PLANT mapColor PLANT.
        assign(PLANT, Blocks.PITCHER_PLANT);
        assign(YELLOW, Blocks.OCHRE_FROGLIGHT, Blocks.BEE_NEST);
        assign(GREEN, Blocks.VERDANT_FROGLIGHT);
        assign(PINK, Blocks.PEARLESCENT_FROGLIGHT);
        assign(WOOD, Blocks.BEEHIVE);
        assign(ORANGE, Blocks.HONEY_BLOCK, Blocks.HONEYCOMB_BLOCK);
        // [ENDER-SHULKER] 엔더 상자는 바닐라 mapColor(STONE) 이다 — 재질이 흑요석이지만
        // 지도색만은 돌 계열로 잡혀 있다([B] EnderChestBlock properties).
        assign(STONE, Blocks.ENDER_CHEST);
        // 셜커 상자 17종은 바닐라 지도색이 그 상자의 **염료 색**이다. 무염색만 보라색
        // (DyeColor.PURPLE)이고 염색 16색은 각자 자기 색이라, 색 표를 그대로 쓰는
        // assignDyes 한 줄이면 16색이 정확히 덮인다(콘크리트·양털과 같은 규약).
        assign(DYE_MAP_COLORS[10], Blocks.SHULKER_BOX);
        assignDyes(Blocks.WHITE_SHULKER_BOX);
        // 철 문·철 다락문은 바닐라 지도색이 METAL 이다(철 블록과 같은 재질 색).
        assign(METAL, Blocks.IRON_DOOR, Blocks.IRON_TRAPDOOR);
        // 철 랜턴은 METAL, 바닐라 사슬은 NONE이다. 프로젝트 수평 사슬도 같은 재질 정체성을 쓴다.
        assign(METAL, Blocks.LANTERN);
        assign(CLEAR, Blocks.CHAIN, Blocks.CHAIN_X, Blocks.CHAIN_Z);
        assignDyes(Blocks.WHITE_CONCRETE);
        assignDyes(Blocks.WHITE_CONCRETE_POWDER);
        assign(TERRACOTTA_MAGENTA, Blocks.MAGENTA_TERRACOTTA);
        assign(TERRACOTTA_LIGHT_BLUE, Blocks.LIGHT_BLUE_TERRACOTTA);
        assign(TERRACOTTA_LIGHT_GREEN, Blocks.LIME_TERRACOTTA);
        assign(TERRACOTTA_PINK, Blocks.PINK_TERRACOTTA);
        assign(TERRACOTTA_GRAY, Blocks.GRAY_TERRACOTTA);
        assign(TERRACOTTA_CYAN, Blocks.CYAN_TERRACOTTA);
        assign(TERRACOTTA_PURPLE, Blocks.PURPLE_TERRACOTTA);
        assign(TERRACOTTA_BLUE, Blocks.BLUE_TERRACOTTA);
        assign(TERRACOTTA_GREEN, Blocks.GREEN_TERRACOTTA);
        assign(TERRACOTTA_BLACK, Blocks.BLACK_TERRACOTTA);
        assignDyes(Blocks.WHITE_GLAZED_TERRACOTTA);
        assignDyes(Blocks.WHITE_STAINED_GLASS);
        assignRange(CLEAR, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS_PANE);

        // [MAPCOLOR-263] 26.3 웨이브들이 map color 없이 착지한 블록들이다. 값은 전부 pinned
        // 26.3-snapshot-7 inner jar(SHA-1 2f1ef79f…)를 Bootstrap 으로 띄워 각 블록
        // defaultBlockState 의 MapColor.id 를 읽은 원문이다 — 기억이나 추정이 아니다.
        // StandaloneMaps.ts 의 같은 절과 값이 문자 그대로 같아야 한다.
        assign(CLAY, Blocks.INFESTED_STONE, Blocks.INFESTED_STONE_BRICKS,
                Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS);
        assign(DEEPSLATE, Blocks.INFESTED_DEEPSLATE);
        assign(WATER, Blocks.TALL_SEAGRASS, Blocks.KELP_PLANT);
        assign(PLANT, Blocks.BLUE_ORCHID, Blocks.LARGE_FERN, Blocks.PINK_PETALS,
                Blocks.SUNFLOWER, Blocks.TALL_GRASS_263, Blocks.LILAC, Blocks.ROSE_BUSH,
                Blocks.PEONY, Blocks.LILY_OF_THE_VALLEY, Blocks.COCOA,
                Blocks.BIG_DRIPLEAF_STEM, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES,
                Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
                Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
                Blocks.MELON_STEM);
        // 감긴 눈꽃은 바닐라가 METAL(6) 이다 — 회백색 상태라 식생 색을 쓰지 않는다.
        assign(METAL, Blocks.CLOSED_EYEBLOSSOM);
        assign(ORANGE, Blocks.OPEN_EYEBLOSSOM);
        assign(LIGHT_GREEN, Blocks.MELON);
        assign(STONE, Blocks.DISPENSER, Blocks.STICKY_PISTON, Blocks.WATER_CAULDRON,
                Blocks.STONE_PRESSURE_PLATE, Blocks.HOPPER, Blocks.DROPPER, Blocks.CRAFTER);
        // 레드스톤 배선·레버·화분·버튼·장벽은 바닐라 map color 가 전부 NONE 이라 지도에서
        // 아래 블록이 그대로 비친다.
        assign(CLEAR, Blocks.TRIPWIRE_HOOK_BLOCK, Blocks.TRIPWIRE, Blocks.LEVER,
                Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.REDSTONE_TORCH,
                Blocks.POTTED_CACTUS, Blocks.POTTED_RED_MUSHROOM, Blocks.STONE_BUTTON,
                Blocks.FLOWER_POT, Blocks.BARRIER, Blocks.POTTED_DEAD_BUSH,
                Blocks.OAK_BUTTON, Blocks.POTTED_RED_TULIP);
        assign(WOOD, Blocks.OAK_WALL_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.WHITE_WALL_BANNER,
                Blocks.BROWN_WALL_BANNER, Blocks.OAK_SLAB, Blocks.OAK_PRESSURE_PLATE,
                Blocks.STRIPPED_OAK_WOOD);
        assign(PODZOL, Blocks.SPRUCE_HANGING_SIGN, Blocks.STRIPPED_SPRUCE_WOOD);
        assign(GREEN, Blocks.END_PORTAL_FRAME);
        assign(BLACK, Blocks.END_PORTAL, Blocks.CRYING_OBSIDIAN, Blocks.END_GATEWAY);
        // 무염색 양초는 SAND(2), 염색 양초는 자기 염료 색이다.
        assign(SAND, Blocks.CANDLE);
        assign(RED, Blocks.RED_CANDLE, Blocks.MANGROVE_WOOD);
        assign(GREEN, Blocks.GREEN_CANDLE);
        assign(PURPLE, Blocks.PURPLE_CANDLE);
        assign(BROWN, Blocks.BROWN_CANDLE);
        assign(YELLOW, Blocks.BAMBOO_FENCE);
        // 양털 계단은 흰 양털과 같은 SNOW(8) 다.
        for (int color = 0; color < 16; color++) {
            assign(DYE_MAP_COLORS[color], Blocks.WOOL_STAIRS_BY_DYE_COLOR[color],
                    Blocks.WHITE_CONCRETE_STAIRS + color, Blocks.WHITE_CONCRETE_SLAB + color);
        }
        // 아카시아 목재는 껍질색 기준이라 바닐라가 GRAY(21) 다.
        assign(GRAY, Blocks.ACACIA_WOOD);
        assign(GOLD, Blocks.BELL);
        // [VOID-END] 핀 26.3 Blocks: 엔드 돌·벽돌 계열 MapColor.SAND, 보라 계열 COLOR_MAGENTA,
        // 후렴 식물·꽃 COLOR_PURPLE, 엔드 막대는 mapColor 가 없어 NONE 이다.
        assign(SAND, Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICK_STAIRS,
                Blocks.END_STONE_BRICK_SLAB, Blocks.END_STONE_BRICK_WALL);
        assign(MAGENTA, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_STAIRS, Blocks.PURPUR_SLAB);
        assign(PURPLE, Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER);
        assign(CLEAR, Blocks.END_ROD);
        // [TRIAL-GAP] 핀 26.3 Blocks: emerald_block MapColor.EMERALD, heavy_core MapColor.METAL.
        assign(EMERALD, Blocks.EMERALD_BLOCK);
        // [UTILITY] 핀 26.3 Blocks: beacon MapColor.DIAMOND · jukebox DIRT · slime_block GRASS.
        assign(DIAMOND, Blocks.BEACON);
        assign(DIRT, Blocks.JUKEBOX);
        assign(GRASS, Blocks.SLIME_BLOCK);
        // [UTILITY] 흑암석 계열 MapColor.COLOR_BLACK.
        assign(BLACK, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS);
        assign(METAL, Blocks.HEAVY_CORE);
        // [DRAGON] 핀 26.3 Blocks: dragon_egg MapColor.COLOR_BLACK.
        assign(BLACK, Blocks.DRAGON_EGG);
        // [END-CITY] 핀 26.3 Blocks: magenta_wall_banner 는 MapColor.WOOD(흰색·갈색 벽 현수막과 같다).
        // dragon_head 는 mapColor 를 지정하지 않아 NONE 이라 지도에 칠하지 않는다.
        assign(WOOD, Blocks.MAGENTA_WALL_BANNER);
        assign(CLEAR, Blocks.DRAGON_HEAD);
        // 핀 26.3 Blocks(inner jar SHA-1 2f1ef79f… defaultBlockState MapColor): lodestone METAL.
        assign(METAL, Blocks.LODESTONE);
        // 살점 지옥 블록은 바닐라 짝이 없는 프로젝트 원본이다. 원본 16px 텍스처(기본 상태) 평균색에
        // CIELAB 거리로 가장 가까운 바닐라 MapColor 를 고정한다(StandaloneMaps.ts 와 같은 값).
        assign(BLACK, Blocks.ABYSS_STONE);
        assign(CRIMSON_HYPHAE, Blocks.FLESH_HEART);
        assign(WARPED_HYPHAE, Blocks.HEART_CORE, Blocks.FLESH_COCOON);
        assign(TERRACOTTA_PURPLE, Blocks.FLESH_BLOCK);
        // [REDSTONE] Pinned 26.3 Blocks properties: absent mapColor means NONE; pistonProperties is STONE.
        assign(CLEAR, Blocks.ACTIVATOR_RAIL, Blocks.DETECTOR_RAIL, Blocks.POWERED_RAIL,
                Blocks.REDSTONE_TORCH_OFF, Blocks.REDSTONE_WALL_TORCH, Blocks.REDSTONE_WALL_TORCH_OFF, Blocks.COMPARATOR);
        assign(FIRE, Blocks.REDSTONE_BLOCK);
        assign(STONE, Blocks.OBSERVER, Blocks.PISTON, Blocks.PISTON_HEAD, Blocks.MOVING_PISTON);
        assign(TERRACOTTA_ORANGE, Blocks.REDSTONE_LAMP, Blocks.REDSTONE_LAMP_LIT);
        assign(QUARTZ, Blocks.TARGET);
        assign(WOOD, Blocks.DAYLIGHT_DETECTOR, Blocks.NOTE_BLOCK);
        // [FLESH] Project-only additions reuse explicit existing material families:
        // artery/anchor -> heart, membrane -> flesh, large cocoon -> cocoon;
        // pale fat/bone -> bone-block SAND, dark necrosis -> abyss BLACK. No implicit fallback.
        assign(CRIMSON_HYPHAE, Blocks.FLESH_ARTERY, Blocks.FLESH_ANCHOR, Blocks.FLESH_RELIQUARY, Blocks.FLESH_PULSE_LAMP);
        assign(TERRACOTTA_PURPLE, Blocks.FLESH_MEMBRANE_BLOCK);
        assign(WARPED_HYPHAE, Blocks.FLESH_LARGE_COCOON);
        assign(SAND, Blocks.FLESH_FAT_SAC, Blocks.FLESH_BONE_SPUR, Blocks.FLESH_FAT_LAMP);
        assign(BLACK, Blocks.FLESH_NECROSIS);

        // [FROST-SOUL] 핀 26.3 Blocks: 살얼음 MapColor.ICE, 영혼 모래·영혼 흙 COLOR_BROWN.
        assign(ICE, Blocks.FROSTED_ICE);
        assign(BROWN, Blocks.SOUL_SAND, Blocks.SOUL_SOIL);
    }

    private static void assignCopperAgeFamily(int firstId, boolean includesBase, int[] stages) {
        int firstStage = includesBase ? 0 : 1;
        for (int offset = 0; offset < 8 - (includesBase ? 0 : 1); offset++) {
            int stage = offset < 4 - firstStage ? firstStage + offset : offset - (4 - firstStage);
            BLOCK_MAP_COLORS[firstId + offset] = (byte) stages[stage];
        }
    }

    interface ColumnView {
        boolean isResident(int worldX, int worldZ);

        /** {@link #isResident}가 참인 컬럼의 블록 ID. */
        int blockAt(int worldX, int worldY, int worldZ);
    }

    record Surface(boolean available, int height, int baseColor, int waterDepth) {
        private static Surface unavailable() {
            return new Surface(false, Blocks.MIN_Y - 1, CLEAR, 0);
        }
    }

    private WorldMapColorSampler() {
    }

    /** 생성 지도에서 상주 중인 플레이어 주변 정사각형을 즉시 권위 색으로 채웁니다. */
    static WorldMapData initialize(WorldMapData draft, double playerX, double playerZ,
            int radius, ColumnView world) {
        if (radius < 0 || radius >= WorldMapData.SIZE) {
            throw new IllegalArgumentException("initial map sample radius is invalid");
        }
        byte[] colors = draft.getColors();
        int playerPixelX = pixel(draft.getCenterX(), playerX);
        int playerPixelZ = pixel(draft.getCenterZ(), playerZ);
        int minX = Math.max(0, playerPixelX - radius);
        int maxX = Math.min(WorldMapData.SIZE - 1, playerPixelX + radius);
        int minZ = Math.max(0, playerPixelZ - radius);
        int maxZ = Math.min(WorldMapData.SIZE - 1, playerPixelZ + radius);
        for (int pixelX = minX; pixelX <= maxX; pixelX++) {
            byte[] column = sampleColumn(draft, colors, pixelX, minZ, maxZ, world);
            for (int row = 0; row < column.length; row++) {
                colors[(minZ + row) * WorldMapData.SIZE + pixelX] = column[row];
            }
        }
        return new WorldMapData(draft.getWorldId(), draft.getMapId(),
                draft.getCenterX(), draft.getCenterZ(), draft.getScale(), draft.isLocked(),
                colors, 0, draft.getTargetMarker());
    }

    /** 비상주 행은 기존 색을 보존하고 상주 행만 새 권위 색으로 바꾼 한 픽셀 폭 패치입니다. */
    static byte[] sampleColumn(WorldMapData map, byte[] currentColors,
            int pixelX, int minZ, int maxZ, ColumnView world) {
        if (currentColors == null || currentColors.length != WorldMapData.COLOR_COUNT
                || pixelX < 0 || pixelX >= WorldMapData.SIZE
                || minZ < 0 || maxZ < minZ || maxZ >= WorldMapData.SIZE) {
            throw new IllegalArgumentException("map sample column is invalid");
        }
        int worldX = map.getCenterX() - WorldMapData.SIZE / 2 + pixelX;
        byte[] colors = new byte[maxZ - minZ + 1];
        for (int pixelZ = minZ; pixelZ <= maxZ; pixelZ++) {
            int worldZ = map.getCenterZ() - WorldMapData.SIZE / 2 + pixelZ;
            Surface surface = sample(world, worldX, worldZ);
            // Vanilla traverses rows along X: the brightness predecessor is the west column at
            // the same Z, never the prior Z entry of this wire-level vertical patch.
            Surface previous = sample(world, worldX - 1, worldZ);
            int output = pixelZ - minZ;
            colors[output] = surface.available()
                    ? encode(surface, previous, worldX, worldZ)
                    : currentColors[pixelZ * WorldMapData.SIZE + pixelX];
        }
        return colors;
    }

    static int pixel(int center, double coordinate) {
        return (int) Math.floor(coordinate) - center + WorldMapData.SIZE / 2;
    }

    /** 유리처럼 map color가 CLEAR인 블록은 통과해 그 아래 첫 유효 표면을 찾습니다. */
    static Surface sample(ColumnView world, int worldX, int worldZ) {
        if (!world.isResident(worldX, worldZ)) return Surface.unavailable();
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            int block = world.blockAt(worldX, y, worldZ);
            if (block < 0) return Surface.unavailable();
            int color = baseColor(block);
            if (color == WATER) {
                int depth = 1;
                while (y - depth >= Blocks.MIN_Y) {
                    int below = world.blockAt(worldX, y - depth, worldZ);
                    if (below < 0 || baseColor(below) != WATER) break;
                    depth++;
                }
                return new Surface(true, y, WATER, depth);
            }
            if (color != CLEAR) return new Surface(true, y, color, 0);
        }
        return Surface.unavailable();
    }

    /** 바닐라 scale-0 높이 기울기·물 깊이 임계값을 하위 2비트 brightness로 인코딩합니다. */
    static byte encode(Surface current, Surface previous, int worldX, int worldZ) {
        if (!current.available() || current.baseColor() == CLEAR) return 0;
        int shade;
        if (current.baseColor() == WATER) {
            double depthShade = current.waterDepth() * 0.1
                    + (((worldX + worldZ) & 1) == 0 ? 0.0 : 0.2);
            shade = depthShade < 0.5 ? 2 : depthShade > 0.9 ? 0 : 1;
        } else {
            int previousHeight = previous.available() ? previous.height() : current.height();
            double slope = (current.height() - previousHeight) * 4.0 / 5.0
                    + ((((worldX + worldZ) & 1) - 0.5) * 0.4);
            shade = slope > 0.6 ? 2 : slope < -0.6 ? 0 : 1;
        }
        return (byte) ((current.baseColor() << 2) | shade);
    }

    /** 등록 블록의 pinned default-state MapColor. 미지정 ID에는 임의 fallback을 만들지 않습니다. */
    static int baseColor(int block) {
        if (block < 0 || block >= BLOCK_MAP_COLORS.length || BLOCK_MAP_COLORS[block] < 0) {
            throw new IllegalArgumentException("block " + block + " has no MapColor identity");
        }
        return BLOCK_MAP_COLORS[block];
    }

    static int registryFingerprint() {
        int hash = 0x811c9dc5;
        for (int block : Blocks.worldBlockIds()) {
            hash = (hash ^ block) * 0x01000193;
            hash = (hash ^ baseColor(block)) * 0x01000193;
        }
        return hash;
    }

    private static void assign(int color, int... blocks) {
        for (int block : blocks) {
            BLOCK_MAP_COLORS[block] = (byte) color;
        }
    }

    private static void assignRange(int color, int first, int last) {
        Arrays.fill(BLOCK_MAP_COLORS, first, last + 1, (byte) color);
    }

    private static void assignDyes(int first) {
        for (int index = 0; index < DYE_MAP_COLORS.length; index++) {
            BLOCK_MAP_COLORS[first + index] = (byte) DYE_MAP_COLORS[index];
        }
    }
}
