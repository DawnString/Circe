package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImageRequestPayload(ResourceLocation id) implements CustomPacketPayload
{
    public static final Type<ImageRequestPayload> TYPE = new Type<>(ResourceLocation.parse("circe:image_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageRequestPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeResourceLocation(payload.id()), buffer -> new ImageRequestPayload(buffer.readResourceLocation()));

    @Override
    public Type<ImageRequestPayload> type()
    {
        return TYPE;
    }
}
