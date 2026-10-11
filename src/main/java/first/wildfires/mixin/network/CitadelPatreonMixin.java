package first.wildfires.mixin.network;

import first.wildfires.compat.net.NetworkGuard;
import java.io.BufferedReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <h2>What this blocks</h2>
 * Citadel reads the Patreon supporter list during common setup
 * ({@code raw.githubusercontent.com/AlexModGuy/.../patreon.txt}) to decide who gets a cosmetic
 * model.
 * <h2>Why the request is redirected instead of the method being cancelled</h2>
 * {@code Citadel.setup} does far more than that one read - it registers the server proxy, loads
 * lectern books and registers four network messages - so cancelling it is not an option. The single
 * URL read is replaced instead with the copy that ships inside Citadel's own jar
 * ({@code assets/citadel/patreon.txt}), which is the very fallback the mod's own helper uses when
 * the download fails. The supporter list therefore still works, offline, with no log noise.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "com.github.alexthe666.citadel.Citadel", remap = false)
public abstract class CitadelPatreonMixin {

    // The handler must NOT be static: Citadel.setup is an instance method, and Mixin requires the
    // handler's 'static' modifier to match the target's ("InvalidInjectionException: 'static'
    // modifier of handler method does not match target"). A non-static handler is merged into the
    // target class and invoked on it with 'this' taken from the target's local 0, so the receiver
    // is implicit and must NOT be added to the parameter list.
    //
    // The redirected call WebHelper.getURLContents is itself static, so - per the @Redirect
    // contract - the handler takes only that method's own arguments. Both conditions therefore give
    // the same signature: (String url, String localPath).
    @Redirect(method = "setup", require = 1, at = @At(
            value = "INVOKE",
            target = "Lcom/github/alexthe666/citadel/web/WebHelper;getURLContents(Ljava/lang/String;Ljava/lang/String;)Ljava/io/BufferedReader;"))
    private BufferedReader wildfires$localPatreonList(String url, String localPath) {
        return NetworkGuard.localResource(localPath);
    }
}
