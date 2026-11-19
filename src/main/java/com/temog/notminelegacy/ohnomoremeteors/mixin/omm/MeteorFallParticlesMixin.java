package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVisuals;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-variant fall particles (2026-11-19). OMM's three fall animations (fancy / less / minimal) add a flash and a
 * small explosion puff on the client, and send flame, smoke, lava and campfire smoke from the server.
 *
 * Client side: the flash and puff calls are redirected here (they are made directly in these methods).
 * Server side: OMM sends from inside a lambda (players().forEach(p -> world.sendParticles(...))), a separate
 * synthetic method, so these methods only mark which variant is animating (MeteorVisuals.ANIMATING, per thread)
 * and ServerLevelFallParticleMixin swaps the particle inside sendParticles. Plain meteors are untouched.
 */
@Mixin(MeteorProjectileEntity.class)
public abstract class MeteorFallParticlesMixin {

    @Inject(method = {"particleAnimation(DDD)V", "lessParticleAnimation(DDD)V", "minimalParticleAnimation(DDD)V"},
            at = @At("HEAD"), require = 0, remap = false)
    private void onmm$beginAnimation(double x, double y, double z, CallbackInfo ci) {
        MeteorVariant variant = null;
        try {
            variant = MeteorVariant.fromEntity((MeteorProjectileEntity) (Object) this);
        } catch (RuntimeException ignored) {
            // plain look
        }
        if (variant == null) {
            MeteorVisuals.ANIMATING.remove();
        } else {
            MeteorVisuals.ANIMATING.set(variant);
        }
    }

    @Inject(method = {"particleAnimation(DDD)V", "lessParticleAnimation(DDD)V", "minimalParticleAnimation(DDD)V"},
            at = @At("RETURN"), require = 0, remap = false)
    private void onmm$endAnimation(double x, double y, double z, CallbackInfo ci) {
        MeteorVisuals.ANIMATING.remove();
    }

    @Redirect(
            method = {"particleAnimation(DDD)V", "lessParticleAnimation(DDD)V", "minimalParticleAnimation(DDD)V"},
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"),
            require = 0, remap = false)
    private void onmm$variantHeadParticle(Level level, ParticleOptions options, boolean force,
                                          double x, double y, double z, double xd, double yd, double zd) {
        MeteorVariant variant = null;
        try {
            variant = MeteorVariant.fromEntity((MeteorProjectileEntity) (Object) this);
        } catch (RuntimeException ignored) {
            // fall through to OMM's own particle
        }
        if (variant == null) {
            level.addParticle(options, force, x, y, z, xd, yd, zd);
        } else if (options.getType() == ParticleTypes.EXPLOSION) {
            level.addParticle(MeteorVisuals.headPuff(variant), force, x, y, z, xd, yd, zd);
        } else if (options instanceof me.emafire003.dev.ohmymeteors.particles.meteor_flash.FlashScaleParticleOptions flash) {
            level.addParticle(MeteorVisuals.flash(variant), force, x, y, z, flash.getScale(), 0.0D, 0.0D);
        } else {
            level.addParticle(options, force, x, y, z, xd, yd, zd);
        }
    }
}
