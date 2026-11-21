package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import me.emafire003.dev.ohmymeteors.particles.OMMParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * Client only: draws one meteor-laser shot at a Komit Kitty (2026-11-20). The server sends one small message per kill
 * (KittyShotMessage); this class turns it into particles. It is reached only through DistExecutor, so a dedicated
 * server never loads it.
 *
 * It uses OMM's own laser particles directly (no ParticleAnimationLib), so the shot looks like an OMM laser shot:
 * a line of laser particles from the top of the laser to the kitty, a laser flash at the laser, and a small
 * Veyrite pop where the kitty was.
 */
public final class KittyClientEffects {

    /** Blocks between two beam particles. */
    private static final double BEAM_STEP = 0.75D;
    /** Most beam particles in one shot, whatever the length. */
    private static final int MAX_BEAM_POINTS = 160;

    private KittyClientEffects() {
    }

    /**
     * Draws one shot.
     *
     * <p>The beam starts at (fx, fy, fz), the top of the laser block, and ends at (tx, ty, tz), where the kitty was shot.
     *
     * @param beam     false when the server's per-tick beam budget was used up: only the flash and the pop are drawn
     * @param advanced true for the Advanced laser (kept for the message format; both lasers draw the same shot)
     */
    public static void shot(double fx, double fy, double fz, double tx, double ty, double tz, boolean beam, boolean advanced) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();

        if (beam) {
            double dx = tx - fx;
            double dy = ty - fy;
            double dz = tz - fz;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int points = Math.min(MAX_BEAM_POINTS, Math.max(1, (int) Math.ceil(length / BEAM_STEP)));
            for (int i = 0; i < points; i++) {
                double t = points <= 1 ? 1.0D : (double) i / (points - 1);
                level.addParticle(OMMParticles.LASER_PARTICLE.get(), true, fx + dx * t, fy + dy * t, fz + dz * t, 0.0D, 0.0D, 0.0D);
            }
        }

        // The flash at the laser top (OMM draws two) and the pop where the kitty was shot.
        for (int i = 0; i < 2; i++) {
            level.addParticle(OMMParticles.LASER_FLASH_PARTICLE.get(), true, fx, fy, fz,
                    (random.nextDouble() - 0.5D) * 0.02D, (random.nextDouble() - 0.5D) * 0.02D, (random.nextDouble() - 0.5D) * 0.02D);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(ModParticles.VEYRITE_SPARK.get(), true, tx, ty, tz,
                    (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D);
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(ModParticles.VEYRITE_SMOKE.get(), true, tx, ty, tz,
                    (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D);
        }
    }
}
