package cn.dawnstring.circe.client;

import net.minecraft.network.chat.Component;

public final class QuestEditorButton extends QuestButton
{
    private boolean isPrimary;

    public QuestEditorButton(int x, int y, int width, Component title, OnPress action)
    {
        super(x, y, width, title, action);
    }

    public QuestEditorButton primary()
    {
        isPrimary = true;
        return this;
    }

    @Override
    protected boolean isPrimary()
    {
        return isPrimary;
    }
}
