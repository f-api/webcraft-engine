package com.gameexpert.authority.versioned;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** An outcome received from a profile-bound producer, not caller-authored loot data. */
public final class LateLootOutcome {
    public static final class Claim {
        private final String structureId,rowSha256;
        private final int originChunkX,originChunkZ;
        public Claim(String structureId,int originChunkX,int originChunkZ,String rowSha256){
            if(structureId==null||!structureId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    ||rowSha256==null||!rowSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid structure claim");
            this.structureId=structureId;this.originChunkX=originChunkX;this.originChunkZ=originChunkZ;this.rowSha256=rowSha256;
        }
        public String structureId(){return structureId;}
        public int originChunkX(){return originChunkX;}
        public int originChunkZ(){return originChunkZ;}
        public String structureRowSha256(){return rowSha256;}
    }
    private final boolean needsStructure;
    private final int chunkX,chunkZ;
    private final String originalContextSha256,referenceEpoch;
    private final byte[] resolution,encoded;
    private final NeutralFinalChunk.ProductionContext context;
    private final List<Claim> claims;
    private final Map<String,byte[]> previews;
    private LateLootOutcome(boolean needsStructure,int chunkX,int chunkZ,String originalContextSha256,
            String referenceEpoch,byte[] resolution,NeutralFinalChunk.ProductionContext context,List<Claim> claims,Map<String,byte[]> previews,byte[] encoded){
        this.needsStructure=needsStructure;this.chunkX=chunkX;this.chunkZ=chunkZ;
        this.originalContextSha256=originalContextSha256;this.referenceEpoch=referenceEpoch;
        this.resolution=resolution==null?null:resolution.clone();this.context=context;this.claims=List.copyOf(claims);this.encoded=encoded.clone();this.previews=new HashMap<>();previews.forEach((key,value)->this.previews.put(key,value.clone()));
    }
    static LateLootOutcome fromWorker(byte[] encoded,byte[] expectedOriginalContext){
        if(encoded==null||encoded.length>64*1024*1024)throw new IllegalArgumentException("invalid late loot outcome bounds");
        try(var in=new DataInputStream(new ByteArrayInputStream(encoded))){
            if(in.readInt()!=0x57504731||in.readUnsignedByte()!=1)throw new IOException("invalid late loot envelope");
            int status=in.readUnsignedByte();
            if(status==0){int x=in.readInt(),z=in.readInt();if(in.available()!=0)throw new IOException("trailing preparation outcome");return new LateLootOutcome(true,x,z,null,null,null,null,List.of(),Map.of(),encoded);}
            if(status!=1)throw new IOException("unknown late loot outcome");
            String original=in.readUTF(),epoch=in.readUTF();
            if(!original.matches("[0-9a-f]{64}")||!epoch.matches("[0-9a-f]{64}")||!original.equals(sha(expectedOriginalContext)))throw new IOException("late loot original context differs");
            byte[] resolution=frame(in),contextBytes=frame(in);NeutralFinalChunk.ProductionContext context;
            try(var contextIn=new DataInputStream(new ByteArrayInputStream(contextBytes))){context=NeutralFinalChunkWire.context(contextIn);if(contextIn.available()!=0)throw new IOException("trailing resolved context");}
            int previewCount=in.readInt();if(previewCount<0||previewCount>1024)throw new IOException("invalid preview count");
            var previews=new HashMap<String,byte[]>();for(int i=0;i<previewCount;i++){String key=in.readUTF();byte[] colors=frame(in);if(!key.matches("[0-9a-f]{64}")||colors.length!=16384||previews.put(key,colors)!=null)throw new IOException("invalid map preview");}
            int count=in.readInt();if(count<0||count>1_000_000)throw new IOException("invalid pending claim count");
            var claims=new ArrayList<Claim>();var keys=new HashSet<String>();
            for(int i=0;i<count;i++){var claim=new Claim(in.readUTF(),in.readInt(),in.readInt(),in.readUTF());if(!keys.add(claim.structureId()+"\0"+claim.originChunkX()+"\0"+claim.originChunkZ()))throw new IOException("duplicate pending claim");claims.add(claim);}
            int authenticatedLength=encoded.length-in.available();String receipt=in.readUTF();if(in.available()!=0)throw new IOException("trailing late loot bytes");
            var digest=MessageDigest.getInstance("SHA-256");digest.update("WEBCRAFT-LATE-LOOT-OUTCOME-V1\0".getBytes(StandardCharsets.US_ASCII));digest.update(encoded,0,authenticatedLength);
            if(!HexFormat.of().formatHex(digest.digest()).equals(receipt))throw new IOException("late loot outcome receipt differs");
            return new LateLootOutcome(false,0,0,original,epoch,resolution,context,claims,previews,encoded);
        }catch(Exception failure){throw new IllegalArgumentException("invalid producer late loot outcome",failure);}
    }
    private static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(value)));}
    private static byte[] frame(DataInputStream in)throws IOException{int n=in.readInt();if(n<=0||n>in.available())throw new IOException("invalid outcome frame");return in.readNBytes(n);}
    public boolean needsStructure(){return needsStructure;}
    public int neededChunkX(){if(!needsStructure)throw new IllegalStateException("not a preparation request");return chunkX;}
    public int neededChunkZ(){if(!needsStructure)throw new IllegalStateException("not a preparation request");return chunkZ;}
    public String originalContextSha256(){return originalContextSha256;}
    public String referenceEpoch(){return referenceEpoch;}
    public byte[] resolution(){if(needsStructure)throw new IllegalStateException("unresolved loot");return resolution.clone();}
    public NeutralFinalChunk.ProductionContext resolvedContext(){if(needsStructure)throw new IllegalStateException("unresolved loot");return context;}
    public List<Claim> claims(){return claims;}
    public byte[] preview(String targetReceipt){byte[] value=previews.get(targetReceipt);if(value==null)throw new IllegalArgumentException("missing executed map preview");return value.clone();}
    public byte[] encoded(){return encoded.clone();}
}
