package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/** Narrow immutable producer view over the authenticated production Village authority. */
final class Mc263VillageProducerGrammarAccess {
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final String INLINE = "inline";

    private Mc263VillageProducerGrammarAccess() { throw new AssertionError("no instances"); }

    static ProducerSpec require(String structureKey) {
        Objects.requireNonNull(structureKey, "Village structure key");
        for (ProducerSpec spec : Holder.SPECS) {
            if (spec.structureKey().equals(structureKey)) return spec;
        }
        throw new IllegalArgumentException("unknown Village family: " + structureKey);
    }

    static List<ProducerSpec> specs() { return Holder.SPECS; }

    private static final class Holder {
        private static final List<ProducerSpec> SPECS = build();
    }

    private static List<ProducerSpec> build() {
        Mc263VillageProductionAuthority.Corpus authority = Mc263VillageProductionAuthority.pinned();
        ArrayList<ProducerSpec> result = new ArrayList<>();
        for (Mc263VillageProductionAuthority.Family family : authority.families()) {
            result.add(buildFamily(authority, family));
        }
        return List.copyOf(result);
    }

    private static ProducerSpec buildFamily(Mc263VillageProductionAuthority.Corpus authority,
            Mc263VillageProductionAuthority.Family family) {
        java.util.LinkedHashSet<String> reachable = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        pending.add(family.startPool());
        while (!pending.isEmpty()) {
            String poolKey = pending.removeFirst();
            if (EMPTY_POOL.equals(poolKey) || !reachable.add(poolKey)) continue;
            Mc263VillageProductionAuthority.Pool pool = authority.requirePool(poolKey);
            if (!EMPTY_POOL.equals(pool.fallback())) pending.addLast(pool.fallback());
            for (Mc263VillageProductionAuthority.PoolElement element : pool.elements()) {
                if (element.kind() == Mc263VillageProductionAuthority.ElementKind.TEMPLATE) {
                    Mc263VillageProductionAuthority.TemplateAuthority template =
                            authority.requireTemplate(element.key());
                    for (Mc263VillageProductionAuthority.Connector connector : template.connectors()) {
                        if (!EMPTY_POOL.equals(connector.pool())) pending.addLast(connector.pool());
                    }
                } else if (element.kind() == Mc263VillageProductionAuthority.ElementKind.FEATURE) {
                    authority.requireFeature(element.key());
                } else if (element.kind() != Mc263VillageProductionAuthority.ElementKind.EMPTY) {
                    throw new IllegalArgumentException("unknown Village producer pool element");
                }
            }
        }

        ArrayList<ProducerPool> pools = new ArrayList<>();
        java.util.LinkedHashSet<String> templates = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> processors = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> features = new java.util.LinkedHashSet<>();
        for (Mc263VillageProductionAuthority.Pool pool : authority.poolsInOrder()) {
            if (!reachable.contains(pool.key())) continue;
            if (!EMPTY_POOL.equals(pool.fallback()) && !reachable.contains(pool.fallback())) {
                throw new IllegalArgumentException("Village producer fallback escaped family closure: "
                        + pool.fallback());
            }
            ArrayList<ProducerElement> elements = new ArrayList<>();
            for (Mc263VillageProductionAuthority.PoolElement element : pool.elements()) {
                if (element.kind() == Mc263VillageProductionAuthority.ElementKind.TEMPLATE) {
                    Mc263VillageProductionAuthority.TemplateAuthority template =
                            authority.requireTemplate(element.key());
                    authority.requireProcessorList(element.processorList());
                    authority.placementProcessors(element.key(), projectionName(element.projection()),
                            element.processorList());
                    templates.add(element.key());
                    if (!INLINE.equals(element.processorList())) processors.add(element.processorList());
                    ArrayList<ProducerConnector> connectors = new ArrayList<>();
                    for (Mc263VillageProductionAuthority.Connector connector : template.connectors()) {
                        if (!EMPTY_POOL.equals(connector.pool())
                                && !reachable.contains(connector.pool())) {
                            throw new IllegalArgumentException(
                                    "Village producer connector escaped family closure: "
                                            + connector.pool());
                        }
                        connectors.add(new ProducerConnector(connector.ordinal(), connector.x(),
                                connector.y(), connector.z(), connector.front(), connector.top(),
                                connector.joint(), connector.name(), connector.target(), connector.pool(),
                                connector.selectionPriority(), connector.placementPriority()));
                    }
                    elements.add(new ProducerElement(ProducerElementKind.TEMPLATE,
                            element.ordinal(), element.weight(), element.projection(), element.key(),
                            element.processorList(), template.sizeX(), template.sizeY(), template.sizeZ(),
                            connectors));
                } else if (element.kind() == Mc263VillageProductionAuthority.ElementKind.FEATURE) {
                    authority.requireFeature(element.key());
                    features.add(element.key());
                    elements.add(new ProducerElement(ProducerElementKind.FEATURE,
                            element.ordinal(), element.weight(), element.projection(), element.key(), "",
                            1, 1, 1, List.of()));
                } else if (element.kind() == Mc263VillageProductionAuthority.ElementKind.EMPTY) {
                    elements.add(new ProducerElement(ProducerElementKind.EMPTY,
                            element.ordinal(), element.weight(), element.projection(), "", "",
                            0, 0, 0, List.of()));
                } else {
                    throw new IllegalArgumentException("unknown Village producer pool element");
                }
            }
            pools.add(new ProducerPool(pool.key(), pool.fallback(), elements));
        }
        if (pools.size() != reachable.size()) {
            throw new IllegalArgumentException("Village producer pool closure cardinality drift");
        }
        return new ProducerSpec(family.structureKey(), family.biomeTag(), family.startPool(),
                family.size(), family.startHeightAbsolute(), family.useExpansionHack(),
                family.projectStartToHeightmap(), family.maxDistanceFromCenter(),
                family.terrainAdaptation(), pools, List.copyOf(reachable), List.copyOf(templates),
                List.copyOf(processors), List.copyOf(features));
    }

    private static String projectionName(String projection) {
        return switch (projection) {
            case "RIGID" -> "rigid";
            case "TERRAIN_MATCHING" -> "terrain_matching";
            default -> throw new IllegalArgumentException("unknown Village projection: " + projection);
        };
    }

    enum ProducerElementKind { TEMPLATE, FEATURE, EMPTY }

    record ProducerSpec(String structureKey, String biomeTag, String startPool, int size,
            int startHeightAbsolute, boolean useExpansionHack, String projectStartToHeightmap,
            int maxDistanceFromCenter, String terrainAdaptation, List<ProducerPool> pools,
            List<String> poolCapabilities, List<String> templateCapabilities,
            List<String> processorCapabilities, List<String> featureCapabilities) {
        ProducerSpec {
            Objects.requireNonNull(structureKey); Objects.requireNonNull(biomeTag);
            Objects.requireNonNull(startPool); Objects.requireNonNull(projectStartToHeightmap);
            Objects.requireNonNull(terrainAdaptation);
            pools = List.copyOf(pools); poolCapabilities = List.copyOf(poolCapabilities);
            templateCapabilities = List.copyOf(templateCapabilities);
            processorCapabilities = List.copyOf(processorCapabilities);
            featureCapabilities = List.copyOf(featureCapabilities);
        }
    }

    record ProducerPool(String key, String fallback, List<ProducerElement> elements) {
        ProducerPool { elements = List.copyOf(elements); }
    }

    record ProducerElement(ProducerElementKind kind, int ordinal, int weight, String projection,
            String key, String processor, int sizeX, int sizeY, int sizeZ,
            List<ProducerConnector> connectors) {
        ProducerElement { connectors = List.copyOf(connectors); }
    }

    record ProducerConnector(int ordinal, int x, int y, int z, String front, String top,
            String joint, String name, String target, String pool,
            int selectionPriority, int placementPriority) { }
}
