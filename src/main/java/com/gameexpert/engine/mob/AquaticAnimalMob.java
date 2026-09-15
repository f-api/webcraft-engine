package com.gameexpert.engine.mob;

import java.util.List;

/** 물속 3차원 배회, 육상 퍼덕임과 건조 피해를 공유하는 수중 동물 기반 클래스. */
abstract class AquaticAnimalMob extends Mob {
    private static final double DRY_DAMAGE_POINTS = 2.0;
    private int swimTicks;
    private double swimDx;
    private double swimDy;
    private double swimDz;
    private boolean swimming;
    private boolean hasSchoolLeader;
    private double schoolX;
    private double schoolY;
    private double schoolZ;

    AquaticAnimalMob(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }

    @Override
    public String movementMedium() {
        return swimming ? "swim" : "land";
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        int waterPresence = bodyWaterPresence(world);
        if (waterPresence == BODY_WATER_UNKNOWN) {
            hasSchoolLeader = false;
            return events;
        }
        swimming = waterPresence == BODY_WATER_WET;
        if (!swimming) {
            state = MobState.IDLE;
            double dx = 0;
            double dz = 0;
            if (onGround && isFish()) {
                dx = (rng.nextFloat() * 2.0 - 1.0) * 0.05;
                dz = (rng.nextFloat() * 2.0 - 1.0) * 0.05;
                // MobPhysics applies authority gravity before moving, leaving the official 0.4 impulse.
                vy = 0.4 + MobPhysics.GRAVITY;
                onGround = false;
                events = appendEvent(events, new MobEvent.Sound("flop"));
            }
            MobPhysics.tickMove(this, world, dx, 0, dz, MoveMode.WALK);
            if (usesGenericDryAirSupply() && tickDryAirSupply()) {
                damageBypassesArmor(DRY_DAMAGE_POINTS);
                events = appendEvent(events, new MobEvent.EnvironmentDamage(isDead()));
            }
            return events;
        }

        if (usesGenericDryAirSupply()) restoreAquaticAirSupply();
        if (hasSchoolLeader) {
            double ax = schoolX - x;
            double ay = schoolY - y;
            double az = schoolZ - z;
            double distance = Math.sqrt(ax * ax + ay * ay + az * az);
            if (distance > 1.5 && distance < 12.0) {
                double speed = type.baseSpeed();
                swimDx = ax / distance * speed;
                swimDy = ay / distance * speed * 0.55;
                swimDz = az / distance * speed;
                swimTicks = 5;
            }
        }
        hasSchoolLeader = false;
        if (swimTicks-- <= 0) {
            if (!mayStartRandomWander()) {
                swimDx = 0.0;
                swimDy = 0.0;
                swimDz = 0.0;
                state = MobState.IDLE;
                MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.SWIM);
                return events;
            }
            double angle = rng.nextDouble() * Math.PI * 2.0;
            swimDx = Math.cos(angle) * type.baseSpeed();
            swimDz = Math.sin(angle) * type.baseSpeed();
            swimDy = (rng.nextDouble() - 0.5) * type.baseSpeed() * 0.75;
            swimTicks = 12 + rng.nextInt(24);
        }
        yaw = Math.atan2(swimDz, swimDx);
        state = MobState.WANDER;
        MobPhysics.tickMove(this, world, swimDx, swimDy, swimDz, MoveMode.SWIM);
        return events;
    }

    private boolean isFish() {
        return type == MobType.COD || type == MobType.SALMON
                || type == MobType.TROPICAL_FISH || type == MobType.PUFFERFISH
                || type == MobType.TADPOLE;
    }

    /** Dolphin owns Mojang's separate moisture state and does not use WaterAnimal Air drying. */
    protected boolean usesGenericDryAirSupply() { return true; }

    /** 번식 중인 수중 동물은 물속에서만 짝에게 헤엄쳐 간다. */
    @Override
    void seekBreedingPartner(Mob partner) {
        if (partner == null || !swimming) return;
        double ax = partner.x - x;
        double ay = partner.y - y;
        double az = partner.z - z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        if (distance <= 1e-6) return;
        double speed = type.baseSpeed();
        swimDx = ax / distance * speed;
        swimDy = ay / distance * speed * 0.55;
        swimDz = az / distance * speed;
        swimTicks = 5;
    }

    @Override
    void seekSchoolLeader(Mob leader) {
        if (leader == null || leader.type != type || leader.id == id) return;
        hasSchoolLeader = true;
        schoolX = leader.x;
        schoolY = leader.y;
        schoolZ = leader.z;
    }
}
