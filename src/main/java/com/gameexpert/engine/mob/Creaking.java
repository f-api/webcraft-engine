package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.WorldClock;

/**
 * 크리킹(stableId 99). 수치·근거·divergence 는 {@link CreakingRules} 가 소유하고 서술 정본은
 * {@code docs/MC-REFERENCE.md} 「크리킹」 절이다.
 *
 * <p><b>상태 기계</b>는 셋뿐이고 <b>난수를 한 톨도 쓰지 않는다</b> — 결정 트레이스의 난수
 * 소비 수열이 이 종을 추가해도 흔들리지 않아야 하기 때문이다(황린 잠복자·좀비 동물 돌진이
 * 낸 선례와 같은 제약).
 * <ol>
 *   <li><b>지연</b> — 하트가 소환한 뒤 {@link CreakingRules#SPAWN_DELAY_TICKS} 동안 굳어 있다.
 *       바닐라의 22 MC 틱 확인 절차를 접은 값이다.</li>
 *   <li><b>관측 정지</b> — 살아 있는 플레이어 중 <b>하나라도</b> 시야 원뿔 안에 이 개체를
 *       담고 있고 그 사이가 가려지지 않았으면 이동·공격을 전부 멈춘다. 원뿔 판정은
 *       {@link CreakingRules#withinObservationCone} 이고 가림은 {@code hasLineOfSight} 다 —
 *       <b>엔더맨 응시 적대화와 같은 두 입력</b>이다.</li>
 *   <li><b>추격</b> — 아무도 보고 있지 않으면 평범한 근접 몹으로 움직인다.</li>
 * </ol>
 *
 * <p><b>무적</b>은 {@link #invulnerable()} 한 곳이 소유한다. 피해 파이프라인이 이 술어를
 * 보고 통째로 거부하며, 이 종을 죽이는 유일한 경로는 하트 파괴가 부르는
 * {@link #killedByHeartLoss()} 다.
 *
 * <p><b>낮 귀환</b>: 밤이 끝나면 사라진다([B] «Creaking» — "despawn during daytime").
 * 이 저장소의 밤 창은 {@link WorldClock#isNight(long)} 하나뿐이라 바닐라의 12600~23400
 * (24000 하루)을 다시 옮기지 않고 <b>이미 있는 밤 정의를 그대로 쓴다</b>(divergence — 값이
 * 아니라 기준을 재사용한다. 그래야 몹 스폰·잠자기·팬텀이 보는 밤과 한 창이 된다).
 */
public final class Creaking extends MeleeMob {

    /**
     * 이 개체를 소환한 하트의 칸 좌표. 결속 거리·낮 귀환·하트 파괴 처치의 기준이다.
     *
     * <p>{@code final} 이 아닌 이유는 {@code MobFactory} 가 모든 종에 같은 4인자 생성자를
     * 쓰기 때문이다 — 세운 직후 {@link #bindHeart(int, int, int)} 로 심는다. 기본값은 자기
     * 발밑이라 심기 전에도 결속 반경 안이고, 그래서 배선이 빠져도 즉사하지 않는다.
     */
    private int heartX;
    private int heartY;
    private int heartZ;

    /** 남은 소환 지연 틱. 0 이 되어야 처음으로 움직인다. */
    private int spawnDelayTicks = CreakingRules.SPAWN_DELAY_TICKS;

    /** 지금 누군가 보고 있는가. 표현(정지 포즈)과 정적판 대조가 읽는다. */
    private boolean observed;

    /** 하트가 부서져 죽는 중인가. 무적 술어를 이 한 틱만 연다. */
    private boolean heartLost;

    public Creaking(long id, double x, double y, double z) {
        this(id, x, y, z, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public Creaking(long id, double x, double y, double z, int heartX, int heartY, int heartZ) {
        super(id, MobType.CREAKING, x, y, z);
        this.heartX = heartX;
        this.heartY = heartY;
        this.heartZ = heartZ;
    }

    // ── MeleeMob 계약 ───────────────────────────────────────────────────────────────
    @Override protected double detectRange() { return CreakingRules.DETECT_RANGE; }
    @Override protected double attackRange() { return CreakingRules.ATTACK_RANGE; }
    @Override protected int attackDamage() { return CreakingRules.MELEE_DAMAGE; }
    @Override protected int attackCooldownTicks() { return CreakingRules.ATTACK_COOLDOWN_TICKS; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() { return CreakingRules.MOVE_SPEED; }

    /**
     * 이 개체를 어느 하트에 묶는다. {@link com.gameexpert.engine.creaking.CreakingSummon}
     * 만 부르며, 세운 직후 한 번뿐이다.
     */
    public void bindHeart(int x, int y, int z) {
        this.heartX = x;
        this.heartY = y;
        this.heartZ = z;
    }

    /** 하트 칸 좌표(x, y, z). 결속·소멸 판정과 정적판 사본이 읽는다. */
    public int heartX() { return heartX; }

    public int heartY() { return heartY; }

    public int heartZ() { return heartZ; }

    /** 지금 누군가의 시야 원뿔 안에 있는가. */
    public boolean observed() { return observed; }

    /** 아직 소환 지연 중인가. 지연 중에는 관측과 무관하게 움직이지 않는다. */
    public boolean spawning() { return spawnDelayTicks > 0; }

    /**
     * 모든 피해를 거부하는가. 하트가 부서지는 순간({@link #killedByHeartLoss()})에만 열린다 —
     * 그 한 경로가 곧 이 종의 유일한 처치 방법이다.
     */
    public boolean invulnerable() {
        return CreakingRules.INVULNERABLE && !heartLost;
    }

    /**
     * 하트가 부서졌다. 무적을 풀고 체력을 0 으로 만든다 — <b>보통 사망 경로</b>를 그대로
     * 타므로 처치 방송·통계·드랍(없음)이 다른 몹과 한 자리에서 처리된다.
     */
    public void killedByHeartLoss() {
        heartLost = true;
        kill();
    }

    /**
     * 방어도 계산 <b>이전</b>에 원본 피해를 0 으로 만든다. 이 저장소의 일반 피해는 전부
     * {@code Mob#damage} → {@code adjustIncomingDamage} 를 지나므로 근접·화살·폭발·가시가
     * 이 한 줄에서 함께 막힌다.
     *
     * <p>피해를 0 으로 <b>만들 뿐</b> 이벤트 자체는 살려 둔다 — {@code damage(amount, tickNo)}
     * 가 여전히 true 를 돌려주므로 피격 반응(파티클·소리)이 나가고, 그것이 곧 바닐라의
     * "피해가 하트로 흘러가는 궤적" 연출 자리다.
     */
    @Override
    protected double adjustIncomingDamage(double rawAmount) {
        return invulnerable() ? 0.0 : rawAmount;
    }

    /**
     * 방어도를 무시하는 환경 피해(불·독·물약)도 같이 막는다. 이 메서드가 그 계열의 유일한
     * 합류점이라 여기 한 곳만 닫으면 되고, 하트 파괴가 쓰는 {@link #kill()} 은 이 경로를
     * 지나지 않아 영향을 받지 않는다.
     */
    @Override
    protected void damageBypassesArmor(double amount) {
        if (invulnerable()) return;
        super.damageBypassesArmor(amount);
    }

    /**
     * 관측·지연·결속·낮 귀환을 한 자리에서 본다. 여기서 가로채면 {@link MeleeMob} 의 추격/공격
     * 본체를 한 글자도 고치지 않아도 된다(황린 잠복자와 같은 배선).
     */
    @Override
    protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        // ① 낮이 되면 하트로 돌아간다. 드랍도 경험치도 없다.
        //
        // <b>제거 경로가 하나다.</b> 바닐라는 "despawn" 이라고 적지만 이 저장소는 낮 귀환도
        // {@link #killedByHeartLoss()} 를 쓴다([C] divergence). 이유는 소유권이다 — 하트
        // 사슬({@code CreakingHeartSystem})이 새벽에 이미 자기 크리킹을 거두므로, 몹 쪽이
        // 다른 제거 문법을 쓰면 같은 개체가 두 경로로 사라져 두 권위의 소멸 사유 토큰이
        // 갈린다. 드랍도 경험치도 없는 종이라 관측 가능한 차이는 사유 토큰 하나뿐이고,
        // 그 하나를 맞추는 쪽이 파리티에 이롭다. 이 분기는 하트 사슬이 닿지 못하는
        // 경우(하트가 비상주 청크로 내려간 뒤 몹만 남은 경우)의 안전망이기도 하다.
        if (!WorldClock.isNight(world.worldTime())) {
            killedByHeartLoss();
            return TargetInterception.HANDLED;
        }
        // ② 하트에서 너무 멀어졌다(밀려났다). 바닐라도 이때 죽는다.
        if (CreakingRules.beyondTether(x, y, z, heartX + 0.5, heartY, heartZ + 0.5)) {
            killedByHeartLoss();
            return TargetInterception.HANDLED;
        }
        // ③ 소환 지연. 굳어 있는 동안에는 관측 판정조차 하지 않는다.
        if (spawnDelayTicks > 0) {
            spawnDelayTicks--;
            observed = true;
            return TargetInterception.HANDLED;
        }
        // ④ 관측 정지. 표적이 없어도 판정한다 — 감지 반경 밖의 플레이어가 바라보는 것도
        //    바닐라에서는 정지 사유이기 때문이다.
        boolean nowObserved = observedByAnyPlayer(world);
        // 전이만 소리를 낸다. 매 틱 내면 시끄럽고, 전이가 곧 플레이어가 배워야 할 규칙이다.
        List<MobEvent> events = nowObserved == observed
                ? List.of()
                : List.of(new MobEvent.Sound(
                        nowObserved ? "creaking_freeze" : "creaking_unfreeze"));
        observed = nowObserved;
        if (observed) {
            if (target != null) faceToward(target.x(), target.z());
            return TargetInterception.handled(events);
        }
        // 정지가 풀린 틱에도 이동은 일반 근접 본체가 맡는다 — 소리만 얹어 흘려보낸다.
        return TargetInterception.unhandled(events);
    }

    /**
     * 살아 있는 플레이어 중 하나라도 이 개체를 시야 원뿔에 담고 있고 그 사이가 뚫려 있는가.
     *
     * <p>순회 순서는 {@code world.players()} 그대로이고 첫 성립에서 멈춘다 — 결과가
     * 불리언 하나라 순서가 값에 영향을 주지 않으므로 두 권위가 목록 순서를 맞출 필요가 없다.
     */
    private boolean observedByAnyPlayer(MobWorldView world) {
        double selfEyeY = y + eyeHeight();
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive()) continue;
            double playerEyeY = CreakingRules.playerEyeY(player);
            double dx = x - player.x();
            double dy = selfEyeY - playerEyeY;
            double dz = z - player.z();
            if (!CreakingRules.withinObservationCone(
                    player.yaw(), player.pitch(), dx, dy, dz)) continue;
            if (!world.hasLineOfSight(
                    player.x(), playerEyeY, player.z(), x, selfEyeY, z)) continue;
            return true;
        }
        return false;
    }

    /** 드랍 없음·경험치 없음. 표를 만들지 않고 사실만 남긴다([B] «Creaking»). */
    public static List<int[]> drops() {
        return List.of();
    }
}
