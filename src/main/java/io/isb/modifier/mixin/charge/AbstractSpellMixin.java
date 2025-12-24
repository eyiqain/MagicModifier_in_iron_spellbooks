package io.isb.modifier.mixin.charge;

import io.isb.modifier.config.IEyiConfigParams;
import io.isb.modifier.spell.IChargedSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractSpell.class)
public class AbstractSpellMixin implements IChargedSpell {
    //作用：运行时。法术逻辑需要数据时，去 Config 里查
    @Override
    public int eyi$getMaxCharges() {
        // ISB 标准写法：从 ServerConfigs 获取当前法术的 Config 对象
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);

        // 通过接口读取值
        if (config instanceof IEyiConfigParams eyiParams) {
            return eyiParams.eyi$getMaxCharges();
        }
        return 1;
    }

    @Override
    public float eyi$getChargeCooldownMultiplier() {
        var config = ServerConfigs.getSpellConfig((AbstractSpell)(Object)this);
        if (config instanceof IEyiConfigParams eyiParams) {
            return (float) eyiParams.eyi$getChargeRatio();
        }
        return 0.1f;
    }
}
