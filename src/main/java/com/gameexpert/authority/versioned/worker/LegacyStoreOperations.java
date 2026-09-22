package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Durable envelope verification remains entirely inside the selected producer namespace. */
public final class LegacyStoreOperations {
    private LegacyStoreOperations() { }
    public static byte[] validateCommit(DataInputStream in) throws Exception {
        long world=in.readLong();int x=in.readInt(),z=in.readInt();
        byte[] fin=bytes(in,false),structure=bytes(in,false),successor=bytes(in,true),fingerprint=bytes(in,false);
        int count=in.readInt();if(count<0||count>98304||count*4L!=in.available())throw new IllegalArgumentException("invalid override frame");
        int[] overrides=new int[count];for(int i=0;i<count;i++)overrides[i]=in.readInt();
        String identity=(String)Class.forName("com.gameexpert.world.WorldBaseline").getField("ID").get(null);
        var commit=new CanonicalWorldgenStore.ChunkCommit(world,identity,x,z,fin,structure,successor,fingerprint,overrides);
        byte[] cf=commit.finalCarrier(),cs=commit.structureCarrier(),cm=commit.mutablePieceSuccessor(),ch=commit.fingerprint();
        var counts=commit.orderedReferenceCounts();
        var result=new WorkerBytes(4+1+16L+cf.length+cs.length+(cm==null?0:cm.length)+ch.length+4+4L*counts.size());var out=new DataOutputStream(result);
        out.writeInt(0x57504731);out.writeByte(1);write(out,cf);write(out,cs);write(out,cm);write(out,ch);
        out.writeInt(counts.size());for(int value:counts)out.writeInt(value);
        out.flush();return result.exact();
    }
    public static byte[] references(DataInputStream in) throws Exception {
        ReferenceSnapshot snapshot=readReferences(in);
        var result=new ByteArrayOutputStream();var out=new DataOutputStream(result);out.writeInt(0x57504731);out.writeByte(1);out.writeUTF(snapshot.receipt());out.writeInt(snapshot.values.size());
        for(var entry:snapshot.values.entrySet()){out.writeUTF(entry.getKey().substring(0,entry.getKey().indexOf('\0')));for(int value:entry.getValue())out.writeInt(value);}
        out.flush();return result.toByteArray();
    }
    static ReferenceSnapshot readReferences(DataInputStream in) throws Exception {
        SnapshotBuilder builder = new SnapshotBuilder(in.readLong(), in.readInt());
        while (!builder.complete()) builder.append(in.readInt(), in.readInt(), bytes(in, false));
        if (in.available() != 0) throw new IllegalArgumentException("trailing snapshot bytes");
        return builder.finish();
    }
    static final class SnapshotBuilder {
        private final MessageDigest digest;
        private final int count;
        private int consumed, priorX, priorZ;
        private final Map<String,int[]> references = new LinkedHashMap<>();
        private final List<RetainedStart> starts = new ArrayList<>();
        private ReferenceSnapshot finished;
        SnapshotBuilder(long world, int count) throws Exception {
            if (world <= 0 || count < 0) throw new IllegalArgumentException("invalid structure snapshot binding");
            this.count = count;
            digest = MessageDigest.getInstance("SHA-256");
            digest.update("MC263-CANONICAL-STRUCTURE-REFERENCE-SNAPSHOT-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            try (DataOutputStream fields = new DataOutputStream(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                fields.writeLong(world); fields.writeInt(count);
            }
        }
        boolean complete() { return consumed == count; }
        void append(int x, int z, byte[] raw) throws Exception {
            if (finished != null || complete()) throw new IllegalArgumentException("too many structure rows");
            if (consumed > 0 && (x < priorX || x == priorX && z <= priorZ)) throw new IllegalArgumentException("structure rows must be unique and ordered");
            if (raw.length == 0 || raw.length > 16 * 1024 * 1024) throw new IllegalArgumentException("structure snapshot row too large");
            try (DataOutputStream fields = new DataOutputStream(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                fields.writeInt(x); fields.writeInt(z); fields.writeInt(raw.length); fields.write(raw);
            }
            Mc263StructureCarrier carrier = Mc263StructureCarrier.decode(raw);
            String rowSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            carrier.startChunks().forEach(chunk -> chunk.orderedStarts().forEach(entry -> {
                if (entry.body() instanceof Mc263StructureCarrier.ValidStart start) {
                    String key = entry.structureId() + "\0" + start.originChunkX() + "\0" + start.originChunkZ();
                    starts.add(new RetainedStart(entry.structureId(), start, rowSha256, x, z));
                    int[] existing = references.get(key);
                    if (existing == null) references.put(key, new int[]{start.originChunkX(), start.originChunkZ(), start.references()});
                    else existing[2] = Math.max(existing[2], start.references());
                }
            }));
            priorX = x; priorZ = z; consumed++;
        }
        ReferenceSnapshot finish() {
            if (!complete()) throw new IllegalArgumentException("incomplete structure snapshot");
            if (finished == null) finished = new ReferenceSnapshot(HexFormat.of().formatHex(digest.digest()), references, starts);
            return finished;
        }
    }
    static final class ReferenceSnapshot implements com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot {
        private final String receipt;
        private final Map<String,int[]> values;
        private final List<RetainedStart> starts;
        private ReferenceSnapshot(String receipt,Map<String,int[]> values,List<RetainedStart> starts){this.receipt=receipt;this.values=Collections.unmodifiableMap(values);this.starts=List.copyOf(starts);}
        List<RetainedStart> starts(){return starts;}
        Map<String,int[]> values(){return values;}
        @Override public String receipt(){return receipt;}
        @Override public int references(String key,int x,int z){int[] row=values.get(key+"\0"+x+"\0"+z);return row==null?0:row[2];}
    }
    static final class RetainedStart {
        final String structureId;
        final int originChunkX, originChunkZ;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final String rowSha256;
        final int sourceChunkX,sourceChunkZ;
        RetainedStart(String structureId,Mc263StructureCarrier.ValidStart start,String rowSha256,int sourceChunkX,int sourceChunkZ){
            this.structureId=structureId;this.rowSha256=rowSha256;
            originChunkX=start.originChunkX();originChunkZ=start.originChunkZ();
            minX=start.adjustedBoundingBox().minX();minY=start.adjustedBoundingBox().minY();minZ=start.adjustedBoundingBox().minZ();
            maxX=start.adjustedBoundingBox().maxX();maxY=start.adjustedBoundingBox().maxY();maxZ=start.adjustedBoundingBox().maxZ();this.sourceChunkX=sourceChunkX;this.sourceChunkZ=sourceChunkZ;
        }
        String key(){return structureId+"\0"+originChunkX+"\0"+originChunkZ;}
        boolean contains(int x,int y,int z){return x>=minX&&x<=maxX&&y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ;}
    }
    private static byte[] bytes(DataInputStream in,boolean nullable)throws IOException{int n=in.readInt();if(nullable&&n==-1)return null;if(n<=0||n>64*1024*1024||n>in.available())throw new IllegalArgumentException("invalid carrier length");return in.readNBytes(n);}
    private static void write(DataOutputStream out,byte[] value)throws IOException{out.writeInt(value==null?-1:value.length);if(value!=null)out.write(value);}
}
