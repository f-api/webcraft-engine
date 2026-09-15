package com.gameexpert.engine.mob;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict, versioned identity component stored on bucketed Axolotls, Tropical Fish and Tadpoles. */
public final class BucketMobPayloadCodec {
    private static final String PREFIX = "WCMB1";
    private static final String SULFUR_PREFIX = "WCSC1";
    private static final int MAX_TADPOLE_AGE_MC_TICKS = 24_000;
    private static final int MAX_CUSTOM_NAME_UTF16_UNITS = 256;

    public record Payload(MobType type, String variant, int tadpoleAgeMcTicks, String customName) {}
    public record SulfurPayload(short bodyItem, String customName) {}

    private BucketMobPayloadCodec() {}

    public static boolean requiresPayload(short itemType) {
        return itemType == com.gameexpert.engine.inventory.PlayerInventory.AXOLOTL_BUCKET
                || itemType == com.gameexpert.engine.inventory.PlayerInventory.TROPICAL_FISH_BUCKET
                || itemType == com.gameexpert.engine.inventory.PlayerInventory.TADPOLE_BUCKET
                || itemType == com.gameexpert.engine.inventory.PlayerInventory.SULFUR_CUBE_BUCKET;
    }

    public static boolean validForItem(short itemType, String encoded) {
        if (!requiresPayload(itemType)) return encoded == null;
        if (encoded == null) return false;
        try {
            if (itemType == com.gameexpert.engine.inventory.PlayerInventory.SULFUR_CUBE_BUCKET) {
                decodeSulfur(encoded);
            } else {
                decode(encoded, typeForItem(itemType));
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String encode(MobType type, String variant, int tadpoleAgeMcTicks,
            String customName) {
        Payload payload = validated(new Payload(type, variant, tadpoleAgeMcTicks, customName));
        return PREFIX + '|' + wireType(payload.type()) + '|' + encodeURIComponent(payload.variant())
                + '|' + payload.tadpoleAgeMcTicks() + '|'
                + encodeURIComponent(payload.customName());
    }

    public static Payload decode(String encoded, MobType expectedType) {
        if (encoded == null) throw new IllegalArgumentException("bucket mob payload is required");
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 5 || !PREFIX.equals(fields[0])
                || !wireType(expectedType).equals(fields[1])) {
            throw new IllegalArgumentException("invalid bucket mob payload version or type");
        }
        if (!fields[3].matches("(?:0|[1-9][0-9]{0,5})")) {
            throw new IllegalArgumentException("invalid bucket mob age");
        }
        int age;
        try {
            age = Integer.parseInt(fields[3]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid bucket mob age", exception);
        }
        String variant = decodeURIComponent(fields[2]);
        String customName = decodeURIComponent(fields[4]);
        return validated(new Payload(expectedType, variant.isEmpty() ? null : variant, age,
                customName.isEmpty() ? null : customName));
    }

    public static String encodeSulfur(short bodyItem, String customName) {
        SulfurPayload payload = validateSulfur(
                new SulfurPayload(bodyItem, customName));
        return SULFUR_PREFIX + '|' + Short.toUnsignedInt(payload.bodyItem()) + '|'
                + encodeURIComponent(payload.customName());
    }

    public static SulfurPayload decodeSulfur(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("sulfur cube payload is required");
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 3 || !SULFUR_PREFIX.equals(fields[0])
                || !fields[1].matches("(?:0|[1-9][0-9]{0,4})")) {
            throw new IllegalArgumentException("invalid sulfur cube payload");
        }
        try {
            int body = Integer.parseInt(fields[1]);
            String name = decodeURIComponent(fields[2]);
            return validateSulfur(new SulfurPayload((short) body,
                    name.isEmpty() ? null : name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid sulfur cube payload", exception);
        }
    }

    public static MobType typeForItem(short itemType) {
        if (itemType == com.gameexpert.engine.inventory.PlayerInventory.AXOLOTL_BUCKET) {
            return MobType.AXOLOTL;
        }
        if (itemType == com.gameexpert.engine.inventory.PlayerInventory.TROPICAL_FISH_BUCKET) {
            return MobType.TROPICAL_FISH;
        }
        if (itemType == com.gameexpert.engine.inventory.PlayerInventory.TADPOLE_BUCKET) {
            return MobType.TADPOLE;
        }
        if (itemType == com.gameexpert.engine.inventory.PlayerInventory.SULFUR_CUBE_BUCKET) {
            return MobType.SULFUR_CUBE;
        }
        throw new IllegalArgumentException("item does not carry bucket mob payload");
    }

    private static Payload validated(Payload payload) {
        if (payload == null || payload.type() == null
                || payload.type() != MobType.AXOLOTL
                        && payload.type() != MobType.TROPICAL_FISH
                        && payload.type() != MobType.TADPOLE
                || !payload.type().acceptsVariant(payload.variant())
                || payload.tadpoleAgeMcTicks() < 0
                || payload.tadpoleAgeMcTicks() > MAX_TADPOLE_AGE_MC_TICKS
                || payload.type() != MobType.TADPOLE && payload.tadpoleAgeMcTicks() != 0
                || payload.customName() != null && (payload.customName().isEmpty()
                        || payload.customName().length() > MAX_CUSTOM_NAME_UTF16_UNITS
                        || !wellFormedUtf16(payload.customName()))) {
            throw new IllegalArgumentException("invalid bucket mob facts");
        }
        return payload;
    }

    private static SulfurPayload validateSulfur(SulfurPayload payload) {
        int body = payload == null ? 0 : Short.toUnsignedInt(payload.bodyItem());
        if (payload == null || body > com.gameexpert.terrain.Blocks.PROTOCOL_ID_HIGH_WATER
                || body != 0 && SulfurCubeRules.archetypeFor(body) == null
                || payload.customName() != null && (payload.customName().isEmpty()
                        || payload.customName().length() > MAX_CUSTOM_NAME_UTF16_UNITS
                        || !wellFormedUtf16(payload.customName()))) {
            throw new IllegalArgumentException("invalid sulfur cube bucket facts");
        }
        return payload;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(unit)) {
                return false;
            }
        }
        return true;
    }

    private static String wireType(MobType type) {
        return switch (type) {
            case AXOLOTL -> "axolotl";
            case TROPICAL_FISH -> "tropical_fish";
            case TADPOLE -> "tadpole";
            default -> throw new IllegalArgumentException("mob type has no bucket payload");
        };
    }

    /** ECMAScript encodeURIComponent allow-list, with UTF-8 bytes and uppercase percent hex. */
    private static String encodeURIComponent(String value) {
        if (value == null) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte raw : bytes) {
            int valueByte = raw & 0xff;
            if (isUriComponentByte(valueByte)) {
                encoded.append((char) valueByte);
            } else {
                encoded.append('%').append(hex[valueByte >>> 4]).append(hex[valueByte & 15]);
            }
        }
        return encoded.toString();
    }

    private static boolean isUriComponentByte(int value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '-' || value == '_'
                || value == '.' || value == '!' || value == '~' || value == '*'
                || value == '\'' || value == '(' || value == ')';
    }

    private static String decodeURIComponent(String encoded) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(encoded.length());
        for (int index = 0; index < encoded.length();) {
            char current = encoded.charAt(index);
            if (current == '%') {
                if (index + 2 >= encoded.length()) {
                    throw new IllegalArgumentException("invalid bucket mob payload encoding");
                }
                int high = Character.digit(encoded.charAt(index + 1), 16);
                int low = Character.digit(encoded.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("invalid bucket mob payload encoding");
                }
                bytes.write((high << 4) | low);
                index += 3;
            } else {
                if (current > 0x7f || !isUriComponentByte(current)) {
                    throw new IllegalArgumentException("invalid bucket mob payload encoding");
                }
                bytes.write(current);
                index++;
            }
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid bucket mob payload encoding", exception);
        }
    }
}
