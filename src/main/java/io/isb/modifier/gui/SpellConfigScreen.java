package io.isb.modifier.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.PacketUpdateConfig;
import io.isb.modifier.config.IEyiConfigParams;
import io.isb.modifier.spell.IChargedSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SpellConfigScreen extends Screen {

    private SpellList list;

    // 筛选与下拉菜单相关
    private SchoolType currentFilter = null;
    private List<SchoolType> availableSchools = new ArrayList<>();
    private FilterButton filterButton;
    private boolean isDropdownOpen = false;

    public SpellConfigScreen() {
        super(Component.literal("魔法平衡性配置"));
    }

    @Override
    protected void init() {
        this.availableSchools = SpellRegistry.getEnabledSpells().stream()
                .map(AbstractSpell::getSchoolType)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(s -> s.getDisplayName().getString()))
                .collect(Collectors.toList());

        int rowWidth = 410;
        int listLeft = (this.width - rowWidth) / 2;
        int btnY = 22;
        int listTop = 55;

        this.list = new SpellList(this.minecraft, this.width, this.height, listTop, this.height - 10, 24);
        this.addWidget(this.list);

        this.filterButton = new FilterButton(listLeft, btnY, 100, 16, Component.empty(), b -> {
            isDropdownOpen = !isDropdownOpen;
        });
        this.addRenderableWidget(this.filterButton);

        updateFilterButtonText();
        reloadSpells();
    }

    private void updateFilterButtonText() {
        String schoolName = currentFilter == null ? "全部" : currentFilter.getDisplayName().getString();
        this.filterButton.setMessage(Component.literal("筛选: " + schoolName));
    }

    private void reloadSpells() {
        this.list.clearConfigEntries();

        List<AbstractSpell> allSpells = SpellRegistry.getEnabledSpells().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AbstractSpell::getSpellName))
                .collect(Collectors.toList());

        if (currentFilter != null) {
            allSpells.stream()
                    .filter(s -> s.getSchoolType() == currentFilter)
                    .forEach(s -> this.list.addConfigEntry(new SpellEntry(s)));
        } else {
            Map<SchoolType, List<AbstractSpell>> grouped = allSpells.stream()
                    .filter(s -> s.getSchoolType() != null)
                    .collect(Collectors.groupingBy(AbstractSpell::getSchoolType));

            List<SchoolType> sortedSchools = new ArrayList<>(grouped.keySet());
            sortedSchools.sort(Comparator.comparing(s -> s.getDisplayName().getString()));

            for (SchoolType school : sortedSchools) {
                this.list.addConfigEntry(new CategoryEntry(school));
                List<AbstractSpell> spellsInSchool = grouped.get(school);
                for (AbstractSpell spell : spellsInSchool) {
                    this.list.addConfigEntry(new SpellEntry(spell));
                }
            }
        }
        this.list.setScrollAmount(0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isDropdownOpen) {
            int x = this.filterButton.getX();
            int y = this.filterButton.getY() + this.filterButton.getHeight() + 1;
            int w = this.filterButton.getWidth();

            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + 15) {
                currentFilter = null;
                isDropdownOpen = false;
                updateFilterButtonText();
                reloadSpells();
                return true;
            }

            for (int i = 0; i < availableSchools.size(); i++) {
                int itemY = y + 15 + (i * 15);
                if (mouseX >= x && mouseX <= x + w && mouseY >= itemY && mouseY < itemY + 15) {
                    currentFilter = availableSchools.get(i);
                    isDropdownOpen = false;
                    updateFilterButtonText();
                    reloadSpells();
                    return true;
                }
            }

            if (mouseY > this.filterButton.getY() + this.filterButton.getHeight()) {
                isDropdownOpen = false;
                updateFilterButtonText();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xD0101010, 0xE0050505);

        this.list.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int rowWidth = 410;
        int listLeft = (this.width - rowWidth) / 2;
        int headerY = 42;

        guiGraphics.fill(listLeft - 10, headerY - 1, listLeft + rowWidth + 20, headerY, 0x55FFFFFF);
        guiGraphics.fill(listLeft - 10, headerY + 11, listLeft + rowWidth + 20, headerY + 12, 0x55FFFFFF);

        guiGraphics.drawString(this.font, "法术", listLeft + 25, headerY + 2, 0xFFFFFF, true);
        guiGraphics.drawCenteredString(this.font, "层数", listLeft + 130, headerY + 2, 0xCCCCCC);
        guiGraphics.drawCenteredString(this.font, "伤害", listLeft + 200, headerY + 2, 0xFF8888);
        guiGraphics.drawCenteredString(this.font, "冷却", listLeft + 270, headerY + 2, 0x88FFFF);
        guiGraphics.drawCenteredString(this.font, "法力", listLeft + 340, headerY + 2, 0x8888FF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (isDropdownOpen) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            renderDropdown(guiGraphics, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private void renderDropdown(GuiGraphics g, int mx, int my) {
        int x = this.filterButton.getX();
        int y = this.filterButton.getY() + this.filterButton.getHeight() + 1;
        int w = this.filterButton.getWidth();
        int h = 15 + (availableSchools.size() * 15);

        g.fill(x, y, x + w, y + h, 0xFF181818);
        g.renderOutline(x, y, w, h, 0xFF404040);

        boolean hoverAll = mx >= x && mx <= x + w && my >= y && my < y + 15;
        if (hoverAll) g.fill(x + 1, y + 1, x + w - 1, y + 14, 0x33FFFFFF);
        g.drawString(this.font, "全部", x + 8, y + 4, 0xFFFFFF, false);

        for (int i = 0; i < availableSchools.size(); i++) {
            SchoolType school = availableSchools.get(i);
            int itemY = y + 15 + (i * 15);
            boolean hover = mx >= x && mx <= x + w && my >= itemY && my < itemY + 15;
            if (hover) g.fill(x + 1, itemY + 1, x + w - 1, itemY + 14, 0x33FFFFFF);
            int textColor = hover ? 0xFFFFFFFF : 0xAAAAAA;
            g.drawString(this.font, school.getDisplayName(), x + 8, itemY + 4, textColor, false);
        }
    }

    // ==========================================
    // FilterButton
    // ==========================================
    class FilterButton extends AbstractButton {
        private final OnPress onPress;

        public FilterButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
            super(x, y, w, h, msg);
            this.onPress = onPress;
        }

        @Override public void onPress() { this.onPress.onPress(this); }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused() || isDropdownOpen;
            int bgColor = hovered ? 0x90222222 : 0x60000000;
            int borderColor = hovered ? 0xFF666666 : 0xFF444444;

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
            String text = this.getMessage().getString();
            String symbol = isDropdownOpen ? "▲" : "▼";

            guiGraphics.drawString(Minecraft.getInstance().font, text, getX() + 6, getY() + (height - 8) / 2, textColor, false);
            int symbolW = Minecraft.getInstance().font.width(symbol);
            guiGraphics.drawString(Minecraft.getInstance().font, symbol, getX() + width - symbolW - 6, getY() + (height - 8) / 2, 0x888888, false);
        }

        @Override public void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        public interface OnPress { void onPress(FilterButton button); }
    }

    abstract class AbstractConfigEntry extends ContainerObjectSelectionList.Entry<AbstractConfigEntry> {}

    class CategoryEntry extends AbstractConfigEntry {
        private final SchoolType school;
        public CategoryEntry(SchoolType school) { this.school = school; }
        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            guiGraphics.fill(left, top + 2, left + width, top + height - 2, 0x44000000);
            Component title = Component.literal("- ").append(school.getDisplayName()).append(" -");
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, title, left + width / 2, top + (height - 8) / 2, 0xFFD700);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

    // ==========================================
    // SpellEntry (🔥 核心修改：支持输入框 + 冷却小数)
    // ==========================================
    class SpellEntry extends AbstractConfigEntry {
        private final AbstractSpell spell;
        private final MiniButton btnChargeMinus, btnChargePlus;
        private final MiniButton btnPowerMinus, btnPowerPlus;
        private final MiniButton btnCdMinus, btnCdPlus;
        private final MiniButton btnManaMinus, btnManaPlus;

        // 输入框
        private final EditBox edCharges;
        private final EditBox edPower;
        private final EditBox edCooldown;
        private final EditBox edMana;

        // 数据
        private int displayCharges;
        private double displayPower;
        private double displayCooldown; // 🔥 改为 double
        private double displayMana;

        public SpellEntry(AbstractSpell spell) {
            this.spell = spell;

            // 1. 初始化数值
            if (spell instanceof IChargedSpell chargedSpell) { this.displayCharges = chargedSpell.eyi$getMaxCharges(); } else { this.displayCharges = 1; }
            var config = ServerConfigs.getSpellConfig(spell);
            if (config instanceof IEyiConfigParams eyiParams) {
                this.displayPower = eyiParams.eyi$getPowerMultiplier();
                this.displayCooldown = eyiParams.eyi$getCooldownMultiplier(); // 🔥 读取 double
                this.displayMana = eyiParams.eyi$getManaMultiplier();
            } else { this.displayPower = 1.0; displayCooldown = 1.0; displayMana = 1.0; }

            // 2. 初始化按钮 (点击时调用带 boolean 的 setter，true表示需要同步更新文本框)
            int btnW = 12; int btnH = 20;
            this.btnChargeMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> setCharges(displayCharges - 1, true));
            this.btnChargePlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> setCharges(displayCharges + 1, true));
            this.btnPowerMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> setPower(displayPower - 0.1, true));
            this.btnPowerPlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> setPower(displayPower + 0.1, true));
            this.btnCdMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> setCooldown(displayCooldown - 0.1, true));
            this.btnCdPlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> setCooldown(displayCooldown + 0.1, true));
            this.btnManaMinus = new MiniButton(0, 0, btnW, btnH, Component.literal("-"), b -> setMana(displayMana - 0.1, true));
            this.btnManaPlus = new MiniButton(0, 0, btnW, btnH, Component.literal("+"), b -> setMana(displayMana + 0.1, true));

            // 3. 初始化输入框
            // 我们在 render 里设置位置，这里只需设置尺寸和逻辑
            int editW = 32; int editH = 16;

            this.edCharges = createEditBox(String.valueOf(displayCharges), val -> {
                try {
                    int v = Integer.parseInt(val);
                    setCharges(v, false); // false: 不要反向更新输入框，因为用户正在打字
                } catch(NumberFormatException ignored) {}
            });

            this.edPower = createEditBox(formatDouble(displayPower), val -> {
                try { setPower(Double.parseDouble(val), false); } catch(NumberFormatException ignored) {}
            });

            this.edCooldown = createEditBox(formatDouble(displayCooldown), val -> {
                try { setCooldown(Double.parseDouble(val), false); } catch(NumberFormatException ignored) {}
            });

            this.edMana = createEditBox(formatDouble(displayMana), val -> {
                try { setMana(Double.parseDouble(val), false); } catch(NumberFormatException ignored) {}
            });
        }

        // 辅助方法：创建统一风格的输入框
        private EditBox createEditBox(String initVal, Consumer<String> responder) {
            EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 32, 14, Component.empty());
            box.setValue(initVal);
            box.setBordered(true); // 保留边框以便知道哪里可以点
            box.setMaxLength(6);
            box.setResponder(responder);
            return box;
        }

        private String formatDouble(double val) { return String.format("%.1f", val); }

        // Setter: 更新数据 + 发包 + 可选更新文本框
        private void setCharges(int val, boolean updateBox) {
            int newVal = Math.max(1, Math.min(20, val));
            if (newVal != this.displayCharges) {
                this.displayCharges = newVal;
                if (updateBox) this.edCharges.setValue(String.valueOf(newVal));
                ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_CHARGES, newVal));
            }
        }
        private void setPower(double val, boolean updateBox) {
            double newVal = Math.round(val * 10.0) / 10.0;
            newVal = Math.max(0.1, Math.min(10.0, newVal));
            if (Math.abs(newVal - this.displayPower) > 0.001) {
                this.displayPower = newVal;
                if (updateBox) this.edPower.setValue(formatDouble(newVal));
                ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_POWER, newVal));
            }
        }
        private void setCooldown(double val, boolean updateBox) {
            double newVal = Math.round(val * 10.0) / 10.0;
            newVal = Math.max(0.0, Math.min(10.0, newVal));
            if (Math.abs(newVal - this.displayCooldown) > 0.001) {
                this.displayCooldown = newVal;
                if (updateBox) this.edCooldown.setValue(formatDouble(newVal));
                // 🔥 这里发包需要 Server 端支持 double，通常 Mod 配置包都是通用的
                ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_COOLDOWN, newVal));
            }
        }
        private void setMana(double val, boolean updateBox) {
            double newVal = Math.round(val * 10.0) / 10.0;
            newVal = Math.max(0.0, Math.min(10.0, newVal));
            if (Math.abs(newVal - this.displayMana) > 0.001) {
                this.displayMana = newVal;
                if (updateBox) this.edMana.setValue(formatDouble(newVal));
                ModMessage.sendToServer(new PacketUpdateConfig(spell.getSpellId(), PacketUpdateConfig.TYPE_MANA, newVal));
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            int bgColor = isHovered ? 0x22FFFFFF : 0x11FFFFFF;
            guiGraphics.fill(left - 2, top, left + width + 2, top + height, bgColor);
            int btnY = top + (height - 20) / 2;
            int editY = top + (height - 14) / 2;

            // 调整布局间距，给输入框留出 32px 空间
            // 按钮1(12px) + 间距(2px) + 输入框(32px) + 间距(2px) + 按钮2(12px) = 总宽 60px
            // 列间距 70px，足够
            int xCharges = left + 100;
            int xPower = left + 170;
            int xCool = left + 240;
            int xMana = left + 310;

            // 渲染图标
            ResourceLocation icon = spell.getSpellIconResource();
            if (icon != null) {
                RenderSystem.setShaderTexture(0, icon);
                guiGraphics.blit(icon, left + 4, top + (height - 16) / 2, 0, 0, 16, 16, 16, 16);
            }

            // 渲染法术名
            String name = spell.getDisplayName(null).getString();
            if (Minecraft.getInstance().font.width(name) > 75) { name = Minecraft.getInstance().font.plainSubstrByWidth(name, 70) + "..."; }
            guiGraphics.drawString(Minecraft.getInstance().font, name, left + 24, top + (height - 8) / 2, 0xFFFFFF, false);

            // 渲染控制组件组
            renderControlGroup(guiGraphics, btnChargeMinus, edCharges, btnChargePlus, xCharges, btnY, editY, mouseX, mouseY, partialTick);
            renderControlGroup(guiGraphics, btnPowerMinus, edPower, btnPowerPlus, xPower, btnY, editY, mouseX, mouseY, partialTick);
            renderControlGroup(guiGraphics, btnCdMinus, edCooldown, btnCdPlus, xCool, btnY, editY, mouseX, mouseY, partialTick);
            renderControlGroup(guiGraphics, btnManaMinus, edMana, btnManaPlus, xMana, btnY, editY, mouseX, mouseY, partialTick);
        }

        private void renderControlGroup(GuiGraphics g, AbstractButton b1, EditBox box, AbstractButton b2, int x, int btnY, int editY, int mx, int my, float pt) {
            b1.setX(x); b1.setY(btnY);
            box.setX(x + 14); box.setY(editY);
            b2.setX(x + 48); b2.setY(btnY);

            b1.render(g, mx, my, pt);
            box.render(g, mx, my, pt);
            b2.render(g, mx, my, pt);
        }

        // 🔥 必须重写 children 和 narratables，确保输入框能接收到点击和键盘事件
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(btnChargeMinus, btnChargePlus, edCharges,
                    btnPowerMinus, btnPowerPlus, edPower,
                    btnCdMinus, btnCdPlus, edCooldown,
                    btnManaMinus, btnManaPlus, edMana);
        }
        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(btnChargeMinus, btnChargePlus, edCharges,
                    btnPowerMinus, btnPowerPlus, edPower,
                    btnCdMinus, btnCdPlus, edCooldown,
                    btnManaMinus, btnManaPlus, edMana);
        }
    }

    class MiniButton extends AbstractButton {
        private final OnPress onPress;
        public MiniButton(int x, int y, int width, int height, Component message, OnPress onPress) { super(x, y, width, height, message); this.onPress = onPress; }
        public interface OnPress { void onPress(MiniButton button); }
        @Override public void onPress() { this.onPress.onPress(this); }
        @Override public void updateWidgetNarration(NarrationElementOutput output) { this.defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? 0x88666666 : 0x44000000;
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            int textColor = hovered ? 0xFFFFFFFF : 0xFFAAAAAA;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }

    class SpellList extends ContainerObjectSelectionList<AbstractConfigEntry> {
        public SpellList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }
        @Override protected void renderBackground(GuiGraphics guiGraphics) { }
        public void addConfigEntry(AbstractConfigEntry entry) { super.addEntry(entry); }
        public void clearConfigEntries() { super.clearEntries(); }
        @Override public int getRowWidth() { return 410; }
        @Override protected int getScrollbarPosition() { return this.width / 2 + 225; }
    }
}
