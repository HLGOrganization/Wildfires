/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/mixin/ForgeEventHandlerMixin`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: only the raw-brightness and height expressions inside TFC 3.2.20's
 * spawn check are altered while the local moon is a visible blood moon; TFC's spawn tags, types,
 * light rules and its own cancellation logic stay untouched.
 */
package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.celestial.CelestialGameplay;
import net.dries007.tfc.ForgeEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Alters only TFC 3.2.20's light/height expressions, leaving all tags, types and cancellation logic intact. */
@Mixin(value = ForgeEventHandler.class, remap = false)
public abstract class ForgeEventHandlerCelestialMixin {

    @ModifyExpressionValue(
            method = "onLivingSpawnCheck",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I",
                    remap = true
            )
    )
    private static int wildfires$allowBloodMoonSurfaceLight(int original, MobSpawnEvent.FinalizeSpawn event) {
        return wildfires$allowsSurfaceException(event) ? 0 : original;
    }

    @ModifyExpressionValue(
            method = "onLivingSpawnCheck",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I",
                    remap = true
            )
    )
    private static int wildfires$allowBloodMoonSurfaceHeight(int original, MobSpawnEvent.FinalizeSpawn event) {
        return wildfires$allowsSurfaceException(event)
                ? Math.max(original, event.getEntity().blockPosition().getY() + 1) : original;
    }

    private static boolean wildfires$allowsSurfaceException(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (!CelestialConfig.serverSettings().bloodMoonSurfaceMonsters()
                || !CelestialGameplay.allowsSurfaceMonster(mob.getType())) {
            return false;
        }
        ServerLevelAccessor level = event.getLevel();
        return CelestialGameplay.visibleBloodMoon(level.getLevel(), mob.blockPosition()) > 0.0D;
    }
}
