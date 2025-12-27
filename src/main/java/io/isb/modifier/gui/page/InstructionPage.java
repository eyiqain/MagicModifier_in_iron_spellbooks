package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

/**
 * 右侧详情页窗口
 * 修复了类型转换错误：String vs Component
 */
public class InstructionPage extends SpellScreen.UiWindow {

    private static final int BG_U = 147;
    private static final int BG_V = 2;

    private static final int CONTENT_TOP = 10;

    // === 核心配置：文本显示范围 ===
    private static final int TEXT_LEFT = 17;
    private static final int TEXT_RIGHT = 121;
    private static final int TEXT_W = TEXT_RIGHT - TEXT_LEFT;

    // === 样式配置 ===
    // 纯黑色
    private static final int COLOR_TEXT = 0xFF000000;
    // 黑色阴影（设为 false 更清晰，设为 true 则是你要的阴影）
    private static final boolean USE_SHADOW = true;

    public InstructionPage(SpellScreen host) {
        super(host);
    }

    @Override
    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
        // 0) 画背景
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEXTURE, 0, 0, BG_U, BG_V, w, h, 512, 512);

        // 1) 只有被激活时才渲染
        if (this.host.getActiveRightWindow() != this) {
            return;
        }

        // 2) 获取选中的法术
        AbstractSpell spell = null;
        int level = 0;

        if (host.getActiveLeftWindow() instanceof SpellPage sp) {
            spell = sp.getSelectedSpell();
            level = sp.getSelectedSpellLevel();
        }

        Font font = this.host.getMinecraft().font;

        // 3) 未选中法术时的提示
        if (spell == null || level <= 0) {
            int y = CONTENT_TOP;
            Component t = Component.literal("未选择法术").withStyle(ChatFormatting.UNDERLINE);
            y = drawComponentWrapped(g, font, t, y) + 4;

            Component tip = Component.literal("请在左侧列表点击一个法术。");
            drawComponentWrapped(g, font, tip, y);
            return;
        }

        // 4) 选中后的详情绘制
        var player = Minecraft.getInstance().player;

        // 定义一个纯黑的基础样式，覆盖原有的颜色
        Style blackStyle = Style.EMPTY.withColor(0x000000);

        int y = CONTENT_TOP;

        // --- 标题 ---
        Component title = spell.getDisplayName(player).copy().withStyle(ChatFormatting.UNDERLINE).withStyle(blackStyle);
        y = drawComponentWrapped(g, font, title, y) + 4;

        // --- 学派 (School) ---
        Component school = spell.getSchoolType().getDisplayName().copy().withStyle(blackStyle);
        y = drawComponentWrapped(g, font, school, y);

        // --- 等级 (Level) ---
        Component lvl = Component.translatable("ui.irons_spellbooks.level", level).withStyle(blackStyle);
        y = drawComponentWrapped(g, font, lvl, y) + 6;

        // --- 蓝耗 (Mana) ---
        int manaCost = spell.getManaCost(level);
        Component manaLine = Component.translatable("ui.irons_spellbooks.mana_cost", manaCost).withStyle(blackStyle);
        y = drawComponentWrapped(g, font, manaLine, y);

        // --- 施法时间 (Cast Time) ---
        // 修复点：timeFromTicks 返回 String，而不是 Component
        String castTimeStr = Utils.timeFromTicks(spell.getEffectiveCastTime(level, null), 1);

        // TooltipsUtils.getCastTimeComponent 接受 String
        Component castLine = TooltipsUtils.getCastTimeComponent(spell.getCastType(), castTimeStr).copy().withStyle(blackStyle);
        y = drawComponentWrapped(g, font, castLine, y);

        // --- 冷却 (Cooldown) ---
        // 修复点：同上，这里返回的是 String
        String cdStr = Utils.timeFromTicks(spell.getSpellCooldown(), 1);

        // Component.translatable 第二个参数可以是 String
        Component cdLine = Component.translatable("ui.irons_spellbooks.cooldown", cdStr).withStyle(blackStyle);
        y = drawComponentWrapped(g, font, cdLine, y);

        // --- 特殊属性 (Unique Info) ---
        List<MutableComponent> uniques = spell.getUniqueInfo(level, null);
        for (MutableComponent c : uniques) {
            if (y > h - 12) break;
            y = drawComponentWrapped(g, font, c.withStyle(blackStyle), y);
        }

        // --- 描述 (Description) ---
        if (y <= h - 24) {
            y += 6;
            Component descTitle = Component.literal("描述").withStyle(ChatFormatting.UNDERLINE).withStyle(blackStyle);
            y = drawComponentWrapped(g, font, descTitle, y);

            // 获取描述行
            List<FormattedCharSequence> descLines = TooltipsUtils.createSpellDescriptionTooltip(spell, font);

            for (FormattedCharSequence line : descLines) {
                if (y > h - 10) break;
                // 直接绘制“死”文本，不进行 split（因为已经是渲染序列了）
                g.drawString(font, line, TEXT_LEFT, y, COLOR_TEXT, USE_SHADOW);
                y += font.lineHeight;
            }
        }
    }

    /**
     * 辅助方法：将 Component 限制在 TEXT_W 宽度内绘制
     * 超出自动换行
     */
    private int drawComponentWrapped(GuiGraphics g, Font font, Component text, int y) {
        // 使用 font.split(Component, width) 将文本切分成多行
        List<FormattedCharSequence> lines = font.split(text, TEXT_W);

        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, TEXT_LEFT, y, COLOR_TEXT, USE_SHADOW);
            y += font.lineHeight;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double localX, double localY, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean mouseReleased(double localX, double localY, int button) {
        return false;
    }
}
