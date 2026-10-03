package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.wildfires.compat.tfc.FireSpreadRules;
import net.dries007.tfc.common.blocks.devices.FirepitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops TFC's campfire from setting fire to the blocks around it.
 *
 * <h2>How TFC spreads fire</h2>
 *
 * <p>All of TFC's heat sources funnel through one helper, {@code Helpers.fireSpreaderTick}, which walks a
 * few steps in random horizontal directions and writes vanilla fire into any air block that has a
 * flammable neighbour. It receives the position <em>above</em> the heat source, so what catches light is
 * the space in front of the fire. Because the helper takes no argument naming its caller, an individual
 * source cannot be filtered from inside it - the call has to be intercepted where it is made.
 *
 * <h2>Why the call is replaced rather than cancelled</h2>
 *
 * <p>Wrapping just this call suppresses the spread while leaving the rest of the random tick alone. An
 * {@code @Inject} at the same point calling {@code ci.cancel()} would return from the entire method,
 * which on the charcoal forge also skips the code below - the half that extinguishes the forge when its
 * insulation is missing. That would trade a fire hazard for a forge that keeps burning inside a wooden
 * building. The same reasoning is applied consistently to both blocks, even though this one currently
 * has nothing else in its random tick, so that a future TFC update adding one does not silently lose it.
 *
 * <h2>Two spellings of the method name</h2>
 *
 * <p>The random tick is inherited from Minecraft, so its physical name depends on the environment: a
 * shipped jar carries the SRG name {@code m_213898_}, while a development workspace sees
 * {@code randomTick}. The operation is therefore declared twice with {@code require = 0}, so exactly one
 * applies per environment and the other is skipped. This is how the other mixins in this mod handle
 * methods inherited from Minecraft.
 *
 * <h2>Scope</h2>
 *
 * <p>Only the campfire. The charcoal forge is handled by {@link CharcoalForgeFireSpreadMixin}, and
 * molten metal - the third caller of the same helper - is deliberately left alone, since spilling metal
 * setting light to its surroundings is a hazard players expect.
 */
@Mixin(FirepitBlock.class)
public abstract class FirepitFireSpreadMixin {

    /** Production name, matching the SRG-mapped runtime. */
    @WrapOperation(
            method = "m_213898_",
            at = @At(value = "INVOKE",
                    target = "Lnet/dries007/tfc/util/Helpers;fireSpreaderTick("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/util/RandomSource;I)V"),
            remap = false,
            require = 0
    )
    private void wildfires$noSpread(ServerLevel level, BlockPos pos, RandomSource random,
                                    int attempts, Operation<Void> original) {
        if (!FireSpreadRules.DISABLE_FIREPIT_SPREAD) {
            original.call(level, pos, random, attempts);
        }
    }

    /** Development name, matching the Mojang-mapped workspace. */
    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/dries007/tfc/util/Helpers;fireSpreaderTick("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/util/RandomSource;I)V"),
            remap = false,
            require = 0
    )
    private void wildfires$noSpreadDev(ServerLevel level, BlockPos pos, RandomSource random,
                                       int attempts, Operation<Void> original) {
        if (!FireSpreadRules.DISABLE_FIREPIT_SPREAD) {
            original.call(level, pos, random, attempts);
        }
    }
}
