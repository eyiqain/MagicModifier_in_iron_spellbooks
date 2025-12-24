package io.isb.modifier.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.spell.IChargedSpell;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.config.ClientConfigs;
import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.player.ClientRenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = MagicModifier.MODID, value = Dist.CLIENT)
public class ClientChargeRenderer {

    // 复用原版材质来画遮罩，或者使用纯色
    public final static ResourceLocation TEXTURE = new ResourceLocation("irons_spellbooks", "textures/gui/icons.png");

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().getPath().equals("spell_bar")) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;

        var ssm = ClientMagicData.getSpellSelectionManager();
        if (ssm.getSpellCount() <= 0) return;

        // --- 坐标计算 (复制原版逻辑) ---
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int configOffsetY = ClientConfigs.SPELL_BAR_Y_OFFSET.get();
        int configOffsetX = ClientConfigs.SPELL_BAR_X_OFFSET.get();
        SpellBarOverlay.Anchor anchor = ClientConfigs.SPELL_BAR_ANCHOR.get();

        int centerX, centerY;
        if (anchor == SpellBarOverlay.Anchor.Hotbar) {
            centerX = screenWidth / 2 - Math.max(110, screenWidth / 4);
            centerY = screenHeight - Math.max(55, screenHeight / 8);
        } else {
            int mx = 0, my = 0;
            switch (anchor) {
                case TopRight -> { mx = 1; my = 0; }
                case BottomLeft -> { mx = 0; my = 1; }
                case BottomRight -> { mx = 1; my = 1; }
            }
            centerX = screenWidth * mx;
            centerY = screenHeight * my;
        }
        centerX += configOffsetX;
        centerY += configOffsetY;

        List<Vec2> locations = ClientRenderCache.relativeSpellBarSlotLocations;
        if (locations.isEmpty()) return;
        int approximateWidth = locations.size() / 3;
        centerX -= approximateWidth * 5;
        // ---------------------------

        GuiGraphics guiHelper = event.getGuiGraphics();
        List<SpellSelectionManager.SelectionOption> spells = ssm.getAllSpells();

        for (int i = 0; i < locations.size() && i < spells.size(); i++) {
            AbstractSpell spell = spells.get(i).spellData.getSpell();

            if (spell instanceof IChargedSpell chargedSpell) {
                int maxCharges = chargedSpell.eyi$getMaxCharges();

                if (maxCharges > 1) {
                    // 1. 获取网络同步的数据
                    int syncedCharges = ClientChargeData.getCharges(spell);
                    int accumulated = ClientChargeData.getPredictedAccumulated(spell,player);

                    // 2. 判断是否处于“真冷却”状态
                    float standardCooldown = ClientMagicData.getCooldownPercent(spell);
                    boolean isOnCooldown = standardCooldown > 0;

                    int x = centerX + (int) locations.get(i).x;
                    int y = centerY + (int) locations.get(i).y;

                    // 4. 绘制充能进度遮罩 (仅当 不在冷却 且 未满层 时)
                    if (!isOnCooldown && syncedCharges < maxCharges) {
                        int baseCooldown = spell.getSpellCooldown();
                        if (baseCooldown > 0) {
                            // 计算当前这层积攒了多少
                            // accumulated 是总积攒量，取模 baseCooldown 得到当前层的进度
                            int progressTicks = accumulated % baseCooldown;
                            float percent = (float) progressTicks / baseCooldown;

                            // 绘制遮罩
                            renderChargingOverlay(guiHelper, x, y, percent);
                        }
                    }

                    // 5. 绘制层数数字
                    renderChargeCount(guiHelper, x, y, syncedCharges, maxCharges);
                }
            }
        }
    }

    private static void renderChargingOverlay(GuiGraphics guiHelper, int x, int y, float percent) {
        // 限制百分比
        if (percent < 0) percent = 0;
        if (percent > 1) percent = 1;

        // 计算遮罩高度 (类似原版冷却，从满到空，或者从空到满)
        // 原版冷却是从下往上消失 (高 -> 低)。
        // 充能我们做成：从下往上增长？
        int fullHeight = 16;
        int currentHeight = (int) (fullHeight * percent); // 比如 50% = 8像素

        // 绘制位置：图标通常在 (x+3, y+3)
        // 我们从底部向上画
        // x+3, y+3 + (16 - height)

        // 使用半透明白色/淡蓝色遮罩
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 设置颜色：淡青色 (RGBA)
        RenderSystem.setShaderColor(0.5f, 1.0f, 1.0f, 0.4f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // 我们复用原版图标纹理里的一块纯白区域或者通用纹理
        // 这里为了简单，直接用 blit 绘制一部分纹理，但要保证颜色叠加
        // 或者直接 fill 矩形 (GuiGraphics.fill) 更简单！

        // 绘制半透明矩形
        // x+3, y+3 是左上角
        // 底部 y 是 y+3+16 = y+19
        // 顶部 y 是 y+19 - currentHeight
        int rectX = x + 3;
        int rectY = y + 19 - currentHeight;
        int rectWidth = 16;
        int rectHeight = currentHeight;

        // ARGB 颜色: 0x66AAFFFF (半透明蓝)
        guiHelper.fill(rectX, rectY, rectX + rectWidth, rectY + rectHeight, 0x66AAFFFF);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // 重置颜色
    }

    private static void renderChargeCount(GuiGraphics guiHelper, int x, int y, int current, int max) {
        PoseStack poseStack = guiHelper.pose();
        poseStack.pushPose();

        float scale = 0.7f;
        float textX = (x + 4) / scale;
        float textY = (y + 4) / scale;

        poseStack.scale(scale, scale, scale);

        String text = String.valueOf(current);

        // 0层灰色，满层金色，其他亮蓝
        int color = 0xFF55FFFF;
        if (current == 0) color = 0xFFAAAAAA;
        else if (current == max) color = 0xFFFFAA00;

        guiHelper.drawString(Minecraft.getInstance().font, text, (int)textX, (int)textY, color, true);

        poseStack.popPose();
    }
}
