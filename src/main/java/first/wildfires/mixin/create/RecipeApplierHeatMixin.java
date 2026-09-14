package first.wildfires.mixin.create;

import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * Create's mechanical arm (deployer) applies its results through
 * {@link RecipeApplier#applyRecipeOn(Level, ItemStack, Recipe, boolean)}. Wooden Cog's
 * ItemStackProvider support only covers the pressing and filling recipe caches, so a
 * {@code create:deploying} result silently loses the heat of the item it was applied to.
 *
 * <p>This injects at the tail of that call and copies the input stack's TFC temperature
 * onto the results, so breaking a filled graphite glass mold in a deployer yields a
 * still-warm product exactly like the manual Shift + right-click path does.
 *
 * <p>The effect is deliberately limited to the filled glass molds so that no other
 * Create processing chain changes behaviour.
 */
@Mixin(value = RecipeApplier.class, remap = false)
public abstract class RecipeApplierHeatMixin {

    /** Inputs whose deployer results must inherit the input temperature. */
    private static final Set<ResourceLocation> HEAT_INHERITING_INPUTS = Set.of(
            ResourceLocation.fromNamespaceAndPath("kubejs", "graphite_jar_mold_filled"),
            ResourceLocation.fromNamespaceAndPath("kubejs", "graphite_wine_bottle_mold_filled"),
            ResourceLocation.fromNamespaceAndPath("kubejs", "graphite_bottle_mold_filled"),
            ResourceLocation.fromNamespaceAndPath("kubejs", "graphite_lampshade_mold_filled")
    );

    @Inject(
            method = "applyRecipeOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/Recipe;Z)Ljava/util/List;",
            at = @At("RETURN"),
            remap = false
    )
    private static void wildfires$copyInputHeatToResults(
            Level level,
            ItemStack input,
            Recipe<?> recipe,
            boolean dropExcess,
            CallbackInfoReturnable<List<ItemStack>> callback
    ) {
        if (input == null || input.isEmpty() || !wildfires$isHeatInheritingInput(input)) {
            return;
        }

        List<ItemStack> results = callback.getReturnValue();
        if (results == null || results.isEmpty()) {
            return;
        }

        IHeat inputHeat = HeatCapability.get(input);
        if (inputHeat == null) {
            return;
        }
        float temperature = inputHeat.getTemperature();
        if (temperature <= 0) {
            return;
        }

        for (ItemStack result : results) {
            if (result == null || result.isEmpty()) {
                continue;
            }
            // Results without a heat definition (such as the returned mold halves)
            // simply have no capability and are skipped.
            IHeat resultHeat = HeatCapability.get(result);
            if (resultHeat != null) {
                resultHeat.setTemperature(temperature);
            }
        }
    }

    private static boolean wildfires$isHeatInheritingInput(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && HEAT_INHERITING_INPUTS.contains(id);
    }
}
