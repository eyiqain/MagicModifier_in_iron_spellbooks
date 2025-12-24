package io.isb.modifier.config;

import java.util.function.Supplier;
//作用：让 DefaultConfig 类拥有存储“默认充能数”的能力，供注册法术时使用。
public interface IEyiConfigParams {
    void eyi$setChargeParams(Supplier<Integer> maxCharges, Supplier<Double> chargeRatio);
    int eyi$getMaxCharges();
    double eyi$getChargeRatio();
}
