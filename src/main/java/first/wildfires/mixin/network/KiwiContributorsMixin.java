package first.wildfires.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>What this blocks</h2>
 * Kiwi downloads its contributor/supporter tiers from the network (with a second mirror as a
 * fallback) whenever a tier provider is refreshed, which happens during start-up.
 * <h2>Why this exact return value</h2>
 * The guarded method already catches every failure and returns {@code true} when the cause was an
 * {@link java.net.UnknownHostException}, which tells the refresh loop to stop trying mirrors. This
 * guard returns that same value at HEAD, so the result is byte-for-byte the offline behaviour: no
 * request, no mirror attempt, no log line above DEBUG, and an empty-but-valid contributor set that
 * the constructor has already installed.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "snownee.kiwi.contributor.impl.JsonTierProvider", remap = false)
public abstract class KiwiContributorsMixin {

    @Inject(method = "load", at = @At("HEAD"), cancellable = true, require = 1)
    private void wildfires$skipContributorFetch(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Boolean.TRUE);
    }
}
