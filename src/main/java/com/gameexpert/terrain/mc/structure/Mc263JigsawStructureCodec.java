package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ConnectorSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Direction;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Joint;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.PoolSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Projection;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ValidatedGrammar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Stable reload codec for validated procedural jigsaw grammar; not a template/NBT codec. */
public final class Mc263JigsawStructureCodec {
    private static final byte[] MAGIC = "JIG263D2".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_POOLS = 4_096;
    private static final int MAX_ELEMENTS = 65_536;
    private static final int MAX_CONNECTORS = 65_536;
    private static final int MAX_COMPONENTS = 4_096;
    private static final int MAX_STRING_BYTES = 32_767;

    private Mc263JigsawStructureCodec() {}

    public static byte[] encode(Grammar grammar) {
        ValidatedGrammar validated = Mc263JigsawStructureBoundary.validate(grammar);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(MAGIC);
            writeString(out, Mc263JigsawStructureCatalog.VERSION);
            writeString(out, Mc263JigsawStructureCatalog.SERVER_SHA1);
            writeString(out, validated.structure().key());
            out.writeInt(validated.pools().size());
            for (PoolSpec pool : validated.pools()) writePool(out, pool);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Grammar decode(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("jigsaw grammar bytes are required");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IllegalArgumentException("unknown jigsaw grammar codec");
            }
            if (!readString(in).equals(Mc263JigsawStructureCatalog.VERSION)
                    || !readString(in).equals(Mc263JigsawStructureCatalog.SERVER_SHA1)) {
                throw new IllegalArgumentException("jigsaw grammar pin mismatch");
            }
            String structureKey = readString(in);
            int poolCount = readCount(in, MAX_POOLS, "pool count");
            List<PoolSpec> pools = new ArrayList<>(poolCount);
            for (int index = 0; index < poolCount; index++) pools.add(readPool(in));
            if (in.read() != -1) throw new IllegalArgumentException("trailing jigsaw grammar data");
            Grammar grammar = new Grammar(structureKey, pools);
            Mc263JigsawStructureBoundary.validate(grammar);
            return grammar;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("truncated jigsaw grammar", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid jigsaw grammar", exception);
        }
    }

    private static void writePool(DataOutputStream out, PoolSpec pool) throws IOException {
        writeString(out, pool.key());
        writeString(out, pool.fallback());
        out.writeInt(pool.elements().size());
        for (ElementSpec element : pool.elements()) writeElement(out, element);
    }

    private static PoolSpec readPool(DataInputStream in) throws IOException {
        String key = readString(in);
        String fallback = readString(in);
        int count = readCount(in, MAX_ELEMENTS, "element count");
        List<ElementSpec> elements = new ArrayList<>(count);
        for (int index = 0; index < count; index++) elements.add(readElement(in));
        return new PoolSpec(key, fallback, elements);
    }

    private static void writeElement(DataOutputStream out, ElementSpec element)
            throws IOException {
        writeString(out, element.type().key());
        writeString(out, element.key());
        out.writeInt(element.weight());
        writeString(out, element.projection().name());
        writeString(out, element.processor());
        out.writeBoolean(element.bounds() != null);
        if (element.bounds() != null) writeBounds(out, element.bounds());
        out.writeInt(element.connectors().size());
        for (ConnectorSpec connector : element.connectors()) writeConnector(out, connector);
        out.writeInt(element.components().size());
        for (String component : element.components()) writeString(out, component);
    }

    private static ElementSpec readElement(DataInputStream in) throws IOException {
        ElementType type = ElementType.fromKey(readString(in));
        String key = readString(in);
        int weight = in.readInt();
        Projection projection = enumValue(Projection.class, readString(in), "projection");
        String processor = readString(in);
        Bounds bounds = in.readBoolean() ? readBounds(in) : null;
        int connectorCount = readCount(in, MAX_CONNECTORS, "connector count");
        List<ConnectorSpec> connectors = new ArrayList<>(connectorCount);
        for (int index = 0; index < connectorCount; index++) {
            connectors.add(readConnector(in));
        }
        int componentCount = readCount(in, MAX_COMPONENTS, "component count");
        List<String> components = new ArrayList<>(componentCount);
        for (int index = 0; index < componentCount; index++) components.add(readString(in));
        return new ElementSpec(type, key, weight, projection, processor, bounds,
                connectors, components);
    }

    private static void writeBounds(DataOutputStream out, Bounds bounds) throws IOException {
        out.writeInt(bounds.minX()); out.writeInt(bounds.minY()); out.writeInt(bounds.minZ());
        out.writeInt(bounds.maxX()); out.writeInt(bounds.maxY()); out.writeInt(bounds.maxZ());
    }

    private static Bounds readBounds(DataInputStream in) throws IOException {
        return new Bounds(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readInt());
    }

    private static void writeConnector(DataOutputStream out, ConnectorSpec connector)
            throws IOException {
        out.writeInt(connector.x()); out.writeInt(connector.y()); out.writeInt(connector.z());
        writeString(out, connector.front().name());
        writeString(out, connector.top().name());
        writeString(out, connector.joint().name());
        writeString(out, connector.name());
        writeString(out, connector.target());
        writeString(out, connector.pool());
        out.writeInt(connector.placementPriority());
        out.writeInt(connector.selectionPriority());
    }

    private static ConnectorSpec readConnector(DataInputStream in) throws IOException {
        int x = in.readInt(), y = in.readInt(), z = in.readInt();
        Direction front = enumValue(Direction.class, readString(in), "front direction");
        Direction top = enumValue(Direction.class, readString(in), "top direction");
        Joint joint = enumValue(Joint.class, readString(in), "joint");
        String name = readString(in), target = readString(in), pool = readString(in);
        int placementPriority = in.readInt();
        int selectionPriority = in.readInt();
        return new ConnectorSpec(x, y, z, front, top, joint, name, target, pool,
                placementPriority, selectionPriority);
    }

    private static int readCount(DataInputStream in, int maximum, String label)
            throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " outside codec bound: " + count);
        }
        return count;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("jigsaw string exceeds codec bound");
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("jigsaw string exceeds codec bound");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("jigsaw string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported " + label + ": " + value,
                    exception);
        }
    }
}
