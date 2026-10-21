package first.wildfires.mixin.tfc;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Reaches the three fields of TFC's collapse record. They are package private and TFC exposes no getter
 * for any of them, so the queue cannot be capped and two collapses cannot be merged without this.
 *
 * <p>{@code net.dries007.tfc.util.tracker.Collapse} is pure data - {@code centerPos}, {@code nextPositions}
 * and {@code radiusSquared}, serialised straight into the level's saved data - and {@code WorldTracker} is
 * the only class that ever reads them (it lives in the same package, which is why TFC needs no getters).
 * The three members are written by the constructor and by {@code WorldTracker} itself; nothing keeps an
 * invariant that writing {@code nextPositions} or {@code radiusSquared} from outside could break, because
 * those are exactly the operations {@code WorldTracker.tick} performs on them.
 *
 * <p>{@code @Pseudo} with the target named as a string, like the rest of this package: the collapse record
 * is only reached when TFC is installed, and the mixin is skipped if it is not.
 */
@Pseudo
@Mixin(targets = "net.dries007.tfc.util.tracker.Collapse", remap = false)
public interface CollapseAccess {

    @Accessor("centerPos")
    BlockPos wildfires$getCenterPos();

    @Accessor("nextPositions")
    List<BlockPos> wildfires$getNextPositions();

    @Accessor("radiusSquared")
    double wildfires$getRadiusSquared();

    @Accessor("radiusSquared")
    void wildfires$setRadiusSquared(double radiusSquared);
}
