package io.isb.modifier.event;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.spell.IMagicChargeData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//同步玩家物品栏的充能数
@Mod.EventBusSubscriber(modid = MagicModifier.MODID) // 确保这里填对你的 MODID
public class ChargeSyncHandler {

    // 玩家登录游戏时
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        syncPlayerCharges(event.getEntity());
    }

    // 玩家重生时（死了复活，数据可能会重置，需要重发）
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncPlayerCharges(event.getEntity());
    }

    // 玩家切换维度时（有时候维度切换会导致客户端数据重载）
    @SubscribeEvent
    public static void onPlayerChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncPlayerCharges(event.getEntity());
    }

    // 统一的同步逻辑
    private static void syncPlayerCharges(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            // 获取 MagicData
            MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);

            // 强转接口并调用 syncAll
            if (magicData instanceof IMagicChargeData chargeData) {
                chargeData.eyi$syncAll(serverPlayer);
            }
        }
    }
}
