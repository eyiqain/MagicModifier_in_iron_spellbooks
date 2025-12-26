package io.isb.modifier.api.modifier.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.isb.modifier.MagicModifier;
import io.isb.modifier.api.modifier.AbstractModifier;
import io.isb.modifier.api.util.RuneContainerHelper;
import io.isb.modifier.init.ModifierRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicModifier.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rune")
                .requires(source -> source.hasPermission(2))

                // apply
                .then(Commands.literal("apply")
                        .then(Commands.argument("modifier", ResourceLocationArgument.id())
                                .suggests(MODIFIER_SUGGESTIONS)
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, RuneContainerHelper.MAX_SLOTS - 1))
                                        .executes(ModCommands::applyModifier)
                                )
                        )
                )

                // clear
                .then(Commands.literal("clear")
                        // clear <slot>
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, RuneContainerHelper.MAX_SLOTS - 1))
                                .executes(ModCommands::clearSlot)
                        )
                        // clear all
                        .then(Commands.literal("all")
                                .executes(ModCommands::clearAll)
                        )
                )
        );
    }

    private static int applyModifier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        if (stack.isEmpty()) {
            context.getSource().sendFailure(Component.literal("你必须手持一个物品！"));
            return 0;
        }

        int slot = IntegerArgumentType.getInteger(context, "slot");
        ResourceLocation modifierId = ResourceLocationArgument.getId(context, "modifier");
        AbstractModifier modifier = ModifierRegistry.REGISTRY.get().getValue(modifierId);

        if (modifier == null) {
            context.getSource().sendFailure(Component.literal("未找到符文: " + modifierId));
            return 0;
        }

        RuneContainerHelper.setRune(stack, slot, modifier);

        context.getSource().sendSuccess(() -> Component.literal("已镶嵌 ")
                .append(modifier.getDisplayName())
                .append(Component.literal(" 到槽位 " + slot)), true);
        return 1;
    }

    // 🔥 修复后的 clearSlot
    private static int clearSlot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        int slot = IntegerArgumentType.getInteger(context, "slot");

        // 直接调用 Helper 的移除方法
        boolean removed = RuneContainerHelper.removeRune(stack, slot);

        if (removed) {
            context.getSource().sendSuccess(() -> Component.literal("已清除槽位 " + slot + " 的符文。"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("槽位 " + slot + " 是空的或物品没有符文。"));
            return 0;
        }
    }

    // 🔥 修复后的 clearAll
    private static int clearAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        if (!RuneContainerHelper.hasRunes(stack)) {
            context.getSource().sendFailure(Component.literal("该物品没有任何符文。"));
            return 0;
        }

        // 直接调用 Helper 的清空方法
        RuneContainerHelper.clearAllRunes(stack);

        context.getSource().sendSuccess(() -> Component.literal("已清除所有符文。"), true);
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> MODIFIER_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggestResource(
                ModifierRegistry.REGISTRY.get().getKeys(),
                builder
        );
    };
}
