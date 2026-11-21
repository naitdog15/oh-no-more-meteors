package com.temog.notminelegacy.ohnomoremeteors.network;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.meteor.SkyGlowKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * The add-on's network channel. Two messages:
 * 0: which variant a falling Oh My, Meteors! meteor is, sent to a player when the meteor comes into their view (the
 * variant claim lives in server-only entity data), so the client can draw the variant's body texture and particles.
 * 1: a meteor-laser shot at a Komit Kitty (beam, flash and pop), sent to players near the shot.
 * 2: a sky-glow omen (target strength, ticks to get there, which SkyGlowKind), sent to players near a Komit Kitty shower.
 * 3: which other mod's registered variant look a falling meteor has (its claim string, ForeignVariantLooks), sent like 0.
 */
public final class ModNetwork {

    private static final String PROTOCOL = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            OhNoMoreMeteors.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(MeteorVariantMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MeteorVariantMessage::encode)
                .decoder(MeteorVariantMessage::decode)
                .consumerMainThread(MeteorVariantMessage::handle)
                .add();
        CHANNEL.messageBuilder(KittyShotMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KittyShotMessage::encode)
                .decoder(KittyShotMessage::decode)
                .consumerMainThread(KittyShotMessage::handle)
                .add();
        CHANNEL.messageBuilder(KittySkyMessage.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KittySkyMessage::encode)
                .decoder(KittySkyMessage::decode)
                .consumerMainThread(KittySkyMessage::handle)
                .add();
        CHANNEL.messageBuilder(ForeignVariantMessage.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForeignVariantMessage::encode)
                .decoder(ForeignVariantMessage::decode)
                .consumerMainThread(ForeignVariantMessage::handle)
                .add();
    }

    /** Tells one player the claim of another mod's registered variant on a meteor (the client checks it again). */
    public static void sendForeignVariant(ServerPlayer player, int entityId, String claim) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ForeignVariantMessage(entityId, claim));
    }

    /** Entity id + another mod's claim string (at most {@link #MAX_CLAIM_LENGTH} characters). */
    public record ForeignVariantMessage(int entityId, String claim) {
        static void encode(ForeignVariantMessage m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeUtf(m.claim, MAX_CLAIM_LENGTH);
        }

        static ForeignVariantMessage decode(FriendlyByteBuf buf) {
            return new ForeignVariantMessage(buf.readVarInt(), buf.readUtf(MAX_CLAIM_LENGTH));
        }

        static void handle(ForeignVariantMessage m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.temog.notminelegacy.ohnomoremeteors.client.ClientMeteorVariants.applyForeign(m.entityId, m.claim));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Longest claim string message 3 carries. */
    public static final int MAX_CLAIM_LENGTH = 256;

    public static void sendVariant(ServerPlayer player, int entityId, MeteorVariant variant) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MeteorVariantMessage(entityId, variant.ordinal()));
    }

    /**
     * Tells the players near a Komit Kitty that a laser shot it down. The beam starts at the top of the laser block.
     * Sent with PacketDistributor.NEAR around the kitty, so it reaches players who see either end of the beam and
     * never asks the world for a chunk (TRACKING_CHUNK would call getChunkAt, which can load one).
     *
     * @param level    the level of the shot
     * @param laser    the laser block's position
     * @param target   where the kitty was shot
     * @param beam     false when the per-tick beam budget is used up: the client then draws only the flash and the pop
     * @param advanced true for the Advanced laser
     */
    public static void sendKittyShot(ServerLevel level, BlockPos laser, Vec3 target, boolean beam, boolean advanced) {
        double range = Config.KITTY_TRAIL_RANGE.get();
        byte flags = (byte) ((beam ? 1 : 0) | (advanced ? 2 : 0));
        KittyShotMessage message = new KittyShotMessage(
                laser.getX() + 0.5F, laser.getY() + 1.5F, laser.getZ() + 0.5F,
                (float) target.x, (float) target.y, (float) target.z, flags);
        CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(target.x, target.y, target.z, range, level.dimension())), message);
    }

    /** A sky-glow omen for one player (strength 0 fades it back). */
    public static void sendSkyOmen(ServerPlayer player, float strength, int rampTicks, SkyGlowKind kind) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new KittySkyMessage(strength, rampTicks, (byte) kind.ordinal()));
    }

    /** Target omen strength (0..1), ticks to reach it, SkyGlowKind ordinal. 6-9 bytes. */
    public record KittySkyMessage(float strength, int rampTicks, byte kind) {
        static void encode(KittySkyMessage m, FriendlyByteBuf buf) {
            buf.writeFloat(m.strength);
            buf.writeVarInt(m.rampTicks);
            buf.writeByte(m.kind);
        }

        static KittySkyMessage decode(FriendlyByteBuf buf) {
            return new KittySkyMessage(buf.readFloat(), buf.readVarInt(), buf.readByte());
        }

        static void handle(KittySkyMessage m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.temog.notminelegacy.ohnomoremeteors.client.SkyGlow.receiveOmen(m.strength, m.rampTicks, m.kind));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Entity id + variant ordinal. */
    public record MeteorVariantMessage(int entityId, int variant) {
        static void encode(MeteorVariantMessage m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeByte(m.variant);
        }

        static MeteorVariantMessage decode(FriendlyByteBuf buf) {
            return new MeteorVariantMessage(buf.readVarInt(), buf.readByte());
        }

        static void handle(MeteorVariantMessage m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.temog.notminelegacy.ohnomoremeteors.client.ClientMeteorVariants.apply(m.entityId, m.variant));
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * A laser shot at a kitty: where the beam starts (the laser top), where the kitty was, and flags
     * (bit 0 = draw the beam, bit 1 = Advanced laser). 25 bytes.
     */
    public record KittyShotMessage(float fx, float fy, float fz, float tx, float ty, float tz, byte flags) {
        static void encode(KittyShotMessage m, FriendlyByteBuf buf) {
            buf.writeFloat(m.fx);
            buf.writeFloat(m.fy);
            buf.writeFloat(m.fz);
            buf.writeFloat(m.tx);
            buf.writeFloat(m.ty);
            buf.writeFloat(m.tz);
            buf.writeByte(m.flags);
        }

        static KittyShotMessage decode(FriendlyByteBuf buf) {
            return new KittyShotMessage(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
        }

        static void handle(KittyShotMessage m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.temog.notminelegacy.ohnomoremeteors.client.KittyClientEffects.shot(
                            m.fx, m.fy, m.fz, m.tx, m.ty, m.tz, (m.flags & 1) != 0, (m.flags & 2) != 0));
            ctx.get().setPacketHandled(true);
        }
    }
}
