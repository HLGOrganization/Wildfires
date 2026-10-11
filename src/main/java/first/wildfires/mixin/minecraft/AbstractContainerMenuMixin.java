package first.wildfires.mixin.minecraft;

import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <h2>容器界面开着的时候，物品温度也要继续同步</h2>
 * 群峦的热数据住在能力（capability）里，不在物品自己的 NBT 上，而原版的槽位同步判断
 * （{@code AbstractContainerMenu.synchronizeSlotToRemote}、{@code synchronizeCarriedToRemote}）
 * 用的是 {@code ItemStack.matches(ItemStack, ItemStack)}：物品和 NBT 之外，Forge 还让它比一遍
 * {@code areCapsCompatible}——群峦的热能力是<b>连时间戳一起</b>序列化的
 * （{@code HeatHandler.serializeNBT} 写 {@code heat} 与 {@code ticks}），
 * 所以物品一升温这个判断就会认为"变了"，同步方法体会照常跑完，客户端也拿得到新温度。
 * （早期那份"它只比数量与 NBT，所以永远认为没变"的分析是错的，温度同步本身不是问题所在。）
 * <p>
 * <b>"打开加热容器就不加热"的元凶是判定顺手改了温度：</b>
 * 早期版本的 {@code HeatStacking}（判定"温差在容差内也算同一种物品"的地方）会在比较时把两边温度
 * 压到较低值，而容器界面<b>每刻</b>都会拿槽里的真实堆叠和上次发出去的快照比一次
 * （{@code AbstractContainerMenu#broadcastChanges} → {@code ItemStack.matches}），
 * 于是每刻都把正在加热的物品按回快照的旧温度：设备当刻加的那点热量当场被抹掉，界面开着就永远热不上去；
 * 关掉界面不再比较，加热立刻恢复——玩家看到的就是"打开木炭炉，上面的金属暂停加热"。
 * {@code HeatStacking} 现在只判定、不写温度（"合并取两者平均"挪到真正合并的地方收尾，
 * 见 {@code first.wildfires.mixin.minecraft.ItemStackMixin} 与 {@code first.wildfires.mixin.tfc.FoodCapabilityMixin}），
 * 那才是修复本身。下面这道判断留着的用处是兜底与省流量。
 * <p>
 * 它做的事：原版说"没变"时，再问一次温度有没有真的走远
 * （≥ {@link #WILDFIRES$SYNC_TEMPERATURE_STEP} °C），走远了就把结论改成"变了"。
 * 后面全部照旧走原版加群峦自己的路径（复制快照 → 分享标签会把 {@code tfc:heat} 一起写进包），
 * 这里不需要额外记任何状态：{@code ItemStack.copy()} 会连能力一起序列化，
 * 所以 {@code remoteSlots} 与 {@code remoteCarried} 里存的那份"上次发出去的副本"本来就带着上次发出去的温度。
 * <p>
 * <b>流量</b>：升温时每个带热数据的槽每刻一个包——物品在升温，能力数据就每刻都在变，这与"原版 + 群峦"
 * 本来的行为一致；降温时不发，因为群峦的降温是"按存下来的时间戳现算"的，客户端手里那份会以同样的速度
 * 往下走，两边读数一直相等，不需要任何包。
 * <p>
 * <b>为什么不改全局的 {@code isSameItemSameTags}</b>：那会让温度不同的物品在任何地方都被判成同一种，
 * 物品栏、合成、机器全受影响，还会把"温差在容差内可以合并"那条规则（{@code HeatStacking}）一起搞乱。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    /**
     * 温度差别到这个程度才值得发一个包。取 1 °C：显示上够细，流量上够省。
     */
    @Unique
    private static final float WILDFIRES$SYNC_TEMPERATURE_STEP = 1.0F;

    /**
     * 原版问"这两个堆叠一样吗"。一样就返回真，整个同步方法会提前收工。
     * 这里在"按原版确实一样"的前提下补一句："温度也真的没变吗"——温度变了就返回假，
     * 让原版照它自己的流程走一遍，群峦的强制发送与分享标签会顺路把新温度带到客户端。
     * <p>
     * 注意这里是<b>实例方法</b>：{@code @Redirect} 的处理器的 static 必须与<b>被注入的那个方法</b>
     * （{@code synchronizeSlotToRemote}，实例方法）一致，而不是与被重定向的调用（{@code ItemStack.matches}，静态）
     * 一致。写成静态会在启动时直接崩：{@code 'static' modifier of handler method does not match target}。
     * 参数表才跟着被重定向的调用走——静态调用不额外传接收者，所以只有这两个参数。
     * 群峦自己的 {@code net.dries007.tfc.mixin.AbstractContainerMenuMixin} 也是这个规矩。
     */
    @Redirect(
            method = {"synchronizeSlotToRemote", "synchronizeCarriedToRemote"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
            )
    )
    private boolean wildfires$syncWhenTemperatureDiffers(ItemStack lastSent, ItemStack current) {
        if (!ItemStack.matches(lastSent, current)) {
            // 原版本就认为不一样，保持原样
            return false;
        }
        return !wildfires$temperatureDrifted(lastSent, current);
    }

    /**
     * @param lastSent 上次发出去的那份快照，带能力数据
     * @param current  服务端此刻的真实堆叠
     * @return 两边温度是否已经差到一个包值得发
     */
    @Unique
    private static boolean wildfires$temperatureDrifted(ItemStack lastSent, ItemStack current) {
        IHeat lastHeat = HeatCapability.get(lastSent);
        IHeat currentHeat = HeatCapability.get(current);
        if (lastHeat == null || currentHeat == null) {
            // 有一边根本没有热数据，那就没有"温度变了"这回事
            return false;
        }
        return Math.abs(currentHeat.getTemperature() - lastHeat.getTemperature()) >= WILDFIRES$SYNC_TEMPERATURE_STEP;
    }
}
