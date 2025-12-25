package io.isb.modifier.gui;

import io.isb.modifier.init.MenuRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class SpellMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    // 需要公开给 Screen 访问，用于读取玩家背包物品
    public final Inventory playerInv;
    // 客户端构造器
    public SpellMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, ContainerLevelAccess.NULL);
    }

    // 服务端/通用构造器
    public SpellMenu(int containerId, Inventory playerInv, ContainerLevelAccess access) {
        super(MenuRegistry.SPELL_MENU.get(), containerId);
        this.access = access;
        this.playerInv = playerInv;

        // === 关键 ===
        // 我们不添加任何 addSlot()。
        // 这意味着这是一个“无槽位”界面，所有的交互（点击、拖拽）都必须在 Client 端
        // 通过 SpellScreen 手动计算坐标并发送网络包来实现。
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // 允许打开
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 因为没有 Slot，Shift+点击无效，直接返回空
        return ItemStack.EMPTY;
    }
}
