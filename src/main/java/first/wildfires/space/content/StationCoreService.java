/*
 * Adapted from NTM: Space OrbitalStation / TileEntityOrbitalStation station-core construction and
 * membership lifecycle.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: replaces the fixed orbit-grid station with UUID stations persisted in
 * SavedData, materializes the exact one-root-plus-forty-nine-proxy 5x5x2 core idempotently at
 * placement, interaction and server-start boundaries, and adds no periodic reconciliation tick.
 */
package first.wildfires.space.content;

import com.mojang.logging.LogUtils;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Idempotently materializes the authoritative core at every station's primary dock. */
public final class StationCoreService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<BlockPos> STRUCTURE_OFFSETS = createOffsets();
    private static final List<BlockPos> FLUID_PORT_OFFSETS = createFluidPortOffsets();

    private StationCoreService() {
    }

    public static int ensureAll(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        SpaceSavedData data = SpaceSavedData.get(server);
        if (!data.writable()) {
            LOGGER.error("Cannot reconcile station cores because space data is read-only: {}",
                    data.writeBlockReason().orElse("unknown reason"));
            return 0;
        }
        int ensured = 0;
        for (StationRecord station : data.stations().values()) {
            if (ensureCore(server, station)) {
                ensured++;
            }
        }
        return ensured;
    }

    public static boolean ensureCore(MinecraftServer server, StationRecord station) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(station, "station");
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) {
            LOGGER.error("Cannot materialize station core {}: wildfires:orbit is unavailable",
                    station.stationId());
            return false;
        }
        BlockPos position = station.primaryDock().position();
        orbit.getChunkAt(position);
        if (!orbit.getBlockState(position).is(SpaceContentRegister.STATION_CORE.get())
                && !orbit.setBlock(position, SpaceContentRegister.STATION_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL)) {
            LOGGER.error("Cannot materialize station core {} at {}", station.stationId(), position);
            return false;
        }
        if (!(orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core)) {
            LOGGER.error("Station core {} at {} has no matching block entity", station.stationId(), position);
            return false;
        }
        core.bind(station.stationId(), station.primaryDockId(), true);
        boolean complete = true;
        for (BlockPos offset : STRUCTURE_OFFSETS) {
            BlockPos proxy = position.offset(offset);
            orbit.getChunkAt(proxy);
            var desired = structureState(offset);
            if (!orbit.getBlockState(proxy).equals(desired)
                    && !orbit.setBlock(proxy, desired, Block.UPDATE_ALL)) {
                LOGGER.error("Cannot materialize station core proxy {} at {}", station.stationId(), proxy);
                complete = false;
                continue;
            }
            bindFluidPort(orbit, position, proxy, offset);
        }
        return complete;
    }

    /** Bottom-centre core plus these forty-nine offsets form NTM's exact 5x5x2 volume. */
    public static List<BlockPos> structureOffsets() {
        return STRUCTURE_OFFSETS;
    }

    /** NTM getConPos: three upper-edge connections on each of the four core sides. */
    public static List<BlockPos> fluidPortOffsets() {
        return FLUID_PORT_OFFSETS;
    }

    public static boolean isCoreStructureBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(SpaceContentRegister.STATION_CORE.get())
                || state.is(SpaceContentRegister.STATION_STRUCTURE.get());
    }

    public static boolean isComplete(net.minecraft.world.level.Level level, BlockPos corePosition) {
        if (!level.getBlockState(corePosition).is(SpaceContentRegister.STATION_CORE.get())) return false;
        return STRUCTURE_OFFSETS.stream().allMatch(offset -> {
            var state = level.getBlockState(corePosition.offset(offset));
            return state.is(SpaceContentRegister.STATION_STRUCTURE.get())
                    && state.getValue(StationStructureBlock.FLUID_PORT) == isFluidPort(offset);
        });
    }

    public static ResourceLocation secondaryDockId(BlockPos position) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", "dock/" + position.getX() + "/"
                + position.getY() + "/" + position.getZ());
    }

    public static boolean canMaterialize(ServerLevel level, BlockPos corePosition) {
        for (int y = 0; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos position = corePosition.offset(x, y, z);
                    if (!level.getBlockState(position).canBeReplaced()) return false;
                }
            }
        }
        return true;
    }

    public static boolean materializeSecondary(ServerLevel level, StationRecord station,
                                               BlockPos position, UUID actor) {
        if (level.dimension() != SpaceDimensions.ORBIT
                || !station.region().containsBuildArea(position) || !station.mayManage(actor)
                || !canMaterialize(level, position)
                || !level.noCollision(first.wildfires.space.capsule.ReturnCapsuleService.capsuleBoundsAt(
                first.wildfires.space.capsule.ReturnCapsuleService.stationDockedPosition(position)))) return false;
        ResourceLocation dockId = secondaryDockId(position);
        var result = first.wildfires.space.station.StationService.setDock(
                SpaceSavedData.get(level.getServer()), station.stationId(), actor,
                new first.wildfires.space.station.StationDockRecord(dockId, position), true,
                level.getServer().overworld().getGameTime());
        if (!result.successful()) return false;
        if (!level.setBlock(position, SpaceContentRegister.STATION_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL) || !(level.getBlockEntity(position) instanceof StationCoreBlockEntity core)) {
            first.wildfires.space.station.StationService.setDock(SpaceSavedData.get(level.getServer()),
                    station.stationId(), actor,
                    new first.wildfires.space.station.StationDockRecord(dockId, position), false,
                    level.getServer().overworld().getGameTime());
            return false;
        }
        core.bind(station.stationId(), dockId, false);
        for (BlockPos offset : STRUCTURE_OFFSETS) {
            BlockPos proxy = position.offset(offset);
            if (!level.setBlock(proxy, structureState(offset), Block.UPDATE_ALL)) {
                removeSecondary(level, position, actor, false);
                return false;
            }
            bindFluidPort(level, position, proxy, offset);
        }
        return true;
    }

    public static boolean removeSecondary(ServerLevel level, BlockPos corePosition, UUID actor,
                                          boolean dropItem) {
        if (!(level.getBlockEntity(corePosition) instanceof StationCoreBlockEntity core)
                || core.primary() || core.claimedCapsuleId().isPresent()) return false;
        UUID stationId = core.stationId().orElse(null);
        ResourceLocation dockId = core.dockId().orElse(null);
        if (stationId == null || dockId == null) return false;
        StationRecord station = SpaceSavedData.get(level.getServer()).station(stationId).orElse(null);
        if (station == null || !station.mayManage(actor)) return false;
        var result = first.wildfires.space.station.StationService.setDock(
                SpaceSavedData.get(level.getServer()), stationId, actor,
                new first.wildfires.space.station.StationDockRecord(dockId, corePosition), false,
                level.getServer().overworld().getGameTime());
        if (!result.successful()) return false;
        level.removeBlock(corePosition, false);
        for (BlockPos offset : STRUCTURE_OFFSETS) level.removeBlock(corePosition.offset(offset), false);
        if (dropItem) Block.popResource(level, corePosition, new ItemStack(SpaceContentRegister.STATION_CORE_ITEM.get()));
        return true;
    }

    /**
     * Resolves one visible proxy back to the sole complete core volume that owns it. Ambiguous or
     * isolated structure blocks deliberately resolve to empty instead of guessing a nearby station.
     */
    public static Optional<BlockPos> coreForStructureBlock(net.minecraft.world.level.Level level,
                                                           BlockPos structurePosition) {
        if (!level.getBlockState(structurePosition).is(SpaceContentRegister.STATION_STRUCTURE.get())) {
            return Optional.empty();
        }
        BlockPos resolved = null;
        for (BlockPos offset : STRUCTURE_OFFSETS) {
            BlockPos candidate = structurePosition.subtract(offset);
            if (!level.getBlockState(candidate).is(SpaceContentRegister.STATION_CORE.get())
                    || !isComplete(level, candidate)) {
                continue;
            }
            if (resolved != null && !resolved.equals(candidate)) {
                return Optional.empty();
            }
            resolved = candidate.immutable();
        }
        return Optional.ofNullable(resolved);
    }

    /** NTM's usable top-centre proxy is directly above the bottom-centre core block. */
    public static BlockPos topCenter(BlockPos corePosition) {
        return corePosition.above().immutable();
    }

    /** Constant-time top-centre owner lookup; it never scans unrelated or isolated proxies. */
    public static Optional<BlockPos> coreForTopCenterBlock(net.minecraft.world.level.Level level,
                                                           BlockPos structurePosition) {
        if (!level.getBlockState(structurePosition).is(SpaceContentRegister.STATION_STRUCTURE.get())) {
            return Optional.empty();
        }
        BlockPos candidate = structurePosition.below();
        return level.getBlockState(candidate).is(SpaceContentRegister.STATION_CORE.get())
                && isComplete(level, candidate) ? Optional.of(candidate.immutable()) : Optional.empty();
    }

    /**
     * Adapts NTM's orbital-station activation onto the one explicitly requested proxy surface.
     * Empty hand enters the docked pod, sneak-empty retrieves it, water fills it, and a programmed
     * station tape is swapped with the pod's one internal tape slot.
     */
    public static InteractionResult interactTopCenter(ServerLevel level, BlockPos corePosition,
                                                       Player player, InteractionHand hand) {
        if (!isComplete(level, corePosition)
                || !(level.getBlockEntity(corePosition) instanceof StationCoreBlockEntity core)) {
            return InteractionResult.FAIL;
        }
        var held = player.getItemInHand(hand);
        boolean supportedItem = held.isEmpty() || held.is(Items.WATER_BUCKET)
                || held.is(SpaceContentRegister.STATION_ID_TAPE.get());
        if (!supportedItem) return InteractionResult.PASS;
        first.wildfires.space.capsule.ReusableReturnCapsuleEntity capsule = core.dockedCapsuleId()
                .map(level::getEntity)
                .filter(first.wildfires.space.capsule.ReusableReturnCapsuleEntity.class::isInstance)
                .map(first.wildfires.space.capsule.ReusableReturnCapsuleEntity.class::cast)
                .filter(value -> value.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED)
                .filter(first.wildfires.space.capsule.ReusableReturnCapsuleEntity::dockLocked)
                .filter(value -> value.position().distanceToSqr(
                        first.wildfires.space.capsule.ReturnCapsuleService.stationDockedPosition(
                                corePosition)) < 0.0625D)
                .orElse(null);
        if (capsule == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "space.wildfires.station_core.no_capsule"), true);
            return InteractionResult.CONSUME;
        }
        if (held.is(SpaceContentRegister.STATION_ID_TAPE.get())) {
            return first.wildfires.space.capsule.ReturnCapsuleService.applyStationTape(
                    player, capsule, hand, held);
        }
        if (held.is(Items.WATER_BUCKET)) {
            return capsule.interact(player, hand);
        }
        if (player.isShiftKeyDown()) {
            boolean recovered = first.wildfires.space.capsule.ReturnCapsuleService
                    .tryRecoverAsItem(player, capsule);
            return recovered ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return capsule.interact(player, hand);
    }

    private static List<BlockPos> createOffsets() {
        List<BlockPos> offsets = new ArrayList<>(49);
        for (int y = 0; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x != 0 || y != 0 || z != 0) offsets.add(new BlockPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(offsets);
    }

    private static List<BlockPos> createFluidPortOffsets() {
        List<BlockPos> offsets = new ArrayList<>(12);
        for (int tangent = -1; tangent <= 1; tangent++) {
            offsets.add(new BlockPos(tangent, 1, -2));
            offsets.add(new BlockPos(tangent, 1, 2));
            offsets.add(new BlockPos(-2, 1, tangent));
            offsets.add(new BlockPos(2, 1, tangent));
        }
        return Collections.unmodifiableList(offsets);
    }

    private static boolean isFluidPort(BlockPos offset) {
        return offset.getY() == 1 && ((Math.abs(offset.getX()) == 2 && Math.abs(offset.getZ()) <= 1)
                || (Math.abs(offset.getZ()) == 2 && Math.abs(offset.getX()) <= 1));
    }

    private static net.minecraft.world.level.block.state.BlockState structureState(BlockPos offset) {
        return SpaceContentRegister.STATION_STRUCTURE.get().defaultBlockState()
                .setValue(StationStructureBlock.FLUID_PORT, isFluidPort(offset));
    }

    private static void bindFluidPort(ServerLevel level, BlockPos corePosition,
                                      BlockPos proxyPosition, BlockPos offset) {
        if (!isFluidPort(offset)) return;
        if (level.getBlockEntity(proxyPosition) instanceof StationFluidPortBlockEntity port) {
            port.bindCore(corePosition);
        } else {
            LOGGER.error("Station fluid interface at {} has no forwarding block entity", proxyPosition);
        }
    }
}
