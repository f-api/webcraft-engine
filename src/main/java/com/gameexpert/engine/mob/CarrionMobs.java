package com.gameexpert.engine.mob;

import java.util.List;

/**
 * WebCraft 창작 좀비 동물 세 종(stableId 82~84)의 AI. 셋은 서로 다른 골격을 쓰지만 같은
 * 설계 의도를 나눠 갖는다 — <b>정면으로 밀어붙이지 않는 위협</b>이다. 수치·근거는 각각
 * {@link CarrionStagRules} · {@link CarrionBoarRules} · {@link CarrionCrowRules} 가 소유한다.
 */
public final class CarrionMobs {

    private CarrionMobs() {
    }

    /**
     * 무덤 사슴. <b>도망가다 반격</b>한다.
     *
     * <p>구현은 상태 하나({@code provoked})와 남은 돌진 횟수 하나뿐이다:
     * <ul>
     *   <li>도발 전 — 공격 사거리를 0 으로 낮춰 <b>공격 상태에 들어갈 수 없게</b> 하고,
     *       추격 이동 벡터를 표적 <b>반대</b>로 뒤집어 {@value CarrionStagRules#FLEE_BLOCKS_PER_TICK}
     *       블록/틱으로 달아난다(플레이어 질주보다 빠르다 — 쫓아가서 잡을 수 없다).</li>
     *   <li>피격 순간 — {@link #onHurt} 가 도발을 세우고 뿔 돌진 횟수를
     *       {@value CarrionStagRules#CHARGE_LIMIT} 로 채운다.</li>
     *   <li>돌진을 다 쓰면 다시 도주로 돌아간다.</li>
     * </ul>
     * 어느 분기도 난수를 소비하지 않는다.
     */
    public static final class CarrionStag extends ChargingUndeadAnimal {
        private boolean provoked;
        private int chargesLeft;

        public CarrionStag(long id, double x, double y, double z) {
            super(id, MobType.CARRION_STAG, x, y, z);
        }

        @Override protected double detectRange() { return CarrionStagRules.DETECT_RANGE; }
        /** 도발 전에는 0 이라 공격 판정이 서지 않는다(도망만 친다). */
        @Override protected double attackRange() {
            return provoked ? CarrionStagRules.ATTACK_RANGE : 0.0;
        }
        @Override protected int attackDamage() { return CarrionStagRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return CarrionStagRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return CarrionStagRules.IDLE_BLOCKS_PER_TICK;
        }
        @Override protected double pursuitSpeed() {
            return CarrionStagRules.PURSUIT_BLOCKS_PER_TICK;
        }
        @Override protected boolean mayCharge() { return chargesLeft > 0; }
        @Override protected void onChargeStarted() { chargesLeft--; }

        /** 도발 상태인가. 정적판 대조·테스트가 읽는다. */
        public boolean provoked() { return provoked; }
        /** 남은 뿔 돌진 횟수. */
        public int chargesLeft() { return chargesLeft; }

        @Override
        public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
            if (attackerNickname == null) return;
            provoked = true;
            chargesLeft = CarrionStagRules.CHARGE_LIMIT;
            forceTarget(attackerNickname);
        }

        @Override
        protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                         MobRandom rng) {
            if (provoked && chargesLeft <= 0 && !charging()) {
                // 뿔을 다 썼다 — 다시 달아난다. 돌진 상태를 되돌려 다음 조우를 깨끗이 시작한다.
                provoked = false;
                resetCharge();
                clearTrackedTarget();
            }
            if (!provoked) return awayFrom(target);
            return super.chaseMovement(world, target, rng);
        }

        /** 표적 반대 방향의 도주 벡터. 겹쳐 있으면 현재 yaw 반대편으로 밀어낸다. */
        private double[] awayFrom(PlayerSnapshot target) {
            double ax = x - target.x();
            double az = z - target.z();
            double d = Math.sqrt(ax * ax + az * az);
            double speed = CarrionStagRules.FLEE_BLOCKS_PER_TICK;
            if (d < 1e-9) {
                return new double[]{Math.cos(yaw) * speed, Math.sin(yaw) * speed};
            }
            return new double[]{ax / d * speed, az / d * speed};
        }
    }

    /**
     * 부패 멧돼지. 처음부터 적대이며 <b>엄니 돌진을 횟수 제한 없이</b> 반복한다.
     * 굼떠서 옆으로 흘리면 지나가지만 정면으로 받으면 좀비곰보다 아프다.
     */
    public static final class CarrionBoar extends ChargingUndeadAnimal {
        public CarrionBoar(long id, double x, double y, double z) {
            super(id, MobType.CARRION_BOAR, x, y, z);
        }

        @Override protected double detectRange() { return CarrionBoarRules.DETECT_RANGE; }
        @Override protected double attackRange() { return CarrionBoarRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return CarrionBoarRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return CarrionBoarRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return CarrionBoarRules.IDLE_BLOCKS_PER_TICK;
        }
        @Override protected double pursuitSpeed() {
            return CarrionBoarRules.PURSUIT_BLOCKS_PER_TICK;
        }
    }

    /**
     * 시체 까마귀. <b>공격하지 않는</b> 비행 신호 종이다.
     *
     * <p>스폰이 좀비 동물 무리 굴림에 부수하며({@code MobSpawner#tryZombieHerdPack}), 그때
     * 무리 중심 좌표를 선회 축으로 받는다. 축을 받은 개체는 반경
     * {@value CarrionCrowRules#ORBIT_RADIUS} · 고도 {@value CarrionCrowRules#ORBIT_ALTITUDE} 에서
     * 틱당 {@value CarrionCrowRules#ORBIT_RADIANS_PER_TICK} 라디안씩 도는 <b>결정적 궤도</b>를
     * 따른다 — 난수를 하나도 소비하지 않으므로 정적판 사본과 틱 단위로 같은 좌표를 낸다.
     * 축이 없으면(영속 복원 등) 제자리 선회로 떨어진다.
     */
    public static final class CarrionCrow extends Mob {
        private final double orbitCenterX;
        private final double orbitCenterZ;
        private final double orbitBaseY;
        private double orbitAngle;

        public CarrionCrow(long id, double x, double y, double z) {
            this(id, x, y, z, x, z, y - CarrionCrowRules.ORBIT_ALTITUDE);
        }

        /** 무리 중심을 축으로 받는 스폰 경로. */
        public CarrionCrow(long id, double x, double y, double z,
                           double centerX, double centerZ, double baseY) {
            super(id, MobType.CARRION_CROW, x, y, z);
            this.orbitCenterX = centerX;
            this.orbitCenterZ = centerZ;
            this.orbitBaseY = baseY;
            this.orbitAngle = Math.atan2(z - centerZ, x - centerX);
            this.state = MobState.WANDER;
        }

        @Override public String movementMedium() { return "fly"; }

        /** 선회 축 X. 정적판 대조가 읽는다. */
        public double orbitCenterX() { return orbitCenterX; }
        /** 선회 축 Z. */
        public double orbitCenterZ() { return orbitCenterZ; }
        /** 현재 선회 각도(라디안). */
        public double orbitAngle() { return orbitAngle; }

        @Override
        public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
            if (isDead()) return List.of();
            orbitAngle += CarrionCrowRules.ORBIT_RADIANS_PER_TICK;
            double nextX = orbitCenterX + Math.cos(orbitAngle) * CarrionCrowRules.ORBIT_RADIUS;
            double nextZ = orbitCenterZ + Math.sin(orbitAngle) * CarrionCrowRules.ORBIT_RADIUS;
            double targetY = orbitBaseY + CarrionCrowRules.ORBIT_ALTITUDE;
            double dx = clampStep(nextX - x);
            double dz = clampStep(nextZ - z);
            double dy = clampStep(targetY - y);
            // 선회 접선을 바라본다 — 궤도를 도는 모습이 멀리서 읽혀야 하는 종이다.
            faceToward(nextX + Math.cos(orbitAngle + Math.PI / 2.0),
                    nextZ + Math.sin(orbitAngle + Math.PI / 2.0));
            state = MobState.WANDER;
            MobPhysics.tickMove(this, world, dx, dy, dz, MoveMode.FLY);
            return List.of();
        }

        /** 한 틱 이동량을 비행 속도로 자른다. */
        private static double clampStep(double delta) {
            double limit = CarrionCrowRules.FLY_BLOCKS_PER_TICK;
            return delta > limit ? limit : delta < -limit ? -limit : delta;
        }
    }
}
