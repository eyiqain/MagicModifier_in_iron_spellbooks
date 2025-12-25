package io.isb.modifier.config;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
//作用：让 SpellConfigParameters 类拥有持有“Config配置项”的能力。
public interface IEyiDefaultConfig {

    // 设置默认值（返回 DefaultConfig 以支持链式调用）
    DefaultConfig eyi$setDefaultMaxCharges(int charges);
    DefaultConfig eyi$setDefaultChargeRatio(double ratio);

    // 获取默认值（供 Config 生成器读取）
    int eyi$getDefaultMaxCharges();
    double eyi$getDefaultChargeRatio();
}
