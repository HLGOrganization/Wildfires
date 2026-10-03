package first.wildfires.compat.sacombat;

import net.minecraft.resources.ResourceLocation;

/**
 * Armour stat overrides for Survivors Arsenal clothing.
 *
 * <h2>What this is for</h2>
 *
 * <p>{@code sa_combat:leon_s_kennedy_jacket} is built with {@code ArmorMaterials.DIAMOND} and the
 * chestplate type, which gives it a diamond chestplate's protection: <b>8 armour</b> and <b>2.0 armour
 * toughness</b>. In this modpack that is far too strong for a piece of clothing the player can wear
 * alongside real armour, so its protection is set to the value below and its toughness removed.
 *
 * <h2>Why these cannot be configured</h2>
 *
 * <p>{@code sa_combat.toml} exposes an item list, but its format is {@code item_id|area|slots|tint} -
 * extra storage slots and a GUI colour, not armour. Neither Survivors Arsenal nor Forge offers a way
 * to restate an item's protection, so the numbers are corrected in
 * {@code LeonJacketArmorMixin} instead.
 *
 * <p>The values live here rather than in the mixin so they can be read and adjusted as plain data,
 * without having to work through bytecode-level code.
 */
public final class ClothingArmorOverrides {

    /** The jacket whose protection is reduced. */
    public static final ResourceLocation LEON_JACKET =
            ResourceLocation.fromNamespaceAndPath("sa_combat", "leon_s_kennedy_jacket");

    /** Armour points, replacing the diamond chestplate's 8. */
    public static final double JACKET_ARMOR = 4.0D;

    /**
     * Armour toughness, replacing the diamond chestplate's 2.0.
     *
     * <p>Zero removes the toughness modifier outright rather than leaving one worth nothing: a
     * {@code +0} entry would still be listed in the item's attribute tooltip.
     */
    public static final double JACKET_TOUGHNESS = 0.0D;

    private ClothingArmorOverrides() {
    }
}
