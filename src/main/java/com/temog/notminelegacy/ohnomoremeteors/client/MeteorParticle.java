package com.temog.notminelegacy.ohnomoremeteors.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * One particle class for all six meteor particles. The sprite follows the age (frame 0 newborn, frame 3 dying);
 * {@link Kind} holds the per-particle tuning. Colour is in the sprite, nothing is tinted.
 */
public class MeteorParticle extends TextureSheetParticle {

    /** Per-particle tuning: lifetime range (ticks), gravity, friction, size, full-bright share of life, fade, spin, jitter. */
    public enum Kind {
        CINDRITE_EMBER(16, 24, 0.02F, 0.90F, 0.12F, 1.0F, false, 0.0F, 0.0F),
        CINDRITE_BURST(18, 28, 0.15F, 0.92F, 0.20F, 0.5F, false, 0.0F, 0.0F),
        CINDRITE_HEAT_SPARK(20, 30, -0.03F, 0.96F, 0.08F, 1.0F, false, 0.0F, 0.01F),
        RIMERITE_SHARD(18, 26, 0.04F, 0.93F, 0.12F, 0.0F, false, 0.1F, 0.0F),
        RIMERITE_BURST(16, 24, 0.10F, 0.90F, 0.18F, 0.0F, false, 0.0F, 0.0F),
        RIMERITE_FROST_MIST(30, 45, 0.004F, 0.96F, 0.30F, 0.0F, true, 0.0F, 0.0F),
        // Falling meteor particles (2026-11-19). Flames rise a little and stay bright; smoke drifts and fades.
        CINDRITE_FLAME(10, 16, -0.02F, 0.92F, 0.20F, 1.0F, false, 0.0F, 0.02F),
        CINDRITE_SMOKE(30, 50, -0.01F, 0.96F, 0.40F, 0.15F, true, 0.0F, 0.0F),
        RIMERITE_FLAME(10, 16, -0.02F, 0.92F, 0.20F, 1.0F, false, 0.0F, 0.02F),
        RIMERITE_SMOKE(30, 50, -0.005F, 0.96F, 0.40F, 0.2F, true, 0.0F, 0.0F),
        VEYRITE_FLAME(10, 16, -0.02F, 0.92F, 0.20F, 1.0F, false, 0.0F, 0.02F),
        VEYRITE_SMOKE(30, 50, -0.01F, 0.96F, 0.40F, 0.2F, true, 0.0F, 0.0F),
        VEYRITE_SPARK(14, 22, 0.03F, 0.92F, 0.10F, 1.0F, false, 0.0F, 0.03F);

        final int minLife;
        final int maxLife;
        final float gravity;
        final float friction;
        final float size;
        /** Share of the life spent full bright (1 = always, 0 = never). */
        final float brightFraction;
        final boolean fadeOut;
        final float spin;
        final float jitter;

        Kind(int minLife, int maxLife, float gravity, float friction, float size, float brightFraction,
             boolean fadeOut, float spin, float jitter) {
            this.minLife = minLife;
            this.maxLife = maxLife;
            this.gravity = gravity;
            this.friction = friction;
            this.size = size;
            this.brightFraction = brightFraction;
            this.fadeOut = fadeOut;
            this.spin = spin;
            this.jitter = jitter;
        }
    }

    private final SpriteSet sprites;
    private final Kind kind;

    protected MeteorParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                             SpriteSet sprites, Kind kind) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.kind = kind;
        this.lifetime = kind.minLife + this.random.nextInt(Math.max(1, kind.maxLife - kind.minLife + 1));
        this.gravity = kind.gravity;
        this.friction = kind.friction;
        this.quadSize = kind.size;
        // Keep the velocity that was sent (the super constructor randomises it).
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        if (kind.jitter > 0.0F) {
            this.xd += (this.random.nextFloat() - 0.5F) * kind.jitter * 2.0F;
            this.zd += (this.random.nextFloat() - 0.5F) * kind.jitter * 2.0F;
        }
        this.roll = kind.spin > 0.0F ? this.random.nextFloat() * Mth.TWO_PI : 0.0F;
        this.oRoll = this.roll;
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        setSpriteFromAge(this.sprites);
        if (kind.spin > 0.0F) {
            this.oRoll = this.roll;
            this.roll += kind.spin;
        }
        if (kind.fadeOut) {
            // 0.9 at birth down to 0 at the end of life (0.6 was invisible in daylight).
            this.alpha = 0.9F *(1.0F - (float) this.age / (float) this.lifetime);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        if (kind.brightFraction > 0.0F && this.age < this.lifetime * kind.brightFraction) {
            return 0xF000F0;
        }
        return super.getLightColor(partialTick);
    }

    /** Provider shared by all six types; the Kind says which one. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MeteorParticle(level, x, y, z, xd, yd, zd, sprites, kind);
        }
    }
}
