package io.isb.modifier.net;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSwapBookSpell {
    private final int fromIndex;
    private final int toIndex;

    public PacketSwapBookSpell(int fromIndex, int toIndex) {
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    public static void encode(PacketSwapBookSpell msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.fromIndex);
        buf.writeInt(msg.toIndex);
    }

    public static PacketSwapBookSpell decode(FriendlyByteBuf buf) {
        return new PacketSwapBookSpell(buf.readInt(), buf.readInt());
    }

    public static void handle(PacketSwapBookSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
            if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;

            ISpellContainer book = ISpellContainer.get(bookStack);
            int max = book.getMaxSpellCount();

            if (msg.fromIndex < 0 || msg.fromIndex >= max) return;
            if (msg.toIndex < 0 || msg.toIndex >= max) return;

            SpellData from = book.getSpellAtIndex(msg.fromIndex);
            SpellData to = book.getSpellAtIndex(msg.toIndex);
            if (from == null || from == SpellData.EMPTY) return;

            // 1) 先清空两个槽位（避免 addSpellAtIndex 失败）
            book.removeSpellAtIndex(msg.fromIndex, bookStack);
            if (to != null && to != SpellData.EMPTY) {
                book.removeSpellAtIndex(msg.toIndex, bookStack);
            }

            // 2) 放入目标槽位（移动）
            book.addSpellAtIndex(from.getSpell(), from.getLevel(), msg.toIndex, false, bookStack);

            // 3) 如果目标原本有东西 => 放回原槽位（交换）
            if (to != null && to != SpellData.EMPTY) {
                book.addSpellAtIndex(to.getSpell(), to.getLevel(), msg.fromIndex, false, bookStack);
            }

            // 4) 保存并同步
            book.save(bookStack);
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
