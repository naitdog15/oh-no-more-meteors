package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.SkyGlowKind;
import com.temog.notminelegacy.ohnomoremeteors.network.ModNetwork;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModSounds;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager of Komit Kitty showers: one shower per level at most.
 *
 * It decides whether a natural OMM meteor turns into a shower, builds the drop queue, and drives the shower from the
 * level tick (deferred init, laser refresh, interception, staggered drops, end). A shower is memory only: nothing is
 * saved, and every way a shower can end (normal end, timeout, stop, config off, level unload, server stopping, a
 * failed tick) discards the kitties it still owns first.
 *
 * Every handler fails open: an error is logged once and never breaks an entity join or a level tick.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KittyShowers {

    private static final ResourceLocation METEOR_TYPE_ID = new ResourceLocation("ohmymeteors", "meteor_projectile");
    /** Extra ticks past warning + duration before a stuck shower is ended by force. */
    private static final int TIMEOUT_EXTRA_TICKS = 1200;

    /** The running shower per level. Server thread only. */
    private static final IdentityHashMap<ServerLevel, KittyShower> SHOWERS = new IdentityHashMap<>();
    /** Game time at which each level's last shower ended; the cooldown is added to it when it is checked. */
    private static final IdentityHashMap<ServerLevel, Long> COOLDOWNS = new IdentityHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private KittyShowers() {
    }

    // ------------------------------------------------------------------------------------------------ public API

    /**
     * Builds the drop queue for a new shower and registers it. Touches no blocks, chunks or players: the warning, the
     * cat hiss and the laser search run on the shower's first level tick.
     *
     * @return the shower, or null if kitty showers are switched off or this level already has one
     */
    @Nullable
    public static KittyShower start(ServerLevel level, double cx, double cz, KittyShowerParams p) {
        if (!Config.KITTY_ENABLED.get() || SHOWERS.containsKey(level)) {
            return null;
        }
        RandomSource r = level.getRandom();
        int n = Math.max(1, p.count());
        int warning = Math.max(0, p.warningTicks());
        int duration = Math.max(1, p.durationTicks());
        double spread = Math.max(0.0D, p.spreadRadius());
        double maxAngle = Mth.clamp(p.maxEntryAngle(), 0.0D, 80.0D);
        int sizeA = Mth.clamp(Config.KITTY_SIZE_MIN.get(), 1, 3);
        int sizeB = Mth.clamp(Config.KITTY_SIZE_MAX.get(), 1, 3);
        int sizeLo = Math.min(sizeA, sizeB);
        int sizeHi = Math.max(sizeA, sizeB);

        ArrayDeque<KittyDrop> queue = new ArrayDeque<>(n);
        for (int i = 0; i < n; i++) {
            // Jittered even spacing: smooth, never a burst. The due times come out in increasing order.
            int due = warning + (int) Math.floor((i + r.nextDouble()) * duration / n);
            double radius = spread * Math.sqrt(r.nextDouble());
            double theta = r.nextDouble() * (Math.PI * 2.0D);
            double tx = cx + radius * Math.cos(theta);
            double tz = cz + radius * Math.sin(theta);
            float angle = (float) (r.nextDouble() * maxAngle);
            float yaw = (float) (r.nextDouble() * 360.0D);
            byte size = (byte) (sizeLo + r.nextInt(sizeHi - sizeLo + 1));
            queue.addLast(new KittyDrop(due, tx, tz, angle, yaw, size));
        }
        KittyShower s = new KittyShower(level, cx, cz, level.getGameTime(), p, queue,
                (long) warning + duration + TIMEOUT_EXTRA_TICKS);
        SHOWERS.put(level, s);
        return s;
    }

    /** The level's running shower, or null. */
    @Nullable
    public static KittyShower active(ServerLevel level) {
        return SHOWERS.get(level);
    }

    /**
     * Ends the level's shower at once: its alive kitties are discarded, the queue is dropped, there is no message and no
     * cooldown.
     *
     * @return true if a shower was running
     */
    public static boolean stop(ServerLevel level) {
        KittyShower s = SHOWERS.get(level);
        if (s == null) {
            return false;
        }
        endShower(level, s);
        return true;
    }

    /**
     * The active shower that owns this kitty's unresolved flight, or null for an orphan, a test kitty or a kitty of an
     * earlier shower. Call it BEFORE the kitty is discarded.
     */
    @Nullable
    public static KittyShower ownerOf(ServerLevel level, Entity kitty) {
        KittyShower s = SHOWERS.get(level);
        if (s == null) {
            return null;
        }
        KittyFlight f = s.byEntity.get(kitty);
        return f != null && !f.resolved && f.owner == s ? s : null;
    }

    /** True while the shower is under the per-shower cat cap; always true without an owner. */
    public static boolean catAllowed(@Nullable KittyShower owner) {
        return owner == null || owner.cats < Config.KITTY_MAX_CATS_PER_SHOWER.get();
    }

    /**
     * Counts a landing for the shower that owns the kitty's flight: marks the flight resolved, adds to {@code landed}
     * and, when a cat was placed, to {@code cats}. Does nothing for a null owner, a finished shower, an unknown kitty or
     * a flight that is already resolved.
     */
    public static void onKittyLanded(@Nullable KittyShower owner, Entity kitty, boolean catPlaced) {
        if (owner == null || owner.finished || SHOWERS.get(owner.level) != owner) {
            return;
        }
        KittyFlight f = owner.byEntity.get(kitty);
        if (f == null || f.resolved || f.owner != owner) {
            return;
        }
        f.resolved = true;
        owner.landed++;
        if (catPlaced) {
            owner.cats++;
        }
    }

    // ------------------------------------------------------------------------------------------------ events

    /**
     * Natural trigger. HIGHEST priority, so the original meteor can still be cancelled before anyone else sees it.
     * Transactional: the shower is built first, and the join is cancelled only if that succeeded. If anything fails the
     * original meteor falls as normal, so a failure can never lose both the meteor and the shower.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoinNatural(EntityJoinLevelEvent event) {
        try {
            if (!(event.getEntity() instanceof MeteorProjectileEntity meteor)
                    || !(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) {
                return;
            }
            if (!METEOR_TYPE_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(meteor.getType()))) {
                return;
            }
            // Command meteors, scatter children, Building Bridges meteors and our own kitties all carry a claim or the scatter flag.
            if (meteor.isScatterMeteor() || MeteorVariant.hasAnyClaim(meteor)) {
                return;
            }
            if (!Config.KITTY_ENABLED.get() || SHOWERS.containsKey(level)) {
                return;
            }
            Long endedAt = COOLDOWNS.get(level);
            if (endedAt != null && level.getGameTime() < endedAt + Config.KITTY_COOLDOWN_TICKS.get()) {
                return;
            }
            double chance = Config.KITTY_NATURAL_CHANCE.get();
            if (!(chance > 0.0D) || !(level.getRandom().nextDouble() < chance)) {
                return;
            }
            KittyShower s = start(level, meteor.getX(), meteor.getZ(), KittyShowerParams.fromConfig(level.getRandom()));
            if (s != null) {
                event.setCanceled(true);
            }
        } catch (RuntimeException | LinkageError ex) {
            warnOnce("natural-trigger", ex);
        }
    }

    /**
     * Kitties saved with their chunk are dropped on load: shower state is never saved, so no laser would track them and
     * they would blast somewhere unannounced.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoinLoaded(EntityJoinLevelEvent event) {
        try {
            if (event.loadedFromDisk() && !event.getLevel().isClientSide() && KomitKitty.isKitty(event.getEntity())) {
                event.setCanceled(true);
            }
        } catch (RuntimeException | LinkageError ex) {
            warnOnce("loaded-kitty", ex);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        KittyShower s = SHOWERS.get(level);
        if (s == null) {
            return;
        }
        try {
            tickShower(level, s);
        } catch (RuntimeException | LinkageError ex) {
            warnOnce("shower-tick", ex);
            try {
                endShower(level, s);
            } catch (RuntimeException | LinkageError ex2) {
                SHOWERS.remove(level);
            }
        }
    }

    /** A level is unloading: discard its kitties and forget its shower, with no messages. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        try {
            if (event.getLevel() instanceof ServerLevel level) {
                KittyShower s = SHOWERS.get(level);
                if (s != null) {
                    endShower(level, s);
                }
                COOLDOWNS.remove(level);
            }
        } catch (RuntimeException | LinkageError ex) {
            warnOnce("level-unload", ex);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            for (ServerLevel level : new ArrayList<>(SHOWERS.keySet())) {
                KittyShower s = SHOWERS.get(level);
                if (s != null) {
                    endShower(level, s);
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            warnOnce("server-stopping", ex);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SHOWERS.clear();
        COOLDOWNS.clear();
    }

    // ------------------------------------------------------------------------------------------------ the tick

    private static void tickShower(ServerLevel level, KittyShower s) {
        if (!Config.KITTY_ENABLED.get()) {
            // Switching the config off ends a running shower.
            endShower(level, s);
            return;
        }
        long now = level.getGameTime();
        long elapsed = now - s.startTick;
        if (s.pendingInit) {
            s.pendingInit = false;
            init(level, s, now);
        }
        int lead = Math.max(0, s.params.warningTicks());
        long finalAt = lead - Math.max(0, s.params.finalWarningTicks());
        // The sky shifts from the start of the shower and reaches full strength at the final warning (user ruling
        // 2026-11-21); refreshed every 2 seconds so players who walk in see it too.
        if (now >= s.nextSkyPing) {
            s.nextSkyPing = now + SKY_PING_TICKS;
            sendSky(level, s, true, (int) Math.max(1L, finalAt - elapsed));
        }
        // During the early-warning wait nothing else runs: no laser search, no drops.
        if (!s.finalWarned) {
            if (elapsed < finalAt) {
                return;
            }
            finalWarning(level, s, now);
        }
        // Lasers: refreshed rarely, and every alive flight is re-bound to the new list.
        if (now >= s.nextLaserRefresh) {
            refreshLasers(level, s, now);
        }
        KittyInterceptor.tick(s);

        // Staggered drops: a held-back drop just waits, nothing is lost.
        int maxAlive = Config.KITTY_MAX_ALIVE.get();
        int maxDrops = Config.KITTY_MAX_DROPS_PER_TICK.get();
        int dropsThisTick = 0;
        while (!s.queue.isEmpty() && s.queue.peekFirst().due() <= elapsed
                && s.alive.size() < maxAlive && dropsThisTick < maxDrops) {
            KittyDrop drop = s.queue.pollFirst();
            dropsThisTick++;
            KittySpawner.spawn(level, drop, s);
        }

        boolean timedOut = elapsed >= s.timeoutTicks;
        if ((s.queue.isEmpty() && s.alive.isEmpty()) || timedOut) {
            finishNormally(level, s, now);
        }
    }

    /** Ticks between sky-shift refreshes; a client that hears nothing for a while fades its sky back by itself. */
    private static final int SKY_PING_TICKS = 40;
    /** Ticks the sky takes to fade back after a shower. */
    private static final int SKY_FADE_OUT_TICKS = 100;

    /**
     * The world-facing start of a shower, deferred to its first level tick: the EARLY warning (chat and the warning
     * sound; the sky starts to shift in the same tick), sent only when the lead is longer than the final warning.
     * Lasers are not searched yet.
     */
    private static void init(ServerLevel level, KittyShower s, long now) {
        s.nextSkyPing = now;
        int lead = Math.max(0, s.params.warningTicks());
        int finalWarning = Math.max(0, s.params.finalWarningTicks());
        if (lead <= finalWarning) {
            return;
        }
        int minutes = Math.max(1, Math.round(lead / 1200.0F));
        Component early = Component.translatable("message.ohnomoremeteors.komit_kitty.early", (int) s.cx, (int) s.cz, minutes)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : playersNear(level, s)) {
            player.sendSystemMessage(early);
        }
        broadcast(level, s, ModSounds.KOMIT_KITTY_WARNING.get(), 1.0F);
    }

    /** The FINAL warning: chat, the arrival sound with a cat hiss (the sky is at full strength now), and the first laser search. */
    private static void finalWarning(ServerLevel level, KittyShower s, long now) {
        s.finalWarned = true;
        Component warning = Component.translatable("message.ohnomoremeteors.komit_kitty.incoming", (int) s.cx, (int) s.cz)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : playersNear(level, s)) {
            player.sendSystemMessage(warning);
            player.playNotifySound(SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 1.0F, 0.8F);
        }
        broadcast(level, s, ModSounds.KOMIT_KITTY_ARRIVAL.get(), 0.9F);
        s.lasers = KittyLasers.gather(level, s.cx, s.cz, s.params.spreadRadius());
        s.nextLaserRefresh = now + Config.KITTY_LASER_REFRESH_TICKS.get();
    }

    /**
     * Plays a shower sound to every player near it, from the shower's direction: the sound is placed a few blocks from
     * the listener toward the sky above the shower centre, so it is clearly heard at any distance and the subtitle
     * arrow points at the shower. Players inside the shower hear it from above.
     */
    private static void broadcast(ServerLevel level, KittyShower s, SoundEvent sound, float pitch) {
        if (!Config.KITTY_BROADCAST_SOUNDS.get()) {
            return;
        }
        float volume = Config.KITTY_BROADCAST_VOLUME.get().floatValue();
        if (volume <= 0.0F) {
            return;
        }
        for (ServerPlayer player : playersNear(level, s)) {
            Vec3 ear = player.getEyePosition();
            Vec3 sky = new Vec3(s.cx, Math.max(ear.y + 40.0D, level.getSeaLevel() + 80.0D), s.cz);
            Vec3 offset = sky.subtract(ear);
            double distance = offset.length();
            Vec3 at = distance < 1.0E-3D ? ear.add(0.0D, 6.0D, 0.0D) : ear.add(offset.scale(6.0D / distance));
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.WEATHER, at.x, at.y, at.z, volume, pitch, level.random.nextLong()));
        }
    }

    /** Sends the sky shift (on, with the ticks left to reach full strength, or off) to the players near the shower. */
    private static void sendSky(ServerLevel level, KittyShower s, boolean on, int rampTicks) {
        if (!Config.KITTY_SKY_SHIFT.get()) {
            return;
        }
        float strength = on ? Config.KITTY_SKY_STRENGTH.get().floatValue() : 0.0F;
        for (ServerPlayer player : playersNear(level, s)) {
            ModNetwork.sendSkyOmen(player, strength, rampTicks, SkyGlowKind.KOMIT_KITTY);
        }
    }

    private static void refreshLasers(ServerLevel level, KittyShower s, long now) {
        ArrayList<KittyLasers.Laser> old = s.lasers;
        ArrayList<KittyLasers.Laser> fresh = KittyLasers.gather(level, s.cx, s.cz, s.params.spreadRadius());
        // Keep each laser's sound timer across the refresh, so the sound limit still holds.
        for (KittyLasers.Laser l : fresh) {
            for (KittyLasers.Laser o : old) {
                if (o.pos.equals(l.pos)) {
                    l.lastSoundTick = o.lastSoundTick;
                    break;
                }
            }
        }
        for (KittyFlight f : s.alive) {
            if (!f.resolved) {
                f.bind(fresh, old);
            }
        }
        s.lasers = fresh;
        s.nextLaserRefresh = now + Config.KITTY_LASER_REFRESH_TICKS.get();
    }

    // ------------------------------------------------------------------------------------------------ ending

    /** Normal end or timeout: end, then the "over" message, one log line and the cooldown. */
    private static void finishNormally(ServerLevel level, KittyShower s, long now) {
        endShower(level, s);
        COOLDOWNS.put(level, now);
        if (Config.KITTY_ANNOUNCE_END.get()) {
            Component over = Component.translatable("message.ohnomoremeteors.komit_kitty.over", s.shot, s.landed)
                    .withStyle(ChatFormatting.GRAY);
            for (ServerPlayer player : playersNear(level, s)) {
                player.sendSystemMessage(over);
            }
        }
        OhNoMoreMeteors.LOGGER.info("[ohnomoremeteors] Komit Kitty shower at {}, {}: dropped {}, shot {}, landed {}, fizzled {}, cats {}",
                (int) s.cx, (int) s.cz, s.dropped, s.shot, s.landed, s.fizzled, s.cats);
    }

    /** Every end path goes through here: the owned kitties are discarded FIRST, then the shower is removed. */
    private static void endShower(ServerLevel level, KittyShower s) {
        try {
            discardFlights(s);
            if (!s.pendingInit) {
                try {
                    sendSky(level, s, false, SKY_FADE_OUT_TICKS);
                } catch (RuntimeException | LinkageError ex) {
                    warnOnce("sky-off", ex);
                }
            }
        } finally {
            s.finished = true;
            s.queue.clear();
            SHOWERS.remove(level, s);
        }
    }

    private static void discardFlights(KittyShower s) {
        for (KittyFlight f : s.alive) {
            f.resolved = true;
            try {
                if (!f.kitty.isRemoved()) {
                    f.kitty.discard();
                }
            } catch (RuntimeException | LinkageError ex) {
                warnOnce("discard-flight", ex);
            }
        }
        s.alive.clear();
        s.byEntity.clear();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Players of the shower's level within the message radius (horizontal) of its centre. */
    private static List<ServerPlayer> playersNear(ServerLevel level, KittyShower s) {
        double radius = Config.KITTY_MESSAGE_RADIUS.get();
        double r2 = radius * radius;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - s.cx;
            double dz = player.getZ() - s.cz;
            if (dx * dx + dz * dz <= r2) {
                out.add(player);
            }
        }
        return out;
    }

    /** Logs a failure once per key, so a broken handler cannot spam the log every tick. */
    static void warnOnce(String key, Throwable ex) {
        if (WARNED.add(key)) {
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Komit Kitty code failed once ({}) and will stay quiet about it: {}", key, ex.toString());
        }
    }
}
