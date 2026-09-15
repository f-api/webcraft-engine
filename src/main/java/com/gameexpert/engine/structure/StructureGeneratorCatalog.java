package com.gameexpert.engine.structure;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.generator.AltarGenerator;
import com.gameexpert.engine.structure.generator.BuriedRuinGenerator;
import com.gameexpert.engine.structure.generator.CampGenerator;
import com.gameexpert.engine.structure.generator.CoralReefGenerator;
import com.gameexpert.engine.structure.generator.DesertTombGenerator;
import com.gameexpert.engine.structure.generator.GlitchDungeonGenerator;
import com.gameexpert.engine.structure.generator.MeteorCraterGenerator;
import com.gameexpert.engine.structure.generator.MonsterSpawnZoneGenerator;
import com.gameexpert.engine.structure.generator.RuinedTowerGenerator;
import com.gameexpert.engine.structure.generator.SealedChamberGenerator;
import com.gameexpert.engine.structure.generator.UndergroundCityGenerator;
import com.gameexpert.engine.structure.generator.UndergroundDungeonGenerator;
import com.gameexpert.engine.structure.generator.UndergroundPrisonGenerator;
import com.gameexpert.engine.structure.generator.UndergroundRuinGenerator;
import com.gameexpert.engine.structure.generator.UnderwaterRuinGenerator;
import com.gameexpert.terrain.Blocks;

/** One-time dispatcher registration; later tracks own only their generator class. */
public final class StructureGeneratorCatalog {
    private static final Map<StructureSiteDescriptor.Kind, StructureOverlayGenerator> GENERATORS = generators();

    private StructureGeneratorCatalog() {}

    public static boolean hasGenerator(StructureSiteDescriptor.Kind kind) {
        return GENERATORS.containsKey(kind);
    }

    public static List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        StructureOverlayGenerator generator = GENERATORS.get(site.kind());
        if (generator == null) return List.of();
        if (!site.kind().hasOverlayWork()) {
            throw new IllegalStateException(site.kind() + " is not an overlay structure");
        }
        return generator.plan(site, world);
    }

    /**
     * Returns a terrain-free superset of this site's horizontal writes for safe chunk rejection.
     * The returned Y span is deliberately the legal world height and carries no vertical claim.
     */
    public static StructureAabb possibleHorizontalBounds(StructureSiteDescriptor site) {
        StructureOverlayGenerator generator = GENERATORS.get(site.kind());
        if (generator == null) {
            int reach = site.kind().maxReach();
            return new StructureAabb(site.anchorX() - reach, Blocks.MIN_Y, site.anchorZ() - reach,
                    site.anchorX() + reach, Blocks.MAX_Y - 1, site.anchorZ() + reach);
        }
        if (!site.kind().hasOverlayWork()) {
            throw new IllegalStateException(site.kind() + " is not an overlay structure");
        }
        return generator.possibleHorizontalBounds(site);
    }

    /** Applies an exact sparse-footprint rejection when a generator provides one. */
    public static boolean possibleHorizontalWriteIntersects(StructureSiteDescriptor site,
            StructureAabb chunk) {
        StructureOverlayGenerator generator = GENERATORS.get(site.kind());
        if (generator == null) return possibleHorizontalBounds(site).intersects(chunk);
        if (!site.kind().hasOverlayWork()) {
            throw new IllegalStateException(site.kind() + " is not an overlay structure");
        }
        return generator.possibleHorizontalWriteIntersects(site, chunk);
    }

    private static Map<StructureSiteDescriptor.Kind, StructureOverlayGenerator> generators() {
        Map<StructureSiteDescriptor.Kind, StructureOverlayGenerator> generators =
                new EnumMap<>(StructureSiteDescriptor.Kind.class);
        generators.put(StructureSiteDescriptor.Kind.CAMPING_SITE, new CampGenerator());
        generators.put(StructureSiteDescriptor.Kind.DESERT_TOMB, new DesertTombGenerator());
        generators.put(StructureSiteDescriptor.Kind.METEOR_CRATER, new MeteorCraterGenerator());
        generators.put(StructureSiteDescriptor.Kind.RUINED_TOWER, new RuinedTowerGenerator());
        generators.put(StructureSiteDescriptor.Kind.ALTAR, new AltarGenerator());
        generators.put(StructureSiteDescriptor.Kind.UNDERWATER_RUIN, new UnderwaterRuinGenerator());
        generators.put(StructureSiteDescriptor.Kind.UNDERGROUND_RUIN, new UndergroundRuinGenerator());
        generators.put(StructureSiteDescriptor.Kind.UNDERGROUND_DUNGEON, new UndergroundDungeonGenerator());
        generators.put(StructureSiteDescriptor.Kind.BURIED_RUIN, new BuriedRuinGenerator());
        generators.put(StructureSiteDescriptor.Kind.SEALED_CHAMBER, new SealedChamberGenerator());
        generators.put(StructureSiteDescriptor.Kind.UNDERGROUND_CITY, new UndergroundCityGenerator());
        generators.put(StructureSiteDescriptor.Kind.UNDERGROUND_PRISON, new UndergroundPrisonGenerator());
        generators.put(StructureSiteDescriptor.Kind.GLITCH_DUNGEON, new GlitchDungeonGenerator());
        generators.put(StructureSiteDescriptor.Kind.MONSTER_SPAWN_ZONE, new MonsterSpawnZoneGenerator());
        // [CORAL-REEF] 온수 바다 산호초 — 바닐라에서는 바이옴 feature 라 옮겨올 격자가
        // 없고, 얼룩덜룩한 숲과 같은 판단으로 구조물 사이트 lane 을 쓴다(CoralReefPlacement 주석).
        generators.put(StructureSiteDescriptor.Kind.CORAL_REEF, new CoralReefGenerator());
        for (StructureSiteDescriptor.Kind kind : generators.keySet()) {
            if (!kind.independentlyPlaced()) {
                throw new IllegalStateException(kind + " host-bound generator must use its host path");
            }
        }
        return Map.copyOf(generators);
    }
}
