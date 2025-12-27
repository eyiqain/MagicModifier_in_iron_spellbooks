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

/**
 * 现在的职责非常单一：只负责“点击合成按钮”后的逻辑
 * 物品的放入和取出，全部交给 PacketUnifiedSwap
 */
public class PacketManageSynth {

    // 不需要 actionType 了，因为只有“合成”这一件事
    // 不需要 slotIndex 了，因为合成总是涉及固定槽位 (0,1 -> 2)

    public PacketManageSynth() {
    }

    public static void encode(PacketManageSynth msg, FriendlyByteBuf buffer) {
        // 空包，不需要参数
    }

    public static PacketManageSynth decode(FriendlyByteBuf buffer) {
        return new PacketManageSynth();
    }

    public static void handle(PacketManageSynth msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof SpellMenu menu) {
                SimpleContainer synth = menu.synthContainer;

                // === 执行合成 (Slot 0 + Slot 1 -> Slot 2) ===
                ItemStack s1 = synth.getItem(0);
                ItemStack s2 = synth.getItem(1);
                ItemStack out = synth.getItem(2);

                if (!s1.isEmpty() && !s2.isEmpty() && out.isEmpty()) {
                    ISpellContainer c1 = ISpellContainer.get(s1);
                    ISpellContainer c2 = ISpellContainer.get(s2);
                    SpellData d1 = c1.getSpellAtIndex(0);
                    SpellData d2 = c2.getSpellAtIndex(0);

                    // 示例合成逻辑：相同法术且相同等级 -> 升级
                    if (d1.getSpell().equals(d2.getSpell()) && d1.getLevel() == d2.getLevel()) {

                        // 1. 消耗原料
                        synth.setItem(0, ItemStack.EMPTY);
                        synth.setItem(1, ItemStack.EMPTY);

                        // 2. 生成产物 (示例：等级+1)
                        int newLevel = d1.getLevel() + 1;
                        ItemStack resultStack = new ItemStack(ItemRegistry.SCROLL.get());
                        SpellContainer resultContainer = new SpellContainer(1, false, false);
                        resultContainer.addSpellAtIndex(d1.getSpell(), newLevel, 0, true, resultStack);

                        // 3. 放入输出槽
                        synth.setItem(2, resultStack);

                        // 4. 同步给客户端
                        ModMessage.sendToPlayer(new PacketSyncSynth(0, ItemStack.EMPTY), player);
                        ModMessage.sendToPlayer(new PacketSyncSynth(1, ItemStack.EMPTY), player);
                        ModMessage.sendToPlayer(new PacketSyncSynth(2, resultStack), player);

                        // 5. 播放一个合成成功的音效（可选）
                        // player.playSound(SoundEvents.ANVIL_USE, 1f, 1f);
                    }
                }

                // 确保数据保存
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
