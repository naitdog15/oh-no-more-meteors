package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * What a Komit Kitty does when it gets past the lasers and lands. Called from the impact mixin at the head of OMM's
 * {@code explodeMeteor()} (server side only), which then cancels OMM's own impact: a kitty never makes OMM's blast,
 * structure, distant crash broadcast or plain-meteor cleanup.
 *
 * Steps of {@link #handle}:
 *  0. Find the owning shower and give the lasers their last roll, BEFORE anything is discarded. A kitty shot here
 *     makes no tunnel, blast, capsule or cat.
 *  1. Read the flight line and discard the kitty. One claim guard is made for the whole landing.
 *  2. Tunnel (only with meteor griefing on): the kitty digs down by block hardness.
 *  3. Blast at the end of the tunnel: TNT power, fire off, but a calculator makes the hole smaller (about 60% of
 *     TNT's blocks by default). If the claim guard refuses the end cell there is no blast, capsule or cat.
 *  4. Capsule at the end of the tunnel (built after the blast, so the blast cannot break it), with a cat roll.
 *  5. Fire pass: a few fires on the blown ground, never inside the capsule.
 *  6. Count the landing in the shower's totals.
 */
public final class KittyImpact {

    /** One warning per game run for a failed landing. */
    private static volatile boolean loggedFailure;
    private static volatile boolean loggedPopFailure;

    /** Players within this many blocks see the Veyrite pop. */
    private static final double POP_RANGE = 96.0D;

    private KittyImpact() {
    }

    /**
     * Logs one warning per game run about a failed kitty hook. The impact mixin calls this too, because a mixin
     * class cannot hold its own static flag.
     */
    public static void warnFailureOnce(String what, Throwable ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Komit Kitty {} failed (this is logged once).", what, ex);
        }
    }

    /**
     * Lands one kitty. Returns true when the kitty was dealt with (shot by a laser at the last moment, or landed),
     * false when this is not a server-side meteor.
     */
    public static boolean handle(MeteorProjectileEntity m) {
        if (!(m.level() instanceof ServerLevel level)) {
            return false;
        }
        // Step 0: owner and the last laser roll, before the kitty is discarded (the flight is looked up by entity).
        KittyShower owner = KittyShowers.ownerOf(level, m);
        if (KittyInterceptor.interceptAtImpact(level, m)) {
            return true;
        }

        // Step 1: flight line, settings, one claim guard for the whole landing.
        Vec3 from = m.position();
        Vec3 motion = m.getDeltaMovement();
        double speed = motion.length();
        Vec3 dir = speed < 1.0E-4D ? new Vec3(0.0D, -1.0D, 0.0D) : motion.scale(1.0D / speed);
        boolean griefing = OhMyMeteors.CONFIG.meteorBehaviourSection.meteor_griefing;
        KittyClaimGuard guard = KittyClaimGuard.of(level);
        RandomSource random = level.getRandom();
        m.discard();

        // Step 2: tunnel.
        KittyTunnel.Result tunnel = griefing ? KittyTunnel.dig(level, from, dir, speed, guard) : KittyTunnel.Result.inPlace(from);
        BlockPos end = tunnel.end();

        boolean catPlaced = false;
        // A claim that refuses the end cell protects the landing: no blast, no capsule, no cat (OMM does the same
        // for a meteor inside a protected claim). The kitty still counts as landed.
        if (guard.allows(end)) {
            // Step 3: blast with fire off.
            Explosion explosion = blast(level, m, Vec3.atCenterOf(end), griefing, guard);

            // Step 4: capsule and cat. The cat roll is the config chance (0 never, 1 always) and the per-shower cap.
            boolean catRoll = random.nextDouble() < Config.KITTY_CAT_CHANCE.get() && KittyShowers.catAllowed(owner);
            boolean capsuleBuilt = false;
            boolean capsuleTried = griefing && tunnel.grounded() && Config.KITTY_CAPSULE_ENABLED.get();
            if (capsuleTried) {
                KittyCapsule.Result capsule = KittyCapsule.build(level, end, random, catRoll, guard);
                capsuleBuilt = capsule.written() > 0;
                catPlaced = capsule.cat() != null;
            } else if (catRoll) {
                // No capsule (griefing off, a mid-air hit or the capsule switched off): the cat is placed loose.
                catPlaced = KittyCapsule.placeCat(level, end, random) != null;
            }

            // Step 5: fire pass (never inside the capsule).
            if (griefing) {
                placeFires(level, explosion, capsuleBuilt ? end : null, random, guard);
            }
            pop(level, end);
        }

        // Step 6: count the landing for the shower that owns this flight.
        KittyShowers.onKittyLanded(owner, m, catPlaced);
        return true;
    }

    // ---- the blast ----

    /** As {@link #blast(ServerLevel, Entity, Vec3, boolean, KittyClaimGuard)} with a fresh claim guard (for tests). */
    public static Explosion blast(ServerLevel level, @Nullable Entity source, Vec3 at, boolean griefing) {
        return blast(level, source, at, griefing, KittyClaimGuard.of(level));
    }

    /**
     * The kitty blast: vanilla's own explosion with the kitty power (4.0, the strength of TNT), fire off and
     * block interaction BLOCK. Entity damage, knockback, sound and particles depend only on the power, so they are
     * the same as TNT; the {@link #calculator} makes the hole smaller. BLOCK (not TNT) means dropped items follow
     * the blockExplosionDropDecay game rule, so up to 70 blasts do not flood the area with items. The fire comes
     * from {@link #placeFires}, never from the explosion.
     */
    public static Explosion blast(ServerLevel level, @Nullable Entity source, Vec3 at, boolean griefing, KittyClaimGuard guard) {
        float power = Config.KITTY_BLAST_POWER.get().floatValue();
        DamageSource damage = source == null ? null : source.damageSources().explosion(source, source);
        // Drops spawn inside explode() (finalizeExplosion); KittyBlastDrops thins them out while this flag is up.
        KittyBlastDrops.begin();
        try {
            return level.explode(source, damage, calculator(griefing, guard), at, power, false, Level.ExplosionInteraction.BLOCK);
        } finally {
            KittyBlastDrops.end();
        }
    }

    /**
     * The block-resistance rule that makes the kitty hole smaller than a TNT hole of the same power. The share of
     * blocks that break is the config blockDamageScale (default 0.6, about 60% of the blocks plain TNT breaks).
     *
     * How it works: vanilla charges each 0.3-block step of an explosion ray 0.225, plus (resistance + 0.3) x 0.3
     * when the calculator returns a resistance. To make every ray reach about k times as far, every step must cost
     * 1/k as much, see {@link #scaledResistance}. The volume of a hole goes roughly with the cube of its reach, so
     * k = cbrt(blockDamageScale). Rays are discrete steps over whole cells, so the result is "about" 60%, not exact.
     *
     * Bedrock resistance (no block damage at all) is returned when griefing is off, when blockDamageScale is 0, for
     * blocks tagged ohmymeteors:meteor_explosion_safe, and for cells the claim guard refuses (so the blast cannot
     * reach into a neighbouring claim). The config is read once per blast.
     */
    public static ExplosionDamageCalculator calculator(boolean griefing, KittyClaimGuard guard) {
        double scale = Mth.clamp(Config.KITTY_BLAST_BLOCK_DAMAGE_SCALE.get(), 0.0D, 1.0D);
        boolean noBlockDamage = !griefing || scale <= 0.001D;
        double k = Math.cbrt(scale);
        float bedrock = Blocks.BEDROCK.getExplosionResistance();
        boolean claims = guard != null && guard.active();
        return new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
                                                               BlockState state, FluidState fluid) {
                if (noBlockDamage || state.is(OhMyMeteors.METEOR_EXPLOSION_SAFE)) {
                    return Optional.of(bedrock);
                }
                if (claims && !guard.allows(pos)) {
                    return Optional.of(bedrock);
                }
                Optional<Float> base = super.getBlockExplosionResistance(explosion, getter, pos, state, fluid);
                if (k >= 1.0D) {
                    return base;
                }
                return Optional.of(scaledResistance(base.isPresent(), base.orElse(0.0F), k));
            }
        };
    }

    /**
     * The pure formula behind {@link #calculator}. A cell with a base resistance r (present = true) gets
     * (r + 1.05) / k - 1.05; an empty cell (air with no fluid) gets 0.75 / k - 1.05, a present value that charges the
     * ray 0.225 / k instead of 0.225. With k = 1 a present base comes back unchanged.
     */
    public static float scaledResistance(boolean present, float base, double k) {
        if (present) {
            if (k >= 1.0D) {
                return base;
            }
            return (float) ((base + 1.05D) / k - 1.05D);
        }
        return (float) (0.75D / k - 1.05D);
    }

    // ---- fire ----

    /**
     * The kitty's own, smaller fire pass (the explosion itself never makes fire). Over the cells the explosion
     * blew, each one catches fire with the config blastFireChance (default 0.1; vanilla uses about 0.33), by
     * vanilla's own rule: the cell is still air and the block below is solid. Cells within 2 blocks (Chebyshev) of
     * the capsule centre are skipped, so the capsule and its cat never burn, and so are cells the claim guard
     * refuses. Returns how many fires were placed.
     *
     * This relies on the explosion keeping its list of blown cells for block-breaking interactions (vanilla only
     * clears it when an explosion does not touch blocks). The live test kittyFirePassRules covers that.
     */
    public static int placeFires(ServerLevel level, Explosion ex, @Nullable BlockPos capsuleCentre, RandomSource r, KittyClaimGuard guard) {
        if (!Config.KITTY_BLAST_FIRE.get()) {
            return 0;
        }
        double chance = Config.KITTY_BLAST_FIRE_CHANCE.get();
        if (chance <= 0.0D) {
            return 0;
        }
        int placed = 0;
        for (BlockPos pos : ex.getToBlow()) {
            if (r.nextDouble() >= chance) {
                continue;
            }
            if (capsuleCentre != null
                    && Math.abs(pos.getX() - capsuleCentre.getX()) <= 2
                    && Math.abs(pos.getY() - capsuleCentre.getY()) <= 2
                    && Math.abs(pos.getZ() - capsuleCentre.getZ()) <= 2) {
                continue;
            }
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isSolidRender(level, below)) {
                continue;
            }
            if (!guard.allows(pos)) {
                continue;
            }
            level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            placed++;
        }
        return placed;
    }

    // ---- particles ----

    /** A small Veyrite pop at the landing, sent to players within 96 blocks with the long-distance flag. */
    private static void pop(ServerLevel level, BlockPos end) {
        try {
            double x = end.getX() + 0.5D;
            double y = end.getY() + 0.5D;
            double z = end.getZ() + 0.5D;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, y, z) > POP_RANGE * POP_RANGE) {
                    continue;
                }
                level.sendParticles(player, ModParticles.VEYRITE_SPARK.get(), true, x, y, z, 10, 0.5D, 0.5D, 0.5D, 0.1D);
                level.sendParticles(player, ModParticles.VEYRITE_SMOKE.get(), true, x, y, z, 6, 0.5D, 0.5D, 0.5D, 0.02D);
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedPopFailure) {
                loggedPopFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The Komit Kitty landing particles failed (this is logged once).", ex);
            }
        }
    }
}
