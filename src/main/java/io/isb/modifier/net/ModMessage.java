package io.isb.modifier.net;

import io.isb.modifier.MagicModifier;
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

        // 已有的包...
        NETWORK.messageBuilder(PacketSyncCharge.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PacketSyncCharge::encode)
                .decoder(PacketSyncCharge::decode)
                .consumerMainThread(PacketSyncCharge::handle)
                .add();
        NETWORK.messageBuilder(PacketUIAction.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketUIAction::encode)
                .decoder(PacketUIAction::decode)
                .consumerMainThread(PacketUIAction::handle)
                .add();

        // === 新增：法术刻印包 (Client -> Server) ===
        NETWORK.messageBuilder(PacketInscribeSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketInscribeSpell::encode)
                .decoder(PacketInscribeSpell::decode)
                .consumerMainThread(PacketInscribeSpell::handle)
                .add();
        NETWORK.messageBuilder(PacketExtractSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketExtractSpell::encode)
                .decoder(PacketExtractSpell::decode)
                .consumerMainThread(PacketExtractSpell::handle)
                .add();
        NETWORK.messageBuilder(PacketSwapBookSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketSwapBookSpell::encode)
                .decoder(PacketSwapBookSpell::decode)
                .consumerMainThread(PacketSwapBookSpell::handle)
                .add();
        NETWORK.messageBuilder(PacketPerformSynthesis.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketPerformSynthesis::encode)
                .decoder(PacketPerformSynthesis::decode)
                .consumerMainThread(PacketPerformSynthesis::handle)
                .add();
    }

    // ... 发送方法保持不变 ...
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
