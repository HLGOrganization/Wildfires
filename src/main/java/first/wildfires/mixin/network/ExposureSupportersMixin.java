package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>What this blocks</h2>
 * Exposure downloads its list of supporters ({@code .../supporters/uuids/gilded.json} and the
 * Patreon equivalent) so that those players get a cosmetic badge in the mod's own screens.
 * <h2>Why cancelling is safe</h2>
 * {@code readFileFromURL} catches every exception itself and answers {@code null} after logging a
 * warning; returning {@code null} at HEAD is the same answer the method gives when the host cannot
 * be reached, minus the warning. Callers treat a missing list as "no supporters", which is the
 * normal offline state. Exposure has no config switch for this.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "io.github.mortuusars.exposure.util.supporter.Supporters$Loader", remap = false)
public abstract class ExposureSupportersMixin {

    @Inject(method = "readFileFromURL", at = @At("HEAD"), cancellable = true, require = 1)
    private void wildfires$skipSupporterList(CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(null);
    }
}
