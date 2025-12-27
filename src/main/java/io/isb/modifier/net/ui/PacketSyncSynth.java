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
            // 只在客户端执行
            if (Minecraft.getInstance().screen instanceof SpellScreen screen) {

                // 1. 【核心】更新客户端 Menu 中的数据源
                // 这是“真值”。FunctionPage 渲染时应该从这里读取数据。
                // 这样即使你现在处于 InstructionPage，后台数据也会被更新。
                if (screen.getMenu() != null) {
                    // 确保索引不越界 (0, 1, 2)
                    if (msg.slotIndex >= 0 && msg.slotIndex < screen.getMenu().synthContainer.getContainerSize()) {
                        screen.getMenu().synthContainer.setItem(msg.slotIndex, msg.itemStack);
                    }
                }

                // 2. 【视觉】如果当前正好是 FunctionPage，通知它立即刷新
                // 这一步是为了触发可能的动画、粒子效果或者缓存更新
                if (screen.getActiveRightWindow() instanceof FunctionPage page) {
                    page.updateSynthSlot(msg.slotIndex, msg.itemStack);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
