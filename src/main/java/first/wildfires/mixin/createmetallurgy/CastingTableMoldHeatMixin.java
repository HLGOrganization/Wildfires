package first.wildfires.mixin.createmetallurgy;

import com.simibubi.create.foundation.item.SmartInventory;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the pouring temperature of glass molds filled on a casting table.
 *
 * <p>Mold workflow: empty mold, lathe, two half molds, assembly, pour molten glass, cool below 900 C,
 * then chisel it apart. The cooling step needs the filled mold to start hot. A cast mold otherwise
 * comes out at ambient temperature, so the workflow stalls and the mold can never be split.
 *
 * <p>Wildfires already handled this in {@code create.WoodenCogMoldHeatMixin}, which injected into
 * Wooden Cog's {@code AdvancedRecipe}. The pouring recipes were later moved from {@code woodencog:filling}
 * to {@code createmetallurgy:casting_in_table}, so that injection point is no longer on the path: a
 * casting table writes its result straight into its inventory and never consults an
 * {@code ItemStackProvider}, which also puts it out of reach of {@code RecipeApplierHeatMixin}.
 *
 * <p>This injects at the return of {@code applyRecipe}, where the finished stack is already in slot 0,
 * and only ever raises the temperature. The molds are matched by namespace and suffix, so no item list
 * has to be maintained. {@code RecipeApplierHeatMixin} keeps its own job: it covers the mechanical-arm
 * route that splits the mold, while this covers the pouring that fills it.
 *
 * <p>Targeted by name with {@link Pseudo} because createmetallurgy is not a mandatory dependency;
 * Wildfires must keep loading when it is absent.
 */
@Pseudo
@Mixin(targets = "fr.lucreeper74.createmetallurgy.content.blocks.casting.CastingBlockEntity", remap = false)
public abstract class CastingTableMoldHeatMixin {

    /** Must match the {@code tfc:add_heat} value the pouring recipes use. */
    private static final float POURING_TEMPERATURE = 1500.0F;

    private static final String MOLD_NAMESPACE = "kubejs";
    private static final String FILLED_MOLD_SUFFIX = "_mold_filled";

    /** The casting result slot. The mold input lives in the separate mold inventory. */
    @Shadow(remap = false)
    public SmartInventory inv;

    @Inject(method = "applyRecipe", at = @At("RETURN"), remap = false)
    private void wildfires$restoreFilledMoldTemperature(CallbackInfo callback) {
        ItemStack output = inv.getStackInSlot(0);
        if (output.isEmpty() || !wildfires$isFilledGlassMold(output)) {
            return;
        }

        IHeat heat = HeatCapability.get(output);
        if (heat == null) {
            // The mold has no item_heat definition, so TFC never attached the capability and
            // there is nowhere to store a temperature.
            return;
        }

        // Only ever raise the temperature, so a mold that was heated beforehand keeps its value.
        if (heat.getTemperature() < POURING_TEMPERATURE) {
            heat.setTemperature(POURING_TEMPERATURE);
        }
    }

    private static boolean wildfires$isFilledGlassMold(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null
                && MOLD_NAMESPACE.equals(id.getNamespace())
                && id.getPath().endsWith(FILLED_MOLD_SUFFIX);
    }
}