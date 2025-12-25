package io.isb.modifier.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.net.*;
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

/**
 * 魔法合成与法术书管理界面
 * 包含左侧卷轴列表、右侧法术书槽位以及中间的合成/升级系统
 */
public class SpellScreen extends AbstractContainerScreen<SpellMenu> {

    // === 资源常量 ===
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicModifier.MODID, "textures/gui/spell_inscription.png");

    // ==========================================
    // === 1. UI 布局配置 (修改此处可调整位置) ===
    // ==========================================

    // 合成区标题
    private static final int SYNTH_LABEL_X = 159;
    private static final int SYNTH_LABEL_Y = 137-12;

    // 合成输入槽 (Input 1, Input 2)
    private static final int SYNTH_IN_X_1 = 160;
    private static final int SYNTH_IN_X_2 = 180;
    private static final int SYNTH_IN_Y_1 = 137;
    private static final int SYNTH_IN_Y_2 = 137;

    // 合成输出槽 (Output 1 - 产物, Output 2 - 装饰/副产物)
    private static final int SYNTH_OUT_X_1 = 217;
    private static final int SYNTH_OUT_X_2 = 237;
    private static final int SYNTH_OUT_Y_1 = 137;
    private static final int SYNTH_OUT_Y_2 = 137;

    // 合成按钮
    private static final int SYNTH_BTN_X = 201;
    private static final int SYNTH_BTN_Y = 139;
    private static final int SYNTH_BTN_W = 14;
    private static final int SYNTH_BTN_H = 14;

    // 左侧列表布局
    private static final int GRID_X = 26;
    private static final int GRID_Y = 12;
    private static final int COL_GAP = 20;
    private static final int ROW_GAP = 24;
    private static final int COLS = 4;
    private static final int MAX_Y = 148; // 列表底部Y坐标限制
    private static final int TITLE_HEIGHT = 11; // 分类标题高度
    private static final int CATEGORY_PADDING = 3; // 分类间距

    // 滚动条
    private static final int SCROLL_X = 123;
    private static final int SCROLL_Y = 32;
    private static final int SCROLL_W = 12;
    private static final int SCROLL_H = 95;
    private static final int SCROLL_COLOR_LIGHT = 0xFFA57855;

    // 右侧法术书区域
    private static final int BOOK_BOX_X = 160;
    private static final int BOOK_BOX_Y = 12;
    private static final int BOOK_BOX_WIDTH = 96;
    private static final int BOOK_BOX_HEIGHT = 80;

    // 统一槽位大小与纹理偏移
    private static final int SLOT_SIZE = 19;
    private static final int SYNTH_SLOT_SIZE = 19;
    private static final int SLOT_TEXTURE_V = 178; // 槽位背景在纹理图的Y坐标
    private static final int SLOT_OFFSET_NORMAL = 0;
    private static final int SLOT_OFFSET_HOVER = 19;
    private static final int SLOT_OFFSET_DRAG = 38;

    // ==========================================
    // === 2. 运行时逻辑变量 ===
    // ==========================================

    // 合成槽数据：0=输入1, 1=输入2, 2=输出1(产物), 3=输出2(保留)
    private final SpellData[] synthSlots = {SpellData.EMPTY, SpellData.EMPTY, SpellData.EMPTY, SpellData.EMPTY};

    // 记录合成槽内的物品来源 (对应玩家背包 Inventory 的 slot index)
    // -1 代表来源于法术书、已消耗或为空
    private int[] synthSourceIndices = {-1, -1};
    // 标记来源是否为法术书 (true=书, false=背包卷轴)
    private boolean[] synthSourceIsBook = {false, false};

    // 标记：是否已点击合成但尚未取走产物 (用于视觉欺骗，隐藏背包里的新物品)
    private boolean isCraftResultPending = false;

    // 拖拽逻辑：合成槽
    private boolean isDraggingSynth = false;
    private int draggedSynthSlotIndex = -1;

    // 拖拽逻辑：左侧列表
    private boolean isDragging = false;
    private int draggedScrollSlotIndex = -1;
    private ItemStack draggedStack = ItemStack.EMPTY;

    // 拖拽逻辑：右侧法术书
    private boolean isDraggingBook = false;
    private int draggedBookSlotIndex = -1;
    private SpellData draggedBookSpellData = SpellData.EMPTY;

    // 列表动画 (消耗时的闪烁/暂存逻辑)
    private boolean pendingConsume = false;
    private int pendingSlotIndex = -1;
    private int pendingTicks = 0;

    // 列表缓存与滚动
    private final Map<SchoolType, List<SpellListEntry>> groupedItemIndices = new LinkedHashMap<>();
    private int cachedContentHeight = 0;
    private float scrollOffs = 0.0f;
    private boolean isScrolling = false;

    // === 内部类：列表条目 ===
    private static class SpellListEntry {
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

    public SpellScreen(SpellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 277;
        this.imageHeight = 177;
        this.inventoryLabelY = -1000; // 隐藏默认文字
        this.titleLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.updateFilteredItems();
    }

    /**
     * 关闭界面时的清理工作
     */
    @Override
    public void onClose() {
        // 关闭时解除视觉屏蔽，因为服务端数据是准的
        isCraftResultPending = false;
        Arrays.fill(synthSlots, SpellData.EMPTY);
        Arrays.fill(synthSourceIndices, -1);
        super.onClose();
    }

    /**
     * 核心逻辑：重新计算左侧列表显示的物品
     * 包含“鼠标拿着”、“合成槽占用”和“产物待领取”的三重扣除逻辑
     */
    private void updateFilteredItems() {
        groupedItemIndices.clear();

        Inventory inv = this.menu.playerInv;
        Map<SchoolType, Map<String, SpellListEntry>> tmp = new LinkedHashMap<>();

        // 准备处理产物屏蔽逻辑
        boolean needToHideResult = isCraftResultPending;
        SpellData pendingResultData = synthSlots[2];

        for (int i = 0; i < inv.items.size(); i++) {
            // 跳过消耗动画中的物品
            if (pendingConsume && i == pendingSlotIndex) continue;

            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof Scroll)) continue;

            ISpellContainer sc = ISpellContainer.get(stack);
            if (sc.isEmpty()) continue;

            SpellData sd = sc.getSpellAtIndex(0);
            if (sd == null || sd == SpellData.EMPTY) continue;

            // === 数量计算核心 ===
            int visibleCount = stack.getCount();

            // 1. 如果鼠标正拖着这个格子 (鼠标也是一种槽)
            if (isDragging && draggedScrollSlotIndex == i) {
                visibleCount--;
            }

            // 2. 如果这个格子被放入了合成槽 Input 1 且来源不是书
            if (!synthSourceIsBook[0] && synthSourceIndices[0] == i) {
                visibleCount--;
            }

            // 3. 如果这个格子被放入了合成槽 Input 2 且来源不是书
            if (!synthSourceIsBook[1] && synthSourceIndices[1] == i) {
                visibleCount--;
            }

            // 如果扣减后数量 <= 0，则不显示在列表中
            if (visibleCount <= 0) continue;

            // 4. 处理“合成后产物需手动拿走”的视觉效果
            // 如果服务端已经合成了，但我们还没点产物槽，列表里其实已经有了那个产物
            // 我们需要在这里把它“藏”起来，直到玩家点击产物槽
            if (needToHideResult && pendingResultData != SpellData.EMPTY) {
                if (sd.getSpell().equals(pendingResultData.getSpell()) &&
                        sd.getLevel() == pendingResultData.getLevel()) {

                    visibleCount--;
                    needToHideResult = false; // 只隐藏一个

                    if (visibleCount <= 0) continue;
                }
            }

            // === 数据聚合 ===
            AbstractSpell spell = sd.getSpell();
            int rawLevel = sd.getLevel();
            if (rawLevel <= 0) rawLevel = 1;
            final int fixedLevel = rawLevel;

            SchoolType school = spell.getSchoolType();
            String key = spell.getSpellId() + "#" + fixedLevel;

            tmp.computeIfAbsent(school, k -> new LinkedHashMap<>());
            Map<String, SpellListEntry> schoolMap = tmp.get(school);
            SpellListEntry entry = schoolMap.computeIfAbsent(key, k -> new SpellListEntry(spell, fixedLevel));

            // 将计算后的可见数量加入条目
            entry.add(i, visibleCount);
        }

        // === 排序与分组 (修改版 2.0) ===
        // 1. 先将 Map 转换为 List 以便进行自定义排序
        List<Map.Entry<SchoolType, List<SpellListEntry>>> sortedSchools = new ArrayList<>();

        for (Map.Entry<SchoolType, Map<String, SpellListEntry>> e : tmp.entrySet()) {
            List<SpellListEntry> list = new ArrayList<>(e.getValue().values());

            // 组内排序：按等级(高->低)，等级一样按名称
            list.sort((a, b) -> {
                // 优先级1：等级 (倒序，例如 10 级排在 1 级前面)
                int levelCompare = Integer.compare(b.level, a.level);
                if (levelCompare != 0) return levelCompare;

                // 优先级2：名称 (正序，字母表顺序)
                String an = a.spell.getDisplayName(this.minecraft.player).getString();
                String bn = b.spell.getDisplayName(this.minecraft.player).getString();
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
        for (Map.Entry<SchoolType, List<SpellListEntry>> e : sortedSchools) {
            groupedItemIndices.put(e.getKey(), e.getValue());
        }

        this.cachedContentHeight = calculateContentHeight();
    }

    /**
     * 计算列表内容的像素高度，用于滚动条逻辑
     */
    private int calculateContentHeight() {
        int height = 0;
        for (Map.Entry<SchoolType, List<SpellListEntry>> entry : groupedItemIndices.entrySet()) {
            height += TITLE_HEIGHT;
            int rows = (entry.getValue().size() + COLS - 1) / COLS;
            if (rows > 0) height += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
        return height == 0 ? 0 : height;
    }

    /**
     * 主渲染循环
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 处理消耗动画计时
        if (pendingConsume) {
            pendingTicks++;
            if (pendingTicks > 20) {
                pendingConsume = false;
                pendingSlotIndex = -1;
            }
        }

        // 每帧更新列表，确保拖拽时的数量变化实时反馈
        this.updateFilteredItems();

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 只有没在拖拽时才显示 Tooltip
        if (!isDragging && !isDraggingBook && !isDraggingSynth) {
            this.renderItemTooltips(guiGraphics, mouseX, mouseY);
            this.renderBookTooltips(guiGraphics, mouseX, mouseY);
            this.renderSynthTooltips(guiGraphics, mouseX, mouseY);
        }

        // 渲染浮动图标（鼠标拿着的东西）
        if (isDragging && !draggedStack.isEmpty()) {
            drawFloatingIcon(guiGraphics, mouseX, mouseY, ISpellContainer.get(draggedStack).getSpellAtIndex(0));
        }
        if (isDraggingBook && draggedBookSpellData != SpellData.EMPTY) {
            drawFloatingIcon(guiGraphics, mouseX, mouseY, draggedBookSpellData);
        }
        if (isDraggingSynth && draggedSynthSlotIndex != -1) {
            // 注意：拖拽合成槽时，槽内原图标不渲染（在 drawSynthSlotContent 处理），只渲染浮动图标
            drawFloatingIcon(guiGraphics, mouseX, mouseY, synthSlots[draggedSynthSlotIndex]);
        }
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

    /**
     * 渲染背景图层及各个静态组件
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight, 512, 512);

        renderBookSlots(guiGraphics, left, top, mouseX, mouseY);
        renderScrollableList(guiGraphics, left, top, mouseX, mouseY);
        renderSynthesisUI(guiGraphics, left, top, mouseX, mouseY);
    }

    /**
     * 渲染右侧法术书槽位
     */
    private void renderBookSlots(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) {
            Component msg = Component.literal("未携带魔法书！").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
            guiGraphics.drawCenteredString(this.font, msg, left + BOOK_BOX_X + BOOK_BOX_WIDTH / 2, top + BOOK_BOX_Y + 30, 0xFFFFFFFF);
            return;
        }

        Component titleMsg = Component.literal("魔法书:").withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK);
        guiGraphics.drawString(this.font, titleMsg, left + BOOK_BOX_X, top + BOOK_BOX_Y, 0xFF000000, false);

        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            int slotX = (int) pos.x;
            int slotY = (int) pos.y;
            boolean isHovered = isHovering(slotX, slotY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);

            int uOffset = SLOT_OFFSET_NORMAL;
            if ((isDragging || isDraggingSynth) && isHovered) uOffset = SLOT_OFFSET_DRAG; // 可放置高亮
            else if (isHovered) uOffset = SLOT_OFFSET_HOVER;

            guiGraphics.blit(TEXTURE, slotX, slotY, uOffset, SLOT_TEXTURE_V, SLOT_SIZE, SLOT_SIZE, 512, 512);

            SpellData spellData = bookContainer.getSpellAtIndex(i);
            // 如果这格正在被拖拽，就不画原来的图标
            if (isDraggingBook && i == draggedBookSlotIndex) spellData = SpellData.EMPTY;

            if (spellData == null || spellData == SpellData.EMPTY) continue;

            AbstractSpell spell = spellData.getSpell();
            guiGraphics.blit(spell.getSpellIconResource(), slotX + 1, slotY + 1, 0, 0, 16, 16, 16, 16);
            drawLevelBadge(guiGraphics, slotX, slotY, spellData.getLevel());
        }
    }

    /**
     * 鼠标点击事件分发
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        if (button == 0) { // 左键
            // 1. 检查滚动条
            if (checkScrollbarClick(mouseX, mouseY, left, top)) return true;

            // 2. 检查合成按钮
            if (isHovering(left + SYNTH_BTN_X, top + SYNTH_BTN_Y, SYNTH_BTN_W, SYNTH_BTN_H, (int)mouseX, (int)mouseY)) {
                if (checkRecipe()) {
                    tryCraft();
                    return true;
                }
            }

            // 3. 尝试开始拖拽
            if (!isDragging && !isDraggingBook && !isDraggingSynth) {
                if (tryStartDragSynth((int)mouseX, (int)mouseY, left, top)) return true;
                if (tryStartDragBookSpell((int) mouseX, (int) mouseY, left, top)) return true;
                if (checkListClick((int)mouseX, (int)mouseY)) return true;
            }

        } else if (button == 1) { // 右键
            checkSynthRightClick((int)mouseX, (int)mouseY, left, top);
            checkBookRightClick((int)mouseX, (int)mouseY);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 检查合成区右键：回收物品或领取产物
     */
    private void checkSynthRightClick(int mouseX, int mouseY, int left, int top) {
        // 1. 处理输入槽 (右键回收)
        for (int i = 0; i < 2; i++) {
            int x = (i == 0) ? (left + SYNTH_IN_X_1) : (left + SYNTH_IN_X_2);
            int y = (i == 0) ? (top + SYNTH_IN_Y_1) : (top + SYNTH_IN_Y_2);

            if (isHovering(x, y, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY)) {
                if (synthSlots[i] != SpellData.EMPTY) {
                    synthSlots[i] = SpellData.EMPTY;
                    // 【核心】：重置来源为 -1，updateFilteredItems 会自动让其在左侧列表重新显示
                    synthSourceIndices[i] = -1;
                    synthSourceIsBook[i] = false;
                    this.updateFilteredItems(); // 立即刷新
                }
                return;
            }
        }

        // 2. 处理产物槽 (右键直接收货)
        int outX = left + SYNTH_OUT_X_1;
        int outY = top + SYNTH_OUT_Y_1;
        if (isHovering(outX, outY, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY)) {
            if (isCraftResultPending) {
                isCraftResultPending = false;
                synthSlots[2] = SpellData.EMPTY;
                this.updateFilteredItems();
            }
        }
    }

    /**
     * 鼠标释放：处理物品放置
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (this.width - this.imageWidth) / 2;
            int top = (this.height - this.imageHeight) / 2;

            // 情况1: 从产物槽拖出的物品
            if (isDraggingSynth && draggedSynthSlotIndex == 2 && isCraftResultPending) {
                if (!tryHandleResultToBookDrop(mouseX, mouseY, left, top)) {
                    // 如果没拖进书里，就算放进背包了
                }
                // 结算完成，清空产物槽，解除屏蔽
                isCraftResultPending = false;
                synthSlots[2] = SpellData.EMPTY;
                isDraggingSynth = false;
                draggedSynthSlotIndex = -1;
                this.updateFilteredItems();
                return true;
            }

            // 情况2: 尝试放入合成槽 (输入槽)
            if (handleDropToSynth((int)mouseX, (int)mouseY, left, top)) {
                return true;
            }

            // 情况3: 法术书拖拽结束
            if (isDraggingBook) {
                handleBookSpellDrop((int) mouseX, (int) mouseY);
                isDraggingBook = false; draggedBookSlotIndex = -1; draggedBookSpellData = SpellData.EMPTY;
                return true;
            }

            // 情况4: 列表卷轴拖拽结束
            if (isDragging) {
                handleListDrop((int) mouseX, (int) mouseY);
                isDragging = false; draggedStack = ItemStack.EMPTY; draggedScrollSlotIndex = -1;
                // 放下（如果没放进任何槽位）后刷新列表，物品回归
                this.updateFilteredItems();
            }

            // 情况5: 合成槽内部拖动结束
            if (isDraggingSynth) {
                isDraggingSynth = false;
                draggedSynthSlotIndex = -1;
            }

            this.isScrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 处理放入合成槽的逻辑
     * 这里需要仔细记录物品是从哪来的 (inv slot index)，以便左侧列表能正确扣除
     */
    private boolean handleDropToSynth(int mouseX, int mouseY, int left, int top) {
        for (int i = 0; i < 2; i++) {
            int x = (i == 0) ? (left + SYNTH_IN_X_1) : (left + SYNTH_IN_X_2);
            int y = (i == 0) ? (top + SYNTH_IN_Y_1) : (top + SYNTH_IN_Y_2);

            if (isHovering(x, y, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY)) {

                SpellData dataToDrop = SpellData.EMPTY;
                int sourceIdx = -1;
                boolean isBook = false;

                // 确定来源
                if (isDraggingBook) {
                    dataToDrop = draggedBookSpellData;
                    sourceIdx = draggedBookSlotIndex;
                    isBook = true;
                } else if (isDragging) {
                    ISpellContainer sc = ISpellContainer.get(draggedStack);
                    if (!sc.isEmpty()) dataToDrop = sc.getSpellAtIndex(0);
                    // 【核心】：记录来源背包 Slot
                    sourceIdx = draggedScrollSlotIndex;
                    isBook = false;
                } else if (isDraggingSynth) {
                    // 从另一个合成槽拖过来
                    dataToDrop = synthSlots[draggedSynthSlotIndex];
                    sourceIdx = synthSourceIndices[draggedSynthSlotIndex];
                    isBook = synthSourceIsBook[draggedSynthSlotIndex];

                    // 移动后清除旧位置
                    synthSlots[draggedSynthSlotIndex] = SpellData.EMPTY;
                    synthSourceIndices[draggedSynthSlotIndex] = -1;
                    synthSourceIsBook[draggedSynthSlotIndex] = false;
                }

                if (dataToDrop != SpellData.EMPTY) {
                    // 填入新位置
                    synthSlots[i] = dataToDrop;
                    synthSourceIndices[i] = sourceIdx;
                    synthSourceIsBook[i] = isBook;

                    // 后续清理
                    if (isDraggingBook) {
                        // 书里的东西拿出来就没了
                        ModMessage.sendToServer(new PacketExtractSpell(draggedBookSlotIndex));
                        isDraggingBook = false; draggedBookSlotIndex = -1; draggedBookSpellData = SpellData.EMPTY;
                    } else if (isDragging) {
                        // 列表的东西放入后，重置拖拽状态，updateFilteredItems 会根据 synthSourceIndices 自动扣除
                        isDragging = false; draggedStack = ItemStack.EMPTY; draggedScrollSlotIndex = -1;
                        this.updateFilteredItems();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 左侧列表开始拖拽检测
     */
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
                    this.isDragging = true;
                    this.draggedScrollSlotIndex = se.pickOneSlot();
                    this.draggedStack = this.menu.playerInv.getItem(this.draggedScrollSlotIndex);
                    // 拖拽开始，鼠标变成临时槽位，updateFilteredItems 会自动减 1
                    this.updateFilteredItems();
                    return true;
                }
            }
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
        return false;
    }

    /**
     * 渲染合成 UI
     */
    private void renderSynthesisUI(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        Component titleMsg = Component.literal("魔法合成:").withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK);
        guiGraphics.drawString(this.font, titleMsg, left + SYNTH_LABEL_X, top + SYNTH_LABEL_Y, 0xFF000000, false);

        // 渲染输入槽 (0, 1)
        for (int i = 0; i < 2; i++) {
            int x = (i == 0) ? (left + SYNTH_IN_X_1) : (left + SYNTH_IN_X_2);
            int y = (i == 0) ? (top + SYNTH_IN_Y_1) : (top + SYNTH_IN_Y_2);

            boolean isHovered = isHovering(x, y, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY);
            int u = 0;
            if ((isDragging || isDraggingBook || isDraggingSynth) && isHovered) u = SLOT_OFFSET_DRAG;
            else if (isHovered) u = SLOT_OFFSET_HOVER;

            guiGraphics.blit(TEXTURE, x, y, u, SLOT_TEXTURE_V, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, 512, 512);
            drawSynthSlotContent(guiGraphics, i, x, y);
        }

        // 渲染合成按钮
        boolean canCraft = checkRecipe();
        int btnX = left + SYNTH_BTN_X;
        int btnY = top + SYNTH_BTN_Y;
        boolean btnHover = isHovering(btnX, btnY, SYNTH_BTN_W, SYNTH_BTN_H, mouseX, mouseY);
        int btnU = 0;
        if (canCraft) btnU = btnHover ? 28 : 14;
        guiGraphics.blit(TEXTURE, btnX, btnY, btnU, 211, SYNTH_BTN_W, SYNTH_BTN_H, 512, 512);

        // 渲染输出槽 (2, 3)
        for (int i = 0; i < 2; i++) {
            int x = (i == 0) ? (left + SYNTH_OUT_X_1) : (left + SYNTH_OUT_X_2);
            int y = (i == 0) ? (top + SYNTH_OUT_Y_1) : (top + SYNTH_OUT_Y_2);

            boolean isHovered = isHovering(x, y, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY);
            int u = isHovered ? SLOT_OFFSET_HOVER : SLOT_OFFSET_NORMAL;
            guiGraphics.blit(TEXTURE, x, y, u, SLOT_TEXTURE_V, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, 512, 512);
            drawSynthSlotContent(guiGraphics, 2 + i, x, y);
        }
    }

    /**
     * 绘制合成槽内容 (如果正在拖拽该槽位，则不绘制内容)
     */
    private void drawSynthSlotContent(GuiGraphics guiGraphics, int slotIdx, int x, int y) {
        SpellData sd = synthSlots[slotIdx];
        if (isDraggingSynth && draggedSynthSlotIndex == slotIdx) return;
        if (sd != null && sd != SpellData.EMPTY) {
            AbstractSpell spell = sd.getSpell();
            guiGraphics.blit(spell.getSpellIconResource(), x + 2, y + 1, 0, 0, 16, 16, 16, 16);
            drawLevelBadge(guiGraphics, x, y, sd.getLevel());
        }
    }

    /**
     * 检查配方是否有效 (同种类、同等级)
     */
    private boolean checkRecipe() {
        SpellData in1 = synthSlots[0];
        SpellData in2 = synthSlots[1];
        if (in1 == null || in1.equals(SpellData.EMPTY) || in2 == null || in2.equals(SpellData.EMPTY)) return false;
        boolean sameId = in1.getSpell().getSpellId().equals(in2.getSpell().getSpellId());
        boolean sameLevel = in1.getLevel() == in2.getLevel();
        return sameId && sameLevel;
    }

    /**
     * 执行合成逻辑
     */
    private void tryCraft() {
        if (!checkRecipe()) return;
        if (isCraftResultPending) return; // 必须取走上一次的产物

        int idx1 = synthSourceIndices[0];
        boolean book1 = synthSourceIsBook[0];
        int idx2 = synthSourceIndices[1];
        boolean book2 = synthSourceIsBook[1];

        // 发送网络包
        ModMessage.sendToServer(new PacketPerformSynthesis(idx1, book1, idx2, book2));

        // 客户端视觉更新
        SpellData in1 = synthSlots[0];
        SpellData resultData = new SpellData(in1.getSpell(), in1.getLevel() + 1);

        synthSlots[0] = SpellData.EMPTY;
        synthSlots[1] = SpellData.EMPTY;
        synthSlots[2] = resultData; // 放入产物槽

        // 标记：这东西还没真正拿走
        isCraftResultPending = true;

        // 材料已消耗，解除对列表的占用锁定
        Arrays.fill(synthSourceIndices, -1);
        Arrays.fill(synthSourceIsBook, false);
        this.updateFilteredItems();
    }

    // ==========================================
    // === 辅助方法区域 (保持原有逻辑优化) ===
    // ==========================================

    private boolean checkScrollbarClick(double mouseX, double mouseY, int left, int top) {
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
        return false;
    }

    private void updateScrollPos(double mouseY, int top) {
        double barStart = top + SCROLL_Y;
        this.scrollOffs = (float) ((mouseY - barStart) / SCROLL_H);
        this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
    }

    // 列表卷轴拖放进书
    private void handleListDrop(int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null) return;
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE && mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {
                ModMessage.sendToServer(new PacketInscribeSpell(draggedScrollSlotIndex, i));
                pendingConsume = true;
                pendingSlotIndex = draggedScrollSlotIndex;
                pendingTicks = 0;
                return;
            }
        }
    }

    // 产物拖放进书
    private boolean tryHandleResultToBookDrop(double mouseX, double mouseY, int left, int top) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null) return false;
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();

        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE && mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {
                // 需要在背包里反向查找那个被隐藏的产物
                SpellData resultData = synthSlots[2];
                Inventory inv = this.menu.playerInv;
                int foundSlot = -1;
                for(int j=0; j<inv.items.size(); j++) {
                    ItemStack s = inv.items.get(j);
                    if(s.getItem() instanceof Scroll) {
                        SpellData sd = ISpellContainer.get(s).getSpellAtIndex(0);
                        if(sd.getSpell().equals(resultData.getSpell()) && sd.getLevel() == resultData.getLevel()) {
                            foundSlot = j;
                            break;
                        }
                    }
                }
                if (foundSlot != -1) {
                    ModMessage.sendToServer(new PacketInscribeSpell(foundSlot, i));
                    return true;
                }
            }
        }
        return false;
    }

    private void renderScrollableList(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        int visibleHeight = MAX_Y - GRID_Y;
        boolean canScroll = this.cachedContentHeight > visibleHeight;

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

    // === 通用渲染工具 ===

    private void renderSynthTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        for (int i = 0; i < 4; i++) {
            int x = (i < 2) ? ((i==0)?left+SYNTH_IN_X_1 : left+SYNTH_IN_X_2) : ((i==2)?left+SYNTH_OUT_X_1 : left+SYNTH_OUT_X_2);
            int y = (i < 2) ? ((i==0)?top+SYNTH_IN_Y_1 : top+SYNTH_IN_Y_2) : ((i==2)?top+SYNTH_OUT_Y_1 : top+SYNTH_OUT_Y_2);
            if (isHovering(x, y, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY)) {
                SpellData sd = synthSlots[i];
                if (sd != null && sd != SpellData.EMPTY && !(isDraggingSynth && draggedSynthSlotIndex == i)) {
                    guiGraphics.renderTooltip(this.font, getTooltipLines(sd), Optional.empty(), mouseX, mouseY);
                }
                return;
            }
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
            if (isHovering((int)pos.x, (int)pos.y, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                if (!(isDragging || isDraggingBook)) {
                    SpellData spellData = bookContainer.getSpellAtIndex(i);
                    if (spellData != null && spellData != SpellData.EMPTY) {
                        guiGraphics.renderTooltip(this.font, getTooltipLines(spellData), Optional.empty(), mouseX, mouseY);
                    }
                }
                return;
            }
        }
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
                        tooltip.add(1, Component.literal(" 数量: " + se.totalCount).withStyle(ChatFormatting.GRAY));
                        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                    }
                }
            }
            int rows = (entries.size() + COLS - 1) / COLS;
            if (rows > 0) currentY += (rows * ROW_GAP) - (ROW_GAP - 16) + CATEGORY_PADDING;
        }
    }

    private List<Component> getTooltipLines(SpellData spellData) {
        List<Component> lines = new ArrayList<>();
        if (spellData == null || SpellData.EMPTY.equals(spellData)) return lines;
        AbstractSpell spell = spellData.getSpell();
        int level = spellData.getLevel();
        var player = this.minecraft.player;
        SpellRarity rarity = spell.getRarity(level);
        lines.add(spell.getDisplayName(player).withStyle(rarity.getDisplayName().getStyle()));
        lines.add(Component.translatable("ui.irons_spellbooks.level", level).withStyle(rarity.getDisplayName().getStyle()));
        List<MutableComponent> uniqueInfo = spell.getUniqueInfo(level, player);
        if (!uniqueInfo.isEmpty()) lines.addAll(uniqueInfo);
        return lines;
    }

    private Vec2 getBookSlotPosition(int slotIndex, int totalSpells, int guiLeft, int guiTop) {
        int boxSize = SLOT_SIZE;
        int[] rowCounts = ClientRenderCache.getRowCounts(totalSpells);
        int rowIndex = 0; int colIndex = slotIndex;
        for (int r = 0; r < rowCounts.length; r++) {
            if (colIndex < rowCounts[r]) { rowIndex = r; break; }
            colIndex -= rowCounts[r];
        }
        int centerX = guiLeft + BOOK_BOX_X + BOOK_BOX_WIDTH / 2;
        int centerY = guiTop + BOOK_BOX_Y + BOOK_BOX_HEIGHT / 2;
        int totalHeight = rowCounts.length * boxSize;
        int currentRowWidth = rowCounts[rowIndex] * boxSize;
        int x = centerX - (currentRowWidth / 2) + (colIndex * boxSize);
        int y = centerY - (totalHeight / 2) + (rowIndex * boxSize);
        return new Vec2(x, y);
    }

    private void drawLevelBadge(GuiGraphics g, int itemX, int itemY, int level) {
        int w = 9; int h = 8;
        int x0 = itemX + 16 - w; int y0 = itemY;
        g.fill(x0, y0, x0 + w, y0 + h, 0xFF000000);
        int color = (level >= 10) ? 0xFFFFD700 : 0xFF00FF00;
        String txt = String.valueOf(Math.min(level, 10));
        g.drawString(this.font, txt, x0 + w - this.font.width(txt) + 1, y0, color, false);
    }

    private boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // 滚动与右侧法术书交互辅助
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleHeight = MAX_Y - GRID_Y;
        if (this.cachedContentHeight > visibleHeight) {
            float scrollStep = (float) ROW_GAP / (this.cachedContentHeight - visibleHeight);
            this.scrollOffs = Mth.clamp(this.scrollOffs - (float)delta * scrollStep, 0.0F, 1.0F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling && button == 0) {
            int top = (this.height - this.imageHeight) / 2;
            updateScrollPos(mouseY, top);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    private void checkBookRightClick(int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null) return;
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE && mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {
                if (bookContainer.getSpellAtIndex(i) != SpellData.EMPTY) ModMessage.sendToServer(new PacketExtractSpell(i));
                return;
            }
        }
    }
    private boolean tryStartDragSynth(int mouseX, int mouseY, int left, int top) {
        for (int i = 0; i < 4; i++) {
            int x = (i < 2) ? ((i==0)?left+SYNTH_IN_X_1 : left+SYNTH_IN_X_2) : ((i==2)?left+SYNTH_OUT_X_1 : left+SYNTH_OUT_X_2);
            int y = (i < 2) ? ((i==0)?top+SYNTH_IN_Y_1 : top+SYNTH_IN_Y_2) : ((i==2)?top+SYNTH_OUT_Y_1 : top+SYNTH_OUT_Y_2);
            if (isHovering(x, y, SYNTH_SLOT_SIZE, SYNTH_SLOT_SIZE, mouseX, mouseY)) {
                if (synthSlots[i] != SpellData.EMPTY) {
                    isDraggingSynth = true;
                    draggedSynthSlotIndex = i;
                    return true;
                }
            }
        }
        return false;
    }
    private boolean tryStartDragBookSpell(int mouseX, int mouseY, int left, int top) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return false;
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();
        for (int i = 0; i < maxSpells; i++) {
            Vec2 pos = getBookSlotPosition(i, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE && mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {
                SpellData data = bookContainer.getSpellAtIndex(i);
                if (data != null && data != SpellData.EMPTY) {
                    isDraggingBook = true;
                    draggedBookSlotIndex = i;
                    draggedBookSpellData = data;
                    return true;
                } return false;
            }
        } return false;
    }
    private void handleBookSpellDrop(int mouseX, int mouseY) {
        ItemStack bookStack = Utils.getPlayerSpellbookStack(this.minecraft.player);
        if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;
        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
        int maxSpells = bookContainer.getMaxSpellCount();
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int extractThresholdX = left + 137;
        if (mouseX < extractThresholdX) {
            ModMessage.sendToServer(new PacketExtractSpell(draggedBookSlotIndex));
            return;
        }
        for (int to = 0; to < maxSpells; to++) {
            Vec2 pos = getBookSlotPosition(to, maxSpells, left, top);
            if (mouseX >= pos.x && mouseX < pos.x + SLOT_SIZE && mouseY >= pos.y && mouseY < pos.y + SLOT_SIZE) {
                if (to == draggedBookSlotIndex) return;
                ModMessage.sendToServer(new PacketSwapBookSpell(draggedBookSlotIndex, to));
                return;
            }
        }
    }
}

