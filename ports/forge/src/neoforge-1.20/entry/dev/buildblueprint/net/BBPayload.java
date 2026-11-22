package dev.buildblueprint.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One Build Blueprint message (NeoForge 1.20.4's payload style): just bytes. */
public record BBPayload(byte[] data) implements CustomPacketPayload {
    @Override public void write(FriendlyByteBuf b) { b.writeBytes(data); }
    @Override public ResourceLocation id() { return ServerNet.ID; }
    static BBPayload read(FriendlyByteBuf b) { byte[] d = new byte[b.readableBytes()]; b.readBytes(d); return new BBPayload(d); }
}
