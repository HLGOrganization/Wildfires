package first.wildfires.compat.sacombat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Maps the plain Wildfires backpack variants onto the Survivors Arsenal family they copy.
 *
 * <p>Survivors Arsenal decides how a backpack is drawn while worn by comparing the stack against
 * the items it knows, through hard-coded {@code isSatchel} and sibling checks in
 * {@code BackpackCurioRenderer}. A new item is therefore invisible on a player's back even though
 * its inventory, menu and Curios slot all work.
 *
 * <p>This class answers the same questions for the plain variants by resource location only, so no
 * Survivors Arsenal type has to be referenced and the mapping stays valid even if the mod is
 * absent. The value is the prototype a plain pack borrows its model and family from.
 */
public final class PlainBackpackFamilies {

    /** Wildfires leather variant -> the sa_combat prototype whose family it belongs to. */
    private static final Map<ResourceLocation, ResourceLocation> FAMILY_BY_PLAIN = Map.of(
            id("wildfires", "small_backpack_leather"), id("sa_combat", "small_backpack_black"),
            id("wildfires", "duffel_bag_leather"), id("sa_combat", "duffel_bag_black"),
            id("wildfires", "hiking_backpack_leather"), id("sa_combat", "hiking_backpack_black"),
            id("wildfires", "military_backpack_leather"), id("sa_combat", "military_backpack_green")
    );

    private PlainBackpackFamilies() {
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** True when this stack is one of the plain variants. */
    public static boolean isPlainVariant(ItemStack stack) {
        return familyOf(stack) != null;
    }

    /**
     * True when the stack is a plain variant that belongs to the family of the given prototype.
     *
     * <p>Callers pass the prototype as a resource location, so the check never touches a Survivors
     * Arsenal class.
     */
    public static boolean belongsToFamily(ItemStack stack, ResourceLocation prototype) {
        ResourceLocation family = familyOf(stack);
        return family != null && family.equals(prototype);
    }

    /**
     * True when the stack is a plain variant whose prototype path starts with the given prefix.
     *
     * <p>Survivors Arsenal groups its backpacks by a name prefix, so a single prefix check covers
     * every colour of a family without listing them.
     */
    public static boolean belongsToFamilyPrefix(ItemStack stack, String prefix) {
        ResourceLocation family = familyOf(stack);
        return family != null
                && "sa_combat".equals(family.getNamespace())
                && family.getPath().startsWith(prefix);
    }
    /** The sa_combat prototype this plain variant copies, or {@code null} when it is not one. */
    public static ResourceLocation familyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : FAMILY_BY_PLAIN.get(key);
    }
}