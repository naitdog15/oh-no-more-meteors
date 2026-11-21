package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Switches Oh My, Meteors!' own skyglow off while the add-on's sky glow replaces it (config [skyGlow]
 * replaceOmmSkyGlow). Common code, so it runs on a dedicated server as well as on the client: whichever side OMM
 * decides the glow on, it sees the switch off. Only the in-memory value changes; OMM's config file is left as it is.
 *
 * Called when a server starts (integrated or dedicated) and from the client tick (client.SkyGlow), so a config
 * reload that turns OMM's value back on is caught again.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OmmSkyGlowSwitch {

    private static boolean warned;

    private OmmSkyGlowSwitch() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        apply();
    }

    /** Turns OMM's skyglow off if ours replaces it. Cheap enough to call every tick (two config reads). */
    public static void apply() {
        try {
            if (Config.SKY_GLOW_ENABLED.get() && Config.SKY_GLOW_REPLACE_OMM.get()
                    && OhMyMeteors.CONFIG.visualsSection.meteor_skyglow) {
                OhMyMeteors.CONFIG.visualsSection.meteor_skyglow = false;
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!warned) {
                warned = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not switch Oh My, Meteors! skyglow off; both glows may show (logged once).", ex);
            }
        }
    }
}
