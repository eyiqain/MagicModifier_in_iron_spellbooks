package io.isb.modifier.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.gui.page.FunctionPage;
import io.isb.modifier.gui.page.ModifyPage;
import io.isb.modifier.gui.page.SpellPage;
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
 * <p>
 * 核心架构：
 * 1. 左右双窗口系统（Left/Right），每个区域同时只能 Active 一个窗口。
 * 2. 局部坐标系：子窗口只处理 (0,0) 开始的局部坐标，主类负责 Translate。
 * 3. 焦点机制（Focus）：
 *    - 点击时抢占 Focus。
 *    - 拖拽/释放只有 Focus 窗口响应（防止拖出窗口后事件丢失或串窗口）。
 *    - Hover/Tooltip 只要鼠标在区域内，Active 窗口均可响应。
 * 4. 鼠标临时栈：主类统一管理 1 个 ItemStack，关闭时归还。
 */
public class SpellScreen extends AbstractContainerScreen<SpellMenu> {

    // =========================================
    // 0. 常量定义：纹理与窗口布局
    // =========================================
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicModifier.MODID, "textures/gui/spell_inscription.png");
    //鼠标栈获得物品
    // 列表动画 (消耗时的闪烁/暂存逻辑/不想瞬间消失)
    private boolean pendingConsume = false;
    private int pendingSlotIndex = -1;
    private int pendingTicks = 0;
    /** 左窗口默认区域（相对 GUI 左上角） */
    private static final int LEFT_ORIGIN_X = 13;
    private static final int LEFT_ORIGIN_Y = 2;
    private static final int LEFT_W = 118; // 示例宽度
    private static final int LEFT_H = 162; // 示例高度

    /** 右窗口默认区域（相对 GUI 左上角） */
    private static final int RIGHT_ORIGIN_X = 147;
    private static final int RIGHT_ORIGIN_Y = 2;
    private static final int RIGHT_W = 118;
    private static final int RIGHT_H = 162;

    /** 切页按钮布局 */
    private static final int TAB_BTN_W = 22;
    private static final int TAB_BTN_H = 27;
    private static final int TAB_SCREEN_Y = 178;
    private static final int TAB_SCREEN_X_1 = 137;
    private static final int TAB_SCREEN_X_2 = 137 + 22;

    // =========================================
    // 1. 核心状态：鼠标栈 & 窗口管理
    // =========================================
    /** 鼠标栈：只允许持有 1 个物品（如书页/卷轴），关闭UI时归还 */
    private ItemStack mouseStack = ItemStack.EMPTY;
    /** 左右窗口列表（按注册顺序存储） */
    private final List<UiWindow> leftWindows = new ArrayList<>();
    private final List<UiWindow> rightWindows = new ArrayList<>();
    /** 当前激活的窗口 */
    private UiWindow activeLeftWindow;
    private UiWindow activeRightWindow;
    /** ID 生成器 */
    private int nextWindowId = 1;
    /**
     * 焦点窗口：当前正在进行“点击/拖拽”交互的窗口。
     * - 点击时：如果 mouseClicked 返回 true，则该窗口获得焦点。
     * - 拖拽/释放时：只分发给 focusedWindow（忽略鼠标位置是否在窗口内）。
     * - 释放后：清空焦点。
     */
    private UiWindow focusedWindow = null;
    /** 是否正在进行拖拽手势 */
    private boolean isDraggingGesture = false;
    public SpellScreen(SpellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 277;
        this.imageHeight = 177;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }
    // =========================================
    // 2. 初始化与窗口注册
    // =========================================
    @Override
    protected void init() {
        super.init();
        // 清理旧状态
        leftWindows.clear();
        rightWindows.clear();
        nextWindowId = 1;
        focusedWindow = null;
        // --- 注册窗口 ---
        // 注册时会自动分配 ID，并按 Side 加入列表
        // 左侧案例：窗口1（列表）
        registerWindow(WindowSide.LEFT, new SpellPage(this));
        // 右侧案例：窗口1（书本），窗口2（占位符）
        registerWindow(WindowSide.RIGHT, new FunctionPage(this));
        registerWindow(WindowSide.RIGHT, new ModifyPage(this));
        // 默认激活第一个
        if (!leftWindows.isEmpty()) activeLeftWindow = leftWindows.get(0);
        if (!rightWindows.isEmpty()) activeRightWindow = rightWindows.get(0);
        // 触发生命周期
        if (activeLeftWindow != null) activeLeftWindow.onShow();
        if (activeRightWindow != null) activeRightWindow.onShow();
    }
    /** 注册窗口并注入 ID/Side/Meta */
    private void registerWindow(WindowSide side, UiWindow window) {
        int id = nextWindowId++;
        // 注入元数据：ID, Side, 局部宽高 (origin在运行时动态计算)
        int w = (side == WindowSide.LEFT) ? LEFT_W : RIGHT_W;
        int h = (side == WindowSide.LEFT) ? LEFT_H : RIGHT_H;
        window._injectMeta(id, side, w, h);
        if (side == WindowSide.LEFT) {
            leftWindows.add(window);
        } else {
            rightWindows.add(window);
        }
    }
    /** 切换右侧窗口 */
    public void switchRightWindow(int index) {
        if (index < 0 || index >= rightWindows.size()) return;
        UiWindow target = rightWindows.get(index);
        if (target == activeRightWindow) return;
        if (activeRightWindow != null) activeRightWindow.onHide();
        activeRightWindow = target;
        if (activeRightWindow != null) activeRightWindow.onShow();
    }
    /** 切换左侧窗口 */
    public void switchLeftWindow(int index) {
        if (index < 0 || index >= leftWindows.size()) return;
        UiWindow target = leftWindows.get(index);
        if (target == activeRightWindow) return;
        if (activeLeftWindow != null) activeLeftWindow.onHide();
        activeLeftWindow = target;
        if (activeLeftWindow != null) activeLeftWindow.onShow();
    }
    // =========================================
    // 3. 渲染循环 (Render Loop)
    // =========================================
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (pendingConsume) {
            pendingTicks++;
            if (pendingTicks > 20) {
                pendingConsume = false;
                pendingSlotIndex = -1;
            }
        }
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick); // 会调用 renderBg 和 renderLabels(空)
         // 2. === 核心：最后绘制 Tooltips (最顶层) ===
        // 即使没有 Focus，只要鼠标悬停在上面，就需要显示提示

        // 处理左窗口 Tooltips
        if (activeLeftWindow != null) {
            // 手动计算局部坐标 (不使用 pose 平移，只算数值)
            int guiLeft = (this.width - this.imageWidth) / 2;
            int guiTop = (this.height - this.imageHeight) / 2;
            int localX = mouseX - (guiLeft + LEFT_ORIGIN_X);
            int localY = mouseY - (guiTop + LEFT_ORIGIN_Y);

            // 判断鼠标是否在窗口范围内 (可选，优化性能)
            if (localX >= 0 && localX < activeLeftWindow.getWidth() &&
                    localY >= 0 && localY < activeLeftWindow.getHeight()) {

                // 传入 全局坐标(用于画) + 局部坐标(用于找)
                activeLeftWindow.renderTooltips(g, mouseX, mouseY, localX, localY);
            }
        }

        // 处理右窗口 Tooltips (同理)
        if (activeRightWindow != null) {
            int guiLeft = (this.width - this.imageWidth) / 2;
            int guiTop = (this.height - this.imageHeight) / 2;
            int localX = mouseX - (guiLeft + RIGHT_ORIGIN_X);
            int localY = mouseY - (guiTop + RIGHT_ORIGIN_Y);

            if (localX >= 0 && localX < activeRightWindow.getWidth() &&
                    localY >= 0 && localY < activeRightWindow.getHeight()) {

                activeRightWindow.renderTooltips(g, mouseX, mouseY, localX, localY);
            }
        }
        // --- 渲染鼠标栈物品 ---
        if (!mouseStack.isEmpty()) {
            g.renderItem(mouseStack, mouseX - 8, mouseY - 8);
            if (mouseStack.getItem() instanceof Scroll) {
                //2. 获取容器 (这是模组的核心 API)
                ISpellContainer container = ISpellContainer.get(mouseStack);
                SpellData spellData = container.getSpellAtIndex(0);
                drawFloatingIcon(g, mouseX, mouseY, spellData);
            }
        }
        // --- 渲染 Tooltip (物品栏的) ---
        this.renderTooltip(g, mouseX, mouseY);
    }
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop  = (this.height - this.imageHeight) / 2;

        // 1. 画大背景 (绝对坐标，原封不动)
        g.blit(TEXTURE, guiLeft, guiTop, 0, 0, this.imageWidth, this.imageHeight, 512, 512);

        // 2. 画切页按钮
        renderTabButtons(g, guiLeft, guiTop, mouseX, mouseY);
        // 3. 渲染左窗口 (Translate -> Local Render)
        if (activeLeftWindow != null) {
            int ox = guiLeft + LEFT_ORIGIN_X;
            int oy = guiTop + LEFT_ORIGIN_Y;
            int lx = mouseX - ox;
            int ly = mouseY - oy;
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            // 子类收到的是局部 lx, ly 和 宽高
            activeLeftWindow.render(g, activeLeftWindow.getWidth(), activeLeftWindow.getHeight(), lx, ly, partialTick);
            g.pose().popPose();
        }
        // 4. 渲染右窗口 (Translate -> Local Render)
        if (activeRightWindow != null) {
            int ox = guiLeft + RIGHT_ORIGIN_X;
            int oy = guiTop + RIGHT_ORIGIN_Y;
            int lx = mouseX - ox;
            int ly = mouseY - oy;
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            activeRightWindow.render(g, activeRightWindow.getWidth(), activeRightWindow.getHeight(), lx, ly, partialTick);
            g.pose().popPose();
        }
    }
    /** 绘制切页按钮 */
    private void renderTabButtons(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int b1x = left + TAB_SCREEN_X_1;
        int b1y = top + TAB_SCREEN_Y;
        int b2x = left + TAB_SCREEN_X_2;
        int b2y = top + TAB_SCREEN_Y;

        boolean r1Active = (activeRightWindow != null && !rightWindows.isEmpty() && activeRightWindow == rightWindows.get(0));
        boolean r2Active = (activeRightWindow != null && rightWindows.size() > 1 && activeRightWindow == rightWindows.get(1));

        // 示例：用颜色区分，你以后换成 blit 贴图
        g.fill(b1x, b1y, b1x + TAB_BTN_W, b1y + TAB_BTN_H, r1Active ? 0xFF00AA00 : 0xFF555555);
        g.fill(b2x, b2y, b2x + TAB_BTN_W, b2y + TAB_BTN_H, r2Active ? 0xFF00AA00 : 0xFF555555);
    }
    // =========================================
    // 4. 输入事件分发 (Event Dispatch)
    // =========================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (focusedWindow != null) return true;

        // 试探右窗口
        if (dispatchToWindow(activeLeftWindow, mouseX, mouseY, (w, lx, ly, inside) -> {
            if (!inside) return false;
            // 点击必须在窗口内
            if (w.mouseClicked(lx, ly, button))
            {   focusedWindow = w;
                // 抢占焦点
                isDraggingGesture = true;
                return true;
            } return false;
        })) return true;
        // 试探右窗口
        if (dispatchToWindow(activeLeftWindow, mouseX, mouseY, (w, lx, ly, inside) -> {
            if (!inside) return false;
            // 点击必须在窗口内
            if (w.mouseClicked(lx, ly, button))
            { focusedWindow = w;
                // 抢占焦点
                isDraggingGesture = true;
                return true;
            } return false;
        })) return true;

        // 4. 如果子窗口没处理，交给 super (处理原版背包点击等)
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 只有在拖拽手势且有焦点时才分发
        if (isDraggingGesture && focusedWindow != null) {
            // 这里不需要 inside 检查：拖出窗口也要发给 focus
            return dispatchToWindow(focusedWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                    w.mouseDragged(lx, ly, button, dragX, dragY)
            );
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (focusedWindow != null) {
            boolean consumed = dispatchToWindow(focusedWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                    w.mouseReleased(lx, ly, button)
            );
            // 无论是否消费，Release 后必须释放焦点
            focusedWindow = null;
            isDraggingGesture = false;
            if (consumed) return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 滚轮不需要焦点，谁在鼠标下谁响应
        // 右窗口
        if (dispatchToWindow(activeRightWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                inside && w.mouseScrolled(lx, ly, delta)
        )) return true;

        // 左窗口
        if (dispatchToWindow(activeLeftWindow, mouseX, mouseY, (w, lx, ly, inside) ->
                inside && w.mouseScrolled(lx, ly, delta)
        )) return true;

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 处理 Tab 按钮点击 */
    private boolean handleTabButtonsClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        int b1x = left + TAB_SCREEN_X_1;
        int b1y = top + TAB_SCREEN_Y;
        int b2x = left + TAB_SCREEN_X_2;
        int b2y = top + TAB_SCREEN_Y;

        if (isHovering(b1x, b1y, TAB_BTN_W, TAB_BTN_H, (int)mouseX, (int)mouseY)) {
            switchRightWindow(0); // 切换到右1
            return true;
        }
        if (isHovering(b2x, b2y, TAB_BTN_W, TAB_BTN_H, (int)mouseX, (int)mouseY)) {
            switchRightWindow(1); // 切换到右2
            return true;
        }
        return false;
    }

    // =========================================
    // 5. 核心工具方法
    // =========================================

    /**
     * 核心分发器：将全局鼠标坐标转换为窗口局部坐标，并执行回调
     */
    private boolean dispatchToWindow(UiWindow window, double mouseX, double mouseY, WindowEventHandler handler) {
        if (window == null) return false;

        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;

        // 根据 Side 决定原点
        int originX = (window.getSide() == WindowSide.LEFT) ? (guiLeft + LEFT_ORIGIN_X) : (guiLeft + RIGHT_ORIGIN_X);
        int originY = (window.getSide() == WindowSide.LEFT) ? (guiTop + LEFT_ORIGIN_Y) : (guiTop + RIGHT_ORIGIN_Y);

        int localX = (int)mouseX - originX;
        int localY = (int)mouseY - originY;

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

    // 鼠标栈管理
    public ItemStack getMouseStack() { return mouseStack; }
    public void setMouseStack(ItemStack stack) { this.mouseStack = (stack == null) ? ItemStack.EMPTY : stack; }
    public ItemStack takeMouseStack() {
        ItemStack old = mouseStack;
        mouseStack = ItemStack.EMPTY;
        return old;
    }

    @Override
    public void onClose() {
        // 生命周期收尾
        if (activeLeftWindow != null) activeLeftWindow.onHide();
        if (activeRightWindow != null) activeRightWindow.onHide();

        // 归还鼠标栈
        returnStackToPlayer(mouseStack);
        mouseStack = ItemStack.EMPTY;

        // 归还各窗口临时栈
        for (UiWindow w : leftWindows) returnStackToPlayer(w.collectTempStackForReturn());
        for (UiWindow w : rightWindows) returnStackToPlayer(w.collectTempStackForReturn());

        super.onClose();
    }

    private void returnStackToPlayer(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            if (!this.minecraft.player.getInventory().add(stack)) {
                this.minecraft.player.drop(stack, false);
            }
        }
    }

    // =========================================
    // 6. 内部类定义：UiWindow 抽象类与枚举
    // =========================================

    public enum WindowSide { LEFT, RIGHT }

    /**
     * 抽象窗口基类
     * 所有 mouseXXX 和 render 接收的参数均为局部坐标 (0,0 = 窗口左上角)
     */
    public abstract static class UiWindow {
        protected final SpellScreen host;
        private int windowId = -1;
        private WindowSide side;
        private int width;
        private int height;

        protected UiWindow(SpellScreen host) { this.host = host; }

        // 元数据注入（主类调用）
        void _injectMeta(int id, WindowSide side, int w, int h) {
            this.windowId = id;
            this.side = side;
            this.width = w;
            this.height = h;
        }

        public int getWindowId() { return windowId; }
        public WindowSide getSide() { return side; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }

        public void onShow() {}
        public void onHide() {}
        public ItemStack collectTempStackForReturn() { return ItemStack.EMPTY; }
        // 渲染：只接收宽高和局部鼠标
        public abstract void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick);
        /**
         * @param g GuiGraphics
         * @param mouseX 全局鼠标X (用于绘制 Tooltip 跟随鼠标)
         * @param mouseY 全局鼠标Y
         * @param localX 局部鼠标X (用于 getEntryAtPosition 碰撞检测)
         * @param localY 局部鼠标Y
         */
        public void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int localX, int localY) {}

        // 【修改】：参数名改为 localX, localY，明确告知实现者这里收到的是相对坐标
        public boolean mouseClicked(double localX, double localY, int button) { return false; }
        public boolean mouseReleased(double localX, double localY, int button) { return false; }
        public boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY) { return false; }
        public boolean mouseScrolled(double localX, double localY, double delta) { return false; }
    }

    /**
     * 绘制跟随鼠标的浮动图标
     */
    private void drawFloatingIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, SpellData sd) {
        if (sd == null || sd == SpellData.EMPTY) return;
        AbstractSpell spell = sd.getSpell();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500); // 极高的Z轴，保证在最上层
        guiGraphics.blit(spell.getSpellIconResource(), mouseX - 8, mouseY - 8, 0, 0, 16, 16, 16, 16);
        guiGraphics.pose().popPose();
    }
}
