package io.isb.modifier.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.net.ModMessage;
import io.isb.modifier.net.PacketExtractSpell;
import io.isb.modifier.net.PacketInscribeSpell;
import io.isb.modifier.net.PacketSwapBookSpell;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.player.ClientRenderCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

import java.util.*;

public class SpellScreen extends AbstractContainerScreen<SpellMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicModifier.MODID, "textures/gui/spell_inscription.png");

    // === 魔法合成系统变量 ===
    // 0,1 为输入槽，2,3 为输出槽
    private final SpellData[] synthSlots = {SpellData.EMPTY, SpellData.EMPTY, SpellData.EMPTY, SpellData.EMPTY};
    private static final int SYNTH_SLOT_SIZE = 19;

    // 坐标常量
    private static final int SYNTH_LABEL_X = 161;
    private static final int SYNTH_LABEL_Y = 88;

    // 输入槽位 (165, 99) 和 (165, 122)
    private static final int SYNTH_IN_X = 165;
    private static final int[] SYNTH_IN_Y = {99, 122};

    // 输出槽位 (232, 99) 和 (232, 122)
    private static final int SYNTH_OUT_X = 232;
    private static final int[] SYNTH_OUT_Y = {99, 122};

    // 按钮 (202, 112)
    private static final int SYNTH_BTN_X = 202;
    private static final int SYNTH_BTN_Y = 112;
    private static final int SYNTH_BTN_W = 14;
    private static final int SYNTH_BTN_H = 14;

    // 合成拖拽逻辑
    private boolean isDraggingSynth = false;
    private int draggedSynthSlotIndex = -1; // 0-3


    // === 左侧列表布局常量 (保持不变) ===
    private boolean pendingConsume = false;
    private int pendingSlotIndex = -1;
    private int pendingTicks = 0;
    // === 右侧书槽位拖拽 ===
    private boolean isDraggingBook = false;
    private int draggedBookSlotIndex = -1;
    private SpellData draggedBookSpellData = SpellData.EMPTY; // 用于渲染icon/判断

    private static final int GRID_X = 26;
    private static final int GRID_Y = 16;
    private static final int COL_GAP = 20;
    private static final int ROW_GAP = 24;
    private static final int COLS = 4;
    private static final int MAX_Y = 151;
    private static final int TITLE_HEIGHT = 11;
    private static final int CATEGORY_PADDING = 3;

    // === 滚轮常量 (完全参考你提供的代码) ===
    private static final int SCROLL_X = 123;
    private static final int SCROLL_Y = 32;
    private static final int SCROLL_W = 12; // 为了方便点击，这里判定范围可以宽一点，但绘制可以用1px
    private static final int SCROLL_H = 95;
    private static final int SCROLL_COLOR_LIGHT = 0xFFA57855;

    // === 右侧法术书槽位 ===
    private static final int BOOK_BOX_X = 160;
    // 【修改点】：之前是24，要求-12，所以设为 12
    private static final int BOOK_BOX_Y = 12;

    private static final int BOOK_BOX_WIDTH = 96;
    private static final int BOOK_BOX_HEIGHT = 80;

    // 槽位逻辑 (19x19)
    private static final int SLOT_SIZE = 19;
    private static final int SLOT_TEXTURE_V = 178;
    private static final int SLOT_OFFSET_NORMAL = 0;
    private static final int SLOT_OFFSET_HOVER = 19;
    private static final int SLOT_OFFSET_DRAG = 38;

    // === 运行时数据 ===
    // 核心修改：Value 类型改为 SpellListEntry
    private final Map<SchoolType, List<SpellListEntry>> groupedItemIndices = new LinkedHashMap<>();

    // 内部类定义（保持在你代码里即可）
    private static class SpellListEntry {
        final AbstractSpell spell;
        final int level;
        final List<Integer> invSlots;
        // 新增：记录物品真实的堆叠总数
        int totalCount = 0;

        SpellListEntry(AbstractSpell spell, int level) {
            this.spell = spell;
            this.level = level;
            this.invSlots = new ArrayList<>();
            this.totalCount = 0;
        }

        // 添加方法：同时记录槽位和数量
        void add(int slotIndex, int count) {
            this.invSlots.add(slotIndex);
            this.totalCount += count;
        }

        int pickOneSlot() { return invSlots.isEmpty() ? -1 : invSlots.get(0); }
    }
    private int cachedContentHeight = 0; // 等同于你提供的 contentHeight

    // === 滚轮变量 (参考你提供的代码) ===
    private float scrollOffs = 0.0f;
    private boolean isScrolling = false;

    // === 拖拽逻辑 ===
    private boolean isDragging = false;
    private int draggedScrollSlotIndex = -1;
    private ItemStack draggedStack = ItemStack.EMPTY;

    public SpellScreen(SpellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 277;
        this.imageHeight = 177;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.updateFilteredItems();
    }

    @Override
    public void onClose() {
        // 关闭界面时，如果手上有东西，重置状态（逻辑上它还在背包里）
        this.isDragging = false;
        this.draggedScrollSlotIndex = -1;
        this.draggedStack = ItemStack.EMPTY;
        super.onClose();
    }

    /**
     * 更新列表
     * 【关键修改】：如果正在拖拽，被拖拽的物品将**完全不包含**在列表中
     */
    private void updateFilteredItems() {
        groupedItemIndices.clear();

        Inventory inv = this.menu.playerInv;

        // 临时聚合：school -> (key -> entry)
        Map<SchoolType, Map<String, SpellListEntry>> tmp = new LinkedHashMap<>();

        for (int i = 0; i < inv.items.size(); i++) {
            if ((isDragging && i == draggedScrollSlotIndex) ||
                    (pendingConsume && i == pendingSlotIndex)) {
                continue;
            }

            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof Scroll)) continue;

            ISpellContainer sc = ISpellContainer.get(stack);
            if (sc.isEmpty()) continue;

            SpellData sd = sc.getSpellAtIndex(0);
            if (sd == null || sd == SpellData.EMPTY) continue;

            AbstractSpell spell = sd.getSpell();

            // 解决 final 报错
            int rawLevel = sd.getLevel();
            if (rawLevel <= 0) rawLevel = 1;
            final int fixedLevel = rawLevel;

            SchoolType school = spell.getSchoolType();
            String key = spell.getSpellId() + "#" + fixedLevel;

            tmp.computeIfAbsent(school, k -> new LinkedHashMap<>());
            Map<String, SpellListEntry> schoolMap = tmp.get(school);

            SpellListEntry entry = schoolMap.computeIfAbsent(key, k -> new SpellListEntry(spell, fixedLevel));

            // ✅ 核心修复：传入 stack.getCount()，确保堆叠的物品也被算入总数
            entry.add(i, stack.getCount());
        }

        // 转成最终结构 + 排序
        for (Map.Entry<SchoolType, Map<String, SpellListEntry>> e : tmp.entrySet()) {
            List<SpellListEntry> list = new ArrayList<>(e.getValue().values());

            list.sort((a, b) -> {
                String an = a.spell.getDisplayName(this.minecraft.player).getString();
                String bn = b.spell.getDisplayName(this.minecraft.player).getString();
                int c = an.compareToIgnoreCase(bn);
                if (c != 0) return c;
                return Integer.compare(a.level, b.level);
            });

            groupedItemIndices.put(e.getKey(), list);
        }

        this.cachedContentHeight = calculateContentHeight();
    }


    private int calculateContentHeight() {
        int height = 0;
        // 这里的 entry.getValue() 现在是 List<SpellListEntry>，size() 依然有效
        for (Map.Entry<SchoolType, List<SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            height += TITLE_HEIGHT;
            int rows = (entry.getValue().size() + COLS - 1) / COLS;
            if (rows > 0) height += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
        return height == 0 ? 0 : height;
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (pendingConsume) {
            pendingTicks++;
            ItemStack s = this.menu.playerInv.getItem(pendingSlotIndex);

            // 条件：该格子已经空了 或 不再是卷轴（说明服务端同步已到）
            if (s.isEmpty() || !(s.getItem() instanceof Scroll)) {
                pendingConsume = false;
                pendingSlotIndex = -1;
            }

            // 超时兜底：比如 20 ticks = 1 秒，避免永远卡住
            if (pendingTicks > 20) {
                pendingConsume = false;
                pendingSlotIndex = -1;
            }
        }

        // 每一帧都更新列表，确保背包变化（如右键卸载后）能立刻反映在列表里
        this.updateFilteredItems();

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // === 现在在这里统一画所有 Tooltip ===
        if (!isDragging && !isDraggingBook) {
            // 1. 画左侧列表 Tooltip
            this.renderItemTooltips(guiGraphics, mouseX, mouseY);
            // 2. 画右侧书槽 Tooltip (新增)
            this.renderBookTooltips(guiGraphics, mouseX, mouseY);
        }


        // === 渲染鼠标上拿着的图标 ===
        if (isDragging && !draggedStack.isEmpty()) {
            ISpellContainer sc = ISpellContainer.get(draggedStack);
            if (!sc.isEmpty()) {
                AbstractSpell spell = sc.getSpellAtIndex(0).getSpell();
                ResourceLocation icon = spell.getSpellIconResource();

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 500); // 顶层渲染
                // 16x16 图标跟随鼠标
                guiGraphics.blit(icon, mouseX - 8, mouseY - 8, 0, 0, 16, 16, 16, 16);
                guiGraphics.pose().popPose();
            }
        }
        // === 渲染鼠标上拿着的图标：书槽位拖拽 ===
        if (isDraggingBook && draggedBookSpellData != null && draggedBookSpellData != SpellData.EMPTY) {
            AbstractSpell spell = draggedBookSpellData.getSpell();
            ResourceLocation icon = spell.getSpellIconResource();

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);
            guiGraphics.blit(icon, mouseX - 8, mouseY - 8, 0, 0, 16, 16, 16, 16);
            guiGraphics.pose().popPose();
        }

    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        // 1. 背景
        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight, 512, 512);

        // 2. 右侧法术书
        renderBookSlots(guiGraphics, left, top, mouseX, mouseY);

        // 3. 左侧滚动列表
        renderScrollableList(guiGraphics, left, top, mouseX, mouseY);

        // 4. === 新增：渲染合成系统 ===
        renderSynthesisUI(guiGraphics, left, top, mouseX, mouseY);
    }


    private void renderBookSlots(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);

        // === 1. 没有魔法书的提示 ===
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) {
            Component msg = Component.literal("未携带魔法书！")
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
            guiGraphics.drawCenteredString(
                    this.font,
                    msg,
                    left + BOOK_BOX_X + BOOK_BOX_WIDTH / 2,
                    top + BOOK_BOX_Y + 30,
                    0xFFFFFFFF
            );
            return;
        }

        // === 2. 标题 ===
        Component titleMsg = Component.literal("魔法书：")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK);
        guiGraphics.drawString(
                this.font,
                titleMsg,
                left + BOOK_BOX_X,
                top + BOOK_BOX_Y,
                0xFF000000,
                false
        );

        // === 3. 获取书的法术容器 ===
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);

        // 这是“槽位数量”，不是已有法术数量
        int maxSpells = bookContainer.getMaxSpellCount();

        // === 4. 按“槽位索引”循环（这是关键） ===
        for (int i = 0; i < maxSpells; i++) {

            // 计算当前槽位的屏幕坐标
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            int slotX = (int) pos.x;
            int slotY = (int) pos.y;

            boolean isHovered = isHovering(
                    slotX, slotY,
                    SLOT_SIZE, SLOT_SIZE,
                    mouseX, mouseY
            );

            // === 5. 槽位背景（hover / drag） ===
            int uOffset = SLOT_OFFSET_NORMAL;
            if (isDragging && isHovered) {
                uOffset = SLOT_OFFSET_DRAG;
            } else if (isHovered) {
                uOffset = SLOT_OFFSET_HOVER;
            }

            guiGraphics.blit(
                    TEXTURE,
                    slotX, slotY,
                    uOffset, SLOT_TEXTURE_V,
                    SLOT_SIZE, SLOT_SIZE,
                    512, 512
            );

            // =====================================================
            // === 6. 【关键修复点】按槽位取法术，而不是用 getAllSpells()
            // =====================================================
            SpellData spellData = bookContainer.getSpellAtIndex(i);
            // 如果正在拖拽书槽位上的法术，则“视觉上隐藏”原槽位的法术显示
            if (isDraggingBook && i == draggedBookSlotIndex) {
                spellData = SpellData.EMPTY; // 只影响显示，不改真实数据
            }
            // 槽位为空，直接跳过（不画图标、不显示 tooltip）
            if (spellData == null || spellData == SpellData.EMPTY) {
                continue;
            }

            // === 7. 绘制法术图标 ===
            AbstractSpell spell = spellData.getSpell();
            ResourceLocation icon = spell.getSpellIconResource();
            guiGraphics.blit(
                    icon,
                    slotX + 1, slotY + 1,
                    0, 0,
                    16, 16,
                    16, 16
            );


        }
    }
    private void renderBookTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            int slotX = (int) pos.x;
            int slotY = (int) pos.y;

            if (isHovering(slotX, slotY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                // 只有当没有拖拽时才显示
                if (!(isDragging || isDraggingBook)) {
                    SpellData spellData = bookContainer.getSpellAtIndex(i);
                    if (spellData != null && spellData != SpellData.EMPTY) {
                        guiGraphics.renderTooltip(
                                this.font,
                                getTooltipLines(spellData),
                                Optional.empty(),
                                mouseX, mouseY
                        );
                    }
                }
                // 找到一个 hover 的槽位后就可以 break 了（因为鼠标只能指一个）
                // 这是一个小的性能优化，不加也没事
                return;
            }
        }
    }
    private List<Component> getTooltipLines(SpellData spellData) {
        List<Component> lines = new ArrayList<>();

        // 1. 判空防御
        if (spellData == null || SpellData.EMPTY.equals(spellData)) {
            return lines;
        }

        AbstractSpell spell = spellData.getSpell();
        int level = spellData.getLevel();
        var player = this.minecraft.player;

        // 2. 标题：法术名 (带稀有度颜色)
        // getRarity(level) 会根据等级计算稀有度
        SpellRarity rarity = spell.getRarity(level);
        lines.add(spell.getDisplayName(player).withStyle(rarity.getDisplayName().getStyle()));

        // 3. 等级显示 (Lv. 10)
        lines.add(Component.translatable("ui.irons_spellbooks.level", level).withStyle(rarity.getDisplayName().getStyle()));

        // 4. 法术独特属性 (伤害、时长等) —— 这是关键，和左边一致的核心
        List<MutableComponent> uniqueInfo = spell.getUniqueInfo(level, player);
        if (!uniqueInfo.isEmpty()) {
            lines.addAll(uniqueInfo);
        }

        return lines;
    }

    private Vec2 getBookSlotPosition(int slotIndex, int totalSpells, int guiLeft, int guiTop) {
        int boxSize = SLOT_SIZE;
        int[] rowCounts = ClientRenderCache.getRowCounts(totalSpells);

        int rowIndex = 0;
        int colIndex = slotIndex;
        for (int r = 0; r < rowCounts.length; r++) {
            if (colIndex < rowCounts[r]) {
                rowIndex = r;
                break;
            }
            colIndex -= rowCounts[r];
        }

        int rowCountInThisRow = rowCounts[rowIndex];
        int centerX = guiLeft + BOOK_BOX_X + BOOK_BOX_WIDTH / 2;
        int centerY = guiTop + BOOK_BOX_Y + BOOK_BOX_HEIGHT / 2;
        int totalHeight = rowCounts.length * boxSize;
        int currentRowWidth = rowCountInThisRow * boxSize;

        int x = centerX - (currentRowWidth / 2) + (colIndex * boxSize);
        int y = centerY - (totalHeight / 2) + (rowIndex * boxSize);

        return new Vec2(x, y);
    }

    // === 交互逻辑：点击 ===
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键
            // 1. 点击滚动条 (参考你提供的代码逻辑)
            int left = (this.width - this.imageWidth) / 2;
            int top = (this.height - this.imageHeight) / 2;

            // 扩充一点判定范围方便点击
            double scrollXMin = left + SCROLL_X - 2;
            double scrollXMax = left + SCROLL_X + SCROLL_W + 2;
            double scrollYMin = top + SCROLL_Y;
            double scrollYMax = top + SCROLL_Y + SCROLL_H;

            if (mouseX >= scrollXMin && mouseX <= scrollXMax && mouseY >= scrollYMin && mouseY <= scrollYMax) {
                int visibleHeight = MAX_Y - GRID_Y;
                if (this.cachedContentHeight > visibleHeight) {
                    this.isScrolling = true;
                    updateScrollPos(mouseY, top);
                    return true;
                }
            }
            if (button == 0) {
                int Dleft = (this.width - this.imageWidth) / 2;
                int Dtop = (this.height - this.imageHeight) / 2;

                // 如果当前没在拖拽卷轴，允许从书槽位开始拖拽
                if (!isDragging && tryStartDragBookSpell((int) mouseX, (int) mouseY, Dleft, Dtop)) {
                    return true;
                }

                // ...你原来的滚动条点击、列表点击逻辑...
            }

            // 2. 检查列表物品点击 (开始拖拽)
            if (checkListClick((int)mouseX, (int)mouseY)) {
                return true;
            }

        } else if (button == 1) { // 右键
            // 3. 检查右键点击魔法书槽位 (卸载)
            checkBookRightClick((int)mouseX, (int)mouseY);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateScrollPos(double mouseY, int top) {
        double barStart = top + SCROLL_Y;
        // 使用 SCROLL_H 进行归一化计算
        this.scrollOffs = (float) ((mouseY - barStart) / SCROLL_H);
        this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
    }

    /**
     * 处理右键点击魔法书槽位 (卸下法术)
     * 逻辑：发包 -> 服务端移除法术并放入背包 -> 服务端同步 -> 客户端每帧刷新列表时检测到新物品
     */
    private void checkBookRightClick(int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null) return;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE &&
                    mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {

                // 只有该槽位确实有法术时，才发送提取请求
                if (bookContainer.getSpellAtIndex(i) != SpellData.EMPTY) {
                    // 发送请求，剩下的交给服务端的一连串原子操作
                    ModMessage.sendToServer(new PacketExtractSpell(i));
                }
                return;
            }
        }
    }

    // === 交互逻辑：滚轮 (完全参考你提供的代码) ===
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleHeight = MAX_Y - GRID_Y;

        if (this.cachedContentHeight > visibleHeight) {
            // 你提供的代码逻辑：scrollStep = ROW_GAP / (contentHeight - visibleHeight)
            float scrollStep = (float) ROW_GAP / (this.cachedContentHeight - visibleHeight);
            this.scrollOffs = Mth.clamp(this.scrollOffs - (float)delta * scrollStep, 0.0F, 1.0F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // === 交互逻辑：拖拽滚动条 ===
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling && button == 0) {
            int top = (this.height - this.imageHeight) / 2;
            updateScrollPos(mouseY, top);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // === 交互逻辑：左侧列表点击检查 ===
    private boolean checkListClick(int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int visibleHeight = MAX_Y - GRID_Y;
        boolean canScroll = this.cachedContentHeight > visibleHeight;
        int scrollPixels = canScroll ? (int) (this.scrollOffs * (this.cachedContentHeight - visibleHeight)) : 0;
        int currentY = top + GRID_Y - scrollPixels;

        if (mouseY < top + GRID_Y || mouseY > top + MAX_Y) return false;

        for (Map.Entry<SchoolType, List<SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            currentY += TITLE_HEIGHT;
            List<SpellListEntry> entries = entry.getValue();

            for (int i = 0; i < entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int itemX = left + GRID_X + col * COL_GAP;
                int itemY = currentY + row * ROW_GAP;

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    SpellListEntry se = entries.get(i);
                    // 开始拖拽
                    this.isDragging = true;
                    // 从 Entry 里取出一个实际的背包槽位
                    this.draggedScrollSlotIndex = se.pickOneSlot();
                    this.draggedStack = this.menu.playerInv.getItem(this.draggedScrollSlotIndex);
                    this.updateFilteredItems();
                    return true;
                }
            }
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
        return false;
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 先处理书槽位拖拽
            if (isDraggingBook) {
                handleBookSpellDrop((int) mouseX, (int) mouseY);
                isDraggingBook = false;
                draggedBookSlotIndex = -1;
                draggedBookSpellData = SpellData.EMPTY;
                return true; // 吃掉
            }

            // 你原本的卷轴拖拽处理
            if (isDragging) {
                handleDrop((int) mouseX, (int) mouseY);
                isDragging = false;
                draggedStack = ItemStack.EMPTY;
                draggedScrollSlotIndex = -1;
                this.updateFilteredItems();
            }
            this.isScrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleDrop(int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null) return;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);

            // 判断是否拖到了某个槽位上
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE &&
                    mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {

                // 1. 发包给服务端 (服务端负责真实的逻辑：装法术 + 删物品)
                ModMessage.sendToServer(new PacketInscribeSpell(draggedScrollSlotIndex, i));
                pendingConsume = true;
                pendingSlotIndex = draggedScrollSlotIndex;
                pendingTicks = 0;


                return;
            }
        }
    }

    private void renderScrollableList(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        int visibleHeight = MAX_Y - GRID_Y;
        boolean canScroll = this.cachedContentHeight > visibleHeight;

        // === 滚动条绘制 (保持不变) ===
        if (canScroll) {
            int scrollBarX = left + SCROLL_X;
            int scrollBarY = top + SCROLL_Y;
            float ratio = (float) visibleHeight / (float) this.cachedContentHeight;
            int barHeight = (int) (ratio * SCROLL_H);
            barHeight = Mth.clamp(barHeight, 10, SCROLL_H);
            int barYOffset = (int) (this.scrollOffs * (SCROLL_H - barHeight));
            guiGraphics.fill(scrollBarX, scrollBarY + barYOffset, scrollBarX + 2, scrollBarY + barYOffset + barHeight, SCROLL_COLOR_LIGHT);
        } else {
            this.scrollOffs = 0.0f;
        }

        int scissorX = left + GRID_X;
        int scissorY = top + GRID_Y;
        int scissorW = COLS * COL_GAP + 20;
        int scissorH = visibleHeight;

        guiGraphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

        int scrollPixels = canScroll ? (int) (this.scrollOffs * (this.cachedContentHeight - visibleHeight)) : 0;
        int currentY = top + GRID_Y - scrollPixels;

        for (Map.Entry<SchoolType, List<SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            SchoolType school = entry.getKey();
            List<SpellListEntry> entries = entry.getValue();

            // 标题
            if (currentY + 9 > top + GRID_Y && currentY < top + MAX_Y) {
                int color = 0xFFFFFFFF;
                if (school.getDisplayName().getStyle().getColor() != null) color = school.getDisplayName().getStyle().getColor().getValue();
                guiGraphics.drawString(this.font, school.getDisplayName(), left + GRID_X, currentY, color, true);
            }
            currentY += TITLE_HEIGHT;

            for (int i = 0; i < entries.size(); i++) {
                SpellListEntry se = entries.get(i);
                int col = i % COLS;
                int row = i / COLS;
                int itemX = left + GRID_X + col * COL_GAP;
                int itemY = currentY + row * ROW_GAP;

                if (itemY + 16 >= top + GRID_Y && itemY <= top + MAX_Y) {
                    ResourceLocation icon = se.spell.getSpellIconResource();

                    // ✅ 修复：只要总数 > 1 (无论是在一个格子里还是分开的)，都显示叠层
                    if (se.totalCount > 1) {
                        RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, 1.0F);
                        guiGraphics.blit(icon, itemX + 2, itemY + 2, 0, 0, 16, 16, 16, 16);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                    guiGraphics.blit(icon, itemX, itemY, 0, 0, 16, 16, 16, 16);

                    drawLevelBadge(guiGraphics, itemX, itemY, se.level);
                }
            }
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
        guiGraphics.disableScissor();
    }




    private void renderItemTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int visibleHeight = MAX_Y - GRID_Y;
        boolean canScroll = this.cachedContentHeight > visibleHeight;
        int scrollPixels = canScroll ? (int) (this.scrollOffs * (this.cachedContentHeight - visibleHeight)) : 0;
        int currentY = top + GRID_Y - scrollPixels;

        for (Map.Entry<SchoolType, List<SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            currentY += TITLE_HEIGHT;
            List<SpellListEntry> entries = entry.getValue();

            for (int i = 0; i < entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int itemX = left + GRID_X + col * COL_GAP;
                int itemY = currentY + row * ROW_GAP;

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    if (itemY >= top + GRID_Y - 1 && itemY + 16 <= top + MAX_Y + 1) {
                        SpellListEntry se = entries.get(i);
                        ItemStack stack = this.menu.playerInv.getItem(se.pickOneSlot());

                        List<Component> tooltip = stack.getTooltipLines(this.minecraft.player, net.minecraft.client.Minecraft.getInstance().options.advancedItemTooltips ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED : net.minecraft.world.item.TooltipFlag.Default.NORMAL);

                        int insertIdx = 1;
                        tooltip.add(insertIdx++, Component.literal(" 数量: " + se.totalCount).withStyle(ChatFormatting.GRAY));



                        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                    }
                }
            }
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
    }



    private boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
    private boolean tryStartDragBookSpell(int mouseX, int mouseY, int left, int top) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return false;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE &&
                    mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {

                SpellData data = bookContainer.getSpellAtIndex(i);
                if (data != null && data != SpellData.EMPTY) {
                    // 开始拖书里的法术
                    isDraggingBook = true;
                    draggedBookSlotIndex = i;
                    draggedBookSpellData = data;
                    return true;
                }
                return false;
            }
        }
        return false;
    }
    private void handleBookSpellDrop(int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        // 规则1：拖到左侧（相对像素 137 往左）=> 退回背包（复用右键退回逻辑）
        // 你说“相对像素137”，这里按 GUI left 对齐：绝对阈值 = left + 137
        int extractThresholdX = left + 137;
        if (mouseX < extractThresholdX) {
            ModMessage.sendToServer(new PacketExtractSpell(draggedBookSlotIndex));
            return;
        }

        // 规则2：拖到另一个书槽位 => 空则移动，有则交换
        for (int to = 0; to < maxSpells; to++) {
            Vec2 pos = getBookSlotPosition(to, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE &&
                    mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {

                if (to == draggedBookSlotIndex) return; // 原地松手，无事发生

                // 发包：服务端执行 move/swap
                ModMessage.sendToServer(new PacketSwapBookSpell(draggedBookSlotIndex, to));
                return;
            }
        }

        // 规则3：丢到其他地方 => 取消（不做任何事）
    }
    private void drawLevelBadge(GuiGraphics g, int itemX, int itemY, int level) {
        // 角标区域（右上角），宽一点保证 "10" 放得下
        int w = 9;
        int h = 8;
        int x0 = itemX + 16 - w; // 16x16 图标
        int y0 = itemY;

        // 黑底方块
        g.fill(x0, y0, x0 + w, y0 + h, 0xFF000000);

        // 等级文本：1~9 绿色，10 金色
        int color = (level >= 10) ? 0xFFFFD700 : 0xFF00FF00; // 金 / 绿
        String txt = String.valueOf(Math.min(level, 10));

        // 右对齐放进角标，确保不溢出
        int tw = this.font.width(txt);
        int tx = x0 + w - tw - 1;
        int ty = y0; // 顶部对齐即可（字体高8，角标高8）

        g.drawString(this.font, txt, tx, ty, color, false);
    }
}
