package first.wildfires.mixin.sophisticateditemactions;

import first.wildfires.compat.sophisticateditemactions.SophisticatedItemActionsConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides blacklisted block containers from Sophisticated Item Actions discovery. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticateditemactions.common.ItemActionHandlerRegistry", remap = false)
public abstract class ItemActionHandlerRegistryMixin {

    @Inject(
            method = "getBlockHandlerFor(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/p3pp3rf1y/sophisticateditemactions/common/IBlockItemActionHandler$Action;)"
                    + "Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void wildfires$ignoreBlacklistedContainer(
            Level level,
            BlockPos pos,
            BlockEntity blockEntity,
            @Coerce Object action,
            CallbackInfoReturnable<Optional<?>> cir
    ) {
        if (SophisticatedItemActionsConfig.isContainerBlacklisted(level, pos)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
