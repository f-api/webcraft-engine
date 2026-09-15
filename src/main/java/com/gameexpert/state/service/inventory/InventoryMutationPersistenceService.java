package com.gameexpert.state.service.inventory;

import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.state.service.PlayerWorldStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 플레이어와 컨테이너 aggregate의 스냅샷을 단일 DB 커밋으로 교체한다. */
@Service
@RequiredArgsConstructor
public class InventoryMutationPersistenceService {

    public enum Outcome { COMMITTED, STALE }

    private final PlayerWorldStateService playerStates;
    private final ChestPersistenceService chests;
    private final FurnacePersistenceService furnaces;
    private final BrewingPersistenceService brewing;
    private final CampfirePersistenceService campfires;
    private final MobPersistenceService mobs;
    private final TransactionTemplate transactionTemplate;

    /**
     * 호출부는 성공 뒤에만 런타임 조작을 승인해야 한다. 지연 명령은 예외를 밖으로 새지 않고
     * STALE을 반환하지만, 그 예외가 트랜잭션 경계를 통과하므로 앞서 갱신한 행도 함께 rollback된다.
     */
    public Outcome persist(InventoryMutationCommand command) {
        try {
            transactionTemplate.executeWithoutResult(ignored -> persistTransaction(command));
            return Outcome.COMMITTED;
        } catch (StaleInventoryMutationException stale) {
            return Outcome.STALE;
        }
    }

    private void persistTransaction(InventoryMutationCommand command) {
        PlayerInventoryMutationSnapshot player = command.player();
        Long worldId = player.worldId();
        // 대상 행을 먼저 잠근다. 플레이어 저장이 먼저 실행된 뒤 대상에서 stale이 나도 rollback되지만,
        // 일관된 대상→플레이어 잠금 순서는 서로 다른 플레이어가 같은 상자를 열 때 교착을 줄인다.
        switch (command.target()) {
            case InventoryMutationTarget.Chests target ->
                    chests.replaceExactSnapshotsJoiningTransaction(worldId, target.halves());
            case InventoryMutationTarget.Furnace target ->
                    furnaces.replaceExactSnapshotJoiningTransaction(worldId, target, target.xpMilli());
            case InventoryMutationTarget.Brewing target ->
                    brewing.replaceExactSnapshotJoiningTransaction(worldId, target);
            case InventoryMutationTarget.Campfire target ->
                    campfires.replaceExactSnapshotJoiningTransaction(worldId, target);
            case InventoryMutationTarget.MobCargo target ->
                    mobs.replaceCargoSnapshotJoiningTransaction(worldId, target);
            case InventoryMutationTarget.GeneratedChestMinecartCargo target ->
                    throw new StaleInventoryMutationException(
                            "generated chest minecart requires a settlement receipt");
        }
        playerStates.replaceExactSnapshotJoiningTransaction(player);
    }
}
