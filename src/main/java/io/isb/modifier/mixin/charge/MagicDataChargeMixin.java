package io.isb.modifier.mixin.charge;

import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.PacketSyncCharge;
import io.isb.modifier.spell.IMagicChargeData;
import io.isb.modifier.spell.IChargedSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerCooldowns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

@Mixin(MagicData.class)
public class MagicDataChargeMixin implements IMagicChargeData {

    @Shadow(remap = false) private PlayerCooldowns playerCooldowns;

    @Unique
    private final Map<String, Integer> eyi$accumulatedTicks = new HashMap<>();

    @Unique
    private int eyi$calculateCharges(int accumulated, int baseCooldown) {
        if (baseCooldown <= 0) return 1;
        // 负数 = 0层；非负数 = 1 + 额外层
        if (accumulated < 0) return 0;
        return 1 + (accumulated / baseCooldown);
    }

    @Override
    public int eyi$getAccumulatedTicks(String spellId) {
        return eyi$accumulatedTicks.getOrDefault(spellId, 0);
    }

    @Override
    public int eyi$calculateAndConsumeCooldown(String spellId, int baseCooldown, int maxCharges, float shortCooldownMultiplier) {
        int limit = (maxCharges - 1) * baseCooldown;
        int accumulated;

        if (!eyi$accumulatedTicks.containsKey(spellId)) {
            accumulated = limit;
        } else {
            accumulated = eyi$accumulatedTicks.get(spellId);
        }

        int resultCooldown;
        int remainingAccumulated;
        int cost = baseCooldown;

        if (accumulated >= cost) {
            remainingAccumulated = accumulated - cost;
            resultCooldown = (int) (baseCooldown * shortCooldownMultiplier);
        } else {
            // 消耗基础层，变为负数，进入真冷却
            remainingAccumulated = accumulated - cost;
            resultCooldown = Math.max(0, baseCooldown - accumulated);
        }

        eyi$accumulatedTicks.put(spellId, remainingAccumulated);
        return resultCooldown;
    }

    @Override
    public void eyi$tickAccumulate(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        for (Map.Entry<String, Integer> entry : eyi$accumulatedTicks.entrySet()) {
            String spellId = entry.getKey();
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == SpellRegistry.none()) continue;

            // 1. 获取当前真实状态
            int storedAccumulated = entry.getValue();
            boolean isOnCooldown = playerCooldowns.isOnCooldown(spell);

            // 2. 冷却逻辑处理
            if (isOnCooldown) {
                // 冷却中，无法积攒，直接跳过
                continue;
            } else {
                // 不在冷却中，但存储值是负数？说明冷却刚刚结束！
                // 【核心修复】：直接跳变到 0，恢复第 1 层
                if (storedAccumulated < 0) {
                    storedAccumulated = 0;
                }
            }

            // 3. 开始积攒逻辑
            if (spell instanceof IChargedSpell chargedSpell) {
                int maxCharges = chargedSpell.eyi$getMaxCharges();
                if (maxCharges <= 1) continue;

                int baseCooldown = spell.getSpellCooldown();
                int limit = (maxCharges - 1) * baseCooldown;

                // 只有未满时才积攒
                if (storedAccumulated < limit) {
                    // 计算旧层数：注意要用 entry.getValue() 的原始值来对比
                    // 如果原始值是 -100 (0层)，现在 storedAccumulated 被修正为 0 (1层)
                    // 这样 oldCharges=0, newCharges=1，就会触发发包
                    int oldRaw = entry.getValue();
                    int oldCharges = eyi$calculateCharges(oldRaw, baseCooldown);

                    // 增加 1 tick
                    int newAccumulated = storedAccumulated + 1;
                    entry.setValue(newAccumulated);

                    int newCharges = eyi$calculateCharges(newAccumulated, baseCooldown);

                    // 4. 发包条件
                    // 情况A: 正常积攒满了一层 (1 -> 2)
                    // 情况B: 冷却结束瞬间 (-100 -> 1)，此时 old=0, new=1，满足 > 条件，立即发包！
                    if (newCharges > oldCharges || (newAccumulated % 20 == 0)) {
                        ModMessage.sendToPlayer(
                                new PacketSyncCharge(spellId, newCharges, newAccumulated),
                                serverPlayer
                        );
                    }
                }
            }
        }
    }

    @Override
    public void eyi$syncAfterCast(ServerPlayer player, String spellId, int baseCooldown) {
        int accumulated = eyi$accumulatedTicks.getOrDefault(spellId, 0);
        int currentCharges = eyi$calculateCharges(accumulated, baseCooldown);
        ModMessage.sendToPlayer(
                new PacketSyncCharge(spellId, currentCharges, accumulated),
                player
        );
    }

    @Override
    public void eyi$syncAll(ServerPlayer player) {
        SpellSelectionManager manager = new SpellSelectionManager(player);
        for (SpellSelectionManager.SelectionOption option : manager.getAllSpells()) {
            AbstractSpell spell = option.spellData.getSpell();
            if (spell instanceof IChargedSpell chargedSpell) {
                int maxCharges = chargedSpell.eyi$getMaxCharges();
                if (maxCharges <= 1) continue;

                int baseCooldown = spell.getSpellCooldown();
                int limit = (maxCharges - 1) * baseCooldown;
                int accumulated = eyi$accumulatedTicks.getOrDefault(spell.getSpellId(), limit);

                int currentCharges = eyi$calculateCharges(accumulated, baseCooldown);

                ModMessage.sendToPlayer(
                        new PacketSyncCharge(spell.getSpellId(), currentCharges, accumulated),
                        player
                );
            }
        }
    }
}
