package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KittyLaserFilter;
import me.emafire003.dev.ohmymeteors.blocks.advanced_laser.AdvancedMeteorLaserBlock;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Komit Kitty showers: OMM's Advanced Meteor Laser ignores kitties. The kitty shower has its own laser rules
 * (KittyInterceptor), so OMM's laser must not shoot them as well.
 *
 * AdvancedMeteorLaserBlock.tick builds a list "meteors" with getEntitiesOfClass and returns if it is empty, otherwise it
 * sets FIRING and shoots each one. This handler runs right after that list is stored and removes the kitties, so a
 * kitty-only list is empty and the laser neither shoots nor switches to its firing state. "meteors" is the only List
 * local in the method (the forEach bodies are separate lambda methods).
 *
 * remap = false: the target is an OMM method, not Minecraft code. The handler has to be static because tick is.
 *
 * Fallback if require = 1 ever fails at load: replace this injector with a @ModifyArg on the getEntitiesOfClass call,
 * index 2 (the predicate), with remap = true on the @At, wrapping the predicate with
 * .and(e -> !KomitKitty.isKitty(e)). Kept only as this note on purpose.
 */
@Mixin(value = AdvancedMeteorLaserBlock.class, remap = false)
public abstract class AdvancedLaserIgnoresKittyMixin {

    @ModifyVariable(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lme/emafire003/dev/ohmymeteors/blocks/advanced_laser/AdvancedMeteorLaserBlockEntity;)V",
            at = @At("STORE"), ordinal = 0, remap = false, require = 1)
    private static List<MeteorProjectileEntity> onmm$ignoreKitties(List<MeteorProjectileEntity> meteors) {
        return KittyLaserFilter.withoutKitties(meteors);
    }
}
