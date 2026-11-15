package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.fix.OmmFixes;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import me.emafire003.dev.ohmymeteors.util.MeteorSizeClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * B2: OMM 1.5.0 MeteorProjectileEntity.getStructureToPlace(MeteorSizeClass, String) re-rolls while the picked
 * structure is a "special" of its size class:
 *     while (structure_id.getPath().startsWith(size + "/special")) { structure_id = pick again }
 * If every candidate of the size class is a special, that loop never ends and the server hangs.
 *
 * Choice of injection (least invasive): a HEAD cancellable inject that rebuilds the same candidate list OMM
 * builds (same size-class prefix, same filter prefix) and acts ONLY when that list is non-empty and every
 * entry is a special. In that one case it returns a uniform random special and OMM's method never runs.
 * In every other case this handler returns without touching anything, consuming no randomness, so OMM's
 * pick distribution, its special-meteor chance and its error handling run exactly as before. A redirect or
 * local-variable edit around the loop was rejected: the loop condition is a plain getPath()/startsWith()
 * pair that is hard to target uniquely, and local capture would depend on compiler local ordering.
 *
 * The one thing replicated from OMM's own first steps is the lazy list rebuild
 * (reInitStructures when the list is empty on the server); it is idempotent, and without it the very
 * first call after start-up could not be checked. The 1-arg overload getStructureToPlace(MeteorSizeClass)
 * delegates to this method, so it is covered too.
 *
 * remap = false: the target is an OMM method. Gated by ommFixes.fixSpecialOnlyHang.
 */
@Mixin(value = MeteorProjectileEntity.class, remap = false)
public abstract class MeteorStructurePickMixin {

    @Inject(
            method = "getStructureToPlace(Lme/emafire003/dev/ohmymeteors/util/MeteorSizeClass;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void onmm$pickWhenOnlySpecials(MeteorSizeClass sizeClass, String filter, CallbackInfoReturnable<ResourceLocation> cir) {
        try {
            if (!Config.FIX_SPECIAL_ONLY_HANG.get()) {
                return;
            }
            MeteorProjectileEntity self = (MeteorProjectileEntity) (Object) this;

            // Same lazy rebuild as OMM's first step. Server side only, like OMM.
            if (OhMyMeteors.METEOR_STRUCTURES.isEmpty() && !self.level().isClientSide()) {
                OhMyMeteors.reInitStructures((ServerLevel) self.level());
            }

            // Same single-"error" case as OMM: leave it to OMM.
            List<ResourceLocation> all = OhMyMeteors.METEOR_STRUCTURES;
            if (all.isEmpty() || (all.size() == 1 && all.get(0).getPath().equals("error"))) {
                return;
            }

            String sizeName = sizeClass.getSerializedName();
            String specialPrefix = sizeName + "/special";
            String filterPrefix = (filter != null && !filter.isEmpty()) ? sizeName + "/" + filter : null;

            // Rebuild OMM's candidate list (same two filters) and note whether anything is not a special.
            List<ResourceLocation> candidates = new java.util.ArrayList<>();
            for (ResourceLocation id : all) {
                String path = id.getPath();
                if (!path.startsWith(sizeName)) {
                    continue;
                }
                if (filterPrefix != null && !path.startsWith(filterPrefix)) {
                    continue;
                }
                if (!path.startsWith(specialPrefix)) {
                    // At least one normal structure: OMM's loop ends, so OMM runs unchanged.
                    return;
                }
                candidates.add(id);
            }
            if (candidates.isEmpty()) {
                // OMM logs its own error for an empty list and uses the "error" structure: leave it.
                return;
            }

            // Only specials exist for this size class: pick one uniformly instead of looping forever.
            int index = self.level().getRandom().nextIntBetweenInclusive(0, candidates.size() - 1);
            cir.setReturnValue(candidates.get(index));
        } catch (RuntimeException ex) {
            // Never throw out of the pick: on any error OMM runs as it would have without this fix.
            OmmFixes.warnOnce("fixSpecialOnlyHang", "The special-only structure pick fix failed; OMM keeps its normal pick.", ex);
        }
    }
}
