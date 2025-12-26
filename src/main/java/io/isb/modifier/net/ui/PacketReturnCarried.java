package io.isb.modifier.net.ui;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketReturnCarried {
    // 不需要参数，只需要一个信号
    public PacketReturnCarried() {}

    public static void encode(PacketReturnCarried msg, FriendlyByteBuf buffer) {}

    public static PacketReturnCarried decode(FriendlyByteBuf buffer) {
        return new PacketReturnCarried();
    }

    public static void handle(PacketReturnCarried msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 获取当前鼠标上的物品
                ItemStack carried = player.containerMenu.getCarried();
                //调试debug
                ISpellContainer container = ISpellContainer.get(carried);
                SpellData spellData = container.getSpellAtIndex(0);
                if(spellData != SpellData.EMPTY){
                    System.out.println("（归还这里) :法术： " + spellData.getSpell().getSpellName()+"等级:"+spellData.getLevel());
                }
                if (!carried.isEmpty()) {
                    // 2. 尝试放回玩家背包 (add 方法会自动寻找空位或堆叠)
                    boolean success = player.getInventory().add(carried);

                    if (!success) {
                        // 3. 兜底逻辑：如果背包满了放不回去，必须扔在脚下，绝对不能吞物品！
                        player.drop(carried, false);
                    }

                    // 4. 清空鼠标槽
                    player.containerMenu.setCarried(ItemStack.EMPTY);

                    // 5. 同步
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
