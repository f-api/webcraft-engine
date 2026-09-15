package com.gameexpert.shulker.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.ShulkerContentsStorage;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.shulker.entity.ShulkerContents;
import com.gameexpert.shulker.repository.ShulkerContentsRepository;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;

import lombok.RequiredArgsConstructor;

/**
 * [SHULKER-CONTENTS] 참조 저장소 런타임 캐시와 {@code shulker_contents} 사이의 얇은 배선.
 *
 * <p>정적판 {@code StandaloneShulkers} 의 서버 짝이다. <b>스스로 트랜잭션을 열지 않는다</b> —
 * 27칸이 좌표 블록 엔티티와 이 테이블 사이를 오가므로, 두 lane 은 반드시
 * {@code ChestPersistenceService} 가 여는 <b>한 트랜잭션</b> 안에서 함께 커밋되어야 한다.
 * 한쪽만 커밋되면 27칸이 복제되거나(설치 후 재기동) 사라진다(채굴 후 재기동).
 */
@Service
@RequiredArgsConstructor
public class ShulkerContentsPersistenceService {

    private final ShulkerContentsRepository repository;
    private final WorldStore worldRepository;

    /** 첫 월드 런타임 생성 시 저장된 참조 행을 캐시에 복원한다. */
    public void loadWorld(Long worldId, ShulkerContentsStorage storage) {
        for (ShulkerContents row : repository.findAllByWorldId(worldId)) {
            ChestInventory chest = new ChestInventory();
            for (ChestItem item : row.getItems()) {
                chest.restoreSlot(item.getSlot(), item.getItemType(), item.getItemCount(),
                        item.getDurability(), item.getEnchantments(), item.getMapId(),
                        item.getShulkerId(), item.getBucketMobData(),
                        item.getItemComponentData());
            }
            storage.load(row.getShulkerId(), chest);
        }
    }

    /**
     * 틱 스레드가 만든 배치를 값 스냅샷으로 굳힌다. 캐시를 읽는 이 단계만 틱 스레드에서
     * 돌고, 실제 쓰기({@link #persist})는 상자 lane 과 같은 트랜잭션에서 돈다.
     */
    public Snapshot snapshot(ShulkerContentsStorage.Batch batch, ShulkerContentsStorage storage) {
        if (batch == null || batch.isEmpty()) return Snapshot.EMPTY;
        Map<Integer, List<ChestItem>> upserts = new HashMap<>();
        for (int shulkerId : batch.upserts()) {
            ChestInventory chest = storage.peek(shulkerId);
            // 같은 배치 안에서 이미 설치로 떼어 낸 참조는 removals 가 소유한다.
            if (chest == null) continue;
            upserts.put(shulkerId, items(chest));
        }
        return new Snapshot(upserts, List.copyOf(batch.removals()));
    }

    /** 비어 있지 않은 칸만 저장 행으로 굳힌다(좌표 상자 flush 와 같은 계약). */
    private static List<ChestItem> items(ChestInventory chest) {
        List<ChestItem> items = new ArrayList<>();
        for (int slot = 0; slot < chest.slots(); slot++) {
            short type = chest.itemType(slot);
            if (type == PlayerInventory.EMPTY || chest.count(slot) <= 0) continue;
            Integer durability = PlayerInventory.isDurable(type) ? chest.durability(slot) : null;
            long mask = chest.enchantments(slot);
            int mapId = chest.mapId(slot);
            // [SHULKER-CONTENTS] 셜커 안에는 셜커가 없다([A] 바닐라) — 참조 열은 언제나 null 이다.
            items.add(new ChestItem(slot, type, chest.count(slot), durability,
                    mask == 0 ? null : mask, mapId == 0 ? null : mapId, null,
                    chest.bucketMobData(slot), chest.itemComponentData(slot)));
        }
        return items;
    }

    /**
     * 상자 lane 이 연 트랜잭션 안에서 참조 행을 쓴다. 삭제를 먼저 적용해, 같은 배치가 한
     * 참조를 지웠다가 다시 발급하는 순서를 뒤집지 않는다.
     */
    public void persist(Long worldId, Snapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        if (!snapshot.removals().isEmpty()) {
            repository.deleteByWorldIdAndShulkerIdIn(worldId, snapshot.removals());
        }
        if (snapshot.upserts().isEmpty()) return;
        Map<Integer, ShulkerContents> existing = new HashMap<>();
        for (ShulkerContents row
                : repository.findDirtyByWorldId(worldId, snapshot.upserts().keySet())) {
            existing.put(row.getShulkerId(), row);
        }
        WorldAccess world = null;
        List<ShulkerContents> changed = new ArrayList<>(snapshot.upserts().size());
        for (Map.Entry<Integer, List<ChestItem>> entry : snapshot.upserts().entrySet()) {
            ShulkerContents row = existing.get(entry.getKey());
            if (row == null) {
                if (world == null) world = worldRepository.getReferenceById(worldId);
                row = new ShulkerContents(world, entry.getKey(), entry.getValue());
            } else {
                row.replaceItems(entry.getValue());
            }
            changed.add(row);
        }
        repository.saveAll(changed);
    }

    /** 한 번의 플러시가 옮기는 값 스냅샷. 틱 스레드 상태를 참조로 들고 가지 않는다. */
    public record Snapshot(Map<Integer, List<ChestItem>> upserts, List<Integer> removals) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), List.of());

        public boolean isEmpty() {
            return upserts.isEmpty() && removals.isEmpty();
        }
    }
}
