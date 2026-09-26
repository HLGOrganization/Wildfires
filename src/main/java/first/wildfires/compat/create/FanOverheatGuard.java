package first.wildfires.compat.create;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Detects an item a blasting fan has heated past its ceiling, and discards it when it is a dropped
 * entity.
 *
 * <p>Four groups exist. The first is never touched, and the others carry their own ceiling:
 * <ul>
 *   <li>No heat definition: Wooden Cog never touches the stack, so Create's own handling is left
 *       exactly as it is.</li>
 *   <li>A welding temperature: the ceiling is {@code welding_temperature * multiplier}, which sits
 *       above every TFC melting point, so the metal still melts into its usual output first.</li>
 *   <li>No welding temperature, but a heating recipe: the recipe owns the outcome. TFC has
 *       "destroy" recipes such as {@code destroy_cooked_meat} that burn food at 900 C, so this group
 *       must be left to Wooden Cog.</li>
 *   <li>No welding temperature and no heating recipe: nothing would ever stop it, so it is destroyed
 *       past the flat {@code noRecipeCeiling}.</li>
 * </ul>
 *
 * <p>The ceiling is only tested once an item is already hot, and the recipe lookup is a plain cache
 * read, so an ordinary item costs one temperature read per tick.
 */
public final class FanOverheatGuard {

    private FanOverheatGuard() {
    }

    /** Discards a dropped item once it has passed its ceiling. */
    public static void discardIfOverheated(ItemEntity itemEntity, FanProcessingType type) {
        if (itemEntity == null || itemEntity.isRemoved()) {
            return;
        }
        if (isOverheated(itemEntity.getItem(), type)) {
            itemEntity.discard();
        }
    }

    /** True when this stack must be destroyed rather than heated further. */
    public static boolean isOverheated(ItemStack stack, FanProcessingType type) {
        if (type == null || !AllFanProcessingTypes.BLASTING.equals(type)) {
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        IHeat heat = HeatCapability.get(stack);
        if (heat == null) {
            // No heat definition: Wooden Cog never touches this stack, so Create's own
            // "no recipe means discard" route must stay exactly as it is.
            return false;
        }

        float temperature = heat.getTemperature();
        float weldingTemperature = heat.getWeldingTemperature();
        if (weldingTemperature > 0.0F) {
            return temperature > weldingTemperature * FanHeatConfig.overheatMultiplier();
        }

        float ceiling = FanHeatConfig.noRecipeCeiling();
        if (ceiling <= 0.0F || temperature <= ceiling) {
            // Below the flat ceiling, or the rule is switched off.
            return false;
        }
        // Hot enough to matter, so pay for the lookup: a recipe means the recipe decides,
        // including the TFC "burn it" recipes that discard the item themselves.
        return HeatingRecipe.getRecipe(stack) == null;
    }
}