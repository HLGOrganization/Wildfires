package first.wildfires.mixin.network;

import first.wildfires.compat.net.NetworkGuard;
import java.io.BufferedReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <h2>What this blocks</h2>
 * Alex's Caves downloads a list of other mods that break its world generation
 * ({@code raw.githubusercontent.com/AlexModGuy/AlexsCaves/.../mod_generation_conflicts.txt}) and
 * logs "Failed to load mod conflicts" when that download fails.
 * <h2>Why the request is redirected instead of the method being cancelled</h2>
 * The very same file ships inside the mod jar at
 * {@code assets/alexscaves/warning/mod_generation_conflicts.txt}, and the mod's own reader keeps
 * reading lines from whatever reader it is given - only a {@code null} reader skips the list. The
 * guard therefore hands back the bundled copy: the conflict warning stays accurate and complete,
 * offline, while the download and its failure log both disappear.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.AlexsCaves", remap = false)
public abstract class AlexsCavesConflictsMixin {

    // The handler must NOT be static: AlexsCaves.readModIncompatibilities is an instance method, and
    // Mixin requires the handler's 'static' modifier to match the target's
    // ("InvalidInjectionException: 'static' modifier of handler method does not match target").
    // A non-static handler is merged into the target class and invoked on it with 'this' taken from
    // the target's local 0, so the receiver is implicit and must NOT be added to the parameters.
    //
    // The redirected call WebHelper.getURLContents is itself static, so - per the @Redirect
    // contract - the handler takes only that method's own arguments. Both conditions therefore give
    // the same signature: (String url, String localPath).
    @Redirect(method = "readModIncompatibilities", require = 1, at = @At(
            value = "INVOKE",
            target = "Lcom/github/alexmodguy/alexscaves/server/misc/WebHelper;getURLContents(Ljava/lang/String;Ljava/lang/String;)Ljava/io/BufferedReader;"))
    private BufferedReader wildfires$localConflictList(String url, String localPath) {
        return NetworkGuard.localResource(localPath);
    }
}
