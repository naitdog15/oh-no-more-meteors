package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

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
 * Fire uses OMM's own detonateSimple(). Ice and space (Veyrite) use the same blast power without fire. Both respect OMM's
 * meteor_griefing setting and the ohmymeteors:meteor_explosion_safe tag.
 */
public final class MeteorImpact {

    /** Set after the first "could not prepare" warning so a broken setup cannot flood the log. */
    private static boolean loggedPrepareFailure;

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
        try {
            centre = BlockPos.containing(meteor.position());
            // Submerged impact: plain OMM behaviour, no crater, no special blast.
            if (isSubmerged(level, centre)) {
                return false;
            }
            griefing = OhMyMeteors.CONFIG.meteorBehaviourSection.meteor_griefing;
            snapshot = griefing ? CraterBuilder.capture(level, centre, CraterBuilder.halfWidth(meteor.getSize())) : null;
        } catch (RuntimeException ex) {
            if (!loggedPrepareFailure) {
                loggedPrepareFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not prepare the {} impact; OMM keeps its normal impact (this is logged once).", variant, ex);
            }
            return false;
        }

        // From here on this mod owns the impact. OMM discards first so the meteor cannot trigger twice.
        meteor.discard();
        try {
            if (variant == MeteorVariant.CINDRITE) {
                meteor.detonateSimple();
            } else {
                iceBlast(level, meteor, griefing);
            }
            CraterBuilder.build(level, snapshot, variant);
            if (variant == MeteorVariant.VEYRITE) {
                // A brief violet haze over the crater (Veyrite aftermath).
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.getX() + 0.5D, centre.getY() + 1.0D, centre.getZ() + 0.5D,
                        80, 2.5D, 1.5D, 2.5D, 0.05D);
            }
        } catch (RuntimeException ex) {
            OhNoMoreMeteors.LOGGER.error("[ohnomoremeteors] The {} impact failed part way.", variant, ex);
        }
        return true;
    }

    /** Submerged means the impact centre block itself holds water (water above a dry centre does not count). */
    private static boolean isSubmerged(ServerLevel level, BlockPos centre) {
        return level.getFluidState(centre).is(FluidTags.WATER);
    }

    /**
     * The ice meteor's blast: the same power OMM would use, but no fire. Sound and the big explosion
     * particle follow OMM's notification settings.
     */
    private static void iceBlast(ServerLevel level, MeteorProjectileEntity meteor, boolean griefing) {
        var behaviour = OhMyMeteors.CONFIG.meteorBehaviourSection;
        int size = meteor.getSize();

        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
                                                               BlockState state, FluidState fluid) {
                if (!griefing || state.is(OhMyMeteors.METEOR_EXPLOSION_SAFE)) {
                    return Optional.of(Blocks.BEDROCK.getExplosionResistance());
                }
                return super.getBlockExplosionResistance(explosion, getter, pos, state, fluid);
            }
        };

        DamageSource source = meteor.damageSources().explosion(meteor, meteor);
        if (behaviour.use_better_explosions) {
            // Same size adjustment OMM applies to its sphere explosions.
            int adjust = size > 10 ? 3 : (size > 3 ? 2 : 0);
            float power = (size + behaviour.explosion_power_modifier + adjust) * behaviour.explosion_power_multiplier;
            ExplosionUtils.createExplosion(level, meteor, source, calculator, meteor.position(), power, false, Level.ExplosionInteraction.TNT);
        } else {
            float power = (size + behaviour.explosion_power_modifier) * behaviour.explosion_power_multiplier;
            level.explode(meteor, source, calculator, meteor.position(), power, false, Level.ExplosionInteraction.TNT);
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
