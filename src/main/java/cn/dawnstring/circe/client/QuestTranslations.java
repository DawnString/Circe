package cn.dawnstring.circe.client;

import net.minecraft.network.chat.Component;

public final class QuestTranslations
{
    private QuestTranslations()
    {
    }

    public static String text(String key, Object... arguments)
    {
        return Component.translatable(key, arguments).getString();
    }
}
