package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Komit Kitty claim. A Komit Kitty is a normal Oh My, Meteors! meteor that carries one extra string in its
 * persistent (ForgeData) tag, under the shared claim key {@link MeteorVariant#NBT_KEY}. Because the key already has a
 * value, every other system (the variant roll, the random size and speed, the plain-meteor cleanup, Building Bridges)
 * treats the meteor as "owned by someone else" and leaves it alone.
 *
 * Pure and safe on both sides, but only the server ever carries this claim: on the client the kitty is shown with the
 * Veyrite claim instead (look only, see MeteorVisuals.lookOf).
 */
public final class KomitKitty {

    /** The exact claim string stored under {@link MeteorVariant#NBT_KEY}. */
    public static final String CLAIM = "ohnomoremeteors:komit_kitty";

    /** Counts how often the OMM chunk-ticket call was skipped for a kitty (live proof for the gametests). */
    public static final AtomicLong TICKETS_SKIPPED = new AtomicLong();

    private KomitKitty() {
    }

    /** True for an OMM meteor that carries the Komit Kitty claim. Null-safe. */
    public static boolean isKitty(@Nullable Entity e) {
        if (!(e instanceof MeteorProjectileEntity)) {
            return false;
        }
        var data = e.getPersistentData();
        return data.contains(MeteorVariant.NBT_KEY, Tag.TAG_STRING) && CLAIM.equals(data.getString(MeteorVariant.NBT_KEY));
    }

    /** Writes the Komit Kitty claim onto the entity. Call it BEFORE the entity joins the level. */
    public static void mark(Entity e) {
        e.getPersistentData().putString(MeteorVariant.NBT_KEY, CLAIM);
    }
}
