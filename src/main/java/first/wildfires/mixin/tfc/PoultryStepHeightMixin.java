package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.entities.livestock.OviparousAnimal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 剪刀右键家禽，切换「能不能跨 2 格」。
 *
 * <h2>问题到底是什么（老大 2026-10-27 报「禽类跳得非常高」）</h2>
 * 不是跳。TFC 的 {@code OviparousAnimal.createAttributes()} 是
 * <pre>
 * Chicken.createAttributes()                                    // 原版鸡，stepHeight 0.6
 *     .add(ForgeMod.STEP_HEIGHT_ADDITION.get(), 1.5)            // ← 这里
 * </pre>
 * Forge 的 {@code STEP_HEIGHT_ADDITION} 会加到实体的 {@code maxUpStep} 上，
 * 所以它们的实际台阶高度是 {@code 0.6 + 1.5 = 2.1} 格 —— 能<b>直接走</b>上两格高的墙和栅栏。
 * 跳跃本身没被改过（{@code JUMP_STRENGTH} 还是 0.42），所以「跳」是高估了，实际是「跨上去」。
 *
 * <p>野禽（grouse / pheasant / turkey / peafowl）走的是 {@code WingedPrey}，
 * 它的 {@code createAttributes()} 没有这一行，所以只有家禽（鸡 / 鸭 / 鹌鹑）有这毛病。
 *
 * <h2>这里做什么</h2>
 * 用剪刀右键一次，把 {@code STEP_HEIGHT_ADDITION} 压到 {@link #LOW_STEP}（0.6），
 * 总台阶高度变成 {@code 0.6 + 0.6 = 1.2} —— 只能上 1 格。
 * 再剪一次恢复原值 1.5。状态写进 NBT（{@link #NBT_KEY}），重进世界还在。
 *
 * <p>不消耗剪刀耐久（老大定的）。
 */
@Mixin(value = OviparousAnimal.class, remap = false)
public abstract class PoultryStepHeightMixin {

    /** NBT 键：true = 已经被剪过，只能上 1 格 */
    @Unique
    private static final String wildfires$NBT_KEY = "wildfires$step_clipped";

    /** 剪过之后的 STEP_HEIGHT_ADDITION。0.6 + 0.6 = 1.2，正好上 1 格不能上 2 格 */
    @Unique
    private static final double wildfires$LOW_STEP = 0.6;

    /** TFC 原值：{@code OviparousAnimal.createAttributes()} 里写的 1.5 */
    @Unique
    private static final double wildfires$HIGH_STEP = 1.5;

    /** 这个个体是否处于「剪过」状态 */
    @Unique
    private boolean wildfires$stepClipped;

    /**
     * 剪刀右键：切换状态，不往下传（不然会接着走拔羽毛那条路）。
     *
     * <p>只在服务端改属性并落盘；客户端返回 SUCCESS 让手臂有摆动。
     *
     * <p>★ 这三个方法都用 {@code remap = true} + 可读方法名，和同模组的
     * {@code PredatorMixin} 注入 {@code handleEntityEvent} 是同一套写法。
     * 反例：写 {@code m_6071_} + {@code remap = false} 会让注解处理器报
     * {@code Cannot find target method}（开发环境 TFC 是 srg 名，对不上），实测过。
     */
    @Inject(
            method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void wildfires$toggleStepHeight(Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (!player.getItemInHand(hand).is(Items.SHEARS)) {
            return;
        }

        OviparousAnimal self = (OviparousAnimal) (Object) this;
        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        wildfires$stepClipped = !wildfires$stepClipped;
        wildfires$applyStepHeight();
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /** 把当前状态同步到属性上，并让客户端重新读一次（hatch 之类的界面会看属性） */
    @Unique
    private void wildfires$applyStepHeight() {
        OviparousAnimal self = (OviparousAnimal) (Object) this;
        AttributeInstance attr = self.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attr == null) {
            return;
        }
        attr.setBaseValue(wildfires$stepClipped ? wildfires$LOW_STEP : wildfires$HIGH_STEP);
    }

    /** 存盘：把剪过的状态写进 NBT（{@code m_7380_} = addAdditionalSaveData） */
    @Inject(
            method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"),
            remap = true
    )
    private void wildfires$saveStepClipped(CompoundTag tag, CallbackInfo ci) {
        if (wildfires$stepClipped) {
            tag.putBoolean(wildfires$NBT_KEY, true);
        }
    }

    /**
     * 读盘（{@code m_7378_} = readAdditionalSaveData）：★ 必须在 TAIL 再套一次属性。
     *
     * <p>{@code createAttributes()} 只在实体构造时跑一次，之后读 NBT 不会重设属性，
     * 所以读完之后要自己把值推回去，否则重进世界会「记得剪过、但又能跨 2 格」。
     */
    @Inject(
            method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"),
            remap = true
    )
    private void wildfires$loadStepClipped(CompoundTag tag, CallbackInfo ci) {
        wildfires$stepClipped = tag.getBoolean(wildfires$NBT_KEY);
        wildfires$applyStepHeight();
    }
}
