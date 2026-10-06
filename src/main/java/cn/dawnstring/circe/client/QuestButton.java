package cn.dawnstring.circe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class QuestButton extends Button
{
    public QuestButton(int x, int y, int width, Component title, OnPress action)
    {
        super(x, y, width, 20, title, action, DEFAULT_NARRATION);
    }

    protected boolean isPrimary()
    {
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        boolean isClassic = QuestTheme.style() == QuestTheme.Style.CLASSIC;
        int background = !active ? QuestTheme.panel() : isHoveredOrFocused() ? QuestTheme.hover()
            : isPrimary() ? (isClassic ? QuestTheme.selected() : QuestTheme.accent()) : QuestTheme.panel();
        int foreground = !active ? QuestTheme.muted()
            : isPrimary() && !isClassic && !isHoveredOrFocused() ? QuestTheme.background() : QuestTheme.text();
        int border = isFocused() || isPrimary() ? QuestTheme.accent() : QuestTheme.border();
        QuestTheme.fill(graphics, getX(), getY(), getX() + width, getY() + height, border);
        QuestTheme.fill(graphics, getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, background);
        if (isClassic && !(this instanceof QuestEditorButton))
        {
            graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height,
                active ? QuestTheme.accent() : QuestTheme.muted());
        }
        var font = Minecraft.getInstance().font;
        String title = QuestTheme.fit(font, getMessage(), width - 12);
        graphics.drawString(font, title, getX() + (width - font.width(title)) / 2,
            getY() + (height - font.lineHeight) / 2, foreground, false);
    }
}
