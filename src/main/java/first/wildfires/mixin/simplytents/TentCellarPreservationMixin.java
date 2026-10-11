package first.wildfires.mixin.simplytents;

import first.wildfires.compat.simplytents.TentCellarPreservation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <h2>What this fixes</h2>
 * When a tent is placed, its block puts the blocks it packed back into the world and hands each block
 * entity the saved data it carried, with one {@code BlockEntity#load(CompoundTag)} per restored block
 * entity. A container holds its items in that data, so a container that stood in a TFC Modern Life cellar
 * arrives with the temporary preservation trait Modern Life had written onto its food, and without the
 * cleanup Modern Life runs when an inventory is loaded.
 *
 * <p>This runs that cleanup on the container the tent has just put down, by way of Modern Life's own public
 * strip ({@link TentCellarPreservation}). The container then holds exactly what it should for where it now
 * stands: Modern Life applies the trait of the cellar it was unpacked in, if any, on its next sync, and
 * otherwise the food keeps only the preservation it earned outside a cellar.
 *
 * <h2>Why the restore rather than the pickup</h2>
 * Packing a tent saves each block entity and removes the block in the same breath, and it does that at two
 * separate call sites - the interior blocks and the canvas walls - so a redirection there would need both
 * pinned by ordinal, which is the kind of thing a mod update breaks. Restoring has one such call for every
 * block entity the tent carried, and it also cleans up tents that were packed before this guard existed.
 *
 * <h2>Why it is @Pseudo with the target named as a string</h2>
 * Simply Tents is not a compile dependency of this mod and does not have to be installed, so the target is
 * named as a string, no type from it appears in the handler signature, and the guard is skipped when the mod
 * is absent. When it is present, {@code require = 1} makes a renamed method fail loudly at start-up rather
 * than let the doubled shelf life come back quietly.
 */
// @Pseudo: the target mod is optional, so its class is absent from the compile classpath (the
// annotation processor would otherwise reject the target) and the mixin is skipped when the mod is
// not installed. Only vanilla types appear in the handler below.
@Pseudo
@Mixin(targets = "com.sappyeddie.simplytents.tent.block.MyGeoBlock", remap = false)
public abstract class TentCellarPreservationMixin {

    /**
     * Loads the saved data as the tent would have, then takes the temporary cellar preservation off what
     * came with it. The target call is named by its SRG name because the mod is compiled against it.
     */
    @Redirect(
            method = "m_6402_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                            + "m_142466_(Lnet/minecraft/nbt/CompoundTag;)V"
            ),
            remap = false,
            require = 1
    )
    private void wildfires$stripTemporaryCellarPreservation(BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.load(tag);
        TentCellarPreservation.sanitize(blockEntity);
    }
}
