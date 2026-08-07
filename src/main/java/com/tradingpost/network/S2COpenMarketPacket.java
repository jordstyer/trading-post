package com.tradingpost.network;

import com.tradingpost.menu.TradingPostMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: the full market (every colony, every item) plus the price-band factors, sent
 * right after the menu opens.
 *
 * <p>This exists separately from the menu-open call itself because {@code NetworkHooks.openScreen}'s
 * extra-data buffer is hard-capped at 32,600 bytes - fine for a {@code BlockPos}, but the full
 * catalog (six colonies, 200+ tag-scanned items, more once datapack overrides add to it) serializes
 * to roughly 90-100KB and blew straight through that limit, throwing
 * {@code IllegalArgumentException: Invalid PacketBuffer for openGui} and silently eating the click -
 * the player saw nothing happen, and the only trace was a server-log stack trace. A normal
 * {@link net.minecraftforge.network.simple.SimpleChannel} packet like this one has no such cap, so
 * the fix is to open the menu with just the position (see {@link TradingPostBlock}) and deliver the
 * actual market data here instead.
 */
public record S2COpenMarketPacket(MarketNetworking.Market market) {

    public static void encode(S2COpenMarketPacket msg, FriendlyByteBuf buf) {
        MarketNetworking.writeMarketBody(buf, msg.market());
    }

    public static S2COpenMarketPacket decode(FriendlyByteBuf buf) {
        return new S2COpenMarketPacket(MarketNetworking.readMarketBody(buf));
    }

    public static void handle(S2COpenMarketPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    private static void handleClient(S2COpenMarketPacket msg) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof TradingPostMenu menu) {
            menu.loadMarket(msg.market());
        }
    }
}
