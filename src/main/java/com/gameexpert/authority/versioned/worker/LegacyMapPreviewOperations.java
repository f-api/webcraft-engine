package com.gameexpert.authority.versioned.worker;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
/** Render only an original authenticated target selected from a verified source declaration. */
public final class LegacyMapPreviewOperations {
 private LegacyMapPreviewOperations() { }
 public static byte[] render(DataInputStream in)throws Exception{
  int x=in.readInt(),z=in.readInt(),n=in.readInt();if(n<=0||n>64*1024*1024||n>in.available()-17)throw new IllegalArgumentException("invalid map source frame");
  var chunk=Mc263FinalChunkCodec.decode(in.readNBytes(n));int declaration=in.readInt(),mapOrdinal=in.readInt();long seed=in.readLong();boolean seaLevel=in.readBoolean();
  if(in.available()!=0||chunk.chunkX()!=x||chunk.chunkZ()!=z||declaration<0||declaration>=chunk.sidecars().containerLootDeclarations().size())throw new IllegalArgumentException("invalid map declaration selection");
  var context=chunk.sidecars().containerLootDeclarations().get(declaration).productionContext();
  if(!(context instanceof Mc263ContainerLootResolver.LocatedProductionContext located)||!Long.toString(seed).equals(context.worldIdentity())||mapOrdinal<0||mapOrdinal>=located.maps().size())throw new IllegalArgumentException("invalid map context binding");
  var target=new ArrayList<>(located.maps().values()).get(mapOrdinal);
  if(!(target instanceof Mc263LocatedMapAuthority.Found found))throw new IllegalArgumentException("map target was not found");
  found.requireAuthenticated();
  // Authentication of the original source declaration already binds this exact target. A fresh
  // renderer must still reproduce every sealed byte; no current reference snapshot is substituted
  // for the historical snapshot at generation time.
  byte[] original=Mc263LocatedMapAuthority.biomePreviewRenderer().render(seed,found.savedCenterX(),found.savedCenterZ(),found.binding().scale());
  if(!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(original),HexFormat.of().parseHex(found.previewSha256())))throw new IllegalStateException("sealed preview cannot be reproduced by selected producer");
  byte[] colors=seaLevel?WorkerSeaLevelMapPreview.render(seed,found.savedCenterX(),found.savedCenterZ(),found.binding().scale()):original;
  if(colors.length!=16384)throw new IllegalStateException("invalid map color count");
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x57504731);out.writeByte(1);out.writeInt(colors.length);out.write(colors);out.flush();return bytes.toByteArray();
 }
}
