package first.wildfires.textile.crop;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 预建的方块状态属性池。
 *
 * <p>★ 为什么需要这个类（2026-10-23 实测崩溃后才想明白）：
 * 原版 {@code CropBlock} 的构造函数里会调用 {@code getAgeProperty()} 并
 * {@code setValue(age, 0)}。但 Java 的初始化顺序是 <b>super() 先于</b>子类字段赋值，
 * 所以子类里 {@code private final IntegerProperty ageProperty} 那时还是 <b>null</b>，直接崩：
 * <pre>
 * IllegalArgumentException: Cannot set property IntegerProperty{name=age, ...}
 *   as it does not exist in Block{minecraft:air}
 * </pre>
 *
 * <p>原模组的解法：把 age 属性做成<b>静态数组</b>（类加载时建好），
 * {@code getAgeProperty(int max)} 从数组里取。这里照搬。
 *
 * <p>★ 2026-10-23 第二个坑：{@code IntegerProperty.create(name, 0, 0)} 会抛
 * <pre>IllegalArgumentException: Max value of age must be greater than min (0)</pre>
 * （原版要求 max &gt; min）。所以数组从 {@code 1} 起：
 * {@code AGES[i]} 对应值域 {@code 0..(i+1)}。作物最少也要 2 个状态，够用。
 */
public final class TextileBlockStateProperties
{
    private TextileBlockStateProperties()
    {
    }

    /** AGES[i] 的值域是 0..(i+1)。上界最小为 1（原版不允许 0..0）。 */
    private static final IntegerProperty[] AGES;

    static
    {
        AGES = new IntegerProperty[8];
        for (int i = 0; i < AGES.length; i++)
        {
            AGES[i] = IntegerProperty.create("age", 0, i + 1);
        }
    }

    /**
     * 取一个值域为 {@code 0..max} 的 age 属性。
     *
     * @param max 上界（必须 1..8）
     */
    public static IntegerProperty getAgeProperty(int max)
    {
        if (max < 1 || max > AGES.length)
        {
            throw new IllegalArgumentException(
                    "Age property max out of range: " + max + " (支持 1.." + AGES.length + ")");
        }
        return AGES[max - 1];
    }
}