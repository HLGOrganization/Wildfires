/*
 * Adapted from NTM: Space TileEntityOrbitalStation's docked-rocket and port bookkeeping.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: replaces the live in-memory docked-rocket reference with persistent
 * station/dock UUIDs, keeps reservation and physical docking as separate ids, exposes the
 * synchronized client clamp rotation, and forwards Forge water through the physically docked
 * capsule only.
 */
package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Stores one primary/secondary dock identity and its physical capsule reservation. */
public final class StationCoreBlockEntity extends BlockEntity {

    private static final String STATION_ID = "station_id";
    private static final String DOCKED_CAPSULE_ID = "docked_capsule_id";
    private static final String RESERVED_CAPSULE_ID = "reserved_capsule_id";
    private static final String DOCK_ID = "dock_id";
    private static final String PRIMARY = "primary";
    private UUID stationId;
    private UUID dockedCapsuleId;
    private UUID reservedCapsuleId;
    private net.minecraft.resources.ResourceLocation dockId;
    private boolean primary;
    private float clientArmRotation;
    private float clientPreviousArmRotation;
    private final IFluidHandler dockedCapsuleFuel = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? dockedCapsule().map(value -> value.fuelTank().getFluidInTank(0))
                    .orElse(FluidStack.EMPTY) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0
                    ? first.wildfires.space.capsule.ReturnCapsuleFuelTank.CAPACITY_MB : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && stack.getFluid() == Fluids.WATER;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return dockedCapsule().map(value -> value.fuelTank().fill(resource, action)).orElse(0);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> dockedCapsuleFuel);

    public StationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    public Optional<UUID> stationId() {
        return Optional.ofNullable(stationId);
    }

    public void bind(UUID stationId, net.minecraft.resources.ResourceLocation dockId, boolean primary) {
        if (stationId.equals(this.stationId) && dockId.equals(this.dockId) && primary == this.primary) {
            return;
        }
        this.stationId = stationId;
        this.dockId = dockId;
        this.primary = primary;
        markAndSync();
    }

    public Optional<net.minecraft.resources.ResourceLocation> dockId() {
        return Optional.ofNullable(dockId);
    }

    public boolean primary() {
        return primary;
    }

    /** Exact client-side TileEntityOrbitalStation rot/prevRot clamp animation. */
    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos,
                                  BlockState state, StationCoreBlockEntity core) {
        if (!level.isClientSide()) return;
        core.clientPreviousArmRotation = core.clientArmRotation;
        if (core.dockedCapsuleId != null) {
            core.clientArmRotation = Math.min(90.0F, core.clientArmRotation + 2.25F);
        } else {
            core.clientArmRotation = Math.max(0.0F, core.clientArmRotation - 2.25F);
        }
    }

    public float clientArmRotation(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick,
                clientPreviousArmRotation, clientArmRotation);
    }

    public Optional<UUID> dockedCapsuleId() {
        return Optional.ofNullable(dockedCapsuleId);
    }

    public Optional<UUID> reservedCapsuleId() {
        return Optional.ofNullable(reservedCapsuleId);
    }

    /** NTM isReserved arbitrates an arriving pod without driving the visible clamp arms. */
    public boolean reserveDock(UUID capsuleId) {
        if (dockedCapsuleId != null && !dockedCapsuleId.equals(capsuleId)) return false;
        if (reservedCapsuleId != null && !reservedCapsuleId.equals(capsuleId)) return false;
        if (dockedCapsuleId == null && reservedCapsuleId == null) {
            reservedCapsuleId = capsuleId;
            markAndSync();
        }
        return true;
    }

    /** NTM dockRocket: only exact physical arrival changes hasDocked and closes the clamps. */
    public boolean completeDock(UUID capsuleId) {
        if (dockedCapsuleId != null && !dockedCapsuleId.equals(capsuleId)) return false;
        if (reservedCapsuleId != null && !reservedCapsuleId.equals(capsuleId)) return false;
        boolean changed = !capsuleId.equals(dockedCapsuleId) || reservedCapsuleId != null;
        dockedCapsuleId = capsuleId;
        reservedCapsuleId = null;
        if (changed) markAndSync();
        return true;
    }

    public Optional<UUID> claimedCapsuleId() {
        return dockedCapsuleId != null ? Optional.of(dockedCapsuleId)
                : Optional.ofNullable(reservedCapsuleId);
    }

    public boolean releaseDockLock(UUID capsuleId) {
        boolean dockedMatches = capsuleId.equals(dockedCapsuleId);
        boolean reservedMatches = capsuleId.equals(reservedCapsuleId);
        if (!dockedMatches && !reservedMatches) {
            return dockedCapsuleId == null && reservedCapsuleId == null;
        }
        if (dockedMatches) dockedCapsuleId = null;
        if (reservedMatches) reservedCapsuleId = null;
        markAndSync();
        return true;
    }

    private Optional<first.wildfires.space.capsule.ReusableReturnCapsuleEntity> dockedCapsule() {
        if (!(level instanceof ServerLevel serverLevel) || dockedCapsuleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(serverLevel.getEntity(dockedCapsuleId))
                .filter(first.wildfires.space.capsule.ReusableReturnCapsuleEntity.class::isInstance)
                .map(first.wildfires.space.capsule.ReusableReturnCapsuleEntity.class::cast)
                .filter(value -> value.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED)
                .filter(first.wildfires.space.capsule.ReusableReturnCapsuleEntity::dockLocked)
                .filter(value -> value.position().distanceToSqr(
                        first.wildfires.space.capsule.ReturnCapsuleService.stationDockedPosition(
                                worldPosition)) < 0.0625D);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable net.minecraft.core.Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> dockedCapsuleFuel);
    }

    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (stationId != null) {
            tag.putUUID(STATION_ID, stationId);
        }
        if (dockedCapsuleId != null) tag.putUUID(DOCKED_CAPSULE_ID, dockedCapsuleId);
        if (reservedCapsuleId != null) tag.putUUID(RESERVED_CAPSULE_ID, reservedCapsuleId);
        if (dockId != null) tag.putString(DOCK_ID, dockId.toString());
        tag.putBoolean(PRIMARY, primary);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stationId = tag.contains(STATION_ID, Tag.TAG_INT_ARRAY) ? tag.getUUID(STATION_ID) : null;
        dockedCapsuleId = tag.contains(DOCKED_CAPSULE_ID, Tag.TAG_INT_ARRAY)
                ? tag.getUUID(DOCKED_CAPSULE_ID) : null;
        reservedCapsuleId = tag.contains(RESERVED_CAPSULE_ID, Tag.TAG_INT_ARRAY)
                ? tag.getUUID(RESERVED_CAPSULE_ID) : null;
        dockId = tag.contains(DOCK_ID, Tag.TAG_STRING)
                ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString(DOCK_ID)) : null;
        // Old bound cores predate secondary docks and are therefore authoritative primary cores.
        primary = tag.contains(PRIMARY, Tag.TAG_BYTE) ? tag.getBoolean(PRIMARY) : stationId != null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-2, -1, -2), worldPosition.offset(3, 3, 3));
    }
}
