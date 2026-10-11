package first.wildfires.compat.tfc;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.BarrelBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * <h2>What quenching is</h2>
 * A hot tool head in water takes one point of {@code Quenching} per tick, and past {@value #CRACK_THRESHOLD}
 * points it starts taking the two crack marks the rest of the pack reads: the micro crack first, and the
 * break once a head that already carries a micro crack is left in longer. Both marks are permanent - nothing
 * in the pack takes them off - and the recipe modifiers carry them onto the finished tool, where they cost
 * extra durability ({@code first.wildfires.mixin.minecraft.ItemStackMixin}).
 *
 * <h2>Why the rules live here</h2>
 * Two things quench, and they see the world in different ways: a dropped item is an entity in a water block,
 * while a head put in a TFC barrel is an item in a block entity's inventory and never becomes an entity at
 * all. Keeping the value, the threshold, the water test and the heat gate in one place is what stops the two
 * from drifting apart.
 *
 * <h2>What counts as water, and as a head</h2>
 * Vanilla water and TFC's fresh water tags, either as a fluid state or as the fluid in a barrel's tank -
 * brine, wine and oil are not a quench. A head is anything carrying TFC heat at 60% of its working
 * temperature or more and tagged {@code kubejs:tool_head}, which is the same gate the dropped path uses.
 */
public final class Quenching {

    /** The value the crack marks start at, one point per tick in water. */
    public static final int CRACK_THRESHOLD = 325;

    private Quenching() {
    }

    /** Whether this fluid is water a hot head can be quenched in. */
    public static boolean isQuenchWater(Fluid fluid) {
        return fluid.is(FluidTags.WATER)
                || fluid.is(TFCTags.Fluids.ANY_FRESH_WATER)
                || fluid.is(TFCTags.Fluids.ANY_WATER);
    }

    /** Whether this fluid state is a source of that water; a flowing block is not a quench. */
    public static boolean isQuenchWater(FluidState fluid) {
        return fluid.isSource() && isQuenchWater(fluid.getType());
    }

    /** Whether this stack is hot enough, and of the kind that takes a quench, right now. */
    public static boolean canQuench(ItemStack stack) {
        IHeat heat = HeatCapability.get(stack);
        return heat != null
                && heat.getTemperature() >= heat.getWorkingTemperature() * 0.6
                && stack.getTags().anyMatch(tag -> tag.location().toString().equals("kubejs:tool_head"));
    }

    /**
     * Quenches this stack once: one point of the value, and the crack marks past the threshold. The stacks
     * handed in are the ones the caller holds, so the change is in place; whoever owns them has to mark
     * itself changed for it to be saved.
     */
    public static void quench(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("Quenching", tag.getInt("Quenching") + 1);
        if (tag.getInt("Quenching") > CRACK_THRESHOLD) {
            if (!tag.getBoolean("MicroCrack")) {
                tag.putBoolean("MicroCrack", true);
            } else if (!tag.getBoolean("Broken")) {
                tag.putBoolean("Broken", true);
            }
        }
    }

    /**
     * Quenches everything in a barrel that is holding water, once, for the barrel's own tick.
     *
     * <p>A head put in a barrel of water is under water exactly as much as one dropped in a lake, and the
     * barrel is where the pack keeps its water: the head is in the barrel's inventory, so nothing about it
     * is ever an item entity and no item tick will find it.
     */
    public static void quenchBarrelContents(BarrelBlockEntity barrel) {
        if (!holdsWater(barrel)) {
            return;
        }
        IItemHandler items = barrel.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (items == null) {
            return;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (canQuench(stack)) {
                quench(stack);
                barrel.setChanged();
            }
        }
    }

    /** Whether this barrel's tank holds water a head can be quenched in. */
    private static boolean holdsWater(BarrelBlockEntity barrel) {
        IFluidHandler fluids = barrel.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        if (fluids == null || fluids.getTanks() <= 0) {
            return false;
        }
        FluidStack fluid = fluids.getFluidInTank(0);
        return !fluid.isEmpty() && isQuenchWater(fluid.getFluid());
    }
}
