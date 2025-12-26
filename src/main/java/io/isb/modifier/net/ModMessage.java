package io.isb.modifier.net;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.net.ui.*;
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

        // === 已有的包 ===
        NETWORK.messageBuilder(PacketSyncCharge.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PacketSyncCharge::encode) // 注意：你的旧包可能叫 encode，保持不动
                .decoder(PacketSyncCharge::decode)
                .consumerMainThread(PacketSyncCharge::handle)
                .add();

        NETWORK.messageBuilder(PacketUIAction.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketUIAction::encode)
                .decoder(PacketUIAction::decode)
                .consumerMainThread(PacketUIAction::handle)
                .add();



        // 【新增】配置更新包 (修复报错的关键)
        // 注意：PacketUpdateConfig 用的是 toBytes 和 构造函数(new)，所以这里写法略有不同
        NETWORK.messageBuilder(PacketUpdateConfig.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketUpdateConfig::toBytes) // 对应 PacketUpdateConfig 里的 public void toBytes(...)
                .decoder(PacketUpdateConfig::new)    // 对应 PacketUpdateConfig 里的 public PacketUpdateConfig(buf)
                .consumerMainThread(PacketUpdateConfig::handle)
                .add();
        // === 新 UI 交互包 (全链路同步) ===
        NETWORK.messageBuilder(PacketPickupSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketPickupSpell::encode)
                .decoder(PacketPickupSpell::decode)
                .consumerMainThread(PacketPickupSpell::handle)
                .add();
        NETWORK.messageBuilder(PacketReturnCarried.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketReturnCarried::encode)
                .decoder(PacketReturnCarried::decode)
                .consumerMainThread(PacketReturnCarried::handle)
                .add();
        // === 刻印/合成相关包 ===
        NETWORK.messageBuilder(PacketInscribeSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketInscribeSpell::encode)
                .decoder(PacketInscribeSpell::decode)
                .consumerMainThread(PacketInscribeSpell::handle)
                .add();
        //=======合成管理=======
        NETWORK.messageBuilder(PacketManageSynth.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketManageSynth::encode)
                .decoder(PacketManageSynth::decode)
                .consumerMainThread(PacketManageSynth::handle)
                .add();
        NETWORK.messageBuilder(PacketExtractSpell.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketExtractSpell::encode)
                .decoder(PacketExtractSpell::decode)
                .consumerMainThread(PacketExtractSpell::handle)
                .add();
        NETWORK.messageBuilder(PacketSyncSynth.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PacketSyncSynth::encode)
                .decoder(PacketSyncSynth::decode)
                .consumerMainThread(PacketSyncSynth::handle)
                .add();
        // === 新增：直接提取到背包的包 ===
        NETWORK.messageBuilder(PacketExtractToInv.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketExtractToInv::encode)
                .decoder(PacketExtractToInv::decode)
                .consumerMainThread(PacketExtractToInv::handle)
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
