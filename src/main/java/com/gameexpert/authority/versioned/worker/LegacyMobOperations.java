package com.gameexpert.authority.versioned.worker;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionEntityAuthority;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** The host selects an authenticated entity row; producer-specific payloads stay private. */
public final class LegacyMobOperations {
 private LegacyMobOperations() { }
 public static byte[] matches(DataInputStream in)throws IOException{
  int x=in.readInt(),z=in.readInt(),n=in.readInt();if(n<=0||n>64*1024*1024||n>in.available()-4)throw new IllegalArgumentException("invalid entity frame");
  var source=Mc263FinalChunkCodec.decode(in.readNBytes(n));int ordinal=in.readInt();
  if(in.available()!=0||source.chunkX()!=x||source.chunkZ()!=z||ordinal<0||ordinal>=source.sidecars().entities().size())throw new IllegalArgumentException("invalid source entity ordinal");
  var e=source.sidecars().entities().get(ordinal);
  boolean valid=isCanonicalSupportedMob(e.entityKey(),e.spawnReason(),e.x(),e.y(),e.z(),e.yaw(),e.pitch(),e.velocityX(),e.velocityY(),e.velocityZ(),e.lootTable(),e.lootSeed(),e.canonicalPayload());
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x57504731);out.writeByte(1);out.writeBoolean(valid);out.flush();return bytes.toByteArray();
 }
    private static boolean isCanonicalSupportedMob(String entityKey, String spawnReason,
            double x, double y, double z, float yaw, float pitch,
            double velocityX, double velocityY, double velocityZ,
            String lootTable, long lootSeed, byte[] canonicalPayloadBytes) {
        if (!switch (entityKey) {
            case "minecraft:witch", "minecraft:cat", "minecraft:villager",
                    "minecraft:zombie_villager", "minecraft:evoker",
                    "minecraft:vindicator", "minecraft:allay" -> true;
            default -> false;
        }) return false;
        byte[] expected = switch (entityKey) {
            case "minecraft:witch", "minecraft:cat" ->
                    "SHW263E1\u0001\u0001".getBytes(StandardCharsets.ISO_8859_1);
            case "minecraft:villager" ->
                    "IGL263E1\u0001".getBytes(StandardCharsets.ISO_8859_1);
            case "minecraft:zombie_villager" ->
                    "IGL263E1\u0002".getBytes(StandardCharsets.ISO_8859_1);
            case "minecraft:evoker", "minecraft:vindicator", "minecraft:allay" -> null;
            default -> throw new IllegalStateException("unreachable unsupported ENTS mob");
        };
        boolean canonicalPayload = expected == null
                ? Mc263WoodlandMansionEntityAuthority.matchesCanonicalPayload(entityKey,
                        new double[] {x, y, z}, new float[] {yaw, pitch},
                        new double[] {velocityX, velocityY, velocityZ}, canonicalPayloadBytes)
                : MessageDigest.isEqual(canonicalPayloadBytes, expected);
        return spawnReason.equals("minecraft:structure") && lootTable.isEmpty() && lootSeed == 0L
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch)
                && Double.isFinite(velocityX) && Double.isFinite(velocityY)
                && Double.isFinite(velocityZ) && canonicalPayload;
    }
}
