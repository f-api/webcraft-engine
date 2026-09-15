package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory.CraftArea;
import com.gameexpert.engine.inventory.PlayerInventory.CraftButton;
import com.gameexpert.engine.inventory.PlayerInventory.FurnaceArea;
import com.gameexpert.engine.inventory.PlayerInventory.ContainerArea;
import com.gameexpert.engine.qa.DenseWorldPerformanceFixturePlan;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Kind;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * [제공코드] WS 스레드가 큐에 넣고 틱 스레드가 소비하는 플레이어 액션.
 *
 * 틱 루프는 한 틱에 이동 → 도착 순서의 플레이 액션 → 부활 단계로 처리합니다
 * (구현: {@link ActionQueue}).
 */
public sealed interface PlayerAction
        permits PlayerAction.Move, PlayerAction.BlockEdit, PlayerAction.Respawn,
                PlayerAction.Attack, PlayerAction.SpearStab, PlayerAction.SelectSlot,
                PlayerAction.SwapHands, PlayerAction.SwapOffhand,
                PlayerAction.MapUse,
                PlayerAction.OpenCrafting, PlayerAction.CraftingClick,
                PlayerAction.CraftingDrag, PlayerAction.PlaceCraftingRecipe,
                PlayerAction.SelectStonecutterRecipe,
                PlayerAction.AnvilRename, PlayerAction.SelectLoomPattern,
                PlayerAction.BeaconConfirm,
                PlayerAction.OpenLectern, PlayerAction.LecternPage, PlayerAction.CloseLectern,
                PlayerAction.OpenBook, PlayerAction.EditBook, PlayerAction.SignBook,
                PlayerAction.CloseBook,
                PlayerAction.CollectCrafting,
                PlayerAction.DropCraftingCursor, PlayerAction.CloseCrafting,
                PlayerAction.BrewingClick, PlayerAction.DropBrewingCursor,
                PlayerAction.BrewingDrag, PlayerAction.CollectBrewing, PlayerAction.CloseBrewing,
                PlayerAction.MoveSlot, PlayerAction.MoveArmor, PlayerAction.Interact,
                PlayerAction.ShelfInteract, PlayerAction.BellRing, PlayerAction.PlaceItemFrame,
                PlayerAction.PlaceEndCrystal,
                PlayerAction.GeneratedEntityInteract,
                PlayerAction.GeneratedEntityAttack,
                PlayerAction.GeneratedEntityCargoTransfer,
                PlayerAction.CloseGeneratedEntityCargo,
                PlayerAction.GeneratedEntityEquipmentSettlement,
                PlayerAction.EditSign,
                PlayerAction.MobInteract, PlayerAction.Consume,
                PlayerAction.MoveChestItem, PlayerAction.CloseChest,
                PlayerAction.FurnaceClick, PlayerAction.FurnaceDrag, PlayerAction.CollectFurnace,
                PlayerAction.PlaceFurnaceRecipe,
                PlayerAction.DropFurnaceCursor, PlayerAction.CloseFurnace,
                PlayerAction.EnchantClick, PlayerAction.EnchantDrag, PlayerAction.CollectEnchant,
                PlayerAction.SelectEnchantOffer,
                PlayerAction.DropEnchantCursor, PlayerAction.CloseEnchanting,
                PlayerAction.ShieldBlock, PlayerAction.BowUse, PlayerAction.SnowballThrow, PlayerAction.EnderPearlThrow,
                PlayerAction.EggThrow, PlayerAction.PotionThrow,
                PlayerAction.WindChargeThrow, PlayerAction.EnderEyeThrow,
                PlayerAction.FishingRodUse, PlayerAction.FireworkUse, PlayerAction.GlideImpact,
                PlayerAction.TrophyItemUse,
                PlayerAction.DropItem,
                PlayerAction.EquipArmor, PlayerAction.MineHit,
                PlayerAction.VillagerTrade, PlayerAction.VillagerTradeSelect,
                PlayerAction.VillagerTradeClick, PlayerAction.VillagerTradeDrag,
                PlayerAction.CollectVillagerTrade,
                PlayerAction.DropVillagerTradeCursor,
                PlayerAction.CloseVillagerTrade,
                PlayerAction.MoveMobCargoItem, PlayerAction.CloseMobCargo,
                PlayerAction.OpenMountedMobInventory,
                PlayerAction.ContainerClick, PlayerAction.ContainerDrag,
                PlayerAction.CollectContainer,
                PlayerAction.DropContainerCursor,
                PlayerAction.CrafterSlotState,
                PlayerAction.PigPos, PlayerAction.LeavePig,
                PlayerAction.MobRiderPos, PlayerAction.MobDismount, PlayerAction.MobJump,
                PlayerAction.PlaceBoat, PlayerAction.PlaceCushion,
                PlayerAction.PlacedEntityCommand,
                PlayerAction.QaSeed, PlayerAction.QaMobAuditStage, PlayerAction.QaPerformanceFixture {

    String nickname();

    /** 아이템 사용이 시작된 권위 손. 공격·채굴·Q는 이 축을 갖지 않고 항상 주손입니다. */
    enum Hand { MAIN, OFFHAND }

    /** 클라이언트 이동 스트림(최대 10Hz). 서버 권위 위치·낙하 추적의 입력입니다. */
    @Getter
    @Accessors(fluent = true)
    final class Move implements PlayerAction {
        private static final java.util.regex.Pattern FINAL_SCENE_ACTION_ID =
                java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{8,128}");

        private final String nickname;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean crouching;
        /**
         * 클라이언트가 겉날개 활공 중인가. 이동은 클라 권위이므로 권위 측은 이 사실을 받아
         * 겉날개 마모와 바닐라 활공 낙하거리 규칙(checkSlowFallDistance)만 확정한다.
         */
        private final boolean gliding;
        private final String finalSceneActionId;

        public Move(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean crouching, boolean gliding) {
            this(nickname, x, y, z, yaw, pitch, crouching, gliding, null);
        }

        public Move(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean crouching, boolean gliding, String finalSceneActionId) {
            if (finalSceneActionId != null
                    && !FINAL_SCENE_ACTION_ID.matcher(finalSceneActionId).matches()) {
                throw new IllegalArgumentException("finalSceneActionId 형식이 올바르지 않습니다.");
            }
            this.nickname = nickname;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.crouching = crouching;
            this.gliding = gliding;
            this.finalSceneActionId = finalSceneActionId;
        }
    }

    enum EditKind { BREAK, PLACE }

    /** 블록 파괴/설치 요청. 리치·범위·가능 여부 검증은 틱 스레드에서 수행합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BlockEdit implements PlayerAction {
        private final String nickname;
        private final EditKind kind;
        private final int x;
        private final int y;
        private final int z;
        private final short blockType;
        private final short state;
        private final Hand hand;
        private final Long requestId;

        public BlockEdit(String nickname, EditKind kind, int x, int y, int z,
                short blockType, short state, Hand hand) {
            this(nickname, kind, x, y, z, blockType, state, hand, null);
        }

        public BlockEdit(String nickname, EditKind kind, int x, int y, int z, short blockType) {
            this(nickname, kind, x, y, z, blockType, (short) 0, Hand.MAIN);
        }

        public BlockEdit(String nickname, EditKind kind, int x, int y, int z,
                short blockType, short state) {
            this(nickname, kind, x, y, z, blockType, state, Hand.MAIN);
        }
    }

    /** 채굴 진행 중 실제 블록을 타격한 한 번의 요청. 성공한 타격만 월드 사운드로 확정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MineHit implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
    }

    /** 사망 상태에서 리스폰 요청. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Respawn implements PlayerAction {
        private final String nickname;
    }

    /** 몹 근접 공격 요청. 사거리·쿨다운·선택 무기 검증은 틱 스레드(전투)에서 수행합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Attack implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final boolean sprinting;
    }

    /**
     * [ENCHANT-WIDE] 창 찌르기 한 번(명중·헛손질 모두). 바닐라 {@code PiercingWeapon.attack} 끝의
     * {@code postPiercingAttack} 자리이며 돌진(lunge) 인챈트의 내구·허기를 권위가 확정한다. 명중한
     * 몹의 피해는 같은 찌르기에서 먼저 보낸 {@code attack} 이 맡는다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SpearStab implements PlayerAction {
        private final String nickname;
    }

    /** 핫바 선택 슬롯 변경(0~8). 전투 피해·설치 소비의 기준입니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SelectSlot implements PlayerAction {
        private final String nickname;
        private final int slot;
    }

    /** 게임 F: 선택 주손과 보조손 전체 스택을 교환합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SwapHands implements PlayerAction {
        private final String nickname;
    }

    /** 인벤토리 F: 가리킨 일반 인벤토리 슬롯과 보조손을 교환합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SwapOffhand implements PlayerAction {
        private final String nickname;
        private final int slot;
    }

    /** 선택 핫바의 빈 지도 한 개를 현재 위치 중심 scale-0 지도로 만듭니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MapUse implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public MapUse(String nickname) { this(nickname, Hand.MAIN); }
    }

    /**
     * 보트 아이템 사용. 보트 틱이 정산하지만 손은 이 액션이 도착 순서대로 적용될 때 잡는다.
     * 그래야 설치 직후의 핫바 스크롤이 먼저 적용돼도 도착 당시의 칸에서 보트를 꺼낸다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlaceBoat implements PlayerAction {
        private final String nickname;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
        private final Hand hand;
    }

    /** 쿠션 아이템 설치. 손을 잡는 시점 계약은 {@link PlaceBoat} 와 같다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlaceCushion implements PlayerAction {
        private final String nickname;
        private final double x;
        private final double y;
        private final double z;
        private final Hand hand;
    }

    /** 주손 방패 우클릭 홀드 상태. 클라이언트의 필수 pressed 값을 틱 스레드에 그대로 전달합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ShieldBlock implements PlayerAction {
        private final String nickname;
        private final boolean pressed;
        private final Hand hand;

        public ShieldBlock(String nickname, boolean pressed) {
            this(nickname, pressed, Hand.MAIN);
        }
    }

    /** 선택 핫바 칸의 방어구를 명시적으로 착용합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EquipArmor implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public EquipArmor(String nickname) { this(nickname, Hand.MAIN); }
    }

    /** 활 당김/해제. cancel은 발사·장전 완료 없이 진행 중인 사용만 끝냅니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BowUse implements PlayerAction {
        private final String nickname;
        private final boolean pressed;
        private final Hand hand;
        private final boolean cancel;

        public BowUse(String nickname, boolean pressed) { this(nickname, pressed, Hand.MAIN); }
        public BowUse(String nickname, boolean pressed, Hand hand) {
            this(nickname, pressed, hand, false);
        }
    }

    /** 선택 핫바 칸의 눈덩이 한 개를 시선 방향으로 던집니다. 소비·투사체 생성은 틱 스레드가 확정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SnowballThrow implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public SnowballThrow(String nickname) { this(nickname, Hand.MAIN); }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EnderPearlThrow implements PlayerAction {
        private final String nickname;
        private final Hand hand;
    }

    /** 선택 핫바 칸의 달걀 한 개를 시선 방향으로 던집니다. 소비·투사체·부화는 틱 스레드가 확정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EggThrow implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public EggThrow(String nickname) { this(nickname, Hand.MAIN); }
    }

    /**
     * [POTION] 선택 핫바 칸의 투척 물약 한 개를 시선 방향으로 던집니다. 소비·투사체·착탄
     * 스플래시는 전부 틱 스레드가 확정합니다(마녀 물약과 같은 경로).
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PotionThrow implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public PotionThrow(String nickname) { this(nickname, Hand.MAIN); }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class WindChargeThrow implements PlayerAction {
        private final String nickname;
        private final Hand hand;
    }

    /**
     * 블록(엔드 차원문 틀 제외)을 겨누지 않은 엔더의 눈 우클릭 한 번({@code EnderEyeItem#use}).
     * 가장 가까운 요새 탐색·소비·비행체 생성은 전부 틱 스레드가 확정합니다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EnderEyeThrow implements PlayerAction {
        private final String nickname;
        private final Hand hand;
    }

    /** 낚싯대 우클릭 한 번. 캐스팅과 회수를 겸하며, 판정은 전부 틱 스레드가 확정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class FishingRodUse implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public FishingRodUse(String nickname) { this(nickname, Hand.MAIN); }
    }

    /** 선택 핫바 칸의 폭죽 로켓 한 개를 쏩니다. 소비·로켓 엔티티·부스트는 틱 스레드가 확정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class FireworkUse implements PlayerAction {
        private final String nickname;
        private final Hand hand;
        private final String finalSceneActionId;

        public FireworkUse(String nickname, Hand hand) { this(nickname, hand, null); }
        public FireworkUse(String nickname) { this(nickname, Hand.MAIN, null); }
    }

    /**
     * [RAID-REWARD] 선택 칸의 전리품 아이템(뿔피리·오르골·꽃잎 주머니) 우클릭 한 번.
     * 셋 다 전투·경제 효과가 0 이고 쿨다운·소모·사운드 방송은 틱 스레드가 확정합니다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class TrophyItemUse implements PlayerAction {
        private final String nickname;
        private final Hand hand;

        public TrophyItemUse(String nickname) { this(nickname, Hand.MAIN); }
    }

    /**
     * 활공 중 벽에 부딪혀 잃은 수평 속력(블록/틱). 이동이 클라 권위라 이 값만 클라가 계산하고,
     * 바닐라 flyIntoWall 피해량 확정과 적용은 권위가 한다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class GlideImpact implements PlayerAction {
        private final String nickname;
        private final double lostSpeed;
        private final String finalSceneActionId;

        public GlideImpact(String nickname, double lostSpeed) {
            this(nickname, lostSpeed, null);
        }
    }

    /**
     * BREWING 은 양조대 앞의 좌표 귀속 5슬롯 세션이다.
     * [STONECUT] STONECUTTER 는 석재 절단기 앞 세션이다(입력 한 칸 + 선택 산출).
     */
    enum CraftStation {
        INVENTORY, TABLE, BREWING, STONECUTTER, SMITHING,
        ANVIL, CARTOGRAPHY, GRINDSTONE, LOOM,
        /** [BEACON] 신호기 화면. 제작 격자의 슬롯 0 하나가 BeaconMenu 의 결제 칸이다. */
        BEACON
    }

    /** 개인 2×2 또는 좌표 작업대 3×3 제작 컨테이너를 엽니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class OpenCrafting implements PlayerAction {
        private final String nickname;
        private final CraftStation station;
        private final int x;
        private final int y;
        private final int z;
        private final Long requestId;

        public OpenCrafting(String nickname, CraftStation station, int x, int y, int z) {
            this(nickname, station, x, y, z, null);
        }
    }

    /** 열린 제작 컨테이너의 실제 슬롯을 클릭합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CraftingClick implements PlayerAction {
        private final String nickname;
        private final CraftArea area;
        private final int slot;
        private final CraftButton button;
        private final boolean shift;
    }

    /** 열린 제작 컨테이너에서 커서 스택을 여러 슬롯에 한 동작으로 배분합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CraftingDrag implements PlayerAction {
        private final String nickname;
        private final CraftArea[] areas;
        private final int[] slots;
        private final CraftButton button;
    }

    /** 레시피 북 선택으로 현재 격자에 한 번분 또는 가능한 최대분의 재료를 재배치합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlaceCraftingRecipe implements PlayerAction {
        private final String nickname;
        private final String recipeId;
        private final boolean maximum;
    }

    /**
     * [STONECUT] 절단기 세션에서 산출을 고릅니다. 선택 id 는 권위가 신뢰하지 않고
     * 지금 입력 칸에서 유도 가능한지 표로 재검증합니다(불가면 조용히 무시).
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SelectStonecutterRecipe implements PlayerAction {
        private final String nickname;
        private final String recipeId;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class AnvilRename implements PlayerAction {
        private final String nickname;
        private final String name;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SelectLoomPattern implements PlayerAction {
        private final String nickname;
        private final com.gameexpert.engine.inventory.LoomRules.Pattern pattern;
    }

    /**
     * [BEACON] 신호기 화면의 확정 버튼({@code ServerboundSetBeaconPacket}). 효과 이름은 null 이면 없음이다.
     * 권위는 결제 칸·층 수·{@code validateEffects} 를 다시 판정한다({@code BeaconMenu.updateEffects}).
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BeaconConfirm implements PlayerAction {
        private final String nickname;
        private final com.gameexpert.engine.BeaconRules.Power primary;
        private final com.gameexpert.engine.BeaconRules.Power secondary;
    }

    /** 더블클릭으로 커서와 같은 아이템을 열린 컨테이너에서 모읍니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CollectCrafting implements PlayerAction {
        private final String nickname;
        private final CraftArea area;
        private final int slot;
    }

    /** 컨테이너 바깥 클릭으로 커서 전체 또는 한 개를 버립니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropCraftingCursor implements PlayerAction {
        private final String nickname;
        private final boolean one;
    }

    /** 제작 창을 닫고 커서·격자 아이템을 인벤토리로 반환합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseCrafting implements PlayerAction {
        private final String nickname;
        private final long sessionId;
        private final Long requestId;

        public CloseCrafting(String nickname, long sessionId) {
            this(nickname, sessionId, null);
        }
    }

    /** Coordinate-owned brewing stand slot action; the open lease supplies the coordinate. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BrewingClick implements PlayerAction {
        private final String nickname;
        private final ContainerArea area;
        private final int slot;
        private final CraftButton button;
        private final boolean shift;
        private final int x;
        private final int y;
        private final int z;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class BrewingDrag implements PlayerAction {
        private final String nickname;
        private final ContainerArea[] areas;
        private final int[] slots;
        private final CraftButton button;
        private final int x;
        private final int y;
        private final int z;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class CollectBrewing implements PlayerAction {
        private final String nickname;
        private final ContainerArea area;
        private final int slot;
        private final int x;
        private final int y;
        private final int z;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropBrewingCursor implements PlayerAction {
        private final String nickname;
        private final boolean one;
        private final int x;
        private final int y;
        private final int z;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseBrewing implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final Long requestId;

        public CloseBrewing(String nickname, int x, int y, int z) {
            this(nickname, x, y, z, null);
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class OpenLectern implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final Long requestId;

        public OpenLectern(String nickname, int x, int y, int z) {
            this(nickname, x, y, z, null);
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class LecternPage implements PlayerAction {
        private final String nickname;
        private final int page;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseLectern implements PlayerAction {
        private final String nickname;
        private final Long requestId;

        public CloseLectern(String nickname) { this(nickname, null); }
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class OpenBook implements PlayerAction {
        private final String nickname;
        private final Hand hand;
        private final Long requestId;

        public OpenBook(String nickname, Hand hand) { this(nickname, hand, null); }
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class EditBook implements PlayerAction {
        private final String nickname;
        private final java.util.List<String> pages;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class SignBook implements PlayerAction {
        private final String nickname;
        private final String title;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class CloseBook implements PlayerAction {
        private final String nickname;
        private final Long requestId;

        public CloseBook(String nickname) { this(nickname, null); }
    }

    /** 인벤토리 슬롯 이동(0~35 swap). 범위·동일 슬롯 검증은 틱 스레드에서 수행합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MoveSlot implements PlayerAction {
        private final String nickname;
        private final int from;
        private final int to;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MoveArmor implements PlayerAction {
        private final String nickname;
        private final com.gameexpert.engine.inventory.ArmorSlot armorSlot;
        private final int inventorySlot;
    }

    /** 선택 인벤토리 슬롯에서 count개를 던지는 요청. 실제 보유량은 틱 스레드가 판정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropItem implements PlayerAction {
        private final String nickname;
        private final int slot;
        private final int count;
    }

    /** 블록 상호작용. 대상별 동작과 리치 검증은 틱 스레드가 판정합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Interact implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final Hand hand;
        private final Long requestId;

        public Interact(String nickname, int x, int y, int z, Hand hand) {
            this(nickname, x, y, z, hand, null);
        }
    }

    /**
     * [CONTAINER-MENUS] One player action on a placed armor stand or minecart
     * ({@link PlacedEntitySystem}): place from the held item onto a block face, interact (with the
     * hit height for an armor stand's slot), attack, dismount, rider move intent or cargo close.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlacedEntityCommand implements PlayerAction {
        public enum Op { PLACE, INTERACT, ATTACK, DISMOUNT, RIDE_INPUT, CLOSE_CARGO }

        private final String nickname;
        private final Op op;
        private final long id;
        private final int x;
        private final int y;
        private final int z;
        private final int face;
        private final boolean offhand;
        private final double hitY;
        private final double inputX;
        private final double inputZ;
    }

    /** Exact generated non-mob interaction; this source can never alias a MobInteract. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class GeneratedEntityInteract implements PlayerAction {
        private final String nickname;
        private final Kind kind;
        private final long entityId;
        private final Hand hand;
        private final Long requestId;

        public GeneratedEntityInteract(String nickname, Kind kind, long entityId, Hand hand) {
            this(nickname, kind, entityId, hand, null);
        }
    }

    /** Exact generated non-mob attack; this source can never alias a mob Attack. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class GeneratedEntityAttack implements PlayerAction {
        private final String nickname;
        private final Kind kind;
        private final long entityId;
        private final boolean sprinting;
    }

    /** One whole-stack transfer in an authority-issued generated Chest Minecart menu session. */
    @Getter
    @Accessors(fluent = true)
    final class GeneratedEntityCargoTransfer implements PlayerAction {
        private final String nickname;
        private final long entityId;
        private final long sessionId;
        private final boolean fromCargo;
        private final int slot;

        public GeneratedEntityCargoTransfer(String nickname, long entityId, long sessionId,
                boolean fromCargo, int slot) {
            this.nickname = requireGeneratedNickname(nickname);
            this.entityId = requireGeneratedIdentity(entityId, "generated cargo entity");
            this.sessionId = requireGeneratedIdentity(sessionId, "generated cargo session");
            this.fromCargo = fromCargo;
            this.slot = slot;
        }
    }

    /** Closes only the exact generated Chest Minecart session issued by the authority. */
    @Getter
    @Accessors(fluent = true)
    final class CloseGeneratedEntityCargo implements PlayerAction {
        private final String nickname;
        private final long entityId;
        private final long sessionId;

        public CloseGeneratedEntityCargo(String nickname, long entityId, long sessionId) {
            this.nickname = requireGeneratedNickname(nickname);
            this.entityId = requireGeneratedIdentity(entityId, "generated cargo entity");
            this.sessionId = requireGeneratedIdentity(sessionId, "generated cargo session");
        }
    }

    /** H12f hand-neutral request; durable entity/player revisions are minted by the tick owner. */
    @Getter
    @Accessors(fluent = true)
    final class GeneratedEntityEquipmentSettlement implements PlayerAction {
        private final String nickname;
        private final long entityId;
        private final Hand hand;

        public GeneratedEntityEquipmentSettlement(String nickname, long entityId, Hand hand) {
            this.nickname = requireGeneratedNickname(nickname);
            this.entityId = requireGeneratedIdentity(entityId, "generated equipment entity");
            this.hand = java.util.Objects.requireNonNull(hand, "generated equipment hand");
        }
    }

    private static String requireGeneratedNickname(String nickname) {
        if (nickname == null || !nickname.matches("[A-Za-z0-9_]{2,12}")) {
            throw new IllegalArgumentException("invalid generated entity actor");
        }
        return nickname;
    }

    private static long requireGeneratedIdentity(long value, String label) {
        if (value <= 0L || value == Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " identity is invalid");
        }
        return value;
    }

    /** Shelf front slot exchange; slot is derived from the authoritative front-face hit. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ShelfInteract implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final int slot;
    }

    /**
     * [BLOCK-SHAPES] 종 치기(BellBlock#useWithoutItem → onHit). {@code face} 는 클릭한 면의 바닐라
     * Direction 번호, {@code hitY} 는 칸 안의 맞은 높이다. isProperHit·리치는 틱 스레드가 판정한다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BellRing implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final int face;
        private final double hitY;
    }

    /**
     * [EC-MOBS] 아이템 액자 설치({@code HangingEntityItem.useOn}): 누른 칸 {@code (x,y,z)} 의 면 {@code face}
     * (Direction 3D 값) 바깥 칸에 그 면을 바라보는 액자를 건다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlaceItemFrame implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final int face;
        private final Hand hand;
    }

    /** [DRAGON] 엔드 수정 설치({@code EndCrystalItem.useOn}): 누른 칸 {@code (x,y,z)} 위에 수정을 세운다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlaceEndCrystal implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final Hand hand;
    }

    /** Sign editor commit. Exactly four validated lines replace the coordinate-owned text. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EditSign implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final java.util.List<String> lines;
    }

    /** 몹 우클릭 요청. 대상은 공격과 같은 서버 mobId로 지정하고 규칙 판정은 틱 스레드가 맡습니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MobInteract implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final Hand hand;
        private final Long interactionId;

        public MobInteract(String nickname, long mobId, Hand hand) {
            this(nickname, mobId, hand, null);
        }
    }

    /**
     * [FARM-ANIMAL] 탑승 중인 돼지의 기수 좌표 업링크. 보트 {@code boatPos} 와 같은 계약이라
     * 좌표 정본은 기수 클라이고, 틱 스레드가 기수 일치·월드 경계·기수 pose 근접만 검증합니다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PigPos implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
    }

    /** [FARM-ANIMAL] 자발적 하차 요청. 실제로 타고 있던 돼지에서만 좌석이 비워집니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class LeavePig implements PlayerAction {
        private final String nickname;
    }

    /**
     * [MOUNT] 종 비의존 기수 좌표 업링크. 돼지 {@code PigPos} 와 같은 계약이며 좌석 번호가
     * 붙는다 — 조종석(0)만 좌표를 올릴 수 있다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MobRiderPos implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final int seatIndex;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
    }

    /** [MOUNT] 종 비의존 자발적 하차 요청. 실제로 앉아 있던 좌석에서만 비워집니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MobDismount implements PlayerAction {
        private final String nickname;
    }

    /**
     * [MOUNT] 탈것 점프 확정 요청. {@code charge} 는 바닐라
     * {@code ServerboundPlayerCommandPacket} 과 같은 0~100 차지 게이지다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MobJump implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final int charge;
    }

    /**
     * [VILLAGER-TRADE] 열린 주민 거래 화면에서 오퍼 하나 확정. 세션·재고·잠금 단계·수요 보정
     * 가격·보유량 검증과 인벤토리 이동은 모두 틱 스레드가 확정한다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class VillagerTrade implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final int offer;
    }

    /** 오퍼 줄 선택. 결제 슬롯만 준비하며 거래는 결과 슬롯 클릭이 따로 확정한다. */
    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class VillagerTradeSelect implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final int offer;
    }

    enum VillagerTradeArea { INVENTORY, PAYMENT, RESULT }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class VillagerTradeClick implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final VillagerTradeArea area;
        private final int slot;
        private final CraftButton button;
        private final boolean shift;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class VillagerTradeDrag implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final VillagerTradeArea[] areas;
        private final int[] slots;
        private final CraftButton button;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class CollectVillagerTrade implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final VillagerTradeArea area;
        private final int slot;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class DropVillagerTradeCursor implements PlayerAction {
        private final String nickname;
        private final long mobId;
        private final boolean one;
    }

    /** [VILLAGER-TRADE] 주민 거래 세션 닫기. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseVillagerTrade implements PlayerAction {
        private final String nickname;
        private final long mobId;
    }

    /**
     * [MOUNT] 열린 몹 화물(상자를 단 당나귀·노새·라마)과 플레이어 인벤토리 사이에서 한 슬롯의
     * 전체 스택을 옮긴다. 좌표 컨테이너와 달리 대상이 움직이므로 키가 {@code mobId} 이고,
     * 세션·사거리·칸 수·병합은 모두 틱 스레드가 확정한다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MoveMobCargoItem implements PlayerAction {
        private final String nickname;
        private final long mobId;
        /** 참이면 화물 → 인벤토리, 거짓이면 인벤토리 → 화물이다. */
        private final boolean fromCargo;
        private final int slot;
    }

    /** [MOUNT] 몹 화물 세션 닫기. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseMobCargo implements PlayerAction {
        private final String nickname;
        private final long mobId;
    }

    /** Inventory-key request while riding this exact live horse-family mob. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class OpenMountedMobInventory implements PlayerAction {
        private final String nickname;
        private final long mobId;
    }

    /** 식량 섭취(§10.2-S2a). 선택 슬롯이 음식이고 HP<20 이면 1개 소비 + 회복. 만복이면 무시. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Consume implements PlayerAction {
        private final String nickname;
        private final Hand hand;
        private final String phase;

        public Consume(String nickname) { this(nickname, Hand.MAIN); }
        public Consume(String nickname, Hand hand) { this(nickname, hand, "finish"); }
    }

    /** 열린 상자와 플레이어 인벤토리 사이에서 한 슬롯의 전체 스택을 옮긴다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class MoveChestItem implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final boolean fromChest;
        private final int slot;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseChest implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
    }

    /**
     * [CONTAINER-CURSOR] 커서 조작 대상 컨테이너. 좌표 컨테이너(상자·큰 상자·통)와 스스로
     * 움직이는 몹 화물의 키가 다르므로 한 값에 둘을 담고 {@code mob} 이 어느 쪽인지 정한다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ContainerRef {
        private final boolean mob;
        private final long mobId;
        private final int x;
        private final int y;
        private final int z;
        /** [CONTAINER-MENUS] A placed chest or hopper minecart's cargo, else 0. */
        private final long placedEntityId;

        public static ContainerRef chest(int x, int y, int z) {
            return new ContainerRef(false, 0L, x, y, z, 0L);
        }

        public static ContainerRef mobCargo(long mobId) {
            return new ContainerRef(true, mobId, 0, 0, 0, 0L);
        }

        public static ContainerRef entityCargo(long entityId) {
            return new ContainerRef(false, 0L, 0, 0, 0, entityId);
        }
    }

    /** [CONTAINER-CURSOR] 열린 보관 컨테이너의 한 칸을 좌/우/Shift 클릭합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ContainerClick implements PlayerAction {
        private final String nickname;
        private final ContainerRef target;
        private final com.gameexpert.engine.inventory.PlayerInventory.ContainerArea area;
        private final int slot;
        private final CraftButton button;
        private final boolean shift;
    }

    /** [CONTAINER-CURSOR] 커서 스택을 지나간 칸들에 분배합니다(좌=균등, 우=1개씩). */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ContainerDrag implements PlayerAction {
        private final String nickname;
        private final ContainerRef target;
        private final com.gameexpert.engine.inventory.PlayerInventory.ContainerArea[] areas;
        private final int[] slots;
        private final CraftButton button;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class CollectContainer implements PlayerAction {
        private final String nickname;
        private final ContainerRef target;
        private final ContainerArea area;
        private final int slot;
    }

    /**
     * [CONTAINER-MENUS] {@code ServerboundContainerSlotStateChangedPacket}: the open crafter's
     * slot is enabled or disabled ({@code CrafterBlockEntity#setSlotState}, empty slots only).
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CrafterSlotState implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final int slot;
        private final boolean enabled;
    }

    /** [CONTAINER-CURSOR] 컨테이너 창 바깥 클릭 — 커서 전체(또는 한 개)를 월드에 던집니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropContainerCursor implements PlayerAction {
        private final String nickname;
        private final ContainerRef target;
        private final boolean one;
    }

    /** 열린 화로 메뉴의 인벤토리 또는 화로 슬롯을 좌/우/Shift 클릭합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class FurnaceClick implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final FurnaceArea area;
        private final int slot;
        private final CraftButton button;
        private final boolean shift;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class FurnaceDrag implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final FurnaceArea[] areas;
        private final int[] slots;
        private final CraftButton button;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class CollectFurnace implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final FurnaceArea area;
        private final int slot;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class PlaceFurnaceRecipe implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final String recipeId;
        private final boolean maximum;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropFurnaceCursor implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final boolean one;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseFurnace implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
    }

    /** [SURV-X] 열린 인챈트 화면의 인벤토리 또는 입력 두 칸을 좌/우/Shift 클릭합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EnchantClick implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final EnchantArea area;
        private final int slot;
        private final CraftButton button;
        private final boolean shift;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class EnchantDrag implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final EnchantArea[] areas;
        private final int[] slots;
        private final CraftButton button;
    }

    @Getter @Accessors(fluent = true) @AllArgsConstructor
    final class CollectEnchant implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final EnchantArea area;
        private final int slot;
    }

    /** [SURV-X] 제안 세 줄 중 하나를 고릅니다. 지불 가능 여부는 서버가 다시 검증합니다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SelectEnchantOffer implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final int offer;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropEnchantCursor implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
        private final boolean one;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CloseEnchanting implements PlayerAction {
        private final String nickname;
        private final int x;
        private final int y;
        private final int z;
    }

    /**
     * QA 시딩 요청. {@code game.qa-seeding=true} 로 뜬 QA 서버에서만 큐에 들어간다
     * ({@code QaSeedWsHandler} 가 게이트). 상태를 직접 쓰지 않고 권위 API
     * (인벤토리 {@code addItem}, 몹 스폰, 월드 시계)를 그대로 호출한다.
     */
    @Getter
    @Accessors(fluent = true)
    final class QaSeed implements PlayerAction {
        private final String nickname;
        /** give | spawn | time | weather | fixture */
        private final String command;
        /** give 는 아이템 id, spawn 은 MobType 이름, time 은 사용하지 않는다. */
        private final String argument;
        /** give 는 개수, time 은 월드 시각. */
        private final int amount;
        private final double x;
        private final double y;
        private final double z;
        /** 예약 QA 월드 이름을 서버 저장소에서 확인한 fixture 요청만 true다. */
        private final boolean fixtureAuthorized;
        /** null for ordinary content-v1 requests; exact H12 id for final-scene preparation. */
        private final String finalSceneScenario;
        /** Connection request nonce. The authority burns it before publishing one receipt. */
        private final String finalSceneActionNonce;

        public QaSeed(String nickname, String command, String argument, int amount,
                double x, double y, double z) {
            this(nickname, command, argument, amount, x, y, z, false, null, null);
        }

        public QaSeed(String nickname, String command, String argument, int amount,
                double x, double y, double z, boolean fixtureAuthorized) {
            this(nickname, command, argument, amount, x, y, z, fixtureAuthorized, null, null);
        }

        public QaSeed(String nickname, String command, String argument, int amount,
                double x, double y, double z, boolean fixtureAuthorized,
                String finalSceneScenario, String finalSceneActionNonce) {
            this.nickname = nickname;
            this.command = command;
            this.argument = argument;
            this.amount = amount;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fixtureAuthorized = fixtureAuthorized;
            this.finalSceneScenario = finalSceneScenario;
            this.finalSceneActionNonce = finalSceneActionNonce;
        }
    }

    enum MobAuditStage {
        AGE_COW, AGE_PIG, AGE_HAPPY_GHAST, BREEDING_COW, BREEDING_PIG, CURE_ZOMBIE_VILLAGER;

        public static MobAuditStage fromWire(String value) {
            return switch (value) {
                case "mob-age-next:cow" -> AGE_COW;
                case "mob-age-next:pig" -> AGE_PIG;
                case "mob-age-next:happy_ghast" -> AGE_HAPPY_GHAST;
                case "mob-breeding-ready:cow" -> BREEDING_COW;
                case "mob-breeding-ready:pig" -> BREEDING_PIG;
                case "mob-cure-next:zombie_villager" -> CURE_ZOMBIE_VILLAGER;
                default -> null;
            };
        }

        public boolean ageBoundary() { return this == AGE_COW || this == AGE_PIG || this == AGE_HAPPY_GHAST; }
        public com.gameexpert.engine.mob.MobType species() {
            return switch (this) {
                case AGE_COW, BREEDING_COW -> com.gameexpert.engine.mob.MobType.COW;
                case AGE_PIG, BREEDING_PIG -> com.gameexpert.engine.mob.MobType.PIG;
                case AGE_HAPPY_GHAST -> com.gameexpert.engine.mob.MobType.HAPPY_GHAST;
                case CURE_ZOMBIE_VILLAGER -> com.gameexpert.engine.mob.MobType.ZOMBIE_VILLAGER;
            };
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class QaMobAuditStage implements PlayerAction {
        private final String nickname;
        private final MobAuditStage stage;
        private final long mobId;
        private final boolean fixtureAuthorized;
    }

    /** Exact, already-authorized version-bound dense performance fixture request. */
    enum PerformanceFixtureOperation { INSTALL, START }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class QaPerformanceFixture implements PlayerAction {
        private final String nickname;
        private final PerformanceFixtureOperation operation;
        /** Pure immutable plan prepared and checksum-validated before entering the owner queue. */
        private final DenseWorldPerformanceFixturePlan plan;
    }

    /** [SURV-X] 인챈트 화면의 클릭 대상. enchant 는 대상 아이템(0)·청금석(1) 두 칸이다. */
    enum EnchantArea {
        INVENTORY,
        ENCHANT
    }
}
