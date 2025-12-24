//package com.ninja.mixin.client;
//
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.ninja.client.ClientChargeData;
//import com.ninja.spell.IChargedSpell;
//import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
//import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
//import io.redspace.ironsspellbooks.config.ClientConfigs;
//import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
//import io.redspace.ironsspellbooks.player.ClientMagicData;
//import io.redspace.ironsspellbooks.player.ClientRenderCache;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.GuiGraphics;
//import net.minecraft.world.phys.Vec2;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
//import java.util.List;
//
//@Mixin(SpellBarOverlay.class)
//public class SpellBarOverlayMixin {
//
//    @Inject(method = "render", at = @At("TAIL"), remap = false)
//    private void eyi$renderSpellCharges(
//            net.minecraftforge.client.gui.overlay.ForgeGui gui,
//            GuiGraphics guiHelper,
//            float partialTick,
//            int screenWidth,
//            int screenHeight,
//            CallbackInfo ci
//    ) {
//        // 1. 重新获取必要的上下文 (复制原版逻辑)
//        var player = Minecraft.getInstance().player;
//        if (player == null) return;
//
//        // 如果原版逻辑判断不显示，我们也不显示
//        // 这里简化判断，假设如果能运行到 TAIL，基本都是显示了的 (除非 return 了)
//        // 但原版开头有几个 return，如果那里 return 了，TAIL 不会执行吗？
//        // 不，@Inject TAIL 只有在方法正常执行完才会触发。如果中间 return 了，TAIL 可能抓不到。
//        // 但对于 void 方法，TAIL 通常是指所有 return 之前。
//        // 只要能进这里，说明没被早期拦截。
//
//        var ssm = ClientMagicData.getSpellSelectionManager();
//        if (ssm.getSpellCount() <= 0) return;
//
//        // 2. 重新计算坐标 (复制原版逻辑)
//        int configOffsetY = ClientConfigs.SPELL_BAR_Y_OFFSET.get();
//        int configOffsetX = ClientConfigs.SPELL_BAR_X_OFFSET.get();
//        SpellBarOverlay.Anchor anchor = ClientConfigs.SPELL_BAR_ANCHOR.get();
//
//        int centerX, centerY;
//        if (anchor == SpellBarOverlay.Anchor.Hotbar) {
//            centerX = screenWidth / 2 - Math.max(110, screenWidth / 4);
//            centerY = screenHeight - Math.max(55, screenHeight / 8);
//        } else {
//            // Anchor 枚举里有 m1, m2 字段，但它们是包级私有的(package-private)还是public?
//            // 原版代码: Anchor(int mx, int my) { this.m1 = mx; ... }
//            // 如果不能直接访问 m1/m2，就手动硬编码一下 switch case
//            // 幸好你是 Mixin 到同一个类，或者我们可以简单推导
//            // 为了省事，我们直接反射或者假定能访问 (如果是 public/protected)
//            // 如果不能访问，用下面的硬编码替代：
//            int m1 = 0, m2 = 0;
//            switch(anchor) {
//                case TopRight: m1=1; m2=0; break;
//                case BottomLeft: m1=0; m2=1; break;
//                case BottomRight: m1=1; m2=1; break;
//                default: m1=0; m2=0; break; // TopLeft & Hotbar(handled above)
//            }
//            centerX = screenWidth * m1;
//            centerY = screenHeight * m2;
//        }
//        centerX += configOffsetX;
//        centerY += configOffsetY;
//
//        // 3. 重新获取位置缓存
//        // 原版: var locations = ClientRenderCache.relativeSpellBarSlotLocations;
//        List<Vec2> locations = ClientRenderCache.relativeSpellBarSlotLocations;
//        if (locations == null || locations.isEmpty()) return;
//
//        // 移动 centerX (原版逻辑)
//        int approximateWidth = locations.size() / 3;
//        centerX -= approximateWidth * 5;
//
//        // 4. 开始绘制
//        List<SpellSelectionManager.SelectionOption> spells = ssm.getAllSpells();
//
//        for (int i = 0; i < locations.size() && i < spells.size(); i++) {
//            AbstractSpell spell = spells.get(i).spellData.getSpell();
//
//            if (spell instanceof IChargedSpell chargedSpell) {
//                int maxCharges = chargedSpell.eyi$getMaxCharges();
//
//                if (maxCharges > 1) {
//                    int currentCharges = ClientChargeData.getCharges(spell);
//
//                    int x = centerX + (int) locations.get(i).x;
//                    int y = centerY + (int) locations.get(i).y;
//
//                    // 绘制左上角数字
//                    renderChargeCount(guiHelper, x, y, currentCharges);
//
//                    // 绘制充能遮罩 (可选，如果你想做的话)
//                    // 如果不在冷却中(cd=0) 且 层数不满
//                    float cdPercent = ClientMagicData.getCooldownPercent(spell);
//                    if (cdPercent <= 0 && currentCharges < maxCharges) {
//                        // 绘制一个半透明白色遮罩，表示正在充能中
//                        // 由于没有进度数据，只能画一个静态的
//                        // renderChargingOverlay(guiHelper, x, y);
//                    }
//                }
//            }
//        }
//    }
//
//    private void renderChargeCount(GuiGraphics guiHelper, int x, int y, int current) {
//        PoseStack poseStack = guiHelper.pose();
//        poseStack.pushPose();
//
//        // 调整文字位置：法术图标大概在 (x+3, y+3) 到 (x+19, y+19)
//        // 我们把字画在左上角 (x+3, y+3)
//        float scale = 0.7f;
//        poseStack.scale(scale, scale, scale);
//
//        // 颜色：0层红色/灰色，有层数蓝色/金色
//        int color = (current == 0) ? 0xFFAAAAAA : 0xFF55FFFF;
//
//        // 带阴影绘制
//        String text = String.valueOf(current);
//
//        // 坐标反算
//        float textX = (x + 4) / scale;
//        float textY = (y + 4) / scale;
//
//        guiHelper.drawString(Minecraft.getInstance().font, text, (int)textX, (int)textY, color, true);
//
//        poseStack.popPose();
//    }
//}
