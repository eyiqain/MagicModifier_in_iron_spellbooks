package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.ui.PacketExtractSpell;
import io.isb.modifier.net.ui.PacketExtractToInv;
import io.isb.modifier.net.ui.PacketInscribeSpell;
import io.isb.modifier.net.ui.PacketManageSynth;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.player.ClientRenderCache;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

/**
 * 魔法合成与法术书管理页面
 * 修正版：添加点击选中、拖拽拿取、高亮显示
 */
public class FunctionPage extends SpellScreen.UiWindow {

    // === 布局常量 ===
    private static final int SYNTH_LABEL_X = 12;
    private static final int SYNTH_LABEL_Y = 123;
    private static final int SYNTH_IN_X_1 = 13;
    private static final int SYNTH_IN_X_2 = 33;
    private static final int SYNTH_IN_Y = 135;
    private static final int SYNTH_OUT_X_1 = 70;
    private static final int SYNTH_OUT_Y = 135;
    private static final int SYNTH_BTN_X = 54;
    private static final int SYNTH_BTN_Y = 137;
    private static final int SYNTH_BTN_W = 14;
    private static final int SYNTH_BTN_H = 14;
    private static final int BOOK_BOX_X = 13;
    private static final int BOOK_BOX_Y = 10;
    private static final int BOOK_BOX_WIDTH = 96;
    private static final int BOOK_BOX_HEIGHT = 80;

    // === 纹理参数 ===
    private static final int SLOT_SIZE = 19;
    private static final int SLOT_TEXTURE_V = 178;

    // 状态贴图 U 偏移
    private static final int SLOT_OFFSET_NORMAL = 0;   // 正常
    private static final int SLOT_OFFSET_HOVER = 19;   // 鼠标悬停（空手）
    private static final int SLOT_OFFSET_ACTIVE = 38;  // 鼠标悬停（拖拽物品中）

    // === 运行时状态 ===
    private final ItemStack[] synthStacks = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private boolean isCraftResultPending = false;

    // === 拖拽与选中逻辑变量 ===
    private SelectedSlot selectedSlot = null; // 当前选中的槽位
    private double dragStartX, dragStartY;    // 拖拽起始点
    private boolean isDraggingItem = false;   // 是否正在拖拽
    private static final double DRAG_THRESHOLD = 3.0;

    // 内部类：用于标识选中的是哪种槽位
    private enum SlotType { SYNTH, BOOK }
    private record SelectedSlot(SlotType type, int index, int x, int y) {}

    public FunctionPage(SpellScreen host) {
        super(host);
    }

    @Override
    public void onHide() {
        clearSynthSlots();
        selectedSlot = null;
        isDraggingItem = false;
    }

    private void clearSynthSlots() {
        for (int i=0; i<4; i++) synthStacks[i] = ItemStack.EMPTY;
        isCraftResultPending = false;
    }

    public void updateSynthSlot(int slotIndex, ItemStack itemStack){
        if (slotIndex >= 0 && slotIndex < 4) {
            synthStacks[slotIndex] = itemStack;
        }
    }
    @Override
    public void clearSelection() {
        this.selectedSlot = null; // 清空本地选中
    }
    @Override
    public void render(GuiGraphics g, int w, int h, int localX, int localY, float partialTick) {
        // 背景绘制
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEXTURE, 0, 0, 278, 2, w, h, 512, 512);

        renderBookSlots(g, localX, localY);
        renderSynthesisUI(g, localX, localY);

        // 最后绘制选中高亮框，保证在最上层
        if (selectedSlot != null && !isDraggingItem) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // 绘制 19x19 的高亮框，坐标需要对准槽位稍微偏移(通常槽位背景大一点或者刚好覆盖)
            // 假设 selectedSlot.x/y 是槽位左上角
            g.blit(TEXTURE, selectedSlot.x - 1, selectedSlot.y - 1, 57, 197, 19, 19, 512, 512);
        }
    }

    // ================= 交互逻辑 (核心修改) =================

    @Override
    public boolean mouseClicked(double localX, double localY, int button) {
        // 1. 合成按钮点击 (优先级最高)
        if (button == 0 && isHovering(SYNTH_BTN_X, SYNTH_BTN_Y, SYNTH_BTN_W, SYNTH_BTN_H, (int)localX, (int)localY)) {
            tryCraft();
            return true;
        }

        // 2. 检测鼠标下的槽位
        SelectedSlot target = getSlotAt(localX, localY);

        // 如果是左键点击 (选中逻辑)
        if (button == 0) {
            if (target != null) {
                // 如果鼠标当前是空的，尝试选中槽位里的东西
                if (host.getMouseStack().isEmpty()) {
                    System.out.println("debug选中槽位内东西" );
                    // 检查该槽位是否有东西
                    boolean hasItem = false;
                    if (target.type == SlotType.SYNTH) {
                        hasItem = !synthStacks[target.index].isEmpty();
                    } else if (target.type == SlotType.BOOK) {
                        hasItem = hasSpellInBook(target.index);
                    }

                    if (hasItem) {
                        System.out.println("debug选中槽位内有有有东西" );
                        this.selectedSlot = target;
                        // 【添加这行】告诉主类：我选中东西了，让左边那个把手松开
                        this.host.claimSelectionFocus(this);
                        this.dragStartX = localX;
                        this.dragStartY = localY;
                        this.isDraggingItem = false;
                        return true;
                    }
                }
                // 如果是产物槽，点击直接领取
                if (target.type == SlotType.SYNTH && target.index == 2) {
                    handleResultSlotClick();
                    return true;
                }
            } else {
                // 点了空白处，取消选中
                this.selectedSlot = null;
            }
        }

        // 3. 右键点击 (保持原有的取出逻辑)
        if (button == 1) {
            if (target != null && host.getMouseStack().isEmpty()) {
                if (target.type == SlotType.SYNTH) {
                    handleSynthSlotExtract_Click(target.index);
                    return true;
                } else if (target.type == SlotType.BOOK) {
                    handleBookExtract_Click(target.index); // 修改为传入 index
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 如果已经开始拿物品了，或者鼠标上有东西，就不处理
        if (isDraggingItem || !host.getMouseStack().isEmpty()) return true;

        // 如果没有选中槽位，无法拖拽
        if (selectedSlot == null) return false;

        // 计算移动距离
        double dist = Math.sqrt(Math.pow(mouseX - dragStartX, 2) + Math.pow(mouseY - dragStartY, 2));

        // === 触发拖拽 ===
        if (dist > DRAG_THRESHOLD) {
            // 执行拿取逻辑
            doPickupSlot(selectedSlot);
            this.isDraggingItem = true;
            // 拖拽开始后，可以清空选中状态，也可以保留，看喜好。通常拿起来后选中框消失比较自然。
            this.selectedSlot = null;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double localX, double localY, int button) {
        this.isDraggingItem = false; // 重置拖拽标志

        ItemStack held = host.getMouseStack();
        if (held.isEmpty()) return false;

        // 获取释放位置的槽位
        SelectedSlot target = getSlotAt(localX, localY);

        if (target != null) {
            // 1. 尝试放入合成槽
            if (target.type == SlotType.SYNTH) {
                // 只有 index 0 和 1 可以放东西，2是输出
                if (target.index < 2) {
                    return handleSynthSlotInsert(target.index, held);
                }
            }
            // 2. 尝试放入法术书
            else if (target.type == SlotType.BOOK) {
                // 注意：这里需要传入目标槽位 index
                return handleBookInsert(target.index, held);
            }
        }

        return false;
    }

    // === 核心：通用槽位查找 ===
    // 统一处理所有槽位的碰撞检测，减少重复代码
    private SelectedSlot getSlotAt(double localX, double localY) {
        // 1. 检查合成槽 (0, 1, 2)
        // 0: Input 1
        if (isHovering(SYNTH_IN_X_1, SYNTH_IN_Y, SLOT_SIZE, SLOT_SIZE, (int)localX, (int)localY))
            return new SelectedSlot(SlotType.SYNTH, 0, SYNTH_IN_X_1, SYNTH_IN_Y);
        // 1: Input 2
        if (isHovering(SYNTH_IN_X_2, SYNTH_IN_Y, SLOT_SIZE, SLOT_SIZE, (int)localX, (int)localY))
            return new SelectedSlot(SlotType.SYNTH, 1, SYNTH_IN_X_2, SYNTH_IN_Y);
        // 2: Output
        if (isHovering(SYNTH_OUT_X_1, SYNTH_OUT_Y, SLOT_SIZE, SLOT_SIZE, (int)localX, (int)localY))
            return new SelectedSlot(SlotType.SYNTH, 2, SYNTH_OUT_X_1, SYNTH_OUT_Y);

        // 2. 检查法术书槽
        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
        if (bookStack != null && bookStack.getItem() instanceof SpellBook) {
            ISpellContainer bookContainer = ISpellContainer.get(bookStack);
            int maxSpells = bookContainer.getMaxSpellCount();

            for (int i = 0; i < maxSpells; i++) {
                Vec2 pos = getBookSlotPosition(i, maxSpells);
                if (isHovering((int)pos.x, (int)pos.y, SLOT_SIZE, SLOT_SIZE, (int)localX, (int)localY)) {
                    return new SelectedSlot(SlotType.BOOK, i, (int)pos.x, (int)pos.y);
                }
            }
        }
        return null;
    }

    // === 动作逻辑 ===

    // 执行拖拽拿取
    private void doPickupSlot(SelectedSlot slot) {
        if (slot.type == SlotType.SYNTH) {
            handleSynthSlotExtract(slot.index);
        } else if (slot.type == SlotType.BOOK) {
            handleBookExtract(slot.index);
        }
    }

    // 合成槽放入
    private boolean handleSynthSlotInsert(int slotIdx, ItemStack held) {
        if (synthStacks[slotIdx].isEmpty() && held.getItem() instanceof Scroll) {
            ItemStack toPlace = held.split(1);
            synthStacks[slotIdx] = toPlace;
            if (held.isEmpty()) host.setMouseStack(ItemStack.EMPTY);
            ModMessage.sendToServer(new PacketManageSynth(0, slotIdx));
            return true;
        }
        return false;
    }

    // 合成槽取出 (  Drag 共用)
    private void handleSynthSlotExtract(int slotIdx) {
        if (host.getMouseStack().isEmpty() && !synthStacks[slotIdx].isEmpty()) {
            ItemStack stack = synthStacks[slotIdx];
            host.setMouseStack(stack);
            synthStacks[slotIdx] = ItemStack.EMPTY;
            ModMessage.sendToServer(new PacketManageSynth(1, slotIdx));
            if (slotIdx == 2) isCraftResultPending = false;
        }
    }
    private void handleSynthSlotExtract_Click(int slotIdx) {
        if (host.getMouseStack().isEmpty() && !synthStacks[slotIdx].isEmpty()) {
            ItemStack stack = synthStacks[slotIdx];
            host.setMouseStack(stack);
            synthStacks[slotIdx] = ItemStack.EMPTY;
            ModMessage.sendToServer(new PacketManageSynth(1, slotIdx));
            if (slotIdx == 2) isCraftResultPending = false;
        }
    }

    // 法术书放入 (修改版：接收 index)
    private boolean handleBookInsert(int targetSlotIndex, ItemStack held) {
        if (!(held.getItem() instanceof Scroll)) return false;
        //获得法术书
        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
        if (bookStack == null) return false;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        ISpellContainer heldContainer = ISpellContainer.get(held);
        // 获取手中卷轴的法术数据
        SpellData incomingSpellData = heldContainer.getSpellAtIndex(0);
        if (incomingSpellData == SpellData.EMPTY) return false;
        //不允许学习同一个法术
        // 遍历书本所有槽位，检查是否已经拥有该法术（跳过当前目标槽位，因为我们要覆盖它）
        int maxSpells = bookContainer.getMaxSpellCount();
        for (int i = 0; i < maxSpells; i++) {
            if (i == targetSlotIndex) continue; // 不查我们要放入的这个坑
            SpellData existing = bookContainer.getSpellAtIndex(i);
            // 如果法术ID相同 (不管等级如何，通常法术书不允许重复学同一个法术)
            if (existing.getSpell().getSpellId().equals(incomingSpellData.getSpell().getSpellId())) {
                System.out.println("法术书内已存在该法术，禁止重复放入");
                return false;
            }
        }
        //也不允许将法术放入已有法术槽内（法术交换和退回后面再写）
        ISpellContainer itemContainer = ISpellContainer.get(bookStack);
        SpellData existingSpell = itemContainer.getSpellAtIndex(targetSlotIndex);
        if (existingSpell != SpellData.EMPTY) {
            return false;
        }
        // 视觉消耗
        held.shrink(1);
        if (held.isEmpty()) host.setMouseStack(ItemStack.EMPTY);
        // 发送包
        ModMessage.sendToServer(new PacketInscribeSpell(targetSlotIndex));
        return true;
    }

    // 法术书取出 (修改版：接收 index)
    private void handleBookExtract(int slotIdx) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
        if (bookStack == null) return;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        SpellData slotData = bookContainer.getSpellAtIndex(slotIdx);

        if (slotData != SpellData.EMPTY) {
            ModMessage.sendToServer(new PacketExtractSpell(slotIdx));//该包把卷轴放鼠标上（客户端/服务端）
        }
    }
    //右键点击
    private void handleBookExtract_Click(int slotIdx) {//必须手动改玩家客户端背包，服务端靠发包
        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
        if (bookStack == null) return;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        SpellData slotData = bookContainer.getSpellAtIndex(slotIdx);

        if (slotData != SpellData.EMPTY) {
            // 这里并没有复杂的本地预测（因为生成 Scroll 需要法术数据），
            // 直接发包让服务器处理，服务器会把物品放到鼠标上并同步回来。
            // 只要网络不卡，体感是瞬间的。
            //调试debug
            System.out.println("法术书取出 : 法术： " + slotData.getSpell().getSpellName()+"等级:"+slotData.getLevel());
            //this.host.setMouseStack(bookStack);
            //ModMessage.sendToServer(new PacketExtractSpell(slotIdx));//该包把卷轴放鼠标上（客户端/服务端）
            ModMessage.sendToServer(new PacketExtractToInv(slotIdx));//直接退回背包（服务端）
            ItemStack tempStack = new ItemStack(ItemRegistry.SCROLL.get());
            // 🔥 关键：不要用 ISpellContainer.get()，直接 new 一个正确初始化的 SpellContainer
            SpellContainer scrollContainer = new SpellContainer(1, false, false);
            //注入魔法
            scrollContainer.addSpellAtIndex(slotData.getSpell(), slotData.getLevel(), 0, true,tempStack);
            this.host.getMenu().playerInv.add(tempStack);
        }
    }
    // 辅助检查法术书某格是否有法术
    private boolean hasSpellInBook(int slotIdx) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
        if (bookStack == null) return false;
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        return bookContainer.getSpellAtIndex(slotIdx) != SpellData.EMPTY;
    }

    // ================= 渲染辅助 =================

    private void renderSynthesisUI(GuiGraphics g, int mouseX, int mouseY) {
        // 标题
        Component titleMsg = Component.literal("魔法合成:").withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK);
        g.drawString(host.getMinecraft().font, titleMsg, SYNTH_LABEL_X, SYNTH_LABEL_Y, 0xFF000000, false);

        boolean hasItem = !host.getMouseStack().isEmpty();

        // 输入槽
        for (int i = 0; i < 2; i++) {
            int x = (i == 0) ? SYNTH_IN_X_1 : SYNTH_IN_X_2;
            int y = SYNTH_IN_Y;
            boolean hover = isHovering(x, y, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);

            // 贴图逻辑
            int uOffset = SLOT_OFFSET_NORMAL;
            if (hover) uOffset = hasItem ? SLOT_OFFSET_ACTIVE : SLOT_OFFSET_HOVER;

            g.blit(TEXTURE, x, y, uOffset, SLOT_TEXTURE_V, SLOT_SIZE, SLOT_SIZE, 512, 512);
            drawSynthSlotContent(g, i, x, y);
        }

        // 合成检测
        boolean canCraft = false;
        if (!synthStacks[0].isEmpty() && !synthStacks[1].isEmpty()) {
            ISpellContainer c1 = ISpellContainer.get(synthStacks[0]);
            ISpellContainer c2 = ISpellContainer.get(synthStacks[1]);
            SpellData d1 = c1.getSpellAtIndex(0);
            SpellData d2 = c2.getSpellAtIndex(0);
            if (d1 != SpellData.EMPTY && d2 != SpellData.EMPTY) {
                if (d1.getSpell().getSpellId().equals(d2.getSpell().getSpellId()) && d1.getLevel() == d2.getLevel()) {
                    canCraft = true;
                }
            }
        }

        // 合成按钮
        boolean hoverBtn = isHovering(SYNTH_BTN_X, SYNTH_BTN_Y, SYNTH_BTN_W, SYNTH_BTN_H, mouseX, mouseY);
        int btnU = canCraft ? (hoverBtn ? 28 : 14) : 0;
        g.blit(TEXTURE, SYNTH_BTN_X, SYNTH_BTN_Y, btnU, 211, SYNTH_BTN_W, SYNTH_BTN_H, 512, 512);

        // 输出槽
        int ox = SYNTH_OUT_X_1;
        int oy = SYNTH_OUT_Y;
        boolean hoverOut = isHovering(ox, oy, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);
        g.blit(TEXTURE, ox, oy, hoverOut ? SLOT_OFFSET_HOVER : SLOT_OFFSET_NORMAL, SLOT_TEXTURE_V, SLOT_SIZE, SLOT_SIZE, 512, 512);
        drawSynthSlotContent(g, 2, ox, oy);
    }

    private void renderBookSlots(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) {
            g.drawCenteredString(this.host.getMinecraft().font, "无魔法书", BOOK_BOX_X + BOOK_BOX_WIDTH / 2, BOOK_BOX_Y + 30, 0xFF555555);
            return;
        }

        Component titleMsg = Component.literal("魔法书:").withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK);
        g.drawString(this.host.getMinecraft().font, titleMsg,BOOK_BOX_X, BOOK_BOX_Y, 0xFF000000, false);

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();
        boolean hasItem = !host.getMouseStack().isEmpty();

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells);
            int sx = (int)pos.x;
            int sy = (int)pos.y;
            boolean hover = isHovering(sx, sy, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);

            int uOffset = SLOT_OFFSET_NORMAL;
            if (hover) uOffset = hasItem ? SLOT_OFFSET_ACTIVE : SLOT_OFFSET_HOVER;

            g.blit(TEXTURE, sx, sy, uOffset, SLOT_TEXTURE_V, SLOT_SIZE, SLOT_SIZE, 512, 512);

            SpellData sd = bookContainer.getSpellAtIndex(i);
            if (sd != SpellData.EMPTY) {
                g.blit(sd.getSpell().getSpellIconResource(), sx + 1, sy + 1, 0, 0, 16, 16, 16, 16);
                drawLevelBadge(g, sx, sy, sd.getLevel());
            }
        }
    }

    @Override
    public void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int localX, int localY) {
        SelectedSlot target = getSlotAt(localX, localY);
        if (target != null) {
            // 合成槽 Tooltip
            if (target.type == SlotType.SYNTH) {
                ItemStack stack = synthStacks[target.index];
                if (!stack.isEmpty()) {
                    g.renderTooltip(host.getMinecraft().font, stack, mouseX, mouseY);
                }
            }
            // 法术书 Tooltip
            else if (target.type == SlotType.BOOK) {
                ItemStack bookStack = Utils.getPlayerSpellbookStack(Objects.requireNonNull(this.host.getMinecraft().player));
                if (bookStack != null) {
                    ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                    SpellData sd = bookContainer.getSpellAtIndex(target.index);
                    if (sd != SpellData.EMPTY) {
                        List<Component> lines = getTooltipLines(sd);
                        g.renderTooltip(host.getMinecraft().font, lines, Optional.empty(), mouseX, mouseY);
                    }
                }
            }
        }
    }

    private void drawSynthSlotContent(GuiGraphics g, int idx, int x, int y) {
        ItemStack stack = synthStacks[idx];
        if (!stack.isEmpty() && stack.getItem() instanceof Scroll) {
            ISpellContainer sc = ISpellContainer.get(stack);
            SpellData sd = sc.getSpellAtIndex(0);
            g.blit(sd.getSpell().getSpellIconResource(),x + 1, y + 1, 0, 0, 16, 16, 16, 16);
            if (sd != SpellData.EMPTY) {
                drawLevelBadge(g, x, y, sd.getLevel());
            }
        }
    }

    private void handleResultSlotClick() {
        if (isCraftResultPending && !synthStacks[2].isEmpty()) {
            isCraftResultPending = false;
            handleSynthSlotExtract(2); // 复用取出逻辑
        }
    }

    private void tryCraft() {
        if (!synthStacks[0].isEmpty() && !synthStacks[1].isEmpty() && synthStacks[2].isEmpty()) {
            ModMessage.sendToServer(new PacketManageSynth(2, -1));
            synthStacks[0] = ItemStack.EMPTY;
            synthStacks[1] = ItemStack.EMPTY;
            isCraftResultPending = true;
        }
    }

    private Vec2 getBookSlotPosition(int slotIndex, int totalSpells) {
        int boxSize = SLOT_SIZE;
        int[] rowCounts = ClientRenderCache.getRowCounts(totalSpells);
        int rowIndex = 0; int colIndex = slotIndex;
        for (int r = 0; r < rowCounts.length; r++) {
            if (colIndex < rowCounts[r]) { rowIndex = r; break; }
            colIndex -= rowCounts[r];
        }
        int centerX = BOOK_BOX_X + BOOK_BOX_WIDTH / 2;
        int centerY = BOOK_BOX_Y + BOOK_BOX_HEIGHT / 2;
        int totalHeight = rowCounts.length * boxSize;
        int currentRowWidth = rowCounts[rowIndex] * boxSize;
        int x = centerX - (currentRowWidth / 2) + (colIndex * boxSize);
        int y = centerY - (totalHeight / 2) + (rowIndex * boxSize);
        return new Vec2(x, y);
    }

    private void drawLevelBadge(GuiGraphics g, int itemX, int itemY, int level) {
        int w = 9; int h = 8;
        int x0 = itemX + 16 - w + 2; int y0 = itemY;
        g.fill(x0, y0, x0 + w, y0 + h, 0xFF000000);
        int color = (level >= 10) ? 0xFFFFD700 : 0xFF00FF00;
        String txt = String.valueOf(Math.min(level, 10));
        g.drawString(this.host.getMinecraft().font, txt, x0 + w - this.host.getMinecraft().font.width(txt) + 1, y0, color, false);
    }

    private List<Component> getTooltipLines(SpellData spellData) {
        List<Component> lines = new ArrayList<>();
        if (spellData == null || SpellData.EMPTY.equals(spellData)) return lines;

        AbstractSpell spell = spellData.getSpell();
        int level = spellData.getLevel();
        var player = this.host.getMinecraft().player;
        io.redspace.ironsspellbooks.api.spells.SpellRarity rarity = spell.getRarity(level);

        lines.add(spell.getDisplayName(player).withStyle(rarity.getDisplayName().getStyle()));
        lines.add(Component.translatable("ui.irons_spellbooks.level", level).withStyle(rarity.getDisplayName().getStyle()));
        List<net.minecraft.network.chat.MutableComponent> uniqueInfo = spell.getUniqueInfo(level, player);
        if (!uniqueInfo.isEmpty()) {
            lines.addAll(uniqueInfo);
        }
        return lines;
    }

    private boolean isHovering(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
