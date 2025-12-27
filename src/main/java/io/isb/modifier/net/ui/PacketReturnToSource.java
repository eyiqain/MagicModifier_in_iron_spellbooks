package io.isb.modifier.net.ui;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketReturnToSource {

    // 用 byte 更省：0=BOOK, 1=SYNTH
    public static final byte TYPE_BOOK = 0;
    public static final byte TYPE_SYNTH = 1;

    private final byte sourceType;
    private final int sourceIndex;

    public PacketReturnToSource(byte sourceType, int sourceIndex) {
        this.sourceType = sourceType;
        this.sourceIndex = sourceIndex;
    }

    public static void encode(PacketReturnToSource msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.sourceType);
        buf.writeInt(msg.sourceIndex);
    }

    public static PacketReturnToSource decode(FriendlyByteBuf buf) {
        return new PacketReturnToSource(buf.readByte(), buf.readInt());
    }

    public static void handle(PacketReturnToSource msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty()) return;

            boolean handled = false;

            // ====== BOOK：把卷轴法术写回书槽 ======
            if (msg.sourceType == TYPE_BOOK) {
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                if (bookStack != null && bookStack.getItem() instanceof SpellBook && carried.getItem() instanceof Scroll) {

                    ISpellContainer book = ISpellContainer.get(bookStack);
                    int max = book.getMaxSpellCount();
                    if (msg.sourceIndex >= 0 && msg.sourceIndex < max) {

                        // 取卷轴里的 SpellData
                        ISpellContainer scrollC = ISpellContainer.get(carried);
                        SpellData sd = scrollC.getSpellAtIndex(0);

                        if (sd != SpellData.EMPTY) {
                            // 允许覆盖回原位：先清空目标槽（防止 add 失败）
                            SpellData existing = book.getSpellAtIndex(msg.sourceIndex);
                            if (existing != SpellData.EMPTY) {
                                book.removeSpellAtIndex(msg.sourceIndex, bookStack);
                            }

                            book.addSpellAtIndex(sd.getSpell(), sd.getLevel(), msg.sourceIndex, true, bookStack);
                            book.save(bookStack);

                            // 清空鼠标（归还不应留下卷轴）
                            carried.shrink(1);
                            player.containerMenu.setCarried(carried);

                            handled = true;
                        }
                    }
                }
            }

            // ====== SYNTH：把 carried 放回合成槽 ======
            // 注意：你现有 synth 是你自定义容器逻辑（PacketManageSynth/服务器端存储在哪）。
            // 这里最稳的做法是：直接复用你服务端的“从 carried 插入 synth 槽”的那段逻辑，
            // 也就是在 PacketManageSynth.handle 里抽一个静态方法出来复用。
            // 所以这里先留一个“转发到 PacketManageSynth 的服务端逻辑”的建议：
            if (!handled && msg.sourceType == TYPE_SYNTH) {
                // 建议：新增 PacketManageSynth 的 mode=RETURN 或者直接在服务端调用同一个插入函数
                // handled = SynthServerLogic.tryInsertFromCarried(player, msg.sourceIndex);
            }

            // 最后同步
            if (handled) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
