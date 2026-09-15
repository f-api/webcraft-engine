package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * 상자 장착 말 계열(Donkey·Mule·Llama)의 <b>상태 없는</b> 화물 규칙. 근거는 Minecraft Java
 * 1.21.4 의 {@code AbstractChestedHorse} 와 {@code Llama} 이며 각 상수 옆에 원문 식을 적어 둔다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneChestedHorseRules.ts} 이고,
 * 두 사본의 상수·판정 동일성은 {@code StandaloneChestedHorseRules.test.ts} 가 이 파일 원문을
 * 읽어 강제한다.
 *
 * <p>화물 칸 자체는 기존 컨테이너 계약(통·상자)의 슬롯 배열을 그대로 재사용한다 — 여기 있는 것은
 * "몇 칸인가"와 "무엇이 상자를 다는가"뿐이고, 슬롯 병합·이동 규칙은 컨테이너 정본이 소유한다.
 */
public final class ChestedHorseRules {

    private ChestedHorseRules() {
    }

    /**
     * 바닐라 {@code AbstractChestedHorse#getInventoryColumns()} = 5. 당나귀·노새가 상자를 달면
     * 언제나 5열이다.
     */
    public static final int CHESTED_HORSE_COLUMNS = 5;

    /**
     * 바닐라 {@code AbstractChestedHorse#getInventorySize()} = {@code getInventoryColumns() * 3 + 1}
     * 의 행 수. +1 은 안장 슬롯이라 <b>화물</b> 칸 수는 열 × 행이다.
     */
    public static final int CARGO_ROWS = 3;

    /** 당나귀·노새의 화물 칸 수 = 5 × 3 = 15. */
    public static final int CHESTED_HORSE_CARGO_SLOTS = CHESTED_HORSE_COLUMNS * CARGO_ROWS;

    /** 상자를 달 수 있는 종. 바닐라 {@code AbstractChestedHorse} 의 구체 하위형 집합이다. */
    public static boolean chestable(MobType type) {
        return switch (type) {
            case DONKEY, MULE, LLAMA, TRADER_LLAMA -> true;
            default -> false;
        };
    }

    /**
     * 상자를 다는 아이템. 바닐라 {@code AbstractChestedHorse#mobInteract} 는
     * {@code itemstack.is(Items.CHEST)} 만 받는다(트랩 상자·통은 받지 않는다).
     */
    public static boolean isChestItem(short itemType) {
        // [CHEST-FAMILY] 여기는 형상군 술어를 **일부러 쓰지 않는다**. 바닐라
        // AbstractChestedHorse#mobInteract 는 `is(Items.CHEST)` 로 일반 상자만 받는다 —
        // 덫 상자·구리 상자는 말에 실을 수 없다. 형상(블록)이 아니라 아이템 동일성 판정이다.
        return itemType == (short) Blocks.CHEST;
    }

    /**
     * 상자를 단 개체의 화물 열 수. 바닐라에서 당나귀·노새는 고정 5열이고
     * ({@code AbstractChestedHorse#getInventoryColumns}) 라마만 힘 스탯이 곧 열 수다
     * ({@code Llama#getInventoryColumns() = hasChest() ? getStrength() : 0}).
     *
     * @param strength 라마 힘 스탯(1~5). 다른 종에서는 무시된다.
     */
    public static int inventoryColumns(MobType type, boolean hasChest, int strength) {
        if (!chestable(type) || !hasChest) return 0;
        if (type == MobType.LLAMA || type == MobType.TRADER_LLAMA) {
            return LlamaRules.clampStrength(strength);
        }
        return CHESTED_HORSE_COLUMNS;
    }

    /**
     * 화물 칸 수 = 열 × 3. 상자가 없으면 0 이다. 라마만 힘 스탯이 열 수라 3~15칸이고, 당나귀·
     * 노새는 언제나 {@link #CHESTED_HORSE_CARGO_SLOTS} 다.
     */
    public static int cargoSlots(MobType type, boolean hasChest, int strength) {
        int columns = inventoryColumns(type, hasChest, strength);
        if (columns == 0) return 0;
        return type == MobType.LLAMA || type == MobType.TRADER_LLAMA
                ? columns * CARGO_ROWS : CHESTED_HORSE_CARGO_SLOTS;
    }

    /**
     * 화물 슬롯 번호가 이 개체의 범위 안인가. 컨테이너 계약이 슬롯을 만지기 전에 보는 게이트다.
     */
    public static boolean validCargoSlot(MobType type, boolean hasChest, int strength, int slot) {
        return slot >= 0 && slot < cargoSlots(type, hasChest, strength);
    }

    /**
     * 화물 패널을 여는 우클릭인가. 바닐라 {@code AbstractChestedHorse#mobInteract} 첫머리 원문:
     *
     * <pre>{@code
     * boolean bl = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
     * if (this.isVehicle() || bl) { this.openCustomInventoryScreen(player); return SUCCESS; }
     * }</pre>
     *
     * 즉 <b>누군가 타고 있으면</b>(아이템·자세 무관) 그냥 우클릭이고, 아니면 <b>길들인 성체를
     * Shift+우클릭</b>이다. 이 판정은 손에 든 아이템보다 앞서므로 먹이·카펫·상자·안장 분기보다
     * 먼저 본다.
     *
     * @param ridden  {@code isVehicle()} — 이 개체에 승객이 하나라도 있는가. 바닐라는 우클릭한
     *                사람이 그 기수인지 묻지 않으므로 옆 사람도 열 수 있다.
     * @param crouching {@code player.isSecondaryUseActive()} — 우클릭한 사람의 웅크림.
     */
    public static boolean opensCargoPanel(MobType type, boolean hasChest, boolean ridden,
            boolean tamed, boolean baby, boolean crouching) {
        if (!chestable(type) || !hasChest) return false;
        return ridden || (!baby && tamed && crouching);
    }

    /**
     * 이 스택을 화물에 넣을 수 있는가. 화물 코덱이 인챈트·지도·셜커 참조까지 모두 보존하므로
     * 일반 컨테이너와 같은 구성요소 스택을 받는다.
     */
    public static boolean cargoAcceptsStack(long enchantments, int mapId, int shulkerId) {
        return true;
    }

    // ── WebCraft divergence ────────────────────────────────────────────
    // 1. 바닐라 말 인벤토리는 슬롯 0 이 안장, 라마는 슬롯 1 이 카펫이며 화물은 그 뒤에 붙는다.
    //    WebCraft 는 안장·카펫을 별도 권위 상태로 갖고 화물만 컨테이너 계약에 태우므로 화물
    //    슬롯 번호가 0 부터 시작한다. 칸 수(열×3)는 바닐라와 같다.
    // 2. 바닐라는 상자를 뗄 수 없다(개체가 죽을 때 상자와 내용물이 함께 떨어진다). WebCraft 도
    //    같다 — 상자 장착은 되돌릴 수 없고 사망 드랍만이 회수 경로다.
    // 3. 바닐라는 상자가 없어도 우클릭이 말 인벤토리 화면(안장·카펫 슬롯)을 연다. WebCraft 는
    //    안장·카펫이 별도 권위 상태라 열 화면이 없으므로, 상자를 단 개체만 화물 패널을 연다
    //    ({@link #opensCargoPanel} 의 hasChest 조건). 상자 없는 개체의 우클릭은 기존 분기
    //    (먹이·카펫·상자·안장·탑승) 그대로다.
    // 4. 화물 슬롯은 일반 컨테이너와 같은 인챈트·지도·셜커 참조 정체성을 싣는다.
}
