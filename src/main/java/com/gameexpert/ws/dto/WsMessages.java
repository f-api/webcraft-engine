package com.gameexpert.ws.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.gameexpert.block.snapshot.ChunkVersion;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.HungerRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.gameexpert.engine.enchant.WideEnchantments;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** WebSocket 평평한 JSON 프로토콜의 서버 송신 DTO 모음입니다. */
public final class WsMessages {

    private WsMessages() {
    }

    @Getter
    @AllArgsConstructor
    public static class CompanionInteractionResult {
        private final String type = "companionInteractionResult";
        private final long interactionId;
        private final long mobId;
        private final String hand;
        private final String result;
    }

    @Getter
    @AllArgsConstructor
    public static class BannerPatternLayer {
        private final String pattern;
        private final int color;
    }

    /** [UTILITY] 와이어 갑옷 장식 {@code {pattern, material}}(바닐라 trim_pattern · trim_material 경로). */
    @Getter
    @AllArgsConstructor
    public static class TrimComponent {
        private final String pattern;
        private final String material;

        /** 성분 값에서 와이어 값을 만든다(없으면 null). */
        public static TrimComponent of(com.gameexpert.engine.inventory.ItemComponentData.ArmorTrim trim) {
            return trim == null ? null : new TrimComponent(trim.pattern(), trim.material());
        }
    }

    @Getter
    @AllArgsConstructor
    public static class BookComponent {
        private final String title;
        private final String author;
        private final List<String> pages;
    }

    /** 인벤토리 한 칸. durability는 내구 아이템에만 필수 정수이고, 그 외에는 null입니다. */
    @Getter
    // [ENCHANT-WIDE] enchantments 는 이제 getter 전용 속성이라 필드 선언 순서를 명시해 옛 키 순서를 지킨다.
    @JsonPropertyOrder({"slot", "itemType", "count", "durability", "enchantments", "mapId",
            "shulkerId", "bucketMobData", "customName", "bannerPatterns", "book", "anvilUseCount", "leatherColor",
            "suspiciousStewEffect", "suspiciousStewDurationMcTicks", "ominousBottleAmplifier",
            "potionContents", "trim"})
    public static class InventorySlot {
        private final int slot;
        private final short itemType;
        private final int count;
        private final Integer durability;
        /**
         * [SURV-X] 현재 프로토콜의 필수 인챈트 압축 마스크(워드 0). 없으면 0입니다. 와이어에는
         * {@link #getEnchantments()} 가 확장 워드와 합친 값을 싣습니다.
         */
        @JsonIgnore
        private final long enchantmentMask;
        /** [ENCHANT-WIDE] 인챈트 ID 16..42 의 확장 워드(성분에서 온 값). 와이어에는 따로 싣지 않습니다. */
        @JsonIgnore
        private final long enchantmentWord1;
        @JsonIgnore
        private final long enchantmentWord2;
        /** 채워진 지도만 가지는 월드 지도 ID. 다른 아이템은 null. */
        private final Integer mapId;
        /**
         * [SHULKER-CONTENTS] 셜커 상자 아이템이 가리키는 내용 27칸의 월드 영속 ID. 다른
         * 아이템은 null 이고 <b>비어 있는 셜커 상자도 null</b> 이다(내용이 생길 때 발급된다).
         *
         * <p>{@code mapId} 와 같은 간접 참조 문법이라 {@code JsonInclude} 를 붙이지 않는다 —
         * 정적판 {@code Protocol.InventorySlot.shulkerId} 도 {@code number | null} 필수 필드라
         * 와이어 모양이 갈리면 안 된다.
         */
        private final Integer shulkerId;
        /** Strict WCMB1 identity for bucketed Axolotl/Tropical Fish/Tadpole stacks. */
        private final String bucketMobData;
        private final String customName;
        private final List<BannerPatternLayer> bannerPatterns;
        private final BookComponent book;
        private final int anvilUseCount;
        /** Dyed leather RGB, or null for undyed and non-leather items. */
        private final Integer leatherColor;
        /** Exact archaeology stew effect key, or null for every other stack. */
        private final String suspiciousStewEffect;
        /** Exact MC-tick duration paired with suspiciousStewEffect, otherwise null. */
        private final Integer suspiciousStewDurationMcTicks;
        /**
         * [TRIAL-GAP] 불길한 병의 비기본 증폭 1..4(바닐라 {@code ominous_bottle_amplifier}).
         * 기본 0 과 다른 모든 아이템은 필드를 싣지 않는다 — 기존 와이어 바이트가 그대로다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Integer ominousBottleAmplifier;
        /**
         * [UTILITY] 범용 물약(CONTENTS_*)의 {@code potion_contents} 키(네임스페이스 없이). 다른 모든
         * 스택은 필드를 싣지 않는다 — 기존 와이어 바이트가 그대로다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String potionContents;
        /** [UTILITY] 장식 가능한 방어구의 {@code trim}(무늬·재료). 없으면 싣지 않는다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final TrimComponent trim;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations;

        public InventorySlot(int slot, short itemType, int count, Integer durability,
                long enchantments, Integer mapId, Integer shulkerId, String bucketMobData,
                String customName, List<BannerPatternLayer> bannerPatterns, BookComponent book,
                int anvilUseCount, Integer leatherColor, String suspiciousStewEffect,
                Integer suspiciousStewDurationMcTicks) {
            this(slot, itemType, count, durability, enchantments, mapId, shulkerId,
                    bucketMobData, customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    suspiciousStewEffect, suspiciousStewDurationMcTicks, null);
        }

        public InventorySlot(int slot, short itemType, int count, Integer durability,
                long enchantments, Integer mapId, Integer shulkerId, String bucketMobData,
                String customName, List<BannerPatternLayer> bannerPatterns, BookComponent book,
                int anvilUseCount, Integer leatherColor, String suspiciousStewEffect,
                Integer suspiciousStewDurationMcTicks, Integer ominousBottleAmplifier) {
            this(slot, itemType, count, durability, WideEnchantments.legacy(enchantments), mapId,
                    shulkerId, bucketMobData, customName, bannerPatterns, book, anvilUseCount,
                    leatherColor, suspiciousStewEffect, suspiciousStewDurationMcTicks,
                    ominousBottleAmplifier);
        }

        /** [ENCHANT-WIDE] 43종 인챈트 집합을 싣는 생성자(불길한 병 증폭 없음). */
        public InventorySlot(int slot, short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks) {
            this(slot, itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData,
                    customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    suspiciousStewEffect, suspiciousStewDurationMcTicks, null);
        }

        /** [ENCHANT-WIDE] 43종 인챈트 집합 + [TRIAL-GAP] 불길한 병 증폭(물약·장식 성분 없음). */
        public InventorySlot(int slot, short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
                Integer ominousBottleAmplifier) {
            this(slot, itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData,
                    customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                    null, null);
        }

        /** [UTILITY] 정본 생성자: 위 성분 + 물약 내용물 + 갑옷 장식. */
        public InventorySlot(int slot, short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
                Integer ominousBottleAmplifier, String potionContents, TrimComponent trim) {
            this(slot, itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData, customName, bannerPatterns, book, anvilUseCount, leatherColor, suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier, potionContents, trim, null);
        }

        public InventorySlot(int slot, short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
                Integer ominousBottleAmplifier, String potionContents, TrimComponent trim,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations) {
            validateStewWireComponent(
                    itemType, suspiciousStewEffect, suspiciousStewDurationMcTicks);
            validateOminousWireComponent(itemType, ominousBottleAmplifier);
            this.slot = slot;
            this.itemType = itemType;
            this.count = count;
            this.durability = durability;
            this.enchantmentMask = enchantments.word0();
            this.enchantmentWord1 = enchantments.word1();
            this.enchantmentWord2 = enchantments.word2();
            this.mapId = mapId;
            this.shulkerId = shulkerId;
            this.bucketMobData = bucketMobData;
            this.customName = customName;
            this.bannerPatterns = bannerPatterns;
            this.book = book;
            this.anvilUseCount = anvilUseCount;
            this.leatherColor = leatherColor;
            this.suspiciousStewEffect = suspiciousStewEffect;
            this.suspiciousStewDurationMcTicks = suspiciousStewDurationMcTicks;
            this.ominousBottleAmplifier = ominousBottleAmplifier;
            this.potionContents = potionContents;
            this.trim = trim;
            this.potDecorations = potDecorations == null || potDecorations.isEmpty()
                    ? null : List.copyOf(potDecorations);
        }

        /** Source convenience only; wire serialization still emits both required nullable fields. */
        public InventorySlot(int slot, short itemType, int count, Integer durability,
                long enchantments, Integer mapId, Integer shulkerId, String bucketMobData,
                String customName, List<BannerPatternLayer> bannerPatterns, BookComponent book,
                int anvilUseCount, Integer leatherColor) {
            this(slot, itemType, count, durability, enchantments, mapId, shulkerId,
                    bucketMobData, customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    null, null);
        }

        /** [ENCHANT-WIDE] 워드 0 과 확장 워드를 합친 43종 집합. */
        @JsonIgnore
        public WideEnchantments getWideEnchantments() {
            return new WideEnchantments(enchantmentMask, enchantmentWord1, enchantmentWord2);
        }

        /**
         * [ENCHANT-WIDE] 와이어 {@code enchantments}: 확장이 없으면 옛 정수 마스크 그대로, 있으면
         * 전체 집합의 정규 16진 문자열이다.
         */
        @JsonProperty("enchantments")
        public Object getEnchantments() {
            return getWideEnchantments().wireValue();
        }

    }

    @Getter
    public static class PlayerPose {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> selectedItemPotDecorations;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> offhandItemPotDecorations;
        private final String nickname;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        /** 서버 권위 웅크림 상태. 서 있을 때도 false를 보내 원격 자세를 복원한다. */
        private final boolean crouching;
        // 화상 여부(§11.5). welcome.players[]·playerMoves.players[] 에 실려 타 플레이어 3인칭 불꽃에 쓰인다.
        private final boolean onFire;
        /** 서버 권위 선택 슬롯의 아이템 ID. 빈손은 0입니다. */
        private final short selectedItemId;
        /** 서버 권위 보조손 아이템 ID. 빈손은 0입니다. */
        private final short offhandItemId;
        /**
         * [GLINT] 선택 슬롯 스택의 인챈트 와이어 값({@link com.gameexpert.engine.enchant.WideEnchantments#wireValue()}
         * — 확장이 없으면 정수, 있으면 16진 문자열). 인챈트가 없으면 0. 원격 3인칭 손의 광택에 쓴다.
         */
        private final Object selectedItemEnchantments;
        /** [GLINT] 보조손 스택의 인챈트 와이어 값. 없으면 0. */
        private final Object offhandItemEnchantments;
        /**
         * [ARROW-GROUND] 몸에 박힌 화살 수(바닐라 {@code LivingEntity.getArrowCount}, 아바타 ArrowLayer 가
         * 그린다). 0 이면 필드가 없다(append-only).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Integer arrowCount;
        /**
         * [UTILITY] 착용 갑옷 네 칸(투구·흉갑·각반·장화 순)의 itemType, 빈칸은 0. 네 칸이 모두 비면 싣지
         * 않는다(append-only 선택 필드 — 없으면 맨몸).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<Short> armorItemIds;
        /** [UTILITY] 같은 네 칸의 갑옷 장식({@code trim}, 없으면 null). 네 칸 모두 없으면 싣지 않는다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<TrimComponent> armorTrims;
        /**
         * [SPEAR-KINETIC] 창 돌진을 쓰는 중(바닐라 {@code isUsingItem} + KINETIC_WEAPON). 쓰지 않으면 필드가 없다
         * (append-only). 원격 3인칭은 참이 된 순간부터 쓰기 틱을 스스로 센다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean spearUsing;
        /**
         * [EQUIPMENT] 같은 네 칸의 인챈트 와이어 값(없으면 0). 원격 아바타 갑옷 광택(armor_entity_glint)에 쓴다.
         * 네 칸 모두 인챈트가 없으면 싣지 않는다(append-only 선택 필드).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<Object> armorEnchantments;
        /**
         * [EQUIPMENT] 같은 네 칸의 염색 가죽 RGB({@code dyed_color}, 없으면 null). 바닐라 DyedItemColor 틴트다.
         * 네 칸 모두 없으면 싣지 않는다(append-only 선택 필드).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<Integer> armorLeatherColors;
        /**
         * [SHIELD-PATTERN] 선택 슬롯 스택의 무늬 층(무늬 있는 방패의 선두 {@code base} 층이 바탕색이다). 원격
         * 3인칭 손이 바닐라 ShieldSpecialRenderer 처럼 무늬를 그린다. 무늬가 없으면 싣지 않는다(append-only).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<BannerPatternLayer> selectedItemBannerPatterns;
        /** [SHIELD-PATTERN] 보조손 스택의 무늬 층. 무늬가 없으면 싣지 않는다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<BannerPatternLayer> offhandItemBannerPatterns;

        /** 인챈트 칸이 없던 호출 지점(테스트 픽스처 등)은 두 손 모두 인챈트 없음이다. */
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId, 0L, 0L, null, null, null);
        }

        /** [GLINT] 박힌 화살 · 갑옷 칸이 없던 호출 지점은 화살 수가 없고 맨몸이다(필드 생략). */
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId,
                    selectedItemEnchantments, offhandItemEnchantments, null, null, null);
        }

        /** [ARROW-GROUND] 인챈트 칸이 없던 호출 지점의 박힌 화살 수. */
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId, Integer arrowCount) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId, 0L, 0L, arrowCount,
                    null, null);
        }

        /** [ARROW-GROUND] 갑옷 칸이 없던 호출 지점은 맨몸이다. */
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId,
                    selectedItemEnchantments, offhandItemEnchantments, arrowCount, null, null);
        }

        /** [UTILITY] 박힌 화살 칸이 없던 호출 지점은 화살 수가 없다. */
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments,
                List<Short> armorItemIds, List<TrimComponent> armorTrims) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId,
                    selectedItemEnchantments, offhandItemEnchantments, null, armorItemIds, armorTrims);
        }

        /** [EQUIPMENT] 갑옷 광택·염색과 [SHIELD-PATTERN] 손 무늬 칸이 없던 호출 지점은 그 필드를 생략한다. */
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount,
                List<Short> armorItemIds, List<TrimComponent> armorTrims) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId,
                    selectedItemEnchantments, offhandItemEnchantments, arrowCount, armorItemIds, armorTrims,
                    null, null, null, null, null);
        }

        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount,
                List<Short> armorItemIds, List<TrimComponent> armorTrims, List<Object> armorEnchantments,
                List<Integer> armorLeatherColors, List<BannerPatternLayer> selectedItemBannerPatterns,
                List<BannerPatternLayer> offhandItemBannerPatterns, Boolean spearUsing) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId, selectedItemEnchantments, offhandItemEnchantments, arrowCount, armorItemIds, armorTrims, armorEnchantments, armorLeatherColors, selectedItemBannerPatterns, offhandItemBannerPatterns, spearUsing, null, null);
        }

        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount,
                List<Short> armorItemIds, List<TrimComponent> armorTrims, List<Object> armorEnchantments,
                List<Integer> armorLeatherColors, List<BannerPatternLayer> selectedItemBannerPatterns,
                List<BannerPatternLayer> offhandItemBannerPatterns, Boolean spearUsing,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> selectedItemPotDecorations,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> offhandItemPotDecorations) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId,
                    selectedItemEnchantments, offhandItemEnchantments, arrowCount, armorItemIds,
                    armorTrims, armorEnchantments, armorLeatherColors, selectedItemBannerPatterns,
                    offhandItemBannerPatterns, spearUsing, selectedItemPotDecorations,
                    offhandItemPotDecorations, false);
        }

        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch,
                boolean onFire, short selectedItemId, short offhandItemId,
                Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount,
                List<Short> armorItemIds, List<TrimComponent> armorTrims, List<Object> armorEnchantments,
                List<Integer> armorLeatherColors, List<BannerPatternLayer> selectedItemBannerPatterns,
                List<BannerPatternLayer> offhandItemBannerPatterns, Boolean spearUsing,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> selectedItemPotDecorations,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> offhandItemPotDecorations,
                boolean crouching) {
            this.crouching = crouching;
            this.selectedItemPotDecorations = selectedItemPotDecorations == null || selectedItemPotDecorations.isEmpty() ? null : List.copyOf(selectedItemPotDecorations);
            this.offhandItemPotDecorations = offhandItemPotDecorations == null || offhandItemPotDecorations.isEmpty() ? null : List.copyOf(offhandItemPotDecorations);
            this.nickname = nickname;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onFire = onFire;
            this.selectedItemId = selectedItemId;
            this.offhandItemId = offhandItemId;
            this.selectedItemEnchantments = selectedItemEnchantments;
            this.offhandItemEnchantments = offhandItemEnchantments;
            this.arrowCount = arrowCount;
            this.armorItemIds = armorItemIds;
            this.armorTrims = armorTrims;
            this.spearUsing = spearUsing;
            this.armorEnchantments = armorEnchantments;
            this.armorLeatherColors = armorLeatherColors;
            this.selectedItemBannerPatterns = selectedItemBannerPatterns;
            this.offhandItemBannerPatterns = offhandItemBannerPatterns;
        }
        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch, boolean onFire, short selectedItemId, short offhandItemId, Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount, List<Short> armorItemIds, List<TrimComponent> armorTrims, Boolean spearUsing) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId, selectedItemEnchantments, offhandItemEnchantments, arrowCount, armorItemIds, armorTrims, null, null, null, null, spearUsing);
        }

        public PlayerPose(String nickname, double x, double y, double z, float yaw, float pitch, boolean onFire, short selectedItemId, short offhandItemId, Object selectedItemEnchantments, Object offhandItemEnchantments, Integer arrowCount, List<Short> armorItemIds, List<TrimComponent> armorTrims, List<Object> armorEnchantments, List<Integer> armorLeatherColors, List<BannerPatternLayer> selectedItemBannerPatterns, List<BannerPatternLayer> offhandItemBannerPatterns) {
            this(nickname, x, y, z, yaw, pitch, onFire, selectedItemId, offhandItemId, selectedItemEnchantments, offhandItemEnchantments, arrowCount, armorItemIds, armorTrims, armorEnchantments, armorLeatherColors, selectedItemBannerPatterns, offhandItemBannerPatterns, null);
        }

    }

    @Getter
    @AllArgsConstructor
    public static class Self {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final int health;
        /** Required authoritative health ceiling for HUD heart rows. */
        private final int maxHealth;
        private final List<InventorySlot> inventory;
        /** 투구·흉갑·각반·장화 순서의 필수 네 착용 스택. */
        private final List<InventorySlot> armor;
        private final String mainArm;
        /** 논리 슬롯 40인 단일 보조손 스택. 빈손도 필수 빈 슬롯으로 전송합니다. */
        private final InventorySlot offhand;
        private final int selectedSlot;
        // 화상 여부(§11.5). welcome.self 로 본인 1인칭 화염 오버레이/파티클에 쓰인다.
        private final boolean onFire;
        /** 활성 상태이상(FX A1). 재접속 복원 직후의 권위 목록이다. */
        private final List<EffectDto> effects;
        /** 저장된 허기 0~20(SURV-H). 필드가 없던 세이브는 만복 20으로 복원된다. */
        private final int hunger;
        /** 저장된 saturation, 1/1000 단위 정수. 필드가 없던 세이브는 5.0(=5000)으로 복원된다. */
        private final int saturationMilli;
        /** [SURV-X] 저장된 누적 경험치. 필드가 없던 세이브는 0으로 복원된다. */
        private final int xpTotal;
        /**
         * [HUD-VANILLA] 입장 시점의 착용 방어구 합산 방어 점수 0~20. 권위가 이미 갖고 있던 값이며
         * 이후 갱신은 {@link ElytraState} 가 나른다.
         */
        private final int armorPoints;
        /** [HUD-VANILLA] 입장 시점의 영속된 흡수 체력 포인트. */
        private final int absorption;

    }

    @Getter
    @AllArgsConstructor
    public static class Welcome {
        private com.gameexpert.world.WorldGenerationProfile generationProfile =
                com.gameexpert.world.WorldGenerationProfiles.newWorldProfile();

        public Welcome withGenerationProfile(com.gameexpert.world.WorldGenerationProfile profile) {
            this.generationProfile = com.gameexpert.world.WorldGenerationProfiles.requireSupported(profile);
            return this;
        }
        private final String type;
        private final Self self;
        private final long worldTime;
        /** 서버 누적 일수(0부터 시작). 클라이언트 표시 Day는 1부터 계산합니다. */
        private final long dayCount;
        private final List<PlayerPose> players;
        private final List<MobSpawnDto> mobs;
        private final List<ItemEntityDto> items;
        /** Silent initial hydration; unlike projectileSpawn this never represents a launch event. */
        private final List<ProjectileSpawn> projectiles;
        private final List<BoatDto> boats;
        private final List<CushionDto> cushions;
        private final List<FireBlock> burningBlocks;
        private final long fireRevision;
        /** 입장 시 활성 범위 안에서 음식이 올라간 모닥불 상태입니다. */
        private final List<CampfireSnapshot> campfires;
        private final WeatherState weather;
        /** Explicit server QA configuration; clients only report this state. */
        private final boolean qaInvulnerable;
        /** 월드 난이도(easy|normal|hard). 난이도 도입 전 서버는 이 필드를 보내지 않는다. */
        private final Difficulty difficulty;
        /** [SURV-X] 입장 시점에 살아 있는 경험치 구슬. */
        private final List<XpOrbDto> xpOrbs;
        /** 입장 시점에 추적 중인 이동식 Java 1.21.4 PrimedTnt 엔티티. */
        private final List<PrimedTntDto> primedTnt;
        /** Authenticated generated non-mob entities, sorted by authoritative entity ID. */
        private final List<GeneratedEntitySnapshot> generatedEntities;
        /**
         * [QA5-3] {@code game.qa-godmode}. {@code qaInvulnerable} 이 끄는 축은 환경 피해뿐이고,
         * 이 스위치는 몹 근접·투사체·폭발까지 끈다. 두 축을 한 필드로 합치면 QA 가 "무엇이 꺼졌는지"
         * 를 구분할 수 없으므로 별도 필드로 붙인다. 클라이언트는 보고만 하고 제어하지 않는다.
         */
        private final boolean qaGodmode;

        public Welcome(Self self, long worldTime, List<PlayerPose> players,
                List<MobSpawnDto> mobs, List<ItemEntityDto> items, List<BoatDto> boats,
                List<CushionDto> cushions,
                List<FireBlock> burningBlocks, long fireRevision,
                WeatherState weather, boolean qaInvulnerable, long dayCount,
                List<CampfireSnapshot> campfires) {
            this(self, worldTime, players, mobs, items, boats, cushions, burningBlocks, fireRevision,
                    weather, qaInvulnerable, dayCount, campfires, Difficulty.DEFAULT, List.of(),
                    List.of(), List.of(), false);
        }

        public Welcome(Self self, long worldTime, List<PlayerPose> players,
                List<MobSpawnDto> mobs, List<ItemEntityDto> items, List<BoatDto> boats,
                List<CushionDto> cushions,
                List<FireBlock> burningBlocks, long fireRevision,
                WeatherState weather, boolean qaInvulnerable, long dayCount,
                List<CampfireSnapshot> campfires, Difficulty difficulty) {
            this(self, worldTime, players, mobs, items, boats, cushions, burningBlocks, fireRevision,
                    weather, qaInvulnerable, dayCount, campfires, difficulty, List.of(), List.of(),
                    List.of(), false);
        }

        public Welcome(Self self, long worldTime, List<PlayerPose> players,
                List<MobSpawnDto> mobs, List<ItemEntityDto> items, List<BoatDto> boats,
                List<CushionDto> cushions,
                List<FireBlock> burningBlocks, long fireRevision,
                WeatherState weather, boolean qaInvulnerable, long dayCount,
                List<CampfireSnapshot> campfires, Difficulty difficulty, List<XpOrbDto> xpOrbs) {
            this(self, worldTime, players, mobs, items, boats, cushions, burningBlocks, fireRevision,
                    weather, qaInvulnerable, dayCount, campfires, difficulty, xpOrbs, List.of(),
                    List.of(), false);
        }

        public Welcome(Self self, long worldTime, List<PlayerPose> players,
                List<MobSpawnDto> mobs, List<ItemEntityDto> items, List<BoatDto> boats,
                List<CushionDto> cushions,
                List<FireBlock> burningBlocks, long fireRevision,
                WeatherState weather, boolean qaInvulnerable, long dayCount,
                List<CampfireSnapshot> campfires, Difficulty difficulty, List<XpOrbDto> xpOrbs,
                List<PrimedTntDto> primedTnt) {
            this(self, worldTime, players, mobs, items, boats, cushions, burningBlocks, fireRevision,
                    weather, qaInvulnerable, dayCount, campfires, difficulty, xpOrbs, primedTnt,
                    List.of(), false);
        }

        /** [QA5-3] qa-godmode 필드까지 싣는 정본 생성자. */
        public Welcome(Self self, long worldTime, List<PlayerPose> players,
                List<MobSpawnDto> mobs, List<ItemEntityDto> items, List<BoatDto> boats,
                List<CushionDto> cushions,
                List<FireBlock> burningBlocks, long fireRevision,
                WeatherState weather, boolean qaInvulnerable, long dayCount,
                List<CampfireSnapshot> campfires, Difficulty difficulty, List<XpOrbDto> xpOrbs,
                List<PrimedTntDto> primedTnt, List<ProjectileSpawn> projectiles,
                boolean qaGodmode) {
            this(self, worldTime, players, mobs, items, boats, cushions, burningBlocks, fireRevision,
                    weather, qaInvulnerable, dayCount, campfires, difficulty, xpOrbs, primedTnt,
                    projectiles, List.of(), qaGodmode);
        }

        public Welcome(Self self, long worldTime, List<PlayerPose> players,
                List<MobSpawnDto> mobs, List<ItemEntityDto> items, List<BoatDto> boats,
                List<CushionDto> cushions,
                List<FireBlock> burningBlocks, long fireRevision,
                WeatherState weather, boolean qaInvulnerable, long dayCount,
                List<CampfireSnapshot> campfires, Difficulty difficulty, List<XpOrbDto> xpOrbs,
                List<PrimedTntDto> primedTnt, List<ProjectileSpawn> projectiles,
                List<GeneratedEntitySnapshot> generatedEntities, boolean qaGodmode) {
            this(com.gameexpert.world.WorldGenerationProfiles.newWorldProfile(),
                    "welcome", self, worldTime, dayCount, players, mobs, items, projectiles, boats,
                    cushions, burningBlocks, fireRevision, campfires, weather, qaInvulnerable,
                    difficulty, xpOrbs, primedTnt, generatedEntities, qaGodmode);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class FireBlock {
        private final int x;
        private final int y;
        private final int z;
    }

    @Getter
    @AllArgsConstructor
    public static class BlockFireUpdate {
        private final String type;
        private final long revision;
        private final List<FireBlock> blocks;

        public BlockFireUpdate(long revision, List<FireBlock> blocks) {
            this("blockFireUpdate", revision, blocks);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PlayerJoin {
        private final String type;
        private final String nickname;

        public PlayerJoin(String nickname) {
            this("playerJoin", nickname);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PlayerLeave {
        private final String type;
        private final String nickname;

        public PlayerLeave(String nickname) {
            this("playerLeave", nickname);
        }
    }

    /** 서버가 마지막 상태 확정과 닉네임 점유 해제를 마친 명시적 퇴장 응답입니다. */
    @Getter
    @AllArgsConstructor
    public static class WorldLeaveReady {
        private final String type;

        public WorldLeaveReady() {
            this("worldLeaveReady");
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PlayerMoves {
        private final String type;
        private final List<PlayerPose> players;

        public PlayerMoves(List<PlayerPose> players) {
            this("playerMoves", players);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Chat {
        private final String type;
        private final String sender;
        private final String content;
        private final LocalDateTime timestamp;

        public Chat(String sender, String content, LocalDateTime timestamp) {
            this("chat", sender, content, timestamp);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Block {
        private final int x;
        private final int y;
        private final int z;
        private final short blockType;
        private final short state;
        private final long revision;
    }

    @Getter
    @AllArgsConstructor
    public static class BlockUpdate {
        private final String type;
        private final List<Block> blocks;
        /** 각 block revision과 함께 gap을 판정하는 필수 청크별 revision 범위입니다. */
        private final List<ChunkVersion> chunkVersions;

        public BlockUpdate(List<Block> blocks) {
            this("blockUpdate", blocks, chunkVersions(blocks));
        }

        private static List<ChunkVersion> chunkVersions(List<Block> blocks) {
            HashMap<Long, ChunkVersionRange> ranges = new HashMap<>();
            for (Block block : blocks) {
                if (block.getRevision() <= 0) continue;
                int cx = Math.floorDiv(block.getX(), 16);
                int cz = Math.floorDiv(block.getZ(), 16);
                long key = ((long) cx << 32) ^ (cz & 0xffff_ffffL);
                ranges.computeIfAbsent(key, ignored -> new ChunkVersionRange(cx, cz))
                        .include(block.getRevision());
            }
            return ranges.values().stream()
                    .map(ChunkVersionRange::toDto)
                    .sorted(java.util.Comparator.comparingInt(ChunkVersion::getCx)
                            .thenComparingInt(ChunkVersion::getCz))
                    .toList();
        }

        private static final class ChunkVersionRange {
            private final int cx;
            private final int cz;
            private long min = Long.MAX_VALUE;
            private long max;

            private ChunkVersionRange(int cx, int cz) {
                this.cx = cx;
                this.cz = cz;
            }

            private void include(long version) {
                min = Math.min(min, version);
                max = Math.max(max, version);
            }

            private ChunkVersion toDto() {
                return new ChunkVersion(cx, cz, min - 1, max);
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public static class BannerBlockState {
        private final int x;
        private final int y;
        private final int z;
        private final List<BannerPatternLayer> patterns;
    }

    @Getter
    @AllArgsConstructor
    public static class BannerUpdate {
        private final String type;
        private final BannerBlockState banner;

        public BannerUpdate(BannerBlockState banner) {
            this("bannerUpdate", banner);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class BannerRemove {
        private final String type;
        private final int x;
        private final int y;
        private final int z;

        public BannerRemove(int x, int y, int z) {
            this("bannerRemove", x, y, z);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class SignBlockState {
        private final int x;
        private final int y;
        private final int z;
        private final List<String> lines;
    }

    @Getter
    @AllArgsConstructor
    public static class SignOpen {
        private final String type;
        private final SignBlockState sign;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;
        public SignOpen(SignBlockState sign) { this(sign, null); }
        public SignOpen(SignBlockState sign, Long requestId) { this("signOpen", sign, requestId); }
        public SignOpen(String type, SignBlockState sign) { this(type, sign, null); }
    }

    @Getter
    @AllArgsConstructor
    public static class SignUpdate {
        private final String type;
        private final SignBlockState sign;
        public SignUpdate(SignBlockState sign) { this("signUpdate", sign); }
    }

    @Getter
    @AllArgsConstructor
    public static class SignRemove {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        public SignRemove(int x, int y, int z) { this("signRemove", x, y, z); }
    }

    /** resident cache 밖의 full snapshot 요청은 생성/활성화를 유발하지 않고 명시적으로 거절합니다. */
    @Getter
    @AllArgsConstructor
    public static class ChunkSnapshotUnavailable {
        private final String type;
        private final int cx;
        private final int cz;
        private final long worldEpoch;

        public ChunkSnapshotUnavailable(int cx, int cz, long worldEpoch) {
            this("chunkSnapshotUnavailable", cx, cz, worldEpoch);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Error {
        private final String type;
        private final String code;
        private final ErrorContext context;

        public Error(String code) {
            this("error", code, null);
        }

        public Error(String code, String action, int x, int y, int z,
                int currentBlock, int requestedBlock) {
            this("error", code,
                    new ErrorContext(action, x, y, z, currentBlock, requestedBlock));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorContext {
        private final String action;
        private final int x;
        private final int y;
        private final int z;
        private final int currentBlock;
        private final int requestedBlock;
    }

    @Getter
    @AllArgsConstructor
    public static class Pong {
        private final String type;

        public Pong() {
            this("pong");
        }
    }

    // ── P5 확정 타입(서버 권위 틱 엔진) ──

    /** 월드 시간 동기화. 100틱마다 브로드캐스트 + welcome에 현재값 포함(CONTRACT §10.2). */
    @Getter
    @AllArgsConstructor
    public static class TimeSync {
        private final String type;
        private final long worldTime;
        /** Y 트랙이 새 값을 제공하기 전까지는 null로 생략되는 하위호환 필드입니다. */
        private final Long dayCount;

        public TimeSync(long worldTime) {
            this("timeSync", worldTime, null);
        }

        public TimeSync(long worldTime, long dayCount) {
            this("timeSync", worldTime, dayCount);
        }
    }

    /** 개인 체력 갱신(cause: fall/drown/lava/regen 등). 허기(SURV-H)도 같은 개인 메시지로 함께 나간다. */
    @Getter
    @AllArgsConstructor
    public static class HealthUpdate {
        public static final Set<String> CAUSES = Set.of(
                "melee", "arrow", "explosion", "fall_small", "fall_big", "on_fire", "in_fire", "lava", "drown", "cactus", "freeze",
                "fly_into_wall", "ender_pearl", "lightning", "geyser",
                "poison", "magic", "starve",
                "food", "regen", "golden_apple_regeneration", "respawn",
                // 체력은 그대로고 허기만 바뀐 갱신. 클라는 체력 비교가 같으므로 피격/회복 연출을 내지 않는다.
                "hunger");
        private final String type;
        private final int health;
        private final int maxHealth;
        private final String cause;
        /** Required authoritative hunger value in the closed current protocol. */
        private final int hunger;
        /** 은닉 saturation, 1/1000 단위 정수(HungerRules.MILLI). HUD 표시는 선택. */
        private final int saturationMilli;

        public HealthUpdate(int health, int maxHealth, String cause, int hunger,
                int saturationMilli) {
            this("healthUpdate", health, maxHealth, cause, hunger, saturationMilli);
            if (cause == null || !CAUSES.contains(cause)) throw new IllegalArgumentException("Unknown healthUpdate cause: " + cause);
        }
    }

    /** 연출용 피격 알림(월드 브로드캐스트). */
    @Getter
    @AllArgsConstructor
    public static class PlayerHurt {
        public static final Set<String> CAUSES = Set.of(
                "melee", "arrow", "explosion", "fall_small", "fall_big", "on_fire", "in_fire", "lava", "drown", "cactus", "freeze",
                "fly_into_wall", "ender_pearl", "lightning", "geyser",
                "poison", "magic", "starve");
        private final String type;
        private final String nickname;
        private final String cause;
        /** 기본 넓백 입력(blocks/s). 클라가 currentVelocity/2 후 더한다. */
        private final double kbX;
        private final double kbY;
        private final double kbZ;
        /** Sprint +1 넓백의 2차 입력(blocks/s). 기본 합성 후 다시 currentVelocity/2 후 더한다. */
        private final double kbBonusX;
        private final double kbBonusZ;

        public PlayerHurt(String nickname, String cause,
                          double kbX, double kbY, double kbZ,
                          double kbBonusX, double kbBonusZ) {
            this("playerHurt", nickname, cause, kbX, kbY, kbZ, kbBonusX, kbBonusZ);
            if (cause == null || !CAUSES.contains(cause)) throw new IllegalArgumentException("Unknown playerHurt cause: " + cause);
        }
    }

    /** 사망 알림. cause: mob/arrow/explosion/fall/on_fire/lava/drown/cactus, killer: 가해자 식별자 또는 null. */
    @Getter
    @AllArgsConstructor
    public static class PlayerDeath {
        /**
         * {@code WorldTickLoop.normalizeDeathCause} 가 내보낼 수 있는 값의 전부여야 한다. 하나라도 빠지면
         * 생성자가 틱 안에서 던지고, 그 예외가 {@code justDied()} 래치를 지우기 전에 나가므로 매 틱 같은
         * 자리에서 되풀이돼 월드가 영구히 멈춘다(hard 난이도 아사가 실제로 그랬다).
         * {@code WsDeathCauseParityTest} 가 두 집합의 일치를 강제한다.
         */
        public static final Set<String> CAUSES = Set.of(
                "mob", "arrow", "explosion", "fall", "on_fire", "in_fire", "lava", "drown", "cactus", "freeze",
                "fly_into_wall", "ender_pearl", "lightning",
                "magic", "starve");
        private final String type;
        private final String nickname;
        private final String cause;
        private final String killer;

        public PlayerDeath(String nickname, String cause, String killer) {
            this("playerDeath", nickname, cause, killer);
            if (cause == null || !CAUSES.contains(cause)) {
                throw new IllegalArgumentException("Unknown playerDeath cause: " + cause);
            }
        }
    }

    /** 개인 리스폰 확정(위치·체력 복원). */
    @Getter
    @AllArgsConstructor
    public static class RespawnSelf {
        private final String type;
        private final double x;
        private final double y;
        private final double z;
        private final int health;

        public RespawnSelf(double x, double y, double z, int health) {
            this("respawn", x, y, z, health);
        }
    }

    /**
     * [GOLD-FOOD] 개인 순간이동 확정. 이동은 설계상 클라 권위지만 <b>후렴과 순간이동만은</b>
     * 서버가 목적지를 정해 통보한다 — 굴림이 {@code ChorusFruitRules} 하나에 있어야 정적판과
     * 결과가 갈리지 않기 때문이다. 클라는 리스폰과 같은 자리에서 위치를 확정한다.
     */
    @Getter
    @AllArgsConstructor
    public static class PlayerTeleportSelf {
        private final String type;
        private final double x;
        private final double y;
        private final double z;

        private final PositionDto from;

        public PlayerTeleportSelf(double x, double y, double z) {
            this("playerTeleport", x, y, z, null);
        }

        public PlayerTeleportSelf(double x, double y, double z, PositionDto from) {
            this("playerTeleport", x, y, z, from);
        }
    }

    /** Observer discontinuity; rotation and all other replicated state remain unchanged. */
    @Getter
    public static final class PlayerRelocated {
        private final String type = "playerRelocated";
        private final String nickname;
        private final double x, y, z;
        public PlayerRelocated(String nickname, double x, double y, double z) {
            this.nickname = nickname; this.x = x; this.y = y; this.z = z;
        }
    }

    /**
     * [END-GATEWAY] 엔드 관문 빔 사건. 바닐라 {@code TheEndGatewayBlockEntity} 의 두 빔을 서버 권위로 알린다:
     * {@code phase="spawn"} 은 새 관문 블록 엔티티(나이 0, {@code isSpawning} 200 게임 틱, 마젠타·높이 maxY),
     * {@code phase="cooldown"} 은 {@code triggerCooldown}(블록 이벤트 1, 쿨다운 40 게임 틱, 보라·높이 50).
     * 좌표는 관문 블록. 블록 이벤트처럼 관문 64 블록 안의 플레이어에게만 간다. 주기(2400 게임 틱) 주의
     * 빔은 월드 시계로 클라가 스스로 푼다(CONTRACT §11A).
     */
    @Getter
    @AllArgsConstructor
    public static class EndGatewayBeam {
        public static final java.util.Set<String> PHASES = java.util.Set.of("spawn", "cooldown");
        private final String type;
        private final String phase;
        private final int x;
        private final int y;
        private final int z;

        public EndGatewayBeam(String phase, int x, int y, int z) {
            this("endGatewayBeam", phase, x, y, z);
            if (!PHASES.contains(phase)) throw new IllegalArgumentException("Unknown endGatewayBeam phase: " + phase);
        }
    }

    /** 리스폰 알림(월드 브로드캐스트). */
    @Getter
    @AllArgsConstructor
    public static class PlayerRespawn {
        private final String type;
        private final String nickname;
        private final double x;
        private final double y;
        private final double z;

        public PlayerRespawn(String nickname, double x, double y, double z) {
            this("playerRespawn", nickname, x, y, z);
        }
    }

    // ── P6 확정 타입(몹 · 투사체 · 전투 · 인벤토리) ──

    /** mobSpawn/welcome.mobs 원소: 렌더링에 필요한 몹 최초 상태. */
    @Getter
    @AllArgsConstructor
    public static class MobSpawnDto {
        private final long id;
        private final String type;
        private final double x;
        private final double y;
        private final double z;
        private final boolean onFire;
        /** 서버 권위 성장 상태. true면 클라이언트가 성체 모델의 절반 크기로 렌더한다. */
        private final boolean baby;
        /** 슬라임 크기. 일반 몹은 0이며, 슬라임의 AABB·모델 배율 정본이다. */
        private final int slimeSize;
        /** 스폰 때 결정되어 수명 동안 유지되는 서버 권위 변종. 없으면 JSON null. */
        private final String variant;
        private final String movementMedium;
        private final int heldItemId;
        private final int offhandItemId;
        private final int helmetItemId;
        private final int chestItemId;
        private final int legsItemId;
        private final int feetItemId;
        private final int carriedBlockId;
        private final String nativeEquipment;
        /** 종별 외형 상태 비트셋. Java Mob.VISUAL_* 상수와 TypeScript 계약이 동일하다. */
        private final int visualFlags;
        /** Exact Nautilus body armor item id; zero for all other/unarmored mobs. */
        private final int nautilusArmorItemId;
        /** Exact ordinary-Horse body armor item id; zero for all other/unarmored mobs. */
        private final int horseArmorItemId;
        private final String actionKind;
        private final String actionPhase;
        private final int actionTicksRemaining;
        /** 실제 공격 이벤트마다 증가하여 클라이언트가 한 번만 재생하는 커서. */
        private final int actionSequence;
        private final long vehicleMobId;
        /** 이름표로 붙인 이름. 없으면 null. 이름이 바뀌면 mobTransform 으로 다시 보낸다. */
        private final String customName;
        /**
         * [GLOWING] 이 몹의 활성 상태이상 프로토콜 이름(열거 순서, 없으면 빈 배열). 바닐라가 몹의
         * 발광 공유 플래그와 효과 입자 목록을 추적 중인 클라에 동기화하는 것과 같은 자리다.
         * 이름 집합은 {@link EffectUpdate#EFFECTS} 와 같다.
         */
        private final List<String> effects;
        /**
         * [MOB-GLINT] 인챈트된 장비 칸 비트(주손 1 · 부손 2 · 투구 4 · 흉갑 8 · 각반 16 · 부츠 32). 바닐라가 몹 장비
         * ItemStack 을 보내 {@code hasFoil} 로 광택을 그리는 자리의 칸별 인챈트 유무다. append-only.
         */
        private final int equipmentGlint;

        /**
         * [MOB-LOOK] 바닐라 netHeadYaw(= yHeadRot - yBodyRot) · headPitch(xRot, 양수 = 아래), 라디안.
         * append-only 필드이며 없는 옛 권위는 0 이다({@code MobHeadLook}).
         */
        private final double headYaw;
        private final double headPitch;

        /** 활성 빔의 권위 표적 [x,y,z]. 일반 몹/준비/종료 상태는 null. */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private final double[] guardianBeamTarget;

        public MobSpawnDto(long id, String type, double x, double y, double z, boolean onFire,
                boolean baby, int slimeSize, String variant, String movementMedium, int heldItemId,
                int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId,
                int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId,
                int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining,
                int actionSequence, long vehicleMobId, String customName, List<String> effects) {
            this(id, type, x, y, z, onFire, baby, slimeSize, variant, movementMedium, heldItemId,
                    offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId,
                    nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind,
                    actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, customName, effects, 0, 0.0, 0.0, null);
        }
        public MobSpawnDto(long id, String type, double x, double y, double z, boolean onFire, boolean baby, int slimeSize, String variant, String movementMedium, int heldItemId, int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence, long vehicleMobId, String customName, List<String> effects, int equipmentGlint) {
            this(id, type, x, y, z, onFire, baby, slimeSize, variant, movementMedium, heldItemId, offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, customName, effects, equipmentGlint, 0, 0, null);
        }

        public MobSpawnDto(long id, String type, double x, double y, double z, boolean onFire, boolean baby, int slimeSize, String variant, String movementMedium, int heldItemId, int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence, long vehicleMobId, String customName, List<String> effects, double headYaw, double headPitch) {
            this(id, type, x, y, z, onFire, baby, slimeSize, variant, movementMedium, heldItemId, offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, customName, effects, 0, headYaw, headPitch, null);
        }

        public MobSpawnDto(long id, String type, double x, double y, double z, boolean onFire, boolean baby, int slimeSize, String variant, String movementMedium, int heldItemId, int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence, long vehicleMobId, String customName, List<String> effects, double headYaw, double headPitch, double[] guardianBeamTarget) {
            this(id, type, x, y, z, onFire, baby, slimeSize, variant, movementMedium, heldItemId, offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, customName, effects, 0, headYaw, headPitch, guardianBeamTarget);
        }

    }

    /** 신규 스폰 몹 배칭(틱당 1회). */
    @Getter
    @AllArgsConstructor
    public static class MobSpawn {
        private final String type;
        private final List<MobSpawnDto> mobs;

        public MobSpawn(List<MobSpawnDto> mobs) {
            this("mobSpawn", mobs);
        }
    }

    /** Atomic same-id species replacement after the authority commits the conversion. */
    @Getter
    @AllArgsConstructor
    public static class MobTransform {
        private final String type;
        private final long eventId;
        private final MobSpawnDto mob;

        public MobTransform(long eventId, MobSpawnDto mob) {
            this("mobTransform", eventId, mob);
        }
    }

    /** mobUpdate 원소: 이번 틱 변경분(발좌표·바라보는 방향·상태 문자열). */
    @Getter
    @AllArgsConstructor
    public static class MobUpdateDto {
        private final long id;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
        private final String state;
        private final boolean onFire;
        /** 성장 완료 전후의 서버 권위 상태. */
        private final boolean baby;
        private final String movementMedium;
        private final int heldItemId;
        private final int offhandItemId;
        private final int helmetItemId;
        private final int chestItemId;
        private final int legsItemId;
        private final int feetItemId;
        private final int carriedBlockId;
        private final String nativeEquipment;
        private final int visualFlags;
        /** Exact Nautilus body armor item id; zero for all other/unarmored mobs. */
        private final int nautilusArmorItemId;
        /** Exact ordinary-Horse body armor item id; zero for all other/unarmored mobs. */
        private final int horseArmorItemId;
        private final String actionKind;
        private final String actionPhase;
        private final int actionTicksRemaining;
        private final int actionSequence;
        private final long vehicleMobId;
        /** [GLOWING] {@link MobSpawnDto#getEffects()} 와 같은 활성 상태이상 이름 목록. */
        private final List<String> effects;
        /** [MOB-GLINT] {@link MobSpawnDto#getEquipmentGlint()} 와 같은 칸 비트. */
        private final int equipmentGlint;

        /** [MOB-LOOK] {@link MobSpawnDto#getHeadYaw()} · {@link MobSpawnDto#getHeadPitch()} 와 같다. */
        private final double headYaw;
        private final double headPitch;

        /** 활성 빔의 권위 표적 [x,y,z]. 일반 몹/준비/종료 상태는 null. */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private final double[] guardianBeamTarget;

        public MobUpdateDto(long id, double x, double y, double z, double yaw, String state,
                boolean onFire, boolean baby, String movementMedium, int heldItemId, int offhandItemId,
                int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId,
                String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId,
                String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence,
                long vehicleMobId, List<String> effects) {
            this(id, x, y, z, yaw, state, onFire, baby, movementMedium, heldItemId, offhandItemId,
                    helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment,
                    visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase,
                    actionTicksRemaining, actionSequence, vehicleMobId, effects, 0, 0.0, 0.0, null);
        }
        public MobUpdateDto(long id, double x, double y, double z, double yaw, String state, boolean onFire, boolean baby, String movementMedium, int heldItemId, int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence, long vehicleMobId, List<String> effects, int equipmentGlint) {
            this(id, x, y, z, yaw, state, onFire, baby, movementMedium, heldItemId, offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, effects, equipmentGlint, 0, 0, null);
        }

        public MobUpdateDto(long id, double x, double y, double z, double yaw, String state, boolean onFire, boolean baby, String movementMedium, int heldItemId, int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence, long vehicleMobId, List<String> effects, double headYaw, double headPitch) {
            this(id, x, y, z, yaw, state, onFire, baby, movementMedium, heldItemId, offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, effects, 0, headYaw, headPitch, null);
        }

        public MobUpdateDto(long id, double x, double y, double z, double yaw, String state, boolean onFire, boolean baby, String movementMedium, int heldItemId, int offhandItemId, int helmetItemId, int chestItemId, int legsItemId, int feetItemId, int carriedBlockId, String nativeEquipment, int visualFlags, int nautilusArmorItemId, int horseArmorItemId, String actionKind, String actionPhase, int actionTicksRemaining, int actionSequence, long vehicleMobId, List<String> effects, double headYaw, double headPitch, double[] guardianBeamTarget) {
            this(id, x, y, z, yaw, state, onFire, baby, movementMedium, heldItemId, offhandItemId, helmetItemId, chestItemId, legsItemId, feetItemId, carriedBlockId, nativeEquipment, visualFlags, nautilusArmorItemId, horseArmorItemId, actionKind, actionPhase, actionTicksRemaining, actionSequence, vehicleMobId, effects, 0, headYaw, headPitch, guardianBeamTarget);
        }

    }

    /** 변경된 몹 배칭(틱당 1회, 변경분만). */
    @Getter
    @AllArgsConstructor
    public static class MobUpdate {
        private final String type;
        private final List<MobUpdateDto> mobs;

        public MobUpdate(List<MobUpdateDto> mobs) {
            this("mobUpdate", mobs);
        }
    }

    /** 몹 소멸 배칭(reason: far/daylight/exploded/death 등, 같은 사유끼리 묶음). */
    @Getter
    @AllArgsConstructor
    public static class MobDespawn {
        private final String type;
        private final List<Long> mobIds;
        private final String reason;

        public MobDespawn(List<Long> mobIds, String reason) {
            this("mobDespawn", mobIds, reason);
        }
    }

    /** 몹 피격 연출(피해를 준 몹 1마리). */
    @Getter
    @AllArgsConstructor
    public static class MobHurt {
        private final String type;
        private final long mobId;
        private final boolean lethal;

        public MobHurt(long mobId, boolean lethal) {
            this("mobHurt", mobId, lethal);
        }
    }

    /**
     * 서버가 확정한 플레이어 근접 공격 결과.
     *
     * [CRIT-FX] {@code mobId}/{@code enchanted} 는 뒤에 덧붙인 선택 필드다(append-only —
     * 기존 5인자 생성자는 그대로 두어 옛 호출자가 깨지지 않는다). 바닐라의
     * {@code ClientboundAnimatePacket(entity, 4|5)} 처럼 **어느 엔티티에** CRIT /
     * ENCHANTED_HIT emitter 를 붙일지 클라가 알아야 하므로 대상 id 가 함께 간다.
     */
    @Getter
    @AllArgsConstructor
    public static class CombatHit {
        private final String type;
        private final long eventId;
        private final boolean critical;
        private final double x;
        private final double y;
        private final double z;
        private final long mobId;
        private final boolean enchanted;
        /**
         * [ENCHANT-WIDE] 이 명중이 휩쓸기 공격(바닐라 {@code Player.doSweepAttack})을 냈는가. append-only
         * 선택 필드라 휩쓸기가 아니면 싣지 않는다(옛 combatHit 바이트 그대로). 클라는 자기 위치 + 시선
         * 앞 1블록에 SWEEP_ATTACK 파티클을 그린다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean sweep;
        /**
         * [SPEAR-KINETIC] 창 찌르기·돌진 명중(바닐라 {@code stabAttack → attackVisualEffects(…, stab=true, …)}).
         * 찌르기는 {@code entity.player.attack.strong/weak} 를 내지 않는다(창 소리가 따로 난다). append-only 선택 필드.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean stab;

        public CombatHit(String type, long eventId, boolean critical, double x, double y, double z,
                long mobId, boolean enchanted, Boolean sweep) {
            this(type, eventId, critical, x, y, z, mobId, enchanted, sweep, null);
        }

        /** [SPEAR-KINETIC] 창 찌르기 명중(치명타·휩쓸기 없음). */
        public static CombatHit stab(long eventId, double x, double y, double z, long mobId, boolean enchanted) {
            return new CombatHit("combatHit", eventId, false, x, y, z, mobId, enchanted, null, Boolean.TRUE);
        }

        public CombatHit(long eventId, boolean critical, double x, double y, double z) {
            this(eventId, critical, x, y, z, 0L, false);
        }

        public CombatHit(long eventId, boolean critical, double x, double y, double z,
                long mobId, boolean enchanted) {
            this("combatHit", eventId, critical, x, y, z, mobId, enchanted, null);
        }

        public CombatHit(long eventId, boolean critical, double x, double y, double z,
                long mobId, boolean enchanted, boolean sweep) {
            this("combatHit", eventId, critical, x, y, z, mobId, enchanted, sweep ? Boolean.TRUE : null);
        }
    }

    /**
     * [SPEAR-KINETIC] 창 돌진 명중(바닐라 {@code KineticWeapon.damageEntities} 의 {@code broadcastEntityEvent(user, 2)}).
     * 받는 클라는 {@code makeLocalHitSound} 로 공격자 위치에 {@code item.spear(_wood).hit} 를 내고, 공격자 본인은
     * 1인칭 명중 반동({@code HIT_FEEDBACK_TICKS} 10)을 시작한다. {@code itemType} 은 찌른 창.
     */
    @Getter
    @AllArgsConstructor
    public static class KineticHit {
        private final String type;
        private final long eventId;
        private final String nickname;
        private final short itemType;
        private final double x;
        private final double y;
        private final double z;

        public KineticHit(long eventId, String nickname, short itemType, double x, double y, double z) {
            this("kineticHit", eventId, nickname, itemType, x, y, z);
        }
    }

    /** 서버 권위 몹 발성/행동 사운드. */
    @Getter
    @AllArgsConstructor
    public static class MobSound {
        private final String type;
        private final long eventId;
        private final long mobId;
        private final String kind;
        private final double x;
        private final double y;
        private final double z;

        public MobSound(long eventId, long mobId, String kind, double x, double y, double z) {
            this("mobSound", eventId, mobId, kind, x, y, z);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PositionDto {
        private final double x;
        private final double y;
        private final double z;
    }

    @Getter
    @AllArgsConstructor
    public static class TeleportParticles {
        private final String type;
        private final PositionDto from;
        private final PositionDto to;
        public TeleportParticles(PositionDto from, PositionDto to) {
            this("teleportParticles", from, to);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class MiningParticles {
        private final String type;
        private final int x, y, z, blockType, state;
        public MiningParticles(int x, int y, int z, int blockType, int state) {
            this("miningParticles", x, y, z, blockType, state);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class MooshroomFeed {
        private final String type;
        private final long mobId;
        private final double x, y, z;
        public MooshroomFeed(long mobId, double x, double y, double z) {
            this("mooshroomFeed", mobId, x, y, z);
        }
    }

    /** 성공한 몹 순간이동의 불연속 이동 사건. */
    @Getter
    @AllArgsConstructor
    public static class MobTeleport {
        private final String type;
        private final long eventId;
        private final long mobId;
        private final PositionDto from;
        private final PositionDto to;

        public MobTeleport(long eventId, long mobId, PositionDto from, PositionDto to) {
            this("mobTeleport", eventId, mobId, from, to);
        }
    }

    /**
     * 투사체 생성(종류·발사 원점·초기 속도 블록/틱).
     * owner 는 던진 플레이어 닉네임이고 몹 투사체면 JSON null 이다(append-only 로 늘린 필드).
     */
    @Getter
    @AllArgsConstructor
    public static class ProjectileSpawn {
        private final String type;
        private final String kind;
        private final long id;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;
        private final String owner;
        private final String eggVariant;
        /**
         * [TRIAL-GAP] 이 개체가 싣는 아이템(잔류형 물약 · 효과 구름의 물약 · 불길한 아이템 소환기의
         * 아이템 · 효과 화살). 그 밖의 종류에서는 필드 자체가 없다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Integer itemType;
        /** [TRIAL-GAP] 효과 구름 반지름(바닐라 DATA_RADIUS). 구름이 아니면 필드가 없다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Double radius;
        /**
         * [ARROW-GROUND] 이미 블록에 박혀 있는 화살이면 {@code true}(바닐라 {@code IN_GROUND}). 이때
         * {@code vx,vy,vz} 는 움직임이 아니라 박힌 순간의 방향이고, 클라는 발사음을 내지 않는다.
         * 박히지 않은 개체에서는 필드가 없다(append-only).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean inGround;

        public ProjectileSpawn(String kind, long id,
                               double x, double y, double z, double vx, double vy, double vz,
                               String owner, String eggVariant) {
            this("projectileSpawn", kind, id, x, y, z, vx, vy, vz, owner, eggVariant, null, null,
                    null);
        }

        public ProjectileSpawn(String kind, long id,
                               double x, double y, double z, double vx, double vy, double vz,
                               String owner, String eggVariant, Integer itemType, Double radius) {
            this("projectileSpawn", kind, id, x, y, z, vx, vy, vz, owner, eggVariant, itemType,
                    radius, null);
        }

        public ProjectileSpawn(String kind, long id,
                               double x, double y, double z, double vx, double vy, double vz,
                               String owner, String eggVariant, Integer itemType, Double radius,
                               Boolean inGround) {
            this("projectileSpawn", kind, id, x, y, z, vx, vy, vz, owner, eggVariant, itemType,
                    radius, inGround);
        }
    }

    /**
     * [FISHING] 입질 직전 접근 단계(바닐라 {@code timeUntilHooked})의 물결 한 표본.
     * 권위가 매 틱 "지금 물결이 어디에 있는가"를 방송하고 클라는 그 자리에 파티클만 낸다.
     * ticks 는 남은 접근량(바닐라 틱)이라 0 에 가까울수록 물결이 찌에 붙는다.
     */
    @Getter
    @AllArgsConstructor
    public static class FishingApproach {
        private final String type;
        private final long id;
        private final double x;
        private final double y;
        private final double z;
        private final int ticks;

        public FishingApproach(long id, double x, double y, double z, int ticks) {
            this("fishingApproach", id, x, y, z, ticks);
        }
    }

    /**
     * [FISHING] 후킹된 플레이어가 회수 순간 캐스터 쪽으로 받는 임펄스(블록/초).
     * 몹은 서버가 직접 밀므로 이 메시지는 플레이어가 걸렸을 때만 나간다.
     */
    @Getter
    @AllArgsConstructor
    public static class FishingPull {
        private final String type;
        private final String nickname;
        private final double vx;
        private final double vy;
        private final double vz;

        public FishingPull(String nickname, double vx, double vy, double vz) {
            this("fishingPull", nickname, vx, vy, vz);
        }
    }

    /**
     * [MACE] 권위가 한 플레이어에게 거는 속도 임펄스(블록/초). 이동은 클라 권위라 서버는 속도를 직접
     * 못 바꾸므로 바닐라 {@code ClientboundSetEntityMotionPacket}·폭발 넉백 자리를 이 메시지가 나른다.
     * {@code setVy} 가 있으면 클라는 먼저 수직 속도를 그 값으로 두고 낙하 기준점을 지금 높이로 옮긴 뒤
     * (철퇴 강타의 {@code Vec3.with(Y, 0.01)} + {@code resetFallDistance}) {@code vx,vy,vz} 를 더한다.
     * {@code setVy} 가 없으면 순수 가산({@code Entity.push})이다. append-only 새 메시지다.
     */
    @Getter
    @AllArgsConstructor
    public static class PlayerImpulse {
        private final String type;
        private final String nickname;
        private final double vx;
        private final double vy;
        private final double vz;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Double setVy;

        public PlayerImpulse(String nickname, double vx, double vy, double vz, Double setVy) {
            this("playerImpulse", nickname, vx, vy, vz, setVy);
        }
    }

    /** projectileUpdate 원소: 현재 화살 위치. */
    @Getter
    @AllArgsConstructor
    public static class ProjectilePos {
        private final long id;
        private final double x;
        private final double y;
        private final double z;
        /** [TRIAL-GAP] 효과 구름의 현재 반지름. 구름이 아니면 필드가 없다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Double radius;
        /**
         * [ARROW-GROUND] 이 표본에서 화살이 블록에 박혀 있으면 {@code true}. 박힌 화살은 움직이지 않아
         * 박힌 틱에 한 번만 실린다 — 클라는 이 전이에서 착탄음과 흔들림을 낸다. 그 밖에는 필드가 없다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean inGround;

        public ProjectilePos(long id, double x, double y, double z) {
            this(id, x, y, z, null, null);
        }

        public ProjectilePos(long id, double x, double y, double z, Double radius) {
            this(id, x, y, z, radius, null);
        }
    }

    /** 살아 있는 화살 위치 배칭(틱당 1회). */
    @Getter
    @AllArgsConstructor
    public static class ProjectileUpdate {
        private final String type;
        private final List<ProjectilePos> projectiles;

        public ProjectileUpdate(List<ProjectilePos> projectiles) {
            this("projectileUpdate", projectiles);
        }
    }

    /** 화살의 서버 권위 종결 결과. targetId는 몹 대상이 없으면 JSON null이다. */
    @Getter
    @AllArgsConstructor
    public static class ProjectileRemoval {
        private final long id;
        private final String reason;
        private final double x;
        private final double y;
        private final double z;
        private final Long targetId;
        /**
         * [ARROW-GROUND] {@code reason = "pickup"} 일 때 주운 플레이어 닉네임(바닐라 {@code Player.take}).
         * 그 밖의 사유에서는 필드가 없다(append-only).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String by;

        public ProjectileRemoval(long id, String reason, double x, double y, double z,
                Long targetId) {
            this(id, reason, x, y, z, targetId, null);
        }
    }

    /** 소멸 화살 결과 배칭. */
    @Getter
    @AllArgsConstructor
    public static class ProjectileRemove {
        private final String type;
        private final List<ProjectileRemoval> projectiles;

        public ProjectileRemove(List<ProjectileRemoval> projectiles) {
            this("projectileRemove", projectiles);
        }
    }

    /**
     * 본인 파생 장비 상태. 값이 바뀐 틱에만 개인 송신한다.
     *
     * <p>{@code durability} 는 흉갑 부위 겉날개의 권위 내구도다(미착용·활공 불가면 0). 이동은
     * 클라 권위이므로 클라이언트는 이 값으로만 활공 개시 자격을 판정한다.
     *
     * <p>[HUD-VANILLA] {@code armorPoints}·{@code absorption} 은 권위가 이미 계산하던 값을 HUD 로
     * 내리는 append-only 필드다. 방어구 줄과 흡수 하트는 이 값만 읽고 스스로 파생하지 않는다.
     */
    @Getter
    @AllArgsConstructor
    public static class ElytraState {
        private final String type;
        private final int durability;
        private final int armorPoints;
        private final int absorption;

        public ElytraState(int durability, int armorPoints, int absorption) {
            this("elytraState", durability, armorPoints, absorption);
        }
    }

    /**
     * 활공 중 폭죽 로켓이 주는 바닐라 부스트 창. {@code ticks} 는 바닐라 20 TPS 기준 로켓 수명이며
     * 클라이언트는 그 동안 바닐라 부스트 식을 자기 이동 예측에 적용한다.
     */
    @Getter
    @AllArgsConstructor
    public static class FireworkBoost {
        private final String type;
        private final String nickname;
        private final int ticks;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String finalSceneActionId;

        public FireworkBoost(String nickname, int ticks) {
            this("fireworkBoost", nickname, ticks, null);
        }

        public FireworkBoost(String nickname, int ticks, String finalSceneActionId) {
            this("fireworkBoost", nickname, ticks, finalSceneActionId);
        }
    }

    /** 최종 장면 QA가 일반 상태 갱신 대신 실제 활공 액션 결과에 귀속시키는 개인 영수증. */
    @Getter
    @AllArgsConstructor
    public static class FinalSceneFlightOutcome {
        private final String type;
        private final String finalSceneActionId;
        private final String kind;
        private final boolean accepted;
        private final int healthBefore;
        private final int healthAfter;
        private final int appliedDamage;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean airborneBefore;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean glidingBefore;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean glidingAfter;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean groundedAfter;

        public FinalSceneFlightOutcome(String finalSceneActionId, int healthBefore,
                int healthAfter, int appliedDamage) {
            this("finalSceneFlightOutcome", finalSceneActionId, "wall-impact", true,
                    healthBefore, healthAfter, appliedDamage, null, null, null, null);
        }

        public static FinalSceneFlightOutcome safeLanding(String finalSceneActionId,
                int healthBefore, int healthAfter) {
            return new FinalSceneFlightOutcome("finalSceneFlightOutcome", finalSceneActionId,
                    "safe-landing", true, healthBefore, healthAfter, 0,
                    true, true, false, true);
        }
    }

    /** One connection-bound acknowledgement of real H12 prerequisite state. */
    @Getter
    @AllArgsConstructor
    public static class FinalScenePrerequisiteReceipt {
        private final String type;
        private final String schema;
        private final String fixtureId;
        private final String fixtureChecksum;
        private final String authority;
        private final String world;
        private final String nickname;
        private final String scenario;
        private final String actionNonce;
        private final long revision;
        private final boolean raidActive;
        private final int raidRoleCount;
        private final boolean bossbarVisible;
        private final boolean rewardsReady;
        private final boolean flightReady;
        private final boolean fishingReady;
        private final boolean connectedShapesReady;

        public FinalScenePrerequisiteReceipt(String fixtureChecksum, String nickname,
                String scenario, String actionNonce, long revision, boolean raidActive,
                int raidRoleCount, boolean bossbarVisible, boolean rewardsReady,
                boolean flightReady, boolean fishingReady, boolean connectedShapesReady) {
            this("finalScenePrerequisiteReceipt",
                    "game-expert.final-scene-prerequisites/v1", "content-v1",
                    fixtureChecksum, "spring",
                    com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_NAME,
                    nickname, scenario, actionNonce, revision, raidActive, raidRoleCount,
                    bossbarVisible, rewardsReady, flightReady, fishingReady,
                    connectedShapesReady);
        }
    }

    /** Authenticated schema-2 origin of one naturally generated H12f/H12g target. */
    @Getter
    public static class FinalSceneNaturalEntityBinding {
        private static final java.util.regex.Pattern SHA256 =
                java.util.regex.Pattern.compile("[0-9a-f]{64}");
        private final int schema;
        private final GeneratedEntityTarget target;
        private final long worldId;
        private final long worldSeed;
        private final long worldEpoch;
        private final String installationIdentity;
        private final String authoritativeId;
        private final int encounterOrdinal;
        private final String activationFingerprint;
        private final String provenanceFingerprint;
        private final String stateProvenanceFingerprint;
        private final long revision;
        private final String lifecycle;
        private final int originChunkX;
        private final int originChunkZ;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final long connectionGeneration;
        private final long evidenceGeneration;

        public FinalSceneNaturalEntityBinding(GeneratedEntityTarget target, long worldId,
                long worldSeed, long worldEpoch, String installationIdentity,
                String authoritativeId, int encounterOrdinal, String activationFingerprint,
                String provenanceFingerprint, String stateProvenanceFingerprint, long revision,
                int originChunkX, int originChunkZ, double originX, double originY, double originZ,
                long connectionGeneration, long evidenceGeneration) {
            this.target = java.util.Objects.requireNonNull(target, "natural entity target");
            if (worldId <= 0L || worldEpoch < 0L || encounterOrdinal < 0 || revision < 0L
                    || connectionGeneration <= 0L || evidenceGeneration <= 0L
                    || target.getSchema() != 1 || target.getEntityId() <= 0L
                    || target.getEntityId() > 9_007_199_254_740_991L
                    || !("ARMOR_STAND".equals(target.getKind())
                            || "CHEST_MINECART".equals(target.getKind()))
                    || !Double.isFinite(originX) || !Double.isFinite(originY)
                    || !Double.isFinite(originZ) || originX < -30_000_000.0
                    || originX > 30_000_000.0 || originY < -128.0 || originY > 384.0
                    || originZ < -30_000_000.0 || originZ > 30_000_000.0
                    || Math.floor(originX / 16.0) != originChunkX
                    || Math.floor(originZ / 16.0) != originChunkZ) {
                throw new IllegalArgumentException("natural entity binding numbers are invalid");
            }
            String expectedInstallation = worldId + ":" + originChunkX + ":" + originChunkZ
                    + ":ENTITIES";
            String sourceKind = "CHEST_MINECART".equals(target.getKind())
                    ? "CHEST_MINECART" : "STRUCTURE_ENTITY";
            String expectedAuthoritative = "final-carrier-ents:" + expectedInstallation + ":"
                    + sourceKind + ":" + encounterOrdinal;
            if (!expectedInstallation.equals(installationIdentity)
                    || !expectedAuthoritative.equals(authoritativeId)
                    || !SHA256.matcher(activationFingerprint).matches()
                    || !SHA256.matcher(provenanceFingerprint).matches()
                    || !SHA256.matcher(stateProvenanceFingerprint).matches()) {
                throw new IllegalArgumentException("natural entity identity is not authenticated");
            }
            this.schema = 2;
            this.worldId = worldId;
            this.worldSeed = worldSeed;
            this.worldEpoch = worldEpoch;
            this.installationIdentity = installationIdentity;
            this.authoritativeId = authoritativeId;
            this.encounterOrdinal = encounterOrdinal;
            this.activationFingerprint = activationFingerprint;
            this.provenanceFingerprint = provenanceFingerprint;
            this.stateProvenanceFingerprint = stateProvenanceFingerprint;
            this.revision = revision;
            this.lifecycle = "LIVE";
            this.originChunkX = originChunkX;
            this.originChunkZ = originChunkZ;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.connectionGeneration = connectionGeneration;
            this.evidenceGeneration = evidenceGeneration;
        }
    }

    /** Marker for the exact H12f/H12g committed prerequisite evidence union. */
    public interface FinalSceneGeneratedPrerequisiteEvidence {}

    @Getter
    public static class FinalSceneH12fPrerequisiteEvidence
            implements FinalSceneGeneratedPrerequisiteEvidence {
        private final String kind = "ARMOR_STAND";
        private final boolean visibleSpawn = true;
        private final boolean interactionCommitted = true;
        private final boolean semanticMutationCommitted = true;
        private final boolean terminalCommitted = true;
        private final boolean unloadReloadPersisted = true;
        private final boolean reconnectPersisted = true;
    }

    @Getter
    public static class FinalSceneH12gPrerequisiteEvidence
            implements FinalSceneGeneratedPrerequisiteEvidence {
        private final String kind = "CHEST_MINECART";
        private final boolean visibleSpawn = true;
        private final boolean interactionCommitted = true;
        private final MinecartCargoTarget cargoTarget;
        private final long cargoRevision;
        private final boolean cargoOpened = true;
        private final boolean cargoMutationCommitted = true;
        private final boolean cargoClosed = true;
        private final boolean unloadReloadPersisted = true;
        private final boolean reconnectPersisted = true;
        private final List<InventorySlot> persistedSlots;
        private final CraftingStack persistedCursor;

        public FinalSceneH12gPrerequisiteEvidence(MinecartCargoTarget cargoTarget,
                long minimumEntityRevision, long cargoRevision, List<InventorySlot> persistedSlots,
                CraftingStack persistedCursor) {
            this.cargoTarget = java.util.Objects.requireNonNull(cargoTarget, "cargo target");
            if (cargoRevision < minimumEntityRevision || persistedSlots == null
                    || persistedSlots.size() != 27) {
                throw new IllegalArgumentException("committed minecart cargo evidence is invalid");
            }
            this.cargoRevision = cargoRevision;
            this.persistedSlots = List.copyOf(persistedSlots);
            this.persistedCursor = java.util.Objects.requireNonNull(
                    persistedCursor, "persisted cargo cursor");
        }
    }

    /**
     * H12f/H12g receipt.  Unlike the v1 fixture receipt, this v2 shape cannot be built without the
     * authority's durable natural target identity and committed semantic/persistence facts.
     */
    @Getter
    public static class FinalSceneGeneratedPrerequisiteReceipt {
        private static final java.util.regex.Pattern NONCE =
                java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{8,128}");
        private final String type = "finalScenePrerequisiteReceipt";
        private final String schema = "game-expert.final-scene-prerequisites/v2";
        private final String fixtureId = "content-v1";
        private final String fixtureChecksum;
        private final String authority;
        private final String world;
        private final String nickname;
        private final String scenario;
        private final String actionNonce;
        private final long revision;
        private final boolean raidActive = true;
        private final int raidRoleCount;
        private final boolean bossbarVisible = true;
        private final boolean rewardsReady = true;
        private final boolean flightReady = true;
        private final boolean fishingReady = true;
        private final boolean connectedShapesReady = true;
        private final FinalSceneNaturalEntityBinding binding;
        private final FinalSceneGeneratedPrerequisiteEvidence evidence;

        public FinalSceneGeneratedPrerequisiteReceipt(String fixtureChecksum, String authority,
                String world, String nickname, String scenario, String actionNonce, long revision,
                int raidRoleCount, FinalSceneNaturalEntityBinding binding,
                FinalSceneGeneratedPrerequisiteEvidence evidence) {
            if (fixtureChecksum == null || !fixtureChecksum.matches("(?:0|[1-9][0-9]*)")
                    || !("spring".equals(authority) || "standalone".equals(authority))
                    || world == null || world.isBlank() || nickname == null
                    || !nickname.matches("[A-Za-z0-9_]{2,12}") || revision <= 0L
                    || raidRoleCount < 5 || actionNonce == null
                    || !NONCE.matcher(actionNonce).matches()) {
                throw new IllegalArgumentException("generated prerequisite receipt header is invalid");
            }
            this.binding = java.util.Objects.requireNonNull(binding, "natural binding");
            this.evidence = java.util.Objects.requireNonNull(evidence, "generated evidence");
            boolean h12f = "H12f".equals(scenario)
                    && "ARMOR_STAND".equals(binding.getTarget().getKind())
                    && evidence instanceof FinalSceneH12fPrerequisiteEvidence;
            boolean h12g = "H12g".equals(scenario)
                    && "CHEST_MINECART".equals(binding.getTarget().getKind())
                    && evidence instanceof FinalSceneH12gPrerequisiteEvidence minecart
                    && minecart.getCargoTarget().getEntityId() == binding.getTarget().getEntityId();
            if (!h12f && !h12g) {
                throw new IllegalArgumentException("generated prerequisite scenario/target mismatch");
            }
            this.fixtureChecksum = fixtureChecksum;
            this.authority = authority;
            this.world = world;
            this.nickname = nickname;
            this.scenario = scenario;
            this.actionNonce = actionNonce;
            this.revision = revision;
            this.raidRoleCount = raidRoleCount;
        }
    }

    /** 크리퍼 폭발 연출(폭심·반경). 블록 파괴는 별도 blockUpdate 로 나갑니다. */
    @Getter
    @AllArgsConstructor
    public static class Explosion {
        private final String type;
        private final double x;
        private final double y;
        private final double z;
        private final double radius;

        public Explosion(double x, double y, double z, double radius) {
            this("explosion", x, y, z, radius);
        }
    }

    /** PrimedTnt position is bottom-center; velocity is blocks/second; fuse is 20 TPS ticks. */
    @Getter
    @AllArgsConstructor
    public static class PrimedTntDto {
        private final long id;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;
        private final int fuse;
    }

    @Getter
    @AllArgsConstructor
    public static class PrimedTntSpawn {
        private final String type;
        private final List<PrimedTntDto> tnts;

        public PrimedTntSpawn(PrimedTntDto tnt) {
            this("primedTntSpawn", List.of(tnt));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PrimedTntUpdate {
        private final String type;
        private final List<PrimedTntDto> tnts;

        public PrimedTntUpdate(List<PrimedTntDto> tnts) {
            this("primedTntUpdate", tnts);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PrimedTntRemove {
        private final String type;
        private final List<Long> ids;

        public PrimedTntRemove(List<Long> ids) {
            this("primedTntRemove", ids);
        }
    }

    /** 개인 인벤토리 갱신(36칸 전체 + 선택 슬롯). 채굴/설치/슬롯선택/제작/이동 결과로 송신. */
    @Getter
    @AllArgsConstructor
    public static class InventoryUpdate {
        private final String type;
        private final List<InventorySlot> inventory;
        private final List<InventorySlot> armor;
        private final InventorySlot offhand;
        private final int selectedSlot;

        public InventoryUpdate(List<InventorySlot> inventory, List<InventorySlot> armor,
                InventorySlot offhand,
                int selectedSlot) {
            this("inventoryUpdate", inventory, armor, offhand, selectedSlot);
        }
    }

    /** 제작 격자·커서·결과 한 칸. 빈 칸은 itemType/count 0, durability null입니다. */
    @Getter
    @JsonPropertyOrder({"itemType", "count", "durability", "enchantments", "mapId", "shulkerId",
            "bucketMobData", "customName", "bannerPatterns", "book", "anvilUseCount", "leatherColor",
            "suspiciousStewEffect", "suspiciousStewDurationMcTicks"})
    public static class CraftingStack {
        private final short itemType;
        private final int count;
        private final Integer durability;
        /** [SURV-X] InventorySlot.enchantments 와 같은 필수 압축 마스크(워드 0). 없으면 0입니다. */
        @JsonIgnore
        private final long enchantmentMask;
        @JsonIgnore
        private final long enchantmentWord1;
        @JsonIgnore
        private final long enchantmentWord2;
        private final Integer mapId;
        private final Integer shulkerId;
        private final String bucketMobData;
        private final String customName;
        private final List<BannerPatternLayer> bannerPatterns;
        private final BookComponent book;
        private final int anvilUseCount;
        private final Integer leatherColor;
        private final String suspiciousStewEffect;
        private final Integer suspiciousStewDurationMcTicks;
        /** [TRIAL-GAP] InventorySlot.ominousBottleAmplifier 와 같은 선택 필드(1..4). */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Integer ominousBottleAmplifier;
        /**
         * [UTILITY] 범용 물약(CONTENTS_*)의 {@code potion_contents} 키(네임스페이스 없이). 다른 모든
         * 스택은 필드를 싣지 않는다 — 기존 와이어 바이트가 그대로다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String potionContents;
        /** [UTILITY] 장식 가능한 방어구의 {@code trim}(무늬·재료). 없으면 싣지 않는다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final TrimComponent trim;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations;

        public CraftingStack(short itemType, int count, Integer durability, long enchantments,
                Integer mapId, Integer shulkerId, String bucketMobData, String customName,
                List<BannerPatternLayer> bannerPatterns, BookComponent book, int anvilUseCount,
                Integer leatherColor, String suspiciousStewEffect,
                Integer suspiciousStewDurationMcTicks) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData,
                    customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    suspiciousStewEffect, suspiciousStewDurationMcTicks, null);
        }

        public CraftingStack(short itemType, int count, Integer durability, long enchantments,
                Integer mapId, Integer shulkerId, String bucketMobData, String customName,
                List<BannerPatternLayer> bannerPatterns, BookComponent book, int anvilUseCount,
                Integer leatherColor, String suspiciousStewEffect,
                Integer suspiciousStewDurationMcTicks, Integer ominousBottleAmplifier) {
            this(itemType, count, durability, WideEnchantments.legacy(enchantments), mapId,
                    shulkerId, bucketMobData, customName, bannerPatterns, book, anvilUseCount,
                    leatherColor, suspiciousStewEffect, suspiciousStewDurationMcTicks,
                    ominousBottleAmplifier);
        }

        /** [ENCHANT-WIDE] 43종 인챈트 집합을 싣는 생성자(불길한 병 증폭 없음). */
        public CraftingStack(short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData,
                    customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    suspiciousStewEffect, suspiciousStewDurationMcTicks, null);
        }

        /** [ENCHANT-WIDE] 43종 인챈트 집합 + [TRIAL-GAP] 불길한 병 증폭(물약·장식 성분 없음). */
        public CraftingStack(short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
                Integer ominousBottleAmplifier) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData,
                    customName, bannerPatterns, book, anvilUseCount, leatherColor,
                    suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                    null, null);
        }

        /** [UTILITY] 정본 생성자: 위 성분 + 물약 내용물 + 갑옷 장식. */
        public CraftingStack(short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
                Integer ominousBottleAmplifier, String potionContents, TrimComponent trim) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData, customName, bannerPatterns, book, anvilUseCount, leatherColor, suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier, potionContents, trim, null);
        }

        public CraftingStack(short itemType, int count, Integer durability,
                WideEnchantments enchantments, Integer mapId, Integer shulkerId,
                String bucketMobData, String customName, List<BannerPatternLayer> bannerPatterns,
                BookComponent book, int anvilUseCount, Integer leatherColor,
                String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
                Integer ominousBottleAmplifier, String potionContents, TrimComponent trim,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations) {
            validateStewWireComponent(
                    itemType, suspiciousStewEffect, suspiciousStewDurationMcTicks);
            validateOminousWireComponent(itemType, ominousBottleAmplifier);
            this.itemType = itemType;
            this.count = count;
            this.durability = durability;
            this.enchantmentMask = enchantments.word0();
            this.enchantmentWord1 = enchantments.word1();
            this.enchantmentWord2 = enchantments.word2();
            this.mapId = mapId;
            this.shulkerId = shulkerId;
            this.bucketMobData = bucketMobData;
            this.customName = customName;
            this.bannerPatterns = bannerPatterns;
            this.book = book;
            this.anvilUseCount = anvilUseCount;
            this.leatherColor = leatherColor;
            this.suspiciousStewEffect = suspiciousStewEffect;
            this.suspiciousStewDurationMcTicks = suspiciousStewDurationMcTicks;
            this.ominousBottleAmplifier = ominousBottleAmplifier;
            this.potionContents = potionContents;
            this.trim = trim;
            this.potDecorations = potDecorations == null || potDecorations.isEmpty()
                    ? null : List.copyOf(potDecorations);
        }

        /** Source convenience only; wire serialization still emits both required nullable fields. */
        public CraftingStack(short itemType, int count, Integer durability, long enchantments,
                Integer mapId, Integer shulkerId, String bucketMobData, String customName,
                List<BannerPatternLayer> bannerPatterns, BookComponent book, int anvilUseCount,
                Integer leatherColor) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, bucketMobData,
                    customName, bannerPatterns, book, anvilUseCount, leatherColor, null, null);
        }

        /** [ENCHANT-WIDE] 워드 0 과 확장 워드를 합친 43종 집합. */
        @JsonIgnore
        public WideEnchantments getWideEnchantments() {
            return new WideEnchantments(enchantmentMask, enchantmentWord1, enchantmentWord2);
        }

        /** [ENCHANT-WIDE] 와이어 {@code enchantments}({@link InventorySlot#getEnchantments()} 와 같다). */
        @JsonProperty("enchantments")
        public Object getEnchantments() {
            return getWideEnchantments().wireValue();
        }

    }

    /** [TRIAL-GAP] 불길한 병 증폭은 비기본값 1..4 만, 불길한 병에만 싣는다. */
    private static void validateOminousWireComponent(short itemType, Integer amplifier) {
        if (amplifier == null) return;
        if (itemType != com.gameexpert.engine.inventory.PlayerInventory.OMINOUS_BOTTLE
                || amplifier < 1 || amplifier > com.gameexpert.engine.inventory.ItemComponentData
                        .MAX_OMINOUS_BOTTLE_AMPLIFIER) {
            throw new IllegalArgumentException("invalid ominous bottle amplifier wire component");
        }
    }

    private static void validateStewWireComponent(short itemType, String effect,
            Integer durationMcTicks) {
        if ((effect == null) != (durationMcTicks == null)) {
            throw new IllegalArgumentException("incomplete suspicious stew wire component");
        }
        if (effect == null) return;
        if (itemType != com.gameexpert.engine.inventory.PlayerInventory.SUSPICIOUS_STEW_POPPY) {
            throw new IllegalArgumentException(
                    "archaeology suspicious stew component requires the generic stew item");
        }
        com.gameexpert.engine.SuspiciousStewRules.requireArchaeologyEffect(
                effect, durationMcTicks);
    }

    @Getter
    @AllArgsConstructor
    public static class BookUpdate {
        private final String type;
        private final String hand;
        private final InventorySlot item;
        private final int page;
        private final boolean editable;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public BookUpdate(String hand, InventorySlot item, int page, boolean editable) {
            this(hand, item, page, editable, null);
        }

        public BookUpdate(String hand, InventorySlot item, int page, boolean editable, Long requestId) {
            this("bookUpdate", hand, item, page, editable, requestId);
        }

        public BookUpdate(String type, String hand, InventorySlot item, int page, boolean editable) {
            this(type, hand, item, page, editable, null);
        }
    }

    /** 열린 제작 컨테이너의 원자적 서버 권위 상태입니다. */
    @Getter
    @AllArgsConstructor
    public static class CraftingUpdate {
        private final String type;
        private final String station;
        private final int gridSize;
        private final int x;
        private final int y;
        private final int z;
        private final List<InventorySlot> inventory;
        /**
         * 인벤토리 메뉴는 방어구 칸을 바꿀 수 있으므로 갑옷 네 칸을 {@code InventoryUpdate} 와
         * 같은 모양으로 함께 싣는다. 이것이 없으면 메뉴에서 갑옷을 갈아입어도 클라가 받는
         * craftingUpdate 만으로는 장비 표시를 갱신할 수 없다.
         */
        private final List<InventorySlot> armor;
        private final InventorySlot offhand;
        private final int selectedSlot;
        private final List<CraftingStack> grid;
        private final CraftingStack cursor;
        private final CraftingStack result;
        private final boolean crafted;
        /**
         * [STONECUT] 절단 세션이 지금 고른 레시피 id. 절단기가 아니거나 선택이 없으면 null.
         * 고를 수 있는 목록은 입력 아이템에서 클라가 카탈로그로 파생하므로 싣지 않는다.
         */
        private final String stonecutterSelection;
        private final String loomSelection;
        private final int levelCost;
        private final boolean tooExpensive;
        private final String resultName;
        private final long sessionId;
        /**
         * [BEACON] 신호기 화면의 BeaconMenu data slot 셋(층 수 · 주 효과 · 보조 효과). 신호기 세션에만
         * 싣는 append-only 선택 필드다 — 다른 스테이션과 옛 클라는 이 키를 보지 않는다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final BeaconMenuState beacon;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public CraftingUpdate(String station, int gridSize, int x, int y, int z,
                List<InventorySlot> inventory, List<InventorySlot> armor, InventorySlot offhand,
                int selectedSlot,
                List<CraftingStack> grid, CraftingStack cursor, CraftingStack result,
                boolean crafted, String stonecutterSelection, String loomSelection, int levelCost,
                boolean tooExpensive, String resultName, long sessionId) {
            this(station, gridSize, x, y, z, inventory, armor, offhand, selectedSlot, grid, cursor,
                    result, crafted, stonecutterSelection, loomSelection, levelCost, tooExpensive,
                    resultName, sessionId, null);
        }

        public CraftingUpdate(String station, int gridSize, int x, int y, int z,
                List<InventorySlot> inventory, List<InventorySlot> armor, InventorySlot offhand,
                int selectedSlot,
                List<CraftingStack> grid, CraftingStack cursor, CraftingStack result,
                boolean crafted, String stonecutterSelection, String loomSelection, int levelCost,
                boolean tooExpensive, String resultName, long sessionId, BeaconMenuState beacon) {
            this(station, gridSize, x, y, z, inventory, armor, offhand, selectedSlot, grid, cursor,
                    result, crafted, stonecutterSelection, loomSelection, levelCost, tooExpensive,
                    resultName, sessionId, beacon, null);
        }

        public CraftingUpdate(String station, int gridSize, int x, int y, int z,
                List<InventorySlot> inventory, List<InventorySlot> armor, InventorySlot offhand,
                int selectedSlot,
                List<CraftingStack> grid, CraftingStack cursor, CraftingStack result,
                boolean crafted, String stonecutterSelection, String loomSelection, int levelCost,
                boolean tooExpensive, String resultName, long sessionId, BeaconMenuState beacon,
                Long requestId) {
            this("craftingUpdate", station, gridSize, x, y, z, inventory, armor, offhand,
                    selectedSlot, grid, cursor, result, crafted, stonecutterSelection, loomSelection,
                    levelCost, tooExpensive, resultName, sessionId, beacon, requestId);
        }

        public CraftingUpdate(String type, String station, int gridSize, int x, int y, int z,
                List<InventorySlot> inventory, List<InventorySlot> armor, InventorySlot offhand,
                int selectedSlot,
                List<CraftingStack> grid, CraftingStack cursor, CraftingStack result,
                boolean crafted, String stonecutterSelection, String loomSelection, int levelCost,
                boolean tooExpensive, String resultName, long sessionId, BeaconMenuState beacon) {
            this(type, station, gridSize, x, y, z, inventory, armor, offhand, selectedSlot,
                    grid, cursor, result, crafted, stonecutterSelection, loomSelection,
                    levelCost, tooExpensive, resultName, sessionId, beacon, null);
        }
    }

    /**
     * [BEACON] {@code BeaconMenu} 의 세 data slot. {@code levels} 는 0..4, 효과는 신호기 효과 이름
     * ({@code speed|haste|resistance|jump_boost|strength|regeneration}) 또는 null(바닐라 {@code NO_EFFECT}).
     */
    @Getter
    public static class BeaconMenuState {
        public static final Set<String> POWERS = Set.of(
                "speed", "haste", "resistance", "jump_boost", "strength", "regeneration");

        private final int levels;
        private final String primary;
        private final String secondary;

        public BeaconMenuState(int levels, String primary, String secondary) {
            if (levels < 0 || levels > 4) throw new IllegalArgumentException("beacon levels out of range");
            if (primary != null && !POWERS.contains(primary)
                    || secondary != null && !POWERS.contains(secondary)) {
                throw new IllegalArgumentException("unknown beacon power");
            }
            this.levels = levels;
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CraftingClosed {
        private final String type;
        private final String station;
        private final int x;
        private final int y;
        private final int z;
        private final long sessionId;

        public CraftingClosed(long sessionId, String station, int x, int y, int z) {
            this("craftingClosed", station, x, y, z, sessionId);
        }
    }

    /** 좌표 양조대 열기 확정. 다섯 슬롯과 현재 연료/양조 진행을 함께 보냅니다. */
    @Getter
    @AllArgsConstructor
    public static class BrewingOpen {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final List<InventorySlot> inventory;
        private final InventorySlot offhand;
        private final int selectedSlot;
        private final List<CraftingStack> slots;
        private final CraftingStack cursor;
        private final int fuel;
        private final int brewTicks;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public BrewingOpen(int x, int y, int z, List<InventorySlot> inventory,
                InventorySlot offhand, int selectedSlot, List<CraftingStack> slots,
                CraftingStack cursor, int fuel, int brewTicks) {
            this(x, y, z, inventory, offhand, selectedSlot, slots, cursor, fuel, brewTicks, null);
        }

        public BrewingOpen(int x, int y, int z, List<InventorySlot> inventory,
                InventorySlot offhand, int selectedSlot, List<CraftingStack> slots,
                CraftingStack cursor, int fuel, int brewTicks, Long requestId) {
            this("brewingOpen", x, y, z, inventory, offhand, selectedSlot, slots,
                    cursor, fuel, brewTicks, requestId);
        }

        public BrewingOpen(String type, int x, int y, int z, List<InventorySlot> inventory,
                InventorySlot offhand, int selectedSlot, List<CraftingStack> slots,
                CraftingStack cursor, int fuel, int brewTicks) {
            this(type, x, y, z, inventory, offhand, selectedSlot, slots, cursor, fuel, brewTicks, null);
        }
    }

    /** Coordinate-bound five-slot brewing stand state. */
    @Getter
    public static class BrewingUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final List<InventorySlot> inventory;
        private final InventorySlot offhand;
        private final int selectedSlot;
        private final List<CraftingStack> slots;
        private final CraftingStack cursor;
        private final int fuel;
        private final int brewTicks;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public BrewingUpdate(int x, int y, int z, List<InventorySlot> inventory,
                InventorySlot offhand, int selectedSlot, List<CraftingStack> slots,
                CraftingStack cursor, int fuel, int brewTicks) {
            this(x, y, z, inventory, offhand, selectedSlot, slots, cursor, fuel, brewTicks, null);
        }

        public BrewingUpdate(int x, int y, int z, List<InventorySlot> inventory,
                InventorySlot offhand, int selectedSlot, List<CraftingStack> slots,
                CraftingStack cursor, int fuel, int brewTicks, Long requestId) {
            this("brewingUpdate", x, y, z, inventory, offhand, selectedSlot, slots,
                    cursor, fuel, brewTicks, requestId);
        }

        private BrewingUpdate(String type, int x, int y, int z, List<InventorySlot> inventory,
                InventorySlot offhand, int selectedSlot, List<CraftingStack> slots,
                CraftingStack cursor, int fuel, int brewTicks, Long requestId) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.inventory = inventory;
            this.offhand = offhand;
            this.selectedSlot = selectedSlot;
            this.slots = slots;
            this.cursor = cursor;
            this.fuel = fuel;
            this.brewTicks = brewTicks;
            this.requestId = requestId;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class BrewingClosed {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public BrewingClosed(int x, int y, int z) {
            this(x, y, z, null);
        }

        public BrewingClosed(int x, int y, int z, Long requestId) {
            this("brewingClosed", x, y, z, requestId);
        }

        public BrewingClosed(String type, int x, int y, int z) {
            this(type, x, y, z, null);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class LecternUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final InventorySlot book;
        private final int page;
        private final int pageCount;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public LecternUpdate(int x, int y, int z, InventorySlot book,
                int page, int pageCount) {
            this(x, y, z, book, page, pageCount, null);
        }

        public LecternUpdate(int x, int y, int z, InventorySlot book,
                int page, int pageCount, Long requestId) {
            this("lecternUpdate", x, y, z, book, page, pageCount, requestId);
        }

        public LecternUpdate(String type, int x, int y, int z, InventorySlot book,
                int page, int pageCount) {
            this(type, x, y, z, book, page, pageCount, null);
        }
    }

    /** 서버가 실제로 확정한 개인 사운드 사건. itemType은 모든 kind에서 필수입니다. */
    @Getter
    @AllArgsConstructor
    public static class SoundEvent {
        private final String type;
        private final String kind;
        private final short itemType;

        public SoundEvent(String kind, short itemType) {
            this("soundEvent", kind, itemType);
        }
    }

    /**
     * [JUKEBOX] 주크박스 곡 시작 · 동기화 · 정지(바닐라 levelEvent 1010 / 1011 과 블록 엔티티 동기화).
     * {@code song} 은 {@code jukebox_song/<key>.json} 의 key, 정지면 null. {@code elapsedMcTicks} 는
     * 곡 시작 뒤 흐른 MC 틱({@code ticks_since_song_started}), {@code started} 는 방금 넣은 음반인지
     * (클라이언트가 "지금 재생 중" 을 띄운다).
     */
    @Getter
    public static class JukeboxSongUpdate {
        private final String type = "jukeboxSong";
        private final int x;
        private final int y;
        private final int z;
        private final String song;
        private final long elapsedMcTicks;
        private final boolean started;

        public JukeboxSongUpdate(int x, int y, int z, String song, long elapsedMcTicks, boolean started) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.song = song;
            this.elapsedMcTicks = elapsedMcTicks;
            this.started = started;
        }
    }

    /** 서버가 확정한 위치 기반 월드 행동 사운드. 모든 필드는 현재 프로토콜에서 필수입니다. */
    @Getter
    @AllArgsConstructor
    public static class WorldSound {
        public static final java.util.Set<String> KINDS = java.util.Set.of(
                "lever_click", "stone_button_click_on", "stone_button_click_off", "wooden_button_click_on", "wooden_button_click_off", "stone_pressure_plate_click_on", "stone_pressure_plate_click_off", "wooden_pressure_plate_click_on", "wooden_pressure_plate_click_off", "piston_extend", "piston_contract", "comparator_click", "redstone_torch_burnout", "tripwire_click_on", "tripwire_click_off", "tripwire_attach", "tripwire_detach", "copper_bulb_turn_on", "copper_bulb_turn_off", "note_block_harp", "note_block_basedrum", "note_block_snare", "note_block_hat", "note_block_bass", "note_block_flute", "note_block_bell", "note_block_guitar", "note_block_chime", "note_block_xylophone", "note_block_iron_xylophone", "note_block_cow_bell", "note_block_didgeridoo", "note_block_bit", "note_block_banjo", "note_block_pling", "note_block_trumpet", "note_block_trumpet_exposed", "note_block_trumpet_weathered", "note_block_trumpet_oxidized", "note_block_imitate_ender_dragon",
                "block_hit", "block_break", "block_place", "harvest",
                "door_open", "door_close", "fence_gate_open", "fence_gate_close",
                "trapdoor_open", "trapdoor_close", "chest_open", "chest_close",
                "bottle_fill", "bottle_empty", "bucket_fill_water", "bucket_fill_lava",
                // [DRAGON] 빈 병으로 드래곤 숨결 구름을 떴다(item.bottle.fill_dragonbreath).
                "bottle_fill_dragonbreath",
                "bucket_empty_water", "bucket_empty_lava", "till", "shovel", "plant",
                "bonemeal", "shears", "campfire_extinguish", "campfire_light",
                "ignite", "tnt_prime", "cow_milk",
                "fishing_cast", "fishing_bite",
                "firework_launch", "firework_blast",
                "goat_horn", "battering_horn", "illager_music_box", "petal_pouch",
                // [DEEP-DARK] 스컬크 전용 음향군. 값은 SculkVibrationSystem.SOUND_SENSOR_CLICK ·
                // SOUND_SHRIEK 과, 클라 `WorldSoundKind` 의 같은 두 문자열과 일치해야 한다.
                "sculk_sensor_click", "sculk_shriek",
                "shelf_mushroom_bounce",
                // 엔더의 눈: 틀 채움(level event 1503)·차원문 개방(global 1038)·투척·소멸.
                "end_portal_frame_fill", "end_portal_spawn", "ender_eye_launch", "ender_eye_death",
                // [BARREL-SOUND] 통 여닫이. 바닐라 BarrelBlockEntity 는 상자와 다른
                // SoundEvents.BARREL_OPEN/BARREL_CLOSE 를 첫 열람·마지막 닫기에만 낸다.
                "barrel_open", "barrel_close",
                // [TRIAL-GAP] 트라이얼 스포너·금고·불길한 아이템 소환기의 서버 playSound 사건
                // (26.3-snapshot-7 javap TrialSpawnerState · OminousItemSpawner · VaultState ·
                // VaultBlockEntity$Server). 블록 좌표 사건은 블록 중심, blockType 은 그 블록 ID 다.
                "trial_spawner_open_shutter", "trial_spawner_close_shutter",
                "trial_spawner_spawn_item_begin", "trial_spawner_about_to_spawn_item",
                "vault_insert_item", "vault_insert_item_fail", "vault_reject_rewarded_player",
                "vault_eject_item", "vault_open_shutter", "vault_close_shutter",
                // [TRIAL-GAP] 화염구 사용(FireChargeItem.useOn: item.firecharge.use, BLOCKS).
                "firecharge_use",
                // [TRIAL-GAP] 돌풍 효과 사망 폭발(WindChargedMobEffect.onMobRemoved →
                // ServerLevel.explode(…, GUST_EMITTER_*, BREEZE_WIND_CHARGE_BURST)). 좌표는 폭발 중심.
                "wind_charged_burst",
                // [BLOCK-SHAPES] 종 울림(BELL_BLOCK, volume 2.0)과 습격자 공명(BELL_RESONATE).
                "bell_use", "bell_resonate",
                // [VANILLA-SOUNDS] 낙하 피해의 블록 낙하음(LivingEntity.playBlockFallSound). 좌표는 착지한
                // 발 위치, blockType 은 (floor x, floor(y − 0.2), floor z) 칸의 블록 ID 다.
                "block_fall",
                // [VANILLA-SOUNDS] 금 간 장식 항아리의 파괴음(SoundType.DECORATED_POT_CRACKED.break).
                "decorated_pot_shatter",
                // [MACE] 철퇴 강타(MaceItem.hurtEnemy: item.mace.smash_air/smash_ground/smash_ground_heavy,
                // volume 1 · pitch 1, 공격자 위치)와 돌풍 인챈트 폭발(entity.wind_charge.wind_burst, 폭발 중심).
                "mace_smash_air", "mace_smash_ground", "mace_smash_ground_heavy", "wind_burst",
                "spear_use", "spear_wood_use", "spear_attack", "spear_wood_attack", "spear_hit", "spear_wood_hit",
                "spear_lunge_1", "spear_lunge_2", "spear_lunge_3",
                // [MACE-B2] 벌레 먹음 좀벌레 생성 순간의 Silverfish.playSound(SILVERFISH_HURT)(생성 위치).
                "silverfish_hurt",
                // [BEACON] BeaconBlockEntity.playSound(BLOCKS, 1, 1): 활성·비활성·맥박·효과 확정.
                "beacon_activate", "beacon_deactivate", "beacon_ambient", "beacon_power_select",
                // [BREWING-26.3] 양조 완료(BrewingStandBlockEntity.doBrew → levelEvent 1035).
                "brewing_stand_brew",
                // [CONTAINER-MENUS] ShulkerBoxBlockEntity start/stopOpen (block.shulker_box.open/
                // close) and DecoratedPotBlock use (block.decorated_pot.insert with a fill pitch and
                // a DUST_PLUME burst the client derives, block.decorated_pot.insert_fail).
                "shulker_box_open", "shulker_box_close",
                "decorated_pot_insert", "decorated_pot_insert_fail",
                // [CONTAINER-MENUS] ArmorStandItem place (BLOCKS 0.75, 0.8), ArmorStand break
                // (1, 1) and an armor stand's armor equip sound (blockType = the armor item).
                "armor_stand_place", "armor_stand_break", "armor_equip",
                // [CONTAINER-MENUS] SnowGolem#shear / Bogged#shear (1, 1) and ButtonBlock#playSound
                // (BlockSetType click on/off, 1, 1; blockType = the button).
                "snow_golem_shear", "bogged_shear", "button_click_on", "button_click_off");
        /**
         * 호출부가 pitch 를 서버 상태로 정하는 kind. 금고 배출(0.8 + 0.4 × 진행도)과
         * [CONTAINER-MENUS] 장식 항아리 넣기(0.7 + 0.5 × 채움)다.
         */
        public static final java.util.Set<String> PITCHED_KINDS =
                java.util.Set.of("vault_eject_item", "decorated_pot_insert", "lever_click", "stone_button_click_on", "stone_button_click_off", "wooden_button_click_on", "wooden_button_click_off", "stone_pressure_plate_click_on", "stone_pressure_plate_click_off", "wooden_pressure_plate_click_on", "wooden_pressure_plate_click_off", "piston_extend", "piston_contract", "comparator_click", "redstone_torch_burnout", "tripwire_click_on", "tripwire_click_off", "tripwire_attach", "tripwire_detach", "copper_bulb_turn_on", "copper_bulb_turn_off", "note_block_harp", "note_block_basedrum", "note_block_snare", "note_block_hat", "note_block_bass", "note_block_flute", "note_block_bell", "note_block_guitar", "note_block_chime", "note_block_xylophone", "note_block_iron_xylophone", "note_block_cow_bell", "note_block_didgeridoo", "note_block_bit", "note_block_banjo", "note_block_pling", "note_block_trumpet", "note_block_trumpet_exposed", "note_block_trumpet_weathered", "note_block_trumpet_oxidized", "note_block_imitate_ender_dragon");
        private final String type;
        private final long eventId;
        private final String kind;
        private final double x;
        private final double y;
        private final double z;
        private final short blockType;
        /**
         * [TRIAL-GAP] 서버가 정한 pitch. {@link #PITCHED_KINDS} 만 싣고 나머지는 생략한다(클라가
         * kind 별 바닐라 규칙으로 정한다).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Float pitch;

        public WorldSound(long eventId, String kind, double x, double y, double z,
                short blockType) {
            this("worldSound", eventId, kind, x, y, z, blockType, null);
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown worldSound kind: " + kind);
            if (PITCHED_KINDS.contains(kind)) {
                throw new IllegalArgumentException("worldSound kind requires pitch: " + kind);
            }
        }

        /** [TRIAL-GAP] 서버가 pitch 를 정하는 kind({@link #PITCHED_KINDS}) 전용. */
        public WorldSound(long eventId, String kind, double x, double y, double z,
                short blockType, float pitch) {
            this("worldSound", eventId, kind, x, y, z, blockType, Float.valueOf(pitch));
            if (!PITCHED_KINDS.contains(kind)) {
                throw new IllegalArgumentException("worldSound kind carries no pitch: " + kind);
            }
            if (!Float.isFinite(pitch) || pitch <= 0f) {
                throw new IllegalArgumentException("worldSound pitch must be positive");
            }
        }
    }

    /** 분출 중인 강한 유황의 표현 cadence 사실. 좌표는 분출구 블록 중심입니다. */
    @Getter
    @AllArgsConstructor
    public static class PotentSulfurEvent {
        public static final java.util.Set<String> PHASES = java.util.Set.of("particle", "sound");
        private final String type;
        private final long eventId;
        private final String phase;
        private final double x;
        private final double y;
        private final double z;

        public PotentSulfurEvent(long eventId, String phase, double x, double y, double z) {
            this("potentSulfurEvent", eventId, phase, x, y, z);
            if (!PHASES.contains(phase)) {
                throw new IllegalArgumentException("Unknown potentSulfurEvent phase: " + phase);
            }
        }
    }

    /** 접속 스냅샷과 전이 방송에 공통으로 쓰는 서버 권위 날씨 상태. */
    @Getter
    @AllArgsConstructor
    public static class WeatherState {
        private final String type;
        private final String kind;

        public WeatherState(String kind) {
            this("weatherState", kind);
        }
    }

    /** 서버가 결정한 번개 좌표. */
    @Getter
    @AllArgsConstructor
    public static class LightningEvent {
        private final String type;
        private final long eventId;
        private final double x;
        private final double y;
        private final double z;

        public LightningEvent(long eventId, double x, double y, double z) {
            this("lightningEvent", eventId, x, y, z);
        }
    }

    /**
     * [TRIAL-GAP] 바닐라 {@code ServerLevel.levelEvent(id, pos, data)} 한 건. 트라이얼 스포너 ·
     * 금고 · 불길한 아이템 소환기 · 거미줄 효과의 일회성 입자/소리 폭발만 싣는다(26.3-snapshot-7
     * {@code LevelEvent} 상수: 3011 PARTICLES_TRIAL_SPAWNER_SPAWN · 3012 _SPAWN_MOB_AT · 3013
     * _DETECT_PLAYER · 3014 ANIMATION_TRIAL_SPAWNER_EJECT_ITEM · 3015 ANIMATION_VAULT_ACTIVATE ·
     * 3016 ANIMATION_VAULT_DEACTIVATE · 3017 ANIMATION_VAULT_EJECT_ITEM · 3018 ANIMATION_SPAWN_COBWEB ·
     * 3019 _DETECT_PLAYER_OMINOUS · 3020 _BECOME_OMINOUS · 3021 _SPAWN_ITEM). x/y/z 는 바닐라 BlockPos 이고
     * {@code data} 는 이벤트별 정수(FlameParticle 서수 · 감지 인원 · 불길 여부)다.
     */
    @Getter
    @AllArgsConstructor
    public static class LevelEvent {
        public static final java.util.Set<Integer> EVENTS = java.util.Set.of(
                // 1018 SOUND_BLAZE_FIREBALL · 1051 SOUND_WIND_CHARGE_SHOOT: 불길한 아이템 소환기가
                // 화염구·돌풍구를 쏠 때 DispenseConfig.overrideDispenseEvent 로 낸다.
                1018, 1051,
                // [DRAGON] 1017 SOUND_DRAGON_FIREBALL · 1028 SOUND_DRAGON_DEATH(globalLevelEvent — 거리 무관) ·
                // 2006 PARTICLES_DRAGON_FIREBALL_SPLASH(data 1, 입자+폭발음) · 2008 PARTICLES_DRAGON_BLOCK_BREAK ·
                // 2015 PARTICLES_DRAGON_EGG · 3001 ANIMATION_DRAGON_SUMMON_ROAR(부활 연출 포효).
                1017, 1028, 2006, 2008, 2015,
                // [END-GATEWAY] 3000 ANIMATION_END_GATEWAY_SPAWN: EnderDragonFight.spawnNewGateway 가 새 관문
                // 자리에 낸다(block.end_gateway.spawn BLOCKS volume 10 · 폭발 방출 파티클).
                3000,
                3001,
                // [MACE] 2013 SMASH_ATTACK: ParticleUtils.spawnSmashAttackParticles(대상 발밑 블록, data 750).
                2013,
                3011, 3012, 3013, 3014, 3015, 3016, 3017, 3018, 3019, 3020, 3021,
                // [CONTAINER-MENUS] 1000 SOUND_DISPENSER_DISPENSE · 1001 _FAIL · 1002
                // _PROJECTILE_LAUNCH · 1004 SOUND_FIREWORK_SHOOT · 1049 SOUND_CRAFTER_CRAFT ·
                // 1050 SOUND_CRAFTER_FAIL · 2000 PARTICLES_SHOOT_SMOKE · 2010
                // PARTICLES_SHOOT_WHITE_SMOKE (data = Direction#get3DDataValue).
                1000, 1001, 1002, 1004, 1049, 1050, 2000, 2010);
        private final String type;
        private final int event;
        private final int x;
        private final int y;
        private final int z;
        private final int data;

        public LevelEvent(int event, int x, int y, int z, int data) {
            this("levelEvent", event, x, y, z, data);
            if (!EVENTS.contains(event)) {
                throw new IllegalArgumentException("Unknown levelEvent: " + event);
            }
        }
    }

    /**
     * [CONTAINER-MENUS] The {@code CrafterMenu} facts a crafter-hosted chest-lane snapshot
     * appends (append-only, omitted for every other host): the disabled slot indices
     * ({@code CrafterBlockEntity.disabled_slots}), the {@code TRIGGERED} power indicator and the
     * {@code NonInteractiveResultSlot} preview ({@code null} when no recipe matches).
     */
    @Getter
    @AllArgsConstructor
    public static class CrafterMenuState {
        private final List<Integer> disabledSlots;
        private final boolean powered;
        private final CraftingStack result;
    }

    /** 상자 열기 확정. 단일 27칸/큰 상자 54칸 전체를 보내며 빈 칸도 포함한다. */
    @Getter
    @AllArgsConstructor
    public static class ChestOpen {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final int hostBlockId;
        private final List<InventorySlot> chest;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final CrafterMenuState crafter;

        public ChestOpen(int x, int y, int z, int hostBlockId, List<InventorySlot> chest) {
            this("chestOpen", x, y, z, hostBlockId, chest, null);
        }

        public ChestOpen(int x, int y, int z, int hostBlockId, List<InventorySlot> chest,
                CrafterMenuState crafter) {
            this("chestOpen", x, y, z, hostBlockId, chest, crafter);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ChestUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final int hostBlockId;
        private final List<InventorySlot> chest;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final CrafterMenuState crafter;

        public ChestUpdate(int x, int y, int z, int hostBlockId, List<InventorySlot> chest) {
            this("chestUpdate", x, y, z, hostBlockId, chest, null);
        }

        public ChestUpdate(int x, int y, int z, int hostBlockId, List<InventorySlot> chest,
                CrafterMenuState crafter) {
            this("chestUpdate", x, y, z, hostBlockId, chest, crafter);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ChestClosed {
        private final String type;
        private final int x;
        private final int y;
        private final int z;

        public ChestClosed(int x, int y, int z) {
            this("chestClosed", x, y, z);
        }
    }

    /**
     * [MOUNT] 상자를 단 말 계열(당나귀·노새·라마) 화물 패널 열기 확정. 좌표 컨테이너와 달리
     * 대상이 움직이므로 키가 {@code mobId} 이고, 칸 수가 개체마다 다르므로 열 수를 함께 싣는다
     * (당나귀·노새 5열 고정, 라마는 힘 스탯이 곧 열 수). 슬롯 배열은 빈 칸을 포함한 열 × 3 이다.
     */
    @Getter
    @AllArgsConstructor
    public static class MobCargoOpen {
        private final String type;
        private final long mobId;
        private final String mobType;
        private final String variant;
        private final boolean baby;
        private final String customName;
        private final int columns;
        private final List<InventorySlot> slots;

        public MobCargoOpen(long mobId, String mobType, String variant, boolean baby,
                String customName, int columns, List<InventorySlot> slots) {
            this("mobCargoOpen", mobId, mobType, variant, baby, customName, columns, slots);
        }
    }

    /** [MOUNT] 화물 재고 갱신. 이동 한 번마다 전체 칸을 다시 싣는다(상자 chestUpdate 와 같다). */
    @Getter
    @AllArgsConstructor
    public static class MobCargoUpdate {
        private final String type;
        private final long mobId;
        private final String mobType;
        private final String variant;
        private final boolean baby;
        private final String customName;
        private final int columns;
        private final List<InventorySlot> slots;

        public MobCargoUpdate(long mobId, String mobType, String variant, boolean baby,
                String customName, int columns, List<InventorySlot> slots) {
            this("mobCargoUpdate", mobId, mobType, variant, baby, customName, columns, slots);
        }
    }

    /** [MOUNT] 화물 세션 종료(자발적 닫기·사망·소멸·사거리 이탈). */
    @Getter
    @AllArgsConstructor
    public static class MobCargoClosed {
        private final String type;
        private final long mobId;

        public MobCargoClosed(long mobId) {
            this("mobCargoClosed", mobId);
        }
    }

    /** Exact authority-owned Chest Minecart menu identity. */
    @Getter
    public static class MinecartCargoTarget {
        private final int schema;
        private final String kind;
        private final long entityId;
        private final long sessionId;

        public MinecartCargoTarget(long entityId, long sessionId) {
            if (entityId <= 0L || entityId > 9_007_199_254_740_991L
                    || sessionId <= 0L || sessionId > 9_007_199_254_740_991L) {
                throw new IllegalArgumentException("minecart cargo target is outside JSON-safe bounds");
            }
            this.schema = 1;
            this.kind = "minecartCargo";
            this.entityId = entityId;
            this.sessionId = sessionId;
        }
    }

    @Getter
    public static class MinecartCargoOpen {
        private final String type = "minecartCargoOpen";
        private final MinecartCargoTarget target;
        private final List<InventorySlot> slots;
        private final CraftingStack cursor;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long requestId;

        public MinecartCargoOpen(MinecartCargoTarget target, List<InventorySlot> slots,
                CraftingStack cursor) {
            this(target, slots, cursor, null);
        }

        public MinecartCargoOpen(MinecartCargoTarget target, List<InventorySlot> slots,
                CraftingStack cursor, Long requestId) {
            this.target = java.util.Objects.requireNonNull(target, "minecart cargo target");
            if (slots == null || slots.size() != 27) {
                throw new IllegalArgumentException("minecart cargo requires exactly 27 slots");
            }
            this.slots = List.copyOf(slots);
            this.cursor = java.util.Objects.requireNonNull(cursor, "minecart cargo cursor");
            this.requestId = requestId;
        }
    }

    @Getter
    public static class MinecartCargoUpdate {
        private final String type = "minecartCargoUpdate";
        private final MinecartCargoTarget target;
        private final List<InventorySlot> slots;
        private final CraftingStack cursor;

        public MinecartCargoUpdate(MinecartCargoTarget target, List<InventorySlot> slots,
                CraftingStack cursor) {
            this.target = java.util.Objects.requireNonNull(target, "minecart cargo target");
            if (slots == null || slots.size() != 27) {
                throw new IllegalArgumentException("minecart cargo requires exactly 27 slots");
            }
            this.slots = List.copyOf(slots);
            this.cursor = java.util.Objects.requireNonNull(cursor, "minecart cargo cursor");
        }
    }

    @Getter
    public static class MinecartCargoClosed {
        private final String type = "minecartCargoClosed";
        private final MinecartCargoTarget target;

        public MinecartCargoClosed(MinecartCargoTarget target) {
            this.target = java.util.Objects.requireNonNull(target, "minecart cargo target");
        }
    }

    /**
     * [CONTAINER-CURSOR] 보관 컨테이너 커서의 권위 상태. 상자 스냅샷은 같은 상자를 보는 모두에게
     * 방송되지만 커서는 개인 소유라 이 한 줄로만 본인에게 갑니다(빈 커서는 itemType 0).
     */
    @Getter
    @AllArgsConstructor
    public static class ContainerCursor {
        private final String type;
        private final CraftingStack cursor;

        public ContainerCursor(CraftingStack cursor) {
            this("containerCursor", cursor);
        }
    }

    /** 좌표 화로 열기 확정. 세 슬롯과 현재 연료/조리 진행을 함께 보냅니다. */
    @Getter
    @AllArgsConstructor
    public static class FurnaceOpen {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        /**
         * [FURNACE-VARIANT] 어느 제련로인가(FURNACE/BLAST/SMOKER). 레시피 북이 변형별 입력
         * 필터(용광로 ORE · 훈연기 FOOD)를 권위와 같은 표로 판정하는 데 쓴다 — 없으면
         * "재료는 다 있는데 눌러도 서버가 조용히 거절하는" 줄이 북에 뜬다.
         */
        private final String variant;
        private final List<InventorySlot> slots;
        private final int burnTicks;
        private final int burnTotalTicks;
        private final int cookTicks;
        private final int cookTotalTicks;
        private final CraftingStack cursor;

        public FurnaceOpen(int x, int y, int z, String variant, List<InventorySlot> slots,
                int burnTicks, int burnTotalTicks, int cookTicks, int cookTotalTicks,
                CraftingStack cursor) {
            this("furnaceOpen", x, y, z, variant, slots, burnTicks, burnTotalTicks,
                    cookTicks, cookTotalTicks, cursor);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class FurnaceUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        /**
         * [FURNACE-VARIANT] 어느 제련로인가(FURNACE/BLAST/SMOKER). 레시피 북이 변형별 입력
         * 필터(용광로 ORE · 훈연기 FOOD)를 권위와 같은 표로 판정하는 데 쓴다 — 없으면
         * "재료는 다 있는데 눌러도 서버가 조용히 거절하는" 줄이 북에 뜬다.
         */
        private final String variant;
        private final List<InventorySlot> slots;
        private final int burnTicks;
        private final int burnTotalTicks;
        private final int cookTicks;
        private final int cookTotalTicks;
        private final CraftingStack cursor;

        public FurnaceUpdate(int x, int y, int z, String variant, List<InventorySlot> slots,
                int burnTicks, int burnTotalTicks, int cookTicks, int cookTotalTicks,
                CraftingStack cursor) {
            this("furnaceUpdate", x, y, z, variant, slots,
                    burnTicks, burnTotalTicks, cookTicks, cookTotalTicks, cursor);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class FurnaceClosed {
        private final String type;
        private final int x;
        private final int y;
        private final int z;

        public FurnaceClosed(int x, int y, int z) {
            this("furnaceClosed", x, y, z);
        }
    }

    /** 모닥불의 안정적인 0..3 슬롯 표현. 빈 칸은 itemType=0, cookTicks=0입니다. */
    @Getter
    @AllArgsConstructor
    public static class CampfireSlot {
        private final int slot;
        private final short itemType;
        private final int cookTicks;
    }

    /** 좌표 모닥불의 welcome 스냅샷입니다. slots는 항상 정확히 네 칸입니다. */
    @Getter
    public static class CampfireSnapshot {
        private final int x;
        private final int y;
        private final int z;
        private final List<CampfireSlot> slots;

        public CampfireSnapshot(int x, int y, int z, List<CampfireSlot> slots) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.slots = slots;
            if (slots == null || slots.size() != 4) {
                throw new IllegalArgumentException("모닥불 welcome은 정확히 4칸이어야 합니다.");
            }
        }
    }

    /** 좌표 모닥불의 음식 네 칸과 개별 진행을 전달합니다. slots는 항상 정확히 네 칸입니다. */
    @Getter
    @AllArgsConstructor
    public static class CampfireUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final List<CampfireSlot> slots;

        public CampfireUpdate(int x, int y, int z, List<CampfireSlot> slots) {
            this("campfireUpdate", x, y, z, slots);
            if (slots == null || slots.size() != 4) {
                throw new IllegalArgumentException("모닥불 업데이트는 정확히 4칸이어야 합니다.");
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ShelfSlot {
        private final int slot;
        private final short itemType;
        /** [GLINT] 칸 스택의 인챈트 와이어 값. 없으면 싣지 않는다(append-only 선택 필드). */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Object enchantments;

        public ShelfSlot(int slot, short itemType) {
            this(slot, itemType, null);
        }
    }

    /**
     * [BLOCK-SHAPES] 종이 울렸다(BellBlockEntity.onHit). {@code face} 는 친 면(clickDirection, 바닐라
     * Direction 번호)이고 클라가 몸통을 50 틱 흔든다(BellRenderer).
     */
    @Getter
    @AllArgsConstructor
    public static class BellRing {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final int face;

        public BellRing(int x, int y, int z, int face) {
            this("bellRing", x, y, z, face);
        }
    }

    /** Committed cocoon alarm: two seconds of presentation without changing block state. */
    @Getter
    public static class FleshDetectorTarget {
        private final String type = "fleshDetectorTarget";
        private final boolean found;
        private final int x, y, z;
        public FleshDetectorTarget(boolean found, int x, int y, int z) {
            this.found=found; this.x=x; this.y=y; this.z=z;
        }
    }

    /** Committed cocoon alarm: two seconds of presentation without changing block state. */
    @Getter
    public static class FleshAlarm {
        private final String type = "fleshAlarm";
        private final int x, y, z;
        public FleshAlarm(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    }

    /** Coordinate-owned three-slot shelf display snapshot/update. */
    @Getter
    @AllArgsConstructor
    public static class ShelfUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final int state;
        private final List<ShelfSlot> slots;

        public ShelfUpdate(int x, int y, int z, int state, List<ShelfSlot> slots) {
            this("shelfUpdate", x, y, z, state, slots);
            if (slots == null || slots.size() != 3) {
                throw new IllegalArgumentException("shelf update requires exactly three slots");
            }
        }
    }

    /**
     * [SWING] 다른 접속자의 손 휘두르기(바닐라 {@code ClientboundAnimatePacket} SWING_MAIN_HAND · SWING_OFF_HAND).
     * {@code hand} 는 {@code main} · {@code offhand}. 받는 클라가 그 손 아이템의 SwingAnimation 으로 3인칭 팔을 휘두른다.
     */
    @Getter
    @AllArgsConstructor
    public static class PlayerSwing {
        private final String type;
        private final String nickname;
        private final String hand;

        public PlayerSwing(String nickname, String hand) {
            this("playerSwing", nickname, hand);
        }
    }

    /** 웨이브 이모트 연출. 서버 쿨다운(3초) 통과분만 브로드캐스트합니다. */
    @Getter
    @AllArgsConstructor
    public static class Emote {
        private final String type;
        private final String nickname;

        public Emote(String nickname) {
            this("emote", nickname);
        }
    }

    // ── S2a 확정 타입(아이템 드랍 엔티티 · 수면/침대) ──

    /** welcome.items 원소: 드랍 아이템 렌더링에 필요한 현재 상태. */
    @Getter
    @AllArgsConstructor
    public static class ItemEntityDto {
        private final long id;
        private final short itemType;
        private final Integer mapId;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;
        /**
         * [GLINT] 스택 인챈트의 와이어 값({@link com.gameexpert.engine.enchant.WideEnchantments#wireValue()}).
         * 인챈트가 없으면 싣지 않는다(append-only 선택 필드). 드랍 아이템의 광택에만 쓴다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Object enchantments;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations;
        /** 권위 스택 수량. 이전 생성자는 단일 아이템으로 호환한다. */
        private final int count;

        public ItemEntityDto(long id, short itemType, Integer mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations) {
            this(id, itemType, mapId, x, y, z, vx, vy, vz, enchantments, potDecorations, 1);
        }

        public ItemEntityDto(long id, short itemType, int mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments, int count) {
            this(id, itemType, mapId == 0 ? null : mapId, x, y, z, vx, vy, vz,
                    enchantments, null, count);
        }

        public ItemEntityDto withPotDecorations(List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> faces) {
            this.potDecorations = faces == null || faces.isEmpty() ? null : List.copyOf(faces);
            return this;
        }


        public ItemEntityDto(long id, short itemType, Integer mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments) {
            this(id, itemType, mapId, x, y, z, vx, vy, vz, enchantments, null);
        }

        public ItemEntityDto(long id, short itemType, double x, double y, double z,
                double vx, double vy, double vz) {
            this(id, itemType, null, x, y, z, vx, vy, vz, null);
        }

        public ItemEntityDto(long id, short itemType, int mapId, double x, double y, double z,
                double vx, double vy, double vz) {
            this(id, itemType, mapId == 0 ? null : mapId, x, y, z, vx, vy, vz, null);
        }

        public ItemEntityDto(long id, short itemType, int mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments) {
            this(id, itemType, mapId == 0 ? null : mapId, x, y, z, vx, vy, vz, enchantments);
        }
    }

    /** 신규 드랍 아이템 엔티티 렌더 스폰. */
    @Getter
    @AllArgsConstructor
    public static class ItemSpawn {
        private final String type;
        private final long id;
        private final short itemType;
        private final Integer mapId;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;

        /** [GLINT] {@link ItemEntityDto#getEnchantments()} 와 같다. 인챈트가 없으면 싣지 않는다. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Object enchantments;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations;
        private final int count;

        public ItemSpawn(String type, long id, short itemType, Integer mapId,
                double x, double y, double z, double vx, double vy, double vz, Object enchantments,
                List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> potDecorations) {
            this(type, id, itemType, mapId, x, y, z, vx, vy, vz, enchantments, potDecorations, 1);
        }

        public ItemSpawn(long id, short itemType, int mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments, int count) {
            this("itemSpawn", id, itemType, mapId == 0 ? null : mapId,
                    x, y, z, vx, vy, vz, enchantments, null, count);
        }

        public ItemSpawn withPotDecorations(List<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration> faces) {
            this.potDecorations = faces == null || faces.isEmpty() ? null : List.copyOf(faces);
            return this;
        }


        public ItemSpawn(String type, long id, short itemType, Integer mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments) {
            this(type, id, itemType, mapId, x, y, z, vx, vy, vz, enchantments, null);
        }

        public ItemSpawn(long id, short itemType, double x, double y, double z,
                double vx, double vy, double vz) {
            this("itemSpawn", id, itemType, null, x, y, z, vx, vy, vz, null);
        }

        public ItemSpawn(long id, short itemType, int mapId, double x, double y, double z,
                double vx, double vy, double vz) {
            this("itemSpawn", id, itemType, mapId == 0 ? null : mapId,
                    x, y, z, vx, vy, vz, null);
        }

        public ItemSpawn(long id, short itemType, int mapId, double x, double y, double z,
                double vx, double vy, double vz, Object enchantments) {
            this("itemSpawn", id, itemType, mapId == 0 ? null : mapId,
                    x, y, z, vx, vy, vz, enchantments);
        }
    }

    @Getter
    public static class PotDecorationsUpdate {
        private final String type = "potDecorationsUpdate";
        private final int x;
        private final int y;
        private final int z;
        private final int[] itemTypes;

        public PotDecorationsUpdate(int x, int y, int z, int[] itemTypes) {
            if (itemTypes != null && itemTypes.length != 4) {
                throw new IllegalArgumentException("pot decorations require four faces");
            }
            this.x = x;
            this.y = y;
            this.z = z;
            this.itemTypes = itemTypes == null ? null : itemTypes.clone();
        }
    }

    /** scale-0 지도의 128×128 권위 전체 상태. */
    @Getter
    @AllArgsConstructor
    public static class MapState {
        private final String type;
        private final int mapId;
        private final int centerX;
        private final int centerZ;
        private final int scale;
        private final boolean locked;
        private final long revision;
        private final String colorsB64;
        private final com.gameexpert.map.dto.WorldMapData.TargetMarker targetMarker;

        public MapState(int mapId, int centerX, int centerZ, int scale, boolean locked,
                long revision, String colorsB64) {
            this(mapId, centerX, centerZ, scale, locked, revision, colorsB64, null);
        }

        public MapState(int mapId, int centerX, int centerZ, int scale, boolean locked,
                long revision, String colorsB64, com.gameexpert.map.dto.WorldMapData.TargetMarker targetMarker) {
            this("mapState", mapId, centerX, centerZ, scale, locked, revision, colorsB64, targetMarker);
        }
    }

    /** 기존 지도에 적용할 행 우선 직사각형 권위 패치. */
    @Getter
    @AllArgsConstructor
    public static class MapPatch {
        private final String type;
        private final int mapId;
        private final long revision;
        private final int x;
        private final int z;
        private final int width;
        private final int height;
        private final String colorsB64;

        public MapPatch(int mapId, long revision, int x, int z,
                int width, int height, String colorsB64) {
            this("mapPatch", mapId, revision, x, z, width, height, colorsB64);
        }
    }

    /** itemUpdates 원소: 아이템 엔티티의 현재 위치와 스택 수량. */
    @Getter
    @AllArgsConstructor
    public static class ItemPos {
        private final long id;
        private final double x;
        private final double y;
        private final double z;
        private final int count;

        public ItemPos(long id, double x, double y, double z) {
            this(id, x, y, z, 1);
        }
    }

    /** 위치 또는 수량이 바뀐 아이템 엔티티 상태 배칭. */
    @Getter
    @AllArgsConstructor
    public static class ItemUpdates {
        private final String type;
        private final List<ItemPos> items;

        public ItemUpdates(List<ItemPos> items) {
            this("itemUpdates", items);
        }
    }

    /** 플레이어 인벤토리로 실제 이동한 아이템의 위치 기반 획득 피드백. 부분 획득도 포함합니다. */
    @Getter
    @AllArgsConstructor
    public static class ItemPickup {
        private final String type;
        private final double x;
        private final double y;
        private final double z;

        public ItemPickup(double x, double y, double z) {
            this("itemPickup", x, y, z);
        }
    }

    /** 아이템 엔티티 소멸 배칭(reason: pickup/despawn/merge/burn, by: pickup 시 획득 닉네임). */
    @Getter
    @AllArgsConstructor
    public static class ItemRemove {
        private final String type;
        private final List<Long> ids;
        private final String reason;
        private final String by;

        public ItemRemove(List<Long> ids, String reason, String by) {
            this("itemRemove", ids, reason, by);
        }
    }

    // ── SURV-X: 경험치 · 인챈트 테이블 ──

    /** [SURV-X] welcome.xpOrbs 원소이자 xpOrbSpawn 의 몸통이 되는 구슬 상태. */
    @Getter
    @AllArgsConstructor
    public static class XpOrbDto {
        private final long id;
        private final int amount;
        private final double x;
        private final double y;
        private final double z;
    }

    /** [SURV-X] 서버 권위 누적 경험치. 레벨·바 채움은 클라가 XpRules 사본으로 파생한다. */
    @Getter
    @AllArgsConstructor
    public static class XpUpdate {
        private final String type;
        private final int xpTotal;

        public XpUpdate(int xpTotal) {
            this("xpUpdate", xpTotal);
        }
    }

    /** [SURV-X] 신규 경험치 구슬 렌더 스폰. */
    @Getter
    @AllArgsConstructor
    public static class XpOrbSpawn {
        private final String type;
        private final long id;
        private final int amount;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;

        public XpOrbSpawn(long id, int amount, double x, double y, double z,
                double vx, double vy, double vz) {
            this("xpOrbSpawn", id, amount, x, y, z, vx, vy, vz);
        }
    }

    /** [SURV-X] xpOrbUpdates 원소: 이동한 구슬의 현재 위치. */
    @Getter
    @AllArgsConstructor
    public static class XpOrbPos {
        private final long id;
        private final double x;
        private final double y;
        private final double z;
    }

    /** [SURV-X] 이동 중인 경험치 구슬 위치 배칭(틱당 1회). */
    @Getter
    @AllArgsConstructor
    public static class XpOrbUpdates {
        private final String type;
        private final List<XpOrbPos> orbs;

        public XpOrbUpdates(List<XpOrbPos> orbs) {
            this("xpOrbUpdates", orbs);
        }
    }

    /** [SURV-X] 경험치 구슬 소멸 배칭(reason: pickup/despawn/merge). */
    @Getter
    @AllArgsConstructor
    public static class XpOrbRemove {
        private final String type;
        private final List<Long> ids;
        private final String reason;

        public XpOrbRemove(List<Long> ids, String reason) {
            this("xpOrbRemove", ids, reason);
        }
    }

    /** [SURV-X] 인챈트 테이블 제안 한 줄. enchantments 는 확정 마스크(미리보기)다. */
    @Getter
    @AllArgsConstructor
    @JsonPropertyOrder({"levelCost", "lapisCost", "enchantments", "affordable"})
    public static class EnchantOfferDto {
        private final int levelCost;
        private final int lapisCost;
        /** [ENCHANT-WIDE] 확정 제안 집합. 와이어는 {@link #getEnchantments()} 가 싣는다. */
        @JsonIgnore
        private final WideEnchantments wideEnchantments;
        private final boolean affordable;

        public EnchantOfferDto(int levelCost, int lapisCost, long enchantments, boolean affordable) {
            this(levelCost, lapisCost, WideEnchantments.legacy(enchantments), affordable);
        }

        /** 워드 0 마스크(옛 접근자). */
        @JsonIgnore
        public long getEnchantmentMask() {
            return wideEnchantments.word0();
        }

        @JsonProperty("enchantments")
        public Object getEnchantments() {
            return wideEnchantments.wireValue();
        }
    }

    /** [SURV-X] 인챈트 화면 열기 확정. 입력 두 칸·커서·제안 세 줄을 함께 보낸다. */
    @Getter
    @AllArgsConstructor
    public static class EnchantingOpen {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final List<InventorySlot> slots;
        private final CraftingStack cursor;
        private final List<EnchantOfferDto> offers;
        private final int bookshelves;
        private final int xpTotal;

        public EnchantingOpen(int x, int y, int z, List<InventorySlot> slots, CraftingStack cursor,
                List<EnchantOfferDto> offers, int bookshelves, int xpTotal) {
            this("enchantingOpen", x, y, z, slots, cursor, offers, bookshelves, xpTotal);
        }
    }

    /** [SURV-X] 열린 인챈트 화면의 원자적 서버 권위 상태. */
    @Getter
    @AllArgsConstructor
    public static class EnchantingUpdate {
        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final List<InventorySlot> slots;
        private final CraftingStack cursor;
        private final List<EnchantOfferDto> offers;
        private final int bookshelves;
        private final int xpTotal;

        public EnchantingUpdate(int x, int y, int z, List<InventorySlot> slots, CraftingStack cursor,
                List<EnchantOfferDto> offers, int bookshelves, int xpTotal) {
            this("enchantingUpdate", x, y, z, slots, cursor, offers, bookshelves, xpTotal);
        }
    }

    /** [SURV-X] 인챈트 화면 닫기 확정. */
    @Getter
    @AllArgsConstructor
    public static class EnchantingClosed {
        private final String type;
        private final int x;
        private final int y;
        private final int z;

        public EnchantingClosed(int x, int y, int z) {
            this("enchantingClosed", x, y, z);
        }
    }

    /** [VILLAGER-TRADE] 주민 거래 오퍼 한 줄. costCount 는 수요 보정을 반영한 현재 가격이다. */
    @Getter
    @AllArgsConstructor
    public static class VillagerTradeOfferDto {
        private final int costItem;
        private final int costCount;
        /** 두 번째 비용이 없으면 0(AIR)이다. */
        private final int costBItem;
        private final int costBCount;
        private final int resultItem;
        private final int resultCount;
        private final int uses;
        private final int maxUses;
        /** 주민 레벨이 모자라 아직 열리지 않은 줄. */
        private final boolean locked;
        private final CraftingStack costStack;
        private final CraftingStack costBStack;
        private final CraftingStack resultStack;
        public VillagerTradeOfferDto(int costItem,int costCount,int costBItem,int costBCount,int resultItem,int resultCount,int uses,int maxUses,boolean locked) {
            this(costItem,costCount,costBItem,costBCount,resultItem,resultCount,uses,maxUses,locked,null,null,null);
        }

    }

    /** [VILLAGER-TRADE] 주민 거래 화면 열기 확정. */
    @Getter
    @AllArgsConstructor
    public static class VillagerTradeOpen {
        private final String type;
        private final long mobId;
        private final int level;
        private final int tradeXp;
        private final List<VillagerTradeOfferDto> offers;
        private final int selectedOffer;
        private final List<CraftingStack> payment;
        private final CraftingStack result;
        private final CraftingStack cursor;

        public VillagerTradeOpen(
                long mobId, int level, int tradeXp, List<VillagerTradeOfferDto> offers,
                int selectedOffer, List<CraftingStack> payment, CraftingStack result,
                CraftingStack cursor) {
            this("villagerTradeOpen", mobId, level, tradeXp, offers, selectedOffer,
                    payment, result, cursor);
        }
    }

    /** [VILLAGER-TRADE] 거래 한 번마다 재고·가격·레벨을 다시 싣는 원자적 상태. */
    @Getter
    @AllArgsConstructor
    public static class VillagerTradeUpdate {
        private final String type;
        private final long mobId;
        private final int level;
        private final int tradeXp;
        private final List<VillagerTradeOfferDto> offers;
        private final int selectedOffer;
        private final List<CraftingStack> payment;
        private final CraftingStack result;
        private final CraftingStack cursor;

        public VillagerTradeUpdate(
                long mobId, int level, int tradeXp, List<VillagerTradeOfferDto> offers,
                int selectedOffer, List<CraftingStack> payment, CraftingStack result,
                CraftingStack cursor) {
            this("villagerTradeUpdate", mobId, level, tradeXp, offers, selectedOffer,
                    payment, result, cursor);
        }
    }

    /** [VILLAGER-TRADE] 주민 거래 화면 닫기 확정. */
    @Getter
    @AllArgsConstructor
    public static class VillagerTradeClosed {
        private final String type;
        private final long mobId;

        public VillagerTradeClosed(long mobId) {
            this("villagerTradeClosed", mobId);
        }
    }

    @Getter
    public static class PlayerStatistics {
        private final String type = "playerStatistics";
        private final int sleepInStrawBed;

        public PlayerStatistics(int sleepInStrawBed) {
            this.sleepInStrawBed = sleepInStrawBed;
        }
    }

    /** 수면 현황 브로드캐스트(sleeping=현재 수면 인원, total=접속자 수). */
    @Getter
    @AllArgsConstructor
    public static class SleepStatus {
        private final String type;
        private final int sleeping;
        private final int total;

        public SleepStatus(int sleeping, int total) {
            this("sleepStatus", sleeping, total);
        }
    }

    /** 접속자 전원 수면 시 아침 전환 알림(월드 브로드캐스트). */
    @Getter
    @AllArgsConstructor
    public static class WakeUp {
        private final String type;

        public WakeUp() {
            this("wakeUp");
        }
    }

    /** 개인 리스폰 지점 설정 확정. */
    @Getter
    @AllArgsConstructor
    public static class BedSpawnSet {
        private final String type;

        public BedSpawnSet() {
            this("bedSpawnSet");
        }
    }

    /**
     * [MAPNAV] 현재 유효한 리스폰 지점(침대가 있으면 침대, 없으면 월드 스폰).
     * 나침반 바늘이 가리킬 좌표이며 입장·침대 설정·리스폰 확정 시 개인에게만 보낸다.
     */
    @Getter
    @AllArgsConstructor
    public static class SpawnPoint {
        private final String type;
        private final double x;
        private final double y;
        private final double z;
        /** 침대 지점이면 true, 월드 스폰 폴백이면 false. */
        private final boolean bed;

        public SpawnPoint(double x, double y, double z, boolean bed) {
            this("spawnPoint", x, y, z, bed);
        }
    }

    // ── 보트(클라 권위·일시적·제작 아이템). 서버는 좌석·중계·수명만 담당 ──

    /**
     * [CONTAINER-MENUS] One player-placed armor stand or minecart. {@code yaw}/{@code pitch} are
     * vanilla {@code yRot}/{@code xRot} degrees. Minecarts carry their rider, the
     * {@code VehicleEntity} hurt shake ({@code hurtTime}, {@code hurtDir}, {@code damage}), the
     * furnace {@code lit} flag and the TNT {@code fuse} (-1 when not primed); armor stands carry
     * their six equipment item IDs (main hand, off hand, feet, legs, chest, head) and client flags
     * (1 small, 4 show arms, 8 no base plate). Hopper minecarts put {@code enabled} in {@code lit}.
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public static class PlacedEntityDto {
        private long mobPassengerId;
        public PlacedEntityDto withMobPassenger(long mobId) { mobPassengerId = mobId; return this; }
        private final long id;
        private final String kind;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final String rider;
        private final int hurtTime;
        private final int hurtDir;
        private final float damage;
        private final List<Integer> equipment;
        private List<PlacedEquipmentAppearance> equipmentDetails = List.of();
        public PlacedEntityDto withEquipmentDetails(List<PlacedEquipmentAppearance> details) {
            equipmentDetails = List.copyOf(details);
            return this;
        }
        private final int flags;
        private final boolean lit;
        private final int fuse;
    }

    @lombok.Value
    public static class PlacedEquipmentAppearance {
        boolean glint;
        Integer leatherColor;
        String trim;
    }

    /** [CONTAINER-MENUS] New placed entities, and every live one for a joining player. */
    @Getter
    @AllArgsConstructor
    public static class PlacedEntitySpawn {
        private final String type;
        private final List<PlacedEntityDto> entities;

        public PlacedEntitySpawn(List<PlacedEntityDto> entities) {
            this("placedEntitySpawn", entities);
        }
    }

    /** [CONTAINER-MENUS] Full current state of the placed entities that changed this tick. */
    @Getter
    @AllArgsConstructor
    public static class PlacedEntityUpdate {
        private final String type;
        private final List<PlacedEntityDto> entities;

        public PlacedEntityUpdate(List<PlacedEntityDto> entities) {
            this("placedEntityUpdate", entities);
        }
    }

    /** [CONTAINER-MENUS] Placed entities that were broken, exploded or dropped as items. */
    @Getter
    @AllArgsConstructor
    public static class PlacedEntityRemove {
        private final String type;
        private final List<Long> ids;

        public PlacedEntityRemove(List<Long> ids) {
            this("placedEntityRemove", ids);
        }
    }

    /**
     * [CONTAINER-MENUS] {@code ArmorStand#hurtServer}'s entity event 32: the client plays
     * {@code entity.armor_stand.hit} (0.3, 1) and starts the 5-tick wobble.
     */
    /**
     * [CONTAINER-MENUS] {@code ServerLevel.sendParticles(SPLASH, x + nextDouble(), y + 1,
     * z + nextDouble(), 1, 0, 0, 0, 1)} five times over a block a water bottle turned into mud
     * ({@code PotionItem#useOn}, {@code DispenseItemBehavior$13}). x/y/z is the block; the client
     * draws the five random offsets. Sent to players within 32 blocks.
     */
    @Getter
    @AllArgsConstructor
    public static class SplashParticles {
        private final String type;
        private final int x;
        private final int y;
        private final int z;

        public SplashParticles(int x, int y, int z) {
            this("splashParticles", x, y, z);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PlacedEntityHit {
        private final String type;
        private final long id;

        public PlacedEntityHit(long id) {
            this("placedEntityHit", id);
        }
    }

    /**
     * [CONTAINER-MENUS] Personal message to a rider that left a minecart: the exact
     * {@code getDismountLocationForPassenger} position the client moves the player to.
     */
    @Getter
    @AllArgsConstructor
    public static class PlacedEntityDismounted {
        private final String type;
        private final long id;
        private final double x;
        private final double y;
        private final double z;

        public PlacedEntityDismounted(long id, double x, double y, double z) {
            this("placedEntityDismounted", id, x, y, z);
        }
    }

    /**
     * [CONTAINER-MENUS] A chest (27) or hopper (5) minecart's cargo menu, opened or updated for
     * every player viewing it. {@code kind} is the minecart kind.
     */
    @Getter
    @AllArgsConstructor
    public static class EntityCargoOpen {
        private final String type;
        private final long entityId;
        private final String kind;
        private final List<InventorySlot> slots;

        public EntityCargoOpen(long entityId, String kind, List<InventorySlot> slots) {
            this("entityCargoOpen", entityId, kind, slots);
        }
    }

    /** [CONTAINER-MENUS] The same snapshot after a cargo change, to every viewer. */
    @Getter
    @AllArgsConstructor
    public static class EntityCargoUpdate {
        private final String type;
        private final long entityId;
        private final String kind;
        private final List<InventorySlot> slots;

        public EntityCargoUpdate(long entityId, String kind, List<InventorySlot> slots) {
            this("entityCargoUpdate", entityId, kind, slots);
        }
    }

    /** [CONTAINER-MENUS] The entity cargo menu closed (distance, removal or request). */
    @Getter
    @AllArgsConstructor
    public static class EntityCargoClosed {
        private final String type;
        private final long entityId;

        public EntityCargoClosed(long entityId) {
            this("entityCargoClosed", entityId);
        }
    }

    /** welcome.boats 원소: 현재 존재하는 보트의 위치·yaw·운전자(없으면 null). */
    @Getter
    @AllArgsConstructor
    public static class BoatDto {
        private final long boatId;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
        private final String driver;
        private final List<String> playerPassengerNames;
        private final List<Long> mobPassengerIds;
    }

    /** 신규 보트 스폰(설치 시 즉시). 운전자 배정은 뒤따르는 boatMount 로 방송. */
    @Getter
    @AllArgsConstructor
    public static class BoatSpawn {
        private final String type;
        private final long boatId;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;

        public BoatSpawn(long boatId, double x, double y, double z, double yaw) {
            this("boatSpawn", boatId, x, y, z, yaw);
        }
    }

    /** boatUpdate 원소: 운전자 클라가 올린 보트의 현재 위치·yaw. */
    @Getter
    @AllArgsConstructor
    public static class BoatPosDto {
        private final long boatId;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
        private final String driver;
        private final List<String> playerPassengerNames;
        private final List<Long> mobPassengerIds;
    }

    /** 이동한 보트 위치 배칭(틱당 1회, 변경분만). */
    @Getter
    @AllArgsConstructor
    public static class BoatUpdate {
        private final String type;
        private final List<BoatPosDto> boats;

        public BoatUpdate(List<BoatPosDto> boats) {
            this("boatUpdate", boats);
        }
    }

    /** 소멸한 보트 ID 배칭. */
    @Getter
    @AllArgsConstructor
    public static class BoatDespawn {
        private final String type;
        private final List<Long> boatIds;

        public BoatDespawn(List<Long> boatIds) {
            this("boatDespawn", boatIds);
        }
    }

    /** 탑승 확정 방송과 변경 후 전체 좌석. */
    @Getter
    @AllArgsConstructor
    public static class BoatMount {
        private final String type;
        private final long boatId;
        private final String nickname;
        private final String driver;
        private final List<String> playerPassengerNames;
        private final List<Long> mobPassengerIds;

        public BoatMount(long boatId, String nickname, String driver,
                List<String> playerPassengerNames, List<Long> mobPassengerIds) {
            this("boatMount", boatId, nickname, driver, playerPassengerNames, mobPassengerIds);
        }
    }

    /** 하차 확정 방송(자발적 하차 또는 세션 종료). */
    @Getter
    @AllArgsConstructor
    public static class BoatDismount {
        private final String type;
        private final long boatId;
        private final String nickname;
        private final String driver;
        private final List<String> playerPassengerNames;
        private final List<Long> mobPassengerIds;

        public BoatDismount(long boatId, String nickname, String driver,
                List<String> playerPassengerNames, List<Long> mobPassengerIds) {
            this("boatDismount", boatId, nickname, driver, playerPassengerNames, mobPassengerIds);
        }
    }

    // ── 26.3 cushion decoration entities ─────────────────────────────

    @Getter
    @AllArgsConstructor
    public static class CushionDto {
        private final long cushionId;
        private final short itemType;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final String rider;
        private final String customName;
    }

    @Getter
    @AllArgsConstructor
    public static class CushionSpawn {
        private final String type;
        private final CushionDto cushion;

        public CushionSpawn(CushionDto cushion) {
            this("cushionSpawn", cushion);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CushionRemove {
        private final String type;
        private final long cushionId;

        public CushionRemove(long cushionId) {
            this("cushionRemove", cushionId);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CushionMount {
        private final String type;
        private final long cushionId;
        private final String nickname;

        public CushionMount(long cushionId, String nickname) {
            this("cushionMount", cushionId, nickname);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CushionDismount {
        private final String type;
        private final long cushionId;
        private final String nickname;

        public CushionDismount(long cushionId, String nickname) {
            this("cushionDismount", cushionId, nickname);
        }
    }

    // ── Authenticated generated non-mob entities ─────────────────────

    /** Marker for the exact discriminated union consumed by GeneratedEntityProtocol.ts. */
    public interface GeneratedEntitySnapshot {}

    @Getter
    @AllArgsConstructor
    public static class ArmorStandSnapshot implements GeneratedEntitySnapshot {
        private final int schema;
        private final String kind;
        private final long entityId;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final double velocityX;
        private final double velocityY;
        private final double velocityZ;
        private final float[] poseHead;
        private final float[] poseBody;
        private final String equipmentSlot;
        private final String equipmentItem;
        private final boolean showArms;
        private final boolean small;
        private final boolean noBasePlate;
        private final boolean invisible;
        private final boolean invulnerable;
        private final int disabledSlots;
        private final float health;

        public float[] getPoseHead() { return poseHead.clone(); }
        public float[] getPoseBody() { return poseBody.clone(); }
    }

    @Getter
    @AllArgsConstructor
    public static class GeneratedCushionSnapshot implements GeneratedEntitySnapshot {
        private final int schema;
        private final String kind;
        private final long entityId;
        private final long cushionId;
        private final short itemType;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final String rider;
        private final String customName;
        private final boolean invulnerable;
    }

    @Getter
    @AllArgsConstructor
    public static class ChestMinecartSnapshot implements GeneratedEntitySnapshot {
        private final int schema;
        private final String kind;
        private final long entityId;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final double velocityX;
        private final double velocityY;
        private final double velocityZ;
        private final String lootState;
    }

    @Getter
    @AllArgsConstructor
    public static class GeneratedEntityTarget {
        private final int schema;
        private final String kind;
        private final long entityId;
    }

    @Getter
    @AllArgsConstructor
    public static class GeneratedEntitySpawn {
        private final String type;
        private final List<GeneratedEntitySnapshot> entities;

        public GeneratedEntitySpawn(List<GeneratedEntitySnapshot> entities) {
            this("generatedEntitySpawn", List.copyOf(entities));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class GeneratedEntityUpdate {
        private final String type;
        private final List<GeneratedEntitySnapshot> entities;

        public GeneratedEntityUpdate(List<GeneratedEntitySnapshot> entities) {
            this("generatedEntityUpdate", List.copyOf(entities));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class GeneratedEntityRemove {
        private final String type;
        private final List<GeneratedEntityTarget> targets;

        public GeneratedEntityRemove(List<GeneratedEntityTarget> targets) {
            this("generatedEntityRemove", List.copyOf(targets));
        }
    }

    /**
     * [FARM-ANIMAL] 돼지 탑승 확정 방송. 이 방송을 받은 기수 클라가 좌표 정본이 되어
     * {@code pigPos} 를 올린다(보트 {@code boatMount} 와 같은 계약).
     */
    @Getter
    @AllArgsConstructor
    public static class PigMount {
        private final String type;
        private final long mobId;
        private final String nickname;

        public PigMount(long mobId, String nickname) {
            this("pigMount", mobId, nickname);
        }
    }

    /** [FARM-ANIMAL] 돼지 하차 확정 방송(자발적 하차·사망·세션 종료). */
    @Getter
    @AllArgsConstructor
    public static class PigDismount {
        private final String type;
        private final long mobId;
        private final String nickname;

        public PigDismount(long mobId, String nickname) {
            this("pigDismount", mobId, nickname);
        }
    }

    /**
     * [FARM-ANIMAL] 당근 낚싯대 부스트 커서 방송. 기수 클라가 바닐라
     * {@code 1 + 1.15·sin(boostTime/boostTimeTotal·PI)} 배율을 그대로 적분하려면
     * 서버가 굴린 총 지속이 필요하다.
     */
    @Getter
    @AllArgsConstructor
    public static class PigBoost {
        private final String type;
        private final long mobId;
        private final int boostMcTicks;
        private final int boostTotalMcTicks;

        public PigBoost(long mobId, int boostMcTicks, int boostTotalMcTicks) {
            this("pigBoost", mobId, boostMcTicks, boostTotalMcTicks);
        }
    }

    /**
     * [MOUNT] 종 비의존 탑승 확정 방송. 돼지는 하위호환을 위해 {@code pigMount} 를 그대로 쓰고,
     * 그 밖의 탈것 종은 이 메시지를 쓴다. {@code seatIndex} 는 다인승(낙타 2인승)을 위해 처음부터
     * 계약에 들어 있으며 조종석은 0 하나뿐이다({@code MobMountRules.CONTROLLING_SEAT_INDEX}).
     * {@code species} 는 클라가 좌석 오프셋·조종 배율을 고르는 표의 키다.
     */
    @Getter
    @AllArgsConstructor
    public static class MobMount {
        private final String type;
        private final long mobId;
        private final String nickname;
        private final int seatIndex;
        private final String species;
        /** 이 개체의 탑승 이동 속도(블록/초)와 점프 강도. 개체 스탯이 있는 종은 개체마다 다르다. */
        private final double speedBlocksPerSecond;
        private final double jumpStrength;
        /**
         * [MOUNT] 이 좌석에서 <b>조종</b>할 수 있는가({@code MobMountRules.steerable}). 바닐라
         * {@code AbstractHorse#getControllingPassenger} 는 안장이 없으면 null 이라, 안장 없는
         * 말·당나귀는 태우기만 하고 이동 입력을 받지 않는다. 라마는 언제나 거짓이다. 기수 클라는
         * 이 값이 거짓이면 이동·점프 입력을 아예 만들지 않는다(권위도 같은 판정으로 거절한다).
         */
        private final boolean steerable;

        public MobMount(long mobId, String nickname, int seatIndex, String species,
                double speedBlocksPerSecond, double jumpStrength, boolean steerable) {
            this("mobMount", mobId, nickname, seatIndex, species, speedBlocksPerSecond,
                    jumpStrength, steerable);
        }
    }

    /** [MOUNT] 종 비의존 하차 확정 방송(자발적 하차·사망·세션 종료·미길들임 말의 낙마). */
    @Getter
    @AllArgsConstructor
    public static class MobDismount {
        private final String type;
        private final long mobId;
        private final String nickname;
        private final int seatIndex;

        public MobDismount(long mobId, String nickname, int seatIndex) {
            this("mobDismount", mobId, nickname, seatIndex);
        }
    }

    /** 현재 기수 한 명의 생물 체력/점프 HUD 권위 스냅샷. */
    @Getter
    @AllArgsConstructor
    public static class MountedHud {
        private final String type;
        private final long mobId;
        private final double health;
        private final double maxHealth;
        private final boolean showJumpMeter;
        private final double charge;

        public MountedHud(long mobId, double health, double maxHealth,
                boolean showJumpMeter, double charge) {
            this("mountedHud", mobId, health, maxHealth, showJumpMeter, charge);
        }
    }

    /**
     * [MOUNT] 탈것 점프 확정 방송. 권위는 좌표를 굴리지 않으므로 검증을 통과한 점프의 초기
     * 수직 속도(블록/초)만 알린다 — 기수 클라는 이 값으로, 구경꾼 클라는 애니메이션으로 쓴다.
     * 바닐라 {@code AbstractHorse#executeRidersJump} 의 {@code jumpStrength × scale} 이다.
     */
    @Getter
    @AllArgsConstructor
    public static class MobJumped {
        private final String type;
        private final long mobId;
        private final String nickname;
        private final double velocityBlocksPerSecond;

        public MobJumped(long mobId, String nickname, double velocityBlocksPerSecond) {
            this("mobJumped", mobId, nickname, velocityBlocksPerSecond);
        }
    }

    /**
     * [MOUNT] 낙타 대시 확정 방송. 낙타의 점프 키는 도약이 아니라 전방 대시라
     * ({@code Camel#executeRidersJump}) 수직 성분 하나로는 재현할 수 없다. 그래서 점프 대신
     * 수평·수직 두 성분과 그 자리에서 채워진 쿨다운을 함께 알린다 — 기수 클라는 임펄스로,
     * 구경꾼 클라는 애니메이션·게이지로 쓴다. {@code mobJumped} 는 한 글자도 바뀌지 않는
     * append-only 추가다.
     */
    @Getter
    @AllArgsConstructor
    public static class MobDashed {
        private final String type;
        private final long mobId;
        private final String nickname;
        /** {@code DASH_HORIZONTAL_MOMENTUM × scale × jumpStrength} 를 20 TPS 로 환산한 값. */
        private final double horizontalBlocksPerSecond;
        /** {@code DASH_VERTICAL_MOMENTUM × scale × jumpStrength} 를 20 TPS 로 환산한 값. */
        private final double verticalBlocksPerSecond;
        /** 그 자리에서 채워진 대시 쿨다운(MC 틱). 클라 게이지가 이 값에서 되감긴다. */
        private final int cooldownMcTicks;

        public MobDashed(long mobId, String nickname, double horizontalBlocksPerSecond,
                double verticalBlocksPerSecond, int cooldownMcTicks) {
            this("mobDashed", mobId, nickname, horizontalBlocksPerSecond,
                    verticalBlocksPerSecond, cooldownMcTicks);
        }
    }

    /**
     * 레이드 보스바 방송. 진행률은 계약대로 "생존 레이더 체력 합 / 누적 total 체력"이며 양 권위가
     * 같은 수치를 냅니다. 목록이 바뀔 때만 보내므로 틱당 상시 트래픽이 없고, {@code active=false}
     * 한 건이 바를 내리는 유일한 신호입니다. raidId 는 64비트라 JS 정밀도 손실을 피해 16자리
     * 16진수 문자열로 싣습니다.
     */
    @Getter
    @AllArgsConstructor
    public static class RaidBossbar {
        public static final Set<String> STATUSES =
                Set.of("ONGOING", "VICTORY", "LOSS", "STOPPED");
        private final String type;
        private final String raidId;
        private final boolean active;
        /** 0..1 */
        private final double progress;
        /** 1-based 현재 웨이브. 아직 방출 전이면 0. */
        private final int wave;
        private final int waveCount;
        private final String status;

        public RaidBossbar(String raidId, boolean active, double progress, int wave,
                int waveCount, String status) {
            this("raidBossbar", raidId, active, progress, wave, waveCount, status);
            if (status == null || !STATUSES.contains(status)) {
                throw new IllegalArgumentException("Unknown raidBossbar status: " + status);
            }
            if (!(progress >= 0.0 && progress <= 1.0)) {
                throw new IllegalArgumentException("raidBossbar progress out of range: " + progress);
            }
        }
    }

    /** [GLOWING] playerEffects 원소: 다른 플레이어가 보는 효과 한 건(종류와 은은함만). */
    @Getter
    @AllArgsConstructor
    public static class PlayerEffectDto {
        private final String effect;
        private final boolean ambient;
    }

    /**
     * [GLOWING] 다른 플레이어의 보이는 효과 목록. 바닐라가 추적 중인 클라에 플레이어의 발광 공유 플래그와
     * 효과 입자 목록(DATA_EFFECT_PARTICLES · DATA_EFFECT_AMBIENCE_ID)을 동기화하는 자리다. 목록의 종류나
     * 은은함이 바뀔 때만 본인을 뺀 접속자에게 보내고, 새로 들어온 접속자는 비어 있지 않은 목록을 한 번씩
     * 받는다. 빈 배열은 전부 해제다. 이름 집합은 {@link EffectUpdate#EFFECTS} 다.
     */
    @Getter
    @AllArgsConstructor
    public static class PlayerEffects {
        private final String type;
        private final String nickname;
        private final List<PlayerEffectDto> effects;

        public PlayerEffects(String nickname, List<PlayerEffectDto> effects) {
            this("playerEffects", nickname, effects);
            for (PlayerEffectDto effect : effects) {
                if (effect.getEffect() == null || !EffectUpdate.EFFECTS.contains(effect.getEffect())) {
                    throw new IllegalArgumentException("Unknown playerEffects effect: " + effect.getEffect());
                }
            }
        }
    }

    /**
     * [DRAGON] 엔더 드래곤 표현 사실(바닐라 {@code DATA_PHASE} · {@code dragonDeathTime} · {@code nearestCrystal}).
     * 바뀔 때만 엔드 차원 월드에 방송하고 입장한 연결에는 현재 값을 한 번 보낸다. {@code deathTicks} 는 20 TPS
     * {@code dragonDeathTime}(살아 있으면 0), {@code nearestCrystalMobId} 는 치유 광선 수정(없으면 null).
     */
    @Getter
    @AllArgsConstructor
    public static class DragonState {
        private final String type;
        private final long mobId;
        private final int phase;
        private final int deathTicks;
        private final Long nearestCrystalMobId;

        public DragonState(long mobId, int phase, int deathTicks, Long nearestCrystalMobId) {
            this("dragonState", mobId, phase, deathTicks, nearestCrystalMobId);
            if (phase < 0 || phase > 10) throw new IllegalArgumentException("dragon phase out of range: " + phase);
        }
    }

    /**
     * [DRAGON] 드래곤 여덟 부위({@code EnderDragonPart}: head, neck, body, tail1..3, wing1, wing2)의 발밑 중심 x,y,z × 8
     * = 24 칸. 권위 두뇌의 그 틱 위치이며 바뀐 틱에만 보낸다. 클라는 형상 정본(DragonPartGeometry, 바닐라 부위
     * 크기 = {@code DragonBrain.PART_WIDTH/PART_HEIGHT})으로 권위와 같은 상자를 만들어 조준한다.
     */
    @Getter
    @AllArgsConstructor
    public static class DragonParts {
        private final String type;
        private final long mobId;
        private final List<Double> centers;

        public DragonParts(long mobId, double[] centers) {
            this("dragonParts", mobId, java.util.Arrays.stream(centers).boxed().toList());
            if (centers.length != 24) throw new IllegalArgumentException("dragon part centers must have 24 values");
        }
    }

    /** [DRAGON] 엔드 수정 한 개의 광선 목표(블록 좌표). {@code target} 이 null 이면 광선이 없다. */
    @Getter
    @AllArgsConstructor
    public static class EndCrystalBeam {
        private final long mobId;
        private final PositionDto target;
    }

    /** [DRAGON] 엔드 수정 광선 목표(바닐라 {@code DATA_BEAM_TARGET}). 바뀐 수정만 싣고 target=null 이 광선을 끈다. */
    @Getter
    @AllArgsConstructor
    public static class EndCrystalBeams {
        private final String type;
        private final List<EndCrystalBeam> crystals;

        public EndCrystalBeams(List<EndCrystalBeam> crystals) {
            this("endCrystalBeams", List.copyOf(crystals));
        }
    }

    /**
     * [DRAGON] 드래곤 보스바(바닐라 {@code EnderDragonFight.dragonEvent}: PINK · PROGRESS · playBossMusic ·
     * createWorldFog). 개인 송신 — 중심 (0,128,0) 192 블록 안 플레이어만 20 MC 틱마다 들고 난다. active=false 한 건이
     * 바를 내린다(보스 음악·안개도 끝난다).
     */
    @Getter
    @AllArgsConstructor
    public static class DragonBossbar {
        private final String type;
        private final boolean active;
        private final double progress;

        public DragonBossbar(boolean active, double progress) {
            this("dragonBossbar", active, progress);
            if (!(progress >= 0.0 && progress <= 1.0)) {
                throw new IllegalArgumentException("dragonBossbar progress out of range: " + progress);
            }
        }
    }

    /** effectUpdate 원소: 상태이상 1건. remainingTicks 는 서버 10 TPS 기준 남은 틱. */
    @Getter
    @AllArgsConstructor
    public static class EffectDto {
        private final String effect;
        /** 0-based 레벨(MC 표기 I = 0). */
        private final int amplifier;
        private final int remainingTicks;
        private final boolean ambient;
    }

    /**
     * 개인 상태이상 갱신. 빈 목록은 전부 해제를 뜻하며, 클라이언트는 남은 시간을 자체 카운트다운합니다.
     * 목록 구성이 바뀔 때만 보내므로 틱당 상시 트래픽이 없습니다.
     */
    @Getter
    @AllArgsConstructor
    public static class EffectUpdate {
        /**
         * Must list every {@code StatusEffect.protocolName()} that can sit in a player's active
         * list, and must stay equal to the client's {@code StatusEffectKind} union. A missing name
         * is not a dropped message: {@code broadcastEffects} throws while building the payload, the
         * effect stays dirty, and the throw repeats every tick for the whole life of the effect —
         * which also skips the rest of that tick's broadcast/persist phase for the world. Browser
         * QA hit exactly that with {@code raid_omen} after drinking an ominous bottle.
         */
        public static final Set<String> EFFECTS = Set.of(
                "poison", "slowness", "weakness", "bad_omen", "raid_omen", "instant_damage",
                "blindness", "speed", "mining_fatigue",
                // [DEEP-DARK] 어둠. 화이트리스트 누락은 메시지 하나가 빠지는 사고가 아니라
                // broadcastEffects 가 매 틱 던지는 영구 장애다(위 주석의 raid_omen 사고).
                "darkness",
                // [SULFUR] 강한 유황 유독 가스가 갱신하는 ambient 메스꺼움.
                "nausea",
                // [BRIMSTONE] 화염 저항. 같은 이유로 화이트리스트에 반드시 있어야 한다.
                "fire_resistance",
                // [POTION-GAP] 힘·수중 호흡·도약.
                "strength", "water_breathing", "breath_of_the_nautilus", "jump_boost",
                // [POTION-GAP] 아래 셋은 이 트랙이 만든 효과가 아니라 **누락 보강**이다 —
                // [COOKING] 이 night_vision·saturation 을, [GOLD-FOOD] 가 resistance 를
                // StatusEffect 에 넣으면서 이 화이트리스트를 같이 갱신하지 않았다. 위 주석대로
                // 누락은 메시지 하나가 빠지는 사고가 아니라 broadcastEffects 가 매 틱 던지는
                // 영구 장애다. 야간 투시는 이 트랙의 물약 사슬도 같은 이름을 부여한다.
                "night_vision", "saturation", "resistance",
                // [CONDUIT] 콘딧 파워. 활성 콘딧이 2초마다 재부여하므로 누락하면
                // broadcastEffects 가 **매 2초마다 영구히** 던진다(위 raid_omen 사고와 같은
                // 꼴이고, 콘딧을 켜 둔 채로는 그 월드의 방송/영속 단계가 계속 잘린다).
                "conduit_power", "hunger",
                // [TRIAL] 시련의 징조. 트라이얼 스포너가 불길한 징조를 바꿔 준다.
                "trial_omen",
                // [TRIAL-GAP] 재생의 물약 · 잔류형 물약 다섯 효과.
                "regeneration", "slow_falling", "wind_charged", "weaving", "oozing",
                "infested",
                // [GLOWING] 발광. 표현 전용이고, 몹은 MobSpawnDto/MobUpdateDto.effects 로 복제된다.
                "glowing",
                // [END-CITY] 셜커 탄환의 공중 부양 · 즉시 회복(목록에 남지 않지만 이름 집합은 닫혀 있어야 한다),
                // [RAID-OMEN] 마을의 영웅. 누락하면 broadcastEffects 가 이름 검사에서 던진다.
                "levitation", "instant_health", "hero_of_the_village",
                // [BEACON] 성급함(신호기 1단계), [BREWING-26.3] 투명화 · 행운 물약.
                "haste", "invisibility", "luck");
        private final String type;
        /** 대상 플레이어 닉네임. */
        private final String target;
        private final List<EffectDto> effects;

        public EffectUpdate(String target, List<EffectDto> effects) {
            this("effectUpdate", target, effects);
            for (EffectDto effect : effects) {
                if (effect.getEffect() == null || !EFFECTS.contains(effect.getEffect())) {
                    throw new IllegalArgumentException(
                            "Unknown effectUpdate effect: " + effect.getEffect());
                }
            }
        }
    }
}
