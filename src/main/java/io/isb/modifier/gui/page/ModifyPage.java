package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import net.minecraft.client.gui.GuiGraphics;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

public class ModifyPage extends  SpellScreen.UiWindow{
    public ModifyPage(SpellScreen spellScreen) {
        super(spellScreen);
    }

    @Override
    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
//        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
//        g.blit(TEXTURE, 0,0, 13, 2, w, h, 512, 512);
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
