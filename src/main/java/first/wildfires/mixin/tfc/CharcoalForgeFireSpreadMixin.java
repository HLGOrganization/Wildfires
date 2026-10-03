package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.wildfires.compat.tfc.FireSpreadRules;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops TFC's charcoal forge from setting fire to the blocks around it.
 *
 * <h2>What else the random tick does</h2>
 *
 * <p>{@code CharcoalForgeBlock.randomTick} does two things. It calls
 * {@code Helpers.fireSpreaderTick} to spread fire into the air above it, and - in the branch taken when
 * the forge is no longer part of a valid insulated multiblock - it sets the forge's heat to zero,
 * putting it out.
 *
 * <p>Only the spread call is replaced. That is the whole reason this mixin wraps the call instead of
 * cancelling the method: an {@code @Inject} plus {@code ci.cancel()} at the same point would return from
 * the method before the extinguishing branch on the other side of the same {@code if}, leaving a forge
 * that goes on burning once its insulation is gone. Trading a fire hazard for an eternal fire inside a
 * wooden building is not an improvement, so the forge's own shut-off is preserved deliberately.
 *
 * <h2>Two spellings of the method name</h2>
 *
 * <p>The random tick is inherited from Minecraft, so its physical name depends on the environment: a
 * shipped jar carries the SRG name {@code m_213898_}, while a development workspace sees
 * {@code randomTick}. The operation is declared twice with {@code require = 0}, so exactly one applies
 * per environment and the other is skipped - the pattern the rest of this mod uses for inherited
 * methods.
 *
 * <h2>Related</h2>
 *
 * <p>Wildfires' own charcoal stove needs no mixin: {@code UnrestrictedCharcoalForgeBlock} overrides its
 * random tick to do nothing, which already removes its spread.
 */
@Mixin(CharcoalForgeBlock.class)
public abstract class CharcoalForgeFireSpreadMixin {

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
        if (!FireSpreadRules.DISABLE_CHARCOAL_FORGE_SPREAD) {
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
        if (!FireSpreadRules.DISABLE_CHARCOAL_FORGE_SPREAD) {
            original.call(level, pos, random, attempts);
        }
    }
}
