package io.isb.modifier.api.util;

import io.isb.modifier.api.AbstractModifier;
import io.isb.modifier.init.ModifierRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable; // 推荐使用 Minecraft 自带的 Nullable

import java.util.HashMap;
import java.util.Map;

public class RuneContainerHelper {

    // NBT 常量定义
    public static final String RUNES_TAG = "MyMod_Runes";
    public static final String SLOT_TAG = "Slot";
    public static final String ID_TAG = "ModifierId";

    public static final int MAX_SLOTS = 6;

    // --- 基础检查 ---
    public static boolean hasRunes(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(RUNES_TAG, Tag.TAG_LIST);
    }

    // --- 获取指定槽位的修饰符 (用于 Tooltip) ---
    @Nullable
    public static AbstractModifier getRuneInSlot(ItemStack stack, int slot) {
        if (!hasRunes(stack)) return null;

        ListTag runesList = stack.getTag().getList(RUNES_TAG, Tag.TAG_COMPOUND);
        for (Tag tag : runesList) {
            CompoundTag runeTag = (CompoundTag) tag;
            if (runeTag.getInt(SLOT_TAG) == slot) {
                String modifierId = runeTag.getString(ID_TAG);
                if (modifierId.isEmpty()) return null;
                try {
                    return ModifierRegistry.REGISTRY.get().getValue(new ResourceLocation(modifierId));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    // --- 镶嵌符文 ---
    public static void setRune(ItemStack targetStack, int slot, AbstractModifier modifier) {
        if (slot < 0 || slot >= MAX_SLOTS) return;

        CompoundTag rootTag = targetStack.getOrCreateTag();
        ListTag runesList;

        if (rootTag.contains(RUNES_TAG, Tag.TAG_LIST)) {
            runesList = rootTag.getList(RUNES_TAG, Tag.TAG_COMPOUND);
        } else {
            runesList = new ListTag();
        }

        // 1. 移除该槽位旧的符文
        runesList.removeIf(tag -> ((CompoundTag) tag).getInt(SLOT_TAG) == slot);

        // 2. 添加新符文
        CompoundTag newRuneTag = new CompoundTag();
        newRuneTag.putInt(SLOT_TAG, slot);
        newRuneTag.putString(ID_TAG, modifier.getModifierId().toString());
        runesList.add(newRuneTag);

        rootTag.put(RUNES_TAG, runesList);
    }

    // 🔥🔥🔥【关键新增 1】适配 MagicManagerMixin
    // Mixin 里的逻辑依赖于 Map<Integer, AbstractModifier> 这个返回值结构
    public static Map<Integer, AbstractModifier> getRunes(ItemStack stack) {
        Map<Integer, AbstractModifier> map = new HashMap<>();

        if (!hasRunes(stack)) return map;

        ListTag runesList = stack.getTag().getList(RUNES_TAG, Tag.TAG_COMPOUND);
        for (Tag tag : runesList) {
            CompoundTag runeTag = (CompoundTag) tag;
            int slot = runeTag.getInt(SLOT_TAG);
            String modifierId = runeTag.getString(ID_TAG);

            try {
                AbstractModifier modifier = ModifierRegistry.REGISTRY.get().getValue(new ResourceLocation(modifierId));
                if (modifier != null) {
                    map.put(slot, modifier);
                }
            } catch (Exception e) {
                // 忽略无效ID
            }
        }
        return map;
    }

    // 🔥🔥🔥【关键新增 2】适配 /rune clear 指令
    public static boolean removeRune(ItemStack stack, int slot) {
        if (!hasRunes(stack)) return false;

        ListTag runesList = stack.getTag().getList(RUNES_TAG, Tag.TAG_COMPOUND);

        // 移除匹配槽位的 Tag
        boolean removed = runesList.removeIf(tag -> ((CompoundTag) tag).getInt(SLOT_TAG) == slot);

        // 如果列表空了，把整个 MyMod_Runes 标签删掉，保持整洁
        if (runesList.isEmpty()) {
            stack.getTag().remove(RUNES_TAG);
        }

        return removed;
    }

    // 🔥🔥🔥【关键新增 3】适配 /rune clear all 指令
    public static void clearAllRunes(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(RUNES_TAG);
        }
    }
}
