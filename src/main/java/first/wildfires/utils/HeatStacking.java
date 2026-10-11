package first.wildfires.utils;

import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.items.MoldItem;
import net.dries007.tfc.common.items.VesselItem;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * <h2>温度接近的两个堆叠也算"同一种物品"</h2>
 * 群峦的热数据住在能力（capability）里，不在物品自己的 NBT 上，而原版判定"能不能叠"时要连能力一起比：
 * {@code ItemStack.areCapsCompatible} 比的是能力序列化出来的整个 {@code CompoundTag}，只要温度差一度就
 * 不相等。所以现实里两把温度不同的热锭永远叠不起来，这正是要解决的问题。
 * <p>
 * 规则：<b>温差不超过"这份物品 10 秒内自己会变化的温度量"的同一物品允许合并，合并后的温度取两者平均。</b>
 * 容差不写死成某个固定度数，而是拿群峦自己的降温公式算出来（见 {@link #tenSecondDrift(float, float)}）：
 * 计算结果与本包配置有关，当前 {@code itemCoolingModifier = 0.2}，也就是 10 秒的降温量
 * {@code = 200 刻 × 0.2 ÷ 热容 = 40 ÷ 热容} °C——铁锭（热容 2.857）约 14 °C、铜锭（2.0）约 20 °C、
 * 玻璃模具（0.2）约 200 °C（同时受"不能降到 0 以下"限制）。
 * 判定对称：两个温度各算一份、取较大的那个，保证 {@code isSameItemSameTags(a, b)} 与 {@code (b, a)} 答案一致。
 * <h2>判定必须无副作用（这一条是踩过坑才写下来的）</h2>
 * 一开始为了复用 Forge 自己的 {@code areCapsCompatible}，是<b>把两边温度临时改成同一个值再判</b>，
 * 顺手就让温度落在较小值上。问题是这个函数在很多地方只是"问一句、不合并"：最要命的是
 * {@code AbstractContainerMenu#broadcastChanges}——容器界面每刻都会拿槽里的<b>真实堆叠</b>和
 * {@code remoteSlots} 里上次发出去的<b>快照</b>比一次（{@code ItemStack.matches}）。
 * 于是每刻都会把正在加热的物品的温度按回快照那个旧温度：木炭炉每刻加的那零点几度当场被抹掉，
 * <b>界面开着就永远加热不上去，关掉界面（不再比较）才恢复</b>。表现就是"打开加热容器，上面的金属暂停加热"。
 * <p>
 * 所以现在判定完全只读：要比较"除了温度以外还差什么"，就复制两份副本、把副本的温度调成同一个值，
 * 再问 {@code areCapsCompatible}；真实堆叠的温度一个都不碰。而"合并取平均"改到真正合并的地方去做——
 * 群峦设备走 {@code FoodCapability#mergeItemStacks}（{@link #takeAverage}），
 * 原版走 {@code ItemStack#grow}（{@link #applyRecordedMergeAverage}，用判定时记下的目标与平均温度）。
 * <h2>开销</h2>
 * 判定开头几步都是普通字段比较，只有"温度确实不同、且温差在容差内"时才会走到两份副本加能力序列化；
 * 容差本身只是照降温公式做两三次浮点运算。反而因为容差比原来的固定值更紧，
 * 容器界面每刻那次比较常常在容差这一步就否掉了，比之前更省。
 */
public final class HeatStacking {

    /**
     * 容差窗口：10 秒 = 200 刻。容差不是"多少度"，而是"这份物品在这么久里自己会变多少度"。
     */
    public static final long TOLERANCE_WINDOW_TICKS = 200L;

    /**
     * 判定通过时记下的"下一次真正合并的目标堆叠"与它该落到的平均温度。
     * 用 {@link ThreadLocal}：客户端与服务端的容器菜单会在各自线程里跑同一段判定。
     */
    private static final ThreadLocal<ItemStack> MERGE_TARGET = new ThreadLocal<>();

    private static final ThreadLocal<Float> MERGE_TEMPERATURE = new ThreadLocal<>();

    /**
     * 这些物品身上有<b>两套</b>热数据：它们自己那份（模具/小罐的能力，热容按金属/内容物算），以及群峦给
     * 普通物品挂的那份通用物品热（在它们身上恒为 {@code {heat:0, ticks:0}}）。Forge 取能力时按提供者顺序
     * 返回第一个，读到哪一份并不由我们决定，所以"温度接近就允许合并"这套设定在它们身上不成立：合并出来的
     * 那一份可能读成 0 度，拆开又各算各的，看起来就是温度凭空消失、两半分开计算。
     *
     * <p>对它们一律不做温度容差、也不改写温度，完全交回原版行为——只有数据（含温度）完全一样才叠。
     */
    private static boolean hasTwoSetsOfHeat(ItemStack stack) {
        return stack.getItem() instanceof MoldItem || stack.getItem() instanceof VesselItem;
    }

    private HeatStacking() {
    }

    /**
     * 在原版判定已经给出"不能叠"之后，判断这两个堆叠是不是只差一点点温度。
     * <b>只读</b>：不会改动任何一个参数的温度。
     *
     * @return {@code true} 表示"按新规则它们算同一种物品"
     */
    public static boolean tolerateSameStack(ItemStack a, ItemStack b) {
        if (a == b || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (hasTwoSetsOfHeat(a) || hasTwoSetsOfHeat(b)) {
            return false;
        }
        if (!ItemStack.isSameItem(a, b)) {
            return false;
        }
        if (!Objects.equals(a.getTag(), b.getTag())) {
            return false;
        }

        IHeat heatA = HeatCapability.get(a);
        IHeat heatB = HeatCapability.get(b);
        if (heatA == null || heatB == null) {
            return false;
        }

        float temperatureA = heatA.getTemperature();
        float temperatureB = heatB.getTemperature();
        // 温度本来就一样却仍然不兼容，说明差的是别的能力，这种情况一律交回原版结论
        if (temperatureA == temperatureB) {
            return false;
        }
        if (Math.abs(temperatureA - temperatureB) > tolerance(heatA, heatB)) {
            return false;
        }

        float average = (temperatureA + temperatureB) * 0.5F;
        if (!onlyTemperatureDiffers(a, b, average)) {
            return false;
        }

        // 判定成立，但这里绝不写温度：只是把"真合并时该落在哪"记下来，交给 ItemStack#grow 收尾。
        MERGE_TARGET.set(a);
        MERGE_TEMPERATURE.set(average);
        return true;
    }

    /**
     * 用两份<b>副本</b>确认"除了温度，其余能力完全一致"。
     * 副本的温度被调成同一个值之后，Forge 的 {@code areCapsCompatible} 只会在别的能力
     * （食物的制作日期、模具里的金属……）真的对不上时才返回假。真实堆叠不受影响。
     * <p>
     * 顺带一提，这里为什么必须"调成同一个值"而不是直接把副本拿去比：群峦的热数据是把
     * {@code heat} 和 {@code ticks}（上次更新的日历刻）一起序列化的，两份副本的时间戳天然不同，
     * 不归一的话 {@code areCapsCompatible} 永远为假。
     * <p>
     * 调用前已经确认过两边都有热数据，所以副本一定还能拿到热数据——{@code ItemStack#copy} 会把能力一起序列化。
     */
    private static boolean onlyTemperatureDiffers(ItemStack a, ItemStack b, float temperature) {
        ItemStack probeA = a.copy();
        ItemStack probeB = b.copy();
        IHeat heatA = HeatCapability.get(probeA);
        IHeat heatB = HeatCapability.get(probeB);
        if (heatA == null || heatB == null) {
            return false;
        }
        heatA.setTemperature(temperature);
        heatB.setTemperature(temperature);
        return probeA.areCapsCompatible(probeB);
    }

    /**
     * 合并真正发生的地方收尾：原版把源堆叠的个数并进目标堆叠时走 {@code ItemStack#grow}，
     * 合并结果会带着目标槽原本的温度。这里把它改成判定时记下的平均温度，并清掉记录。
     * <p>
     * 只有"记录里的那个堆叠自己"、而且它当前温度离平均温度不超过容差时才会写：
     * 记录是上一次判定留下的，若中间隔了很久、物品已经烧到很远，就不该再回头动它。
     */
    public static void applyRecordedMergeAverage(ItemStack target) {
        ItemStack recorded = MERGE_TARGET.get();
        Float average = MERGE_TEMPERATURE.get();
        if (recorded == null || average == null) {
            return;
        }
        MERGE_TARGET.remove();
        MERGE_TEMPERATURE.remove();
        if (recorded != target) {
            return;
        }
        applyAverage(target, average);
    }

    /**
     * 合并完成之后，把合并结果改成两个温度的平均值。给走"先比后并"的合并实现用：
     * 群峦设备是拿堆叠的副本去判定的，真正的堆叠并没有被判定碰到，
     * 所以合并结果会带着目标槽原本的温度。
     *
     * @param merged       合并后的堆叠，带热能力时才会被改写
     * @param temperatureA 合并前目标槽的温度
     * @param temperatureB 合并前放进去那一堆的温度
     */
    public static void takeAverage(ItemStack merged, float temperatureA, float temperatureB) {
        if (merged == null || merged.isEmpty() || hasTwoSetsOfHeat(merged)) {
            return;
        }
        float maximum = Math.max(temperatureA, temperatureB);
        if (maximum <= 0.0F) {
            // 两边都是冷的，没有热数据需要改写
            return;
        }
        IHeat heat = HeatCapability.get(merged);
        if (heat == null) {
            return;
        }
        if (maximum - Math.min(temperatureA, temperatureB) > tenSecondDrift(maximum, heat.getHeatCapacity())) {
            // 本来就不该合并的两个堆叠，保守起见不动它们的温度
            return;
        }
        heat.setTemperature((temperatureA + temperatureB) * 0.5F);
    }

    /**
     * 把堆叠的温度改成平均温度，但只在它当前温度离平均值不超过容差时才动手
     * （记录可能已经过期，比如判定之后物品又被加热了很久）。
     * <p>
     * 唯一的例外是"读到 0 度、平均值却是热的"：模具、小罐这类物品的热能力是挂在堆叠上的<b>活对象</b>，
     * 合并出来的那一份可能是刚建出来的空壳（构造出来的温度就是 0），而堆叠自己存的能力 NBT 里温度还是
     * 好的——把这一堆拆开就会立刻恢复，正是这个原因。所以这里的 0 不代表"它后来凉了"，不能当作记录
     * 过期的证据，否则合并结果会一直停在 0 度。
     */
    private static void applyAverage(ItemStack stack, float average) {
        if (hasTwoSetsOfHeat(stack)) {
            return;
        }
        IHeat heat = HeatCapability.get(stack);
        if (heat == null) {
            return;
        }
        float current = heat.getTemperature();
        if (current <= 0.0F && average > 0.0F) {
            heat.setTemperature(average);
            return;
        }
        if (Math.abs(current - average) > tenSecondDrift(Math.max(current, average), heat.getHeatCapacity())) {
            return;
        }
        heat.setTemperature(average);
    }

    private static float tolerance(IHeat heatA, IHeat heatB) {
        return Math.max(tenSecondDrift(heatA), tenSecondDrift(heatB));
    }

    private static float tenSecondDrift(IHeat heat) {
        return tenSecondDrift(heat.getTemperature(), heat.getHeatCapacity());
    }

    /**
     * 这份热数据在 10 秒里会变化多少度：{@code temperature - adjustTemp(temperature, capacity, 200)}。
     * <p>
     * 群峦自己的降温是 {@code max(0, 温度 - 过去刻数 × itemCoolingModifier ÷ 热容)}，
     * 本包 {@code itemCoolingModifier = 0.2}，所以结果是 {@code 40 ÷ 热容} °C，
     * 并且不会超过物品当前的温度（冷的东西本来就不会变多少）。热容越大降得越慢、容差越小，
     * 与"热容大的东西温度更稳，应该要求更接近才能算同温"是同一个方向。
     */
    private static float tenSecondDrift(float temperature, float heatCapacity) {
        if (!(temperature > 0.0F)) {
            return 0.0F;
        }
        if (!(heatCapacity > 0.0F)) {
            // 热容为 0 等于瞬间与环境同温，任何温差下一秒就没了
            return temperature;
        }
        float cooled = HeatCapability.adjustTemp(temperature, heatCapacity, TOLERANCE_WINDOW_TICKS);
        return Math.max(0.0F, temperature - cooled);
    }
}
