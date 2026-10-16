/*
 * Adapted from Caelum `1.20.1-2.0.0.0` (`mixin.MixinClientLevel`).
 * SPDX-License-Identifier: MIT (see third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md).
 * Wildfires modifications: same sky-colour, cloud, star-brightness and lightmap redirect points,
 * but the substituted value is the local apparent time of the unified model, and the whole
 * modification is skipped while another shader pack owns the sky.
 */
package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.client.celestial.CelestialClientTime;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.client.celestial.CelestialSkyOwnership;
import first.wildfires.client.space.OrbitClientIllumination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses local apparent solar time inside client-only sky, cloud, star and lightmap calculations. */
@Mixin(ClientLevel.class)
public abstract class CelestialClientLevelMixin {

    @ModifyExpressionValue(
            method = "getSkyColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localSkyColorTime(float original, Vec3 observer, float partialTick) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!wildfires$usesLocalCelestialVisuals(level)) {
            return original;
        }
        CelestialState state = CelestialClientStateCache.stateOrNull(level, observer, partialTick);
        return state == null ? original : CelestialClientTime.visualCelestialAngle(
                state.daylight().apparentDayTime(), state.solarEclipse(), original);
    }

    @ModifyExpressionValue(
            method = "getCloudColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localCloudTime(float original, float partialTick) {
        return wildfires$localCameraTime(original, partialTick);
    }

    @ModifyExpressionValue(
            method = "getStarBrightness",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localStarTime(float original, float partialTick) {
        return wildfires$localCameraTime(original, partialTick);
    }

    @ModifyExpressionValue(
            method = "getSkyDarken",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localLightmapTime(float original, float partialTick) {
        return wildfires$localCameraTime(original, partialTick);
    }

    @ModifyReturnValue(method = "getSkyDarken", at = @At("RETURN"))
    private float wildfires$orbitSunBrightness(float original, float partialTick) {
        ClientLevel level = (ClientLevel) (Object) this;
        Vec3 observer = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return OrbitClientIllumination.resolve(level, observer, partialTick)
                .map(illumination -> (float) illumination.sunlight())
                .orElse(original);
    }

    private float wildfires$localCameraTime(float original, float partialTick) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!wildfires$usesLocalCelestialVisuals(level)) {
            return original;
        }
        Vec3 observer = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        CelestialState state = CelestialClientStateCache.stateOrNull(level, observer, partialTick);
        return state == null ? original : CelestialClientTime.visualCelestialAngle(
                state.daylight().apparentDayTime(), state.solarEclipse(), original);
    }

    private static boolean wildfires$usesLocalCelestialVisuals(ClientLevel level) {
        return level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || CelestialSkyOwnership.usesWildfiresOverworldVisuals();
    }
}
