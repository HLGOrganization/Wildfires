package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import first.wildfires.compat.create.ChainConveyorChains;
import first.wildfires.compat.create.ChainConveyorWrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在入包前替换材质：满背包掉落也使用正确的链，不依赖事后统计。 */
@Mixin(value = ChainConveyorConnectionPacket.class, remap = false, priority = 900)
public abstract class ChainConveyorReturnChainMixin
{
    @Shadow(remap = false) private BlockPos targetPos;
    @Shadow(remap = false) private boolean connect;
    @Unique private ItemStack wildfires$returnChain = ItemStack.EMPTY;

    @Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V",
            at = @At("HEAD"), remap = false)
    private void wildfires$captureChainType(ServerPlayer player, ChainConveyorBlockEntity be, CallbackInfo ci)
    {
        wildfires$returnChain = !connect && be != null && targetPos != null
                ? ChainConveyorWrenchHelper.getRecordedChain(be, targetPos).copy() : ItemStack.EMPTY;
    }

    // remap=false 的外部模组方法内，Minecraft 调用必须匹配部署 jar 的 SRG 名。
    @ModifyArg(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;m_150079_(Lnet/minecraft/world/item/ItemStack;)V"),
            index = 0, remap = false, require = 1)
    private ItemStack wildfires$returnSameChain(ItemStack original)
    {
        // 保留其他兼容模组已替换的物品（例如幻影链）。
        if (original.is(Items.CHAIN) && ChainConveyorChains.isChain(wildfires$returnChain))
            return wildfires$returnChain.copyWithCount(original.getCount());
        return original;
    }
}