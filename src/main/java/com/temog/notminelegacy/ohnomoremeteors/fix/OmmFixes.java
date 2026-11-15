package com.temog.notminelegacy.ohnomoremeteors.fix;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-side Oh My, Meteors! fixes (no mixin needed):
 *  - B4: OMM keeps its list of meteor structures in a public static field that is never cleared when a server
 *    stops, so a single-player session that opens another world would keep the old world's list until /reload.
 *    A ServerStoppedEvent handler resets it; OMM rebuilds the list lazily when it is empty.
 *  - One start-up INFO line that lists which fixes are switched on, plus a warning when the temperature band
 *    thresholds are out of order.
 *  - {@link #warnOnce}: the shared "log a failure only once" helper for the mixin fixes. It lives here, outside
 *    the mixin package, because classes in the mixin package cannot be loaded as ordinary classes.
 *
 * The mixin fixes (B1-B3, Q1) live in {@code mixin.omm.*}; each checks its own config key at the point of use.
 * This class subscribes itself on the FORGE event bus; nothing needs wiring in the main class.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OmmFixes {

    /** Keys of the failures already logged this game run. */
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private OmmFixes() {
    }

    /**
     * Logs {@code message} with the exception as a WARN the first time {@code key} fails in this game run,
     * and stays quiet after that. Used by the mixin handlers, which can run every tick.
     */
    public static void warnOnce(String key, String message, Throwable ex) {
        if (LOGGED_FAILURES.add(key)) {
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] " + message + " (further failures of this fix are not logged)", ex);
        }
    }

    /** B4: forget the stopped server's structure list. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        try {
            if (Config.FIX_STALE_STRUCTURE_LIST.get()) {
                // A fresh list rather than clear(): OMM may hand the old list out, so never mutate it in place.
                OhMyMeteors.METEOR_STRUCTURES = new ArrayList<>();
            }
        } catch (RuntimeException | LinkageError ex) {
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not clear the OMM structure list on server stop.", ex);
        }
    }

    /** Logs the active fixes once per server start, and checks the band thresholds. The config is loaded by this point. */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            List<String> active = new ArrayList<>();
            if (Config.FIX_BASIC_LASER_RADIUS.get()) {
                active.add("fixBasicLaserRadius");
            }
            if (Config.FIX_SPECIAL_ONLY_HANG.get()) {
                active.add("fixSpecialOnlyHang");
            }
            if (Config.FIX_ENTITY_HIT_CLAIM_CHECK.get()) {
                active.add("fixEntityHitClaimCheck");
            }
            if (Config.FIX_STALE_STRUCTURE_LIST.get()) {
                active.add("fixStaleStructureList");
            }
            if (Config.METEORIC_ROCK_WATER_QUENCH.get()) {
                active.add("meteoricRockWaterQuench");
            }
            OhNoMoreMeteors.LOGGER.info("[ohnomoremeteors] Oh My, Meteors! fixes active: {}",
                    active.isEmpty() ? "none" : String.join(", ", active));

            // The bands are cut in a fixed order (COLD first, then HOT, then WARM). Out-of-order thresholds make a
            // band overlap another or never happen, so say so once instead of guessing what was meant.
            double cold = Config.COLD_BELOW.get();
            double warm = Config.WARM_ABOVE.get();
            double hot = Config.HOT_ABOVE.get();
            if (!(cold < warm && warm <= hot)) {
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Temperature thresholds are out of order (coldBelow {}, warmAbove {}, hotAbove {});"
                        + " keep coldBelow < warmAbove <= hotAbove, or some temperature bands overlap or never happen.", cold, warm, hot);
            }
        } catch (RuntimeException ex) {
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not list the active Oh My, Meteors! fixes.", ex);
        }
    }
}
