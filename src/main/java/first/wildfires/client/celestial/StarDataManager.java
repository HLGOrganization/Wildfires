/*
 * Adapted from Caelum `1.20.1-2.0.0.0` (`client.data.StarDataManager`,
 * `client.event.ClientReloadListeners`).
 * SPDX-License-Identifier: MIT (see third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md).
 * Wildfires modifications: keeps the resource-reload registration and the merged star-table
 * contract, but the buffer it builds is consumed by the unified sky renderer instead of
 * replacing vanilla's star buffer through an ordinal injection.
 */
package first.wildfires.client.celestial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import first.wildfires.celestial.CelestialConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/** Loads every namespace's stars/*.json resources and owns the resulting GPU buffer. */
public final class StarDataManager extends SimpleJsonResourceReloadListener implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    public static final StarDataManager INSTANCE = new StarDataManager();

    private volatile List<StarTableLoader.Star> loadedStars = List.of();
    private VertexBuffer customBuffer;
    private ConfigSignature builtFor;
    private boolean dirty = true;

    private StarDataManager() {
        super(GSON, "stars");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        StarTableLoader.Result result = StarTableLoader.load(resources);
        result.errors().forEach(error ->
                LOGGER.error("Ignoring invalid star table {}: {}", error.resource(), error.message()));
        loadedStars = result.stars();
        dirty = true;
        LOGGER.info("Loaded {} Wildfires/Caelum stellar records from {} resource tables", result.stars().size(),
                result.resourceCount());
    }

    public VertexBuffer customBuffer() {
        RenderSystem.assertOnRenderThread();
        double maxMagnitude = CelestialConfig.maxMagnitude();
        boolean colors = CelestialConfig.starColors();
        double size = CelestialConfig.starSize();
        if (dirty || builtFor == null || !builtFor.matches(maxMagnitude, colors, size)) {
            rebuild(new ConfigSignature(maxMagnitude, colors, size));
        }
        return customBuffer;
    }

    /** Immutable catalog snapshot used by the in-game planetarium. */
    List<StarTableLoader.Star> stars() {
        return loadedStars;
    }

    private void rebuild(ConfigSignature signature) {
        close();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        MagnitudeRange range = magnitudeRange(loadedStars);
        double catalogMin = range.minimum();
        double catalogMax = range.maximum();
        RandomSource random = RandomSource.create(10842L);
        for (StarTableLoader.Star star : loadedStars) {
            if (!visibleMagnitude(star.magnitude(), signature.maxMagnitude())) {
                continue;
            }
            CelestialVisualRules.StarAppearance appearance = CelestialVisualRules.starAppearance(
                    star.magnitude(), catalogMin, catalogMax, signature.size());
            double size = appearance.radius();
            if (size <= 0.0D) continue;
            int color = signature.colors() ? star.rgb() : 0xFFFFFF;
            int red = color >> 16 & 255;
            int green = color >> 8 & 255;
            int blue = color & 255;
            int alpha = (int) Math.round(255.0D * clamp(appearance.alpha(), 0.0D, 1.0D));
            appendStar(builder, random, star.ascension(), star.declination(), size, red, green, blue, alpha);
        }
        customBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        customBuffer.bind();
        customBuffer.upload(builder.end());
        VertexBuffer.unbind();
        builtFor = signature;
        dirty = false;
    }

    private static void appendStar(BufferBuilder builder, RandomSource random, double ascension,
                                   double declination, double size, int red, int green, int blue, int alpha) {
        double cosineDeclination = Math.cos(declination);
        double x = cosineDeclination * Math.cos(ascension);
        double y = cosineDeclination * Math.sin(ascension);
        double z = Math.sin(declination);
        double centerX = x * 100.0D;
        double centerY = y * 100.0D;
        double centerZ = z * 100.0D;
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
            double rotatedA = a * cosRoll - b * sinRoll;
            double rotatedB = b * cosRoll + a * sinRoll;
            double vertical = rotatedA * sinPitch;
            double depth = -rotatedA * cosPitch;
            double offsetX = depth * sinYaw - rotatedB * cosYaw;
            double offsetZ = rotatedB * sinYaw + depth * cosYaw;
            builder.vertex(centerX + offsetX, centerY + vertical, centerZ + offsetZ)
                    .color(red, green, blue, alpha).endVertex();
        }
    }

    /** Finite catalog reduction in the same encounter order used by DoubleStream min/max. */
    static MagnitudeRange magnitudeRange(List<StarTableLoader.Star> stars) {
        Objects.requireNonNull(stars, "stars");
        if (stars.isEmpty()) {
            return MagnitudeRange.ZERO;
        }
        double minimum = stars.get(0).magnitude();
        double maximum = minimum;
        for (int index = 1; index < stars.size(); index++) {
            double magnitude = stars.get(index).magnitude();
            minimum = Math.min(minimum, magnitude);
            maximum = Math.max(maximum, magnitude);
        }
        return new MagnitudeRange(minimum, maximum);
    }

    static boolean visibleMagnitude(double magnitude, double maximumMagnitude) {
        return magnitude <= maximumMagnitude;
    }

    @Override
    public void close() {
        if (customBuffer != null) {
            customBuffer.close();
            customBuffer = null;
        }
        builtFor = null;
        dirty = true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record ConfigSignature(double maxMagnitude, boolean colors, double size) {
        boolean matches(double currentMaxMagnitude, boolean currentColors, double currentSize) {
            return Double.compare(maxMagnitude, currentMaxMagnitude) == 0
                    && colors == currentColors
                    && Double.compare(size, currentSize) == 0;
        }
    }

    record MagnitudeRange(double minimum, double maximum) {
        private static final MagnitudeRange ZERO = new MagnitudeRange(0.0D, 0.0D);
    }
}
