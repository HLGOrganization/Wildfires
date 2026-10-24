package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import first.wildfires.compat.create.ChainConveyorChains;
import first.wildfires.compat.create.ChainConveyorReflect;
import first.wildfires.compat.create.ChainConveyorWrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拆锁链及拆传动轮本体时按连接记录返还材质。
 * 木齿轮替换了 forPointsAlongChains 的 consumer，需在它采样前补回 native 材质表；
 * 空间掉落无法执行时，另在 Create 的双参 ItemStack 构造处覆盖 fallback。
 * 潜行扳手拆本体直接返包，由 ChainConveyorBlockWrenchMixin 单独处理。
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorChainDestroyedMixin
{
    // destroy 在内部逐条调用 chainDestroyed。先在外层回填，确保早于木齿轮采样材质，
    // 不依赖两个 chainDestroyed HEAD 注入的先后顺序。
    @Inject(method = "destroy", at = @At("HEAD"), remap = false, require = 1)
    private void wildfires$restoreNativeDropTypes(CallbackInfo ci)
    {
        final ChainConveyorBlockEntity be = (ChainConveyorBlockEntity) (Object) this;
        if (be.cancelDrops) return;
        for (final BlockPos relative : be.connections)
            ChainConveyorReflect.restoreNativeChainType(be, relative,
                    ChainConveyorWrenchHelper.getRecordedChain(be, relative));
    }

    @Redirect(
            method = "chainDestroyed",
            at = @At(value = "NEW",
                    target = "(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 0),
            remap = false,
            require = 1
    )
    private ItemStack wildfires$dropRecordedChain(ItemLike original, int count, BlockPos target)
    {
        final ItemStack recorded = ChainConveyorWrenchHelper.getRecordedChain(
                (ChainConveyorBlockEntity) (Object) this, target);

        if (original.asItem() == Items.CHAIN && ChainConveyorChains.isChain(recorded))
        {
            final ItemStack result = recorded.copy();
            result.setCount(count);
            return result;
        }

        return new ItemStack(original, count);
    }
}
