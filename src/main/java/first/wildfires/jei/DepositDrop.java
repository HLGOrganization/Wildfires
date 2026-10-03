package first.wildfires.jei;

import net.minecraft.resources.ResourceLocation;

/**
 * One item a deposit's loot table can produce, with the chance of getting it from a single roll.
 *
 * <p>Deliberately keyed by item id rather than by {@code ItemStack}: the table maths then needs neither
 * a booted game nor the item registry, which is what lets {@link DepositLootSelfTest} check the
 * probabilities directly. {@link DepositRecipes} turns these into displayable stacks.
 *
 * <p>{@code exact} records whether the chance could actually be computed. TFC's deposits gate every
 * entry with {@code minecraft:random_chance}, which is exact; a table using a condition this mod does
 * not understand still has its items listed, but the chance is flagged so the display can say "about"
 * instead of presenting a number that was invented.
 */
public record DepositDrop(ResourceLocation item, int count, float chance, boolean exact) {
}
