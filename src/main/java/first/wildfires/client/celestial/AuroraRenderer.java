/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/shaders/aurora/*`, `tfccaelum/util/AuroraHandler`
 * and `tfccaelum/util/AuroraFactory`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: retains the 24 colour sets, 12 band geometries, 1-3 bands and the
 * 512-tick fade design, but updates static VBO geometry only when the event or hemisphere changes,
 * computes the wave in the vertex shader, and does not reproduce the upstream integer-division
 * age-ratio defect.
 */
package first.wildfires.client.celestial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.celestial.CelestialEventRules;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

/** Polar-night aurora using TFCCaelum's visual presets through a Forge-managed Wildfires shader. */
public final class AuroraRenderer {

    private static final int FADE_TICKS = 512;
    private static final Palette[] PALETTES = createPalettes();
    private static final Geometry[] GEOMETRIES = createGeometries();

    private static ShaderBindings bindings;
    private static long activeKey = Long.MIN_VALUE;
    private static double lastAnimationTick;
    private static double fadeAge;
    private static AuroraStyle activeStyle;
    private static VertexBuffer geometryBuffer;
    private static double activePole;

    private AuroraRenderer() {}

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("aurora"),
                DefaultVertexFormat.POSITION_TEX), loaded -> bindings = ShaderBindings.create(loaded));
    }

    static void render(ClientLevel level, CelestialState state, float partialTick, PoseStack poseStack) {
        ShaderBindings active = bindings;
        if (active == null) {
            return;
        }
        boolean legacy = CelestialConfig.auroraMode() == CelestialConfig.AuroraMode.LEGACY_GLOBAL;
        long eventKey = CelestialEventRules.auroraEventKey(state.calendarTicks(), state.latitudeRadians());
        double roll = CelestialEventRules.auroraRoll(eventKey);
        boolean visible = CelestialVisualRules.auroraVisible(legacy,
                CelestialConfig.auroraMode() == CelestialConfig.AuroraMode.DISABLED,
                CelestialConfig.auroraBands(), state.latitudeRadians(), state.sun().altitudeRadians(), roll);
        if (!visible) {
            resetAppearance();
            return;
        }

        double pole = state.latitudeRadians() >= 0.0D ? -1.0D : 1.0D;
        double animationTick = level.getGameTime() + partialTick;
        updateAppearance(eventKey, animationTick, CelestialConfig.auroraBands(), pole);
        double lifecycleAlpha = Math.min(1.0D,
                activeStyle.geometry().alphaLimit() / 255.0D * (fadeAge / FADE_TICKS) * 2.0D);
        float alpha = (float) (lifecycleAlpha * state.weatherVisibility() * (legacy ? 1.0D
                : CelestialVisualRules.auroraNightFactor(state.sun().altitudeRadians())));
        if (alpha <= 0.001F) {
            return;
        }

        Palette colors = activeStyle.colors();
        Geometry geometry = activeStyle.geometry();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.disableCull();
        RenderSystem.setShader(active);
        active.time().set((level.getGameTime() + partialTick) / 20.0F * 0.75F);
        active.resolution().set(
                (geometry.nodes() - 1) * geometry.nodeWidth(), 180.0F);
        active.nodeWidth().set(geometry.nodeWidth());
        setColor(active.topColor(), colors.top());
        setColor(active.middleColor(), colors.middle());
        setColor(active.bottomColor(), colors.bottom());
        active.alpha().set(alpha);
        active.wavePhase().set((float) wavePhaseDegrees(animationTick));

        active.pole().set((float) pole);
        if (geometryBuffer != null) {
            geometryBuffer.bind();
            geometryBuffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(),
                    active.shader());
            VertexBuffer.unbind();
        }
    }

    private static void appendBand(BufferBuilder builder, AuroraStyle style, int band, double pole) {
        double[] pathX = style.pathX();
        double[] pathZ = style.pathZ();
        float panelTextureWidth = 1.0F / (pathX.length - 1);
        double bandOffset = pole * style.offset() * band * 0.35D;
        for (int index = 0; index < pathX.length - 1; index++) {
            float x0 = (float) pathX[index];
            float x1 = (float) pathX[index + 1];
            float z0 = (float) (pole * 82.0D + pathZ[index] + bandOffset);
            float z1 = (float) (pole * 82.0D + pathZ[index + 1] + bandOffset);
            float u0 = index * panelTextureWidth;
            float u1 = (index + 1) * panelTextureWidth;
            builder.vertex(x0, 12.0F, z0).uv(u0, 0.0F).endVertex();
            builder.vertex(x1, 12.0F, z1).uv(u1, 0.0F).endVertex();
            builder.vertex(x1, 65.0F, z1).uv(u1, 0.5F).endVertex();
            builder.vertex(x0, 65.0F, z0).uv(u0, 0.5F).endVertex();
        }
    }

    private static void setColor(AbstractUniform uniform, Rgb color) {
        uniform.set(color.red(), color.green(), color.blue(), 1.0F);
    }

    private static void updateAppearance(long eventKey, double animationTick, int maxBands, double pole) {
        if (eventKey != activeKey || activeStyle == null || !Double.isFinite(animationTick)
                || animationTick < lastAnimationTick
                || pole != activePole) {
            activeKey = eventKey;
            lastAnimationTick = Double.isFinite(animationTick) ? animationTick : 0.0D;
            fadeAge = 0.0D;
            activeStyle = styleFor(eventKey, maxBands);
            activePole = pole;
            rebuildGeometry();
            return;
        }
        fadeAge = advanceFadeAge(fadeAge, lastAnimationTick, animationTick);
        lastAnimationTick = animationTick;
    }

    static AuroraStyle styleFor(long seed, int maxBands) {
        Random random = new Random(seed);
        int bandCount = Math.min(random.nextInt(3) + 1, Math.max(1, maxBands));
        float offset = random.nextInt(20) + 20.0F;
        Palette colors = PALETTES[random.nextInt(PALETTES.length)];
        Geometry geometry = GEOMETRIES[random.nextInt(GEOMETRIES.length)];
        double[][] path = generatePath(random, geometry);
        return new AuroraStyle(colors, geometry, bandCount, offset, path[0], path[1]);
    }

    private static double[][] generatePath(Random random, Geometry geometry) {
        int length = geometry.nodes();
        double[] x = new double[length];
        double[] z = new double[length];
        double[] angles = new double[length];
        int center = length / 2 - 1;
        double angleTotal = 0.0D;
        for (int group = length / 16 - 1; group >= 0; group--) {
            double angle = (random.nextFloat() - 0.5F) * 8.0F;
            angleTotal += angle;
            if (Math.abs(angleTotal) > 180.0D) {
                angle = -angle;
                angleTotal += angle;
            }
            for (int member = 7; member >= 0; member--) {
                int index = group * 8 + member;
                if (index == center) {
                    angles[index] = angle;
                } else {
                    double heading = angles[index + 1] + angle;
                    double radians = Math.toRadians(heading);
                    z[index] = z[index + 1] - Math.sin(radians) * geometry.nodeLength();
                    x[index] = x[index + 1] - Math.cos(radians) * geometry.nodeLength();
                    angles[index] = heading;
                }
            }
        }
        angleTotal = 0.0D;
        for (int group = length / 16; group < length / 8; group++) {
            double angle = (random.nextFloat() - 0.5F) * 8.0F;
            angleTotal += angle;
            if (Math.abs(angleTotal) > 180.0D) {
                angle = -angle;
                angleTotal += angle;
            }
            for (int member = 0; member < 8; member++) {
                int index = group * 8 + member - 1;
                double heading = angles[index] + angle;
                double radians = Math.toRadians(heading);
                z[index + 1] = z[index] + Math.sin(radians) * geometry.nodeLength();
                x[index + 1] = x[index] + Math.cos(radians) * geometry.nodeLength();
                angles[index + 1] = heading;
            }
        }
        normalizePath(x, z);
        return new double[][]{x, z};
    }

    private static void normalizePath(double[] x, double[] z) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < x.length; index++) {
            minX = Math.min(minX, x[index]);
            maxX = Math.max(maxX, x[index]);
            minZ = Math.min(minZ, z[index]);
            maxZ = Math.max(maxZ, z[index]);
        }
        double scale = 140.0D / Math.max(1.0D, Math.max(maxX - minX, maxZ - minZ));
        double centerX = (minX + maxX) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;
        for (int index = 0; index < x.length; index++) {
            x[index] = (x[index] - centerX) * scale;
            z[index] = (z[index] - centerZ) * scale;
        }
    }

    private record ShaderBindings(ShaderInstance shader,
                                  AbstractUniform time,
                                  AbstractUniform resolution,
                                  AbstractUniform nodeWidth,
                                  AbstractUniform topColor,
                                  AbstractUniform middleColor,
                                  AbstractUniform bottomColor,
                                  AbstractUniform alpha,
                                  AbstractUniform wavePhase,
                                  AbstractUniform pole)
            implements Supplier<ShaderInstance> {

        private static ShaderBindings create(ShaderInstance shader) {
            return new ShaderBindings(shader,
                    shader.safeGetUniform("Time"),
                    shader.safeGetUniform("Resolution"),
                    shader.safeGetUniform("NodeWidth"),
                    shader.safeGetUniform("TopColor"),
                    shader.safeGetUniform("MiddleColor"),
                    shader.safeGetUniform("BottomColor"),
                    shader.safeGetUniform("Alpha"),
                    shader.safeGetUniform("WavePhase"),
                    shader.safeGetUniform("Pole"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }

    static void reset() {
        resetAppearance();
    }

    private static void resetAppearance() {
        activeKey = Long.MIN_VALUE;
        lastAnimationTick = 0.0D;
        fadeAge = 0.0D;
        activeStyle = null;
        activePole = 0.0D;
        closeGeometry();
    }

    private static void rebuildGeometry() {
        closeGeometry();
        if (activeStyle == null) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int band = 0; band < activeStyle.bandCount(); band++) {
            appendBand(builder, activeStyle, band, activePole);
        }
        geometryBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        geometryBuffer.bind();
        geometryBuffer.upload(builder.end());
        VertexBuffer.unbind();
    }

    private static void closeGeometry() {
        if (geometryBuffer != null) {
            geometryBuffer.close();
            geometryBuffer = null;
        }
    }

    static double waveOffset(int nodeIndex, double cycleDegrees) {
        return Math.cos(Math.toRadians((nodeIndex << 3) + cycleDegrees));
    }

    static double wavePhaseDegrees(double animationTicks) {
        double phase = animationTicks * 0.75D % 360.0D;
        return phase < 0.0D ? phase + 360.0D : phase;
    }

    static double advanceFadeAge(double currentAge, double previousAnimationTick, double animationTick) {
        if (!Double.isFinite(currentAge) || !Double.isFinite(previousAnimationTick)
                || !Double.isFinite(animationTick) || animationTick < previousAnimationTick) {
            return 0.0D;
        }
        double age = Math.max(0.0D, Math.min(FADE_TICKS, currentAge));
        double elapsed = Math.max(0.0D, Math.min(FADE_TICKS, animationTick - previousAnimationTick));
        return Math.min(FADE_TICKS, age + elapsed);
    }

    static int paletteCount() {
        return PALETTES.length;
    }

    static int geometryCount() {
        return GEOMETRIES.length;
    }

    private static Palette[] createPalettes() {
        Rgb red = rgb(255, 0, 0);
        Rgb yellow = rgb(255, 255, 0);
        Rgb lightGreen = rgb(127, 255, 0);
        Rgb green = rgb(0, 255, 0);
        Rgb turquoise = rgb(0, 255, 127);
        Rgb cyan = rgb(0, 255, 255);
        Rgb blue = rgb(0, 0, 255);
        Rgb magenta = rgb(255, 0, 255);
        Rgb indigo = rgb(75, 0, 130);
        Rgb navy = rgb(0, 0, 128);
        Rgb auroraRed = new Rgb(1.0F, 0.0F, 0.0F);
        Rgb auroraGreen = new Rgb(0.5F, 1.0F, 0.0F);
        Rgb auroraBlue = new Rgb(0.0F, 0.8F, 1.0F);
        List<Palette> palettes = new ArrayList<>();
        palettes.add(palette(rgb(0, 255, 153), rgb(51, 255, 0)));
        palettes.add(palette(blue, green));
        palettes.add(palette(magenta, green));
        palettes.add(palette(indigo, green));
        palettes.add(palette(turquoise, lightGreen));
        palettes.add(palette(yellow, red));
        palettes.add(palette(green, red));
        palettes.add(palette(green, yellow));
        palettes.add(palette(red, yellow));
        palettes.add(palette(navy, indigo));
        palettes.add(palette(cyan, magenta));
        palettes.add(new Palette(auroraGreen, auroraRed, auroraBlue));
        addLuminanceVariants(palettes, 0.3F, yellow, red, green, blue, indigo,
                auroraGreen, auroraRed, auroraBlue);
        addLuminanceVariants(palettes, -0.3F, yellow, red, green, blue, indigo,
                auroraGreen, auroraRed, auroraBlue);
        return palettes.toArray(Palette[]::new);
    }

    private static void addLuminanceVariants(List<Palette> palettes, float amount,
                                             Rgb yellow, Rgb red, Rgb green, Rgb blue, Rgb indigo,
                                             Rgb auroraGreen, Rgb auroraRed, Rgb auroraBlue) {
        palettes.add(palette(yellow.luminance(amount), red.luminance(amount)));
        palettes.add(palette(green.luminance(amount), red.luminance(amount)));
        palettes.add(palette(green.luminance(amount), yellow.luminance(amount)));
        palettes.add(palette(blue.luminance(amount), green.luminance(amount)));
        palettes.add(palette(indigo.luminance(amount), green.luminance(amount)));
        palettes.add(new Palette(auroraGreen.luminance(amount), auroraRed.luminance(amount),
                auroraBlue.luminance(amount)));
    }

    private static Geometry[] createGeometries() {
        List<Geometry> geometries = new ArrayList<>();
        for (int alpha : new int[]{96, 80, 64}) {
            for (int nodes : new int[]{128, 64}) {
                for (float length : new float[]{30.0F, 15.0F}) {
                    geometries.add(new Geometry(nodes, length, 2.0F, alpha));
                }
            }
        }
        return geometries.toArray(Geometry[]::new);
    }

    private static Palette palette(Rgb bottom, Rgb top) {
        return new Palette(bottom, top, bottom);
    }

    private static Rgb rgb(int red, int green, int blue) {
        return new Rgb(red / 255.0F, green / 255.0F, blue / 255.0F);
    }

    record Rgb(float red, float green, float blue) {
        Rgb luminance(float amount) {
            return new Rgb(clamp(red * (1.0F + amount)), clamp(green * (1.0F + amount)),
                    clamp(blue * (1.0F + amount)));
        }

        private static float clamp(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }

    record Palette(Rgb bottom, Rgb top, Rgb middle) {}

    record Geometry(int nodes, float nodeLength, float nodeWidth, int alphaLimit) {}

    record AuroraStyle(Palette colors, Geometry geometry, int bandCount, float offset,
                       double[] pathX, double[] pathZ) {}
}
