package first.wildfires.event.forgeEvent;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.utility.CreateLang;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.client.ThermalDebugRenderer;
import first.wildfires.client.ThermalHudState;
import first.wildfires.client.space.NtmAscentAtmosphereVisuals;
import first.wildfires.client.space.OrbitVisualDebugClock;
import first.wildfires.client.space.ReturnCapsuleClientTransition;
import first.wildfires.client.space.ReturnCapsuleSoundController;
import first.wildfires.client.space.StationCoreOverlay;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.client.spell.GalaxyHymnImpactVisuals;
import first.wildfires.client.space.render.NtmAscentPlanetRenderer;
import first.wildfires.api.customEvent.CreativeTabBuildEvent;
import first.wildfires.compat.sacombat.SatchelSizeRules;
import first.wildfires.dumbbell.Dumbbells;
import first.wildfires.kinetic.loom.LoomControlBlock;
import first.wildfires.network.PlayerInputPacket;
import first.wildfires.ponder.WildfiresPonderPlugin;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.CreativeModeTabRegister;
import first.wildfires.register.ItemRegister;
import first.wildfires.utils.CuriosUtil;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.dries007.tfc.common.capabilities.size.Size;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvent {

    private static final float LOOM_STRESS_IMPACT = 4.0f;
    private static boolean jumpWasDown;

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ReturnCapsuleClientTransition.shutdown();
        CuriosUtil.clear();
        ThermalHudState.reset();
        GalaxyHymnImpactVisuals.reset();
        jumpWasDown = false;
    }

    /** Normal gameplay input is authoritative; this is the same Forge edge NTM-style launch used. */
    @SubscribeEvent
    public static void inputEvent(MovementInputUpdateEvent event) {
        syncJumpState(event.getInput().jumping);
    }


    @SubscribeEvent
    public static void renderThermalDebug(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && NtmAscentAtmosphereVisuals
                    .ascentBody(minecraft.level, event.getCamera()).isPresent()) {
                var state = CelestialClientStateCache.stateForBoundSurfaceAscent(minecraft.level,
                        event.getCamera().getPosition(), event.getPartialTick());
                NtmAscentPlanetRenderer.render(minecraft.level, state, event.getCamera(),
                        event.getPoseStack(), event.getProjectionMatrix());
            }
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ReturnCapsuleClientTransition.markTargetFrameRendered();
        }
        ThermalDebugRenderer.render(event);
    }

    @SubscribeEvent
      public static void tickThermalDebug(TickEvent.ClientTickEvent event) {
          if (event.phase == TickEvent.Phase.START) {
              GalaxyHymnImpactVisuals.tickShakeAtStart();
          }
          if (event.phase == TickEvent.Phase.END) {
              if (ReturnCapsuleClientTransition.armed()) syncPhysicalJumpKey();
              ReturnCapsuleClientTransition.tick();
              ReturnCapsuleSoundController.tick();
              ThermalDebugRenderer.tick();
              GalaxyHymnImpactVisuals.tick();
        }
    }

    @SubscribeEvent
    public static void renderGalaxyHymnImpact(RenderLevelStageEvent event) {
        GalaxyHymnImpactVisuals.render(event);
    }

    @SubscribeEvent
    public static void shakeGalaxyHymnCamera(ViewportEvent.ComputeCameraAngles event) {
        GalaxyHymnImpactVisuals.applyCameraShake(event);
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        var thermalDebug = Commands.literal("thermaldebug")
                .executes(context -> {
                    boolean enabled = ThermalDebugRenderer.toggle();
                    int sources = enabled ? ThermalDebugRenderer.refreshAndGetSourceCount() : 0;
                    context.getSource().sendSuccess(() -> Component.literal(enabled
                            ? "热调试已开启，热源数量：" + sources
                            : "热调试已关闭"), false);
                    return 1;
                })
                .then(Commands.literal("hidden").executes(context -> {
                    boolean enabled = ThermalDebugRenderer.toggleHidden();
                    context.getSource().sendSuccess(() -> Component.literal(enabled
                            ? "隐藏热场调试已开启（绿色）"
                            : "隐藏热场调试已关闭"), false);
                    return 1;
                }))
                .then(Commands.literal("stats").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            ThermalDebugRenderer.diagnosticsSummary()), false);
                    return 1;
                }));
        var thermalHud = Commands.literal("thermalhud")
                .executes(context -> setThermalHud(context.getSource(), ThermalHudState.toggle()))
                .then(Commands.literal("on")
                        .executes(context -> setThermalHud(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setThermalHud(context.getSource(), false)));
        event.getDispatcher().register(Commands.literal("wildfires")
                .then(thermalDebug)
                .then(thermalHud));
        if (!FMLEnvironment.production) {
            var orbitVisualTime = Commands.literal("orbitvisualtime")
                    .then(Commands.literal("set")
                            .then(Commands.argument("game_time", DoubleArgumentType.doubleArg(0.0D))
                                    .then(Commands.argument("calendar_ticks", DoubleArgumentType.doubleArg(0.0D))
                                            .executes(context -> {
                                                double gameTime = DoubleArgumentType.getDouble(context, "game_time");
                                                double calendarTicks = DoubleArgumentType.getDouble(context,
                                                        "calendar_ticks");
                                                OrbitVisualDebugClock.set(gameTime, calendarTicks);
                                                context.getSource().sendSuccess(() -> Component.literal(
                                                        "Orbit visual time frozen: game=" + gameTime
                                                                + ", calendar=" + calendarTicks), false);
                                                return 1;
                                            }))))
                    .then(Commands.literal("clear").executes(context -> {
                        OrbitVisualDebugClock.clear();
                        context.getSource().sendSuccess(() -> Component.literal(
                                "Orbit visual time returned to synchronized clocks"), false);
                        return 1;
                    }));
            event.getDispatcher().register(Commands.literal("wildfires")
                    .then(orbitVisualTime));
        }
    }

    @SubscribeEvent
    public static void renderStationCoreInformation(RenderGuiOverlayEvent.Post event) {
        StationCoreOverlay.render(event);
    }

    /** Polls the physical key through receiving screens so one held press cannot become release+press. */
    private static void syncPhysicalJumpKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            jumpWasDown = false;
            return;
        }
        // During ClientboundRespawn the player object can be absent for a few ticks. Preserve the
        // last state actually sent to the server; once the target player exists, a real release is
        // delivered and a still-held key does not manufacture a second press.
        if (minecraft.player == null) return;
        syncJumpState(minecraft.options.keyJump.isDown());
    }

    private static void syncJumpState(boolean jumping) {
        if (jumping == jumpWasDown) return;
        new PlayerInputPacket(jumping).sendToServer();
        jumpWasDown = jumping;
    }

    private static int setThermalHud(CommandSourceStack source, boolean enabled) {
        ThermalHudState.setEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "commands.wildfires.thermal_hud.enabled"
                : "commands.wildfires.thermal_hud.disabled"), false);
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        List<Component> toolTip = event.getToolTip();
        CompoundTag tag = stack.getTag();

        // Survivors Arsenal containers: show the TFC size ceiling of this container.
        //
        // The mod has no such setting, so the limit is imposed from the outside by
        // SatchelSizeRules; without a tooltip the player has no way to know why a large item is
        // refused. The size name is read from TFC's own tfc.enum.size.* keys, so the wording
        // matches what the size system shows everywhere else.
        Size sizeLimit = SatchelSizeRules.tooltipLimitFor(stack);
        if (sizeLimit != null) {
            toolTip.add(Component.translatable("wildfires.tooltip.size_limit",
                            Component.translatable(SatchelSizeRules.sizeTranslationKey(sizeLimit)))
                    .withStyle(ChatFormatting.GRAY));
        }

        if (tag != null && tag.contains("Reinforcement")) {
            int reinforcement = tag.getInt("Reinforcement");
            String statusKey = reinforcement >= 6 ? "wildfires.tooltip.reinforcement.firm" : reinforcement >= 4 ? "wildfires.tooltip.reinforcement.bound" : "wildfires.tooltip.reinforcement.loose";
            ChatFormatting statusColor = reinforcement >= 8 ? ChatFormatting.BLUE : reinforcement >= 6 ? ChatFormatting.WHITE : reinforcement >= 2 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;
            int reinforcementTooltipIndex = Math.min(1, toolTip.size());
            toolTip.add(reinforcementTooltipIndex, Component.translatable("wildfires.tooltip.reinforcement.normal", Component.translatable(statusKey).withStyle(statusColor)));
            if (player != null && player.getAbilities().instabuild) {
                int noDurabilityDamageChance = reinforcement * 5 - 20;
                toolTip.add(reinforcementTooltipIndex + 1, Component.translatable("wildfires.tooltip.reinforcement", reinforcement, noDurabilityDamageChance).withStyle(ChatFormatting.GRAY));
            }
        }
        BlockEntry<LoomControlBlock> loomControlBlock = BlockRegister.LoomControlBlock;
        if (stack.getItem() == loomControlBlock.asItem()) {
            WildfiresPonderPlugin plugin = WildfiresPonderPlugin.getPlugin();
            if (!plugin.loaded) {
                plugin.loaded = true;
                PonderIndex.addPlugin(plugin);
                PonderIndex.reload();
            }
            CreateLang.builder()
                    .space()
                    .addTo(toolTip);
            CreateLang.translate("tooltip.stressImpact")
                    .style(ChatFormatting.GRAY).addTo(toolTip);
            String string = player != null && (player.getItemBySlot(EquipmentSlot.HEAD).getItem() == AllItems.GOGGLES.get() || CuriosUtil.isEquipped(player, AllItems.GOGGLES.get())) ? " 4x RPM" : " 中";
            CreateLang.text(" ██▒" + string)
                    .style(ChatFormatting.GOLD)
                    .addTo(toolTip);
        }

        // Dumbbells: the vanilla attribute section lists the raw modifiers, so a dumbbell reads as
        // "-3.5 attack speed" even though the player's real attack speed is 0.5 (4.0 base - 3.5) and the real
        // damage of the item is 5 / 7. Show the totals the player actually gets instead.
        if (Dumbbells.isDumbbell(stack)) {
            replaceAttributeTotal(toolTip, Attributes.ATTACK_DAMAGE, Dumbbells.attackDamage(stack));
            replaceAttributeTotal(toolTip, Attributes.ATTACK_SPEED, Dumbbells.ATTACK_SPEED);
        }
    }

    /**
     * Rewrites the vanilla "when in main hand" line of one attribute so it shows the final value instead of the flat
     * modifier. Vanilla 1.20.1 fires {@link ItemTooltipEvent} once that section has been built, so the line is always
     * present by the time this runs; its original style (colour, indent) is kept.
     */
    private static void replaceAttributeTotal(List<Component> toolTip, Attribute attribute, double total) {
        String name = Component.translatable(attribute.getDescriptionId()).getString();
        if (name.isEmpty()) {
            return;
        }
        for (int i = 1; i < toolTip.size(); i++) {
            Component line = toolTip.get(i);
            String rendered = line.getString();
            int nameAt = rendered.lastIndexOf(name);
            if (nameAt < 0) {
                continue;
            }
            toolTip.set(i, Component.literal(attributeValueLead(rendered.substring(0, nameAt)))
                    .append(Component.translatable("attribute.modifier.equals.0",
                            ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(total),
                            Component.translatable(attribute.getDescriptionId())))
                    .withStyle(line.getStyle()));
            return;
        }
    }

    /** Everything in front of the number in a vanilla attribute line, so the rewrite keeps its indent. */
    private static String attributeValueLead(String beforeName) {
        int i = 0;
        while (i < beforeName.length()) {
            char c = beforeName.charAt(i);
            if (Character.isDigit(c) || c == '-' || c == '+' || c == '\u00d7') {
                break;
            }
            i++;
        }
        return beforeName.substring(0, i);
    }

}
