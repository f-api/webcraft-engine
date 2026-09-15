package com.gameexpert.shulker.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.api.persistence.WorldAccess;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [SHULKER-CONTENTS] <b>아이템으로 이동 중인</b> 셜커 상자 한 개의 27칸 저장 행입니다.
 *
 * <p>구조 선례는 {@code world_maps}({@link com.gameexpert.map.entity.WorldMap}) 그대로다 —
 * 월드가 소유하고, 월드 안에서 유일한 정수 참조 ID 를 갖는다({@code uq_shulker_contents_id}).
 * 칸 목록은 좌표 상자({@code world_chests})와 같은 {@link ChestItem} 을 재사용하되 자기
 * 수집 테이블({@code shulker_contents_items})에 담는다.
 *
 * <p><b>놓여 있는 동안에는 행이 없다.</b> 설치된 셜커 상자의 27칸은 다른 상자와 똑같이 좌표 키
 * 블록 엔티티({@code world_chests})가 소유하고, 이 테이블에는 "아이템으로 이동 중인" 27칸만
 * 남는다. 그래서 27칸이 두 곳에 동시에 존재하는 순간이 구조적으로 없다:
 * <ul>
 *   <li>설치 = 이 행을 지우면서 <b>같은 트랜잭션</b>에 좌표 행을 만든다.</li>
 *   <li>채굴 = 좌표 행을 지우면서 <b>같은 트랜잭션</b>에 이 행을 만든다.</li>
 * </ul>
 *
 * <p>정적판 {@code StandaloneShulkers}(DB v19)의 짝이다.
 */
@Getter
@Entity
@Table(
        name = "shulker_contents",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_shulker_contents_id", columnNames = {"world_id", "shulker_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShulkerContents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WorldAccess world;

    /** 월드 안에서 유일한 참조 ID. 아이템 칸·드랍 엔티티가 이 값 하나만 들고 다닌다. */
    @Column(nullable = false)
    private int shulkerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "shulker_contents_items",
            joinColumns = @JoinColumn(name = "shulker_contents_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uq_shulker_contents_item_slot",
                    columnNames = { "shulker_contents_id", "slot" }))
    private List<ChestItem> items = new ArrayList<>();

    public ShulkerContents(WorldAccess world, int shulkerId, List<ChestItem> items) {
        if (world == null || world.getId() == null) {
            throw new IllegalArgumentException("shulker contents must belong to a persisted world");
        }
        if (shulkerId <= 0) {
            throw new IllegalArgumentException("shulker reference id must be positive");
        }
        this.world = world;
        this.shulkerId = shulkerId;
        replaceItems(items);
    }

    /** 비어 있지 않은 칸만 통째로 교체합니다(틱 스레드가 만든 스냅샷을 그대로 저장). */
    public void replaceItems(List<ChestItem> snapshot) {
        this.items.clear();
        if (snapshot != null) this.items.addAll(snapshot);
    }
}
