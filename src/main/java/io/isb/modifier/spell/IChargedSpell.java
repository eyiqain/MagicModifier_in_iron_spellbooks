package io.isb.modifier.spell;

public interface IChargedSpell {
    // 运行时逻辑调用：获取当前充能上限（读配置）
    int eyi$getMaxCharges();

    // 运行时逻辑调用：获取充能冷却比例（读配置）
    float eyi$getChargeCooldownMultiplier();
}