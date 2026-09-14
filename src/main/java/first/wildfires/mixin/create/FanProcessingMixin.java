package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * A lava fan destroys every item it cannot process, because the blasting
 * processor answers with an empty list for those. Handing the original stack
 * back keeps such items in the world instead.
 */
@Mixin(value = AllFanProcessingTypes.BlastingType.class, remap = false)
public abstract class FanProcessingMixin {

    @Inject(
            method = "process(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void wildfires$keepUnprocessedBlastingItem(
            ItemStack stack,
            Level level,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        List<ItemStack> processed = cir.getReturnValue();
        if (processed != null && !processed.isEmpty()) {
            return;
        }

        List<ItemStack> kept = new ArrayList<>(1);
        kept.add(stack.copy());
        cir.setReturnValue(kept);
    }
}
