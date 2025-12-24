package io.isb.modifier.mixin.charge;

import io.isb.modifier.spell.IChargedSpell;
import io.isb.modifier.spell.IMagicChargeData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MagicManager.class)
public class MagicManagerMixin {

    @ModifyVariable(
            method = "addCooldown",
            at = @At(value = "STORE"), // 这里拦截的是 effectiveCooldown 变量被存储/使用的时候
            ordinal = 0,               // 拦截方法里第一个 int 类型的局部变量（如果不准，可能需要更精确的定位，但既然你代码跑通了说明是对的）
            remap = false
    )
    private int eyi$modifyCooldown(int effectiveCooldown, ServerPlayer serverPlayer, AbstractSpell spell, CastSource castSource) {

        if (spell instanceof IChargedSpell chargedSpell) {
            int maxCharges = chargedSpell.eyi$getMaxCharges();

            if (maxCharges > 1) {
                MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                IMagicChargeData chargeData = (IMagicChargeData) (Object) magicData;

                // 1. 获取施法前的积攒量
                int storedTicks = chargeData.eyi$getAccumulatedTicks(spell.getSpellId());

                // 2. 执行核心计算（扣除充能，算出新冷却）
                // 这步操作已经更新了服务端的充能数据
                int newCooldown = chargeData.eyi$calculateAndConsumeCooldown(
                        spell.getSpellId(),
                        effectiveCooldown,
                        maxCharges,
                        chargedSpell.eyi$getChargeCooldownMultiplier()
                );

                // === 【新增】 网络同步逻辑 ===
                // 既然服务端数据刚更新了，马上告诉客户端
                chargeData.eyi$syncAfterCast(
                        serverPlayer,
                        spell.getSpellId(),
                        effectiveCooldown // 传入基础冷却时间用于计算层数
                );

                return newCooldown;
            }
        }
        return effectiveCooldown;
    }
}
