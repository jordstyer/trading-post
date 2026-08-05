package com.tradingpost.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "I want to sell `quantity` of `itemId` to colony `colonyId`." */
public record C2SSellPacket(String colonyId, ResourceLocation itemId, int quantity) {

    public static void encode(C2SSellPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.colonyId());
        buf.writeResourceLocation(msg.itemId());
        buf.writeVarInt(msg.quantity());
    }

    public static C2SSellPacket decode(FriendlyByteBuf buf) {
        return new C2SSellPacket(buf.readUtf(), buf.readResourceLocation(), buf.readVarInt());
    }

    public static void handle(C2SSellPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                TradeExecutor.sell(player, msg.colonyId(), msg.itemId(), msg.quantity());
            }
        });
        ctx.setPacketHandled(true);
    }
}
