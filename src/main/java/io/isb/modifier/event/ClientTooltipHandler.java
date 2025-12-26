package io.isb.modifier.event;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.api.AbstractModifier;
import io.isb.modifier.api.util.RuneContainerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// 注意：Tooltip 是客户端特性，所以用 Dist.CLIENT
@Mod.EventBusSubscriber(modid = MagicModifier.MODID, value = Dist.CLIENT)
public class ClientTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // 1. 检查这个物品是否有我们的符文 NBT
        if (RuneContainerHelper.hasRunes(stack)) {

            event.getToolTip().add(Component.empty()); // 加个空行
            event.getToolTip().add(Component.literal("Runes Socketed:").withStyle(ChatFormatting.GOLD));

            // 2. 遍历 0 到 5 号槽位
            for (int i = 0; i < RuneContainerHelper.MAX_SLOTS; i++) {
                AbstractModifier modifier = RuneContainerHelper.getRuneInSlot(stack, i);

                if (modifier != null) {
                    // 如果槽位有符文，显示符文名字
                    event.getToolTip().add(Component.literal(" [" + (i+1) + "] ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(modifier.getDisplayName())); // 显示修饰符的彩色名字
                } else {
                    // 如果槽位是空的，显示 [Empty]
                    event.getToolTip().add(Component.literal(" [" + (i+1) + "] ---")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
    }
}
