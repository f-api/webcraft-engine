package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Versioned, domain-separated Java/Rust receipt for one dormant FEATURES dispatch result. */
final class Mc263FeaturesReceipt {
    private static final byte[] MAGIC = "MCF263FR".getBytes(StandardCharsets.US_ASCII);
    private static final int SCHEMA_VERSION = 5;

    private Mc263FeaturesReceipt() {
    }

    static byte[] encode(Mc263FeatureDispatcher.DispatchResult result) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(700_000);
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(MAGIC);
            output.writeShort(SCHEMA_VERSION);
            Mc263FeaturesRegion.CenterSnapshot center = result.center();
            output.writeInt(center.chunkX());
            output.writeInt(center.chunkZ());
            writeSection(output, "SCHD", schedulePayload(result.scheduleTrace()));
            writeSection(output, "BLKS", blockPayload(center));
            writeSection(output, "STAT", statePayload(center));
            writeSection(output, "HMAP", heightmapPayload(center));
            writeSection(output, "POST", postprocessPayload(center));
            writeSection(output, "TICK", tickPayload(center));
            writeSection(output, "FLTK", fluidTickPayload(center));
            writeSection(output, "BCAP", capabilityPayload(center));
            writeSection(output, "LOOT", lootPayload(center));
            writeSection(output, "SPWN", spawnerPayload(center));
            writeSection(output, "OWNR", ownershipPayload(center));
            writeSection(output, "ARCH", archaeologyPayload(center));
            writeSection(output, "BEES", beehivePayload(center));
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory FEATURES receipt failed", impossible);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static byte[] schedulePayload(
            List<Mc263FeatureDispatcher.ScheduleEvent> events) throws IOException {
        return payload(output -> {
            output.writeInt(events.size());
            for (Mc263FeatureDispatcher.ScheduleEvent event : events) {
                output.writeInt(event.sourceChunkX());
                output.writeInt(event.sourceChunkZ());
                output.writeByte(event.kind() == Mc263FeatureDispatcher.Kind.STRUCTURE ? 0 : 1);
                writeUnsignedByte(output, event.step(), "schedule step");
                writeUnsignedShort(output, event.globalIndex(), "schedule global index");
                writeUtf8(output, event.key());
            }
        });
    }

    private static byte[] blockPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        short[] blocks = center.borrowedBlockIds();
        // AGENTS 10l: writeShort emits the high byte then the low byte through
        // DataOutputStream's counter and ByteArrayOutputStream's grow check, one word at a time,
        // 98,304 times per chunk. The buffer below is the identical big-endian stream written in
        // one bulk copy, into a stream sized for it up front.
        byte[] packed = new byte[Math.multiplyExact(blocks.length, 2)];
        for (int index = 0; index < blocks.length; index++) {
            int block = Short.toUnsignedInt(blocks[index]);
            packed[index * 2] = (byte) (block >>> 8);
            packed[index * 2 + 1] = (byte) block;
        }
        return payload(Integer.BYTES + packed.length, output -> {
            output.writeInt(blocks.length);
            output.write(packed);
        });
    }

    /**
     * The exact-state section: a natural-ordered palette of every state present in the centre
     * chunk, then one unsigned-16 palette index per block.
     *
     * <p>AGENTS 10l: the palette is decided by the <em>set</em> of block IDs present plus the
     * sparse exact-state overrides, so it is decided once per distinct ID rather than once per
     * cell, and the per-cell index is then one array read rather than a binary search over the
     * sorted string palette (seventeen string comparisons per block, 98,304 blocks). Both the
     * palette order and the emitted indices are unchanged: a {@code TreeSet<String>} still
     * realises the natural ordering, and the index tables below are read out of that same
     * ordering.</p>
     */
    private static byte[] statePayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        short[] blocks = center.borrowedBlockIds();
        int[] overridePositions = center.borrowedExactStateOverridePositions();
        Mc263FeatureBlockState[] overrideStates = center.borrowedExactStateOverrideStates();
        // The ID bound is the authenticated block-ID table's own capacity (AGENTS 10m).
        boolean[] idPresent = new boolean[Blocks.BLOCK_ID_TABLE_CAPACITY];
        int overrideCursor = 0;
        for (int index = 0; index < blocks.length; index++) {
            if (overrideCursor < overridePositions.length
                    && overridePositions[overrideCursor] == index) {
                overrideCursor++;
                continue;
            }
            idPresent[Short.toUnsignedInt(blocks[index])] = true;
        }
        TreeSet<String> orderedStates = new TreeSet<>();
        for (int id = 0; id < idPresent.length; id++) {
            if (idPresent[id]) orderedStates.add(Mc263FeatureBlockState.defaultExactStateForId(id));
        }
        for (Mc263FeatureBlockState override : overrideStates) {
            orderedStates.add(override.exactState());
        }
        if (orderedStates.size() > 0xffff) {
            throw new IllegalStateException("FEATURES state palette exceeds unsigned-16: "
                    + orderedStates.size());
        }
        List<String> palette = List.copyOf(orderedStates);
        Map<String, Integer> paletteIndex = new HashMap<>(palette.size() * 2);
        for (int index = 0; index < palette.size(); index++) {
            paletteIndex.put(palette.get(index), index);
        }
        int[] indexById = new int[idPresent.length];
        for (int id = 0; id < idPresent.length; id++) {
            indexById[id] = idPresent[id]
                    ? paletteIndex.get(Mc263FeatureBlockState.defaultExactStateForId(id)) : -1;
        }
        int[] indexByOverride = new int[overrideStates.length];
        for (int index = 0; index < overrideStates.length; index++) {
            indexByOverride[index] = paletteIndex.get(overrideStates[index].exactState());
        }
        byte[] packed = new byte[Math.multiplyExact(Blocks.CHUNK_BLOCKS, 2)];
        int sparseIndex = 0;
        for (int index = 0; index < Blocks.CHUNK_BLOCKS; index++) {
            int value;
            if (sparseIndex < overridePositions.length
                    && overridePositions[sparseIndex] == index) {
                value = indexByOverride[sparseIndex++];
            } else {
                value = indexById[Short.toUnsignedInt(blocks[index])];
            }
            if (value < 0) {
                throw new IllegalStateException("FEATURES exact state absent from canonical "
                        + "palette: " + Mc263FeatureBlockState.defaultExactStateForId(
                                Short.toUnsignedInt(blocks[index])));
            }
            packed[index * 2] = (byte) (value >>> 8);
            packed[index * 2 + 1] = (byte) value;
        }
        return payload(Short.BYTES + palette.size() * 64 + Integer.BYTES + packed.length,
                output -> {
                    output.writeShort(palette.size());
                    for (String state : palette) writeUtf8(output, state);
                    output.writeInt(Blocks.CHUNK_BLOCKS);
                    output.write(packed);
                });
    }

    private static byte[] heightmapPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        int[] worldSurface = center.worldSurfaceWg();
        int[] oceanFloor = center.oceanFloorWg();
        int[] motionBlocking = center.motionBlocking();
        return payload(Integer.BYTES * (1 + worldSurface.length + oceanFloor.length
                + motionBlocking.length), output -> {
            output.writeInt(worldSurface.length);
            for (int height : worldSurface) output.writeInt(height);
            for (int height : oceanFloor) output.writeInt(height);
            for (int height : motionBlocking) output.writeInt(height);
        });
    }

    private static byte[] postprocessPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.PostprocessMark> marks = center.postprocessMarks();
        return payload(output -> {
            output.writeInt(marks.size());
            for (Mc263FeaturesRegion.PostprocessMark mark : marks) {
                int section = Math.floorDiv(mark.blockY() - Blocks.MIN_Y, 16);
                writeUnsignedByte(output, section, "postprocess section");
                writeUnsignedByte(output, mark.localX(), "postprocess local X");
                output.writeInt(mark.blockY());
                writeUnsignedByte(output, mark.localZ(), "postprocess local Z");
            }
        });
    }

    private static byte[] tickPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.ScheduledBlockTick> ticks = center.scheduledBlockTicks();
        return payload(output -> {
            output.writeInt(ticks.size());
            for (Mc263FeaturesRegion.ScheduledBlockTick tick : ticks) {
                writeUnsignedByte(output, tick.localX(), "tick local X");
                output.writeInt(tick.blockY());
                writeUnsignedByte(output, tick.localZ(), "tick local Z");
                writeUtf8(output, tick.blockKey());
                output.writeInt(tick.delay());
            }
        });
    }

    private static byte[] capabilityPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.BlockEntityCapabilityReceipt> receipts =
                center.capabilityReceipts();
        return payload(output -> {
            output.writeInt(receipts.size());
            for (Mc263FeaturesRegion.BlockEntityCapabilityReceipt receipt : receipts) {
                writeUnsignedByte(output, receipt.localX(), "capability local X");
                output.writeInt(receipt.blockY());
                writeUnsignedByte(output, receipt.localZ(), "capability local Z");
                output.writeByte(switch (receipt.capability()) {
                    case RANDOMIZABLE_CONTAINER -> 1;
                    case SPAWNER -> 2;
                    case POTENT_SULFUR -> 3;
                    case BRUSHABLE -> 4;
                    case SCULK_CATALYST -> 5;
                    case SCULK_SENSOR -> 6;
                    case SCULK_SHRIEKER -> 7;
                    case BEEHIVE -> 8;
                    case NONE -> throw new IllegalArgumentException(
                            "empty capability receipt");
                });
                writeUtf8(output, receipt.exactState());
            }
        });
    }

    private static byte[] fluidTickPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.ScheduledFluidTick> ticks = center.scheduledFluidTicks();
        return payload(output -> {
            output.writeInt(ticks.size());
            for (Mc263FeaturesRegion.ScheduledFluidTick tick : ticks) {
                writeUnsignedByte(output, tick.localX(), "fluid tick local X");
                output.writeInt(tick.blockY());
                writeUnsignedByte(output, tick.localZ(), "fluid tick local Z");
                writeUtf8(output, tick.fluidKey());
                output.writeInt(tick.delay());
            }
        });
    }

    private static byte[] lootPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.ChestLoot> loot = center.chestLoot();
        return payload(output -> {
            output.writeInt(loot.size());
            for (Mc263FeaturesRegion.ChestLoot chest : loot) {
                writeUnsignedByte(output, chest.localX(), "loot local X");
                output.writeInt(chest.blockY());
                writeUnsignedByte(output, chest.localZ(), "loot local Z");
                output.writeByte(facingCode(chest.facing()));
                writeUtf8(output, chest.lootTable());
                output.writeLong(chest.lootSeed());
            }
        });
    }

    private static byte[] spawnerPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.SpawnerMob> spawners = center.spawnerMobs();
        return payload(output -> {
            output.writeInt(spawners.size());
            for (Mc263FeaturesRegion.SpawnerMob spawner : spawners) {
                writeUnsignedByte(output, spawner.localX(), "spawner local X");
                output.writeInt(spawner.blockY());
                writeUnsignedByte(output, spawner.localZ(), "spawner local Z");
                writeUtf8(output, spawner.entityType());
            }
        });
    }

    private static byte[] ownershipPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.OwnedBlock> owners = center.ownedBlocks();
        return payload(output -> {
            output.writeInt(owners.size());
            for (Mc263FeaturesRegion.OwnedBlock owned : owners) {
                writeUnsignedByte(output, owned.localX(), "owner local X");
                output.writeInt(owned.blockY());
                writeUnsignedByte(output, owned.localZ(), "owner local Z");
                output.writeLong(owned.owner());
            }
        });
    }

    private static byte[] archaeologyPayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.ArchaeologyLoot> archaeology = center.archaeologyLoot();
        return payload(output -> {
            output.writeInt(archaeology.size());
            for (Mc263FeaturesRegion.ArchaeologyLoot loot : archaeology) {
                writeUnsignedByte(output, loot.localX(), "archaeology local X");
                output.writeInt(loot.blockY());
                writeUnsignedByte(output, loot.localZ(), "archaeology local Z");
                writeUtf8(output, loot.lootTable());
                output.writeLong(loot.lootSeed());
            }
        });
    }

    private static byte[] beehivePayload(Mc263FeaturesRegion.CenterSnapshot center)
            throws IOException {
        List<Mc263FeaturesRegion.BeehiveNest> nests = center.beehiveNests();
        return payload(output -> {
            output.writeInt(nests.size());
            for (Mc263FeaturesRegion.BeehiveNest nest : nests) {
                writeUnsignedByte(output, nest.localX(), "beehive local X");
                output.writeInt(nest.blockY());
                writeUnsignedByte(output, nest.localZ(), "beehive local Z");
                output.writeInt(nest.occupants().size());
                for (Mc263FeaturesRegion.BeehiveOccupant occupant : nest.occupants()) {
                    output.writeInt(occupant.ticksInHive());
                }
            }
        });
    }

    /**
     * Project-local loot facing encoding, written as one unsigned byte and append-only.
     *
     * <pre>0=north 1=east 2=south 3=west 4=up 5=down</pre>
     *
     * <p>The horizontal codes {@code 0..3} are the original encoding and keep every already
     * written receipt and final-carrier byte unchanged; {@code 4} and {@code 5} were appended for
     * vanilla's vertical randomizable containers ({@code barrel[facing=down|up]},
     * {@code dispenser[facing=up]}, {@code hopper[facing=down]}). The field is a whole byte, so
     * codes {@code 6..255} remain spare. This table is mirrored by
     * {@link Mc263FinalChunkCodec#FACING_CODES}.</p>
     */
    private static int facingCode(String facing) {
        int code = Mc263FinalChunkCodec.FACING_CODES.indexOf(facing);
        if (code < 0) throw new IllegalArgumentException("invalid chest facing: " + facing);
        return code;
    }

    private static void writeSection(DataOutputStream output, String tag, byte[] payload)
            throws IOException {
        byte[] tagBytes = tag.getBytes(StandardCharsets.US_ASCII);
        if (tagBytes.length != 4) throw new IllegalArgumentException("receipt tag must be 4 bytes");
        output.write(tagBytes);
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) {
            throw new IllegalArgumentException("receipt string exceeds unsigned-16 bytes");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static void writeUnsignedByte(DataOutputStream output, int value, String description)
            throws IOException {
        if (value < 0 || value > 0xff) {
            throw new IllegalArgumentException(description + " exceeds unsigned-8: " + value);
        }
        output.writeByte(value);
    }

    private static void writeUnsignedShort(DataOutputStream output, int value, String description)
            throws IOException {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException(description + " exceeds unsigned-16: " + value);
        }
        output.writeShort(value);
    }

    private static byte[] payload(IoWriter writer) throws IOException {
        return payload(1024, writer);
    }

    /**
     * Writes one section into a stream already sized for it. A section that outgrows the estimate
     * still grows normally, so the size is a cost hint and never a contract.
     */
    private static byte[] payload(int expectedBytes, IoWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(32, expectedBytes));
        DataOutputStream output = new DataOutputStream(bytes);
        writer.write(output);
        output.flush();
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
