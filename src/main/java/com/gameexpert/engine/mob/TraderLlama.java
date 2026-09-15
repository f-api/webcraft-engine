package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 트레이더 라마(바닐라 실존 종, stableId 88). 바닐라 {@code TraderLlama extends Llama} 는
 * AABB·attribute override 가 없어 {@link MobType#LLAMA} 와 글자 그대로 같은 수치를 쓴다 —
 * 근거는 {@link MobType#TRADER_LLAMA} 의 인라인 인용이다.
 *
 * <p><b>WebCraft divergence(등급 C)</b>: 바닐라는 리드(lead)로 행상인에 묶여 따라오지만
 * WebCraft 에는 리드 아이템이 없다. 그래서 이 웨이브는 <b>근접 추종</b>으로 갈음한다 —
 * {@value #FOLLOW_RANGE} 블록 안의 행상인을 따라가고, 그 너머로 벌어지면 배회로 돌아간다.
 * 추종 배선 자체는 다음 단계이고 이 웨이브는 계약과 상수만 세운다.
 *
 * <p>변종 4종(creamy/white/brown/gray)은 라마와 같지만 이 웨이브 밖이다(변종은 다음 단계).
 */
public final class TraderLlama extends AnimalMob implements TimedDespawn {
    private final com.gameexpert.engine.ChestInventory equipment =
            new com.gameexpert.engine.ChestInventory(2);
    private final int strength;
    private boolean chested;
    private com.gameexpert.engine.ChestInventory cargo;

    /** 리드가 없는 대신 쓰는 근접 추종 반경. 리드 길이(10블록)를 넘지 않는다. */
    public static final double FOLLOW_RANGE = 10.0;

    /**
     * 남은 체류 시간(MC 틱). 바닐라 {@code TraderLlama} 의 필드 초기값이 글자 그대로
     * {@code private int despawnDelay = 47999;} 다 — 행상인의 48,000 보다 1 작아서 리드에
     * 묶이지 않아도 <b>행상인과 같은 틱 또는 한 틱 먼저</b> 사라진다. 리드가 없는 WebCraft
     * divergence 에서도 이 초기값이 그대로 "트레이더 소멸 동반"을 만든다:
     * 같은 굴림에서 함께 나왔으므로 두 개체의 남은 시간이 처음부터 나란하다.
     */
    public static final int DESPAWN_TICKS = 47_999;

    private int despawnDelay = DESPAWN_TICKS;

    public TraderLlama(long id, double x, double y, double z) {
        super(MobType.TRADER_LLAMA, id, x, y, z);
        strength = LlamaRules.rollStrength(LlamaRules.strengthRandom(id));
    }

    /** 리드가 없는 WebCraft 추종 계약. 공통 panic·breeding goal 뒤, 배회 goal 앞에서 돈다. */
    @Override
    protected double[] scheduledWalk(MobWorldView world) {
        Mob trader = preparedSocialTarget();
        if (trader == null || trader.type != MobType.WANDERING_TRADER
                || trader.isDead() || trader.removed) return null;
        double dx = trader.x - x, dz = trader.z - z;
        if (dx * dx + dz * dz > FOLLOW_RANGE * FOLLOW_RANGE) return null;
        return towardHoriz(trader.x, trader.z, type.baseSpeed());
    }

    @Override
    public int remainingDespawnMcTicks() {
        return despawnDelay;
    }
    com.gameexpert.engine.ChestInventory equipment() { return equipment; }
    int strength() { return strength; }
    boolean chested() { return chested; }
    com.gameexpert.engine.ChestInventory cargo() { return chested ? cargo : null; }

    boolean attachChest() {
        if (isDead() || removed || isBaby() || chested) return false;
        preflightHorseMenuPersistenceRevision();
        com.gameexpert.engine.ChestInventory newCargo = new com.gameexpert.engine.ChestInventory(
                ChestedHorseRules.cargoSlots(type, true, strength));
        advanceHorseMenuPersistenceRevision();
        chested = true;
        cargo = newCargo;
        setPersistenceRequired(true);
        return true;
    }

    boolean decorate(short itemType) {
        if (LlamaRules.carpetColorIndex(itemType) == LlamaRules.NO_CARPET) return false;
        return decorate(new PlayerInventory.StackSnapshot(
                itemType, 1, 0, 0, 0, 0, null, null));
    }

    boolean decorate(PlayerInventory.StackSnapshot carpet) {
        if (isDead() || removed || equipment.itemType(1) != 0
                || carpet == null || carpet.count() != 1
                || LlamaRules.carpetColorIndex(carpet.itemType()) == LlamaRules.NO_CARPET) {
            return false;
        }
        return putHorseMenuEquipment(equipment, 1, carpet);
    }

    void restoreHorseState(boolean restoredChested, int restoredCarpetColor, int restoredStrength) {
        if (restoredStrength != 0 && restoredStrength != strength) {
            throw new IllegalArgumentException("invalid persisted trader llama strength");
        }
        if (!LlamaRules.isValidCarpetColor(restoredCarpetColor)) {
            throw new IllegalArgumentException("invalid persisted trader llama carpet");
        }
        equipment.clearForRestore();
        if (restoredCarpetColor != LlamaRules.NO_CARPET) {
            equipment.restoreSlot(1, LlamaRules.carpetItem(restoredCarpetColor), 1, null);
        }
        chested = restoredChested;
        cargo = restoredChested ? new com.gameexpert.engine.ChestInventory(
                ChestedHorseRules.cargoSlots(type, true, strength)) : null;
    }

    void restoreCargo(String encoded) {
        if (encoded == null || encoded.isEmpty()) return;
        if (!chested || cargo == null) {
            throw new IllegalArgumentException("chestless trader llama carries cargo");
        }
        MobCargoCodec.decodeInto(cargo, encoded);
    }

    /** 되살린 개체의 남은 체류 시간 복원(영속 왕복). */
    public void setRemainingDespawnMcTicks(int mcTicks) {
        despawnDelay = Math.max(0, mcTicks);
    }

    @Override
    public boolean expireDespawnDelay(int mcTicks) {
        if (shouldPersist()) return false;
        despawnDelay = Math.max(0, despawnDelay - mcTicks);
        return despawnDelay == 0;
    }
}
