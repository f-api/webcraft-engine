package com.gameexpert.terrain.mc.feature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The pinned capability allow-lists of the FEATURES world adapter, loaded once from
 * {@code /mc263/feature-block-capability-sets-v1.txt}.
 *
 * <p>Each of these sets used to exist twice — a Java {@code switch}/{@code Set.of} and a
 * hand-written Rust {@code matches!} mirror. Two hand copies of one authenticated list is a defect
 * class, not a defect: a key added to one side and forgotten on the other diverges silently and
 * only surfaces as a byte difference deep inside a feature step. The list now exists exactly once,
 * in the pinned resource, and both languages read that one document — Java through this class, Rust
 * through {@code mc_feature_world_adapter_263::capability_set} over the same file with the same
 * identity pin. Adding a key means editing the resource; there is no second place to forget.
 *
 * <p>The resource is not evidence of vanilla behaviour on its own: it is the projection of the
 * pinned tag/registry closures the adapter already carried, and its identity constants are pinned
 * here so a silent edit fails closed.
 */
public final class Mc263FeatureCapabilitySets {

    public static final String RESOURCE = "/mc263/feature-block-capability-sets-v1.txt";
    public static final int RESOURCE_LENGTH = 4_584;
    public static final String RESOURCE_SHA256 =
            "9490277a5820e139f177b95e96097fa3f7331c0d869ea210b6887cadcae824a7";
    public static final int ROW_BYTES = 4_482;
    public static final String ROW_SHA256 =
            "2cdfb4ce4f6bde124f2f35a9e14b8086e2a5cb8bafb4ba9258ebf224cf6c13ae";
    public static final int SET_COUNT = 8;
    public static final int KEY_COUNT = 100;

    private static final Map<String, Set<String>> PINNED = load();

    private Mc263FeatureCapabilitySets() {}

    /** {@code RootSystemFeature} azalea substrate closure. */
    public static Set<String> azaleaGrowsOn() { return set("azalea_grows_on"); }

    /** The three dry-vegetation blocks whose survival consults the dry substrate closure. */
    public static Set<String> dryVegetation() { return set("dry_vegetation"); }

    /** Substrates that carry dry vegetation beyond {@code #supports_vegetation}. */
    public static Set<String> dryVegetationExtra() { return set("dry_vegetation_extra"); }

    /** Lush-ground substrates beyond the moss-replaceable closure. */
    public static Set<String> lushGroundExtra() { return set("lush_ground_extra"); }

    /** {@code #moss_replaceable} closure. */
    public static Set<String> mossReplaceable() { return set("moss_replaceable"); }

    /** Saplings whose tree feature the adapter can run. */
    public static Set<String> saplingSurvives() { return set("sapling_survives"); }

    /** Tree feature keys the adapter answers beyond every generated tree registry. */
    public static Set<String> treeExtraKeys() { return set("tree_extra_keys"); }

    /** Placed tree wrapper keys the adapter answers. */
    public static Set<String> treeWrapperKeys() { return set("tree_wrapper_keys"); }

    private static Set<String> set(String name) {
        Set<String> value = PINNED.get(name);
        if (value == null) {
            throw new IllegalStateException("unknown feature capability set: " + name);
        }
        return value;
    }

    private static Map<String, Set<String>> load() {
        byte[] resource;
        try (InputStream input =
                Mc263FeatureCapabilitySets.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing feature capability sets resource");
            }
            resource = input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("failed to read feature capability sets resource",
                    error);
        }
        require(resource.length == RESOURCE_LENGTH && sha256(resource).equals(RESOURCE_SHA256),
                "feature capability sets resource identity drift");
        String text = new String(resource, StandardCharsets.US_ASCII);
        require(text.endsWith("\n") && text.indexOf('\r') < 0,
                "feature capability sets newline drift");
        String[] lines = text.split("\n", -1);
        String[] header = lines[0].split("\\|", -1);
        require(header.length == 7 && header[0].equals("H") && header[1].equals("FBC2631")
                        && header[2].equals("26.3-snapshot-7")
                        && Integer.parseInt(header[3]) == SET_COUNT
                        && Integer.parseInt(header[4]) == KEY_COUNT
                        && Integer.parseInt(header[5]) == ROW_BYTES
                        && header[6].equals(ROW_SHA256),
                "feature capability sets header drift");
        byte[] rows = Arrays.copyOfRange(resource,
                lines[0].getBytes(StandardCharsets.US_ASCII).length + 1, resource.length);
        require(rows.length == ROW_BYTES && sha256(rows).equals(ROW_SHA256),
                "feature capability sets row identity drift");

        LinkedHashMap<String, Set<String>> loaded = new LinkedHashMap<>();
        String current = null;
        int declared = 0;
        LinkedHashSet<String> keys = null;
        int total = 0;
        for (int index = 1; index < lines.length - 1; index++) {
            String[] row = lines[index].split("\\|", -1);
            require(row.length == 3, "feature capability sets row shape drift");
            switch (row[0]) {
                case "S" -> {
                    finish(loaded, current, keys, declared);
                    current = row[1];
                    declared = Integer.parseInt(row[2]);
                    keys = new LinkedHashSet<>();
                    require(!loaded.containsKey(current),
                            "duplicate feature capability set: " + current);
                }
                case "K" -> {
                    require(current != null && current.equals(row[1]),
                            "feature capability key outside its set");
                    require(row[2].startsWith("minecraft:"),
                            "unnamespaced feature capability key: " + row[2]);
                    require(keys.add(row[2]),
                            "duplicate feature capability key: " + row[2]);
                    total++;
                }
                default -> throw new IllegalStateException(
                        "unknown feature capability opcode: " + row[0]);
            }
        }
        finish(loaded, current, keys, declared);
        require(loaded.size() == SET_COUNT, "feature capability set count drift");
        require(total == KEY_COUNT, "feature capability key count drift");
        return Map.copyOf(loaded);
    }

    private static void finish(Map<String, Set<String>> loaded, String name,
            Set<String> keys, int declared) {
        if (name == null) return;
        require(keys.size() == declared,
                "feature capability set size drift: " + name);
        loaded.put(name, Set.copyOf(keys));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
