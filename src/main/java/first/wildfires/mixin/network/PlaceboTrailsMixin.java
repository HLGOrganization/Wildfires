package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this blocks</h2>
 * Placebo downloads a Patreon trail list from {@code raw.githubusercontent.com} from a lambda
 * started by {@code TrailsManager.init()} (every player's trails, not just the local one).
 * <h2>Why cancelling is safe</h2>
 * That lambda only fills the manager's map; the map itself is created and published by
 * {@code init()} before the lambda runs, so cancelling leaves an empty-but-valid manager and the
 * cosmetic trails simply never appear. The same lambda's "loading patreon trails data" log line
 * disappears with it, which is the point.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "dev.shadowsoffire.placebo.patreon.TrailsManager", remap = false)
public abstract class PlaceboTrailsMixin {

    @Inject(method = "lambda$init$0", at = @At("HEAD"), cancellable = true, require = 1)
    private static void wildfires$skipPatreonTrails(CallbackInfo ci) {
        // Intentionally empty: the request and the map fill-up are both skipped.
    }
}
