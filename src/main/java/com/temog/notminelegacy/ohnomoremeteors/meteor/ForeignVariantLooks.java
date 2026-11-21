package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Looks for other mods' meteor variants (2026-11-21). A mod that claims Oh My, Meteors! meteors under the shared
 * MeteorVariant.NBT_KEY convention (Building Bridges writes "buildingbridges:ae2") can give its meteors their own
 * body texture, fall particles, trail, sky glow and distant impact sound, drawn by this mod's existing look paths.
 *
 * <p><b>Registration</b> is by IMC, sent to "ohnomoremeteors" with method {@link #IMC_METHOD}. The payload supplier
 * returns a {@code Map.Entry<String, CompoundTag>}: the key is the claim string, the value describes the look:
 * <pre>
 *   textures:     { cold, mid, hot }                 texture ResourceLocation strings
 *   glow:         int 0xRRGGBB                      sky glow colour
 *   glowWeight:   float 0 - 4 (default 1.0)         sky glow strength
 *   glowLift:     float -1 - 1 (default 0.0)        sky brightness change
 *   particles:    { flame, smoke, flash, spark, burst }  SimpleParticleType ids
 *   trailDensity: float 0 - 4 (default 1.0)
 *   distant:      { far, very_far }                 SoundEvent ids
 *   burns:        byte (default 1)                  0 = no burning overlay
 * </pre>
 *
 * <p><b>Refused:</b> a claim that is not a valid id, a claim outside the sending mod's namespace, any
 * "ohnomoremeteors:" claim (this mod's variants and the Komit Kitty keep their own looks), a claim that is already
 * registered (the first registration wins), and numbers that are not finite or out of range. A refused registration
 * is logged once and changes nothing.
 *
 * <p><b>Speed.</b> A meteor whose claim is registered here belongs to the registering mod for its fall speed too:
 * the plain-meteor speed fallback (MeteorEvents.applySpeed with no variant) skips it, whether or not looks are
 * switched on. See {@link #isRegistered(String)}.
 *
 * <p>The registry is filled during mod loading (both sides) and only read afterwards. It is an immutable map behind a
 * volatile field, so the render thread and the server thread can read it without locks.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForeignVariantLooks {

    /** IMC method name. */
    public static final String IMC_METHOD = "register_variant_look";

    private static final String OWN_NAMESPACE = OhNoMoreMeteors.MODID;

    private static volatile Map<String, ForeignLook> looks = Map.of();

    private ForeignVariantLooks() {
    }

    @SubscribeEvent
    public static void onImc(InterModProcessEvent event) {
        InterModComms.getMessages(OhNoMoreMeteors.MODID, IMC_METHOD::equals).forEach(msg -> {
            String sender = msg.senderModId();
            Object payload;
            try {
                payload = msg.messageSupplier().get();
                if (payload instanceof Supplier<?> inner) {
                    payload = inner.get(); // tolerate a supplier wrapped twice
                }
            } catch (RuntimeException | LinkageError ex) {
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Variant look from {}: the payload supplier threw; skipped.", sender, ex);
                return;
            }
            if (!(payload instanceof Map.Entry<?, ?> entry) || !(entry.getKey() instanceof String claim)
                    || !(entry.getValue() instanceof CompoundTag tag)) {
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Variant look from {}: the payload is not a Map.Entry<String, CompoundTag>; skipped.", sender);
                return;
            }
            register(sender, claim, tag);
        });
    }

    /**
     * Registers one look. Used by the IMC handler (and by tests); the same rules apply to both.
     *
     * @return null when the look was registered, otherwise the reason it was refused (also logged)
     */
    @Nullable
    public static synchronized String register(String sender, String claim, CompoundTag tag) {
        String reason = check(sender, claim);
        ForeignLook look = null;
        if (reason == null) {
            try {
                look = parse(claim, tag.copy());
            } catch (IllegalArgumentException ex) {
                reason = ex.getMessage();
            } catch (RuntimeException | LinkageError ex) {
                reason = "the look could not be read (" + ex + ")";
            }
        }
        if (reason != null) {
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Variant look '{}' from {} refused: {}.", claim, sender, reason);
            return reason;
        }
        Map<String, ForeignLook> next = new HashMap<>(looks);
        next.put(claim, look);
        looks = Map.copyOf(next);
        OhNoMoreMeteors.LOGGER.info("[ohnomoremeteors] Variant look '{}' registered by {}.", claim, sender);
        return null;
    }

    @Nullable
    private static String check(@Nullable String sender, @Nullable String claim) {
        if (claim == null || sender == null) {
            return "no claim or no sender";
        }
        ResourceLocation id = ResourceLocation.tryParse(claim);
        if (id == null || !id.toString().equals(claim)) {
            return "not a valid namespaced id";
        }
        // Checked on its own, before the sender: even this mod may not register a look for its own claims.
        if (OWN_NAMESPACE.equals(id.getNamespace())) {
            return "ohnomoremeteors claims keep their own look";
        }
        if (!id.getNamespace().equals(sender)) {
            return "the claim is outside the sender's namespace";
        }
        if (looks.containsKey(claim)) {
            return "already registered (the first registration wins)";
        }
        return null;
    }

    private static ForeignLook parse(String claim, CompoundTag tag) {
        CompoundTag tex = tag.getCompound("textures");
        CompoundTag par = tag.getCompound("particles");
        CompoundTag snd = tag.getCompound("distant");
        int glow = SkyGlowKind.PLAIN.rgb();
        if (tag.contains("glow")) {
            if (!tag.contains("glow", Tag.TAG_INT)) {
                throw new IllegalArgumentException("glow must be an int 0xRRGGBB");
            }
            glow = tag.getInt("glow");
            if (glow < 0 || glow > 0xFFFFFF) {
                throw new IllegalArgumentException("glow must be 0x000000 - 0xFFFFFF");
            }
        }
        float glowWeight = number(tag, "glowWeight", 1.0F, 0.0F, 4.0F);
        float glowLift = number(tag, "glowLift", 0.0F, -1.0F, 1.0F);
        float trailDensity = number(tag, "trailDensity", 1.0F, 0.0F, 4.0F);
        boolean burns = !tag.contains("burns", Tag.TAG_ANY_NUMERIC) || tag.getByte("burns") != 0;
        return new ForeignLook(claim,
                texture(tex, "cold"), texture(tex, "mid"), texture(tex, "hot"),
                glow, glowWeight, glowLift,
                particle(claim, par, "flame"), particle(claim, par, "smoke"), particle(claim, par, "flash"),
                particle(claim, par, "spark"), particle(claim, par, "burst"),
                trailDensity,
                sound(claim, snd, "far"), sound(claim, snd, "very_far"),
                burns);
    }

    private static float number(CompoundTag tag, String key, float fallback, float min, float max) {
        if (!tag.contains(key)) {
            return fallback;
        }
        if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        float v = tag.getFloat(key);
        if (!Float.isFinite(v) || v < min || v > max) {
            throw new IllegalArgumentException(key + " must be " + min + " - " + max + " (was " + v + ")");
        }
        return v;
    }

    @Nullable
    private static ResourceLocation texture(CompoundTag tex, String key) {
        return tex.contains(key, Tag.TAG_STRING) ? ResourceLocation.tryParse(tex.getString(key)) : null;
    }

    @Nullable
    private static ParticleOptions particle(String claim, CompoundTag par, String key) {
        ResourceLocation id = par.contains(key, Tag.TAG_STRING) ? ResourceLocation.tryParse(par.getString(key)) : null;
        if (id == null) {
            return null;
        }
        var type = ForgeRegistries.PARTICLE_TYPES.containsKey(id) ? ForgeRegistries.PARTICLE_TYPES.getValue(id) : null;
        if (type instanceof SimpleParticleType simple) {
            return simple;
        }
        OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Variant look '{}': particle {} '{}' is not a registered simple particle; OMM's own is kept.", claim, key, id);
        return null;
    }

    @Nullable
    private static SoundEvent sound(String claim, CompoundTag snd, String key) {
        ResourceLocation id = snd.contains(key, Tag.TAG_STRING) ? ResourceLocation.tryParse(snd.getString(key)) : null;
        if (id == null) {
            return null;
        }
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.containsKey(id) ? ForgeRegistries.SOUND_EVENTS.getValue(id) : null;
        if (sound == null) {
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Variant look '{}': distant sound {} '{}' is not registered; the plain meteor sound is kept.", claim, key, id);
        }
        return sound;
    }

    // ---------------------------------------------------------------------------------------------- reading

    /** True when a look is registered for this exact claim string. Cheap (one map lookup). */
    public static boolean isRegistered(@Nullable String claim) {
        return claim != null && looks.containsKey(claim);
    }

    /** True when the entity carries a claim that has a registered look (looks switched on or not). */
    public static boolean isRegisteredClaim(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(MeteorVariant.NBT_KEY, Tag.TAG_STRING) && isRegistered(data.getString(MeteorVariant.NBT_KEY));
    }

    /** The registered look for a claim string, or null. */
    @Nullable
    public static ForeignLook get(@Nullable String claim) {
        return claim == null ? null : looks.get(claim);
    }

    /**
     * The foreign look a meteor shows, or null: null for an unclaimed meteor, for any ohnomoremeteors claim (the
     * variants and the Komit Kitty), for an unregistered claim, and when [foreignVariants] showLooks is off.
     */
    @Nullable
    public static ForeignLook lookOf(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(MeteorVariant.NBT_KEY, Tag.TAG_STRING)) {
            return null;
        }
        ForeignLook look = looks.get(data.getString(MeteorVariant.NBT_KEY));
        if (look == null) {
            return null;
        }
        try {
            if (!Config.FOREIGN_LOOKS_ENABLED.get()) {
                return null;
            }
        } catch (RuntimeException ex) {
            // config not loaded yet: show the look
        }
        return look;
    }
}
