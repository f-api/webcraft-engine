package com.gameexpert.engine;

import static com.gameexpert.engine.Fluids.*;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * [제공코드] 서버 권위 블록 편집 검증(순수 함수). 틱 루프가 이동 반영 뒤의 플레이어 위치로 호출합니다.
 *
 * 거부 조건: y 범위 밖([MIN_Y,MAX_Y]), 바닐라 눈·블록 AABB 리치 초과, 파괴 불가(공기·기반암·유체),
 * 설치 불가(AIR·유체·스포너·기반암·예약 ID·점화 화로 ID)이거나 대상 칸이
 * 공기/유체/덮어쓸 수 있는 지표 장식이 아님.
 */
public final class BlockEditRules {

    private BlockEditRules() {
    }

    /** 요청 좌표가 월드 Y 범위와 바닐라 서버 블록 리치 안인가. */
    public static boolean isTargetRejected(double px, double py, double pz, boolean crouching,
            int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            return true;
        }
        return !PlayerInteractionRules.canInteractWithBlock(
                px, py, pz, crouching, x, y, z);
    }

    /**
     * 양동이 interact 순수 판정. 빈 양동이는 정확한 물/용암 소스 또는 가루눈만 뜨고,
     * 채운 양동이는 요청 칸이 서버 기준 replaceable일 때만 내용물을 놓습니다.
     */
    public static boolean isBucketRejected(double px, double py, double pz, boolean crouching,
            int x, int y, int z, short bucketItem, int current) {
        if (isTargetRejected(px, py, pz, crouching, x, y, z)) {
            return true;
        }
        return !bucketTargetAccepted(bucketItem, current);
    }

    /**
     * The player-independent half of {@link #isBucketRejected}: whether a bucket's contents can
     * be placed into (or picked up from) the cell. The dispenser bucket behaviours share it.
     */
    public static boolean bucketTargetAccepted(short bucketItem, int current) {
        return !bucketTargetRejected(bucketItem, current);
    }

    private static boolean bucketTargetRejected(short bucketItem, int current) {
        if (bucketItem == com.gameexpert.engine.inventory.PlayerInventory.BUCKET) {
            return current != WATER_SOURCE && current != LAVA_SOURCE && current != Blocks.POWDER_SNOW;
        }
        if (bucketItem == com.gameexpert.engine.inventory.PlayerInventory.POWDER_SNOW_BUCKET) {
            return current != AIR;
        }
        if (bucketItem == com.gameexpert.engine.inventory.PlayerInventory.WATER_BUCKET
                || bucketItem == com.gameexpert.engine.inventory.PlayerInventory.LAVA_BUCKET) {
            return !isReplaceable(current);
        }
        return true;
    }

    public static boolean isRejected(double px, double py, double pz, boolean crouching,
            PlayerAction.EditKind kind, int x, int y, int z, int blockType, int current) {
        if (isTargetRejected(px, py, pz, crouching, x, y, z)) {
            return true;
        }
        if (kind == PlayerAction.EditKind.BREAK) {
            // 이미 제거된 칸의 중복 요청은 채굴 예산·도구 내구를 소비하기 전에 거절한다.
            // 엔드 차원문과 그 틀은 기반암처럼 파괴 불가다(바닐라 destroyTime -1).
            return current == AIR || current == BEDROCK || current == Blocks.ABYSS_STONE || current == Blocks.NETHER_PORTAL
                    || current == Blocks.END_PORTAL || current == Blocks.END_PORTAL_FRAME
                    // [VOID-END] 엔드 관문도 strength(-1, 3600000) 파괴 불가다.
                    || current == Blocks.END_GATEWAY
                    || isFluid(current);
        }
        if (!BlockPlacementRules.isPlaceable(blockType)) {
            return true; // 설치 가능한 등록 블록이 아니면 거부(단일 출처 = BlockPlacementRules).
            // AIR·기반암·유체·내부 상태와 순수 아이템 ID가 월드 블록으로 설치되는 것을 막는다.
        }
        if (blockType == Blocks.LILY_PAD) {
            // 아래 수원은 인벤 소비 전 isPlacementSupported의 현재 월드 뷰로 검증한다. 여기서는 흐르는
            // 물을 수련잎으로 치환하지 못하게 대상 칸이 정확히 AIR인지 먼저 제한한다.
            return current != AIR;
        }
        return !isPlacementReplaceable(current);
    }

    /**
     * 클라이언트 BlockRegistry의 replaceable 지표 장식과 동일한 서버 권위 집합입니다.
     * 줄기·작물·묘목처럼 보존해야 하는 비고체 블록은 포함하지 않습니다.
     */
    public static boolean isPlacementReplaceable(int current) {
        return current == AIR || isFluid(current)
                || current == Blocks.TALL_GRASS
                || current == Blocks.FLOWER_RED
                || current == Blocks.FLOWER_YELLOW
                || current == Blocks.LILY_PAD
                || current == Blocks.FERN
                || current == Blocks.BUSH
                || current == Blocks.DEAD_BUSH;
    }

    /**
     * 선택 아이템으로 파괴한 블록의 드랍을 획득할 수 있는가.
     * 나무(101) / 돌(116) / 철(120) 곡괭이만 티어 1 / 2 / 3으로 인정한다.
     * 곡괭이가 필요 없는 블록은 선택 아이템과 무관하게 true다.
     */
    public static boolean canHarvest(int blockType, short selectedItemType) {
        if (blockType == Blocks.ABYSS_STONE) return false;
        if (FleshNetherRules.isFlesh(blockType)) return FleshNetherRules.isMiningTool(selectedItemType);
        int requiredTier = Blocks.isSulfurBlock(blockType) || Blocks.isCinnabarBlock(blockType)
                ? 1 : switch (blockType) {
            case Blocks.REDSTONE_BLOCK, Blocks.OBSERVER, Blocks.STONE, Blocks.COBBLE, Blocks.STONE_BRICK, Blocks.LODESTONE,
                    Blocks.FURNACE, Blocks.FURNACE_LIT, Blocks.COAL_ORE,
                    Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.RED_SANDSTONE,
                    Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA,
                    Blocks.RED_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA,
                    Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
                    Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE,
                    Blocks.SMOOTH_BASALT, Blocks.AMETHYST_BLOCK,
                    // 인챈트 테이블은 바닐라대로 곡괭이 전용이다(책장은 도구 제한 없음).
                    Blocks.ENCHANTING_TABLE,
                    // [CONTAINER-MENUS] 발사기·공급기·제작기·깔때기는 바닐라 requiresCorrectToolForDrops +
                    // mineable/pickaxe 이고 needs_*_tool 태그가 없어 나무 곡괭이(티어 1)로 회수된다.
                    Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER, Blocks.HOPPER,
                    Blocks.DEEPSLATE_COAL_ORE,
                    // 심층암 가공 계열은 곡괭이 전용이지만 needs_stone_tool 태그가 없어
                    // 나무 곡괭이(티어 1)로도 회수된다 — 심층암·조약돌 심층암과 같다.
                    Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
                    Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
                    Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE,
                    Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS,
                    Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB,
                    Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB,
                    Blocks.POLISHED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICK_WALL,
                    Blocks.DEEPSLATE_TILE_WALL,
                    // [VILLAGER-STATION] 곡괭이 전용 스테이션 여섯 + 매끄러운 돌. 여섯 모두
                    // mineable/pickaxe 이지만 needs_stone_tool 태그가 없어 나무 곡괭이로도
                    // 회수된다. 나머지 일곱(통·제도판·퇴비통·화살 제작대·독서대·직조기·
                    // 대장장이 작업대)은 도끼 선호일 뿐 도구 없이도 드랍하므로 티어 0 이다.
                    Blocks.BLAST_FURNACE, Blocks.BREWING_STAND, Blocks.CAULDRON,
                    Blocks.GRINDSTONE, Blocks.SMOKER, Blocks.STONECUTTER,
                    // [FURNACE-VARIANT] 점화 쌍둥이도 같은 곡괭이 티어다. 캐는 도중 점화되면
                    // 회수 가능 판정이 뒤집혀 드랍이 사라지는 경로를 막는다.
                    Blocks.BLAST_FURNACE_LIT, Blocks.SMOKER_LIT,
                    Blocks.SMOOTH_STONE,
                    // [STONE-PROC] 석재 가공 계열도 곡괭이 전용이지만 needs_stone_tool 태그가
                    // 없어 나무 곡괭이(티어 1)로 회수된다 — 원본 재질과 같은 규칙이다.
                    Blocks.GRANITE_STAIRS, Blocks.DIORITE_STAIRS, Blocks.ANDESITE_STAIRS,
                    Blocks.RED_SANDSTONE_STAIRS, Blocks.STONE_STAIRS,
                    Blocks.MOSSY_COBBLE_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
                    Blocks.GRANITE_SLAB, Blocks.DIORITE_SLAB, Blocks.ANDESITE_SLAB,
                    Blocks.CUT_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB, Blocks.STONE_SLAB,
                    Blocks.MOSSY_COBBLE_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB,
                    Blocks.GRANITE_WALL, Blocks.DIORITE_WALL, Blocks.ANDESITE_WALL,
                    Blocks.SANDSTONE_WALL, Blocks.RED_SANDSTONE_WALL,
                    Blocks.MOSSY_STONE_BRICK_WALL,
                    // [STONE-RESIDUAL] 석재 잔여 계열도 곡괭이 전용(티어 1)이다. 다진 진흙만
                    // 바닐라에서 mineable/shovel 이고 도구 요구가 없어 아래 default 0 으로 빠진다.
                    Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
                    Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
                    Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.BRICKS,
                    Blocks.MUD_BRICKS, Blocks.CHISELED_TUFF, Blocks.POLISHED_TUFF,
                    Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF_BRICKS,
                    Blocks.POLISHED_GRANITE_STAIRS, Blocks.POLISHED_DIORITE_STAIRS,
                    Blocks.POLISHED_ANDESITE_STAIRS, Blocks.SMOOTH_SANDSTONE_STAIRS,
                    Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.BRICK_STAIRS,
                    Blocks.MUD_BRICK_STAIRS, Blocks.TUFF_STAIRS, Blocks.POLISHED_TUFF_STAIRS,
                    Blocks.TUFF_BRICK_STAIRS, Blocks.POLISHED_GRANITE_SLAB,
                    Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_ANDESITE_SLAB,
                    Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.CUT_RED_SANDSTONE_SLAB,
                    Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.SMOOTH_STONE_SLAB,
                    Blocks.BRICK_SLAB, Blocks.MUD_BRICK_SLAB, Blocks.TUFF_SLAB,
                    Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_SLAB, Blocks.BRICK_WALL,
                    Blocks.MUD_BRICK_WALL, Blocks.TUFF_WALL, Blocks.POLISHED_TUFF_WALL,
                    Blocks.TUFF_BRICK_WALL,
                    // [PRISMARINE] 프리즈머린 3재질과 그 형상 변형도 곡괭이 전용(티어 1)이다.
                    // 바닐라 mineable/pickaxe 이며 needs_stone_tool 태그가 없다.
                    // 바다 랜턴·스펀지 둘은 바닐라에 도구 요구가 없어 default 0 으로 빠진다
                    // (sea_lantern 은 어떤 태그에도 없고 sponge/wet_sponge 는 mineable/hoe).
                    Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                    Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS,
                    Blocks.DARK_PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB,
                    Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB,
                    Blocks.PRISMARINE_WALL,
                    // [VOID-END] 엔드 돌·보라 계열은 mineable/pickaxe(담장은 #walls)이고
                    // requiresCorrectToolForDrops 이며 needs_*_tool 태그가 없다(티어 1).
                    Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICK_STAIRS,
                    Blocks.END_STONE_BRICK_SLAB, Blocks.END_STONE_BRICK_WALL,
                    Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_STAIRS,
                    Blocks.PURPUR_SLAB,
                    // [QUARTZ] 석영 계열 962~975 도 곡괭이 전용(티어 1)이다. 바닐라
                    // mineable/pickaxe 이며 needs_*_tool 태그가 없다.
                    Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR,
                    Blocks.QUARTZ_PILLAR_X, Blocks.QUARTZ_PILLAR_Z, Blocks.SMOOTH_QUARTZ,
                    Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_STAIRS,
                    Blocks.QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_WALL,
                    Blocks.SMOOTH_QUARTZ_WALL, Blocks.QUARTZ_BRICK_WALL,
                    // [PROP-MATERIAL] 사슬 3축·랜턴도 곡괭이 전용(티어 1)이다. 바닐라
                    // chain·lantern 은 mineable/pickaxe 이면서 needs_*_tool 태그가 없어
                    // 어느 곡괭이로도 회수되고, 맨손·다른 도구로는 아무것도 떨구지 않는다.
                    Blocks.CHAIN, Blocks.CHAIN_X, Blocks.CHAIN_Z, Blocks.LANTERN,
                    // [GRATE-TIER] 철창은 바닐라 iron_bars 가 mineable/pickaxe 이고
                    // needs_*_tool 태그가 없어 <b>어느 곡괭이로든</b> 회수되지만 맨손으로는
                    // 아무것도 떨구지 않는다. 이 저장소는 오랫동안 티어 0(맨손 회수)으로
                    // 등록돼 있었고 OPENABLE-METAL 트랙이 결손으로 보고했다 — 여기서 교정한다.
                    // 저장된 월드의 드랍 기대치가 바뀌므로 MC-REFERENCE 가 그 영향을 소유한다.
                    // 유리판·색 유리판은 바닐라에 도구 태그 자체가 없어 티어 0 이 맞고,
                    // 구리 격자는 needs_stone_tool 이라 아래 티어 2 갈래가 이미 소유한다.
                    Blocks.IRON_BARS,
                    Blocks.SULFUR_BLOCK,
                    // [ENDER-SHULKER] 엔더 상자는 바닐라 mineable/pickaxe 이고 needs_*_tool
                    // 태그가 없어 **나무 곡괭이(티어 1)** 로 회수된다 [B]. 흑요석(티어 4)과
                    // 헷갈리기 쉬운 자리라 한 줄을 따로 둔다 — 재료가 흑요석일 뿐 블록 자체는
                    // 다이아 곡괭이를 요구하지 않는다. 셜커 상자는 도구 요구가 아예 없어
                    // (아무 도구로나 회수) 이 표에 넣지 않는다(기본 티어 0).
                    Blocks.ENDER_CHEST,
                    // 네더랙은 mineable/pickaxe 이지만 needs_*_tool 태그가 없어 나무 곡괭이부터
                    // 자기 자신을 회수한다. 맨손·다른 도구 파괴는 드랍이 없다.
                    Blocks.NETHERRACK,
                    // [UTILITY] 흑암석 계열: requiresCorrectToolForDrops · #mineable/pickaxe, needs_* 없음.
                    Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
                    Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL -> 1;
            case Blocks.IRON_ORE, Blocks.LAPIS_ORE, Blocks.COPPER_ORE,
                    Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.DEEPSLATE_LAPIS_ORE, Blocks.RAW_IRON_BLOCK,
                    Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK,
                    Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER,
                    Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER -> 2;
            case Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.REDSTONE_ORE,
                    Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                    Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                    // [TRIAL-GAP] 에메랄드 블록은 requiresCorrectToolForDrops 이고 needs_iron_tool
                    // 이라 철 곡괭이 이상이다. 무거운 핵은 mineable/pickaxe 뿐(도구 요구 없음)이라
                    // 이 표에 넣지 않는다(기본 티어 0).
                    Blocks.EMERALD_BLOCK -> 3;
            // 흑요석은 다이아몬드 곡괭이로만 회수한다. 철과 다이아의 일반 harvest level이
            // 같으므로 아래 tier 숫자만으로 합치지 않고 선택 아이템을 별도로 확인한다.
            case Blocks.OBSIDIAN, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK -> 4;
            default -> copperFamilyTier(blockType);
        };
        if (requiredTier == 0) {
            return true;
        }
        int selectedTier = switch (selectedItemType) {
            case PlayerInventory.PICKAXE -> 1;
            case PlayerInventory.STONE_TIER_MIN -> 2;
            case PlayerInventory.COPPER_PICKAXE -> 2;
            case PlayerInventory.IRON_TIER_MIN -> 3;
            case PlayerInventory.GOLD_PICKAXE -> 1;
            case PlayerInventory.DIAMOND_PICKAXE -> 3;
            case PlayerInventory.NETHERITE_PICKAXE -> 4;
            default -> 0;
        };
        return requiredTier == 4
                ? selectedItemType == PlayerInventory.DIAMOND_PICKAXE
                        || selectedItemType == PlayerInventory.NETHERITE_PICKAXE
                : selectedTier >= requiredTier;
    }

    /**
     * [CONCRETE] 콘크리트·색 테라코타·유광 테라코타는 바닐라 mineable/pickaxe 이고
     * needs_*_tool 태그가 없어 <b>나무 곡괭이(티어 1)</b>로 회수된다. 콘크리트 가루만
     * mineable/shovel 이고 도구 요구가 없어 티어 0(맨손 회수)이다.
     *
     * <p>색 16개씩 나열하지 않고 계열 술어로 묻는다 — 하나만 빠지면 그 색만 조용히 맨손
     * 회수가 되어 아무 게이트도 잡지 못한다. 기존 여섯 색 테라코타는 위 case 목록에 이미
     * 있어 여기 도달하지 않는다.
     */
    /**
     * [COPPER] 구리 계열 곡괭이 티어. 987~1063 은 바닐라 {@code needs_stone_tool} 이라
     * 티어 2 이고(기존 구리 4블록 375~378 과 같다), 구리 랜턴만 바닐라 {@code lantern} 이
     * mineable/pickaxe 이면서 needs_*_tool 태그가 없어 티어 1 이다.
     */
    private static int copperFamilyTier(int blockType) {
        if (Blocks.isCopperBuildingBlock(blockType)) return 2;
        // [CHEST-FAMILY] 구리 상자는 바닐라 needs_stone_tool 이라 티어 2 다 — 돌 곡괭이
        // 이상이라야 자기 자신을 떨군다. 덫 상자는 나무 상자라 여기 들어오지 않는다.
        if (Blocks.chestKind(blockType) == Blocks.CHEST_KIND_COPPER) return 2;
        // [OPENABLE-METAL] 구리 창살·철 문·철 다락문은 바닐라 mineable/pickaxe 이지만
        // needs_*_tool 태그가 없어 **나무 곡괭이(티어 1)** 로 회수된다. 구리 창살이 구리
        // 계열의 티어 2 가 아닌 것은 클래스가 IronBarsBlock 이기 때문이다.
        if (Blocks.isCopperBars(blockType)
                || blockType == Blocks.IRON_DOOR || blockType == Blocks.IRON_TRAPDOOR) {
            return 1;
        }
        // [COPPER-CHAIN] 구리 사슬도 같은 이유로 티어 1 이다 — 재질만 구리이고 클래스가
        // ChainBlock 이라 철 사슬(위 case 목록)과 같은 태그를 물려받는다. 구리 계열의
        // needs_stone_tool(티어 2)로 끌려가면 나무 곡괭이로 캔 사슬이 사라진다.
        if (Blocks.isCopperChain(blockType)) return 1;
        if (blockType == Blocks.COPPER_LANTERN
                || blockType >= Blocks.EXPOSED_COPPER_LANTERN
                        && blockType <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN) return 1;
        if (CopperAgeRules.isLightningRod(blockType)
                || blockType >= Blocks.COPPER_GOLEM_STATUE
                        && blockType <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE) return 2;
        return concreteFamilyTier(blockType);
    }

    private static int concreteFamilyTier(int blockType) {
        if (Blocks.isConcretePowder(blockType)) return 0;
        return Blocks.isConcrete(blockType) || Blocks.isConcreteStairs(blockType) || Blocks.isConcreteSlab(blockType)
                || Blocks.isGlazedTerracotta(blockType)
                || Blocks.isColoredTerracotta(blockType) ? 1 : 0;
    }

    /**
     * 설치할 블록을 대상 칸에 가상 반영한 월드 뷰로 지지 조건을 검증한다.
     * 인벤토리를 소비하기 전에 호출하는 순수 판정이다.
     */
    static boolean isPlacementSupported(int blockType, int x, int y, int z,
            SupportRules.BlockLookup lookup) {
        return isPlacementSupported(blockType, 0, x, y, z, lookup);
    }

    /** 방향 state가 지지 면을 고르는 블록까지 포함한 설치 전 검증. */
    static boolean isPlacementSupported(int blockType, int state, int x, int y, int z,
            SupportRules.BlockLookup lookup) {
        return isPlacementSupported(blockType, state, x, y, z, lookup,
                (qx, qy, qz, blockId) -> 0);
    }

    /** 대상 주변의 state 기반 지지 면까지 포함한 설치 전 검증. */
    static boolean isPlacementSupported(int blockType, int state, int x, int y, int z,
            SupportRules.BlockLookup lookup, SupportRules.StateLookup states) {
        // 침대는 발/머리 두 셀을 한 요청으로 원자 배치한다. 설치 전에는 짝 셀이 아직 없으므로
        // 런타임의 pair 무결성 검사 대신 각 셀의 바닥 지지만 여기서 확인한다.
        if (Blocks.isBed(blockType)) {
            return Fluids.isSolid(lookup.getBlock(x, y - 1, z));
        }
        if (!SupportRules.requiresSupport(blockType)) {
            return true;
        }
        return SupportRules.isSupported(blockType, state, x, y, z, (qx, qy, qz) -> {
            if (qx == x && qy == y && qz == z) {
                return blockType;
            }
            return lookup.getBlock(qx, qy, qz);
        }, states);
    }
}
