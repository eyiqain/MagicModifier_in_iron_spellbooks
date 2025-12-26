package io.isb.modifier.api.magic;

import io.isb.modifier.api.AbstractModifier;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class TestModifier extends AbstractModifier {
    @Override
    public Component getDisplayName() {
        return Component.literal("Test Rune").withStyle(ChatFormatting.RED);
    }

    @Override
    public void onSpellCast(SpellOnCastEvent event) {
        // 当法术释放时，在控制台打印一句话
        System.out.println(">>> 成功触发！修饰符系统运作正常！ <<<");

        // 也可以在游戏里给玩家发一条消息
        if(event.getEntity() != null) {
            event.getEntity().sendSystemMessage(Component.literal("修饰符生效了！"));
        }
    }

    @Override
    public float getCooldownMultiplier() {
        return 0;
    }
}
