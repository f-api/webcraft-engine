package com.gameexpert.mob.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 청크 생성 시 동물 무리 배치(§39 vanilla creatureGenerationProbability 0.1)를 이미 판정한
 * 청크의 영구 표식입니다. 실패한 판정도 기록해 바닐라처럼 청크당 정확히 한 번만 굴립니다.
 */
@Getter
@Entity
@Table(
        name = "world_populated_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_populated_chunk",
                columnNames = { "world_id", "chunk_x", "chunk_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldPopulatedChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private int chunkX;

    @Column(nullable = false)
    private int chunkZ;

    public WorldPopulatedChunk(Long worldId, int chunkX, int chunkZ) {
        this.worldId = worldId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }
}
