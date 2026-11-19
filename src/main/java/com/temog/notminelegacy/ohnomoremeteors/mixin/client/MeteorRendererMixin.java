package com.temog.notminelegacy.ohnomoremeteors.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVisuals;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import me.emafire003.dev.ohmymeteors.entities.client.MeteorProjectileEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only: a falling fire / ice / space meteor is drawn with its variant's own body texture (cold / mid / hot,
 * following OMM's own texture state) instead of OMM's meteoric rock. Plain meteors are untouched.
 *
 * render() (listed under its dev name and its production SRG name m_7392_, unmapped, because it is OMM's typed
 * override and the annotation processor has no mapping for that descriptor) remembers which meteor is being drawn; every texture OMM asks for while drawing it (in render() itself
 * and in its textureByHeight / textureByDistance helpers) is then swapped by MeteorVisuals#bodyTexture.
 */
@Mixin(MeteorProjectileEntityRenderer.class)
public abstract class MeteorRendererMixin {

    @Unique
    private static MeteorVariant onmm$drawing;

    @Inject(method = {"render(Lme/emafire003/dev/ohmymeteors/entities/MeteorProjectileEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", "m_7392_(Lme/emafire003/dev/ohmymeteors/entities/MeteorProjectileEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = @At("HEAD"), require = 0, remap = false)
    private void onmm$rememberVariant(MeteorProjectileEntity entity, float yaw, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        MeteorVariant variant = null;
        try {
            variant = MeteorVariant.fromEntity(entity);
        } catch (RuntimeException ignored) {
            // plain look
        }
        onmm$drawing = variant;
    }

    @Inject(method = {"render(Lme/emafire003/dev/ohmymeteors/entities/MeteorProjectileEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", "m_7392_(Lme/emafire003/dev/ohmymeteors/entities/MeteorProjectileEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = @At("RETURN"), require = 0, remap = false)
    private void onmm$forgetVariant(MeteorProjectileEntity entity, float yaw, float partialTick, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        onmm$drawing = null;
    }

    @ModifyArg(method = {"textureByHeight", "textureByDistance",
            "render(Lme/emafire003/dev/ohmymeteors/entities/MeteorProjectileEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            "m_7392_(Lme/emafire003/dev/ohmymeteors/entities/MeteorProjectileEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = @At(value = "INVOKE", remap = true,
                    target = "Lme/emafire003/dev/ohmymeteors/entities/client/MeteorProjectileEntityModel;renderType(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"),
            index = 0, require = 0, remap = false)
    private ResourceLocation onmm$variantTextureInHelpers(ResourceLocation original) {
        return onmm$swap(original);
    }

    @Unique
    private static ResourceLocation onmm$swap(ResourceLocation original) {
        MeteorVariant variant = onmm$drawing;
        return variant == null ? original : MeteorVisuals.bodyTexture(variant, original);
    }
}
