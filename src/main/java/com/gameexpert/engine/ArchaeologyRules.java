package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * [ARCHAEOLOGY] 고고학(해안판) 규칙 정본 — 붓질 상태기계 · 전용 전리품표 · 장식 항아리.
 *
 * <p><b>전부 순수 함수</b>다 — 월드도 좌표도 난수원도 받지 않는다. 난수는 호출자가 굴린
 * [0,1) 값을 넘겨 각 권위가 자기 프리픽스를 그대로 유지한다.
 *
 * <p>client {@code world/ArchaeologyRules.ts} 와 <b>함수 하나하나가 같은 값을 내야 한다</b>.
 * 두 권위가 각자 판정을 복제하면 한쪽만 고쳐졌을 때 같은 붓질이 서로 다른 조각을 낸다.
 *
 * <p>발췌 핀은 {@code docs/research/mc-archaeology-1214.md} 다.
 */
public final class ArchaeologyRules {

    private ArchaeologyRules() {
    }

    // ── 붓질 타이밍([A] 1.21.4 → 10 TPS 환산) ────────────────────────────────
    //
    // [A] {@code BrushItem.onUseTick} 은 사용 시작 후 {@code i % 10 == 5} 인 틱마다 한 획을
    // 긋고(i 는 1 부터 세는 경과 틱), {@code BrushableBlockEntity.BRUSH_COOLDOWN_TICKS = 10}
    // 이 그 간격을 다시 강제한다. {@code REQUIRED_BRUSHES_TO_BREAK = 10} 획이면 열린다.
    // 첫 획 6 틱 + 9×10 = 96 MC tick = 4.8 초이고, [B] 위키 «Brush» 의
    // "96 game ticks (4.8 seconds)" 와 같은 값이다.

    /** [A] 한 획 사이의 MC 틱. */
    public static final int BRUSH_STROKE_MC_TICKS = 10;
    /** [A] 사용 시작부터 첫 획까지의 MC 틱. */
    public static final int BRUSH_FIRST_STROKE_MC_TICKS = 6;
    /** [A]+[B] 의심 블록 하나를 다 파내는 데 드는 MC 틱(96). */
    public static final int BRUSH_TOTAL_MC_TICKS =
            BRUSH_FIRST_STROKE_MC_TICKS
                    + (Blocks.BRUSH_STROKES_TO_BREAK - 1) * BRUSH_STROKE_MC_TICKS;
    /** 10 TPS 권위 틱으로 환산한 획 간격(5). */
    public static final int BRUSH_STROKE_AUTHORITY_TICKS = BRUSH_STROKE_MC_TICKS / 2;
    /** 10 TPS 권위 틱으로 환산한 첫 획까지의 지연(3). */
    public static final int BRUSH_FIRST_STROKE_AUTHORITY_TICKS = BRUSH_FIRST_STROKE_MC_TICKS / 2;

    /**
     * [B] 위키 «Brush»: "depleting 1 durability point on the brush" — 의심 블록 <b>하나를
     * 다 파낼 때</b> 1 만 깎는다(획마다가 아니다). 아르마딜로 솔질의 16 과 다른 값이며,
     * 그 16 은 {@link MobSystem} 이 이미 소유하고 있다.
     */
    public static final int BRUSH_BLOCK_DURABILITY_COST = 1;

    /** 붓질 한 획의 결과. 완료면 블록이 열리고 전리품이 한 번 굴러 배출된다. */
    public record BrushStroke(boolean brushed, int blockType, int state, boolean completed) {
    }

    /**
     * 의심 블록에 한 획을 긋는다. 상태 바이트에 실린 획 수를 하나 올리고, [A]
     * {@code REQUIRED_BRUSHES_TO_BREAK} 에 닿으면 일반 블록으로 바꾼다.
     *
     * <p>진행도가 <b>블록 상태</b>에 살아 있으므로 청크 언로드 · 재접속을 넘어 보존된다.
     * [C] 바닐라의 {@code BRUSH_RESET_TICKS = 40}(2 초 손을 떼면 한 단계씩 되감김)은 옮기지
     * 않았다 — 이 저장소에는 블록 엔티티도 scheduled tick 어휘도 없어 좌표별 타이머를 둘
     * 곳이 없고, 되감기가 없는 쪽은 플레이어에게 불리하지 않은 방향이라 안전하다.
     */
    public static BrushStroke brushStroke(int blockType, int state) {
        if (!Blocks.isBrushable(blockType)) {
            return new BrushStroke(false, blockType, state, false);
        }
        int strokes = (state & 0x0f) + 1;
        if (strokes >= Blocks.BRUSH_STROKES_TO_BREAK) {
            return new BrushStroke(true, Blocks.brushedInto(blockType), 0, true);
        }
        return new BrushStroke(true, blockType, strokes, false);
    }

    // ── 전용 전리품표([A] archaeology/ocean_ruin_warm · ocean_ruin_cold) ──────

    /** 가중 전리품 한 칸. {@code itemType == Blocks.AIR} 는 어휘가 없는 칸이다. */
    public record LootEntry(short itemType, int weight) {
    }

    /**
     * [A] {@code loot_table/archaeology/ocean_ruin_warm.json} 그대로. 원문은
     * {@code docs/research/mc-archaeology-1214.md} §1 에 통째로 보관한다.
     *
     * <p>스니퍼 알은 현재 등록된 블록·아이템 어휘이므로 원문의 가중치 1 칸을 그대로 쓴다.
     * 배열 순서와 총 가중치 15는 기존 난수 계약을 보존한다.
     */
    public static final LootEntry[] OCEAN_RUIN_WARM_LOOT = {
        new LootEntry((short) Blocks.ANGLER_POTTERY_SHERD, 1),
        new LootEntry((short) Blocks.SHELTER_POTTERY_SHERD, 1),
        new LootEntry((short) Blocks.SNORT_POTTERY_SHERD, 1),
        new LootEntry((short) Blocks.SNIFFER_EGG, 1),
        new LootEntry(PlayerInventory.IRON_AXE, 1),
        new LootEntry(PlayerInventory.EMERALD, 2),
        new LootEntry(PlayerInventory.WHEAT, 2),
        new LootEntry(PlayerInventory.WOODEN_HOE, 2),
        new LootEntry(PlayerInventory.COAL, 2),
        new LootEntry(PlayerInventory.GOLD_NUGGET, 2),
    };

    /**
     * [A] {@code loot_table/archaeology/ocean_ruin_cold.json} 그대로. 냉수 표는 조각 넷이고
     * 스니퍼 알이 없어 열 칸 전부가 이 저장소의 실제 어휘다.
     */
    public static final LootEntry[] OCEAN_RUIN_COLD_LOOT = {
        new LootEntry((short) Blocks.BLADE_POTTERY_SHERD, 1),
        new LootEntry((short) Blocks.EXPLORER_POTTERY_SHERD, 1),
        new LootEntry((short) Blocks.MOURNER_POTTERY_SHERD, 1),
        new LootEntry((short) Blocks.PLENTY_POTTERY_SHERD, 1),
        new LootEntry(PlayerInventory.IRON_AXE, 1),
        new LootEntry(PlayerInventory.EMERALD, 2),
        new LootEntry(PlayerInventory.WHEAT, 2),
        new LootEntry(PlayerInventory.WOODEN_HOE, 2),
        new LootEntry(PlayerInventory.COAL, 2),
        new LootEntry(PlayerInventory.GOLD_NUGGET, 2),
    };

    private static final LootEntry[] EMPTY_LOOT = {};

    /**
     * 붓질 대상 → 그 블록이 쓰는 전리품표.
     *
     * <p><b>표를 블록이 고르는 이유.</b> 바닐라는 구조물 조각이 블록 엔티티에 표 이름을
     * 실어 두지만, 해안판에서는 [A] 배치가 온수 유적 = 의심스러운 모래 · 냉수 유적 =
     * 의심스러운 자갈로 1:1 이라 블록 ID 가 곧 표다. 사막 · 오솔길 유적이 들어오면 그때
     * 좌표별 표 이름이 필요해진다 — 그 트랙이 이 함수를 넓히면 된다.
     */
    public static LootEntry[] lootTable(int blockType) {
        if (blockType == Blocks.SUSPICIOUS_SAND) return OCEAN_RUIN_WARM_LOOT;
        if (blockType == Blocks.SUSPICIOUS_GRAVEL) return OCEAN_RUIN_COLD_LOOT;
        return EMPTY_LOOT;
    }

    /** 표의 가중치 합. [A] 두 표 모두 15 다. */
    public static int lootWeight(LootEntry[] table) {
        int total = 0;
        for (LootEntry entry : table) total += entry.weight();
        return total;
    }

    /**
     * 전리품 한 번 굴리기. {@code roll} 은 [0,1) 균등 난수이며 호출자가 자기 권위의 난수원을
     * 그대로 넘긴다. 결과가 {@link Blocks#AIR} 면 배출물이 없다(스니퍼 알 빈 칸).
     */
    public static short lootRoll(int blockType, double roll) {
        LootEntry[] table = lootTable(blockType);
        int total = lootWeight(table);
        if (total <= 0) return (short) Blocks.AIR;
        double clamped = roll < 0 ? 0 : roll >= 1 ? Math.nextDown(1.0) : roll;
        int cursor = (int) Math.floor(clamped * total);
        for (LootEntry entry : table) {
            cursor -= entry.weight();
            if (cursor < 0) return entry.itemType();
        }
        return table[table.length - 1].itemType();
    }

    // ── 장식 항아리 ─────────────────────────────────────────────────────────

    /**
     * 장식 항아리 → 그 네 면을 이루는 <b>아이템</b>. 무늬 없는 항아리는 점토 벽돌(765)이고
     * 나머지 일곱은 각자의 조각이다. 항아리가 아니면 {@link Blocks#AIR} 다.
     */
    public static short decoratedPotIngredient(int pot) {
        if (!Blocks.isDecoratedPot(pot)) return (short) Blocks.AIR;
        return pot == Blocks.DECORATED_POT
                ? PlayerInventory.BRICK
                : (short) Blocks.sherdForDecoratedPot(pot);
    }

    /**
     * 재료 아이템 → 그 재료 넷으로 만드는 장식 항아리. [A]
     * {@code #decorated_pot_ingredients} 태그는 {@code minecraft:brick} +
     * {@code #decorated_pot_sherds} 이고, 이 트랙의 어휘로는 점토 벽돌 + 조각 7 종이다.
     * 재료가 아니면 −1 이다.
     */
    public static int decoratedPotForIngredient(short ingredient) {
        if (ingredient == PlayerInventory.BRICK) return Blocks.DECORATED_POT;
        int id = Short.toUnsignedInt(ingredient);
        return Blocks.isPotterySherd(id) ? Blocks.decoratedPotForSherd(id) : -1;
    }

    /** 장식 항아리 파괴 결과 한 스택. */
    public record PotDrop(short itemType, int count) {
    }

    /**
     * 장식 항아리를 부쉈을 때 나오는 것.
     *
     * <p>[A] {@code loot_table/blocks/decorated_pot.json} 은 {@code cracked=true} 면
     * {@code sherds}(넣은 재료 넷)를, 아니면 항아리 자신을 떨군다. 이 저장소는 무늬가 ID 에 있어
     * {@code pot_decorations} 성분이 필요 없고, 그 두 갈래를 그대로 옮긴다. {@code cracked} 는
     * {@link #shattersDecoratedPot} 이 정한다(도구로 부쉈을 때와 투사체에 맞았을 때만 켜진다 —
     * 맨손·폭발·다른 아이템이면 항아리 자신이다).
     */
    public static PotDrop decoratedPotDrop(int pot, boolean cracked) {
        if (!Blocks.isDecoratedPot(pot)) return new PotDrop((short) Blocks.AIR, 0);
        if (!cracked) return new PotDrop((short) pot, 1);
        return new PotDrop(decoratedPotIngredient(pot), Blocks.DECORATED_POT_SHERD_COUNT);
    }

    /**
     * [VANILLA-SOUNDS] {@code DecoratedPotBlock.playerWillDestroy}(26.3-snapshot-7 javap): 주 손 아이템이
     * {@code #breaks_decorated_pots}(검·도끼·곡괭이·삽·괭이 태그와 삼지창·철퇴)이고 인챈트에
     * {@code #prevents_decorated_pot_shattering}(실크 터치)이 없으면 부수기 직전에 {@code cracked=true} 를
     * 켠다. 금 간 항아리는 {@code SoundType.DECORATED_POT_CRACKED} 라 파괴음이
     * {@code block.decorated_pot.shatter} 이고 loot 는 재료 넷이다.
     */
    public static boolean shattersDecoratedPot(short heldItem, long enchantments) {
        boolean breaksPots = com.gameexpert.engine.enchant.EnchantmentRules.isSwordItem(heldItem)
                || com.gameexpert.engine.enchant.EnchantmentRules.isAxeItem(heldItem)
                || com.gameexpert.engine.enchant.EnchantmentRules.isPickaxeItem(heldItem)
                || com.gameexpert.engine.enchant.EnchantmentRules.isShovelItem(heldItem)
                || com.gameexpert.engine.enchant.EnchantmentRules.isHoeItem(heldItem)
                || heldItem == PlayerInventory.TRIDENT;
        return breaksPots && com.gameexpert.engine.enchant.EnchantmentRules.enchantLevel(
                enchantments, com.gameexpert.engine.enchant.EnchantmentRules.SILK_TOUCH) <= 0;
    }
}
