package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** A staged reference epoch; publication belongs to the caller's loot settlement transaction. */
final class WorkerClaimSnapshot {
    private final LegacyStoreOperations.ReferenceSnapshot source;
    private final boolean declarationWitnessOnly;
    private final Map<String,LegacyStoreOperations.RetainedStart> canonicalStarts = new HashMap<>();
    private final LinkedHashMap<String,String> existing = new LinkedHashMap<>();
    private final LinkedHashMap<String,String> pending = new LinkedHashMap<>();
    private final Mc263LocatedMapAuthority.StructureReferenceSnapshot capability;

    WorkerClaimSnapshot(LegacyStoreOperations.ReferenceSnapshot source,
            Map<String,String> persistedClaims) throws Exception {
        this(source,persistedClaims,false);
    }
    private WorkerClaimSnapshot(LegacyStoreOperations.ReferenceSnapshot source,
            Map<String,String> persistedClaims,boolean declarationWitnessOnly) throws Exception {
        this.declarationWitnessOnly=declarationWitnessOnly;
        this.source=Objects.requireNonNull(source);
        source.starts().stream()
                .filter(row -> row.sourceChunkX == row.originChunkX && row.sourceChunkZ == row.originChunkZ)
                .forEach(row -> canonicalStarts.putIfAbsent(row.key(), row));
        for(Map.Entry<String,String> claim:persistedClaims.entrySet()) {
            LegacyStoreOperations.RetainedStart canonical = canonicalStarts.get(claim.getKey());
            boolean matched = canonical != null && canonical.rowSha256.equals(claim.getValue());
            if(!matched)throw new IllegalArgumentException("claim is not bound to an immutable structure row");
            existing.put(claim.getKey(),claim.getValue());
        }
        Class<?> type=Class.forName("com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority$ClaimableStructureReferenceSnapshot");
        capability=(Mc263LocatedMapAuthority.StructureReferenceSnapshot)Proxy.newProxyInstance(
                type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->switch(method.getName()) {
                    case "receipt" -> receipt();
                    case "references" -> references((String)args[0],(Integer)args[1],(Integer)args[2]);
                    case "claimContaining" -> { claim((Integer)args[0],(Integer)args[1],(Integer)args[2],args[3]); yield proxy; }
                    case "claimLocated" -> { claimLocated((String)args[0],(Integer)args[1],(Integer)args[2]); yield null; }
                    case "toString" -> "WorkerClaimSnapshot["+receipt()+"]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    default -> throw new IllegalArgumentException("unsupported claim capability method");
                });
    }
    /** Generation seals a declaration witness; actual map-function claims happen only at loot open. */
    static Mc263LocatedMapAuthority.StructureReferenceSnapshot generationWitness(
            LegacyStoreOperations.ReferenceSnapshot source) throws Exception {
        try {Class.forName("com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority$ClaimableStructureReferenceSnapshot");}
        catch(ClassNotFoundException legacy){return source;}
        return new WorkerClaimSnapshot(source,Map.of(),true).capability();
    }
    Mc263LocatedMapAuthority.StructureReferenceSnapshot capability(){return capability;}
    Map<String,String> pending(){return Collections.unmodifiableMap(new LinkedHashMap<>(pending));}
    private int references(String id,int x,int z){
        String key=id+"\0"+x+"\0"+z;
        return Math.max(source.references(id,x,z),existing.containsKey(key)||pending.containsKey(key)?1:0);
    }
    private void claim(int x,int y,int z,Object acceptedObject){
        if(declarationWitnessOnly)return;
        if(!(acceptedObject instanceof Set<?> accepted))throw new IllegalArgumentException("claim destination set required");
        for(var row:source.starts()) {
            if(!accepted.contains(row.structureId)||!row.contains(x,y,z))continue;
            if(references(row.structureId,row.originChunkX,row.originChunkZ)==0) {
                var canonical=canonicalRow(row.structureId,row.originChunkX,row.originChunkZ);
                pending.put(canonical.key(),canonical.rowSha256);
            }
            // getStructureAt selects a containing start before canBeReferenced is checked.
            return;
        }
    }
    private void claimLocated(String structureId,int chunkX,int chunkZ) {
        if(declarationWitnessOnly)return;
        String key=structureId+"\0"+chunkX+"\0"+chunkZ;
        var row=canonicalRow(structureId,chunkX,chunkZ);
        if(references(structureId,chunkX,chunkZ)!=0)throw new IllegalStateException("located target is already referenced");
        pending.put(key,row.rowSha256);
    }
    private LegacyStoreOperations.RetainedStart canonicalRow(String structureId,int x,int z) {
        String key=structureId+"\0"+x+"\0"+z;
        LegacyStoreOperations.RetainedStart row = canonicalStarts.get(key);
        if (row == null) throw new NeedsStructure(x,z);
        return row;
    }
    static final class NeedsStructure extends RuntimeException {
        final int chunkX,chunkZ;
        NeedsStructure(int chunkX,int chunkZ){super("canonical target structure preparation required");this.chunkX=chunkX;this.chunkZ=chunkZ;}
    }
    private String receipt()throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(OutputStream.nullOutputStream(),digest));
        out.write("WEBCRAFT-STRUCTURE-CLAIM-SNAPSHOT-V1\0".getBytes(StandardCharsets.US_ASCII));
        out.writeUTF(source.receipt());
        var all=new TreeMap<>(existing);all.putAll(pending);out.writeInt(all.size());
        for(var entry:all.entrySet()){out.writeUTF(entry.getKey());out.writeUTF(entry.getValue());}
        out.flush();return HexFormat.of().formatHex(digest.digest());
    }
}
