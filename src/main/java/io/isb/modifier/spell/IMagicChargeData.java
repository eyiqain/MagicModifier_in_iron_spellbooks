package io.isb.modifier.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public interface IMagicChargeData {
    // 获取当前积攒的 tick
    int eyi$getAccumulatedTicks(String spellId);

    // 消耗 tick 的核心逻辑（返回：这次施法后应该给多少冷却）
    int eyi$calculateAndConsumeCooldown(String spellId, int baseCooldown, int maxCharges, float shortCooldownMultiplier);

    // 每 tick 调用，用来“积攒”时间
    void eyi$tickAccumulate(Player player);

    //渲染同步
    void eyi$syncAfterCast(ServerPlayer player, String spellId, int baseCooldown) ;
    // === 【新增】 进游戏时全量同步 ===
    void eyi$syncAll(ServerPlayer player);
}
