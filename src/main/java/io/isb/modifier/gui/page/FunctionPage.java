package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.player.ClientRenderCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

import java.util.Objects;
import java.util.Optional;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

public class FunctionPage extends SpellScreen.UiWindow {

    public FunctionPage(SpellScreen host) {
        super(host);
    }

    // 合成区标题
    private static final int SYNTH_LABEL_X = 159;
    private static final int SYNTH_LABEL_Y = 137-12;

    // 合成输入槽 (Input 1, Input 2)
    private static final int SYNTH_IN_X_1 = 160;
    private static final int SYNTH_IN_X_2 = 180;
    private static final int SYNTH_IN_Y_1 = 137;
    private static final int SYNTH_IN_Y_2 = 137;


    // 拖拽逻辑：右侧法术书
    private boolean isDraggingBook = false;
    private int draggedBookSlotIndex = -1;
    private SpellData draggedBookSpellData = SpellData.EMPTY;

    // 合成输出槽 (Output 1 - 产物, Output 2 - 装饰/副产物)
    private static final int SYNTH_OUT_X_1 = 217;
    private static final int SYNTH_OUT_X_2 = 237;
    private static final int SYNTH_OUT_Y_1 = 137;
    private static final int SYNTH_OUT_Y_2 = 137;

    // 合成按钮
    private static final int SYNTH_BTN_X = 201;
    private static final int SYNTH_BTN_Y = 139;
    private static final int SYNTH_BTN_W = 14;
    private static final int SYNTH_BTN_H = 14;

        // 右侧法术书区域
    private static final int BOOK_BOX_X = 160;
    private static final int BOOK_BOX_Y = 12;
    private static final int BOOK_BOX_WIDTH = 96;
    private static final int BOOK_BOX_HEIGHT = 80;

    // 统一槽位大小与纹理偏移
    private static final int SLOT_SIZE = 19;
    private static final int SYNTH_SLOT_SIZE = 19;
    private static final int SLOT_TEXTURE_V = 178; // 槽位背景在纹理图的Y坐标
    private static final int SLOT_OFFSET_NORMAL = 0;
    private static final int SLOT_OFFSET_HOVER = 19;
    private static final int SLOT_OFFSET_DRAG = 38;


    @Override
    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEXTURE, 0, 0, 278, 2, w, h, 512, 512);
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
    private void renderBookSlots(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
            ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
            if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) {
                Component msg = Component.literal("未携带魔法书！").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
                guiGraphics.drawCenteredString(this.host.getMinecraft().font, msg, left + BOOK_BOX_X + BOOK_BOX_WIDTH / 2, top + BOOK_BOX_Y + 30, 0xFFFFFFFF);
                return;
            }
            Component titleMsg = Component.literal("魔法书:").withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK);
            guiGraphics.drawString(this.host.getMinecraft().font, titleMsg, left + BOOK_BOX_X, top + BOOK_BOX_Y, 0xFF000000, false);

            ISpellContainer bookContainer = ISpellContainer.get(bookStack);
            int maxSpells = bookContainer.getMaxSpellCount();

            for (int i = 0; i < maxSpells; i++) {
                Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
                int slotX = (int) pos.x;
                int slotY = (int) pos.y;
                boolean isHovered = isHovering(slotX, slotY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);

                int uOffset = SLOT_OFFSET_NORMAL;
                if (( false) && isHovered) uOffset = SLOT_OFFSET_DRAG; // 可放置高亮
                else if (isHovered) uOffset = SLOT_OFFSET_HOVER;

                guiGraphics.blit(TEXTURE, slotX, slotY, uOffset, SLOT_TEXTURE_V, SLOT_SIZE, SLOT_SIZE, 512, 512);

                SpellData spellData = bookContainer.getSpellAtIndex(i);
                // 如果这格正在被拖拽，就不画原来的图标
                if (isDraggingBook && i == draggedBookSlotIndex) spellData = SpellData.EMPTY;

                if (spellData == null || spellData == SpellData.EMPTY) continue;

                AbstractSpell spell = spellData.getSpell();
                guiGraphics.blit(spell.getSpellIconResource(), slotX + 1, slotY + 1, 0, 0, 16, 16, 16, 16);
               // drawLevelBadge(guiGraphics, slotX, slotY, spellData.getLevel());
            }
        }
    private Vec2 getBookSlotPosition(int slotIndex, int totalSpells, int guiLeft, int guiTop) {
        int boxSize = SLOT_SIZE;
        int[] rowCounts = ClientRenderCache.getRowCounts(totalSpells);
        int rowIndex = 0; int colIndex = slotIndex;
        for (int r = 0; r < rowCounts.length; r++) {
            if (colIndex < rowCounts[r]) { rowIndex = r; break; }
            colIndex -= rowCounts[r];
        }
        int centerX = guiLeft + BOOK_BOX_X + BOOK_BOX_WIDTH / 2;
        int centerY = guiTop + BOOK_BOX_Y + BOOK_BOX_HEIGHT / 2;
        int totalHeight = rowCounts.length * boxSize;
        int currentRowWidth = rowCounts[rowIndex] * boxSize;
        int x = centerX - (currentRowWidth / 2) + (colIndex * boxSize);
        int y = centerY - (totalHeight / 2) + (rowIndex * boxSize);
        return new Vec2(x, y);
    }
//    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
//        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
//        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;
//        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
//        int maxSpells = bookContainer.getMaxSpellCount();
//        int left = (this.width - this.imageWidth) / 2;
//        int top = (this.height - this.imageHeight) / 2;
//        for (int i = 0; i < maxSpells; i++) {
//            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
//            if (isHovering((int)pos.x, (int)pos.y, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
//                if (!(isDragging || isDraggingBook)) {
//                    SpellData spellData = bookContainer.getSpellAtIndex(i);
//                    if (spellData != null && spellData != SpellData.EMPTY) {
//                        guiGraphics.renderTooltip(this.font, getTooltipLines(spellData), Optional.empty(), mouseX, mouseY);
//                    }
//                }
//                return;
//            }
//        }
//    }
    public static boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

///** 案例2：右侧书籍窗口 (Right Window 1) */
//private static class RightWindow1_Book extends SpellScreen.UiWindow {
//    RightWindow1_Book(SpellScreen host) { super(host); }
//
//    @Override
//    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
//        g.drawString(host.font, "Right Book (ID:" + getWindowId() + ")", 5, 5, 0xFF000000, false);
//
//        // 模拟一个书槽区域 (局部坐标 40,40)
//        g.fill(40, 40, 40 + 18, 40 + 18, 0xFF888888);
//
//        // 如果鼠标栈有东西，这里可以高亮提示“可放下”
//        if (!host.getMouseStack().isEmpty()) {
//            g.renderItemDecorations(host.font, host.getMouseStack(), 40, 40);
//        }
//    }
}
