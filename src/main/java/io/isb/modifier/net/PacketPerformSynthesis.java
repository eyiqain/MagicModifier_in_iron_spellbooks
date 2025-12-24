package io.isb.modifier.net;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPerformSynthesis {
    private final int slot1;
    private final boolean isBook1;
    private final int slot2;
    private final boolean isBook2;

    public PacketPerformSynthesis(int slot1, boolean isBook1, int slot2, boolean isBook2) {
        this.slot1 = slot1;
        this.isBook1 = isBook1;
        this.slot2 = slot2;
        this.isBook2 = isBook2;
    }

    // 编码 (Client -> Server)
    public static void encode(PacketPerformSynthesis msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slot1);
        buf.writeBoolean(msg.isBook1);
        buf.writeInt(msg.slot2);
        buf.writeBoolean(msg.isBook2);
    }

    // 解码 (Server 接收)
    public static PacketPerformSynthesis decode(FriendlyByteBuf buf) {
        return new PacketPerformSynthesis(buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readBoolean());
    }

    // 业务逻辑
    public static void handle(PacketPerformSynthesis msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // === 验证逻辑开始 ===
            // 1. 获取两个输入源的法术数据
            SpellData spellData1 = getSpellDataFromSource(player, msg.slot1, msg.isBook1);
            SpellData spellData2 = getSpellDataFromSource(player, msg.slot2, msg.isBook2);

            // 2. 验证：必须非空
            if (spellData1 == null || spellData1.equals(SpellData.EMPTY) ||
                    spellData2 == null || spellData2.equals(SpellData.EMPTY)) {
                System.out.println("Synthesis Failed: One or both slots are empty.");
                return;
            }

            // 3. 验证：必须相同法术ID且相同等级
            if (!spellData1.getSpell().getSpellId().equals(spellData2.getSpell().getSpellId())) {
                System.out.println("Synthesis Failed: Spells do not match.");
                return;
            }
            if (spellData1.getLevel() != spellData2.getLevel()) {
                System.out.println("Synthesis Failed: Spell levels do not match.");
                return;
            }

            // 假设最大等级限制 (比如 10)，这里可以加判断
            // if (spellData1.getLevel() >= 10) return;

            // === 执行逻辑开始 ===

            // 4. 消耗材料
            boolean consumed1 = consumeItemFromSource(player, msg.slot1, msg.isBook1);
            boolean consumed2 = false;

            // 特殊情况处理：如果两个来源完全相同（同一个背包格子的堆叠物品）
            if (!msg.isBook1 && !msg.isBook2 && msg.slot1 == msg.slot2) {
                // 上一步已经拿走了一个，如果还要拿，就再次尝试拿
                consumed2 = consumeItemFromSource(player, msg.slot2, msg.isBook2);
            } else {
                // 正常拿第二个
                consumed2 = consumeItemFromSource(player, msg.slot2, msg.isBook2);
            }

            if (consumed1 && consumed2) {
                // 5. 生成产物：等级 + 1
                AbstractSpell resultSpell = spellData1.getSpell();
                int resultLevel = spellData1.getLevel() + 1;

                ItemStack scrollStack = new ItemStack(ItemRegistry.SCROLL.get());

                // 参考你的 PacketExtractSpell 里的写法，使用 createScrollContainer
                ISpellContainer.createScrollContainer(
                        resultSpell,
                        resultLevel,
                        scrollStack
                );

                // 6. 给予玩家
                if (!player.getInventory().add(scrollStack)) {
                    player.drop(scrollStack, false);
                }

                // 7. 刷新背包 (背包小连招)
                player.getInventory().setChanged();
                player.containerMenu.slotsChanged(player.getInventory());
                player.containerMenu.broadcastChanges();
                player.inventoryMenu.broadcastChanges();

                System.out.println("Synthesis Success: " + resultSpell.getSpellId() + " Lv." + resultLevel);
            } else {
                System.err.println("Synthesis Error: Failed to consume items properly.");
                // 在这里你可能需要做回滚逻辑，或者因为是原子操作失败通常不会发生，除非刷包
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // 辅助方法：获取 SpellData
    private static SpellData getSpellDataFromSource(ServerPlayer player, int slotIndex, boolean isBook) {
        if (slotIndex < 0) return SpellData.EMPTY;

        if (isBook) {
            ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
            if (bookStack != null && bookStack.getItem() instanceof SpellBook) {
                ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                if (slotIndex < bookContainer.getMaxSpellCount()) {
                    return bookContainer.getSpellAtIndex(slotIndex);
                }
            }
        } else {
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (!stack.isEmpty() && stack.getItem() instanceof Scroll) {
                ISpellContainer scrollContainer = ISpellContainer.get(stack);
                return scrollContainer.getSpellAtIndex(0);
            }
        }
        return SpellData.EMPTY;
    }

    // 辅助方法：消耗物品
    private static boolean consumeItemFromSource(ServerPlayer player, int slotIndex, boolean isBook) {
        if (slotIndex < 0) return false;

        if (isBook) {
            ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
            if (bookStack != null && bookStack.getItem() instanceof SpellBook) {
                ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                if (slotIndex < bookContainer.getMaxSpellCount()) {
                    // 从书里移除
                    bookContainer.removeSpellAtIndex(slotIndex, bookStack);
                    bookContainer.save(bookStack); // 必须保存
                    return true;
                }
            }
        } else {
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (!stack.isEmpty()) {
                // 从背包移除 1 个
                player.getInventory().removeItem(slotIndex, 1);
                return true;
            }
        }
        return false;
    }
}
