package io.isb.modifier.gui.page;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.gui.SpellScreen;
import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.ui.PacketUnifiedSwap;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

import static io.isb.modifier.gui.SpellScreen.TEXTURE;

public class SpellPage extends  SpellScreen.UiWindow{
    public SpellPage(SpellScreen spellScreen) {
        super(spellScreen);
    }
    // === 在 SpellPage 类中添加 ===

    // 记录当前选中的条目（用来渲染高亮框）
    private SpellListEntry selectedEntry = null;

    // 拖拽逻辑辅助变量
    private double dragStartX, dragStartY; // 按下时的坐标
    private boolean isDraggingItem = false; // 是否已经触发了物品拿取
    private static final double DRAG_THRESHOLD = 3.0; // 移动超过4像素才算拖拽

    private static final int TITLE_HEIGHT = 11; // 分类标题高度
    private static final int CATEGORY_PADDING = 3; // 分类间距
    private static final int ROW_GAP = 24;//每个图标的列间距
    private static final int COLS = 4;//每个图标横间距
    //布局
    private static final int GRID_X = 11;
    private static final int GRID_Y = 10;
    private static final int COL_GAP = 20;
    private static final int MAX_Y = 146; // 列表底部Y坐标限制
    // 滚动条
    private static final int SCROLL_X = 110;
    private static final int SCROLL_Y = 30;
    private static final int SCROLL_W = 12;
    private static final int SCROLL_H = 95;
    private static final int SCROLL_COLOR_LIGHT = 0xFFA57855;
    private static final float SCROLL_STEP = 16.0f; // 每一格滚轮滚动多少像素(可改成 12/18/24 试手感)

    // 列表缓存与滚动
    private final Map<SchoolType, List<SpellPage.SpellListEntry>> groupedItemIndices = new LinkedHashMap<>();
    private int cachedContentHeight = 0;
    private float scrollOffs = 0.0f;
    private boolean isScrolling = false;
    // === 内部类：列表条目 ===
    private static class SpellListEntry {
        //法术,等级,对应背包槽位，数量
        final AbstractSpell spell;
        final int level;
        final List<Integer> invSlots;
        int totalCount = 0;
        SpellListEntry(AbstractSpell spell, int level) {
            this.spell = spell;
            this.level = level;
            this.invSlots = new ArrayList<>();
            this.totalCount = 0;
        }


        void add(int slotIndex, int count) {
            this.invSlots.add(slotIndex);
            this.totalCount += count;
        }

        // 获取该法术堆叠中的第一个实际背包槽位
        int pickOneSlot() { return invSlots.isEmpty() ? -1 : invSlots.get(0); }
    }
    /** 核心逻辑：重新计算左侧列表显示的物品
     * 包含“鼠标拿着”、“合成槽占用”和“产物待领取”的三重扣除逻辑
     */
    @Override
    public void clearSelection() {
        this.selectedEntry = null; // 清空本地选中
    }
    // === 对外暴露当前选中的法术条目（给右侧详情窗口用）===
    public AbstractSpell getSelectedSpell() {
        return selectedEntry == null ? null : selectedEntry.spell;
    }

    public int getSelectedSpellLevel() {
        return selectedEntry == null ? 0 : selectedEntry.level;
    }

    public boolean hasSelectedSpell() {
        return selectedEntry != null;
    }
    private void updateFilteredItems() {
        groupedItemIndices.clear();//列表，为了实时更新列表会经常修改
        Inventory inv = this.host.getMenu().playerInv;//玩家的背包
        Map<SchoolType, Map<String, SpellPage.SpellListEntry>> tmp = new LinkedHashMap<>();
        boolean IsselectedEntry = false;
        for (int i = 0; i < inv.items.size(); i++) {

            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof Scroll)) continue;
            //空检测（安全）
            ISpellContainer sc = ISpellContainer.get(stack);
            if (sc.isEmpty()) continue;
            SpellData sd = sc.getSpellAtIndex(0);
            if (sd == null || sd == SpellData.EMPTY) continue;
            // === 数量计算核心 ===
            int visibleCount = stack.getCount();
            // 1. 如果鼠标正拖着这个格子 (鼠标也是一种槽)（数量会减，但是我已经打算分割玩家和槽位了）
            // 2. 如果这个格子被放入了合成槽 Input 1 且来源不是书
            // 3. 如果这个格子被放入了合成槽 Input 2 且来源不是书
            // 如果扣减后数量 <= 0，则不显示在列表中
            // 4. 处理“合成后产物需手动拿走”的视觉效果
            // 如果服务端已经合成了，但我们还没点产物槽，列表里其实已经有了那个产物
            // 我们需要在这里把它“藏”起来，直到玩家点击产物槽
            // === 数据聚合 ===
            AbstractSpell spell = sd.getSpell();
            int rawLevel = sd.getLevel();
            if (rawLevel <= 0) rawLevel = 1;
            final int fixedLevel = rawLevel;
            SchoolType school = spell.getSchoolType();
            //为了做排序，分离出school的“string”
            String key = spell.getSpellId() + "#" + fixedLevel;
            tmp.computeIfAbsent(school, k -> new LinkedHashMap<>());
            Map<String, SpellPage.SpellListEntry> schoolMap = tmp.get(school);
            //就是创建个新的
            SpellPage.SpellListEntry entry = schoolMap.computeIfAbsent(key, k -> new SpellPage.SpellListEntry(spell, fixedLevel));
            // 将计算后的可见数量加入条目
            entry.add(i, visibleCount);
            // 【新增】：校验 selectedEntry 是否还存在于新列表中
            // ==================================================
            if (this.selectedEntry != null) {
                if(selectedEntry.level == fixedLevel) {
                    if(selectedEntry.spell == spell){
                        //只要有一个法术通过这两关，则还存在列表中
                        IsselectedEntry = true;
                        //System.out.println("update ： entry= " + entry.spell.getSpellName());
                    }
                }
            }
        }
        if(!IsselectedEntry) {
           this.selectedEntry = null;

        }

        // === 排序与分组 (修改版 2.0) ===
        // 1. 先将 Map 转换为 List 以便进行自定义排序
        List<Map.Entry<SchoolType, List<SpellPage.SpellListEntry>>> sortedSchools = new ArrayList<>();
        for (Map.Entry<SchoolType, Map<String, SpellPage.SpellListEntry>> e : tmp.entrySet()) {
            List<SpellPage.SpellListEntry> list = new ArrayList<>(e.getValue().values());
            // 组内排序：按等级(高->低)，等级一样按名称
            list.sort((a, b) -> {
                // 优先级1：等级 (倒序，例如 10 级排在 1 级前面)
                int levelCompare = Integer.compare(b.level, a.level);
                if (levelCompare != 0) return levelCompare;
                // 优先级2：名称 (正序，字母表顺序)
                String an = a.spell.getDisplayName(this.host.getMinecraft().player).getString();
                String bn = b.spell.getDisplayName(this.host.getMinecraft().player).getString();
                return an.compareToIgnoreCase(bn);
            });
            sortedSchools.add(Map.entry(e.getKey(), list));
        }

        // 2. 对学派(School)进行排序 (保持逻辑：数量多的学派在最上面)
        sortedSchools.sort((a, b) -> {
            // 第一优先级：法术个数 (倒序，多的在上面)
            int countCompare = Integer.compare(b.getValue().size(), a.getValue().size());
            if (countCompare != 0) return countCompare;
            // 第二优先级：学派名称 (正序)
            return a.getKey().getDisplayName().getString().compareTo(b.getKey().getDisplayName().getString());
        });
        // 3. 填入最终的 LinkedHashMap
        for (Map.Entry<SchoolType, List<SpellPage.SpellListEntry>> e : sortedSchools) {
            groupedItemIndices.put(e.getKey(), e.getValue());
        }
        this.cachedContentHeight = calculateContentHeight();
    }
    /**
     * 计算列表内容的像素高度，用于滚动条逻辑
     */
    private int calculateContentHeight() {
        int height = 0;
        for (Map.Entry<SchoolType, List<SpellPage.SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            height += TITLE_HEIGHT;
            int rows = (entry.getValue().size() + COLS - 1) / COLS;
            if (rows > 0) height += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
        return height == 0 ? 0 : height;
    }

    @Override
    public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY, float partialTick) {
        // 处理消耗动画计时
        // 每帧更新列表，确保拖拽时的数量变化实时反馈
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEXTURE, 0, 0, 13, 2, w, h, 512, 512);
        updateFilteredItems();
        int visibleHeight = MAX_Y - GRID_Y;
        if (this.cachedContentHeight <= visibleHeight) {
            this.scrollOffs = 0.0f;
        } else {
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0f, 1.0f);
        }
        renderScrollableList(g);

    }
    @Override
    public boolean mouseScrolled(double localX, double localY, double delta) {
        // 只在鼠标位于列表可视区域时响应
        if (localX < GRID_X || localX > (GRID_X + COLS * COL_GAP + 20)) return false;
        if (localY < GRID_Y || localY > MAX_Y) return false;

        int visibleHeight = MAX_Y - GRID_Y;
        if (this.cachedContentHeight <= visibleHeight) return false; // 不需要滚动

        // 最大可滚像素
        int maxScrollPixels = this.cachedContentHeight - visibleHeight;

        // 当前滚动像素
        float currentPixels = this.scrollOffs * maxScrollPixels;

        // delta：通常 >0 是向上滚，<0 是向下滚
        // 向上滚：内容下移 => scrollPixels 减少
        currentPixels -= (float) (delta * SCROLL_STEP);

        // clamp
        currentPixels = Mth.clamp(currentPixels, 0.0f, (float) maxScrollPixels);

        // 转回 0~1
        this.scrollOffs = currentPixels / (float) maxScrollPixels;

        return true; // 吃掉滚轮事件
    }
    @Override
    public boolean mouseClicked(double localX, double localY, int button) {
        if (button != 0) return false;

        // 1. 直接查找
        SpellListEntry entry = getEntryAtPosition(localX, localY);
        System.out.println("mouseclick ");
        if (entry != null) {
            System.out.println("mouseclick(spell) : entry= " + entry.spell.getSpellName());
            this.selectedEntry = entry;
            this.dragStartX = localX;
            this.dragStartY = localY;
            this.isDraggingItem = false;
            // 【添加这行】告诉主类：我选中东西了，让右边那个把手松开
            this.host.claimSelectionFocus(this);
            // 播放个点击音效不错
            // Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true; // 抢占焦点
        }
        // 如果点了滚动条区域或其他地方...
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 如果已经开始拿物品了，就不做额外逻辑，交给 host 的 render 渲染鼠标栈即可
        if (isDraggingItem || !host.getMouseStack().isEmpty()) return true;

        // 计算移动距离
        double dist = Math.sqrt(Math.pow(mouseX - dragStartX, 2) + Math.pow(mouseY - dragStartY, 2));

        // === 触发拖拽的时刻 ===
        if (dist > DRAG_THRESHOLD && this.selectedEntry != null) {
            // 1. 真正执行“拿取物品”逻辑
            doPickupItem(this.selectedEntry);
            //调试debug
            ISpellContainer container = ISpellContainer.get(this.host.getMouseStack());
            SpellData spellData = container.getSpellAtIndex(0);
            if(spellData != SpellData.EMPTY){
                System.out.println("mouseclick(Screen这里) :法术： " + spellData.getSpell().getSpellName()+"等级:"+spellData.getLevel());
            }
            // 2. 标记为正在拖拽
            this.isDraggingItem = true;
            // 3. (可选) 拖起物品后，通常取消高亮选中，或者保留看你喜好
            // this.selectedEntry = null;
        }
        return true;
    }

    // 辅助方法：判断两个 Entry 是否代表同一个法术 (忽略对象地址，只看内容)
    private boolean isSameSpellEntry(SpellListEntry a, SpellListEntry b) {
        if (a == null || b == null) return false;
        // 比较法术 ID
        if (!a.spell.getSpellId().equals(b.spell.getSpellId())) return false;
        // 比较等级
        return a.level == b.level;
    }
    private void renderScrollableList(GuiGraphics guiGraphics) {
        int visibleHeight = MAX_Y - GRID_Y;
        boolean canScroll = this.cachedContentHeight > visibleHeight;

        // --- 1. 绘制滚动条 ---
        if (canScroll) {
            int scrollBarX = SCROLL_X;
            int scrollBarY = SCROLL_Y;

            float ratio = (float) visibleHeight / (float) this.cachedContentHeight;
            int barHeight = (int) (ratio * SCROLL_H);
            barHeight = Mth.clamp(barHeight, 10, SCROLL_H);

            int barYOffset = (int) (this.scrollOffs * (SCROLL_H - barHeight));
            guiGraphics.fill(scrollBarX, scrollBarY + barYOffset, scrollBarX + 2, scrollBarY + barYOffset + barHeight, SCROLL_COLOR_LIGHT);
        } else {
            this.scrollOffs = 0.0f;
        }

        // --- 2. 设置裁剪区域 (Scissor) ---
        // 【修正3】：Scissor 需要屏幕绝对坐标。
        // 我们需要计算：屏幕原点 + 窗口偏移 + 列表内偏移
        // 注意：这里假设 host.imageWidth=277, height=177，如果不固定请用 host.getImageWidth()
        int guiLeft = (this.host.width - 277) / 2;
        int guiTop  = (this.host.height - 177) / 2;

        // 窗口在 GUI 中的绝对偏移 (硬编码 13, 2，对应 SpellScreen 的 LEFT_ORIGIN)
        // 建议：最好在 SpellPage 里存一下自己的 originX/Y 或者从 SpellScreen 获取，这里先硬编码修复
        int winAbsX = guiLeft + 13;
        int winAbsY = guiTop + 2;

        int scissorX = winAbsX + GRID_X-2;
        int scissorY = winAbsY + GRID_Y;
        int scissorW = COLS * COL_GAP + 20; // 宽度稍微宽一点防止切字
        int scissorH = visibleHeight;

        // 开启裁剪
        guiGraphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

        // --- 3. 绘制列表内容 ---
        // 计算滚动像素
        int scrollPixels = canScroll ? (int) (this.scrollOffs * (this.cachedContentHeight - visibleHeight)) : 0;
        int currentY = GRID_Y - scrollPixels;

        for (Map.Entry<SchoolType, List<SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            SchoolType school = entry.getKey();
            List<SpellListEntry> entries = entry.getValue();

            // 绘制标题
            if (currentY + 9 > GRID_Y && currentY < MAX_Y) {
                int color = 0xFFFFFFFF;
                if (school.getDisplayName().getStyle().getColor() != null)
                    color = school.getDisplayName().getStyle().getColor().getValue();

                // 使用 host.font 绘制
                guiGraphics.drawString(this.host.getMinecraft().font, school.getDisplayName(), GRID_X, currentY, color, true);

            }
            currentY += TITLE_HEIGHT;

            // 绘制图标
            for (int i = 0; i < entries.size(); i++) {
                SpellListEntry se = entries.get(i);
                int col = i % COLS;
                int row = i / COLS;
                int itemX = GRID_X + col * COL_GAP;
                int itemY = currentY + row * ROW_GAP;

                // === 检测法术及其等级
                boolean can_kuang = false;
                if (this.selectedEntry != null && !this.isDraggingItem  ) {
                    if (this.selectedEntry.spell == se.spell  ) { // se 是当前循环到的 entry
                        if (this.selectedEntry.level == se.level) {
                            can_kuang = true;
                        }
                    }
                }

                // 只绘制在可见区域内的
                if (itemY + 16 >= GRID_Y && itemY <= MAX_Y) {
                    ResourceLocation icon = se.spell.getSpellIconResource();

//                    // 绘制堆叠阴影
//                    if (se.totalCount > 1 && !can_kuang) {
//                        RenderSystem.setShaderColor(1F, 1F, 0.2F, 1.0F);
//                        guiGraphics.blit(icon, itemX, itemY + 2, 2, 0, 16, 16, 16, 16);
//                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
//                    }
                    // 绘制本体
                    guiGraphics.blit(icon, itemX, itemY, 0, 0, 16, 16, 16, 16);
                    drawLevelBadge(guiGraphics, itemX, itemY, se.level);
                    if (can_kuang && !this.isDraggingItem  ) {
                                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                                // 在物品图标外画一个 18x18 的选中框
                                guiGraphics.blit(
                                        TEXTURE,
                                        itemX - 2, itemY - 2,   // 屏幕坐标（比物品大一圈）
                                        57, 178,                // 贴图坐标（你画好的框）
                                        19, 19,                 // 框的宽高
                                        512, 512                // texture 尺寸
                                );
                    }
                }
            }
            // 计算下一组的Y偏移
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }

        // 关闭裁剪
        guiGraphics.disableScissor();
    }
    private void drawLevelBadge(GuiGraphics g, int itemX, int itemY, int level) {
        int w = 9; int h = 8;
        int x0 = itemX + 16 - w; int y0 = itemY;
        g.fill(x0, y0, x0 + w, y0 + h, 0xFF000000);
        int color = (level >= 10) ? 0xFFFFD700 : 0xFF00FF00;
        String txt = String.valueOf(Math.min(level, 10));
        g.drawString(this.host.getMinecraft().font, txt, x0 + w - this.host.getMinecraft().font.width(txt) + 1, y0, color, false);
    }


    @Override
    public boolean mouseReleased(double localX, double localY, int button) {
        // 1. 结束拖拽状态
        this.isDraggingItem = false;
        //将物品归还背包
        this.host.returnStackToPlayer(this.host.getMouseStack());
        return false;
    }

    // 执行拿取逻辑
    // 执行拿取逻辑

    private void doPickupItem(SpellListEntry entry) {
        int slot = entry.pickOneSlot();
        if (slot != -1) {
            // 获取客户端玩家对象
            var player = this.host.getMinecraft().player;
            if (player == null) return;

            // --- 1. 客户端视觉预测 (Client-side Prediction) ---
            // 获取客户端背包里的那个物品堆
            ItemStack clientStack = this.host.getMenu().playerInv.getItem(slot);

            if (!clientStack.isEmpty()) {
                // 【关键步骤】直接在客户端内存中扣除数量！
                // split(1) 会修改 clientStack 的 count，并返回分出来的 1 个物品
                ItemStack visualHeldStack = clientStack.split(1);

                // 【关键步骤】立即设置到客户端的鼠标上
                // 这样 renderTooltip 和 render 方法里的 host.getMouseStack() 才能立马读到东西
                this.host.getMenu().setCarried(visualHeldStack);
            }

            // --- 2. 告诉服务端同步状态 ---
            // 发送包：告诉服务器“我实际执行了这个操作，请校验并同步”
            ModMessage.sendToServer(new PacketUnifiedSwap(
                    PacketUnifiedSwap.TYPE_PLAYER, slot ,    //  玩家背包
                    PacketUnifiedSwap.TYPE_MOUSE, 0     //  鼠标
            ));

            // --- 3. 强制触发一次列表更新 ---
            // 虽然 render 会每帧调用，但手动重置一下状态是个好习惯，确保 selectedEntry 状态正确
            this.updateFilteredItems();
        }
    }
    @Override
    public void renderTooltips(GuiGraphics g,int mouseX, int mouseY, int localX, int localY) {
        // 1. 直接复用查找逻辑
        SpellListEntry entry = getEntryAtPosition(localX, localY);
        System.out.println("Tooltips（spell）:DEBUG");
        if (entry != null) {
            // 2. 找到对应的真实物品
            int slot = entry.pickOneSlot();
            if (slot != -1) {
                ItemStack stack = this.host.getMenu().playerInv.getItem(slot);
                if (!stack.isEmpty()) {
                    if(stack.getItem() instanceof Scroll){
                        List<Component> tooltip = stack.getTooltipLines(this.host.getMinecraft().player, net.minecraft.client.Minecraft.getInstance().options.advancedItemTooltips ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED : net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                        // ▼▼▼▼▼▼▼▼▼▼▼▼▼▼ 这里是插入显示数量的代码 ▼▼▼▼▼▼▼▼▼▼▼▼▼▼
                        tooltip.add(1, Component.literal(" 数量: " + entry.totalCount).withStyle(ChatFormatting.GRAY));
                        // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
                        g.renderTooltip(this.host.getMinecraft().font, tooltip, Optional.empty(), mouseX, mouseY);
                    }


                }
            }
        }
    }
    /**
     * 核心碰撞检测：局部坐标计算
     * 自动处理了：滚动偏移、标题高度、裁剪区域
     */
    private SpellListEntry getEntryAtPosition(double localX, double localY) {
        if (localX < GRID_X || localY > MAX_Y) {
            return null;
        }

        // 2. 计算当前滚动偏移量 (像素)
        int visibleHeight = MAX_Y - GRID_Y;
        boolean canScroll = this.cachedContentHeight > visibleHeight;
        int scrollPixels = canScroll ? (int) (this.scrollOffs * (this.cachedContentHeight - visibleHeight)) : 0;

        // 3. 计算内容的起始 Y (注意：这里是把内容向上推，所以是减去 scrollPixels)
        int currentY = GRID_Y - scrollPixels;

        // 4. 遍历列表 (复用你的布局逻辑)
        for (Map.Entry<SchoolType, List<SpellListEntry>> group : groupedItemIndices.entrySet()) {
            // 跳过标题高度
            currentY += TITLE_HEIGHT;

            List<SpellListEntry> entries = group.getValue();
            for (int i = 0; i < entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;

                // 计算该图标的局部坐标
                int itemX = GRID_X + col * COL_GAP;
                int itemY = currentY + row * ROW_GAP;

                // 5. 核心判定
                // A. 鼠标是否在图标范围内 (16x16)
                if (localX >= itemX && localX < itemX + 16 &&
                        localY >= itemY && localY < itemY + 16) {
                    // B. 再次确认图标是否真正可见 (防穿透)
                    // 比如一个图标虽然算出来在这个位置，但它可能只露出一半，或者被上方标题挡住
                    // 简单的做法是：只要中心点在显示区就算有效，或者严格全包围
                    if (itemY >= GRID_Y - 8 && itemY + 16 <= MAX_Y + 8) {
                        return entries.get(i);
                    }
                }
            }

            // 跳过这一组的剩余高度
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }

        return null; // 没找到
    }

}
