package first.wildfires.register;

import first.wildfires.Wildfires;
import first.wildfires.network.PlayerInputPacket;
import first.wildfires.network.TemperatureFilterSyncPacket;
import first.wildfires.network.ThermalOffsetSyncPacket;
import first.wildfires.network.ThermalDebugRequestPacket;
import first.wildfires.network.ThermalDebugSnapshotPacket;
import first.wildfires.network.CelestialSettingsSyncPacket;
import first.wildfires.network.TfcCalendarRateSyncPacket;
import first.wildfires.network.TemperatureRangesSyncPacket;
import first.wildfires.network.StationContextPacket;
import first.wildfires.network.StationRemovedPacket;
import first.wildfires.network.RequestStationTravelPacket;
import first.wildfires.network.ReturnCapsuleTransitionPacket;
import first.wildfires.network.ReturnCapsuleTransitionArmedPacket;
import first.wildfires.network.ReturnCapsuleTrackingAckPacket;
import first.wildfires.network.ReturnCapsuleTrackingCommitPacket;
import first.wildfires.network.ReturnCapsuleTrackingReadyPacket;
import first.wildfires.network.ReturnCapsuleTransitionCompletePacket;
import first.wildfires.network.ReturnCapsuleTransitionAbortPacket;
import first.wildfires.network.GalaxyHymnImpactVisualPacket;
import first.wildfires.network.PerfectForgeRequestPacket;
import first.wildfires.network.PerfectForgeSyncPacket;
import first.wildfires.network.ForgeSeedSyncPacket;
import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkPacketRegister {

    public static final String Version = "21";
    public static final SimpleChannel Instance = NetworkRegistry.newSimpleChannel(Wildfires.rl("main"), () -> Version, Version::equals, Version::equals);
    public static int id = 1;

    public static void register() {
        ICustomPacketPayload.register(PlayerInputPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(TemperatureFilterSyncPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ThermalOffsetSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(ThermalDebugRequestPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ThermalDebugSnapshotPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(CelestialSettingsSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(TfcCalendarRateSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(TemperatureRangesSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(StationContextPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(StationRemovedPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(RequestStationTravelPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ReturnCapsuleTransitionPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(ReturnCapsuleTransitionArmedPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ReturnCapsuleTrackingAckPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ReturnCapsuleTrackingCommitPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(ReturnCapsuleTrackingReadyPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ReturnCapsuleTransitionCompletePacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(ReturnCapsuleTransitionAbortPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(GalaxyHymnImpactVisualPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(PerfectForgeRequestPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(PerfectForgeSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(ForgeSeedSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
    }

}
