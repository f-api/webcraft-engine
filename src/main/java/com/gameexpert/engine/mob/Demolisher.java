package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/** WebCraft raid structure demolisher. */
public final class Demolisher extends RaidMeleeMob {
    public Demolisher(long id, double x, double y, double z) {
        this(id, x, y, z, 0);
    }

    public Demolisher(long id, double x, double y, double z, int worldSeed) {
        super(id, MobType.DEMOLISHER, x, y, z, 5, 2.5);
        configurePickaxe(worldSeed, 0);
    }

    static short pickaxeForRoll(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("roll");
        if (roll < 55) return PlayerInventory.STONE_PICKAXE;
        if (roll < 90) return PlayerInventory.IRON_PICKAXE;
        return PlayerInventory.DIAMOND_PICKAXE;
    }

    void configurePickaxe(int worldSeed, long contextIdentity) {
        installGeneratedHeldItem(generatedPickaxe(worldSeed, contextIdentity, id));
    }

    static short generatedPickaxe(int worldSeed, long contextIdentity, long mobId) {
        int bits = worldSeed ^ (int) mobId ^ (int) (mobId >>> 32)
                ^ (int) contextIdentity ^ (int) (contextIdentity >>> 32);
        bits ^= bits >>> 16;
        bits *= 0x7feb352d;
        bits ^= bits >>> 15;
        bits *= 0x846ca68b;
        bits ^= bits >>> 16;
        return pickaxeForRoll(Math.floorMod(bits, 100));
    }

    @Override public void commitAcceptedMeleeAttack() {
        commitRoleAction("demolish", 20);
    }

    /**
     * 파괴는 raid 대상 생성 구조물 블록만 노린다. 자연 지형·플레이어 편집·컨테이너는 저널이 거부한다.
     */
    @Override protected int roleMutationCap() { return 6; }

    @Override
    protected MobMutationJournal.Result requestRoleWorldMutation(
            MobWorldView world, MobMutationJournal journal) {
        int[] cell = facingCell(1);
        return journal.request(MobMutationJournal.Origin.RAID_DEMOLISH, roleEventId(), id, roleActionId(),
                cell[0], cell[1], cell[2], com.gameexpert.terrain.Blocks.AIR, 0);
    }
}
