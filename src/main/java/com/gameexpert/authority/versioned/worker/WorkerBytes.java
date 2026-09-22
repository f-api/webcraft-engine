package com.gameexpert.authority.versioned.worker;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Response buffer sized up front, so multi-megabyte carrier frames are neither regrown nor copied out. */
final class WorkerBytes extends ByteArrayOutputStream {
    WorkerBytes(long expected) {
        super((int) Math.max(64, Math.min(expected, 64L * 1024 * 1024 + 4096)));
    }

    /** The written bytes; the internal array itself when the estimate was exact. */
    byte[] exact() {
        return count == buf.length ? buf : Arrays.copyOf(buf, count);
    }
}
