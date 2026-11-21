package com.temog.notminelegacy.ohnomoremeteors.meteor;

import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted on the Forge event bus (server side only) after this mod has handled a meteor impact and its crater
 * builder has returned without an error. Other mods can listen for it to know that a variant crater was really
 * built, instead of guessing. Not cancellable, and posted after the work is done, so a listener cannot change
 * the impact.
 *
 * Not posted when OMM keeps the impact (a meteor that is not claimed, a switched-off variant, a submerged
 * impact, a failure while preparing) or when the impact fails part way. Komit Kitty impacts have their own
 * path and do not post it.
 *
 * The meteor entity has already been discarded when this is posted; its data (tags, ForgeData) can still be
 * read. {@link #blocksWritten()} is 0 when OMM's meteor_griefing is off (blast only, no blocks changed).
 */
public final class CraterBuiltEvent extends Event {

    private final ServerLevel level;
    private final MeteorProjectileEntity meteor;
    private final BlockPos centre;
    private final MeteorVariant variant;
    private final int blocksWritten;

    public CraterBuiltEvent(ServerLevel level, MeteorProjectileEntity meteor, BlockPos centre, MeteorVariant variant, int blocksWritten) {
        this.level = level;
        this.meteor = meteor;
        this.centre = centre.immutable();
        this.variant = variant;
        this.blocksWritten = blocksWritten;
    }

    public ServerLevel level() {
        return level;
    }

    public MeteorProjectileEntity meteor() {
        return meteor;
    }

    public BlockPos centre() {
        return centre;
    }

    public MeteorVariant variant() {
        return variant;
    }

    /** Lower-case variant name ("cindrite", "rimerite", "veyrite"), for listeners that avoid the enum. */
    public String variantId() {
        return variant.name().toLowerCase(java.util.Locale.ROOT);
    }

    public int blocksWritten() {
        return blocksWritten;
    }
}
