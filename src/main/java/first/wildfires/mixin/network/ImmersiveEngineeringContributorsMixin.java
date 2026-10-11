package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this blocks</h2>
 * Immersive Engineering starts a thread during class initialisation that downloads
 * {@code contributorRevolvers.json} from {@code raw.githubusercontent.com} and logs
 * "Attempting to download special revolvers from GitHub" while doing it.
 * <h2>Why cancelling the whole run is safe</h2>
 * The thread's only effects are filling {@code RevolverItem.specialRevolvers} and
 * {@code specialRevolversByTag} - cosmetic revolver skins. Nothing waits on it or reads its
 * completion state (the field that holds the thread is never cleared, not even after a normal run),
 * so cancelling costs nothing but the skins and removes both the request and the log noise.
 * Redirecting the request instead would print an INFO line plus a stack trace.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.ImmersiveEngineering$ThreadContributorSpecialsDownloader", remap = false)
public abstract class ImmersiveEngineeringContributorsMixin {

    @Inject(method = "run", at = @At("HEAD"), cancellable = true, require = 1)
    private void wildfires$skipContributorDownload(CallbackInfo ci) {
        // Intentionally empty: the whole download thread body is skipped.
    }
}
