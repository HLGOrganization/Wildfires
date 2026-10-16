/*
 * Adapted from Caelum `1.20.1-2.0.0.0` (`mixin.MixinFogRenderer`).
 * SPDX-License-Identifier: MIT (see third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md).
 * Wildfires modifications: keeps the `setupColor` time redirect, but apparent time and sun
 * direction come from the unified model and the redirect is gated on sky ownership.
 */
package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.client.celestial.CelestialClientTime;
import first.wildfires.client.celestial.CelestialSkyOwnership;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Aligns clear-air fog brightness and dawn direction with the unified local sun. */
@Mixin(FogRenderer.class)
public abstract class CelestialFogRendererMixin {

    @ModifyExpressionValue(
            method = "setupColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private static float wildfires$localFogTime(float original, Camera camera, float partialTick,
                                                 ClientLevel level, int renderDistanceChunks,
                                                 float bossColorModifier) {
        if (!wildfires$usesLocalCelestialVisuals(level)) {
            return original;
        }
        CelestialState state = CelestialClientStateCache.stateOrNull(
                level, camera.getPosition(), partialTick);
        return state == null ? original : CelestialClientTime.visualCelestialAngle(
                state.daylight().apparentDayTime(), state.solarEclipse(), original);
    }

    @Redirect(
            method = "setupColor",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;dot(Lorg/joml/Vector3fc;)F", remap = false)
    )
    private static float wildfires$localSunriseDirection(Vector3f look, Vector3fc original,
                                                          Camera camera, float partialTick, ClientLevel level,
                                                          int renderDistanceChunks, float bossColorModifier) {
        if (!wildfires$usesLocalCelestialVisuals(level)) {
            return look.dot(original);
        }
        CelestialState state = CelestialClientStateCache.stateOrNull(
                level, camera.getPosition(), partialTick);
        if (state == null) {
            return look.dot(original);
        }
        double x = state.sun().observerDirection().x();
        double z = -state.sun().observerDirection().z();
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-9D) {
            return 0.0F;
        }
        return look.dot((float) (x / length), 0.0F, (float) (z / length));
    }

    private static boolean wildfires$usesLocalCelestialVisuals(ClientLevel level) {
        return level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || CelestialSkyOwnership.usesWildfiresOverworldVisuals();
    }
}
