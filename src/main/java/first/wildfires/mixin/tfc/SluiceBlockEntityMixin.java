package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.blockentities.SluiceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SluiceBlockEntity.class,remap = false)
public class SluiceBlockEntityMixin {

    /**
     * Lets a hopper (or any other item automation) feed the sluice.
     * <p>
     * TFC builds its sided handler but never calls {@code on(...)} on it, and
     * {@code SidedHandler.Builder#getSidedHandler(Direction)} answers
     * {@code LazyOptional.empty()} for every direction that was not registered. A vanilla hopper
     * always asks with a direction, so the sluice exposed no item capability at all and a hopper
     * could not insert anything.
     * <p>
     * Every side except the bottom is registered. A hopper on top (or beside) the sluice asks with
     * the face it points at, while the only way for a hopper to <em>pull</em> from a block is the
     * hopper underneath it asking with {@code DOWN} - so leaving the bottom unregistered keeps
     * unwashed soil in the sluice instead of letting a hopper below drain it back out.
     * The inventory itself is not wrapped, so insertion is still filtered by
     * {@code SluiceBlockEntity#isItemValid} (sluiceable items only, one per slot).
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void wildfires$enableHopperInput(CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        final InventoryBlockEntityAccessor<ItemStackHandler> accessor =
                (InventoryBlockEntityAccessor<ItemStackHandler>) (Object) this;
        accessor.getSidedInventory().on(accessor.getInventory(), direction -> direction != Direction.DOWN);
    }

    @ModifyExpressionValue(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootParams$Builder;withOptionalParameter(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;Ljava/lang/Object;)Lnet/minecraft/world/level/storage/loot/LootParams$Builder;"
            ),
            remap = true
    )
    private static LootParams.Builder LootParams$Builder(LootParams.Builder original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return WildfiresUtil.modifyLootParams(original, level, Player.class, new AABB(pos).inflate(32));
    }

}
