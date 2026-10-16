/*
 * Adapted from NTM: Space TileEntityOrbitalStation's multiple reservable docking-core membership.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: placement is authoritative on the server, requires the clicked block
 * to sit inside the player's station region, materializes one secondary 5x5x2 core, and treats
 * the clicked block as a construction anchor rather than a one-block BlockItem offset.
 */
package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Places an NTM-style additional docking core inside the player's station region. */
public final class StationDockCoreItem extends BlockItem {
    public StationDockCoreItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.FAIL;
        BlockPos target = placementRoot(context.getClickedPos(), context.getClickedFace());
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (level.dimension() != SpaceDimensions.ORBIT) return InteractionResult.FAIL;
        StationRecord station = SpaceSavedData.get(level.getServer()).stationAt(target.getX(), target.getZ())
                .orElse(null);
        if (station == null || !StationCoreService.materializeSecondary(level, station, target,
                context.getPlayer().getUUID())) return InteractionResult.FAIL;
        if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    /**
     * The clicked block is a construction anchor, not part of the port. A one-block BlockItem
     * offset is impossible for this model: it would put the anchor inside either the 5x5x2 shell
     * or the capsule bay beneath it. These fixed offsets leave both volumes clear on every face.
     */
    static BlockPos placementRoot(BlockPos anchor, Direction face) {
        int clearance = face == Direction.UP ? 5 : face == Direction.DOWN ? 2 : 3;
        return anchor.relative(face, clearance).immutable();
    }
}
