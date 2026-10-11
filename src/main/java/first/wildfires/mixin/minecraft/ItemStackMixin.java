package first.wildfires.mixin.minecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import first.wildfires.utils.FoodStacking;
import first.wildfires.utils.HeatStacking;
import first.wildfires.utils.ItemDamageContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * <h2>温度接近（或剩余保质期接近）的物品也算同一种物品</h2>
     * 这是原版的"能不能叠"判定，物品栏、物品实体、合成栏、容器之间搬运物品全都要过它。
     * 群峦的温度存在能力里，而这里在比较物品和 NBT 之后还要 {@code areCapsCompatible}，
     * 所以温度差一度就判不相同。原版已经返回假时，问一次
     * {@link HeatStacking#tolerateSameStack}：只差容差以内的温度就放行。
     * <p>
     * <b>这里只判定、不写温度。</b>容器界面每刻都会拿槽里的真实堆叠和上次发出去的快照过一遍
     * （{@code AbstractContainerMenu#broadcastChanges} → {@code ItemStack.matches}），
     * 一旦在这里顺手把温度压低，正在加热的物品就会每刻被按回旧温度，界面开着时永远热不上去。
     * 合并结果取平均另找真正合并的地方收尾：原版走下面的 {@code grow}，
     * 群峦设备走 {@code FoodCapability#mergeItemStacks}（见 {@code first.wildfires.mixin.tfc.FoodCapabilityMixin}）。
     * <p>
     * 机器、管道那一侧走的是 {@code ItemHandlerHelper.canItemStacksStack}，另有
     * {@code first.wildfires.mixin.forge.ItemHandlerHelperMixin} 同样处理，两边缺一不可。
     */
    @Inject(
            method = "isSameItemSameTags",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void wildfires$allowCloseTemperaturesToStack(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (HeatStacking.tolerateSameStack(a, b) || FoodStacking.tolerateSameFood(a, b))) {
            cir.setReturnValue(true);
        }
    }

    /**
     * <h2>合并结果取两者温度（或制作日期）的平均</h2>
     * 原版把源堆叠并进目标堆叠时最后都会走 {@code ItemStack#grow}（{@code AbstractContainerMenu#moveItemStackTo}、
     * {@code Inventory#add}、物品实体被捡起……）。上一步判定如果是靠温差放行的，那里已经记下了
     * "目标堆叠 + 该落到哪个温度（两者平均）"，这里把它写上去，合并结果就不会单方面带着目标槽原来的温度——
     * 既不白白继承热的那一份，也不被冷的那一份拖低。
     * <p>
     * 只认正数：{@code ItemStack#shrink} 内部也是 {@code grow(-n)}，那只是在减数量，不该顺手改温度。
     */
    @Inject(
            method = "grow",
            at = @At("HEAD")
    )
    private void wildfires$takeAverageValueOnMerge(int amount, CallbackInfo ci) {
        if (amount <= 0) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        HeatStacking.applyRecordedMergeAverage(self);
        FoodStacking.applyRecordedMergeAverage(self);
    }

    @ModifyVariable(
            method = "hurt",
            ordinal = 0,
            at = @At("HEAD"),
            argsOnly = true
    )
    private int modifyPAmount(int amount) {
        ItemStack itemStack = (ItemStack) (Object) this;
        int remove = 0;
        CompoundTag tag = itemStack.getTag();
        if (itemStack.isDamageableItem() && tag != null) {
            // 微裂 / 龟裂：淬火入水时温度过高留下的内应力。每次确定消耗耐久时各按 25% 概率多掉 1 点。
            // 两个标记互相独立、可以叠加：同时带着微裂和龟裂就是两次独立判定，一次最多多掉 2 点。
            if (tag.getBoolean("MicroCrack") && Math.random() < 0.25) {
                amount = amount + 1;
            }
            if (tag.getBoolean("Broken") && Math.random() < 0.25) {
                amount = amount + 1;
            }
            int quenching = tag.getInt("Quenching");
            if (quenching > 0 && Math.random() < (1 - 1.0 / (1 + quenching / 900.0))) {
                amount = 0;
            }
            int polish = tag.getInt("Polish");
            if (polish > 0 && !ItemDamageContext.isInstantBlockBreak()) {
                tag.putInt("Polish", polish - amount);
            }
            if (polish <= 0) {
                tag.remove("Polish");
            }
            int reinforcement = tag.getInt("Reinforcement");
            if (amount > 0 && tag.contains("Reinforcement")) {
                double reinforcementChance = (reinforcement * 5 - 20) / 100.0;
                if (reinforcementChance < 0) {
                    if (Math.random() < -reinforcementChance) {
                        amount++;
                    }
                } else if (Math.random() < reinforcementChance) {
                    amount = 0;
                }
            }
            int tempDuration = tag.getInt("TempDuration");
            if (tempDuration > 0) {
                if (tempDuration > amount) {
                    remove = amount;
                    tag.putInt("TempDuration", tempDuration - amount);
                } else {
                    remove = tempDuration;
                    tag.remove("TempDuration");
                }
            }
        }
        return amount - remove;
    }

}
