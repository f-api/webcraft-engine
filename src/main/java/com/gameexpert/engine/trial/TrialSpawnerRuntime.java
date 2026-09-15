package com.gameexpert.engine.trial;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.PlayerInteractionRules;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.MobWorldView;
import com.gameexpert.engine.mob.PlayerSnapshot;
import com.gameexpert.engine.mob.SpawnRequest;
import com.gameexpert.engine.raid.RaidLedger;
import com.gameexpert.terrain.Blocks;

/**
 * [TRIAL] 트라이얼 스포너 · 금고 <b>실동작</b> 권위. 바닐라 {@code TrialSpawnerState.tickAndGetNext}
 * 와 {@code VaultState.tickAndGetNext}(26.3-snapshot-7 jar javap)의 상태 전이를 실제 틱·실제 소환·
 * 실제 금고로 잇는 유일한 자리다.
 *
 * <ul>
 *   <li>INACTIVE → WAITING_FOR_PLAYERS: 다음 틱(바닐라는 표시 개체를 만든 뒤).</li>
 *   <li>WAITING_FOR_PLAYERS → ACTIVE: 감지 주기({@link TrialSpawnerContract#isPlayerScanTick})에
 *       시선이 닿는 범위 안 플레이어를 찾으면({@code PlayerDetector.NO_CREATIVE_PLAYERS}).</li>
 *   <li>ACTIVE: 틱 머리의 추가 인원으로 목표 총 소환 수를 다시 세고({@code hasFinishedSpawningAllMobs}),
 *       {@code nextMobSpawnsAt} 이 지나고 동시 생존 수가 상한 아래면 한 마리씩 소환한다. 불길하면
 *       160 MC 틱마다 불길한 아이템 소환기를 띄운다. 총 소환 수를 채우고 명단이 비면
 *       WAITING_FOR_REWARD_EJECTION(쿨다운 시각 확정).</li>
 *   <li>WAITING_FOR_REWARD_EJECTION → EJECTING_REWARD: 승리 40 MC 틱 뒤 셔터가 열린다.</li>
 *   <li>EJECTING_REWARD: 30 MC 틱마다 감지 집합의 한 명 몫을 배출하고 그 한 명을 뺀다. 집합이 비면
 *       셔터를 닫고 COOLDOWN.</li>
 *   <li>COOLDOWN → WAITING_FOR_PLAYERS: 쿨다운이 끝나면 불길함을 벗는다. 불길하지 않은 스포너는
 *       쿨다운 중에도 징조를 지닌 플레이어를 감지해 불길해지고 곧장 ACTIVE 가 된다.</li>
 * </ul>
 *
 * <p>금고는 바닐라 {@code VaultBlockEntity.Server.tick} 을 따른다: INACTIVE 는 활성 반경 4, ACTIVE ·
 * EJECTING 끝은 비활성 반경 4.5 안의 <b>아직 보상받지 않은</b> 플레이어({@code connectedPlayers})가
 * 있으면 ACTIVE, 없으면 INACTIVE 이고, 판정 뒤 20 MC 틱을 쉰다. 열쇠를 넣으면 UNLOCKING(14 MC 틱)
 * → EJECTING 이 되어 20 MC 틱마다 전리품을 하나씩(목록의 끝부터) 윗면 1.2 위로 배출한다.</p>
 *
 * <p><b>재사용</b>: 소환 회계는 {@link RaidLedger} 다(한 마리 = 한 웨이브). anchor 자리에는
 * 몹 대신 스포너 블록의 존재와 청크 틱 여부를 넣고, 레이드의 활성 시간 상한은 쓰지 않는다(바닐라
 * 트라이얼 스포너에는 그런 상한이 없다 — 명단이 비기 전까지 ACTIVE 다).</p>
 *
 * <p><b>문서화된 축소</b>: 소환 후보 좌표는 좌표 시드 lane 이다(바닐라의 연속 무작위 좌표 대신,
 * 시선 검사는 바닐라 그대로). 레벨 난수를 쓰는 선택(spawn_potentials · 장비 표 · 아이템 소환기의
 * 대상·항목)은 두 권위가 같은 값을 내도록 결정론 시드로 옮겼다. 게임 모드가 없어 모든 플레이어가
 * 생존 모드로 감지된다.</p>
 *
 * <p>월드·JPA·프로토콜 타입을 참조하지 않고 {@link MobWorldView} 포트만 읽는다. 정적판
 * {@code StandaloneTrialRuntime.ts} 가 같은 판정을 한 줄씩 사본으로 유지한다.</p>
 */
public final class TrialSpawnerRuntime {

    /** 쿨다운(권위 틱). {@link TrialSpawnerContract#COOLDOWN_TICKS} 와 같은 값이다. */
    public static final int COOLDOWN_TICKS = TrialSpawnerContract.COOLDOWN_TICKS;

    /** 소환 후보 y 를 스포너 높이 기준으로 훑는 범위. 챔버 바닥/한 칸 위를 모두 본다. */
    private static final int VERTICAL_SEARCH_MIN = -1;
    private static final int VERTICAL_SEARCH_MAX = 2;

    /** 바닐라 {@code TrialSpawner.MAX_MOB_TRACKING_DISTANCE_SQR = Mth.square(47)}. */
    public static final int MAX_MOB_TRACKING_DISTANCE_SQR = 47 * 47;

    /** 바닐라 {@code Player} 서 있기 1.8 · 웅크리기 1.5 높이({@code calculatePositionAbove} 가 쓴다). */
    private static final double PLAYER_STANDING_HEIGHT = 1.8;
    private static final double PLAYER_CROUCHING_HEIGHT = 1.5;

    /** 영속된 옛 행(상태 칸이 없던 스키마)의 표식. */
    public static final int LEGACY_STATE = -1;

    // ── 바닐라 level event 번호(LevelEvent) ──
    public static final int EVENT_SPAWNER_SPAWN = 3011;
    public static final int EVENT_SPAWNER_SPAWN_MOB_AT = 3012;
    public static final int EVENT_SPAWNER_DETECT_PLAYER = 3013;
    public static final int EVENT_SPAWNER_EJECT_ITEM = 3014;
    public static final int EVENT_VAULT_ACTIVATE = 3015;
    public static final int EVENT_VAULT_DEACTIVATE = 3016;
    public static final int EVENT_VAULT_EJECT_ITEM = 3017;
    public static final int EVENT_SPAWNER_DETECT_PLAYER_OMINOUS = 3019;
    public static final int EVENT_SPAWNER_BECOME_OMINOUS = 3020;

    // ── 금고 수치(VaultConfig.DEFAULT · VaultBlockEntity$Server · VaultState, MC 틱) ──
    public static final double VAULT_ACTIVATION_RANGE = TrialVaultContract.ACTIVATION_RANGE;
    public static final double VAULT_DEACTIVATION_RANGE = TrialVaultContract.DEACTIVATION_RANGE;
    public static final int VAULT_UPDATE_PAUSE_MC = 20;
    public static final int VAULT_UNLOCKING_DELAY_MC = 14;
    public static final int VAULT_INSERT_FAIL_SOUND_BUFFER_MC = 15;

    /** 소환 하나. {@code wave} 는 원장의 1-기반 소환 번호다. */
    public record Release(long trialId, int wave, SpawnRequest request) {}

    /** 렌더 상태 비트 한 칸. 권위가 실제 블록 상태를 이 값으로 덮는다. */
    /**
     * 블록 상태 비트 변화. [TRIAL-GAP] 금고 전이는 그 순간의 {@code stateUpdatingResumesAt}(MC 틱)을
     * 함께 싣는다 — 바닐라 {@code VaultServerData} CODEC 이 저장하는 칸이다. 금고가 아니면 -1.
     */
    public record StateChange(int x, int y, int z, int blockType, int state, long vaultResumesAtMc) {
        public StateChange(int x, int y, int z, int blockType, int state) {
            this(x, y, z, blockType, state, -1L);
        }
    }

    /** 불길한 징조를 시련의 징조로 바꿔야 할 플레이어. 호출자가 실제 효과 목록을 고친다. */
    public record OmenConversion(String nickname) {}

    /** 바닐라 {@code ServerLevel.levelEvent(null, event, pos, data)} 한 번. */
    public record LevelEvent(int event, int x, int y, int z, int data) {}

    /**
     * 바닐라 서버 {@code playSound(null, pos, event, BLOCKS)} 한 번. 좌표는 소리 원점(블록 중심)이고
     * {@code pitch} 가 NaN 이면 kind 의 바닐라 기본(1.0)이다.
     */
    public record SoundEmit(String kind, double x, double y, double z, int blockType, float pitch) {}

    /** 불길한 아이템 소환기 하나(바닐라 {@code OminousItemSpawner.create} + {@code snapTo}). */
    public record ItemSpawnerRequest(double x, double y, double z, short itemType, int count) {}

    /** 금고가 배출할 전리품 한 칸. {@code token} 은 durable outbox 의 행 identity 다. */
    public record VaultItem(String token, TrialLootTables.Stack stack) {}

    /** 금고 배출 한 번(바닐라 {@code VaultState.ejectResultItem}). */
    public record VaultEjection(long vaultId, int x, int y, int z, String token, long entityId,
            TrialLootTables.Stack stack) {}

    /**
     * 배출 한 번(바닐라 {@code ejectReward}). 이름은 옛 "승리 보상" 계약을 잇는다 — 첫 배출의
     * identity 가 옛 승리 보상 identity 와 같아 이미 기록된 보상 행이 그대로 이어진다.
     */
    public record Victory(long trialId, int x, int y, int z, String heroNickname,
            String rewardIdentity, long rewardEntityId, List<int[]> vaults, short itemType,
            int count) {
        public Victory {
            if (rewardIdentity == null || rewardIdentity.isBlank()) {
                throw new IllegalArgumentException("trial reward identity is required");
            }
            if (itemType == 0 || count <= 0) {
                throw new IllegalArgumentException("trial reward item is required");
            }
            vaults = copyPositions(vaults);
        }

        @Override public List<int[]> vaults() { return copyPositions(vaults); }
    }

    /** Complete durable aggregate for one stable world-position trial identity. */
    public record SiteSnapshot(int x, int y, int z, long trialId, long armedTick,
            int detectedPlayers, boolean armed, long cooldownUntilTick, int phaseState,
            RaidLedger.InstanceSnapshot ledger, String heroNickname, String rewardIdentity,
            boolean rewardPending, long rewardEntityId, List<int[]> activatedVaults,
            int trialState, boolean ominous, int ejectionsRemaining, int ejectedCount,
            long nextSpawnTick, short rewardItemType, int rewardCount,
            List<String> detectedNicknames) {
        public SiteSnapshot {
            if (detectedPlayers < 0) throw new IllegalArgumentException("detectedPlayers must be nonnegative");
            if (rewardPending && (rewardIdentity == null || rewardIdentity.isBlank())) {
                throw new IllegalArgumentException("pending trial reward identity is required");
            }
            if (rewardEntityId < 0 || rewardEntityId == Long.MAX_VALUE) {
                throw new IllegalArgumentException("trial reward entity identity is invalid");
            }
            if (ledger != null && ledger.raidId() != trialId) {
                throw new IllegalArgumentException("trial ledger identity mismatch");
            }
            if (trialState != LEGACY_STATE && TrialSpawnerContract.State.fromBits(trialState) == null) {
                throw new IllegalArgumentException("trial state is invalid");
            }
            if (ejectionsRemaining < 0 || ejectedCount < 0) {
                throw new IllegalArgumentException("trial ejection counters must be nonnegative");
            }
            if (rewardPending && (rewardItemType == 0 || rewardCount <= 0)) {
                throw new IllegalArgumentException("pending trial reward item is required");
            }
            activatedVaults = copyPositions(activatedVaults);
            detectedNicknames = detectedNicknames == null ? List.of() : List.copyOf(detectedNicknames);
            for (String nickname : detectedNicknames) {
                if (nickname == null || nickname.isBlank()) {
                    throw new IllegalArgumentException("detected trial nickname is invalid");
                }
            }
            if (new LinkedHashSet<>(detectedNicknames).size() != detectedNicknames.size()) {
                throw new IllegalArgumentException("detected trial nicknames must be unique");
            }
        }

        /** [TRIAL] 옛 스키마(감지 집합 칸 없음) 호환 생성자. 인원수만 들고 있던 행이다. */
        public SiteSnapshot(int x, int y, int z, long trialId, long armedTick,
                int detectedPlayers, boolean armed, long cooldownUntilTick, int phaseState,
                RaidLedger.InstanceSnapshot ledger, String heroNickname, String rewardIdentity,
                boolean rewardPending, long rewardEntityId, List<int[]> activatedVaults,
                int trialState, boolean ominous, int ejectionsRemaining, int ejectedCount,
                long nextSpawnTick, short rewardItemType, int rewardCount) {
            this(x, y, z, trialId, armedTick, detectedPlayers, armed, cooldownUntilTick,
                    phaseState, ledger, heroNickname, rewardIdentity, rewardPending,
                    rewardEntityId, activatedVaults, trialState, ominous, ejectionsRemaining,
                    ejectedCount, nextSpawnTick, rewardItemType, rewardCount, List.of());
        }

        /** 옛 스키마 호환 생성자: 새 칸은 옛 행 표식(상태 −1 · 트라이얼 열쇠 1개)이다. */
        public SiteSnapshot(int x, int y, int z, long trialId, long armedTick,
                int detectedPlayers, boolean armed, long cooldownUntilTick, int phaseState,
                RaidLedger.InstanceSnapshot ledger, String heroNickname, String rewardIdentity,
                boolean rewardPending, long rewardEntityId, List<int[]> activatedVaults) {
            this(x, y, z, trialId, armedTick, detectedPlayers, armed, cooldownUntilTick,
                    phaseState, ledger, heroNickname, rewardIdentity, rewardPending,
                    rewardEntityId, activatedVaults, LEGACY_STATE, false, 0, 0, 0L,
                    com.gameexpert.engine.inventory.PlayerInventory.TRIAL_KEY, 1, List.of());
        }

        @Override public List<int[]> activatedVaults() { return copyPositions(activatedVaults); }
    }

    /** 스포너 한 자리. */
    private static final class Site {
        private final int x;
        private final int y;
        private final int z;
        private final long trialId;
        private TrialSpawnerContract.State state = TrialSpawnerContract.State.WAITING_FOR_PLAYERS;
        private boolean ominous;
        private long armedTick;
        /** 바닐라 {@code TrialSpawnerStateData.detectedPlayers}(UUID 집합 → 닉네임 집합). */
        private final LinkedHashSet<String> detected = new LinkedHashSet<>();
        /** 바닐라 {@code cooldownEndsAt}. ACTIVE 에서는 불길한 아이템 소환기 시각을 겸한다. */
        private long cooldownUntilTick;
        /** 바닐라 {@code nextMobSpawnsAt}(권위 틱). */
        private long nextSpawnTick;
        private int ejectionsRemaining;
        private int ejectedCount;
        /** 직전 틱의 살아 있는 명단 수. 줄면 바닐라처럼 다음 소환 시각을 미룬다(영속하지 않는다). */
        private int lastLiveMembers;
        private int lastPublishedState = -1;
        private String heroNickname;
        private String rewardIdentity;
        private boolean rewardPending;
        private long rewardEntityId;
        private short rewardItemType;
        private int rewardCount;
        private List<int[]> activatedVaults = List.of();
        /** 바닐라 {@code dispensing}: 스포너마다 한 번 굴려 고정한 아이템 소환기 목록(캐시). */
        private List<TrialLootTables.Stack> dispensing;

        private Site(int x, int y, int z, long trialId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.trialId = trialId;
        }

        private int blockBits() {
            return state.stateBits() | (ominous ? TrialSpawnerContract.OMINOUS : 0);
        }

        private long victoryTick() {
            return cooldownUntilTick - COOLDOWN_TICKS;
        }
    }

    /** 금고 한 자리(바닐라 {@code VaultServerData} + {@code VaultSharedData.connectedPlayers}). */
    private static final class Vault {
        private final int x;
        private final int y;
        private final int z;
        private final long vaultId;
        private int state;
        private int shapeBits;
        private long resumesAtMc;
        private long lastInsertFailMc;
        private final LinkedHashSet<String> connected = new LinkedHashSet<>();
        private final LinkedHashSet<String> rewarded = new LinkedHashSet<>();
        /** 바닐라 {@code itemsToEject}. 끝에서부터 꺼낸다. */
        private final ArrayList<VaultItem> items = new ArrayList<>();
        private int totalEjectionsNeeded;
        /** 열쇠 정산이 영속 lane 에 있는 동안은 바닐라처럼 UNLOCKING 으로 본다(두 번째 삽입 거절). */
        private boolean unlockInFlight;

        private Vault(int x, int y, int z, long vaultId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vaultId = vaultId;
        }

        private boolean ominous() {
            return (shapeBits & TrialVaultContract.OMINOUS) != 0;
        }
    }

    /** 명단원의 현재 좌표. {@code {x, y, z, height}} 또는 없으면(죽음·제거) null. */
    public interface MemberView {
        double[] member(long mobId);
    }

    private final RaidLedger ledger = new RaidLedger();
    private final Map<Long, Site> sites = new LinkedHashMap<>();
    private final Map<Long, Vault> vaults = new LinkedHashMap<>();
    /** 복원된 금고별 보상 받은 닉네임(금고 개체가 아직 관찰되지 않은 동안 보관). */
    private final Map<Long, Set<String>> restoredVaultRewarded = new LinkedHashMap<>();
    /** 복원된 금고별 남은 배출(금고 개체가 아직 관찰되지 않은 동안 보관). */
    private final Map<Long, List<VaultItem>> restoredVaultItems = new LinkedHashMap<>();
    /** [TRIAL-GAP] 복원된 금고별 stateUpdatingResumesAt(MC 틱), 좌표 키. */
    private final Map<Long, Long> restoredVaultTimers = new LinkedHashMap<>();
    private final List<StateChange> pendingStateChanges = new ArrayList<>();
    private final List<Victory> pendingVictories = new ArrayList<>();
    private final List<OmenConversion> pendingOmenConversions = new ArrayList<>();
    private final List<Long> pendingDiscardedMembers = new ArrayList<>();
    private final List<LevelEvent> pendingLevelEvents = new ArrayList<>();
    private final List<SoundEmit> pendingSounds = new ArrayList<>();
    private final List<ItemSpawnerRequest> pendingItemSpawners = new ArrayList<>();
    private final List<VaultEjection> pendingVaultEjections = new ArrayList<>();

    private List<int[]> spawnerBlocks = List.of();
    private List<int[]> vaultBlocks = List.of();
    /** 마지막으로 돈 권위 틱. 틱 밖(상호작용·영속 완료)에서 금고 시각을 잴 때 쓴다. */
    private long lastWorldTick;

    /**
     * 이번 틱의 후보 좌표. 인덱스({@code SpawnerIndex})가 플레이어 주변 청크에서만 찾아 주므로
     * 이 클래스는 월드 전역 탐색을 하지 않는다.
     */
    public void observeFixtures(MobWorldView world, List<int[]> spawners, List<int[]> vaults) {
        this.spawnerBlocks = spawners == null ? List.of() : spawners;
        this.vaultBlocks = vaults == null ? List.of() : vaults;
        if (world == null) return;
        pendingStateChanges.removeIf(change -> {
            if (change.blockType() != Blocks.VAULT
                    || !world.isChunkActive(Math.floorDiv(change.x(), 16),
                            Math.floorDiv(change.z(), 16))
                    || world.getBlock(change.x(), change.y(), change.z()) == (short) Blocks.VAULT) {
                return false;
            }
            this.vaults.remove(positionKey(change.x(), change.y(), change.z()));
            return true;
        });
        // Vault state is persisted as the block's sparse state. Rebuilding only the coordinate
        // index after a server restart must restore the vault from that state.
        for (int[] vault : this.vaultBlocks) {
            if (vault == null || vault.length < 3) continue;
            int x = vault[0], y = vault[1], z = vault[2];
            if (world.getBlock(x, y, z) != (short) Blocks.VAULT) continue;
            int blockState = world.blockState(x, y, z, Blocks.VAULT);
            Vault known = vaultAt(world, x, y, z, blockState);
            known.shapeBits = blockState & ~TrialVaultContract.STATE_MASK;
        }
    }

    private Vault vaultAt(MobWorldView world, int x, int y, int z, int blockState) {
        long key = positionKey(x, y, z);
        Vault vault = vaults.get(key);
        if (vault != null) return vault;
        vault = new Vault(x, y, z, TrialVaultContract.vaultId(world.worldSeed(), x, y, z));
        vault.state = blockState & TrialVaultContract.STATE_MASK;
        vault.shapeBits = blockState & ~TrialVaultContract.STATE_MASK;
        Long resumesAt = restoredVaultTimers.remove(key);
        if (resumesAt != null) vault.resumesAtMc = resumesAt;
        Set<String> rewarded = restoredVaultRewarded.remove(vault.vaultId);
        if (rewarded != null) vault.rewarded.addAll(rewarded);
        List<VaultItem> items = restoredVaultItems.remove(vault.vaultId);
        if (items != null && !items.isEmpty()) {
            vault.items.addAll(items);
            vault.totalEjectionsNeeded = items.size();
            // 배출 도중 멈춘 금고는 바닐라처럼 EJECTING 에서 이어 간다.
            vault.state = TrialVaultContract.State.EJECTING.stateBits();
        }
        vaults.put(key, vault);
        return vault;
    }

    /** 소환 회계 원장. 레이드 원장과 같은 클래스이며 인스턴스만 다르다. */
    public RaidLedger ledger() {
        return ledger;
    }

    /** 옛 호출부·테스트용: 명단 좌표를 모르면 아이템 소환기·폐기 입자가 몹을 보지 않는다. */
    public List<Release> releases(MobWorldView world, long worldTick) {
        return releases(world, mobId -> null, worldTick);
    }

    /**
     * 상태 기계의 틱 단계(바닐라 {@code tickAndGetNext}). 이 틱에 나와야 할 소환을 돌려준다.
     * 호출자는 각 요청을 자기 스폰 경로로 실체화한 뒤 실제 몹 id 로 {@link #join} 을 불러야
     * 명단이 성립한다 — 명단이 없으면 승리도 없다. 금고 상태 기계도 같은 틱에 돈다.
     */
    public List<Release> releases(MobWorldView world, MemberView members, long worldTick) {
        lastWorldTick = worldTick;
        List<Release> releases = null;
        for (int[] position : spawnerBlocks) {
            int x = position[0];
            int y = position[1];
            int z = position[2];
            if (world.getBlock(x, y, z) != (short) Blocks.TRIAL_SPAWNER) continue;
            // 바닐라 블록 개체는 틱 중인 청크에서만 돈다. 비활성 청크의 스포너는 상태를 그대로 둔다.
            if (!world.isChunkActive(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            long key = positionKey(x, y, z);
            Site site = sites.get(key);
            if (site == null) {
                site = newSite(world, x, y, z);
                sites.put(key, site);
            }
            Release release = tickSite(world, members, site, worldTick);
            if (release != null) {
                if (releases == null) releases = new ArrayList<>(2);
                releases.add(release);
            }
            publishState(site);
        }
        tickVaults(world, worldTick);
        return releases == null ? List.of() : releases;
    }

    private Site newSite(MobWorldView world, int x, int y, int z) {
        Site site = new Site(x, y, z, TrialSpawnerContract.trialId(world.worldSeed(), x, y, z));
        int blockState = world.blockState(x, y, z, Blocks.TRIAL_SPAWNER);
        TrialSpawnerContract.State carried = TrialSpawnerContract.State.fromBits(blockState);
        // 기록이 없는 자리는 원장·쿨다운 사실이 없으므로 대기(구조물 carrier 의 waiting_for_players)
        // 에서 시작한다. INACTIVE 만 그대로 두어 바닐라처럼 다음 틱에 대기로 넘어간다.
        site.state = carried == TrialSpawnerContract.State.INACTIVE
                ? TrialSpawnerContract.State.INACTIVE
                : TrialSpawnerContract.State.WAITING_FOR_PLAYERS;
        site.lastPublishedState = blockState
                & (TrialSpawnerContract.STATE_MASK | TrialSpawnerContract.OMINOUS);
        return site;
    }

    private Release tickSite(MobWorldView world, MemberView members, Site site, long worldTick) {
        switch (site.state) {
            case INACTIVE -> site.state = TrialSpawnerContract.State.WAITING_FOR_PLAYERS;
            case WAITING_FOR_PLAYERS -> {
                detectPlayers(world, members, site, worldTick);
                if (!site.detected.isEmpty()) arm(world, site, worldTick);
            }
            case ACTIVE -> {
                // 바닐라 ACTIVE: countAdditionalPlayers 는 tryDetectPlayers 보다 먼저 센다.
                int additional = TrialSpawnerContract.additionalPlayers(site.detected.size());
                detectPlayers(world, members, site, worldTick);
                // 불길해지는 순간 원장을 비웠다(소환 수 0). 같은 ACTIVE 에서 불길한 설정으로 다시 센다.
                if (ledger.instance(site.trialId) == null) arm(world, site, worldTick);
                if (site.ominous) spawnItemSpawner(world, members, site, worldTick);
                ledger.raiseWaveCount(site.trialId,
                        Math.max(1, config(site).targetTotalMobs(additional)));
                return spawnStep(world, site, worldTick, additional);
            }
            case WAITING_FOR_REWARD_EJECTION -> {
                if (worldTick >= site.victoryTick() + TrialSpawnerContract.OPEN_SHUTTER_DELAY_TICKS) {
                    sound(site, "trial_spawner_open_shutter");
                    site.state = TrialSpawnerContract.State.EJECTING_REWARD;
                }
            }
            case EJECTING_REWARD -> ejectStep(site, worldTick);
            case COOLDOWN -> {
                detectPlayers(world, members, site, worldTick);
                if (!site.detected.isEmpty()) {
                    // 쿨다운 중 감지는 불길해진 경우뿐이다(아래 detectPlayers 가 그 외에는 더하지 않는다).
                    // 바닐라 COOLDOWN 분기: totalMobsSpawned = 0 · nextMobSpawnsAt = 0 → ACTIVE.
                    site.nextSpawnTick = 0;
                    arm(world, site, worldTick);
                } else if (worldTick >= site.cooldownUntilTick) {
                    // 바닐라 removeOminous + TrialSpawnerStateData.reset.
                    site.ominous = false;
                    site.detected.clear();
                    site.nextSpawnTick = 0;
                    site.cooldownUntilTick = 0;
                    site.state = TrialSpawnerContract.State.WAITING_FOR_PLAYERS;
                }
            }
        }
        return null;
    }

    /**
     * 바닐라 {@code tryDetectPlayers}. 감지 주기에만 본다. 시선이 닿는 범위 안 플레이어 중 징조를
     * 지닌 이가 있으면 불길해진다(불길한 징조는 이 자리에서 시련의 징조로 바뀐다). 감지 집합이 비어
     * 있으면 시선 목록을, 아니면 시선 없이 범위 안 전원을 집합에 더한다. 쿨다운 중에는 불길해진
     * 경우에만 더한다. 집합이 커지면 첫 소환을 40 MC 틱 뒤로 미루고 감지 입자(3013/3019)를 낸다.
     */
    private void detectPlayers(MobWorldView world, MemberView members, Site site, long worldTick) {
        if (!TrialSpawnerContract.isPlayerScanTick(site.x, site.y, site.z, worldTick)) return;
        boolean cooldown = site.state == TrialSpawnerContract.State.COOLDOWN;
        if (cooldown && site.ominous) return;
        List<PlayerSnapshot> sighted = detect(world, site.x, site.y, site.z, true);
        boolean becameOminous = false;
        if (!site.ominous && !sighted.isEmpty()) {
            PlayerSnapshot omen = playerWithOminousEffect(sighted);
            if (omen != null) {
                if (!omen.hasEffect(StatusEffect.TRIAL_OMEN)) {
                    pendingOmenConversions.add(new OmenConversion(omen.nickname()));
                }
                double eyeY = omen.y() + PlayerInteractionRules.eyeHeight(omen.crouching());
                pendingLevelEvents.add(new LevelEvent(EVENT_SPAWNER_BECOME_OMINOUS,
                        (int) Math.floor(omen.x()), (int) Math.floor(eyeY),
                        (int) Math.floor(omen.z()), 0));
                applyOminous(members, site, worldTick);
                becameOminous = true;
            }
        }
        if (cooldown && !becameOminous) return;
        List<PlayerSnapshot> found = site.detected.isEmpty()
                ? sighted : detect(world, site.x, site.y, site.z, false);
        boolean grew = false;
        for (PlayerSnapshot player : found) grew |= site.detected.add(player.nickname());
        if (!grew) return;
        site.nextSpawnTick = Math.max(worldTick
                + TrialSpawnerContract.DETECT_PLAYER_SPAWN_BUFFER_TICKS, site.nextSpawnTick);
        if (site.heroNickname == null || site.state != TrialSpawnerContract.State.ACTIVE) {
            site.heroNickname = nearestPlayerNickname(found, site.x, site.y, site.z);
        }
        if (!becameOminous) {
            pendingLevelEvents.add(new LevelEvent(site.ominous
                    ? EVENT_SPAWNER_DETECT_PLAYER_OMINOUS : EVENT_SPAWNER_DETECT_PLAYER,
                    site.x, site.y, site.z, site.detected.size()));
        }
    }

    /**
     * 바닐라 {@code findPlayerWithOminousEffect}: 감지 순서대로 시련의 징조가 있으면 그 플레이어,
     * 없으면 마지막으로 만난 불길한 징조 플레이어.
     */
    private static PlayerSnapshot playerWithOminousEffect(List<PlayerSnapshot> players) {
        PlayerSnapshot badOmen = null;
        for (PlayerSnapshot player : players) {
            if (player.hasEffect(StatusEffect.TRIAL_OMEN)) return player;
            if (player.hasEffect(StatusEffect.BAD_OMEN)) badOmen = player;
        }
        return badOmen;
    }

    /**
     * 바닐라 {@code applyOminous} + {@code resetAfterBecomingOminous}: 블록을 불길하게 바꾸고(3020,
     * data 1), 현재 몹을 치우고(몹마다 3012 · 드랍 없음), 소환 수를 0 으로, 다음 소환을 불길한 설정의
     * 간격 뒤로, 아이템 소환기 시각을 160 MC 틱 뒤로 둔다.
     */
    private void applyOminous(MemberView members, Site site, long worldTick) {
        site.ominous = true;
        pendingLevelEvents.add(new LevelEvent(EVENT_SPAWNER_BECOME_OMINOUS,
                site.x, site.y, site.z, 1));
        RaidLedger.Instance instance = ledger.instance(site.trialId);
        if (instance != null) {
            for (long memberId : instance.liveMemberIds()) {
                double[] position = members.member(memberId);
                if (position != null) {
                    pendingLevelEvents.add(new LevelEvent(EVENT_SPAWNER_SPAWN_MOB_AT,
                            (int) Math.floor(position[0]), (int) Math.floor(position[1]),
                            (int) Math.floor(position[2]), TrialSpawnerContract.FLAME_NORMAL));
                }
                pendingDiscardedMembers.add(memberId);
            }
            ledger.forget(site.trialId);
        }
        site.lastLiveMembers = 0;
        TrialSpawnerContract.MobConfig config = config(site);
        site.nextSpawnTick = worldTick + config.ticksBetweenSpawn();
        site.cooldownUntilTick = worldTick + TrialSpawnerContract.TICKS_BETWEEN_ITEM_SPAWNERS;
    }

    private TrialSpawnerContract.MobConfig config(Site site) {
        // trialId 는 spawnerSeed 와 같은 값이라 역할 선택이 소환 lane 과 같은 종을 고른다.
        return TrialSpawnerContract.mobConfig(TrialSpawnerPool.select(site.trialId), site.ominous);
    }

    /** 원장을 새로 무장해 ACTIVE 로 둔다. 총 소환 수는 매 ACTIVE 틱에 다시 올린다. */
    private void arm(MobWorldView world, Site site, long worldTick) {
        TrialSpawnerContract.MobConfig config = config(site);
        int total = Math.max(1, config.targetTotalMobs(
                TrialSpawnerContract.additionalPlayers(site.detected.size())));
        ledger.forget(site.trialId);
        site.armedTick = worldTick;
        site.lastLiveMembers = 0;
        // anchor 는 몹이 아니라 블록이므로 anchorMobId 자리는 0 이다(정적판과 같다).
        ledger.arm(site.trialId, 0L, site.x + 0.5, site.y, site.z + 0.5,
                site.heroNickname, worldTick, total, world.worldSeed());
        site.state = TrialSpawnerContract.State.ACTIVE;
    }

    /** ACTIVE 의 소환 한 걸음(바닐라 {@code isReadyToSpawnNextMob} → {@code spawnMob}). */
    private Release spawnStep(MobWorldView world, Site site, long worldTick, int additional) {
        RaidLedger.Instance instance = ledger.instance(site.trialId);
        if (instance == null || !instance.ongoing()) return null;
        TrialSpawnerContract.MobConfig config = config(site);
        int live = instance.liveMemberCount();
        if (live < site.lastLiveMembers) {
            // 바닐라 tickServer: 추적 몹이 빠지면 nextMobSpawnsAt = gameTime + ticksBetweenSpawn.
            site.nextSpawnTick = worldTick + config.ticksBetweenSpawn();
        }
        site.lastLiveMembers = live;
        if (instance.allWavesReleased() || worldTick < site.nextSpawnTick) return null;
        if (live >= config.targetSimultaneousMobs(additional)) return null;
        int spawnIndex = instance.releasedWaves() + 1;
        TrialSpawnerContract.Summon summon = TrialSpawnerContract.summon(
                TrialSpawnerContract.spawnerSeed(world.worldSeed(), site.x, site.y, site.z),
                site.x, site.y, site.z, spawnIndex, worldTick);
        MobType type = roleType(summon.role());
        int slimeSize = type == MobType.SLIME
                ? TrialSpawnerContract.slimeSize(site.trialId, site.armedTick, spawnIndex) : 0;
        double width = slimeSize > 0 ? 0.51 * slimeSize : type.width();
        double height = slimeSize > 0 ? 0.51 * slimeSize : type.height();
        Integer spawnY = resolveSpawnY(world, width, height, summon.x(), summon.y(), summon.z());
        // 바닐라 spawnMob 이 자리를 못 찾으면 소환 수도 시각도 그대로 두고 다음 틱에 다시 굴린다.
        if (spawnY == null) return null;
        double spawnX = summon.x() + 0.5;
        double spawnZ = summon.z() + 0.5;
        // 바닐라 TrialSpawner.inLineOfSight(level, center(pos), spawnVec): 소환 자리에서 스포너 중심이
        // 보여야 한다.
        if (!TrialVisualClip.inLineOfSight(world, spawnX, spawnY, spawnZ,
                site.x + 0.5, site.y + 0.5, site.z + 0.5)) {
            return null;
        }
        TrialSpawnerPool.Role role = summon.role();
        List<TrialLootTables.Stack> equipment = site.ominous
                && TrialSpawnerContract.equipsWhenOminous(role)
                ? TrialLootTables.equipment(TrialSpawnerContract.rangedEquipment(role),
                        TrialSpawnerContract.equipmentSeed(site.trialId, site.armedTick, spawnIndex))
                : List.of();
        ledger.markWaveReleased(site.trialId, spawnIndex);
        site.nextSpawnTick = worldTick + config.ticksBetweenSpawn();
        site.lastLiveMembers = live + 1;
        int flame = site.ominous ? TrialSpawnerContract.FLAME_OMINOUS
                : TrialSpawnerContract.FLAME_NORMAL;
        pendingLevelEvents.add(new LevelEvent(EVENT_SPAWNER_SPAWN, site.x, site.y, site.z, flame));
        pendingLevelEvents.add(new LevelEvent(EVENT_SPAWNER_SPAWN_MOB_AT,
                summon.x(), spawnY, summon.z(), flame));
        return new Release(site.trialId, spawnIndex,
                SpawnRequest.trial(type, spawnX, spawnY, spawnZ, slimeSize, equipment));
    }

    /**
     * 바닐라 {@code spawnOminousOminousItemSpawner}. 아이템 소환기 시각이 되면 감지 집합의 살아 있는
     * 플레이어(범위 14 이내)가 있을 때, 명단 몹과 그 플레이어들 중 한쪽을 반반으로 골라 그중 하나의
     * 머리 위(키 + 2 + [0,4) 블록, 시각 모양이 막으면 그 아래)에 소환기를 띄운다.
     */
    private void spawnItemSpawner(MobWorldView world, MemberView members, Site site,
            long worldTick) {
        if (site.dispensing == null) {
            site.dispensing = TrialLootTables.ominousDispensingItems(world.worldSeed(),
                    site.x, site.y, site.z);
        }
        if (site.dispensing.isEmpty() || worldTick < site.cooldownUntilTick) return;
        double centerX = site.x + 0.5;
        double centerY = site.y + 0.5;
        double centerZ = site.z + 0.5;
        double rangeSquared = TrialSpawnerContract.ACTIVATION_RANGE
                * TrialSpawnerContract.ACTIVATION_RANGE;
        List<double[]> players = new ArrayList<>(2);
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive() || !site.detected.contains(player.nickname())) continue;
            if (distanceSquared(player.x(), player.y(), player.z(), centerX, centerY, centerZ)
                    > rangeSquared) continue;
            players.add(new double[] {player.x(), player.y(), player.z(),
                player.crouching() ? PLAYER_CROUCHING_HEIGHT : PLAYER_STANDING_HEIGHT});
        }
        if (players.isEmpty()) return;
        List<double[]> mobs = new ArrayList<>(2);
        RaidLedger.Instance instance = ledger.instance(site.trialId);
        if (instance != null) {
            for (long memberId : instance.liveMemberIds()) {
                double[] mob = members.member(memberId);
                if (mob == null || distanceSquared(mob[0], mob[1], mob[2], centerX, centerY,
                        centerZ) > rangeSquared) continue;
                mobs.add(mob);
            }
        }
        long seed = itemSpawnerSeed(site.trialId, worldTick);
        List<double[]> chosen = TrialSpawnerContract.draw(seed, 0, 2) == 0 ? mobs : players;
        if (chosen.isEmpty()) return;
        double[] entity = chosen.size() == 1 ? chosen.get(0)
                : chosen.get(TrialSpawnerContract.draw(seed, 1, chosen.size()));
        // 바닐라 calculatePositionAbove: position.relative(UP, bbHeight + 2 + nextInt(4)) (float 합).
        float lift = (float) entity[3] + 2.0F + TrialSpawnerContract.draw(seed, 2, 4);
        double topY = entity[1] + lift;
        int[] hit = TrialVisualClip.clip(world, entity[0], entity[1], entity[2],
                entity[0], topY, entity[2]);
        int hitX = hit != null ? hit[0] : (int) Math.floor(entity[0]);
        int hitY = hit != null ? hit[1] : (int) Math.floor(topY);
        int hitZ = hit != null ? hit[2] : (int) Math.floor(entity[2]);
        if (TrialVisualClip.hasCollision(world, hitX, hitY - 1, hitZ)) return;
        TrialLootTables.Stack item = weightedDispensing(site.dispensing,
                TrialSpawnerContract.draw(seed, 3, dispensingWeight(site.dispensing)));
        pendingItemSpawners.add(new ItemSpawnerRequest(hitX + 0.5, hitY - 0.5, hitZ + 0.5,
                item.itemType(), 1));
        // 바닐라: SPAWN_ITEM_BEGIN(BLOCKS, volume 1, pitch (r1 - r2) × 0.2 + 1)을 소환기 칸에서 낸다.
        // pitch 의 두 난수는 레벨 난수라 결정론이 필요 없어 클라가 같은 식으로 굴린다(worldSound kind).
        pendingSounds.add(new SoundEmit("trial_spawner_spawn_item_begin",
                hitX + 0.5, hitY - 0.5, hitZ + 0.5, Blocks.TRIAL_SPAWNER, Float.NaN));
        site.cooldownUntilTick = worldTick + TrialSpawnerContract.TICKS_BETWEEN_ITEM_SPAWNERS;
    }

    /** 바닐라 {@code getDispensingItems}: 스택마다 개수를 가중치로 삼는다. */
    private static int dispensingWeight(List<TrialLootTables.Stack> dispensing) {
        int total = 0;
        for (TrialLootTables.Stack stack : dispensing) total += stack.count();
        return total;
    }

    private static TrialLootTables.Stack weightedDispensing(List<TrialLootTables.Stack> dispensing,
            int pick) {
        for (TrialLootTables.Stack stack : dispensing) {
            pick -= stack.count();
            if (pick < 0) return stack;
        }
        return dispensing.get(dispensing.size() - 1);
    }

    static long itemSpawnerSeed(long trialId, long worldTick) {
        return TrialSpawnerContract.rewardSeed(trialId ^ 0x7E3D_91A5_C24F_0B67L, worldTick);
    }

    /** EJECTING_REWARD 의 한 걸음(바닐라 {@code isReadyToEjectItems}). */
    private void ejectStep(Site site, long worldTick) {
        long sinceVictory = worldTick - site.victoryTick();
        if (sinceVictory % TrialSpawnerContract.TICKS_BETWEEN_EJECTIONS != 0) return;
        // 직전 배출이 아직 durable 하게 정산되지 않았으면 이 차례를 건너뛴다(보상 행은 하나다).
        if (site.rewardPending) return;
        if (site.ejectionsRemaining <= 0 || site.detected.isEmpty()) {
            // 바닐라: 감지 집합이 비면 셔터를 닫고 COOLDOWN(배출마다 한 명씩 뺐다).
            site.detected.clear();
            site.ejectionsRemaining = 0;
            sound(site, "trial_spawner_close_shutter");
            site.state = TrialSpawnerContract.State.COOLDOWN;
            return;
        }
        TrialSpawnerContract.Reward reward = TrialSpawnerContract.ejectedReward(
                site.trialId, site.armedTick, site.ominous, site.ejectedCount);
        int ordinal = site.ejectedCount;
        site.ejectedCount++;
        site.ejectionsRemaining--;
        // 바닐라: detectedPlayers.remove(detectedPlayers.iterator().next()).
        Iterator<String> first = site.detected.iterator();
        first.next();
        first.remove();
        if (reward.isNothing()) return;
        site.rewardIdentity = rewardIdentity(site.trialId, site.armedTick, ordinal);
        site.rewardPending = true;
        site.rewardEntityId = 0;
        site.rewardItemType = reward.itemType();
        site.rewardCount = reward.count();
        pendingVictories.add(new Victory(site.trialId, site.x, site.y, site.z,
                site.heroNickname, site.rewardIdentity, 0L, List.of(),
                reward.itemType(), reward.count()));
        pendingLevelEvents.add(new LevelEvent(EVENT_SPAWNER_EJECT_ITEM, site.x, site.y, site.z, 0));
    }

    private void sound(Site site, String kind) {
        pendingSounds.add(new SoundEmit(kind, site.x + 0.5, site.y + 0.5, site.z + 0.5,
                Blocks.TRIAL_SPAWNER, Float.NaN));
    }

    /** 실체화된 소환 하나를 명단에 새긴다. {@code maxHealth} 는 실제 몹의 최대 체력이다. */
    public boolean join(long trialId, long mobId, int wave, double maxHealth) {
        return ledger.join(trialId, mobId, wave, maxHealth);
    }

    /** 살아 있는 명단원의 현재 체력. 청크가 내려간 멤버는 관찰하지 않는다. */
    public void observeMember(long mobId, double health) {
        ledger.observeMember(mobId, health);
    }

    /** 죽거나 사라진 명단원. 이 자리가 곧 승리 판정의 입력이다. */
    public void retireMember(long mobId) {
        ledger.retireMember(mobId);
    }

    public Long trialIdOfMember(long mobId) {
        return ledger.raidIdOfMember(mobId);
    }

    /** 명단원 하나의 관찰 결과. {@code null} 은 "권위가 더 이상 들고 있지 않다"(사망/제거) 다. */
    public interface MemberObservation {
        Double health(long mobId);

        /** 명단원의 발 좌표 {x, y, z}. 모르면 null(추적 거리 판정을 하지 않는다). */
        default double[] position(long mobId) { return null; }
    }

    /**
     * 명단을 권위와 다시 맞춘다. 청크만 내려간 멤버는 여전히 권위가 들고 있으므로 은퇴하지
     * 않는다 — 언로드가 처치로 오인되면 시련이 저절로 끝난다(레이드 원장의 같은 규칙이다).
     * 바닐라 {@code shouldMobBeUntracked}: 블록 좌표가 스포너에서 47 블록을 넘은 몹은 명단에서만 빠진다.
     */
    public void reconcileMembers(MemberObservation observation) {
        for (RaidLedger.Instance instance : List.copyOf(ledger.instances())) {
            Site site = siteByTrialId(instance.raidId());
            for (Long memberId : instance.memberIds()) {
                Double health = observation.health(memberId);
                if (health == null) {
                    ledger.retireMember(memberId);
                    continue;
                }
                double[] position = site == null ? null : observation.position(memberId);
                if (position != null) {
                    long dx = (long) Math.floor(position[0]) - site.x;
                    long dy = (long) Math.floor(position[1]) - site.y;
                    long dz = (long) Math.floor(position[2]) - site.z;
                    if (dx * dx + dy * dy + dz * dz > MAX_MOB_TRACKING_DISTANCE_SQR) {
                        ledger.retireMember(memberId);
                        continue;
                    }
                }
                ledger.observeMember(memberId, health);
            }
        }
    }

    /**
     * ACTIVE 자리의 원장을 한 틱 진행한다. 명단 관찰/은퇴가 끝난 뒤에 불러야 한다.
     *
     * @param blockPresent 스포너 블록이 아직 그 자리에 있는가(anchor 존재). 청크가 틱 중이
     *     아닐 때는 묻지 않는다 — 관찰할 수 없는 것을 부재로 읽으면 안 된다.
     * @param chunkTicking 그 청크가 이번 틱에 시뮬레이션 중인가(anchor 틱)
     */
    public void advance(long worldTick, BlockPresence blockPresent, ChunkTicking chunkTicking) {
        for (Map.Entry<Long, Site> entry : List.copyOf(sites.entrySet())) {
            Site site = entry.getValue();
            boolean ticking = chunkTicking.ticking(
                    Math.floorDiv(site.x, 16), Math.floorDiv(site.z, 16));
            RaidLedger.Instance instance = ledger.instance(site.trialId);
            if (site.state != TrialSpawnerContract.State.ACTIVE || instance == null) {
                // A completed/cooldown site still owns runtime state. If the actual block is broken
                // while its chunk is observable, discard that state so a later replacement does
                // not inherit the old cooldown forever. An unloaded chunk remains unknown.
                if (ticking && !blockPresent.present(site.x, site.y, site.z)) {
                    ledger.forget(site.trialId);
                    discardPendingFixtureState(site.x, site.y, site.z, Blocks.TRIAL_SPAWNER);
                    sites.remove(entry.getKey());
                }
                continue;
            }
            // 틱 중인 청크에서만 블록 부재를 사실로 인정한다. 비활성 청크의 블록 조회는
            // 음수 sentinel 이라 그대로 믿으면 멀어진 플레이어가 남의 시련을 LOSS 로 끝낸다.
            boolean present = !ticking || blockPresent.present(site.x, site.y, site.z);
            // [TRIAL-GAP] 원장의 활성 시간(레이드 48000 MC 틱 중단)은 세지 않는다 — 바닐라 트라이얼
            // 스포너는 명단이 빌 때까지 ACTIVE 이고 시간 상한이 없다(TrialSpawnerState.tickAndGetNext).
            RaidLedger.Transition transition =
                    ledger.advance(site.trialId, worldTick, present, false);
            switch (transition) {
                case VICTORY -> {
                    // 바닐라: cooldownEndsAt = gameTime + targetCooldownLength, 소환 수 0.
                    site.heroNickname = heroOf(site.trialId, site.heroNickname);
                    site.cooldownUntilTick = worldTick + COOLDOWN_TICKS;
                    site.nextSpawnTick = 0;
                    site.lastLiveMembers = 0;
                    site.ejectionsRemaining = Math.max(1, site.detected.size());
                    site.ejectedCount = 0;
                    site.activatedVaults = List.of();
                    site.state = TrialSpawnerContract.State.WAITING_FOR_REWARD_EJECTION;
                    publishState(site);
                    ledger.forget(site.trialId);
                }
                case LOSS, STOPPED -> {
                    // 스포너 블록이 사라졌다. 쿨다운 없이 대기로 되돌린다(바닐라 resetStatistics 와
                    // 같다). 불길함은 쿨다운이 끝날 때만 벗는다.
                    ledger.forget(site.trialId);
                    site.detected.clear();
                    site.nextSpawnTick = 0;
                    site.lastLiveMembers = 0;
                    if (!present) {
                        discardPendingFixtureState(site.x, site.y, site.z, Blocks.TRIAL_SPAWNER);
                        sites.remove(entry.getKey());
                    } else {
                        site.state = TrialSpawnerContract.State.WAITING_FOR_PLAYERS;
                        publishState(site);
                    }
                }
                default -> { }
            }
        }
    }

    /** 스포너 블록이 아직 있는가. */
    public interface BlockPresence {
        boolean present(int x, int y, int z);
    }

    /** 그 청크가 이번 틱 시뮬레이션 대상인가. */
    public interface ChunkTicking {
        boolean ticking(int chunkX, int chunkZ);
    }

    // ── 금고(바닐라 VaultBlockEntity.Server) ─────────────────────────────────────

    /** 이번 틱의 금고 상태 기계. 틱 중인 청크의 금고만 돈다(블록 개체 틱과 같다). */
    private void tickVaults(MobWorldView world, long worldTick) {
        long nowMc = Math.multiplyExact(worldTick, 2L);
        for (int[] position : vaultBlocks) {
            if (position == null || position.length < 3) continue;
            int x = position[0], y = position[1], z = position[2];
            if (!world.isChunkActive(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            if (world.getBlock(x, y, z) != (short) Blocks.VAULT) continue;
            Vault vault = vaults.get(positionKey(x, y, z));
            if (vault == null) continue;
            if (nowMc < vault.resumesAtMc) continue;
            int next = vaultTickAndGetNext(world, vault, nowMc);
            if (next != vault.state) setVaultState(vault, next);
        }
    }

    /** 바닐라 {@code VaultState.tickAndGetNext}. */
    private int vaultTickAndGetNext(MobWorldView world, Vault vault, long nowMc) {
        int inactive = TrialVaultContract.State.INACTIVE.stateBits();
        int active = TrialVaultContract.State.ACTIVE.stateBits();
        int unlocking = TrialVaultContract.State.UNLOCKING.stateBits();
        if (vault.state == inactive) {
            return updateConnected(world, vault, nowMc, VAULT_ACTIVATION_RANGE);
        }
        if (vault.state == active) {
            return updateConnected(world, vault, nowMc, VAULT_DEACTIVATION_RANGE);
        }
        if (vault.state == unlocking) {
            vault.resumesAtMc = nowMc + VAULT_UPDATE_PAUSE_MC;
            return TrialVaultContract.State.EJECTING.stateBits();
        }
        // EJECTING
        if (vault.items.isEmpty()) {
            vault.totalEjectionsNeeded = 0;
            return updateConnected(world, vault, nowMc, VAULT_DEACTIVATION_RANGE);
        }
        float progress = vault.totalEjectionsNeeded == 1 ? 1.0F
                : 1.0F - ((float) vault.items.size() - 1.0F)
                        / ((float) vault.totalEjectionsNeeded - 1.0F);
        VaultItem item = vault.items.remove(vault.items.size() - 1);
        pendingVaultEjections.add(new VaultEjection(vault.vaultId, vault.x, vault.y, vault.z,
                item.token(), 0L, item.stack()));
        pendingLevelEvents.add(new LevelEvent(EVENT_VAULT_EJECT_ITEM,
                vault.x, vault.y, vault.z, 0));
        pendingSounds.add(new SoundEmit("vault_eject_item", vault.x + 0.5, vault.y + 0.5,
                vault.z + 0.5, Blocks.VAULT, 0.8F + 0.4F * progress));
        vault.resumesAtMc = nowMc + VAULT_UPDATE_PAUSE_MC;
        return TrialVaultContract.State.EJECTING.stateBits();
    }

    /**
     * 바닐라 {@code updateStateForConnectedPlayers}: 범위 안(블록 좌표 거리 제곱 &lt; range²,
     * {@code INCLUDING_CREATIVE_PLAYERS} · 시선 없음)에서 아직 보상받지 않은 플레이어가 곧
     * {@code connectedPlayers} 이고, 20 MC 틱을 쉰 뒤 그 유무로 ACTIVE/INACTIVE 를 정한다.
     */
    private int updateConnected(MobWorldView world, Vault vault, long nowMc, double range) {
        updateConnectedPlayers(world, vault, range);
        vault.resumesAtMc = nowMc + VAULT_UPDATE_PAUSE_MC;
        return vault.connected.isEmpty() ? TrialVaultContract.State.INACTIVE.stateBits()
                : TrialVaultContract.State.ACTIVE.stateBits();
    }

    private static void updateConnectedPlayers(MobWorldView world, Vault vault, double range) {
        vault.connected.clear();
        double rangeSquared = range * range;
        for (PlayerSnapshot player : world.players()) {
            long dx = (long) Math.floor(player.x()) - vault.x;
            long dy = (long) Math.floor(player.y()) - vault.y;
            long dz = (long) Math.floor(player.z()) - vault.z;
            if (dx * dx + dy * dy + dz * dz >= rangeSquared) continue;
            if (vault.rewarded.contains(player.nickname())) continue;
            vault.connected.add(player.nickname());
        }
    }

    /** 바닐라 {@code setVaultState} → {@code onTransition}(onExit 옛 상태, onEnter 새 상태). */
    private void setVaultState(Vault vault, int next) {
        int previous = vault.state;
        vault.state = next;
        int ominous = vault.ominous() ? 1 : 0;
        if (previous == TrialVaultContract.State.EJECTING.stateBits()) {
            vaultSound(vault, "vault_close_shutter", Float.NaN);
        }
        if (next == TrialVaultContract.State.INACTIVE.stateBits()) {
            pendingLevelEvents.add(new LevelEvent(EVENT_VAULT_DEACTIVATE,
                    vault.x, vault.y, vault.z, ominous));
        } else if (next == TrialVaultContract.State.ACTIVE.stateBits()) {
            pendingLevelEvents.add(new LevelEvent(EVENT_VAULT_ACTIVATE,
                    vault.x, vault.y, vault.z, ominous));
        } else if (next == TrialVaultContract.State.UNLOCKING.stateBits()) {
            vaultSound(vault, "vault_insert_item", Float.NaN);
        } else {
            vaultSound(vault, "vault_open_shutter", Float.NaN);
        }
        pendingStateChanges.add(new StateChange(vault.x, vault.y, vault.z, Blocks.VAULT,
                vault.shapeBits | next, vault.resumesAtMc));
    }

    private void vaultSound(Vault vault, String kind, float pitch) {
        pendingSounds.add(new SoundEmit(kind, vault.x + 0.5, vault.y + 0.5, vault.z + 0.5,
                Blocks.VAULT, pitch));
    }

    /**
     * 바닐라 {@code VaultBlock.useItemOn}: 손에 무언가를 든 채 ACTIVE 금고를 눌렀을 때만 삽입을
     * 시도한다. 열쇠 정산이 영속 lane 에 있는 동안은 UNLOCKING 과 같이 거절한다.
     */
    public boolean vaultActive(int x, int y, int z) {
        Vault vault = vaults.get(positionKey(x, y, z));
        return vault != null && !vault.unlockInFlight
                && vault.state == TrialVaultContract.State.ACTIVE.stateBits();
    }

    /** 금고가 불길한가(carrier 의 ominous 비트). 불길한 금고는 불길한 열쇠만 받는다. */
    public boolean vaultOminous(int x, int y, int z) {
        Vault vault = vaults.get(positionKey(x, y, z));
        return vault != null && vault.ominous();
    }

    /** 이 플레이어가 이미 이 금고의 보상을 받았는가({@code VaultServerData.hasRewardedPlayer}). */
    public boolean vaultRewarded(int x, int y, int z, String nickname) {
        Vault vault = vaults.get(positionKey(x, y, z));
        return vault != null && vault.rewarded.contains(nickname);
    }

    /**
     * 바닐라 {@code playInsertFailSound}: 마지막 실패 소리 뒤 15 MC 틱이 지났을 때만
     * {@code kind}(vault_insert_item_fail · vault_reject_rewarded_player)를 낸다.
     */
    public boolean vaultInsertFail(int x, int y, int z, String kind) {
        Vault vault = vaults.get(positionKey(x, y, z));
        if (vault == null) return false;
        long nowMc = Math.multiplyExact(lastWorldTick, 2L);
        if (nowMc < vault.lastInsertFailMc + VAULT_INSERT_FAIL_SOUND_BUFFER_MC) return false;
        vaultSound(vault, kind, Float.NaN);
        vault.lastInsertFailMc = nowMc;
        return true;
    }

    /** 열쇠 정산을 영속 lane 에 올린다. ACTIVE 금고만 받는다. */
    public boolean beginVaultUnlock(int x, int y, int z) {
        Vault vault = vaults.get(positionKey(x, y, z));
        if (vault == null || vault.unlockInFlight
                || vault.state != TrialVaultContract.State.ACTIVE.stateBits()) return false;
        vault.unlockInFlight = true;
        return true;
    }

    /** 영수증이 이미 GRANTED 인 플레이어(이 변경 전의 저장)를 보상 받은 집합에 더한다. */
    public void markVaultRewarded(int x, int y, int z, String nickname) {
        Vault vault = vaults.get(positionKey(x, y, z));
        if (vault != null) vault.rewarded.add(nickname);
    }

    /** 정산이 거절·실패했다. 금고는 그대로 ACTIVE 다. */
    public void abortVaultUnlock(int x, int y, int z) {
        Vault vault = vaults.get(positionKey(x, y, z));
        if (vault != null) vault.unlockInFlight = false;
    }

    /**
     * 정산이 커밋됐다(열쇠 소비 · 영수증 GRANTED · 배출 outbox 가 한 트랜잭션). 바닐라 {@code unlock}:
     * 배출 목록을 넣고 14 MC 틱을 쉰 뒤 UNLOCKING, 보상 받은 집합에 더하고 연결 플레이어를 비활성
     * 반경으로 다시 센다.
     */
    public void completeVaultUnlock(MobWorldView world, int x, int y, int z, String nickname,
            List<VaultItem> items) {
        Vault vault = vaults.get(positionKey(x, y, z));
        if (vault == null) return;
        vault.unlockInFlight = false;
        vault.items.clear();
        vault.items.addAll(items);
        vault.totalEjectionsNeeded = vault.items.size();
        vault.resumesAtMc = Math.multiplyExact(lastWorldTick, 2L) + VAULT_UNLOCKING_DELAY_MC;
        setVaultState(vault, TrialVaultContract.State.UNLOCKING.stateBits());
        vault.rewarded.add(nickname);
        if (world != null) updateConnectedPlayers(world, vault, VAULT_DEACTIVATION_RANGE);
    }

    /**
     * 복원: 금고별 보상 받은 닉네임(영수증 GRANTED 행)과 아직 배출하지 않은 전리품(outbox 행).
     * 금고 개체는 청크가 관찰될 때 만들어지므로 그때까지 보관한다.
     */
    /**
     * [TRIAL-GAP] 영속된 금고별 {@code stateUpdatingResumesAt}(MC 틱). 금고 개체가 청크 관찰로 만들어질 때
     * 그 좌표의 값을 넣는다(바닐라는 블록 개체 NBT 로 되읽는다).
     */
    public void restoreVaultTimers(Map<Long, Long> resumesAtByPosition) {
        if (resumesAtByPosition == null) return;
        for (Map.Entry<Long, Long> entry : resumesAtByPosition.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            restoredVaultTimers.put(entry.getKey(), entry.getValue());
            Vault live = vaults.get(entry.getKey());
            if (live != null) live.resumesAtMc = entry.getValue();
        }
    }

    public void restoreVaults(Map<Long, Set<String>> rewarded, Map<Long, List<VaultItem>> items) {
        if (rewarded != null) {
            for (Map.Entry<Long, Set<String>> entry : rewarded.entrySet()) {
                restoredVaultRewarded.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>())
                        .addAll(entry.getValue());
            }
        }
        if (items != null) {
            for (Map.Entry<Long, List<VaultItem>> entry : items.entrySet()) {
                restoredVaultItems.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                        .addAll(entry.getValue());
            }
        }
    }

    /** 확정됐지만 아직 durable 지면 정산을 받지 못한 금고 배출들. */
    public List<VaultEjection> pendingVaultEjections() {
        if (pendingVaultEjections.isEmpty()) return List.of();
        return List.copyOf(pendingVaultEjections);
    }

    /** 지면 정산 전에 아이템 개체 identity 를 묶는다. */
    public boolean bindVaultEjectionEntity(String token, long entityId) {
        if (entityId <= 0 || entityId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("vault ejection entity identity is required");
        }
        for (int index = 0; index < pendingVaultEjections.size(); index++) {
            VaultEjection pending = pendingVaultEjections.get(index);
            if (!pending.token().equals(token)) continue;
            if (pending.entityId() != 0 && pending.entityId() != entityId) {
                throw new IllegalStateException("vault ejection entity identity cannot change");
            }
            pendingVaultEjections.set(index, new VaultEjection(pending.vaultId(), pending.x(),
                    pending.y(), pending.z(), pending.token(), entityId, pending.stack()));
            return true;
        }
        return false;
    }

    /** 지면 정산이 커밋됐다(또는 이미 커밋돼 있었다). */
    public boolean acknowledgeVaultEjection(String token) {
        return pendingVaultEjections.removeIf(pending -> pending.token().equals(token));
    }

    /** 옛 호출부 호환: 금고 상태 비트를 직접 되돌린다(청크 재관찰 전 복원 테스트). */
    public void restoreVaultState(int x, int y, int z, int state) {
        Vault vault = vaults.get(positionKey(x, y, z));
        if (vault != null) vault.state = state & TrialVaultContract.STATE_MASK;
    }

    // ── 영속 · 가장자리 ─────────────────────────────────────────────────────────

    /**
     * 권위가 실제 블록에 써야 할 상태 비트 변화.
     *
     * <p>영속 transaction이 실패해도 다음 틱에 같은 배치를 재시도할 수 있게 조회와
     * 승인을 분리한다. 이 메서드는 현재 pending 배치의 불변 사본을 돌려준다.</p>
     */
    public List<StateChange> pendingStateChanges() {
        if (pendingStateChanges.isEmpty()) return List.of();
        return List.copyOf(pendingStateChanges);
    }

    /** 영속이 승인한 접두 배치만 제거한다. */
    public void acknowledgeStateChanges(List<StateChange> committed) {
        if (committed == null || committed.isEmpty()) return;
        if (committed.size() > pendingStateChanges.size()
                || !pendingStateChanges.subList(0, committed.size()).equals(committed)) {
            throw new IllegalStateException("trial state-change acknowledgement is not the pending prefix");
        }
        pendingStateChanges.subList(0, committed.size()).clear();
    }

    /** 확정됐지만 아직 durable 보상 승인을 받지 못한 배출들. */
    public List<Victory> pendingVictories() {
        if (pendingVictories.isEmpty()) return List.of();
        return List.copyOf(pendingVictories);
    }

    /** 이번 틱에 시련의 징조로 바꿔야 할 플레이어들. 소비하면 비워진다. */
    public List<OmenConversion> drainOmenConversions() {
        return drain(pendingOmenConversions);
    }

    /**
     * 불길해진 스포너가 치운 몹 id(바닐라 {@code resetAfterBecomingOminous} 의 {@code remove(
     * DISCARDED)}). 호출자가 실제 몹을 제거한다. 소비하면 비워진다.
     */
    public List<Long> drainDiscardedMembers() {
        return drain(pendingDiscardedMembers);
    }

    /** 이번 틱의 level event(입자·소리 폭발). 소비하면 비워진다. */
    public List<LevelEvent> drainLevelEvents() {
        return drain(pendingLevelEvents);
    }

    /** 이번 틱의 서버 playSound. 소비하면 비워진다. */
    public List<SoundEmit> drainSounds() {
        return drain(pendingSounds);
    }

    /** 이번 틱에 띄울 불길한 아이템 소환기. 소비하면 비워진다. */
    public List<ItemSpawnerRequest> drainItemSpawners() {
        return drain(pendingItemSpawners);
    }

    private static <T> List<T> drain(List<T> pending) {
        if (pending.isEmpty()) return List.of();
        List<T> drained = List.copyOf(pending);
        pending.clear();
        return drained;
    }

    public boolean hasPendingPersistenceEdges() {
        return !pendingStateChanges.isEmpty() || !pendingVictories.isEmpty();
    }

    /** Legacy-style one-shot helpers are retained only for pure rule tests. */
    public List<StateChange> drainStateChanges() {
        List<StateChange> drained = pendingStateChanges();
        acknowledgeStateChanges(drained);
        return drained;
    }

    public List<Victory> drainVictories() {
        List<Victory> drained = pendingVictories();
        pendingVictories.clear();
        return drained;
    }

    /** Exact pending reward acknowledgement after its durable item/vault settlement commits. */
    public boolean acknowledgeVictory(long trialId, String rewardIdentity) {
        Site site = siteByTrialId(trialId);
        if (site == null || !site.rewardPending
                || !java.util.Objects.equals(site.rewardIdentity, rewardIdentity)) return false;
        site.rewardPending = false;
        pendingVictories.removeIf(victory -> victory.trialId() == trialId
                && java.util.Objects.equals(victory.rewardIdentity(), rewardIdentity));
        return true;
    }

    /** Binds the item-system identity before the pending aggregate is first submitted. */
    public boolean bindVictoryRewardEntity(long trialId, String rewardIdentity, long entityId) {
        if (entityId <= 0 || entityId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("reward entity identity is required");
        }
        Site site = siteByTrialId(trialId);
        if (site == null || !site.rewardPending
                || !java.util.Objects.equals(site.rewardIdentity, rewardIdentity)) return false;
        if (site.rewardEntityId != 0 && site.rewardEntityId != entityId) {
            throw new IllegalStateException("trial reward entity identity cannot change");
        }
        site.rewardEntityId = entityId;
        for (int index = 0; index < pendingVictories.size(); index++) {
            Victory pending = pendingVictories.get(index);
            if (pending.trialId() == trialId
                    && java.util.Objects.equals(pending.rewardIdentity(), rewardIdentity)) {
                pendingVictories.set(index, new Victory(pending.trialId(), pending.x(), pending.y(),
                        pending.z(), pending.heroNickname(), pending.rewardIdentity(), entityId,
                        pending.vaults(), pending.itemType(), pending.count()));
            }
        }
        return true;
    }

    /** Snapshot for the persistence lane; callers persist after every accepted state mutation. */
    public List<SiteSnapshot> persistentSnapshots() {
        Map<Long, RaidLedger.InstanceSnapshot> ledgers = new LinkedHashMap<>();
        for (RaidLedger.InstanceSnapshot snapshot : ledger.snapshot()) {
            ledgers.put(snapshot.raidId(), snapshot);
        }
        List<SiteSnapshot> snapshots = new ArrayList<>(sites.size());
        for (Site site : sites.values()) {
            snapshots.add(new SiteSnapshot(site.x, site.y, site.z, site.trialId,
                    site.armedTick, site.detected.size(),
                    site.state == TrialSpawnerContract.State.ACTIVE, site.cooldownUntilTick,
                    site.lastPublishedState, ledgers.get(site.trialId), site.heroNickname,
                    site.rewardIdentity, site.rewardPending, site.rewardEntityId,
                    site.activatedVaults, site.state.stateBits(), site.ominous,
                    site.ejectionsRemaining, site.ejectedCount, site.nextSpawnTick,
                    site.rewardPending ? site.rewardItemType : 0,
                    site.rewardPending ? site.rewardCount : 0,
                    List.copyOf(site.detected)));
        }
        return List.copyOf(snapshots);
    }

    /** Hydrates one world before fixture observation; malformed or mismatched identities fail fast. */
    public void restorePersistentSnapshots(int worldSeed, List<SiteSnapshot> snapshots) {
        if (!sites.isEmpty() || !ledger.instances().isEmpty()) {
            throw new IllegalStateException("trial runtime already initialized");
        }
        List<RaidLedger.InstanceSnapshot> ledgerRows = new ArrayList<>();
        java.util.Set<Long> restoredTrialIds = new java.util.HashSet<>();
        java.util.Set<Long> restoredPositions = new java.util.HashSet<>();
        if (snapshots == null) return;
        for (SiteSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.trialId() != TrialSpawnerContract.trialId(
                    worldSeed, snapshot.x(), snapshot.y(), snapshot.z())) {
                throw new IllegalStateException("invalid persisted trial identity");
            }
            long position = positionKey(snapshot.x(), snapshot.y(), snapshot.z());
            if (!restoredTrialIds.add(snapshot.trialId()) || !restoredPositions.add(position)) {
                throw new IllegalStateException("duplicate persisted trial identity");
            }
            Site site = new Site(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.trialId());
            site.armedTick = snapshot.armedTick();
            restoreDetected(site, snapshot.detectedNicknames(), snapshot.detectedPlayers());
            site.cooldownUntilTick = snapshot.cooldownUntilTick();
            site.lastPublishedState = snapshot.phaseState();
            site.heroNickname = snapshot.heroNickname();
            site.rewardIdentity = snapshot.rewardIdentity();
            site.rewardPending = snapshot.rewardPending();
            site.rewardEntityId = snapshot.rewardEntityId();
            site.rewardItemType = snapshot.rewardItemType();
            site.rewardCount = snapshot.rewardCount();
            site.activatedVaults = copyPositions(snapshot.activatedVaults());
            if (snapshot.trialState() == LEGACY_STATE) {
                migrateLegacy(site, snapshot);
            } else {
                site.state = TrialSpawnerContract.State.fromBits(snapshot.trialState());
                site.ominous = snapshot.ominous();
                site.ejectionsRemaining = snapshot.ejectionsRemaining();
                site.ejectedCount = snapshot.ejectedCount();
                site.nextSpawnTick = snapshot.nextSpawnTick();
            }
            sites.put(position, site);
            if (snapshot.ledger() != null) ledgerRows.add(snapshot.ledger());
            if (site.rewardPending) {
                pendingVictories.add(new Victory(site.trialId, site.x, site.y, site.z,
                        site.heroNickname, site.rewardIdentity, site.rewardEntityId,
                        List.of(), site.rewardItemType, site.rewardCount));
            }
        }
        ledger.restore(ledgerRows);
    }

    /**
     * 감지 집합 복원. 닉네임 칸이 없던 옛 행은 인원수만 들고 있으므로, 추가 인원·배출 횟수가 같은
     * 값을 내도록 실제 닉네임과 겹칠 수 없는 자리표({@code "#legacy-N"})로 채운다.
     */
    private static void restoreDetected(Site site, List<String> nicknames, int count) {
        site.detected.clear();
        site.detected.addAll(nicknames);
        for (int index = 0; site.detected.size() < count; index++) {
            site.detected.add("#legacy-" + index);
        }
    }

    /**
     * 옛 4-상태 행의 해석. 옛 계약은 승리 순간에 열쇠를 배출하고 쿨다운 내내 코드 2 를 그렸으므로
     * 무장 행은 ACTIVE, 쿨다운 기록이 있는 행은 COOLDOWN(배출은 이미 끝났다), 나머지는 대기다.
     * 옛 보상 행은 트라이얼 열쇠 1개(생성자 기본값)이고 identity 도 옛 형식 그대로다.
     */
    private static void migrateLegacy(Site site, SiteSnapshot snapshot) {
        site.ominous = false;
        site.ejectionsRemaining = 0;
        site.ejectedCount = 0;
        site.nextSpawnTick = 0;
        if (snapshot.armed()) {
            site.state = TrialSpawnerContract.State.ACTIVE;
        } else if (snapshot.cooldownUntilTick() > 0) {
            site.state = TrialSpawnerContract.State.COOLDOWN;
            site.detected.clear();
        } else {
            site.state = TrialSpawnerContract.State.WAITING_FOR_PLAYERS;
            site.detected.clear();
        }
    }

    /** 테스트·진단용: 한 자리의 현재 상태. 기록이 없으면 null. */
    public TrialSpawnerContract.State stateAt(int x, int y, int z) {
        Site site = sites.get(positionKey(x, y, z));
        return site == null ? null : site.state;
    }

    /** 테스트·진단용: 한 자리가 불길한가. */
    public boolean ominousAt(int x, int y, int z) {
        Site site = sites.get(positionKey(x, y, z));
        return site != null && site.ominous;
    }

    /** 테스트·진단용: 한 자리의 감지 집합(바닐라 {@code detectedPlayers}). */
    public List<String> detectedAt(int x, int y, int z) {
        Site site = sites.get(positionKey(x, y, z));
        return site == null ? List.of() : List.copyOf(site.detected);
    }

    /** 테스트·진단용: 금고 상태 비트. 기록이 없으면 −1. */
    public int vaultStateAt(int x, int y, int z) {
        Vault vault = vaults.get(positionKey(x, y, z));
        return vault == null ? -1 : vault.state;
    }

    private String heroOf(long trialId, String fallback) {
        RaidLedger.Instance instance = ledger.instance(trialId);
        return instance == null || instance.heroNickname() == null ? fallback : instance.heroNickname();
    }

    private Site siteByTrialId(long trialId) {
        for (Site site : sites.values()) if (site.trialId == trialId) return site;
        return null;
    }

    /** 첫 배출(ordinal 0)은 옛 승리 보상 identity 와 같은 형식이다. */
    public static String rewardIdentity(long trialId, long armedTick) {
        return "trial_reward_v1:" + Long.toUnsignedString(trialId, 16) + ":" + armedTick;
    }

    /** 배출 ordinal 의 보상 identity. ordinal 0 은 {@link #rewardIdentity(long, long)} 다. */
    public static String rewardIdentity(long trialId, long armedTick, int ordinal) {
        String base = rewardIdentity(trialId, armedTick);
        return ordinal == 0 ? base : base + ":" + ordinal;
    }

    private static List<int[]> copyPositions(List<int[]> positions) {
        if (positions == null || positions.isEmpty()) return List.of();
        List<int[]> copy = new ArrayList<>(positions.size());
        for (int[] position : positions) {
            if (position == null || position.length != 3) {
                throw new IllegalArgumentException("trial vault position must have three coordinates");
            }
            copy.add(position.clone());
        }
        return List.copyOf(copy);
    }

    private void publishState(Site site) {
        int bits = site.blockBits();
        if (site.lastPublishedState == bits) return;
        site.lastPublishedState = bits;
        pendingStateChanges.add(
                new StateChange(site.x, site.y, site.z, Blocks.TRIAL_SPAWNER, bits));
    }

    private void discardPendingFixtureState(int x, int y, int z, int blockType) {
        pendingStateChanges.removeIf(change -> change.x() == x && change.y() == y
                && change.z() == z && change.blockType() == blockType);
    }

    /**
     * 바닐라 {@code PlayerDetector.NO_CREATIVE_PLAYERS}: {@code player.blockPosition().closerThan(pos,
     * range)}(두 블록 좌표의 정수 거리 제곱 &lt; range²)이고 관전자가 아니며, {@code requireLineOfSight}
     * 면 눈에서 스포너 중심으로 쏜 VISUAL 선분이 스포너 칸에 먼저 닿거나 아무것도 치지 않아야 한다.
     * 바닐라 {@code ServerLevel.getPlayers} 는 죽은 플레이어도 돌려주므로 생존 여부를 묻지 않는다.
     */
    private static List<PlayerSnapshot> detect(MobWorldView world, int x, int y, int z,
            boolean requireLineOfSight) {
        double rangeSquared = TrialSpawnerContract.ACTIVATION_RANGE
                * TrialSpawnerContract.ACTIVATION_RANGE;
        List<PlayerSnapshot> found = null;
        for (PlayerSnapshot player : world.players()) {
            long dx = (long) Math.floor(player.x()) - x;
            long dy = (long) Math.floor(player.y()) - y;
            long dz = (long) Math.floor(player.z()) - z;
            if (dx * dx + dy * dy + dz * dz >= rangeSquared) continue;
            if (requireLineOfSight && !TrialVisualClip.inLineOfSight(world,
                    player.x(), player.y() + PlayerInteractionRules.eyeHeight(player.crouching()),
                    player.z(), x + 0.5, y + 0.5, z + 0.5)) continue;
            if (found == null) found = new ArrayList<>(2);
            found.add(player);
        }
        return found == null ? List.of() : found;
    }

    private static String nearestPlayerNickname(List<PlayerSnapshot> players, int x, int y, int z) {
        String nearest = null;
        double best = Double.MAX_VALUE;
        for (PlayerSnapshot player : players) {
            double distance = distanceSquared(player.x(), player.y(), player.z(),
                    x + 0.5, y, z + 0.5);
            if (distance >= best) continue;
            best = distance;
            nearest = player.nickname();
        }
        return nearest;
    }

    private static double distanceSquared(double ax, double ay, double az,
            double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 계약이 지형을 모르므로 여기서 실제로 몹을 받을 수 있는 y 를 찾는다. 고체 바닥·머리
     * 공간·유체 없음은 레이드 소환과 같은 판정이다. 크기는 실제 소환 몹의 것이다(슬라임 크기 포함).
     */
    private static Integer resolveSpawnY(MobWorldView world, double width, double height,
            int x, int y, int z) {
        for (int offset = VERTICAL_SEARCH_MIN; offset <= VERTICAL_SEARCH_MAX; offset++) {
            int candidate = y + offset;
            if (validSpawnCell(world, width, height, x + 0.5, candidate, z + 0.5)) return candidate;
        }
        return null;
    }

    private static boolean validSpawnCell(MobWorldView world, double width, double height,
            double x, int y, double z) {
        if (y <= Blocks.MIN_Y || y + height >= Blocks.MAX_Y) return false;
        double half = width * 0.5;
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half - 1e-9);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half - 1e-9);
        int maxY = (int) Math.ceil(y + height) - 1;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                short support = world.getBlock(bx, y - 1, bz);
                if (support < 0 || !world.isSolid(support)) return false;
                for (int by = y; by <= maxY; by++) {
                    short block = world.getBlock(bx, by, bz);
                    if (block < 0 || world.isSolid(block) || Fluids.isFluid(block)) return false;
                }
            }
        }
        return true;
    }

    /**
     * 역할 서수 → 실제 몹 종. {@link TrialSpawnerPool.Role} 의 표가 정본이고 이 사상은
     * 그 표를 실제 등록 종에 잇기만 한다 — 분기를 늘리지 않고 행만 늘린다.
     */
    public static MobType roleType(TrialSpawnerPool.Role role) {
        return switch (role) {
            case ZOMBIE -> MobType.ZOMBIE;
            case SKELETON -> MobType.SKELETON;
            case SPIDER -> MobType.SPIDER;
            case CAVE_SPIDER -> MobType.CAVE_SPIDER;
            case HUSK -> MobType.HUSK;
            case STRAY -> MobType.STRAY;
            case SLIME -> MobType.SLIME;
            case BREEZE -> MobType.BREEZE;
        };
    }

    /** 좌표 하나를 원장 identity 로 쓸 수 있는 64비트 키로 접는다. */
    public static long positionKey(int x, int y, int z) {
        return ((long) (x & 0x3FF_FFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FF_FFFFL);
    }
}
