package io.isb.modifier.mixin;

import io.isb.modifier.spell.IMagicChargeData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    // 在玩家每一帧逻辑执行完后，顺便处理一下魔法充能
    @Inject(method = "tick", at = @At("TAIL"))
    private void eyi$onPlayerTick(CallbackInfo ci) {
        // 强转 this 为 ServerPlayer
        ServerPlayer player = (ServerPlayer) (Object) this;

        // 获取该玩家的 MagicData
        // (Iron's Spells 内部做了缓存，这里获取通常很快)
        MagicData magicData = MagicData.getPlayerMagicData(player);

        // 驱动我们在 MagicDataMixin 里写的那个循环
        ((IMagicChargeData) (Object) magicData).eyi$tickAccumulate(player);
    }
}
