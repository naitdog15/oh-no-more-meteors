package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import me.emafire003.dev.ohmymeteors.util.ExplosionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

import java.util.Optional;

/**
 * What happens when a fire or ice meteor lands. Called from the mixin at the head of OMM's
 * {@code explodeMeteor()}; returns true when this mod handled the impact (the mixin then cancels OMM's own
 * impact so it does not blast and build a structure a second time).
 *
 * Steps for a claimed meteor on a server level:
 *  1. Decide (nothing is changed yet): a meteor that is not ours, a switched-off variant or a submerged
 *     impact returns false and OMM carries on exactly as normal.
 *  2. Photograph the cube around the impact, discard the meteor (as OMM does first), blast, build the crater.
 *
 * Every claimed variant uses this mod's own blast (ownBlast): the same power formula for all of them, with fire
 * only for Cindrite (and only when OMM's spawn_fire_with_meteor is on). All respect OMM's meteor_griefing setting
 * and the ohmymeteors:meteor_explosion_safe tag.
 */
public final class MeteorImpact {

    /** Set after the first "could not prepare" warning so a broken setup cannot flood the log. */
    private static boolean loggedPrepareFailure;
    private static boolean loggedAftermathFailure;
    /** Most living entities set on fire / frozen per impact. */
    private static final int MAX_AFTERMATH_ENTITIES = 64;

    private MeteorImpact() {
    }

    public static boolean handle(MeteorProjectileEntity meteor) {
        if (!(meteor.level() instanceof ServerLevel level)) {
            return false;
        }
        MeteorVariant variant = MeteorVariant.fromEntity(meteor);
        if (variant == null || !variant.isEnabled()) {
            return false;
        }

        BlockPos centre;
        boolean griefing;
        CraterBuilder.Snapshot snapshot;
        float power;
        ImpactFormula.Impact impact;
        try {
            centre = BlockPos.containing(meteor.position());
            // Fluid landings (2026-11-21): an impact in or over water or lava is this mod's own (slowed by the fluid).
            FluidLanding.Wet wet = FluidLanding.detect(level, meteor, centre);
            if (wet != null && wet.wet()) {
                return handleWet(level, meteor, variant, wet);
            }
            // Submerged impact with fluidLandings off: plain OMM behaviour, no crater, no special blast.
            if (wet == null && isSubmerged(level, centre)) {
                return false;
            }
            griefing = OhMyMeteors.CONFIG.meteorBehaviourSection.meteor_griefing;
            // One formula for the whole impact: blast, crater area, ore and rock amounts (see ImpactFormula).
            impact = ImpactFormula.of(meteor, variant);
            int halfWidth = Config.CRATER_STYLE.get() == Config.CraterStyle.LINED
                    ? CraterBuilder.halfWidth(meteor.getSize(), variant) : impact.halfWidth();
            snapshot = griefing ? CraterBuilder.capture(level, centre, halfWidth) : null;
            power = impact.power();
        } catch (RuntimeException ex) {
            if (!loggedPrepareFailure) {
                loggedPrepareFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not prepare the {} impact; OMM keeps its normal impact (this is logged once).", variant, ex);
            }
            return false;
        }

        // From here on this mod owns the impact. OMM discards first so the meteor cannot trigger twice.
        Vec3 approach = meteor.getDeltaMovement(); // the last flight direction, for the crater carve
        meteor.discard();
        try {
            ownBlast(level, meteor, griefing, variant, power);
            blastAftermath(level, meteor, variant, power);
            int written = CraterBuilder.buildDry(level, snapshot, variant, impact, centre, approach);
            postBuilt(level, meteor, centre, variant, written);
            burst(level, centre, variant, meteor.getSize(), snapshot == null ? impact.halfWidth() : (snapshot.side() - 1) / 2);
            if (Config.LOG_IMPACTS.get()) {
                OhNoMoreMeteors.LOGGER.info("[ohnomoremeteors] {} impact at {}: size {}, speed roll {}, energy {}, blast {}, crater half-width {}, ore {} / lump {} ({} blocks written), heard up to {} blocks at loudness {} [{}]",
                        variant, centre.toShortString(), impact.size(), String.format("%.2f", impact.speedRoll()),
                        String.format("%.1f", impact.energy()), String.format("%.1f", impact.power()), impact.halfWidth(),
                        impact.resources(), impact.lumpBlocks(), written, Math.round(impact.hearingRange()),
                        String.format("%.2f", impact.loudness()), CraterBuilder.lastCoreStats);
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedImpactFailure) {
                loggedImpactFailure = true;
                OhNoMoreMeteors.LOGGER.error("[ohnomoremeteors] The {} impact failed part way (this is logged once).", variant, ex);
            }
        }
        return true;
    }

    /**
     * A landing in water or lava, or through shattered ice over them (2026-11-21). The fluid has slowed the meteor
     * (FluidLanding.Wet): BLAST = a vanilla blast at the floor, power scaled by the speed left, fluid cells neither
     * stop the rays nor get removed; SETTLE = no blast and no destruction. Then the lump is built on the floor
     * (CraterBuilder wet mode). Returns false (OMM keeps its impact) only when the preparation fails.
     */
    private static boolean handleWet(ServerLevel level, MeteorProjectileEntity meteor, MeteorVariant variant, FluidLanding.Wet found) {
        FluidLanding.Wet wet;
        boolean griefing;
        ImpactFormula.Impact impact;
        BlockPos floor;
        CraterBuilder.Snapshot snapshot = null;
        float power = 0.0F;
        try {
            wet = FluidLanding.resolveBypassedSheet(level, meteor, found);
            floor = wet.floorTop();
            griefing = OhMyMeteors.CONFIG.meteorBehaviourSection.meteor_griefing;
            impact = ImpactFormula.of(meteor, variant);
            if (wet.mode() == FluidLanding.Mode.BLAST) {
                power = (float) (impact.power() * wet.remaining() / wet.v0());
                snapshot = griefing ? CraterBuilder.capture(level, floor, impact.halfWidth()) : null;
            }
        } catch (RuntimeException ex) {
            FluidLanding.warnFailureOnce("wet landing preparation", ex);
            return false;
        }

        meteor.discard();
        try {
            if (wet.mode() == FluidLanding.Mode.BLAST) {
                ownBlast(level, meteor, griefing, variant, power, Vec3.atCenterOf(floor));
                blastAftermath(level, meteor, variant, power, Vec3.atCenterOf(floor));
            } else {
                meteor.announceSpawn();
            }
            // meteor_griefing=false: no block placement at all (the dry claimed path builds nothing either).
            int written = griefing ? CraterBuilder.build(level, snapshot, variant, impact, floor, wet) : 0;
            postBuilt(level, meteor, floor, variant, written);
            burst(level, floor, variant, meteor.getSize(), snapshot == null ? impact.halfWidth() : (snapshot.side() - 1) / 2);
            if (Config.LOG_IMPACTS.get()) {
                OhNoMoreMeteors.LOGGER.info("[ohnomoremeteors] {} impact at {}: size {}, speed roll {}, energy {}, blast {}, crater half-width {}, ore {} / lump {} ({} blocks written), heard up to {} blocks at loudness {} [{}] wet {} water {} lava {} ice {} speed {}/{}",
                        variant, floor.toShortString(), impact.size(), String.format("%.2f", impact.speedRoll()),
                        String.format("%.1f", impact.energy()), String.format("%.1f", power), impact.halfWidth(),
                        impact.resources(), impact.lumpBlocks(), written, Math.round(impact.hearingRange()),
                        String.format("%.2f", impact.loudness()), CraterBuilder.lastCoreStats,
                        wet.mode().name().toLowerCase(java.util.Locale.ROOT), wet.nWater(), wet.nLava(), wet.mIce(),
                        String.format("%.2f", wet.remaining()), String.format("%.2f", wet.v0()));
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedImpactFailure) {
                loggedImpactFailure = true;
                OhNoMoreMeteors.LOGGER.error("[ohnomoremeteors] The {} impact failed part way (this is logged once).", variant, ex);
            }
        }
        return true;
    }

    private static boolean loggedListenerFailure;

    /** Tells other mods the crater was built (CraterBuiltEvent). A failing listener never breaks the impact. */
    private static void postBuilt(ServerLevel level, MeteorProjectileEntity meteor, BlockPos centre, MeteorVariant variant, int written) {
        try {
            MinecraftForge.EVENT_BUS.post(new CraterBuiltEvent(level, meteor, centre, variant, written));
        } catch (RuntimeException ex) {
            if (!loggedListenerFailure) {
                loggedListenerFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] A CraterBuiltEvent listener failed (this is logged once).", ex);
            }
        }
    }

    /** Most particles one impact burst may send, whatever the size and density settings. */
    private static final int BURST_CAP = 160;
    private static boolean loggedBurstFailure;
    private static boolean loggedImpactFailure;

    /**
     * The impact burst: Cindrite and Rimerite throw their own particles, Veyrite keeps its violet haze.
     * Count = (12 + 2 * min(size, 20)) times the variant's burst density, capped at BURST_CAP. Fails open.
     */
    private static void burst(ServerLevel level, BlockPos centre, MeteorVariant variant, int size, int halfWidth) {
        try {
            if (!VariantTuning.burstOn(variant)) {
                return;
            }
            int base = variant == MeteorVariant.VEYRITE ? 80 : 12 + 2 * Math.min(size, 20);
            int count = Math.min(BURST_CAP, VariantTuning.densityCount(base, VariantTuning.burstDensity(variant)));
            if (count <= 0) {
                return;
            }
            double x = centre.getX() + 0.5D;
            double y = centre.getY() + 1.0D;
            double z = centre.getZ() + 0.5D;
            if (variant == MeteorVariant.VEYRITE) {
                // A brief violet haze over the crater (Veyrite aftermath).
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, count, 2.5D, 1.5D, 2.5D, 0.05D);
                return;
            }
            double hw = Math.max(1.0D, halfWidth);
            var type = variant == MeteorVariant.CINDRITE ? ModParticles.CINDRITE_BURST.get() : ModParticles.RIMERITE_BURST.get();
            level.sendParticles(type, x, y, z, count, hw * 0.4D, 1.0D, hw * 0.4D, 0.25D);
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedBurstFailure) {
                loggedBurstFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor impact particle burst failed; skipped (this is logged once).", ex);
            }
        }
    }

    /** Submerged means the impact centre block itself holds water (water above a dry centre does not count). */
    private static boolean isSubmerged(ServerLevel level, BlockPos centre) {
        return level.getFluidState(centre).is(FluidTags.WATER);
    }

    /**
     * Sets living things inside the blast on fire (Cindrite) or freezes them (Rimerite). Radius = the blast power,
     * no line-of-sight check, at most MAX_AFTERMATH_ENTITIES. Fails open.
     */
    private static void blastAftermath(ServerLevel level, MeteorProjectileEntity meteor, MeteorVariant variant, float power) {
        blastAftermath(level, meteor, variant, power, meteor.position());
    }

    private static void blastAftermath(ServerLevel level, MeteorProjectileEntity meteor, MeteorVariant variant, float power, Vec3 origin) {
        try {
            int fireSeconds = variant == MeteorVariant.CINDRITE ? Config.CINDRITE_V_BLAST_FIRE_SECONDS.get() : 0;
            int freezeTicks = variant == MeteorVariant.RIMERITE ? Config.RIMERITE_V_BLAST_FREEZE_TICKS.get() : 0;
            if ((fireSeconds <= 0 && freezeTicks <= 0) || !(power > 0.0F)) {
                return;
            }
            double r = Math.min(power, 64.0F);
            AABB box = new AABB(origin, origin).inflate(r);
            int handled = 0;
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !e.isSpectator())) {
                if (handled++ >= MAX_AFTERMATH_ENTITIES) {
                    break;
                }
                if (fireSeconds > 0 && !living.fireImmune()) {
                    living.setSecondsOnFire(fireSeconds);
                }
                if (freezeTicks > 0 && living.canFreeze()) {
                    int target = Math.min(living.getTicksRequiredToFreeze() + freezeTicks, living.getTicksFrozen() + freezeTicks);
                    living.setTicksFrozen(Math.max(living.getTicksFrozen(), target));
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedAftermathFailure) {
                loggedAftermathFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor blast fire/freeze step failed; skipped (this is logged once).", ex);
            }
        }
    }

    /**
     * This mod's own blast for every claimed variant, at the power worked out while preparing. Fire only for
     * Cindrite (when OMM's spawn_fire_with_meteor is on). Sound and the big explosion particle follow OMM's
     * notification settings.
     */
    private static void ownBlast(ServerLevel level, MeteorProjectileEntity meteor, boolean griefing, MeteorVariant variant, float power) {
        ownBlast(level, meteor, griefing, variant, power, null);
    }

    /**
     * {@code wetOrigin} non-null = a wet blast at the fluid floor: always vanilla level.explode (it honours
     * shouldBlockExplode), no fire, pure fluid cells get wetFluidResistance and are never removed. Null = the dry blast.
     */
    private static void ownBlast(ServerLevel level, MeteorProjectileEntity meteor, boolean griefing, MeteorVariant variant, float power, Vec3 wetOrigin) {
        var behaviour = OhMyMeteors.CONFIG.meteorBehaviourSection;
        boolean wet = wetOrigin != null;
        boolean fire = !wet && variant == MeteorVariant.CINDRITE && behaviour.spawn_fire_with_meteor;

        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
                                                               BlockState state, FluidState fluid) {
                if (!griefing || state.is(OhMyMeteors.METEOR_EXPLOSION_SAFE)) {
                    return Optional.of(Blocks.BEDROCK.getExplosionResistance());
                }
                if (wet && state.getBlock() instanceof LiquidBlock && FluidLanding.isWaterOrLava(state.getFluidState())) {
                    return Optional.of(Config.FL_WET_FLUID_RESISTANCE.get().floatValue());
                }
                return super.getBlockExplosionResistance(explosion, getter, pos, state, fluid);
            }

            @Override
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state, float power) {
                if (wet && state.getBlock() instanceof LiquidBlock && FluidLanding.isWaterOrLava(state.getFluidState())) {
                    return false; // the water or lava stays where it is
                }
                return super.shouldBlockExplode(explosion, getter, pos, state, power);
            }
        };

        DamageSource source = meteor.damageSources().explosion(meteor, meteor);
        if (behaviour.use_better_explosions && !wet) {
            ExplosionUtils.createExplosion(level, meteor, source, calculator, meteor.position(), power, fire, Level.ExplosionInteraction.TNT);
        } else {
            level.explode(meteor, source, calculator, wet ? wetOrigin : meteor.position(), power, fire, Level.ExplosionInteraction.TNT);
        }

        var notify = OhMyMeteors.CONFIG.notificationSection;
        for (ServerPlayer player : level.players()) {
            double dist = player.position().distanceTo(meteor.position());
            // The directional distant-impact sound (DistantImpactBroadcast) replaces OMM's flat far-away boom.
            boolean broadcast = Config.DISTANT_IMPACT_ENABLED.get();
            if (!broadcast && notify.global_explosion_sound && dist > 60) {
                player.playNotifySound(SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5F, 0.8F);
            } else if (!broadcast && notify.area_explosion_sound && dist < notify.area_explosion_sound_radius && dist > 60) {
                player.playNotifySound(SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5F, 0.8F);
            }
            level.sendParticles(player, ParticleTypes.EXPLOSION_EMITTER, true, meteor.getX(), meteor.getY(), meteor.getZ(),
                    1, 0.1, 0.1, 0.1, 0.1);
        }
        meteor.announceSpawn();
    }
}
