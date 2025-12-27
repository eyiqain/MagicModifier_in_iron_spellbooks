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

/**
 * 统一交换包：服务端权威处理任意两个槽位之间的交换
 */
public class PacketUnifiedSwap {

    // === 地址类型定义 ===
    public static final byte TYPE_BOOK   = 0; // 法术书内的槽位
    public static final byte TYPE_MOUSE  = 1; // 鼠标上的物品 (Index通常为0)
    public static final byte TYPE_PLAYER = 2; // 玩家背包 (Inventory)
    public static final byte TYPE_SYNTH  = 3; // 合成台槽位 (0, 1, 2)

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

            // 1. 提取两边的物品（统一转为 ItemStack 处理）
            //    注意：如果是书本，这里会生成临时的卷轴 Stack
            ItemStack stackFrom = getStackAt(player, menu, msg.fromType, msg.fromIndex);
            ItemStack stackTo   = getStackAt(player, menu, msg.toType, msg.toIndex);

            // 2. 检查交换是否合法（预留接口）
            if (!canSwap(msg.fromType, stackFrom, msg.toType, stackTo)) {
                // 如果不能交换，可以在这里发一个 PacketPlaySound 拒绝音效，或者直接 return
                // 客户端数据没变，只需要强制同步一次覆盖掉客户端的预测即可
                player.containerMenu.broadcastChanges();
                return;
            }

            // 3. 执行交换 (写入操作)
            //    尝试把 To 的东西放进 From，把 From 的东西放进 To
            boolean successA = setStackAt(player, menu, msg.fromType, msg.fromIndex, stackTo);
            boolean successB = setStackAt(player, menu, msg.toType, msg.toIndex, stackFrom);

            // 4. 处理半途失败的回滚逻辑 (极少发生，但为了严谨)
            if (!successA || !successB) {
                // 如果一边写成功一边失败，这里可以尝试回滚，或者打日志
                // 简单处理：重新广播，以此覆盖错误状态
            } else {
                // 交换成功额外处理：如果是法术书，需要保存NBT
                saveIfBook(player, msg.fromType);
                saveIfBook(player, msg.toType);
            }

            // 5. 统一同步
            //    broadcastChanges 会覆盖 Mouse, Player, Slot 的状态
            player.containerMenu.broadcastChanges();
            player.getInventory().setChanged();

            // 针对 Synth 槽位，为了保险起见，手动发同步包（复用你之前的逻辑）
            syncSynthIfNeed(player, msg.fromType, msg.fromIndex);
            syncSynthIfNeed(player, msg.toType, msg.toIndex);
        });
        ctx.get().setPacketHandled(true);
    }

    // ================== 核心抽象逻辑 ==================

    /**
     * 从指定位置读取物品。如果是法术书，会自动把 SpellData 包装成卷轴返回。
     */
    private static ItemStack getStackAt(ServerPlayer player, SpellMenu menu, byte type, int index) {
        switch (type) {
            case TYPE_MOUSE:
                return menu.getCarried().copy();

            case TYPE_PLAYER:
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
                            // 🔥 核心：把法术变成卷轴
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

    /**
     * 把物品写入指定位置。如果是法术书，会自动解析卷轴并写入 SpellData。
     * 返回 true 表示写入成功。
     */
    private static boolean setStackAt(ServerPlayer player, SpellMenu menu, byte type, int index, ItemStack stack) {
        // 如果要写入的是空，视为“清除”
        boolean isEmpty = stack.isEmpty();

        switch (type) {
            case TYPE_MOUSE:
                menu.setCarried(stack);
                return true;

            case TYPE_PLAYER:
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
                            // 🔥 核心：把卷轴变成法术
                            ISpellContainer scrollC = ISpellContainer.get(stack);
                            SpellData data = scrollC.getSpellAtIndex(0);
                            // 强制写入 (true)，因为我们是交换，不是新增
                            // 注意：这里先移除旧的，防止 add 失败
                            bookContainer.removeSpellAtIndex(index, bookStack);
                            bookContainer.addSpellAtIndex(data.getSpell(), data.getLevel(), index, true, bookStack);
                            return true;
                        } else {
                            // 尝试把非卷轴放进书里 -> 失败
                            return false;
                        }
                    }
                }
                return false;
        }
        return false;
    }

    /**
     * 交换规则检查（这就是你要的“留出不能交换的接口”）
     */
    private static boolean canSwap(byte typeA, ItemStack stackA, byte typeB, ItemStack stackB) {
        // 规则1：如果目标是书本，那么来源必须是卷轴或者是空的
        if (typeA == TYPE_BOOK && !stackB.isEmpty() && !(stackB.getItem() instanceof Scroll)) return false;
        if (typeB == TYPE_BOOK && !stackA.isEmpty() && !(stackA.getItem() instanceof Scroll)) return false;

        // 规则2：合成槽输出位 (假设 index 2 是输出) 通常不接受放入，除非你是管理员或者特殊逻辑
        // if (typeA == TYPE_SYNTH && indexA == 2 && !stackB.isEmpty()) return false;

        return true;
    }

    private static void saveIfBook(ServerPlayer player, byte type) {
        if (type == TYPE_BOOK) {
            ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
            if (bookStack != null) {
                ISpellContainer.get(bookStack).save(bookStack);
            }
        }
    }

    private static void syncSynthIfNeed(ServerPlayer player, byte type, int index) {
        if (type == TYPE_SYNTH) {
            // 复用你现有的同步包，确保万无一失
            ItemStack item = player.containerMenu instanceof SpellMenu ?
                    ((SpellMenu)player.containerMenu).synthContainer.getItem(index) : ItemStack.EMPTY;
            ModMessage.sendToPlayer(new PacketSyncSynth(index, item), player);
        }
    }
}
