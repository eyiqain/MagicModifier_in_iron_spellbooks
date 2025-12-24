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
//作用：连接点。读取 DefaultConfig -> 生成 TOML 配置 -> 存入 SpellConfigParameters
@Mixin(ServerConfigs.class)
public class ServerConfigsMixin {

    @Shadow(remap = false) @Final private static ForgeConfigSpec.Builder BUILDER;
    @Shadow(remap = false) @Final private static Map<String, ServerConfigs.SpellConfigParameters> SPELL_CONFIGS;

    @Inject(
            method = "createSpellConfig",
            at = @At(
                    value = "INVOKE",
                    // 注意：pop() 返回 Builder，所以签名必须带返回值类型
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$Builder;pop()Lnet/minecraftforge/common/ForgeConfigSpec$Builder;"
            ),
            remap = false
    )
    private static void eyi$appendChargeConfig(AbstractSpell spell, CallbackInfo ci) {
        // 1. 获取该法术的代码默认配置
        DefaultConfig config = spell.getDefaultConfig();

        int defCharges = 1;
        double defRatio = 0.5;

        // 通过接口读取 DefaultConfigMixin 里的值
        if (config instanceof IEyiDefaultConfig eyiDef) {
            defCharges = eyiDef.eyi$getDefaultMaxCharges();
            defRatio = eyiDef.eyi$getDefaultChargeRatio();
        }

        // 2. 定义 TOML 配置项 (使用上面的默认值)
        ForgeConfigSpec.ConfigValue<Integer> maxCharges = BUILDER
                .comment("Max charges for this spell. 1 = Standard cooldown.")
                .define("MaxCharges", defCharges);

        ForgeConfigSpec.ConfigValue<Double> chargeRatio = BUILDER
                .comment("Cooldown multiplier when a charge is used (0.5 = 50% cd).")
                .define("ChargeCooldownRatio", defRatio);

        // 3. 将 ConfigValue 注入到 Parameters 对象中
        ServerConfigs.SpellConfigParameters params = SPELL_CONFIGS.get(spell.getSpellId());
        if (params instanceof IEyiConfigParams eyiParams) {
            eyiParams.eyi$setChargeParams(maxCharges, chargeRatio);
        }
    }
}
