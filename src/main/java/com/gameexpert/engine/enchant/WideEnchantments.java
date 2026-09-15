package com.gameexpert.engine.enchant;

/**
 * [ENCHANT-WIDE] 바닐라 26.3 인챈트 43종 전부를 담는 넓은 인챈트 집합.
 *
 * <p><b>레이아웃</b>: 인챈트 ID {@code id} 의 레벨은 3비트 칸 하나이며, 칸은 48비트 워드 세 개에
 * 16칸씩 들어간다({@code word = id / 16}, {@code shift = (id % 16) * 3}). 워드 0 은 역사적
 * 48비트 마스크와 <b>비트 단위로 같다</b> — 옛 마스크는 워드 0 만 채운 값으로 1:1 대응한다.
 * 워드 하나가 48비트라 세 언어(Java {@code long}, TypeScript 안전 정수, Rust {@code u64}) 어디서도
 * 정밀도 손실 없이 다룰 수 있고, 16진수 12자리와 정확히 맞는다.
 *
 * <p><b>저장 규약(Java)</b>: 워드 0 은 기존 {@code long enchantments} 칸(DB 열·스택 필드)에 그대로
 * 남고, 워드 1·2 는 스택 성분 {@code ItemComponentData} 의 확장 인챈트 필드로 간다. 확장이 없는
 * 스택의 성분 문자열은 이전과 바이트 단위로 같다.
 *
 * <p><b>와이어 규약</b>: {@code enchantments} 는 확장이 없으면 지금과 같은 정수(워드 0)이고, 확장
 * 인챈트가 하나라도 있으면 전체 집합을 선행 0 없는 소문자 16진 문자열로 싣는다. 문자열은 언제나
 * 13자 이상이므로(워드 1·2 중 하나가 0 이 아님) 같은 집합이 두 모양으로 표현되는 일은 없다.
 */
public record WideEnchantments(long word0, long word1, long word2) {

    /** 워드 하나에 담기는 인챈트 칸 수. */
    public static final int WORD_SLOTS = 16;
    /** 워드 하나의 비트 폭(16칸 × 3비트). */
    public static final int WORD_BITS = WORD_SLOTS * EnchantmentRules.ENCHANT_BITS;
    /** 워드 0·1 의 상한(48비트). */
    public static final long FULL_WORD_LIMIT = (1L << WORD_BITS) - 1L;
    /** 워드 2 의 상한. ID 32..42 의 11칸만 쓰므로 그 위 비트는 세워질 수 없다. */
    public static final long LAST_WORD_LIMIT =
            (1L << ((EnchantmentRules.ENCHANTMENT_COUNT - 2 * WORD_SLOTS)
                    * EnchantmentRules.ENCHANT_BITS)) - 1L;
    /** 16진 워드 하나의 자릿수. */
    private static final int HEX_DIGITS_PER_WORD = WORD_BITS / 4;

    public static final WideEnchantments EMPTY = new WideEnchantments(0L, 0L, 0L);

    public WideEnchantments {
        if (word0 < 0L || word0 > FULL_WORD_LIMIT || word1 < 0L || word1 > FULL_WORD_LIMIT
                || word2 < 0L || word2 > LAST_WORD_LIMIT) {
            throw new IllegalArgumentException("enchantment words are outside the 43-slot layout");
        }
    }

    /** 옛 48비트 마스크 하나로 된 집합. */
    public static WideEnchantments legacy(long mask) {
        return new WideEnchantments(mask, 0L, 0L);
    }

    /** 레이아웃 안에 들어가는 세 워드인가(예외 없이 판정). */
    public static boolean validWords(long word0, long word1, long word2) {
        return word0 >= 0L && word0 <= FULL_WORD_LIMIT && word1 >= 0L && word1 <= FULL_WORD_LIMIT
                && word2 >= 0L && word2 <= LAST_WORD_LIMIT;
    }

    public long word(int index) {
        return switch (index) {
            case 0 -> word0;
            case 1 -> word1;
            case 2 -> word2;
            default -> throw new IndexOutOfBoundsException("enchantment word " + index);
        };
    }

    /** 인챈트 레벨. 범위 밖 ID 는 0. */
    public int level(int enchantId) {
        if (enchantId < 0 || enchantId >= EnchantmentRules.ENCHANTMENT_COUNT) return 0;
        long word = word(enchantId / WORD_SLOTS);
        return (int) ((word >>> ((enchantId % WORD_SLOTS) * EnchantmentRules.ENCHANT_BITS))
                & EnchantmentRules.ENCHANT_LEVEL_MASK);
    }

    /** 레벨을 써 넣은 새 집합. 레벨은 0..7 로 잘린다. 범위 밖 ID 는 그대로 돌려준다. */
    public WideEnchantments with(int enchantId, int level) {
        if (enchantId < 0 || enchantId >= EnchantmentRules.ENCHANTMENT_COUNT) return this;
        int clamped = level < 0 ? 0 : Math.min(level, EnchantmentRules.ENCHANT_LEVEL_MASK);
        int index = enchantId / WORD_SLOTS;
        int shift = (enchantId % WORD_SLOTS) * EnchantmentRules.ENCHANT_BITS;
        long updated = (word(index) & ~((long) EnchantmentRules.ENCHANT_LEVEL_MASK << shift))
                | ((long) clamped << shift);
        return switch (index) {
            case 0 -> new WideEnchantments(updated, word1, word2);
            case 1 -> new WideEnchantments(word0, updated, word2);
            default -> new WideEnchantments(word0, word1, updated);
        };
    }

    public boolean isEmpty() {
        return word0 == 0L && word1 == 0L && word2 == 0L;
    }

    /** 워드 0 밖(ID 16 이상)에 인챈트가 있는가. */
    public boolean hasExtended() {
        return word1 != 0L || word2 != 0L;
    }

    /** 칸별 OR. */
    public WideEnchantments union(WideEnchantments other) {
        return new WideEnchantments(word0 | other.word0, word1 | other.word1, word2 | other.word2);
    }

    /** 칸별 AND. */
    public WideEnchantments intersect(WideEnchantments other) {
        return new WideEnchantments(word0 & other.word0, word1 & other.word1, word2 & other.word2);
    }

    /** 칸별 차집합(this 에서 other 의 비트를 지운다). */
    public WideEnchantments without(WideEnchantments other) {
        return new WideEnchantments(word0 & ~other.word0, word1 & ~other.word1,
                word2 & ~other.word2);
    }

    /** 걸린 인챈트 수. */
    public int count() {
        int count = 0;
        for (int id = 0; id < EnchantmentRules.ENCHANTMENT_COUNT; id++) {
            if (level(id) > 0) count++;
        }
        return count;
    }

    /** 전체 집합의 정규 16진 표기(선행 0 없음, 빈 집합은 "0"). */
    public String toHex() {
        if (word2 != 0L) {
            return Long.toHexString(word2) + pad(word1) + pad(word0);
        }
        if (word1 != 0L) return Long.toHexString(word1) + pad(word0);
        return Long.toHexString(word0);
    }

    /**
     * 워드 1·2 만의 정규 16진 표기. 확장이 없으면 {@code null}. 스택 성분 문자열이 이 값을 싣는다.
     */
    public String extendedHex() {
        if (!hasExtended()) return null;
        return word2 != 0L ? Long.toHexString(word2) + pad(word1) : Long.toHexString(word1);
    }

    /** 성분 문자열의 확장 16진 표기를 워드 1·2 로 되읽는다. 비정규 표기는 거부한다. */
    public static WideEnchantments withExtendedHex(long word0, String extendedHex) {
        if (extendedHex == null) return legacy(word0);
        long[] words = parseCanonicalHex(extendedHex, 2);
        if (words[0] == 0L && words[1] == 0L) {
            throw new IllegalArgumentException("empty extended enchantment component");
        }
        return new WideEnchantments(word0, words[0], words[1]);
    }

    /** 와이어 값: 확장이 없으면 {@link Long}, 있으면 전체 16진 {@link String}. */
    public Object wireValue() {
        return hasExtended() ? toHex() : Long.valueOf(word0);
    }

    /** 와이어 값을 되읽는다. 정수는 옛 48비트 마스크, 문자열은 확장이 있는 정규 16진 표기다. */
    public static WideEnchantments fromWire(Object value) {
        if (value instanceof Number number) {
            if (!(value instanceof Long || value instanceof Integer || value instanceof Short
                    || value instanceof Byte)) {
                throw new IllegalArgumentException("enchantment wire number must be integral");
            }
            return legacy(number.longValue());
        }
        if (value instanceof String text) {
            long[] words = parseCanonicalHex(text, 3);
            WideEnchantments parsed = new WideEnchantments(words[0], words[1], words[2]);
            if (!parsed.hasExtended()) {
                throw new IllegalArgumentException("48-bit enchantment sets must use the integer form");
            }
            return parsed;
        }
        throw new IllegalArgumentException("enchantment wire value must be an integer or a string");
    }

    /** 선행 0 없는 소문자 16진 표기({@link #toHex()} 의 역). 빈 집합은 {@code "0"}. */
    public static WideEnchantments fromHex(String text) {
        if ("0".equals(text)) return EMPTY;
        long[] words = parseCanonicalHex(text, 3);
        return new WideEnchantments(words[0], words[1], words[2]);
    }

    private static String pad(long word) {
        String hex = Long.toHexString(word);
        return "0".repeat(HEX_DIGITS_PER_WORD - hex.length()) + hex;
    }

    /** 선행 0 없는 소문자 16진 문자열을 최하위 워드부터 {@code wordCount} 개로 쪼갠다. */
    private static long[] parseCanonicalHex(String text, int wordCount) {
        if (text.isEmpty() || text.length() > wordCount * HEX_DIGITS_PER_WORD
                || text.charAt(0) == '0') {
            throw new IllegalArgumentException("non-canonical enchantment hex");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
                throw new IllegalArgumentException("non-canonical enchantment hex");
            }
        }
        long[] words = new long[wordCount];
        int end = text.length();
        for (int index = 0; index < wordCount && end > 0; index++) {
            int start = Math.max(0, end - HEX_DIGITS_PER_WORD);
            words[index] = Long.parseLong(text.substring(start, end), 16);
            end = start;
        }
        // 마지막 워드(ID 32..42)는 11칸뿐이다. 그 위 비트는 알 수 없는 인챈트다.
        if (words[wordCount - 1] > LAST_WORD_LIMIT) {
            throw new IllegalArgumentException("enchantment hex sets an unknown slot");
        }
        return words;
    }

    @Override
    public String toString() {
        return "WideEnchantments[" + toHex() + "]";
    }
}
