package first.wildfires.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A shape-only block that uses Create's native wrench interaction. */
public class WrenchableShapeBlock extends CustomShapeBlock implements IWrenchable {

    public WrenchableShapeBlock(Properties properties, VoxelShape voxelShape) {
        super(properties, voxelShape);
    }
}
