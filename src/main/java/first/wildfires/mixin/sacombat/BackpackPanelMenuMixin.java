package first.wildfires.mixin.sacombat;

import com.mojang.logging.LogUtils;
import com.ogaba.sa_survival.menu.BackpackMenu;
import com.ogaba.sa_survival.menu.BackpackSlot;
import first.wildfires.compat.sacombat.BackpackPanelLayout;
import first.wildfires.mixin.minecraft.SlotPositionAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebuilds a backpack menu's geometry as a vertical arrangement: backpack panel on top, player
 * inventory underneath, six slots per row.
 *
 * <h2>Why the geometry has to change</h2>
 *
 * <p>{@code BackpackMenu} supports two layouts. The one every backpack opens with is the generic
 * layout, which hardcodes nine columns and leaves the screen to blit vanilla's {@code generic_54}
 * sheet, whose grid is baked into the texture. A 15-slot satchel therefore draws
 * {@code ceil(15 / 9) * 9 = 18} cells with only 15 slots behind them - the reported bug.
 *
 * <p>This mixin puts backpacks on the other path, the one {@code BackpackScreen} already implements,
 * and then reshapes that path from side-by-side into stacked.
 *
 * <h2>Two hooks, because the layout flag is read while the constructor runs</h2>
 *
 * <p>The flag has to be cleared <em>before</em> the constructor uses it, not after. It drives three
 * decisions: the column count, whether the panel or a chest grid is drawn, and - the one that would
 * otherwise be missed - whether the player's inventory is added as a bare 36-slot block or as the
 * vanilla arrangement with armour and offhand. Correcting the flag at {@code RETURN} would leave the
 * player slots built for the wrong layout and they would not line up with the inventory sheet. So:
 *
 * <ol>
 *   <li>{@code @ModifyVariable} at {@code HEAD} clears the flag, so the original code builds the
 *       vanilla-style player inventory and panel-shaped geometry.</li>
 *   <li>{@code @Inject} at {@code RETURN} overwrites that geometry with the stacked arrangement and
 *       moves the slots onto it.</li>
 * </ol>
 *
 * <p>{@code @Inject} cannot be used at an {@code INVOKE} point inside a constructor - Mixin rejects it
 * with "Found @Inject targetting a constructor" - which is why the first hook is a variable modifier.
 * At a constructor's {@code HEAD} the handler must be static, and the target's parameters follow the
 * modified variable in the signature.
 *
 * <h2>Moving slots instead of rebuilding them</h2>
 *
 * <p>No slot is created or destroyed. Slots exist on both sides and are matched by index, so adding or
 * removing them here would desynchronise the menu from the server. The backpack slots are placed on the
 * six-column grid, and every other slot is shifted by however much the player-inventory origin moved -
 * they were created relative to that origin, so a uniform shift preserves their arrangement.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.menu.BackpackMenu", remap = false)
public abstract class BackpackPanelMenuMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean wildfires$logged;

    /**
     * Clears the generic-layout flag before the constructor reads it.
     *
     * <p>{@code genericLayout} is parameter index 5. Clearing it makes the original code take the panel
     * branch, which is what gives the player inventory its armour and offhand slots and what stops the
     * screen from blitting a chest grid.
     */
    @ModifyVariable(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
                    + "Lnet/minecraft/world/item/ItemStack;IZI)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5,
            remap = false,
            require = 0
    )
    private static boolean wildfires$usePanelLayout(boolean genericLayout, int syncId,
                                                    Inventory inventory, ItemStack backpackStack) {
        return false;
    }

    /**
     * Replaces the panel path's side-by-side geometry with the stacked arrangement.
     *
     * <p>Runs after the constructor, so the player-inventory origin is already known; the slots built
     * against it can then be shifted by the difference.
     */
    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
                    + "Lnet/minecraft/world/item/ItemStack;IZI)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void wildfires$stackedGeometry(int syncId, Inventory inventory, ItemStack stack,
                                           int slotCount, boolean genericLayout,
                                           int ownerInventoryIndex, CallbackInfo ci) {
        BackpackMenu menu = (BackpackMenu) (Object) this;
        BackpackMenuGeometryAccessor accessor = (BackpackMenuGeometryAccessor) this;

        int slots = accessor.wildfires$getBackpackSlots();
        if (slots <= 0) {
            return;
        }

        // The flag hook above should have cleared this. If it is still set then that hook did not
        // apply (it is written with require = 0 so a reshape degrades instead of crashing), and
        // reshaping the geometry now would move slots on top of a chest grid and corrupt the view.
        // Leaving everything untouched keeps the previous behaviour instead.
        if (menu.isGenericLayout()) {
            return;
        }

        // Where the player inventory currently sits, so the shift below can be derived.
        int oldVanillaX = menu.getVanillaInventoryX();
        int oldVanillaY = menu.getVanillaInventoryY();

        int rows = BackpackPanelLayout.rowsFor(slots);
        int panelX = BackpackPanelLayout.panelX();
        int panelY = BackpackPanelLayout.PANEL_Y;
        int newVanillaX = BackpackPanelLayout.vanillaInventoryX();
        int newVanillaY = BackpackPanelLayout.vanillaInventoryY(slots);

        accessor.wildfires$setBackpackColumns(BackpackPanelLayout.COLUMNS);
        accessor.wildfires$setBackpackRows(rows);

        accessor.wildfires$setBackpackPanelWidth(BackpackPanelLayout.PANEL_WIDTH);
        accessor.wildfires$setBackpackPanelHeight(BackpackPanelLayout.panelHeightFor(slots));
        accessor.wildfires$setBackpackPanelX(panelX);
        accessor.wildfires$setBackpackPanelY(panelY);

        accessor.wildfires$setVanillaInventoryX(newVanillaX);
        accessor.wildfires$setVanillaInventoryY(newVanillaY);

        accessor.wildfires$setImageWidth(BackpackPanelLayout.imageWidth());
        accessor.wildfires$setImageHeight(BackpackPanelLayout.imageHeightFor(slots));

        int shiftX = newVanillaX - oldVanillaX;
        int shiftY = newVanillaY - oldVanillaY;

        for (Slot slot : menu.slots) {
            SlotPositionAccessor position = (SlotPositionAccessor) slot;
            if (slot instanceof BackpackSlot) {
                int index = slot.getContainerSlot();
                position.wildfires$setX(BackpackPanelLayout.slotX(panelX, index));
                position.wildfires$setY(BackpackPanelLayout.slotY(panelY, index));
                continue;
            }

            // Slots are stored relative to the player-inventory origin, so a uniform shift keeps the
            // arrangement intact while following the origin to its new place.
            int offsetY = slot.y - oldVanillaY;
            if (BackpackPanelLayout.isHiddenSlot(offsetY)) {
                // Armour and offhand live above the drawn sheet. They have to stay in the menu - the
                // server sent them and both sides match slots by index - but they must not land on top
                // of the backpack panel, so they are parked out of reach. The ordinary inventory screen
                // still reaches them.
                position.wildfires$setX(BackpackPanelLayout.HIDDEN_SLOT_POS);
                position.wildfires$setY(BackpackPanelLayout.HIDDEN_SLOT_POS);
            } else {
                position.wildfires$setX(slot.x + shiftX);
                position.wildfires$setY(slot.y + shiftY);
            }
        }

        if (!wildfires$logged) {
            wildfires$logged = true;
            LOGGER.info("[Wildfires] Backpack panel: {} slots -> {} columns, {} rows",
                    slots, BackpackPanelLayout.COLUMNS, rows);
        }
    }
}
