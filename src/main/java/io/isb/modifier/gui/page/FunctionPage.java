package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import net.minecraft.client.gui.GuiGraphics;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

public class FunctionPage extends SpellScreen.UiWindow {
    public FunctionPage(SpellScreen host) {
        super(host);
    }

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
}
//
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
