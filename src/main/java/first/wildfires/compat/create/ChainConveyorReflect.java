package first.wildfires.compat.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射调用 {@code ChainConveyorBlockEntity} 上由 mixin 织入的 {@code wildfires$*} 方法。
 *
 * <h2>★ 2026-10-24：为什么必须用反射</h2>
 * 原先 {@link ChainConveyorWrenchHelper} 里写的是：
 * <pre>
 * final ChainConveyorBlockEntityChainTypeMixin self =
 *         (ChainConveyorBlockEntityChainTypeMixin) (Object) be;
 * self.wildfires$setChainType(target, chain);
 * </pre>
 * 编译能过、mixin 也确实织入了目标类（运行时能看到
 * {@code wildfires$getChainType}/{@code setChainType}/{@code clearChainType} 三个方法），
 * 但<b>一旦在普通类里直接引用 mixin 类型，运行期就炸</b>：
 * <pre>
 * NoClassDefFoundError: first.wildfires.mixin.create.ChainConveyorBlockEntityChainTypeMixin is invalid
 * </pre>
 * 因为 mixin 类在运行期被 Mixin 框架改写后<b>不能当普通类加载</b>，
 * 对它的 {@code checkcast} 会连带触发类加载，于是抛错。
 *
 * <p>而且这个错误只是 {@code NoClassDefFoundError}（不是 {@code ReflectiveOperationException}），
 * 之前的 {@code catch} 根本接不住 ⇒ 整条调用链静默失败 ⇒
 * 「记录写了但查不到」「拆链掉原版锁链」全都由此而来。
 *
 * <p>现在改成按<b>方法名</b>反射调用目标类上的方法，不引用任何 mixin 类型。
 */
public final class ChainConveyorReflect
{
    private ChainConveyorReflect()
    {
    }

    /** 方法名缓存：类 → (方法名 → Method)，避免每次调用都查一遍 */
    private static final Map<Class<?>, Map<String, Method>> CACHE = new ConcurrentHashMap<>();

    private static final String M_GET = "wildfires$getChainType";
    private static final String M_SET = "wildfires$setChainType";
    private static final String M_CLEAR = "wildfires$clearChainType";

    @Nullable
    private static Method find(Class<?> cls, String name)
    {
        return CACHE.computeIfAbsent(cls, c ->
        {
            final Map<String, Method> map = new ConcurrentHashMap<>();
            // 沿继承链找（@Unique 方法织在目标类自身，但保险起见向上找一层）
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            {
                for (final Method m : k.getDeclaredMethods())
                {
                    if (m.getName().equals(M_GET) || m.getName().equals(M_SET)
                            || m.getName().equals(M_CLEAR) || m.getName().equals("getConnectionsChain"))
                    {
                        m.setAccessible(true);
                        map.putIfAbsent(m.getName(), m);
                    }
                }
            }
            return map;
        }).get(name);
    }

    /** 木齿轮原有记录采用相对坐标，也能恢复旧连接的材质。 */
    public static ItemStack getNativeChainType(ChainConveyorBlockEntity be, BlockPos relative)
    {
        final Method method = find(be.getClass(), "getConnectionsChain");
        if (method != null)
        {
            try
            {
                final Object value = ((Map<?, ?>) method.invoke(be)).get(relative);
                if (value instanceof net.minecraft.world.level.ItemLike item)
                    return new ItemStack(item);
            }
            catch (ReflectiveOperationException ignored) { }
        }
        return ItemStack.EMPTY;
    }

    /** 取一条连接记录，没记录返回 {@link ItemStack#EMPTY} */
    public static ItemStack getChainType(ChainConveyorBlockEntity be, BlockPos target)
    {
        final Method m = find(be.getClass(), M_GET);
        if (m == null)
        {
            return ItemStack.EMPTY;
        }
        try
        {
            return (ItemStack) m.invoke(be, target);
        }
        catch (ReflectiveOperationException e)
        {
            return ItemStack.EMPTY;
        }
    }

    /** 写一条连接记录 */
    public static void setChainType(ChainConveyorBlockEntity be, BlockPos target, ItemStack stack)
    {
        final Method m = find(be.getClass(), M_SET);
        if (m == null)
        {
            return;
        }
        try
        {
            m.invoke(be, target, stack);
        }
        catch (ReflectiveOperationException ignored)
        {
            // 记录失败不影响主流程，只是拆链时退回原版锁链
        }
    }

    /** 清一条连接记录 */
    public static void clearChainType(ChainConveyorBlockEntity be, BlockPos target)
    {
        final Method m = find(be.getClass(), M_CLEAR);
        if (m == null)
        {
            return;
        }
        try
        {
            m.invoke(be, target);
        }
        catch (ReflectiveOperationException ignored)
        {
            // 同上
        }
    }
}
