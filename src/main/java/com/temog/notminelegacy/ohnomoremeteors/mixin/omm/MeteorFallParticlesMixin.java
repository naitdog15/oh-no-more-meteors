package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVisuals;
import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KittyTrail;
import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KomitKitty;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 *
 * Komit Kitties (2026-11-20): the first three injectors below run before the others. On the server they cancel OMM's
 * fall animation for a kitty and send the cheaper KittyTrail instead (a few Veyrite particles, only to nearby players).
 * On the client a kitty carries the Veyrite claim, so OMM's client part and the redirect below work unchanged.
 */
@Mixin(MeteorProjectileEntity.class)
public abstract class MeteorFallParticlesMixin {

    @Inject(method = "particleAnimation(DDD)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onmm$kittyTrailFancy(double x, double y, double z, CallbackInfo ci) {
        onmm$kittyTrail(x, y, z, KittyTrail.MODE_FANCY, ci);
    }

    @Inject(method = "lessParticleAnimation(DDD)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onmm$kittyTrailLess(double x, double y, double z, CallbackInfo ci) {
        onmm$kittyTrail(x, y, z, KittyTrail.MODE_LESS, ci);
    }

    @Inject(method = "minimalParticleAnimation(DDD)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onmm$kittyTrailMinimal(double x, double y, double z, CallbackInfo ci) {
        onmm$kittyTrail(x, y, z, KittyTrail.MODE_MINIMAL, ci);
    }

    /** Server side only: a kitty skips OMM's fall animation and gets KittyTrail. Fail-open: on an error OMM's animation runs. */
    @Unique
    private void onmm$kittyTrail(double x, double y, double z, int mode, CallbackInfo ci) {
        try {
            MeteorProjectileEntity self = (MeteorProjectileEntity) (Object) this;
            if (!self.level().isClientSide() && KomitKitty.isKitty(self)) {
                MeteorVisuals.ANIMATING.remove();
                MeteorVisuals.ANIMATING_FOREIGN.remove();
                KittyTrail.emit(self, x, y, z, mode);
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError ex) {
            KittyTrail.warnHookFailure(ex);
        }
    }

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
        // Another mod's look (only when no variant of ours animates): marked beside it, cleared at the same boundaries.
        com.temog.notminelegacy.ohnomoremeteors.meteor.ForeignLook foreign = null;
        if (variant == null) {
            try {
                foreign = MeteorVisuals.foreignLookOf((MeteorProjectileEntity) (Object) this);
            } catch (RuntimeException ignored) {
                // plain look
            }
        }
        if (foreign == null) {
            MeteorVisuals.ANIMATING_FOREIGN.remove();
        } else {
            MeteorVisuals.ANIMATING_FOREIGN.set(foreign);
        }
    }

    @Inject(method = {"particleAnimation(DDD)V", "lessParticleAnimation(DDD)V", "minimalParticleAnimation(DDD)V"},
            at = @At("RETURN"), require = 0, remap = false)
    private void onmm$endAnimation(double x, double y, double z, CallbackInfo ci) {
        MeteorVisuals.ANIMATING.remove();
        MeteorVisuals.ANIMATING_FOREIGN.remove();
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
            // Another mod's look: its burst for the puff, its flash for the flash; anything else falls to OMM's below.
            com.temog.notminelegacy.ohnomoremeteors.meteor.ForeignLook foreign = null;
            try {
                foreign = MeteorVisuals.foreignLookOf((MeteorProjectileEntity) (Object) this);
            } catch (RuntimeException ignored) {
                // fall through to OMM's own particle
            }
            if (foreign != null && foreign.burst() != null && options.getType() == ParticleTypes.EXPLOSION) {
                level.addParticle(foreign.burst(), force, x, y, z, xd, yd, zd);
                return;
            }
            if (foreign != null && foreign.flash() != null
                    && options instanceof me.emafire003.dev.ohmymeteors.particles.meteor_flash.FlashScaleParticleOptions flash) {
                level.addParticle(foreign.flash(), force, x, y, z, flash.getScale(), 0.0D, 0.0D);
                return;
            }
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
