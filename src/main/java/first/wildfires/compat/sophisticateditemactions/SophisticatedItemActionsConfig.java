package first.wildfires.compat.sophisticateditemactions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Configuration for the optional Sophisticated Item Actions compatibility layer. */
public final class SophisticatedItemActionsConfig {

    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONTAINER_BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Compatibility settings for Sophisticated Item Actions.")
                .push("sophisticatedItemActions");
        CONTAINER_BLACKLIST = builder
                .comment("Block ids that Sophisticated Item Actions must ignore for highlight, deposit and restock actions.")
                .defineListAllowEmpty("containerBlacklist", List.of("tfc:thatch_bed"), value ->
                        value instanceof String id && ResourceLocation.tryParse(id) != null);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private SophisticatedItemActionsConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC,
                "wildfires-sophisticated-item-actions.toml");
    }

    public static boolean isContainerBlacklisted(Level level, BlockPos pos) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (blockId == null) {
            return false;
        }
        Set<ResourceLocation> blacklist = new HashSet<>();
        for (String id : CONTAINER_BLACKLIST.get()) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) {
                blacklist.add(parsed);
            }
        }
        return blacklist.contains(blockId);
    }
}
