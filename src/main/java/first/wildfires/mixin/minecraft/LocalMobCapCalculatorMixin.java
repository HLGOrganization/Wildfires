/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/mixin/LocalMobCapCalculatorMixin` and
 * `tfccaelum/mixin/accessor/ChunkMapAccessor`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: same redirect point on `MobCounts.canSpawn`, but the extended limit is
 * finite and derived from the synchronized celestial state plus the configured multiplier instead
 * of raw legacy intensity values.
 */
package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.celestial.CelestialGameplay;
import first.wildfires.celestial.CelestialGameplayRules;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Extends the existing local cap to a finite intensity-scaled limit without replacing the calculator. */
@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapCalculatorMixin {

    @Shadow
    @Final
    private Map<ServerPlayer, ?> playerMobCounts;

    @Shadow
    private List<ServerPlayer> getPlayersNear(ChunkPos position) {
        throw new AssertionError();
    }

    @ModifyExpressionValue(
            method = "canSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LocalMobCapCalculator$MobCounts;canSpawn(Lnet/minecraft/world/entity/MobCategory;)Z"
            )
    )
    private boolean wildfires$useFiniteBloodMoonCap(boolean original, MobCategory category, ChunkPos position) {
        if (original) {
            return true;
        }
        List<ServerPlayer> players = getPlayersNear(position);
        if (players.isEmpty()) {
            return false;
        }
        BlockPos sample = new BlockPos(position.getMiddleBlockX(), players.get(0).blockPosition().getY(),
                position.getMiddleBlockZ());
        double intensity = CelestialGameplay.visibleBloodMoon(players.get(0).serverLevel(), sample);
        if (intensity <= 0.0D) {
            return false;
        }
        int limit = CelestialGameplayRules.localMobCapLimit(category.getMaxInstancesPerChunk(), intensity,
                CelestialConfig.serverSettings().bloodMoonSpawnMultiplier());
        for (ServerPlayer player : players) {
            Object counts = playerMobCounts.get(player);
            if (counts == null || ((LocalMobCountsAccessor) counts).wildfires$getCounts()
                    .getOrDefault(category, 0) < limit) {
                return true;
            }
        }
        return false;
    }
}
