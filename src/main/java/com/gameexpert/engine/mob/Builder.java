package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/** WebCraft raid combat builder. */
public final class Builder extends RaidMeleeMob {
    public Builder(long id, double x, double y, double z) {
        super(id, MobType.BUILDER, x, y, z, 4, 2.5);
        setNativeEquipment("builder_kit");
    }

    @Override public void commitAcceptedMeleeAttack() {
        commitRoleAction("build", 12);
    }

    /** 건설자는 앞의 빈 칸에 plank 엄폐물만 세운다. 지형·구조물·플레이어 편집을 대체하지 않는다. */
    @Override protected int roleMutationCap() { return 6; }

    @Override
    protected MobMutationJournal.Result requestRoleWorldMutation(
            MobWorldView world, MobMutationJournal journal) {
        int[] cell = facingCell(0);
        return journal.request(MobMutationJournal.Origin.RAID_BUILD, roleEventId(), id, roleActionId(),
                cell[0], cell[1], cell[2], Blocks.PLANK, 0);
    }
}
