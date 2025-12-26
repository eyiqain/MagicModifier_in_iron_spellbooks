package io.isb.modifier.init;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.api.modifier.AbstractModifier;
import io.isb.modifier.api.modifier.magic.TestModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModifierRegistry {
    public static final DeferredRegister<AbstractModifier> MODIFIERS = DeferredRegister.create(new ResourceLocation(MagicModifier.MODID, "modifiers"), MagicModifier.MODID);

    // 创建一个新的 Registry
    public static final Supplier<IForgeRegistry<AbstractModifier>> REGISTRY = MODIFIERS.makeRegistry(RegistryBuilder::new);

    public static void register(IEventBus eventBus) {
        MODIFIERS.register(eventBus);
    }
    public static final RegistryObject<AbstractModifier> TEST_MODIFIER =
            MODIFIERS.register("test", TestModifier::new); // 只要写名字，模组ID会自动加
    // 示例：注册一个“爆炸”修饰符
    // public static final RegistryObject<AbstractModifier> EXPLOSIVE = MODIFIERS.register("explosive", ExplosiveModifier::new);
}
