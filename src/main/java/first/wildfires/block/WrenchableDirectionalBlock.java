package first.wildfires.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A directional block that uses Create's native wrench interaction. */
public class WrenchableDirectionalBlock extends CustomDirectionalBlock implements IWrenchable {

    public WrenchableDirectionalBlock(Properties properties, VoxelShape north, VoxelShape east,
                                      VoxelShape south, VoxelShape west) {
        super(properties, north, east, south, west);
    }
}
