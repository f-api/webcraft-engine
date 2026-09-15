package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;

/** Shared exact configured-feature body for the pinned {@code DiskFeature}. */
public final class Mc263DiskFeatureKernel {
    private Mc263DiskFeatureKernel() {
    }

    public interface WorldAccess {
        String blockState(int blockX, int blockY, int blockZ);

        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state, int flags);

        boolean tryMarkPosForPostProcessing(int blockX, int blockY, int blockZ);
    }

    @FunctionalInterface
    public interface Target {
        Match test(String blockState);
    }

    @FunctionalInterface
    public interface StateProvider {
        String state(WorldgenRandom random, int blockX, int blockY, int blockZ);
    }

    public interface Events {
        boolean enabled();

        void radius(int radius);

        void column(int blockX, int blockZ);

        void cell(int blockX, int blockY, int blockZ, int targetId, boolean accepted,
                int flags, boolean retained);

        void post(int blockX, int blockY, int blockZ, boolean retained);

        static Events disabled() {
            return DisabledEvents.INSTANCE;
        }
    }

    public record Match(int id, boolean accepted) {
    }

    public record Result(boolean placed, int attemptedWrites, int retainedWrites,
                         int attemptedPostProcessMarks, int retainedPostProcessMarks) {
        public Result {
            if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                    || attemptedPostProcessMarks < 0 || retainedPostProcessMarks < 0
                    || retainedPostProcessMarks > attemptedPostProcessMarks) {
                throw new IllegalArgumentException("invalid disk kernel counts");
            }
            if (!placed && (attemptedWrites != 0 || attemptedPostProcessMarks != 0)) {
                throw new IllegalArgumentException("unplaced disk cannot have attempts");
            }
        }
    }

    /**
     * Executes the official X-fast/Z-next, top-down configured disk body. Rejected region writes
     * remain attempts and do not change success or contiguous-run post-processing semantics.
     */
    public static Result place(WorldgenRandom random, int originX, int originY, int originZ,
            int minimumRadius, int maximumRadius, int halfHeight, int flags, WorldAccess world,
            Target target, StateProvider provider, Events events) {
        if (random == null || world == null || target == null || provider == null
                || events == null) {
            throw new IllegalArgumentException("disk kernel collaborators are required");
        }
        if (minimumRadius < 0 || maximumRadius < minimumRadius || halfHeight < 0) {
            throw new IllegalArgumentException("invalid disk kernel dimensions");
        }

        int radius = minimumRadius + random.nextInt(maximumRadius - minimumRadius + 1);
        boolean tracing = events.enabled();
        if (tracing) events.radius(radius);
        int attemptedWrites = 0;
        int retainedWrites = 0;
        int attemptedMarks = 0;
        int retainedMarks = 0;
        boolean placed = false;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = originX + dx;
                int z = originZ + dz;
                if (tracing) events.column(x, z);
                boolean previousAccepted = false;
                for (int y = originY + halfHeight; y >= originY - halfHeight; y--) {
                    Match match = target.test(world.blockState(x, y, z));
                    if (match == null) throw new IllegalArgumentException("null target match");
                    boolean retained = false;
                    if (match.accepted()) {
                        String state = provider.state(random, x, y, z);
                        if (state == null) {
                            if (tracing) {
                                events.cell(x, y, z, match.id(), true, flags, false);
                            }
                            continue;
                        }
                        attemptedWrites++;
                        retained = world.trySetBlockState(x, y, z, state, flags);
                        if (retained) retainedWrites++;
                        placed = true;
                    }
                    if (tracing) {
                        events.cell(x, y, z, match.id(), match.accepted(), flags, retained);
                    }
                    if (match.accepted() && !previousAccepted) {
                        for (int offset = 1; offset <= 2; offset++) {
                            int markY = y + offset;
                            if (isAir(blockKey(world.blockState(x, markY, z)))) break;
                            attemptedMarks++;
                            boolean markRetained =
                                    world.tryMarkPosForPostProcessing(x, markY, z);
                            if (markRetained) retainedMarks++;
                            if (tracing) events.post(x, markY, z, markRetained);
                        }
                    }
                    previousAccepted = match.accepted();
                }
            }
        }
        return new Result(placed, attemptedWrites, retainedWrites, attemptedMarks,
                retainedMarks);
    }

    /** Exact block key used by the configured target and air checks. */
    public static String blockKey(String state) {
        if (state == null || !state.startsWith("minecraft:")
                || state.length() == "minecraft:".length()) {
            throw new IllegalArgumentException(
                    "exact minecraft namespaced block state is required: " + state);
        }
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static boolean isAir(String block) {
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:void_air");
    }

    private enum DisabledEvents implements Events {
        INSTANCE;

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void radius(int radius) {
            throw new AssertionError("disabled disk kernel events emitted");
        }

        @Override
        public void column(int blockX, int blockZ) {
            throw new AssertionError("disabled disk kernel events emitted");
        }

        @Override
        public void cell(int blockX, int blockY, int blockZ, int targetId, boolean accepted,
                int flags, boolean retained) {
            throw new AssertionError("disabled disk kernel events emitted");
        }

        @Override
        public void post(int blockX, int blockY, int blockZ, boolean retained) {
            throw new AssertionError("disabled disk kernel events emitted");
        }
    }
}
