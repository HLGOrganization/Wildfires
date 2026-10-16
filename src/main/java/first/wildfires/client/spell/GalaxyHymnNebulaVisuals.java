package first.wildfires.client.spell;

/*
 * Presentation layer for the Galaxy Hymn impact nebula.
 * The rendered volume is the adapted user-supplied nebula shader set; its tone
 * mapping derives from 5.txt, attributed there to sonicether's
 * "Gargantua With HDR Bloom". License: non-commercial, share-alike, attribution
 * (Shadertoy platform default); not part of the Wildfires ARR partition.
 * Evidence: third_party/nebula/user-supplied-2026-08-24/PROVENANCE.md
 */
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** World-positioned bipolar nebula which unfolds from the final center-star point. */
public final class GalaxyHymnNebulaVisuals {

    public static final int EXPANSION_TICKS = 18;
    public static final int FADE_START_TICKS = 82;
    public static final int LIFETIME_TICKS = 122;
    public static final float FULL_RADIUS = 14.0F;

    private static ClientLevel activeLevel;
    private static Vec3 center;
    private static long startGameTime;
    private static int visualSeed;

    private GalaxyHymnNebulaVisuals() {
    }

    public static void trigger(ClientLevel level, Vec3 burstCenter, int seed) {
        activeLevel = level;
        center = burstCenter;
        startGameTime = level.getGameTime();
        visualSeed = seed;
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || minecraft.level == null || activeLevel != minecraft.level || center == null) {
            return;
        }
        float age = age(event.getPartialTick());
        if (age >= LIFETIME_TICKS) {
            return;
        }

        float expansion = smoothstep(Mth.clamp(age / EXPANSION_TICKS, 0.0F, 1.0F));
        // Start as the center star's contracted point, then unfold the full volume quickly.
        float radius = FULL_RADIUS * Math.max(0.012F, expansion);
        float opacity = age < FADE_START_TICKS ? 1.0F
                : 1.0F - smoothstep(Mth.clamp((age - FADE_START_TICKS)
                / (LIFETIME_TICKS - FADE_START_TICKS), 0.0F, 1.0F));
        if (opacity <= 0.001F) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 relativeCenter = center.subtract(camera.getPosition());
        Vector3f leftVector = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        Vec3 left = new Vec3(leftVector.x(), leftVector.y(), leftVector.z()).scale(radius);
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z()).scale(radius);

        // AFTER_WEATHER is after vanilla clouds and precipitation while retaining the same
        // valid level PoseStack. Ignore earlier depth, but never write depth back to the world.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            modelViewStack.setIdentity();
            modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();
            Supplier<ShaderInstance> baseShader = GalaxyHymnNebulaShader.prepare(
                    opacity, visualSeed, 0.0F, relativeCenter, radius);
            if (baseShader == null) {
                return;
            }
            // The base shader JSON owns premultiplied-alpha blending. ShaderInstance.apply()
            // reapplies that state at draw time, so a Java-side blendFunc here is not reliable.
            drawBillboard(baseShader, relativeCenter, left, up);

            // The second pass carries only bright filaments, the cyan core and
            // stars. Keeping bloom additive prevents the red/brown cloud body
            // from washing out into the old uniform blue sphere.
            Supplier<ShaderInstance> glowShader = GalaxyHymnNebulaShader.prepare(
                    opacity, visualSeed, 1.0F, relativeCenter, radius);
            drawBillboard(glowShader, relativeCenter, left, up);
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    public static void reset() {
        activeLevel = null;
        center = null;
        startGameTime = 0L;
        visualSeed = 0;
    }

    private static void vertex(BufferBuilder builder, Vec3 point, float u, float v) {
        builder.vertex(point.x, point.y, point.z).uv(u, v).endVertex();
    }

    private static void drawBillboard(Supplier<ShaderInstance> shader, Vec3 centerPoint,
                                      Vec3 left, Vec3 up) {
        RenderSystem.setShader(shader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, centerPoint.subtract(left).subtract(up), 0.0F, 1.0F);
        vertex(builder, centerPoint.add(left).subtract(up), 1.0F, 1.0F);
        vertex(builder, centerPoint.add(left).add(up), 1.0F, 0.0F);
        vertex(builder, centerPoint.subtract(left).add(up), 0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.end());
    }

    private static float age(float partialTick) {
        return activeLevel == null ? LIFETIME_TICKS
                : Math.max(0.0F, activeLevel.getGameTime() - startGameTime + partialTick);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
