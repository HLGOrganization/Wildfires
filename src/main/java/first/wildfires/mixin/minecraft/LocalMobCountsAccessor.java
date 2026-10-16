/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/mixin/accessor/MobCountsAccessor`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: same target and named-getter accessor pattern, renamed into the
 * Wildfires accessor namespace.
 */
package first.wildfires.mixin.minecraft;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimal accessor used to enforce a finite, intensity-scaled local mob cap. */
@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public interface LocalMobCountsAccessor {

    @Accessor("counts")
    Object2IntMap<MobCategory> wildfires$getCounts();
}
