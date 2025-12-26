package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import net.minecraft.client.gui.GuiGraphics;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

public class InstructionPage extends SpellScreen.UiWindow {

    public InstructionPage(SpellScreen host) {
        super(host);

    }

    @Override
    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEXTURE, 0, 0, 147, 2, w, h, 512, 512);
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

///** 案例1：左侧列表窗口 (Left Window 1) */
//private static class LeftWindow1_List extends SpellScreen.UiWindow {
//    LeftWindow1_List(SpellScreen host) { super(host); }
//    @Override
//    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
//        // 局部坐标绘制：(0,0) 就是左窗口的左上角
//        g.drawString(host.font, "Left List (ID:" + getWindowId() + ")", 5, 5, 0xFF000000, false);
//        g.drawString(host.font, "Item A", 10, 20, 0xFF444444, false);
//        g.drawString(host.font, "Item B", 10, 32, 0xFF444444, false);
//        // 简单的 Hover 效果
//        if (mouseY >= 20 && mouseY < 30) g.fill(8, 20, 100, 30, 0x400000FF);
//    }
//
//    @Override
//    public boolean mouseClicked(int mouseX, int mouseY, int button) {
//        if (mouseY >= 20 && mouseY < 30) {
//            // 点击 Item A
//            // host.minecraft.player.playSound(...);
//            return true; // 消费事件
//        }
//        return false;
//    }
//}