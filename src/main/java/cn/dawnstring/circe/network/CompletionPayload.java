package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompletionPayload(ResourceLocation questId, String title) implements CustomPacketPayload
{
    public static final Type<CompletionPayload> TYPE = new Type<>(ResourceLocation.parse("circe:completion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompletionPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeResourceLocation(payload.questId());
            buffer.writeUtf(payload.title(), 256);
        }, buffer -> new CompletionPayload(buffer.readResourceLocation(), buffer.readUtf(256)));

    @Override
    public Type<CompletionPayload> type()
    {
        return TYPE;
    }
}
