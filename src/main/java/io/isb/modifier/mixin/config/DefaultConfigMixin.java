package io.isb.modifier.mixin.config;

import io.isb.modifier.config.IEyiDefaultConfig;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

//作用：劫持 DefaultConfig，增加两个字段存默认值。
@Mixin(DefaultConfig.class)
public class DefaultConfigMixin implements IEyiDefaultConfig {

    // 默认值：1层充能，0.5比例
    @Unique private int eyi$defaultMaxCharges = 1;
    @Unique private double eyi$defaultChargeRatio = 0.1d;

    @Override
    public DefaultConfig eyi$setDefaultMaxCharges(int charges) {
        this.eyi$defaultMaxCharges = charges;
        return (DefaultConfig) (Object) this;
    }

    @Override
    public DefaultConfig eyi$setDefaultChargeRatio(double ratio) {
        this.eyi$defaultChargeRatio = ratio;
        return (DefaultConfig) (Object) this;
    }

    @Override
    public int eyi$getDefaultMaxCharges() {
        return this.eyi$defaultMaxCharges;
    }

    @Override
    public double eyi$getDefaultChargeRatio() {
        return this.eyi$defaultChargeRatio;
    }
}
