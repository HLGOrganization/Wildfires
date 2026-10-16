/*
 * Adapted from NTM: Space TileEntityOrbitalStation's 5x5x2 docking-core volume.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: materializes the upstream multi-block tile entity as one root block
 * entity plus forty-nine proxy blocks carrying a fluid-port flag, with proxy ownership, teardown
 * and recovery resolved only at placement, interaction, destruction and server-start boundaries.
 */
package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One of forty-nine proxy blocks owned by a primary or secondary NTM 5x5x2 docking core. */
public final class StationStructureBlock extends BaseEntityBlock {

    public static final BooleanProperty FLUID_PORT = BooleanProperty.create("fluid_port");

    public StationStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FLUID_PORT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,
            BlockState> builder) {
        builder.add(FLUID_PORT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(FLUID_PORT) ? new StationFluidPortBlockEntity(pos, state) : null;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        // NTM's docking core owns and positions its rocket entity directly. Proxy blocks retain
        // normal player/world collision but never push the managed shuttle away from its port.
        return context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof first.wildfires.space.capsule.ReusableReturnCapsuleEntity
                ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level instanceof Level world && StationCoreService.coreForStructureBlock(world, pos)
                .flatMap(core -> world.getBlockEntity(core) instanceof StationCoreBlockEntity blockEntity
                        ? java.util.Optional.of(blockEntity) : java.util.Optional.empty())
                .filter(StationCoreBlockEntity::primary).isEmpty()) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        return 0.0F;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        BlockPos core = StationCoreService.coreForStructureBlock(serverLevel, pos).orElse(null);
        return core != null && StationCoreService.removeSecondary(serverLevel, core,
                player.getUUID(), !player.isCreative());
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        // As with the root block, teardown must pass through the authorized atomic dock removal.
        return false;
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // Secondary cores are removed through their root; primary proxies remain protected.
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
        if (level.isClientSide()) {
            if (StationCoreService.coreForTopCenterBlock(level, pos).isPresent()) {
                var held = player.getItemInHand(hand);
                return held.isEmpty() || held.is(net.minecraft.world.item.Items.WATER_BUCKET)
                        || held.is(SpaceContentRegister.STATION_ID_TAPE.get())
                        ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player.getItemInHand(hand).is(SpaceContentRegister.STATION_ID_TAPE.get())
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }
        BlockPos topCore = StationCoreService.coreForTopCenterBlock(serverLevel, pos).orElse(null);
        if (topCore != null) {
            return StationCoreService.interactTopCenter(serverLevel, topCore, player, hand);
        }
        if (!player.getItemInHand(hand).is(SpaceContentRegister.STATION_ID_TAPE.get())) {
            return InteractionResult.PASS;
        }
        return StationCoreService.coreForStructureBlock(serverLevel, pos)
                .filter(core -> Math.abs(core.getX() - pos.getX()) < 2
                        && Math.abs(core.getZ() - pos.getZ()) < 2)
                .map(core -> StationIdTapeItem.programFromCore(serverLevel, core, player, hand))
                .orElse(InteractionResult.FAIL);
    }
}
