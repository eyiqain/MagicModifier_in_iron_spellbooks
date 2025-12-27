package io.isb.modifier.net.ui;

import io.isb.modifier.gui.SpellMenu;
import io.isb.modifier.net.ModMessage;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUnifiedSwap {

    // === 地址类型定义 ===
    public static final byte TYPE_BOOK   = 0;
    public static final byte TYPE_MOUSE  = 1;
    public static final byte TYPE_PLAYER = 2;
    public static final byte TYPE_SYNTH  = 3;

    private final byte fromType;
    private final int fromIndex;
    private final byte toType;
    private final int toIndex;

    public PacketUnifiedSwap(byte fromType, int fromIndex, byte toType, int toIndex) {
        this.fromType = fromType;
        this.fromIndex = fromIndex;
        this.toType = toType;
        this.toIndex = toIndex;
    }

    public static void encode(PacketUnifiedSwap msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.fromType);
        buf.writeInt(msg.fromIndex);
        buf.writeByte(msg.toType);
        buf.writeInt(msg.toIndex);
    }

    public static PacketUnifiedSwap decode(FriendlyByteBuf buf) {
        return new PacketUnifiedSwap(buf.readByte(), buf.readInt(), buf.readByte(), buf.readInt());
    }

    public static void handle(PacketUnifiedSwap msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.containerMenu instanceof SpellMenu)) return;
            SpellMenu menu = (SpellMenu) player.containerMenu;

            // 1. 获取原始堆叠
            ItemStack stackFrom = getStackAt(player, menu, msg.fromType, msg.fromIndex);
            ItemStack stackTo   = getStackAt(player, menu, msg.toType, msg.toIndex);

            // 2. 检查基本合法性 (不再检查数量>1)
            if (!canSwap(msg.fromType, stackFrom, msg.toType, stackTo)) {
                sendResultAndSync(player, false, msg);
                return;
            }

            // === 🔥🔥🔥 核心修改：计算实际要写入的数据 🔥🔥🔥 ===
            ItemStack finalStackForTarget; // 最终要写入目标槽(To)的物品
            ItemStack finalStackForSource; // 最终要写入源槽(From)的物品

            // 场景：如果目标是鼠标，且源物品数量 > 1，且鼠标是空的
            // 行为：执行“拆分”，只拿 1 个走，剩下的留给源
            if (msg.toType == TYPE_MOUSE && stackFrom.getCount() > 1 && stackTo.isEmpty()) {
                // 拿走的 1 个
                finalStackForTarget = stackFrom.copy();
                finalStackForTarget.setCount(1);

                // 留下的 (N - 1) 个
                finalStackForSource = stackFrom.copy();
                finalStackForSource.shrink(1);
            }
            else {
                // 标准交换：A 去 B，B 去 A
                finalStackForTarget = stackFrom;
                finalStackForSource = stackTo;
            }

            // 3. 执行写入
            boolean successTo   = setStackAt(player, menu, msg.toType, msg.toIndex, finalStackForTarget);
            boolean successFrom = setStackAt(player, menu, msg.fromType, msg.fromIndex, finalStackForSource);

            if (!successTo || !successFrom) {
                // 失败回滚
                sendResultAndSync(player, false, msg);
            } else {
                // 成功
                saveIfBook(player, msg.fromType);
                saveIfBook(player, msg.toType);
                sendResultAndSync(player, true, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ================== 规则检查 ==================

    private static boolean canSwap(byte typeA, ItemStack stackA, byte typeB, ItemStack stackB) {
        // 规则 1：法术书互斥逻辑
        if (typeA == TYPE_BOOK && !stackB.isEmpty() && !(stackB.getItem() instanceof Scroll)) return false;
        if (typeB == TYPE_BOOK && !stackA.isEmpty() && !(stackA.getItem() instanceof Scroll)) return false;

        // 🔥🔥🔥 删除：之前的数量限制已移除，现在允许拿取堆叠物品（通过上面的 handle 拆分逻辑处理）

        return true;
    }

    // ================== 辅助方法 (保持不变) ==================

    private static void sendResultAndSync(ServerPlayer player, boolean success, PacketUnifiedSwap msg) {
        // ModMessage.sendToPlayer(new PacketSwapResult(success), player); // 音效包
        player.containerMenu.broadcastChanges();
        player.getInventory().setChanged();
        syncSynthIfNeed(player, msg.fromType, msg.fromIndex);
        syncSynthIfNeed(player, msg.toType, msg.toIndex);
    }

    private static void syncSynthIfNeed(ServerPlayer player, byte type, int index) {
        if (type == TYPE_SYNTH) {
            ItemStack item = player.containerMenu instanceof SpellMenu ?
                    ((SpellMenu)player.containerMenu).synthContainer.getItem(index) : ItemStack.EMPTY;
            ModMessage.sendToPlayer(new PacketSyncSynth(index, item), player);
        }
    }

    private static void saveIfBook(ServerPlayer player, byte type) {
        if (type == TYPE_BOOK) {
            ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
            if (bookStack != null) {
                ISpellContainer.get(bookStack).save(bookStack);
            }
        }
    }

    private static ItemStack getStackAt(ServerPlayer player, SpellMenu menu, byte type, int index) {
        switch (type) {
            case TYPE_MOUSE:
                return menu.getCarried().copy();
            case TYPE_PLAYER:
                if (index == -1) return ItemStack.EMPTY;
                if (index >= 0 && index < player.getInventory().items.size()) {
                    return player.getInventory().getItem(index).copy();
                }
                break;
            case TYPE_SYNTH:
                SimpleContainer synth = menu.synthContainer;
                if (index >= 0 && index < synth.getContainerSize()) {
                    return synth.getItem(index).copy();
                }
                break;
            case TYPE_BOOK:
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                if (bookStack != null && bookStack.getItem() instanceof SpellBook) {
                    ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                    if (index >= 0 && index < bookContainer.getMaxSpellCount()) {
                        SpellData data = bookContainer.getSpellAtIndex(index);
                        if (data != SpellData.EMPTY) {
                            ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                            SpellContainer scrollContainer = new SpellContainer(1, false, false);
                            scrollContainer.addSpellAtIndex(data.getSpell(), data.getLevel(), 0, true, scroll);
                            return scroll;
                        }
                    }
                }
                break;
        }
        return ItemStack.EMPTY;
    }

    private static boolean setStackAt(ServerPlayer player, SpellMenu menu, byte type, int index, ItemStack stack) {
        boolean isEmpty = stack.isEmpty();
        switch (type) {
            case TYPE_MOUSE:
                menu.setCarried(stack);
                return true;
            case TYPE_PLAYER:
                if (index == -1) {
                    if (isEmpty) return true;
                    return player.getInventory().add(stack);
                }
                if (index >= 0 && index < player.getInventory().items.size()) {
                    player.getInventory().setItem(index, stack);
                    return true;
                }
                return false;
            case TYPE_SYNTH:
                SimpleContainer synth = menu.synthContainer;
                if (index >= 0 && index < synth.getContainerSize()) {
                    synth.setItem(index, stack);
                    return true;
                }
                return false;
            case TYPE_BOOK:
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                if (bookStack != null && bookStack.getItem() instanceof SpellBook) {
                    ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                    if (index >= 0 && index < bookContainer.getMaxSpellCount()) {
                        if (isEmpty) {
                            bookContainer.removeSpellAtIndex(index, bookStack);
                            return true;
                        } else if (stack.getItem() instanceof Scroll) {
                            ISpellContainer scrollC = ISpellContainer.get(stack);
                            SpellData data = scrollC.getSpellAtIndex(0);
                            bookContainer.removeSpellAtIndex(index, bookStack);
                            bookContainer.addSpellAtIndex(data.getSpell(), data.getLevel(), index, true, bookStack);
                            return true;
                        }
                    }
                }
                return false;
        }
        return false;
    }
}
