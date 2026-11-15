package com.temog.notminelegacy.ohnomoremeteors.mixin;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.DistantImpactBroadcast;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorImpact;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Impact hook of the fire and ice meteors. Oh My, Meteors! sends every block or entity impact through
 * {@code explodeMeteor()}, which first discards the meteor and then blasts and places a rock structure.
 * This injector looks at the very start of that method. If the meteor carries one of this mod's claims
 * (omm_variant = ohnomoremeteors:cindrite or :rimerite) and {@link MeteorImpact#handle} takes over, the
 * original method is cancelled so OMM does not blast and build a second time. Any other meteor passes
 * through untouched. Lasers never call explodeMeteor, so they never make a crater.
 *
 * remap = false everywhere: the target is an OMM method, not Minecraft code, so there is nothing for the
 * refmap to look up.
 */
@Mixin(value = MeteorProjectileEntity.class, remap = false)
public abstract class MeteorProjectileEntityMixin {

    /** OMM's "already exploded" guard flag (protected there). Set it exactly as OMM would have. */
    @Shadow
    protected boolean exploded;

    @Inject(method = "explodeMeteor()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onmm$replaceImpact(CallbackInfo ci) {
        MeteorProjectileEntity self = (MeteorProjectileEntity) (Object) this;
        // explodeMeteor also runs on the client (there it only discards the entity); leave that alone.
        if (self.level().isClientSide()) {
            return;
        }
        // Distant crash sound for every meteor (ours or not), once: a repeat call finds exploded already set.
        if (!this.exploded) {
            try {
                DistantImpactBroadcast.onImpact(self);
            } catch (RuntimeException ex) {
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The distant impact sound failed.", ex);
            }
        }
        try {
            // One of our meteors that already exploded must never explode again (a second hit before the
            // entity is gone would otherwise repeat the blast and the crater).
            if (this.exploded && MeteorVariant.fromEntity(self) != null) {
                ci.cancel();
                return;
            }
            // Mark the meteor as exploded before anything is detonated, exactly as OMM does right after discard().
            // If handle() declines, OMM's own method runs next and sets the same flag anyway.
            this.exploded = true;
            if (MeteorImpact.handle(self)) {
                ci.cancel();
            }
        } catch (RuntimeException ex) {
            // Never cancel on an error before the impact was taken over: OMM then runs its normal impact.
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor impact hook failed; OMM keeps its normal impact.", ex);
        }
    }
}
