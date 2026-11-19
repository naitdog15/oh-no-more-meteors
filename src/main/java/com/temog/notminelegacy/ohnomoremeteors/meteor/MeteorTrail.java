package com.temog.notminelegacy.ohnomoremeteors.meteor;

import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Server-side particle trail for falling fire and ice meteors (vanilla particles only, no client code).
 *
 * The list of live special meteors is kept from join and leave events, so the per-tick work never scans
 * entities. It is only touched on the server thread. Particles are sent per player and only to players
 * within the configured range; a hard per-level-tick budget drops the extras during a meteor shower.
 *
 * Fire: ember flakes (custom) and smoke. Ice: rime shards (custom) and white ash. Space: vanilla portal, villager and end rod particles.
 */
public final class MeteorTrail {

    /** Most particle sends per level tick, counted per particle AND per recipient, so many players do not multiply the load. */
    private static final int BUDGET_PER_TICK = 480;
    /** Vanilla only draws particles from the normal packet within this distance; beyond it the long-distance flag is needed. */
    private static final double NEAR_SQ = 32.0 * 32.0;

    /** Live special meteors and their variant. Identity based, server thread only. */
    private static final Map<Entity, MeteorVariant> TRACKED = new IdentityHashMap<>();

    private MeteorTrail() {
    }

    static void track(Entity meteor, MeteorVariant variant) {
        TRACKED.put(meteor, variant);
    }

    static void untrack(Entity meteor) {
        if (!TRACKED.isEmpty()) {
            TRACKED.remove(meteor);
        }
    }

    static void clear() {
        TRACKED.clear();
    }

    /** Called once per server level tick (END phase). */
    static void tick(ServerLevel level) {
        if (TRACKED.isEmpty()) {
            return;
        }
        boolean enabled = Config.TRAIL_ENABLED.get();
        // Emit every second tick; the particles are short lived and this halves the packet count.
        boolean emitNow = enabled && (level.getGameTime() & 1L) == 0L;
        double range = Config.TRAIL_RANGE.get();
        double rangeSq = range * range;
        int budget = BUDGET_PER_TICK;

        Iterator<Map.Entry<Entity, MeteorVariant>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, MeteorVariant> entry = it.next();
            Entity meteor = entry.getKey();
            if (meteor.isRemoved()) {
                it.remove();
                continue;
            }
            if (!emitNow || meteor.level() != level || budget <= 0) {
                continue;
            }
            budget -= emit(level, meteor, entry.getValue(), rangeSq, budget);
        }
    }

    /** Sends one emission for one meteor; returns how many particles it spent from the budget. */
    private static int emit(ServerLevel level, Entity meteor, MeteorVariant variant, double rangeSq, int budget) {
        if (!VariantTuning.trailOn(variant)) {
            return 0;
        }
        double density = VariantTuning.trailDensity(variant);
        Vec3 motion = meteor.getDeltaMovement();
        double speed = motion.length();
        Vec3 back = speed < 1.0E-2 ? new Vec3(0, 1, 0) : motion.scale(-1.0 / speed);

        // Start behind the meteor's body, spread along the stretch it just flew so fast meteors leave no gaps.
        double width = meteor.getBbWidth();
        double along = width * 0.5 + level.getRandom().nextDouble() * Math.min(speed, 4.0);
        Vec3 base = meteor.position().add(0, meteor.getBbHeight() * 0.5, 0).add(back.scale(along));

        int size = meteor instanceof MeteorProjectileEntity m ? m.getSize() : 1;
        int baseCount = 1 + Math.min(3, size / 5);
        int count = VariantTuning.densityCount(baseCount, density);
        // Shards / portal particles use base + 1, scaled as a whole by density.
        int countPlus = VariantTuning.densityCount(baseCount + 1, density);
        // Single-particle sends (lava drip, smoke, ash body) follow the same density; 0 skips them.
        int one = VariantTuning.densityCount(1, density);
        if (count <= 0 && countPlus <= 0 && one <= 0) {
            return 0;
        }
        double spread = 0.15 + width * 0.2;

        List<ServerPlayer> near = new ArrayList<>();
        List<Boolean> far = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(base);
            if (d <= rangeSq) {
                near.add(p);
                far.add(d > NEAR_SQ);
            }
        }
        if (near.isEmpty()) {
            return 0;
        }

        int spent = 0;
        // Each send reports its real cost (particles x recipients) and is skipped when the budget cannot cover it.
        if (variant == MeteorVariant.CINDRITE) {
            spent += send(level, near, far, ModParticles.CINDRITE_EMBER.get(), base, count, spread, 0.02, budget - spent);
            spent += send(level, near, far, ModParticles.CINDRITE_SMOKE.get(), base.add(back.scale(1.0)), one, spread, 0.01, budget - spent);
        } else if (variant == MeteorVariant.VEYRITE) {
            spent += send(level, near, far, ModParticles.VEYRITE_SPARK.get(), base, countPlus, spread, 0.03, budget - spent);
            spent += send(level, near, far, ModParticles.VEYRITE_FLAME.get(), base.add(back.scale(0.5)), one, spread, 0.0, budget - spent);
            spent += send(level, near, far, ModParticles.VEYRITE_SMOKE.get(), base.add(back.scale(1.0)), one, spread, 0.01, budget - spent);
        } else {
            spent += send(level, near, far, ModParticles.RIMERITE_SHARD.get(), base, countPlus, spread, 0.03, budget - spent);
            spent += send(level, near, far, ModParticles.RIMERITE_SMOKE.get(), base.add(back.scale(1.0)), count, spread, 0.02, budget - spent);
        }
        return spent;
    }

    /**
     * Sends one particle kind to every listed player. Far players get a single particle and the long-distance flag.
     * Returns the cost spent: the sum of particles sent over all recipients (0 if the budget could not cover it).
     */
    private static int send(ServerLevel level, List<ServerPlayer> players, List<Boolean> far, ParticleOptions particle,
                            Vec3 pos, int count, double spread, double speed, int budgetLeft) {
        if (count <= 0) {
            return 0;
        }
        int cost = 0;
        for (Boolean isFar : far) {
            cost += isFar ? 1 : count;
        }
        if (cost > budgetLeft) {
            return 0;
        }
        for (int i = 0; i < players.size(); i++) {
            boolean isFar = far.get(i);
            level.sendParticles(players.get(i), particle, isFar, pos.x, pos.y, pos.z,
                    isFar ? 1 : count, spread, spread, spread, speed);
        }
        return cost;
    }
}
