package com.gameexpert.state.service.inventory;

/** 플레이어와 대상 aggregate를 원자 교체하는 명령. */
public record InventoryMutationCommand(
        PlayerInventoryMutationSnapshot player, InventoryMutationTarget target) {
    public InventoryMutationCommand {
        if (player == null || target == null) {
            throw new IllegalArgumentException("player and target snapshots are required");
        }
    }
}
