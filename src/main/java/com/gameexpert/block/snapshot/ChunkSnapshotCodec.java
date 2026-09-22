package com.gameexpert.block.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;

/**
 * 청크 스냅샷 binary WebSocket frame 코덱입니다.
 *
 * <p>frame은 GEXS/GEXF format, 선택한 월드의 Minecraft baseline/protocol/terrain fingerprint, payload로
 * 구성됩니다. body의 각 16³ section은 (block type, state) palette와 LSB-first bit-packed palette
 * index로 표현됩니다. 전체 body를 zlib로 한 번 더 압축하므로 바이트 배열/base64 JSON을 전송하지
 * 않습니다.</p>
 */
public final class ChunkSnapshotCodec {

    private static final byte[] MAGIC = {'G', 'E', 'X', 'S'};
    private static final byte[] FRAGMENT_MAGIC = {'G', 'E', 'X', 'F'};
    private static final int FORMAT_VERSION = 3;
    private static final int SNAPSHOT_KIND = 1;
    /** 64 KiB ConcurrentWebSocketSessionDecorator를 넘지 않는 hard per-WebSocket-message bound. */
    public static final int MAX_TRANSPORT_FRAME_BYTES = 48 * 1024;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_CELLS = Blocks.CHUNK_X * Blocks.CHUNK_Z * SECTION_HEIGHT;
    private static final int SECTION_COUNT = Blocks.CHUNK_Y / SECTION_HEIGHT;
    private static final int MAX_BODY_BYTES = 2_000_000;
    /** block ID table의 각 항목에 unsigned byte state 256개를 배정한 조합 공간입니다. */
    private static final int BLOCK_STATE_ID_CAPACITY = Blocks.BLOCK_ID_TABLE_CAPACITY << 8;
    private static final ThreadLocal<EncoderScratch> ENCODER_SCRATCH =
            ThreadLocal.withInitial(EncoderScratch::new);

    private static final java.util.concurrent.ConcurrentMap<WorldGenerationProfile, WireIdentity> WIRE_IDENTITIES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static WireIdentity wireIdentity(WorldGenerationProfile profile) {
        return WIRE_IDENTITIES.computeIfAbsent(WorldGenerationProfiles.requireSupported(profile), WireIdentity::new);
    }

    private static final class WireIdentity {
        private final byte[] BASELINE_ID;
        private final byte[] INPUT_FINGERPRINT;
        private final int protocol;
        private final int HEADER_BYTES;
        private final int FRAGMENT_HEADER_BYTES;
        private final int MAX_FRAGMENT_PAYLOAD_BYTES;
        private WireIdentity(WorldGenerationProfile profile) {
            String id = profile.getBaselineId();
            if (id.isEmpty() || id.length() > 255 || id.chars().anyMatch(c -> c > 127)) {
                throw new IllegalArgumentException("invalid snapshot generation identity");
            }
            BASELINE_ID = id.getBytes(StandardCharsets.US_ASCII);
            INPUT_FINGERPRINT = HexFormat.of().parseHex(profile.getInputFingerprintSha256());
            protocol = profile.getProtocolVersion();
            int identityBytes = 1 + BASELINE_ID.length + 4 + 32;
            HEADER_BYTES = 10 + identityBytes;
            FRAGMENT_HEADER_BYTES = 29 + identityBytes;
            MAX_FRAGMENT_PAYLOAD_BYTES = MAX_TRANSPORT_FRAME_BYTES - FRAGMENT_HEADER_BYTES;
        }
    }

    private ChunkSnapshotCodec() {
    }

    public static byte[] encode(ChunkSnapshot snapshot) {
        return encode(snapshot, WorldGenerationProfiles.newWorldProfile());
    }

    public static byte[] encode(ChunkSnapshot snapshot, WorldGenerationProfile profile) {
        WireIdentity identity = wireIdentity(profile);
        try {
            byte[] body = encodeBody(snapshot);
            byte[] compressed = compress(body);
            ByteArrayOutputStream frame = new ByteArrayOutputStream(identity.HEADER_BYTES + compressed.length);
            frame.write(MAGIC);
            frame.write(FORMAT_VERSION);
            frame.write(SNAPSHOT_KIND);
            writeWireIdentity(frame, identity);
            writeInt(frame, body.length);
            frame.write(compressed);
            return frame.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("청크 스냅샷 인코딩에 실패했습니다.", exception);
        }
    }

    public static ChunkSnapshot decode(byte[] frame) {
        return decode(frame, WorldGenerationProfiles.newWorldProfile());
    }

    public static ChunkSnapshot decode(byte[] frame, WorldGenerationProfile profile) {
        WireIdentity identity = wireIdentity(profile);
        if (frame == null || frame.length < MAGIC.length + 1) {
            throw new IllegalArgumentException("청크 스냅샷 frame이 너무 짧습니다.");
        }
        Cursor header = new Cursor(frame);
        for (byte expected : MAGIC) {
            if (header.readUnsignedByte() != Byte.toUnsignedInt(expected)) {
                throw new IllegalArgumentException("청크 스냅샷 magic이 올바르지 않습니다.");
            }
        }
        if (header.readUnsignedByte() != FORMAT_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 청크 스냅샷 버전입니다.");
        }
        if (frame.length < identity.HEADER_BYTES) {
            throw new IllegalArgumentException("청크 스냅샷 frame이 너무 짧습니다.");
        }
        if (header.readUnsignedByte() != SNAPSHOT_KIND) {
            throw new IllegalArgumentException("지원하지 않는 청크 스냅샷 frame 종류입니다.");
        }
        readAndValidateWireIdentity(header, "청크 스냅샷", identity);
        int bodyLength = header.readInt();
        if (bodyLength < 0 || bodyLength > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("청크 스냅샷 body 길이가 올바르지 않습니다.");
        }
        byte[] compressed = Arrays.copyOfRange(frame, header.position(), frame.length);
        byte[] body = inflate(compressed, bodyLength);
        return decodeBody(body);
    }

    /**
     * 하나의 logical snapshot을 <=48 KiB binary WebSocket frames로 분할합니다. 각 frame은 epoch/chunk
     * identity와 전체 compressed logical-frame 길이를 갖고, 순서대로 재조립한 뒤 {@link #decode(byte[])}
     * 합니다.
     */
    public static List<byte[]> encodeFrames(ChunkSnapshot snapshot) {
        return encodeFrames(snapshot, WorldGenerationProfiles.newWorldProfile());
    }

    public static List<byte[]> encodeFrames(ChunkSnapshot snapshot, WorldGenerationProfile profile) {
        WireIdentity identity = wireIdentity(profile);
        byte[] logical = encode(snapshot, profile);
        int count = Math.max(1, (logical.length + identity.MAX_FRAGMENT_PAYLOAD_BYTES - 1) / identity.MAX_FRAGMENT_PAYLOAD_BYTES);
        if (count > 0xFFFF) throw new IllegalArgumentException("청크 스냅샷 fragment 수가 너무 많습니다.");
        List<byte[]> frames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * identity.MAX_FRAGMENT_PAYLOAD_BYTES;
            int length = Math.min(identity.MAX_FRAGMENT_PAYLOAD_BYTES, logical.length - offset);
            try {
                ByteArrayOutputStream frame = new ByteArrayOutputStream(identity.FRAGMENT_HEADER_BYTES + length);
                frame.write(FRAGMENT_MAGIC);
                frame.write(FORMAT_VERSION);
                writeWireIdentity(frame, identity);
                writeUnsignedShort(frame, index);
                writeUnsignedShort(frame, count);
                writeInt(frame, logical.length);
                writeLong(frame, snapshot.getWorldEpoch());
                writeInt(frame, snapshot.getCx());
                writeInt(frame, snapshot.getCz());
                frame.write(logical, offset, length);
                byte[] bytes = frame.toByteArray();
                if (bytes.length > MAX_TRANSPORT_FRAME_BYTES) {
                    throw new IllegalStateException("청크 스냅샷 frame 크기 상한을 넘었습니다.");
                }
                frames.add(bytes);
            } catch (IOException exception) {
                throw new IllegalStateException("청크 스냅샷 fragment 인코딩에 실패했습니다.", exception);
            }
        }
        return List.copyOf(frames);
    }

    /** 테스트/차기 클라이언트 구현이 사용할 strict ordered fragment 재조립입니다. */
    public static ChunkSnapshot decodeFrames(List<byte[]> frames) {
        return decodeFrames(frames, WorldGenerationProfiles.newWorldProfile());
    }

    public static ChunkSnapshot decodeFrames(List<byte[]> frames, WorldGenerationProfile profile) {
        WireIdentity identity = wireIdentity(profile);
        if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("청크 스냅샷 fragment가 없습니다.");
        FragmentHeader first = parseFragmentHeader(frames.getFirst(), identity);
        if (frames.size() != first.count) throw new IllegalArgumentException("청크 스냅샷 fragment 수가 맞지 않습니다.");
        ByteArrayOutputStream logical = new ByteArrayOutputStream(first.logicalLength);
        for (int index = 0; index < frames.size(); index++) {
            byte[] frame = frames.get(index);
            FragmentHeader header = parseFragmentHeader(frame, identity);
            if (header.index != index || header.count != first.count
                    || header.logicalLength != first.logicalLength || header.worldEpoch != first.worldEpoch
                    || header.cx != first.cx || header.cz != first.cz) {
                throw new IllegalArgumentException("청크 스냅샷 fragment 순서 또는 identity가 맞지 않습니다.");
            }
            logical.write(frame, identity.FRAGMENT_HEADER_BYTES, frame.length - identity.FRAGMENT_HEADER_BYTES);
        }
        if (logical.size() != first.logicalLength) {
            throw new IllegalArgumentException("청크 스냅샷 fragment 재조립 길이가 맞지 않습니다.");
        }
        ChunkSnapshot snapshot = decode(logical.toByteArray(), profile);
        if (snapshot.getWorldEpoch() != first.worldEpoch || snapshot.getCx() != first.cx
                || snapshot.getCz() != first.cz) {
            throw new IllegalArgumentException("청크 스냅샷 fragment header와 body identity가 다릅니다.");
        }
        return snapshot;
    }

    private static byte[] encodeBody(ChunkSnapshot snapshot) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(32_768);
        writeLong(out, snapshot.getWorldEpoch());
        writeInt(out, snapshot.getCx());
        writeInt(out, snapshot.getCz());
        writeLong(out, snapshot.getFromVersion());
        writeLong(out, snapshot.getToVersion());
        out.write(snapshot.surfaceBiomesInternal());
        for (short height : snapshot.surfaceHeightsInternal()) {
            validateSurfaceHeight(height);
            writeShort(out, height);
        }
        out.write(SECTION_COUNT);

        short[] types = snapshot.blockTypesInternal();
        byte[] states = snapshot.blockStatesInternal();
        EncoderScratch scratch = ENCODER_SCRATCH.get();
        for (int section = 0; section < SECTION_COUNT; section++) {
            encodeSection(out, section, types, states, scratch);
        }
        encodeDecoratedPotMotifSidecar(out, snapshot.getDecoratedPotMotif());
        return out.toByteArray();
    }

    private static void encodeSection(ByteArrayOutputStream out, int section,
            short[] types, byte[] states, EncoderScratch scratch) throws IOException {
        int startY = Blocks.MIN_Y + section * SECTION_HEIGHT;
        int stamp = scratch.nextStamp();
        int paletteSize = 0;
        int cursor = 0;
        for (int y = startY; y < startY + SECTION_HEIGHT; y++) {
            for (int z = 0; z < Blocks.CHUNK_Z; z++) {
                for (int x = 0; x < Blocks.CHUNK_X; x++) {
                    int cell = Blocks.blockIndex(x, y, z);
                    int blockType = Short.toUnsignedInt(types[cell]);
                    int value = (blockType << 8) | Byte.toUnsignedInt(states[cell]);
                    if (value >= BLOCK_STATE_ID_CAPACITY) validateBlockType(blockType);
                    int slot = (value * 0x9E3779B9) >>> (32 - EncoderScratch.SLOT_BITS);
                    while (scratch.stamps[slot] == stamp && scratch.keys[slot] != value) {
                        slot = (slot + 1) & (EncoderScratch.SLOTS - 1);
                    }
                    int paletteIndex;
                    if (scratch.stamps[slot] != stamp) {
                        validateBlockType(blockType);
                        paletteIndex = paletteSize;
                        scratch.stamps[slot] = stamp;
                        scratch.keys[slot] = value;
                        scratch.reverse[slot] = paletteIndex;
                        scratch.palette[paletteSize++] = value;
                    } else {
                        paletteIndex = scratch.reverse[slot];
                    }
                    scratch.indexes[cursor++] = paletteIndex;
                }
            }
        }
        int bits = bitsFor(paletteSize);
        byte[] packed = pack(scratch.indexes, bits);
        out.write(section);
        writeUnsignedShort(out, paletteSize);
        out.write(bits);
        writeUnsignedShort(out, packed.length);
        for (int index = 0; index < paletteSize; index++) {
            int value = scratch.palette[index];
            writeUnsignedShort(out, value >>> 8);
            out.write(value & 0xFF);
        }
        out.write(packed);
    }

    private static ChunkSnapshot decodeBody(byte[] body) {
        Cursor cursor = new Cursor(body);
        long worldEpoch = cursor.readLong();
        int cx = cursor.readInt();
        int cz = cursor.readInt();
        long fromVersion = cursor.readLong();
        long toVersion = cursor.readLong();
        byte[] biomes = cursor.readBytes(Blocks.CHUNK_X * Blocks.CHUNK_Z);
        short[] heights = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        for (int i = 0; i < heights.length; i++) {
            short height = cursor.readShort();
            validateSurfaceHeight(height);
            heights[i] = height;
        }
        if (cursor.readUnsignedByte() != SECTION_COUNT) {
            throw new IllegalArgumentException("청크 스냅샷 section 수가 올바르지 않습니다.");
        }
        short[] types = new short[Blocks.CHUNK_BLOCKS];
        byte[] states = new byte[Blocks.CHUNK_BLOCKS];
        for (int expectedSection = 0; expectedSection < SECTION_COUNT; expectedSection++) {
            int section = cursor.readUnsignedByte();
            if (section != expectedSection) {
                throw new IllegalArgumentException("청크 스냅샷 section 순서가 올바르지 않습니다.");
            }
            int paletteSize = cursor.readUnsignedShort();
            int bits = cursor.readUnsignedByte();
            int packedLength = cursor.readUnsignedShort();
            if (paletteSize < 1 || paletteSize > SECTION_CELLS || bits != bitsFor(paletteSize)
                    || packedLength != packedLength(bits)) {
                throw new IllegalArgumentException("청크 스냅샷 section 형식이 올바르지 않습니다.");
            }
            int[] palette = new int[paletteSize];
            for (int index = 0; index < paletteSize; index++) {
                int blockType = cursor.readUnsignedShort();
                validateBlockType(blockType);
                palette[index] = (blockType << 8) | cursor.readUnsignedByte();
            }
            int[] indexes = unpack(cursor.readBytes(packedLength), bits);
            int startY = Blocks.MIN_Y + section * SECTION_HEIGHT;
            int index = 0;
            for (int y = startY; y < startY + SECTION_HEIGHT; y++) {
                for (int z = 0; z < Blocks.CHUNK_Z; z++) {
                    for (int x = 0; x < Blocks.CHUNK_X; x++) {
                        int paletteIndex = indexes[index++];
                        if (paletteIndex < 0 || paletteIndex >= paletteSize) {
                            throw new IllegalArgumentException("청크 스냅샷 palette index가 범위를 벗어났습니다.");
                        }
                        int value = palette[paletteIndex];
                        int cell = Blocks.blockIndex(x, y, z);
                        types[cell] = (short) (value >>> 8);
                        states[cell] = (byte) value;
                    }
                }
            }
        }
        ChunkSnapshot.DecoratedPotMotif decoratedPotMotif = decodeDecoratedPotMotifSidecar(cursor, types);
        if (!cursor.atEnd()) throw new IllegalArgumentException("청크 스냅샷 body 끝에 알 수 없는 바이트가 있습니다.");
        return ChunkSnapshot.takeOwnership(worldEpoch, cx, cz, fromVersion, toVersion,
                biomes, heights, types, states, decoratedPotMotif);
    }

    private static void encodeDecoratedPotMotifSidecar(ByteArrayOutputStream out,
            ChunkSnapshot.DecoratedPotMotif motif) throws IOException {
        if (motif.isEmpty()) {
            out.write(0);
            return;
        }
        List<String> keys = motif.keys();
        int[] packedIndexes = motif.packedIndexes();
        byte[] faces = motif.faces();
        out.write(1);
        out.write(keys.size());
        for (String key : keys) {
            byte[] bytes = key.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length < 1 || bytes.length > 64 || !key.equals(new String(bytes, StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("장식 항아리 키 ASCII 길이가 올바르지 않습니다.");
            }
            out.write(bytes.length);
            out.write(bytes);
        }
        writeInt(out, packedIndexes.length);
        for (int pot = 0; pot < packedIndexes.length; pot++) {
            writeInt(out, packedIndexes[pot]);
            for (int face = 0; face < 4; face++) out.write(faces[pot * 4 + face]);
        }
    }

    private static ChunkSnapshot.DecoratedPotMotif decodeDecoratedPotMotifSidecar(Cursor cursor,
            short[] blockTypes) {
        int present = cursor.readUnsignedByte();
        if (present == 0) return ChunkSnapshot.DecoratedPotMotif.empty();
        if (present != 1) throw new IllegalArgumentException("장식 항아리 sidecar presence가 올바르지 않습니다.");
        int keyCount = cursor.readUnsignedByte();
        if (keyCount < 1 || keyCount > ChunkSnapshot.DecoratedPotMotif.declaration().size()) {
            throw new IllegalArgumentException("장식 항아리 sidecar 키 수가 올바르지 않습니다.");
        }
        List<String> keys = new ArrayList<>(keyCount);
        for (int index = 0; index < keyCount; index++) {
            int length = cursor.readUnsignedByte();
            if (length < 1 || length > 64) {
                throw new IllegalArgumentException("장식 항아리 sidecar 키 길이가 올바르지 않습니다.");
            }
            byte[] bytes = cursor.readBytes(length);
            for (byte value : bytes) {
                int ascii = Byte.toUnsignedInt(value);
                if (ascii < 0x20 || ascii > 0x7e) {
                    throw new IllegalArgumentException("장식 항아리 sidecar 키가 ASCII가 아닙니다.");
                }
            }
            keys.add(new String(bytes, StandardCharsets.US_ASCII));
        }
        int potCount = cursor.readInt();
        if (potCount < 1 || potCount > Blocks.CHUNK_BLOCKS) {
            throw new IllegalArgumentException("장식 항아리 sidecar 항아리 수가 올바르지 않습니다.");
        }
        int[] packedIndexes = new int[potCount];
        byte[] faces = new byte[Math.multiplyExact(potCount, 4)];
        for (int pot = 0; pot < potCount; pot++) {
            int packed = cursor.readInt();
            if (packed < 0 || packed >= Blocks.CHUNK_BLOCKS) {
                throw new IllegalArgumentException("장식 항아리 sidecar packed 칸이 올바르지 않습니다.");
            }
            packedIndexes[pot] = packed;
            for (int face = 0; face < 4; face++) {
                int keyIndex = cursor.readUnsignedByte();
                if (keyIndex >= keyCount) {
                    throw new IllegalArgumentException("장식 항아리 sidecar 면 키 index가 올바르지 않습니다.");
                }
                faces[pot * 4 + face] = (byte) keyIndex;
            }
        }
        return new ChunkSnapshot.DecoratedPotMotif(keys, packedIndexes, faces).validatedFor(blockTypes);
    }

    private static void validateSurfaceHeight(int height) {
        if (height < Blocks.MIN_Y - 1 || height > Blocks.MAX_Y) {
            throw new IllegalArgumentException("청크 스냅샷 지표 높이가 범위를 벗어났습니다.");
        }
    }

    private static void validateBlockType(int blockType) {
        if (!Blocks.isWorldBlockId(blockType)) {
            throw new IllegalArgumentException("청크 스냅샷 블록 ID가 등록되지 않았습니다.");
        }
    }

    private static byte[] compress(byte[] source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(source.length / 3);
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out,
                new Deflater(Deflater.BEST_SPEED, false))) {
            deflater.write(source);
        }
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] source, int expectedLength) {
        Inflater inflater = new Inflater(false);
        try {
            inflater.setInput(source);
            byte[] result = new byte[expectedLength];
            int written = 0;
            while (!inflater.finished()) {
                if (written == expectedLength) {
                    throw new IllegalArgumentException("청크 스냅샷 압축 해제 길이가 상한을 넘었습니다.");
                }
                int read = inflater.inflate(result, written, expectedLength - written);
                written += read;
                if (inflater.needsDictionary()) {
                    throw new IllegalArgumentException("청크 스냅샷 zlib 사전은 허용되지 않습니다.");
                }
                if (read == 0) {
                    if (inflater.needsInput()) {
                        throw new IllegalArgumentException("청크 스냅샷 zlib stream이 끝나지 않았습니다.");
                    }
                    throw new IllegalArgumentException("청크 스냅샷 zlib stream이 올바르지 않습니다.");
                }
            }
            if (written != expectedLength) {
                throw new IllegalArgumentException("청크 스냅샷 압축 해제 길이가 올바르지 않습니다.");
            }
            if (inflater.getRemaining() != 0) {
                throw new IllegalArgumentException("청크 스냅샷 zlib stream 끝에 바이트가 있습니다.");
            }
            return result;
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("청크 스냅샷 zlib stream이 올바르지 않습니다.", exception);
        } finally {
            inflater.end();
        }
    }

    private static FragmentHeader parseFragmentHeader(byte[] frame, WireIdentity identity) {
        if (frame == null || frame.length < FRAGMENT_MAGIC.length + 1
                || frame.length > MAX_TRANSPORT_FRAME_BYTES) {
            throw new IllegalArgumentException("청크 스냅샷 fragment 길이가 올바르지 않습니다.");
        }
        Cursor cursor = new Cursor(frame);
        for (byte expected : FRAGMENT_MAGIC) {
            if (cursor.readUnsignedByte() != Byte.toUnsignedInt(expected)) {
                throw new IllegalArgumentException("청크 스냅샷 fragment magic이 올바르지 않습니다.");
            }
        }
        if (cursor.readUnsignedByte() != FORMAT_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 청크 스냅샷 fragment 버전입니다.");
        }
        if (frame.length < identity.FRAGMENT_HEADER_BYTES) {
            throw new IllegalArgumentException("청크 스냅샷 fragment 길이가 올바르지 않습니다.");
        }
        readAndValidateWireIdentity(cursor, "청크 스냅샷 fragment", identity);
        int index = cursor.readUnsignedShort();
        int count = cursor.readUnsignedShort();
        int logicalLength = cursor.readInt();
        long worldEpoch = cursor.readLong();
        int cx = cursor.readInt();
        int cz = cursor.readInt();
        if (count < 1 || index >= count || logicalLength < identity.HEADER_BYTES || logicalLength > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("청크 스냅샷 fragment header가 올바르지 않습니다.");
        }
        int expectedCount = (logicalLength + identity.MAX_FRAGMENT_PAYLOAD_BYTES - 1) / identity.MAX_FRAGMENT_PAYLOAD_BYTES;
        if (count != expectedCount) {
            throw new IllegalArgumentException("청크 스냅샷 fragment 수가 올바르지 않습니다.");
        }
        int payloadLength = frame.length - identity.FRAGMENT_HEADER_BYTES;
        int expectedPayloadLength = index < count - 1
                ? identity.MAX_FRAGMENT_PAYLOAD_BYTES
                : logicalLength - identity.MAX_FRAGMENT_PAYLOAD_BYTES * (count - 1);
        if (payloadLength != expectedPayloadLength) {
            throw new IllegalArgumentException("청크 스냅샷 fragment payload 길이가 올바르지 않습니다.");
        }
        return new FragmentHeader(index, count, logicalLength, worldEpoch, cx, cz);
    }

    private static int bitsFor(int paletteSize) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    private static int packedLength(int bits) {
        return (SECTION_CELLS * bits + 7) / 8;
    }

    private static void writeWireIdentity(ByteArrayOutputStream out, WireIdentity identity) throws IOException {
        if (identity.BASELINE_ID.length > 0xFF || identity.INPUT_FINGERPRINT.length != 32) {
            throw new IllegalStateException("청크 스냅샷 wire identity가 올바르지 않습니다.");
        }
        out.write(identity.BASELINE_ID.length);
        out.write(identity.BASELINE_ID);
        writeInt(out, identity.protocol);
        out.write(identity.INPUT_FINGERPRINT);
    }

    private static void readAndValidateWireIdentity(Cursor cursor, String label, WireIdentity identity) {
        int baselineLength = cursor.readUnsignedByte();
        if (baselineLength != identity.BASELINE_ID.length || !cursor.matches(identity.BASELINE_ID)
                || cursor.readInt() != identity.protocol
                || !cursor.matches(identity.INPUT_FINGERPRINT)) {
            throw new IllegalArgumentException(label + " wire identity가 현재 Minecraft baseline과 다릅니다.");
        }
    }

    private static byte[] pack(int[] indexes, int bits) {
        byte[] result = new byte[packedLength(bits)];
        long accumulator = 0;
        int heldBits = 0;
        int out = 0;
        for (int value : indexes) {
            accumulator |= (long) value << heldBits;
            heldBits += bits;
            while (heldBits >= 8) {
                result[out++] = (byte) accumulator;
                accumulator >>>= 8;
                heldBits -= 8;
            }
        }
        if (heldBits > 0) result[out] = (byte) accumulator;
        return result;
    }

    private static int[] unpack(byte[] packed, int bits) {
        int[] result = new int[SECTION_CELLS];
        long accumulator = 0;
        int heldBits = 0;
        int in = 0;
        int mask = (1 << bits) - 1;
        for (int i = 0; i < result.length; i++) {
            while (heldBits < bits) {
                accumulator |= (long) Byte.toUnsignedInt(packed[in++]) << heldBits;
                heldBits += 8;
            }
            result[i] = (int) accumulator & mask;
            accumulator >>>= bits;
            heldBits -= bits;
        }
        return result;
    }

    private static void writeUnsignedShort(ByteArrayOutputStream out, int value) throws IOException {
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException("unsigned short 범위 밖입니다.");
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeShort(ByteArrayOutputStream out, short value) throws IOException {
        writeUnsignedShort(out, Short.toUnsignedInt(value));
    }

    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) throws IOException {
        for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (value >>> shift));
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int position;

        private Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        private int position() {
            return position;
        }

        private boolean atEnd() {
            return position == bytes.length;
        }

        private int readUnsignedByte() {
            require(1);
            return Byte.toUnsignedInt(bytes[position++]);
        }

        private short readShort() {
            return (short) readUnsignedShort();
        }

        private int readUnsignedShort() {
            return (readUnsignedByte() << 8) | readUnsignedByte();
        }

        private int readInt() {
            return (readUnsignedByte() << 24) | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8) | readUnsignedByte();
        }

        private long readLong() {
            long value = 0;
            for (int shift = 56; shift >= 0; shift -= 8) value |= (long) readUnsignedByte() << shift;
            return value;
        }

        private byte[] readBytes(int length) {
            require(length);
            byte[] result = Arrays.copyOfRange(bytes, position, position + length);
            position += length;
            return result;
        }

        private boolean matches(byte[] expected) {
            require(expected.length);
            boolean matches = true;
            for (byte value : expected) matches &= bytes[position++] == value;
            return matches;
        }

        private void require(int length) {
            if (length < 0 || position + length > bytes.length) {
                throw new IllegalArgumentException("청크 스냅샷 body가 중간에 끝났습니다.");
            }
        }
    }

    /**
     * 인코더 호출 스레드가 재사용하는 primitive scratch입니다. stamp가 같은 section에서만 slot이
     * 유효하므로 매 section마다 표를 지울 필요가 없고, palette 배열 순서는 첫 등장 순서 그대로입니다.
     * 한 section의 서로 다른 값은 최대 4,096개이므로 8,192칸 open-addressing 표로 충분합니다
     * (block-state 조합 공간 전체를 직접 색인하면 스레드마다 8MB가 듭니다).
     */
    private static final class EncoderScratch {
        private static final int SLOT_BITS = 13;
        private static final int SLOTS = 1 << SLOT_BITS;
        private final int[] keys = new int[SLOTS];
        private final int[] reverse = new int[SLOTS];
        private final int[] stamps = new int[SLOTS];
        private final int[] palette = new int[SECTION_CELLS];
        private final int[] indexes = new int[SECTION_CELLS];
        private int stamp;

        private int nextStamp() {
            stamp++;
            if (stamp == 0) {
                Arrays.fill(stamps, 0);
                stamp = 1;
            }
            return stamp;
        }
    }

    private static final class FragmentHeader {
        private final int index;
        private final int count;
        private final int logicalLength;
        private final long worldEpoch;
        private final int cx;
        private final int cz;

        private FragmentHeader(int index, int count, int logicalLength, long worldEpoch, int cx, int cz) {
            this.index = index;
            this.count = count;
            this.logicalLength = logicalLength;
            this.worldEpoch = worldEpoch;
            this.cx = cx;
            this.cz = cz;
        }
    }
}
