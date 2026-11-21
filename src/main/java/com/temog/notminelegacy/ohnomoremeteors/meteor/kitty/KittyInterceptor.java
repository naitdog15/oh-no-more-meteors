package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.network.ModNetwork;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import me.emafire003.dev.ohmymeteors.sounds.OMMSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/**
 * Lets Meteor Lasers shoot Komit Kitties. Runs once per level tick for the running shower ({@link #tick}) and once more
 * at the moment a kitty hits the ground ({@link #interceptAtImpact}).
 *
 * The rolls follow the pass-chance share model of {@link KittyLasers}. A laser may only roll after
 * {@link #valid} confirms it is still there with open sky, so a laser broken or roofed over since the last refresh
 * stops working on its very next roll. The check comes before the roll (not only after a hit), so a laser that cannot
 * shoot never uses up any of its share of the band: the pass chance stays exact if it works again later.
 * The laser block itself is never touched (no state change, no cooldown flip).
 */
public final class KittyInterceptor {

    private KittyInterceptor() {
    }

    /**
     * One pass over the shower's falling kitties: drops finished flights, fizzles kitties that no longer tick, and rolls
     * the share of each covering laser's band that the kitty crossed this tick.
     */
    public static void tick(KittyShower s) {
        ServerLevel level = s.level;
        boolean lasersOn = Config.KITTY_LASERS_ENABLED.get();
        RandomSource random = level.getRandom();
        ArrayList<KittyFlight> alive = s.alive;
        for (int i = alive.size() - 1; i >= 0; i--) {
            KittyFlight f = alive.get(i);
            MeteorProjectileEntity kitty = f.kitty;
            if (f.resolved || kitty.isRemoved()) {
                // Landed kitties are counted by KittyShowers.onKittyLanded; other removals are simply dropped.
                f.resolved = true;
                removeAt(s, i);
                continue;
            }
            // A frozen kitty would hold the shower open and be saved with its chunk, so it is dropped when nobody is near.
            if (!level.isPositionEntityTicking(kitty.blockPosition())) {
                f.resolved = true;
                kitty.discard();
                s.fizzled++;
                removeAt(s, i);
                continue;
            }
            double curY = kitty.getY();
            boolean killed = false;
            if (lasersOn) {
                for (int j = 0; j < f.lasers.length && !killed; j++) {
                    int idx = f.lasers[j];
                    if (idx < 0 || idx >= s.lasers.size()) {
                        continue;
                    }
                    KittyLasers.Laser laser = s.lasers.get(idx);
                    if (laser.dead || f.hEff[j] <= 0.0D) {
                        continue;
                    }
                    double overlap = KittyLasers.bandOverlap(f.prevY, curY, f.lo[j], laser.bandTop);
                    if (overlap <= 0.0D) {
                        continue;
                    }
                    double share = Math.min(overlap / f.hEff[j], 1.0D - f.done[j]);
                    if (share <= 0.0D) {
                        continue;
                    }
                    // Check the laser BEFORE rolling: a laser that is gone, unloaded or roofed over must not use up any
                    // of its share (done stays as it is), or it could let a kitty through once it works again.
                    if (!valid(level, laser)) {
                        continue;
                    }
                    boolean hit = KittyLasers.roll(KittyLasers.passChance(laser), share, random);
                    f.done[j] += share;
                    if (hit) {
                        kill(s, f, laser, kitty.position());
                        killed = true;
                    }
                }
            }
            if (killed) {
                removeAt(s, i);
                continue;
            }
            f.prevY = curY;
        }
    }

    /**
     * The last roll for a kitty that has just hit something. Called as the very first step of the landing, before the
     * kitty is discarded. Rolls the whole share of every covering laser's band that has not been rolled yet, so no impact
     * height is needed (OMM moves the kitty back before it explodes, so the entity's y is not the real impact height).
     *
     * @return true if a laser shot the kitty: it has been discarded and counted, and there is nothing left to land
     */
    public static boolean interceptAtImpact(ServerLevel level, MeteorProjectileEntity kitty) {
        try {
            if (!Config.KITTY_LASERS_ENABLED.get()) {
                return false;
            }
            KittyShower s = KittyShowers.ownerOf(level, kitty);
            if (s == null) {
                return false;
            }
            KittyFlight f = s.byEntity.get(kitty);
            if (f == null || f.resolved) {
                return false;
            }
            RandomSource random = level.getRandom();
            for (int j = 0; j < f.lasers.length; j++) {
                int idx = f.lasers[j];
                if (idx < 0 || idx >= s.lasers.size()) {
                    continue;
                }
                KittyLasers.Laser laser = s.lasers.get(idx);
                if (laser.dead) {
                    continue;
                }
                double share = 1.0D - f.done[j];
                if (share <= 0.0D) {
                    continue;
                }
                // As in tick: check first, so a laser that cannot shoot leaves its share unrolled.
                if (!valid(level, laser)) {
                    continue;
                }
                boolean hit = KittyLasers.roll(KittyLasers.passChance(laser), share, random);
                f.done[j] = 1.0D;
                if (hit) {
                    kill(s, f, laser, kitty.position());
                    return true;
                }
            }
            return false;
        } catch (RuntimeException | LinkageError ex) {
            KittyShowers.warnOnce("impact-intercept", ex);
            return false;
        }
    }

    /**
     * Checks a cached laser right before it rolls: its chunk is loaded (never loads it), the same block entity is
     * still there, and the sky above it is open. A laser that fails is marked dead until the next refresh.
     */
    public static boolean valid(ServerLevel level, KittyLasers.Laser l) {
        boolean ok;
        try {
            LevelChunk chunk = level.getChunkSource().getChunkNow(l.pos.getX() >> 4, l.pos.getZ() >> 4);
            ok = chunk != null
                    && !l.be.isRemoved()
                    && chunk.getBlockEntities().get(l.pos) == l.be
                    && level.canSeeSky(l.pos.above());
        } catch (RuntimeException | LinkageError ex) {
            KittyShowers.warnOnce("laser-valid", ex);
            ok = false;
        }
        if (!ok) {
            l.dead = true;
        }
        return ok;
    }

    /** Swap-removes flight i (the caller walks the list backwards) and forgets its entity. */
    private static void removeAt(KittyShower s, int i) {
        ArrayList<KittyFlight> alive = s.alive;
        KittyFlight f = alive.get(i);
        KittyFlight last = alive.remove(alive.size() - 1);
        if (i < alive.size()) {
            alive.set(i, last);
        }
        if (s.byEntity.get(f.kitty) == f) {
            s.byEntity.remove(f.kitty);
        }
    }

    /** Shoots the kitty: resolved, discarded, counted, with the sound and the beam. Nothing else happens in the world. */
    private static void kill(KittyShower s, KittyFlight f, KittyLasers.Laser laser, Vec3 at) {
        f.resolved = true;
        f.kitty.discard();
        s.shot++;
        try {
            effects(s, laser, at);
        } catch (RuntimeException | LinkageError ex) {
            KittyShowers.warnOnce("shot-effects", ex);
        }
    }

    /** OMM's own laser sound (at most once per laser per interval) and one beam message, capped per tick. */
    private static void effects(KittyShower s, KittyLasers.Laser laser, Vec3 at) {
        ServerLevel level = s.level;
        long now = level.getGameTime();
        if (now - laser.lastSoundTick >= Config.KITTY_LASER_SOUND_INTERVAL.get()) {
            laser.lastSoundTick = now;
            level.playSound(null, laser.pos, OMMSounds.LASER_FIRE.get(), SoundSource.BLOCKS, 1.0F, laser.advanced ? 1.4F : 1.25F);
        }
        if (s.beamTick != now) {
            s.beamTick = now;
            s.beamsThisTick = 0;
        }
        boolean beam = s.beamsThisTick < Config.KITTY_MAX_BEAMS_PER_TICK.get();
        if (beam) {
            s.beamsThisTick++;
        }
        ModNetwork.sendKittyShot(level, laser.pos, at, beam, laser.advanced);
    }
}
