package first.wildfires.mixin.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import first.wildfires.client.spawnegg.SpawnEggPreviewConfig;
import first.wildfires.client.spawnegg.SpawnEggPreviewRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * <h2>What this does</h2>
 * Draws the mob a spawn egg belongs to in place of the flat egg icon. The egg's own model is skipped
 * entirely: the injection runs at the head of {@code ItemRenderer#render}, and once a mob has been
 * drawn the vanilla call is cancelled, so the two never overlap.
 * <p>This is a port of the standalone {@code entityspawnegg} 1.0.0 mod by mantodea, which shipped
 * the same behaviour as its own mixin config.
 *
 * <h2>What changed against the original</h2>
 * <ul>
 *   <li>The cache is a {@link WeakHashMap} keyed by {@link EntityType} instead of a {@link java.util.HashMap}.
 *       The original held a strong reference to every mob it had ever rendered, for the whole session.
 *       The keys are the registry's own {@code EntityType} singletons, so they are never collected
 *       while the game runs; the values were what leaked, and a weak map releases a mob as soon as
 *       nothing else wants it. A cached mob is only a pose template - it is reset on every draw - so
 *       rebuilding one costs a constructor and nothing is lost by dropping it.</li>
 *   <li>The {@code mythicmounts} special case is gone. The original suppressed the preview for that
 *       one mod while a screen was open, which is dead code in this pack: the mod is not installed.</li>
 *   <li>The pose reset covers the fields the original missed - in particular {@code walkAnimation}
 *       speed and position, which otherwise keep whatever the last tick left behind and make a
 *       carried egg's mob slide on the spot.</li>
 * </ul>
 *
 * <h2>Why the entity is drawn rather than an item model</h2>
 * A spawn egg's item model is a flat sprite. Drawing the mob needs a live {@link Mob} instance to
 * hand to the {@link EntityRenderDispatcher}; one is built from the egg's {@link EntityType} and
 * cached, never added to a level, and reset before every draw so its animation state is fixed.
 *
 * <h2>Why the render is cancelled</h2>
 * Returning from the injection without cancelling would let vanilla draw the flat egg on top of the
 * mob. The cancel is what replaces the icon; every early return above it leaves the vanilla path
 * untouched, so an egg whose entity cannot be created still renders normally.
 */
@Mixin(ItemRenderer.class)
public class SpawnEggItemRendererMixin {

    /**
     * Pose templates, one per entity type. Weak so a client that has rendered a hundred egg types
     * does not pin a hundred mobs for the session; see the class note.
     */
    @Unique
    private final Map<EntityType<?>, Mob> wildfires$previewEntities = new WeakHashMap<>();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wildfires$renderSpawnEggAsMob(ItemStack stack, ItemDisplayContext displayContext,
                                               boolean leftHand, PoseStack poseStack,
                                               MultiBufferSource buffer, int combinedLight,
                                               int combinedOverlay, BakedModel model,
                                               CallbackInfo callback) {
        if (!(stack.getItem() instanceof SpawnEggItem spawnEgg)) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        EntityType<?> entityType = spawnEgg.getType(stack.getTag());
        Mob mob = wildfires$previewEntities.get(entityType);
        if (mob == null) {
            // create(...) can return null for an entity type whose factory refuses this level, and a
            // throw here would break the whole inventory render, so a failed build just falls through
            // to the vanilla icon.
            if (!(entityType.create(level) instanceof Mob created)) {
                return;
            }
            mob = created;
            wildfires$previewEntities.put(entityType, mob);
        }

        wildfires$resetEntityState(mob);

        poseStack.pushPose();

        boolean inGui = displayContext == ItemDisplayContext.GUI;
        float fitted = SpawnEggPreviewRules.fitScale(mob.getBbWidth(), mob.getBbHeight(),
                SpawnEggPreviewConfig.minZoomRate(), SpawnEggPreviewConfig.maxZoomRate())
                * SpawnEggPreviewConfig.totalZoomRate();

        if (SpawnEggPreviewRules.usesIconFrame(inGui)) {
            // The icon sits on the inventory's own offset grid, so it is tilted and lifted to look
            // upright in the slot. Held and dropped contexts already have a sensible frame and only
            // take the scale.
            poseStack.mulPose(Axis.YP.rotationDegrees(SpawnEggPreviewConfig.rotationAngle()));
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }

        float scale = SpawnEggPreviewRules.contextScale(fitted, inGui,
                SpawnEggPreviewConfig.iconZoomRate(), SpawnEggPreviewConfig.itemZoomRate());
        poseStack.scale(scale, scale, scale);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        // The dispatcher is asked for a full-bright draw with a fixed partial tick of 1, so the mob
        // does not flicker with the world's day cycle or the frame's interpolation.
        dispatcher.render(mob, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poseStack, buffer, combinedLight);

        poseStack.popPose();
        callback.cancel();
    }

    /**
     * Clears every pose field the mob renderer reads, not just the position.
     * <p>The original reset rotation, motion and the walk-animation position but left the walk
     * <em>speed</em> fields alone. A mob that was mid-stride when it was cached keeps that speed, and
     * the renderer keeps advancing the animation from it, so the preview slides on the spot. The
     * counters are cleared too: they drive the idle limb sway, which would otherwise drift for as
     * long as the egg is held.
     */
    @Unique
    private static void wildfires$resetEntityState(Mob mob) {
        // Position and rotation.
        mob.setPos(0.0D, 0.0D, 0.0D);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setYRot(0.0F);
        mob.setXRot(0.0F);
        mob.yRotO = 0.0F;
        mob.xRotO = 0.0F;
        mob.yBodyRot = 0.0F;
        mob.yBodyRotO = 0.0F;
        mob.yHeadRot = 0.0F;
        mob.yHeadRotO = 0.0F;

        // Walk animation: both the position and the speed that advances it.
        mob.walkAnimation.setSpeed(0.0F);
        mob.walkAnimation.update(0.0F, 0.0F);
        mob.walkAnimation.position(0.0F);

        // Age and the hurt/death counters that add a shake to the render.
        mob.tickCount = 0;
        mob.hurtTime = 0;
        mob.deathTime = 0;
        mob.hurtDuration = 0;
        mob.attackAnim = 0.0F;
        mob.oAttackAnim = 0.0F;
        mob.swingTime = 0;

        // A newborn mount keeps its riding offset; with no rider the renderer would still apply it.
        mob.setYHeadRot(0.0F);
        mob.setSprinting(false);
        mob.setShiftKeyDown(false);
        mob.setSwimming(false);
    }

    /**
     * The single scale that makes any mob fit the slot. The arithmetic lives in
     * {@link SpawnEggPreviewRules#fitScale} so a plain-JVM test can pin its boundaries; this method is
     * only the bridge from a live mob and the two config clamps to that rule.
     */
    @Unique
    private static float wildfires$fitScale(Mob mob) {
        return SpawnEggPreviewRules.fitScale(mob.getBbWidth(), mob.getBbHeight(),
                SpawnEggPreviewConfig.minZoomRate(), SpawnEggPreviewConfig.maxZoomRate());
    }
}
