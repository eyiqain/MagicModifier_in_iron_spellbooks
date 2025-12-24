package io.isb.modifier;


import com.mojang.logging.LogUtils;
import io.isb.modifier.net.ModMessage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MagicModifier.MODID)
public class MagicModifier {

    public static final String MODID = "magic_modifier";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MagicModifier() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModMessage.register();//注册网络
        InitAll(bus);//注册物品
        config();//注册配置
    }
    public void InitAll(IEventBus iEventBus){//正常注册进世界总线
        //ModItemRegister.init(iEventBus);
    }
    private void config() {
       // ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
    }
}
