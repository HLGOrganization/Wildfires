/*
 * Adapted from NTM: Space TileEntityOrbitalStation's primary/secondary docking-core contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: splits the single upstream station core into one immutable primary
 * block plus removable secondary dock blocks, addressed by synchronized station UUIDs through
 * Forge 1.20.1 block/entity APIs instead of the 1.7.10 tile-entity contract.
 */
package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Shared NTM-style model for an immutable primary core or a removable secondary dock. */
public final class StationCoreBlock extends BaseEntityBlock {

    public StationCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof first.wildfires.space.capsule.ReusableReturnCapsuleEntity
                ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide()) return null;
        return createTickerHelper(type, SpaceContentRegister.STATION_CORE_BLOCK_ENTITY.get(),
                StationCoreBlockEntity::clientTick);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StationCoreBlockEntity core && core.primary()
                ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(serverLevel.getBlockEntity(pos) instanceof StationCoreBlockEntity core)
                || core.primary()) return false;
        return StationCoreService.removeSecondary(serverLevel, pos, player.getUUID(), !player.isCreative());
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        // Secondary ports are player-managed SavedData records, not ordinary loose blocks.
        // Mobs and scripted entity destruction cannot supply the station permission/lock
        // transaction required by removeSecondary.
        return false;
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // Dock records require an authorized, transactional teardown; explosions never bypass it.
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(SpaceContentRegister.STATION_ID_TAPE.get())) {
            return StationIdTapeItem.programFromCore(level, pos, player, hand);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel
                && level.getBlockEntity(pos) instanceof StationCoreBlockEntity core) {
            player.displayClientMessage(core.stationId()
                    .map(id -> Component.translatable("space.wildfires.station_core.bound", id))
                    .orElseGet(() -> Component.translatable("space.wildfires.station_core.unbound")), true);
        }
        return InteractionResult.CONSUME;
    }
}
