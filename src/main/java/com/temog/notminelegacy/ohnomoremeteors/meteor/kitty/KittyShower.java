package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * State of one running Komit Kitty shower. Server thread only; nothing here is ever saved.
 *
 * The shower is mostly a data-only queue of {@link KittyDrop}s. A real meteor entity exists only for a kitty that is
 * falling right now (at most {@code maxAlive}). {@link KittyShowers} owns the lifecycle; the other classes of this
 * package read and update the fields directly (package-private on purpose), the outside world uses the getters.
 */
public final class KittyShower {

    final ServerLevel level;
    final double cx;
    final double cz;
    final long startTick;
    final KittyShowerParams params;
    /** Ticks after {@link #startTick} at which the shower ends no matter what (warning + duration + 1200). */
    final long timeoutTicks;

    /** True until the first level tick has run the deferred init (the early warning). */
    boolean pendingInit = true;
    boolean finished;
    /** The final warning has gone out (chat, arrival sound, sky at full strength, lasers gathered). */
    boolean finalWarned;
    /** Game time of the next sky-shift refresh sent to the players near the shower. */
    long nextSkyPing;

    /** Planned drops, sorted by due time, built once. */
    final ArrayDeque<KittyDrop> queue;
    /** Kitties falling right now (resolved ones stay until the next interceptor pass removes them). */
    final ArrayList<KittyFlight> alive = new ArrayList<>();
    /** Entity to flight. Entries are removed only by the shower itself, never by a leave-level event. */
    final IdentityHashMap<Entity, KittyFlight> byEntity = new IdentityHashMap<>();

    ArrayList<KittyLasers.Laser> lasers = new ArrayList<>();
    long nextLaserRefresh;

    int dropped;
    int shot;
    int landed;
    int fizzled;
    int cats;

    /** Beams sent in the tick {@link #beamTick}; the cap per tick keeps the particle load bounded. */
    int beamsThisTick;
    long beamTick = Long.MIN_VALUE;

    KittyShower(ServerLevel level, double cx, double cz, long startTick, KittyShowerParams params,
                ArrayDeque<KittyDrop> queue, long timeoutTicks) {
        this.level = level;
        this.cx = cx;
        this.cz = cz;
        this.startTick = startTick;
        this.params = params;
        this.queue = queue;
        this.timeoutTicks = timeoutTicks;
    }

    /** Kitties shot down by a laser so far. */
    public int shot() {
        return shot;
    }

    /** Kitties that reached the ground (counted when their impact finished). */
    public int landed() {
        return landed;
    }

    /** Kitties that were created. */
    public int dropped() {
        return dropped;
    }

    /** Drops that never became an entity or were removed in flight because no player was near. */
    public int fizzled() {
        return fizzled;
    }

    /** Meteor cats placed in capsules by this shower. */
    public int cats() {
        return cats;
    }

    /** True once the shower has ended or been stopped. */
    public boolean isFinished() {
        return finished;
    }

    /** Shower centre, block x. */
    public double centreX() {
        return cx;
    }

    /** Shower centre, block z. */
    public double centreZ() {
        return cz;
    }

    /** Drops still waiting in the queue. */
    public int queued() {
        return queue.size();
    }

    /** Kitties currently tracked as falling (including ones resolved this tick that are not purged yet). */
    public int aliveCount() {
        return alive.size();
    }

    /** The parameters this shower was started with. */
    public KittyShowerParams params() {
        return params;
    }

    /** The lasers found at the last refresh (read-only view). */
    public List<KittyLasers.Laser> lasers() {
        return lasers;
    }
}

/**
 * One falling kitty, as the shower sees it. The target column is the place the kitty was aimed at; the laser arrays are
 * indexed alike, one slot per laser whose square covers that column.
 */
final class KittyFlight {

    final KittyShower owner;
    final MeteorProjectileEntity kitty;
    double prevY;
    final double tx;
    final double ty;
    final double tz;

    /** Indexes into {@code owner.lasers} of the lasers covering the landing column. */
    int[] lasers = new int[0];
    /** Per covering laser: this flight's band bottom, max(bandBottom, ty). */
    double[] lo = new double[0];
    /** Per covering laser: this flight's band height, bandTop - lo (can be zero or negative). */
    double[] hEff = new double[0];
    /** Per covering laser: share of that laser's band that has already been rolled (0..1). */
    double[] done = new double[0];
    /** Shot, landed or dropped: never rolled or counted again. */
    boolean resolved;

    KittyFlight(KittyShower owner, MeteorProjectileEntity kitty, double prevY, double tx, double ty, double tz) {
        this.owner = owner;
        this.kitty = kitty;
        this.prevY = prevY;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    /**
     * (Re)computes the covering lasers, band bottoms and band heights from a laser list. A laser the flight already had
     * (matched by block position in {@code previous}) keeps its {@code done} share; a new laser starts at 0.
     *
     * @param current  the new laser list; the indexes stored in {@link #lasers} point into it
     * @param previous the list the old indexes pointed into, or null when this is the first binding
     */
    void bind(List<KittyLasers.Laser> current, @Nullable List<KittyLasers.Laser> previous) {
        int[] idx = KittyLasers.covering(current, tx, tz);
        double[] newLo = new double[idx.length];
        double[] newH = new double[idx.length];
        double[] newDone = new double[idx.length];
        for (int i = 0; i < idx.length; i++) {
            KittyLasers.Laser l = current.get(idx[i]);
            newLo[i] = Math.max(l.bandBottom, ty);
            newH[i] = l.bandTop - newLo[i];
            if (previous != null) {
                for (int j = 0; j < lasers.length; j++) {
                    int old = lasers[j];
                    if (old >= 0 && old < previous.size() && previous.get(old).pos.equals(l.pos)) {
                        newDone[i] = done[j];
                        break;
                    }
                }
            }
        }
        this.lasers = idx;
        this.lo = newLo;
        this.hEff = newH;
        this.done = newDone;
    }
}
