package com.gameexpert.engine.dragon;

import com.gameexpert.engine.dragon.DragonWorld.DragonCrystal;
import com.gameexpert.engine.dragon.DragonWorld.DragonPlayer;
import com.gameexpert.engine.dragon.DragonWorld.DragonVictim;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.dimension.voidend.VoidEndGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [DRAGON] 바닐라 {@code net.minecraft.world.level.dimension.end.EnderDragonFight} + {@code DragonRespawnStage} +
 * {@code EndCrystal.tick/hurtServer} + 드래곤 개체의 한 틱을 묶은 순수 상태 기계(핀 26.3 javap). 권위(Spring
 * {@code DragonFightSystem} · 정적판 {@code StandaloneDragonFightHost})는 {@link Host} 만 구현하고, 정적판
 * {@code DragonFight.ts} 가 줄 단위 사본이다. 모든 호출은 바닐라 20 TPS 한 틱({@link #tickMc})이며 권위 틱마다 두 번
 * 부른다.
 *
 * <p>한 MC 틱의 순서는 {@code ServerLevel.tick} 과 같다: 싸움 틱 → 엔드 수정 틱(불 놓기) → 드래곤 틱.
 *
 * <p>WebCraft 차이(문서화, CONTRACT §11A):
 * <ul>
 * <li>{@code findExitPortal}/{@code hasActiveExitPortal} 의 블록 패턴 검색 대신 생성기가 둔 귀환 포털 자리
 * {@code (0, podiumY(seed), 0)} 를 본다(기반암은 부술 수 없어 자리가 바뀌지 않는다).</li>
 * <li>{@code isArenaLoaded}(−8..8 청크 BLOCK_TICKING)는 권위의 상주 판정이고, {@code DRAGON} 티켓은
 * {@link Host#holdArena} 가 같은 청크를 시뮬레이션 집합에 넣는다.</li>
 * <li>이 기능 이전에 만든 엔드 차원(생성 단계에 활성 포털과 첫 관문이 있던 월드)은 싸움 행이 없고 (0,0) 청크가
 * 이미 채워져 있다. 그 월드는 {@link #createDefault}{@code (legacy = true)} 로 시작해 활성 포털과 첫 고리 관문을
 * 월드 편집으로 되살린 뒤 바닐라 {@code scanState} 가 "이미 처치한 월드"로 판정한다(플레이어가 갇히지 않는다).</li>
 * <li>난수: 바닐라 개체 난수 원천 대신 {@link DragonRandom} 을 쓴다(싸움 = 시드, 드래곤 = 시드 ^ 몹 id).</li>
 * </ul>
 */
public final class DragonFight implements DragonWorld {
    public static final int MAX_TICKS_BEFORE_DRAGON_RESPAWN = 1200;
    public static final int TIME_BETWEEN_CRYSTAL_SCANS = 100;
    public static final int TIME_BETWEEN_PLAYER_SCANS = 20;
    public static final int ARENA_SIZE_CHUNKS = 8;
    public static final double BOSS_EVENT_RANGE = 192.0;
    /** {@code EndCrystal} 폭발 세기({@code hurtServer} 6, 부활 연출 종료 6 NONE, 가시 기둥 재건 5 BLOCK). */
    public static final float CRYSTAL_EXPLOSION_POWER = 6.0F;
    public static final float PILLAR_EXPLOSION_POWER = 5.0F;

    /** 부활 연출 단계 id({@code DragonRespawnStage} 순서, 저장 계약). */
    public static final int STAGE_NONE = -1;
    public static final int STAGE_START = 0;
    public static final int STAGE_PREPARING_TO_SUMMON_PILLARS = 1;
    public static final int STAGE_SUMMONING_PILLARS = 2;
    public static final int STAGE_SUMMONING_DRAGON = 3;
    public static final int STAGE_END = 4;

    /** 권위가 구현하는 세계 포트. */
    public interface Host {
        int seed();

        /** {@code isArenaLoaded}: −8..8 청크가 모두 상주한다. */
        boolean arenaLoaded();

        /** {@code DRAGON} 티켓(반지름 9): 참이면 아레나 청크를 시뮬레이션에 붙잡는다. */
        void holdArena(boolean hold);

        /** 블록 ID(공기 0), 비상주면 음수. */
        int block(int x, int y, int z);

        /** 드랍 없는 월드 편집({@code setBlockAndUpdate} / {@code Feature.setBlock}). 비상주 칸은 무시한다. */
        void setBlock(int x, int y, int z, int block, int state);

        /** {@code removeBlock(pos, false)}: 공기로 바꾸고, 바뀌었으면 true. */
        boolean removeBlock(int x, int y, int z);

        /** MOTION_BLOCKING_NO_LEAVES 첫 빈 칸 y. */
        int heightNoLeaves(int x, int z);

        /** MOTION_BLOCKING 첫 빈 칸 y. */
        int heightMotionBlocking(int x, int z);

        List<DragonPlayer> players();

        boolean lineOfSight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);

        List<DragonVictim> livingEntitiesIn(double minX, double minY, double minZ, double maxX, double maxY,
                double maxZ);

        void push(DragonVictim victim, double dx, double dy, double dz);

        void hurtByDragon(DragonVictim victim, float amount);

        /** 상주하는 엔드 수정 전부(몹 id 순서). */
        List<DragonCrystal> crystals();

        /** 엔드 수정을 세운다({@code showBottom} 이 거짓이면 받침을 숨긴다). 새 몹 id. */
        long spawnCrystal(double x, double y, double z, boolean showBottom);

        /** 엔드 수정을 없앤다(사망 퇴장, 드랍 없음). */
        void removeCrystal(long crystalId);

        /** 드래곤 몹을 세우고 id 를 돌려준다. */
        long spawnDragon(double x, double y, double z, float yRot);

        /** 이 드래곤 몹이 원장에 있는가. */
        boolean dragonPresent(long mobId);

        /** 원장의 드래곤 몹 id 들({@code ServerLevel.getDragons}). */
        List<Long> dragons();

        /** 드래곤 몹의 위치와 체력(없으면 null): x, y, z, health. */
        double[] dragonPose(long mobId);

        /** 드래곤 몹을 사망 퇴장시킨다(드랍·XP 없음 — XP 는 사망 연출이 준다). */
        void removeDragon(long mobId);

        void spawnFireball(double x, double y, double z, double dirX, double dirY, double dirZ);

        long spawnSittingFlame(double x, double y, double z);

        void discardCloud(long cloudId);

        void levelEvent(int event, int x, int y, int z, int data, boolean global);

        void awardExperience(double x, double y, double z, int amount);

        boolean mobGriefing();

        /** {@code level.explode(…, power, false, interaction)}: {@code destroyBlocks} 는 BLOCK, 거짓이면 NONE. */
        void explode(double x, double y, double z, float power, boolean destroyBlocks, String attackerNickname);

        /**
         * {@code EnderDragonFight.spawnNewGateway(pos)}: level event 3000 + {@code END_GATEWAY_DELAYED} 관문(고리 관문
         * state, 출구 없음). 권위의 관문 블록 엔티티가 생성 빔을 연다.
         */
        void spawnGateway(int x, int y, int z);
    }

    private final Host host;
    private final DragonFightState state;
    private DragonBrain brain;
    private final DragonRandom random;
    // ── 보스바(ServerBossEvent) ──
    private final Set<String> bossPlayers = new LinkedHashSet<>();
    private boolean bossVisible = true;
    private float bossProgress = 1.0F;
    private boolean arenaHeld;
    private boolean dirty;

    public DragonFight(Host host, DragonFightState state) {
        this.host = host;
        this.state = state;
        this.random = new DragonRandom(host.seed());
        if (state.fightRandomState >= 0) random.restoreState(state.fightRandomState);
        if (state.gateways.isEmpty() && !state.gatewaysInitialized) {
            // EnderDragonFight.init: gateways 가 비었으면 0..19 를 시드로 섞어 채운다.
            for (int index : VoidEndGenerator.gatewayOrder(host.seed())) state.gateways.add(index);
            state.gatewaysInitialized = true;
            dirty = true;
        }
    }

    /**
     * {@code EnderDragonFight.createDefault}(needsStateScanning = true). {@code legacy} 는 이 기능 이전의 월드다
     * (클래스 문서): 첫 적재 틱에 활성 포털과 첫 고리 관문을 되살린다.
     */
    public static DragonFightState createDefault(boolean legacy) {
        DragonFightState state = new DragonFightState();
        state.needsStateScanning = true;
        state.legacyMigration = legacy;
        return state;
    }

    public DragonFightState state() {
        return state;
    }

    public DragonBrain brain() {
        return brain;
    }

    /** 영속할 값이 바뀌었는가(권위가 저장한 뒤 {@link #clearDirty}). */
    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    public Set<String> bossPlayers() {
        return bossPlayers;
    }

    public boolean bossVisible() {
        return bossVisible;
    }

    public float bossProgress() {
        return bossProgress;
    }

    /** 수정 광선 목표(몹 id → 블록 좌표). 없으면 광선이 없다. */
    public Map<Long, int[]> beams() {
        return state.crystalBeams;
    }

    public int exitPortalY() {
        return VoidEndGenerator.podiumY(host.seed());
    }

    // ═══════════════ 한 MC 틱 ═══════════════

    public void tickMc() {
        tick();
        for (DragonCrystal crystal : host.crystals()) tickCrystal(crystal);
        tickDragon();
        state.fightRandomState = random.state();
    }

    /** {@code EnderDragonFight.tick}. */
    void tick() {
        bossVisible = !state.dragonKilled;
        if (++state.ticksSinceLastPlayerScan >= TIME_BETWEEN_PLAYER_SCANS) {
            updatePlayers();
            state.ticksSinceLastPlayerScan = 0;
        }
        if (!bossPlayers.isEmpty()) {
            holdArena(true);
            if (!host.arenaLoaded()) return;
            if (state.legacyMigration) {
                migrateLegacyWorld();
                state.legacyMigration = false;
                dirty = true;
            }
            if (state.needsStateScanning) {
                scanState();
                state.needsStateScanning = false;
                dirty = true;
            }
            if (state.respawnStage != STAGE_NONE) {
                List<DragonCrystal> crystals = aliveRespawnCrystals();
                if (crystals.isEmpty()) {
                    abortRespawnSequence();
                    return;
                }
                tickRespawnStage(crystals, state.respawnTime++);
                dirty = true;
            }
            if (!state.dragonKilled) {
                if (state.dragonMobId == 0 || ++state.ticksSinceDragonSeen >= MAX_TICKS_BEFORE_DRAGON_RESPAWN) {
                    findOrCreateDragon();
                    state.ticksSinceDragonSeen = 0;
                }
                if (++state.ticksSinceCrystalsScanned >= TIME_BETWEEN_CRYSTAL_SCANS) {
                    updateCrystalCount();
                    state.ticksSinceCrystalsScanned = 0;
                }
            }
        } else {
            holdArena(false);
        }
    }

    private void holdArena(boolean hold) {
        if (arenaHeld == hold) return;
        arenaHeld = hold;
        host.holdArena(hold);
    }

    /** 이전 월드 이주(클래스 문서): 활성 포털 + 첫 처치 관문을 월드 편집으로 되살린다. */
    private void migrateLegacyWorld() {
        spawnExitPortal(true);
        if (!state.gateways.isEmpty()) {
            int index = state.gateways.remove(state.gateways.size() - 1);
            int[] ring = VoidEndGenerator.gatewayRing(index);
            host.spawnGateway(ring[0], ring[1], ring[2]);
        }
    }

    /** {@code scanState}: 포털이 활성이면 이미 처치한 월드, 드래곤 개체가 있으면 싸움이 이어진다. */
    private void scanState() {
        boolean active = hasActiveExitPortal();
        if (active) {
            state.previouslyKilled = true;
        } else {
            state.previouslyKilled = false;
            // findExitPortal() == null 이면 spawnExitPortal(false): 생성기가 이미 비활성 포털을 두었다.
            if (!exitPortalPresent()) spawnExitPortal(false);
        }
        List<Long> dragons = host.dragons();
        if (dragons.isEmpty()) {
            state.dragonKilled = true;
        } else {
            long dragon = dragons.get(0);
            state.dragonMobId = dragon;
            state.dragonKilled = false;
            if (!active) {
                // "But we didn't have a portal, let's remove it."
                host.removeDragon(dragon);
                brain = null;
                state.dragonMobId = 0;
            }
        }
        if (!state.previouslyKilled && state.dragonKilled) state.dragonKilled = false;
    }

    private boolean hasActiveExitPortal() {
        int y = exitPortalY();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (host.block(x, y, z) == Blocks.END_PORTAL) return true;
            }
        }
        return false;
    }

    /** 포털 테두리(기반암 고리)와 기둥이 있는가 — {@code exitPortalPattern} 의 근사. */
    private boolean exitPortalPresent() {
        int y = exitPortalY();
        return host.block(0, y, 0) == Blocks.BEDROCK && host.block(0, y + 3, 0) == Blocks.BEDROCK
                && host.block(3, y, 0) == Blocks.BEDROCK && host.block(-3, y, 0) == Blocks.BEDROCK;
    }

    /** {@code findOrCreateDragon}. */
    private void findOrCreateDragon() {
        List<Long> dragons = host.dragons();
        if (dragons.isEmpty()) {
            createNewDragon();
        } else {
            long dragon = dragons.get(0);
            if (dragon != state.dragonMobId || brain == null) bindDragon(dragon);
            state.dragonMobId = dragon;
            dirty = true;
        }
    }

    /** {@code createNewDragon}: (0,128,0), 무작위 yRot, HOLDING_PATTERN. */
    private void createNewDragon() {
        float yRot = random.nextFloat() * 360.0F;
        long id = host.spawnDragon(0.0, DragonBrain.DRAGON_SPAWN_Y, 0.0, yRot);
        state.dragonMobId = id;
        brain = new DragonBrain(brainSeed(id), 0.0, DragonBrain.DRAGON_SPAWN_Y, 0.0, yRot);
        brain.setPhase(DragonPhase.HOLDING_PATTERN);
        state.dragonPhase = DragonPhase.HOLDING_PATTERN.id();
        state.dragonDeathTime = 0;
        dirty = true;
    }

    private long brainSeed(long mobId) {
        return (long) host.seed() ^ mobId;
    }

    /**
     * 원장에 있는 드래곤 몹에 두뇌를 붙인다(재기동·재적재). 저장된 단계·사망 시간·방향을 되살린다 — 바닐라
     * {@code EnderDragon.readAdditionalSaveData} 의 {@code DragonPhase}·{@code DragonDeathTime} 과 같다.
     */
    public void bindDragon(long mobId) {
        double[] pose = host.dragonPose(mobId);
        if (pose == null) return;
        DragonBrain restored = new DragonBrain(brainSeed(mobId), pose[0], pose[1], pose[2], state.dragonYRot);
        restored.setHealth((float) pose[3]);
        if (state.dragonMobId == mobId) {
            if (state.dragonRandomState >= 0) restored.random.restoreState(state.dragonRandomState);
            restored.dragonDeathTime = state.dragonDeathTime;
            restored.restorePhase(DragonPhase.byId(state.dragonPhase));
        } else {
            restored.restorePhase(DragonPhase.HOLDING_PATTERN);
        }
        brain = restored;
    }

    private void updateCrystalCount() {
        state.ticksSinceCrystalsScanned = 0;
        int alive = 0;
        for (VoidEndGenerator.Spike spike : VoidEndGenerator.spikes(host.seed())) {
            alive += crystalsInSpikeTop(spike).size();
        }
        state.aliveCrystals = alive;
    }

    /** {@code EndSpike.getTopBoundingBox}: 가시 기둥 반지름 사각 기둥(월드 전 높이). */
    private List<DragonCrystal> crystalsInSpikeTop(VoidEndGenerator.Spike spike) {
        List<DragonCrystal> out = new ArrayList<>();
        double minX = spike.centerX() - spike.radius();
        double maxX = spike.centerX() + spike.radius();
        double minZ = spike.centerZ() - spike.radius();
        double maxZ = spike.centerZ() + spike.radius();
        for (DragonCrystal crystal : host.crystals()) {
            // 수정 AABB(2×2×2, 발밑 중심)와 상자의 겹침.
            if (crystal.x() + 1.0 > minX && crystal.x() - 1.0 < maxX
                    && crystal.z() + 1.0 > minZ && crystal.z() - 1.0 < maxZ) {
                out.add(crystal);
            }
        }
        return out;
    }

    /** {@code updatePlayers}: 중심 (0,128,0) 192 블록 안의 살아 있는 플레이어가 보스바를 본다. */
    private void updatePlayers() {
        Set<String> next = new LinkedHashSet<>();
        for (DragonPlayer player : host.players()) {
            if (!player.alive()) continue;
            double dx = player.x();
            double dy = player.y() - DragonBrain.DRAGON_SPAWN_Y;
            double dz = player.z();
            if (dx * dx + dy * dy + dz * dz < BOSS_EVENT_RANGE * BOSS_EVENT_RANGE) next.add(player.nickname());
        }
        bossPlayers.retainAll(next);
        bossPlayers.addAll(next);
    }

    /** {@code setDragonKilled}: 보스바를 내리고 포털·관문·(첫 처치면) 알. */
    private void setDragonKilled(long mobId) {
        if (mobId != state.dragonMobId) return;
        bossProgress = 0.0F;
        bossVisible = false;
        spawnExitPortal(true);
        spawnNewGateway();
        if (!state.previouslyKilled) {
            int eggY = host.heightMotionBlocking(0, 0);
            host.setBlock(0, eggY, 0, Blocks.DRAGON_EGG, 0);
        }
        state.previouslyKilled = true;
        state.dragonKilled = true;
        dirty = true;
    }

    /** {@code spawnNewGateway()}: 목록 마지막 자리를 꺼내 (floor(96cos), 75, floor(96sin)) 에 관문. */
    private void spawnNewGateway() {
        if (state.gateways.isEmpty()) return;
        int index = state.gateways.remove(state.gateways.size() - 1);
        int[] ring = VoidEndGenerator.gatewayRing(index);
        host.spawnGateway(ring[0], ring[1], ring[2]);
        dirty = true;
    }

    /** {@code spawnExitPortal(active)}: 귀환 포털 자리에 {@code EndPodiumFeature(active)}. */
    private void spawnExitPortal(boolean active) {
        if (!state.hasExitPortal) {
            state.hasExitPortal = true;
            state.exitPortalY = exitPortalY();
            dirty = true;
        }
        VoidEndGenerator.podiumCells(state.exitPortalY, active, host::setBlock);
    }

    /** {@code updateDragon}: 보스바 진행도 = 체력 / 최대 체력. */
    private void updateDragon(DragonBrain dragon) {
        bossProgress = dragon.health() / DragonBrain.MAX_HEALTH;
        state.ticksSinceDragonSeen = 0;
    }

    // ═══════════════ 엔드 수정 ═══════════════

    /** {@code EndCrystal.tick}: 싸움이 있는 차원(엔드 차원)에서 제 칸이 공기면 불을 놓는다. */
    private void tickCrystal(DragonCrystal crystal) {
        int bx = DragonMath.floor(crystal.x());
        int by = DragonMath.floor(crystal.y());
        int bz = DragonMath.floor(crystal.z());
        if (host.block(bx, by, bz) == Blocks.AIR) host.setBlock(bx, by, bz, Blocks.FIRE, 0);
    }

    /** 피해 원인의 두 사실({@code DamageTypeTags.IS_EXPLOSION} · 원인 개체가 드래곤인가). */
    public enum CrystalDamage { PLAYER_OR_OTHER, EXPLOSION, DRAGON }

    /**
     * {@code EndCrystal.hurtServer}: 무적(부활 연출의 기둥 수정)이거나 드래곤이 원인이면 거절. 아니면 제거하고,
     * 폭발 피해가 아니면 제자리에 세기 6 폭발(블록 파괴, 불 없음)을 낸 뒤 싸움에 알린다.
     *
     * @return 받아들였는가
     */
    public boolean hurtCrystal(long crystalId, CrystalDamage damage, String attackerNickname) {
        if (state.invulnerableCrystals.contains(crystalId) || damage == CrystalDamage.DRAGON) return false;
        DragonCrystal crystal = null;
        for (DragonCrystal candidate : host.crystals()) {
            if (candidate.mobId() == crystalId) {
                crystal = candidate;
                break;
            }
        }
        if (crystal == null) return false;
        host.removeCrystal(crystalId);
        state.crystalBeams.remove(crystalId);
        if (damage != CrystalDamage.EXPLOSION) {
            host.explode(crystal.x(), crystal.y(), crystal.z(), CRYSTAL_EXPLOSION_POWER, true, attackerNickname);
        }
        onCrystalDestroyed(crystal, attackerNickname);
        dirty = true;
        return true;
    }

    /** {@code EnderDragonFight.onCrystalDestroyed}. */
    private void onCrystalDestroyed(DragonCrystal crystal, String attackerNickname) {
        if (state.respawnStage != STAGE_NONE && state.respawnCrystals.contains(crystal.mobId())) {
            abortRespawnSequence();
            return;
        }
        updateCrystalCount();
        if (brain != null && state.dragonMobId != 0 && host.dragonPresent(state.dragonMobId)) {
            brain.onCrystalDestroyed(this, crystal.mobId(), crystal.x(), crystal.y(), crystal.z(),
                    DragonMath.floor(crystal.x()), DragonMath.floor(crystal.y()), DragonMath.floor(crystal.z()),
                    attackerNickname);
        }
    }

    // ═══════════════ 부활 ═══════════════

    /**
     * {@code EnderDragonFight.tryRespawn}(엔드 수정 아이템을 놓은 뒤): 처치된 싸움이고 연출 중이 아니면 귀환 포털
     * 위 한 칸에서 수평 네 방향 3칸 떨어진 칸마다 수정이 있어야 시작한다.
     */
    public void tryRespawn() {
        if (!state.dragonKilled || state.respawnStage != STAGE_NONE) return;
        if (!state.hasExitPortal) {
            if (!exitPortalPresent()) spawnExitPortal(true);
            state.hasExitPortal = true;
            state.exitPortalY = exitPortalY();
        }
        int y = state.exitPortalY + 1;
        int[][] offsets = {{0, -3}, {0, 3}, {-3, 0}, {3, 0}};
        List<DragonCrystal> found = new ArrayList<>();
        for (int[] offset : offsets) {
            int bx = offset[0];
            int bz = offset[1];
            List<DragonCrystal> here = new ArrayList<>();
            for (DragonCrystal crystal : host.crystals()) {
                // new AABB(pos) 와 수정 AABB(x±1, y..y+2, z±1)의 겹침.
                if (crystal.x() + 1.0 > bx && crystal.x() - 1.0 < bx + 1
                        && crystal.y() + 2.0 > y && crystal.y() < y + 1
                        && crystal.z() + 1.0 > bz && crystal.z() - 1.0 < bz + 1) {
                    here.add(crystal);
                }
            }
            if (here.isEmpty()) return;
            found.addAll(here);
        }
        respawnDragon(found);
    }

    /** {@code respawnDragon}: 포털 층·기둥을 엔드 돌로 덮고 비활성 포털을 다시 세운 뒤 START. */
    private void respawnDragon(List<DragonCrystal> crystals) {
        if (!state.dragonKilled || state.respawnStage != STAGE_NONE) return;
        int y = state.exitPortalY;
        // findExitPortal 이 맞을 때마다 패턴 칸의 기반암·END_PORTAL 을 END_STONE 으로(한 번이면 더는 맞지 않는다).
        VoidEndGenerator.podiumCells(y, true, (x, cy, z, block, blockState) -> {
            int current = host.block(x, cy, z);
            if (current == Blocks.BEDROCK || current == Blocks.END_PORTAL) host.setBlock(x, cy, z, Blocks.END_STONE, 0);
        });
        state.respawnStage = STAGE_START;
        state.respawnTime = 0;
        spawnExitPortal(false);
        state.respawnCrystals.clear();
        for (DragonCrystal crystal : crystals) {
            if (!state.respawnCrystals.contains(crystal.mobId())) state.respawnCrystals.add(crystal.mobId());
        }
        dirty = true;
    }

    private List<DragonCrystal> aliveRespawnCrystals() {
        List<DragonCrystal> out = new ArrayList<>();
        for (DragonCrystal crystal : host.crystals()) {
            if (state.respawnCrystals.contains(crystal.mobId())) out.add(crystal);
        }
        return out;
    }

    /** {@code abortRespawnSequence}. */
    private void abortRespawnSequence() {
        state.respawnStage = STAGE_NONE;
        state.respawnTime = 0;
        resetSpikeCrystals();
        spawnExitPortal(true);
        dirty = true;
    }

    /** {@code resetSpikeCrystals}: 가시 기둥 수정의 무적과 광선을 푼다. */
    private void resetSpikeCrystals() {
        for (VoidEndGenerator.Spike spike : VoidEndGenerator.spikes(host.seed())) {
            for (DragonCrystal crystal : crystalsInSpikeTop(spike)) {
                state.invulnerableCrystals.remove(crystal.mobId());
                state.crystalBeams.remove(crystal.mobId());
            }
        }
    }

    /** {@code setRespawnStage}. */
    private void setRespawnStage(int stage) {
        state.respawnTime = 0;
        if (stage == STAGE_END) {
            state.respawnStage = STAGE_NONE;
            state.dragonKilled = false;
            createNewDragon();
        } else {
            state.respawnStage = stage;
        }
        dirty = true;
    }

    /** {@code DragonRespawnStage.tick} 다섯 갈래. */
    private void tickRespawnStage(List<DragonCrystal> crystals, int time) {
        int[] summon = {0, DragonBrain.DRAGON_SPAWN_Y, 0};
        switch (state.respawnStage) {
            case STAGE_START -> {
                for (DragonCrystal crystal : crystals) state.crystalBeams.put(crystal.mobId(), summon.clone());
                setRespawnStage(STAGE_PREPARING_TO_SUMMON_PILLARS);
            }
            case STAGE_PREPARING_TO_SUMMON_PILLARS -> {
                if (time < 100) {
                    if (time == 0 || time == 50 || time == 51 || time == 52 || time >= 95) {
                        host.levelEvent(DragonEvents.ANIMATION_DRAGON_SUMMON_ROAR, 0, DragonBrain.DRAGON_SPAWN_Y, 0,
                                0, false);
                    }
                } else {
                    setRespawnStage(STAGE_SUMMONING_PILLARS);
                }
            }
            case STAGE_SUMMONING_PILLARS -> {
                boolean start = time % 40 == 0;
                boolean finish = time % 40 == 39;
                if (start || finish) {
                    VoidEndGenerator.Spike[] spikes = VoidEndGenerator.spikes(host.seed());
                    int index = time / 40;
                    if (index < spikes.length) {
                        VoidEndGenerator.Spike spike = spikes[index];
                        if (start) {
                            for (DragonCrystal crystal : crystals) {
                                state.crystalBeams.put(crystal.mobId(),
                                        new int[] {spike.centerX(), spike.height() + 1, spike.centerZ()});
                            }
                        } else {
                            for (int bx = spike.centerX() - 10; bx <= spike.centerX() + 10; bx++) {
                                for (int by = spike.height() - 10; by <= spike.height() + 10; by++) {
                                    for (int bz = spike.centerZ() - 10; bz <= spike.centerZ() + 10; bz++) {
                                        host.removeBlock(bx, by, bz);
                                    }
                                }
                            }
                            host.explode(spike.centerX() + 0.5, spike.height(), spike.centerZ() + 0.5,
                                    PILLAR_EXPLOSION_POWER, true, null);
                            // new EndSpikeFeature(List.of(spike), crystalInvulnerable = true, beam = (0,128,0)).place
                            VoidEndGenerator.spikeCells(spike, host::setBlock);
                            long crystal = host.spawnCrystal(spike.centerX() + 0.5, spike.height() + 1,
                                    spike.centerZ() + 0.5, true);
                            state.invulnerableCrystals.add(crystal);
                            state.crystalBeams.put(crystal, summon.clone());
                        }
                    } else if (start) {
                        setRespawnStage(STAGE_SUMMONING_DRAGON);
                    }
                }
            }
            case STAGE_SUMMONING_DRAGON -> {
                if (time >= 100) {
                    setRespawnStage(STAGE_END);
                    resetSpikeCrystals();
                    for (DragonCrystal crystal : crystals) {
                        state.crystalBeams.remove(crystal.mobId());
                        host.explode(crystal.x(), crystal.y(), crystal.z(), CRYSTAL_EXPLOSION_POWER, false, null);
                        host.removeCrystal(crystal.mobId());
                    }
                    state.respawnCrystals.clear();
                } else if (time >= 80) {
                    host.levelEvent(DragonEvents.ANIMATION_DRAGON_SUMMON_ROAR, 0, DragonBrain.DRAGON_SPAWN_Y, 0, 0,
                            false);
                } else if (time == 0) {
                    for (DragonCrystal crystal : crystals) state.crystalBeams.put(crystal.mobId(), summon.clone());
                } else if (time < 5) {
                    host.levelEvent(DragonEvents.ANIMATION_DRAGON_SUMMON_ROAR, 0, DragonBrain.DRAGON_SPAWN_Y, 0, 0,
                            false);
                }
            }
            default -> {
            }
        }
    }

    // ═══════════════ 드래곤 ═══════════════

    private void tickDragon() {
        DragonBrain dragon = brain;
        if (dragon == null || state.dragonMobId == 0) return;
        if (!host.dragonPresent(state.dragonMobId)) {
            brain = null;
            return;
        }
        // tickDeath/aiStep 모두 첫 줄이 dragonFight.updateDragon(this) 다.
        updateDragon(dragon);
        dragon.tick(this);
        if (!dragon.removed) updateDragon(dragon);
        if (brain == dragon) {
            state.dragonPhase = dragon.phase().id();
            state.dragonDeathTime = dragon.dragonDeathTime;
            state.dragonYRot = dragon.yRot;
            state.dragonRandomState = dragon.random.state();
        }
    }

    /** 드래곤 부위 피해({@code EnderDragon.hurt(level, part, source, amount)}). 드래곤이 없으면 거절. */
    public DragonBrain.HurtResult hurtDragon(int part, DragonBrain.DamageSource source, float amount) {
        if (brain == null) return new DragonBrain.HurtResult(false, false, false, false);
        DragonBrain.HurtResult result = brain.hurt(part, source, amount);
        if (result.healthChanged()) {
            bossProgress = brain.health() / DragonBrain.MAX_HEALTH;
            dirty = true;
        }
        return result;
    }

    // ═══════════════ DragonWorld(두뇌 포트) ═══════════════

    @Override public int heightNoLeaves(int x, int z) { return host.heightNoLeaves(x, z); }

    @Override public int heightMotionBlocking(int x, int z) { return host.heightMotionBlocking(x, z); }

    @Override public int block(int x, int y, int z) { return host.block(x, y, z); }

    @Override public boolean removeBlock(int x, int y, int z) { return host.removeBlock(x, y, z); }

    @Override public List<DragonPlayer> players() { return host.players(); }

    @Override
    public boolean lineOfSight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return host.lineOfSight(fromX, fromY, fromZ, toX, toY, toZ);
    }

    @Override
    public List<DragonVictim> livingEntitiesIn(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        return host.livingEntitiesIn(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override public void push(DragonVictim victim, double dx, double dy, double dz) { host.push(victim, dx, dy, dz); }

    @Override public void hurtByDragon(DragonVictim victim, float amount) { host.hurtByDragon(victim, amount); }

    @Override
    public List<DragonCrystal> crystalsNear(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        List<DragonCrystal> out = new ArrayList<>();
        for (DragonCrystal crystal : host.crystals()) {
            if (crystal.x() + 1.0 > minX && crystal.x() - 1.0 < maxX && crystal.y() + 2.0 > minY
                    && crystal.y() < maxY && crystal.z() + 1.0 > minZ && crystal.z() - 1.0 < maxZ) {
                out.add(crystal);
            }
        }
        return out;
    }

    @Override
    public boolean crystalAlive(long crystalMobId) {
        for (DragonCrystal crystal : host.crystals()) {
            if (crystal.mobId() == crystalMobId) return true;
        }
        return false;
    }

    @Override
    public void spawnFireball(double x, double y, double z, double dirX, double dirY, double dirZ) {
        host.spawnFireball(x, y, z, dirX, dirY, dirZ);
    }

    @Override public long spawnSittingFlame(double x, double y, double z) { return host.spawnSittingFlame(x, y, z); }

    @Override public void discardCloud(long cloudId) { host.discardCloud(cloudId); }

    @Override
    public void levelEvent(int event, int x, int y, int z, int data, boolean global) {
        host.levelEvent(event, x, y, z, data, global);
    }

    @Override public void awardExperience(double x, double y, double z, int amount) { host.awardExperience(x, y, z, amount); }

    @Override public boolean mobGriefing() { return host.mobGriefing(); }

    @Override public int aliveCrystals() { return state.aliveCrystals; }

    @Override public boolean previouslyKilledDragon() { return state.previouslyKilled; }

    @Override
    public void dragonKilled() {
        long mobId = state.dragonMobId;
        setDragonKilled(mobId);
        host.removeDragon(mobId);
        brain = null;
        state.dragonMobId = 0;
        state.dragonPhase = DragonPhase.HOLDING_PATTERN.id();
        state.dragonDeathTime = 0;
        dirty = true;
    }

    /** 영속 스냅샷에 들어갈 드래곤 없는 싸움의 수정 id 정리(없어진 수정의 광선·무적을 지운다). */
    public void pruneCrystalState() {
        Set<Long> alive = new HashSet<>();
        for (DragonCrystal crystal : host.crystals()) alive.add(crystal.mobId());
        state.crystalBeams.keySet().removeIf(id -> !alive.contains(id));
        state.invulnerableCrystals.removeIf(id -> !alive.contains(id));
    }

    /** 수정 광선 사본(몹 id → 좌표). 권위가 차분 송신에 쓴다. */
    public Map<Long, int[]> beamSnapshot() {
        Map<Long, int[]> out = new LinkedHashMap<>();
        // 치유 광선은 dragonState.nearestCrystalMobId 로 따로 싣는다(바닐라 EnderDragonRenderer 가 그린다).
        for (Map.Entry<Long, int[]> entry : state.crystalBeams.entrySet()) out.put(entry.getKey(), entry.getValue().clone());
        return out;
    }
}
