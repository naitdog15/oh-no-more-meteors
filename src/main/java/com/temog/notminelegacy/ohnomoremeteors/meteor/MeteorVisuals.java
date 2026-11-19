package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The look of a falling fire / ice / space meteor (2026-11-19): which texture the meteor body uses and which
 * particles replace Oh My, Meteors! own fall particles. Plain meteors (variant null) keep OMM's look untouched.
 * Pure mapping, safe on both sides (no client classes).
 */
public final class MeteorVisuals {

    /** The variant whose OMM fall animation is running on this thread (set by MeteorFallParticlesMixin), else empty. */
    public static final ThreadLocal<MeteorVariant> ANIMATING = new ThreadLocal<>();
    /** How many OMM fall particles have been swapped since start (a check that the swap is live; read by tests). */
    public static final java.util.concurrent.atomic.AtomicLong SWAPPED = new java.util.concurrent.atomic.AtomicLong();

    private MeteorVisuals() {
    }

    /**
     * The meteor body texture for a variant, matching OMM's state: OMM picks meteoric_rock (cold, high up),
     * meteoric_rock_mid_static or meteoric_rock_hot_static while the meteor falls; the same state is kept here.
     */
    public static ResourceLocation bodyTexture(MeteorVariant variant, ResourceLocation ommTexture) {
        String path = ommTexture.getPath();
        String state = path.contains("hot") ? "hot" : path.contains("mid") ? "mid" : "cold";
        return OhNoMoreMeteors.id("textures/entity/meteor/" + variant.name().toLowerCase(java.util.Locale.ROOT) + "_meteor_" + state + ".png");
    }

    /**
     * Server side: the variant's particle in place of one OMM sends during the fall (flame, smoke, lava drops,
     * campfire smoke trail). Anything else is returned unchanged.
     */
    public static ParticleOptions fallParticle(@Nullable MeteorVariant variant, ParticleOptions original) {
        if (variant == null) {
            return original;
        }
        var type = original.getType();
        // OMM's own big red/orange meteor smoke: Cindrite keeps it; ice and space get it in their own colours.
        if (original instanceof me.emafire003.dev.ohmymeteors.particles.meteor_smoke.MeteorSmokeScaledOptions smoke) {
            boolean plume = smoke.getScale() > 3.5F;
            return switch (variant) {
                case CINDRITE -> original;
                case RIMERITE -> plume ? ModParticles.RIMERITE_BIG_PLUME.get() : ModParticles.RIMERITE_BIG_SMOKE.get();
                case VEYRITE -> plume ? ModParticles.VEYRITE_BIG_PLUME.get() : ModParticles.VEYRITE_BIG_SMOKE.get();
            };
        }
        if (type == ParticleTypes.FLAME) {
            return switch (variant) {
                case CINDRITE -> ModParticles.CINDRITE_FLAME.get();
                case RIMERITE -> ModParticles.RIMERITE_FLAME.get();
                case VEYRITE -> ModParticles.VEYRITE_FLAME.get();
            };
        }
        if (type == ParticleTypes.SMOKE || type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.LARGE_SMOKE) {
            return smoke(variant);
        }
        if (type == ParticleTypes.LAVA) {
            return switch (variant) {
                case CINDRITE -> ModParticles.CINDRITE_EMBER.get();
                case RIMERITE -> ModParticles.RIMERITE_SHARD.get();
                case VEYRITE -> ModParticles.VEYRITE_SPARK.get();
            };
        }
        return original;
    }

    public static ParticleOptions smoke(MeteorVariant variant) {
        return switch (variant) {
            case CINDRITE -> ModParticles.CINDRITE_SMOKE.get();
            case RIMERITE -> ModParticles.RIMERITE_SMOKE.get();
            case VEYRITE -> ModParticles.VEYRITE_SMOKE.get();
        };
    }

    /** Client side: the head glow that replaces OMM's flash (its scale goes in the x speed argument). */
    public static ParticleOptions flash(MeteorVariant variant) {
        return switch (variant) {
            case CINDRITE -> ModParticles.CINDRITE_FLASH.get();
            case RIMERITE -> ModParticles.RIMERITE_FLASH.get();
            case VEYRITE -> ModParticles.VEYRITE_FLASH.get();
        };
    }

    /** Client side: the puff at the head that replaces OMM's small explosion particle. */
    public static ParticleOptions headPuff(MeteorVariant variant) {
        return switch (variant) {
            case CINDRITE -> ModParticles.CINDRITE_BURST.get();
            case RIMERITE -> ModParticles.RIMERITE_BURST.get();
            case VEYRITE -> ModParticles.VEYRITE_SMOKE.get();
        };
    }
}
