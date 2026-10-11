package first.wildfires.mixin;

import first.wildfires.compat.net.NetworkGuardTargets;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * <h2>What this does</h2>
 * Drops the network guards of {@code first.wildfires.mixin.network} when the mod they patch is not
 * installed. {@code wildfires.mixins.json} names this class under {@code "plugin"}, so Mixin asks it
 * about every mixin of that config before the mixin is prepared.
 * <h2>Why the check has to happen here</h2>
 * Mixin resolves the declared targets while it prepares the config. A guard whose target class does
 * not exist is already survivable - each guard carries {@code @Pseudo} - but that leaves the skip
 * silent and at the mercy of the target name, so the mod is checked explicitly instead. This method
 * runs before target resolution, which is the only point where a guard can be removed without the
 * target ever being looked up.
 * <h2>Why the target class is never loaded</h2>
 * Loading a target from here would trip Mixin's own "target was loaded too early" check. The mod id
 * comes from {@link NetworkGuardTargets}, which holds nothing but strings.
 * <h2>Why only {@code fmlloader} is touched</h2>
 * A mixin plugin is loaded from the launch layer, where {@code net.minecraftforge.fml.ModList} (the
 * post-launch list) is not necessarily reachable, while {@code LoadingModList} is: several mods in
 * this pack use exactly this lookup in their own plugins. When that list is not available either,
 * the guard is applied as before and {@code @Pseudo} handles an absent target, so the failure mode
 * stays "guard applied to a missing mod, skipped silently" instead of "guard silently disabled for
 * an installed mod".
 */
public class WildfiresMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing to prepare: the guard table is a constant and the mod list is read per mixin.
    }

    @Override
    public String getRefMapperConfig() {
        return null; // wildfires.mixins.json names its own refmap; returning one here would override it
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String modId = NetworkGuardTargets.requiredMod(mixinClassName);
        if (modId == null || isModInstalled(modId)) {
            return true;
        }
        NetworkGuardTargets.recordDropped(mixinClassName, modId);
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No cross-config target handling.
    }

    @Override
    public List<String> getMixins() {
        return null; // no companion mixins
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No per-target work.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No per-target work.
    }

    /**
     * True when a mod with this id was discovered, or when that cannot be decided yet. The
     * {@code mods.toml} data is keyed by mod id, so a hit means the mod that owns the guard's target
     * package is going to be loaded.
     */
    private static boolean isModInstalled(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList == null) {
            return true;
        }
        return loadingModList.getModFileById(modId) != null;
    }
}
