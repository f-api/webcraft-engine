package com.gameexpert.engine.mob;

import com.gameexpert.engine.mob.villager.VillagerActivityLedger;
import com.gameexpert.engine.mob.villager.VillagerBrainRules;
import com.gameexpert.engine.mob.villager.VillagerSocialState;
import com.gameexpert.engine.mob.villager.VillagerSocietyRules;

/**
 * 마을 구조가 소유하는 주민 엔티티의 권위 기반이다. 주민은 자연 몹 cap으로 생성되지 않고 항상
 * 영속하며, 전용 기억·POI 계층이 연결되기 전에도 일반 동물과 같은 배회·피격 도주만 수행한다.
 */
public final class Villager extends AnimalMob {
    private VillagerSocialState social = new VillagerSocialState();

    public Villager(long id, double x, double y, double z) {
        super(MobType.VILLAGER, id, x, y, z, null);
        setPersistenceRequired(true);
    }

    /**
     * 지금 이 주민의 활동. 정본은 {@code VillagerActivityLedger}(종·습격이 만든 HIDE/PRE_RAID/RAID
     * 포함)이고, 그 lane 이 배선되지 않은 순수 테스트 월드는 막 만들어진 뇌의 일정 값이다.
     */
    public VillagerBrainRules.Activity activity(MobWorldView world) {
        VillagerActivityLedger.Snapshot decision = world.villagerActivity(id);
        return decision != null ? decision.activity()
                : VillagerActivityLedger.fallbackActivity(
                        VillagerSocietyRules.mcDayTime(world.worldTime()), isBaby());
    }

    /** 지금 활동이 향하는 POI. WORK=직장, MEET=집합점, REST=침대, 사건 활동은 없음. */
    public VillagerBrainRules.ActivityDestination activityDestination(MobWorldView world) {
        return VillagerBrainRules.activityDestination(activity(world));
    }

    public VillagerSocialState social() { return social; }

    /** 바닐라 거래 거부의 40 MC틱 head-shake. */
    public void rejectTradeVisual() { markVisualAction("no", 20); }

    @Override public int visualFlags() { return villagerAppearanceFlags(); }

    /**
     * 이 주민이 속한 마을의 앵커 {@code {x,y,z}} 또는 {@code null}. 바닐라 {@code PoiManager}
     * 마을 인덱스가 없는 자리를 대신하며, POI 기억이 하나도 없는 주민의 울타리 원점이다.
     */
    public int[] homeVillageAnchor() { return social.homeVillageAnchor(); }

    /** 마을 site 가 이 주민을 놓은 칸을 앵커로 기억한다. 이미 있으면 그대로 둔다. */
    public boolean rememberHomeVillage(int x, int y, int z) {
        return social.rememberHomeVillage(x, y, z);
    }

    /** 영속용 한 줄. 저장할 것이 없으면 null 이다. */
    public String encodeSocialState() { return social.encode(); }

    /** 재접속·언로드 복구 경로. 손상된 문자열은 빈 사회 기억으로 읽힌다. */
    public void restoreSocialState(String encoded) {
        social = VillagerSocialState.decode(encoded);
    }

    @Override
    protected boolean mayWander(MobWorldView world) {
        decayGossip(world);
        return strollFactor(world) > 0.0;
    }

    /**
     * 걷기 목표. 활동 원장의 WALK_TARGET(바닐라 {@code LocateHidingPlace} 가 은신처로 둔 칸)이 먼저이고,
     * 없으면 이번 틱 배정 lane 이 정한 직업지로 WORK 배속(0.4)으로 걷는다(바닐라
     * {@code SetWalkTargetFromBlockMemory}/{@code WorkAtPoi}). 목표를 고르는 판정은 전부 두 lane 이
     * 이미 끝냈고 여기서는 변위만 만든다.
     */
    @Override
    protected double[] scheduledWalk(MobWorldView world) {
        VillagerActivityLedger.Snapshot decision = world.villagerActivity(id);
        if (decision != null && decision.walkTarget() != null) {
            int[] hide = decision.walkTarget();
            return towardHoriz(hide[0] + 0.5, hide[2] + 0.5,
                    type.baseSpeed() * decision.walkSpeedFactor());
        }
        int[] target = world.villagerJobWalkTarget(id);
        if (target == null) return null;
        decayGossip(world);
        double speed = type.baseSpeed() * VillagerBrainRules.WORK_WALK_SPEED_MODIFIER;
        return towardHoriz(target[0] + 0.5, target[2] + 0.5, speed);
    }

    @Override
    protected double idleMoveSpeed(MobWorldView world) {
        decayGossip(world);
        double base = type.baseSpeed() * strollFactor(world);
        VillagerBrainRules.ActivityDestination destination = activityDestination(world);
        if (destination == VillagerBrainRules.ActivityDestination.NONE) return base;
        return base * VillagerBrainRules.destinationSpeedModifier(destination);
    }

    /** 활동이 허락하는 배회 배속. 0 이면 배회하지 않는다(REST·HIDE·진행 중인 RAID). */
    private double strollFactor(MobWorldView world) {
        VillagerActivityLedger.Snapshot decision = world.villagerActivity(id);
        return decision != null ? decision.strollFactor()
                : VillagerActivityLedger.strollFactor(activity(world), false);
    }

    /**
     * 하루가 지났으면 gossip 을 감쇠한다(바닐라 {@code maybeDecayGossip}). 감쇠 시각의 축은 영속된
     * {@code lastGossipDecayTime} 과 같은 {@code dayCount*24000 + worldTime} 을 그대로 쓴다.
     */
    private void decayGossip(MobWorldView world) {
        long gameTime = world.dayCount() * VillagerBrainRules.DAY_LENGTH_TICKS
                + Math.floorMod(world.worldTime(), VillagerBrainRules.DAY_LENGTH_TICKS);
        social.maybeDecayGossip(gameTime);
    }
}
