package com.temog.notminelegacy.ohnomoremeteors.network;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * The add-on's network channel. One message so far: which variant a falling Oh My, Meteors! meteor is, sent to a
 * player when the meteor comes into their view (the variant claim lives in server-only entity data), so the client
 * can draw the variant's body texture and particles.
 */
public final class ModNetwork {

    private static final String PROTOCOL = "1";
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
    }

    public static void sendVariant(ServerPlayer player, int entityId, MeteorVariant variant) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MeteorVariantMessage(entityId, variant.ordinal()));
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
}
