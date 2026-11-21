package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KomitKitty;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

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
    /**
     * The other mod's look whose OMM fall animation is running on this thread (set by MeteorFallParticlesMixin beside
     * {@link #ANIMATING}, only when that is empty), else empty. See {@link ForeignVariantLooks}.
     */
    public static final ThreadLocal<ForeignLook> ANIMATING_FOREIGN = new ThreadLocal<>();

    private MeteorVisuals() {
    }

    /**
     * The variant a meteor LOOKS like: its real variant, or Veyrite for a Komit Kitty (kitties are small Veyrite-looking
     * meteors but stay a separate claim for gameplay). Null for a plain meteor.
     *
     * Use this ONLY for looks (body texture, particles, the burning overlay, what the client is told). Gameplay keeps
     * {@link MeteorVariant#fromEntity}, which is null for a kitty, so no variant rule ever applies to it.
     */
    @Nullable
    public static MeteorVariant lookOf(Entity entity) {
        MeteorVariant variant = MeteorVariant.fromEntity(entity);
        if (variant != null) {
            return variant;
        }
        return KomitKitty.isKitty(entity) ? MeteorVariant.VEYRITE : null;
    }

    /**
     * The other mod's look a meteor shows (2026-11-21), or null. Always null when {@link #lookOf} is not null, so this
     * mod's variants and the Komit Kitty keep their own looks; null for unregistered claims too. Both sides.
     */
    @Nullable
    public static ForeignLook foreignLookOf(Entity entity) {
        if (lookOf(entity) != null) {
            return null;
        }
        return ForeignVariantLooks.lookOf(entity);
    }

    /** The other mod's look of a meteor body texture, in OMM's current state (OMM's own texture when the look has none). */
    public static ResourceLocation bodyTexture(ForeignLook look, ResourceLocation ommTexture) {
        return look.bodyTexture(ommTexture);
    }

    /**
     * Server side, other mod's look: the same swap as {@link #fallParticle(MeteorVariant, ParticleOptions)}. OMM's big
     * meteor smoke, smoke, large smoke and campfire smoke become the look's smoke, flame its flame, lava its spark.
     * A part the look has no particle for keeps OMM's own.
     */
    public static ParticleOptions fallParticle(@Nullable ForeignLook look, ParticleOptions original) {
        if (look == null) {
            return original;
        }
        var type = original.getType();
        if (original instanceof me.emafire003.dev.ohmymeteors.particles.meteor_smoke.MeteorSmokeScaledOptions
                || type == ParticleTypes.SMOKE || type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.LARGE_SMOKE) {
            return ForeignLook.or(look.smoke(), original);
        }
        if (type == ParticleTypes.FLAME) {
            return ForeignLook.or(look.flame(), original);
        }
        if (type == ParticleTypes.LAVA) {
            return ForeignLook.or(look.spark(), original);
        }
        return original;
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
