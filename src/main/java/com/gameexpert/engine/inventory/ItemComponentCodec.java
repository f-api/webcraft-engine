package com.gameexpert.engine.inventory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.SuspiciousStewRules;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;

/**
 * Strict current-schema persistence codec for all stack identity components.
 *
 * <p>[ENCHANT-WIDE] One versioned format family. {@code WCIC2} has ten fields. [TRIAL-GAP]
 * {@code WCIC3} appends the ominous bottle amplifier (1..4) as an eleventh field. {@code WCIC4} has
 * twelve fields: the ten WCIC2 fields, the amplifier or {@code ~}, and the canonical lowercase hex of
 * extended enchantment words 2..1 ({@link WideEnchantments#extendedHex()}). The encoder picks the
 * smallest schema that can hold the value, so every WCIC2/WCIC3 string decodes and re-encodes
 * byte-identically and each value has exactly one encoding.
 *
 * <p>[UTILITY] {@code WCIC5} has fourteen fields: the ten WCIC2 fields, the amplifier or {@code ~},
 * the extended enchantment hex or {@code ~}, the potion contents key or {@code ~} and the armour
 * trim {@code pattern:material} or {@code ~}. It is chosen only when a potion or trim component is
 * present, so every WCIC2/3/4 string keeps its exact bytes.
 */
public final class ItemComponentCodec {
    private static final String PREFIX = "WCIC2";
    /**
     * [TRIAL-GAP] 불길한 병 증폭(11번째 칸)을 싣는 스키마. 증폭이 없는 스택은 여전히 WCIC2 로
     * 바이트 그대로 인코딩되므로 기존 행·비교 키가 하나도 바뀌지 않고, 옛 WCIC2 행은 증폭 0 이다.
     */
    private static final String PREFIX_V3 = "WCIC3";
    /**
     * [ENCHANT-WIDE] 확장 인챈트(ID 16..42)를 싣는 스키마. 12칸 = WCIC2 의 10칸 + 증폭(없으면 {@code ~}) +
     * 확장 인챈트 16진(워드 2·1, 정규 소문자, 비어 있으면 안 된다). 확장 인챈트가 있는 스택만 WCIC4 가
     * 되고, 없으면 증폭 유무에 따라 WCIC3·WCIC2 를 바이트 그대로 쓴다 — 한 값에 인코딩은 하나뿐이다.
     */
    private static final String PREFIX_V4 = "WCIC4";
    /** [UTILITY] 물약 내용물·갑옷 장식을 싣는 14칸 스키마. 두 성분이 없으면 쓰지 않는다. */
    private static final String PREFIX_V5 = "WCIC5";
    private static final String PREFIX_V6 = "WCIC6";
    private static final String NULL = "~";
    private ItemComponentCodec() {}

    public static String encode(short itemType, ItemComponentData data) {
        if (data == null || data == ItemComponentData.EMPTY
                || data.customName() == null && data.bannerPatterns().isEmpty()
                        && data.book() == null && data.anvilUseCount() == 0
                        && data.leatherColor() == null
                        && data.suspiciousStewEffect() == null
                        && data.suspiciousStewDurationMcTicks() == null
                        && data.ominousBottleAmplifierComponent() == null
                        && !data.hasExtendedEnchantments()
                        && data.potionContents() == null && data.trim() == null && data.potDecorations().isEmpty()) return null;
        validateForItem(itemType, data);
        StringBuilder patterns = new StringBuilder();
        for (ItemComponentData.BannerLayer layer : data.bannerPatterns()) {
            if (!patterns.isEmpty()) patterns.append(',');
            patterns.append(layer.pattern().wireName()).append(':').append(layer.color());
        }
        ItemComponentData.BookData book = data.book();
        String title = book == null ? NULL : encodeText(book.title());
        String author = book == null ? NULL : encodeText(book.author());
        String pages = NULL;
        if (book != null) {
            StringBuilder encodedPages = new StringBuilder();
            for (String page : book.pages()) {
                if (!encodedPages.isEmpty()) encodedPages.append(',');
                encodedPages.append(encodeText(page));
            }
            pages = encodedPages.toString();
        }
        Integer amplifier = data.ominousBottleAmplifierComponent();
        boolean extended = data.hasExtendedEnchantments();
        boolean v6 = !data.potDecorations().isEmpty();
        boolean v5 = v6 || data.potionContents() != null || data.trim() != null;
        String prefix = v6 ? PREFIX_V6 : v5 ? PREFIX_V5 : extended ? PREFIX_V4 : amplifier == null ? PREFIX : PREFIX_V3;
        return prefix + '|'
                + data.anvilUseCount() + '|'
                + encodeText(data.customName()) + '|'
                + patterns + '|' + title + '|' + author + '|' + pages + '|'
                + (data.leatherColor() == null ? NULL : data.leatherColor()) + '|'
                + (data.suspiciousStewEffect() == null
                        ? NULL : data.suspiciousStewEffect()) + '|'
                + (data.suspiciousStewDurationMcTicks() == null
                        ? NULL : data.suspiciousStewDurationMcTicks())
                + (v5
                        ? "|" + (amplifier == null ? NULL : amplifier) + "|"
                                + (extended ? data.enchantments(0L).extendedHex() : NULL) + "|"
                                + (data.potionContents() == null ? NULL : data.potionContents()) + "|"
                                + (data.trim() == null ? NULL : data.trim().encoded())
                        : extended
                        ? "|" + (amplifier == null ? NULL : amplifier) + "|"
                                + data.enchantments(0L).extendedHex()
                        : amplifier == null ? "" : "|" + amplifier)
                + (v6 ? "|" + encodePotDecorations(data.potDecorations()) : "");
    }

    /**
     * [ENCHANT-WIDE] 검증을 이미 통과한 성분 문자열에서 확장 인챈트 16진 필드만 꺼낸다. {@code WCIC4}
     * 가 아니면 {@code null}. 전투·방어처럼 매 틱 읽는 경로가 책·배너 텍스트를 다시 풀지 않게 한다.
     */
    public static String extendedEnchantmentHex(String encoded) {
        if (encoded == null) return null;
        if (encoded.startsWith(PREFIX_V5 + '|') || encoded.startsWith(PREFIX_V6 + '|')) {
            // [UTILITY] WCIC5 의 12번째 칸(인덱스 11)이 확장 인챈트 16진 또는 ~ 다.
            String[] fields = encoded.split("\\|", -1);
            return (fields.length == 14 || fields.length == 15) && !NULL.equals(fields[11]) ? fields[11] : null;
        }
        if (!encoded.startsWith(PREFIX_V4 + '|')) return null;
        return encoded.substring(encoded.lastIndexOf('|') + 1);
    }

    /**
     * [GLINT] 스택 칸(워드 0 + 성분 문자열)의 인챈트 와이어 값({@link WideEnchantments#wireValue()}), 인챈트가
     * 없으면 {@code null}. 드랍 아이템·선반 칸의 광택 필드가 쓴다.
     */
    public static Object enchantmentWireOrNull(long word0, String encoded) {
        String extended = extendedEnchantmentHex(encoded);
        if (word0 == 0L && extended == null) return null;
        return (extended == null ? WideEnchantments.legacy(word0)
                : WideEnchantments.withExtendedHex(word0, extended)).wireValue();
    }

    /**
     * [ENCHANT-WIDE] 성분 문자열의 확장 인챈트 워드만 {@code enchantments} 의 워드 1·2 로 바꾼 새
     * 문자열. 다른 성분(이름·배너·책·가죽 색·불길한 병 증폭 등)은 그대로 둔다. 워드 0 은 호출자가 스택 칸에 쓴다.
     */
    public static String withEnchantments(short itemType, String encoded,
            WideEnchantments enchantments) {
        ItemComponentData current = decode(itemType, encoded);
        if (current.enchantmentWord1() == enchantments.word1()
                && current.enchantmentWord2() == enchantments.word2()) {
            return encoded;
        }
        return encode(itemType, current.withEnchantments(enchantments));
    }

    public static ItemComponentData decode(short itemType, String encoded) {
        if (encoded == null) return ItemComponentData.EMPTY;
        String[] fields = encoded.split("\\|", -1);
        boolean v3 = fields.length == 11 && PREFIX_V3.equals(fields[0]);
        boolean v4 = fields.length == 12 && PREFIX_V4.equals(fields[0]);
        boolean v6 = fields.length == 15 && PREFIX_V6.equals(fields[0]);
        if (v6 && encoded.length() > 4096) throw new IllegalArgumentException("pot components too large");
        boolean v5 = v6 || fields.length == 14 && PREFIX_V5.equals(fields[0]);
        if (!v3 && !v4 && !v5 && (fields.length != 10 || !PREFIX.equals(fields[0]))) {
            throw new IllegalArgumentException("invalid item component schema");
        }
        WideEnchantments extended;
        boolean hasExtendedField = v4 || v5 && !NULL.equals(fields[11]);
        try {
            // WCIC4 는 확장 인챈트가 반드시 있다(없으면 WCIC2/3 이 정규형이다). WCIC5 는 ~ 로 비울 수 있다.
            extended = hasExtendedField
                    ? WideEnchantments.withExtendedHex(0L, fields[11]) : WideEnchantments.EMPTY;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid extended enchantment component", invalid);
        }
        if (hasExtendedField && !extended.hasExtended()) {
            throw new IllegalArgumentException("invalid extended enchantment component");
        }
        String potionContents = null;
        ItemComponentData.ArmorTrim trim = null;
        if (v5) {
            potionContents = NULL.equals(fields[12]) ? null : fields[12];
            if (!NULL.equals(fields[13])) {
                int colon = fields[13].indexOf(':');
                if (colon <= 0) throw new IllegalArgumentException("invalid armor trim component");
                trim = new ItemComponentData.ArmorTrim(
                        fields[13].substring(0, colon), fields[13].substring(colon + 1));
            }
            // WCIC5 는 물약·장식 성분이 있을 때만 정규형이다.
            if (!v6 && potionContents == null && trim == null) {
                throw new IllegalArgumentException("invalid item component schema");
            }
        }
        int anvilUse;
        try { anvilUse = Integer.parseInt(fields[1]); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid anvil use count", invalid);
        }
        ArrayList<ItemComponentData.BannerLayer> patterns = new ArrayList<>();
        if (!fields[3].isEmpty()) {
            for (String layer : fields[3].split(",", -1)) {
                int colon = layer.lastIndexOf(':');
                if (colon <= 0 || colon == layer.length() - 1) {
                    throw new IllegalArgumentException("invalid banner layer");
                }
                LoomRules.Pattern pattern;
                int color;
                try {
                    pattern = LoomRules.Pattern.valueOf(
                            layer.substring(0, colon).toUpperCase(java.util.Locale.ROOT));
                    color = Integer.parseInt(layer.substring(colon + 1));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException("invalid banner layer", invalid);
                }
                patterns.add(new ItemComponentData.BannerLayer(pattern, color));
            }
        }
        boolean noBook = NULL.equals(fields[4]) && NULL.equals(fields[5]) && NULL.equals(fields[6]);
        ItemComponentData.BookData book = null;
        if (!noBook) {
            if (NULL.equals(fields[6])) throw new IllegalArgumentException("invalid book fields");
            List<String> pages = fields[6].isEmpty() ? List.of("")
                    : java.util.Arrays.stream(fields[6].split(",", -1))
                            .map(ItemComponentCodec::decodeText)
                            .toList();
            book = new ItemComponentData.BookData(decodeText(fields[4]), decodeText(fields[5]), pages);
        }
        Integer leatherColor;
        try { leatherColor = NULL.equals(fields[7]) ? null : Integer.valueOf(fields[7]); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid leather color", invalid);
        }
        String stewEffect = NULL.equals(fields[8]) ? null : fields[8];
        Integer stewDuration;
        try { stewDuration = NULL.equals(fields[9]) ? null : Integer.valueOf(fields[9]); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid suspicious stew duration", invalid);
        }
        Integer amplifier = null;
        if (v3 || (v4 || v5) && !NULL.equals(fields[10])) {
            try { amplifier = Integer.valueOf(fields[10]); }
            catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("invalid ominous bottle amplifier", invalid);
            }
            // WCIC3 는 비기본 증폭 1..4 만 싣는다. 0 을 실은 행은 정규형이 아니다.
            if (amplifier < 1 || amplifier > ItemComponentData.MAX_OMINOUS_BOTTLE_AMPLIFIER) {
                throw new IllegalArgumentException("invalid ominous bottle amplifier");
            }
        }
        ItemComponentData data = new ItemComponentData(
                decodeText(fields[2]), patterns, book, anvilUse, leatherColor,
                stewEffect, stewDuration, amplifier, extended.word1(), extended.word2(),
                potionContents, trim, v6 ? decodePotDecorations(fields[14]) : List.of());
        validateForItem(itemType, data);
        return data;
    }

    /**
     * [SHIELD-PATTERN] 방패 무늬: 맨 앞은 바닐라 {@code BASE_COLOR} 인 {@code BASE} 층 하나, 그 뒤는
     * 현수막에서 옮긴 무늬(BASE 아님) 여섯 층까지다.
     */
    private static void validateShieldLayers(java.util.List<ItemComponentData.BannerLayer> layers) {
        if (layers.size() > ItemComponentData.MAX_SHIELD_LAYERS
                || layers.get(0).pattern() != LoomRules.Pattern.BASE) {
            throw new IllegalArgumentException("invalid shield patterns");
        }
        for (int i = 1; i < layers.size(); i++) {
            if (layers.get(i).pattern() == LoomRules.Pattern.BASE) {
                throw new IllegalArgumentException("invalid shield patterns");
            }
        }
    }

    public static void validateForItem(short itemType, ItemComponentData data) {
        if (data == null) throw new IllegalArgumentException("components are required");
        int id = Short.toUnsignedInt(itemType);
        if (!data.potDecorations().isEmpty() && !Blocks.isDecoratedPot(id))
            throw new IllegalArgumentException("pot decorations require decorated pot");
        if (!data.bannerPatterns().isEmpty()) {
            if (itemType == PlayerInventory.SHIELD) {
                validateShieldLayers(data.bannerPatterns());
            } else if (!Blocks.isBanner(id)) {
                throw new IllegalArgumentException("banner patterns require a banner");
            } else if (data.bannerPatterns().size() > ItemComponentData.MAX_BANNER_LAYERS) {
                throw new IllegalArgumentException("too many banner layers");
            }
        }
        if (data.book() != null) {
            boolean valid = itemType == PlayerInventory.WRITABLE_BOOK && !data.book().signed()
                    || itemType == PlayerInventory.WRITTEN_BOOK && data.book().signed();
            if (!valid) throw new IllegalArgumentException("book component does not match item");
        }
        if (data.hasExtendedEnchantments()
                && !EnchantmentRules.canHoldEnchantments(itemType)) {
            throw new IllegalArgumentException("extended enchantments require an enchantable item");
        }
        if (data.leatherColor() != null && !isDyeableLeather(itemType)) {
            throw new IllegalArgumentException("leather color requires dyeable armor");
        }
        if (data.ominousBottleAmplifierComponent() != null
                && itemType != PlayerInventory.OMINOUS_BOTTLE) {
            throw new IllegalArgumentException("ominous bottle amplifier requires an ominous bottle");
        }
        // [UTILITY] 물약 내용물은 범용 물약 세 형태에만, 갑옷 장식은 장식 가능한 방어구에만 붙는다.
        if (data.potionContents() != null && !isContentsPotion(itemType)) {
            throw new IllegalArgumentException("potion contents require a generic potion item");
        }
        if (data.trim() != null && !ArmorTrimKeys.isTrimmableArmor(itemType)) {
            throw new IllegalArgumentException("armor trim requires trimmable armor");
        }
        if (data.suspiciousStewEffect() != null) {
            if (!PlayerInventory.isSuspiciousStew(itemType)) {
                throw new IllegalArgumentException("suspicious stew effect requires suspicious stew");
            }
            SuspiciousStewRules.requireArchaeologyEffect(
                    data.suspiciousStewEffect(), data.suspiciousStewDurationMcTicks());
        }
    }

    /** [UTILITY] 물약 내용물 성분을 싣는 범용 물약 세 형태인가. */
    static boolean isContentsPotion(short itemType) {
        return itemType == PlayerInventory.CONTENTS_POTION
                || itemType == PlayerInventory.CONTENTS_SPLASH_POTION
                || itemType == PlayerInventory.CONTENTS_LINGERING_POTION;
    }

    static boolean isDyeableLeather(short itemType) {
        return itemType >= PlayerInventory.LEATHER_HELMET
                && itemType <= PlayerInventory.LEATHER_BOOTS
                || itemType == PlayerInventory.LEATHER_HORSE_ARMOR
                || itemType == PlayerInventory.WOLF_ARMOR;
    }

    private static String encodePotDecorations(List<ItemComponentData.PotDecoration> faces) {
        return faces.stream().map(face -> Short.toUnsignedInt(face.itemType()) + ":"
                + face.anvilUseCount() + ":" + encodeText(face.customName()))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static List<ItemComponentData.PotDecoration> decodePotDecorations(String encoded) {
        String[] faces = encoded.split(",", -1);
        if (faces.length != 4) throw new IllegalArgumentException("pot decorations require four faces");
        List<ItemComponentData.PotDecoration> result = new ArrayList<>();
        for (String face : faces) {
            String[] fields = face.split(":", -1);
            if (fields.length != 3) throw new IllegalArgumentException("invalid pot decoration");
            int id = Integer.parseInt(fields[0]), uses = Integer.parseInt(fields[1]);
            if (id < 0 || id > 32767 || !Integer.toString(id).equals(fields[0])
                    || !Integer.toString(uses).equals(fields[1]))
                throw new IllegalArgumentException("noncanonical pot decoration");
            String name = decodeText(fields[2]);
            if (!encodeText(name).equals(fields[2])) throw new IllegalArgumentException("noncanonical pot name");
            result.add(new ItemComponentData.PotDecoration((short) id, name, uses));
        }
        return List.copyOf(result);
    }

    private static String encodeText(String value) {
        if (value == null) return NULL;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        if (NULL.equals(value)) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException invalid) {
            throw new IllegalArgumentException("invalid item component text", invalid);
        }
    }
}
