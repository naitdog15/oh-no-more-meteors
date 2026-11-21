package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The despawn rule for the cats that come out of kitty capsules, so neglected cats do not pile up.
 *
 *  - A capsule cat is marked in its own saved data (ForgeData) with two keys: {@link #CAT_KEY} and {@link #SEEN_KEY}
 *    (the game time a player was last near it). It is also set to "persistent", so vanilla's stray-cat despawn
 *    cannot remove it early: this rule is the only one.
 *  - Every 200 ticks each loaded marked cat is checked: tamed or named cats are kept for good (the marks are
 *    removed); a cat with a player within catPlayerRange blocks gets a fresh timestamp; any other cat is removed
 *    once catDespawnTicks (default 10 minutes) have passed since it was last seen. 0 means never.
 *  - Time while the chunk was unloaded counts (game time keeps running): a stale untamed cat that loads in from
 *    disk has its join cancelled, so it is gone the moment its chunk loads. Server downtime does not count.
 *
 * Cost: a handful of cats every 10 seconds.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapsuleCats {

    /** ForgeData key: this cat came out of a kitty capsule. */
    public static final String CAT_KEY = "ohnomoremeteors:capsule_cat";
    /** ForgeData key: the game time a player was last near this cat. */
    public static final String SEEN_KEY = "ohnomoremeteors:cat_seen";

    /** How often (in ticks) the loaded cats are checked. */
    private static final int CHECK_INTERVAL = 200;

    /** Loaded marked cats per level (identity sets; entities are compared by reference). */
    private static final Map<ServerLevel, Set<Mob>> TRACKED = new IdentityHashMap<>();

    private static boolean loggedFailure;

    private CapsuleCats() {
    }

    /**
     * Marks a cat as a capsule cat: both keys, plus persistence so only this rule can remove it. Call it before
     * the cat is added to the level so the join event tracks it.
     */
    public static void mark(Mob cat, long gameTime) {
        CompoundTag data = cat.getPersistentData();
        data.putBoolean(CAT_KEY, true);
        data.putLong(SEEN_KEY, gameTime);
        cat.setPersistenceRequired();
    }

    /** A cat that stays for good: tamed or named. */
    private static boolean kept(Mob cat) {
        return (cat instanceof TamableAnimal tame && tame.isTame()) || cat.hasCustomName();
    }

    private static boolean isMarked(Entity entity) {
        return entity instanceof Mob && entity.getPersistentData().getBoolean(CAT_KEY);
    }

    /** Removes both marks from a cat that is now kept for good. */
    private static void unmark(Mob cat) {
        CompoundTag data = cat.getPersistentData();
        data.remove(CAT_KEY);
        data.remove(SEEN_KEY);
    }

    /**
     * One pass over the loaded capsule cats of a level (public for the gametests). Works on a snapshot, because
     * removing a cat fires the leave event, which edits the tracked set.
     */
    public static void check(ServerLevel level) {
        Set<Mob> cats = TRACKED.get(level);
        if (cats == null || cats.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        long despawn = Config.KITTY_CAT_DESPAWN_TICKS.get();
        int range = Config.KITTY_CAT_PLAYER_RANGE.get();
        for (Mob cat : new ArrayList<>(cats)) {
            if (cat.isRemoved()) {
                cats.remove(cat);
                continue;
            }
            if (kept(cat)) {
                unmark(cat);
                cats.remove(cat);
                continue;
            }
            if (level.hasNearbyAlivePlayer(cat.getX(), cat.getY(), cat.getZ(), range)) {
                cat.getPersistentData().putLong(SEEN_KEY, now);
                continue;
            }
            if (despawn > 0 && now - cat.getPersistentData().getLong(SEEN_KEY) >= despawn) {
                cat.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level) || !isMarked(event.getEntity())) {
                return;
            }
            Mob cat = (Mob) event.getEntity();
            if (event.loadedFromDisk() && !kept(cat)) {
                long despawn = Config.KITTY_CAT_DESPAWN_TICKS.get();
                long seen = cat.getPersistentData().getLong(SEEN_KEY);
                if (despawn > 0 && level.getGameTime() - seen >= despawn) {
                    event.setCanceled(true); // neglected while its chunk was unloaded: gone as it loads
                    return;
                }
            }
            TRACKED.computeIfAbsent(level, l -> Collections.newSetFromMap(new IdentityHashMap<>())).add(cat);
        } catch (RuntimeException | LinkageError ex) {
            warnOnce(ex);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            Set<Mob> cats = TRACKED.get(level);
            if (cats != null && event.getEntity() instanceof Mob cat) {
                cats.remove(cat);
            }
        } catch (RuntimeException | LinkageError ex) {
            warnOnce(ex);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % CHECK_INTERVAL != 0L || TRACKED.isEmpty()) {
            return;
        }
        try {
            check(level);
        } catch (RuntimeException | LinkageError ex) {
            warnOnce(ex);
        }
    }

    /** A level that unloads drops its tracked cats (they are saved with their chunks). */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TRACKED.remove(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKED.clear();
    }

    private static void warnOnce(Throwable ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The capsule cat despawn rule failed (this is logged once).", ex);
        }
    }
}
