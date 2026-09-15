package com.gameexpert.mob.entity;

import com.gameexpert.engine.mob.villager.VillagerSocietyRules;
import com.gameexpert.mob.dto.VillagerBedClaimSnapshot;
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
 * 주민 HOME 침대 claim 한 칸. 구조물 occupant claim 과 같은 (world, 좌표) 유일 제약을 쓰되
 * 소유자가 바뀔 수 있어 갱신 가능한 레인이다.
 */
@Getter
@Entity
@Table(
        name = "world_villager_bed_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_villager_bed_claim",
                columnNames = { "world_id", "bed_x", "bed_y", "bed_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldVillagerBedClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private int bedX;

    @Column(nullable = false)
    private int bedY;

    @Column(nullable = false)
    private int bedZ;

    @Column(nullable = false)
    private long ownerVillagerId;

    @Column(nullable = false)
    private int policyVersion;

    public WorldVillagerBedClaim(Long worldId, VillagerBedClaimSnapshot snapshot) {
        this.worldId = worldId;
        this.bedX = snapshot.bedX();
        this.bedY = snapshot.bedY();
        this.bedZ = snapshot.bedZ();
        this.ownerVillagerId = snapshot.ownerVillagerId();
        this.policyVersion = snapshot.policyVersion();
    }

    /** 같은 침대의 소유자가 바뀌었을 때의 제자리 갱신. */
    public void reassign(VillagerBedClaimSnapshot snapshot) {
        this.ownerVillagerId = snapshot.ownerVillagerId();
        this.policyVersion = snapshot.policyVersion();
    }

    public VillagerBedClaimSnapshot toSnapshot() {
        return new VillagerBedClaimSnapshot(bedX, bedY, bedZ, ownerVillagerId, policyVersion);
    }

    public String identityKey() {
        return VillagerSocietyRules.bedClaimKey(bedX, bedY, bedZ);
    }
}
