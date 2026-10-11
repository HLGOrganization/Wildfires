package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this blocks</h2>
 * KubeJS checks {@code v.kubejs.com} for a newer version from the lambda started by
 * {@code KubeJS.loadComplete}, which runs on both the client and a dedicated server.
 * <h2>Why cancelling is safe</h2>
 * The lambda is wrapped in a swallow-everything {@code try}/{@code catch}, so failing it was always
 * a supported outcome; cancelling it only loses the "new version available" notice. There is no
 * config switch for the check.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.KubeJS", remap = false)
public abstract class KubeJsUpdateMixin {

    @Inject(method = "lambda$loadComplete$3", at = @At("HEAD"), cancellable = true, require = 1)
    private static void wildfires$skipUpdateCheck(CallbackInfo ci) {
        // Intentionally empty: the version check never runs.
    }
}
