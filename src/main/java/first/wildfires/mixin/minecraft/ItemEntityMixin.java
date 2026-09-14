package first.wildfires.mixin.minecraft;

import first.wildfires.api.customEvent.ItemEntityTickEvent;
import first.wildfires.compat.create.FanItemHeatSync;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void tickHead(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemEntityTickEvent.Pre event = new ItemEntityTickEvent.Pre(itemEntity);
        WildfiresUtil.post(event);
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void tickTail(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemEntityTickEvent.Post event = new ItemEntityTickEvent.Post(itemEntity);
        WildfiresUtil.post(event);
        FanItemHeatSync.tick(itemEntity);
    }

}
