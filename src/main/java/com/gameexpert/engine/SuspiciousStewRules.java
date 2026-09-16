package com.gameexpert.engine;

import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * [COOKING] 수상한 스튜의 꽃 → 효과 매핑입니다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneSuspiciousStew.ts} 이며 두 소스는
 * 파리티 게이트로 묶여 있습니다. 근거는 {@code docs/research/mc-food-cooking.md} §4 입니다.
 *
 * <p><b>지속시간 단위</b>: 바닐라 표는 20 TPS 게임 틱이고 이 저장소의 효과 목록은 그대로
 * <b>MC 틱</b>으로 보관하므로({@code StatusEffects.MC_TICKS_PER_SERVER_TICK}=2) 바닐라 틱수를
 * 변환 없이 쓸 수 있습니다. 서버 틱으로 나눠 담았다면 민들레의 7 틱이 3.5 가 되어 반올림
 * 오차가 났을 자리입니다.
 *
 * <p><b>이 저장소가 만들 수 있는 스튜는 두 종뿐</b>입니다 — 작은 꽃이 양귀비
 * ({@link Blocks#FLOWER_RED}) · 민들레({@link Blocks#FLOWER_YELLOW}) 둘만 등록돼 있기
 * 때문입니다. 나머지 꽃은 지형 배치물이라 지형 레인 없이 세울 수 없습니다. 그래도 바닐라
 * 표 전량을 {@link #vanillaEffectTicks} 에 남겨 둡니다 — 나중에 꽃이 등록될 때 다시 조사하지
 * 않도록 하기 위해서고, 그 꽃들의 효과(점프 강화·재생·위더·멀미)는 아직 이 저장소에
 * 없다는 사실도 함께 기록합니다.
 */
public final class SuspiciousStewRules {

    public static final String DESERT_WELL_ARCHAEOLOGY_TABLE =
            "minecraft:archaeology/desert_well";

    private static final long LEGACY_MULTIPLIER = 0x5DEECE66DL;
    private static final long LEGACY_ADDEND = 0xBL;
    private static final long LEGACY_MASK = (1L << 48) - 1;

    /** Pinned {@code set_stew_effect} entry order from desert-well archaeology loot. */
    private static final EffectRange[] DESERT_WELL_EFFECTS = {
        new EffectRange("night_vision", StatusEffect.NIGHT_VISION, 7, 10),
        new EffectRange("jump_boost", StatusEffect.JUMP_BOOST, 7, 10),
        new EffectRange("weakness", StatusEffect.WEAKNESS, 6, 8),
        new EffectRange("blindness", StatusEffect.BLINDNESS, 5, 7),
        new EffectRange("poison", StatusEffect.POISON, 10, 20),
        new EffectRange("saturation", StatusEffect.SATURATION, 7, 10),
    };

    private record EffectRange(String protocolName, StatusEffect effect,
            int minSeconds, int maxSeconds) {
    }

    public record StewEffect(StatusEffect effect, int durationMcTicks) {
        public StewEffect {
            if (effect == null || durationMcTicks <= 0) {
                throw new IllegalArgumentException("invalid suspicious stew effect");
            }
        }
    }

    /** One exact desert-well loot result, including its persistence/merge identity. */
    public record DesertWellLoot(PlayerInventory.StackSnapshot stack, String resultIdentity) {
        public DesertWellLoot {
            if (stack == null || stack.isEmpty() || resultIdentity == null
                    || resultIdentity.isBlank()) {
                throw new IllegalArgumentException("invalid desert well loot result");
            }
        }
    }

    /** 양귀비 스튜: 야간 투시 100 MC 틱(5초). */
    public static final int POPPY_NIGHT_VISION_MC_TICKS = 100;

    /** 민들레 스튜: 포만감 7 MC 틱(0.35초). */
    public static final int DANDELION_SATURATION_MC_TICKS = 7;

    /** 두 스튜 모두 앰프 0(레벨 I)입니다. */
    public static final int AMPLIFIER = 0;

    private SuspiciousStewRules() {
    }

    /** 이 블록이 수상한 스튜를 만들 수 있는 작은 꽃인가. */
    public static boolean isStewFlower(int blockId) {
        return blockId == Blocks.FLOWER_RED || blockId == Blocks.FLOWER_YELLOW
                || blockId == Blocks.OPEN_EYEBLOSSOM || blockId == Blocks.CLOSED_EYEBLOSSOM;
    }

    /** 작은 꽃 → 그 꽃으로 만들어지는 스튜 아이템. 스튜 꽃이 아니면 {@link PlayerInventory#EMPTY}. */
    public static short stewForFlower(int blockId) {
        if (blockId == Blocks.FLOWER_RED || blockId == Blocks.OPEN_EYEBLOSSOM
                || blockId == Blocks.CLOSED_EYEBLOSSOM) return PlayerInventory.SUSPICIOUS_STEW_POPPY;
        if (blockId == Blocks.FLOWER_YELLOW) return PlayerInventory.SUSPICIOUS_STEW_DANDELION;
        return PlayerInventory.EMPTY;
    }

    public static ItemComponentData flowerComponents(int flower) {
        if (flower == Blocks.OPEN_EYEBLOSSOM) return ItemComponentData.EMPTY.withSuspiciousStewEffect("blindness", 220);
        if (flower == Blocks.CLOSED_EYEBLOSSOM) return ItemComponentData.EMPTY.withSuspiciousStewEffect("nausea", 140);
        return ItemComponentData.EMPTY;
    }

    /** 스튜 아이템이 부여하는 효과. 수상한 스튜가 아니면 null. */
    public static StatusEffect effectOf(short itemType) {
        if (itemType == PlayerInventory.SUSPICIOUS_STEW_POPPY) return StatusEffect.NIGHT_VISION;
        if (itemType == PlayerInventory.SUSPICIOUS_STEW_DANDELION) return StatusEffect.SATURATION;
        return null;
    }

    /** Component-aware effect lookup; crafted poppy/dandelion variants keep their ID defaults. */
    public static StatusEffect effectOf(PlayerInventory.StackSnapshot stack) {
        if (stack == null) return null;
        ItemComponentData components = stack.itemComponents();
        if (components.suspiciousStewEffect() == null) return effectOf(stack.itemType());
        return requireArchaeologyEffect(components.suspiciousStewEffect(),
                components.suspiciousStewDurationMcTicks()).effect();
    }

    /** 스튜 아이템이 부여하는 효과의 지속(MC 틱). 수상한 스튜가 아니면 0. */
    public static int effectMcTicks(short itemType) {
        if (itemType == PlayerInventory.SUSPICIOUS_STEW_POPPY) return POPPY_NIGHT_VISION_MC_TICKS;
        if (itemType == PlayerInventory.SUSPICIOUS_STEW_DANDELION) {
            return DANDELION_SATURATION_MC_TICKS;
        }
        return 0;
    }

    /** Component-aware duration lookup; crafted variants remain 100/7 MC ticks. */
    public static int effectMcTicks(PlayerInventory.StackSnapshot stack) {
        if (stack == null) return 0;
        ItemComponentData components = stack.itemComponents();
        if (components.suspiciousStewEffect() == null) return effectMcTicks(stack.itemType());
        return requireArchaeologyEffect(components.suspiciousStewEffect(),
                components.suspiciousStewDurationMcTicks()).durationMcTicks();
    }

    /**
     * Strictly validates one pinned desert-well stew component. Unknown, unavailable, out-of-range,
     * or sub-second durations fail closed instead of degrading to an item-ID default.
     */
    public static StewEffect requireArchaeologyEffect(String protocolName,
            Integer durationMcTicks) {
        if (protocolName == null || durationMcTicks == null) {
            throw new IllegalArgumentException("complete suspicious stew effect is required");
        }
        if (protocolName.equals("saturation") && durationMcTicks >= 7 && durationMcTicks <= 10)
            return new StewEffect(StatusEffect.SATURATION, durationMcTicks);
        if (protocolName.equals("blindness") && durationMcTicks == 220) return new StewEffect(StatusEffect.BLINDNESS, 220);
        if (protocolName.equals("nausea") && durationMcTicks == 140) return new StewEffect(StatusEffect.NAUSEA, 140);
        for (EffectRange range : DESERT_WELL_EFFECTS) {
            if (!range.protocolName().equals(protocolName)) continue;
            StatusEffect available = StatusEffect.byProtocolName(protocolName);
            int duration = durationMcTicks;
            if (available != range.effect() || duration % 20 != 0
                    || duration < range.minSeconds() * 20
                    || duration > range.maxSeconds() * 20) {
                throw new IllegalArgumentException("invalid or unavailable suspicious stew effect");
            }
            return new StewEffect(available, duration);
        }
        throw new IllegalArgumentException("unknown suspicious stew effect: " + protocolName);
    }

    /** A fresh exact resolver for the loot table's persisted seed. */
    public static DesertWellLoot resolveDesertWellLoot(long lootSeed) {
        return new DesertWellResolver(lootSeed).nextLoot();
    }

    /**
     * Stateful {@code LegacyRandomSource}-compatible desert-well resolver. Every call consumes the
     * weighted selection first; only a stew selection consumes effect choice and inclusive uniform
     * duration, in that exact order and from the same 48-bit stream.
     */
    public static final class DesertWellResolver {
        private long seed;

        public DesertWellResolver(long seed) {
            this.seed = (seed ^ LEGACY_MULTIPLIER) & LEGACY_MASK;
        }

        public DesertWellLoot nextLoot() {
            int selection = nextInt(8);
            short itemType = switch (selection) {
                case 0, 1 -> PlayerInventory.ARMS_UP_POTTERY_SHERD;
                case 2, 3 -> PlayerInventory.BREWER_POTTERY_SHERD;
                case 4 -> PlayerInventory.BRICK;
                case 5 -> PlayerInventory.EMERALD;
                case 6 -> PlayerInventory.STICK;
                case 7 -> PlayerInventory.SUSPICIOUS_STEW_POPPY;
                default -> throw new IllegalStateException("unreachable desert well selection");
            };
            String encodedComponents = null;
            if (selection == 7) {
                EffectRange range = DESERT_WELL_EFFECTS[nextInt(DESERT_WELL_EFFECTS.length)];
                int seconds = range.minSeconds()
                        + nextInt(range.maxSeconds() - range.minSeconds() + 1);
                ItemComponentData components = new ItemComponentData(
                        null, java.util.List.of(), null, 0, null,
                        range.protocolName(), seconds * 20);
                encodedComponents = ItemComponentCodec.encode(itemType, components);
            }
            PlayerInventory.StackSnapshot stack = new PlayerInventory.StackSnapshot(
                    itemType, 1, 0, 0, 0, 0, null, encodedComponents);
            return new DesertWellLoot(stack, resultIdentity(stack));
        }

        private int next(int bits) {
            seed = (seed * LEGACY_MULTIPLIER + LEGACY_ADDEND) & LEGACY_MASK;
            return (int) (seed >>> (48 - bits));
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }
    }

    /** Canonical exact identity used by ARCH consume/ACK consumers. */
    public static String resultIdentity(PlayerInventory.StackSnapshot stack) {
        if (stack == null || stack.isEmpty() || stack.count() != 1) {
            throw new IllegalArgumentException("single non-empty archaeology result is required");
        }
        ItemComponentData components = ItemComponentCodec.decode(
                stack.itemType(), stack.itemComponentData());
        if (PlayerInventory.isSuspiciousStew(stack.itemType())) {
            if (stack.itemType() != PlayerInventory.SUSPICIOUS_STEW_POPPY
                    || components.suspiciousStewEffect() == null) {
                throw new IllegalArgumentException(
                        "desert well suspicious stew requires exact effect identity");
            }
            requireArchaeologyEffect(components.suspiciousStewEffect(),
                    components.suspiciousStewDurationMcTicks());
        }
        return Short.toUnsignedInt(stack.itemType()) + "#"
                + (stack.itemComponentData() == null ? "~" : stack.itemComponentData());
    }

    /**
     * 바닐라 전량 표의 지속(MC 틱)을 꽃 이름(바닐라 id)으로 돌려줍니다. <b>이 저장소가 아직
     * 등록하지 않은 꽃까지</b> 담고 있으며, 구현 여부와 무관한 <b>발췌 고정본</b>입니다 —
     * 실제 부여 경로는 {@link #effectOf} 뿐입니다. 알 수 없는 이름이면 0.
     */
    public static int vanillaEffectTicks(String vanillaFlowerId) {
        if (vanillaFlowerId == null) return 0;
        return switch (vanillaFlowerId) {
            // 이 저장소에 있는 두 꽃.
            case "poppy" -> 100;                 // night_vision
            case "dandelion" -> 7;               // saturation
            // 아직 블록으로 등록되지 않은 꽃들(효과 일부도 미구현이다).
            case "blue_orchid" -> 7;             // saturation
            case "allium" -> 60;                 // fire_resistance
            case "azure_bluet" -> 220;           // blindness
            case "cornflower" -> 100;            // jump_boost — 이 저장소에 없는 효과
            case "lily_of_the_valley" -> 220;    // poison
            case "oxeye_daisy" -> 140;           // regeneration — 이 저장소에 없는 효과
            case "red_tulip", "orange_tulip", "white_tulip", "pink_tulip" -> 140; // weakness
            case "wither_rose" -> 140;           // wither — 이 저장소에 없는 효과
            case "torchflower" -> 100;           // night_vision
            case "open_eyeblossom" -> 220;       // blindness
            case "closed_eyeblossom" -> 140;     // nausea — 이 저장소에 없는 효과
            default -> 0;
        };
    }
}
