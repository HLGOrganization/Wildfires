package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import first.wildfires.compat.create.ChainConveyorChains;
import first.wildfires.compat.create.ChainConveyorWrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 潜行扳手拆传动轮本体走独立返包循环；在入包前按当前连接替换材质。 */
@Mixin(value = ChainConveyorBlock.class, remap = false)
public abstract class ChainConveyorBlockWrenchMixin
{
    @ModifyArg(method = "lambda$onSneakWrenched$1",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;m_150079_(Lnet/minecraft/world/item/ItemStack;)V"),
            index = 0, remap = false, require = 1)
    private static ItemStack wildfires$returnRecordedBodyChain(ItemStack original,
            @Local(argsOnly = true) ChainConveyorBlockEntity be, @Local(ordinal = 0) BlockPos relative)
    {
        // 幻影链等已有非原版结果交回原兼容逻辑，数量仍由 Create 决定。
        if (!original.is(Items.CHAIN)) return original;
        final ItemStack recorded = ChainConveyorWrenchHelper.getRecordedChain(be, relative);
        return ChainConveyorChains.isChain(recorded)
                ? recorded.copyWithCount(original.getCount()) : original;
    }
}
