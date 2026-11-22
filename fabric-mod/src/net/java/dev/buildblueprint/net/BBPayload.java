package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Proto;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One Build Blueprint message on the "buildblueprint:sync" channel: just bytes (the same as a Paper plugin message). */
public record BBPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<BBPayload> TYPE = new Type<>(Identifier.parse(Proto.CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, BBPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBytes(p.data()),
            buf -> { byte[] d = new byte[buf.readableBytes()]; buf.readBytes(d); return new BBPayload(d); });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
