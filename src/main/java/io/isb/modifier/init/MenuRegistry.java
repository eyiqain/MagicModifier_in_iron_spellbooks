package io.isb.modifier.init;

import io.isb.modifier.MagicModifier;
import io.isb.modifier.gui.SpellMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MenuRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MagicModifier.MODID);

    public static final RegistryObject<MenuType<SpellMenu>> SPELL_MENU =
            MENUS.register("spell_menu", () -> IForgeMenuType.create(SpellMenu::new));
}
