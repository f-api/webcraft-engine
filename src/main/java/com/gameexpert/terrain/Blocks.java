package com.gameexpert.terrain;

/**
 * WebCraft 블록 ID 상수 — 공유 계약(docs/CONTRACT.md) §2 정본.
 * TS 측 client/src/world/blocks.ts 와 문자 그대로 동일해야 한다.
 */
public final class Blocks {
    private Blocks() {}

    /**
     * 블록이 월드에 남기 위해 필요한 지지 형태.
     * {@link SupportKind#UNSPECIFIED}는 미등록 ID, {@link SupportKind#NONE}은 등록됐지만 지지가 필요 없는 블록이다.
     */
    public enum SupportKind {
        UNSPECIFIED,
        NONE,
        DIRT_OR_GRASS_BELOW,
        SOLID_BELOW,
        FARMLAND_BELOW,
        SAND_AND_WATER,
        CACTUS_COLUMN,
        WATER_BELOW,
        WALL_ANY,
        WALL_NORTH,
        WALL_EAST,
        WALL_SOUTH,
        WALL_WEST
    }

    /** 블록 정의와 함께 보관하는 지지 조건 및 지지 상실 드랍. */
    public static final class SupportMetadata {
        private final SupportKind supportKind;
        private final int supportLossDrop;

        private SupportMetadata(SupportKind supportKind, int supportLossDrop) {
            this.supportKind = supportKind;
            this.supportLossDrop = supportLossDrop;
        }

        public SupportKind supportKind() {
            return supportKind;
        }

        public int supportLossDrop() {
            return supportLossDrop;
        }
    }

    public static final int AIR = 0;
    public static final int STONE = 1;
    public static final int DIRT = 2;
    public static final int GRASS = 3;
    public static final int SAND = 4;
    public static final int LOG = 5;
    public static final int LEAVES = 6;
    public static final int PLANK = 7;
    public static final int COBBLE = 8;
    public static final int BEDROCK = 9;
    public static final int GLASS = 10;

    // 지형 v2 신규 블록(§2). 11~13 비고체·즉시파괴·무드랍 장식, 14/15 자작나무, 16 자갈.
    public static final int TALL_GRASS = 11;
    public static final int FLOWER_RED = 12;
    public static final int FLOWER_YELLOW = 13;
    public static final int BIRCH_LOG = 14;
    public static final int BIRCH_LEAVES = 15;
    public static final int GRAVEL = 16;
    // 17 TORCH: 비고체·즉시파괴·드랍 자신·설치 전용(지형 생성 미포함).
    public static final int TORCH = 17;

    // 지형 v3 배치A 신규 블록(§2).
    // 18 SUGARCANE: cross·비고체·즉시파괴·드랍 자신(물가 dirt/sand 계열 위 스폰).
    // 19 CACTUS: 고체·드랍 자신(sand/red_sand 위 스폰).
    public static final int SUGARCANE = 18;
    public static final int CACTUS = 19;

    public static final int COAL_ORE = 20;
    public static final int IRON_ORE = 21;
    public static final int GOLD_ORE = 22;
    public static final int DIAMOND_ORE = 23;

    // 24/25 버섯(§2 v3): cross·비고체·즉시파괴·드랍 자신(동굴 공동 바닥·큰나무 근처 스폰). 갈색/빨강.
    public static final int MUSHROOM_BROWN = 24;
    public static final int MUSHROOM_RED = 25;

    // 건축 B1 신규 블록(§2 26~31): 전부 플레이어 설치 전용 — 지형 생성 파이프라인 미포함(golden 무영향).
    // 26 STONE_BRICK: 고체·드랍 자신. 27/28 slab은 state 0=bottom, 1=top, 2=double이며
    // 서버 충돌도 실제 반 높이를 쓴다. 29 DOOR는 재질 ID 하나이고 개폐·방향·상하·경첩은 state다.
    // 30은 append-only 계약상 예약된 미사용 ID다. 31 LADDER: 비고체(통과·등반)·드랍 자신.
    public static final int STONE_BRICK = 26;
    public static final int PLANK_SLAB = 27;
    public static final int COBBLE_SLAB = 28;
    public static final int DOOR_CLOSED = 29;
    public static final int LADDER = 31;
    // 32 BED: 고체·드랍 자신·바닥 지지 필요. 지형 생성 파이프라인 미포함.
    public static final int BED = 32;

    // 생존 S2b 신규 블록(§2 33): FURNACE. 고체(Fluids 기본 규칙)·드랍 자신·플레이어 설치 전용
    // (지형 파이프라인 미포함 → golden 무영향). 제련 레시피(requiresFurnace)의 근접 판정 대상.
    // 32 BED 는 위 건축 블록 구간에 정의되며 지형 생성 파이프라인에는 포함하지 않는다.
    public static final int FURNACE = 33;

    // 생존 F2 신규 블록(§2 34~38): 제련 화로 점화 상태 + 광물 압축 블록. 전부 지형 파이프라인 미포함(golden 무영향).
    // 34 FURNACE_LIT: 고체·직접 설치 불가(제련 큐가 33↔34 스왑, 드랍 33)·서버 solid 기본 규칙. 시간제 제련
    //   진행 중인 화로의 렌더 상태 변형(정면 불꽃 애니). 제련 근접 판정에서 33 과 동일하게 화로로 취급.
    // 35~38 COAL/IRON/GOLD/DIAMOND_BLOCK: 고체·드랍 자신·설치 허용. 광물 9개 압축(양방향 레시피 §10.2-F2).
    public static final int FURNACE_LIT = 34;
    public static final int COAL_BLOCK = 35;
    public static final int IRON_BLOCK = 36;
    public static final int GOLD_BLOCK = 37;
    public static final int DIAMOND_BLOCK = 38;

    // 수면 장식: 수련잎. 블록 ID를 아이템 ID로 그대로 재사용하며 지형 생성기가 자연 배치한다.
    public static final int LILY_PAD = 39;

    // 벽토치(§2 개정 52~55): 토치 아이템(17) 설치 시 조준한 벽 면으로 방향을 확정해 저장하는
    // 렌더/방향 전용 블록. 전부 플레이어 설치 전용(지형 파이프라인 미포함 → golden 무영향)·비고체
    // (Fluids.isDecoration 통과)·드랍은 토치 아이템 17(InventoryRules.dropFor). 부착 벽 좌표 오프셋은
    // 52=(0,0,-1) · 53=(+1,0,0) · 54=(0,0,+1) · 55=(-1,0,0).
    public static final int WALL_TORCH_N = 52;
    public static final int WALL_TORCH_E = 53;
    public static final int WALL_TORCH_S = 54;
    public static final int WALL_TORCH_W = 55;

    // 물-용암 접촉 및 깊은 용암호 바닥에서 생성되는 고체 블록.
    public static final int OBSIDIAN = 56;

    // 수중 생태계 장식. 식생 블록 하나가 같은 셀의 물 렌더를 겸한다.
    public static final int KELP = 57;
    public static final int SEAGRASS = 58;
    public static final int CORAL = 59;

    public static final int WATER_SOURCE = 40;
    public static final int LAVA_SOURCE = 48;

    // 스포너: 60=좀비 / 61=거미 / 62=스켈레톤
    public static final int SPAWNER_BASE = 60;
    public static final int SEA_PICKLE = 63;

    // 한랭 지표. 57~59/63은 수중 생태계가 이미 사용하므로 다음 미사용 ID를 쓴다.
    public static final int SNOW = 64;
    public static final int ICE = 65;

    // 이끼 계열. 동굴 바닥과 던전 팔레트에 자연 생성되며 건축용 등록 블록으로도 쓴다.
    public static final int MOSS_BLOCK = 66;
    public static final int MOSSY_COBBLE = 67;
    public static final int MOSSY_STONE_BRICK = 68;

    // 69 상자, 70~73은 원목의 저장 축 변형이다. 자연 수평 가지는 축 변형을 직접 생성하고,
    // 기존 LOG/BIRCH_LOG는 수직 줄기와 인벤토리의 y축 정본으로 유지한다.
    public static final int CHEST = 69;
    public static final int LOG_X = 70;
    public static final int LOG_Z = 71;
    public static final int BIRCH_LOG_X = 72;
    public static final int BIRCH_LOG_Z = 73;
    /** 플레이어 설치 전용 작업대. 동결 지형 생성 파이프라인에는 포함하지 않는다. */
    public static final int CRAFTING_TABLE = 74;
    /** 라이터로 만든 흑요석 프레임 내부에만 생기는 비고체 포털. 직접 설치·드랍하지 않는다. */
    public static final int NETHER_PORTAL = 75;
    /** 플레이어 설치 전용 TNT와 점화 후 도화선이 타는 런타임 상태. 지형 생성에는 쓰지 않는다. */
    public static final int TNT = 76;
    public static final int PRIMED_TNT = 77;
    /** 농사 블록은 생성기에 들어가지 않는 런타임 블록이다. 상태: 경작지 0=마름,1=수분; 밀 0~7=성장. */
    public static final int FARMLAND = 78;
    public static final int WHEAT_CROP = 79;

    // 기본 건축 팔레트. 전부 플레이어/런타임 설치 전용이며 지형 생성기에서는 쓰지 않는다.
    public static final int SANDSTONE = 80;
    public static final int WOOD_STAIRS = 81;
    public static final int COBBLE_STAIRS = 82;
    public static final int STONE_BRICK_STAIRS = 83;
    public static final int SANDSTONE_STAIRS = 84;
    public static final int STONE_BRICK_SLAB = 85;
    public static final int SANDSTONE_SLAB = 86;
    public static final int COBBLE_WALL = 87;
    public static final int MOSSY_COBBLE_WALL = 88;
    public static final int STONE_BRICK_WALL = 89;
    public static final int WOOD_FENCE = 90;
    public static final int WOOD_FENCE_GATE = 91;
    public static final int GLASS_PANE = 92;
    public static final int IRON_BARS = 93;
    public static final int WOOD_TRAPDOOR = 94;
    public static final int RAIL = 95;
    public static final int CAMPFIRE = 96;
    // 사막 건축 팔레트. 전부 플레이어 설치 전용 고체 블록이며 지형 생성기에는 넣지 않는다.
    public static final int ORANGE_TERRACOTTA = 97;
    public static final int CUT_SANDSTONE = 98;
    public static final int CHISELED_SANDSTONE = 99;

    // 공유 프로토콜의 순수 아이템 ID. 묘목 133/134만 아이템 ID를 월드 블록 ID로도
    // 그대로 재사용하여 드랍↔설치 변환 테이블이 필요 없다. ID는 append-only 계약이다.
    public static final int FLINT = 282;
    public static final int COAL = 283;
    public static final int RAW_IRON = 284;
    public static final int RAW_GOLD = 285;
    public static final int DIAMOND = 286;
    public static final int OAK_SAPLING = 133;
    public static final int BIRCH_SAPLING = 134;
    public static final int APPLE = 287;

    // 전리품 전용 사슬 방어구 아이템. 월드 블록으로 등록하거나 제작식에 넣지 않는다.
    public static final int CHAINMAIL_HELMET = 335;
    public static final int CHAINMAIL_CHESTPLATE = 336;
    public static final int CHAINMAIL_LEGGINGS = 337;
    public static final int CHAINMAIL_BOOTS = 338;

    // ── p1-surface 187–194 ──
    public static final int CLAY = 187;
    public static final int CALCITE = 188;
    public static final int MUD = 189;
    public static final int PODZOL = 190;
    public static final int MYCELIUM = 191;
    public static final int DIRT_PATH = 192;
    public static final int RED_SAND = 193;
    public static final int ROOTED_DIRT = 194;

    // ── p2-plant 195–200 ──
    public static final int BAMBOO = 195;
    public static final int VINE = 196;
    public static final int FERN = 197;
    public static final int BUSH = 198;
    public static final int MOSS_CARPET = 199;
    public static final int MANGROVE_ROOTS = 200;

    // ── p3-lush 201–207 ──
    public static final int GLOW_LICHEN = 201;
    public static final int HANGING_ROOTS = 202;
    public static final int SPORE_BLOSSOM = 203;
    public static final int SMALL_DRIPLEAF = 204;
    public static final int BIG_DRIPLEAF = 205;
    public static final int AZALEA = 206;
    public static final int FLOWERING_AZALEA = 207;

    // ── p4-utility 208–213 ──
    public static final int COBWEB = 208;
    public static final int HAY_BLOCK = 209;
    public static final int PUMPKIN = 210;
    public static final int CARVED_PUMPKIN = 211;
    public static final int JACK_O_LANTERN = 212;
    public static final int BONE_BLOCK = 213;

    // ── p5-ice 214–216 ──
    public static final int PACKED_ICE = 214;
    public static final int BLUE_ICE = 215;
    public static final int NETHER_GOLD_ORE = 216;

    // ── 블록 팩 드랍·상호작용 순수 아이템 339–344 ──
    public static final int CLAY_BALL = 339;
    public static final int GLASS_BOTTLE = 340;
    public static final int WATER_BOTTLE = 341;
    public static final int GOLD_NUGGET = 342;
    public static final int PUMPKIN_SEEDS = 343;
    public static final int SHEARS = 344;

    // ── ores 런타임 등록 223–228 ──
    // 광석 3종은 이번 배치에서 자연 생성기에 넣지 않는다. 대응 비블록 드랍은 345–347이다.
    public static final int EMERALD_ORE = 223;
    public static final int LAPIS_ORE = 224;
    public static final int REDSTONE_ORE = 225;

    // ── cropengine 월드 블록 229–232 ──
    public static final int CARROT_CROP = 229;
    public static final int POTATO_CROP = 230;
    public static final int BEETROOT_CROP = 231;
    public static final int PUMPKIN_STEM = 232;
    // ── vanilla surface/material expansion 233–244 ──
    // Runtime/world block registrations only; frozen generateChunk() and its golden remain untouched.
    public static final int SNOW_BLOCK = 233;
    public static final int COARSE_DIRT = 234;
    public static final int POWDER_SNOW = 235;
    public static final int TERRACOTTA = 236;
    public static final int WHITE_TERRACOTTA = 237;
    public static final int RED_SANDSTONE = 238;
    public static final int DEEPSLATE = 239;
    public static final int YELLOW_TERRACOTTA = 240;
    public static final int BROWN_TERRACOTTA = 241;
    public static final int RED_TERRACOTTA = 242;
    public static final int LIGHT_GRAY_TERRACOTTA = 243;
    public static final int COBBLED_DEEPSLATE = 244;
    // ── vanilla wood expansion 245–254 ──
    // Runtime-overlay vegetation output only. Frozen generateChunk() never emits these IDs.
    public static final int SPRUCE_LOG = 245;
    public static final int SPRUCE_LEAVES = 246;
    public static final int JUNGLE_LOG = 247;
    public static final int JUNGLE_LEAVES = 248;
    public static final int ACACIA_LOG = 249;
    public static final int ACACIA_LEAVES = 250;
    public static final int DARK_OAK_LOG = 251;
    public static final int DARK_OAK_LEAVES = 252;
    public static final int CHERRY_LOG = 253;
    public static final int CHERRY_LEAVES = 254;

    // ── cropengine 순수 아이템 348–357 ──
    public static final int BEETROOT = 348;
    public static final int BEETROOT_SEEDS = 349;
    public static final int BOWL = 350;
    public static final int MUSHROOM_STEW = 351;
    public static final int BEETROOT_SOUP = 352;
    public static final int RABBIT_STEW = 353;
    public static final int RABBIT_RAW = 354;
    public static final int RABBIT_COOKED = 355;
    public static final int RABBIT_HIDE = 356;
    public static final int RABBIT_FOOT = 357;

    // 공유 ID는 append-only다. 255~357의 기존 예약/아이템 구간을 되메우거나 재번호하지 않고,
    // 새 월드 블록도 현재 high-water mark 다음 번호부터 추가한다.
    public static final int DEAD_BUSH = 358;

    // ── vanilla nature/geology expansion 359–391 ──
    public static final int GRANITE = 359;
    public static final int DIORITE = 360;
    public static final int ANDESITE = 361;
    public static final int TUFF = 362;
    public static final int COPPER_ORE = 363;
    public static final int DEEPSLATE_COAL_ORE = 364;
    public static final int DEEPSLATE_IRON_ORE = 365;
    public static final int DEEPSLATE_COPPER_ORE = 366;
    public static final int DEEPSLATE_GOLD_ORE = 367;
    public static final int DEEPSLATE_REDSTONE_ORE = 368;
    public static final int DEEPSLATE_EMERALD_ORE = 369;
    public static final int DEEPSLATE_LAPIS_ORE = 370;
    public static final int DEEPSLATE_DIAMOND_ORE = 371;
    public static final int RAW_IRON_BLOCK = 372;
    public static final int RAW_COPPER_BLOCK = 373;
    public static final int RAW_GOLD_BLOCK = 374;
    public static final int COPPER_BLOCK = 375;
    public static final int EXPOSED_COPPER = 376;
    public static final int WEATHERED_COPPER = 377;
    public static final int OXIDIZED_COPPER = 378;
    public static final int DRIPSTONE_BLOCK = 379;
    public static final int POINTED_DRIPSTONE = 380;
    public static final int CAVE_VINES = 381;
    public static final int CAVE_VINES_PLANT = 382;
    public static final int MANGROVE_LOG = 383;
    public static final int MANGROVE_LEAVES = 384;
    public static final int MANGROVE_PROPAGULE = 385;
    public static final int MUDDY_MANGROVE_ROOTS = 386;
    public static final int SPRUCE_SAPLING = 387;
    public static final int JUNGLE_SAPLING = 388;
    public static final int ACACIA_SAPLING = 389;
    public static final int DARK_OAK_SAPLING = 390;
    public static final int CHERRY_SAPLING = 391;

    // ── nature/geology pure items 392–396; later additions remain append-only ──
    public static final int RAW_COPPER = 392;
    public static final int COPPER_INGOT = 393;
    public static final int GLOW_BERRIES = 394;
    public static final int SNOWBALL = 395;
    public static final int POWDER_SNOW_BUCKET = 396;

    // ── vanilla mushroom/geode expansion 397–406 ──
    // Runtime-overlay 자연 feature만 생성한다. 기존 392–396 순수 아이템은 append-only라 건너뛴다.
    public static final int BROWN_MUSHROOM_BLOCK = 397;
    public static final int RED_MUSHROOM_BLOCK = 398;
    public static final int MUSHROOM_STEM = 399;
    public static final int SMOOTH_BASALT = 400;
    public static final int AMETHYST_BLOCK = 401;
    public static final int BUDDING_AMETHYST = 402;
    public static final int SMALL_AMETHYST_BUD = 403;
    public static final int MEDIUM_AMETHYST_BUD = 404;
    public static final int LARGE_AMETHYST_BUD = 405;
    public static final int AMETHYST_CLUSTER = 406;
    // 397–406 뒤에 추가된 순수 아이템. 이전 빈 번호를 backfill하지 않는다.
    public static final int AMETHYST_SHARD = 407;
    // ── animal expansion pure items 408–417 ──
    public static final int BAKED_POTATO = 408;
    public static final int GOLDEN_CARROT = 409;
    public static final int INK_SAC = 410;
    public static final int GLOW_INK_SAC = 411;
    public static final int COD_RAW = 412;
    public static final int COD_COOKED = 413;
    public static final int SALMON_RAW = 414;
    public static final int SALMON_COOKED = 415;
    public static final int COD_BUCKET = 416;
    public static final int SALMON_BUCKET = 417;
    public static final int TROPICAL_FISH_BUCKET = 418;
    // ── 적대 몹 드랍 순수 아이템 419–420 ──
    public static final int ENDER_PEARL = 419;
    public static final int SLIME_BALL = 420;
    // ── SURV-X 인챈트 확장 월드 블록 421–422 ──
    // 둘 다 제작 전용이며 지형 생성이 만들지 않는다(worldPresence.placementOnlyIds).
    public static final int ENCHANTING_TABLE = 421;
    public static final int BOOKSHELF = 422;
    // ── SURV-X 순수 아이템 423. 블록 구간 뒤에 append-only로 배정한다. ──
    public static final int BOOK = 423;
    // ── navigation pure items 430–433 ──
    // 424–429는 다른 트랙에 예약되어 있어 backfill하지 않는다.
    public static final int COMPASS = 430;
    public static final int CLOCK = 431;
    public static final int MAP = 432;
    public static final int PAPER = 433;
    // ── fishing pure items 440–444 ──
    // 434–439는 다른 트랙에 예약되어 있어 backfill하지 않는다.
    public static final int FISHING_ROD = 440;
    /** Raid captain consumable. Pure item; never valid in a world block array. */
    public static final int OMINOUS_BOTTLE = 445;
    // ── wildlife interaction pure items 446–451 ──
    public static final int ARMADILLO_SCUTE = 446;
    public static final int BRUSH = 447;
    public static final int WOLF_ARMOR = 448;
    public static final int SWEET_BERRIES = 449;
    public static final int TROPICAL_FISH = 450;
    public static final int PUFFERFISH = 451;
    // ── append-only dye items 452–467 (MC DyeColor network ID order) ──
    public static final int WHITE_DYE = 452;
    public static final int ORANGE_DYE = 453;
    public static final int MAGENTA_DYE = 454;
    public static final int LIGHT_BLUE_DYE = 455;
    public static final int YELLOW_DYE = 456;
    public static final int LIME_DYE = 457;
    public static final int PINK_DYE = 458;
    public static final int GRAY_DYE = 459;
    public static final int LIGHT_GRAY_DYE = 460;
    public static final int CYAN_DYE = 461;
    public static final int PURPLE_DYE = 462;
    public static final int BLUE_DYE = 463;
    public static final int BROWN_DYE = 464;
    public static final int GREEN_DYE = 465;
    public static final int RED_DYE = 466;
    public static final int BLACK_DYE = 467;
    public static final int PUFFERFISH_BUCKET = 468;
    // ── elytra/firework pure items 469–472 ──
    // 겉날개는 흉갑 부위 장비이고, 폭죽 로켓은 화약 개수(1~3)가 정하는 비행 지속 티어마다
    // 별도 ID를 쓴다(바닐라는 한 아이템 + fireworks 컴포넌트, WebCraft 는 컴포넌트가 없다).
    public static final int ELYTRA = 469;
    public static final int FIREWORK_ROCKET_1 = 470;
    public static final int FIREWORK_ROCKET_2 = 471;
    public static final int FIREWORK_ROCKET_3 = 472;
    // ── 레이드 승리 전리품 475–481. 473~474는 다른 트랙 예약 공백이라 되메우지 않는다. ──
    // 설치형 두 종은 placement-only 월드 블록이라 지형 생성기가 절대 만들지 않는다.
    /** 찢어진 군기. 방향(facing bits0..1)을 보존하는 전리품 블록이며 파괴하면 자신을 드랍한다. */
    public static final int TATTERED_BANNER = 475;
    /** 벚꽃 분재. 화분형 장식 블록이며 클라이언트가 주변 벚꽃잎 낙하 파티클을 파생한다. */
    public static final int CHERRY_BONSAI = 476;
    /** 기념 주화. 순수 수집품이며 전투·경제 효과가 전혀 없다. */
    public static final int COMMEMORATIVE_COIN = 477;
    /** 파성추의 뿔피리. 사용하면 뿔피리 소리만 나고 쿨다운이 스팸을 막는다. */
    public static final int BATTERING_HORN = 478;
    /** 꽃잎 주머니. 사용하면 벚꽃잎 파티클이 한 번 분출되고 한 개가 소모된다. */
    public static final int PETAL_POUCH = 479;
    /** 우민 오르골. 사용하면 우민 군가 멜로디만 재생된다. */
    public static final int ILLAGER_MUSIC_BOX = 480;
    /** 다이아 낚싯대. 기존 낚싯대의 상위 변형으로 내구만 512로 다르다. */
    public static final int DIAMOND_FISHING_ROD = 481;
    // ── 바닐라 낚시 전리품 아이템 484–488. 482~483은 다른 트랙 예약 공백이라 되메우지 않는다. ──
    // 근거는 MC Java 1.21.4 loot_tables/gameplay/fishing_junk.json · fishing_treasure.json 이다.
    // 나머지 junk·treasure 항목(수련잎·가죽 장화·가죽·뼈·물병·그릇·썩은 살점·실·막대·먹물 주머니·
    // 낚싯대·활)은 이미 등록돼 있어 새 ID 를 쓰지 않는다.
    /** 이름표(fishing_treasure 가중치 1). 몹 이름 지정 시스템이 없어 소지·전송만 가능하다. */
    public static final int NAME_TAG = 484;
    /** 안장(fishing_treasure 가중치 1). 몹 탑승 시스템이 없어 소지·전송만 가능하다. */
    public static final int SADDLE = 485;
    /** 앵무조개 껍데기(fishing_treasure 가중치 1). 바닐라에서도 제작 재료라 사용처가 없다. */
    public static final int NAUTILUS_SHELL = 486;
    /**
     * 마법이 부여된 책(fishing_treasure 가중치 1). 27비트 인챈트 마스크를 담는
     * 유일한 비도구 아이템이며 30레벨 인챈트를 갖고 나온다.
     */
    public static final int ENCHANTED_BOOK = 487;
    /** 철사 덫 갈고리(fishing_junk 가중치 10). 레드스톤·철사 덫이 없어 수집품으로만 존재한다. */
    public static final int TRIPWIRE_HOOK = 488;
    // ── 동물 상호작용 순수 아이템 489–490(MC Java 1.21.4). 491~492는 같은 트랙 예약 공백이다. ──
    /**
     * 달걀. 닭이 6,000~12,000틱마다 한 개 투하하고, 던지면 1/8 로 병아리가 태어난다
     * (근거·수치는 {@code MC-REFERENCE.md} 「닭 산란·달걀 투척」 절). 바닐라 max_stack_size 16.
     */
    public static final int EGG = 489;
    /**
     * 당근 낚싯대. 안장을 얹은 돼지에 탑승한 채 사용하면 돼지가 부스트한다.
     * 바닐라 max_damage 25 이고 한 번 부스트에 7 이 닳는다(4회 사용에 파괴된다).
     */
    public static final int CARROT_ON_A_STICK = 490;
    // ── 피글린 장비 순수 아이템 501(MC Java 1.21.4). 491~500은 다른 트랙 예약 공백이라
    // 되메우지 않는다. 502~505는 같은 트랙 예약 공백이다. ──
    /**
     * 석궁. 바닐라 max_damage 465 이고 장전에 {@code CrossbowItem.getChargeDuration} 25 바닐라
     * 틱이 걸린다. 제작은 막대 3 · 실 2 · 철 주괴 1 · 철사 덫 갈고리 1 이다.
     * 피글린 성체가 50% 확률로 들고 나오는 무기이기도 하다.
     */
    public static final int CROSSBOW = 501;

    // ── 심층암 가공 계열 월드 블록 506–520(MC Java 1.21.4). 502~505 는 다른 트랙 예약 공백이라
    // 되메우지 않는다. 전부 제작 전용 placement-only 라 지형 생성기·runtime overlay·구조물이
    // 어느 경로로도 만들지 않는다(worldPresence.placementOnlyIds → golden 무영향).
    // 배치는 형상군이 연속 구간이 되도록 재질 6종 → 계단 3종 → 반 블록 3종 → 담장 3종 순이다.
    /** polished_deepslate. 조약돌 심층암 4개 2×2 → 4개. */
    public static final int POLISHED_DEEPSLATE = 506;
    /** deepslate_bricks. 다듬은 심층암 4개 2×2 → 4개. */
    public static final int DEEPSLATE_BRICKS = 507;
    /** cracked_deepslate_bricks. 심층암 벽돌 제련 산출물(제작식 없음). */
    public static final int CRACKED_DEEPSLATE_BRICKS = 508;
    /** deepslate_tiles. 심층암 벽돌 4개 2×2 → 4개. */
    public static final int DEEPSLATE_TILES = 509;
    /** cracked_deepslate_tiles. 심층암 타일 제련 산출물(제작식 없음). */
    public static final int CRACKED_DEEPSLATE_TILES = 510;
    /** chiseled_deepslate. 다듬은 심층암 반 블록 2개를 세로로 → 1개. */
    public static final int CHISELED_DEEPSLATE = 511;
    public static final int POLISHED_DEEPSLATE_STAIRS = 512;
    public static final int DEEPSLATE_BRICK_STAIRS = 513;
    public static final int DEEPSLATE_TILE_STAIRS = 514;
    public static final int POLISHED_DEEPSLATE_SLAB = 515;
    public static final int DEEPSLATE_BRICK_SLAB = 516;
    public static final int DEEPSLATE_TILE_SLAB = 517;
    public static final int POLISHED_DEEPSLATE_WALL = 518;
    public static final int DEEPSLATE_BRICK_WALL = 519;
    public static final int DEEPSLATE_TILE_WALL = 520;

    // ── 주민 직업 스테이션 월드 블록 521–533 + 부재료 534(MC Java 1.21.4). 535~560 은 같은
    // 트랙 예약 공백이고 561~600 은 다른 트랙 예약 공백이라 되메우지 않는다. 전부 제작 전용
    // placement-only 라 지형 생성기·runtime overlay·구조물이 어느 경로로도 만들지 않는다
    // (worldPresence.placementOnlyIds → golden 무영향).
    //
    // 이 13종이 바닐라 1.21.4 의 직업 사이트 POI 전부다(barrel, blast_furnace, brewing_stand,
    // cartography_table, cauldron, composter, fletching_table, grindstone, lectern, loom,
    // smithing_table, smoker, stonecutter). 순서는 바닐라 식별자 알파벳순이라 새 스테이션이
    // 생겨도 삽입 위치가 논쟁거리가 되지 않는다. 직업 대응은
    // {@link com.gameexpert.engine.mob.villager.VillagerJobSitePolicy} 가 단독으로 소유한다.
    /** barrel. 어부 작업장이자 상자와 같은 보관 컨테이너. destroy_time 2.5 · 도끼 선호(도구 무관 드랍). */
    public static final int BARREL = 521;
    /** blast_furnace. 갑옷 장인 작업장. 광물 전용 제련로(제련 시간 절반). destroy_time 3.5. */
    public static final int BLAST_FURNACE = 522;
    /** brewing_stand. 성직자 작업장. 양조 자체는 미등록이라 POI 전용이다. destroy_time 0.5. */
    public static final int BREWING_STAND = 523;
    /** cartography_table. 제도사 작업장. destroy_time 2.5. */
    public static final int CARTOGRAPHY_TABLE = 524;
    /** cauldron. 가죽 세공사 작업장. 물 담기는 미등록이라 POI 전용이다. destroy_time 2.0. */
    public static final int CAULDRON = 525;
    /** composter. 농부 작업장. 퇴비화는 미등록이라 POI 전용이다. destroy_time 0.6. */
    public static final int COMPOSTER = 526;
    /** fletching_table. 화살 제작자 작업장. destroy_time 2.5. */
    public static final int FLETCHING_TABLE = 527;
    /** grindstone. 무기 대장장이 작업장. destroy_time 2.0 · explosion_resistance 6.0. */
    public static final int GRINDSTONE = 528;
    /** lectern. 사서 작업장. destroy_time 2.5. */
    public static final int LECTERN = 529;
    /** loom. 양치기 작업장. destroy_time 2.5. */
    public static final int LOOM = 530;
    /** smithing_table. 도구 대장장이 작업장. destroy_time 2.5. */
    public static final int SMITHING_TABLE = 531;
    /** smoker. 도살자 작업장. 음식 전용 제련로(제련 시간 절반). destroy_time 3.5. */
    public static final int SMOKER = 532;
    /** stonecutter. 석공 작업장. destroy_time 3.5. */
    public static final int STONECUTTER = 533;
    /**
     * smooth_stone. 스테이션 자체는 아니지만 바닐라 blast_furnace 제작식의 필수 부재료이며
     * 돌을 제련해 얻는다. destroy_time 2.0 · explosion_resistance 6.0.
     */
    public static final int SMOOTH_STONE = 534;

    // ── 양털 16색 월드 블록 561–576 · 카펫 16색 577–592(MC Java 1.21.4). 535~560 은 다른
    // 트랙 예약 공백이고 593~600 은 같은 트랙 예약 공백이라 되메우지 않는다. 전부 제작 전용
    // placement-only 라 지형 생성기·runtime overlay·구조물이 어느 경로로도 만들지 않는다
    // (worldPresence.placementOnlyIds → golden 무영향).
    //
    // 색 순서는 MC {@code DyeColor} 네트워크 ID 순서이며 염료 452–467 과 문자 그대로 같다.
    // 양 권위의 {@code sheepColor} 도 같은 인덱스를 쓰므로 전단 드랍은 색 인덱스를 그대로
    // {@link #WOOL_BY_DYE_COLOR} 에 넣어 색별 양털을 떨군다.
    //
    // 순수 아이템 {@code PlayerInventory.WOOL=264} 는 의미를 바꾸지 않고 그대로 둔다.
    // 저장된 인벤토리·상자 전리품·주민 거래가 이미 그 ID 를 쓰고 있어 재해석이 곧 데이터
    // 파손이기 때문이다. 대신 264 는 흰 양털로 1:1 제작 변환된다(CraftRecipe "white_wool_from_wool").
    // 양털: destroy_time 0.8 · explosion_resistance 0.8 · 도구 무관 회수.
    // 카펫: destroy_time 0.1 · explosion_resistance 0.1 · 부분 높이 1/16.
    public static final int WHITE_WOOL = 561;
    public static final int ORANGE_WOOL = 562;
    public static final int MAGENTA_WOOL = 563;
    public static final int LIGHT_BLUE_WOOL = 564;
    public static final int YELLOW_WOOL = 565;
    public static final int LIME_WOOL = 566;
    public static final int PINK_WOOL = 567;
    public static final int GRAY_WOOL = 568;
    public static final int LIGHT_GRAY_WOOL = 569;
    public static final int CYAN_WOOL = 570;
    public static final int PURPLE_WOOL = 571;
    public static final int BLUE_WOOL = 572;
    public static final int BROWN_WOOL = 573;
    public static final int GREEN_WOOL = 574;
    public static final int RED_WOOL = 575;
    public static final int BLACK_WOOL = 576;
    public static final int WHITE_CARPET = 577;
    public static final int ORANGE_CARPET = 578;
    public static final int MAGENTA_CARPET = 579;
    public static final int LIGHT_BLUE_CARPET = 580;
    public static final int YELLOW_CARPET = 581;
    public static final int LIME_CARPET = 582;
    public static final int PINK_CARPET = 583;
    public static final int GRAY_CARPET = 584;
    public static final int LIGHT_GRAY_CARPET = 585;
    public static final int CYAN_CARPET = 586;
    public static final int PURPLE_CARPET = 587;
    public static final int BLUE_CARPET = 588;
    public static final int BROWN_CARPET = 589;
    public static final int GREEN_CARPET = 590;
    public static final int RED_CARPET = 591;
    public static final int BLACK_CARPET = 592;

    /**
     * 색 인덱스(0..15) → 양털 블록 ID. 인덱스는 MC {@code DyeColor} 네트워크 ID이며
     * 염료 452–467 및 양 권위 {@code sheepColor} 와 같은 순서다.
     */
    public static final int[] WOOL_BY_DYE_COLOR = {
            WHITE_WOOL, ORANGE_WOOL, MAGENTA_WOOL, LIGHT_BLUE_WOOL,
            YELLOW_WOOL, LIME_WOOL, PINK_WOOL, GRAY_WOOL,
            LIGHT_GRAY_WOOL, CYAN_WOOL, PURPLE_WOOL, BLUE_WOOL,
            BROWN_WOOL, GREEN_WOOL, RED_WOOL, BLACK_WOOL,
    };

    /** 색 인덱스(0..15) → 카펫 블록 ID. {@link #WOOL_BY_DYE_COLOR} 와 같은 순서다. */
    public static final int[] CARPET_BY_DYE_COLOR = {
            WHITE_CARPET, ORANGE_CARPET, MAGENTA_CARPET, LIGHT_BLUE_CARPET,
            YELLOW_CARPET, LIME_CARPET, PINK_CARPET, GRAY_CARPET,
            LIGHT_GRAY_CARPET, CYAN_CARPET, PURPLE_CARPET, BLUE_CARPET,
            BROWN_CARPET, GREEN_CARPET, RED_CARPET, BLACK_CARPET,
    };

    // ── 종별 목재 가공 계열 월드 블록 601–649(MC Java 1.21.4). 521~600 은 다른 트랙 배정
    // 구간이라 되메우지 않는다. 전부 제작 전용 placement-only 라 지형 생성기·runtime overlay·
    // 구조물이 어느 경로로도 만들지 않는다(worldPresence.placementOnlyIds → golden 무영향).
    //
    // 배치는 형상군이 연속 구간이 되도록 **형상 바깥 / 수종 안쪽**이다. 즉 판자 7 → 계단 7 →
    // 반 블록 7 → 울타리 7 → 울타리문 7 → 다락문 7 → 문 7 이고, 각 구간의 수종 순서는 항상
    // 자작·가문비·정글·아카시아·짙은참나무·벚나무·맹그로브다. 형상 판정(isStairs·isSlab·
    // isFence·isFenceGate·isTrapdoor·isDoor)이 범위 비교 하나로 끝나야 렌더·물리·권위 세 사본이
    // 갈릴 여지가 없다.
    //
    // 기존 총칭 ID(PLANK 7 · PLANK_SLAB 27 · DOOR_CLOSED 29 · WOOD_STAIRS 81 ·
    // WOOD_FENCE 90 · WOOD_FENCE_GATE 91 · WOOD_TRAPDOOR 94)의 저장 의미는 바꾸지 않는다.
    // 정확한 구조물 상태가 별도 항목을 요구하면 append-only ID를 뒤에 추가한다.
    public static final int BIRCH_PLANK = 601;
    public static final int SPRUCE_PLANK = 602;
    public static final int JUNGLE_PLANK = 603;
    public static final int ACACIA_PLANK = 604;
    public static final int DARK_OAK_PLANK = 605;
    public static final int CHERRY_PLANK = 606;
    public static final int MANGROVE_PLANK = 607;
    public static final int BIRCH_STAIRS = 608;
    public static final int SPRUCE_STAIRS = 609;
    public static final int JUNGLE_STAIRS = 610;
    public static final int ACACIA_STAIRS = 611;
    public static final int DARK_OAK_STAIRS = 612;
    public static final int CHERRY_STAIRS = 613;
    public static final int MANGROVE_STAIRS = 614;
    public static final int BIRCH_SLAB = 615;
    public static final int SPRUCE_SLAB = 616;
    public static final int JUNGLE_SLAB = 617;
    public static final int ACACIA_SLAB = 618;
    public static final int DARK_OAK_SLAB = 619;
    public static final int CHERRY_SLAB = 620;
    public static final int MANGROVE_SLAB = 621;
    public static final int BIRCH_FENCE = 622;
    public static final int SPRUCE_FENCE = 623;
    public static final int JUNGLE_FENCE = 624;
    public static final int ACACIA_FENCE = 625;
    public static final int DARK_OAK_FENCE = 626;
    public static final int CHERRY_FENCE = 627;
    public static final int MANGROVE_FENCE = 628;
    public static final int BIRCH_FENCE_GATE = 629;
    public static final int SPRUCE_FENCE_GATE = 630;
    public static final int JUNGLE_FENCE_GATE = 631;
    public static final int ACACIA_FENCE_GATE = 632;
    public static final int DARK_OAK_FENCE_GATE = 633;
    public static final int CHERRY_FENCE_GATE = 634;
    public static final int MANGROVE_FENCE_GATE = 635;
    public static final int BIRCH_TRAPDOOR = 636;
    public static final int SPRUCE_TRAPDOOR = 637;
    public static final int JUNGLE_TRAPDOOR = 638;
    public static final int ACACIA_TRAPDOOR = 639;
    public static final int DARK_OAK_TRAPDOOR = 640;
    public static final int CHERRY_TRAPDOOR = 641;
    public static final int MANGROVE_TRAPDOOR = 642;
    public static final int BIRCH_DOOR = 643;
    public static final int SPRUCE_DOOR = 644;
    public static final int JUNGLE_DOOR = 645;
    public static final int ACACIA_DOOR = 646;
    public static final int DARK_OAK_DOOR = 647;
    public static final int CHERRY_DOOR = 648;
    public static final int MANGROVE_DOOR = 649;

    // ── 석재 가공 계열 월드 블록 701–721(MC Java 1.21.4). 650~700 은 다른 트랙 배정
    // 구간이라 되메우지 않는다. 전부 제작 전용 placement-only 라 지형 생성기·runtime
    // overlay·구조물이 어느 경로로도 만들지 않는다(worldPresence.placementOnlyIds →
    // golden 무영향). 배치는 형상군이 연속 구간이 되도록 계단 7종 → 반 블록 8종 →
    // 담장 6종 순이다.
    //
    // 이 계열은 **새 아틀라스 타일을 쓰지 않는다**. 16×16=256 슬롯 블록 아틀라스가 이미
    // 가득 찼고(정점 속성 aTileIndex 가 Uint8 이라 타일 상한이 자료형으로 255) 새 재질을
    // 배정할 자리가 없어, 이미 타일이 있는 재질의 형상 변형만 등록한다. 다듬은 화성암·
    // 매끄러운 돌/사암·금 간·조각된 석재 벽돌·점토/진흙 벽돌·응회암·프리즈머린은
    // 아틀라스 확장(512px + aTileIndex Uint16) 뒤로 미룬다.
    /** granite_stairs. 화강암 6 → 4. 경도 1.5 · 폭발 저항 6.0(화강암 사본). */
    public static final int GRANITE_STAIRS = 701;
    /** diorite_stairs. 섬록암 6 → 4. */
    public static final int DIORITE_STAIRS = 702;
    /** andesite_stairs. 안산암 6 → 4. */
    public static final int ANDESITE_STAIRS = 703;
    /** red_sandstone_stairs. 붉은 사암 6 → 4. 경도 0.8 · 폭발 저항 0.8(붉은 사암 사본). */
    public static final int RED_SANDSTONE_STAIRS = 704;
    /** stone_stairs. 돌 6 → 4. 경도 1.5 · 폭발 저항 6.0(돌 사본). */
    public static final int STONE_STAIRS = 705;
    /** mossy_cobblestone_stairs. 이끼 낀 조약돌 6 → 4. 경도 2.0 · 폭발 저항 6.0. */
    public static final int MOSSY_COBBLE_STAIRS = 706;
    /** mossy_stone_brick_stairs. 이끼 낀 석재 벽돌 6 → 4. 경도 1.5 · 폭발 저항 6.0. */
    public static final int MOSSY_STONE_BRICK_STAIRS = 707;
    public static final int GRANITE_SLAB = 708;
    public static final int DIORITE_SLAB = 709;
    public static final int ANDESITE_SLAB = 710;
    /** cut_sandstone_slab. 잘린 사암 3 → 6. 바닐라가 반 블록에만 주는 경도 2.0 · 저항 6.0. */
    public static final int CUT_SANDSTONE_SLAB = 711;
    /** red_sandstone_slab. 붉은 사암 3 → 6. 계단(0.8)과 달리 반 블록은 2.0 / 6.0 이다. */
    public static final int RED_SANDSTONE_SLAB = 712;
    /** stone_slab. 돌 3 → 6. 계단(1.5)과 달리 반 블록은 2.0 / 6.0 이다. */
    public static final int STONE_SLAB = 713;
    public static final int MOSSY_COBBLE_SLAB = 714;
    public static final int MOSSY_STONE_BRICK_SLAB = 715;
    public static final int GRANITE_WALL = 716;
    public static final int DIORITE_WALL = 717;
    public static final int ANDESITE_WALL = 718;
    /** sandstone_wall. 사암 6 → 6. 경도 0.8 · 폭발 저항 0.8(사암 사본). */
    public static final int SANDSTONE_WALL = 719;
    public static final int RED_SANDSTONE_WALL = 720;
    public static final int MOSSY_STONE_BRICK_WALL = 721;

    // ── [STONE-RESIDUAL] 석재 잔여 계열 722–764 + 점토 벽돌 아이템 765(MC Java 1.21.4). ──
    // 앞 트랙(701~721)이 **아틀라스 슬롯 고갈** 때문에 미뤄 둔 새 재질들이다. ATLAS-1024
    // 확장(512px · aTileIndex Uint16 · 1024 슬롯)으로 제약이 풀려 이제 착지한다.
    // 전부 제작 전용 placement-only 라 지형 생성기·runtime overlay·구조물이 어느 경로로도
    // 만들지 않는다(worldPresence.placementOnlyIds → golden terrain 무영향).
    // 766~800 은 이 트랙이 남긴 예약 공백이다.
    //
    // 배치는 앞 트랙과 같이 형상군이 연속 구간이 되게 완전 큐브 16 → 계단 10 → 반 블록 12 →
    // 담장 5 순이다(BuildingBlockRules·클라 판정이 범위 하나만 더 본다).
    //
    // 경도/폭발 저항은 재질이 아니라 **변형별**로 갈린다. 사암 원석 0.8 인데 매끄러운 사암은
    // 2.0/6.0 이고, 붉은 사암 잘린·조각된은 0.8/0.8 인데 잘린 반 블록만 2.0/6.0 이다.
    // 진흙 계열만 폭발 저항이 3.0 이다(다진 진흙 1.0/3.0 · 진흙 벽돌 1.5/3.0).
    //
    // 프리즈머린 11종은 **일부러 빠져 있다**. 바닐라 획득 경로가 가디언 드랍과 해저 신전뿐인데
    // 이 저장소에는 둘 다 없어, 등록하면 어떤 정상 플레이로도 닿지 못하는 사장 블록이 된다.
    /** polished_granite. 화강암 4 → 4. 경도 1.5 · 폭발 저항 6.0. */
    public static final int POLISHED_GRANITE = 722;
    /** polished_diorite. 섬록암 4 → 4. */
    public static final int POLISHED_DIORITE = 723;
    /** polished_andesite. 안산암 4 → 4. */
    public static final int POLISHED_ANDESITE = 724;
    /** smooth_sandstone. 사암 제련. 원석(0.8/0.8)이 아니라 경도 2.0 · 폭발 저항 6.0 이다. */
    public static final int SMOOTH_SANDSTONE = 725;
    /** chiseled_red_sandstone. 붉은 사암 반 블록 2 세로. 경도 0.8 · 폭발 저항 0.8. */
    public static final int CHISELED_RED_SANDSTONE = 726;
    /** cut_red_sandstone. 붉은 사암 4 → 4. 경도 0.8 · 폭발 저항 0.8. */
    public static final int CUT_RED_SANDSTONE = 727;
    /** smooth_red_sandstone. 붉은 사암 제련. 경도 2.0 · 폭발 저항 6.0. */
    public static final int SMOOTH_RED_SANDSTONE = 728;
    /** cracked_stone_bricks. 석재 벽돌 제련. 경도 1.5 · 폭발 저항 6.0. */
    public static final int CRACKED_STONE_BRICKS = 729;
    /** chiseled_stone_bricks. 석재 벽돌 반 블록 2 세로. */
    public static final int CHISELED_STONE_BRICKS = 730;
    /** bricks. 점토 벽돌 아이템 4 → 1. 경도 2.0 · 폭발 저항 6.0. */
    public static final int BRICKS = 731;
    /** packed_mud. 진흙 1 + 밀 1. 이 계열에서 유일하게 경도 1.0 · 폭발 저항 3.0 이다. */
    public static final int PACKED_MUD = 732;
    /** mud_bricks. 다진 진흙 4 → 4. 경도 1.5 · 폭발 저항 3.0. */
    public static final int MUD_BRICKS = 733;
    /** chiseled_tuff. 응회암 반 블록 2 세로. 응회암 계열은 전부 1.5 / 6.0 이다. */
    public static final int CHISELED_TUFF = 734;
    /** polished_tuff. 응회암 4 → 4. */
    public static final int POLISHED_TUFF = 735;
    /** tuff_bricks. 다듬은 응회암 4 → 4. */
    public static final int TUFF_BRICKS = 736;
    /** chiseled_tuff_bricks. 응회암 벽돌 반 블록 2 세로. */
    public static final int CHISELED_TUFF_BRICKS = 737;
    public static final int POLISHED_GRANITE_STAIRS = 738;
    public static final int POLISHED_DIORITE_STAIRS = 739;
    public static final int POLISHED_ANDESITE_STAIRS = 740;
    public static final int SMOOTH_SANDSTONE_STAIRS = 741;
    public static final int SMOOTH_RED_SANDSTONE_STAIRS = 742;
    public static final int BRICK_STAIRS = 743;
    public static final int MUD_BRICK_STAIRS = 744;
    public static final int TUFF_STAIRS = 745;
    public static final int POLISHED_TUFF_STAIRS = 746;
    public static final int TUFF_BRICK_STAIRS = 747;
    public static final int POLISHED_GRANITE_SLAB = 748;
    public static final int POLISHED_DIORITE_SLAB = 749;
    public static final int POLISHED_ANDESITE_SLAB = 750;
    public static final int SMOOTH_SANDSTONE_SLAB = 751;
    /** cut_red_sandstone_slab. 원석·잘린 붉은 사암(0.8)과 달리 반 블록만 2.0 / 6.0 이다. */
    public static final int CUT_RED_SANDSTONE_SLAB = 752;
    public static final int SMOOTH_RED_SANDSTONE_SLAB = 753;
    /** smooth_stone_slab. 매끄러운 돌 3 → 6. 본체(534)와 같은 2.0 / 6.0. */
    public static final int SMOOTH_STONE_SLAB = 754;
    public static final int BRICK_SLAB = 755;
    public static final int MUD_BRICK_SLAB = 756;
    public static final int TUFF_SLAB = 757;
    public static final int POLISHED_TUFF_SLAB = 758;
    public static final int TUFF_BRICK_SLAB = 759;
    public static final int BRICK_WALL = 760;
    public static final int MUD_BRICK_WALL = 761;
    public static final int TUFF_WALL = 762;
    public static final int POLISHED_TUFF_WALL = 763;
    public static final int TUFF_BRICK_WALL = 764;
    /**
     * brick. 점토 덩이 제련 산출인 **순수 아이템**이라 월드 블록 배열에 들어가지 않는다
     * (BLOCK_ID_HIGH_WATER 를 올리지 않는다). 넷을 2×2 로 모으면 BRICKS=731 이 된다.
     */
    public static final int BRICK = 765;

    // ── [POTION] 양조 순수 아이템 801–815(MC Java 1.21.4). 766~800 은 석재 잔여 트랙 예약 공백이라
    // 되메우지 않는다. 816~832 는 같은 트랙의 강화 물약이고 833~840 이 남은 예약 공백이다.
    // 전부 순수 아이템이라 월드 블록
    // 배열에 들어가지 않는다(BLOCK_ID_HIGH_WATER 를 올리지 않는다). ──
    /** 설탕. 바닐라 {@code sugar_from_sugar_cane}: 사탕수수 1 → 설탕 1. */
    public static final int SUGAR = 801;
    /** 발효된 거미 눈. 바닐라 무형 제작: 거미 눈 1 + 설탕 1 + 갈색 버섯 1. */
    public static final int FERMENTED_SPIDER_EYE = 802;
    /** 어색한 물약. 효과가 없는 양조 중간재이며 모든 효과 물약의 바탕이다. */
    public static final int AWKWARD_POTION = 803;
    /** 신속의 물약(3:00). 어색한 물약 + 설탕. */
    public static final int POTION_SWIFTNESS = 804;
    /** 독 물약(0:45). 어색한 물약 + 거미 눈. */
    public static final int POTION_POISON = 805;
    /** 감속의 물약(1:30). 신속의 물약 + 발효된 거미 눈. */
    public static final int POTION_SLOWNESS = 806;
    /** 나약함의 물약(1:30). 물병 + 발효된 거미 눈(바닐라 그대로 어색한 물약이 필요 없다). */
    public static final int POTION_WEAKNESS = 807;
    /** 고통의 물약(즉발 6). 독 물약 + 발효된 거미 눈. */
    public static final int POTION_HARMING = 808;
    /** 투척용 신속의 물약. 어떤 물약이든 화약 1을 더하면 투척형이 된다(바닐라 그대로). */
    public static final int SPLASH_POTION_SWIFTNESS = 809;
    public static final int SPLASH_POTION_POISON = 810;
    public static final int SPLASH_POTION_SLOWNESS = 811;
    public static final int SPLASH_POTION_WEAKNESS = 812;
    public static final int SPLASH_POTION_HARMING = 813;
    /**
     * 블레이즈 막대. 바닐라 양조대 제작식(블레이즈 막대 1 + 조약돌 3)의 재료이며 이 저장소에는
     * 네더가 없어 <b>레이드 승리 보상 풀</b>과 <b>던전 상자 전리품</b>으로만 얻는다(획득 경로
     * divergence — 재료 자체는 바닐라 원본 그대로다). 순수 아이템이라 월드 블록이 아니다.
     */
    public static final int BLAZE_ROD = 814;
    /**
     * 네더 사마귀. 바닐라 어색한 물약(물병 + 네더 사마귀)의 재료다. 영혼 모래가 없어 재배 경로가
     * 없으므로 블레이즈 막대와 같은 두 획득 경로(레이드 보상·던전 상자)로만 얻는다. 이 저장소에서는
     * 심는 작물이 아니라 순수 아이템이다(월드 블록 배열·작물 성장 표에 들어가지 않는다).
     */
    public static final int NETHER_WART = 815;

    // ── [POTION-UPGRADE] 강화 물약 순수 아이템 816–832(MC Java 1.21.4). 바닐라 양조 강화는
    // 레드스톤(연장) · 발광석 가루(II 등급) 둘뿐이고 한 병에 동시에 걸 수 없다. 833~840 은
    // 같은 트랙 예약 공백으로 남는다. 전부 순수 아이템이라 월드 블록이 아니다. ──
    /**
     * 발광석 가루. 바닐라 II 등급 강화 재료다. 이 저장소에는 발광석 블록이 없어 채굴 드랍
     * 경로가 없고, 바닐라 마녀 전리품표(`entities/witch`)의 후보 그대로 마녀에게서만 얻는다
     * (획득 경로 divergence — 재료 자체는 바닐라 원본이다). 스택 64.
     */
    public static final int GLOWSTONE_DUST = 816;
    // 연장(레드스톤) 네 종. 고통의 물약은 즉발이라 바닐라에도 연장형이 없다.
    /** 신속의 물약 (연장) 8:00. 신속의 물약 + 레드스톤. */
    public static final int POTION_SWIFTNESS_LONG = 817;
    /** 독 물약 (연장) 1:30. 독 물약 + 레드스톤. */
    public static final int POTION_POISON_LONG = 818;
    /** 감속의 물약 (연장) 4:00. 감속의 물약 + 레드스톤. */
    public static final int POTION_SLOWNESS_LONG = 819;
    /** 나약함의 물약 (연장) 4:00. 나약함의 물약 + 레드스톤. */
    public static final int POTION_WEAKNESS_LONG = 820;
    // II 등급(발광석 가루) 네 종. 나약함은 바닐라에도 II 등급이 없다.
    /** 신속의 물약 II 1:30(증폭 1). 신속의 물약 + 발광석 가루. */
    public static final int POTION_SWIFTNESS_II = 821;
    /** 독 물약 II 0:21(증폭 1). 독 물약 + 발광석 가루. */
    public static final int POTION_POISON_II = 822;
    /** 감속의 물약 II 0:20(증폭 3 — 바닐라 STRONG_SLOWNESS 는 감속 IV 다). */
    public static final int POTION_SLOWNESS_II = 823;
    /** 고통의 물약 II(즉발, 증폭 1). 고통의 물약 + 발광석 가루. */
    public static final int POTION_HARMING_II = 824;
    // 투척 강화 여덟 종. 마시는 강화 여덟 종과 같은 순서라 `id - POTION_SWIFTNESS_LONG` 으로
    // 서로 변환된다(기본 다섯 종의 ±5 규약과 같은 꼴이고 여기서는 오프셋이 8 이다).
    public static final int SPLASH_POTION_SWIFTNESS_LONG = 825;
    public static final int SPLASH_POTION_POISON_LONG = 826;
    public static final int SPLASH_POTION_SLOWNESS_LONG = 827;
    public static final int SPLASH_POTION_WEAKNESS_LONG = 828;
    public static final int SPLASH_POTION_SWIFTNESS_II = 829;
    public static final int SPLASH_POTION_POISON_II = 830;
    public static final int SPLASH_POTION_SLOWNESS_II = 831;
    public static final int SPLASH_POTION_HARMING_II = 832;

    // ── [FURNACE-VARIANT] 제련로 변형의 점화 쌍둥이 841–842(MC Java 1.21.4의 lit=true 상태).
    // 843~850 은 같은 트랙 예약 공백이다. 바닐라에서 blast_furnace·smoker 는 furnace 와 같이
    // {@code lit} 불리언 상태를 가지며, 이 저장소는 상태 비트가 아니라 별도 ID 로 그 상태를
    // 표현하는 기존 {@link #FURNACE}(33) ↔ {@link #FURNACE_LIT}(34) 선례를 그대로 따른다.
    //
    // 이 두 ID 는 런타임 전용이다 — 제련 큐가 522↔841 · 532↔842 를 스왑하며 만들고,
    // 플레이어가 직접 설치할 수 없고({@link #isPlaceableBlock}), 부수면 미점화 원본을
    // 드랍한다(InventoryRules). 지형 생성기·구조물은 어느 경로로도 만들지 않으므로
    // worldPresence 에서는 {@code runtimeIds} 이며 golden terrain 해시에 영향이 없다.
    //
    // 점유 불변식: 점화 상태에서도 주민 직업 사이트 판정이 유지되어야 한다. lit ID 가
    // 521–533 연속 구간 밖이므로 {@code VillagerJobSitePolicy.stationCode} 는 lit→base
    // 정규화를 먼저 거친다(그 정규화의 정본은 {@link com.gameexpert.engine.FurnaceVariant}).
    /** blast_furnace[lit=true]. 522 의 런타임 점화 쌍둥이. 드랍·경도·폭발 저항은 522 와 같다. */
    public static final int BLAST_FURNACE_LIT = 841;
    /** smoker[lit=true]. 532 의 런타임 점화 쌍둥이. 드랍·경도·폭발 저항은 532 와 같다. */
    public static final int SMOKER_LIT = 842;

    // ── [BED-COLOR] 침대 16색 월드 블록 851–866(MC Java 1.21.4). 843~850 은 제련로 변형 트랙
    // 예약 공백이고 867~870 은 같은 트랙 예약 공백이라 되메우지 않는다. 전부 제작 전용
    // placement-only 라 지형 생성기·runtime overlay·구조물이 어느 경로로도 만들지 않는다
    // (worldPresence.placementOnlyIds → golden 무영향). 마을 생성기는 계속 총칭 {@link #BED} 만
    // 놓는다.
    //
    // 색 순서는 MC {@code DyeColor} 네트워크 ID 순서이며 염료 452–467 · 양털 561–576 과
    // 문자 그대로 같다.
    //
    // 총칭 {@code BED=32} 는 의미를 바꾸지 않고 그대로 둔다 — 저장된 월드·마을 생성물·
    // 플레이어 인벤토리가 이미 그 ID 를 쓰고 있어 재해석이 곧 데이터 파손이기 때문이다
    // (양털 264 를 남겨 둔 것과 같은 근거). 대신 {@link #isBed(int)} 가 32 와 851~866 을
    // 한 판정으로 묶어 수면·리스폰·마을 침대 집계가 색과 무관하게 침대를 본다.
    //
    // 형상·상태·충돌·지지 계약은 전부 기존 침대 계약을 그대로 재사용한다(새 shape 없음).
    // 렌더도 새 아틀라스 타일을 만들지 않고 기존 침대 타일에 색조를 곱한다(양털과 같은 방식).
    public static final int WHITE_BED = 851;
    public static final int ORANGE_BED = 852;
    public static final int MAGENTA_BED = 853;
    public static final int LIGHT_BLUE_BED = 854;
    public static final int YELLOW_BED = 855;
    public static final int LIME_BED = 856;
    public static final int PINK_BED = 857;
    public static final int GRAY_BED = 858;
    public static final int LIGHT_GRAY_BED = 859;
    public static final int CYAN_BED = 860;
    public static final int PURPLE_BED = 861;
    public static final int BLUE_BED = 862;
    public static final int BROWN_BED = 863;
    public static final int GREEN_BED = 864;
    public static final int RED_BED = 865;
    public static final int BLACK_BED = 866;

    /**
     * 색 인덱스(0..15) → 침대 블록 ID. 인덱스는 MC {@code DyeColor} 네트워크 ID이며
     * 염료 452–467 및 {@link #WOOL_BY_DYE_COLOR} 와 같은 순서다.
     */
    public static final int[] BED_BY_DYE_COLOR = {
            WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED,
            YELLOW_BED, LIME_BED, PINK_BED, GRAY_BED,
            LIGHT_GRAY_BED, CYAN_BED, PURPLE_BED, BLUE_BED,
            BROWN_BED, GREEN_BED, RED_BED, BLACK_BED,
    };

    // ── [STRIPPED-LOG] 벗긴 원목 8수종 × 3축 = 24 월드 블록 871–894(MC Java 1.21.4).
    // 867~870 은 침대 트랙 예약 공백이라 되메우지 않고 895~930 이 이 트랙의 예약 공백이다.
    //
    // 축 표현은 **기존 통나무 규약을 그대로 따른다** — 참나무·자작나무가 이미 축을 별도
    // ID(LOG/LOG_X/LOG_Z · BIRCH_LOG/…)로 잡고 있으므로 벗긴 원목도 축을 ID 로 잡는다.
    // 배치는 **수종 바깥·축 안쪽**이라 한 수종의 세 축이 항상 연속 3칸이다:
    //
    //   id = STRIPPED_OAK_LOG + species*3 + axis      (axis 0=Y, 1=X, 2=Z)
    //
    // 그래서 "벗긴 원목인가" 판정은 {@link #isStrippedLog(int)} 의 범위 비교 한 줄이고,
    // 축·수종 분해도 나눗셈 하나다(형상 판정이 재질별 나열로 흩어지면 한 사본만 갱신되는
    // 사고가 난다 — 종별 목재 세트에서 이미 겪은 교훈).
    //
    // 수종 순서는 참나무를 맨 앞에 두고 그 뒤로 종별 목재 세트(601~649)와 같은 순서다:
    //   oak · birch · spruce · jungle · acacia · dark_oak · cherry · mangrove.
    //
    // 세계 존재 경로는 **제작 전용이 아니라 상호작용 전용**이다. 지형 생성기·runtime
    // overlay·구조물이 어느 경로로도 만들지 않고(golden terrain 무영향), 플레이어가 도끼로
    // 통나무를 우클릭해 벗기거나 그렇게 얻은 블록을 다시 설치할 때만 월드에 나타난다.
    //
    // 물성은 바닐라 stripped_*_log 그대로다: destroy_time 2.0 · explosion_resistance 2.0 ·
    // 도구 티어 없음(맨손 채굴 가능, 도끼가 빠를 뿐) · 자기 자신 드랍 · 지지 없음.
    // {@link com.gameexpert.engine.BlockFamilies#isWoodLog(int)} 가 이 구간을 포함하므로
    // 경도·폭발 저항·드랍·잎 유지·인화성이 통나무와 한 표를 공유한다(바닐라 #minecraft:logs).
    public static final int STRIPPED_OAK_LOG = 871;
    public static final int STRIPPED_OAK_LOG_X = 872;
    public static final int STRIPPED_OAK_LOG_Z = 873;
    public static final int STRIPPED_BIRCH_LOG = 874;
    public static final int STRIPPED_BIRCH_LOG_X = 875;
    public static final int STRIPPED_BIRCH_LOG_Z = 876;
    public static final int STRIPPED_SPRUCE_LOG = 877;
    public static final int STRIPPED_SPRUCE_LOG_X = 878;
    public static final int STRIPPED_SPRUCE_LOG_Z = 879;
    public static final int STRIPPED_JUNGLE_LOG = 880;
    public static final int STRIPPED_JUNGLE_LOG_X = 881;
    public static final int STRIPPED_JUNGLE_LOG_Z = 882;
    public static final int STRIPPED_ACACIA_LOG = 883;
    public static final int STRIPPED_ACACIA_LOG_X = 884;
    public static final int STRIPPED_ACACIA_LOG_Z = 885;
    public static final int STRIPPED_DARK_OAK_LOG = 886;
    public static final int STRIPPED_DARK_OAK_LOG_X = 887;
    public static final int STRIPPED_DARK_OAK_LOG_Z = 888;
    public static final int STRIPPED_CHERRY_LOG = 889;
    public static final int STRIPPED_CHERRY_LOG_X = 890;
    public static final int STRIPPED_CHERRY_LOG_Z = 891;
    public static final int STRIPPED_MANGROVE_LOG = 892;
    public static final int STRIPPED_MANGROVE_LOG_X = 893;
    public static final int STRIPPED_MANGROVE_LOG_Z = 894;

    /**
     * 수종 인덱스(0..7) → 그 수종의 y축 통나무 ID. 순서는 벗긴 원목 구간의 수종 순서와
     * 문자 그대로 같아서 {@code STRIPPED_OAK_LOG + species*3} 의 역함수 역할을 한다.
     */
    /**
     * 벗긴 원목 정본 표. 인덱스는 {@code species*3 + axis}(axis 0=Y, 1=X, 2=Z)이며
     * {@link #strippedLog(int, int)} 가 이 표만 읽는다. 산술로만 두면 24개 상수가 어디에서도
     * 참조되지 않는 "선언만 있는 ID"가 되어 재배정 사고를 잡을 수 없으므로, 표를 정본으로 두고
     * 산술은 그 표의 인덱스 규약으로만 남긴다.
     */
    public static final int[] STRIPPED_LOG_BY_SPECIES_AND_AXIS = {
            STRIPPED_OAK_LOG, STRIPPED_OAK_LOG_X, STRIPPED_OAK_LOG_Z,
            STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_LOG_X, STRIPPED_BIRCH_LOG_Z,
            STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_LOG_X, STRIPPED_SPRUCE_LOG_Z,
            STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_LOG_X, STRIPPED_JUNGLE_LOG_Z,
            STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_LOG_X, STRIPPED_ACACIA_LOG_Z,
            STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_LOG_X, STRIPPED_DARK_OAK_LOG_Z,
            STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_LOG_X, STRIPPED_CHERRY_LOG_Z,
            STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_LOG_X, STRIPPED_MANGROVE_LOG_Z,
    };

    public static final int[] LOG_BY_WOOD_SPECIES = {
            LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG,
            ACACIA_LOG, DARK_OAK_LOG, CHERRY_LOG, MANGROVE_LOG,
    };

    // ── [GUARDIAN] 해저 신전 전리품 ────────────────────────────────────────
    // 젖은 스펀지는 바닐라 `minecraft:wet_sponge` 월드 블록이다(엘더 가디언 100% 드랍).
    // 바닐라 물성 그대로: destroy_time 0.6 · 도구 티어 없음(괭이가 빠를 뿐) · 자기 자신
    // 드랍 · 지지 없음.
    //
    // [PRISMARINE] 후행 트랙 갱신: 마른 스펀지({@link #SPONGE} 934)와 화로 건조
    // (젖은 스펀지 → 스펀지)가 착지했다. 바닐라 {@code wet_sponge} 제련은 물병을 부산물로
    // 주지만 이 저장소의 제련 계약은 산출 1종뿐이라 물병은 나오지 않는다(divergence).
    public static final int WET_SPONGE = 931;

    // [GUARDIAN] 프리즈머린 조각·수정은 바닐라에서도 순수 아이템이다.
    // [PRISMARINE] 후행 트랙에서 제작 소비처가 생겼다 — 조각 4→프리즈머린, 조각 9→벽돌,
    // 조각 8+검은 염료→어두운 프리즈머린, 수정 5+조각 4→바다 랜턴(전부 바닐라 원본 배치).
    public static final int PRISMARINE_SHARD = 932;
    public static final int PRISMARINE_CRYSTALS = 933;

    // ── [PRISMARINE] 프리즈머린 계열 · 바다 랜턴 · 마른 스펀지 934–945(MC Java 1.21.4). ──
    // 앞선 [GUARDIAN] 트랙이 획득 경로(엘더 가디언 드랍 = 젖은 스펀지 931 · 프리즈머린
    // 조각 932 · 수정 933)를 먼저 착지시켰고, 이 트랙이 그 재료가 흘러 들어갈 **제작 산출
    // 블록**을 등록한다. 재료 대체는 하지 않는다 — 배치·개수는 바닐라 recipes/*.json 그대로다.
    //
    // 전부 제작 전용(placement-only)이라 지형 생성기·runtime overlay 가 만들지 않는다.
    // 예외는 {@link #PRISMARINE} 하나로, 해저 유적 생성기가 석재 트랙 시절 임시로 쓰던
    // {@link #CALCITE} 대체 팔레트를 이 트랙이 원래 재질로 되돌리면서 구조물 생성물이 됐다
    // (worldPresence.runtimeIds). 어느 쪽이든 golden terrain 해시는 u8 ID 범위(≤255)만
    // 담으므로 영향이 없다.
    //
    // 배치는 앞 석재 계열과 같이 **형상군이 연속 구간**이 되게 완전 큐브 5 → 계단 3 →
    // 반 블록 3 → 담장 1 순이다(범위 판정 하나면 isStairs/isSlab/isWall 이 끝난다).
    //
    // 담장은 **프리즈머린 하나뿐**이다. 바닐라 1.21.4 에는 {@code prismarine_wall} 만
    // 있고 {@code prismarine_brick_wall}·{@code dark_prismarine_wall} 은 존재하지 않는다
    // (담장이 있는 재질 목록: cobblestone·mossy_cobblestone·stone_brick·mossy_stone_brick·
    // granite·diorite·andesite·sandstone·red_sandstone·brick·mud_brick·nether_brick·
    // red_nether_brick·end_stone_brick·blackstone·polished_blackstone(+brick)·deepslate 3종·
    // tuff 3종·**prismarine**). 없는 담장을 만들어 내지 않는다.
    /** 마른 스펀지. 바닐라 0.6 / 0.6 · 도구 요구 없음(mineable/hoe) · 자기 자신 드랍. */
    public static final int SPONGE = 934;
    /** prismarine. 1.5 / 6.0 · 곡괭이 티어 1 · 자기 자신 드랍. */
    public static final int PRISMARINE = 935;
    public static final int PRISMARINE_BRICKS = 936;
    public static final int DARK_PRISMARINE = 937;
    /**
     * sea_lantern. 바닐라 광량 15 · destroy_time 0.3 · explosion_resistance 0.3 ·
     * 도구 요구 없음. 실크 터치 없이 부수면 프리즈머린 수정 2~3(행운 보정 상한 5)을,
     * 실크 터치로 부수면 자기 자신을 떨군다.
     */
    public static final int SEA_LANTERN = 938;
    public static final int PRISMARINE_STAIRS = 939;
    public static final int PRISMARINE_BRICK_STAIRS = 940;
    public static final int DARK_PRISMARINE_STAIRS = 941;
    public static final int PRISMARINE_SLAB = 942;
    public static final int PRISMARINE_BRICK_SLAB = 943;
    public static final int DARK_PRISMARINE_SLAB = 944;
    public static final int PRISMARINE_WALL = 945;

    // ── [QUARTZ] 석영 계열 961–975(MC Java 1.21.4). ────────────────────────
    // 바닐라 원천은 **네더 석영 광석**(네더 전용)이라 이 게임에는 원천이 없다. 그래서
    // 앞선 [POTION] 블레이즈 막대·네더 사마귀 선례를 그대로 따라 재료(순수 아이템
    // {@link #QUARTZ} 961)를 등록하고 **획득 경로만** 신설한다(레이드 승리 보상 풀 +
    // 던전 탐험 상자). 제작 사슬은 바닐라 recipes/*.json 그대로이고 재료 대체는 없다.
    //
    // 물성은 계열 전체가 한 값이다: destroy_time 0.8 · explosion_resistance 0.8 ·
    // mineable/pickaxe(needs_*_tool 태그 없음 → 이 저장소 티어 1) · 자기 자신 드랍 ·
    // SoundType.STONE. 매끄러운 석영도 제련 산출이지만 바닐라 값이 같은 0.8 / 0.8 이다
    // (매끄러운 사암이 원석보다 단단해지는 것과 달리 석영은 변하지 않는다).
    //
    // 배치는 앞 석재·프리즈머린 계열과 같이 **형상군이 연속 구간**이다:
    // 완전 큐브 7(962~968) → 계단 2(969~970) → 반 블록 2(971~972) → 담장 3(973~975).
    //
    // 전부 제작 전용(placement-only)이라 지형 생성기·runtime overlay·구조물이 만들지
    // 않는다(golden terrain 해시 불변).
    /** 네더 석영 아이템. 이 저장소에는 광석이 없어 획득 경로가 divergence 다(MC-REFERENCE). */
    public static final int QUARTZ = 961;
    /** quartz_block. 0.8 / 0.8 · 곡괭이 티어 1 · 자기 자신 드랍. */
    public static final int QUARTZ_BLOCK = 962;
    public static final int CHISELED_QUARTZ_BLOCK = 963;
    // 석영 기둥은 통나무와 같이 **축이 별도 ID** 다(LOG/LOG_X/LOG_Z 규약). 바닐라는
    // axis property 지만 이 저장소는 축을 state 가 아니라 ID 로 잡은 선례가 정본이다.
    public static final int QUARTZ_PILLAR = 964;
    public static final int QUARTZ_PILLAR_X = 965;
    public static final int QUARTZ_PILLAR_Z = 966;
    /** smooth_quartz. 석영 블록 제련 산출이며 바닐라 물성은 원석과 같은 0.8 / 0.8 이다. */
    public static final int SMOOTH_QUARTZ = 967;
    public static final int QUARTZ_BRICKS = 968;
    public static final int QUARTZ_STAIRS = 969;
    public static final int SMOOTH_QUARTZ_STAIRS = 970;
    public static final int QUARTZ_SLAB = 971;
    public static final int SMOOTH_QUARTZ_SLAB = 972;
    // ── 담장 3종은 **바닐라에 없는 WebCraft 고유 추가**다(MC-REFERENCE divergence). ──
    // 수치를 지어내지 않고 기존 담장 계약(형상 · 제작 6→6 · 물성은 모재와 동일)만
    // 그대로 적용한다. 사용자 명시 지시로 들어온 항목이다.
    public static final int QUARTZ_WALL = 973;
    public static final int SMOOTH_QUARTZ_WALL = 974;
    public static final int QUARTZ_BRICK_WALL = 975;

    /**
     * 석영 기둥 축 정본 표. 인덱스는 축(0=Y, 1=X, 2=Z)이며 {@link #quartzPillar(int)} 가 이
     * 표만 읽는다. 벗긴 원목 표와 같은 이유로 산술 대신 표를 정본으로 둔다 — 그러지 않으면
     * 축 변형 두 상수가 어디에서도 참조되지 않는 "선언만 있는 ID" 가 되어 재배정 사고를
     * 잡을 수 없다.
     */
    public static final int[] QUARTZ_PILLAR_BY_AXIS = {
            QUARTZ_PILLAR, QUARTZ_PILLAR_X, QUARTZ_PILLAR_Z,
    };

    // ── [COPPER] 구리 계열 986–1064(MC Java 1.21.4). ───────────────────────────
    //
    // 기존 4블록 {@link #COPPER_BLOCK}~{@link #OXIDIZED_COPPER}(375~378)와 그 산화 랜덤틱은
    // 이미 있었다. 이 트랙은 바닐라 구리 **전 계열**을 채우고, 산화 표를 하드코딩 범위가 아니라
    // 정본 표(COPPER_OXIDATION_FAMILIES)로 일반화한다.
    //
    // ■ ID 배치 규약 — **각 계열은 산화 4단계가 연속 ID** 다.
    //   순서는 언제나 unaffected → exposed → weathered → oxidized 이며, 이는 기존
    //   375~378 배치와 같은 순서다. 산화 랜덤틱이 block + 1 산술 하나로 다음 단계를 구하는
    //   것(RandomTickSystem.weatherCopper)이 이 배치에 매여 있으므로, 계열 안에 다른 ID 를
    //   끼워 넣거나 순서를 뒤집으면 산화가 엉뚱한 블록으로 간다.
    //
    // ■ 밀랍(waxed) 대응은 **전 계열**이다(기존 4블록 포함). 밀랍 블록은 바닐라에서
    //   ChangeOverTimeBlock 이 아니라 산화하지 않는다 — nextOxidationStage 가 밀랍 구간에
    //   대해 -1 을 돌려주는 것이 그 표현이다.
    //
    // ■ 물성은 계열 전체가 한 값이다: destroy_time 3.0 · explosion_resistance 6.0 ·
    //   needs_stone_tool(이 저장소 티어 2) · 자기 자신 드랍 · SoundType.COPPER.
    //   밀랍 변형도 바닐라와 같이 물성이 원본과 동일하다.
    //
    // ■ 전부 제작 전용(placement-only)이라 지형 생성기·runtime overlay·구조물이 만들지
    //   않는다(golden terrain 해시 불변). 1065~1075 는 이 트랙 예약 공백이라 되메우지 않는다.

    /**
     * 밀랍(honeycomb). 벌이 없어 바닐라 원천(벌집/벌집 상자)이 이 게임에 없으므로 앞선
     * [POTION] 블레이즈 막대·[QUARTZ] 네더 석영 선례대로 재료만 등록하고 <b>획득 경로를
     * 신설</b>한다(레이드 승리 보상 풀 + 던전 탐험 상자). 순수 아이템이라 월드 블록이 아니다.
     */
    public static final int HONEYCOMB = 986;

    // ── 산화 4단계 계열(밀랍 없음) 987–1022 ──────────────────────
    /** cut_copper. 구리 블록 4 → 잘린 구리 4(바닐라 cut_copper.json). */
    public static final int CUT_COPPER = 987;
    public static final int EXPOSED_CUT_COPPER = 988;
    public static final int WEATHERED_CUT_COPPER = 989;
    public static final int OXIDIZED_CUT_COPPER = 990;
    public static final int CUT_COPPER_STAIRS = 991;
    public static final int EXPOSED_CUT_COPPER_STAIRS = 992;
    public static final int WEATHERED_CUT_COPPER_STAIRS = 993;
    public static final int OXIDIZED_CUT_COPPER_STAIRS = 994;
    public static final int CUT_COPPER_SLAB = 995;
    public static final int EXPOSED_CUT_COPPER_SLAB = 996;
    public static final int WEATHERED_CUT_COPPER_SLAB = 997;
    public static final int OXIDIZED_CUT_COPPER_SLAB = 998;
    /** chiseled_copper. 1.21 에서 추가된 계열이며 잘린 구리 반 블록 2 를 세로로 쌓아 만든다. */
    public static final int CHISELED_COPPER = 999;
    public static final int EXPOSED_CHISELED_COPPER = 1000;
    public static final int WEATHERED_CHISELED_COPPER = 1001;
    public static final int OXIDIZED_CHISELED_COPPER = 1002;
    /**
     * copper_grate. 바닐라 noOcclusion 격자라 풀 큐브 부피를 차지하되 이웃 면을 가리지 않는
     * 컷아웃이다(빛과 시야가 통과한다).
     */
    public static final int COPPER_GRATE = 1003;
    public static final int EXPOSED_COPPER_GRATE = 1004;
    public static final int WEATHERED_COPPER_GRATE = 1005;
    public static final int OXIDIZED_COPPER_GRATE = 1006;
    /**
     * copper_bulb(소등). 바닐라는 lit·powered 가 blockstate 속성이지만 이 저장소의 블록광
     * 방출표(BLOCK_LIGHT_EMISSION)는 <b>ID 색인</b>이라 state 로는 광량을 바꿀 수 없다.
     * 그래서 점화 화로 선례({@link #FURNACE_LIT})와 같이 <b>ID 쌍</b>으로 두고, 정규화는
     * {@link #copperBulbUnlit(int)} 한 곳이 맡는다(FurnaceVariant.baseBlockId 와 같은 역할).
     */
    public static final int COPPER_BULB = 1007;
    public static final int EXPOSED_COPPER_BULB = 1008;
    public static final int WEATHERED_COPPER_BULB = 1009;
    public static final int OXIDIZED_COPPER_BULB = 1010;
    /** copper_bulb(점등). 산화 단계별 바닐라 광량 15 / 12 / 8 / 4 를 낸다. */
    public static final int COPPER_BULB_LIT = 1011;
    public static final int EXPOSED_COPPER_BULB_LIT = 1012;
    public static final int WEATHERED_COPPER_BULB_LIT = 1013;
    public static final int OXIDIZED_COPPER_BULB_LIT = 1014;
    public static final int COPPER_DOOR = 1015;
    public static final int EXPOSED_COPPER_DOOR = 1016;
    public static final int WEATHERED_COPPER_DOOR = 1017;
    public static final int OXIDIZED_COPPER_DOOR = 1018;
    public static final int COPPER_TRAPDOOR = 1019;
    public static final int EXPOSED_COPPER_TRAPDOOR = 1020;
    public static final int WEATHERED_COPPER_TRAPDOOR = 1021;
    public static final int OXIDIZED_COPPER_TRAPDOOR = 1022;

    // ── 밀랍 대응 1023–1062. 순서는 위 비밀랍 계열과 **정확히 같다**(오프셋 대응의 전제). ──
    public static final int WAXED_COPPER_BLOCK = 1023;
    public static final int WAXED_EXPOSED_COPPER = 1024;
    public static final int WAXED_WEATHERED_COPPER = 1025;
    public static final int WAXED_OXIDIZED_COPPER = 1026;
    public static final int WAXED_CUT_COPPER = 1027;
    public static final int WAXED_EXPOSED_CUT_COPPER = 1028;
    public static final int WAXED_WEATHERED_CUT_COPPER = 1029;
    public static final int WAXED_OXIDIZED_CUT_COPPER = 1030;
    public static final int WAXED_CUT_COPPER_STAIRS = 1031;
    public static final int WAXED_EXPOSED_CUT_COPPER_STAIRS = 1032;
    public static final int WAXED_WEATHERED_CUT_COPPER_STAIRS = 1033;
    public static final int WAXED_OXIDIZED_CUT_COPPER_STAIRS = 1034;
    public static final int WAXED_CUT_COPPER_SLAB = 1035;
    public static final int WAXED_EXPOSED_CUT_COPPER_SLAB = 1036;
    public static final int WAXED_WEATHERED_CUT_COPPER_SLAB = 1037;
    public static final int WAXED_OXIDIZED_CUT_COPPER_SLAB = 1038;
    public static final int WAXED_CHISELED_COPPER = 1039;
    public static final int WAXED_EXPOSED_CHISELED_COPPER = 1040;
    public static final int WAXED_WEATHERED_CHISELED_COPPER = 1041;
    public static final int WAXED_OXIDIZED_CHISELED_COPPER = 1042;
    public static final int WAXED_COPPER_GRATE = 1043;
    public static final int WAXED_EXPOSED_COPPER_GRATE = 1044;
    public static final int WAXED_WEATHERED_COPPER_GRATE = 1045;
    public static final int WAXED_OXIDIZED_COPPER_GRATE = 1046;
    public static final int WAXED_COPPER_BULB = 1047;
    public static final int WAXED_EXPOSED_COPPER_BULB = 1048;
    public static final int WAXED_WEATHERED_COPPER_BULB = 1049;
    public static final int WAXED_OXIDIZED_COPPER_BULB = 1050;
    public static final int WAXED_COPPER_BULB_LIT = 1051;
    public static final int WAXED_EXPOSED_COPPER_BULB_LIT = 1052;
    public static final int WAXED_WEATHERED_COPPER_BULB_LIT = 1053;
    public static final int WAXED_OXIDIZED_COPPER_BULB_LIT = 1054;
    public static final int WAXED_COPPER_DOOR = 1055;
    public static final int WAXED_EXPOSED_COPPER_DOOR = 1056;
    public static final int WAXED_WEATHERED_COPPER_DOOR = 1057;
    public static final int WAXED_OXIDIZED_COPPER_DOOR = 1058;
    public static final int WAXED_COPPER_TRAPDOOR = 1059;
    public static final int WAXED_EXPOSED_COPPER_TRAPDOOR = 1060;
    public static final int WAXED_WEATHERED_COPPER_TRAPDOOR = 1061;
    public static final int WAXED_OXIDIZED_COPPER_TRAPDOOR = 1062;

    /**
     * lightning_rod. 바닐라와 같이 <b>방향 설치</b>(6면)이고 state 는 자수정 싹의 facing
     * 어휘(0=+Y,1=-Y,2=-Z,3=+X,4=+Z,5=-X)를 그대로 쓴다 — 새 방위 규약을 만들지 않는다.
     * 물성은 구리 계열과 같은 3.0 / 6.0 이다. 낙뢰 연동은 이 웨이브 범위 밖이고, 이 ID 와
     * 산화 정본 표만 다음 웨이브에 노출한다.
     */
    public static final int LIGHTNING_ROD = 1063;
    /**
     * 구리 랜턴. <b>바닐라 1.21.4 에 없는 WebCraft 고유 추가</b>다(MC-REFERENCE divergence).
     * 수치를 지어내지 않고 바닐라 lantern 계약(광량 15 · destroy_time 3.5 ·
     * explosion_resistance 3.5 · 곡괭이 필요 · SHAPE_STANDING = box(5,0,5,11,7,11))을 그대로
     * 쓰고 재질만 구리다. 산화하지 않는다(바닐라 랜턴이 ChangeOverTimeBlock 이 아니다).
     */
    public static final int COPPER_LANTERN = 1064;

    // ── [OPENABLE-METAL] 금속 개폐·격자 계열 1370~1379 ────────────────────────
    //
    // 이 트랙에 배정된 ID 구간은 1370~1399 이고 1380~1399 는 예약 공백이다. 값은
    // append-only 순서상 유황(1300~1310) 뒤지만 **선언은 여기**다 — 구리 창살 8종이
    // {@link #COPPER_OXIDATION_FAMILIES}/{@link #WAXED_COPPER_FAMILIES} 표에 합류해야 하고,
    // 자바는 클래스 변수 초기화식에서 뒤에 선언된 이름을 단순명으로 참조할 수 없다(JLS 8.3.3).
    // 표를 뒤로 옮기면 기존 열 계열의 행 순서가 흔들리므로, 상수 선언 쪽을 앞으로 당긴다.
    //
    // 철 문·철 다락문은 구리가 아니지만 같은 배정 구간이라 한 덩어리로 둔다(구간이 두 곳에
    // 흩어지면 예약 공백을 누가 소유하는지 알 수 없게 된다).

    /**
     * 철 문(iron_door). 바닐라 {@code DoorBlock} 이라 형상·2셀 설치·state 어휘가 목재 문과
     * 완전히 같고, destroy_time 5.0 · explosion_resistance 5.0 · 곡괭이 필요만 다르다. [A]
     *
     * <p><b>divergence</b>: 바닐라 철 문은 {@code BlockSetType.IRON.canOpenByHand()==false} 라
     * 레드스톤 신호로만 열린다. 이 저장소에는 레드스톤이 없어 그 계약을 그대로 옮기면 문이
     * <b>영원히 열리지 않는다</b> — 획득 경로만 있고 쓸 수 없는 블록이 된다. 그래서 구리 전구
     * (바닐라도 레드스톤 전용 토글)가 이미 낸 선례를 그대로 따라 <b>우클릭 개폐</b>를 허용한다.
     * 대신 그 밖의 모든 것(형상·소리·2셀 원자 설치·좀비가 부수지 못함)은 바닐라 그대로다.
     * MC-REFERENCE 에 기록한다.
     */
    public static final int IRON_DOOR = 1370;
    /**
     * 철 다락문(iron_trapdoor). 바닐라 {@code TrapDoorBlock} 이라 목재 다락문과 형상·state 가
     * 같고 destroy_time 5.0 · explosion_resistance 5.0 · 곡괭이 필요만 다르다. [A]
     * 개폐 divergence 는 {@link #IRON_DOOR} 과 같은 근거다.
     */
    public static final int IRON_TRAPDOOR = 1371;
    /**
     * 구리 창살(copper_bars, MC Java 1.21.9 "Copper Age"). 바닐라도 철창과 같은
     * {@code IronBarsBlock} 이라 연결·형상·충돌 계약을 통째로 물려받고, 다른 것은 산화한다는
     * 것 하나다. destroy_time 5.0 · explosion_resistance 6.0 · 곡괭이 필요 — 철창과 같은 값이다.
     * 구리 계열 나머지(3.0/6.0)와 갈리는 것이 바닐라 그대로이므로 물성은 철창 쪽을 따른다. [A]
     *
     * <p>1372~1375 가 산화 4단계, 1376~1379 가 그 밀랍 대응이며 둘 다 4연속이라
     * 정본 표의 한 행씩으로 합류한다.
     */
    public static final int COPPER_BARS = 1372;
    public static final int EXPOSED_COPPER_BARS = 1373;
    public static final int WEATHERED_COPPER_BARS = 1374;
    public static final int OXIDIZED_COPPER_BARS = 1375;
    public static final int WAXED_COPPER_BARS = 1376;
    public static final int WAXED_EXPOSED_COPPER_BARS = 1377;
    public static final int WAXED_WEATHERED_COPPER_BARS = 1378;
    public static final int WAXED_OXIDIZED_COPPER_BARS = 1379;
    // 1380~1399 는 이 트랙의 예약 공백이라 되메우지 않는다.

    // ── [COPPER-CHAIN] 구리 사슬 1430–1453 ──────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1430~1459 이고 1454~1459 는 예약 공백이다. 1411~1419 는
    // [ZOMBIE-ANIMAL] 트랙 예약 공백, 1420~1429 는 다른 트랙 배정 구간이라 되메우지 않는다.
    //
    // 바닐라 1.21.9(Copper Age) {@code copper_chain} 은 산화 4단계 + 밀랍 대응을 가진 사슬이다.
    // 이 저장소는 사슬 축을 상태가 아니라 <b>ID</b> 로 표현하므로(철 사슬 1336~1338 이 이미 낸
    // 선례 — 원목·석영 기둥과 같은 문법) 필요한 ID 가 <b>축 3 × 산화 4 × 밀랍 2 = 24</b> 다.
    // 직전 트랙(OPENABLE-METAL)은 남은 예약 공백이 20 칸뿐이라 이 계열을 보류했고, 축을 상태로
    // 접어 8 로 줄이는 선택지는 "철 사슬과 구리 사슬이 서로 다른 축 문법을 갖는" 결과가 되어
    // 거부했다. 그래서 여기서는 <b>연속 24 ID 구간을 새로 배정</b>한다.
    //
    // 배치는 <b>축이 바깥, 산화가 안쪽</b>이다: {@code id = COPPER_CHAIN + wax*12 + axis*4 + stage}.
    // 산화 4단계가 안쪽에 있어야 각 계열이 4연속이 되고, 그래야 정본 표
    // {@link #COPPER_OXIDATION_FAMILIES} 의 한 행 = 4연속 ID 라는 전제와
    // {@link #nextOxidationStage(int)} 의 {@code id + 1} 산술이 성립한다. 축마다 한 행씩
    // 총 3행이 산화 표 <b>맨 끝에</b> 붙고, 대응하는 밀랍 3행이 밀랍 표 맨 끝에 붙는다.
    // 행을 끝에 붙였기 때문에 기존 계열의 packed index 가 한 칸도 밀리지 않고, 랜덤틱은 표를
    // 순회하지 않고 O(1) 조회만 하므로 <b>난수 소비 지점·횟수가 글자 그대로 같다</b>
    // (정적판 미러와 draw 순서 불변). 낙뢰 환원·도끼 긁기·밀랍 도포도 같은 표 조회 하나만
    // 보므로 코드 한 줄 없이 따라온다.
    //
    // 물성은 철 사슬 그대로다 — destroy_time 5.0 · explosion_resistance 6.0 ·
    // mineable/pickaxe 이고 needs_*_tool 태그가 없어 나무 곡괭이(티어 1)로 회수된다.
    // 구리 계열 공통(3.0 / 6.0 / needs_stone_tool)이 <b>아니다</b>: 구리 사슬은 재질만 구리이고
    // 형상·물성은 {@code ChainBlock} 에서 물려받는다 — 구리 창살이 {@code IronBarsBlock} 에서
    // 물려받는 것과 같은 이유다. [B] 위키 «Copper Chain»(1.21.9).
    //
    // 제작은 바닐라 {@code copper_chain.json} 그대로 구리 조각 1 + 구리 주괴 1 + 구리 조각 1 을
    // 세로로 → 1 이다(철 사슬 {@code chain.json} 과 같은 틀, 재료만 구리). 산화 세 단계와
    // 밀랍 여덟… 열두 종에는 제작식이 없다 — 산화·밀랍 도포로만 얻는다.

    /** copper_chain[axis=y]. 산화 미진행 · 비밀랍. 이 계열 24 ID 의 기준점이다. */
    public static final int COPPER_CHAIN = 1430;
    public static final int EXPOSED_COPPER_CHAIN = 1431;
    public static final int WEATHERED_COPPER_CHAIN = 1432;
    public static final int OXIDIZED_COPPER_CHAIN = 1433;
    /** copper_chain[axis=x]. 축마다 산화 4연속 한 벌을 따로 가진다. */
    public static final int COPPER_CHAIN_X = 1434;
    public static final int EXPOSED_COPPER_CHAIN_X = 1435;
    public static final int WEATHERED_COPPER_CHAIN_X = 1436;
    public static final int OXIDIZED_COPPER_CHAIN_X = 1437;
    /** copper_chain[axis=z]. */
    public static final int COPPER_CHAIN_Z = 1438;
    public static final int EXPOSED_COPPER_CHAIN_Z = 1439;
    public static final int WEATHERED_COPPER_CHAIN_Z = 1440;
    public static final int OXIDIZED_COPPER_CHAIN_Z = 1441;
    /** waxed_copper_chain[axis=y]. 밀랍 세 벌은 비밀랍 세 벌과 <b>같은 순서</b>다. */
    public static final int WAXED_COPPER_CHAIN = 1442;
    public static final int WAXED_EXPOSED_COPPER_CHAIN = 1443;
    public static final int WAXED_WEATHERED_COPPER_CHAIN = 1444;
    public static final int WAXED_OXIDIZED_COPPER_CHAIN = 1445;
    /** waxed_copper_chain[axis=x]. */
    public static final int WAXED_COPPER_CHAIN_X = 1446;
    public static final int WAXED_EXPOSED_COPPER_CHAIN_X = 1447;
    public static final int WAXED_WEATHERED_COPPER_CHAIN_X = 1448;
    public static final int WAXED_OXIDIZED_COPPER_CHAIN_X = 1449;
    /** waxed_copper_chain[axis=z]. 이 계열의 마지막 ID 이자 월드 블록 high-water 다. */
    public static final int WAXED_COPPER_CHAIN_Z = 1450;
    public static final int WAXED_EXPOSED_COPPER_CHAIN_Z = 1451;
    public static final int WAXED_WEATHERED_COPPER_CHAIN_Z = 1452;
    public static final int WAXED_OXIDIZED_COPPER_CHAIN_Z = 1453;

    /**
     * [CHEST-FAMILY] 구리 상자(Java 1.21.9 "The Copper Age" 실재 [B]). 바닐라 제작식 그대로
     * <b>가운데 상자 1 을 구리 주괴 8 로 둘러싼다</b>. 단일 27칸 · 큰 구리 상자 54칸으로
     * 일반 상자와 같은 좌우 짝을 이룬다.
     *
     * <p>물성은 구리 계열 공통이다 — destroy_time 3.0 · explosion_resistance 6.0 ·
     * <b>돌 곡괭이 이상</b>이라야 자기 자신을 떨군다.
     *
     * <p>산화 4단계(<b>연속 ID</b> 여야 한다)와 밀랍 4종이
     * {@link #COPPER_OXIDATION_FAMILIES} / {@link #WAXED_COPPER_FAMILIES} 표 <b>맨 끝</b>에
     * 한 행씩 합류하는 것만으로 랜덤틱 산화 · 낙뢰 환원 · 도끼 긁기 · 밀랍 도포가 전부
     * 따라온다(구리 사슬 트랙이 낸 선례 — 표 조회가 유일한 원천이다).
     *
     * <p><b>divergence [C]</b>: 구리 골렘이 없으므로 "구리 골렘이 구리 상자에 아이템을 넣고
     * 뺀다"는 계약에 넣지 않는다. 또한 <b>산화 단계가 다른 두 구리 상자가 짝을 이루는가</b>는
     * 위키 문장이 갈려 [B] 로 확인하지 못했다 — 이 저장소는 {@link #chestPairs(int, int)} 로
     * <b>같은 ID 끼리만 짝을 이룬다</b>고 못박는다(덫 상자/일반 상자가 섞이지 않는 것과 같은
     * 규칙 하나로 끝난다).
     */
    public static final int COPPER_CHEST = 1811;
    /** 약간 녹슨 구리 상자. 산화 1단계. */
    public static final int EXPOSED_COPPER_CHEST = 1812;
    /** 녹슨 구리 상자. 산화 2단계. */
    public static final int WEATHERED_COPPER_CHEST = 1813;
    /** 산화된 구리 상자. 산화 3단계 — 더 진행하지 않는다. */
    public static final int OXIDIZED_COPPER_CHEST = 1814;
    /** 밀랍 바른 구리 상자. {@link #COPPER_CHEST} 와 같은 좌표(산화 0)다. */
    public static final int WAXED_COPPER_CHEST = 1815;
    /** 밀랍 바른 약간 녹슨 구리 상자. */
    public static final int WAXED_EXPOSED_COPPER_CHEST = 1816;
    /** 밀랍 바른 녹슨 구리 상자. */
    public static final int WAXED_WEATHERED_COPPER_CHEST = 1817;
    /**
     * 밀랍 바른 산화된 구리 상자. 이 구간의 마지막 ID 이자 현재
     * {@link #BLOCK_ID_HIGH_WATER} · {@link #PROTOCOL_ID_HIGH_WATER} 다.
     */
    public static final int WAXED_OXIDIZED_COPPER_CHEST = 1818;

    // ── [CHEST-FAMILY] 여기까지가 구리 상자 8종 ─────────────────────────────────


    // ── [BRIMSTONE] 황린 잠복자 · 화염 저항 사슬 1460~1466 ────────────────────
    //
    // 이 트랙에 배정된 구간은 1460~1479 이고 1467~1479 는 예약 공백이다. 1454~1459 는
    // [COPPER-CHAIN] 트랙의 예약 공백이라 되메우지 않는다.
    //
    // 이 구간이 담는 것은 <b>서로 한 고리로 묶인 넷</b>이다: 유황 간헐천 하부의 마그마 블록 →
    // 그 위에 사는 황린 잠복자 → 잠복자가 떨구는 마그마 크림 → 마그마 크림이 만드는 화염 저항
    // 물약. 서사와 획득 경로가 한 줄로 이어지는 것이 이 배정의 이유다.
    //
    // 근거 등급 [A] = 바닐라 클래스/데이터, [B] = minecraft.wiki 본문, [C] = 자체 계약.

    /**
     * magma_block. <b>바닐라 실존 블록</b>이며 물성을 원문 그대로 쓴다 —
     * destroy_time <b>0.5</b> · explosion_resistance <b>0.5</b> · mineable/pickaxe ·
     * 광량 <b>3</b> · 밟으면 피해(vanilla {@code hot_floor}). 이 저장소는 별도 사인을 만들지
     * 않고 모닥불·화재 접촉이 이미 쓰는 {@code "in_fire"} 계약을 그대로 받는다.
     * [B] 위키 «Magma Block».
     *
     * <p><b>획득 경로 divergence</b>: 바닐라는 네더 용암 바다 주변에서 캐지만 이 저장소에는
     * 네더가 없다. 그래서 <b>유황 간헐천 분출구 하부</b>가 유일한 자연 산지다 — 유황 리서치가
     * "분출체 아래 마그마 블록이 필요하다" 고 적은 실사양을 이제야 지형에 세우는 것이라,
     * 이 추가는 강력한 유황의 공식 마그마 기반 분출 조건을 성립시킨다.
     */
    public static final int MAGMA = 1460;
    /**
     * 황린 갑각 트로피. 잠복자가 1% 로 떨구는 <b>설치 가능한 장식 블록</b>이며 바닐라 대응이
     * 없는 [C] 창작이다. 물성은 유황 수정 계열과 같은 destroy_time 1.5 ·
     * explosion_resistance 6.0 · mineable/pickaxe 이고, 잔열이 남아 광량 <b>5</b> 를 낸다
     * (유황 군집과 같은 값 — 새 광량 등급을 만들지 않는다).
     */
    public static final int BRIMSTONE_CARAPACE_TROPHY = 1461;
    /**
     * magma_cream. <b>바닐라 실존 아이템</b>이며 화염 저항 물약의 유일한 재료다. 순수
     * 아이템이라 월드 블록 배열에 들어가지 않는다(BLOCK_ID_HIGH_WATER 를 올리지 않는다).
     *
     * <p><b>획득 경로 divergence</b>: 바닐라의 원천은 마그마 큐브(네더) 드랍과 블레이즈 가루 +
     * 슬라임볼 제작이다. 이 저장소에는 네더가 없어 <b>황린 잠복자 처치 40%</b> 가 유일한
     * 경로다 — {@link #BLAZE_ROD} · {@link #NETHER_WART} 가 이미 낸 "재료 자체는 바닐라
     * 원본이고 획득 경로만 이 저장소 것" 선례를 그대로 잇는다. 스택 64.
     */
    public static final int MAGMA_CREAM = 1462;
    /**
     * 화염 저항 물약(3:00). <b>바닐라 레시피 원본 그대로</b> 어색한 물약 + 마그마 크림이다.
     * 순수 아이템이며, 이 네 종은 마시는 → 투척 <b>오프셋 2</b> 라는 자기 산술 규약을 갖는다
     * (기본 다섯 종의 ±5 · 강화 여덟 종의 ±8 과 같은 꼴이다).
     */
    public static final int POTION_FIRE_RESISTANCE = 1463;
    /** 화염 저항 물약 (연장) 8:00. 화염 저항 물약 + 레드스톤(바닐라 그대로). */
    public static final int POTION_FIRE_RESISTANCE_LONG = 1464;
    /** 투척용 화염 저항 물약. 마시는 쪽과 나열 순서가 같아 {@code id + 2} 로 대응한다. */
    public static final int SPLASH_POTION_FIRE_RESISTANCE = 1465;
    /** 투척용 화염 저항 물약 (연장). */
    public static final int SPLASH_POTION_FIRE_RESISTANCE_LONG = 1466;

    // ── [WAVE-86-97] 신종 사망 드랍의 순수 아이템 1480~1481 ────────────────────────
    //
    // 1467~1479 는 [BRIMSTONE] 트랙의 예약 공백이라 되메우지 않고 그 뒤에 append 한다.
    // 1482~1489 는 이 트랙의 예약 공백이다. 워든의 스컬크 촉매는 이미
    // {@link #SCULK_CATALYST}=1202 로 있으므로 새 ID 를 만들지 않는다 — 같은 블록을 두 ID 로
    // 만들면 저장 월드가 갈라진다.
    /**
     * breeze_rod. <b>바닐라 실존 아이템</b>(1.21 {@code minecraft:breeze_rod})이며 순수
     * 아이템이라 월드 블록 배열에 들어가지 않는다(BLOCK_ID_HIGH_WATER 를 올리지 않는다).
     *
     * <p>획득 경로는 바닐라와 <b>같다</b>: 브리즈 처치 드랍 하나뿐이다(1.21
     * {@code entities/breeze.json} → {@code breeze_rod} 1~2). 스택 64.
     */
    public static final int BREEZE_ROD = 1480;
    /**
     * phantom_membrane. <b>바닐라 실존 아이템</b>이며 순수 아이템이다. 획득 경로는 바닐라와
     * 같은 <b>팬텀 처치</b>(플레이어가 죽였을 때 0~1)뿐이다 — 바닐라의 다른 원천인 낚시
     * 전리품·주민 거래는 이 웨이브 밖이다. 스택 64.
     */
    public static final int PHANTOM_MEMBRANE = 1481;

    // ── [PALE-GARDEN] 창백한 정원 계열 월드 블록 1490~1507 ────────────────────────
    //
    // 이 트랙에 배정된 구간은 1490~1529 이고 1508~1529 는 예약 공백이다. 1482~1489 는
    // [WAVE-86-97] 트랙 예약 공백, 1530~ 은 [POPLAR] 트랙 배정 구간이라 되메우지 않는다.
    // 열여덟 전부 <b>월드 블록</b>이고 순수 아이템은 없다(축 변형 넷을 뺀 열넷이 그대로
    // 자기 자신의 아이템이다). 값은 {@link #BLOCK_ID_HIGH_WATER} 보다 작으므로 이 트랙은
    // 상한을 올리지 않는다. client 사본은 client/src/world/blocks.ts 의 같은 절이다.
    //
    // 근거 등급: <b>[A] 원문 JSON</b>. 이 저장소가 고정한 1.21.4 데이터 스냅샷
    // (docs/research/mc-vanilla-1214/) 안에 창백한 정원 계열이 전량 있다 — 바이옴 1 ·
    // configured_feature 5 · placed_feature 5. 발췌 핀은
    // docs/research/mc-pale-garden-1214.md 이고, 물성표(§5)만 [B] 위키 등급이다
    // (destroy_time / explosion_resistance 는 데이터팩이 아니라 Java 하드코딩이다).
    //
    // <b>창백한 참나무의 물성은 기존 여덟 수종과 한 글자도 다르지 않다</b> — 그래서 이 절은
    // 새 경도 등급을 하나도 만들지 않고 기존 목재 계약을 그대로 물려받는다.
    //
    // <b>ID 가 종별 목재 구간(601~649)·벗긴 원목 구간(871~894)에 이어붙지 않는 이유</b>:
    // 벗긴 원목은 {@code STRIPPED_OAK_LOG + species*3 + axis} 산술이 정본인데 아홉 번째
    // 수종의 자리(895~)는 이미 다른 트랙 예약 공백이다. 여덟 수종 표를 늘리면 표와 산술이
    // 조용히 어긋나 재배정 사고를 아무도 잡지 못하므로, 창백한 참나무는
    // {@link #LOG_BY_WOOD_SPECIES} · {@link #STRIPPED_LOG_BY_SPECIES_AND_AXIS}
    // <b>두 표에 들어가지 않고</b> 형상군 술어와 벗기기 변환이 명시 갈래 하나씩을 갖는다.
    // ([POPLAR] 트랙이 1530~ 에서 같은 판단을 내렸다 — 두 트랙이 같은 형태를 쓴다.)
    /**
     * pale_oak_log(y축). 바닐라 destroy_time 2.0 · explosion_resistance 2.0 · mineable/axe
     * (도구 요구 없음) · WOOD 음향 · 자기 자신 드랍으로 다른 수종 통나무와 완전히 같다.
     *
     * <p>축은 참나무·자작나무 선례를 따라 <b>별도 ID</b> 다. 가문비나무 이하 여섯 수종은 축이
     * state 하위 2비트에 있지만 그 표현은 {@link #LOG_BY_WOOD_SPECIES} 인덱스에 묶여 있어
     * 표 밖 수종이 쓸 수 없다 — 별도 ID 쪽이 이 구간에서 유일하게 정합한 선택이다.
     */
    public static final int PALE_OAK_LOG = 1490;
    /** 창백한 참나무 원목(x축). */
    public static final int PALE_OAK_LOG_X = 1491;
    /** 창백한 참나무 원목(z축). */
    public static final int PALE_OAK_LOG_Z = 1492;
    /**
     * stripped_pale_oak_log(y축). 물성은 통나무와 같은 2.0/2.0 이고, 도끼 우클릭으로
     * 통나무에서 <b>축을 보존한 채</b> 변환된다(바닐라 {@code AxeItem.STRIPPABLES}).
     */
    public static final int STRIPPED_PALE_OAK_LOG = 1493;
    /** 벗긴 창백한 참나무 원목(x축). */
    public static final int STRIPPED_PALE_OAK_LOG_X = 1494;
    /** 벗긴 창백한 참나무 원목(z축). */
    public static final int STRIPPED_PALE_OAK_LOG_Z = 1495;
    /**
     * pale_oak_planks. 종별 판자 세트(601~607)와 같은 계약이고 값도 같다 —
     * destroy_time 2.0 · explosion_resistance 3.0 · mineable/axe · 가연.
     */
    public static final int PALE_OAK_PLANK = 1496;
    /** 창백한 참나무 계단. 2.0/3.0. */
    public static final int PALE_OAK_STAIRS = 1497;
    /** 창백한 참나무 반 블록. 2.0/3.0. state 0=bottom, 1=top, 2=double(기존 계약). */
    public static final int PALE_OAK_SLAB = 1498;
    /** 창백한 참나무 울타리. 2.0/3.0. */
    public static final int PALE_OAK_FENCE = 1499;
    /** 창백한 참나무 울타리 문. 2.0/3.0. */
    public static final int PALE_OAK_FENCE_GATE = 1500;
    /** 창백한 참나무 다락문. 문과 같이 destroy_time <b>3.0</b> 이다(바닐라 그대로). */
    public static final int PALE_OAK_TRAPDOOR = 1501;
    /** 창백한 참나무 문. destroy_time 3.0 · 2칸 구조 · 아래칸이 SOLID_BELOW 를 요구한다. */
    public static final int PALE_OAK_DOOR = 1502;
    /**
     * pale_oak_leaves. destroy_time 0.2 · 잎 붕괴 대상 · 묘목 5% / 막대 2% 드랍으로 다른
     * 수종 잎과 한 표다. 다만 <b>바이옴 틴트를 받지 않는 고정색</b>이다 — 바닐라의 창백한
     * 참나무 잎도 자작나무·가문비나무와 같이 {@code foliage_color} 를 쓰지 않는다(핀 §1a).
     */
    public static final int PALE_OAK_LEAVES = 1503;
    /** pale_oak_sapling. destroy_time 0 · DIRT_OR_GRASS_BELOW 지지 · 잎에서 5% 드랍. */
    public static final int PALE_OAK_SAPLING = 1504;
    /**
     * pale_moss_block. 바닐라 destroy_time 0.1 · mineable/hoe · MOSS 음향으로 기존 이끼
     * 블록({@link #MOSS_BLOCK})과 같은 값이고 색만 회녹색이다.
     *
     * <p><b>제작할 수 없다</b> — 바닐라도 같다(뼛가루로 퍼뜨리는 것뿐이다). 자연 산지에서만
     * 얻으므로 획득 경로는 창백한 정원 지대(후속 트랙, 핀 §6)가 소유한다.
     */
    public static final int PALE_MOSS_BLOCK = 1505;
    /**
     * pale_moss_carpet. 바닐라는 벽을 타고 오르는 다면(multiface) 블록이지만 이 저장소에는
     * 그 형상 문법이 없다. 기존 이끼 바닥({@link #MOSS_CARPET})과 같은 <b>높이 1/16
     * partial</b> 로 이식한다([C] divergence — 새 형상군 비용이 겉모습 차이보다 크다).
     * 창백한 이끼 블록 2 → 3 제작은 바닐라 배치 그대로다.
     */
    public static final int PALE_MOSS_CARPET = 1506;
    /**
     * pale_hanging_moss. 천장에 매달린 이끼 가닥이다. 이 저장소의 매달린 식생 표현인
     * 동굴 덩굴({@link #CAVE_VINES})과 같은 <b>cross 컷아웃</b>으로 이식하며 바닐라의
     * {@code tip} 상태(끝 가닥이 다른 텍스처)는 두지 않는다([C] divergence).
     */
    public static final int PALE_HANGING_MOSS = 1507;

    // ── [CREAKING] 크리킹 하트 · 수지 1508~1511 ────────────────────────────────────
    //
    // 창백한 정원 트랙(1490~1529)의 <b>같은 구간 이월분</b>이다. 1512~1529 는 여전히 이
    // 트랙의 예약 공백이라 되메우지 않는다. ID 정본은 이 파일이고 client {@code blocks.ts}
    // 는 사본이다. 근거 핀은 {@code docs/research/mc-pale-garden-1214.md} §7 이며 그 절은
    // 이 웨이브가 [B] minecraft.wiki «Creaking Heart» · «Resin Clump» · «Creaking»
    // (조회 2026-08-10)으로 다시 세운 것이다.
    //
    // <b>왜 자연 배치가 아니라 제작인가.</b> 바닐라의 크리킹 하트는 창백한 정원 나무 열 그루
    // 중 하나에 박혀 나오지만(핀 §3 의 {@code pale_oak_creaking} 0.1 갈래) 그 배치는 지형
    // lane 을 건드린다. 그리고 <b>바닐라 자신이 1.21.4(24w44a)에서 제작법을 함께 냈다</b> —
    // 창백한 참나무 원목 2 + 수지 블록 1 무형 제작이다. 그래서 이 웨이브는 재료를 대체하지
    // 않고 <b>바닐라 제작법을 그대로</b> 쓰고, 그 사슬의 뿌리인 수지 덩어리는 바닐라가 이미
    // 정한 획득처(대저택 상자 pool 2 · 가중치 50)를 쓴다. 그 자리는 이 저장소에
    // {@code ExplorationLoot.MANSION_SUPPLIES} 의 <b>EMPTY 자리표</b>로 이미 서 있었고
    // (주석에 "resin clump" 이라고 적혀 있다) 이 웨이브가 그것을 실물로 되돌린다 —
    // 마법이 부여된 황금 사과가 낸 선례와 같은 수술이라 확률 질량이 한 글자도 움직이지 않는다.

    /**
     * creaking_heart. 창백한 참나무 원목 사이에 끼워 두는 블록이며 <b>밤에 크리킹 하나를
     * 소환</b>한다. 바닐라 destroy_time <b>10.0</b> · explosion_resistance 10.0 · mineable/axe
     * 로 도끼 최적 블록 중 가장 단단하다([B] «Creaking Heart»).
     *
     * <p>이 ID 는 <b>비활성</b>(바닐라의 {@code uprooted} + {@code dormant})이고 활성은
     * {@link #CREAKING_HEART_ACTIVE} 라는 <b>ID 쌍</b>이다 — 화로 {@link #FURNACE}↔
     * {@link #FURNACE_LIT} 과 구리 전구 {@code lit} 쌍둥이가 이미 낸 표현 문법 그대로다.
     * 지시서는 "활성 상태 비트" 라고 적었지만 이 저장소에서 상태 바이트는 형상·단계에
     * 쓰이고 <b>겉모습 스왑은 ID 쌍</b>이 정본이라, 새 문법을 만들지 않는 쪽을 골랐다
     * (divergence 1 — 관측 가능한 동작은 같다).
     *
     * <p><b>축 속성을 두지 않는다</b>(divergence 2). 바닐라는 {@code axis} 를 설치 시점에
     * 굳히고 그 축의 창백한 참나무 원목 두 칸에 끼어야 dormant 가 된다. 이 저장소는 축을
     * 저장하지 않고 <b>매 틱 세 축을 다시 본다</b>({@code CreakingHeartRules.alignedAxis}).
     * 바닐라도 축을 이웃 원목에서 정하므로 관측 결과는 같고, 다른 축으로 원목을 다시
     * 쌓았을 때만 갈린다(바닐라는 uprooted, 여기는 다시 dormant).
     *
     * <p>광량은 <b>0 이다</b> — 활성 하트의 주황 심장은 발광 텍스처이지 광원이 아니다
     * ([B] «Creaking Heart» Luminant: No). 핀 §7 의 옛 서술("활성일 때 파동하는 주황빛")은
     * 겉모습을 광량으로 잘못 옮긴 것이라 이 웨이브가 바로잡았다.
     */
    public static final int CREAKING_HEART = 1508;
    /**
     * 활성(밤 · 정렬됨) 크리킹 하트. 겉모습만 다른 {@link #CREAKING_HEART} 의 쌍둥이이며
     * 드랍·경도·도구가 모두 같다. 직접 설치할 수 없고 낮/밤 사슬
     * ({@code CreakingHeartSystem})만 두 ID 를 맞바꾼다(FURNACE↔FURNACE_LIT 과 같은 계약).
     */
    public static final int CREAKING_HEART_ACTIVE = 1509;
    /**
     * resin_block(Block of Resin). 수지 덩어리 9 로 만드는 저장 블록이며 크리킹 하트 제작의
     * 재료다. 바닐라 물성표를 확보하지 못해 이 저장소는 <b>같은 9칸 저장 블록</b> 계약
     * (건초 더미·점토 블록과 같은 자리)을 쓴다 — destroy_time 1.0 · 도구 요구 없음([C]).
     */
    public static final int RESIN_BLOCK = 1510;
    /**
     * resin_clump. <b>순수 아이템</b>이다(설치할 수 없다). 바닐라는 벽면에 붙는 다면 블록
     * 이기도 하지만 이 저장소에는 그 형상 문법이 없고, 이 사슬에 필요한 것은 재료로서의
     * 아이템뿐이라 아이템만 등록한다([C] divergence — 창백한 이끼 바닥이 내린 것과 같은 판단).
     *
     * <p>획득처는 둘 다 바닐라 그대로다: 대저택 상자 pool 2(가중치 50 · 2~4개)와 크리킹
     * 하트를 실크터치 없이 캘 때의 1~3개(행운 레벨당 최대 +1).
     */
    public static final int RESIN_CLUMP = 1511;
    // 1512~1529 는 이 트랙의 예약 공백이라 되메우지 않는다.

    /** 크리킹 하트인가(비활성·활성 둘 다). 두 ID 는 겉모습만 다른 한 블록이다. */
    public static boolean isCreakingHeart(int id) {
        return id == CREAKING_HEART || id == CREAKING_HEART_ACTIVE;
    }

    /**
     * 크리킹 하트를 캘 때 인벤토리에 들어가는 대표 ID. 활성 하트도 비활성으로 접힌다
     * ({@link #FURNACE_LIT} 이 {@link #FURNACE} 로 접히는 것과 같은 규약).
     */
    public static int creakingHeartItem(int id) {
        return isCreakingHeart(id) ? CREAKING_HEART : AIR;
    }

    /**
     * 창백한 참나무 가공 계열 7종(1496~1502 — 판자·계단·반·울타리·울타리문·다락문·문)인가.
     * client {@code blocks.ts} 의 {@code isPaleOakBuildingBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isPaleOakBuildingBlock(int id) {
        return id >= PALE_OAK_PLANK && id <= PALE_OAK_DOOR;
    }

    /** 창백한 참나무 원목(축 3종, 1490~1492)인가. 벗긴 원목은 포함하지 않는다. */
    public static boolean isPaleOakLog(int id) {
        return id >= PALE_OAK_LOG && id <= PALE_OAK_LOG_Z;
    }

    /** 벗긴 창백한 참나무 원목(축 3종, 1493~1495)인가. */
    public static boolean isStrippedPaleOakLog(int id) {
        return id >= STRIPPED_PALE_OAK_LOG && id <= STRIPPED_PALE_OAK_LOG_Z;
    }

    /**
     * 창백한 참나무 원목·벗긴 원목의 축(0=Y, 1=X, 2=Z). 둘 다 아니면 {@code -1}.
     * 축이 ID 에 있는 다른 계열(참나무·자작나무 통나무·석영 기둥·사슬)과 같은 규약이다.
     */
    public static int paleOakLogAxis(int id) {
        if (isPaleOakLog(id)) return id - PALE_OAK_LOG;
        if (isStrippedPaleOakLog(id)) return id - STRIPPED_PALE_OAK_LOG;
        return -1;
    }

    /**
     * 창백한 참나무 원목·벗긴 원목의 인벤토리 아이템 ID(축 변형은 y축 대표 ID 로 접힌다).
     * 창백한 원목 계열이 아니면 {@link #AIR}.
     *
     * <p>창백한 참나무는 여덟 수종 표({@link #LOG_BY_WOOD_SPECIES} ·
     * {@link #STRIPPED_LOG_BY_SPECIES_AND_AXIS})에 <b>들어가지 않는다</b> — 그 표의 정본
     * 산술은 {@code STRIPPED_OAK_LOG + species*3 + axis} 인데 아홉 번째 자리(895~)는 다른
     * 트랙 예약 공백이라, 표만 늘리면 표와 산술이 조용히 어긋나 재배정 사고를 아무도 잡지
     * 못한다. 그래서 명시 갈래를 쓴다.
     */
    public static int paleOakLogItem(int id) {
        if (isPaleOakLog(id)) return PALE_OAK_LOG;
        if (isStrippedPaleOakLog(id)) return STRIPPED_PALE_OAK_LOG;
        return AIR;
    }

    /**
     * 도끼로 창백한 참나무 원목을 벗긴 결과. 바닐라와 같이 <b>축을 보존</b>한다. 창백한
     * 원목이 아니면 {@code -1} — 호출부가 여덟 수종 경로({@link #strippedLogFor(int, int)})
     * 와 이 갈래를 명시적으로 나눌 수 있어야 하기 때문이다.
     */
    public static int strippedPaleOakLogFor(int blockId) {
        if (!isPaleOakLog(blockId)) return -1;
        return STRIPPED_PALE_OAK_LOG + (blockId - PALE_OAK_LOG);
    }

    // ── [CHEST-FAMILY] 상자류 컨테이너 재료 순수 아이템 1603·1604·1613 ─────────────
    //
    // 이 트랙에 배정된 구간은 1600~1629 이고, 그중 <b>이 절은 순수 아이템만</b> 선언한다
    // (덫 상자 1600 · 엔더 상자 1602 · 구리 상자 8종 1605~1612 는 월드 블록이라
    // {@link #BLOCK_ID_HIGH_WATER} 를 올린다 — 별도 절에서 등록한다). 1614~1629 는 이 트랙의
    // 예약 공백이다. 1482~1599 · 1630~1669 는 다른 트랙 배정 구간이라 되메우지 않는다.
    // 발췌 핀은 docs/research/mc-26x-chests.md 이고 client 사본은
    // client/src/world/blocks.ts 의 같은 절이다.
    //
    // 근거 등급: 전부 [B](위키 본문)다. 이 저장소의 고정 스냅샷(1.21.4-data-json)에는 지형
    // 입력만 있고 레시피·아이템 JSON 이 없어 [A] 원문을 인용할 수 없다.
    //
    // <b>원본 레시피 무결성</b>: 네 상자의 바닐라 제작식에 나오는 재료 중 이 저장소에 없던
    // 것만 여기서 신설한다. 재료를 다른 것으로 <b>대체하지 않는다</b> — 대신 없는 재료를
    // 등록하고 획득 경로를 새로 연다(밀랍 986 · 네더 석영 961 이 낸 선례).
    // <b>줄 갈고리는 새로 만들지 않는다</b>: 덫 상자의 바닐라 재료 {@code tripwire_hook} 은
    // 이미 {@link #TRIPWIRE_HOOK}=488 로 있다(낚시 전리품 구간). 같은 아이템을 두 ID 로
    // 만들면 저장 인벤토리가 갈라지므로 1601 은 배정하지 않고 예약 공백으로 남긴다.
    /**
     * blaze_powder. <b>바닐라 실존 아이템</b>이며 순수 아이템이다. 획득 경로는 바닐라와 같은
     * 제작 하나뿐이다 — 블레이즈 막대({@link #BLAZE_ROD}) 1 → 가루 2. 스택 64.
     *
     * <p>엔더의 눈 재료라 이 트랙이 신설하지만, 바닐라의 다른 소비처(양조 연료·마그마 크림)는
     * 이 웨이브 밖이다.
     */
    public static final int BLAZE_POWDER = 1603;
    /**
     * ender_eye. <b>바닐라 실존 아이템</b>이며 순수 아이템이다. 제작은 바닐라 그대로
     * 엔더 진주({@link #ENDER_PEARL}) 1 + 블레이즈 가루 1 → 1 이다. 스택 64.
     *
     * <p>이 저장소에는 엔드 차원도 요새도 없어 <b>던지는 용도가 없다</b>([C] divergence).
     * 유일한 소비처는 엔더 상자 제작이다.
     */
    public static final int EYE_OF_ENDER = 1604;
    /**
     * shulker_shell. <b>바닐라 실존 아이템</b>이며 순수 아이템이다. 셜커 상자의 유일한 재료다.
     *
     * <p><b>획득 경로 divergence</b>: 바닐라의 유일한 원천은 엔드 도시의 셜커 처치인데 이
     * 저장소에는 엔드도 셜커도 없다(몹 추가는 다른 웨이브 소유다). 그래서 밀랍 986 ·
     * 네더 석영 961 · 코코아 콩 1631 이 낸 선례대로 <b>던전 탐험 상자</b>에 낮은 확률로 넣는다
     * ({@code ExplorationLoot.SHULKER_SHELL_DUNGEON_WEIGHT} = 0.08). 스택 64.
     *
     * <p><b>레이드 승리 보상 풀에는 아직 넣지 않았다</b>: 그 풀은 슬롯 하나를 늘리면
     * {@code POOL_SIZE} 가 바뀌어 <b>기존 모든 시드의 보상이 통째로 달라진다</b>. 셜커 상자
     * 블록이 착지하는 트랙에서 정적판 미러({@code StandaloneRaidVictoryReward})와 한 묶음으로
     * 옮기는 것이 맞다. 그때까지 획득 경로는 던전 상자 하나다.
     */
    public static final int SHULKER_SHELL = 1613;

    // ── [SHELF-FUNGUS-WOOL-SLAB] 선반 · 선반버섯 · 양털 반 블록 1560~1587 ──────────
    //
    // 이 트랙 배정은 1560~1599 이고 1589~1599 는 예약 공백이다. 1508~1559 는 다른 트랙
    // 배정 구간이라 되메우지 않는다. 스물여섯 전부 월드 블록이고 순수 아이템은 없다
    // (스물여섯 모두 자기 자신이 아이템이다). 값은 {@link #BLOCK_ID_HIGH_WATER}(케이크
    // 1630)보다 작으므로 이 트랙은 상한을 올리지 않는다.
    //
    // 세 종 <b>모두 바닐라 실존</b>이다 — 선반은 Java 1.21.9, 선반버섯·양털 반 블록은 26.3 이
    // 추가했다. 둘 다 이 저장소가 고정한 1.21.4 데이터 스냅샷보다 뒤라 원문 JSON 이 없어
    // 물성은 전부 [B] 위키 등급이고, 발췌 핀은
    // docs/research/mc-26.3/SHELF-FUNGUS-WOOL-SLAB.md 다.
    //
    // 배치는 앞 계열들과 같이 형상군이 연속 구간이 되게 반 블록 16 → 선반버섯 2 → 선반 8 이다.
    /**
     * 양털 반 블록 16색 1560~1575. 색 순서는 양털 561~576 · 카펫 577~592 · 침대 851~866 ·
     * 쿠션 1282~1297 과 문자 그대로 같다. 물성은 <b>양털과 같다</b> — destroy_time 0.8 ·
     * explosion_resistance 0.8 · 도구 제한 없음(가위가 가장 빠르다) · WOOL 음향 ·
     * 가연 장려도 30 / 연소 60. 형상은 기존 반 블록 계약(state 0=bottom, 1=top, 2=double)을
     * 그대로 쓴다. <b>절단기 레시피는 없다</b> — 바닐라 절단은 돌 계열 재료에만 있다.
     */
    public static final int WHITE_WOOL_SLAB = 1560;
    public static final int ORANGE_WOOL_SLAB = 1561;
    public static final int MAGENTA_WOOL_SLAB = 1562;
    public static final int LIGHT_BLUE_WOOL_SLAB = 1563;
    public static final int YELLOW_WOOL_SLAB = 1564;
    public static final int LIME_WOOL_SLAB = 1565;
    public static final int PINK_WOOL_SLAB = 1566;
    public static final int GRAY_WOOL_SLAB = 1567;
    public static final int LIGHT_GRAY_WOOL_SLAB = 1568;
    public static final int CYAN_WOOL_SLAB = 1569;
    public static final int PURPLE_WOOL_SLAB = 1570;
    public static final int BLUE_WOOL_SLAB = 1571;
    public static final int BROWN_WOOL_SLAB = 1572;
    public static final int GREEN_WOOL_SLAB = 1573;
    public static final int RED_WOOL_SLAB = 1574;
    public static final int BLACK_WOOL_SLAB = 1575;
    /**
     * shelf_mushroom(작은 것). 옆면이 꽉 찬 블록의 <b>수직 벽면</b>에 붙는 장식 버섯이다.
     * 바닐라 destroy_time 0 · explosion_resistance 0 · 아무 도구 · 드랍 1개이고, 상태는
     * 수평 facing 2비트와 26.3의 {@code age} 상태 비트를 같은 ID에 보존한다.
     *
     * <p><b>바이옴 틴트를 받지 않는다.</b> 위키 infobox 의 map color {@code 7 PLANT} 는 지도
     * 픽셀 색이지 모델의 {@code tintindex} 가 아니다.
     */
    public static final int SHELF_MUSHROOM = 1576;
    /** Removed pre-26.3 fake age-as-ID slot. Numeric reservation only; never register or obtain. */
    public static final int REMOVED_LARGE_SHELF_MUSHROOM_1577 = 1577;
    /**
     * shelf 1580~1587. 바닐라 1.21.9 destroy_time 2 · explosion_resistance 3 ·
     * mineable/axe(요구 없음) · WOOD 음향 · 가연 장려도 30 이고, 상태는 수평 facing 2비트다.
     *
     * <p>수종 순서 0..7 은 {@link #LOG_BY_WOOD_SPECIES} 와 문자 그대로 같아
     * {@code OAK_SHELF + species} 가 성립한다. 창백한 참나무는 여덟 수종 표 밖이라
     * 아홉 번째 자리에 별도 상수로 붙이고 산술 범위에는 넣지 않는다.
     *
     * <p>세 슬롯의 정확한 스택, 정면 교환, 통전 핫바 스왑, 뒷면 비교기, 수중 상태를 영속한다.
     */
    public static final int OAK_SHELF = 1580;
    public static final int BIRCH_SHELF = 1581;
    public static final int SPRUCE_SHELF = 1582;
    public static final int JUNGLE_SHELF = 1583;
    public static final int ACACIA_SHELF = 1584;
    public static final int DARK_OAK_SHELF = 1585;
    public static final int CHERRY_SHELF = 1586;
    public static final int MANGROVE_SHELF = 1587;

    /**
     * 수종 인덱스(0..7) → 그 수종의 선반 ID. {@code OAK_SHELF + species} 와 같은 값이지만,
     * 산술만 두면 중간 상수가 어디에서도 참조되지 않는 "선언만 있는 ID" 가 되어 재배정 사고를
     * 아무도 잡지 못한다(벗긴 원목·구리 산화 표와 같은 이유).
     *
     * <p>이 표는 {@link #LOG_BY_WOOD_SPECIES} 의 <b>앞 여덟 칸</b>과만 짝이다. 포플러(수종
     * 인덱스 8)는 이 트랙 시점에 한쪽 권위에만 있는 진행 중 수종이라 선반을 만들지 않는다 —
     * 반쪽만 있는 ID 는 양 권위 파리티를 깨뜨린다. 포플러 선반은 후속 트랙 몫이고, 그때
     * 1588~1599 예약 공백에서 ID 를 잡으면 된다.
     */
    public static final int[] SHELF_BY_WOOD_SPECIES = {
            OAK_SHELF, BIRCH_SHELF, SPRUCE_SHELF, JUNGLE_SHELF,
            ACACIA_SHELF, DARK_OAK_SHELF, CHERRY_SHELF, MANGROVE_SHELF,
    };

    /** 양털 반 블록 16색 구간 판정. client {@code isWoolSlab} 과 값이 같아야 한다. */
    public static boolean isWoolSlab(int id) {
        return id >= WHITE_WOOL_SLAB && id <= BLACK_WOOL_SLAB;
    }

    /** 기존 8수종 또는 포플러 선반인가. */
    public static boolean isShelf(int id) {
        return id >= OAK_SHELF && id <= MANGROVE_SHELF || id == POPLAR_SHELF
                || id == PALE_OAK_SHELF;
    }

    /** 선반버섯 블록 판정. 크기는 AGE 상태가 소유한다. */
    public static boolean isShelfMushroom(int id) {
        return id == SHELF_MUSHROOM;
    }

    // ── [COOKING] 요리 계열 1630~1669 ─────────────────────────────────────────────
    //
    // 1482~1629 는 **다른 트랙 배정 구간**이라 되메우지 않는다. 이 트랙 배정은 1630~1669 이고
    // 케이크(월드 블록) 하나 + 순수 아이템 여섯을 쓴 뒤 1637~1669 를 예약 공백으로 남긴다.
    // 발췌 핀은 docs/research/mc-food-cooking.md 다.
    /**
     * 케이크. <b>이 트랙이 유일하게 만드는 월드 블록</b>이라 {@link #BLOCK_ID_HIGH_WATER} 를
     * 여기까지 올린다. 바닐라 {@code minecraft:cake} 그대로 상태값 {@code bites} 0~6 을
     * 갖고 총 <b>7회</b> 취식된다. 경도 0.5 · 폭발 저항 0.5 · 도구 무관이고 부수면 아무것도
     * 떨구지 않는다(실크 터치도 회수 불가) — 그래서 지지 상실 드랍도 {@link #AIR} 다.
     *
     * <p>바닐라와 갈리는 유일한 취식 규칙: <b>허기가 가득 차 있으면 먹을 수 없다</b>.
     * 다른 음식은 이 저장소에서도 만복에 먹히지만 케이크만 바닐라처럼 막힌다.
     * [B] minecraft.wiki «Cake».
     */
    public static final int CAKE = 1630;
    /**
     * cocoa_beans. <b>바닐라 실존 아이템</b>이고 순수 아이템이다. 세계에 붙는
     * 꼬투리는 나중 append 된 별도 월드 블록 {@link #COCOA}다.
     *
     * <p><b>획득 경로 divergence</b>: 바닐라 Java 의 유일한 원천은 자연 생성 정글 통나무에
     * 붙는 코코아 꼬투리인데, 꼬투리는 지형 배치물이라 지형 레인 없이 세울 수 없다. 그래서
     * {@link #MAGMA_CREAM}·{@link #BLAZE_ROD}·{@link #NETHER_WART} 가 낸 선례대로
     * <b>탐험 전리품</b>을 유일한 경로로 둔다(바닐라 26.x 가 버려진 야영지 상자에 코코아를
     * 넣은 것과 성질이 같다). 스택 64.
     */
    public static final int COCOA_BEANS = 1631;
    /** cookie. 밀 2 + 코코아 콩 1 → 8개(바닐라 그대로). 허기 2 · 포화 0.4. 스택 64. */
    public static final int COOKIE = 1632;
    /** pumpkin_pie. 호박 + 설탕 + 달걀 무형(바닐라 그대로). 허기 8 · 포화 4.8. 스택 64. */
    public static final int PUMPKIN_PIE = 1633;
    /**
     * suspicious_stew (양귀비). 그릇 + 갈색 버섯 + 붉은 버섯 + 양귀비(바닐라 그대로).
     * 허기 6 · 포화 7.2 이고 먹으면 그릇을 돌려준다 — 토끼 스튜·버섯 스튜와 같은 그릇 음식
     * 계약이다. 스택 1(바닐라 {@code max_stack_size} 1). 먹으면 야간 투시 5초.
     *
     * <p><b>왜 꽃마다 ID 를 나누나</b>: 바닐라는 수상한 스튜 <b>한 아이템</b>이 NBT
     * ({@code suspicious_stew_effects} 컴포넌트)로 효과를 들고 다니지만, 이 저장소의 아이템
     * 스택에는 NBT 가 없다(종류 + 개수 + 내구 + 인챈트 마스크뿐). 같은 문제를 이미 물약이
     * 겪었고 해법도 이미 정해져 있다 — <b>효과마다 별도 아이템 ID</b>
     * ({@link #POTION_SWIFTNESS}·{@link #POTION_POISON}…). 수상한 스튜도 그 선례를 그대로
     * 잇는다. 표시 이름과 수치·그릇 반환은 두 ID 가 완전히 같고 효과만 갈린다.
     */
    public static final int SUSPICIOUS_STEW_POPPY = 1634;
    /** suspicious_stew (민들레). 먹으면 포만감 0.35초(MC 7틱). 나머지는 양귀비 쪽과 같다. */
    public static final int SUSPICIOUS_STEW_DANDELION = 1635;
    /**
     * dried_kelp. 다시마({@link #KELP})를 화로·훈연기·모닥불에서 제련해 얻는다(바닐라 그대로).
     * 허기 1 · 포화 0.6. 스택 64. 말린 다시마 <b>블록</b>은 이 웨이브 밖이다(음식이 아니다).
     */
    public static final int DRIED_KELP = 1636;

    // ── [POPLAR] 26.3 포플러 목재 계열 · 얼룩덜룩한 숲 지표 월드 블록 1530~1555 ────
    //
    // 이 트랙에 배정된 구간은 1530~1559 이고 1556~1559 만 예약 공백이다.
    // 1482~1529 · 1560~ 은 다른 트랙 배정 구간이라 되메우지 않는다. 발췌 핀은
    // docs/research/mc-26.3/DAPPLED_FOREST.md 이고 client 사본은
    // client/src/world/blocks.ts 의 같은 절이다.
    //
    // 근거는 고정 26.3-snapshot-7 클라이언트 JAR의 blockstate·recipe·loot·tag JSON 원문이다.
    //
    // <b>수종 세트의 폭은 기존 7수종과 정확히 같다</b>: 판자·계단·반 블록·울타리·울타리문·
    // 다락문·문 일곱 형상이다. 표지판·걸린 표지판·선반·버튼·압력판·나무(bark)·종별 보트는
    // 자작~맹그로브 어느 수종도 갖고 있지 않은 형상이라 포플러도 만들지 않는다 — 재료
    // 대체가 아니라 형상 미존재라 원본 레시피 무결성과 무관하다.
    //
    // <b>형상 판정은 두 구간을 본다</b>: 601–649 의 연속 7수종 구간과 이 절의 단일 포플러
    // ID 다. 601–649 를 재배정하지 않는다는 append-only 계약 때문이며, 그래서 각 형상
    // 판정(isWoodPlank·isWoodStairs·…)이 `범위 || 포플러` 두 항으로만 늘어난다.

    /** poplar_log(축 y). 경도 2 · 폭발 저항 2 · 도끼 · 가연 5. [B] */
    public static final int POPLAR_LOG = 1530;
    /** poplar_log(축 x). 축은 기존 수종과 같이 ID 로 잡는다. */
    public static final int POPLAR_LOG_X = 1531;
    /** poplar_log(축 z). */
    public static final int POPLAR_LOG_Z = 1532;
    /** stripped_poplar_log(축 y). 도끼로 벗겨 얻는다. */
    public static final int STRIPPED_POPLAR_LOG = 1533;
    /** stripped_poplar_log(축 x). */
    public static final int STRIPPED_POPLAR_LOG_X = 1534;
    /** stripped_poplar_log(축 z). */
    public static final int STRIPPED_POPLAR_LOG_Z = 1535;
    /**
     * yellow_poplar_leaves. 경도 0.2 · 폭발 저항 0.2 · 괭이 · 가연 30 ·
     * 묘목 5 % · 막대기 2 %. map color 18 COLOR_YELLOW. [B]
     */
    public static final int POPLAR_LEAVES_YELLOW = 1536;
    /** orange_poplar_leaves. 같은 물성, map color 15 COLOR_ORANGE. [B] */
    public static final int POPLAR_LEAVES_ORANGE = 1537;
    /** red_poplar_leaves. 같은 물성, map color 28 COLOR_RED. [B] */
    public static final int POPLAR_LEAVES_RED = 1538;
    /**
     * poplar_sapling. 묘목은 <b>한 종류</b>이고 자랄 때 세 잎 변종 중 하나를 무작위로
     * 고른다 [B]. 성장 광량 9 이상 [B].
     */
    public static final int POPLAR_SAPLING = 1539;
    /**
     * red_shrub. 경도 0 · 폭발 저항 0 · 맨손 즉시 파괴 · <b>언제나 자신을 드랍한다</b> [B].
     * 가연이지만 <b>용암에는 붙지 않는다</b> [B]. 화분에는 넣을 수 없다 [B].
     */
    public static final int RED_SHRUB = 1540;
    /** poplar_button. 판자 1개의 무형 제작으로 1개를 얻는 목재 버튼이다. */
    public static final int POPLAR_BUTTON = 1541;
    /** poplar_pressure_plate. 판자 2개를 가로로 놓아 1개를 얻는 목재 압력판이다. */
    public static final int POPLAR_PRESSURE_PLATE = 1542;
    /** poplar_sign. 벽 부착 형태는 같은 아이템과 block state를 공유한다. */
    public static final int POPLAR_SIGN = 1543;
    /** poplar_hanging_sign. 벽 부착 형태는 같은 아이템과 block state를 공유한다. */
    public static final int POPLAR_HANGING_SIGN = 1544;
    /** poplar_wood. 여섯 면이 모두 포플러 껍질인 원목. */
    public static final int POPLAR_WOOD = 1545;
    /** stripped_poplar_wood. 여섯 면이 모두 벗긴 포플러 결인 원목. */
    public static final int STRIPPED_POPLAR_WOOD = 1546;
    /** poplar_shelf. 벗긴 포플러 원목 6개로 선반 6개를 만든다. */
    public static final int POPLAR_SHELF = 1547;
    /** potted_poplar_sapling. 화분에 포플러 묘목이 든 블록 상태다. */
    public static final int POTTED_POPLAR_SAPLING = 1548;
    /** poplar_planks. 원목 1 → 판자 4. 경도 2 · 폭발 저항 3(바닐라 판자 공통). */
    public static final int POPLAR_PLANK = 1549;
    /** poplar_stairs. 판자 6 → 계단 4. */
    public static final int POPLAR_STAIRS = 1550;
    /** poplar_slab. 판자 3 → 반 블록 6. */
    public static final int POPLAR_SLAB = 1551;
    /** poplar_fence. 판자 4 + 막대기 2 → 울타리 3. */
    public static final int POPLAR_FENCE = 1552;
    /** poplar_fence_gate. 판자 2 + 막대기 4 → 울타리 문 1. */
    public static final int POPLAR_FENCE_GATE = 1553;
    /** poplar_trapdoor. 판자 6 → 다락문 2. */
    public static final int POPLAR_TRAPDOOR = 1554;
    /** poplar_door. 판자 6 → 문 3. */
    public static final int POPLAR_DOOR = 1555;
    // 1556~1559 는 이 트랙의 예약 공백이라 되메우지 않는다.

    /** 포플러 잎 세 변종인가. 드랍·가연·틴트가 이 판정 하나만 본다. */
    public static boolean isPoplarLeaves(int id) {
        return id >= POPLAR_LEAVES_YELLOW && id <= POPLAR_LEAVES_RED;
    }

    /**
     * 포플러 가공 계열 7종(1549~1555 — 판자·계단·반·울타리·울타리문·다락문·문)인가.
     * client {@code blocks.ts} 의 {@code isPoplarBuildingBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isPoplarBuildingBlock(int id) {
        return (id >= POPLAR_BUTTON && id <= POPLAR_PRESSURE_PLATE)
                || (id >= POPLAR_WOOD && id <= POPLAR_SHELF)
                || id >= POPLAR_PLANK && id <= POPLAR_DOOR;
    }

    /** 포플러 목재 가공물 중 화재 표(ignite 5 / burn 20)를 쓰는 블록. */
    public static boolean isPoplarFlammableProduct(int id) {
        return id >= POPLAR_BUTTON && id <= POPLAR_HANGING_SIGN || id == POPLAR_SHELF;
    }

    /** 포플러 원목(축 3종)인가. */
    public static boolean isPoplarLog(int id) {
        return id >= POPLAR_LOG && id <= POPLAR_LOG_Z;
    }

    /** 벗긴 포플러 원목(축 3종)인가. */
    public static boolean isStrippedPoplarLog(int id) {
        return id >= STRIPPED_POPLAR_LOG && id <= STRIPPED_POPLAR_LOG_Z;
    }

    /** {@code #minecraft:poplar_logs} 태그의 원목 네 형식인가. */
    public static boolean isPoplarLogsTag(int id) {
        return isPoplarLog(id) || isStrippedPoplarLog(id)
                || id == POPLAR_WOOD || id == STRIPPED_POPLAR_WOOD;
    }

    /**
     * 포플러 원목·벗긴 포플러 원목의 인벤토리 아이템 ID(축 변형은 y축 대표 ID 로 접힌다).
     * 포플러 원목 계열이 아니면 {@link #AIR}.
     *
     * <p>포플러는 여덟 수종 표({@link #LOG_BY_WOOD_SPECIES} ·
     * {@link #STRIPPED_LOG_BY_SPECIES_AND_AXIS})에 <b>들어가지 않는다</b> — 그 표의 정본
     * 산술은 {@code STRIPPED_OAK_LOG + species*3 + axis} 인데 아홉 번째 자리(895~)는 다른
     * 트랙 예약 공백이라 표만 늘리면 표와 산술이 조용히 어긋난다. 창백한 참나무(1490~)가
     * 같은 이유로 이미 명시 갈래를 쓰므로 포플러도 같은 규약을 따른다.
     */
    public static int poplarLogItem(int id) {
        if (isPoplarLog(id)) return POPLAR_LOG;
        if (isStrippedPoplarLog(id)) return STRIPPED_POPLAR_LOG;
        if (id == POPLAR_WOOD || id == STRIPPED_POPLAR_WOOD) return id;
        return AIR;
    }

    /**
     * 도끼로 포플러 원목을 벗긴 결과. 바닐라와 같이 <b>축을 보존</b>한다. 포플러 원목이
     * 아니면 {@code -1} — 호출부가 여덟 수종 경로({@link #strippedLogFor(int, int)})와 이
     * 갈래를 명시적으로 나눌 수 있어야 하기 때문이다.
     */
    public static int strippedPoplarLogFor(int blockId) {
        if (blockId == POPLAR_WOOD) return STRIPPED_POPLAR_WOOD;
        if (!isPoplarLog(blockId)) return -1;
        return STRIPPED_POPLAR_LOG + (blockId - POPLAR_LOG);
    }

    // ── [SPRING-TO-LIFE] 1.21.5 자연 요소 월드 블록 1350~1355 ──────────────────
    //
    // 이 트랙에 배정된 구간은 1350~1369 이고 1356~1369 는 예약 공백이다. 1340~1349 ·
    // 1370~1399 는 다른 트랙 배정 구간이라 되메우지 않는다.
    // 발췌 핀은 docs/research/mc-1215/SPRING_TO_LIFE.md 이고 client 사본은
    // client/src/world/blocks.ts 의 같은 절이다.
    //
    // 근거 등급: [A]=원문 JSON(1.21.5-rc1 에셋) · [B]=위키 본문 · [C]=이 저장소 자체 계약.
    // 1.21.5 는 고정 데이터 스냅샷(1.21.4-data-json)보다 뒤 버전이라 저장소 안에서 데이터팩을
    // 읽을 수 없다 — 물성은 대부분 [B] 이고 블록스테이트/모델만 [A] 다.
    //
    // <b>수풀(bush)은 새 ID 를 만들지 않는다</b>: 1.21.5 의 {@code bush} 는 이 저장소에 이미
    // {@link #BUSH}=198 로 있고, 이 트랙은 그 ID 에 1.21.5 계약(가위 드랍 · 뼛가루 이웃 번식)만
    // 얹는다. 같은 블록을 두 ID 로 만들면 저장 월드가 갈라진다.
    //
    // <b>지형 생성은 이 계열을 만들지 않는다</b>(divergence, 딥다크·유황 선례): 자연 배치
    // 정본인 wasm {@code mc_natural} 이 이 웨이브의 범위 밖이라 배치를 배선하지 않는다
    // → golden terrain 해시 불변.

    /**
     * wildflowers. 바닐라 {@code FlowerBedBlock} 계열(핑크 꽃잎과 같은 문법) — 한 칸에
     * 사분면 조각 최대 넷이다. destroy_time 0 · explosion_resistance 0 · 발광 없음 [B].
     * 상태 인코딩은 {@link #flowerBedState(int, int)} 가 소유한다.
     *
     * <p>블록스테이트 원문 [A]: 사분면 모델 {@code wildflowers_1..4} 가 <b>누적</b>되고
     * {@code facing} 이 그 묶음의 y 회전이다. 낙엽 리터와 달리 모델에 {@code tintindex} 가
     * 없어 <b>바이옴 틴트를 받지 않는다</b>.
     */
    public static final int WILDFLOWERS = 1350;
    /**
     * leaf_litter. 야생화와 같은 {@code FlowerBedBlock} 문법이고 상태 이름만
     * {@code segment_amount} 다. destroy_time 0 · explosion_resistance 0 [B].
     *
     * <p>모델 원문 [A] {@code template_leaf_litter_1}: {@code from [0,0.25,0] → to [8,0.25,8]}
     * 즉 <b>두께 0 인 사분면 평면</b>이고 up/down 면 모두 {@code tintindex: 0} 이라
     * <b>바이옴 폴리지 틴트를 받는다</b>. 이 저장소의 렌더 계약에는 두께 0 형상이 없어 공통
     * 부분 높이 계열의 최소값 <b>1/16</b> 으로 낸다([C] divergence — 이끼 바닥 199 선례).
     */
    public static final int LEAF_LITTER = 1351;
    /**
     * firefly_bush. destroy_time 0 · explosion_resistance 0 · <b>광량 2</b> [B]. 반딧불
     * 파티클 조건은 "제자리 내부 광량 13 이하"이고 틱당 70% 로 최대 5칸 위·수평 5칸 안의
     * 공기 칸에 하나를 띄우며 수명은 200~300 MC 틱이다 [B].
     *
     * <p>파티클은 <b>클라 전용 파생</b>이라 서버는 어떤 파티클 사실도 보내지 않는다
     * (CONTRACT 6a — 벚꽃 분재 꽃잎과 같은 근거).
     */
    public static final int FIREFLY_BUSH = 1352;
    /**
     * cactus_flower. destroy_time 0 · explosion_resistance 0 · 맨손 즉시 파괴이고 언제나
     * 자신을 드랍한다 [B]. 선인장 꼭대기나 "윗면 중앙 지지"가 있는 블록 위에 놓인다 [B].
     *
     * <p>선인장 성장 분기(높이 1~2 에서 10% · 높이 3 에서 25% 로 키 대신 꽃) [B] 는 선인장
     * 성장 규칙을 소유한 트랙 몫이라 이 웨이브는 <b>설치·상호작용 계약까지만</b> 낸다.
     */
    public static final int CACTUS_FLOWER = 1353;
    /**
     * short_dry_grass. destroy_time 0 · 가위 또는 실크 터치일 때만 자신을 드랍하고 아니면
     * 아무것도 떨구지 않는다 [B]. 뼛가루를 쓰면 <b>큰 마른 풀로 바뀐다</b> [B].
     */
    public static final int SHORT_DRY_GRASS = 1354;
    /**
     * tall_dry_grass. 같은 드랍 계약이고, 다른 tall 계열과 달리 <b>한 칸 높이</b>다 [B].
     * 뼛가루를 쓰면 이웃 칸에 짧은 마른 풀을 놓는다 [B].
     */
    public static final int TALL_DRY_GRASS = 1355;
    // 1356~1369 는 이 트랙의 예약 공백이라 되메우지 않는다.

    /**
     * {@code FlowerBedBlock} 문법 블록(야생화 1350 · 낙엽 리터 1351)인가. 상태 인코딩·설치
     * 누적·드랍 수량이 전부 이 판정 하나만 본다.
     * client {@code blocks.ts} 의 {@code isFlowerBedBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isFlowerBedBlock(int id) {
        return id == WILDFLOWERS || id == LEAF_LITTER;
    }

    /** {@code FlowerBedBlock} 한 칸의 조각 수 상한. 바닐라 1..4 [A]. */
    public static final int FLOWER_BED_MAX_AMOUNT = 4;

    /**
     * {@code FlowerBedBlock} 상태 인코딩. 바닐라의 두 속성을 한 바이트에 담는다 —
     * 하위 2비트가 {@code amount − 1}(0..3), 그 위 2비트가 {@code facing}(0..3).
     * 두 권위와 메셔가 전부 이 함수들만 쓰므로 비트 배치가 한 곳에만 있다.
     */
    public static int flowerBedState(int amount, int facing) {
        int clamped = Math.max(1, Math.min(FLOWER_BED_MAX_AMOUNT, amount));
        return ((facing & 3) << 2) | (clamped - 1);
    }

    /** 상태 → 조각 수(1..4). */
    public static int flowerBedAmount(int state) {
        return (state & 3) + 1;
    }

    /** 상태 → facing(0..3). */
    public static int flowerBedFacing(int state) {
        return (state >> 2) & 3;
    }

    /**
     * 1.21.5 마른 풀 두 종(1354~1355)인가. 가위 드랍·가연성·설치 바닥이 이 판정 하나만 본다.
     * client {@code blocks.ts} 의 {@code isDryGrass} 와 같은 판정이어야 한다.
     */
    public static boolean isDryGrass(int id) {
        return id == SHORT_DRY_GRASS || id == TALL_DRY_GRASS;
    }

    /**
     * [COPPER] 산화 4단계 계열 정본 표. 행 하나가 한 계열이고 열이
     * unaffected / exposed / weathered / oxidized 다.
     *
     * <p>산술({@code base + stage})로만 두지 않고 <b>모든 단계를 표에 적는</b> 이유는 벗긴
     * 원목·석영 기둥 표와 같다 — 그러지 않으면 중간 단계 상수가 어디에서도 참조되지 않는
     * "선언만 있는 ID" 가 되어 재배정 사고를 아무도 잡지 못한다. 대신 각 행이 실제로 4연속
     * ID 인지는 아래 정적 초기화가 검사하므로, 표와 산술이 어긋날 수 없다.
     *
     * <p>이 표가 {@link #nextOxidationStage(int)}·{@link #previousOxidationStage(int)}·
     * {@link #copperOxidationAge(int)} 의 유일한 원천이라, 예전처럼 375~378 을 하드코딩한 범위
     * 비교가 코드 여기저기로 복제되지 않는다. 밀랍 계열은 여기에 없다 — 밀랍 블록은 바닐라에서
     * ChangeOverTimeBlock 이 아니라 산화하지 않기 때문이다.
     *
     * <p>랜덤틱은 이 표를 순회하지 않고 O(1) 조회만 하므로 표의 순서가 난수 소비 순서에
     * 영향을 주지 않는다(정적판 미러의 draw 순서 불변 근거).
     */
    public static final int[][] COPPER_OXIDATION_FAMILIES = {
            { COPPER_BLOCK, EXPOSED_COPPER, WEATHERED_COPPER, OXIDIZED_COPPER },
            { CUT_COPPER, EXPOSED_CUT_COPPER, WEATHERED_CUT_COPPER, OXIDIZED_CUT_COPPER },
            { CUT_COPPER_STAIRS, EXPOSED_CUT_COPPER_STAIRS, WEATHERED_CUT_COPPER_STAIRS, OXIDIZED_CUT_COPPER_STAIRS },
            { CUT_COPPER_SLAB, EXPOSED_CUT_COPPER_SLAB, WEATHERED_CUT_COPPER_SLAB, OXIDIZED_CUT_COPPER_SLAB },
            { CHISELED_COPPER, EXPOSED_CHISELED_COPPER, WEATHERED_CHISELED_COPPER, OXIDIZED_CHISELED_COPPER },
            { COPPER_GRATE, EXPOSED_COPPER_GRATE, WEATHERED_COPPER_GRATE, OXIDIZED_COPPER_GRATE },
            { COPPER_BULB, EXPOSED_COPPER_BULB, WEATHERED_COPPER_BULB, OXIDIZED_COPPER_BULB },
            { COPPER_BULB_LIT, EXPOSED_COPPER_BULB_LIT, WEATHERED_COPPER_BULB_LIT, OXIDIZED_COPPER_BULB_LIT },
            { COPPER_DOOR, EXPOSED_COPPER_DOOR, WEATHERED_COPPER_DOOR, OXIDIZED_COPPER_DOOR },
            { COPPER_TRAPDOOR, EXPOSED_COPPER_TRAPDOOR, WEATHERED_COPPER_TRAPDOOR, OXIDIZED_COPPER_TRAPDOOR },
            // [OPENABLE-METAL] 구리 창살(1.21.9). 행은 언제나 **끝에 덧붙인다** — 앞에 끼우면
            // packedCopperSlot 의 index 가 통째로 밀려 저장된 값과 무관한 재계산이 필요해진다.
            { COPPER_BARS, EXPOSED_COPPER_BARS, WEATHERED_COPPER_BARS, OXIDIZED_COPPER_BARS },
            // [COPPER-CHAIN] 구리 사슬(1.21.9). 축이 상태가 아니라 ID 라 <b>축마다 한 행</b>이고,
            // 세 행 모두 언제나 **끝에** 덧붙인다 — 앞에 끼우면 packedCopperSlot 의 index 가
            // 통째로 밀려 저장된 월드와 무관한 재계산이 필요해진다.
            { COPPER_CHAIN, EXPOSED_COPPER_CHAIN, WEATHERED_COPPER_CHAIN, OXIDIZED_COPPER_CHAIN },
            { COPPER_CHAIN_X, EXPOSED_COPPER_CHAIN_X, WEATHERED_COPPER_CHAIN_X, OXIDIZED_COPPER_CHAIN_X },
            { COPPER_CHAIN_Z, EXPOSED_COPPER_CHAIN_Z, WEATHERED_COPPER_CHAIN_Z, OXIDIZED_COPPER_CHAIN_Z },
            // [CHEST-FAMILY] 구리 상자(1.21.9). 여기도 언제나 **끝에** 덧붙인다 — 앞에 끼우면
            // packedCopperSlot 의 index 가 통째로 밀려 저장된 월드와 무관한 재계산이 필요해진다.
            { COPPER_CHEST, EXPOSED_COPPER_CHEST, WEATHERED_COPPER_CHEST, OXIDIZED_COPPER_CHEST },
    };

    /**
     * [COPPER] 밀랍 계열 정본 표. {@link #COPPER_OXIDATION_FAMILIES} 와 <b>같은 순서·같은
     * 모양</b>이며 같은 좌표가 같은 계열·같은 산화 단계다(밀랍 도포/제거가 좌표 대응 하나로
     * 끝난다). 첫 행만 기존 4블록(375~378)의 밀랍 대응이라 다른 ID 구간에 있다.
     */
    public static final int[][] WAXED_COPPER_FAMILIES = {
            { WAXED_COPPER_BLOCK, WAXED_EXPOSED_COPPER, WAXED_WEATHERED_COPPER, WAXED_OXIDIZED_COPPER },
            { WAXED_CUT_COPPER, WAXED_EXPOSED_CUT_COPPER, WAXED_WEATHERED_CUT_COPPER, WAXED_OXIDIZED_CUT_COPPER },
            { WAXED_CUT_COPPER_STAIRS, WAXED_EXPOSED_CUT_COPPER_STAIRS, WAXED_WEATHERED_CUT_COPPER_STAIRS, WAXED_OXIDIZED_CUT_COPPER_STAIRS },
            { WAXED_CUT_COPPER_SLAB, WAXED_EXPOSED_CUT_COPPER_SLAB, WAXED_WEATHERED_CUT_COPPER_SLAB, WAXED_OXIDIZED_CUT_COPPER_SLAB },
            { WAXED_CHISELED_COPPER, WAXED_EXPOSED_CHISELED_COPPER, WAXED_WEATHERED_CHISELED_COPPER, WAXED_OXIDIZED_CHISELED_COPPER },
            { WAXED_COPPER_GRATE, WAXED_EXPOSED_COPPER_GRATE, WAXED_WEATHERED_COPPER_GRATE, WAXED_OXIDIZED_COPPER_GRATE },
            { WAXED_COPPER_BULB, WAXED_EXPOSED_COPPER_BULB, WAXED_WEATHERED_COPPER_BULB, WAXED_OXIDIZED_COPPER_BULB },
            { WAXED_COPPER_BULB_LIT, WAXED_EXPOSED_COPPER_BULB_LIT, WAXED_WEATHERED_COPPER_BULB_LIT, WAXED_OXIDIZED_COPPER_BULB_LIT },
            { WAXED_COPPER_DOOR, WAXED_EXPOSED_COPPER_DOOR, WAXED_WEATHERED_COPPER_DOOR, WAXED_OXIDIZED_COPPER_DOOR },
            { WAXED_COPPER_TRAPDOOR, WAXED_EXPOSED_COPPER_TRAPDOOR, WAXED_WEATHERED_COPPER_TRAPDOOR, WAXED_OXIDIZED_COPPER_TRAPDOOR },
            { WAXED_COPPER_BARS, WAXED_EXPOSED_COPPER_BARS, WAXED_WEATHERED_COPPER_BARS, WAXED_OXIDIZED_COPPER_BARS },
            { WAXED_COPPER_CHAIN, WAXED_EXPOSED_COPPER_CHAIN, WAXED_WEATHERED_COPPER_CHAIN, WAXED_OXIDIZED_COPPER_CHAIN },
            { WAXED_COPPER_CHAIN_X, WAXED_EXPOSED_COPPER_CHAIN_X, WAXED_WEATHERED_COPPER_CHAIN_X, WAXED_OXIDIZED_COPPER_CHAIN_X },
            { WAXED_COPPER_CHAIN_Z, WAXED_EXPOSED_COPPER_CHAIN_Z, WAXED_WEATHERED_COPPER_CHAIN_Z, WAXED_OXIDIZED_COPPER_CHAIN_Z },
            { WAXED_COPPER_CHEST, WAXED_EXPOSED_COPPER_CHEST, WAXED_WEATHERED_COPPER_CHEST, WAXED_OXIDIZED_COPPER_CHEST },
    };

    public static final int COPPER_OXIDATION_STAGES = 4;

    // ── [CONCRETE] 콘크리트 16 · 콘크리트 가루 16 · 테라코타 잔여 10색 · 유광 테라코타 16
    //    (1076~1133, MC Java 1.21.4). ──────────────────────────────────────────
    //
    // 색 순서는 **DyeColor 네트워크 ID** 다(흰색 0 … 검은색 15). 양털·카펫 561~592 와
    // 침대 851~866 이 이미 쓰는 규약이라 "색 인덱스 = ID − 계열 시작" 산술이 세 계열에서
    // 같다. 계열마다 연속 16 구간이므로 지지 분류·물성·중력 판정이 범위 술어 한 줄로 끝난다.
    //
    // 테라코타는 **기존 등록분 보완**이다. 총칭 TERRACOTTA(236)와 흰색(237)·노란색(240)·
    // 갈색(241)·빨간색(242)·밝은 회색(243)·주황색(97) 여섯 색이 이미 지형 팔레트로 있었고
    // 여기서 남은 열 색만 추가한다 — 그래서 색 테라코타는 두 구간에 흩어지고
    // {@link #TERRACOTTA_BY_COLOR} 가 그 둘을 잇는 유일한 표다.
    //
    // 전부 제작 전용(placement-only)이라 지형 생성기·runtime overlay·구조물이 만들지 않는다
    // (golden terrain 해시 불변). 1134~1140 은 이 트랙 예약 공백이라 되메우지 않는다.
    /** 콘크리트 16색의 첫 ID(흰색). 바닐라 destroy_time 1.8 · explosion_resistance 1.8. */
    public static final int WHITE_CONCRETE = 1076;
    public static final int ORANGE_CONCRETE = 1077;
    public static final int MAGENTA_CONCRETE = 1078;
    public static final int LIGHT_BLUE_CONCRETE = 1079;
    public static final int YELLOW_CONCRETE = 1080;
    public static final int LIME_CONCRETE = 1081;
    public static final int PINK_CONCRETE = 1082;
    public static final int GRAY_CONCRETE = 1083;
    public static final int LIGHT_GRAY_CONCRETE = 1084;
    public static final int CYAN_CONCRETE = 1085;
    public static final int PURPLE_CONCRETE = 1086;
    public static final int BLUE_CONCRETE = 1087;
    public static final int BROWN_CONCRETE = 1088;
    public static final int GREEN_CONCRETE = 1089;
    public static final int RED_CONCRETE = 1090;
    public static final int BLACK_CONCRETE = 1091;

    /**
     * 콘크리트 가루 16색. 바닐라 destroy_time 0.5 · explosion_resistance 0.5 이고 모래·자갈과
     * 같은 <b>중력 블록</b>이며, 물에 닿으면 같은 색 콘크리트로 굳는다(ConcretePowderBlock).
     * 색 순서가 콘크리트와 같아 굳기 변환이 {@link #CONCRETE_POWDER_TO_CONCRETE} 뺄셈 하나다.
     */
    public static final int WHITE_CONCRETE_POWDER = 1092;
    public static final int ORANGE_CONCRETE_POWDER = 1093;
    public static final int MAGENTA_CONCRETE_POWDER = 1094;
    public static final int LIGHT_BLUE_CONCRETE_POWDER = 1095;
    public static final int YELLOW_CONCRETE_POWDER = 1096;
    public static final int LIME_CONCRETE_POWDER = 1097;
    public static final int PINK_CONCRETE_POWDER = 1098;
    public static final int GRAY_CONCRETE_POWDER = 1099;
    public static final int LIGHT_GRAY_CONCRETE_POWDER = 1100;
    public static final int CYAN_CONCRETE_POWDER = 1101;
    public static final int PURPLE_CONCRETE_POWDER = 1102;
    public static final int BLUE_CONCRETE_POWDER = 1103;
    public static final int BROWN_CONCRETE_POWDER = 1104;
    public static final int GREEN_CONCRETE_POWDER = 1105;
    public static final int RED_CONCRETE_POWDER = 1106;
    public static final int BLACK_CONCRETE_POWDER = 1107;

    /** 가루 → 콘크리트 ID 거리. 두 계열이 같은 색 순서의 연속 16 구간이라 뺄셈 하나가 정본이다. */
    public static final int CONCRETE_POWDER_TO_CONCRETE = WHITE_CONCRETE_POWDER - WHITE_CONCRETE;

    // 테라코타 잔여 10색(1108~1117). 기존 여섯 색(97·237·240~243)에 없던 색만 채운다.
    public static final int MAGENTA_TERRACOTTA = 1108;
    public static final int LIGHT_BLUE_TERRACOTTA = 1109;
    public static final int LIME_TERRACOTTA = 1110;
    public static final int PINK_TERRACOTTA = 1111;
    public static final int GRAY_TERRACOTTA = 1112;
    public static final int CYAN_TERRACOTTA = 1113;
    public static final int PURPLE_TERRACOTTA = 1114;
    public static final int BLUE_TERRACOTTA = 1115;
    public static final int GREEN_TERRACOTTA = 1116;
    public static final int BLACK_TERRACOTTA = 1117;

    /**
     * 유광 테라코타 16색(1118~1133). 바닐라 destroy_time 1.4 · explosion_resistance 1.4 로
     * 색 테라코타(1.25 / 4.2)와 다르다 — 구우면 단단해지지만 폭발에는 훨씬 약해진다.
     *
     * <p><b>MC-REFERENCE divergence</b>: 바닐라는 {@code facing} 4방향으로 무늬가 90°씩
     * 회전하지만 이 저장소의 렌더 계약({@code BlockMeta.stateFaceTile})은 "상태 → 여섯 면 중
     * 어느 타일" 만 고를 수 있고 면 안의 UV 회전을 표현하지 못한다. 회전은 메셔의 위치 해시
     * 경로 전용이라 상태를 읽지 않는다 — 새 렌더 계약을 만드는 것은 이 트랙 범위 밖이라
     * <b>무회전</b>으로 둔다.
     */
    public static final int WHITE_GLAZED_TERRACOTTA = 1118;
    public static final int ORANGE_GLAZED_TERRACOTTA = 1119;
    public static final int MAGENTA_GLAZED_TERRACOTTA = 1120;
    public static final int LIGHT_BLUE_GLAZED_TERRACOTTA = 1121;
    public static final int YELLOW_GLAZED_TERRACOTTA = 1122;
    public static final int LIME_GLAZED_TERRACOTTA = 1123;
    public static final int PINK_GLAZED_TERRACOTTA = 1124;
    public static final int GRAY_GLAZED_TERRACOTTA = 1125;
    public static final int LIGHT_GRAY_GLAZED_TERRACOTTA = 1126;
    public static final int CYAN_GLAZED_TERRACOTTA = 1127;
    public static final int PURPLE_GLAZED_TERRACOTTA = 1128;
    public static final int BLUE_GLAZED_TERRACOTTA = 1129;
    public static final int BROWN_GLAZED_TERRACOTTA = 1130;
    public static final int GREEN_GLAZED_TERRACOTTA = 1131;
    public static final int RED_GLAZED_TERRACOTTA = 1132;
    public static final int BLACK_GLAZED_TERRACOTTA = 1133;

    /**
     * 색 테라코타 16색을 색 인덱스(DyeColor 네트워크 ID) 순으로 편 정본 표. 여섯 색은 기존
     * 흩어진 ID(97·237·240~243)이고 열 색은 신규 연속 구간(1108~1117)이라, 이 표가 두 구간을
     * 잇는 <b>유일한</b> 지점이다. TS {@code blocks.ts TERRACOTTA_BY_COLOR} 사본이다.
     */
    public static final int[] TERRACOTTA_BY_COLOR = {
            WHITE_TERRACOTTA, ORANGE_TERRACOTTA, MAGENTA_TERRACOTTA, LIGHT_BLUE_TERRACOTTA,
            YELLOW_TERRACOTTA, LIME_TERRACOTTA, PINK_TERRACOTTA, GRAY_TERRACOTTA,
            LIGHT_GRAY_TERRACOTTA, CYAN_TERRACOTTA, PURPLE_TERRACOTTA, BLUE_TERRACOTTA,
            BROWN_TERRACOTTA, GREEN_TERRACOTTA, RED_TERRACOTTA, BLACK_TERRACOTTA,
    };

    /**
     * 색 인덱스 → 콘크리트 / 콘크리트 가루 / 유광 테라코타 블록 ID. 세 계열은 연속 구간이라
     * 산술로도 풀리지만 <b>표를 정본으로 둔다</b> — 그러지 않으면 색 상수 45개가 어디에서도
     * 참조되지 않는 "선언만 있는 ID" 가 되어 재배정 사고를 게이트가 잡지 못한다
     * (벗긴 원목·석영 기둥 표와 같은 이유). 제작·제련 레시피도 이 표만 읽는다.
     */
    public static final int[] CONCRETE_BY_COLOR = {
            WHITE_CONCRETE, ORANGE_CONCRETE, MAGENTA_CONCRETE, LIGHT_BLUE_CONCRETE,
            YELLOW_CONCRETE, LIME_CONCRETE, PINK_CONCRETE, GRAY_CONCRETE,
            LIGHT_GRAY_CONCRETE, CYAN_CONCRETE, PURPLE_CONCRETE, BLUE_CONCRETE,
            BROWN_CONCRETE, GREEN_CONCRETE, RED_CONCRETE, BLACK_CONCRETE,
    };
    public static final int[] CONCRETE_POWDER_BY_COLOR = {
            WHITE_CONCRETE_POWDER, ORANGE_CONCRETE_POWDER, MAGENTA_CONCRETE_POWDER,
            LIGHT_BLUE_CONCRETE_POWDER, YELLOW_CONCRETE_POWDER, LIME_CONCRETE_POWDER,
            PINK_CONCRETE_POWDER, GRAY_CONCRETE_POWDER, LIGHT_GRAY_CONCRETE_POWDER,
            CYAN_CONCRETE_POWDER, PURPLE_CONCRETE_POWDER, BLUE_CONCRETE_POWDER,
            BROWN_CONCRETE_POWDER, GREEN_CONCRETE_POWDER, RED_CONCRETE_POWDER,
            BLACK_CONCRETE_POWDER,
    };
    public static final int[] GLAZED_TERRACOTTA_BY_COLOR = {
            WHITE_GLAZED_TERRACOTTA, ORANGE_GLAZED_TERRACOTTA, MAGENTA_GLAZED_TERRACOTTA,
            LIGHT_BLUE_GLAZED_TERRACOTTA, YELLOW_GLAZED_TERRACOTTA, LIME_GLAZED_TERRACOTTA,
            PINK_GLAZED_TERRACOTTA, GRAY_GLAZED_TERRACOTTA, LIGHT_GRAY_GLAZED_TERRACOTTA,
            CYAN_GLAZED_TERRACOTTA, PURPLE_GLAZED_TERRACOTTA, BLUE_GLAZED_TERRACOTTA,
            BROWN_GLAZED_TERRACOTTA, GREEN_GLAZED_TERRACOTTA, RED_GLAZED_TERRACOTTA,
            BLACK_GLAZED_TERRACOTTA,
    };

    /** 콘크리트 가루인가. 중력·물 경화 판정의 단일 술어다(이름 16개 나열 금지). */
    public static boolean isConcretePowder(int id) {
        return id >= WHITE_CONCRETE_POWDER && id <= BLACK_CONCRETE_POWDER;
    }

    /** 콘크리트인가. */
    public static boolean isConcrete(int id) {
        return id >= WHITE_CONCRETE && id <= BLACK_CONCRETE;
    }

    /** 유광 테라코타인가. */
    public static boolean isGlazedTerracotta(int id) {
        return id >= WHITE_GLAZED_TERRACOTTA && id <= BLACK_GLAZED_TERRACOTTA;
    }

    /** 색 테라코타(기존 6색 + 신규 10색)인가. 총칭 TERRACOTTA(236)는 포함하지 않는다. */
    public static boolean isColoredTerracotta(int id) {
        if (id >= MAGENTA_TERRACOTTA && id <= BLACK_TERRACOTTA) return true;
        for (int terracotta : TERRACOTTA_BY_COLOR) {
            if (terracotta == id) return true;
        }
        return false;
    }

    /** 가루가 물에 닿아 굳은 결과. 가루가 아니면 그대로 돌려준다. */
    public static int concreteForPowder(int id) {
        return isConcretePowder(id) ? id - CONCRETE_POWDER_TO_CONCRETE : id;
    }

    // ── [STAINED-GLASS] 색 유리 16색 1141~1156 · 색 유리판 16색 1157~1172(MC Java 1.21.4). ──
    //
    // 색 순서는 **DyeColor 네트워크 ID** 다(흰색 0 … 검은색 15). 염료 452~467 · 양털 561~592 ·
    // 침대 851~866 · 콘크리트 1076~1107 이 이미 쓰는 규약이라 "색 인덱스 = ID − 계열 시작" 이
    // 그대로 성립하고, 지지 분류·물성·형상 판정이 전부 범위 술어 한 줄로 끝난다.
    //
    // 물성은 무색 유리와 같다: destroy_time 0.3 · explosion_resistance 0.3 · 도구 요구 없음.
    // 렌더는 새 아틀라스 타일을 하나도 쓰지 않는다(무색 유리 타일 + colorTint 곱).
    //
    // 전부 제작 전용(placement-only)이라 지형 생성기·runtime overlay·구조물이 만들지 않는다
    // (golden terrain 해시 불변). 1173~1175 는 이 트랙 예약 공백이라 되메우지 않는다.
    /** white_stained_glass. 0.3 / 0.3 · 도구 요구 없음 · 실크 터치 없이는 드랍 없음. */
    public static final int WHITE_STAINED_GLASS = 1141;
    public static final int ORANGE_STAINED_GLASS = 1142;
    public static final int MAGENTA_STAINED_GLASS = 1143;
    public static final int LIGHT_BLUE_STAINED_GLASS = 1144;
    public static final int YELLOW_STAINED_GLASS = 1145;
    public static final int LIME_STAINED_GLASS = 1146;
    public static final int PINK_STAINED_GLASS = 1147;
    public static final int GRAY_STAINED_GLASS = 1148;
    public static final int LIGHT_GRAY_STAINED_GLASS = 1149;
    public static final int CYAN_STAINED_GLASS = 1150;
    public static final int PURPLE_STAINED_GLASS = 1151;
    public static final int BLUE_STAINED_GLASS = 1152;
    public static final int BROWN_STAINED_GLASS = 1153;
    public static final int GREEN_STAINED_GLASS = 1154;
    public static final int RED_STAINED_GLASS = 1155;
    public static final int BLACK_STAINED_GLASS = 1156;
    /** white_stained_glass_pane. 무색 유리판(92)과 같은 GlassPaneBlock 형상·물성이다. */
    public static final int WHITE_STAINED_GLASS_PANE = 1157;
    public static final int ORANGE_STAINED_GLASS_PANE = 1158;
    public static final int MAGENTA_STAINED_GLASS_PANE = 1159;
    public static final int LIGHT_BLUE_STAINED_GLASS_PANE = 1160;
    public static final int YELLOW_STAINED_GLASS_PANE = 1161;
    public static final int LIME_STAINED_GLASS_PANE = 1162;
    public static final int PINK_STAINED_GLASS_PANE = 1163;
    public static final int GRAY_STAINED_GLASS_PANE = 1164;
    public static final int LIGHT_GRAY_STAINED_GLASS_PANE = 1165;
    public static final int CYAN_STAINED_GLASS_PANE = 1166;
    public static final int PURPLE_STAINED_GLASS_PANE = 1167;
    public static final int BLUE_STAINED_GLASS_PANE = 1168;
    public static final int BROWN_STAINED_GLASS_PANE = 1169;
    public static final int GREEN_STAINED_GLASS_PANE = 1170;
    public static final int RED_STAINED_GLASS_PANE = 1171;
    public static final int BLACK_STAINED_GLASS_PANE = 1172;
    // 1173~1175는 stained-glass 트랙 예약 공백이라 되메우지 않는다.
    /** Java 1.21.4 fire. 상태 하위 4비트는 AGE 0..15이고 부착 형상은 이웃에서 파생한다. */
    public static final int FIRE = 1176;
    /** 고유 world map 저장 레코드를 가리키는 순수 아이템. */
    public static final int FILLED_MAP = 1177;
    public static final int ABANDONED_CAMPSITE_MAP = 2499;
    public static final int ANCIENT_CITY_MAP = 2500;
    public static final int BURIED_TREASURE_MAP = 2501;
    public static final int DESERT_PYRAMID_MAP = 2502;
    public static final int DESERT_VILLAGE_MAP = 2503;
    public static final int JUNGLE_EXPLORER_MAP = 2504;
    public static final int MINESHAFT_MAP = 2505;
    public static final int OCEAN_EXPLORER_MAP = 2506;
    public static final int PLAINS_VILLAGE_MAP = 2507;
    public static final int SAVANNA_VILLAGE_MAP = 2508;
    public static final int SNOWY_VILLAGE_MAP = 2509;
    public static final int SWAMP_EXPLORER_MAP = 2510;
    public static final int TAIGA_VILLAGE_MAP = 2511;
    public static final int TRIAL_EXPLORER_MAP = 2512;
    public static final int WARM_OCEAN_RUINS_MAP = 2513;
    public static final int WOODLAND_EXPLORER_MAP = 2514;
    /** 치명 피해를 한 번 막는 순수 아이템. */
    public static final int TOTEM_OF_UNDYING = 1178;
    // [ROTTEN-LEATHER] 썩은 가죽 재료 + 방어구 4부위 1179~1183. 1134~1140(콘크리트)·843~850
    // (제련로) 같은 **다른 트랙의 예약 공백**은 되메우지 않고 직전 high-water 뒤에 append 한다.
    /** 좀비 동물이 떨구는 부패한 가죽. 썩은 가죽 방어구의 유일한 재료다. 스택 64. */
    public static final int ROTTEN_LEATHER = 1179;
    /** 썩은 가죽 방어구 4부위(투구·흉갑·레깅스·부츠). 방어도·내구는 가죽 세트와 같다. */
    public static final int ROTTEN_LEATHER_HELMET = 1180;
    public static final int ROTTEN_LEATHER_CHESTPLATE = 1181;
    public static final int ROTTEN_LEATHER_LEGGINGS = 1182;
    public static final int ROTTEN_LEATHER_BOOTS = 1183;

    // ── [DEEP-DARK] 스컬크 블록군 1200–1204 · 말린 가스트 1250 ────────────
    //
    // 이 트랙에 배정된 구간은 1200~1250 이다. 1184~1199 는 **다른 트랙 배정 구간**이라
    // 되메우지 않고, 1205~1249 는 이 트랙의 예약 공백이다(스컬크 잔여·워든 부산물 몫).
    //
    // 근거 등급 [A] = 바닐라 데이터/코드에서 확인, [B] = 위키 본문, [C] = 이 저장소가 정한
    // 축소 계약. 스컬크 물성은 전부 [B] minecraft.wiki «Sculk»·«Sculk Shrieker» Java 판이다.
    //
    // **바이옴이 아니라 구조물 지대다**(divergence): 이 저장소에는 deep_dark 바이옴이 없어
    // 스컬크는 지형 생성이 만들지 않고 DEEP_DARK_CITY 구조물 오버레이만 배치한다.
    // golden terrain 해시는 그대로다.

    /**
     * sculk. 경도 0.2 · 폭발 저항 0.2 · 호미 최적 도구. 섬세한 손길 없이는 아무것도 떨구지
     * 않고 경험치 1 을 준다. 발광하지 않는다. [B] 위키 «Sculk» Java 판.
     */
    public static final int SCULK = 1200;
    /**
     * sculk_vein. 벽·바닥·천장에 붙는 덩굴형 오버레이다. 물성은 스컬크와 같고 형상 계약은
     * 기존 {@link #VINE} 의 면 마스크를 그대로 재사용한다(새 상태 문법을 만들지 않는다).
     * [B] 위키 «Sculk Vein» Java 판 · [C] 면 마스크 재사용은 이 저장소 계약.
     */
    public static final int SCULK_VEIN = 1201;
    /**
     * sculk_catalyst. 경도 3.0 · 호미 최적 · 경험치 5 · 광량 6. 바닐라의 "죽음으로 스컬크를
     * 번식시키는" 촉매 역할은 다음 웨이브(워든/몹 사망 훅) 몫이고 이 웨이브는 블록만 둔다.
     * [B] 위키 «Sculk Catalyst» Java 판.
     */
    public static final int SCULK_CATALYST = 1202;
    /**
     * sculk_sensor. 경도 1.5 · 호미 최적 · 경험치 5 · 광량 1. 진동 계약은
     * {@code engine.sculk.SculkVibrationRules} 가 소유한다 — 전면 레드스톤이 없으므로
     * "주변 블록 변경·발소리 이벤트를 반경 8 안에서 듣고 비명체를 깨운다" 로 축소했다. [C]
     */
    public static final int SCULK_SENSOR = 1203;
    /**
     * sculk_shrieker. 경도 3.0 · 호미 최적 · 경험치 5 · 비발광. 경고 단계 0~3 을 상태 하위
     * 2비트에 담고 4번째 활성화에서 워든 소환 훅을 부른다(훅 구현은 다음 웨이브).
     * [B] 위키 «Sculk Shrieker» Java 판.
     */
    public static final int SCULK_SHRIEKER = 1204;
    // 1205~1249 는 이 트랙의 예약 공백이다.
    /**
     * dried_ghast. 딥다크 도시 상자의 희귀 전리품이자 <b>설치 가능한 월드 블록</b>이다.
     * 물에 잠기면 수화 단계 0→3 을 밟고 가스틀링으로 소생한다 — 상태 하위 2비트가 수화
     * 단계다. 소생 대상 몹(가스틀링)은 다음 웨이브라 이 트랙은 블록 상태까지만 낸다.
     *
     * <p>바닐라 1.21.6 은 네더 화석·피글린 물물교환·제작으로 얻고 경도 0 이다 [B] 위키
     * «Dried Ghast». <b>획득 경로만 divergence</b>다 — 이 저장소에는 네더 지형이 없어
     * 사용자 지시대로 딥다크 도시 전리품으로 옮겼다. 수화 규약은 바닐라 그대로다. [C]
     */
    public static final int DRIED_GHAST = 1250;

    /** 스컬크 계열 5종(1200~1204)인가. 채굴 XP·실크 터치·호미 최적 판정이 이 하나만 본다. */
    public static boolean isSculkBlock(int id) {
        return id >= SCULK && id <= SCULK_SHRIEKER;
    }

    // ── [TRIAL] 트라이얼 챔버 기반 1251–1253 ───────────────────────────────
    //
    // 1184~1250 은 **다른 트랙 배정 구간**이라 되메우지 않는다(38c: append-only, 빈 구간
    // 되메우기 금지). 이 트랙에 배정된 구간은 1251~1270 이고 그중 셋만 쓴다.
    //
    // 근거 등급 [A] = 바닐라 1.21.4 데이터/코드에서 확인한 값, [B] = 위키 본문,
    // [C] = 이 저장소가 정한 축소 계약(바닐라에 대응이 있으나 범위를 줄인 것).

    /**
     * trial_spawner. 바닐라 1.21 트라이얼 스포너의 <b>축소 계약</b> 앵커 블록이다.
     *
     * <p>물성은 바닐라 그대로다 — destroy_time 50.0 · explosion_resistance 50.0 ·
     * 곡괭이로도 아이템을 떨구지 않는다(바닐라 trial_spawner 는 loot table 이 비어 있다).
     * [B] 위키 «Trial Spawner» Java 판.
     *
     * <p>상태 비트는 바닐라 {@code trial_spawner_state} 6 상태 코드(비트 0..2: 0=WAITING_FOR_PLAYERS,
     * 1=ACTIVE, 2=WAITING_FOR_REWARD_EJECTION, 3=EJECTING_REWARD, 4=INACTIVE, 5=COOLDOWN)와
     * 바닐라 {@code ominous} 비트 3 이다. 정본은 {@code engine/trial/TrialSpawnerContract.State}
     * 다. 불길한 변형은 별도 ID 가 아니라 이 비트다(바닐라도 같은 블록의 속성이다).
     */
    public static final int TRIAL_SPAWNER = 1251;
    /**
     * vault. 트라이얼 열쇠로 <b>플레이어 1인당 정확히 한 번</b> 열리는 전리품 블록이다.
     * 물성은 바닐라 그대로 destroy_time 50.0 · explosion_resistance 50.0 이며 드랍이 없다.
     * [B] 위키 «Vault» Java 판.
     *
     * <p>상태 비트는 {@code vault_state} 중 0=INACTIVE, 1=ACTIVE, 2=UNLOCKING, 3=EJECTING
     * 넷이다. "이미 연 사람인가" 는 블록 상태가 아니라 영수증
     * ({@code engine/trial/TrialVaultReceipt}) 이 소유한다 — 상태로 담으면 플레이어마다
     * 다른 값을 한 칸에 넣을 수 없다.
     */
    public static final int VAULT = 1252;
    /**
     * trial_key. 금고를 여는 순수 아이템이다. 스택 64, 내구 없음.
     * [B] 위키 «Trial Key» Java 판. 불길한 열쇠는 {@link #OMINOUS_TRIAL_KEY} 다.
     */
    public static final int TRIAL_KEY = 1253;

    /** 트라이얼 스포너 또는 금고인가. 두 블록만 트라이얼 축소 계약의 권위 대상이다. */
    public static boolean isTrialChamberFixture(int id) {
        return id == TRIAL_SPAWNER || id == VAULT;
    }

    // ── [FURNITURE-26.3] 건초 침대 1281 · 쿠션 16색 1282–1297 ────────────────
    //
    // 1254~1280 은 **다른 트랙 배정 구간**이라 되메우지 않는다(38c: append-only, 빈 구간
    // 되메우기 금지).
    //
    // 근거 등급 [A] = 바닐라 데이터/코드에서 확인, [B] = 위키 본문, [C] = 이 저장소 자체 계약.
    // 26.3 은 이 저장소가 고정한 데이터 스냅샷(1.21.4-data-json)보다 뒤 버전이라 원문 JSON 을
    // 받을 수 없다 — 아래 수치는 전부 [B] 이고 발췌 핀은
    // {@code docs/research/mc-26.3/FURNITURE.md} 다.

    /**
     * straw_bed. 26.3 dappled forest 드롭의 <b>건초 침대</b>다.
     *
     * <p>물성 [B]: destroy_time 0.2 · explosion_resistance 0.2 · 도구 제한 없음 · 스택 16 ·
     * 머리/발 2칸 · 제작은 건초 더미 3 → 건초 침대 4.
     *
     * <p><b>수면 계약은 일반 침대와 두 가지만 다르다</b> [B]:
     * <ol>
     *   <li>개인 리스폰 지점을 <b>설정하지 않는다</b>.</li>
     *   <li>아침에 깨거나 수면 도중 침대를 떠나면 <b>아무것도 떨구지 않고 부서진다</b>(단회성).</li>
     * </ol>
     * 그 밖의 형상·상태·충돌·지지·쌍 판정은 전부 기존 침대 계약을 그대로 쓴다 — 그래서
     * {@link #isBed(int)} 가 건초 침대를 <b>포함한다</b>. 색 침대 851~866 이 그랬듯 새 shape
     * 도, 새 상태 문법도 만들지 않는다(state = facing 2비트 + {@code BED_HEAD}).
     *
     * <p>재질만 건초라 소리는 건초 더미와 같은 GRASS 음향군이고 이불 틴트가 없다 —
     * {@code colorTint} 를 주지 않는다. 전용 {@code break_leave} 사운드는 이 저장소의 음향
     * 카탈로그에 대응이 없어 내지 않는다([C] 축소).
     *
     * <p>자연 생성은 이 트랙 밖이다 — 바닐라는 abandoned camp·통 전리품에서 나오지만 야영지
     * 생성기 파일 소유가 다른 트랙이라 여기서는 <b>제작 전용(placement-only)</b> 이다([C]).
     */
    public static final int STRAW_BED = 1281;

    // 쿠션 16색 1282–1297. 색 순서는 MC {@code DyeColor} 네트워크 ID 순서이며 염료 452–467 ·
    // 양털 561–576 · 색 침대 851–866 과 문자 그대로 같다.
    //
    // <b>divergence [C]</b>: 바닐라 쿠션은 **엔티티**(높이 0.25 · 너비 1.0 · 충돌 없음 ·
    // use 로 앉기)다. 이 저장소에는 좌석(마운트) 계약을 재사용할 수 있는 설치형 엔티티 경로가
    // 없고 새로 내면 프로토콜·영속·틱 세 층을 건드려야 하므로, 이 웨이브는 **양털 계열 부분
    // 높이 월드 블록 4/16**(= 바닐라 히트박스 높이 0.25 를 그대로 옮긴 값)로 낸다. 앉기는
    // 다음 웨이브(좌석) 몫이라 이 트랙이 흉내내지 않는다.
    public static final int WHITE_CUSHION = 1282;
    public static final int ORANGE_CUSHION = 1283;
    public static final int MAGENTA_CUSHION = 1284;
    public static final int LIGHT_BLUE_CUSHION = 1285;
    public static final int YELLOW_CUSHION = 1286;
    public static final int LIME_CUSHION = 1287;
    public static final int PINK_CUSHION = 1288;
    public static final int GRAY_CUSHION = 1289;
    public static final int LIGHT_GRAY_CUSHION = 1290;
    public static final int CYAN_CUSHION = 1291;
    public static final int PURPLE_CUSHION = 1292;
    public static final int BLUE_CUSHION = 1293;
    public static final int BROWN_CUSHION = 1294;
    public static final int GREEN_CUSHION = 1295;
    public static final int RED_CUSHION = 1296;
    public static final int BLACK_CUSHION = 1297;

    /**
     * 색 인덱스(0..15) → 쿠션 블록 ID. 인덱스는 MC {@code DyeColor} 네트워크 ID이며
     * 염료 452–467 · {@link #WOOL_BY_DYE_COLOR} · {@link #BED_BY_DYE_COLOR} 와 같은 순서다.
     */
    public static final int[] CUSHION_BY_DYE_COLOR = {
            WHITE_CUSHION, ORANGE_CUSHION, MAGENTA_CUSHION, LIGHT_BLUE_CUSHION,
            YELLOW_CUSHION, LIME_CUSHION, PINK_CUSHION, GRAY_CUSHION,
            LIGHT_GRAY_CUSHION, CYAN_CUSHION, PURPLE_CUSHION, BLUE_CUSHION,
            BROWN_CUSHION, GREEN_CUSHION, RED_CUSHION, BLACK_CUSHION,
    };

    /**
     * [FURNITURE-26.3] 건초 침대인가. 일반 침대와 갈라지는 세 지점 — 리스폰 지점을 설정하지
     * 않는다 · 수면이 끝나면 드랍 없이 부서진다 · 경도가 0.4 가 아니라 0.2 다 — 만 이 판정을
     * 보고, 나머지는 {@link #isBed(int)} 가 묶어 색·재질과 무관하게 침대로 다룬다.
     */
    public static boolean isStrawBed(int id) {
        return id == STRAW_BED;
    }

    /** [FURNITURE-26.3] 쿠션 16색(1282~1297)인가. 양털 물성·낙하 감쇠 판정이 이 하나만 본다. */
    public static boolean isCushion(int id) {
        return id >= WHITE_CUSHION && id <= BLACK_CUSHION;
    }

    // 1301~1310 were removed before the 26.3 replacement and stay reserved forever.
    /** Minecraft 26.3 sulfur. */
    public static final int SULFUR_BLOCK = 1300;
    /** Removed pre-26.3 placeholders; numeric slots remain permanently reserved. */
    public static final int REMOVED_SULFUR_1301 = 1301;
    public static final int REMOVED_SULFUR_1302 = 1302;
    public static final int REMOVED_SULFUR_1303 = 1303;
    public static final int REMOVED_SULFUR_1304 = 1304;
    public static final int REMOVED_SULFUR_1305 = 1305;
    public static final int REMOVED_SULFUR_1306 = 1306;
    public static final int REMOVED_SULFUR_1307 = 1307;
    public static final int REMOVED_SULFUR_1308 = 1308;
    public static final int REMOVED_SULFUR_1309 = 1309;
    public static final int REMOVED_SULFUR_1310 = 1310;

    // ── [PROP-MATERIAL] 소품·재료 1320–1341 ────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1320~1349 다. 그중 1320~1335 는 **팻말 웨이브 예약 구간**이라
    // 이 창에서는 만들지 않는다(팻말은 텍스트 편집·부속 데이터·월드 텍스트 렌더가 함께 와야
    // 하는 블록이라 껍데기 등록이 곧 스텁이다 — AGENTS §39d). 1342~1349 는 예약 공백이다.
    // 확정된 팻말 계약은 docs/research/mc-vanilla-prop-material.md 가 소유한다.
    //
    // 근거 등급 [A] = 바닐라 클래스/데이터에서 확인, [B] = minecraft.wiki 본문.

    /**
     * chain. 바닐라 {@code ChainBlock extends RotatedPillarBlock} 이라 축 세 형태를 가지고,
     * 이 저장소는 원목·석영 기둥 선례대로 축을 상태가 아니라 <b>ID 세 개</b>로 표현한다
     * (1336=Y · 1337=X · 1338=Z). 물성은 바닐라 그대로 destroy_time 5.0 ·
     * explosion_resistance 6.0 · mineable/pickaxe(needs_*_tool 태그 없음)이고, 형상은
     * {@code SHAPE_Y = box(6.5,0,6.5,9.5,16,9.5)} 다. [A] {@code ChainBlock} / [B] 위키 «Chain».
     */
    public static final int CHAIN = 1336;
    /** chain[axis=x]. */
    public static final int CHAIN_X = 1337;
    /** chain[axis=z]. */
    public static final int CHAIN_Z = 1338;
    /**
     * lantern. 바닐라 {@code LanternBlock} — 광량 15 · destroy_time 3.5 ·
     * explosion_resistance 3.5 · mineable/pickaxe 이고 needs_*_tool 태그가 없어 티어 요구가
     * 없다. 이미 등록된 구리 랜턴(1064)과 같은 형상 계약을 쓰며 매달림(hanging) 상태는
     * 구리 랜턴과 같이 아직 표현하지 않는 문서화된 divergence 다. [A] {@code LanternBlock}.
     */
    public static final int LANTERN = 1339;
    /**
     * 철 조각. 바닐라 {@code iron_nugget} 순수 아이템이다. 주괴 1↔9 양방향 제작과 철 도구·
     * 방어구·사슬 갑옷 제련({@code iron_nugget_from_smelting}, 경험치 0.1)으로 얻고
     * 랜턴 8+횃불 · 사슬 2+주괴 · 이름표 1+종이로 나간다. [B] 위키 «Iron Nugget».
     */
    public static final int IRON_NUGGET = 1340;
    /**
     * 구리 조각. 바닐라 1.21.9(25w31a) {@code copper_nugget} 순수 아이템이다. 이미 등록된
     * 구리 랜턴(1064)의 바닐라 레시피 틀이 <b>주괴가 아니라 조각 8</b> 이라, 이 조각이 없으면
     * 그 레시피를 바닐라 원본으로 되돌릴 수 없다. [B] 위키 «Copper Nugget».
     */
    public static final int COPPER_NUGGET = 1341;
    /**
     * [ZOMBIE-ANIMAL] 상한 달걀. 좀비 닭이 죽을 때만 나오는 <b>순수 아이템</b>이며 바닐라에
     * 대응물이 없는 WebCraft 자체 계약이다(근거 등급 C — 계약 정본은
     * {@code ZombieChickenRules}). 바닐라 달걀({@link #EGG})의 세 성질 중 둘만 물려받는다:
     * 던질 수 있고(같은 {@code ProjectileSim.Kind.EGG} 경로), 제련 대상이 아니다.
     * <b>부화 확률은 0</b> 이며 섭취는 썩은 살점과 <b>같은 정본 상수</b>를 참조한다(사본 금지).
     */
    public static final int SPOILED_EGG = 1410;

    // ── [GOLD-FOOD] 황금·특수 식품 1730~1733 ───────────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1730~1759 이고 1734~1759 는 예약 공백이다. 1637~1729 ·
    // 1760~ 은 다른 트랙 배정 구간이라 되메우지 않는다.
    //
    // 이미 저장소에 있는 황금 식품 둘(황금사과 301 · 황금 당근 409)은 <b>새 ID 를 만들지
    // 않는다</b> — 이 절은 그 둘에 없던 획득 경로만 덧붙이고(`ExplorationLoot`), 아래 넷만
    // 새로 등록한다. 넷 중 셋은 순수 아이템이라 {@link #BLOCK_ID_HIGH_WATER} 를 올리지
    // 않고, 황금 민들레만 <b>월드 블록</b>이라 올린다.
    //
    // <b>원본 레시피 무결성</b>: 마법이 부여된 황금 사과는 바닐라 1.9(15w44a)부터 제작이
    // 삭제돼 <b>제작식을 만들지 않는다</b>([A] 변경 로그 · [B] minecraft.wiki
    // «Enchanted Golden Apple»). 황금 민들레만 바닐라 제작식(민들레 1 + 금 조각 8)을
    // 그대로 쓰고 재료 대체는 없다 — 둘 다 이미 저장소에 있다(FLOWER_YELLOW=13 ·
    // GOLD_NUGGET=342). 후렴과·꿀이 든 병은 바닐라 원천(엔드 섬 후렴화 · 벌집)이 이
    // 저장소에 없어 <b>획득 경로 divergence</b> 로만 열린다(상자 전리품 · 금고 전리품).
    /**
     * enchanted_golden_apple. <b>바닐라 실존 아이템</b>이며 순수 아이템이다.
     *
     * <p><b>제작 불가</b>: 바닐라 1.9 snapshot 15w44a 에서 금 블록 8 + 사과 제작식이 삭제돼
     * 그 뒤로 재생 불가 전리품 전용이다. 이 저장소도 제작식을 만들지 않는다.
     *
     * <p>획득 경로는 바닐라 전리품 표 중 이 저장소에 실재하는 자리만 연다 — 대저택 pool 1
     * 가중치 2/127(바닐라 원문 그대로, 지금까지 EMPTY 자리표였다)와 레이드 승리 보상 풀이다.
     * 효과·영양은 {@code PlayerTickState.consumeEnchantedGoldenApple} 이 소유한다. 스택 64.
     * [B] minecraft.wiki «Enchanted Golden Apple».
     */
    public static final int ENCHANTED_GOLDEN_APPLE = 1730;
    /**
     * chorus_fruit. <b>바닐라 실존 아이템</b>이며 순수 아이템이다. 바닐라 원천인 엔드 섬의
     * 후렴화가 이 저장소에 없어(엔드 차원 자체가 없다) 획득 경로를 <b>구조물 상자 낮은
     * 확률</b>로 옮긴 divergence 다 — 네더 석영·밀랍과 같은 선례이며 재료 대체가 아니다.
     *
     * <p>취식 효과는 바닐라 그대로다: 만복에서도 먹히고(always edible) ±8 블록 안에서 최대
     * 16 회 굴려 안전 지점으로 순간이동한다. 스택 64. [B] minecraft.wiki «Chorus Fruit».
     */
    public static final int CHORUS_FRUIT = 1731;
    /**
     * honey_bottle. <b>바닐라 실존 아이템</b>이며 순수 아이템이다. 바닐라 원천 둘(벌집/벌집
     * 상자에 유리병 사용 · 꿀 블록 1 + 유리병 4 제작)이 모두 이 저장소에 없다 — 꿀벌 몹
     * ({@code Bee})은 있지만 벌집·벌집 상자·꿀 블록 <b>블록</b>이 하나도 등록돼 있지 않다.
     * 그래서 제작식을 만들지 않고(없는 재료를 대체하지 않는다) 획득 경로만
     * <b>트라이얼 금고 전리품 + 구조물 상자 낮은 확률</b>로 연다.
     *
     * <p>바닐라 성질은 그대로 옮긴다: 마시면 독을 해제하고 빈 유리병({@link #GLASS_BOTTLE})을
     * 같은 칸에 돌려주며, 스택 상한이 <b>16</b> 이다. [B] minecraft.wiki «Honey Bottle».
     */
    public static final int HONEY_BOTTLE = 1732;
    /**
     * golden_dandelion. <b>바닐라 실존 블록</b>이다 — Java 26.1 "Tiny Takeover" 가 추가한
     * 교차 평면 꽃이며 제작으로만 얻는다(민들레 1 + 금 조각 8). 이 저장소의 민들레는
     * {@link #FLOWER_YELLOW}=13 이므로 재료 대체 없이 바닐라 원본 제작식이 성립한다.
     *
     * <p>물성도 바닐라 그대로다: 경도 0 · 폭발 저항 0 · 즉시 파괴 · 아래 흙/잔디 지지가
     * 필요하고 지지를 잃으면 자기 자신을 드랍한다({@link #FLOWER_YELLOW} 와 같은 계약).
     * 지형 생성기·overlay·구조물이 어느 경로로도 만들지 않는다
     * (worldPresence.placementOnlyIds → golden terrain 해시 불변).
     *
     * <p><b>미구현 divergence</b>: 바닐라의 주 용도인 "아기 몹에게 먹여 성장 정지"는 몹
     * 파일이 다른 웨이브 소유라 이 트랙이 만들지 않는다. 피글린 관심·벌집 묘목 5% 도 같은
     * 이유(피글린·벌집 부재)로 없다. [B] minecraft.wiki «Golden Dandelion».
     */
    public static final int GOLDEN_DANDELION = 1733;

    // ── [POTION-GAP] 힘 · 수중 호흡 · 도약 · 야간 투시 물약 1760~1779 ────────────────
    //
    // 이 트랙에 배정된 구간은 1760~1789 이고 1780~1789 는 예약 공백이다. 1637~1759 는
    // 다른 트랙 배정 구간이라 되메우지 않는다. 전부 순수 아이템이라
    // {@link #BLOCK_ID_HIGH_WATER} 를 올리지 않는다.
    //
    // <b>원본 레시피 무결성</b>: 네 사슬 모두 바닐라 {@code PotionBrewing} 표 그대로다 —
    // 어색한 물약 + 블레이즈 가루 / 복어 / 토끼 발 / 황금 당근이며 재료 대체는 없다. 네 재료가
    // 이미 전부 저장소에 있으므로(블레이즈 가루 1603 · 복어 451 · 토끼 발 357 · 황금 당근 409)
    // 이 트랙은 <b>재료를 하나도 신설하지 않고</b> 물약 ID 만 append 한다.
    //
    // 강화 규약은 기존 [POTION-UPGRADE] 절과 같다: 레드스톤 = 연장, 발광석 가루 = II 등급이고
    // 한 병에 둘을 같이 걸 수 없다. 바닐라에 II 가 없는 수중 호흡·야간 투시는 II 를 만들지
    // 않는다([B] minecraft.wiki «Potion» 표 · 1.21.4 {@code Potions} 등록부).
    //
    // 각 사슬의 <b>마시는 쪽 → 투척 쪽</b> 나열 순서가 같아 {@code PotionRules} 가 기존 세
    // 구간과 같은 "상수 오프셋" 산술을 그대로 쓴다(힘·도약 오프셋 3 · 수중 호흡·야간 투시 2).
    /** 힘의 물약 3:00 (근접 피해 +3). 어색한 물약 + 블레이즈 가루(바닐라 그대로). */
    public static final int POTION_STRENGTH = 1760;
    /** 힘의 물약 (연장) 8:00. 힘의 물약 + 레드스톤. */
    public static final int POTION_STRENGTH_LONG = 1761;
    /** 힘의 물약 II 1:30 (근접 피해 +6). 힘의 물약 + 발광석 가루. */
    public static final int POTION_STRENGTH_II = 1762;
    /** 투척용 힘의 물약. 마시는 셋과 나열 순서가 같아 {@code id + 3} 으로 대응한다. */
    public static final int SPLASH_POTION_STRENGTH = 1763;
    /** 투척용 힘의 물약 (연장). */
    public static final int SPLASH_POTION_STRENGTH_LONG = 1764;
    /** 투척용 힘의 물약 II. */
    public static final int SPLASH_POTION_STRENGTH_II = 1765;
    /** 수중 호흡 물약 3:00. 어색한 물약 + 복어(바닐라 그대로). 바닐라에 II 등급이 없다. */
    public static final int POTION_WATER_BREATHING = 1766;
    /** 수중 호흡 물약 (연장) 8:00. 수중 호흡 물약 + 레드스톤. */
    public static final int POTION_WATER_BREATHING_LONG = 1767;
    /** 투척용 수중 호흡 물약. 마시는 둘과 나열 순서가 같아 {@code id + 2} 로 대응한다. */
    public static final int SPLASH_POTION_WATER_BREATHING = 1768;
    /** 투척용 수중 호흡 물약 (연장). */
    public static final int SPLASH_POTION_WATER_BREATHING_LONG = 1769;
    /** 도약의 물약 3:00 (점프 +0.1 배). 어색한 물약 + 토끼 발(바닐라 그대로). */
    public static final int POTION_LEAPING = 1770;
    /** 도약의 물약 (연장) 8:00. 도약의 물약 + 레드스톤. */
    public static final int POTION_LEAPING_LONG = 1771;
    /** 도약의 물약 II 1:30. 도약의 물약 + 발광석 가루. */
    public static final int POTION_LEAPING_II = 1772;
    /** 투척용 도약의 물약. 마시는 셋과 나열 순서가 같아 {@code id + 3} 으로 대응한다. */
    public static final int SPLASH_POTION_LEAPING = 1773;
    /** 투척용 도약의 물약 (연장). */
    public static final int SPLASH_POTION_LEAPING_LONG = 1774;
    /** 투척용 도약의 물약 II. */
    public static final int SPLASH_POTION_LEAPING_II = 1775;
    /** 야간 투시 물약 3:00. 어색한 물약 + 황금 당근(바닐라 그대로). 바닐라에 II 등급이 없다. */
    public static final int POTION_NIGHT_VISION = 1776;
    /** 야간 투시 물약 (연장) 8:00. 야간 투시 물약 + 레드스톤. */
    public static final int POTION_NIGHT_VISION_LONG = 1777;
    /** 투척용 야간 투시 물약. 마시는 둘과 나열 순서가 같아 {@code id + 2} 로 대응한다. */
    public static final int SPLASH_POTION_NIGHT_VISION = 1778;
    /** 투척용 야간 투시 물약 (연장). */
    public static final int SPLASH_POTION_NIGHT_VISION_LONG = 1779;

    // ── [HARNESS] 하네스 16색 1790~1805 ────────────────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1790~1809 이고 1806~1809 는 예약 공백이다. 1780~1789 는
    // [POTION-GAP] 트랙 예약 공백이라 되메우지 않는다. 전부 <b>순수 아이템</b>이라
    // {@link #BLOCK_ID_HIGH_WATER} 를 올리지 않고, 월드 타일이 없으므로 <b>새 아틀라스 슬롯을
    // 0개</b> 쓴다(아이콘은 {@code client/src/ui/icons.ts} 의 절차적 그리드다). 이 트랙에
    // 예약해 둔 아틀라스 밴드 810~829 는 그래서 비워 둔 채 남는다.
    //
    // <b>원본 레시피 무결성</b>: [B] minecraft.wiki «Harness» 원문 배치 그대로다 —
    // 윗줄 가죽 3, 가운뎃줄 유리 1 + 그 색 양털 1 + 유리 1, 아랫줄 비움 → 그 색 하네스 1.
    // 재료를 하나도 신설하지 않는다: 가죽({@code PlayerInventory.LEATHER} 334) · 유리
    // ({@link #GLASS} 10) · 색 양털({@link #WOOL_BY_DYE_COLOR} 561~576) 이 이미 전부 있다.
    // 양털이 <b>색 정확 일치</b>인 것도 바닐라 그대로다(태그가 아니라 색마다 별도 레시피).
    //
    // 색 순서는 MC {@code DyeColor} 네트워크 ID 순서이고 {@link #WOOL_BY_DYE_COLOR} ·
    // {@link #CUSHION_BY_DYE_COLOR} 와 <b>같은 순서</b>다. 그래서 {@link #HARNESS_BY_DYE_COLOR}
    // 의 인덱스가 곧 하네스 색이며, 해피 가스트가 들고 있는 색 상태와 같은 어휘를 쓴다.
    /** 흰색 하네스. 해피 가스트 성체에게 씌우면 좌석 4 개가 열린다. */
    public static final int WHITE_HARNESS = 1790;
    public static final int ORANGE_HARNESS = 1791;
    public static final int MAGENTA_HARNESS = 1792;
    public static final int LIGHT_BLUE_HARNESS = 1793;
    public static final int YELLOW_HARNESS = 1794;
    public static final int LIME_HARNESS = 1795;
    public static final int PINK_HARNESS = 1796;
    public static final int GRAY_HARNESS = 1797;
    public static final int LIGHT_GRAY_HARNESS = 1798;
    public static final int CYAN_HARNESS = 1799;
    public static final int PURPLE_HARNESS = 1800;
    public static final int BLUE_HARNESS = 1801;
    public static final int BROWN_HARNESS = 1802;
    public static final int GREEN_HARNESS = 1803;
    public static final int RED_HARNESS = 1804;
    /** 검은색 하네스. 이 구간의 마지막 ID 이자 현재 {@link #PROTOCOL_ID_HIGH_WATER} 다. */
    public static final int BLACK_HARNESS = 1805;

    /**
     * MC {@code DyeColor} 네트워크 ID(0..15) → 하네스 아이템 ID. {@link #WOOL_BY_DYE_COLOR} ·
     * {@link #CUSHION_BY_DYE_COLOR} 와 같은 순서이며, 인덱스가 곧 해피 가스트가 저장하는
     * 하네스 색이다. 표 하나만 도는 등록부(레시피·아이콘·복원)가 이 배열을 읽는다.
     */
    public static final int[] HARNESS_BY_DYE_COLOR = {
            WHITE_HARNESS, ORANGE_HARNESS, MAGENTA_HARNESS, LIGHT_BLUE_HARNESS,
            YELLOW_HARNESS, LIME_HARNESS, PINK_HARNESS, GRAY_HARNESS,
            LIGHT_GRAY_HARNESS, CYAN_HARNESS, PURPLE_HARNESS, BLUE_HARNESS,
            BROWN_HARNESS, GREEN_HARNESS, RED_HARNESS, BLACK_HARNESS,
    };

    /** 하네스 아이템 ID 인가. 색 구간 하나라 산술 한 줄이다. */
    public static boolean isHarnessItem(int id) {
        return id >= WHITE_HARNESS && id <= BLACK_HARNESS;
    }

    /**
     * 하네스 아이템 ID → MC {@code DyeColor} 네트워크 ID(0..15). 하네스가 아니면 -1 이다
     * (양의 {@link #sheepColor} 계약과 같은 "종이 아니면 -1" 어휘).
     */
    public static int harnessDyeColor(int id) {
        return isHarnessItem(id) ? id - WHITE_HARNESS : -1;
    }

    // ── [CHEST-FAMILY] 상자 형상군 월드 블록 1810~1839 ────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1810~1839 이고 1819~1839 는 예약 공백이다. 1806~1809 는
    // [HARNESS] 트랙 예약 공백이라 되메우지 않는다. 재료 계층(블레이즈 가루 1603 ·
    // 엔더의 눈 1604 · 셜커 껍데기 1613)은 앞선 웨이브가 이미 착지시켰고, 이 절은
    // 그때 미뤄 둔 <b>월드 블록</b> 몫이다({@code docs/research/mc-26x-chests.md} §6a).
    //
    // 엔더 상자는 이 트랙이 내지 않는다 — 좌표 키가 아니라 <b>플레이어 키</b> 저장소라
    // 형상군 전환과 무관한 영속 스키마가 선행 조건이고(같은 문서 §3), 셜커 상자는
    // 아이템 한 칸이 내용 27칸을 물고 다니는 스키마가 선행 조건이다(§4). 그래서
    // 1810~1839 안에 그 둘의 자리를 미리 파 두지 않는다 — 각자 자기 구간을 새로 배정한다.
    //
    // 아틀라스 배정 밴드는 830~849 이고 이 절이 실제로 쓰는 것은 830~838 아홉 장이다
    // (덫 상자 정면 1 + 구리 상자 top/side/front × 산화 4 = 8). 밀랍 4종은 슬롯을 쓰지
    // 않는다 — 구리 계열 공통 규약대로 밀랍은 원본과 같은 텍스처다.

    /**
     * [CHEST-FAMILY] 덫 상자. 바닐라 제작식 그대로 <b>상자 1 + 철사 덫 갈고리 1</b> 무정형이며
     * ({@link #TRIPWIRE_HOOK}=488 이 이미 있어 재료를 하나도 신설하지 않는다) 물성도 일반
     * 상자와 같은 값이다 — destroy_time 2.5 · explosion_resistance 2.5 · 도끼가 가장 빠름 ·
     * 자기 자신 드랍. 겉모습은 상자와 같은 텍스처에 <b>잠금쇠만 붉은색</b>이다.
     *
     * <p><b>divergence [C]</b>: 이 저장소에는 <b>레드스톤이 없다</b>. 바닐라 덫 상자의 유일한
     * 존재 이유인 "열고 있는 사람 수만큼(최대 15) 신호를 낸다"를 낼 대상이 없으므로, 이
     * 저장소의 덫 상자는 <b>외형·전리품 배치용</b>이며 일반 상자와 완전히 같은 27/54칸
     * 보관함으로 동작한다. 열림 인원 수를 세는 계약을 두지 않는다 — 레드스톤이 생기면
     * 셀 지점은 이미 있는 상자 구독자 집합(양 권위의 {@code chestSubscribersByPosition})
     * 하나뿐이다.
     *
     * <p>짝 판정은 {@link #chestPairs(int, int)} 가 소유한다: 덫 상자는 <b>덫 상자끼리만</b>
     * 짝을 이루고 일반 상자와 나란히 놓아도 합쳐지지 않는다([B] 바닐라 그대로).
     */
    public static final int TRAPPED_CHEST = 1810;

    // 구리 상자 8종(1811~1818)의 ID 는 이 계열 표보다 **먼저** 선언돼야 하므로 위
    // 구리 절(구리 사슬 바로 뒤)에 있다. 나머지 계약은 그 자리의 주석이 소유한다.

    // ── [ENDER-SHULKER] 엔더 상자 · 셜커 상자 1840~1879 ──────────────────────────
    //
    // 이 트랙에 배정된 구간은 1840~1879 이고 실제로 쓰는 것은 1840~1857 열여덟이다
    // (엔더 상자 1 + 셜커 상자 무염색 1 + 염색 16). 1859~1879 가 이 트랙의 남은 예약
    // 공백이다 — 1858 은 [DIAMOND-SHIELD] 트랙이 순수 아이템으로 가져갔다.
    // 1819~1839 는 [CHEST-FAMILY] 트랙의 예약 공백이라 <b>되메우지 않는다</b> —
    // {@code docs/research/mc-26x-chests.md} §6 이 "후속 트랙은 자기 구간을 새로 배정한다"
    // 고 못박아 둔 그대로다.
    //
    // 아틀라스 배정 밴드는 850~869 이고 실제로 쓰는 것은 850~855 여섯 장이다
    // (엔더 상자 top/side/front 3 + 셜커 상자 top/side/bottom 3). 셜커 17색은 타일을
    // 색마다 두지 않는다 — 무채색 세 장에 {@code colorTint} 정점색을 곱하는 색 침대·쿠션
    // 16색의 선례를 그대로 따른다.

    /**
     * [ENDER-SHULKER] 엔더 상자. 바닐라 제작식 그대로 <b>흑요석 8 + 엔더의 눈 1</b>(가운데)
     * 이며 재료를 하나도 신설하지 않는다({@link #OBSIDIAN} · {@link #EYE_OF_ENDER}=1604 가
     * 둘 다 이미 있다). 물성도 바닐라 그대로다 — destroy_time 22.5 · explosion_resistance 600 ·
     * 곡괭이 필요 · 광량 7.
     *
     * <p><b>드랍</b>: 섬세한 손길이 없으면 <b>흑요석 8</b>, 있으면 자기 자신이다
     * ([A] {@code minecraft:blocks/ender_chest} 전리품표). 이 저장소에는 섬세한 손길이
     * 이미 있어({@code EnchantmentRules.SILK_TOUCH}) 바닐라 두 갈래를 그대로 옮긴다.
     *
     * <p><b>저장소가 좌표 키가 아니다.</b> 형상군의 다른 여덟과 갈리는 유일한 지점이며,
     * 이 하나 때문에 {@link #CHEST_KIND_ENDER} 갈래가 따로 있다: 내용 27칸은 좌표가 아니라
     * <b>플레이어</b>가 소유하고, 월드 안 모든 엔더 상자가 같은 27칸을 비춘다. 그래서
     * 부숴도 내용이 사라지지 않고(블록만 사라진다) 다른 플레이어가 같은 자리를 열어도
     * 자기 27칸을 본다. 영속은 플레이어 상태에 append 되는 슬롯 밴드
     * ({@code PlayerInventory.ENDER_SLOT_BASE})가 소유한다.
     *
     * <p><b>짝을 이루지 않는다</b> — {@link #chestPairs(int, int)} 가 이 갈래를 명시적으로
     * 뺀다([B] 바닐라 {@code EnderChestBlock} 은 언제나 {@code ChestType.SINGLE} 이다).
     */
    public static final int ENDER_CHEST = 1840;

    /**
     * [ENDER-SHULKER] 셜커 상자(무염색). 바닐라 제작식 그대로 <b>셜커 껍데기 2 + 상자 1</b>
     * 세로이며 재료를 하나도 신설하지 않는다({@link #SHULKER_SHELL}=1613 이 앞선 웨이브에서
     * 던전 상자 획득 경로와 함께 이미 착지했다). 물성은 destroy_time 2 ·
     * explosion_resistance 2 · 곡괭이가 가장 빠르되 <b>아무 도구로나 회수</b>된다.
     *
     * <p><b>형상군이 아니다.</b> 바닐라 셜커 상자는 {@code ShulkerBoxBlock} 이고 닫힌 상태의
     * 충돌·렌더가 <b>풀 큐브</b>라, 14/16 인셋 박스인 {@link #isChestShaped(int)} 에 들어가지
     * 않는다(통 {@link #BARREL} 이 27칸 보관함이면서도 형상군 밖인 것과 같은 이유다).
     * 보관함 여부는 {@code InteractRules.isContainer} 가 따로 본다.
     *
     * <p><b>부숴도 내용이 아이템 안에 남는다</b> — 이 저장소에서 아이템 한 칸이 내용 27칸을
     * 물고 다니는 첫 사례이며, 지도({@code mapId})가 낸 <b>간접 참조</b> 문법을 그대로 따라
     * 아이템 칸에는 {@code shulkerId} 하나만 두고 내용은 별도 저장소가 소유한다.
     */
    public static final int SHULKER_BOX = 1841;

    /**
     * [ENDER-SHULKER] 흰색 염색 셜커 상자. 여기서부터 16색이 MC {@code DyeColor} 네트워크 ID
     * 순서이며 {@link #WOOL_BY_DYE_COLOR} · {@link #HARNESS_BY_DYE_COLOR} 와 <b>같은 순서</b>다.
     */
    public static final int WHITE_SHULKER_BOX = 1842;
    /** [ENDER-SHULKER] 검은색 염색 셜커 상자. 16색 구간의 끝(1857)이다. */
    public static final int BLACK_SHULKER_BOX = 1857;

    /**
     * [DIAMOND-SHIELD] 다이아 방패. <b>바닐라에 없는 WebCraft 창작 아이템</b>이다(divergence) —
     * 마인크래프트에는 방패 티어가 하나뿐이고 다이아 방패는 존재하지 않는다.
     *
     * <p>기존 방패({@code PlayerInventory.SHIELD} 321)의 계약을 정본 삼은 상위 티어이며,
     * 막기 성능은 <b>티어와 무관하게 동일</b>하다(바닐라 방패도 정면 100% 차단이라 올릴 여지가
     * 없다). 유일한 차별점은 내구도이고, 그 값은 {@code PlayerInventory.DIAMOND_SHIELD_DURABILITY}
     * 가 다이아 티어 도구 표에서 파생한다. 아틀라스 타일이 없는 순수 아이템이라 월드 블록으로
     * 놓이지 않는다.
     */
    public static final int DIAMOND_SHIELD = 1858;

    // ── [CORAL-REEF] 산호 어휘 1880~1919 ────────────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1870~1919 이고 실제로 쓰는 것은 <b>1880~1919 마흔</b>이다.
    // 1870~1879 는 [ENDER-SHULKER] 트랙의 남은 예약 공백이라 <b>되메우지 않는다</b>(§38c
    // append-only). 1910~1919 는 이미 예약해 둔 구간을 벽 산호 부채로 정확히 채운다.
    //
    // 어휘는 [A] 핀 버전 그대로 <b>5색 × 4형상 × 2생사 = 40</b> 이다:
    //   · 산호 블록(full cube) 5 + 죽은 산호 블록 5
    //   · 산호(식물형 cross) 5 + 죽은 산호 5
    //   · 산호 부채(바닥 부착) 5 + 죽은 산호 부채 5
    //   · 벽 산호 부채(수평면 부착) 5 + 죽은 벽 산호 부채 5
    //
    // 색 순서는 [A] 바닐라 레지스트리 순서 <b>tube · brain · bubble · fire · horn</b> 이고,
    // 네 형상 모두 같은 순서라 {@code species = id − base} 산술이 네 벌 모두에서 성립한다.
    // 죽은 변형은 살아있는 변형 + {@link #CORAL_DEAD_OFFSET} 이다 — 구리 산화표가 4연속
    // ID 를 강제한 것과 같은 이유로 아래 static 검사가 이 산술을 로드 시점에 못박는다.
    //
    // 기존 단일 {@link #CORAL}(59)은 <b>그대로 둔다</b>. 자연 생성(mc_natural.rs ·
    // SurfaceDecorator)이 이미 그 ID 를 쓰고 있고 그 두 파일은 이 트랙의 불가침이라,
    // 새 어휘는 산호초 사이트 lane 과 플레이어 설치로만 등장한다.
    //
    // 아틀라스 배정 밴드는 850~899 이고 실제로 쓰는 것은 <b>860~899 마흔 장</b>이다
    // (850~855 는 [ENDER-SHULKER] 가 이미 잡았다). 형상마다 색당 한 장씩이며 죽은 변형도
    // 자기 장을 가진다 — 죽은 산호는 회색조라 틴트 곱으로 살릴 수 없다.

    /** [CORAL-REEF] 색 수(tube · brain · bubble · fire · horn). [A] 바닐라 레지스트리 순서다. */
    public static final int CORAL_SPECIES_COUNT = 5;
    /** [CORAL-REEF] 살아있는 변형 → 죽은 변형의 ID 거리. 세 형상 모두 같다. */
    public static final int CORAL_DEAD_OFFSET = CORAL_SPECIES_COUNT;

    /** [CORAL-REEF] 관 산호 블록(파랑). 산호 블록 5색 구간(1880~1884)의 시작이다. */
    public static final int TUBE_CORAL_BLOCK = 1880;
    /** [CORAL-REEF] 뇌 산호 블록(분홍). */
    public static final int BRAIN_CORAL_BLOCK = 1881;
    /** [CORAL-REEF] 거품 산호 블록(보라). */
    public static final int BUBBLE_CORAL_BLOCK = 1882;
    /** [CORAL-REEF] 불 산호 블록(빨강). */
    public static final int FIRE_CORAL_BLOCK = 1883;
    /** [CORAL-REEF] 뿔 산호 블록(노랑). 산호 블록 5색 구간의 끝이다. */
    public static final int HORN_CORAL_BLOCK = 1884;

    /** [CORAL-REEF] 죽은 관 산호 블록. 죽은 산호 블록 5색 구간(1885~1889)의 시작이다. */
    public static final int DEAD_TUBE_CORAL_BLOCK = 1885;
    /** [CORAL-REEF] 죽은 뿔 산호 블록. 죽은 산호 블록 5색 구간의 끝이다. */
    public static final int DEAD_HORN_CORAL_BLOCK = 1889;

    /** [CORAL-REEF] 관 산호(식물형). 산호 5색 구간(1890~1894)의 시작이다. */
    public static final int TUBE_CORAL = 1890;
    /** [CORAL-REEF] 뿔 산호(식물형). 산호 5색 구간의 끝이다. */
    public static final int HORN_CORAL = 1894;

    /** [CORAL-REEF] 죽은 관 산호. 죽은 산호 5색 구간(1895~1899)의 시작이다. */
    public static final int DEAD_TUBE_CORAL = 1895;
    /** [CORAL-REEF] 죽은 뿔 산호. 죽은 산호 5색 구간의 끝이다. */
    public static final int DEAD_HORN_CORAL = 1899;

    /** [CORAL-REEF] 관 산호 부채. 산호 부채 5색 구간(1900~1904)의 시작이다. */
    public static final int TUBE_CORAL_FAN = 1900;
    /** [CORAL-REEF] 뿔 산호 부채. 산호 부채 5색 구간의 끝이다. */
    public static final int HORN_CORAL_FAN = 1904;

    /** [CORAL-REEF] 죽은 관 산호 부채. 죽은 산호 부채 5색 구간(1905~1909)의 시작이다. */
    public static final int DEAD_TUBE_CORAL_FAN = 1905;
    /** [CORAL-REEF] 죽은 뿔 산호 부채. 바닥 부채 구간의 마지막 ID다. */
    public static final int DEAD_HORN_CORAL_FAN = 1909;

    /** [CORAL-REEF] 관 벽 산호 부채. 살아있는 벽 부채 5색 구간(1910~1914)의 시작이다. */
    public static final int TUBE_CORAL_WALL_FAN = 1910;
    public static final int BRAIN_CORAL_WALL_FAN = 1911;
    public static final int BUBBLE_CORAL_WALL_FAN = 1912;
    public static final int FIRE_CORAL_WALL_FAN = 1913;
    public static final int HORN_CORAL_WALL_FAN = 1914;

    /** [CORAL-REEF] 죽은 관 벽 산호 부채. 죽은 벽 부채 5색 구간(1915~1919)의 시작이다. */
    public static final int DEAD_TUBE_CORAL_WALL_FAN = 1915;
    public static final int DEAD_BRAIN_CORAL_WALL_FAN = 1916;
    public static final int DEAD_BUBBLE_CORAL_WALL_FAN = 1917;
    public static final int DEAD_FIRE_CORAL_WALL_FAN = 1918;
    /** [CORAL-REEF] 죽은 뿔 벽 산호 부채. 산호 계열의 마지막 ID다. */
    public static final int DEAD_HORN_CORAL_WALL_FAN = 1919;

    /**
     * [TRIDENT] 삼지창. 순수 아이템이며 client {@code items.ts} 의 {@code TRIDENT} 와 같은 값이다.
     *
     * <p>[A] 1.21.4 {@code item_components} 원문(docs/research/mc-trident-1214.md §1):
     * {@code max_damage} 250 · {@code max_stack_size} 1 ·
     * {@code attack_damage} <b>+8</b>(플레이어 기본 1 을 더해 최종 <b>9</b>) ·
     * {@code attack_speed} -2.9(기본 4 를 더해 <b>1.1</b>) · {@code rarity} rare.
     *
     * <p><b>수리 재료도 제작법도 없다.</b> 같은 원문 덤프에서 {@code iron_sword}·{@code shield}
     * 는 {@code minecraft:repairable} 을 갖는데 {@code trident}·{@code bow} 는 갖지 않는다 —
     * 덤프에 repairable 성분 자체가 존재하므로 삼지창의 부재는 수집 누락이 아니라 바닐라
     * 사실이다. 그래서 수리 재료를 배선하지 않았다(배선했다면 그쪽이 divergence 다).
     * 입수 경로는 드라운드 장비 드랍 하나뿐이다.
     */
    public static final int TRIDENT = 1920;

    /**
     * [CORAL-REEF] <b>살아있는</b> 산호 블록(풀 큐브) 5색인가.
     * client {@code blocks.ts} 의 {@code isLiveCoralBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isLiveCoralBlock(int id) {
        return id >= TUBE_CORAL_BLOCK && id <= HORN_CORAL_BLOCK;
    }

    /** [CORAL-REEF] <b>죽은</b> 산호 블록 5색인가. */
    public static boolean isDeadCoralBlock(int id) {
        return id >= DEAD_TUBE_CORAL_BLOCK && id <= DEAD_HORN_CORAL_BLOCK;
    }

    /** [CORAL-REEF] 산호 블록(생사 무관) 10 종인가. */
    public static boolean isCoralBlock(int id) {
        return id >= TUBE_CORAL_BLOCK && id <= DEAD_HORN_CORAL_BLOCK;
    }

    /** [CORAL-REEF] <b>살아있는</b> 산호(식물형) 5색인가. */
    public static boolean isLiveCoralPlant(int id) {
        return id >= TUBE_CORAL && id <= HORN_CORAL;
    }

    /** [CORAL-REEF] <b>살아있는</b> 산호 부채 5색인가. */
    public static boolean isLiveCoralFan(int id) {
        return id >= TUBE_CORAL_FAN && id <= HORN_CORAL_FAN;
    }

    /** [CORAL-REEF] <b>살아있는</b> 벽 산호 부채 5색인가. */
    public static boolean isLiveCoralWallFan(int id) {
        return id >= TUBE_CORAL_WALL_FAN && id <= HORN_CORAL_WALL_FAN;
    }

    /**
     * [CORAL-REEF] 산호 식물형 · 부채(생사 무관) 30 종인가. 기존 단일 {@link #CORAL}(59)은
     * 이 계열에 <b>들어가지 않는다</b> — 자연 생성이 그 ID 를 쓰고 있고 전이 계약도 없다.
     */
    public static boolean isCoralPlantOrFan(int id) {
        return id >= TUBE_CORAL && id <= DEAD_HORN_CORAL_WALL_FAN;
    }

    /** [CORAL-REEF] 이 트랙이 등록한 산호 어휘 40 종인가(기존 {@link #CORAL} 59 는 제외). */
    public static boolean isCoralFamily(int id) {
        return id >= TUBE_CORAL_BLOCK && id <= DEAD_HORN_CORAL_WALL_FAN;
    }

    /**
     * [CORAL-REEF] 살아있는 산호 어휘인가 — 물이 마르면 {@link #deadCoral(int)} 로 전이하는
     * 집합이다. 죽은 변형과 기존 {@link #CORAL} 은 {@code false} 다.
     */
    public static boolean isLiveCoral(int id) {
        return isLiveCoralBlock(id) || isLiveCoralPlant(id) || isLiveCoralFan(id)
                || isLiveCoralWallFan(id);
    }

    /**
     * [CORAL-REEF] 살아있는 산호 → 같은 색·같은 형상의 죽은 변형. 살아있는 산호가 아니면
     * 입력을 그대로 돌려준다(호출부가 조용히 다른 블록을 놓지 않게).
     *
     * <p>[A] 바닐라 {@code CoralBlock} · {@code BaseCoralPlantTypeBlock} 의 랜덤틱 전이
     * 대상과 같다. 산술이 성립하려면 네 형상 모두 살아있는 5색 뒤에 죽은 5색이 <b>붙어</b>
     * 있어야 하고, 아래 static 검사가 그 배치를 로드 시점에 확인한다.
     */
    public static int deadCoral(int id) {
        return isLiveCoral(id) ? id + CORAL_DEAD_OFFSET : id;
    }

    // ── [CONDUIT] 콘딧 사슬 1950~1959 ──────────────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 <b>1950~1959</b> 이고 실제로 쓰는 것은 <b>1950~1951 둘</b>이다.
    // 1910~1949 는 다른 트랙(산호초 예약 공백 포함)의 구간이라 되메우지 않고, 1952~1959 는
    // 이 트랙의 예약 공백이다.
    //
    // 둘뿐인 이유는 사슬의 나머지가 <b>이미 전부 있기 때문</b>이다:
    //  · 앵무조개 껍데기 486 — [FISHING] 낚시 전리품이 이미 등록했다(제작 재료 8개).
    //  · 프리즈머린 계열 934~945 — [PRISMARINE] 이 이미 등록했다(활성 프레임 재질).
    // 그래서 이 트랙이 신설하는 것은 <b>바다의 심장(순수 아이템)</b>과 <b>콘딧(월드 블록)</b>
    // 둘뿐이고, 레시피 무결성대로 없는 재료를 대체하지 않는다.
    //
    // 바다의 심장의 획득 경로(묻힌 보물 상자 전리품)는 <b>이 트랙의 표면이 아니다</b> —
    // 해저 유적·보물 트랙이 그 전리품표를 소유한다. 이 절은 ID 와 제작만 소유한다.
    //
    // 아틀라스 배정 밴드는 920~929 이고 실제로 쓰는 것은 <b>920 한 장</b>이다(콘딧 본체).
    // 바다의 심장은 순수 아이템이라 아틀라스 슬롯을 쓰지 않는다 — 2D 아이콘은 ui/icons.ts 다.

    /**
     * [CONDUIT] 바다의 심장. <b>순수 아이템</b>이라 {@link #BLOCK_ID_HIGH_WATER} 를 올리지
     * 않는다. [A] 1.21.4 {@code minecraft:heart_of_the_sea} 는 묻힌 보물 상자에서만 나오는
     * 전리품이며 제작법이 없다 — 그 획득 경로의 정본은 전리품표 쪽이고 여기서는 ID 만 만든다.
     */
    public static final int HEART_OF_THE_SEA = 1950;

    /**
     * [CONDUIT] 콘딧. <b>월드 블록</b>이라 {@link #BLOCK_ID_HIGH_WATER} 를 올린다.
     *
     * <p>[A] 1.21.4 {@code ConduitBlock} 의 블록 속성은
     * {@code Properties.of().mapColor(MapColor.DIAMOND).strength(3.0F).lightLevel(s -> 15)} 다 —
     * 즉 <b>도구 요구가 없고</b>({@code requiresCorrectToolForDrops} 미지정) 광량 15 이며
     * destroy_time · explosion_resistance 가 둘 다 3.0 이다. 바다 랜턴(광량 15)과 같은
     * 발광 계약이라 새 축을 만들지 않는다.
     *
     * <p>활성 여부는 <b>블록 상태가 아니다</b> — 프레임 블록 배치에서 파생되는 값이라
     * 저장하지 않는다({@link com.gameexpert.engine.ConduitRules} 가 그 순수 판정의 정본).
     */
    public static final int CONDUIT = 1951;

    // ── [TURTLE] 거북 생애 주기 1930~1949 ──────────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1930~1949 이고 실제로 쓰는 것은 <b>1930~1932 셋</b>이다.
    // 1921~1929 는 [TRIDENT] 예약 공백이라 되메우지 않고 1933~1949 는 이 트랙의 예약
    // 공백이다. 이 파일이 ID 정본이고 client {@code world/blocks.ts} 가 그 사본이다.
    //
    // 셋 중 <b>월드 블록은 거북 알(1930) 하나</b>이고 나머지 둘은 순수 아이템이다. 세 값
    // 모두 콘딧(1951)보다 낮아 {@link #BLOCK_ID_HIGH_WATER} · {@link #PROTOCOL_ID_HIGH_WATER}
    // 를 올리지 않는다.
    //
    // <p><b>거북 장인의 물약은 등록하지 않는다.</b> [A] {@code Potions.TURTLE_MASTER} 는
    // 효과 둘(감속 IV + 저항 III)을 한 병에 싣는데, 이 저장소의 물약 축
    // ({@link com.gameexpert.engine.effect.PotionRules})은 물약 하나당 효과 하나 계약이라
    // 담기지 않는다. 축을 넓히는 것은 이 트랙의 표면이 아니므로 ID 를 잡지 않고 비워 둔다.

    /**
     * [TURTLE] 거북 알. <b>월드 블록</b>이다.
     *
     * <p>[A] 1.21.4 {@code TurtleEggBlock} 의 블록 속성은
     * {@code Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.METAL)
     * .randomTicks().noOcclusion()} 이다 — 도구 요구가 없고 destroy_time ·
     * explosion_resistance 가 둘 다 0.5 다.
     *
     * <p>바닐라 블록스테이트는 {@code eggs}(1..4) × {@code hatch}(0..2) = 12 가지다. 이
     * 저장소는 상태 바이트가 있으므로 ID 를 열둘로 쪼개지 않고 한 ID 안에 둘 다 싣는다 —
     * 비트 배치의 정본은 {@link com.gameexpert.engine.TurtleEggRules} 다.
     */
    public static final int TURTLE_EGG = 1930;

    /**
     * [TURTLE] 거북 등딱지 조각. <b>순수 아이템</b>이다.
     *
     * <p>[A] 1.20.5 에서 {@code scute} 가 {@code turtle_scute} 로 갈라졌고 아르마딜로의 것은
     * {@code armadillo_scute} 라 <b>서로 다른 아이템</b>이다 — 기존 {@code ARMADILLO_SCUTE}
     * (446)와 ID 를 공유하지 않는다. 입수 경로는 새끼 거북의 성장 한 번뿐이다.
     */
    public static final int TURTLE_SCUTE = 1931;

    /**
     * [TURTLE] 거북 등껍질. <b>순수 아이템</b>이며 투구 부위 방어구다.
     *
     * <p>[A] {@code Items.TURTLE_HELMET} 은 방어도 2 · {@code max_damage 275} 이고, 물 밖에
     * 있을 때 착용자에게 수중 호흡 10 초를 계속 다시 걸어 준다({@code TurtleHelmetItem}).
     * 효과 축은 이미 있는 수중 호흡을 그대로 재사용한다.
     */
    public static final int TURTLE_SHELL = 1932;

    // ── [ARCHAEOLOGY] 고고학(해안판) 1960~1999 ────────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1960~1999 이고 실제로 쓰는 것은 <b>1960~1968 · 1970~1977
    // 열일곱</b>이다. 1952~1959 는 [CONDUIT] 예약 공백이라 되메우지 않고 1969 · 1978~1999
    // 는 이 트랙의 예약 공백이다. 이 파일이 ID 정본이고 client {@code world/blocks.ts} 가
    // 그 사본이다. 발췌 핀은 docs/research/mc-archaeology-1214.md 다.
    //
    // <p><b>붓을 새로 만들지 않았다.</b> [A] {@code minecraft:brush} 는 아르마딜로와 의심
    // 블록 <b>양쪽</b>을 솔질하는 하나의 아이템이고, 이 저장소에는 이미 {@link #BRUSH}(447)
    // 가 아르마딜로 경로로 등록돼 있다. 같은 아이템을 두 ID 로 만들지 않는다.
    //
    // <p><b>해안판 도자기 조각 7종의 근거는 전리품표다.</b> [A] 20종 중 해저 유적
    // 두 표가 싣는 것은 {@code ocean_ruin_warm} = angler · shelter · snort,
    // {@code ocean_ruin_cold} = blade · explorer · mourner · plenty 뿐이다. 사막 피라미드·우물
    // 전용 6종은 월드 블록 상한 뒤 2219~2224에 순수 아이템으로 별도 append한다.

    /**
     * [ARCHAEOLOGY] 의심스러운 모래. <b>월드 블록</b>이다.
     *
     * <p>[A] 1.21.4 {@code Blocks.SUSPICIOUS_SAND} 는
     * {@code new BrushableBlock(Blocks.SAND, SoundEvents.BRUSH_SAND, …,
     * Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE)
     * .strength(0.25F).sound(SoundType.SUSPICIOUS_SAND))} 이다 — destroy_time ·
     * explosion_resistance 가 0.25 이고, 다 파내면 일반 모래가 된다.
     *
     * <p><b>상태 바이트에 붓질 횟수를 싣는다.</b> 바닐라는 블록스테이트에
     * {@code dusted}(0..3)만 두고 실제 횟수(0..9)는 {@code BrushableBlockEntity.brushCount}
     * 가 든다. 이 저장소에는 블록 엔티티가 없으므로 <b>횟수를 상태에 직접 싣고 dusted 를
     * 순수 함수로 파생</b>한다({@link #brushDusted}) — 진행도가 청크 저장에 실려 언로드 ·
     * 재접속을 넘어 살아남고, 두 권위가 같은 바이트만 보고 같은 단계를 그린다.
     */
    public static final int SUSPICIOUS_SAND = 1960;

    /**
     * [ARCHAEOLOGY] 의심스러운 자갈. <b>월드 블록</b>이다. [A]
     * {@code new BrushableBlock(Blocks.GRAVEL, …)} 이라 다 파내면 일반 자갈이 되고 나머지
     * 계약은 의심스러운 모래와 같다.
     */
    public static final int SUSPICIOUS_GRAVEL = 1961;

    /** [ARCHAEOLOGY] 낚시꾼 도자기 조각. 조각 7 종 구간(1962~1968)의 시작이다(온수 유적). */
    public static final int ANGLER_POTTERY_SHERD = 1962;
    /** [ARCHAEOLOGY] 칼날 도자기 조각(냉수 유적). */
    public static final int BLADE_POTTERY_SHERD = 1963;
    /** [ARCHAEOLOGY] 탐험가 도자기 조각(냉수 유적). */
    public static final int EXPLORER_POTTERY_SHERD = 1964;
    /** [ARCHAEOLOGY] 애도자 도자기 조각(냉수 유적). */
    public static final int MOURNER_POTTERY_SHERD = 1965;
    /** [ARCHAEOLOGY] 풍요 도자기 조각(냉수 유적). */
    public static final int PLENTY_POTTERY_SHERD = 1966;
    /** [ARCHAEOLOGY] 안식처 도자기 조각(온수 유적). */
    public static final int SHELTER_POTTERY_SHERD = 1967;
    /** [ARCHAEOLOGY] 스니퍼 도자기 조각(온수 유적). 조각 7 종 구간의 끝이다. */
    public static final int SNORT_POTTERY_SHERD = 1968;

    // 1969 는 이 트랙의 예약 공백이다 — 장식 항아리 8 종을 1970 에서 시작시켜 무늬 인덱스와
    // ID 하위 자릿수가 눈으로 맞아떨어지게 두는 편이 읽기 · 디버깅에 낫다.

    /**
     * [ARCHAEOLOGY] 장식 항아리(점토 벽돌판). <b>월드 블록</b>이고 무늬 8 종 구간
     * (1970~1977)의 시작이다.
     *
     * <p>[A] {@code DecoratedPotBlock} 은 {@code strength(0.0F, 0.0F)} · {@code noOcclusion()}
     * 이고 형상은 {@code box(1,0,1,15,16,15)} — XZ 1px 인셋 박스 하나다.
     *
     * <p><b>무늬를 ID 로 쪼갠 이유.</b> 바닐라는 항아리 ID 하나에 {@code pot_decorations}
     * 아이템 컴포넌트로 네 면의 조각을 싣는다. 이 저장소에는 아이템 컴포넌트가 없어
     * (인벤 스키마는 itemType · count · durability · enchantments 뿐) "조각 4 조합 → 파괴 시
     * 그 조각 회수"를 무손실로 지키려면 <b>네 조각이 같을 때만</b> 조합을 성립시켜 ID 를
     * 여덟으로 접는 수밖에 없다(전 조합에 ID 를 주면 8^4 = 4096 으로 예산 밖이다).
     * 서로 다른 조각을 섞는 조합만 성립하지 않는 것이 [C] divergence 이고, 근거는
     * docs/research/mc-archaeology-1214.md §4 가 소유한다.
     */
    public static final int DECORATED_POT = 1970;
    /** [ARCHAEOLOGY] 낚시꾼 조각 넷으로 만든 장식 항아리. */
    public static final int ANGLER_DECORATED_POT = 1971;
    /** [ARCHAEOLOGY] 스니퍼 조각 넷으로 만든 장식 항아리. 무늬 8 종 구간의 끝이다. */
    public static final int SNORT_DECORATED_POT = 1977;

    /** [ARCHAEOLOGY] [A] {@code BrushableBlockEntity.REQUIRED_BRUSHES_TO_BREAK}. */
    public static final int BRUSH_STROKES_TO_BREAK = 10;
    /** [ARCHAEOLOGY] [A] {@code DecoratedPotBlock} 은 네 면에 조각을 하나씩 싣는다. */
    public static final int DECORATED_POT_SHERD_COUNT = 4;

    /** [ARCHAEOLOGY] 붓질 대상(의심스러운 모래 · 자갈)인가. */
    public static boolean isBrushable(int id) {
        return id == SUSPICIOUS_SAND || id == SUSPICIOUS_GRAVEL;
    }

    /**
     * [ARCHAEOLOGY] 다 파낸 뒤 남는 일반 블록. [A] {@code BrushableBlock.turnsInto} 그대로
     * 모래 → 모래 · 자갈 → 자갈이고, 붓질 대상이 아니면 입력을 그대로 돌려준다.
     */
    public static int brushedInto(int id) {
        if (id == SUSPICIOUS_SAND) return SAND;
        if (id == SUSPICIOUS_GRAVEL) return GRAVEL;
        return id;
    }

    /** [ARCHAEOLOGY] 상태 바이트가 든 붓질 횟수(0..9). 상위 비트는 무시한다. */
    public static int brushStrokes(int state) {
        return Math.min(BRUSH_STROKES_TO_BREAK - 1, state & 0x0f);
    }

    /**
     * [ARCHAEOLOGY] 붓질 횟수 → 겉모습 단계 {@code dusted}(0..3). [A]
     * {@code BrushableBlockEntity.getCompletionState()} 의 경계 그대로다:
     * 0 → 0 · 1..2 → 1 · 3..5 → 2 · 6..9 → 3.
     */
    public static int brushDusted(int strokes) {
        if (strokes <= 0) return 0;
        if (strokes < 3) return 1;
        return strokes < 6 ? 2 : 3;
    }

    /** [ARCHAEOLOGY] 현재 등록된 공식 도자기 조각인가. */
    public static boolean isPotterySherd(int id) {
        return id >= ANGLER_POTTERY_SHERD && id <= SNORT_POTTERY_SHERD
                || id >= ARCHER_POTTERY_SHERD && id <= BREWER_POTTERY_SHERD
                || id >= BURN_POTTERY_SHERD && id <= SHEAF_POTTERY_SHERD
                || id >= FLOW_POTTERY_SHERD && id <= SCRAPE_POTTERY_SHERD;
    }

    /** [ARCHAEOLOGY] 장식 항아리 8 종(점토 벽돌판 + 조각 7 종)인가. */
    public static boolean isDecoratedPot(int id) {
        return id >= DECORATED_POT && id <= SNORT_DECORATED_POT;
    }

    /** [ARCHAEOLOGY] 조각 → 그 조각 넷으로 만든 장식 항아리. 조각이 아니면 −1 이다. */
    public static int decoratedPotForSherd(int sherd) {
        return sherd >= ANGLER_POTTERY_SHERD && sherd <= SNORT_POTTERY_SHERD
                ? DECORATED_POT + 1 + (sherd - ANGLER_POTTERY_SHERD)
                : -1;
    }

    /**
     * [ARCHAEOLOGY] 장식 항아리 → 그 네 면을 이루는 조각. 무늬 없는 항아리(점토 벽돌판)는
     * 조각이 아니라 {@link #AIR}(0)을 돌려주며, 그 재료가 점토 벽돌이라는 사실은 아이템
     * 축을 아는 {@code com.gameexpert.engine.ArchaeologyRules} 가 소유한다.
     */
    public static int sherdForDecoratedPot(int pot) {
        return pot > DECORATED_POT && pot <= SNORT_DECORATED_POT
                ? ANGLER_POTTERY_SHERD + (pot - DECORATED_POT - 1)
                : AIR;
    }

    // ── [SPEAR] 창 여섯 티어 2000~2005 ──────────────────────────────────────
    //
    // 발췌 핀은 docs/research/mc-spear-1-21-11.md 다(1.21.11 "Mounts of Mayhem", 등급 <b>B</b>
    // — 이 저장소가 고정한 1.21.4 스냅샷보다 뒤 버전이라 [A] 원문 JSON 은 하나도 없다).
    // 이 파일이 ID 정본이고 client {@code ui/items.ts} 가 그 사본이다.
    //
    // <b>전부 순수 아이템</b>이라 {@link #BLOCK_ID_HIGH_WATER} 를 올리지 않고
    // {@link #PROTOCOL_ID_HIGH_WATER} 만 올린다. 아틀라스 슬롯도 <b>0개</b> 쓴다
    // (아이콘이 icons.ts 의 절차적 그리드다 — [DIAMOND-SHIELD]·[TRIDENT] 와 같은 선례).
    //
    // <b>재료를 하나도 신설하지 않았다</b>(핀 §2 재료 무결성 확인): 대각선 배치가 요구하는
    // 여섯 재료 막대 260 · 판자 · 돌 · 구리 주괴 393 · 철 주괴 298 · 금 주괴 299 ·
    // 다이아몬드 286 이 이미 전부 실재한다.
    //
    // <b>티어가 여섯인 이유</b>(핀 §6): 바닐라는 일곱 티어지만 이 저장소에는 네더라이트
    // 사슬이 통째로 없다 — 고대 잔해 · 네더라이트 조각 · 네더라이트 주괴 · 대장장이 틀 중
    // 어느 것도 등록되어 있지 않다(대장장이 <b>작업대</b> 블록 {@code SMITHING_TABLE=531}
    // 만 있다). 레시피 무결성 규칙상 없는 재료를 다른 재료로 대체할 수 없으므로 네더라이트
    // 창은 <b>2006 을 예약만 하고 등록하지 않는다</b>. 2007~2011 은 이 트랙의 남은 배정
    // 구간, 2012~2019 는 예약 공백이다.

    /** [SPEAR] 나무 창. 창 여섯 티어 구간(2000~2005)의 시작이다. */
    public static final int WOODEN_SPEAR = 2000;
    /** [SPEAR] 돌 창. */
    public static final int STONE_SPEAR = 2001;
    /**
     * [SPEAR] 구리 창. <b>구리가 정식 무기 티어로 들어간 첫 사례</b>다(핀 §1) — 이 저장소의
     * 다른 무기·도구에는 구리 티어가 없다.
     */
    public static final int COPPER_SPEAR = 2002;
    /** [SPEAR] 철 창. 좀비 계열이 자연 무장으로 드는 티어다(핀 §5). */
    public static final int IRON_SPEAR = 2003;
    /** [SPEAR] 금 창. 피글린 계열이 자연 무장으로 드는 티어다(핀 §5). */
    public static final int GOLD_SPEAR = 2004;
    /** [SPEAR] 다이아몬드 창. 등록된 마지막 티어이자 현재 프로토콜 high-water 다. */
    public static final int DIAMOND_SPEAR = 2005;

    /**
     * [SPEAR] 등록된 창 여섯 티어 중 하나인가. client {@code items.ts} 의 {@code isSpear} 와
     * 같은 판정이어야 하고, "창인가" 를 묻는 자리는 전부 이 술어 하나만 지난다.
     */
    public static boolean isSpear(int id) {
        return (id >= WOODEN_SPEAR && id <= DIAMOND_SPEAR) || id == NETHERITE_SPEAR
                || id == FLESH_BONE_SPEAR || id == FLESH_HOOKED_SPEAR;
    }

    // ── [SHIELD-FAMILY] 방패 네 티어 2020~2023 ──────────────────────────────
    //
    // 근거 등급 <b>[C]</b>: 바닐라에는 방패 티어가 <b>하나뿐</b>이라 이 네 종에 대응하는 원문이
    // 없다. 다이아 방패 1858 이 낸 선례("바닐라에 없는 WebCraft 창작이고 막기 성능은 기본
    // 방패와 완전히 같으며 차별점은 내구뿐")를 그대로 잇는 확장이다.
    //
    // <b>철 방패를 새로 만들지 않는다</b>: 이 가족의 철 자리는 이미 {@code PlayerInventory.SHIELD}
    // 321 이 차지하고 있다. 같은 물건에 ID 를 둘 주면 제작·전리품·복원이 서로 다른 ID 를 들고
    // 조용히 갈리므로, 티어 여섯 중 철(321)과 다이아(1858)는 <b>기존 ID 를 재사용</b>하고 이
    // 절은 나머지 넷만 신설한다. 그래서 구간이 2020 에서 시작하고 값이 연속 넷이다.
    //
    // <b>전부 순수 아이템</b>이라 {@link #BLOCK_ID_HIGH_WATER} 를 올리지 않고
    // {@link #PROTOCOL_ID_HIGH_WATER} 만 올린다. 아틀라스 슬롯도 <b>0개</b> 쓴다
    // (아이콘이 icons.ts 의 절차적 그리드다 — [DIAMOND-SHIELD]·[SPEAR] 와 같은 선례).
    //
    // <b>재료를 하나도 신설하지 않았다</b>: 가죽 334 · 조약돌 8 · 구리 주괴 393 · 금 주괴 299 ·
    // 철 주괴 298 이 이미 전부 실재한다.
    //
    // 2024~2039 는 이 트랙의 예약 공백이다. 2006 은 [SPEAR] 네더라이트 창 예약,
    // 2007~2019 는 [SPEAR] 의 남은 배정·예약 구간이라 되메우지 않는다.

    /** [SHIELD-FAMILY] 가죽 방패. 방패 네 티어 구간(2020~2023)의 시작이다. */
    public static final int LEATHER_SHIELD = 2020;
    /** [SHIELD-FAMILY] 돌 방패. */
    public static final int STONE_SHIELD = 2021;
    /** [SHIELD-FAMILY] 구리 방패. */
    public static final int COPPER_SHIELD = 2022;
    /**
     * [SHIELD-FAMILY] 금 방패. 이 절이 신설하는 마지막 ID 이자 현재 프로토콜 high-water 다.
     * 금 티어는 이 저장소의 규약대로 <b>내구가 가장 낮다</b>(금 도구 30 과 같은 값).
     */
    public static final int GOLD_SHIELD = 2023;

    /**
     * [SHIELD-FAMILY] 이 절이 신설한 방패 네 티어(2020~2023) 중 하나인가.
     *
     * <p><b>이것은 "방패인가" 를 묻는 술어가 아니다</b> — 기본 방패 321 과 다이아 방패 1858 은
     * 이 구간 밖이라 여기서 false 다. 막기 판정·내구 소모·손 자세처럼 "방패인가" 를 묻는 자리는
     * 전부 {@code PlayerInventory.isShield} 하나만 지나야 한다. 이 술어는 오직 <b>연속 ID 구간</b>
     * 을 한 곳에서 소유하기 위한 것이고 그 술어와 복원 게이트가 이것을 읽는다.
     */
    public static boolean isShieldFamilyTier(int id) {
        return id >= LEATHER_SHIELD && id <= GOLD_SHIELD;
    }

    // ── [NAUTILUS-MOUNT] 노틸러스 갑옷 네 티어 2040~2043 ────────────────────
    //
    // 근거 등급 <b>[B]</b>: {@code docs/research/mc-nautilus-1-21-11.md} §8-2 —
    // "길들인 성체는 안장 슬롯과 노틸러스 갑옷 슬롯 둘을 갖는다" 이고 원문 티어는
    // <b>구리·철·금·다이아몬드·네더라이트 다섯</b>이다.
    //
    // <b>네더라이트는 신설하지 않는다.</b> 이 저장소에는 네더라이트 재료·장비가 <b>하나도</b>
    // 없다({@code NETHERITE} 로 시작하는 ID 가 0개다). 없는 재료를 지어내면 제작 경로가 없는
    // 죽은 아이템이 되므로 [B] 다섯 티어 중 재료가 실재하는 <b>넷만</b> 신설한다 —
    // 이 결손은 divergence 로 정직하게 기록하고 네더라이트 트랙이 열릴 때 2044 를 이어 쓴다.
    //
    // <b>ID 구간 조율</b>: 2024~2039 는 [SHIELD-FAMILY] 트랙이 못 박아 둔 예약 공백이라
    // 되메우지 않고 그 <b>뒤</b>인 2040 에서 시작한다. 넷이라 2043 에서 끝나
    // 현재 독립 프로토콜 표 용량 안에 들어간다.
    //
    // <b>전부 순수 아이템</b>이라 {@link #BLOCK_ID_HIGH_WATER} 를 올리지 않고
    // {@link #PROTOCOL_ID_HIGH_WATER} 만 올린다. 아틀라스 슬롯도 <b>0개</b> 쓴다
    // (아이콘이 icons.ts 의 절차적 그리드다 — [SHIELD-FAMILY] 와 같은 선례).
    //
    // <b>재료를 하나도 신설하지 않았다</b>: 구리 주괴 393 · 철 주괴 298 · 금 주괴 299 ·
    // 다이아몬드 286 이 이미 전부 실재한다.

    /** [NAUTILUS-MOUNT] 구리 노틸러스 갑옷. 갑옷 네 티어 구간(2040~2043)의 시작이다. */
    public static final int COPPER_NAUTILUS_ARMOR = 2040;
    /** [NAUTILUS-MOUNT] 철 노틸러스 갑옷. */
    public static final int IRON_NAUTILUS_ARMOR = 2041;
    /** [NAUTILUS-MOUNT] 금 노틸러스 갑옷. */
    public static final int GOLD_NAUTILUS_ARMOR = 2042;
    /**
     * [NAUTILUS-MOUNT] 다이아몬드 노틸러스 갑옷. 이 절이 신설하는 마지막 ID 이자 현재
     * 프로토콜 high-water 다. 네더라이트(2044)는 재료가 없어 신설하지 않는다.
     */
    public static final int DIAMOND_NAUTILUS_ARMOR = 2043;
    public static final int NETHERITE_NAUTILUS_ARMOR = 2044;

    // [FEATURE-WAVE] Append-only Copper Age world blocks 2045~2071.
    public static final int COPPER_TORCH = 2045;
    public static final int COPPER_WALL_TORCH_N = 2046;
    public static final int COPPER_WALL_TORCH_E = 2047;
    public static final int COPPER_WALL_TORCH_S = 2048;
    public static final int COPPER_WALL_TORCH_W = 2049;
    public static final int EXPOSED_COPPER_LANTERN = 2050;
    public static final int WEATHERED_COPPER_LANTERN = 2051;
    public static final int OXIDIZED_COPPER_LANTERN = 2052;
    public static final int WAXED_COPPER_LANTERN = 2053;
    public static final int WAXED_EXPOSED_COPPER_LANTERN = 2054;
    public static final int WAXED_WEATHERED_COPPER_LANTERN = 2055;
    public static final int WAXED_OXIDIZED_COPPER_LANTERN = 2056;
    public static final int EXPOSED_LIGHTNING_ROD = 2057;
    public static final int WEATHERED_LIGHTNING_ROD = 2058;
    public static final int OXIDIZED_LIGHTNING_ROD = 2059;
    public static final int WAXED_LIGHTNING_ROD = 2060;
    public static final int WAXED_EXPOSED_LIGHTNING_ROD = 2061;
    public static final int WAXED_WEATHERED_LIGHTNING_ROD = 2062;
    public static final int WAXED_OXIDIZED_LIGHTNING_ROD = 2063;
    public static final int COPPER_GOLEM_STATUE = 2064;
    public static final int EXPOSED_COPPER_GOLEM_STATUE = 2065;
    public static final int WEATHERED_COPPER_GOLEM_STATUE = 2066;
    public static final int OXIDIZED_COPPER_GOLEM_STATUE = 2067;
    public static final int WAXED_COPPER_GOLEM_STATUE = 2068;
    public static final int WAXED_EXPOSED_COPPER_GOLEM_STATUE = 2069;
    public static final int WAXED_WEATHERED_COPPER_GOLEM_STATUE = 2070;
    public static final int WAXED_OXIDIZED_COPPER_GOLEM_STATUE = 2071;
    public static final int NETHERRACK = 2072;
    public static final int ANCIENT_DEBRIS = 2073;
    public static final int NETHERITE_BLOCK = 2074;

    // [ANIMAL-DEPENDENCY] Blocks required by registered animal lifecycles.
    public static final int FROGSPAWN = 2075;
    public static final int SNIFFER_EGG = 2076;
    public static final int TORCHFLOWER_CROP = 2077;
    public static final int TORCHFLOWER = 2078;
    public static final int PITCHER_CROP = 2079;
    public static final int OCHRE_FROGLIGHT = 2080;
    public static final int VERDANT_FROGLIGHT = 2081;
    public static final int PEARLESCENT_FROGLIGHT = 2082;
    public static final int BEE_NEST = 2083;
    public static final int BEEHIVE = 2084;
    public static final int HONEY_BLOCK = 2085;
    public static final int HONEYCOMB_BLOCK = 2086;

    // Pure-item continuation. World-block high-water intentionally stops at 2086.
    public static final int COPPER_PICKAXE = 2087;
    public static final int COPPER_AXE = 2088;
    public static final int COPPER_SHOVEL = 2089;
    public static final int COPPER_SWORD = 2090;
    public static final int COPPER_HOE = 2091;
    public static final int COPPER_HELMET = 2092;
    public static final int COPPER_CHESTPLATE = 2093;
    public static final int COPPER_LEGGINGS = 2094;
    public static final int COPPER_BOOTS = 2095;
    public static final int COPPER_HORSE_ARMOR = 2096;
    public static final int DIAMOND_HOE = 2097;
    public static final int NETHERITE_SCRAP = 2098;
    public static final int NETHERITE_INGOT = 2099;
    public static final int NETHERITE_UPGRADE_SMITHING_TEMPLATE = 2100;
    public static final int NETHERITE_PICKAXE = 2101;
    public static final int NETHERITE_AXE = 2102;
    public static final int NETHERITE_SHOVEL = 2103;
    public static final int NETHERITE_SWORD = 2104;
    public static final int NETHERITE_HOE = 2105;
    public static final int NETHERITE_HELMET = 2106;
    public static final int NETHERITE_CHESTPLATE = 2107;
    public static final int NETHERITE_LEGGINGS = 2108;
    public static final int NETHERITE_BOOTS = 2109;
    public static final int NETHERITE_SPEAR = 2110;
    public static final int NETHERITE_HORSE_ARMOR = 2111;
    public static final int GOAT_HORN = 2112;
    public static final int TADPOLE_BUCKET = 2113;
    public static final int LEATHER_HORSE_ARMOR = 2114;
    public static final int IRON_HORSE_ARMOR = 2115;
    public static final int GOLDEN_HORSE_ARMOR = 2116;
    public static final int DIAMOND_HORSE_ARMOR = 2117;
    public static final int TORCHFLOWER_SEEDS = 2118;
    public static final int PITCHER_POD = 2119;
    public static final int LEAD = 2120;
    public static final int AXOLOTL_BUCKET = 2121;

    // [BUILDING-VOCABULARY] Append-only anvil damage family. These are world blocks and items.
    public static final int ANVIL = 2122;
    public static final int CHIPPED_ANVIL = 2123;
    public static final int DAMAGED_ANVIL = 2124;
    /** tinted_glass. MapColor.COLOR_GRAY, strength 0.3, full light block, self drop. */
    public static final int TINTED_GLASS = 2125;
    public static final int GLOWSTONE = 2126;
    /** 프로젝트 생태 블록. 세부 행동은 전담 권위 트랙이 소유한다. */
    public static final int RAFFLESIA = 2127;
    public static final int WHITE_BANNER = 2128;
    public static final int ORANGE_BANNER = 2129;
    public static final int MAGENTA_BANNER = 2130;
    public static final int LIGHT_BLUE_BANNER = 2131;
    public static final int YELLOW_BANNER = 2132;
    public static final int LIME_BANNER = 2133;
    public static final int PINK_BANNER = 2134;
    public static final int GRAY_BANNER = 2135;
    public static final int LIGHT_GRAY_BANNER = 2136;
    public static final int CYAN_BANNER = 2137;
    public static final int PURPLE_BANNER = 2138;
    public static final int BLUE_BANNER = 2139;
    public static final int BROWN_BANNER = 2140;
    public static final int GREEN_BANNER = 2141;
    public static final int RED_BANNER = 2142;
    public static final int BLACK_BANNER = 2143;
    public static final int WRITABLE_BOOK = 2144;
    public static final int WRITTEN_BOOK = 2145;

    // ── [SULFUR-26.3] pinned Java 26.3-snapshot-7 family ───────────────────
    // IDs 1301~1310 remain tombstones for the removed pre-26.3 fictional ore/crystal/vent
    // family.  They are intentionally never reused.  SULFUR_BLOCK (1300) is the official
    // minecraft:sulfur block and these append-only IDs complete its official family.
    public static final int CHISELED_SULFUR = 2146;
    public static final int POLISHED_SULFUR = 2147;
    public static final int POLISHED_SULFUR_SLAB = 2148;
    public static final int POLISHED_SULFUR_STAIRS = 2149;
    public static final int POLISHED_SULFUR_WALL = 2150;
    public static final int POTENT_SULFUR = 2151;
    public static final int SULFUR_BRICKS = 2152;
    public static final int SULFUR_BRICK_SLAB = 2153;
    public static final int SULFUR_BRICK_STAIRS = 2154;
    public static final int SULFUR_BRICK_WALL = 2155;
    public static final int SULFUR_SLAB = 2156;
    public static final int SULFUR_STAIRS = 2157;
    public static final int SULFUR_WALL = 2158;
    public static final int SULFUR_SPIKE = 2159;
    public static final int CHISELED_CINNABAR = 2160;
    public static final int CINNABAR = 2161;
    public static final int CINNABAR_BRICKS = 2162;
    public static final int CINNABAR_BRICK_SLAB = 2163;
    public static final int CINNABAR_BRICK_STAIRS = 2164;
    public static final int CINNABAR_BRICK_WALL = 2165;
    public static final int CINNABAR_SLAB = 2166;
    public static final int CINNABAR_STAIRS = 2167;
    public static final int CINNABAR_WALL = 2168;
    public static final int POLISHED_CINNABAR = 2169;
    public static final int POLISHED_CINNABAR_SLAB = 2170;
    public static final int POLISHED_CINNABAR_STAIRS = 2171;
    public static final int POLISHED_CINNABAR_WALL = 2172;
    /** 26.3 cold chicken lay item. Pure item; carries the cold chicken identity when thrown. */
    public static final int BLUE_EGG = 2173;
    /** 26.3 warm chicken lay item. Pure item; carries the warm chicken identity when thrown. */
    public static final int BROWN_EGG = 2174;
    /** Pinned 26.3 shelf; bamboo shelf is omitted until its stripped-bamboo prerequisite exists. */
    public static final int PALE_OAK_SHELF = 2175;
    /** Pinned 26.3 throwable wind-charge item. The breeze projectile is an entity, not another item. */
    public static final int WIND_CHARGE = 2176;
    /** 26.3 sulfur cube bucket; pure item carrying the entity's body/age/name component. */
    public static final int SULFUR_CUBE_BUCKET = 2177;
    /** 26.3 infested stone. It is visually identical to STONE but retains its own authority ID. */
    public static final int INFESTED_STONE = 2178;
    /** 26.3 infested deepslate. It is visually identical to DEEPSLATE but retains its own authority ID. */
    public static final int INFESTED_DEEPSLATE = 2179;
    /** 26.3 two-block submerged vegetation; exact upper/lower state is carried separately. */
    public static final int TALL_SEAGRASS = 2180;
    public static final int BLUE_ORCHID = 2181;
    public static final int CLOSED_EYEBLOSSOM = 2182;
    public static final int LARGE_FERN = 2183;
    public static final int MELON = 2184;
    public static final int PINK_PETALS = 2185;
    public static final int SUNFLOWER = 2186;
    /** Actual two-block minecraft:tall_grass; legacy ID 11 represents short grass. */
    public static final int TALL_GRASS_263 = 2187;
    /** 26.3 kelp body identity; legacy KELP=57 is the terminal growth-tip block. */
    public static final int KELP_PLANT = 2188;
    /** 26.3 two-block forest flower; exact lower/upper half lives in the feature carrier. */
    public static final int LILAC = 2189;
    /** 26.3 two-block forest flower; exact lower/upper half lives in the feature carrier. */
    public static final int ROSE_BUSH = 2190;
    /** 26.3 two-block forest flower; exact lower/upper half lives in the feature carrier. */
    public static final int PEONY = 2191;
    /** 26.3 single-block forest flower. */
    public static final int LILY_OF_THE_VALLEY = 2192;
    /** 26.3 jungle-tree cocoa pod; age/facing remain exact feature state properties. */
    public static final int COCOA = 2193;
    /** 26.3 big-dripleaf column body; distinct from the terminal leaf block. */
    public static final int BIG_DRIPLEAF_STEM = 2194;
    /** 26.3 azalea-tree foliage; distinct from the plant-form azalea block. */
    public static final int AZALEA_LEAVES = 2195;
    /** 26.3 flowering azalea-tree foliage; distinct from the plant-form flowering azalea. */
    public static final int FLOWERING_AZALEA_LEAVES = 2196;
    /** 26.3 flower-patch identities; each remains distinct through protocol and rendering. */
    public static final int ALLIUM = 2197;
    public static final int AZURE_BLUET = 2198;
    public static final int RED_TULIP = 2199;
    public static final int ORANGE_TULIP = 2200;
    public static final int WHITE_TULIP = 2201;
    public static final int PINK_TULIP = 2202;
    public static final int OXEYE_DAISY = 2203;
    public static final int CORNFLOWER = 2204;
    /** Jungle-pyramid mechanism block; distinct from item-only TRIPWIRE_HOOK=488. */
    public static final int TRIPWIRE_HOOK_BLOCK = 2205;
    public static final int TRIPWIRE = 2206;
    public static final int DISPENSER = 2207;
    public static final int LEVER = 2208;
    public static final int REDSTONE_WIRE = 2209;
    public static final int REPEATER = 2210;
    public static final int STICKY_PISTON = 2211;
    /** Exact pinned igloo-only world identities, appended without reusing an existing block ID. */
    public static final int REDSTONE_TORCH = 2212;
    public static final int INFESTED_STONE_BRICKS = 2213;
    public static final int INFESTED_CHISELED_STONE_BRICKS = 2214;
    public static final int INFESTED_MOSSY_STONE_BRICKS = 2215;
    public static final int WATER_CAULDRON = 2216;
    public static final int POTTED_CACTUS = 2217;
    public static final int OAK_WALL_SIGN = 2218;
    /** Desert-pyramid archaeology pottery sherds, appended after the world-block high-water. */
    public static final int ARCHER_POTTERY_SHERD = 2219;
    public static final int MINER_POTTERY_SHERD = 2220;
    public static final int PRIZE_POTTERY_SHERD = 2221;
    public static final int SKULL_POTTERY_SHERD = 2222;
    /** Desert-well archaeology pottery sherds. */
    public static final int ARMS_UP_POTTERY_SHERD = 2223;
    public static final int BREWER_POTTERY_SHERD = 2224;
    /** Desert-pyramid trap plate; appended after the archaeology pure-item tranche. */
    public static final int STONE_PRESSURE_PLATE = 2225;
    /** Exact pinned swamp-hut flower-pot identity. */
    public static final int POTTED_RED_MUSHROOM = 2226;
    /** Exact structure-loot item identities. Rails remain items, not additional world blocks. */
    public static final int ACTIVATOR_RAIL = 2227;
    public static final int DETECTOR_RAIL = 2228;
    public static final int POWERED_RAIL = 2229;
    public static final int MELON_SEEDS = 2230;
    public static final int MUSIC_DISC_13 = 2231;
    public static final int MUSIC_DISC_CAT = 2232;
    public static final int MUSIC_DISC_OTHERSIDE = 2233;
    public static final int MUSIC_DISC_BOUNCE = 2234;
    public static final int DUNE_ARMOR_TRIM_SMITHING_TEMPLATE = 2235;
    public static final int WILD_ARMOR_TRIM_SMITHING_TEMPLATE = 2236;
    public static final int EYE_ARMOR_TRIM_SMITHING_TEMPLATE = 2237;
    /** Stronghold-only vanilla identities appended after the released structure-loot tranche. */
    public static final int STONE_BUTTON = 2238;
    public static final int END_PORTAL_FRAME = 2239;
    public static final int END_PORTAL = 2240;
    /** Trail-ruins common archaeology block-item vocabulary, in pinned loot-table order. */
    public static final int RED_CANDLE = 2241;
    public static final int GREEN_CANDLE = 2242;
    public static final int PURPLE_CANDLE = 2243;
    public static final int BROWN_CANDLE = 2244;
    public static final int SPRUCE_HANGING_SIGN = 2245;
    public static final int OAK_HANGING_SIGN = 2246;
    public static final int FLOWER_POT = 2247;
    /** Trail-ruins rare archaeology pure-item vocabulary, in pinned loot-table order. */
    public static final int BURN_POTTERY_SHERD = 2248;
    public static final int DANGER_POTTERY_SHERD = 2249;
    public static final int FRIEND_POTTERY_SHERD = 2250;
    public static final int HEART_POTTERY_SHERD = 2251;
    public static final int HEARTBREAK_POTTERY_SHERD = 2252;
    public static final int HOWL_POTTERY_SHERD = 2253;
    public static final int SHEAF_POTTERY_SHERD = 2254;
    public static final int WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE = 2255;
    public static final int RAISER_ARMOR_TRIM_SMITHING_TEMPLATE = 2256;
    public static final int SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE = 2257;
    public static final int HOST_ARMOR_TRIM_SMITHING_TEMPLATE = 2258;
    public static final int MUSIC_DISC_RELIC = 2259;
    /** Official invisible full-collision archaeology processor carrier. */
    public static final int BARRIER = 2260;
    /** Ruined Portal processor output; distinct from portal-frame-capable normal obsidian. */
    public static final int CRYING_OBSIDIAN = 2261;
    /** Pinned 26.3 Abandoned Camp placeable vocabulary absent from the prior block table. */
    public static final int BAMBOO_FENCE = 2262;
    public static final int WHITE_WOOL_STAIRS = 2263;
    public static final int WHITE_WALL_BANNER = 2264;
    public static final int CANDLE = 2265;
    public static final int MANGROVE_WOOD = 2266;
    public static final int POTTED_DEAD_BUSH = 2267;
    public static final int HOPPER = 2268;
    public static final int OAK_BUTTON = 2269;
    public static final int OAK_SLAB = 2270;
    public static final int OAK_PRESSURE_PLATE = 2271;
    public static final int POTTED_RED_TULIP = 2272;
    /** Village G3J15 exact-state identities absent from the prior append-only table. */
    public static final int ACACIA_WOOD = 2273;
    public static final int BELL = 2274;
    public static final int BROWN_WALL_BANNER = 2275;
    public static final int MELON_STEM = 2276;
    public static final int STRIPPED_OAK_WOOD = 2277;
    public static final int STRIPPED_SPRUCE_WOOD = 2278;
    /** 늦게 append한 고고학 순수 아이템 3종. 월드 블록 상한은 올리지 않는다. */
    public static final int FLOW_POTTERY_SHERD = 2279;
    public static final int GUSTER_POTTERY_SHERD = 2280;
    public static final int SCRAPE_POTTERY_SHERD = 2281;
    /** 자연 상자 보상의 자석석. 기존 블록·아이템 ID 뒤에만 추가한다. */
    public static final int LODESTONE = 2282;
    // 사용자 정의 살점 지옥. 기존 ID를 재사용하지 않고 순수 아이템 2287도 별도 분류한다.
    public static final int ABYSS_STONE = 2283;
    public static final int FLESH_HEART = 2284;
    public static final int HEART_CORE = 2285;
    public static final int FLESH_BLOCK = 2286;
    public static final int MYSTERY_FLESH = 2287;
    public static final int FLESH_COCOON = 2288;
    // 배정 예약만 소유한다. 콘텐츠 등록 전에는 지지/아이템 표를 채우지 않는다.
    public static final int RESERVED_FLESH_FIRST = 2283;
    public static final int RESERVED_FLESH_LAST = 2288;
    public static final int RESERVED_MYSTERY_FLESH_ITEM = 2287;
    public static final int RESERVED_VOID_END_FIRST = 2289;
    public static final int RESERVED_VOID_END_LAST = 2312;
    /**
     * [TRIAL] ominous_trial_key. 불길한 금고({@code vault[ominous=true]})를 여는 순수 아이템이다.
     * 스택 64, 내구 없음, 제작법 없음 — 바닐라 입수 경로는 불길한 트라이얼 스포너의 배출 표
     * {@code spawners/ominous/trial_chamber/key}(가중치 3/10) 하나다. 차원 예약(2283~2312) 뒤에
     * append 한다.
     */
    public static final int OMINOUS_TRIAL_KEY = 2313;
    // [TRIAL-GAP] 트라이얼 챔버 표가 이름을 대는데 없던 순수 아이템 2314~2330 과 월드 블록 2331~2332.
    // 조정자가 배정한 구간이며 정적판 ui/items.ts · world/blocks.ts 와 같은 값이다.
    /** potion[regeneration] — 재생 I 900 MC 틱. spawners/…/consumables 의 재생 항목. */
    public static final int POTION_REGENERATION = 2314;
    /** lingering_potion 일곱 종(items_to_drop_when_ominous 첫 풀 순서). */
    public static final int LINGERING_POTION_WIND_CHARGED = 2315;
    public static final int LINGERING_POTION_OOZING = 2316;
    public static final int LINGERING_POTION_WEAVING = 2317;
    public static final int LINGERING_POTION_INFESTED = 2318;
    public static final int LINGERING_POTION_STRENGTH = 2319;
    public static final int LINGERING_POTION_SWIFTNESS = 2320;
    public static final int LINGERING_POTION_SLOW_FALLING = 2321;
    /** tipped_arrow[poison] · tipped_arrow[strong_slowness]. 스택 64. */
    public static final int TIPPED_ARROW_POISON = 2322;
    public static final int TIPPED_ARROW_STRONG_SLOWNESS = 2323;
    /** fire_charge. 스택 64. */
    public static final int FIRE_CHARGE = 2324;
    public static final int BOLT_ARMOR_TRIM_SMITHING_TEMPLATE = 2325;
    public static final int FLOW_ARMOR_TRIM_SMITHING_TEMPLATE = 2326;
    /** guster/flow_banner_pattern. 바닐라 max_stack_size 1. */
    public static final int GUSTER_BANNER_PATTERN = 2327;
    public static final int FLOW_BANNER_PATTERN = 2328;
    /** music_disc_precipice · music_disc_creator. 스택 1. */
    public static final int MUSIC_DISC_PRECIPICE = 2329;
    public static final int MUSIC_DISC_CREATOR = 2330;
    /** emerald_block. 핀 26.3 strength(5, 6), 철 곡괭이 이상. */
    public static final int EMERALD_BLOCK = 2331;
    /** heavy_core. 핀 26.3 strength(10, 1200), HeavyCoreBlock 모양 4..12 × 0..8 × 4..12. */
    public static final int HEAVY_CORE = 2332;
    // [VOID-END] 엔드 차원 콘텐츠가 예약 2289~2312 앞쪽을 쓴다(2289~2302). 2303~2312 는 후속 엔드 콘텐츠 예약이다.
    // 형상군은 연속 구간이다: 계단 2293~2294, 반 블록 2295~2296. 물성은 핀 26.3 Blocks 그대로다.
    public static final int END_STONE = 2289;
    public static final int END_STONE_BRICKS = 2290;
    public static final int PURPUR_BLOCK = 2291;
    /** 보라 기둥. state 0..2 = 축 y·x·z(뼈 블록·원목과 같은 어휘). */
    public static final int PURPUR_PILLAR = 2292;
    public static final int END_STONE_BRICK_STAIRS = 2293;
    public static final int PURPUR_STAIRS = 2294;
    public static final int END_STONE_BRICK_SLAB = 2295;
    public static final int PURPUR_SLAB = 2296;
    public static final int END_STONE_BRICK_WALL = 2297;
    /** 후렴 식물. state 비트 N1·E2·S4·W8·up16·down32 (VoidEndBlockRules.CHORUS_PLANT_*). */
    public static final int CHORUS_PLANT = 2298;
    /** 후렴 꽃. state 0..5 = AGE(5 = 시든 꽃). */
    public static final int CHORUS_FLOWER = 2299;
    /** 엔드 막대. state 0..5 = 0 up · 1 down · 2 N · 3 E · 4 S · 5 W(번개막대·통 어휘), 광량 14. */
    public static final int END_ROD = 2300;
    /** 튀긴 후렴과(순수 아이템): 후렴과 제련 산물, 보라 블록·엔드 막대 재료. */
    public static final int POPPED_CHORUS_FRUIT = 2301;
    /**
     * [VOID-END] 엔드 관문(바닐라 END_GATEWAY). 아이템 없음·파괴 불가·광량 15·통과. state 는 목적지
     * 종류다: 0 본섬 귀환(정확히 100,50,0) · 1 본섬 고리 → 바깥 섬 출구 · 2 출구 → 본섬 고리
     * (VoidEndBlockRules.GATEWAY_*). 2303~2312 는 후속 엔드 콘텐츠 예약이다.
     */
    public static final int END_GATEWAY = 2302;
    /**
     * [END-CITY] 아이템 액자(순수 아이템, 스택 64). 바닐라 {@code HangingEntityItem(ITEM_FRAME)}: 블록 면(6방향)에
     * 아이템 액자 개체를 건다. 레시피 막대 8 + 가죽 1({@code recipe/item_frame.json}).
     */
    public static final int ITEM_FRAME = 2303;
    /**
     * [END-CITY] 드래곤 머리(바닐라 DRAGON_HEAD 와 DRAGON_WALL_HEAD 를 한 ID 로 접는다). state 0..15 = 바닥
     * {@code SkullBlock.ROTATION}(16방위), 16..19 = 벽 {@code WallSkullBlock.FACING} 북·동·남·서. 핀 26.3 Blocks:
     * strength 1.0 · pushReaction DESTROY · 모양 바닥 box(4,0,4,12,8,12) · 벽 box(4,4,8,12,12,16)(북) — 충돌도 같다.
     * 지지 조건이 없다(SkullBlock/WallSkullBlock 에 canSurvive 없음). 엔드 배 뱃머리에 벽 머리로 놓인다.
     */
    public static final int DRAGON_HEAD = 2304;
    /**
     * [END-CITY] 자홍색 벽 현수막(바닐라 magenta_wall_banner). state 0..3 = FACING 북·동·남·서(천이 향하는 면).
     * 엔드 도시 tower_top 템플릿의 블록 엔티티 무늬(검은 triangle_top + triangle_bottom)를 블록 자체가 그린다 —
     * 플레이어는 벽 현수막을 놓지 못하므로 이 ID 는 엔드 도시에서만 나온다. 부서지면 같은 무늬의 자홍색 현수막.
     */
    public static final int MAGENTA_WALL_BANNER = 2305;
    /**
     * [DRAGON] 드래곤 알(바닐라 DRAGON_EGG). 월드 블록이자 자기 아이템이다. 핀 26.3 Blocks: mapColor
     * COLOR_BLACK · strength(3.0, 9.0) · lightLevel 1 · noOcclusion · pushReaction POPPED, 모양
     * {@code Block.column(14, 0, 16)} = box(1,0,1,15,16,15). FallingBlock(설치 뒤 5틱)이며 때리거나
     * 쓰면 ±15·±7·±15 안의 빈 칸으로 순간이동한다(DragonEggBlock.teleport). 첫 처치 때 귀환 포털 기둥
     * 위에 놓인다(EnderDragonFight.setDragonKilled).
     */
    public static final int DRAGON_EGG = 2306;
    /** [DRAGON] 엔드 수정(순수 아이템, 스택 64). 흑요석·기반암 위에만 놓인다(EndCrystalItem.useOn). */
    public static final int END_CRYSTAL = 2307;
    /** [DRAGON] 드래곤의 숨결(순수 아이템, 스택 64). 드래곤 숨결 구름을 빈 병으로 떠 얻는다(BottleItem.use). */
    public static final int DRAGON_BREATH = 2308;
    /**
     * [END-CITY] 강한 치유의 물약(순수 아이템, 스택 1). 바닐라 {@code Potions.STRONG_HEALING} = 즉시 회복 II
     * ({@code InstantenousMobEffect}: 4 &lt;&lt; 1 = 8 체력). 엔드 배 양조기의 두 병({@code ship.nbt})으로만 나온다.
     */
    public static final int POTION_STRONG_HEALING = 2309;
    // [FROST-SOUL] 조율자 배정 2333~2335. [TRIAL-GAP] 2314~2332(순수 아이템·월드 블록) 바로 뒤다.
    // 물성은 핀 26.3 Blocks 원문이다(javap net/minecraft/world/level/block/Blocks).
    /**
     * 서리 얼음(바닐라 FROSTED_ICE, BlockIds 등록이라 아이템 없음). state 0..3 = AGE
     * (FrostedIceBlock.AGE = BlockStateProperties.AGE_3). mapColor ICE · friction 0.98 · strength 0.5 ·
     * SoundType.GLASS · noOcclusion. loot_table/blocks/frosted_ice 는 풀이 없어 아무것도 떨구지 않는다.
     */
    public static final int FROSTED_ICE = 2333;
    /**
     * 영혼 모래. mapColor COLOR_BROWN · strength 0.5 · speedFactor 0.4 · SoundType.SOUL_SAND.
     * 충돌 형상은 SoulSandBlock.SHAPE = Block.column(16, 0, 14)(윗면 14/16), 지지·시각 형상은 풀 블록.
     */
    public static final int SOUL_SAND = 2334;
    /** 영혼 흙. mapColor COLOR_BROWN · strength 0.5 · SoundType.SOUL_SOIL, 풀 큐브. */
    public static final int SOUL_SOIL = 2335;
    /**
     * [PITCHER] 벌레잡이풀(바닐라 PITCHER_PLANT, {@code DoublePlantBlock} 두 칸 꽃, 자기 자신이 아이템).
     * 조율자 배정 2429(2336–2339 · 2370 · 2417–2426 은 다른 트랙 보유). 두 반은 같은 ID 이고 윗 반은
     * {@code PitcherRules.UPPER} 비트다. 핀 26.3 Blocks: DoublePlantBlock · mapColor PLANT · noCollision ·
     * instabreak · SoundType.CROP · offsetType XZ · ignitedByLava · PushReaction.POPPED. 다 자란 벌레잡이풀 작물
     * 수확({@code blocks/pitcher_crop.json} age=4)이 떨군다.
     */
    public static final int PITCHER_PLANT = 2429;
    /**
     * [GLOWING] 분광 화살(순수 아이템, 바닐라 {@code Items.SPECTRAL_ARROW}). 조율자 배정 2338 이다 —
     * 2336~2337 은 다른 트랙(발사기 · 제작기) 몫이라 되메우지 않는다. 명중한 개체에 발광 200 MC 틱
     * ({@code SpectralArrow.duration})을 건다.
     */
    public static final int SPECTRAL_ARROW = 2338;
    /**
     * [CONTAINER-MENUS] 경험치 병(순수 아이템, 바닐라 {@code Items.EXPERIENCE_BOTTLE}). 조율자 배정
     * 2339 다. 던지면 {@code ThrownExperienceBottle} 이 되어 착탄 자리에 경험치 3 + U(5) + U(5) 를 뿌린다.
     */
    public static final int EXPERIENCE_BOTTLE = 2339;
    /**
     * [MACE] 철퇴(minecraft:mace). 조율자 배정 2370 의 <b>순수 아이템</b>이라 {@link #BLOCK_ID_HIGH_WATER}
     * 를 올리지 않고 {@link #PROTOCOL_ID_HIGH_WATER} 만 올린다. 26.3-snapshot-7 {@code Items.MACE}:
     * rarity EPIC · durability 500 · {@code MaceItem.createAttributes}(attack_damage +5 · attack_speed
     * -3.4) · repairable 브리즈 막대 · enchantable 15 · {@code Weapon(1)}. 재료는 무거운 핵(2332)과
     * 브리즈 막대(1480)가 이미 있어 하나도 신설하지 않았다. 2339~2369 는 다른 트랙 배정 구간이다.
     */
    public static final int MACE = 2370;
    /**
     * [CONTAINER-MENUS] 갑옷 거치대 아이템(순수 아이템, 스택 16). 조율자 배정 2430 이다 —
     * 2340~2419 는 다른 트랙 몫이라 되메우지 않는다. 놓으면 갑옷 거치대 개체가 된다.
     */
    public static final int ARMOR_STAND = 2430;
    /**
     * [CONTAINER-MENUS] 광산 수레 다섯 종(순수 아이템, 스택 1). 조율자 배정 2431~2436 중
     * 2431~2435 를 쓰고 2436 은 예비로 비워 둔다. 레일 위에 놓으면 광산 수레 개체가 된다.
     */
    public static final int MINECART = 2431;
    public static final int CHEST_MINECART = 2432;
    public static final int HOPPER_MINECART = 2433;
    public static final int FURNACE_MINECART = 2434;
    public static final int TNT_MINECART = 2435;

    // [UTILITY] 조율자 배정 2380~2419(유틸리티 체계 레인: 신호기·주크박스·갑옷 장식·양조). 2380~2382 ·
    // 2417~2419(흑암석 계열)는 월드 블록, 2383~2416 은 순수 아이템이다. 2336~2379 는 다른
    // 레인 배정 구간이라 되메우지 않는다. 물성은 핀 26.3 Blocks 원문이다(javap Blocks.<clinit>).
    /** 신호기. strength 3.0 · 광량 15 · SoundType.STONE(기본값) · noOcclusion · 도구 무관. 블록 엔티티(BeaconBlockEntity). */
    public static final int BEACON = 2380;
    /** 주크박스. strength(2.0, 6.0) · SoundType.WOOD · 도끼 최적. 상태 0/1 = has_record. */
    public static final int JUKEBOX = 2381;
    /** 슬라임 블록. strength 0 · friction 0.8 · SoundType.SLIME_BLOCK · noOcclusion(HalfTransparentBlock). */
    public static final int SLIME_BLOCK = 2382;
    /**
     * [UTILITY] 흑암석 계열 2417~2419(주둥이 형판 복제 재료 minecraft:blackstone). 핀 26.3 Blocks:
     * blackstone strength(1.5, 6.0) · polished_blackstone ofLegacyCopy(blackstone) strength(2.0, 6.0) ·
     * polished_blackstone_bricks ofLegacyCopy(polished) strength(1.5, 6.0). 셋 다 MapColor.COLOR_BLACK ·
     * requiresCorrectToolForDrops · SoundType.STONE · #mineable/pickaxe(도구 티어 태그 없음 = 나무 곡괭이부터).
     * 네더가 없어 획득 경로만 레이드 승리 보상(UTILITY_RARE, EXPLORATION-LOOT.md)이다.
     */
    public static final int BLACKSTONE = 2417;
    public static final int POLISHED_BLACKSTONE = 2418;
    public static final int POLISHED_BLACKSTONE_BRICKS = 2419;
    /** Flesh-colony material; pure item, never a placeable world block. */
    public static final int FLESH_FIBER = 2420;
    public static final int FLESH_ARTERY = 2421;
    public static final int FLESH_FAT_SAC = 2422;
    public static final int FLESH_MEMBRANE_BLOCK = 2423;
    public static final int FLESH_BONE_SPUR = 2424;
    public static final int FLESH_NECROSIS = 2425;
    public static final int FLESH_CLOT_SAC = 2426;
    public static final int FLESH_FAT = 2427;
    public static final int FLESH_MEMBRANE = 2428;
    public static final int FLESH_ANCHOR = 2436;
    public static final int FLESH_LARGE_COCOON = 2437;
    public static final int FLESH_BONE_PLATE = 2438;
    public static final int FLESH_HOOK_CLAW = 2439;
    public static final int FLESH_FIBER_BOOTS = 2440;
    public static final int FLESH_FIBER_BUNDLE = 2441;
    public static final int FLESH_BONE_BUNDLE = 2442;
    public static final int FLESH_FAT_LAMP = 2443;
    public static final int FLESH_BONE_SPEAR = 2444;
    public static final int FLESH_BONE_CHESTPLATE = 2445;
    public static final int FLESH_HOOK_BLADE = 2446;
    public static final int FLESH_HOOKED_SPEAR = 2447;
    public static final int FLESH_RELIQUARY = 2448;
    public static final int FLESH_DETECTOR = 2449;
    public static final int FLESH_BANDAGE = 2450;
    public static final int FLESH_PULSE_LAMP = 2451;
    public static boolean isFleshTissue(int id) {
        return id >= FLESH_ARTERY && id <= FLESH_NECROSIS || id == FLESH_ANCHOR || id == FLESH_LARGE_COCOON;
    }
    /** 네더의 별(순수 아이템). 바닐라 원천 위더가 없어 획득 경로만 divergence(EXPLORATION-LOOT.md). */
    public static final int NETHER_STAR = 2383;
    /**
     * 2384 는 비워 둔다. 이 레인이 드래곤의 숨결을 여기에 두었으나 [DRAGON] 레인이 먼저 {@link #DRAGON_BREATH}
     * 2308 로 착지해 그 ID 를 쓴다. 제거 tombstone(REMOVED_) 규약대로 되메우지 않는 공백으로 남긴다.
     */
    public static final int REMOVED_UTILITY_2384 = 2384;
    /** 가스트의 눈물(순수 아이템). 가스트가 없어 획득 경로만 divergence. */
    public static final int GHAST_TEAR = 2385;
    /** 수박 조각(순수 아이템, 음식 2/1.2). 수박 블록의 바닐라 드랍이다. */
    public static final int MELON_SLICE = 2386;
    /** 반짝이는 수박 조각(순수 아이템, 양조 재료). */
    public static final int GLISTERING_MELON_SLICE = 2387;
    /** 수지 벽돌(순수 아이템, 수지 덩어리 제련 산물 · 갑옷 장식 재료). */
    public static final int RESIN_BRICK = 2388;
    /**
     * 범용 물약 세 형태(바닐라 minecraft:potion · splash_potion · lingering_potion). 스택 성분
     * {@code potion_contents}(ItemComponentData.potionContents)가 물약 종류를 싣는다. 기존 전용 물약
     * ID 가 있는 (형태, 종류) 쌍은 그 전용 ID 가 계속 정규형이다(저장 호환).
     */
    public static final int CONTENTS_POTION = 2389;
    public static final int CONTENTS_SPLASH_POTION = 2390;
    public static final int CONTENTS_LINGERING_POTION = 2391;
    /** 음반 15종(바닐라 jukebox_song 22종 중 기존 7종을 뺀 나머지). 스택 1. */
    public static final int MUSIC_DISC_11 = 2392;
    public static final int MUSIC_DISC_5 = 2393;
    public static final int MUSIC_DISC_BLOCKS = 2394;
    public static final int MUSIC_DISC_CHIRP = 2395;
    public static final int MUSIC_DISC_CREATOR_MUSIC_BOX = 2396;
    public static final int MUSIC_DISC_FAR = 2397;
    public static final int MUSIC_DISC_LAVA_CHICKEN = 2398;
    public static final int MUSIC_DISC_MALL = 2399;
    public static final int MUSIC_DISC_MELLOHI = 2400;
    public static final int MUSIC_DISC_PIGSTEP = 2401;
    public static final int MUSIC_DISC_STAL = 2402;
    public static final int MUSIC_DISC_STRAD = 2403;
    public static final int MUSIC_DISC_TEARS = 2404;
    public static final int MUSIC_DISC_WAIT = 2405;
    public static final int MUSIC_DISC_WARD = 2406;
    /** 음반 조각 5. 스택 64. 아홉 개로 음반 5 를 만든다. */
    public static final int DISC_FRAGMENT_5 = 2407;
    /** 갑옷 장식 대장장이 형판 9종(바닐라 trim_pattern 18종 중 기존 9종을 뺀 나머지). 스택 64. */
    public static final int COAST_ARMOR_TRIM_SMITHING_TEMPLATE = 2408;
    public static final int RIB_ARMOR_TRIM_SMITHING_TEMPLATE = 2409;
    public static final int SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE = 2410;
    public static final int SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE = 2411;
    public static final int SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE = 2412;
    public static final int SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE = 2413;
    public static final int TIDE_ARMOR_TRIM_SMITHING_TEMPLATE = 2414;
    public static final int VEX_ARMOR_TRIM_SMITHING_TEMPLATE = 2415;
    public static final int WARD_ARMOR_TRIM_SMITHING_TEMPLATE = 2416;
    // [REDSTONE] 조율자 배정 2340~2369 중 2340~2353. 상태 바이트 어휘는 engine/redstone/RedstoneState
    // 한 곳이 소유한다(정적판 world/redstoneState.ts 사본). 물성은 핀 26.3 Blocks 원문이다.
    /** 레드스톤 블록. strength(5, 6) · 곡괭이 · 신호 15 · isRedstoneConductor(never). */
    public static final int REDSTONE_BLOCK = 2340;
    /** 꺼진 바닥 레드스톤 횃불. 켜진 횃불은 {@link #REDSTONE_TORCH}(광량 7) — 광량이 ID 색인이라 쌍이다. */
    public static final int REDSTONE_TORCH_OFF = 2341;
    /** 켜진 벽 레드스톤 횃불. state 비트 0..1 = 벽 반대 방향(바닐라 FACING). */
    public static final int REDSTONE_WALL_TORCH = 2342;
    /** 꺼진 벽 레드스톤 횃불. */
    public static final int REDSTONE_WALL_TORCH_OFF = 2343;
    /** 레드스톤 비교기(OutputSignal 은 state 비트 4..7). */
    public static final int COMPARATOR = 2344;
    /** 관찰자. strength 3 · 곡괭이 · isRedstoneConductor(never). */
    public static final int OBSERVER = 2345;
    /** 피스톤(비끈끈이). 끈끈이 피스톤은 {@link #STICKY_PISTON}. */
    public static final int PISTON = 2346;
    /** 피스톤 머리(아이템 없음). */
    public static final int PISTON_HEAD = 2347;
    /** 움직이는 피스톤 칸(아이템 없음·보이지 않음). 옮겨지는 블록은 엔진 블록 엔티티가 든다. */
    public static final int MOVING_PISTON = 2348;
    /** 레드스톤 램프(꺼짐). */
    public static final int REDSTONE_LAMP = 2349;
    /** 켜진 레드스톤 램프(광량 15, 꺼진 램프를 떨군다). */
    public static final int REDSTONE_LAMP_LIT = 2350;
    /** 과녁 블록. state 비트 0..3 = POWER. */
    public static final int TARGET = 2351;
    /** 햇빛 감지기. state 비트 0..3 = POWER, 비트 4 = INVERTED. */
    public static final int DAYLIGHT_DETECTOR = 2352;
    /** 소리 블록. state 비트 0..4 = NOTE(0..24), 비트 5 = POWERED. */
    public static final int NOTE_BLOCK = 2353;

    public static boolean isInfestedStone(int id) {
        return id == INFESTED_STONE || id == INFESTED_DEEPSLATE
                || id >= INFESTED_STONE_BRICKS && id <= INFESTED_MOSSY_STONE_BRICKS;
    }

    /** Vanilla silk-touch and silverfish wake-up replacement host; AIR for a non-infested block. */
    public static int infestedHostBlock(int id) {
        return switch (id) {
            case INFESTED_STONE -> STONE;
            case INFESTED_DEEPSLATE -> DEEPSLATE;
            case INFESTED_STONE_BRICKS -> STONE_BRICK;
            case INFESTED_CHISELED_STONE_BRICKS -> CHISELED_STONE_BRICKS;
            case INFESTED_MOSSY_STONE_BRICKS -> MOSSY_STONE_BRICK;
            default -> AIR;
        };
    }

    /** 영속되는 손상 3단계가 공유하는 형상·물리·채굴 계열 판정. */
    public static boolean isAnvil(int id) {
        return id >= ANVIL && id <= DAMAGED_ANVIL;
    }

    public static boolean isBanner(int id) {
        return id >= WHITE_BANNER && id <= BLACK_BANNER;
    }


    /**
     * [NAUTILUS-MOUNT] 이 절이 신설한 노틸러스 갑옷 네 티어(2040~2043) 중 하나인가.
     *
     * <p>연속 ID 구간을 <b>한 곳에서</b> 소유하기 위한 술어다. 장착 게이트·복원 게이트·
     * 클라 아이콘이 전부 이 술어 하나만 지난다 — 리터럴 비교를 복제하면 한 곳만 갱신됐을 때
     * 그 경로만 조용히 갈린다([SHIELD-FAMILY]·[SPEAR] 가 남긴 규칙이다).
     */
    public static boolean isNautilusArmorTier(int id) {
        return id >= COPPER_NAUTILUS_ARMOR && id <= NETHERITE_NAUTILUS_ARMOR;
    }

    /**
     * [NAUTILUS-MOUNT] 노틸러스 갑옷의 티어 서열(0=구리 … 3=다이아몬드). 갑옷이 아니면 -1.
     * 영속 컬럼이 저장하는 값이 이 서열이고, ID 자체를 저장하지 않는 이유는 네더라이트가
     * 뒤에 붙어도 저장된 월드가 그대로 읽히게 하기 위해서다.
     */
    public static int nautilusArmorTier(int id) {
        return isNautilusArmorTier(id) ? id - COPPER_NAUTILUS_ARMOR : -1;
    }

    /** [NAUTILUS-MOUNT] 티어 서열 → 갑옷 아이템 ID. 서열 밖이면 0(빈 아이템). */
    public static int nautilusArmorItemForTier(int tier) {
        return tier >= 0 && tier <= NETHERITE_NAUTILUS_ARMOR - COPPER_NAUTILUS_ARMOR
                ? COPPER_NAUTILUS_ARMOR + tier : 0;
    }

    /**
     * 셜커 상자(무염색 1 + 염색 16 = 17종)인가. 형상·보관함·내용 유지 계약이 전부 이 술어
     * 하나만 본다. client {@code blocks.ts} 의 {@code isShulkerBox} 와 같은 판정이어야 한다.
     */
    public static boolean isShulkerBox(int id) {
        return id == SHULKER_BOX || (id >= WHITE_SHULKER_BOX && id <= BLACK_SHULKER_BOX);
    }

    /**
     * 셜커 상자 ID → MC {@code DyeColor} 네트워크 ID(0..15). 무염색이거나 셜커 상자가 아니면
     * -1 이다(양의 {@link #sheepColor} · 하네스 {@link #harnessDyeColor} 와 같은 어휘).
     */
    public static int shulkerBoxDyeColor(int id) {
        return id >= WHITE_SHULKER_BOX && id <= BLACK_SHULKER_BOX ? id - WHITE_SHULKER_BOX : -1;
    }

    /**
     * MC {@code DyeColor} 네트워크 ID(0..15) → 염색 셜커 상자 ID. 범위 밖이면 무염색
     * {@link #SHULKER_BOX} 다(염색 레시피가 색을 못 찾아 조용히 다른 상자를 내지 않게).
     */
    public static int shulkerBoxForDyeColor(int dyeColor) {
        return dyeColor >= 0 && dyeColor < 16 ? WHITE_SHULKER_BOX + dyeColor : SHULKER_BOX;
    }

    // ── [CHEST-FAMILY] 상자 형상군 술어 ──────────────────────────────────────────
    //
    // 바닐라의 {@code block instanceof ChestBlock} 에 해당한다. 이 술어가 생기기 전에는
    // 양 권위에 {@code == Blocks.CHEST} 값 비교가 마흔 곳 가까이 복제돼 있어, 상자를 한 종
    // 늘릴 때마다 그 마흔 곳을 전부 찾아 고쳐야 했다(찾다 한 곳을 빠뜨리면 새 상자만
    // 조용히 다르게 동작한다 — 형상·충돌·지지·폭발·발광·몹 회피가 전부 갈린다).
    // {@link #isBed(int)} 가 색 침대 16종에, {@link #isDoor(int)} 가 종별·구리 문에 낸
    // 선례와 같은 계약이다: <b>모든 호출부가 값 비교를 복제하지 않고 이 술어만 쓴다.</b>

    /** {@link #chestKind(int)} 가 상자가 아닌 ID 에 내는 값. */
    public static final int CHEST_KIND_NONE = -1;
    /** 일반 상자({@link #CHEST}) 갈래. */
    public static final int CHEST_KIND_NORMAL = 0;
    /** 덫 상자({@link #TRAPPED_CHEST}) 갈래. */
    public static final int CHEST_KIND_TRAPPED = 1;
    /** 구리 상자 8종 갈래(산화 4 + 밀랍 4). */
    public static final int CHEST_KIND_COPPER = 2;
    /**
     * [ENDER-SHULKER] 엔더 상자({@link #ENDER_CHEST}) 갈래. 형상군의 다른 갈래와 <b>두 곳</b>
     * 에서 갈린다: 짝을 이루지 않고({@link #chestPairs}), 내용 27칸을 좌표가 아니라
     * 플레이어가 소유한다. 그 둘 말고는 형상·충돌·지지·발광 차단·몹 회피가 전부 같다.
     */
    public static final int CHEST_KIND_ENDER = 3;

    /**
     * <b>상자 형상군</b>인가 — 일반 상자({@link #CHEST}=69) · 덫 상자
     * ({@link #TRAPPED_CHEST}=1810) · 구리 상자 8종(1811~1818).
     *
     * <p>"형상군"은 렌더 형상(14/16 인셋 박스) · 충돌 상자 · 지지 계약 · 좌우 짝 상태
     * ({@link BuildingBlockRules#CHEST_TYPE_MASK}) · 27칸 좌표 키 보관함 · 불투명하지 않음을
     * <b>전부</b> 공유하는 집합이다. 통({@link #BARREL})은 같은 27칸 보관함이지만 풀 큐브라
     * 형상이 다르므로 <b>여기 들어가지 않는다</b> — 보관함 여부는 별도로
     * {@code InteractRules.isContainer} 가 본다.
     */
    public static boolean isChestShaped(int id) {
        // [ENDER-SHULKER] 엔더 상자도 바닐라 AbstractChestBlock 이라 같은 14/16 인셋 박스다.
        // 셜커 상자는 풀 큐브라 여기 들어오지 않는다(isShulkerBox 가 따로 소유한다).
        return id == CHEST || id == TRAPPED_CHEST || id == ENDER_CHEST
                || (id >= COPPER_CHEST && id <= WAXED_OXIDIZED_COPPER_CHEST);
    }

    /**
     * 상자 형상군의 갈래 — {@link #CHEST_KIND_NORMAL} · {@link #CHEST_KIND_TRAPPED} ·
     * {@link #CHEST_KIND_COPPER}. 상자가 아니면 {@link #CHEST_KIND_NONE}
     * (양의 {@link #sheepColor} 계약과 같은 "종이 아니면 -1" 어휘).
     *
     * <p>갈래가 갈리는 지점은 <b>물성뿐</b>이다(구리 상자만 경도 3.0 · 폭발 저항 6.0 ·
     * 돌 곡괭이 요구). 형상·보관함·짝 규칙은 갈래와 무관하게 형상군 전체가 같다.
     */
    public static int chestKind(int id) {
        if (id == CHEST) return CHEST_KIND_NORMAL;
        if (id == TRAPPED_CHEST) return CHEST_KIND_TRAPPED;
        if (id == ENDER_CHEST) return CHEST_KIND_ENDER;
        if (id >= COPPER_CHEST && id <= WAXED_OXIDIZED_COPPER_CHEST) return CHEST_KIND_COPPER;
        return CHEST_KIND_NONE;
    }

    /**
     * 두 상자가 <b>큰 상자로 짝을 이루는가</b>. 형상군 전체에 걸린 규칙 하나다:
     * <b>같은 ID 끼리만 짝을 이룬다.</b>
     *
     * <p>[B] 바닐라에서 덫 상자는 덫 상자끼리만 짝을 이루고 일반 상자와 나란히 놓아도
     * 합쳐지지 않는다. 구리 상자의 <b>산화 단계가 다른 둘</b>이 짝을 이루는지는 위키 문장이
     * 갈려 [B] 로 확인하지 못했으므로, 이 저장소는 같은 규칙을 그대로 밀어 <b>같은 ID</b> 로
     * 못박는다([C] — {@code docs/research/mc-26x-chests.md} §5). 밀랍 여부·산화 단계가 다르면
     * 짝을 이루지 않는다.
     *
     * <p><b>엔더 상자는 예외다</b>: 같은 ID 끼리도 짝을 이루지 않는다. [B] 바닐라
     * {@code EnderChestBlock.getStateForPlacement} 는 이웃을 아예 보지 않고 언제나
     * {@code ChestType.SINGLE} 을 낸다 — 내용이 좌표가 아니라 플레이어에게 매여 있어
     * "두 좌표를 54칸으로 합친다"는 개념 자체가 성립하지 않기 때문이다. 규칙을 여기
     * 한 곳에만 두므로 {@code chestPlacement} · {@code chestStateAfterNeighborChange} ·
     * {@code openChestAccess} 가 저절로 단일 27칸으로 떨어진다.
     */
    public static boolean chestPairs(int id, int neighborId) {
        if (id == ENDER_CHEST) return false;
        return isChestShaped(id) && id == neighborId;
    }

    // ── [CROP-BERRY] 작물·열매류 결손 보충 1700~1729 ─────────────────────────────
    //
    // 이 트랙에 배정된 구간은 1700~1729 다. 1637~1699 · 1730~1759 는 다른 트랙 배정/예약
    // 구간이라 되메우지 않는다.
    //
    // <b>전수 조사 결과 대부분이 이미 있다</b> — 당근({@link #CARROT_CROP} 229) · 감자
    // ({@link #POTATO_CROP} 230) · 비트({@link #BEETROOT_CROP} 231, 씨앗 분리 349) 는 성장
    // 단계 · 씨앗/작물 드랍 · 경작지 계약 · 뼛가루가 전부 {@code CropRules} 에 있고, 동굴 덩굴
    // ({@link #CAVE_VINES} 381 · {@link #CAVE_VINES_PLANT} 382) 도 열매 유무 상태 · 광량 14
    // ({@code MobLightEngine}) · 우클릭 수확 · 뼛가루 성장이 전부 있다. 다시마
    // ({@link #KELP} 57) 와 말린 다시마({@link #DRIED_KELP} 1636, [COOKING] 트랙 소유) 도 있다.
    //
    // 그래서 이 구간이 신설하는 것은 <b>실제로 없던 둘</b>뿐이다.
    /**
     * 독 감자. 바닐라 그대로 <b>다 자란 감자 작물을 수확할 때 2% 확률로 하나 더</b> 나오며
     * ({@code minecraft:blocks/potatoes} 전리품표의 세 번째 풀, {@code random_chance 0.02})
     * 그 외 획득 경로는 없다 — 재료 대체도 새 획득 경로 신설도 하지 않는다.
     * 섭취는 기존 음식 경로 그대로 허기 2 · 포화 1.2 이고 <b>60% 확률로 독 I 5초</b>가 붙는다
     * ([A] {@code Foods.POISONOUS_POTATO}: nutrition 2 · saturationModifier 0.3 ·
     * {@code effect(POISON, 100틱, 0)} 확률 0.6). 순수 아이템이라
     * {@link #BLOCK_ID_HIGH_WATER} 를 올리지 않는다.
     */
    public static final int POISONOUS_POTATO = 1700;

    /**
     * 달콤한 열매 덤불. 아이템 {@code SWEET_BERRIES}(449)는 이미 있었지만 <b>덤불 블록이 없어
     * 획득 경로가 하나도 없던</b> 결손을 닫는다. 열매를 바닥에 놓으면 심어지고, 랜덤틱마다
     * 20% 로 age 0→3 까지 자라며(위 칸 광량 ≥ 9), age 2 부터 우클릭으로 열매만 딸 수 있다.
     * 수치 정본은 {@code engine.crop.SweetBerryBushRules} 이고 근거는 [A]
     * {@code SweetBerryBushBlock} · {@code docs/research/mc-crops-berries.md} §3 이다.
     *
     * <p><b>이 구간이 만드는 유일한 월드 블록</b>이지만 값이 1701 이라
     * {@link #BLOCK_ID_HIGH_WATER}(황금 민들레 1733) 밑이므로 그 줄을 올리지 않는다.
     * 1702~1729 는 이 트랙의 예약 공백으로 남는다.
     */
    public static final int SWEET_BERRY_BUSH = 1701;

    /** 축 인덱스(0=Y,1=X,2=Z) → 사슬 ID. client {@code CHAIN_BY_AXIS} 와 값이 같아야 한다. */
    public static final int[] CHAIN_BY_AXIS = { CHAIN, CHAIN_X, CHAIN_Z };

    /** 사슬 3축(1336~1338)인가. 물성·지도 색·렌더가 이 하나만 본다. */
    public static boolean isChainBlock(int id) {
        return id >= CHAIN && id <= CHAIN_Z;
    }

    /**
     * [COPPER-CHAIN] 축 하나가 차지하는 ID 폭. 산화 4단계가 안쪽에 있으므로 곧
     * {@link #COPPER_OXIDATION_STAGES} 와 같은 값이고, 그래서 각 축이 4연속 한 행이 된다.
     */
    private static final int COPPER_CHAIN_AXIS_STRIDE = COPPER_OXIDATION_STAGES;
    /** [COPPER-CHAIN] 밀랍 한 벌이 차지하는 ID 폭(축 3 × 산화 4). */
    private static final int COPPER_CHAIN_WAX_STRIDE = 3 * COPPER_CHAIN_AXIS_STRIDE;

    /** 구리 사슬 24종(1430~1453)인가. 물성·채굴 티어·지도 색·렌더가 이 하나만 본다. */
    public static boolean isCopperChain(int id) {
        return id >= COPPER_CHAIN && id <= WAXED_OXIDIZED_COPPER_CHAIN_Z;
    }

    /**
     * 구리 사슬의 축 인덱스(0=Y, 1=X, 2=Z). 구리 사슬이 아니면 −1 이다.
     * client {@code copperChainAxis} 와 같은 판정이어야 한다.
     */
    public static int copperChainAxis(int id) {
        if (!isCopperChain(id)) return -1;
        return ((id - COPPER_CHAIN) % COPPER_CHAIN_WAX_STRIDE) / COPPER_CHAIN_AXIS_STRIDE;
    }

    /**
     * 같은 산화 단계·같은 밀랍 여부를 유지한 채 축만 바꾼 구리 사슬 ID. 구리 사슬이 아니면
     * −1 이다. 설치(클릭한 면의 축)와 인벤토리 접기(축 0)가 이 하나만 쓴다 —
     * 산술을 호출부로 복제하면 산화·밀랍 좌표가 조용히 어긋난다.
     */
    public static int copperChainWithAxis(int id, int axis) {
        int current = copperChainAxis(id);
        if (current < 0 || axis < 0 || axis > 2) return -1;
        return id + (axis - current) * COPPER_CHAIN_AXIS_STRIDE;
    }

    /** 어느 재질이든 사슬인가(철 3축 + 구리 24종). 렌더 형상·소리가 이 하나만 본다. */
    public static boolean isAnyChain(int id) {
        return isChainBlock(id) || isCopperChain(id);
    }

    /**
     * 랜턴을 매달 수 있는 지지인가. 바닐라 {@code LanternBlock.canSurvive} 는 부착면이
     * sturdy 이거나 <b>사슬</b>이면 통과시킨다 — 사슬 아래 랜턴이 성립하는 유일한 이유다.
     * 매달림 상태를 아직 두지 않아 판정만 계약으로 고정한다. [A] {@code LanternBlock}.
     * 바닐라 태그 {@code #minecraft:chains} 는 구리 사슬까지 포함하므로 재질을 가리지 않는다.
     */
    public static boolean supportsHangingLantern(int id) {
        return isAnyChain(id);
    }

    /**
     * 유황 계열 월드 블록 10종(1300~1309)인가. 채굴 티어·폭발 저항·지도 색이 이 하나만 본다.
     * client {@code blocks.ts} 의 {@code isSulfurBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isSulfurBlock(int id) {
        return id == SULFUR_BLOCK
                || id >= CHISELED_SULFUR && id <= SULFUR_SPIKE;
    }

    public static boolean isCinnabarBlock(int id) {
        return id >= CHISELED_CINNABAR && id <= POLISHED_CINNABAR_WALL;
    }

    /**
     * 색 인덱스(DyeColor 네트워크 ID) → 색 유리 / 색 유리판 ID. 염색 제작이 이 표 하나만 읽어
     * 색을 옮긴다. client `blocks.ts` 의 같은 이름 표와 값이 같아야 한다.
     */
    public static final int[] STAINED_GLASS_BY_COLOR = {
            WHITE_STAINED_GLASS, ORANGE_STAINED_GLASS, MAGENTA_STAINED_GLASS,
            LIGHT_BLUE_STAINED_GLASS, YELLOW_STAINED_GLASS, LIME_STAINED_GLASS,
            PINK_STAINED_GLASS, GRAY_STAINED_GLASS, LIGHT_GRAY_STAINED_GLASS,
            CYAN_STAINED_GLASS, PURPLE_STAINED_GLASS, BLUE_STAINED_GLASS,
            BROWN_STAINED_GLASS, GREEN_STAINED_GLASS, RED_STAINED_GLASS, BLACK_STAINED_GLASS,
    };
    public static final int[] STAINED_GLASS_PANE_BY_COLOR = {
            WHITE_STAINED_GLASS_PANE, ORANGE_STAINED_GLASS_PANE, MAGENTA_STAINED_GLASS_PANE,
            LIGHT_BLUE_STAINED_GLASS_PANE, YELLOW_STAINED_GLASS_PANE, LIME_STAINED_GLASS_PANE,
            PINK_STAINED_GLASS_PANE, GRAY_STAINED_GLASS_PANE, LIGHT_GRAY_STAINED_GLASS_PANE,
            CYAN_STAINED_GLASS_PANE, PURPLE_STAINED_GLASS_PANE, BLUE_STAINED_GLASS_PANE,
            BROWN_STAINED_GLASS_PANE, GREEN_STAINED_GLASS_PANE, RED_STAINED_GLASS_PANE,
            BLACK_STAINED_GLASS_PANE,
    };

    /** 색 유리 16색(1141~1156)인가. 무색 유리 10 은 포함하지 않는다. */
    public static boolean isStainedGlass(int id) {
        return id >= WHITE_STAINED_GLASS && id <= BLACK_STAINED_GLASS;
    }

    /** 색 유리판 16색(1157~1172)인가. 무색 유리판 92 는 포함하지 않는다. */
    public static boolean isStainedGlassPane(int id) {
        return id >= WHITE_STAINED_GLASS_PANE && id <= BLACK_STAINED_GLASS_PANE;
    }

    /**
     * 무색 유리(10) 또는 색 유리 16색인가. 바닐라 {@code AbstractGlassBlock} 의 풀 큐브 갈래
     * 대응이며, 빛 투과·sturdy 면·드랍·잔디 조명이 전부 이 판정 하나만 본다.
     * client {@code blocks.ts} 의 {@code isGlassBlock} 과 같은 판정이어야 한다.
     */
    public static boolean isGlassBlock(int id) {
        return id == GLASS || isStainedGlass(id);
    }

    /**
     * 무색 유리판(92) 또는 색 유리판 16색인가. 철창(93)은 재질이 달라 포함하지 않는다 —
     * 형상만 같은 계열이므로 형상 판정은 {@code BuildingBlockRules.isPane} 이 맡는다.
     * client {@code blocks.ts} 의 {@code isGlassPane} 과 같은 판정이어야 한다.
     */
    public static boolean isGlassPane(int id) {
        return id == GLASS_PANE || isStainedGlassPane(id);
    }

    /** 마지막 월드 블록 ID. 순수 아이템은 이 값을 올리지 않는다. */
    // [CORAL-REEF] 산호 어휘 40 종(1880~1919)이 [ENDER-SHULKER] 셜커 상자(1857)를 넘어
    // 여기까지 올린다 — 마흔 전부가 월드 블록이고 순수 아이템은 없다(모두 자기 자신이
    // 아이템이다). 1859~1879 는 [ENDER-SHULKER] 의 남은 예약 공백이다.
    // [CONDUIT] 콘딧(1951)이 산호 어휘를 넘어 여기까지 올린다 — 이 트랙의 두 ID 중 월드
    // 블록은 콘딧 하나이고 바다의 심장(1950)은 순수 아이템이라 이 줄을 올리지 않는다.
    // 1910~1949 는 다른 트랙 구간, 1952~1959 는 이 트랙의 예약 공백이다.
    // [ARCHAEOLOGY] 장식 항아리 8 종(1970~1977)이 콘딧을 넘어 여기까지 올린다 — 여덟 전부
    // 월드 블록이고, 도자기 조각 7 종(1962~1968)은 순수 아이템이라 이 줄을 올리지 않는다.
    // 1969 · 1978~1999 는 이 트랙의 예약 공백이다.
    // [TRIAL-GAP] 금고 보상 표의 월드 블록 EMERALD_BLOCK(2331) · HEAVY_CORE(2332)가 순수 아이템
    // 2313~2330 을 건너 올렸고, [FROST-SOUL] 서리 얼음·영혼 모래·영혼 흙(2333~2335)이 그 바로 뒤에
    // 붙는다. [CONTAINER-MENUS] 공급기·제작기 월드 블록(2336~2337)이 그 뒤에 붙는다.
    // 그 사이 순수 아이템은 classifyPureItemRange 로 남는다.
    /** DropperBlock: DispenserBlock state (facing 0..5 + TRIGGERED bit 3), nine-slot menu. */
    public static final int DROPPER = 2336;
    /** CrafterBlock: ORIENTATION (FrontAndTop ordinal + 2) % 12 + TRIGGERED bit 4 + CRAFTING bit 5. */
    public static final int CRAFTER = 2337;
    // [UTILITY] 신호기·주크박스·슬라임 블록(2380~2382)이 다른 레인 구간 2338~2379 를 건너 올리고,
    // 흑암석 계열(2417~2419)이 같은 레인의 순수 아이템 2383~2416 을 건너 여기까지 올린다.
/** Minecraft 26.3 colored building shapes; existing IDs remain unchanged. */
    public static final int ORANGE_WOOL_STAIRS = 2452;
    public static final int MAGENTA_WOOL_STAIRS = 2453;
    public static final int LIGHT_BLUE_WOOL_STAIRS = 2454;
    public static final int YELLOW_WOOL_STAIRS = 2455;
    public static final int LIME_WOOL_STAIRS = 2456;
    public static final int PINK_WOOL_STAIRS = 2457;
    public static final int GRAY_WOOL_STAIRS = 2458;
    public static final int LIGHT_GRAY_WOOL_STAIRS = 2459;
    public static final int CYAN_WOOL_STAIRS = 2460;
    public static final int PURPLE_WOOL_STAIRS = 2461;
    public static final int BLUE_WOOL_STAIRS = 2462;
    public static final int BROWN_WOOL_STAIRS = 2463;
    public static final int GREEN_WOOL_STAIRS = 2464;
    public static final int RED_WOOL_STAIRS = 2465;
    public static final int BLACK_WOOL_STAIRS = 2466;
    public static final int WHITE_CONCRETE_STAIRS = 2467;
    public static final int ORANGE_CONCRETE_STAIRS = 2468;
    public static final int MAGENTA_CONCRETE_STAIRS = 2469;
    public static final int LIGHT_BLUE_CONCRETE_STAIRS = 2470;
    public static final int YELLOW_CONCRETE_STAIRS = 2471;
    public static final int LIME_CONCRETE_STAIRS = 2472;
    public static final int PINK_CONCRETE_STAIRS = 2473;
    public static final int GRAY_CONCRETE_STAIRS = 2474;
    public static final int LIGHT_GRAY_CONCRETE_STAIRS = 2475;
    public static final int CYAN_CONCRETE_STAIRS = 2476;
    public static final int PURPLE_CONCRETE_STAIRS = 2477;
    public static final int BLUE_CONCRETE_STAIRS = 2478;
    public static final int BROWN_CONCRETE_STAIRS = 2479;
    public static final int GREEN_CONCRETE_STAIRS = 2480;
    public static final int RED_CONCRETE_STAIRS = 2481;
    public static final int BLACK_CONCRETE_STAIRS = 2482;
    public static final int WHITE_CONCRETE_SLAB = 2483;
    public static final int ORANGE_CONCRETE_SLAB = 2484;
    public static final int MAGENTA_CONCRETE_SLAB = 2485;
    public static final int LIGHT_BLUE_CONCRETE_SLAB = 2486;
    public static final int YELLOW_CONCRETE_SLAB = 2487;
    public static final int LIME_CONCRETE_SLAB = 2488;
    public static final int PINK_CONCRETE_SLAB = 2489;
    public static final int GRAY_CONCRETE_SLAB = 2490;
    public static final int LIGHT_GRAY_CONCRETE_SLAB = 2491;
    public static final int CYAN_CONCRETE_SLAB = 2492;
    public static final int PURPLE_CONCRETE_SLAB = 2493;
    public static final int BLUE_CONCRETE_SLAB = 2494;
    public static final int BROWN_CONCRETE_SLAB = 2495;
    public static final int GREEN_CONCRETE_SLAB = 2496;
    public static final int RED_CONCRETE_SLAB = 2497;
    public static final int BLACK_CONCRETE_SLAB = 2498;
    public static final int[] WOOL_STAIRS_BY_DYE_COLOR = {WHITE_WOOL_STAIRS,ORANGE_WOOL_STAIRS,MAGENTA_WOOL_STAIRS,LIGHT_BLUE_WOOL_STAIRS,YELLOW_WOOL_STAIRS,LIME_WOOL_STAIRS,PINK_WOOL_STAIRS,GRAY_WOOL_STAIRS,LIGHT_GRAY_WOOL_STAIRS,CYAN_WOOL_STAIRS,PURPLE_WOOL_STAIRS,BLUE_WOOL_STAIRS,BROWN_WOOL_STAIRS,GREEN_WOOL_STAIRS,RED_WOOL_STAIRS,BLACK_WOOL_STAIRS};
    public static boolean isWoolStairs(int id) { return id == WHITE_WOOL_STAIRS || id >= ORANGE_WOOL_STAIRS && id <= BLACK_WOOL_STAIRS; }
    public static boolean isConcreteStairs(int id) { return id >= WHITE_CONCRETE_STAIRS && id <= BLACK_CONCRETE_STAIRS; }
    public static boolean isConcreteSlab(int id) { return id >= WHITE_CONCRETE_SLAB && id <= BLACK_CONCRETE_SLAB; }
    public static final int OPEN_EYEBLOSSOM = 2515;
    public static final int BLOCK_ID_HIGH_WATER = 2515;
    /**
     * 블록과 순수 아이템을 합친 공유 프로토콜 ID 상한. 441~444 낚시 예약 공백 뒤
     * wildlife interaction 아이템 446–451, 염료 452–467, 복어 양동이 468,
     * 겉날개·폭죽 로켓 469–472, 레이드 전리품 475–481, 낚시 전리품 484–488,
     * 동물 상호작용 489–490, 피글린 장비 501, 심층암 가공 계열 월드 블록 506–520을
     * append-only로 추가했고, 양털·카펫 16색 561–592 와 종별 목재 가공 계열 월드 블록
     * 601–649와 석재 가공 계열 월드 블록 701–721이 그 뒤를 잇고, 마지막이 양조 순수 아이템
     * 801–815이고(양조 재료·물약 801–813 + 획득 경로 복구 아이템 {@link #BLAZE_ROD} 814 ·
     * {@link #NETHER_WART} 815), 그 뒤가 제련로 변형 점화 쌍둥이 월드 블록 841–842,
     * 그 뒤가 침대 16색 월드 블록 851–866이고, 마지막이 벗긴 원목 24종 월드 블록 871–894다.
     * 473~474·482~483·491~500·502~505·521~600·650~700·722~800은 다른 트랙 예약
     * 공백이고 816~832는 양조 트랙의 강화 물약(발광석 가루 816 + 연장·II 등급 817~832),
     * 833~840은 그 트랙의 남은 예약 공백이고, 843~850은 제련로 변형 트랙, 867~870은 침대 트랙,
     * 895~930은 벗긴 원목 트랙 예약 공백이다. 그 뒤가 해저 신전 전리품 —
     * 젖은 스펀지 월드 블록 931 과 프리즈머린 순수 아이템 932–933 이고,
     * 마지막이 프리즈머린 계열·바다 랜턴·마른 스펀지 월드 블록 934–945다.
     * 946~960은 프리즈머린 트랙 예약 공백이라 되메우지 않는다.
     * 그 뒤가 [QUARTZ] 석영 계열 — 순수 아이템 네더 석영 961 과 월드 블록 962–975 이고
     * 976~985 는 같은 트랙 예약 공백이다.
     * 그 뒤가 [CONCRETE] 콘크리트 16 + 콘크리트 가루 16 + 테라코타 잔여 10 + 유광
     * 테라코타 16 — 월드 블록 1076–1133 이고 1134~1140 이 같은 트랙 예약 공백이다.
     * 986~1075 는 다른 트랙 배정 구간이라 되메우지 않는다.
     * [STAINED-GLASS] 색 유리 16 + 색 유리판 16 — 월드 블록 1141–1172 뒤 1173~1175는
     * 예약 공백이고, FIRE 월드 블록 1176·FILLED_MAP/TOTEM 순수 아이템 1177~1178이 이어진다.
     * 그 뒤가 [ROTTEN-LEATHER] 썩은 가죽 재료·방어구 순수 아이템 1179~1183이고,
     * 그 뒤가 [DEEP-DARK] 스컬크 계열 월드 블록 1200~1204 와 말린 가스트 월드 블록 1250 이며
     * 1184~1199는 다른 트랙 배정 구간, 1205~1249는 딥다크 트랙 예약 공백이다.
     * 마지막이 [TRIAL] 트라이얼 챔버 기반 — 월드 블록 1251~1252 와 순수 아이템 1253 이며
     * 1254~1270은 같은 트랙 예약 공백이다.
     * 그 뒤가 [FURNITURE-26.3] 26.3 가구 — 건초 침대 월드 블록 1281 과 쿠션 16색 월드 블록
     * 1282~1297 이며 1271~1280은 다른 트랙 배정 구간, 1298~1299는 같은 트랙 예약 공백이다.
     * 마지막이 [SULFUR] 유황 동굴 지대 — 월드 블록 1300~1309 와 순수 아이템 유황 가루 1310
     * 이며 1311~1319는 같은 트랙 예약 공백이다.
     * 마지막이 [OPENABLE-METAL] 금속 개폐·격자 계열 — 철 문 1370 · 철 다락문 1371 ·
     * 구리 창살 8종 1372~1379 로 전부 월드 블록이고 순수 아이템은 없다.
     * 1320~1369는 다른 트랙 배정 구간, 1380~1399는 같은 트랙 예약 공백이다.
     * 그 뒤가 [ZOMBIE-ANIMAL] 좀비 동물 10종 — 순수 아이템 상한 달걀 1410 하나뿐이고
     * 1400~1409는 다른 트랙 배정 구간, 1411~1419는 같은 트랙 예약 공백이다.
     * 마지막이 [COPPER-CHAIN] 구리 사슬 24종 1430~1453 으로 전부 월드 블록이고 순수 아이템은
     * 없다. 1420~1429는 다른 트랙 배정 구간, 1454~1459는 같은 트랙 예약 공백이다.
     * 마지막이 [BRIMSTONE] 황린 잠복자·화염 저항 사슬 — 월드 블록 마그마 1460 · 황린 갑각
     * 트로피 1461 과 순수 아이템 마그마 크림 1462 · 화염 저항 물약 네 종 1463~1466 이며
     * 1467~1479는 같은 트랙 예약 공백이다.
     * 마지막이 [WAVE-86-97] 신종 사망 드랍 순수 아이템 — 브리즈 막대 1480 · 팬텀 막 1481 이며
     * 1482~1489는 같은 트랙 예약 공백이다. 워든의 스컬크 촉매는 이미 월드 블록 1202 라
     * 새 ID 를 만들지 않는다.
     * 마지막이 [COOKING] 요리 계열 — 월드 블록 케이크 1630 과 순수 아이템 코코아 콩 1631 ·
     * 쿠키 1632 · 호박 파이 1633 · 수상한 스튜 두 종 1634~1635 · 말린 다시마 1636 이며
     * 1482~1629는 다른 트랙 배정 구간, 1637~1669는 같은 트랙 예약 공백이다.
     * 마지막이 [POTION-GAP] 힘·수중 호흡·도약·야간 투시 물약 20종 1760~1779 로 전부 순수
     * 아이템이며(재료는 하나도 신설하지 않았다) 1637~1759는 다른 트랙 배정 구간,
     * 1780~1789는 같은 트랙 예약 공백이다.
     * 마지막이 [HARNESS] 하네스 16색 1790~1805 로 전부 순수 아이템이며(가죽·유리·색 양털이
     * 이미 전부 있어 재료를 하나도 신설하지 않았다) 1806~1809는 같은 트랙 예약 공백이다.
     * 마지막이 [CHEST-FAMILY] 상자 형상군 월드 블록 아홉 — 덫 상자 1810 · 구리 상자 산화 4종
     * 1811~1814 · 밀랍 4종 1815~1818 이며(재료는 상자·철사 덫 갈고리 488·구리 주괴가 이미
     * 전부 있어 하나도 신설하지 않았다) 1819~1839는 같은 트랙 예약 공백이다.
     * 마지막이 [ENDER-SHULKER] 엔더 상자 1840 · 셜커 상자 무염색 1841 · 셜커 상자 염색 16색
     * 1842~1857 로 전부 월드 블록이며(재료는 흑요석 · 엔더의 눈 1604 · 상자 · 셜커 껍데기
     * 1613 · 염료 16색이 이미 전부 있어 하나도 신설하지 않았다) 1859~1879는 같은 트랙
     * 예약 공백이다.
     * 마지막이 [DIAMOND-SHIELD] 다이아 방패 1858 하나로 순수 아이템이며(재료는 방패 321 ·
     * 다이아몬드 286 이 이미 있어 하나도 신설하지 않았다), 1858 은 상자 웨이브가 예약한
     * 1810~1857 구간 <b>밖</b>의 append-only 다음 칸이다.
     * 마지막이 [CORAL-REEF] 산호 어휘 40 종 1880~1919 로 전부 월드 블록이며(마흔 모두 자기
     * 자신이 아이템이라 순수 아이템을 하나도 신설하지 않았다), 1870~1879 는 [ENDER-SHULKER]
     * 예약 공백이라 되메우지 않았다.
     * 마지막이 [TRIDENT] 삼지창 1920 하나로 순수 아이템이며 재료를 하나도 신설하지 않았다
     * (바닐라 삼지창에는 제작법이 없다 — 드라운드 장비 드랍이 유일한 입수 경로다).
     * 1910~1919 는 [CORAL-REEF] 벽 산호 부채 구간이고 1921~1929 는 같은 트랙 예약
     * 공백이다. 아틀라스 슬롯은 <b>0개</b> 쓴다 — 순수 아이템이라 아이콘이 icons.ts 의
     * 절차적 그리드다([DIAMOND-SHIELD]·[HARNESS] 와 같은 선례).
     * 마지막이 [CONDUIT] 콘딧 사슬 둘 — 순수 아이템 바다의 심장 1950 과 월드 블록 콘딧 1951
     * 이며(앵무조개 껍데기 486 · 프리즈머린 계열 934~945 가 이미 전부 있어 재료를 하나도
     * 신설하지 않았다) 1910~1949 는 다른 트랙 구간, 1952~1959 는 같은 트랙 예약 공백이다.
     * 마지막이 [TURTLE] 거북 생애 주기 셋 — 월드 블록 거북 알 1930 과 순수 아이템 거북
     * 등딱지 조각 1931 · 거북 등껍질 1932 이며(해초 58 이 이미 있었고 등껍질은 조각
     * 다섯만으로 만들어 재료를 하나도 신설하지 않았다) 1921~1929 는 [TRIDENT] 예약 공백,
     * 1933~1949 는 같은 트랙 예약 공백이다. 거북 장인의 물약은 효과가 둘이라 이 저장소의
     * 물약 축에 담기지 않아 ID 를 잡지 않았다({@link #TURTLE_EGG} 절 주석이 근거를 소유한다).
     * 세 값 모두 콘딧 1951 보다 낮아 아래 두 상한 줄을 올리지 않는다.
     * 마지막이 [SPEAR] 창 여섯 티어 2000~2005 로 <b>전부 순수 아이템</b>이며 재료를 하나도
     * 신설하지 않았다(막대 260 · 판자 · 돌 · 구리 주괴 393 · 철 주괴 298 · 금 주괴 299 ·
     * 다이아몬드 286 이 이미 있었다). 순수 아이템이라 {@link #BLOCK_ID_HIGH_WATER} 는 그대로
     * 두고 이 줄만 {@link #DIAMOND_SPEAR} 값으로 올린다. 2006 은 네더라이트 창 예약이라
     * 등록하지 않고(핀 §6), 2007~2011 은 이 트랙의 남은 배정 구간, 2012~2019 는 예약 공백,
     * 1978~1999 는 [ARCHAEOLOGY] 예약 공백이라 되메우지 않는다.
     * 마지막이 [SHIELD-FAMILY] 방패 네 티어 2020~2023 으로 <b>전부 순수 아이템</b>이며 재료를
     * 하나도 신설하지 않았다(가죽 334 · 조약돌 8 · 구리 주괴 393 · 금 주괴 299 · 철 주괴 298 이
     * 이미 있었다). 순수 아이템이라 {@link #BLOCK_ID_HIGH_WATER} 는 그대로 두고 이 줄만
     * {@link #GOLD_SHIELD} 값으로 올린다. 철 방패는 이 트랙이 만들지 않는다 — 그 자리는
     * {@code PlayerInventory.SHIELD} 321 이고 다이아 방패도 1858 로 이미 있어, 티어 여섯 중
     * <b>넷만</b> 신설했다(같은 물건에 ID 를 둘 주지 않는다). 2006~2019 는 [SPEAR] 의 예약·
     * 배정 구간이라 되메우지 않고 2024~2039 는 같은 트랙 예약 공백이다.
     * 마지막이 [NAUTILUS-MOUNT] 노틸러스 갑옷 네 티어 2040~2043 으로 <b>전부 순수 아이템</b>
     * 이며 재료를 하나도 신설하지 않았다(구리 주괴 393 · 철 주괴 298 · 금 주괴 299 ·
     * 다이아몬드 286 이 이미 있었다). 순수 아이템이라 {@link #BLOCK_ID_HIGH_WATER} 는 그대로
     * 두고 이 줄만 {@link #DIAMOND_NAUTILUS_ARMOR} 값으로 올린다. 2024~2039 는
     * [SHIELD-FAMILY] 의 예약 공백이라 되메우지 않고 그 <b>뒤</b>인 2040 에서 시작했다.
     * 네더라이트 갑옷(2044)은 이 저장소에 네더라이트 재료가 없어 신설하지 않았다.
     */
    // 리터럴로 둔다 — 정적판 파리티 테스트가 이 줄의 **원문**을 client 값과 대조한다.
    // 오솔길 유적 순수 아이템 MUSIC_DISC_RELIC(2259) 뒤에 Ocean archaeology processor의
    // BARRIER(2260), Ruined Portal의 CRYING_OBSIDIAN(2261), Abandoned Camp에 기존 ID가 없던
    // BAMBOO_FENCE(2262)·WHITE_WOOL_STAIRS(2263)를 append 했다.
    // [TRIAL] 불길한 트라이얼 열쇠 OMINOUS_TRIAL_KEY(2313)가 차원 예약 뒤 첫 순수 아이템이다.
    // [TRIAL-GAP] 트라이얼 챔버 표의 순수 아이템 2314~2330 · 월드 블록 2331~2332 가 그 뒤에 붙었고,
    // [FROST-SOUL] 월드 블록 2333~2335(SOUL_SOIL), [CONTAINER-MENUS] 월드 블록 DROPPER(2336) ·
    // CRAFTER(2337)가 이어지고, [GLOWING] 분광 화살 SPECTRAL_ARROW(2338), [CONTAINER-MENUS] 경험치 병
    // EXPERIENCE_BOTTLE(2339), [MACE] 조율자 배정 2370 의 순수 아이템 MACE, [UTILITY] 월드 블록 2380~2382 ·
    // 순수 아이템 2383~2416 · 흑암석 계열 월드 블록 2417~2419 가 붙었고, [CONTAINER-MENUS] 갑옷 거치대
    // ARMOR_STAND(2430)와 광산 수레 2431~2435(TNT_MINECART), 벌레잡이풀 2429와 살점 블록 2436~2437이 등록된다.
    public static final int PROTOCOL_ID_HIGH_WATER = 2515;
    /** 블록과 순수 아이템이 공유하는 append-only 프로토콜 ID 표의 독립 용량입니다. */
    public static final int PROTOCOL_ID_TABLE_CAPACITY = 4096;
    /**
     * 블록 메타데이터 표의 용량이자 경계 평면이 무손실 압축하는 ID 상한이다.
     * 심층암 가공 계열이 512를 넘으면서 9비트로는 경계 ID를 담을 수 없어 10비트로 넓혔고,
     * 구리·유리·콘크리트 계열이 1024의 남은 여유를 넘기므로 다시 11비트로 넓혔다
     * (client/src/engine/ChunkMesher.ts 의 경계 평면 상위 비트셋 네 장 · render_light.rs 사본).
     */
    public static final int BLOCK_ID_TABLE_CAPACITY = 4096;

    // 청크/월드 상수 (§1)
    public static final int CHUNK_X = 16;
    public static final int CHUNK_Z = 16;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    public static final int CHUNK_Y = MAX_Y - MIN_Y + 1;
    public static final int CHUNK_BLOCKS = CHUNK_X * CHUNK_Z * CHUNK_Y; // 98304
    /** Vanilla sea level is the exclusive upper fluid bound: default ocean water ends at Y=62. */
    public static final int SEA_LEVEL = 63;

    private static final SupportMetadata UNSPECIFIED_SUPPORT =
            new SupportMetadata(SupportKind.UNSPECIFIED, AIR);
    // 월드 블록의 논리 상한과 테이블 용량을 분리해 다음 추가가
    // 즉시 고정 길이 예외를 만들지 않도록 독립시킨다. 미등록 슬롯은 UNSPECIFIED로 남는다.
    private static final SupportMetadata[] SUPPORT_BY_BLOCK_ID =
            new SupportMetadata[BLOCK_ID_TABLE_CAPACITY];
    // 월드 블록이 아니지만 공유 프로토콜 ID를 차지하는 순수 아이템(CONTRACT §3).
    // 지지 메타데이터가 없는 이유를 명시적 분류로 남겨, 새 ID가 두 표 어디에도 없는
    // "조용한 미분류" 상태로 남지 못하게 한다.
    private static final boolean[] PURE_ITEM_BY_ID = new boolean[PROTOCOL_ID_TABLE_CAPACITY];

    static {
        if (BLOCK_ID_TABLE_CAPACITY <= BLOCK_ID_HIGH_WATER) {
            throw new IllegalStateException("block metadata table does not cover registered IDs");
        }
        if (PROTOCOL_ID_HIGH_WATER < BLOCK_ID_HIGH_WATER) {
            throw new IllegalStateException("protocol high-water mark is below the world block range");
        }
        if (PROTOCOL_ID_TABLE_CAPACITY <= PROTOCOL_ID_HIGH_WATER) {
            throw new IllegalStateException("protocol ID table does not cover registered IDs");
        }
        // [COPPER] 정본 표의 각 행은 반드시 **4연속 ID** 여야 한다 — 산화 랜덤틱의
        // block + 1 산술과 아틀라스 슬롯 base + stage 배정이 둘 다 이 전제 위에 서 있다.
        // 표에 단계를 손으로 적는 대가로 이 검사가 표와 산술의 어긋남을 로드 시점에 잡는다.
        for (int[][] families : new int[][][] {COPPER_OXIDATION_FAMILIES, WAXED_COPPER_FAMILIES}) {
            for (int[] family : families) {
                if (family.length != COPPER_OXIDATION_STAGES) {
                    throw new IllegalStateException("copper family must list every oxidation stage");
                }
                for (int stage = 1; stage < family.length; stage++) {
                    if (family[stage] != family[stage - 1] + 1) {
                        throw new IllegalStateException(
                                "copper oxidation stages must be consecutive IDs: " + family[stage]);
                    }
                }
            }
        }
        if (COPPER_OXIDATION_FAMILIES.length != WAXED_COPPER_FAMILIES.length) {
            throw new IllegalStateException("waxed copper table must mirror the oxidation table");
        }

        // [CORAL-REEF] 네 형상 모두 "살아있는 5색 + 죽은 5색" 이 연속이어야 한다 —
        // deadCoral(id) = id + 5 산술과 아틀라스 슬롯 base + species 배정이 둘 다 이 전제
        // 위에 서 있다. 구리 산화 4연속 검사와 같은 이유로 로드 시점에 못박는다.
        int[][] coralShapes = {
                {TUBE_CORAL_BLOCK, HORN_CORAL_BLOCK, DEAD_TUBE_CORAL_BLOCK, DEAD_HORN_CORAL_BLOCK},
                {TUBE_CORAL, HORN_CORAL, DEAD_TUBE_CORAL, DEAD_HORN_CORAL},
                {TUBE_CORAL_FAN, HORN_CORAL_FAN, DEAD_TUBE_CORAL_FAN, DEAD_HORN_CORAL_FAN},
                {TUBE_CORAL_WALL_FAN, HORN_CORAL_WALL_FAN,
                        DEAD_TUBE_CORAL_WALL_FAN, DEAD_HORN_CORAL_WALL_FAN},
        };
        for (int[] shape : coralShapes) {
            if (shape[1] - shape[0] != CORAL_SPECIES_COUNT - 1
                    || shape[3] - shape[2] != CORAL_SPECIES_COUNT - 1
                    || shape[2] - shape[0] != CORAL_DEAD_OFFSET) {
                throw new IllegalStateException(
                        "coral shape must be five live species followed by five dead: " + shape[0]);
            }
        }

        java.util.Arrays.fill(SUPPORT_BY_BLOCK_ID, UNSPECIFIED_SUPPORT);

        // 정본 월드 블록 ID 전부를 먼저 NONE으로 명시 등록한 뒤, 지지 대상만 구체 조건으로 덮어쓴다.
        classifyRange(AIR, LILY_PAD, SupportKind.NONE, AIR);                 // 0~39
        SUPPORT_BY_BLOCK_ID[30] = UNSPECIFIED_SUPPORT;                       // 예약 ID
        classifyRange(WATER_SOURCE, 47, SupportKind.NONE, AIR);             // 물 40~47
        classifyRange(LAVA_SOURCE, 51, SupportKind.NONE, AIR);              // 용암 48~51
        classifyRange(WALL_TORCH_N, WALL_TORCH_W, SupportKind.NONE, AIR);   // 52~55
        classify(OBSIDIAN, SupportKind.NONE, AIR);                          // 56
        classify(KELP, SupportKind.SOLID_BELOW, KELP);
        classify(SEAGRASS, SupportKind.SOLID_BELOW, AIR);
        classify(CORAL, SupportKind.SOLID_BELOW, AIR);
        classifyRange(SPAWNER_BASE, SPAWNER_BASE + 2, SupportKind.NONE, AIR); // 60~62
        classify(SEA_PICKLE, SupportKind.SOLID_BELOW, SEA_PICKLE);
        classify(SNOW, SupportKind.SOLID_BELOW, AIR);
        classify(ICE, SupportKind.NONE, AIR);
        classifyRange(MOSS_BLOCK, MOSSY_STONE_BRICK, SupportKind.NONE, AIR); // 66~68
        classify(CHEST, SupportKind.NONE, CHEST);                           // 69
        classify(LOG_X, SupportKind.NONE, LOG);                             // 70
        classify(LOG_Z, SupportKind.NONE, LOG);                             // 71
        classify(BIRCH_LOG_X, SupportKind.NONE, BIRCH_LOG);                 // 72
        classify(BIRCH_LOG_Z, SupportKind.NONE, BIRCH_LOG);                 // 73
        classify(CRAFTING_TABLE, SupportKind.NONE, CRAFTING_TABLE);         // 74
        classify(NETHER_PORTAL, SupportKind.NONE, AIR);                     // 75
        classify(TNT, SupportKind.NONE, TNT);                               // 76
        classify(PRIMED_TNT, SupportKind.NONE, AIR);                        // 77 (직접 설치·드랍 불가)
        classify(FARMLAND, SupportKind.NONE, DIRT);                         // 78 (괭이 상호작용 전용)
        classify(WHEAT_CROP, SupportKind.FARMLAND_BELOW, AIR);
        classifyRange(SANDSTONE, IRON_BARS, SupportKind.NONE, AIR);         // 80~93 구조/연결 블록
        classify(WOOD_TRAPDOOR, SupportKind.NONE, WOOD_TRAPDOOR);          // MC: 지지면 제거 후에도 유지
        classify(RAIL, SupportKind.SOLID_BELOW, RAIL);
        classify(CAMPFIRE, SupportKind.SOLID_BELOW, CAMPFIRE);
        classifyRange(ORANGE_TERRACOTTA, CHISELED_SANDSTONE, SupportKind.NONE, AIR);
        classifyRange(CLAY, NETHER_GOLD_ORE, SupportKind.NONE, AIR);        // 팩 신규 월드 블록
        classifyRange(EMERALD_ORE, REDSTONE_ORE, SupportKind.NONE, AIR);   // 광석 3종(자연 생성은 [3])
        classify(CARROT_CROP, SupportKind.FARMLAND_BELOW, AIR);
        classify(POTATO_CROP, SupportKind.FARMLAND_BELOW, AIR);
        classify(BEETROOT_CROP, SupportKind.FARMLAND_BELOW, AIR);
        classify(PUMPKIN_STEM, SupportKind.FARMLAND_BELOW, AIR);
        classifyRange(SNOW_BLOCK, COBBLED_DEEPSLATE, SupportKind.NONE, AIR);
        classifyRange(SPRUCE_LOG, CHERRY_LEAVES, SupportKind.NONE, AIR);
        classifyRange(GRANITE, MANGROVE_LEAVES, SupportKind.NONE, AIR);
        classify(MANGROVE_PROPAGULE, SupportKind.NONE, MANGROVE_PROPAGULE);
        classify(MUDDY_MANGROVE_ROOTS, SupportKind.NONE, MUDDY_MANGROVE_ROOTS);
        classify(SPRUCE_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, SPRUCE_SAPLING);
        classify(JUNGLE_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, JUNGLE_SAPLING);
        classify(ACACIA_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, ACACIA_SAPLING);
        classify(DARK_OAK_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, DARK_OAK_SAPLING);
        classify(CHERRY_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, CHERRY_SAPLING);
        classifyRange(BROWN_MUSHROOM_BLOCK, BUDDING_AMETHYST, SupportKind.NONE, AIR);
        classifyRange(SMALL_AMETHYST_BUD, AMETHYST_CLUSTER, SupportKind.NONE, AIR);
        // 인챈트 테이블·책장은 지지가 필요 없는 완전 큐브다(설치 전용, 자연 생성 없음).
        classifyRange(ENCHANTING_TABLE, BOOKSHELF, SupportKind.NONE, AIR);
        // 레이드 전리품 설치 블록. 둘 다 아래 고체 블록이 사라지면 공통 연쇄로 제거·드랍된다.
        classify(TATTERED_BANNER, SupportKind.SOLID_BELOW, TATTERED_BANNER);
        classify(CHERRY_BONSAI, SupportKind.SOLID_BELOW, CHERRY_BONSAI);
        // 심층암 가공 계열은 지지가 필요 없는 석재다. 계단·반 블록·담장도 같은 규칙이다.
        classifyRange(POLISHED_DEEPSLATE, DEEPSLATE_TILE_WALL, SupportKind.NONE, AIR);
        // 주민 직업 스테이션 13종 + 매끄러운 돌. 바닐라의 어느 스테이션도 지지면이 사라졌다고
        // 부서지지 않는다(SurvivalCheck 없음). 대장간 숫돌은 벽·천장에도 붙지만 그 부착면이
        // 사라져도 유지되므로 여기서도 SupportKind.NONE 이다.
        classifyRange(BARREL, SMOOTH_STONE, SupportKind.NONE, AIR);
        // [FURNACE-VARIANT] 점화 쌍둥이는 미점화 원본과 같은 지지 계약(NONE)이다. FURNACE_LIT(34)
        // 이 FURNACE(33) 와 같은 분류를 쓰는 것과 같은 근거 — lit 은 렌더/발광 상태일 뿐이다.
        classifyRange(BLAST_FURNACE_LIT, SMOKER_LIT, SupportKind.NONE, AIR);
        // 양털은 지지가 필요 없는 완전 큐브다. 카펫은 바닐라 CarpetBlock 과 달리 이 저장소의
        // 부분 높이 카펫 선례(MOSS_CARPET=199)와 같은 SupportKind.NONE 을 쓴다 — 부분 높이
        // 계약만 재사용하고 지지 연쇄는 새로 만들지 않는다는 이번 트랙의 범위 결정이다.
        classifyRange(WHITE_WOOL, BLACK_CARPET, SupportKind.NONE, AIR);
        // 종별 목재 가공 계열. 판자~다락문 601~642 는 지지가 필요 없고, 다락문은 참나무
        // WOOD_TRAPDOOR 와 같이 지지면이 사라져도 남으며 자신을 드랍한다(MC TrapDoorBlock).
        classifyRange(BIRCH_PLANK, MANGROVE_FENCE_GATE, SupportKind.NONE, AIR);
        for (int id = BIRCH_TRAPDOOR; id <= MANGROVE_TRAPDOOR; id++) {
            classify(id, SupportKind.NONE, id);
        }
        // 문은 참나무 DOOR_CLOSED 와 같이 아래 고체 지지가 필요하고 잃으면 자신을 드랍한다.
        for (int id = BIRCH_DOOR; id <= MANGROVE_DOOR; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        // 석재 가공 계열은 지지가 필요 없는 석재다. 계단·반 블록·담장 모두 같은 규칙이다.
        classifyRange(GRANITE_STAIRS, MOSSY_STONE_BRICK_WALL, SupportKind.NONE, AIR);
        // [STONE-RESIDUAL] 석재 잔여 계열도 지지가 필요 없는 석재다. 완전 큐브·계단·반 블록·
        // 담장 43종이 한 연속 구간이라 범위 한 줄로 끝난다(BRICK=765 는 순수 아이템이라 제외).
        classifyRange(POLISHED_GRANITE, TUFF_BRICK_WALL, SupportKind.NONE, AIR);

        classify(OAK_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, OAK_SAPLING);
        classify(BIRCH_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, BIRCH_SAPLING);

        classify(TALL_GRASS, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(DEAD_BUSH, SupportKind.SOLID_BELOW, AIR);
        classify(FLOWER_RED, SupportKind.DIRT_OR_GRASS_BELOW, FLOWER_RED);
        classify(FLOWER_YELLOW, SupportKind.DIRT_OR_GRASS_BELOW, FLOWER_YELLOW);
        // [GOLD-FOOD] 황금 민들레는 바닐라에서도 평범한 꽃이라 민들레와 같은 계약이다 —
        // 아래 흙/잔디 지지가 필요하고 지지를 잃으면 자기 자신을 드랍한다.
        classify(GOLDEN_DANDELION, SupportKind.DIRT_OR_GRASS_BELOW, GOLDEN_DANDELION);
        // [CROP-BERRY] 달콤한 열매 덤불. 바닐라 BushBlock.mayPlaceOn 은 #minecraft:dirt 에
        // **경작지**를 더한 목록이라 DIRT_OR_GRASS_BELOW 만으로는 경작지 위를 놓치므로
        // SupportRules 가 이 ID 를 메타데이터 switch 앞에서 가로챈다(마른 풀 선례와 같은 꼴).
        // 지지를 잃으면 심을 열매 하나를 돌려준다.
        classify(SWEET_BERRY_BUSH, SupportKind.DIRT_OR_GRASS_BELOW, SWEET_BERRIES);
        classify(TORCH, SupportKind.SOLID_BELOW, TORCH);
        classify(SUGARCANE, SupportKind.SAND_AND_WATER, SUGARCANE);
        classify(CACTUS, SupportKind.CACTUS_COLUMN, CACTUS);
        classify(MUSHROOM_BROWN, SupportKind.SOLID_BELOW, MUSHROOM_BROWN);
        classify(MUSHROOM_RED, SupportKind.SOLID_BELOW, MUSHROOM_RED);
        classify(DOOR_CLOSED, SupportKind.SOLID_BELOW, DOOR_CLOSED);
        classify(LADDER, SupportKind.WALL_ANY, LADDER);
        classify(BED, SupportKind.SOLID_BELOW, BED);
        // [BED-COLOR] 색 침대는 총칭 침대와 같은 계약이다 — 아래 고체 지지가 필요하고
        // 지지를 잃으면 자기 색을 드랍한다(바닐라 BedBlock 은 색마다 같은 블록 클래스다).
        for (int id = WHITE_BED; id <= BLACK_BED; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        // [FURNITURE-26.3] 건초 침대도 침대와 같은 지지 계약이다. 지지를 잃으면 자기 자신을
        // 떨군다 — 수면이 끝나 자멸할 때만 드랍이 없고(그 경로는 WorldTickLoop 이 소유한다),
        // 평범한 채굴·지지 상실은 일반 침대와 같이 아이템을 돌려준다 [B].
        classify(STRAW_BED, SupportKind.SOLID_BELOW, STRAW_BED);
        // [FURNITURE-26.3] 쿠션 16색은 카펫과 같은 계약이다 — 아래 고체 지지가 필요하고
        // 지지를 잃으면 자기 색을 떨군다(바닐라 쿠션도 지지 블록이 사라지면 부서진다 [B]).
        for (int id = WHITE_CUSHION; id <= BLACK_CUSHION; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        // [STRIPPED-LOG] 벗긴 원목 24종은 통나무와 같은 완전 큐브다 — 지지가 필요 없고
        // 부수면 (축을 접은) 자기 자신을 드랍한다. 축 변형이 총칭 ID 로 접히는 것은
        // BlockFamilies.woodLogItem 이 맡으므로 여기 supportLossDrop 은 쓰이지 않는다(NONE).
        classifyRange(STRIPPED_OAK_LOG, STRIPPED_MANGROVE_LOG_Z, SupportKind.NONE, AIR);

        // [GUARDIAN] 젖은 스펀지는 완전 큐브라 지지가 필요 없고 부수면 자기 자신을 떨군다.
        classify(WET_SPONGE, SupportKind.NONE, AIR);
        // [PRISMARINE] 마른 스펀지·프리즈머린 계열·바다 랜턴 934~945 도 지지가 필요 없다.
        // 계단·반 블록·담장까지 한 연속 구간이라 범위 한 줄로 끝난다.
        classifyRange(SPONGE, PRISMARINE_WALL, SupportKind.NONE, AIR);
        // [QUARTZ] 석영 계열 962~975 도 지지가 필요 없는 석재다. 완전 큐브·기둥 축 변형·
        // 계단·반 블록·담장이 한 연속 구간이라 범위 한 줄로 끝난다. 축 변형이 총칭 ID 로
        // 접히는 것은 BlockFamilies.quartzPillarItem 이 맡으므로 supportLossDrop 은 쓰이지
        // 않는다(NONE).
        classifyRange(QUARTZ_BLOCK, QUARTZ_BRICK_WALL, SupportKind.NONE, AIR);
        // [COPPER] 구리 계열 987~1064. 완전 큐브·계단·반 블록·격자·전구는 지지가 필요 없고,
        // 다락문은 참나무 WOOD_TRAPDOOR 와 같이 지지면이 사라져도 남으며 자신을 드랍하고,
        // 문은 DOOR_CLOSED 와 같이 아래 고체 지지가 필요하다. 밀랍 대응도 같은 계약이다.
        classifyRange(CUT_COPPER, OXIDIZED_COPPER_TRAPDOOR, SupportKind.NONE, AIR);
        classifyRange(WAXED_COPPER_BLOCK, WAXED_OXIDIZED_COPPER_TRAPDOOR, SupportKind.NONE, AIR);
        for (int id = COPPER_TRAPDOOR; id <= OXIDIZED_COPPER_TRAPDOOR; id++) {
            classify(id, SupportKind.NONE, id);
        }
        for (int id = WAXED_COPPER_TRAPDOOR; id <= WAXED_OXIDIZED_COPPER_TRAPDOOR; id++) {
            classify(id, SupportKind.NONE, id);
        }
        for (int id = COPPER_DOOR; id <= OXIDIZED_COPPER_DOOR; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        for (int id = WAXED_COPPER_DOOR; id <= WAXED_OXIDIZED_COPPER_DOOR; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        // 피뢰침은 바닐라 LightningRodBlock 이 부착면을 잃어도 유지되므로(SurvivalCheck 없음)
        // 자수정 싹과 같은 SupportKind.NONE 이다. 구리 랜턴은 바닐라 LanternBlock 이 지지면을
        // 요구하지만, 이 저장소의 부분 형상 장식 선례(CHERRY_BONSAI)와 같이 아래 고체를 잃으면
        // 자신을 드랍하는 SOLID_BELOW 를 쓴다.
        classify(LIGHTNING_ROD, SupportKind.NONE, LIGHTNING_ROD);
        classify(COPPER_LANTERN, SupportKind.SOLID_BELOW, COPPER_LANTERN);
        // [CONCRETE] 콘크리트·가루·테라코타 잔여·유광 1076~1133 은 전부 완전 큐브라 지지가
        // 필요 없다. **콘크리트 가루도 SupportKind.NONE 이다** — 중력은 지지 계약이 아니라
        // SupportRules.isGravityBlock 이 소유하는 별개 축이고, 모래·자갈이 이미 그렇다
        // (SAND 는 위 classifyRange(AIR, LILY_PAD, NONE) 안에 있다). 여기서 SOLID_BELOW 를
        // 주면 낙하 대신 지지 상실 드랍이 걸려 두 계약이 같은 칸을 두 번 처리한다.
        classifyRange(WHITE_CONCRETE, BLACK_GLAZED_TERRACOTTA, SupportKind.NONE, AIR);
        // [STAINED-GLASS] 색 유리·색 유리판 1141~1172 는 무색 유리(10)·유리판(92)과 같이
        // 지지가 필요 없다. 두 형상이 한 연속 구간이라 범위 한 줄로 끝난다.
        classifyRange(WHITE_STAINED_GLASS, BLACK_STAINED_GLASS_PANE, SupportKind.NONE, AIR);
        // FIRE의 복합 지지/가연 이웃 생존 판정은 FireBlock 규칙이 소유한다.
        classify(FIRE, SupportKind.NONE, AIR);
        // [DEEP-DARK] 스컬크 5종 1200~1204. 바닐라에서 스컬크·촉매·감지체·비명체는 완전
        // 큐브(또는 그에 준하는 자립 블록)라 지지가 없고, 스컬크 정맥만 덩굴처럼 어느 면에나
        // 붙는다 — 기존 {@link SupportKind#WALL_ANY} 를 그대로 쓰고 지지를 잃으면 자기 자신을
        // 떨군다(덩굴 계열 선례). 말린 가스트 1250 도 자립 큐브라 지지가 없다.
        classifyRange(SCULK, SCULK, SupportKind.NONE, AIR);
        classify(SCULK_VEIN, SupportKind.WALL_ANY, SCULK_VEIN);
        classifyRange(SCULK_CATALYST, SCULK_SHRIEKER, SupportKind.NONE, AIR);
        classify(DRIED_GHAST, SupportKind.NONE, DRIED_GHAST);
        // [TRIAL] 트라이얼 스포너·금고 1251~1252 는 바닐라와 같이 완전 큐브라 지지가 없다.
        // 둘 다 드랍이 없으므로 지지 상실 드랍도 AIR 이다(애초에 지지 상실이 없다).
        classifyRange(TRIAL_SPAWNER, VAULT, SupportKind.NONE, AIR);
        // [SULFUR] 유황 블록·광석 두 벌·싹트는 유황·모래는 자립 큐브라 지지가 없다. 싹 3단계와
        // 군집은 바닐라 자수정 싹처럼 어느 면에나 붙으므로 {@link SupportKind#WALL_ANY} 를
        // 쓰고, 지지를 잃으면 <b>자기 자신이 아니라 AIR</b> 을 떨군다 — 바닐라도 미성숙 싹은
        // 파괴 시 아무것도 남기지 않는다(군집만 유황 가루를 주며 그건 채굴 드랍표 몫이다).
        // 분출구는 지형에 박힌 설비라 지지가 없다.
        classify(SULFUR_BLOCK, SupportKind.NONE, SULFUR_BLOCK);
        for (int id = CHISELED_SULFUR; id <= SULFUR_WALL; id++) {
            classify(id, SupportKind.NONE, id);
        }
        // Directional support is stateful and shared with pointed dripstone in P6Rules.
        classify(SULFUR_SPIKE, SupportKind.NONE, SULFUR_SPIKE);
        for (int id = CHISELED_CINNABAR; id <= POLISHED_CINNABAR_WALL; id++) {
            classify(id, SupportKind.NONE, id);
        }
        // [BRIMSTONE] 마그마 블록과 황린 갑각 트로피는 둘 다 자립 큐브라 지지가 없다.
        // 바닐라 magma_block 은 중력 블록도 지지 의존 블록도 아니다.
        classifyRange(MAGMA, BRIMSTONE_CARAPACE_TROPHY, SupportKind.NONE, AIR);
        // [COOKING] 케이크는 바닐라 CakeBlock 처럼 아래 고체를 요구한다(반 블록 위에도 놓인다).
        // 지지를 잃으면 <b>자기 자신이 아니라 AIR</b> 을 떨군다 — 놓인 케이크는 어떤 경로로도
        // 아이템으로 돌아오지 않는다는 바닐라 규칙(실크 터치도 회수 불가)을 여기서도 지킨다.
        classify(CAKE, SupportKind.SOLID_BELOW, AIR);
        // [PROP-MATERIAL] 사슬 3축·랜턴 1336~1339.
        //  · 사슬은 바닐라 ChainBlock 이 SurvivalCheck 를 하지 않아 어느 면도 요구하지 않는다
        //    (공중에 걸린 사슬이 남는 것이 바닐라 동작이다) → SupportKind.NONE.
        //  · 랜턴은 바닐라 LanternBlock 이 지지면을 요구하지만, 이 저장소는 이미 등록된
        //    구리 랜턴(1064)과 **같은 계약**을 쓴다 — 아래 고체를 잃으면 자신을 드랍하는
        //    SOLID_BELOW 다. 두 랜턴이 다른 지지 규칙을 가지면 같은 물건이 아니게 된다.
        classifyRange(CHAIN, CHAIN_Z, SupportKind.NONE, CHAIN);
        classify(LANTERN, SupportKind.SOLID_BELOW, LANTERN);
        // [SPRING-TO-LIFE] 1.21.5 자연 요소. 여섯 종 모두 지지면이 사라지면 자신을 드랍한다
        // (바닐라도 "attachment block 이 사라지면 아이템으로 떨어진다" [B]).
        //  · 야생화·반딧불 수풀·마른 풀 두 종 = 흙 계열 위에만 놓인다 → DIRT_OR_GRASS_BELOW
        //    (마른 풀의 모래·테라코타 바닥은 SupportRules 가 이 트랙 전용 술어로 넓힌다).
        //  · 낙엽 리터 = "full solid top surface" [B] → SOLID_BELOW.
        //  · 선인장 꽃 = 선인장 꼭대기 또는 윗면 중앙 지지 → SOLID_BELOW 를 쓰고
        //    선인장 갈래는 SupportRules 가 더한다(CACTUS_COLUMN 은 선인장 자신의 성장
        //    규칙이라 꽃에 그대로 쓰면 이웃 고체 금지까지 딸려 온다).
        classify(WILDFLOWERS, SupportKind.DIRT_OR_GRASS_BELOW, WILDFLOWERS);
        classify(LEAF_LITTER, SupportKind.SOLID_BELOW, LEAF_LITTER);
        classify(FIREFLY_BUSH, SupportKind.DIRT_OR_GRASS_BELOW, FIREFLY_BUSH);
        classify(CACTUS_FLOWER, SupportKind.SOLID_BELOW, CACTUS_FLOWER);
        classify(SHORT_DRY_GRASS, SupportKind.DIRT_OR_GRASS_BELOW, SHORT_DRY_GRASS);
        classify(TALL_DRY_GRASS, SupportKind.DIRT_OR_GRASS_BELOW, TALL_DRY_GRASS);

        // [PALE-GARDEN] 창백한 정원 계열 1490~1507. 형상별 계약은 전부 기존 것을 물려받는다.
        //  · 원목·벗긴 원목·잎·판자~울타리문은 지지가 없는 완전 큐브/제작 블록이다.
        //  · 다락문은 참나무 WOOD_TRAPDOOR 와 같이 지지면 없이 남고 자신을 드랍한다.
        //  · 문은 아래 고체 지지가 필요하고 잃으면 자신을 드랍한다(MC DoorBlock).
        //  · 묘목은 흙 계열 위에만 놓인다(다른 수종 묘목과 같은 계약).
        //  · 창백한 이끼 블록은 완전 큐브라 지지가 없다.
        //  · 창백한 이끼 바닥은 부분 높이 카펫 선례(MOSS_CARPET=199)와 같이
        //    SupportKind.NONE 을 쓰되, 자연 산지에서만 얻는 블록이라 드랍은 자기 자신이다.
        //  · 늘어진 창백한 이끼는 천장에 매달리는 식생이라 지지가 위에 있다. 이 저장소의
        //    SupportKind 에 "위쪽 고체" 갈래가 없어 새 갈래를 만들지 않고 NONE 으로 둔다
        //    ([C] divergence — 동굴 덩굴이 이미 쓰는 것과 같은 판단이다). 드랍은 자기 자신.
        classifyRange(PALE_OAK_LOG, STRIPPED_PALE_OAK_LOG_Z, SupportKind.NONE, AIR);
        classifyRange(PALE_OAK_PLANK, PALE_OAK_FENCE_GATE, SupportKind.NONE, AIR);
        classify(PALE_OAK_TRAPDOOR, SupportKind.NONE, PALE_OAK_TRAPDOOR);
        classify(PALE_OAK_DOOR, SupportKind.SOLID_BELOW, PALE_OAK_DOOR);
        classify(PALE_OAK_LEAVES, SupportKind.NONE, AIR);
        classify(PALE_OAK_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, PALE_OAK_SAPLING);
        classify(PALE_MOSS_BLOCK, SupportKind.NONE, AIR);
        classify(PALE_MOSS_CARPET, SupportKind.NONE, PALE_MOSS_CARPET);
        classify(PALE_HANGING_MOSS, SupportKind.NONE, PALE_HANGING_MOSS);

        // [CREAKING] 크리킹 하트 두 ID 와 수지 블록 1508~1510.
        //  · 하트는 원목 사이에 끼는 완전 큐브라 지지가 없다. 실크터치가 없으면 자기 자신이
        //    아니라 수지 덩어리를 떨구므로 드랍 표는 AIR 로 두고 실제 산출은 채굴 드랍 규칙
        //    (InventoryRules 계열)이 정한다 — 잎이 자기 자신이 아니라 묘목을 떨구는 것과
        //    같은 자리다. 활성 쌍둥이도 같은 분류다(겉모습만 다른 한 블록이므로).
        //  · 수지 블록은 평범한 9칸 저장 블록이라 지지가 없고 자기 자신을 떨군다.
        classify(CREAKING_HEART, SupportKind.NONE, AIR);
        classify(CREAKING_HEART_ACTIVE, SupportKind.NONE, AIR);
        classify(RESIN_BLOCK, SupportKind.NONE, AIR);

        // [POPLAR] 26.3 포플러 계열 · 얼룩덜룩한 숲 지표 1530~1555.
        //  · 원목·벗긴 원목·잎·판자~울타리문은 지지가 없는 완전 큐브/제작 블록이다.
        //  · 다락문은 참나무 WOOD_TRAPDOOR 와 같이 지지면 없이 남고 자신을 드랍한다.
        //  · 문은 아래 고체 지지가 필요하고 잃으면 자신을 드랍한다(MC DoorBlock).
        //  · 묘목은 흙 계열 위에만 놓인다.
        //  · 붉은 관목은 흙 계열 위에만 놓이고 지지를 잃으면 <b>자신을 드랍한다</b> —
        //    바닐라도 "언제나 자신을 드랍한다" [B].
        classifyRange(POPLAR_LOG, STRIPPED_POPLAR_LOG_Z, SupportKind.NONE, AIR);
        classifyRange(POPLAR_LEAVES_YELLOW, POPLAR_LEAVES_RED, SupportKind.NONE, AIR);
        classify(POPLAR_SAPLING, SupportKind.DIRT_OR_GRASS_BELOW, POPLAR_SAPLING);
        classify(RED_SHRUB, SupportKind.DIRT_OR_GRASS_BELOW, RED_SHRUB);
        // 버튼은 벽·바닥·천장, 팟말은 바닥·벽 state를 공유하므로
        // 단일 방향 SupportKind로 축소하지 않는다.
        classify(POPLAR_BUTTON, SupportKind.NONE, POPLAR_BUTTON);
        classify(POPLAR_PRESSURE_PLATE, SupportKind.SOLID_BELOW, POPLAR_PRESSURE_PLATE);
        classify(POPLAR_SIGN, SupportKind.NONE, POPLAR_SIGN);
        classify(POPLAR_HANGING_SIGN, SupportKind.NONE, POPLAR_HANGING_SIGN);
        for (int id = POPLAR_WOOD; id <= POPLAR_SHELF; id++) {
            classify(id, SupportKind.NONE, id);
        }
        classify(POTTED_POPLAR_SAPLING, SupportKind.SOLID_BELOW, POPLAR_SAPLING);
        classifyRange(POPLAR_PLANK, POPLAR_FENCE_GATE, SupportKind.NONE, AIR);
        classify(POPLAR_TRAPDOOR, SupportKind.NONE, POPLAR_TRAPDOOR);
        classify(POPLAR_DOOR, SupportKind.SOLID_BELOW, POPLAR_DOOR);
        // [OPENABLE-METAL] 철 문·철 다락문·구리 창살 1370~1379.
        //  · 철 문은 이미 등록된 구리 문(1015~1018·1055~1058)과 **같은 계약**을 쓴다 —
        //    바닐라 DoorBlock 은 아래 칸이 지지면을 잃으면 두 칸이 함께 무너지므로
        //    SOLID_BELOW 이고 드랍은 자기 자신이다. 두 금속 문이 다른 지지 규칙을 가지면
        //    같은 형상 계약이 아니게 된다.
        //  · 철 다락문은 목재 다락문(94)과 같이 지지면을 요구하지 않는다(바닐라 TrapDoorBlock
        //    은 SurvivalCheck 가 없어 공중에 남는다) → SupportKind.NONE, 드랍은 자기 자신.
        //  · 구리 창살은 철창(93)이 이미 들어 있는 classifyRange(SANDSTONE, IRON_BARS, NONE, AIR)
        //    과 같은 계약이다. 다만 그 범위는 드랍이 AIR 인 옛 구간이므로 여기서는 형상 계약만
        //    물려받고 드랍은 자기 자신으로 둔다(제작으로만 얻는 블록이 사라지면 안 된다).
        classify(IRON_DOOR, SupportKind.SOLID_BELOW, IRON_DOOR);
        classify(IRON_TRAPDOOR, SupportKind.NONE, IRON_TRAPDOOR);
        for (int id = COPPER_BARS; id <= WAXED_OXIDIZED_COPPER_BARS; id++) {
            classify(id, SupportKind.NONE, id);
        }
        // [COPPER-CHAIN] 구리 사슬 24종 1430~1453 은 철 사슬(1336~1338)과 같은 계약이다 —
        // 바닐라 ChainBlock 은 SurvivalCheck 를 하지 않아 어느 면도 요구하지 않는다(공중에
        // 걸린 사슬이 남는 것이 바닐라 동작이다). 지지 상실 드랍은 축을 접은 y축 대표 ID 다:
        // 지지가 NONE 이라 실제로는 쓰이지 않지만, 축 ID 를 그대로 두면 "축이 붙은 아이템"
        // 이라는 존재하지 않는 물건을 가리키게 되어 표가 거짓말을 한다.
        for (int id = COPPER_CHAIN; id <= WAXED_OXIDIZED_COPPER_CHAIN_Z; id++) {
            classify(id, SupportKind.NONE, copperChainWithAxis(id, 0));
        }

        // [SHELF-FUNGUS-WOOL-SLAB] 1560~1587.
        //  · 양털 반 블록 16색은 자립 반 블록이라 지지가 없다 — 양털·카펫 구간
        //    classifyRange(WHITE_WOOL, BLACK_CARPET, NONE, AIR) 와 같은 계약이되, 제작으로만
        //    얻는 블록이므로 드랍은 AIR 이 아니라 자기 자신이다.
        //  · 선반버섯은 바닐라가 "옆면이 꽉 찬 아무 블록"에 붙는다고만 적어
        //    {@link SupportKind#WALL_ANY} 를 쓴다(유황 싹·스컬크 정맥 선례). 지지를 잃으면
        //    <b>자기 자신을 드랍한다</b> — 큰 것도 지지 상실 드랍은 1개다(2개는 채굴 드랍표
        //    몫이고, 바닐라도 지지 상실은 블록의 기본 드랍을 쓴다).
        //  · 선반은 벽에 거는 물건처럼 보이지만 바닐라 ShelfBlock 은 지지 검사를 하지 않는다
        //    (공중에 남는다) → SupportKind.NONE, 드랍은 자기 자신.
        for (int id = WHITE_WOOL_SLAB; id <= BLACK_WOOL_SLAB; id++) {
            classify(id, SupportKind.NONE, id);
        }
        classify(SHELF_MUSHROOM, SupportKind.WALL_ANY, SHELF_MUSHROOM);
        for (int id = OAK_SHELF; id <= MANGROVE_SHELF; id++) {
            classify(id, SupportKind.NONE, id);
        }
        classify(PALE_OAK_SHELF, SupportKind.NONE, PALE_OAK_SHELF);

        // [INFESTED-26.3] Both are full self-supporting world blocks and have BlockItems, although
        // survival mining never returns that BlockItem: ordinary mining drops nothing and Silk
        // Touch returns the uninfested host. InventoryRules owns that loot split.
        classify(INFESTED_STONE, SupportKind.NONE, INFESTED_STONE);
        classify(INFESTED_DEEPSLATE, SupportKind.NONE, INFESTED_DEEPSLATE);
        // [SEAGRASS-26.3] The lower half requires the same solid seabed support as ordinary
        // seagrass. Exact upper/lower state and resident source water are carried independently;
        // neither half yields an item merely because support was lost.
        classify(TALL_SEAGRASS, SupportKind.SOLID_BELOW, AIR);
        // [SIMPLE-BLOCK-26.3] Exact half/facing/flower-amount properties live in the feature
        // carrier. This table owns append-only identity, ground support and support-loss output.
        classify(BLUE_ORCHID, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(CLOSED_EYEBLOSSOM, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(OPEN_EYEBLOSSOM, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(LARGE_FERN, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(MELON, SupportKind.NONE, MELON);
        classify(PINK_PETALS, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(SUNFLOWER, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(TALL_GRASS_263, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        // [KELP-26.3] Body and growth tip are distinct block identities but share kelp drops and
        // the established submerged-column support family. The exact live column handles body
        // adjacency; this metadata is the common unsupported-ground fallback.
        classify(KELP_PLANT, SupportKind.SOLID_BELOW, KELP);
        // [TREE-T0-26.3] Forest-flower identities are append-only world blocks. Double-plant
        // half state is retained by the exact feature carrier; support loss never aliases one
        // flower to another item identity.
        classify(LILAC, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(ROSE_BUSH, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(PEONY, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(LILY_OF_THE_VALLEY, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        // [TREE-T3-26.3] The pod is a world block distinct from the cocoa-beans item. Its exact
        // jungle-log-facing survival is retained by the tree feature state adapter; this shared
        // metadata records the horizontal attachment family and the item yielded on support loss.
        classify(COCOA, SupportKind.WALL_ANY, COCOA_BEANS);
        // [TREE-NESTED-26.3] The dripleaf body is generated state, not an inventory item. Its
        // exact same-column survival is retained by the feature/runtime lifecycle, while this
        // catalog prevents it from being rejected as an unknown persisted world identity.
        classify(BIG_DRIPLEAF_STEM, SupportKind.NONE, AIR);
        // Azalea foliage uses ordinary leaf decay/loot through BlockFamilies and InventoryRules.
        // It is not interchangeable with the bush-form AZALEA/FLOWERING_AZALEA identities.
        classify(AZALEA_LEAVES, SupportKind.NONE, AIR);
        classify(FLOWERING_AZALEA_LEAVES, SupportKind.NONE, AIR);
        // [STEP9-FLOWER-26.3] These eight generated small flowers are separate world identities,
        // never aliases of the legacy poppy/dandelion pair. Their authority item/drop integration
        // is outside this identity tranche, so unsupported generated flowers clear to AIR just like
        // BLUE_ORCHID and LILY_OF_THE_VALLEY above.
        classify(ALLIUM, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(AZURE_BLUET, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(RED_TULIP, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(ORANGE_TULIP, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(WHITE_TULIP, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(PINK_TULIP, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(OXEYE_DAISY, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(CORNFLOWER, SupportKind.DIRT_OR_GRASS_BELOW, AIR);

        // [CHEST-FAMILY] 덫 상자와 구리 상자 8종은 모두 자립 컨테이너다. 상수·형상·
        // 컨테이너 규칙만 추가하고 지지 정본에서 빠뜨리면 공통 블록 가드가 이들을 미등록
        // 프로토콜 ID로 거부하므로, append-only 착지 구간 전체를 월드 블록으로 등록한다.
        classify(TRAPPED_CHEST, SupportKind.NONE, TRAPPED_CHEST);
        for (int id = COPPER_CHEST; id <= WAXED_OXIDIZED_COPPER_CHEST; id++) {
            classify(id, SupportKind.NONE, id);
        }

        // [ENDER-SHULKER] 엔더 상자와 셜커 상자 17종도 자립 컨테이너다. 엔더 상자의 드랍
        // 정본은 전리품표 쪽(InventoryRules)이라 여기서는 자기 자신으로 두고 — 지지 정본의
        // 세 번째 인자는 "설치에 쓰는 아이템 ID"이지 채굴 드랍이 아니다 — 실크 터치 유무
        // 갈래는 채굴 경로가 소유한다.
        classify(ENDER_CHEST, SupportKind.NONE, ENDER_CHEST);
        classify(SHULKER_BOX, SupportKind.NONE, SHULKER_BOX);
        for (int id = WHITE_SHULKER_BOX; id <= BLACK_SHULKER_BOX; id++) {
            classify(id, SupportKind.NONE, id);
        }

        // [CORAL-REEF] 산호 어휘 40 종(1880~1919).
        //  · 산호 블록 10 종은 풀 큐브 자립 블록이라 지지가 없다([A] CoralBlock 은 지지
        //    검사를 하지 않는다) — 드랍은 채굴 경로가 실크 터치 유무로 가른다.
        //  · 식물형·바닥 부채 20 종은 바닥 부착이라 {@link SupportKind#SOLID_BELOW} 다
        //    ([A] BaseCoralPlantBlock/BaseCoralFanBlock 의 mayPlaceOn 은 "아래가 꽉 찬 면").
        //    기존 켈프·해초·산호(57~59)와 같은 계약이다.
        for (int id = TUBE_CORAL_BLOCK; id <= DEAD_HORN_CORAL_BLOCK; id++) {
            classify(id, SupportKind.NONE, id);
        }
        for (int id = TUBE_CORAL; id <= DEAD_HORN_CORAL_FAN; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        //  · 벽 부채 10 종은 수평면 부착이다. 방향별 정확한 지지면은 상태 캐리어가
        //    소유하고, ID 메타데이터는 최소 조건인 수평 고체 이웃을 요구한다.
        for (int id = TUBE_CORAL_WALL_FAN; id <= DEAD_HORN_CORAL_WALL_FAN; id++) {
            classify(id, SupportKind.WALL_ANY, id);
        }

        // [CONDUIT] 콘딧은 자립 블록이다 — [A] ConduitBlock 은 지지 검사를 하지 않고
        // 물 속에 떠 있는 것이 정상 설치다(활성 조건은 프레임이지 지지가 아니다).
        // 드랍은 자기 자신이다(전리품표에 특별 갈래가 없다).
        classify(CONDUIT, SupportKind.NONE, CONDUIT);

        // [TURTLE] 거북 알은 모래 위에서만 산다 — [A] TurtleEggBlock.mayPlaceOn 은
        // {@code onSand(level, pos)} 이고 canSurvive 도 같은 판정이라 지지를 잃으면
        // 부서진다. 이 저장소의 지지 어휘에는 "모래 계열 아래" 갈래가 없으므로
        // {@link SupportKind#SOLID_BELOW} 로 두고 <b>모래 여부는 설치 규칙</b>
        // ({@link com.gameexpert.engine.TurtleEggRules#canPlaceOn})이 따로 본다 —
        // 지지 표는 청크 갱신 때마다 도는 핫 경로라 종류 하나를 위해 갈래를 늘리지 않는다.
        // 드랍은 실크 터치 전용이라 여기 자기 자신을 적어도 채굴 경로가 다시 가른다
        // (전리품표 갈래의 정본은 TurtleEggRules 다).
        classify(TURTLE_EGG, SupportKind.SOLID_BELOW, TURTLE_EGG);

        // [ARCHAEOLOGY] 의심스러운 모래 · 자갈은 자립 블록이다 — [A] BrushableBlock 은
        // 지지 검사를 하지 않는다(모래 · 자갈과 같이 중력만 받는다). 지지 상실 드랍은
        // 자기 자신으로 적지만 채굴 경로가 다시 가른다 — [A]
        // loot_table/blocks/suspicious_sand.json 에는 pools 가 아예 없어 <b>부수면 아무것도
        // 나오지 않는다</b>(파묻힌 것이 함께 사라진다). 그 갈래의 정본은
        // {@link com.gameexpert.engine.ArchaeologyRules} 다.
        classify(SUSPICIOUS_SAND, SupportKind.NONE, SUSPICIOUS_SAND);
        classify(SUSPICIOUS_GRAVEL, SupportKind.NONE, SUSPICIOUS_GRAVEL);

        // [ARCHAEOLOGY] 장식 항아리 8 종도 자립 블록이다 — [A] DecoratedPotBlock 은 지지
        // 검사를 하지 않는다. 드랍은 실크 터치 유무로 갈리므로(항아리 자신 vs 재료 넷)
        // 여기 자기 자신을 적어 두고 채굴 경로가 ArchaeologyRules 로 다시 가른다.
        for (int id = DECORATED_POT; id <= SNORT_DECORATED_POT; id++) {
            classify(id, SupportKind.NONE, id);
        }

        classify(LILY_PAD, SupportKind.WATER_BELOW, LILY_PAD);
        classify(WALL_TORCH_N, SupportKind.WALL_NORTH, TORCH);
        classify(WALL_TORCH_E, SupportKind.WALL_EAST, TORCH);
        classify(WALL_TORCH_S, SupportKind.WALL_SOUTH, TORCH);
        classify(WALL_TORCH_W, SupportKind.WALL_WEST, TORCH);

        // CONTRACT §3 순수 아이템 구간. 값은 상수 정본을 그대로 가리키며 구간만 열거한다.
        classifyPureItemRange(FLINT, APPLE);                       // 282~287
        classifyPureItemRange(CHAINMAIL_HELMET, SHEARS);           // 335~344
        classifyPureItemRange(BEETROOT, RABBIT_FOOT);              // 348~357
        classifyPureItemRange(RAW_COPPER, POWDER_SNOW_BUCKET);     // 392~396
        classifyPureItemRange(AMETHYST_SHARD, SLIME_BALL);         // 407~420
        classifyPureItemRange(BOOK, BOOK);                         // 423
        classifyPureItemRange(COMPASS, PAPER);                     // 430~433
        classifyPureItemRange(FISHING_ROD, FISHING_ROD);           // 440
        classifyPureItemRange(OMINOUS_BOTTLE, PUFFERFISH_BUCKET);  // 445~468
        classifyPureItemRange(ELYTRA, FIREWORK_ROCKET_3);          // 469~472
        classifyPureItemRange(COMMEMORATIVE_COIN, DIAMOND_FISHING_ROD); // 477~481
        classifyPureItemRange(NAME_TAG, TRIPWIRE_HOOK);            // 484~488
        classifyPureItemRange(EGG, CARROT_ON_A_STICK);             // 489~490
        classifyPureItemRange(CROSSBOW, CROSSBOW);                 // 501
        classifyPureItemRange(BRICK, BRICK);                       // 765
        classifyPureItemRange(SUGAR, NETHER_WART);                 // 801~815
        classifyPureItemRange(GLOWSTONE_DUST, SPLASH_POTION_HARMING_II); // 816~832
        classifyPureItemRange(PRISMARINE_SHARD, PRISMARINE_CRYSTALS); // 932~933
        classifyPureItemRange(QUARTZ, QUARTZ);                     // 961
        classifyPureItemRange(HONEYCOMB, HONEYCOMB);               // 986
        classifyPureItemRange(ABANDONED_CAMPSITE_MAP, WOODLAND_EXPLORER_MAP);
        classifyPureItemRange(FILLED_MAP, TOTEM_OF_UNDYING);       // 1177~1178
        classifyPureItemRange(ROTTEN_LEATHER, ROTTEN_LEATHER_BOOTS); // 1179~1183
        classifyPureItemRange(TRIAL_KEY, TRIAL_KEY);               // 1253
        classifyPureItemRange(OMINOUS_TRIAL_KEY, OMINOUS_TRIAL_KEY); // 2313
        classifyPureItemRange(POTION_REGENERATION, MUSIC_DISC_CREATOR); // 2314~2330
        classifyPureItemRange(SPECTRAL_ARROW, SPECTRAL_ARROW);     // 2338
        classifyPureItemRange(EXPERIENCE_BOTTLE, EXPERIENCE_BOTTLE); // 2339
        classifyPureItemRange(ARMOR_STAND, TNT_MINECART);          // 2430~2435
        classifyPureItemRange(IRON_NUGGET, COPPER_NUGGET);         // 1340~1341
        classifyPureItemRange(SPOILED_EGG, SPOILED_EGG);           // 1410
        classifyPureItemRange(BLUE_EGG, BROWN_EGG);                // 2173~2174
        classifyPureItemRange(WIND_CHARGE, WIND_CHARGE);          // 2176
        classifyPureItemRange(SULFUR_CUBE_BUCKET, SULFUR_CUBE_BUCKET); // 2177
        // [BRIMSTONE] 마그마 크림 + 화염 저항 물약 네 종. 마그마·트로피(1460~1461)는
        // 월드 블록이라 위 지지 분류가 맡는다.
        classifyPureItemRange(MAGMA_CREAM, SPLASH_POTION_FIRE_RESISTANCE_LONG); // 1462~1466
        // [WAVE-86-97] 신종 사망 드랍의 순수 아이템. 워든의 스컬크 촉매는 월드 블록 1202 라
        // 위 지지 분류가 이미 맡고 있다.
        classifyPureItemRange(BREEZE_ROD, PHANTOM_MEMBRANE);       // 1480~1481
        // [COOKING] 요리 순수 아이템. 케이크(1630)는 월드 블록이라 위 지지 분류가 맡는다.
        classifyPureItemRange(COCOA_BEANS, DRIED_KELP);            // 1631~1636
        // [CHEST-FAMILY] 상자류 재료 순수 아이템. 줄 갈고리는 이미 488 이라 여기 줄이 없다.
        classifyPureItemRange(BLAZE_POWDER, EYE_OF_ENDER);         // 1603~1604
        classifyPureItemRange(SHULKER_SHELL, SHULKER_SHELL);       // 1613
        // [POTION-GAP] 힘·수중 호흡·도약·야간 투시 물약 20종. 네 사슬의 재료가 이미 전부
        // 있어(블레이즈 가루 1603 · 복어 451 · 토끼 발 357 · 황금 당근 409) 이 구간에는
        // 물약만 있다.
        classifyPureItemRange(POTION_STRENGTH, SPLASH_POTION_NIGHT_VISION_LONG); // 1760~1779
        // [HARNESS] 하네스 16색. 월드 블록이 아니라 해피 가스트가 입는 장비 아이템이라
        // 이 구간에는 하네스만 있다(가죽 334 · 유리 10 · 색 양털 561~576 은 이미 있었다).
        classifyPureItemRange(WHITE_HARNESS, BLACK_HARNESS);       // 1790~1805
        // [GOLD-FOOD] 황금·특수 식품의 순수 아이템 셋. 황금 민들레(1733)는 월드 블록이라
        // 위 지지 분류(DIRT_OR_GRASS_BELOW)가 맡는다.
        classifyPureItemRange(ENCHANTED_GOLDEN_APPLE, HONEY_BOTTLE); // 1730~1732
        // [CROP-BERRY] 독 감자. 이 트랙이 만드는 유일한 순수 아이템이다 — 당근·감자·비트·
        // 동굴 덩굴·다시마는 전부 이미 등록되어 있어 새 ID 를 만들지 않는다.
        classifyPureItemRange(POISONOUS_POTATO, POISONOUS_POTATO); // 1700
        // [CREAKING] 수지 덩어리. 이 트랙의 유일한 순수 아이템이다 — 크리킹 하트(1508·1509)와
        // 수지 블록(1510)은 월드 블록이라 위 지지 분류가 맡는다.
        classifyPureItemRange(RESIN_CLUMP, RESIN_CLUMP);           // 1511
        // [DIAMOND-SHIELD] 다이아 방패. 이 트랙의 유일한 ID 다 — 방패(321)와 다이아몬드(286)가
        // 이미 있어 재료를 하나도 신설하지 않았다.
        classifyPureItemRange(DIAMOND_SHIELD, DIAMOND_SHIELD);     // 1858
        // [CONDUIT] 바다의 심장. 이 트랙의 유일한 순수 아이템이다 — 콘딧(1951)은 월드 블록
        // 이라 바로 위 지지 분류가 맡고, 앵무조개 껍데기(486)·프리즈머린(934~945)은 이미
        // 등록되어 있어 재료를 하나도 신설하지 않았다.
        classifyPureItemRange(HEART_OF_THE_SEA, HEART_OF_THE_SEA); // 1950
        // [TRIDENT] 삼지창. 이 트랙의 유일한 ID 이자 유일한 순수 아이템이다 — 바닐라에
        // 제작법이 없어 재료를 하나도 신설하지 않았다.
        classifyPureItemRange(TRIDENT, TRIDENT);                   // 1920
        // [MACE] 철퇴. 이 트랙의 유일한 ID 이자 순수 아이템이다(PlayerInventory.isRegisteredItemType 과 짝).
        classifyPureItemRange(MACE, MACE);                         // 2370
        // [TURTLE] 거북 등딱지 조각 · 거북 등껍질. 거북 알(1930)은 월드 블록이라 위 지지
        // 분류가 맡는다. 재료를 하나도 신설하지 않았다 — 등껍질은 조각 다섯으로만 만든다.
        classifyPureItemRange(TURTLE_SCUTE, TURTLE_SHELL);         // 1931~1932
        // [SPEAR] 창 여섯 티어. 전부 순수 아이템이고 재료를 하나도 신설하지 않았다.
        // 네더라이트 창(2006)은 예약만 하고 등록하지 않으므로 구간 끝은 DIAMOND_SPEAR 다.
        // 이 줄과 PlayerInventory.isRegisteredItemType 의 창 절은 **반드시 짝**이다 —
        // 한쪽만 있으면 창을 든 채 재접속할 때 인벤토리 복원이 예외로 터진다
        // (삼지창 1920 이 실제로 그 짝을 빠뜨린 선례다).
        classifyPureItemRange(WOODEN_SPEAR, DIAMOND_SPEAR);        // 2000~2005
        // [SHIELD-FAMILY] 방패 네 티어. 전부 순수 아이템이고 재료를 하나도 신설하지 않았다.
        // 철 방패(321)와 다이아 방패(1858)는 이미 등록돼 있어 이 구간에 넣지 않는다 — 같은
        // 물건에 ID 를 둘 주지 않는 것이 이 트랙의 첫 계약이다.
        // 이 줄과 PlayerInventory.isRegisteredItemType 의 방패 절은 **반드시 짝**이다 —
        // 한쪽만 있으면 방패를 든 채 재접속할 때 인벤토리 복원이 예외로 터진다
        // (삼지창 1920 이 실제로 그 짝을 빠뜨린 선례다).
        classifyPureItemRange(LEATHER_SHIELD, GOLD_SHIELD);        // 2020~2023
        // [NAUTILUS-MOUNT] 이 줄은 PlayerInventory.isRegisteredItemType 의
        // Blocks.isNautilusArmorTier(id) 절과 **짝**이다 — 한쪽만 있으면 갑옷을 든 채
        // 재접속할 때 인벤토리 복원이 예외로 터진다(삼지창 1920 선례).
        classifyPureItemRange(COPPER_NAUTILUS_ARMOR, NETHERITE_NAUTILUS_ARMOR); // 2040~2044
        classify(COPPER_TORCH, SupportKind.SOLID_BELOW, COPPER_TORCH);
        classify(COPPER_WALL_TORCH_N, SupportKind.WALL_NORTH, COPPER_TORCH);
        classify(COPPER_WALL_TORCH_E, SupportKind.WALL_EAST, COPPER_TORCH);
        classify(COPPER_WALL_TORCH_S, SupportKind.WALL_SOUTH, COPPER_TORCH);
        classify(COPPER_WALL_TORCH_W, SupportKind.WALL_WEST, COPPER_TORCH);
        for (int id = EXPOSED_COPPER_LANTERN; id <= WAXED_OXIDIZED_COPPER_LANTERN; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        for (int id = EXPOSED_LIGHTNING_ROD; id <= WAXED_OXIDIZED_LIGHTNING_ROD; id++) {
            classify(id, SupportKind.NONE, id);
        }
        for (int id = COPPER_GOLEM_STATUE; id <= WAXED_OXIDIZED_COPPER_GOLEM_STATUE; id++) {
            classify(id, SupportKind.NONE, id);
        }
        classifyRange(NETHERRACK, NETHERITE_BLOCK, SupportKind.NONE, AIR);
        classify(FROGSPAWN, SupportKind.WATER_BELOW, FROGSPAWN);
        classify(SNIFFER_EGG, SupportKind.SOLID_BELOW, SNIFFER_EGG);
        // [SNIFFER] 두 작물은 밀처럼 경작지 위에서만 산다(정적판 StandaloneBlockRules 와 같은 값).
        classify(TORCHFLOWER_CROP, SupportKind.FARMLAND_BELOW, AIR);
        classify(TORCHFLOWER, SupportKind.DIRT_OR_GRASS_BELOW, AIR);
        classify(PITCHER_CROP, SupportKind.FARMLAND_BELOW, AIR);
        // [PITCHER] 아래 반은 흙 계열·경작지(VegetationBlock.mayPlaceOn), 윗 반은 아래 반 위다 — 두 반 판정은
        // SupportRules 가 PitcherRules 로 가로챈다. 지지 상실 드랍은 아래 반의 전리품(자기 자신)이다.
        classify(PITCHER_PLANT, SupportKind.DIRT_OR_GRASS_BELOW, PITCHER_PLANT);
        classifyRange(OCHRE_FROGLIGHT, HONEYCOMB_BLOCK, SupportKind.NONE, AIR);
        classifyRange(TRIPWIRE_HOOK_BLOCK, STICKY_PISTON, SupportKind.NONE, AIR);
        classify(REDSTONE_TORCH, SupportKind.SOLID_BELOW, REDSTONE_TORCH);
        classifyRange(INFESTED_STONE_BRICKS, INFESTED_MOSSY_STONE_BRICKS,
                SupportKind.NONE, AIR);
        classify(WATER_CAULDRON, SupportKind.NONE, CAULDRON);
        classify(POTTED_CACTUS, SupportKind.SOLID_BELOW, CACTUS);
        classify(OAK_WALL_SIGN, SupportKind.WALL_ANY, OAK_WALL_SIGN);
        classifyPureItemRange(COPPER_PICKAXE, AXOLOTL_BUCKET); // 2087~2121
        classifyRange(ANVIL, DAMAGED_ANVIL, SupportKind.NONE, AIR);
        classify(TINTED_GLASS, SupportKind.NONE, AIR);
        classify(GLOWSTONE, SupportKind.NONE, AIR);
        classify(RAFFLESIA, SupportKind.DIRT_OR_GRASS_BELOW, RAFFLESIA);
        for (int id = WHITE_BANNER; id <= BLACK_BANNER; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        classifyPureItemRange(WRITABLE_BOOK, WRITTEN_BOOK);
        // [ARCHAEOLOGY] 해안판 도자기 조각 7종과 사막판 6종. 장식 항아리 8종(1970~1977)과 의심 블록 둘
        // (1960~1961)은 월드 블록이라 위 지지 분류가 맡는다. 붓(447)은 이미 등록돼 있어
        // 재료를 하나도 신설하지 않았다. 이 줄과 PlayerInventory.isRegisteredItemType 의
        // 조각 절은 **반드시 짝**이다.
        classifyPureItemRange(ANGLER_POTTERY_SHERD, SNORT_POTTERY_SHERD); // 1962~1968
        classifyPureItemRange(ARCHER_POTTERY_SHERD, BREWER_POTTERY_SHERD); // 2219~2224
        classify(STONE_PRESSURE_PLATE, SupportKind.SOLID_BELOW, STONE_PRESSURE_PLATE);
        classify(POTTED_RED_MUSHROOM, SupportKind.SOLID_BELOW, MUSHROOM_RED);
        classifyPureItemRange(2230, EYE_ARMOR_TRIM_SMITHING_TEMPLATE); // Rails 2227..2229 are now world blocks.
        classify(STONE_BUTTON, SupportKind.WALL_ANY, STONE_BUTTON);
        classify(END_PORTAL_FRAME, SupportKind.NONE, END_PORTAL_FRAME);
        classify(END_PORTAL, SupportKind.NONE, AIR);
        for (int id = RED_CANDLE; id <= BROWN_CANDLE; id++) {
            classify(id, SupportKind.SOLID_BELOW, id);
        }
        classify(SPRUCE_HANGING_SIGN, SupportKind.NONE, SPRUCE_HANGING_SIGN);
        classify(OAK_HANGING_SIGN, SupportKind.NONE, OAK_HANGING_SIGN);
        classify(FLOWER_POT, SupportKind.SOLID_BELOW, FLOWER_POT);
        classifyPureItemRange(BURN_POTTERY_SHERD, MUSIC_DISC_RELIC); // 2248~2259
        classify(BARRIER, SupportKind.NONE, AIR);
        classify(CRYING_OBSIDIAN, SupportKind.NONE, AIR);
        classify(BAMBOO_FENCE, SupportKind.NONE, BAMBOO_FENCE);
        classify(WHITE_WOOL_STAIRS, SupportKind.NONE, WHITE_WOOL_STAIRS);
        for (int id = ORANGE_WOOL_STAIRS; id <= BLACK_CONCRETE_SLAB; id++) classify(id, SupportKind.NONE, id);
        classify(WHITE_WALL_BANNER, SupportKind.WALL_ANY, WHITE_WALL_BANNER);
        classify(CANDLE, SupportKind.SOLID_BELOW, CANDLE);
        classify(MANGROVE_WOOD, SupportKind.NONE, MANGROVE_WOOD);
        classify(POTTED_DEAD_BUSH, SupportKind.SOLID_BELOW, DEAD_BUSH);
        classify(HOPPER, SupportKind.NONE, HOPPER);
        classify(OAK_BUTTON, SupportKind.WALL_ANY, OAK_BUTTON);
        classify(OAK_SLAB, SupportKind.NONE, OAK_SLAB);
        classify(OAK_PRESSURE_PLATE, SupportKind.SOLID_BELOW, OAK_PRESSURE_PLATE);
        classify(POTTED_RED_TULIP, SupportKind.SOLID_BELOW, RED_TULIP);
        classify(ACACIA_WOOD, SupportKind.NONE, AIR);
        classify(BELL, SupportKind.NONE, BELL);
        classify(BROWN_WALL_BANNER, SupportKind.WALL_ANY, BROWN_WALL_BANNER);
        classify(MELON_STEM, SupportKind.FARMLAND_BELOW, AIR);
        classify(STRIPPED_OAK_WOOD, SupportKind.NONE, AIR);
        classify(STRIPPED_SPRUCE_WOOD, SupportKind.NONE, AIR);
        classifyPureItemRange(FLOW_POTTERY_SHERD, SCRAPE_POTTERY_SHERD);
        classify(LODESTONE, SupportKind.NONE, AIR);
        classify(ABYSS_STONE, SupportKind.NONE, AIR);
        classify(FLESH_HEART, SupportKind.NONE, HEART_CORE);
        classify(HEART_CORE, SupportKind.NONE, HEART_CORE);
        classify(FLESH_BLOCK, SupportKind.NONE, AIR);
        classifyPureItemRange(MYSTERY_FLESH, MYSTERY_FLESH);
        classifyPureItemRange(FLESH_FIBER, FLESH_FIBER);
        for (int id = FLESH_ARTERY; id <= FLESH_NECROSIS; id++) classify(id, SupportKind.NONE, AIR);
        classifyPureItemRange(FLESH_CLOT_SAC, FLESH_MEMBRANE);
        classifyPureItemRange(FLESH_BONE_PLATE, FLESH_BONE_BUNDLE);
        classify(FLESH_ANCHOR, SupportKind.NONE, AIR);
        classify(FLESH_LARGE_COCOON, SupportKind.NONE, AIR);
        classify(FLESH_COCOON, SupportKind.NONE, AIR);
        classify(FLESH_FAT_LAMP, SupportKind.NONE, FLESH_FAT_LAMP);
        classify(FLESH_RELIQUARY, SupportKind.NONE, FLESH_RELIQUARY);
        classifyPureItemRange(FLESH_DETECTOR, FLESH_DETECTOR);
        classifyPureItemRange(FLESH_BANDAGE, FLESH_BANDAGE);
        classify(FLESH_PULSE_LAMP, SupportKind.NONE, FLESH_PULSE_LAMP);
        classifyPureItemRange(FLESH_BONE_SPEAR, FLESH_BONE_SPEAR);
        classifyPureItemRange(FLESH_BONE_CHESTPLATE, FLESH_BONE_CHESTPLATE);
        classifyPureItemRange(FLESH_HOOK_BLADE, FLESH_HOOKED_SPEAR);
        // [VOID-END] 돌·보라 계열은 지지 없는 블록이다. 엔드 막대는 지지를 잃으면 자기 자신을 떨군다.
        // 후렴 식물의 지지 상실 드랍은 50% 확률이라 이 결정적 표가 아니라
        // VoidEndBlockRules.chorusPlantSupportLossDrop(시드·좌표 결정적 난수)이 정한다. 후렴 꽃은
        // loot_table/blocks/chorus_flower 의 entity_properties(this) 조건 때문에 개체 없이 부서지면
        // (지지 상실) 아무것도 떨구지 않는다.
        classifyRange(END_STONE, END_STONE_BRICK_WALL, SupportKind.NONE, AIR);
        classify(CHORUS_PLANT, SupportKind.NONE, CHORUS_FRUIT);
        classify(CHORUS_FLOWER, SupportKind.NONE, AIR);
        classify(END_ROD, SupportKind.NONE, END_ROD);
        classifyPureItemRange(POPPED_CHORUS_FRUIT, POPPED_CHORUS_FRUIT);
        classify(END_GATEWAY, SupportKind.NONE, AIR);
        // [END-CITY] 아이템 액자는 순수 아이템(개체로 걸린다), 드래곤 머리는 지지 조건이 없고, 자홍색 벽 현수막은
        // 흰색/갈색 벽 현수막과 같은 벽 지지이며 지지를 잃으면 자홍색 현수막을 떨군다.
        classifyPureItemRange(ITEM_FRAME, ITEM_FRAME);
        classify(DRAGON_HEAD, SupportKind.NONE, AIR);
        classify(MAGENTA_WALL_BANNER, SupportKind.WALL_ANY, MAGENTA_BANNER);
        // [DRAGON] 드래곤 알은 지지 요구가 없는 중력 블록이다(FallingBlock — SupportRules.isGravityBlock).
        classify(DRAGON_EGG, SupportKind.NONE, AIR);
        classifyPureItemRange(END_CRYSTAL, DRAGON_BREATH);
        classifyPureItemRange(POTION_STRONG_HEALING, POTION_STRONG_HEALING);
        // [TRIAL-GAP] 에메랄드 블록은 풀 큐브, 무거운 핵은 바닥에 놓인 4..12 × 0..8 기둥이다. 둘 다
        // 지지 요구가 없고(바닐라 Block · HeavyCoreBlock 에 canSurvive 가 없다) 지지 상실 드랍도 없다.
        classify(EMERALD_BLOCK, SupportKind.NONE, AIR);
        classify(HEAVY_CORE, SupportKind.NONE, AIR);
        // [FROST-SOUL] 셋 다 지지 없는 블록이다. 서리 얼음은 전리품 풀이 없어 AIR 다.
        classify(FROSTED_ICE, SupportKind.NONE, AIR);
        classify(SOUL_SAND, SupportKind.NONE, AIR);
        classify(SOUL_SOIL, SupportKind.NONE, AIR);
        // [CONTAINER-MENUS] Dropper and crafter: free-standing full blocks like the dispenser.
        classify(DROPPER, SupportKind.NONE, AIR);
        classify(CRAFTER, SupportKind.NONE, AIR);
        // [UTILITY] 신호기·주크박스·슬라임 블록은 지지 요구가 없는 블록이다(바닐라 canSurvive 없음).
        // 셋 다 부서지면 자기 자신을 떨구지만 지지 상실은 일어나지 않으므로 지지 상실 드랍은 AIR 다.
        classify(BEACON, SupportKind.NONE, AIR);
        classify(JUKEBOX, SupportKind.NONE, AIR);
        classify(SLIME_BLOCK, SupportKind.NONE, AIR);
        classifyPureItemRange(NETHER_STAR, NETHER_STAR); // 2383 (2384 는 REMOVED_UTILITY_2384)
        classifyPureItemRange(GHAST_TEAR, WARD_ARMOR_TRIM_SMITHING_TEMPLATE); // 2385~2416
        // 흑암석 계열은 지지 요구 없는 풀 큐브다.
        classify(BLACKSTONE, SupportKind.NONE, AIR);
        classify(POLISHED_BLACKSTONE, SupportKind.NONE, AIR);
        classify(POLISHED_BLACKSTONE_BRICKS, SupportKind.NONE, AIR);
        // [REDSTONE] Engine-owned supports are stateful and use its ordered neighbor queue.
        classifyRange(REDSTONE_BLOCK, NOTE_BLOCK, SupportKind.NONE, AIR);
        for (int id : new int[]{ACTIVATOR_RAIL, DETECTOR_RAIL, POWERED_RAIL, REDSTONE_WIRE, REDSTONE_TORCH,
                LEVER, REPEATER, STICKY_PISTON, STONE_BUTTON, OAK_BUTTON, POPLAR_BUTTON,
                STONE_PRESSURE_PLATE, OAK_PRESSURE_PLATE, POPLAR_PRESSURE_PLATE, TRIPWIRE, TRIPWIRE_HOOK_BLOCK}) {
            classify(id, SupportKind.NONE, AIR);
        }

    }

    /** 알 수 없는 ID에는 {@link SupportKind#UNSPECIFIED} 메타데이터를 반환한다. */
    public static SupportMetadata supportMetadata(int blockId) {
        if (blockId < 0 || blockId >= SUPPORT_BY_BLOCK_ID.length) {
            return UNSPECIFIED_SUPPORT;
        }
        return SUPPORT_BY_BLOCK_ID[blockId];
    }

    /** 명시적으로 등록된 월드 블록 ID인가. 아이템 및 아직 정의되지 않은 ID는 {@code false}다. */
    public static boolean isWorldBlockId(int blockId) {
        return supportMetadata(blockId).supportKind() != SupportKind.UNSPECIFIED;
    }

    /**
     * 명시적으로 등록된 순수 아이템 ID인가. 월드 블록과 아직 배정되지 않은 ID는 {@code false}다.
     * 지지 메타데이터를 가지지 않는 이유가 "아이템이라서"임을 코드에서 증명한다.
     */
    public static boolean isPureItemId(int id) {
        return id >= 0 && id < PURE_ITEM_BY_ID.length && PURE_ITEM_BY_ID[id];
    }

    /** 가드 테스트와 진단용 정본 월드 블록 ID 목록. */
    public static int[] worldBlockIds() {
        int count = 0;
        for (SupportMetadata metadata : SUPPORT_BY_BLOCK_ID) {
            if (metadata.supportKind() != SupportKind.UNSPECIFIED) {
                count++;
            }
        }
        int[] ids = new int[count];
        int index = 0;
        for (int id = 0; id < SUPPORT_BY_BLOCK_ID.length; id++) {
            if (isWorldBlockId(id)) {
                ids[index++] = id;
            }
        }
        return ids;
    }

    private static void classifyRange(int firstId, int lastId, SupportKind supportKind, int supportLossDrop) {
        for (int id = firstId; id <= lastId; id++) {
            classify(id, supportKind, supportLossDrop);
        }
    }

    private static void classifyPureItemRange(int firstId, int lastId) {
        for (int id = firstId; id <= lastId; id++) {
            if (id > PROTOCOL_ID_HIGH_WATER) {
                throw new IllegalStateException("pure item ID exceeds the protocol high-water mark: " + id);
            }
            if (isWorldBlockId(id)) {
                throw new IllegalStateException("ID is registered as both world block and item: " + id);
            }
            PURE_ITEM_BY_ID[id] = true;
        }
    }

    private static void classify(int blockId, SupportKind supportKind, int supportLossDrop) {
        SUPPORT_BY_BLOCK_ID[blockId] = new SupportMetadata(supportKind, supportLossDrop);
    }

    // ── 목재 계열 형상군 판정 ───────────────────────────────────
    // 종별 목재 세트가 들어오면서 "이 ID 가 문인가/울타리인가"를 묻는 자리가 여러 패키지에
    // 흩어졌다. 값 비교를 각자 복제하면 한 사본만 갱신되어 보이지 않는 벽이나 열리지 않는 문이
    // 생기므로, 형상군 판정은 ID 정본인 이 클래스 한 곳에서만 정의한다.

    /** 참나무 총칭 판자(7) 또는 종별 판자(601~607). */
    public static boolean isPlankBlock(int id) {
        return id == PLANK || (id >= BIRCH_PLANK && id <= MANGROVE_PLANK)
                || id == POPLAR_PLANK
                // [PALE-GARDEN] 창백한 판자(1496).
                || id == PALE_OAK_PLANK;
    }

    /** 목재 계단(81) 또는 종별 목재 계단(608~614). 석재 계단은 포함하지 않는다. */
    public static boolean isWoodStairs(int id) {
        return id == WOOD_STAIRS || (id >= BIRCH_STAIRS && id <= MANGROVE_STAIRS)
                || id == POPLAR_STAIRS
                || id == PALE_OAK_STAIRS;
    }

    /** 기존 목재 반 블록(27), exact 참나무 반 블록 또는 종별 목재 반 블록. */
    public static boolean isWoodSlab(int id) {
        return id == PLANK_SLAB || id == OAK_SLAB || (id >= BIRCH_SLAB && id <= MANGROVE_SLAB)
                || id == POPLAR_SLAB
                || id == PALE_OAK_SLAB;
    }

    /** 울타리(90) 또는 종별 울타리(622~628) 또는 포플러·창백한·대나무 울타리. */
    public static boolean isFence(int id) {
        return id == WOOD_FENCE || (id >= BIRCH_FENCE && id <= MANGROVE_FENCE)
                || id == POPLAR_FENCE
                || id == PALE_OAK_FENCE
                || id == BAMBOO_FENCE;
    }

    /** 울타리 문(91) 또는 종별 울타리 문(629~635). */
    public static boolean isFenceGate(int id) {
        return id == WOOD_FENCE_GATE || (id >= BIRCH_FENCE_GATE && id <= MANGROVE_FENCE_GATE)
                || id == POPLAR_FENCE_GATE
                || id == PALE_OAK_FENCE_GATE;
    }

    /**
     * 다락문(94) 또는 종별 다락문(636~642) 또는 [COPPER] 구리 다락문 8종
     * (1019~1022 · 밀랍 1059~1062). 구리 다락문은 바닐라에서도 목재 다락문과 같은
     * {@code TrapDoorBlock} 이라 형상·개폐 계약을 그대로 물려받는다.
     */
    public static boolean isTrapdoor(int id) {
        return id == WOOD_TRAPDOOR || (id >= BIRCH_TRAPDOOR && id <= MANGROVE_TRAPDOOR)
                || id == POPLAR_TRAPDOOR
                // [PALE-GARDEN] 창백한 다락문(1501).
                || id == PALE_OAK_TRAPDOOR
                || (id >= COPPER_TRAPDOOR && id <= OXIDIZED_COPPER_TRAPDOOR)
                || (id >= WAXED_COPPER_TRAPDOOR && id <= WAXED_OXIDIZED_COPPER_TRAPDOOR)
                // [OPENABLE-METAL] 철 다락문(1371)도 바닐라에서 같은 TrapDoorBlock 이다.
                || id == IRON_TRAPDOOR;
    }

    /**
     * 문(29) 또는 종별 문(643~649) 또는 [COPPER] 구리 문 8종
     * (1015~1018 · 밀랍 1055~1058). 구리 문도 바닐라에서 같은 {@code DoorBlock} 이다.
     */
    public static boolean isDoor(int id) {
        return id == DOOR_CLOSED || (id >= BIRCH_DOOR && id <= MANGROVE_DOOR)
                || id == POPLAR_DOOR
                // [PALE-GARDEN] 창백한 문(1502).
                || id == PALE_OAK_DOOR
                || (id >= COPPER_DOOR && id <= OXIDIZED_COPPER_DOOR)
                || (id >= WAXED_COPPER_DOOR && id <= WAXED_OXIDIZED_COPPER_DOOR)
                // [OPENABLE-METAL] 철 문(1370)도 바닐라에서 같은 DoorBlock 이다. 개폐 가능
                // 여부만 갈리는데(레드스톤 전용) 이 저장소는 우클릭 개폐로 divergence 한다.
                || id == IRON_DOOR;
    }

    /**
     * [BED-COLOR] 침대인가. 총칭 {@code BED=32} 와 색 침대 851~866 과 건초 침대 1281 을
     * 한 판정으로 묶는다.
     *
     * <p>바닐라의 {@code block instanceof BedBlock} 에 해당한다. 수면·개인 리스폰 지점·
     * 마을 침대 집계(주민 번식 조건·좀비 주민 치료 가속)는 전부 색과 무관해야 하므로,
     * 모든 호출부가 값 비교를 복제하지 않고 이 한 판정만 쓴다.
     *
     * <p>[FURNITURE-26.3] 건초 침대도 여기 들어간다 — 2칸 머리/발 구조·형상·충돌·지지·쌍
     * 판정이 침대와 완전히 같기 때문이다. 갈라지는 세 지점(리스폰 미설정 · 수면 후 자멸 ·
     * 경도 0.2)만 {@link #isStrawBed(int)} 를 따로 본다.
     */
    public static boolean isBed(int id) {
        return id == BED || (id >= WHITE_BED && id <= BLACK_BED) || id == STRAW_BED;
    }

    /** 종별 목재 가공 계열 전체 구간(601~649). 참나무 총칭 세트는 포함하지 않는다. */
    public static boolean isSpeciesWoodBuildingBlock(int id) {
        return (id >= BIRCH_PLANK && id <= MANGROVE_DOOR)
                || isPoplarBuildingBlock(id)
                || isPaleOakBuildingBlock(id);
    }

    // ── [STRIPPED-LOG] 벗긴 원목 구간 판정/분해 ─────────────────
    // 판정은 구간 하나뿐이고 축·수종 분해도 나눗셈 하나다. 렌더·물리·양 권위가 값 비교를
    // 복제하지 않도록 ID 정본인 이 클래스만 정의한다(client/src/world/blocks.ts 사본과 동치).

    /** 벗긴 원목 24종(871~894)인가. 축 변형을 모두 포함한다. */
    public static boolean isStrippedLog(int id) {
        return id >= STRIPPED_OAK_LOG && id <= STRIPPED_MANGROVE_LOG_Z;
    }

    /** 벗긴 원목의 축(0=Y, 1=X, 2=Z). 벗긴 원목이 아니면 {@code -1}. */
    public static int strippedLogAxis(int id) {
        return isStrippedLog(id) ? (id - STRIPPED_OAK_LOG) % 3 : -1;
    }

    /**
     * 벗긴 원목의 수종 인덱스(0..7, {@link #LOG_BY_WOOD_SPECIES} 와 같은 순서).
     * 벗긴 원목이 아니면 {@code -1}.
     */
    public static int strippedLogSpecies(int id) {
        return isStrippedLog(id) ? (id - STRIPPED_OAK_LOG) / 3 : -1;
    }

    /** 벗긴 원목의 인벤토리 아이템 ID(축 변형은 y축 대표 ID 로 접힌다). */
    public static int strippedLogItem(int id) {
        return isStrippedLog(id) ? STRIPPED_OAK_LOG + strippedLogSpecies(id) * 3 : AIR;
    }

    /** 수종 인덱스 + 축 → 벗긴 원목 ID. */
    public static int strippedLog(int species, int axis) {
        return STRIPPED_LOG_BY_SPECIES_AND_AXIS[species * 3 + axis];
    }

    /**
     * 도끼 우클릭으로 통나무를 벗긴 결과(MC {@code AxeItem} → {@code STRIPPABLES}).
     * 바닐라와 같이 <b>축을 보존</b>한다 — y축 통나무는 y축 벗긴 원목이 되고, 가로 통나무는
     * 그 축 그대로 남는다. 벗길 수 없는 블록(이미 벗긴 원목 포함)이면 입력 ID 를 그대로 돌려준다.
     *
     * <p>참나무·자작나무는 축이 별도 ID 에 있고 그 밖 여섯 수종은 축이 state 에 있으므로
     * ({@link com.gameexpert.engine.BlockFamilies#isStateOrientedWoodLog(int)}) 축 원천이
     * 두 가지다. 이 메서드가 그 차이를 한 곳에서 흡수해 두 권위가 같은 답을 낸다.
     *
     * @param state 블록 state 바이트. 축이 state 에 있는 수종에서만 하위 2비트를 읽는다.
     */
    public static int strippedLogFor(int blockId, int state) {
        // [POPLAR] 포플러는 여덟 수종 표 밖이라 표 조회보다 먼저 자기 갈래를 본다.
        // 축은 여기서도 보존되고(별도 ID), 이미 벗긴 원목이면 −1 이라 입력 그대로 남는다.
        int poplar = strippedPoplarLogFor(blockId);
        if (poplar >= 0) return poplar;
        // [PALE-GARDEN] 창백한 참나무도 같은 이유로 표 밖이라 자기 갈래를 먼저 본다.
        int paleOak = strippedPaleOakLogFor(blockId);
        if (paleOak >= 0) return paleOak;
        if (blockId == LOG) return strippedLog(0, 0);
        if (blockId == LOG_X) return strippedLog(0, 1);
        if (blockId == LOG_Z) return strippedLog(0, 2);
        if (blockId == BIRCH_LOG) return strippedLog(1, 0);
        if (blockId == BIRCH_LOG_X) return strippedLog(1, 1);
        if (blockId == BIRCH_LOG_Z) return strippedLog(1, 2);
        // 나머지 여섯 수종은 축이 state 하위 2비트에 있다(0=Y, 1=X, 2=Z).
        for (int species = 2; species < LOG_BY_WOOD_SPECIES.length; species++) {
            if (LOG_BY_WOOD_SPECIES[species] != blockId) continue;
            int axis = state & 3;
            return strippedLog(species, axis > 2 ? 0 : axis);
        }
        return blockId;
    }

    /** [QUARTZ] 석영 기둥 3축(964~966)인가. */
    public static boolean isQuartzPillar(int id) {
        return id >= QUARTZ_PILLAR && id <= QUARTZ_PILLAR_Z;
    }

    /** 축(0=Y, 1=X, 2=Z) → 석영 기둥 ID. 통나무 축 변형과 같은 규약이다. */
    public static int quartzPillar(int axis) {
        return QUARTZ_PILLAR_BY_AXIS[axis];
    }

    // ── [COPPER] 산화·밀랍 정본 표 조회 ────────────────────────────
    // 예전에는 "375 이상 378 미만" 같은 하드코딩 범위가 랜덤틱·정적판·클라에 흩어져 있었다.
    // 계열이 열 개로 늘면 그 복제는 반드시 한 사본만 갱신되는 사고가 되므로, 조회는 전부
    // COPPER_OXIDATION_FAMILIES / WAXED_COPPER_FAMILIES 두 표만 읽는다.
    // (client/src/world/blocks.ts 의 같은 이름 함수가 문자 그대로의 사본이다.)

    /**
     * 산화 계열 안의 위치를 {@code index * 4 + age} 로 인코딩한 packed 값. 계열이 아니면 -1.
     * 표 조회를 한 번만 하고 인덱스와 단계를 함께 쓰려는 호출부를 위한 내부 형태다.
     */
    private static int packedCopperSlot(int[][] families, int id) {
        for (int index = 0; index < families.length; index++) {
            int age = id - families[index][0];
            if (age >= 0 && age < COPPER_OXIDATION_STAGES) {
                return index * COPPER_OXIDATION_STAGES + age;
            }
        }
        return -1;
    }

    /** 산화하는(= 밀랍이 아닌) 구리 블록인가. 바닐라 {@code ChangeOverTimeBlock} 판정에 해당한다. */
    public static boolean isOxidizableCopper(int id) {
        return packedCopperSlot(COPPER_OXIDATION_FAMILIES, id) >= 0;
    }

    /** 밀랍 구리 블록인가. 바닐라 {@code HoneycombItem.WAX_OFF_BY_BLOCK} 의 키 집합이다. */
    public static boolean isWaxedCopper(int id) {
        return packedCopperSlot(WAXED_COPPER_FAMILIES, id) >= 0;
    }

    /**
     * 구리 블록의 산화 단계(0=unaffected … 3=oxidized). 밀랍 블록도 그 단계를 돌려주고,
     * 구리 계열이 아니면 -1 이다. 랜덤틱의 이웃 비교는 밀랍을 세지 않으므로
     * {@link #isOxidizableCopper(int)} 로 먼저 거른 뒤 이 값을 읽는다(바닐라도 밀랍 블록은
     * ChangeOverTimeBlock 이 아니라 이웃 집계에 들어가지 않는다).
     */
    public static int copperOxidationAge(int id) {
        int packed = packedCopperSlot(COPPER_OXIDATION_FAMILIES, id);
        if (packed >= 0) return packed % COPPER_OXIDATION_STAGES;
        packed = packedCopperSlot(WAXED_COPPER_FAMILIES, id);
        return packed < 0 ? -1 : packed % COPPER_OXIDATION_STAGES;
    }

    /**
     * 한 단계 더 산화한 블록 ID. 이미 최종 단계이거나 산화 대상이 아니면 -1.
     * 계열이 연속 4 ID 라 결과는 언제나 {@code id + 1} 이고, 이 함수는 그 산술이 계열 경계를
     * 넘지 않는지를 표로 확인하는 자리다(예전 375~378 하드코딩의 일반화).
     */
    public static int nextOxidationStage(int id) {
        int packed = packedCopperSlot(COPPER_OXIDATION_FAMILIES, id);
        if (packed < 0) return -1;
        return packed % COPPER_OXIDATION_STAGES == COPPER_OXIDATION_STAGES - 1 ? -1 : id + 1;
    }

    /**
     * 한 단계 덜 산화한 블록 ID(도끼 긁기). unaffected 이거나 산화 대상이 아니면 -1.
     * 바닐라 {@code WeatheringCopper.getPrevious} 에 해당하며, 밀랍 블록은 여기서 -1 이다
     * (밀랍은 긁으면 산화가 아니라 밀랍만 벗겨진다 — {@link #unwaxedCopperFor(int)}).
     */
    public static int previousOxidationStage(int id) {
        int packed = packedCopperSlot(COPPER_OXIDATION_FAMILIES, id);
        if (packed < 0) return -1;
        return packed % COPPER_OXIDATION_STAGES == 0 ? -1 : id - 1;
    }

    /**
     * 완전히 환원된(unaffected, 0단계) 대응 블록 ID. 산화 대상이 아니면 -1.
     *
     * <p>바닐라 {@code WeatheringCopper.getFirst} 에 해당한다. 낙뢰 직격 구리는 한 단계가 아니라
     * 여기까지 한 번에 돌아간다({@code LightningBolt#clearCopperOnLightningStrike}). 밀랍 블록은
     * {@code WeatheringCopper} 가 아니므로 여기서도 -1 이다.
     */
    public static int firstOxidationStage(int id) {
        int packed = packedCopperSlot(COPPER_OXIDATION_FAMILIES, id);
        if (packed < 0) return -1;
        return id - packed % COPPER_OXIDATION_STAGES;
    }

    /**
     * 밀랍을 바른 대응 블록 ID(산화 단계 보존). 밀랍을 바를 수 없으면 -1.
     * 바닐라 {@code HoneycombItem.WAXABLES} 에 해당한다.
     */
    public static int waxedCopperFor(int id) {
        int packed = packedCopperSlot(COPPER_OXIDATION_FAMILIES, id);
        if (packed < 0) return -1;
        return WAXED_COPPER_FAMILIES[packed / COPPER_OXIDATION_STAGES]
                [packed % COPPER_OXIDATION_STAGES];
    }

    /**
     * 밀랍을 벗긴 대응 블록 ID(산화 단계 보존). 밀랍 블록이 아니면 -1.
     * 바닐라 {@code HoneycombItem.WAX_OFF_BY_BLOCK} 에 해당한다.
     */
    public static int unwaxedCopperFor(int id) {
        int packed = packedCopperSlot(WAXED_COPPER_FAMILIES, id);
        if (packed < 0) return -1;
        return COPPER_OXIDATION_FAMILIES[packed / COPPER_OXIDATION_STAGES]
                [packed % COPPER_OXIDATION_STAGES];
    }

    /**
     * [COPPER] 도끼 우클릭(긁기) 결과. 대상이 아니면 -1.
     *
     * <p>바닐라 {@code AxeItem.evaluateNewBlockState} 는 벗기기 → 산화 -1 → 밀랍 제거 순으로
     * 본다. 뒤 두 가지는 <b>서로 배타적</b>이라(밀랍 블록은 {@code WeatheringCopper} 가 아니고
     * 비밀랍 블록은 {@code WAX_OFF_BY_BLOCK} 에 없다) 판정 순서가 결과를 바꾸지 않는다.
     * 그래서 여기서는 한 함수로 합치고, 밀랍 제거를 먼저 본다.
     *
     * <p>밀랍 제거는 산화 단계를 보존한다(밀랍 바른 산화 구리 → 산화 구리).
     */
    public static int copperScrapeResult(int id) {
        int unwaxed = unwaxedCopperFor(id);
        return unwaxed >= 0 ? unwaxed : previousOxidationStage(id);
    }

    /**
     * [COPPER] 물성이 구리 계열 공통(destroy_time 3.0 · explosion_resistance 6.0 ·
     * needs_stone_tool)인 월드 블록인가. 잘린 구리 계열·조각된 구리·격자·전구·문·다락문과
     * 그 밀랍 대응, 그리고 피뢰침까지 987~1063 한 구간이라 술어 한 줄로 끝난다.
     *
     * <p>구리 랜턴(1064)은 여기 들어가지 않는다 — 바닐라에 없는 추가라 수치를 지어내지 않고
     * 바닐라 {@code lantern} 물성(3.5 / 3.5)을 그대로 쓰기 때문이다.
     */
    public static boolean isCopperBuildingBlock(int id) {
        return id >= CUT_COPPER && id <= LIGHTNING_ROD;
    }

    /**
     * [OPENABLE-METAL] 구리 창살 8종(1372~1379, 밀랍 포함)인가. 바닐라 copper_bars 는 재질만
     * 구리이고 클래스는 {@code IronBarsBlock} 이라 <b>물성이 구리 계열(3.0/6.0)이 아니라 철창
     * (5.0/6.0)</b> 이다. 그 갈림을 {@link #isCopperBuildingBlock(int)} 구간에 억지로 끼우지
     * 않고 술어를 따로 두어, 채굴 시간·폭발 저항이 한 곳만 보고 판정되게 한다.
     */
    public static boolean isCopperBars(int id) {
        return id >= COPPER_BARS && id <= WAXED_OXIDIZED_COPPER_BARS;
    }

    /** 구리 전구(점등·소등·밀랍 포함)인가. */
    public static boolean isCopperBulb(int id) {
        return (id >= COPPER_BULB && id <= COPPER_BULB_LIT + COPPER_OXIDATION_STAGES - 1)
                || (id >= WAXED_COPPER_BULB
                        && id <= WAXED_COPPER_BULB_LIT + COPPER_OXIDATION_STAGES - 1);
    }

    /** 구리 전구가 점등 상태인가. 소등 쌍둥이·비전구는 {@code false}. */
    public static boolean isCopperBulbLit(int id) {
        return (id >= COPPER_BULB_LIT && id < COPPER_BULB_LIT + COPPER_OXIDATION_STAGES)
                || (id >= WAXED_COPPER_BULB_LIT
                        && id < WAXED_COPPER_BULB_LIT + COPPER_OXIDATION_STAGES);
    }

    /**
     * 전구 ID 를 소등 쌍둥이로 정규화한다(드랍·제작·POI·채굴이 한 값을 본다).
     * 점화 화로의 {@code FurnaceVariant.baseBlockId} 와 같은 역할이고, 전구가 아니면 입력 그대로다.
     */
    public static int copperBulbUnlit(int id) {
        return isCopperBulbLit(id) ? id - COPPER_OXIDATION_STAGES : id;
    }

    /** 소등 전구 ↔ 점등 전구 토글. 전구가 아니면 -1. */
    public static int toggledCopperBulb(int id) {
        if (!isCopperBulb(id)) return -1;
        return isCopperBulbLit(id) ? id - COPPER_OXIDATION_STAGES : id + COPPER_OXIDATION_STAGES;
    }

    /**
     * 점등 구리 전구의 바닐라 블록광. 산화 단계마다 15 / 12 / 8 / 4 이고(1.21 {@code CopperBulbBlock}
     * lightLevel), 소등이거나 전구가 아니면 0 이다.
     */
    public static int copperBulbLightLevel(int id) {
        if (!isCopperBulbLit(id)) return 0;
        return COPPER_BULB_LIGHT_BY_AGE[copperOxidationAge(id)];
    }

    /** 점등 구리 전구의 산화 단계별 광량(MC 1.21 CopperBulbBlock). */
    public static final int[] COPPER_BULB_LIGHT_BY_AGE = { 15, 12, 8, 4 };

    /** index = x + z*16 + (worldY-MIN_Y)*256 (worldY ∈ [MIN_Y,MAX_Y]) §1. */
    public static int blockIndex(int x, int worldY, int z) {
        return x + z * 16 + (worldY - MIN_Y) * 256;
    }

    /** 등록 월드 블록 중 플레이어가 직접 설치할 수 있는가. 런타임 전용 상태만 제외한다. */
    public static boolean isPlaceableBlock(int id) {
        return isWorldBlockId(id)
                && id != AIR
                && id != BEDROCK
                && id != ABYSS_STONE && id != FLESH_HEART
                && id != FLESH_BLOCK && id != FLESH_COCOON
                && !isFleshTissue(id)
                && id != REDSTONE_TORCH_OFF && id != REDSTONE_WALL_TORCH_OFF && id != REDSTONE_LAMP_LIT
                && id != PISTON_HEAD && id != MOVING_PISTON
                && id != FURNACE_LIT
                // [FURNACE-VARIANT] 용광로·훈연기의 점화 쌍둥이도 런타임 전용이다.
                && !(id >= BLAST_FURNACE_LIT && id <= SMOKER_LIT)
                // [COPPER] 구리 전구의 점등 쌍둥이도 같은 이유로 런타임 전용이다 —
                // 설치는 언제나 소등 ID 이고 점등은 우클릭 토글로만 들어간다.
                && !isCopperBulbLit(id)
                && !(id >= WATER_SOURCE && id < WALL_TORCH_N)
                && !(id >= SPAWNER_BASE && id <= SPAWNER_BASE + 2)
                && id != NETHER_PORTAL
                // [VOID-END] 엔드 관문은 바닐라에 아이템이 없다(생성·관문 순간이동 전용).
                && id != END_GATEWAY
                // [FROST-SOUL] 서리 얼음도 BlockIds 로만 등록돼 아이템이 없다(차가운 걸음 전용).
                && id != FROSTED_ICE
                && id != PRIMED_TNT
                && id != CAVE_VINES
                && id != CAVE_VINES_PLANT
                && id != BIG_DRIPLEAF_STEM
                && !(id >= BROWN_MUSHROOM_BLOCK && id <= MUSHROOM_STEM)
                && id != BUDDING_AMETHYST
                && !(id >= SMALL_AMETHYST_BUD && id <= AMETHYST_CLUSTER)
                // [SHELF-FUNGUS-WOOL-SLAB] 성숙 외형은 별도 아이템/ID가 아니라 동일
                // 선반버섯의 AGE 상태다. 설치 스택은 항상 기본 AGE로 배치한다.
                // [POPLAR-26.3] 화분에 담긴 묘목은 독립 아이템이 아니라 flower_pot
                // 상호작용 결과다. 스택으로 직접 설치하지 않는다.
                //
                // [POTTED-PLACEMENT] 같은 규약이 화분에 심은 블록 **전부**에 적용된다.
                // 고정본 inner jar 의 net/minecraft/world/item/Items 에는 potted_* 아이템이
                // 하나도 없다(문자열 "POTTED" 출현 0회) — 바닐라는 화분을 놓고 식물을
                // 넣는 상호작용만 있고 화분 블록 자체를 스택으로 놓지 못한다. 이글루·
                // 특성 카탈로그 트랙이 뒤에 추가한 2217 · 2226 · 2267 · 2272 는 이
                // 제외 목록에 들어오지 못해 설치 가능으로 새 나갔던 것이고, 여기서
                // POTTED_POPLAR_SAPLING 과 같은 계약으로 맞춘다. 화분 본체
                // {@link #FLOWER_POT} 는 실제 바닐라 아이템이므로 설치 가능으로 남는다.
                && id != POTTED_POPLAR_SAPLING
                && id != POTTED_CACTUS
                && id != POTTED_RED_MUSHROOM
                && id != POTTED_DEAD_BUSH
                && id != POTTED_RED_TULIP;
    }
}
