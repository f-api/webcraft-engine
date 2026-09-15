package com.gameexpert.engine.persistence.tick;

import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable semantic outbox value for one durable final-carrier tick publication.
 *
 * <p>The state is deliberately kept outside the encoded semantic body. A retry or ACK therefore
 * cannot change the body digest, while the body still contains the full tick identity and result.
 */
public final class FinalCarrierTickPublication {
    private final FinalCarrierTickScheduler.ScheduledTick scheduledTick;
    private final FinalCarrierTickScheduler.DueDisposition disposition;
    private final List<FinalCarrierTickScheduler.BlockMutation> blockCells;
    private final List<GroundItemSnapshot> groundItems;
    private final PublicationState publicationState;

    public FinalCarrierTickPublication(FinalCarrierTickScheduler.ScheduledTick scheduledTick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation) {
        this(scheduledTick, disposition,
                Objects.requireNonNull(mutation, "tick mutation").blocks(), mutation.drops(),
                PublicationState.UNACKNOWLEDGED);
    }

    public FinalCarrierTickPublication(FinalCarrierTickScheduler.ScheduledTick scheduledTick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation, PublicationState publicationState) {
        this(scheduledTick, disposition,
                Objects.requireNonNull(mutation, "tick mutation").blocks(), mutation.drops(),
                publicationState);
    }

    public FinalCarrierTickPublication(FinalCarrierTickScheduler.ScheduledTick scheduledTick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            List<FinalCarrierTickScheduler.BlockMutation> blockCells,
            List<GroundItemSnapshot> groundItems) {
        this(scheduledTick, disposition, blockCells, groundItems,
                PublicationState.UNACKNOWLEDGED);
    }

    public FinalCarrierTickPublication(FinalCarrierTickScheduler.ScheduledTick scheduledTick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            List<FinalCarrierTickScheduler.BlockMutation> blockCells,
            List<GroundItemSnapshot> groundItems, PublicationState publicationState) {
        this.scheduledTick = Objects.requireNonNull(scheduledTick, "scheduled tick");
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.publicationState = Objects.requireNonNull(publicationState, "publication state");
        this.blockCells = canonicalBlocks(blockCells);
        this.groundItems = canonicalGroundItems(groundItems);
        if (disposition == FinalCarrierTickScheduler.DueDisposition.LIVE_TYPE_NO_OP
                && (!this.blockCells.isEmpty() || !this.groundItems.isEmpty())) {
            throw new IllegalArgumentException("no-op publication cannot contain a mutation");
        }
    }

    public FinalCarrierTickScheduler.ScheduledTick scheduledTick() {
        return scheduledTick;
    }

    public FinalCarrierTickScheduler.ScheduledTick tick() {
        return scheduledTick;
    }

    public FinalCarrierTickScheduler.DueDisposition disposition() {
        return disposition;
    }

    public List<FinalCarrierTickScheduler.BlockMutation> blockCells() {
        return blockCells;
    }

    public List<FinalCarrierTickScheduler.BlockMutation> blocks() {
        return blockCells;
    }

    public List<GroundItemSnapshot> groundItems() {
        return groundItems;
    }

    public List<GroundItemSnapshot> stableGroundItems() {
        return groundItems;
    }

    public PublicationState publicationState() {
        return publicationState;
    }

    public PublicationState state() {
        return publicationState;
    }

    public FinalCarrierTickScheduler.TickMutation mutation() {
        return new FinalCarrierTickScheduler.TickMutation(blockCells, groundItems);
    }

    public FinalCarrierTickPublication withState(PublicationState nextState) {
        Objects.requireNonNull(nextState, "next publication state");
        if (!FinalCarrierConsumedTick.isLegalPublicationTransition(publicationState, nextState)) {
            throw new IllegalArgumentException("illegal publication state transition: "
                    + publicationState + " -> " + nextState);
        }
        return new FinalCarrierTickPublication(scheduledTick, disposition, blockCells, groundItems,
                nextState);
    }

    private static List<FinalCarrierTickScheduler.BlockMutation> canonicalBlocks(
            List<FinalCarrierTickScheduler.BlockMutation> values) {
        Objects.requireNonNull(values, "block cells");
        ArrayList<FinalCarrierTickScheduler.BlockMutation> copy = new ArrayList<>(values.size());
        Set<BlockPosition> positions = new HashSet<>();
        for (FinalCarrierTickScheduler.BlockMutation value : values) {
            Objects.requireNonNull(value, "block cell");
            if (!positions.add(new BlockPosition(value.x(), value.y(), value.z()))) {
                throw new IllegalArgumentException("publication contains duplicate block cell");
            }
            copy.add(value);
        }
        copy.sort(Comparator.comparingInt(FinalCarrierTickScheduler.BlockMutation::x)
                .thenComparingInt(FinalCarrierTickScheduler.BlockMutation::y)
                .thenComparingInt(FinalCarrierTickScheduler.BlockMutation::z));
        return List.copyOf(copy);
    }

    private static List<GroundItemSnapshot> canonicalGroundItems(
            List<GroundItemSnapshot> values) {
        Objects.requireNonNull(values, "ground items");
        ArrayList<GroundItemSnapshot> copy = new ArrayList<>(values.size());
        Set<Long> entityIds = new HashSet<>();
        for (GroundItemSnapshot value : values) {
            Objects.requireNonNull(value, "ground item snapshot");
            if (!entityIds.add(value.entityId())) {
                throw new IllegalArgumentException(
                        "publication contains duplicate ground item entity ID");
            }
            FinalCarrierTickPublicationCodec.validateGroundItemSnapshot(value);
            copy.add(value);
        }
        copy.sort(Comparator.comparingLong(GroundItemSnapshot::entityId));
        return List.copyOf(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FinalCarrierTickPublication value)) return false;
        return scheduledTick.equals(value.scheduledTick)
                && disposition == value.disposition
                && blockCells.equals(value.blockCells)
                && groundItems.equals(value.groundItems)
                && publicationState == value.publicationState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduledTick, disposition, blockCells, groundItems, publicationState);
    }

    @Override
    public String toString() {
        return "FinalCarrierTickPublication[tick=" + scheduledTick
                + ", disposition=" + disposition + ", blockCells=" + blockCells.size()
                + ", groundItems=" + groundItems.size() + ", state=" + publicationState + "]";
    }

    private static final class BlockPosition {
        private final int x;
        private final int y;
        private final int z;

        private BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BlockPosition value && x == value.x && y == value.y
                    && z == value.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
