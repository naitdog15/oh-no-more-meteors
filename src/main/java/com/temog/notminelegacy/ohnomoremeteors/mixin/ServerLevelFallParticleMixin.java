package com.temog.notminelegacy.ohnomoremeteors.mixin;

import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVisuals;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Server half of the per-variant fall particles (see MeteorFallParticlesMixin): while a fire / ice / space meteor's
 * fall animation runs on this thread, the flame / smoke / lava / campfire smoke it sends to each player is swapped
 * for the variant's own particle. Any other sendParticles call (nothing animating) is untouched.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelFallParticleMixin {

    @ModifyVariable(
            method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private ParticleOptions onmm$variantFallParticle(ParticleOptions original) {
        MeteorVariant variant = MeteorVisuals.ANIMATING.get();
        // Another mod's look (ForeignVariantLooks), marked beside ANIMATING by MeteorFallParticlesMixin.
        com.temog.notminelegacy.ohnomoremeteors.meteor.ForeignLook foreign = variant == null ? MeteorVisuals.ANIMATING_FOREIGN.get() : null;
        if (foreign != null) {
            try {
                ParticleOptions swapped = MeteorVisuals.fallParticle(foreign, original);
                if (swapped != original) {
                    MeteorVisuals.SWAPPED.incrementAndGet();
                }
                return swapped;
            } catch (RuntimeException ex) {
                return original;
            }
        }
        if (variant == null) {
            return original;
        }
        try {
            ParticleOptions swapped = MeteorVisuals.fallParticle(variant, original);
            if (swapped != original) {
                MeteorVisuals.SWAPPED.incrementAndGet();
            }
            return swapped;
        } catch (RuntimeException ex) {
            return original;
        }
    }
}
