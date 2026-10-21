package first.wildfires.mixin.tfc;

import first.wildfires.compat.tfc.DeviceHeatRate;
import first.wildfires.compat.tfc.TfcDeviceHeatConfig;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes TFC's crucible heat twice as fast and Wildfires' corundum crucible two and a half times as fast, and
 * leaves every other user of {@code CrucibleBlockEntity} exactly as TFC wrote it.
 *
 * <p>Which blocks use this block entity - the complete list, because "only these three" is a claim about all
 * of them:
 * <ul>
 *   <li>{@code tfc:crucible} - TFC's own crucible, scaled by {@code crucibleMultiplier} (2.0 = +100%);</li>
 *   <li>{@code wildfires:crucible} - the corundum crucible, scaled by {@code corundumCrucibleMultiplier}
 *       (2.5 = +150%). It is an instance of TFC's own {@code CrucibleBlock} and registers the same block
 *       entity, so the registered id is the only thing that separates it from TFC's;</li>
 *   <li>{@code wildfires:decorative_crucible} - an unfired decorative block with no block entity and no
 *       {@code serverTick}, so it never reaches this point. It has no item heat of its own either;</li>
 *   <li>{@code createmetallurgy:industrial_crucible} - a different block entity entirely
 *       ({@code fr.lucreeper74.createmetallurgy...CrucibleBlockEntity extends SmartBlockEntity}), and
 *       {@code alexscaves:conversion_crucible} - another mod's own class again. Neither reaches this point,
 *       and no Wildfires code names either of them. Both have a {@code blockstates/*_crucible.json} of their
 *       own and share nothing with TFC's crucible but the English word.</li>
 * </ul>
 *
 * <h2>Why the call is redirected</h2>
 *
 * <p>The injection point is the {@code HeatCapability.addTemp(IHeat, float, float)} call inside
 * {@code CrucibleBlockEntity.serverTick} (offset 169 in TFC 3.2.20). This handler receives the call's three
 * arguments in order - {@code heat}, {@code temperature} and {@code b} - so {@code b} is the very number TFC
 * computed three instructions earlier ({@code 2 + 0.0025 * temperature}), and it is TFC's own value that gets
 * scaled. {@code temperature} is passed straight through, so the ceiling {@code addTemp} clamps to is never
 * touched.
 *
 * <p>This was written as a {@code @ModifyArgs} first, and that crashed the game on startup: {@code @ModifyArgs}
 * needs {@code org.spongepowered.asm.synthetic.args.Args$1}, a synthetic class the Mixin annotation processor
 * generates and Mixin then defines at runtime, and in this Forge 1.20.1 environment it is not present
 * ({@code NoClassDefFoundError: org/spongepowered/asm/synthetic/args/Args$1} raised out of
 * {@code TFCBlockEntities.<clinit>}, which takes the whole launch down). {@code @Redirect} does not use that
 * machinery at all - it rewrites the call instruction in place and calls an ordinary method - so it cannot
 * fail that way.
 */
@Mixin(value = CrucibleBlockEntity.class, remap = false)
public abstract class CrucibleHeatSpeedMixin {

    @Unique
    private static final ResourceLocation WILDFIRES$CORUNDUM_CRUCIBLE =
            ResourceLocation.fromNamespaceAndPath("wildfires", "crucible");

    @Redirect(
            method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/capabilities/heat/HeatCapability;addTemp(Lnet/dries007/tfc/common/capabilities/heat/IHeat;FF)V"),
            remap = false
    )
    private static void wildfires$redirectCrucibleHeat(IHeat heat, float temperature, float b, Level level,
                                                       BlockPos pos, BlockState state,
                                                       CrucibleBlockEntity crucible) {
        HeatCapability.addTemp(heat, temperature, DeviceHeatRate.scale(
                b,
                DeviceHeatRate.heatingModifier(),
                DeviceHeatRate.coolingModifier(),
                wildfires$multiplier(state)));
    }

    /**
     * The corundum crucible registers the same block entity as TFC's crucible and is an instance of the same
     * block class, so the registered id is what tells them apart.
     */
    @Unique
    private static double wildfires$multiplier(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return WILDFIRES$CORUNDUM_CRUCIBLE.equals(id)
                ? TfcDeviceHeatConfig.corundumCrucibleMultiplier()
                : TfcDeviceHeatConfig.crucibleMultiplier();
    }
}
