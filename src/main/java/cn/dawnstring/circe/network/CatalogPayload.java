package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CatalogPayload(String json) implements CustomPacketPayload
{
    private static final int MAX_SNAPSHOT_CHARACTERS = 524_288;
    public static final Type<CatalogPayload> TYPE = new Type<>(ResourceLocation.parse("circe:catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CatalogPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeUtf(payload.json(), MAX_SNAPSHOT_CHARACTERS),
        buffer -> new CatalogPayload(buffer.readUtf(MAX_SNAPSHOT_CHARACTERS)));

    @Override
    public Type<CatalogPayload> type()
    {
        return TYPE;
    }
}
