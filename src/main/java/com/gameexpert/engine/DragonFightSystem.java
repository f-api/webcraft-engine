package com.gameexpert.engine;

import com.gameexpert.dragonfight.service.DragonFightPersistenceService;
import com.gameexpert.endgateway.dto.EndGatewayData;
import com.gameexpert.engine.blocks.VoidEndBlockRules;
import com.gameexpert.engine.dragon.DragonBrain;
import com.gameexpert.engine.dragon.DragonFight;
import com.gameexpert.engine.dragon.DragonFightState;
import com.gameexpert.engine.dragon.DragonWorld.DragonCrystal;
import com.gameexpert.engine.dragon.DragonWorld.DragonPlayer;
import com.gameexpert.engine.dragon.DragonWorld.DragonVictim;
import com.gameexpert.engine.mob.EndCrystal;
import com.gameexpert.engine.mob.EnderDragon;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobEvent;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.ProjectileSim;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.dimension.DimensionRegistry;
import com.gameexpert.ws.dto.WsMessages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [DRAGON] 엔드 차원 드래곤전의 Spring 권위 어댑터. 순수 상태 기계 {@link DragonFight}(정적판
 * {@code StandaloneDragonFightHost} 가 같은 것을 돌린다)에 이 월드의 블록·몹·투사체·폭발·관문·XP 를 {@link DragonFight.Host}
 * 로 잇고, 결과를 몹 원장(드래곤 몹 행)과 WS(dragonState · endCrystalBeams · dragonBossbar)로 내보낸다.
 *
 * <p>틱: 권위 틱마다 몹 lane 앞에서 MC 틱 두 번. 드래곤 몹 행의 위치·방향·체력은 그 뒤 몹 lane 의 이동 방송이 싣는다.
 * 엔드 수정은 엔드 차원 밖에서도 개체로 존재한다(수정 아이템) — 피해는 싸움이 없어도 {@link #hurtCrystal} 이 받는다.
 */
final class DragonFightSystem implements DragonFight.Host {
    private static final Logger log = LoggerFactory.getLogger(DragonFightSystem.class);
    private static final int MC_TICKS_PER_TICK = 2;
    private static final int PERIODIC_FLUSH_TICKS = 100;
    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double LINE_OF_SIGHT_RANGE = 128.0;

    private final WorldRuntime rt;
    private DragonFightPersistenceService persistence;
    private DragonFight fight;
    private boolean loaded;
    private boolean flushing;
    private long lastFlushTick = Long.MIN_VALUE;
    private final ConcurrentLinkedQueue<Boolean> flushed = new ConcurrentLinkedQueue<>();
    // ── 방송 차분 ──
    private final Set<String> knownPlayers = new HashSet<>();
    private String lastDragonStateKey;
    private final Map<Long, int[]> lastBeams = new LinkedHashMap<>();
    /** [DRAGON] 마지막으로 보낸 부위 중심({@code dragonParts}); 바뀐 틱에만 보낸다. */
    private double[] lastPartCenters;
    private long lastPartsMobId;
    private final Map<String, double[]> lastBossbar = new HashMap<>();
    /**
     * 이번 권위 틱에 날개가 민 플레이어의 누적 밀치기(블록/MC 틱). 같은 명중이 있으면 그 넉백으로 싣고, 남은 것은 틱
     * 끝에 피해 없는 밀치기({@code playerImpulse})로 보낸다.
     */
    private final Map<String, double[]> pushes = new HashMap<>();
    /** 드래곤 알 순간이동 난수(바닐라 level.random 자리). */
    private final java.util.Random eggRandom;

    DragonFightSystem(WorldRuntime rt) {
        this.rt = rt;
        this.eggRandom = new java.util.Random(0x2015L);
    }

    void install(DragonFightPersistenceService service) {
        persistence = service;
    }

    private boolean voidEnd() {
        return DimensionRegistry.VOID_END.equals(rt.dimensionKey());
    }

    DragonFight fight() {
        return fight;
    }

    // ── 틱 ───────────────────────────────────────────────────────────────────────

    void tick(long tickNo) {
        if (!voidEnd()) return;
        if (!loaded) load();
        drainFlushed();
        for (int mcTick = 0; mcTick < MC_TICKS_PER_TICK; mcTick++) fight.tickMc();
        deliverPushOnly();
        syncDragonMob();
        broadcast();
        flush(tickNo);
    }

    /**
     * 싸움 행을 되살리거나 새로 만든다. 행이 없고 (0,0) 청크가 이미 채워진 월드는 이 기능 이전의 엔드 차원이다
     * ({@link DragonFight} 문서의 이주 정책).
     */
    private void load() {
        loaded = true;
        DragonFightState state = null;
        if (persistence != null) {
            try {
                state = persistence.load(rt.worldId()).map(DragonFightState::decode).orElse(null);
            } catch (RuntimeException failure) {
                log.warn("드래곤전 상태 복원 실패, 새 싸움으로 시작: world={}", rt.worldId(), failure);
            }
        }
        boolean fresh = state == null;
        if (fresh) state = DragonFight.createDefault(rt.mobSystem().chunkPopulated(0, 0));
        fight = new DragonFight(this, state);
        if (fresh) fight.markDirty();
        if (state.dragonMobId != 0 && dragonPresent(state.dragonMobId)) fight.bindDragon(state.dragonMobId);
    }

    /**
     * [DRAGON] {@code EnderDragon.knockBack} 의 {@code Entity.push} 는 피해와 무관하게 매 MC 틱 걸린다(앉은 단계 ·
     * 피격 대기 중에도). 명중이 싣지 않은 밀치기는 권위 임펄스 {@code playerImpulse}(순수 가산, 연출 없음)로
     * 그 플레이어에게만 보낸다. 단위는 블록/초(= 블록/MC 틱 × 20).
     */
    private void deliverPushOnly() {
        for (Map.Entry<String, double[]> entry : pushes.entrySet()) {
            PlayerTickState player = rt.players().get(entry.getKey());
            if (player == null || player.isDead()) continue;
            double[] push = entry.getValue();
            org.springframework.web.socket.WebSocketSession session = rt.session(player.nickname());
            if (session == null) continue;
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, new WsMessages.PlayerImpulse(
                    player.nickname(), push[0] * 20.0, push[1] * 20.0, push[2] * 20.0, null));
        }
        pushes.clear();
    }

    /** 두뇌의 결과를 드래곤 몹 행에 싣는다(위치 · yRot · 체력 · 부위 상자). */
    private void syncDragonMob() {
        DragonBrain brain = fight.brain();
        if (brain == null) return;
        Mob mob = rt.mobSystem().mobById(fight.state().dragonMobId);
        if (mob instanceof EnderDragon dragon && !dragon.isDead()) {
            dragon.syncFromBrain(brain);
            rt.mobSystem().refreshMobSpatialIndex(dragon);
        }
    }

    // ── 방송 ─────────────────────────────────────────────────────────────────────

    private void broadcast() {
        DragonBrain brain = fight.brain();
        long dragonId = fight.state().dragonMobId;
        WsMessages.DragonState dragonState = brain == null || dragonId == 0 ? null
                : new WsMessages.DragonState(dragonId, brain.phase().id(), brain.dragonDeathTime,
                        brain.nearestCrystalId == 0 ? null : brain.nearestCrystalId);
        String key = dragonState == null ? null : dragonId + "|" + dragonState.getPhase() + "|"
                + dragonState.getNearestCrystalMobId() + "|" + (dragonState.getDeathTicks() > 0);
        boolean stateChanged = !Objects.equals(key, lastDragonStateKey);
        lastDragonStateKey = key;
        Map<Long, int[]> beams = fight.beamSnapshot();
        List<WsMessages.EndCrystalBeam> beamChanges = new ArrayList<>();
        for (Map.Entry<Long, int[]> entry : beams.entrySet()) {
            int[] previous = lastBeams.get(entry.getKey());
            if (previous == null || !java.util.Arrays.equals(previous, entry.getValue())) {
                beamChanges.add(beam(entry.getKey(), entry.getValue()));
            }
        }
        for (Long id : lastBeams.keySet()) {
            if (!beams.containsKey(id)) beamChanges.add(beam(id, null));
        }
        lastBeams.clear();
        lastBeams.putAll(beams);
        // [DRAGON] 부위 발밑 중심: 클라 조준이 권위와 같은 부위 상자(바닐라 부위 크기)를 만든다.
        double[] centers = brain == null || dragonId == 0 ? null : brain.partCenters();
        boolean partsChanged = centers != null
                && (dragonId != lastPartsMobId || !java.util.Arrays.equals(centers, lastPartCenters));
        WsMessages.DragonParts parts = centers == null ? null : new WsMessages.DragonParts(dragonId, centers);
        lastPartCenters = centers;
        lastPartsMobId = centers == null ? 0L : dragonId;
        knownPlayers.retainAll(rt.players().keySet());
        lastBossbar.keySet().retainAll(rt.players().keySet());
        for (PlayerTickState player : rt.players().values()) {
            String nickname = player.nickname();
            if (knownPlayers.add(nickname)) {
                if (dragonState != null) send(player, dragonState);
                if (parts != null) send(player, parts);
                List<WsMessages.EndCrystalBeam> all = new ArrayList<>();
                for (Map.Entry<Long, int[]> entry : beams.entrySet()) all.add(beam(entry.getKey(), entry.getValue()));
                if (!all.isEmpty()) send(player, new WsMessages.EndCrystalBeams(all));
            } else {
                if (stateChanged && dragonState != null) send(player, dragonState);
                if (partsChanged) send(player, parts);
                if (!beamChanges.isEmpty()) send(player, new WsMessages.EndCrystalBeams(beamChanges));
            }
            boolean active = fight.bossVisible() && fight.bossPlayers().contains(nickname);
            double progress = active ? Math.max(0.0, Math.min(1.0, fight.bossProgress())) : 0.0;
            double[] previous = lastBossbar.get(nickname);
            boolean wasActive = previous != null && previous[0] != 0.0;
            if (active ? previous == null || !wasActive || previous[1] != progress : wasActive) {
                send(player, new WsMessages.DragonBossbar(active, progress));
                lastBossbar.put(nickname, new double[] {active ? 1.0 : 0.0, progress});
            }
        }
    }

    private static WsMessages.EndCrystalBeam beam(long id, int[] target) {
        return new WsMessages.EndCrystalBeam(id,
                target == null ? null : new WsMessages.PositionDto(target[0], target[1], target[2]));
    }

    private void send(PlayerTickState player, Object message) {
        var session = rt.session(player.nickname());
        if (session != null) rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
    }

    // ── 영속 ─────────────────────────────────────────────────────────────────────

    private void drainFlushed() {
        while (flushed.poll() != null) flushing = false;
    }

    private void flush(long tickNo) {
        if (persistence == null || flushing || rt.ctx().persistenceExecutor() == null) return;
        boolean periodic = fight.brain() != null && tickNo - lastFlushTick >= PERIODIC_FLUSH_TICKS;
        if (!fight.dirty() && !periodic) return;
        fight.pruneCrystalState();
        String encoded = fight.state().encode();
        fight.clearDirty();
        lastFlushTick = tickNo;
        long worldId = rt.worldId();
        DragonFightPersistenceService service = persistence;
        flushing = true;
        boolean accepted = rt.ctx().persistenceExecutor().trySubmit(() -> {
            try {
                service.save(worldId, encoded);
            } catch (RuntimeException failure) {
                log.warn("드래곤전 상태 저장 실패: world={}", worldId, failure);
            }
            flushed.add(Boolean.TRUE);
        });
        if (!accepted) {
            flushing = false;
            fight.markDirty();
        }
    }

    /** 월드 폐기 직전 마지막 상태를 영속 실행기의 폐기 장벽 앞에 넣는다. */
    void flushForDisposal() {
        if (persistence == null || fight == null) return;
        fight.pruneCrystalState();
        String encoded = fight.state().encode();
        long worldId = rt.worldId();
        DragonFightPersistenceService service = persistence;
        rt.submitDisposalPersistence(() -> service.save(worldId, encoded));
    }

    // ── 피해 진입점(몹 lane · 전투) ───────────────────────────────────────────────

    /**
     * {@code EndCrystal.hurtServer}. 싸움이 없는 차원의 수정(아이템으로 놓은 수정)도 부서져 폭발한다.
     *
     * @return 받아들였는가
     */
    boolean hurtCrystal(long crystalId, boolean explosion, String attackerNickname) {
        if (fight != null) {
            return fight.hurtCrystal(crystalId,
                    explosion ? DragonFight.CrystalDamage.EXPLOSION : DragonFight.CrystalDamage.PLAYER_OR_OTHER,
                    attackerNickname);
        }
        Mob mob = rt.mobSystem().mobById(crystalId);
        if (!(mob instanceof EndCrystal crystal) || crystal.isDead()) return false;
        removeCrystal(crystalId);
        if (!explosion) explode(crystal.x, crystal.y, crystal.z, DragonFight.CRYSTAL_EXPLOSION_POWER, true, attackerNickname);
        return true;
    }

    /** 투사체 명중: 맞은 부위(가장 가까운 부위 상자)로 {@code hurt(part, …)}. */
    void hurtDragonByProjectile(EnderDragon dragon, ProjectileSim.Kind kind, MobEvent.AttackMob attack,
            double hitX, double hitY, double hitZ) {
        if (fight == null) return;
        int part = dragon.partNearest(hitX, hitY, hitZ);
        DragonBrain.DamageKind damageKind = switch (kind) {
            case ARROW, TRIDENT -> DragonBrain.DamageKind.ARROW;
            case WIND_CHARGE, BREEZE_WIND_CHARGE -> DragonBrain.DamageKind.WIND_CHARGE;
            default -> DragonBrain.DamageKind.OTHER_PROJECTILE;
        };
        String shooter = attack.shooterNickname();
        PlayerTickState player = shooter == null ? null : rt.players().get(shooter);
        DragonBrain.DamageSource source = new DragonBrain.DamageSource(damageKind, player != null, shooter,
                player == null ? null : player.x(), player == null ? null : player.z(),
                attack.hitVelocityX(), attack.hitVelocityZ());
        applyDragonHurt(dragon, fight.hurtDragon(part, source, attack.damage()));
    }

    /**
     * 폭발: 바닐라 {@code ServerExplosion.hurtEntities} 는 부위 개체를 하나씩 맞힌다. 부위마다 거리·노출로 피해를
     * 구해 {@code hurt(part, explosion, …)} 에 넣는다(피격 무적이 두 번째 이후를 거른다).
     */
    void hurtDragonByExplosion(EnderDragon dragon, double x, double y, double z, double power,
            ExplosionRules.BlockLookup blocks) {
        if (fight == null) return;
        double[][] parts = dragon.partHitBoxes();
        if (parts == null) return;
        boolean changed = false;
        for (int part = 0; part < parts.length; part++) {
            double[] box = parts[part];
            double px = (box[0] + box[3]) / 2.0;
            double py = box[1];
            double pz = (box[2] + box[5]) / 2.0;
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance >= 2.0 * power) continue;
            double exposure = ExplosionRules.exposure(blocks, x, y, z,
                    box[0], box[1], box[2], box[3], box[4], box[5]);
            int damage = ExplosionRules.damageAt(distance, power, exposure);
            if (damage <= 0) continue;
            DragonBrain.HurtResult result = fight.hurtDragon(part,
                    new DragonBrain.DamageSource(DragonBrain.DamageKind.EXPLOSION, false, null, x, z, null, null),
                    damage);
            changed |= result.healthChanged();
        }
        if (changed) rt.mobSystem().broadcastMobHurt(dragon.id, false);
    }

    /**
     * 근접: 눈 광선이 처음 닿는 부위(없으면 거절)로 {@code hurt(part, playerAttack, amount)}.
     *
     * @return 두뇌의 결과(받아들이지 않았으면 accepted=false)
     */
    DragonBrain.HurtResult meleeDragon(PlayerTickState player, EnderDragon dragon, double amount) {
        DragonBrain.HurtResult rejected = new DragonBrain.HurtResult(false, false, false, false);
        if (fight == null) return rejected;
        int part = partUnderCrosshair(player, dragon);
        if (part < 0) return rejected;
        DragonBrain.HurtResult result = fight.hurtDragon(part,
                DragonBrain.DamageSource.player(DragonBrain.DamageKind.MELEE, player.nickname(), player.x(),
                        player.z()), (float) amount);
        applyDragonHurt(dragon, result);
        return result;
    }

    /**
     * [SPEAR-KINETIC] 창 돌진: 바닐라 {@code KineticWeapon.damageEntities} 가 부위를 부모로 바꿔 {@code stabAttack(dragon)} 하고
     * {@code EnderDragon.hurtServer} 가 {@code hurt(body, source, 피해)} 로 넘긴다(몸통 부위 규칙).
     */
    DragonBrain.HurtResult kineticDragon(PlayerTickState player, EnderDragon dragon, double amount) {
        if (fight == null) return new DragonBrain.HurtResult(false, false, false, false);
        DragonBrain.HurtResult result = fight.hurtDragon(DragonBrain.PART_BODY,
                DragonBrain.DamageSource.player(DragonBrain.DamageKind.MELEE, player.nickname(), player.x(),
                        player.z()), (float) amount);
        applyDragonHurt(dragon, result);
        return result;
    }

    private void applyDragonHurt(EnderDragon dragon, DragonBrain.HurtResult result) {
        if (result.healthChanged()) rt.mobSystem().broadcastMobHurt(dragon.id, false);
    }

    /** 플레이어 눈 광선과 부위 상자의 첫 교차(사거리 6 블록 안). 없으면 −1. */
    static int partUnderCrosshair(PlayerTickState player, EnderDragon dragon) {
        double[][] parts = dragon.partHitBoxes();
        if (parts == null) return -1;
        double eyeY = player.y() + (player.crouching() ? 1.27 : 1.62);
        double cosPitch = Math.cos(player.pitch());
        double dirX = -Math.sin(player.yaw()) * cosPitch;
        double dirY = Math.sin(player.pitch());
        double dirZ = -Math.cos(player.yaw()) * cosPitch;
        double reach = 6.0;
        return partUnderRay(parts, player.x(), eyeY, player.z(), dirX * reach, dirY * reach, dirZ * reach);
    }

    /**
     * 광선 {@code origin + t·delta}(t ∈ [0, 1])가 처음 닿는 부위(같은 t 면 앞 부위), 없으면 −1. 정적판 권위 · 클라
     * 조준의 {@code DragonPartGeometry.dragonPartRayHit} 와 같은 식이다.
     */
    static int partUnderRay(double[][] parts, double ox, double oy, double oz, double dx, double dy, double dz) {
        int best = -1;
        double bestT = Double.MAX_VALUE;
        for (int part = 0; part < parts.length; part++) {
            double t = rayBox(ox, oy, oz, dx, dy, dz, parts[part]);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = part;
            }
        }
        return best;
    }

    private static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz, double[] box) {
        double t0 = 0.0;
        double t1 = 1.0;
        double[] origin = {ox, oy, oz};
        double[] direction = {dx, dy, dz};
        for (int axis = 0; axis < 3; axis++) {
            double lo = box[axis];
            double hi = box[axis + 3];
            double o = origin[axis];
            double d = direction[axis];
            if (Math.abs(d) < 1e-12) {
                if (o < lo || o > hi) return -1;
                continue;
            }
            double ta = (lo - o) / d;
            double tb = (hi - o) / d;
            if (ta > tb) {
                double swap = ta;
                ta = tb;
                tb = swap;
            }
            t0 = Math.max(t0, ta);
            t1 = Math.min(t1, tb);
            if (t0 > t1) return -1;
        }
        return t0;
    }

    /**
     * {@code EndCrystalItem.useOn}: 누른 칸이 흑요석·기반암이고 윗칸이 비었으며 (윗칸, 1×2×1) 상자에 개체가 없으면
     * 받침을 숨긴 수정을 윗칸 바닥 중심에 세운다. 엔드 차원이면 {@code tryRespawn}.
     *
     * @return 놓았는가(아이템을 하나 쓴다)
     */
    boolean placeEndCrystal(int clickedX, int clickedY, int clickedZ) {
        int clicked = block(clickedX, clickedY, clickedZ);
        if (clicked != Blocks.OBSIDIAN && clicked != Blocks.BEDROCK) return false;
        int ax = clickedX;
        int ay = clickedY + 1;
        int az = clickedZ;
        if (block(ax, ay, az) != Blocks.AIR) return false;
        if (rt.mobSystem().anyEntityIn(ax, ay, az, ax + 1, ay + 2, az + 1)) return false;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            if (player.x() + PLAYER_HALF_WIDTH > ax && player.x() - PLAYER_HALF_WIDTH < ax + 1
                    && player.y() + PLAYER_HEIGHT > ay && player.y() < ay + 2
                    && player.z() + PLAYER_HALF_WIDTH > az && player.z() - PLAYER_HALF_WIDTH < az + 1) {
                return false;
            }
        }
        spawnCrystal(ax + 0.5, ay, az + 0.5, false);
        if (fight != null) fight.tryRespawn();
        return true;
    }

    /**
     * [DRAGON] 드래곤 알을 때리거나 썼다({@code DragonEggBlock.attack/useWithoutItem} → {@code teleport}). 알이 아니면 거짓.
     */
    boolean teleportEgg(int x, int y, int z) {
        if (block(x, y, z) != Blocks.DRAGON_EGG) return false;
        int[] target = com.gameexpert.engine.dragon.DragonEggRules.teleportTarget(eggRandom::nextInt, this::block, x, y, z);
        if (target == null) return true;
        rt.mobSystem().levelEvent(com.gameexpert.engine.dragon.DragonEvents.PARTICLES_DRAGON_EGG, x, y, z, target[3]);
        setBlock(target[0], target[1], target[2], Blocks.DRAGON_EGG, 0);
        setBlock(x, y, z, Blocks.AIR, 0);
        return true;
    }

    // ═══════════════ DragonFight.Host ═══════════════

    @Override
    public int seed() {
        return rt.seed();
    }

    @Override
    public boolean arenaLoaded() {
        for (int cz = -DragonFight.ARENA_SIZE_CHUNKS; cz <= DragonFight.ARENA_SIZE_CHUNKS; cz++) {
            for (int cx = -DragonFight.ARENA_SIZE_CHUNKS; cx <= DragonFight.ARENA_SIZE_CHUNKS; cx++) {
                if (!rt.accessor().isChunkResident(cx, cz)) return false;
            }
        }
        return true;
    }

    @Override
    public void holdArena(boolean hold) {
        rt.setDragonArenaHeld(hold);
    }

    @Override
    public int block(int x, int y, int z) {
        return WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
    }

    @Override
    public void setBlock(int x, int y, int z, int block, int state) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        int current = block(x, y, z);
        if (current == WorldTickLoop.UNAVAILABLE_BLOCK) return;
        int currentState = current == Blocks.AIR ? 0 : rt.blockStates().get(x, y, z, current);
        if (current == block && currentState == state) return;
        rt.fluidSim().applyChange(x, y, z, block);
        rt.setBlockState(x, y, z, block, state);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
    }

    @Override
    public boolean removeBlock(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
        int current = block(x, y, z);
        if (current == WorldTickLoop.UNAVAILABLE_BLOCK || current == Blocks.AIR) return false;
        rt.fluidSim().applyChange(x, y, z, Blocks.AIR);
        rt.tickLoop().onExplosionBlockRemoved(x, y, z, current);
        WorldTickLoop.dropRemovedBlockContents(rt, current, x, y, z, 0, false);
        return true;
    }

    @Override
    public int heightNoLeaves(int x, int z) {
        return rt.mobSystem().dragonWorldView().motionBlockingNoLeavesHeight(x, z) + 1;
    }

    @Override
    public int heightMotionBlocking(int x, int z) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            int id = block(x, y, z);
            if (id < 0) return Blocks.MAX_Y + 1;
            if (id != Blocks.AIR && (BuildingBlockRules.blocksMotion(id, rt.blockStates().get(x, y, z, id))
                    || Fluids.isWaterMedium(id) || Fluids.isLava(id))) {
                return y + 1;
            }
        }
        return Blocks.MIN_Y;
    }

    @Override
    public List<DragonPlayer> players() {
        List<DragonPlayer> out = new ArrayList<>();
        List<String> names = new ArrayList<>(rt.players().keySet());
        names.sort(String::compareTo);
        for (String name : names) {
            PlayerTickState player = rt.players().get(name);
            boolean alive = !player.isDead();
            double eye = player.y() + (player.crouching() ? 1.27 : 1.62);
            out.add(new DragonPlayer(name, player.x(), player.y(), player.z(), eye, alive, alive, player.crouching()));
        }
        return out;
    }

    @Override
    public boolean lineOfSight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        if (dx * dx + dy * dy + dz * dz > LINE_OF_SIGHT_RANGE * LINE_OF_SIGHT_RANGE) return false;
        return rt.mobSystem().dragonWorldView().hasLineOfSight(fromX, fromY, fromZ, toX, toY, toZ);
    }

    @Override
    public List<DragonVictim> livingEntitiesIn(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        List<DragonVictim> out = new ArrayList<>();
        for (DragonPlayer player : players()) {
            if (!player.attackable()) continue;
            if (player.x() + PLAYER_HALF_WIDTH > minX && player.x() - PLAYER_HALF_WIDTH < maxX
                    && player.y() + PLAYER_HEIGHT > minY && player.y() < maxY
                    && player.z() + PLAYER_HALF_WIDTH > minZ && player.z() - PLAYER_HALF_WIDTH < maxZ) {
                out.add(new DragonVictim(player.nickname(), 0L, player.x(), player.y(), player.z()));
            }
        }
        for (Mob mob : rt.mobSystem().livingMobsIn(minX, minY, minZ, maxX, maxY, maxZ)) {
            out.add(new DragonVictim(null, mob.id, mob.x, mob.y, mob.z));
        }
        return out;
    }

    @Override
    public void push(DragonVictim victim, double dx, double dy, double dz) {
        if (victim.nickname() != null) {
            // Entity.push 는 MC 틱 속도에 더한다(누적). 같은 명중(hurtByDragon)이 있으면 그 넉백으로 싣고, 없으면 틱
            // 끝의 deliverPushOnly 가 보낸다.
            pushes.merge(victim.nickname(), new double[] {dx, dy, dz},
                    (a, b) -> new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]});
        } else {
            rt.mobSystem().pushMobByDragon(victim.mobId(), dx, dy, dz);
        }
    }

    @Override
    public void hurtByDragon(DragonVictim victim, float amount) {
        DragonBrain brain = fight == null ? null : fight.brain();
        double sx = brain == null ? victim.x() : brain.x;
        double sz = brain == null ? victim.z() : brain.z;
        if (victim.nickname() != null) {
            rt.mobSystem().hurtPlayerByDragon(victim.nickname(), Math.round(amount), sx, sz);
            double[] push = pushes.remove(victim.nickname());
            PlayerTickState player = rt.players().get(victim.nickname());
            if (push != null && player != null && !player.isDead()) {
                player.setHurtKnockback(push[0] * 20.0, push[1] * 20.0, push[2] * 20.0, 0.0, 0.0);
            }
        } else {
            rt.mobSystem().hurtMobByDragon(victim.mobId(), amount);
        }
    }

    @Override
    public List<DragonCrystal> crystals() {
        List<DragonCrystal> out = new ArrayList<>();
        for (Mob mob : rt.mobSystem().mobsOfType(MobType.END_CRYSTAL)) {
            out.add(new DragonCrystal(mob.id, mob.x, mob.y, mob.z));
        }
        return out;
    }

    @Override
    public long spawnCrystal(double x, double y, double z, boolean showBottom) {
        return rt.mobSystem().spawnDragonFightMob(MobType.END_CRYSTAL, x, y, z,
                showBottom ? EndCrystal.SHOW_BOTTOM : EndCrystal.HIDE_BOTTOM, 0.0).id;
    }

    @Override
    public void removeCrystal(long crystalId) {
        if (rt.mobSystem().mobById(crystalId) instanceof EndCrystal crystal && !crystal.isDead()) {
            crystal.destroy();
            rt.mobSystem().markDragonFightDeath(crystal);
        }
    }

    @Override
    public long spawnDragon(double x, double y, double z, float yRot) {
        return rt.mobSystem().spawnDragonFightMob(MobType.ENDER_DRAGON, x, y, z, null, Math.toRadians(yRot)).id;
    }

    @Override
    public boolean dragonPresent(long mobId) {
        Mob mob = rt.mobSystem().mobById(mobId);
        return mob instanceof EnderDragon && !mob.isDead() && !mob.removed;
    }

    @Override
    public List<Long> dragons() {
        List<Long> out = new ArrayList<>();
        for (Mob mob : rt.mobSystem().mobsOfType(MobType.ENDER_DRAGON)) out.add(mob.id);
        return out;
    }

    @Override
    public double[] dragonPose(long mobId) {
        Mob mob = rt.mobSystem().mobById(mobId);
        if (!(mob instanceof EnderDragon)) return null;
        return new double[] {mob.x, mob.y, mob.z, mob.exactHealth()};
    }

    @Override
    public void removeDragon(long mobId) {
        if (rt.mobSystem().mobById(mobId) instanceof EnderDragon dragon && !dragon.isDead()) {
            dragon.finishDeath();
            rt.mobSystem().markDragonFightDeath(dragon);
        }
    }

    @Override
    public void spawnFireball(double x, double y, double z, double dirX, double dirY, double dirZ) {
        long owner = fight == null ? 0L : fight.state().dragonMobId;
        rt.mobSystem().addDragonProjectile(ProjectileSim.dragonFireball(owner, x, y, z, dirX, dirY, dirZ));
    }

    @Override
    public long spawnSittingFlame(double x, double y, double z) {
        long owner = fight == null ? 0L : fight.state().dragonMobId;
        return rt.mobSystem().addDragonProjectile(ProjectileSim.dragonBreath(owner, x, y, z, true));
    }

    @Override
    public void discardCloud(long cloudId) {
        rt.mobSystem().expireProjectile(cloudId);
    }

    @Override
    public void levelEvent(int event, int x, int y, int z, int data, boolean global) {
        if (!global) {
            rt.mobSystem().levelEvent(event, x, y, z, data);
            return;
        }
        WsMessages.LevelEvent message = new WsMessages.LevelEvent(event, x, y, z, data);
        for (PlayerTickState player : rt.players().values()) send(player, message);
    }

    @Override
    public void awardExperience(double x, double y, double z, int amount) {
        if (amount > 0) rt.xpOrbSystem().spawnOrbs(amount, x, y, z);
    }

    @Override
    public boolean mobGriefing() {
        return true;
    }

    @Override
    public void explode(double x, double y, double z, float power, boolean destroyBlocks, String attackerNickname) {
        rt.mobSystem().explodeOrQueue(x, y, z, Math.round(power), destroyBlocks);
    }

    @Override
    public void spawnGateway(int x, int y, int z) {
        EndGatewayData existing = rt.endGateways().gateway(x, y, z);
        rt.endGateways().spawnGateway(x, y, z, VoidEndBlockRules.GATEWAY_OUTBOUND, existing, true);
        // [END-GATEWAY-SPAWN] EndGatewaySystem.spawnGateway 가 생성 빔(endGatewayBeam spawn)과 level event 3000 을 낸다.
    }
}
