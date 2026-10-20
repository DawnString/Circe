package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TeamStatePayload(String json) implements CustomPacketPayload
{
    public static final Type<TeamStatePayload> TYPE = new Type<>(ResourceLocation.parse("circe:team_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamStatePayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeUtf(payload.json(), 65536),
        buffer -> new TeamStatePayload(buffer.readUtf(65536)));

    @Override
    public Type<TeamStatePayload> type()
    {
        return TYPE;
    }
}
