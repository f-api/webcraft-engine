package com.gameexpert.mob.service;

import com.gameexpert.engine.raid.RaidLedger;
import com.gameexpert.engine.raid.RaidRewardReceipt;
import com.gameexpert.engine.raid.RaidVictoryReward;
import com.gameexpert.engine.trial.TrialVaultContract;
import com.gameexpert.mob.entity.WorldRaid;
import com.gameexpert.mob.entity.WorldRaidMember;
import com.gameexpert.mob.entity.WorldRaidReceipt;
import com.gameexpert.mob.entity.WorldRewardDelivery;
import com.gameexpert.mob.dto.RewardDeliverySnapshot;
import com.gameexpert.mob.dto.RewardPlayerSnapshot;
import com.gameexpert.mob.dto.RewardSettlement;
import com.gameexpert.mob.repository.WorldRaidMemberRepository;
import com.gameexpert.mob.repository.WorldRaidReceiptRepository;
import com.gameexpert.mob.repository.WorldRaidRepository;
import com.gameexpert.mob.repository.WorldRewardDeliveryRepository;
import com.gameexpert.state.entity.PlayerWorldState;
import com.gameexpert.state.repository.PlayerWorldStateRepository;
import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 레이드 원장·멤버·보상 receipt 의 영속 경계입니다.
 *
 * <p>원장과 멤버는 몹 aggregate 처럼 월드 단위 원자 교체이고, receipt 는 append-only 입니다.
 * 승리 확정과 receipt 기록이 한 트랜잭션이며, 실제 지급은 {@link #claimVictoryReward} 가
 * {@code PENDING → GRANTED} 를 조건부로 뒤집는 두 번째 경계에서만 일어납니다. 그래서 크래시·
 * 재접속·언로드 뒤 재시도는 언제나 같은 durable delivery로 수렴하며, 실제 플레이어 인벤토리와
 * 남은 수량도 receipt 전이와 같은 트랜잭션에서 저장합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class RaidPersistenceService {

    private final WorldRaidRepository raidRepository;
    private final WorldRaidMemberRepository memberRepository;
    private final WorldRaidReceiptRepository receiptRepository;
    private final WorldRewardDeliveryRepository deliveryRepository;
    private final PlayerWorldStateRepository playerStateRepository;
    /** 시작 때 읽었거나 마지막 커밋이 승인한 정렬 원장. 변경 없는 5초 체크포인트를 제거합니다. */
    private final Map<Long, List<RaidLedger.InstanceSnapshot>> savedInstances =
            new ConcurrentHashMap<>();

    /** 월드 활성화 때 원장을 새 런타임에 넣을 수 있는 불변 스냅샷으로 읽습니다. */
    @Transactional(readOnly = true)
    public List<RaidLedger.InstanceSnapshot> loadWorld(Long worldId) {
        Map<Long, List<RaidLedger.MemberSnapshot>> membersByRaid = new LinkedHashMap<>();
        for (WorldRaidMember member : memberRepository.findAllByWorldId(worldId)) {
            membersByRaid.computeIfAbsent(member.getRaidId(), key -> new ArrayList<>())
                    .add(member.toSnapshot());
        }
        for (List<RaidLedger.MemberSnapshot> members : membersByRaid.values()) {
            members.sort((left, right) -> Long.compare(left.mobId(), right.mobId()));
        }
        List<RaidLedger.InstanceSnapshot> rows = new ArrayList<>();
        for (WorldRaid raid : raidRepository.findAllByWorldId(worldId)) {
            rows.add(raid.toSnapshot(membersByRaid.getOrDefault(raid.getRaidId(), List.of())));
        }
        rows.sort((left, right) -> Long.compare(left.raidId(), right.raidId()));
        List<RaidLedger.InstanceSnapshot> snapshot = List.copyOf(rows);
        savedInstances.put(worldId, snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<RaidRewardReceipt> loadReceipts(Long worldId) {
        return receiptRepository.findAllByWorldId(worldId).stream()
                .map(WorldRaidReceipt::toReceipt)
                .sorted((left, right) -> Long.compare(left.raidId(), right.raidId()))
                .toList();
    }

    /**
     * 원장 전체 교체와 새 receipt 기록을 한 트랜잭션으로 커밋합니다. 승리 틱의 원장 상태와 그
     * 승리가 만든 receipt 가 절대 따로 저장되지 않아야 receipt 유실이 생기지 않습니다.
     */
    @Transactional
    public void flushWorld(Long worldId, Collection<RaidLedger.InstanceSnapshot> instances,
                           Collection<RaidRewardReceipt> receipts) {
        List<RaidLedger.InstanceSnapshot> current = instances == null
                ? List.of()
                : instances.stream()
                        .sorted((left, right) -> Long.compare(left.raidId(), right.raidId()))
                        .toList();
        boolean ledgerChanged = !current.equals(savedInstances.get(worldId));
        if (ledgerChanged) {
            raidRepository.deleteAllByWorldId(worldId);
            memberRepository.deleteAllByWorldId(worldId);
            List<WorldRaid> raidRows = new ArrayList<>();
            List<WorldRaidMember> memberRows = new ArrayList<>();
            for (RaidLedger.InstanceSnapshot instance : current) {
                raidRows.add(new WorldRaid(worldId, instance));
                for (RaidLedger.MemberSnapshot member : instance.members()) {
                    memberRows.add(new WorldRaidMember(worldId, instance.raidId(), member));
                }
            }
            raidRepository.saveAll(raidRows);
            memberRepository.saveAll(memberRows);
            rememberAfterCommit(worldId, current);
        }
        if (receipts == null) return;
        for (RaidRewardReceipt receipt : receipts) {
            recordReceipt(worldId, receipt);
        }
    }

    private void rememberAfterCommit(Long worldId,
            List<RaidLedger.InstanceSnapshot> snapshot) {
        List<RaidLedger.InstanceSnapshot> immutable = List.copyOf(snapshot);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            savedInstances.put(worldId, immutable);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                savedInstances.put(worldId, immutable);
            }
        });
    }

    /** 월드 삭제 경계에서 마지막 커밋 캐시와 더는 수령할 수 없는 receipt를 함께 지웁니다. */
    @Transactional
    public void forgetWorld(Long worldId) {
        if (worldId == null) return;
        deliveryRepository.deleteAllByWorldId(worldId);
        receiptRepository.deleteAllByWorldId(worldId);
        savedInstances.remove(worldId);
    }

    /**
     * receipt 는 append-only 입니다. 같은 `(worldId, raidId, rewardToken)` 이 이미 있으면 상태를
     * 덮어쓰지 않습니다. 이미 GRANTED 인 행을 PENDING 으로 되돌리면 재지급이 열리기 때문입니다.
     */
    @Transactional
    public boolean recordReceipt(Long worldId, RaidRewardReceipt receipt) {
        if (receipt == null) return false;
        if (receiptRepository.existsByWorldIdAndRaidIdAndRewardToken(
                worldId, receipt.raidId(), receipt.rewardToken())) {
            return false;
        }
        try {
            receiptRepository.save(new WorldRaidReceipt(worldId, receipt));
            return true;
        } catch (DataIntegrityViolationException concurrentInsert) {
            // 유일 제약이 동시 두 번째 기록을 막았다. 이미 존재하므로 성공과 같은 의미다.
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Optional<RaidRewardReceipt> findReceipt(Long worldId, long raidId, String rewardToken) {
        return receiptRepository.findByWorldIdAndRaidIdAndRewardToken(worldId, raidId, rewardToken)
                .map(WorldRaidReceipt::toReceipt);
    }

    /**
     * receipt 소비. 상태를 뒤집는 데 성공한 호출만 {@code grant} 훅을 실행하므로 같은 receipt의
     * 중복 청구는 없습니다. 훅이 예외를 던지면 트랜잭션이 롤백되어 receipt 는 PENDING 으로 남고
     * 다음 재시도가 같은 보상값으로 다시 시도합니다. 훅은 외부 상태를 저장하지 않고 불변 지급값만
     * 포착해야 합니다.
     *
     * @param grant 실제 보상 지급 훅. 인자는 영속된 보상 롤 시드이며, 다음 단계가 채웁니다.
     */
    @Transactional
    public RaidRewardReceipt.ClaimOutcome claimVictoryReward(Long worldId, long raidId,
            LongFunction<Boolean> grant) {
        Optional<WorldRaidReceipt> stored = receiptRepository
                .findByWorldIdAndRaidIdAndRewardToken(
                        worldId, raidId, RaidLedger.VICTORY_REWARD_TOKEN);
        if (stored.isEmpty()) return RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT;
        WorldRaidReceipt receipt = stored.get();
        if (!receipt.markGranted()) return RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED;
        receiptRepository.save(receipt);
        if (grant != null) grant.apply(receipt.getRewardSeed());
        return RaidRewardReceipt.ClaimOutcome.GRANTED;
    }

    /**
     * [TRIAL] 금고 개봉. 레이드 승리와 <b>같은 receipt 계약</b>을 쓰되 토큰이
     * {@code trial_vault_v1:<닉>} 이라 "금고마다 · 플레이어마다 한 번" 이 키 변경 없이 나온다
     * ({@link TrialVaultContract#rewardToken}).
     *
     * <p>승리 시점에는 수령자가 정해지지 않으므로(누구든 열쇠를 들고 오면 자기 몫이 있다)
     * PENDING 행은 개봉 시도의 <b>같은 트랜잭션</b>에서 처음 기록된다. 기록은 append-only 라
     * 두 번째 시도는 기존 행을 그대로 읽고, 그 행이 이미 GRANTED 면
     * {@code ALREADY_GRANTED} 로 거부된다 — 재접속·크래시 뒤에도 같은 판정이다.</p>
     *
     * @param pending {@link TrialVaultContract#pending} 이 만든 이 플레이어의 영수증
     * @param award 확정된 전리품을 실제로 쥐여 주는 훅. 실패를 알리면 트랜잭션이 롤백되어
     *              영수증이 PENDING 으로 남고 다음 시도가 같은 보상을 다시 지급한다.
     */
    @Transactional
    public RaidRewardReceipt.ClaimOutcome claimTrialVaultPrize(Long worldId,
            RaidRewardReceipt pending, Predicate<TrialVaultContract.Prize> award) {
        if (pending == null) return RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT;
        recordReceipt(worldId, pending);
        Optional<WorldRaidReceipt> stored = receiptRepository
                .findByWorldIdAndRaidIdAndRewardToken(
                        worldId, pending.raidId(), pending.rewardToken());
        if (stored.isEmpty()) return RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT;
        WorldRaidReceipt receipt = stored.get();
        if (!receipt.markGranted()) return RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED;
        receiptRepository.save(receipt);
        TrialVaultContract.Prize prize = TrialVaultContract.roll(receipt.getRewardSeed());
        if (award != null && !award.test(prize)) {
            throw new IllegalStateException(
                    "trial vault prize could not be awarded: " + pending.rewardToken());
        }
        return RaidRewardReceipt.ClaimOutcome.GRANTED;
    }

    /**
     * 승리 보상 지급. {@link #claimVictoryReward} 의 grant 람다 안에서 영속된 롤 시드를
     * {@link RaidVictoryReward#roll(long)} 에 그대로 넣으므로, 지급 내용은 receipt 가 처음
     * 기록된 순간에 이미 확정돼 있다. 재접속·재시도는 상태 전이에서 걸러져
     * {@code ALREADY_GRANTED} 로 수렴하고, 어떤 경로도 보상을 다시 뽑지 않는다.
     *
     * @param award 확정된 보상을 실제 인벤토리에 넣는 훅. 실패를 알리면 트랜잭션이 롤백되어
     *              receipt 가 PENDING 으로 남고 다음 시도가 같은 보상을 다시 지급한다.
     */
    @Transactional
    public RaidRewardReceipt.ClaimOutcome claimVictoryPrize(Long worldId, long raidId,
            Predicate<RaidVictoryReward.Prize> award) {
        return claimVictoryReward(worldId, raidId, rewardSeed -> {
            RaidVictoryReward.Prize prize = RaidVictoryReward.roll(rewardSeed);
            if (award == null) return Boolean.TRUE;
            if (!award.test(prize)) {
                throw new IllegalStateException("raid victory prize could not be awarded: " + raidId);
            }
            return Boolean.TRUE;
        });
    }

    /**
     * 승리 receipt, durable delivery, 플레이어 인벤토리를 한 DB 트랜잭션에서 정산한다.
     * 인벤토리가 가득 차면 delivery의 remainingCount가 그대로 남아 다음 접속/flush가 이어 받는다.
     */
    @Transactional
    public RewardSettlement settleVictoryPrize(
            Long worldId, long raidId, RewardPlayerSnapshot player) {
        Optional<WorldRaidReceipt> stored = receiptRepository
                .findLockedByWorldIdAndRaidIdAndRewardToken(
                        worldId, raidId, RaidLedger.VICTORY_REWARD_TOKEN);
        if (stored.isEmpty()) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        WorldRaidReceipt receipt = stored.get();
        if (!player.nickname().equals(receipt.getRecipientNickname())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        PlayerWorldState playerState = lockMatchingPlayerState(worldId, player);
        if (playerState == null) return RewardSettlement.stale();
        boolean newlyGranted = !receipt.toReceipt().granted();
        WorldRewardDelivery delivery = deliveryRepository
                .findLockedByWorldIdAndRaidIdAndRewardToken(
                        worldId, raidId, RaidLedger.VICTORY_REWARD_TOKEN)
                .orElseGet(() -> {
                    if (!newlyGranted) return null; // 옛 GRANTED 행에는 지급 근거를 재창조하지 않는다.
                    RaidVictoryReward.Prize prize = RaidVictoryReward.roll(receipt.getRewardSeed());
                    return deliveryRepository.save(new WorldRewardDelivery(worldId, raidId,
                            RaidLedger.VICTORY_REWARD_TOKEN, player.nickname(), prize.itemType(),
                            prize.count(), prize.durability(), prize.enchantments()));
                });
        if (delivery == null) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED);
        }
        if (newlyGranted) receipt.markGranted();
        RewardSettlement settlement = settleLockedDelivery(
                worldId, delivery, player, player.inventory(), null, playerState);
        return new RewardSettlement(
                newlyGranted ? RaidRewardReceipt.ClaimOutcome.GRANTED
                        : RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED,
                settlement.itemType(), settlement.insertedCount(), settlement.durability(),
                settlement.enchantments(), settlement.remainingCount(), null,
                settlement.inventoryCommitOutcome(), settlement.committedInventoryRevision(),
                settlement.committedInventory());
    }

    /** 금고의 열쇠 소비, receipt 생성/소비, 전리품 전달을 한 트랜잭션에서 정산한다. */
    @Transactional
    public RewardSettlement settleTrialVaultPrize(Long worldId, RaidRewardReceipt pending,
            RewardPlayerSnapshot player) {
        if (pending == null || !player.nickname().equals(pending.recipientNickname())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        Optional<WorldRaidReceipt> existing = receiptRepository
                .findLockedByWorldIdAndRaidIdAndRewardToken(
                        worldId, pending.raidId(), pending.rewardToken());
        if (existing.isPresent() && existing.get().toReceipt().granted()) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED);
        }
        PlayerWorldState playerState = lockMatchingPlayerState(worldId, player);
        if (playerState == null) return RewardSettlement.stale();
        WorldRaidReceipt receipt = existing.orElseGet(() ->
                receiptRepository.save(new WorldRaidReceipt(worldId, pending)));
        PlayerInventory inventory = player.inventory();
        // 손 참조는 라이브 인벤토리에서 잡혔고 정산은 detached 사본 위에서 하므로 다시 묶는다.
        PlayerInventory.HandRef keyHand = inventory.rebindHand(player.trialKeyHand());
        // [TRIAL] 손에 든 금고 열쇠(일반·불길한) 하나를 소비한다. 금고와 열쇠의 짝은 틱 스레드가 가렸다.
        if (keyHand == null || !TrialVaultContract.isVaultKey(inventory.stack(keyHand).itemType())
                || !inventory.consumeOne(keyHand, inventory.stack(keyHand).itemType())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        TrialVaultContract.Prize prize = TrialVaultContract.roll(receipt.getRewardSeed());
        WorldRewardDelivery delivery = deliveryRepository.save(new WorldRewardDelivery(
                worldId, pending.raidId(), pending.rewardToken(), player.nickname(),
                prize.itemType(), prize.count(), prize.durability(), prize.enchantments()));
        receipt.markGranted();
        RewardSettlement settlement = settleLockedDelivery(
                worldId, delivery, player, inventory, keyHand, playerState);
        return new RewardSettlement(RaidRewardReceipt.ClaimOutcome.GRANTED,
                settlement.itemType(), settlement.insertedCount(), settlement.durability(),
                settlement.enchantments(), settlement.remainingCount(), keyHand,
                settlement.inventoryCommitOutcome(), settlement.committedInventoryRevision(),
                settlement.committedInventory());
    }

    /** 재기동 뒤 남은 delivery를 플레이어 인벤토리와 원자적으로 계속 정산한다. */
    @Transactional
    public RewardSettlement settlePendingDelivery(Long worldId, RewardDeliverySnapshot requested,
            RewardPlayerSnapshot player) {
        if (requested == null || !player.nickname().equals(requested.recipientNickname())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        Optional<WorldRewardDelivery> delivery = deliveryRepository
                .findLockedByWorldIdAndRaidIdAndRewardToken(
                        worldId, requested.raidId(), requested.rewardToken());
        if (delivery.isEmpty() || delivery.get().getRemainingCount() <= 0) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED);
        }
        PlayerWorldState playerState = lockMatchingPlayerState(worldId, player);
        if (playerState == null) return RewardSettlement.stale();
        return settleLockedDelivery(
                worldId, delivery.get(), player, player.inventory(), null, playerState);
    }

    @Transactional(readOnly = true)
    public List<RewardDeliverySnapshot> loadPendingDeliveries(Long worldId) {
        // [TRIAL-GAP] 금고 배출 outbox 행은 인벤토리가 아니라 금고가 지면으로 갚는다.
        return deliveryRepository.findAllByWorldIdAndRemainingCountGreaterThan(worldId, 0)
                .stream().filter(row -> !TrialVaultContract.isEjectToken(row.getRewardToken()))
                .map(WorldRewardDelivery::snapshot).toList();
    }

    /**
     * [TRIAL-GAP] 금고 열쇠 정산(바닐라 {@code tryInsertKey} 의 영속 절반). 열쇠 하나 소비 · 이 플레이어의
     * 영수증 GRANTED · 굴린 전리품마다 배출 outbox 행 하나를 <b>한 트랜잭션</b>에 쓴다. 인벤토리에는
     * 전리품을 넣지 않는다 — 금고가 EJECTING 에서 지면으로 배출한다({@link #settleVaultEjection}).
     * 이미 GRANTED 인 영수증은 아무것도 소비하지 않고 {@code ALREADY_GRANTED} 다.
     */
    @Transactional
    public RewardSettlement settleTrialVaultUnlock(Long worldId, RaidRewardReceipt pending,
            RewardPlayerSnapshot player, boolean ominousVault) {
        if (pending == null || !player.nickname().equals(pending.recipientNickname())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        Optional<WorldRaidReceipt> existing = receiptRepository
                .findLockedByWorldIdAndRaidIdAndRewardToken(
                        worldId, pending.raidId(), pending.rewardToken());
        if (existing.isPresent() && existing.get().toReceipt().granted()) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED);
        }
        PlayerWorldState playerState = lockMatchingPlayerState(worldId, player);
        if (playerState == null) return RewardSettlement.stale();
        WorldRaidReceipt receipt = existing.orElseGet(() ->
                receiptRepository.save(new WorldRaidReceipt(worldId, pending)));
        PlayerInventory inventory = player.inventory();
        PlayerInventory.HandRef keyHand = inventory.rebindHand(player.trialKeyHand());
        if (keyHand == null || !TrialVaultContract.isVaultKey(inventory.stack(keyHand).itemType())
                || !inventory.consumeOne(keyHand, inventory.stack(keyHand).itemType())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        List<com.gameexpert.engine.trial.TrialLootTables.Stack> items =
                com.gameexpert.engine.trial.TrialLootTables.vaultReward(
                        ominousVault, receipt.getRewardSeed());
        for (int index = 0; index < items.size(); index++) {
            var stack = items.get(index);
            deliveryRepository.save(new WorldRewardDelivery(worldId, pending.raidId(),
                    TrialVaultContract.ejectToken(player.nickname(), index), player.nickname(),
                    stack.itemType(), stack.count(), vaultOutboxDurability(stack),
                    stack.enchantments(), stack.itemComponentData()));
        }
        receipt.markGranted();
        PlayerInventory.CompletePersistenceSnapshot committed =
                inventory.completePersistenceSnapshot();
        long committedRevision = committed.revision();
        if (!playerState.beginInventoryPersistenceRevision(committedRevision)) {
            throw new IllegalStateException("vault key inventory revision did not advance");
        }
        PlayerInventory.PersistenceSnapshot snapshot = committed.persistenceSnapshot();
        playerState.updateInventory(snapshot.itemTypes(), snapshot.counts(),
                snapshot.durabilities(), snapshot.enchantments(), snapshot.mapIds(),
                snapshot.shulkerIds(), snapshot.bucketMobData(), snapshot.itemComponentData(),
                committed.equippedTypes(), committed.equippedDurabilities(),
                committed.equippedEnchantments(), committed.equippedItemComponentData());
        playerState.updateOffhand(committed.offhand());
        return new RewardSettlement(RaidRewardReceipt.ClaimOutcome.GRANTED, (short) 0, 0, 0, 0,
                0, keyHand, RewardSettlement.InventoryCommitOutcome.COMMITTED, committedRevision,
                committed);
    }

    /**
     * [TRIAL-GAP] 금고 배출 outbox 행의 내구 칸. 불길한 병(내구 없음)은 이 칸에 증폭 단계(0..4)를
     * 싣는다 — outbox 행에 컴포넌트 칸이 없기 때문이며, 이 인코딩은 배출 토큰 행에만 쓴다.
     */
    public static int vaultOutboxDurability(com.gameexpert.engine.trial.TrialLootTables.Stack stack) {
        return stack.itemType() == PlayerInventory.OMINOUS_BOTTLE
                ? Math.max(0, stack.ominousAmplifier()) : stack.durability();
    }

    /** {@link #vaultOutboxDurability} 의 역. */
    public static com.gameexpert.engine.trial.TrialLootTables.Stack vaultOutboxStack(
            short itemType, int count, int durability, long enchantments) {
        return vaultOutboxStack(itemType, count, durability, enchantments, null);
    }

    /** [ENCHANT-WIDE] 성분 문자열(확장 인챈트)까지 되돌리는 역. */
    public static com.gameexpert.engine.trial.TrialLootTables.Stack vaultOutboxStack(
            short itemType, int count, int durability, long enchantments,
            String itemComponentData) {
        boolean bottle = itemType == PlayerInventory.OMINOUS_BOTTLE;
        return new com.gameexpert.engine.trial.TrialLootTables.Stack(itemType, count,
                bottle ? 0 : durability, enchantments, bottle ? durability : -1,
                bottle ? null : itemComponentData);
    }

    /** 금고 배출 정산 결과. */
    public enum VaultEjectionOutcome { COMMITTED, IDEMPOTENT, REJECTED }

    /**
     * [TRIAL-GAP] 금고 배출 한 번. 배출 outbox 행을 잠그고, 남아 있으면 지면 변이({@code mutation})와
     * 행 소진을 같은 트랜잭션에 커밋한다. 이미 소진된 행은 아무것도 하지 않는다.
     */
    @Transactional
    public VaultEjectionOutcome settleVaultEjection(Long worldId, long vaultId, String token,
            Runnable mutation) {
        if (!TrialVaultContract.isEjectToken(token) || mutation == null) {
            return VaultEjectionOutcome.REJECTED;
        }
        WorldRewardDelivery row = deliveryRepository
                .findLockedByWorldIdAndRaidIdAndRewardToken(worldId, vaultId, token).orElse(null);
        if (row == null) return VaultEjectionOutcome.REJECTED;
        if (row.getRemainingCount() <= 0) return VaultEjectionOutcome.IDEMPOTENT;
        mutation.run();
        row.delivered(row.getRemainingCount());
        deliveryRepository.save(row);
        return VaultEjectionOutcome.COMMITTED;
    }

    /** [TRIAL-GAP] 재기동 뒤 금고 상태: 금고별 보상 받은 닉네임과 아직 배출하지 않은 outbox 행. */
    public record TrialVaultRestore(Map<Long, java.util.Set<String>> rewarded,
            Map<Long, List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem>> items) {}

    @Transactional(readOnly = true)
    public TrialVaultRestore loadTrialVaultState(Long worldId) {
        Map<Long, java.util.Set<String>> rewarded = new LinkedHashMap<>();
        for (WorldRaidReceipt receipt : receiptRepository.findAllByWorldId(worldId)) {
            String nickname = TrialVaultContract.rewardTokenNickname(receipt.getRewardToken());
            if (nickname == null || !receipt.toReceipt().granted()) continue;
            rewarded.computeIfAbsent(receipt.getRaidId(), key -> new java.util.LinkedHashSet<>())
                    .add(nickname);
        }
        Map<Long, List<WorldRewardDelivery>> rows = new LinkedHashMap<>();
        for (WorldRewardDelivery row : deliveryRepository
                .findAllByWorldIdAndRemainingCountGreaterThan(worldId, 0)) {
            if (!TrialVaultContract.isEjectToken(row.getRewardToken())) continue;
            rows.computeIfAbsent(row.getRaidId(), key -> new ArrayList<>()).add(row);
        }
        Map<Long, List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem>> items =
                new LinkedHashMap<>();
        for (Map.Entry<Long, List<WorldRewardDelivery>> entry : rows.entrySet()) {
            List<WorldRewardDelivery> ordered = new ArrayList<>(entry.getValue());
            ordered.sort(java.util.Comparator
                    .comparing(WorldRewardDelivery::getRecipientNickname)
                    .thenComparingInt(row -> TrialVaultContract.ejectTokenIndex(row.getRewardToken())));
            List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem> list = new ArrayList<>();
            for (WorldRewardDelivery row : ordered) {
                list.add(new com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem(
                        row.getRewardToken(), vaultOutboxStack(row.getItemType(),
                                row.getRemainingCount(), row.getDurability(),
                                row.getEnchantments(), row.getItemComponentData())));
            }
            items.put(entry.getKey(), list);
        }
        return new TrialVaultRestore(rewarded, items);
    }

    private RewardSettlement settleLockedDelivery(Long worldId, WorldRewardDelivery delivery,
            RewardPlayerSnapshot player, PlayerInventory inventory,
            PlayerInventory.HandRef consumedKeyHand, PlayerWorldState state) {
        if (!worldId.equals(delivery.getWorldId())
                || !player.nickname().equals(delivery.getRecipientNickname())) {
            return RewardSettlement.outcome(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT);
        }
        int inserted = delivery.getRemainingCount() <= 0 ? 0 : inventory.addItem(
                delivery.getItemType(), delivery.getRemainingCount(), delivery.getDurability(),
                delivery.getEnchantments());
        boolean inventoryChanged = inserted > 0 || consumedKeyHand != null;
        if (inventoryChanged) {
            PlayerInventory.CompletePersistenceSnapshot committed =
                    inventory.completePersistenceSnapshot();
            long committedRevision = committed.revision();
            if (!state.beginInventoryPersistenceRevision(committedRevision)) {
                throw new IllegalStateException("reward inventory revision did not advance");
            }
            PlayerInventory.PersistenceSnapshot snapshot = committed.persistenceSnapshot();
            state.updateInventory(snapshot.itemTypes(), snapshot.counts(), snapshot.durabilities(),
                    snapshot.enchantments(), snapshot.mapIds(), snapshot.shulkerIds(),
                    snapshot.bucketMobData(), snapshot.itemComponentData(),
                    committed.equippedTypes(), committed.equippedDurabilities(),
                    committed.equippedEnchantments(), committed.equippedItemComponentData());
            state.updateOffhand(committed.offhand());
            delivery.delivered(inserted);
            return new RewardSettlement(RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED,
                    delivery.getItemType(), inserted, delivery.getDurability(),
                    delivery.getEnchantments(), delivery.getRemainingCount(), consumedKeyHand,
                    RewardSettlement.InventoryCommitOutcome.COMMITTED, committedRevision, committed);
        }
        delivery.delivered(inserted);
        return new RewardSettlement(RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED,
                delivery.getItemType(), inserted, delivery.getDurability(),
                delivery.getEnchantments(), delivery.getRemainingCount(), consumedKeyHand,
                RewardSettlement.InventoryCommitOutcome.UNCHANGED,
                RewardSettlement.NO_COMMITTED_INVENTORY_REVISION, null);
    }

    /** 호출자가 포착한 세대와 같은 플레이어 행만 보상 트랜잭션의 입력으로 승인한다. */
    private PlayerWorldState lockMatchingPlayerState(
            Long worldId, RewardPlayerSnapshot player) {
        PlayerWorldState state = playerStateRepository
                .findLockedByPlayerIdAndWorldId(player.playerId(), worldId)
                .orElseThrow(() -> new IllegalStateException("PLAYER_WORLD_STATE_NOT_FOUND"));
        return state.getInventoryPersistenceRevision() == player.inventoryRevision()
                ? state : null;
    }
}
