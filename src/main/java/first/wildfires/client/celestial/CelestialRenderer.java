/*
 * Adapted in part from Caelum `1.20.1-2.0.0.0` (`client.SkyUtils`, `mixin.MixinLevelRenderer`).
 * SPDX-License-Identifier: MIT (see third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md).
 * Wildfires modifications: the star pass keeps the upstream custom-buffer switch and the
 * latitude-driven celestial rotation, but latitude and year come from the unified model (TFE
 * hemisphere scale plus the TFC calendar). The planets, moon, eclipse layers, aurora and rainbow
 * around it are Wildfires' own rendering rather than the upstream sky rewrite.
 */
package first.wildfires.client.celestial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.celestial.CelestialDiscGeometry;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.client.space.NtmAscentAtmosphereVisuals;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Owns all main-overworld sky buffers and renders the unified celestial state in a fixed order. */
public final class CelestialRenderer {

    private static final ResourceLocation SUN = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/environment/moon_phases.png");
    private static final CelestialBodies[] ORDERED_BODIES = CelestialBodies.values();
    private static VertexBuffer skyBuffer;
    private static VertexBuffer darkBuffer;
    private static VertexBuffer vanillaStars;

    private CelestialRenderer() {}

    public static void render(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                              Matrix4f projectionMatrix, boolean foggy, Runnable setupFog) {
        setupFog.run();
        if (foggy || camera.getFluidInCamera() == FogType.POWDER_SNOW
                || camera.getFluidInCamera() == FogType.LAVA || blockedByEffect(camera)) {
            return;
        }
        CelestialState state = CelestialClientStateCache.stateOrNull(
                level, camera.getPosition(), partialTick);
        if (state == null) {
            return;
        }
        ensureBuffers();
        double visualDayTime = CelestialClientTime.visualApparentDayTime(
                state.daylight().apparentDayTime(), state.solarEclipse());
        CelestialVisualRules.VisibilityProducts visibility =
                CelestialVisualRules.prepareVisibility(visualDayTime, state.weatherVisibility());
        float ascentAtmosphere = NtmAscentAtmosphereVisuals.factor(level, camera);
        Vec3 skyColor = NtmAscentAtmosphereVisuals.fadeSky(
                level, camera, level.getSkyColor(camera.getPosition(), partialTick));
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            renderSkyBase(skyColor, poseStack, projectionMatrix);
            renderHorizon(level, partialTick, poseStack, projectionMatrix);
            renderTwilight(level, state, partialTick, ascentAtmosphere, poseStack);
            renderStars(state, visibility, poseStack, projectionMatrix, setupFog);
            renderSun(skyColor, state, visibility, poseStack);
            renderPlanets(state, visibility, poseStack);
            renderMoon(skyColor, state, visualDayTime, visibility, poseStack);
            RainbowRenderer.render(level, state, partialTick, poseStack);
            AuroraRenderer.render(level, state, partialTick, poseStack);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            setupFog.run();
        }
    }

    private static void renderSkyBase(Vec3 color, PoseStack poseStack, Matrix4f projectionMatrix) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor((float) color.x, (float) color.y, (float) color.z, 1.0F);
        ShaderInstance shader = RenderSystem.getShader();
        skyBuffer.bind();
        skyBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
        VertexBuffer.unbind();
    }

    private static void renderHorizon(ClientLevel level, float partialTick, PoseStack poseStack,
                                      Matrix4f projectionMatrix) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        double eyeBelowHorizon = minecraft.player.getEyePosition(partialTick).y
                - level.getLevelData().getHorizonHeight(level);
        if (eyeBelowHorizon < 0.0D) {
            RenderSystem.setShader(GameRenderer::getPositionShader);
            RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
            poseStack.pushPose();
            poseStack.translate(0.0F, 12.0F, 0.0F);
            darkBuffer.bind();
            darkBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, RenderSystem.getShader());
            VertexBuffer.unbind();
            poseStack.popPose();
        }
    }

    private static void renderTwilight(ClientLevel level, CelestialState state, float partialTick,
                                       float atmosphere, PoseStack poseStack) {
        double alpha = CelestialVisualRules.twilightAlpha(state.sun().altitudeRadians(),
                state.weatherVisibility()) * atmosphere;
        if (alpha <= 0.001D) return;
        float localAngle = CelestialClientTime.vanillaCelestialAngle(
                state.daylight().apparentDayTime(), Float.NaN);
        float[] sourceColor = level.effects().getSunriseColor(localAngle, partialTick);
        if (sourceColor == null) return;
        CelestialVisualRules.HorizonFrame horizon = CelestialVisualRules.horizonFrame(
                state.sun().observerDirection());
        Vec3 center = worldDirection(horizon.horizon()).scale(100.0D);
        Vec3 right = worldDirection(horizon.right());
        Vec3 up = worldDirection(horizon.up());
        float centerAlpha = (float) (sourceColor[3] * alpha);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        builder.vertex(matrix, (float) center.x, (float) center.y, (float) center.z)
                .color(sourceColor[0], sourceColor[1], sourceColor[2], centerAlpha).endVertex();
        for (int i = 0; i <= 24; i++) {
            double rightScale = CelestialVisualRules.twilightCosine(i) * 80.0D;
            double upScale = CelestialVisualRules.twilightSine(i) * 32.0D;
            builder.vertex(matrix,
                            (float) ((center.x + right.x * rightScale) + up.x * upScale),
                            (float) ((center.y + right.y * rightScale) + up.y * upScale),
                            (float) ((center.z + right.z * rightScale) + up.z * upScale))
                    .color(sourceColor[0], sourceColor[1], sourceColor[2], 0.0F).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void renderStars(CelestialState state,
                                    CelestialVisualRules.VisibilityProducts visibility,
                                    PoseStack poseStack,
                                    Matrix4f projectionMatrix, Runnable setupFog) {
        if (CelestialConfig.starsMode() == CelestialConfig.StarsMode.NONE) return;
        float brightness = (float) CelestialVisualRules.starShaderBrightness(
                visibility, CelestialConfig.starBrightness());
        if (brightness <= 0.001F) return;
        VertexBuffer buffer = CelestialConfig.starsMode() == CelestialConfig.StarsMode.CUSTOM
                ? StarDataManager.INSTANCE.customBuffer() : vanillaStars;
        if (buffer == null) return;
        enableAdditiveCelestialBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(brightness, brightness, brightness, brightness);
        FogRenderer.setupNoFog();
        poseStack.pushPose();
        poseStack.mulPoseMatrix(equatorialToWorld(state.latitudeRadians(),
                localSiderealAngle(state)));
        buffer.bind();
        buffer.drawWithShader(poseStack.last().pose(), projectionMatrix, RenderSystem.getShader());
        VertexBuffer.unbind();
        poseStack.popPose();
        setupFog.run();
    }

    private static void renderSun(Vec3 skyColor, CelestialState state,
                                  CelestialVisualRules.VisibilityProducts visibility,
                                  PoseStack poseStack) {
        boolean sunRenderable = CelestialVisualRules.celestialDiscRenderable(
                state.sun().observerDirection());
        float sunSize = (float) CelestialDiscGeometry.SUN_TEXTURE_HALF_SIZE * (float) state.sunScale();
        DiscFrame sunFrame = sunRenderable
                ? discFrame(state.sun().observerDirection(), state.celestialNorth()) : null;
        if (sunRenderable && CelestialVisualRules.sunSkyCoverVisible(visibility)) {
            drawCelestialPixelCover(poseStack, sunFrame,
                    (float) CelestialVisualRules.sunAtlasBodyHalfSize(sunSize),
                    skyColor.x, skyColor.y, skyColor.z);
        }
        enableAdditiveCelestialBlend();
        if (sunRenderable) {
            float weatherAlpha = (float) state.weatherVisibility();
            CelestialVisualRules.SunAppearance appearance = CelestialVisualRules.sunAppearance(
                    state.sun().altitudeRadians());
            CelestialVisualRules.SunTint tint = CelestialVisualRules.solarEclipseSunTint(state.solarEclipse());
            float red = (float) (appearance.red() * tint.red());
            float green = (float) (appearance.green() * tint.green());
            float blue = (float) (appearance.blue() * tint.blue());
            drawDisc(poseStack, sunFrame,
                    sunSize, SUN, 0.0F, 0.0F, 1.0F, 1.0F,
                    red, green, blue, weatherAlpha);
        }
    }

    /**
     * The Moon is always the nearest celestial layer. Its ordinary body cover, texture and eclipse
     * shadow therefore keep one stable order instead of inserting a second occultor at first contact.
     */
    private static void renderMoon(Vec3 skyColor, CelestialState state, double visualDayTime,
                                   CelestialVisualRules.VisibilityProducts visibility,
                                   PoseStack poseStack) {
        int moonPhase = state.moonPhase();
        boolean moonRenderable = CelestialVisualRules.celestialDiscRenderable(
                state.moon().observerDirection());
        if (!moonRenderable) {
            return;
        }
        DiscFrame moonFrame = discFrame(state.moon().observerDirection(), state.celestialNorth());
        boolean moonVisible = CelestialVisualRules.moonTextureVisible(moonPhase);
        float moonDistanceScale = (float) (CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM
                / state.moon().distance());
        float moonSize = (float) CelestialDiscGeometry.MOON_TEXTURE_HALF_SIZE
                * (float) state.moonScale() * moonDistanceScale;
        float moonBodyHalfSize = (float) CelestialVisualRules.moonAtlasBodyHalfSize(moonSize);
        float moonGlowRadius = (float) CelestialVisualRules.moonAtlasGlowRadius(moonSize);
        if (visibility.starVisibility() > 0.001D) {
            CelestialVisualRules.MoonHalo halo = CelestialVisualRules.moonHalo(
                    CelestialVisualRules.lunarEclipseMoonlight(state.moon().illuminatedFraction(),
                            state.lunarEclipse()), visibility);
            if (halo.centerAlpha() > 0.0D) {
                drawMoonHalo(poseStack, moonFrame,
                        moonGlowRadius * (float) halo.radiusMultiplier(), skyColor, (float) halo.centerAlpha());
            }
        }
        if (moonVisible) {
            CelestialVisualRules.SolarOccultorTint foreground = CelestialVisualRules.solarOccultorTint(
                    skyColor.x, skyColor.y, skyColor.z, state.solarEclipse());
            drawCelestialPixelCover(poseStack, moonFrame,
                    moonBodyHalfSize, foreground.red(), foreground.green(), foreground.blue());
        }
        float moonAlpha = (float) CelestialVisualRules.solarOccultorMoonAlpha(
                visibility, state.solarEclipse());
        CelestialVisualRules.MoonTint ordinaryMoonTint = CelestialVisualRules.moonSkyTint(
                skyColor.x, skyColor.y, skyColor.z, visibility);
        double blueMoonIntensity = CelestialVisualRules.supermoonBlueIntensity(
                visualDayTime, visibility, state.supermoon(),
                CelestialVisualRules.lunarEclipseTintCoverage(state.lunarEclipseRegion()),
                state.sun().altitudeRadians(), state.moon().altitudeRadians());
        CelestialVisualRules.MoonTint moonTint = CelestialVisualRules.supermoonTint(
                ordinaryMoonTint, blueMoonIntensity);
        if (moonVisible && moonAlpha > 0.0F) {
            int column = moonPhase % 4;
            int row = moonPhase / 4 % 2;
            float u0 = column / 4.0F;
            float v0 = row / 2.0F;
            float u1 = (column + 1) / 4.0F;
            float v1 = (row + 1) / 2.0F;
            enableAdditiveCelestialBlend();
            drawRoundTexturedDisc(poseStack, moonFrame,
                    moonSize, MOON, u0, v0, u1, v1,
                    (float) moonTint.red(), (float) moonTint.green(), (float) moonTint.blue(), moonAlpha);
        }
        LunarEclipseRenderer.render(state, moonFrame, poseStack, moonBodyHalfSize, skyColor,
                moonTint, moonAlpha);
    }

    private static void renderPlanets(CelestialState state,
                                      CelestialVisualRules.VisibilityProducts visibility,
                                      PoseStack poseStack) {
        if (!CelestialConfig.planets()) return;
        double planetVisibility = CelestialVisualRules.planetVisibility(visibility);
        float night = (float) planetVisibility;
        if (night <= 0.001F) return;
        enableAdditiveCelestialBlend();
        List<CelestialBodyState> bodies = state.orbitingBodies();
        float alpha = (float) planetVisibility;
        for (int bodyIndex = 0; bodyIndex < bodies.size(); bodyIndex++) {
            CelestialBodyState body = bodies.get(bodyIndex);
            CelestialBodies definition = bodyDefinitionAt(body, bodyIndex);
            if (definition == null) continue;
            float size = (float) CelestialVisualRules.planetRenderRadius(body.angularRadiusRadians(),
                    definition.scaleFactor(), CelestialConfig.planetScale());
            if (size <= 0.0F) continue;
            CelestialVector renderDirection = body.observerDirection();
            if (definition.parent() != null) {
                CelestialBodyState parent = bodyAtDefinition(bodies, definition.parent());
                if (parent != null) {
                    renderDirection = CelestialVisualRules.satelliteRenderDirection(
                            parent.observerDirection(), body.observerDirection());
                }
            }
            if (!CelestialVisualRules.celestialDiscRenderable(renderDirection)) continue;
            drawDisc(poseStack, renderDirection, state.celestialNorth(), size, definition.renderTexture(),
                    0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, alpha);
        }
    }

    /** Uses the authoritative enum order when it is still present, with the old ID lookup as fallback. */
    static CelestialBodies bodyDefinitionAt(CelestialBodyState body, int bodyIndex) {
        if (bodyIndex >= 0 && bodyIndex < ORDERED_BODIES.length) {
            CelestialBodies ordered = ORDERED_BODIES[bodyIndex];
            if (ordered.id().equals(body.id())) {
                return ordered;
            }
        }
        return CelestialBodies.byId(body.id());
    }

    private static CelestialBodyState bodyAtDefinition(List<CelestialBodyState> bodies,
                                                        CelestialBodies definition) {
        int index = definition.ordinal();
        if (index < bodies.size()) {
            CelestialBodyState candidate = bodies.get(index);
            if (candidate.id().equals(definition.id())) {
                return candidate;
            }
        }
        for (CelestialBodyState candidate : bodies) {
            if (candidate.id().equals(definition.id())) {
                return candidate;
            }
        }
        return null;
    }

    private static void drawDisc(PoseStack poseStack, CelestialVector apiDirection,
                                 CelestialVector celestialNorth, float size,
                                 ResourceLocation texture, float u0, float v0, float u1, float v1,
                                 float red, float green, float blue, float alpha) {
        Vec3 direction = worldDirection(apiDirection);
        Basis basis = basis(apiDirection, celestialNorth);
        drawDisc(poseStack, direction, basis.right(), basis.up(), size, texture,
                u0, v0, u1, v1, red, green, blue, alpha);
    }

    private static void drawDisc(PoseStack poseStack, DiscFrame frame, float size,
                                 ResourceLocation texture, float u0, float v0, float u1, float v1,
                                 float red, float green, float blue, float alpha) {
        drawDisc(poseStack, frame.direction(), frame.right(), frame.up(), size, texture,
                u0, v0, u1, v1, red, green, blue, alpha);
    }

    private static void drawDisc(PoseStack poseStack, Vec3 direction, Vec3 basisRight,
                                 Vec3 basisUp, float size, ResourceLocation texture,
                                 float u0, float v0, float u1, float v1,
                                 float red, float green, float blue, float alpha) {
        double centerX = direction.x * CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        double centerY = direction.y * CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        double centerZ = direction.z * CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        double rightX = basisRight.x * size;
        double rightY = basisRight.y * size;
        double rightZ = basisRight.z * size;
        double upX = basisUp.x * size;
        double upY = basisUp.y * size;
        double upZ = basisUp.z * size;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, matrix, (centerX - rightX) - upX,
                (centerY - rightY) - upY, (centerZ - rightZ) - upZ, u0, v1);
        vertex(builder, matrix, (centerX + rightX) - upX,
                (centerY + rightY) - upY, (centerZ + rightZ) - upZ, u1, v1);
        vertex(builder, matrix, (centerX + rightX) + upX,
                (centerY + rightY) + upY, (centerZ + rightZ) + upZ, u1, v0);
        vertex(builder, matrix, (centerX - rightX) + upX,
                (centerY - rightY) + upY, (centerZ - rightZ) + upZ, u0, v0);
        BufferUploader.drawWithShader(builder.end());
    }

    /** Clips an atlas cell to a round sky body so its opaque rectangular background cannot be exposed. */
    private static void drawRoundTexturedDisc(PoseStack poseStack, DiscFrame frame, float size,
                                              ResourceLocation texture, float u0, float v0, float u1, float v1,
                                              float red, float green, float blue, float alpha) {
        Vec3 direction = frame.direction();
        double centerX = direction.x * CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        double centerY = direction.y * CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        double centerZ = direction.z * CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        double rightX = frame.right().x * size;
        double rightY = frame.right().y * size;
        double rightZ = frame.right().z * size;
        double upX = frame.up().x * size;
        double upY = frame.up().y * size;
        double upZ = frame.up().z * size;
        float centerU = (u0 + u1) * 0.5F;
        float centerV = (v0 + v1) * 0.5F;
        float radiusU = (u1 - u0) * 0.499F;
        float radiusV = (v1 - v0) * 0.499F;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, matrix, centerX, centerY, centerZ, centerU, centerV);
        for (int index = 0; index <= 48; index++) {
            double cosine = CelestialVisualRules.discCosine(index);
            double sine = CelestialVisualRules.discSine(index);
            vertex(builder, matrix, (centerX + rightX * cosine) + upX * sine,
                    (centerY + rightY * cosine) + upY * sine,
                    (centerZ + rightZ * cosine) + upZ * sine,
                    centerU + radiusU * (float) cosine,
                    centerV - radiusV * (float) sine);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Vanilla sun/moon textures use a centered 8x8 physical body that must occult stars. */
    private static void drawCelestialPixelCover(PoseStack poseStack, DiscFrame frame, float halfSize,
                                                double red, double green, double blue) {
        if (!(halfSize > 0.0F) || !Float.isFinite(halfSize)) return;
        Vec3 direction = frame.direction();
        double centerX = direction.x * CelestialDiscGeometry.PIXEL_COVER_RADIUS;
        double centerY = direction.y * CelestialDiscGeometry.PIXEL_COVER_RADIUS;
        double centerZ = direction.z * CelestialDiscGeometry.PIXEL_COVER_RADIUS;
        double rightX = frame.right().x * halfSize;
        double rightY = frame.right().y * halfSize;
        double rightZ = frame.right().z * halfSize;
        double upX = frame.up().x * halfSize;
        double upY = frame.up().y * halfSize;
        double upZ = frame.up().z * halfSize;
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorVertex(builder, matrix, (centerX - rightX) + upX,
                (centerY - rightY) + upY, (centerZ - rightZ) + upZ, red, green, blue);
        colorVertex(builder, matrix, (centerX + rightX) + upX,
                (centerY + rightY) + upY, (centerZ + rightZ) + upZ, red, green, blue);
        colorVertex(builder, matrix, (centerX + rightX) - upX,
                (centerY + rightY) - upY, (centerZ + rightZ) - upZ, red, green, blue);
        colorVertex(builder, matrix, (centerX - rightX) - upX,
                (centerY - rightY) - upY, (centerZ - rightZ) - upZ, red, green, blue);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void colorVertex(BufferBuilder builder, Matrix4f matrix,
                                    double x, double y, double z,
                                    double red, double green, double blue) {
        builder.vertex(matrix, (float) x, (float) y, (float) z)
                .color((float) red, (float) green, (float) blue, 1.0F).endVertex();
    }

    /** Radial moonlight veil drawn after stars; it brightens the local sky while reducing star contrast. */
    private static void drawMoonHalo(PoseStack poseStack, DiscFrame frame, float radius, Vec3 skyColor,
                                     float centerAlpha) {
        if (!(radius > 0.0F) || !Float.isFinite(radius)
                || !(centerAlpha > 0.0F) || !Float.isFinite(centerAlpha)) return;
        Vec3 direction = frame.direction();
        double centerX = direction.x * 99.85D;
        double centerY = direction.y * 99.85D;
        double centerZ = direction.z * 99.85D;
        float red = (float) clamp(skyColor.x + 0.16D, 0.0D, 1.0D);
        float green = (float) clamp(skyColor.y + 0.20D, 0.0D, 1.0D);
        float blue = (float) clamp(skyColor.z + 0.32D, 0.0D, 1.0D);
        enableAlphaCelestialBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, (float) centerX, (float) centerY, (float) centerZ)
                .color(red, green, blue, centerAlpha).endVertex();
        for (int index = 0; index <= 48; index++) {
            double rightScale = CelestialVisualRules.discCosine(index) * radius;
            double upScale = CelestialVisualRules.discSine(index) * radius;
            builder.vertex(matrix,
                            (float) ((centerX + frame.right().x * rightScale)
                                    + frame.up().x * upScale),
                            (float) ((centerY + frame.right().y * rightScale)
                                    + frame.up().y * upScale),
                            (float) ((centerZ + frame.right().z * rightScale)
                                    + frame.up().z * upScale))
                    .color(red, green, blue, 0.0F).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Matches the vanilla/TFC sky blend: black texels add nothing instead of becoming opaque quads. */
    private static void enableAdditiveCelestialBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    /** Eclipse layers must darken the destination; additive blending cannot render a shadow. */
    private static void enableAlphaCelestialBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               double x, double y, double z, float u, float v) {
        builder.vertex(matrix, (float) x, (float) y, (float) z).uv(u, v).endVertex();
    }

    static Vec3 worldDirection(CelestialVector vector) {
        return new Vec3(vector.x(), vector.y(), -vector.z()).normalize();
    }

    static DiscFrame discFrame(CelestialVector direction, CelestialVector celestialNorth) {
        Vec3 worldDirection = worldDirection(direction);
        Basis basis = basis(direction, celestialNorth);
        return new DiscFrame(worldDirection, basis.right(), basis.up());
    }

    private static Basis basis(CelestialVector direction, CelestialVector celestialNorth) {
        CelestialDiscGeometry.Basis basis = CelestialVisualRules.stableDiscBasis(direction, celestialNorth);
        return new Basis(worldDirection(basis.right()), worldDirection(basis.up()));
    }

    private static Matrix4f equatorialToWorld(double latitude, double sidereal) {
        double sinL = Math.sin(sidereal);
        double cosL = Math.cos(sidereal);
        double sinLat = Math.sin(latitude);
        double cosLat = Math.cos(latitude);
        return new Matrix4f()
                .m00((float) -sinL).m01((float) (cosLat * cosL)).m02((float) (sinLat * cosL))
                .m10((float) cosL).m11((float) (cosLat * sinL)).m12((float) (sinLat * sinL))
                .m20(0.0F).m21((float) sinLat).m22((float) -cosLat);
    }

    private static double localSiderealAngle(CelestialState state) {
        CelestialVector sun = state.sun().geocentricPosition().normalized();
        double sunRightAscension = Math.atan2(sun.y(), sun.x());
        return sunRightAscension + CelestialMath.TAU * (state.fractionOfDay() - 0.5D);
    }

    private static boolean blockedByEffect(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private static void ensureBuffers() {
        RenderSystem.assertOnRenderThread();
        if (skyBuffer == null) skyBuffer = uploadSkyDisc(16.0F);
        if (darkBuffer == null) darkBuffer = uploadSkyDisc(-16.0F);
        if (vanillaStars == null) vanillaStars = uploadVanillaStars();
    }

    private static VertexBuffer uploadSkyDisc(float y) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        float radius = Math.signum(y) * 512.0F;
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        builder.vertex(0.0D, y, 0.0D).endVertex();
        for (int angle = -180; angle <= 180; angle += 45) {
            builder.vertex(radius * Math.cos(Math.toRadians(angle)), y,
                    512.0F * Math.sin(Math.toRadians(angle))).endVertex();
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    private static VertexBuffer uploadVanillaStars() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        RandomSource random = RandomSource.create(10842L);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 1500; i++) {
            double x = random.nextFloat() * 2.0F - 1.0F;
            double y = random.nextFloat() * 2.0F - 1.0F;
            double z = random.nextFloat() * 2.0F - 1.0F;
            double length = x * x + y * y + z * z;
            if (length >= 1.0D || length <= 0.01D) continue;
            length = 1.0D / Math.sqrt(length);
            x *= length;
            y *= length;
            z *= length;
            appendVanillaStar(builder, random, x, y, z, 0.15D + random.nextFloat() * 0.1D);
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    private static void appendVanillaStar(BufferBuilder builder, RandomSource random, double x, double y,
                                          double z, double size) {
        double yaw = Math.atan2(x, z);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double pitch = Math.atan2(Math.sqrt(x * x + z * z), y);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        double roll = random.nextDouble() * Math.PI * 2.0D;
        double sinRoll = Math.sin(roll);
        double cosRoll = Math.cos(roll);
        for (int corner = 0; corner < 4; corner++) {
            double a = ((corner & 2) - 1) * size;
            double b = (((corner + 1) & 2) - 1) * size;
            double ra = a * cosRoll - b * sinRoll;
            double rb = b * cosRoll + a * sinRoll;
            double vertical = ra * sinPitch;
            double depth = -ra * cosPitch;
            double offsetX = depth * sinYaw - rb * cosYaw;
            double offsetZ = rb * sinYaw + depth * cosYaw;
            builder.vertex(x * 100.0D + offsetX, y * 100.0D + vertical, z * 100.0D + offsetZ)
                    .color(255, 255, 255, 255).endVertex();
        }
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        if (skyBuffer != null) skyBuffer.close();
        if (darkBuffer != null) darkBuffer.close();
        if (vanillaStars != null) vanillaStars.close();
        skyBuffer = null;
        darkBuffer = null;
        vanillaStars = null;
        StarDataManager.INSTANCE.close();
        RainbowRenderer.reset();
        AuroraRenderer.reset();
        CelestialClientStateCache.reset();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record DiscFrame(Vec3 direction, Vec3 right, Vec3 up) {}

    private record Basis(Vec3 right, Vec3 up) {}
}
