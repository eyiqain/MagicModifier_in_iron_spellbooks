package io.isb.modifier.mixin.charge;

import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.PacketSyncCharge;
import io.isb.modifier.spell.IChargedSpell;
import io.isb.modifier.spell.IMagicChargeData;
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

    @Shadow(remap = false)
    private PlayerCooldowns playerCooldowns;

    // 存储每个法术的积攒时间 (tick)
    // > 0 : 拥有额外层数的积攒进度
    // = limit : 满层
    // < 0 : 第一层都在冷却中 (此时数值代表负的剩余冷却时间，大致概念)
    @Unique
    private final Map<String, Integer> eyi$accumulatedTicks = new HashMap<>();

    /**
     * 辅助方法：根据积攒时间计算当前层数
     */
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

    /**
     * 核心逻辑：消耗层数并计算新的冷却
     */
    @Override
    public int eyi$calculateAndConsumeCooldown(String spellId, int baseCooldown, int maxCharges, float shortCooldownMultiplier) {
        int limit = (maxCharges - 1) * baseCooldown;
        int accumulated;

        if (!eyi$accumulatedTicks.containsKey(spellId)) {
            // 如果从来没记录过，假设它是满状态 (或者由 syncAll 初始化)
            accumulated = limit;
        } else {
            accumulated = eyi$accumulatedTicks.get(spellId);
        }

        int resultCooldown;
        int remainingAccumulated;
        int cost = baseCooldown;

        if (accumulated >= cost) {
            // 还有足够的积攒时间扣除一层
            remainingAccumulated = accumulated - cost;
            // 既然是消耗积攒层，给予一个极短的冷却（连发）
            resultCooldown = (int) (baseCooldown * shortCooldownMultiplier);
        } else {
            // 积攒不够了（比如只有 0.5 个冷却时间），说明耗尽了
            // 变为负数，进入真冷却
            remainingAccumulated = accumulated - cost;
            // 剩余真实冷却 = base - 已经积攒的部分
            resultCooldown = Math.max(0, baseCooldown - accumulated);
        }

        eyi$accumulatedTicks.put(spellId, remainingAccumulated);
        return resultCooldown;
    }

    /**
     * 每 tick 调用，处理积攒和状态修正
     */
    @Override
    public void eyi$tickAccumulate(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // 遍历所有记录的法术
        for (Map.Entry<String, Integer> entry : eyi$accumulatedTicks.entrySet()) {
            String spellId = entry.getKey();
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == SpellRegistry.none()) continue;

            // 1. 获取当前状态
            int storedAccumulated = entry.getValue();
            boolean isOnCooldown = playerCooldowns.isOnCooldown(spell);

            // 2. 冷却状态监测与修正
            if (isOnCooldown) {
                // 冷却中，无法积攒，直接跳过
                continue;
            } else {
                // 不在冷却中，但存储值是负数？说明原版冷却刚刚结束！
                // 修正为 0 (即恢复到第 1 层)
                if (storedAccumulated < 0) {
                    storedAccumulated = 0;
                    // 这里不用急着 set，后面流程会处理
                }
            }

            // 3. 积攒逻辑
            if (spell instanceof IChargedSpell chargedSpell) {
                int maxCharges = chargedSpell.eyi$getMaxCharges();

                // === 如果最大层数被配置改成了 1，或者更小 ===
                if (maxCharges <= 1) {
                    // 如果手里还有积攒值，清零并同步
                    if (storedAccumulated > 0) {
                        entry.setValue(0);
                        ModMessage.sendToPlayer(new PacketSyncCharge(spellId, 1, 0), serverPlayer);
                    }
                    continue;
                }

                int baseCooldown = spell.getSpellCooldown();
                int limit = (maxCharges - 1) * baseCooldown;

                // === 🔥 BUG 修复：超标削减 (Overcharge Correction) 🔥 ===
                // 情况：玩家本来叠了5层(limit大)，突然配置改成3层(limit小)。
                // 此时 storedAccumulated > 新limit。
                if (storedAccumulated > limit) {
                    // 1. 强制削减到新上限
                    entry.setValue(limit);

                    // 2. 立即发包同步客户端 (让 UI 从 5 变 3)
                    ModMessage.sendToPlayer(
                            new PacketSyncCharge(spellId, maxCharges, limit),
                            serverPlayer
                    );

                    // 本 tick 处理完毕，跳过后续增加逻辑
                    continue;
                }
                // =======================================================

                // 只有未满时才积攒
                if (storedAccumulated < limit) {
                    // 修正当前值为逻辑起点 (处理 storedAccumulated < 0 变为 0 的情况)
                    int currentEffective = Math.max(0, storedAccumulated);
                    int oldCharges = eyi$calculateCharges(currentEffective, baseCooldown);

                    // 增加 1 tick
                    int newAccumulated = currentEffective + 1;
                    entry.setValue(newAccumulated);

                    int newCharges = eyi$calculateCharges(newAccumulated, baseCooldown);

                    // 4. 发包条件
                    // 情况A: 层数变化 (1 -> 2)
                    // 情况B: 周期性同步 (防丢包)
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

    /**
     * 施法后同步
     */
    @Override
    public void eyi$syncAfterCast(ServerPlayer player, String spellId, int baseCooldown) {
        int accumulated = eyi$accumulatedTicks.getOrDefault(spellId, 0);
        int currentCharges = eyi$calculateCharges(accumulated, baseCooldown);
        ModMessage.sendToPlayer(
                new PacketSyncCharge(spellId, currentCharges, accumulated),
                player
        );
    }

    /**
     * 进服全量同步
     */
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

                // 默认是满层
                int accumulated = eyi$accumulatedTicks.getOrDefault(spell.getSpellId(), limit);
                // 如果 map 里没存，说明是满的，存一下以防万一
                if (!eyi$accumulatedTicks.containsKey(spell.getSpellId())) {
                    eyi$accumulatedTicks.put(spell.getSpellId(), limit);
                }

                int currentCharges = eyi$calculateCharges(accumulated, baseCooldown);

                ModMessage.sendToPlayer(
                        new PacketSyncCharge(spell.getSpellId(), currentCharges, accumulated),
                        player
                );
            }
        }
    }
}
