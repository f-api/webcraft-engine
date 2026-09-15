package com.gameexpert.engine.trial;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.enchant.EnchantRandom;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.LegacyRand;

/**
 * [TRIAL-GAP] 트라이얼 챔버의 바닐라 전리품 표 다섯 벌과 그 해석기.
 *
 * <ul>
 *   <li>{@code chests/trial_chambers/reward} · {@code reward_ominous}(+ 각 common/rare/unique) — 금고.</li>
 *   <li>{@code spawners/trial_chamber/items_to_drop_when_ominous} — 불길한 스포너의 아이템 소환기.</li>
 *   <li>{@code equipment/trial_chamber_melee} · {@code trial_chamber_ranged}(+ {@code trial_chamber}) —
 *       불길한 설정이 소환하는 몹의 장비.</li>
 * </ul>
 *
 * <p>표의 모양·가중치·개수·함수 순서는 핀 26.3-snapshot-7 jar 의 {@code data/minecraft/loot_table}
 * JSON 그대로다. 해석은 바닐라 {@code LootTable.getRandomItemsRaw} → {@code LootPool.addRandomItems}
 * 순서를 따른다: 풀 조건({@code random_chance}: {@code nextFloat() < chance}) → 굴림 수(상수는 난수를 쓰지
 * 않고 {@code uniform} 은 {@code nextInt(max-min+1)+min}) → 항목이 하나면 난수 없이, 둘 이상이면
 * {@code nextInt(총 가중치)} → 항목 함수를 선언 순서대로. 난수원은 바닐라 {@code LegacyRandomSource}
 * ({@link LegacyRand}) 이고, 시드는 호출자가 정한다. 정적판 {@code StandaloneTrialLootTables.ts} 가
 * 같은 표·같은 순서의 손 사본이다.</p>
 *
 * <p><b>문서화된 축소</b>: {@code enchant_with_levels} 는 바닐라 {@code EnchantmentHelper} 가 아니라 이
 * 저장소의 인챈트 추첨({@link EnchantmentRules#modifiedEnchantLevel} · {@code rollEnchantmentsAt})으로
 * 옮긴다 — 낚시 보물({@code FishingRules.rollLootEnchantments})과 같은 선례다. 인챈트 저장 폭(16칸 ×
 * 3비트)에 없는 인챈트는 {@link #trialEnchantmentRepresentable} 한 곳이 가려 원래 아이템으로 둔다.
 * 장비의 갑옷 장식({@code set_components minecraft:trim})은 아직 스택 성분이 없어 TRIM 함수 자리(연결 지점)만
 * 남는다. [MOB-EQUIP] 장비 표의 인챈트·성분은 몹 장비 칸이 그대로 싣는다.</p>
 */
public final class TrialLootTables {

    private TrialLootTables() {}

    /** 해석 결과 한 칸. {@code ominousAmplifier} 는 불길한 병만 0..4, 그 외 −1 이다. */
    public record Stack(short itemType, int count, int durability, long enchantments,
            int ominousAmplifier, String itemComponentData) {
        public Stack {
            if (itemType == PlayerInventory.EMPTY || count <= 0) {
                throw new IllegalArgumentException("trial loot stack must be real");
            }
        }

        /** 확장 인챈트(ID 16 이상)가 없는 스택. */
        public Stack(short itemType, int count, int durability, long enchantments,
                int ominousAmplifier) {
            this(itemType, count, durability, enchantments, ominousAmplifier, null);
        }

        /**
         * [ENCHANT-WIDE] 43종 인챈트 집합. 워드 0 은 {@link #enchantments()}, 워드 1·2 는 성분
         * 문자열({@code WCIC4}, 확장이 없으면 null)이 싣는다.
         */
        public com.gameexpert.engine.enchant.WideEnchantments wideEnchantments() {
            return PlayerInventory.wideEnchantmentsOf(enchantments, itemComponentData);
        }
    }

    // ── 표 모델 ────────────────────────────────────────────────────────────────

    private enum FnKind { COUNT, POTION, DAMAGE, ENCHANT_WITH_LEVELS, ENCHANT_RANDOMLY,
        SET_ENCHANTMENTS, OMINOUS_AMPLIFIER, TRIM }

    /** 함수 하나. {@code min == max} 인 정수 범위는 바닐라 {@code ConstantValue}(난수 없음)다. */
    private record Fn(FnKind kind, int min, int max, float floatMin, float floatMax,
            String[] keys, int[] levels) {}

    private record Entry(String item, Table nested, int weight, Fn[] functions) {}

    /** {@code chance < 0} 은 조건 없는 풀이다. */
    private record Pool(int minRolls, int maxRolls, float chance, Entry[] entries) {}

    private record Table(Pool[] pools) {}

    private static Fn count(int value) {
        return new Fn(FnKind.COUNT, value, value, 0, 0, null, null);
    }

    private static Fn count(int min, int max) {
        return new Fn(FnKind.COUNT, min, max, 0, 0, null, null);
    }

    private static Fn potion(String key) {
        return new Fn(FnKind.POTION, 0, 0, 0, 0, new String[] {key}, null);
    }

    private static Fn damage(float min, float max) {
        return new Fn(FnKind.DAMAGE, 0, 0, min, max, null, null);
    }

    private static Fn enchantWithLevels(int min, int max) {
        return new Fn(FnKind.ENCHANT_WITH_LEVELS, min, max, 0, 0, null, null);
    }

    private static Fn enchantRandomly(String... keys) {
        return new Fn(FnKind.ENCHANT_RANDOMLY, 0, 0, 0, 0, keys, null);
    }

    private static Fn setEnchantments(String[] keys, int[] levels) {
        return new Fn(FnKind.SET_ENCHANTMENTS, 0, 0, 0, 0, keys, levels);
    }

    private static Fn ominousAmplifier(int min, int max) {
        return new Fn(FnKind.OMINOUS_AMPLIFIER, min, max, 0, 0, null, null);
    }

    private static Fn trim() {
        return new Fn(FnKind.TRIM, 0, 0, 0, 0, null, null);
    }

    private static Entry item(String key, int weight, Fn... functions) {
        return new Entry(key, null, weight, functions);
    }

    private static Entry nested(Table table, int weight) {
        return new Entry(null, table, weight, new Fn[0]);
    }

    /** 바닐라 {@code minecraft:empty} 항목(가중치만 차지하고 아무것도 내지 않는다). */
    private static Entry empty(int weight) {
        return new Entry(null, null, weight, new Fn[0]);
    }

    private static Pool pool(Entry... entries) {
        return new Pool(1, 1, -1f, entries);
    }

    private static Pool pool(int minRolls, int maxRolls, Entry... entries) {
        return new Pool(minRolls, maxRolls, -1f, entries);
    }

    private static Pool chancePool(float chance, Entry... entries) {
        return new Pool(1, 1, chance, entries);
    }

    private static Table table(Pool... pools) {
        return new Table(pools);
    }

    // ── 금고: chests/trial_chambers/reward* ────────────────────────────────────

    private static final Table REWARD_COMMON = table(pool(
            item("minecraft:arrow", 4, count(2, 8)),
            item("minecraft:tipped_arrow", 4, count(2, 8), potion("minecraft:poison")),
            item("minecraft:emerald", 4, count(2, 4)),
            item("minecraft:wind_charge", 3, count(1, 3)),
            item("minecraft:iron_ingot", 3, count(1, 4)),
            item("minecraft:honey_bottle", 3, count(1, 2)),
            item("minecraft:ominous_bottle", 2, count(1), ominousAmplifier(0, 1)),
            item("minecraft:wind_charge", 1, count(4, 12)),
            item("minecraft:diamond", 1, count(1, 2))));
    private static final Table REWARD_RARE = table(pool(
            item("minecraft:emerald", 3, count(2, 4)),
            item("minecraft:shield", 3, damage(0.5f, 1.0f)),
            item("minecraft:bow", 3, enchantWithLevels(5, 15)),
            item("minecraft:crossbow", 2, enchantWithLevels(5, 20)),
            item("minecraft:iron_axe", 2, enchantWithLevels(0, 10)),
            item("minecraft:iron_chestplate", 2, enchantWithLevels(0, 10)),
            item("minecraft:golden_carrot", 2, count(1, 2)),
            item("minecraft:book", 2, enchantRandomly("minecraft:sharpness",
                    "minecraft:bane_of_arthropods", "minecraft:efficiency", "minecraft:fortune",
                    "minecraft:silk_touch", "minecraft:feather_falling")),
            item("minecraft:book", 2, enchantRandomly("minecraft:riptide", "minecraft:loyalty",
                    "minecraft:channeling", "minecraft:impaling", "minecraft:mending")),
            item("minecraft:diamond_chestplate", 1, enchantWithLevels(5, 15)),
            item("minecraft:diamond_axe", 1, enchantWithLevels(5, 15))));
    private static final Table REWARD_UNIQUE = table(pool(
            item("minecraft:golden_apple", 4),
            item("minecraft:bolt_armor_trim_smithing_template", 3),
            item("minecraft:guster_banner_pattern", 2),
            item("minecraft:music_disc_precipice", 2),
            item("minecraft:trident", 1)));
    private static final Table REWARD = table(
            pool(nested(REWARD_RARE, 8), nested(REWARD_COMMON, 2)),
            pool(1, 3, nested(REWARD_COMMON, 1)),
            chancePool(0.25f, nested(REWARD_UNIQUE, 1)));

    private static final Table REWARD_OMINOUS_COMMON = table(pool(
            item("minecraft:emerald", 5, count(4, 10)),
            item("minecraft:wind_charge", 4, count(8, 12)),
            item("minecraft:tipped_arrow", 3, count(4, 12), potion("minecraft:strong_slowness")),
            item("minecraft:diamond", 2, count(2, 3)),
            item("minecraft:ominous_bottle", 1, count(1), ominousAmplifier(2, 4))));
    private static final Table REWARD_OMINOUS_RARE = table(pool(
            item("minecraft:emerald_block", 5),
            item("minecraft:iron_block", 4),
            item("minecraft:crossbow", 4, enchantWithLevels(5, 20)),
            item("minecraft:golden_apple", 3),
            item("minecraft:diamond_axe", 3, enchantWithLevels(10, 20)),
            item("minecraft:diamond_chestplate", 3, enchantWithLevels(10, 20)),
            item("minecraft:book", 2, enchantRandomly("minecraft:knockback", "minecraft:punch",
                    "minecraft:smite", "minecraft:looting", "minecraft:multishot")),
            item("minecraft:book", 2, enchantRandomly("minecraft:breach", "minecraft:density")),
            item("minecraft:book", 2, setEnchantments(new String[] {"minecraft:wind_burst"},
                    new int[] {1})),
            item("minecraft:diamond_block", 1)));
    private static final Table REWARD_OMINOUS_UNIQUE = table(pool(
            item("minecraft:enchanted_golden_apple", 3),
            item("minecraft:flow_armor_trim_smithing_template", 3),
            item("minecraft:flow_banner_pattern", 2),
            item("minecraft:music_disc_creator", 1),
            item("minecraft:heavy_core", 1)));
    private static final Table REWARD_OMINOUS = table(
            pool(nested(REWARD_OMINOUS_RARE, 8), nested(REWARD_OMINOUS_COMMON, 2)),
            pool(1, 3, nested(REWARD_OMINOUS_COMMON, 1)),
            chancePool(0.75f, nested(REWARD_OMINOUS_UNIQUE, 1)));

    // ── 불길한 아이템 소환기: spawners/trial_chamber/items_to_drop_when_ominous ─────────

    private static final Table ITEMS_TO_DROP_WHEN_OMINOUS = table(
            pool(
                    item("minecraft:lingering_potion", 1, count(1), potion("minecraft:wind_charged")),
                    item("minecraft:lingering_potion", 1, count(1), potion("minecraft:oozing")),
                    item("minecraft:lingering_potion", 1, count(1), potion("minecraft:weaving")),
                    item("minecraft:lingering_potion", 1, count(1), potion("minecraft:infested")),
                    item("minecraft:lingering_potion", 1, count(1), potion("minecraft:strength")),
                    item("minecraft:lingering_potion", 1, count(1), potion("minecraft:swiftness")),
                    item("minecraft:lingering_potion", 1, count(1),
                            potion("minecraft:slow_falling"))),
            pool(
                    item("minecraft:arrow", 1, count(1)),
                    item("minecraft:arrow", 1, count(1), potion("minecraft:poison")),
                    item("minecraft:arrow", 1, count(1), potion("minecraft:strong_slowness")),
                    item("minecraft:fire_charge", 1, count(1, 3)),
                    item("minecraft:wind_charge", 1, count(1, 3))));

    // ── 장비: equipment/trial_chamber* ─────────────────────────────────────────

    private static final String[] ARMOR_ENCHANTS = {"minecraft:fire_protection",
        "minecraft:projectile_protection", "minecraft:protection"};
    private static final int[] ARMOR_LEVELS = {4, 4, 4};

    private static Table armorSet(String helmet, String chestplate) {
        return table(
                chancePool(0.5f, item(helmet, 1, trim(),
                        setEnchantments(ARMOR_ENCHANTS, ARMOR_LEVELS))),
                chancePool(0.5f, item(chestplate, 1, trim(),
                        setEnchantments(ARMOR_ENCHANTS, ARMOR_LEVELS))));
    }

    private static final Table EQUIPMENT_TRIAL_CHAMBER = table(pool(
            nested(armorSet("minecraft:chainmail_helmet", "minecraft:chainmail_chestplate"), 4),
            nested(armorSet("minecraft:iron_helmet", "minecraft:iron_chestplate"), 2),
            nested(armorSet("minecraft:diamond_helmet", "minecraft:diamond_chestplate"), 1)));
    private static final Table EQUIPMENT_MELEE = table(
            pool(nested(EQUIPMENT_TRIAL_CHAMBER, 1)),
            pool(item("minecraft:iron_sword", 4),
                    item("minecraft:iron_sword", 1, setEnchantments(
                            new String[] {"minecraft:sharpness"}, new int[] {1})),
                    item("minecraft:iron_sword", 1, setEnchantments(
                            new String[] {"minecraft:knockback"}, new int[] {1})),
                    item("minecraft:diamond_sword", 1)));
    private static final Table EQUIPMENT_RANGED = table(
            pool(nested(EQUIPMENT_TRIAL_CHAMBER, 1)),
            pool(item("minecraft:bow", 2),
                    item("minecraft:bow", 1, setEnchantments(
                            new String[] {"minecraft:power"}, new int[] {1})),
                    item("minecraft:bow", 1, setEnchantments(
                            new String[] {"minecraft:punch"}, new int[] {1}))));

    // ── [END-CITY] chests/end_city_treasure ─────────────────────────────────────
    // 핀 26.3 jar 의 표 그대로다(항목 순서·가중치·개수·enchant_with_levels 20..39). 인챈트는 위 문서화된
    // 축소(이 저장소 추첨)를 공유한다. 두 번째 풀(빈 14 : 가시 갑옷 장식 형판 1)의 형판은 [UTILITY] 갑옷 장식
    // 트랙이 배정한 SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE 다.
    private static final Table END_CITY_TREASURE = table(
            pool(2, 6,
                    item("minecraft:diamond", 5, count(2, 7)),
                    item("minecraft:iron_ingot", 10, count(4, 8)),
                    item("minecraft:gold_ingot", 15, count(2, 7)),
                    item("minecraft:emerald", 2, count(2, 6)),
                    item("minecraft:beetroot_seeds", 5, count(1, 10)),
                    item("minecraft:saddle", 3),
                    item("minecraft:copper_horse_armor", 1),
                    item("minecraft:iron_horse_armor", 1),
                    item("minecraft:golden_horse_armor", 1),
                    item("minecraft:diamond_horse_armor", 1),
                    item("minecraft:diamond_sword", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_spear", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_boots", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_chestplate", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_leggings", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_helmet", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_pickaxe", 3, enchantWithLevels(20, 39)),
                    item("minecraft:diamond_shovel", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_sword", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_boots", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_chestplate", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_leggings", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_helmet", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_pickaxe", 3, enchantWithLevels(20, 39)),
                    item("minecraft:iron_shovel", 3, enchantWithLevels(20, 39))),
            pool(empty(14), item("minecraft:spire_armor_trim_smithing_template", 1)));

    /**
     * [END-CITY] {@code chests/end_city_treasure} 를 바닐라 {@code LootTable.fill} 로 27칸 상자에 채운 결과
     * (칸 → 스택). 난수는 상자 {@code LootTableSeed} 의 {@code LegacyRandomSource} 하나로 표 굴림 →
     * {@code getAvailableSlots}(빈 칸 목록 {@code Util.shuffle}) → {@code shuffleAndSplitItems} → 칸 목록 뒤에서부터
     * 꺼내 놓기 순서다. 정적판 사본은 {@code StandaloneTrialLootTables.endCityTreasureSlots}.
     */
    public static java.util.Map<Integer, Stack> endCityTreasureSlots(long lootSeed) {
        LegacyRand random = LegacyRand.fromSeed(lootSeed);
        List<Stack> items = new ArrayList<>(8);
        addTable(END_CITY_TREASURE, random, items);
        return fillChest(items, random, 27);
    }

    /** 바닐라 {@code LootTable.fill}(빈 상자): 칸 셔플 → 나눠 흩기 → 셔플된 칸 목록 뒤에서부터 놓는다. */
    static java.util.Map<Integer, Stack> fillChest(List<Stack> rolled, LegacyRand random, int size) {
        List<Integer> slots = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) slots.add(slot);
        shuffle(slots, random);
        List<Stack> items = new ArrayList<>(rolled);
        // shuffleAndSplitItems: 2개 이상 스택을 떼어 두고, 빈 칸이 남는 동안 무작위 스택을 무작위로 쪼갠다.
        List<Stack> splittable = new ArrayList<>();
        items.removeIf(stack -> {
            if (stack.count() > 1) {
                splittable.add(stack);
                return true;
            }
            return false;
        });
        while (size - items.size() - splittable.size() > 0 && !splittable.isEmpty()) {
            Stack stack = splittable.remove(nextIntBetween(random, 0, splittable.size() - 1));
            int split = nextIntBetween(random, 1, stack.count() / 2);
            Stack taken = withCount(stack, split);
            Stack rest = withCount(stack, stack.count() - split);
            if (rest.count() > 1 && random.nextBoolean()) splittable.add(rest);
            else items.add(rest);
            if (taken.count() > 1 && random.nextBoolean()) splittable.add(taken);
            else items.add(taken);
        }
        items.addAll(splittable);
        shuffle(items, random);
        java.util.Map<Integer, Stack> out = new java.util.LinkedHashMap<>();
        for (Stack stack : items) {
            if (slots.isEmpty()) break;
            out.put(slots.remove(slots.size() - 1), stack);
        }
        return out;
    }

    private static Stack withCount(Stack stack, int count) {
        return new Stack(stack.itemType(), count, stack.durability(), stack.enchantments(),
                stack.ominousAmplifier(), stack.itemComponentData());
    }

    /** 바닐라 {@code Mth.nextInt(random, a, b)}. */
    private static int nextIntBetween(LegacyRand random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** 바닐라 {@code Util.shuffle}: 뒤에서부터 {@code nextInt(i)} 자리와 맞바꾼다. */
    private static <T> void shuffle(List<T> list, LegacyRand random) {
        for (int i = list.size(); i > 1; i--) {
            int j = random.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
    }

    /** 금고 표 선택. 일반 금고는 {@code reward}, 불길한 금고는 {@code reward_ominous} 다. */
    public static List<Stack> vaultReward(boolean ominous, long seed) {
        return roll(ominous ? REWARD_OMINOUS : REWARD, seed);
    }

    /**
     * 불길한 스포너의 아이템 소환기 표. 바닐라 {@code getDispensingItems} 는 이 표를
     * {@code seed = level.getSeed() + BlockPos(x/30, y/20, z/30).asLong()} 로 한 번 굴려 스포너마다
     * 고정하고, 결과 스택마다 개수를 가중치로 삼은 1개짜리 목록을 만든다.
     */
    public static List<Stack> ominousDispensingItems(long worldSeed, int x, int y, int z) {
        return roll(ITEMS_TO_DROP_WHEN_OMINOUS, dispensingSeed(worldSeed, x, y, z));
    }

    /** 바닐라 {@code TrialSpawnerStateData.lowResolutionPosition}. */
    public static long dispensingSeed(long worldSeed, int x, int y, int z) {
        int lx = (int) Math.floor((float) x / 30.0f);
        int ly = (int) Math.floor((float) y / 20.0f);
        int lz = (int) Math.floor((float) z / 30.0f);
        return worldSeed + TrialSpawnerContract.packedBlockPos(lx, ly, lz);
    }

    /** 몹 한 마리의 장비(바닐라 {@code Mob.equip}). {@code ranged} 는 해골·스트레이 설정이다. */
    public static List<Stack> equipment(boolean ranged, long seed) {
        return roll(ranged ? EQUIPMENT_RANGED : EQUIPMENT_MELEE, seed);
    }

    static List<Stack> roll(Table table, long seed) {
        LegacyRand random = LegacyRand.fromSeed(seed);
        List<Stack> out = new ArrayList<>(4);
        addTable(table, random, out);
        return List.copyOf(out);
    }

    private static void addTable(Table table, LegacyRand random, List<Stack> out) {
        for (Pool pool : table.pools()) {
            if (pool.chance() >= 0f && !(random.nextFloat() < pool.chance())) continue;
            int rolls = uniformInt(random, pool.minRolls(), pool.maxRolls());
            for (int roll = 0; roll < rolls; roll++) addRandomItem(pool.entries(), random, out);
        }
    }

    private static void addRandomItem(Entry[] entries, LegacyRand random, List<Stack> out) {
        Entry chosen;
        if (entries.length == 1) {
            chosen = entries[0];
        } else {
            int total = 0;
            for (Entry entry : entries) total += entry.weight();
            int pick = random.nextInt(total);
            chosen = null;
            for (Entry entry : entries) {
                pick -= entry.weight();
                if (pick < 0) {
                    chosen = entry;
                    break;
                }
            }
        }
        if (chosen.nested() != null) {
            addTable(chosen.nested(), random, out);
            return;
        }
        if (chosen.item() == null) return;
        out.add(createStack(chosen, random));
    }

    /** 바닐라 {@code UniformGenerator.getInt}: 상수는 난수를 쓰지 않는다. */
    private static int uniformInt(LegacyRand random, int min, int max) {
        return min == max ? min : random.nextInt(max - min + 1) + min;
    }

    private static Stack createStack(Entry entry, LegacyRand random) {
        String key = entry.item();
        String potion = null;
        int count = 1;
        int damageValue = -1;
        com.gameexpert.engine.enchant.WideEnchantments enchantments =
                com.gameexpert.engine.enchant.WideEnchantments.EMPTY;
        boolean enchantedBook = false;
        int amplifier = -1;
        for (Fn fn : entry.functions()) {
            switch (fn.kind()) {
                case COUNT -> count = uniformInt(random, fn.min(), fn.max());
                case POTION -> potion = fn.keys()[0];
                case DAMAGE -> {
                    // 바닐라 SetItemDamageFunction: setDamageValue(floor((1 - f) * max)).
                    float fraction = random.nextFloat() * (fn.floatMax() - fn.floatMin())
                            + fn.floatMin();
                    int max = PlayerInventory.initialDurability(itemFor(key, null));
                    damageValue = (int) Math.floor((1.0f - fraction) * max);
                }
                case ENCHANT_WITH_LEVELS -> {
                    int levels = uniformInt(random, fn.min(), fn.max());
                    short base = itemFor(key, null);
                    EnchantRandom enchantRandom = new EnchantRandom(random.nextInt());
                    int level = EnchantmentRules.modifiedEnchantLevel(enchantRandom, base, levels);
                    com.gameexpert.engine.enchant.WideEnchantments rolled =
                            EnchantmentRules.rollEnchantmentsAt(base, level, enchantRandom);
                    if (!rolled.isEmpty()) {
                        enchantments = rolled;
                        if (base == PlayerInventory.BOOK) enchantedBook = true;
                    }
                }
                case ENCHANT_RANDOMLY -> {
                    // 바닐라 EnchantRandomlyFunction: 책은 호환 여부를 묻지 않고 후보 전부에서 하나,
                    // 레벨은 Mth.nextInt(random, 1, maxLevel).
                    String chosen = fn.keys()[random.nextInt(fn.keys().length)];
                    int maxLevel = vanillaMaxLevel(chosen);
                    int level = maxLevel <= 1 ? 1 : random.nextInt(maxLevel) + 1;
                    int id = trialEnchantmentId(chosen);
                    if (trialEnchantmentRepresentable(chosen)) {
                        enchantments = enchantments.with(id, level);
                        enchantedBook = true;
                    }
                }
                case SET_ENCHANTMENTS -> {
                    for (int index = 0; index < fn.keys().length; index++) {
                        String chosen = fn.keys()[index];
                        if (!trialEnchantmentRepresentable(chosen)) continue;
                        int id = trialEnchantmentId(chosen);
                        // [MOB-EQUIP] 바닐라 SetEnchantmentsFunction 은 호환을 묻지 않는다(add=false 면 레벨을
                        // 덮어쓴다). 그래서 트라이얼 몹 갑옷은 화염·발사체·일반 보호 IV 를 함께 단다 — 스택은 배타
                        // 조합을 담을 수 있고(isValidEnchantmentsForItem), 호환은 모루·마법부여대 적용만 묻는다.
                        enchantments = enchantments.with(id, fn.levels()[index]);
                        if ("minecraft:book".equals(key)) enchantedBook = true;
                    }
                }
                case OMINOUS_AMPLIFIER -> amplifier = uniformInt(random, fn.min(), fn.max());
                // [MOB-EQUIP] 갑옷 장식 연결 지점: 장식 성분이 생기면 여기서 스택 성분 문자열에 싣는다 —
                // Mob.installTrialEquipment 는 성분 문자열을 손대지 않고 몹 장비 칸으로 옮긴다.
                case TRIM -> { }
            }
        }
        short itemType = enchantedBook ? PlayerInventory.ENCHANTED_BOOK : itemFor(key, potion);
        int maxDurability = PlayerInventory.initialDurability(itemType);
        int durability = maxDurability <= 0 ? 0
                : damageValue < 0 ? maxDurability
                // 이 저장소 인벤토리는 남은 내구 0 을 담지 못한다(EquipmentDropRules 와 같은 하한).
                : Math.max(1, maxDurability - damageValue);
        if (!EnchantmentRules.isValidEnchantmentsForItem(itemType, enchantments)) {
            enchantments = com.gameexpert.engine.enchant.WideEnchantments.EMPTY;
        }
        // [ENCHANT-WIDE] 워드 0 은 스택 칸, ID 16 이상은 성분 문자열(WCIC4)로 싣는다.
        return new Stack(itemType, count, durability, enchantments.word0(),
                itemType == PlayerInventory.OMINOUS_BOTTLE ? Math.max(0, amplifier) : -1,
                enchantments.hasExtended()
                        ? com.gameexpert.engine.inventory.ItemComponentCodec.withEnchantments(
                                itemType, null, enchantments)
                        : null);
    }

    /** 바닐라 아이템 키(+ potion_contents) → 이 저장소 아이템 ID. 없는 조합은 예외다. */
    static short itemFor(String key, String potion) {
        if (potion != null) {
            return switch (key + "|" + potion) {
                case "minecraft:tipped_arrow|minecraft:poison",
                        "minecraft:arrow|minecraft:poison" -> PlayerInventory.TIPPED_ARROW_POISON;
                case "minecraft:tipped_arrow|minecraft:strong_slowness",
                        "minecraft:arrow|minecraft:strong_slowness" ->
                        PlayerInventory.TIPPED_ARROW_STRONG_SLOWNESS;
                case "minecraft:lingering_potion|minecraft:wind_charged" ->
                        PlayerInventory.LINGERING_POTION_WIND_CHARGED;
                case "minecraft:lingering_potion|minecraft:oozing" ->
                        PlayerInventory.LINGERING_POTION_OOZING;
                case "minecraft:lingering_potion|minecraft:weaving" ->
                        PlayerInventory.LINGERING_POTION_WEAVING;
                case "minecraft:lingering_potion|minecraft:infested" ->
                        PlayerInventory.LINGERING_POTION_INFESTED;
                case "minecraft:lingering_potion|minecraft:strength" ->
                        PlayerInventory.LINGERING_POTION_STRENGTH;
                case "minecraft:lingering_potion|minecraft:swiftness" ->
                        PlayerInventory.LINGERING_POTION_SWIFTNESS;
                case "minecraft:lingering_potion|minecraft:slow_falling" ->
                        PlayerInventory.LINGERING_POTION_SLOW_FALLING;
                case "minecraft:potion|minecraft:regeneration" -> PlayerInventory.POTION_REGENERATION;
                case "minecraft:potion|minecraft:swiftness" -> PlayerInventory.POTION_SWIFTNESS;
                case "minecraft:potion|minecraft:strength" -> PlayerInventory.POTION_STRENGTH;
                default -> throw new IllegalArgumentException("unmapped trial potion " + key + "|" + potion);
            };
        }
        return switch (key) {
            case "minecraft:arrow" -> PlayerInventory.ARROW;
            case "minecraft:emerald" -> PlayerInventory.EMERALD;
            case "minecraft:wind_charge" -> PlayerInventory.WIND_CHARGE;
            case "minecraft:iron_ingot" -> PlayerInventory.IRON_INGOT;
            case "minecraft:honey_bottle" -> PlayerInventory.HONEY_BOTTLE;
            case "minecraft:ominous_bottle" -> PlayerInventory.OMINOUS_BOTTLE;
            case "minecraft:diamond" -> PlayerInventory.DIAMOND;
            case "minecraft:shield" -> PlayerInventory.SHIELD;
            case "minecraft:bow" -> PlayerInventory.BOW;
            case "minecraft:crossbow" -> PlayerInventory.CROSSBOW;
            case "minecraft:iron_axe" -> PlayerInventory.IRON_AXE;
            case "minecraft:iron_chestplate" -> PlayerInventory.IRON_CHESTPLATE;
            case "minecraft:golden_carrot" -> PlayerInventory.GOLDEN_CARROT;
            case "minecraft:book" -> PlayerInventory.BOOK;
            case "minecraft:diamond_chestplate" -> PlayerInventory.DIAMOND_CHESTPLATE;
            case "minecraft:diamond_axe" -> PlayerInventory.DIAMOND_AXE;
            case "minecraft:golden_apple" -> PlayerInventory.GOLDEN_APPLE;
            case "minecraft:bolt_armor_trim_smithing_template" ->
                    PlayerInventory.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "minecraft:guster_banner_pattern" -> PlayerInventory.GUSTER_BANNER_PATTERN;
            case "minecraft:music_disc_precipice" -> PlayerInventory.MUSIC_DISC_PRECIPICE;
            case "minecraft:trident" -> PlayerInventory.TRIDENT;
            case "minecraft:emerald_block" -> PlayerInventory.EMERALD_BLOCK;
            case "minecraft:iron_block" -> (short) Blocks.IRON_BLOCK;
            case "minecraft:diamond_block" -> (short) Blocks.DIAMOND_BLOCK;
            case "minecraft:enchanted_golden_apple" -> PlayerInventory.ENCHANTED_GOLDEN_APPLE;
            case "minecraft:flow_armor_trim_smithing_template" ->
                    PlayerInventory.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "minecraft:flow_banner_pattern" -> PlayerInventory.FLOW_BANNER_PATTERN;
            case "minecraft:music_disc_creator" -> PlayerInventory.MUSIC_DISC_CREATOR;
            case "minecraft:heavy_core" -> PlayerInventory.HEAVY_CORE;
            case "minecraft:fire_charge" -> PlayerInventory.FIRE_CHARGE;
            case "minecraft:chainmail_helmet" -> PlayerInventory.CHAINMAIL_HELMET;
            case "minecraft:chainmail_chestplate" -> PlayerInventory.CHAINMAIL_CHESTPLATE;
            case "minecraft:iron_helmet" -> PlayerInventory.IRON_HELMET;
            case "minecraft:diamond_helmet" -> PlayerInventory.DIAMOND_HELMET;
            case "minecraft:iron_sword" -> PlayerInventory.IRON_SWORD;
            case "minecraft:diamond_sword" -> PlayerInventory.DIAMOND_SWORD;
            // [END-CITY] chests/end_city_treasure 의 나머지 항목.
            case "minecraft:gold_ingot" -> PlayerInventory.GOLD_INGOT;
            case "minecraft:beetroot_seeds" -> PlayerInventory.BEETROOT_SEEDS;
            case "minecraft:saddle" -> PlayerInventory.SADDLE;
            case "minecraft:copper_horse_armor" -> PlayerInventory.COPPER_HORSE_ARMOR;
            case "minecraft:iron_horse_armor" -> PlayerInventory.IRON_HORSE_ARMOR;
            case "minecraft:golden_horse_armor" -> PlayerInventory.GOLDEN_HORSE_ARMOR;
            case "minecraft:diamond_horse_armor" -> PlayerInventory.DIAMOND_HORSE_ARMOR;
            case "minecraft:diamond_spear" -> PlayerInventory.DIAMOND_SPEAR;
            case "minecraft:diamond_boots" -> PlayerInventory.DIAMOND_BOOTS;
            case "minecraft:diamond_leggings" -> PlayerInventory.DIAMOND_LEGGINGS;
            case "minecraft:diamond_pickaxe" -> PlayerInventory.DIAMOND_PICKAXE;
            case "minecraft:diamond_shovel" -> PlayerInventory.DIAMOND_SHOVEL;
            case "minecraft:iron_boots" -> PlayerInventory.IRON_BOOTS;
            case "minecraft:iron_leggings" -> PlayerInventory.IRON_LEGGINGS;
            case "minecraft:iron_pickaxe" -> PlayerInventory.IRON_PICKAXE;
            case "minecraft:iron_shovel" -> PlayerInventory.IRON_SHOVEL;
            case "minecraft:spire_armor_trim_smithing_template" ->
                    PlayerInventory.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
            default -> throw new IllegalArgumentException("unmapped trial loot item " + key);
        };
    }

    /**
     * 바닐라 인챈트 {@code max_level}(26.3 {@code data/minecraft/enchantment/*.json}). enchant_randomly
     * 가 레벨을 {@code Mth.nextInt(random, 1, maxLevel)} 로 뽑으므로 표현 가능 여부와 무관하게 필요하다.
     */
    static int vanillaMaxLevel(String key) {
        return switch (key) {
            case "minecraft:sharpness", "minecraft:bane_of_arthropods", "minecraft:efficiency",
                    "minecraft:smite", "minecraft:impaling", "minecraft:power",
                    "minecraft:density" -> 5;
            case "minecraft:fortune", "minecraft:riptide", "minecraft:loyalty",
                    "minecraft:looting" -> 3;
            case "minecraft:feather_falling", "minecraft:protection", "minecraft:breach",
                    "minecraft:fire_protection", "minecraft:projectile_protection" -> 4;
            case "minecraft:knockback", "minecraft:punch" -> 2;
            case "minecraft:silk_touch", "minecraft:channeling", "minecraft:mending",
                    "minecraft:multishot" -> 1;
            case "minecraft:wind_burst" -> 3;
            default -> throw new IllegalArgumentException("unknown trial enchantment " + key);
        };
    }

    /**
     * [ENCHANT-CAPACITY] 이 바닐라 인챈트를 게임플레이 스택이 담을 수 있는가. [ENCHANT-WIDE] 저장 폭이
     * 43종 전부로 넓어져 공유 API {@link EnchantmentRules#isRepresentableEnchantmentKey} 를 그대로
     * 부른다 — 26.3 의 모든 키가 참이라 트라이얼 보상이 "원래 아이템" 으로 떨어지지 않는다.
     * 정적판 사본은 {@code StandaloneTrialLootTables.trialEnchantmentRepresentable} 이다.
     */
    public static boolean trialEnchantmentRepresentable(String key) {
        return EnchantmentRules.isRepresentableEnchantmentKey(key);
    }

    /** [ENCHANT-CAPACITY] 바닐라 인챈트 키 → 이 저장소 인챈트 ID(공유 {@code canonicalEnchantmentId}). 모르면 −1. */
    static int trialEnchantmentId(String key) {
        return EnchantmentRules.canonicalEnchantmentId(key);
    }
}
