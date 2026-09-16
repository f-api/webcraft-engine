package com.gameexpert.container.persistence;

import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator;
import com.gameexpert.engine.GeneratedStructureEntityMutationCoordinator.GeneratedEntityMutationOperation;
import com.gameexpert.engine.CombatRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityStateRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntity;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityRepository;
import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.mob.service.MobPersistenceService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import com.gameexpert.authority.versioned.NeutralFinalChunk.StructureEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 컨테이너 스냅샷과 안정 정산 영수증을 호출자의 플레이어 저장 트랜잭션에 합류시킨다. */
@Service
public class ContainerSettlementPersistenceService {

    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    public enum ArmorStandEquipmentOutcome { COMMITTED, STALE }

    /** Complete two-authority command; no live mutable inventory crosses the transaction seam. */
    public record ArmorStandEquipmentSettlementCommand(
            long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot sourcePlayer,
            PlayerInventoryMutationSnapshot committedPlayer,
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot sourceEntity,
            String committedEquipmentItem,
            PlayerInventory.Hand hand,
            boolean crouching,
            StructureEntityAggregate aggregate) {
        public ArmorStandEquipmentSettlementCommand {
            Objects.requireNonNull(sourcePlayer, "source player inventory");
            Objects.requireNonNull(committedPlayer, "committed player inventory");
            Objects.requireNonNull(sourceEntity, "source Armor Stand state");
            Objects.requireNonNull(committedEquipmentItem,
                    "committed Armor Stand equipment item");
            Objects.requireNonNull(hand, "Armor Stand settlement hand");
            Objects.requireNonNull(aggregate, "Armor Stand installed aggregate");
        }
    }

    public record ArmorStandEquipmentSettlementResult(
            ArmorStandEquipmentOutcome outcome,
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot currentEntity,
            PlayerInventoryMutationSnapshot currentPlayer) {
        public ArmorStandEquipmentSettlementResult {
            Objects.requireNonNull(outcome, "Armor Stand equipment outcome");
            Objects.requireNonNull(currentEntity, "Armor Stand equipment current entity");
            Objects.requireNonNull(currentPlayer, "Armor Stand equipment current player");
        }
    }

    private final WorldContainerSettlementRepository settlements;
    private final ChestPersistenceService chests;
    private final FurnacePersistenceService furnaces;
    private final BrewingPersistenceService brewing;
    private final CampfirePersistenceService campfires;
    private final MobPersistenceService mobs;
    private final WorldGeneratedStructureEntityStateRepository generatedEntityStates;
    private final GeneratedStructureEntityMutationCoordinator generatedEntityMutations;
    private final WorldStructureEntityRepository generatedEntitySources;
    private final PlayerWorldStateService playerStates;

    @org.springframework.beans.factory.annotation.Autowired
    public ContainerSettlementPersistenceService(WorldContainerSettlementRepository settlements,
            ChestPersistenceService chests, FurnacePersistenceService furnaces,
            BrewingPersistenceService brewing, CampfirePersistenceService campfires,
            org.springframework.beans.factory.ObjectProvider<MobPersistenceService> mobs,
            org.springframework.beans.factory.ObjectProvider<WorldGeneratedStructureEntityStateRepository>
                    generatedEntityStates,
            org.springframework.beans.factory.ObjectProvider<GeneratedStructureEntityMutationCoordinator>
                    generatedEntityMutations,
            org.springframework.beans.factory.ObjectProvider<WorldStructureEntityRepository>
                    generatedEntitySources,
            org.springframework.beans.factory.ObjectProvider<PlayerWorldStateService>
                    playerStates) {
        this.settlements = settlements;
        this.chests = chests;
        this.furnaces = furnaces;
        this.brewing = brewing;
        this.campfires = campfires;
        this.mobs = mobs.getIfAvailable();
        this.generatedEntityStates = generatedEntityStates.getIfAvailable();
        this.generatedEntityMutations = generatedEntityMutations.getIfAvailable();
        this.generatedEntitySources = generatedEntitySources.getIfAvailable();
        this.playerStates = playerStates.getIfAvailable();
    }

    public ContainerSettlementPersistenceService(WorldContainerSettlementRepository settlements,
            ChestPersistenceService chests, FurnacePersistenceService furnaces,
            BrewingPersistenceService brewing, CampfirePersistenceService campfires) {
        this.settlements = settlements;
        this.chests = chests;
        this.furnaces = furnaces;
        this.brewing = brewing;
        this.campfires = campfires;
        this.mobs = null;
        this.generatedEntityStates = null;
        this.generatedEntityMutations = null;
        this.generatedEntitySources = null;
        this.playerStates = null;
    }

    /** 생성 상태 도입 전 집중 slice 테스트의 기존 수동 구성을 유지한다. */
    public ContainerSettlementPersistenceService(WorldContainerSettlementRepository settlements,
            ChestPersistenceService chests, FurnacePersistenceService furnaces,
            BrewingPersistenceService brewing, CampfirePersistenceService campfires,
            org.springframework.beans.factory.ObjectProvider<MobPersistenceService> mobs) {
        this.settlements = settlements;
        this.chests = chests;
        this.furnaces = furnaces;
        this.brewing = brewing;
        this.campfires = campfires;
        this.mobs = mobs.getIfAvailable();
        this.generatedEntityStates = null;
        this.generatedEntityMutations = null;
        this.generatedEntitySources = null;
        this.playerStates = null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome settleJoiningTransaction(Long worldId, ContainerSettlementCommand command) {
        String commandHash = commandHash(command);
        var existing = settlements.findByWorldIdAndSettlementId(worldId, command.getSettlementId());
        if (existing.isPresent()) return idempotentOrCollision(existing.get(), commandHash);

        boolean changed = switch (command.getTarget()) {
            case InventoryMutationTarget.Chests target ->
                    chests.replaceExactSnapshotsAtExpectedRevisionsJoiningTransaction(
                            worldId, target.halves(), command.getExpectedRevisions());
            case InventoryMutationTarget.Furnace target ->
                    furnaces.replaceExactSnapshotAtExpectedRevisionJoiningTransaction(
                            worldId, target, target.xpMilli(),
                            command.getExpectedRevisions().getFirst());
            case InventoryMutationTarget.Brewing target ->
                    brewing.replaceExactSnapshotAtExpectedRevisionJoiningTransaction(
                            worldId, target, command.getExpectedRevisions().getFirst());
            case InventoryMutationTarget.Campfire target ->
                    campfires.replaceExactSnapshotAtExpectedRevisionJoiningTransaction(
                            worldId, target, command.getExpectedRevisions().getFirst());
            case InventoryMutationTarget.MobCargo target -> mobs != null
                    && mobs.replaceCargoSnapshotAtExpectedRevisionJoiningTransaction(
                            worldId, target, command.getExpectedRevisions().getFirst());
            case InventoryMutationTarget.GeneratedChestMinecartCargo target ->
                    replaceGeneratedChestMinecartCargoAtExpectedRevisionJoiningTransaction(
                            worldId, target, command.getExpectedRevisions().getFirst());
        };
        if (!changed) {
            // 같은 대상의 동시 재시도는 대상 row lock 뒤 첫 트랜잭션의 영수증을 볼 수 있다.
            existing = settlements.findByWorldIdAndSettlementId(worldId, command.getSettlementId());
            return existing.isPresent()
                    ? idempotentOrCollision(existing.get(), commandHash) : Outcome.STALE;
        }
        settlements.save(new WorldContainerSettlement(
                worldId, command.getSettlementId(), commandHash));
        return Outcome.COMMITTED;
    }

    /** Atomically commits one exact player-hand/Armor-Stand equipment exchange. */
    @Transactional
    public ArmorStandEquipmentSettlementResult settleArmorStandEquipment(
            ArmorStandEquipmentSettlementCommand command) {
        if (playerStates == null || generatedEntityStates == null
                || generatedEntityMutations == null) {
            throw new IllegalStateException(
                    "Armor Stand equipment settlement authority is unavailable");
        }
        validateArmorStandEquipmentCommand(command);
        var expected = command.sourceEntity();
        long worldId = expected.binding().worldId();
        WorldGeneratedStructureEntityState state = generatedEntityStates
                .findLockedByWorldIdAndAuthoritativeEntityId(
                        worldId, expected.binding().entityId())
                .orElse(null);
        if (state == null) {
            throw new IllegalStateException("durable Armor Stand state is unavailable");
        }
        var current = state.runtimeSnapshot();
        if (!(current instanceof WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot
                currentArmor)) {
            throw new IllegalStateException("durable generated entity kind changed");
        }
        long persistedPlayerRevision = playerStates
                .lockInventoryPersistenceRevisionJoiningTransaction(
                        command.sourcePlayer().playerId(), worldId);
        if (persistedPlayerRevision != command.expectedPlayerRevision()
                || !sameArmorSnapshot(expected, currentArmor)) {
            return new ArmorStandEquipmentSettlementResult(
                    ArmorStandEquipmentOutcome.STALE, currentArmor, command.sourcePlayer());
        }
        var capability = generatedEntityMutations.prepareArmorStandEquipmentSettlement(
                state, command.aggregate(), command.committedEquipmentItem());
        try {
            state.applyArmorStandEquipment(capability, command.committedEquipmentItem());
            playerStates.replaceExactSnapshotJoiningTransaction(command.committedPlayer());
            generatedEntityStates.save(state);
        } catch (RuntimeException | Error failure) {
            generatedEntityMutations.cancelGeneratedEntityMutation(capability);
            throw failure;
        }
        return new ArmorStandEquipmentSettlementResult(
                ArmorStandEquipmentOutcome.COMMITTED,
                (WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot)
                        state.runtimeSnapshot(),
                command.committedPlayer());
    }

    private static Outcome idempotentOrCollision(
            WorldContainerSettlement receipt, String commandHash) {
        if (!receipt.matches(commandHash)) {
            throw new IllegalStateException("container settlement identity collision");
        }
        return Outcome.IDEMPOTENT;
    }

    private static String commandHash(ContainerSettlementCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putLong(digest, command.getSettlementId());
            digest.update(command.getParticipantFingerprint().getBytes(StandardCharsets.UTF_8));
            for (long revision : command.getExpectedRevisions()) putLong(digest, revision);
            switch (command.getTarget()) {
                case InventoryMutationTarget.Chests target -> {
                    digest.update((byte) 1);
                    for (InventoryMutationTarget.ChestHalf half : target.halves()) {
                        putPosition(digest, half.position());
                        putLong(digest, half.revision());
                        var contents = half.contents();
                        putShorts(digest, contents.itemTypes());
                        putInts(digest, contents.counts());
                        putInts(digest, contents.durabilities());
                        putLongs(digest, contents.enchantments());
                        putInts(digest, contents.mapIds());
                        putInts(digest, contents.shulkerIds());
                        putStrings(digest, contents.bucketMobData());
                        putStrings(digest, contents.itemComponentData());
                    }
                }
                case InventoryMutationTarget.Furnace target -> {
                    digest.update((byte) 2);
                    putPosition(digest, target.position());
                    putLong(digest, target.revision());
                    putShorts(digest, target.itemTypes());
                    putInts(digest, target.counts());
                    putInt(digest, target.burnTicks());
                    putInt(digest, target.burnTotalTicks());
                    putInt(digest, target.cookTicks());
                    putInt(digest, target.variantCode());
                    putInt(digest, target.xpMilli());
                    for (PlayerInventory.StackSnapshot stack : target.stacks()) {
                        putInt(digest, stack.durability());
                        putLong(digest, stack.enchantments());
                        putInt(digest, stack.mapId());
                        putInt(digest, stack.shulkerId());
                        putNullableString(digest, stack.bucketMobData());
                        putNullableString(digest, stack.itemComponentData());
                    }
                }
                case InventoryMutationTarget.Brewing target -> {
                    digest.update((byte) 4);
                    putPosition(digest, target.position());
                    putLong(digest, target.revision());
                    putShorts(digest, target.itemTypes());
                    putInts(digest, target.counts());
                    putInt(digest, target.fuel());
                    putInt(digest, target.brewTicks());
                    putStrings(digest, target.components());
                    digest.update(ByteBuffer.allocate(2).putShort(target.brewingIngredient()).array());
                }
                case InventoryMutationTarget.Campfire target -> {
                    digest.update((byte) 3);
                    putPosition(digest, target.position());
                    putLong(digest, target.revision());
                    putShorts(digest, target.itemTypes());
                    putInts(digest, target.cookTicks());
                }
                case InventoryMutationTarget.MobCargo target -> {
                    digest.update((byte) 5);
                    putLong(digest, target.mob().getMobId());
                    putLong(digest, target.revision());
                    String payload = target.mob().getHorseInventoryData();
                    putStrings(digest, new String[] {payload});
                }
                case InventoryMutationTarget.GeneratedChestMinecartCargo target -> {
                    digest.update((byte) 6);
                    putLong(digest, target.entityId());
                    putLong(digest, target.revision());
                    putCargo(digest, target.cargo());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void putPosition(MessageDigest digest, InventoryMutationTarget.Position pos) {
        putInt(digest, pos.x());
        putInt(digest, pos.y());
        putInt(digest, pos.z());
    }

    private static void putShorts(MessageDigest digest, short[] values) {
        putInt(digest, values.length);
        for (short value : values) digest.update(ByteBuffer.allocate(2).putShort(value).array());
    }

    private static void putInts(MessageDigest digest, int[] values) {
        putInt(digest, values.length);
        for (int value : values) putInt(digest, value);
    }

    private static void putLongs(MessageDigest digest, long[] values) {
        putInt(digest, values.length);
        for (long value : values) putLong(digest, value);
    }

    private static void putStrings(MessageDigest digest, String[] values) {
        putInt(digest, values.length);
        for (String value : values) {
            byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
            putInt(digest, value == null ? -1 : bytes.length);
            if (value != null) digest.update(bytes);
        }
    }

    private boolean replaceGeneratedChestMinecartCargoAtExpectedRevisionJoiningTransaction(
            long worldId, InventoryMutationTarget.GeneratedChestMinecartCargo target,
            long expectedRevision) {
        if (generatedEntityStates == null || generatedEntityMutations == null
                || generatedEntitySources == null
                || expectedRevision < 0L
                || expectedRevision == Long.MAX_VALUE
                || target.revision() != Math.addExact(expectedRevision, 1L)) return false;
        WorldGeneratedStructureEntityState state = generatedEntityStates
                .findLockedByWorldIdAndAuthoritativeEntityId(worldId, target.entityId())
                .orElse(null);
        if (state == null || state.getWorldId() != worldId
                || state.getAuthoritativeEntityId() != target.entityId()
                || state.getLifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || state.getKind() != GeneratedStructureEntityFacts.Kind.CHEST_MINECART
                || state.getRevision() != expectedRevision
                || state.getMinecartLootStatus()
                        != WorldGeneratedStructureEntityState.LootStatus.RESOLVED) {
            return false;
        }
        StructureEntityAggregate aggregate = restoreInstalledAggregate(state);
        MinecartRuntimeSnapshot current = (MinecartRuntimeSnapshot) state.runtimeSnapshot();
        MinecartRuntimeSnapshot intendedNext = new MinecartRuntimeSnapshot(current.binding(),
                target.revision(), current.lifecycle(), current.transform(), current.lootTable(),
                current.lootSeed(), current.provenance(), current.lootStatus(),
                current.lootDefinitionFingerprint(), current.lootResultFingerprint(),
                current.lootResolution(), target.cargo());
        var capability = generatedEntityMutations.prepareGeneratedEntityMutation(state,
                aggregate, intendedNext, GeneratedEntityMutationOperation.CHEST_MINECART_CARGO);
        try {
            state.applyMinecartCargo(capability, target.cargo());
        } catch (RuntimeException | Error failure) {
            generatedEntityMutations.cancelGeneratedEntityMutation(capability);
            throw failure;
        }
        generatedEntityStates.save(state);
        return true;
    }

    /** Rebuilds the exact terminal ENTS aggregate from its locked durable source rows. */
    private StructureEntityAggregate restoreInstalledAggregate(
            WorldGeneratedStructureEntityState state) {
        WorldStructureEntity targetSource = generatedEntitySources
                .findByWorldIdAndAuthoritativeEntityId(
                        state.getWorldId(), state.getAuthoritativeEntityId())
                .orElseThrow(() -> new IllegalStateException(
                        "generated chest minecart source row is unavailable"));
        List<WorldStructureEntity> rows = generatedEntitySources
                .findAllByLaneInstallationIdentityOrderByEncounterOrdinal(
                        state.getLaneInstallationIdentity());
        if (rows.isEmpty() || state.getEncounterOrdinal() >= rows.size()
                || rows.get(state.getEncounterOrdinal()).getAuthoritativeEntityId()
                        != targetSource.getAuthoritativeEntityId()) {
            throw new IllegalStateException("generated chest minecart aggregate is incomplete");
        }
        List<StructureEntity> carriers = rows.stream().map(row -> new StructureEntity(
                row.getEntityKey(), row.getSpawnReason(),
                Double.longBitsToDouble(row.getXBits()),
                Double.longBitsToDouble(row.getYBits()),
                Double.longBitsToDouble(row.getZBits()),
                Float.intBitsToFloat(row.getYawBits()),
                Float.intBitsToFloat(row.getPitchBits()),
                Double.longBitsToDouble(row.getVelocityXBits()),
                Double.longBitsToDouble(row.getVelocityYBits()),
                Double.longBitsToDouble(row.getVelocityZBits()), row.getLootTable(),
                row.getLootSeed(), row.getCanonicalPayload())).toList();
        List<Long> entityIds = rows.stream()
                .map(WorldStructureEntity::getAuthoritativeEntityId).toList();
        StructureEntityAggregate.Installation installation =
                new StructureEntityAggregate.Installation(
                        state.getLaneInstallationIdentity(), carriers, entityIds);
        if (!installation.sourceFingerprint().equals(state.getInstallationSourceFingerprint())) {
            throw new IllegalStateException("generated chest minecart aggregate source drift");
        }
        for (int ordinal = 0; ordinal < rows.size(); ordinal++) {
            WorldStructureEntity row = rows.get(ordinal);
            if (row.getWorldId() != state.getWorldId()
                    || row.getChunkX() != state.getChunkX()
                    || row.getChunkZ() != state.getChunkZ()
                    || !row.matches(state.getWorldId(), state.getChunkX(), state.getChunkZ(),
                            state.getLaneInstallationIdentity(), installation.sourceFingerprint(),
                            installation.plannedEntities().get(ordinal))) {
                throw new IllegalStateException("generated chest minecart aggregate row drift");
            }
        }
        StructureEntityAggregate aggregate = StructureEntityAggregate.prepare(installation);
        aggregate.commit(rows.stream().map(WorldStructureEntity::toReceipt).toList());
        state.requireSameBinding(state.getWorldId(), state.getChunkX(), state.getChunkZ(),
                state.getLaneInstallationIdentity(), targetSource, aggregate);
        return aggregate;
    }

    private static void validateArmorStandEquipmentCommand(
            ArmorStandEquipmentSettlementCommand command) {
        var source = command.sourcePlayer();
        var committed = command.committedPlayer();
        var armor = command.sourceEntity();
        if (command.expectedPlayerRevision() <= 0L
                || command.expectedPlayerRevision() == Long.MAX_VALUE
                || source.revision() != command.expectedPlayerRevision()
                || committed.revision() != Math.addExact(source.revision(), 1L)
                || !Objects.equals(source.playerId(), committed.playerId())
                || !Objects.equals(source.worldId(), committed.worldId())
                || source.worldId() != armor.binding().worldId()
                || armor.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || armor.revision() == Long.MAX_VALUE
                || !samePlayerEnvelope(source, committed)) {
            throw new IllegalArgumentException(
                    "invalid Armor Stand player/entity settlement identity");
        }
        double height = armor.small() ? 0.9875 : 1.975;
        if (!CombatRules.withinAuthorityReach(source.x(), source.y(), source.z(),
                command.crouching(), armor.transform().x(), armor.transform().y(),
                armor.transform().z(), 0.5, height)) {
            throw new IllegalArgumentException("Armor Stand settlement actor is out of reach");
        }
        PlayerInventory.StackSnapshot held = handStack(source, command.hand());
        String intendedItem = equipmentItemForHeld(armor.equipmentSlot(), held);
        if (!intendedItem.equals(command.committedEquipmentItem())
                || intendedItem.equals(armor.equipmentItem())) {
            throw new IllegalArgumentException("Armor Stand equipment transition is not exact");
        }
        PlayerInventory.StackSnapshot outgoing = equipmentStack(armor.equipmentItem());
        if (!outgoing.equals(handStack(committed, command.hand()))
                || !sameInventoryOutsideHand(source, committed, command.hand())) {
            throw new IllegalArgumentException(
                    "committed player snapshot differs outside the authenticated hand exchange");
        }
    }

    private static PlayerInventory.StackSnapshot handStack(
            PlayerInventoryMutationSnapshot snapshot, PlayerInventory.Hand hand) {
        if (hand == PlayerInventory.Hand.OFFHAND) return snapshot.offhand();
        int slot = snapshot.selectedSlot();
        return new PlayerInventory.StackSnapshot(snapshot.itemTypes()[slot],
                snapshot.counts()[slot], snapshot.durabilities()[slot],
                snapshot.enchantments()[slot], snapshot.mapIds()[slot],
                snapshot.shulkerIds()[slot], snapshot.bucketMobData()[slot],
                snapshot.itemComponentData()[slot]);
    }

    private static String equipmentItemForHeld(
            String slot, PlayerInventory.StackSnapshot held) {
        if (held.isEmpty()) return "minecraft:air";
        if (held.count() != 1 || held.enchantments() != 0L || held.mapId() != 0
                || held.shulkerId() != 0 || held.bucketMobData() != null
                || held.itemComponentData() != null) {
            throw new IllegalArgumentException(
                    "Armor Stand equipment requires one canonical unmodified item");
        }
        if ("head".equals(slot) && held.itemType() == PlayerInventory.IRON_HELMET
                && held.durability() == PlayerInventory.initialDurability(
                        PlayerInventory.IRON_HELMET)) return "minecraft:iron_helmet";
        if ("chest".equals(slot) && held.itemType() == PlayerInventory.IRON_CHESTPLATE
                && held.durability() == PlayerInventory.initialDurability(
                        PlayerInventory.IRON_CHESTPLATE)) return "minecraft:iron_chestplate";
        throw new IllegalArgumentException("held item does not match the Armor Stand slot");
    }

    private static PlayerInventory.StackSnapshot equipmentStack(String item) {
        short type = switch (item) {
            case "minecraft:air" -> PlayerInventory.EMPTY;
            case "minecraft:iron_helmet" -> PlayerInventory.IRON_HELMET;
            case "minecraft:iron_chestplate" -> PlayerInventory.IRON_CHESTPLATE;
            default -> throw new IllegalArgumentException(
                    "unsupported durable Armor Stand equipment item");
        };
        return type == PlayerInventory.EMPTY ? PlayerInventory.StackSnapshot.EMPTY
                : new PlayerInventory.StackSnapshot(type, 1,
                        PlayerInventory.initialDurability(type), 0L, 0, 0, null, null);
    }

    private static boolean samePlayerEnvelope(PlayerInventoryMutationSnapshot a,
            PlayerInventoryMutationSnapshot b) {
        return Double.doubleToRawLongBits(a.x()) == Double.doubleToRawLongBits(b.x())
                && Double.doubleToRawLongBits(a.y()) == Double.doubleToRawLongBits(b.y())
                && Double.doubleToRawLongBits(a.z()) == Double.doubleToRawLongBits(b.z())
                && Float.floatToRawIntBits(a.yaw()) == Float.floatToRawIntBits(b.yaw())
                && Float.floatToRawIntBits(a.pitch()) == Float.floatToRawIntBits(b.pitch())
                && a.health() == b.health() && a.selectedSlot() == b.selectedSlot()
                && Arrays.equals(a.equippedTypes(), b.equippedTypes())
                && Arrays.equals(a.equippedDurabilities(), b.equippedDurabilities())
                && Arrays.equals(a.equippedEnchantments(), b.equippedEnchantments())
                && Arrays.equals(a.equippedItemComponentData(),
                        b.equippedItemComponentData())
                && Objects.equals(a.spawnX(), b.spawnX())
                && Objects.equals(a.spawnY(), b.spawnY())
                && Objects.equals(a.spawnZ(), b.spawnZ())
                && a.hunger() == b.hunger()
                && a.saturationMilli() == b.saturationMilli()
                && a.xpTotal() == b.xpTotal() && a.enchantSeed() == b.enchantSeed()
                && a.timeSinceRestMcTicks() == b.timeSinceRestMcTicks()
                && sameChest(a.enderChest(), b.enderChest())
                && Objects.equals(a.statusEffects(), b.statusEffects())
                && Objects.equals(a.effectClocks(), b.effectClocks())
                && a.fireTicks() == b.fireTicks()
                && a.fireDamageAccum() == b.fireDamageAccum();
    }

    private static boolean sameChest(com.gameexpert.engine.ChestInventory.Snapshot a,
            com.gameexpert.engine.ChestInventory.Snapshot b) {
        return Arrays.equals(a.itemTypes(), b.itemTypes())
                && Arrays.equals(a.counts(), b.counts())
                && Arrays.equals(a.durabilities(), b.durabilities())
                && Arrays.equals(a.enchantments(), b.enchantments())
                && Arrays.equals(a.mapIds(), b.mapIds())
                && Arrays.equals(a.shulkerIds(), b.shulkerIds())
                && Arrays.equals(a.bucketMobData(), b.bucketMobData())
                && Arrays.equals(a.itemComponentData(), b.itemComponentData());
    }

    private static boolean sameArmorSnapshot(
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot a,
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot b) {
        return a.binding().equals(b.binding()) && a.revision() == b.revision()
                && a.lifecycle() == b.lifecycle() && a.transform().equals(b.transform())
                && Arrays.equals(a.poseHead(), b.poseHead())
                && Arrays.equals(a.poseBody(), b.poseBody())
                && Objects.equals(a.equipmentSlot(), b.equipmentSlot())
                && Objects.equals(a.equipmentItem(), b.equipmentItem())
                && a.showArms() == b.showArms() && a.small() == b.small()
                && a.noBasePlate() == b.noBasePlate()
                && a.invisible() == b.invisible()
                && a.invulnerable() == b.invulnerable()
                && a.disabledSlots() == b.disabledSlots()
                && Float.floatToRawIntBits(a.health())
                        == Float.floatToRawIntBits(b.health());
    }

    private static boolean sameInventoryOutsideHand(PlayerInventoryMutationSnapshot a,
            PlayerInventoryMutationSnapshot b, PlayerInventory.Hand hand) {
        short[] at = a.itemTypes(), bt = b.itemTypes();
        int[] ac = a.counts(), bc = b.counts();
        int[] ad = a.durabilities(), bd = b.durabilities();
        long[] ae = a.enchantments(), be = b.enchantments();
        int[] am = a.mapIds(), bm = b.mapIds();
        int[] as = a.shulkerIds(), bs = b.shulkerIds();
        String[] ab = a.bucketMobData(), bb = b.bucketMobData();
        String[] ai = a.itemComponentData(), bi = b.itemComponentData();
        int ignored = hand == PlayerInventory.Hand.MAIN ? a.selectedSlot() : -1;
        for (int slot = 0; slot < at.length; slot++) {
            if (slot == ignored) continue;
            if (at[slot] != bt[slot] || ac[slot] != bc[slot] || ad[slot] != bd[slot]
                    || ae[slot] != be[slot] || am[slot] != bm[slot]
                    || as[slot] != bs[slot] || !Objects.equals(ab[slot], bb[slot])
                    || !Objects.equals(ai[slot], bi[slot])) return false;
        }
        return hand == PlayerInventory.Hand.OFFHAND || a.offhand().equals(b.offhand());
    }

    private static void putCargo(MessageDigest digest, java.util.List<ChestItem> cargo) {
        putInt(digest, cargo.size());
        for (ChestItem item : cargo) {
            putInt(digest, item.getSlot());
            putShort(digest, item.getItemType());
            putInt(digest, item.getItemCount());
            putNullableInt(digest, item.getDurability());
            putNullableLong(digest, item.getEnchantments());
            putNullableInt(digest, item.getMapId());
            putNullableInt(digest, item.getShulkerId());
            putNullableString(digest, item.getBucketMobData());
            putNullableString(digest, item.getItemComponentData());
        }
    }

    private static void putShort(MessageDigest digest, short value) {
        digest.update(ByteBuffer.allocate(Short.BYTES).putShort(value).array());
    }

    private static void putNullableInt(MessageDigest digest, Integer value) {
        putInt(digest, value == null ? -1 : 1);
        if (value != null) putInt(digest, value);
    }

    private static void putNullableLong(MessageDigest digest, Long value) {
        putInt(digest, value == null ? -1 : 1);
        if (value != null) putLong(digest, value);
    }

    private static void putNullableString(MessageDigest digest, String value) {
        putStrings(digest, new String[] {value});
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(4).putInt(value).array());
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(8).putLong(value).array());
    }
}
