package first.wildfires;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public final class WildfiresTags {
    public static final TagKey<Block> MINEABLE_WITH_SHEARS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "mineable/shears")
    );

    public static final TagKey<Item> TFC_KNIVES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("tfc", "knives")
    );

    /** The dumbbells, which are given a slow heavy melee style and a charge throw. Open to any item. */
    public static final TagKey<Item> DUMBBELLS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "dumbbells")
    );

    private WildfiresTags() {
    }
}
