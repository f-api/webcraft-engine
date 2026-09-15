package com.gameexpert.block.entity;


import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import com.gameexpert.api.persistence.WorldAccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "월드 지형 중, 시드로 만든 원본과 달라진 블록 한 칸"을 저장하는 엔티티입니다.
 * 테이블 {@code world_block_diffs} 에 매핑됩니다.
 *
 * 지형 전체를 저장하면 용량이 엄청나므로, 플레이어가 설치/파괴해 <b>바뀐 칸만</b> 기록합니다.
 * (원본은 시드로 언제든 다시 계산할 수 있습니다.)
 *   - 좌표 (x, y, z) 는 월드 절대 좌표입니다.
 *   - blockType 0(AIR) 은 "이 칸을 파괴해 비웠다"는 뜻입니다.
 *   - chunkX/chunkZ 는 조회 속도를 위해 x, z 에서 자동 계산해 저장합니다(청크 단위 조회용 인덱스).
 */
@Getter
@Entity
@Table(
        name = "world_block_diffs",
        // 한 월드의 같은 좌표(x,y,z)에는 변경 기록이 하나만 존재해야 합니다.
        uniqueConstraints = @UniqueConstraint(name = "uq_world_xyz", columnNames = {"world_id", "x", "y", "z"}),
        // 청크 단위 조회(월드 + 청크 좌표)를 빠르게 하기 위한 인덱스.
        indexes = {
                @Index(name = "idx_world_chunk", columnList = "world_id, chunk_x, chunk_z"),
                @Index(name = "idx_world_block_type", columnList = "world_id, block_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldBlockDiff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 월드의 변경인지. LAZY = 실제로 world 를 쓸 때만 DB 에서 불러옵니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    private int x;
    private int y;
    private int z;

    // x, z 로부터 자동 계산되는 청크 좌표(아래 @PrePersist/@PreUpdate 참고).
    @Column(nullable = false)
    private int chunkX;

    @Column(nullable = false)
    private int chunkZ;

    // 블록 종류 ID (계약 §2). SMALLINT 범위라 short 로 충분합니다.
    @Column(nullable = false)
    private short blockType;

    // 블록 타입별 의미를 갖는 상태 바이트.
    @Column(nullable = false)
    private short blockState;

    /** 몹 변형 저널이 쓴 diff의 entry key. null이면 플레이어/환경 변경입니다. */
    @Column(name = "mob_mutation_key", length = 160)
    private String mobMutationKey;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public WorldBlockDiff(WorldAccess world, int x, int y, int z, short blockType) {
        this(world, x, y, z, blockType, (short) 0);
    }

    public WorldBlockDiff(WorldAccess world, int x, int y, int z, short blockType, short blockState) {
        this(world, x, y, z, blockType, blockState, null);
    }

    public WorldBlockDiff(WorldAccess world, int x, int y, int z, short blockType, short blockState,
            String mobMutationKey) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
        this.blockState = blockState;
        this.mobMutationKey = mobMutationKey;
    }

    /** Replace the exact durable cell while retaining the unique world/coordinate identity. */
    public void replace(short blockType, short blockState, String mobMutationKey) {
        this.blockType = blockType;
        this.blockState = blockState;
        this.mobMutationKey = mobMutationKey;
    }

    /**
     * 저장/수정 직전에 청크 좌표를 다시 계산합니다.
     * 음수 좌표에서도 올바르게 내림하도록 반드시 Math.floorDiv 를 씁니다(계약 §1).
     * 예) x=-1 → floorDiv(-1,16) = -1  (일반 나눗셈 -1/16 = 0 과 다릅니다)
     */
    @PrePersist
    @PreUpdate
    private void computeChunkCoords() {
        this.chunkX = Math.floorDiv(x, 16);
        this.chunkZ = Math.floorDiv(z, 16);
    }
}
