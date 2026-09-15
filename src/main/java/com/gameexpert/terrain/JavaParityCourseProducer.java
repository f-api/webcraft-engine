package com.gameexpert.terrain;

import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.persistence.InMemoryCanonicalWorldgenStore;
import com.gameexpert.world.WorldBaseline;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Independent Java producer for the format-3 canonical-final parity course.
 *
 * <p>Production course input is read only from the retained Spring Boot producer jar. The
 * producer authenticates the jar manifest, loader and application entries, both code sources,
 * and the live {@code PropertiesLauncher} stack frame before reading the course entry. Each
 * worker owns one input-builder context and one detached canonical source. All workers share the
 * caller-supplied authenticated production-context provider, while each source requests a fresh
 * single-product authority. Results are collected in declaration order and are committed as an
 * artifact followed by a durable receipt marker.</p>
 */
public final class JavaParityCourseProducer {
    public static final String COURSE_RESOURCE = "mc263/java-parity-course-v3.json";
    public static final String PRODUCER = "java-canonical-chunk";
    public static final int FORMAT = 3;
    public static final int PARALLEL_WORKERS = 4;
    public static final int CANONICAL_CHUNK_BYTES = Blocks.CHUNK_BLOCKS * Short.BYTES;

    private static final int AUTHENTICATED_COURSE_ROWS = 9;
    private static final int MAX_COURSE_ROWS = AUTHENTICATED_COURSE_ROWS;
    private static final int MAX_FAILURES = MAX_COURSE_ROWS + (PARALLEL_WORKERS * 2) + 3;
    private static final int MAX_QUEUE_ROWS = MAX_COURSE_ROWS;
    private static final int MAX_CLASS_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PUBLICATION_BASENAME_CHARS = 240;
    private static final int AUTHENTICATED_CFPR_BYTES = 7_248;
    private static final String CFPR_RESOURCE =
            "mc263/canonical-final-product-receipt-v1.txt";
    private static final String CFPR_ENTRY = "BOOT-INF/classes/" + CFPR_RESOURCE;
    private static final String CFPR_COLUMNS =
            "columns|kind|seed|chunkX|chunkZ|blocksSha256|exactStateCount|exactStatesSha256|"
                    + "btikCount|btikSha256|ftikCount|ftikSha256|postprocessedOccurrences|"
                    + "finalPostprocessMarkCount|finalPostprocessMarksSha256|finalCarrierSha256|"
                    + "sidecarsSha256|successorStrSha256|wgrSuccessorSha256|wgrContinuationHex";
    private static final Duration PRODUCTION_DEADLINE = Duration.ofMinutes(10);
    private static final String PROPERTIES_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String BOOT_JAR_LAUNCHER =
            "org.springframework.boot.loader.launch.JarLauncher";
    private static final String BOOT_LAUNCHER =
            "org.springframework.boot.loader.launch.Launcher";
    private static final String BOOT_LAUNCHER_MAIN_METHOD = "main";
    private static final String BOOT_LAUNCH_METHOD = "launch";
    private static final String PROPERTIES_LAUNCHER_CLASS_ENTRY =
            "org/springframework/boot/loader/launch/PropertiesLauncher.class";
    private static final String BOOT_JAR_LAUNCHER_CLASS_ENTRY =
            "org/springframework/boot/loader/launch/JarLauncher.class";
    private static final String BOOT_LAUNCHER_CLASS_ENTRY =
            "org/springframework/boot/loader/launch/Launcher.class";
    private static final String PRODUCER_CLASS_ENTRY =
            "BOOT-INF/classes/com/gameexpert/terrain/JavaParityCourseProducer.class";
    private static final String COURSE_ENTRY = "BOOT-INF/classes/" + COURSE_RESOURCE;
    private static final String PRODUCER_SOURCE_RELATIVE =
            "src/main/java/com/gameexpert/terrain/JavaParityCourseProducer.java";
    private static final String PUBLICATION_OWNER_MAGIC = "JPCP-OWNER-1";
    private static final String PUBLICATION_OWNER_SUFFIX = ".java-parity-owner";
    private static final int MAX_COURSE_RESOURCE_BYTES = 16 * 1024;
    private static final int MAX_RECEIPT_BYTES = 1 * 1024 * 1024;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int PUBLICATION_WRITE_CHUNK_BYTES = 4 * 1024;
    private static final long NORMAL_POOL_CLOSE_MILLIS = 2_000L;
    private static final long TIMEOUT_POOL_CLOSE_MILLIS = 100L;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private JavaParityCourseProducer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: java -Dloader.main=" + JavaParityCourseProducer.class.getName()
                            + " -cp <frozen-boot.jar> " + PROPERTIES_LAUNCHER
                            + " <artifact> <producer-receipt>");
        }
        requirePropertiesLauncherInvocation();
        // A JVM launcher cannot inject the authenticated Provider object. Refuse to enter the
        // production path until an explicit provider-bearing caller is wired.
        throw new IllegalStateException(
                "Java parity production requires an explicitly supplied authenticated "
                        + "production context catalog provider");
    }

    /**
     * Loads the course from the authenticated producer boot jar.
     *
     * <p>Focused tests use {@link #loadCourseForTest()} because their classpath is deliberately
     * compiled classes rather than a retained executable jar.</p>
     */
    public static Course loadCourse() throws IOException {
        Path producerJar = producerJarFromProtectionDomain();
        AuthenticatedBootJar authenticated = authenticateProducerBootJar(producerJar);
        Course course = parseCourse(authenticated.courseBytes(), authenticated.courseResourcePath(),
                authenticated.courseResourceSha256(), authenticated.cfpr());
        authenticated.verifyStillBound();
        return course;
    }

    /** Package test seam; it still parses the exact main resource and never changes production loading. */
    static Course loadCourseForTest() throws IOException {
        byte[] resourceBytes = readClasspathResource(COURSE_RESOURCE,
                MAX_COURSE_RESOURCE_BYTES, "course resource exceeds its bounded input size");
        byte[] cfprBytes = readClasspathResource(CFPR_RESOURCE, AUTHENTICATED_CFPR_BYTES,
                "CFPR resource exceeds its authenticated size");
        AuthenticatedCfpr cfpr = authenticateCfpr(cfprBytes, "classpath:/" + CFPR_RESOURCE);
        return parseCourse(resourceBytes, "classpath:/" + COURSE_RESOURCE, sha256(resourceBytes), cfpr);
    }

    /** Test seam for causal stale/forged course and CFPR rows without changing production loading. */
    static Course parseCourseForTest(byte[] courseBytes, byte[] cfprBytes) throws IOException {
        Objects.requireNonNull(courseBytes, "course bytes");
        Objects.requireNonNull(cfprBytes, "CFPR bytes");
        AuthenticatedCfpr cfpr = authenticateCfpr(cfprBytes, "test:/" + CFPR_RESOURCE);
        return parseCourse(courseBytes, "test:/" + COURSE_RESOURCE, sha256(courseBytes), cfpr);
    }

    /** Test seam for the archive grammar, independent of class-loader code-source setup. */
    static void authenticateArchiveGrammarForTest(Path jar) throws IOException {
        try (JarFile archive = openJar(jar)) {
            authenticateArchiveGrammar(archive);
        }
    }

    /** Test seam for duplicate-entry and multi-release-shadow grammar cases. */
    static void authenticateArchiveNamesForTest(List<String> names) throws IOException {
        authenticateArchiveNames(names);
    }

    /** Test seam that proves a path identity is rejected after an inode or digest ABA mutation. */
    static void verifyIdentityAfterTestMutation(Path path, IdentityMutation mutation)
            throws IOException {
        Identity expected = identity("testIdentity", path);
        mutation.afterCapture();
        verifyIdentities(List.of(expected));
    }

    /** Test seam for proving source selection never consults the current working directory. */
    static Path producerSourcePathForTest(Path producerJar) throws IOException {
        Path source = canonicalProducerSourcePath(producerJar);
        if (source == null) throw new IOException("canonical producer source is missing");
        return source;
    }

    /** Generates declaration-order bytes with the fixed production worker count and deadline. */
    public static byte[] generateAggregate(Course course, int workerCount,
            Mc263ProductionContextCatalog.Provider productionContextProvider) throws Exception {
        productionContextProvider = requireProductionContextProvider(productionContextProvider);
        return generateAggregateReport(course, workerCount, PRODUCTION_DEADLINE,
                null, productionContextProvider).aggregate();
    }

    /** Returns bytes plus the deterministic worker proof used in the receipt. */
    public static GenerationReport generateAggregateReport(Course course, int workerCount,
            Mc263ProductionContextCatalog.Provider productionContextProvider) throws Exception {
        productionContextProvider = requireProductionContextProvider(productionContextProvider);
        return generateAggregateReport(course, workerCount, PRODUCTION_DEADLINE,
                null, productionContextProvider);
    }

    /**
     * Short-deadline test seam. The production worker/context/source lifecycle remains active;
     * only row byte production is supplied by the test so failure and timeout cases are cheap.
     */
    public static GenerationReport generateAggregateForTest(Course course, int workerCount,
            Duration deadline, RowGenerator generator,
            Mc263ProductionContextCatalog.Provider productionContextProvider) throws Exception {
        productionContextProvider = requireProductionContextProvider(productionContextProvider);
        return generateAggregateReport(course, workerCount, deadline, generator,
                productionContextProvider);
    }

    /** Package test seam for the secure publication and crash-recovery contract. */
    static void publishForTest(Path artifact, Path receipt, byte[] artifactBytes,
            byte[] receiptBytes) throws IOException {
        publishArtifactThenReceipt(artifact, receipt, artifactBytes, receiptBytes, null, null);
    }

    /** Package test seam which runs after the retained artifact directory is forced. */
    static void publishForTest(Path artifact, Path receipt, byte[] artifactBytes,
            byte[] receiptBytes, PublicationHook afterArtifact) throws IOException {
        publishArtifactThenReceipt(artifact, receipt, artifactBytes, receiptBytes, afterArtifact,
                null);
    }

    /** Package test seam for a crash after a bounded partial write. */
    static void publishForTest(Path artifact, Path receipt, byte[] artifactBytes,
            byte[] receiptBytes, PublicationHook afterArtifact, WriteHook writeHook)
            throws IOException {
        publishArtifactThenReceipt(artifact, receipt, artifactBytes, receiptBytes, afterArtifact,
                writeHook);
    }

    /** Strict JSON receipt round-trip seam used by the canonicality regression tests. */
    static String canonicalReceiptRoundTrip(String receipt) throws IOException {
        if (receipt == null || !receipt.endsWith("\n") || receipt.endsWith("\r\n")) {
            throw new IOException("receipt must end with one LF");
        }
        String document = receipt.substring(0, receipt.length() - 1);
        if (document.indexOf('\n') >= 0 || document.indexOf('\r') >= 0) {
            throw new IOException("receipt contains an unescaped line break");
        }
        requireWellFormedUnicode(receipt, "receipt");
        byte[] inputBytes = receipt.getBytes(StandardCharsets.UTF_8);
        if (inputBytes.length > MAX_RECEIPT_BYTES) {
            throw new IOException("receipt exceeds its bounded publication capacity");
        }
        if (!receipt.equals(decodeUtf8(inputBytes, "receipt"))) {
            throw new IOException("receipt UTF-8 decode/re-encode is not stable");
        }
        JsonNode root = JSON.readTree(document);
        if (root == null || !root.isObject()) throw new IOException("receipt is not a JSON object");
        validateJsonUnicode(root);
        String canonical = JSON.writeValueAsString(root) + "\n";
        if (!receipt.equals(canonical)
                || !Arrays.equals(inputBytes, canonical.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("receipt is not canonical JSON");
        }
        return receipt;
    }

    private static GenerationReport generateAggregateReport(Course course, int workerCount,
            Duration deadline, RowGenerator generator,
            Mc263ProductionContextCatalog.Provider productionContextProvider) throws Exception {
        validateGenerationArguments(course, workerCount, deadline, generator);
        productionContextProvider = Objects.requireNonNull(productionContextProvider,
                "production context catalog provider");
        List<Worker> workers = createWorkers(workerCount, course.chunkBytes(),
                productionContextProvider);
        List<List<Integer>> assignments = assignRows(course, workerCount);
        for (int worker = 0; worker < workers.size(); worker++) {
            workers.get(worker).assignedRows.addAll(assignments.get(worker));
        }

        ExecutorService pool = Executors.newFixedThreadPool(workerCount,
                parityThreadFactory());
        CompletionService<WorkerResult> completed = new ExecutorCompletionService<>(pool);
        Map<Future<WorkerResult>, SubmittedWork> submitted = new ConcurrentHashMap<>();
        List<SubmittedWork> submittedInOrder = new ArrayList<>(workerCount);
        Set<Thread> actualWorkerThreads = ConcurrentHashMap.newKeySet();
        Map<Integer, Worker> activeWorkers = new ConcurrentHashMap<>();
        byte[][] ordered = new byte[course.cardinality()][];
        boolean[] resolved = new boolean[course.cardinality()];
        List<RowFailure> failures = new ArrayList<>(MAX_FAILURES);
        long deadlineNanos = deadline.toNanos();
        long deadlineAt = saturatingDeadline(System.nanoTime(), deadlineNanos);
        boolean timedOut = false;
        InterruptedException interrupted = null;

        try {
            int submittedRows = 0;
            for (int workerIndex = 0; workerIndex < workers.size(); workerIndex++) {
                List<Integer> rowIndexes = assignments.get(workerIndex);
                if (rowIndexes.isEmpty()) continue;
                if (submittedRows + rowIndexes.size() > MAX_QUEUE_ROWS) {
                    for (int rowIndex : rowIndexes) {
                        resolved[rowIndex] = true;
                        addFailure(failures, new RowFailure(rowIndex,
                                course.rows().get(rowIndex).coordinate(), workerIndex,
                                "queue-bound", new IllegalStateException(
                                        "parity submission queue exceeded its bounded capacity")));
                    }
                    continue;
                }
                try {
                    int submittedWorkerIndex = workerIndex;
                    Future<WorkerResult> future = completed.submit(() -> runWorker(
                            workers.get(submittedWorkerIndex), rowIndexes, course.rows(),
                            actualWorkerThreads, activeWorkers, deadlineAt,
                            generator));
                    SubmittedWork work = new SubmittedWork(future, workerIndex, rowIndexes);
                    submitted.put(future, work);
                    submittedInOrder.add(work);
                    submittedRows += rowIndexes.size();
                } catch (Throwable failure) {
                    for (int rowIndex : rowIndexes) {
                        resolved[rowIndex] = true;
                        addFailure(failures, new RowFailure(rowIndex,
                                course.rows().get(rowIndex).coordinate(), workerIndex,
                                "submission", failure));
                    }
                }
            }

            int finishedWorkers = 0;
            int lastProgress = 0;
            while (finishedWorkers < submittedInOrder.size()) {
                long remaining = deadlineAt - System.nanoTime();
                if (remaining <= 0) {
                    timedOut = true;
                    break;
                }
                Future<WorkerResult> future = completed.poll(remaining, TimeUnit.NANOSECONDS);
                if (future == null) {
                    timedOut = true;
                    break;
                }
                finishedWorkers++;
                collectFuture(future, submitted.get(future), course.rows(), ordered, resolved,
                        failures);
                int resolvedRows = resolvedRows(resolved);
                if (resolvedRows - lastProgress >= 4 || resolvedRows == course.cardinality()) {
                    System.out.println("java-parity progress " + resolvedRows + "/"
                            + course.cardinality());
                    System.out.flush();
                    lastProgress = resolvedRows;
                }
                if (System.nanoTime() >= deadlineAt) {
                    timedOut = true;
                    break;
                }
            }

            if (timedOut) {
                cancelOutstanding(submitted);
                for (SubmittedWork submittedWork : submittedInOrder) {
                    if (allResolved(submittedWork.rowIndexes(), resolved)) continue;
                    Future<WorkerResult> future = submittedWork.future();
                    if (future.isDone() && !future.isCancelled()) {
                        collectFuture(future, submittedWork, course.rows(), ordered, resolved,
                                failures);
                        for (int rowIndex : submittedWork.rowIndexes()) {
                            if (ordered[rowIndex] != null) {
                                ordered[rowIndex] = null;
                                addFailure(failures, new RowFailure(rowIndex,
                                        course.rows().get(rowIndex).coordinate(),
                                        submittedWork.workerIndex(), "timeout",
                                        new TimeoutException(
                                                "row completed after the parity deadline")));
                            } else if (!resolved[rowIndex]) {
                                resolved[rowIndex] = true;
                                addFailure(failures, new RowFailure(rowIndex,
                                        course.rows().get(rowIndex).coordinate(),
                                        submittedWork.workerIndex(), "timeout",
                                        new TimeoutException("parity deadline exceeded")));
                            }
                        }
                    } else {
                        for (int rowIndex : submittedWork.rowIndexes()) {
                            if (resolved[rowIndex]) continue;
                            resolved[rowIndex] = true;
                            addFailure(failures, new RowFailure(rowIndex,
                                    course.rows().get(rowIndex).coordinate(),
                                    submittedWork.workerIndex(), "timeout",
                                    new TimeoutException("parity deadline exceeded")));
                        }
                    }
                }
            }
        } catch (InterruptedException failure) {
            interrupted = failure;
            cancelOutstanding(submitted);
            for (SubmittedWork submittedWork : submittedInOrder) {
                Future<WorkerResult> future = submittedWork.future();
                if (future.isDone() && !future.isCancelled()) {
                    collectFuture(future, submittedWork, course.rows(), ordered, resolved,
                            failures);
                }
                for (int rowIndex : submittedWork.rowIndexes()) {
                    if (!resolved[rowIndex]) {
                        resolved[rowIndex] = true;
                        addFailure(failures, new RowFailure(rowIndex,
                                course.rows().get(rowIndex).coordinate(),
                                submittedWork.workerIndex(), "interrupted", failure));
                    }
                }
            }
        } finally {
            if (timedOut || interrupted != null) pool.shutdownNow();
            else pool.shutdown();
            boolean terminated;
            try {
                terminated = pool.awaitTermination(
                        timedOut || interrupted != null
                                ? TIMEOUT_POOL_CLOSE_MILLIS : NORMAL_POOL_CLOSE_MILLIS,
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException failure) {
                if (interrupted == null) interrupted = failure;
                pool.shutdownNow();
                terminated = false;
                cancelOutstanding(submitted);
                for (SubmittedWork submittedWork : submittedInOrder) {
                    Future<WorkerResult> future = submittedWork.future();
                    if (future.isDone() && !future.isCancelled()) {
                        collectFuture(future, submittedWork, course.rows(), ordered, resolved,
                                failures);
                    }
                    for (int rowIndex : submittedWork.rowIndexes()) {
                        if (!resolved[rowIndex]) {
                            resolved[rowIndex] = true;
                            addFailure(failures, new RowFailure(rowIndex,
                                    course.rows().get(rowIndex).coordinate(),
                                    submittedWork.workerIndex(), "interrupted", failure));
                        }
                    }
                }
            }
            if (!terminated) {
                addFailure(failures, new RowFailure(-1, null, -1, "worker-pool-live",
                        new IllegalStateException("parity worker pool did not terminate")));
            }
            closeWorkers(workers, failures);
        }

        List<WorkerTelemetry> telemetry = workerTelemetry(workers, actualWorkerThreads, failures);
        for (WorkerTelemetry worker : telemetry) {
            if (!worker.contextQuiescent()) {
                addFailure(failures, new RowFailure(-1, null, worker.worker(), "context-live",
                        new IllegalStateException("input-builder context " + worker.worker()
                                + " did not quiesce")));
            }
        }
        if (timedOut) {
            addFailure(failures, new RowFailure(-1, null, -1, "deadline",
                    new TimeoutException("parity deadline exceeded; result is not publishable")));
        }
        if (interrupted != null) {
            Thread.currentThread().interrupt();
            throw generationException(failures, telemetry, timedOut, interrupted);
        }
        if (!failures.isEmpty()) throw generationException(failures, telemetry, timedOut, null);
        if (workerCount == PARALLEL_WORKERS) requireFourWorkerOverlap(workers, actualWorkerThreads);

        byte[] aggregate = new byte[course.aggregateBytes()];
        for (int rowIndex = 0; rowIndex < ordered.length; rowIndex++) {
            byte[] bytes = ordered[rowIndex];
            if (bytes == null) throw new IllegalStateException("missing generated row " + rowIndex);
            System.arraycopy(bytes, 0, aggregate, rowIndex * course.chunkBytes(), bytes.length);
        }
        if (aggregate.length != course.aggregateBytes()) {
            throw new IllegalStateException("aggregate canonical byte length drift");
        }
        return new GenerationReport(aggregate, telemetry);
    }

    private static void validateGenerationArguments(Course course, int workerCount,
            Duration deadline, RowGenerator generator) {
        if (course == null || course.cardinality() == 0
                || course.cardinality() != course.rows().size()
                || course.cardinality() > MAX_COURSE_ROWS) {
            throw new IllegalArgumentException("course must contain exactly its row list");
        }
        if (workerCount != 1 && workerCount != PARALLEL_WORKERS) {
            throw new IllegalArgumentException("worker count must be one or four");
        }
        if (workerCount == PARALLEL_WORKERS && course.cardinality() < PARALLEL_WORKERS) {
            throw new IllegalArgumentException("four workers require at least four course rows");
        }
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("generation deadline must be positive");
        }
        if (course.aggregateBytes() > Math.multiplyExact(MAX_COURSE_ROWS,
                CANONICAL_CHUNK_BYTES)) {
            throw new IllegalArgumentException("course aggregate allocation exceeds its bound");
        }
    }

    private static List<Worker> createWorkers(int workerCount, int chunkBytes,
            Mc263ProductionContextCatalog.Provider productionContextProvider) throws Exception {
        productionContextProvider = requireProductionContextProvider(productionContextProvider);
        List<Worker> workers = new ArrayList<>(workerCount);
        try {
            for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
                Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext =
                        Mc263FeaturesRegionBridge.newInputBuilderContext();
                try {
                    ChunkProductSource source = new CanonicalOriginChunkProductSource(
                            new InMemoryCanonicalWorldgenStore(),
                            0x4a50435200000000L + workerIndex,
                            new CanonicalPostprocessActivationContext(0L), inputBuilderContext,
                            productionContextProvider);
                    workers.add(new Worker(workerIndex, source, inputBuilderContext, chunkBytes));
                } catch (Throwable failure) {
                    try {
                        inputBuilderContext.close();
                    } catch (Throwable closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                    rethrow(failure);
                }
            }
            return workers;
        } catch (Throwable failure) {
            List<RowFailure> closeFailures = new ArrayList<>(PARALLEL_WORKERS);
            closeWorkers(workers, closeFailures);
            for (RowFailure closeFailure : closeFailures) {
                failure.addSuppressed(closeFailure.cause());
            }
            rethrow(failure);
            throw new AssertionError("unreachable");
        }
    }

    private static List<List<Integer>> assignRows(Course course, int workerCount) {
        List<List<Integer>> assignments = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) assignments.add(new ArrayList<>());
        for (int rowIndex = 0; rowIndex < course.cardinality(); rowIndex++) {
            assignments.get(rowIndex % workerCount).add(rowIndex);
        }
        return assignments;
    }

    private static WorkerResult runWorker(Worker worker, List<Integer> rowIndexes,
            List<CourseRow> rows, Set<Thread> actualWorkerThreads,
            Map<Integer, Worker> activeWorkers, long deadlineAt,
            RowGenerator generator) {
        List<RowResult> results = new ArrayList<>(rowIndexes.size());
        try {
            for (int rowIndex : rowIndexes) {
                results.add(runRow(new WorkItem(rowIndex, rows.get(rowIndex), worker.workerIndex()),
                        worker, actualWorkerThreads, activeWorkers, deadlineAt, generator));
            }
        } catch (Throwable failure) {
            while (results.size() < rowIndexes.size()) results.add(new RowResult(null, failure));
        }
        return new WorkerResult(List.copyOf(results));
    }

    private static RowResult runRow(WorkItem item, Worker worker,
            Set<Thread> actualWorkerThreads, Map<Integer, Worker> activeWorkers,
            long deadlineAt, RowGenerator generator) {
        try {
            synchronized (worker) {
                worker.start(actualWorkerThreads, deadlineAt);
                worker.enterWork(activeWorkers);
                try {
                    byte[] bytes = generator == null
                            ? worker.generate(item.row())
                            : generator.generate(worker.workerIndex(), item.row());
                    if (System.nanoTime() >= deadlineAt) {
                        throw new TimeoutException("row completed after the parity deadline");
                    }
                    if (bytes == null || bytes.length != worker.expectedChunkBytes()) {
                        throw new IllegalStateException("canonical byte length drift at "
                                + item.row().coordinate().key());
                    }
                    String actualDigest = sha256(bytes);
                    if (!actualDigest.equals(item.row().blocksSha256())) {
                        throw new IllegalStateException("CFPR1 blocksSha256 mismatch at "
                                + item.row().coordinate().key() + ": expected "
                                + item.row().blocksSha256() + " but was " + actualDigest);
                    }
                    if (System.nanoTime() >= deadlineAt) {
                        throw new TimeoutException("row digest completed after the parity deadline");
                    }
                    worker.completedRows.incrementAndGet();
                    return new RowResult(bytes, null);
                } finally {
                    worker.leaveWork(activeWorkers);
                }
            }
        } catch (Throwable failure) {
            worker.failedRows.incrementAndGet();
            return new RowResult(null, failure);
        }
    }

    private static void collectFuture(Future<WorkerResult> future, SubmittedWork work,
            List<CourseRow> rows, byte[][] ordered, boolean[] resolved,
            List<RowFailure> failures) {
        if (work == null) {
            addFailure(failures, new RowFailure(-1, null, -1, "future-attribution",
                    new IllegalStateException("completed parity worker has no attribution")));
            return;
        }
        try {
            if (future.isCancelled()) {
                markWorkerRows(work, rows, resolved, failures, "cancelled",
                        new CancellationException("parity worker future was cancelled"));
                return;
            }
            WorkerResult result = future.get();
            if (result == null || result.rowResults() == null
                    || result.rowResults().size() != work.rowIndexes().size()) {
                markWorkerRows(work, rows, resolved, failures, "execution",
                        new IllegalStateException("worker returned an incomplete row result"));
                return;
            }
            for (int index = 0; index < work.rowIndexes().size(); index++) {
                int rowIndex = work.rowIndexes().get(index);
                collectRowResult(result.rowResults().get(index),
                        new WorkItem(rowIndex, rows.get(rowIndex), work.workerIndex()),
                        ordered, resolved, failures);
            }
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            markWorkerRows(work, rows, resolved, failures, "execution", cause);
        } catch (CancellationException failure) {
            markWorkerRows(work, rows, resolved, failures, "cancelled", failure);
        } catch (Throwable failure) {
            markWorkerRows(work, rows, resolved, failures, "execution", failure);
        }
    }

    private static void collectRowResult(RowResult result, WorkItem item, byte[][] ordered,
            boolean[] resolved, List<RowFailure> failures) {
        int rowIndex = item.rowIndex();
        if (resolved[rowIndex]) return;
        resolved[rowIndex] = true;
        if (result == null) {
            addFailure(failures, new RowFailure(rowIndex, item.row().coordinate(),
                    item.workerIndex(), "generation",
                    new IllegalStateException("worker returned no row result")));
        } else if (result.failure() != null) {
            addFailure(failures, new RowFailure(rowIndex, item.row().coordinate(),
                    item.workerIndex(), result.failure() instanceof TimeoutException
                            ? "timeout" : "generation", result.failure()));
        } else if (result.bytes() == null) {
            addFailure(failures, new RowFailure(rowIndex, item.row().coordinate(),
                    item.workerIndex(), "generation",
                    new IllegalStateException("worker returned no bytes")));
        } else {
            ordered[rowIndex] = result.bytes();
        }
    }

    private static void markWorkerRows(SubmittedWork work, List<CourseRow> rows,
            boolean[] resolved, List<RowFailure> failures, String phase, Throwable cause) {
        for (int rowIndex : work.rowIndexes()) {
            if (resolved[rowIndex]) continue;
            resolved[rowIndex] = true;
            addFailure(failures, new RowFailure(rowIndex, rows.get(rowIndex).coordinate(),
                    work.workerIndex(), phase, cause));
        }
    }

    private static boolean allResolved(List<Integer> rowIndexes, boolean[] resolved) {
        for (int rowIndex : rowIndexes) if (!resolved[rowIndex]) return false;
        return true;
    }

    private static int resolvedRows(boolean[] resolved) {
        int count = 0;
        for (boolean row : resolved) if (row) count++;
        return count;
    }

    private static void cancelOutstanding(Map<Future<WorkerResult>, SubmittedWork> submitted) {
        for (Future<WorkerResult> future : submitted.keySet()) {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private static List<WorkerTelemetry> workerTelemetry(List<Worker> workers,
            Set<Thread> actualWorkerThreads, List<RowFailure> failures) {
        boolean distinct = actualWorkerThreads.size() == workers.size()
                && workers.stream().allMatch(worker -> worker.startedThread.get() != null);
        List<WorkerTelemetry> telemetry = new ArrayList<>(workers.size());
        for (Worker worker : workers) {
            int failedRows = 0;
            for (RowFailure failure : failures) {
                if (failure.worker() == worker.workerIndex() && failure.rowIndex() >= 0) failedRows++;
            }
            Thread startedThread = worker.startedThread.get();
            Mc263FeaturesRegionBridge.InputBuilderTelemetry context = worker.contextTelemetry();
            telemetry.add(new WorkerTelemetry(worker.workerIndex(), worker.assignedRows,
                    worker.completedRows.get(), failedRows, distinct, worker.overlapped.get(),
                    startedThread != null && startedThread.isDaemon(), context,
                    worker.contextHasLiveThreads()));
        }
        return List.copyOf(telemetry);
    }

    private static void requireFourWorkerOverlap(List<Worker> workers, Set<Thread> threads) {
        if (workers.size() != PARALLEL_WORKERS || threads.size() != PARALLEL_WORKERS
                || workers.stream().anyMatch(worker -> !worker.overlapped.get()
                        || worker.startedThread.get() == null
                        || !worker.startedThread.get().isDaemon())) {
            throw new IllegalStateException("workers 0 through 3 must be distinct, daemon, and overlapping");
        }
    }

    private static void closeWorkers(List<Worker> workers, List<RowFailure> failures) {
        for (Worker worker : workers) {
            try {
                worker.close();
            } catch (Throwable failure) {
                addFailure(failures, new RowFailure(-1, null, worker.workerIndex(),
                        "context-close", failure));
            }
        }
    }

    private static void addFailure(List<RowFailure> failures, RowFailure failure) {
        if (failures.size() >= MAX_FAILURES) {
            throw new IllegalStateException("parity failure bound exceeded", failure.cause());
        }
        failures.add(Objects.requireNonNull(failure, "failure"));
    }

    private static GenerationException generationException(List<RowFailure> failures,
            List<WorkerTelemetry> telemetry, boolean timedOut, Throwable cause) {
        List<RowFailure> ordered = new ArrayList<>(failures);
        ordered.sort((first, second) -> {
            int row = Integer.compare(first.rowIndex(), second.rowIndex());
            if (row != 0) return row;
            int worker = Integer.compare(first.worker(), second.worker());
            if (worker != 0) return worker;
            return first.phase().compareTo(second.phase());
        });
        StringBuilder message = new StringBuilder("canonical parity generation failed");
        if (timedOut) message.append(" (deadline exceeded)");
        message.append(" for ").append(ordered.size()).append(" row(s): ");
        for (int index = 0; index < ordered.size(); index++) {
            if (index > 0) message.append("; ");
            message.append(ordered.get(index).attribution());
        }
        GenerationException result = new GenerationException(message.toString(), ordered, telemetry,
                timedOut);
        if (cause != null) result.initCause(cause);
        for (RowFailure failure : ordered) {
            result.addSuppressed(new AttributedFailure(failure.attribution(), failure.cause()));
        }
        return result;
    }

    private static long saturatingDeadline(long start, long duration) {
        long max = Long.MAX_VALUE - start;
        return duration > max ? Long.MAX_VALUE : start + duration;
    }

    private static ThreadFactory parityThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "java-parity-course-worker-"
                    + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Mc263ProductionContextCatalog.Provider requireProductionContextProvider(
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        return Objects.requireNonNull(productionContextProvider,
                "production context catalog provider");
    }

    private static void produce(Path requestedArtifact, Path requestedReceipt,
            Mc263ProductionContextCatalog.Provider productionContextProvider) throws Exception {
        productionContextProvider = requireProductionContextProvider(productionContextProvider);
        Path artifact = absoluteNormalized(requestedArtifact);
        Path receipt = absoluteNormalized(requestedReceipt);
        rejectPublicationPaths(artifact, receipt);

        Path producerJar = producerJarFromProtectionDomain();
        AuthenticatedBootJar authenticated = authenticateProducerBootJar(producerJar);
        Course course = parseCourse(authenticated.courseBytes(), authenticated.courseResourcePath(),
                authenticated.courseResourceSha256(), authenticated.cfpr());
        authenticated.verifyStillBound();
        SourceBinding producerSource = producerSourceBinding(producerJar,
                authenticated.producerClassBinding());
        List<Identity> identities = List.of(
                identity("javaExecutable", javaExecutable()),
                identity("javaRuntime", javaRuntime()),
                authenticated.jarIdentity());
        if (producerSource.fileIdentity() != null) {
            identities = new ArrayList<>(identities);
            identities.add(producerSource.fileIdentity());
            identities = List.copyOf(identities);
        }
        rejectDuplicateIdentityPaths(identities, artifact, receipt);
        List<Identity> boundIdentities = List.copyOf(identities);
        verifyIdentities(boundIdentities);
        producerSource.verify(authenticated);

        GenerationReport generated = generateAggregateReport(course, PARALLEL_WORKERS,
                PRODUCTION_DEADLINE, null, productionContextProvider);
        if (generated.aggregate().length != course.aggregateBytes()) {
            throw new IllegalStateException("aggregate bytes were not derived from course rows");
        }
        String aggregateSha = sha256(generated.aggregate());
        String receiptDocument = receiptJson(artifact, generated.aggregate().length, aggregateSha,
                course, boundIdentities, producerSource);
        byte[] receiptBytes = receiptDocument.getBytes(StandardCharsets.UTF_8);

        publishArtifactThenReceipt(artifact, receipt, generated.aggregate(), receiptBytes,
                () -> {
                    verifyIdentities(boundIdentities);
                    producerSource.verify(authenticated);
                    authenticated.verifyStillBound();
                }, null);
    }

    private static void requirePropertiesLauncherInvocation() throws Exception {
        String selectedMain = System.getProperty("loader.main", "");
        if (!JavaParityCourseProducer.class.getName().equals(selectedMain)) {
            throw new IllegalStateException("producer must be selected by loader.main");
        }
        List<StackWalker.StackFrame> frames = StackWalker.getInstance(
                StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream -> stream.limit(64).toList());
        requireLauncherFrameOrder(frames);
        Class<?> authenticatedPropertiesLauncher;
        try {
            authenticatedPropertiesLauncher = Class.forName(PROPERTIES_LAUNCHER, false,
                    JavaParityCourseProducer.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("live PropertiesLauncher class is unavailable", failure);
        }
        StackWalker.StackFrame livePropertiesFrame = frames.stream()
                .filter(frame -> frame.getDeclaringClass() == authenticatedPropertiesLauncher
                        && BOOT_LAUNCHER_MAIN_METHOD.equals(frame.getMethodName()))
                .findFirst().orElse(null);
        if (livePropertiesFrame == null) {
            throw new IllegalStateException("live PropertiesLauncher main frame is not authenticated");
        }
        Path jar = producerJarFromProtectionDomain();
        authenticateProducerBootJar(jar);
    }

    private static void requireLauncherFrameOrder(List<StackWalker.StackFrame> frames) {
        int producerMain = frameIndex(frames, JavaParityCourseProducer.class.getName(),
                BOOT_LAUNCHER_MAIN_METHOD, 0);
        int launcherLaunch = frameIndex(frames, BOOT_LAUNCHER, BOOT_LAUNCH_METHOD, producerMain + 1);
        int propertiesMain = frameIndex(frames, PROPERTIES_LAUNCHER,
                BOOT_LAUNCHER_MAIN_METHOD, launcherLaunch + 1);
        if (producerMain < 0 || launcherLaunch < 0 || propertiesMain < 0
                || !(producerMain < launcherLaunch && launcherLaunch < propertiesMain)) {
            throw new IllegalStateException(
                    "producer must be reached by a live PropertiesLauncher main -> Launcher launch in order");
        }
    }

    private static int frameIndex(List<StackWalker.StackFrame> frames, String className,
            String methodName, int start) {
        for (int index = Math.max(0, start); index < frames.size(); index++) {
            StackWalker.StackFrame frame = frames.get(index);
            if (className.equals(frame.getDeclaringClass().getName())
                    && methodName.equals(frame.getMethodName())) return index;
        }
        return -1;
    }

    private static AuthenticatedBootJar authenticateProducerBootJar(Path jar) throws IOException {
        Identity jarIdentity = identity("producerJar", jar);
        Class<?> producer = JavaParityCourseProducer.class;
        Class<?> propertiesLauncher;
        Class<?> jarLauncher;
        Class<?> launcher;
        try {
            propertiesLauncher = Class.forName(PROPERTIES_LAUNCHER, false,
                    JavaParityCourseProducer.class.getClassLoader());
            jarLauncher = Class.forName(BOOT_JAR_LAUNCHER, false,
                    JavaParityCourseProducer.class.getClassLoader());
            launcher = Class.forName(BOOT_LAUNCHER, false,
                    JavaParityCourseProducer.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IOException("Spring Boot launcher classes are not in the producer runtime",
                    failure);
        }
        requireLauncherMethodContract(propertiesLauncher, jarLauncher, launcher);
        Identity producerCodeSource = authenticateCodeSource(producer, jarIdentity, "producer");
        Identity propertiesCodeSource = authenticateCodeSource(propertiesLauncher, jarIdentity,
                "PropertiesLauncher");
        Identity jarLauncherCodeSource = authenticateCodeSource(jarLauncher, jarIdentity,
                "JarLauncher");
        Identity launcherCodeSource = authenticateCodeSource(launcher, jarIdentity, "Launcher");

        byte[] producerClassBytes;
        byte[] propertiesClassBytes;
        byte[] jarLauncherClassBytes;
        byte[] launcherClassBytes;
        byte[] courseBytes;
        byte[] cfprBytes;
        try (JarFile archive = openJar(jar)) {
            authenticateArchiveGrammar(archive);
            requireBootManifest(archive.getManifest());
            requireRegularJarEntry(archive, BOOT_JAR_LAUNCHER_CLASS_ENTRY);
            requireRegularJarEntry(archive, BOOT_LAUNCHER_CLASS_ENTRY);
            requireRegularJarEntry(archive, PROPERTIES_LAUNCHER_CLASS_ENTRY);
            requireRegularJarEntry(archive, PRODUCER_CLASS_ENTRY);
            requireRegularJarEntry(archive, COURSE_ENTRY);
            requireRegularJarEntry(archive, CFPR_ENTRY);
            producerClassBytes = readJarEntry(archive, PRODUCER_CLASS_ENTRY, MAX_CLASS_BYTES,
                    "producer class exceeds its bounded input size");
            propertiesClassBytes = readJarEntry(archive, PROPERTIES_LAUNCHER_CLASS_ENTRY,
                    MAX_CLASS_BYTES, "PropertiesLauncher class exceeds its bounded input size");
            jarLauncherClassBytes = readJarEntry(archive, BOOT_JAR_LAUNCHER_CLASS_ENTRY,
                    MAX_CLASS_BYTES, "JarLauncher class exceeds its bounded input size");
            launcherClassBytes = readJarEntry(archive, BOOT_LAUNCHER_CLASS_ENTRY,
                    MAX_CLASS_BYTES, "Launcher class exceeds its bounded input size");
            courseBytes = readJarEntry(archive, COURSE_ENTRY, MAX_COURSE_RESOURCE_BYTES,
                    "course resource exceeds its bounded input size");
            cfprBytes = readJarEntry(archive, CFPR_ENTRY, AUTHENTICATED_CFPR_BYTES,
                    "CFPR resource exceeds its authenticated size");
        }

        ClassBinding producerBinding = authenticateClassBinding(producer, PRODUCER_CLASS_ENTRY,
                producerClassBytes, jar, producerCodeSource);
        ClassBinding propertiesBinding = authenticateClassBinding(propertiesLauncher,
                PROPERTIES_LAUNCHER_CLASS_ENTRY, propertiesClassBytes, jar, propertiesCodeSource);
        ClassBinding jarLauncherBinding = authenticateClassBinding(jarLauncher,
                BOOT_JAR_LAUNCHER_CLASS_ENTRY, jarLauncherClassBytes, jar, jarLauncherCodeSource);
        ClassBinding launcherBinding = authenticateClassBinding(launcher,
                BOOT_LAUNCHER_CLASS_ENTRY, launcherClassBytes, jar, launcherCodeSource);
        AuthenticatedCfpr cfpr = authenticateCfpr(cfprBytes, jar.toString() + "!/" + CFPR_ENTRY);

        URL courseUrl = resourceUrl(COURSE_RESOURCE);
        authenticateJarResourceUrl(courseUrl, jar, COURSE_ENTRY, "course resource");
        byte[] classpathBytes = readResource(courseUrl, MAX_COURSE_RESOURCE_BYTES,
                "course resource exceeds its bounded input size");
        if (!Arrays.equals(courseBytes, classpathBytes)) {
            throw new IOException("course classpath resource does not match retained jar entry");
        }
        URL cfprUrl = resourceUrl(CFPR_RESOURCE);
        authenticateJarResourceUrl(cfprUrl, jar, CFPR_ENTRY, "CFPR resource");
        byte[] classpathCfpr = readResource(cfprUrl, AUTHENTICATED_CFPR_BYTES,
                "CFPR resource exceeds its authenticated size");
        if (!Arrays.equals(cfprBytes, classpathCfpr)) {
            throw new IOException("CFPR classpath resource does not match retained jar entry");
        }
        AuthenticatedBootJar authenticated = new AuthenticatedBootJar(courseBytes,
                jar.toString() + "!/" + COURSE_ENTRY, sha256(courseBytes), cfpr, jarIdentity,
                producerBinding, propertiesBinding, jarLauncherBinding, launcherBinding);
        authenticated.verifyStillBound();
        return authenticated;
    }

    private static void requireBootManifest(Manifest manifest) throws IOException {
        if (manifest == null
                || !BOOT_JAR_LAUNCHER.equals(manifest.getMainAttributes().getValue("Main-Class"))
                || !"BOOT-INF/classes/".equals(
                        manifest.getMainAttributes().getValue("Spring-Boot-Classes"))
                || !"BOOT-INF/lib/".equals(
                        manifest.getMainAttributes().getValue("Spring-Boot-Lib"))) {
            throw new IOException("producer jar manifest is not the retained Spring Boot launcher");
        }
    }

    private static Identity authenticateCodeSource(Class<?> type, Identity expectedJar, String label)
            throws IOException {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        Path actual = codeSourcePath(codeSource, label);
        Identity actualIdentity = identity(label + "CodeSource", actual);
        if (!sameIdentity(expectedJar, actualIdentity)) {
            throw new IOException(label + " code source is not the retained producer jar");
        }
        return actualIdentity;
    }

    private static void requireLauncherMethodContract(Class<?> propertiesLauncher,
            Class<?> jarLauncher, Class<?> launcher) throws IOException {
        try {
            Method propertiesMain = propertiesLauncher.getDeclaredMethod(
                    BOOT_LAUNCHER_MAIN_METHOD, String[].class);
            Method jarMain = jarLauncher.getDeclaredMethod(BOOT_LAUNCHER_MAIN_METHOD,
                    String[].class);
            Method launch = launcher.getDeclaredMethod(BOOT_LAUNCH_METHOD, String[].class);
            if (!Modifier.isStatic(propertiesMain.getModifiers())
                    || !Modifier.isStatic(jarMain.getModifiers())
                    || Modifier.isStatic(launch.getModifiers())
                    || propertiesMain.getReturnType() != void.class
                    || jarMain.getReturnType() != void.class
                    || launch.getReturnType() != void.class
                    || !Modifier.isPublic(propertiesMain.getModifiers())
                    || !Modifier.isPublic(jarMain.getModifiers())) {
                throw new IOException("Spring Boot launcher entry methods have unexpected modifiers");
            }
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Spring Boot launcher entry methods are unavailable", failure);
        }
    }

    private static ClassBinding authenticateClassBinding(Class<?> type, String entry,
            byte[] archiveBytes, Path expectedJar, Identity codeSource) throws IOException {
        URL resource = classResourceUrl(type, entry);
        authenticateJarResourceUrl(resource, expectedJar, entry, type.getName() + " class");
        byte[] loaded = readResource(resource, MAX_CLASS_BYTES,
                type.getName() + " class exceeds its bounded input size");
        if (!Arrays.equals(archiveBytes, loaded)) {
            throw new IOException(type.getName() + " loaded class bytes do not match " + entry);
        }
        return new ClassBinding(type, entry, sha256(archiveBytes), archiveBytes.length, codeSource);
    }

    private static void verifyClassBinding(ClassBinding binding, Path expectedJar)
            throws IOException {
        String label = binding.type().getName() + " class";
        Identity currentCodeSource = authenticateCodeSource(binding.type(),
                identity("producerJar", expectedJar), label);
        if (!sameIdentity(binding.codeSourceIdentity(), currentCodeSource)) {
            throw new IOException(label + " code-source identity drifted");
        }
        URL resource = classResourceUrl(binding.type(), binding.entry());
        authenticateJarResourceUrl(resource, expectedJar, binding.entry(), label);
        byte[] loaded = readResource(resource, MAX_CLASS_BYTES,
                label + " exceeds its bounded input size");
        if (loaded.length != binding.byteLength()
                || !binding.sha256().equals(sha256(loaded))) {
            throw new IOException(label + " bytes drifted after authentication");
        }
    }

    private static void requireBytesEqual(byte[] expected, byte[] actual, String label)
            throws IOException {
        if (!Arrays.equals(expected, actual)) {
            throw new IOException(label + " bytes drifted after authentication");
        }
    }

    private static void requireBoundClassBytes(ClassBinding binding, byte[] actual, String label)
            throws IOException {
        if (actual.length != binding.byteLength() || !binding.sha256().equals(sha256(actual))) {
            throw new IOException(label + " bytes drifted after authentication");
        }
    }

    private static URL resourceUrl(String resourceName) throws IOException {
        URL resource = JavaParityCourseProducer.class.getClassLoader().getResource(resourceName);
        if (resource == null) throw new IOException("missing classpath resource " + resourceName);
        return resource;
    }

    private static URL classResourceUrl(Class<?> type, String entry) throws IOException {
        ClassLoader loader = type.getClassLoader();
        String classpathName = entry.startsWith("BOOT-INF/classes/")
                ? entry.substring("BOOT-INF/classes/".length()) : entry;
        URL resource = loader == null ? null : loader.getResource(entry);
        if (resource == null && loader != null) resource = loader.getResource(classpathName);
        if (resource == null) resource = type.getResource("/" + entry);
        if (resource == null) resource = type.getResource("/" + classpathName);
        if (resource == null) throw new IOException("missing loaded class resource " + entry);
        return resource;
    }

    private static byte[] readResource(URL resource, int limit, String message) throws IOException {
        try {
            java.net.URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            try (InputStream input = connection.getInputStream()) {
                return readBounded(input, limit, message);
            }
        } catch (RuntimeException failure) {
            throw new IOException("unable to read authenticated resource", failure);
        }
    }

    private static byte[] readClasspathResource(String resourceName, int limit, String message)
            throws IOException {
        URL resource = resourceUrl(resourceName);
        return readResource(resource, limit, message);
    }

    private static void authenticateJarResourceUrl(URL resource, Path expectedJar,
            String expectedEntry, String label)
            throws IOException {
        if (resource == null || !"jar".equalsIgnoreCase(resource.getProtocol())) {
            throw new IOException(label + " is not loaded from a jar entry");
        }
        try {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            Path actualJar = Path.of(connection.getJarFileURL().toURI()).toRealPath();
            if (!actualJar.equals(expectedJar.toAbsolutePath().normalize().toRealPath())) {
                throw new IOException(label + " is shadowed by another jar");
            }
            String entryName = connection.getEntryName();
            if (!expectedEntry.equals(entryName)) {
                throw new IOException(label + " URL has an unexpected entry");
            }
        } catch (ClassCastException | URISyntaxException failure) {
            throw new IOException(label + " URL cannot be authenticated", failure);
        }
    }

    private static JarFile openJar(Path jar) throws IOException {
        return new JarFile(jar.toFile(), false, JarFile.OPEN_READ, Runtime.version());
    }

    private static void authenticateArchiveGrammar(JarFile archive) throws IOException {
        Manifest manifest = archive.getManifest();
        if (manifest != null && "true".equalsIgnoreCase(
                manifest.getMainAttributes().getValue("Multi-Release"))) {
            throw new IOException("producer jar must not be multi-release");
        }
        Enumeration<JarEntry> entries = archive.entries();
        List<String> names = new ArrayList<>();
        while (entries.hasMoreElements()) {
            names.add(entries.nextElement().getName());
        }
        authenticateArchiveNames(names);
    }

    private static void authenticateArchiveNames(Iterable<String> entries) throws IOException {
        Set<String> names = new HashSet<>();
        List<String> protectedEntries = List.of(BOOT_JAR_LAUNCHER_CLASS_ENTRY,
                BOOT_LAUNCHER_CLASS_ENTRY, PROPERTIES_LAUNCHER_CLASS_ENTRY, PRODUCER_CLASS_ENTRY,
                COURSE_ENTRY, CFPR_ENTRY);
        for (String name : entries) {
            if (name == null || !names.add(name)) {
                throw new IOException("producer jar contains duplicate entry " + name);
            }
            if (name.startsWith("/") || name.indexOf('\0') >= 0
                    || Arrays.asList(name.split("/", -1)).contains("..")) {
                throw new IOException("producer jar contains an unsafe entry name");
            }
            for (String protectedEntry : protectedEntries) {
                if (isMultiReleaseShadow(name, protectedEntry)
                        || (protectedEntry.startsWith("BOOT-INF/classes/")
                                && isMultiReleaseShadow(name,
                                        protectedEntry.substring("BOOT-INF/classes/".length())))) {
                    throw new IOException("producer jar contains a multi-release shadow for "
                            + protectedEntry);
                }
            }
        }
    }

    private static boolean isMultiReleaseShadow(String name, String protectedEntry) {
        String prefix = "META-INF/versions/";
        if (!name.startsWith(prefix)) return false;
        int versionEnd = name.indexOf('/', prefix.length());
        if (versionEnd < 0 || versionEnd == prefix.length()) return false;
        String version = name.substring(prefix.length(), versionEnd);
        if (!version.chars().allMatch(Character::isDigit)) return false;
        return protectedEntry.equals(name.substring(versionEnd + 1));
    }

    private static Course parseCourse(byte[] resourceBytes, String resourcePath,
            String resourceSha256, AuthenticatedCfpr cfpr) throws IOException {
        Objects.requireNonNull(resourceBytes, "course resource bytes");
        Objects.requireNonNull(cfpr, "authenticated CFPR");
        if (resourceBytes.length > MAX_COURSE_RESOURCE_BYTES) {
            throw new IOException("course resource exceeds its bounded input size");
        }
        String resource = decodeUtf8(resourceBytes, "course resource");
        if (!resource.endsWith("\n") || resource.endsWith("\r\n")) {
            throw new IOException("course resource must end with exactly one LF");
        }
        String document = resource.substring(0, resource.length() - 1);
        if (document.indexOf('\n') >= 0 || document.indexOf('\r') >= 0) {
            throw new IOException("course resource must be one JSON document with one LF");
        }
        requireWellFormedUnicode(resource, "course resource");
        JsonNode root = JSON.readTree(document);
        if (root == null || !root.isObject()
                || !exactKeys(root, Set.of("byteOrder", "cfprSha256", "chunkShape",
                        "coordinates", "stage"))) {
            throw new IOException("course resource has an exact-schema mismatch");
        }
        validateJsonUnicode(root);
        if (!"big-endian-u16".equals(text(root, "byteOrder"))
                || !"canonical-final".equals(text(root, "stage"))) {
            throw new IOException("course resource encoding or stage is malformed");
        }
        String cfprSha256 = text(root, "cfprSha256");
        requireDigest(cfprSha256, "cfprSha256");
        if (!cfprSha256.equals(cfpr.sha256())) {
            throw new IOException("course does not name the authenticated CFPR receipt");
        }
        int chunkBytes = parseChunkBytes(root.get("chunkShape"));
        JsonNode coordinates = root.get("coordinates");
        if (coordinates == null || !coordinates.isArray()
                || coordinates.size() != AUTHENTICATED_COURSE_ROWS) {
            throw new IOException("course must contain exactly nine coordinate rows");
        }
        List<CourseRow> rows = new ArrayList<>(AUTHENTICATED_COURSE_ROWS);
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < coordinates.size(); index++) {
            JsonNode row = coordinates.get(index);
            if (row == null || !row.isObject()
                    || !exactKeys(row, Set.of("blocksSha256", "cx", "cz", "seed"))) {
                throw new IOException("course coordinate row " + index
                        + " has an exact-schema mismatch");
            }
            String blocksSha256 = text(row, "blocksSha256");
            requireDigest(blocksSha256, "coordinates[" + index + "].blocksSha256");
            CourseRow courseRow = new CourseRow(
                    intValue(row.get("seed"), "coordinates[" + index + "].seed"),
                    intValue(row.get("cx"), "coordinates[" + index + "].cx"),
                    intValue(row.get("cz"), "coordinates[" + index + "].cz"),
                    blocksSha256);
            if (!seen.add(courseRow.coordinate().key())) {
                throw new IOException("course repeats " + courseRow.coordinate().key());
            }
            rows.add(courseRow);
        }
        if (!cfpr.rows().equals(rows)) {
            throw new IOException("course coordinate rows do not match authenticated CFPR order");
        }
        String decodedReencoded;
        try {
            decodedReencoded = JSON.writeValueAsString(root);
        } catch (RuntimeException failure) {
            throw new IOException("course resource cannot be canonically re-encoded", failure);
        }
        if (!document.equals(decodedReencoded)) {
            throw new IOException("course resource is not canonical JSON after decode/re-encode");
        }
        String canonical = canonicalCourseJson(cfprSha256, rows);
        if (!document.equals(canonical)) {
            throw new IOException("course resource is not canonical declaration-order JSON");
        }
        if (resourceSha256 == null) throw new IOException("course resource identity is missing");
        requireDigest(resourceSha256, "course resource identity");
        if (!resourceSha256.equals(sha256(resourceBytes))) {
            throw new IOException("course resource identity digest mismatch");
        }
        return new Course(cfprSha256, rows, canonical + "\n", chunkBytes,
                resourcePath, resourceSha256);
    }

    private static AuthenticatedCfpr authenticateCfpr(byte[] bytes, String path) throws IOException {
        if (bytes == null || bytes.length != AUTHENTICATED_CFPR_BYTES) {
            throw new IOException("CFPR byte length is not the authenticated length");
        }
        String digest = sha256(bytes);
        String text = decodeUtf8(bytes, "CFPR resource");
        if (!text.endsWith("\n") || text.endsWith("\r\n")
                || text.indexOf('\r') >= 0) {
            throw new IOException("CFPR resource must use LF-delimited UTF-8 grammar");
        }
        requireWellFormedUnicode(text, "CFPR resource");
        String[] lines = text.split("\\n", -1);
        if (lines.length != AUTHENTICATED_COURSE_ROWS + 5 || !lines[0].equals("CFPR1")
                || !lines[2].equals(CFPR_COLUMNS)
                || !lines[AUTHENTICATED_COURSE_ROWS + 3].equals("total|9")
                || !lines[lines.length - 1].isEmpty()) {
            throw new IOException("CFPR resource has an unexpected authenticated grammar");
        }
        String[] source = lines[1].split("\\|", -1);
        if (source.length != 4 || !source[0].equals("source")
                || !source[1].equals("sha256-path-length-bytes-v1")
                || !source[2].equals(String.valueOf(WorldBaseline.GENERATOR_SOURCE_FILE_COUNT))
                || !source[3].equals(WorldBaseline.GENERATOR_SOURCE_SHA256)) {
            throw new IOException("CFPR source identity row is malformed");
        }
        List<CourseRow> rows = new ArrayList<>(AUTHENTICATED_COURSE_ROWS);
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < AUTHENTICATED_COURSE_ROWS; index++) {
            String[] fields = lines[index + 3].split("\\|", -1);
            if (fields.length != 20 || !fields[0].equals("r")
                    || !fields[1].equals(index < 4 ? "carrier-origin" : "monument-probe")) {
                throw new IOException("CFPR row " + index + " has an unexpected grammar");
            }
            int seed = parseCfprInteger(fields[2], "CFPR row " + index + " seed");
            int chunkX = parseCfprInteger(fields[3], "CFPR row " + index + " chunkX");
            int chunkZ = parseCfprInteger(fields[4], "CFPR row " + index + " chunkZ");
            requireDigest(fields[5], "CFPR row " + index + " blocksSha256");
            for (int field = 6; field <= 18; field++) {
                if (field == 6 || field == 8 || field == 10 || field == 12 || field == 13) {
                    requireCfprNonNegativeInteger(fields[field], "CFPR row " + index
                            + " field " + field);
                } else {
                    requireDigest(fields[field], "CFPR row " + index + " field " + field);
                }
            }
            String[] continuation = fields[19].split(",", -1);
            if (continuation.length != 8) {
                throw new IOException("CFPR row " + index + " continuation count is not eight");
            }
            for (String token : continuation) {
                if (!token.matches("[0-9a-f]{16}")) {
                    throw new IOException("CFPR row " + index + " continuation is malformed");
                }
            }
            CourseRow row = new CourseRow(seed, chunkX, chunkZ, fields[5]);
            if (!seen.add(row.coordinate().key())) {
                throw new IOException("CFPR repeats " + row.coordinate().key());
            }
            rows.add(row);
        }
        return new AuthenticatedCfpr(path, bytes.clone(), digest, List.copyOf(rows));
    }

    private static int parseCfprInteger(String value, String label) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IOException(label + " is not a signed int32", failure);
        }
    }

    private static void requireCfprNonNegativeInteger(String value, String label)
            throws IOException {
        if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')
                || !value.chars().allMatch(Character::isDigit)) {
            throw new IOException(label + " is not a canonical non-negative integer");
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IOException(label + " is outside signed integer bounds", failure);
        }
    }

    private static int parseChunkBytes(JsonNode shape) throws IOException {
        if (shape == null || !shape.isArray() || shape.size() != 3
                || intValue(shape.get(0), "chunkShape[0]") != Blocks.CHUNK_X
                || intValue(shape.get(1), "chunkShape[1]") != Blocks.CHUNK_Y
                || intValue(shape.get(2), "chunkShape[2]") != Blocks.CHUNK_Z) {
            throw new IOException("course resource chunk shape is not the canonical chunk shape");
        }
        try {
            long cells = Math.multiplyExact(
                    Math.multiplyExact((long) intValue(shape.get(0), "chunkShape[0]"),
                            intValue(shape.get(1), "chunkShape[1]")),
                    intValue(shape.get(2), "chunkShape[2]"));
            return Math.toIntExact(Math.multiplyExact(cells, Short.BYTES));
        } catch (ArithmeticException failure) {
            throw new IOException("course chunk shape is too large", failure);
        }
    }

    private static boolean exactKeys(JsonNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        for (String name : node.propertyNames()) actual.add(name);
        return actual.size() == expected.size() && actual.equals(expected);
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) throw new IOException("course field is not text: " + field);
        return value.asString();
    }

    private static int intValue(JsonNode node, String label) throws IOException {
        if (node == null || !node.isIntegralNumber()) throw new IOException(label + " is not an integer");
        long value = node.longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException(label + " is outside signed int32");
        }
        return (int) value;
    }

    private static String canonicalCourseJson(String cfprSha256, List<CourseRow> rows) {
        StringBuilder json = new StringBuilder(1_024);
        json.append("{\"byteOrder\":\"big-endian-u16\",\"cfprSha256\":")
                .append(jsonString(cfprSha256))
                .append(",\"chunkShape\":[16,384,16],\"coordinates\":[");
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) json.append(',');
            CourseRow row = rows.get(index);
            json.append("{\"cx\":").append(row.cx()).append(",\"cz\":")
                    .append(row.cz()).append(",\"seed\":").append(row.seed())
                    .append(",\"blocksSha256\":").append(jsonString(row.blocksSha256()))
                    .append('}');
        }
        return json.append("],\"stage\":\"canonical-final\"}").toString();
    }

    private static String receiptJson(Path artifact, int byteLength, String sha256,
            Course course, List<Identity> identities, SourceBinding producerSource)
            throws IOException {
        if (byteLength != course.aggregateBytes()) {
            throw new IOException("receipt byte length is not derived from course rows");
        }
        Identity javaExecutable = identityNamed(identities, "javaExecutable");
        Identity javaRuntime = identityNamed(identities, "javaRuntime");
        Identity producerJar = identityNamed(identities, "producerJar");
        StringBuilder json = new StringBuilder(8_192);
        json.append("{\"artifactPath\":").append(jsonString(artifact.toString()))
                .append(",\"byteLength\":").append(byteLength)
                .append(",\"course\":").append(canonicalReceiptCourseJson(course))
                .append(",\"format\":").append(FORMAT)
                .append(",\"producer\":").append(jsonString(PRODUCER))
                .append(",\"producerIdentity\":{\"javaExecutable\":")
                .append(identityJson(javaExecutable))
                .append(",\"javaRuntime\":").append(identityJson(javaRuntime))
                .append(",\"producerJar\":").append(identityJson(producerJar))
                .append(",\"producerSource\":").append(sourceJson(producerSource, identities))
                .append('}')
                .append(",\"sha256\":").append(jsonString(sha256))
                .append("}\n");
        return canonicalReceiptRoundTrip(json.toString());
    }

    private static String canonicalReceiptCourseJson(Course course) {
        StringBuilder json = new StringBuilder(1_024);
        json.append("{\"byteOrder\":\"big-endian-u16\",\"cfprSha256\":")
                .append(jsonString(course.cfprSha256()))
                .append(",\"chunkShape\":[16,384,16],\"coordinates\":[");
        for (int index = 0; index < course.rows().size(); index++) {
            if (index > 0) json.append(',');
            CourseRow row = course.rows().get(index);
            json.append("{\"blocksSha256\":").append(jsonString(row.blocksSha256()))
                    .append(",\"cx\":").append(row.cx())
                    .append(",\"cz\":").append(row.cz())
                    .append(",\"seed\":").append(row.seed())
                    .append('}');
        }
        return json.append("],\"stage\":\"canonical-final\"}").toString();
    }

    private static Identity identityNamed(List<Identity> identities, String name) {
        return identities.stream().filter(identity -> identity.name().equals(name)).findFirst().orElseThrow();
    }

    private static String identityJson(Identity identity) {
        return "{\"path\":" + jsonString(identity.path().toString())
                + ",\"sha256\":" + jsonString(identity.sha256()) + "}";
    }

    private static String sourceJson(SourceBinding source, List<Identity> identities)
            throws IOException {
        if (source.fileIdentity() != null) {
            return "{\"path\":"
                    + jsonString(source.fileIdentity().path().toString())
                    + ",\"sha256\":" + jsonString(source.fileIdentity().sha256()) + "}";
        }
        Identity producerJar = identityNamed(identities, "producerJar");
        if (source.archivePath() == null || !producerJar.path().equals(source.archivePath())) {
            throw new IOException("producer source archive identity is not the retained jar");
        }
        return "{\"path\":" + jsonString(source.archivePath().toString())
                + ",\"sha256\":" + jsonString(producerJar.sha256()) + "}";
    }

    private static String jsonString(String value) {
        if (value == null) throw new IllegalArgumentException("JSON string is null");
        requireWellFormedUnicode(value, "JSON string");
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                escaped.append(character).append(value.charAt(++index));
                continue;
            }
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format(Locale.ROOT,
                            "\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static Path absoluteNormalized(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("publication path is null");
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || absolute.getFileName() == null) return absolute;
        if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) return absolute;
        return parent.toRealPath().resolve(absolute.getFileName()).normalize();
    }

    private static void rejectPublicationPaths(Path artifact, Path receipt) throws IOException {
        if (artifact.equals(receipt)) throw new IOException("artifact and receipt paths overlap");
        validatePublicationParent(artifact.getParent());
        validatePublicationParent(receipt.getParent());
        if (artifact.getFileName() == null || receipt.getFileName() == null) {
            throw new IOException("publication target has no relative filename");
        }
        validatePublicationBasename(artifact.getFileName().toString(), "artifact");
        validatePublicationBasename(receipt.getFileName().toString(), "receipt");
        String owner = ownerMarkerName(artifact);
        if (artifact.getParent().equals(receipt.getParent())
                && owner.equals(receipt.getFileName().toString())) {
            throw new IOException("receipt path collides with its ownership marker");
        }
    }

    private static void validatePublicationBasename(String name, String label) throws IOException {
        try {
            requireWellFormedUnicode(name, label + " filename");
        } catch (IllegalArgumentException failure) {
            throw new IOException(label + " filename has malformed Unicode", failure);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('\0') >= 0 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.codePoints().anyMatch(Character::isISOControl)
                || name.length() > MAX_PUBLICATION_BASENAME_CHARS) {
            throw new IOException(label + " filename is outside its safe bounded form");
        }
        if (name.length() + PUBLICATION_OWNER_SUFFIX.length() + 1
                > MAX_PUBLICATION_BASENAME_CHARS) {
            throw new IOException("artifact filename leaves no bounded ownership-marker name");
        }
    }

    private static String ownerMarkerName(Path artifact) {
        return "." + artifact.getFileName() + PUBLICATION_OWNER_SUFFIX;
    }

    private static byte[] ownerMarkerBytes(Path artifact, Path receipt,
            byte[] artifactBytes, byte[] receiptBytes) throws IOException {
        String marker;
        try {
            marker = PUBLICATION_OWNER_MAGIC + "\n"
                    + "artifactPath=" + jsonString(artifact.toString()) + "\n"
                    + "artifactLength=" + artifactBytes.length + "\n"
                    + "artifactSha256=" + sha256(artifactBytes) + "\n"
                    + "receiptPath=" + jsonString(receipt.toString()) + "\n"
                    + "receiptLength=" + receiptBytes.length + "\n"
                    + "receiptSha256=" + sha256(receiptBytes) + "\n";
        } catch (IllegalArgumentException failure) {
            throw new IOException("publication paths contain malformed Unicode", failure);
        }
        return marker.getBytes(StandardCharsets.UTF_8);
    }

    private static void validatePublicationParent(Path parent) throws IOException {
        if (parent == null) throw new IOException("publication path has no parent");
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("publication parent is not an existing directory: " + parent);
        }
        for (Path current = parent; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("publication path traverses a symlink: " + current);
            }
        }
    }

    private static void publishArtifactThenReceipt(Path requestedArtifact, Path requestedReceipt,
            byte[] artifactBytes, byte[] receiptBytes, PublicationHook afterArtifact,
            WriteHook writeHook)
            throws IOException {
        Objects.requireNonNull(artifactBytes, "artifact bytes");
        Objects.requireNonNull(receiptBytes, "receipt bytes");
        if (artifactBytes.length > MAX_COURSE_ROWS * CANONICAL_CHUNK_BYTES) {
            throw new IOException("artifact allocation exceeds its bounded course capacity");
        }
        if (receiptBytes.length > MAX_RECEIPT_BYTES) {
            throw new IOException("receipt allocation exceeds its bounded publication capacity");
        }
        Path artifact = absoluteNormalized(requestedArtifact);
        Path receipt = absoluteNormalized(requestedReceipt);
        rejectPublicationPaths(artifact, receipt);
        byte[] ownerBytes = ownerMarkerBytes(artifact, receipt, artifactBytes, receiptBytes);
        String ownerName = ownerMarkerName(artifact);
        Path artifactParent = artifact.getParent();
        Path receiptParent = receipt.getParent();
        boolean sameParent = artifactParent.equals(receiptParent);

        try (DirectoryHandle artifactDirectory = openDirectory(artifactParent)) {
            if (sameParent) {
                publishWithDirectories(artifact, receipt, artifactBytes, receiptBytes,
                        ownerName, ownerBytes, artifactDirectory, artifactDirectory,
                        afterArtifact, writeHook);
            } else {
                try (DirectoryHandle receiptDirectory = openDirectory(receiptParent)) {
                    publishWithDirectories(artifact, receipt, artifactBytes, receiptBytes,
                            ownerName, ownerBytes, artifactDirectory, receiptDirectory,
                            afterArtifact, writeHook);
                }
            }
        }
    }

    private static void publishWithDirectories(Path artifact, Path receipt, byte[] artifactBytes,
            byte[] receiptBytes, String ownerName, byte[] ownerBytes,
            DirectoryHandle artifactDirectory, DirectoryHandle receiptDirectory,
            PublicationHook afterArtifact, WriteHook writeHook) throws IOException {
        TargetState artifactState = artifactDirectory.inspect(artifact.getFileName());
        TargetState receiptState = receiptDirectory.inspect(receipt.getFileName());
        if (!artifactState.present() && receiptState.present()) {
            throw new IOException("receipt exists without its artifact; preserving both targets");
        }
        if (artifactState.present() && receiptState.present()
                && Objects.equals(artifactState.fileKey(), receiptState.fileKey())) {
            throw new IOException("artifact and receipt are aliases; preserving target");
        }
        if (receiptState.present()) {
            receiptDirectory.verifyExact(receipt.getFileName(), receiptBytes,
                    "existing receipt");
        }
        TargetState ownerState = artifactDirectory.inspect(Path.of(ownerName));
        if (ownerState.present()) {
            artifactDirectory.verifyExact(Path.of(ownerName), ownerBytes,
                    "existing ownership marker");
        }
        if (artifactState.present()) {
            if (ownerState.present() && artifactState.size() < artifactBytes.length
                    && !artifactDirectory.matchesExact(
                            artifact.getFileName(), artifactBytes, "owned artifact")) {
                artifactDirectory.rewriteExact(artifact.getFileName(), artifactBytes,
                        "owned artifact recovery", writeHook);
            } else {
                artifactDirectory.verifyExact(artifact.getFileName(), artifactBytes,
                        "existing artifact");
            }
        } else {
            if (!ownerState.present()) {
                artifactDirectory.createExact(Path.of(ownerName), ownerBytes,
                        "ownership marker", null);
                artifactDirectory.forceDirectory();
                ownerState = new TargetState(true, null, ownerBytes.length);
            }
            artifactDirectory.createExact(artifact.getFileName(), artifactBytes, "artifact",
                    writeHook);
        }
        if (!ownerState.present()) {
            artifactDirectory.createExact(Path.of(ownerName), ownerBytes,
                    "ownership marker", null);
        }
        artifactDirectory.forceDirectory();

        if (afterArtifact != null) afterArtifact.afterArtifact();
        artifactDirectory.assertBound();
        receiptDirectory.assertBound();

        receiptState = receiptDirectory.inspect(receipt.getFileName());
        if (receiptState.present()) {
            receiptDirectory.verifyExact(receipt.getFileName(), receiptBytes,
                    "existing receipt");
        } else {
            receiptDirectory.createExact(receipt.getFileName(), receiptBytes, "receipt", null);
        }
        receiptDirectory.forceDirectory();
        artifactDirectory.assertBound();
        receiptDirectory.assertBound();
        artifactDirectory.verifyExact(artifact.getFileName(), artifactBytes, "published artifact");
        receiptDirectory.verifyExact(receipt.getFileName(), receiptBytes, "published receipt");
        artifactDirectory.verifyExact(Path.of(ownerName), ownerBytes,
                "published ownership marker");
        artifactDirectory.deleteExact(Path.of(ownerName), ownerBytes, "ownership marker");
        artifactDirectory.forceDirectory();
        artifactDirectory.assertBound();
    }

    private static DirectoryHandle openDirectory(Path parent) throws IOException {
        validatePublicationParent(parent);
        BasicFileAttributes before = attributes(parent);
        if (before.fileKey() == null) throw new IOException("publication parent has no stable identity");
        FileChannel forceChannel = null;
        DirectoryStream<Path> stream = null;
        try {
            forceChannel = FileChannel.open(parent,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            stream = Files.newDirectoryStream(parent);
            BasicFileAttributes after = attributes(parent);
            if (after.isSymbolicLink() || !after.isDirectory()
                    || !before.fileKey().equals(after.fileKey())) {
                throw new IOException("publication parent rebound while being retained");
            }
            if (!(stream instanceof SecureDirectoryStream<?>)) {
                stream.close();
                stream = null;
                return new DirectoryHandle(parent, before.fileKey(), forceChannel, null);
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) stream;
            return new DirectoryHandle(parent, before.fileKey(), forceChannel, secure);
        } catch (IOException | RuntimeException failure) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (forceChannel != null) {
                try {
                    forceChannel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure instanceof IOException io) throw io;
            throw new IOException("unable to retain publication parent", failure);
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static final class DirectoryHandle implements AutoCloseable {
        private final Path parent;
        private final Object fileKey;
        private final FileChannel forceChannel;
        private final SecureDirectoryStream<Path> directory;

        private DirectoryHandle(Path parent, Object fileKey, FileChannel forceChannel,
                SecureDirectoryStream<Path> directory) {
            this.parent = parent;
            this.fileKey = fileKey;
            this.forceChannel = forceChannel;
            this.directory = directory;
        }

        private void assertBound() throws IOException {
            BasicFileAttributes current;
            try {
                current = attributes(parent);
            } catch (NoSuchFileException absent) {
                throw new IOException("publication parent identity drifted: " + parent, absent);
            }
            if (current.isSymbolicLink() || !current.isDirectory()
                    || !fileKey.equals(current.fileKey())) {
                throw new IOException("publication parent identity drifted: " + parent);
            }
        }

        private TargetState inspect(Path relative) throws IOException {
            assertBound();
            try {
                BasicFileAttributes state = relativeAttributes(relative);
                if (state.fileKey() == null) throw new IOException("publication target has no stable identity");
                if (!state.isRegularFile()) {
                    throw new IOException("publication target is not a regular file; preserving it");
                }
                return new TargetState(true, state.fileKey(), state.size());
            } catch (NoSuchFileException absent) {
                return new TargetState(false, null, 0L);
            }
        }

        private void createExact(Path relative, byte[] expected, String label, WriteHook writeHook)
                throws IOException {
            assertBound();
            try {
                Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
                try (SeekableByteChannel channel = openRelative(relative, options)) {
                    if (!(channel instanceof FileChannel file)) {
                        throw new IOException("publication target is not a forceable file channel");
                    }
                    writeExact(file, expected, writeHook);
                    file.force(true);
                }
            } catch (FileAlreadyExistsException race) {
                verifyExact(relative, expected, label + " appeared concurrently");
                return;
            } catch (UnsupportedOperationException failure) {
                throw new IOException("secure relative publication is unsupported", failure);
            }
            verifyExact(relative, expected, label);
        }

        private void rewriteExact(Path relative, byte[] expected, String label, WriteHook writeHook)
                throws IOException {
            assertBound();
            TargetState before = inspect(relative);
            if (!before.present()) throw new IOException(label + " disappeared; preserving target");
            try (SeekableByteChannel channel = openRelative(relative,
                    Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS))) {
                if (!(channel instanceof FileChannel file)) {
                    throw new IOException(label + " is not a forceable file channel");
                }
                writeExact(file, expected, writeHook);
                file.force(true);
            } catch (UnsupportedOperationException failure) {
                throw new IOException("secure relative rewrite is unsupported", failure);
            }
            TargetState after = inspect(relative);
            if (!before.fileKey().equals(after.fileKey())) {
                throw new IOException(label + " identity changed while recovering; preserving target");
            }
            verifyExact(relative, expected, label);
        }

        private static void writeExact(FileChannel file, byte[] expected, WriteHook writeHook)
                throws IOException {
            ByteBuffer input = ByteBuffer.wrap(expected);
            int bytesWritten = 0;
            while (input.hasRemaining()) {
                int end = Math.min(input.limit(), input.position() + PUBLICATION_WRITE_CHUNK_BYTES);
                int previousLimit = input.limit();
                input.limit(end);
                while (input.hasRemaining()) {
                    int written = file.write(input);
                    if (written <= 0) throw new IOException("publication made no write progress");
                    bytesWritten = Math.addExact(bytesWritten, written);
                }
                input.limit(previousLimit);
                if (writeHook != null) writeHook.afterBytes(bytesWritten);
            }
        }

        private void verifyExact(Path relative, byte[] expected, String label) throws IOException {
            assertBound();
            TargetState before = inspect(relative);
            if (!before.present() || before.size() != expected.length) {
                throw new IOException(label + " does not exactly match; preserving target");
            }
            String expectedSha256 = sha256(expected);
            MessageDigest digest = sha256Digest();
            try (SeekableByteChannel channel = openRelative(relative,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                if (!(channel instanceof FileChannel file)) {
                    throw new IOException(label + " is not a forceable file channel");
                }
                ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
                long bytesRead = 0L;
                while (true) {
                    int read = file.read(buffer);
                    if (read < 0) break;
                    if (read == 0) continue;
                    buffer.flip();
                    digest.update(buffer);
                    bytesRead = Math.addExact(bytesRead, read);
                    buffer.clear();
                }
                if (bytesRead != expected.length) {
                    throw new IOException(label + " length changed; preserving target");
                }
            } catch (UnsupportedOperationException failure) {
                throw new IOException("secure relative verification is unsupported", failure);
            } catch (ArithmeticException failure) {
                throw new IOException(label + " length overflow; preserving target", failure);
            }
            TargetState after = inspect(relative);
            if (!Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !expectedSha256.equals(hex(digest.digest()))) {
                throw new IOException(label + " identity or digest mismatch; preserving target");
            }
        }

        private boolean matchesExact(Path relative, byte[] expected, String label) {
            try {
                verifyExact(relative, expected, label);
                return true;
            } catch (IOException mismatch) {
                return false;
            }
        }

        private BasicFileAttributes relativeAttributes(Path relative) throws IOException {
            if (directory != null) {
                BasicFileAttributeView view = directory.getFileAttributeView(relative,
                        BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (view == null) throw new IOException("secure relative attributes are unavailable");
                return view.readAttributes();
            }
            return Files.readAttributes(relativeTarget(relative), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        }

        private SeekableByteChannel openRelative(Path relative, Set<OpenOption> options)
                throws IOException {
            assertBound();
            if (directory != null) return directory.newByteChannel(relative, options);
            return Files.newByteChannel(relativeTarget(relative), options);
        }

        private void deleteRelative(Path relative) throws IOException {
            assertBound();
            if (directory != null) directory.deleteFile(relative);
            else Files.delete(relativeTarget(relative));
            assertBound();
        }

        private Path relativeTarget(Path relative) throws IOException {
            if (relative == null || relative.isAbsolute() || relative.getNameCount() != 1) {
                throw new IOException("publication target is not a single relative filename");
            }
            Path target = parent.resolve(relative).normalize();
            if (!parent.equals(target.getParent())) {
                throw new IOException("publication target escapes its retained parent");
            }
            return target;
        }

        private void forceDirectory() throws IOException {
            assertBound();
            forceChannel.force(true);
        }

        private void deleteExact(Path relative, byte[] expected, String label) throws IOException {
            verifyExact(relative, expected, label + " before deletion");
            try {
                deleteRelative(relative);
            } catch (NoSuchFileException absent) {
                throw new IOException(label + " disappeared before deletion", absent);
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            if (directory != null) {
                try {
                    directory.close();
                } catch (IOException closeFailure) {
                    failure = closeFailure;
                }
            }
            try {
                forceChannel.close();
            } catch (IOException closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
            if (failure != null) throw failure;
        }
    }

    private static void rejectDuplicateIdentityPaths(List<Identity> identities, Path artifact,
            Path receipt) throws IOException {
        Set<Path> paths = new HashSet<>();
        paths.add(artifact);
        paths.add(receipt);
        for (Identity identity : identities) {
            if (!paths.add(identity.path())) throw new IOException("producer identity paths are not distinct");
        }
    }

    private static void verifyIdentities(List<Identity> identities) throws IOException {
        for (Identity identity : identities) {
            Identity current = identity(identity.name(), identity.path());
            if (!sameIdentity(identity, current)) {
                throw new IOException("producer identity drift: " + identity.name());
            }
        }
    }

    private static boolean sameIdentity(Identity first, Identity second) {
        return first.path().equals(second.path())
                && Objects.equals(first.fileKey(), second.fileKey())
                && first.byteLength() == second.byteLength()
                && first.sha256().equals(second.sha256());
    }

    private static Identity identity(String name, Path requested) throws IOException {
        BasicFileAttributes requestedAttributes = attributes(requested);
        if (requestedAttributes.isSymbolicLink()) {
            throw new IOException("producer identity path is a symlink: " + requested);
        }
        if (requestedAttributes.fileKey() == null) {
            throw new IOException("producer identity has no stable file identity: " + requested);
        }
        Path path = requested.toRealPath();
        BasicFileAttributes requestedAfter = attributes(requested);
        if (!requestedAttributes.fileKey().equals(requestedAfter.fileKey())) {
            throw new IOException("producer identity path drifted: " + requested);
        }
        BasicFileAttributes before = attributes(path);
        if (before.isSymbolicLink() || !before.isRegularFile() || before.fileKey() == null) {
            throw new IOException("producer identity is not a stable regular file: " + path);
        }
        String sha = sha256File(path, before.fileKey());
        BasicFileAttributes after = attributes(path);
        if (!before.fileKey().equals(after.fileKey()) || before.size() != after.size()) {
            throw new IOException("producer identity drift: " + name);
        }
        return new Identity(name, path, before.fileKey(), before.size(), sha);
    }

    private static Path producerJarFromProtectionDomain() throws IOException {
        CodeSource codeSource = JavaParityCourseProducer.class.getProtectionDomain().getCodeSource();
        Path jar = codeSourcePath(codeSource, "producer");
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)
                || !jar.getFileName().toString().endsWith(".jar")) {
            throw new IOException("producer code source is not an executable jar: " + jar);
        }
        return jar;
    }

    private static Path codeSourcePath(CodeSource codeSource, String label) throws IOException {
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IOException(label + " code source is missing");
        }
        URL location = codeSource.getLocation();
        try {
            Path candidate;
            if ("jar".equalsIgnoreCase(location.getProtocol())) {
                candidate = Path.of(((JarURLConnection) location.openConnection()).getJarFileURL()
                        .toURI());
            } else if ("file".equalsIgnoreCase(location.getProtocol())) {
                candidate = Path.of(URI.create(location.toExternalForm()));
            } else {
                throw new IOException(label + " code source is not file-backed: " + location);
            }
            return candidate.toRealPath();
        } catch (URISyntaxException | ClassCastException failure) {
            throw new IOException("unable to resolve " + label + " code source", failure);
        }
    }

    private static void requireRegularJarEntry(JarFile archive, String name) throws IOException {
        JarEntry entry = archive.getJarEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("retained producer jar is missing entry " + name);
        }
    }

    private static byte[] readJarEntry(JarFile archive, String name, int limit, String message)
            throws IOException {
        JarEntry entry = archive.getJarEntry(name);
        if (entry == null || entry.isDirectory()) throw new IOException("missing jar entry " + name);
        try (InputStream input = archive.getInputStream(entry)) {
            return readBounded(input, limit, message);
        }
    }

    private static byte[] readBounded(InputStream input, int limit, String message) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit || input.read() != -1) throw new IOException(message);
        return bytes;
    }

    private static String decodeUtf8(byte[] bytes, String label) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) {
            throw new IOException(label + " is not valid UTF-8", failure);
        }
    }

    private static void requireDigest(String value, String label) throws IOException {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IOException(label + " is not a lowercase SHA-256 digest");
        }
    }

    private static void validateJsonUnicode(JsonNode node) throws IOException {
        if (node.isTextual()) {
            requireWellFormedUnicode(node.asString(), "JSON text value");
        } else if (node.isObject()) {
            for (String name : node.propertyNames()) {
                requireWellFormedUnicode(name, "JSON property name");
                validateJsonUnicode(node.get(name));
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) validateJsonUnicode(value);
        }
    }

    private static void requireWellFormedUnicode(String value, String label) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains a lone high surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(label + " contains a lone low surrogate");
            }
        }
    }

    private static Path javaExecutable() throws IOException {
        Path fallback = Path.of(System.getProperty("java.home"), "bin", executableName());
        String processCommand = ProcessHandle.current().info().command().orElse(null);
        Path requested = processCommand == null || processCommand.isBlank()
                ? fallback : Path.of(processCommand);
        try {
            return requested.toRealPath();
        } catch (NoSuchFileException absent) {
            return fallback.toRealPath();
        }
    }

    private static String executableName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
    }

    private static Path javaRuntime() throws IOException {
        Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
        Path modules = javaHome.resolve("lib/modules");
        if (Files.isRegularFile(modules, LinkOption.NOFOLLOW_LINKS)) return modules;
        Path release = javaHome.resolve("release");
        if (Files.isRegularFile(release, LinkOption.NOFOLLOW_LINKS)) return release;
        throw new IOException("Java runtime identity file is missing under " + javaHome);
    }

    private static SourceBinding producerSourceBinding(Path producerJar,
            ClassBinding producerClassBinding) throws IOException {
        Path source = canonicalProducerSourcePath(producerJar);
        if (source != null) {
            return new SourceBinding(null, null, identity("producerSource", source));
        }
        if (producerClassBinding == null) {
            throw new IOException("canonical producer source is missing and no archive binding exists");
        }
        return new SourceBinding(producerJar.toRealPath(), producerClassBinding, null);
    }

    private static Path canonicalProducerSourcePath(Path producerJar) throws IOException {
        if (producerJar == null) throw new IOException("producer jar path is null");
        Path jar = producerJar.toAbsolutePath().normalize().toRealPath();
        Path libs = jar.getParent();
        Path build = libs == null ? null : libs.getParent();
        Path projectRoot = build == null ? null : build.getParent();
        if (libs == null || build == null || projectRoot == null
                || !"libs".equals(libs.getFileName().toString())
                || !"build".equals(build.getFileName().toString())) {
            return null;
        }
        Path candidate = projectRoot.resolve(PRODUCER_SOURCE_RELATIVE).normalize();
        if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(candidate)) {
            return candidate.toRealPath();
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        return hex(sha256Digest().digest(bytes));
    }

    private static String sha256File(Path path, Object expectedKey) throws IOException {
        MessageDigest digest = sha256Digest();
        try (FileChannel input = FileChannel.open(path,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            BasicFileAttributes opened = attributes(path);
            if (!opened.isRegularFile() || !expectedKey.equals(opened.fileKey())) {
                throw new IOException("identity drift while reading " + path);
            }
            ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
            while (input.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Error error) throw error;
        if (failure instanceof Exception exception) throw exception;
        throw new Exception(failure);
    }

    @FunctionalInterface
    public interface RowGenerator {
        byte[] generate(int worker, CourseRow row) throws Throwable;
    }

    @FunctionalInterface
    interface PublicationHook {
        void afterArtifact() throws IOException;
    }

    @FunctionalInterface
    interface WriteHook {
        void afterBytes(int bytesWritten) throws IOException;
    }

    @FunctionalInterface
    interface IdentityMutation {
        void afterCapture() throws IOException;
    }

    public record Coordinate(int seed, int cx, int cz) {
        private String key() {
            return seed + ":" + cx + ":" + cz;
        }
    }

    public record CourseRow(int seed, int cx, int cz, String blocksSha256) {
        public CourseRow {
            Objects.requireNonNull(blocksSha256, "blocksSha256");
            if (!blocksSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("blocksSha256 must be lowercase SHA-256");
            }
        }

        public Coordinate coordinate() {
            return new Coordinate(seed, cx, cz);
        }
    }

    public record Course(String cfprSha256, List<CourseRow> rows, String canonicalJson,
            int chunkBytes, String resourcePath, String resourceSha256) {
        public Course {
            Objects.requireNonNull(cfprSha256, "cfprSha256");
            Objects.requireNonNull(rows, "rows");
            if (rows.size() > MAX_COURSE_ROWS) {
                throw new IllegalArgumentException("course row count exceeds its authenticated bound");
            }
            rows = List.copyOf(rows);
            Objects.requireNonNull(canonicalJson, "canonicalJson");
            Objects.requireNonNull(resourcePath, "resourcePath");
            Objects.requireNonNull(resourceSha256, "resourceSha256");
            if (!cfprSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("cfprSha256 must be lowercase SHA-256");
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("course rows are empty");
            if (chunkBytes <= 0 || chunkBytes > CANONICAL_CHUNK_BYTES) {
                throw new IllegalArgumentException("chunkBytes exceeds its authenticated bound");
            }
            if (!resourceSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("resourceSha256 must be lowercase SHA-256");
            }
        }

        public int cardinality() {
            return rows.size();
        }

        public int aggregateBytes() {
            int bytes = 0;
            for (int index = 0; index < rows.size(); index++) {
                bytes = Math.addExact(bytes, chunkBytes);
            }
            return bytes;
        }

        public List<Coordinate> coordinates() {
            List<Coordinate> coordinates = new ArrayList<>(rows.size());
            for (CourseRow row : rows) coordinates.add(row.coordinate());
            return List.copyOf(coordinates);
        }
    }

    public record WorkerTelemetry(int worker, List<Integer> rowIndexes, int completedRows,
            int failedRows, boolean distinct, boolean overlap, boolean daemon,
            long contextSubmittedTasks, long contextStartedTasks, long contextCompletedTasks,
            long contextCancelledTasks, long contextFailedTasks, long contextRejectedTasks,
            long contextParallelBuilds, long contextInlineBuilds, int contextTrackedFutures,
            int contextActiveThreads, int contextQueuedTasks, boolean contextClosed,
            boolean contextTerminated, boolean contextLiveThreads) {
        public WorkerTelemetry(int worker, List<Integer> rowIndexes, int completedRows,
                int failedRows, boolean distinct, boolean overlap, boolean daemon) {
            this(worker, rowIndexes, completedRows, failedRows, distinct, overlap, daemon,
                    0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0, true, true, false);
        }

        private WorkerTelemetry(int worker, List<Integer> rowIndexes, int completedRows,
                int failedRows, boolean distinct, boolean overlap, boolean daemon,
                Mc263FeaturesRegionBridge.InputBuilderTelemetry context,
                boolean contextLiveThreads) {
            this(worker, rowIndexes, completedRows, failedRows, distinct, overlap, daemon,
                    context.submittedTasks(), context.startedTasks(), context.completedTasks(),
                    context.cancelledTasks(), context.failedTasks(), context.rejectedTasks(),
                    context.parallelBuilds(), context.inlineBuilds(), context.trackedFutures(),
                    context.activeThreads(), context.queuedTasks(), context.closed(),
                    context.terminated(), contextLiveThreads);
        }

        public WorkerTelemetry {
            rowIndexes = List.copyOf(rowIndexes);
            if (worker < 0 || completedRows < 0 || failedRows < 0
                    || contextSubmittedTasks < 0 || contextStartedTasks < 0
                    || contextCompletedTasks < 0 || contextCancelledTasks < 0
                    || contextFailedTasks < 0 || contextRejectedTasks < 0
                    || contextParallelBuilds < 0 || contextInlineBuilds < 0
                    || contextTrackedFutures < 0 || contextActiveThreads < 0
                    || contextQueuedTasks < 0) {
                throw new IllegalArgumentException("worker telemetry contains a negative bound");
            }
        }

        public boolean contextQuiescent() {
            return contextClosed && contextTerminated && contextTrackedFutures == 0
                    && contextActiveThreads == 0 && contextQueuedTasks == 0
                    && !contextLiveThreads;
        }
    }

    public record GenerationReport(byte[] aggregate, List<WorkerTelemetry> workerTelemetry) {
        public GenerationReport {
            Objects.requireNonNull(aggregate, "aggregate");
            aggregate = aggregate.clone();
            workerTelemetry = List.copyOf(workerTelemetry);
        }

        @Override
        public byte[] aggregate() {
            return aggregate.clone();
        }

        public byte[] bytes() {
            return aggregate();
        }
    }

    public record RowFailure(int rowIndex, Coordinate coordinate, int worker, String phase,
            Throwable cause) {
        public String attribution() {
            String row = coordinate == null ? "row=" + rowIndex
                    : "row=" + rowIndex + " coordinate=" + coordinate.key();
            return row + " worker=" + worker + " phase=" + phase;
        }
    }

    public static final class GenerationException extends IllegalStateException {
        private final List<RowFailure> failures;
        private final List<WorkerTelemetry> workerTelemetry;
        private final boolean timedOut;

        private GenerationException(String message, List<RowFailure> failures,
                List<WorkerTelemetry> workerTelemetry, boolean timedOut) {
            super(message);
            this.failures = List.copyOf(failures);
            this.workerTelemetry = List.copyOf(workerTelemetry);
            this.timedOut = timedOut;
        }

        public List<RowFailure> failures() {
            return failures;
        }

        public List<WorkerTelemetry> workerTelemetry() {
            return workerTelemetry;
        }

        public boolean timedOut() {
            return timedOut;
        }
    }

    private static final class AttributedFailure extends Exception {
        private AttributedFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record AuthenticatedCfpr(String resourcePath, byte[] bytes, String sha256,
            List<CourseRow> rows) {
        private AuthenticatedCfpr {
            bytes = bytes.clone();
            rows = List.copyOf(rows);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record ClassBinding(Class<?> type, String entry, String sha256, long byteLength,
            Identity codeSourceIdentity) {
    }

    private record SourceBinding(Path archivePath, ClassBinding classBinding,
            Identity fileIdentity) {
        private void verify(AuthenticatedBootJar authenticated) throws IOException {
            if (fileIdentity != null) {
                verifyIdentities(List.of(fileIdentity));
            } else if (classBinding == null
                    || !classBinding.equals(authenticated.producerClassBinding())) {
                throw new IOException("producer source archive binding drifted");
            } else {
                authenticated.verifyStillBound();
            }
        }
    }

    private record AuthenticatedBootJar(byte[] courseBytes, String courseResourcePath,
            String courseResourceSha256, AuthenticatedCfpr cfpr, Identity jarIdentity,
            ClassBinding producerClassBinding, ClassBinding propertiesLauncherBinding,
            ClassBinding jarLauncherBinding, ClassBinding launcherBinding) {
        private AuthenticatedBootJar {
            courseBytes = courseBytes.clone();
        }

        @Override
        public byte[] courseBytes() {
            return courseBytes.clone();
        }

        private void verifyStillBound() throws IOException {
            Identity currentJar = identity("producerJar", jarIdentity.path());
            if (!sameIdentity(jarIdentity, currentJar)) {
                throw new IOException("producer jar identity drifted after authentication");
            }
            if (!courseResourceSha256.equals(sha256(courseBytes))) {
                throw new IOException("course resource digest drifted after authentication");
            }
            verifyClassBinding(producerClassBinding, currentJar.path());
            verifyClassBinding(propertiesLauncherBinding, currentJar.path());
            verifyClassBinding(jarLauncherBinding, currentJar.path());
            verifyClassBinding(launcherBinding, currentJar.path());
            try (JarFile archive = openJar(currentJar.path())) {
                authenticateArchiveGrammar(archive);
                requireBootManifest(archive.getManifest());
                requireRegularJarEntry(archive, BOOT_JAR_LAUNCHER_CLASS_ENTRY);
                requireRegularJarEntry(archive, BOOT_LAUNCHER_CLASS_ENTRY);
                requireRegularJarEntry(archive, PROPERTIES_LAUNCHER_CLASS_ENTRY);
                requireRegularJarEntry(archive, PRODUCER_CLASS_ENTRY);
                requireRegularJarEntry(archive, COURSE_ENTRY);
                requireRegularJarEntry(archive, CFPR_ENTRY);
                requireBytesEqual(courseBytes,
                        readJarEntry(archive, COURSE_ENTRY, MAX_COURSE_RESOURCE_BYTES,
                                "course resource exceeds its bounded input size"),
                        "course archive entry");
                requireBytesEqual(cfpr.bytes(),
                        readJarEntry(archive, CFPR_ENTRY, AUTHENTICATED_CFPR_BYTES,
                                "CFPR resource exceeds its authenticated size"),
                        "CFPR archive entry");
                requireBoundClassBytes(producerClassBinding,
                        readJarEntry(archive, PRODUCER_CLASS_ENTRY, MAX_CLASS_BYTES,
                                "producer class exceeds its bounded input size"),
                        "producer archive class entry");
                requireBoundClassBytes(propertiesLauncherBinding,
                        readJarEntry(archive, PROPERTIES_LAUNCHER_CLASS_ENTRY, MAX_CLASS_BYTES,
                                "PropertiesLauncher class exceeds its bounded input size"),
                        "PropertiesLauncher archive class entry");
                requireBoundClassBytes(jarLauncherBinding,
                        readJarEntry(archive, BOOT_JAR_LAUNCHER_CLASS_ENTRY, MAX_CLASS_BYTES,
                                "JarLauncher class exceeds its bounded input size"),
                        "JarLauncher archive class entry");
                requireBoundClassBytes(launcherBinding,
                        readJarEntry(archive, BOOT_LAUNCHER_CLASS_ENTRY, MAX_CLASS_BYTES,
                                "Launcher class exceeds its bounded input size"),
                        "Launcher archive class entry");
            }
            URL courseUrl = resourceUrl(COURSE_RESOURCE);
            authenticateJarResourceUrl(courseUrl, currentJar.path(), COURSE_ENTRY,
                    "course resource");
            requireBytesEqual(courseBytes, readResource(courseUrl, MAX_COURSE_RESOURCE_BYTES,
                    "course resource exceeds its bounded input size"),
                    "course classpath entry");
            URL cfprUrl = resourceUrl(CFPR_RESOURCE);
            authenticateJarResourceUrl(cfprUrl, currentJar.path(), CFPR_ENTRY, "CFPR resource");
            requireBytesEqual(cfpr.bytes(), readResource(cfprUrl, AUTHENTICATED_CFPR_BYTES,
                    "CFPR resource exceeds its authenticated size"),
                    "CFPR classpath entry");
        }

    }

    private record Identity(String name, Path path, Object fileKey, long byteLength, String sha256) {
    }

    private record TargetState(boolean present, Object fileKey, long size) {
    }

    private record WorkItem(int rowIndex, CourseRow row, int workerIndex) {
    }

    private record SubmittedWork(Future<WorkerResult> future, int workerIndex,
            List<Integer> rowIndexes) {
        private SubmittedWork {
            rowIndexes = List.copyOf(rowIndexes);
        }
    }

    private record RowResult(byte[] bytes, Throwable failure) {
    }

    private record WorkerResult(List<RowResult> rowResults) {
        private WorkerResult {
            rowResults = List.copyOf(rowResults);
        }
    }

    private static final class Worker {
        private final int workerIndex;
        private final ChunkProductSource source;
        private final Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext;
        private final int expectedChunkBytes;
        private final AtomicReference<Thread> startedThread = new AtomicReference<>();
        private final AtomicBoolean overlapped = new AtomicBoolean();
        private final AtomicInteger completedRows = new AtomicInteger();
        private final AtomicInteger failedRows = new AtomicInteger();
        private final List<Integer> assignedRows = new ArrayList<>();

        private Worker(int workerIndex, ChunkProductSource source,
                Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
                int expectedChunkBytes) {
            this.workerIndex = workerIndex;
            this.source = source;
            this.inputBuilderContext = inputBuilderContext;
            this.expectedChunkBytes = expectedChunkBytes;
        }

        private int workerIndex() {
            return workerIndex;
        }

        private int expectedChunkBytes() {
            return expectedChunkBytes;
        }

        private byte[] generate(CourseRow row) {
            ChunkGenerator.GeneratedChunk product = source.generate(
                    row.seed(), row.cx(), row.cz());
            if (product == null || product.finalLiveCarrier() == null || product.blocks() == null
                    || product.blocks().length != Blocks.CHUNK_BLOCKS) {
                throw new IllegalStateException("canonical-final product is incomplete at "
                        + row.coordinate().key());
            }
            byte[] bytes = ChunkGenerator.canonicalBytes(product.blocks());
            if (bytes.length != expectedChunkBytes()) {
                throw new IllegalStateException("canonical chunk shape drift at "
                        + row.coordinate().key());
            }
            return bytes;
        }

        private void start(Set<Thread> actualWorkerThreads, long deadlineAt)
                throws TimeoutException {
            Thread current = Thread.currentThread();
            Thread previous = startedThread.get();
            if (previous != null && previous != current) {
                throw new IllegalStateException("worker " + workerIndex
                        + " crossed its bound execution thread");
            }
            if (!startedThread.compareAndSet(null, current)
                    && startedThread.get() != current) {
                throw new IllegalStateException("worker " + workerIndex
                        + " crossed its bound execution thread");
            }
            actualWorkerThreads.add(current);
            long remaining = deadlineAt - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException("worker start exceeded parity deadline");
            }
        }

        private void enterWork(Map<Integer, Worker> activeWorkers) {
            activeWorkers.put(workerIndex, this);
            if (activeWorkers.size() > 1) {
                for (Worker active : activeWorkers.values()) active.overlapped.set(true);
            }
        }

        private void leaveWork(Map<Integer, Worker> activeWorkers) {
            activeWorkers.remove(workerIndex, this);
        }

        private Mc263FeaturesRegionBridge.InputBuilderTelemetry contextTelemetry() {
            return inputBuilderContext.telemetry();
        }

        private boolean contextHasLiveThreads() {
            return inputBuilderContext.hasLiveThreads();
        }

        private void close() {
            inputBuilderContext.close();
        }
    }
}
