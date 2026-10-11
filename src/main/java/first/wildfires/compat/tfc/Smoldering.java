package first.wildfires.compat.tfc;

import first.wildfires.block.UnrestrictedCharcoalForgeBlock;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.FirepitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Relights TFC's heat producing devices from the fuel they hold while they are still hot. The rule that
 * decides it, and why it is what it is, lives in {@link SmolderingRules}.
 *
 * <h2>Where this runs</h2>
 *
 * <p>Both entry points are called at the very end of the device's server tick, after that tick has moved
 * whatever fuel arrived during it down into the slot the device consumes from. Fuel the player has just
 * pushed into a forge's fuel slots, or thrown on top of a firepit, is therefore caught on the tick it
 * arrives. Nothing happens on the client: the relight is a server decision and TFC syncs the flame to the
 * client by itself.
 *
 * <h2>Why the devices are lit through {@code light()}</h2>
 *
 * <p>Writing {@code LIT = true}, or a non-zero {@code HEAT}, straight into the block state lights a device
 * for a single tick even when it has no fuel to burn, which is exactly why both TFC block entities offer
 * {@code light(state)} instead. It consumes one item of fuel and only then sets the state, and when there is
 * nothing to consume it does nothing at all - no flame, no sound, no inventory change - so calling it every
 * tick on a hot device that has not been fed is free.
 */
public final class Smoldering {

    private Smoldering() {
    }

    /** A campfire, cooking pot or grill: any of the three shares this one tick. */
    public static void firepit(Level level, BlockState state, AbstractFirepitBlockEntity<?> firepit) {
        if (level.isClientSide()) {
            return;
        }
        if (!SmolderingRules.shouldRelight(state.getValue(FirepitBlock.LIT), firepit.getTemperature(), true)) {
            return;
        }
        firepit.light(state);
    }

    /** A charcoal forge, and the earthen oven, which is a forge that needs no multiblock around it. */
    public static void forge(Level level, BlockPos pos, BlockState state, CharcoalForgeBlockEntity forge) {
        if (level.isClientSide()) {
            return;
        }
        final boolean canStayLit = state.getBlock() instanceof UnrestrictedCharcoalForgeBlock
                || CharcoalForgeBlock.isValid(level, pos);
        if (!SmolderingRules.shouldRelight(state.getValue(CharcoalForgeBlock.HEAT) > 0, forge.getTemperature(), canStayLit)) {
            return;
        }
        forge.light(state);
    }
}
