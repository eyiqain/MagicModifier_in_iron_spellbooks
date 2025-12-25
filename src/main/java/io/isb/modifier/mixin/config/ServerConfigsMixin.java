package io.isb.modifier.mixin.config;

import io.isb.modifier.config.IEyiConfigParams;
import io.isb.modifier.config.IEyiDefaultConfig;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ServerConfigs.class)
public class ServerConfigsMixin {

    @Shadow(remap = false) @Final private static ForgeConfigSpec.Builder BUILDER;
    @Shadow(remap = false) @Final private static Map<String, ServerConfigs.SpellConfigParameters> SPELL_CONFIGS;

    @Inject(
            method = "createSpellConfig",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$Builder;pop()Lnet/minecraftforge/common/ForgeConfigSpec$Builder;"
            ),
            remap = false
    )
    private static void eyi$appendChargeConfig(AbstractSpell spell, CallbackInfo ci) {
        DefaultConfig config = spell.getDefaultConfig();
        int defCharges = 1;
        double defRatio = 0.5;

        if (config instanceof IEyiDefaultConfig eyiDef) {
            defCharges = eyiDef.eyi$getDefaultMaxCharges();
            defRatio = eyiDef.eyi$getDefaultChargeRatio();
        }

        // 定义配置项
        ForgeConfigSpec.ConfigValue<Integer> maxCharges = BUILDER
                .comment("Max charges (1 = default)")
                .define("MaxCharges", defCharges);

        ForgeConfigSpec.ConfigValue<Double> chargeRatio = BUILDER
                .comment("Charge Cooldown Ratio (0.5 = 50%)")
                .define("ChargeCooldownRatio", defRatio);

        ForgeConfigSpec.ConfigValue<Double> powerMultiplier = BUILDER
                .comment("Power Multiplier")
                .define("PowerMultiplier", 1.0d);

        ForgeConfigSpec.ConfigValue<Double> cooldownMultiplier = BUILDER
                .comment("Cooldown Multiplier")
                .define("CooldownMultiplier", 1.0d);

        // 🔥 新增法力配置
        ForgeConfigSpec.ConfigValue<Double> manaMultiplier = BUILDER
                .comment("Mana Cost Multiplier")
                .define("ManaCostMultiplier", 1.0d);

        // 注入
        ServerConfigs.SpellConfigParameters params = SPELL_CONFIGS.get(spell.getSpellId());
        if (params instanceof IEyiConfigParams eyiParams) {
            eyiParams.eyi$setChargeParams(maxCharges, chargeRatio);
            eyiParams.eyi$setPowerParams(powerMultiplier);
            eyiParams.eyi$setCooldownParams(cooldownMultiplier);
            eyiParams.eyi$setManaParams(manaMultiplier); // 🔥
        }
    }
}
