package io.isb.modifier.config;

import net.minecraftforge.common.ForgeConfigSpec;

public interface IEyiConfigParams {
    // 写入配置对象
    void eyi$setChargeParams(ForgeConfigSpec.ConfigValue<Integer> maxCharges, ForgeConfigSpec.ConfigValue<Double> chargeRatio);
    void eyi$setPowerParams(ForgeConfigSpec.ConfigValue<Double> powerMultiplier);
    void eyi$setCooldownParams(ForgeConfigSpec.ConfigValue<Double> cooldownMultiplier);
    // 🔥 新增
    void eyi$setManaParams(ForgeConfigSpec.ConfigValue<Double> manaMultiplier);

    // 读取数值
    int eyi$getMaxCharges();
    double eyi$getChargeRatio();
    double eyi$getPowerMultiplier();
    double eyi$getCooldownMultiplier();
    // 🔥 新增
    double eyi$getManaMultiplier();

    // 写入数值 (保存)
    void eyi$setConfigMaxCharges(int charges);
    void eyi$setConfigChargeRatio(double ratio);
    void eyi$setConfigPowerMultiplier(double multiplier);
    void eyi$setConfigCooldownMultiplier(double multiplier);
    // 🔥 新增
    void eyi$setConfigManaMultiplier(double multiplier);
}
