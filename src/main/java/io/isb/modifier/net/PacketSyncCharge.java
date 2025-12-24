package io.isb.modifier.net;

import io.isb.modifier.client.ClientChargeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketSyncCharge {
    private final String spellId;
    private final int charges;
    private final int accumulatedTicks; // 新增：当前积攒的tick数

    public PacketSyncCharge(String spellId, int charges, int accumulatedTicks) {
        this.spellId = spellId;
        this.charges = charges;
        this.accumulatedTicks = accumulatedTicks;
    }

    public static void encode(PacketSyncCharge msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.spellId);
        buffer.writeInt(msg.charges);
        buffer.writeInt(msg.accumulatedTicks); // 写入
    }

    public static PacketSyncCharge decode(FriendlyByteBuf buffer) {
        return new PacketSyncCharge(
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readInt() // 读取
        );
    }

    public static void handle(PacketSyncCharge msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 更新客户端数据，传入两个 int
            ClientChargeData.setChargeData(msg.spellId, msg.charges, msg.accumulatedTicks);
        });
        ctx.get().setPacketHandled(true);
    }
}
