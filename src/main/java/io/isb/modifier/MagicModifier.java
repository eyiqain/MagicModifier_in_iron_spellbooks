package io.isb.modifier;

import io.isb.modifier.gui.SpellMenu;
import io.isb.modifier.gui.SpellScreen;
import com.mojang.logging.LogUtils;
import io.isb.modifier.init.MenuRegistry;
import io.isb.modifier.net.ModMessage;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MagicModifier.MODID)
public class MagicModifier {

    public static final String MODID = "magic_modifier";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MagicModifier() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModMessage.register();//注册网络
        // 【必须添加这行！】将 MenuRegistry 的 DeferredRegister 注册到总线
        MenuRegistry.MENUS.register(bus);

        InitAll(bus);//注册物品
        config();//注册配置
        // 3. 注册客户端设置事件
        bus.addListener(this::clientSetup);
    }
    // 这是一个只在客户端运行的方法
    private void clientSetup(final FMLClientSetupEvent event) {
        // 使用 enqueueWork 确保在正确的主线程时机执行（虽然 register 本身通常也是安全的，但这是最佳实践）
        event.enqueueWork(() -> {
            MenuScreens.register(MenuRegistry.SPELL_MENU.get(), SpellScreen::new);
        });
    }

    public void InitAll(IEventBus iEventBus){//正常注册进世界总线
        //ModItemRegister.init(iEventBus);
    }
    private void config() {
       // ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
    }
}
