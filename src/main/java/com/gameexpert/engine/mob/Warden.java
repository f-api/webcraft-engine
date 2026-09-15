package com.gameexpert.engine.mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 워든(바닐라 실존 종, stableId 97). 수치 근거는 {@link MobType#WARDEN} 의 인라인 인용이다
 * (MC Java 1.21.4 {@code EntityType.WARDEN sized(0.9F, 2.9F)} · {@code createAttributes()}
 * MAX_HEALTH 500 · MOVEMENT_SPEED 0.3 · KNOCKBACK_RESISTANCE 1.0 · ATTACK_KNOCKBACK 1.5 ·
 * ATTACK_DAMAGE 30 · FOLLOW_RANGE 24).
 *
 * <p><b>출현</b>: 자연 스폰 항목이 없다. 스컬크 비명체의 4단계 경고가 유일한 출처이고,
 * 그 자리는 {@code SculkVibrationRules.WardenSummonSink} 로 이미 뚫려 있다 — 지금까지
 * {@code NO_OP} 였던 이유는 "종이 등록되지 않았다" 하나뿐이다. 이 웨이브가 그 싱크를
 * 교체한다. 솟는 연출은 {@value #EMERGE_TICKS} 틱이고 그동안 무적·부동이다.
 *
 * <p><b>진동 기반 탐지</b>: 시각을 쓰지 않고 스컬크 진동 사슬을 소비한다
 * ({@code SculkVibrationSystem} 이 이미 그 사슬을 갖고 있다). 분노는
 * {@code AngerManagement} 규약대로 <b>대상별 누적</b>이며
 * {@value #ANGER_ATTACK_INCREMENT}(피격) · {@value #ANGER_VIBRATION_INCREMENT}(진동) ·
 * {@value #ANGER_TOUCH_INCREMENT}(접촉)씩 쌓이고, {@value #ANGER_DECAY_TICKS} 틱마다 1씩
 * 줄어 0 이 되면 표적을 잊는다. 가장 많이 쌓인 대상이 표적이다.
 *
 * <p><b>어그로 표</b>(바닐라 {@code Warden#increaseAngerAt} 호출부)
 * <table>
 *   <caption>분노 증가량</caption>
 *   <tr><th>사건</th><th>증가</th></tr>
 *   <tr><td>직접 피격</td><td>{@value #ANGER_ATTACK_INCREMENT}</td></tr>
 *   <tr><td>진동 감지</td><td>{@value #ANGER_VIBRATION_INCREMENT}</td></tr>
 *   <tr><td>접촉(밀림)</td><td>{@value #ANGER_TOUCH_INCREMENT}</td></tr>
 * </table>
 * 분노가 {@value #ANGRY_THRESHOLD} 이상이면 추격하고 그 아래면 소리 쪽으로 걸어간다.
 *
 * <p><b>원거리 음파</b>(sonic boom): {@value #SONIC_BOOM_RANGE} 블록까지 직선으로 꽂히며
 * {@value #SONIC_BOOM_DAMAGE} 피해를 준다. 근접 피해는 {@value #MELEE_DAMAGE} 다(어려움 기준
 * 바닐라 30). 이 웨이브가 <b>발사 자체</b>를 배선한다: 근접 사거리 밖·음파 사거리 안·시야가
 * 통하는 표적에게 {@value #SONIC_BOOM_COOLDOWN_TICKS} 틱마다 한 발. <b>아직 배선하지 않은 것</b>은
 * 방어구·방패 무시다 — 몹 → 플레이어 피해는 {@code MobEvent.AttackPlayer} 하나로 흐르고 그
 * 소비 지점이 방어구·방패를 강제로 적용한다. 무시 플래그는 피해원(damage source) 계약을 여는
 * 별도 트랙이며, 여기서는 그 자리에 표식을 남긴다.
 *
 * <p><b>이탈 시 굴착 소멸</b>: {@value #DIG_DOWN_IDLE_TICKS} 틱 동안 표적이 없으면 땅으로
 * 파고들어 사라진다.
 *
 * <p><b>darkness 는 부여하지 않는다</b> — 스컬크 비명체가 이미 담당하고 있고, 워든이 또
 * 부여하면 같은 효과가 두 출처에서 겹친다.
 */
public final class Warden extends MeleeMob {

    /** 솟는 연출 틱. 그동안 무적·부동이다. */
    public static final int EMERGE_TICKS = 134;
    /** 직접 피격 분노 증가량. */
    public static final int ANGER_ATTACK_INCREMENT = 35;
    /** 진동 감지 분노 증가량. */
    public static final int ANGER_VIBRATION_INCREMENT = 10;
    /** 접촉 분노 증가량. */
    public static final int ANGER_TOUCH_INCREMENT = 20;
    /** 추격으로 넘어가는 분노 임계. */
    public static final int ANGRY_THRESHOLD = 80;
    /** 분노가 1 줄어드는 주기(틱). */
    public static final int ANGER_DECAY_TICKS = 20;
    /** 근접 피해(바닐라 ATTACK_DAMAGE 30). */
    public static final int MELEE_DAMAGE = 30;
    /**
     * 음파 피해. 방어구·방패를 무시한다.
     *
     * <p><b>10 이다(15 에서 교정).</b> [B] MC Java 1.21.6
     * {@code ai.behavior.warden.SonicBoom} 은 {@code target.hurt(sonicBoom(warden), 10.0F)} 로
     * <b>난이도 배율이 붙기 전의 기본값 10</b> 을 넘긴다. 위키 표의 15 는 <b>어려움</b> 열
     * (기본값 × 1.5)이다 — [B] minecraft.wiki «Warden»: 음파 보통 10 · 어려움 15, 근접 보통 30 ·
     * 어려움 45.
     *
     * <p>이 저장소의 피해 스케일은 <b>배율 이전의 기본 어트리뷰트</b>로 통일돼 있다:
     * {@code Zombie#attackDamage} 3(바닐라 기본 3, 어려움 4.5) · {@link #MELEE_DAMAGE} 30
     * (바닐라 기본 30, 어려움 45)이고, {@link com.gameexpert.engine.Difficulty} 는 지역/전역
     * 난이도 피해 배율을 아예 도입하지 않았다. 그래서 여기만 어려움 열 값을 쓰면 워든의 근접
     * (기본값)과 음파(어려움값)가 서로 다른 난이도의 표에서 온 값이 되고, 플레이어 최대 체력
     * 20 대비 음파 한 방이 체력의 3/4 를 가져간다. 정적판 사본은
     * {@code STANDALONE_WARDEN_SONIC_BOOM_DAMAGE} 이며 같은 값이다.
     */
    public static final int SONIC_BOOM_DAMAGE = 10;
    /** 음파 사거리(블록). */
    public static final double SONIC_BOOM_RANGE = 15.0;
    /** 표적이 없을 때 굴착 소멸까지의 틱. */
    public static final int DIG_DOWN_IDLE_TICKS = 1_200;
    /**
     * 음파 재발사 간격(MC 틱). 바닐라 {@code SonicBoom} 행동은 {@code COOLDOWN} 40 틱을 쓴다.
     * [B] MC Java 1.21.4 {@code ai.behavior.warden.SonicBoom} — {@code TICKS_BEFORE_PLAYING_SOUND=34}
     * · 행동 종료 뒤 {@code SONIC_BOOM_COOLDOWN} 40 틱.
     */
    public static final int SONIC_BOOM_COOLDOWN_TICKS = 40;

    /**
     * MC 틱을 권위 틱(10 TPS)으로 접는 나눗셈. {@code StatusEffects.MC_TICKS_PER_SERVER_TICK} 와
     * 같은 규약이며, 이 클래스가 바깥 상수에 의존하지 않도록 여기서 한 번만 접는다.
     */
    private static final int MC_TICKS_PER_AUTHORITY_TICK = 2;

    /** 솟는 연출의 권위 틱 길이. 그동안 무적·부동이다. */
    public static final int EMERGE_AUTHORITY_TICKS = EMERGE_TICKS / MC_TICKS_PER_AUTHORITY_TICK;
    /** 분노가 1 줄어드는 권위 틱 주기. */
    public static final int ANGER_DECAY_AUTHORITY_TICKS =
            ANGER_DECAY_TICKS / MC_TICKS_PER_AUTHORITY_TICK;
    /** 음파 재발사 간격의 권위 틱. */
    public static final int SONIC_BOOM_COOLDOWN_AUTHORITY_TICKS =
            SONIC_BOOM_COOLDOWN_TICKS / MC_TICKS_PER_AUTHORITY_TICK;
    /** 연속으로 표적이 없는 권위 틱 수. 출현 연출은 이 시간에 포함하지 않는다. */
    /**
     * [MOB-LOOK] {@code WardenAi.DIGGING_DURATION = Mth.ceil(100)} MC ticks of {@code Pose.DIGGING}
     * ({@code Digging} behaviour) before {@code remove(DISCARDED)}; invulnerable and still meanwhile
     * ({@code isDiggingOrEmerging}).
     */
    public static final int DIG_TICKS = 100;
    public static final int DIG_AUTHORITY_TICKS = DIG_TICKS / MC_TICKS_PER_AUTHORITY_TICK;
    /** [MOB-LOOK] Species-scoped visual bits: Pose.EMERGING / Pose.DIGGING (client WardenModel clips). */
    public static final int VISUAL_EMERGING = Mob.VISUAL_WARDEN_EMERGING;
    public static final int VISUAL_DIGGING = Mob.VISUAL_WARDEN_DIGGING;
    public static final int DIG_DOWN_IDLE_AUTHORITY_TICKS =
            DIG_DOWN_IDLE_TICKS / MC_TICKS_PER_AUTHORITY_TICK;

    /**
     * 남은 출현 연출 틱. 소환 싱크가 {@link #beginEmerge()} 로 세운다 — <b>생성자에서 세우지
     * 않는다</b>. 생성자에서 세우면 청크 재적재로 복원된 워든이 다시 땅에서 솟고, 종 트레이스가
     * "만들자마자 부동" 으로 굳어 자연 스폰 없는 종의 기본 상태가 바뀐다.
     */
    private int emergeTicks;
    /** [MOB-LOOK] Remaining {@code Pose.DIGGING} ticks before the warden leaves (0 = not digging). */
    private int digTicks;
    /** 대상별 누적 분노. 바닐라 {@code AngerManagement} 의 대상별 표와 같은 자리다. */
    private final Map<String, Integer> anger = new HashMap<>();
    /** 다음 분노 감쇠까지 남은 권위 틱. */
    private int angerDecayCountdown = ANGER_DECAY_AUTHORITY_TICKS;
    /** 남은 음파 쿨다운(권위 틱). */
    private int sonicBoomCooldown;
    /** 다른 워든 행동 타이머와 같은 비영속 활성 시뮬레이션 상태다. */
    private int untargetedTicks;

    public Warden(long id, double x, double y, double z) {
        super(id, MobType.WARDEN, x, y, z);
    }

    /**
     * 소환 직후의 출현 연출을 연다. 소환 싱크가 개체를 만든 <b>바로 그 자리</b>에서 부른다.
     * 연출 중에는 이동·공격이 없고 들어오는 피해가 0 이다(바닐라 {@code WardenAi} 의
     * {@code Emerging} 활동과 {@code Warden#isInvulnerableTo} 가 같은 창을 쓴다).
     */
    public void beginEmerge() {
        emergeTicks = EMERGE_AUTHORITY_TICKS;
        untargetedTicks = 0;
    }

    /** 지금 솟는 중인가(무적·부동). */
    public boolean emerging() {
        return emergeTicks > 0;
    }

    /** 남은 출현 연출 틱(진단·테스트용). */
    public int emergeTicksRemaining() {
        return emergeTicks;
    }

    /**
     * 바닐라 {@code Warden#increaseAngerAt}. 대상별로 쌓고, 상한은 바닐라
     * {@code AngerManagement.MAX_ANGER} 150 이다. [B] MC Java 1.21.4.
     */
    private int vibrationCooldown;

    /** 진동 반경16, cooldown40MC틱; 차분할 때84MC틱 냄새 맡기 자세를 시작한다. */
    public void hearVibration(int vx, int vy, int vz, String sourceNickname) {
        if (isDead() || removed || emergeTicks > 0 || digTicks > 0 || vibrationCooldown > 0) return;
        double dx = vx + 0.5 - x, dy = vy + 0.5 - (y + eyeHeight()), dz = vz + 0.5 - z;
        if (dx * dx + dy * dy + dz * dz > 16 * 16) return;
        vibrationCooldown = 20;
        untargetedTicks = 0;
        increaseAngerAt(sourceNickname, ANGER_VIBRATION_INCREMENT);
        if (angerAt(angriestTarget()) < ANGRY_THRESHOLD) {
            faceToward(vx + 0.5, vz + 0.5);
            markVisualAction("sniff", 42);
        }
    }

    public void increaseAngerAt(String nickname, int amount) {
        if (nickname == null || amount <= 0) return;
        anger.merge(nickname, amount, (a, b) -> Math.min(MAX_ANGER, a + b));
        String angriest = angriestTarget();
        if (angriest != null) forceTarget(angriest);
    }

    /** 바닐라 {@code AngerManagement.MAX_ANGER}. [B] */
    public static final int MAX_ANGER = 150;

    /**
     * 이미 이 거리 안에 워든이 있으면 새로 부르지 않고 그 개체를 화나게 한다.
     * [B] MC Java 1.21.4 {@code WardenSpawnTracker#hasNearbyWarden} — 48 블록.
     */
    public static final double NEARBY_WARDEN_RADIUS = 48.0;

    /** 이 대상에게 쌓인 분노. */
    public int angerAt(String nickname) {
        return nickname == null ? 0 : anger.getOrDefault(nickname, 0);
    }

    /**
     * 바닐라 {@code Warden#hurtServer} 의 직접 가해자 분노 경계. 공통 전투 시스템은 유효한
     * 비치명 피격 뒤에만 이 훅을 부르므로 피격 무적으로 거부된 휘두르기는 분노를 올리지 않는다.
     */
    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        if (attackerNickname == null) return;
        increaseAngerAt(attackerNickname, ANGER_ATTACK_INCREMENT);
        forceTarget(attackerNickname);
    }

    /** 가장 많이 쌓인 대상. 동률이면 닉네임 순으로 갈라 두 권위가 같은 답을 낸다. */
    public String angriestTarget() {
        String best = null;
        int bestAnger = 0;
        for (Map.Entry<String, Integer> entry : anger.entrySet()) {
            int value = entry.getValue();
            if (value <= 0) continue;
            if (value > bestAnger
                    || (value == bestAnger && best != null && entry.getKey().compareTo(best) < 0)) {
                best = entry.getKey();
                bestAnger = value;
            }
        }
        return best;
    }

    /** [MOB-LOOK] Sinking back into the ground ({@code Pose.DIGGING}). */
    public boolean digging() {
        return digTicks > 0;
    }

    @Override public int visualFlags() {
        return (emerging() ? VISUAL_EMERGING : 0) | (digging() ? VISUAL_DIGGING : 0);
    }

    /** 출현 연출 중에는 어떤 피해도 통하지 않는다. [MOB-LOOK] 파고드는 중도 같다(isDiggingOrEmerging). */
    @Override protected double adjustIncomingDamage(double rawAmount) {
        return emerging() || digging() ? 0.0 : rawAmount;
    }

    /**
     * 한 틱. 순서는 두 권위가 같아야 한다(정적판 사본은
     * {@code StandaloneMobRuntime.tickWarden}):
     * ① 출현 연출 → ② 분노 감쇠 → ③ 음파 쿨다운 → ④ 무표적 귀환 → ⑤ 음파·근접 골격.
     */
    @Override public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead() || removed) return List.of();
        if (vibrationCooldown > 0) vibrationCooldown--;
        if (emergeTicks > 0) {
            emergeTicks--;
            state = MobState.IDLE;
            return List.of();
        }
        // [MOB-LOOK] Digging: still and invulnerable for DIG_TICKS, then discarded.
        if (digTicks > 0) {
            state = MobState.IDLE;
            if (--digTicks == 0) {
                removed = true;
                // 생존 상태로 원장·영속 행만 닫는다. 사유가 없으면 바깥 방송이 death로 분류한다.
                return List.of(new MobEvent.Despawned("far"));
            }
            return List.of();
        }
        if ("sniff".equals(actionKind()) && "active".equals(actionPhase())) {
            state = MobState.IDLE;
            return List.of();
        }
        if (--angerDecayCountdown <= 0) {
            angerDecayCountdown = ANGER_DECAY_AUTHORITY_TICKS;
            anger.replaceAll((ignored, value) -> value - 1);
            anger.values().removeIf(value -> value <= 0);
        }
        if (angriestTarget() == null) clearTrackedTarget();
        if (sonicBoomCooldown > 0) sonicBoomCooldown--;
        // 분노 원장은 여기서 <b>표적을 고르지 않는다</b>. 바닐라 워든은 시각이 아니라
        // AngerManagement 의 최다 누적 대상을 쫓지만, 그 조향은 "진동을 표적 후보로 바꾸는"
        // 별도 계약(진동 사슬 → 몹 원장 연결)을 요구한다. 이 웨이브가 세우는 것은 원장·감쇠·
        // 소환 어그로까지이고, 추격 표적은 아직 근접 골격의 시야 판정이 고른다.
        PlayerSnapshot target = trackedTarget(world, detectRange(), false);
        if (target == null) {
            // 거리/자격 판정으로 표적을 잃은 이 틱이 첫 무표적 틱이다. 분노 잔량과는 별개다.
            if (++untargetedTicks >= DIG_DOWN_IDLE_AUTHORITY_TICKS) {
                // [MOB-LOOK] WardenAi: DIG_COOLDOWN elapsed without a target → the Digging behaviour.
                digTicks = DIG_AUTHORITY_TICKS;
                state = MobState.IDLE;
                return List.of();
            }
        } else {
            untargetedTicks = 0;
        }
        if (target != null) {
            double distance = dist3d(target);
            if (sonicBoomCooldown == 0 && distance > attackRange()
                    && distance <= SONIC_BOOM_RANGE && canSeeTargetNow(world, target)) {
                faceToward(target.x(), target.z());
                state = MobState.ATTACK;
                sonicBoomCooldown = SONIC_BOOM_COOLDOWN_AUTHORITY_TICKS;
                // 음파는 근접 쿨다운을 쓰지 않는다 — 바닐라도 두 행동의 쿨다운이 따로다.
                return List.of(new MobEvent.AttackPlayer(
                        target.nickname(), SONIC_BOOM_DAMAGE, x, z));
            }
        }
        return super.tick(world, rng);
    }

    @Override protected double detectRange() { return 24.0; }
    @Override protected boolean mayAcquireVisualTarget(MobWorldView world) { return false; }
    @Override protected double attackRange() { return 3.0; }
    @Override protected int attackDamage() { return MELEE_DAMAGE; }
    @Override protected int attackCooldownTicks() { return 20; }
    @Override protected boolean climbWalls() { return false; }

    /** 바닐라 KNOCKBACK_RESISTANCE 1.0 — 일반 넉백이 통하지 않는다(철 골렘과 같은 자리). */
    @Override public void applyKnockback(double baseX, double baseZ, boolean grounded,
                                         double bonusX, double bonusZ) {
        // Fully resisted.
    }

    @Override public void applyExplosionKnockback(double x, double y, double z) {
        // Fully resisted.
    }
}
