package com.gameexpert.engine.structure;

import java.util.EnumMap;
import java.util.Map;

/** Executable §4.4 hard/soft/forbidden/envelope rules, scored against every camp kind. */
public final class StructureIdentityRules {
    public static final class Features {
        private final int span;
        private final int parts;
        private final boolean fire;
        private final boolean rest;
        private final boolean work;
        private final boolean storage;
        private final boolean mining;
        private final boolean mineHost;
        private final boolean underground;
        private final int humanTraceKinds;
        private final int traps;
        private final int enclosedRooms;

        public Features(int span, int parts, boolean fire, boolean rest, boolean work,
                boolean storage, boolean mining, boolean mineHost, boolean underground,
                int humanTraceKinds, int traps, int enclosedRooms) {
            this.span = span;
            this.parts = parts;
            this.fire = fire;
            this.rest = rest;
            this.work = work;
            this.storage = storage;
            this.mining = mining;
            this.mineHost = mineHost;
            this.underground = underground;
            this.humanTraceKinds = humanTraceKinds;
            this.traps = traps;
            this.enclosedRooms = enclosedRooms;
        }
    }

    public static final class Evaluation {
        private final boolean hard;
        private final boolean forbidden;
        private final boolean envelope;
        private final double score;

        private Evaluation(boolean hard, boolean forbidden, boolean envelope, double score) {
            this.hard = hard;
            this.forbidden = forbidden;
            this.envelope = envelope;
            this.score = score;
        }

        public boolean hard() { return hard; }
        public boolean forbidden() { return forbidden; }
        public boolean envelope() { return envelope; }
        public double score() { return score; }
    }

    private interface Rule { Evaluation evaluate(Features features); }
    private static final Map<StructureSiteDescriptor.Kind, Rule> RULES = rules();

    private StructureIdentityRules() {}

    public static Evaluation evaluate(StructureSiteDescriptor.Kind kind, Features features) {
        Rule rule = RULES.get(kind);
        if (rule == null) throw new IllegalStateException(kind + " identity rule is not configured");
        return rule.evaluate(features);
    }

    public static boolean confused(StructureSiteDescriptor.Kind actual, Features features) {
        double actualScore = evaluate(actual, features).score();
        double bestOther = -1;
        for (StructureSiteDescriptor.Kind kind : StructureSiteDescriptor.Kind.values()) {
            if (kind != actual && RULES.containsKey(kind)) {
                bestOther = max(bestOther, evaluate(kind, features).score());
            }
        }
        return actualScore < bestOther || actualScore - bestOther < .10;
    }

    public static boolean configured(StructureSiteDescriptor.Kind kind) {
        return RULES.containsKey(kind);
    }

    private static Map<StructureSiteDescriptor.Kind, Rule> rules() {
        Map<StructureSiteDescriptor.Kind, Rule> rules = new EnumMap<>(StructureSiteDescriptor.Kind.class);
        rules.put(StructureSiteDescriptor.Kind.CAMPING_SITE, features -> {
            boolean envelope = between(features.span, 4, 14) && between(features.parts, 2, 7);
            boolean hard = features.fire && features.rest && features.parts >= 2;
            boolean forbidden = features.traps > 0 || features.enclosedRooms > 0 || features.mineHost;
            double score = points(between(features.span, 4, 14), .20)
                    + points(between(features.parts, 2, 7), .15) + points(features.fire, .20)
                    + points(features.rest, .20) + points(!features.work, .15)
                    + points(!features.mineHost, .10);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        rules.put(StructureSiteDescriptor.Kind.MINER_CAMP, features -> {
            int roles = (features.mining ? 1 : 0) + (features.storage ? 1 : 0) + (features.rest ? 1 : 0);
            boolean envelope = between(features.span, 5, 17) && between(features.parts, 3, 13);
            boolean hard = features.mineHost && features.mining && roles >= 2;
            boolean forbidden = features.traps > 0 || features.enclosedRooms > 0 || !features.underground;
            double score = points(between(features.span, 5, 17), .10)
                    + points(between(features.parts, 3, 13), .10) + points(features.mineHost, .25)
                    + points(features.mining, .20) + points(features.work, .10)
                    + points(features.rest, .05) + points(features.storage, .10)
                    + points(features.underground, .10);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        rules.put(StructureSiteDescriptor.Kind.SMALL_RUIN, features -> {
            boolean envelope = between(features.span, 5, 15) && between(features.parts, 3, 12);
            boolean hard = features.enclosedRooms == 0 && !features.fire && !features.mineHost;
            boolean forbidden = features.traps > 1 || features.fire;
            double score = points(envelope, .45) + points(hard, .35)
                    + points(!features.storage && !features.work, .20);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        rules.put(StructureSiteDescriptor.Kind.MEDIUM_RUIN, features -> {
            boolean envelope = between(features.span, 12, 29) && features.parts >= 8;
            boolean hard = features.enclosedRooms >= 2 && !features.fire;
            boolean forbidden = features.mineHost || features.traps > 3;
            double score = points(envelope, .40) + points(hard, .40)
                    + points(features.storage, .20);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        rules.put(StructureSiteDescriptor.Kind.FLOODED_RUIN, features -> {
            boolean envelope = between(features.span, 10, 33) && features.parts >= 6;
            boolean hard = features.underground && features.enclosedRooms >= 1 && !features.fire;
            boolean forbidden = features.mineHost;
            double score = points(envelope, .35) + points(hard, .50)
                    + points(!features.storage, .15);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        rules.put(StructureSiteDescriptor.Kind.TOMB_RUIN, features -> {
            boolean envelope = between(features.span, 7, 19) && between(features.parts, 5, 15);
            boolean hard = features.enclosedRooms == 1 && features.storage && features.rest;
            boolean forbidden = features.fire || features.mineHost;
            double score = points(envelope, .35) + points(hard, .45)
                    + points(features.humanTraceKinds >= 1, .20);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        rules.put(StructureSiteDescriptor.Kind.GENERAL_RUIN, features -> {
            boolean envelope = between(features.span, 8, 27) && features.parts >= 6;
            boolean hard = features.work && !features.fire && !features.mineHost;
            boolean forbidden = features.traps > 3;
            double score = points(envelope, .40) + points(hard, .40)
                    + points(features.humanTraceKinds >= 1, .20);
            return new Evaluation(hard, forbidden, envelope, score);
        });
        return rules;
    }

    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }

    private static double points(boolean condition, double points) {
        return condition ? points : 0;
    }

    private static double max(double one, double two) {
        return one > two ? one : two;
    }
}
