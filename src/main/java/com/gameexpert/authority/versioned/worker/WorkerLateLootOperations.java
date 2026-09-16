package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;

/** New-profile loot execution; no database state or partial random result is published here. */
final class WorkerLateLootOperations {
    static byte[] execute(DataInputStream input)throws Exception {
        byte[] legacy=frame(input),snapshotBytes=frame(input);
        var snapshot=LegacyStoreOperations.readReferences(new DataInputStream(new ByteArrayInputStream(snapshotBytes)));
        int count=input.readInt();if(count<0)throw new IllegalArgumentException("invalid claim count");
        var persisted=new LinkedHashMap<String,String>();
        for(int i=0;i<count;i++) {
            String structure=input.readUTF();int x=input.readInt(),z=input.readInt();String sha=input.readUTF();
            if(!structure.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!sha.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid claim fields");
            if(persisted.put(structure+"\0"+x+"\0"+z,sha)!=null)throw new IllegalArgumentException("duplicate claim key");
        }
        if(input.available()!=0)throw new IllegalArgumentException("trailing late loot request");
        return execute(legacy, snapshot, persisted);
    }
    static byte[] execute(byte[] legacy, LegacyStoreOperations.ReferenceSnapshot snapshot,
            Map<String,String> persisted) throws Exception {
        var claims=new WorkerClaimSnapshot(snapshot,persisted);
        String epoch=claims.capability().receipt();
        var runner=new Runner(claims);
        try {
            byte[] storedFrame=WorkerLootOperations.execute(new DataInputStream(new ByteArrayInputStream(legacy)),true,runner);
            var storedIn=new DataInputStream(new ByteArrayInputStream(storedFrame));
            if(storedIn.readInt()!=0x57504731||storedIn.readUnsignedByte()!=1)throw new IllegalStateException("invalid private loot result");
            byte[] stored=frame(storedIn);if(storedIn.available()!=0)throw new IllegalStateException("trailing private loot result");
            var contextBytes=new ByteArrayOutputStream();var contextOut=new DataOutputStream(contextBytes);
            LegacySidecarProjectionWriter.context(contextOut,Objects.requireNonNull(runner.resolved,"resolved context"));contextOut.flush();
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);header(out);out.writeByte(1);
            out.writeUTF(originalContextSha(legacy));out.writeUTF(epoch);write(out,stored);write(out,contextBytes.toByteArray());
            out.writeInt(runner.previews.size());
            for(var entry:runner.previews.entrySet()){out.writeUTF(entry.getKey());write(out,entry.getValue());}
            out.writeInt(claims.pending().size());for(var entry:claims.pending().entrySet()){
                String[] key=entry.getKey().split("\0",-1);out.writeUTF(key[0]);out.writeInt(Integer.parseInt(key[1]));out.writeInt(Integer.parseInt(key[2]));out.writeUTF(entry.getValue());
            }
            out.flush();var digest=MessageDigest.getInstance("SHA-256");digest.update("WEBCRAFT-LATE-LOOT-OUTCOME-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.writeUTF(HexFormat.of().formatHex(digest.digest(bytes.toByteArray())));out.flush();return bytes.toByteArray();
        } catch(WorkerClaimSnapshot.NeedsStructure needs) {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);header(out);out.writeByte(0);out.writeInt(needs.chunkX);out.writeInt(needs.chunkZ);out.flush();return bytes.toByteArray();
        }
    }
    private static final class Runner implements WorkerLootOperations.ResolutionRunner {
        private final WorkerClaimSnapshot claims;
        private Mc263ContainerLootResolver.LocatedProductionContext resolved;
        private final Map<String,byte[]> previews=new LinkedHashMap<>();
        Runner(WorkerClaimSnapshot claims){this.claims=claims;}
        @Override public Mc263ContainerLootResolver.Resolution resolve(long seed,String table,long rawSeed,
                int x,int y,int z,int slots,Mc263ContainerLootResolver.XoroshiroState initial,
                Mc263ContainerLootResolver.LocatedProductionContext original)throws Exception {
            Class<?> callbackType=Class.forName("com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver$MapTargetResolver");
            var rendered=new HashMap<String,byte[][]>();
            var delegate=Mc263LocatedMapAuthority.biomePreviewRenderer();
            var previewRenderer=new Mc263LocatedMapAuthority.PreviewRenderer(){
                @Override public String sourceReceipt(){return delegate.sourceReceipt();}
                private String key(int cx,int cz,int scale){return cx+":"+cz+":"+scale;}
                @Override public byte[] render(long worldSeed,int cx,int cz,int scale){
                    byte[] old=delegate.render(worldSeed,cx,cz,scale);
                    rendered.put(key(cx,cz,scale),new byte[][]{old.clone(),WorkerSeaLevelMapPreview.render(worldSeed,cx,cz,scale)});return old;
                }
                @Override public byte[] render(com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess world,int cx,int cz,int scale){
                    byte[] old=delegate.render(world,cx,cz,scale);
                    rendered.put(key(cx,cz,scale),new byte[][]{old.clone(),WorkerSeaLevelMapPreview.render(world,cx,cz,scale)});return old;
                }
            };
            var authority=Mc263LocatedMapAuthority.pinned(previewRenderer,requested->{if(requested!=seed)throw new IllegalArgumentException("foreign loot seed");return claims.capability();});
            var session=authority.openSession(seed);var targets=new LinkedHashMap<>(original.maps());
            Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                if(!method.getName().equals("locate"))throw new IllegalArgumentException("unexpected map callback");
                String destination=(String)args[0];var target=session.locate(original.sourceIdentity(),table,x,y,z,destination);
                if(target instanceof Mc263LocatedMapAuthority.Found found){
                    byte[][] colors=rendered.get(found.savedCenterX()+":"+found.savedCenterZ()+":"+found.binding().scale());
                    if(colors==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(colors[0])).equals(found.previewSha256()))throw new IllegalStateException("located preview witness is missing");
                    previews.put(found.targetReceipt(),colors[1].clone());
                }
                targets.put(destination,target);return target;
            });
            Method method=Mc263ContainerLootResolver.class.getMethod("resolveLocatedAtOpen",long.class,String.class,long.class,int.class,int.class,int.class,int.class,
                    Mc263ContainerLootResolver.XoroshiroState.class,Mc263ContainerLootResolver.LocatedProductionContext.class,callbackType);
            Mc263ContainerLootResolver.Resolution result;
            try {result=(Mc263ContainerLootResolver.Resolution)method.invoke(null,seed,table,rawSeed,x,y,z,slots,initial,original,callback);}
            catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception cause)throw cause;throw failure;}
            String receipt=Mc263ContainerLootResolver.targetProductionContextReceipt(original.biomeKey(),original.worldIdentity(),original.sourceIdentity(),table,x,y,z,targets);
            resolved=Mc263ContainerLootResolver.LocatedProductionContext.authenticated(original.biomeKey(),targets,original.worldIdentity(),original.sourceIdentity(),table,x,y,z,receipt);
            return result;
        }
    }
    private static String originalContextSha(byte[] legacy)throws Exception{
        var in=new DataInputStream(new ByteArrayInputStream(legacy));in.readInt();in.readInt();frame(in);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(frame(in)));
    }
    private static byte[] frame(DataInputStream in)throws IOException{int size=in.readInt();if(size<=0||size>64*1024*1024||size>in.available())throw new IllegalArgumentException("invalid late loot frame");return in.readNBytes(size);}
    private static void write(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static void header(DataOutputStream out)throws IOException{out.writeInt(0x57504731);out.writeByte(1);}
}
