package first.wildfires.mixin.tfc;

import first.wildfires.utils.HeatStacking;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>群峦设备自己那一套合并</h2>
 * 群峦的机器合并物品时不走原版判定，而是 {@code Helpers.mergeInsertStack}：把槽里的东西取出来、
 * 交给 {@link FoodCapability#mergeItemStacks} 合并、再放回槽里。跟原版一样，它判定用的是
 * <b>堆叠的副本</b>（{@code areStacksStackableExceptCreationDate} 会先复制再改制作日期），
 * 而 {@link HeatStacking#tolerateSameStack} 本身只判定、不写温度，
 * 所以合并结果会保留目标槽原本的温度——未必是两者中较低的那个，也可能高得没道理。
 * <p>
 * 于是这里在合并前后各插一手：合并前记下两个温度，合并后把结果改成两者的平均温度。
 * 原版那一侧同样的收尾在 {@code ItemStack#grow}，见 {@code first.wildfires.mixin.minecraft.ItemStackMixin}。
 * 用 {@link ThreadLocal} 而不是在返回时重读，是因为源堆叠在完全并进去之后计数会归零，温度已经不可信。
 * <p>
 * 只在温度真的不同时才会写，且差值超过容差时不动（那种情况本来就叠不起来，不该在这里改温度）。
 */
@Mixin(value = FoodCapability.class, remap = false)
public class FoodCapabilityMixin {

    private static final ThreadLocal<float[]> WILDFIRES$TEMPERATURES = new ThreadLocal<>();

    @Inject(method = "mergeItemStacks", at = @At("HEAD"))
    private static void wildfires$captureTemperatures(ItemStack into, ItemStack from, CallbackInfoReturnable<ItemStack> cir) {
        WILDFIRES$TEMPERATURES.set(new float[]{
                HeatCapability.getTemperature(into),
                HeatCapability.getTemperature(from)
        });
    }

    @Inject(method = "mergeItemStacks", at = @At("RETURN"))
    private static void wildfires$takeAverageTemperature(ItemStack into, ItemStack from, CallbackInfoReturnable<ItemStack> cir) {
        float[] temperatures = WILDFIRES$TEMPERATURES.get();
        WILDFIRES$TEMPERATURES.remove();
        if (temperatures != null) {
            HeatStacking.takeAverage(cir.getReturnValue(), temperatures[0], temperatures[1]);
        }
    }
}
