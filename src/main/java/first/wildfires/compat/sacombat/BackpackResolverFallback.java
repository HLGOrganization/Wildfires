package first.wildfires.compat.sacombat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 从 {@code DynamicBackpackItemHandler} 里取出玩家，再问一遍「身上有没有背包」。
 *
 * <h2>为什么需要这个类</h2>
 * {@code DynamicBackpackItemHandler} 只认 Curios 的 {@code back} 槽
 * （{@code BackpackResolver.resolveEquipped}），背包拿在手上时它判定为「没有背包」，
 * 于是 42 个槽位全部关闭，但面板 UI 照样显示格子 ⇒ 玩家放进去的东西被静默吞掉。
 * 详见 {@code BackpackFallbackMixin} 的类注释（附实测数据）。
 *
 * <h2>为什么用反射</h2>
 * 与同包的 {@link SatchelSizeRules} 同一个理由：Survivors Arsenal 是可选的编译期依赖，
 * 开发包里可能整个不存在。这里一个该模组的类型都不引用，
 * 找不到就返回 {@link ItemStack#EMPTY}（fail-open，退回上游行为）。
 *
 * <p>★ mixin 类型<b>绝不能</b>在这里强转 —— 见 {@code skill-mc-mixin-inject} 的已知坑：
 * 被改写的 mixin 类不能当普通类加载，{@code checkcast} 会抛 {@code NoClassDefFoundError}，
 * 而那是 {@code Error}，{@code catch (ReflectiveOperationException)} 接不住，整条链会静默失败。
 * 所以参数类型写成 {@code Object}，只按名字反射。
 */
public final class BackpackResolverFallback {

    /** {@code DynamicBackpackItemHandler.player} */
    private static final String PLAYER_FIELD = "player";

    /** {@code BackpackResolver.resolve(Player)} —— 主手 / 副手 / 背包栏 / Curios 都查 */
    private static final String RESOLVER_CLASS = "com.ogaba.sa_survival.item.backpack.BackpackResolver";

    private BackpackResolverFallback() {
    }

    /**
     * 手上或背包栏里有没有背包。
     *
     * @param handler {@code DynamicBackpackItemHandler} 实例。参数刻意是 {@code Object}，
     *                避免引用该模组的类型。
     * @return 找到的背包栈；没有则 {@link ItemStack#EMPTY}
     */
    public static ItemStack resolveWildfiresHeld(Object handler) {
        Player player = playerOf(handler);
        if (player == null) {
            return ItemStack.EMPTY;
        }
        return resolveFor(player);
    }

    /** 用 {@code BackpackResolver.resolve} 查一遍玩家身上的背包。 */
    public static ItemStack resolveFor(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        try {
            Class<?> resolver = Class.forName(RESOLVER_CLASS);
            Method resolve = resolver.getMethod("resolve", Player.class);
            Object found = resolve.invoke(null, player);
            if (found == null) {
                return ItemStack.EMPTY;
            }
            // Found 是 record：stack() 给出 ItemStack
            Method stack = found.getClass().getMethod("stack");
            Object value = stack.invoke(found);
            return value instanceof ItemStack items ? items : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError e) {
            // 模组不在 / 版本换了名字：退回上游行为，不抛
            return ItemStack.EMPTY;
        }
    }

    /**
     * 这一格现在能不能真的存东西。
     *
     * <h2>为什么需要它</h2>
     * 上游 {@code DynamicBackpackItemHandler.setStackInSlot} 的写法是：
     * <pre>
     * BackpackInventory inv = createInventoryForSlot(slot);
     * if (inv != null) { inv.setStackInSlot(slot, stack); }
     * // inv == null ⇒ 静默什么都不做，既不报错也不归还物品
     * </pre>
     * 而原版点击流程已经先把物品从光标上拿走了
     * （{@code AbstractContainerMenu#doClick} → {@code Slot#setByPlayer} → {@code carried = EMPTY}），
     * 所以「写不进去」就等于<b>物品凭空消失</b>。
     *
     * <p>而 {@code BackpackSlot#mayPlace} 只拒绝嵌套背包，<b>不检查槽位是否启用</b>，
     * 于是这条静默丢失路径对玩家完全敞开。
     *
     * <p>这里在 {@code mayPlace} 里先问一句「这格能用吗」，不能用就直接拒绝放置，
     * 物品留在光标上 —— 玩家看得见，不会丢。
     *
     * @param slot 背包槽位实例（{@code BackpackSlot} / {@code InventoryBackpackSlot}）。
     *             参数是 {@code Object}，不引用该模组类型。
     * @return {@code false} 只在<b>确认</b>这一格被禁用时；任何一环取不到都返回 {@code true}
     *         （fail-open，退回上游原来的行为）
     */
    public static boolean slotUsable(Object slot) {
        try {
            Object handler = callNoArg(slot, "getItemHandler");
            if (handler == null
                    || !handler.getClass().getName().endsWith("DynamicBackpackItemHandler")) {
                // 背包全屏菜单用的是 BackpackInventory（普通 ItemStackHandler），没有启用概念
                return true;
            }
            int index = intField(slot, "index");
            if (index < 0) {
                return true;
            }
            Object enabled = callWithInt(handler, "isSlotEnabled", index);
            return !(enabled instanceof Boolean flag) || flag;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** 无参方法调用，失败返回 {@code null}。 */
    private static Object callNoArg(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    /** 单 int 参数方法调用，失败返回 {@code null}。 */
    private static Object callWithInt(Object target, String name, int arg) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(name, int.class);
        return method.invoke(target, arg);
    }

    /** 读 int 字段（{@code SlotItemHandler.index}），取不到返回 -1。 */
    private static int intField(Object target, String name) {
        if (target == null) {
            return -1;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (NoSuchFieldException ignored) {
                // 往上一层找
            } catch (ReflectiveOperationException | RuntimeException e) {
                return -1;
            }
        }
        return -1;
    }

    /** 读 {@code handler.player} 字段。 */
    private static Player playerOf(Object handler) {
        if (handler == null) {
            return null;
        }
        for (Class<?> type = handler.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(PLAYER_FIELD);
                field.setAccessible(true);
                Object value = field.get(handler);
                return value instanceof Player player ? player : null;
            } catch (NoSuchFieldException ignored) {
                // 往上一层找
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
