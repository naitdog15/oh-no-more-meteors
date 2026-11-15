package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.fix.OmmFixes;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import me.emafire003.dev.ohmymeteors.util.MeteorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B3: OMM 1.5.0 MeteorProjectileEntity.onHitEntity discards the meteor when
 * MeteorUtils.canSpawnInModdedRegion(level, pos) is TRUE, which is the wrong way round: onHitBlock uses the
 * negation, so a meteor is removed only inside a protected claim. Here the one call inside onHitEntity is
 * redirected to return the negation. Only matters with OMM explode_on_entity_collision = true.
 * Gated by ommFixes.fixEntityHitClaimCheck.
 *
 * No class-level remap = false on purpose: onHitEntity overrides a vanilla method (Projectile.onHitEntity), so
 * the method name has to be remapped through the refmap. The INVOKE target is an OMM method, so that @At has
 * remap = false. The full descriptor on the target selects the (ServerLevel, BlockPos) overload; MeteorUtils
 * also has (ServerPlayer, BlockPos) and (ServerPlayer, ServerLevel, BlockPos). onHitBlock is not touched.
 */
@Mixin(MeteorProjectileEntity.class)
public abstract class MeteorEntityHitMixin {

    @Redirect(
            method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/emafire003/dev/ohmymeteors/util/MeteorUtils;canSpawnInModdedRegion(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Z",
                    remap = false
            ),
            require = 1
    )
    // An instance handler, because the target method (onHitEntity) is an instance method.
    private boolean onmm$invertClaimCheck(ServerLevel level, BlockPos pos) {
        boolean original = MeteorUtils.canSpawnInModdedRegion(level, pos);
        try {
            // Key off: hand back OMM's own answer unchanged.
            return Config.FIX_ENTITY_HIT_CLAIM_CHECK.get() ? !original : original;
        } catch (RuntimeException ex) {
            OmmFixes.warnOnce("fixEntityHitClaimCheck", "The entity hit claim fix failed; OMM keeps its original check.", ex);
            return original;
        }
    }
}
