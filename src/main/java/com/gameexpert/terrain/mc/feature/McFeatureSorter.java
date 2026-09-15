package com.gameexpert.terrain.mc.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Name-based reproduction of the pinned Minecraft {@code FeatureSorter} DAG algorithm. */
public final class McFeatureSorter {
    private McFeatureSorter() {
    }

    public record BiomeFeatures(String biomeKey, List<List<String>> featuresByStep) {
        public BiomeFeatures {
            requireKey(biomeKey, "biome");
            Objects.requireNonNull(featuresByStep, "featuresByStep");
            List<List<String>> steps = new ArrayList<>(featuresByStep.size());
            for (List<String> step : featuresByStep) {
                Objects.requireNonNull(step, "feature step");
                for (String feature : step) requireKey(feature, "feature");
                steps.add(List.copyOf(step));
            }
            featuresByStep = List.copyOf(steps);
        }
    }

    public record StepFeatures(int step, List<String> featureKeys) {
        public StepFeatures {
            if (step < 0) throw new IllegalArgumentException("negative generation step");
            featureKeys = List.copyOf(featureKeys);
        }
    }

    /**
     * Uses the same first-identity encounter index, (step,index) comparator, biome-flattened edges,
     * sorted depth-first traversal and final reversal as 26.3-snapshot-7's FeatureSorter bytecode.
     */
    public static List<StepFeatures> sort(List<BiomeFeatures> biomes) {
        Objects.requireNonNull(biomes, "biomes");
        Set<String> biomeKeys = new HashSet<>();
        Map<String, Integer> firstFeatureIndex = new LinkedHashMap<>();
        List<List<Node>> flattenedBiomes = new ArrayList<>(biomes.size());
        int maxStepCount = 0;

        for (BiomeFeatures biome : biomes) {
            Objects.requireNonNull(biome, "biome");
            if (!biomeKeys.add(biome.biomeKey())) {
                throw new IllegalArgumentException("duplicate biome key: " + biome.biomeKey());
            }
            maxStepCount = Math.max(maxStepCount, biome.featuresByStep().size());
            List<Node> flattened = new ArrayList<>();
            for (int step = 0; step < biome.featuresByStep().size(); step++) {
                for (String feature : biome.featuresByStep().get(step)) {
                    int featureIndex = firstFeatureIndex.computeIfAbsent(
                            feature, ignored -> firstFeatureIndex.size());
                    flattened.add(new Node(featureIndex, step, feature));
                }
            }
            flattenedBiomes.add(flattened);
        }

        Comparator<Node> comparator = Comparator.comparingInt(Node::step)
                .thenComparingInt(Node::featureIndex);
        Map<Node, Set<Node>> edges = new TreeMap<>(comparator);
        for (List<Node> flattened : flattenedBiomes) {
            for (int i = 0; i < flattened.size(); i++) {
                Node node = flattened.get(i);
                Set<Node> successors = edges.computeIfAbsent(
                        node, ignored -> new TreeSet<>(comparator));
                if (i + 1 < flattened.size()) successors.add(flattened.get(i + 1));
            }
        }

        Set<Node> finished = new TreeSet<>(comparator);
        Set<Node> inProgress = new TreeSet<>(comparator);
        List<Node> postOrder = new ArrayList<>(edges.size());
        for (Node node : edges.keySet()) {
            if (!inProgress.isEmpty()) {
                throw new IllegalStateException("FeatureSorter DFS retained in-progress nodes");
            }
            if (!finished.contains(node)
                    && depthFirstSearch(edges, finished, inProgress, postOrder, node)) {
                throw new IllegalStateException("feature order cycle found");
            }
        }

        List<StepFeatures> result = new ArrayList<>(maxStepCount);
        for (int step = 0; step < maxStepCount; step++) {
            List<String> keys = new ArrayList<>();
            for (int i = postOrder.size() - 1; i >= 0; i--) {
                Node node = postOrder.get(i);
                if (node.step() == step) keys.add(node.featureKey());
            }
            result.add(new StepFeatures(step, keys));
        }
        return List.copyOf(result);
    }

    private static boolean depthFirstSearch(Map<Node, Set<Node>> edges, Set<Node> finished,
            Set<Node> inProgress, List<Node> postOrder, Node node) {
        if (finished.contains(node)) return false;
        if (inProgress.contains(node)) return true;
        inProgress.add(node);
        for (Node successor : edges.getOrDefault(node, Set.of())) {
            if (depthFirstSearch(edges, finished, inProgress, postOrder, successor)) return true;
        }
        inProgress.remove(node);
        finished.add(node);
        postOrder.add(node);
        return false;
    }

    static void requireUnambiguous(List<BiomeFeatures> biomes) {
        Map<Integer, Set<String>> globalPerStep = new HashMap<>();
        for (BiomeFeatures biome : biomes) {
            Set<StepKey> seenInBiome = new HashSet<>();
            for (int step = 0; step < biome.featuresByStep().size(); step++) {
                Set<String> global = globalPerStep.computeIfAbsent(step, ignored -> new HashSet<>());
                for (String feature : biome.featuresByStep().get(step)) {
                    if (!seenInBiome.add(new StepKey(step, feature))) {
                        throw new IllegalArgumentException("duplicate feature reference in "
                                + biome.biomeKey() + " step " + step + ": " + feature);
                    }
                    global.add(feature);
                }
            }
        }
    }

    private static void requireKey(String key, String label) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(label + " key is required");
        }
    }

    private record Node(int featureIndex, int step, String featureKey) {
    }

    private record StepKey(int step, String featureKey) {
    }
}
