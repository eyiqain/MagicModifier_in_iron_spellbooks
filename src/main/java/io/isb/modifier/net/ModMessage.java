package io.isb.modifier.net;

import io.isb.modifier.MagicModifier; // 请确认你的主类名
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessage {
    private static SimpleChannel NETWORK;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        NETWORK = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MagicModifier.MODID, "main"),
                () -> "1.0",
                s -> true,
                s -> true
        );

        // === 1. 同步法术充能 (Server -> Client) ===
        NETWORK.messageBuilder(PacketSyncCharge.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PacketSyncCharge::encode)
                .decoder(PacketSyncCharge::decode)
                .consumerMainThread(PacketSyncCharge::handle)
                .add();
    }

    public static <MSG> void sendToAll(MSG message) {
        NETWORK.send(PacketDistributor.ALL.noArg(), message);
    }

    public static <MSG> void sendToServer(MSG Message) {
        NETWORK.sendToServer(Message);
    }

    public static <MSG> void sendToPlayer(MSG Message, ServerPlayer player) {
        NETWORK.send(PacketDistributor.PLAYER.with(() -> player), Message);
    }
}
