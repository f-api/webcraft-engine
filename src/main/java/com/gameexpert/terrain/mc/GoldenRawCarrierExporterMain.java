package com.gameexpert.terrain.mc;

import com.gameexpert.terrain.CanonicalPostprocessActivationContext;
import com.gameexpert.terrain.Mc263BaseHeightSampler;
import com.gameexpert.terrain.Mc263FeaturesRegionBridge;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalGenerationProduct;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars.IntegratedBuildReceipt;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrierOrigin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exports raw Java canonical-product bytes for the focused Rust mismatch classifier.
 *
 * <p>The manifest grammar and file stems deliberately match the ignored classifier tests in
 * {@code mc_features_region_bridge_263.rs}. Production is delegated to the same canonical Java
 * carrier APIs as the Golden identity producer; this class contains no terrain-generation logic.
 * Every worker owns its world contexts and bounded memos. Results are retained by declaration
 * index and published only after the collecting producer has visited the complete manifest.</p>
 */
public final class GoldenRawCarrierExporterMain {
    static final int MAX_WINDOWS = 8_192;
    static final long MAX_MANIFEST_BYTES = 1_048_576L;
    static final int DEFAULT_WORKERS = 4;
    static final int MAX_WORKERS = 16;
    static final int PROGRESS_INTERVAL = 25;
    private static final int CARRIER_MEMO_REGIONS = 9;
    private static final String INTEGRATED_BUILD_RECEIPT_ENV =
            "MISMATCH_INTEGRATED_BUILD_RECEIPT";

    private GoldenRawCarrierExporterMain() { }

    record Coordinate(int seed, int chunkX, int chunkZ) {
        String stem() { return seed + "_" + chunkX + "_" + chunkZ; }
        @Override public String toString() { return seed + "," + chunkX + "," + chunkZ; }
    }

    record Product(byte[] carrier, byte[] origin, byte[] provenance, byte[] binding) {
        Product {
            carrier = requireBytes(carrier, "carrier");
            origin = requireBytes(origin, "origin");
            provenance = requireBytes(provenance, "provenance");
            binding = requireBytes(binding, "product binding");
        }

        Product(byte[] carrier, byte[] origin, byte[] provenance) {
            this(carrier, origin, provenance, testBinding(carrier, origin, provenance));
        }

        private static byte[] requireBytes(byte[] value, String label) {
            if (value == null || value.length == 0) {
                throw new IllegalArgumentException(label + " is empty");
            }
            return value.clone();
        }

        private static byte[] testBinding(byte[] carrier, byte[] origin, byte[] provenance) {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                digest.update("MCP263-TEST-BINDING-V1\0".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII));
                digest.update(carrier); digest.update(origin); digest.update(provenance);
                return digest.digest();
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    @FunctionalInterface
    interface Producer {
        Product produce(Coordinate coordinate) throws Exception;
    }

    private static final class CanonicalProducer implements Producer {
        private final IntegratedBuildReceipt integratedBuildReceipt;
        // Offline export has no durable world-reference authority. The authenticated renderer
        // serves ordinary contexts; skip-existing map requests must still fail closed.
        private final Map<Integer, Mc263ProductionContextCatalog.Provider> productionContexts =
                new HashMap<>();
        private final Map<Integer, Mc263StructureCarrierOrigin.WorldContext> worlds =
                new HashMap<>();
        private final Map<Integer, Mc263StructureCarrierOrigin.RegionMemo> carrierMemos =
                new HashMap<>();
        private final Map<Integer, Mc263FeaturesRegionBridge.RegionMemo> featureMemos =
                new HashMap<>();

        private CanonicalProducer(IntegratedBuildReceipt integratedBuildReceipt) {
            this.integratedBuildReceipt = Objects.requireNonNull(integratedBuildReceipt,
                    "integrated build receipt");
        }

        @Override
        public Product produce(Coordinate coordinate) throws Exception {
            int seed = coordinate.seed();
            Mc263StructureCarrierOrigin.WorldContext world = worlds.computeIfAbsent(seed,
                    value -> Mc263StructureCarrierOrigin.prepare(value,
                            Mc263BaseHeightSampler.overworld(value)));
            Mc263StructureCarrierOrigin.RegionMemo carrierMemo = carrierMemos.computeIfAbsent(seed,
                    ignored -> Mc263StructureCarrierOrigin.RegionMemo.bounded(
                            CARRIER_MEMO_REGIONS));
            Mc263FeaturesRegionBridge.RegionMemo featureMemo = featureMemos.computeIfAbsent(seed,
                    ignored -> Mc263FeaturesRegionBridge.RegionMemo.bounded(
                            Mc263FeaturesRegionBridge.DEFAULT_MEMO_CHUNKS));
            Mc263StructureCarrierOrigin.Assembly assembly = Mc263StructureCarrierOrigin.assemble(
                    world, coordinate.chunkX(), coordinate.chunkZ(), carrierMemo);
            Mc263CanonicalGenerationProduct product =
                    Mc263FeaturesRegionBridge.generateCanonicalProduct(seed,
                            coordinate.chunkX(), coordinate.chunkZ(), assembly.carrier(),
                            productionContexts.computeIfAbsent(seed,
                                    ignored -> Mc263ProductionContextCatalog.liveProvider(
                                            Mc263LocatedMapAuthority.pinnedBiomePreview())),
                            new CanonicalPostprocessActivationContext(0), featureMemo);
            byte[] origin = carrierOrigin(coordinate, product.structureCarrier());
            return new Product(product.finalCarrier(), origin, product.provenanceReceipt(),
                    product.exportProductBinding(origin, integratedBuildReceipt));
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) {
            throw new IllegalArgumentException(
                    "arguments are forbidden; set MISMATCH_WINDOWS and MISMATCH_JAVA_DIR");
        }
        Path manifest = requiredAbsolutePath("MISMATCH_WINDOWS");
        Path output = requiredAbsolutePath("MISMATCH_JAVA_DIR");
        Path integratedReceiptPath = requiredAbsolutePath(INTEGRATED_BUILD_RECEIPT_ENV);
        int workers = workers(System.getenv("MISMATCH_THREADS"));
        runCanonical(manifest, output, workers,
                IntegratedBuildReceipt.read(integratedReceiptPath));
    }

    static void runCanonical(Path manifest, Path output, int workers) throws Exception {
        throw new IllegalArgumentException(
                "integrated build receipt is required for canonical export");
    }

    static void runCanonical(Path manifest, Path output, int workers,
            IntegratedBuildReceipt integratedBuildReceipt) throws Exception {
        IntegratedBuildReceipt receipt = Objects.requireNonNull(integratedBuildReceipt,
                "integrated build receipt");
        receipt.verifyAgainstCurrentBuild();
        run(manifest, output, workers, () -> new CanonicalProducer(receipt));
    }

    static IntegratedBuildReceipt currentIntegratedBuildReceiptForTesting() {
        return com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars
                .currentIntegratedBuildReceiptForTesting();
    }

    static void run(Path manifest, Path output, int workers,
            java.util.function.Supplier<? extends Producer> producerFactory) throws Exception {
        if (workers < 1 || workers > MAX_WORKERS) {
            throw new IllegalArgumentException("MISMATCH_THREADS must be between 1 and "
                    + MAX_WORKERS);
        }
        List<Coordinate> coordinates = readManifest(manifest);
        Path target = output.toAbsolutePath().normalize();
        try {
            Files.createDirectory(target);
        } catch (FileAlreadyExistsException existing) {
            throw new IllegalArgumentException("MISMATCH_JAVA_DIR already exists", existing);
        }

        try {
            Product[] products = collect(coordinates, workers, producerFactory);
            publish(target, coordinates, products);
        } catch (Throwable failure) {
            try {
                cleanup(target, coordinates);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }
    }

    static List<Coordinate> readManifest(Path manifest) throws IOException {
        if (manifest == null || !manifest.isAbsolute() || !Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("MISMATCH_WINDOWS must be an absolute regular file");
        }
        if (Files.size(manifest) > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("MISMATCH_WINDOWS exceeds "
                    + MAX_MANIFEST_BYTES + " bytes");
        }
        List<String> lines = Files.readAllLines(manifest);
        List<Coordinate> coordinates = new ArrayList<>();
        Set<String> stems = new HashSet<>();
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1).trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split(",", -1);
            if (fields.length != 3) {
                throw new IllegalArgumentException("MISMATCH_WINDOWS row " + lineNumber
                        + " must contain exactly seed,cx,cz");
            }
            Coordinate coordinate;
            try {
                coordinate = new Coordinate(Integer.parseInt(fields[0].trim()),
                        Integer.parseInt(fields[1].trim()),
                        Integer.parseInt(fields[2].trim()));
            } catch (NumberFormatException malformed) {
                throw new IllegalArgumentException("MISMATCH_WINDOWS row " + lineNumber
                        + " has a non-i32 coordinate", malformed);
            }
            if (!stems.add(coordinate.stem())) {
                throw new IllegalArgumentException("duplicate MISMATCH_WINDOWS coordinate at row "
                        + lineNumber);
            }
            coordinates.add(coordinate);
            if (coordinates.size() > MAX_WINDOWS) {
                throw new IllegalArgumentException("MISMATCH_WINDOWS exceeds " + MAX_WINDOWS
                        + " rows");
            }
        }
        if (coordinates.isEmpty()) {
            throw new IllegalArgumentException("MISMATCH_WINDOWS is empty");
        }
        return List.copyOf(coordinates);
    }

    private static Product[] collect(List<Coordinate> coordinates, int workers,
            java.util.function.Supplier<? extends Producer> producerFactory) throws Exception {
        System.err.println("[golden:raw-export] windows=" + coordinates.size()
                + " workers=" + workers);
        Product[] products = new Product[coordinates.size()];
        Throwable[] failures = new Throwable[coordinates.size()];
        AtomicInteger completed = new AtomicInteger();
        long started = System.nanoTime();
        ThreadLocal<Producer> producers = ThreadLocal.withInitial(producerFactory::get);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>(coordinates.size());
        try {
            for (int index = 0; index < coordinates.size(); index++) {
                int declarationIndex = index;
                futures.add(pool.submit(() -> {
                    try {
                        products[declarationIndex] = producers.get().produce(
                                coordinates.get(declarationIndex));
                    } catch (Throwable failure) {
                        failures[declarationIndex] = failure;
                    } finally {
                        int done = completed.incrementAndGet();
                        if (done % PROGRESS_INTERVAL == 0 || done == coordinates.size()) {
                            System.err.println("[golden:raw-export] " + done + "/"
                                    + coordinates.size() + " elapsed="
                                    + ((System.nanoTime() - started) / 1_000_000_000L) + "s");
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException unexpected) {
                    throw new IllegalStateException("raw exporter worker escaped collecting mode",
                            unexpected.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        IllegalStateException aggregate = null;
        for (int index = 0; index < failures.length; index++) {
            if (failures[index] == null) continue;
            if (aggregate == null) {
                aggregate = new IllegalStateException("raw carrier export failed for one or more "
                        + "windows");
            }
            aggregate.addSuppressed(new IllegalStateException(
                    coordinates.get(index) + ": " + failures[index], failures[index]));
        }
        if (aggregate != null) throw aggregate;
        return products;
    }

    static byte[] carrierOrigin(Coordinate coordinate,
            Mc263StructureCarrier successor) throws Exception {
        byte[] carrier = successor.receiptBytes();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(72 + carrier.length);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes("SCOR2631");
            output.writeInt(5);
            output.writeShort(com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec.SCHEMA);
            output.writeShort(0);
            output.writeLong(coordinate.seed());
            output.writeInt(coordinate.chunkX());
            output.writeInt(coordinate.chunkZ());
            output.writeInt(successor.referenceChunk(
                    coordinate.chunkX(), coordinate.chunkZ()).orElseThrow()
                    .orderedSets().size());
            output.writeInt(carrier.length);
            output.write(java.security.MessageDigest.getInstance("SHA-256").digest(carrier));
            output.write(carrier);
        }
        return bytes.toByteArray();
    }

    private static void publish(Path output, List<Coordinate> coordinates, Product[] products)
            throws IOException {
        for (int index = 0; index < coordinates.size(); index++) {
            Coordinate coordinate = coordinates.get(index);
            Product product = products[index];
            writeNew(output.resolve(coordinate.stem() + ".carrier"), product.carrier());
            writeNew(output.resolve(coordinate.stem() + ".origin"), product.origin());
            writeNew(output.resolve(coordinate.stem() + ".prov"), product.provenance());
            writeNew(output.resolve(coordinate.stem() + ".product"), product.binding());
        }
    }

    private static void writeNew(Path destination, byte[] bytes) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination);
        }
    }

    private static void cleanup(Path output, List<Coordinate> coordinates) throws IOException {
        IOException failure = null;
        for (Coordinate coordinate : coordinates) {
            for (String suffix : List.of(".carrier", ".origin", ".prov", ".product")) {
                Path destination = output.resolve(coordinate.stem() + suffix);
                for (Path owned : List.of(destination,
                        destination.resolveSibling(destination.getFileName() + ".tmp"))) {
                    try {
                        Files.deleteIfExists(owned);
                    } catch (IOException cleanupFailure) {
                        if (failure == null) failure = cleanupFailure;
                        else failure.addSuppressed(cleanupFailure);
                    }
                }
            }
        }
        try {
            Files.deleteIfExists(output);
        } catch (IOException cleanupFailure) {
            if (failure == null) failure = cleanupFailure;
            else failure.addSuppressed(cleanupFailure);
        }
        if (failure != null) throw failure;
    }

    private static Path requiredAbsolutePath(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        Path resolved = Path.of(value);
        if (!resolved.isAbsolute()) throw new IllegalArgumentException(name + " must be absolute");
        return resolved.normalize();
    }

    private static int workers(String value) {
        if (value == null || value.isBlank()) return DEFAULT_WORKERS;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("MISMATCH_THREADS must be an integer", malformed);
        }
    }
}
