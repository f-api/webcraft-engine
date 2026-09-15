package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.inventory.PlayerInventory;

/** Shared horse-family locomotion/variant boundary; species interactions remain in each authority. */
abstract class EquineMob extends AnimalMob {
    EquineMob(MobType type, long id, double x, double y, double z, String variant) {
        super(type, id, x, y, z, variant);
    }
}

/**
 * 말 계열 공통 상태 기계. 바닐라 {@code AbstractHorse} + {@code AbstractChestedHorse} +
 * {@code RunAroundLikeCrazyGoal} 의 <b>상태만</b> 얹고, 수치·판정은 전부 {@link HorseRules}·
 * {@link ChestedHorseRules}·{@link LlamaRules} 에 있다. AI 는 {@link AnimalMob} 그대로다.
 *
 * <p>종 차이는 네 개의 훅으로만 갈린다:
 * <ul>
 *   <li>{@link #maxTemper()} — 라마만 30({@code Llama#getMaxTemper}).</li>
 *   <li>{@link #saddleable()} — 라마는 안장이 없다.</li>
 *   <li>{@link #chestable()} — 말만 상자를 달 수 없다({@code AbstractChestedHorse} 하위형이 아니다).</li>
 *   <li>{@link #carpetable()} — 라마만 카펫 장식을 받는다({@code Llama#isBodyArmorItem}).</li>
 * </ul>
 * 스탯 롤 자체도 종마다 다르지만({@code Horse} 는 셋 다 굴리고 상자 말 계열은 체력만 굴린다)
 * 그 차이는 {@link HorseRules#rollStats(MobType, MobRandom)} 한 곳이 소유한다.
 *
 * <p>좌석 계약은 {@link MobMountRules} 로 돼지와 공유한다: 탑승이 확정되고 <b>그 종이 조종
 * 가능하면</b> 좌표 정본이 기수 클라로 넘어가고 서버 AI 이동은 멈춘다. 라마는 조종 불가라
 * 승객을 태운 채로도 서버 AI 가 계속 걷는다(바닐라 {@code Llama#getControllingPassenger} = null).
 */
abstract class AbstractHorseMob extends EquineMob {

    /**
     * 개체 스탯. 생성 즉시 {@link HorseRules#rollStats(MobType, MobRandom)} 로 굴리고 이후 영속된다.
     *
     * <p>WebCraft divergence: 바닐라는 {@code finalizeSpawn} 에서 레벨 RNG 를 소비하지만,
     * WebCraft 는 몹 ID 로 시드한 개체 전용 RNG 를 쓴다. 스폰 경로마다 tick RNG 를 실어 나르지
     * 않고도 두 권위가 같은 소비 순서로 같은 분포를 내며, 같은 ID 를 가진 개체가 언제나 같은
     * 스탯으로 재현된다.
     */
    private HorseRules.Stats stats;
    private boolean tamed;
    private int temper;
    /**
     * Vanilla horse-menu equipment slots. Keeping real stacks here (instead of booleans/item ids)
     * preserves names, enchantments and every current item component across menu moves and saves.
     * Slot 0 is SADDLE and slot 1 is BODY (horse armor or llama carpet).
     */
    private final ChestInventory equipment = new ChestInventory(2);
    private String riderNickname;
    /**
     * 미길들임 상태로 태운 뒤 굴린 길들이기 판정 결과. 실제 낙마는 {@code MobSystem} 이
     * 좌석 원장과 함께 처리하므로, 여기서는 "이번 틱에 낙마해야 한다"만 남긴다.
     */
    private boolean buckPending;

    /** 상자 장착 여부. 바닐라와 같이 한 번 달면 뗄 수 없고 사망 드랍만이 회수 경로다. */
    private boolean chested;
    /** 화물. 상자를 달 때 종·힘 스탯이 정한 칸 수로 만들어진다(기존 컨테이너 계약 재사용). */
    private ChestInventory cargo;
    /** 라마 장식 카펫 색(0~15) 또는 {@link LlamaRules#NO_CARPET}. 다른 종은 언제나 장식 없음이다. */

    AbstractHorseMob(MobType type, long id, double x, double y, double z, String variant) {
        super(type, id, x, y, z, variant);
        applyStats(HorseRules.rollStats(type, HorseRules.statRandom(id)));
    }

    // ── 종 훅 ─────────────────────────────────────────────────────────
    /** 이 종의 temper 상한. {@link HorseRules#maxTemper(MobType)} 한 곳에서만 갈린다. */
    int maxTemper() { return HorseRules.maxTemper(type); }

    /** 안장을 받는가. 바닐라 라마는 안장 슬롯 자체가 없다. */
    boolean saddleable() { return type != MobType.LLAMA; }

    /** 상자를 받는가. 바닐라 {@code AbstractChestedHorse} 하위형만 참이다. */
    boolean chestable() { return ChestedHorseRules.chestable(type); }

    /** 카펫 장식을 받는가. 바닐라 {@code Llama#isBodyArmorItem} 이 있는 종만 참이다. */
    boolean carpetable() { return type == MobType.LLAMA; }
    boolean armorable() { return HorseRules.canWearArmor(type); }

    /** 화물 열 수를 정하는 힘 스탯. 라마만 1~5 이고 나머지는 0(고정 5열)이다. */
    int strength() { return 0; }

    /**
     * 태어날 때부터 길들여진 종인가. 좀비 말({@link ZombieHorseRules})만 참이다 — 바닐라
     * {@code RunAroundLikeCrazyGoal} 의 낙마·temper 누적은 "야생마를 길들이는 과정"의 표현인데
     * 이미 죽은 말에 그 과정을 붙일 근거가 없다는 자체 계약이다. 참이면 temper 는 언제나 상한에
     * 굳어 있고 낙마 예약({@code buckPending})이 서지 않는다.
     */
    boolean bornTamed() { return false; }

    /** {@link #bornTamed()} 종의 초기화. 주인은 두지 않는다 — 누구나 안장을 얹을 수 있다. */
    final void tameFromBirth() {
        tamed = true;
        temper = maxTemper();
    }

    // ── 스탯 ──────────────────────────────────────────────────────────
    HorseRules.Stats stats() { return stats; }
    short armorItem() { return armorable() ? equipment.itemType(1) : 0; }
    ChestInventory equipment() { return equipment; }

    boolean equipArmor(short itemType) {
        if (!HorseRules.isArmorItem(itemType)) return false;
        int durability = PlayerInventory.isDurable(itemType)
                ? PlayerInventory.initialDurability(itemType) : 0;
        return equipArmor(new PlayerInventory.StackSnapshot(itemType, 1, durability,
                0, 0, 0, null, null));
    }

    boolean equipArmor(PlayerInventory.StackSnapshot armor) {
        if (isDead() || removed || isBaby() || !tamed || !armorable()
                || equipment.itemType(1) != 0 || armor == null || armor.count() != 1
                || !HorseRules.isArmorItem(armor.itemType())) return false;
        return putHorseMenuEquipment(equipment, 1, armor);
    }

    short removeArmor() {
        PlayerInventory.StackSnapshot removed = removeArmorStack();
        return removed.isEmpty() ? 0 : removed.itemType();
    }

    PlayerInventory.StackSnapshot removeArmorStack() {
        if (isDead() || removed || !armorable() || equipment.itemType(1) == 0
                || equipment.count(1) != 1 || riderNickname != null) {
            return PlayerInventory.StackSnapshot.EMPTY;
        }
        return takeHorseMenuEquipment(equipment, 1);
    }

    void restoreArmor(short restoredItem) {
        if (restoredItem != 0 && (!armorable() || !HorseRules.isArmorItem(restoredItem))) {
            throw new IllegalArgumentException("invalid persisted horse armor");
        }
        if (!armorable()) return;
        equipment.take(1, equipment.count(1));
        if (restoredItem != 0) equipment.restoreSlot(1, restoredItem, 1, null);
    }

    void applyStats(HorseRules.Stats next) {
        if (!next.valid()) throw new IllegalArgumentException("invalid horse stats");
        stats = next;
        setInitialHealth(next.maxHealth());
    }

    /**
     * 개체 스탯이 곧 최대 체력이다(바닐라 {@code Attributes.MAX_HEALTH} 기본값).
     * 번식 계승은 소수 체력을 내므로 표시·상한 정수는 올림한다({@code Mob.setInitialHealth} 와 같다).
     */
    @Override public int maxHp() { return (int) Math.ceil(stats.maxHealth() - 1e-9); }

    // ── 길들이기 ──────────────────────────────────────────────────────
    boolean tamed() { return tamed; }
    int temper() { return temper; }
    boolean saddled() { return equipment.itemType(0) == PlayerInventory.SADDLE; }
    String riderNickname() { return riderNickname; }
    boolean buckPending() { return buckPending; }
    boolean chested() { return chested; }
    int carpetColor() {
        return carpetable() ? LlamaRules.carpetColorIndex(equipment.itemType(1))
                : LlamaRules.NO_CARPET;
    }

    /**
     * 먹이 한 개. 바닐라 {@code AbstractHorse#handleEating}: temper 상승·회복·새끼 성장 단축이
     * 한 표에서 나온다. 표에 없는 아이템이면 거짓이고 아이템은 소비되지 않는다.
     */
    boolean feed(short itemType) {
        int gain = HorseRules.feedTemperGain(itemType);
        if (gain < 0 || isDead() || removed) return false;
        if (!tamed && gain > 0) temper = HorseRules.clampTemper(type, temper + gain);
        double heal = HorseRules.feedHealAmount(itemType);
        if (heal > 0) heal(heal);
        if (isBaby()) {
            ageUpSeconds(HorseRules.feedAgeUpSeconds(itemType),
                    FarmAnimalRules.AUTHORITY_TICKS_PER_SECOND);
        }
        setPersistenceRequired(true);
        // 먹이 표에 있으면 바닐라는 언제나 아이템을 소비한다(temper 가 이미 max 여도 같다).
        return true;
    }

    /** 바닐라 {@code AbstractHorse#tameWithName}. 길들이면 temper 는 종 상한으로 굳는다. */
    void tame(String nickname) {
        tamed = true;
        temper = maxTemper();
        setOwnerNickname(nickname);
        setPersistenceRequired(true);
    }

    /** 안장 장착. 바닐라는 길들인 성체만 안장을 받고, 라마는 아예 받지 않는다. */
    boolean saddle() {
        return saddle(new PlayerInventory.StackSnapshot(PlayerInventory.SADDLE, 1, 0,
                0, 0, 0, null, null));
    }

    boolean saddle(PlayerInventory.StackSnapshot saddle) {
        if (!saddleable() || isDead() || removed || saddled() || isBaby() || !tamed
                || saddle == null || saddle.count() != 1
                || saddle.itemType() != PlayerInventory.SADDLE) return false;
        return putHorseMenuEquipment(equipment, 0, saddle);
    }

    /**
     * 상자 장착. 바닐라 {@code AbstractChestedHorse#mobInteract} 는 길들인 성체이고 아직 상자가
     * 없을 때만 {@code Items.CHEST} 를 받는다. 화물 칸 수는 그 순간의 종·힘 스탯이 정한다.
     */
    boolean attachChest() {
        if (!chestable() || isDead() || removed || chested || isBaby() || !tamed) return false;
        int slots = ChestedHorseRules.cargoSlots(type, true, strength());
        if (slots <= 0) return false;
        preflightHorseMenuPersistenceRevision();
        ChestInventory newCargo = new ChestInventory(slots);
        advanceHorseMenuPersistenceRevision();
        chested = true;
        cargo = newCargo;
        setPersistenceRequired(true);
        return true;
    }

    /** 화물 컨테이너. 상자를 달지 않았으면 null 이다(열 수 있는 것이 없다). */
    ChestInventory cargo() {
        return chested ? cargo : null;
    }

    /**
     * 사망 시 화물을 비우고 내용물을 돌려준다. 상자 자체도 함께 떨어지므로 호출부가
     * {@code Blocks.CHEST} 한 개를 더한다(바닐라 {@code AbstractChestedHorse#dropEquipment}).
     */
    List<ChestInventory.StoredStack> drainCargo() {
        if (!chested || cargo == null) return List.of();
        return cargo.drainAll();
    }

    /**
     * 카펫 장식. 바닐라 {@code Llama} 는 body-armor 슬롯에 wool carpet 을 받으며 길들임 여부를
     * 묻지 않는다({@code AbstractHorse#mobInteract} 의 body-armor 경로). 이미 장식이 있으면
     * 교체하지 않는다(바닐라는 슬롯이 비어 있을 때만 넣는다).
     */
    boolean decorate(short itemType) {
        int color = LlamaRules.carpetColorIndex(itemType);
        if (color == LlamaRules.NO_CARPET) return false;
        return decorate(new PlayerInventory.StackSnapshot(itemType, 1, 0,
                0, 0, 0, null, null));
    }

    boolean decorate(PlayerInventory.StackSnapshot carpet) {
        if (!carpetable() || isDead() || removed || equipment.itemType(1) != 0
                || carpet == null || carpet.count() != 1
                || LlamaRules.carpetColorIndex(carpet.itemType()) == LlamaRules.NO_CARPET) {
            return false;
        }
        return putHorseMenuEquipment(equipment, 1, carpet);
    }

    /**
     * 탑승. 바닐라 {@code AbstractHorse#mobInteract → doPlayerRide} 는 <b>길들이기 여부와
     * 무관하게</b> 태운다 — 미길들임 개체는 태운 뒤 {@code RunAroundLikeCrazyGoal} 이 판정한다.
     * 새끼는 태울 수 없다({@code isBaby()} 가면 {@code doPlayerRide} 에 닿지 않는다).
     */
    boolean mount(String nickname) {
        if (isDead() || removed || isBaby() || riderNickname != null
                || nickname == null || nickname.isEmpty()) return false;
        riderNickname = nickname;
        buckPending = false;
        setPersistenceRequired(true);
        return true;
    }

    boolean dismount(String nickname) {
        if (riderNickname == null || !riderNickname.equals(nickname)) return false;
        riderNickname = null;
        buckPending = false;
        setPersistenceRequired(true);
        return true;
    }

    void clearRider() {
        riderNickname = null;
        buckPending = false;
    }

    /**
     * 기수 좌표 업링크(돼지와 같은 계약). 실제 기수이고 <b>조종 가능한 종</b>이며 <b>안장이
     * 있을 때만</b> 참이다 — 라마는 승객이 있어도 좌표 정본이 넘어가지 않고, 안장 없는 말·당나귀는
     * 타기는 해도 조종되지 않는다(바닐라 {@code AbstractHorse#getControllingPassenger} 의
     * {@code isSaddled()} 가드).
     */
    boolean applyRiderPosition(String nickname, double x, double y, double z, double yaw) {
        if (!MobMountRules.steerable(type, saddled())) return false;
        if (riderNickname == null || !riderNickname.equals(nickname)) return false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        vy = 0;
        horizontalVx = 0;
        horizontalVz = 0;
        knockbackVx = 0;
        knockbackVz = 0;
        return true;
    }

    /**
     * 바닐라 {@code RunAroundLikeCrazyGoal#tick()} 한 MC 틱. 미길들임 상태로 기수를 태우고
     * 있을 때만 굴린다. 성공하면 길들여지고, 실패하면 temper 가 오른 뒤 낙마 예약이 선다.
     *
     * @return 이번 굴림에서 길들여졌으면 true
     */
    private boolean tickTameAttempt(MobRandom rng) {
        if (tamed || riderNickname == null) return false;
        if (rng.nextInt(HorseRules.TAME_ATTEMPT_ROLL_BOUND) != 0) return false;
        if (HorseRules.tamesOnAttempt(temper, rng.nextInt(maxTemper()))) {
            tame(riderNickname);
            return true;
        }
        temper = HorseRules.clampTemper(type, temper + HorseRules.TAME_FAILURE_TEMPER_GAIN);
        buckPending = true;
        setPersistenceRequired(true);
        return false;
    }

    /** {@code MobSystem} 이 낙마를 처리한 뒤 예약을 지운다. */
    void consumeBuck() { buckPending = false; }

    /**
     * 점프 강도 검증. 권위는 물리를 굴리지 않으므로(좌표 정본이 기수 클라) 기수가 주장한
     * 차지가 바닐라 범위 안인지, 그리고 안장이 있는지만 본다
     * (바닐라 {@code AbstractHorse#onPlayerJump} 는 {@code isSaddled()} 가 아니면 무시한다).
     * 조종 불가 종은 안장이 없어 여기 닿지 않는다.
     */
    boolean acceptsJump(String nickname, int charge) {
        return saddled() && MobMountRules.controllable(type) && riderNickname != null
                && riderNickname.equals(nickname)
                && charge >= 0 && charge <= HorseRules.MAX_JUMP_CHARGE;
    }

    @Override public int visualFlags() {
        int flags = saddled() ? Mob.VISUAL_HORSE_SADDLED : 0;
        if (tamed) flags |= Mob.VISUAL_HORSE_TAMED;
        if (chested) flags |= Mob.VISUAL_HORSE_CHESTED;
        int carpetColor = carpetColor();
        if (carpetColor != LlamaRules.NO_CARPET) {
            flags |= Mob.VISUAL_LLAMA_CARPET;
            flags |= (carpetColor << Mob.VISUAL_LLAMA_CARPET_COLOR_SHIFT)
                    & Mob.VISUAL_LLAMA_CARPET_COLOR_MASK;
        }
        return flags;
    }

    void restoreHorseState(boolean restoredTamed, int restoredTemper, boolean restoredSaddled,
                           double restoredMaxHealth, double restoredSpeed,
                           double restoredJumpStrength) {
        restoreHorseState(restoredTamed, restoredTemper, restoredSaddled,
                restoredMaxHealth, restoredSpeed, restoredJumpStrength,
                false, LlamaRules.NO_CARPET);
    }

    void restoreHorseState(boolean restoredTamed, int restoredTemper, boolean restoredSaddled,
                           double restoredMaxHealth, double restoredSpeed,
                           double restoredJumpStrength,
                           boolean restoredChested, int restoredCarpetColor) {
        HorseRules.Stats restored =
                new HorseRules.Stats(restoredMaxHealth, restoredSpeed, restoredJumpStrength);
        if (!restored.valid() || restoredTemper < 0 || restoredTemper > maxTemper()
                || restoredSaddled && (!restoredTamed || !saddleable())
                || restoredChested && !chestable()
                || !LlamaRules.isValidCarpetColor(restoredCarpetColor)
                || restoredCarpetColor != LlamaRules.NO_CARPET && !carpetable()) {
            throw new IllegalArgumentException("invalid persisted horse-family state");
        }
        stats = restored;
        tamed = restoredTamed;
        temper = restoredTemper;
        // 태어날 때부터 길들여진 종은 저장된 값이 무엇이든 길들여진 채로 되살아난다 —
        // 옛 행이나 손상된 행이 언데드 말에 낙마 판정을 되살리지 못하게 한다.
        if (bornTamed()) tameFromBirth();
        equipment.clearForRestore();
        if (restoredSaddled) equipment.restoreSlot(0, PlayerInventory.SADDLE, 1, null);
        if (restoredCarpetColor != LlamaRules.NO_CARPET) {
            equipment.restoreSlot(1, LlamaRules.carpetItem(restoredCarpetColor), 1, null);
        }
        if (restoredChested) {
            int slots = ChestedHorseRules.cargoSlots(type, true, strength());
            if (slots <= 0) throw new IllegalArgumentException("chested row without cargo slots");
            chested = true;
            if (cargo == null || cargo.slots() != slots) cargo = new ChestInventory(slots);
        } else {
            chested = false;
            cargo = null;
        }
        // 재시작·언로드 뒤에는 기수 세션이 없으므로 좌석은 비운 채로 복원한다(돼지와 같다).
        riderNickname = null;
        buckPending = false;
    }

    /**
     * 저장된 화물 한 줄을 되돌린다. {@link #restoreHorseState} 가 이미 상자를 달아 이 개체의
     * 칸 수로 컨테이너를 만들어 둔 상태여야 한다 — 상자 없는 개체에 화물이 붙은 행은 손상이다.
     */
    void restoreCargo(String encodedCargo) {
        if (encodedCargo == null || encodedCargo.isEmpty()) return;
        if (!chested || cargo == null) {
            throw new IllegalArgumentException("chestless horse-family row carries cargo");
        }
        MobCargoCodec.decodeInto(cargo, encodedCargo);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        // 조종 가능한 종은 탑승 중 기수 클라이언트가 좌표 정본이라 서버 AI 이동을 돌리지 않는다.
        // 라마는 조종 불가라 승객이 있어도 서버가 계속 걷는다(바닐라와 같다).
        boolean frozen = riderNickname != null && MobMountRules.controllable(type);
        if (type == MobType.HORSE) {
            if (riderNickname != null && "graze".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
            if (riderNickname == null && !"graze".equals(actionKind()) && fleeTimer == 0
                    && world.getBlock((int) Math.floor(x), (int) Math.floor(y - 0.01), (int) Math.floor(z))
                            == com.gameexpert.terrain.Blocks.GRASS) {
                for (int step = 0; step < 2; step++) if (rng.nextInt(300) == 0) {
                    markVisualAction("graze", 26);
                    break;
                }
            }
            if ("graze".equals(actionKind()) && fleeTimer == 0) {
                state = MobState.IDLE;
                MobPhysics.tickMove(this, world, 0, 0, 0, MoveMode.WALK);
                return List.of();
            }
        }
        List<MobEvent> events = frozen ? List.of() : super.tick(world, rng);
        if (riderNickname != null) {
            if (frozen) state = MobState.IDLE;
            // 바닐라 goal 은 20 TPS 라 권위 틱(10 TPS)마다 두 번 굴린다.
            for (int step = 0; step < 2 && riderNickname != null && !tamed && !buckPending; step++) {
                tickTameAttempt(rng);
            }
        }
        return events;
    }
}

/**
 * 말. 바닐라 {@code Horse} — 말 계열에서 유일하게 체력·속도·점프를 <b>모두</b> 굴리고 상자를
 * 달지 못한다.
 */
final class Horse extends AbstractHorseMob {
    Horse(long id, double x, double y, double z, String variant) {
        super(MobType.HORSE, id, x, y, z, variant);
    }
}

/**
 * 당나귀. 바닐라 {@code Donkey extends AbstractChestedHorse} — 체력만 굴리고 속도 0.175·점프
 * 0.5 고정이며, 상자를 달면 5열 × 3행 = 15칸 화물을 갖는다. 말과 교배해 노새를 낳는다.
 */
final class Donkey extends AbstractHorseMob {
    Donkey(long id, double x, double y, double z) {
        super(MobType.DONKEY, id, x, y, z, null);
    }
}

/**
 * 노새. 바닐라 {@code Mule} — 당나귀와 같은 스탯·화물 계약을 갖되 자연 스폰하지 않고
 * <b>번식할 수 없다</b>({@code MobType.BREEDABLE} 에 없다). 말+당나귀 교배로만 태어난다.
 */
final class Mule extends AbstractHorseMob {
    Mule(long id, double x, double y, double z) {
        super(MobType.MULE, id, x, y, z, null);
    }
}

/**
 * 라마. 바닐라 {@code Llama extends AbstractChestedHorse} 이지만 세 가지가 다르다:
 * 안장이 없고 {@code getControllingPassenger()} 가 null 이라 <b>타되 조종할 수 없으며</b>,
 * max temper 가 30 이고, 힘 스탯(1~5)이 곧 화물 열 수다. 카펫 장식과 침 뱉기도 라마 전용이다.
 */
final class Llama extends AbstractHorseMob {

    /**
     * 바닐라 {@code Llama#setRandomStrength} 의 결과. 스탯과 같은 이유로 몹 ID 시드 RNG 를 쓰므로
     * 같은 ID 는 언제나 같은 힘을 낸다(두 권위 재현성).
     */
    private int strength;

    /**
     * 남은 발사 주기(권위 틱). 바닐라 {@code RangedAttackGoal} 의 {@code attackTime} 이며
     * 발사 여부와 무관하게 주기가 유지된다(스켈레톤·마녀와 같은 계약).
     */
    private int spitCooldownTicks;

    Llama(long id, double x, double y, double z, String variant) {
        super(MobType.LLAMA, id, x, y, z, variant);
        strength = LlamaRules.rollStrength(LlamaRules.strengthRandom(id));
    }

    @Override int strength() { return strength; }

    /** 영속 복구. 범위 밖 값은 계약대로 클램프한다. */
    void restoreStrength(int restored) {
        strength = LlamaRules.clampStrength(restored);
    }

    /**
     * 바닐라 {@code LlamaHurtByTargetGoal}: 라마의 <b>유일한</b> 표적 획득 경로는 피격이다.
     * 도주(PanicGoal)는 다른 동물과 같이 {@link AnimalMob#onHurt} 가 그대로 굴린다.
     */
    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        super.onHurt(attackerNickname, attackerX, attackerZ);
        if (attackerNickname != null && !attackerNickname.isEmpty()) {
            forceTarget(attackerNickname);
        }
    }

    /**
     * 바닐라 {@code RangedAttackGoal(this, 1.25, 40, 20.0F)} 의 발사 부분. 표적 획득은
     * {@link #onHurt} 만이 하므로 여기서는 <b>새 표적을 찾지 않는다</b>
     * ({@code trackedTarget(..., mayAcquire=false)}).
     *
     * <p>이동(접근·전략)은 굴리지 않는다 — 라마의 이동은 말 계열 공통 AI 가 소유하고, 여기서
     * 더하면 좌표 정본이 둘이 된다. 사거리(20)는 표적 유지 거리(16)보다 넓어서, 실제로는
     * 추적이 살아 있는 동안 언제나 사거리 안이다.
     */
    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (isDead() || removed) return events;
        if (spitCooldownTicks > 0) spitCooldownTicks--;
        PlayerSnapshot target = trackedTarget(world, LlamaRules.TARGET_FOLLOW_RANGE, false);
        if (target == null) return events;
        faceToward(target.x(), target.z());
        if (spitCooldownTicks > 0 || dist3d(target) > LlamaRules.SPIT_ATTACK_RADIUS
                || !canSeeTargetNow(world, target)) {
            return events;
        }
        spitCooldownTicks = LlamaRules.SPIT_INTERVAL_AUTHORITY_TICKS;
        MobEvent.ShootArrow spit = spitAt(target, rng);
        return spit == null ? events : appendEvent(events, spit);
    }

    /**
     * 바닐라 {@code Llama#spit(LivingEntity)}: 눈높이에서 대상 몸통 1/3 높이를 겨냥해
     * {@code shoot(dx, dy + d3, dz, 1.5F, 10.0F)} 한다. 조준·발사 식은 {@link LlamaRules} 가,
     * 오차 표본은 여기 RNG 가 소유한다(스켈레톤 화살과 같은 소비 순서: x, y, z 각 2 draw).
     */
    private MobEvent.ShootArrow spitAt(PlayerSnapshot target, MobRandom rng) {
        double sx = x;
        double sy = y + eyeHeight();
        double sz = z;
        double[] aim = LlamaRules.spitAim(sx, sy, sz,
                target.x(), target.y(), ProjectileSim.PLAYER_HEIGHT, target.z());
        double noise = ProjectileSim.INACCURACY_NOISE_SCALE * LlamaRules.SPIT_INACCURACY;
        double[] velocity = LlamaRules.spitVelocity(aim,
                Skeleton.nextGaussian(rng) * noise,
                Skeleton.nextGaussian(rng) * noise,
                Skeleton.nextGaussian(rng) * noise);
        if (velocity == null) return null;
        return new MobEvent.ShootArrow(ProjectileSim.Kind.LLAMA_SPIT, sx, sy, sz,
                velocity[0], velocity[1], velocity[2], LlamaRules.SPIT_DAMAGE);
    }
}
