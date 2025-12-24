package io.isb.modifier.event;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.client.ClientChargeData;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicModifier.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.PlayerTickEvent event) {
        // 只在客户端的主玩家身上运行，且只在 Phase.END 运行一次
        if (event.phase == TickEvent.Phase.END && event.side.isClient()) {
            if (event.player == Minecraft.getInstance().player) {
                ClientChargeData.clientTick(event.player);
            }
        }
    }
}
