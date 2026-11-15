package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.fix.OmmFixes;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.blocks.basic_laser.BasicMeteorLaserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B1: OMM 1.5.0 BasicMeteorLaserBlock.getRadiusAreaCoverage() returns basic_laser_height (default 64) where it
 * should return basic_laser_area_radius (default 32). The Advanced laser has its own, correct static method
 * (a separate method that hides this one), so it is not touched here.
 *
 * Note for players: at OMM defaults this halves the basic laser's real coverage, from 64 to the 32 that the OMM
 * config says it is. Gated by ommFixes.fixBasicLaserRadius.
 *
 * remap = false: the target is an OMM method, not Minecraft code, so there is nothing for the refmap to look up.
 */
@Mixin(value = BasicMeteorLaserBlock.class, remap = false)
public abstract class BasicLaserRadiusMixin {

    // The target is a static method, so the handler has to be static too. It runs before the original body.
    @Inject(method = "getRadiusAreaCoverage()I", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void onmm$useAreaRadius(CallbackInfoReturnable<Integer> cir) {
        try {
            if (!Config.FIX_BASIC_LASER_RADIUS.get()) {
                return;
            }
            // Read OMM's config the same way OMM does (OhMyMeteors.CONFIG, lasersSection).
            cir.setReturnValue(OhMyMeteors.CONFIG.lasersSection.basic_laser_area_radius);
        } catch (RuntimeException ex) {
            // Never break the laser: if anything is wrong, OMM's original value is used.
            OmmFixes.warnOnce("fixBasicLaserRadius", "The basic laser radius fix failed; OMM keeps its original value.", ex);
        }
    }
}
