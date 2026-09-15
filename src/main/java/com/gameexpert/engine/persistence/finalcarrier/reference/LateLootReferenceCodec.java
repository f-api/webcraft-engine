package com.gameexpert.engine.persistence.finalcarrier.reference;

import com.gameexpert.authority.versioned.CanonicalStructureSnapshot;
import com.gameexpert.authority.versioned.LateLootOutcome;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable membership reconstructs the original worker input even after the world grows. */
public final class LateLootReferenceCodec {
    private LateLootReferenceCodec() { }
    public static byte[] membership(CanonicalStructureSnapshot snapshot) {
        return encode(out->{out.writeInt(0x574c5331);out.writeByte(1);out.writeInt(snapshot.rows().size());
            for(var row:snapshot.rows()){out.writeInt(row.chunkX());out.writeInt(row.chunkZ());out.writeUTF(sha256(row.carrier()));}});
    }
    public static byte[] claims(List<LateLootOutcome.Claim> input) {
        var ordered=ordered(input);
        return encode(out->{out.writeInt(0x574c4331);out.writeByte(1);out.writeInt(ordered.size());
            for(var row:ordered){out.writeUTF(row.structureId());out.writeInt(row.originChunkX());out.writeInt(row.originChunkZ());out.writeUTF(row.structureRowSha256());}});
    }
    public static List<LateLootOutcome.Claim> readClaims(byte[] bytes) {
        try(var in=read(bytes,0x574c4331)) {
            int n=count(in);var result=new ArrayList<LateLootOutcome.Claim>();String previous=null;
            for(int i=0;i<n;i++){var row=new LateLootOutcome.Claim(in.readUTF(),in.readInt(),in.readInt(),in.readUTF());String key=key(row);if(previous!=null&&previous.compareTo(key)>=0)throw new IOException("claim order drift");result.add(row);previous=key;}
            if(in.available()!=0)throw new IOException("trailing claims");return List.copyOf(result);
        }catch(IOException failure){throw new IllegalArgumentException("invalid saved reference claims",failure);}
    }
    public static CanonicalStructureSnapshot restore(WorldLootReferenceSnapshot saved,
            CanonicalStructureSnapshot current) {
        if(saved.getWorldId()!=current.worldId()||!saved.getBaselineId().equals(current.profile().getBaselineId()))throw new IllegalArgumentException("snapshot world/profile differs");
        var available=new HashMap<Long,CanonicalStructureSnapshot.Row>();for(var row:current.rows())available.put(key(row.chunkX(),row.chunkZ()),row);
        try(var in=read(saved.getMembershipPayload(),0x574c5331)) {
            int count=count(in);var rows=new ArrayList<CanonicalStructureSnapshot.Row>();int previousX=0,previousZ=0;
            for(int i=0;i<count;i++){int x=in.readInt(),z=in.readInt();String expected=in.readUTF();
                if(i>0&&(x<previousX||x==previousX&&z<=previousZ))throw new IOException("membership order drift");previousX=x;previousZ=z;
                var row=available.get(key(x,z));if(row==null||!sha256(row.carrier()).equals(expected))throw new IOException("immutable structure member missing or changed");rows.add(row);
            }
            if(in.available()!=0)throw new IOException("trailing snapshot membership");
            var restored=new CanonicalStructureSnapshot(current.worldId(),current.profile(),rows);
            byte[] claimBytes=saved.getClaimsPayload();var before=readClaims(claimBytes);
            if(!restored.receipt().equals(saved.getStructureSnapshotReceipt())
                    ||!sha256(claimBytes).equals(saved.getClaimsFingerprint())
                    ||!epoch(restored.receipt(),before).equals(saved.getSnapshotIdentity()))throw new IOException("historical snapshot receipt differs");
            return restored;
        }catch(IOException failure){throw new IllegalArgumentException("invalid immutable replay membership",failure);}
    }
    public static String epoch(String structureReceipt,List<LateLootOutcome.Claim> claims) {
        if(structureReceipt==null||!structureReceipt.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid structure receipt");
        var ordered=ordered(claims);
        return sha256(encode(out->{out.write("WEBCRAFT-STRUCTURE-CLAIM-SNAPSHOT-V1\0".getBytes(StandardCharsets.US_ASCII));out.writeUTF(structureReceipt);out.writeInt(ordered.size());for(var row:ordered){out.writeUTF(key(row));out.writeUTF(row.structureRowSha256());}}));
    }
    public static String sha256(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static List<LateLootOutcome.Claim> ordered(List<LateLootOutcome.Claim> rows){
        if(rows==null||rows.size()>1_000_000)throw new IllegalArgumentException("invalid claims");var ordered=new ArrayList<>(rows);ordered.sort(Comparator.comparing(LateLootReferenceCodec::key));for(int i=1;i<ordered.size();i++)if(key(ordered.get(i-1)).equals(key(ordered.get(i))))throw new IllegalArgumentException("duplicate claim");return ordered;
    }
    private static String key(LateLootOutcome.Claim row){return row.structureId()+"\0"+row.originChunkX()+"\0"+row.originChunkZ();}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static DataInputStream read(byte[] bytes,int magic)throws IOException{if(bytes==null||bytes.length>64*1024*1024)throw new IOException("invalid snapshot bounds");var in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=magic||in.readUnsignedByte()!=1)throw new IOException("snapshot schema differs");return in;}
    private static int count(DataInputStream in)throws IOException{int count=in.readInt();if(count<0||count>1_000_000)throw new IOException("snapshot row bounds");return count;}
    @FunctionalInterface private interface Writer {void write(DataOutputStream out)throws IOException;}
    private static byte[] encode(Writer writer){try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);writer.write(out);out.flush();if(bytes.size()>64*1024*1024)throw new IllegalArgumentException("snapshot exceeds bounds");return bytes.toByteArray();}catch(IOException impossible){throw new IllegalStateException(impossible);}}
}
