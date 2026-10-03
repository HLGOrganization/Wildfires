package first.wildfires.compat.sacombat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * GUI tint for the Wildfires leather backpacks.
 *
 * <p>Survivors Arsenal colours the backpack panel by comparing the stack against its own items one
 * by one ({@code BackpackInventoryPanelOverlay#getBackpackGuiTint}). A backpack that is not in that
 * chain falls through to {@code -1}, which means "do not tint", so the Wildfires packs would draw
 * the raw grey panel art instead of leather.
 *
 * <p>The value used here is the one the mod gives its own {@code leather_backpack}, so the Wildfires
 * packs match the leather look the pack already ships rather than an invented colour.
 *
 * <p>Keeping the decision in one place means the mixin stays a thin hook and the rule is testable
 * on its own.
 */
public final class LeatherBackpackGuiTint {

    /** The tint Survivors Arsenal applies to {@code sa_combat:leather_backpack} (0xFF7A5B3D). */
    public static final int LEATHER_TINT = 0xFF7A5B3D;

    /** The value Survivors Arsenal returns for "not one of mine", meaning no tint. */
    private static final int NO_TINT = -1;

    /** Item ids that should receive {@link #LEATHER_TINT}. */
    private static final Set<ResourceLocation> LEATHER_PACKS = Set.of(
            id("small_backpack_leather"),
            id("duffel_bag_leather"),
            id("hiking_backpack_leather"),
            id("military_backpack_leather")
    );

    private LeatherBackpackGuiTint() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    /**
     * Returns the tint for this stack, or {@link #NO_TINT} to leave Survivors Arsenal's own logic
     * to run. Returning {@code -1} rather than {@code 0} matters: {@code 0} is opaque black, while
     * {@code -1} is the mod's own "unhandled" marker.
     */
    public static int tintFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return NO_TINT;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && LEATHER_PACKS.contains(key) ? LEATHER_TINT : NO_TINT;
    }
}
