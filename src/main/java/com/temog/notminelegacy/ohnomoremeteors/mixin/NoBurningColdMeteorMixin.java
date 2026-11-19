package com.temog.notminelegacy.ohnomoremeteors.mixin;

import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An Oh My, Meteors! meteor sets itself on fire every tick (AbstractHurtingProjectile#shouldBurn is true), which
 * draws vanilla's burning-entity fire around it. That suits Cindrite and plain meteors, but not the ice (Rimerite)
 * or space (Veyrite) meteor: those do not burn, so their own body texture and particles show (2026-11-19).
 */
@Mixin(AbstractHurtingProjectile.class)
public abstract class NoBurningColdMeteorMixin {

    @Inject(method = "shouldBurn", at = @At("HEAD"), cancellable = true, require = 0)
    private void onmm$coldMeteorsDoNotBurn(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MeteorProjectileEntity meteor)) {
            return;
        }
        try {
            MeteorVariant variant = MeteorVariant.fromEntity(meteor);
            if (variant == MeteorVariant.RIMERITE || variant == MeteorVariant.VEYRITE) {
                cir.setReturnValue(false);
            }
        } catch (RuntimeException ignored) {
            // keep OMM's behaviour
        }
    }
}
