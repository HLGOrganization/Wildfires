package first.wildfires.mixin.network;

import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <h2>What this blocks</h2>
 * Ad Astra downloads its radio station list from {@code https://adastra.terrarium.earth/stations}
 * during start-up.
 * <h2>Why the request is redirected instead of the method being cancelled</h2>
 * {@code StationLoader.init()} first asks {@code readLocalStations()} (a file the pack may point at
 * with the {@code adastra.stations} system property) and only reaches for the network when that
 * returned nothing. Cancelling {@code init} would skip the local read as well, so the fetch itself
 * is replaced with {@code null}: {@code init} then takes its "no data" branch and leaves the
 * already-initialised station list untouched. No station list is bundled in the jar, so this is the
 * same result as an unreachable server, without the exception an unreachable server would throw.
 */
// @Pseudo: the target mod is optional, so it is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped if the
// mod is not installed. When the mod is present, require = 1 below still makes a renamed
// method fail loudly.
@Pseudo
@Mixin(targets = "earth.terrarium.adastra.common.utils.radio.StationLoader", remap = false)
public abstract class AdAstraStationsMixin {

    @Redirect(method = "init", require = 1, at = @At(
            value = "INVOKE",
            target = "Lcom/teamresourceful/resourcefullib/common/utils/WebUtils;getJson(Ljava/lang/String;)Lcom/google/gson/JsonObject;"))
    private static JsonObject wildfires$noRemoteStations(String url) {
        return null;
    }
}
