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
        var result=new ByteArrayOutputStream();var out=new DataOutputStream(result);
        out.writeInt(0x57504731);out.writeByte(1);write(out,commit.finalCarrier());write(out,commit.structureCarrier());write(out,commit.mutablePieceSuccessor());write(out,commit.fingerprint());
        out.writeInt(commit.orderedReferenceCounts().size());for(int value:commit.orderedReferenceCounts())out.writeInt(value);
        out.flush();return result.toByteArray();
    }
    public static byte[] references(DataInputStream in) throws Exception {
        ReferenceSnapshot snapshot=readReferences(in);
        var result=new ByteArrayOutputStream();var out=new DataOutputStream(result);out.writeInt(0x57504731);out.writeByte(1);out.writeUTF(snapshot.receipt());out.writeInt(snapshot.values.size());
        for(var entry:snapshot.values.entrySet()){out.writeUTF(entry.getKey().substring(0,entry.getKey().indexOf('\0')));for(int value:entry.getValue())out.writeInt(value);}
        out.flush();return result.toByteArray();
    }
    static ReferenceSnapshot readReferences(DataInputStream in) throws Exception {
        long world=in.readLong();int count=in.readInt();
        if(world<=0||count<0||count>1_000_000)throw new IllegalArgumentException("invalid structure snapshot binding");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        digest.update("MC263-CANONICAL-STRUCTURE-REFERENCE-SNAPSHOT-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var digestBytes=new ByteArrayOutputStream();var fields=new DataOutputStream(digestBytes);fields.writeLong(world);fields.writeInt(count);fields.flush();digest.update(digestBytes.toByteArray());
        var references=new LinkedHashMap<String,int[]>();var starts=new ArrayList<RetainedStart>();int priorX=0,priorZ=0;
        for(int i=0;i<count;i++){
            int x=in.readInt(),z=in.readInt();if(i>0&&(x<priorX||x==priorX&&z<=priorZ))throw new IllegalArgumentException("structure rows must be unique and ordered");priorX=x;priorZ=z;
            byte[] raw=bytes(in,false);if(raw.length>16*1024*1024)throw new IllegalArgumentException("structure snapshot row too large");
            digestBytes.reset();fields.writeInt(x);fields.writeInt(z);fields.writeInt(raw.length);fields.flush();digest.update(digestBytes.toByteArray());digest.update(raw);
            var carrier=Mc263StructureCarrier.decode(raw);
            String rowSha256=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            for(var chunk:carrier.startChunks())for(var entry:chunk.orderedStarts())if(entry.body() instanceof Mc263StructureCarrier.ValidStart start){
                String key=entry.structureId()+"\0"+start.originChunkX()+"\0"+start.originChunkZ();
                starts.add(new RetainedStart(entry.structureId(),start,rowSha256,x,z));
                int[] existing=references.get(key);if(existing==null)references.put(key,new int[]{start.originChunkX(),start.originChunkZ(),start.references()});else existing[2]=Math.max(existing[2],start.references());
            }
        }
        if(in.available()!=0)throw new IllegalArgumentException("trailing snapshot bytes");
        return new ReferenceSnapshot(HexFormat.of().formatHex(digest.digest()),references,starts);
    }
    static final class ReferenceSnapshot implements com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot {
        private final String receipt;
        private final Map<String,int[]> values;
        private final List<RetainedStart> starts;
        private ReferenceSnapshot(String receipt,Map<String,int[]> values,List<RetainedStart> starts){this.receipt=receipt;this.values=Collections.unmodifiableMap(values);this.starts=List.copyOf(starts);}
        List<RetainedStart> starts(){return starts;}
        @Override public String receipt(){return receipt;}
        @Override public int references(String key,int x,int z){int[] row=values.get(key+"\0"+x+"\0"+z);return row==null?0:row[2];}
    }
    static final class RetainedStart {
        final String structureId;
        final Mc263StructureCarrier.ValidStart start;
        final String rowSha256;
        final int sourceChunkX,sourceChunkZ;
        RetainedStart(String structureId,Mc263StructureCarrier.ValidStart start,String rowSha256,int sourceChunkX,int sourceChunkZ){
            this.structureId=structureId;this.start=start;this.rowSha256=rowSha256;this.sourceChunkX=sourceChunkX;this.sourceChunkZ=sourceChunkZ;
        }
        String key(){return structureId+"\0"+start.originChunkX()+"\0"+start.originChunkZ();}
        boolean contains(int x,int y,int z){var box=start.adjustedBoundingBox();return x>=box.minX()&&x<=box.maxX()&&y>=box.minY()&&y<=box.maxY()&&z>=box.minZ()&&z<=box.maxZ();}
    }
    private static byte[] bytes(DataInputStream in,boolean nullable)throws IOException{int n=in.readInt();if(nullable&&n==-1)return null;if(n<=0||n>64*1024*1024||n>in.available())throw new IllegalArgumentException("invalid carrier length");return in.readNBytes(n);}
    private static void write(DataOutputStream out,byte[] value)throws IOException{out.writeInt(value==null?-1:value.length);if(value!=null)out.write(value);}
}
