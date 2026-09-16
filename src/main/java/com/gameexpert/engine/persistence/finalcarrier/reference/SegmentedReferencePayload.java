package com.gameexpert.engine.persistence.finalcarrier.reference;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Physical pages preserve the exact legacy logical stream and its hashes. */
public final class SegmentedReferencePayload {
    public static final int PAGE_BYTES = 1024 * 1024;
    private SegmentedReferencePayload() { }
    @FunctionalInterface public interface Writer { void write(OutputStream output) throws IOException; }

    public static boolean segmented(byte[] payload) {
        return payload != null && payload.length == 49 && payload[4] == 2;
    }

    public static byte[] write(int magic, Writer writer, Consumer<byte[]> sink) {
        MessageDigest digest = digest();
        class Pages extends OutputStream {
            final byte[] buffer = new byte[PAGE_BYTES];
            int used;
            int count;
            long length;
            @Override public void write(int value) {
                buffer[used++] = (byte) value;
                digest.update((byte) value);
                length++;
                if (used == buffer.length) emit();
            }
            @Override public void write(byte[] bytes, int offset, int size) {
                digest.update(bytes, offset, size);
                length = Math.addExact(length, size);
                while (size > 0) {
                    int copy = Math.min(size, buffer.length - used);
                    System.arraycopy(bytes, offset, buffer, used, copy);
                    used += copy; offset += copy; size -= copy;
                    if (used == buffer.length) emit();
                }
            }
            void emit() {
                if (used == 0) return;
                sink.accept(Arrays.copyOf(buffer, used));
                count = Math.incrementExact(count);
                used = 0;
            }
        }
        Pages pages = new Pages();
        try {
            writer.write(pages);
            pages.emit();
            ByteArrayOutputStream marker = new ByteArrayOutputStream(49);
            DataOutputStream output = new DataOutputStream(marker);
            output.writeInt(magic); output.writeByte(2); output.writeInt(pages.count);
            output.writeLong(pages.length); output.write(digest.digest());
            return marker.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot encode reference pages", failure);
        }
    }

    public static InputStream open(byte[] marker, IntFunction<byte[]> loader) {
        if (!segmented(marker)) return new ByteArrayInputStream(marker);
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(marker));
            input.readInt(); input.readByte();
            int count = input.readInt(); long length = input.readLong(); byte[] expected = input.readNBytes(32);
            if (count <= 0 || length < 9 || (length - 1) / PAGE_BYTES + 1 != count) {
                throw new IOException("invalid reference page envelope");
            }
            return new InputStream() {
                final MessageDigest digest = digest();
                byte[] page = new byte[0];
                int offset;
                int number;
                long consumed;
                boolean verified;
                @Override public int read() throws IOException {
                    byte[] single = new byte[1];
                    return read(single, 0, 1) == -1 ? -1 : single[0] & 255;
                }
                @Override public int read(byte[] target, int start, int requested) throws IOException {
                    java.util.Objects.checkFromIndexSize(start, requested, target.length);
                    if (requested == 0) return 0;
                    if (offset == page.length) {
                        if (number == count) {
                            if (!verified && (consumed != length || !MessageDigest.isEqual(expected, digest.digest()))) {
                                throw new IOException("reference page digest differs");
                            }
                            verified = true;
                            return -1;
                        }
                        page = loader.apply(number++);
                        long remaining = length - consumed;
                        int expectedLength = (int) Math.min(PAGE_BYTES, remaining);
                        if (page == null || page.length != expectedLength) throw new IOException("reference page missing or truncated");
                        offset = 0;
                    }
                    int size = Math.min(requested, page.length - offset);
                    System.arraycopy(page, offset, target, start, size);
                    digest.update(page, offset, size); offset += size; consumed += size;
                    return size;
                }
            };
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid paged reference payload", failure);
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
