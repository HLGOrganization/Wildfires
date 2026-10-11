package first.wildfires.compat.simplytents;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;

/**
 * <h2>What this fixes</h2>
 * A tent packs what is inside it by copying each block entity's saved data into the tent item and then
 * removing the block, and puts it back with {@code BlockEntity#load(CompoundTag)}. A container keeps its
 * items in that data, so whatever those items carry travels with the tent.
 *
 * <p>TFC Modern Life writes a temporary preservation trait onto the food inside a container standing in one
 * of its cellars - {@code tfc_modern_life:cellar_2x} and up, each halving the decay rate or better - and
 * takes it off again whenever the food leaves the cellar: on the item capability's extract, on the container
 * slots, on the block's own removal and when an inventory is loaded. The tent's copy runs before any of that:
 * it saves the block entity and only then removes the block, so the removal hooks find no block entity left
 * to clean. The preservation is therefore carried away inside the tent item, and food that never stood in a
 * cellar at its new home keeps the extra shelf life it was given in the old one.
 *
 * <p>The cleanup is run on the way back out instead, on the container the tent has just put down: the same
 * thing Modern Life does to an inventory it has just loaded. Unpacked inside a cellar, Modern Life applies
 * that cellar's own trait on its next sync and the food is preserved exactly as much as the cellar it stands
 * in; unpacked anywhere else, the temporary trait is simply gone. Tents packed before this existed are
 * cleaned by the same call, which a fix on the packing side could not have done.
 *
 * <h2>Why the strip is Modern Life's own method</h2>
 * What it manages is not only its own cellar traits but FirmaLife's shelved and hung ones, and it also
 * reaches into the small vessels nested inside a container. {@code sanitizeTakenStack} is the public call
 * that does all of that, and it is the same call its own slot and capability paths make.
 *
 * <h2>Why this is reflection</h2>
 * TFC Modern Life is optional and not a compile dependency, and this is cleanup rather than a feature: the
 * helper is looked up by name once, and a missing mod - or a method renamed by a later version - leaves the
 * food as it is rather than failing the tent.
 */
public final class TentCellarPreservation {

    /** The class and method TFC Modern Life strips its temporary food traits with. */
    private static final String HELPER = "com.jccy.tfcmodernlife.common.climate.CellarPreservationHelper";
    private static final String STRIP = "sanitizeTakenStack";

    private static volatile Method strip;
    private static volatile boolean looked;

    private TentCellarPreservation() {
    }

    /**
     * Takes the temporary cellar preservation off everything inside a container the tent has just put back
     * down. Does nothing when TFC Modern Life is not installed, and never throws: this is cleanup, and the
     * container is already back in the world by the time it runs.
     */
    public static void sanitize(BlockEntity blockEntity) {
        try {
            Method method = stripMethod();
            if (method == null || blockEntity == null) {
                return;
            }
            if (blockEntity instanceof Container container) {
                sanitizeContainer(container, method);
                return;
            }
            sanitizeHandler(blockEntity, method);
        } catch (Throwable failure) {
            // Nothing here is worth a crash. The worst case is the food keeping the trait it arrived with -
            // the very behaviour this class exists to remove - and Modern Life still clears it the next time
            // the food is taken out or the container is opened.
        }
    }

    /**
     * A chest and everything else that is a plain container. The stacks are the container's own, so taking
     * a trait off one of them is the change; marking the block entity changed is what gets it saved.
     */
    private static void sanitizeContainer(Container container, Method method) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            strip(method, container.getItem(slot));
        }
        container.setChanged();
    }

    /**
     * TFC's own containers - chests, large vessels, placed items - are not vanilla containers, so they are
     * reached through the item capability TFC exposes for them, which is what Modern Life walks as well.
     */
    private static void sanitizeHandler(BlockEntity blockEntity, Method method) {
        LazyOptional<IItemHandler> handlers = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER);
        IItemHandler handler = handlers.resolve().orElse(null);
        if (handler == null) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            strip(method, handler.getStackInSlot(slot));
        }
        blockEntity.setChanged();
    }

    /** Hands one stack to Modern Life's strip, if it holds anything at all. */
    private static void strip(Method method, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        try {
            method.invoke(null, stack);
        } catch (ReflectiveOperationException failure) {
            // One stack that could not be cleaned does not stop the rest of the container.
        }
    }

    /** The strip, or {@code null} when TFC Modern Life is not installed. Resolved once. */
    private static Method stripMethod() {
        if (!looked) {
            Method found = null;
            try {
                found = Class.forName(HELPER).getMethod(STRIP, ItemStack.class);
            } catch (Throwable absent) {
                found = null;
            }
            strip = found;
            looked = true;
        }
        return strip;
    }
}
