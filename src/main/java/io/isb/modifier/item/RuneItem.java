package io.isb.modifier.item;

import io.isb.modifier.api.modifier.AbstractModifier;
import io.isb.modifier.init.ModifierRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RuneItem extends Item {

    public RuneItem(Properties pProperties) {
        super(pProperties);
    }

    // --- NBT 操作 ---

    // 将修饰符写入符文物品的 NBT
    public static void setModifier(ItemStack stack, AbstractModifier modifier) {
        CompoundTag tag = stack.getOrCreateTag();
        // 这里你已经写对了，用的是 getModifierId
        tag.putString("ModifierId", modifier.getModifierId().toString());
    }

    // 从符文物品读取修饰符
    @Nullable
    public static AbstractModifier getModifier(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("ModifierId")) {
            ResourceLocation id = new ResourceLocation(stack.getTag().getString("ModifierId"));
            return ModifierRegistry.REGISTRY.get().getValue(id);
        }
        return null; // 空符文
    }

    // --- 显示 ---

    @Override
    public Component getName(ItemStack pStack) {
        AbstractModifier modifier = getModifier(pStack);
        if (modifier != null) {
            // 确保这里的 item.mymod.rune 在你的语言文件(en_us.json)里有翻译，或者改成 item.magic_modifier.rune
            return Component.translatable("item.magic_modifier.rune").append(": ").append(modifier.getDisplayName());
        }
        return super.getName(pStack);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        AbstractModifier modifier = getModifier(pStack);
        if (modifier != null) {
            // 【修改处】将 getRegistryName() 改为 getModifierId()
            pTooltipComponents.add(Component.literal("§7Function: " + modifier.getModifierId()));
        }
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
    }
}
