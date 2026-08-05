package com.tradingpost.network;

import com.tradingpost.menu.TradingPostMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: "here is the up-to-date state of this one colony." Sent after every
 * successful buy/sell to the acting player so their open screen reflects the real, server-side
 * price and supply rather than anything the client guessed.
 */
public record S2CSyncMarketPacket(MarketNetworking.ColonySnapshot colony) {

    public static void encode(S2CSyncMarketPacket msg, FriendlyByteBuf buf) {
        MarketNetworking.writeColonySnapshot(buf, msg.colony());
    }

    public static S2CSyncMarketPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncMarketPacket(MarketNetworking.readColonySnapshot(buf));
    }

    public static void handle(S2CSyncMarketPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    private static void handleClient(S2CSyncMarketPacket msg) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof TradingPostMenu menu) {
            menu.updateColony(msg.colony());
        }
    }
}
