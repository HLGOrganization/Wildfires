package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.compat.tfc.TorchIgnitionRules;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blockentities.LogPileBlockEntity;
import net.dries007.tfc.common.blockentities.PitKilnBlockEntity;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.BurningLogPileBlock;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.events.StartFireEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns a lit torch into a tool: a sneak click lights one of the handful of devices a torch should light, and a
 * swing at a mob sets it alight. The list of devices, and what lighting each of them costs, lives in
 * {@link TorchIgnitionRules}.
 *
 * <h2>Where the click is caught</h2>
 *
 * <p>Nothing is hooked here. TFC's {@code TorchItem.useOn} already posts a {@link StartFireEvent} at
 * {@code FireStrength.WEAK} before it falls back to placing the torch, and answers nothing itself, so a weak
 * request that is not cancelled is a request that has been declined. Cancelling it is what makes TFC's own item
 * return success and stop short of the placement; not cancelling it leaves the vanilla behaviour - the torch is
 * put down - exactly as it was.
 *
 * <h2>Why the click is cancelled on the client too</h2>
 *
 * <p>The client predicts the click and posts the same event, which is what places a block in the player's own
 * screen without waiting for the server. That prediction is the reason the answer here cannot depend on whether
 * the device had fuel: the client has no business running a device's {@code light()} - it would spend a copy of a
 * fuel stack that only the server owns - so a decision made per-device could only be made on the server, and a
 * client that guessed wrong would be left with a torch standing in the world that no later packet would ever
 * remove, because a cancelled click never sends a block update for the place it did not change.
 *
 * <p>So the answer is decided by the one thing both sides can see - the block that was clicked - and the event is
 * cancelled on both sides for every device on the list. What differs is only what happens next: the server lights
 * the device and spends the torch, the client does nothing at all. A click that is answered on only one side is
 * harmless here, because cancelling leaves no trace on either side to disagree about.
 *
 * <h2>What a failed lighting costs</h2>
 *
 * <p>A device with no fuel, no charge or no logs is left exactly as it was and costs nothing: the torch is spent
 * only where something actually caught. The exception is a burnt out torch, which is not a device at all but the
 * same torch a moment before it went out, so relighting it is free.
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TorchIgnitionEvents {

    private TorchIgnitionEvents() {
    }

    /**
     * Lights one of the devices a torch is allowed to light, and spends the torch.
     *
     * <p>Only ever answers a weak request: a strong one comes from a firestarter, a flint and steel or a fire
     * charge, and those already have TFC's own lighting behind them - the unrestricted charcoal forge is lit from
     * that path already - so a torch that stepped in there would be spending itself on a fire it did not start.
     * The item check below is what keeps this honest, and it also keeps out the dropped torch, which asks for a
     * strong fire from an empty stack.
     */
    @SubscribeEvent
    public static void onStartFire(StartFireEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isShiftKeyDown() || event.isStrong()) {
            return;
        }

        ItemStack torch = event.getItemStack();
        if (!torch.is(TFCItems.TORCH.get())) {
            return;
        }

        TorchIgnitionRules.Target target = TorchIgnitionRules.byId(
                BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString());
        if (target == null) {
            return;
        }

        if (!event.getLevel().isClientSide() && light(event.getLevel(), event.getPos(), event.getState(), target)) {
            spendTorch(player, torch, target);
        }

        event.setCanceled(true);
    }

    /**
     * Sets a mob alight when it is struck with a lit torch.
     *
     * <p>The client runs its own copy of the attack for prediction, so this only ever acts on the server. Nothing
     * is spent: the torch is a brand to be waved at something, not a charge to be thrown.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.getMainHandItem().is(TFCItems.TORCH.get())) {
            return;
        }
        if (event.getTarget() instanceof LivingEntity target) {
            target.setSecondsOnFire(TorchIgnitionRules.MOB_FIRE_SECONDS);
        }
    }

    /**
     * @return whether the target actually caught. A device that did not light is left alone.
     */
    private static boolean light(Level level, BlockPos pos, BlockState state, TorchIgnitionRules.Target target) {
        if (target.relightsInPlace()) {
            relight(level, pos, state, target);
            return true;
        }

        return switch (target) {
            case FIREPIT -> level.getBlockEntity(pos) instanceof AbstractFirepitBlockEntity<?> firepit
                    && firepit.light(state);
            case CHARCOAL_FORGE, UNRESTRICTED_CHARCOAL_FORGE ->
                    level.getBlockEntity(pos) instanceof CharcoalForgeBlockEntity forge && forge.light(state);
            case BLAST_FURNACE -> level.getBlockEntity(pos) instanceof BlastFurnaceBlockEntity furnace
                    && furnace.light(level, pos, state);
            case BLOOMERY -> level.getBlockEntity(pos) instanceof BloomeryBlockEntity bloomery
                    && bloomery.light(state);
            case PIT_KILN -> level.getBlockEntity(pos) instanceof PitKilnBlockEntity kiln && kiln.tryLight();
            case LOG_PILE -> lightLogPile(level, pos);
            default -> false;
        };
    }

    /**
     * Puts a burning torch back where a burnt out one stood.
     *
     * <p>The wall variant is rare enough to be worth mentioning twice: it keeps the face it was mounted on, which
     * is the whole point of the block existing separately, so the replacement is built from the old state rather
     * than from the floor torch's default.
     *
     * <p>The burn counter is then started by hand. A torch is normally safe for its full life because placing it
     * from the item resets the counter, and this replacement never goes through that item: the fresh block entity
     * would otherwise be left holding the sentinel a new counter is born with, which reads as having burned since
     * the beginning of time, and the torch would go out again on its very next random tick.
     */
    private static void relight(Level level, BlockPos pos, BlockState state, TorchIgnitionRules.Target target) {
        if (target == TorchIgnitionRules.Target.DEAD_WALL_TORCH) {
            level.setBlockAndUpdate(pos, TFCBlocks.WALL_TORCH.get().defaultBlockState()
                    .setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING)));
        } else {
            level.setBlockAndUpdate(pos, TFCBlocks.TORCH.get().defaultBlockState());
        }
        TickCounterBlockEntity.reset(level, pos);
    }

    /**
     * @return whether the pile had logs in it to burn. TFC lights an empty pile as readily as a full one, and a
     *         burning pile with no logs in it makes no charcoal, so the torch is kept instead.
     */
    private static boolean lightLogPile(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LogPileBlockEntity pile) || pile.logCount() <= 0) {
            return false;
        }
        BurningLogPileBlock.lightLogPile(level, pos);
        return true;
    }

    /**
     * Spends the torch, in survival only. The stack is the one the event carries, which for a player is the live
     * stack in the hand that clicked - the same stack TFC's own torch use shrinks when it is relit from.
     */
    private static void spendTorch(Player player, ItemStack torch, TorchIgnitionRules.Target target) {
        if (target.consumesTorch() && !player.isCreative()) {
            torch.shrink(1);
        }
    }
}
