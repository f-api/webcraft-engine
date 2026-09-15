package com.gameexpert.engine.inventory;

import java.util.function.DoubleSupplier;

import com.gameexpert.engine.BlockEditRules;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.BlockPlacementRules;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.ArchaeologyRules;
import com.gameexpert.engine.SuspiciousStewRules;
import com.gameexpert.terrain.Blocks;

/**
 * 서버 권위 인벤토리 규칙(§11.4 드랍 · 설치 소비 · 내구도 마모). 순수 함수 모음이라 상태가 없습니다.
 *
 * 드랍(파괴 성공 시): 석재·광석은 MC 재료로 변환하고, 자갈·잎·풀은 주입 난수로 확률을 판정한다.
 *   선택 도구가 필요 티어에 미달하면 블록은 파괴되지만 드랍은 없다.
 * 설치: 선택 슬롯이 그 blockType 을 1개 이상 들고 있어야 하며(검·도구는 설치 불가), 성공 시 1개 차감.
 * 내구도(§4): 채굴 완료 1회당 선택 도구 −1, 몹 명중 1회당 선택 검 −1. 0 이 되면 소멸.
 */
public final class InventoryRules {

    // WorldTickLoop를 건드리지 않고도 "인벤 차감 + 지원 검사 + 블록 반영"을 한 틱에서 원자적으로
    // 처리하기 위한 틱 스레드 전용 보류 슬롯. 일반 블록은 기존처럼 즉시 차감한다.
    private static final ThreadLocal<PendingLilyPlace> PENDING_LILY_PLACE = new ThreadLocal<>();

    private static final class PendingLilyPlace {
        private final PlayerInventory inventory;
        private final PlayerInventory.HandRef hand;

        private PendingLilyPlace(PlayerInventory inventory, PlayerInventory.HandRef hand) {
            this.inventory = inventory;
            this.hand = hand;
        }
    }

    private InventoryRules() {
    }

    /** 선택 핫바 칸의 방어구를 서버 착용 상태로 옮깁니다. 같은 부위가 있으면 선택 칸으로 맞바꿉니다. */
    public static boolean equipSelectedArmor(PlayerInventory inv) {
        return inv.equip(inv.selectedSlot());
    }

    /** 블록 유체 상호작용에 쓰는 빈/물/용암 양동이 아이템인가. */
    public static boolean isBucket(short itemType) {
        return PlayerInventory.isBucket(itemType);
    }

    /**
     * 서버 판정이 끝난 양동이 상호작의 선택 슬롯을 제자리에서 교체합니다.
     * 선택 아이템이 예상과 다르거나 비정상적으로 스택된 양동이면 무변경 거부합니다.
     */
    public static boolean replaceSelectedBucket(PlayerInventory inv, short expectedType, short replacementType) {
        if (!isBucketContainer(expectedType) || !isBucketContainer(replacementType)) {
            return false;
        }
        return inv.replaceSelectedSingle(expectedType, replacementType);
    }

    public static boolean replaceBucket(PlayerInventory inv, PlayerInventory.HandRef hand,
            short expectedType, short replacementType) {
        if (!isBucketContainer(expectedType) || !isBucketContainer(replacementType)) return false;
        return inv.replaceSingle(hand, expectedType, replacementType);
    }

    /** 제자리 교체 가능한 양동이 용기. 우유는 월드 블록을 옮기지 않지만 같은 단일 슬롯 계약을 쓴다. */
    public static boolean isBucketContainer(short itemType) {
        return isBucket(itemType) || itemType == PlayerInventory.MILK_BUCKET
                || itemType == PlayerInventory.COD_BUCKET
                || itemType == PlayerInventory.SALMON_BUCKET
                || itemType == PlayerInventory.TROPICAL_FISH_BUCKET
                || itemType == PlayerInventory.PUFFERFISH_BUCKET
                || itemType == PlayerInventory.TADPOLE_BUCKET
                || itemType == PlayerInventory.AXOLOTL_BUCKET;
    }

    /** 선택 칸의 예상 일반 아이템을 정확히 하나 소비한다. 대상·리치 판정은 호출자가 먼저 끝내야 한다. */
    public static boolean consumeSelectedOne(PlayerInventory inv, short expectedType) {
        int slot = inv.selectedSlot();
        if (inv.itemType(slot) != expectedType || inv.count(slot) <= 0) {
            return false;
        }
        try {
            return inv.set(slot, expectedType, inv.count(slot) - 1);
        } catch (IllegalStateException rejected) {
            // A persistence-terminal or otherwise rejected low-level write is a
            // gameplay refusal, not a partially consumed item.
            return false;
        }
    }

    public static boolean consumeOne(PlayerInventory inv, PlayerInventory.HandRef hand,
            short expectedType) {
        return inv.consumeOne(hand, expectedType);
    }

    /** 선택 칸의 뼛가루를 정확히 하나 소비한다. */
    public static boolean consumeSelectedBoneMeal(PlayerInventory inv) {
        return consumeSelectedOne(inv, PlayerInventory.BONE_MEAL);
    }

    /**
     * 파괴된 blockType 의 결정적 드랍(없으면 0). 확률 드랍인 잎·풀은 여기서 0이며,
     * 실제 플레이어 채굴은 {@link #minedDropFor(short, short, DoubleSupplier)}를 사용한다.
     * v3 신규 사탕수수(18)·선인장(19)·버섯(24,25)은 목록에 없어 자기 자신을 드랍(기본 규칙).
     * 건축 B1 블록 26~29·31은 자기 자신을 드랍한다.
     * 생존 F2: 점화 화로(34)는 화로 아이템(33)을 드랍, 광물 블록(35~38)은 자기 자신.
     * 벽토치(52~55)는 방향과 무관하게 토치 아이템(17)을 드랍.
     */
    public static short dropFor(short brokenBlockType) {
        if (brokenBlockType == Blocks.REDSTONE_WIRE) return PlayerInventory.REDSTONE_DUST;
        if (brokenBlockType == Blocks.TRIPWIRE) return PlayerInventory.STRING;
        if (brokenBlockType == Blocks.TRIPWIRE_HOOK_BLOCK) return PlayerInventory.TRIPWIRE_HOOK;
        if (brokenBlockType == Blocks.REDSTONE_TORCH_OFF || brokenBlockType == Blocks.REDSTONE_WALL_TORCH || brokenBlockType == Blocks.REDSTONE_WALL_TORCH_OFF) return (short) Blocks.REDSTONE_TORCH;
        if (brokenBlockType == Blocks.REDSTONE_LAMP_LIT) return (short) Blocks.REDSTONE_LAMP;
        if (brokenBlockType == Blocks.PISTON_HEAD || brokenBlockType == Blocks.MOVING_PISTON) return 0;
        if (com.gameexpert.engine.FleshNetherRules.isFlesh(brokenBlockType)
                || brokenBlockType == Blocks.ABYSS_STONE) {
            // Non-player removal never makes an internal flesh block obtainable.
            return (short) com.gameexpert.engine.FleshNetherRules.dropItem(brokenBlockType, 1.0);
        }
        // [END-CITY] 벽 현수막은 같은 색 현수막 아이템을 떨군다(loot_table/blocks/magenta_banner).
        if (brokenBlockType == Blocks.MAGENTA_WALL_BANNER) return (short) Blocks.MAGENTA_BANNER;
        // [POPLAR-26.3] 화분 상태는 아이템이 아니다. 현재 프로토콜에 flower_pot
        // 아이템이 없으므로 최소한 내부의 포플러 묘목은 보존한다.
        if (brokenBlockType == Blocks.POTTED_POPLAR_SAPLING) {
            return (short) Blocks.POPLAR_SAPLING;
        }
        int logItem = BlockFamilies.woodLogItem(Short.toUnsignedInt(brokenBlockType));
        if (logItem != Blocks.AIR) return (short) logItem;
        // [QUARTZ] 석영 기둥의 축 변형도 통나무와 같이 y축 대표 ID 를 드랍한다.
        int pillarItem = BlockFamilies.quartzPillarItem(Short.toUnsignedInt(brokenBlockType));
        if (pillarItem != Blocks.AIR) return (short) pillarItem;
        // [PROP-MATERIAL] 사슬 축 변형도 같은 이유로 y축 대표 ID 를 드랍한다.
        int chainItem = BlockFamilies.chainItem(Short.toUnsignedInt(brokenBlockType));
        if (chainItem != Blocks.AIR) return (short) chainItem;
        // [COPPER-CHAIN] 구리 사슬은 축만 접고 산화 단계·밀랍은 보존한 대표 ID 를 떨군다.
        int copperChain = BlockFamilies.copperChainItem(Short.toUnsignedInt(brokenBlockType));
        if (copperChain != Blocks.AIR) return (short) copperChain;
        // [COPPER] 점등 구리 전구는 점화 화로(34→33)와 같이 소등 쌍둥이를 드랍한다.
        // 정규화 지점이 한 곳이라 캐는 도중 토글돼도 드랍이 갈리지 않는다.
        int bulb = Blocks.copperBulbUnlit(Short.toUnsignedInt(brokenBlockType));
        if (bulb != Short.toUnsignedInt(brokenBlockType)) return (short) bulb;
        // [STAINED-GLASS] 색 유리 16색은 무색 유리와 같이 섬세한 손길 없이는 아무것도 떨구지
        // 않는다. 색 유리판은 무색 유리판(92)과 같이 자기 자신을 떨궈 이 목록에 넣지 않는다.
        if (isWoodLeaves(brokenBlockType) || Blocks.isGlassBlock(brokenBlockType)
                || brokenBlockType == Blocks.TALL_GRASS || brokenBlockType == Blocks.DEAD_BUSH
                // [VOID-END] 후렴 식물은 확률 드랍(후렴과 0~1)이라 결정적 표에서는 0 이다.
                || brokenBlockType == Blocks.CHORUS_PLANT) return 0;
        if (brokenBlockType >= Blocks.SPAWNER_BASE && brokenBlockType <= Blocks.SPAWNER_BASE + 2) return 0;
        if (brokenBlockType == Blocks.PRIMED_TNT || brokenBlockType == Blocks.NETHER_PORTAL
                || brokenBlockType == Blocks.FIRE) return 0;
        // [FROST-SOUL] loot_table/blocks/frosted_ice 는 풀이 없다 — 섬세한 손길로도 아무것도 떨구지 않는다.
        if (brokenBlockType == Blocks.FROSTED_ICE) return 0;
        if (brokenBlockType == Blocks.FROGSPAWN || brokenBlockType == Blocks.BEE_NEST) return 0;
        if (brokenBlockType == Blocks.TORCHFLOWER_CROP) return PlayerInventory.TORCHFLOWER_SEEDS;
        if (brokenBlockType == Blocks.PITCHER_CROP) return PlayerInventory.PITCHER_POD;
        // [DEEP-DARK] 스컬크 5종은 바닐라대로 섬세한 손길 없이는 **아무것도** 떨구지 않는다
        // (대신 경험치가 나온다 — XpRules.minedBlockXpMin/Max). 실크 터치 회수는
        // isSilkTouchSelfDrop 이 맡는다. 말린 가스트(1250)는 자기 자신을 떨궈 여기 없다.
        if (Blocks.isSculkBlock(brokenBlockType)) return 0;
        // [ENDER-SHULKER] 엔더 상자는 실크 터치가 없으면 **흑요석 8** 이다
        // ([A] blocks/ender_chest: 실크 터치 없는 갈래가 obsidian ×8 고정). 개수 8 은
        // P1Rules.minedDropCount 가 소유한다(진흙 4 와 같은 자리) — 종류와 개수 정본을
        // 한 함수에 겹쳐 두면 폭발 드랍(explosionDropFor)이 개수를 잃는다.
        if (brokenBlockType == Blocks.ENDER_CHEST) return (short) Blocks.OBSIDIAN;
        if (brokenBlockType == Blocks.FARMLAND) return (short) Blocks.DIRT;
        // [INFESTED-26.3] Ordinary mining has no loot pool. The block-breaking hook separately
        // spawns its silverfish; Silk Touch is handled below and returns the uninfested host.
        if (Blocks.isInfestedStone(brokenBlockType)) return 0;
        // 눈 블록은 실제 채굴 경로에서 삽일 때 눈덩이 네 개를 방출한다. 가루눈은 양동이 상호작용으로만 회수한다.
        if (brokenBlockType == Blocks.SNOW_BLOCK || brokenBlockType == Blocks.POWDER_SNOW) return 0;
        if (brokenBlockType == Blocks.WHEAT_CROP) return PlayerInventory.WHEAT_SEEDS;
        if (brokenBlockType == Blocks.STONE) return (short) Blocks.COBBLE;
        if (brokenBlockType == Blocks.GRASS) return (short) Blocks.DIRT;
        if (brokenBlockType == Blocks.COAL_ORE) return PlayerInventory.COAL;
        if (brokenBlockType == Blocks.IRON_ORE) return PlayerInventory.RAW_IRON;
        if (brokenBlockType == Blocks.GOLD_ORE) return PlayerInventory.RAW_GOLD;
        if (brokenBlockType == Blocks.DIAMOND_ORE) return PlayerInventory.DIAMOND;
        if (brokenBlockType == Blocks.EMERALD_ORE) return PlayerInventory.EMERALD;
        if (brokenBlockType == Blocks.LAPIS_ORE) return PlayerInventory.LAPIS_LAZULI;
        if (brokenBlockType == Blocks.REDSTONE_ORE) return PlayerInventory.REDSTONE_DUST;
        if (brokenBlockType == Blocks.COPPER_ORE || brokenBlockType == Blocks.DEEPSLATE_COPPER_ORE) {
            return PlayerInventory.RAW_COPPER;
        }
        if (brokenBlockType == Blocks.DEEPSLATE_COAL_ORE) return PlayerInventory.COAL;
        if (brokenBlockType == Blocks.DEEPSLATE_IRON_ORE) return PlayerInventory.RAW_IRON;
        if (brokenBlockType == Blocks.DEEPSLATE_GOLD_ORE) return PlayerInventory.RAW_GOLD;
        if (brokenBlockType == Blocks.DEEPSLATE_REDSTONE_ORE) return PlayerInventory.REDSTONE_DUST;
        if (brokenBlockType == Blocks.DEEPSLATE_EMERALD_ORE) return PlayerInventory.EMERALD;
        if (brokenBlockType == Blocks.DEEPSLATE_LAPIS_ORE) return PlayerInventory.LAPIS_LAZULI;
        if (brokenBlockType == Blocks.DEEPSLATE_DIAMOND_ORE) return PlayerInventory.DIAMOND;
        if (brokenBlockType == Blocks.CAVE_VINES || brokenBlockType == Blocks.CAVE_VINES_PLANT) return 0;
        // [FURNACE-VARIANT] 점화 중인 제련로는 미점화 원본을 드랍한다(화로 33/34 선례).
        // 변형 표가 세 쌍 전부를 정규화하므로 쌍이 늘어도 이 줄은 그대로다.
        if (com.gameexpert.engine.FurnaceVariant.isLitBlockId(brokenBlockType)) {
            return (short) com.gameexpert.engine.FurnaceVariant.baseBlockId(brokenBlockType);
        }
        // [PRISMARINE] 바다 랜턴은 실크 터치가 없으면 프리즈머린 수정을 떨군다(개수는
        // OreRules.minedDropCount 가 2~3 으로, 행운 상한 5 는 OreRules.dropCountLimit 이 정한다).
        // 프리즈머린 3재질·스펀지 둘은 바닐라대로 자기 자신이라 아래 default 로 빠진다.
        if (brokenBlockType == Blocks.SEA_LANTERN) return PlayerInventory.PRISMARINE_CRYSTALS;
        if (brokenBlockType == Blocks.GLOWSTONE) return PlayerInventory.GLOWSTONE_DUST;
        // [UTILITY] 수박은 실크 터치가 없으면 수박 조각을 떨군다(바닐라 blocks/melon.json).
        if (brokenBlockType == Blocks.MELON) return PlayerInventory.MELON_SLICE;
        if (brokenBlockType >= Blocks.WALL_TORCH_N && brokenBlockType <= Blocks.WALL_TORCH_W) return (short) Blocks.TORCH;
        if (brokenBlockType >= Blocks.COPPER_WALL_TORCH_N
                && brokenBlockType <= Blocks.COPPER_WALL_TORCH_W) {
            return (short) Blocks.COPPER_TORCH;
        }
        return brokenBlockType;
    }

    /**
     * 실제 플레이어 채굴용 드랍. 선택 아이템의 곡괭이 티어를 먼저 검사하고,
     * 자갈·풀은 주입된 난수를 한 번만 굴린다. 독립 다중 풀인 잎은 ItemEntitySystem이 전담한다.
     */
    public static short minedDropFor(short brokenBlockType, short selectedItemType, DoubleSupplier random) {
        return minedDropFor(brokenBlockType, selectedItemType,
                EnchantmentRules.EMPTY_ENCHANTMENTS, random);
    }

    /**
     * [SURV-X] 인챈트 마스크를 함께 받는 채굴 드랍. 섬세한 손길(Silk Touch)은 loot 표 자체를 갈아치우고,
     * 행운(Fortune)은 자갈의 부싯돌 확률표만 여기서 바꾼다(개수 배율은 ItemEntitySystem 이 곱한다).
     */
    public static short minedDropFor(short brokenBlockType, short selectedItemType,
            long enchantments, DoubleSupplier random) {
        if (!BlockEditRules.canHarvest(brokenBlockType, selectedItemType)) {
            return 0;
        }
        if (com.gameexpert.engine.FleshNetherRules.isFlesh(brokenBlockType)) {
            return (short) com.gameexpert.engine.FleshNetherRules.dropItem(brokenBlockType,
                    com.gameexpert.engine.FleshNetherRules.requiresLootRoll(brokenBlockType)
                            ? random.getAsDouble() : 1.0);
        }
        short silk = silkTouchDropFor(brokenBlockType, enchantments);
        if (silk != 0) return silk;
        if (brokenBlockType == Blocks.SNOW_BLOCK) {
            return isShovel(selectedItemType) ? PlayerInventory.SNOWBALL : 0;
        }
        return minedDropIgnoringTool(brokenBlockType, enchantments, random);
    }

    /** 폭발은 도구 수확 게이트 없이 일반 채굴 loot 표만 사용한다. */
    public static short explosionDropFor(short brokenBlockType, DoubleSupplier random) {
        return minedDropIgnoringTool(brokenBlockType,
                EnchantmentRules.EMPTY_ENCHANTMENTS, random);
    }

    /**
     * [SURV-X] 섬세한 손길이 붙은 도구로 캤을 때 블록 자신을 돌려줄 대상인가.
     * 대상이 아니거나 마스크에 섬세한 손길이 없으면 0(=일반 loot 표 사용)이다.
     */
    public static short silkTouchDropFor(short brokenBlockType, long enchantments) {
        if (EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.SILK_TOUCH) <= 0) return 0;
        // [CREAKING] 겉모습 쌍둥이는 <b>비활성 대표 ID</b> 로 접힌다(화로 점화 쌍둥이가 33 으로
        // 접히는 것과 같은 규약). 이 갈래가 아래 일반 self-drop 보다 먼저 와야 활성 하트를
        // 캤을 때 설치 불가능한 ID 가 인벤토리에 들어가지 않는다.
        if (Blocks.isCreakingHeart(brokenBlockType)) {
            return (short) Blocks.creakingHeartItem(brokenBlockType);
        }
        if (Blocks.isInfestedStone(brokenBlockType)) {
            return (short) Blocks.infestedHostBlock(brokenBlockType);
        }
        if (isWoodLeaves(brokenBlockType) || isSilkTouchSelfDrop(brokenBlockType)) {
            return brokenBlockType;
        }
        return 0;
    }

    /** Pinned InfestedBlock spawnAfterBreak enchantment gate. */
    public static boolean spawnsSilverfishWhenMined(int blockType, long enchantments) {
        return Blocks.isInfestedStone(blockType)
                && EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.SILK_TOUCH) <= 0;
    }

    /** 섬세한 손길이 블록 자신을 회수하는 목록(§3). 원래 자기 자신을 드랍하는 블록은 영향이 없다. */
    private static boolean isSilkTouchSelfDrop(short block) {
        if (isOreBlock(block)) return true;
        // [STAINED-GLASS] 색 유리 16색도 무색 유리와 같은 실크 터치 회수 대상이다.
        return block == Blocks.STONE || Blocks.isGlassBlock(block) || block == Blocks.GLOWSTONE
                || block == Blocks.GRAVEL
                || block == Blocks.GRASS || block == Blocks.DEEPSLATE
                || block == Blocks.AMETHYST_CLUSTER
                // [PRISMARINE] 바다 랜턴만 실크 터치로 자기 자신을 회수한다. 스펀지 둘과
                // 프리즈머린 3재질은 원래 자기 자신을 떨궈 목록에 넣을 필요가 없다.
                || block == Blocks.SEA_LANTERN
                // [UTILITY] 수박은 실크 터치로 자기 자신(없으면 수박 조각 3~7)이다.
                || block == Blocks.MELON
                // [DEEP-DARK] 스컬크 5종. 바닐라도 이 다섯만 실크 터치 회수 대상이다.
                || Blocks.isSculkBlock(block)
                // [CREAKING] 크리킹 하트는 실크 터치가 있어야 자기 자신이 나온다 —
                // 없으면 수지 덩어리 1~3 이다 [B] «Creaking Heart». 활성 쌍둥이도 같은
                // 대상이지만 회수되는 아이템은 비활성 대표 ID 다(creakingHeartItem).
                || Blocks.isCreakingHeart(block)
                // [ENDER-SHULKER] 엔더 상자는 실크 터치가 있어야 자기 자신이 나온다 —
                // 없으면 흑요석 8 이다 [A] blocks/ender_chest. 셜커 상자는 실크 터치와
                // 무관하게 언제나 자기 자신이라(내용까지 담고) 이 목록에 넣지 않는다.
                || block == Blocks.ENDER_CHEST
                // [ARCHAEOLOGY] 장식 항아리 8종은 실크 터치가 있어야 <b>항아리 자신</b>이
                // 나온다 — 없으면 넣은 재료 넷이다. [A] loot_table/blocks/decorated_pot.json
                // 은 cracked 로 갈리고 DecoratedPotBlock.playerWillDestroy 가
                // `#prevents_decorated_pot_shattering`(= 실크 터치) 가 없을 때 cracked 를
                // 켜므로, 실전 규칙이 정확히 이 두 갈래다.
                || Blocks.isDecoratedPot(block);
    }

    /** 행운·섬세한 손길·채굴 경험치가 공유하는 광석 블록 판정. */
    public static boolean isOreBlock(int block) {
        return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.NETHER_GOLD_ORE;
    }

    private static short minedDropIgnoringTool(
            short brokenBlockType, long enchantments, DoubleSupplier random) {
        if (brokenBlockType == Blocks.GRAVEL) {
            int permille = EnchantmentRules.gravelFlintChancePermille(
                    EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.FORTUNE));
            return random.getAsDouble() < permille / (double) EnchantmentRules.MILLI
                    ? PlayerInventory.FLINT : (short) Blocks.GRAVEL;
        }
        if (brokenBlockType == Blocks.TALL_GRASS) {
            return random.getAsDouble() < 0.125 ? PlayerInventory.WHEAT_SEEDS : 0;
        }
        // [VOID-END] loot_table/blocks/chorus_plant: set_count uniform(0,1) → NumberProvider.getInt 의
        // Math.round 라 [0.5,1] 이면 후렴과 1개, 아니면 없음(50%).
        if (brokenBlockType == Blocks.CHORUS_PLANT) {
            return random.getAsDouble() >= 0.5 ? PlayerInventory.CHORUS_FRUIT : 0;
        }
        // [ARCHAEOLOGY] 금 가지 않은 장식 항아리는 항아리 자신이다(폭발·맨손). 도구로 부숴 금 간 항아리의
        // 재료 넷은 ItemEntitySystem.spawnMinedBlockDrop 이 먼저 가른다. 두 갈래의 정본은 ArchaeologyRules 다.
        if (Blocks.isDecoratedPot(brokenBlockType)) {
            return ArchaeologyRules.decoratedPotDrop(brokenBlockType, false).itemType();
        }
        // [ARCHAEOLOGY] 의심 블록을 붓 대신 삽·곡괭이로 부수면 <b>아무것도 나오지 않는다</b>
        // — [A] loot_table/blocks/suspicious_sand.json 에 pools 가 아예 없다(파묻힌 것이
        // 함께 사라진다). 붓질 배출물은 이 표가 아니라 ArchaeologyRules 의 전용 표가 낸다.
        if (Blocks.isBrushable(brokenBlockType)) return 0;
        return dropFor(brokenBlockType);
    }

    /** 독립 묘목·막대·사과 풀과 가위 self-drop을 쓰는 나뭇잎인가. */
    public static boolean isWoodLeaves(short block) {
        return block == Blocks.LEAVES || block == Blocks.BIRCH_LEAVES || isRuntimeWoodLeaves(block);
    }

    private static boolean isRuntimeWoodLeaves(short block) {
        return block == Blocks.SPRUCE_LEAVES || block == Blocks.JUNGLE_LEAVES
                || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES
                || block == Blocks.CHERRY_LEAVES || block == Blocks.MANGROVE_LEAVES
                || block == Blocks.AZALEA_LEAVES || block == Blocks.FLOWERING_AZALEA_LEAVES
                // [PALE-GARDEN] 창백한 참나무 잎도 묘목 5% · 막대 2% · 가위 self-drop 이
                // 다른 수종과 같은 표다(핀 §5c).
                || block == Blocks.PALE_OAK_LEAVES
                // [POPLAR] 포플러 잎 세 변종도 묘목 5% · 막대 2% · 가위 self-drop 이
                // 같은 표다([B] 위키 «Poplar Leaves»).
                || Blocks.isPoplarLeaves(block);
    }

    /** 나뭇잎 종에 대응하는 묘목/주아 아이템. 나뭇잎이 아니면 0. */
    public static short saplingForLeaves(short block) {
        if (block == Blocks.LEAVES) return PlayerInventory.OAK_SAPLING;
        if (block == Blocks.BIRCH_LEAVES) return PlayerInventory.BIRCH_SAPLING;
        if (block == Blocks.SPRUCE_LEAVES) return (short) Blocks.SPRUCE_SAPLING;
        if (block == Blocks.JUNGLE_LEAVES) return (short) Blocks.JUNGLE_SAPLING;
        if (block == Blocks.ACACIA_LEAVES) return (short) Blocks.ACACIA_SAPLING;
        if (block == Blocks.DARK_OAK_LEAVES) return (short) Blocks.DARK_OAK_SAPLING;
        if (block == Blocks.CHERRY_LEAVES) return (short) Blocks.CHERRY_SAPLING;
        if (block == Blocks.MANGROVE_LEAVES) return (short) Blocks.MANGROVE_PROPAGULE;
        if (block == Blocks.AZALEA_LEAVES) return (short) Blocks.AZALEA;
        if (block == Blocks.FLOWERING_AZALEA_LEAVES) return (short) Blocks.FLOWERING_AZALEA;
        if (block == Blocks.PALE_OAK_LEAVES) return (short) Blocks.PALE_OAK_SAPLING;
        // [POPLAR] 잎 변종이 셋이지만 묘목은 하나뿐이다 — 바닐라도 묘목이 자랄 때 세 변종
        // 중 하나를 고르는 쪽이라, 어느 색 잎에서 떨어져도 같은 포플러 묘목이다 [B].
        if (Blocks.isPoplarLeaves(block)) return (short) Blocks.POPLAR_SAPLING;
        return 0;
    }

    /** 참나무·짙은 참나무 잎만 독립 사과 풀을 가진다. */
    public static boolean leavesDropApple(short block) {
        return block == Blocks.LEAVES || block == Blocks.DARK_OAK_LEAVES;
    }

    private static boolean isShovel(short item) {
        return item == PlayerInventory.SHOVEL || item == PlayerInventory.STONE_SHOVEL
                || item == PlayerInventory.IRON_SHOVEL || item == PlayerInventory.GOLD_SHOVEL
                || item == PlayerInventory.DIAMOND_SHOVEL;
    }

    /**
     * [SURV-H] 식량 1개의 허기 회복량(바닐라 nutrition, 0~20). 음식이 아니면 0.
     * 바닐라 값 그대로이며 정적판 {@code ui/items.ts}의 {@code FOOD_NUTRITION}과 파리티 게이트로 묶여 있다.
     */
    public static int foodNutrition(short itemType) {
        if (itemType == PlayerInventory.MYSTERY_FLESH) return foodNutrition(PlayerInventory.ROTTEN_FLESH);
        // [ZOMBIE-ANIMAL] 상한 달걀은 썩은 살점과 **같은 정본 상수**를 참조한다. 수치를
        // 사본으로 적으면 썩은 살점 값이 바뀔 때 조용히 갈라지므로 여기서 되물어본다.
        if (itemType == PlayerInventory.SPOILED_EGG) {
            return foodNutrition(PlayerInventory.ROTTEN_FLESH);
        }
        if (itemType == PlayerInventory.POTATO || itemType == PlayerInventory.BEETROOT
                // [COOKING] 말린 다시마 1 / 0.6. 바닐라 Java 값이다(베드락은 5 / 0.6 이라 다르다).
                || itemType == PlayerInventory.DRIED_KELP) {
            return 1;
        }
        if (itemType == PlayerInventory.TROPICAL_FISH || itemType == PlayerInventory.PUFFERFISH) {
            return 1;
        }
        if (itemType == PlayerInventory.CHICKEN_RAW || itemType == PlayerInventory.MUTTON_RAW
                || itemType == PlayerInventory.SPIDER_EYE || itemType == PlayerInventory.GLOW_BERRIES
                // [CROP-BERRY] 독 감자 2 / 1.2([A] Foods.POISONOUS_POTATO).
                || itemType == PlayerInventory.POISONOUS_POTATO
                || itemType == PlayerInventory.COD_RAW || itemType == PlayerInventory.SALMON_RAW
                || itemType == PlayerInventory.SWEET_BERRIES
                // [UTILITY] 수박 조각 2 / 1.2(Foods.MELON_SLICE nutrition 2 · saturation 0.3).
                || itemType == PlayerInventory.MELON_SLICE
                // [COOKING] 쿠키 2 / 0.4. **케이크는 이 표에 없다** — 바닐라에서도 손에 든
                // 케이크는 먹을 수 없고 설치한 블록을 먹는다. 그 값은 CakeRules 가 소유한다.
                || itemType == PlayerInventory.COOKIE) {
            return 2;
        }
        if (itemType == PlayerInventory.BEEF_RAW || itemType == PlayerInventory.PORK_RAW
                || itemType == PlayerInventory.CARROT || itemType == PlayerInventory.RABBIT_RAW) {
            return 3;
        }
        if (itemType == PlayerInventory.ROTTEN_FLESH || itemType == PlayerInventory.APPLE
                || itemType == PlayerInventory.GOLDEN_APPLE
                // [GOLD-FOOD] 마법이 부여된 황금 사과 4 / 9.6 · 후렴과 4 / 2.4([A] 바닐라 값).
                || itemType == PlayerInventory.ENCHANTED_GOLDEN_APPLE
                || itemType == PlayerInventory.CHORUS_FRUIT) {
            return 4;
        }
        if (itemType == PlayerInventory.BREAD || itemType == PlayerInventory.BAKED_POTATO
                || itemType == PlayerInventory.COD_COOKED || itemType == PlayerInventory.RABBIT_COOKED) {
            return 5;
        }
        if (itemType == PlayerInventory.CHICKEN_COOKED || itemType == PlayerInventory.MUTTON_COOKED
                || itemType == PlayerInventory.SALMON_COOKED || itemType == PlayerInventory.GOLDEN_CARROT
                || itemType == PlayerInventory.MUSHROOM_STEW || itemType == PlayerInventory.BEETROOT_SOUP
                // [COOKING] 수상한 스튜는 꽃과 무관하게 버섯 스튜와 같은 6 / 7.2 다.
                || itemType == PlayerInventory.SUSPICIOUS_STEW_POPPY
                || itemType == PlayerInventory.SUSPICIOUS_STEW_DANDELION
                // [GOLD-FOOD] 꿀이 든 병 6 / 1.2. 바닐라에서 <b>허기를 채우는 유일한 음료</b>다.
                || itemType == PlayerInventory.HONEY_BOTTLE) {
            return 6;
        }
        if (itemType == PlayerInventory.BEEF_COOKED || itemType == PlayerInventory.PORK_COOKED
                // [COOKING] 호박 파이 8 / 4.8.
                || itemType == PlayerInventory.PUMPKIN_PIE) {
            return 8;
        }
        if (itemType == PlayerInventory.RABBIT_STEW) {
            return 10;
        }
        return 0;
    }

    /**
     * [SURV-H] 식량 1개의 saturation 회복량(1/1000 단위 = {@code HungerRules.MILLI}).
     * 바닐라 값은 {@code nutrition * saturationModifier * 2}이며, 모두 0.2의 배수라 milli 정수로 손실 없이 담긴다.
     */
    public static int foodSaturationMilli(short itemType) {
        // [UTILITY] 수박 조각: 2 × 0.3 × 2 = 1.2.
        if (itemType == PlayerInventory.MELON_SLICE) {
            return 1200;
        }
        if (itemType == PlayerInventory.MYSTERY_FLESH) return foodSaturationMilli(PlayerInventory.ROTTEN_FLESH);
        // [ZOMBIE-ANIMAL] 상한 달걀은 썩은 살점과 같은 정본 상수를 참조한다(사본 금지).
        if (itemType == PlayerInventory.SPOILED_EGG) {
            return foodSaturationMilli(PlayerInventory.ROTTEN_FLESH);
        }
        if (itemType == PlayerInventory.GLOW_BERRIES || itemType == PlayerInventory.COD_RAW
                || itemType == PlayerInventory.SALMON_RAW || itemType == PlayerInventory.SWEET_BERRIES
                // [COOKING] 쿠키 0.4. 케이크 한 입도 같은 0.4 지만 손에 든 케이크는 먹을 수
                // 없으므로 이 표가 아니라 CakeRules 가 그 값을 소유한다.
                || itemType == PlayerInventory.COOKIE) {
            return 400; // 0.4
        }
        if (itemType == PlayerInventory.TROPICAL_FISH || itemType == PlayerInventory.PUFFERFISH) {
            return 200; // 0.2
        }
        if (itemType == PlayerInventory.POTATO
                // [COOKING] 말린 다시마 0.6(감자와 같은 값이라 같은 항에 둔다).
                || itemType == PlayerInventory.DRIED_KELP) {
            return 600; // 0.6
        }
        if (itemType == PlayerInventory.ROTTEN_FLESH) {
            return 800; // 0.8
        }
        if (itemType == PlayerInventory.CHICKEN_RAW || itemType == PlayerInventory.MUTTON_RAW
                || itemType == PlayerInventory.BEETROOT
                // [CROP-BERRY] 독 감자 2 × 0.3 × 2 = 1.2.
                || itemType == PlayerInventory.POISONOUS_POTATO
                // [GOLD-FOOD] 꿀이 든 병 6 × 0.1 × 2 = 1.2.
                || itemType == PlayerInventory.HONEY_BOTTLE) {
            return 1_200; // 1.2
        }
        if (itemType == PlayerInventory.BEEF_RAW || itemType == PlayerInventory.PORK_RAW
                || itemType == PlayerInventory.RABBIT_RAW) {
            return 1_800; // 1.8
        }
        if (itemType == PlayerInventory.APPLE
                // [GOLD-FOOD] 후렴과 4 × 0.3 × 2 = 2.4(사과와 같은 값이라 같은 항에 둔다).
                || itemType == PlayerInventory.CHORUS_FRUIT) {
            return 2_400; // 2.4
        }
        if (itemType == PlayerInventory.SPIDER_EYE) {
            return 3_200; // 3.2
        }
        if (itemType == PlayerInventory.CARROT) {
            return 3_600; // 3.6
        }
        if (itemType == PlayerInventory.BREAD || itemType == PlayerInventory.BAKED_POTATO
                || itemType == PlayerInventory.COD_COOKED || itemType == PlayerInventory.RABBIT_COOKED) {
            return 6_000; // 6.0
        }
        if (itemType == PlayerInventory.CHICKEN_COOKED || itemType == PlayerInventory.MUSHROOM_STEW
                || itemType == PlayerInventory.BEETROOT_SOUP
                // [COOKING] 수상한 스튜도 버섯 스튜와 같은 7.2 다.
                || itemType == PlayerInventory.SUSPICIOUS_STEW_POPPY
                || itemType == PlayerInventory.SUSPICIOUS_STEW_DANDELION) {
            return 7_200; // 7.2
        }
        if (itemType == PlayerInventory.MUTTON_COOKED || itemType == PlayerInventory.SALMON_COOKED
                || itemType == PlayerInventory.GOLDEN_APPLE
                // [GOLD-FOOD] 마법이 부여된 황금 사과도 황금사과와 같은 4 / 9.6 이다.
                || itemType == PlayerInventory.ENCHANTED_GOLDEN_APPLE) {
            return 9_600; // 9.6
        }
        // [COOKING] 호박 파이 4.8. 사과(2.4)와 거미 눈(3.2) 사이라 자기 항을 만든다.
        if (itemType == PlayerInventory.PUMPKIN_PIE) {
            return 4_800; // 4.8
        }
        if (itemType == PlayerInventory.RABBIT_STEW) {
            return 12_000; // 12.0
        }
        if (itemType == PlayerInventory.BEEF_COOKED || itemType == PlayerInventory.PORK_COOKED) {
            return 12_800; // 12.8
        }
        if (itemType == PlayerInventory.GOLDEN_CARROT) {
            return 14_400; // 14.4
        }
        return 0;
    }

    /**
     * [COOKING] 바닐라 {@code crafting_remaining_item}. 제작에서 이 재료가 소모될 때 같은 칸에
     * 남는 아이템이며, 없으면 {@link PlayerInventory#EMPTY} 다. 정적판
     * {@code StandaloneCrafting} 이 같은 표를 읽는다.
     *
     * <p>케이크의 우유 양동이는 빈 양동이, 꿀 블록의 꿀이 든 병은 유리병을 반환한다.
     * 병은 쌓을 수 있으므로 입력 스택이 남아 있어도 제작마다 하나씩 반환한다.
     */
    public static short craftingRemainder(short itemType) {
        return itemType == PlayerInventory.MILK_BUCKET
                ? PlayerInventory.BUCKET
                : itemType == PlayerInventory.HONEY_BOTTLE ? PlayerInventory.GLASS_BOTTLE
                : PlayerInventory.EMPTY;
    }

    /** 소비된 식량 1개의 허기·saturation 회복량(정적판 {@code StandaloneConsumedFood} 대응). */
    public record ConsumedFood(PlayerInventory.StackSnapshot stack,
            int nutrition, int saturationMilli) {
        public ConsumedFood {
            if (stack == null || stack.isEmpty()) {
                throw new IllegalArgumentException("consumed food stack is required");
            }
        }
        public short itemType() { return stack.itemType(); }
        public String itemComponentData() { return stack.itemComponentData(); }
    }

    // ── [CROP-BERRY] 섭취 부작용 계약 ────────────────────────────────────────────
    //
    // 바닐라 {@code FoodProperties} 의 {@code effect(MobEffectInstance, probability)} 항이다.
    // 지금까지 이 저장소의 음식은 허기/포화만 주었고 부작용 항이 아예 없었다 — 독 감자가 그
    // 첫 항이라 계약을 여기 하나 세운다. 정적판 {@code items.ts} 의 {@code foodSideEffect} 와
    // 파리티 게이트로 묶인다.
    //
    // 지속시간 단위는 <b>10 TPS 서버 틱</b>이다(바닐라 100 MC 틱 = 5초 = 이 프로젝트 50 틱).
    // 확률은 부동소수 대신 분자/분모 정수라 두 권위가 같은 {@code nextInt(분모) < 분자} 를 쓴다.

    /** [CROP-BERRY] 독 감자 독 I 지속(10 TPS 서버 틱). 바닐라 100 MC 틱(5초). */
    public static final int POISONOUS_POTATO_POISON_DURATION_TICKS = 50;
    /** [CROP-BERRY] 바닐라 probability 0.6 = 3/5. */
    public static final int POISONOUS_POTATO_POISON_NUMERATOR = 3;
    public static final int POISONOUS_POTATO_POISON_DENOMINATOR = 5;

    /**
     * 섭취 시 확률로 붙는 상태이상. 없으면 {@code null}.
     *
     * <p>divergence: 바닐라 거미 눈(독 I 5초, 확률 1.0)은 이 표에 넣지 않았다 — 거미 눈은 이
     * 트랙 배정 밖 음식이라 여기서 계약을 바꾸면 남의 줄을 움직이게 된다. 보고서에만 결손으로
     * 남긴다.
     */
    public record FoodSideEffect(com.gameexpert.engine.effect.StatusEffect effect, int amplifier,
            int durationTicks, int chanceNumerator, int chanceDenominator) {
    }

    public static FoodSideEffect foodSideEffect(short itemType) {
        if (itemType == PlayerInventory.POISONOUS_POTATO) {
            return new FoodSideEffect(com.gameexpert.engine.effect.StatusEffect.POISON, 0,
                    POISONOUS_POTATO_POISON_DURATION_TICKS,
                    POISONOUS_POTATO_POISON_NUMERATOR, POISONOUS_POTATO_POISON_DENOMINATOR);
        }
        return null;
    }

    /**
     * 굴림 하나로 부작용이 실제로 붙는가.
     *
     * <p>퇴비 선례({@code ComposterRules.levelAfterInsert(…, roll)})와 같은 꼴로 <b>난수를
     * 인자로 주입</b>한다 — 호출부가 {@code Random} 을 직접 굴리면 정적판과 굴림당 결과를
     * 대조할 수 있는 표면이 없어지기 때문이다. 판정식은 정수 분자/분모 계약을 그대로 둔
     * {@code (int)(roll × 분모) < 분자} 라 이전의 {@code nextInt(분모) < 분자} 와 분포가
     * 같다(독 감자는 3/5 = 60%). 정적판 {@code items.ts::foodSideEffectApplies} 짝이다.
     *
     * <p>표 조회를 이 안으로 넣지 않은 이유는 <b>난수 소비 규율</b> 때문이다: 호출부가
     * {@code foodSideEffect(...) != null} 로 먼저 걸러 부작용이 있는 음식에서만 굴림을
     * 당기게 해야 이 난수열의 위상이 일반 식사에 밀리지 않는다.
     *
     * @param roll {@code [0,1)} 균등 난수
     */
    public static boolean foodSideEffectApplies(FoodSideEffect sideEffect, double roll) {
        return (int) (roll * sideEffect.chanceDenominator()) < sideEffect.chanceNumerator();
    }

    /**
     * 선택 슬롯이 일반 식량이면 1개를 소비하고 허기 회복량을 돌려준다(§10.2-S2a).
     * 황금사과는 별도 효과 경로({@code consumeSelectedGoldenApple})라 여기서 거부한다.
     * 음식이 아니거나 개수가 없거나 그릇 음식을 겹쳐 든 경우 아무것도 바꾸지 않고 null.
     */
    public static ConsumedFood consumeSelectedFood(PlayerInventory inv) {
        return consumeFood(inv, inv.capture(PlayerInventory.Hand.MAIN));
    }

    /** 캡처된 원래 손의 일반 식량만 소비합니다. 선택 슬롯이 바뀌어도 다른 칸을 건드리지 않습니다. */
    public static ConsumedFood consumeFood(
            PlayerInventory inv, PlayerInventory.HandRef hand) {
        if (inv == null || hand == null) return null;
        PlayerInventory.StackSnapshot stack;
        try {
            stack = inv.stack(hand);
        } catch (IllegalArgumentException malformedStack) {
            return null;
        }
        short type = stack.itemType();
        if (type == PlayerInventory.GOLDEN_APPLE) return null;
        // [GOLD-FOOD] 전용 효과 경로가 있는 셋도 여기서 거부한다 — 이 경로로 새면 효과 없이
        // 허기만 채워지고(마법 사과·후렴과) 빈 유리병도 돌아오지 않는다(꿀이 든 병).
        if (hasDedicatedConsumePath(type)) return null;
        int nutrition = foodNutrition(type);
        // [COOKING] 그릇 음식 판정은 PlayerInventory 가 소유한다 — 수상한 스튜가 합류하면서
        // 같은 목록을 두 군데에 사본으로 두면 조용히 갈리기 때문이다.
        boolean bowlFood = PlayerInventory.isBowlFood(type);
        if (nutrition <= 0 || stack.count() <= 0 || (bowlFood && stack.count() != 1)) {
            return null;
        }
        // ARCH stew components must resolve before the inventory mutation/ACK boundary. Crafted
        // poppy and dandelion stews intentionally have no component and retain their ID defaults.
        if (PlayerInventory.isSuspiciousStew(type)) {
            SuspiciousStewRules.effectOf(stack);
            SuspiciousStewRules.effectMcTicks(stack);
        }
        if (bowlFood) {
            if (!inv.replaceSingle(hand, type, PlayerInventory.BOWL)) return null;
        } else {
            if (!inv.consumeOne(hand, type)) return null;
        }
        return new ConsumedFood(stack, nutrition, foodSaturationMilli(type));
    }

    /** 선택 슬롯의 황금사과를 하나 소비한다. 효과 부여는 PlayerTickState가 담당한다. */
    public static boolean consumeSelectedGoldenApple(PlayerInventory inv) {
        return consumeGoldenApple(inv, inv.capture(PlayerInventory.Hand.MAIN));
    }

    public static boolean consumeGoldenApple(
            PlayerInventory inv, PlayerInventory.HandRef hand) {
        return inv.consumeOne(hand, PlayerInventory.GOLDEN_APPLE);
    }

    /**
     * [GOLD-FOOD] 일반 식량 경로({@link #consumeSelectedFood})가 건드리면 안 되는 아이템인가.
     * 셋 다 <b>음식 표에는 있지만</b> 전용 소비 경로가 효과·용기 반환을 함께 처리한다.
     */
    public static boolean hasDedicatedConsumePath(short type) {
        return type == PlayerInventory.ENCHANTED_GOLDEN_APPLE
                || type == PlayerInventory.CHORUS_FRUIT
                || type == PlayerInventory.HONEY_BOTTLE;
    }

    /**
     * [GOLD-FOOD] 선택 슬롯의 꿀이 든 병 하나를 마시고 <b>같은 칸에 빈 유리병을 돌려준다</b>.
     * 물약 경로와 같은 계약이며, 스택이 2개 이상이면 바닐라와 같이 나머지를 그대로 두고
     * 유리병은 인벤토리의 다른 자리로 간다(자리가 없으면 마시지 못한다).
     */
    public static boolean consumeSelectedHoneyBottle(PlayerInventory inv) {
        return consumeHoneyBottle(inv, inv.capture(PlayerInventory.Hand.MAIN));
    }

    public static boolean consumeHoneyBottle(
            PlayerInventory inv, PlayerInventory.HandRef hand) {
        PlayerInventory.StackSnapshot before = inv.stack(hand);
        if (before.itemType() != PlayerInventory.HONEY_BOTTLE || before.count() <= 0) return false;
        if (before.count() == 1) {
            return inv.replaceSingle(hand, PlayerInventory.HONEY_BOTTLE,
                    PlayerInventory.GLASS_BOTTLE);
        }
        if (!inv.consumeOne(hand, PlayerInventory.HONEY_BOTTLE)) return false;
        if (inv.addItem(PlayerInventory.GLASS_BOTTLE, 1) == 1) return true;
        // 빈 병 자리가 없으면 마신 것 자체를 되돌린다 — 병을 조용히 삭제하지 않는다.
        inv.setStack(hand, before);
        return false;
    }

    /**
     * 선택 슬롯이 보트 아이템이면 1개를 소모합니다(보트 설치 = 엔티티 스폰). 배치 검증(리치·물)은 클라 권위라
     * 서버는 소모만 담당합니다. 보트가 아니거나 개수가 없으면 아무것도 바꾸지 않고 false.
     * @return 소모했으면 true(인벤 변경), 아니면 false(설치 거부).
     */
    public static boolean consumeSelectedBoat(PlayerInventory inv) {
        int slot = inv.selectedSlot();
        if (inv.itemType(slot) != PlayerInventory.BOAT || inv.count(slot) <= 0) {
            return false;
        }
        try {
            return inv.set(slot, PlayerInventory.BOAT, inv.count(slot) - 1);
        } catch (IllegalStateException rejected) {
            return false;
        }
    }

    /**
     * 설치할 blockType 에 필요한 인벤토리 아이템 ID. 방향 전용 통나무는 기본 통나무로,
     * 벽토치(52~55)는 토치 아이템(17)로 환산해 소비·보유를 판정한다.
     */
    public static short placeItemFor(short blockType) {
        if (blockType == Blocks.REDSTONE_WIRE) return PlayerInventory.REDSTONE_DUST;
        if (blockType == Blocks.TRIPWIRE) return PlayerInventory.STRING;
        if (blockType == Blocks.TRIPWIRE_HOOK_BLOCK) return PlayerInventory.TRIPWIRE_HOOK;
        if (blockType == Blocks.REDSTONE_TORCH_OFF || blockType == Blocks.REDSTONE_WALL_TORCH || blockType == Blocks.REDSTONE_WALL_TORCH_OFF) return (short) Blocks.REDSTONE_TORCH;
        if (blockType == Blocks.REDSTONE_LAMP_LIT) return (short) Blocks.REDSTONE_LAMP;

        int logItem = BlockFamilies.woodLogItem(Short.toUnsignedInt(blockType));
        if (logItem != Blocks.AIR) return (short) logItem;
        // [QUARTZ] 석영 기둥 축 변형도 총칭 기둥 아이템 하나로 설치·소비를 판정한다.
        int pillarItem = BlockFamilies.quartzPillarItem(Short.toUnsignedInt(blockType));
        if (pillarItem != Blocks.AIR) return (short) pillarItem;
        // [PROP-MATERIAL] 사슬 축 변형도 총칭 사슬 아이템 하나로 설치·소비를 판정한다.
        int chainPlaceItem = BlockFamilies.chainItem(Short.toUnsignedInt(blockType));
        if (chainPlaceItem != Blocks.AIR) return (short) chainPlaceItem;
        // [COPPER-CHAIN] 구리 사슬도 축만 접은 대표 ID 하나로 설치·소비를 판정한다.
        int copperChainPlaceItem = BlockFamilies.copperChainItem(Short.toUnsignedInt(blockType));
        if (copperChainPlaceItem != Blocks.AIR) return (short) copperChainPlaceItem;
        // [COPPER] 점등 전구는 소등 아이템 하나로 설치·소비를 판정한다(점화 화로와 같다).
        int bulbItem = Blocks.copperBulbUnlit(Short.toUnsignedInt(blockType));
        if (bulbItem != Short.toUnsignedInt(blockType)) return (short) bulbItem;
        if (blockType >= Blocks.WALL_TORCH_N && blockType <= Blocks.WALL_TORCH_W) return (short) Blocks.TORCH;
        if (blockType >= Blocks.COPPER_WALL_TORCH_N
                && blockType <= Blocks.COPPER_WALL_TORCH_W) {
            return (short) Blocks.COPPER_TORCH;
        }
        if (blockType == Blocks.CAVE_VINES) return PlayerInventory.GLOW_BERRIES;
        return blockType;
    }

    /** 선택 슬롯이 blockType 설치에 필요한 아이템을 1개 이상 들고 있는가(검·도구·빈 칸은 설치 불가). */
    public static boolean canPlaceSelected(PlayerInventory inv, short blockType) {
        return canPlace(inv, inv.capture(PlayerInventory.Hand.MAIN), blockType);
    }

    private static boolean canPlace(PlayerInventory inv, PlayerInventory.HandRef hand,
            short blockType) {
        short need = placeItemFor(blockType);
        if (!BlockPlacementRules.isPlaceable(blockType)
                || need == PlayerInventory.EMPTY || PlayerInventory.isDurable(need)) return false;
        PlayerInventory.StackSnapshot stack = inv.stack(hand);
        return stack.itemType() == need && stack.count() >= 1;
    }

    /**
     * 설치 성공 시 선택 슬롯에서 필요한 아이템 1개를 차감합니다(벽토치는 토치 아이템 17 차감).
     * @return 보유하고 있어 차감했으면 true, 없으면 false(설치 거부).
     */
    public static boolean consumeForPlace(PlayerInventory inv, short blockType) {
        return consumeForPlace(inv, inv.capture(PlayerInventory.Hand.MAIN), blockType);
    }

    /** 설치를 시작한 손/주손 슬롯을 고정하여 지연 확정에서도 같은 스택만 소비합니다. */
    public static boolean consumeForPlace(PlayerInventory inv, PlayerInventory.HandRef hand,
            short blockType) {
        if (inv == null || hand == null || !canPlace(inv, hand, blockType)) return false;
        if (blockType == Blocks.LILY_PAD) {
            // 실제 차감은 WorldRuntime FluidWorld.setBlock이 아래 물 소스를 확인한 뒤 수행한다.
            // 이전 호출이 FluidSimulator의 no-op 경로에서 끝났더라도 다음 요청에 누수되지 않게 덮어쓴다.
            PENDING_LILY_PLACE.set(new PendingLilyPlace(inv, hand));
            return true;
        }
        // 이전 수련잎 요청이 FluidSimulator no-op 등으로 setBlock까지 도달하지 못했어도 누수시키지 않는다.
        PENDING_LILY_PLACE.remove();
        return inv.consumeOne(hand, placeItemFor(blockType));
    }

    /** 지원 검증이 끝난 수련잎 설치의 보류 소비를 확정한다. 틱 스레드 밖/불일치 호출은 무변경 거부. */
    public static boolean commitPendingLilyPlace() {
        PendingLilyPlace pending = PENDING_LILY_PLACE.get();
        PENDING_LILY_PLACE.remove();
        if (pending == null
                || !canPlace(pending.inventory, pending.hand, (short) Blocks.LILY_PAD)) {
            return false;
        }
        return pending.inventory.consumeOne(pending.hand, (short) Blocks.LILY_PAD);
    }

    /** 지원 검증 실패/no-op 뒤 보류 상태를 폐기한다. 인벤토리는 건드리지 않는다. */
    public static void cancelPendingLilyPlace() {
        PENDING_LILY_PLACE.remove();
    }

    /**
     * 곡괭이·도끼·삽(도구)인가. 각 티어의 검은 제외된다(명중으로만 마모).
     */
    public static boolean isTool(short type) {
        return (type >= PlayerInventory.PICKAXE && type <= PlayerInventory.SHOVEL)
                || (type >= PlayerInventory.STONE_TIER_MIN && type < PlayerInventory.STONE_TIER_MAX)
                || (type >= PlayerInventory.IRON_TIER_MIN && type < PlayerInventory.IRON_TIER_MAX)
                || (type >= PlayerInventory.GOLD_TIER_MIN && type <= PlayerInventory.GOLD_MINING_TOOL_MAX)
                || (type >= PlayerInventory.DIAMOND_TIER_MIN && type <= PlayerInventory.DIAMOND_MINING_TOOL_MAX)
                || (type >= PlayerInventory.COPPER_PICKAXE && type <= PlayerInventory.COPPER_SHOVEL)
                || (type >= PlayerInventory.NETHERITE_PICKAXE && type <= PlayerInventory.NETHERITE_SHOVEL)
                || type == (short) Blocks.SHEARS;
    }

    /** 검인가 — 나무·돌·철·금·다이아 티어(§10.2-S2b). */
    public static boolean isSword(short type) {
        return type == PlayerInventory.SWORD_ITEM
                || type == PlayerInventory.STONE_TIER_MAX
                || type == PlayerInventory.IRON_TIER_MAX
                || type == PlayerInventory.GOLD_SWORD
                || type == PlayerInventory.DIAMOND_SWORD
                || type == PlayerInventory.COPPER_SWORD
                || type == PlayerInventory.NETHERITE_SWORD;
    }

    /**
     * 채굴 완료 시 선택 슬롯이 도구(곡괭이·도끼·삽, 전 티어)면 내구도를 1 깎습니다(0 이면 소멸).
     * @return 도구라서 마모가 일어났으면 true(인벤 변경), 그 외(검·블록·맨손)면 false.
     */
    public static boolean wearSelectedOnMine(PlayerInventory inv) {
        int slot = inv.selectedSlot();
        if (isTool(inv.itemType(slot))) {
            inv.degrade(slot);
            return true;
        }
        return false;
    }

    /**
     * 몹 명중 시 선택 슬롯이 검이면 내구도를 1 깎습니다(0 이면 소멸).
     * @return 검이라서 마모가 일어났으면 true(인벤 변경), 그 외면 false.
     */
    public static boolean wearSelectedOnHit(PlayerInventory inv) {
        int slot = inv.selectedSlot();
        if (isSword(inv.itemType(slot))) {
            inv.degrade(slot);
            return true;
        }
        return false;
    }
}
