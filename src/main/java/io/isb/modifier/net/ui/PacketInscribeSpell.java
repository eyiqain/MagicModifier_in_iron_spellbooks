package io.isb.modifier.net.ui;

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
    private final int bookSlotIndex;

    // 只需传入目标书本的槽位，源物品默认从鼠标取
    public PacketInscribeSpell(int bookSlotIndex) {
        this.bookSlotIndex = bookSlotIndex;
    }

    public static void encode(PacketInscribeSpell msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.bookSlotIndex);
    }

    public static PacketInscribeSpell decode(FriendlyByteBuf buffer) {
        return new PacketInscribeSpell(buffer.readInt());
    }

    public static void handle(PacketInscribeSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 获取副手/主手的法术书
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;

                // 2. 获取鼠标上的物品 (核心修改点)
                ItemStack mouseStack = player.containerMenu.getCarried();
                if (mouseStack.isEmpty() || !(mouseStack.getItem() instanceof Scroll)) return;

                // 3. 提取数据
                ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                ISpellContainer scrollContainer = ISpellContainer.get(mouseStack);
                SpellData spellData = scrollContainer.getSpellAtIndex(0);

                // 4. 执行抄写逻辑
                if (bookContainer.addSpellAtIndex(spellData.getSpell(), spellData.getLevel(), msg.bookSlotIndex, false, bookStack)) {

                    // 保存书本 NBT
                    bookContainer.save(bookStack);

                    // 5. 消耗鼠标上的物品
                    mouseStack.shrink(1); // 数量减1
                    player.containerMenu.setCarried(mouseStack); // 更新服务端状态

                    // 6. 同步
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
