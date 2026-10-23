package first.wildfires.textile.crop;

import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.dries007.tfc.util.calendar.ICalendarTickable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Textile 作物的方块实体。
 *
 * <p>从 textile-2.2.3 的 {@code TextileCropBlockEntity} <b>逐字段</b>移植。
 * 它不继承 TFC 的 {@code CropBlockEntity}，而是继承 {@link TFCBlockEntity} +
 * {@link ICalendarTickable}，自己存 5 个字段。
 *
 * <p>★ NBT 键名是 2026-10-23 从 {@code loadAdditional} 字节码<b>逐条抄下来的</b>，
 * 不是猜的（昨天猜错过：漏了 {@code tick} 字段）：
 * <pre>
 * growth         → "growth"          (float)
 * yield          → "yield"           (float)
 * expiry         → "expiry"          (float)
 * lastUpdateTick → "tick"            (long)   ← 名字和字段名不一样！
 * lastGrowthTick → "lastGrowthTick"  (long)
 * </pre>
 *
 * <p>★ 生长不在这个类里算：{@link #onCalendarUpdate} 只是把调用<b>委托回</b>方块的
 * {@link ITextileCropBlock#growthTick}，真正的公式在 {@code TextileCropHelpers}。
 */
public class TextileCropBlockEntity extends TFCBlockEntity implements ICalendarTickable
{
    /** 原模组 {@code lastUpdateTick} 的初值（不是 0）。 */
    private static final long UNSET_TICK = -2147483648L;

    private float growth;
    private float yield;
    private float expiry;
    private long lastUpdateTick;
    private long lastGrowthTick;

    public TextileCropBlockEntity(BlockPos pos, BlockState state)
    {
        this(TextileBlockEntities.CROP.get(), pos, state);
    }

    public TextileCropBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
        this.growth = 0f;
        this.yield = 0f;
        this.expiry = 0f;
        this.lastUpdateTick = UNSET_TICK;
        this.lastGrowthTick = 0L;
    }

    /** 常规 tick。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop)
    {
        crop.checkForCalendarUpdate();
    }

    /**
     * 双高作物专用 tick：<b>只有下半格</b>才检查日历更新。
     *
     * <p>字节码实测：{@code if (state.getValue(PART) == Part.BOTTOM) crop.checkForCalendarUpdate();}
     */
    public static void serverTickBottomPartOnly(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop)
    {
        if (state.getValue(TextileDoubleCropBlock.PART) == TextileDoubleCropBlock.Part.BOTTOM)
        {
            crop.checkForCalendarUpdate();
        }
    }

    @Override
    public void onCalendarUpdate(long deltaTick)
    {
        assert level != null;
        final BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof ITextileCropBlock cropBlock)
        {
            cropBlock.growthTick(level, worldPosition, state, this);
        }
    }

    public float getGrowth()
    {
        return growth;
    }

    public void setGrowth(float growth)
    {
        this.growth = growth;
    }

    public float getYield()
    {
        return yield;
    }

    public void setYield(float yield)
    {
        this.yield = yield;
    }

    public float getExpiry()
    {
        return expiry;
    }

    public void setExpiry(float expiry)
    {
        this.expiry = expiry;
    }

    public long getLastGrowthTick()
    {
        return lastGrowthTick;
    }

    public void setLastGrowthTick(long tick)
    {
        this.lastGrowthTick = tick;
    }

    /** {@link ICalendarTickable} 要求：存"上次日历更新时刻"，用独立的 lastUpdateTick 字段。 */
    @Override
    public long getLastCalendarUpdateTick()
    {
        return lastUpdateTick;
    }

    @Override
    public void setLastCalendarUpdateTick(long tick)
    {
        this.lastUpdateTick = tick;
    }

    @Override
    protected void loadAdditional(CompoundTag tag)
    {
        // 键名逐条照抄原模组 loadAdditional 字节码
        this.growth = tag.getFloat("growth");
        this.yield = tag.getFloat("yield");
        this.expiry = tag.getFloat("expiry");
        this.lastUpdateTick = tag.getLong("tick");
        this.lastGrowthTick = tag.getLong("lastGrowthTick");
        super.loadAdditional(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        tag.putFloat("growth", growth);
        tag.putFloat("yield", yield);
        tag.putFloat("expiry", expiry);
        tag.putLong("tick", lastUpdateTick);
        tag.putLong("lastGrowthTick", lastGrowthTick);
        super.saveAdditional(tag);
    }
}
