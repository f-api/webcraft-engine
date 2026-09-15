package com.gameexpert.engine.mob;

/**
 * [MOB-LOOK] Vanilla {@code Panda.Gene} (26.3-snapshot-7 javap/vineflower): the displayed variant of a
 * panda is {@code Gene.getVariantFromGenes(main, hidden)} — a dominant main gene shows itself, a recessive
 * one (brown, weak) only when the hidden gene matches, else normal. A spawned panda rolls both genes with
 * {@code Gene.getRandom}: {@code nextInt(16)} → 0 lazy, 1 worried, 2 playful, 4 aggressive, 3 and 5..8
 * weak, 9..10 brown, 11..15 normal.
 *
 * <p>The authorities keep the variant as the mob variant string and draw it with the existing
 * deterministic coordinate hash over the 256 equally likely (main, hidden) rolls, main-major — the same
 * displayed-variant distribution as vanilla without an RNG draw. The standalone twin is the panda row of
 * {@code StandaloneMobRuntime.deterministicMobVariant}. Saves from before the variant existed read as
 * {@code normal}.
 */
public final class PandaGenes {
    private static final String[] GENE_BY_ROLL = {
        "lazy", "worried", "playful", "weak", "aggressive", "weak", "weak", "weak", "weak",
        "brown", "brown", "normal", "normal", "normal", "normal", "normal",
    };

    private PandaGenes() {}

    static boolean recessive(String gene) {
        return "brown".equals(gene) || "weak".equals(gene);
    }

    /** {@code Gene.getVariantFromGenes}. */
    static String displayed(String main, String hidden) {
        return recessive(main) ? (main.equals(hidden) ? main : "normal") : main;
    }

    /** The 256 (main, hidden) roll pairs, main-major, as displayed variants. */
    static String[] variantDistribution() {
        String[] out = new String[GENE_BY_ROLL.length * GENE_BY_ROLL.length];
        for (int main = 0; main < GENE_BY_ROLL.length; main++) {
            for (int hidden = 0; hidden < GENE_BY_ROLL.length; hidden++) {
                out[main * GENE_BY_ROLL.length + hidden] = displayed(GENE_BY_ROLL[main], GENE_BY_ROLL[hidden]);
            }
        }
        return out;
    }
}
