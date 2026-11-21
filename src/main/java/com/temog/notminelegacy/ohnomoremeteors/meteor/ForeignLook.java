package com.temog.notminelegacy.ohnomoremeteors.meteor;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;

/**
 * The look of another mod's meteor variant (2026-11-21), registered through {@link ForeignVariantLooks}. Everything
 * is resolved once, when the look is registered, so nothing here is looked up again while meteors fall.
 *
 * A null field means "keep Oh My, Meteors! own one" for that part (an id that was missing or not registered).
 *
 * @param claim        the claim string under MeteorVariant.NBT_KEY, for example "buildingbridges:ae2"
 * @param cold         body texture high up (OMM's meteoric_rock state)
 * @param mid          body texture in the middle state
 * @param hot          body texture in the hot state
 * @param glow         sky glow colour, 0xRRGGBB
 * @param glowWeight   sky glow strength relative to the shared baseline (as SkyGlowKind.weight)
 * @param glowLift     sky brightness change at full strength (as SkyGlowKind.lift)
 * @param flame        replaces OMM's flame
 * @param smoke        replaces OMM's smoke, large smoke, campfire smoke and its big meteor smoke
 * @param flash        replaces OMM's head flash (the flash scale goes in the x speed argument)
 * @param spark        replaces OMM's lava drops, and is the main trail particle
 * @param burst        replaces OMM's small explosion puff at the head
 * @param trailDensity multiplier on the fall trail's particle counts (0 = no trail)
 * @param distantFar   the distant impact sound, far tier
 * @param distantVeryFar the distant impact sound, very far tier
 * @param burns        false = the meteor does not show vanilla's burning overlay
 */
public record ForeignLook(String claim,
                          @Nullable ResourceLocation cold, @Nullable ResourceLocation mid, @Nullable ResourceLocation hot,
                          int glow, float glowWeight, float glowLift,
                          @Nullable ParticleOptions flame, @Nullable ParticleOptions smoke, @Nullable ParticleOptions flash,
                          @Nullable ParticleOptions spark, @Nullable ParticleOptions burst,
                          float trailDensity,
                          @Nullable SoundEvent distantFar, @Nullable SoundEvent distantVeryFar,
                          boolean burns) {

    /** The body texture for OMM's current texture state (cold / mid / hot), or OMM's own when this look has none. */
    public ResourceLocation bodyTexture(ResourceLocation ommTexture) {
        String path = ommTexture.getPath();
        ResourceLocation swapped = path.contains("hot") ? hot : path.contains("mid") ? mid : cold;
        return swapped == null ? ommTexture : swapped;
    }

    /** The given particle, or OMM's original when this look has none for that part. */
    public static ParticleOptions or(@Nullable ParticleOptions particle, ParticleOptions original) {
        return particle == null ? original : particle;
    }
}
