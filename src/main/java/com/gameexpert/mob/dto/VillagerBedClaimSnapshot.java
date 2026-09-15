package com.gameexpert.mob.dto;

import com.gameexpert.engine.mob.villager.VillagerSocietyRules;

/** 주민 한 명이 소유한 HOME 침대 한 칸의 durable claim. */
public final class VillagerBedClaimSnapshot {
    private final int bedX;
    private final int bedY;
    private final int bedZ;
    private final long ownerVillagerId;
    private final int policyVersion;

    public VillagerBedClaimSnapshot(int bedX, int bedY, int bedZ, long ownerVillagerId,
            int policyVersion) {
        if (ownerVillagerId <= 0) {
            throw new IllegalArgumentException("bed claim owner must be a live villager id");
        }
        if (policyVersion <= 0) {
            throw new IllegalArgumentException("invalid bed claim policy version");
        }
        this.bedX = bedX;
        this.bedY = bedY;
        this.bedZ = bedZ;
        this.ownerVillagerId = ownerVillagerId;
        this.policyVersion = policyVersion;
    }

    public int bedX() { return bedX; }

    public int bedY() { return bedY; }

    public int bedZ() { return bedZ; }

    public long ownerVillagerId() { return ownerVillagerId; }

    public int policyVersion() { return policyVersion; }

    /** claim 레인 이름을 포함하므로 다른 claim 레인과 절대 충돌하지 않는다. */
    public String identityKey() {
        return VillagerSocietyRules.bedClaimKey(bedX, bedY, bedZ);
    }
}
