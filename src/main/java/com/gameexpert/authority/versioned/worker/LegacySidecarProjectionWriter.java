package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import java.io.DataOutputStream;
import java.io.IOException;

/** Runs only in the profile's private class loader, after its own carrier codec accepted bytes. */
public final class LegacySidecarProjectionWriter {
    private LegacySidecarProjectionWriter() { }
    public static void write(DataOutputStream out, Mc263FinalChunkSidecars s) throws IOException {
        out.writeInt(s.blockTicks().size());
        for(var v:s.blockTicks()) { out.writeInt(v.packed()); out.writeInt(v.blockId()); out.writeUTF(v.key()); out.writeInt(v.delay()); out.writeInt(v.priority().value()); out.writeLong(v.subTickOrder()); }
        out.writeInt(s.fluidTicks().size());
        for(var v:s.fluidTicks()) { out.writeInt(v.packed()); out.writeUTF(v.key()); out.writeInt(v.delay()); out.writeInt(v.priority().value()); out.writeLong(v.subTickOrder()); }
        out.writeInt(s.loot().size());
        for(var v:s.loot()) { out.writeInt(v.packed()); out.writeUTF(v.facing()); out.writeUTF(v.table()); out.writeLong(v.seed()); }
        out.writeInt(s.spawners().size());
        for(var v:s.spawners()) { out.writeInt(v.packed()); out.writeUTF(v.entityType()); }
        out.writeInt(s.owners().size());
        for(var v:s.owners()) { out.writeInt(v.packed()); out.writeLong(v.owner()); }
        out.writeInt(s.archaeology().size());
        for(var v:s.archaeology()) { out.writeInt(v.packed()); out.writeUTF(v.table()); out.writeLong(v.seed()); }
        out.writeInt(s.bees().size());
        for(var v:s.bees()) { out.writeInt(v.packed()); out.writeInt(v.ticksInHive().size()); for(int ticks:v.ticksInHive()) out.writeInt(ticks); }
        out.writeInt(s.blockEntities().size());
        for(var v:s.blockEntities()) { out.writeInt(v.packed()); out.writeUTF(v.blockIdentity()); out.writeUTF(v.entityType()); bytes(out,v.canonicalNbt()); }
        out.writeInt(s.entities().size());
        for(var v:s.entities()) { out.writeUTF(v.entityKey()); out.writeUTF(v.spawnReason()); out.writeDouble(v.x()); out.writeDouble(v.y()); out.writeDouble(v.z()); out.writeFloat(v.yaw()); out.writeFloat(v.pitch()); out.writeDouble(v.velocityX()); out.writeDouble(v.velocityY()); out.writeDouble(v.velocityZ()); out.writeUTF(v.lootTable()); out.writeLong(v.lootSeed()); bytes(out,v.canonicalPayload()); }
        out.writeInt(s.containerLootDeclarations().size());
        for(var v:s.containerLootDeclarations()) { out.writeInt(v.ordinal()); out.writeByte(v.sourceSection()==Mc263FinalChunkSidecars.ContainerLootSourceSection.LOOT?0:1); out.writeInt(v.sourceSectionOrdinal()); out.writeInt(v.containerSize()); context(out,v.productionContext()); out.writeUTF(v.producerSourceSha256()); out.writeUTF(v.sourceDeclarationSha256()); }
    }
    private static void bytes(DataOutputStream out,byte[] bytes) throws IOException { out.writeInt(bytes.length); out.write(bytes); }
    static void context(DataOutputStream out, Mc263ContainerLootResolver.LootProductionContext c) throws IOException {
        // The selected codec has already checked context and declaration authentication. Preserve all facts.
        out.writeUTF(c.biomeKey()); out.writeUTF(c.worldIdentity()); out.writeUTF(c.sourceIdentity()); out.writeUTF(c.tableIdentity());
        out.writeInt(c.originX()); out.writeInt(c.originY()); out.writeInt(c.originZ()); out.writeUTF(c.catalogReceipt());
        if(c instanceof Mc263ContainerLootResolver.LocatedProductionContext located) {
            out.writeByte(1); out.writeInt(located.maps().size());
            for(var entry:located.maps().entrySet()) {
                out.writeUTF(entry.getKey()); var target=entry.getValue(); var b=target.binding();
                out.writeUTF(b.destination()); out.writeUTF(b.destinationTag()); out.writeUTF(b.structureSet());
                out.writeInt(b.acceptedMembers().size()); for(String member:b.acceptedMembers()) out.writeUTF(member);
                out.writeUTF(b.worldIdentity()); out.writeUTF(b.sourceIdentity()); out.writeUTF(b.tableIdentity());
                out.writeInt(b.originX()); out.writeInt(b.originY()); out.writeInt(b.originZ()); out.writeInt(b.scale()); out.writeInt(b.searchRadius());
                out.writeBoolean(b.skipExistingChunks()); out.writeUTF(b.locatorSourceReceipt()); out.writeUTF(b.referenceSnapshotReceipt());
                out.writeUTF(target.targetReceipt());
                if(target instanceof Mc263LocatedMapAuthority.Found f) { out.writeBoolean(true); out.writeInt(f.targetX()); out.writeInt(f.targetZ()); out.writeInt(f.savedCenterX()); out.writeInt(f.savedCenterZ()); out.writeUTF(f.previewSha256()); }
                else if(target instanceof Mc263LocatedMapAuthority.NotFound) out.writeBoolean(false);
                else throw new IOException("unknown located target variant");
            }
        } else if(c instanceof Mc263ContainerLootResolver.ProductionContext legacy) {
            out.writeByte(0); out.writeInt(legacy.maps().size());
            for(var entry:legacy.maps().entrySet()) { out.writeUTF(entry.getKey()); var m=entry.getValue();
                out.writeUTF(m.destination()); out.writeUTF(m.worldIdentity()); out.writeUTF(m.sourceIdentity()); out.writeUTF(m.tableIdentity());
                out.writeInt(m.mapId()); out.writeInt(m.centerX()); out.writeInt(m.centerZ()); out.writeInt(m.originX()); out.writeInt(m.originY()); out.writeInt(m.originZ()); out.writeInt(m.scale()); out.writeUTF(m.resolverCatalogReceipt()); }
        } else throw new IOException("unknown production context variant");
    }
}
