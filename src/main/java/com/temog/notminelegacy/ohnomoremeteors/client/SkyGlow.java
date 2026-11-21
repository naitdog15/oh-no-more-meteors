package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVisuals;
import com.temog.notminelegacy.ohnomoremeteors.meteor.SkyGlowKind;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * Client only: the add-on's own sky glow (2026-11-21), replacing Oh My, Meteors!' skyglow.
 *
 * <p><b>Sources.</b> Every falling meteor near the camera (its look picks the {@link SkyGlowKind}: plain, Cindrite,
 * Rimerite, Veyrite), and one server-pushed event, the Komit Kitty shower omen. While the omen is up it takes over:
 * the meteor glows fade under it, so a shower reads as one clean colour instead of a flicker of kitties.
 *
 * <p><b>Baseline</b> (shared by every kind): {@link #RANGE} reach with a smooth falloff, {@link #RISE_PER_TICK} and
 * {@link #FALL_PER_TICK} fades, peak strength from the config. A kind only brings its colour, weight and lift.
 *
 * <p><b>Clean look.</b> The sky keeps its own brightness: the hue moves toward the kind's colour and the brightness
 * changes only by the kind's small lift (glows brighten a little, the omen darkens). Nothing flashes to white. The fog
 * follows at a softer strength so the horizon blends instead of banding.
 *
 * <p><b>Cost.</b> Meteors are tracked through join and leave events (no entity scans). All the work happens once per
 * client tick over that short list; the per-frame sky and fog hooks only read two floats and a cached colour, and do
 * nothing at all when no glow is up.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, value = Dist.CLIENT)
public final class SkyGlow {

    /** Blocks from the camera at which a meteor starts to tint the sky. */
    public static final double RANGE = 192.0D;
    /** Strength gained per tick (full in 1 second). */
    public static final float RISE_PER_TICK = 0.05F;
    /** Strength lost per tick (gone in 3 seconds). */
    public static final float FALL_PER_TICK = 1.0F / 60.0F;
    /** Fog follows the sky at this share, so the horizon stays soft. */
    private static final float FOG_SHARE = 0.6F;
    /** Ticks without an omen refresh after which it fades back by itself. */
    private static final int OMEN_STALE_TICKS = 120;
    private static final int OMEN_FADE_TICKS = 100;

    private static final SkyGlowKind[] KINDS = SkyGlowKind.values();
    private static final float[] TARGET = new float[KINDS.length];
    private static final float[] LEVEL = new float[KINDS.length];
    private static final ArrayList<MeteorProjectileEntity> METEORS = new ArrayList<>();

    // Omen (event source): eased toward its target over the ramp the server gives.
    private static float omen;
    private static float omenTarget;
    private static float omenStep;
    private static int omenKind = SkyGlowKind.KOMIT_KITTY.ordinal();
    private static int omenSince;

    // Composite, computed once per tick and read per frame.
    private static float strength;
    private static float strengthO;
    private static float cr = 1.0F, cg = 1.0F, cb = 1.0F;
    private static float lift;

    // Other mods' looks (ForeignVariantLooks): ONE extra slot beside the SkyGlowKind arrays. Its colour, weight and lift
    // come from the strongest foreign meteor in range, set once per tick (kept while the slot fades out).
    private static float foreignTarget;
    private static float foreignLevel;
    private static float foreignR = 1.0F, foreignG = 1.0F, foreignB = 1.0F;
    private static float foreignWeight = 1.0F;
    private static float foreignLift;

    private SkyGlow() {
    }

    // ---------------------------------------------------------------------------------------------- sources

    /** The server's omen message: target strength (0 = fade back), ticks to get there, which kind. */
    public static void receiveOmen(float target, int rampTicks, int kind) {
        omenTarget = Mth.clamp(target, 0.0F, 1.0F);
        omenStep = Math.abs(omenTarget - omen) / Math.max(1, rampTicks);
        omenKind = SkyGlowKind.byOrdinal(kind).ordinal();
        omenSince = 0;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof MeteorProjectileEntity m) {
            METEORS.add(m);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof MeteorProjectileEntity m) {
            METEORS.remove(m);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        METEORS.clear();
        java.util.Arrays.fill(TARGET, 0.0F);
        java.util.Arrays.fill(LEVEL, 0.0F);
        foreignTarget = foreignLevel = 0.0F;
        omen = omenTarget = omenStep = 0.0F;
        strength = strengthO = 0.0F;
    }

    // ---------------------------------------------------------------------------------------------- the tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        replaceOmmSkyGlow();
        strengthO = strength;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        boolean enabled = Config.SKY_GLOW_ENABLED.get();

        // Omen ramp, with a stale fallback so it can never stick.
        if (omenTarget > 0.0F && ++omenSince > OMEN_STALE_TICKS) {
            omenTarget = 0.0F;
            omenStep = omen / OMEN_FADE_TICKS;
        }
        if (omen != omenTarget) {
            float s = Math.max(omenStep, 1.0E-4F);
            omen = omen < omenTarget ? Math.min(omenTarget, omen + s) : Math.max(omenTarget, omen - s);
        }

        java.util.Arrays.fill(TARGET, 0.0F);
        foreignTarget = 0.0F;
        if (enabled && !METEORS.isEmpty()) {
            com.temog.notminelegacy.ohnomoremeteors.meteor.ForeignLook strongestForeign = null;
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double r2 = RANGE * RANGE;
            float peak = Config.SKY_GLOW_STRENGTH.get().floatValue();
            for (int i = METEORS.size() - 1; i >= 0; i--) {
                MeteorProjectileEntity m = METEORS.get(i);
                if (m.isRemoved()) {
                    METEORS.remove(i);
                    continue;
                }
                double d2 = m.distanceToSqr(cam);
                if (d2 >= r2) {
                    continue;
                }
                float near = 1.0F - (float) Math.sqrt(d2 / r2);
                float w = near * near * (3.0F - 2.0F * near) * peak; // smoothstep falloff
                // Another mod's look goes to the foreign slot (not PLAIN). Null for every look of this mod.
                com.temog.notminelegacy.ohnomoremeteors.meteor.ForeignLook foreign = MeteorVisuals.foreignLookOf(m);
                if (foreign != null) {
                    if (w > foreignTarget) {
                        foreignTarget = w;
                        strongestForeign = foreign;
                    }
                    continue;
                }
                int k = SkyGlowKind.of(MeteorVisuals.lookOf(m)).ordinal();
                if (w > TARGET[k]) {
                    TARGET[k] = w;
                }
            }
            if (strongestForeign != null) {
                int rgb = strongestForeign.glow();
                foreignR = ((rgb >> 16) & 0xFF) / 255.0F;
                foreignG = ((rgb >> 8) & 0xFF) / 255.0F;
                foreignB = (rgb & 0xFF) / 255.0F;
                foreignWeight = strongestForeign.glowWeight();
                foreignLift = strongestForeign.glowLift();
            }
            // The omen takes over: meteor glows fade under it.
            if (omen > 0.0F) {
                float keep = 1.0F - Mth.clamp(omen / Math.max(0.05F, Config.KITTY_SKY_STRENGTH.get().floatValue()), 0.0F, 1.0F);
                for (int k = 0; k < TARGET.length; k++) {
                    TARGET[k] *= keep;
                }
                foreignTarget *= keep;
            }
        }
        if (omen > TARGET[omenKind]) {
            TARGET[omenKind] = omen; // the omen is already eased; it is not faded again below
        }

        float sum = 0.0F, r = 0.0F, g = 0.0F, b = 0.0F, l = 0.0F, max = 0.0F;
        for (int k = 0; k < LEVEL.length; k++) {
            float t = TARGET[k];
            float v = LEVEL[k];
            if (k == omenKind && t == omen && omen > 0.0F) {
                v = t;
            } else if (v < t) {
                v = Math.min(t, v + RISE_PER_TICK);
            } else if (v > t) {
                v = Math.max(t, v - FALL_PER_TICK);
            }
            LEVEL[k] = v;
            if (v <= 0.0F) {
                continue;
            }
            SkyGlowKind kind = KINDS[k];
            float w = v * kind.weight;
            int rgb = kind.rgb();
            r += w * ((rgb >> 16) & 0xFF) / 255.0F;
            g += w * ((rgb >> 8) & 0xFF) / 255.0F;
            b += w * (rgb & 0xFF) / 255.0F;
            l += w * kind.lift;
            sum += w;
            max = Math.max(max, w);
        }
        // The foreign slot: same fades as the kinds above.
        if (foreignLevel < foreignTarget) {
            foreignLevel = Math.min(foreignTarget, foreignLevel + RISE_PER_TICK);
        } else if (foreignLevel > foreignTarget) {
            foreignLevel = Math.max(foreignTarget, foreignLevel - FALL_PER_TICK);
        }
        if (foreignLevel > 0.0F) {
            float w = foreignLevel * foreignWeight;
            r += w * foreignR;
            g += w * foreignG;
            b += w * foreignB;
            l += w * foreignLift;
            sum += w;
            max = Math.max(max, w);
        }
        if (sum > 0.0F) {
            cr = r / sum;
            cg = g / sum;
            cb = b / sum;
            lift = l / sum;
        }
        strength = Math.min(1.0F, max);
    }

    /** Oh My, Meteors! skyglow is switched off in memory while ours runs (config replaceOmmSkyGlow); see OmmSkyGlowSwitch. */
    private static void replaceOmmSkyGlow() {
        com.temog.notminelegacy.ohnomoremeteors.meteor.OmmSkyGlowSwitch.apply();
    }

    // ---------------------------------------------------------------------------------------------- per frame

    /** Current glow strength (0..1), for tests and debugging. */
    public static float strength() {
        return strength;
    }

    /** True when any glow is visible this frame. */
    public static boolean active(float partialTick) {
        return strength > 0.0F || strengthO > 0.0F;
    }

    /** The sky (or fog) colour with the glow applied at the given share of its strength. */
    public static Vec3 apply(Vec3 sky, float partialTick, float share) {
        float k = Mth.lerp(partialTick, strengthO, strength) * share;
        if (k <= 0.0F) {
            return sky;
        }
        // Keep the sky's own brightness; move the hue toward the glow colour, then the small lift (or darkening).
        double lum = sky.x * 0.3D + sky.y * 0.59D + sky.z * 0.11D;
        double tl = cr * 0.3D + cg * 0.59D + cb * 0.11D;
        double scale = (tl > 1.0E-3D ? lum / tl : 1.0D) * (1.0D + lift);
        double tr = Math.min(1.0D, cr * scale);
        double tg = Math.min(1.0D, cg * scale);
        double tb = Math.min(1.0D, cb * scale);
        return new Vec3(Mth.lerp(k, sky.x, tr), Mth.lerp(k, sky.y, tg), Mth.lerp(k, sky.z, tb));
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float pt = (float) event.getPartialTick();
        if (!active(pt)) {
            return;
        }
        Vec3 fog = apply(new Vec3(event.getRed(), event.getGreen(), event.getBlue()), pt, FOG_SHARE);
        event.setRed((float) fog.x);
        event.setGreen((float) fog.y);
        event.setBlue((float) fog.z);
    }
}
