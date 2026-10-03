package first.wildfires.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * What a sluice or a gold pan can get out of one deposit, ready to be drawn by JEI.
 *
 * <p>TFC keeps no recipe for either device: a {@code Sluiceable} or a {@code Pannable} only maps the
 * deposit to a loot table, and the drops are whatever that table rolls. This record is the resolved
 * form of that pair, so the sluicing and panning categories can share one recipe shape.
 */
public record DepositLootRecipe(ResourceLocation id, List<ItemStack> inputs, List<LootDrop> drops) {

    public DepositLootRecipe {
        // The best odds are listed first, so a page opens on the drops that are certain and ends on the
        // checkered cells that can fail instead of starting the grid on something that probably will not
        // come out. Order carries no other meaning - a table is a set of rolls - and the sort is stable,
        // so drops that share a chance keep the order their table declared them in.
        drops = drops.stream()
                .sorted(Comparator.comparingDouble(LootDrop::chance).reversed())
                .toList();
    }
}
