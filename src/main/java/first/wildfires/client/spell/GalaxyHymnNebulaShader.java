package first.wildfires.client.spell;

/*
 * The procedural cloud volume comes from the user-supplied 1.txt; the tone
 * mapping is adapted from 5.txt, which attributes its Buffer B/C/D and image
 * pass to sonicether's "Gargantua With HDR Bloom". The stars() routine credited to Shadertoy
 * fl2Bzd is not compiled, and the supplied opaque backdrop and background star
 * field are excluded at source. The Shadertoy feedback buffers are replaced by
 * a bounded Forge core-shader pass.
 * License: non-commercial, share-alike, attribution (Shadertoy platform
 * default); not part of the Wildfires ARR partition.
 * Evidence: third_party/nebula/user-supplied-2026-08-24/PROVENANCE.md
 */
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import first.wildfires.Wildfires;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;

/** Managed shader for the impact-frame point-to-nebula expansion. */
public final class GalaxyHymnNebulaShader {

    private static ShaderBindings baseBindings;
    private static ShaderBindings glowBindings;

    private GalaxyHymnNebulaShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("galaxy_hymn_nebula"), DefaultVertexFormat.POSITION_TEX),
                loaded -> baseBindings = ShaderBindings.create(loaded));
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("galaxy_hymn_nebula_glow"), DefaultVertexFormat.POSITION_TEX),
                loaded -> glowBindings = ShaderBindings.create(loaded));
    }

    public static Supplier<ShaderInstance> prepare(float opacity, int seed,
                                                   float glowPass, Vec3 relativeCenter,
                                                   float radius) {
        ShaderBindings active = glowPass < 0.5F ? baseBindings : glowBindings;
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || minecraft.level == null) {
            return null;
        }
        RenderSystem.setShader(active);
        active.opacity().set(opacity);
        active.seed().set((float) (seed & 0xFFFF) / 65535.0F);
        active.glowPass().set(glowPass);
        active.centerRelative().set((float) relativeCenter.x, (float) relativeCenter.y,
                (float) relativeCenter.z);
        active.radius().set(radius);
        active.screenSize().set((float) minecraft.getWindow().getWidth(),
                (float) minecraft.getWindow().getHeight());
        return active;
    }

    private record ShaderBindings(ShaderInstance shader, AbstractUniform opacity,
                                  AbstractUniform seed, AbstractUniform glowPass,
                                  AbstractUniform centerRelative, AbstractUniform radius,
                                  AbstractUniform screenSize)
            implements Supplier<ShaderInstance> {
        private static ShaderBindings create(ShaderInstance shader) {
            return new ShaderBindings(shader, shader.safeGetUniform("Opacity"),
                    shader.safeGetUniform("Seed"), shader.safeGetUniform("GlowPass"),
                    shader.safeGetUniform("CenterRelative"), shader.safeGetUniform("Radius"),
                    shader.safeGetUniform("ScreenSize"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}
