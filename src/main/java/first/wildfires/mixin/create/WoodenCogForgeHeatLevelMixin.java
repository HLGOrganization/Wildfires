package first.wildfires.mixin.create;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import first.wildfires.compat.create.WoodenCogBurnerTemperatures;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Create's own recipe types see a charcoal forge as truly superheated.
 *
 * <h2>The problem</h2>
 *
 * <p>Wildfires gives a charcoal forge an eighth heat tier: when its real temperature reaches 2300C the
 * heat level is pushed to 8, one above TFC's own maximum of 7. Create's recipes on a basin are gated by
 * a heat level, not by a temperature, and Wooden Cog translates the forge's heat level back into one
 * with a lossy formula:
 *
 * <pre>
 *   temp = (heatLevel - 1) / 6 * Heat.maxVisibleTemperature()   // maxVisibleTemperature = 1600
 * </pre>
 *
 * <p>At heat level 8 that yields 1866.7C, while {@code HeatCondition.SUPERHEATED} needs 2300C. The
 * calculation is short by 433.3C no matter how hot the forge actually is, so every superheated basin
 * recipe fails on a charcoal forge. Reaching the threshold by the same formula would need a heat level
 * of 9.625, which the property cannot hold.
 *
 * <p>Wooden Cog has a method that does read the real temperature,
 * {@code BasinBlockEntityExtended.getHeatSourceTemperature}, but it only feeds Wooden Cog's own recipe
 * type. Create's native types go through the heat level.
 *
 * <h2>Why this injection point</h2>
 *
 * <p>{@code BasinBlockEntity.getHeatLevel()} is the outer link of that chain - it calls the static
 * {@code getHeatLevelOf} and caches the result. Overriding its return value is therefore a pure upgrade:
 * everything underneath has already run, and nothing about the forge or the heat level property is
 * touched.
 *
 * <p>{@code getHeatLevelOf} deliberately is <em>not</em> the target. Wooden Cog injects into its head
 * with {@code cancellable} and calls {@code setReturnValue} for a charcoal forge, which means the
 * method body and its own {@code RETURN} never execute - an injection there would be skipped outright.
 * Beating that would mean out-prioritising Wooden Cog, which would then depend on its internal
 * priority never changing. Targeting {@code getHeatLevel} avoids the contest entirely, and the two
 * mixins land on different methods.
 *
 * <h2>Scope</h2>
 *
 * <p>The value only moves upward, and only when the block below the basin is a charcoal forge. A blaze
 * burner, or any other heat source, returns through the null and type checks unchanged. Because the
 * upgrade is derived from the real temperature, it also stops applying on its own once the forge cools.
 *
 * <p>Following this mod's convention for Create classes, the mixin names the class directly, omits
 * {@code @Pseudo} since Create is a hard dependency, and uses {@code remap = false}.
 */
// DISABLED 2026-09-30 - see the comment in wildfires.mixins.json.
//
// Obsolete. Wooden Cog commit 582c633, which is present in woodencog 1.2.17, changed
// tempFromBlockstate's multiplier from Heat.maxVisibleTemperature() (1600) to the configured
// blaze_burner_seething, which this pack sets to 2300. Heat level 7 now reverse-computes to
// 2300C and level 8 to 2683C, so a charcoal forge already reports SEETHING by itself and the
// 433.3C shortfall this mixin was written to close no longer exists.
//
// Kept for reference. To re-enable, restore the annotation below and the matching entry in
// wildfires.mixins.json.
// @Mixin(value = BasinBlockEntity.class, priority = 900, remap = false)
public abstract class WoodenCogForgeHeatLevelMixin {

    /**
     * Reads the forge's real temperature and upgrades the reported heat level when it deserves better.
     *
     * <p>The thresholds come from {@link WoodenCogBurnerTemperatures}, which reads them from Wooden
     * Cog's config with fallbacks matching the values the pack ships - the same source the rest of this
     * mod's burner integration uses, so the numbers cannot drift apart from each other.
     */
    @Inject(method = "getHeatLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private void wildfires$useTrueForgeTemperature(CallbackInfoReturnable<HeatLevel> cir) {
        BasinBlockEntity basin = (BasinBlockEntity) (Object) this;

        Level level = basin.getLevel();
        if (level == null) {
            return;
        }

        // The heat source sits directly beneath the basin, which is where Create's own lookup and
        // Wooden Cog's getHeatSourceTemperature both read from.
        BlockPos below = basin.getBlockPos().below();
        BlockEntity heatSource = level.getBlockEntity(below);
        if (!(heatSource instanceof CharcoalForgeBlockEntity forge)) {
            return;
        }

        float temperature = forge.getTemperature();
        if (temperature <= 0.0F) {
            return;
        }

        HeatLevel fromTrueTemperature = heatLevelFor(temperature);
        HeatLevel original = cir.getReturnValue();

        // Upgrade only. A lower value would mean the temperature is stale relative to the block state,
        // and reporting less heat than Create already believes in is not what this is for.
        if (original == null || fromTrueTemperature.ordinal() > original.ordinal()) {
            cir.setReturnValue(fromTrueTemperature);
        }
    }

    /**
     * Maps a real temperature to Create's heat level.
     *
     * <p>Deliberately does not reuse {@code WoodenCogBurnerTemperatures.targetForHeatLevel}: that method
     * maps in the opposite direction and collapses everything below KINDLED to none, so it cannot answer
     * this question.
     */
    private static HeatLevel heatLevelFor(float temperature) {
        if (temperature >= WoodenCogBurnerTemperatures.seething()) {
            return HeatLevel.SEETHING;
        }
        if (temperature >= WoodenCogBurnerTemperatures.kindled()) {
            return HeatLevel.KINDLED;
        }
        if (temperature >= WoodenCogBurnerTemperatures.smouldering()) {
            return HeatLevel.SMOULDERING;
        }
        return HeatLevel.NONE;
    }
}
