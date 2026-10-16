/*
 * Adapted from NTM: Space OrbitalStation / TileEntityOrbitalStation station lifecycle.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: replaces the 1.7.10 world-load and teleporter hooks with Forge
 * RegisterCommandsEvent, OnDatapackSyncEvent, ServerStartedEvent, BlockEvent.BreakEvent and
 * EntityMountEvent listeners plus the journey ticker, without adding a periodic station scan.
 */
package first.wildfires.space.station;

import first.wildfires.command.SpaceStationCommand;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationCoreService;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Forge-bus registration for station commands and definition-reload reconciliation. */
public final class StationServerEvents {

    private static boolean registered;

    private StationServerEvents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onBreakBlock);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onEntityMount);
        MinecraftForge.EVENT_BUS.addListener(StationJourneyTicker::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(StationJourneyTicker::onServerStopped);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wildfires")
                .then(SpaceStationCommand.createWildfiresBranch()));
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        StationService.reconcileDefinitions(SpaceSavedData.get(server),
                CelestialRegistryRuntime.current(), server.overworld().getGameTime());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        StationCoreService.ensureAll(event.getServer());
    }

    private static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && event.getState().is(SpaceContentRegister.STATION_CORE.get())
                && level.getBlockEntity(event.getPos()) instanceof first.wildfires.space.content.StationCoreBlockEntity core
                && core.primary()) {
            event.setCanceled(true);
        } else if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && event.getState().is(SpaceContentRegister.STATION_STRUCTURE.get())) {
            BlockPos corePos = StationCoreService.coreForStructureBlock(level, event.getPos()).orElse(null);
            if (corePos != null && level.getBlockEntity(corePos) instanceof first.wildfires.space.content.StationCoreBlockEntity core
                    && core.primary()) event.setCanceled(true);
        }
    }

    private static void onEntityMount(EntityMountEvent event) {
        if (!event.getEntity().level().isClientSide()
                && event.isDismounting()
                && event.getEntityBeingMounted() instanceof first.wildfires.space.capsule.ReusableReturnCapsuleEntity capsule
                && !capsule.capsuleState().interactive()
                && !capsule.transferDismountInProgress()) {
            event.setCanceled(true);
        }
    }

}
