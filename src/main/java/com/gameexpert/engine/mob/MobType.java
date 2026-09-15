package com.gameexpert.engine.mob;

import java.util.EnumSet;

import com.gameexpert.engine.TurtleEggRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * 몹 종류와 그 물리/전투 상수. AABB 폭/높이는 CONTRACT §11.1(플레이어) 및
 * client/src/entities/mobs 프로필(적대: MobModelFactory MOB_PROFILES / 동물: AnimalProfiles)과 1:1 일치.
 * baseSpeed 는 Java Edition 이동 속도 속성의 기본값이다. WebCraft 물리는 이 값을
 * 10TPS 틱당 수평 이동량으로 사용하는 단순 매핑을 한다.
 *
 * <p>각 종류는 자연 스폰 cap과 환경을 결정하는 {@link MobCategory}를 가진다.
 * 자연 스폰 category와 행동 적대성은 별개다. WebCraft의 Brown Bear는 CREATURE
 * cap과 주간 배치를 유지하지만 플레이어에게 적대적이다.
 */
public enum MobType {
    ZOMBIE(0, 0.6, 1.95, 20, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    BABY_ZOMBIE(1, 0.3, 0.975, 20, 2.0, 0.0, 0.345, MobCategory.MONSTER),
    ZOMBIE_PIGMAN(2, 0.6, 1.95, 20, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    PILLAGER(3, 0.6, 1.95, 24, 0.0, 0.0, 0.35, MobCategory.MONSTER),
    ENDERMAN(4, 0.6, 2.9, 40, 0.0, 0.0, 0.30, MobCategory.MONSTER),
    SILVERFISH(5, 0.4, 0.3, 8, 0.0, 0.0, 0.25, MobCategory.MONSTER),
    HUSK(6, 0.6, 1.95, 20, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    DROWNED(7, 0.6, 1.95, 20, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    ZOMBIE_VILLAGER(8, 0.6, 1.95, 20, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    SLIME(9, 1.02, 1.02, 4, 0.0, 0.0, 0.20, MobCategory.MONSTER),
    WITCH(10, 0.6, 1.95, 26, 0.0, 0.0, 0.25, MobCategory.MONSTER),
    STRAY(11, 0.6, 1.99, 20, 0.0, 0.0, 0.25, MobCategory.MONSTER),
    BOGGED(12, 0.6, 1.99, 16, 0.0, 0.0, 0.25, MobCategory.MONSTER),
    CAVE_SPIDER(13, 0.7, 0.5, 12, 0.0, 0.0, 0.30, MobCategory.MONSTER),
    BEE(14, 0.7, 0.6, 10, 0.0, 0.0, 0.30, MobCategory.CREATURE),
    SPIDER(15, 1.4, 0.9, 16, 0.0, 0.0, 0.30, MobCategory.MONSTER),
    SKELETON(16, 0.6, 1.99, 20, 0.0, 0.0, 0.25, MobCategory.MONSTER),
    CREEPER(17, 0.6, 1.7, 20, 0.0, 0.0, 0.25, MobCategory.MONSTER),

    // [FARM-VARIANT] 소의 variantDistribution 은 **성별 가중표**다(75% 암소). 1.21.5 기후
    // 변종은 이 표를 한 글자도 바꾸지 않고 뽑힌 성별 뒤에 접미사로 붙는다 —
    // 어휘 전체와 합성 규칙은 FarmAnimalVariantRules.COW_VARIANTS 가 소유하고
    // acceptsVariant 만 그쪽으로 위임한다.
    COW(18, 0.9, 1.4, 10, 0.0, 0.0, 0.20, MobCategory.CREATURE,
            "male", "female", "female", "female"),
    // [FARM-VARIANT] 1.21.5 `minecraft:pig_variant` / `chicken_variant`. 선언 순서 첫 항목이
    // 기본값이자 도입 전 세이브의 마이그레이션 값이다(«Pig»: "a temperate pig is spawned").
    // 이 표는 deterministicVariant 로 뽑히지 않는다 — 스폰 바이옴이 정하고, 바이옴을 모르는
    // 경로는 legacyVariant()(= temperate)로 떨어진다.
    PIG(19, 0.9, 0.9, 10, 0.0, 0.0, 0.25, MobCategory.CREATURE,
            "temperate", "warm", "cold"),
    SHEEP(20, 0.9, 1.3, 8, 0.0, 0.0, 0.23, MobCategory.CREATURE),
    CHICKEN(21, 0.4, 0.7, 4, 0.0, 0.0, 0.25, MobCategory.CREATURE,
            "temperate", "warm", "cold"),
    RABBIT(22, 0.4, 0.5, 3, 0.0, 0.0, 0.30, MobCategory.CREATURE,
            "brown", "brown", "brown", "brown", "brown",
            "salt", "salt", "salt", "salt", "black",
            "white", "white_splotched", "gold",
            "cream", "cinnamon", "blue_gray", "harlequin"),
    BAT(23, 0.5, 0.9, 6, 0.0, 0.0, 0.30, MobCategory.AMBIENT),
    SQUID(24, 0.8, 0.8, 10, 0.0, 0.0, 0.18, MobCategory.WATER_CREATURE),
    GLOW_SQUID(25, 0.8, 0.8, 10, 0.0, 0.0, 0.18,
            MobCategory.UNDERGROUND_WATER_CREATURE),
    COD(26, 0.5, 0.3, 3, 0.0, 0.0, 0.12, MobCategory.WATER_AMBIENT),
    SALMON(27, 0.7, 0.4, 3, 0.0, 0.0, 0.12, MobCategory.WATER_AMBIENT),
    AXOLOTL(28, 0.75, 0.42, 14, 0.0, 0.0, 0.10, MobCategory.AXOLOTLS,
            "lucy", "wild", "gold", "cyan", "blue"),
    IRON_GOLEM(29, 1.4, 2.7, 100, 0.0, 0.0, 0.25, MobCategory.MISC),
    // 신규 종류는 뒤에만 추가한다. stableId는 프로토콜·영속 정체성이므로 기존 값을 바꾸지 않는다.
    // 변종 순서는 Java 1.21.4 TropicalFish.COMMON_VARIANTS 배열 그대로다(자연 스폰이 그 배열을
    // nextInt(22)로 뽑으므로 순서가 RNG 정체성이다). 무늬/색 해석은 프로토콜 정본인
    // client/src/entities/mobs/TropicalFishVariants.ts 가 소유한다.
    TROPICAL_FISH(30, 0.5, 0.4, 3, 0.0, 0.0, 0.12, MobCategory.WATER_AMBIENT,
            "anemone", "black_tang", "blue_tang", "butterflyfish", "cichlid", "clownfish",
            "cotton_candy_betta", "dottyback", "emperor_red_snapper", "goatfish",
            "moorish_idol", "ornate_butterflyfish", "parrotfish", "queen_angelfish",
            "red_cichlid", "red_lipped_blenny", "red_snapper", "threadfin",
            "tomato_clownfish", "triggerfish", "yellowtail_parrotfish", "yellow_tang"),
    PIGLIN(31, 0.6, 1.95, 16, 0.0, 0.0, 0.35, MobCategory.MONSTER),
    ZOMBIFIED_PIGLIN(32, 0.6, 1.95, 20, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    VILLAGER(33, 0.6, 1.95, 20, 0.0, 0.0, 0.5, MobCategory.MISC),
    PIGMAN(34, 0.6, 1.95, 20, 2.0, 0.0, 0.30, MobCategory.MONSTER),
    VINDICATOR(35, 0.6, 1.95, 24, 0.0, 0.0, 0.35, MobCategory.MONSTER),
    EVOKER(36, 0.6, 1.95, 24, 0.0, 0.0, 0.50, MobCategory.MONSTER),
    ILLUSIONER(37, 0.6, 1.95, 32, 0.0, 0.0, 0.50, MobCategory.MONSTER),
    RAVAGER(38, 1.95, 2.20, 100, 0.0, 0.0, 0.30, MobCategory.MONSTER),
    VEX(39, 0.4, 0.8, 14, 0.0, 0.0, 0.70, MobCategory.MONSTER),
    STANDARD_BEARER(40, 0.6, 1.95, 28, 2.0, 0.0, 0.32, MobCategory.MONSTER),
    WEB_TRAPPER(41, 0.6, 1.95, 20, 0.0, 0.0, 0.34, MobCategory.MONSTER),
    BREACHER(42, 0.6, 1.95, 32, 4.0, 0.0, 0.38, MobCategory.MONSTER),
    DEMOLISHER(43, 0.6, 1.95, 26, 3.0, 0.0, 0.30, MobCategory.MONSTER),
    BUILDER(44, 0.6, 1.95, 24, 2.0, 0.0, 0.32, MobCategory.MONSTER),
    ARMADILLO(45, 0.7, 0.65, 12, 0.0, 0.0, 0.14, MobCategory.CREATURE),
    DOLPHIN(46, 0.9, 0.6, 10, 0.0, 0.0, 1.20, MobCategory.WATER_CREATURE),
    BROWN_BEAR(47, 1.4, 1.5, 30, 2.0, 0.0, 0.12, MobCategory.CREATURE),
    BRIARBACK(48, 0.95, 1.05, 28, 2.0, 0.0, 0.68, MobCategory.MONSTER),
    GLOAMKITE(49, 1.2, 0.55, 16, 0.0, 0.0, 0.72, MobCategory.MONSTER),
    DONKEY(50, 1.3964844, 1.5, 53, 0.0, 0.0, 0.174999997, MobCategory.CREATURE),
    FOX(51, 0.6, 0.7, 10, 0.0, 0.0, 0.300000012, MobCategory.CREATURE,
            "red", "snow"),
    FROG(52, 0.5, 0.5, 10, 0.0, 0.0, 1.0, MobCategory.CREATURE,
            "temperate", "warm", "cold"),
    GOAT(53, 0.9, 1.3, 10, 0.0, 0.0, 0.200000003, MobCategory.CREATURE),
    HORSE(54, 1.3964844, 1.6, 53, 0.0, 0.0, 0.224999994, MobCategory.CREATURE,
            "white_none", "white_white", "white_white_field", "white_white_dots", "white_black_dots",
            "creamy_none", "creamy_white", "creamy_white_field", "creamy_white_dots", "creamy_black_dots",
            "chestnut_none", "chestnut_white", "chestnut_white_field", "chestnut_white_dots", "chestnut_black_dots",
            "brown_none", "brown_white", "brown_white_field", "brown_white_dots", "brown_black_dots",
            "black_none", "black_white", "black_white_field", "black_white_dots", "black_black_dots",
            "gray_none", "gray_white", "gray_white_field", "gray_white_dots", "gray_black_dots",
            "dark_brown_none", "dark_brown_white", "dark_brown_white_field", "dark_brown_white_dots", "dark_brown_black_dots"),
    LLAMA(55, 0.9, 1.87, 53, 0.0, 0.0, 0.174999997, MobCategory.CREATURE,
            "creamy", "white", "brown", "gray"),
    MOOSHROOM(56, 0.9, 1.4, 10, 0.0, 0.0, 0.2, MobCategory.CREATURE,
            "red", "brown"),
    OCELOT(57, 0.6, 0.7, 10, 0.0, 0.0, 0.300000012, MobCategory.CREATURE),
    // [MOB-LOOK] 바닐라 Panda.Gene 표시 변종(주·숨은 유전자 16×16 굴림, PandaGenes). 도입 전 행은 normal.
    PANDA(58, 1.3, 1.25, 20, 0.0, 0.0, 0.150000006, MobCategory.CREATURE,
            PandaGenes.variantDistribution()),
    PARROT(59, 0.5, 0.9, 6, 0.0, 0.0, 0.400000006, MobCategory.CREATURE,
            "red_blue", "blue", "green", "yellow_blue", "gray"),
    POLAR_BEAR(60, 1.4, 1.4, 30, 0.0, 0.0, 0.25, MobCategory.CREATURE),
    PUFFERFISH(61, 0.7, 0.7, 3, 0.0, 0.0, 0.12, MobCategory.WATER_AMBIENT),
    TURTLE(62, 1.2, 0.4, 30, 0.0, 0.0, 0.25, MobCategory.CREATURE),
    WOLF(63, 0.6, 0.85, 8, 0.0, 0.0, 0.300000012, MobCategory.CREATURE,
            "pale", "spotted", "snowy", "black", "ashen", "rusty", "woods",
            "chestnut", "striped"),
    MULE(64, 1.3964844, 1.6, 53, 0.0, 0.0, 0.174999997, MobCategory.CREATURE),
    TADPOLE(65, 0.4, 0.3, 6, 0.0, 0.0, 1.0, MobCategory.CREATURE),
    COPPER_GOLEM(66, 0.49, 0.98, 12, 0.0, 0.0, 0.200000003, MobCategory.MISC),
    NAUTILUS(67, 0.875, 0.95, 15, 0.0, 0.0, 1.0, MobCategory.WATER_CREATURE),
    ZOMBIE_NAUTILUS(68, 0.875, 0.95, 15, 0.0, 0.0, 1.100000024, MobCategory.MONSTER),
    // [GUARDIAN] MC Java 1.21.4 `Guardian` / `ElderGuardian`.
    // Guardian     : width 0.85 · height 0.85 · MAX_HEALTH 30 · MOVEMENT_SPEED 0.5 · ATTACK_DAMAGE 6
    // ElderGuardian: Guardian AABB × ELDER_SIZE_SCALE 2.35 → 1.9975 · MAX_HEALTH 80 ·
    //                MOVEMENT_SPEED 0.3 (Guardian 0.5 × 0.6) · ATTACK_DAMAGE 8
    // 방어도·방어구 관통 저항은 둘 다 0(바닐라 attribute 미등록)이라 armor/toughness 는 0 이다.
    GUARDIAN(69, 0.85, 0.85, 30, 0.0, 0.0, 0.5, MobCategory.MONSTER),
    ELDER_GUARDIAN(70, 1.9975, 1.9975, 80, 0.0, 0.0, 0.3, MobCategory.MONSTER),
    // Java 1.21.4 Allay: 0.35×0.6 AABB, 20 HP, flying speed attribute 0.1.
    // Allays are MISC and have no ordinary natural-spawn entry.
    ALLAY(71, 0.35, 0.6, 20, 0.0, 0.0, 0.1, MobCategory.MISC),
    // Java 1.21.4 Camel: EntityType.CAMEL sized(1.7F, 2.375F), createAttributes 의
    // MAX_HEALTH 32.0 · MOVEMENT_SPEED 0.09F. 방어도 attribute 는 없어 0 이다.
    // 수치 정본과 근거는 CamelRules 가 소유한다.
    CAMEL(72, CamelRules.WIDTH, CamelRules.HEIGHT, CamelRules.MAX_HEALTH, 0.0, 0.0,
            CamelRules.MOVEMENT_SPEED, MobCategory.CREATURE),
    // WebCraft 창작몹: 부패한 갈색곰. 바닐라 대응이 없으므로 수치 정본과 그 근거는
    // ZombieBearRules 가 소유한다(서술 정본은 docs/MC-REFERENCE.md 「WebCraft 창작몹」).
    // 치수는 BROWN_BEAR 와 글자 그대로 같고, MONSTER cap·밤 스폰을 쓰는 적대 종이다.
    ZOMBIE_BEAR(73, ZombieBearRules.WIDTH, ZombieBearRules.HEIGHT, ZombieBearRules.MAX_HEALTH,
            ZombieBearRules.ARMOR, ZombieBearRules.TOUGHNESS,
            ZombieBearRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    // 좀비 말. 26.3-snapshot-7 에서는 평원 계열 MONSTER 표에 자연 스폰하고,
    // EntityTypes 등록 category 도 MONSTER 다. 개체 수치와 말 생애주기는 ZombieHorseRules
    // 계약을 유지하며, category 는 적대성 여부와 독립적이다.
    ZOMBIE_HORSE(74, ZombieHorseRules.WIDTH, ZombieHorseRules.HEIGHT,
            (int) ZombieHorseRules.MAX_HEALTH, ZombieHorseRules.ARMOR,
            ZombieHorseRules.TOUGHNESS, ZombieHorseRules.MOVEMENT_SPEED, MobCategory.MONSTER),
    // WebCraft 창작몹: 부패한 늑대. 바닐라 대응이 없으므로 수치 정본과 그 근거는
    // ZombieWolfRules 가 소유한다(서술 정본은 docs/MC-REFERENCE.md 「WebCraft 좀비 동물」).
    // 치수는 WOLF 와 글자 그대로 같고, MONSTER cap·밤 스폰을 쓰는 적대 종이다.
    ZOMBIE_WOLF(75, ZombieWolfRules.WIDTH, ZombieWolfRules.HEIGHT, ZombieWolfRules.MAX_HEALTH,
            ZombieWolfRules.ARMOR, ZombieWolfRules.TOUGHNESS,
            ZombieWolfRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    // ── 좀비 동물 10종(stableId 76~85) ───────────────────────────────────────────────
    // 공통 계약은 UndeadAnimalRules 가 소유하고 종별 수치·근거는 각 *Rules 클래스가 갖는다.
    // 좀비화 6종(76~81)은 원본 동물의 AABB·눈높이를 글자 그대로 물려받고 MONSTER cap·밤 스폰을
    // 쓰는 적대 종이다. 원본 종(COW·PIG·SHEEP·GOAT·FOX·CHICKEN)은 CREATURE 인 채로 남는다.
    ZOMBIE_COW(76, ZombieCowRules.WIDTH, ZombieCowRules.HEIGHT, ZombieCowRules.MAX_HEALTH,
            ZombieCowRules.ARMOR, ZombieCowRules.TOUGHNESS,
            ZombieCowRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    ZOMBIE_PIG(77, ZombiePigRules.WIDTH, ZombiePigRules.HEIGHT, ZombiePigRules.MAX_HEALTH,
            ZombiePigRules.ARMOR, ZombiePigRules.TOUGHNESS,
            ZombiePigRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    ZOMBIE_SHEEP(78, ZombieSheepRules.WIDTH, ZombieSheepRules.HEIGHT, ZombieSheepRules.MAX_HEALTH,
            ZombieSheepRules.ARMOR, ZombieSheepRules.TOUGHNESS,
            ZombieSheepRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    ZOMBIE_GOAT(79, ZombieGoatRules.WIDTH, ZombieGoatRules.HEIGHT, ZombieGoatRules.MAX_HEALTH,
            ZombieGoatRules.ARMOR, ZombieGoatRules.TOUGHNESS,
            ZombieGoatRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    ZOMBIE_FOX(80, ZombieFoxRules.WIDTH, ZombieFoxRules.HEIGHT, ZombieFoxRules.MAX_HEALTH,
            ZombieFoxRules.ARMOR, ZombieFoxRules.TOUGHNESS,
            ZombieFoxRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    ZOMBIE_CHICKEN(81, ZombieChickenRules.WIDTH, ZombieChickenRules.HEIGHT,
            ZombieChickenRules.MAX_HEALTH, ZombieChickenRules.ARMOR,
            ZombieChickenRules.TOUGHNESS, ZombieChickenRules.IDLE_BLOCKS_PER_TICK,
            MobCategory.MONSTER),
    // 창작 3종. 무덤 사슴은 도망가다 반격하는 적대 종이라 MONSTER cap 을 쓰고,
    // 시체 까마귀는 공격하지 않는 비행 신호 종이라 박쥐와 같은 AMBIENT cap 을 쓴다
    // (AMBIENT 는 passive() 도 hostile() 도 아니다 — 이 종의 계약과 정확히 맞는다).
    CARRION_STAG(82, CarrionStagRules.WIDTH, CarrionStagRules.HEIGHT,
            CarrionStagRules.MAX_HEALTH, CarrionStagRules.ARMOR, CarrionStagRules.TOUGHNESS,
            CarrionStagRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    CARRION_BOAR(83, CarrionBoarRules.WIDTH, CarrionBoarRules.HEIGHT,
            CarrionBoarRules.MAX_HEALTH, CarrionBoarRules.ARMOR, CarrionBoarRules.TOUGHNESS,
            CarrionBoarRules.IDLE_BLOCKS_PER_TICK, MobCategory.MONSTER),
    CARRION_CROW(84, CarrionCrowRules.WIDTH, CarrionCrowRules.HEIGHT,
            CarrionCrowRules.MAX_HEALTH, CarrionCrowRules.ARMOR, CarrionCrowRules.TOUGHNESS,
            CarrionCrowRules.FLY_BLOCKS_PER_TICK, MobCategory.AMBIENT),
    // 낙타 husk 는 바닐라 1.21.11 실존 종이다(수치 등급 A). 기수 계약이 아직 없어 단독
    // 개체는 선공하지 않으므로 좀비 말과 같은 CREATURE cap 을 쓴다 — 근거는 CamelHuskRules.
    CAMEL_HUSK(85, CamelHuskRules.WIDTH, CamelHuskRules.HEIGHT, CamelHuskRules.MAX_HEALTH,
            CamelHuskRules.ARMOR, CamelHuskRules.TOUGHNESS, CamelHuskRules.MOVEMENT_SPEED,
            MobCategory.CREATURE),
    // ── 신종 12종(stableId 86~97) ────────────────────────────────────────────────────
    // 이 웨이브는 **코어 등록**이다: 치수·체력·속도·category·스폰 계약·드랍·영속을 세우고,
    // 모델·사운드·변종은 다음 단계다. 등급 A(바닐라 원문)인 종은 여기 인라인 주석이 근거를
    // 갖고, 등급 B/C(리서치·자체 계약)인 두 종만 *Rules 클래스를 따로 둔다.
    //
    // MC Java 1.21.4 `Cat`: EntityType.CAT sized(0.6F, 0.7F) · Cat.createAttributes()
    //   = Animal.createAnimalAttributes().add(MAX_HEALTH, 10.0).add(MOVEMENT_SPEED, 0.3)
    //     .add(ATTACK_DAMAGE, 3.0). MobCategory.CREATURE.
    // 크리퍼 회피는 바닐라 `Creeper#registerGoals` 의
    //   `new AvoidEntityGoal<>(this, Cat.class, 6.0F, 1.0, 1.2)` 가 근거다(6블록).
    // [CAT-VARIANT] `minecraft:cat_variant` 어휘 11칸. 순서는 핀 고정 26.3-snapshot-7 이너 JAR 의
    //   net/minecraft/world/entity/animal/feline/CatVariants.class
    //   (sha256 8c7108c75f7e7a82082dfa029c65cd100d2febe11b400201507bb6362c5b2043) bootstrap
    //   등록 순서 그대로다. 자연 스폰이 이 표를 nextInt(11)로 뽑으므로 순서 자체가 RNG 정체성이며,
    //   마을 엔티티 증거(Mc263VillageEntityAuthority)와 프로토콜 어휘가 같은 순서를 요구한다.
    CAT(86, 0.6, 0.7, 10, 0.0, 0.0, 0.3, MobCategory.CREATURE,
            "tabby", "black", "red", "siamese", "british_shorthair", "calico", "persian",
            "ragdoll", "white", "jellie", "all_black"),
    // MC Java 1.21.4 `WanderingTrader`: EntityType.WANDERING_TRADER sized(0.6F, 1.95F) ·
    //   createAttributes() = Mob.createMobAttributes().add(MAX_HEALTH, 20.0)
    //   .add(MOVEMENT_SPEED, 0.5). MobCategory.CREATURE.
    WANDERING_TRADER(87, 0.6, 1.95, 20, 0.0, 0.0, 0.5, MobCategory.CREATURE),
    // MC Java 1.21.4 `TraderLlama extends Llama`: 별도 AABB·attribute override 가 없어
    //   라마와 글자 그대로 같다(EntityType.TRADER_LLAMA sized(0.9F, 1.87F),
    //   AbstractHorse.createBaseHorseAttributes() MAX_HEALTH 53 · MOVEMENT_SPEED 0.175).
    //   변종 4종(creamy/white/brown/gray)은 이 웨이브 밖이다 — 변종은 다음 단계.
    TRADER_LLAMA(88, 0.9, 1.87, 53, 0.0, 0.0, 0.174999997, MobCategory.CREATURE),
    // MC Java 1.21.4 `SkeletonHorse`: EntityType.SKELETON_HORSE sized(1.3964844F, 1.6F) ·
    //   createAttributes() = createBaseHorseAttributes().add(MAX_HEALTH, 15.0)
    //   .add(MOVEMENT_SPEED, 0.2). MobCategory.CREATURE — 바닐라도 자연 스폰 항목은 없고
    //   뇌우 트랩(`SkeletonHorse#setTrap`)으로만 나온다. 안장 없이 탄다
    //   (`AbstractHorse#isSaddleable` 이 SkeletonHorse 에서 true 로 남는다).
    SKELETON_HORSE(89, 1.3964844, 1.6, 15, 0.0, 0.0, 0.2, MobCategory.CREATURE),
    // MC Java 1.21.4 `Phantom`: EntityType.PHANTOM sized(0.9F, 0.5F) · createAttributes()
    //   = Mob.createMobAttributes().add(ATTACK_DAMAGE, 6.0). MOVEMENT_SPEED override 가
    //   없어 `Attributes.MOVEMENT_SPEED` 기본값 0.7 이 남는다(팬텀의 실제 활공은
    //   moveTargetPoint 벡터가 만든다 — WebCraft 는 그 벡터를 아직 옮기지 않았다).
    PHANTOM(90, 0.9, 0.5, 20, 0.0, 0.0, 0.7, MobCategory.MONSTER),
    // 파치드(1.21.11 "Mounts of Mayhem"). 수치·근거·divergence 는 ParchedRules 소유.
    // [PARCHED-FAMILY] 허스크 사본(20 · 2.0 · 0.23 · 1.95)에서 **스켈레톤 변종** 원문
    //   (16 · 0 · 0.25 · 1.99)으로 정정했다. 스켈레톤·스트레이가 0.6 × 1.99 인 것과 같은 줄에
    //   서고 체력만 보그드와 같은 16 이다.
    PARCHED(91, ParchedRules.WIDTH, ParchedRules.HEIGHT, ParchedRules.MAX_HEALTH,
            ParchedRules.ARMOR, ParchedRules.TOUGHNESS, ParchedRules.MOVEMENT_SPEED,
            MobCategory.MONSTER),
    // MC Java 1.21.4 `SnowGolem`: EntityType.SNOW_GOLEM sized(0.7F, 1.9F) ·
    //   createAttributes() = Mob.createMobAttributes().add(MAX_HEALTH, 4.0)
    //   .add(MOVEMENT_SPEED, 0.2). MobCategory.MISC — 자연 스폰이 없고 제작 소환만 있다.
    SNOW_GOLEM(92, 0.7, 1.9, 4, 0.0, 0.0, 0.2, MobCategory.MISC),
    // MC Java 1.21.4 `Sniffer`: EntityType.SNIFFER sized(1.9F, 1.75F) · createAttributes()
    //   = Animal.createAnimalAttributes().add(MOVEMENT_SPEED, 0.1).add(MAX_HEALTH, 14.0).
    //   MobCategory.CREATURE(자연 스폰 항목은 없고 부화로만 늘어난다).
    SNIFFER(93, 1.9, 1.75, 14, 0.0, 0.0, 0.1, MobCategory.CREATURE),
    // MC Java 1.21.4 `Breeze`: EntityType.BREEZE sized(0.6F, 1.77F) · createAttributes()
    //   = Monster.createMonsterAttributes().add(MOVEMENT_SPEED, 0.63)
    //   .add(MAX_HEALTH, 30.0).add(FOLLOW_RANGE, 24.0). MobCategory.MONSTER —
    //   자연 스폰 항목은 없고 시련 소환기 전용이다.
    BREEZE(94, 0.6, 1.77, 30, 0.0, 0.0, 0.63, MobCategory.MONSTER),
    // MC Java 1.21.6 `HappyGhast`: EntityType.HAPPY_GHAST sized(4.0F, 4.0F) ·
    //   createAttributes() = Mob.createMobAttributes().add(MAX_HEALTH, 20.0)
    //   .add(FLYING_SPEED, 0.05).add(MOVEMENT_SPEED, 0.05).add(FOLLOW_RANGE, 16.0)
    //   .add(CAMERA_DISTANCE, 8.0). MobCategory.CREATURE. 온순해 선공하지 않는다.
    HAPPY_GHAST(95, 4.0, 4.0, 20, 0.0, 0.0, 0.05, MobCategory.CREATURE),
    // MC Java 26.3-snapshot-7 유황 큐브. 수치·근거는 SulfurCubeRules 소유.
    SULFUR_CUBE(96, SulfurCubeRules.WIDTH, SulfurCubeRules.HEIGHT, SulfurCubeRules.MAX_HEALTH,
            SulfurCubeRules.ARMOR, SulfurCubeRules.TOUGHNESS, SulfurCubeRules.MOVEMENT_SPEED,
            MobCategory.MONSTER),
    // MC Java 1.21.4 `Warden`: EntityType.WARDEN sized(0.9F, 2.9F) · createAttributes()
    //   = Monster.createMonsterAttributes().add(MAX_HEALTH, 500.0)
    //   .add(MOVEMENT_SPEED, 0.3).add(KNOCKBACK_RESISTANCE, 1.0)
    //   .add(ATTACK_KNOCKBACK, 1.5).add(ATTACK_DAMAGE, 30.0).add(FOLLOW_RANGE, 24.0).
    //   MobCategory.MONSTER — 자연 스폰 항목은 없고 스컬크 비명체 4단계 경고로만 솟는다.
    WARDEN(97, 0.9, 2.9, 500, 0.0, 0.0, 0.3, MobCategory.MONSTER),
    // [SULFUR] 황린 잠복자. 유황 동굴 지대 고유 **엘리트** 창작몹이라 바닐라 대응이 없다.
    // 수치·근거·divergence 는 BrimstoneLurkerRules 가 소유하고 서술 정본은
    // docs/MC-REFERENCE.md 「황린 잠복자」 절이다. 자연 스폰은 0 이고(기존 확률 전수 불변)
    // 유황 동굴 구조물 훅으로만 나오지만, cap·적대성 축은 다른 적대 종과 같은 MONSTER 다.
    BRIMSTONE_LURKER(98, BrimstoneLurkerRules.WIDTH, BrimstoneLurkerRules.HEIGHT,
            BrimstoneLurkerRules.MAX_HEALTH, BrimstoneLurkerRules.ARMOR,
            BrimstoneLurkerRules.TOUGHNESS, BrimstoneLurkerRules.IDLE_BLOCKS_PER_TICK,
            MobCategory.MONSTER),
    // [CREAKING] 크리킹. **바닐라 실존 종**이며 수치·근거는 CreakingRules 가 소유한다
    // ([B] minecraft.wiki «Creaking», 조회 2026-08-10 — 이 저장소가 고정한 1.21.4 데이터
    // 스냅샷에는 몹 어트리뷰트가 들어 있지 않다). 자연 스폰은 0 이고 — 바닐라도 창백한
    // 정원 바이옴의 spawners 에 크리킹이 없다(핀 §1c) — 크리킹 하트 블록이 밤마다 하나씩
    // 소환한다. 그래서 이 종을 등록해도 어떤 바이옴 스폰 표도, 어떤 기존 종의 상대 확률도
    // 바뀌지 않는다(황린 잠복자가 낸 것과 같은 자리). cap·적대성 축은 MONSTER 다.
    CREAKING(99, CreakingRules.WIDTH, CreakingRules.HEIGHT,
            CreakingRules.MAX_HEALTH, CreakingRules.ARMOR,
            CreakingRules.TOUGHNESS, CreakingRules.MOVE_SPEED,
            MobCategory.MONSTER),
    POISON_DART_FROG(100, PoisonDartFrogRules.WIDTH, PoisonDartFrogRules.HEIGHT,
            PoisonDartFrogRules.MAX_HEALTH, 0.0, 0.0, PoisonDartFrogRules.BASE_SPEED,
            MobCategory.CREATURE, "azure", "golden", "strawberry", "mint"),
    FLESH_STALKER(101, 0.6, 1.95, 36, 2.0, 0.0, 0.23, MobCategory.MONSTER),
    // [DRAGON] 엔더 드래곤. 핀 26.3 EntityType.ENDER_DRAGON sized(16, 8) · MobCategory.MONSTER ·
    // createAttributes MAX_HEALTH 200(이동 속성은 createMobAttributes 기본 0.7). 비행·부위 판정·사망
    // 연출은 {@link EnderDragon}/드래곤전 런타임이 소유하고 MobRuntime 일반 AI 는 이 종을 틱하지 않는다.
    ENDER_DRAGON(102, 16.0, 8.0, 200, 0.0, 0.0, 0.7, MobCategory.MONSTER),
    // [DRAGON] 엔드 수정. 핀 26.3 EntityType.END_CRYSTAL sized(2, 2) · MobCategory.MISC. LivingEntity 가
    // 아니라 체력이 없고 어떤 피해든 파괴된다(체력 1 은 원장 표기). 변종은 DATA_SHOW_BOTTOM 이다.
    END_CRYSTAL(103, 2.0, 2.0, 1, 0.0, 0.0, 0.0, MobCategory.MISC, "show_bottom", "hide_bottom"),
    // [EC-MOBS] 셜커. 핀 26.3 EntityType.SHULKER sized(1, 1) · MobCategory.MONSTER · MAX_HEALTH 30 · 움직이지 않는다
    // (Shulker.getDeltaMovement 0). 닫혀 있으면 방어 +20 (ShulkerRules.COVERED_ARMOR). 엔드 도시 Sentry 표지가 둔다.
    SHULKER(104, ShulkerRules.WIDTH, ShulkerRules.HEIGHT, ShulkerRules.MAX_HEALTH, 0.0, 0.0, 0.0,
            MobCategory.MONSTER),
    // [EC-MOBS] 아이템 액자. 핀 26.3 EntityType.ITEM_FRAME sized(0.5, 0.5) · MobCategory.MISC. 살아 있는 개체가 아니라
    // 체력 1 은 원장 표기이고 명중 상자는 ItemFrameRules.boundingBox(방향별 0.75 × 0.75 × 0.0625)다.
    ITEM_FRAME(105, 0.5, 0.5, 1, 0.0, 0.0, 0.0, MobCategory.MISC),
    GHOUL(106, 0.6, 1.9, 24, 2.0, 0.0, 0.25, MobCategory.MONSTER),
    BABY_GHOUL(107, 0.45, 1.1, 28, 2.0, 0.0, 0.325, MobCategory.MONSTER),
    BONE_PROCESSION(108, 4.5, 2.0, 100, 8.0, 0.0, 0.18, MobCategory.MONSTER),
    HANGING_MAW(109, 1.5, 1.5, 28, 0.0, 0.0, 0.0, MobCategory.MONSTER),
    BOAR(110, 1.0, 1.0, 20, 2.0, 0.0, 0.14, MobCategory.CREATURE),
    GRIZZLY_BEAR(111, 1.9, 2.0, 60, 4.0, 0.0, 0.10, MobCategory.CREATURE);

    private final int stableId;
    private final double width;
    private final double height;
    private final int maxHp;
    private final double armor;
    private final double toughness;
    private final double baseSpeed;
    private final MobCategory category;
    private final String[] variantDistribution;
    // 활성 종 인덱스와 미래 배정 상한은 다르다. 예약을 enum 종으로 등록하지 않는다.
    public static final int RESERVED_FLESH_STALKER_ID = 101;
    public static final int RESERVED_VOID_END_FIRST = 102;
    public static final int RESERVED_VOID_END_LAST = 105;
    public static final int ALLOCATION_HIGH_WATER_MARK = 111;
    public static final int STABLE_ID_HIGH_WATER_MARK = 111;
    private static final MobType[] BY_STABLE_ID = stableIdIndex();
    private static final EnumSet<MobType> BREEDABLE =
            EnumSet.of(BEE, COW, PIG, SHEEP, CHICKEN, RABBIT, AXOLOTL, ARMADILLO,
                    DONKEY, FOX, GOAT, HORSE, LLAMA, MOOSHROOM, OCELOT, PANDA, WOLF, CAMEL,
                    // [TURTLE] 맨 끝 append 다 — 앞의 열여덟은 한 톨도 건드리지 않는다.
                    // 거북은 짝짓기가 끝나도 새끼가 바로 나오지 않고 한쪽이 알을 배는 것이
                    // [A] 인데, 그 갈림은 이 집합이 아니라 산란 경로가 진다.
                    // client StandaloneMobRules.standaloneMobBreedingEnabled 의 같은 append 와 짝이다.
                    TURTLE, FROG, CAT, SNIFFER,
                    // [NAUTILUS-BEHAVIOR] 맨 끝 append. [B] «Nautilus»: 성체는 아무 생선/생선
                    // 양동이로 "bred if at full health" 다. 만피 관문은 이 집합이 아니라
                    // MobSystem#interactNautilusFamily 가 진다. 좀비 노틸러스는 [B] 가
                    // "cannot be bred" 라고 못 박아 여기에 없다.
                    NAUTILUS, POISON_DART_FROG);

    MobType(int stableId, double width, double height, int maxHp, double armor, double toughness,
            double baseSpeed, MobCategory category, String... variantDistribution) {
        this.stableId = stableId;
        this.width = width;
        this.height = height;
        this.maxHp = maxHp;
        this.armor = armor;
        this.toughness = toughness;
        this.baseSpeed = baseSpeed;
        this.category = category;
        this.variantDistribution = variantDistribution;
    }

    /** Append-only protocol/persistence identity. Never derive durable identity from enum order. */
    public int stableId() { return stableId; }

    public static MobType fromStableId(int stableId) {
        if (stableId < 0 || stableId >= BY_STABLE_ID.length) {
            throw new IllegalArgumentException("unknown mob stable ID: " + stableId);
        }
        return BY_STABLE_ID[stableId];
    }

    /** 수평 폭(X·Z 전체 폭, half-width = width/2). */
    public double width() { return width; }
    /** 세로 높이(발밑 y = AABB 바닥 기준). */
    public double height() { return height; }
    public int maxHp() { return maxHp; }
    public double armor() { return armor; }
    public double toughness() { return toughness; }
    /** 지상 이동 속도(블록/틱). */
    public double baseSpeed() { return baseSpeed; }
    /** 공격하지 않는 CREATURE 계열이면 true. Spawn category와 행동 적대성은 별개다. */
    public boolean passive() {
        return category == MobCategory.CREATURE && this != BROWN_BEAR && this != GRIZZLY_BEAR || this == ALLAY;
    }
    public boolean hostile() {
        return this != ZOMBIE_HORSE && (category == MobCategory.MONSTER || this == BROWN_BEAR || this == GRIZZLY_BEAR);
    }
    public boolean naturallyPersistent() {
        return category == MobCategory.CREATURE && this != BROWN_BEAR && this != GRIZZLY_BEAR;
    }
    public MobCategory category() { return category; }

    public boolean breedingEnabled() { return BREEDABLE.contains(this); }

    /** Java Edition의 종별 번식/성장 먹이 중 WebCraft에 실제 존재하는 아이템만 허용한다. */
    public boolean isBreedingFood(short itemType) {
        return switch (this) {
            // 바닐라 Camel#isFood: 선인장 하나뿐이다.
            case CAMEL -> CamelRules.isBreedingFood(itemType);
            case BEE -> itemType == (short) Blocks.FLOWER_RED
                    || itemType == (short) Blocks.FLOWER_YELLOW
                    || itemType == (short) Blocks.FLOWERING_AZALEA
                    || itemType == (short) Blocks.SPORE_BLOSSOM;
            case COW, SHEEP -> itemType == PlayerInventory.WHEAT;
            case PIG -> itemType == PlayerInventory.CARROT
                    || itemType == PlayerInventory.POTATO
                    || itemType == PlayerInventory.BEETROOT;
            case CHICKEN -> itemType == PlayerInventory.WHEAT_SEEDS
                    || itemType == PlayerInventory.PUMPKIN_SEEDS
                    || itemType == PlayerInventory.BEETROOT_SEEDS;
            case RABBIT -> itemType == PlayerInventory.CARROT
                    || itemType == PlayerInventory.GOLDEN_CARROT
                    || itemType == (short) Blocks.FLOWER_YELLOW;
            case AXOLOTL -> itemType == PlayerInventory.TROPICAL_FISH_BUCKET;
            case FROG -> itemType == PlayerInventory.SLIME_BALL;
            case CAT -> itemType == PlayerInventory.COD_RAW
                    || itemType == PlayerInventory.SALMON_RAW;
            case SNIFFER -> itemType == PlayerInventory.TORCHFLOWER_SEEDS;
            case ARMADILLO -> itemType == PlayerInventory.SPIDER_EYE;
            case DONKEY, HORSE -> itemType == PlayerInventory.GOLDEN_CARROT
                    || itemType == PlayerInventory.GOLDEN_APPLE;
            case FOX -> itemType == PlayerInventory.SWEET_BERRIES
                    || itemType == PlayerInventory.GLOW_BERRIES;
            case GOAT, MOOSHROOM -> itemType == PlayerInventory.WHEAT;
            case LLAMA -> itemType == (short) Blocks.HAY_BLOCK;
            case OCELOT -> itemType == PlayerInventory.COD_RAW
                    || itemType == PlayerInventory.SALMON_RAW;
            case PANDA -> itemType == (short) Blocks.BAMBOO;
            case WOLF -> itemType == PlayerInventory.BEEF_RAW
                    || itemType == PlayerInventory.BEEF_COOKED
                    || itemType == PlayerInventory.CHICKEN_RAW
                    || itemType == PlayerInventory.CHICKEN_COOKED
                    || itemType == PlayerInventory.MUTTON_RAW
                    || itemType == PlayerInventory.MUTTON_COOKED
                    || itemType == PlayerInventory.PORK_RAW
                    || itemType == PlayerInventory.PORK_COOKED
                    || itemType == PlayerInventory.RABBIT_RAW
                    || itemType == PlayerInventory.RABBIT_COOKED
                    || itemType == PlayerInventory.ROTTEN_FLESH
                    || itemType == PlayerInventory.COD_RAW
                    || itemType == PlayerInventory.COD_COOKED
                    || itemType == PlayerInventory.SALMON_RAW
                    || itemType == PlayerInventory.SALMON_COOKED
                    || itemType == PlayerInventory.TROPICAL_FISH
                    || itemType == PlayerInventory.PUFFERFISH
                    || itemType == PlayerInventory.RABBIT_STEW;
            // [TURTLE] [A] Turtle.isFood 는 #minecraft:turtle_food 태그 하나이고 그 태그의
            // 유일한 원소가 해초다. 판정 정본은 TurtleEggRules.BREEDING_FOOD 이고 client
            // StandaloneMobRules 의 turtle 갈래와 같은 값이다. 스위치 맨 끝 append 라
            // 앞 갈래의 판정은 그대로다.
            case TURTLE -> itemType == (short) TurtleEggRules.BREEDING_FOOD;
            // [NAUTILUS-BEHAVIOR] [B] «Nautilus»: "any fish or any bucket of fish".
            // 이 저장소에 실재하는 생선 5종과 생선 양동이 4종이 그 집합의 전부다.
            case NAUTILUS -> itemType == PlayerInventory.COD_RAW
                    || itemType == PlayerInventory.COD_COOKED
                    || itemType == PlayerInventory.SALMON_RAW
                    || itemType == PlayerInventory.SALMON_COOKED
                    || itemType == PlayerInventory.TROPICAL_FISH
                    || itemType == PlayerInventory.PUFFERFISH
                    || itemType == PlayerInventory.COD_BUCKET
                    || itemType == PlayerInventory.SALMON_BUCKET
                    || itemType == PlayerInventory.TROPICAL_FISH_BUCKET
                    || itemType == PlayerInventory.PUFFERFISH_BUCKET;
            case POISON_DART_FROG -> itemType == PlayerInventory.SPIDER_EYE;
            default -> false;
        };
    }

    public boolean canBreedWith(MobType other) {
        if (!breedingEnabled() || !other.breedingEnabled()) return false;
        return this == other || (this == HORSE && other == DONKEY)
                || (this == DONKEY && other == HORSE);
    }

    /** Vanilla horse-family cross: Horse + Donkey always produces the infertile Mule. */
    public MobType breedingOffspringType(MobType other) {
        if (!canBreedWith(other)) throw new IllegalArgumentException("incompatible breeding pair");
        // Frog breeding settles a FROGSPAWN block through MobRuntime's placement request lane;
        // this return value is only a type-level compatibility fact and never spawns a child.
        if (this == FROG) return FROG;
        return this == other ? this : MULE;
    }

    /** 이 종류가 서버 권위 변종 값을 요구하는지 여부. */
    public boolean hasVariants() { return variantDistribution.length > 0; }

    /** 선언된 변종 개수. 열대어처럼 균등 분포를 인덱스로 뽑는 자연 스폰이 쓴다. */
    public int variantCount() { return variantDistribution.length; }

    /** 선언 순서의 변종 이름. 순서는 프로토콜/RNG 정체성이므로 바꾸지 않는다. */
    public String variantAt(int index) { return variantDistribution[index]; }

    /**
     * 이 종류가 나중에 변종을 갖게 되기 전에 저장된 행의 마이그레이션 값.
     * 변종이 없던 시절의 null 을 선언 순서 첫 변종으로 승격해 오래된 월드를 계속 읽는다.
     */
    public String legacyVariant() { return hasVariants() ? variantDistribution[0] : null; }

    /** 프로토콜 변종 문자열이 이 몹 종류에 선언된 값인지 검사한다. */
    public boolean acceptsVariant(String variant) {
        // 기존 보통 변종 null을 보존하고 따뜻한 바다 산호 변종만 추가한다.
        if (this == ZOMBIE_NAUTILUS) return variant == null || "warm".equals(variant);
        // [FARM-VARIANT] 소만 어휘와 추첨표가 다르다. 추첨표는 성별 4칸 가중표이고 어휘는
        // 성별×기후 6칸이라, 어휘 검사는 FarmAnimalVariantRules 가 갖는다.
        if (this == COW) return FarmAnimalVariantRules.acceptsCowVariant(variant);
        if (variant == null) return !hasVariants();
        for (String candidate : variantDistribution) if (candidate.equals(variant)) return true;
        return false;
    }

    /** 좌표 해시로 변종 분포를 선택한다. RNG를 소비하지 않으며 변종이 없으면 null이다. */
    public String deterministicVariant(int worldSeed, long mobId, double spawnX, double spawnZ) {
        // [FARM-VARIANT] 돼지·닭의 기후는 **스폰 바이옴**이 정본이라 좌표 해시로 뽑으면 안 된다.
        // 바이옴을 모르는 호출자는 바닐라와 같이 temperate 로 떨어진다("unlisted biome →
        // temperate"). 소는 이 표가 성별 가중표라 그대로 뽑는다.
        if (this == PIG || this == CHICKEN) return legacyVariant();
        if (!hasVariants()) return null;
        int index = MobVariant.deterministicIndex(
                worldSeed, mobId, spawnX, spawnZ, variantDistribution.length);
        return variantDistribution[index];
    }

    private static MobType[] stableIdIndex() {
        MobType[] index = new MobType[STABLE_ID_HIGH_WATER_MARK + 1];
        for (MobType type : values()) {
            if (type.stableId < 0 || type.stableId >= index.length
                    || index[type.stableId] != null) {
                throw new ExceptionInInitializerError("invalid mob stable ID: " + type);
            }
            index[type.stableId] = type;
        }
        for (int stableId = 0; stableId < index.length; stableId++) {
            if (index[stableId] == null) {
                throw new ExceptionInInitializerError("missing mob stable ID: " + stableId);
            }
        }
        return index;
    }
}
