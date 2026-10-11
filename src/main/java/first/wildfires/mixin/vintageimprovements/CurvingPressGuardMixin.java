package first.wildfires.mixin.vintageimprovements;

import first.wildfires.compat.vintageimprovements.CurvingPressGuard;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>What this fixes</h2>
 * Vintage Improvements' curving press answers "can this recipe be curved in this mode" with
 * {@code CurvingPressBlockEntity.canCurve(Recipe, int)}. In modes 1 and 2 that method walks the
 * recipe's ingredients, takes the first candidate slot and reads {@code getItems()[0]} of it without
 * checking the length of the array, so an ingredient that is not empty while its item list is empty -
 * a TFC trait, food or heat filter whose entries failed to resolve when the recipes were read -
 * throws {@code ArrayIndexOutOfBoundsException} out of a predicate.
 *
 * <p>That throw is expensive out of proportion to itself. JEI registers the curving category by
 * pushing every crafting recipe through this predicate inside one plugin callback; JEI wraps that
 * callback in a try/catch, logs it, and never retries, so the whole callback is lost - the turning
 * and laser-cutting categories come up empty for the rest of the session while the press itself
 * keeps working, because the press picks its recipe from its own screen. The two crashes logged on
 * 2026-10-05 are this call, twelve seconds after joining a server: the client had just replaced its
 * recipe set with the server's.
 *
 * <h2>Why returning false is the whole repair</h2>
 * {@code canCurve} answers false for every recipe it cannot process, and the guarded slot is the one
 * its own pre-check already looked at, so a recipe this guard refuses is a recipe whose next
 * instruction was a throw. Nothing that the press would have accepted is turned away: the guard is
 * the length check the method is missing, and the refused call is a data problem that the player
 * cannot craft around anyway.
 *
 * <h2>Why it is @Pseudo with the target named as a string</h2>
 * Vintage Improvements is not a compile dependency of this mod and does not have to be installed, so
 * the target is named as a string, no type from it appears in a handler signature, and the guard is
 * skipped when the mod is absent. When it is present, {@code require = 1} still makes a renamed
 * method fail loudly at start-up rather than let this crash come back quietly.
 */
// @Pseudo: the target mod is optional, so its class is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped when the mod is
// not installed. Only vanilla types appear in the handler below.
@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.curving_press.CurvingPressBlockEntity", remap = false)
public abstract class CurvingPressGuardMixin {

    /**
     * The single-argument overload delegates to this one, so both entry points are covered here. The
     * handler is static because the target method is static.
     */
    @Inject(method = "canCurve(Lnet/minecraft/world/item/crafting/Recipe;I)Z", at = @At("HEAD"),
            cancellable = true, require = 1)
    private static void wildfires$refuseRecipeWithNoItemList(Recipe<?> recipe, int mode,
            CallbackInfoReturnable<Boolean> cir) {
        if (CurvingPressGuard.refuses(recipe, mode)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
