package first.wildfires.client.smithing;

import net.minecraft.client.Minecraft;

/**
 * The client-only half of the forging manual item.
 *
 * <p>Kept apart from the item itself so the item class can live on a dedicated server without mentioning
 * a screen.
 */
public final class ClientForgingManual {

    private ClientForgingManual() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ForgingManualScreen());
    }
}
