package first.wildfires.soldering;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import org.slf4j.Logger;

/**
 * Client-only AUI wiring. Loaded only when ApricityUI is present.
 *
 * <p>{@link #registerScreen()} registers the AUI container screen for our menu type. The screen is
 * {@link SolderingScreen}, which is constructor-only: AUI draws the page, and every backpack click
 * goes through {@code AbstractContainerScreen}'s normal slot path.
 *
 * <p>{@link #registerBindings()} tells AUI which package contains the {@code @KJSBindings} class.
 * It must run during mod initialisation, before KubeJS asks AUI to register page globals.
 */
public final class SolderingAuiClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SolderingAuiClient() {
    }

    public static void registerBindings() {
        com.sighs.apricityui.script.KubeJS.scanPackage("first.wildfires.soldering");
    }

    public static void registerScreen() {
        LOGGER.info("[Soldering] registering SolderingScreen for {}", SolderingRegister.SOLDERING_MENU.getId());
        MenuScreens.register(SolderingRegister.SOLDERING_MENU.get(), SolderingScreen::new);
    }
}
