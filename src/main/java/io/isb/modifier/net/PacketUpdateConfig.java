package io.isb.modifier.net;

import io.isb.modifier.config.IEyiConfigParams;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateConfig {
    private final String spellId;
    private final int type;
    private final double value;

    public static final int TYPE_CHARGES = 0;
    public static final int TYPE_POWER = 1;
    public static final int TYPE_COOLDOWN = 2;
    public static final int TYPE_MANA = 3; // 🔥

    public PacketUpdateConfig(String spellId, int type, double value) {
        this.spellId = spellId;
        this.type = type;
        this.value = value;
    }

    public PacketUpdateConfig(FriendlyByteBuf buf) {
        this.spellId = buf.readUtf();
        this.type = buf.readInt();
        this.value = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(spellId);
        buf.writeInt(type);
        buf.writeDouble(value);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.hasPermissions(2)) {
                AbstractSpell spell = SpellRegistry.getSpell(spellId);
                if (spell != SpellRegistry.none()) {
                    var config = ServerConfigs.getSpellConfig(spell);
                    if (config instanceof IEyiConfigParams eyiParams) {
                        switch (type) {
                            case TYPE_CHARGES -> eyiParams.eyi$setConfigMaxCharges((int) value);
                            case TYPE_POWER -> eyiParams.eyi$setConfigPowerMultiplier(value);
                            case TYPE_COOLDOWN -> eyiParams.eyi$setConfigCooldownMultiplier(value);
                            case TYPE_MANA -> eyiParams.eyi$setConfigManaMultiplier(value); // 🔥
                        }
                    }
                }
            }
        });
        return true;
    }
}
