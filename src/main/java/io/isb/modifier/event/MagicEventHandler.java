package io.isb.modifier.event;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.api.AbstractModifier;
import io.isb.modifier.api.util.RuneContainerHelper;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicModifier.MODID)
public class MagicEventHandler {

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        // [调试 1] 确认事件本身触发了没有
        System.out.println("DEBUG: 监听到 SpellOnCastEvent！");

        Player player = event.getEntity();
        if (player == null) return;

        // 如果是客户端，有时候不发消息是正常的，我们主要看服务端
        if (player.level().isClientSide) {
            System.out.println("DEBUG: 这是客户端触发");
        } else {
            System.out.println("DEBUG: 这是服务端触发");
        }

        ItemStack castingStack = MagicData.getPlayerMagicData(player).getPlayerCastingItem();

        // [调试 2] 看看手里拿的是不是我们塞了 NBT 的东西
        System.out.println("DEBUG: 施法物品是 " + castingStack.getItem().getName(castingStack).getString());
        System.out.println("DEBUG: 是否有符文 NBT? " + RuneContainerHelper.hasRunes(castingStack));

        if (RuneContainerHelper.hasRunes(castingStack)) {
            for (int i = 0; i < RuneContainerHelper.MAX_SLOTS; i++) {
                AbstractModifier modifier = RuneContainerHelper.getRuneInSlot(castingStack, i);

                // [调试 3] 看看有没有读出具体的修饰符对象
                if (modifier != null) {
                    System.out.println("DEBUG: 槽位 " + i + " 找到了修饰符: " + modifier.getModifierId());
                    modifier.onSpellCast(event);
                } else {
                    // 如果 NBT 有东西但这里是 null，说明 ID 没对上，注册表里没找到
                    // System.out.println("DEBUG: 槽位 " + i + " 是空的或读取失败");
                }
            }
        }
    }
}
