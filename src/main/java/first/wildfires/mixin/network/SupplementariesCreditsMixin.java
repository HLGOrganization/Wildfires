package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this blocks</h2>
 * Supplementaries fetches a remote credits/supporter document
 * ({@code data.chocolateminecraft.com}) from the async lambda started by
 * {@code Credits.fetchFromServer}.
 * <h2>Why cancelling is safe</h2>
 * The lambda would only replace the static {@code INSTANCE}, which the class initialiser has
 * already built from its own bundled data and a {@code Map.of()} default. Cancelling keeps the
 * built-in credits, which is exactly the offline result. The class has no config switch for this.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.utils.Credits", remap = false)
public abstract class SupplementariesCreditsMixin {

    @Inject(method = "lambda$fetchFromServer$17", at = @At("HEAD"), cancellable = true, require = 1)
    private static void wildfires$skipRemoteCredits(CallbackInfo ci) {
        // Intentionally empty: the request and the INSTANCE replacement are both skipped.
    }
}
