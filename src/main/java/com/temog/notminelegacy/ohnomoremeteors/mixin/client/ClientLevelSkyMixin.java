package com.temog.notminelegacy.ohnomoremeteors.mixin.client;

import com.temog.notminelegacy.ohnomoremeteors.client.SkyGlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the add-on's sky glow (client.SkyGlow) to the sky colour. A no-op when no glow is up. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void ohnomoremeteors$skyGlow(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (SkyGlow.active(partialTick)) {
            cir.setReturnValue(SkyGlow.apply(cir.getReturnValue(), partialTick, 1.0F));
        }
    }
}
