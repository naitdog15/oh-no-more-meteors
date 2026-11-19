package com.temog.notminelegacy.ohnomoremeteors.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.joml.Vector3f;

/**
 * The big drifting smoke behind a falling ice (Rimerite) or space (Veyrite) meteor, in place of Oh My, Meteors!
 * red/orange meteor smoke (2026-11-19). Same behaviour as OMM's smoke: OMM's own soft big_smoke sprites, 80-130 tick
 * life, slow drift, fade over the last 60 ticks, and a colour that shifts from a dark to a light tone over its life
 * (one of two tone pairs per puff, 2 in 3 the first). Only the colours are the variant's.
 */
public class VariantBigSmokeParticle extends TextureSheetParticle {

    /** Two colour ramps (start -> end, 0-255) per variant look. */
    public enum Palette {
        RIMERITE(new Vector3f(46, 104, 128), new Vector3f(176, 214, 226),
                new Vector3f(95, 169, 199), new Vector3f(220, 239, 245)),
        VEYRITE(new Vector3f(74, 50, 107), new Vector3f(148, 116, 170),
                new Vector3f(101, 71, 128), new Vector3f(143, 184, 121));

        final Vector3f startA, endA, startB, endB;

        Palette(Vector3f startA, Vector3f endA, Vector3f startB, Vector3f endB) {
            this.startA = startA;
            this.endA = endA;
            this.startB = startB;
            this.endB = endB;
        }
    }

    private final Palette palette;
    private final boolean first;

    protected VariantBigSmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                      Palette palette, float scale) {
        super(level, x, y, z);
        this.palette = palette;
        this.scale(scale);
        this.setSize(0.25F, 0.25F);
        this.lifetime = this.random.nextInt(50) + 80;
        this.first = this.random.nextInt(3) != 2;
        this.gravity = 3.0E-6F;
        this.xd = xd;
        this.yd = yd + this.random.nextFloat() / 500.0F;
        this.zd = zd;
        applyColour();
    }

    private void applyColour() {
        float t = this.lifetime <= 0 ? 0.0F : (float) this.age / (float) this.lifetime;
        Vector3f c = first ? new Vector3f(palette.startA).lerp(palette.endA, t) : new Vector3f(palette.startB).lerp(palette.endB, t);
        this.rCol = c.x / 255.0F;
        this.gCol = c.y / 255.0F;
        this.bCol = c.z / 255.0F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime && this.alpha > 0.0F) {
            this.xd += this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
            this.zd += this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
            this.yd -= this.gravity;
            this.move(this.xd, this.yd, this.zd);
            if (this.age >= this.lifetime - 60 && this.alpha > 0.01F) {
                this.alpha -= 0.015F;
            }
            applyColour();
        } else {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** One provider per variant and size (puff = OMM scale 3, plume = OMM's one big size-based puff). */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Palette palette;
        private final float scale;

        public Provider(SpriteSet sprites, Palette palette, float scale) {
            this.sprites = sprites;
            this.palette = palette;
            this.scale = scale;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            VariantBigSmokeParticle p = new VariantBigSmokeParticle(level, x, y, z, xd, yd, zd, palette, scale);
            p.setAlpha(0.93F);
            p.pickSprite(sprites);
            return p;
        }
    }
}
