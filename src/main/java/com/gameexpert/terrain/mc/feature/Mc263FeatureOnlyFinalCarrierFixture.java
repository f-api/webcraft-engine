package com.gameexpert.terrain.mc.feature;

import java.util.Arrays;
import java.util.Objects;

/**
 * Test-only proof that the dormant feature-only pipeline can reach an {@code MCF263LC} carrier.
 *
 * <p>This is deliberately noncanonical: it uses the structure-empty step 0..10 coordinator and
 * must never be connected to chunk generation, terrain caches, or runtime carrier handoff.</p>
 */
final class Mc263FeatureOnlyFinalCarrierFixture {
    private Mc263FeatureOnlyFinalCarrierFixture() {}

    static Result produce(Mc263PostCarversFeaturesRegionBuilder.RegionInput input,
            Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates predicates,
            Mc263PostprocessResolver.ActivationContext activation) {
        Objects.requireNonNull(input, "post-CARVERS input");
        Objects.requireNonNull(predicates, "heightmap predicates");
        Objects.requireNonNull(activation, "POST activation context");

        byte[] postCarversReceipt = Mc263PostCarversFeaturesRegionBuilder.receipt(input);
        Mc263FeaturesRegion region = Mc263PostCarversFeaturesRegionBuilder.build(input, predicates);
        Mc263FeatureDispatcher.DispatchResult features = Mc263FeaturesStep10Stage.dispatch(
                input.target().worldSeed(), region);
        if (features.scheduleTrace().stream()
                .anyMatch(event -> event.kind() == Mc263FeatureDispatcher.Kind.STRUCTURE)) {
            throw new IllegalStateException("feature-only fixture scheduled a structure");
        }

        byte[] featuresReceipt = features.binaryReceipt();
        Mc263PostprocessResolver.Result post = Mc263PostprocessResolver.resolve(region, activation);
        Mc263FinalChunkCodec.FinalChunk assembled =
                Mc263FinalChunkAssembler.assemble(region.snapshotCenter());
        if (assembled.chunkX() != input.target().chunkX()
                || assembled.chunkZ() != input.target().chunkZ()) {
            throw new IllegalStateException("final carrier target changed during fixture pipeline");
        }
        byte[] carrier = Mc263FinalChunkCodec.encode(assembled);
        byte[] canonical = Mc263FinalChunkCodec.encode(Mc263FinalChunkCodec.decode(carrier));
        if (!Arrays.equals(carrier, canonical)) {
            throw new IllegalStateException("final carrier failed canonical re-encoding");
        }
        return new Result(postCarversReceipt, featuresReceipt, post, carrier);
    }

    record Result(byte[] postCarversReceipt, byte[] featuresReceipt,
                  Mc263PostprocessResolver.Result postprocess, byte[] finalCarrier) {
        Result {
            postCarversReceipt = postCarversReceipt.clone();
            featuresReceipt = featuresReceipt.clone();
            postprocess = Objects.requireNonNull(postprocess, "POST result");
            finalCarrier = finalCarrier.clone();
        }

        @Override public byte[] postCarversReceipt() { return postCarversReceipt.clone(); }
        @Override public byte[] featuresReceipt() { return featuresReceipt.clone(); }
        @Override public byte[] finalCarrier() { return finalCarrier.clone(); }
    }
}
