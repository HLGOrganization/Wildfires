package first.wildfires.mixin.create;

import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wooden Cog applies a TFC {@code ItemStackProvider} to filling results in
 * {@code AdvancedRecipe#onResultStackSingle}, but the tail of that method overwrites the
 * freshly built result with the <em>input</em> stack's NBT
 * ({@code result.setTag(inputStack.getTag())}). For pouring molten glass into a graphite
 * mold the input is the cold, empty mold, so the 1500 C that {@code tfc:add_heat} had just
 * written onto the filled mold is lost and the poured mold comes out at ambient temperature.
 *
 * <p>This injects at the return of that method and re-applies the pouring temperature to
 * glass mold results, so a freshly poured mold is genuinely 1500 C and then cools down
 * through TFC's normal heat decay. The temperature is only forced upwards and only for the
 * {@code kubejs:*_mold_filled} items, so no other Wooden Cog recipe changes behaviour.
 */
// DISABLED 2026-09-30 - see the comment in wildfires.mixins.json.
//
// Dead code. The last woodencog:filling mould recipe is commented out in
// kubejs/server_scripts/thirace/recipes/glass_mold.js and pouring moved to
// createmetallurgy:casting_in_table, so AdvancedRecipe.onResultStackSingle is no longer on any
// live path and this injection can never fire. CastingTableMoldHeatMixin, still enabled,
// handles the casting-table route that replaced it.
//
// Kept for reference. To re-enable, restore the annotations below and the matching entry in
// wildfires.mixins.json.
// @Pseudo
// @Mixin(targets = "net.chauvedev.woodencog.recipes.advancedProcessingRecipe.baseRecipes.AdvancedRecipe", remap = false)
public abstract class WoodenCogMoldHeatMixin {

    /** Must match the {@code tfc:add_heat} value used by the pouring recipes. */
    private static final float POURING_TEMPERATURE = 1500.0F;

    private static final String MOLD_NAMESPACE = "kubejs";
    private static final String FILLED_MOLD_SUFFIX = "_mold_filled";

    @Inject(
            method = "onResultStackSingle(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            remap = false
    )
    private void wildfires$restoreFilledMoldTemperature(
            ItemStack resultStack,
            ItemStack inputStack,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        ItemStack result = callback.getReturnValue();
        if (result == null || result.isEmpty() || !wildfires$isFilledGlassMold(result)) {
            return;
        }

        IHeat heat = HeatCapability.get(result);
        if (heat == null) {
            // The mold has no item_heat definition, so TFC never attached the capability
            // and no temperature can be stored on it.
            return;
        }

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
