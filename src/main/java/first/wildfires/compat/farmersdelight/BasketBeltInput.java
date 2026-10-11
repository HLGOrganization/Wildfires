package first.wildfires.compat.farmersdelight;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.block.entity.BasketBlockEntity;

/**
 * Stands in for the {@link DirectBeltInputBehaviour} a basket cannot carry.
 *
 * <p>Create keeps that behaviour on {@code SmartBlockEntity} subclasses only, and every lookup ends at
 * {@code BlockEntityBehaviour.get(world, pos, TYPE)} which rejects anything else. A Farmer's Delight
 * basket extends vanilla's {@code RandomizableContainerBlockEntity}, so the behaviour can never live
 * on it - {@code BasketBeltInputMixin} therefore hands Create one of these instead, built for the
 * basket's position.
 *
 * <p>Only the two methods Create calls on a target behaviour are meaningful here:
 * {@link #canInsertFromSide} (left at Create's default, "any side", the way a chest behaves) and the
 * insertion handler installed below, which inserts through the basket's item handler - the capability
 * {@code BasketItemHandler} attaches. The host block entity is null on purpose; the position it would
 * have reported is the one this instance was built with.
 */
public final class BasketBeltInput extends DirectBeltInputBehaviour {

    private final @Nullable Level level;
    private final BlockPos pos;

    private BasketBeltInput(BlockGetter reader, BlockPos pos) {
        // No host: nothing here may fall through to BlockEntityBehaviour's blockEntity field.
        super(null);
        this.level = reader instanceof Level asLevel ? asLevel : null;
        this.pos = pos;
        setInsertionHandler(this::insert);
    }

    /**
     * The behaviour Create should see at this position, or {@code null} when it holds anything other
     * than a basket - in which case the caller's own lookup decides as before.
     */
    public static @Nullable BasketBeltInput of(BlockGetter reader, BlockPos pos) {
        return reader.getBlockEntity(pos) instanceof BasketBlockEntity ? new BasketBeltInput(reader, pos) : null;
    }

    @Override
    public @Nullable Level getWorld() {
        return level;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    private ItemStack insert(TransportedItemStack transported, Direction side, boolean simulate) {
        BlockEntity basket = level == null ? null : level.getBlockEntity(pos);
        if (basket == null) {
            return transported.stack;
        }
        IItemHandler handler = basket.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        if (handler == null) {
            return transported.stack;
        }
        return ItemHandlerHelper.insertItemStacked(handler, transported.stack.copy(), simulate);
    }
}
