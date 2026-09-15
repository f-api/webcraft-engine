package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ProducerGraphPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Registry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StructureDefinition;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.TerrainAdjustment;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.ExistingStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.ExistingStartFact;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.GeneratorResult;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.ValidSettlement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Production {@link Mc263StructureStartDecisionCoordinator.AuthorityAccess} that dispatches one
 * attempt to the landed {@code AttemptContext} start generator for its structure key.
 *
 * <p>The dispatch table is closed: a structure key that has no landed generator throws with the
 * complete list of the keys that do, so an unfinished family can never silently decide as invalid.
 * The single exception is a key whose pinned {@code Mc263StructureIndexReceipt} row records an
 * empty Overworld biome mask — the four Nether/End families and
 * {@code minecraft:ruined_portal_nether}. Vanilla tests the structure's biome set during start
 * generation, and an empty set can never accept, so those attempts terminate as discarded invalid
 * starts without any world read, RNG draw, or persistence.</p>
 *
 * <p>Every family whose landed executors reload a persisted start also emits that producer's own
 * STRRAW01 raw start payload here (and its STRGRF01 producer graph where the family defines one).
 * The bytes are always the ones the producer already encoded; nothing is re-encoded or
 * synthesized, and an absent payload stays absent rather than being invented.</p>
 */
public final class Mc263StructureAuthorityAccess
        implements Mc263StructureStartDecisionCoordinator.AuthorityAccess {

    /** One landed {@code AttemptContext} start generator. */
    @FunctionalInterface
    private interface StartGenerator {
        Optional<ChunkStarts> generate(AttemptContext context, Mc263StructureWorldAccess world,
                Registry registry);
    }

    /** Pinned 26.3 Overworld build-height boundary, exclusive maximum, as every producer asserts. */
    private static final int MIN_BUILD_Y = com.gameexpert.terrain.Blocks.MIN_Y;
    private static final int MAX_BUILD_Y = com.gameexpert.terrain.Blocks.MAX_Y + 1;
    private static final String STRONGHOLD = "minecraft:stronghold";
    /**
     * The six Overworld Ruined Portal members. {@code minecraft:ruined_portal_nether} is the
     * seventh pinned set member and is deliberately absent: it has zero Overworld biome membership
     * ({@code Mc263StructureIndexReceipt} records mask {@code 0}) and this product ships no Nether.
     */
    private static final List<String> RUINED_PORTAL_OVERWORLD_MEMBERS = List.of(
            "minecraft:ruined_portal", "minecraft:ruined_portal_desert",
            "minecraft:ruined_portal_jungle", "minecraft:ruined_portal_mountain",
            "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_swamp");
    private static final java.util.Set<String> JIGSAW_KEYS =
            Mc263JigsawStructureCatalog.structures().stream()
                    .map(Mc263JigsawStructureCatalog.StructureSpec::key)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Registry PINNED_REGISTRY = buildPinnedRegistry();
    private static final Map<String, StartGenerator> LANDED_GENERATORS = landedGenerators();

    private final Mc263StructureWorldAccess world;
    private final Registry registry;
    private final Map<String, StartGenerator> generators;
    private final Map<StartKey, ValidStart> persisted = new LinkedHashMap<>();
    private final Map<StartKey, RawStartPayload> rawStartPayloads = new LinkedHashMap<>();
    private final Map<StartKey, ProducerGraphPayload> producerGraphPayloads =
            new LinkedHashMap<>();
    private final List<ValidSettlement> settlements = new ArrayList<>();
    private boolean admissionQueryFailed;

    private Mc263StructureAuthorityAccess(Mc263StructureWorldAccess world, Registry registry,
            Map<String, StartGenerator> generators) {
        this.world = world;
        this.registry = registry;
        this.generators = Map.copyOf(generators);
    }

    /** Every pinned Overworld family that has a landed carrier start origin. */
    public static Mc263StructureAuthorityAccess landed(Mc263StructureWorldAccess world) {
        Objects.requireNonNull(world, "structure world access");
        return new Mc263StructureAuthorityAccess(world, PINNED_REGISTRY, LANDED_GENERATORS);
    }

    /** Builds the immutable process-wide dispatch catalog from pinned structure identities. */
    private static Map<String, StartGenerator> landedGenerators() {
        Map<String, StartGenerator> table = new LinkedHashMap<>();
        table.put(Mc263IglooStartGenerator.STRUCTURE, Mc263StructureAuthorityAccess::igloo);
        table.put(Mc263BuriedTreasureStartGenerator.STRUCTURE,
                Mc263StructureAuthorityAccess::buriedTreasure);
        table.put(Mc263DesertPyramidStartGenerator.STRUCTURE,
                Mc263StructureAuthorityAccess::desertPyramid);
        table.put(Mc263SwampHutStartGenerator.STRUCTURE, Mc263StructureAuthorityAccess::swampHut);
        table.put(Mc263JunglePyramidStartGenerator.STRUCTURE,
                Mc263StructureAuthorityAccess::junglePyramid);
        for (Mc263AbandonedCampStartGenerator.StructureFact fact
                : Mc263AbandonedCampStartGenerator.structureFacts()) {
            table.put(fact.structureKey(), Mc263StructureAuthorityAccess::abandonedCamp);
        }
        table.put(Mc263OceanMonumentStartGenerator.STRUCTURE,
                Mc263StructureAuthorityAccess::oceanMonument);
        table.put(Mc263OceanRuinStartGenerator.COLD_STRUCTURE,
                Mc263StructureAuthorityAccess::oceanRuin);
        table.put(Mc263OceanRuinStartGenerator.WARM_STRUCTURE,
                Mc263StructureAuthorityAccess::oceanRuin);
        table.put(Mc263TrailRuinsStartOrigin.STRUCTURE,
                Mc263StructureAuthorityAccess::trailRuins);
        for (String member : Mc263VillageStartOrigin.MEMBERS) {
            table.put(member, Mc263StructureAuthorityAccess::village);
        }
        table.put(Mc263PillagerOutpostStartOrigin.STRUCTURE,
                Mc263StructureAuthorityAccess::pillagerOutpost);
        table.put(Mc263TrialChambersStartOrigin.STRUCTURE,
                (context, world, registry) -> Optional.empty());
        table.put(Mc263AncientCityStartOrigin.STRUCTURE,
                Mc263StructureAuthorityAccess::ancientCity);
        table.put(Mc263WoodlandMansionStartOrigin.STRUCTURE,
                Mc263StructureAuthorityAccess::woodlandMansion);
        table.put(Mc263ShipwreckCarrier.OCEAN, Mc263StructureAuthorityAccess::shipwreck);
        table.put(Mc263ShipwreckCarrier.BEACHED, Mc263StructureAuthorityAccess::shipwreck);
        for (Mc263MineshaftStartGenerator.Type type
                : Mc263MineshaftStartGenerator.Type.values()) {
            table.put(type.structureId(), Mc263StructureAuthorityAccess::mineshaft);
        }
        for (String member : RUINED_PORTAL_OVERWORLD_MEMBERS) {
            table.put(member, Mc263StructureAuthorityAccess::ruinedPortal);
        }
        table.put(STRONGHOLD, Mc263StructureAuthorityAccess::stronghold);
        return Map.copyOf(table);
    }

    /** Structure keys this authority can decide, in pinned registry order. */
    public List<String> landedStructureKeys() {
        return Mc263StructureIndexReceipt.entries().stream()
                .map(Mc263StructureIndexReceipt.Entry::key)
                .filter(generators::containsKey)
                .toList();
    }

    public Registry registry() {
        return registry;
    }

    public List<ValidSettlement> settlements() {
        return List.copyOf(settlements);
    }

    /** The valid start this authority produced and persisted for one structure and chunk. */
    public Optional<ValidStart> persistedStart(String structureKey, int chunkX, int chunkZ) {
        return Optional.ofNullable(persisted.get(new StartKey(structureKey, chunkX, chunkZ)));
    }

    /** Producer-owned optional STRRAW01 payload for one landed valid start. */
    public Optional<RawStartPayload> rawStartPayload(String structureKey, ValidStart start) {
        Objects.requireNonNull(structureKey, "structure key");
        Objects.requireNonNull(start, "valid start");
        RawStartPayload produced = rawStartPayloads.get(new StartKey(structureKey,
                start.originChunkX(), start.originChunkZ()));
        if (produced != null) return Optional.of(produced);
        boolean abandonedCamp = Mc263AbandonedCampStartGenerator.structureFacts().stream()
                .anyMatch(fact -> fact.structureKey().equals(structureKey));
        if (!abandonedCamp) return Optional.empty();
        byte[] predecessor = Mc263AbandonedCampProducer.persistedStartNbt(structureKey, start, 0);
        byte[] successor = Mc263AbandonedCampProducer.persistedStartNbt(structureKey, start, 1);
        return Optional.of(new RawStartPayload(structureKey, start.startKey(),
                start.originChunkX(), start.originChunkZ(), predecessor, successor));
    }

    /** Producer-owned optional STRGRF01 payload for one landed valid start. */
    public Optional<ProducerGraphPayload> producerGraphPayload(String structureKey,
            ValidStart start) {
        Objects.requireNonNull(structureKey, "structure key");
        Objects.requireNonNull(start, "valid start");
        return Optional.ofNullable(producerGraphPayloads.get(new StartKey(structureKey,
                start.originChunkX(), start.originChunkZ())));
    }

    @Override public boolean hasExistingStartFacts() { return true; }
    @Override public boolean hasAttemptGenerator() { return true; }
    @Override public boolean hasValidStartPersistence() { return true; }

    @Override
    public ExistingStartFact existingStart(String structureKey, int chunkX, int chunkZ) {
        ValidStart start = persisted.get(new StartKey(structureKey, chunkX, chunkZ));
        return start == null ? new ExistingStartFact(ExistingStart.ABSENT, 0)
                : new ExistingStartFact(ExistingStart.VALID, start.references());
    }

    @Override
    public GeneratorResult generate(AttemptContext context) {
        Objects.requireNonNull(context, "attempt context");
        if (admissionQueryFailed) return new GeneratorResult(false, 0L);
        StartGenerator generator = generators.get(context.structureKey());
        if (generator == null) {
            if (zeroOverworldBiomeMembership(context.structureKey())) {
                // Authenticated zero Overworld biome mask: vanilla's start generation tests the
                // structure's biome set at the locate position, and an empty set can never
                // accept, so the attempt terminates as a discarded invalid start. Termination
                // performs no world read, no RNG draw and no persistence, exactly like the
                // Rust carrier origin's zero-Overworld lane.
                return new GeneratorResult(false, 0L);
            }
            throw new UnsupportedOperationException("no landed AttemptContext start generator for "
                    + context.structureKey() + "; landed keys are " + landedStructureKeys());
        }
        Mc263PillagerOutpostStartOrigin.OriginResult outpost = null;
        Mc263AncientCityStartOrigin.OriginResult ancientCity = null;
        Mc263VillageStartOrigin.OriginResult village = null;
        Mc263WoodlandMansionStartOrigin.OriginResult mansion = null;
        Optional<ChunkStarts> produced;
        if (context.structureKey().equals(Mc263PillagerOutpostStartOrigin.STRUCTURE)) {
            outpost = pillagerOutpostOrigin(context, world);
            produced = outpost.valid() ? Optional.of(outpost.startChunk()) : Optional.empty();
        } else if (Mc263VillageStartOrigin.MEMBERS.contains(context.structureKey())) {
            village = villageOrigin(context, world);
            produced = village.valid() ? Optional.of(village.startChunk()) : Optional.empty();
        } else if (context.structureKey().equals(Mc263WoodlandMansionStartOrigin.STRUCTURE)) {
            mansion = woodlandMansionOrigin(context, world);
            produced = mansion.valid() ? Optional.of(mansion.startChunk()) : Optional.empty();
        } else if (context.structureKey().equals(Mc263AncientCityStartOrigin.STRUCTURE)) {
            boolean[] invalidBiome = new boolean[1];
            try {
                ancientCity = ancientCityOrigin(context, world, invalidBiome);
                produced = ancientCity.valid()
                        ? Optional.of(ancientCity.startChunk()) : Optional.empty();
            } catch (IllegalArgumentException raised) {
                if (!invalidBiome[0]) throw raised;
                produced = Optional.empty();
            }
        } else if (context.structureKey().equals(Mc263TrialChambersStartOrigin.STRUCTURE)) {
            TrialChambersResult trialChambers = trialChambers(context, world, registry);
            if (trialChambers.admissionQueryFailed()) {
                admissionQueryFailed = true;
                return new GeneratorResult(false, 0L);
            }
            produced = trialChambers.starts();
        } else {
            produced = generator.generate(context, world, registry);
        }
        if (produced.isEmpty()) return new GeneratorResult(false, 0L);
        ValidStart start = validStart(produced.get(), context);
        StartKey key = new StartKey(context.structureKey(), context.chunkX(), context.chunkZ());
        persisted.put(key, start);
        if (outpost != null) {
            rawStartPayloads.put(key, Mc263PillagerOutpostStartOrigin.rawStartPayload(
                    outpost.producerStart(), start));
        }
        if (ancientCity != null) {
            rawStartPayloads.put(key, Mc263AncientCityStartOrigin.rawStartPayload(
                    ancientCity.producerStart(), start));
            producerGraphPayloads.put(key, Mc263AncientCityStartOrigin.producerGraphPayload(
                    ancientCity.producerStart(), start));
        }
        if (village != null) {
            if (!(village.attempt() instanceof Mc263VillageProducer.Accepted accepted)) {
                throw new IllegalStateException("valid Village start without an accepted attempt");
            }
            rawStartPayloads.put(key, Mc263VillageStartOrigin.rawStartPayload(
                    accepted.start(), start));
        }
        if (mansion != null) {
            if (!(mansion.producerResult() instanceof Mc263WoodlandMansionProducer.Start built)) {
                throw new IllegalStateException(
                        "valid Woodland Mansion start without a producer start");
            }
            rawStartPayloads.put(key, Mc263WoodlandMansionStartOrigin.rawStartPayload(
                    built, start));
            producerGraphPayloads.put(key, Mc263WoodlandMansionStartOrigin.producerGraphPayload(
                    built, start));
        }
        return new GeneratorResult(true, startReceipt(context.structureKey(), start));
    }

    @Override
    public void persistValid(ValidSettlement settlement) {
        Objects.requireNonNull(settlement, "valid settlement");
        if (!persisted.containsKey(new StartKey(settlement.structureKey(),
                settlement.chunkX(), settlement.chunkZ()))) {
            throw new IllegalStateException("settlement without a generated start: " + settlement);
        }
        settlements.add(settlement);
    }

    // ── landed generators ────────────────────────────────────────────────────

    private static Optional<ChunkStarts> igloo(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263IglooStartGenerator.generate(context,
                new Mc263IglooStartGenerator.WorldAccess() {
                    @Override public boolean supportsWorldSurfaceWg() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int worldSurfaceWg(int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(Mc263IglooStartGenerator.STRUCTURE, x, y, z);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> buriedTreasure(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263BuriedTreasureStartGenerator.generate(context,
                new Mc263BuriedTreasureStartGenerator.WorldAccess() {
                    @Override public boolean supportsOceanFloorWg() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int oceanFloorWg(int x, int z) {
                        return world.oceanFloorWg(x, z);
                    }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(
                                Mc263BuriedTreasureStartGenerator.STRUCTURE, x, y, z);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> desertPyramid(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263DesertPyramidStartGenerator.generate(context, registry,
                new Mc263DesertPyramidStartGenerator.WorldAccess() {
                    @Override public boolean supportsWorldSurfaceWg() { return true; }
                    @Override public boolean supportsSeaLevel() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int worldSurfaceWg(int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                    @Override public int seaLevel() { return world.seaLevel(); }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(
                                Mc263DesertPyramidStartGenerator.STRUCTURE, x, y, z);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> swampHut(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263SwampHutStartGenerator.generate(context, registry,
                new Mc263SwampHutStartGenerator.WorldAccess() {
                    @Override public boolean supportsWorldSurfaceWg() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int worldSurfaceWg(int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(Mc263SwampHutStartGenerator.STRUCTURE, x, y, z);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> junglePyramid(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263JunglePyramidStartGenerator.generate(context, registry,
                new Mc263JunglePyramidStartGenerator.WorldAccess() {
                    @Override public boolean supportsWorldSurfaceWg() { return true; }
                    @Override public boolean supportsSeaLevel() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int worldSurfaceWg(int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                    @Override public int seaLevel() { return world.seaLevel(); }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(
                                Mc263JunglePyramidStartGenerator.STRUCTURE, x, y, z);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    // ── S2 family origins ────────────────────────────────────────────────────

    private static Optional<ChunkStarts> abandonedCamp(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263AbandonedCampStartGenerator.generate(context,
                new Mc263AbandonedCampStartGenerator.WorldAccess() {
                    @Override public boolean supportsWorldSurfaceHeight() { return true; }
                    @Override public int worldSurfaceHeight(int blockX, int blockZ) {
                        return world.worldSurfaceWg(blockX, blockZ);
                    }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public boolean isValidBiome(int blockX, int blockY, int blockZ) {
                        return world.isValidBiome(context.structureKey(), blockX, blockY, blockZ);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> oceanMonument(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263OceanMonumentStartGenerator.generate(context, registry,
                new Mc263OceanMonumentStartGenerator.WorldAccess() {
                    @Override public boolean supportsOceanFloorWg() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int oceanFloorWg(int x, int z) {
                        return world.oceanFloorWg(x, z);
                    }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(
                                Mc263OceanMonumentStartGenerator.STRUCTURE, x, y, z);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> oceanRuin(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263OceanRuinStartGenerator.generate(context, registry,
                new Mc263OceanRuinStartGenerator.WorldAccess() {
                    @Override public boolean supportsOceanFloorWg() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int oceanFloorWg(int x, int z) {
                        return world.oceanFloorWg(x, z);
                    }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(context.structureKey(), x, y, z);
                    }
                }, Mc263OceanRuinTemplateCatalog.official());
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> trailRuins(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263TrailRuinsStartOrigin.originate(context,
                new Mc263TrailRuinsStartOrigin.WorldAccess() {
                    @Override public boolean supportsWorldSurfaceWg() { return true; }
                    @Override public int worldSurfaceWg(int blockX, int blockZ) {
                        return world.worldSurfaceWg(blockX, blockZ);
                    }
                });
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Optional<ChunkStarts> village(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = villageOrigin(context, world);
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Mc263VillageStartOrigin.OriginResult villageOrigin(
            AttemptContext context, Mc263StructureWorldAccess world) {
        return Mc263VillageStartOrigin.originate(context,
                new Mc263VillageProducer.WorldAccess() {
                    @Override public boolean supportsHeightmap(String heightmap) {
                        return Mc263VillageProducer.HEIGHTMAP.equals(heightmap);
                    }
                    @Override public boolean supportsBuildHeightBoundary() { return true; }
                    @Override public boolean supportsPool(String poolKey) { return true; }
                    @Override public boolean supportsTemplate(String templateKey) { return true; }
                    @Override public boolean supportsProcessorList(String key) { return true; }
                    @Override public boolean supportsConfiguredFeature(String key) { return true; }
                    @Override public int minBuildY() { return MIN_BUILD_Y; }
                    @Override public int maxBuildY() { return MAX_BUILD_Y; }
                    @Override public int baseHeight(String heightmap, int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                },
                new Mc263VillageProducer.BiomeAdmission() {
                    @Override public boolean supports(String key, String tag) { return true; }
                    @Override public Mc263VillageProducer.BiomeSample actualBiome(String key,
                            String tag, int x, int y, int z) {
                        return new Mc263VillageProducer.BiomeSample(world.biomeAt(x, y, z),
                                world.isValidBiome(key, x, y, z));
                    }
                },
                new Mc263VillageProducer.Publisher() {
                    @Override public boolean supports(String key, String format) {
                        return Mc263VillageProducer.CARRIER_FORMAT.equals(format);
                    }
                    @Override public void publishAtomically(Mc263VillageProducer.Start start) { }
                });
    }

    private static Optional<ChunkStarts> pillagerOutpost(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = pillagerOutpostOrigin(context, world);
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Mc263PillagerOutpostStartOrigin.OriginResult pillagerOutpostOrigin(
            AttemptContext context, Mc263StructureWorldAccess world) {
        return Mc263PillagerOutpostStartOrigin.originate(context,
                new Mc263PillagerOutpostProducer.WorldAccess() {
                    @Override public boolean supportsHeightmap(String heightmap) {
                        return Mc263PillagerOutpostProducer.HEIGHTMAP.equals(heightmap);
                    }
                    @Override public boolean supportsBuildHeightBoundary() { return true; }
                    @Override public int minBuildY() { return MIN_BUILD_Y; }
                    @Override public int maxBuildY() { return MAX_BUILD_Y; }
                    @Override public int baseHeight(String heightmap, int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                },
                new Mc263PillagerOutpostProducer.Publisher() {
                    @Override public boolean supports(String key, String format) {
                        return Mc263PillagerOutpostProducer.CARRIER_FORMAT.equals(format);
                    }
                    @Override public void publishAtomically(
                            Mc263PillagerOutpostProducer.Start start) { }
                });
    }

    private record TrialChambersResult(Optional<ChunkStarts> starts,
            boolean admissionQueryFailed) { }

    private static TrialChambersResult trialChambers(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263TrialChambersStartOrigin.originate(context,
                new Mc263TrialChambersProducer.WorldAccess() {
                    @Override public boolean supportsBuildHeightBoundary() { return true; }
                    @Override public int minBuildY() { return MIN_BUILD_Y; }
                    @Override public int maxBuildY() { return MAX_BUILD_Y; }
                },
                new Mc263TrialChambersProducer.Publisher() {
                    @Override public boolean supports(String key, String format) {
                        return Mc263TrialChambersProducer.CARRIER_FORMAT.equals(format);
                    }
                    @Override public void publishAtomically(
                            Mc263TrialChambersProducer.Start start) { }
                });
        if (!result.valid()) return new TrialChambersResult(Optional.empty(), false);
        Mc263TrialChambersProducer.Vec3i stub = result.producerStart().stubPosition();
        Optional<Boolean> admitted = trialChambersBiomeAdmission(world, stub);
        if (admitted.isEmpty()) return new TrialChambersResult(Optional.empty(), true);
        return new TrialChambersResult(admitted.get() ? Optional.of(result.startChunk())
                : Optional.empty(), false);
    }

    /** Queries the real world only; an unavailable admission read fails the authority closed. */
    static Optional<Boolean> trialChambersBiomeAdmission(Mc263StructureWorldAccess world,
            Mc263TrialChambersProducer.Vec3i stub) {
        Objects.requireNonNull(world, "structure world access");
        Objects.requireNonNull(stub, "trial chambers producer stub");
        try {
            return Optional.of(world.isValidBiome(Mc263TrialChambersStartOrigin.STRUCTURE,
                    stub.x(), stub.y(), stub.z()));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private static Optional<ChunkStarts> ancientCity(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        // The landed producer only learns its deep-dark stub position after planning, and it
        // raises rather than returning a rejection when that stub's biome is outside the
        // structure's tag. Vanilla treats exactly that as an invalid start, so the invalid sample
        // this adapter itself returned is the only rejection the raise is translated back into.
        boolean[] invalidBiome = new boolean[1];
        try {
            return ancientCity(context, world, invalidBiome);
        } catch (IllegalArgumentException raised) {
            if (invalidBiome[0]) return Optional.empty();
            throw raised;
        }
    }

    private static Optional<ChunkStarts> ancientCity(AttemptContext context,
            Mc263StructureWorldAccess world, boolean[] invalidBiome) {
        var result = ancientCityOrigin(context, world, invalidBiome);
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Mc263AncientCityStartOrigin.OriginResult ancientCityOrigin(
            AttemptContext context, Mc263StructureWorldAccess world, boolean[] invalidBiome) {
        return Mc263AncientCityStartOrigin.originate(context,
                new Mc263AncientCityProducer.WorldAccess() {
                    @Override public boolean supportsBuildHeightBoundary() { return true; }
                    @Override public boolean supportsBiomeMembership(String structureKey) {
                        return Mc263AncientCityProducer.STRUCTURE_KEY.equals(structureKey);
                    }
                    @Override public boolean supportsTemplate(String template,
                            Mc263AncientCityProducer.TemplateStatus status) {
                        return true;
                    }
                    @Override public boolean supportsProcessorList(String identity) {
                        return true;
                    }
                    @Override public boolean supportsConfiguredFeature(String featureKey) {
                        return true;
                    }
                    @Override public int minBuildY() { return MIN_BUILD_Y; }
                    @Override public int maxBuildY() { return MAX_BUILD_Y; }
                    @Override public Mc263AncientCityProducer.BiomeSample biomeAt(int x, int y,
                            int z) {
                        boolean valid = world.isValidBiome(
                                Mc263AncientCityProducer.STRUCTURE_KEY, x, y, z);
                        if (!valid) invalidBiome[0] = true;
                        return new Mc263AncientCityProducer.BiomeSample(world.biomeAt(x, y, z),
                                valid);
                    }
                },
                new Mc263AncientCityProducer.Publisher() {
                    @Override public boolean supports(String key, String format) {
                        return Mc263AncientCityProducer.CARRIER_FORMAT.equals(format);
                    }
                    @Override public void publishAtomically(
                            Mc263AncientCityProducer.Start start) { }
                });
    }

    private static Optional<ChunkStarts> woodlandMansion(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = woodlandMansionOrigin(context, world);
        return result.valid() ? Optional.of(result.startChunk()) : Optional.empty();
    }

    private static Mc263WoodlandMansionStartOrigin.OriginResult woodlandMansionOrigin(
            AttemptContext context, Mc263StructureWorldAccess world) {
        return Mc263WoodlandMansionStartOrigin.originate(context,
                new Mc263WoodlandMansionProducer.LocatedWorldAccess() {
                    @Override public boolean supportsHeightmap(
                            Mc263WoodlandMansionProducer.Heightmap heightmap) {
                        return heightmap
                                == Mc263WoodlandMansionProducer.Heightmap.WORLD_SURFACE_WG;
                    }
                    @Override public int baseHeight(
                            Mc263WoodlandMansionProducer.Heightmap heightmap, int x, int z) {
                        return world.worldSurfaceWg(x, z);
                    }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public boolean isValidBiome(String structureKey, int x, int y,
                            int z) {
                        return world.isValidBiome(structureKey, x, y, z);
                    }
                });
    }

    // ── bespoke-WorldAccess families ─────────────────────────────────────────

    private static Optional<ChunkStarts> shipwreck(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        Mc263ShipwreckStartGenerator.Result result = Mc263ShipwreckStartGenerator.generate(
                context.structureKey(), context.worldSeed(), context.chunkX(), context.chunkZ(),
                context.priorReferences(), registry,
                new Mc263ShipwreckStartGenerator.WorldAccess() {
                    @Override public boolean supportsOceanFloorWg() { return true; }
                    @Override public boolean supportsWorldSurfaceWg() { return true; }
                    @Override public boolean supportsValidBiomeTest() { return true; }
                    @Override public int baseHeight(Mc263ShipwreckStartGenerator.Heightmap map,
                            int blockX, int blockZ) {
                        return map == Mc263ShipwreckStartGenerator.Heightmap.OCEAN_FLOOR_WG
                                ? world.oceanFloorWg(blockX, blockZ)
                                : world.worldSurfaceWg(blockX, blockZ);
                    }
                    @Override public boolean isValidBiome(int x, int y, int z) {
                        return world.isValidBiome(context.structureKey(), x, y, z);
                    }
                });
        return result == null ? Optional.empty() : Optional.of(result.startChunk());
    }

    private static Optional<ChunkStarts> mineshaft(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var generated = Mc263MineshaftStartGenerator.generate(context.worldSeed(),
                context.chunkX(), context.chunkZ(), context.structureKey(),
                context.priorReferences(), world::worldSurfaceWg);
        return Optional.of(new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(context.structureKey(), generated.start()))));
    }

    /**
     * The Ruined Portal family's landed start path is
     * {@link Mc263RuinedPortalProgram#plan} plus that plan's own one-piece STR263C1 carrier —
     * the same entry {@code Mc263RuinedPortalProductionExecutor} consumes. Vanilla's
     * {@code findValidGenerationPoint} tests the member's pinned biome tag at the generated
     * template position before admitting a start.
     */
    private static Optional<ChunkStarts> ruinedPortal(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        Mc263RuinedPortalProgram.Plan plan = Mc263RuinedPortalProgram.plan(context.structureKey(),
                context.worldSeed(), context.chunkX(), context.chunkZ(),
                new Mc263RuinedPortalProgram.Terrain() {
                    @Override public int minY() { return MIN_BUILD_Y; }
                    @Override public int seaLevel() { return world.seaLevel(); }
                    @Override public int baseHeight(int x, int z,
                            Mc263RuinedPortalProgram.Heightmap heightmap) {
                        return heightmap == Mc263RuinedPortalProgram.Heightmap.WORLD_SURFACE_WG
                                ? world.worldSurfaceWg(x, z) : world.oceanFloorWg(x, z);
                    }
                    @Override public boolean opaqueInBaseColumn(int x, int y, int z,
                            Mc263RuinedPortalProgram.Heightmap heightmap) {
                        return world.opaqueInBaseColumn(x, y, z,
                                heightmap == Mc263RuinedPortalProgram.Heightmap.OCEAN_FLOOR_WG);
                    }
                    @Override public boolean coldEnoughToSnow(
                            Mc263RuinedPortalProgram.BlockPos position, int seaLevel) {
                        return world.coldEnoughToSnow(position.x(), position.y(), position.z(),
                                seaLevel);
                    }
                });
        Mc263RuinedPortalProgram.BlockPos stub = plan.templatePosition();
        if (!world.isValidBiome(context.structureKey(), stub.x(), stub.y(), stub.z())) {
            return Optional.empty();
        }
        List<ChunkStarts> starts = plan.structureCarrier(registry, context.priorReferences())
                .startChunks();
        if (starts.size() != 1) {
            throw new IllegalStateException("ruined-portal plan carried no single chunk start");
        }
        return Optional.of(starts.getFirst());
    }

    private static Optional<ChunkStarts> stronghold(AttemptContext context,
            Mc263StructureWorldAccess world, Registry registry) {
        var result = Mc263StrongholdStartGenerator.generate(context.worldSeed(),
                context.chunkX(), context.chunkZ(), context.priorReferences());
        return Optional.of(new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(STRONGHOLD, result.start()))));
    }

    // ── carrier plumbing ─────────────────────────────────────────────────────

    /** The pinned 52-entry structure registry every template start generator validates against. */
    public static Registry pinnedRegistry() {
        return PINNED_REGISTRY;
    }

    private static Registry buildPinnedRegistry() {
        List<Mc263StructureIndexReceipt.Entry> entries = Mc263StructureIndexReceipt.entries();
        List<StructureDefinition> definitions = new ArrayList<>(entries.size());
        for (int ordinal = 0; ordinal < entries.size(); ordinal++) {
            Mc263StructureIndexReceipt.Entry entry = entries.get(ordinal);
            definitions.add(new StructureDefinition(entry.key(), ordinal, entry.step(),
                    terrainAdjustment(entry.key())));
        }
        return new Registry(definitions);
    }

    /**
     * Per-structure terrain adaptation. The 27 pinned jigsaw structures take theirs directly from
     * {@link Mc263JigsawStructureCatalog}, the authenticated 26.3 catalog row; Stronghold takes
     * the {@code BURY} its promoted canonical executor pins. Every remaining pinned row keeps
     * {@code NONE}, so a family whose carrier start inflates its own aggregate without a pinned
     * adaptation fails the carrier's adjusted-bounding-box check instead of being admitted on a
     * guess.
     */
    private static TerrainAdjustment terrainAdjustment(String structureKey) {
        if (JIGSAW_KEYS.contains(structureKey)) {
            return switch (Mc263JigsawStructureCatalog.require(structureKey)
                    .terrainAdaptation()) {
                case BURY -> TerrainAdjustment.BURY;
                case BEARD_THIN -> TerrainAdjustment.BEARD_THIN;
                case BEARD_BOX -> TerrainAdjustment.BEARD_BOX;
                case ENCAPSULATE -> TerrainAdjustment.ENCAPSULATE;
            };
        }
        return STRONGHOLD.equals(structureKey) ? TerrainAdjustment.BURY : TerrainAdjustment.NONE;
    }

    private static ValidStart validStart(ChunkStarts starts, AttemptContext context) {
        if (starts.chunkX() != context.chunkX() || starts.chunkZ() != context.chunkZ()) {
            throw new IllegalStateException("generated start chunk does not match the attempt");
        }
        List<StartEntry> ordered = starts.orderedStarts();
        if (ordered.size() != 1 || !ordered.getFirst().structureId().equals(context.structureKey())
                || !(ordered.getFirst().body() instanceof ValidStart start)) {
            throw new IllegalStateException("valid attempt produced no single valid start");
        }
        return start;
    }

    /** Deterministic 64-bit digest of one valid start's exact binary-NBT piece graph. */
    public static long startReceipt(String structureKey, ValidStart start) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write("SAA263S1".getBytes(StandardCharsets.US_ASCII));
            writeString(out, structureKey);
            writeString(out, start.startKey());
            out.writeInt(start.originChunkX());
            out.writeInt(start.originChunkZ());
            out.writeInt(start.references());
            writeBox(out, start.adjustedBoundingBox());
            out.writeInt(start.orderedPieces().size());
            for (Piece piece : start.orderedPieces()) {
                writeString(out, piece.pieceType());
                writeBox(out, piece.boundingBox());
                out.writeBoolean(piece.poolElement());
                out.writeByte(piece.projection().ordinal());
                byte[] nbt = piece.persistedPayload().binaryNbtCompound();
                out.writeInt(nbt.length);
                out.write(nbt);
            }
            out.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            long receipt = 0L;
            for (int index = 0; index < 8; index++) {
                receipt = (receipt << 8) | (digest[index] & 0xFFL);
            }
            return receipt;
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeBox(DataOutputStream out, BoundingBox box) throws IOException {
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(encoded.length);
        out.write(encoded);
    }

    /** Distinct structure keys the pinned sets can attempt but this authority cannot decide. */
    public List<String> unlandedStructureKeys() {
        TreeSet<String> missing = new TreeSet<>();
        for (Mc263StructureIndexReceipt.PlacementRecord record
                : Mc263StructureIndexReceipt.placements()) {
            for (String token : record.weightedStructures().split(",", -1)) {
                String key = token.split("=", -1)[0];
                if (!generators.containsKey(key)) missing.add(key);
            }
        }
        return List.copyOf(missing);
    }

    /**
     * True when the pinned registry records an empty Overworld biome mask for this structure.
     *
     * <p>{@code Mc263StructureIndexReceipt} is the authenticated 26.3 structure index; the only
     * rows it records with mask {@code 0} are the four Nether/End families and
     * {@code minecraft:ruined_portal_nether}. A key absent from the index is never terminated —
     * it still fails closed.</p>
     */
    private static boolean zeroOverworldBiomeMembership(String structureKey) {
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            if (entry.key().equals(structureKey)) return entry.biomeMask() == 0L;
        }
        return false;
    }

    private record StartKey(String structureKey, int chunkX, int chunkZ) { }
}
