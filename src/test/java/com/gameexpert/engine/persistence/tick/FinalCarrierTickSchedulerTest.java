package com.gameexpert.engine.persistence.tick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierDurableStateException;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.authority.versioned.NeutralFinalChunk.BlockTick;
import com.gameexpert.authority.versioned.NeutralFinalChunk.FluidTick;
import com.gameexpert.authority.versioned.NeutralFinalChunk.TickPriority;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class FinalCarrierTickSchedulerTest {
    @Test
    void inFlightPublicationRetainsOriginAndDestinationChunksUntilAcknowledged() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(230L, ticks), 0, true, ticks);
        FinalCarrierTickScheduler.TickMutation mutation = new FinalCarrierTickScheduler.TickMutation(List.of(
                new FinalCarrierTickScheduler.BlockMutation(16, Blocks.MIN_Y, -1, Blocks.AIR, 0)));
        assertThat(scheduler.pendingPublicationChunks()).isEmpty();
        assertThatThrownBy(() -> scheduler.drainDue(0, liveTypes(), (tick, world) -> mutation,
                (tick, result) -> { throw new IllegalStateException("publication pending"); }, 1))
                .hasMessage("publication pending");
        assertThat(scheduler.pendingPublicationChunks()).containsExactlyInAnyOrder(0L, (1L << 32) | 0xffffffffL);
        scheduler.evictChunk(0, 0);
        assertThat(scheduler.pendingPublicationChunks()).hasSize(2);
        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> { throw new AssertionError("must not replan"); },
                (tick, result) -> assertThat(result).isEqualTo(mutation), 1)).isOne();
        assertThat(scheduler.pendingPublicationChunks()).isEmpty();
    }


    @Test
    void admissionRequiresStructureReadyAndUnsupportedBlockLaneFailsClosed() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        BlockTick caveAir = block(0, Blocks.AIR, "minecraft:cave_air", 2,
                TickPriority.NORMAL, 0);
        var receipt = blockReceipt(7L, List.of(caveAir));

        assertThatThrownBy(() -> scheduler.admitBlockLane(receipt, 100L, false,
                List.of(caveAir))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("structure-ready");
        assertThat(durable.admissionCalls).isZero();

        BlockTick unsupported = block(1, Blocks.STONE, "minecraft:stone", 0,
                TickPriority.NORMAL, 0);
        List<BlockTick> unsupportedLane = List.of(caveAir, unsupported);
        assertThatThrownBy(() -> scheduler.admitBlockLane(blockReceipt(7L, unsupportedLane),
                100L, true, unsupportedLane)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported final-carrier block tick");
        assertThat(durable.admissionCalls).isZero();
    }

    @Test
    void pointedDripstoneCarrierAdmissionIsDurableInsteadOfRejectingTheWholeLane() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.POINTED_DRIPSTONE,
                "minecraft:pointed_dripstone", 2, TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(9L, ticks), 100L, true, ticks);
        assertThat(durable.admissionCalls).isEqualTo(1);
    }

    @Test
    void absoluteDuePrioritySignedSubTickAndBlockBeforeFluidAreExact() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(
                        block(0, Blocks.AIR, "minecraft:cave_air", 4,
                                TickPriority.NORMAL, Long.MAX_VALUE),
                        block(1, Blocks.AIR, "minecraft:cave_air", 4,
                                TickPriority.EXTREMELY_HIGH, 20),
                        block(2, Blocks.AIR, "minecraft:cave_air", 4,
                                TickPriority.NORMAL, Long.MIN_VALUE));
        List<FluidTick> fluids = List.of(fluid(3, "minecraft:water", 0,
                TickPriority.EXTREMELY_HIGH, Long.MIN_VALUE));
        scheduler.admitBlockLane(blockReceipt(1L, blocks), 1_000L, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(2L, fluids), 1_000L, true, fluids);
        assertThat(durable.admissionBases).containsExactly(1_000L, 1_000L);

        List<String> order = new ArrayList<>();
        scheduler.drainDue(1_004L, liveTypes(), (tick, world) -> mutation(tick.typeKey()),
                (tick, mutation) -> order.add(tick.lane() + ":" + tick.x()), 32);

        assertThat(order).containsExactly("BLOCK:1", "BLOCK:2", "BLOCK:0", "FLUID:3");
        assertThat(durable.settled).allSatisfy(tick -> assertThat(tick.dueTick())
                .isIn(1_000L, 1_004L));
    }

    @Test
    void firstLanePositionTypeAdmissionWinsWithoutRescheduling() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 9,
                        TickPriority.LOW, 10),
                block(0, Blocks.AIR, "minecraft:cave_air", 1,
                        TickPriority.EXTREMELY_HIGH, -10));
        var receipt = blockReceipt(3L, ticks);
        scheduler.admitBlockLane(receipt, 50L, true, ticks);

        assertThat(durable.admissionCandidates).singleElement().satisfies(raw ->
                assertThat(raw).singleElement().satisfies(candidate -> {
                    assertThat(candidate.dueTick()).isEqualTo(59L);
                    assertThat(candidate.priority()).isEqualTo(TickPriority.LOW);
                    assertThat(candidate.subTickOrder()).isEqualTo(10L);
                }));
        assertThat(scheduler.pendingTicks()).singleElement().satisfies(tick -> {
            assertThat(tick.dueTick()).isEqualTo(59L);
            assertThat(tick.priority()).isEqualTo(TickPriority.LOW);
            assertThat(tick.subTickOrder()).isEqualTo(10L);
        });
    }

    @Test
    void acceptedAdmissionMustReturnExactPreparedCandidateIdentity() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 9,
                TickPriority.LOW, 10));
        var prepared = scheduler.prepareBlockLane(blockReceipt(3L, ticks), 50L, true, ticks);
        var candidate = prepared.candidates().getFirst();
        var foreignKey = new FinalCarrierTickScheduler.TickKey(candidate.lane(),
                candidate.x() + 1, candidate.y(), candidate.z(), candidate.typeKey());
        List<FinalCarrierTickScheduler.ScheduledTick> corrupt = List.of(
                new FinalCarrierTickScheduler.ScheduledTick(foreignKey, candidate.receipt(),
                        candidate.expectedBlockId(), candidate.dueTick(), candidate.priority(),
                        candidate.subTickOrder(), 1L),
                new FinalCarrierTickScheduler.ScheduledTick(candidate.key(), candidate.receipt(),
                        candidate.expectedBlockId() + 1, candidate.dueTick(), candidate.priority(),
                        candidate.subTickOrder(), 1L),
                new FinalCarrierTickScheduler.ScheduledTick(candidate.key(), candidate.receipt(),
                        candidate.expectedBlockId(), candidate.dueTick() + 1,
                        candidate.priority(), candidate.subTickOrder(), 1L),
                new FinalCarrierTickScheduler.ScheduledTick(candidate.key(), candidate.receipt(),
                        candidate.expectedBlockId(), candidate.dueTick(), TickPriority.HIGH,
                        candidate.subTickOrder(), 1L),
                new FinalCarrierTickScheduler.ScheduledTick(candidate.key(), candidate.receipt(),
                        candidate.expectedBlockId(), candidate.dueTick(), candidate.priority(),
                        candidate.subTickOrder() + 1, 1L));

        for (var row : corrupt) {
            var admission = new FinalCarrierTickScheduler.Admission(
                    FinalCarrierTickScheduler.AdmissionStatus.ADMITTED, List.of(row));
            assertThatThrownBy(() -> scheduler.acceptAdmission(prepared, admission))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("prepared candidate identity");
        }
    }

    @Test
    void admittedAdmissionMustReturnACompleteCandidateDispositionBeforeInstall() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 1));
        var prepared = scheduler.prepareBlockLane(blockReceipt(25L, ticks), 0L, true, ticks);
        var omitted = durable(prepared.candidates().getFirst(), 1L);

        assertThatThrownBy(() -> scheduler.acceptAdmission(prepared,
                new FinalCarrierTickScheduler.Admission(
                        FinalCarrierTickScheduler.AdmissionStatus.ADMITTED, List.of(omitted))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("every candidate");
        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.pendingTicks()).isEmpty();
    }

    @Test
    void alreadyAcknowledgedAdmissionMustAuthenticateTheStoredCandidateBody() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        var prepared = scheduler.prepareBlockLane(blockReceipt(33L, ticks), 0L, true, ticks);
        var candidate = prepared.candidates().getFirst();
        var wrongBody = new FinalCarrierTickScheduler.ScheduledTick(candidate.key(),
                candidate.receipt(), candidate.expectedBlockId(), candidate.dueTick(),
                TickPriority.HIGH, candidate.subTickOrder(), 1L);

        assertThatThrownBy(() -> scheduler.acceptAdmission(prepared,
                new FinalCarrierTickScheduler.Admission(
                        FinalCarrierTickScheduler.AdmissionStatus.ALREADY_ACKNOWLEDGED,
                        List.of(wrongBody))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unauthenticated durable body");
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void alreadyAcknowledgedAdmissionMustAccountForEveryCandidateBeforeInstall() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 1));
        var prepared = scheduler.prepareBlockLane(blockReceipt(37L, ticks), 0L, true, ticks);

        assertThatThrownBy(() -> scheduler.acceptAdmission(prepared,
                new FinalCarrierTickScheduler.Admission(
                        FinalCarrierTickScheduler.AdmissionStatus.ALREADY_ACKNOWLEDGED,
                        List.of(durable(prepared.candidates().getFirst(), 1L)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("every candidate");
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void durablePublicationRejectsAStoredBodyThatDoesNotEncodeItsMutation() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        var prepared = scheduler.prepareBlockLane(blockReceipt(38L, ticks), 0L, true, ticks);
        var tick = durable(prepared.candidates().getFirst(), 1L);
        var mutation = mutation("authenticated-body");
        var result = FinalCarrierTickScheduler.SettlementResult.forDefault(tick,
                FinalCarrierTickScheduler.DueDisposition.EXECUTE,
                FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED, mutation);
        byte[] tampered = result.mutationBody();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> new FinalCarrierTickScheduler.DurablePublication(
                tick, FinalCarrierTickScheduler.DueDisposition.EXECUTE, mutation,
                result.publicationKey(),
                FinalCarrierTickPublicationCodec.publicationDigest(tampered), tampered,
                com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick
                        .PublicationState.OUTCOME_UNKNOWN))
                .isInstanceOf(FinalCarrierDurableStateException.class)
                .hasMessageContaining("identity is not canonical");
    }

    @Test
    void restoreValidatesTheFullBatchBeforeAnyResidentMutation() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0));
        var prepared = scheduler.prepareBlockLane(blockReceipt(26L, ticks), 0L, true, ticks);
        var valid = durable(prepared.candidates().getFirst(), 1L);
        var comparatorConflict = durable(prepared.candidates().get(1), 1L);
        durable.rows.put(valid.key(), valid);
        durable.rows.put(comparatorConflict.key(), comparatorConflict);

        assertThatThrownBy(scheduler::restoreWorld).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate durable admission order");
        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.pendingTicks()).isEmpty();

        durable.rows.remove(comparatorConflict.key());
        scheduler.restoreWorld();
        assertThat(scheduler.pendingTicks()).containsExactly(valid);
    }

    @Test
    void restoreCapacityValidationIsAtomicForTheCompleteBatch() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = new FinalCarrierTickScheduler(9L, durable, 1);
        List<BlockTick> ticks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 1));
        var prepared = scheduler.prepareBlockLane(blockReceipt(34L, ticks), 0L, true, ticks);
        var first = durable(prepared.candidates().getFirst(), 1L);
        var second = durable(prepared.candidates().get(1), 2L);
        durable.rows.put(first.key(), first);
        durable.rows.put(second.key(), second);

        assertThatThrownBy(scheduler::restoreWorld).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");
        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.pendingTicks()).isEmpty();
    }

    @Test
    void restoreSourceIdentityConflictIsRejectedBeforeResidentMutation() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        var firstReceipt = new FinalCarrierTickScheduler.CarrierReceipt(
                9L, 0, 0, "a".repeat(64), "b".repeat(64),
                FinalCarrierTickScheduler.Lane.BLOCK);
        var conflictingReceipt = new FinalCarrierTickScheduler.CarrierReceipt(
                9L, 0, 0, "a".repeat(64), "c".repeat(64),
                FinalCarrierTickScheduler.Lane.BLOCK);
        var firstKey = new FinalCarrierTickScheduler.TickKey(
                FinalCarrierTickScheduler.Lane.BLOCK, 0, Blocks.MIN_Y, 0,
                "minecraft:cave_air");
        var conflictingKey = new FinalCarrierTickScheduler.TickKey(
                FinalCarrierTickScheduler.Lane.BLOCK, 1, Blocks.MIN_Y, 0,
                "minecraft:cave_air");
        var first = new FinalCarrierTickScheduler.ScheduledTick(firstKey, firstReceipt,
                Blocks.AIR, 0L, TickPriority.NORMAL, 0L, 1L);
        var conflict = new FinalCarrierTickScheduler.ScheduledTick(conflictingKey,
                conflictingReceipt, Blocks.AIR, 0L, TickPriority.NORMAL, 1L, 2L);
        durable.rows.put(first.key(), first);
        durable.rows.put(conflict.key(), conflict);

        assertThatThrownBy(scheduler::restoreWorld).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting lane payloads");
        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.pendingTicks()).isEmpty();

        durable.rows.remove(conflict.key());
        scheduler.restoreWorld();
        assertThat(scheduler.pendingTicks()).containsExactly(first);
    }

    @Test
    void activateRejectsAForeignChunkBeforeResidentMutation() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        var receipt = blockReceipt(27L, 1, 0, ticks);
        var prepared = scheduler.prepareBlockLane(receipt, 0L, true, ticks);
        var foreign = durable(prepared.candidates().getFirst(), 1L);
        durable.forcedChunkRows = List.of(foreign);

        assertThatThrownBy(() -> scheduler.activateChunk(0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another chunk");
        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.pendingTicks()).isEmpty();
    }

    @Test
    void liveTypeMismatchIsAnAtomicNoOpReceipt() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(4L, ticks), 0L, true, ticks);
        int[] planned = {0};

        scheduler.drainDue(0L, (lane, x, y, z) -> "minecraft:stone", (tick, world) -> {
            planned[0]++;
            return mutation("must-not-run");
        }, (tick, mutation) -> { }, 1);

        assertThat(planned[0]).isZero();
        assertThat(durable.dispositions)
                .containsExactly(FinalCarrierTickScheduler.DueDisposition.LIVE_TYPE_NO_OP);
        assertThat(durable.mutations).containsExactly(FinalCarrierTickScheduler.TickMutation.NONE);
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void retryKeepsOriginalDueAndCapacityIsIndependentPerLane() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = new FinalCarrierTickScheduler(9L, durable, 2);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 2,
                TickPriority.NORMAL, 0), block(1, Blocks.AIR, "minecraft:cave_air", 20,
                TickPriority.NORMAL, 1));
        List<FluidTick> fluids = List.of(fluid(1, "minecraft:water", 3,
                TickPriority.NORMAL, 0), fluid(2, "minecraft:lava", 30,
                TickPriority.NORMAL, 1));
        scheduler.admitBlockLane(blockReceipt(5L, blocks), 10L, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(6L, fluids), 10L, true, fluids);

        List<FluidTick> overflow = List.of(fluid(3, "minecraft:water", 0,
                TickPriority.NORMAL, 0));
        assertThatThrownBy(() -> scheduler.admitFluidLane(
                fluidReceipt(7L, overflow), 10L, true, overflow))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("65536")
                .hasMessageContaining("capacity");

        durable.nextSettlement = FinalCarrierTickScheduler.Settlement.RETRY;
        scheduler.drainDue(12L, liveTypes(), (tick, world) -> mutation(tick.typeKey()),
                (tick, mutation) -> { }, 1);
        assertThat(scheduler.pendingTicks()).anySatisfy(tick ->
                assertThat(tick.dueTick()).isEqualTo(12L));
    }

    @Test
    void evictionAndReactivationPreserveAbsoluteDue() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<FluidTick> ticks = List.of(fluid(0, "minecraft:water", 5,
                TickPriority.HIGH, -7));
        scheduler.admitFluidLane(fluidReceipt(8L, ticks), 200L, true, ticks);

        scheduler.evictChunk(0, 0);
        assertThat(scheduler.pendingCount()).isZero();
        scheduler.activateChunk(0, 0);

        assertThat(scheduler.pendingTicks()).singleElement().satisfies(tick -> {
            assertThat(tick.dueTick()).isEqualTo(205L);
            assertThat(tick.priority()).isEqualTo(TickPriority.HIGH);
            assertThat(tick.subTickOrder()).isEqualTo(-7L);
        });
    }

    @Test
    void durableConsumedReceiptMakesRestartAndRedeliveryIdempotent() {
        MemoryBoundary durable = new MemoryBoundary();
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        var receipt = blockReceipt(9L, ticks);
        FinalCarrierTickScheduler first = scheduler(durable);
        first.admitBlockLane(receipt, 0L, true, ticks);
        first.drainDue(0L, liveTypes(), (tick, world) -> mutation(tick.typeKey()),
                (tick, mutation) -> { }, 1);

        FinalCarrierTickScheduler restarted = scheduler(durable);
        restarted.restoreWorld();
        assertThat(restarted.pendingCount()).isZero();
        assertThat(restarted.admitBlockLane(receipt, 100L, true, ticks))
                .isEqualTo(FinalCarrierTickScheduler.AdmissionStatus.ALREADY_ACKNOWLEDGED);
        assertThat(restarted.pendingCount()).isZero();

        List<BlockTick> changed = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 30,
                TickPriority.NORMAL, 0));
        assertThatThrownBy(() -> restarted.admitBlockLane(receipt, 100L, true, changed))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("payload");
        assertThatThrownBy(() -> restarted.admitBlockLane(
                blockReceipt(9L, changed), 100L, true, changed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same carrier source");
    }

    @Test
    void restartRecoversOutcomeUnknownPublicationWithoutReplanningOrDuplicatingTheEffect()
            throws Exception {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler first = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        first.admitBlockLane(blockReceipt(35L, blocks), 0L, true, blocks);
        FinalCarrierTickScheduler.TickMutation exact = new FinalCarrierTickScheduler.TickMutation(
                List.of(new FinalCarrierTickScheduler.BlockMutation(
                        0, Blocks.MIN_Y, 0, Blocks.AIR, 21)));
        boolean[] failAfterEffect = {true};
        assertThatThrownBy(() -> first.drainDue(0L, liveTypes(), (tick, world) -> exact,
                (tick, mutation) -> {
                    if (failAfterEffect[0]) {
                        failAfterEffect[0] = false;
                        throw new IllegalStateException("effect committed before transport failed");
                    }
                }, 1)).isInstanceOf(IllegalStateException.class);
        assertThat(durable.publications).hasSize(1);

        FinalCarrierTickScheduler restarted = scheduler(durable);
        restarted.restoreWorld();
        int[] replans = {0};
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();
        assertThat(restarted.drainDue(0L, liveTypes(), (tick, world) -> {
            replans[0]++;
            throw new AssertionError("outbox recovery must not replan");
        }, (tick, mutation) -> published.add(mutation), 1)).isOne();

        assertThat(replans[0]).isZero();
        assertThat(published).containsExactly(exact);
        assertThat(durable.publications).hasSize(1);
        assertThat(durable.publications.values().iterator().next().publicationState()).isEqualTo(
                com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick
                        .PublicationState.ACKNOWLEDGED);
        assertThat(restarted.pendingCount()).isZero();
    }

    @Test
    void fluidLegacySuppressionIsExactCellOnly() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<FluidTick> ticks = List.of(fluid(0, "minecraft:water", 2,
                TickPriority.NORMAL, 0));
        scheduler.admitFluidLane(fluidReceipt(10L, ticks), 0L, true, ticks);

        assertThat(scheduler.suppressesLegacyFluidAt(0, Blocks.MIN_Y, 0)).isTrue();
        assertThat(scheduler.suppressesLegacyFluidAt(1, Blocks.MIN_Y, 0)).isFalse();
        assertThat(scheduler.suppressesLegacyFluidAt(0, Blocks.MIN_Y + 1, 0)).isFalse();
    }

    @Test
    void eachLaneGetsItsOwnDrainBudgetAndDurableOrderBreaksExactTies() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(
                block(4, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 7),
                block(2, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 7));
        List<FluidTick> fluids = List.of(
                fluid(5, "minecraft:water", 0, TickPriority.NORMAL, 7),
                fluid(3, "minecraft:lava", 0, TickPriority.NORMAL, 7));
        scheduler.admitBlockLane(blockReceipt(11L, blocks), 0, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(12L, fluids), 0, true, fluids);
        List<String> committed = new ArrayList<>();

        assertThat(scheduler.drainDue(0, liveTypesForAll(),
                (tick, world) -> mutation(tick.typeKey()),
                (tick, mutation) -> committed.add(tick.lane() + ":" + tick.x()), 1))
                .isEqualTo(2);
        assertThat(committed).containsExactly("BLOCK:4", "FLUID:5");
    }

    @Test
    void negativeTimeUnsupportedEyeblossomAndCorruptDurableRowsFailClosed() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> cave = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        assertThatThrownBy(() -> scheduler.admitBlockLane(blockReceipt(13L, cave), -1,
                true, cave)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        int eyeblossomId = com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState
                .fromExact("minecraft:closed_eyeblossom").blockId();
        List<BlockTick> future = List.of(block(0, eyeblossomId,
                "minecraft:closed_eyeblossom", 0, TickPriority.NORMAL, 0));
        assertThatThrownBy(() -> scheduler.admitBlockLane(blockReceipt(14L, future), 0,
                true, future)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");

        int admissionCalls = durable.admissionCalls;
        List<FluidTick> unsupportedFluid = List.of(fluid(0, "minecraft:honey", 0,
                TickPriority.NORMAL, 0));
        assertThatThrownBy(() -> scheduler.admitFluidLane(
                fluidReceipt(15L, unsupportedFluid), 0, true, unsupportedFluid))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsupported");
        assertThat(durable.admissionCalls).isEqualTo(admissionCalls);

        var receipt = fluidReceipt(16L, List.of(fluid(0, "minecraft:water", 0,
                TickPriority.NORMAL, 0)));
        var corruptKey = new FinalCarrierTickScheduler.TickKey(
                FinalCarrierTickScheduler.Lane.FLUID, 0, Blocks.MIN_Y, 0, "minecraft:honey");
        durable.rows.put(corruptKey, new FinalCarrierTickScheduler.ScheduledTick(corruptKey,
                receipt, -1, 0, TickPriority.NORMAL, 0, 1));
        assertThatThrownBy(scheduler::restoreWorld).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported durable fluid tick");
    }

    @Test
    void blockSettlementRetryPreventsFluidLeapfrogAndAlreadyCommittedPublishesRecovery() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        List<FluidTick> fluids = List.of(fluid(1, "minecraft:water", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(16L, blocks), 0, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(17L, fluids), 0, true, fluids);
        durable.nextSettlement = FinalCarrierTickScheduler.Settlement.RETRY;
        List<String> published = new ArrayList<>();

        assertThat(scheduler.drainDue(0, liveTypes(),
                (tick, world) -> mutation(tick.typeKey()),
                (tick, mutation) -> published.add(tick.lane().name()), 1)).isZero();
        assertThat(scheduler.pendingCount()).isEqualTo(2);
        assertThat(durable.dispositions).containsExactly(
                FinalCarrierTickScheduler.DueDisposition.EXECUTE);

        durable.nextSettlement = FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED;
        assertThat(scheduler.drainDue(0, liveTypes(),
                (tick, world) -> mutation(tick.typeKey()),
                (tick, mutation) -> published.add(tick.lane().name()), 1)).isEqualTo(2);
        assertThat(published).containsExactly("BLOCK", "FLUID");
    }

    @Test
    void unknownCommitRetainsExactPlanForAlreadyCommittedRecovery() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(19L, blocks), 0, true, blocks);
        int[] planCalls = {0};
        List<FinalCarrierTickScheduler.TickMutation> planned = new ArrayList<>();
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();
        durable.nextSettlement = FinalCarrierTickScheduler.Settlement.RETRY;

        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            FinalCarrierTickScheduler.TickMutation result = mutation(
                    planCalls[0] == 1 ? "first-plan" : "replanned");
            planned.add(result);
            return result;
        }, (tick, mutation) -> published.add(mutation), 1)).isZero();
        assertThat(scheduler.pendingCount()).isOne();
        assertThat(published).isEmpty();

        durable.nextSettlement = FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED;
        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            return mutation("must-not-replan");
        }, (tick, mutation) -> published.add(mutation), 1)).isOne();

        assertThat(planCalls[0]).isOne();
        assertThat(published).containsExactly(planned.getFirst());
        assertThat(scheduler.pendingCount()).isZero();
        assertThat(durable.dispositions).containsExactly(
                FinalCarrierTickScheduler.DueDisposition.EXECUTE,
                FinalCarrierTickScheduler.DueDisposition.EXECUTE);
    }

    @Test
    void alreadyCommittedRecoveryPublishesExactlyOnce() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(20L, blocks), 0, true, blocks);
        durable.nextSettlement = FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED;
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();

        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> mutation("recovered"),
                (tick, mutation) -> published.add(mutation), 1)).isOne();
        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> mutation("duplicate"),
                (tick, mutation) -> published.add(mutation), 1)).isZero();

        assertThat(published).hasSize(1);
        assertThat(durable.settlementCalls).isOne();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void sinkFailureRetainsExactPlanAndRetriesThroughAlreadyCommitted() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(23L, blocks), 0, true, blocks);
        int[] planCalls = {0};
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();
        boolean[] failOnce = {true};

        assertThatThrownBy(() -> scheduler.drainDue(0, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            return mutation("stable-sink-plan");
        }, (tick, mutation) -> {
            if (failOnce[0]) {
                failOnce[0] = false;
                throw new IllegalStateException("sink unavailable");
            }
            published.add(mutation);
        }, 1)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sink unavailable");
        assertThat(scheduler.pendingCount()).isOne();
        assertThat(scheduler.pendingTicks()).singleElement().satisfies(tick ->
                assertThat(tick.typeKey()).isEqualTo("minecraft:cave_air"));
        assertThat(durable.consumed).hasSize(1);

        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            return mutation("must-not-replan");
        }, (tick, mutation) -> published.add(mutation), 1)).isOne();

        assertThat(planCalls[0]).isOne();
        assertThat(published).hasSize(1);
        assertThat(durable.settlementCalls).isOne();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void partialPublicationRetriesTheExactPlanWithoutRepeatingAppliedEffects() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(28L, blocks), 0L, true, blocks);
        FinalCarrierTickScheduler.TickMutation exact = new FinalCarrierTickScheduler.TickMutation(
                List.of(new FinalCarrierTickScheduler.BlockMutation(
                                0, Blocks.MIN_Y, 0, Blocks.AIR, 10),
                        new FinalCarrierTickScheduler.BlockMutation(
                                1, Blocks.MIN_Y, 0, Blocks.STONE, 11)));
        List<FinalCarrierTickScheduler.BlockMutation> applied = new ArrayList<>();
        int[] planCalls = {0};
        boolean[] throwAfterFirst = {true};

        assertThatThrownBy(() -> scheduler.drainDue(0L, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            return exact;
        }, (tick, mutation) -> {
            if (throwAfterFirst[0]) {
                throwAfterFirst[0] = false;
                applied.add(mutation.blocks().getFirst());
                throw new IllegalStateException("publication failed after first effect");
            }
            for (var block : mutation.blocks()) {
                if (!applied.contains(block)) applied.add(block);
            }
        }, 1)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after first effect");

        assertThat(scheduler.pendingCount()).isOne();
        assertThat(scheduler.drainDue(0L, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            return new FinalCarrierTickScheduler.TickMutation(List.of());
        }, (tick, mutation) -> {
            for (var block : mutation.blocks()) {
                if (!applied.contains(block)) applied.add(block);
            }
        }, 1)).isOne();

        assertThat(planCalls[0]).isOne();
        assertThat(applied).containsExactlyElementsOf(exact.blocks());
        assertThat(durable.settlementCalls).isOne();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void reentrantDrainReturnsTypedResultAndCannotReleaseTheOuterHead() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(29L, blocks), 0L, true, blocks);
        List<FinalCarrierTickScheduler.DrainReport> nested = new ArrayList<>();

        FinalCarrierTickScheduler.DrainReport outer = scheduler.drainDueReport(0L, liveTypes(),
                (tick, world) -> mutation("reentrant"), (tick, mutation) -> nested.add(
                        scheduler.drainDueReport(0L, liveTypes(),
                                (nestedTick, nestedWorld) -> mutation("must-not-plan"),
                                (nestedTick, nestedMutation) -> { }, 1)), 1);

        assertThat(outer).isEqualTo(new FinalCarrierTickScheduler.DrainReport(1,
                FinalCarrierTickScheduler.DrainStatus.DRAINED));
        assertThat(nested).containsExactly(new FinalCarrierTickScheduler.DrainReport(0,
                FinalCarrierTickScheduler.DrainStatus.REENTRANT));
        assertThat(durable.settlementCalls).isOne();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void drainIsBoundToTheOwnerThread() throws InterruptedException {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(30L, blocks), 0L, true, blocks);
        scheduler.drainDue(0L, liveTypes(), (tick, world) -> mutation("owner"),
                (tick, mutation) -> { }, 1);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                scheduler.drainDue(0L, liveTypes(), (tick, world) -> mutation("foreign"),
                        (tick, mutation) -> { }, 1);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        other.start();
        other.join();
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner-thread-only");
    }

    @Test
    void successfulPublicationReleasesResidentQueueOnlyAfterSinkReturns() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(24L, blocks), 0, true, blocks);
        List<Integer> pendingAtSink = new ArrayList<>();

        assertThat(scheduler.drainDue(0, liveTypes(), (tick, world) -> mutation("ordered"),
                (tick, mutation) -> {
                    pendingAtSink.add(scheduler.pendingCount());
                    assertThat(scheduler.pendingTicks()).containsExactly(tick);
                }, 1)).isOne();

        assertThat(pendingAtSink).containsExactly(1);
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void ambiguousDuplicateMutationPositionsFailBeforePersistence() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(18L, blocks), 0, true, blocks);

        assertThatThrownBy(() -> scheduler.drainDue(0, liveTypes(), (tick, world) ->
                new FinalCarrierTickScheduler.TickMutation(List.of(
                        new FinalCarrierTickScheduler.BlockMutation(
                                0, Blocks.MIN_Y, 0, Blocks.AIR, 0),
                        new FinalCarrierTickScheduler.BlockMutation(
                                0, Blocks.MIN_Y, 0, Blocks.STONE, 0))),
                (tick, mutation) -> { }, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate block position");
        assertThat(durable.settled).isEmpty();
        assertThat(scheduler.pendingCount()).isOne();
    }

    @Test
    void blockBacklogReservesTheRemainingSliceForGeneratedFluid() {
        ManualNanoClock clock = new ManualNanoClock(100L);
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        List<BlockTick> blocks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 1));
        List<FluidTick> fluids = List.of(fluid(2, "minecraft:water", 0, TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(31L, blocks), 0L, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(32L, fluids), 0L, true, fluids);
        List<String> published = new ArrayList<>();
        scheduler.drainDueReport(0L, liveTypes(), (tick, world) -> {
            if (tick.lane() == FinalCarrierTickScheduler.Lane.BLOCK) clock.set(104L);
            return mutation("reserved-fluid");
        }, (tick, mutation) -> published.add(tick.lane().name()), 32, 108L);
        assertThat(published).containsExactly("BLOCK", "FLUID");
        assertThat(scheduler.pendingCount()).isEqualTo(1);
    }

    @Test
    void cooperativeDeadlineStopsAtTheCausalBoundaryAndPreservesTheExactPlan() {
        ManualNanoClock clock = new ManualNanoClock(100L);
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        List<BlockTick> blocks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 1));
        List<FluidTick> fluids = List.of(fluid(2, "minecraft:water", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(31L, blocks), 0L, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(32L, fluids), 0L, true, fluids);
        List<FinalCarrierTickScheduler.TickMutation> planned = new ArrayList<>();
        int[] planCalls = {0};

        FinalCarrierTickScheduler.DrainReport bounded = scheduler.drainDueReport(0L,
                liveTypes(), (tick, world) -> {
                    planCalls[0]++;
                    FinalCarrierTickScheduler.TickMutation result = mutation("deadline-plan-"
                            + planCalls[0]);
                    planned.add(result);
                    clock.set(105L);
                    return result;
                }, (tick, mutation) -> { }, FinalCarrierTickScheduler.MAX_PENDING_TICKS, 105L);

        assertThat(bounded).isEqualTo(new FinalCarrierTickScheduler.DrainReport(0,
                FinalCarrierTickScheduler.DrainStatus.DEADLINE));
        assertThat(planCalls[0]).isOne();
        assertThat(durable.settlementCalls).isZero();
        assertThat(scheduler.pendingCount()).isEqualTo(3);

        clock.set(200L);
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();
        assertThat(scheduler.drainDue(0L, liveTypes(), (tick, world) -> {
            planCalls[0]++;
            return mutation("must-not-replan-" + planCalls[0]);
        }, (tick, mutation) -> published.add(mutation),
                FinalCarrierTickScheduler.MAX_PENDING_TICKS)).isEqualTo(3);
        assertThat(planCalls[0]).isEqualTo(3);
        assertThat(published).hasSize(3);
        assertThat(published.getFirst()).isEqualTo(planned.getFirst());
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void unavailableHeadDefersOnlyItselfWhileReadyRowsRetainTheirOrder() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        List<BlockTick> blocks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 1, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 1, TickPriority.NORMAL, 1),
                block(2, Blocks.AIR, "minecraft:cave_air", 1, TickPriority.NORMAL, 2));
        List<FluidTick> fluids = List.of(
                fluid(3, "minecraft:water", 1, TickPriority.NORMAL, 0),
                fluid(4, "minecraft:water", 1, TickPriority.NORMAL, 1));
        scheduler.admitBlockLane(blockReceipt(21L, blocks), 0L, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(22L, fluids), 0L, true, fluids);

        List<String> published = new ArrayList<>();
        List<String> examined = new ArrayList<>();
        Set<Integer> unavailable = new HashSet<>(List.of(0));
        FinalCarrierTickScheduler.TickSemantics semantics = (tick, world) -> {
            examined.add(tick.lane() + ":" + tick.x());
            if (unavailable.contains(tick.x())) {
                throw new FinalCarrierTickScheduler.UnavailableNeighborhood(
                        "final-carrier semantic read crossed resident chunks");
            }
            return mutation(tick.typeKey());
        };

        int processed = scheduler.drainDue(1L, liveTypes(), semantics,
                (tick, mutation) -> published.add(tick.lane() + ":" + tick.x()),
                FinalCarrierTickScheduler.MAX_PENDING_TICKS);

        assertThat(processed).isEqualTo(4);
        assertThat(examined).containsExactly("BLOCK:0", "BLOCK:1", "BLOCK:2", "FLUID:3", "FLUID:4");
        assertThat(published).containsExactly("BLOCK:1", "BLOCK:2", "FLUID:3", "FLUID:4");
        assertThat(scheduler.pendingTicks().stream()
                .map(tick -> tick.lane() + ":" + tick.x()).toList())
                .containsExactly("BLOCK:0");

        // An all-unavailable turn examines only each lane head, settles nothing, and returns promptly.
        unavailable.addAll(List.of(1, 2));
        examined.clear();
        int settledBefore = durable.settled.size();
        int settlementCallsBefore = durable.dispositions.size();
        FinalCarrierTickScheduler.DrainReport allUnavailable = scheduler.drainDueReport(1L,
                liveTypes(), semantics,
                (tick, mutation) -> published.add(tick.lane() + ":" + tick.x()),
                FinalCarrierTickScheduler.MAX_PENDING_TICKS, 50_000_000L);

        assertThat(allUnavailable.processed()).isZero();
        assertThat(allUnavailable.status()).isEqualTo(
                FinalCarrierTickScheduler.DrainStatus.UNAVAILABLE);
        assertThat(examined).containsExactly("BLOCK:0");
        assertThat(durable.settled).hasSize(settledBefore);
        assertThat(durable.dispositions).hasSize(settlementCallsBefore);

        // The neighbourhood comes back; its original absolute due tick is still pending.
        unavailable.clear();
        examined.clear();
        assertThat(scheduler.drainDue(1L, liveTypes(), semantics,
                (tick, mutation) -> published.add(tick.lane() + ":" + tick.x()),
                FinalCarrierTickScheduler.MAX_PENDING_TICKS)).isEqualTo(1);
        assertThat(examined).containsExactly("BLOCK:0");
        assertThat(published).containsExactly(
                "BLOCK:1", "BLOCK:2", "FLUID:3", "FLUID:4", "BLOCK:0");
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void unavailablePrefixResumesItsScanWithoutStarvingReadyRows() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        List<BlockTick> blocks = java.util.stream.IntStream.range(0, 12)
                .mapToObj(x -> block(x, Blocks.AIR, "minecraft:cave_air", 0,
                        TickPriority.NORMAL, x)).toList();
        scheduler.admitBlockLane(blockReceipt(120L, blocks), 0L, true, blocks);
        List<Integer> examined = new ArrayList<>();
        List<Integer> published = new ArrayList<>();
        for (int turn = 0; turn < 3; turn++) {
            clock.set(0L);
            scheduler.drainDueReport(0L, liveTypes(), (tick, world) -> {
                examined.add(tick.x());
                if (tick.x() < 10) {
                    clock.set(clock.getAsLong() + 1);
                    throw new FinalCarrierTickScheduler.UnavailableNeighborhood("cold neighbour");
                }
                return FinalCarrierTickScheduler.TickMutation.NONE;
            }, (tick, mutation) -> published.add(tick.x()), 32, 8L);
        }
        assertThat(published).containsExactly(10, 11);
        assertThat(examined.subList(0, 12)).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, 12).boxed().toList());
        assertThat(scheduler.pendingTicks()).hasSize(10)
                .allSatisfy(tick -> assertThat(tick.dueTick()).isZero());
        assertThat(scheduler.drainDue(0L, liveTypes(),
                (tick, world) -> FinalCarrierTickScheduler.TickMutation.NONE,
                (tick, mutation) -> {}, 32)).isEqualTo(10);
    }

    @Test
    void blockHalfDeadlineDoesNotLetFluidObserveAnUnpublishedBlockPlan() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        List<FluidTick> fluids = List.of(fluid(1, "minecraft:water", 0, TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(121L, blocks), 0L, true, blocks);
        scheduler.admitFluidLane(fluidReceipt(122L, fluids), 0L, true, fluids);
        List<String> events = new ArrayList<>();
        scheduler.drainDueReport(0L, liveTypes(), (tick, world) -> {
            events.add("plan:" + tick.lane());
            if (tick.lane() == FinalCarrierTickScheduler.Lane.BLOCK) clock.set(4L);
            return FinalCarrierTickScheduler.TickMutation.NONE;
        }, (tick, mutation) -> events.add("publish:" + tick.lane()), 32, 8L);
        assertThat(events).containsExactly("plan:BLOCK", "publish:BLOCK", "plan:FLUID", "publish:FLUID");
    }

    @Test
    void restoredPublicationsRetainCanonicalOrderAheadOfNewPlanning() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler first = scheduler(durable);
        List<BlockTick> blocks = List.of(
                block(0, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 0),
                block(1, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 1),
                block(2, Blocks.AIR, "minecraft:cave_air", 0, TickPriority.NORMAL, 2));
        first.admitBlockLane(blockReceipt(126L, blocks), 0L, true, blocks);
        List<FinalCarrierTickScheduler.ScheduledTick> rows = first.pendingTicks();
        // Recovery sources need not return map/hash iteration in scheduling order.
        for (int index : List.of(1, 0)) {
            FinalCarrierTickScheduler.ScheduledTick tick = rows.get(index);
            FinalCarrierTickScheduler.SettlementResult result = durable.settleAtomicallyWithPlan(
                    tick, FinalCarrierTickScheduler.DueDisposition.EXECUTE,
                    FinalCarrierTickScheduler.TickMutation.NONE);
            durable.beginOutcomeUnknown(tick, FinalCarrierTickScheduler.DueDisposition.EXECUTE, result);
        }
        FinalCarrierTickScheduler restored = scheduler(durable);
        restored.restoreWorld();
        List<String> events = new ArrayList<>();
        assertThat(restored.drainDue(0L, liveTypes(), (tick, world) -> {
            events.add("plan:" + tick.x());
            return FinalCarrierTickScheduler.TickMutation.NONE;
        }, (tick, mutation) -> events.add("publish:" + tick.x()), 32)).isEqualTo(3);
        assertThat(events).containsExactly("publish:0", "publish:1", "plan:2", "publish:2");
    }

    @Test
    void publicationThatUsesTheDeadlineDefersAckWithoutRepeatingItsEffect() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        List<BlockTick> blocks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(125L, blocks), 0L, true, blocks);
        List<String> events = new ArrayList<>();
        FinalCarrierTickScheduler.TickSemantics semantics =
                (tick, world) -> FinalCarrierTickScheduler.TickMutation.NONE;
        FinalCarrierTickScheduler.CommittedMutationSink sink = (tick, mutation) -> {
            events.add("published");
            clock.set(8L);
        };
        FinalCarrierTickScheduler.DrainReport first = scheduler.drainDueReport(
                0L, liveTypes(), semantics, sink, 32, 8L);
        assertThat(first).isEqualTo(new FinalCarrierTickScheduler.DrainReport(
                0, FinalCarrierTickScheduler.DrainStatus.DEADLINE));
        assertThat(durable.publications.values()).singleElement().satisfies(publication ->
                assertThat(publication.publicationState()).isEqualTo(
                        com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick
                                .PublicationState.OUTCOME_UNKNOWN));
        clock.set(0L);
        assertThat(scheduler.drainDueReport(0L, liveTypes(), semantics, sink, 32, 8L).processed()).isOne();
        assertThat(events).containsExactly("published");
        assertThat(durable.settlementCalls).isOne();
    }

    @Test
    void beginStepResumesWithoutRepeatingCompletedSettlement() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        StepExecutor executor = new StepExecutor();
        executor.holdFrom = 2;
        executor.onSubmit = () -> { if (executor.submissions == 2) clock.set(8L); };
        scheduler.installSettlementExecutor(executor);
        List<FluidTick> fluids = List.of(fluid(0, "minecraft:water", 0, TickPriority.NORMAL, 0));
        scheduler.admitFluidLane(fluidReceipt(123L, fluids), 0L, true, fluids);
        List<String> events = new ArrayList<>();
        FinalCarrierTickScheduler.TickSemantics semantics = (tick, world) -> {
            events.add("plan:" + tick.lane());
            return FinalCarrierTickScheduler.TickMutation.NONE;
        };
        FinalCarrierTickScheduler.CommittedMutationSink sink =
                (tick, mutation) -> events.add("publish:" + tick.lane());
        scheduler.drainDueReport(0L, liveTypes(), semantics, sink, 32, 8L);
        assertThat(durable.settlementCalls).isOne();
        executor.holdFrom = Integer.MAX_VALUE;
        executor.onSubmit = null;
        executor.release();
        clock.set(0L);
        List<BlockTick> blocks = List.of(block(1, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(124L, blocks), 0L, true, blocks);
        scheduler.drainDueReport(0L, liveTypes(), semantics, sink, 32, 8L);
        assertThat(events).containsExactly("plan:FLUID", "publish:FLUID", "plan:BLOCK", "publish:BLOCK");
        assertThat(durable.settlementCalls).isEqualTo(2);
        assertThat(executor.submissions).isEqualTo(6);
        assertThat(scheduler.pendingCount()).isZero();
    }

    // ---- 정산 executor(비동기 경로) 계약 -------------------------------------------------
    // executor 가 없으면 모든 단계가 호출 스레드에서 그대로 돌기 때문에, 위의 테스트들은
    // 전부 동기 경로만 덮는다. 실서비스는 executor 를 설치해 돌리므로 아래 셋이 그 경로의
    // 계약이다: 정상 정산, 드레인 예산을 넘긴 단계의 이월, 그리고 ACK 만 남기고 끝난 턴의 재개.

    @Test
    void settlementRunsOnTheInstalledExecutorAndStillSettlesExactlyOnce() {
        MemoryBoundary durable = new MemoryBoundary();
        FinalCarrierTickScheduler scheduler = scheduler(durable);
        StepExecutor executor = new StepExecutor();
        scheduler.installSettlementExecutor(executor);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(101L, ticks), 0L, true, ticks);
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();

        int processed = scheduler.drainDue(0L, liveTypes(),
                (tick, world) -> mutation(tick.typeKey()),
                (tick, applied) -> published.add(applied),
                FinalCarrierTickScheduler.MAX_PENDING_TICKS);

        assertThat(processed).isOne();
        // 정산·발행 시작·ACK 세 단계가 모두 executor 를 거친다.
        assertThat(executor.submissions).isEqualTo(3);
        assertThat(executor.stepThreads).hasSize(3)
                .allSatisfy(name -> assertThat(name).startsWith(StepExecutor.WORKER_NAME));
        assertThat(executor.stepThreads).doesNotContain(Thread.currentThread().getName());
        assertThat(published).hasSize(1);
        assertThat(durable.settlementCalls).isOne();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void aStepThatOutlivesTheDrainSliceIsCollectedByTheNextTurnInsteadOfResubmitted() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        StepExecutor executor = new StepExecutor();
        // 첫 단계를 붙잡고, 제출과 동시에 드레인 예산을 넘겨 이 턴을 끝낸다.
        executor.holdFrom = 1;
        executor.onSubmit = () -> clock.set(10_000L);
        scheduler.installSettlementExecutor(executor);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(102L, ticks), 0L, true, ticks);
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();
        FinalCarrierTickScheduler.TickSemantics semantics =
                (tick, world) -> mutation(tick.typeKey());
        FinalCarrierTickScheduler.CommittedMutationSink sink =
                (tick, applied) -> published.add(applied);

        FinalCarrierTickScheduler.DrainReport first = scheduler.drainDueReport(0L, liveTypes(),
                semantics, sink, FinalCarrierTickScheduler.MAX_PENDING_TICKS, 5_000L);

        assertThat(first).isEqualTo(new FinalCarrierTickScheduler.DrainReport(0,
                FinalCarrierTickScheduler.DrainStatus.DEADLINE));
        assertThat(executor.submissions).isOne();
        assertThat(published).isEmpty();
        // 행은 큐 머리에 그대로 남는다.
        assertThat(scheduler.pendingCount()).isOne();

        // 붙잡힌 단계가 끝난 뒤의 턴: 같은 단계를 다시 제출하지 않고 결과만 거둔다.
        executor.holdFrom = Integer.MAX_VALUE;
        executor.onSubmit = null;
        clock.set(0L);
        executor.release();

        FinalCarrierTickScheduler.DrainReport second = scheduler.drainDueReport(0L, liveTypes(),
                semantics, sink, FinalCarrierTickScheduler.MAX_PENDING_TICKS, 5_000L);

        assertThat(second.processed()).isOne();
        // 정산 1 + 발행 시작 1 + ACK 1. 이월된 정산이 다시 제출됐다면 넷이 됐을 것이다.
        assertThat(executor.submissions).isEqualTo(3);
        // 그리고 내구 정산은 정확히 한 번만 도달한다.
        assertThat(durable.settlementCalls).isOne();
        assertThat(published).hasSize(1);
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void aTurnThatEndsBeforeTheAcknowledgementResumesAtItWithoutPublishingTwice() {
        MemoryBoundary durable = new MemoryBoundary();
        ManualNanoClock clock = new ManualNanoClock(0L);
        FinalCarrierTickScheduler scheduler = scheduler(durable, clock);
        StepExecutor executor = new StepExecutor();
        // 정산과 발행 시작은 통과시키고, ACK 만 붙잡은 채 예산을 넘긴다.
        executor.holdFrom = 3;
        StepExecutor held = executor;
        executor.onSubmit = () -> {
            if (held.submissions >= 3) clock.set(10_000L);
        };
        scheduler.installSettlementExecutor(executor);
        List<BlockTick> ticks = List.of(block(0, Blocks.AIR, "minecraft:cave_air", 0,
                TickPriority.NORMAL, 0));
        scheduler.admitBlockLane(blockReceipt(103L, ticks), 0L, true, ticks);
        List<FinalCarrierTickScheduler.TickMutation> published = new ArrayList<>();
        int[] planCalls = {0};
        FinalCarrierTickScheduler.TickSemantics semantics = (tick, world) -> {
            planCalls[0]++;
            return mutation(tick.typeKey());
        };
        FinalCarrierTickScheduler.CommittedMutationSink sink =
                (tick, applied) -> published.add(applied);

        FinalCarrierTickScheduler.DrainReport first = scheduler.drainDueReport(0L, liveTypes(),
                semantics, sink, FinalCarrierTickScheduler.MAX_PENDING_TICKS, 5_000L);

        // 살아있는 월드에는 이미 적용됐지만 ACK 은 아직 내려가지 않았다.
        assertThat(first.processed()).isZero();
        assertThat(published).hasSize(1);
        assertThat(scheduler.pendingCount()).isOne();
        assertThat(executor.submissions).isEqualTo(3);

        executor.holdFrom = Integer.MAX_VALUE;
        executor.onSubmit = null;
        clock.set(0L);
        executor.release();

        FinalCarrierTickScheduler.DrainReport second = scheduler.drainDueReport(0L, liveTypes(),
                semantics, sink, FinalCarrierTickScheduler.MAX_PENDING_TICKS, 5_000L);

        assertThat(second.processed()).isOne();
        // 재개는 ACK 에서 시작한다. 다시 정산하지도, 다시 계획하지도, 다시 발행하지도 않는다.
        assertThat(published).hasSize(1);
        assertThat(planCalls[0]).isOne();
        assertThat(durable.settlementCalls).isOne();
        assertThat(executor.submissions).isEqualTo(3);
        assertThat(scheduler.pendingCount()).isZero();
    }

    /**
     * 정산 단계를 실제로 다른 스레드에서 돌리는 테스트용 executor.
     *
     * <p>제출된 단계를 새 스레드에서 실행하고 그 자리에서 합류하므로, 단계가 호출 스레드를
     * 떠났다는 사실은 진짜로 검증되면서 테스트는 결정적으로 남는다. {@code holdFrom} 이후의
     * 제출은 실행하지 않고 붙잡아 둬, 드레인 예산이 먼저 끝나는 상황을 만든다.
     */
    private static final class StepExecutor
            implements FinalCarrierTickScheduler.SettlementExecutor {

        private static final String WORKER_NAME = "test-final-carrier-settlement";

        private final List<Runnable> holding = new ArrayList<>();
        private final List<String> stepThreads = new ArrayList<>();
        private int submissions;
        /** 이 순번(1부터)부터의 제출은 실행하지 않고 붙잡는다. */
        private int holdFrom = Integer.MAX_VALUE;
        private Runnable onSubmit;

        @Override
        public boolean submit(Runnable step) {
            submissions++;
            if (onSubmit != null) onSubmit.run();
            if (submissions >= holdFrom) {
                holding.add(step);
                return true;
            }
            runOffThread(step);
            return true;
        }

        private void release() {
            List<Runnable> pending = List.copyOf(holding);
            holding.clear();
            for (Runnable step : pending) runOffThread(step);
        }

        private void runOffThread(Runnable step) {
            Thread worker = new Thread(() -> {
                stepThreads.add(Thread.currentThread().getName());
                step.run();
            }, WORKER_NAME + "-" + submissions);
            worker.start();
            try {
                worker.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("settlement step join interrupted", interrupted);
            }
        }
    }

    private static FinalCarrierTickScheduler scheduler(MemoryBoundary durable) {
        return new FinalCarrierTickScheduler(9L, durable);
    }

    private static FinalCarrierTickScheduler scheduler(MemoryBoundary durable,
            LongSupplier nanoTime) {
        return new FinalCarrierTickScheduler(9L, durable,
                FinalCarrierTickScheduler.MAX_PENDING_TICKS, nanoTime);
    }

    private static FinalCarrierTickScheduler.CarrierReceipt blockReceipt(
            long source, List<BlockTick> ticks) {
        return blockReceipt(source, 0, 0, ticks);
    }

    private static FinalCarrierTickScheduler.CarrierReceipt blockReceipt(
            long source, int chunkX, int chunkZ, List<BlockTick> ticks) {
        return FinalCarrierTickScheduler.blockReceipt(9L, chunkX, chunkZ,
                "%064x".formatted(source), ticks);
    }

    private static FinalCarrierTickScheduler.CarrierReceipt fluidReceipt(
            long source, List<FluidTick> ticks) {
        return FinalCarrierTickScheduler.fluidReceipt(9L, 0, 0,
                "%064x".formatted(source), ticks);
    }

    private static BlockTick block(int localX, int id, String key, int delay,
            TickPriority priority, long subTick) {
        return new BlockTick(localX, id, key, delay, priority, subTick);
    }

    private static FluidTick fluid(int localX, String key, int delay,
            TickPriority priority, long subTick) {
        return new FluidTick(localX, key, delay, priority, subTick);
    }

    private static FinalCarrierTickScheduler.LiveTypes liveTypes() {
        return (lane, x, y, z) -> lane == FinalCarrierTickScheduler.Lane.BLOCK
                ? "minecraft:cave_air" : "minecraft:water";
    }

    private static FinalCarrierTickScheduler.LiveTypes liveTypesForAll() {
        return (lane, x, y, z) -> lane == FinalCarrierTickScheduler.Lane.BLOCK
                ? "minecraft:cave_air" : (x == 5 ? "minecraft:water" : "minecraft:lava");
    }

    private static FinalCarrierTickScheduler.TickMutation mutation(String value) {
        return new FinalCarrierTickScheduler.TickMutation(List.of(
                new FinalCarrierTickScheduler.BlockMutation(0, Blocks.MIN_Y, 0,
                        value.hashCode() & 0xffff, 0)));
    }

    private static FinalCarrierTickScheduler.ScheduledTick durable(
            FinalCarrierTickScheduler.ScheduledTick candidate, long durableOrder) {
        return new FinalCarrierTickScheduler.ScheduledTick(candidate.key(), candidate.receipt(),
                candidate.expectedBlockId(), candidate.dueTick(), candidate.priority(),
                candidate.subTickOrder(), durableOrder);
    }

    private static final class ManualNanoClock implements LongSupplier {
        private long now;

        private ManualNanoClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        void set(long now) {
            this.now = now;
        }
    }

    private static final class MemoryBoundary
            implements FinalCarrierTickScheduler.AtomicPersistence {
        final Map<FinalCarrierTickScheduler.TickKey, FinalCarrierTickScheduler.ScheduledTick> rows =
                new LinkedHashMap<>();
        final Set<FinalCarrierTickScheduler.CarrierReceipt> receipts = new HashSet<>();
        final Map<String, String> payloadBySourceLane = new HashMap<>();
        final Set<FinalCarrierTickScheduler.TickKey> consumed = new HashSet<>();
        final Map<FinalCarrierTickScheduler.TickKey,
                FinalCarrierTickScheduler.SettlementResult> settlementResults = new HashMap<>();
        final Map<FinalCarrierTickScheduler.TickKey,
                FinalCarrierTickScheduler.DurablePublication> publications = new LinkedHashMap<>();
        final Map<FinalCarrierTickScheduler.TickKey,
                FinalCarrierTickScheduler.ScheduledTick> consumedRows = new HashMap<>();
        final List<FinalCarrierTickScheduler.ScheduledTick> settled = new ArrayList<>();
        final List<FinalCarrierTickScheduler.DueDisposition> dispositions = new ArrayList<>();
        final List<FinalCarrierTickScheduler.TickMutation> mutations = new ArrayList<>();
        final List<Long> admissionBases = new ArrayList<>();
        final List<List<FinalCarrierTickScheduler.ScheduledTick>> admissionCandidates =
                new ArrayList<>();
        List<FinalCarrierTickScheduler.ScheduledTick> forcedChunkRows;
        int admissionCalls;
        int settlementCalls;
        FinalCarrierTickScheduler.Settlement nextSettlement =
                FinalCarrierTickScheduler.Settlement.COMMITTED;
        long nextDurableOrder = 1L;

        @Override
        public FinalCarrierTickScheduler.Admission admitAndAcknowledge(
                FinalCarrierTickScheduler.CarrierReceipt receipt,
                List<FinalCarrierTickScheduler.ScheduledTick> candidates,
                long admissionBaseMcTick, int capacity) {
            admissionCalls++;
            admissionBases.add(admissionBaseMcTick);
            admissionCandidates.add(List.copyOf(candidates));
            String sourceLane = receipt.worldId() + ":" + receipt.chunkX() + ":"
                    + receipt.chunkZ() + ":" + receipt.sourceFingerprint() + ":" + receipt.lane();
            String previousPayload = payloadBySourceLane.putIfAbsent(
                    sourceLane, receipt.lanePayloadFingerprint());
            if (previousPayload != null && !previousPayload.equals(receipt.lanePayloadFingerprint())) {
                throw new IllegalStateException("same carrier source has a different lane payload");
            }
            if (receipts.contains(receipt)) {
                return new FinalCarrierTickScheduler.Admission(
                        FinalCarrierTickScheduler.AdmissionStatus.ALREADY_ACKNOWLEDGED,
                        rows.values().stream().filter(tick -> tick.receipt().equals(receipt)).toList(),
                        consumedRows.values().stream()
                                .filter(tick -> tick.receipt().equals(receipt)).toList());
            }
            long laneRows = rows.values().stream()
                    .filter(tick -> tick.lane() == receipt.lane()).count();
            long newKeys = candidates.stream().map(FinalCarrierTickScheduler.ScheduledTick::key)
                    .filter(key -> !rows.containsKey(key) && !consumed.contains(key)).distinct().count();
            if (laneRows + newKeys > capacity) {
                return new FinalCarrierTickScheduler.Admission(
                        FinalCarrierTickScheduler.AdmissionStatus.CAPACITY_REJECTED, List.of());
            }
            receipts.add(receipt);
            for (var candidate : candidates) rows.computeIfAbsent(candidate.key(), ignored ->
                    new FinalCarrierTickScheduler.ScheduledTick(candidate.key(), candidate.receipt(),
                            candidate.expectedBlockId(), candidate.dueTick(), candidate.priority(),
                            candidate.subTickOrder(), nextDurableOrder++));
            return new FinalCarrierTickScheduler.Admission(
                    FinalCarrierTickScheduler.AdmissionStatus.ADMITTED,
                    rows.values().stream().filter(tick -> tick.receipt().equals(receipt)).toList());
        }

        @Override
        public List<FinalCarrierTickScheduler.ScheduledTick> loadWorld(long worldId) {
            return rows.values().stream().filter(tick -> tick.receipt().worldId() == worldId).toList();
        }

        @Override
        public List<FinalCarrierTickScheduler.ScheduledTick> loadChunk(
                long worldId, int chunkX, int chunkZ) {
            if (forcedChunkRows != null) return List.copyOf(forcedChunkRows);
            return rows.values().stream().filter(tick -> tick.receipt().worldId() == worldId
                    && Math.floorDiv(tick.x(), 16) == chunkX
                    && Math.floorDiv(tick.z(), 16) == chunkZ).toList();
        }

        @Override
        public FinalCarrierTickScheduler.Settlement settleAtomically(
                FinalCarrierTickScheduler.ScheduledTick tick,
                FinalCarrierTickScheduler.DueDisposition disposition,
                FinalCarrierTickScheduler.TickMutation mutation) {
            settlementCalls++;
            dispositions.add(disposition);
            mutations.add(mutation);
            FinalCarrierTickScheduler.Settlement requested = nextSettlement;
            if (requested != FinalCarrierTickScheduler.Settlement.COMMITTED) {
                nextSettlement = FinalCarrierTickScheduler.Settlement.COMMITTED;
                if (requested == FinalCarrierTickScheduler.Settlement.RETRY) return requested;
                rows.remove(tick.key());
                consumed.add(tick.key());
                consumedRows.put(tick.key(), tick);
                return requested;
            }
            if (!rows.containsKey(tick.key())) return FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED;
            rows.remove(tick.key());
            consumed.add(tick.key());
            consumedRows.put(tick.key(), tick);
            settled.add(tick);
            return FinalCarrierTickScheduler.Settlement.COMMITTED;
        }

        @Override
        public FinalCarrierTickScheduler.SettlementResult settleAtomicallyWithPlan(
                FinalCarrierTickScheduler.ScheduledTick tick,
                FinalCarrierTickScheduler.DueDisposition disposition,
                FinalCarrierTickScheduler.TickMutation mutation) {
            FinalCarrierTickScheduler.Settlement status = settleAtomically(
                    tick, disposition, mutation);
            if (status == FinalCarrierTickScheduler.Settlement.RETRY) {
                return FinalCarrierTickScheduler.SettlementResult.forDefault(
                        tick, disposition, status, mutation);
            }
            FinalCarrierTickScheduler.SettlementResult stored = settlementResults.get(tick.key());
            if (stored != null) {
                return new FinalCarrierTickScheduler.SettlementResult(status, stored.mutation(),
                        stored.publicationKey(), stored.mutationDigest(), stored.mutationBody());
            }
            FinalCarrierTickScheduler.SettlementResult result =
                    FinalCarrierTickScheduler.SettlementResult.forDefault(
                            tick, disposition, status, mutation);
            settlementResults.put(tick.key(), result);
            return result;
        }

        @Override
        public FinalCarrierTickScheduler.DurablePublication beginOutcomeUnknown(
                FinalCarrierTickScheduler.ScheduledTick tick,
                FinalCarrierTickScheduler.DueDisposition disposition,
                FinalCarrierTickScheduler.SettlementResult result) {
            FinalCarrierTickScheduler.DurablePublication existing = publications.get(tick.key());
            if (existing != null) return existing;
            FinalCarrierTickScheduler.DurablePublication publication =
                    new FinalCarrierTickScheduler.DurablePublication(tick, disposition,
                            result.mutation(), result.publicationKey(), result.mutationDigest(),
                            result.mutationBody(),
                            com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick
                                    .PublicationState.OUTCOME_UNKNOWN);
            publications.put(tick.key(), publication);
            return publication;
        }

        @Override
        public void acknowledgePublication(FinalCarrierTickScheduler.DurablePublication publication) {
            publications.put(publication.tick().key(), new FinalCarrierTickScheduler.DurablePublication(
                    publication.tick(), publication.disposition(), publication.mutation(),
                    publication.publicationKey(), publication.mutationDigest(),
                    publication.mutationBody(),
                    com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick
                            .PublicationState.ACKNOWLEDGED));
        }

        @Override
        public List<FinalCarrierTickScheduler.DurablePublication> loadDurablePublications(
                long worldId, int limit) {
            return publications.values().stream()
                    .filter(publication -> publication.tick().receipt().worldId() == worldId)
                    .limit(limit)
                    .toList();
        }
    }
}
