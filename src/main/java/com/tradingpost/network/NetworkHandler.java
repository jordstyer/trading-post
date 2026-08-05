package com.tradingpost.network;

import com.tradingpost.TradingPostMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** A single SimpleChannel carrying the mod's buy/sell request packets and their sync reply. */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TradingPostMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private NetworkHandler() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SBuyPacket.class, C2SBuyPacket::encode, C2SBuyPacket::decode, C2SBuyPacket::handle);
        CHANNEL.registerMessage(id++, C2SSellPacket.class, C2SSellPacket::encode, C2SSellPacket::decode, C2SSellPacket::handle);
        CHANNEL.registerMessage(id++, S2CSyncMarketPacket.class, S2CSyncMarketPacket::encode, S2CSyncMarketPacket::decode, S2CSyncMarketPacket::handle);
    }
}
