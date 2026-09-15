package com.gameexpert.mob.service;

import com.gameexpert.engine.raid.RaidLedger;
import com.gameexpert.engine.raid.RaidRewardReceipt;
import com.gameexpert.engine.raid.RaidVictoryReward;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.entity.WorldMob;
import com.gameexpert.mob.entity.WorldPopulatedChunk;
import com.gameexpert.mob.dto.StructureOccupantClaimSnapshot;
import com.gameexpert.mob.dto.VillagerJobClaimSnapshot;
import com.gameexpert.mob.dto.VillagerTradeSnapshot;
import com.gameexpert.mob.entity.WorldStructureOccupantClaim;
import com.gameexpert.mob.entity.WorldVillagerJobClaim;
import com.gameexpert.mob.entity.WorldVillagerTradeState;
import com.gameexpert.mob.repository.WorldMobRepository;
import com.gameexpert.mob.repository.WorldPopulatedChunkRepository;
import com.gameexpert.mob.repository.WorldStructureOccupantClaimRepository;
import com.gameexpert.mob.repository.WorldVillagerJobClaimRepository;
import com.gameexpert.mob.repository.WorldVillagerTradeStateRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import com.gameexpert.mob.dto.RewardDeliverySnapshot;
import com.gameexpert.mob.dto.RewardPlayerSnapshot;
import com.gameexpert.mob.dto.RewardSettlement;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.gameexpert.engine.mob.origin.persistence.BrimstoneSitePersistenceService;

/** 몹 런타임 스냅샷과 JPA 행 사이의 얇은 변환 계층입니다. */
@Service
public class MobPersistenceService {

    private final WorldMobRepository repository;
    private final WorldPopulatedChunkRepository populatedChunkRepository;
    private final WorldStructureOccupantClaimRepository structureOccupantClaimRepository;
    /** Null in unit contexts that only exercise the mob aggregate. */
    private final RaidPersistenceService raidPersistence;
    /** Null in slice tests that do not raise the villager job lane repository. */
    private final WorldVillagerJobClaimRepository villagerJobClaimRepository;
    /** Null in slice tests that do not raise the villager trade lane repository. */
    private final WorldVillagerTradeStateRepository villagerTradeStateRepository;
    private final Map<Long, java.util.function.UnaryOperator<List<MobPersistenceSnapshot>>>
            snapshotRetentions = new ConcurrentHashMap<>();

    /** Optional spatial plugin protects committed rows until their owner publication fence passes. */
    public void registerSnapshotRetention(long worldId,
            java.util.function.UnaryOperator<List<MobPersistenceSnapshot>> retention) {
        snapshotRetentions.put(worldId, java.util.Objects.requireNonNull(retention));
    }

    private final Map<Long, List<MobPersistenceSnapshot>> savedSnapshots = new ConcurrentHashMap<>();
    private final Map<Long, List<MobLoadQuarantine>> loadQuarantines = new ConcurrentHashMap<>();
    private PlayerWorldStateService playerStates;
    private BrimstoneSitePersistenceService brimstoneSites;

    @Autowired
    void setPlayerWorldStateService(PlayerWorldStateService playerStates) {
        this.playerStates = playerStates;
    }

    @Autowired(required = false)
    void setBrimstoneSitePersistenceService(BrimstoneSitePersistenceService service) {
        this.brimstoneSites = service;
    }

    public BrimstoneSitePersistenceService brimstoneSites() {
        if (brimstoneSites == null) {
            throw new IllegalStateException("Brimstone site persistence is required");
        }
        return brimstoneSites;
    }

    @Transactional
    public void deleteBrimstoneWorld(Long worldId) {
        brimstoneSites().deleteWorld(worldId);
    }

    public enum NameTagOutcome { COMMITTED, STALE }

    @Transactional
    public NameTagOutcome settleNameTag(long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot player, MobPersistenceSnapshot mob, String name) {
        if (playerStates == null || player == null || mob == null || name == null || name.isBlank()
                || player.revision() != Math.addExact(expectedPlayerRevision, 1)) {
            throw new IllegalArgumentException("complete name-tag settlement is required");
        }
        long persisted = playerStates.lockInventoryPersistenceRevisionJoiningTransaction(
                player.playerId(), player.worldId());
        if (persisted != expectedPlayerRevision) return NameTagOutcome.STALE;
        WorldMob row = repository.findLockedByWorldIdAndMobId(player.worldId(), mob.getMobId())
                .orElseGet(() -> new WorldMob(player.worldId(), mob));
        row.apply(mob);
        row.rename(name);
        repository.save(row);
        playerStates.replaceExactSnapshotJoiningTransaction(player);
        return NameTagOutcome.COMMITTED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceCargoSnapshotJoiningTransaction(Long worldId,
            InventoryMutationTarget.MobCargo snapshot) {
        requireMatchingCargoSnapshot(snapshot);
        WorldMob entity = repository.findLockedByWorldIdAndMobId(
                        worldId, snapshot.mob().getMobId())
                .orElseThrow(() -> new IllegalStateException("mob cargo target is not persisted"));
        if (!entity.applyCargoSnapshotIfNewer(snapshot.mob(), snapshot.revision())) {
            throw new StaleInventoryMutationException("mob " + snapshot.mob().getMobId());
        }
        repository.save(entity);
        // 일반 delta flush의 비교 기준은 커밋 뒤에만 바꾼다. 그렇지 않으면 rollback 뒤에도
        // 메모리 캐시가 DB보다 앞서 다음 정상 flush를 건너뛸 수 있다.
        rememberMobSnapshotAfterCommit(worldId, snapshot.mob());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceCargoSnapshotAtExpectedRevisionJoiningTransaction(Long worldId,
            InventoryMutationTarget.MobCargo snapshot, long expectedRevision) {
        requireMatchingHorseMenuSnapshot(snapshot);
        if (expectedRevision < 0L || expectedRevision == Long.MAX_VALUE
                || snapshot.revision() != expectedRevision + 1L) {
            throw new IllegalArgumentException("mob cargo revision must advance expected revision once");
        }
        WorldMob entity = repository.findLockedByWorldIdAndMobId(
                worldId, snapshot.mob().getMobId()).orElse(null);
        if (entity == null || entity.getHorseMenuPersistenceRevision() != expectedRevision) return false;
        if (!entity.applyHorseMenuSnapshotIfNext(snapshot.mob(), snapshot.revision())) return false;
        repository.save(entity);
        rememberMobSnapshotAfterCommit(worldId, snapshot.mob());
        return true;
    }

    /** A placed vehicle and its passenger row commit together; ordinary mob flush retains its cache fence. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertPlacedVehiclePassengerJoiningTransaction(Long worldId, MobPersistenceSnapshot snapshot) {
        com.gameexpert.engine.mob.MobRuntime.validateHorseInventoryPayload(snapshot);
        WorldMob entity = repository.findLockedByWorldIdAndMobId(worldId, snapshot.getMobId()).orElse(null);
        if (entity == null) entity = new WorldMob(worldId, snapshot);
        else entity.apply(snapshot);
        repository.save(entity);
        rememberMobSnapshotAfterCommit(worldId, snapshot);
    }

    private static void requireMatchingHorseMenuSnapshot(InventoryMutationTarget.MobCargo target) {
        if (target == null || target.mob() == null
                || target.revision() <= 0L || target.revision() >= Long.MAX_VALUE - 1L
                || target.revision() != target.mob().getHorseMenuPersistenceRevision()) {
            throw new IllegalArgumentException("horse menu revision does not match snapshot");
        }
        WorldMob.requireValidMobId(target.mob().getMobId());
        com.gameexpert.engine.mob.MobRuntime.validateHorseInventoryPayload(target.mob());
    }

    private static void requireMatchingCargoSnapshot(InventoryMutationTarget.MobCargo target) {
        if (target == null || target.mob() == null
                || target.revision() <= 0L || target.revision() == Long.MAX_VALUE
                || target.revision() != target.mob().getCargoPersistenceRevision()) {
            throw new IllegalArgumentException("mob cargo revision does not match snapshot");
        }
        WorldMob.requireValidMobId(target.mob().getMobId());
        com.gameexpert.engine.mob.MobRuntime.validateHorseInventoryPayload(target.mob());
    }

    private void rememberMobSnapshotAfterCommit(Long worldId, MobPersistenceSnapshot snapshot) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                savedSnapshots.computeIfPresent(worldId, (ignored, current) -> {
                    List<MobPersistenceSnapshot> next = new ArrayList<>(current);
                    next.removeIf(value -> value.getMobId() == snapshot.getMobId());
                    next.add(snapshot);
                    next.sort(Comparator.comparingLong(MobPersistenceSnapshot::getMobId));
                    return List.copyOf(next);
                });
            }
        });
    }

    public MobPersistenceService(WorldMobRepository repository,
            WorldPopulatedChunkRepository populatedChunkRepository,
            WorldStructureOccupantClaimRepository structureOccupantClaimRepository) {
        this(repository, populatedChunkRepository, structureOccupantClaimRepository,
                (RaidPersistenceService) null, null, null);
    }

    /**
     * JPA slice 테스트는 raid 저장소 빈을 올리지 않는다. {@link ObjectProvider} 로 받아 빈이
     * 없으면 몹 aggregate만 다루는 기존 동작을 그대로 유지한다.
     */
    public MobPersistenceService(WorldMobRepository repository,
            WorldPopulatedChunkRepository populatedChunkRepository,
            WorldStructureOccupantClaimRepository structureOccupantClaimRepository,
            ObjectProvider<RaidPersistenceService> raidPersistence) {
        this(repository, populatedChunkRepository, structureOccupantClaimRepository,
                raidPersistence.getIfAvailable(), null, null);
    }

    /** 주민 직업·거래 lane 저장소도 같은 이유로 선택 주입이다. */
    @Autowired
    public MobPersistenceService(WorldMobRepository repository,
            WorldPopulatedChunkRepository populatedChunkRepository,
            WorldStructureOccupantClaimRepository structureOccupantClaimRepository,
            ObjectProvider<RaidPersistenceService> raidPersistence,
            ObjectProvider<WorldVillagerJobClaimRepository> villagerJobClaimRepository,
            ObjectProvider<WorldVillagerTradeStateRepository> villagerTradeStateRepository) {
        this(repository, populatedChunkRepository, structureOccupantClaimRepository,
                raidPersistence.getIfAvailable(), villagerJobClaimRepository.getIfAvailable(),
                villagerTradeStateRepository.getIfAvailable());
    }

    private MobPersistenceService(WorldMobRepository repository,
            WorldPopulatedChunkRepository populatedChunkRepository,
            WorldStructureOccupantClaimRepository structureOccupantClaimRepository,
            RaidPersistenceService raidPersistence,
            WorldVillagerJobClaimRepository villagerJobClaimRepository,
            WorldVillagerTradeStateRepository villagerTradeStateRepository) {
        this.repository = repository;
        this.populatedChunkRepository = populatedChunkRepository;
        this.structureOccupantClaimRepository = structureOccupantClaimRepository;
        this.raidPersistence = raidPersistence;
        this.villagerJobClaimRepository = villagerJobClaimRepository;
        this.villagerTradeStateRepository = villagerTradeStateRepository;
    }

    /** 월드 활성화 때 저장 대상을 새 런타임에 넣을 수 있는 불변 스냅샷으로 읽습니다. */
    @Transactional(readOnly = true)
    public List<MobPersistenceSnapshot> loadWorld(Long worldId) {
        List<WorldMob> rows = new ArrayList<>(repository.findAllByWorldId(worldId));
        rows.sort(Comparator.comparingLong(WorldMob::getMobId));
        List<MobPersistenceSnapshot> accepted = new ArrayList<>(rows.size());
        List<MobLoadQuarantine> quarantined = new ArrayList<>();
        for (WorldMob row : rows) {
            try {
                accepted.add(row.toSnapshot());
            } catch (RuntimeException malformed) {
                quarantined.add(new MobLoadQuarantine(row.getMobId(),
                        malformed.getClass().getName(), String.valueOf(malformed.getMessage())));
            }
        }
        List<MobPersistenceSnapshot> storedSnapshots = List.copyOf(accepted);
        List<MobPersistenceSnapshot> liveSnapshots = storedSnapshots.stream()
                .filter(snapshot -> snapshot.getHealthPoints() > 1e-9)
                .toList();
        // 죽은 몹은 런타임 대상이 아니다. 저장 캐시에는 원본을 남겨 다음 flush가 stale 행을
        // 실제 DB에서도 제거하게 하며, 한 행 때문에 월드 전체 복원이 중단되지 않게 한다.
        savedSnapshots.put(worldId, storedSnapshots);
        loadQuarantines.put(worldId, List.copyOf(quarantined));
        return liveSnapshots;
    }

    public record MobLoadQuarantine(long mobId, String causeType, String message) { }

    /** Malformed rows remain untouched in storage; this immutable receipt preserves why. */
    public List<MobLoadQuarantine> loadQuarantine(Long worldId) {
        return loadQuarantines.getOrDefault(worldId, List.of());
    }

    /**
     * Highest identity observed during the latest {@link #loadWorld} call, including stale dead
     * rows which are intentionally not restored. The runtime must still reserve those numbers
     * until the next delta flush deletes the rows, otherwise a fresh mob can reuse a durable id.
     */
    public long loadedMobIdHighWater(Long worldId) {
        List<MobPersistenceSnapshot> snapshots = savedSnapshots.get(worldId);
        long accepted = snapshots == null || snapshots.isEmpty()
                ? 0L : snapshots.getLast().getMobId();
        List<MobLoadQuarantine> quarantined = loadQuarantines.get(worldId);
        long rejected = quarantined == null || quarantined.isEmpty()
                ? 0L : quarantined.getLast().mobId();
        return Math.max(accepted, rejected);
    }

    /**
     * 월드 삭제가 커밋된 뒤 마지막 스냅샷 캐시를 버립니다. 삭제 transaction이 rollback되면
     * 살아 있는 월드의 delta 기준도 그대로 남아야 하므로, 활성 transaction 안에서는
     * afterCommit까지 제거를 미룹니다.
     */
    public void forgetWorld(Long worldId) {
        if (worldId == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            snapshotRetentions.remove(worldId);
            savedSnapshots.remove(worldId);
            loadQuarantines.remove(worldId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                snapshotRetentions.remove(worldId);
                savedSnapshots.remove(worldId);
                loadQuarantines.remove(worldId);
            }
        });
    }

    /** 월드 활성화 때 이미 무리 배치를 판정한 청크 키(chunkKey 인코딩) 집합을 읽습니다. */
    @Transactional(readOnly = true)
    public List<long[]> loadPopulatedChunks(Long worldId) {
        return populatedChunkRepository.findAllByWorldId(worldId).stream()
                .map(row -> new long[] { row.getChunkX(), row.getChunkZ() })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StructureOccupantClaimSnapshot> loadStructureOccupantClaims(Long worldId) {
        return structureOccupantClaimRepository.findAllByWorldId(worldId).stream()
                .map(WorldStructureOccupantClaim::toSnapshot)
                .sorted(Comparator.comparing(StructureOccupantClaimSnapshot::identityKey))
                .toList();
    }

    /**
     * 월드 활성화 때 주민 직업 lane 행을 읽습니다. 해석할 수 없는 행은 건너뛰어 한 행 때문에
     * 월드 전체 복원이 멈추지 않게 합니다.
     */
    @Transactional(readOnly = true)
    public List<VillagerJobClaimSnapshot> loadVillagerJobClaims(Long worldId) {
        if (villagerJobClaimRepository == null) return List.of();
        List<VillagerJobClaimSnapshot> rows = new ArrayList<>();
        for (WorldVillagerJobClaim row : villagerJobClaimRepository.findAllByWorldId(worldId)) {
            VillagerJobClaimSnapshot snapshot = row.toSnapshot();
            if (snapshot != null) rows.add(snapshot);
        }
        rows.sort(Comparator.comparingLong(VillagerJobClaimSnapshot::mobId));
        return List.copyOf(rows);
    }

    /**
     * 월드 활성화 때 주민 거래 진행도 lane 행을 읽습니다. 스키마 버전이 다르거나 깨진 행은
     * 건너뜁니다 — 그 주민은 직업 lane 의 직업으로 오퍼를 새로 뽑습니다(기본값 폴백).
     */
    @Transactional(readOnly = true)
    public List<VillagerTradeSnapshot> loadVillagerTradeStates(Long worldId) {
        if (villagerTradeStateRepository == null) return List.of();
        List<VillagerTradeSnapshot> rows = new ArrayList<>();
        for (WorldVillagerTradeState row : villagerTradeStateRepository.findAllByWorldId(worldId)) {
            VillagerTradeSnapshot snapshot = row.toSnapshot();
            if (snapshot != null) rows.add(snapshot);
        }
        rows.sort(Comparator.comparingLong(VillagerTradeSnapshot::mobId));
        return List.copyOf(rows);
    }

    /** 직업 해제·첫 거래 잠금과 가변 직업/거래 진행도를 같은 커밋으로 보존한다. */
    @Transactional
    public void flushVillagerJobsAndTrades(Long worldId,
            Collection<VillagerJobClaimSnapshot> jobUpserts, Collection<Long> jobRemovals,
            Collection<VillagerTradeSnapshot> tradeUpserts, Collection<Long> tradeRemovals) {
        flushVillagerJobClaims(worldId, jobUpserts, jobRemovals);
        flushVillagerTradeStates(worldId, tradeUpserts, tradeRemovals);
    }

    /**
     * 주민 거래 lane 은 직업 lane 과 저장소가 분리돼 있고 셀 unique 제약이 없다. 삭제(주민 소멸)와
     * upsert 를 한 트랜잭션으로 커밋하며, 삭제를 먼저 적용해 같은 flush 에서 사라졌다 다시 생긴
     * id 가 두 행이 되지 않게 한다.
     */
    @Transactional
    public void flushVillagerTradeStates(Long worldId,
            Collection<VillagerTradeSnapshot> upserts, Collection<Long> removals) {
        if (villagerTradeStateRepository == null) return;
        if (removals != null && !removals.isEmpty()) {
            villagerTradeStateRepository.deleteByWorldIdAndMobIds(worldId, removals);
            villagerTradeStateRepository.flush();
        }
        if (upserts == null || upserts.isEmpty()) return;
        List<VillagerTradeSnapshot> sorted = upserts.stream()
                .sorted(Comparator.comparingLong(VillagerTradeSnapshot::mobId)).toList();
        for (VillagerTradeSnapshot snapshot : sorted) {
            WorldVillagerTradeState existing = villagerTradeStateRepository
                    .findByWorldIdAndMobId(worldId, snapshot.mobId()).orElse(null);
            if (existing == null) {
                villagerTradeStateRepository.save(new WorldVillagerTradeState(worldId, snapshot));
                continue;
            }
            existing.apply(snapshot);
            villagerTradeStateRepository.save(existing);
        }
    }

    /**
     * 주민 직업 lane 은 구조물 claim 과 달리 해제가 있으므로 upsert 와 삭제를 함께 커밋합니다.
     * 같은 트랜잭션 안에서 삭제를 먼저 적용해야 해제된 칸을 같은 flush 에서 다른 주민이
     * 점유하는 행이 unique 제약에 걸리지 않습니다.
     */
    @Transactional
    public void flushVillagerJobClaims(Long worldId,
            Collection<VillagerJobClaimSnapshot> upserts, Collection<Long> removals) {
        if (villagerJobClaimRepository == null) return;
        if (removals != null && !removals.isEmpty()) {
            villagerJobClaimRepository.deleteByWorldIdAndMobIds(worldId, removals);
        }
        if (upserts == null || upserts.isEmpty()) return;
        List<VillagerJobClaimSnapshot> sorted = upserts.stream()
                .sorted(Comparator.comparingLong(VillagerJobClaimSnapshot::mobId)).toList();
        List<WorldVillagerJobClaim> rows = new ArrayList<>(sorted.size());
        // 셀을 먼저 모두 비우고 flush 한 뒤 값을 넣는다. 한 flush 안에서 A가 떠난 칸을 B가
        // 점유하는 경우 쓰기 순서에 따라 unique 제약이 잠깐 깨질 수 있고, Hibernate 의 기본
        // 실행 순서(insert 먼저)로는 그 순간을 피할 수 없다.
        for (VillagerJobClaimSnapshot snapshot : sorted) {
            WorldVillagerJobClaim existing = villagerJobClaimRepository
                    .findByWorldIdAndMobId(worldId, snapshot.mobId()).orElse(null);
            if (existing == null) {
                rows.add(null);
                continue;
            }
            existing.clearSite();
            rows.add(villagerJobClaimRepository.save(existing));
        }
        villagerJobClaimRepository.flush();
        for (int index = 0; index < sorted.size(); index++) {
            VillagerJobClaimSnapshot snapshot = sorted.get(index);
            WorldVillagerJobClaim row = rows.get(index);
            if (row == null) {
                villagerJobClaimRepository.save(new WorldVillagerJobClaim(worldId, snapshot));
                continue;
            }
            row.apply(snapshot);
            villagerJobClaimRepository.save(row);
        }
    }

    private void persistPopulatedChunks(Long worldId, Collection<long[]> chunks) {
        for (long[] chunk : chunks) {
            int chunkX = (int) chunk[0];
            int chunkZ = (int) chunk[1];
            if (populatedChunkRepository.existsByWorldIdAndChunkXAndChunkZ(worldId, chunkX, chunkZ)) {
                continue;
            }
            populatedChunkRepository.save(new WorldPopulatedChunk(worldId, chunkX, chunkZ));
        }
    }

    /** 현재 런타임에 살아 있는 몹 전체를 월드 단위로 원자 교체합니다. */
    @Transactional
    public void flushWorld(Long worldId, Collection<MobPersistenceSnapshot> runtimeMobs) {
        flushWorld(worldId, runtimeMobs, List.of(), List.of());
    }

    /** 최초 배치 청크 표식과 그 결과인 몹 스냅샷을 한 트랜잭션으로 저장합니다. */
    @Transactional
    public void flushWorld(Long worldId, Collection<MobPersistenceSnapshot> runtimeMobs,
                           Collection<long[]> populatedChunks) {
        flushWorld(worldId, runtimeMobs, populatedChunks, List.of());
    }

    /** Population markers, structure claims and their persistent mobs commit as one unit. */
    @Transactional
    public void flushWorld(Long worldId, Collection<MobPersistenceSnapshot> runtimeMobs,
                           Collection<long[]> populatedChunks,
                           Collection<StructureOccupantClaimSnapshot> structureClaims) {
        flushWorld(worldId, runtimeMobs, populatedChunks, structureClaims, null, null);
    }

    /** 월드 활성화 때 레이드 원장을 읽습니다. 배선되지 않은 테스트 컨텍스트에서는 빈 목록입니다. */
    @Transactional(readOnly = true)
    public List<RaidLedger.InstanceSnapshot> loadRaidLedger(Long worldId) {
        return raidPersistence == null ? List.of() : raidPersistence.loadWorld(worldId);
    }

    /**
     * 월드 활성화 때 승리 receipt 를 읽습니다. 아직 {@code PENDING} 인 행은 지급이 끝나지 않은
     * 승리이며, 재기동한 런타임이 그 지급을 다시 시도할 유일한 근거입니다.
     */
    @Transactional(readOnly = true)
    public List<RaidRewardReceipt> loadRaidReceipts(Long worldId) {
        return raidPersistence == null ? List.of() : raidPersistence.loadReceipts(worldId);
    }

    /**
     * 승리 보상 청구를 한 번 시도합니다. receipt 의 {@code PENDING → GRANTED} 조건부 전이가
     * 중복 청구를 막으므로, 이 구 callback API는 persistence가 없는 엔진 단위 테스트 대역만
     * 위해 남아 있습니다. 제품 경로는 아래 `settleRaidVictoryPrize`의 receipt·delivery·플레이어
     * 원자 transaction을 쓹니다. raid 저장소가 없는 테스트 컨텍스트에서는
     * 소비할 receipt 자체가 없으므로 {@code UNKNOWN_RECEIPT} 입니다.
     */
    public RaidRewardReceipt.ClaimOutcome claimRaidVictoryPrize(Long worldId, long raidId,
            Predicate<RaidVictoryReward.Prize> award) {
        return raidPersistence == null
                ? RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT
                : raidPersistence.claimVictoryPrize(worldId, raidId, award);
    }

    /**
     * [TRIAL] 금고 개봉 한 번. 중복 청구 방지는 레이드와 같은 receipt 의
     * {@code PENDING → GRANTED} 조건부 전이가 소유하고, 토큰만 금고·닉네임의 것이다.
     * raid 저장소가 없는 테스트 컨텍스트에서는 소비할 receipt 자체가 없으므로
     * {@code UNKNOWN_RECEIPT} 입니다.
     */
    public RaidRewardReceipt.ClaimOutcome claimTrialVaultPrize(Long worldId,
            RaidRewardReceipt pending,
            Predicate<com.gameexpert.engine.trial.TrialVaultContract.Prize> award) {
        return raidPersistence == null
                ? RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT
                : raidPersistence.claimTrialVaultPrize(worldId, pending, award);
    }

    public RewardSettlement settleRaidVictoryPrize(
            Long worldId, long raidId, RewardPlayerSnapshot player) {
        if (raidPersistence != null) return raidPersistence.settleVictoryPrize(worldId, raidId, player);
        // Persistence가 없는 엔진 단위 테스트 더블은 기존 callback API를 재정의한다.
        RaidVictoryReward.Prize[] prize = new RaidVictoryReward.Prize[1];
        RaidRewardReceipt.ClaimOutcome outcome = claimRaidVictoryPrize(worldId, raidId, rolled -> {
            prize[0] = rolled;
            return true;
        });
        if (outcome != RaidRewardReceipt.ClaimOutcome.GRANTED || prize[0] == null) {
            return RewardSettlement.outcome(outcome);
        }
        com.gameexpert.engine.inventory.PlayerInventory inventory = player.inventory();
        int inserted = prize[0].isNothing() ? 0 : inventory.addItem(
                prize[0].itemType(), prize[0].count(), prize[0].durability(),
                prize[0].enchantments());
        return new RewardSettlement(outcome, prize[0].itemType(), inserted,
                prize[0].durability(), prize[0].enchantments(),
                prize[0].count() - inserted, null,
                inserted > 0 ? RewardSettlement.InventoryCommitOutcome.COMMITTED
                        : RewardSettlement.InventoryCommitOutcome.UNCHANGED,
                inserted > 0 ? inventory.revision()
                        : RewardSettlement.NO_COMMITTED_INVENTORY_REVISION,
                inserted > 0 ? inventory.completePersistenceSnapshot() : null);
    }

    public RewardSettlement settleTrialVaultPrize(
            Long worldId, RaidRewardReceipt pending, RewardPlayerSnapshot player) {
        if (raidPersistence != null) return raidPersistence.settleTrialVaultPrize(worldId, pending, player);
        com.gameexpert.engine.trial.TrialVaultContract.Prize[] prize =
                new com.gameexpert.engine.trial.TrialVaultContract.Prize[1];
        RaidRewardReceipt.ClaimOutcome outcome = claimTrialVaultPrize(worldId, pending, rolled -> {
            prize[0] = rolled;
            return true;
        });
        if (outcome != RaidRewardReceipt.ClaimOutcome.GRANTED || prize[0] == null) {
            return RewardSettlement.outcome(outcome);
        }
        com.gameexpert.engine.inventory.PlayerInventory inventory = player.inventory();
        // 손 참조는 라이브 인벤토리에서 잡혔고 정산은 detached 사본 위에서 하므로 다시 묶는다.
        com.gameexpert.engine.inventory.PlayerInventory.HandRef keyHand =
                inventory.rebindHand(player.trialKeyHand());
        // [TRIAL] 일반 금고는 트라이얼 열쇠, 불길한 금고는 불길한 열쇠다. 어느 쪽인지는 틱 스레드가
        // 금고의 ominous 비트로 이미 가렸고, 여기서는 손에 든 그 열쇠 하나를 소비한다.
        if (keyHand == null || !com.gameexpert.engine.trial.TrialVaultContract.isVaultKey(
                inventory.stack(keyHand).itemType())
                || !inventory.consumeOne(keyHand, inventory.stack(keyHand).itemType())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        int inserted = prize[0].isNothing() ? 0 : inventory.addItem(
                prize[0].itemType(), prize[0].count(), prize[0].durability(),
                prize[0].enchantments());
        return new RewardSettlement(outcome, prize[0].itemType(), inserted,
                prize[0].durability(), prize[0].enchantments(),
                prize[0].count() - inserted, keyHand,
                RewardSettlement.InventoryCommitOutcome.COMMITTED, inventory.revision(),
                inventory.completePersistenceSnapshot());
    }

    /**
     * [TRIAL-GAP] 금고 열쇠 정산. 영속 raid 저장소가 없는 엔진 테스트 더블은 열쇠만 소비하고
     * GRANTED 를 돌려준다(배출 outbox 는 런타임이 직접 들고 있다).
     */
    public RewardSettlement settleTrialVaultUnlock(Long worldId, RaidRewardReceipt pending,
            RewardPlayerSnapshot player, boolean ominousVault) {
        if (raidPersistence != null) {
            return raidPersistence.settleTrialVaultUnlock(worldId, pending, player, ominousVault);
        }
        if (pending == null || !player.nickname().equals(pending.recipientNickname())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        com.gameexpert.engine.inventory.PlayerInventory inventory = player.inventory();
        com.gameexpert.engine.inventory.PlayerInventory.HandRef keyHand =
                inventory.rebindHand(player.trialKeyHand());
        if (keyHand == null || !com.gameexpert.engine.trial.TrialVaultContract.isVaultKey(
                inventory.stack(keyHand).itemType())
                || !inventory.consumeOne(keyHand, inventory.stack(keyHand).itemType())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        return new RewardSettlement(RaidRewardReceipt.ClaimOutcome.GRANTED, (short) 0, 0, 0, 0,
                0, keyHand, RewardSettlement.InventoryCommitOutcome.COMMITTED,
                inventory.revision(), inventory.completePersistenceSnapshot());
    }

    /** [TRIAL-GAP] 금고 배출 한 번의 지면 정산. 테스트 더블은 변이만 곧장 돌린다. */
    public RaidPersistenceService.VaultEjectionOutcome settleVaultEjection(Long worldId,
            long vaultId, String token, Runnable mutation) {
        if (raidPersistence != null) {
            return raidPersistence.settleVaultEjection(worldId, vaultId, token, mutation);
        }
        mutation.run();
        return RaidPersistenceService.VaultEjectionOutcome.COMMITTED;
    }

    /** [TRIAL-GAP] 재기동 뒤 금고의 보상 집합과 남은 배출. */
    public RaidPersistenceService.TrialVaultRestore loadTrialVaultState(Long worldId) {
        return raidPersistence == null
                ? new RaidPersistenceService.TrialVaultRestore(java.util.Map.of(), java.util.Map.of())
                : raidPersistence.loadTrialVaultState(worldId);
    }

    public RewardSettlement settlePendingRewardDelivery(
            Long worldId, RewardDeliverySnapshot delivery, RewardPlayerSnapshot player) {
        return raidPersistence == null
                ? RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT)
                : raidPersistence.settlePendingDelivery(worldId, delivery, player);
    }

    public List<RewardDeliverySnapshot> loadPendingRewardDeliveries(Long worldId) {
        return raidPersistence == null ? List.of() : raidPersistence.loadPendingDeliveries(worldId);
    }

    /**
     * 몹 aggregate, 최초 배치 표식, 구조물 claim, 레이드 원장과 승리 receipt 를 한 트랜잭션으로
     * 커밋합니다. 승리한 틱의 멤버 상태와 그 승리가 만든 receipt 가 함께 커밋되어야 크래시가
     * 지급 근거만 남기거나 근거 없이 상태만 남기는 반쪽 저장이 생기지 않습니다.
     */
    @Transactional
    public void flushWorld(Long worldId, Collection<MobPersistenceSnapshot> runtimeMobs,
                           Collection<long[]> populatedChunks,
                           Collection<StructureOccupantClaimSnapshot> structureClaims,
                           Collection<RaidLedger.InstanceSnapshot> raidInstances,
                           Collection<RaidRewardReceipt> raidReceipts) {
        List<MobPersistenceSnapshot> persistentSnapshots = runtimeMobs.stream()
                .sorted(Comparator.comparingLong(MobPersistenceSnapshot::getMobId))
                .toList();
        var retention = snapshotRetentions.get(worldId);
        if (retention != null) persistentSnapshots = List.copyOf(retention.apply(persistentSnapshots));
        for (MobPersistenceSnapshot snapshot : persistentSnapshots) {
            com.gameexpert.engine.mob.MobRuntime.validateHorseInventoryPayload(snapshot);
        }
        if (raidPersistence != null && (raidInstances != null || raidReceipts != null)) {
            raidPersistence.flushWorld(worldId,
                    raidInstances == null ? List.of() : raidInstances,
                    raidReceipts == null ? List.of() : raidReceipts);
        }
        // 몹 스냅샷이 직전 저장과 같아도 새 청크 표식은 반드시 저장해야 한다.
        persistPopulatedChunks(worldId, populatedChunks);
        persistStructureOccupantClaims(worldId, structureClaims);
        List<MobPersistenceSnapshot> previous = savedSnapshots.get(worldId);
        if (previous != null && previous.equals(persistentSnapshots)) {
            return;
        }

        if (previous != null) {
            persistMobDelta(worldId, previous, persistentSnapshots);
            rememberAfterCommit(worldId, persistentSnapshots);
            return;
        }

        List<WorldMob> persistentMobs = persistentSnapshots.stream()
                .map(snapshot -> new WorldMob(worldId, snapshot))
                .toList();
        repository.deleteAllByWorldId(worldId);
        repository.saveAll(persistentMobs);
        rememberAfterCommit(worldId, persistentSnapshots);
    }

    /**
     * Runtime activation seeds {@code savedSnapshots} from the same rows, so the sorted snapshots
     * are an exact write set. Preserve existing JPA identities and touch only added/changed/removed
     * mob ids instead of deleting and recreating the complete world population every checkpoint.
     */
    private void persistMobDelta(Long worldId, List<MobPersistenceSnapshot> previous,
            List<MobPersistenceSnapshot> current) {
        Map<Long, MobPersistenceSnapshot> previousById = new HashMap<>(previous.size() * 2);
        for (MobPersistenceSnapshot snapshot : previous) {
            previousById.put(snapshot.getMobId(), snapshot);
        }
        List<MobPersistenceSnapshot> changed = new ArrayList<>();
        Map<Long, MobPersistenceSnapshot> changedBaselines = new HashMap<>();
        for (MobPersistenceSnapshot snapshot : current) {
            MobPersistenceSnapshot before = previousById.remove(snapshot.getMobId());
            if (!snapshot.equals(before)) {
                changed.add(snapshot);
                changedBaselines.put(snapshot.getMobId(), before);
            }
        }
        if (!previousById.isEmpty()) {
            repository.deleteByWorldIdAndMobIds(worldId, previousById.keySet());
        }
        if (changed.isEmpty()) return;

        List<Long> changedIds = changed.stream()
                .map(MobPersistenceSnapshot::getMobId).toList();
        Map<Long, WorldMob> rowsById = new HashMap<>(changed.size() * 2);
        for (WorldMob row : repository.findAllByWorldIdAndMobIds(worldId, changedIds)) {
            rowsById.put(row.getMobId(), row);
        }
        List<WorldMob> rows = new ArrayList<>(changed.size());
        for (MobPersistenceSnapshot snapshot : changed) {
            WorldMob row = rowsById.get(snapshot.getMobId());
            if (row == null) row = new WorldMob(worldId, snapshot);
            else row.applyCheckpoint(changedBaselines.get(snapshot.getMobId()), snapshot);
            rows.add(row);
        }
        repository.saveAll(rows);
    }

    private void persistStructureOccupantClaims(Long worldId,
            Collection<StructureOccupantClaimSnapshot> claims) {
        for (StructureOccupantClaimSnapshot claim : claims) {
            if (structureOccupantClaimRepository
                    .existsByWorldIdAndSiteKindAndCellXAndCellZ(
                            worldId, claim.siteKind(), claim.cellX(), claim.cellZ())) {
                continue;
            }
            structureOccupantClaimRepository.save(
                    new WorldStructureOccupantClaim(worldId, claim));
        }
    }

    private void rememberAfterCommit(Long worldId, List<MobPersistenceSnapshot> snapshots) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            savedSnapshots.put(worldId, snapshots);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                savedSnapshots.put(worldId, snapshots);
            }
        });
    }
}
