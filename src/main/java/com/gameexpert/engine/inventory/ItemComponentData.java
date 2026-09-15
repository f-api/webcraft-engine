package com.gameexpert.engine.inventory;

import java.util.List;

import com.gameexpert.engine.SuspiciousStewRules;
import com.gameexpert.engine.enchant.WideEnchantments;

/** Immutable validated stack identity components. */
public final class ItemComponentData {
    public static final ItemComponentData EMPTY = new ItemComponentData(
            null, List.of(), null, 0, null, null, null, null, 0L, 0L, null, null);
    public static final int MAX_BANNER_LAYERS = 6;
    /**
     * [SHIELD-PATTERN] 방패는 바닐라 {@code BASE_COLOR} 를 맨 앞 {@code BASE} 층으로 싣고 그 뒤에
     * 현수막의 무늬 여섯 층까지를 싣는다({@code ShieldDecorationRecipe.assemble}).
     */
    public static final int MAX_SHIELD_LAYERS = MAX_BANNER_LAYERS + 1;
    /**
     * [TRIAL-GAP] 바닐라 {@code OminousBottleAmplifier} 코덱 범위 0..4
     * ({@code ExtraCodecs.intRange(0, 4)}). 0 은 {@code Items.OMINOUS_BOTTLE} 의 기본 컴포넌트라
     * 여기서는 "없음"(null)으로 정규화한다 — 바닐라도 기본값과 같은 패치는 스택 정체성에서 사라지고,
     * 옛 저장 행(필드 없음)이 그대로 증폭 0 이 된다.
     */
    public static final int MAX_OMINOUS_BOTTLE_AMPLIFIER = 4;

    private final String customName;
    private final List<BannerLayer> bannerPatterns;
    private final BookData book;
    private final int anvilUseCount;
    private final Integer leatherColor;
    private final String suspiciousStewEffect;
    private final Integer suspiciousStewDurationMcTicks;
    /** [TRIAL-GAP] 불길한 병 증폭 1..4. 기본 0 은 null 로 정규화한다. */
    private final Integer ominousBottleAmplifier;
    /**
     * [ENCHANT-WIDE] 인챈트 ID 16..31 의 3비트 칸(워드 1)과 ID 32..42 의 칸(워드 2). 워드 0 은
     * 스택의 {@code long enchantments} 칸에 그대로 산다({@link WideEnchantments}).
     */
    private final long enchantmentWord1;
    private final long enchantmentWord2;
    /**
     * [UTILITY] 바닐라 {@code minecraft:potion_contents} 의 물약 레지스트리 경로(네임스페이스 없이,
     * 예 {@code long_turtle_master}). 범용 물약 세 형태(CONTENTS_*)에만 붙는다. 없으면 null.
     */
    private final String potionContents;
    /** [UTILITY] 바닐라 {@code minecraft:trim}(무늬 + 재료). 장식 가능한 방어구에만 붙는다. 없으면 null. */
    private final ArmorTrim trim;
    private final List<PotDecoration> potDecorations;

    /** Four face ingredients in back, left, right, front order; ingredients cannot nest pots. */
    @lombok.Getter
    public static final class PotDecoration {
        private final short itemType;
        private final String customName;
        private final int anvilUseCount;

        public PotDecoration(short itemType, String customName, int anvilUseCount) {
            if (itemType != PlayerInventory.BRICK &&
                    !com.gameexpert.terrain.Blocks.isPotterySherd(Short.toUnsignedInt(itemType)))
                throw new IllegalArgumentException("pot decoration requires brick or pottery sherd");
            new ItemComponentData(customName, List.of(), null, anvilUseCount, null);
            this.itemType = itemType;
            this.customName = customName;
            this.anvilUseCount = anvilUseCount;
        }
        public short itemType() { return itemType; }
        public String customName() { return customName; }
        public int anvilUseCount() { return anvilUseCount; }
        public String componentData() {
            return ItemComponentCodec.encode(itemType,
                    new ItemComponentData(customName, List.of(), null, anvilUseCount, null));
        }
        @Override public boolean equals(Object other) {
            return other instanceof PotDecoration that && itemType == that.itemType
                    && anvilUseCount == that.anvilUseCount
                    && java.util.Objects.equals(customName, that.customName);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(itemType, customName, anvilUseCount);
        }
    }

    public List<PotDecoration> potDecorations() { return potDecorations; }
    public ItemComponentData withPotDecorations(List<PotDecoration> value) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, trim, value);
    }

    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor) {
        this(customName, bannerPatterns, book, anvilUseCount, leatherColor, null, null);
    }

    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor,
            String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks) {
        this(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, null, 0L, 0L);
    }

    /** [TRIAL-GAP] 증폭만 싣는 옛 8인자 생성자(확장 인챈트 없음). */
    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor,
            String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
            Integer ominousBottleAmplifier) {
        this(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier, 0L, 0L);
    }

    /** [ENCHANT-WIDE] 확장 인챈트 워드만 싣는 9인자 생성자(증폭 없음). */
    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor,
            String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
            long enchantmentWord1, long enchantmentWord2) {
        this(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, null,
                enchantmentWord1, enchantmentWord2);
    }

    /** [ENCHANT-WIDE] 증폭 + 확장 인챈트까지 싣는 옛 10인자 생성자(물약·장식 성분 없음). */
    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor,
            String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
            Integer ominousBottleAmplifier, long enchantmentWord1, long enchantmentWord2) {
        this(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, null, null);
    }

    /** [UTILITY] 정본 생성자: 옛 10칸 + 물약 내용물 + 갑옷 장식. */
    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor,
            String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
            Integer ominousBottleAmplifier, long enchantmentWord1, long enchantmentWord2,
            String potionContents, ArmorTrim trim) {
        this(customName, bannerPatterns, book, anvilUseCount, leatherColor, suspiciousStewEffect,
                suspiciousStewDurationMcTicks, ominousBottleAmplifier, enchantmentWord1,
                enchantmentWord2, potionContents, trim, List.of());
    }

    public ItemComponentData(String customName, List<BannerLayer> bannerPatterns,
            BookData book, int anvilUseCount, Integer leatherColor,
            String suspiciousStewEffect, Integer suspiciousStewDurationMcTicks,
            Integer ominousBottleAmplifier, long enchantmentWord1, long enchantmentWord2,
            String potionContents, ArmorTrim trim, List<PotDecoration> potDecorations) {
        if (potDecorations == null || !potDecorations.isEmpty() && potDecorations.size() != 4)
            throw new IllegalArgumentException("pot decorations require exactly four faces");
        this.potDecorations = List.copyOf(potDecorations);
        if (customName != null && (customName.isBlank()
                || customName.length() > NameTagRules.MAX_NAME_UTF16_UNITS
                || !wellFormedUtf16(customName))) {
            throw new IllegalArgumentException("invalid custom name");
        }
        if (bannerPatterns == null || bannerPatterns.size() > MAX_SHIELD_LAYERS
                || bannerPatterns.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid banner pattern list");
        }
        if (anvilUseCount < 0 || anvilUseCount > 30) {
            throw new IllegalArgumentException("invalid anvil use count");
        }
        if (leatherColor != null && (leatherColor < 0 || leatherColor > 0xFFFFFF)) {
            throw new IllegalArgumentException("invalid leather color");
        }
        this.customName = customName;
        this.bannerPatterns = List.copyOf(bannerPatterns);
        this.book = book;
        this.anvilUseCount = anvilUseCount;
        this.leatherColor = leatherColor;
        if ((suspiciousStewEffect == null) != (suspiciousStewDurationMcTicks == null)) {
            throw new IllegalArgumentException("incomplete suspicious stew component");
        }
        if (suspiciousStewEffect != null) {
            SuspiciousStewRules.requireArchaeologyEffect(
                    suspiciousStewEffect, suspiciousStewDurationMcTicks);
        }
        this.suspiciousStewEffect = suspiciousStewEffect;
        this.suspiciousStewDurationMcTicks = suspiciousStewDurationMcTicks;
        if (ominousBottleAmplifier != null && (ominousBottleAmplifier < 0
                || ominousBottleAmplifier > MAX_OMINOUS_BOTTLE_AMPLIFIER)) {
            throw new IllegalArgumentException("invalid ominous bottle amplifier");
        }
        this.ominousBottleAmplifier = ominousBottleAmplifier == null || ominousBottleAmplifier == 0
                ? null : ominousBottleAmplifier;
        if (!WideEnchantments.validWords(0L, enchantmentWord1, enchantmentWord2)) {
            throw new IllegalArgumentException("invalid extended enchantment words");
        }
        this.enchantmentWord1 = enchantmentWord1;
        this.enchantmentWord2 = enchantmentWord2;
        if (potionContents != null
                && !com.gameexpert.engine.effect.PotionCatalog.isKey(potionContents)) {
            throw new IllegalArgumentException("invalid potion contents");
        }
        this.potionContents = potionContents;
        this.trim = trim;
    }

    /** [TRIAL-GAP] 불길한 병 하나의 컴포넌트(증폭 0 이면 {@link #EMPTY}). */
    public static ItemComponentData ominousBottle(int amplifier) {
        return EMPTY.withOminousBottleAmplifier(amplifier);
    }

    public String customName() { return customName; }
    public List<BannerLayer> bannerPatterns() { return bannerPatterns; }
    public BookData book() { return book; }
    public int anvilUseCount() { return anvilUseCount; }
    public Integer leatherColor() { return leatherColor; }
    public String suspiciousStewEffect() { return suspiciousStewEffect; }
    public Integer suspiciousStewDurationMcTicks() { return suspiciousStewDurationMcTicks; }
    /** 저장된 비기본 증폭(1..4) 또는 null. */
    public Integer ominousBottleAmplifierComponent() { return ominousBottleAmplifier; }
    /** 바닐라 {@code OMINOUS_BOTTLE_AMPLIFIER} 값(기본 0). */
    public int ominousBottleAmplifier() {
        return ominousBottleAmplifier == null ? 0 : ominousBottleAmplifier;
    }

    public long enchantmentWord1() { return enchantmentWord1; }
    public long enchantmentWord2() { return enchantmentWord2; }

    /** [ENCHANT-WIDE] ID 16 이상의 인챈트가 하나라도 있는가. */
    public boolean hasExtendedEnchantments() {
        return enchantmentWord1 != 0L || enchantmentWord2 != 0L;
    }

    /** [ENCHANT-WIDE] 스택의 워드 0 마스크와 합친 43종 집합. */
    public WideEnchantments enchantments(long word0) {
        return new WideEnchantments(word0, enchantmentWord1, enchantmentWord2);
    }

    /** [ENCHANT-WIDE] 집합의 워드 1·2 만 성분에 싣는다(워드 0 은 스택 칸이 소유한다). */
    public ItemComponentData withEnchantments(WideEnchantments value) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                value.word1(), value.word2(), potionContents, trim, potDecorations);
    }

    public ItemComponentData withCustomName(String value) {
        return new ItemComponentData(value, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, trim, potDecorations);
    }

    public ItemComponentData withBannerPatterns(List<BannerLayer> value) {
        return new ItemComponentData(customName, value, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, trim, potDecorations);
    }

    public ItemComponentData withBook(BookData value) {
        return new ItemComponentData(customName, bannerPatterns, value, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, trim, potDecorations);
    }

    public ItemComponentData afterAnvilUse() {
        return new ItemComponentData(customName, bannerPatterns, book,
                Math.min(30, anvilUseCount + 1), leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, trim, potDecorations);
    }

    public ItemComponentData withLeatherColor(Integer value) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, value,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, trim, potDecorations);
    }

    public ItemComponentData withSuspiciousStewEffect(String effect, Integer durationMcTicks) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                effect, durationMcTicks, ominousBottleAmplifier, enchantmentWord1, enchantmentWord2,
                potionContents, trim, potDecorations);
    }

    public ItemComponentData withOminousBottleAmplifier(int amplifier) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, amplifier, enchantmentWord1,
                enchantmentWord2, potionContents, trim, potDecorations);
    }

    /** [UTILITY] 물약 내용물 키 또는 null. */
    public String potionContents() { return potionContents; }
    /** [UTILITY] 갑옷 장식 또는 null. */
    public ArmorTrim trim() { return trim; }

    public ItemComponentData withPotionContents(String value) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, value, trim, potDecorations);
    }

    public ItemComponentData withTrim(ArmorTrim value) {
        return new ItemComponentData(customName, bannerPatterns, book, anvilUseCount, leatherColor,
                suspiciousStewEffect, suspiciousStewDurationMcTicks, ominousBottleAmplifier,
                enchantmentWord1, enchantmentWord2, potionContents, value, potDecorations);
    }

    public int priorWorkPenalty() {
        if (anvilUseCount == 0) return 0;
        long penalty = (1L << Math.min(anvilUseCount, 30)) - 1L;
        return penalty > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) penalty;
    }

    public static final class BannerLayer {
        private final LoomRules.Pattern pattern;
        private final int color;
        public BannerLayer(LoomRules.Pattern pattern, int color) {
            if (pattern == null || color < 0 || color > 15) {
                throw new IllegalArgumentException("invalid banner layer");
            }
            this.pattern = pattern;
            this.color = color;
        }
        public LoomRules.Pattern pattern() { return pattern; }
        public int color() { return color; }
    }

    /**
     * [UTILITY] 갑옷 장식 한 벌(바닐라 {@code ArmorTrim(pattern, material)}). 두 키 모두
     * {@link ArmorTrimKeys} 어휘다. 값 동등성으로 비교한다(같은 장식을 다시 입히는 대장장이 결과 없음 판정).
     */
    public static final class ArmorTrim {
        private final String pattern;
        private final String material;

        public ArmorTrim(String pattern, String material) {
            if (!ArmorTrimKeys.isPattern(pattern) || !ArmorTrimKeys.isMaterial(material)) {
                throw new IllegalArgumentException("invalid armor trim");
            }
            this.pattern = pattern;
            this.material = material;
        }

        public String pattern() { return pattern; }
        public String material() { return material; }

        @Override
        public boolean equals(Object other) {
            return other instanceof ArmorTrim that
                    && pattern.equals(that.pattern) && material.equals(that.material);
        }

        @Override
        public int hashCode() {
            return pattern.hashCode() * 31 + material.hashCode();
        }

        /** 성분 문자열 표기 {@code pattern:material}. */
        public String encoded() {
            return pattern + ':' + material;
        }
    }

    public static final class BookData {
        public static final int MAX_PAGES = 100;
        public static final int MAX_PAGE_UTF16_UNITS = 1024;
        public static final int MAX_TITLE_UTF16_UNITS = 32;
        private final String title;
        private final String author;
        private final List<String> pages;

        public BookData(String title, String author, List<String> pages) {
            if (pages == null || pages.isEmpty() || pages.size() > MAX_PAGES
                    || pages.stream().anyMatch(page -> page == null
                            || page.length() > MAX_PAGE_UTF16_UNITS
                            || !wellFormedUtf16(page))
                    || title != null && (title.isBlank() || title.length() > MAX_TITLE_UTF16_UNITS
                            || !wellFormedUtf16(title))
                    || author != null && (author.isBlank()
                            || author.length() > NameTagRules.MAX_NAME_UTF16_UNITS
                            || !wellFormedUtf16(author))) {
                throw new IllegalArgumentException("invalid book component");
            }
            if ((title == null) != (author == null)) {
                throw new IllegalArgumentException("signed book requires title and author together");
            }
            this.title = title;
            this.author = author;
            this.pages = List.copyOf(pages);
        }
        public String title() { return title; }
        public String author() { return author; }
        public List<String> pages() { return pages; }
        public boolean signed() { return title != null; }
    }

    private static boolean wellFormedUtf16(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
