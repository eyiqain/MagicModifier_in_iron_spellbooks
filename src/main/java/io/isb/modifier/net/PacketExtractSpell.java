package io.isb.modifier.net;

import io.isb.modifier.gui.SpellMenu;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketExtractSpell {
    private final int bookSlotIndex;

    public PacketExtractSpell(int bookSlotIndex) {
        this.bookSlotIndex = bookSlotIndex;
    }

    public static void encode(PacketExtractSpell msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.bookSlotIndex);
    }

    public static PacketExtractSpell decode(FriendlyByteBuf buffer) {
        return new PacketExtractSpell(buffer.readInt());
    }

    public static void handle(PacketExtractSpell msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack bookStack = Utils.getPlayerSpellbookStack(player);
                if (bookStack == null || !(bookStack.getItem() instanceof SpellBook)) return;

                ISpellContainer bookContainer = ISpellContainer.get(bookStack);

                // === DEBUG 1: 打印魔法书内所有法术 ===
                System.out.println("========== [DEBUG: PacketExtractSpell] Start ==========");
                System.out.println("Player: " + player.getName().getString());
                System.out.println("SpellBook Max Slots: " + bookContainer.getMaxSpellCount());

                SpellData[] allSpells = bookContainer.getAllSpells();
                for (int i = 0; i < allSpells.length; i++) {
                    SpellData sd = allSpells[i];
                    if (sd != null && !sd.equals(SpellData.EMPTY)) {
                        System.out.println("  [Book Slot " + i + "] ID: " + sd.getSpell().getSpellId() + " | Level: " + sd.getLevel());
                    } else {
                        // 如果只想看有东西的，可以注释掉下面这行
                        // System.out.println("  [Book Slot " + i + "] Empty");
                    }
                }

                if (msg.bookSlotIndex >= 0 && msg.bookSlotIndex < bookContainer.getMaxSpellCount()) {
                    // 1. 获取源数据
                    SpellData sourceData = bookContainer.getSpellAtIndex(msg.bookSlotIndex);

                    if (sourceData != null && !sourceData.equals(SpellData.EMPTY)) {

                        // === 2. 显式记录法术ID和等级 ===
                        AbstractSpell spellToKeep = sourceData.getSpell();
                        int levelToKeep = sourceData.getLevel();

                        // 防御性编程
                        if (levelToKeep <= 0) levelToKeep = 1;

                        // === DEBUG 2: 打印准备刻录的数据 ===
                        System.out.println(">>> Target Operation: Extract from Slot " + msg.bookSlotIndex);
                        System.out.println(">>> Source Spell: " + spellToKeep.getSpellId());
                        System.out.println(">>> Source Level (levelToKeep): " + levelToKeep);

                        // === 3. 生成并初始化卷轴 ===
                        ItemStack scrollStack = new ItemStack(ItemRegistry.SCROLL.get());
                        ISpellContainer scrollContainer = ISpellContainer.createScrollContainer(
                                spellToKeep,
                                levelToKeep,
                                scrollStack
                        );
                        scrollContainer.save(scrollStack);



                        // === 5. 双重验证与结果打印 ===
                        SpellData checkData = scrollContainer.getSpellAtIndex(0);

                        // 打印读取回来的数据
                        if (checkData != null && !checkData.equals(SpellData.EMPTY)) {
                            System.out.println(">>> ReadBack Check - Spell: " + checkData.getSpell().getSpellId());
                            System.out.println(">>> ReadBack Check - Level: " + checkData.getLevel());
                        } else {
                            System.out.println(">>> ReadBack Check - RESULT IS EMPTY/NULL!");
                        }

                        boolean writeSuccess = checkData != null &&
                                !checkData.equals(SpellData.EMPTY) &&
                                checkData.getSpell().equals(spellToKeep);

                        System.out.println(">>> Final Decision (writeSuccess): " + writeSuccess);

                        if (writeSuccess) {
                            System.out.println(">>> SUCCESS! Removing spell from book and giving scroll.");
                            // 移除书里的法术
                            bookContainer.removeSpellAtIndex(msg.bookSlotIndex, bookStack);
                            bookContainer.save(bookStack); // 关键：书也要 save
                            // 给玩家卷轴
                            if (!player.getInventory().add(scrollStack)) {
                                System.out.println(">>> Inventory full, dropping item.");
                                player.drop(scrollStack, false);
                            }
                            //似乎是更新背包小连招
                            player.getInventory().setChanged();
                            player.containerMenu.slotsChanged(player.getInventory());
                            player.containerMenu.broadcastChanges();
                            player.inventoryMenu.broadcastChanges(); // 可选但通常很有用

                        } else {
                            System.out.println("Error: Failed to inscribe spell onto scroll. Operation aborted.");
                        }
                    } else {
                        System.out.println("Error: Source slot " + msg.bookSlotIndex + " is empty on server side!");
                    }
                } else {
                    System.out.println("Error: Slot index out of bounds: " + msg.bookSlotIndex);
                }
                System.out.println("========== [DEBUG] End ==========");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
