package com.temog.notminelegacy.ohnomoremeteors.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * The glow at the head of a falling fire / ice / space meteor, in place of Oh My, Meteors! white flash. Same timing
 * and size curve as OMM's flash (4 ticks, swells then shrinks, scaled by the meteor size), but with the variant's own
 * sprite. The scale is passed in the x speed argument (the particle itself does not move).
 */
public class VariantFlashParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float scale;

    protected VariantFlashParticle(ClientLevel level, double x, double y, double z, float scale, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.scale = Mth.clamp(scale, 0.01F, 50.0F);
        this.lifetime = 4;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        this.setAlpha(Mth.clamp(0.6F - (this.age + partialTick - 1.0F) * 0.125F, 0.0F, 1.0F));
        super.render(consumer, camera, partialTick);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.scale * Mth.sin((this.age + partialTick - 1.0F) * 0.25F * (float) Math.PI);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    /** Provider for the three flash types; the x speed argument carries the scale. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new VariantFlashParticle(level, x, y, z, (float) xd, sprites);
        }
    }
}
