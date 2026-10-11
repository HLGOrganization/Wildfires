package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this blocks</h2>
 * The wings half of Placebo's Patreon cosmetics: a start-up download of
 * {@code PatreonWings.txt} from {@code raw.githubusercontent.com}, started by
 * {@code WingsManager.init()}.
 * <h2>Why cancelling is safe</h2>
 * Exactly as for the trails manager, the lambda only populates a map that {@code init()} has
 * already published, so cancelling yields an empty but usable manager and no cosmetic wings.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "dev.shadowsoffire.placebo.patreon.WingsManager", remap = false)
public abstract class PlaceboWingsMixin {

    @Inject(method = "lambda$init$1", at = @At("HEAD"), cancellable = true, require = 1)
    private static void wildfires$skipPatreonWings(CallbackInfo ci) {
        // Intentionally empty: the request and the map fill-up are both skipped.
    }
}
