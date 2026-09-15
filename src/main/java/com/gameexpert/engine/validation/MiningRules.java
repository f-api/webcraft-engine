package com.gameexpert.engine.validation;

import com.gameexpert.engine.BlockEditRules;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * 서버가 인정하는 <b>최소 채굴 시간</b>의 순수 규칙. 클라 진행률 정본
 * {@code client/src/player/Interaction.ts} 와 같은 수식을 쓴다:
 *
 * <pre>break time = hardness * (canHarvest ? 1.5 : 5) / toolSpeedMultiplier</pre>
 *
 * <p>{@code hardness} 는 클라 {@code BlockRegistry}/{@code packs} 의 값을 그대로 옮긴 표이고,
 * {@code canHarvest} 는 이미 서버 정본인 {@link BlockEditRules#canHarvest} 다.
 *
 * <p>도구 배속만 클라와 다르게 <b>보수적으로</b> 잡는다. 클라는 그때 선택한 슬롯의 도구 배속을 쓰지만
 * 서버는 <b>인벤토리에 든 도구 중 가장 빠른 것</b>을 기준으로 계산한다. 클라가 어떤 슬롯을 들고
 * 채굴하든 실제 진행률은 이 값을 넘을 수 없으므로(채굴 도중 도구를 바꿔도 마찬가지) 정상 플레이는
 * 절대 걸리지 않고, "물리적으로 불가능한 속도"만 남는다.
 *
 * <p>표에 없는 블록은 hardness 0(=최소 시간 없음)으로 본다. 즉시 파괴 식생·장식이 대부분이고,
 * 그 경우에도 {@link MiningLimits} 의 파괴 레이트 예산이 틱당 무제한 파괴를 막는다.
 */
public final class MiningRules {

    /** 적합 도구로 캘 때의 계수(바닐라 1.5). */
    public static final double HARVESTABLE_FACTOR = 1.5;

    /** 티어를 못 채운 도구·맨손의 페널티 계수(바닐라 5). */
    public static final double UNHARVESTABLE_FACTOR = 5.0;

    /** 양털 가위가 잎에 내는 특수 배속(클라 miningSpeed 와 같은 값). */
    private static final double SHEARS_LEAF_SPEED = 15.0;

    private MiningRules() {
    }

    /**
     * 클라 BlockRegistry 의 hardness 대응표(초). 파괴 자체가 거부되는 기반암·유체·지옥문은
     * {@link BlockEditRules#isRejected} 가 앞서 막으므로 여기서는 다루지 않는다.
     */
    public static double hardness(int blockType) {
        if (blockType == Blocks.REDSTONE_BLOCK) return 5;
        if (blockType == Blocks.OBSERVER) return 3;
        if (blockType == Blocks.PISTON || blockType == Blocks.STICKY_PISTON || blockType == Blocks.PISTON_HEAD) return 1.5;
        if (blockType == Blocks.MOVING_PISTON) return Double.POSITIVE_INFINITY;
        if (blockType == Blocks.REDSTONE_LAMP || blockType == Blocks.REDSTONE_LAMP_LIT) return .3;
        if (blockType == Blocks.TARGET || blockType == Blocks.LEVER || com.gameexpert.engine.redstone.RedstoneState.isButton(blockType) || com.gameexpert.engine.redstone.RedstoneState.isPressurePlate(blockType)) return .5;
        if (blockType == Blocks.DAYLIGHT_DETECTOR) return .2;
        if (blockType == Blocks.NOTE_BLOCK) return .8;
        if (com.gameexpert.engine.redstone.RedstoneState.isRedstoneRail(blockType)) return .7;
        if (com.gameexpert.engine.redstone.RedstoneState.isRedstoneTorch(blockType) || blockType == Blocks.REDSTONE_WIRE || blockType == Blocks.REPEATER || blockType == Blocks.COMPARATOR || blockType == Blocks.TRIPWIRE || blockType == Blocks.TRIPWIRE_HOOK_BLOCK) return 0;
        if (blockType == Blocks.ABYSS_STONE) return Double.POSITIVE_INFINITY;
        if (blockType == Blocks.FLESH_FAT_LAMP || blockType == Blocks.FLESH_PULSE_LAMP
                || blockType == Blocks.FLESH_RELIQUARY) return hardness(Blocks.SANDSTONE);
        if (com.gameexpert.engine.FleshNetherRules.isFlesh(blockType)) return hardness(Blocks.SANDSTONE);
        if (blockType == Blocks.LODESTONE) return 3.5;
        // [TRIAL-GAP] 핀 26.3 Blocks: emerald_block strength(5, 6), heavy_core strength(10) 에
        // explosionResistance(1200) 을 덮어쓴다(파괴 시간은 10 그대로).
        if (blockType == Blocks.EMERALD_BLOCK) return 5.0;
        if (blockType == Blocks.HEAVY_CORE) return 10.0;
        // [DRAGON] 핀 26.3 Blocks: dragon_egg strength(3.0, 9.0). 바닐라에서 맨손 공격은 순간이동을
        // 부르므로(DragonEggBlock.attack) 캐기 진행 자체가 거의 일어나지 않는다.
        if (blockType == Blocks.DRAGON_EGG) return 3.0;
        // [END-CITY] 핀 26.3 Blocks: dragon_head strength(1.0), magenta_wall_banner strength(1.0).
        if (blockType == Blocks.DRAGON_HEAD || blockType == Blocks.MAGENTA_WALL_BANNER) return 1.0;
        // [UTILITY] 핀 26.3 Blocks: beacon strength(3.0) · jukebox strength(2.0, 6.0) ·
        // slime_block 은 strength 호출이 없어 0(즉시 파괴)이다.
        if (blockType == Blocks.BEACON) return 3.0;
        if (blockType == Blocks.JUKEBOX) return 2.0;
        if (blockType == Blocks.SLIME_BLOCK) return 0.0;
        // [UTILITY] 흑암석 strength(1.5, 6.0) · 다듬은 흑암석 (2.0, 6.0) · 다듬은 흑암석 벽돌 (1.5, 6.0).
        if (blockType == Blocks.BLACKSTONE || blockType == Blocks.POLISHED_BLACKSTONE_BRICKS) return 1.5;
        if (blockType == Blocks.POLISHED_BLACKSTONE) return 2.0;
        // [VOID-END] 핀 26.3 Blocks: 엔드 돌·벽돌과 그 계단·반 블록·담장 strength(3.0, 9.0),
        // 보라 블록·기둥·계단 1.5, 보라 반 블록만 registerSlab(…, 2.0f) 의 destroyTime 2.0,
        // 후렴 식물·꽃 0.4, 엔드 막대 instabreak(0).
        if (blockType == Blocks.END_STONE || blockType == Blocks.END_STONE_BRICKS
                || blockType == Blocks.END_STONE_BRICK_STAIRS || blockType == Blocks.END_STONE_BRICK_SLAB
                || blockType == Blocks.END_STONE_BRICK_WALL) {
            return 3.0;
        }
        if (blockType == Blocks.PURPUR_BLOCK || blockType == Blocks.PURPUR_PILLAR
                || blockType == Blocks.PURPUR_STAIRS) {
            return 1.5;
        }
        if (blockType == Blocks.PURPUR_SLAB) return 2.0;
        if (blockType == Blocks.CHORUS_PLANT || blockType == Blocks.CHORUS_FLOWER) return 0.4;
        if (blockType == Blocks.END_ROD) return 0.0;
        if (blockType == Blocks.POPLAR_BUTTON || blockType == Blocks.POPLAR_PRESSURE_PLATE) {
            return 0.5;
        }
        if (blockType == Blocks.POPLAR_SIGN || blockType == Blocks.POPLAR_HANGING_SIGN) return 1.0;
        if (blockType == Blocks.POTTED_POPLAR_SAPLING) return 0.0;
        if (BlockFamilies.isWoodLog(blockType)) return 2.0;
        // 종별 목재 가공 계열은 참나무 총칭 세트와 같은 바닐라 destroy_time 을 쓴다:
        // 판자·계단·반 블록·울타리·울타리문 2.0, 다락문·문 3.0(MC Java 1.21.4).
        if (Blocks.isSpeciesWoodBuildingBlock(blockType)) {
            return Blocks.isTrapdoor(blockType) || Blocks.isDoor(blockType) ? 3.0 : 2.0;
        }
        if (BlockFamilies.isLeaves(blockType)) return 0.2;
        // [SHELF-FUNGUS-WOOL-SLAB] 양털 반 블록은 **양털과 같은 0.8** 이고(가위가 가장 빠를 뿐
        // 도구 요구는 없다), 선반은 바닐라 destroy_time 2 · 도끼 최적, 선반버섯은 0 이다 [B].
        // 색 16개·수종 8개를 case 로 나열하지 않고 계열 술어 한 줄로 받는다(색 침대 선례).
        if (Blocks.isWoolSlab(blockType) || Blocks.isWoolStairs(blockType)) return 0.8;
        if (Blocks.isConcreteStairs(blockType) || Blocks.isConcreteSlab(blockType)) return 1.8;
        if (Blocks.isShelf(blockType)) return 2.0;
        if (Blocks.isShelfMushroom(blockType)) return 0.0;
        // [FURNITURE-26.3] 건초 침대만 침대 계열에서 경도가 갈라진다 — 바닐라 destroy_time 이
        // 0.4 가 아니라 **0.2** 다 [B]. isBed 보다 먼저 봐야 아래 침대 갈래에 먹히지 않는다.
        if (Blocks.isStrawBed(blockType)) return 0.2;
        // [BED-COLOR] 침대는 색과 무관하게 같은 경도다(바닐라 BedBlock 은 색마다 같은 설정을
        // 공유한다). 총칭 32 의 기존 값을 그대로 승계한다.
        if (Blocks.isBed(blockType)) return 0.4;
        // [FURNITURE-26.3] 쿠션은 바닐라에서 엔티티라 destroy_time 이 없고 "공격받으면 즉시
        // 부서진다" 로만 서술된다 [B]. 블록으로 옮긴 이 저장소에서는 그 즉시성을 경도 0 으로
        // 옮긴다([C] — 값의 출처는 바닐라 수치가 아니라 서술이다).
        if (Blocks.isCushion(blockType)) return Double.POSITIVE_INFINITY;
        // [STAINED-GLASS] 색 유리·색 유리판은 무색 유리와 같은 바닐라 destroy_time 0.3 이고
        // 도구 제한도 없다. 색 32개를 case 로 나열하지 않고 계열 술어 한 줄로 끝낸다.
        if (Blocks.isStainedGlass(blockType) || Blocks.isStainedGlassPane(blockType)) return 0.3;
        // [COPPER] 구리 계열 987~1063 은 전부 바닐라 destroy_time 3.0 이다(기존 구리
        // 4블록 375~378 과 같은 값). 78 개를 case 로 나열하지 않고 계열 술어 한 줄로 끝낸다.
        // 밀랍 변형도 바닐라에서 물성이 원본과 완전히 같다.
        if (Blocks.isCopperBuildingBlock(blockType)) return 3.0;
        // [CHEST-FAMILY] 구리 상자(1811~1818)도 구리 계열 공통 destroy_time 3.0 이다.
        // ID 가 987~1063 밖이라 위 술어에 걸리지 않아 한 줄을 따로 둔다.
        if (Blocks.chestKind(blockType) == Blocks.CHEST_KIND_COPPER) return 3.0;
        // [ENDER-SHULKER] 엔더 상자 destroy_time 22.5 · 셜커 상자 17종 2.0 [B].
        // 셜커 상자는 술어 한 줄로 17 개를 한꺼번에 덮는다(색마다 case 를 나열하면 한 색을
        // 빠뜨려도 조용히 지나간다).
        if (blockType == Blocks.ENDER_CHEST) return 22.5;
        if (Blocks.isShulkerBox(blockType)) return 2.0;
        // [OPENABLE-METAL] 구리 창살만 구리 계열 3.0 이 아니라 철창과 같은 5.0 이다
        // (바닐라 copper_bars 는 IronBarsBlock 이라 물성을 철창에서 물려받는다).
        // 철 문·철 다락문도 같은 5.0 이다.
        if (Blocks.isCopperBars(blockType)
                || blockType == Blocks.IRON_DOOR || blockType == Blocks.IRON_TRAPDOOR) {
            return 5.0;
        }
        // 구리 랜턴만 바닐라 lantern 의 3.5 다(바닐라에 없는 추가라 그 계약을 그대로 쓴다).
        if (blockType == Blocks.COPPER_LANTERN
                || blockType >= Blocks.EXPOSED_COPPER_LANTERN
                        && blockType <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN) return 3.5;
        if (blockType >= Blocks.EXPOSED_LIGHTNING_ROD
                && blockType <= Blocks.WAXED_OXIDIZED_LIGHTNING_ROD
                || blockType >= Blocks.COPPER_GOLEM_STATUE
                        && blockType <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE) return 3.0;
        // [PROP-MATERIAL] 철 랜턴은 바닐라 lantern 의 원본 3.5 이고 사슬은 chain 의 5.0 이다.
        if (blockType == Blocks.LANTERN) return 3.5;
        // [COPPER-CHAIN] 구리 사슬도 같은 5.0 이다 — 재질만 구리이고 클래스가 ChainBlock 이라
        // 물성을 철 사슬에서 물려받는다(구리 창살이 IronBarsBlock 에서 물려받는 것과 같다).
        if (Blocks.isAnyChain(blockType)) return 5.0;
        if (Blocks.isAnvil(blockType)) return 5.0;
        if (Blocks.isSulfurBlock(blockType) || Blocks.isCinnabarBlock(blockType)) return 1.5;
        if (blockType == Blocks.TINTED_GLASS) return 0.3;
        if (blockType == Blocks.GLOWSTONE) return 0.3;
        if (blockType == Blocks.INFESTED_STONE) return 0.75;
        if (blockType == Blocks.INFESTED_DEEPSLATE) return 1.5;
        return switch (blockType) {
            case Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK -> 50.0;
            case Blocks.ANCIENT_DEBRIS -> 30.0;
            case Blocks.COAL_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK,
                    Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK,
                    Blocks.IRON_BARS, Blocks.ENCHANTING_TABLE,
                    Blocks.SPAWNER_BASE, Blocks.SPAWNER_BASE + 1, Blocks.SPAWNER_BASE + 2 -> 5.0;
            case Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                    Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                    Blocks.DEEPSLATE_DIAMOND_ORE -> 4.5;
            case Blocks.COBWEB -> 4.0;
            // [CREAKING] 크리킹 하트는 바닐라 destroy_time **10.0** 으로 도끼 최적 블록 중
            // 가장 단단하다 [B] «Creaking Heart». 활성 쌍둥이도 같은 값이다(같은 블록의
            // 겉모습 상태이므로 — 화로 점화 쌍둥이가 같은 판단을 이미 냈다).
            case Blocks.CREAKING_HEART, Blocks.CREAKING_HEART_ACTIVE -> 10.0;
            // [CREAKING] 수지 블록. 바닐라 물성표를 확보하지 못해 9칸 저장 블록의 이 저장소
            // 계약(1.0)을 쓴다([C] — 근거 등급을 Blocks.RESIN_BLOCK 주석이 소유한다).
            case Blocks.RESIN_BLOCK -> 1.0;
            // [VILLAGER-STATION] 주민 직업 스테이션 destroy_time(MC Java 1.21.4). 값이 겹치는
            // 기존 case 에 끼워 넣지 않고 한 덩어리로 두어 바닐라 표와 1:1 로 대조된다.
            // [FURNACE-VARIANT] 점화 쌍둥이는 바닐라에서 같은 블록의 lit 상태라 destroy_time 이 같다.
            case Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.STONECUTTER,
                    Blocks.BLAST_FURNACE_LIT, Blocks.SMOKER_LIT -> 3.5;
            case Blocks.BARREL, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE,
                    Blocks.LECTERN, Blocks.LOOM, Blocks.SMITHING_TABLE -> 2.5;
            case Blocks.CAULDRON, Blocks.GRINDSTONE, Blocks.SMOOTH_STONE -> 2.0;
            case Blocks.COMPOSTER -> 0.6;
            case Blocks.BREWING_STAND -> 0.5;
            // 심층암 가공 계열은 전 변형이 바닐라 destroy_time 3.5 다(조약돌 심층암과 같다).
            case Blocks.FURNACE, Blocks.FURNACE_LIT, Blocks.COBBLED_DEEPSLATE,
                    Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
                    Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
                    Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE,
                    Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS,
                    Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB,
                    Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB,
                    Blocks.POLISHED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICK_WALL,
                    Blocks.DEEPSLATE_TILE_WALL -> 3.5;
            case Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
                    Blocks.EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE,
                    Blocks.COPPER_ORE, Blocks.NETHER_GOLD_ORE, Blocks.DEEPSLATE,
                    Blocks.GOLD_BLOCK, Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER,
                    Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
                    Blocks.WOOD_TRAPDOOR -> 3.0;
            case Blocks.BLUE_ICE -> 2.8;
            // [CHEST-FAMILY] 덫 상자는 바닐라에서 일반 상자와 물성이 같다(2.5).
            case Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE -> 2.5;
            // 석재 가공 계열(MC Java 1.21.4). 1.14 변형은 원본 속성을 복사하고 1.13 에 개별
            // 등록된 돌 계열 반 블록은 원본과 무관하게 2.0 이라, 같은 재질에서도 계단과
            // 반 블록의 경도가 갈린다(붉은 사암 계단 0.8 · 붉은 사암 반 블록 2.0).
            case Blocks.MOSSY_COBBLE_STAIRS, Blocks.MOSSY_COBBLE_SLAB,
                    Blocks.CUT_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB,
                    Blocks.STONE_SLAB -> 2.0;
            case Blocks.GRANITE_STAIRS, Blocks.DIORITE_STAIRS, Blocks.ANDESITE_STAIRS,
                    Blocks.STONE_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
                    Blocks.GRANITE_SLAB, Blocks.DIORITE_SLAB, Blocks.ANDESITE_SLAB,
                    Blocks.MOSSY_STONE_BRICK_SLAB,
                    Blocks.GRANITE_WALL, Blocks.DIORITE_WALL, Blocks.ANDESITE_WALL,
                    Blocks.MOSSY_STONE_BRICK_WALL -> 1.5;
            case Blocks.RED_SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL,
                    Blocks.RED_SANDSTONE_WALL,
                    // [STONE-RESIDUAL] 붉은 사암 원석 속성을 복사하는 잘린·조각된 변형.
                    // 같은 재질인데도 잘린 **반 블록**만 아래 2.0 이다.
                    Blocks.CHISELED_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE -> 0.8;
            // [STONE-RESIDUAL] 석재 잔여 계열 destroy_time. 재질이 아니라 변형별로 갈린다.
            //   · 매끄러운(제련) 사암/붉은 사암은 원석 0.8 이 아니라 2.0 이다.
            //   · 잘린 붉은 사암 반 블록은 원석·잘린 블록(0.8)과 달리 2.0 이다.
            //   · 점토 벽돌 계열은 전 변형 2.0, 매끄러운 돌 반 블록은 본체와 같은 2.0 이다.
            case Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.BRICKS,
                    Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_STAIRS,
                    Blocks.BRICK_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB,
                    Blocks.CUT_RED_SANDSTONE_SLAB, Blocks.SMOOTH_RED_SANDSTONE_SLAB,
                    Blocks.SMOOTH_STONE_SLAB, Blocks.BRICK_SLAB, Blocks.BRICK_WALL -> 2.0;
            // 다듬은 화성암 3종·석재 벽돌 변형 2종·진흙 벽돌·응회암 가공 전 계열이 1.5 다.
            case Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
                    Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.MUD_BRICKS,
                    Blocks.CHISELED_TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS,
                    Blocks.CHISELED_TUFF_BRICKS, Blocks.POLISHED_GRANITE_STAIRS,
                    Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS,
                    Blocks.MUD_BRICK_STAIRS, Blocks.TUFF_STAIRS, Blocks.POLISHED_TUFF_STAIRS,
                    Blocks.TUFF_BRICK_STAIRS, Blocks.POLISHED_GRANITE_SLAB,
                    Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_ANDESITE_SLAB,
                    Blocks.MUD_BRICK_SLAB, Blocks.TUFF_SLAB, Blocks.POLISHED_TUFF_SLAB,
                    Blocks.TUFF_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.TUFF_WALL,
                    Blocks.POLISHED_TUFF_WALL, Blocks.TUFF_BRICK_WALL -> 1.5;
            // 다진 진흙만 1.0 이다(같은 계열 진흙 벽돌은 위 1.5).
            case Blocks.PACKED_MUD -> 1.0;
            case Blocks.COBBLE, Blocks.PLANK, Blocks.PLANK_SLAB, Blocks.COBBLE_SLAB,
                    Blocks.WOOD_STAIRS, Blocks.COBBLE_STAIRS, Blocks.COBBLE_WALL,
                    Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE_WALL, Blocks.WOOD_FENCE,
                    Blocks.WOOD_FENCE_GATE, Blocks.CAMPFIRE, Blocks.BONE_BLOCK -> 2.0;
            case Blocks.STONE, Blocks.STONE_BRICK, Blocks.STONE_BRICK_SLAB,
                    Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK,
                    Blocks.DOOR_CLOSED, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
                    Blocks.TUFF, Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE,
                    Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.SMALL_AMETHYST_BUD,
                    Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
                    Blocks.AMETHYST_CLUSTER, Blocks.BOOKSHELF -> 1.5;
            case Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                    Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA,
                    Blocks.ORANGE_TERRACOTTA, Blocks.SMOOTH_BASALT -> 1.25;
            case Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN,
                    Blocks.BAMBOO -> 1.0;
            case Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_STAIRS,
                    Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.RED_SANDSTONE -> 0.8;
            case Blocks.CALCITE -> 0.75;
            case Blocks.MANGROVE_ROOTS, Blocks.RAIL -> 0.7;
            case Blocks.DIRT_PATH -> 0.65;
            case Blocks.GRASS, Blocks.CLAY, Blocks.MYCELIUM, Blocks.GRAVEL, Blocks.FARMLAND -> 0.6;
            case Blocks.DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.MUD,
                    Blocks.PODZOL,
                    Blocks.ROOTED_DIRT, Blocks.COARSE_DIRT, Blocks.HAY_BLOCK,
                    Blocks.ICE, Blocks.PACKED_ICE,
                    // [FROST-SOUL] 핀 26.3 Blocks: 살얼음·영혼 모래·영혼 흙 모두 strength(0.5).
                    Blocks.FROSTED_ICE, Blocks.SOUL_SAND, Blocks.SOUL_SOIL -> 0.5;
            // 양털 16색은 전 색이 바닐라 destroy_time 0.8 이다(도구 제한 없음).
            case Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL,
                    Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
                    Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL,
                    Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
                    Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL,
                    Blocks.BLACK_WOOL -> 0.8;
            case Blocks.LADDER, Blocks.CACTUS, Blocks.NETHERRACK -> 0.4;
            case Blocks.GLASS, Blocks.GLASS_PANE -> 0.3;
            case Blocks.POWDER_SNOW -> 0.25;
            case Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.VINE,
                    Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK,
                    Blocks.MUSHROOM_STEM -> 0.2;
            // 카펫 16색은 전 색이 바닐라 destroy_time 0.1 이다(이끼 카펫과 같다).
            case Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.MAGENTA_CARPET,
                    Blocks.LIGHT_BLUE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIME_CARPET,
                    Blocks.PINK_CARPET, Blocks.GRAY_CARPET, Blocks.LIGHT_GRAY_CARPET,
                    Blocks.CYAN_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET,
                    Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.RED_CARPET,
                    Blocks.BLACK_CARPET -> 0.1;
            case Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.BIG_DRIPLEAF -> 0.1;
            // [GUARDIAN] 바닐라 wet_sponge destroy_time 0.6(도구 티어 없음).
            // [PRISMARINE] 마른 스펀지도 같은 0.6 이다(바닐라 sponge/wet_sponge 동일값).
            case Blocks.WET_SPONGE, Blocks.SPONGE -> 0.6;
            // [PRISMARINE] 바다 랜턴만 0.3 이다(바닐라 sea_lantern destroy_time 0.3).
            case Blocks.SEA_LANTERN -> 0.3;
            // [PRISMARINE] 프리즈머린 3재질과 그 계단·반 블록·담장은 전부 1.5 다
            // (바닐라 prismarine / prismarine_bricks / dark_prismarine 전 변형 공통).
            case Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                    Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS,
                    Blocks.DARK_PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB,
                    Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB,
                    Blocks.PRISMARINE_WALL -> 1.5;
            // [QUARTZ] 석영 계열 962~975 는 전부 바닐라 destroy_time 0.8 이다
            // (quartz_block · chiseled · pillar · smooth_quartz · quartz_bricks 와 그
            // 계단·반 블록이 한 값이며, 담장 3종은 바닐라에 없는 추가라 모재값을 쓴다).
            case Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR,
                    Blocks.QUARTZ_PILLAR_X, Blocks.QUARTZ_PILLAR_Z, Blocks.SMOOTH_QUARTZ,
                    Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_STAIRS,
                    Blocks.QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_WALL,
                    Blocks.SMOOTH_QUARTZ_WALL, Blocks.QUARTZ_BRICK_WALL -> 0.8;
            default -> concreteFamilyHardness(blockType);
        };
    }

    /**
     * [CONCRETE] 콘크리트·가루·색 테라코타·유광 테라코타의 바닐라 destroy_time.
     *
     * <p>switch 의 case 로 넣지 않는 것은 색 16개씩 58개를 나열하면 어느 하나가 빠져도
     * 조용히 0.0(= 즉시 파괴)이 되기 때문이다. 계열 술어로 물으면 색을 빠뜨릴 수가 없다.
     * 값은 전부 MC Java 1.21.4 다 — concrete 1.8 · concrete_powder 0.5 ·
     * terracotta(색) 1.25 · glazed_terracotta 1.4.
     *
     * <p>색 테라코타는 기존 여섯 색이 위 1.25 case 에 이미 있어 여기 도달하지 않는다.
     * 신규 열 색만 이 갈래로 온다 — 두 경로가 같은 1.25 를 내는지는 파생 테스트가 고정한다.
     */
    private static double concreteFamilyHardness(int blockType) {
        if (Blocks.isConcrete(blockType)) return 1.8;
        if (Blocks.isConcretePowder(blockType)) return 0.5;
        if (Blocks.isGlazedTerracotta(blockType)) return 1.4;
        if (Blocks.isColoredTerracotta(blockType)) return 1.25;
        return 0.0;
    }

    /**
     * 이 인벤토리로 낼 수 있는 최대 채굴 배속. 도구 종류가 블록군과 맞는지는 따지지 않고
     * (맞는 경우를 가정해) 가장 유리한 값을 고른다 — 서버는 상한만 판정한다.
     */
    public static double fastestSpeedMultiplier(PlayerInventory inventory, int blockType) {
        double best = 1.0;
        for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
            short item = inventory.itemType(slot);
            if (item == PlayerInventory.EMPTY) continue;
            double base = baseSpeed(item, blockType);
            if (base <= 1.0) continue;
            int efficiency = EnchantmentRules.enchantLevel(
                    inventory.enchantments(slot), EnchantmentRules.EFFICIENCY);
            best = Math.max(best, base + EnchantmentRules.efficiencySpeedBonus(efficiency));
        }
        return best;
    }

    /** 클라 {@code toolTierSpeed} 대응(나무 2 · 돌 4 · 철 6 · 다이아 8 · 금 12). 도구가 아니면 1. */
    private static double baseSpeed(short item, int blockType) {
        if (Blocks.isStrawBed(blockType) && EnchantmentRules.isHoeItem(item)) {
            if (item == PlayerInventory.NETHERITE_HOE) return 9.0;
            if (item == PlayerInventory.DIAMOND_HOE) return 8.0;
            if (item == PlayerInventory.COPPER_HOE) return 5.0;
            return 2.0;
        }
        if (item == PlayerInventory.SHEARS && Blocks.isWoolStairs(blockType)) return 5.0;
        if (item == PlayerInventory.SHEARS) {
            return BlockFamilies.isLeaves(blockType) ? SHEARS_LEAF_SPEED : 1.0;
        }
        if (!EnchantmentRules.isPickaxeItem(item) && !EnchantmentRules.isAxeItem(item)
                && !EnchantmentRules.isShovelItem(item) && !EnchantmentRules.isSwordItem(item)) {
            return 1.0;
        }
        if (item >= PlayerInventory.STONE_TIER_MIN && item <= PlayerInventory.STONE_SWORD) return 4.0;
        if (item >= PlayerInventory.IRON_TIER_MIN && item <= PlayerInventory.IRON_SWORD) return 6.0;
        if (item >= PlayerInventory.DIAMOND_TIER_MIN && item <= PlayerInventory.DIAMOND_TOOL_MAX) return 8.0;
        if (item >= PlayerInventory.GOLD_TIER_MIN && item <= PlayerInventory.GOLD_TOOL_MAX) return 12.0;
        if (item >= PlayerInventory.COPPER_PICKAXE && item <= PlayerInventory.COPPER_HOE) return 5.0;
        if (item >= PlayerInventory.NETHERITE_PICKAXE
                && item <= PlayerInventory.NETHERITE_SWORD) return 9.0;
        return 2.0;
    }

    /** 이 인벤토리의 아이템 중 하나라도 이 블록의 채굴 티어를 채우는가. */
    public static boolean canHarvestWithAnyHeldTool(int blockType, PlayerInventory inventory) {
        if (BlockEditRules.canHarvest(blockType, PlayerInventory.EMPTY)) return true;
        for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
            short item = inventory.itemType(slot);
            if (item != PlayerInventory.EMPTY && BlockEditRules.canHarvest(blockType, item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 이 플레이어가 이 블록을 파괴하는 데 <b>물리적으로 필요한 최소 시간</b>(초).
     * 소지한 최선의 도구를 가정하므로 실제 채굴 시간은 항상 이 값 이상이다.
     */
    public static double minimumBreakSeconds(int blockType, PlayerInventory inventory) {
        double hardness = hardness(blockType);
        if (hardness <= 0.0) return 0.0;
        double factor = canHarvestWithAnyHeldTool(blockType, inventory)
                ? HARVESTABLE_FACTOR : UNHARVESTABLE_FACTOR;
        return hardness * factor / fastestSpeedMultiplier(inventory, blockType);
    }
}
