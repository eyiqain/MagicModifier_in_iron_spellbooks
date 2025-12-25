package io.isb.modifier.mixin.charge;

import io.isb.modifier.config.IEyiConfigParams;
import io.isb.modifier.spell.IChargedSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSpell.class)
public class AbstractSpellMixin implements IChargedSpell {

    // 1. 伤害 (通常只有一个入口，维持原样)
    @Inject(method = "getSpellPower", at = @At("RETURN"), cancellable = true, remap = false)
    private void eyi$modifySpellPower(int spellLevel, Entity sourceEntity, CallbackInfoReturnable<Float> cir) {
        float originalPower = cir.getReturnValue();
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);
        if (config instanceof IEyiConfigParams eyiParams) {
            double multiplier = eyiParams.eyi$getPowerMultiplier();
            if (Math.abs(multiplier - 1.0) > 0.001) {
                cir.setReturnValue(originalPower * (float) multiplier);
            }
        }
    }

    // 2. 冷却 (冷却通常没有嵌套调用，维持原样)
    @Inject(method = "getSpellCooldown", at = @At("RETURN"), cancellable = true, remap = false)
    private void eyi$modifyCooldown(CallbackInfoReturnable<Integer> cir) {
        int originalCooldown = cir.getReturnValue();
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);
        if (config instanceof IEyiConfigParams eyiParams) {
            double multiplier = eyiParams.eyi$getCooldownMultiplier();
            if (Math.abs(multiplier - 1.0) > 0.001) {
                cir.setReturnValue((int) (originalCooldown * multiplier));
            }
        }
    }

    // 3. 🔥 法力消耗 (修复关键点)
    // ⚠️ 我们强制指定方法签名 `(I)I` 即 `int getManaCost(int level)`
    // 这样只会在计算“基础耗蓝”时乘一次，不会在“带实体耗蓝”时重复乘。
    @Inject(
            method = "getManaCost(I)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void eyi$modifyManaCost(int level, CallbackInfoReturnable<Integer> cir) {
        int originalMana = cir.getReturnValue();
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);
        if (config instanceof IEyiConfigParams eyiParams) {
            double multiplier = eyiParams.eyi$getManaMultiplier();

            // 只有当倍率不是 1.0 时才运算
            if (Math.abs(multiplier - 1.0) > 0.001) {
                cir.setReturnValue((int) (originalMana * multiplier));
            }
        }
    }

    // === IChargedSpell 接口实现 ===
    @Override
    public int eyi$getMaxCharges() {
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);
        if (config instanceof IEyiConfigParams eyiParams) { return eyiParams.eyi$getMaxCharges(); }
        return 1;
    }

    @Override
    public float eyi$getChargeCooldownMultiplier() {
        return 0;
    }

    @Override
    public double eyi$getChargeRatio() {
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);
        if (config instanceof IEyiConfigParams eyiParams) { return eyiParams.eyi$getChargeRatio(); }
        return 0.5;
    }
}
