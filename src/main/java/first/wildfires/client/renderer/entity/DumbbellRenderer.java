package first.wildfires.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import first.wildfires.entity.DumbbellProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the flying dumbbell as a flat item sprite that tumbles in place.
 *
 * <p>The sprite stays billboarded towards the camera and only rolls around the view axis, so the
 * dumbbell is never seen edge-on and therefore never flickers away mid spin.</p>
 */
public class DumbbellRenderer extends EntityRenderer<DumbbellProjectile> {

    /** Same guard as the vanilla thrown item renderer: never block the thrower's own view. */
    private static final double MIN_CAMERA_DISTANCE_SQUARED = 12.25D;

    private final ItemRenderer itemRenderer;

    public DumbbellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DumbbellProjectile entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.tickCount < 2 && this.entityRenderDispatcher.camera.getEntity()
                .distanceToSqr(entity) < MIN_CAMERA_DISTANCE_SQUARED) {
            return;
        }

        ItemStack stack = entity.getItem();
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * entity.getSpinRate()));
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DumbbellProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

}
