package io.isb.modifier.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.gui.page.FunctionPage;
import io.isb.modifier.gui.page.InstructionPage;
import io.isb.modifier.gui.page.ModifyPage;
import io.isb.modifier.gui.page.SpellPage;
import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.ui.PacketManageSynth;
import io.isb.modifier.net.ui.PacketReturnCarried;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 主UI壳：SpellBrowserScreen
 */
public class SpellScreen extends AbstractContainerScreen<SpellMenu> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicModifier.MODID, "textures/gui/spell_inscription.png");

    private ItemStack mouseStack = ItemStack.EMPTY;

    // 窗口布局常量
    private static final int LEFT_ORIGIN_X = 13;
    private static final int LEFT_ORIGIN_Y = 2;
    private static final int LEFT_W = 118;
    private static final int LEFT_H = 162;

    private static final int RIGHT_ORIGIN_X = 147;
    private static final int RIGHT_ORIGIN_Y = 2;
    private static final int RIGHT_W = 118;
    private static final int RIGHT_H = 162;

    // 按钮布局

    // 按钮大小
    private static final int TAB_BTN_W = 22;
    private static final int TAB_BTN_H = 27;

    // 按钮在屏幕上的位置 (相对 guiLeft, guiTop)
    private static final int TAB_BTN_X_START = 161; // 起始X
    private static final int TAB_BTN_Y_OFFSET = -23; // 起始Y (负数表示在界面上方突出)

    // 贴图 UV 配置
    private static final int TAB_TEX_U_START = 137; // 贴图起始 U
    private static final int TAB_TEX_V_START = 178; // 贴图起始 V

    private final List<UiWindow> leftWindows = new ArrayList<>();
    private final List<UiWindow> rightWindows = new ArrayList<>();

    private UiWindow activeLeftWindow;
    private UiWindow activeRightWindow;
    private int nextWindowId = 1;

    private UiWindow focusedWindow = null;
    private boolean isDraggingGesture = false;
    public static class DragContext {
        public boolean active;
        public UiWindow sourceWindow;
        public String sourceType; // "BOOK" / "SYNTH" / "Player"
        public int sourceIndex;
    }
    private final DragContext dragContext = new DragContext();
    public DragContext dragCtx() { return dragContext; }
    public SpellScreen(SpellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 277;
        this.imageHeight = 177;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }
    public void beginDrag(UiWindow sourceWindow, String sourceType, int sourceIndex) {
        dragContext.active = true;
        dragContext.sourceWindow = sourceWindow;
        dragContext.sourceType = sourceType;
        dragContext.sourceIndex = sourceIndex;

    }

    public void clearDragCtx() {
        dragContext.active = false;
        dragContext.sourceWindow = null;
        dragContext.sourceType = null;
        dragContext.sourceIndex = -1;
    }

    @Override
    protected void init() {
        super.init();
        // 【关键修复 1】：必须清理列表，防止每次调整窗口大小时重复添加窗口
        leftWindows.clear();
        rightWindows.clear();
        leftWindows.clear();
        rightWindows.clear();
        nextWindowId = 1;
        focusedWindow = null;

        // 注册窗口
        registerWindow(WindowSide.LEFT, new SpellPage(this));

        // 注册右侧功能页
        registerWindow(WindowSide.RIGHT, new FunctionPage(this));
        //registerWindow(WindowSide.RIGHT, new ModifyPage(this));
         registerWindow(WindowSide.RIGHT, new InstructionPage(this)); // 如有

        if (!leftWindows.isEmpty()) activeLeftWindow = leftWindows.get(0);
        if (!rightWindows.isEmpty()) activeRightWindow = rightWindows.get(0);

        if (activeLeftWindow != null) activeLeftWindow.onShow();
        if (activeRightWindow != null) activeRightWindow.onShow();
        //清理拖拽上下文
        clearDragCtx();
    }
    public UiWindow getActiveRightWindow(){
        return activeRightWindow;
    }
    public UiWindow getActiveLeftWindow(){return activeLeftWindow;}

    private void registerWindow(WindowSide side, UiWindow window) {
        int id = nextWindowId++;
        int w = (side == WindowSide.LEFT) ? LEFT_W : RIGHT_W;
        int h = (side == WindowSide.LEFT) ? LEFT_H : RIGHT_H;
        window._injectMeta(id, side, w, h);
        if (side == WindowSide.LEFT) leftWindows.add(window);
        else rightWindows.add(window);
    }

    public void switchRightWindow(int index) {
        if (index < 0 || index >= rightWindows.size()) return;
        UiWindow target = rightWindows.get(index);
        if (target == activeRightWindow) return;
        if (activeRightWindow != null) activeRightWindow.onHide();
        activeRightWindow = target;
        if (activeRightWindow != null) activeRightWindow.onShow();
    }

    // ================= 渲染逻辑 =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // 注意：不要调用 super.render，因为它会调用原生的 renderLabels 和 renderTooltip
        // 我们需要自己控制顺序，或者确保 super.render 不会画出多余的东西
        // 通常建议是先画背景，再画窗口，最后画鼠标物品

        // 1. 画背景和窗口 (复用你的 renderBg 逻辑，或者直接调用 super.renderBg 如果你复写了它)
        // 但根据你的代码结构，你是在 renderBg 里画窗口的，所以：
        super.render(g, mouseX, mouseY, partialTick);

        // ... (dispatchToWindow 逻辑保持不变) ...

        // 2. 渲染鼠标手中的物品 (改成从 Menu 获取)
        ItemStack carried = getMouseStack(); // 使用修改后的 getter
        if (!carried.isEmpty()) {
            // 渲染物品本身
            g.renderItem(carried, mouseX - 8, mouseY - 8);
            // 渲染数量
            g.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8, null);

            // 如果是卷轴，绘制悬浮的大图标
            if (carried.getItem() instanceof Scroll) {
                ISpellContainer container = ISpellContainer.get(carried);
                SpellData spellData = container.getSpellAtIndex(0);
                drawFloatingIcon(g, mouseX, mouseY, spellData);
            }
        } else {
            // B. 【核心修复】：显式渲染子窗口的 Tooltip！！！
            // 必须手动检查左右窗口
            if (activeLeftWindow != null) {
                renderWindowTooltips(activeLeftWindow, g, mouseX, mouseY);
            }
            if (activeRightWindow != null) {
                renderWindowTooltips(activeRightWindow, g, mouseX, mouseY);
            }
            // 只有鼠标没东西时才显示 Tooltip
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    private void renderWindowTooltips(UiWindow win, GuiGraphics g, int mx, int my) {
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;
        int ox = (win.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_X : RIGHT_ORIGIN_X;
        int oy = (win.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_Y : RIGHT_ORIGIN_Y;

        int lx = mx - (guiLeft + ox);
        int ly = my - (guiTop + oy);

        if (lx >= 0 && lx < win.getWidth() && ly >= 0 && ly < win.getHeight()) {
            win.renderTooltips(g, mx, my, lx, ly);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop  = (this.height - this.imageHeight) / 2;

        g.blit(TEXTURE, guiLeft, guiTop, 0, 0, this.imageWidth, this.imageHeight, 512, 512);

        renderTabButtons(g, guiLeft, guiTop, mouseX, mouseY);
        // 【关键修复 2】：确保只渲染 active 的那个，不要写成 for 循环遍历 rightWindows
        if (activeLeftWindow != null)
            renderWindow(activeLeftWindow, g, guiLeft, guiTop, mouseX, mouseY, partialTick);

        if (activeRightWindow != null)
            renderWindow(activeRightWindow, g, guiLeft, guiTop, mouseX, mouseY, partialTick);

    }

    private void renderWindow(UiWindow win, GuiGraphics g, int guiLeft, int guiTop, int mx, int my, float partialTick) {
        int ox = (win.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_X : RIGHT_ORIGIN_X;
        int oy = (win.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_Y : RIGHT_ORIGIN_Y;

        int lx = mx - (guiLeft + ox);
        int ly = my - (guiTop + oy);

        g.pose().pushPose();
        g.pose().translate(guiLeft + ox, guiTop + oy, 0);
        win.render(g, win.getWidth(), win.getHeight(), lx, ly, partialTick);
        g.pose().popPose();
    }


    // ================= 事件处理 =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleTabButtonsClick(mouseX, mouseY, button)) return true;

        if (focusedWindow != null) return true;

        // 优先分发给右窗口
        if (dispatchToWindow(activeRightWindow, mouseX, mouseY, (w, lx, ly, inside) -> {
            if (!inside) return false;
            if (w.mouseClicked(lx, ly, button)) {
                focusedWindow = w;
                isDraggingGesture = true;
                return true;
            }
            return false;
        })) return true;

        // 分发给左窗口
        if (dispatchToWindow(activeLeftWindow, mouseX, mouseY, (w, lx, ly, inside) -> {
            if (!inside) return false;
            if (w.mouseClicked(lx, ly, button)) {
                focusedWindow = w;
                isDraggingGesture = true;
                return true;
            }
            return false;
        })) return true;//与里面的结果一致

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingGesture && focusedWindow != null) {
            return dispatchToWindow(focusedWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                    w.mouseDragged(lx, ly, button, dragX, dragY)
            );
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = false;
        System.out.println("debug:ID " + activeRightWindow.windowId);
        // 先问右侧激活窗口 (FunctionPage 或 ModifyPage)
        //调试debug
        ISpellContainer container = ISpellContainer.get(this.getMouseStack());
        System.out.println("主类-released"+this.getMouseStack().getItem());
        SpellData spellData = container.getSpellAtIndex(0);
        if(spellData != SpellData.EMPTY){
            System.out.println("Release(Screen这里) :法术： " + spellData.getSpell().getSpellName()+"等级:"+spellData.getLevel());
        }
        if (activeRightWindow != null) {
            consumed = dispatchToWindow(activeRightWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                    // 只有在窗口范围内释放，或者窗口处于某种特殊状态时才处理
                    inside && w.mouseReleased(lx, ly, button)
            );
        }
        // 如果右侧没处理，再问左侧激活窗口 (SpellPage)
        if (!consumed && activeLeftWindow != null) {
            consumed = dispatchToWindow(activeLeftWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                    inside && w.mouseReleased(lx, ly, button)
            );
        }
        //}
        // 3. 【核心兜底逻辑】：全局归还机制
        // 如果鼠标上有东西，而且刚才没有任何子窗口返回 true (consumed == false)
        if (!getMouseStack().isEmpty() && !consumed) {
            fallbackReturnCarried(getMouseStack());
            consumed = true;
        }
        // 清理拖拽状态
        focusedWindow = null;
        isDraggingGesture = false;
        // 调用 super 确保原版逻辑（如 NEI/JEI 兼容等）正常运行
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isHoveringWindow(UiWindow w, int mx, int my) {
        if (w == null) return false;
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;
        int ox = (w.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_X : RIGHT_ORIGIN_X;
        int oy = (w.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_Y : RIGHT_ORIGIN_Y;
        int gx = guiLeft + ox;
        int gy = guiTop + oy;
        return mx >= gx && mx < gx + w.getWidth() && my >= gy && my < gy + w.getHeight();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (dispatchToWindow(activeRightWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                inside && w.mouseScrolled(lx, ly, delta))) return true;

        if (dispatchToWindow(activeLeftWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                inside && w.mouseScrolled(lx, ly, delta))) return true;

        return super.mouseScrolled(mouseX, mouseY, delta);
    }


    // ================= 辅助方法 =================

    private boolean dispatchToWindow(UiWindow window, double mouseX, double mouseY, WindowEventHandler handler) {
        if (window == null) return false;
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;
        int ox = (window.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_X : RIGHT_ORIGIN_X;
        int oy = (window.getSide() == WindowSide.LEFT) ? LEFT_ORIGIN_Y : RIGHT_ORIGIN_Y;
        int localX = (int)mouseX - (guiLeft + ox);
        int localY = (int)mouseY - (guiTop + oy);
        boolean inside = (localX >= 0 && localX < window.getWidth() && localY >= 0 && localY < window.getHeight());
        return handler.handle(window, localX, localY, inside);
    }

    @FunctionalInterface
    private interface WindowEventHandler {
        boolean handle(UiWindow w, int localX, int localY, boolean inside);
    }

    public static boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // --- 修改核心存取器 ---

    // 获取当前鼠标上的物品 (直接读 Menu，这是服务端同步过来的真值)
    public ItemStack getMouseStack() {
        return this.menu.getCarried();
    }

    // 设置鼠标物品 (仅用于客户端预测，如拖拽瞬间的视觉反馈)
    // 真正的修改必须发包给服务器！
    public void setMouseStack(ItemStack stack) {
        this.menu.setCarried(stack);
        //调试debug
        ISpellContainer container = ISpellContainer.get(this.getMouseStack());
        SpellData spellData = container.getSpellAtIndex(0);
        if(spellData != SpellData.EMPTY){
            System.out.println("客户端：(setMouseStack这里) :法术： " + spellData.getSpell().getSpellName()+"等级:"+spellData.getLevel());
        }
    }
    // 拿走物品 (同上，直接操作 Menu)
    public ItemStack takeMouseStack() {
        ItemStack old = this.menu.getCarried();
        this.menu.setCarried(ItemStack.EMPTY);
        return old;
    }
    // 供子类调用：归还物品逻辑 (现在是发送网络包)
    public void returnStackToPlayer(ItemStack stack) {
        // 1. 只有当鼠标真的有东西时才执行
        if (stack != null && !stack.isEmpty()) {
            // 2. 告诉服务器：把鼠标上的东西还回去
            ModMessage.sendToServer(new PacketReturnCarried());

            // --- B. 客户端视觉预测（这是为了不闪烁） ---
            // 1. 把物品“假装”塞回客户端背包
            // 这样下一帧 SpellPage updateFilteredItems 时就能看到它了
            this.menu.playerInv.add(stack.copy());
            // 2. 把客户端鼠标清空
            this.menu.setCarried(ItemStack.EMPTY);

            System.out.println("debug: 正在归还物品 " + stack.getItem());
        } else {
            System.out.println("debug: 物品为空，无需归还");
        }
    }
    /**
     * 兜底归还：优先按 dragCtx 归还到来源槽位；不满足条件则回背包
     * - SYNTH: 复用 PacketManageSynth(0, sourceIndex)
     * - BOOK : 使用 PacketReturnToSource(TYPE_BOOK, sourceIndex) （你需要新增这个包）
     * - 其他 : PacketReturnCarried -> 背包
     */
    private void fallbackReturnCarried(ItemStack carried) {
        if (carried == null || carried.isEmpty()) return;

        boolean handled = false;

        if (dragContext.active && dragContext.sourceType != null) {
            // 1) 合成槽：直接“放回原槽”
            if ("SYNTH".equals(dragContext.sourceType)) {
                int idx = dragContext.sourceIndex;
                // 只允许回输入槽（0/1），避免把卷轴塞回输出槽 2
                if (idx >= 0 && idx < 2) {
                    ModMessage.sendToServer(new io.isb.modifier.net.ui.PacketManageSynth(0, idx));
                    // 客户端视觉预测：清空鼠标，等待 PacketSyncSynth 同步槽位
                    this.menu.setCarried(ItemStack.EMPTY);
                    handled = true;
                }
            }
            // 2) 书本：需要一个“回原书槽”的包（不要用 Inscribe）
            else if ("BOOK".equals(dragContext.sourceType)) {
                int idx = dragContext.sourceIndex;
                ModMessage.sendToServer(new io.isb.modifier.net.ui.PacketReturnToSource(
                        io.isb.modifier.net.ui.PacketReturnToSource.TYPE_BOOK, idx
                ));
                // 客户端视觉预测：清空鼠标
                this.menu.setCarried(ItemStack.EMPTY);
                handled = true;
            }
        }

        // 3) 没处理掉就回背包（你原有逻辑）
        if (!handled) {
            returnStackToPlayer(carried);
        }

        // 4) 清理上下文，避免串场
        clearDragCtx();
    }

    // 复写 onClose，确保关闭 UI 时归还物品
    @Override
    public void onClose() {
        ItemStack carried = getMouseStack();
        if (!carried.isEmpty()) {
            fallbackReturnCarried(carried); // 关闭也优先回原槽
        }
        super.onClose();
    }

    public abstract static class UiWindow {
        protected final SpellScreen host;
        private int windowId = -1;
        private WindowSide side;
        private int width;
        private int height;

        protected UiWindow(SpellScreen host) { this.host = host; }

        void _injectMeta(int id, WindowSide side, int w, int h) {
            this.windowId = id; this.side = side; this.width = w; this.height = h;
        }

        public int getWindowId() { return windowId; }
        public WindowSide getSide() { return side; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }

        public void onShow() {}
        public void onHide() {}
        // 关闭UI时调用，把暂存物品吐出来
        public ItemStack collectTempStackForReturn() { return ItemStack.EMPTY; }

        public abstract void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick);
        public void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int localX, int localY) {}
        /**
         * 新增：当主类要求该窗口清除选中状态时调用
         */
        public void clearSelection() { }
        // 事件方法 (参数均为局部坐标)
        public boolean mouseClicked(double localX, double localY, int button) { return false; }
        public boolean mouseReleased(double localX, double localY, int button) { return false; }
        public boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY) { return false; }
        public boolean mouseScrolled(double localX, double localY, double delta) { return false; }
    }

    public enum WindowSide { LEFT, RIGHT }

    private void drawFloatingIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, SpellData sd) {
        if (sd == null || sd == SpellData.EMPTY) return;
        AbstractSpell spell = sd.getSpell();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500);
        guiGraphics.blit(spell.getSpellIconResource(), mouseX - 8, mouseY - 8, 0, 0, 16, 16, 16, 16);
        guiGraphics.pose().popPose();
    }
    /**
     * 渲染 Tab 按钮
     * 逻辑：当前选中的页面对应的按钮是"暗"的，未选中的是"明"的
     */
    private void renderTabButtons(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        // 获取当前激活的是第几个窗口 (0 或 1)
        int activeIndex = -1;
        if (activeRightWindow != null) {
            activeIndex = rightWindows.indexOf(activeRightWindow);
        }

        int y = top + TAB_BTN_Y_OFFSET;

        // --- 渲染 Tab 1 ---
        // 位置：164
        int x1 = left + TAB_BTN_X_START;

        // U坐标计算：
        // 如果 Tab1 是激活的(activeIndex==0) -> 显示"暗"(第2个图标, 偏移22)
        // 如果 Tab1 未激活 -> 显示"明"(第1个图标, 偏移0)
        int u1 = TAB_TEX_U_START + (activeIndex == 0 ? 22 : 0);

        g.blit(TEXTURE, x1, y, u1, TAB_TEX_V_START, TAB_BTN_W, TAB_BTN_H, 512, 512);

        // --- 渲染 Tab 2 (如果存在) ---
        if (rightWindows.size() > 1) {
            // 位置：164 + 22 = 186
            int x2 = left + TAB_BTN_X_START + TAB_BTN_W;

            // U坐标计算：
            // 基础偏移是 44 (跳过 Tab1 的两个图标)
            // 如果 Tab2 是激活的(activeIndex==1) -> 显示"暗"(第4个图标, 基础+22)
            // 如果 Tab2 未激活 -> 显示"明"(第3个图标, 基础+0)
            int u2 = TAB_TEX_U_START + 44 + (activeIndex == 1 ? 22 : 0);

            g.blit(TEXTURE, x2, y, u2, TAB_TEX_V_START, TAB_BTN_W, TAB_BTN_H, 512, 512);
        }
    }
    /**
     * 处理 Tab 点击
     * 逻辑：只有点击"明"(未选中)的按钮才生效，点击"暗"(已选中)的不做反应
     */
    private boolean handleTabButtonsClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // 只响应左键

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int y = top + TAB_BTN_Y_OFFSET;

        // 获取当前激活索引，用于判断是否可以点击
        int activeIndex = -1;
        if (activeRightWindow != null) {
            activeIndex = rightWindows.indexOf(activeRightWindow);
        }

        // --- 检查 Tab 1 ---
        int x1 = left + TAB_BTN_X_START;
        if (mouseX >= x1 && mouseX < x1 + TAB_BTN_W && mouseY >= y && mouseY < y + TAB_BTN_H) {
            // 只有当 Tab 1 不是当前页面时，才允许切换
            if (activeIndex != 0) {
                switchRightWindow(0);
                // 可以在这里播放点击音效
                // Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // --- 检查 Tab 2 ---
        if (rightWindows.size() > 1) {
            int x2 = left + TAB_BTN_X_START + TAB_BTN_W;
            if (mouseX >= x2 && mouseX < x2 + TAB_BTN_W && mouseY >= y && mouseY < y + TAB_BTN_H) {
                // 只有当 Tab 2 不是当前页面时，才允许切换
                if (activeIndex != 1) {
                    switchRightWindow(1);
                    // Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }

        return false;
    }
    // ================= 全局选中管理 (新增核心) =================

    /**
     * 子窗口调用此方法声明它获取了选中焦点。
     * 主类会负责通知其他窗口清除它们的选中状态。
     *
     * @param source 触发选中的窗口
     */
    public void claimSelectionFocus(UiWindow source) {
        // 如果左侧窗口不是触发源，且处于激活状态，让它清除选中
        if (activeLeftWindow != null && activeLeftWindow != source) {
            activeLeftWindow.clearSelection();
        }
        // 如果右侧窗口不是触发源，且处于激活状态，让它清除选中
        if (activeRightWindow != null && activeRightWindow != source) {
            activeRightWindow.clearSelection();
        }
    }

}
