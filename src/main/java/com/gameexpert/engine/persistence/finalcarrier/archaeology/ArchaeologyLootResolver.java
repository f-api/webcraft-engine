package com.gameexpert.engine.persistence.finalcarrier.archaeology;

import com.gameexpert.engine.ArchaeologyRules;
import com.gameexpert.engine.SuspiciousStewRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import java.util.Set;

/** Exact pinned-26.3 ARCH table boundary. Unsupported official vocabulary fails closed. */
public interface ArchaeologyLootResolver {
    String DESERT_PYRAMID = "minecraft:archaeology/desert_pyramid";
    String DESERT_WELL = "minecraft:archaeology/desert_well";
    String OCEAN_RUIN_COLD = "minecraft:archaeology/ocean_ruin_cold";
    String OCEAN_RUIN_WARM = "minecraft:archaeology/ocean_ruin_warm";
    String TRAIL_RUINS_COMMON = "minecraft:archaeology/trail_ruins_common";
    String TRAIL_RUINS_RARE = "minecraft:archaeology/trail_ruins_rare";

    Set<String> OFFICIAL_TABLES = Set.of(DESERT_PYRAMID, DESERT_WELL,
            OCEAN_RUIN_COLD, OCEAN_RUIN_WARM, TRAIL_RUINS_COMMON, TRAIL_RUINS_RARE);

    static ArchaeologyLootResolver pinned() {
        return PinnedMc263.INSTANCE;
    }

    boolean supports(String exactTableKey);

    ResolvedLoot resolve(String exactTableKey, long signedSeed);

    final class ResolvedLoot {
        private final PlayerInventory.StackSnapshot stack;
        private final String resultIdentity;

        public ResolvedLoot(PlayerInventory.StackSnapshot stack, String resultIdentity) {
            if (stack == null || stack.isEmpty() || stack.count() != 1
                    || resultIdentity == null || resultIdentity.isBlank()) {
                throw new IllegalArgumentException("invalid exact archaeology result");
            }
            this.stack = stack;
            this.resultIdentity = resultIdentity;
        }

        public PlayerInventory.StackSnapshot stack() {
            return stack;
        }

        public String resultIdentity() {
            return resultIdentity;
        }
    }

    final class PinnedMc263 implements ArchaeologyLootResolver {
        private static final PinnedMc263 INSTANCE = new PinnedMc263();
        private static final ArchaeologyRules.LootEntry[] DESERT_PYRAMID_LOOT = {
            new ArchaeologyRules.LootEntry(PlayerInventory.ARCHER_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.MINER_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.PRIZE_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.SKULL_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.DIAMOND, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.TNT, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.GUNPOWDER, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.EMERALD, 1),
        };
        private static final ArchaeologyRules.LootEntry[] TRAIL_RUINS_COMMON_LOOT = {
            new ArchaeologyRules.LootEntry(PlayerInventory.EMERALD, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.WHEAT, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.WOODEN_HOE, 2),
            new ArchaeologyRules.LootEntry((short) Blocks.CLAY, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.BRICK, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.YELLOW_DYE, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.BLUE_DYE, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.LIGHT_BLUE_DYE, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.WHITE_DYE, 2),
            new ArchaeologyRules.LootEntry(PlayerInventory.ORANGE_DYE, 2),
            new ArchaeologyRules.LootEntry((short) Blocks.RED_CANDLE, 2),
            new ArchaeologyRules.LootEntry((short) Blocks.GREEN_CANDLE, 2),
            new ArchaeologyRules.LootEntry((short) Blocks.PURPLE_CANDLE, 2),
            new ArchaeologyRules.LootEntry((short) Blocks.BROWN_CANDLE, 2),
            new ArchaeologyRules.LootEntry((short) Blocks.MAGENTA_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.PINK_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.BLUE_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.RED_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.YELLOW_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.PURPLE_STAINED_GLASS_PANE, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.SPRUCE_HANGING_SIGN, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.OAK_HANGING_SIGN, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.GOLD_NUGGET, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.COAL, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.WHEAT_SEEDS, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.BEETROOT_SEEDS, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.DEAD_BUSH, 1),
            new ArchaeologyRules.LootEntry((short) Blocks.FLOWER_POT, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.STRING, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.LEAD, 1),
        };
        private static final ArchaeologyRules.LootEntry[] TRAIL_RUINS_RARE_LOOT = {
            new ArchaeologyRules.LootEntry(PlayerInventory.BURN_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.DANGER_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.FRIEND_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.HEART_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.HEARTBREAK_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.HOWL_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.SHEAF_POTTERY_SHERD, 1),
            new ArchaeologyRules.LootEntry(
                    PlayerInventory.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            new ArchaeologyRules.LootEntry(
                    PlayerInventory.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            new ArchaeologyRules.LootEntry(
                    PlayerInventory.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            new ArchaeologyRules.LootEntry(
                    PlayerInventory.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            new ArchaeologyRules.LootEntry(PlayerInventory.MUSIC_DISC_RELIC, 1),
        };
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private PinnedMc263() {
        }

        @Override
        public boolean supports(String exactTableKey) {
            return OFFICIAL_TABLES.contains(exactTableKey);
        }

        @Override
        public ResolvedLoot resolve(String exactTableKey, long signedSeed) {
            if (!OFFICIAL_TABLES.contains(exactTableKey)) {
                throw new IllegalArgumentException("unknown official archaeology table: "
                        + exactTableKey);
            }
            if (DESERT_WELL.equals(exactTableKey)) {
                SuspiciousStewRules.DesertWellLoot loot =
                        SuspiciousStewRules.resolveDesertWellLoot(signedSeed);
                return new ResolvedLoot(loot.stack(), loot.resultIdentity());
            }
            ArchaeologyRules.LootEntry[] table = switch (exactTableKey) {
                case DESERT_PYRAMID -> DESERT_PYRAMID_LOOT;
                case OCEAN_RUIN_COLD -> ArchaeologyRules.OCEAN_RUIN_COLD_LOOT;
                case OCEAN_RUIN_WARM -> ArchaeologyRules.OCEAN_RUIN_WARM_LOOT;
                case TRAIL_RUINS_COMMON -> TRAIL_RUINS_COMMON_LOOT;
                case TRAIL_RUINS_RARE -> TRAIL_RUINS_RARE_LOOT;
                default -> throw new IllegalStateException("unreachable archaeology table");
            };
            int total = ArchaeologyRules.lootWeight(table);
            ArchaeologyRules.LootEntry selected = null;
            int cursor = nextInt(signedSeed, total);
            for (ArchaeologyRules.LootEntry entry : table) {
                cursor -= entry.weight();
                if (cursor < 0) {
                    selected = entry;
                    break;
                }
            }
            if (selected == null) throw new IllegalStateException("unresolved archaeology loot");
            int durability = PlayerInventory.isDurable(selected.itemType())
                    ? PlayerInventory.initialDurability(selected.itemType()) : 0;
            PlayerInventory.StackSnapshot stack = new PlayerInventory.StackSnapshot(
                    selected.itemType(), 1, durability, 0, 0, 0, null, null);
            return new ResolvedLoot(stack, SuspiciousStewRules.resultIdentity(stack));
        }

        private static int nextInt(long signedSeed, int bound) {
            LegacyRandom random = new LegacyRandom(signedSeed);
            return random.nextInt(bound);
        }

        private static final class LegacyRandom {
            private long seed;

            private LegacyRandom(long seed) {
                this.seed = (seed ^ MULTIPLIER) & MASK;
            }

            private int next(int bits) {
                seed = (seed * MULTIPLIER + ADDEND) & MASK;
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
    }
}
