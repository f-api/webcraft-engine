package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ws.dto.WsMessages.BannerPatternLayer;
import com.gameexpert.ws.dto.WsMessages.BookComponent;
import com.gameexpert.ws.dto.WsMessages.CraftingStack;
import com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteEvidence;
import com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt;
import com.gameexpert.ws.dto.WsMessages.FinalSceneH12fPrerequisiteEvidence;
import com.gameexpert.ws.dto.WsMessages.FinalSceneH12gPrerequisiteEvidence;
import com.gameexpert.ws.dto.WsMessages.FinalSceneNaturalEntityBinding;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntityTarget;
import com.gameexpert.ws.dto.WsMessages.InventorySlot;
import com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Owner-thread evidence ledger for generated-entity final-scene prerequisites.
 *
 * <p>The natural binding is the only authority source. Later calls carry observation stamps only
 * to fence actual Runtime/persistence callbacks against that source; they never replace binding
 * fields. Any identity or generation drift burns the ledger permanently.</p>
 */
public final class GeneratedFinalSceneEvidenceTracker {
    private static final String ARMOR_STAND = "ARMOR_STAND";
    private static final String CHEST_MINECART = "CHEST_MINECART";

    /** Exact identity attached by Runtime to one observed/committed callback. */
    public record EvidenceStamp(long worldId, long entityId, long revision,
            long connectionGeneration, long evidenceGeneration) {
        public EvidenceStamp {
            if (worldId <= 0L || entityId <= 0L || revision < 0L
                    || connectionGeneration <= 0L || evidenceGeneration <= 0L) {
                throw new IllegalArgumentException("generated evidence stamp is invalid");
            }
        }
    }

    /** Non-authority receipt header; the scenario is derived from the authenticated target. */
    public record ReceiptHeader(String fixtureChecksum, String authority, String world,
            String nickname, String actionNonce, long receiptRevision, int raidRoleCount) {}

    private final Thread owner;
    private final FinalSceneNaturalEntityBinding source;
    private final String kind;
    private long currentRevision;
    private long connectionGeneration;
    private long evidenceGeneration;
    private boolean visible;
    private boolean interaction;
    private boolean semanticMutation;
    private boolean terminal;
    private boolean cargoOpen;
    private boolean cargoMutation;
    private boolean cargoClosed;
    private boolean unloadReload;
    private boolean reconnect;
    private boolean invalidated;
    private MinecartCargoTarget cargoTarget;
    private long cargoRevision = -1L;
    private List<InventorySlot> persistedSlots;
    private CraftingStack persistedCursor;
    private FinalSceneGeneratedPrerequisiteReceipt frozen;

    private GeneratedFinalSceneEvidenceTracker(FinalSceneNaturalEntityBinding binding) {
        owner = Thread.currentThread();
        source = Objects.requireNonNull(binding, "authenticated natural binding");
        if (binding.getSchema() != 2 || !"LIVE".equals(binding.getLifecycle())
                || binding.getTarget() == null
                || !(ARMOR_STAND.equals(binding.getTarget().getKind())
                        || CHEST_MINECART.equals(binding.getTarget().getKind()))) {
            throw new IllegalArgumentException("authenticated live generated binding is required");
        }
        kind = binding.getTarget().getKind();
        currentRevision = binding.getRevision();
        connectionGeneration = binding.getConnectionGeneration();
        evidenceGeneration = binding.getEvidenceGeneration();
    }

    public static GeneratedFinalSceneEvidenceTracker begin(
            FinalSceneNaturalEntityBinding authenticatedBinding) {
        return new GeneratedFinalSceneEvidenceTracker(authenticatedBinding);
    }

    public void recordVisible(EvidenceStamp stamp) {
        requireOpen();
        acceptSameRevision(stamp);
        visible = true;
    }

    public void recordInteractionCommitted(EvidenceStamp stamp) {
        requireOpen();
        requireFact(visible, "visible spawn evidence is missing");
        acceptSameRevision(stamp);
        interaction = true;
    }

    public void recordH12fSemanticMutationCommitted(EvidenceStamp committed) {
        requireKind(ARMOR_STAND);
        requireFact(interaction, "interaction evidence is missing");
        acceptNextRevision(committed);
        semanticMutation = true;
    }

    public void recordH12fTerminalCommitted(EvidenceStamp committed) {
        requireKind(ARMOR_STAND);
        requireFact(semanticMutation, "semantic mutation evidence is missing");
        acceptNextRevision(committed);
        terminal = true;
    }

    public void recordH12gCargoOpened(EvidenceStamp stamp, MinecartCargoTarget target) {
        requireKind(CHEST_MINECART);
        requireFact(interaction, "interaction evidence is missing");
        acceptSameRevision(stamp);
        requireCargoTarget(target, false);
        cargoOpen = true;
    }

    public void recordH12gCargoMutationCommitted(EvidenceStamp committed,
            MinecartCargoTarget target, List<InventorySlot> canonicalSlots,
            CraftingStack canonicalCursor) {
        requireKind(CHEST_MINECART);
        requireFact(cargoOpen, "cargo-open evidence is missing");
        requireCargoTarget(target, true);
        List<InventorySlot> copiedSlots = copyCanonicalSlots(canonicalSlots);
        CraftingStack copiedCursor = copyCursor(Objects.requireNonNull(
                canonicalCursor, "persisted cargo cursor"));
        acceptNextRevision(committed);
        persistedSlots = copiedSlots;
        persistedCursor = copiedCursor;
        cargoRevision = committed.revision();
        cargoMutation = true;
    }

    public void recordH12gCargoClosed(EvidenceStamp stamp, MinecartCargoTarget target) {
        requireKind(CHEST_MINECART);
        requireFact(cargoMutation, "cargo-mutation evidence is missing");
        requireCargoTarget(target, true);
        acceptSameRevision(stamp);
        cargoClosed = true;
    }

    public void recordUnloadReloadPersisted(EvidenceStamp stamp) {
        requireOpen();
        requireFact(ARMOR_STAND.equals(kind) ? terminal : cargoClosed,
                "terminal or cargo-close evidence is missing");
        acceptSameRevision(stamp);
        unloadReload = true;
    }

    /** The sole legal connection-generation transition. */
    public void recordReconnectPersisted(EvidenceStamp stamp) {
        requireOpen();
        requireFact(unloadReload, "unload/reload evidence is missing");
        verifyIdentity(stamp);
        if (stamp.revision() != currentRevision
                || stamp.connectionGeneration() <= connectionGeneration
                || stamp.evidenceGeneration() <= evidenceGeneration) {
            invalidate("reconnect generation or revision drift");
        }
        connectionGeneration = stamp.connectionGeneration();
        evidenceGeneration = stamp.evidenceGeneration();
        reconnect = true;
    }

    /** Returns empty until complete; the first successful result permanently freezes the ledger. */
    public Optional<FinalSceneGeneratedPrerequisiteReceipt> freezeReceipt(
            ReceiptHeader header) {
        requireThread();
        if (invalidated) return Optional.empty();
        if (frozen != null) return Optional.of(frozen);
        if (!complete()) return Optional.empty();
        Objects.requireNonNull(header, "generated receipt header");
        FinalSceneGeneratedPrerequisiteEvidence evidence;
        String scenario;
        if (ARMOR_STAND.equals(kind)) {
            scenario = "H12f";
            evidence = new FinalSceneH12fPrerequisiteEvidence();
        } else {
            scenario = "H12g";
            evidence = new FinalSceneH12gPrerequisiteEvidence(copyCargoTarget(cargoTarget),
                    source.getRevision(), cargoRevision, copyCanonicalSlots(persistedSlots),
                    copyCursor(persistedCursor));
        }
        frozen = new FinalSceneGeneratedPrerequisiteReceipt(header.fixtureChecksum(),
                header.authority(), header.world(), header.nickname(), scenario,
                header.actionNonce(), header.receiptRevision(), header.raidRoleCount(),
                frozenBinding(), evidence);
        return Optional.of(frozen);
    }

    public boolean invalidated() {
        requireThread();
        return invalidated;
    }

    private boolean complete() {
        boolean common = visible && interaction && unloadReload && reconnect;
        return common && (ARMOR_STAND.equals(kind)
                ? semanticMutation && terminal
                : cargoOpen && cargoMutation && cargoClosed && persistedSlots != null
                        && persistedCursor != null && cargoRevision >= source.getRevision());
    }

    private void acceptSameRevision(EvidenceStamp stamp) {
        verifyIdentity(stamp);
        if (stamp.revision() != currentRevision
                || stamp.connectionGeneration() != connectionGeneration
                || stamp.evidenceGeneration() <= evidenceGeneration) {
            invalidate("generated evidence revision or generation drift");
        }
        evidenceGeneration = stamp.evidenceGeneration();
    }

    private void acceptNextRevision(EvidenceStamp stamp) {
        verifyIdentity(stamp);
        if (currentRevision == Long.MAX_VALUE
                || stamp.revision() != currentRevision + 1L
                || stamp.connectionGeneration() != connectionGeneration
                || stamp.evidenceGeneration() <= evidenceGeneration) {
            invalidate("generated committed revision or generation drift");
        }
        currentRevision = stamp.revision();
        evidenceGeneration = stamp.evidenceGeneration();
    }

    private void verifyIdentity(EvidenceStamp stamp) {
        Objects.requireNonNull(stamp, "generated evidence stamp");
        if (stamp.worldId() != source.getWorldId()
                || stamp.entityId() != source.getTarget().getEntityId()) {
            invalidate("generated evidence world or target drift");
        }
    }

    private void requireCargoTarget(MinecartCargoTarget target, boolean existing) {
        Objects.requireNonNull(target, "minecart cargo target");
        if (target.getEntityId() != source.getTarget().getEntityId()
                || target.getSchema() != 1 || !"minecartCargo".equals(target.getKind())
                || existing && (cargoTarget == null
                        || target.getSessionId() != cargoTarget.getSessionId())) {
            invalidate("minecart cargo target drift");
        }
        if (!existing) {
            if (cargoTarget != null && cargoTarget.getSessionId() != target.getSessionId()) {
                invalidate("minecart cargo session drift");
            }
            cargoTarget = copyCargoTarget(target);
        }
    }

    private void requireKind(String expected) {
        requireOpen();
        if (!expected.equals(kind)) invalidate("generated evidence kind drift");
    }

    private void requireOpen() {
        requireThread();
        if (invalidated || frozen != null) {
            throw new IllegalStateException("generated evidence ledger is closed");
        }
    }

    private void requireThread() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("generated evidence ledger crossed owner thread");
        }
    }

    private static void requireFact(boolean present, String message) {
        if (!present) throw new IllegalStateException(message);
    }

    private void invalidate(String message) {
        invalidated = true;
        throw new IllegalStateException(message);
    }

    private FinalSceneNaturalEntityBinding frozenBinding() {
        GeneratedEntityTarget target = new GeneratedEntityTarget(source.getTarget().getSchema(),
                source.getTarget().getKind(), source.getTarget().getEntityId());
        return new FinalSceneNaturalEntityBinding(target, source.getWorldId(),
                source.getWorldSeed(), source.getWorldEpoch(), source.getInstallationIdentity(),
                source.getAuthoritativeId(), source.getEncounterOrdinal(),
                source.getActivationFingerprint(), source.getProvenanceFingerprint(),
                source.getStateProvenanceFingerprint(), source.getRevision(),
                source.getOriginChunkX(), source.getOriginChunkZ(), source.getOriginX(),
                source.getOriginY(), source.getOriginZ(), connectionGeneration,
                evidenceGeneration);
    }

    private static MinecartCargoTarget copyCargoTarget(MinecartCargoTarget target) {
        return new MinecartCargoTarget(target.getEntityId(), target.getSessionId());
    }

    private static List<InventorySlot> copyCanonicalSlots(List<InventorySlot> slots) {
        if (slots == null || slots.size() != 27) {
            throw new IllegalArgumentException("persisted cargo requires exactly 27 slots");
        }
        java.util.ArrayList<InventorySlot> copy = new java.util.ArrayList<>(27);
        for (int index = 0; index < 27; index++) {
            InventorySlot slot = Objects.requireNonNull(slots.get(index), "persisted cargo slot");
            if (slot.getSlot() != index || slot.getItemType() < 0 || slot.getCount() < 0
                    || (slot.getItemType() == PlayerInventory.EMPTY) != (slot.getCount() == 0)
                    || PlayerInventory.isDurable(slot.getItemType())
                            != (slot.getDurability() != null)) {
                throw new IllegalArgumentException("persisted cargo slot is not canonical");
            }
            copy.add(copySlot(slot));
        }
        return List.copyOf(copy);
    }

    private static InventorySlot copySlot(InventorySlot slot) {
        return new InventorySlot(slot.getSlot(), slot.getItemType(), slot.getCount(),
                slot.getDurability(), slot.getWideEnchantments(), slot.getMapId(),
                slot.getShulkerId(), slot.getBucketMobData(), slot.getCustomName(),
                copyPatterns(slot.getBannerPatterns()), copyBook(slot.getBook()),
                slot.getAnvilUseCount(), slot.getLeatherColor(),
                slot.getSuspiciousStewEffect(), slot.getSuspiciousStewDurationMcTicks(),
                slot.getOminousBottleAmplifier(), slot.getPotionContents(), slot.getTrim(), slot.getPotDecorations());
    }

    private static CraftingStack copyCursor(CraftingStack cursor) {
        Objects.requireNonNull(cursor, "persisted cargo cursor");
        if (cursor.getItemType() < 0 || cursor.getCount() < 0
                || (cursor.getItemType() == PlayerInventory.EMPTY) != (cursor.getCount() == 0)
                || PlayerInventory.isDurable(cursor.getItemType())
                        != (cursor.getDurability() != null)) {
            throw new IllegalArgumentException("persisted cargo cursor is not canonical");
        }
        return new CraftingStack(cursor.getItemType(), cursor.getCount(), cursor.getDurability(),
                cursor.getWideEnchantments(), cursor.getMapId(), cursor.getShulkerId(),
                cursor.getBucketMobData(), cursor.getCustomName(),
                copyPatterns(cursor.getBannerPatterns()), copyBook(cursor.getBook()),
                cursor.getAnvilUseCount(), cursor.getLeatherColor(),
                cursor.getSuspiciousStewEffect(), cursor.getSuspiciousStewDurationMcTicks(),
                cursor.getOminousBottleAmplifier(), cursor.getPotionContents(), cursor.getTrim(), cursor.getPotDecorations());
    }

    private static List<BannerPatternLayer> copyPatterns(List<BannerPatternLayer> patterns) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        return patterns.stream().map(pattern -> new BannerPatternLayer(
                pattern.getPattern(), pattern.getColor())).toList();
    }

    private static BookComponent copyBook(BookComponent book) {
        return book == null ? null : new BookComponent(book.getTitle(), book.getAuthor(),
                book.getPages() == null ? List.of() : List.copyOf(book.getPages()));
    }
}
