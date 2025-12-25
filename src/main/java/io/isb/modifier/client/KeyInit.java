package io.isb.modifier.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.gui.SpellConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MagicModifier.MODID, value = Dist.CLIENT)
public class KeyInit {

    // 定义 K 键
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.magic_modifier.open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.category.magic_modifier"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (OPEN_CONFIG_KEY.consumeClick()) {
            // 打开 UI
            Minecraft.getInstance().setScreen(new SpellConfigScreen());
        }
    }
}
