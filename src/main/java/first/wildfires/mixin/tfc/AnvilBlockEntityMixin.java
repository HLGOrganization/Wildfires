package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;
import first.wildfires.api.customEvent.AnvilWeldEvent;
import first.wildfires.register.ItemRegister;
import first.wildfires.smithing.ForgeMemory;
import first.wildfires.smithing.PerfectForgeService;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.capabilities.forge.ForgingBonus;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.common.recipes.WeldingRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

@Mixin(value = AnvilBlockEntity.class, remap = false)
public class AnvilBlockEntityMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Welds a lone book into the forging manual.
     *
     * <p>The manual is made by putting a book on an anvil and welding it with nothing - "welding with
     * air". TFC cannot express that as a recipe: every welding recipe needs two ingredients, and an
     * {@code Ingredient} cannot match an empty slot. Its weld also insists both inputs be hot enough to
     * weld, which a book never is - it has no heat capability at all - and it wants flux and a high
     * enough anvil tier on top. So the case is recognised here instead, before any of that is consulted.
     *
     * <p>Nothing else reaches this branch: a weld with something in the second slot cannot match a
     * recipe that takes one item, and every TFC welding recipe needs two. Normal welding is untouched.
     *
     * <p>Both sides run this, exactly as TFC's own weld does, so the client and server agree on the
     * result rather than the client briefly showing something else.
     */
    @Inject(method = "weld", at = @At("HEAD"), cancellable = true, remap = false)
    private void wildfires$weldBookIntoManual(Player player, CallbackInfoReturnable<InteractionResult> cir) {
        AnvilBlockEntity anvil = (AnvilBlockEntity) (Object) this;
        if (!(anvil instanceof InventoryBlockEntityAccessor<?> accessor)
                || !(accessor.getInventory() instanceof AnvilBlockEntity.AnvilInventory inventory)) {
            return;
        }

        if (!inventory.getRight().isEmpty()) {
            return;
        }
        ItemStack book = inventory.getLeft();
        if (!book.is(Items.BOOK)) {
            return;
        }

        ItemStack manual = new ItemStack(ItemRegister.ForgingManual.get());
        book.shrink(1);
        if (book.isEmpty()) {
            inventory.setStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN, manual);
        } else {
            // More books are waiting, so the manual goes to the overflow list, as any weld result would.
            ((AnvilInventoryExcessAccessor) inventory).wildfires$getExcess().add(manual);
        }
        anvil.markForSync();
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * Notes one blow of a forging, so the memory forge can strike the same sequence again.
     *
     * <p>TFC's own record of a forging is the last three strikes, which is all the anvil needs and not
     * enough to repeat anyone's work, so every accepted blow is collected at the point TFC takes it. The
     * strike number is what the blow is filed under, which is what makes the collection survive the
     * forging being restarted.
     *
     * <p>Injected after {@code addStep}, so the blow has already been counted and the position read here
     * is the one it went in at. Nothing needs to be excluded: the anvil only reaches this call for a blow
     * it accepted, after the hammer, the tier and the temperature have all been answered.
     */
    @Inject(
            method = "work",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/capabilities/forge/Forging;"
                            + "addStep(Lnet/dries007/tfc/common/capabilities/forge/ForgeStep;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false,
            require = 1
    )
    private void wildfires$rememberStep(ServerPlayer player, ForgeStep step,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        ForgeMemory.record(player, (AnvilBlockEntity) (Object) this, step);
    }

    /**
     * Credits a completed forge of a recipe that carries a forging quality.
     *
     * <p>TFC works out how well the recipe was forged at the exact moment it stamps the quality onto the
     * result, which is the one place that judgement is made. Hooking that call keeps this in step with
     * TFC's own scale - including the ratio it computes from the steps taken - instead of re-deriving it
     * here and risking the two disagreeing, and it sees the quality itself rather than a yes/no on whether
     * it was perfect. A sloppy completion of a graded recipe earns its one point, a flawless one the full
     * five; hooking the advancement TFC fires only for the flawless ones would ignore the rest of the
     * scale entirely.
     *
     * <p>The injector runs before TFC writes the result into the input slot, so the anvil still holds the
     * stack whose {@code Forging} names the recipe that was just completed.
     *
     * <p>A recipe that carries no forging quality never gets this far - TFC skips the whole bonus block,
     * stamp included. Those are covered by {@link #wildfires$recordQualitylessForge} below.
     */
    @Inject(
            method = "work",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/capabilities/forge/ForgingBonus;"
                            + "set(Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/dries007/tfc/common/capabilities/forge/ForgingBonus;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false,
            require = 1
    )
    private void wildfires$recordGradedForge(ServerPlayer player, ForgeStep step,
                                             CallbackInfoReturnable<InteractionResult> cir,
                                             @Local(name = "bonus") ForgingBonus bonus) {
        AnvilBlockEntity anvil = (AnvilBlockEntity) (Object) this;
        PerfectForgeService.recordForge(player, anvil, bonus);
        ForgeMemory.commit(player, anvil);
    }

    /**
     * Credits a completed forge of a recipe that carries no forging quality at all.
     *
     * <p>{@code apply_forging_bonus} defaults to <b>false</b> in TFC's anvil recipe format, and 143 of the
     * 278 shipped recipes leave the field out - every sheet, rod, bar, chain, tuyere and unfinished armour
     * piece among them, which is to say most of what anyone actually forges. On those TFC never computes a
     * ratio and never stamps a quality, so the injector above can never see them and their one-click forge
     * could never unlock no matter how often the recipe was forged.
     *
     * <p>There is nothing to do well on such a recipe - TFC applies no quality to the result, so a sloppy
     * completion and a flawless one yield the identical item. Every completed forge therefore counts, at
     * the flat one point an ungraded result is worth.
     *
     * <p>Injected before {@code assemble}: that is the first thing TFC does once it has confirmed the
     * recipe is complete, and the incomplete path jumps past it, so it is reached exactly once per
     * completed forge. The test below makes the two injectors mutually exclusive, so a recipe that does
     * apply a bonus is never counted twice.
     */
    @Inject(
            method = "work",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/recipes/AnvilRecipe;"
                            + "assemble(Lnet/dries007/tfc/common/recipes/AnvilRecipe$Inventory;"
                            + "Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"
            ),
            remap = false,
            require = 1
    )
    private void wildfires$recordQualitylessForge(ServerPlayer player, ForgeStep step,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        AnvilBlockEntity anvil = (AnvilBlockEntity) (Object) this;
        Level level = anvil.getLevel();
        Forging forging = anvil.getMainInputForging();
        AnvilRecipe recipe = level == null || forging == null ? null : forging.getRecipe(level);
        if (recipe != null && !recipe.shouldApplyForgingBonus()) {
            PerfectForgeService.recordForge(player, anvil, null);
            ForgeMemory.commit(player, anvil);
        }
    }

    @Inject(
            method = "weld",
            at = @At("HEAD"),
            cancellable = true
    )
    private void weld(Player player, CallbackInfoReturnable<InteractionResult> cir) {
        AnvilBlockEntity anvilBlockEntity = (AnvilBlockEntity) (Object) this;
        InventoryBlockEntityAccessor<?> accessor = (InventoryBlockEntityAccessor<?>) anvilBlockEntity;
        Level level = anvilBlockEntity.getLevel();
        if (level != null && accessor.getInventory() instanceof AnvilBlockEntity.AnvilInventory inventory) {
            ItemStack tool = inventory.getLeft();
            ItemStack material = inventory.getRight();
            WeldingRecipe recipe = level.getRecipeManager().getRecipeFor(TFCRecipeTypes.WELDING.get(), inventory, level).orElse(null);
            LOGGER.info("[WeldRepairDebug] weld invoked: physicalLeft={}, physicalRight={}, existingRecipe={}",
                    tool, material, recipe != null);
            if (recipe == null && !tool.isEmpty() && !material.isEmpty()) {
                IHeat toolHeat = HeatCapability.get(tool);
                IHeat materialHeat = HeatCapability.get(material);
                ItemStack flux = inventory.getStackInSlot(3);
                LOGGER.info("[WeldRepairDebug] gates: toolHeat={}, materialHeat={}, toolCanWeld={}, materialCanWeld={}, flux={}",
                        toolHeat != null,
                        materialHeat != null,
                        toolHeat != null && toolHeat.canWeld(),
                        materialHeat != null && materialHeat.canWeld(),
                        flux);
                if (toolHeat != null && materialHeat != null && toolHeat.canWeld() && materialHeat.canWeld() && !flux.isEmpty()) {
                    // The legacy KubeJS contract exposes repair material as left and tool as right.
                    AnvilWeldEvent event = new AnvilWeldEvent(material, tool);
                    WildfiresUtil.post(event);
                    LOGGER.info("[WeldRepairDebug] event posted: result={}, eventLeft={}, eventRight={}, rightDamage={}/{}",
                            event.getResult(), event.getLeft(), event.getRight(),
                            event.getRight().getDamageValue(), event.getRight().getMaxDamage());
                    if (event.getResult() == Event.Result.ALLOW) {
                        inventory.setStackInSlot(1, event.getLeft());
                        inventory.setStackInSlot(0, event.getRight());
                        flux.shrink(1);
                        anvilBlockEntity.markForSync();
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        LOGGER.info("[WeldRepairDebug] repair applied and inventory synced");
                    }
                }
            }
        }
    }

}
