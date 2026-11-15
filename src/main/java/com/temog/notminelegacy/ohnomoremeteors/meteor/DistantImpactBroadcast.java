package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModSounds;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The distant crash: when any Oh My, Meteors! meteor lands, players in the same dimension who are too far
 * away to hear the explosion (beyond distantImpactMinDistance) hear a subtitled impact from its direction.
 *
 * The sound is sent to each listener on its own, placed a few blocks from the player toward the impact, so
 * the stereo position and the subtitle arrow point at the crash. Which sound plays depends on the meteor
 * kind (plain, fire, ice) and the distance tier ("far" or, from distantImpactVeryFarDistance on, the
 * softer "very far" rumble). Loudness falls with distance and rises with meteor size.
 *
 * Scatter meteors never broadcast (one sound per impact). Each player has a cooldown so a shower does not
 * stack crashes. Lasers never call explodeMeteor, so a lasered meteor is silent.
 */
public final class DistantImpactBroadcast {

    /** How far from the listener the sound is placed. Close enough to stay clearly audible at full volume. */
    private static final double PLACE_DISTANCE = 6.0D;

    /** Per-level, per-player game time of the last distant impact heard. */
    private static final Map<ServerLevel, Map<UUID, Long>> LAST_HEARD = new WeakHashMap<>();

    private DistantImpactBroadcast() {
    }

    /** Called once per meteor impact on the server, before the impact itself runs. */
    public static void onImpact(MeteorProjectileEntity meteor) {
        if (!Config.DISTANT_IMPACT_ENABLED.get() || !(meteor.level() instanceof ServerLevel level)) {
            return;
        }
        if (meteor.isScatterMeteor()) {
            return;
        }
        MeteorVariant variant = MeteorVariant.fromEntity(meteor);
        Vec3 impact = meteor.position();
        double min = Config.DISTANT_IMPACT_MIN_DISTANCE.get();
        double veryFar = Config.DISTANT_IMPACT_VERY_FAR_DISTANCE.get();
        double max = Math.max(min + 1.0D, Config.DISTANT_IMPACT_MAX_DISTANCE.get());
        long cooldown = Config.DISTANT_IMPACT_COOLDOWN_TICKS.get();
        long now = level.getGameTime();
        float sizeGain = Mth.clamp(0.55F + 0.07F * meteor.getSize(), 0.6F, 1.0F);
        float master = Config.DISTANT_IMPACT_VOLUME.get().floatValue();
        Map<UUID, Long> heard = LAST_HEARD.computeIfAbsent(level, l -> new java.util.HashMap<>());

        for (ServerPlayer player : level.players()) {
            Vec3 ear = player.getEyePosition();
            Vec3 offset = impact.subtract(ear);
            double distance = offset.length();
            if (distance < min || distance > max) {
                continue;
            }
            Long last = heard.get(player.getUUID());
            if (last != null && now - last < cooldown) {
                continue;
            }
            heard.put(player.getUUID(), now);

            boolean isVeryFar = distance >= veryFar;
            SoundEvent sound = pick(variant, isVeryFar);
            // 1.0 just past the hearing edge, down to 0.45 at the maximum distance.
            float distanceGain = 1.0F - 0.55F * (float) ((distance - min) / (max - min));
            float volume = Mth.clamp(master * sizeGain * distanceGain, 0.0F, 1.0F);
            if (volume <= 0.01F) {
                continue;
            }
            Vec3 at = ear.add(offset.scale(PLACE_DISTANCE / distance));
            float pitch = 0.92F + level.random.nextFloat() * 0.12F;
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.WEATHER, at.x, at.y, at.z, volume, pitch, level.random.nextLong()));
        }
    }

    private static SoundEvent pick(MeteorVariant variant, boolean veryFar) {
        if (variant == MeteorVariant.CINDRITE) {
            return (veryFar ? ModSounds.DISTANT_CINDRITE_VERY_FAR : ModSounds.DISTANT_CINDRITE_FAR).get();
        }
        if (variant == MeteorVariant.VEYRITE) {
            return (veryFar ? ModSounds.DISTANT_VEYRITE_VERY_FAR : ModSounds.DISTANT_VEYRITE_FAR).get();
        }
        if (variant == MeteorVariant.RIMERITE) {
            return (veryFar ? ModSounds.DISTANT_RIMERITE_VERY_FAR : ModSounds.DISTANT_RIMERITE_FAR).get();
        }
        return (veryFar ? ModSounds.DISTANT_METEOR_VERY_FAR : ModSounds.DISTANT_METEOR_FAR).get();
    }
}
