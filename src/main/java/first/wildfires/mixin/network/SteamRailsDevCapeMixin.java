package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this blocks</h2>
 * Steam 'n' Rails downloads a developer cape list
 * ({@code raw.githubusercontent.com/Layers-of-Railways/data}) from the lambda that
 * {@code DevCapeUtils.refresh()} hands to an executor.
 * <h2>Why cancelling is safe</h2>
 * The refreshed set is one the constructor already assigned, so cancelling leaves an empty set:
 * developer capes are simply not rendered. Nothing else reads the field, and the fetch has no
 * config switch.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "com.railwayteam.railways.util.DevCapeUtils", remap = false)
public abstract class SteamRailsDevCapeMixin {

    @Inject(method = "lambda$refresh$0", at = @At("HEAD"), cancellable = true, require = 1)
    private void wildfires$skipDevCapeList(CallbackInfo ci) {
        // Intentionally empty: the request and the set replacement are both skipped.
    }
}
