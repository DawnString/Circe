package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EditResultPayload(boolean isSuccessful, Component message) implements CustomPacketPayload
{
    public static final Type<EditResultPayload> TYPE = new Type<>(ResourceLocation.parse("circe:edit_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditResultPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, EditResultPayload::isSuccessful,
        ComponentSerialization.STREAM_CODEC, EditResultPayload::message,
        EditResultPayload::new);

    @Override
    public Type<EditResultPayload> type()
    {
        return TYPE;
    }
}
