package io.isb.modifier.mixin.config;

import io.isb.modifier.config.IEyiConfigParams;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.function.Supplier;

//作用：劫持 SpellConfigParameters，增加字段持有 Config 的 Supplier。
@Mixin(ServerConfigs.SpellConfigParameters.class)
public class SpellConfigParametersMixin implements IEyiConfigParams {

    @Unique private Supplier<Integer> eyi$MAX_CHARGES;
    @Unique private Supplier<Double> eyi$CHARGE_RATIO;

    @Override
    public void eyi$setChargeParams(Supplier<Integer> maxCharges, Supplier<Double> chargeRatio) {
        this.eyi$MAX_CHARGES = maxCharges;
        this.eyi$CHARGE_RATIO = chargeRatio;
    }

    @Override
    public int eyi$getMaxCharges() {
        // 懒加载：调用 Supplier.get() 才是真正去 Config 对象里读值
        return eyi$MAX_CHARGES != null ? eyi$MAX_CHARGES.get() : 1;
    }

    @Override
    public double eyi$getChargeRatio() {
        return eyi$CHARGE_RATIO != null ? eyi$CHARGE_RATIO.get() : 0.5;
    }
}
