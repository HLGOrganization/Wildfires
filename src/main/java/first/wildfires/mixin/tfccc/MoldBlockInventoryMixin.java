package first.wildfires.mixin.tfccc;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>What this does</h2>
 * Makes a TFC Channel Casting mould table refuse every fluid that arrives through its Forge fluid
 * capability. Only the mod's own casting channel can fill it after this.
 *
 * <h2>The hole this closes</h2>
 * A mould table is meant to be fed by a casting channel, and the channel carries the crucible's
 * temperature with the metal: {@code MoldBlockEntity.serverTick} drains one unit out of the crucible,
 * merges it into the mould item with {@code crucible.getTemperature()}, and copies that temperature
 * onto the mould stack. The metal arrives at the temperature of its molten source, and the casting
 * recipe that turns it into an ingot is only reachable once the metal is hot enough.
 *
 * <p>The table also exposes a plain {@code IFluidHandler} on five of its six faces, and that handler's
 * {@code fill} never looks at a temperature. Any pipe from any mod - Create, Immersive Engineering, a
 * tank, a bucket - could push molten metal in cold and have the recipe above produce a finished ingot
 * instantly, skipping both the heat and the channel. The temperature nobody paid for is the whole
 * point of the channel.
 *
 * <h2>Why the channel is unaffected</h2>
 * The channel never calls {@code fill}. Its path is {@code ChannelFlow} deciding a mould is fillable,
 * {@code MoldBlockEntity.setSource} recording where the flow comes from, then {@code serverTick}'s own
 * per-tick drain out of the crucible into the mould item's {@code MoldLike}. None of those touch
 * {@code MoldBlockInventory.fill}, so refusing every call to it removes exactly the unsupported input
 * path and leaves the supported one untouched.
 *
 * <h2>Why the whole method and not a check inside it</h2>
 * There is no caller to test against. The capability hands the handler to whatever asks, and the
 * caller is a foreign mod with no marker this class could recognise - telling "a pipe" from "the
 * channel" by inspecting a stack trace or a thread name would be a guess that breaks on the next
 * update. The channel's independence from this method is a property of the mod, not of the runtime, so
 * the split is made once here rather than inferred per call.
 *
 * <h2>What is also removed</h2>
 * The method additionally serves TFC instant-barrel recipes - a mould table holding an item plus a
 * fluid that match a {@code tfc:barrel_instant} recipe, which is how this mod mixes chocolate. That
 * branch is reachable only through the same {@code fill}, and it is the same shape of hole: fluid
 * that never went past the table's own temperature handling producing a result instantly. It goes
 * with the rest. A pack that wants the chocolate path back can still reach it through a real barrel,
 * which is where the recipe belongs.
 *
 * <h2>Why both actions are refused</h2>
 * A pipe asks with {@code SIMULATE} first and only calls {@code EXECUTE} if the answer was non-zero.
 * Refusing only {@code EXECUTE} would leave every pipe in the pack believing it had accepted fluid on
 * the simulate pass and losing it on the execute pass. Answering zero to both is the honest answer -
 * the table accepts nothing - and it costs the pipe nothing, because a pipe that is told zero keeps
 * its own contents. Throwing instead would fault inside the pipe's ticker.
 */
// @Pseudo: Channel Casting is an optional mod, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and this mixin is dropped if the mod is
// not installed. When it is present, require = 1 below still makes a renamed method fail loudly.
@Pseudo
@Mixin(targets = "com.ljuangbminecraft.tfcchannelcasting.common.blockentities.MoldBlockEntity$MoldBlockInventory",
        remap = false)
public abstract class MoldBlockInventoryMixin {

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true, require = 1)
    private void wildfires$refusePipedFluid(FluidStack resource, IFluidHandler.FluidAction action,
                                            CallbackInfoReturnable<Integer> cir) {
        // The channel does not reach this method; see the class note. Anything that does is a pipe or
        // another mod's handler, and the answer to all of them is the same.
        cir.setReturnValue(0);
    }
}
