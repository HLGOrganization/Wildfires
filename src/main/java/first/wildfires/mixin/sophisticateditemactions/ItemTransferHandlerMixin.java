package first.wildfires.mixin.sophisticateditemactions;

import first.wildfires.compat.sophisticateditemactions.SophisticatedItemActionsConfig;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-checks block positions on the server before item transfers are executed. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticateditemactions.common.ItemTransferHandler", remap = false)
public abstract class ItemTransferHandlerMixin {

    @Inject(method = "handleDeposit", at = @At("HEAD"), remap = false)
    private static void wildfires$filterDepositContainers(
            Player player,
            int minSlot,
            int maxSlot,
            Map<ResourceLocation, List<BlockPos>> storagePositions,
            Map<ResourceLocation, List<Integer>> entities,
            boolean onlyMatching,
            CallbackInfo ci
    ) {
        filterBlacklistedContainers(player, storagePositions);
    }

    @Inject(method = "handleRestock", at = @At("HEAD"), remap = false)
    private static void wildfires$filterRestockContainers(
            Player player,
            Map<ResourceLocation, List<BlockPos>> storagePositions,
            Map<ResourceLocation, List<Integer>> entities,
            int minSlot,
            int maxSlot,
            net.minecraft.world.item.ItemStack filter,
            boolean fillEmpty,
            boolean refillSingle,
            CallbackInfo ci
    ) {
        filterBlacklistedContainers(player, storagePositions);
    }

    private static void filterBlacklistedContainers(Player player, Map<ResourceLocation, List<BlockPos>> storagePositions) {
        storagePositions.values().forEach(positions ->
                positions.removeIf(pos -> SophisticatedItemActionsConfig.isContainerBlacklisted(player.level(), pos)));
        storagePositions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
