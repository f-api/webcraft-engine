package com.gameexpert.engine.mob;

/** 종류·ID·좌표로 몹 인스턴스 생성. 종별 구현 트랙은 각 전용 클래스만 채운다. */
public final class MobFactory {
    private MobFactory() {}

    public static Mob create(MobType type, long id, double x, double y, double z) {
        return create(type, id, x, y, z, 0, false);
    }

    static Mob create(MobType type, long id, double x, double y, double z,
                      int worldSeed, boolean firstCowInHerd) {
        return create(type, id, x, y, z, worldSeed, firstCowInHerd, null);
    }

    static Mob create(MobType type, long id, double x, double y, double z,
                      int worldSeed, boolean firstCowInHerd, String requestedVariant) {
        return switch (type) {
            case FLESH_STALKER -> new FleshStalker(id, x, y, z);
            case GHOUL -> new Ghoul(id, x, y, z, false);
            case BABY_GHOUL -> new Ghoul(id, x, y, z, true);
            case BONE_PROCESSION -> new BoneProcession(id, x, y, z);
            case HANGING_MAW -> new HangingMaw(id, x, y, z);
            case ENDER_DRAGON -> new EnderDragon(id, x, y, z);
            case END_CRYSTAL -> new EndCrystal(id, x, y, z, requestedVariant);
            case SHULKER -> new Shulker(id, x, y, z);
            case ITEM_FRAME -> new ItemFrame(id, x, y, z);
            case ZOMBIE -> new Zombie(id, x, y, z);
            case BABY_ZOMBIE -> new BabyZombie(id, x, y, z);
            case ZOMBIE_PIGMAN -> new ZombiePigman(id, x, y, z);
            case PILLAGER -> new Pillager(id, x, y, z);
            case ENDERMAN -> new Enderman(id, x, y, z);
            case SILVERFISH -> new Silverfish(id, x, y, z);
            case HUSK -> new Husk(id, x, y, z);
            case DROWNED -> new Drowned(id, x, y, z);
            case ZOMBIE_VILLAGER -> new ZombieVillager(id, x, y, z);
            case SLIME -> new Slime(id, x, y, z);
            case WITCH -> new Witch(id, x, y, z);
            case STRAY -> new Stray(id, x, y, z);
            case BOGGED -> new Bogged(id, x, y, z);
            case CAVE_SPIDER -> new CaveSpider(id, x, y, z);
            case BEE -> new Bee(id, x, y, z);
            case SPIDER -> new Spider(id, x, y, z);
            case SKELETON -> new Skeleton(id, x, y, z);
            case CREEPER -> new Creeper(id, x, y, z);
            // [FARM-VARIANT] 소·돼지·닭은 스포너가 **기후 낱말**을 requestedVariant 로 준다.
            // 소는 성별을 여기서(기존 가중표 그대로) 뽑고 기후를 뒤에 합성하고, 돼지·닭은
            // 기후가 곧 변종이다. 바이옴을 모르는 경로(스폰 알·명령·테스트)는 temperate 다.
            case COW -> new AnimalMob(type, id, x, y, z,
                    FarmAnimalVariantRules.resolveCowVariant(
                            firstCowInHerd ? CowSex.FEMALE.protocolName()
                                    : type.deterministicVariant(worldSeed, id, x, z),
                            requestedVariant));
            case PIG -> new Pig(id, x, y, z, farmAnimalClimate(type, requestedVariant));
            case SHEEP -> new Sheep(id, x, y, z);
            case CHICKEN -> new Chicken(id, x, y, z, farmAnimalClimate(type, requestedVariant));
            case RABBIT -> new Rabbit(id, x, y, z,
                    requestedVariant == null
                            ? type.deterministicVariant(worldSeed, id, x, z)
                            : requestedVariant);
            case BAT -> new Bat(id, x, y, z);
            case SQUID -> new Squid(id, x, y, z);
            case GLOW_SQUID -> new GlowSquid(id, x, y, z);
            case COD -> new Cod(id, x, y, z);
            case SALMON -> new Salmon(id, x, y, z);
            case TROPICAL_FISH -> new TropicalFish(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
            case PIGLIN -> new Piglin(id, x, y, z);
            case ZOMBIFIED_PIGLIN -> new ZombifiedPiglin(id, x, y, z);
            case VILLAGER -> new Villager(id, x, y, z);
            case PIGMAN -> new Pigman(id, x, y, z);
            case VINDICATOR -> new Vindicator(id, x, y, z);
            case EVOKER -> new Evoker(id, x, y, z);
            case ILLUSIONER -> new Illusioner(id, x, y, z);
            case RAVAGER -> new Ravager(id, x, y, z);
            case VEX -> new Vex(id, x, y, z);
            case STANDARD_BEARER -> new StandardBearer(id, x, y, z);
            case WEB_TRAPPER -> new WebTrapper(id, x, y, z);
            case BREACHER -> new Breacher(id, x, y, z);
            case DEMOLISHER -> new Demolisher(id, x, y, z, worldSeed);
            case BUILDER -> new Builder(id, x, y, z);
            case AXOLOTL -> new Axolotl(id, x, y, z,
                    requestedVariant == null
                            ? type.deterministicVariant(worldSeed, id, x, z)
                            : requestedVariant);
            case IRON_GOLEM -> new IronGolem(id, x, y, z);
            case ARMADILLO -> new Armadillo(id, x, y, z, worldSeed);
            case DOLPHIN -> new Dolphin(id, x, y, z);
            case BROWN_BEAR -> new BrownBear(id, x, y, z);
            case BRIARBACK -> new Briarback(id, x, y, z);
            case GLOAMKITE -> new Gloamkite(id, x, y, z);
            case DONKEY -> new Donkey(id, x, y, z);
            case FOX -> new Fox(id, x, y, z, variant(type, requestedVariant, worldSeed, id, x, z));
            case FROG -> new Frog(id, x, y, z, variant(type, requestedVariant, worldSeed, id, x, z));
            case GOAT -> new Goat(id, x, y, z);
            case HORSE -> new Horse(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
            case LLAMA -> new Llama(id, x, y, z, variant(type, requestedVariant, worldSeed, id, x, z));
            case MOOSHROOM -> new Mooshroom(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
            case OCELOT -> new Ocelot(id, x, y, z);
            case PANDA -> new Panda(id, x, y, z, variant(type, requestedVariant, worldSeed, id, x, z));
            case PARROT -> new Parrot(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
            case POLAR_BEAR -> new PolarBear(id, x, y, z);
            case PUFFERFISH -> new Pufferfish(id, x, y, z);
            case TURTLE -> new Turtle(id, x, y, z);
            case WOLF -> new Wolf(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
            case MULE -> new Mule(id, x, y, z);
            case TADPOLE -> new Tadpole(id, x, y, z);
            case COPPER_GOLEM -> new CopperGolem(id, x, y, z);
            case NAUTILUS -> new Nautilus(id, x, y, z);
            case ZOMBIE_NAUTILUS -> new ZombieNautilus(id, x, y, z, requestedVariant);
            case GUARDIAN -> new Guardian(id, x, y, z);
            case ELDER_GUARDIAN -> new ElderGuardian(id, x, y, z);
            case ALLAY -> new Allay(id, x, y, z);
            case CAMEL -> new Camel(id, x, y, z);
            case ZOMBIE_BEAR -> new ZombieBear(id, x, y, z);
            case ZOMBIE_HORSE -> new ZombieHorse(id, x, y, z);
            case ZOMBIE_WOLF -> new ZombieWolf(id, x, y, z);
            // 좀비 동물 10종(stableId 76~85).
            case ZOMBIE_COW -> new ZombieFarmAnimals.ZombieCow(id, x, y, z);
            case ZOMBIE_PIG -> new ZombieFarmAnimals.ZombiePig(id, x, y, z);
            case ZOMBIE_SHEEP -> new ZombieFarmAnimals.ZombieSheep(id, x, y, z);
            case ZOMBIE_GOAT -> new ZombieFarmAnimals.ZombieGoat(id, x, y, z);
            case ZOMBIE_FOX -> new ZombieFarmAnimals.ZombieFox(id, x, y, z);
            case ZOMBIE_CHICKEN -> new ZombieFarmAnimals.ZombieChicken(id, x, y, z);
            case CARRION_STAG -> new CarrionMobs.CarrionStag(id, x, y, z);
            case GRIZZLY_BEAR -> new GrizzlyBear(id, x, y, z);
            case BOAR -> new Boar(id, x, y, z);
            case CARRION_BOAR -> new CarrionMobs.CarrionBoar(id, x, y, z);
            case CARRION_CROW -> new CarrionMobs.CarrionCrow(id, x, y, z);
            case CAMEL_HUSK -> new CamelHusk(id, x, y, z);
            // 신종 12종(stableId 86~97).
            case CAT -> new Cat(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
            case WANDERING_TRADER -> new WanderingTrader(id, x, y, z);
            case TRADER_LLAMA -> new TraderLlama(id, x, y, z);
            case SKELETON_HORSE -> new SkeletonHorse(id, x, y, z);
            case PHANTOM -> new Phantom(id, x, y, z);
            case PARCHED -> new Parched(id, x, y, z);
            case SNOW_GOLEM -> new SnowGolem(id, x, y, z);
            case SNIFFER -> new Sniffer(id, x, y, z);
            case BREEZE -> new Breeze(id, x, y, z);
            case HAPPY_GHAST -> new HappyGhast(id, x, y, z);
            case SULFUR_CUBE -> new SulfurCube(id, x, y, z);
            case WARDEN -> new Warden(id, x, y, z);
            // [SULFUR] 황린 잠복자. 자연 스폰은 0 이고 유황 동굴 구조물 훅만 이 자리를 부른다.
            case BRIMSTONE_LURKER -> new BrimstoneLurker(id, x, y, z);
            // [CREAKING] 크리킹. 자연 스폰은 0 이고 크리킹 하트 사슬(CreakingSummon)만 이
            // 자리를 부른다. 이 생성자는 하트 좌표를 자기 발밑으로 잡으므로, 하트가 아닌
            // 곳에서 세워지면 결속 반경 32 안에 자기 자신이 있어 즉사하지 않는다 —
            // 실제 하트 좌표는 CreakingSummon 이 네 인자 생성자로 심는다.
            case CREAKING -> new Creaking(id, x, y, z);
            case POISON_DART_FROG -> new PoisonDartFrog(id, x, y, z,
                    variant(type, requestedVariant, worldSeed, id, x, z));
        };
    }

    private static String variant(MobType type, String requestedVariant, int worldSeed,
                                  long id, double x, double z) {
        return requestedVariant == null
                ? type.deterministicVariant(worldSeed, id, x, z)
                : requestedVariant;
    }

    /**
     * [FARM-VARIANT] 돼지·닭의 기후 변종. 바이옴을 아는 경로만 값을 주고, 나머지는 전부
     * 선언 순서 첫 변종(temperate)으로 떨어진다 — 바닐라의 "unlisted biome → temperate"
     * 와 같은 계약이다. 좌표 해시 추첨을 쓰지 않는 것이 핵심이다(바이옴이 정본).
     */
    private static String farmAnimalClimate(MobType type, String requestedVariant) {
        return requestedVariant == null ? type.legacyVariant() : requestedVariant;
    }

    static Mob restore(MobType type, long id, double x, double y, double z,
                       String variant, int worldSeed) {
        return restore(type, id, x, y, z, variant, worldSeed,
                type == MobType.SLIME ? 2 : 0);
    }

    static Mob restore(MobType type, long id, double x, double y, double z,
                       String variant, int worldSeed, int slimeSize) {
        // 열대어는 단일 외형으로 먼저 출시돼 variant=null 로 저장된 행이 남아 있다. 22종 어휘를
        // 추가한 뒤에도 그 행을 계속 읽기 위해 선언 순서 첫 변종으로 승격한다. 처음부터 변종을
        // 가졌던 종의 null 은 여전히 손상으로 보고 거부한다.
        if (variant == null && type == MobType.TROPICAL_FISH) variant = type.legacyVariant();
        // [FARM-VARIANT] 돼지·닭은 변종 없이 먼저 출시돼 variant=null 로 저장된 행이 남아
        // 있다. 1.21.5 기후 어휘를 추가한 뒤에도 그 행을 계속 읽기 위해 temperate 로
        // 승격한다 — 열대어와 같은 마이그레이션 계약이다. 소는 옛 이름이 그대로 temperate
        // 어휘라 승격 없이 읽힌다.
        if (variant == null && (type == MobType.PIG || type == MobType.CHICKEN)) {
            variant = type.legacyVariant();
        }
        // [MOB-LOOK] 판다는 유전자 변종 없이 먼저 출시돼 variant=null 행이 남아 있다 — 바닐라 기본 NORMAL.
        if (variant == null && type == MobType.PANDA) variant = "normal";
        if (!type.acceptsVariant(variant)) {
            throw new IllegalArgumentException(
                    "invalid persisted variant for " + type + ": " + variant);
        }
        if (type == MobType.COW) return new AnimalMob(type, id, x, y, z, variant);
        // 세 종은 종 전용 상태(양 색·전단, 닭 산란 커서, 돼지 안장)를 갖는다. 여기서는 기본값으로
        // 만들고, 저장된 값은 MobRuntime 의 restore* 경계가 곧바로 덮어쓴다.
        if (type == MobType.PIG) return new Pig(id, x, y, z, variant);
        if (type == MobType.SHEEP) return new Sheep(id, x, y, z);
        if (type == MobType.CHICKEN) return new Chicken(id, x, y, z, variant);
        if (type == MobType.RABBIT) return new Rabbit(id, x, y, z, variant);
        if (type == MobType.AXOLOTL) return new Axolotl(id, x, y, z, variant);
        if (type == MobType.TROPICAL_FISH) return new TropicalFish(id, x, y, z, variant);
        if (type == MobType.SLIME) {
            if (slimeSize < 1) throw new IllegalArgumentException("invalid persisted slime size");
            return new Slime(id, x, y, z, slimeSize);
        }
        return create(type, id, x, y, z, worldSeed, false, variant);
    }

}
