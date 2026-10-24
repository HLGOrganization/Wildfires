package first.wildfires.mixin.vintageimprovements;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/** Keeps the consumed material's current TFC temperature on newly hammered products. */
@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.helve_hammer.HammeringRecipe", remap = false)
public abstract class HammeringRecipeHeatMixin {

    /**
     * Vintage rolls once during its simulated pass, before consuming any input, then
     * inserts those same products during the real pass. The local counts identify
     * exactly which stacks it matched; other inventory slots and crafting remainders
     * must not contribute heat or have their temperature changed.
     */
    @ModifyExpressionValue(
            method = "apply(Lcom/negodya1/vintageimprovements/content/kinetics/helve_hammer/HelveBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
            at = @At(value = "INVOKE", target = "Lcom/negodya1/vintageimprovements/content/kinetics/helve_hammer/HammeringRecipe;rollResults()Ljava/util/List;"),
            remap = false,
            require = 1
    )
    private static List<ItemStack> wildfires$inheritInputTemperature(
            List<ItemStack> results,
            @Local IItemHandlerModifiable inventory,
            @Local int[] consumedCounts
    ) {
        double totalTemperature = 0;
        int heatedItems = 0;
        for (int slot = 0; slot < Math.min(inventory.getSlots(), consumedCounts.length); slot++) {
            int consumed = consumedCounts[slot];
            if (consumed <= 0) {
                continue;
            }
            IHeat heat = HeatCapability.get(inventory.getStackInSlot(slot));
            if (heat == null) {
                continue;
            }
            float temperature = heat.getTemperature();
            if (!Float.isFinite(temperature)) {
                return results;
            }
            totalTemperature += temperature * (double) consumed;
            heatedItems += consumed;
        }
        if (heatedItems == 0) {
            return results;
        }

        // The pack exposes one input slot; averaging also handles consumed stacks
        // safely if Vintage is later allowed to use more than one heated input.
        float temperature = (float) (totalTemperature / heatedItems);
        List<ItemStack> heatedResults = new ArrayList<>(results.size());
        for (ItemStack result : results) {
            if (result.isEmpty() || HeatCapability.get(result) == null) {
                heatedResults.add(result);
                continue;
            }
            // Preserve result NBT (including sequenced assembly progress), while
            // leaving recipe templates and forced-result suppliers untouched.
            ItemStack copy = result.copy();
            HeatCapability.get(copy).setTemperature(temperature);
            heatedResults.add(copy);
        }
        return heatedResults;
    }
}
