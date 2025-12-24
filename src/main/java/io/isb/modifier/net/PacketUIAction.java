package io.isb.modifier.net;

import io.isb.modifier.gui.SpellMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUIAction {
    private final int actionId; // 0=升级, 1=销毁, etc.

    public PacketUIAction(int actionId) {
        this.actionId = actionId;
    }

    public static void encode(PacketUIAction msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.actionId);
    }

    public static PacketUIAction decode(FriendlyByteBuf buffer) {
        return new PacketUIAction(buffer.readInt());
    }

    public static void handle(PacketUIAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof SpellMenu menu) {
                // 安全检查：确保玩家打开的确实是这个 Menu
                System.out.println("收到 UI 操作: " + msg.actionId);
                // menu.handleMyCustomAction(msg.actionId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
