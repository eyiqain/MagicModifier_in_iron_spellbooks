package io.isb.modifier.mixin.config;

import io.isb.modifier.config.IEyiConfigParams;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerConfigs.SpellConfigParameters.class)
public class SpellConfigParametersMixin implements IEyiConfigParams {

    @Unique private ForgeConfigSpec.ConfigValue<Integer> eyi$MAX_CHARGES_CONFIG;
    @Unique private ForgeConfigSpec.ConfigValue<Double> eyi$CHARGE_RATIO_CONFIG;
    @Unique private ForgeConfigSpec.ConfigValue<Double> eyi$POWER_MULTIPLIER_CONFIG;
    @Unique private ForgeConfigSpec.ConfigValue<Double> eyi$COOLDOWN_MULTIPLIER_CONFIG;
    @Unique private ForgeConfigSpec.ConfigValue<Double> eyi$MANA_MULTIPLIER_CONFIG; // 🔥

    // === 初始化 ===
    @Override
    public void eyi$setChargeParams(ForgeConfigSpec.ConfigValue<Integer> maxCharges, ForgeConfigSpec.ConfigValue<Double> chargeRatio) {
        this.eyi$MAX_CHARGES_CONFIG = maxCharges;
        this.eyi$CHARGE_RATIO_CONFIG = chargeRatio;
    }
    @Override
    public void eyi$setPowerParams(ForgeConfigSpec.ConfigValue<Double> powerMultiplier) {
        this.eyi$POWER_MULTIPLIER_CONFIG = powerMultiplier;
    }
    @Override
    public void eyi$setCooldownParams(ForgeConfigSpec.ConfigValue<Double> cooldownMultiplier) {
        this.eyi$COOLDOWN_MULTIPLIER_CONFIG = cooldownMultiplier;
    }
    @Override
    public void eyi$setManaParams(ForgeConfigSpec.ConfigValue<Double> manaMultiplier) { // 🔥
        this.eyi$MANA_MULTIPLIER_CONFIG = manaMultiplier;
    }

    // === Getters ===
    @Override
    public int eyi$getMaxCharges() { return eyi$MAX_CHARGES_CONFIG != null ? eyi$MAX_CHARGES_CONFIG.get() : 1; }
    @Override
    public double eyi$getChargeRatio() { return eyi$CHARGE_RATIO_CONFIG != null ? eyi$CHARGE_RATIO_CONFIG.get() : 0.5; }
    @Override
    public double eyi$getPowerMultiplier() { return eyi$POWER_MULTIPLIER_CONFIG != null ? eyi$POWER_MULTIPLIER_CONFIG.get() : 1.0; }
    @Override
    public double eyi$getCooldownMultiplier() { return eyi$COOLDOWN_MULTIPLIER_CONFIG != null ? eyi$COOLDOWN_MULTIPLIER_CONFIG.get() : 1.0; }

    @Override
    public double eyi$getManaMultiplier() { // 🔥
        return eyi$MANA_MULTIPLIER_CONFIG != null ? eyi$MANA_MULTIPLIER_CONFIG.get() : 1.0;
    }

    // === Setters ===
    @Override
    public void eyi$setConfigMaxCharges(int charges) { if (eyi$MAX_CHARGES_CONFIG != null) { eyi$MAX_CHARGES_CONFIG.set(charges); eyi$MAX_CHARGES_CONFIG.save(); } }
    @Override
    public void eyi$setConfigChargeRatio(double ratio) { if (eyi$CHARGE_RATIO_CONFIG != null) { eyi$CHARGE_RATIO_CONFIG.set(ratio); eyi$CHARGE_RATIO_CONFIG.save(); } }
    @Override
    public void eyi$setConfigPowerMultiplier(double multiplier) { if (eyi$POWER_MULTIPLIER_CONFIG != null) { eyi$POWER_MULTIPLIER_CONFIG.set(multiplier); eyi$POWER_MULTIPLIER_CONFIG.save(); } }
    @Override
    public void eyi$setConfigCooldownMultiplier(double multiplier) { if (eyi$COOLDOWN_MULTIPLIER_CONFIG != null) { eyi$COOLDOWN_MULTIPLIER_CONFIG.set(multiplier); eyi$COOLDOWN_MULTIPLIER_CONFIG.save(); } }

    @Override
    public void eyi$setConfigManaMultiplier(double multiplier) { // 🔥
        if (eyi$MANA_MULTIPLIER_CONFIG != null) {
            eyi$MANA_MULTIPLIER_CONFIG.set(multiplier);
            eyi$MANA_MULTIPLIER_CONFIG.save();
        }
    }
}
