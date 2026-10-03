package first.wildfires.jei;

import net.minecraft.world.item.ItemStack;

/**
 * One item a deposit can yield, with the chance of getting it from a single roll of the deposit's
 * loot table.
 *
 * <p>{@code exact} records whether that chance could actually be computed. TFC's deposits gate every
 * entry with {@code minecraft:random_chance}, which can be evaluated exactly; a table using any
 * condition this mod does not understand still has its items listed, but the chance is flagged so the
 * display can say "about" instead of presenting a number that was invented.
 */
public record LootDrop(ItemStack stack, float chance, boolean exact) {
}
