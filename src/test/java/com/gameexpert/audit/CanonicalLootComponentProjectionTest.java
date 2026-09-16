package com.gameexpert.audit;

import com.gameexpert.chest.entity.WorldChest;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.lang.reflect.Method;
import com.gameexpert.authority.versioned.CanonicalStructureSnapshot;
import com.gameexpert.authority.versioned.LateLootOutcome;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.ProducerAuthorities;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAssignmentPlan;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.world.WorldGenerationProfiles;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalLootComponentProjectionTest {
    @Test void actualPinnedWorkerComponentsSurviveProjection() {
        verify("minecraft:chests/pillager_outpost", 1L, "INSTRUMENT");
        verify("minecraft:chests/shipwreck_supply", 2L, "SUSPICIOUS_STEW_EFFECTS");
        verify("minecraft:chests/trial_chambers/corridor", 3L, "DAMAGE");
    }
    private static void verify(String table, long seed, String requiredKind) {
        CanonicalLootStoredResolution resolved = realResolve(table, seed);
        List<com.gameexpert.chest.entity.ChestItem> projected = WorldChest.projectCanonicalLootItems(resolved);
        boolean found = false;
        for (com.gameexpert.chest.entity.ChestItem item : projected) {
            CanonicalLootStoredResolution.Slot source = resolved.slots().get(item.getSlot());
            com.gameexpert.engine.inventory.ItemComponentData metadata =
                    com.gameexpert.engine.inventory.ItemComponentCodec.decode(item.getItemType(), item.getItemComponentData());
            for (CanonicalLootStoredResolution.Component component : source.components().values()) {
                if (component.kind().equals(requiredKind)) found = true;
                switch (component.kind()) {
                    case "INSTRUMENT" -> {
                        assertThat(metadata.instrument()).isEqualTo(component.instrumentKey());
                        String renamed = com.gameexpert.engine.inventory.ItemComponentCodec.encode(item.getItemType(),
                                metadata.withCustomName("Saved horn"));
                        assertThat(com.gameexpert.engine.inventory.ItemComponentCodec.decode(item.getItemType(), renamed).instrument())
                                .isEqualTo(component.instrumentKey());
                    }
                    case "SUSPICIOUS_STEW_EFFECTS" -> {
                        assertThat(metadata.suspiciousStewEffect()).isEqualTo(component.stewEffects().getFirst().effectKey().substring(10));
                        assertThat(metadata.suspiciousStewDurationMcTicks()).isEqualTo(component.stewEffects().getFirst().duration());
                    }
                    case "DAMAGE" -> {
                        int canonicalMax = switch (source.itemKey()) {
                            case "minecraft:stone_axe", "minecraft:stone_pickaxe" -> 131;
                            case "minecraft:iron_axe" -> 250;
                            case "minecraft:diamond_axe", "minecraft:diamond_pickaxe" -> 1561;
                            case "minecraft:golden_axe", "minecraft:golden_pickaxe" -> 32;
                            case "minecraft:shield" -> 336;
                            default -> throw new AssertionError(source.itemKey());
                        };
                        int gameMax = com.gameexpert.engine.inventory.PlayerInventory.initialDurability(item.getItemType());
                        assertThat(item.getDurability()).isEqualTo(Math.max(1,
                                gameMax * (canonicalMax - component.damage()) / canonicalMax));
                    }
                    default -> { }
                }
            }
        }
        assertThat(found).as(requiredKind).isTrue();
        assertThatThrownBy(() -> WorldChest.projectCanonicalLootItems(resolved, 3)).isInstanceOf(IllegalStateException.class);
    }
    @Test void bambooHangingSignHasAppendOnlyIdentity() {
        CanonicalLootStoredResolution resolved = realResolve("minecraft:chests/trial_chambers/corridor", 3L);
        assertThat(WorldChest.projectCanonicalLootItems(resolved).stream().map(item -> (int) item.getItemType()))
                .contains(2516);
        assertThat(com.gameexpert.engine.inventory.PlayerInventory.stackMax((short)2516)).isEqualTo(16);
    }
    // Synthetic authenticated carrier; loot execution is the actual isolated pinned worker.
    private static CanonicalLootStoredResolution realResolve(String table, long rawSeed) {
        try {
            String producer = Mc263FinalChunkSidecars.CONTAINER_LOOT_PRODUCER_SOURCE_SHA256;
            String receipt = Mc263ContainerLootResolver.targetProductionContextReceipt(
                    "minecraft:plains", "0", producer, table, 0, 64, 0, Map.of());
            Mc263ContainerLootResolver.LocatedProductionContext context =
                    Mc263ContainerLootResolver.LocatedProductionContext.authenticated(
                            "minecraft:plains", Map.of(), "0", producer, table, 0, 64, 0, receipt);
            Mc263FinalChunkSidecars.Loot loot = new Mc263FinalChunkSidecars.Loot(
                    Blocks.blockIndex(0, 64, 0), "north", table, rawSeed);
            Method binding = Mc263FinalChunkSidecars.class.getDeclaredMethod("sourceDeclarationBinding",
                    int.class, int.class, int.class, int.class, Mc263FinalChunkSidecars.Loot.class,
                    int.class, Mc263ContainerLootResolver.LootProductionContext.class, String.class);
            binding.setAccessible(true);
            String declarationReceipt = (String) binding.invoke(null, 0, 0, 0, 0, loot, 27, context, producer);
            Mc263FinalChunkSidecars.ContainerLootDeclaration declaration =
                    new Mc263FinalChunkSidecars.ContainerLootDeclaration(0,
                            Mc263FinalChunkSidecars.ContainerLootSourceSection.LOOT, 0, 27,
                            context, producer, declarationReceipt);
            Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(List.of(), List.of(), List.of(loot),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(declaration));
            short[] blocks = new short[Blocks.CHUNK_BLOCKS];
            Arrays.fill(blocks, (short) Blocks.AIR);
            blocks[loot.packed()] = (short) Blocks.CHEST;
            int[] heights = new int[Blocks.CHUNK_X * Blocks.CHUNK_Z];
            Arrays.fill(heights, Blocks.MIN_Y);
            byte[] encoded = Mc263FinalChunkCodec.encode(new Mc263FinalChunkCodec.FinalChunk(0, 0,
                    blocks, Map.of(), heights, heights, heights, sidecars));
            NeutralFinalChunk source = ProducerAuthorities.verify(WorldGenerationProfiles.CURRENT, 0, 0, encoded);
            NeutralFinalChunk.ContainerLootDeclaration neutral = source.sidecars().containerLootDeclarations().getFirst();
            byte[] payload = CanonicalLootAssignmentPlan.encodeLocatedProductionContextPayload(
                    new CanonicalLootAssignmentPlan.LocatedProductionContextPayload(0,
                            neutral.sourceSection(), 0, 27, neutral.productionContext(),
                            neutral.producerSourceSha256(), neutral.sourceDeclarationSha256()));
            LateLootOutcome outcome = source.prepareLateLoot(payload, 0, table, rawSeed, 0, 64, 0,
                    27, null, null, new CanonicalStructureSnapshot(773L, WorldGenerationProfiles.CURRENT, List.of()),
                    List.of());
            assertThat(outcome.needsStructure()).isFalse();
            return CanonicalLootStoredResolution.decode(outcome.resolution());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("fixture authentication failed", failure);
        }
    }

}
