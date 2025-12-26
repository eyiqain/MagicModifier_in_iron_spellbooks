package io.isb.modifier.gui;

import io.isb.modifier.init.MenuRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class SpellMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    // 需要公开给 Screen 访问，用于读取玩家背包物品
    public final Inventory playerInv;

    // 🔥 新增：定义一个3格的内部容器 (0,1=输入, 2=输出)
    public final SimpleContainer synthContainer = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            // 这里可以添加逻辑：当内容改变时，通知客户端同步 (可选，不做也行，依靠发包回执)
        }
    };
    // 🔥 必须重写：关闭界面时，把合成槽里的东西退给玩家，否则就吞了！
    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.synthContainer);
    }
    // 客户端构造器
    public SpellMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, ContainerLevelAccess.NULL);
    }
    // 服务端/通用构造器
    public SpellMenu(int containerId, Inventory playerInv, ContainerLevelAccess access) {
        super(MenuRegistry.SPELL_MENU.get(), containerId);
        this.access = access;
        this.playerInv = playerInv;
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
