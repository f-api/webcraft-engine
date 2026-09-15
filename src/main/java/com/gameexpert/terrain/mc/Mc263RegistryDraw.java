package com.gameexpert.terrain.mc;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Structural registry-draw primitive shared by every authenticated variant/index draw.
 *
 * <p>A registry draw is a uniform draw over an <em>authenticated table</em> followed by an index
 * into that same table. Writing the bound as a literal ({@code nextInt(11)}) and the table as a
 * separate declaration lets the two drift apart silently: the bound stays 11 while the table gains
 * or loses a row, and the defect surfaces only as an unauthenticated index at runtime — or, worse,
 * as a byte divergence against the official oracle.
 *
 * <p>{@link #uniform} makes that class impossible by construction: the caller never states a
 * bound. The bound is derived from {@code authenticated.size()} and handed to the draw operator, so
 * bound and table cannot disagree. There is deliberately no constant-bound entry point here; the
 * raw {@code nextInt(bound)} of the RNG layer remains for genuine non-registry draws (rotations by
 * ordinal arithmetic, counts, offsets, probabilities) and must never be used to index an
 * authenticated table.
 *
 * <p>Rust mirror: {@code mc_registry_draw_263::uniform}.
 */
public final class Mc263RegistryDraw {

    private Mc263RegistryDraw() {}

    /**
     * Draws one row uniformly from {@code authenticated} in registration order.
     *
     * @param authenticated the authenticated table, in registration order; its size is the draw
     *     bound and is never restated by the caller
     * @param what the draw's name, used verbatim in refusal messages
     * @param draw the RNG's {@code nextInt} equivalent; it receives the derived bound, so the
     *     consumed RNG operation is byte-identical to the previous literal-bound call whenever the
     *     table size equals the previous constant
     */
    public static <T> T uniform(List<T> authenticated, String what, IntUnaryOperator draw) {
        if (authenticated.isEmpty()) {
            throw new IllegalArgumentException(what + " registry table is empty");
        }
        int index = draw.applyAsInt(authenticated.size());
        if (index < 0 || index >= authenticated.size()) {
            throw new IllegalArgumentException("unauthenticated " + what + " index: " + index);
        }
        return authenticated.get(index);
    }
}
