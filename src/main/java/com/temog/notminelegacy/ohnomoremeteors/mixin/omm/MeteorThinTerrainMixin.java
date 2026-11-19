package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.fix.OmmFixes;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix for meteors that fall through thin terrain (config ommFixes.fixThinTerrain).
 *
 * After its first block hit, an OMM meteor keeps flying for size/2 more hits so it looks as if it digs in, then
 * jumps back to the first contact point ({@code explosionPos}) and explodes there. Where the ground is thinner than
 * that (superflat, End islands, a thin cave roof, a floating build) the meteor passes through before it has counted
 * enough hits, finds only air below, and falls into the void without ever exploding.
 *
 * This runs at the end of every server tick of a meteor that has already hit something:
 *  - if it is now in open air (no collision at its block and the block below) or below the world, the dig-in is
 *    over: it goes back to the first contact point and explodes there, exactly where OMM would have exploded it;
 *  - a meteor that never hit anything and has fallen 64 blocks below the world is removed (OMM only removes
 *    meteors above y 2000, so an End-void meteor otherwise falls forever).
 * Inside normal terrain nothing changes: the meteor is in solid blocks until it explodes on its own.
 */
@Mixin(MeteorProjectileEntity.class)
public abstract class MeteorThinTerrainMixin {

    @Shadow(remap = false)
    protected boolean exploded;

    @Shadow(remap = false)
    protected Vec3 explosionPos;

    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void onmm$explodeOnPassThrough(CallbackInfo ci) {
        MeteorProjectileEntity self = (MeteorProjectileEntity) (Object) this;
        Level level = self.level();
        if (level.isClientSide() || this.exploded || self.isRemoved()) {
            return;
        }
        try {
            if (!Config.FIX_THIN_TERRAIN.get()) {
                return;
            }
            double minY = level.getMinBuildHeight();
            if (this.explosionPos == null) {
                if (self.getY() < minY - 64.0D) {
                    self.discard();
                }
                return;
            }
            BlockPos pos = self.blockPosition();
            boolean belowWorld = self.getY() < minY;
            boolean inOpenAir = !belowWorld && level.isLoaded(pos) && level.isLoaded(pos.below())
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
            if (belowWorld || inOpenAir) {
                self.setPosRaw(this.explosionPos.x, this.explosionPos.y, this.explosionPos.z);
                self.explodeMeteor();
            }
        } catch (RuntimeException ex) {
            OmmFixes.warnOnce("fixThinTerrain", "The thin terrain fix failed; OMM keeps its normal behaviour.", ex);
        }
    }
}
