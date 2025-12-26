package io.isb.modifier.net.ui;

import io.isb.modifier.gui.SpellScreen;
import io.isb.modifier.gui.page.FunctionPage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncSynth {
    private final int slotIndex;
    private final ItemStack itemStack;

    public PacketSyncSynth(int slotIndex, ItemStack itemStack) {
        this.slotIndex = slotIndex;
        this.itemStack = itemStack;
    }

    public static void encode(PacketSyncSynth msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.slotIndex);
        buffer.writeItem(msg.itemStack);
    }

    public static PacketSyncSynth decode(FriendlyByteBuf buffer) {
        return new PacketSyncSynth(buffer.readInt(), buffer.readItem());
    }

    public static void handle(PacketSyncSynth msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端逻辑
            if (Minecraft.getInstance().screen instanceof SpellScreen screen) {
                // 我们需要一个方法能在 SpellScreen 里找到 FunctionPage
                // 这里假设 FunctionPage 是 activeRightWindow，或者你可以遍历查找
                if (screen.getActiveRightWindow() instanceof FunctionPage page) {

                    page.updateSynthSlot(msg.slotIndex, msg.itemStack);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
