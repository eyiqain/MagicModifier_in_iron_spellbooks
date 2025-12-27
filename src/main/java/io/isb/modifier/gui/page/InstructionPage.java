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
 * 右侧详情页窗口（local 坐标绘制）
 * - 内容来自左侧 SpellPage 当前选中条目
 * - 只在自身为 activeRightWindow 时才渲染内容
 * - “描述(非 tooltip)”直接画在窗口里
 */
public class InstructionPage extends SpellScreen.UiWindow {

    // 背景区域：和 InstructionPage 一样
    private static final int BG_U = 147;
    private static final int BG_V = 2;

    // 内容布局（全部 local 坐标）
    private static final int PADDING = 6;
    private static final int CONTENT_W = 118 - PADDING * 2; // 右侧窗口宽 118（由 SpellScreen 注入）
    private static final int CONTENT_TOP = 10;

    // 固定显示范围的左和右 X 坐标
    private static final int TEXT_LEFT = 155;
    private static final int TEXT_RIGHT = 258;

    public InstructionPage(SpellScreen host) {
        super(host);
    }

    @Override
    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
        // 0) 画背景（local 坐标：窗口内 (0,0)）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEXTURE, 0, 0, BG_U, BG_V, w, h, 512, 512);

        // 1) 必须判断窗口是否被激活（你要求的）
        if (this.host.getActiveRightWindow() != this) {
            return;
        }

        // 2) 从左侧窗口读取选中的法术
        AbstractSpell spell = null;
        int level = 0;

        if (host.getActiveLeftWindow() instanceof SpellPage sp) {
            spell = sp.getSelectedSpell();
            level = sp.getSelectedSpellLevel();
        }

        // 3) 没选中 -> 提示
        if (spell == null || level <= 0) {
            drawCentered(g, this.host.getMinecraft().font,
                    Component.literal("未选择法术").withStyle(ChatFormatting.UNDERLINE),
                    w, CONTENT_TOP, 0xFFFFFFFF);

            g.drawWordWrap(this.host.getMinecraft().font,
                    Component.literal("请在左侧列表点击一个法术。"),
                    PADDING, CONTENT_TOP + 18, CONTENT_W, 0xFFE8E0D8);

            return;
        }

        // 4) 选中了 -> 画详情（仍然全部 local 坐标）
        var player = Minecraft.getInstance().player;
        Font font = this.host.getMinecraft().font;

        Style base = Style.EMPTY.withColor(0xE8E0D8);
        Style colorMana = Style.EMPTY.withColor(0x4FA0FF);
        Style colorCooldown = Style.EMPTY.withColor(0x55CC77);

        int y = CONTENT_TOP;

        // 标题（居中 + 下划线）
        Component title = spell.getDisplayName(player).copy()
                .withStyle(ChatFormatting.UNDERLINE);
        y = drawCenteredWrapped(g, font, title, w, y, CONTENT_W, 0xFFFFFFFF) + 4;

        // School（居中）
        Component school = spell.getSchoolType().getDisplayName();
        y = drawCenteredWrapped(g, font, school, w, y, CONTENT_W, 0xFFFFFFFF);

        // Level（居中）
        Component lvl = Component.translatable("ui.irons_spellbooks.level", level).withStyle(base);
        y = drawCenteredWrapped(g, font, lvl, w, y, CONTENT_W, 0xFFFFFFFF) + 6;

        // Mana
        int manaCost = spell.getManaCost(level);
        Component manaLine = Component.translatable("ui.irons_spellbooks.mana_cost",
                Component.literal(String.valueOf(manaCost)).withStyle(colorMana)
        ).withStyle(base);
        y += drawWrapped(g, font, manaLine, PADDING, y, CONTENT_W, 0xFFFFFFFF);

        // Cast Time
        Component castLine = TooltipsUtils.getCastTimeComponent(
                spell.getCastType(),
                Utils.timeFromTicks(spell.getEffectiveCastTime(level, null), 1)
        ).copy().withStyle(base);
        y += drawWrapped(g, font, castLine, PADDING, y, CONTENT_W, 0xFFFFFFFF);

        // Cooldown
        Component cdLine = Component.translatable("ui.irons_spellbooks.cooldown",
                Component.literal(Utils.timeFromTicks(spell.getSpellCooldown(), 1)).withStyle(colorCooldown)
        ).withStyle(base);
        y += drawWrapped(g, font, cdLine, PADDING, y, CONTENT_W, 0xFFFFFFFF);

        // Unique Info
        List<MutableComponent> uniques = spell.getUniqueInfo(level, null);
        for (MutableComponent c : uniques) {
            if (y > h - 12) break;
            y += drawWrapped(g, font, c.copy().withStyle(base), PADDING, y, CONTENT_W, 0xFFFFFFFF);
        }

        // 描述（非 tooltip）——把 tooltip 的描述内容当正文画出来
        if (y <= h - 24) {
            y += 6;
            Component descTitle = Component.literal("描述").withStyle(ChatFormatting.UNDERLINE).withStyle(base);
            y += drawWrapped(g, font, descTitle, PADDING, y, CONTENT_W, 0xFFFFFFFF);

            List<FormattedCharSequence> descLines = TooltipsUtils.createSpellDescriptionTooltip(spell, font);
            for (FormattedCharSequence line : descLines) {
                if (y > h - 10) break;
                g.drawString(font, line, PADDING, y, 0xFFE8E0D8, false);
                y += font.lineHeight;
            }
        }
    }

    // ===================== local 坐标辅助绘制 =====================

    private void drawCentered(GuiGraphics g, Font font, Component text, int winW, int y, int color) {
        int tw = font.width(text);
        int x = (winW - tw) / 2;
        g.drawString(font, text, x, y, color, false);
    }

    private int drawCenteredWrapped(GuiGraphics g, Font font, Component text, int winW, int y, int wrapW, int color) {
        List<FormattedCharSequence> lines = font.split(text, wrapW);
        for (FormattedCharSequence line : lines) {
            int tw = font.width(line);
            int x = (winW - tw) / 2;
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
        return y;
    }

    private int drawWrapped(GuiGraphics g, Font font, Component text, int x, int y, int wrapW, int color) {
        g.drawWordWrap(font, text, x, y, wrapW, color);
        return font.wordWrapHeight(text, wrapW);
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
