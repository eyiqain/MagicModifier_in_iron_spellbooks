package io.isb.modifier.net.ui;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketExtractToInv {
    private final int bookSlotIndex;

    public PacketExtractToInv(int bookSlotIndex) {
        this.bookSlotIndex = bookSlotIndex;
    }

    public static void encode(PacketExtractToInv msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.bookSlotIndex);
    }

    public static PacketExtractToInv decode(FriendlyByteBuf buffer) {
        return new PacketExtractToInv(buffer.readInt());
    }

    public static void handle(PacketExtractToInv msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 获取法术书
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);

                // 2. 校验：手里必须是法术书 (这里不需要校验鼠标是否为空，因为我们直接放背包)
                if (bookStack != null && bookStack.getItem() instanceof SpellBook) {
                    ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                    SpellData spellData = bookContainer.getSpellAtIndex(msg.bookSlotIndex);

                    if (spellData != SpellData.EMPTY) {
                        // 3. 从书里移除法术
                        bookContainer.removeSpellAtIndex(msg.bookSlotIndex, bookStack);
                        bookContainer.save(bookStack);

                        // 4. 生成卷轴并放到鼠标上
                        ItemStack scrollStack = new ItemStack(ItemRegistry.SCROLL.get());
                        // 🔥 关键：不要用 ISpellContainer.get()，直接 new 一个正确初始化的 SpellContainer
                        SpellContainer scrollContainer = new SpellContainer(1, false, false);
                        scrollContainer.addSpellAtIndex(spellData.getSpell(), spellData.getLevel(), 0, true, scrollStack);

                        System.out.println("scrollStack.getTag() = " + scrollStack.getTag());


                        System.out.println("服务端: 已提取法术到背包: " + spellData.getSpell().getSpellName());

                        // 5. 🔥 核心区别：尝试放入玩家背包
                        if (!player.getInventory().add(scrollStack)) {
                            // 6. 兜底：如果背包满了，扔在脚下，防止物品丢失
                            player.drop(scrollStack, false);
                        }

                        // 7. 同步库存变化（书本变了，背包也变了）
                        player.getInventory().setChanged(); // add方法通常会自动标记
                        player.containerMenu.broadcastChanges();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
