package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/** WebCraft raid trap specialist. */
public final class WebTrapper extends RaidMeleeMob {
    public WebTrapper(long id, double x, double y, double z) {
        super(id, MobType.WEB_TRAPPER, x, y, z, 3, 2.5);
        setNativeEquipment("web_bundle");
    }

    @Override
    protected ProjectileEffect meleeEffect(MobWorldView world) {
        return MobEffectRules.WEB_TRAPPER_SLOWNESS;
    }

    @Override public void commitAcceptedMeleeAttack() {
        commitRoleAction("trap", 8);
    }

    /** 그물사는 앞의 빈 칸에만 거미줄을 놓는다. 어떤 기존 블록도 지우지 않는다. */
    @Override protected int roleMutationCap() { return 4; }

    @Override
    protected MobMutationJournal.Result requestRoleWorldMutation(
            MobWorldView world, MobMutationJournal journal) {
        int[] cell = facingCell(0);
        return journal.request(MobMutationJournal.Origin.RAID_TRAP, roleEventId(), id, roleActionId(),
                cell[0], cell[1], cell[2], Blocks.COBWEB, 0);
    }
}
