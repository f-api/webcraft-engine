package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Generated Ruined Portal template-processor rows of
 * {@code exact-state-production-closure-v1.json}.
 *
 * <p>Derivation: 26.3 builds a ruined portal by running the 13 pinned templates through the
 * {@code minecraft:ruined_portal} processor list and then settling the placed state against the
 * fluid already at the position. The tranche is exactly that pipeline's emittable surface, as
 * {@code Mc263RuinedPortalProductionExecutor#processorStateClosure} enumerates it: the pinned
 * template palette, the rule processor (gold rot, lava to netherrack/magma, netherrack to magma),
 * the block-age processor (stone brick rot to mossy/cracked bricks, stairs to mossy stairs or
 * slabs, and {@code withPropertiesOf} swaps of every slab and wall to the mossy stone brick
 * variant, so {@code type}, {@code waterlogged} and the wall connections survive the swap), the
 * water settlement {@code StructureTemplate} applies to a waterloggable placed state — a slab
 * accepts water only outside {@code type=double}, matching {@code SlabBlock.canPlaceLiquid} — and
 * finally the placement rotation and mirror. This tranche appends the
 * {@value #AUTHENTICATED_STATE_COUNT} surface rows the closure did not already carry, append-only
 * after the sculk-patch tranche. Every row is a released 26.3 block state of a block the catalog
 * already codes, so the tranche claims no new block id.</p>
 */
final class Mc263ExactStateProductionClosureRuinedPortalData {
    static final int AUTHENTICATED_STATE_COUNT = 24;
    static final int RECEIPT_BASE_STATE_COUNT = 3_203;
    static final int RECEIPT_POST_STATE_COUNT = 1_812;
    static final int RECEIPT_CONFIGURED_FEATURE_STATE_COUNT = 130;
    static final String RECEIPT_SHA256 =
            "8da716044ae5b5321119b834ad7cb36fc1414f62cb31886a3d27cde34cb4cfe9";
    private static final String GZIP_BASE64 = "H4sIAAAAAAAC/72TXQ6DIBCE33sWTtCEk5jGgG4tKbIG1hhvX8W0Nm2a4s/6RoCdb2cWauOg8OpK58L3xlU56mBKo9ypfp3UGEKfB0IHufamuO"
            + "fBKp1R34DUSIS16BSBt1hVUMqrsgEu6+vJt8nlJbbawno8YbNf8f/GO2VtBiqQtNgJh55u0g3HImD7XLZNFPpuS3QwFI53eCDTxlJGlJsgI47H"
            + "yDLIZiNvaY04HiMJkD2NpIxk6IMfcowVGjSYKPNU+PI6ZPIzZHtaowL/5H9SNucVldkfcQplnZWozP4fF1I+rTwAe6TCxW0IAAA=";

    private Mc263ExactStateProductionClosureRuinedPortalData() {}

    static List<String> states() {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(GZIP_BASE64)))) {
            List<String> states = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines().toList();
            if (states.size() != AUTHENTICATED_STATE_COUNT) {
                throw new ExceptionInInitializerError(
                        "authenticated ruined-portal state row count drift");
            }
            return states;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
