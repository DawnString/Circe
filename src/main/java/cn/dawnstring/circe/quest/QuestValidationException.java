package cn.dawnstring.circe.quest;

import net.minecraft.network.chat.Component;

public final class QuestValidationException extends IllegalArgumentException
{
    private final Component message;

    public QuestValidationException(String key, Object... arguments)
    {
        super(key);
        message = Component.translatable(key, arguments);
    }

    public Component translatedMessage()
    {
        return message;
    }

    public static Component message(Throwable exception)
    {
        if (exception instanceof QuestValidationException validation)
        {
            return validation.translatedMessage();
        }
        String message = exception.getMessage();
        return message == null ? Component.translatable("circe.error.save_unknown")
            : Component.translatable(message.substring(0, Math.min(1024, message.length())));
    }
}
