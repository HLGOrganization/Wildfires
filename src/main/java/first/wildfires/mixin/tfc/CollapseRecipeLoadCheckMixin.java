package first.wildfires.mixin.tfc;

import first.wildfires.compat.tfc.CollapseLoadCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Requirement one of 2026-10-20: stop paying for the {@code isAreaLoaded(pos, 32)} guard on every trigger
 * attempt, without ever letting a collapse start against an unloaded area.
 *
 * <h2>The injection point</h2>
 *
 * <p>{@code CollapseRecipe.tryTriggerCollapse(Level, BlockPos)} is called from
 * {@code ForgeEventHandler.onBlockBroken} ({@code BlockEvent.BreakEvent}) and from
 * {@code ChiselItem}'s use path, and its first act - before the trigger chance is rolled, so it runs for
 * every break that reaches it - is:
 *
 * <pre>
 *   tryTriggerCollapse(Level level, BlockPos pos):
 *       6: invokevirtual  Level.isClientSide:()Z
 *       9: ifne 434                                   // client: return false
 *      12: aload_0 / aload_1 / bipush 32
 *      16: invokevirtual  Level.isAreaLoaded:(Lnet/minecraft/core/BlockPos;I)Z
 *      19: ifeq 434                                   // not loaded: return false
 *      22: ... trigger chance ...
 * </pre>
 *
 * <p>That single call is redirected. {@code LevelReader.isAreaLoaded(BlockPos, int)} is a default method
 * (it is still named {@code isAreaLoaded} in Forge's SRG jar, because it is a Forge-added default rather
 * than a vanilla member), so the call site keeps its name after obfuscation and the {@code @At} target needs
 * no refmap entry - {@code remap = false} is correct here and the build's refmap stays silent about it.
 *
 * <p>The predicate is <em>not</em> changed: the replacement answers with the real
 * {@code level.isAreaLoaded(pos, 32)} whenever the tick-local cache has no answer for that chunk yet, and
 * with the stored answer only inside the same level tick. See {@link CollapseLoadCache} for the cost it
 * removes, why it keys on the centre chunk rather than a larger cell, and how the cache ends.
 */
@Pseudo
@Mixin(targets = "net.dries007.tfc.common.recipes.CollapseRecipe", remap = false)
public abstract class CollapseRecipeLoadCheckMixin {

    @Redirect(
            method = "tryTriggerCollapse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isAreaLoaded(Lnet/minecraft/core/BlockPos;I)Z",
                    remap = false
            ),
            require = 1
    )
    private static boolean wildfires$areaLoadedFromTickCache(Level level, BlockPos pos, int range) {
        return CollapseLoadCache.isAreaLoaded(level, pos, range);
    }
}
