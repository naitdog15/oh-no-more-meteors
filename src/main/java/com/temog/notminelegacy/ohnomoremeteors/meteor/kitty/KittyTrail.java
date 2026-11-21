package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side fall particles for a Komit Kitty (Veyrite look, no new particles).
 *
 * Oh My, Meteors! sends about 55 particles in 5 packets per meteor per tick to EVERY player in the level. A kitty
 * shower has up to 18 kitties in the air, so MeteorFallParticlesMixin cancels OMM's server part for kitties and
 * calls {@link #emit} instead: a few Veyrite particles, sent only to players within the trail range, with the
 * long-distance flag so far players still see them.
 *
 * Particle table (per fall tick): FANCY = flame x4 every tick, smoke x3 and big smoke x2 every 2nd tick, spark x2
 * every 4th tick. LESS = flame x2, big smoke x1 every 2nd tick, spark x1 every 4th tick. MINIMAL = big smoke x1
 * every 2nd tick. OMM's NONE mode calls no animation method, so it gives no trail.
 */
public final class KittyTrail {

    /** Particle mode 0: OMM's "fancy" fall animation. */
    public static final int MODE_FANCY = 0;
    /** Particle mode 1: OMM's "less" fall animation. */
    public static final int MODE_LESS = 1;
    /** Particle mode 2: OMM's "minimal" fall animation. */
    public static final int MODE_MINIMAL = 2;

    /** How many times {@link #emit} has been called since start, counted even when no player is near (a check that the hook is live; read by tests). */
    public static final AtomicLong EMITTED = new AtomicLong();

    private static final double SPREAD = 0.15D;
    private static boolean loggedFailure;
    private static boolean loggedHookFailure;

    private KittyTrail() {
    }

    /**
     * Sends one tick of trail particles for a falling kitty to the players near it. Server side only.
     *
     * @param kitty the falling kitty (its tick count picks the every-2nd and every-4th tick rows)
     * @param x     where OMM is about to place the kitty this tick
     * @param y     same, height
     * @param z     same, depth
     * @param mode  0 fancy, 1 less, 2 minimal (which OMM animation method called this)
     */
    public static void emit(MeteorProjectileEntity kitty, double x, double y, double z, int mode) {
        EMITTED.incrementAndGet();
        try {
            if (!(kitty.level() instanceof ServerLevel level)) {
                return;
            }
            int tick = kitty.tickCount;
            boolean every2 = (tick & 1) == 0;
            boolean every4 = (tick & 3) == 0;

            int flame = mode == MODE_FANCY ? 4 : mode == MODE_LESS ? 2 : 0;
            int smoke = mode == MODE_FANCY && every2 ? 3 : 0;
            int bigSmoke = every2 ? (mode == MODE_FANCY ? 2 : 1) : 0;
            int spark = every4 ? (mode == MODE_FANCY ? 2 : mode == MODE_LESS ? 1 : 0) : 0;
            if (flame == 0 && smoke == 0 && bigSmoke == 0 && spark == 0) {
                return;
            }

            double range = Config.KITTY_TRAIL_RANGE.get();
            double rangeSq = range * range;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, y, z) > rangeSq) {
                    continue;
                }
                send(level, player, ModParticles.VEYRITE_FLAME.get(), flame, x, y, z, 0.02D);
                send(level, player, ModParticles.VEYRITE_SMOKE.get(), smoke, x, y, z, 0.01D);
                send(level, player, ModParticles.VEYRITE_BIG_SMOKE.get(), bigSmoke, x, y, z, 0.01D);
                send(level, player, ModParticles.VEYRITE_SPARK.get(), spark, x, y, z, 0.03D);
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedFailure) {
                loggedFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] A Komit Kitty trail could not be sent; kitties may fall without a trail (this is logged once).", ex);
            }
        }
    }

    /** Logs, once, that the fall-particle hook for kitties failed (called from MeteorFallParticlesMixin). */
    public static void warnHookFailure(Throwable ex) {
        if (!loggedHookFailure) {
            loggedHookFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The Komit Kitty fall trail hook failed; OMM's own fall particles run for kitties (this is logged once).", ex);
        }
    }

    private static void send(ServerLevel level, ServerPlayer player, ParticleOptions type, int count,
                             double x, double y, double z, double speed) {
        if (count > 0) {
            level.sendParticles(player, type, true, x, y, z, count, SPREAD, SPREAD, SPREAD, speed);
        }
    }
}
