package io.isb.modifier.client;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class ClientChargeData {

    public static class ChargeEntry {
        public int charges;          // 服务端同步的层数
        public int baseAccumulated;  // 服务端同步的基准积攒值
        public long lastUpdateTick;  // 上次更新时间戳

        public ChargeEntry(int c, int a) {
            this.charges = c;
            this.baseAccumulated = a;
            this.lastUpdateTick = -1; // -1 表示刚初始化
        }
    }

    private static final Map<String, ChargeEntry> SPELL_DATA = new HashMap<>();

    // 接收网络包调用
    public static void setChargeData(String spellId, int charges, int accumulatedTicks) {
        // 收到包时，重置数据
        SPELL_DATA.put(spellId, new ChargeEntry(charges, accumulatedTicks));
    }

    /**
     * 核心逻辑：每 Tick 调用
     * 负责在冷却时暂停时间戳，防止进度条偷跑
     */
    public static void clientTick(Player player) {
        long currentTick = player.tickCount;

        for (Map.Entry<String, ChargeEntry> entry : SPELL_DATA.entrySet()) {
            String spellId = entry.getKey();
            ChargeEntry data = entry.getValue();

            // 获取法术引用
            AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);

            // 检查是否在冷却
            float cooldown = ClientMagicData.getCooldownPercent(spell);
            boolean isOnCooldown = cooldown > 0;

            if (data.lastUpdateTick == -1) {
                // 刚收到包，初始化时间基准
                data.lastUpdateTick = currentTick;
            } else if (isOnCooldown) {
                // 如果在冷却中，为了让 (current - last) 保持不变（即进度暂停），
                // 我们必须让 last 随着 current 一起增加
                data.lastUpdateTick = currentTick;
            }
        }
    }

    public static int getCharges(AbstractSpell spell) {
        if (spell == null) return 0;
        ChargeEntry entry = SPELL_DATA.get(spell.getSpellId());
        return entry == null ? 0 : entry.charges;
    }

    // === 这里就是你报错的方法 ===
    public static int getPredictedAccumulated(AbstractSpell spell, Player player) {
        if (spell == null) return 0;
        ChargeEntry entry = SPELL_DATA.get(spell.getSpellId());

        // 如果没有数据，或者刚才还没初始化 tick，直接返回 0
        if (entry == null || entry.lastUpdateTick == -1) return 0;

        // 预测公式：基准值 + (当前时间 - 上次更新时间)
        long delta = player.tickCount - entry.lastUpdateTick;
        if (delta < 0) delta = 0;

        return (int) (entry.baseAccumulated + delta);
    }

    public static void clear() {
        SPELL_DATA.clear();
    }
}
