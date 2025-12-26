package io.isb.modifier.net.ui;

import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.ui.PacketReturnCarried;
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

public class PacketExtractSpell {
    private final int bookSlotIndex;

    public PacketExtractSpell(int bookSlotIndex) {
        this.bookSlotIndex = bookSlotIndex;
    }

    public static void encode(PacketExtractSpell msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.bookSlotIndex);
    }

    public static PacketExtractSpell decode(FriendlyByteBuf buffer) {
        return new PacketExtractSpell(buffer.readInt());
    }

    public static void handle(PacketExtractSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 获取法术书
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                // 2. 确保鼠标是空的 (防止覆盖物品)
                if (bookStack != null && bookStack.getItem() instanceof SpellBook && player.containerMenu.getCarried().isEmpty()) {
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
//
//                        // 4. 生成卷轴并放到鼠标上
//                        ItemStack scrollStack = new ItemStack(ItemRegistry.SCROLL.get());
//                        ISpellContainer scrollContainer = ISpellContainer.get(scrollStack);
//                        // 🔥 先初始化 maxSpells
//                        scrollContainer.setMaxSpellCount(1);
//                        scrollContainer.save(scrollStack);//这个不加就是null
//                        scrollContainer.addSpellAtIndex(spellData.getSpell(), spellData.getLevel(), 0, true, scrollStack);

                        System.out.println("服务端（包内） :法术： " + spellData.getSpell().getSpellName()+"等级:"+spellData.getLevel());
                        player.containerMenu.setCarried(scrollStack);

                        //ModMessage.sendToServer(new PacketReturnCarried());

                        // 5. 同步
                        player.containerMenu.broadcastChanges();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
