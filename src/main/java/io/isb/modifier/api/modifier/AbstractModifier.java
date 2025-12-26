package io.isb.modifier.api.modifier;

import io.isb.modifier.init.ModifierRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;

public abstract class AbstractModifier {

    // 修饰符的显示名称
    public abstract Component getDisplayName();

    // 核心逻辑：当法术施放时触发
    public void onSpellCast(SpellOnCastEvent event) {
        // 默认什么都不做
    }
    // 获取修饰符的唯一ID
    public ResourceLocation getModifierId() {
        // 确保 ModifierRegistry.REGISTRY 已经初始化
        ResourceLocation key = ModifierRegistry.REGISTRY.get().getKey(this);
        return key;
    }

    public abstract float getCooldownMultiplier();
}
