package com.temog.notminelegacy.ohnomoremeteors.mixin;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.DistantImpactBroadcast;
import com.temog.notminelegacy.ohnomoremeteors.meteor.FluidLanding;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorImpact;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KittyImpact;
import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KomitKitty;
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
 * Komit Kitty meteors (2026-11-20) are handled FIRST at the head of explodeMeteor(): KittyImpact lands them (tunnel,
 * scaled blast, capsule) and the method is always cancelled, so a kitty never gets OMM's blast or structure, the
 * distant impact broadcast, or the plain-meteor cleanup (cancelling at the head also skips the RETURN cleanup).
 * A second inject cancels OMM's per-tick loadChunk() for kitties, so they never take chunk tickets.
 *
 * remap = false everywhere: the target is an OMM method, not Minecraft code, so there is nothing for the
 * refmap to look up.
 */
@Mixin(value = MeteorProjectileEntity.class, remap = false)
public abstract class MeteorProjectileEntityMixin {

    /** OMM's "already exploded" guard flag (protected there). Set it exactly as OMM would have. */
    @Shadow
    protected boolean exploded;

    /** OMM's embed state after the first block contact (protected there). Reset when an ice sheet is shattered so the meteor flies on. */
    @Shadow
    protected net.minecraft.world.phys.Vec3 explosionPos;

    @Shadow
    protected int travelledBlocksAfterHit;

    /** Plain meteor cleanup: what the impact area held before OMM's own impact (null when not needed). */
    @org.spongepowered.asm.mixin.Unique
    private com.temog.notminelegacy.ohnomoremeteors.meteor.PlainMeteorCleanup.Before onmm$plainBefore;

    @Inject(method = "explodeMeteor()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onmm$replaceImpact(CallbackInfo ci) {
        MeteorProjectileEntity self = (MeteorProjectileEntity) (Object) this;
        // explodeMeteor also runs on the client (there it only discards the entity); leave that alone.
        if (self.level().isClientSide()) {
            return;
        }
        // Komit Kitty: its own landing, first of all. Its own exploded guard, and the method is always cancelled.
        boolean kitty = false;
        try {
            kitty = KomitKitty.isKitty(self);
        } catch (RuntimeException | LinkageError ex) {
            KittyImpact.warnFailureOnce("claim check", ex);
        }
        if (kitty) {
            if (!this.exploded) {
                this.exploded = true;
                try {
                    KittyImpact.handle(self);
                } catch (RuntimeException | LinkageError ex) {
                    KittyImpact.warnFailureOnce("landing", ex);
                    self.discard();
                }
            }
            ci.cancel();
            return;
        }
        // Ice over fluid (2026-11-21): a claimed meteor stopped by packed / blue / frosted ice over water or lava shatters an
        // ice disc as big as itself and flies on; OMM's embed state is reset so its next hit starts afresh.
        if (!this.exploded && self.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            try {
                if (FluidLanding.shatterIceAhead(serverLevel, self)) {
                    this.explosionPos = null;
                    this.travelledBlocksAfterHit = 0;
                    ci.cancel();
                    return;
                }
            } catch (RuntimeException | LinkageError ex) {
                FluidLanding.warnFailureOnce("ice shatter", ex);
            }
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
            // Building Bridges' Sky Stone meteors are not landed here, but the ice sheet they bypassed is shattered like ours.
            if (FluidLanding.isAe2Claim(self) && self.level() instanceof net.minecraft.server.level.ServerLevel foreignLevel) {
                try {
                    FluidLanding.shatterForForeign(foreignLevel, self);
                } catch (RuntimeException | LinkageError ex) {
                    FluidLanding.warnFailureOnce("foreign ice shatter", ex);
                }
            }
            if (MeteorImpact.handle(self)) {
                ci.cancel();
            } else {
                // OMM runs its own impact next: note the area so the plain-meteor cleanup can undo icy / lava / loot blocks.
                this.onmm$plainBefore = com.temog.notminelegacy.ohnomoremeteors.meteor.PlainMeteorCleanup.capture(self);
            }
        } catch (RuntimeException ex) {
            this.onmm$plainBefore = null;
            // Never cancel on an error before the impact was taken over: OMM then runs its normal impact.
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor impact hook failed; OMM keeps its normal impact.", ex);
        }
    }
    /**
     * Komit Kitty: OMM's per-tick loadChunk() adds chunk tickets while meteors_load_chunks is on. A kitty never
     * takes one: the call is cancelled for server-side kitties. Fails open (OMM keeps its tickets on an error).
     */
    @Inject(method = "loadChunk()V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void onmm$kittyNoTickets(CallbackInfo ci) {
        try {
            MeteorProjectileEntity self = (MeteorProjectileEntity) (Object) this;
            if (!self.level().isClientSide() && KomitKitty.isKitty(self)) {
                KomitKitty.TICKETS_SKIPPED.incrementAndGet();
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError ex) {
            KittyImpact.warnFailureOnce("chunk ticket skip", ex);
        }
    }

    /** After OMM's own impact (blast + structure) of a plain meteor: swap the blocks it may not leave. */
    @Inject(method = "explodeMeteor()V", at = @At("RETURN"), remap = false)
    private void onmm$cleanPlainImpact(CallbackInfo ci) {
        com.temog.notminelegacy.ohnomoremeteors.meteor.PlainMeteorCleanup.Before before = this.onmm$plainBefore;
        this.onmm$plainBefore = null;
        if (before != null) {
            com.temog.notminelegacy.ohnomoremeteors.meteor.PlainMeteorCleanup.apply(before);
        }
    }
}
