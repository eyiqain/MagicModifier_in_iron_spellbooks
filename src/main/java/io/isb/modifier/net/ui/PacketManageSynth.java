package io.isb.modifier.net.ui;

import io.isb.modifier.gui.SpellMenu;
import io.isb.modifier.net.ModMessage;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketManageSynth {
    // 动作类型：0=放入, 1=取出, 2=执行合成
    private final int actionType;
    private final int slotIndex; // 针对放入/取出 (0, 1, 2)

    public PacketManageSynth(int actionType, int slotIndex) {
        this.actionType = actionType;
        this.slotIndex = slotIndex;
    }

    public static void encode(PacketManageSynth msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.actionType);
        buffer.writeInt(msg.slotIndex);
    }

    public static PacketManageSynth decode(FriendlyByteBuf buffer) {
        return new PacketManageSynth(buffer.readInt(), buffer.readInt());
    }

    public static void handle(PacketManageSynth msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof SpellMenu menu) {
                SimpleContainer synth = menu.synthContainer;
                ItemStack carried = player.containerMenu.getCarried();//服务端的

                switch (msg.actionType) {
                    case 0: // === 放入 (Mouse -> Slot) ===
                        if (!carried.isEmpty() && synth.getItem(msg.slotIndex).isEmpty()) {
                            // 校验：是否是卷轴 (仅针对输入槽 0, 1)
                            if (msg.slotIndex < 2 && !(carried.getItem() instanceof Scroll)) break;

                            // 执行移动
                            synth.setItem(msg.slotIndex, carried.split(1)); // 鼠标拿到合成槽
                            // 🔥🔥🔥 必须添加：同步这个槽位（变空了）给客户端 🔥🔥🔥
                            //ModMessage.sendToPlayer(new PacketSyncSynth(msg.slotIndex, ItemStack.EMPTY), player);
                        }
                        break;

                    case 1: // === 取出 (Slot -> Mouse) ===
                        if (carried.isEmpty() && !synth.getItem(msg.slotIndex).isEmpty()) {
                            ItemStack itemInSlot = synth.getItem(msg.slotIndex);
                            player.containerMenu.setCarried(itemInSlot); // 放到鼠标
                            synth.setItem(msg.slotIndex, ItemStack.EMPTY); // 清空槽位
                            // 🔥🔥🔥 必须添加：同步这个槽位（变空了）给客户端 🔥🔥🔥
                            //ModMessage.sendToPlayer(new PacketSyncSynth(msg.slotIndex, ItemStack.EMPTY), player);
                        }
                        break;

                    case 2: // === 执行合成 (Slot 0 + Slot 1 -> Slot 2) ===
                        ItemStack s1 = synth.getItem(0);
                        ItemStack s2 = synth.getItem(1);
                        ItemStack out = synth.getItem(2);

                        if (!s1.isEmpty() && !s2.isEmpty() && out.isEmpty()) {
                            // 这里写你的具体合成逻辑，例如：
                            ISpellContainer c1 = ISpellContainer.get(s1);
                            ISpellContainer c2 = ISpellContainer.get(s2);
                            SpellData d1 = c1.getSpellAtIndex(0);
                            SpellData d2 = c2.getSpellAtIndex(0);

                            // 示例：相同法术且相同等级 -> 升级
                            if (d1.getSpell().equals(d2.getSpell()) && d1.getLevel() == d2.getLevel()) {
                                // 消耗原料
                                synth.setItem(0, ItemStack.EMPTY);
                                synth.setItem(1, ItemStack.EMPTY);

                                // 生成产物 (示例：等级+1)
                                int newLevel = d1.getLevel() + 1;
                                ItemStack resultStack = new ItemStack(ItemRegistry.SCROLL.get());
                                SpellContainer resultContainer = new SpellContainer(1, false, false);
                                resultContainer.addSpellAtIndex(d1.getSpell(), d1.getLevel()+1, 0, true, resultStack);
                                // 4. 生成卷轴并放到鼠标上
                                synth.setItem(2, resultStack);
                                // 🔥🔥🔥 关键：发送同步包给玩家 🔥🔥🔥
                                ModMessage.sendToPlayer(new PacketSyncSynth(0, ItemStack.EMPTY), player);
                                ModMessage.sendToPlayer(new PacketSyncSynth(1, ItemStack.EMPTY), player);
                                ModMessage.sendToPlayer(new PacketSyncSynth(2, resultStack), player);
                            }
                        }
                        break;
                }

                // 极度重要：通知客户端同步变更！
                // 简单粗暴的方法：利用 broadcastChanges 或发送自定义同步包
                // 这里为了省事，因为我们操作了 Carried，调用这个通常会触发 UpdateInventory
                player.containerMenu.broadcastChanges();


            }
        });
        ctx.get().setPacketHandled(true);
    }
}
