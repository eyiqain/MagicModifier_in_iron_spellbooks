package io.isb.modifier.net;

import io.isb.modifier.gui.SpellMenu;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketInscribeSpell {
    private final int inventorySlotIndex;
    private final int bookSlotIndex;

    public PacketInscribeSpell(int inventorySlotIndex, int bookSlotIndex) {
        this.inventorySlotIndex = inventorySlotIndex;
        this.bookSlotIndex = bookSlotIndex;
    }

    public static void encode(PacketInscribeSpell msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.inventorySlotIndex);
        buffer.writeInt(msg.bookSlotIndex);
    }

    public static PacketInscribeSpell decode(FriendlyByteBuf buffer) {
        return new PacketInscribeSpell(buffer.readInt(), buffer.readInt());
    }

    public static void handle(PacketInscribeSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;

                ItemStack scrollStack = player.getInventory().getItem(msg.inventorySlotIndex);
                if (!(scrollStack.getItem() instanceof Scroll)) return;

                ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                ISpellContainer scrollContainer = ISpellContainer.get(scrollStack);
                SpellData spellData = scrollContainer.getSpellAtIndex(0);

                if (bookContainer.addSpellAtIndex(spellData.getSpell(), spellData.getLevel(), msg.bookSlotIndex, false, bookStack)) {

                    // 1) 把书的法术写回 NBT
                    bookContainer.save(bookStack);

                    // 2) 消耗卷轴
                    player.getInventory().removeItem(msg.inventorySlotIndex, 1);

                    // 3) 标记背包变更 + 强制容器同步
                    player.getInventory().setChanged();
                    player.containerMenu.slotsChanged(player.getInventory());
                    player.containerMenu.broadcastChanges();
                    player.inventoryMenu.broadcastChanges(); // 可选但很有用
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
