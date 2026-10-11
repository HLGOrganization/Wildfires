package first.wildfires.utils;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * <h2>剩余保质期差一天以内也算"同一种食物"</h2>
 * 和 {@link HeatStacking} 是同一件事的另一半：群峦把食物的制作日期也存在能力里，原版判定要
 * {@code areCapsCompatible} 连它一起比，于是同一块肉只要做出来的时刻差一点就永远叠不起来。
 * 规则：<b>除了制作日期以外完全相同的两份食物，制作日期相差不超过一天（24000 刻）就允许合并，
 * 合并后的制作日期取两者按数量加权的平均</b>（数量加权和群峦自己 {@code mergeItemStacks} 的口径一致）。
 * <p>
 * "除了制作日期以外完全相同"不自己比：直接用群峦自己的
 * {@link FoodCapability#areStacksStackableExceptCreationDate}——那正是它设备内部合并用的判据，
 * 食物数据、口味、腐败修正全都比到了，只有日期被排除在外。
 * <p>
 * 日期差换算成剩余保质期：同一份食物（数据、口味一致）的保质期长度相同，所以
 * {@code 剩余时间之差 = 制作日期之差}，比较日期就是在比较"还能放多久"。
 * <h2>判定必须无副作用</h2>
 * 和温度那边同一条教训：这个判定会被容器界面每刻问一次（{@code AbstractContainerMenu#broadcastChanges}
 * → {@code ItemStack#matches}），所以这里只记录"真合并时该落在哪个日期"，日期一律等真正合并
 * （原版 {@code ItemStack#grow}，见 {@code first.wildfires.mixin.minecraft.ItemStackMixin}）再写。
 */
public final class FoodStacking {

    /** 容差：一天。群峦的日历一天固定 24000 刻。 */
    public static final long TOLERANCE_TICKS = 24000L;

    /**
     * 判定通过时记下的"下一次真正合并的目标堆叠"与它该落到的制作日期。
     * 用 {@link ThreadLocal}，理由和 {@link HeatStacking} 一样：客户端与服务端的容器菜单各跑一份。
     */
    private static final ThreadLocal<ItemStack> MERGE_TARGET = new ThreadLocal<>();

    private static final ThreadLocal<Long> MERGE_DATE = new ThreadLocal<>();

    /**
     * 防重入闸。
     *
     * <p>群峦自己的 {@link FoodCapability#areStacksStackableExceptCreationDate} <b>内部会调
     * {@code ItemHandlerHelper#canItemStacksStack}</b>（把日期归一之后再问一次能不能叠），而那个方法
     * 正是问我们的地方（{@code ItemHandlerHelperMixin}）。所以不设闸就是无限递归：实测表现为服务端
     * tick 物品处理器时 {@code StackOverflowError}。
     *
     * <p>重入时一律回答"不算同一种"，也就是把结论交回群峦自己那一次判定——它要的答案本来就不是
     * "温差/日期差在容差内"，而是"除了日期以外是否完全一致"。
     */
    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FoodStacking() {
    }

    /**
     * 在原版判定已经给出"不能叠"之后，判断这两份食物是不是只差一点制作日期。
     * <b>只读</b>：不会改动任何一个参数的日期。
     *
     * @return {@code true} 表示"按新规则它们算同一种物品"
     */
    public static boolean tolerateSameFood(ItemStack a, ItemStack b) {
        if (CHECKING.get()) {
            return false;
        }
        CHECKING.set(Boolean.TRUE);
        try {
            return tolerateSameFoodUnchecked(a, b);
        } finally {
            CHECKING.set(Boolean.FALSE);
        }
    }

    private static boolean tolerateSameFoodUnchecked(ItemStack a, ItemStack b) {
        if (a == b || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (!ItemStack.isSameItem(a, b)) {
            return false;
        }
        if (!Objects.equals(a.getTag(), b.getTag())) {
            return false;
        }
        if (!FoodCapability.areStacksStackableExceptCreationDate(a, b)) {
            return false;
        }

        IFood foodA = FoodCapability.get(a);
        IFood foodB = FoodCapability.get(b);
        if (foodA == null || foodB == null) {
            return false;
        }

        long dateA = foodA.getCreationDate();
        long dateB = foodB.getCreationDate();
        // 日期本来就一样却仍然叠不上，说明差的是别的数据，这种情况一律交回原版结论
        if (dateA == dateB) {
            return false;
        }
        if (Math.abs(dateA - dateB) > TOLERANCE_TICKS) {
            return false;
        }

        // 判定成立，但这里绝不写日期：只把"真合并时该落在哪"记下来，交给 ItemStack#grow 收尾。
        MERGE_TARGET.set(a);
        MERGE_DATE.set(weightedAverage(dateA, a.getCount(), dateB, b.getCount()));
        return true;
    }

    /**
     * 合并真正发生的地方收尾：原版把源堆叠并进目标堆叠时走 {@code ItemStack#grow}，合并结果会带着
     * 目标槽原本的制作日期。这里把它改成判定时记下的加权平均日期，并清掉记录。
     * <p>
     * 只有"记录里的那个堆叠自己"、而且它当前日期离平均日期不超过容差时才会写：
     * 记录是上一次判定留下的，若中间隔了很久、这一堆已经被并过别的日期，就不该再回头动它。
     */
    public static void applyRecordedMergeAverage(ItemStack target) {
        ItemStack recorded = MERGE_TARGET.get();
        Long date = MERGE_DATE.get();
        if (recorded == null || date == null) {
            return;
        }
        MERGE_TARGET.remove();
        MERGE_DATE.remove();
        if (recorded != target) {
            return;
        }

        IFood food = FoodCapability.get(target);
        if (food == null) {
            return;
        }
        if (Math.abs(food.getCreationDate() - date) > TOLERANCE_TICKS) {
            return;
        }
        food.setCreationDate(date);
    }

    /** 按数量加权的平均制作日期：{@code (dateA × countA + dateB × countB) / (countA + countB)}。 */
    private static long weightedAverage(long dateA, int countA, long dateB, int countB) {
        long total = (long) countA + countB;
        if (total <= 0L) {
            return (dateA + dateB) / 2L;
        }
        return (dateA * countA + dateB * countB) / total;
    }
}
