package com.gameexpert.engine.persistence.finalcarrier.reference;

import com.gameexpert.authority.versioned.*;
import com.gameexpert.engine.persistence.finalcarrier.loot.*;
import com.gameexpert.terrain.ChunkProductSource;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import com.gameexpert.api.persistence.WorldStore;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Terrain work precedes settlement; replay reconstructs the original immutable worker input. */
@Service
public final class LateCanonicalLootPreparationService {
    public static final class Retry extends RuntimeException {
        public Retry(String reason){super(reason);}
    }
    public static final class Prepared {
        private final long assignmentId;
        private final String definitionFingerprint;
        private final CanonicalStructureSnapshot snapshot;
        private final List<LateLootOutcome.Claim> beforeClaims;
        private final LateLootOutcome outcome;
        private Prepared(WorldCanonicalLootAssignment assignment,CanonicalStructureSnapshot snapshot,
                List<LateLootOutcome.Claim> beforeClaims,LateLootOutcome outcome){
            if(assignment.getId()==null)throw new IllegalStateException("persisted assignment required");
            this.assignmentId=assignment.getId();this.definitionFingerprint=assignment.getDefinitionFingerprint();
            this.snapshot=snapshot;this.beforeClaims=List.copyOf(beforeClaims);this.outcome=outcome;
        }
        public LateLootOutcome outcome(){return outcome;}
    }
    private static final class Input {
        final CanonicalStructureSnapshot snapshot;
        final List<LateLootOutcome.Claim> claims;
        Input(CanonicalStructureSnapshot snapshot,List<LateLootOutcome.Claim> claims){this.snapshot=snapshot;this.claims=List.copyOf(claims);}
    }
    private final WorldStore worlds;
    private final CanonicalWorldgenStore canonical;
    private final WorldCanonicalLootAssignmentRepository assignments;
    private final WorldStructureReferenceClaimRepository claims;
    private final WorldLootReferenceSnapshotRepository snapshots;
    private final TransactionTemplate readSnapshot;
    public LateCanonicalLootPreparationService(WorldStore worlds,CanonicalWorldgenStore canonical,
            WorldCanonicalLootAssignmentRepository assignments,WorldStructureReferenceClaimRepository claims,
            WorldLootReferenceSnapshotRepository snapshots,PlatformTransactionManager transactions){
        this.worlds=Objects.requireNonNull(worlds);this.canonical=Objects.requireNonNull(canonical);
        this.assignments=Objects.requireNonNull(assignments);this.claims=Objects.requireNonNull(claims);this.snapshots=Objects.requireNonNull(snapshots);
        readSnapshot=new TransactionTemplate(Objects.requireNonNull(transactions));readSnapshot.setReadOnly(true);
        readSnapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readSnapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public Prepared prepare(long worldId,int x,int y,int z,CanonicalLootContainerKind kind,
            boolean playerOverride,ChunkProductSource generation){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("late loot generation must precede database settlement");
        if(playerOverride)return null;
        var sourceProfile=Objects.requireNonNull(generation).generationProfile();
        var world=worlds.findById(worldId).orElseThrow(()->new IllegalStateException("loot world missing"));
        var profile=world.generationProfile();
        if(!profile.equals(sourceProfile))throw new IllegalStateException("loot generator belongs to another profile");
        var assignment=assignments.findByWorldIdAndPosXAndPosYAndPosZ(worldId,x,y,z).orElse(null);
        if(assignment==null||assignment.getStatus()==WorldCanonicalLootAssignment.Status.REJECTED||assignment.getContainerKind()!=kind)return null;
        if(assignment.getWorldSeed()!=world.getSeed())throw new IllegalStateException("loot world seed differs");
        NeutralFinalChunk source=source(assignment);
        assignment.verifyProducer(source);
        if(assignment.getLocatedProductionContext().targets().isEmpty())return null;
        if(assignment.getStatus()==WorldCanonicalLootAssignment.Status.RESOLVED){
            if(assignment.getLateOutcomePayload()==null)throw new IllegalStateException("new-profile map outcome lacks replay input");
            var saved=snapshots.findByWorldIdAndBaselineIdAndSnapshotIdentity(worldId,profile.getBaselineId(),assignment.getLateSnapshotIdentity())
                    .orElseThrow(()->new IllegalStateException("late loot replay membership missing"));
            var current=canonical.structureSnapshot(worldId,profile);
            var historical=LateLootReferenceCodec.restore(saved,current);
            var before=LateLootReferenceCodec.readClaims(saved.getClaimsPayload());
            var outcome=execute(source,assignment,historical,before);
            if(outcome.needsStructure()||!Arrays.equals(outcome.encoded(),assignment.getLateOutcomePayload()))
                throw new IllegalStateException("late loot did not replay its exact historical producer outcome");
            return new Prepared(assignment,historical,before,outcome);
        }
        if(!Objects.requireNonNull(generation).generationProfile().equals(profile))throw new IllegalStateException("loot preparation generator profile differs");
        var requested=new HashSet<Long>();
        for(int attempt=0;attempt<32;attempt++){
            Input input=Objects.requireNonNull(readSnapshot.execute(status->new Input(canonical.structureSnapshot(worldId,profile),currentClaims(worldId,profile))));
            var outcome=execute(source,assignment,input.snapshot,input.claims);
            if(!outcome.needsStructure())return new Prepared(assignment,input.snapshot,input.claims,outcome);
            int neededX=outcome.neededChunkX(),neededZ=outcome.neededChunkZ();
            long key=((long)neededX<<32)^(neededZ&0xffffffffL);
            if(!requested.add(key))throw new IllegalStateException("verified target chunk did not supply its required structure start");
            generation.generate(Math.toIntExact(assignment.getWorldSeed()),neededX,neededZ);
            if(canonical.find(worldId,neededX,neededZ)==null)throw new IllegalStateException("target generation did not commit to the same canonical store");
        }
        throw new Retry("late loot preparation changed repeatedly");
    }
    public void lockWorldJoiningTransaction(long worldId,Prepared prepared){
        requireTransaction();
        var world=worlds.findByIdForUpdate(worldId).orElseThrow(()->new IllegalStateException("loot world missing"));
        if(worldId!=prepared.snapshot.worldId()||!world.generationProfile().equals(prepared.snapshot.profile()))
            throw new IllegalStateException("prepared loot world/profile differs");
    }
    public void validateJoiningTransaction(WorldCanonicalLootAssignment assignment,Prepared prepared,
            NeutralFinalChunk source){
        requireTransaction();
        if(!Objects.equals(assignment.getId(),prepared.assignmentId)||!assignment.getDefinitionFingerprint().equals(prepared.definitionFingerprint))
            throw new Retry("loot assignment changed after preparation");
        assignment.verifyProducer(source);
        if(assignment.getStatus()==WorldCanonicalLootAssignment.Status.RESOLVED){
            if(!Arrays.equals(assignment.getLateOutcomePayload(),prepared.outcome.encoded()))throw new Retry("another loot outcome settled first");
        }else{
            var now=canonical.structureSnapshot(assignment.getWorldId(),prepared.snapshot.profile());
            String epoch=LateLootReferenceCodec.epoch(now.receipt(),currentClaims(assignment.getWorldId(),prepared.snapshot.profile()));
            if(!epoch.equals(prepared.outcome.referenceEpoch()))throw new Retry("structure references changed after preparation");
        }
        assignment.bindVerifiedLateOutcome(prepared.outcome);
    }
    public void stageJoiningTransaction(WorldCanonicalLootAssignment assignment,Prepared prepared){
        requireTransaction();
        var profile=prepared.snapshot.profile();long worldId=assignment.getWorldId();
        byte[] membership=LateLootReferenceCodec.membership(prepared.snapshot),before=LateLootReferenceCodec.claims(prepared.beforeClaims);
        String identity=prepared.outcome.referenceEpoch(),claimHash=LateLootReferenceCodec.sha256(before);
        var existing=snapshots.findByWorldIdAndBaselineIdAndSnapshotIdentity(worldId,profile.getBaselineId(),identity).orElse(null);
        if(existing==null)snapshots.save(new WorldLootReferenceSnapshot(worldId,profile.getBaselineId(),identity,prepared.snapshot.receipt(),claimHash,membership,before));
        else if(!existing.matchesEvidence(prepared.snapshot.receipt(),claimHash,membership,before))throw new IllegalStateException("immutable loot replay snapshot differs");
        for(var claim:prepared.outcome.claims()){
            var row=canonical.find(worldId,claim.originChunkX(),claim.originChunkZ());
            if(row==null||!row.commit().worldIdentity().equals(profile.getBaselineId())
                    ||!LateLootReferenceCodec.sha256(row.commit().structureCarrier()).equals(claim.structureRowSha256()))
                throw new IllegalStateException("claim no longer matches its verified canonical origin row");
            String fingerprint=LateLootReferenceCodec.sha256(row.commit().fingerprint());
            var settled=claims.findByWorldIdAndBaselineIdAndStructureIdAndOriginChunkXAndOriginChunkZ(worldId,profile.getBaselineId(),claim.structureId(),claim.originChunkX(),claim.originChunkZ()).orElse(null);
            if(settled==null)claims.save(new WorldStructureReferenceClaim(worldId,profile.getBaselineId(),claim.structureId(),claim.originChunkX(),claim.originChunkZ(),fingerprint,claim.structureRowSha256()));
            else if(!settled.matchesEvidence(fingerprint,claim.structureRowSha256()))throw new IllegalStateException("settled structure claim evidence differs");
        }
        assignment.stageLateOutcome(identity,prepared.outcome);
    }
    private List<LateLootOutcome.Claim> currentClaims(long worldId,WorldGenerationProfile profile){
        return claims.findAllByWorldIdAndBaselineId(worldId,profile.getBaselineId()).stream()
                .map(row->new LateLootOutcome.Claim(row.getStructureId(),row.getOriginChunkX(),row.getOriginChunkZ(),row.getStructureRowSha256())).toList();
    }
    private NeutralFinalChunk source(WorldCanonicalLootAssignment assignment){
        var source=canonical.find(assignment.getWorldId(),assignment.getChunkX(),assignment.getChunkZ());
        if(source==null)throw new IllegalStateException("loot producer source missing");return source.commit().semanticFinalChunk();
    }
    private LateLootOutcome execute(NeutralFinalChunk source,WorldCanonicalLootAssignment assignment,
            CanonicalStructureSnapshot snapshot,List<LateLootOutcome.Claim> before){
        return source.prepareLateLoot(assignment.getProductionContextPayload(),assignment.getWorldSeed(),assignment.getTableKey(),assignment.getRawSeed(),
                assignment.getPosX(),assignment.getPosY(),assignment.getPosZ(),assignment.getContainerKind().slots(),assignment.getInitialSeedLo(),assignment.getInitialSeedHi(),snapshot,before);
    }
    private static void requireTransaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("loot settlement transaction required");}
}
