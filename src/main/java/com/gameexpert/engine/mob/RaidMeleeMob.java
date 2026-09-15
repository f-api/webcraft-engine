package com.gameexpert.engine.mob;

import java.util.List;

/** Shared movement shell for raid-only melee roles; role-specific actions remain in each subclass. */
abstract class RaidMeleeMob extends MeleeMob {
    private final int damage;
    private final double reach;
    /** 커밋된 역할 행동의 안정적 일련번호. 저널 entry key의 일부라 재시도가 중복 적용되지 않는다. */
    private long roleActionId;
    private boolean roleActionPending;
    private int acceptedRoleMutations;

    RaidMeleeMob(long id, MobType type, double x, double y, double z,
                 int damage, double reach) {
        super(id, type, x, y, z);
        this.damage = damage;
        this.reach = reach;
    }

    @Override protected double detectRange() { return 16.0; }
    @Override protected double attackRange() { return reach; }
    @Override protected int attackDamage() { return damage; }
    @Override protected int attackCooldownTicks() { return 20; }
    @Override protected boolean climbWalls() { return false; }

    protected final void commitRoleAction(String kind, int visualTicks) {
        attackCd = Math.max(1, (int) Math.ceil(
                attackCooldownTicks() * preparedRaidCooldownMultiplier()));
        markVisualAction(kind, visualTicks);
        roleActionId++;
        roleActionPending = true;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (roleActionPending) {
            roleActionPending = false;
            MobMutationJournal journal = world.mobMutationJournal();
            // 역할별 mutation target cap(그물4·파괴6·건설6)을 넘으면 더는 월드를 바꾸지 않는다.
            if (journal != null && acceptedRoleMutations < roleMutationCap()) {
                if (requestRoleWorldMutation(world, journal)
                        == MobMutationJournal.Result.ACCEPTED) {
                    acceptedRoleMutations++;
                }
            }
        }
        return events;
    }

    /** 한 개체가 이벤트 동안 남길 수 있는 최대 변형 수. 변형이 없는 역할은 0이다. */
    protected int roleMutationCap() { return 0; }

    /** 이미 저널이 받아들인 이 개체의 변형 수. 영속 복구 뒤에도 cap 을 넘지 않게 유지한다. */
    protected final int acceptedRoleMutations() { return acceptedRoleMutations; }

    /**
     * 커밋된 역할 행동이 월드에 남기는 변형을 저널에 요청한다. 저널이 기록을 마친 뒤에야 적용되고,
     * 플레이어 편집·역할 계약 위반은 저널이 거부한다. 변형이 없는 역할은 재정의하지 않는다.
     */
    protected MobMutationJournal.Result requestRoleWorldMutation(
            MobWorldView world, MobMutationJournal journal) {
        return MobMutationJournal.Result.ROLE_FORBIDDEN;
    }

    /** 현재 역할 행동 번호. 같은 행동의 재요청은 같은 번호를 써서 중복 적용을 받지 않는다. */
    protected final long roleActionId() { return roleActionId; }

    /**
     * raid 이벤트 식별자. 우민 동행/거주자 계약이 실어 주는 지속 identity 를 그대로 쓰고,
     * 아직 이벤트에 묶이지 않은 개체는 0을 쓴다(되감기 단위가 없는 개별 행동).
     */
    protected final long roleEventId() { return illagerContextIdentity(); }

    /** 몹이 바라보는 방향의 바로 앞 칸. 역할 변형의 결정적 대상 좌표다. */
    protected final int[] facingCell(int yOffset) {
        int targetX = (int) Math.floor(x - Math.sin(yaw));
        int targetZ = (int) Math.floor(z + Math.cos(yaw));
        return new int[] { targetX, (int) Math.floor(y) + yOffset, targetZ };
    }
}
