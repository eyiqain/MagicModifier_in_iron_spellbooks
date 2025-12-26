package io.isb.modifier.net.ui;

import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPickupSpell {
    private final int slotIndex;

    public PacketPickupSpell(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public static void encode(PacketPickupSpell msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.slotIndex);
    }

    public static PacketPickupSpell decode(FriendlyByteBuf buffer) {
        return new PacketPickupSpell(buffer.readInt());
    }

    public static void handle(PacketPickupSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 安全检查：确保玩家鼠标上是空的，防止覆盖已有物品
                if (!player.containerMenu.getCarried().isEmpty()) {
                    return;
                }

                // 2. 获取背包中的源物品
                ItemStack stackInSlot = player.getInventory().getItem(msg.slotIndex);
                // 3. 执行“拿取”动作 (拆分 1 个出来)
                // split(1) 会自动减少 stackInSlot 的数量
                ItemStack toHold = stackInSlot.split(1);

                // 4. 设置到服务端鼠标槽
                player.containerMenu.setCarried(toHold);

                // 5. 极其重要：通知所有监听者同步库存变化
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
