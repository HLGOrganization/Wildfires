package first.wildfires.item;

import com.ogaba.sa_survival.item.backpack.BackpackItem;
import net.minecraft.world.item.Item;

/**
 * A colourless variant of a Survivors Arsenal backpack.
 *
 * <p>The modpack ships every backpack only in dyed colours, so a plain one is added for recipes
 * that should not require a dye. Everything - the slot count, the menu, the stored contents and
 * the automation behaviour - comes from {@link BackpackItem}, which takes the slot count as a
 * constructor argument; this class only fixes that argument per family.
 *
 * <p>The textures are deliberately borrowed from {@code sa_combat} so the item renders before any
 * replacement art exists. An item model may reference a texture from another namespace, so the
 * model files point at the existing {@code sa_combat:item/...} sprites.
 */
public class PlainBackpackItem extends BackpackItem {

    public PlainBackpackItem(Item.Properties properties, int slots) {
        super(properties, slots);
    }
}