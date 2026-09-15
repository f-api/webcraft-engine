package com.gameexpert.engine.mob;

import com.gameexpert.engine.dragon.DragonBrain;
import java.util.List;

/**
 * [DRAGON] 엔더 드래곤(바닐라 {@code EnderDragon}, stableId 102). 비행·부위 판정·단계 AI·사망 연출은 엔드 차원
 * 드래곤전({@code engine.dragon.DragonFight})의 {@link DragonBrain} 이 소유하고, 이 몹 행은 원장 표현(위치 · 방향 ·
 * 체력 · 부위 상자)만 싣는다. 그래서 일반 몹 AI 틱은 아무것도 하지 않고, 일반 피해 경로({@link #damage})도
 * 받지 않는다 — 플레이어 근접·투사체·폭발은 권위가 부위를 골라 두뇌의 {@code hurt(part, …)} 로 보낸다.
 * 바닐라처럼 불에 면역이다({@code EntityType.ENDER_DRAGON.fireImmune()}).
 */
public final class EnderDragon extends Mob {
    private double[][] parts;

    public EnderDragon(long id, double x, double y, double z) {
        super(id, MobType.ENDER_DRAGON, x, y, z);
    }

    @Override
    public boolean immovable() {
        return true;
    }

    /** [DRAGON] 드래곤은 걷지 않는다: 날갯짓(entity.ender_dragon.flap)은 클라 틱이 낸다. ambient(growl)는 남는다. */
    @Override public boolean hasStepSound() { return false; }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean damage(double amount, long tickNo) {
        return false;
    }

    @Override
    public double[][] partHitBoxes() {
        return parts;
    }

    /**
     * 두뇌의 이번 틱 결과를 원장에 싣는다. 방향은 바닐라 yRot(도, 0 = +Z)을 라디안으로 옮긴 값이다. 사망 연출 중
     * 체력 0 은 몹 원장의 사망(제거)과 구별되도록 아주 작은 값으로 싣고, 연출이 끝나면 권위가 0 으로 내린다.
     */
    public void syncFromBrain(DragonBrain brain) {
        x = brain.x;
        y = brain.y;
        z = brain.z;
        yaw = Math.toRadians(brain.yRot);
        double[][] boxes = new double[8][];
        for (int part = 0; part < 8; part++) boxes[part] = brain.partBox(part);
        parts = boxes;
        setInitialHealth(Math.max(brain.health(), 1.0e-3));
    }

    /** 사망 연출이 끝났다: 체력 0(런타임이 틱 끝에 사망 퇴장시킨다). */
    public void finishDeath() {
        setInitialHealth(0.0);
    }

    /** 이 좌표에 가장 가까운 부위({@code EnderDragonPart} 순서 head, neck, body, tail1..3, wing1, wing2). */
    public int partNearest(double px, double py, double pz) {
        if (parts == null) return DragonBrain.PART_BODY;
        int best = DragonBrain.PART_BODY;
        double bestDistance = Double.MAX_VALUE;
        for (int part = 0; part < parts.length; part++) {
            double[] box = parts[part];
            double dx = Math.max(Math.max(box[0] - px, 0.0), px - box[3]);
            double dy = Math.max(Math.max(box[1] - py, 0.0), py - box[4]);
            double dz = Math.max(Math.max(box[2] - pz, 0.0), pz - box[5]);
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = part;
            }
        }
        return best;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        return List.of();
    }
}
