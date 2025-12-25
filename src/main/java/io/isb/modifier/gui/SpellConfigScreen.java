package io.isb.modifier.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.PacketUpdateConfig;
import io.isb.modifier.config.IEyiConfigParams;
import io.isb.modifier.spell.IChargedSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

public class SpellConfigScreen extends Screen {

    private SpellList list;

    public SpellConfigScreen() {
        super(Component.literal("魔法平衡性配置"));
    }

    @Override
    protected void init() {
        this.list = new SpellList(this.minecraft, this.width, this.height, 35, this.height - 10, 24);
        this.addWidget(this.list);

        SpellRegistry.getEnabledSpells().stream()
                .sorted(Comparator.comparing(AbstractSpell::getSpellName))
                .forEach(spell -> this.list.addSpell(new SpellEntry(spell)));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 全屏深色磨砂背景
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xD0101010, 0xE0050505);

        this.list.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // 表头装饰
        int rowWidth = 410;
        int listLeft = (this.width - rowWidth) / 2;
        int headerY = 22;

        guiGraphics.fill(listLeft - 10, headerY - 4, listLeft + rowWidth + 20, headerY + 10, 0x22FFFFFF);
        guiGraphics.fill(listLeft - 10, headerY + 10, listLeft + rowWidth + 20, headerY + 11, 0x55FFFFFF);

        // 表头文字
        guiGraphics.drawString(this.font, "法术", listLeft + 25, headerY, 0xFFFFFF, true);
        guiGraphics.drawCenteredString(this.font, "层数", listLeft + 130, headerY, 0xCCCCCC);
        guiGraphics.drawCenteredString(this.font, "伤害", listLeft + 200, headerY, 0xFF8888);
        guiGraphics.drawCenteredString(this.font, "冷却", listLeft + 270, headerY, 0x88FFFF);
        guiGraphics.drawCenteredString(this.font, "法力", listLeft + 340, headerY, 0x8888FF);
    }

    // ==========================================
    // 自定义极简按钮类 (🔥 新增)
    // ==========================================
    class MiniButton extends AbstractButton {
        private final Button.OnPress onPress;

        public MiniButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            this.onPress.onPress(null); // 这里的参数可以传 null 或者 this，视具体需求
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            // 🎨 美化逻辑：
            // 平时：深色半透明 (0x44000000)
            // 悬停：亮灰色半透明 (0x88666666)
            int bgColor = hovered ? 0x88666666 : 0x44000000;

            // 绘制扁平背景矩形
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            // 绘制文字 (白色，悬停时更亮)
            int textColor = hovered ? 0xFFFFFFFF : 0xFFAAAAAA;

            // 手动绘制居中文字，不带阴影看起来更现代 (false)
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2,
                    textColor);
        }
    }

    // ==========================================
    // 列表类
    // ==========================================
    class SpellList extends ContainerObjectSelectionList<SpellEntry> {
        public SpellList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }
        @Override protected void renderBackground(GuiGraphics guiGraphics) { } // 透明
        public void addSpell(SpellEntry entry) { super.addEntry(entry); }
        @Override public int getRowWidth() { return 410; }
        @Override protected int getScrollbarPosition() { return this.width / 2 + 225; }
    }

    // ==========================================
    // 列表行类
    // ==========================================
    class SpellEntry extends ContainerObjectSelectionList.Entry<SpellEntry> {
        private final AbstractSpell spell;

        // 🔥 改用 MiniButton
        private final MiniButton btnChargeMinus, btnChargePlus;
        private final MiniButton btnPowerMinus, btnPowerPlus;
        private final MiniButton btnCdMinus, btnCdPlus;
        private final MiniButton btnManaMinus, btnManaPlus;

        private int displayCharges;
        private double displayPower;
        private int displayCooldown;
        private double displayMana;

        public SpellEntry(AbstractSpell spell) {
            this.spell = spell;

            if (spell instanceof IChargedSpell chargedSpell) {
                this.displayCharges = chargedSpell.eyi$getMaxCharges();
            } else { this.displayCharges = 1; }

            var config = ServerConfigs.getSpellConfig(spell);
            if (config instanceof IEyiConfigParams eyiParams) {
                this.displayPower = eyiParams.eyi$getPowerMultiplier();
                this.displayCooldown = (int) Math.round(eyiParams.eyi$getCooldownMultiplier());
                this.displayMana = eyiParams.eyi$getManaMultiplier();
            } else {
                this.displayPower = 1.0; displayCooldown = 1; displayMana = 1.0;
            }

            int btnW = 12; int btnH = 20;

            // 🔥 使用 new MiniButton 替换 Button.builder
            this.btnChargeMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> updateCharges(-1));
            this.btnChargePlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> updateCharges(1));

            this.btnPowerMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> updatePower(-0.1));
            this.btnPowerPlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> updatePower(0.1));

            this.btnCdMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> updateCooldown(-1));
            this.btnCdPlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> updateCooldown(1));

            this.btnManaMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> updateMana(-0.1));
            this.btnManaPlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> updateMana(0.1));
        }

        private void updateCharges(int delta) {
            int newVal = Math.max(1, Math.min(20, this.displayCharges + delta));
            if (newVal != this.displayCharges) { this.displayCharges = newVal; ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_CHARGES, newVal)); }
        }
        private void updatePower(double delta) {
            double newVal = Math.round((this.displayPower + delta) * 10.0) / 10.0;
            if (newVal < 0.1) newVal = 0.1; if (newVal > 10.0) newVal = 10.0;
            if (Math.abs(newVal - this.displayPower) > 0.001) { this.displayPower = newVal; ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_POWER, newVal)); }
        }
        private void updateCooldown(int delta) {
            int newVal = Math.max(0, Math.min(10, this.displayCooldown + delta));
            if (newVal != this.displayCooldown) { this.displayCooldown = newVal; ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_COOLDOWN, (double) newVal)); }
        }
        private void updateMana(double delta) {
            double newVal = Math.round((this.displayMana + delta) * 10.0) / 10.0;
            if (newVal < 0.0) newVal = 0.0; if (newVal > 10.0) newVal = 10.0;
            if (Math.abs(newVal - this.displayMana) > 0.001) { this.displayMana = newVal; ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_MANA, newVal)); }
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            // 行背景
            int bgColor = isHovered ? 0x22FFFFFF : 0x11FFFFFF;
            guiGraphics.fill(left - 2, top, left + width + 2, top + height, bgColor);

            int btnY = top + (height - 20) / 2;
            int textY = top + (height - 8) / 2;
            int xCharges = left + 105; int xPower = left + 175; int xCool = left + 245; int xMana = left + 315;

            // 图标
            ResourceLocation icon = spell.getSpellIconResource();
            RenderSystem.setShaderTexture(0, icon);
            guiGraphics.blit(icon, left + 4, top + (height - 16) / 2, 0, 0, 16, 16, 16, 16);

            // 名字
            String name = spell.getDisplayName(null).getString();
            if (Minecraft.getInstance().font.width(name) > 75) { name = Minecraft.getInstance().font.plainSubstrByWidth(name, 70) + "..."; }
            guiGraphics.drawString(Minecraft.getInstance().font, name, left + 24, textY, 0xFFFFFF, false);

            // 控件
            renderControl(guiGraphics, btnChargeMinus, btnChargePlus, String.valueOf(displayCharges), xCharges, btnY, textY, 0xAAAAAA, mouseX, mouseY, partialTick);
            renderControl(guiGraphics, btnPowerMinus, btnPowerPlus, String.format("%.1f", displayPower), xPower, btnY, textY, 0xFF5555, mouseX, mouseY, partialTick);
            renderControl(guiGraphics, btnCdMinus, btnCdPlus, displayCooldown + "x", xCool, btnY, textY, 0x55FFFF, mouseX, mouseY, partialTick);
            renderControl(guiGraphics, btnManaMinus, btnManaPlus, String.format("%.1f", displayMana), xMana, btnY, textY, 0x5555FF, mouseX, mouseY, partialTick);
        }

        private void renderControl(GuiGraphics g, AbstractButton b1, AbstractButton b2, String text, int x, int btnY, int textY, int color, int mx, int my, float pt) {
            b1.setX(x); b1.setY(btnY); b2.setX(x + 35); b2.setY(btnY);
            // 手动调用 renderWidget 或者 render (render 会处理 tooltip 等，renderWidget 是核心绘制)
            b1.render(g, mx, my, pt);
            b2.render(g, mx, my, pt);
            g.drawCenteredString(Minecraft.getInstance().font, text, x + 24, textY, color);
        }

        @Override public List<? extends GuiEventListener> children() { return List.of(btnChargeMinus, btnChargePlus, btnPowerMinus, btnPowerPlus, btnCdMinus, btnCdPlus, btnManaMinus, btnManaPlus); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(btnChargeMinus, btnChargePlus, btnPowerMinus, btnPowerPlus, btnCdMinus, btnCdPlus, btnManaMinus, btnManaPlus); }
    }
}
