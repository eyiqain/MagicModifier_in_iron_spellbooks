package io.isb.modifier.mixin.block;

import io.isb.modifier.gui.SpellMenu; // 你自己的 Menu
import io.redspace.ironsspellbooks.block.inscription_table.InscriptionTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InscriptionTableBlock.class)
public class InscriptionTableBlockMixin {

    /**
     * 拦截方块的右键点击事件 (use 方法)
     * cancellable = true 允许我们取消原版逻辑
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        // 1. 确保只在主手交互时触发（防止触发两次）
        if (hand == InteractionHand.MAIN_HAND) {

            // 2. 服务端逻辑：打开你的 UI
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {

                NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("gui.magic_modifier.spell_inscription");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                        // 这里创建你之前写好的 SpellMenu
                        return new SpellMenu(windowId, inv, ContainerLevelAccess.create(level, pos));
                    }
                }, pos); // 传入 pos 以便 Menu 能通过检测
            }

            // 3. 返回成功，告诉游戏“如果不取消，点击已处理”
            cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));

            // 4. 这一步最关键：取消原版方法的执行！
            // 这样原版的 InscriptionTableMenu 就永远不会被创建，原版 UI 也不会打开。
            cir.cancel();
        }
    }
}
