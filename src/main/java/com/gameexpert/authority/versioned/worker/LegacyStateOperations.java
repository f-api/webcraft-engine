package com.gameexpert.authority.versioned.worker;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import java.io.*;
/** State facts come from the selected producer catalog, including runtime overlay states. */
public final class LegacyStateOperations {
 private LegacyStateOperations() { }
 public static byte[] decode(DataInputStream in)throws IOException{
  int id=in.readInt(),code=in.readInt();if(in.available()!=0)throw new IllegalArgumentException("trailing decode state input");return result(code == 0 && spawnerAlias(id) ? Mc263FeatureBlockState.defaultForId(id) : Mc263ExactStateCodec.decode(id,code));
 }
 public static byte[] exact(DataInputStream in)throws IOException{
  String exact=in.readUTF();if(in.available()!=0||exact.isEmpty()||exact.length()>4096)throw new IllegalArgumentException("invalid exact state input");return result(Mc263FeatureBlockState.fromExact(exact));
 }
 private static boolean spawnerAlias(int id) { return id == com.gameexpert.terrain.Blocks.SPAWNER_BASE + 1 || id == com.gameexpert.terrain.Blocks.SPAWNER_BASE + 2; }
 private static byte[] result(Mc263FeatureBlockState state)throws IOException{
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x57504731);out.writeByte(1);out.writeInt(state.blockId());out.writeInt(spawnerAlias(state.blockId()) ? 0 : Mc263ExactStateCodec.stateCode(state));out.writeUTF(state.exactState());out.writeBoolean(state.isLeavesTag());out.writeUTF(state.fluidTypeKey());out.flush();return bytes.toByteArray();
 }
}
