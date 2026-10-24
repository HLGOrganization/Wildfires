package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import first.wildfires.compat.create.ChainConveyorChainTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 给锁链传动轮的方块实体加一份「每条连接用的是哪种锁链」的记录。
 *
 * <p>★ 2026-10-23 实测的根因（Create 6.0.8 字节码）：
 * <ul>
 *   <li>{@code ChainConveyorConnectionHandler.isChain} 直接 {@code return stack.is(Items.CHAIN)}；</li>
 *   <li>{@code ChainConveyorBlockEntity.getChainsFromInventory} 只按 {@code stack.is(template.getItem())} 比较；</li>
 *   <li>但拆链时 {@code ChainConveyorConnectionPacket.applySettings} 硬编码
 *       {@code inventory.add(new ItemStack(Items.CHAIN, ...))} ⇒ 套什么拆下来都是原版锁链。</li>
 * </ul>
 *
 * <p>★ 这里的读写全部委托给 {@link ChainConveyorChainTypes}（普通类）。
 * 曾经把 {@code ItemStack#save} / {@code NbtUtils#writeBlockPos} 直接写在 mixin 里，
 * 因为 {@code remap = false} 不映射 MC 方法名，运行期报：
 * <pre>
 * MixinTransformerError: Error resolving INVOKEVIRTUAL for ...wildfires$write...
 * </pre>
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorBlockEntityChainTypeMixin
{
    @Unique
    private final Map<BlockPos, ItemStack> wildfires$chainTypes = ChainConveyorChainTypes.create();

    @Unique
    public void wildfires$setChainType(BlockPos target, ItemStack stack)
    {
        ChainConveyorChainTypes.put(wildfires$chainTypes, target, stack);
    }

    @Unique
    public ItemStack wildfires$getChainType(BlockPos target)
    {
        return ChainConveyorChainTypes.get(wildfires$chainTypes, target);
    }

    @Unique
    public void wildfires$clearChainType(BlockPos target)
    {
        wildfires$chainTypes.remove(target);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void wildfires$readChainTypes(CompoundTag tag, boolean clientPacket, CallbackInfo ci)
    {
        ChainConveyorChainTypes.read(wildfires$chainTypes, tag);
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void wildfires$writeChainTypes(CompoundTag tag, boolean clientPacket, CallbackInfo ci)
    {
        ChainConveyorChainTypes.write(wildfires$chainTypes, tag);
    }
}