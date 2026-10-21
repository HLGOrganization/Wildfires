package first.wildfires.mixin.tfc;

import first.wildfires.block.UnrestrictedCharcoalForgeBlock;
import first.wildfires.compat.tfc.DeviceHeatRate;
import first.wildfires.compat.tfc.TfcDeviceHeatConfig;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Wildfires' standalone charcoal stove heat the items inside it 50% faster, and leaves every other
 * user of {@code CharcoalForgeBlockEntity} exactly as TFC wrote it.
 *
 * <p>Why a mixin on the TFC class rather than on {@code UnrestrictedCharcoalForgeBlock#serverTick}: that
 * override in {@code first.wildfires.block} is a one line delegation to
 * {@code CharcoalForgeBlockEntity.serverTick}, so there is no Wildfires code path to hook - the heating
 * arithmetic runs inside TFC, once, for both blocks. Both of them therefore arrive at this injection point
 * and the block state is what tells them apart.
 *
 * <p>Which blocks use this block entity - the complete list, because "only these three" is a claim about
 * all of them:
 * <ul>
 *   <li>{@code tfc:charcoal_forge} - TFC's own forge, <b>unchanged</b>, it does not pass the block check;</li>
 *   <li>{@code wildfires:unrestricted_charcoal_forge} - the only block scaled here;</li>
 *   <li>any third-party forge that registers {@code TFCBlockEntities.CHARCOAL_FORGE} for its own block -
 *       <b>unchanged</b>, for the same reason. Nothing is decided from the block entity's own type, which
 *       both blocks share.</li>
 * </ul>
 *
 * <h2>Why the call is redirected instead of its rate constant being edited</h2>
 *
 * <p>The obvious injection - changing the {@code 3.0F} that becomes the rate parameter - cannot work here.
 * That constant lives inside the body of the two-argument overload, not at the call site:
 * <pre>
 *   public static void addTemp(IHeat, float);
 *      0: aload_0
 *      1: fload_1
 *      2: ldc           // float 3.0f
 *      4: invokestatic  // addTemp:(LIHeat;FF)V
 *      7: return
 * </pre>
 * {@code CharcoalForgeBlockEntity.serverTick} holds only {@code addTemp:(LIHeat;F)V} at offset 413, so no
 * injection at that call site can reach the constant - and a {@code @ModifyExpressionValue} on it is rejected
 * outright, because MixinExtras refuses to modify an instruction whose return type is {@code void}
 * ({@code ModifyExpressionValueInjector#checkTargetReturnsAValue} builds an
 * {@code InvalidInjectionException} for exactly that case). So the call itself is redirected, and this
 * handler builds the replacement call: TFC's own two-argument form when the block is not the stove, and the
 * three-argument form with a recomputed rate parameter when it is.
 *
 * <p>{@code temperature} (the first argument) is passed straight through on both paths. It is the ceiling
 * {@code addTemp} clamps the result to, so it is the one value that decides how hot an item may get, and
 * nothing here can move it. {@code DeviceHeatRate} explains the recomputation, which is exact at TFC's
 * shipped modifiers and at any other pair a pack configures.
 */
@Mixin(value = CharcoalForgeBlockEntity.class, remap = false)
public abstract class CharcoalForgeHeatSpeedMixin {

    @Redirect(
            method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/capabilities/heat/HeatCapability;addTemp(Lnet/dries007/tfc/common/capabilities/heat/IHeat;F)V"),
            remap = false
    )
    private static void wildfires$redirectForgeHeat(IHeat heat, float temperature, Level level, BlockPos pos,
                                                    BlockState state, CharcoalForgeBlockEntity forge) {
        if (state.getBlock() instanceof UnrestrictedCharcoalForgeBlock) {
            HeatCapability.addTemp(heat, temperature, DeviceHeatRate.scale(
                    DeviceHeatRate.TWO_ARGUMENT_RATE,
                    DeviceHeatRate.heatingModifier(),
                    DeviceHeatRate.coolingModifier(),
                    TfcDeviceHeatConfig.unrestrictedCharcoalForgeMultiplier()));
            return;
        }

        // TFC's own call, unchanged: the two-argument overload is the three-argument one with a rate
        // parameter of 3.0F, so this is byte for byte the heating TFC performed before this mixin existed.
        HeatCapability.addTemp(heat, temperature);
    }
}
